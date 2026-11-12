package net.nerdsmp.paper.booster;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

final class WeekendScheduleTest {
    static final ZoneId DENVER = ZoneId.of("America/Denver");
    final WeekendSchedule weekend = WeekendSchedule.standard(DENVER);

    static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(DENVER).toInstant();
    }

    @Test void runsFromSaturdayMorningToSundayNight() {
        // 14 November 2026 is a Saturday.
        assertFalse(weekend.active(at(2026, 11, 13, 23, 0)), "Friday night");
        assertFalse(weekend.active(at(2026, 11, 14, 7, 59)), "Saturday before 8");
        assertTrue(weekend.active(at(2026, 11, 14, 8, 0)), "Saturday 8:00");
        assertTrue(weekend.active(at(2026, 11, 15, 12, 0)), "Sunday noon");
        assertTrue(weekend.active(at(2026, 11, 15, 23, 58)), "Sunday 23:58");
        assertFalse(weekend.active(at(2026, 11, 15, 23, 59)), "Sunday 23:59 is the end");
        assertFalse(weekend.active(at(2026, 11, 18, 12, 0)), "Wednesday");
        assertEquals(2D, weekend.multiplierAt(at(2026, 11, 14, 9, 0)));
        assertEquals(1D, weekend.multiplierAt(at(2026, 11, 18, 9, 0)));
    }

    @Test void windowsAndTheNextOne() {
        var window = weekend.current(at(2026, 11, 15, 12, 0)).orElseThrow();
        assertEquals(at(2026, 11, 14, 8, 0), window.start());
        assertEquals(at(2026, 11, 15, 23, 59), window.end());
        // From a Wednesday, the next is this coming Saturday; from inside one, the following week's.
        assertEquals(at(2026, 11, 14, 8, 0), weekend.next(at(2026, 11, 11, 12, 0)).orElseThrow().start());
        assertEquals(at(2026, 11, 21, 8, 0), weekend.next(at(2026, 11, 14, 9, 0)).orElseThrow().start());
    }

    @Test void keepsLocalTimesAcrossDaylightSaving() {
        // Daylight saving ends 1 November 2026; the weekends either side still start at 8:00 local.
        assertTrue(weekend.active(at(2026, 10, 31, 8, 0)));
        assertTrue(weekend.active(at(2026, 11, 7, 8, 0)));
        assertFalse(weekend.active(at(2026, 11, 7, 7, 59)));
    }

    @Test void wrapsPastTheEndOfTheWeek() {
        var friToMon = new WeekendSchedule(true, DENVER, DayOfWeek.FRIDAY, LocalTime.of(18, 0), DayOfWeek.MONDAY, LocalTime.of(6, 0), 2D);
        assertTrue(friToMon.active(at(2026, 11, 16, 5, 0)), "Monday early");
        assertFalse(friToMon.active(at(2026, 11, 16, 6, 0)));
        assertTrue(friToMon.active(at(2026, 11, 13, 18, 0)));
    }

    @Test void offMeansNever() {
        var off = new WeekendSchedule(false, DENVER, DayOfWeek.SATURDAY, LocalTime.of(8, 0), DayOfWeek.SUNDAY, LocalTime.of(23, 59), 2D);
        assertFalse(off.active(at(2026, 11, 14, 12, 0)));
        assertTrue(off.next(at(2026, 11, 14, 12, 0)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new WeekendSchedule(true, DENVER, DayOfWeek.SATURDAY, LocalTime.NOON, DayOfWeek.SATURDAY, LocalTime.NOON, 2D));
        assertThrows(IllegalArgumentException.class, () -> new WeekendSchedule(true, DENVER, DayOfWeek.SATURDAY, LocalTime.NOON, DayOfWeek.SUNDAY, LocalTime.NOON, 0.5D));
    }

    @Test void multipliersFormatPlainly() {
        assertEquals("2", BoosterService.times(2D));
        assertEquals("1.5", BoosterService.times(1.5D));
    }
}
