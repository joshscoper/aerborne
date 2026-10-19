package net.nerdsmp.paper.trade;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TradeInventoryCapacityTest {
    @Test
    void partialStacksAllowAFullInventoryToReceiveTheOffer() {
        assertTrue(TradeInventoryCapacity.fits(List.of(stack("iron", 60, 64)),
            List.of(stack("iron", 4, 64)), String::equals));
    }

    @Test
    void allOffersShareTheSameAvailableSpace() {
        assertFalse(TradeInventoryCapacity.fits(Arrays.asList(stack("iron", 64, 64), null),
            List.of(stack("iron", 64, 64), stack("diamond", 1, 64)), String::equals));
    }

    @Test
    void equipmentCannotStackJustBecauseItHasTheSameMaterial() {
        assertFalse(TradeInventoryCapacity.fits(List.of(stack("sword", 1, 1)),
            List.of(stack("sword", 1, 1)), String::equals));
    }

    @Test
    void aStackMayUsePartialSpaceAndThenAnEmptySlot() {
        assertTrue(TradeInventoryCapacity.fits(Arrays.asList(stack("iron", 63, 64), null),
            List.of(stack("iron", 64, 64)), String::equals));
    }

    private static TradeInventoryCapacity.Stack<String> stack(String item, int amount, int maximum) {
        return new TradeInventoryCapacity.Stack<>(item, amount, maximum);
    }
}
