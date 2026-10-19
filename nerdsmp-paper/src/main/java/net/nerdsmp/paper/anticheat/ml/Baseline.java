package net.nerdsmp.paper.anticheat.ml;

import java.util.Arrays;
import java.util.Random;

/**
 * Unsupervised "how unusual is this" score that works before anything is labelled: each feature is compared with
 * the population median using the median absolute deviation, which a few cheaters cannot drag around the way they
 * could a mean. Advisory only; it never triggers actions.
 */
public final class Baseline {
    private static final int CAPACITY = 5_000;
    private final double[][] reservoir = new double[CAPACITY][];
    private final Random random = new Random(3L);
    private long seen;
    private double[] median;
    private double[] spread;
    private int sinceRefresh;

    public synchronized void observe(double[] features) {
        seen++;
        if (seen <= CAPACITY) reservoir[(int) seen - 1] = features.clone();
        else {
            long slot = (long) (random.nextDouble() * seen);
            if (slot < CAPACITY) reservoir[(int) slot] = features.clone();
        }
        if (++sinceRefresh >= 25 || median == null) refresh();
    }

    public synchronized int samples() {
        return (int) Math.min(seen, CAPACITY);
    }

    /** Mean of the three largest robust z-scores, capped at 10. 0 until 50 windows have been seen. */
    public synchronized double anomaly(double[] features, int[] topFeatures) {
        if (median == null || samples() < 50) return 0.0D;
        double[] z = new double[features.length];
        for (int f = 0; f < features.length; f++) z[f] = Math.min(10.0D, Math.abs(features[f] - median[f]) / spread[f]);
        Integer[] order = new Integer[z.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(z[b], z[a]));
        if (topFeatures != null) for (int i = 0; i < Math.min(topFeatures.length, order.length); i++) topFeatures[i] = order[i];
        return (z[order[0]] + z[order[1]] + z[order[2]]) / 3.0D;
    }

    private void refresh() {
        sinceRefresh = 0;
        int size = samples();
        if (size == 0) return;
        int features = reservoir[0].length;
        median = new double[features];
        spread = new double[features];
        double[] column = new double[size];
        for (int f = 0; f < features; f++) {
            for (int i = 0; i < size; i++) column[i] = reservoir[i][f];
            Arrays.sort(column, 0, size);
            double m = column[size / 2];
            median[f] = m;
            for (int i = 0; i < size; i++) column[i] = Math.abs(column[i] - m);
            Arrays.sort(column, 0, size);
            spread[f] = Math.max(1.0E-3D, 1.4826D * column[size / 2]);
        }
    }
}
