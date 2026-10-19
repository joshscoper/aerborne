package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class RepairPlanTest {
    @Test
    void repairsOnlyQuotedSlotsAndPreservesEverythingExceptDamage() {
        Gear sword = new Gear(18, "ruby:3", "keen:2", "masterwork", "venom:70");
        Gear repaired = new Gear(0, sword.sockets(), sword.enchantments(), sword.quality(), sword.poison());
        Map<Integer, Gear> inventory = new HashMap<>(Map.of(11, sword, 15, sword));
        RepairPlan<Gear> plan = new RepairPlan<>(List.of(new RepairPlan.Entry<>(11, sword, repaired)));
        assertEquals(RepairPlan.ApplyResult.APPLIED, plan.apply(inventory::get, inventory::put));
        assertEquals(repaired, inventory.get(11));
        assertEquals(sword, inventory.get(15));
        assertEquals(1, plan.size());
    }

    @Test
    void anyChangedSlotOrMetadataCancelsEntireBatchBeforeWrites() {
        Map<Integer, String> inventory = new HashMap<>(Map.of(0, "damaged-a", 39, "renamed-b"));
        RepairPlan<String> plan = new RepairPlan<>(List.of(
            new RepairPlan.Entry<>(0, "damaged-a", "repaired-a"),
            new RepairPlan.Entry<>(39, "damaged-b", "repaired-b")));
        assertFalse(plan.matches(inventory::get));
        assertEquals(RepairPlan.ApplyResult.INPUT_CHANGED, plan.apply(inventory::get, inventory::put));
        assertEquals("damaged-a", inventory.get(0));
        assertEquals("renamed-b", inventory.get(39));
    }

    @Test
    void completedRepairsCannotBeReplayed() {
        Map<Integer, String> inventory = new HashMap<>(Map.of(0, "damaged"));
        RepairPlan<String> plan = new RepairPlan<>(List.of(new RepairPlan.Entry<>(0, "damaged", "repaired")));
        AtomicInteger writes = new AtomicInteger();
        assertEquals(RepairPlan.ApplyResult.APPLIED, plan.apply(inventory::get, (slot, item) -> writes.incrementAndGet()));
        assertEquals(RepairPlan.ApplyResult.ALREADY_ATTEMPTED, plan.apply(inventory::get, (slot, item) -> writes.incrementAndGet()));
        assertEquals(1, writes.get());
    }

    @Test
    void failedReadIsKnownNotToHaveMutatedAndCanBeRefunded() {
        RepairPlan<String> plan = new RepairPlan<>(List.of(new RepairPlan.Entry<>(0, "damaged", "repaired")));
        AtomicInteger writes = new AtomicInteger();
        assertEquals(RepairPlan.ApplyResult.UNAVAILABLE, plan.apply(slot -> {
            throw new IllegalStateException("unavailable inventory");
        }, (slot, item) -> writes.incrementAndGet()));
        assertEquals(0, writes.get());
    }

    @Test
    void failedWriteRequiresReviewAndCannotRepeatOrAutomaticallyRefund() {
        RepairPlan<String> plan = new RepairPlan<>(List.of(
            new RepairPlan.Entry<>(0, "damaged", "repaired"),
            new RepairPlan.Entry<>(1, "damaged", "repaired")));
        AtomicInteger writes = new AtomicInteger();
        assertEquals(RepairPlan.ApplyResult.REVIEW_REQUIRED, plan.apply(slot -> "damaged", (slot, item) -> {
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("ambiguous write");
        }));
        assertEquals(RepairPlan.ApplyResult.ALREADY_ATTEMPTED, plan.apply(slot -> "damaged", (slot, item) -> writes.incrementAndGet()));
        assertEquals(2, writes.get());
    }

    @Test
    void invalidOrDuplicateSlotPlansFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new RepairPlan<>(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RepairPlan<>(List.of(
            new RepairPlan.Entry<>(0, "a", "b"), new RepairPlan.Entry<>(0, "a", "b"))));
        assertThrows(IllegalArgumentException.class, () -> new RepairPlan.Entry<>(-1, "a", "b"));
    }

    private record Gear(int damage, String sockets, String enchantments, String quality, String poison) { }
}
