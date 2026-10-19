package net.nerdsmp.paper.listener;

import org.bukkit.Material;

import java.util.Set;

/** Defines which placed blocks represent construction work and their XP tier. */
public final class BuildingMaterialCatalog {
    private static final Set<String> RESOURCE_BLOCKS = Set.of(
        "COAL_BLOCK", "RAW_COPPER_BLOCK", "RAW_IRON_BLOCK", "RAW_GOLD_BLOCK",
        "COPPER_BLOCK", "IRON_BLOCK", "GOLD_BLOCK", "LAPIS_BLOCK", "REDSTONE_BLOCK",
        "EMERALD_BLOCK", "DIAMOND_BLOCK", "NETHERITE_BLOCK", "ANCIENT_DEBRIS"
    );
    private static final Set<String> TEMPORARY_BLOCKS = Set.of(
        "DIRT", "GRASS_BLOCK", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM",
        "SAND", "RED_SAND", "GRAVEL", "CLAY", "MUD", "SOUL_SAND", "SOUL_SOIL",
        "SCAFFOLDING", "SLIME_BLOCK", "HONEY_BLOCK", "TNT"
    );
    private static final Set<String> DETAIL_BLOCKS = Set.of(
        "GLASS", "TINTED_GLASS", "IRON_BARS", "CHAIN", "LADDER", "FLOWER_POT",
        "LANTERN", "SOUL_LANTERN", "SEA_LANTERN", "GLOWSTONE", "SHROOMLIGHT",
        "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "END_ROD"
    );

    private BuildingMaterialCatalog() {
    }

    public static Category category(Material material) {
        if (material == null || !material.isBlock() || material.isAir()) {
            return Category.NONE;
        }
        return category(material.name());
    }

    static Category category(String name) {
        if (name == null || name.isBlank()) {
            return Category.NONE;
        }
        if (name.endsWith("_ORE") || RESOURCE_BLOCKS.contains(name) || TEMPORARY_BLOCKS.contains(name)) {
            return Category.NONE;
        }
        if (isLandmark(name)) {
            return Category.LANDMARK;
        }
        if (isDetail(name)) {
            return Category.DETAIL;
        }
        return isStructural(name) ? Category.STRUCTURE : Category.NONE;
    }

    private static boolean isLandmark(String name) {
        return name.contains("QUARTZ") || name.contains("PRISMARINE") || name.contains("PURPUR")
            || name.contains("COPPER")
            || name.contains("OBSIDIAN") || name.equals("AMETHYST_BLOCK")
            || name.equals("GILDED_BLACKSTONE");
    }

    private static boolean isDetail(String name) {
        return DETAIL_BLOCKS.contains(name) || name.endsWith("_STAIRS") || name.endsWith("_SLAB")
            || name.endsWith("_WALL") || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")
            || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_SIGN")
            || name.endsWith("_HANGING_SIGN") || name.endsWith("_GLASS_PANE") || name.endsWith("_CARPET")
            || name.endsWith("_BANNER") || name.endsWith("_CANDLE") || name.endsWith("_LEAVES")
            || name.contains("CHISELED") || name.startsWith("POLISHED_") || name.startsWith("CUT_");
    }

    private static boolean isStructural(String name) {
        return name.equals("STONE") || name.endsWith("_STONE") || name.contains("COBBLESTONE")
            || name.contains("STONE_BRICKS") || name.contains("BLACKSTONE") || name.contains("SANDSTONE")
            || name.endsWith("_BRICKS") || name.equals("BRICKS") || name.contains("DEEPSLATE")
            || name.contains("TUFF") || name.equals("CALCITE") || name.equals("DRIPSTONE_BLOCK")
            || name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD")
            || name.endsWith("_STEM") || name.endsWith("_HYPHAE") || name.startsWith("BAMBOO_")
            || name.endsWith("_CONCRETE") || name.endsWith("_TERRACOTTA") || name.endsWith("_WOOL")
            || name.endsWith("_GLASS") || name.equals("PACKED_MUD") || name.equals("MUD_BRICKS")
            || name.equals("BASALT") || name.equals("SMOOTH_BASALT") || name.equals("NETHERRACK")
            || name.equals("END_STONE") || name.endsWith("BOOKSHELF") || name.equals("BONE_BLOCK")
            || name.endsWith("MUSHROOM_BLOCK") || name.equals("MUSHROOM_STEM")
            || name.endsWith("WART_BLOCK") || name.equals("SNOW_BLOCK");
    }

    public enum Category {
        NONE,
        STRUCTURE,
        DETAIL,
        LANDMARK
    }
}
