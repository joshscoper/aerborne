package net.nerdsmp.paper.campaign.season;

import net.nerdsmp.paper.campaign.season.SeasonDefinition.BoostKind;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Milestone;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Season progress, free of Bukkit events: goal counters, each player's daily and season
 * contributions, reached milestones, timed server-wide buffs and claim receipts. Reached milestones
 * stay reached for the whole season, even if staff later raise a threshold.
 */
public final class SeasonLedger {
    /** A running timed reward. {@code key} groups rewards that extend each other instead of stacking. */
    public record ActiveBuff(String key, String label, String effect, int amplifier, BoostKind boost, int percent, Instant endsAt) {
        public boolean potion() { return effect != null; }
    }

    private final Map<String, Long> goals = new LinkedHashMap<>();
    private final Map<String, Map<String, Long>> players = new HashMap<>();
    private final Map<String, Long> daily = new HashMap<>();
    private LocalDate day;
    private final Map<String, Instant> reached = new LinkedHashMap<>();
    private final Map<String, ActiveBuff> buffs = new LinkedHashMap<>();
    private final Set<String> claims = new HashSet<>();
    private final Map<String, String> names = new HashMap<>();
    private Instant firstLoaded;

    /** Remembers a contributor's last known name for leaderboards. */
    public synchronized void name(String account, String name) { names.put(account, name); }
    public synchronized Optional<String> name(String account) { return Optional.ofNullable(names.get(account)); }

    public synchronized long points(String goal) { return goals.getOrDefault(goal, 0L); }
    public synchronized Map<String, Long> points() { return Map.copyOf(goals); }
    public synchronized long total() { return goals.values().stream().mapToLong(Long::longValue).sum(); }

    public synchronized long contributed(String account, String goal) {
        return players.getOrDefault(account, Map.of()).getOrDefault(goal, 0L);
    }

    public synchronized Map<String, Long> contributions(String account) { return Map.copyOf(players.getOrDefault(account, Map.of())); }

    /** Top contributors to one goal (or every goal when {@code goal} is null), highest first. */
    public synchronized List<Map.Entry<String, Long>> top(String goal, int limit) {
        List<Map.Entry<String, Long>> rows = new ArrayList<>();
        players.forEach((account, values) -> {
            long amount = goal == null ? values.values().stream().mapToLong(Long::longValue).sum() : values.getOrDefault(goal, 0L);
            if (amount > 0) rows.add(Map.entry(account, amount));
        });
        rows.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return rows.subList(0, Math.min(limit, rows.size()));
    }

    public synchronized long today(String account, String goal, LocalDate date) {
        rollDay(date);
        return daily.getOrDefault(account + "|" + goal, 0L);
    }

    /**
     * Adds a player's points to a goal, clamped to what is left of their daily cap for it.
     * Returns the points that counted (0 when capped).
     */
    public synchronized long add(String account, String goal, long points, LocalDate date, long dailyCap) {
        if (points <= 0) return 0;
        rollDay(date);
        String key = account + "|" + goal;
        long used = daily.getOrDefault(key, 0L);
        long applied = Math.min(points, Math.max(0, dailyCap - used));
        if (applied == 0) return 0;
        daily.put(key, used + applied);
        goals.merge(goal, applied, Math::addExact);
        players.computeIfAbsent(account, ignored -> new HashMap<>()).merge(goal, applied, Math::addExact);
        return applied;
    }

    /** Staff adjustment: no cap and no player credit. Negative values lower the counter, never below zero. */
    public synchronized void adjust(String goal, long points) {
        goals.put(goal, Math.max(0, Math.addExact(goals.getOrDefault(goal, 0L), points)));
    }

    public synchronized boolean reached(String milestone) { return reached.containsKey(milestone); }
    public synchronized Map<String, Instant> reachedMilestones() { return Map.copyOf(reached); }

