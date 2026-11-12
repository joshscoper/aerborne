package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What island levels earn, every five levels: a lasting perk for the whole co-op while they work on their own
 * island, and a one-time reward each member claims from the journal. Perks add up, so a level 50 island has all ten.
 * Talent points still come every two levels; these are on top.
 */
public enum IslandMilestone {
    HOMEBOUND(5, "Homebound", Material.LEATHER_BOOTS,
        "Speed I while on your island.", 500, false),
    STEADY_HANDS(10, "Steady Hands", Material.GOLDEN_PICKAXE,
        "Haste I while on your island.", 1_000, false),
    FOUNDRY_CHARTER(15, "Foundry Charter", Material.BLAST_FURNACE,
        "+5% skill XP from work on your island.", 1_500, false),
    RICH_GROUND(20, "Rich Ground", Material.MOSS_BLOCK,
        "+10% crop growth and +5% generator stone reroll.", 2_000, false),
    AETHER_CHARTER(25, "Aether Charter", Material.AMETHYST_SHARD,
        "+5% more skill XP on your island (10% in all).", 3_000, true),
    MASTER_BUILDERS(30, "Master Builders", Material.DIAMOND_PICKAXE,
        "Haste II while on your island.", 4_000, false),
    DEEP_STORAGE(35, "Deep Storage", Material.BARREL,
        "+2 chunk hoppers and +16 items per collection pass.", 5_000, false),
    WINDRUNNER(40, "Windrunner", Material.FEATHER,
        "Speed II while on your island.", 6_000, false),
    GOLDEN_SEAMS(45, "Golden Seams", Material.RAW_GOLD_BLOCK,
        "+10% generator better-ore reroll.", 8_000, false),
    PARAGON_ISLE(50, "Paragon Isle", Material.NETHER_STAR,
        "Regeneration I and +5% more skill XP on your island (15% in all).", 12_000, true);

    public static final int STEP = 5;
    private final int level;
    private final String title, perk;
    private final Material icon;
    private final long marks;
    private final boolean booster;

    IslandMilestone(int level, String title, Material icon, String perk, long marks, boolean booster) {
        this.level = level; this.title = title; this.icon = icon; this.perk = perk; this.marks = marks; this.booster = booster;
    }

    public int level() { return level; }
    public String title() { return title; }
    public Material icon() { return icon; }
    public String perk() { return perk; }
    /** Marks each member claims once a season when the island reaches this level. */
    public long marks() { return marks; }
    /** Whether the claim also gives the member a server-wide XP booster. */
    public boolean booster() { return booster; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public boolean reached(int islandLevel) { return islandLevel >= level; }

    /** "1,500 Marks + an XP booster": the one-time claim, for lore. */
    public String rewardLabel() {
        return String.format(Locale.ROOT, "%,d Marks", marks) + (booster ? " + an XP booster" : "");
    }

    public static Optional<IslandMilestone> at(int level) {
        for (IslandMilestone milestone : values()) if (milestone.level == level) return Optional.of(milestone);
        return Optional.empty();
    }

    public static Optional<IslandMilestone> next(int islandLevel) {
        for (IslandMilestone milestone : values()) if (!milestone.reached(islandLevel)) return Optional.of(milestone);
        return Optional.empty();
    }

    public static List<IslandMilestone> reachedBy(int islandLevel) {
        List<IslandMilestone> reached = new ArrayList<>();
        for (IslandMilestone milestone : values()) if (milestone.reached(islandLevel)) reached.add(milestone);
        return reached;
    }

    public static IslandMilestone parse(String id) {
        try { return valueOf(id.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Unknown island milestone: " + id); }
    }

    // ---------------------------------------------------------------- the perks, as numbers the game reads

    /** Percent skill XP for work on the island: 5 at 15, 10 at 25, 15 at 50. */
    public static double skillXpPercent(int islandLevel) {
        return (PARAGON_ISLE.reached(islandLevel) ? 15 : AETHER_CHARTER.reached(islandLevel) ? 10 : FOUNDRY_CHARTER.reached(islandLevel) ? 5 : 0);
    }

    /** Percent chance for a growing crop to gain an extra stage, on top of Cultivation. */
    public static int cropGrowthPercent(int islandLevel) { return RICH_GROUND.reached(islandLevel) ? 10 : 0; }

    /** Percent chance to reroll generator stone, on top of Prospecting. */
    public static int stoneRerollPercent(int islandLevel) { return RICH_GROUND.reached(islandLevel) ? 5 : 0; }

    /** Percent chance to reroll a generator ore for a rarer one, on top of Rich seams and Deep veins. */
    public static int oreRerollPercent(int islandLevel) { return GOLDEN_SEAMS.reached(islandLevel) ? 10 : 0; }

    public static int extraHoppers(int islandLevel) { return DEEP_STORAGE.reached(islandLevel) ? 2 : 0; }

    public static int extraHopperBatch(int islandLevel) { return DEEP_STORAGE.reached(islandLevel) ? 16 : 0; }

    /** An effect the island grants its members while they're on it. Amplifier 0 is level I. */
    public record Aura(PotionEffectType type, int amplifier) { }

    public static List<Aura> auras(int islandLevel) {
        List<Aura> auras = new ArrayList<>();
        if (WINDRUNNER.reached(islandLevel)) auras.add(new Aura(PotionEffectType.SPEED, 1));
        else if (HOMEBOUND.reached(islandLevel)) auras.add(new Aura(PotionEffectType.SPEED, 0));
        if (MASTER_BUILDERS.reached(islandLevel)) auras.add(new Aura(PotionEffectType.HASTE, 1));
        else if (STEADY_HANDS.reached(islandLevel)) auras.add(new Aura(PotionEffectType.HASTE, 0));
        if (PARAGON_ISLE.reached(islandLevel)) auras.add(new Aura(PotionEffectType.REGENERATION, 0));
        return auras;
    }
}
