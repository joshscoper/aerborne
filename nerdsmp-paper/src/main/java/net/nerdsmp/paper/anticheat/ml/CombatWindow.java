package net.nerdsmp.paper.anticheat.ml;

import net.nerdsmp.paper.anticheat.physics.ClickProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * One burst of combat for one player, filled from both the packet thread (clicks, rotations, attacks) and the main
 * thread (reach, aim offset, hitbox results). All access is synchronised on the window. It closes after
 * {@link #DURATION_NANOS} or {@link #MAX_ATTACKS} attacks and becomes one row of model features.
 */
public final class CombatWindow {
    public static final long DURATION_NANOS = 15_000_000_000L;
    public static final int MAX_ATTACKS = 30;
    public static final int MIN_ATTACKS = 6;

    private final long startedNanos;
    private final ClickProfile clicks = new ClickProfile(256);
    private final List<float[]> rotations = new ArrayList<>();
    private final List<Double> aimOffsets = new ArrayList<>();
    private final List<Double> reaches = new ArrayList<>();
    private int attacks;
    private int missingSwings;
    private int hitboxMisses;
    private int evaluatedHits;
    private int targetSwitches;
    private int lastTarget = Integer.MIN_VALUE;
    private int ruleFlags;
    private int pingSum;
    private int pingSamples;
    private long lastAttackNanos;

    public CombatWindow(long startedNanos) {
        this.startedNanos = startedNanos;
    }

    public synchronized boolean closed(long nowNanos) {
        return attacks >= MAX_ATTACKS || nowNanos - startedNanos >= DURATION_NANOS;
    }

    public synchronized boolean usable() {
        return attacks >= MIN_ATTACKS;
    }

    public synchronized void attack(int entityId, long nanos) {
        attacks++;
        if (lastTarget != Integer.MIN_VALUE && entityId != lastTarget) targetSwitches++;
        lastTarget = entityId;
        lastAttackNanos = nanos;
    }

    public synchronized void click(int ticksSincePrevious) { clicks.add(ticksSincePrevious); }
    public synchronized void missingSwing() { missingSwings++; }
    public synchronized void rotation(float yawDelta, float pitchDelta) { if (rotations.size() < 2_000) rotations.add(new float[] {yawDelta, pitchDelta}); }
    public synchronized void ruleFlag() { ruleFlags++; }
    public synchronized void ping(int ping) { pingSum += ping; pingSamples++; }

    public synchronized void hit(double reach, double aimOffset, boolean insideHitbox) {
        evaluatedHits++;
        if (!insideHitbox) hitboxMisses++;
        if (reach >= 0.0D) reaches.add(reach);
        if (aimOffset >= 0.0D) aimOffsets.add(aimOffset);
    }

    public synchronized long lastAttackNanos() {
        return lastAttackNanos;
    }

    public synchronized double[] features() {
        double[] x = new double[Features.COUNT];
        ClickProfile.Summary click = clicks.summary();
        x[Features.CPS] = click.clicksPerSecond();
        x[Features.CLICK_MODE] = click.modeFraction();
        x[Features.CLICK_STD] = click.intervalStd();
        x[Features.CLICK_ENTROPY] = click.entropy();

        int n = rotations.size();
        if (n > 0) {
            double yawSum = 0, yawSq = 0, pitchSum = 0, pitchSq = 0, jerk = 0;
            int locked = 0, turning = 0, snaps = 0;
            List<Double> pitchSteps = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                double yaw = Math.abs(rotations.get(i)[0]);
                double pitch = Math.abs(rotations.get(i)[1]);
                yawSum += yaw; yawSq += yaw * yaw; pitchSum += pitch; pitchSq += pitch * pitch;
                if (i > 0) jerk += Math.abs(yaw - Math.abs(rotations.get(i - 1)[0]));
                if (yaw > 1.0D) { turning++; if (pitch == 0.0D) locked++; }
                if (i + 1 < n && yaw > 25.0D && Math.abs(rotations.get(i + 1)[0]) < 1.5D) snaps++;
                if (pitch > 1.0E-4D && pitch < 20.0D) pitchSteps.add(pitch);
            }
            x[Features.YAW_MEAN] = yawSum / n;
            x[Features.YAW_STD] = Math.sqrt(Math.max(0.0D, yawSq / n - x[Features.YAW_MEAN] * x[Features.YAW_MEAN]));
            x[Features.PITCH_MEAN] = pitchSum / n;
            x[Features.PITCH_STD] = Math.sqrt(Math.max(0.0D, pitchSq / n - x[Features.PITCH_MEAN] * x[Features.PITCH_MEAN]));
            x[Features.YAW_JERK] = n > 1 ? jerk / (n - 1) : 0.0D;
            x[Features.PITCH_LOCK] = turning == 0 ? 0.0D : (double) locked / turning;
            x[Features.GCD_ERROR] = gcdError(pitchSteps);
            x[Features.SNAP_RATIO] = (double) snaps / n;
        }
        x[Features.AIM_OFFSET_MEAN] = mean(aimOffsets);
        x[Features.AIM_OFFSET_STD] = std(aimOffsets);
        x[Features.REACH_MEAN] = mean(reaches);
        x[Features.REACH_MAX] = reaches.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
        x[Features.HITBOX_MISS] = evaluatedHits == 0 ? 0.0D : (double) hitboxMisses / evaluatedHits;
        x[Features.MISSING_SWING] = attacks == 0 ? 0.0D : (double) missingSwings / attacks;
        double seconds = Math.max(1.0D, (lastAttackNanos - startedNanos) / 1.0E9D);
        x[Features.ATTACK_RATE] = attacks / seconds;
        x[Features.TARGET_SWITCH] = attacks == 0 ? 0.0D : (double) targetSwitches / attacks;
        x[Features.PING] = pingSamples == 0 ? 0.0D : (double) pingSum / pingSamples;
        x[Features.RULE_FLAGS] = ruleFlags;
        return x;
    }

    /**
     * Vanilla turns the mouse in whole steps of a sensitivity-derived unit, so pitch changes share a common divisor.
     * Returns the fraction of steps that do not fit the estimated divisor (aim-assist and smoothing break the pattern).
     */
    static double gcdError(List<Double> steps) {
        if (steps.size() < 10) return 0.0D;
        double divisor = steps.getFirst();
        for (double step : steps) divisor = approximateGcd(divisor, step);
        if (divisor < 1.0E-4D) return 1.0D;
        int misfits = 0;
        for (double step : steps) {
            double ratio = step / divisor;
            if (Math.abs(ratio - Math.rint(ratio)) > 0.05D) misfits++;
        }
        return (double) misfits / steps.size();
    }

    private static double approximateGcd(double a, double b) {
        for (int guard = 0; guard < 64 && b > 1.0E-4D; guard++) {
            double remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    private static double mean(List<Double> values) {
        return values.isEmpty() ? 0.0D : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
    }

    private static double std(List<Double> values) {
        if (values.size() < 2) return 0.0D;
        double mean = mean(values);
        double sum = 0.0D;
        for (double value : values) sum += (value - mean) * (value - mean);
        return Math.sqrt(sum / values.size());
    }
}
