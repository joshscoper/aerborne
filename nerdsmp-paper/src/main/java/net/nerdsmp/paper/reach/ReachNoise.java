package net.nerdsmp.paper.reach;

import java.util.SplittableRandom;

/**
 * Seeded 2D simplex noise (Gustavson's algorithm) with fractal helpers. Immutable after construction, so the
 * world generator can call it from any worker thread.
 */
final class ReachNoise {
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double[][] GRAD = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {0.7071, 0.7071}, {-0.7071, 0.7071}, {0.7071, -0.7071}, {-0.7071, -0.7071}
    };
    private final short[] perm = new short[512];

    ReachNoise(long seed) {
        short[] source = new short[256];
        for (short i = 0; i < 256; i++) source[i] = i;
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            short swap = source[i]; source[i] = source[j]; source[j] = swap;
        }
        for (int i = 0; i < 512; i++) perm[i] = source[i & 255];
    }

    /** Simplex noise in roughly [-1, 1]. */
    double noise(double x, double y) {
        double s = (x + y) * F2;
        int i = fastFloor(x + s), j = fastFloor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t), y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0, j1 = x0 > y0 ? 0 : 1;
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2, y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255, jj = j & 255;
        double n0 = corner(perm[ii + perm[jj]] % 12, x0, y0);
        double n1 = corner(perm[ii + i1 + perm[jj + j1]] % 12, x1, y1);
        double n2 = corner(perm[ii + 1 + perm[jj + 1]] % 12, x2, y2);
        return 70.0 * (n0 + n1 + n2);
    }

    private static double corner(int gradient, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) return 0;
        t *= t;
        return t * t * (GRAD[gradient][0] * x + GRAD[gradient][1] * y);
    }

    /** Fractal sum, normalised to roughly [-1, 1]. */
    double fbm(double x, double y, int octaves) {
        double sum = 0, amplitude = 1, total = 0, frequency = 1;
        for (int o = 0; o < octaves; o++) {
            sum += amplitude * noise(x * frequency + o * 31.7, y * frequency - o * 17.3);
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.03;
        }
        return sum / total;
    }

    /** Ridged fractal in [0, 1]: sharp crests where the noise crosses zero. */
    double ridged(double x, double y, int octaves) {
        double sum = 0, amplitude = 1, total = 0, frequency = 1, weight = 1;
        for (int o = 0; o < octaves; o++) {
            double n = 1.0 - Math.abs(noise(x * frequency + o * 13.1, y * frequency + o * 7.9));
            n *= n * weight;
            weight = Math.min(1.0, n * 1.6);
            sum += amplitude * n;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.1;
        }
        return sum / total;
    }

    static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static double clamp(double v, double min, double max) { return v < min ? min : v > max ? max : v; }
}
