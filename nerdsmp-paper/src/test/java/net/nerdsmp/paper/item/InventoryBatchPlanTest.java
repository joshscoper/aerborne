package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class InventoryBatchPlanTest {
    @Test void identifiesTheQuotedBatchWithoutTouchingOtherItemsAndCannotReplay() {
        var inventory = new AtomicReference<>(Arrays.asList("sealed-a", "sealed-b", "food", null));
        var plan = new InventoryBatchPlan<>(inventory.get(), Arrays.asList("revealed-a", "revealed-b", "food", null));
        assertEquals(InventoryBatchPlan.Result.APPLIED, plan.apply(inventory::get, inventory::set));
        assertEquals(Arrays.asList("revealed-a", "revealed-b", "food", null), inventory.get());
        assertEquals(InventoryBatchPlan.Result.ALREADY_ATTEMPTED, plan.apply(inventory::get, inventory::set));
    }

    @Test void scrappingAndDustDeliveryAreOneInventoryReplacement() {
        var inventory = new AtomicReference<>(Arrays.asList("sealed-a", "food", "dust:60", "sealed-b"));
        var plan = new InventoryBatchPlan<>(inventory.get(), Arrays.asList("dust:3", "food", "dust:64", null));
        AtomicInteger writes = new AtomicInteger();
        assertEquals(InventoryBatchPlan.Result.APPLIED, plan.apply(inventory::get, result -> {
            writes.incrementAndGet(); inventory.set(result);
        }));
        assertEquals(1, writes.get());
        assertEquals(Arrays.asList("dust:3", "food", "dust:64", null), inventory.get());
    }

    @Test void anyInventoryChangeIncludingAnUnrelatedStackRefusesBeforeWriting() {
        var before = Arrays.asList("sealed", "food:5", null);
        var plan = new InventoryBatchPlan<>(before, Arrays.asList("revealed", "food:5", null));
        assertFalse(plan.matches(Arrays.asList("sealed", "food:4", null)));
        AtomicInteger writes = new AtomicInteger();
        assertEquals(InventoryBatchPlan.Result.INPUT_CHANGED,
            plan.apply(() -> Arrays.asList("sealed", "food:4", null), result -> writes.incrementAndGet()));
        assertEquals(0, writes.get());
    }

    @Test void snapshotsAreDefensiveAndCannotBeEditedByReplacementCode() {
        List<String> before = new ArrayList<>(Arrays.asList("sealed", null));
        List<String> after = new ArrayList<>(Arrays.asList(null, "dust"));
        var plan = new InventoryBatchPlan<>(before, after);
        before.set(0, "changed"); after.set(1, "changed");
        assertTrue(plan.matches(Arrays.asList("sealed", null)));
        assertEquals(InventoryBatchPlan.Result.APPLIED, plan.apply(() -> Arrays.asList("sealed", null), result -> {
            assertEquals(Arrays.asList(null, "dust"), result);
            assertThrows(UnsupportedOperationException.class, () -> result.set(1, "changed"));
        }));
    }

    @Test void failedReadsAreRefundableButAmbiguousWritesAreNeverReplayed() {
        var plan = new InventoryBatchPlan<>(List.of("sealed"), List.of("revealed"));
        AtomicInteger writes = new AtomicInteger();
        assertEquals(InventoryBatchPlan.Result.UNAVAILABLE, plan.apply(() -> { throw new IllegalStateException(); }, result -> writes.incrementAndGet()));
        assertEquals(0, writes.get());
        assertEquals(InventoryBatchPlan.Result.REVIEW_REQUIRED, plan.apply(() -> List.of("sealed"), result -> {
            writes.incrementAndGet(); throw new IllegalStateException();
        }));
        assertEquals(InventoryBatchPlan.Result.ALREADY_ATTEMPTED, plan.apply(() -> List.of("sealed"), result -> writes.incrementAndGet()));
        assertEquals(1, writes.get());
    }

    @Test void emptyUnchangedOversizedOrMismatchedPlansAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryBatchPlan<>(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new InventoryBatchPlan<>(List.of("a"), List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> new InventoryBatchPlan<>(List.of("a"), List.of("b", "c")));
        assertThrows(IllegalArgumentException.class, () -> new InventoryBatchPlan<>(Collections.nCopies(37,"a"), Collections.nCopies(37,"b")));
    }
}
