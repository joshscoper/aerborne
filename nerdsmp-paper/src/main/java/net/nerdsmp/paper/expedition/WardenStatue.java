package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

/**
 * A colossus of one of the Three Wardens, hooded, with both hands on a sword grounded in front of it. It's turned on a
 * lathe, height by height, as an ellipse: the folds of the robe are a wave in the radius, so it reads as cloth from a
 * long way off, and the arms are drawn after as thick lines from shoulder to pommel.
 */
final class WardenStatue {
    /** {@code robe} and {@code shade} dress the cloth, {@code bright} the gauntlets and hilt, {@code eye} the glow in the hood. */
    record Dress(Material robe, Material shade, Material bright, Material blade, Material eye) { }

    static final int HIGH = 31;
    private static final int HEM = 18, SHOULDER = 23, NECK = 25;

    private WardenStatue() { }

    private static double lerp(double from, double to, double share) { return from + (to - from) * share; }

    /** Half its width at each height. */
    private static double wide(int h) {
        if (h <= HEM) return lerp(7.6, 4.8, h / (double) HEM);
        if (h <= SHOULDER) return lerp(4.8, 7.4, (h - HEM) / (double) (SHOULDER - HEM));
        if (h <= NECK) return lerp(7.4, 3.6, (h - SHOULDER) / (double) (NECK - SHOULDER));
        return 3.8 * Math.sqrt(Math.max(.05, 1 - Math.pow((h - NECK) / (double) (HIGH - NECK + 1), 2)));
    }

    /** Half its depth at each height. */
    private static double deep(int h) { return h <= NECK ? wide(h) * .68 : wide(h) * 1.05; }

    /** Stands one on {@code x, y, z}, its feet on {@code y}, looking along {@code dirX, dirZ}. */
    static void stand(BuildCanvas b, int x, int y, int z, int dirX, int dirZ, Dress dress) {
        Carving c = new Carving(b, x, y, z, dirX, dirZ);
        for (int h = 0; h <= HIGH; h++) {
            double wide = wide(h), deep = deep(h);
            int reach = (int) Math.ceil(wide * 1.12) + 1;
            for (int a = -reach; a <= reach; a++) for (int f = -reach; f <= reach; f++) {
                double angle = Math.atan2(f, a), fold = h <= SHOULDER ? Math.sin(angle * 11 + h * .05) * Math.min(.1, (HEM + 4 - h) * .02 + .03) : 0;
                double out = Math.hypot(a / wide, f / deep);
                if (out > 1 + fold) continue;
                c.set(a, h, f, fold < -.02 ? dress.shade() : dress.robe());
            }
        }
        hood(c, dress);
        arms(c, dress);
        sword(c, dress);
    }

