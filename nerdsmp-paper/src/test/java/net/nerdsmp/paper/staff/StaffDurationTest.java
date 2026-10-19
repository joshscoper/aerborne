package net.nerdsmp.paper.staff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StaffDurationTest {
    @Test
    void acceptsUnitDurations() {
        assertEquals(Duration.ofMinutes(30), StaffManager.moderationDuration("30m").orElseThrow());
        assertEquals(Duration.ofHours(2), StaffManager.moderationDuration("2h").orElseThrow());
        assertEquals(Duration.ofDays(7), StaffManager.moderationDuration("7d").orElseThrow());
    }

    @Test
    void keepsBareNumbersAsMinutes() {
        assertEquals(Duration.ofMinutes(1440), StaffManager.moderationDuration("1440").orElseThrow());
    }

    @Test
    void rejectsZeroMalformedAndOverlongDurations() {
        assertTrue(StaffManager.moderationDuration("0").isEmpty());
        assertTrue(StaffManager.moderationDuration("soon").isEmpty());
        assertTrue(StaffManager.moderationDuration("31d").isEmpty());
        assertTrue(StaffManager.moderationDuration("43201").isEmpty());
        assertEquals(Duration.ofDays(30), StaffManager.moderationDuration("30d").orElseThrow());
    }
}
