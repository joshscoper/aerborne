package net.nerdsmp.paper.loot.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class LootProfileRollsTest {
    private Entry entry(String id, double chance, int minimum, int maximum) {
        return new Entry(id, Kind.ITEM, "sword", chance, minimum, maximum, false, 0);
    }
    @Test void zeroNeverDropsAndGuaranteedEntriesDropExactlyOnce() {
        var profile = new LootProfileDefinition("a", Mode.REPLACE, List.of(entry("never", 0, 1, 1), entry("always", 1, 2, 2)));
        var rolls = LootProfileRolls.roll(profile, () -> 0);
        assertEquals(1, rolls.size()); assertEquals("always", rolls.getFirst().entry().id()); assertEquals(2, rolls.getFirst().amount());
        assertThrows(UnsupportedOperationException.class, () -> rolls.clear());
    }
    @Test void authoredPercentageIsExactAndIndependentOfStackOrLevel() {
        var profile = new LootProfileDefinition("a", Mode.ADDITIVE, List.of(entry("rare", 0.25, 1, 4)));
        assertEquals(1, LootProfileRolls.roll(profile, () -> 0.249999).size());
        assertTrue(LootProfileRolls.roll(profile, () -> 0.25).isEmpty());
        assertTrue(LootProfileRolls.roll(profile, () -> 0.99).isEmpty());
    }
    @Test void amountRangeIncludesBothEndpointsWithoutOverrun() {
        var profile = new LootProfileDefinition("a", Mode.ADDITIVE, List.of(entry("quantity", 1, 2, 64)));
        assertEquals(2, LootProfileRolls.roll(profile, () -> 0).getFirst().amount());
        assertEquals(64, LootProfileRolls.roll(profile, () -> Math.nextDown(1.0)).getFirst().amount());
    }
    @Test void randomCallCountIsOneChancePerEntryAndOneAmountPerSuccessfulEntry() {
        AtomicInteger calls = new AtomicInteger();
        var profile = new LootProfileDefinition("a", Mode.REPLACE, List.of(entry("never", 0, 1, 1), entry("first", 1, 1, 1), entry("second", 1, 1, 1)));
        assertEquals(2, LootProfileRolls.roll(profile, () -> { calls.incrementAndGet(); return 0.5; }).size());
        assertEquals(5, calls.get());
    }
    @Test void invalidRandomResultsFailInsteadOfManufacturingLoot() {
        var profile = new LootProfileDefinition("a", Mode.REPLACE, List.of(entry("drop", 1, 1, 1)));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1})
            assertThrows(IllegalArgumentException.class, () -> LootProfileRolls.roll(profile, () -> value));
    }
}
