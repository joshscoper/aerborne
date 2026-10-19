package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Seasonal co-op progression. Pending work is bounded and flushed once per ten seconds. */
public final class IslandDevelopment implements AutoCloseable {
    public static final int MAX_LEVEL = 50, DAILY_XP_CAP = 10_000, MAX_AWARD = 25;
    private final PlayerDataStore data;
    private final IslandService islands;
    private final String path;
    private final Supplier<LocalDate> today;
    private final Map<AccountId, Progress> saved = new HashMap<>(), pending = new HashMap<>();

    public IslandDevelopment(PlayerDataStore data, IslandService islands, String season, Supplier<LocalDate> today) {
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid season");
        this.data = data; this.islands = islands; this.today = today;
        path = "seasons." + season + ".island-development";
        // Validate saved data at startup, not halfway through a player's first upgrade.
        data.knownAccounts().stream().filter(a -> islands.owned(a).isPresent()).forEach(this::progress);
    }
    public synchronized Progress progress(AccountId owner) {
        requireIsland(owner);
        return saved.computeIfAbsent(owner, this::load);
    }
    /** Called only for a successfully awarded, non-replayed skill activity on this island. */
    public synchronized void record(AccountId actor, AccountId owner, long baseXp) {
        if (baseXp <= 0 || !requireIsland(owner).member(actor)) return;
        Progress current = pending.getOrDefault(owner, progress(owner));
        LocalDate day = today.get();
        int earned = current.day().equals(day) ? current.earnedToday() : 0;
        long amount = Math.min(Math.min(baseXp, MAX_AWARD), Math.min(DAILY_XP_CAP - earned, experienceForLevel(MAX_LEVEL) - current.xp()));
        if (amount <= 0) return;
        pending.put(owner, new Progress(current.xp() + amount, day, earned + (int) amount, current.talents()));
    }
    public synchronized void learn(AccountId actor, AccountId owner, IslandTalent talent, int expectedRank) {
        if (!requireIsland(owner).owner().equals(actor)) throw new IllegalArgumentException("Only the island owner can spend its talent points.");
        flush(owner);
        Progress before = progress(owner);
        if (before.rank(talent) != expectedRank) throw new IllegalArgumentException("This talent changed. Open the tree again.");
        if (expectedRank >= IslandTalent.MAX_RANK) throw new IllegalArgumentException("That talent is fully trained.");
        if (before.availablePoints() <= 0) throw new IllegalArgumentException("Earn a talent point every two island levels.");
        if (before.level() < talent.requiredLevel()) throw new IllegalArgumentException("Reach island level " + talent.requiredLevel() + " first.");
        if (talent.prerequisite() != null && before.rank(talent.prerequisite()) < 2)
            throw new IllegalArgumentException("Train " + talent.prerequisite().title() + " to rank 2 first.");
        Map<IslandTalent, Integer> ranks = new EnumMap<>(IslandTalent.class); ranks.putAll(before.talents());
        ranks.put(talent, expectedRank + 1);
        commit(owner, new Progress(before.xp(), before.day(), before.earnedToday(), ranks));
    }
    public synchronized void flush() {
        RuntimeException failure = null;
        for (AccountId owner : List.copyOf(pending.keySet())) {
            try { flush(owner); } catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }
    public synchronized void flush(AccountId owner) {
        Progress next = pending.get(owner);
        if (next != null) { commit(owner, next); pending.remove(owner); }
    }
    private IslandService.Island requireIsland(AccountId owner) {
        return islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
    }
    private void commit(AccountId owner, Progress next) {
        data.update(owner, yaml -> {
            yaml.set(path, null); yaml.set(path + ".schema", 1); yaml.set(path + ".xp", next.xp());
            yaml.set(path + ".day", next.day().toString()); yaml.set(path + ".earned-today", next.earnedToday());
            next.talents().forEach((talent, rank) -> yaml.set(path + ".talents." + talent.id(), rank));
        });
        saved.put(owner, next);
    }
    private Progress load(AccountId owner) {
        return data.read(owner, yaml -> {
            if (!yaml.contains(path)) return new Progress(0, today.get(), 0, Map.of());
            try {
                ConfigurationSection section = yaml.getConfigurationSection(path);
                if (section == null || section.getInt("schema") != 1 || !section.isInt("earned-today")) throw new IllegalArgumentException("Invalid schema");
                Object rawXp = section.get("xp");
                if (!(rawXp instanceof Long) && !(rawXp instanceof Integer)) throw new IllegalArgumentException("Invalid XP");
                Map<IslandTalent, Integer> ranks = new EnumMap<>(IslandTalent.class);
                if (section.contains("talents")) {
                    ConfigurationSection talents = section.getConfigurationSection("talents");
                    if (talents == null) throw new IllegalArgumentException("Invalid talents");
                    for (String key : talents.getKeys(false)) {
                        if (!talents.isInt(key)) throw new IllegalArgumentException("Invalid rank");
                        ranks.put(IslandTalent.parse(key), talents.getInt(key));
                    }
                }
                return new Progress(((Number) rawXp).longValue(), LocalDate.parse(section.getString("day", "")), section.getInt("earned-today"), ranks);
            } catch (RuntimeException error) { throw new IllegalStateException("Invalid island development for " + owner + "; refusing to replace it", error); }
        });
    }
    public static long experienceForLevel(int level) {
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("Invalid island level");
        return 200L * (level - 1) + 50L * (level - 1) * (level - 2);
    }
    public static int level(long xp) {
        int result = 1;
        while (result < MAX_LEVEL && xp >= experienceForLevel(result + 1)) result++;
        return result;
    }
    @Override public void close() { flush(); }
    public record Progress(long xp, LocalDate day, int earnedToday, Map<IslandTalent, Integer> talents) {
        public Progress {
            Objects.requireNonNull(day); talents = Map.copyOf(talents);
            if (xp < 0 || xp > experienceForLevel(MAX_LEVEL) || earnedToday < 0 || earnedToday > DAILY_XP_CAP) throw new IllegalArgumentException("Invalid island XP");
            int spent = 0;
            for (var entry : talents.entrySet()) {
                if (entry.getValue() < 1 || entry.getValue() > IslandTalent.MAX_RANK) throw new IllegalArgumentException("Invalid talent rank");
                if (entry.getKey().prerequisite() != null && talents.getOrDefault(entry.getKey().prerequisite(), 0) < 2) throw new IllegalArgumentException("Missing talent prerequisite");
                spent += entry.getValue();
            }
            if (spent > earnedPoints(IslandDevelopment.level(xp))) throw new IllegalArgumentException("Overspent talent points");
        }
        public int level() { return IslandDevelopment.level(xp); }
        public int rank(IslandTalent talent) { return talents.getOrDefault(talent, 0); }
        public int availablePoints() { return earnedPoints(level()) - talents.values().stream().mapToInt(Integer::intValue).sum(); }
        public int stackLimit() { return switch (rank(IslandTalent.STACKING)) { case 1 -> 4; case 2 -> 8; case 3 -> 16; default -> 1; }; }
        public int hopperLimit() { return (switch (rank(IslandTalent.LOGISTICS)) { case 1 -> 2; case 2 -> 4; case 3 -> 8; default -> 0; }) + 2 * rank(IslandTalent.STORAGE_NETWORK); }
        public int hopperBatch() { return (8 << rank(IslandTalent.LOGISTICS)) + 16 * rank(IslandTalent.STORAGE_NETWORK); }
        public int machineCycleSeconds(int baseSeconds) { return Math.max(1, (int) Math.ceil(baseSeconds * (1 - .10 * rank(IslandTalent.WORKSHOP)))); }
        public double skillBonus(net.nerdsmp.api.model.Skill skill) { return switch (skill) {
            case MINING, SMITHING -> 8 * rank(IslandTalent.MINING_GUILD);
            case FISHING, COOKING -> 8 * rank(IslandTalent.ANGLING);
            default -> 0;
        }; }
        public String progressLabel() { return level() == MAX_LEVEL ? "Maximum island level" : (xp - experienceForLevel(level())) + " / " + (experienceForLevel(level() + 1) - experienceForLevel(level())) + " island XP"; }
    }
    public static int earnedPoints(int level) { return Math.clamp(level, 1, MAX_LEVEL) / 2; }
}
