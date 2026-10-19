package net.nerdsmp.paper.activity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ActivityRollsTest {
    @Test
    void fishingLuckAndStreakImproveRarity() {
        assertEquals(FishingRolls.Rarity.COMMON, FishingRolls.rarity(0, 0.0D, 0, 0.40D));
        assertEquals(FishingRolls.Rarity.UNCOMMON, FishingRolls.rarity(50, 50.0D, 20, 0.40D));
    }

    @Test
    void openWaterImprovesFishingRarity() {
        assertEquals(FishingRolls.Rarity.COMMON, FishingRolls.rarity(0, 0.0D, 0, false, 0.25D));
        assertEquals(FishingRolls.Rarity.UNCOMMON, FishingRolls.rarity(0, 0.0D, 0, true, 0.25D));
    }

    @Test
    void excavationFindChanceGrowsButIsCapped() {
        assertTrue(ExcavationRolls.findChance(50, 100.0D, 30) > ExcavationRolls.findChance(0, 0.0D, 0));
        assertEquals(0.15D, ExcavationRolls.findChance(500, 1000.0D, 500), 0.000001D);
    }
}
