package net.nerdsmp.paper.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BankPageLayoutTest {
    @Test
    void purchasingAnotherSlotDoesNotMakeTheOldLockedIconWritable() {
        var original = new BankPageLayout(0, 9);
        var upgraded = new BankPageLayout(0, 10);

        assertEquals(8, original.writableIndex(8));
        assertEquals(-1, original.writableIndex(9));
        assertEquals(9, upgraded.writableIndex(9));
    }

    @Test
    void theSecondPageHasOnlyNineStorageSlotsAndNeverIncludesControls() {
        var full = new BankPageLayout(1, 54);
        assertEquals(45, full.writableIndex(0));
        assertEquals(53, full.writableIndex(8));
        assertEquals(-1, full.writableIndex(9));
        assertEquals(-1, full.writableIndex(49));
        assertEquals(-1, full.writableIndex(-999));
    }
}
