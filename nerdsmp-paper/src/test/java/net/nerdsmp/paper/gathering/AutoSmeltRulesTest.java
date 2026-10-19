package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AutoSmeltRulesTest {
    @Test void convertsOnlyTheExpectedNativeDropForEachEligibleOre() {
        for (Material ore : new Material[]{Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE})
            assertEquals(Material.IRON_INGOT, AutoSmeltRules.output(ore, Material.RAW_IRON, false));
        for (Material ore : new Material[]{Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE})
            assertEquals(Material.COPPER_INGOT, AutoSmeltRules.output(ore, Material.RAW_COPPER, false));
        for (Material ore : new Material[]{Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE})
            assertEquals(Material.GOLD_INGOT, AutoSmeltRules.output(ore, Material.RAW_GOLD, false));
        assertEquals(Material.NETHERITE_SCRAP, AutoSmeltRules.output(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS, false));
    }

    @Test void silkTouchWrongOreAndAlreadySmeltedDropsNeverConvert() {
        for (Material ore : Material.values()) {
            assertNull(AutoSmeltRules.output(ore, Material.RAW_IRON, true));
            assertNull(AutoSmeltRules.output(ore, Material.IRON_INGOT, false));
        }
        assertNull(AutoSmeltRules.output(Material.IRON_ORE, Material.IRON_ORE, false));
        assertNull(AutoSmeltRules.output(Material.IRON_ORE, Material.RAW_GOLD, false));
        assertNull(AutoSmeltRules.output(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET, false));
        assertFalse(AutoSmeltRules.ore(Material.RAW_IRON_BLOCK));
        assertFalse(AutoSmeltRules.ore(null));
    }

    @Test void retainsTheActualFortuneQuantityWithoutAnotherRoll() {
        for (int fortuneQuantity : new int[]{1, 2, 4, 7, 20, 64})
            assertEquals(fortuneQuantity, AutoSmeltRules.units(fortuneQuantity));
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE})
            assertEquals(0, AutoSmeltRules.units(invalid));
    }

    @Test void processingUsesTheExistingVerifiedSmeltXpDefaults() {
        assertEquals(4, AutoSmeltRules.defaultExperience(Material.IRON_INGOT));
        assertEquals(4, AutoSmeltRules.defaultExperience(Material.COPPER_INGOT));
        assertEquals(6, AutoSmeltRules.defaultExperience(Material.GOLD_INGOT));
        assertEquals(12, AutoSmeltRules.defaultExperience(Material.NETHERITE_SCRAP));
        assertEquals(0, AutoSmeltRules.defaultExperience(Material.DIAMOND));
        assertEquals("netherite", AutoSmeltRules.metal(Material.NETHERITE_SCRAP));
        assertEquals("", AutoSmeltRules.metal(Material.DIAMOND));
    }
}
