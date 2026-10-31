package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * Thornwood Canopy, second edition: a night climb with a long way down. Rootgate opens on the Low
 * Boughs, a wide deck wound round two trunks and two drops, which leads to the Fork, the hub. East are
 * the Signal Boughs, whose boards lower the span, and above them Verrin's Perch, an optional fight in the
 * wind. West is the Silk Walk and the Matron's Nest, a hollow trunk; killing her breaks open a catwalk
 * to the climb. The climb rises to the Heart Gate and the Thornheart Grove.
 *
 * <p>Runs south (+Z); low decks at Y63, the perch at Y70 and the high decks at Y77, so players stand a
 * block above those. Anyone who falls is caught ({@code falls:} in {@code expeditions/thornwood_canopy.yml}).
 */
final class ThornwoodTemplate {
    static final int Y = 63, PERCH = 70, HIGH = 77;
    private static final int FOREST_FLOOR = 30, CROWN_LINE = 96;
    private static final int RAMP = 88, ANTE = 102, GATE = 115, GROVE_Z = 137, HEART_Z = 155;
    private static final int NEST_X = -60, NEST_Z = 65, NEST_INSIDE = 18, NEST_OUTSIDE = 20, NEST_TOP = 88;
    private static final Material LOG = Material.DARK_OAK_LOG;
    private static final Material WOOD = Material.DARK_OAK_WOOD;
    private static final Material LEAVES = Material.DARK_OAK_LEAVES;
    private static final Material PLANK = Material.SPRUCE_PLANKS;
    private static final Material RAIL = Material.SPRUCE_FENCE;
    /** Her sacs, round the wall of the nest. The door side stays clear. */
    private static final int[][] SACS = {{-49, 54}, {-60, 50}, {-71, 54}, {-75, 65}, {-71, 76}, {-60, 80}, {-49, 76}};
    private static final int[][] BOARDS = {{27, 58}, {45, 58}, {27, 72}, {45, 72}};
    private static final int[][] KNOTS = {{-5, 80, 149}, {5, 80, 149}, {-3, 84, 148}, {3, 84, 148}, {0, 88, 147}, {-7, 83, 152}, {7, 83, 152}};

