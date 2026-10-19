package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.ChunkKey;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ClaimRegionPolicyTest {
    @Test void reservesIncludeBothEdgesAndOnlyTheirOwnWorld() {
        UUID world = UUID.randomUUID();
        var region = new ClaimRegionPolicy.Region(world, -3, -2, 3, 2);
        assertTrue(region.contains(new ChunkKey(world, -3, -2)));
        assertTrue(region.contains(new ChunkKey(world, 3, 2)));
        assertFalse(region.contains(new ChunkKey(world, 4, 2)));
        assertFalse(region.contains(new ChunkKey(UUID.randomUUID(), 0, 0)));
    }

    @Test void aClaimMustFitCompletelyInsideTheWorldBorder() {
        UUID world = UUID.randomUUID();
        var border = new ClaimRegionPolicy.Bounds(-32, -32, 32, 32);
        assertTrue(border.contains(new ChunkKey(world, -2, -2)));
        assertTrue(border.contains(new ChunkKey(world, 1, 1)));
        assertFalse(border.contains(new ChunkKey(world, 2, 0)));
        assertFalse(border.contains(new ChunkKey(world, -3, 0)));
        assertFalse(new ClaimRegionPolicy.Bounds(-31, -32, 32, 32).contains(new ChunkKey(world, -2, 0)));
    }
}
