package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The Hall of the Three Wardens, on one axis from north to south. A landing stands low in the clouds; the Long Stair climbs
 * from it past two landings to the High Gate, between colossi of the Wardens; behind the gate is the Hall of Seals under
 * its dome, and from that the three wings go out west, east and south. All the floors from the gate on are at {@link #Y}.
 * The last room is out of the way entirely, in {@link WardensVoid}.
 */
final class WardensTemplate {
    static final int Y = 63;
    static final int LANDING_Y = 41, LANDING_Z = -170;
    static final int PLAZA_Z = -16, PLAZA_RADIUS = 28;
    /** The hall's centre. Wings go out from it to their courts, and on to their arenas. */
    static final int HALL_Z = 46, COURT = 74, ARENA = 138, COURT_RADIUS = 22, ARENA_RADIUS = 28;
    static final Isle HALL_ROCK = new Isle(Material.SMOOTH_SANDSTONE, Material.SANDSTONE,
        new Material[]{Material.STONE, Material.ANDESITE, Material.TUFF, Material.STONE, Material.SMOOTH_SANDSTONE});
    static final Material PAVE = Material.SMOOTH_SANDSTONE, BLOCK = Material.CUT_SANDSTONE, TRIM = Material.CHISELED_SANDSTONE, COURSE = Material.SANDSTONE;

    private static final int[][] FLIGHTS = {{-144, -128, LANDING_Y}, {-100, -86, 49}, {-58, -44, 56}};
    private static final int STAIR_HALF = 5, PAD_RADIUS = 14;

    private WardensTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        sky(b);
        landing(b);
        longStair(b);
        plaza(b);
        WardensHall.build(b);
        WardensWings.build(b);
        WardensVoid.build(b);
        SampleChests.place(b, "hall_of_wardens");
        return b.freeze();
    }

    private static void sky(BuildCanvas b) {
        int[][] banks = {{-70, -150, 30}, {60, -120, 34}, {-120, -60, 26}, {120, -40, 38}, {-180, 90, 30}, {190, 60, 28}, {-60, 150, 32}, {80, 190, 36},
            {0, -190, 26}, {-30, 40, 16}, {50, 120, 22}, {-110, 140, 20}, {130, 130, 24}, {-200, -20, 44}, {210, -60, 48}};
        for (int index = 0; index < banks.length; index++) SkyKit.cloud(b, banks[index][0], banks[index][2], banks[index][1], 26 + index % 3 * 7, 4 + index % 3, 600 + index);
    }

    /** Where the party comes in: a low island at the foot of the stair, with Vell's camp. */
    private static void landing(BuildCanvas b) {
        island(b, 0, LANDING_Y, LANDING_Z, 24, 22, HALL_ROCK);
        SkyRuins.hang(b, 0, LANDING_Y, LANDING_Z, 24, 22);
        for (int x = -24; x <= 24; x++) for (int z = LANDING_Z - 24; z <= LANDING_Z + 24; z++) {
            double r = Math.hypot(x, z - LANDING_Z);
            if (r > 21 || b.get(x, LANDING_Y, z) == null) continue;
            double wear = smooth(x, z, 6, 611);
            b.block(x, LANDING_Y, z, wear > .62 ? Material.GRASS_BLOCK : wear > .5 ? Material.MOSS_BLOCK : (x + z) % 2 == 0 ? PAVE : COURSE);
        }
        // The paved way from the portal to the foot of the stair.
        for (int z = LANDING_Z - 12; z <= FLIGHTS[0][0]; z++) for (int x = -STAIR_HALF; x <= STAIR_HALF; x++)
            b.block(x, LANDING_Y, z, Math.abs(x) == STAIR_HALF ? BLOCK : x == 0 ? Material.GOLD_BLOCK : PAVE);
        for (int side = -1; side <= 1; side += 2) {
            obelisk(b, side * 7, LANDING_Y + 1, FLIGHTS[0][0] - 3);
            SkyKit.bannerPost(b, side * 15, LANDING_Y + 1, LANDING_Z + 6, true, Material.RED_WOOL);
        }
        SkyKit.tent(b, -12, LANDING_Y + 1, LANDING_Z - 8, Material.RED_WOOL);
        SkyKit.tent(b, 12, LANDING_Y + 1, LANDING_Z - 8, Material.YELLOW_WOOL);
        SkyKit.crates(b, 17, LANDING_Y + 1, LANDING_Z + 1);
        SkyKit.crates(b, -18, LANDING_Y + 1, LANDING_Z - 2);
        b.block(-5, LANDING_Y + 1, LANDING_Z - 4, Material.LECTERN, "facing=east,has_book=false");
        b.block(7, LANDING_Y + 1, LANDING_Z - 6, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        SkyKit.waterfall(b, 23, LANDING_Y, LANDING_Z, 40);
        SkyKit.waterfall(b, -23, LANDING_Y, LANDING_Z + 4, 44);
    }

    /** A tapering stone needle with a gold cap. */
    static void obelisk(BuildCanvas b, int x, int y, int z) {
        b.box(x - 1, y, z - 1, x + 1, y, z + 1, TRIM);
        b.box(x - 1, y + 1, z - 1, x + 1, y + 5, z + 1, BLOCK);
        for (int h = 6; h <= 9; h++) b.block(x, y + h, z, BLOCK);
        b.block(x, y + 10, z, Material.GOLD_BLOCK);
        b.block(x, y + 11, z, Material.LIGHTNING_ROD, "facing=up");
    }

    private static void longStair(BuildCanvas b) {
        for (int[] flight : FLIGHTS) flight(b, flight[0], flight[1], flight[2]);
        landingPad(b, -114, 49);
        landingPad(b, -72, 56);
    }

    /** One run of steps, two blocks of going to each block of rise, over a wall of arches. */
    private static void flight(BuildCanvas b, int from, int to, int y) {
        for (int z = from; z < to; z++) {
            int rise = (z - from) / 2, level = y + rise;
            boolean nose = (z - from) % 2 == 0;
            for (int x = -STAIR_HALF; x <= STAIR_HALF; x++) {
                if (nose && Math.abs(x) < STAIR_HALF) b.block(x, level + 1, z, Math.abs(x) == 1 ? Material.SMOOTH_SANDSTONE_STAIRS : Material.SANDSTONE_STAIRS, "facing=south,half=bottom,shape=straight");
                b.block(x, level, z, Math.abs(x) == STAIR_HALF ? BLOCK : PAVE);
                if (!nose) b.block(x, level + 1, z, Math.abs(x) == STAIR_HALF ? BLOCK : PAVE);
            }
            for (int side = -1; side <= 1; side += 2) {
                b.block(side * STAIR_HALF, level + 1, z, BLOCK);
                b.block(side * STAIR_HALF, level + 2, z, BLOCK);
                b.block(side * STAIR_HALF, level + 3, z, Material.SMOOTH_SANDSTONE_SLAB, "type=bottom");
                arches(b, side * STAIR_HALF, level - 1, z, 34 - (y - LANDING_Y) / 2);
            }
            for (int x = -STAIR_HALF + 1; x < STAIR_HALF; x++) b.box(x, level - 3, z, x, level - 1, z, COURSE);
        }
    }

    /** Wall under a flight: a run of round arches cut through it, with the piers going on down to a point. */
    private static void arches(BuildCanvas b, int x, int top, int z, int depth) {
        int bay = Math.floorMod(z, 14) - 7;
        for (int down = 0; down < depth; down++) {
            boolean open = Math.hypot(bay, down - 6) < 5.2 || down >= 6 && Math.abs(bay) < 5;
            int end = depth - Math.abs(bay) * 2;
            if (!open && down < end) b.block(x, top - down, z, down % 7 == 3 ? TRIM : BLOCK);
        }
    }

    /** A round rest on the way up, with a wall round it and a gap at each end for the flights. */
    private static void landingPad(BuildCanvas b, int mid, int y) {
        island(b, 0, y, mid, PAD_RADIUS + 1, 18, HALL_ROCK);
        SkyRuins.hang(b, 0, y, mid, PAD_RADIUS + 1, 18);
        for (int x = -PAD_RADIUS - 1; x <= PAD_RADIUS + 1; x++) for (int z = mid - PAD_RADIUS - 1; z <= mid + PAD_RADIUS + 1; z++) {
            double r = Math.hypot(x, z - mid);
            if (r > PAD_RADIUS + .6) continue;
            b.block(x, y, z, Math.abs(r - 6) < .7 ? Material.GOLD_BLOCK : Math.abs(r - 11) < .7 ? TRIM : Math.abs(x) < 2 ? BLOCK : (x + z) % 2 == 0 ? PAVE : COURSE);
            if (Math.abs(r - PAD_RADIUS) > .75 || Math.abs(x) <= STAIR_HALF) continue;
            b.block(x, y + 1, z, BLOCK);
            b.block(x, y + 2, z, Math.floorMod(x + z, 4) == 0 ? BLOCK : COURSE);
        }
        medallion(b, 0, y, mid, 3);
        for (int side = -1; side <= 1; side += 2) for (int end = -1; end <= 1; end += 2) obelisk(b, side * 9, y + 1, mid + end * 9);
    }

    /** The great round court before the gate. */
    private static void plaza(BuildCanvas b) {
        island(b, 0, Y, PLAZA_Z, PLAZA_RADIUS + 2, 26, HALL_ROCK);
        SkyRuins.hang(b, 0, Y, PLAZA_Z, PLAZA_RADIUS + 2, 26);
        for (int x = -PLAZA_RADIUS - 2; x <= PLAZA_RADIUS + 2; x++) for (int z = PLAZA_Z - PLAZA_RADIUS - 2; z <= PLAZA_Z + PLAZA_RADIUS + 2; z++) {
            double dz = z - PLAZA_Z, r = Math.hypot(x, dz);
            if (r > PLAZA_RADIUS + 1) continue;
            b.block(x, Y, z, knotFloor(r, Math.atan2(dz, x), 20, 12));
        }
        medallion(b, 0, Y, PLAZA_Z, 6);
        rim(b, 0, PLAZA_Z, PLAZA_RADIUS, Y, STAIR_HALF, true);
        // The wind crosses the plaza from the east and west, and these are the only cover from it.
        for (int[] stone : new int[][]{{-15, -29}, {15, -29}, {-20, -12}, {20, -12}, {-9, -2}, {9, -2}, {-7, -19}, {7, -19}}) standingStone(b, stone[0], stone[1]);
        SkyKit.waterfall(b, -PLAZA_RADIUS - 2, Y, PLAZA_Z - 8, 58);
        SkyKit.waterfall(b, PLAZA_RADIUS + 2, Y, PLAZA_Z - 8, 54);
    }

    /** A squat block of the hall's stone with a gold cap, broad enough for two to shelter behind. */
    private static void standingStone(BuildCanvas b, int x, int z) {
        b.box(x - 2, Y + 1, z - 2, x + 2, Y + 1, z + 2, TRIM);
        b.box(x - 1, Y + 2, z - 2, x + 1, Y + 5, z + 2, BLOCK);
        b.box(x - 1, Y + 6, z - 1, x + 1, Y + 6, z + 1, Material.GOLD_BLOCK);
    }

    /** A breast-high wall round a round court, broken at its north end for the stair if {@code north}. */
    static void rim(BuildCanvas b, int cx, int cz, int radius, int y, int gap, boolean north) {
        for (int x = cx - radius - 2; x <= cx + radius + 2; x++) for (int z = cz - radius - 2; z <= cz + radius + 2; z++) {
            double r = Math.hypot(x - cx, z - cz);
            if (Math.abs(r - radius) > .75) continue;
            if (north && z < cz && Math.abs(x - cx) <= gap) continue;
            b.block(x, y + 1, z, BLOCK);
            b.block(x, y + 2, z, Math.floorMod(x + z * 3, 5) == 0 ? BLOCK : COURSE);
        }
        int posts = radius * 2 / 5;
        for (int index = 0; index < posts; index++) {
            double angle = index * Math.TAU / posts + .13;
            int x = cx + (int) Math.round(Math.cos(angle) * radius), z = cz + (int) Math.round(Math.sin(angle) * radius);
            if (north && z < cz && Math.abs(x - cx) <= gap + 1) continue;
            b.box(x, y + 1, z, x, y + 3, z, TRIM);
            b.block(x, y + 4, z, Material.GOLD_BLOCK);
            b.block(x, y + 5, z, Material.LANTERN, "hanging=false");
        }
    }

    /**
     * A floor of plain stone with a braid of gold running round it. Two waves cross and cross again; at each crossing one
     * strand breaks off to pass under the other, which is all it takes for a line to read as knotwork.
     */
    static Material knotFloor(double r, double angle, double middle, int lobes) {
        double swing = 2.6, wave = Math.sin(lobes * angle), one = middle + swing * wave, two = middle - swing * wave;
        int crossing = (int) Math.round(lobes * angle / Math.PI);
        boolean near = Math.abs(wave) < .32, under = Math.floorMod(crossing, 2) == 0;
        if (Math.abs(r - one) < .8 && !(near && under)) return Material.GOLD_BLOCK;
        if (Math.abs(r - two) < .8 && !(near && !under)) return Material.GOLD_BLOCK;
        if (Math.abs(r - (middle - swing - 2.4)) < .6 || Math.abs(r - (middle + swing + 2.4)) < .6) return TRIM;
        if (r > middle + swing + 2.6 || r < middle - swing - 2.6) return Math.floorMod((int) (r * .5) + (int) (angle * r * .3), 2) == 0 ? PAVE : COURSE;
        return BLOCK;
    }

    /** A round gold seal let into a floor: a ring, three spokes, and a boss in the middle. */
    static void medallion(BuildCanvas b, int cx, int y, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            double dx = x - cx, dz = z - cz, r = Math.hypot(dx, dz), angle = Math.atan2(dz, dx);
            if (r > radius + .4) continue;
            boolean ring = r > radius - 1.2, boss = r < 1.3;
            boolean spoke = Math.abs(Math.sin(angle * 1.5 + Math.PI / 4)) < .16;
            b.block(x, y, z, ring || boss || spoke ? Material.GOLD_BLOCK : Material.RAW_GOLD_BLOCK);
        }
    }
}