    private ThornwoodTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        forest(b);
        rootgate(b);
        bridge(b, Y, 11, 16, PLANK, RAIL);
        lowBoughs(b);
        hiddenHollow(b);
        bridge(b, Y, 47, 52, PLANK, RAIL);
        fork(b);
        bridgeX(b, Y, 14, 22, 65);
        signalBoughs(b);
        perch(b);
        bridgeX(b, Y, -22, -14, 65);
        silkWalk(b);
        nest(b);
        catwalk(b);
        climb(b);
        grove(b);
        SampleChests.place(b, "thornwood_canopy");
        return b.freeze();
    }

    /** The trunks the decks hang between. They rise out of the dark and close over at the crown line. */
    private static void forest(BuildCanvas b) {
        int[][] trees = {{-14, 0, 3}, {-26, 20, 4}, {26, 12, 4}, {21, 44, 3}, {-24, 47, 4}, {22, 92, 4}, {-24, 98, 4}, {60, 62, 5}, {-40, 110, 5}, {40, 112, 5}, {36, 150, 5}, {-36, 150, 5}};
        for (int[] tree : trees) giantTree(b, tree[0], tree[1], FOREST_FLOOR, CROWN_LINE, tree[2], 13, LOG, LEAVES);
    }

    private static void bridgeX(BuildCanvas b, int y, int x1, int x2, int z) {
        for (int x = x1; x <= x2; x++) {
            for (int dz = -1; dz <= 1; dz++) b.block(x, y, z + dz, PLANK);
            for (int side : new int[]{-2, 2}) {
                b.block(x, y, z + side, PLANK);
                b.block(x, y + 1, z + side, RAIL);
            }
        }
        for (int x = x1; x <= x2; x += 4) for (int side : new int[]{-2, 2}) b.block(x, y + 2, z + side, Material.LANTERN);
    }

    /** A drop through a deck, railed all round. */
    private static void hole(BuildCanvas b, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
            boolean rim = x < x1 || x > x2 || z < z1 || z > z2;
            if (rim) b.block(x, y + 1, z, RAIL);
            else for (int down = y - 4; down <= y; down++) b.block(x, down, z, Material.AIR);
        }
    }

    private static void webs(BuildCanvas b, int y, int[][] spots) {
        for (int[] web : spots) b.block(web[0], y, web[1], Material.COBWEB);
    }

    private static void sac(BuildCanvas b, int x, int y, int z) {
        b.block(x, y, z, Material.WHITE_WOOL);
        b.block(x, y + 1, z, Material.COBWEB);
    }

    private static void rootgate(BuildCanvas b) {
        deck(b, -7, -4, 7, 10, Y, PLANK, RAIL, (x, z) -> z == 10 && Math.abs(x) <= 2);
        lamp(b, -6, Y + 1, 9, 2, RAIL);
        lamp(b, 6, Y + 1, 9, 2, RAIL);
        lamp(b, -6, Y + 1, -3, 2, RAIL);
        lamp(b, 6, Y + 1, -3, 2, RAIL);
        b.block(-3, Y + 1, 7, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(-4, Y + 1, 7, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        b.block(-2, Y + 1, 8, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
        b.block(5, Y + 1, 1, Material.BARREL, "facing=up");
        b.block(5, Y + 2, 1, Material.LANTERN);
        b.block(6, Y + 1, 2, Material.TARGET);
        // A ranger's lookout above the deck.
        for (int y = Y + 1; y <= Y + 6; y++) {
            b.block(5, y, -2, Material.LADDER, "facing=south");
            b.block(5, y, -3, PLANK);
        }
        deck(b, 3, -3, 6, -1, Y + 7, PLANK, RAIL, (x, z) -> false);
    }

    /** The first long deck: the walk winds between two trunks and two drops, and the packs can be read from a way off. */
    private static void lowBoughs(BuildCanvas b) {
        deck(b, -17, 17, 17, 46, Y, PLANK, RAIL, (x, z) -> z == 17 && Math.abs(x) <= 2 || z == 46 && Math.abs(x) <= 2 || x == -17 && Math.abs(z - 30) <= 1);
        hole(b, 4, 20, 11, 27, Y);
        hole(b, -11, 37, -4, 43, Y);
        giantTree(b, -8, 27, FOREST_FLOOR, CROWN_LINE, 3, 13, LOG, LEAVES);
        giantTree(b, 8, 37, FOREST_FLOOR, CROWN_LINE, 3, 13, LOG, LEAVES);
        for (int[] light : new int[][]{{-3, 22}, {3, 30}, {-3, 34}, {3, 42}, {-14, 20}, {14, 42}, {14, 22}, {-14, 34}}) lamp(b, light[0], Y + 1, light[1], 2, RAIL);
        webs(b, Y + 1, new int[][]{{-16, 18}, {16, 18}, {-16, 45}, {16, 45}, {-12, 31}, {12, 31}});
        webs(b, Y + 2, new int[][]{{-16, 18}, {16, 18}, {-16, 45}, {16, 45}});
        for (int[] mushroom : new int[][]{{-12, 19}, {13, 29}, {-15, 41}, {15, 39}, {-1, 45}}) b.block(mushroom[0], Y + 1, mushroom[1], Material.RED_MUSHROOM);
        for (int x = -16; x <= 16; x++) for (int z = 18; z <= 45; z++) {
            if (Math.abs(x) <= 2 || noise(x, z, 151) > .1) continue;
            if (b.get(x, Y, z) == PLANK && b.get(x, Y + 1, z) == null) b.block(x, Y + 1, z, Material.MOSS_CARPET);
        }
    }

    /** Off the west rail, out across the drop: pads to a stump with something still in it. */
    private static void hiddenHollow(BuildCanvas b) {
        leafPad(b, -20, Y, 30, LEAVES, LOG);
        leafPad(b, -24, Y + 1, 31, LEAVES, LOG);
        leafPad(b, -28, Y + 1, 29, LEAVES, LOG);
        for (int x = -36; x <= -31; x++) for (int z = 26; z <= 33; z++) b.block(x, Y + 2, z, Material.SPRUCE_SLAB, "type=top");
        for (int y = Y + 3; y <= Y + 6; y++) for (int z = 26; z <= 33; z++) b.block(-37, y, z, WOOD);
        for (int y = Y + 3; y <= Y + 6; y++) for (int x = -36; x <= -31; x++) {
            b.block(x, y, 25, WOOD);
            b.block(x, y, 34, WOOD);
        }
        for (int x = -37; x <= -31; x++) for (int z = 25; z <= 34; z++) b.block(x, Y + 7, z, LEAVES, "persistent=true");
        b.block(-36, Y + 3, 33, Material.LANTERN);
    }

    /** The hub: a round deck, a fire to run back to, and a way off it in every direction. */
    private static void fork(BuildCanvas b) {
        roundDeck(b, 0, 65, 14, Y, PLANK, RAIL, (x, z) -> Math.abs(x) <= 2 || Math.abs(z - 65) <= 2);
        b.block(0, Y + 1, 65, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        for (int[] seat : new int[][]{{-2, 65, 0}, {2, 65, 0}, {0, 63, 1}, {0, 67, 1}}) b.block(seat[0], Y + 1, seat[1], Material.STRIPPED_DARK_OAK_LOG, seat[2] == 0 ? "axis=z" : "axis=x");
        for (int[] light : new int[][]{{-9, 57}, {9, 57}, {-9, 73}, {9, 73}}) lamp(b, light[0], Y + 1, light[1], 3, RAIL);
        // Where each way goes, cut into a post: the broken span south shows it still has to be lowered.
        for (int[] post : new int[][]{{-3, 53}, {3, 53}, {-3, 77}, {3, 77}}) b.block(post[0], Y + 1, post[1], Material.CHISELED_TUFF_BRICKS);
        bridge(b, Y, 78, 79, PLANK, RAIL);
        for (int x = -3; x <= 3; x++) {
            b.block(x, Y, 79, Material.STRIPPED_SPRUCE_LOG, "axis=x");
            if (Math.abs(x) >= 3) for (int y = Y + 1; y <= Y + 4; y++) b.block(x, y, 79, RAIL);
        }
        for (int y = Y + 2; y <= Y + 4; y++) for (int x : new int[]{-3, 3}) b.block(x, y, 84, RAIL);
    }

    private static void signalBoughs(BuildCanvas b) {
        deck(b, 23, 54, 49, 76, Y, PLANK, RAIL, (x, z) -> x == 23 && Math.abs(z - 65) <= 2 || z == 54 && Math.abs(x - 41) <= 1);
        giantTree(b, 36, 65, FOREST_FLOOR, CROWN_LINE, 3, 13, LOG, LEAVES);
        for (int[] board : BOARDS) {
            for (int y = Y + 1; y <= Y + 2; y++) b.block(board[0], y, board[1], RAIL);
            b.block(board[0], Y + 3, board[1], Material.TARGET);
            b.block(board[0] + (board[0] < 36 ? 1 : -1), Y + 1, board[1], Material.LANTERN);
        }
        b.block(25, Y + 1, 56, Material.BARREL, "facing=up");
        for (int[] light : new int[][]{{24, 60}, {24, 70}, {48, 60}, {48, 70}, {36, 55}, {36, 75}}) lamp(b, light[0], Y + 1, light[1], 2, RAIL);
        webs(b, Y + 1, new int[][]{{48, 55}, {48, 75}});
        webs(b, Y + 2, new int[][]{{48, 55}, {48, 75}});
    }

    /** A bare platform out in the wind, railed only on the side it's reached from. Stumps are the only cover. */
    private static void perch(BuildCanvas b) {
        for (int z = 53; z >= 47; z--) {
            int y = Y + 1 + 53 - z;
            for (int x = 40; x <= 42; x++) {
                b.block(x, y, z, Material.SPRUCE_STAIRS, "facing=north,half=bottom,shape=straight");
                for (int under = y - 1; under >= y - 2; under--) b.block(x, under, z, PLANK);
            }
            for (int x : new int[]{39, 43}) {
                b.block(x, y, z, PLANK);
                b.block(x, y + 1, z, RAIL);
            }
        }
        for (int x = 28; x <= 54; x++) for (int z = 26; z <= 46; z++) {
            b.block(x, PERCH, z, PLANK);
            boolean railed = x == 28 || z == 46;
            if (railed && !(z == 46 && Math.abs(x - 41) <= 1)) b.block(x, PERCH + 1, z, RAIL);
            if (Math.floorMod(x * 3 + z * 5, 11) == 0) b.block(x, PERCH - 1, z, Material.SPRUCE_FENCE);
        }
        for (int[] stump : new int[][]{{34, 32}, {48, 32}, {34, 41}, {48, 41}, {41, 29}}) for (int x = stump[0] - 1; x <= stump[0] + 1; x++) for (int z = stump[1] - 1; z <= stump[1] + 1; z++)
            for (int y = PERCH + 1; y <= PERCH + 3; y++) b.block(x, y, z, WOOD);
        for (int[] rack : new int[][]{{30, 45}, {52, 45}}) {
            b.block(rack[0], PERCH + 1, rack[1], Material.BARREL, "facing=up");
            b.block(rack[0], PERCH + 2, rack[1], Material.LANTERN);
        }
    }

    private static void silkWalk(BuildCanvas b) {
        deck(b, -40, 56, -23, 74, Y, PLANK, RAIL, (x, z) -> (x == -23 || x == -40) && Math.abs(z - 65) <= 2);
        for (int[] prop : new int[][]{{-29, 60}, {-35, 70}}) for (int y = Y + 1; y <= Y + 12; y++) for (int x = prop[0]; x <= prop[0] + 1; x++) for (int z = prop[1]; z <= prop[1] + 1; z++) b.block(x, y, z, WOOD);
        for (int x = -39; x <= -24; x++) for (int z = 57; z <= 73; z++) {
            double roll = noise(x, z, 161);
            if (Math.abs(z - 65) > 2 && roll < .08 && b.get(x, Y + 1, z) == null) b.block(x, Y + 1, z, Material.COBWEB);
            if (roll > .9) b.block(x, Y + 5, z, Material.COBWEB);
        }
        sac(b, -38, Y + 1, 58);
        sac(b, -38, Y + 1, 72);
        sac(b, -25, Y + 1, 72);
        for (int[] light : new int[][]{{-24, 58}, {-24, 72}, {-39, 62}, {-39, 68}}) lamp(b, light[0], Y + 1, light[1], 2, RAIL);
    }

    /** The Matron's Nest: the inside of a dead trunk, with two props to hide behind and her sacs round the wall. */
    private static void nest(BuildCanvas b) {
        int outside = NEST_OUTSIDE * NEST_OUTSIDE, inside = NEST_INSIDE * NEST_INSIDE;
        for (int x = NEST_X - NEST_OUTSIDE; x <= NEST_X + NEST_OUTSIDE; x++) for (int z = NEST_Z - NEST_OUTSIDE; z <= NEST_Z + NEST_OUTSIDE; z++) {
            int distance = (x - NEST_X) * (x - NEST_X) + (z - NEST_Z) * (z - NEST_Z);
            if (distance > outside) continue;
            b.block(x, Y, z, noise(x, z, 171) < .2 ? Material.MUDDY_MANGROVE_ROOTS : Material.DARK_OAK_PLANKS);
            b.block(x, Y - 1, z, WOOD);
            b.block(x, NEST_TOP + 1, z, WOOD);
            b.block(x, NEST_TOP + 2, z, LEAVES, "persistent=true");
            if (distance > inside) for (int y = FOREST_FLOOR; y <= NEST_TOP; y++) if (y != Y) b.block(x, y, z, noise(x * 3 + y, z, 173) < .25 ? WOOD : LOG, "");
            if (distance <= inside && distance >= 100) for (int y = NEST_TOP - 6; y <= NEST_TOP; y++)
                if (noise(x + y, z, 175) < .16) b.block(x, y, z, Material.COBWEB);
        }
        // The door, and the props.
        for (int x = -43; x <= -39; x++) for (int z = 63; z <= 67; z++) for (int y = Y + 1; y <= Y + 4; y++) b.block(x, y, z, Material.AIR);
        for (int[] prop : new int[][]{{NEST_X, 59}, {NEST_X, 71}}) for (int x = prop[0] - 1; x <= prop[0] + 1; x++) for (int z = prop[1] - 1; z <= prop[1] + 1; z++)
            for (int y = Y + 1; y <= NEST_TOP; y++) b.block(x, y, z, LOG, "axis=y");
        for (int[] sac : SACS) sac(b, sac[0], Y + 1, sac[1]);
        // Rows of strand across the floor, which her webs run down.
        for (int x = -77; x <= -43; x++) for (int z : new int[]{59, 71}) if (b.get(x, Y, z) != null) b.block(x, Y, z, Material.STRIPPED_DARK_OAK_WOOD);
        for (int z = 48; z <= 82; z++) for (int x : new int[]{-52, -68}) if (b.get(x, Y, z) != null) b.block(x, Y, z, Material.STRIPPED_DARK_OAK_WOOD);
        for (int angle = 0; angle < 12; angle++) {
            int x = NEST_X + (int) Math.round(Math.cos(angle * Math.PI / 6) * 17), z = NEST_Z + (int) Math.round(Math.sin(angle * Math.PI / 6) * 17);
            b.block(x, Y + 5, z, Material.SHROOMLIGHT);
        }
        // The wall that gives way, scarred so it reads as weak before it goes.
        for (int x = -62; x <= -54; x++) for (int z = 82; z <= 85; z++) for (int y = Y + 1; y <= Y + 4; y++) if (b.get(x, y, z) != null) b.block(x, y, z, Material.STRIPPED_DARK_OAK_WOOD);
    }

    /** From behind the nest to the foot of the climb. It's nothing until the wall comes down. */
    private static void catwalk(BuildCanvas b) {
        for (int x = -60; x <= -4; x++) {
            for (int z = 85; z <= 86; z++) b.block(x, Y, z, PLANK);
            if (x > -50) b.block(x, Y + 1, 84, RAIL);
            b.block(x, Y + 1, 87, RAIL);
            if (x % 8 == 0) b.block(x, Y + 2, 87, Material.LANTERN);
            if (x % 12 == 0) for (int y = Y - 1; y >= Y - 5; y--) b.block(x, y, 86, RAIL);
        }
    }

    /** The landing the span comes down on, and the long stair up to the Heart Gate. */
    private static void climb(BuildCanvas b) {
        for (int x = -3; x <= 3; x++) for (int z = 84; z <= 87; z++) b.block(x, Y, z, PLANK);
        for (int z = 84; z <= 87; z++) {
            if (z < 85 || z > 86) b.block(-3, Y + 1, z, RAIL);
            b.block(3, Y + 1, z, RAIL);
        }
        for (int z = RAMP; z < ANTE; z++) {
            int y = Y + 1 + z - RAMP;
            for (int x = -2; x <= 2; x++) {
                b.block(x, y, z, Material.SPRUCE_STAIRS, "facing=south,half=bottom,shape=straight");
                b.block(x, y - 1, z, PLANK);
                b.block(x, y - 2, z, PLANK);
            }
            for (int x : new int[]{-3, 3}) {
                for (int under = y - 2; under <= y; under++) b.block(x, under, z, PLANK);
                b.block(x, y + 1, z, RAIL);
                if ((z - RAMP) % 4 == 0) b.block(x, y + 2, z, Material.LANTERN);
            }
        }
        deck(b, -10, ANTE, 10, GATE - 1, HIGH, PLANK, RAIL, (x, z) -> (z == ANTE || z == GATE - 1) && Math.abs(x) <= 2);
        b.block(5, HIGH + 1, 105, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(6, HIGH + 1, 105, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        for (int[] light : new int[][]{{-9, 103}, {9, 103}, {-9, 113}, {9, 113}}) lamp(b, light[0], HIGH + 1, light[1], 3, RAIL);
        for (int x : new int[]{-3, 3}) for (int y = HIGH + 1; y <= HIGH + 6; y++) b.block(x, y, GATE, LOG, "axis=y");
        for (int x = -3; x <= 3; x++) b.block(x, HIGH + 6, GATE, LOG, "axis=x");
    }

    /**
     * The Heart Tree and the great deck round its foot. Three stretches of it can burn away, each with a
     * cistern on the sound middle of the deck. Roots in the boards mark where the thorns run.
     */
    private static void grove(BuildCanvas b) {
        roundDeck(b, 0, GROVE_Z, 22, HIGH, PLANK, RAIL, (x, z) -> z < GROVE_Z - 18 && Math.abs(x) <= 2);
        giantTree(b, 0, HEART_Z, FOREST_FLOOR, 112, 8, 20, LOG, LEAVES);
        // Roots running out from the trunk, set flush into the deck.
        for (int[] root : new int[][]{{0, 146, 0, 118}, {-5, 148, -19, 127}, {5, 148, 19, 127}, {-8, 150, -21, 150}, {8, 150, 21, 150}}) {
            int steps = Math.max(Math.abs(root[2] - root[0]), Math.abs(root[3] - root[1]));
            for (int step = 0; step <= steps; step++) {
                int x = root[0] + Math.round((root[2] - root[0]) * step / (float) steps), z = root[1] + Math.round((root[3] - root[1]) * step / (float) steps);
                if (b.get(x, HIGH, z) == PLANK) b.block(x, HIGH, z, step % 4 == 0 ? Material.MAGMA_BLOCK : Material.STRIPPED_DARK_OAK_WOOD);
            }
        }
        // The rot in the bark, where the fire comes from.
        for (int[] knot : KNOTS) for (int y = knot[1]; y >= HIGH + 1; y -= 2) b.block(knot[0], y, knot[2], y == knot[1] ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK);
        // The way out: a split in the foot of the trunk.
        for (int x = -1; x <= 1; x++) for (int z = 147; z <= 149; z++) for (int y = HIGH + 1; y <= HIGH + 3; y++) b.block(x, y, z, Material.AIR);
        b.block(0, HIGH + 1, 150, Material.SOUL_LANTERN);
        for (int[] cistern : new int[][]{{-7, 127}, {-9, 136}, {9, 136}}) {
            b.block(cistern[0], HIGH + 1, cistern[1], Material.WATER_CAULDRON, "level=3");
            b.block(cistern[0], HIGH + 2, cistern[1], RAIL);
            b.block(cistern[0], HIGH + 3, cistern[1], Material.LANTERN);
        }
        for (int index = 0; index < 10; index++) {
            double angle = Math.PI + Math.PI * index / 9;
            int x = (int) Math.round(Math.cos(angle) * 20), z = GROVE_Z + (int) Math.round(Math.sin(angle) * 20);
            if (Math.abs(x) > 3) lamp(b, x, HIGH + 1, z, 3, RAIL);
        }
        for (int x = -21; x <= 21; x++) for (int z = GROVE_Z - 21; z <= GROVE_Z + 21; z++) {
            if (b.get(x, HIGH, z) != PLANK || b.get(x, HIGH + 1, z) != null || Math.abs(x) <= 2) continue;
            double roll = noise(x, z, 181);
            if (roll < .04 && (Math.abs(x) > 10 || z < 126)) b.block(x, HIGH + 1, z, Material.SWEET_BERRY_BUSH, "age=3");
            else if (roll < .12) b.block(x, HIGH + 1, z, Material.MOSS_CARPET);
        }
    }
}
