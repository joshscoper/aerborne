package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * Cinder Keep, the season's last raid. It's staged for the Nether's fog, which closes in at about ninety blocks: nothing
 * here is seen all at once, things loom. The party lands under the Ember Gate, crosses the gulf on the Chain Bridge, takes
 * the outer ward and the hound pens, goes up the nave of the Ash Cathedral, through the Great Bellows to the Steward's throne,
 * and then down through the floor to the Molten Heart. All of it is on one axis running south, with floors at {@link #Y}.
 */
final class CinderKeepTemplate {
    static final int Y = 63, GULF = 14;
    static final int LANDING_Z = -252, GATE_Z = -230, BRIDGE_END = -120, PLATFORM_Z = -170, BRIDGE_HALF = 7, PLATFORM_HALF = 17;
    static final Material BRICK = Material.POLISHED_BLACKSTONE_BRICKS, CRACKED = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, DARK = Material.BLACKSTONE, POLISHED = Material.POLISHED_BLACKSTONE,
        GILT = Material.GILDED_BLACKSTONE, RED = Material.NETHER_BRICKS, HOT = Material.MAGMA_BLOCK, GOLD = Material.GOLD_BLOCK, LAMP = Material.SHROOMLIGHT;

    private CinderKeepTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        gulf(b);
        landing(b);
        emberGate(b);
        bridge(b);
        CinderWard.build(b);
        CinderHalls.build(b);
        CinderHeart.build(b);
        SampleChests.place(b, "cinder_keep");
        return b.freeze();
    }

    /** Worn black masonry: blotched with cracks where the heat has been, picked out in the old red where it hasn't. */
    static Material masonry(int x, int y, int z) {
        double blob = smooth(x + y, z, 8, 951), fine = noise(x * 3 + y, z, 952);
        if (blob > .63) return fine < .4 ? CRACKED : Material.CHISELED_POLISHED_BLACKSTONE;
        if (blob < .37) return fine < .3 ? RED : DARK;
        return fine < .06 ? CRACKED : BRICK;
    }

    /** Raw rock that runs hot in veins. */
    static Material rock(int x, int y, int z) {
        double seam = Math.abs(smooth(x + y, z - y, 9, 953) - .5);
        if (seam < .012) return HOT;
        return smooth(x, y + z, 6, 954) > .5 ? Material.BASALT : Material.BLACKSTONE;
    }

    /** A lake of fire under everything, with needles of basalt standing out of it in the haze. */
    private static void gulf(BuildCanvas b) {
        for (int x = -96; x <= 96; x++) for (int z = -236; z <= -116; z += 1) {
            b.block(x, GULF, z, Material.LAVA, "level=0");
            b.block(x, GULF - 1, z, Material.BASALT);
        }
        int[][] needles = {{-44, -214, 46}, {52, -204, 62}, {-70, -182, 70}, {38, -160, 40}, {-34, -146, 54}, {64, -132, 58}, {-58, -126, 42}, {78, -188, 50}};
        for (int[] needle : needles) for (int y = GULF; y <= GULF + needle[2]; y++) {
            double t = (y - GULF) / (double) needle[2], wide = 8 * Math.pow(1 - t, .7) + 1;
            int reach = (int) Math.ceil(wide);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                if (Math.hypot(dx, dz) + noise(dx + y, dz, 955) * 2 - 1 <= wide) b.block(needle[0] + dx, y, needle[1] + dz, rock(needle[0] + dx, y, needle[1] + dz));
        }
    }

    /** The Last Landing: a shelf of rock in the wall of the gulf, with the camp of everyone who came this far. */
    private static void landing(BuildCanvas b) {
        for (int x = -34; x <= 34; x++) for (int z = LANDING_Z - 22; z <= GATE_Z; z++) {
            double r = Math.hypot(x / 34D, (z - LANDING_Z) / 22D);
            if (r > 1 && z < GATE_Z - 8) continue;
            b.block(x, Y, z, Math.abs(x) <= 2 ? BRICK : smooth(x, z, 7, 956) > .55 ? Material.BLACKSTONE : Material.BASALT);
            for (int y = Y - 1; y >= Y - 5 - (int) ((1 - Math.min(1, r)) * 30); y--) b.block(x, y, z, rock(x, y, z));
        }
        for (int side = -1; side <= 1; side += 2) {
            brazier(b, side * 8, LANDING_Z + 6);
            brazier(b, side * 8, GATE_Z - 8);
            SkyKit.bannerPost(b, side * 16, Y + 1, LANDING_Z - 2, true, Material.RED_WOOL);
        }
        SkyKit.tent(b, -15, Y + 1, LANDING_Z - 8, Material.RED_WOOL);
        SkyKit.tent(b, 15, Y + 1, LANDING_Z - 8, Material.YELLOW_WOOL);
        SkyKit.crates(b, 20, Y + 1, LANDING_Z + 2);
        SkyKit.crates(b, -22, Y + 1, LANDING_Z + 4);
        b.block(4, Y + 1, LANDING_Z - 6, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(4, Y + 1, -148, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    /** A stone bowl of fire on a short stem. */
    static void brazier(BuildCanvas b, int x, int z) {
        b.box(x - 1, Y + 1, z - 1, x + 1, Y + 1, z + 1, BRICK);
        b.block(x, Y + 2, z, POLISHED);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.hypot(dx, dz) <= 2.4) b.block(x + dx, Y + 3, z + dz, Math.hypot(dx, dz) > 1.4 ? GOLD : HOT);
        b.block(x, Y + 4, z, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
    }

    /**
     * The Ember Gate. Forty blocks of black wall across the head of the gulf, the seals' three sockets over the arch, and a
     * crowned head the width of the doors looking down at whoever brought them.
     */
    private static void emberGate(BuildCanvas b) {
        int top = 52, half = 70;
        for (int x = -half; x <= half; x++) for (int h = -50; h <= top + 6 - Math.abs(x) / 8; h++) for (int d = 0; d <= 5; d++) {
            int off = Math.floorMod(x + 6, 12) - 6;
            boolean pier = Math.abs(off) <= 1;
            if (d == 0 && !pier && h > 2 && h < top - 4) continue;
            b.block(x, Y + h, GATE_Z + d, pier && d == 0 ? (Math.floorMod(h, 8) == 0 ? GILT : BRICK) : masonry(x, Y + h, GATE_Z + d));
        }
        for (int x = -half; x <= half; x += 2) for (int d = -1; d <= 0; d++) b.block(x, Y + top - 3, GATE_Z + d, GILT);
        // The arch, pointed, cut right through, with gilt round it.
        for (int a = -11; a <= 11; a++) for (int h = 1; h <= 27; h++) {
            double point = Math.abs(a) * 1.35 + Math.max(0, h - 14);
            if (point > 15) continue;
            for (int d = 0; d <= 5; d++) b.block(a, Y + h, GATE_Z + d, point > 13.2 ? (d == 0 ? GILT : BRICK) : Material.AIR);
        }
        // The doors themselves, which the seals open.
        for (int a = -11; a <= 11; a++) for (int h = 1; h <= 27; h++) {
            if (Math.abs(a) * 1.35 + Math.max(0, h - 14) > 13.2) continue;
            b.block(a, Y + h, GATE_Z + 2, Math.abs(a) <= 1 ? GILT : Math.floorMod(h, 5) == 0 || Math.floorMod(a, 4) == 0 ? POLISHED : DARK);
        }
        // Three sockets.
        socket(b, -16, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
        socket(b, 0, Material.BLUE_ICE, Material.SEA_LANTERN);
        socket(b, 16, Material.MOSS_BLOCK, Material.OCHRE_FROGLIGHT);
    }

    private static void socket(BuildCanvas b, int cx, Material ring, Material heart) {
        for (int a = -5; a <= 5; a++) for (int h = -5; h <= 5; h++) {
            double r = Math.hypot(a, h);
            if (r > 5.4) continue;
            b.block(cx + a, Y + 36 + h, GATE_Z - 1, r > 4.2 ? GOLD : r > 1.8 ? ring : heart);
        }
    }

    /** The bridge. Its deck hangs from cables between four pylons, and the middle of it widens into a floor to fight on. */
    private static void bridge(BuildCanvas b) {
        for (int z = GATE_Z + 6; z <= BRIDGE_END; z++) {
            double fromMiddle = Math.abs(z - PLATFORM_Z);
            int half = fromMiddle <= 22 ? PLATFORM_HALF : BRIDGE_HALF;
            for (int x = -half; x <= half; x++) {
                int a = Math.abs(x);
                b.block(x, Y, z, a == half ? BRICK : a <= 1 ? GILT : Math.floorMod(x * 3 + z, 11) == 0 ? CRACKED : Math.floorMod(x + z, 2) == 0 ? POLISHED : BRICK);
                b.block(x, Y - 1, z, a == half ? RED : DARK);
                if (Math.floorMod(z, 12) == 0 || a == half) b.block(x, Y - 2, z, RED);
                if (a == half) {
                    b.block(x, Y + 1, z, BRICK);
                    b.block(x, Y + 2, z, Math.floorMod(z, 6) == 0 ? GILT : Material.POLISHED_BLACKSTONE_BRICK_SLAB, Math.floorMod(z, 6) == 0 ? "" : "type=bottom,waterlogged=false");
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            pylon(b, side * (PLATFORM_HALF + 3), PLATFORM_Z - 26, side);
            pylon(b, side * (PLATFORM_HALF + 3), PLATFORM_Z + 26, side);
            cable(b, side * (BRIDGE_HALF + 1), GATE_Z + 4, Y + 44, side * (PLATFORM_HALF + 3), PLATFORM_Z - 26, Y + 46);
            cable(b, side * (PLATFORM_HALF + 3), PLATFORM_Z - 26, Y + 46, side * (PLATFORM_HALF + 3), PLATFORM_Z + 26, Y + 46);
            cable(b, side * (PLATFORM_HALF + 3), PLATFORM_Z + 26, Y + 46, side * (BRIDGE_HALF + 1), BRIDGE_END + 2, Y + 44);
            for (int offset : new int[]{-22, 22}) ballista(b, side * (PLATFORM_HALF - 3), PLATFORM_Z + offset, side);
        }
    }

    private static void pylon(BuildCanvas b, int cx, int cz, int side) {
        for (int y = GULF; y <= Y + 50; y++) {
            int half = y < Y ? 4 + (Y - y) / 12 : 3;
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
                boolean corner = Math.abs(dx) == half && Math.abs(dz) == half;
                if (corner && y > Y) continue;
                b.block(cx + dx, y, cz + dz, Math.floorMod(y, 12) == 0 ? GILT : masonry(cx + dx, y, cz + dz));
            }
        }
        for (int h = 50; h <= 58; h++) {
            int half = 3 - (h - 50) / 3;
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) b.block(cx + dx, Y + h, cz + dz, h % 3 == 0 ? GILT : RED);
        }
        b.block(cx, Y + 59, cz, HOT);
        b.block(cx, Y + 60, cz, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
    }

    /** A hanging cable of black links, sagging between two high points, with hangers down to the deck's edge. */
    private static void cable(BuildCanvas b, int x1, int z1, int y1, int x2, int z2, int y2) {
        int steps = Math.abs(z2 - z1);
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t), z = z1 + (z2 > z1 ? step : -step);
            int y = (int) Math.round(y1 + (y2 - y1) * t - Math.sin(t * Math.PI) * 26);
            for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) b.block(x + dx, y + dy, z, Math.floorMod(z, 3) == 0 ? GILT : POLISHED);
            if (Math.floorMod(z, 6) == 0 && y > Y + 4) for (int down = Y + 3; down < y; down++) b.block(x, down, z, Material.IRON_CHAIN, "axis=y");
        }
    }

    /** A ballista on the deck: a stock on a post, with the bow across it. Its dispenser is the part you use. */
    private static void ballista(BuildCanvas b, int x, int z, int side) {
        b.box(x - 1, Y + 1, z - 1, x + 1, Y + 1, z + 1, BRICK);
        b.block(x, Y + 2, z, Material.DISPENSER, "facing=up,triggered=false");
        for (int a = -3; a <= 3; a++) b.block(x + a, Y + 3, z, Material.DARK_OAK_PLANKS);
        for (int d = -2; d <= 2; d++) b.block(x, Y + 3, z + d, Material.DARK_OAK_PLANKS);
        b.block(x, Y + 4, z, Material.IRON_BLOCK);
        for (int a : new int[]{-3, 3}) b.block(x + a, Y + 4, z, Material.IRON_BARS);
    }
}
