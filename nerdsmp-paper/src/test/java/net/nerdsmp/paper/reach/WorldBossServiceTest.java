package net.nerdsmp.paper.reach;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class WorldBossServiceTest {
    static final ZoneId DENVER = ZoneId.of("America/Denver");

    static Instant at(int hour, int minute) { return LocalDateTime.of(2026, 11, 12, hour, minute).atZone(DENVER).toInstant(); }

    @Test void comesOnEvenHours() {
        assertEquals(at(10, 0), WorldBossService.next(at(9, 13), DENVER, 120));
        assertEquals(at(12, 0), WorldBossService.next(at(10, 0), DENVER, 120), "exactly on the hour waits for the next one");
        assertEquals(LocalDateTime.of(2026, 11, 13, 0, 0).atZone(DENVER).toInstant(), WorldBossService.next(at(23, 59), DENVER, 120));
        assertEquals(at(9, 30), WorldBossService.next(at(9, 13), DENVER, 30));
    }

    @Test void payIsSharedByDamageWithAFloor() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), idle = UUID.randomUUID();
        Map<UUID, Long> pay = WorldBossService.payouts(Map.of(a, 900.0, b, 300.0, idle, 5.0));
        assertEquals(2, pay.size(), "a token hit doesn't earn a share");
        assertTrue(pay.get(a) > pay.get(b));
        assertTrue(pay.get(b) >= 200);
        assertEquals(2_500 + 1_000, pay.get(a) + pay.get(b), 2);
        assertTrue(WorldBossService.payouts(Map.of()).isEmpty());
    }

    @Test void settingsAreClamped() {
        var defaults = WorldBossService.settings(null);
        assertTrue(defaults.enabled());
        assertEquals(120, defaults.intervalMinutes());
        assertEquals("thunderhide", defaults.mob());
    }
}
