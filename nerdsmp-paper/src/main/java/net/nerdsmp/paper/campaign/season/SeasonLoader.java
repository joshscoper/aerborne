package net.nerdsmp.paper.campaign.season;

import net.nerdsmp.paper.campaign.season.SeasonDefinition.BoostKind;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Goal;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Milestone;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Source;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.SourceType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads a season file. Structural mistakes fail with the path to fix; {@link SeasonValidator} checks names against the server. */
public final class SeasonLoader {
    private static final String ID = "[a-z0-9_-]{1,64}";

    private SeasonLoader() { }

    /** {@code fallbackStart} is used when the file gives no {@code starts-at}: the first time the season was loaded. */
    public static SeasonDefinition parse(ConfigurationSection yaml, Instant fallbackStart) {
        String id = text(yaml, "id", null);
        if (id == null || !id.matches(ID)) throw new IllegalArgumentException("id: use 1-64 lowercase letters, digits, - or _.");
        Instant start = yaml.contains("starts-at") ? instant(yaml, "starts-at") : fallbackStart;
        Instant end;
        if (yaml.contains("ends-at")) end = instant(yaml, "ends-at");
        else {
            if (!yaml.isInt("duration-days")) throw new IllegalArgumentException("Give ends-at or duration-days.");
            end = start.plus(Duration.ofDays(yaml.getInt("duration-days")));
        }
        if (!end.isAfter(start)) throw new IllegalArgumentException("The season must end after it starts.");
        Map<String, Goal> goals = new LinkedHashMap<>();
        ConfigurationSection goalSection = yaml.getConfigurationSection("goals");
        if (goalSection == null || goalSection.getKeys(false).isEmpty()) throw new IllegalArgumentException("goals: define at least one goal.");
        for (String goalId : goalSection.getKeys(false)) {
            if (!goalId.matches(ID)) throw new IllegalArgumentException("goals." + goalId + ": invalid goal id.");
            ConfigurationSection goal = section(goalSection, goalId, "goals." + goalId);
            List<Source> sources = new ArrayList<>();
            List<?> rawSources = goal.getList("sources", List.of());
            for (int index = 0; index < rawSources.size(); index++)
                sources.add(source(map(rawSources.get(index), "goals." + goalId + ".sources[" + (index + 1) + "]"),
                    "goals." + goalId + ".sources[" + (index + 1) + "]"));
            if (!goal.isInt("target") && !goal.isLong("target")) throw new IllegalArgumentException("goals." + goalId + ".target: must be a whole number.");
            goals.put(goalId, new Goal(goalId, text(goal, "title", titleCase(goalId)), text(goal, "description", ""),
                text(goal, "icon", "beacon").toLowerCase(Locale.ROOT), goal.getLong("target"),
                goal.contains("daily-cap") ? goal.getLong("daily-cap") : null, sources));
        }
        Map<String, Milestone> milestones = new LinkedHashMap<>();
        ConfigurationSection milestoneSection = yaml.getConfigurationSection("milestones");
        if (milestoneSection != null) for (String milestoneId : milestoneSection.getKeys(false)) {
            if (!milestoneId.matches(ID)) throw new IllegalArgumentException("milestones." + milestoneId + ": invalid milestone id.");
            String path = "milestones." + milestoneId;
            ConfigurationSection milestone = section(milestoneSection, milestoneId, path);
            Map<String, Long> requires = new LinkedHashMap<>();
            ConfigurationSection requirement = milestone.getConfigurationSection("requires");
            if (requirement == null || requirement.getKeys(false).isEmpty())
                throw new IllegalArgumentException(path + ".requires: name at least one goal and its threshold.");
            for (String goal : requirement.getKeys(false)) {
                if (!requirement.isInt(goal) && !requirement.isLong(goal)) throw new IllegalArgumentException(path + ".requires." + goal + ": must be a number.");
                requires.put(goal, requirement.getLong(goal));
            }
            List<Reward> rewards = new ArrayList<>();
            List<?> rawRewards = milestone.getList("rewards", List.of());
            for (int index = 0; index < rawRewards.size(); index++)
                rewards.add(reward(map(rawRewards.get(index), path + ".rewards[" + (index + 1) + "]"), path + ".rewards[" + (index + 1) + "]"));
            milestones.put(milestoneId, new Milestone(milestoneId, text(milestone, "title", titleCase(milestoneId)),
                text(milestone, "description", ""), text(milestone, "icon", "nether_star").toLowerCase(Locale.ROOT), requires, rewards));
        }
        return new SeasonDefinition(id, text(yaml, "title", titleCase(id)), text(yaml, "description", ""), start, end,
            yaml.getLong("player-daily-cap", 2500), goals, milestones);
    }

