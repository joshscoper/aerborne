package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EnchantingRollsTest {
    @Test
    void topOfferNeedsBookshelvesAndSmithingForLevelFive() {
        assertEquals(3, EnchantingRolls.level(2, 15, 10, 0.0D, 5));
        assertEquals(4, EnchantingRolls.level(2, 15, 25, 0.0D, 5));
        assertEquals(5, EnchantingRolls.level(2, 15, 40, 0.0D, 5));
    }

    @Test
    void arcaneDustCostsRiseByOfferTierWithoutUsingExperienceLevels() {
        assertEquals(4, EnchantingRolls.dustCost(0));
        assertEquals(8, EnchantingRolls.dustCost(1));
        assertEquals(12, EnchantingRolls.dustCost(2));
    }

    @Test
    void anOfferNeverPromotesPastWhatItsTierActuallyRolled() {
        assertEquals(0, EnchantingRolls.upgradeLevel(4, 3, 5));
        assertEquals(0, EnchantingRolls.upgradeLevel(4, 4, 5));
        assertEquals(5, EnchantingRolls.upgradeLevel(4, 5, 5));
    }
}
