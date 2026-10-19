package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;

/** Transparent 100-ticket ore table; roll once per actual water/lava formation. */
public final class GeneratorLoot {
    private GeneratorLoot() { }
    private static final java.util.List<Material> ORDER = java.util.List.of(Material.COBBLESTONE, Material.COAL_ORE,
        Material.COPPER_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.LAPIS_ORE, Material.NETHER_QUARTZ_ORE,
        Material.REDSTONE_ORE, Material.OBSIDIAN, Material.DIAMOND_ORE, Material.EMERALD_ORE);
    public static Material rarer(Material first, Material second) { return ORDER.indexOf(first) >= ORDER.indexOf(second) ? first : second; }
    public static Material roll(int tier, int ticket) {
        if (tier < 0 || tier > 3 || ticket < 0 || ticket >= 100) throw new IllegalArgumentException("Invalid generator roll");
        if (tier >= 3 && ticket == 99) return Material.DIAMOND_ORE;
        if (tier >= 2 && ticket >= 96) return Material.REDSTONE_ORE;
        if (tier >= 1 && ticket >= 93) return Material.LAPIS_ORE;
        if (tier >= 1 && ticket >= 90) return Material.GOLD_ORE;
        if (ticket >= 85) return Material.IRON_ORE;
        if (ticket >= 78) return Material.COPPER_ORE;
        if (ticket >= 68) return Material.COAL_ORE;
        return Material.COBBLESTONE;
    }

    /** Additive resource milestones: all four existing tables remain byte-for-byte equivalent. */
    public static Material rollResources(int stage, int ticket) {
        if (stage < 0 || stage > 5 || ticket < 0 || ticket >= 100)
            throw new IllegalArgumentException("Invalid resource generator roll");
        if (stage <= 3) return roll(stage, ticket);
        if (stage == 4) {
            if (ticket >= 98) return Material.NETHER_QUARTZ_ORE;
            if (ticket >= 96) return Material.DIAMOND_ORE;
            if (ticket >= 93) return Material.REDSTONE_ORE;
            if (ticket >= 90) return Material.LAPIS_ORE;
            if (ticket >= 86) return Material.GOLD_ORE;
            if (ticket >= 78) return Material.IRON_ORE;
            if (ticket >= 70) return Material.COPPER_ORE;
            if (ticket >= 60) return Material.COAL_ORE;
            return Material.COBBLESTONE;
        }
        if (ticket >= 99) return Material.OBSIDIAN;
        if (ticket >= 97) return Material.EMERALD_ORE;
        if (ticket >= 94) return Material.NETHER_QUARTZ_ORE;
        if (ticket >= 91) return Material.DIAMOND_ORE;
        if (ticket >= 87) return Material.REDSTONE_ORE;
        if (ticket >= 83) return Material.LAPIS_ORE;
        if (ticket >= 78) return Material.GOLD_ORE;
        if (ticket >= 70) return Material.IRON_ORE;
        if (ticket >= 62) return Material.COPPER_ORE;
        if (ticket >= 52) return Material.COAL_ORE;
        return Material.COBBLESTONE;
    }

    /** Probabilities shown in the journal are derived from the same actual generator rolls. */
    public static java.util.Map<Material, Integer> resourceTable(int stage) {
        java.util.Map<Material, Integer> result = new java.util.LinkedHashMap<>();
        for (int ticket = 0; ticket < 100; ticket++) result.merge(rollResources(stage, ticket), 1, Integer::sum);
        return java.util.Collections.unmodifiableMap(result);
    }
}
