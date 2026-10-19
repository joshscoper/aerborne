package net.nerdsmp.paper.skyblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IslandBoundaryTest {
    @Test void exactChunkEdgesAndWidthsMatchProtectedLandForEveryTier() {
        for (int slot : new int[]{0, 1, 999, 1000, 99999}) for (int tier = 0; tier <= 3; tier++) {
            var bounds = IslandBoundary.bounds(slot, tier);
            assertEquals((tier * 2 + 3) * 16, bounds.width());
            assertTrue(IslandGrid.contains(slot, tier, bounds.minX() >> 4, bounds.minZ() >> 4));
            assertTrue(IslandGrid.contains(slot, tier, (bounds.maxX() - 1) >> 4, (bounds.maxZ() - 1) >> 4));
            assertFalse(IslandGrid.contains(slot, tier, bounds.maxX() >> 4, bounds.minZ() >> 4));
            assertFalse(IslandGrid.contains(slot, tier, bounds.minX() >> 4, bounds.maxZ() >> 4));
        }
    }
    @Test void visualPointsStayOnEdgesNearViewerAndNeverExceedPacketBudget() {
        var bounds = IslandBoundary.bounds(0, 3);
        var points = IslandBoundary.sample(bounds, bounds.minX() + 2, 150, bounds.minZ() + 2);
        assertFalse(points.isEmpty()); assertTrue(points.size() <= 64);
        for (var point : points) {
            assertTrue(point.x() == bounds.minX() || point.x() == bounds.maxX() || point.z() == bounds.minZ() || point.z() == bounds.maxZ());
            assertTrue(point.y() == 150 || point.y() == 154);
            assertTrue(Math.hypot(point.x() - (bounds.minX() + 2), point.z() - (bounds.minZ() + 2)) <= 40);
        }
        assertThrows(UnsupportedOperationException.class, () -> points.clear());
    }
    @Test void distantEdgesAndInvalidInputsAreNotRendered() {
        var bounds = IslandBoundary.bounds(0, 3);
        assertTrue(IslandBoundary.sample(bounds, 8, 96, 8).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> IslandBoundary.bounds(0, 4));
        assertThrows(IllegalArgumentException.class, () -> IslandBoundary.bounds(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> IslandBoundary.sample(bounds, Double.NaN, 96, 8));
    }
}
