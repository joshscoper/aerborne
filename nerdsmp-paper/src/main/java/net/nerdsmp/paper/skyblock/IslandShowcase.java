package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Locale;

/**
 * Earned, deterministic display unlocks. Never an inventory item or a loot roll. The first four come from the land
 * chapters; the rest from island levels and resource stages, so every kind of island progress shows above the landing.
 */
public enum IslandShowcase {
    HEARTH("Skybound Ember", "FOUNDATION", Kind.CHAPTER, 0, Material.LANTERN, NamedTextColor.GOLD),
    VERDANT("Verdant Heart", "RARE", Kind.CHAPTER, 1, Material.FLOWERING_AZALEA, NamedTextColor.GREEN),
    ARCANE("Arcane Prism", "EPIC", Kind.CHAPTER, 2, Material.AMETHYST_CLUSTER, NamedTextColor.LIGHT_PURPLE),
    CELESTIAL("Celestial Star", "LEGENDARY", Kind.CHAPTER, 3, Material.NETHER_STAR, NamedTextColor.AQUA),
    HARVEST("Harvest Sheaf", "COMMON", Kind.LEVEL, 5, Material.HAY_BLOCK, NamedTextColor.YELLOW),
    TIDECALLER("Tidecaller Shell", "UNCOMMON", Kind.LEVEL, 10, Material.NAUTILUS_SHELL, NamedTextColor.DARK_AQUA),
    HIVE("Honeyed Hive", "UNCOMMON", Kind.LEVEL, 15, Material.BEE_NEST, NamedTextColor.GOLD),
    ANVIL("Smith's Anvil", "RARE", Kind.LEVEL, 20, Material.ANVIL, NamedTextColor.GRAY),
    SEA_HEART("Heart of the Sea", "RARE", Kind.LEVEL, 25, Material.HEART_OF_THE_SEA, NamedTextColor.AQUA),
    BELL("Sentinel Bell", "EPIC", Kind.LEVEL, 30, Material.BELL, NamedTextColor.GOLD),
    TOTEM("Totem of the Isles", "EPIC", Kind.LEVEL, 35, Material.TOTEM_OF_UNDYING, NamedTextColor.GREEN),
    CONDUIT("Deepwater Conduit", "EPIC", Kind.LEVEL, 40, Material.CONDUIT, NamedTextColor.DARK_AQUA),
    BEACON("Reach Beacon", "LEGENDARY", Kind.LEVEL, 45, Material.BEACON, NamedTextColor.AQUA),
    WYRM_EGG("Wyrm's Egg", "MYTHIC", Kind.LEVEL, 50, Material.DRAGON_EGG, NamedTextColor.DARK_PURPLE),
    LODESTONE("Foundry Lodestone", "RARE", Kind.STAGE, 4, Material.LODESTONE, NamedTextColor.GRAY),
    AETHER_CRYSTAL("Aether Crystal", "LEGENDARY", Kind.STAGE, 5, Material.END_CRYSTAL, NamedTextColor.LIGHT_PURPLE),
    // Achievement milestones: the best achievement score of anyone in the co-op.
    SPYGLASS("Pathfinder's Spyglass", "UNCOMMON", Kind.ACHIEVEMENT, 100, Material.SPYGLASS, NamedTextColor.YELLOW),
    GILDED_CLOCK("Gilded Clock", "RARE", Kind.ACHIEVEMENT, 300, Material.CLOCK, NamedTextColor.GOLD),
    CROWN_OF_DEEDS("Crown of Deeds", "LEGENDARY", Kind.ACHIEVEMENT, 600, Material.GOLDEN_HELMET, NamedTextColor.GOLD),
    // Quest chains of the Reach, finished by anyone in the co-op.
    CHARTER_MAP("Skyfarer's Charter", "UNCOMMON", Kind.QUEST, "reach_prologue", Material.FILLED_MAP, NamedTextColor.AQUA),
    EMBER_CROWN("The Scarecrow's Crown", "EPIC", Kind.QUEST, "quiet_fields", Material.CARVED_PUMPKIN, NamedTextColor.GOLD),
    ORCHARD_APPLE("Brindle Gold", "RARE", Kind.QUEST, "brindle_orchards", Material.GOLDEN_APPLE, NamedTextColor.YELLOW),
    THISTLE_GRINDSTONE("Thistle Millstone", "UNCOMMON", Kind.QUEST, "thistle_farm", Material.GRINDSTONE, NamedTextColor.GRAY),
    BOUNTY_HORN("Hunter's Horn", "RARE", Kind.QUEST, "vale_bounties", Material.GOAT_HORN, NamedTextColor.RED),
    // Expeditions cleared by anyone in the co-op, on any tier.
    GUILD_PENNANT("Guild Pennant", "UNCOMMON", Kind.EXPEDITION, "first_flight", Material.WHITE_BANNER, NamedTextColor.WHITE),
    BARROW_SKULL("Huntsman's Skull", "RARE", Kind.EXPEDITION, "hollow_barrow", Material.SKELETON_SKULL, NamedTextColor.GRAY),
    AQUEDUCT_TRIDENT("Aqueduct Trident", "RARE", Kind.EXPEDITION, "sunken_aqueduct", Material.TRIDENT, NamedTextColor.DARK_AQUA),
    GLOOMFORGE_ORE("Gloomforge Ore", "EPIC", Kind.EXPEDITION, "gloomforge_mines", Material.ANCIENT_DEBRIS, NamedTextColor.DARK_RED),
    THORNWOOD_BLOSSOM("Thornwood Blossom", "EPIC", Kind.EXPEDITION, "thornwood_canopy", Material.SPORE_BLOSSOM, NamedTextColor.LIGHT_PURPLE),
    CINDER_HEART("Cinder Heart", "LEGENDARY", Kind.EXPEDITION, "cinder_keep", Material.MAGMA_BLOCK, NamedTextColor.RED),
    WARDENS_ECHO("Wardens' Echo", "MYTHIC", Kind.EXPEDITION, "hall_of_wardens", Material.ECHO_SHARD, NamedTextColor.DARK_AQUA),
    // Island visitors served by the co-op (IslandGuests).
    GUEST_BELL("Welcome Bell", "UNCOMMON", Kind.GUESTS, 10, Material.BELL, NamedTextColor.YELLOW),
    GUESTBOOK("Golden Guestbook", "RARE", Kind.GUESTS, 50, Material.WRITTEN_BOOK, NamedTextColor.GOLD),
    WAYFARER_LANTERN("Wayfarer's Lantern", "EPIC", Kind.GUESTS, 200, Material.SOUL_LANTERN, NamedTextColor.AQUA),
    // The Workers' Lodge: different worker tiers the co-op has reached.
    LODGE_SIGN("Lodge Sign", "UNCOMMON", Kind.WORKERS, 8, Material.OAK_HANGING_SIGN, NamedTextColor.YELLOW),
    FOREMANS_LEDGER("Foreman's Ledger", "RARE", Kind.WORKERS, 25, Material.WRITABLE_BOOK, NamedTextColor.GOLD),
    GOLDEN_PICK("Golden Pick of the Guild", "LEGENDARY", Kind.WORKERS, 50, Material.GOLDEN_PICKAXE, NamedTextColor.GOLD),
    // Thunderhide, the world boss: the most kills by anyone in the co-op.
    STORM_FANG("Storm Fang", "RARE", Kind.BOSS, 1, Material.PRISMARINE_SHARD, NamedTextColor.AQUA),
    LIGHTNING_ROD("Rod of the Rings", "EPIC", Kind.BOSS, 10, Material.LIGHTNING_ROD, NamedTextColor.BLUE),
    THUNDER_HEART("Thunderhide's Heart", "MYTHIC", Kind.BOSS, 25, Material.HEAVY_CORE, NamedTextColor.DARK_AQUA),
    // Prestige stars, earned with island XP past level 50.
    PRESTIGE_STAR("Prestige Star", "EPIC", Kind.PRESTIGE, 1, Material.FIREWORK_STAR, NamedTextColor.GOLD),
    ASCENDED_LANTERN("Lantern of the Ascended", "LEGENDARY", Kind.PRESTIGE, 5, Material.SEA_LANTERN, NamedTextColor.AQUA),
    STARFALL_CROWN("Starfall Crown", "MYTHIC", Kind.PRESTIGE, 10, Material.NETHERITE_HELMET, NamedTextColor.LIGHT_PURPLE);

