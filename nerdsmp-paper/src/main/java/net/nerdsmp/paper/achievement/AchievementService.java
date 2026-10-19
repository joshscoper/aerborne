package net.nerdsmp.paper.achievement;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.mob.CustomMobManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.VisualFormat;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AchievementService implements Listener {
    private static final int[] ACHIEVEMENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final ProgressionService progression;
    private final CustomMobManager mobs;
    private final PlayerDataStore store;
    private final Map<String, AchievementDefinition> definitions;
    private final Map<AccountId, ProgressState> progress = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AchievementService(
        JavaPlugin plugin,
        SessionRegistry sessions,
        EconomyService economy,
        ProgressionService progression,
        CustomMobManager mobs,
        PlayerDataStore store
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.economy = economy;
        this.progression = progression;
        this.mobs = mobs;
        this.store = store;
        definitions = AchievementCatalog.load(plugin);
    }

    public void onSessionReady(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            return;
        }
        synchronized (this) {
            ensureLoaded(account);
        }
        setAtLeast(player, AchievementMetric.FISHING_SPECIES,
            store.read(account, yaml -> {
                var entries = yaml.getConfigurationSection("collections.entries");
                if (entries == null) {
                    return 0L;
                }
                return entries.getKeys(false).stream()
                    .filter(id -> id.startsWith("fishing_") && entries.getInt(id + ".turn-ins") > 0)
                    .count();
            }));
        evaluate(player, account);
        progression.snapshot(account).whenComplete((snapshot, error) -> sync(() -> {
            if (error != null) {
                plugin.getLogger().warning("Could not refresh achievements for " + player.getName()
                    + ": " + error.getMessage());
                return;
            }
            if (player.isOnline()) {
                updateProgression(player, snapshot.skills());
            }
        }));
    }

    public void updateProgression(Player player, Map<Skill, ProgressionService.SkillProgress> skills) {
        long totalExperience = skills.values().stream().mapToLong(ProgressionService.SkillProgress::experience).sum();
        int highestLevel = skills.values().stream().mapToInt(ProgressionService.SkillProgress::level).max().orElse(0);
        int adventurerLevel = AdventurerProgression.fromSkills(skills).level();
        setAtLeast(player, AchievementMetric.TOTAL_SKILL_XP, totalExperience);
        setAtLeast(player, AchievementMetric.HIGHEST_SKILL_LEVEL, highestLevel);
        setAtLeast(player, AchievementMetric.ADVENTURER_LEVEL, adventurerLevel);
        setAtLeast(player, AchievementMetric.COOKING_LEVEL, skills.getOrDefault(Skill.COOKING, new ProgressionService.SkillProgress(0, 0)).level());
        refreshIslandMilestones(player);
    }

    public void record(Player player, AchievementMetric metric, long amount) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            return;
        }
        record(account, player, metric, amount);
    }

    public void record(AccountId account, Player player, AchievementMetric metric, long amount) {
        if (amount <= 0L) {
            return;
        }
        synchronized (this) {
            ProgressState state = ensureLoaded(account).copy();
            long current = state.metrics.getOrDefault(metric, 0L);
            state.metrics.put(metric, current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount);
            persist(account, state);
            progress.put(account, state);
        }
        evaluate(player, account);
    }

    public void setAtLeast(Player player, AchievementMetric metric, long value) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null || value < 0L) {
            return;
        }
        boolean changed;
        synchronized (this) {
            ProgressState state = ensureLoaded(account).copy();
            long current = state.metrics.getOrDefault(metric, 0L);
            changed = value > current;
            if (changed) {
                state.metrics.put(metric, value);
                persist(account, state);
                progress.put(account, state);
            }
        }
        if (changed) {
            evaluate(player, account);
        }
    }

    public void open(Player player) {
        refreshIslandMilestones(player);
        open(player, "All", 0, false);
    }
    public synchronized int points(AccountId account) {
        return ensureLoaded(account).unlocked.stream().map(definitions::get).filter(java.util.Objects::nonNull).mapToInt(AchievementDefinition::points).sum();
    }
    private void refreshIslandMilestones(Player player) {
        if (!(plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core)) return;
        var account = sessions.account(player.getUniqueId()).orElse(null); if (account == null) return;
        setAtLeast(player, AchievementMetric.ACTIVE_SET_BONUSES, core.gearSets().profile(player).activeBonuses().size());
        if (core.skyblock() != null) {
            var owner = core.skyblock().activityIsland(player);
            if (owner == null && core.skyblock().islands().owned(account).isPresent()) owner = account;
            if (owner != null) {
                var island = core.skyblock().development().progress(owner);
                setAtLeast(player, AchievementMetric.ISLAND_LEVEL, island.level());
                setAtLeast(player, AchievementMetric.ISLAND_TALENTS, island.talents().values().stream().mapToLong(Integer::longValue).sum());
                setAtLeast(player, AchievementMetric.RESOURCE_STAGE, core.skyblock().resources().effectiveStage(owner));
            }
        }
    }
    private void open(Player player, String category, int requestedPage, boolean unfinishedOnly) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        ProgressState state;
        synchronized (this) {
            state = ensureLoaded(account).copy();
        }
        AchievementHolder holder = new AchievementHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54,
            miniMessage.deserialize("<gradient:#ffd166:#b38cff><bold>Achievements</bold></gradient>"));
        holder.inventory = inventory;
        holder.owner = player.getUniqueId();
        decorate(inventory);
        int earnedPoints = state.unlocked.stream().map(definitions::get)
            .filter(java.util.Objects::nonNull).mapToInt(AchievementDefinition::points).sum();
        inventory.setItem(4, icon(Material.NETHER_STAR, "<gold><bold>Achievement Record</bold></gold>", List.of(
            "<gray>Completed</gray> <white>" + state.unlocked.stream().filter(definitions::containsKey).count() + "/" + definitions.size() + "</white>",
            "<gray>Achievement Points</gray> <gold>" + earnedPoints + "</gold>",
            "<dark_gray>Progress and rewards are account-wide.</dark_gray>"
        ), true));
        List<AchievementDefinition> ordered = definitions.values().stream()
            .filter(d -> category.equals("All") || d.category().equals(category))
            .filter(d -> !unfinishedOnly || !state.unlocked.contains(d.id()))
            .sorted(Comparator.comparing((AchievementDefinition d) -> state.unlocked.contains(d.id()))
                .thenComparing(AchievementDefinition::category).thenComparing(d -> d.metric().id()).thenComparingLong(AchievementDefinition::target))
            .toList();
        int page = Math.clamp(requestedPage, 0, Math.max(0, (ordered.size() - 1) / ACHIEVEMENT_SLOTS.length));
        List<String> categories = new ArrayList<>(); categories.add("All"); definitions.values().stream().map(AchievementDefinition::category).distinct().sorted().forEach(categories::add);
        inventory.setItem(0, icon(Material.BOOKSHELF, "<aqua>" + category + "</aqua>", MenuKit.withHint(List.of("<gray>Chapter shown now.</gray>"), "choose the next chapter"), false));
        holder.actions.put(0, () -> open(player, categories.get((Math.max(0, categories.indexOf(category)) + 1) % categories.size()), 0, unfinishedOnly));
        inventory.setItem(8, icon(Material.COMPARATOR, "<aqua>" + (unfinishedOnly ? "Unfinished only" : "All milestones") + "</aqua>", MenuKit.withHint(List.of("<gray>Filter shown now.</gray>"), "change the filter"), false));
        holder.actions.put(8, () -> open(player, category, 0, !unfinishedOnly));
        for (int offset = 0; offset < ACHIEVEMENT_SLOTS.length && page * ACHIEVEMENT_SLOTS.length + offset < ordered.size(); offset++) {
            int index = page * ACHIEVEMENT_SLOTS.length + offset;
            AchievementDefinition definition = ordered.get(index);
            long current = Math.min(definition.target(), state.metrics.getOrDefault(definition.metric(), 0L));
            boolean unlocked = state.unlocked.contains(definition.id());
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + definition.category().toUpperCase(java.util.Locale.ROOT) + "</dark_gray>");
            lore.addAll(wrap(definition.description(), 38).stream().map(line -> "<gray>" + line + "</gray>").toList());
            lore.add("");
            lore.add(VisualFormat.progressBar(current, definition.target(), 10)
                + " <white>" + VisualFormat.compact(current) + "/"
                + VisualFormat.compact(definition.target()) + "</white>");
            lore.add("<gray>Worth</gray> <gold>" + definition.points() + " points</gold>"
                + (definition.markReward() > 0L ? " <dark_gray>•</dark_gray> "
                + Chat.marksTag(definition.markReward()) : ""));
            lore.add(unlocked ? "<green>Completed</green>" : "<yellow>In progress</yellow>");
            inventory.setItem(ACHIEVEMENT_SLOTS[offset], icon(definition.icon(),
                unlocked ? "<green><bold>✓ " + definition.title() + "</bold></green>"
                    : "<white>" + definition.title() + "</white>", lore, unlocked));
        }
        int pages = Math.max(1, (ordered.size() + 20) / 21);
        inventory.setItem(40, icon(Material.PAPER, "<white>Page " + (page + 1) + "/" + pages + "</white>", List.of("<gray>Rewards are granted once per account.</gray>", "<gray>Older achievements stay in your record.</gray>"), false));
        if (page > 0) { inventory.setItem(MenuKit.previousSlot(54), MenuKit.previous(page, pages)); holder.actions.put(MenuKit.previousSlot(54), () -> open(player, category, page - 1, unfinishedOnly)); }
        if ((page + 1) * 21 < ordered.size()) { inventory.setItem(MenuKit.nextSlot(54), MenuKit.next(page, pages)); holder.actions.put(MenuKit.nextSlot(54), () -> open(player, category, page + 1, unfinishedOnly)); }
        if (plugin instanceof NerdSmpPlugin nerd) {
            inventory.setItem(MenuKit.backSlot(54), MenuKit.back("Adventure"));
            holder.actions.put(MenuKit.backSlot(54), () -> nerd.menus().open(player, MenuManager.View.ADVENTURE));
        }
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getGameMode() != org.bukkit.GameMode.SURVIVAL || mobs.isMechanicObjective(event.getEntity())
            || (!(event.getEntity() instanceof Enemy) && !mobs.isCustomMob(event.getEntity())
            && !mobs.isBoss(event.getEntity()))) {
            return;
        }
        record(killer, AchievementMetric.MOBS_DEFEATED, 1L);
        if (mobs.isElite(event.getEntity())) {
            record(killer, AchievementMetric.ELITES_DEFEATED, 1L);
        }
        if (mobs.isBoss(event.getEntity())) {
            record(killer, AchievementMetric.BOSSES_DEFEATED, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // Accepted catch receipts are recorded by FishingService, not by raw fish events.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && player.getGameMode() == org.bukkit.GameMode.SURVIVAL && event.getCurrentItem() != null) {
            var result = event.getCurrentItem();
            var receipt = new net.nerdsmp.paper.item.CraftingReceipt(event.getInventory(), player, result, event.isShiftClick());
            var account = sessions.account(player.getUniqueId()).orElse(null);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!event.isCancelled() && player.isOnline() && player.getGameMode() == org.bukkit.GameMode.SURVIVAL
                    && account != null && sessions.account(player.getUniqueId()).filter(account::equals).isPresent()) record(player, AchievementMetric.ITEMS_CRAFTED, (long) receipt.crafts(event.getInventory(), player) * result.getAmount());
            });
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AchievementHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && player.getUniqueId().equals(holder.owner) && event.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
            if (event.getRawSlot() == MenuKit.closeSlot(54)) { player.closeInventory(); return; }
            Runnable action = holder.actions.get(event.getRawSlot());
            if (action != null) MenuKit.clickSound(player);
            if (action != null) Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline() && player.getOpenInventory().getTopInventory() == holder.inventory) action.run(); });
        }
    }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof AchievementHolder) event.setCancelled(true); }

    private void evaluate(Player player, AccountId account) {
        List<AchievementDefinition> ready;
        synchronized (this) {
            ProgressState state = ensureLoaded(account);
            ready = definitions.values().stream().filter(definition ->
                !state.unlocked.contains(definition.id())
                    && state.metrics.getOrDefault(definition.metric(), 0L) >= definition.target()
                    && pending.add(account + ":" + definition.id())
            ).toList();
        }
        ready.forEach(definition -> award(player, account, definition));
    }

    private void award(Player player, AccountId account, AchievementDefinition definition) {
        economy.credit(
            account,
            definition.markReward(),
            new EconomyService.TransactionReason("achievement.reward", definition.title()),
            "achievement:" + account + ":" + definition.id()
        ).whenComplete((result, error) -> sync(() -> {
            String pendingKey = account + ":" + definition.id();
            if (error != null || result == null
                || (result.status() != EconomyService.TransactionResult.Status.APPLIED
                && result.status() != EconomyService.TransactionResult.Status.DUPLICATE)) {
                synchronized (this) {
                    pending.remove(pendingKey);
                }
                plugin.getLogger().warning("Could not grant achievement " + definition.id() + " to " + account
                    + ": " + (error == null ? "rejected reward" : error.getMessage()));
                return;
            }
            boolean newlyUnlocked;
            synchronized (this) {
                ProgressState state = ensureLoaded(account).copy();
                newlyUnlocked = state.unlocked.add(definition.id());
                pending.remove(pendingKey);
                if (newlyUnlocked) {
                    try { persist(account, state); progress.put(account, state); }
                    catch (RuntimeException failure) {
                        plugin.getLogger().warning("Achievement receipt could not be saved for " + account + "; its idempotent reward will be reconciled on retry.");
                        return;
                    }
                }
            }
            Player online = player != null && player.isOnline() ? player
                : sessions.player(account).map(Bukkit::getPlayer).orElse(null);
            if (newlyUnlocked && online != null && online.isOnline() && sessions.account(online.getUniqueId()).filter(account::equals).isPresent()) {
                online.showTitle(Title.title(
                    miniMessage.deserialize("<gold><bold>Achievement Unlocked</bold></gold>"),
                    miniMessage.deserialize("<white>" + escape(definition.title()) + "</white>" + (definition.markReward() > 0L
                        ? " <dark_gray>•</dark_gray> <gold>+" + Chat.marks(definition.markReward()) + "</gold>" : "")),
                    Title.Times.times(Duration.ofMillis(200L), Duration.ofSeconds(2L), Duration.ofMillis(500L))
                ));
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.1F);
            }
        }));
    }

    private ProgressState ensureLoaded(AccountId account) {
        return progress.computeIfAbsent(account, ignored -> store.read(account, yaml -> {
            EnumMap<AchievementMetric, Long> metrics = new EnumMap<>(AchievementMetric.class);
            var section = yaml.getConfigurationSection("achievements.metrics");
            if (section != null) {
                for (String raw : section.getKeys(false)) {
                    AchievementMetric metric = AchievementMetric.fromId(raw);
                    if (metric != null) {
                        metrics.put(metric, Math.max(0L, section.getLong(raw)));
                    }
                }
            }
            Set<String> unlocked = new LinkedHashSet<>(yaml.getStringList("achievements.unlocked"));
            return new ProgressState(metrics, unlocked);
        }));
    }

    private void persist(AccountId account, ProgressState state) {
        store.update(account, yaml -> {
            yaml.set("achievements.metrics", null);
            state.metrics.forEach((metric, value) ->
                yaml.set("achievements.metrics." + metric.id(), value));
            yaml.set("achievements.unlocked", state.unlocked.stream().sorted().toList());
        });
    }

    private void decorate(Inventory inventory) {
        MenuKit.frame(inventory);
    }

    private ItemStack icon(Material material, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.displayName(null);
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line)
            .decoration(TextDecoration.ITALIC, false)).toList());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(glint);
        item.setItemMeta(meta);
        return item;
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static final class ProgressState {
        private final EnumMap<AchievementMetric, Long> metrics;
        private final Set<String> unlocked;

        private ProgressState(EnumMap<AchievementMetric, Long> metrics, Set<String> unlocked) {
            this.metrics = metrics;
            this.unlocked = unlocked;
        }

        private ProgressState copy() {
            return new ProgressState(new EnumMap<>(metrics), new LinkedHashSet<>(unlocked));
        }
    }

    private static final class AchievementHolder implements InventoryHolder {
        private Inventory inventory;
        private java.util.UUID owner;
        private final Map<Integer, Runnable> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
