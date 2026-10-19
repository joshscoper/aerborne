package net.nerdsmp.paper.zone;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AreaSelectionTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();

    @Test void eitherCornerCanBeSelectedFirstButBothAreRequired() {
        AreaSelection areas = new AreaSelection();
        assertTrue(areas.selection(PLAYER).isEmpty());
        AreaSelection.Selection second = areas.select(PLAYER, WORLD, 5, 64, 7, false);
        assertFalse(second.complete());
        assertNull(second.first());
        assertEquals(new AreaSelection.Point(5, 64, 7), second.second());
        assertThrows(IllegalStateException.class, second::bounds);
        assertThrows(IllegalStateException.class, second::volume);
        AreaSelection.Selection complete = areas.select(PLAYER, WORLD, 2, 62, 3, true);
        assertTrue(complete.complete());
        assertEquals(new ZoneDefinition.Bounds(2, 62, 3, 5, 64, 7), complete.bounds());
        assertEquals(60, complete.volume());
    }

    @Test void reverseCornersAndNegativeCoordinatesProduceAnInclusiveCuboid() {
        AreaSelection areas = new AreaSelection();
        areas.select(PLAYER, WORLD, -3, 70, -2, true);
        AreaSelection.Selection selected = areas.select(PLAYER, WORLD, -9, 64, -8, false);
        assertEquals(new ZoneDefinition.Bounds(-9, 64, -8, -3, 70, -2), selected.bounds());
        assertEquals(343, selected.volume());
        assertTrue(selected.bounds().contains(-3.1, 70.9, -2.1));
        assertFalse(selected.bounds().contains(-2, 70, -2));
    }

    @Test void switchingWorldsDropsTheOtherCornerInBothDirections() {
        AreaSelection areas = new AreaSelection();
        UUID nextWorld = UUID.randomUUID();
        areas.select(PLAYER, WORLD, 0, 64, 0, true);
        areas.select(PLAYER, WORLD, 1, 65, 1, false);
        AreaSelection.Selection next = areas.select(PLAYER, nextWorld, 8, 72, 8, false);
        assertEquals(nextWorld, next.worldId());
        assertNull(next.first());
        assertFalse(next.complete());
        AreaSelection.Selection back = areas.select(PLAYER, WORLD, -8, 64, -8, true);
        assertNull(back.second());
        assertEquals(WORLD, back.worldId());
        assertFalse(back.complete());
    }

    @Test void previousSnapshotsRemainImmutableWhenCornersAreChangedOrCleared() {
        AreaSelection areas = new AreaSelection();
        AreaSelection.Selection initial = areas.select(PLAYER, WORLD, 0, 64, 0, true);
        AreaSelection.Selection complete = areas.select(PLAYER, WORLD, 1, 65, 1, false);
        areas.select(PLAYER, WORLD, 2, 66, 2, true);
        assertNull(initial.second());
        assertEquals(new AreaSelection.Point(0, 64, 0), complete.first());
        assertEquals(8, complete.volume());
        areas.clear(PLAYER);
        assertTrue(areas.selection(PLAYER).isEmpty());
        assertEquals(8, complete.volume());
        areas.clear(PLAYER);
    }

    @Test void invalidCoordinatesOrOversizedCornersNeverReplaceExistingState() {
        AreaSelection areas = new AreaSelection();
        AreaSelection.Selection first = areas.select(PLAYER, WORLD, 0, 64, 0, true);
        assertThrows(IllegalArgumentException.class, () -> areas.select(PLAYER, WORLD, 0, 320, 0, false));
        assertThrows(IllegalArgumentException.class, () -> areas.select(PLAYER, WORLD, Integer.MAX_VALUE, 64, 0, false));
        assertThrows(IllegalArgumentException.class, () -> areas.select(PLAYER, WORLD, 2048, 64, 0, false));
        assertEquals(first, areas.selection(PLAYER).orElseThrow());
        AreaSelection.Selection complete = areas.select(PLAYER, WORLD, 1, 65, 1, false);
        assertThrows(IllegalArgumentException.class, () -> areas.select(PLAYER, WORLD, -2048, 64, 0, true));
        assertEquals(complete, areas.selection(PLAYER).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new AreaSelection.Point(0, -65, 0));
    }

    @Test void volumeCapAndWorldEdgeCoordinatesUseZoneLimits() {
        AreaSelection areas = new AreaSelection();
        areas.select(PLAYER, WORLD, 0, -64, 0, true);
        assertThrows(IllegalArgumentException.class, () -> areas.select(PLAYER, WORLD, 1024, 319, 1024, false));
        UUID edgePlayer = UUID.randomUUID();
        areas.select(edgePlayer, WORLD, -29_999_984, -64, 29_999_984, true);
        assertEquals(1, areas.select(edgePlayer, WORLD, -29_999_984, -64, 29_999_984, false).volume());
        assertThrows(IllegalArgumentException.class, () -> new AreaSelection.Point(-29_999_985, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new AreaSelection.Point(0, 64, 29_999_985));
    }

    @Test void selectionsArePlayerScopedBoundedAndCapacityCanBeReleased() {
        AreaSelection areas = new AreaSelection();
        UUID[] players = new UUID[AreaSelection.MAX_PLAYERS];
        for (int index = 0; index < players.length; index++) {
            players[index] = new UUID(1, index);
            areas.select(players[index], WORLD, index, 64, 0, true);
        }
        UUID extra = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> areas.select(extra, WORLD, 0, 64, 0, true));
        assertTrue(areas.selection(extra).isEmpty());
        areas.select(players[0], WORLD, 0, 65, 0, false);
        assertTrue(areas.selection(players[0]).orElseThrow().complete());
        assertFalse(areas.selection(players[1]).orElseThrow().complete());
        areas.clear(players[1]);
        assertDoesNotThrow(() -> areas.select(extra, WORLD, 0, 64, 0, true));
        areas.clearAll();
        for (UUID player : players) assertTrue(areas.selection(player).isEmpty());
        assertTrue(areas.selection(extra).isEmpty());
    }

    @Test void emptyOrUnidentifiedSelectionsAreRejected() {
        AreaSelection areas = new AreaSelection();
        assertThrows(NullPointerException.class, () -> areas.select(null, WORLD, 0, 64, 0, true));
        assertThrows(NullPointerException.class, () -> areas.select(PLAYER, null, 0, 64, 0, true));
        assertThrows(NullPointerException.class, () -> areas.selection(null));
        assertThrows(NullPointerException.class, () -> areas.clear(null));
        assertThrows(IllegalArgumentException.class, () -> new AreaSelection.Selection(WORLD, null, null));
    }
}
