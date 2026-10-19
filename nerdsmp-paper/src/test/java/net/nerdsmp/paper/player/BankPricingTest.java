package net.nerdsmp.paper.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BankPricingTest {
    @Test
    void nineSlotsAreFreeAndEveryUpgradeCostsProgressivelyMore() {
        assertEquals(250L, BankService.slotCost(9, 9, 250L, 125L));
        assertEquals(375L, BankService.slotCost(10, 9, 250L, 125L));
        assertTrue(BankService.slotCost(53, 9, 250L, 125L)
            > BankService.slotCost(20, 9, 250L, 125L));
    }
}
