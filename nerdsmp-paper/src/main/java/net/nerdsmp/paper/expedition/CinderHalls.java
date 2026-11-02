package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.CinderKeepTemplate.*;
import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The keep proper, one vast room after another. The Ash Cathedral has a pointed vault on black columns over a congregation
 * turned to stone, and a window of fire at its east end; the Great Bellows breathe for every forge in the deep; the Steward's
 * throne stands at the end of a hall of kneeling giants, and under its floor is the way down.
 */
final class CinderHalls {
    static final int NAVE_NORTH = 0, NAVE_SOUTH = 84, NAVE_HALF = 20, NAVE_TOP = 46;
    static final int BELLOWS_NORTH = 88, BELLOWS_SOUTH = 122, BELLOWS_HALF = 22;
    static final int THRONE_NORTH = 126, THRONE_SOUTH = 200, THRONE_HALF = 26, THRONE_TOP = 52, PIT_NORTH = 166, PIT_SOUTH = 176;
    private static final Material GLASS = Material.ORANGE_STAINED_GLASS, GLASS_B = Material.RED_STAINED_GLASS;

    private CinderHalls() { }

    static void build(BuildCanvas b) {
        cathedral(b);
        bellows(b);
        throneHall(b);
        descent(b);
    }

    private static void floor(BuildCanvas b, int half, int north, int south) {
        for (int x = -half - 3; x <= half + 3; x++) for (int z = north - 3; z <= south + 3; z++) {
            b.block(x, Y, z, Math.abs(x) <= 2 ? RED : Math.floorMod(x + z, 2) == 0 ? BRICK : POLISHED);
            b.block(x, Y - 1, z, DARK);
        }
    }

    /** How high a pointed vault stands, {@code off} across from its ridge. */
    private static int vault(int off, int half, int top, int spring) {
        double t = Math.abs(off) / (double) half;
        return spring + (int) Math.round((top - spring) * (1 - t * t) * (1 - t * .25));
    }

    /** Side walls and a pointed barrel vault over a run of floor, with its ribs and bays. */
    private static void nave(BuildCanvas b, int half, int north, int south, int top, int spring, int bay) {
        for (int z = north; z <= south; z++) {
            boolean rib = Math.floorMod(z - north, bay) <= 1;
            for (int x = -half - 2; x <= half + 2; x++) {
                int off = Math.abs(x), roof = vault(x, half + 2, top, spring);
                for (int d = 0; d <= 2; d++) b.block(x, Y + roof + d, z, rib && d == 0 ? GILT : d == 2 ? RED : masonry(x, Y + roof + d, z));
                if (off >= half) for (int h = 1; h <= roof; h++) b.block(x, Y + h, z, off == half && Math.floorMod(h, 7) == 0 ? BRICK : masonry(x, Y + h, z));
            }
        }
    }

    private static void endWall(BuildCanvas b, int half, int z, int top, int spring) {
        for (int x = -half - 2; x <= half + 2; x++) {
            int roof = vault(x, half + 2, top, spring);
            for (int h = 1; h <= roof; h++) for (int d = 0; d <= 2; d++) b.block(x, Y + h, z + d, masonry(x, Y + h, z + d));
        }
    }

