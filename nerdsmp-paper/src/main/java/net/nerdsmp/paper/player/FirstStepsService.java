package net.nerdsmp.paper.player;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A new player's First Steps: seven things to do, each ticked off by doing it (never by reading), each paying a few
 * Marks, with the next one always shown under their stats and on the companion HUD. It replaces the walls of join
 * text: a new player is told one thing at a time. {@code /quickstart} (also {@code /start}) opens the checklist.
 * Players who were already playing before it existed are marked finished and never see it.
 */
public final class FirstStepsService implements Listener, AutoCloseable {
    static final String PATH = "onboarding.first-steps";
    public static final long FINISH_MARKS = 500;

    /** One step: what to do, how, what clicking it in the checklist runs, and what it pays. */
    public enum Step {
        WREN("Meet Captain Wren", Material.WHITE_BANNER, "She waits on the Sky Docks where you arrived.",
            "Right-click her and accept her quest.", "spawn", 50),
        MENU("Open your menu", Material.COMPASS, "Everything on the server is one click away.",
            "Type /menu.", "menu", 50),
        ISLAND("Claim your island", Material.GRASS_BLOCK, "A home in the sky with a stone generator,",
            "a tree, crops and water. Type /island create.", "island create", 100),
        SKILL("Train a skill", Material.IRON_PICKAXE, "Mine, chop, farm, fish or dig: all of it levels up.",
            "Your island's generator is a good place to start.", "island home", 75),
        HUNT("Defeat a creature", Material.IRON_SWORD, "Cross the Kingsbridge north of Highcrown.",
            "Gnawers and boars roam Kingsgate Fields.", "spawn", 100),
        DAILY("Pick up daily contracts", Material.CLOCK, "Paid jobs that refresh every day.",
            "Type /daily.", "daily", 50),
        FLIGHT("Join an expedition", Material.ELYTRA, "First Flight is the gentle one: a short flight with a boss at the end.",
            "Captain Wren signs crews on, or open /expedition.", "expedition", 200);

        private final String title, why, how, command;
        private final Material icon;
        private final long marks;
        Step(String title, Material icon, String why, String how, String command, long marks) {
            this.title = title; this.icon = icon; this.why = why; this.how = how; this.command = command; this.marks = marks;
        }
        public String title() { return title; }
        public String why() { return why; }
        public String how() { return how; }
        public String command() { return command; }
        public Material icon() { return icon; }
        public long marks() { return marks; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        static Step parse(String id) {
            for (Step step : values()) if (step.id().equals(id)) return step;
            return null;
        }
    }

    /** Where a player stands: what they've done, and whether the checklist is over (finished or skipped). */
    public record State(Set<Step> done, boolean finished) {
        public State { done = done.isEmpty() ? EnumSet.noneOf(Step.class) : EnumSet.copyOf(done); }
        /** The first step not yet done, or null when every step is. */
        public Step next() {
            for (Step step : Step.values()) if (!done.contains(step)) return step;
            return null;
        }
        public boolean active() { return !finished; }
    }

    private final NerdSmpPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask task;

    public FirstStepsService(NerdSmpPlugin plugin) { this.plugin = plugin; }

