package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ForgeOutputPlanTest {
    @Test void FullStorageRejectsOutputWithoutChangingItsSnapshot() {
        var original = Collections.nCopies(36, new ForgeOutputPlan.Stack<>("stone", 64, 64));
        var snapshot = new ArrayList<>(original);
        assertTrue(ForgeOutputPlan.place(original, "ruby:3", 64, String::equals).isEmpty());
        assertEquals(snapshot, original);
    }

    @Test void MatchingGemHasSpaceEvenWithoutAnEmptySlot() {
        var storage = List.of(new ForgeOutputPlan.Stack<>("ruby:3", 63, 64),
            new ForgeOutputPlan.Stack<>("stone", 64, 64));
        assertEquals(new ForgeOutputPlan.Delivery(0, 64),
            ForgeOutputPlan.place(storage, "ruby:3", 64, String::equals).orElseThrow());
        assertEquals(63, storage.get(0).amount());
    }

    @Test void FullGemStackCannotOverflowOrMergeDifferentTier() {
        assertTrue(ForgeOutputPlan.place(List.of(new ForgeOutputPlan.Stack<>("ruby:3", 64, 64)),
            "ruby:3", 64, String::equals).isEmpty());
        assertTrue(ForgeOutputPlan.place(List.of(new ForgeOutputPlan.Stack<>("ruby:2", 63, 64)),
            "ruby:3", 64, String::equals).isEmpty());
    }

    @Test void EmptySlotIsUsedAfterExistingMatchingStacks() {
        var storage = new ArrayList<ForgeOutputPlan.Stack<String>>();
        storage.add(null);
        storage.add(new ForgeOutputPlan.Stack<>("ruby:3", 3, 64));
        assertEquals(new ForgeOutputPlan.Delivery(1, 4),
            ForgeOutputPlan.place(storage, "ruby:3", 64, String::equals).orElseThrow());
        assertNull(storage.get(0));
    }

    @Test void EmptyStorageSlotReceivesExactlyOneRecoveredGem() {
        var storage = new ArrayList<ForgeOutputPlan.Stack<String>>();
        storage.add(new ForgeOutputPlan.Stack<>("stone", 64, 64));
        storage.add(null);
        assertEquals(new ForgeOutputPlan.Delivery(1, 1),
            ForgeOutputPlan.place(storage, "prismatic:4", 64, String::equals).orElseThrow());
        assertNull(storage.get(1));
    }

    @Test void BothItemAndInventoryStackLimitsApply() {
        assertTrue(ForgeOutputPlan.place(List.of(new ForgeOutputPlan.Stack<>("ruby:3", 16, 16)),
            "ruby:3", 64, String::equals).isEmpty());
        assertTrue(ForgeOutputPlan.place(List.of(new ForgeOutputPlan.Stack<>("ruby:3", 16, 64)),
            "ruby:3", 16, String::equals).isEmpty());
        assertTrue(ForgeOutputPlan.place(Collections.singletonList(null), "ruby:3", 0, String::equals).isEmpty());
    }
}
