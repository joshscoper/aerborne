package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.AqueductTemplate.*;
import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The Great Dam and the two fights under it. The dam is curved like the inside of a bowl, so it wraps the Tidewarden's
 * Basin the way the Wardens' arenas are wrapped: the eye comes out of the door and goes straight up her figure, cut proud
 * of the face in green bronze, with the water leaving her hands. Off the Long Span, down the tower, is the Silt Bed.
 */
final class AqueductDam {
    static final int BASIN_Z = 276, BASIN_RADIUS = 28, CREST = 146, FACE_Z = 332, DAM_HALF = 112;
    static final int SILT_X = -52, SILT_Z = BASTION_Z, SILT_Y = LAKE + 2, SILT_RADIUS = 26;
    private static final Material COPPER = VERDIGRIS, COPPER_B = Material.WAXED_WEATHERED_COPPER, COPPER_DARK = Material.WAXED_WEATHERED_CUT_COPPER;

    private AqueductDam() { }

    static void build(BuildCanvas b) {
        apron(b);
        dam(b);
        figure(b);
        spouts(b);
        crest(b);
        cloudfall(b);
    }

    /** The face's z at this x: furthest away in the middle, coming forward to the cliffs. */
    private static int faceZ(int x) { return FACE_Z - (int) Math.round(x * x / (double) (DAM_HALF * DAM_HALF) * 44); }

    private static void dam(BuildCanvas b) {
        for (int x = -DAM_HALF - 8; x <= DAM_HALF + 8; x++) {
            int face = faceZ(x);
            for (int y = LAKE - 2; y <= CREST; y++) {
                int batter = Math.max(0, (CREST - y) / 7);
                int front = face - batter;
                b.block(x, y, front, damStone(x, y));
                b.block(x, y, front + 1, BEDS);
            }
            // A buttress every so often, carried up the slope.
            if (Math.floorMod(x + 6, 13) <= 1) for (int y = LAKE; y <= CREST - 8; y++) {
                int batter = Math.max(0, (CREST - y) / 7);
                b.block(x, y, face - batter - 1, y % 9 == 0 ? DRESSED : WORN);
                b.block(x, y, face - batter - 2, Material.CHISELED_STONE_BRICKS);
            }
        }
    }

    private static Material damStone(int x, int y) {
        double stain = smooth(x, y / 3, 6, 931), course = noise(x, y, 932);
        if (y < LAKE + 6) return course < .5 ? TIDE : Material.PRISMARINE_BRICKS;
        if (Math.floorMod(y, 12) == 0) return DRESSED;
        if (stain > .62 && y < 110) return Material.MOSSY_STONE_BRICKS;
        if (stain < .38) return BEDS;
        return course < .08 ? Material.CRACKED_STONE_BRICKS : WORN;
    }

    private static double blob(double a, double h, double ca, double ch, double wide, double high) { return Math.max(0, 1 - Math.pow((a - ca) / wide, 2) - Math.pow((h - ch) / high, 2)); }

    /** Half the figure's width at this height: skirts spreading to the hem, full sleeves, a sloped shoulder, then nothing. */
    private static double figureHalf(int h) {
        if (h <= 46) return 19 + (46 - h) * .22;
        if (h <= 72) return 19 + (h - 46) * .46;
        return h <= 86 ? 31 * Math.sqrt(Math.max(0, 1 - Math.pow((h - 72) / 14D, 2))) : 0;
    }

    /**
     * How far the figure stands out of the face at across {@code a}, height {@code h}. It's a depth map, built like a
     * sculptor's clay: a rounded robe, with folds drawn down it, sleeves laid over, hands and an urn on those, a hood above.
     */
    private static double relief(int a, int h) {
        double half = figureHalf(h), robe = 0;
        if (half > 0 && Math.abs(a) < half) robe = Math.sqrt(1 - Math.pow(a / half, 2)) * 8 + Math.sin(a * .8 + h * .03) * (h < 46 ? 1.5 : .6);
        double sleeves = Math.max(blob(a, h, -17, 58, 12, 21), blob(a, h, 17, 58, 12, 21));
        double hands = Math.max(blob(a, h, -8, 50, 7, 5), blob(a, h, 8, 50, 7, 5));
        double urn = blob(a, h, 0, 52, 7.5, 8);
        double hood = blob(a, h, 0, 91, 13, 15);
        double out = Math.max(robe, robe * .6 + sleeves * 7);
        out = Math.max(out, out * .5 + hands * 9);
        out = Math.max(out, urn * 13);
        out = Math.max(out, hood * 11);
        return out;
    }

