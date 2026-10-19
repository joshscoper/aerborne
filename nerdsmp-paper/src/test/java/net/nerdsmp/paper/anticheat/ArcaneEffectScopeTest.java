package net.nerdsmp.paper.anticheat;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneEffectScopeTest {
    private final UUID player = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @Test
    void anAuthorizationCoversOnlyItsOwnPlayerTargetAndDamageBound() {
        assertTrue(ArcaneEffectScope.run(player, "stormwake", target, 6.0D, () -> {
            assertTrue(ArcaneEffectScope.covers(player, target, 6.0D));
            assertFalse(ArcaneEffectScope.covers(player, UUID.randomUUID(), 1.0D), "another target");
            assertFalse(ArcaneEffectScope.covers(UUID.randomUUID(), target, 1.0D), "another attacker");
            assertFalse(ArcaneEffectScope.covers(player, target, 6.5D), "more damage than authorized");
            return true;
        }));
    }

    @Test
    void authorizationsEndWithTheCallAndCannotBeReplayed() {
        ArcaneEffectScope.run(player, "stormwake", target, 6.0D, () -> true);
        assertFalse(ArcaneEffectScope.inside());
        assertFalse(ArcaneEffectScope.covers(player, target, 1.0D));
    }

    @Test
    void authorizationsEndEvenWhenTheEffectThrows() {
        try {
            ArcaneEffectScope.run(player, "stormwake", target, 6.0D, () -> { throw new IllegalStateException("boom"); });
        } catch (IllegalStateException expected) {
            // The scope must still be closed.
        }
        assertFalse(ArcaneEffectScope.inside());
    }

    @Test
    void anEffectCannotAuthorizeAnotherFromInsideItsOwnEvent() {
        AtomicBoolean nested = new AtomicBoolean(true);
        ArcaneEffectScope.run(player, "stormwake", target, 6.0D, () -> {
            nested.set(ArcaneEffectScope.run(player, "stormwake", UUID.randomUUID(), 6.0D, () -> true));
            return true;
        });
        assertFalse(nested.get());
    }

    @Test
    void malformedAuthorizationsAreRefused() {
        assertFalse(ArcaneEffectScope.run(player, "x", player, 1.0D, () -> true), "self-target");
        assertFalse(ArcaneEffectScope.run(player, "x", target, 0.0D, () -> true), "no damage");
        assertFalse(ArcaneEffectScope.run(player, "x", target, Double.NaN, () -> true), "NaN");
        assertFalse(ArcaneEffectScope.run(null, "x", target, 1.0D, () -> true), "no player");
    }

    @Test
    void authorizationsAreThreadConfined() throws Exception {
        AtomicBoolean seenElsewhere = new AtomicBoolean(true);
        ArcaneEffectScope.run(player, "stormwake", target, 6.0D, () -> {
            Thread other = new Thread(() -> seenElsewhere.set(ArcaneEffectScope.covers(player, target, 1.0D)));
            other.start();
            try {
                other.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return true;
        });
        assertFalse(seenElsewhere.get());
    }
}
