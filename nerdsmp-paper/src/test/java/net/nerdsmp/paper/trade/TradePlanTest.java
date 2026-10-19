package net.nerdsmp.paper.trade;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TradePlanTest {
    private TradeInventoryCapacity.Stack<String> stack(String name, int count) { return new TradeInventoryCapacity.Stack<>(name, count, 64); }
    @Test void outgoingSlotsMakeRoomForAFullInventorySwap() {
        var sword = new TradeInventoryCapacity.Stack<>("sword", 1, 1);
        var pick = new TradeInventoryCapacity.Stack<>("pick", 1, 1);
        var inventory = List.of(sword, stack("stone", 64));
        var result = TradePlan.exchange(inventory, List.of(new TradePlan.Offer<>(0, sword, 1)), List.of(pick), String::equals);
        assertEquals(List.of(pick, stack("stone", 64)), result);
        assertEquals(sword, inventory.getFirst());
    }
    @Test void partiallyOfferedStacksArePreservedAndIncomingFillsExistingStacksFirst() {
        var inventory = Arrays.asList(stack("stone", 64), stack("apple", 63), null);
        var result = TradePlan.exchange(inventory, List.of(new TradePlan.Offer<>(0, inventory.getFirst(), 10)), List.of(stack("apple", 4)), String::equals);
        assertEquals(List.of(stack("stone", 54), stack("apple", 64), stack("apple", 3)), result);
        assertNull(inventory.get(2));
    }
    @Test void staleAmountOrMetadataRejectsTheWholeOffer() {
        var snapshot = stack("enchanted_sword", 1);
        assertThrows(IllegalArgumentException.class, () -> TradePlan.exchange(List.of(stack("sword", 1)), List.of(new TradePlan.Offer<>(0, snapshot, 1)), List.of(), String::equals));
        assertThrows(IllegalArgumentException.class, () -> TradePlan.exchange(List.of(stack("enchanted_sword", 2)), List.of(new TradePlan.Offer<>(0, snapshot, 1)), List.of(), String::equals));
    }
    @Test void duplicateSlotCannotBeOfferedTwice() {
        var stone = stack("stone", 64); var offer = new TradePlan.Offer<>(0, stone, 16);
        assertThrows(IllegalArgumentException.class, () -> TradePlan.exchange(List.of(stone), List.of(offer, offer), List.of(), String::equals));
    }
    @Test void overflowDoesNotPartiallyMutateTheSource() {
        var inventory = new ArrayList<>(List.of(stack("stone", 63)));
        assertThrows(IllegalArgumentException.class, () -> TradePlan.exchange(inventory, List.of(), List.of(stack("stone", 2)), String::equals));
        assertEquals(List.of(stack("stone", 63)), inventory);
    }
    @Test void invalidAmountsAndSlotsAreRejected() {
        var stone = stack("stone", 1);
        for (int amount : new int[]{0, -1, 2}) assertThrows(IllegalArgumentException.class,
            () -> TradePlan.exchange(List.of(stone), List.of(new TradePlan.Offer<>(0, stone, amount)), List.of(), String::equals));
        assertThrows(IllegalArgumentException.class, () -> TradePlan.exchange(List.of(stone), List.of(new TradePlan.Offer<>(1, stone, 1)), List.of(), String::equals));
        assertThrows(IllegalArgumentException.class, () -> TradePlan.exchange(Arrays.asList((TradeInventoryCapacity.Stack<String>) null), List.of(), List.of(stack("stone", 65)), String::equals));
    }
    @Test void unstackableEquipmentNeverCombines() {
        var sword = new TradeInventoryCapacity.Stack<>("sword", 1, 1);
        var result = TradePlan.exchange(Arrays.asList(sword, null), List.of(), List.of(sword), String::equals);
        assertEquals(List.of(sword, sword), result);
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }
    @Test void randomizedTwoSidedExchangeConservesEveryItem() {
        var random = new Random(731);
        for (int i = 0; i < 500; i++) {
            var a = stack("ore", random.nextInt(64) + 1); var b = stack("crop", random.nextInt(64) + 1);
            int giveA = random.nextInt(a.amount()) + 1, giveB = random.nextInt(b.amount()) + 1;
            var left = TradePlan.exchange(Arrays.asList(a, null), List.of(new TradePlan.Offer<>(0, a, giveA)), List.of(stack("crop", giveB)), String::equals);
            var right = TradePlan.exchange(Arrays.asList(b, null), List.of(new TradePlan.Offer<>(0, b, giveB)), List.of(stack("ore", giveA)), String::equals);
            var totals = new HashMap<String, Integer>();
            java.util.stream.Stream.concat(left.stream(), right.stream()).filter(Objects::nonNull).forEach(s -> totals.merge(s.item(), s.amount(), Integer::sum));
            assertEquals(Map.of("ore", a.amount(), "crop", b.amount()), totals);
        }
    }
}
