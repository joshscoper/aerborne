package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Island rankings, after the seasonal island leaderboards Hytale's skyblock servers run: a season score that sums all
 * of an island's progress, and a weekly race on what each island gained since Monday, whose top three co-ops are paid
 * every week. Scores are worked out a few islands at a time so the ranking never stalls the server.
 */
public final class IslandRanking implements Listener, AutoCloseable {
    /** What counts: island XP (and prestige XP past level 50), plus fixed points per showcase, visitor and worker tier. */
    public static long score(long islandXp, long prestigeXp, int showcases, int guestsServed, int workerUnlocks) {
        return islandXp + prestigeXp + showcases * 1_000L + guestsServed * 100L + workerUnlocks * 250L;
    }

    /** Weekly prizes per member of the top three islands. */
    static final long[] WEEKLY_MARKS = {5_000, 3_000, 1_500};
    static final int BATCH = 8;

    public record Entry(AccountId owner, String name, Material icon, int level, int stars, long score, long week) { }

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final File file;
    private final YamlConfiguration state;
    private final ArrayDeque<AccountId> queue = new ArrayDeque<>();
    private final Map<AccountId, Entry> entries = new HashMap<>();
    private List<Entry> season = List.of(), weekly = List.of();
    private long passStarted;
    private boolean dirty;
    private BukkitTask task;

    IslandRanking(NerdSmpPlugin plugin, SkyblockController islands, String season) {
        this.plugin = plugin;
        this.islands = islands;
        this.file = new File(plugin.getDataFolder(), "island-rankings-" + season + ".yml");
        this.state = YamlConfiguration.loadConfiguration(file);
    }