    /** What earns a showcase: a land chapter, an island level, or a resource stage ({@link IslandResourceStage}). */
    public enum Kind { CHAPTER, LEVEL, STAGE, ACHIEVEMENT, QUEST, EXPEDITION, GUESTS, WORKERS, BOSS, PRESTIGE }

    /**
     * Everything that unlocks showcases, for one island: its chapter, level and stage, plus the best achievement
     * score, the quest chains finished and the expeditions cleared by anyone in the co-op, and visitors served.
     */
    public record Context(int tier, int islandLevel, int resourceStage, int achievementPoints,
                          java.util.Set<String> questChains, java.util.Set<String> expeditions, int guestsServed,
                          int workerUnlocks, int bossKills, int stars) {
        public Context {
            questChains = java.util.Set.copyOf(questChains);
            expeditions = java.util.Set.copyOf(expeditions);
        }
        public static Context basic(int tier, int islandLevel, int resourceStage) {
            return new Context(tier, islandLevel, resourceStage, 0, java.util.Set.of(), java.util.Set.of(), 0, 0, 0, 0);
        }
        /** How many showcases this island has unlocked. */
        public int collected() { return (int) java.util.Arrays.stream(values()).filter(s -> s.unlocked(this)).count(); }
    }

    private final String title, rarity;
    private final Kind kind;
    private final int need;
    private final String key;
    private final Material material;
    private final NamedTextColor color;
    IslandShowcase(String title, String rarity, Kind kind, int need, Material material, NamedTextColor color) {
        this.title = title; this.rarity = rarity; this.kind = kind; this.need = need; this.key = null; this.material = material; this.color = color;
    }
    IslandShowcase(String title, String rarity, Kind kind, String key, Material material, NamedTextColor color) {
        this.title = title; this.rarity = rarity; this.kind = kind; this.need = 0; this.key = key; this.material = material; this.color = color;
    }
    /** The quest chain or expedition id for those kinds; null for the rest. */
    public String key() { return key; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String rarity() { return rarity; }
    public Kind kind() { return kind; }
    /** The chapter that unlocks a chapter showcase; -1 for the others. */
    public int chapter() { return kind == Kind.CHAPTER ? need : -1; }
    public int need() { return need; }
    public Material material() { return material; }
    public NamedTextColor color() { return color; }
    /** Unlocked by chapters alone: true only for a chapter showcase the island's chapter has reached. */
    public boolean unlocked(int tier) { return kind == Kind.CHAPTER && tier >= need; }
    public boolean unlocked(int tier, int islandLevel, int resourceStage) { return unlocked(Context.basic(tier, islandLevel, resourceStage)); }
    public boolean unlocked(Context c) {
        return switch (kind) {
            case CHAPTER -> c.tier() >= need;
            case LEVEL -> c.islandLevel() >= need;
            case STAGE -> c.resourceStage() >= need;
            case ACHIEVEMENT -> c.achievementPoints() >= need;
            case QUEST -> c.questChains().contains(key);
            case EXPEDITION -> c.expeditions().contains(key);
            case GUESTS -> c.guestsServed() >= need;
            case WORKERS -> c.workerUnlocks() >= need;
            case BOSS -> c.bossKills() >= need;
            case PRESTIGE -> c.stars() >= need;
        };
    }
    /** "Complete chapter 2", "Reach island level 30", "Unlock the Foundry stage". */
    public String requirement() {
        return switch (kind) {
            case CHAPTER -> need == 0 ? "Every island starts with it" : "Complete land chapter " + need;
            case LEVEL -> "Reach island level " + need;
            case STAGE -> "Unlock the " + IslandResourceStage.at(need).title() + " resource stage";
            case ACHIEVEMENT -> "Earn " + need + " achievement points (any co-op member)";
            case QUEST -> "Finish the " + QUEST_CHAINS.getOrDefault(key, key) + " quests (any co-op member)";
            case EXPEDITION -> "Clear " + EXPEDITIONS.getOrDefault(key, key) + " (any co-op member)";
            case GUESTS -> "Serve " + need + " island visitors";
            case WORKERS -> "Reach " + need + " different worker tiers in the Workers' Lodge";
            case BOSS -> "Help defeat Thunderhide " + (need == 1 ? "once" : need + " times") + " (any co-op member)";
            case PRESTIGE -> "Earn " + need + " prestige star" + (need == 1 ? "" : "s") + " (island XP past level 50)";
        };
    }
    private static final java.util.Map<String, String> QUEST_CHAINS = java.util.Map.of("reach_prologue", "Above the Clouds",
        "quiet_fields", "Rot in the Fields", "brindle_orchards", "Brindle Orchards", "thistle_farm", "Thistle Farm", "vale_bounties", "Vale bounty");
    private static final java.util.Map<String, String> EXPEDITIONS = java.util.Map.of("first_flight", "First Flight",
        "hollow_barrow", "the Hollow Barrow", "sunken_aqueduct", "the Sunken Aqueduct", "gloomforge_mines", "the Gloomforge Mines",
        "thornwood_canopy", "the Thornwood Canopy", "cinder_keep", "Cinder Keep", "hall_of_wardens", "the Hall of the Three Wardens");

    /** Museum-style milestones: every this many showcases collected pays a reward and raises visitor payouts. */
    public static final int MILESTONE_STEP = 5;
    public static int milestones(int collected) { return collected / MILESTONE_STEP; }
    /** Marks paid for reaching collection milestone {@code n} (1-based): 500, 1,000, 1,500 ... */
    public static long milestoneMarks(int n) { return 500L * n; }
    /** Extra share of visitor Marks per collection milestone reached. */
    public static final double VISITOR_BONUS_PER_MILESTONE = 0.04;

    public static IslandShowcase parse(String id) {
        for (var choice : values()) if (choice.id().equals(id)) return choice;
        throw new IllegalArgumentException("Unknown showcase. Open " + "/island showcase" + " to see them all.");
    }
    public static int certified(IslandService.Island island) {
        if (island.tier() >= IslandChapter.values().length) return 3;
        var chapter = IslandChapter.values()[island.tier()];
        return (int) chapter.requirements().keySet().stream()
            .filter(skill -> island.proofs().containsKey(island.tier() + ":" + skill.name())).count();
    }
    public static String progress(IslandService.Island island) {
        return island.tier() == 3 ? "All chapters complete" : certified(island) + "/3 milestones certified";
    }
}
