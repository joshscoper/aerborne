package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AttackCooldownTrackerTest {
    @Test
    void capturedPreAttackStrengthWinsOverTheResetEventFallback() {
        AttackCooldownTracker tracker = new AttackCooldownTracker();
        UUID playerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        tracker.capture(playerId, targetId, 1.0D);

        assertEquals(1.0D, tracker.consume(playerId, targetId, 0.0D), 0.0001D);
        assertEquals(0.0D, tracker.consume(playerId, targetId, 0.0D), 0.0001D);
    }

    @Test
    void capturesAreMatchedToTheAttackedEntityAndExpireSafely() {
        AttackCooldownTracker tracker = new AttackCooldownTracker();
        UUID playerId = UUID.randomUUID();
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();
        var captured = tracker.capture(playerId, firstTarget, 2.0D);

        assertEquals(0.25D, tracker.consume(playerId, secondTarget, 0.25D), 0.0001D);
        tracker.expire(playerId, firstTarget, captured);
        assertEquals(0.4D, tracker.consume(playerId, firstTarget, 0.4D), 0.0001D);
    }
}