    public void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 40L, 40L); }

    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }

    /** This player's checklist, read once per session. Empty while their account loads. */
    public java.util.Optional<State> state(Player player) {
        State cached = states.get(player.getUniqueId());
        if (cached != null) return java.util.Optional.of(cached);
        AccountId account = account(player);
        if (account == null) return java.util.Optional.empty();
        State loaded = plugin.playerData().read(account, yaml -> {
            if (!yaml.contains(PATH)) return null;
            Set<Step> done = EnumSet.noneOf(Step.class);
            for (String id : yaml.getStringList(PATH + ".done")) { Step step = Step.parse(id); if (step != null) done.add(step); }
            return new State(done, yaml.getBoolean(PATH + ".finished"));
        });
        if (loaded == null) {
            // First time we've seen this account: someone who already plays never gets a beginner's checklist.
            loaded = new State(Set.of(), veteran(player, account));
            save(account, loaded);
        }
        states.put(player.getUniqueId(), loaded);
        return java.util.Optional.of(loaded);
    }

    /** Whether the player shows a beginner's checklist under their stats right now. */
    public boolean active(Player player) { return state(player).map(State::active).orElse(false); }

    /**
     * Everyone who joined before First Steps existed was invited to the old tutorial at their first login (that
     * invitation no longer goes out), or already has an island: either way, not a beginner.
     */
    private boolean veteran(Player player, AccountId account) {
        var tutorial = plugin.tutorial().progress().state(account);
        if (tutorial.invited() || tutorial.complete()) return true;
        return plugin.skyblock() != null && !plugin.skyblock().islands().homes(account).isEmpty();
    }

    private void save(AccountId account, State state) {
        plugin.playerData().update(account, yaml -> {
            yaml.set(PATH + ".done", state.done().stream().map(Step::id).toList());
            yaml.set(PATH + ".finished", state.finished());
        });
    }

    // ------------------------------------------------------------------ joining

    /** The one line a player gets at join about what to do: their next step, or the menu. */
    public void greet(Player player) {
        var state = state(player).orElse(null);
        if (state == null || !state.active()) {
            Chat.info(player, "Your shortcuts are in " + Chat.command("menu") + ".");
            return;
        }
        Step next = state.next();
        if (next == null) return;
        if (state.done().isEmpty()) Chat.send(player, "<gold><bold>Welcome!</bold></gold> <gray>Seven quick steps get you started; each pays Marks."
            + " Your next one is always under your stats. See them all with</gray> " + Chat.command("start") + "<gray>.</gray>");
        Chat.send(player, "<gold>Next step:</gold> <white>" + Chat.escape(next.title()) + "</white> <gray>— " + Chat.escape(next.how()) + "</gray>");
    }

    // ------------------------------------------------------------------ ticking steps off

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        Player player = event.getPlayer();
        if (label.equals("menu") || label.equals("m")) Bukkit.getScheduler().runTask(plugin, () -> complete(player, Step.MENU));
        if (label.equals("daily") || label.equals("contracts")) Bukkit.getScheduler().runTask(plugin, () -> complete(player, Step.DAILY));
    }

    private void check() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state == null || !state.active() || !plugin.presentation().progressionReady(player)) continue;
            AccountId account = account(player);
            if (account == null) continue;
            try {
                for (Step step : Step.values()) if (!state.done().contains(step) && reached(player, account, step)) complete(player, step);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "First steps check failed for " + player.getName(), failure);
            }
        }
    }

    private boolean reached(Player player, AccountId account, Step step) {
        ProgressionService.ProgressionSnapshot skills = plugin.presentation().progression(player);
        return switch (step) {
            case WREN -> plugin.quests() != null && plugin.quests().started(player);
            case ISLAND -> plugin.skyblock() != null && !plugin.skyblock().islands().homes(account).isEmpty();
            case SKILL -> skills.skills().entrySet().stream().anyMatch(e -> e.getKey() != Skill.HUNTING && e.getValue().experience() > 0);
            case HUNT -> skills.skills().getOrDefault(Skill.HUNTING, new ProgressionService.SkillProgress(0, 0)).experience() > 0;
            case FLIGHT -> net.nerdsmp.paper.expedition.InstanceWorlds.isRunWorld(player.getWorld())
                || plugin.quests() != null && plugin.quests().completed(player, "reach_the_guild_test");
            case MENU, DAILY -> false;
        };
    }

    /** Ticks a step off, pays it, and shows the next. Doing a step twice, or after finishing, does nothing. */
    public void complete(Player player, Step step) {
        State state = state(player).orElse(null);
        AccountId account = account(player);
        if (state == null || account == null || !state.active() || state.done().contains(step)) return;
        Set<Step> done = EnumSet.copyOf(state.done().isEmpty() ? EnumSet.noneOf(Step.class) : state.done());
        done.add(step);
        boolean all = done.size() == Step.values().length;
        State next = new State(done, all);
        states.put(player.getUniqueId(), next);
        save(account, next);
        pay(account, step.marks(), "first-steps:" + account.value() + ":" + step.id(), "First steps: " + step.title());
        Chat.success(player, "<white>" + Chat.escape(step.title()) + "</white> done! <gold>+" + Chat.marks(step.marks()) + "</gold> <gray>(" + done.size() + "/" + Step.values().length + ")</gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
        if (all) {
            pay(account, FINISH_MARKS, "first-steps:" + account.value() + ":finished", "First steps complete");
            Chat.send(player, "<gold><bold>✦ First Steps complete!</bold></gold> <gray>Here's</gray> <gold>" + Chat.marks(FINISH_MARKS)
                + "</gold><gray>. From here, follow Captain Wren's story in</gray> " + Chat.command("journal") + " <gray>and grow your island in</gray> " + Chat.command("island journal") + "<gray>.</gray>");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1F);
            return;
        }
        Step upcoming = next.next();
        if (upcoming != null)
            Chat.send(player, "<gold>Next step:</gold> <white>" + Chat.escape(upcoming.title()) + "</white> <gray>— " + Chat.escape(upcoming.how()) + "</gray>");
    }

    private void pay(AccountId account, long marks, String key, String detail) {
        plugin.economy().credit(account, marks, new EconomyService.TransactionReason("onboarding.first-steps", detail), key)
            .exceptionally(error -> { plugin.getLogger().log(Level.WARNING, "First steps reward was not paid: " + key, error); return null; });
    }

    /** Ends the checklist without the remaining rewards. */
    public void skip(Player player) {
        State state = state(player).orElse(null);
        AccountId account = account(player);
        if (state == null || account == null || !state.active()) return;
        State next = new State(state.done(), true);
        states.put(player.getUniqueId(), next);
        save(account, next);
        Chat.info(player, "First steps hidden. " + Chat.command("start") + " still shows them, and " + Chat.command("quickstart commands") + " lists the main commands.");
    }

    // ------------------------------------------------------------------ the checklist

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25};

    public void open(Player player) {
        State state = state(player).orElse(null);
        if (state == null) { Chat.accountLoading(player); return; }
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>First Steps · " + state.done().size() + "/" + Step.values().length + "</gold>"));
        MenuKit.frame(holder.inventory);
        Step next = state.next();
        long earned = state.done().stream().mapToLong(Step::marks).sum();
        holder.inventory.setItem(4, MenuKit.item(Material.WRITABLE_BOOK, "<gold>Getting started</gold>", List.of(
            "<gray>Seven things to try, in any order.</gray>", "<gray>Each one ticks itself off when you do it.</gray>",
            "<gray>Earned so far:</gray> <gold>" + Chat.marks(earned) + "</gold>",
            "<gray>All seven:</gray> <gold>+" + Chat.marks(FINISH_MARKS) + "</gold> <gray>bonus</gray>")));
        Step[] steps = Step.values();
        for (int i = 0; i < steps.length; i++) {
            Step step = steps[i];
            boolean done = state.done().contains(step), current = step == next;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + Chat.escape(step.why()) + "</gray>");
            lore.add("<gray>" + Chat.escape(step.how()) + "</gray>");
            lore.add("<gray>Reward:</gray> <gold>" + Chat.marks(step.marks()) + "</gold>");
            lore.add(done ? "<green>✓ Done</green>" : "<yellow>Click to " + (current ? "start here" : "go") + ".</yellow>");
            String color = done ? "green" : current ? "yellow" : "white";
            holder.inventory.setItem(SLOTS[i], MenuKit.item(done ? Material.LIME_DYE : step.icon(),
                "<" + color + ">" + (i + 1) + ". " + Chat.escape(step.title()) + "</" + color + ">", lore));
            if (current) holder.inventory.getItem(SLOTS[i]).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        holder.inventory.setItem(38, MenuKit.item(Material.PAPER, "<aqua>Command list</aqua>", List.of("<gray>The handful of commands worth knowing.</gray>", "<yellow>Click to show them in chat.</yellow>")));
        holder.inventory.setItem(40, MenuKit.item(Material.BOOK, "<aqua>Lessons</aqua>", List.of("<gray>Short reads on equipment, talents,</gray>", "<gray>trading and support.</gray>", "<yellow>Click to open.</yellow>")));
        if (state.active()) holder.inventory.setItem(42, MenuKit.item(Material.BARRIER, "<red>Hide first steps</red>", List.of("<gray>Stop showing the next step.</gray>", "<gray>Remaining rewards are not paid.</gray>", "<yellow>Shift-click to hide.</yellow>")));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        boolean shift = event.isShiftClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) {
                Step step = Step.values()[i];
                if (state(player).map(s -> s.done().contains(step)).orElse(true)) return;
                player.closeInventory();
                player.performCommand(step.command());
                // A command run from here skips the chat-command event that ticks these two off.
                if (step == Step.MENU || step == Step.DAILY) complete(player, step);
                return;
            }
            switch (slot) {
                case 38 -> { player.closeInventory(); plugin.guide().commandList(player); }
                case 40 -> { player.closeInventory(); player.performCommand("tutorial"); }
                case 42 -> { if (shift) { player.closeInventory(); skip(player); } }
                case 49 -> player.closeInventory();
                default -> { }
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { states.remove(event.getPlayer().getUniqueId()); }

    @Override public void close() { if (task != null) task.cancel(); }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; Inventory inventory;
        Holder(UUID viewer) { this.viewer = viewer; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
