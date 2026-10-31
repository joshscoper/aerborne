package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.CanopyKit.*;
import static net.nerdsmp.paper.expedition.SampleKit.leafPad;
import static net.nerdsmp.paper.expedition.SampleKit.noise;

/**
 * Thornwood Canopy, second edition: a night climb through a ranger village with a long way down.
 * Rootgate opens on the Low Boughs, two round decks wound about two great trunks, which lead to the
 * Fork, the hub. East are the Signal Boughs, whose boards lower the span, and above them Verrin's Perch,
 * an optional fight in the wind. West is the Silk Walk and the Matron's Nest, a dead trunk; killing her
 * breaks open a catwalk to the climb. The climb winds round a trunk to the Heart Gate and the Grove.
 *
 * <p>Runs south (+Z); low decks at Y63, the perch at Y70 and the high decks at Y77, so players stand a
 * block above those. Anyone who falls is caught ({@code falls:} in {@code expeditions/thornwood_canopy.yml}).
 */
final class ThornwoodTemplate {
    static final int Y = 63, PERCH = 70, HIGH = 77;
    private static final int FOREST_FLOOR = 30, CROWN_LINE = 104, SEA_OF_LEAVES = 44;
    private static final int STAIR_Z = 95, STAIR_TRUNK = 6;
    private static final int ANTE = 106, GATE = 115, GROVE_Z = 137, HEART_Z = 159;
    private static final int NEST_X = -60, NEST_Z = 65, NEST_INSIDE = 18, NEST_OUTSIDE = 20, NEST_TOP = 88;
    private static final Material WOOD = Material.DARK_OAK_WOOD;
    private static final Material LEAVES = Material.DARK_OAK_LEAVES;
    /** Her sacs, round the wall of the nest. The door side stays clear. */
    private static final int[][] SACS = {{-49, 54}, {-60, 50}, {-71, 54}, {-75, 65}, {-71, 76}, {-60, 80}, {-49, 76}};
    private static final int[][] BOARD_POSTS = {{27, 58}, {45, 58}, {27, 72}, {45, 72}};
    private static final int[][] KNOTS = {{-5, 80, 149}, {5, 80, 149}, {-3, 84, 148}, {3, 84, 148}, {0, 88, 147}, {-7, 83, 152}, {7, 83, 152}};

