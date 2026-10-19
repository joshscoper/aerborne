package net.nerdsmp.paper.anticheat.physics;

/**
 * Detects a client running its game clock fast ("timer") from the arrival times of its end-of-tick packets.
 *
 * <p>A legitimate client sends exactly 20 per second. Over a window of N ticks the arrival spacing differs from
 * N x 50 ms only by how much network delay changed between the first and last packet, and a client that froze can
 * only catch up 10 ticks (500 ms) because vanilla drops the rest. So a window that arrives much faster than real
 * time, sustained across several overlapping evaluations, cannot be network noise.
 */
public final class TimerWindow {
    /** Outcome of one evaluation: how many ms early the window arrived and the implied client speed. */
    public record Reading(long earlyMillis, double speed, boolean violation) { }

    private final long[] arrivals;
    private final long earlyThresholdNanos;
    private final int evaluateEvery;
    private int count;
    private int head;
    private int sinceEvaluation;

    /**
     * @param ticks window length, e.g. 200 for ten seconds
     * @param earlyThresholdMillis how much faster than real time the window may arrive before it counts
     */
    public TimerWindow(int ticks, long earlyThresholdMillis, int evaluateEvery) {
        arrivals = new long[ticks + 1];
        earlyThresholdNanos = earlyThresholdMillis * 1_000_000L;
        this.evaluateEvery = evaluateEvery;
    }

    /** Records one end-of-tick arrival. Returns a reading when an evaluation is due, otherwise null. */
    public Reading tick(long nanos) {
        arrivals[head] = nanos;
        head = (head + 1) % arrivals.length;
        if (count < arrivals.length) count++;
        if (count < arrivals.length || ++sinceEvaluation < evaluateEvery) return null;
        sinceEvaluation = 0;
        long oldest = arrivals[head]; // the slot about to be overwritten is the oldest
        long newest = nanos;
        long expected = (arrivals.length - 1) * 50_000_000L;
        long elapsed = Math.max(1L, newest - oldest);
        long early = expected - elapsed;
        return new Reading(early / 1_000_000L, (double) expected / elapsed, early > earlyThresholdNanos);
    }

    /** Discards history, e.g. after a world change or long pause, so old arrivals cannot mix with new ones. */
    public void reset() {
        count = 0;
        head = 0;
        sinceEvaluation = 0;
    }
}
