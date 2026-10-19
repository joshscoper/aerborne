package net.nerdsmp.paper.gathering;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class AutoSmeltReceiptTest {
    private static final UUID PLAYER = UUID.randomUUID(), WORLD = UUID.randomUUID();
    private static final String HARVEST = "harvest:" + UUID.randomUUID();

    @Test void oneHarvestOneConversionOneStableProcessingAward() {
        var receipt = proven();
        assertFalse(receipt.prove(HARVEST));
        assertTrue(receipt.convert(identity()));
        assertFalse(receipt.convert(identity()));
        assertTrue(receipt.settle(true, true, 20));
        assertFalse(receipt.settle(true, true, 20));
        assertEquals(HARVEST + ":furnace_heart", receipt.operation());
        assertEquals(AutoSmeltReceipt.State.SETTLED, receipt.state());
    }

    @Test void originalActorAccountWorldCellStateAndSameTickAreAllRequired() {
        for (var mismatch : List.of(
            new AutoSmeltReceipt.Identity(UUID.randomUUID(), "account", WORLD, 1, 64, 2, "iron_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "other", WORLD, 1, 64, 2, "iron_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "account", UUID.randomUUID(), 1, 64, 2, "iron_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "account", WORLD, 2, 64, 2, "iron_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "account", WORLD, 1, 65, 2, "iron_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "account", WORLD, 1, 64, 3, "iron_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "account", WORLD, 1, 64, 2, "copper_ore", 100),
            new AutoSmeltReceipt.Identity(PLAYER, "account", WORLD, 1, 64, 2, "iron_ore", 101)))
            assertFalse(proven().convert(mismatch));
    }

    @Test void finalCancellationMissingOrUnboundedItemsAndUnprovenDropsCannotAward() {
        assertFalse(new AutoSmeltReceipt(identity()).convert(identity()));
        assertFalse(proven().settle(true, true, 1));
        for (int units : new int[]{-1, 0, AutoSmeltRules.MAX_UNITS + 1}) {
            var receipt = proven();
            assertTrue(receipt.convert(identity()));
            assertFalse(receipt.settle(true, true, units));
            assertEquals(AutoSmeltReceipt.State.REFUSED, receipt.state());
        }
        var canceledBreak = proven(); canceledBreak.convert(identity());
        assertFalse(canceledBreak.settle(false, true, 1));
        var canceledDrop = proven(); canceledDrop.convert(identity());
        assertFalse(canceledDrop.settle(true, false, 1));
    }

    @Test void refusedExpiredOrDisconnectedWorkCannotBeRevived() {
        var receipt = proven();
        receipt.refuse();
        assertFalse(receipt.convert(identity()));
        assertFalse(receipt.prove(HARVEST));
        assertFalse(receipt.settle(true, true, 1));
        for (String invalid : new String[]{"", "harvest:", "harvest:abc", "foreign:" + UUID.randomUUID()})
            assertFalse(new AutoSmeltReceipt(identity()).prove(invalid));
    }

    private static AutoSmeltReceipt proven() {
        var receipt = new AutoSmeltReceipt(identity());
        assertTrue(receipt.prove(HARVEST));
        return receipt;
    }
    private static AutoSmeltReceipt.Identity identity() {
        return new AutoSmeltReceipt.Identity(PLAYER, "account", WORLD, 1, 64, 2, "iron_ore", 100);
    }
}
