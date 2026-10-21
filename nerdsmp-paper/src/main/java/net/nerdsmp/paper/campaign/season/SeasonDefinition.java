package net.nerdsmp.paper.campaign.season;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A season campaign as staff write it in {@code campaign/season.yml}: server-wide goals that player
 * actions feed, and milestones that unlock rewards for everyone once their goal thresholds are met.
 */
public record SeasonDefinition(String id, String title, String description, Instant startsAt, Instant endsAt,
                               long playerDailyCap, Map<String, Goal> goals, Map<String, Milestone> milestones) {
    public SeasonDefinition {
        Objects.requireNonNull(id);
        goals = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(goals));
        milestones = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(milestones));
    }

    public boolean active(Instant now) { return !now.isBefore(startsAt) && now.isBefore(endsAt); }

    /** The milestone (if any) whose rewards unlock an expedition. */
    public Optional<Milestone> unlocking(String expedition) {
        return milestones.values().stream().filter(milestone -> milestone.rewards().stream()
            .anyMatch(reward -> reward instanceof Reward.Unlock unlock && expedition.equals(unlock.expedition()))).findFirst();
    }

    public record Goal(String id, String title, String description, String icon, long target, Long dailyCap, List<Source> sources) {
        public Goal {
            sources = List.copyOf(sources);
        }
    }

    public enum SourceType {
        MINE, SMELT, CRAFT, FISH, KILL, BOSS, EXPEDITION, CONTRIBUTE;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * One kind of player action that feeds a goal. {@code filters} are materials (mine, smelt, craft,
     * fish, contribute), mob ids (kill, boss: {@code custom:<id>}, {@code vanilla:<type>},
     * {@code hostile}) or expedition ids (expedition). An empty filter accepts everything.
     */
    public record Source(SourceType type, Set<String> filters, long points, Map<String, Double> tierMultipliers) {
        public Source {
            filters = Set.copyOf(filters);
            tierMultipliers = Map.copyOf(tierMultipliers);
        }

        public boolean accepts(String value) { return filters.isEmpty() || filters.contains(value); }
    }

    public record Milestone(String id, String title, String description, String icon, Map<String, Long> requires, List<Reward> rewards) {
        public Milestone {
            requires = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(requires));
            rewards = List.copyOf(rewards);
        }

        public boolean claimable() {
            return rewards.stream().anyMatch(reward -> reward instanceof Reward.Item || reward instanceof Reward.Marks);
        }
    }

    public enum BoostKind {
        SKILL_XP("skill XP"), JOB_MARKS("job Marks");

        private final String label;

        BoostKind(String label) { this.label = label; }

        public String label() { return label; }
        public String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    }

    public sealed interface Reward {
        /** A potion effect every online player gets for the duration. */
        record Buff(String effect, int amplifier, Duration duration) implements Reward { }
        /** A percentage boost to skill XP or job Marks for the duration. */
        record Boost(BoostKind kind, int percent, Duration duration) implements Reward { }
        /** Every account can claim this once with /campaign claim. */
        record Item(String item, int amount) implements Reward { }
        /** Every account can claim these Marks once with /campaign claim. */
        record Marks(long amount) implements Reward { }
        /** Opens an expedition, or a server feature such as world-events. */
        record Unlock(String expedition, String feature) implements Reward { }
        record Broadcast(String message) implements Reward { }
        /** A console command run once when the milestone is reached. */
        record Command(String command) implements Reward { }
    }
}
