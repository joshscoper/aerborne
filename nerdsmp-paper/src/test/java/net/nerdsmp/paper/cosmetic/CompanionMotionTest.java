package net.nerdsmp.paper.cosmetic;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Companions trail behind their owner smoothly, and trails draw only while moving. */
class CompanionMotionTest {
    @Test void walkingCompanionsStayBehindTheOwner() {
        // Facing +Z (yaw 0): behind means a smaller Z.
        var spot = FollowMath.target(0, 64, 0, 0f, false, 0);
        assertTrue(spot.z() < 0, "behind");
        assertEquals(64, spot.y(), 1e-9, "walkers stay at the owner's height until placed on the ground");
        var flying = FollowMath.target(0, 64, 0, 0f, true, 0);
        assertTrue(flying.y() > 65, "flyers hover near the shoulder");
    }

    @Test void movementEasesAndSnapsWhenFarBehind() {
        var start = new FollowMath.Point(0, 64, 0, 0f);
        var target = new FollowMath.Point(4, 64, 0, 0f);
        var next = FollowMath.step(start, target, false);
        assertTrue(next.x() > 0 && next.x() < 4, "eases part of the way");
        assertFalse(FollowMath.snapped(start, target));
        var far = new FollowMath.Point(40, 64, 0, 0f);
        assertEquals(far, FollowMath.step(start, far, false), "jumps to the owner after a teleport");
        assertTrue(FollowMath.snapped(start, far));
        var settled = FollowMath.step(new FollowMath.Point(4.1, 64, 0, 0f), target, false);
        assertEquals(4.1, settled.x(), 1e-9, "a walker close enough stays put instead of fidgeting");
    }

    @Test void companionsFaceWhereTheyAreGoing() {
        var next = FollowMath.step(new FollowMath.Point(0, 64, 0, 0f), new FollowMath.Point(0, 64, 5, 0f), false);
        assertEquals(0f, next.yaw(), 0.5f, "moving toward +Z faces yaw 0");
        var west = FollowMath.step(new FollowMath.Point(0, 64, 0, 0f), new FollowMath.Point(-5, 64, 0, 0f), false);
        assertEquals(90f, west.yaw(), 0.5f, "moving toward -X faces yaw 90");
    }

    @Test void trailsDrawOnlyWhileMovingExceptTheSwirl() {
        for (var trail : CosmeticCatalog.defaults().trails()) {
            if (trail.pattern().equals("swirl")) continue;
            assertTrue(TrailPatterns.frame(trail.pattern(), 4, 0f, 0, false).isEmpty(), trail.slug() + " stays still when you do");
            boolean drew = false;
            for (long tick = 0; tick < 12; tick++) drew |= !TrailPatterns.frame(trail.pattern(), tick, 0f, 0, true).isEmpty();
            assertTrue(drew, trail.slug() + " draws while walking");
        }
        assertTrue(TrailPatterns.frame("swirl", 1, 0f, 5, true).isEmpty(), "Stardust Waltz waits for a turn");
        assertEquals(8, TrailPatterns.frame("swirl", 1, 0f, 45, false).size());
    }

    @Test void trailsStayLight() {
        Set<String> patterns = Set.of("steps", "scatter", "orbit", "ring", "rise", "streak", "drift", "swirl");
        for (String pattern : patterns) {
            for (long tick = 0; tick < 20; tick++) {
                int particles = TrailPatterns.frame(pattern, tick, 90f, 45, true).stream().mapToInt(TrailPatterns.Puff::count).sum();
                assertTrue(particles <= 8, pattern + " draws at most eight particles a frame");
            }
        }
    }
}
