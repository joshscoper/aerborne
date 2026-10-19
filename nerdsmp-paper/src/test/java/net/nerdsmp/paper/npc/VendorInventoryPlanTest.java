package net.nerdsmp.paper.npc;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class VendorInventoryPlanTest {
    record Stack(String identity, int amount, int maximum) { }
    private static final VendorInventoryPlan.Items<Stack> ITEMS = new VendorInventoryPlan.Items<>() {
        public boolean empty(Stack item) { return item == null; }
        public boolean same(Stack left, Stack right) { return left.identity.equals(right.identity) && left.maximum == right.maximum; }
        public int amount(Stack item) { return item.amount; }
        public int maximum(Stack item) { return item.maximum; }
        public Stack copy(Stack item, int amount) { return new Stack(item.identity, amount, item.maximum); }
    };
    @Test void purchaseFillsMatchingStacksBeforeEmptySlotsWithoutChangingInput() {
        var before = Arrays.asList(new Stack("stone", 60, 64), null, new Stack("named_stone", 10, 64));
        var next = VendorInventoryPlan.plan(before, new Stack("stone", 16, 64), 16, true, ITEMS);
        assertEquals(List.of(new Stack("stone", 64, 64), new Stack("stone", 12, 64), new Stack("named_stone", 10, 64)), next);
        assertEquals(60, before.getFirst().amount); assertNull(before.get(1));
    }
    @Test void fullStorageRefusesTheWholePurchaseWithoutChangingAnySlot() {
        var before = List.of(new Stack("stone", 63, 64), new Stack("dirt", 64, 64));
        assertNull(VendorInventoryPlan.plan(before, new Stack("stone", 16, 64), 16, true, ITEMS));
        assertEquals(63, before.getFirst().amount);
    }
    @Test void saleUsesExactItemIdentityAndNeverConsumesChangedGear() {
        var before = List.of(new Stack("iron_ingot", 8, 64), new Stack("iron_ingot:renamed", 64, 64), new Stack("iron_ingot", 8, 64));
        var next = VendorInventoryPlan.plan(before, new Stack("iron_ingot", 16, 64), 16, false, ITEMS);
        assertNull(next.getFirst()); assertNull(next.getLast()); assertEquals(before.get(1), next.get(1));
        assertEquals(8, before.getFirst().amount);
        assertNull(VendorInventoryPlan.plan(List.of(new Stack("sword:socketed", 1, 1)), new Stack("sword", 1, 1), 1, false, ITEMS));
    }
    @Test void quantityAndActualStackLimitAreEnforced() {
        var empty = Arrays.<Stack>asList(null, null);
        assertThrows(IllegalArgumentException.class, () -> VendorInventoryPlan.plan(empty, new Stack("sword", 1, 1), 16, true, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> VendorInventoryPlan.plan(empty, new Stack("stone", 1, 64), 0, true, ITEMS));
        assertThrows(IllegalArgumentException.class, () -> VendorInventoryPlan.plan(empty, new Stack("stone", 1, 64), 65, true, ITEMS));
        assertEquals(1, VendorInventoryPlan.plan(empty, new Stack("sword", 1, 1), 1, true, ITEMS).getFirst().amount);
    }
    @Test void insufficientSaleOrMalformedInventoryHasNoPartialResult() {
        assertNull(VendorInventoryPlan.plan(List.of(new Stack("stone", 4, 64)), new Stack("stone", 16, 64), 16, false, ITEMS));
        assertNull(VendorInventoryPlan.plan(List.of(new Stack("stone", 65, 64)), new Stack("stone", 1, 64), 1, true, ITEMS));
    }
}