    /** True in the dark of the hood, where the face would be. */
    private static boolean hollow(int a, int h) { return blob(a, h, 0, 89, 6.5, 9) > 0; }

    private static boolean urnAt(int a, int h) { return blob(a, h, 0, 52, 7.5, 8) * 13 > 6; }

    private static void figure(BuildCanvas b) {
        int foot = DEEP;
        for (int a = -44; a <= 44; a++) for (int h = 0; h <= 108; h++) {
            double depth = relief(a, h);
            if (depth < .5) continue;
            int front = faceZ(a) - Math.max(0, (CREST - (foot + h)) / 7), deep = (int) Math.ceil(depth);
            // Light comes from high up on the left: faces turned to it are the palest green, faces turned away the darkest.
            double slope = (relief(a - 1, h) - relief(a + 1, h)) + (relief(a, h + 1) - relief(a, h - 1));
            Material skin = slope > 1.2 ? Material.WAXED_WEATHERED_COPPER : slope > .3 ? Material.WAXED_WEATHERED_CUT_COPPER : slope > -.6 ? COPPER : Material.WAXED_OXIDIZED_CUT_COPPER;
            for (int d = 1; d <= deep; d++) b.block(a, foot + h, front - d, d < deep ? COPPER_DARK : urnAt(a, h) ? (Math.floorMod(h, 4) == 0 ? Material.GOLD_BLOCK : Material.WAXED_EXPOSED_COPPER) : skin);
            if (hollow(a, h)) for (int d = deep - 3; d <= deep; d++) b.block(a, foot + h, front - d, d == deep - 3 ? Material.BLACK_CONCRETE : Material.AIR);
        }
        int eye = faceZ(0) - Math.max(0, (CREST - (foot + 90)) / 7) - 8;
        for (int side = -1; side <= 1; side += 2) for (int a = 2; a <= 3; a++) b.block(side * a, foot + 90, eye, Material.SEA_LANTERN);
        // A diadem of gold rays standing out of the hood.
        for (int ray = -3; ray <= 3; ray++) {
            double angle = ray * .3;
            for (int r = 15; r <= 22 - Math.abs(ray); r++) {
                int a = (int) Math.round(Math.sin(angle) * r), h = 91 + (int) Math.round(Math.cos(angle) * r);
                b.block(a, foot + h, faceZ(a) - Math.max(0, (CREST - (foot + h)) / 7) - 2, Material.GOLD_BLOCK);
            }
        }
    }

    /** The urn pours down the front of her robe into the moat, and the two side sluices run down the dam either side. */
    private static void spouts(BuildCanvas b) {
        for (int a = -3; a <= 3; a++) for (int h = 44; h >= -14; h--) {
            int z = faceZ(a) - Math.max(0, (CREST - (DEEP + h)) / 7) - (int) Math.ceil(relief(a, Math.max(h, 0))) - 2;
            b.block(a, DEEP + h, z, Material.WATER, h == 44 ? "level=0" : "level=8");
        }
        for (int side = -1; side <= 1; side += 2) fall(b, side * 70, CREST - 30, 7);
    }

    private static void fall(BuildCanvas b, int x, int top, int half) {
        for (int a = -half; a <= half; a++) for (int y = top; y >= LAKE; y--) {
            int z = faceZ(x + a) - Math.max(0, (CREST - y) / 7) - 4;
            b.block(x + a, y, z, Material.WATER, y == top ? "level=0" : "level=8");
            if (y == top) b.block(x + a, y - 1, z + 1, DRESSED);
        }
    }

