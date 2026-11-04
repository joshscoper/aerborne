package net.nerdsmp.paper.reach;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachSettleTest {
    private static final ReachTerrain TERRAIN = new ReachTerrain();
    /** High in the sky over the void, so no terrain touches the test blocks. */
    private static final int X = 2000, Y = 300, Z = 2000;

    @Test
    void cornerStairsTakeTheirCornerShape() {
        ReachCanvas canvas = new ReachCanvas();
        // A roof's corner: a run facing east meets a run facing south.
        canvas.set(X, Y, Z, Material.OAK_STAIRS, "facing=east,half=bottom");
        canvas.set(X, Y, Z + 1, Material.OAK_STAIRS, "facing=east,half=bottom");
        canvas.set(X + 1, Y, Z, Material.OAK_STAIRS, "facing=south,half=bottom");
        ReachSettle.settle(canvas, TERRAIN);
        String corner = canvas.get(X, Y, Z).state();
        assertTrue(corner.contains("shape=inner_") || corner.contains("shape=outer_"), corner);
        assertTrue(canvas.get(X, Y, Z + 1).state().contains("shape=straight"), canvas.get(X, Y, Z + 1).state());
    }

    @Test
    void fencesWallsAndPanesJoinWhatTheyTouch() {
        ReachCanvas canvas = new ReachCanvas();
        canvas.set(X, Y, Z, Material.OAK_FENCE);
        canvas.set(X + 1, Y, Z, Material.OAK_FENCE);
        canvas.set(X, Y, Z + 1, Material.STONE_BRICKS);
        canvas.set(X + 10, Y, Z, Material.STONE_BRICK_WALL);
        canvas.set(X + 11, Y, Z, Material.STONE_BRICK_WALL);
        canvas.set(X + 12, Y, Z, Material.STONE_BRICK_WALL);
        canvas.set(X + 20, Y, Z, Material.GLASS_PANE);
        canvas.set(X + 21, Y, Z, Material.STONE_BRICKS);
        ReachSettle.settle(canvas, TERRAIN);
        String fence = canvas.get(X, Y, Z).state();
        assertTrue(fence.contains("east=true") && fence.contains("south=true") && fence.contains("west=false"), fence);
        String wall = canvas.get(X + 11, Y, Z).state();
        assertTrue(wall.contains("east=low") && wall.contains("west=low") && wall.contains("up=false"), wall);
        assertTrue(canvas.get(X + 10, Y, Z).state().contains("up=true"), canvas.get(X + 10, Y, Z).state());
        assertTrue(canvas.get(X + 20, Y, Z).state().contains("east=true"), canvas.get(X + 20, Y, Z).state());
    }

    @Test
    void stateStringsSurviveARoundTrip() {
        String s = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight]";
        assertEquals(s, ReachSettle.State.parse(s).write());
    }
}