    /** A black cluster column, gilt at the base and capital. */
    private static void column(BuildCanvas b, int cx, int cz, int high) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double r = Math.hypot(dx, dz);
            double lobes = 2.6 + Math.sin(Math.atan2(dz, dx) * 4) * .6;
            for (int h = 1; h <= high; h++) {
                double wide = h <= 2 || h >= high - 2 ? 3.3 : lobes;
                if (r <= wide) b.block(cx + dx, Y + h, cz + dz, h == 1 || h == high - 1 ? GILT : h == 2 || h == high ? BRICK : Math.floorMod(h, 8) == 0 ? RED : POLISHED);
            }
        }
    }

    /** A gate of bars in a doorway. */
    private static void bars(BuildCanvas b, int z) {
        for (int x = -3; x <= 3; x++) for (int h = 1; h <= 8; h++) b.block(x, Y + h, z, Material.IRON_BARS, "north=false,south=false,east=true,west=true");
    }

    /** A cut through a wall, three wide or more, with gilt round it. */
    private static void door(BuildCanvas b, int z, int half, int high, int deep) {
        for (int x = -half - 1; x <= half + 1; x++) for (int h = 1; h <= high + 1; h++) for (int d = 0; d < deep; d++) {
            boolean head = Math.abs(x) > half || h > high;
            b.block(x, Y + h, z + d, head ? (d == 0 ? GILT : BRICK) : Material.AIR);
        }
    }

    private static void cathedral(BuildCanvas b) {
        floor(b, NAVE_HALF, NAVE_NORTH, NAVE_SOUTH);
        nave(b, NAVE_HALF, NAVE_NORTH, NAVE_SOUTH, NAVE_TOP, 22, 12);
        endWall(b, NAVE_HALF, NAVE_NORTH - 3, NAVE_TOP, 22);
        endWall(b, NAVE_HALF, NAVE_SOUTH + 1, NAVE_TOP, 22);
        for (int z = NAVE_NORTH + 8; z <= NAVE_SOUTH - 8; z += 12) for (int side = -1; side <= 1; side += 2) column(b, side * 11, z, 24);
        // Arcade over the columns, carrying the wall the vault springs from.
        for (int z = NAVE_NORTH; z <= NAVE_SOUTH; z++) for (int side = -1; side <= 1; side += 2) for (int d = -1; d <= 1; d++) {
            int off = Math.floorMod(z - NAVE_NORTH - 8 + 6, 12) - 6;
            int under = 16 + (int) Math.round(Math.sqrt(Math.max(0, 36 - off * off)) * 1.2);
            for (int h = under; h <= 34; h++) b.block(side * (11 + d), Y + h, z, h == 34 ? GILT : masonry(side * 11, Y + h, z));
        }
        congregation(b);
        roseWindow(b);
        transepts(b);
        door(b, NAVE_NORTH - 3, 3, 8, 3);
        door(b, NAVE_SOUTH + 1, 3, 8, 3);
        bars(b, NAVE_NORTH - 2);
        bars(b, NAVE_SOUTH + 2);
        for (int x = -16; x <= 16; x += 8) for (int z = NAVE_NORTH + 6; z <= NAVE_SOUTH - 6; z += 8) b.block(x, Y + 12, z, Material.LIGHT, "level=12,waterlogged=false");
        for (int z = NAVE_NORTH + 14; z <= NAVE_SOUTH - 14; z += 24) chandelier(b, z);
        altar(b);
    }

    /** Rows of kneeling figures in ash-grey stone, facing the altar. They've been waiting a long time. */
    private static void congregation(BuildCanvas b) {
        for (int row = NAVE_NORTH + 14; row <= NAVE_NORTH + 54; row += 6) for (int side = -1; side <= 1; side += 2) for (int x = 5; x <= 8; x += 3) {
            if (Math.floorMod(x + row, 5) == 4) continue;
            kneeler(b, side * x, row);
        }
    }

    private static void kneeler(BuildCanvas b, int x, int z) {
        b.block(x, Y + 1, z, Material.GRAY_TERRACOTTA);
        b.block(x, Y + 1, z + 1, Material.GRAY_TERRACOTTA);
        b.block(x, Y + 2, z + 1, Material.LIGHT_GRAY_TERRACOTTA);
        b.block(x, Y + 3, z + 1, Material.GRAY_TERRACOTTA);
    }

    private static void chandelier(BuildCanvas b, int z) {
        int high = 26;
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double r = Math.hypot(dx, dz);
            if (Math.abs(r - 4.6) < .7) b.block(dx, Y + high, z + dz, GILT);
            if (Math.abs(r - 4.6) < .4 && Math.floorMod(dx * 3 + dz, 4) == 0) b.block(dx, Y + high + 1, z + dz, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        }
        for (int h = high + 1; h <= NAVE_TOP - 1; h++) b.block(0, Y + h, z, Material.IRON_CHAIN, "axis=y");
        b.block(0, Y + high, z, HOT);
    }

    /** The great window at the east end: tracery in black, glass the colour of a forge mouth, and light behind it. */
    private static void roseWindow(BuildCanvas b) {
        int cy = 30, radius = 15;
        for (int a = -radius - 1; a <= radius + 1; a++) for (int h = cy - radius - 1; h <= cy + radius + 1; h++) {
            double r = Math.hypot(a, h - cy), angle = Math.atan2(h - cy, a);
            if (r > radius + 1.2) continue;
            boolean rim = r > radius - .4, hub = r < 2.6, ring = Math.abs(r - 8) < .7, spoke = Math.abs(Math.sin(angle * 6)) < .1, petal = Math.abs(Math.sin(angle * 12)) < .08 && r > 8;
            int z = NAVE_SOUTH + 1;
            if (rim || ring || spoke || petal) {
                b.block(a, Y + h, z, rim || ring ? GILT : POLISHED);
                continue;
            }
            b.block(a, Y + h, z, hub ? LAMP : Math.floorMod((int) (angle * 3.8) + (int) (r / 5), 2) == 0 ? GLASS : GLASS_B);
            b.block(a, Y + h, z + 1, HOT);
        }
    }

    /** The altar, raised on three steps, with a burning crown over it. */
    private static void altar(BuildCanvas b) {
        for (int step = 0; step < 3; step++) {
            int half = 12 - step * 2, near = NAVE_SOUTH - 16 + step * 2;
            b.box(-half, Y + 1 + step, near, half, Y + 1 + step, NAVE_SOUTH - 4, step == 2 ? RED : BRICK);
        }
        b.box(-4, Y + 4, NAVE_SOUTH - 9, 4, Y + 5, NAVE_SOUTH - 6, GILT);
        b.box(-1, Y + 1, 81, 1, Y + 3, 81, BRICK);
        b.block(0, Y + 4, 81, Material.LEVER, "face=floor,facing=north,powered=false");
        for (int a = -5; a <= 5; a++) {
            int high = 12 + (int) (Math.abs(Math.sin(a * .95)) * 3);
            for (int h = 8; h <= high; h++) b.block(a, Y + h, NAVE_SOUTH - 6, h == high ? HOT : GOLD);
        }
    }

    /** A chapel off each side of the crossing, with a rune valve at the end of it, and a third behind the altar. */
    private static void transepts(BuildCanvas b) {
        for (int side = -1; side <= 1; side += 2) {
            for (int a = NAVE_HALF - 2; a <= NAVE_HALF + 14; a++) for (int z = 36; z <= 54; z++) {
                b.block(side * a, Y, z, Math.abs(z - 45) <= 1 ? RED : BRICK);
                boolean edge = a == NAVE_HALF + 14 || z == 36 || z == 54;
                int top = 14 + (int) (Math.sqrt(Math.max(0, 81 - (z - 45) * (z - 45))) * .9);
                for (int h = 1; h <= top + 1; h++) {
                    if (edge || h > top) b.block(side * a, Y + h, z, masonry(side * a, Y + h, z));
                    else b.block(side * a, Y + h, z, Material.AIR);
                }
            }
            valve(b, side * (NAVE_HALF + 12), 45);
        }
        b.block(9, Y + 1, 6, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    /** A pedestal with a lever on it, and the pipe it shuts. */
    private static void valve(BuildCanvas b, int x, int z) {
        b.box(x - 1, Y + 1, z - 1, x + 1, Y + 1, z + 1, GILT);
        b.block(x, Y + 2, z, BRICK);
        b.block(x, Y + 3, z, Material.LEVER, "face=floor,facing=north,powered=false");
        for (int h = 4; h <= 12; h++) b.block(x, Y + h, z + 3, HOT);
    }

    private static void bellows(BuildCanvas b) {
        floor(b, BELLOWS_HALF, BELLOWS_NORTH, BELLOWS_SOUTH);
        nave(b, BELLOWS_HALF, BELLOWS_NORTH, BELLOWS_SOUTH, 28, 14, 8);
        endWall(b, BELLOWS_HALF, BELLOWS_NORTH - 3, 28, 14);
        endWall(b, BELLOWS_HALF, BELLOWS_SOUTH + 1, 28, 14);
        door(b, BELLOWS_NORTH - 3, 3, 8, 3);
        door(b, BELLOWS_SOUTH + 1, 3, 8, 3);
        bars(b, BELLOWS_SOUTH + 2);
        b.block(-5, Y + 1, 118, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        for (int side = -1; side <= 1; side += 2) for (int z : new int[]{97, 113}) bellowsPair(b, side, z);
        for (int x = -16; x <= 16; x += 8) for (int z = BELLOWS_NORTH + 4; z <= BELLOWS_SOUTH - 4; z += 8) b.block(x, Y + 9, z, Material.LIGHT, "level=13,waterlogged=false");
    }

    /** A pair of leather lungs the size of a barn, in a timber frame, with a nozzle into the floor. */
    private static void bellowsPair(BuildCanvas b, int side, int z) {
        for (int a = 9; a <= 19; a++) {
            double t = (a - 9) / 10D;
            int high = 2 + (int) (t * 7), half = 1 + (int) (t * 5);
            for (int dz = -half; dz <= half; dz++) for (int h = 1; h <= high; h++) b.block(side * a, Y + h, z + dz, Math.floorMod(a, 3) == 0 ? Material.DARK_OAK_PLANKS : Material.BROWN_WOOL);
        }
        for (int dz = -7; dz <= 7; dz += 14) for (int h = 1; h <= 12; h++) b.block(side * 14, Y + h, z + dz, Material.DARK_OAK_LOG, "axis=y");
        for (int dz = -7; dz <= 7; dz++) b.block(side * 14, Y + 12, z + dz, Material.DARK_OAK_LOG, "axis=z");
        for (int h = 1; h <= 3; h++) b.block(side * 8, Y + h - 1, z, HOT);
    }

    private static void throneHall(BuildCanvas b) {
        floor(b, THRONE_HALF, THRONE_NORTH, THRONE_SOUTH);
        nave(b, THRONE_HALF, THRONE_NORTH, THRONE_SOUTH, THRONE_TOP, 26, 15);
        endWall(b, THRONE_HALF, THRONE_NORTH - 3, THRONE_TOP, 26);
        endWall(b, THRONE_HALF, THRONE_SOUTH + 1, THRONE_TOP, 26);
        door(b, THRONE_NORTH - 3, 3, 8, 3);
        for (int z = THRONE_NORTH + 12; z <= THRONE_SOUTH - 14; z += 15) for (int side = -1; side <= 1; side += 2) giant(b, side * 20, z, side);
        // A red runner, and a ring of gilt round the place the floor will go.
        for (int z = THRONE_NORTH; z <= THRONE_SOUTH; z++) for (int x = -3; x <= 3; x++) b.block(x, Y, z, Math.abs(x) == 3 ? GOLD : Material.RED_CONCRETE);
        for (int x = -10; x <= 10; x++) for (int z = PIT_NORTH - 2; z <= PIT_SOUTH + 2; z++) {
            boolean inside = Math.abs(x) <= 8 && z >= PIT_NORTH && z <= PIT_SOUTH;
            b.block(x, Y, z, inside ? Math.floorMod(x + z, 2) == 0 ? CRACKED : BRICK : GILT);
            if (inside) b.block(x, Y - 1, z, CRACKED);
        }
        throne(b);
        for (int x = -20; x <= 20; x += 10) for (int z = THRONE_NORTH + 6; z <= THRONE_SOUTH - 6; z += 10) b.block(x, Y + 14, z, Material.LIGHT, "level=12,waterlogged=false");
    }

    /** A stone king on one knee, head bowed over the sword planted before him. The Sovereign had them carved so every guest would kneel with them. */
    private static void giant(BuildCanvas b, int x, int z, int side) {
        Carving c = new Carving(b, x, Y, z, -side, 0);
        // The knee that's down, and the foot that's planted.
        limb(c, -3, 2, -5, -3, 2, 3, 1.7, 2.3, DARK);
        limb(c, -3, 3, 3, -2.5, 11, -2, 2.4, 3.2, DARK);
        limb(c, 3.5, 1.5, 5, 3.5, 9, 4, 1.9, 2.3, DARK);
        limb(c, 3.5, 9, 4, 2.5, 11, -2, 2.4, 3.2, DARK);
        limb(c, 3.5, 1.5, 4, 3.5, 1.5, 7, 1.4, 1.4, BRICK);
        limb(c, -3, 3, 3, -3, 3, 3, 2.6, 2.6, GILT);
        limb(c, 3.5, 9, 4, 3.5, 9, 4, 2.6, 2.6, GILT);
        // The body leans in over the sword.
        for (int a = -6; a <= 6; a++) for (int s = 0; s <= 11; s++) for (int f = -6; f <= 7; f++) {
            double reach = 3.6 + s * .16, lean = s * .28 - 1.5;
            if (Math.hypot(a / 1.25, f - lean) > reach) continue;
            c.set(a, 10 + s, f, s == 1 || s == 2 ? GILT : s > 8 && f > lean + 2 ? BRICK : POLISHED);
        }
        // Arms down to the hilt, in pauldrons wider than the chest.
        for (int a = -1; a <= 1; a += 2) {
            limb(c, a * 6.5, 20, 1.5, a * 7, 14, 4, 2, 1.7, DARK);
            limb(c, a * 7, 14, 4, a * 1.5, 13, 7, 1.7, 1.5, POLISHED);
            limb(c, a * 7, 15, 4, a * 7, 15, 4, 1.9, 1.9, GILT);
            limb(c, a * 6.5, 20.5, 1, a * 6.5, 20.5, 1, 3, 3, BRICK);
            limb(c, a * 7, 22.5, 1, a * 7, 22.5, 1, 1.5, 1.5, GILT);
        }
        // The sword: gold at the guard, and its point in the floor.
        limb(c, 0, 1, 8, 0, 11, 8, 1, 1.3, BRICK);
        limb(c, -3.5, 12, 8, 3.5, 12, 8, .7, .7, GOLD);
        limb(c, 0, 12, 8, 0, 15, 8, .6, .6, DARK);
        limb(c, 0, 13.5, 7.5, 0, 13.5, 7.5, 2, 2, POLISHED);
        // A bowed head under a gilt crown, the beard falling onto the chest. Only the eyes are still lit.
        limb(c, 0, 21, 2, 0, 23, 3.5, 2, 2.2, DARK);
        limb(c, 0, 24, 4.5, 0, 24, 4.5, 3, 3, POLISHED);
        limb(c, 0, 22.5, 6.5, 0, 17.5, 6.5, 2, .8, BRICK);
        for (int a = -3; a <= 3; a++) for (int f = 1; f <= 8; f++) if (Math.abs(Math.hypot(a, f - 4.5) - 3) < .8) {
            c.set(a, 26, f, GILT);
            if ((a + f) % 2 == 0) c.set(a, 27, f, GILT);
        }
        c.set(-1, 24, 7, HOT);
        c.set(1, 24, 7, HOT);
    }

    /** A rounded limb from one point to another, thickening or tapering along the way. */
    private static void limb(Carving c, double a1, double h1, double f1, double a2, double h2, double f2, double r1, double r2, Material block) {
        double reach = Math.max(r1, r2);
        double da = a2 - a1, dh = h2 - h1, df = f2 - f1, length = da * da + dh * dh + df * df;
        for (int a = (int) Math.floor(Math.min(a1, a2) - reach); a <= Math.ceil(Math.max(a1, a2) + reach); a++)
            for (int h = (int) Math.max(1, Math.floor(Math.min(h1, h2) - reach)); h <= Math.ceil(Math.max(h1, h2) + reach); h++)
                for (int f = (int) Math.floor(Math.min(f1, f2) - reach); f <= Math.ceil(Math.max(f1, f2) + reach); f++) {
                    double t = length == 0 ? 0 : Math.clamp(((a - a1) * da + (h - h1) * dh + (f - f1) * df) / length, 0, 1);
                    if (Math.hypot(Math.hypot(a - a1 - t * da, h - h1 - t * dh), f - f1 - t * df) <= r1 + (r2 - r1) * t) c.set(a, h, f, block);
                }
    }

    /** The Steward's chair is a dais of nine steps and a back of black spears, with an empty and much larger seat above his. */
    private static void throne(BuildCanvas b) {
        int back = THRONE_SOUTH - 2;
        for (int step = 0; step < 6; step++) {
            int half = 18 - step * 2, near = back - 18 + step * 2;
            b.box(-half, Y + 1 + step, near, half, Y + 1 + step, back, step == 5 ? RED : BRICK);
            for (int x = -half; x <= half; x++) b.block(x, Y + 1 + step, near, GILT);
        }
        b.box(-3, Y + 7, back - 6, 3, Y + 8, back - 4, GOLD);
        b.box(-3, Y + 9, back - 3, 3, Y + 14, back - 2, BRICK);
        for (int a = -12; a <= 12; a++) {
            int high = 24 + (int) (Math.cos(a * .26) * 20);
            for (int h = 7; h <= high; h++) b.block(a, Y + h, back, Math.floorMod(a, 3) == 0 ? (h == high ? HOT : DARK) : h > 20 ? BRICK : DARK);
        }
        for (int a = -7; a <= 7; a++) for (int h = 30; h <= 44; h++) {
            double r = Math.hypot(a, h - 37);
            if (r < 7 && r > 5.8) b.block(a, Y + h, back - 1, GILT);
            else if (r <= 5.8) b.block(a, Y + h, back - 1, HOT);
        }
    }

    /** The shaft under the Steward's floor, and the long stair from it to the Heart. */
    private static void descent(BuildCanvas b) {
        int deep = CinderHeart.FLOOR;
        for (int step = 0; step <= Y - deep; step++) {
            int z = PIT_NORTH + 2 + step * 2, y = Y - 1 - step;
            for (int x = -5; x <= 5; x++) for (int d = 0; d <= 1; d++) {
                b.block(x, y, z + d, Math.abs(x) == 5 ? RED : Math.abs(x) == 4 ? GILT : BRICK);
                b.block(x, y - 1, z + d, DARK);
                // Nothing over the steps but the hall's own floor, which is what falls in.
                for (int h = 1; h <= 7; h++) if (y + h < Y && b.get(x, y + h, z + d) != null) b.block(x, y + h, z + d, Material.AIR);
            }
            for (int d = 0; d <= 1; d++) for (int h = 1; h <= 7; h++) for (int x : new int[]{-6, 6}) if (y + h < Y - 1) b.block(x, y + h, z + d, h % 4 == 0 ? HOT : masonry(x, y + h, z + d));
            if (y + 8 < Y - 1) for (int d = 0; d <= 1; d++) for (int x = -6; x <= 6; x++) b.block(x, y + 8, z + d, Math.floorMod(z, 8) < 2 ? GILT : masonry(x, y + 8, z + d));
            if (z % 8 == 0) b.block(0, y + 5, z, Material.LIGHT, "level=12,waterlogged=false");
        }
    }
}
