package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RepairPricingTest {
    @Test
    void emptyInventoryHasNoRepairCharge() {
        assertEquals(0L, RepairPricing.quote(0, 0L, 20L, 15L, 0.2D, 25_000L));
    }

    @Test
    void quoteIncludesBaseItemsAndMissingDurabilityAndHonorsTheCap() {
        assertEquals(110L, RepairPricing.quote(2, 300L, 20L, 15L, 0.2D, 25_000L));
        assertEquals(500L, RepairPricing.quote(20, 100_000L, 20L, 15L, 1.0D, 500L));
    }
}
