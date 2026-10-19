package net.nerdsmp.paper.forge.arcane;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure rules for Arcane Signatures, kept apart from Bukkit so they are tested directly. */
public final class SignatureRules {
    private SignatureRules() {
    }

    /**
     * A counter that fills by one per qualifying action, at most once per server tick, empties after
     * {@code decayMillis} without progress, and releases when it reaches the threshold. A release
     * starts a cooldown during which nothing is gained. Used for Static Charge and Resonance.
     */
    public record Counter(int value, long lastGainMillis, int lastTick, long readyAtMillis) {
        public static final Counter EMPTY = new Counter(0, 0L, Integer.MIN_VALUE, 0L);

        public boolean cooling(long now) {
            return now < readyAtMillis;
        }
    }

    public record Step(Counter counter, boolean released, boolean counted) {
    }

    public static Step gain(Counter counter, long now, int tick, int threshold, long decayMillis, long cooldownMillis) {
        if (threshold <= 0) return new Step(counter, false, false);
        if (counter.cooling(now) || counter.lastTick() == tick) return new Step(counter, false, false);
        int current = decayMillis > 0 && now - counter.lastGainMillis() > decayMillis ? 0 : counter.value();
        int next = current + 1;
        if (next >= threshold) {
            return new Step(new Counter(0, now, tick, now + Math.max(0L, cooldownMillis)), true, true);
        }
        return new Step(new Counter(next, now, tick, counter.readyAtMillis()), false, true);
    }

    /** Effective cooldown on an unstable (overcharged) item: longer by a percentage per point. */
    public static long cooldownMillis(long baseSeconds, int instability, int percentPerPoint) {
        long base = Math.max(0L, baseSeconds) * 1000L;
        return base + base * Math.max(0, instability) * Math.max(0, percentPerPoint) / 100L;
    }

    public record Point(UUID id, double x, double y, double z) {
        double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Strike(UUID target, double damage, Point from, Point to) {
    }

    /**
     * Plans a chain from {@code origin}: each hop goes to the nearest unvisited candidate within
     * {@code range} of the previous node. Damage falls off per hop. At most {@code maximumTargets}
     * strikes; the origin itself is never struck again. Candidates are expected to be pre-filtered
     * for eligibility (protection, PvP, line of sight).
     */
    public static List<Strike> chain(Point origin, List<Point> candidates, int maximumTargets, double range,
                                     double baseDamage, double falloff) {
        List<Strike> strikes = new ArrayList<>();
        if (maximumTargets <= 0 || range <= 0.0D || !(baseDamage > 0.0D)) return strikes;
        List<Point> remaining = new ArrayList<>();
        for (Point candidate : candidates) {
            if (!candidate.id().equals(origin.id()) && remaining.stream().noneMatch(point -> point.id().equals(candidate.id()))) {
                remaining.add(candidate);
            }
        }
        double rangeSquared = range * range;
        Point current = origin;
        double damage = baseDamage;
        while (strikes.size() < maximumTargets && !remaining.isEmpty()) {
            Point nearest = null;
            double best = Double.MAX_VALUE;
            for (Point candidate : remaining) {
                double distance = current.distanceSquared(candidate);
                if (distance <= rangeSquared && distance < best) {
                    best = distance;
                    nearest = candidate;
                }
            }
            if (nearest == null) break;
            strikes.add(new Strike(nearest.id(), damage, current, nearest));
            remaining.remove(nearest);
            current = nearest;
            damage *= falloff;
            if (!(damage > 0.01D)) break;
        }
        return strikes;
    }
}
