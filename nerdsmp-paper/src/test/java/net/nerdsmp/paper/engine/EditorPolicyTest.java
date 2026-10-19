package net.nerdsmp.paper.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EditorPolicyTest {
    @Test void identifiersAreCanonicalAndBounded() {
        assertEquals("sky_blade", EditorPolicy.identifier(" SKY_BLADE "));
        assertThrows(IllegalArgumentException.class, () -> EditorPolicy.identifier("../unsafe"));
        assertThrows(IllegalArgumentException.class, () -> EditorPolicy.identifier("x".repeat(49)));
        assertThrows(IllegalArgumentException.class, () -> EditorPolicy.identifier(""));
    }

    @Test void playerTextIsPlainAndNeverAllowsControlCharacters() {
        assertEquals("Warden's Edge", EditorPolicy.text(" Warden's Edge ", 80));
        assertEquals("<red>plain", EditorPolicy.text("<red>plain", 80));
        assertThrows(IllegalArgumentException.class, () -> EditorPolicy.text("line\nline", 80));
        assertThrows(IllegalArgumentException.class, () -> EditorPolicy.text("a".repeat(81), 80));
    }

    @Test void numbersRejectNonFiniteOutOfBoundsAndFractionalIntegers() {
        assertEquals(1.25, EditorPolicy.number("1.25", 0, 10));
        for (String value : List.of("NaN", "Infinity", "-Infinity", "1e309", "-1", "11", "damage")) {
            assertThrows(IllegalArgumentException.class, () -> EditorPolicy.number(value, 0, 10));
        }
        assertEquals(10, EditorPolicy.integer("10", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> EditorPolicy.integer("1.5", 1, 100));
    }

    @Test void paginationIsStableEmptySafeAndClamped() {
        List<Integer> data = IntStream.range(0, 65).boxed().toList();
        assertEquals(28, EditorPolicy.entries(data, 0).size());
        assertEquals(28, EditorPolicy.entries(data, 1).getFirst());
        assertEquals(List.of(56, 57, 58, 59, 60, 61, 62, 63, 64), EditorPolicy.entries(data, 99));
        assertEquals(0, EditorPolicy.entries(data, -5).getFirst());
        assertTrue(EditorPolicy.entries(List.of(), 99).isEmpty());
    }

    @Test void viewGuardsRequireOwnerTokenRevisionAndPermission() {
        UUID owner = UUID.randomUUID(), token = UUID.randomUUID();
        assertTrue(EditorPolicy.current(owner, owner, token, token, 4, 4, true));
        assertFalse(EditorPolicy.current(owner, UUID.randomUUID(), token, token, 4, 4, true));
        assertFalse(EditorPolicy.current(owner, owner, token, UUID.randomUUID(), 4, 4, true));
        assertFalse(EditorPolicy.current(owner, owner, token, token, 4, 5, true));
        assertFalse(EditorPolicy.current(owner, owner, token, token, 4, 4, false));
    }

    @Test void promptsExpireAndCannotModifyNewerDrafts() {
        assertTrue(EditorPolicy.promptCurrent(1_000, 999, 4, 4));
        assertFalse(EditorPolicy.promptCurrent(1_000, 1_000, 4, 4));
        assertFalse(EditorPolicy.promptCurrent(1_000, 999, 4, 5));
    }
}
