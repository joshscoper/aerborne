package net.nerdsmp.paper.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class AttackCooldownTracker {
    private final Map<AttackKey, CapturedCooldown> capturedByAttack = new HashMap<>();
    private long sequence;

    CapturedCooldown capture(UUID playerId, UUID targetId, double strength) {
        CapturedCooldown captured = new CapturedCooldown(++sequence, clamp(strength));
        capturedByAttack.put(new AttackKey(playerId, targetId), captured);
        return captured;
    }

    double consume(UUID playerId, UUID targetId, double fallback) {
        CapturedCooldown captured = capturedByAttack.remove(new AttackKey(playerId, targetId));
        return captured == null ? clamp(fallback) : captured.strength();
    }

    void expire(UUID playerId, UUID targetId, CapturedCooldown captured) {
        capturedByAttack.remove(new AttackKey(playerId, targetId), captured);
    }

    private static double clamp(double strength) {
        return Math.max(0.0D, Math.min(1.0D, strength));
    }

    record CapturedCooldown(long sequence, double strength) {
    }

    private record AttackKey(UUID playerId, UUID targetId) {
    }
}
