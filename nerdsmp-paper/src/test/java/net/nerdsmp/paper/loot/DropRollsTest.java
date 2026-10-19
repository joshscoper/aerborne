package net.nerdsmp.paper.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DropRollsTest {
    @Test
    void chanceIncludesLootingAndStaysBounded() {
        assertEquals(0.31D, DropRolls.chance(0.25D, 0.02D, 3), 0.000001D);
        assertEquals(1.0D, DropRolls.chance(0.99D, 0.10D, 3), 0.000001D);
        assertEquals(0.0D, DropRolls.chance(-1.0D, 0.10D, 0), 0.000001D);
    }

    @Test
    void amountCoversInclusiveRange() {
        assertEquals(2, DropRolls.amount(2, 5, 0.0D));
        assertEquals(5, DropRolls.amount(2, 5, 0.999999D));
        assertEquals(1, DropRolls.amount(0, -1, 0.5D));
    }

    @Test
    void distanceAndTierRewardsScaleChancesAndAmountsWithinAnEconomyCap() {
        assertEquals(0.20D, DropRolls.scaledChance(0.10D, 4.0D), 0.000001D);
        assertEquals(1.0D, DropRolls.scaledChance(0.75D, 100.0D), 0.000001D);
        assertEquals(20, DropRolls.scaledAmount(10, 10, 0.5D, 4.0D));
        assertEquals(25, DropRolls.scaledAmount(10, 10, 0.5D, 100.0D));
    }
}
