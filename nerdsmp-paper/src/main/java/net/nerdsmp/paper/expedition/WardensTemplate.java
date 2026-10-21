package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The Hall of the Three Wardens: a marble concourse floating at the centre (spawn and exit), with
 * three wings: west to the embers, east to the frost, south to the stone. Each wing is a puzzle
 * antechamber island bridged to a warden's arena. Floors at Y63.
 */
final class WardensTemplate {
    static final int Y = 63;
    static final Isle MARBLE = new Isle(Material.CALCITE, Material.SMOOTH_STONE,
        new Material[]{Material.STONE, Material.DIORITE, Material.CALCITE, Material.TUFF});
    static final Isle EMBER = new Isle(Material.BLACKSTONE, Material.BASALT,
        new Material[]{Material.BLACKSTONE, Material.BASALT, Material.MAGMA_BLOCK, Material.BLACKSTONE});
    static final Isle FROST = new Isle(Material.SNOW_BLOCK, Material.PACKED_ICE,
        new Material[]{Material.PACKED_ICE, Material.STONE, Material.BLUE_ICE, Material.SNOW_BLOCK});
    static final Isle STONE = new Isle(Material.MOSS_BLOCK, Material.DIRT,
        new Material[]{Material.STONE, Material.ANDESITE, Material.MOSSY_COBBLESTONE, Material.COBBLESTONE});

