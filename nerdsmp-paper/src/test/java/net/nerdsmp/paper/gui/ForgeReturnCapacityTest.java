package net.nerdsmp.paper.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ForgeReturnCapacityTest {
    @Test
    void checksBothInputsTogetherRatherThanReusingOneEmptySlot() {
        var storage = new ArrayList<ForgeReturnCapacity.Stack<String>>();
        storage.add(null);
        assertFalse(ForgeReturnCapacity.fits(storage, List.of(
            new ForgeReturnCapacity.Stack<>("sword", 1, 1),
            new ForgeReturnCapacity.Stack<>("dust", 5, 64)), String::equals));
        assertNull(storage.get(0));
    }

    @Test
    void matchingCatalystStackCanUsePartialSpaceWhileGearUsesEmptySlot() {
        var storage = new ArrayList<ForgeReturnCapacity.Stack<String>>();
        storage.add(new ForgeReturnCapacity.Stack<>("dust", 60, 64));
        storage.add(null);
        assertTrue(ForgeReturnCapacity.fits(storage, List.of(
            new ForgeReturnCapacity.Stack<>("sword", 1, 1),
            new ForgeReturnCapacity.Stack<>("dust", 4, 64)), String::equals));
        assertEquals(60, storage.get(0).amount());
    }

    @Test
    void fullStorageAndDifferentItemMetadataRejectReturns() {
        var storage = List.of(new ForgeReturnCapacity.Stack<>("ruby:2", 63, 64));
        assertFalse(ForgeReturnCapacity.fits(storage,
            List.of(new ForgeReturnCapacity.Stack<>("ruby:3", 1, 64)), String::equals));
        assertFalse(ForgeReturnCapacity.fits(storage,
            List.of(new ForgeReturnCapacity.Stack<>("ruby:2", 2, 64)), String::equals));
    }

    @Test
    void itemAndInventoryStackLimitsRemainInEffect() {
        var storage = List.of(new ForgeReturnCapacity.Stack<>("dust", 16, 16));
        assertFalse(ForgeReturnCapacity.fits(storage,
            List.of(new ForgeReturnCapacity.Stack<>("dust", 1, 64)), String::equals));
    }
}
