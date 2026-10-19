package net.nerdsmp.paper.loot.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class LootProfileDefinitionTest {
    private Entry item(String id, int amount) { return new Entry(id, Kind.ITEM, "crafted_diamond_sword", 0.5, 1, amount, true, 2); }

    @Test void identifiersEntriesAndSnapshotsAreImmutable() {
        var entry = item("sword", 1);
        var profile = new LootProfileDefinition("crypt_rewards", Mode.REPLACE, List.of(entry));
        assertEquals("crafted_diamond_sword", entry.source());
        assertThrows(UnsupportedOperationException.class, () -> profile.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> new LootProfileDefinition("../escape", Mode.REPLACE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LootProfileDefinition("Crypt", Mode.REPLACE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LootProfileDefinition("crypt", Mode.REPLACE, List.of(entry, entry)));
        assertThrows(IllegalArgumentException.class, () -> item("a.b", 1));
    }
    @Test void nonFiniteChancesAndInvalidAmountsAreRejected() {
        for (double chance : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1})
            assertThrows(IllegalArgumentException.class, () -> new Entry("a", Kind.ITEM, "sword", chance, 1, 1, false, 0));
        for (int[] amounts : new int[][]{{0, 1}, {2, 1}, {1, 65}})
            assertThrows(IllegalArgumentException.class, () -> new Entry("a", Kind.ITEM, "sword", 1, amounts[0], amounts[1], false, 0));
        for (int affixes : new int[]{-1, 4})
            assertThrows(IllegalArgumentException.class, () -> new Entry("a", Kind.ITEM, "sword", 1, 1, 1, false, affixes));
    }
    @Test void marksCanOnlyBeAwardedThroughBoundedPurseEntries() {
        assertEquals(10_000, new Entry("money", Kind.MARKS, "marks", 1, 1, 10_000, false, 0).maximum());
        assertThrows(IllegalArgumentException.class, () -> new Entry("money", Kind.MARKS, "marks", 1, 1, 10_001, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Entry("money", Kind.MARKS, "currency", 1, 1, 10, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Entry("money", Kind.MARKS, "marks", 1, 1, 10, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new Entry("money", Kind.ITEM, "coin_purse", 1, 1, 1, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new LootProfileDefinition("rewards", Mode.ADDITIVE, List.of(
            new Entry("a", Kind.MARKS, "marks", 1, 1, 6_000, false, 0),
            new Entry("b", Kind.MARKS, "marks", 1, 1, 6_000, false, 0))));
    }
    @Test void validMaterialsNormalizeWithoutNeedingAPaperRegistry() {
        assertEquals("IRON_INGOT", new Entry("iron", Kind.MATERIAL, "iron_ingot", 1, 1, 8, false, 0).source());
        for (String source : List.of("not_an_item", "AIR", "CAVE_AIR", "VOID_AIR", "LEGACY_STONE"))
            assertThrows(IllegalArgumentException.class, () -> new Entry("iron", Kind.MATERIAL, source, 1, 1, 1, false, 0));
        assertDoesNotThrow(() -> new Entry("water", Kind.MATERIAL, "WATER", 1, 1, 1, false, 0),
            "Runtime availability is validated by the store, not by registry-free authoring drafts.");
        assertThrows(IllegalArgumentException.class, () -> new Entry("sword", Kind.MATERIAL, "DIAMOND_SWORD", 1, 1, 1, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new Entry("sword", Kind.MATERIAL, "DIAMOND_SWORD", 1, 1, 1, false, 1));
    }
    @Test void perKillWorstCaseBudgetAndEntryCountAreBounded() {
        assertDoesNotThrow(() -> new LootProfileDefinition("full", Mode.REPLACE,
            IntStream.range(0, 4).mapToObj(i -> item("item_" + i, 64)).toList()));
        assertThrows(IllegalArgumentException.class, () -> new LootProfileDefinition("too_many", Mode.ADDITIVE,
            IntStream.range(0, 5).mapToObj(i -> item("item_" + i, 64)).toList()));
        assertThrows(IllegalArgumentException.class, () -> new LootProfileDefinition("too_many", Mode.ADDITIVE,
            IntStream.range(0, 33).mapToObj(i -> item("item_" + i, 1)).toList()));
        assertDoesNotThrow(() -> new LootProfileDefinition("none", Mode.REPLACE, List.of()), "An explicitly empty replacement table is valid.");
    }
}