    private WardensTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        concourse(b);
        embers(b);
        frost(b);
        stone(b);
        return b.freeze();
    }

    private static void concourse(BuildCanvas b) {
        island(b, 0, Y, 0, 13, 14, MARBLE);
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            if (x * x + z * z > 121) continue;
            boolean ring = Math.abs(Math.sqrt(x * x + z * z) - 7) < .6;
            b.block(x, Y, z, ring ? Material.GOLD_BLOCK : (x + z) % 2 == 0 ? Material.SMOOTH_QUARTZ : Material.CALCITE);
        }
        // Three archways, one for each wing, and the archive dais in the middle.
        arch(b, -10, 0, true, Material.ORANGE_STAINED_GLASS);
        arch(b, 10, 0, true, Material.LIGHT_BLUE_STAINED_GLASS);
        arch(b, 0, 10, false, Material.GREEN_STAINED_GLASS);
        b.disk(0, Y + 1, 0, 2, Material.SMOOTH_QUARTZ_SLAB);
        b.block(0, Y + 1, 0, Material.LODESTONE);
        for (int[] statue : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            b.block(statue[0], Y + 1, statue[1], Material.CHISELED_QUARTZ_BLOCK);
            b.block(statue[0], Y + 2, statue[1], Material.QUARTZ_PILLAR, "axis=y");
            b.block(statue[0], Y + 3, statue[1], Material.END_ROD, "facing=up");
        }
        b.block(6, Y + 1, -9, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(-6, Y + 1, -9, Material.LECTERN, "facing=south,has_book=false");
        for (int z = -12; z <= -9; z++) for (int x = -1; x <= 1; x++) b.block(x, Y, z, Material.GOLD_BLOCK);
        // Bridges out to each wing.
        for (int x = -20; x <= -12; x++) walk(b, x, 0, true);
        for (int x = 12; x <= 20; x++) walk(b, x, 0, true);
        for (int z = 12; z <= 20; z++) walk(b, 0, z, false);
    }

    /** A pointed arch facing along X ({@code alongX}) or Z, with tinted glass in its crown. */
    private static void arch(BuildCanvas b, int cx, int cz, boolean alongX, Material glass) {
        for (int offset = -3; offset <= 3; offset++) for (int y = Y + 1; y <= Y + 7; y++) {
            int x = alongX ? cx : cx + offset, z = alongX ? cz + offset : cz;
            boolean post = Math.abs(offset) == 3 || Math.abs(offset) == 2 && y >= Y + 5;
            boolean crown = y == Y + 7 && Math.abs(offset) <= 1 || y == Y + 6 && Math.abs(offset) == 2;
            if (post) b.block(x, y, z, Material.QUARTZ_PILLAR, "axis=y");
            else if (crown) b.block(x, y, z, Material.CHISELED_QUARTZ_BLOCK);
            else if (y == Y + 6 && Math.abs(offset) <= 1) b.block(x, y, z, glass);
        }
    }

    /** A three-wide bridge slice with railings. */
    private static void walk(BuildCanvas b, int x, int z, boolean alongX) {
        for (int offset = -2; offset <= 2; offset++) {
            int bx = alongX ? x : x + offset, bz = alongX ? z + offset : z;
            b.block(bx, Y, bz, Math.abs(offset) <= 1 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS);
            if (Math.abs(offset) == 2) b.block(bx, Y + 1, bz, Material.QUARTZ_SLAB, "type=bottom");
            b.block(bx, Y - 1, bz, Material.QUARTZ_BRICKS);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // West: the embers
    // ---------------------------------------------------------------------------------------------

    private static void embers(BuildCanvas b) {
        island(b, -28, Y, 0, 8, 10, EMBER);
        // Four brazier pedestals with targets to light by arrow.
        for (int[] pedestal : new int[][]{{-33, -4}, {-33, 4}, {-23, -4}, {-23, 4}}) {
            b.block(pedestal[0], Y + 1, pedestal[1], Material.POLISHED_BLACKSTONE_BRICKS);
            b.block(pedestal[0], Y + 2, pedestal[1], Material.TARGET);
            b.block(pedestal[0], Y + 3, pedestal[1], Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        }
        gateWall(b, -36, true, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int x = -42; x <= -37; x++) walk(b, x, 0, true);
        arena(b, -54, 0, EMBER, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK, Material.CAMPFIRE);
    }

    // ---------------------------------------------------------------------------------------------
    // East: the frost
    // ---------------------------------------------------------------------------------------------

    private static void frost(BuildCanvas b) {
        island(b, 28, Y, 0, 8, 10, FROST);
        // Four ice pillars, each with a chime (a button) to ring in order.
        int[][] chimes = {{24, -4}, {32, -4}, {24, 4}, {32, 4}};
        for (int[] chime : chimes) {
            for (int y = Y + 1; y <= Y + 4; y++) b.block(chime[0], y, chime[1], Material.PACKED_ICE);
            b.block(chime[0], Y + 5, chime[1], Material.BLUE_ICE);
            b.block(chime[0], Y + 2, chime[1] + (chime[1] < 0 ? 1 : -1), Material.POLISHED_DIORITE);
            b.block(chime[0], Y + 3, chime[1] + (chime[1] < 0 ? 1 : -1), Material.STONE_BUTTON, "face=floor,facing=north,powered=false");
        }
        gateWall(b, 36, true, Material.PACKED_ICE);
        for (int x = 37; x <= 42; x++) walk(b, x, 0, true);
        arena(b, 54, 0, FROST, Material.PACKED_ICE, Material.BLUE_ICE, Material.SOUL_LANTERN);
    }

    // ---------------------------------------------------------------------------------------------
    // South: the stone
    // ---------------------------------------------------------------------------------------------

    private static void stone(BuildCanvas b) {
        island(b, 0, Y, 28, 8, 10, STONE);
        for (int[] plate : new int[][]{{-4, 26}, {4, 26}, {0, 31}}) {
            b.box(plate[0] - 1, Y + 1, plate[1] - 1, plate[0] + 1, Y + 1, plate[1] + 1, Material.STONE_BRICKS);
            b.block(plate[0], Y + 2, plate[1], Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "power=0");
        }
        for (int[] menhir : new int[][]{{-6, 22}, {6, 22}, {-6, 33}, {6, 33}})
            for (int y = Y + 1; y <= Y + 4; y++) b.block(menhir[0], y, menhir[1], Material.MOSSY_STONE_BRICKS);
        gateWall(b, 36, false, Material.STONE_BRICKS);
        for (int z = 37; z <= 42; z++) walk(b, 0, z, false);
        arena(b, 0, 54, STONE, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.LANTERN);
    }

    /** A wall across the bridge with an iron-bar gate in the middle (the antechamber's gate). */
    private static void gateWall(BuildCanvas b, int at, boolean alongX, Material wall) {
        for (int offset = -3; offset <= 3; offset++) for (int y = Y + 1; y <= Y + 5; y++) {
            int x = alongX ? at : offset, z = alongX ? offset : at;
            if (Math.abs(offset) <= 1 && y <= Y + 3) b.block(x, y, z, Material.IRON_BARS, alongX ? "north=true,south=true" : "east=true,west=true");
            else b.block(x, y, z, wall);
            b.block(x, Y, z, wall);
        }
    }

    /** A warden's arena: a round island with a ring of pillars, themed floor accents and lights. */
    private static void arena(BuildCanvas b, int cx, int cz, Isle isle, Material pillar, Material accent, Material light) {
        island(b, cx, Y, cz, 13, 15, isle);
        for (int x = cx - 11; x <= cx + 11; x++) for (int z = cz - 11; z <= cz + 11; z++) {
            double dx = x - cx, dz = z - cz;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 11) continue;
            if (Math.abs(distance - 9) < .6 || Math.abs(distance - 4) < .5) b.block(x, Y, z, accent);
        }
        for (int index = 0; index < 10; index++) {
            double angle = 2 * Math.PI * index / 10 + Math.PI / 10;
            int x = cx + (int) Math.round(Math.cos(angle) * 11), z = cz + (int) Math.round(Math.sin(angle) * 11);
            for (int y = Y + 1; y <= Y + 5 + index % 3; y++) b.block(x, y, z, pillar);
            if (light == Material.CAMPFIRE) b.block(x, Y + 6 + index % 3, z, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
            else b.block(x, Y + 6 + index % 3, z, light, light == Material.LANTERN || light == Material.SOUL_LANTERN ? "hanging=false" : "");
        }
    }
}