    public synchronized boolean met(Milestone milestone) {
        return milestone.requires().entrySet().stream().allMatch(entry -> goals.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    /** Marks newly met milestones reached, starts their timed rewards, and returns them in file order. */
    public synchronized List<Milestone> evaluate(SeasonDefinition season, Instant now) {
        List<Milestone> fresh = new ArrayList<>();
        for (Milestone milestone : season.milestones().values()) {
            if (reached.containsKey(milestone.id()) || !met(milestone)) continue;
            reached.put(milestone.id(), now);
            fresh.add(milestone);
            for (Reward reward : milestone.rewards()) startTimed(reward, milestone.title(), now);
        }
        return fresh;
    }

    /** Starts a timed reward; the same effect or boost kind extends the running one rather than stacking. */
    public synchronized void startTimed(Reward reward, String source, Instant now) {
        String key;
        ActiveBuff next;
        if (reward instanceof Reward.Buff buff) {
            key = "effect:" + buff.effect();
            ActiveBuff current = active(key, now);
            Instant base = current == null ? now : current.endsAt();
            int amplifier = current == null ? buff.amplifier() : Math.max(current.amplifier(), buff.amplifier());
            next = new ActiveBuff(key, source, buff.effect(), amplifier, null, 0, base.plus(buff.duration()));
        } else if (reward instanceof Reward.Boost boost) {
            key = "boost:" + boost.kind().key();
            ActiveBuff current = active(key, now);
            Instant base = current == null ? now : current.endsAt();
            int percent = current == null ? boost.percent() : Math.max(current.percent(), boost.percent());
            next = new ActiveBuff(key, source, null, 0, boost.kind(), percent, base.plus(boost.duration()));
        } else return;
        buffs.put(key, next);
    }

    private ActiveBuff active(String key, Instant now) {
        ActiveBuff buff = buffs.get(key);
        return buff != null && buff.endsAt().isAfter(now) ? buff : null;
    }

    public synchronized List<ActiveBuff> activeBuffs(Instant now) {
        buffs.values().removeIf(buff -> !buff.endsAt().isAfter(now));
        return List.copyOf(buffs.values());
    }

    public synchronized int boostPercent(BoostKind kind, Instant now) {
        ActiveBuff buff = active("boost:" + kind.key(), now);
        return buff == null ? 0 : buff.percent();
    }

    public synchronized boolean claimed(String account, String milestone) { return claims.contains(account + "|" + milestone); }

    /** Reached milestones with item or Marks rewards this account has not claimed yet. */
    public synchronized List<Milestone> claimable(SeasonDefinition season, String account) {
        return season.milestones().values().stream()
            .filter(milestone -> reached.containsKey(milestone.id()) && milestone.claimable() && !claims.contains(account + "|" + milestone.id())).toList();
    }

    /** Records a claim; false when it was already claimed (so delivery must not run twice). */
    public synchronized boolean claim(String account, String milestone) { return claims.add(account + "|" + milestone); }

    public synchronized void unclaim(String account, String milestone) { claims.remove(account + "|" + milestone); }

    public synchronized Instant firstLoaded(Instant fallback) {
        if (firstLoaded == null) firstLoaded = fallback;
        return firstLoaded;
    }

    private void rollDay(LocalDate date) {
        if (!date.equals(day)) {
            daily.clear();
            day = date;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    public synchronized YamlConfiguration save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (firstLoaded != null) yaml.set("first-loaded", firstLoaded.toString());
        goals.forEach((goal, points) -> yaml.set("goals." + goal, points));
        players.forEach((account, values) -> values.forEach((goal, points) -> yaml.set("players." + account + "." + goal, points)));
        if (day != null) {
            yaml.set("day", day.toString());
            daily.forEach((key, points) -> {
                String[] parts = key.split("\\|", 2);
                yaml.set("daily." + parts[0] + "." + parts[1], points);
            });
        }
        reached.forEach((milestone, at) -> yaml.set("reached." + milestone, at.toString()));
        buffs.forEach((key, buff) -> {
            String path = "buffs." + key.replace(':', '_');
            yaml.set(path + ".key", key);
            yaml.set(path + ".label", buff.label());
            if (buff.effect() != null) {
                yaml.set(path + ".effect", buff.effect());
                yaml.set(path + ".amplifier", buff.amplifier());
            } else {
                yaml.set(path + ".boost", buff.boost().name());
                yaml.set(path + ".percent", buff.percent());
            }
            yaml.set(path + ".ends-at", buff.endsAt().toString());
        });
        yaml.set("claims", claims.stream().sorted().toList());
        names.forEach((account, name) -> yaml.set("names." + account, name));
        return yaml;
    }

    public static SeasonLedger load(ConfigurationSection yaml) {
        SeasonLedger ledger = new SeasonLedger();
        if (yaml.contains("first-loaded")) ledger.firstLoaded = Instant.parse(yaml.getString("first-loaded"));
        section(yaml, "goals").ifPresent(goals -> goals.getKeys(false).forEach(goal -> ledger.goals.put(goal, nonNegative(goals.getLong(goal)))));
        section(yaml, "players").ifPresent(players -> players.getKeys(false).forEach(account -> {
            ConfigurationSection values = players.getConfigurationSection(account);
            if (values != null) values.getKeys(false).forEach(goal ->
                ledger.players.computeIfAbsent(account, ignored -> new HashMap<>()).put(goal, nonNegative(values.getLong(goal))));
        }));
        if (yaml.contains("day")) {
            ledger.day = LocalDate.parse(yaml.getString("day"));
            section(yaml, "daily").ifPresent(daily -> daily.getKeys(false).forEach(account -> {
                ConfigurationSection values = daily.getConfigurationSection(account);
                if (values != null) values.getKeys(false).forEach(goal -> ledger.daily.put(account + "|" + goal, nonNegative(values.getLong(goal))));
            }));
        }
        section(yaml, "reached").ifPresent(reached -> reached.getKeys(false).forEach(milestone ->
            ledger.reached.put(milestone, Instant.parse(reached.getString(milestone)))));
        section(yaml, "buffs").ifPresent(buffs -> buffs.getKeys(false).forEach(node -> {
            ConfigurationSection buff = buffs.getConfigurationSection(node);
            if (buff == null) return;
            String key = buff.getString("key", node);
            BoostKind boost = buff.contains("boost") ? BoostKind.valueOf(buff.getString("boost")) : null;
            ledger.buffs.put(key, new ActiveBuff(key, buff.getString("label", ""), buff.getString("effect"), buff.getInt("amplifier"),
                boost, buff.getInt("percent"), Instant.parse(buff.getString("ends-at"))));
        }));
        ledger.claims.addAll(yaml.getStringList("claims"));
        section(yaml, "names").ifPresent(names -> names.getKeys(false).forEach(account -> ledger.names.put(account, names.getString(account))));
        return ledger;
    }

    private static Optional<ConfigurationSection> section(ConfigurationSection yaml, String key) {
        return Optional.ofNullable(yaml.getConfigurationSection(key));
    }

    private static long nonNegative(long value) {
        if (value < 0) throw new IllegalStateException("Negative campaign points in the saved state");
        return value;
    }

    /** Time left on a buff, for menus. */
    public static Duration remaining(ActiveBuff buff, Instant now) { return Duration.between(now, buff.endsAt()).isNegative() ? Duration.ZERO : Duration.between(now, buff.endsAt()); }
}