    void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 100L, 20L); }

    private ZoneId zone() { return plugin.boosters() == null ? ZoneId.of("America/Denver") : plugin.boosters().weekend().zone(); }

    /** "2026-W46": the ranking week, Monday to Sunday in the event time zone. */
    static String week(Instant now, ZoneId zone) {
        var date = now.atZone(zone).toLocalDate();
        return date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Works out a few islands each second; after a full pass, rebuilds the boards and settles a finished week. */
    private void step() {
        try {
            if (queue.isEmpty()) {
                if (System.currentTimeMillis() - passStarted < 5 * 60_000L && !entries.isEmpty()) return;
                passStarted = System.currentTimeMillis();
                islands.islands().all().forEach(i -> queue.add(i.owner()));
                return;
            }
            String thisWeek = week(Instant.now(), zone());
            for (int n = 0; n < BATCH && !queue.isEmpty(); n++) {
                AccountId owner = queue.poll();
                IslandService.Island island = islands.islands().owned(owner).orElse(null);
                if (island == null) { entries.remove(owner); continue; }
                var context = islands.showcaseContext(owner);
                var development = islands.development();
                long score = score(development.progress(owner).xp(), development.prestigeXp(owner), context.collected(),
                    context.guestsServed(), islands.workers().unlocks(owner));
                String key = "week-start." + owner.value();
                if (!thisWeek.equals(state.getString(key + ".week"))) {
                    state.set(key + ".week", thisWeek);
                    state.set(key + ".score", score);
                    dirty = true;
                }
                long gained = Math.max(0, score - state.getLong(key + ".score", score));
                entries.put(owner, new Entry(owner, island.name(), island.showcase().material(), development.progress(owner).level(),
                    development.stars(owner), score, gained));
            }
            if (queue.isEmpty()) finishPass(thisWeek);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Island ranking step failed", failure);
            queue.clear();
        }
    }

    private void finishPass(String thisWeek) {
        season = entries.values().stream().sorted(Comparator.comparingLong(Entry::score).reversed()).toList();
        weekly = entries.values().stream().sorted(Comparator.comparingLong(Entry::week).reversed()
            .thenComparing(Comparator.comparingLong(Entry::score).reversed())).toList();
        // The first pass in a new week settles the last one, from the gains recorded before the snapshot moved on.
        String settled = state.getString("settled-week");
        if (settled == null) { state.set("settled-week", thisWeek); dirty = true; }
        else if (!settled.equals(thisWeek)) payLastWeek(settled, thisWeek);
        save();
    }

    private void payLastWeek(String finished, String thisWeek) {
        state.set("settled-week", thisWeek);
        dirty = true;
        // Gains for the finished week were frozen into "standing" at each pass; pay from that.
        List<String> standing = state.getStringList("standing");
        List<String> lines = new ArrayList<>();
        for (int rank = 0; rank < Math.min(WEEKLY_MARKS.length, standing.size()); rank++) {
            String[] parts = standing.get(rank).split("\\|");
            AccountId owner = AccountId.parse(parts[0]);
            IslandService.Island island = islands.islands().owned(owner).orElse(null);
            if (island == null) continue;
            List<AccountId> crew = new ArrayList<>(island.members());
            crew.add(island.owner());
            for (AccountId member : crew) {
                plugin.economy().credit(member, WEEKLY_MARKS[rank], new EconomyService.TransactionReason("island-weekly", "Island of the week #" + (rank + 1)),
                    "island-weekly:" + finished + ":" + member.value());
                if (rank == 0 && plugin.boosters() != null) plugin.boosters().earn(member, "island-weekly:" + finished);
            }
            lines.add("#" + (rank + 1) + " " + island.name() + " (+" + String.format("%,d", Long.parseLong(parts[1])) + ")");
        }
        state.set("last-week", finished);
        state.set("last-week-results", lines);
        if (!lines.isEmpty()) for (Player player : Bukkit.getOnlinePlayers()) {
            Chat.send(player, "<gold><bold>✦ Islands of the week</bold></gold> <gray>" + Chat.escape(String.join(" • ", lines))
                + ". Members of the top three are paid; the winners also get an XP booster. A new week has begun:</gray> " + Chat.command("island top"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1F);
        }
    }

    /** Freezes this week's standing after each pass, so the payout uses the week's final gains, not next week's. */
    private void save() {
        state.set("standing", weekly.stream().limit(WEEKLY_MARKS.length).map(e -> e.owner().value() + "|" + e.week()).toList());
        try { AtomicYamlFiles.save(state, file); dirty = false; }
        catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Could not save island rankings", failure); }
    }

    // ------------------------------------------------------------------ the board

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 22, 24};

    void open(Player player, boolean weeklyView) {
        if (season.isEmpty()) {
            Chat.info(player, "The island rankings are still being worked out. Try again in a minute.");
            if (queue.isEmpty()) passStarted = 0;
            return;
        }
        Holder holder = new Holder(player.getUniqueId(), weeklyView);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>" + (weeklyView ? "Islands of the Week" : "Season Rankings") + "</gold>"));
        MenuKit.frame(holder.inventory);
        List<Entry> board = weeklyView ? weekly : season;
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        Entry mine = null; int myRank = -1;
        if (account != null) {
            var homes = islands.islands().homes(account);
            for (int i = 0; i < board.size(); i++) {
                AccountId owner = board.get(i).owner();
                if (homes.stream().anyMatch(h -> h.owner().equals(owner))) { mine = board.get(i); myRank = i + 1; break; }
            }
        }
        String last = state.getString("last-week");
        holder.inventory.setItem(4, MenuKit.item(weeklyView ? Material.CLOCK : Material.GOLDEN_HELMET,
            "<gold>" + (weeklyView ? "Islands of the Week" : "Season Rankings") + "</gold>", List.of(
                weeklyView ? "<gray>Who gained the most since Monday.</gray>" : "<gray>Everything an island has done this season.</gray>",
                weeklyView ? "<gray>Every member of the top three is paid on Monday:</gray>" : "<gray>Island XP and prestige, plus 1,000 a showcase,</gray>",
                weeklyView ? "<gold>5,000 • 3,000 • 1,500 Marks</gold> <gray>and an XP booster for #1.</gray>" : "<gray>100 a visitor served, 250 a worker tier.</gray>",
                mine == null ? "<gray>Your island isn't ranked yet.</gray>" : "<gray>Your island:</gray> <white>#" + myRank + " " + Chat.escape(mine.name()) + "</white>",
                last == null ? "" : "<dark_gray>Last week (" + last + "): " + Chat.escape(String.join(", ", state.getStringList("last-week-results"))) + "</dark_gray>")));
        for (int i = 0; i < SLOTS.length && i < board.size(); i++) {
            Entry e = board.get(i);
            String medal = i == 0 ? "<gold>" : i == 1 ? "<gray>" : i == 2 ? "<#cd7f32>" : "<white>";
            holder.inventory.setItem(SLOTS[i], MenuKit.item(e.icon(), medal + "#" + (i + 1) + " " + Chat.escape(e.name()) + (e.stars() > 0 ? " <gold>" + "★".repeat(e.stars()) + "</gold>" : ""), List.of(
                "<gray>Owner:</gray> <white>" + Chat.escape(name(e.owner())) + "</white>",
                "<gray>Island level</gray> <white>" + e.level() + "</white>",
                weeklyView ? "<gray>Gained this week:</gray> <gold>+" + String.format("%,d", e.week()) + "</gold>"
                    : "<gray>Season score:</gray> <gold>" + String.format("%,d", e.score()) + "</gold>")));
            if (i < 3) holder.inventory.getItem(SLOTS[i]).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        holder.inventory.setItem(39, MenuKit.item(weeklyView ? Material.GOLDEN_HELMET : Material.CLOCK,
            "<yellow>" + (weeklyView ? "Season rankings" : "Islands of the week") + "</yellow>", List.of("<yellow>Click to switch.</yellow>")));
        holder.inventory.setItem(41, MenuKit.item(Material.BOOK, "<aqua>How to climb</aqua>", List.of("<gray>Level your island; past 50, earn prestige stars.</gray>",
            "<gray>Collect showcases, serve visitors,</gray>", "<gray>and hire and upgrade workers.</gray>")));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Back to your island</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private String name(AccountId id) { return plugin.playerData().read(id, yaml -> yaml.getString("identity.last-known-name", "Unknown")); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            if (slot == 39) open(player, !holder.weekly);
            else if (slot == 45) player.performCommand("island");
            else if (slot == 49) player.closeInventory();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    @Override public void close() { if (task != null) task.cancel(); if (dirty) save(); }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; final boolean weekly; Inventory inventory;
        Holder(UUID viewer, boolean weekly) { this.viewer = viewer; this.weekly = weekly; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
