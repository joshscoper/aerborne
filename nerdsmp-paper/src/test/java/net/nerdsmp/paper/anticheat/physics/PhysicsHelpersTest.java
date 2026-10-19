package net.nerdsmp.paper.anticheat.physics;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsHelpersTest {
    private static TimerWindow window() {
        return new TimerWindow(200, 350L, 20);
    }

    private static boolean anyViolation(TimerWindow window, long[] arrivals) {
        boolean flagged = false;
        for (long arrival : arrivals) {
            var reading = window.tick(arrival);
            if (reading != null && reading.violation()) flagged = true;
        }
        return flagged;
    }

    @Test void legitimateClientWithJitterFreezesAndPingDropsNeverTriggersTimer() {
        Random random = new Random(1);
        long[] arrivals = new long[1200];
        long send = 0L;
        for (int i = 0; i < arrivals.length; i++) {
            send += 50_000_000L;
            if (i == 300) send += 2_000_000_000L;          // two-second freeze...
            if (i > 300 && i <= 310) send -= 45_000_000L; // ...then vanilla's 10-tick catch-up burst
            long delay = (i < 600 ? 300_000_000L : 50_000_000L) + random.nextInt(40_000_000); // 250 ms ping drop at tick 600
            arrivals[i] = send + delay;
        }
        java.util.Arrays.sort(arrivals); // network preserves order
        assertFalse(anyViolation(window(), arrivals));
    }

    @Test void sixPercentTimerIsCaught() {
        long[] arrivals = new long[400];
        for (int i = 0; i < arrivals.length; i++) arrivals[i] = (long) (i * 50_000_000L / 1.06D);
        assertTrue(anyViolation(window(), arrivals));
    }

    @Test void fixedDelayClickerIsRoboticButHumanRhythmIsNot() {
        ClickProfile robot = new ClickProfile(50);
        for (int i = 0; i < 50; i++) robot.add(2);
        assertTrue(robot.summary().robotic());
        assertEquals(10.0D, robot.summary().clicksPerSecond(), 1.0E-9);

        ClickProfile human = new ClickProfile(50);
        Random random = new Random(5);
        for (int i = 0; i < 50; i++) human.add(1 + random.nextInt(3));
        assertFalse(human.summary().robotic());
        assertTrue(human.summary().entropy() > 1.0D);
    }

    @Test void pausesAreNotPartOfTheClickStream() {
        ClickProfile clicks = new ClickProfile(10);
        clicks.add(40);
        clicks.add(-1);
        assertEquals(0, clicks.size());
    }

    @Test void rayHitsAndMissesBoxes() {
        var box = new HitboxMath.Box(-0.3, 0, 2.7, 0.3, 1.8, 3.3);
        double[] forward = HitboxMath.direction(0.0F, 0.0F);
        assertEquals(2.7D, HitboxMath.rayHit(0, 1.62, 0, forward, box), 1.0E-9);
        assertEquals(-1.0D, HitboxMath.rayHit(0, 1.62, 0, HitboxMath.direction(90.0F, 0.0F), box));
        assertEquals(-1.0D, HitboxMath.rayHit(2.0, 1.62, 0, forward, box));
        assertEquals(0.0D, HitboxMath.rayHit(0, 1.0, 3.0, forward, box), 1.0E-9);
        assertEquals(2.7D, HitboxMath.distance(0, 1.0, 0, box), 1.0E-9);
        assertEquals(0.0D, HitboxMath.offsetToCenter(0, 0.9, 0, forward, box), 1.0E-6);
    }

    @Test void anglesWrapTheShortWay() {
        assertEquals(-170.0F, HitboxMath.wrap(190.0F), 1.0E-4);
        assertEquals(175.0F, HitboxMath.lerpAngle(170.0F, -180.0F, 0.5D), 1.0E-4);
        assertEquals(-175.0F, HitboxMath.wrap(HitboxMath.lerpAngle(170.0F, -160.0F, 0.5D)), 1.0E-4);
    }
}