    private ThornwoodTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        seaOfLeaves(b);
        forest(b);
        rootgate(b);
        rope(b, 0, 13, 0, 17, Y, 0);
        lowBoughs(b);
        hiddenHollow(b);
        rope(b, 0, 46, 0, 53, Y, 1);
        fork(b);
        rope(b, 14, 65, 23, 65, Y, 2);
        signalBoughs(b);
        perch(b);
        rope(b, -14, 65, -22, 65, Y, 2);
        silkWalk(b);
        nest(b);
        catwalk(b);
        climb(b);
        grove(b);
        SampleChests.place(b, "thornwood_canopy");
        return b.freeze();
    }

    /** Treetops a long way down, so a fall has something to fall into. */
    private static void seaOfLeaves(BuildCanvas b) {
        for (int x = -96; x <= 84; x += 17) for (int z = -30; z <= 176; z += 17) {
            int nudge = (int) (noise(x, z, 191) * 9) - 4;
            blob(b, x + nudge, SEA_OF_LEAVES - (int) (noise(z, x, 192) * 6), z - nudge, 9, 4, 193 + x + z);
        }
    }

    private static void tree(BuildCanvas b, int cx, int cz, double radius, int salt) {
        trunk(b, cx, cz, FOREST_FLOOR, CROWN_LINE, radius, salt);
        crown(b, cx, CROWN_LINE, cz, 15, salt);
    }

    /** The great trees the village hangs between, some with huts of their own high overhead. */
    private static void forest(BuildCanvas b) {
        int[][] trees = {{-14, -2, 4}, {-30, 10, 5}, {26, 8, 5}, {22, 44, 4}, {-24, 47, 5}, {24, 92, 5}, {-26, 100, 5}, {62, 66, 6}, {-40, 116, 6}, {42, 116, 6}, {38, 152, 6}, {-38, 152, 6}};
        for (int index = 0; index < trees.length; index++) tree(b, trees[index][0], trees[index][1], trees[index][2], 7 + index * 3);
        // Far-off huts, so the dark past the rails has windows in it.
        perched(b, 26, 8, 5, 88, THATCH, 1);
        perched(b, 62, 66, 6, 86, VERDIGRIS, 2);
        perched(b, -40, 116, 6, 92, SHINGLES, 0);
        perched(b, -30, 10, 5, 84, MOSSY, 0);
        perched(b, 42, 116, 6, 90, THATCH, 3);
        perched(b, 38, 152, 6, 94, MOSSY, 2);
        perched(b, -38, 152, 6, 96, VERDIGRIS, 1);
    }

    /** A ring of deck round a trunk, well out of reach, with a hut on it and lanterns hung beneath. */
    private static void perched(BuildCanvas b, int cx, int cz, int radius, int y, Roof roof, int side) {
        int deck = radius + 5;
        platform(b, cx, cz, deck, y, (x, z) -> false);
        underside(b, cx, cz, deck, y, radius, cx + cz);
        trunk(b, cx, cz, y - 2, y + 8, radius, cx);
        int[][] sides = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        hut(b, cx + sides[side][0] * (deck - 2), y, cz + sides[side][1] * (deck - 2), 2, 3, roof, side);
        for (int index = 0; index < 6; index++) {
            double angle = index * Math.TAU / 6 + .4;
            lantern(b, cx + (int) Math.round(Math.cos(angle) * (deck - 1)), y - 1, cz + (int) Math.round(Math.sin(angle) * (deck - 1)), 2 + index % 3);
        }
    }

    private static void webs(BuildCanvas b, int y, int[][] spots) {
        for (int[] web : spots) b.block(web[0], y, web[1], Material.COBWEB);
    }

    private static void sac(BuildCanvas b, int x, int y, int z) {
        b.block(x, y, z, Material.WHITE_WOOL);
        b.block(x, y + 1, z, Material.COBWEB);
    }

    private static void post(BuildCanvas b, int x, int y, int z, int height) {
        for (int up = 0; up < height; up++) b.block(x, y + up, z, RAIL);
        b.block(x, y + height, z, Material.LANTERN);
    }

    /** Moss and red caps across a deck, off the walkway. */
    private static void dress(BuildCanvas b, int x1, int z1, int x2, int z2, int y, int salt) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            if (!boarded(b.get(x, y, z)) || b.get(x, y + 1, z) != null) continue;
            double roll = noise(x, z, salt);
            if (roll < .1) b.block(x, y + 1, z, Material.MOSS_CARPET);
            else if (roll < .115) b.block(x, y + 1, z, Material.RED_MUSHROOM);
        }
    }

    private static void rootgate(BuildCanvas b) {
        platform(b, 0, 3, 10, Y, (x, z) -> z > 11 && Math.abs(x) <= 2 || x < -7 && z < 3);
        underside(b, 0, 3, 10, Y, 1, 3);
        // The ranger's lodge on the north side.
        pod(b, 0, Y, -9, 4, 4, THATCH, 1, 5);
        for (int x = -2; x <= 2; x++) for (int z = -5; z <= -3; z++) b.block(x, Y, z, Material.SPRUCE_PLANKS);
        for (int x = -2; x <= 2; x++) b.block(x, Y + 1, -5, Material.AIR);
        b.block(-3, Y + 1, 7, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(-4, Y + 1, 7, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        b.block(-2, Y + 1, 8, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
        b.block(6, Y + 1, 1, Material.BARREL, "facing=up");
        b.block(6, Y + 2, 1, Material.LANTERN);
        b.block(7, Y + 1, 2, Material.TARGET);
        post(b, -7, Y + 1, 9, 3);
        post(b, 7, Y + 1, 9, 3);
        // The branch out west to the lookout tree, and the watch hut on it.
        limb(b, -9, Y - 1, 1, -14, Y + 4, -2, 2.5, 3, 11);
        limb(b, -14, 76, -2, -2, 86, 4, 2.5, 1, 13);
        limb(b, -14, 80, -2, -24, 86, 12, 2.5, 1, 15);
        platform(b, -14, -2, 9, 74, (x, z) -> false);
        underside(b, -14, -2, 9, 74, 4, 17);
        trunk(b, -14, -2, 72, 84, 4, 18);
        hut(b, -21, 74, -2, 2, 3, MOSSY, 0);
        for (int[] light : new int[][]{{-2, 4, 84}, {4, 8, 83}, {-6, 1, 83}, {-3, -3, 85}}) lantern(b, light[0], light[2], light[1], 7);
    }

    /** Two round decks, each about its own great trunk, merged into one winding walk. */
    private static void lowBoughs(BuildCanvas b) {
        platform(b, -8, 27, 14, Y, (x, z) -> z < 16 && Math.abs(x) <= 2 || x < -20 && Math.abs(z - 27) <= 1 || x + z * .4 > 2 && z > 17);
        platform(b, 8, 37, 14, Y, (x, z) -> z > 47 && Math.abs(x) <= 2 || x - z * .4 < -8 && z < 44 || z > 45 && x < 2);
        // Where the two rings cross, no rail.
        for (int x = -6; x <= 7; x++) for (int z = 22; z <= 46; z++)
            if (Math.hypot(x + 8, z - 27) <= 14.4 && Math.hypot(x - 8, z - 37) <= 14.4) {
                if (b.get(x, Y + 1, z) == RAIL) b.block(x, Y + 1, z, Material.AIR);
            }
        for (int x = -2; x <= 2; x++) for (int z = 14; z <= 22; z++) if (b.get(x, Y + 1, z) == RAIL && z > 17) b.block(x, Y + 1, z, Material.AIR);
        for (int x = -2; x <= 2; x++) for (int z = 40; z <= 47; z++) if (b.get(x, Y + 1, z) == RAIL) b.block(x, Y + 1, z, Material.AIR);
        underside(b, -8, 27, 14, Y, 3, 21);
        underside(b, 8, 37, 14, Y, 3, 23);
        trunk(b, -8, 27, FOREST_FLOOR, CROWN_LINE, 4, 24);
        crown(b, -8, CROWN_LINE, 27, 15, 25);
        trunk(b, 8, 37, FOREST_FLOOR, CROWN_LINE, 4, 26);
        crown(b, 8, CROWN_LINE, 37, 15, 27);
        // Huts up on the trunks, reached by nobody: their light falls on the walk below.
        high(b, -8, 27, 4, 80, SHINGLES, 2);
        high(b, 8, 37, 4, 84, THATCH, 0);
        rope(b, -3, 34, 1, 34, 80, 0);
        // A hut on a pod off each deck.
        pod(b, -21, Y, 40, 3, 3, MOSSY, 0, 29);
        rope(b, -18, 37, -14, 37, Y, 0);
        pod(b, 22, Y, 26, 3, 3, THATCH, 2, 31);
        rope(b, 19, 29, 19, 33, Y, 0);
        for (int[] light : new int[][]{{-3, 20}, {4, 44}, {-14, 19}, {17, 42}, {-16, 34}, {15, 30}}) post(b, light[0], Y + 1, light[1], 2);
        webs(b, Y + 1, new int[][]{{-18, 22}, {-12, 37}, {17, 45}, {18, 30}});
        webs(b, Y + 2, new int[][]{{-18, 22}, {-12, 37}});
        dress(b, -22, 13, 22, 51, Y, 151);
    }

    private static void high(BuildCanvas b, int cx, int cz, int radius, int y, Roof roof, int side) {
        int deck = radius + 5;
        platform(b, cx, cz, deck, y, (x, z) -> false);
        underside(b, cx, cz, deck, y, radius, cx + y);
        int[][] sides = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        hut(b, cx + sides[side][0] * (deck - 2), y, cz + sides[side][1] * (deck - 2), 2, 3, roof, side);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.TAU / 8 + .2;
            lantern(b, cx + (int) Math.round(Math.cos(angle) * (deck - 1)), y - 1, cz + (int) Math.round(Math.sin(angle) * (deck - 1)), 3 + index % 4);
        }
    }

    /** Off the west rail, out across the drop: pads to a dead stump with something still in it. */
    private static void hiddenHollow(BuildCanvas b) {
        leafPad(b, -25, Y, 27, LEAVES, Material.DARK_OAK_LOG);
        leafPad(b, -29, Y + 1, 28, LEAVES, Material.DARK_OAK_LOG);
        leafPad(b, -33, Y + 1, 26, LEAVES, Material.DARK_OAK_LOG);
        for (int x = -43; x <= -38; x++) for (int z = 23; z <= 30; z++) b.block(x, Y + 2, z, Material.SPRUCE_SLAB, "type=top");
        b.block(-37, Y + 2, 26, Material.SPRUCE_SLAB, "type=top");
        b.block(-36, Y + 2, 26, Material.SPRUCE_SLAB, "type=top");
        for (int y = Y + 3; y <= Y + 6; y++) for (int z = 23; z <= 30; z++) b.block(-44, y, z, WOOD);
        for (int y = Y + 3; y <= Y + 6; y++) for (int x = -43; x <= -38; x++) {
            b.block(x, y, 22, WOOD);
            b.block(x, y, 31, WOOD);
        }
        for (int x = -44; x <= -37; x++) for (int z = 22; z <= 31; z++) b.block(x, Y + 7, z, LEAVES, "persistent=true");
        b.block(-43, Y + 3, 30, Material.LANTERN);
    }

    /** The hub: a round deck, a fire to run back to, a way off it each way, and a hut in each corner. */
    private static void fork(BuildCanvas b) {
        platform(b, 0, 65, 14, Y, (x, z) -> Math.abs(x) <= 2 || Math.abs(z - 65) <= 2);
        underside(b, 0, 65, 14, Y, 1, 33);
        b.block(0, Y + 1, 65, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        for (int[] seat : new int[][]{{-2, 65, 0}, {2, 65, 0}, {0, 63, 1}, {0, 67, 1}}) b.block(seat[0], Y + 1, seat[1], Material.STRIPPED_DARK_OAK_LOG, seat[2] == 0 ? "axis=z" : "axis=x");
        for (int[] corner : new int[][]{{-14, 51, 3}, {14, 51, 3}, {-14, 79, 1}, {14, 79, 1}}) {
            int x = corner[0], z = corner[1];
            pod(b, x, Y, z, 3, 4, x < 0 ? (z < 65 ? MOSSY : SHINGLES) : z < 65 ? VERDIGRIS : THATCH, z < 65 ? 1 : 3, 35 + x + z);
        }
        // Great limbs over the deck, reaching across from the trees round it, with lanterns on long chains.
        limb(b, -24, 86, 47, 6, 90, 62, 2.5, 1, 41);
        limb(b, 22, 84, 44, -6, 88, 70, 2.5, 1, 43);
        limb(b, 24, 80, 92, 4, 90, 72, 2.5, 1, 45);
        for (int[] light : new int[][]{{-9, 56, 86}, {-3, 60, 87}, {9, 56, 85}, {3, 63, 86}, {-8, 72, 83}, {8, 74, 83}, {0, 69, 86}})
            lantern(b, light[0], light[2], light[1], light[2] - 73);
        // Where each way goes: the broken span south shows it still has to be lowered.
        rope(b, 0, 77, 0, 79, Y, 0);
        for (int x : new int[]{-3, 3}) for (int y = Y + 1; y <= Y + 4; y++) b.block(x, y, 79, RAIL);
        b.block(-3, Y + 5, 79, Material.LANTERN);
        b.block(3, Y + 5, 79, Material.LANTERN);
        dress(b, -14, 51, 14, 79, Y, 153);
    }

    private static void signalBoughs(BuildCanvas b) {
        platform(b, 36, 65, 14, Y, (x, z) -> x < 26 && Math.abs(z - 65) <= 2 || z < 54 && Math.abs(x - 41) <= 1);
        underside(b, 36, 65, 14, Y, 4, 47);
        trunk(b, 36, 65, FOREST_FLOOR, CROWN_LINE, 4.5, 48);
        crown(b, 36, CROWN_LINE, 65, 15, 49);
        for (int[] board : BOARD_POSTS) {
            for (int y = Y + 1; y <= Y + 2; y++) b.block(board[0], y, board[1], RAIL);
            b.block(board[0], Y + 3, board[1], Material.TARGET);
            b.block(board[0] + (board[0] < 36 ? 1 : -1), Y + 1, board[1], Material.LANTERN);
        }
        b.block(26, Y + 1, 57, Material.BARREL, "facing=up");
        pod(b, 52, Y, 52, 3, 4, VERDIGRIS, 2, 51);
        rope(b, 46, 55, 49, 55, Y, 0);
        pod(b, 52, Y, 78, 3, 4, SHINGLES, 2, 53);
        rope(b, 46, 75, 49, 75, Y, 0);
        pod(b, 36, Y, 84, 3, 3, THATCH, 3, 55);
        rope(b, 36, 79, 36, 81, Y, 0);
        for (int[] light : new int[][]{{24, 60}, {24, 70}, {48, 60}, {48, 70}}) post(b, light[0], Y + 1, light[1], 2);
        high(b, 36, 65, 5, 84, MOSSY, 1);
        dress(b, 22, 51, 50, 79, Y, 155);
    }

    /** A bare platform out in the wind, railed only on the sides it's reached from. Stumps are the only cover. */
    private static void perch(BuildCanvas b) {
        for (int z = 53; z >= 47; z--) {
            int y = Y + 1 + 53 - z;
            for (int x = 40; x <= 42; x++) {
                b.block(x, y, z, Material.SPRUCE_STAIRS, "facing=north,half=bottom,shape=straight");
                for (int under = y - 1; under >= y - 2; under--) b.block(x, under, z, Material.SPRUCE_PLANKS);
            }
            for (int x : new int[]{39, 43}) {
                b.block(x, y, z, Material.STRIPPED_SPRUCE_WOOD);
                b.block(x, y + 1, z, RAIL);
            }
        }
        for (int x = 26; x <= 56; x++) for (int z = 24; z <= 48; z++) {
            double across = (x - 41) / 14.4, along = (z - 36) / 11.4, inside = across * across + along * along;
            if (inside > 1) continue;
            b.block(x, PERCH, z, board(x, z, Math.hypot(x - 41, (z - 36) * 1.26), 14.4));
            if (inside > .87 && (x < 34 || z > 42) && !(z >= 46 && Math.abs(x - 41) <= 1)) b.block(x, PERCH + 1, z, RAIL);
        }
        underside(b, 41, 36, 11, PERCH, 2, 57);
        for (int[] stump : new int[][]{{34, 32}, {48, 32}, {34, 41}, {48, 41}, {41, 29}}) for (int x = stump[0] - 1; x <= stump[0] + 1; x++) for (int z = stump[1] - 1; z <= stump[1] + 1; z++)
            for (int y = PERCH + 1; y <= PERCH + 3; y++) b.block(x, y, z, y == PERCH + 3 && (x + z) % 2 == 0 ? Material.MOSS_BLOCK : WOOD);
        for (int[] rack : new int[][]{{31, 42}, {37, 45}}) {
            b.block(rack[0], PERCH + 1, rack[1], Material.BARREL, "facing=up");
            b.block(rack[0], PERCH + 2, rack[1], Material.LANTERN);
        }
        // His rope, and the limb it hangs from.
        limb(b, 62, 96, 66, 41, 90, 36, 3, 1.5, 59);
        limb(b, 26, 88, 8, 41, 91, 36, 3, 1.5, 61);
    }

    private static void silkWalk(BuildCanvas b) {
        platform(b, -31, 65, 10, Y, (x, z) -> Math.abs(z - 65) <= 2);
        underside(b, -31, 65, 10, Y, 1, 63);
        for (int[] prop : new int[][]{{-29, 60}, {-35, 70}}) for (int y = Y + 1; y <= Y + 12; y++) for (int x = prop[0]; x <= prop[0] + 1; x++) for (int z = prop[1]; z <= prop[1] + 1; z++) b.block(x, y, z, WOOD);
        // A hut the brood took, with the silk still on it.
        pod(b, -31, Y, 80, 3, 3, SHINGLES, 3, 65);
        rope(b, -31, 75, -31, 76, Y, 0);
        pod(b, -31, Y, 50, 3, 3, MOSSY, 1, 67);
        rope(b, -31, 54, -31, 55, Y, 0);
        for (int x = -40; x <= -22; x++) for (int z = 45; z <= 85; z++) {
            double roll = noise(x, z, 161);
            if (Math.abs(z - 65) > 2 && roll < .09 && b.get(x, Y, z) != null && b.get(x, Y + 1, z) == null) b.block(x, Y + 1, z, Material.COBWEB);
            for (int y = Y + 2; y <= Y + 8; y++) if (roll > .92 && noise(x, y, 162) < .3 && b.get(x, y, z) == null) b.block(x, y, z, Material.COBWEB);
        }
        sac(b, -37, Y + 1, 59);
        sac(b, -37, Y + 1, 71);
        sac(b, -25, Y + 1, 71);
        for (int[] light : new int[][]{{-24, 59}, {-24, 71}}) post(b, light[0], Y + 1, light[1], 2);
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
            if (distance > inside) for (int y = FOREST_FLOOR; y <= NEST_TOP; y++) if (y != Y) b.block(x, y, z, noise(x * 3 + y, z, 173) < .25 ? WOOD : Material.DARK_OAK_LOG, "");
            if (distance <= inside && distance >= 100) for (int y = NEST_TOP - 6; y <= NEST_TOP; y++)
                if (noise(x + y, z, 175) < .16) b.block(x, y, z, Material.COBWEB);
        }
        crown(b, NEST_X, NEST_TOP + 2, NEST_Z, 14, 69);
        // The door, and the props.
        for (int x = -43; x <= -39; x++) for (int z = 63; z <= 67; z++) for (int y = Y + 1; y <= Y + 4; y++) b.block(x, y, z, Material.AIR);
        for (int[] prop : new int[][]{{NEST_X, 59}, {NEST_X, 71}}) for (int x = prop[0] - 1; x <= prop[0] + 1; x++) for (int z = prop[1] - 1; z <= prop[1] + 1; z++)
            for (int y = Y + 1; y <= NEST_TOP; y++) b.block(x, y, z, Material.DARK_OAK_LOG, "axis=y");
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
            int drop = (int) Math.round(Math.sin((x + 60) / 56D * Math.PI));
            for (int z = 85; z <= 86; z++) b.block(x, Y - drop, z, x % 2 == 0 ? Material.SPRUCE_PLANKS : Material.STRIPPED_SPRUCE_WOOD);
            if (x > -50) b.block(x, Y + 1 - drop, 84, RAIL);
            b.block(x, Y + 1 - drop, 87, RAIL);
            if (x % 8 == 0) b.block(x, Y + 2 - drop, 87, Material.LANTERN);
        }
    }

    /** The landing the span comes down on, the stair round the climbing tree, and the Heart Gate at the top. */
    private static void climb(BuildCanvas b) {
        for (int x = -3; x <= 3; x++) for (int z = 84; z <= 87; z++) b.block(x, Y, z, Material.SPRUCE_PLANKS);
        for (int z = 84; z <= 87; z++) {
            if (z < 85 || z > 86) b.block(-3, Y + 1, z, RAIL);
        }
        trunk(b, 0, STAIR_Z, FOREST_FLOOR, CROWN_LINE, STAIR_TRUNK, 71);
        crown(b, 0, CROWN_LINE, STAIR_Z, 15, 72);
        // The stair winds up the east side, half a block at a time, between the trunk and a rail.
        for (int x = -1; x <= 11; x++) for (int z = 84; z <= 106; z++) {
            double dx = x, dz = z - STAIR_Z, distance = Math.hypot(dx, dz);
            if (distance < 6.6 || distance > 10.6 || dx < 0) continue;
            double turned = (Math.atan2(dz, dx) + Math.PI / 2) / Math.PI;
            int half = (int) Math.round(Math.clamp(turned, 0D, 1D) * 28);
            int y = Y + half / 2;
            if (half % 2 == 1) b.block(x, y + 1, z, Material.SPRUCE_SLAB, "type=bottom");
            b.block(x, y, z, Material.SPRUCE_PLANKS);
            b.block(x, y - 1, z, Material.STRIPPED_DARK_OAK_WOOD);
            if (distance > 9.7) {
                b.block(x, y + 1 + half % 2, z, RAIL);
                if (Math.floorMod(x * 3 + z, 9) == 0) b.block(x, y + 2 + half % 2, z, Material.LANTERN);
            }
            if (noise(x, z, 177) < .12) strand(b, x, y - 2, z, 2 + (int) (noise(z, x, 178) * 6), true);
        }
        // The deck at the top.
        for (int x = -12; x <= 12; x++) for (int z = ANTE; z < GATE; z++) {
            double round = Math.hypot(Math.max(0, Math.abs(x) - 8), Math.max(0, ANTE + 4 - z));
            if (round > 4.4) continue;
            b.block(x, HIGH, z, (x + z * 3) % 5 == 0 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
            boolean rim = round > 3.5 || Math.abs(x) == 12;
            if (rim && !(z <= ANTE + 1 && x >= 0 && x <= 10)) b.block(x, HIGH + 1, z, RAIL);
        }
        underside(b, 0, 110, 9, HIGH, 1, 73);
        b.block(6, HIGH + 1, 110, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        b.block(7, HIGH + 1, 110, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        // The gatehouse: two huts and a lintel of living root.
        pod(b, -13, HIGH, 110, 3, 4, VERDIGRIS, 0, 75);
        pod(b, 13, HIGH, 112, 3, 4, MOSSY, 2, 77);
        for (int x : new int[]{-3, 3}) for (int y = HIGH + 1; y <= HIGH + 7; y++) for (int z = GATE - 1; z <= GATE; z++) b.block(x, y, z, Material.DARK_OAK_LOG, "axis=y");
        limb(b, -3, HIGH + 7, GATE, 3, HIGH + 7, GATE, 1.5, 1.5, 79);
        lantern(b, -3, HIGH + 5, GATE - 2, 2);
        lantern(b, 3, HIGH + 5, GATE - 2, 2);
    }

    /**
     * The Heart Tree and the great deck round its foot, with the old village hanging in its limbs overhead.
     * Three stretches of deck can burn away, each with a cistern on the sound middle. Roots in the boards
     * mark where the thorns run.
     */
    private static void grove(BuildCanvas b) {
        platform(b, 0, GROVE_Z, 22, HIGH, (x, z) -> z < GROVE_Z - 18 && Math.abs(x) <= 2);
        trunk(b, 0, HEART_Z, FOREST_FLOOR, 124, 12, 81);
        // Buttress roots coming down to the deck on the far side, clear of the fight.
        for (int[] root : new int[][]{{-14, 100, 150, -24, 76, 150}, {14, 100, 150, 24, 76, 150}, {-10, 104, 170, -20, 70, 176}, {10, 104, 170, 20, 70, 176}, {0, 104, 174, 0, 66, 182}})
            limb(b, root[0], root[1], root[2], root[3], root[4], root[5], 4, 2, 90 + root[0]);
        crown(b, 0, 128, HEART_Z, 26, 82);
        for (int[] crownlet : new int[][]{{-24, 142, 112}, {24, 142, 112}, {-14, 118, 116}, {14, 118, 116}}) {
            limb(b, 0, 104, HEART_Z, crownlet[0], crownlet[2], crownlet[1], 4, 1.5, 83 + crownlet[0]);
            blob(b, crownlet[0], crownlet[2] + 3, crownlet[1], 11, 5, 84 + crownlet[0]);
        }
        // Roots running out from the trunk, set flush into the deck.
        for (int[] root : new int[][]{{0, 143, 0, 118}, {-6, 142, -19, 127}, {6, 142, 19, 127}, {-11, 148, -17, 150}, {11, 148, 17, 150}}) {
            int steps = Math.max(Math.abs(root[2] - root[0]), Math.abs(root[3] - root[1]));
            for (int step = 0; step <= steps; step++) {
                int x = root[0] + Math.round((root[2] - root[0]) * step / (float) steps), z = root[1] + Math.round((root[3] - root[1]) * step / (float) steps);
                if (boarded(b.get(x, HIGH, z))) b.block(x, HIGH, z, step % 4 == 0 ? Material.MAGMA_BLOCK : Material.STRIPPED_DARK_OAK_WOOD);
            }
        }
        // The rot in the bark, where the fire comes from.
        for (int[] knot : KNOTS) {
            for (int z = knot[2] + 1; z <= knot[2] + 3; z++) b.block(knot[0], knot[1], z, WOOD);
            for (int y = knot[1]; y >= HIGH + 1; y -= 2) {
                b.block(knot[0], y, knot[2], y == knot[1] ? Material.SHROOMLIGHT : Material.MAGMA_BLOCK);
                b.block(knot[0], y, knot[2] + 1, WOOD);
            }
        }
        // The way out: a split in the foot of the trunk.
        for (int x = -1; x <= 1; x++) for (int z = 145; z <= 152; z++) for (int y = HIGH + 1; y <= HIGH + 3; y++) b.block(x, y, z, Material.AIR);
        for (int x = -1; x <= 1; x++) for (int z = 145; z <= 152; z++) b.block(x, HIGH, z, Material.SPRUCE_PLANKS);
        for (int z = 147; z <= 153; z++) for (int y = HIGH + 1; y <= HIGH + 4; y++) for (int x : new int[]{-2, 2}) b.block(x, y, z, WOOD);
        for (int z = 147; z <= 153; z++) for (int x = -2; x <= 2; x++) b.block(x, HIGH + 4, z, WOOD);
        for (int y = HIGH + 1; y <= HIGH + 4; y++) for (int x = -2; x <= 2; x++) b.block(x, y, 153, WOOD);
        b.block(0, HIGH + 1, 152, Material.SOUL_LANTERN);
        for (int[] cistern : new int[][]{{-7, 127}, {-9, 136}, {9, 136}}) {
            b.block(cistern[0], HIGH + 1, cistern[1], Material.WATER_CAULDRON, "level=3");
            b.block(cistern[0], HIGH + 2, cistern[1], RAIL);
            b.block(cistern[0], HIGH + 3, cistern[1], Material.LANTERN);
        }
        for (int index = 0; index < 10; index++) {
            double angle = Math.PI + Math.PI * index / 9;
            int x = (int) Math.round(Math.cos(angle) * 20), z = GROVE_Z + (int) Math.round(Math.sin(angle) * 20);
            if (Math.abs(x) > 3) post(b, x, HIGH + 1, z, 3);
        }
        // The old village in the limbs: a ring of huts and bridges high over the fight, and its lanterns on long chains.
        int[][] village = {{-20, 150, 100, 0}, {20, 148, 102, 2}, {-13, 130, 104, 1}, {13, 128, 108, 1}, {0, 122, 112, 1}};
        for (int[] hut : village) pod(b, hut[0], hut[2], hut[1], 3, 4, hut[3] == 0 ? THATCH : hut[3] == 2 ? VERDIGRIS : hut[0] < 0 ? MOSSY : SHINGLES, hut[3], 85 + hut[0]);
        for (int[] hut : village) limb(b, 0, hut[2] + 8, HEART_Z - 4, hut[0], hut[2] + 5, hut[1], 3, 1, 87 + hut[0]);
        for (int[] light : new int[][]{{-17, 120, 98}, {17, 120, 98}, {-8, 118, 104}, {8, 116, 104}, {-15, 135, 100}, {15, 136, 98}, {-5, 137, 106}, {6, 139, 106}, {-19, 142, 95}, {19, 142, 95}, {0, 128, 106}})
            lantern(b, light[0], light[2], light[1], light[2] - 88 - Math.floorMod(light[0] + light[1], 5));
        for (int x = -21; x <= 21; x++) for (int z = GROVE_Z - 21; z <= GROVE_Z + 21; z++) {
            if (!boarded(b.get(x, HIGH, z)) || b.get(x, HIGH + 1, z) != null || Math.abs(x) <= 2) continue;
            double roll = noise(x, z, 181);
            if (roll < .04 && (Math.abs(x) > 10 || z < 126)) b.block(x, HIGH + 1, z, Material.SWEET_BERRY_BUSH, "age=3");
            else if (roll < .12) b.block(x, HIGH + 1, z, Material.MOSS_CARPET);
        }
    }
}
