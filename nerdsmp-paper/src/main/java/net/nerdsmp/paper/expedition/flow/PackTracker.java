package net.nerdsmp.paper.expedition.flow;

import java.util.List;

/**
 * The rules for one standing pack, free of Bukkit. A pack waits (or walks its patrol) until someone
 * comes close enough to be seen, is hit, or a fight spills into it. It then fights together. If the
 * party gets away or dies, it gives up, walks home unharmed and heals. Dead members stay dead, so a
 * wipe never brings back what was already killed.
 */
public final class PackTracker {
    public enum State { WAITING, FIGHTING, RETURNING, DEAD }

    /** How long a fighting pack keeps looking for a target before giving up. */
    public static final long GIVE_UP_MILLIS = 4_000;
    /** How long a leashed pack walks home before it's put back. */
    public static final long RETURN_MILLIS = 5_000;

    /** What the run sees this tick. Distances are in blocks; a value below zero means nobody. */
    public record View(int alive, double seenDistance, double homeDistance, boolean hasTarget) { }

    private final int aggro;
    private final int leash;
    private State state = State.WAITING;
    private long lostTargetAt = -1;
    private long returnedAt = -1;

    public PackTracker(int aggro, int leash) {
        this.aggro = aggro;
        this.leash = leash;
    }

    public State state() { return state; }
    public boolean waiting() { return state == State.WAITING; }
    public boolean fighting() { return state == State.FIGHTING; }

    /** A hit, a taunt or a neighbouring fight wakes the pack. True when it wasn't already fighting. */
    public boolean pull() {
        if (state == State.DEAD || state == State.FIGHTING) return false;
        state = State.FIGHTING;
        lostTargetAt = -1;
        returnedAt = -1;
        return true;
    }

    /** Moves the pack on. Returns the state it entered, or null when nothing changed. */
    public State tick(View view, long now) {
        if (state == State.DEAD) return null;
        if (view.alive() <= 0) return state = State.DEAD;
        switch (state) {
            case WAITING -> {
                if (view.seenDistance() >= 0 && view.seenDistance() <= aggro) return pull() ? State.FIGHTING : null;
            }
            case FIGHTING -> {
                if (view.homeDistance() < 0 || view.homeDistance() > leash) return giveUp(now);
                if (view.hasTarget()) {
                    lostTargetAt = -1;
                    return null;
                }
                if (lostTargetAt < 0) lostTargetAt = now;
                if (now - lostTargetAt >= GIVE_UP_MILLIS) return giveUp(now);
            }
            case RETURNING -> {
                if (now - returnedAt >= RETURN_MILLIS) {
                    returnedAt = -1;
                    return state = State.WAITING;
                }
            }
            default -> { }
        }
        return null;
    }

    private State giveUp(long now) {
        lostTargetAt = -1;
        returnedAt = now;
        return state = State.RETURNING;
    }

    /** The next stop on a patrol route that walks there and back. Returns {stop, direction}. */
    public static int[] nextStop(int stop, int direction, int stops) {
        if (stops <= 1) return new int[]{0, 1};
        if (stop + direction < 0 || stop + direction >= stops) direction = -direction;
        return new int[]{stop + direction, direction};
    }

    /** The spot a pack member takes around its centre: a tight cluster, spread by index. */
    public static double[] offset(int index, int size) {
        if (size <= 1) return new double[]{0, 0};
        double angle = 2 * Math.PI * index / size;
        double radius = size <= 3 ? 1.3 : size <= 5 ? 1.8 : 2.4;
        return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    /** Living pack members left in {@code packs} that still block a boss door. */
    public static int blocking(List<PackTracker> packs) {
        return (int) packs.stream().filter(pack -> pack.state != State.DEAD).count();
    }
}
