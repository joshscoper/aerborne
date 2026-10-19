package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import java.util.Locale;

/** Four co-op specializations. Stable IDs keep existing purchases intact. */
public enum IslandTalent {
    PROSPECTING("Prospecting", "Industry", Material.IRON_PICKAXE, null,
        "10% per rank to reroll stone from your generator."),
    RICH_SEAMS("Rich seams", "Industry", Material.DIAMOND_ORE, PROSPECTING,
        "10% per rank to roll an ore again and keep the rarer result."),
    CULTIVATION("Cultivation", "Nature", Material.WHEAT, null,
        "15% per rank for growing crops to gain an extra growth stage."),
    NURSERY("Nursery", "Nature", Material.BONE_MEAL, CULTIVATION,
        "Fertilized crops gain one extra growth stage per rank."),
    STACKING("Compact spawners", "Automation", Material.SPAWNER, null,
        "Stack up to 4 / 8 / 16 matching spawners in one block."),
    LOGISTICS("Collection network", "Automation", Material.HOPPER, STACKING,
        "Run 2 / 4 / 8 chunk hoppers; move 16 / 32 / 64 items per pass."),
    FISHERY("Living waters", "Stewardship", Material.FISHING_ROD, null,
        "Island pond catches gain 5% fishing luck per rank."),
    LINEAGE("Prize livestock", "Stewardship", Material.WHEAT, FISHERY,
        "Breeding gains 4% per rank to improve inherited animal traits."),
    MINING_GUILD("Miner's guild", "Industry", Material.IRON_INGOT, RICH_SEAMS,
        "+8% Mining and Smithing XP per rank while working on this island."),
    ORE_MASTERY("Deep veins", "Industry", Material.EMERALD_ORE, MINING_GUILD,
        "+5% per rank to the Rich seams reroll. Never unlocks ores ahead of your journal."),
    BOUNTIFUL("Bountiful harvest", "Nature", Material.GOLDEN_CARROT, NURSERY,
        "+4% per rank for an extra crop from an eligible island harvest."),
    TIMBERLAND("Timber reserve", "Nature", Material.OAK_SAPLING, BOUNTIFUL,
        "+5% per rank for an extra log from an eligible island tree."),
    WORKSHOP("Busy workshop", "Automation", Material.FURNACE, LOGISTICS,
        "Processing machines finish 10% sooner per rank. Same fuel and ingredients."),
    STORAGE_NETWORK("Freight network", "Automation", Material.CHEST, WORKSHOP,
        "+2 chunk hoppers and +16 items per collection pass per rank."),
    ANGLING("Angler's lodge", "Stewardship", Material.COOKED_COD, LINEAGE,
        "+8% Fishing and Cooking XP per rank while working on this island."),
    HEARTH("Co-op kitchen", "Stewardship", Material.CAMPFIRE, ANGLING,
        "Meals prepared on this island last 10% longer per rank.");

    public static final int MAX_RANK = 3;
    private final String title, branch, description;
    private final Material icon;
    private final IslandTalent prerequisite;
    IslandTalent(String title, String branch, Material icon, IslandTalent prerequisite, String description) {
        this.title = title; this.branch = branch; this.icon = icon; this.prerequisite = prerequisite; this.description = description;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String branch() { return branch; }
    public String description() { return description; }
    public Material icon() { return icon; }
    public IslandTalent prerequisite() { return prerequisite; }
    public int depth() { return prerequisite == null ? 0 : prerequisite.depth() + 1; }
    public int column() { return switch (branch) { case "Industry" -> 0; case "Nature" -> 1; case "Automation" -> 2; case "Stewardship" -> 3; default -> throw new IllegalStateException("Unknown island branch"); }; }
    public int requiredLevel() { return switch (depth()) { case 0 -> 2; case 1 -> 6; case 2 -> 14; default -> 26; }; }
    public int menuSlot() { return 10 + column() * 2 + depth() * 9; }
    public String effect(int rank) {
        rank = Math.clamp(rank, 0, MAX_RANK);
        return switch (this) {
            case PROSPECTING -> (10 * rank) + "% stone reroll";
            case RICH_SEAMS -> (10 * rank) + "% better-ore reroll";
            case CULTIVATION -> (15 * rank) + "% extra crop growth";
            case NURSERY -> "+" + rank + " fertilized growth stages";
            case STACKING -> (switch (rank) {case 1 -> 4;case 2 -> 8;case 3 -> 16;default -> 1;}) + " spawners per stack";
            case LOGISTICS -> (switch (rank) {case 1 -> 2;case 2 -> 4;case 3 -> 8;default -> 0;}) + " hoppers, " + (8 << rank) + " items/pass";
            case FISHERY -> "+" + (5 * rank) + "% pond luck";
            case LINEAGE -> "+" + (4 * rank) + "% trait improvement";
            case MINING_GUILD -> "+" + (8 * rank) + "% Mining / Smithing XP";
            case ORE_MASTERY -> "+" + (5 * rank) + "% to the ore reroll";
            case BOUNTIFUL -> "+" + (4 * rank) + "% extra crop chance";
            case TIMBERLAND -> "+" + (5 * rank) + "% extra log chance";
            case WORKSHOP -> (10 * rank) + "% shorter machine cycles";
            case STORAGE_NETWORK -> "+" + (2 * rank) + " hoppers, +" + (16 * rank) + " items/pass";
            case ANGLING -> "+" + (8 * rank) + "% Fishing / Cooking XP";
            case HEARTH -> "+" + (10 * rank) + "% prepared meal duration";
        };
    }
    public static IslandTalent parse(String id) {
        try { return valueOf(id.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Choose a talent from /island talents."); }
    }
}
