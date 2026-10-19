package net.nerdsmp.paper.item;

import org.bukkit.Material;

import net.nerdsmp.paper.item.PaperItemService.GearSlot;

/**
 * One source of truth for player equipment supported by the NerdSMP item layer.
 */
public final class EquipmentMaterials {
    private EquipmentMaterials() {
    }

    public static GearSlot slot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return GearSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) {
            return GearSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return GearSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return GearSlot.FEET;
        }
        if (material == Material.SHIELD) {
            return GearSlot.OFF_HAND;
        }
        return isWeapon(material) || isTool(material) ? GearSlot.MAIN_HAND : GearSlot.NONE;
    }

    public static boolean isEquipment(Material material) {
        return slot(material) != GearSlot.NONE;
    }

    public static boolean isMeleeWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SPEAR")
            || material == Material.MACE || material == Material.TRIDENT;
    }

    public static boolean isRangedWeapon(Material material) {
        return material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT;
    }

    public static boolean isWeapon(Material material) {
        return isMeleeWeapon(material) || isRangedWeapon(material);
    }

    public static boolean isArmor(Material material) {
        GearSlot slot = slotWithoutRecursion(material);
        return slot == GearSlot.HEAD || slot == GearSlot.CHEST || slot == GearSlot.LEGS || slot == GearSlot.FEET;
    }

    public static boolean isTool(Material material) {
        String name = material.name();
        return name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || material == Material.FISHING_ROD || material == Material.BRUSH || material == Material.SHEARS;
    }

    public static boolean isMiningTool(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL");
    }

    public static boolean isHarvestTool(Material material) {
        String name = material.name();
        return name.endsWith("_HOE") || name.endsWith("_AXE") || material == Material.SHEARS;
    }

    public static boolean isExcavationTool(Material material) {
        return material.name().endsWith("_SHOVEL") || material == Material.BRUSH;
    }

    private static GearSlot slotWithoutRecursion(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return GearSlot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) {
            return GearSlot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return GearSlot.LEGS;
        }
        if (name.endsWith("_BOOTS")) {
            return GearSlot.FEET;
        }
        return GearSlot.NONE;
    }
}
