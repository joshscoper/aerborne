package net.nerdsmp.paper.npc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static net.nerdsmp.paper.npc.ShopControls.Availability.*;
import static org.junit.jupiter.api.Assertions.*;

class ShopControlsTest {
    @Test void includesConfiguredBundleAndRemovesDuplicateOptions() {
        assertEquals(List.of(1, 8, 16, 64), ShopControls.quantities(8, 64));
        assertEquals(List.of(1, 16, 24, 64), ShopControls.quantities(24, 64));
        assertEquals(List.of(1, 16, 64), ShopControls.quantities(16, 64));
        assertEquals(List.of(1, 16, 64), ShopControls.quantities(64, 64));
    }

    @Test void respectsSmallerStackLimitsAndShowsOneOptionForTools() {
        assertEquals(List.of(1, 8, 16), ShopControls.quantities(8, 16));
        assertEquals(List.of(1, 16), ShopControls.quantities(24, 16));
        assertEquals(List.of(1), ShopControls.quantities(1, 1));
    }

    @Test void rejectsInvalidQuantityBounds() {
        for (int invalid : List.of(-1, 0, 65, Integer.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> ShopControls.quantities(invalid, 64));
            assertThrows(IllegalArgumentException.class, () -> ShopControls.quantities(1, invalid));
        }
    }

    @Test void quantityControlsAreCenteredWithoutOverlappingOtherControls() {
        assertEquals(List.of(22), slots(1));
        assertEquals(List.of(21, 23), slots(2));
        assertEquals(List.of(20, 22, 24), slots(3));
        assertEquals(List.of(19, 21, 23, 25), slots(4));
        for (int options = 1; options <= 4; options++) {
            var unique = new HashSet<>(slots(options));
            assertEquals(options, unique.size());
            assertTrue(unique.stream().allMatch(slot -> slot / 9 == 2 && slot % 9 > 0 && slot % 9 < 8));
            assertTrue(unique.stream().noneMatch(List.of(4, 8, 13, 38, 42, 45, 47, 49, 51, 53)::contains));
        }
    }

    @Test void rejectsInvalidQuantityControlIndices() {
        assertThrows(IllegalArgumentException.class, () -> ShopControls.quantitySlot(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShopControls.quantitySlot(0, 5));
        assertThrows(IllegalArgumentException.class, () -> ShopControls.quantitySlot(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> ShopControls.quantitySlot(3, 3));
    }

    @Test void buyStatesExplainPriceBalanceAndRoom() {
        assertEquals(NOT_FOR_SALE, ShopControls.buyAvailability(0, 0, false));
        assertEquals(INSUFFICIENT_MARKS, ShopControls.buyAvailability(9, 10, true));
        assertEquals(NO_INVENTORY_ROOM, ShopControls.buyAvailability(10, 10, false));
        assertEquals(READY, ShopControls.buyAvailability(10, 10, true));
        assertEquals(READY, ShopControls.buyAvailability(Long.MAX_VALUE, 1_000_000_000L, true));
    }

    @Test void invalidBuyInputsFailClosed() {
        assertEquals(INVALID, ShopControls.buyAvailability(-1, 10, true));
        assertEquals(INVALID, ShopControls.buyAvailability(10, -1, true));
        assertEquals(INVALID, ShopControls.buyAvailability(Long.MAX_VALUE, 1_000_000_001L, true));
    }

    @Test void sellStatesExplainUnsupportedStockDailyLimitAndRequiredBundle() {
        assertEquals(NOT_ACCEPTED, ShopControls.sellAvailability(0, 8, 0, 0));
        assertEquals(DAILY_LIMIT_REACHED, ShopControls.sellAvailability(64, 8, 0, 5));
        assertEquals(INSUFFICIENT_ITEMS, ShopControls.sellAvailability(7, 8, 1, 5));
        assertEquals(READY, ShopControls.sellAvailability(8, 8, 1, 5));
        assertEquals(READY, ShopControls.sellAvailability(100, 64, 5, 1_000_000_000L));
    }

    @Test void invalidSellInputsFailClosed() {
        assertEquals(INVALID, ShopControls.sellAvailability(-1, 8, 1, 5));
        assertEquals(INVALID, ShopControls.sellAvailability(8, 0, 1, 5));
        assertEquals(INVALID, ShopControls.sellAvailability(64, 65, 1, 5));
        assertEquals(INVALID, ShopControls.sellAvailability(8, 8, -1, 5));
        assertEquals(INVALID, ShopControls.sellAvailability(8, 8, 1, -1));
        assertEquals(INVALID, ShopControls.sellAvailability(8, 8, 1, 1_000_000_001L));
    }

    @Test void onlyReadyStateIsActionableAndEveryStateHasPlayerFacingText() {
        for (var state : ShopControls.Availability.values()) {
            assertEquals(state == READY, state.available());
            assertFalse(state.reason().isBlank());
        }
    }

    private static List<Integer> slots(int options) {
        return IntStream.range(0, options).map(index -> ShopControls.quantitySlot(index, options)).boxed().toList();
    }
}
