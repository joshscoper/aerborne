package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * First Flight, the tutorial expedition: a chain of small sky islands at dusk. The party lands at
 * an airship pier, hops across stepping stones, pulls the levers of an old watchpost, lights a
 * campfire, drills in a training yard and wakes the Old Sentinel. Runs south (+Z); floors at Y63.
 * The shipped {@code expeditions/first_flight.yml} marks the same coordinates.
 */
final class FirstFlightTemplate {
    static final int Y = 63;

    private FirstFlightTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        pier(b);
        skyhop(b);
        watchpost(b);
        landing(b);
        yard(b);
        sentinel(b);
        return b.freeze();
    }

    /** The landing pier on the first island, with a mast and sail behind it. */
    private static void pier(BuildCanvas b) {
        island(b, 0, Y, 6, 7, 8, MEADOW);
        meadow(b, 0, Y, 6, 7, 31);
        path(b, Y, 2, 13);
        for (int z = -3; z <= 1; z++) for (int x = -2; x <= 2; x++) b.block(x, Y, z, Material.SPRUCE_PLANKS);
        for (int z = -3; z <= 1; z++) for (int x : new int[]{-3, 3}) {
            b.block(x, Y, z, Material.SPRUCE_PLANKS);
            b.block(x, Y + 1, z, Material.SPRUCE_FENCE);
        }
        for (int x = -3; x <= 3; x++) {
            b.block(x, Y, -4, Material.SPRUCE_PLANKS);
            b.block(x, Y + 1, -4, Material.SPRUCE_FENCE);
        }
        lamp(b, -3, Y + 2, 1, 0, Material.SPRUCE_FENCE);
        lamp(b, 3, Y + 2, 1, 0, Material.SPRUCE_FENCE);
        airship(b, -9, Y, -2);
        // Crates and a barrel by the pier.
        b.block(4, Y + 1, 3, Material.BARREL, "facing=up");
        b.block(4, Y + 1, 4, Material.BARREL, "facing=up");
        b.block(-4, Y + 1, 5, Material.HAY_BLOCK, "axis=y");
        lamp(b, 4, Y + 1, 9, 2, Material.SPRUCE_FENCE);
        lamp(b, -4, Y + 1, 9, 2, Material.SPRUCE_FENCE);
    }

    /**
     * The airship that brought the party, moored off the west side of the pier: a plank hull with a
     * rail, a mast, and a gas envelope roped above it. {@code (cx, deck, cz)} is the middle of the deck.
     */
    private static void airship(BuildCanvas b, int cx, int deck, int cz) {
        int half = 6;
        for (int dz = -half; dz <= half; dz++) {
            int width = dz == -half || dz == half ? 1 : Math.abs(dz) >= half - 1 ? 2 : 3;
            for (int dx = -width; dx <= width; dx++) {
                b.block(cx + dx, deck, cz + dz, Material.SPRUCE_PLANKS);
                b.block(cx + dx, deck - 1, cz + dz, Math.abs(dx) == width ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                if (Math.abs(dx) < width) b.block(cx + dx, deck - 2, cz + dz, Material.DARK_OAK_PLANKS);
                if (Math.abs(dx) <= width - 2) b.block(cx + dx, deck - 3, cz + dz, Material.DARK_OAK_LOG, "axis=z");
                if (Math.abs(dx) == width) b.block(cx + dx, deck + 1, cz + dz, Material.SPRUCE_FENCE);
            }
        }
        // The bow rises a little, the stern has a wheel.
        b.block(cx, deck + 1, cz - half, Material.SPRUCE_FENCE);
        b.block(cx, deck + 2, cz - half - 1, Material.SPRUCE_FENCE);
        b.block(cx, deck + 1, cz + half - 1, Material.SPRUCE_FENCE);
        b.block(cx, deck + 2, cz + half - 1, Material.SPRUCE_TRAPDOOR, "facing=north,half=top,open=true");
        // Gangplank onto the pier.
        for (int x = cx + 4; x <= -3; x++) b.block(x, deck, cz + 1, Material.SPRUCE_SLAB, "type=top");
        // Mast with a crow's nest.
        for (int y = deck + 1; y <= deck + 8; y++) b.block(cx, y, cz, Material.STRIPPED_SPRUCE_LOG, "axis=y");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) b.block(cx + dx, deck + 6, cz + dz, Material.SPRUCE_SLAB, "type=bottom");
        // Gas envelope: a long ellipsoid of pale wool with a darker seam.
        int ey = deck + 12;
        double rx = 3.6, ry = 3.2, rz = 8.2;
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -9; dz <= 9; dz++) {
            double inside = dx * dx / (rx * rx) + dy * dy / (ry * ry) + dz * dz / (rz * rz);
            if (inside > 1 || inside < .55) continue;
            Material wool = dy == 0 ? Material.LIGHT_GRAY_WOOL : Math.abs(dz) % 4 == 0 ? Material.BROWN_WOOL : Material.WHITE_WOOL;
            b.block(cx + dx, ey + dy, cz + dz, wool);
        }
        // Ropes from the envelope down to the rail.
        for (int[] rope : new int[][]{{-2, -4}, {2, -4}, {-2, 4}, {2, 4}})
            for (int y = deck + 2; y <= ey - 3; y++) b.block(cx + rope[0], y, cz + rope[1], Material.IRON_CHAIN, "axis=y");
        // A small propeller at the stern.
        for (int[] blade : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}})
            b.block(cx + blade[0], deck + 4 + blade[1], cz + half + 2, Material.SPRUCE_TRAPDOOR, "facing=south,half=top,open=true");
        b.block(cx, deck + 4, cz + half + 1, Material.SPRUCE_FENCE);
    }

    /** Stepping stones over the drop to the watchpost island. */
    private static void skyhop(BuildCanvas b) {
        int[][] stones = {{0, 16}, {2, 20}, {0, 24}, {-2, 28}, {0, 32}, {1, 36}};
        for (int[] spot : stones) stone(b, spot[0], Y, spot[1], spot[1] % 8 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        // Hanging lanterns on chains above each stone, so the way reads in the dusk.
        for (int[] spot : stones) b.block(spot[0] + (spot[0] >= 0 ? 1 : -1), Y + 1, spot[1] + 1, Material.LANTERN);
    }

    /** The ruined watchpost: three levers open the iron gate in its south wall. */
    private static void watchpost(BuildCanvas b) {
        island(b, 0, Y, 50, 10, 11, MOSSY);
        meadow(b, 0, Y, 50, 10, 41);
        path(b, Y, 39, 60);
        ruinWall(b, -8, 43, -8, 57, Y + 1, 4, 51);
        ruinWall(b, 8, 43, 8, 57, Y + 1, 4, 52);
        ruinWall(b, -7, 42, -3, 42, Y + 1, 3, 53);
        ruinWall(b, 3, 42, 7, 42, Y + 1, 3, 54);
        // The south wall with the gate.
        for (int x = -7; x <= 7; x++) for (int y = Y + 1; y <= Y + 5; y++) {
            if (Math.abs(x) <= 1 && y <= Y + 3) b.block(x, y, 59, Material.IRON_BARS, "east=true,west=true");
            else b.block(x, y, 59, pick(new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS}, x, y, 55));
        }
        b.box(-2, Y + 4, 59, 2, Y + 4, 59, Material.CHISELED_STONE_BRICKS);
        // Lever posts.
        for (int[] post : new int[][]{{-6, 50}, {6, 50}, {-4, 56}}) {
            b.block(post[0], Y + 1, post[1], Material.CHISELED_STONE_BRICKS);
            b.block(post[0], Y + 2, post[1], Material.LEVER, "face=floor,facing=south,powered=false");
        }
        lamp(b, -5, Y + 1, 45, 2, Material.COBBLESTONE_WALL);
        lamp(b, 5, Y + 1, 45, 2, Material.COBBLESTONE_WALL);
        lamp(b, 5, Y + 1, 56, 2, Material.COBBLESTONE_WALL);
    }

    /** A rope bridge to a small landing with the Wayfarer's Fire. */
    private static void landing(BuildCanvas b) {
        bridge(b, Y, 60, 63, Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE);
        island(b, 0, Y, 68, 5, 6, MEADOW);
        path(b, Y, 63, 73);
        meadow(b, 0, Y, 68, 5, 61);
        // Logs to sit on round the fire.
        b.block(5, Y + 1, 68, Material.STRIPPED_OAK_LOG, "axis=z");
        b.block(3, Y + 1, 70, Material.STRIPPED_OAK_LOG, "axis=x");
        b.block(3, Y + 1, 66, Material.STRIPPED_OAK_LOG, "axis=x");
        b.block(3, Y + 1, 68, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        bridge(b, Y, 73, 75, Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE);
    }

    /** The training yard: a walled ring with weapon racks and target blocks. */
    private static void yard(BuildCanvas b) {
        island(b, 0, Y, 86, 12, 12, MEADOW);
        for (int x = -10; x <= 10; x++) for (int z = 76; z <= 96; z++) {
            double dx = x, dz = z - 86;
            if (dx * dx + dz * dz <= 81) b.block(x, Y, z, noise(x, z, 71) < .6 ? Material.COARSE_DIRT : Material.GRAVEL);
        }
        for (int index = 0; index < 40; index++) {
            double angle = 2 * Math.PI * index / 40;
            int x = (int) Math.round(Math.cos(angle) * 10.5), z = 86 + (int) Math.round(Math.sin(angle) * 10.5);
            if (Math.abs(x) <= 1) continue; // North and south openings.
            b.block(x, Y + 1, z, Material.SPRUCE_FENCE);
        }
        for (int[] rack : new int[][]{{-8, 82}, {8, 82}, {-8, 90}, {8, 90}}) {
            b.block(rack[0], Y + 1, rack[1], Material.BARREL, "facing=up");
            b.block(rack[0], Y + 2, rack[1], Material.TARGET);
        }
        for (int[] bale : new int[][]{{-5, 79}, {5, 79}, {-5, 93}, {5, 93}}) b.block(bale[0], Y + 1, bale[1], Material.HAY_BLOCK, "axis=y");
        lamp(b, -9, Y + 1, 86, 3, Material.SPRUCE_FENCE);
        lamp(b, 9, Y + 1, 86, 3, Material.SPRUCE_FENCE);
        // The exit arch and its gate.
        for (int x = -4; x <= 4; x++) for (int y = Y + 1; y <= Y + 5; y++) {
            if (Math.abs(x) <= 1 && y <= Y + 3) b.block(x, y, 98, Material.IRON_BARS, "east=true,west=true");
            else if (Math.abs(x) >= 2 || y >= Y + 4) b.block(x, y, 98, Material.STONE_BRICKS);
        }
        bridge(b, Y, 99, 103, Material.SPRUCE_PLANKS, Material.SPRUCE_FENCE);
    }

    /** The Sentinel's island: a ring of broken pillars round a plinth at the back. */
    private static void sentinel(BuildCanvas b) {
        island(b, 0, Y, 116, 13, 14, MOSSY);
        for (int x = -9; x <= 9; x++) for (int z = 107; z <= 125; z++) {
            double dx = x, dz = z - 116;
            if (dx * dx + dz * dz <= 72) b.block(x, Y, z, pick(new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS}, x, z, 81));
        }
        pillarRing(b, 0, Y + 1, 116, 10, 12, 6, 83);
        // Two henge arches flank the arena, their lintels still standing.
        for (int side : new int[]{-1, 1}) {
            int x = side * 8;
            for (int z : new int[]{111, 121}) for (int y = Y + 1; y <= Y + 6; y++)
                b.block(x, y, z, y % 3 == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            for (int z = 110; z <= 122; z++) b.block(x, Y + 7, z, z % 4 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
            b.block(x, Y + 8, 116, Material.STONE_BRICK_WALL);
            b.block(x, Y + 9, 116, Material.SOUL_LANTERN);
        }
        // A pillar that already fell, and rubble from the rest.
        for (int z = 109; z <= 113; z++) b.block(-5, Y + 1, z, z == 109 ? Material.CHISELED_STONE_BRICKS : Material.CRACKED_STONE_BRICKS);
        for (int x = 4; x <= 6; x++) b.block(x, Y + 1, 112, Material.MOSSY_STONE_BRICKS);
        for (int x = -9; x <= 9; x++) for (int z = 106; z <= 126; z++) {
            if (Math.abs(x) <= 2 || (x * x + (z - 116) * (z - 116)) > 90) continue;
            double roll = noise(x, z, 91);
            if (roll < .05) b.block(x, Y + 1, z, Material.COBBLESTONE_SLAB, "type=bottom");
            else if (roll < .09) b.block(x, Y + 1, z, Material.MOSS_CARPET);
            else if (roll < .11) b.block(x, Y + 1, z, Material.ANDESITE_WALL);
        }
        b.box(-2, Y + 1, 123, 2, Y + 1, 125, Material.CHISELED_STONE_BRICKS);
        b.box(-1, Y + 2, 124, 1, Y + 2, 125, Material.POLISHED_ANDESITE);
        for (int x : new int[]{-4, 4}) b.block(x, Y + 1, 124, Material.SOUL_LANTERN);
        // The way home: a pale stone circle behind the plinth.
        b.disk(0, Y, 128, 1, Material.CALCITE);
        lamp(b, -3, Y + 1, 127, 2, Material.COBBLESTONE_WALL);
        lamp(b, 3, Y + 1, 127, 2, Material.COBBLESTONE_WALL);
    }
}