    private static Source source(ConfigurationSection map, String path) {
        String rawType = text(map, "type", null);
        if (rawType == null) throw new IllegalArgumentException(path + ".type: missing (mine, smelt, craft, fish, kill, boss, expedition or contribute).");
        SourceType type;
        try {
            type = SourceType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + ".type: '" + rawType + "' is not mine, smelt, craft, fish, kill, boss, expedition or contribute.");
        }
        Set<String> filters = new HashSet<>();
        for (String key : List.of("materials", "mobs", "expeditions", "match"))
            for (String value : map.getStringList(key)) if (!value.equalsIgnoreCase("any")) filters.add(value.toLowerCase(Locale.ROOT));
        if (!map.isInt("points") && map.contains("points")) throw new IllegalArgumentException(path + ".points: must be a whole number.");
        long points = map.getLong("points", 1);
        if (points < 1 || points > 10_000) throw new IllegalArgumentException(path + ".points: must be 1-10000.");
        Map<String, Double> tiers = new LinkedHashMap<>();
        ConfigurationSection tierSection = map.getConfigurationSection("tiers");
        if (tierSection != null) for (String tier : tierSection.getKeys(false)) tiers.put(tier.toLowerCase(Locale.ROOT), tierSection.getDouble(tier));
        return new Source(type, filters, points, tiers);
    }

    private static Reward reward(ConfigurationSection map, String path) {
        String type = text(map, "type", "").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "buff" -> new Reward.Buff(required(map, "effect", path).toLowerCase(Locale.ROOT), map.getInt("amplifier", 0), duration(map, path));
            case "boost" -> {
                BoostKind kind;
                try {
                    kind = BoostKind.valueOf(required(map, "kind", path).toUpperCase(Locale.ROOT).replace('-', '_'));
                } catch (IllegalArgumentException unknown) {
                    throw new IllegalArgumentException(path + ".kind: use skill-xp or job-marks.");
                }
                yield new Reward.Boost(kind, map.getInt("percent", 25), duration(map, path));
            }
            case "item" -> new Reward.Item(required(map, "item", path).toLowerCase(Locale.ROOT), map.getInt("amount", 1));
            case "marks" -> new Reward.Marks(map.getLong("amount", 0));
            case "unlock" -> {
                String expedition = text(map, "expedition", null), feature = text(map, "feature", null);
                if ((expedition == null) == (feature == null)) throw new IllegalArgumentException(path + ": an unlock names one expedition or one feature.");
                yield new Reward.Unlock(expedition == null ? null : expedition.toLowerCase(Locale.ROOT), feature == null ? null : feature.toLowerCase(Locale.ROOT));
            }
            case "broadcast" -> new Reward.Broadcast(required(map, "message", path));
            case "command" -> new Reward.Command(required(map, "command", path));
            default -> throw new IllegalArgumentException(path + ".type: use buff, boost, item, marks, unlock, broadcast or command.");
        };
    }

    private static Duration duration(ConfigurationSection map, String path) {
        Duration duration = Duration.ofDays(map.getLong("days", 0)).plusHours(map.getLong("hours", 0)).plusMinutes(map.getLong("minutes", 0));
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException(path + ": give minutes, hours or days.");
        if (duration.compareTo(Duration.ofDays(30)) > 0) throw new IllegalArgumentException(path + ": timed rewards last at most 30 days.");
        return duration;
    }

    @SuppressWarnings("unchecked")
    private static ConfigurationSection map(Object raw, String path) {
        if (raw instanceof ConfigurationSection section) return section;
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + ": must be a map such as {type: mine, points: 1}.");
        MemoryConfiguration section = new MemoryConfiguration();
        ((Map<String, Object>) map).forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) section.createSection(key, nested);
            else section.set(key, value);
        });
        return section;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key, String path) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) throw new IllegalArgumentException(path + ": must be a section.");
        return section;
    }

    private static Instant instant(ConfigurationSection yaml, String key) {
        try {
            return Instant.parse(String.valueOf(yaml.get(key)));
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(key + ": use an ISO time such as 2026-11-01T00:00:00Z.");
        }
    }

    private static String required(ConfigurationSection map, String key, String path) {
        String value = text(map, key, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(path + "." + key + ": missing.");
        return value;
    }

    private static String text(ConfigurationSection section, String key, String fallback) {
        Object value = section.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    static String titleCase(String id) {
        StringBuilder title = new StringBuilder();
        for (String word : id.split("[-_]")) {
            if (word.isEmpty()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }
}
