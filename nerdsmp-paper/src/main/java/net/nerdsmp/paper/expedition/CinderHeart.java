package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.CinderKeepTemplate.*;
import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The Molten Heart: the inside of a volcano, seen from a disc of black stone in a sea of fire. Chains as thick as a tower run
 * from the crater wall out into the lava at the far side, where they go under. That's where he's bound. They're the Wardens'
 * work, and they're all that's holding him. Every sightline in the room runs along them to the same place.
 */
final class CinderHeart {
    static final int FLOOR = 31, LAVA = 27, CENTER_Z = 300, RADIUS = 31, CRATER = 84, ROOF = 112, RISE_Z = CENTER_Z + 42;
    /** The three Wardens' plates on the disc, for the Binding: ember, frost, stone. */
    static final int PLATE_RING = 19;

    private CinderHeart() { }

    static void build(BuildCanvas b) {
        crater(b);
        sea(b);
        disc(b);
        causeway(b);
        chains(b);
        ledges(b);
        for (int x = -4; x <= 4; x++) for (int z = CENTER_Z + RADIUS - 1; z <= CENTER_Z + RADIUS + 8; z++) for (int y = LAVA - 1; y <= FLOOR; y++) b.block(x, y, z, y == FLOOR ? BRICK : rock(x, y, z));
        b.block(5, FLOOR + 1, 256, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    private static double wallTop(double angle) { return ROOF + Math.sin(angle * 3) * 5; }

    /** The crater, a bowl of rock narrowing towards a roof that glows, faulted and hung with fire. */
    private static void crater(BuildCanvas b) {
        for (int x = -CRATER - 12; x <= CRATER + 12; x++) for (int z = CENTER_Z - CRATER - 12; z <= CENTER_Z + CRATER + 12; z++) {
            double r = Math.hypot(x, z - CENTER_Z), angle = Math.atan2(z - CENTER_Z, x);
            double wobble = (smooth(x, z, 19, 981) - .5) * 14 + Math.sin(angle * 5) * 4;
            double edge = CRATER + wobble;
            if (r < edge + 2) {
                // The roof, only as a shell, closing over the top of the bowl.
                double up = ROOF + (1 - Math.pow(r / edge, 2)) * 26;
                int y = (int) Math.round(up);
                for (int d = 0; d <= 2; d++) b.block(x, y + d, z, d == 0 && smooth(x, z, 11, 982) > .62 ? HOT : Material.BASALT);
                continue;
            }
            if (r > edge + 7) continue;
            int top = (int) wallTop(angle) + 6;
            for (int y = LAVA - 2; y <= top; y++) {
                double inner = edge - 8 + (top - y) * .12 + (smooth(y * 2, (int) (angle * 30), 7, 983) - .5) * 9;
                if (r < inner) continue;
                b.block(x, y, z, stratum(x, y, z));
            }
        }
    }

    private static Material stratum(int x, int y, int z) {
        double bed = smooth(x + z, y * 3, 17, 984), seam = Math.abs(smooth(x - z, y * 2, 13, 985) - .5);
        if (seam < .01) return HOT;
        if (y < LAVA + 6) return Material.BLACKSTONE;
        return bed > .62 ? Material.BASALT : bed > .4 ? Material.BLACKSTONE : Material.SMOOTH_BASALT;
    }

    private static void sea(BuildCanvas b) {
        for (int x = -CRATER - 2; x <= CRATER + 2; x++) for (int z = CENTER_Z - CRATER - 2; z <= CENTER_Z + CRATER + 2; z++) {
            if (Math.hypot(x, z - CENTER_Z) > CRATER + 4) continue;
            b.block(x, LAVA, z, Material.LAVA, "level=0");
            b.block(x, LAVA - 1, z, Material.BASALT);
        }
    }

    /** The disc: the Wardens' own floor, cut with their seal, and the only cold stone in the mountain. */
    private static void disc(BuildCanvas b) {
        for (int x = -RADIUS - 3; x <= RADIUS + 3; x++) for (int z = CENTER_Z - RADIUS - 3; z <= CENTER_Z + RADIUS + 3; z++) {
            double r = Math.hypot(x, z - CENTER_Z), angle = Math.atan2(z - CENTER_Z, x), edge = RADIUS + (smooth(x, z, 7, 986) - .5) * 3;
            if (r > edge) continue;
            Material knot = WardensTemplate.knotFloor(r, angle, 25, 14);
            b.block(x, FLOOR, z, r > RADIUS - 2 ? BRICK : knot == GOLD ? GOLD : knot == WardensTemplate.TRIM ? Material.CHISELED_POLISHED_BLACKSTONE : Math.floorMod((int) (r * .34) + (int) Math.floor(angle * r * .12), 2) == 0 ? POLISHED : BRICK);
            int under = 3 + (int) ((RADIUS - r) * .8);
            for (int y = FLOOR - 1; y >= Math.max(LAVA - 1, FLOOR - under); y--) b.block(x, y, z, rock(x, y, z));
        }
        WardensTemplate.medallion(b, 0, FLOOR, CENTER_Z, 8);
        plate(b, -PLATE_RING, CENTER_Z, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
        plate(b, PLATE_RING, CENTER_Z, Material.BLUE_ICE, Material.SEA_LANTERN);
        plate(b, 0, CENTER_Z - PLATE_RING, Material.MOSS_BLOCK, Material.OCHRE_FROGLIGHT);
        for (int x = -24; x <= 24; x += 8) for (int z = CENTER_Z - 24; z <= CENTER_Z + 24; z += 8) if (Math.hypot(x, z - CENTER_Z) <= 28) b.block(x, FLOOR + 7, z, Material.LIGHT, "level=13,waterlogged=false");
        // Stumps of the old ring of the Wardens' piers, each with its fire.
        for (double angle = Math.PI / 12; angle < Math.PI * 2; angle += Math.PI / 6) {
            int x = (int) Math.round(Math.cos(angle) * (RADIUS - 2)), z = CENTER_Z + (int) Math.round(Math.sin(angle) * (RADIUS - 2));
            if (z < CENTER_Z - 26 && Math.abs(x) < 7) continue;
            if (z > CENTER_Z + 20) continue;
            for (int h = 1; h <= 4; h++) b.box(x - 1, FLOOR + h, z - 1, x + 1, FLOOR + h, z + 1, h == 4 ? GILT : BRICK);
            b.block(x, FLOOR + 5, z, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        }
    }

    private static void plate(BuildCanvas b, int cx, int cz, Material ring, Material heart) {
        for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 4; z <= cz + 4; z++) {
            double r = Math.hypot(x - cx, z - cz);
            if (r <= 4.3) b.block(x, FLOOR, z, r < 1.3 ? heart : r > 3.4 ? GOLD : ring);
        }
    }

    /** The way out of the stair: a tunnel through the crater wall, then a narrow bridge of black stone across the fire. */
    private static void causeway(BuildCanvas b) {
        int start = CinderHalls.PIT_NORTH + 2 + (Y - FLOOR) * 2;
        for (int z = CinderHalls.THRONE_SOUTH + 8; z <= CENTER_Z - RADIUS; z++) {
            int y = z < start ? Y - 1 - (z - CinderHalls.PIT_NORTH - 2) / 2 : FLOOR;
            for (int x = -5; x <= 5; x++) for (int h = 1; h <= 7; h++) if (b.get(x, y + h, z) != null) b.block(x, y + h, z, Material.AIR);
        }
        for (int z = start; z <= CENTER_Z - RADIUS + 3; z++) for (int x = -4; x <= 4; x++) {
            b.block(x, FLOOR, z, Math.abs(x) == 4 ? GILT : Math.abs(x) <= 1 ? BRICK : Math.floorMod(x + z, 2) == 0 ? POLISHED : BRICK);
            for (int y = FLOOR - 1; y >= LAVA - 1; y--) b.block(x, y, z, rock(x, y, z));
        }
        // The bars that close behind you, in an arch at the near end of the disc.
        int door = CENTER_Z - RADIUS - 6;
        for (int side = -1; side <= 1; side += 2) for (int a = 4; a <= 6; a++) for (int h = 1; h <= 12; h++) b.block(side * a, FLOOR + h, door, a == 4 ? GILT : BRICK);
        for (int a = -6; a <= 6; a++) for (int h = 12; h <= 15; h++) b.block(a, FLOOR + h, door, h == 15 ? GILT : BRICK);
    }

    /** Four of the Wardens' chains, each link the size of a room, from the crater wall to where he lies. */
    private static void chains(BuildCanvas b) {
        double[][] anchors = {{-78, 104, 58}, {78, 104, 58}, {-64, 88, 96}, {64, 88, 96}};
        for (double[] anchor : anchors) chain(b, anchor[0], anchor[1], CENTER_Z + anchor[2], anchor[0] * .14, LAVA + 12, RISE_Z);
    }

    private static void chain(BuildCanvas b, double x1, double y1, double z1, double x2, double y2, double z2) {
        double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int links = (int) (length / 7);
        for (int link = 0; link <= links; link++) {
            double t = link / (double) links, sag = Math.sin(t * Math.PI) * 10;
            double x = x1 + (x2 - x1) * t, y = y1 + (y2 - y1) * t - sag, z = z1 + (z2 - z1) * t;
            boolean flat = link % 2 == 0;
            for (double u = 0; u < Math.TAU; u += .1) {
                double along = Math.cos(u) * 5, across = Math.sin(u) * 2.6;
                double ux = (x2 - x1) / length, uy = (y2 - y1) / length, uz = (z2 - z1) / length;
                for (double thick = -.8; thick <= .8; thick += .8) {
                    int px = (int) Math.round(x + ux * along + (flat ? across : thick)), py = (int) Math.round(y + uy * along + (flat ? thick : across)), pz = (int) Math.round(z + uz * along);
                    b.block(px, py, pz, link % 5 == 0 ? GILT : Material.POLISHED_BLACKSTONE);
                }
            }
        }
    }

    /** Ledges high in the crater wall, where the people who came with you will stand when the time comes. */
    private static void ledges(BuildCanvas b) {
        for (int[] ledge : new int[][]{{-58, 44, CENTER_Z - 44}, {58, 44, CENTER_Z - 44}, {0, 48, CENTER_Z - 72}, {-72, 40, CENTER_Z + 8}, {72, 40, CENTER_Z + 8}}) {
            for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 7.4) continue;
                b.block(ledge[0] + dx, ledge[1], ledge[2] + dz, r > 6 ? GILT : BRICK);
                for (int down = 1; down <= (int) (8 - r); down++) b.block(ledge[0] + dx, ledge[1] - down, ledge[2] + dz, rock(ledge[0] + dx, ledge[1] - down, ledge[2] + dz));
            }
            b.block(ledge[0], ledge[1] + 1, ledge[2], Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        }
    }
}
