package net.nerdsmp.paper.anticheat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WatchdogMathTest {
    @Test
    void evidenceDecaysWithoutGoingNegative() {
        assertEquals(9.0D, WatchdogMath.decay(10.0D, 10_000L, 0.1D), 0.000001D);
        assertEquals(0.0D, WatchdogMath.decay(1.0D, 100_000L, 1.0D), 0.000001D);
    }

    @Test
    void reachUsesNearestPointOnBoundingBox() {
        assertEquals(2.0D, WatchdogMath.distanceToBox(0, 1, 0, 2, 0, -1, 3, 2, 1), 0.000001D);
        assertEquals(0.0D, WatchdogMath.distanceToBox(2.5, 1, 0, 2, 0, -1, 3, 2, 1), 0.000001D);
    }
}
