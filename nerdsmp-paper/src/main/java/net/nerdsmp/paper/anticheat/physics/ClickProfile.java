package net.nerdsmp.paper.anticheat.physics;

import java.util.Arrays;

/**
 * Click rhythm statistics. The client processes mouse clicks during its tick, so swing packets are spaced in whole
 * client ticks: intervals are counted in end-of-tick packets, which removes network jitter entirely. A human hand
 * spreads clicks across several interval lengths; a fixed-delay clicker repeats one.
 */
public final class ClickProfile {
    public record Summary(double clicksPerSecond, double modeFraction, double intervalStd, double entropy, int samples) {
        public boolean robotic() {
            return samples >= 40 && clicksPerSecond >= 7.0D && modeFraction >= 0.95D;
        }
    }

    private static final int MAX_INTERVAL = 6; // longer gaps are pauses, not part of a click stream
    private final int[] intervals;
    private int size;
    private int head;

    public ClickProfile(int capacity) {
        intervals = new int[capacity];
    }

    /** Adds the number of client ticks since the previous click; pauses are ignored. */
    public void add(int ticksSinceLastClick) {
        if (ticksSinceLastClick < 0 || ticksSinceLastClick > MAX_INTERVAL) return;
        intervals[head] = ticksSinceLastClick;
        head = (head + 1) % intervals.length;
        if (size < intervals.length) size++;
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
        head = 0;
    }

    public Summary summary() {
        if (size == 0) return new Summary(0.0D, 0.0D, 0.0D, 0.0D, 0);
        int[] histogram = new int[MAX_INTERVAL + 1];
        double sum = 0.0D;
        for (int i = 0; i < size; i++) {
            histogram[intervals[i]]++;
            sum += intervals[i];
        }
        double mean = sum / size;
        double variance = 0.0D;
        for (int i = 0; i < size; i++) variance += (intervals[i] - mean) * (intervals[i] - mean);
        double entropy = 0.0D;
        for (int count : histogram) {
            if (count == 0) continue;
            double p = (double) count / size;
            entropy -= p * Math.log(p) / Math.log(2.0D);
        }
        int mode = Arrays.stream(histogram).max().orElse(0);
        double cps = mean <= 0.0D ? 20.0D * 2.0D : 20.0D / mean;
        return new Summary(cps, (double) mode / size, Math.sqrt(variance / size), entropy, size);
    }
}