    /** Seats one on a throne, the foot of the throne on {@code x, y, z}, its knees towards {@code dirX, dirZ}. */
    static void sit(BuildCanvas b, int x, int y, int z, int dirX, int dirZ, Dress dress) {
        Carving c = new Carving(b, x, y, z, dirX, dirZ);
        // The throne: seat, arms, and a back as high as the head with a step cut in its shoulders.
        c.box(-14, 14, 0, 15, -13, 3, dress.shade());
        for (int h = 16; h <= 64; h++) {
            int half = h < 50 ? 16 : 16 - (h - 49);
            if (half < 3) break;
            c.box(-half, half, h, h, -15, -10, h % 16 == 0 ? dress.bright() : dress.shade());
        }
        // Feet on the ground, shins up to the knee, thighs back to the hip, and cloth across the lap.
        for (int side = -1; side <= 1; side += 2) {
            line(c, side * 6.5, 2, 16, side * 6.5, 2, 12, 3, dress.shade());
            line(c, side * 6.5, 3, 11, side * 6.5, 17, 10, 3.8, dress.robe());
            line(c, side * 6, 18, 10, side * 5.5, 19, -3, 4.8, dress.robe());
        }
        c.box(-4, 4, 14, 20, -2, 8, dress.robe());
        for (int h = 19; h <= 38; h++) {
            double wide = h <= 33 ? lerp(7.6, 10, (h - 19) / 14D) : lerp(10, 5.4, (h - 33) / 5D), deep = wide * .62;
            int reach = (int) Math.ceil(wide) + 1;
            for (int a = -reach; a <= reach; a++) for (int f = -reach; f <= reach; f++) {
                double fold = Math.sin(Math.atan2(f, a) * 13 + h * .05) * .05;
                if (Math.hypot(a / wide, f / deep) <= 1 + fold) c.set(a, h, f - 3, fold < -.02 ? dress.shade() : dress.robe());
            }
        }
        for (int a = -7; a <= 7; a++) c.set(a, 36, 1, dress.bright());
        // A great hood, round at the crown, deep enough to keep its face in the dark.
        for (int h = 37; h <= 52; h++) {
            double up = (h - 37) / 15D, wide = 8.2 * Math.sqrt(1 - up * up * .92) + (h < 40 ? 1 : 0), deep = wide * 1.02;
            int reach = (int) Math.ceil(wide) + 1;
            for (int a = -reach; a <= reach; a++) for (int f = -reach; f <= reach; f++)
                if (Math.hypot(a / wide, f / deep) <= 1) c.set(a, h, f - 1, dress.robe());
        }
        for (int h = 40; h <= 48; h++) {
            int half = h <= 45 ? 4 : 48 - h + 1;
            for (int a = -half; a <= half; a++) {
                for (int f = 3; f <= 9; f++) c.set(a, h, f, Material.AIR);
                c.set(a, h, 2, Material.BLACK_CONCRETE);
            }
        }
        c.both(2, 2, 44, 44, 2, 2, dress.eye());
        c.both(5, 5, 39, 47, 7, 7, dress.shade());
        for (int a = -5; a <= 5; a++) c.set(a, 49, 7, dress.shade());
        // Upper arms down the sides, forearms to the knees, and hands laid flat on them.
        for (int side = -1; side <= 1; side += 2) {
            line(c, side * 10.5, 33, -3, side * 12.5, 25, 0, 3.8, dress.robe());
            line(c, side * 12.5, 25, 0, side * 8, 22.5, 9, 3.2, dress.robe());
            line(c, side * 8, 22.5, 10, side * 7, 22, 14, 2.6, dress.shade());
            c.both(6, 10, 22, 24, 6, 6, dress.bright());
        }
        // A thousand years of weather.
        for (int a = -18; a <= 18; a++) for (int h = 0; h <= 62; h++) for (int f = -15; f <= 18; f++) {
            Material here = b.get(c.wx(a, f), y + h, c.wz(a, f));
            if (here == dress.robe() && SampleKit.smooth(a * 2 + h, f, 5, 741) > .6) c.set(a, h, f, Material.MOSSY_STONE_BRICKS);
            else if (here == dress.shade() && SampleKit.noise(a + h * 3, f, 742) < .12) c.set(a, h, f, Material.MOSSY_STONE_BRICKS);
        }
    }

    /** A dark hollow in the front of the hood, with two points of light far back in it. */
    private static void hood(Carving c, Dress dress) {
        for (int h = NECK + 1; h <= NECK + 4; h++) {
            int half = h == NECK + 4 ? 1 : 2;
            for (int a = -half; a <= half; a++) {
                c.set(a, h, 4, Material.AIR);
                c.set(a, h, 3, Material.AIR);
                c.set(a, h, 2, Material.BLACK_CONCRETE);
                c.set(a, h, 1, Material.BLACK_CONCRETE);
            }
        }
        c.both(1, 1, NECK + 2, NECK + 2, 2, 2, dress.eye());
        // The lip of the hood, a line of the bright metal round the opening.
        for (int a = -3; a <= 3; a++) c.set(a, NECK + 5, 4, dress.shade());
        c.both(3, 3, NECK, NECK + 4, 4, 4, dress.shade());
    }

    private static void line(Carving c, double a1, double h1, double f1, double a2, double h2, double f2, double radius, Material block) {
        int steps = (int) Math.ceil(Math.hypot(Math.hypot(a2 - a1, h2 - h1), f2 - f1) * 2);
        int reach = (int) Math.ceil(radius);
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps, a = lerp(a1, a2, t), h = lerp(h1, h2, t), f = lerp(f1, f2, t);
            for (int da = -reach; da <= reach; da++) for (int dh = -reach; dh <= reach; dh++) for (int df = -reach; df <= reach; df++)
                if (da * da + dh * dh + df * df <= radius * radius) c.set((int) Math.round(a) + da, (int) Math.round(h) + dh, (int) Math.round(f) + df, block);
        }
    }

    private static void arms(Carving c, Dress dress) {
        for (int side = -1; side <= 1; side += 2) {
            line(c, side * 6.4, 22, .5, side * 7, 17, 3.4, 2.6, dress.robe());
            line(c, side * 7, 17, 3.4, side * 3, 16, 7.2, 2.1, dress.robe());
            line(c, side * 2.8, 16, 7.4, side * 1.1, 16, 8, 1.7, dress.bright());
        }
    }

    private static void sword(Carving c, Dress dress) {
        c.box(-1, 1, 0, 12, 8, 8, dress.blade());
        c.box(0, 0, 0, 12, 7, 9, dress.blade());
        c.box(-6, 6, 13, 13, 7, 9, dress.bright());
        c.both(5, 6, 14, 14, 7, 9, dress.bright());
        c.box(0, 0, 14, 19, 8, 8, dress.bright());
        c.box(-1, 1, 19, 20, 7, 9, dress.bright());
    }
}
