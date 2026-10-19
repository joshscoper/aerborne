package net.nerdsmp.paper.resource;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourceLedgerTest {
    @Test
    void emptyLedgerAllowsEveryNamedCell() {
        ResourceLedger ledger = ResourceLedger.empty();

        assertFalse(ledger.blocked(0, -64, 0));
        assertFalse(ledger.blocked(15, 319, 15));
        assertArrayEquals(new long[0], ledger.serialize());
        assertSame(ledger, ResourceLedger.from(new long[0]));
    }

    @Test
    void negativeAndPositiveHeightsRemainDistinctAcrossPersistence() {
        ResourceLedger ledger = ResourceLedger.empty().withBlocked(List.of(
            cell(3, -64, 5), cell(3, -1, 5), cell(3, 0, 5), cell(3, 64, 5)));

        ResourceLedger loaded = ResourceLedger.from(ledger.serialize());

        for (int y : new int[] {-64, -1, 0, 64}) assertTrue(loaded.blocked(3, y, 5));
        assertFalse(loaded.blocked(3, 1, 5));
        assertFalse(loaded.blocked(3, -63, 5));
        assertFalse(loaded.blocked(5, -64, 3));
    }

    @Test
    void everyLocalPositionAndFullIntegerHeightRangeRoundTripWithoutCollisions() {
        List<ResourceLedger.Cell> cells = new ArrayList<>();
        for (int y : new int[] {Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) cells.add(cell(x, y, z));
            }
        }
        ResourceLedger ledger = ResourceLedger.empty().withBlocked(cells);
        long[] encoded = ledger.serialize();
        ResourceLedger loaded = ResourceLedger.from(encoded);

        assertTrue(encoded.length == cells.size());
        for (ResourceLedger.Cell cell : cells) {
            assertTrue(loaded.blocked(cell.localX(), cell.y(), cell.localZ()));
        }
    }

    @Test
    void canonicalEncodingSignExtendsYWithoutTruncation() {
        ResourceLedger ledger = ResourceLedger.empty().withBlocked(List.of(
            cell(0, Integer.MIN_VALUE, 0), cell(0, -1, 0), cell(15, -1, 15),
            cell(0, 0, 0), cell(15, 0, 15), cell(15, Integer.MAX_VALUE, 15)));

        assertArrayEquals(new long[] {
            -549755813888L, -256, -1, 0, 255, 549755813887L
        }, ledger.serialize());
    }

    @Test
    void corruptedOrOverflowedYEncodingsAreRejectedInsteadOfAliasingAValidCell() {
        for (long encoded : new long[] {
            -549755813889L, 549755813888L, Long.MIN_VALUE, Long.MAX_VALUE,
            0x000000ffffffffffL, 0xffffff0000000000L
        }) {
            assertThrows(IllegalArgumentException.class, () -> ResourceLedger.from(new long[] {encoded}));
        }
    }

    @Test
    void duplicatePersistedCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ResourceLedger.from(new long[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> ResourceLedger.from(new long[] {-1, 17, -1}));
    }

    @Test
    void markingTheSameCellRepeatedlyIsIdempotent() {
        ResourceLedger.Cell cell = cell(15, -64, 0);
        ResourceLedger once = ResourceLedger.empty().withBlocked(List.of(cell, cell));

        assertSame(once, once.withBlocked(List.of(cell)));
        assertSame(once, once.withBlocked(List.of()));
        assertTrue(once.serialize().length == 1);
    }

    @Test
    void renewalOnlyClearsNamedCells() {
        ResourceLedger ledger = ResourceLedger.empty().withBlocked(List.of(
            cell(7, 64, 7), cell(7, 65, 7), cell(8, 64, 7), cell(7, 64, 8)));

        ResourceLedger renewed = ledger.withRenewed(List.of(cell(7, 64, 7), cell(7, 64, 7)));

        assertFalse(renewed.blocked(7, 64, 7));
        assertTrue(renewed.blocked(7, 65, 7));
        assertTrue(renewed.blocked(8, 64, 7));
        assertTrue(renewed.blocked(7, 64, 8));
        assertTrue(ledger.blocked(7, 64, 7));
        assertSame(renewed, renewed.withRenewed(List.of(cell(7, 64, 7))));
    }

    @Test
    void renewingAllBlockedCellsReturnsEmptyLedger() {
        ResourceLedger.Cell cell = cell(4, 40, 4);
        ResourceLedger ledger = ResourceLedger.empty().withBlocked(List.of(cell));

        assertSame(ResourceLedger.empty(), ledger.withRenewed(List.of(cell)));
        assertSame(ledger, ledger.withRenewed(List.of()));
    }

    @Test
    void callerMutationsDoNotChangeLoadedOrSerializedState() {
        long[] input = new long[] {-256, 255};
        ResourceLedger loaded = ResourceLedger.from(input);
        input[0] = 12;
        long[] snapshot = loaded.serialize();
        snapshot[1] = 0;

        assertArrayEquals(new long[] {-256, 255}, loaded.serialize());
        assertTrue(loaded.blocked(0, -1, 0));
        assertTrue(loaded.blocked(15, 0, 15));
        assertFalse(loaded.blocked(0, 0, 0));
        assertFalse(loaded.blocked(0, 0, 12));
    }

    @Test
    void collectionMutationsDoNotChangePriorOrUpdatedLedger() {
        List<ResourceLedger.Cell> cells = new ArrayList<>(List.of(cell(2, 20, 2)));
        ResourceLedger before = ResourceLedger.empty();
        ResourceLedger after = before.withBlocked(cells);
        cells.clear();
        cells.add(cell(3, 20, 3));

        assertFalse(before.blocked(2, 20, 2));
        assertTrue(after.blocked(2, 20, 2));
        assertFalse(after.blocked(3, 20, 3));
    }

    @Test
    void persistedOrderIsDeterministicRegardlessOfInsertionOrder() {
        ResourceLedger first = ResourceLedger.from(new long[] {255, 16, -256, 0, -1});
        ResourceLedger second = ResourceLedger.from(new long[] {-1, 0, -256, 16, 255});

        assertArrayEquals(new long[] {-256, -1, 0, 16, 255}, first.serialize());
        assertArrayEquals(first.serialize(), second.serialize());
    }

    @Test
    void absoluteOrOutOfRangeHorizontalCoordinatesAreRejected() {
        for (int coordinate : new int[] {Integer.MIN_VALUE, -1, 16, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> cell(coordinate, 64, 0));
            assertThrows(IllegalArgumentException.class, () -> cell(0, 64, coordinate));
            assertThrows(IllegalArgumentException.class,
                () -> ResourceLedger.empty().blocked(coordinate, 64, 0));
            assertThrows(IllegalArgumentException.class,
                () -> ResourceLedger.empty().blocked(0, 64, coordinate));
        }
    }

    @Test
    void nullInputFailsWithoutChangingAnExistingLedger() {
        ResourceLedger ledger = ResourceLedger.empty().withBlocked(List.of(cell(1, 1, 1)));
        List<ResourceLedger.Cell> invalid = Arrays.asList(cell(2, 2, 2), null);

        assertThrows(NullPointerException.class, () -> ResourceLedger.from(null));
        assertThrows(NullPointerException.class, () -> ledger.withBlocked(null));
        assertThrows(NullPointerException.class, () -> ledger.withRenewed(null));
        assertThrows(NullPointerException.class, () -> ledger.withBlocked(invalid));
        assertThrows(NullPointerException.class, () -> ledger.withRenewed(invalid));
        assertTrue(ledger.blocked(1, 1, 1));
        assertFalse(ledger.blocked(2, 2, 2));
    }

    private static ResourceLedger.Cell cell(int x, int y, int z) {
        return new ResourceLedger.Cell(x, y, z);
    }
}
