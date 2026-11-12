package net.nerdsmp.paper.collection;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import net.nerdsmp.paper.presentation.VisualFormat;
import net.nerdsmp.paper.skyblock.IslandFarmstead;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Collections, after Hypixel SkyBlock's: every natural ore mined, log felled, fish caught and beast slain counts toward
 * that item's collection, and each tier pays Marks, skill XP and Favour. Farming collections are the Farmstead's crop
 * milestones. Bringing a whole category to tier III earns a title, and enough tiers in all bring a collector to visit.
 * Only natural blocks count: the resource ledger turns away anything a player placed.
 */
public final class Collections implements Listener {
    public enum Category {
        FARMING("Farming", Material.GOLDEN_HOE, Skill.FARMING, "Harvest Keeper"),
        MINING("Mining", Material.IRON_PICKAXE, Skill.MINING, "Stoneheart"),
        FORAGING("Foraging", Material.IRON_AXE, Skill.FORESTRY, "Timberwarden"),
        FISHING("Fishing", Material.FISHING_ROD, Skill.FISHING, "Tidecaller"),
        COMBAT("Combat", Material.IRON_SWORD, Skill.HUNTING, "Beastbane");

        final String title, honour; final Material icon; final Skill skill;
        Category(String title, Material icon, Skill skill, String honour) { this.title = title; this.icon = icon; this.skill = skill; this.honour = honour; }
        public String title() { return title; }
        /** The cosmetic key (and title slug) earned for bringing every collection here to tier III. */
        public String key() { return "collection:" + name().toLowerCase(Locale.ROOT); }
        public String honour() { return honour; }
    }

    /** Tier thresholds by how common the thing is. */
    static final int[] BULK = {500, 2500, 10000, 50000, 200000};
    static final int[] COMMON = {50, 250, 1000, 5000, 20000};
    static final int[] LOGS = {100, 500, 2500, 10000, 50000};
    static final int[] PRECIOUS = {10, 50, 200, 1000, 5000};
    static final int[] CATCHES = {25, 100, 500, 2000, 10000};
    static final int[] KILLS = {50, 250, 1000, 5000, 25000};
    /** The tier every collection in a category must reach for its title. */
    public static final int TITLE_TIER = 3;
    /** Collection tiers, all told, that bring Collector Pell to visit. */
    public static final int COLLECTOR_TIERS = 15;

    /** One collection: what it counts and the tiers it climbs. Farming ones read the Farmstead's crop milestones. */
    public record Entry(String id, Category category, String title, Material icon, int[] tiers, long marksBase, IslandFarmstead.Crop crop) {
        public int tier(long count) {
            int[] steps = crop != null ? IslandFarmsteadTiers.TIERS : tiers;
            int tier = 0;
            for (int need : steps) if (count >= need) tier++;
            return tier;
        }
        public int maxTier() { return (crop != null ? IslandFarmsteadTiers.TIERS : tiers).length; }
        public int next(int tier) { int[] steps = crop != null ? IslandFarmsteadTiers.TIERS : tiers; return tier < steps.length ? steps[tier] : -1; }
        @Override public boolean equals(Object other) { return other instanceof Entry e && e.id.equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return id; }
    }

    /** Mirrors the Farmstead's crop milestone tiers for the farming collections. */
    static final class IslandFarmsteadTiers { static final int[] TIERS = {100, 500, 1500, 5000, 15000, 50000}; }

    static final List<Entry> ENTRIES;
    /** Which collection a broken block, caught fish or slain beast counts toward. */
    static final Map<Material, Entry> BY_BLOCK = new LinkedHashMap<>();
    static final Map<Material, Entry> BY_CATCH = new LinkedHashMap<>();
    static final Map<EntityType, Entry> BY_KILL = new LinkedHashMap<>();
    static final Entry RARE_CATCHES, REACH_BEASTS;