    /** The walk along the top, on a run of arches, with a gatehouse over each sluice. */
    private static void crest(BuildCanvas b) {
        for (int x = -DAM_HALF - 8; x <= DAM_HALF + 8; x++) {
            int z = faceZ(x);
            for (int y = CREST + 1; y <= CREST + 9; y++) for (int d = 0; d <= 2; d++) {
                int bay = Math.floorMod(x + 4, 9) - 4;
                boolean open = d == 0 && y <= CREST + 6 && Math.hypot(bay, Math.max(0, y - (CREST + 4))) < 3.4;
                b.block(x, y, z + d, open ? Material.AIR : y == CREST + 9 ? DRESSED : WORN);
            }
            if (Math.floorMod(x, 3) == 0) b.block(x, CREST + 10, z, Material.STONE_BRICK_SLAB, "type=bottom,waterlogged=false");
            for (int d = 3; d <= 34; d++) b.block(x, CREST - 1, z + d, Material.WATER, "level=0");
        }
        for (int x : new int[]{-76, 76, 0}) for (int dx = -8; dx <= 8; dx++) for (int dz = -2; dz <= 8; dz++) for (int y = CREST + 1; y <= CREST + 16; y++) {
            int z = faceZ(x) + dz;
            boolean window = y > CREST + 10 && y < CREST + 14 && Math.floorMod(dx, 4) == 2 && dz == -2;
            b.block(x + dx, y, z, y == CREST + 16 ? COPPER : window ? Material.CYAN_STAINED_GLASS : y % 6 == 0 ? DRESSED : WORN);
        }
    }

    /** Where it all comes from: a river out of the cloud, falling behind the dam. */
    private static void cloudfall(BuildCanvas b) {
        for (int x = -18; x <= 18; x++) for (int y = CREST - 1; y <= 212; y++) {
            int z = 352 + (int) (Math.abs(x) * .3);
            b.block(x, y, z, Material.WATER, y == 212 ? "level=0" : "level=8");
        }
        SkyKit.cloud(b, 0, 212, 352, 38, 7, 931);
        SkyKit.cloud(b, -38, 206, 358, 24, 5, 932);
        SkyKit.cloud(b, 36, 208, 354, 24, 5, 933);
    }

    /** A round stone apron at the foot of the dam, moated, with bollards to get behind when the wave comes. */
    private static void apron(BuildCanvas b) {
        for (int x = -BASIN_RADIUS - 8; x <= BASIN_RADIUS + 8; x++) for (int z = BASIN_Z - BASIN_RADIUS - 8; z <= BASIN_Z + BASIN_RADIUS + 8; z++) {
            double r = Math.hypot(x, z - BASIN_Z), angle = Math.atan2(z - BASIN_Z, x);
            if (r > BASIN_RADIUS + 5) continue;
            if (r > BASIN_RADIUS + 1.5) {
                for (int y = DEEP; y >= LAKE; y -= 1) if (y < LAKE + 2 || r > BASIN_RADIUS + 3.5) b.block(x, y, z, y == DEEP ? Material.STONE_BRICK_SLAB : stone(x, y, z), y == DEEP ? "type=bottom,waterlogged=false" : "");
                continue;
            }
            Material knot = WardensTemplate.knotFloor(r, angle, 19, 10);
            b.block(x, DEEP, z, knot == Material.GOLD_BLOCK ? Material.PRISMARINE_BRICKS : knot == WardensTemplate.TRIM ? Material.CHISELED_STONE_BRICKS : Math.floorMod((int) (r * .4) + (int) Math.floor(angle * r * .14), 2) == 0 ? WORN : DRESSED);
            for (int y = DEEP - 1; y >= LAKE - 1; y--) b.block(x, y, z, r < BASIN_RADIUS - 3 && y < DEEP - 3 ? BEDS : stone(x, y, z));
            if (Math.abs(r - BASIN_RADIUS) < 1.2 && z > BASIN_Z - BASIN_RADIUS + 3) {
                b.block(x, DEEP + 1, z, WORN);
                b.block(x, DEEP + 2, z, Math.floorMod(x + z, 5) == 0 ? DRESSED : Material.STONE_BRICK_SLAB, Math.floorMod(x + z, 5) == 0 ? "" : "type=bottom,waterlogged=false");
            }
        }
        // Cover from the wave, and sluice plates under the geysers.
        for (double angle = Math.PI / 8; angle < Math.TAU; angle += Math.PI / 4) {
            int cx = (int) Math.round(Math.cos(angle) * 16), cz = BASIN_Z + (int) Math.round(Math.sin(angle) * 16);
            b.box(cx - 2, DEEP + 1, cz - 2, cx + 2, DEEP + 1, cz + 2, Material.CHISELED_STONE_BRICKS);
            b.box(cx - 1, DEEP + 2, cz - 2, cx + 1, DEEP + 6, cz + 1, WORN);
            b.box(cx - 2, DEEP + 7, cz - 2, cx + 2, DEEP + 7, cz + 2, DRESSED);
            b.block(cx, DEEP + 8, cz, Material.SEA_LANTERN);
        }
        // The way in, from the control room's door.
        for (int z = AqueductWorks.CONTROL_SOUTH + 5; z <= BASIN_Z - BASIN_RADIUS + 3; z++) for (int x = -4; x <= 4; x++) {
            b.block(x, DEEP, z, Math.abs(x) == 4 ? WORN : Math.floorMod(z, 2) == 0 ? DRESSED : WORN);
            for (int y = DEEP + 1; y <= DEEP + 8; y++) if (b.get(x, y, z) != null) b.block(x, y, z, Material.AIR);
            for (int y = DEEP - 1; y >= LAKE - 1; y--) b.block(x, y, z, stone(x, y, z));
            if (Math.abs(x) == 4) {
                b.block(x, DEEP + 1, z, WORN);
                b.block(x, DEEP + 2, z, Material.STONE_BRICK_SLAB, "type=bottom,waterlogged=false");
            }
        }
        for (int x = -24; x <= 24; x += 8) for (int z = BASIN_Z - 24; z <= BASIN_Z + 24; z += 8) if (Math.hypot(x, z - BASIN_Z) <= 26) b.block(x, DEEP + 7, z, Material.LIGHT, "level=12,waterlogged=false");
    }

