package net.nerdsmp.paper.booster;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

/**
 * The weekly double XP weekend: from {@code startDay startTime} to {@code endDay endTime} in one time zone, every
 * week, by default Saturday 08:00 to Sunday 23:59. Pure clock arithmetic, so it holds across restarts and needs no
 * saved state.
 */
public record WeekendSchedule(boolean enabled, ZoneId zone, DayOfWeek startDay, LocalTime startTime,
                              DayOfWeek endDay, LocalTime endTime, double multiplier) {
    public WeekendSchedule {
        Objects.requireNonNull(zone); Objects.requireNonNull(startDay); Objects.requireNonNull(startTime);
        Objects.requireNonNull(endDay); Objects.requireNonNull(endTime);
        if (!(multiplier >= 1D) || multiplier > 10D) throw new IllegalArgumentException("Weekend multiplier must be 1 to 10");
        if (startDay == endDay && !endTime.isAfter(startTime)) throw new IllegalArgumentException("The weekend must end after it starts");
    }

    public static WeekendSchedule standard(ZoneId zone) {
        return new WeekendSchedule(true, zone, DayOfWeek.SATURDAY, LocalTime.of(8, 0), DayOfWeek.SUNDAY, LocalTime.of(23, 59), 2D);
    }

    /** One weekend: when it starts and ends. */
    public record Window(Instant start, Instant end) {
        public boolean contains(Instant now) { return !now.isBefore(start) && now.isBefore(end); }
        public Duration left(Instant now) { return Duration.between(now, end).isNegative() ? Duration.ZERO : Duration.between(now, end); }
        public Duration length() { return Duration.between(start, end); }
    }

    /** The weekend that started most recently at or before {@code now} (whether or not it has ended). */
    private Window latest(Instant now) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime start = local.with(TemporalAdjusters.previousOrSame(startDay)).with(startTime);
        if (start.isAfter(local)) start = start.minusWeeks(1);
        ZonedDateTime end = start.with(TemporalAdjusters.nextOrSame(endDay)).with(endTime);
        if (!end.isAfter(start)) end = end.plusWeeks(1);
        return new Window(start.toInstant(), end.toInstant());
    }

    /** The weekend running now, if any. */
    public Optional<Window> current(Instant now) {
        if (!enabled) return Optional.empty();
        Window window = latest(now);
        return window.contains(now) ? Optional.of(window) : Optional.empty();
    }

    /** The next weekend to start after {@code now}. Empty when the event is off. */
    public Optional<Window> next(Instant now) {
        if (!enabled) return Optional.empty();
        Window window = latest(now);
        ZonedDateTime start = window.start().atZone(zone).plusWeeks(1);
        ZonedDateTime end = window.end().atZone(zone).plusWeeks(1);
        return Optional.of(new Window(start.toInstant(), end.toInstant()));
    }

    public boolean active(Instant now) { return current(now).isPresent(); }

    /** The XP multiplier the weekend adds now: {@link #multiplier} during it, 1 otherwise. */
    public double multiplierAt(Instant now) { return active(now) ? multiplier : 1D; }
}
