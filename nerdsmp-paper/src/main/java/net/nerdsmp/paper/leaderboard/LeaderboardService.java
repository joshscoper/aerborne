package net.nerdsmp.paper.leaderboard;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Account-wide cached rankings, including offline accounts. Opening a GUI never scans player files. */
public final class LeaderboardService implements Listener, AutoCloseable {
    public enum Board {
        MARKS("Marks", Material.GOLD_INGOT), ADVENTURER("Adventurer XP", Material.NETHER_STAR),
        SKILLS("Total skill levels", Material.EXPERIENCE_BOTTLE), ISLAND("Island XP", Material.GRASS_BLOCK),
        ACHIEVEMENTS("Achievement points", Material.BOOK), COOKING("Cooking XP", Material.COOKED_CHICKEN);
        final String title; final Material icon;
        Board(String title, Material icon) { this.title = title; this.icon = icon; }
    }
    public record Entry(AccountId account, String name, Map<Board, Long> scores) {
        public Entry { Objects.requireNonNull(account); Objects.requireNonNull(name); scores = Map.copyOf(scores); if (scores.values().stream().anyMatch(v -> v < 0)) throw new IllegalArgumentException("Negative leaderboard score"); }
        public long score(Board board) { return scores.getOrDefault(board, 0L); }
    }
    /** Public profile facts shown by the website: the same numbers players already see in /leaderboard. */
    public record Profile(String name, int adventurerLevel, String rank, Map<Skill, Integer> skillLevels) {
        public Profile { Objects.requireNonNull(name); Objects.requireNonNull(rank); skillLevels = Map.copyOf(skillLevels); }
    }
    private record Collected(EnumMap<Board, Long> scores, int adventurerLevel, String rank, Map<Skill, Integer> skillLevels) {}
    public static List<Entry> rank(Collection<Entry> entries, Board board) {
        return entries.stream().sorted(Comparator.comparingLong((Entry e) -> e.score(board)).reversed()
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)).thenComparing(e -> e.account().toString())).toList();
    }
    private final NerdSmpPlugin plugin;
    private Map<Board, List<Entry>> rankings = Map.of();
    private Map<AccountId, Profile> profiles = Map.of();
    private final Map<AccountId, Profile> collectingProfiles = new HashMap<>();
    private Queue<AccountId> queue;
    private final Map<AccountId, Entry> collecting = new HashMap<>();
    private int inFlight;
    private boolean failed;
    private volatile boolean closed;
    private long nextRefresh;
    private Instant updated;
    private org.bukkit.scheduler.BukkitTask task;
    public LeaderboardService(NerdSmpPlugin plugin) { this.plugin = plugin; }
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 10);
    }
    private void tick() {
        if (closed) return;
        if (queue == null) {
            if (System.nanoTime() < nextRefresh) return;
            queue = new ArrayDeque<>(plugin.playerData().knownAccounts()); collecting.clear(); collectingProfiles.clear(); failed = false;
        }
        for (int count = 0; count < 12 && inFlight < 24 && !queue.isEmpty(); count++) {
            AccountId account = queue.remove(); inFlight++;
            try {
                plugin.progression().snapshot(account).thenCombine(plugin.economy().balance(account), (progress, marks) -> {
                    EnumMap<Board, Long> scores = new EnumMap<>(Board.class);
                    scores.put(Board.MARKS, marks); scores.put(Board.ADVENTURER, progress.adventurer().experience());
                    scores.put(Board.SKILLS, progress.skills().values().stream().mapToLong(p -> p.level()).sum());
                    scores.put(Board.COOKING, progress.skills().getOrDefault(Skill.COOKING, new net.nerdsmp.api.service.ProgressionService.SkillProgress(0, 0)).experience());
                    EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
                    progress.skills().forEach((skill, skillProgress) -> levels.put(skill, skillProgress.level()));
                    var adventurer = progress.adventurer();
                    return new Collected(scores, adventurer.level(), adventurer.rank().displayName(), levels);
                }).toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).whenComplete((collected, error) -> {
                    if (closed) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (closed) return;
                        try {
                            if (error != null) { failed = true; return; }
                            EnumMap<Board, Long> scores = collected.scores();
                            String name = plugin.playerData().read(account, yaml -> yaml.getString("identity.last-known-name", account.toString().substring(0, 8)));
                            scores.put(Board.ACHIEVEMENTS, (long) plugin.achievements().points(account));
                            long islandXp = plugin.skyblock() == null || plugin.skyblock().islands().owned(account).isEmpty() ? 0 : plugin.skyblock().development().progress(account).xp();
                            scores.put(Board.ISLAND, islandXp);
                            collecting.put(account, new Entry(account, name, scores));
                            collectingProfiles.put(account, new Profile(name, collected.adventurerLevel(), collected.rank(), collected.skillLevels()));
                        } catch (RuntimeException unavailable) { failed = true; }
                        finally { inFlight--; }
                    });
                });
            } catch (RuntimeException unavailable) { inFlight--; failed = true; }
        }
        if (!queue.isEmpty() || inFlight != 0) return;
        if (!failed) {
            Map<Board, List<Entry>> next = new EnumMap<>(Board.class);
            for (Board board : Board.values()) next.put(board, rank(collecting.values(), board));
            rankings = Map.copyOf(next); profiles = Map.copyOf(collectingProfiles); updated = Instant.now();
        } else plugin.getLogger().warning("Leaderboard refresh incomplete; keeping the previous complete snapshot.");
        collecting.clear(); collectingProfiles.clear(); queue = null; nextRefresh = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    }
    /** Latest complete snapshot, read on the main thread by the web API. */
    public Map<Board, List<Entry>> rankings() { return rankings; }
    public Map<AccountId, Profile> profiles() { return profiles; }
    public Instant updated() { return updated; }
    public boolean command(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        Board board;
        try { board = args.length == 0 ? Board.MARKS : Board.valueOf(args[0].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { Chat.usage(player, "/leaderboard [marks|adventurer|skills|island|achievements|cooking]"); return true; }
        open(player, board, 0); return true;
    }
    private void open(Player player, Board board, int requested) {
        List<Entry> entries = rankings.getOrDefault(board, List.of());
        int page = Math.clamp(requested, 0, Math.max(0, (entries.size() - 1) / 21));
        int pages = Math.max(1, (entries.size() + 20) / 21);
        View view = new View(player.getUniqueId()); view.inventory = Bukkit.createInventory(view, 54, Chat.render("<gold><bold>Leaderboards</bold></gold> <gray>" + board.title + "</gray>"));
        MenuKit.frame(view.inventory);
        int[] tabs = {0, 2, 3, 5, 6, 8};
        for (Board option : Board.values()) {
            if (option == board) view.inventory.setItem(tabs[option.ordinal()], MenuKit.item(option.icon, "<green>" + option.title + "</green>", List.of("<green>Showing now.</green>")));
            else button(view, tabs[option.ordinal()], MenuKit.item(option.icon, "<aqua>" + option.title + "</aqua>", List.of(MenuKit.hint("view this ranking"))), () -> open(player, option, 0));
        }
        view.inventory.setItem(4, MenuKit.item(Material.CLOCK, "<gold>Server Records</gold>", List.of(updated == null
            ? "<gray>Preparing the first snapshot...</gray>" : "<gray>Includes offline players. Refreshes once a minute.</gray>", MenuKit.pageLine(page, pages))));
        for (int i = 0; i < 21 && page * 21 + i < entries.size(); i++) {
            int position = page * 21 + i; Entry entry = entries.get(position);
            view.inventory.setItem(10 + i / 7 * 9 + i % 7, MenuKit.item(board.icon, "<aqua>#" + (position + 1) + "</aqua> <white>" + Chat.escape(entry.name()) + "</white>",
                List.of(score(board, entry.score(board)))));
        }
        var account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        int mine = -1; for (int i = 0; i < entries.size(); i++) if (entries.get(i).account().equals(account)) { mine = i; break; }
        view.inventory.setItem(40, MenuKit.item(Material.NAME_TAG, "<aqua>Your Standing</aqua>", mine < 0 ? List.of("<gray>Your first snapshot will appear shortly.</gray>")
            : List.of("<gray>Rank</gray> <white>#" + (mine + 1) + "</white>", score(board, entries.get(mine).score(board)))));
        if (page > 0) button(view, MenuKit.previousSlot(54), MenuKit.previous(page, pages), () -> open(player, board, page - 1));
        button(view, MenuKit.backSlot(54), MenuKit.back("Character"), () -> plugin.menus().open(player, MenuManager.View.CHARACTER));
        button(view, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory);
        if ((page + 1) * 21 < entries.size()) button(view, MenuKit.nextSlot(54), MenuKit.next(page, pages), () -> open(player, board, page + 1));
        player.openInventory(view.inventory);
    }
    private static String score(Board board, long score) {
        if (board == Board.MARKS) return Chat.marksTag(score);
        return "<white>" + String.format(Locale.ROOT, "%,d", score) + "</white> <gray>" + board.title + "</gray>";
    }
    private static void button(View view, int slot, ItemStack item, Runnable action) { view.inventory.setItem(slot, item); view.actions.put(slot, action); }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true);
        if (!view.owner.equals(event.getWhoClicked().getUniqueId()) || event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) return;
        Runnable action = view.actions.get(event.getRawSlot());
        if (action != null && event.getWhoClicked() instanceof Player clicker && event.getRawSlot() != MenuKit.closeSlot(54)) MenuKit.clickSound(clicker);
        if (action != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed && event.getWhoClicked().getOpenInventory().getTopInventory() == view.inventory) action.run();
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true); }
    @Override public void close() { closed = true; if (task != null) task.cancel(); HandlerList.unregisterAll(this); }
    private static final class View implements InventoryHolder {
        final UUID owner; Inventory inventory; final Map<Integer, Runnable> actions = new HashMap<>(); View(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