    static {
        List<Entry> all = new ArrayList<>();
        for (IslandFarmstead.Crop crop : IslandFarmstead.Crop.values())
            all.add(new Entry("crop_" + crop.name().toLowerCase(Locale.ROOT), Category.FARMING, crop.title(), crop.produce(), null, 0, crop));
        mine(all, "cobblestone", "Cobblestone", Material.COBBLESTONE, BULK, 5, Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE);
        mine(all, "coal", "Coal", Material.COAL, COMMON, 20, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
        mine(all, "copper", "Copper", Material.RAW_COPPER, COMMON, 20, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        mine(all, "iron", "Iron", Material.RAW_IRON, COMMON, 30, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        mine(all, "gold", "Gold", Material.RAW_GOLD, COMMON, 40, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE);
        mine(all, "lapis", "Lapis", Material.LAPIS_LAZULI, COMMON, 30, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        mine(all, "redstone", "Redstone", Material.REDSTONE, COMMON, 30, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        mine(all, "quartz", "Quartz", Material.QUARTZ, COMMON, 30, Material.NETHER_QUARTZ_ORE);
        mine(all, "obsidian", "Obsidian", Material.OBSIDIAN, COMMON, 40, Material.OBSIDIAN);
        mine(all, "amethyst", "Amethyst", Material.AMETHYST_SHARD, COMMON, 40, Material.AMETHYST_CLUSTER);
        mine(all, "diamond", "Diamond", Material.DIAMOND, PRECIOUS, 120, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        mine(all, "emerald", "Emerald", Material.EMERALD, PRECIOUS, 150, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        for (String wood : List.of("oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "cherry", "mangrove")) {
            Material log = Material.valueOf(wood.toUpperCase(Locale.ROOT) + "_LOG");
            Entry entry = new Entry("log_" + wood, Category.FORAGING, VisualFormat.titleCase(wood.replace('_', ' ')) + " Log", log, LOGS, 15, null);
            all.add(entry);
            BY_BLOCK.put(log, entry);
            Material wood6 = Material.matchMaterial(wood.toUpperCase(Locale.ROOT) + "_WOOD");
            if (wood6 != null) BY_BLOCK.put(wood6, entry);
        }
        catchOf(all, "cod", "Cod", Material.COD, 15);
        catchOf(all, "salmon", "Salmon", Material.SALMON, 20);
        catchOf(all, "pufferfish", "Pufferfish", Material.PUFFERFISH, 30);
        catchOf(all, "tropical_fish", "Tropical Fish", Material.TROPICAL_FISH, 30);
        RARE_CATCHES = new Entry("rare_catches", Category.FISHING, "Rare Catches", Material.NAUTILUS_SHELL, PRECIOUS, 120, null);
        all.add(RARE_CATCHES);
        kill(all, "zombie", "Zombie", Material.ROTTEN_FLESH, EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER);
        kill(all, "skeleton", "Skeleton", Material.BONE, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED);
        kill(all, "spider", "Spider", Material.STRING, EntityType.SPIDER, EntityType.CAVE_SPIDER);
        kill(all, "creeper", "Creeper", Material.GUNPOWDER, EntityType.CREEPER);
        kill(all, "enderman", "Enderman", Material.ENDER_PEARL, EntityType.ENDERMAN);
        kill(all, "slime", "Slime", Material.SLIME_BALL, EntityType.SLIME, EntityType.MAGMA_CUBE);
        kill(all, "blaze", "Blaze", Material.BLAZE_ROD, EntityType.BLAZE, EntityType.BREEZE);
        kill(all, "witch", "Witch", Material.GLASS_BOTTLE, EntityType.WITCH);
        REACH_BEASTS = new Entry("reach_beasts", Category.COMBAT, "Reach Beasts", Material.LEATHER, KILLS, 40, null);
        all.add(REACH_BEASTS);
        ENTRIES = List.copyOf(all);
    }

    private static void mine(List<Entry> all, String id, String title, Material icon, int[] tiers, long marks, Material... blocks) {
        Entry entry = new Entry("ore_" + id, Category.MINING, title, icon, tiers, marks, null);
        all.add(entry);
        for (Material block : blocks) BY_BLOCK.put(block, entry);
    }

    private static void catchOf(List<Entry> all, String id, String title, Material fish, long marks) {
        Entry entry = new Entry("fish_" + id, Category.FISHING, title, fish, CATCHES, marks, null);
        all.add(entry);
        BY_CATCH.put(fish, entry);
    }

    private static void kill(List<Entry> all, String id, String title, Material icon, EntityType... types) {
        Entry entry = new Entry("kill_" + id, Category.COMBAT, title, icon, KILLS, 20, null);
        all.add(entry);
        for (EntityType type : types) BY_KILL.put(type, entry);
    }

    /** What reaching a tier pays: Marks growing with the tier's square, skill XP and Favour. */
    static long marks(Entry entry, int tier) { return entry.marksBase() * tier * tier; }
    static long skillXp(int tier) { return 100L * tier * tier; }
    static long favour(int tier) { return 25L * tier; }

    public static Entry entry(String id) { return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null); }

    // ------------------------------------------------------------------ storage

    private final NerdSmpPlugin plugin;
    private final String path;

    public Collections(NerdSmpPlugin plugin, String season) {
        this.plugin = plugin;
        this.path = "seasons." + season + ".collections.";
    }

    public long count(AccountId account, Entry entry) {
        if (entry.crop() != null) return plugin.skyblock() == null ? 0 : plugin.skyblock().farmstead().harvested(account, entry.crop());
        return plugin.playerData().read(account, yaml -> yaml.getLong(path + entry.id(), 0L));
    }

    /** Every collection tier the player has reached, all told. */
    public int totalTiers(AccountId account) {
        int total = 0;
        for (Entry entry : ENTRIES) total += entry.tier(count(account, entry));
        return total;
    }

    private void add(Player player, Entry entry, long amount) {
        if (entry == null || entry.crop() != null || amount <= 0) return;
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) return;
        long[] now = {0};
        plugin.playerData().update(account, yaml -> {
            now[0] = yaml.getLong(path + entry.id(), 0L) + amount;
            yaml.set(path + entry.id(), now[0]);
        });
        if (plugin.festivals() != null) plugin.festivals().progress(player, (int) amount);
        int before = entry.tier(now[0] - amount), after = entry.tier(now[0]);
        for (int tier = before + 1; tier <= after; tier++) reached(player, account, entry, tier);
    }

    private void reached(Player player, AccountId account, Entry entry, int tier) {
        long marks = Math.round(marks(entry, tier) * (plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.COLLECTORS_ZEAL) ? 1.5 : 1.0)), favour = favour(tier), xp = skillXp(tier);
        plugin.economy().credit(account, marks, new EconomyService.TransactionReason("collection", entry.title() + " collection " + roman(tier)),
            "collection:" + account.value() + ":" + entry.id() + ":" + tier);
        if (plugin.skyblock() != null && plugin.skyblock().guests() != null) plugin.skyblock().guests().addFavour(account, favour);
        if (plugin.skills() != null) plugin.skills().awardMilestone(player, entry.category().skill, xp, "collection:" + entry.id() + ":" + tier);
        Chat.send(player, "<gold>✦ " + Chat.escape(entry.title()) + " collection " + roman(tier) + "!</gold> <gold>+" + Chat.marks(marks)
            + "</gold> <aqua>+" + xp + " " + VisualFormat.titleCase(entry.category().skill.name().toLowerCase(Locale.ROOT)) + " XP</aqua> <yellow>+" + favour + " Favour</yellow>");
        ClientFeeds.toast(player, "achievement", Chat.render("<gold>Collection " + roman(tier) + "</gold>"), Chat.render("<white>" + Chat.escape(entry.title()) + "</white>"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.6F);
        checkTitle(player, account, entry.category());
    }

    /** Grants a category's title once every collection in it reaches tier III. */
    public void checkTitle(Player player, AccountId account, Category category) {
        for (Entry entry : ENTRIES) if (entry.category() == category && entry.tier(count(account, entry)) < TITLE_TIER) return;
        if (plugin.cosmetics() == null) return;
        boolean[] first = {false};
        plugin.playerData().update(account, yaml -> {
            first[0] = !yaml.getBoolean(path + "titles." + category.name(), false);
            yaml.set(path + "titles." + category.name(), true);
        });
        plugin.cosmetics().earn(player, List.of(category.key()));
        if (first[0]) Chat.send(player, "<light_purple>✦ Every " + category.title + " collection at tier III: you've earned the title</light_purple> <white>" + category.honour + "</white><light_purple>!</light_purple>");
    }

    // ------------------------------------------------------------------ counting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Entry entry = BY_BLOCK.get(block.getType());
        if (entry == null) return;
        if (plugin.resources() != null) {
            boolean natural = entry.category() == Category.FORAGING ? plugin.resources().naturalLog(block) : plugin.resources().cropRewardEligible(block);
            if (!natural) return;
        }
        add(event.getPlayer(), entry, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) return;
        ItemStack stack = caught.getItemStack();
        boolean custom = plugin.items() != null && plugin.items().describe(stack).isPresent();
        add(event.getPlayer(), custom ? RARE_CATCHES : BY_CATCH.get(stack.getType()), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player) return;
        boolean custom = plugin.mobs() != null && plugin.mobs().isCustomMob(dead);
        add(killer, custom ? REACH_BEASTS : BY_KILL.get(dead.getType()), 1);
    }

    // ------------------------------------------------------------------ menus

    static String roman(int n) { return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI"; default -> "0"; }; }

    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};
    private static final int[] ENTRY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public void open(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        Holder holder = new Holder(player.getUniqueId(), null);
        int total = totalTiers(account), max = ENTRIES.stream().mapToInt(Entry::maxTier).sum();
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Collections · " + total + "/" + max + "</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.BOOKSHELF, "<gold>Collections</gold>", List.of(
            "<white>" + total + "/" + max + "</white> <gray>tiers reached.</gray>",
            "<gray>Natural ore, logs, fish, beasts and crops all count.</gray>",
            "<gray>Each tier pays Marks, skill XP and Favour.</gray>",
            "<gray>Every collection in a category at tier III earns a title.</gray>",
            total >= COLLECTOR_TIERS ? "<green>Collector Pell visits your island.</green>" : "<gray>At " + COLLECTOR_TIERS + " tiers, Collector Pell visits your island.</gray>")));
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            List<Entry> entries = ENTRIES.stream().filter(e -> e.category() == category).toList();
            int reached = 0, possible = 0, atTitle = 0;
            for (Entry entry : entries) {
                int tier = entry.tier(count(account, entry));
                reached += tier; possible += entry.maxTier();
                if (tier >= TITLE_TIER) atTitle++;
            }
            holder.inventory.setItem(CATEGORY_SLOTS[i], MenuKit.item(category.icon, "<yellow>" + category.title + "</yellow>", List.of(
                VisualFormat.progressBar(reached, possible, 10) + " <white>" + reached + "/" + possible + "</white>",
                "<gray>Title</gray> <white>" + category.honour + "</white><gray>: " + atTitle + "/" + entries.size() + " at tier III</gray>",
                "<yellow>Click to open.</yellow>")));
        }
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    void openCategory(Player player, Category category) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        Holder holder = new Holder(player.getUniqueId(), category);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>" + category.title + " Collections</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(category.icon, "<gold>" + category.title + "</gold>", List.of(
            category == Category.FARMING ? "<gray>Ripe crops harvested on islands (crop milestones).</gray>"
                : category == Category.MINING ? "<gray>Natural ore and stone mined.</gray>"
                : category == Category.FORAGING ? "<gray>Natural logs felled.</gray>"
                : category == Category.FISHING ? "<gray>Fish caught; custom catches count as Rare Catches.</gray>"
                : "<gray>Beasts slain; the Reach's own beasts count together.</gray>",
            "<gray>All at tier III earns the title</gray> <white>" + category.honour + "</white><gray>.</gray>")));
        List<Entry> entries = ENTRIES.stream().filter(e -> e.category() == category).toList();
        for (int i = 0; i < entries.size() && i < ENTRY_SLOTS.length; i++) {
            Entry entry = entries.get(i);
            long have = count(account, entry);
            int tier = entry.tier(have), next = entry.next(tier);
            List<String> lore = new ArrayList<>();
            lore.add(next < 0 ? "<green>Maxed: " + have + "</green>" : VisualFormat.progressBar(have, next, 10) + " <white>" + have + "/" + next + "</white>");
            lore.add("<gray>Tier</gray> <white>" + roman(tier) + "/" + roman(entry.maxTier()) + "</white>");
            if (next >= 0 && entry.crop() == null) lore.add("<gray>Next:</gray> <gold>" + Chat.marks(marks(entry, tier + 1)) + "</gold> <aqua>+" + skillXp(tier + 1) + " XP</aqua> <yellow>+" + favour(tier + 1) + " Favour</yellow>");
            if (entry.crop() != null) lore.add("<gray>Rewards come from the Farmstead's crop milestones.</gray>");
            ItemStack icon = MenuKit.item(have > 0 ? entry.icon() : Material.GRAY_DYE, (tier > 0 ? "<green>" : "<white>") + Chat.escape(entry.title()) + " " + roman(tier) + (tier > 0 ? "</green>" : "</white>"), lore);
            icon.setAmount(Math.max(1, tier));
            holder.inventory.setItem(ENTRY_SLOTS[i], icon);
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>All collections</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            if (holder.category == null) {
                for (int i = 0; i < CATEGORY_SLOTS.length; i++) if (CATEGORY_SLOTS[i] == slot) { openCategory(player, Category.values()[i]); return; }
                if (slot == 49) player.closeInventory();
            } else if (slot == 45) open(player);
            else if (slot == 49) player.closeInventory();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; final Category category; Inventory inventory;
        Holder(UUID viewer, Category category) { this.viewer = viewer; this.category = category; }
        @Override public Inventory getInventory() { return inventory; }
    }

    static Set<Material> countedBlocks() { return BY_BLOCK.keySet(); }
    static List<Integer> tiersOf(Entry entry) { return Arrays.stream(entry.tiers() == null ? IslandFarmsteadTiers.TIERS : entry.tiers()).boxed().toList(); }
    static Map<Category, Integer> sizes() {
        Map<Category, Integer> sizes = new EnumMap<>(Category.class);
        for (Entry entry : ENTRIES) sizes.merge(entry.category(), 1, Integer::sum);
        return sizes;
    }
}
