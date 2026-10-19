package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.paper.engagement.QuestAction;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

public record ReputationContract(
    String id,
    FactionId factionId,
    Cadence cadence,
    String title,
    String description,
    QuestAction action,
    int target,
    int reputationReward,
    long markReward,
    int minimumAdventurerLevel,
    String prerequisiteId,
    String chapter
) {
    public ReputationContract(
        String id,
        FactionId factionId,
        Cadence cadence,
        String title,
        String description,
        QuestAction action,
        int target,
        int reputationReward,
        long markReward,
        int minimumAdventurerLevel
    ) {
        this(id, factionId, cadence, title, description, action, target, reputationReward, markReward,
            minimumAdventurerLevel, null, "");
    }

    public ReputationContract {
        if (id == null || !id.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid reputation contract id");
        }
        Objects.requireNonNull(factionId, "factionId");
        Objects.requireNonNull(cadence, "cadence");
        Objects.requireNonNull(action, "action");
        if (title == null || title.isBlank() || title.length() > 64) {
            throw new IllegalArgumentException("Invalid reputation contract title: " + id);
        }
        if (description == null || description.isBlank() || description.length() > 160) {
            throw new IllegalArgumentException("Invalid reputation contract description: " + id);
        }
        if (target < 1 || target > 100_000) {
            throw new IllegalArgumentException("Reputation contract target is outside safe limits: " + id);
        }
        if (reputationReward < 1 || reputationReward > 500 || markReward < 0L || markReward > 100_000L) {
            throw new IllegalArgumentException("Reputation contract reward is outside safe limits: " + id);
        }
        if (minimumAdventurerLevel < 1 || minimumAdventurerLevel > 100) {
            throw new IllegalArgumentException("Invalid contract Adventurer Level: " + id);
        }
        if (prerequisiteId != null && !prerequisiteId.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid story prerequisite: " + id);
        }
        chapter = chapter == null ? "" : chapter.trim();
    }

    public enum Cadence {
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly"),
        STORY("Story");

        private final String label;

        Cadence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Period period(Instant instant) {
            LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
            return switch (this) {
                case DAILY -> {
                    Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
                    yield new Period(date.toString(), date.toEpochDay(), start, start.plusSeconds(86_400L));
                }
                case WEEKLY -> {
                    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    Instant start = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
                    yield new Period(monday.toString(), Math.floorDiv(monday.toEpochDay(), 7L),
                        start, start.plusSeconds(604_800L));
                }
                case MONTHLY -> {
                    YearMonth month = YearMonth.from(date);
                    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                    long serial = month.getYear() * 12L + month.getMonthValue() - 1L;
                    yield new Period(month.toString(), serial, start, end);
                }
                case STORY -> new Period("story", 0L, Instant.EPOCH,
                    Instant.parse("9999-12-31T23:59:59Z"));
            };
        }
    }

    public record Period(String key, long serial, Instant startsAt, Instant endsAt) {
        public Period {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(endsAt, "endsAt");
            if (!endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException("Contract period must end after it starts");
            }
        }
    }
}
