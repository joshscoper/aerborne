package net.nerdsmp.paper.skyblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IslandGridTest {
    @Test void entireLayoutRoundTripsAndMaximumTerritoriesStayInTheirCells() {
        for (int slot = 0; slot < IslandGrid.LIMIT; slot++) {
            int x = IslandGrid.chunkX(slot), z = IslandGrid.chunkZ(slot);
            assertEquals(slot, IslandGrid.slotAt(x, z));
            assertEquals(slot, IslandGrid.slotAt(x - 4, z - 4)); assertEquals(slot, IslandGrid.slotAt(x + 4, z + 4));
            assertTrue(IslandGrid.contains(slot, 3, x - 4, z + 4)); assertFalse(IslandGrid.contains(slot, 3, x + 5, z));
        }
    }
    @Test void negativeCoordinatesNearOriginWorkWithoutGrantingNeighborCells() {
        assertEquals(0, IslandGrid.slotAt(-1, -1)); assertTrue(IslandGrid.contains(0, 0, -1, -1));
        assertEquals(-1, IslandGrid.slotAt(-17, 0)); assertEquals(-1, IslandGrid.slotAt(0, -17));
        assertFalse(IslandGrid.contains(0, 3, -16, 0));
    }
    @Test void invalidSlotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> IslandGrid.blockX(-1));
        assertThrows(IllegalArgumentException.class, () -> IslandGrid.chunkZ(IslandGrid.LIMIT));
    }
}
