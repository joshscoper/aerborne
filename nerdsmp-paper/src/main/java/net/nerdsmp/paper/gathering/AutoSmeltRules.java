package net.nerdsmp.paper.gathering;

import org.bukkit.Material;

/** Converts the actual native ore drop, never a new Fortune roll or a Silk Touch block. */
public final class AutoSmeltRules {
    public static final int MAX_DROP_ENTITIES = 16;
    public static final int MAX_UNITS = 1024;
    private AutoSmeltRules() { }

    public static Material output(Material ore, Material dropped, boolean silkTouch) {
        if (ore == null || dropped == null || silkTouch) return null;
        return switch (ore) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> dropped == Material.RAW_IRON ? Material.IRON_INGOT : null;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> dropped == Material.RAW_COPPER ? Material.COPPER_INGOT : null;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> dropped == Material.RAW_GOLD ? Material.GOLD_INGOT : null;
            case ANCIENT_DEBRIS -> dropped == Material.ANCIENT_DEBRIS ? Material.NETHERITE_SCRAP : null;
            default -> null;
        };
    }

    public static boolean ore(Material material) {
        if (material == null) return false;
        return output(material, switch (material) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.RAW_IRON;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.RAW_COPPER;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.RAW_GOLD;
            default -> material;
        }, false) != null;
    }

    public static int units(int nativeAmount) {
        return nativeAmount > 0 && nativeAmount <= 64 ? nativeAmount : 0;
    }

    public static String metal(Material output) {
        return switch (output) {
            case IRON_INGOT -> "iron";
            case COPPER_INGOT -> "copper";
            case GOLD_INGOT -> "gold";
            case NETHERITE_SCRAP -> "netherite";
            default -> "";
        };
    }

    public static long defaultExperience(Material output) {
        return switch (output) {
            case IRON_INGOT, COPPER_INGOT -> 4;
            case GOLD_INGOT -> 6;
            case NETHERITE_SCRAP -> 12;
            default -> 0;
        };
    }
}
