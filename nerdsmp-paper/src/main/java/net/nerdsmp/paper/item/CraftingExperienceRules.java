package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.Skill;
import org.bukkit.Material;

/** Positive crafting work; reversible packing/unpacking and recipe previews never grant experience. */
public final class CraftingExperienceRules {
    private CraftingExperienceRules() { }

    public static Skill vanillaSkill(Material output) {
        if (EquipmentMaterials.isEquipment(output) || output == Material.ANVIL || output == Material.SMITHING_TABLE)
            return Skill.SMITHING;
        return output == Material.BREWING_STAND || output == Material.GLASS_BOTTLE || output == Material.BLAZE_POWDER
            || output == Material.FERMENTED_SPIDER_EYE || output == Material.MAGMA_CREAM ? Skill.ALCHEMY : null;
    }

    public static long experience(long perCraft, int crafts) {
        return Math.min(64_000L, Math.clamp(perCraft, 1, 1000) * Math.clamp(crafts, 0, 64));
    }
}
