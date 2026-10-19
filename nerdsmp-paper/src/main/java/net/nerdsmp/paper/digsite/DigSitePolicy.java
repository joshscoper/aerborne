package net.nerdsmp.paper.digsite;

/** Pure cooldown and restoration decisions shared by the live controller and its tests. */
final class DigSitePolicy {
    static final long CLOCK_SKEW_MILLIS = 300_000L;
    private DigSitePolicy() { }

    enum MarkerState { ABSENT, PRESENT, CORRUPT }
    enum BlockState { EXPECTED, AIR, FOREIGN }
    enum Decision { INACTIVE, BLOCKED, READY, WAITING, RESTORE, RELEASE_RETAINED, OCCUPIED }
    record Marker(MarkerState state, long due) { }

    static Marker marker(boolean present, boolean correctType, Long due, long now, int maximumCooldownSeconds) {
        if (!present) return new Marker(MarkerState.ABSENT, 0);
        long maximum = saturatedAdd(now, (long) maximumCooldownSeconds * 1000L + CLOCK_SKEW_MILLIS);
        if (!correctType || due == null || due <= 0 || due > maximum)
            return new Marker(MarkerState.CORRUPT, 0);
        return new Marker(MarkerState.PRESENT, due);
    }

    static Decision decide(boolean active, boolean quarantined, Marker marker, BlockState block, boolean occupied, long now) {
        if (!active) return Decision.INACTIVE;
        if (quarantined || marker.state() == MarkerState.CORRUPT || block == BlockState.FOREIGN)
            return Decision.BLOCKED;
        if (marker.state() == MarkerState.ABSENT)
            return block == BlockState.EXPECTED ? Decision.READY : Decision.BLOCKED;
        if (marker.due() > now) return Decision.WAITING;
        // A crash or final event cancellation can leave the captured block in place.
        if (block == BlockState.EXPECTED) return Decision.RELEASE_RETAINED;
        return occupied ? Decision.OCCUPIED : Decision.RESTORE;
    }

    static long due(long now, int cooldownSeconds) {
        if (now <= 0 || cooldownSeconds < 1) throw new IllegalArgumentException("Invalid dig-node cooldown clock");
        return Math.addExact(now, Math.multiplyExact((long) cooldownSeconds, 1000L));
    }

    static String markerKey(java.util.UUID generation, int x, int y, int z) {
        java.util.Objects.requireNonNull(generation);
        return "digsite_" + generation.toString().replace("-", "") + "_"
            + (x & 15) + "_" + (y + 64) + "_" + (z & 15);
    }

    private static long saturatedAdd(long first, long second) {
        try { return Math.addExact(first, second); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }
}