    /**
     * The Silt Bed. A stair goes down the west tower of the bastion to a flat of mud against the cliff, where the old west
     * branch of the aqueduct fell, and a mill wheel the size of a house lies half under.
     */
    static void silt(BuildCanvas b) {
        int tx = -16, tz = BASTION_Z;
        AqueductTemplate.tower(b, tx, tz, 6, 10);
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int y = SILT_Y + 1; y <= Y + 8; y++) if (Math.hypot(dx, dz) <= 4.6) b.block(tx + dx, y, tz + dz, Material.AIR);
        for (int y = Y; y >= SILT_Y; y--) {
            int step = Y - y;
            double angle = step * Math.PI / 7;
            for (double r = 1; r <= 4.4; r += .4) for (double spread = 0; spread < .5; spread += .1) {
                int sx = tx + (int) Math.round(Math.cos(angle + spread) * r), sz = tz + (int) Math.round(Math.sin(angle + spread) * r);
                b.block(sx, y, sz, DRESSED);
            }
            b.block(tx, y, tz, BEDS);
            for (int up = 1; up <= 3; up++) b.block(tx, y + up, tz, BEDS);
        }
        for (int z = tz - 2; z <= tz + 2; z++) for (int x = tx + 4; x <= tx + 7; x++) for (int y = Y + 1; y <= Y + 5; y++) b.block(x, y, z, Material.AIR);
        for (int x = tx - 7; x >= tx - 4 - 1; x--) for (int z = tz - 2; z <= tz + 2; z++) for (int y = SILT_Y + 1; y <= SILT_Y + 5; y++) b.block(x, y, z, Material.AIR);
        // The causeway across to the flat.
        for (int x = tx - 7; x >= SILT_X + SILT_RADIUS - 2; x--) for (int z = tz - 3; z <= tz + 3; z++) {
            b.block(x, SILT_Y, z, Math.abs(z - tz) == 3 ? WORN : Math.floorMod(x, 2) == 0 ? DRESSED : Material.MOSSY_STONE_BRICKS);
            for (int y = SILT_Y - 1; y >= LAKE - 1; y--) b.block(x, y, z, stone(x, y, z));
        }
        for (int x = SILT_X - SILT_RADIUS - 14; x <= SILT_X + SILT_RADIUS + 2; x++) for (int z = SILT_Z - SILT_RADIUS - 8; z <= SILT_Z + SILT_RADIUS + 8; z++) {
            double r = Math.hypot(x - SILT_X, z - SILT_Z) + (smooth(x, z, 5, 941) - .5) * 5, run = Math.abs(smooth(x, z, 11, 942) - .5);
            if (r > SILT_RADIUS + 3) continue;
            int y = r > SILT_RADIUS ? SILT_Y - 1 : SILT_Y;
            boolean channel = run < .014 && r > 9;
            b.block(x, y, z, channel ? Material.WATER : run < .05 ? Material.PACKED_MUD : Material.MUD, channel ? "level=0" : "");
            for (int under = y - 1; under >= LAKE - 1; under--) b.block(x, under, z, Material.MUD);
            if (!channel && r < SILT_RADIUS && noise(x, z, 943) < .05) b.block(x, y + 1, z, Material.TALL_DRY_GRASS);
        }
        wheel(b);
        fallenArches(b);
        for (int[] tree : new int[][]{{SILT_X + 14, SILT_Z - 20}, {SILT_X + 18, SILT_Z + 18}, {SILT_X - 4, SILT_Z - 23}}) deadTree(b, tree[0], tree[1]);
        for (int x = SILT_X - 20; x <= SILT_X + 20; x += 10) for (int z = SILT_Z - 20; z <= SILT_Z + 20; z += 10) b.block(x, SILT_Y + 8, z, Material.LIGHT, "level=9,waterlogged=false");
    }

    private static void deadTree(BuildCanvas b, int x, int z) {
        for (int up = 1; up <= 9; up++) {
            int lean = up / 3;
            b.block(x + lean, SILT_Y + up, z, Material.STRIPPED_DARK_OAK_LOG);
            if (up > 4 && up % 2 == 0) for (int reach = 1; reach <= 3; reach++) b.block(x + lean + (up % 4 == 0 ? reach : -reach), SILT_Y + up + reach / 2, z + reach / 2, Material.DARK_OAK_FENCE);
        }
    }

    /** The mill wheel, the height of a house, tipped into the mud facing the causeway. */
    private static void wheel(BuildCanvas b) {
        int cx = SILT_X - 10, cy = SILT_Y + 11, cz = SILT_Z;
        for (int dz = -18; dz <= 18; dz++) for (int dy = -18; dy <= 18; dy++) for (int d = -3; d <= 3; d++) {
            double r = Math.hypot(dz, dy), angle = Math.atan2(dy, dz);
            boolean rim = r > 13.8 && r < 17.2 && Math.abs(d) >= 2, paddle = r > 14 && r < 18 && Math.abs(Math.sin(angle * 9)) < .12, spoke = r < 15 && Math.abs(Math.sin(angle * 4)) < .08 && Math.abs(d) == 2, hub = r < 3 && Math.abs(d) <= 2;
            if (cy + dy > SILT_Y && (rim || paddle || spoke || hub)) b.block(cx + d, cy + dy, cz + dz, hub ? BRONZE : paddle ? Material.DARK_OAK_PLANKS : noise(dz, dy, 945) < .16 ? Material.MOSSY_STONE_BRICKS : Material.STRIPPED_DARK_OAK_LOG);
        }
    }

    /** The west branch where it came down: its arches stand broken behind the wheel, higher at the north end. */
    private static void fallenArches(BuildCanvas b) {
        int x0 = SILT_X - 19;
        for (int z = SILT_Z - 30; z <= SILT_Z + 30; z++) for (int y = SILT_Y; y <= SILT_Y + 30; y++) for (int d = -3; d <= 3; d++) {
            int off = Math.floorMod(z, 16) - 8;
            double arch = Math.hypot(off, Math.max(0, y - (SILT_Y + 15)));
            int top = SILT_Y + 30 - (int) ((z - SILT_Z + 30) * .55) - (int) (smooth(z, d, 4, 944) * 7);
            if (y > top || arch < 6) continue;
            b.block(x0 + d, y, z, y == SILT_Y + 22 ? Material.CHISELED_STONE_BRICKS : Math.abs(arch - 6.8) < .8 ? TIDE : y < SILT_Y + 6 ? TIDE : smooth(z, y, 5, 946) > .46 ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS);
        }
    }
}
