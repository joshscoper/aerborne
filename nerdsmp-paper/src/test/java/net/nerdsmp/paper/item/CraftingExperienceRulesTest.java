package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.Skill;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingExperienceRulesTest {
    @Test void toolsAndWeaponsIncludingPreviouslyMissedRodsAndBowsTrainSmithing() {
        for (Material material : new Material[]{Material.FISHING_ROD, Material.BOW, Material.CROSSBOW,
            Material.SHEARS, Material.BRUSH, Material.SHIELD, Material.IRON_SWORD, Material.COPPER_SPEAR})
            assertEquals(Skill.SMITHING, CraftingExperienceRules.vanillaSkill(material), material.name());
        assertEquals(Skill.ALCHEMY, CraftingExperienceRules.vanillaSkill(Material.BREWING_STAND));
    }
    @Test void reversiblePackingAndRawMaterialExtractionAreNotCraftingWork() {
        for (Material material : new Material[]{Material.IRON_BLOCK, Material.IRON_INGOT, Material.IRON_NUGGET,
            Material.GOLD_BLOCK, Material.GOLD_INGOT, Material.BONE_MEAL, Material.DIAMOND_BLOCK, Material.HAY_BLOCK})
            assertNull(CraftingExperienceRules.vanillaSkill(material), material.name());
    }
    @Test void confirmedShiftCraftingCountsActualOperationsAndEmptyReceiptsEarnNothing() {
        assertEquals(0, CraftingExperienceRules.experience(8, 0));
        assertEquals(8, CraftingExperienceRules.experience(8, 1));
        assertEquals(8 * 32, CraftingExperienceRules.experience(8, 32));
        assertEquals(64_000, CraftingExperienceRules.experience(Long.MAX_VALUE, Integer.MAX_VALUE));
    }
}
