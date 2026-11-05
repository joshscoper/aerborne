package net.nerdsmp.paper.reach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

/**
 * A river or road: a polyline resampled every {@link #STEP} blocks, each sample carrying one value (a water level or
 * a graded road height) and a half-width. Nearest-point queries go through a coarse grid, so a column only looks at
 * the few samples near it.
 */
final class ReachPath {
    static final double STEP = 4.0;
    private static final int CELL = 32;
    final double[] xs, zs, halfWidths;
    double[] values;
    private final Map<Long, int[]> grid = new HashMap<>();

    record Hit(double distance, double value, double halfWidth, int index, double along) { }

    ReachPath(double[][] points, double startHalfWidth, double endHalfWidth) {
        List<double[]> samples = new ArrayList<>();
        double total = 0;
        for (int i = 1; i < points.length; i++) total += Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
        double walked = 0;
        for (int i = 1; i < points.length; i++) {
            double[] a = points[i - 1], b = points[i];
            double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int steps = Math.max(1, (int) Math.ceil(length / STEP));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps;
                samples.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, (walked + length * t) / total});
            }
            walked += length;
        }
        double[] last = points[points.length - 1];
        samples.add(new double[] {last[0], last[1], 1.0});
        int n = samples.size();
        xs = new double[n]; zs = new double[n]; halfWidths = new double[n]; values = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = samples.get(i)[0];
            zs[i] = samples.get(i)[1];
            halfWidths[i] = startHalfWidth + (endHalfWidth - startHalfWidth) * samples.get(i)[2];
        }
        Map<Long, List<Integer>> building = new HashMap<>();
        for (int i = 0; i < n - 1; i++) {
            int minX = Math.floorDiv((int) Math.floor(Math.min(xs[i], xs[i + 1])), CELL), maxX = Math.floorDiv((int) Math.ceil(Math.max(xs[i], xs[i + 1])), CELL);
            int minZ = Math.floorDiv((int) Math.floor(Math.min(zs[i], zs[i + 1])), CELL), maxZ = Math.floorDiv((int) Math.ceil(Math.max(zs[i], zs[i + 1])), CELL);
            for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++)
                building.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(i);
        }
        building.forEach((k, list) -> grid.put(k, list.stream().mapToInt(Integer::intValue).toArray()));
    }

    int size() { return xs.length; }

    /**
     * Rounds a polyline's corners by Chaikin's cutting: each pass replaces every segment with points a quarter and
     * three quarters along it, keeping the two ends. Three passes turn a route's kinks into sweeping curves that stay
     * inside the original line's bends.
     */
    static double[][] chaikin(double[][] points, int passes) {
        double[][] p = points;
        for (int pass = 0; pass < passes; pass++) {
            List<double[]> out = new ArrayList<>();
            out.add(p[0]);
            for (int i = 0; i < p.length - 1; i++) {
                double[] a = p[i], b = p[i + 1];
                out.add(new double[] {a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25});
                out.add(new double[] {a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75});
            }
            out.add(p[p.length - 1]);
            p = out.toArray(new double[0][]);
        }
        return p;
    }

    /** Fills each sample's value from {@code sampler(x, z)}. */
    void sample(DoubleBinaryOperator sampler) {
        for (int i = 0; i < xs.length; i++) values[i] = sampler.applyAsDouble(xs[i], zs[i]);
    }

    /** Each value becomes at most the one before it, so water only ever runs downhill. */
    void neverRise(double drop) {
        for (int i = 1; i < values.length; i++) values[i] = Math.min(values[i], values[i - 1] - drop);
    }

    /** Moving average over {@code radius} samples each side. */
    void smooth(int radius) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double sum = 0; int count = 0;
            for (int j = Math.max(0, i - radius); j <= Math.min(values.length - 1, i + radius); j++) { sum += values[j]; count++; }
            out[i] = sum / count;
        }
        values = out;
    }

    /** Limits the change between neighbouring samples so a road never climbs faster than {@code perBlock}. */
    void limitSlope(double perBlock) {
        double max = perBlock * STEP;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i < values.length; i++) values[i] = ReachNoise.clamp(values[i], values[i - 1] - max, values[i - 1] + max);
            for (int i = values.length - 2; i >= 0; i--) values[i] = ReachNoise.clamp(values[i], values[i + 1] - max, values[i + 1] + max);
        }
    }

    /** The nearest point within {@code reach} blocks, or null. */
    Hit nearest(double x, double z, double reach) {
        int cx0 = Math.floorDiv((int) Math.floor(x - reach), CELL), cx1 = Math.floorDiv((int) Math.floor(x + reach), CELL);
        int cz0 = Math.floorDiv((int) Math.floor(z - reach), CELL), cz1 = Math.floorDiv((int) Math.floor(z + reach), CELL);
        double best = reach; int bestIndex = -1; double bestT = 0;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            int[] segments = grid.get(key(cx, cz));
            if (segments == null) continue;
            for (int i : segments) {
                double ax = xs[i], az = zs[i], bx = xs[i + 1], bz = zs[i + 1];
                double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
                double t = len2 == 0 ? 0 : ReachNoise.clamp(((x - ax) * dx + (z - az) * dz) / len2, 0, 1);
                double d = Math.hypot(x - (ax + dx * t), z - (az + dz * t));
                if (d < best) { best = d; bestIndex = i; bestT = t; }
            }
        }
        if (bestIndex < 0) return null;
        double value = values[bestIndex] + (values[bestIndex + 1] - values[bestIndex]) * bestT;
        double width = halfWidths[bestIndex] + (halfWidths[bestIndex + 1] - halfWidths[bestIndex]) * bestT;
        return new Hit(best, value, width, bestIndex, (bestIndex + bestT) / (xs.length - 1));
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }
}
