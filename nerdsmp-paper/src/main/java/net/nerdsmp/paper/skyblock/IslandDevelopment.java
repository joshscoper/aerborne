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
    private java.util.function.DoubleSupplier xpMultiplier = () -> 1D;
    private LevelListener levelListener = (owner, from, to) -> { };

    /** Told when an island's saved level rises, after the write: {@code from} and {@code to} are levels. */
    @FunctionalInterface public interface LevelListener { void leveled(AccountId owner, int from, int to); }

    public IslandDevelopment(PlayerDataStore data, IslandService islands, String season, Supplier<LocalDate> today) {
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid season");
        this.data = data; this.islands = islands; this.today = today;
        path = "seasons." + season + ".island-development";
        // Validate saved data at startup, not halfway through a player's first upgrade.
        data.knownAccounts().stream().filter(a -> islands.owned(a).isPresent()).forEach(this::progress);
    }
    /** Island XP is raised like skill XP: the double XP weekend and XP boosters. The daily cap counts unraised XP. */
    public synchronized void xpMultiplier(java.util.function.DoubleSupplier multiplier) { xpMultiplier = Objects.requireNonNull(multiplier); }
    public synchronized void onLevelUp(LevelListener listener) { levelListener = Objects.requireNonNull(listener); }

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
        long counted = Math.min(Math.min(baseXp, MAX_AWARD), DAILY_XP_CAP - earned);
        if (counted <= 0) return;
        double multiplier = Math.max(1D, xpMultiplier.getAsDouble());
        long raw = Math.round(counted * multiplier);
        long amount = Math.min(raw, experienceForLevel(MAX_LEVEL) - current.xp());
        // Past level 50 the XP isn't wasted: it becomes prestige.
        if (raw > amount) prestigePending.merge(owner, raw - Math.max(0, amount), Long::sum);
        pending.put(owner, new Progress(current.xp() + Math.max(0, amount), day, earned + (int) counted, current.talents()));
    }
    /**
     * Island XP paid as a reward (an island visitor served), outside the daily cap for work. Raised by the double XP
     * weekend and boosters like work is; at most {@link #MAX_GRANT} at a time.
     */
    public synchronized void grant(AccountId owner, long xp) {
        if (xp <= 0) return;
        Progress current = pending.getOrDefault(owner, progress(owner));
        long raw = Math.round(Math.min(xp, MAX_GRANT) * Math.max(1D, xpMultiplier.getAsDouble()));
        long amount = Math.min(raw, experienceForLevel(MAX_LEVEL) - current.xp());
        if (raw > amount) prestigePending.merge(owner, raw - Math.max(0, amount), Long::sum);
        if (amount <= 0) return;
        pending.put(owner, new Progress(current.xp() + amount, current.day(), current.earnedToday(), current.talents()));
    }
    public static final long MAX_GRANT = 2_000;

    // ------------------------------------------------------------------ prestige

    /** Island XP earned past level 50 for each star, and the most stars an island can hold. */
    public static final long XP_PER_STAR = 25_000;
    public static final int MAX_STARS = 10;
    private final Map<AccountId, Long> prestigeSaved = new HashMap<>(), prestigePending = new HashMap<>();
    private String prestigePath() { return path + "-prestige.xp"; }

    /** Island XP earned past level 50 this season, saved and pending. */
    public synchronized long prestigeXp(AccountId owner) {
        long saved = prestigeSaved.computeIfAbsent(owner, o -> data.read(o, yaml -> Math.max(0L, yaml.getLong(prestigePath(), 0L))));
        return saved + prestigePending.getOrDefault(owner, 0L);
    }
    public synchronized int stars(AccountId owner) { return stars(prestigeXp(owner)); }
    public static int stars(long prestigeXp) { return (int) Math.min(MAX_STARS, Math.max(0, prestigeXp) / XP_PER_STAR); }

    /** All the island's skill XP bonus on this island: talents, level rewards, and +1% per prestige star. */
    public synchronized double skillBonus(AccountId owner, net.nerdsmp.api.model.Skill skill) {
        return progress(owner).skillBonus(skill) + stars(owner);
    }

    public synchronized void learn(AccountId actor, AccountId owner, IslandTalent talent, int expectedRank) {
        if (!requireIsland(owner).owner().equals(actor)) throw new IllegalArgumentException("Only the island owner can spend its talent points.");
        flush(owner);
        Progress before = progress(owner);
        if (before.rank(talent) != expectedRank) throw new IllegalArgumentException("This talent changed. Open the tree again.");
        if (expectedRank >= IslandTalent.MAX_RANK) throw new IllegalArgumentException("That talent is fully trained.");
        if (before.availablePoints() <= 0) throw new IllegalArgumentException("Earn a talent point every two island levels.");
        if (before.level() < talent.requiredLevel()) throw new IllegalArgumentException("Reach island level " + talent.requiredLevel() + " first.");
        if (!talent.requirementsMet(before.talents()))
            throw new IllegalArgumentException("Train a connected prerequisite to rank 2 first.");
        Map<IslandTalent, Integer> ranks = new EnumMap<>(IslandTalent.class); ranks.putAll(before.talents());
        ranks.put(talent, expectedRank + 1);
        commit(owner, new Progress(before.xp(), before.day(), before.earnedToday(), ranks));
    }
    public synchronized void flush() {
        RuntimeException failure = null;
        Set<AccountId> owners = new java.util.LinkedHashSet<>(pending.keySet());
        owners.addAll(prestigePending.keySet());
        for (AccountId owner : owners) {
            try { flush(owner); } catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }
    public synchronized void flush(AccountId owner) {
        Progress next = pending.get(owner);
        if (next != null) { commit(owner, next); pending.remove(owner); }
        Long extra = prestigePending.get(owner);
        if (extra != null && extra > 0) {
            int before = stars(owner);
            long total = prestigeXp(owner);
            data.update(owner, yaml -> yaml.set(prestigePath(), total));
            prestigeSaved.put(owner, total);
            prestigePending.remove(owner);
            int after = stars(total);
            if (after > before) {
                try { levelListener.leveled(owner, MAX_LEVEL + before, MAX_LEVEL + after); }
                catch (RuntimeException ignored) { /* a broken notice must not undo saved XP */ }
            }
        } else prestigePending.remove(owner);
    }
    private IslandService.Island requireIsland(AccountId owner) {
        return islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
    }
    private void commit(AccountId owner, Progress next) {
        Progress before = saved.get(owner);
        data.update(owner, yaml -> {
            yaml.set(path, null); yaml.set(path + ".schema", 1); yaml.set(path + ".xp", next.xp());
            yaml.set(path + ".day", next.day().toString()); yaml.set(path + ".earned-today", next.earnedToday());
            next.talents().forEach((talent, rank) -> yaml.set(path + ".talents." + talent.id(), rank));
        });
        saved.put(owner, next);
        if (before != null && next.level() > before.level()) {
            try { levelListener.leveled(owner, before.level(), next.level()); }
            catch (RuntimeException ignored) { /* a broken notice must not undo saved XP */ }
        }
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
                if (!entry.getKey().requirementsMet(talents)) throw new IllegalArgumentException("Missing talent prerequisite");
                spent += entry.getValue();
            }
            if (spent > earnedPoints(IslandDevelopment.level(xp))) throw new IllegalArgumentException("Overspent talent points");
        }
        public int level() { return IslandDevelopment.level(xp); }
        public int rank(IslandTalent talent) { return talents.getOrDefault(talent, 0); }
        public int availablePoints() { return earnedPoints(level()) - talents.values().stream().mapToInt(Integer::intValue).sum(); }
        public int stackLimit() { return switch (rank(IslandTalent.STACKING)) { case 1 -> 4; case 2 -> 8; case 3 -> 16; default -> 1; }; }
        public int hopperLimit() { return (switch (rank(IslandTalent.LOGISTICS)) { case 1 -> 2; case 2 -> 4; case 3 -> 8; default -> 0; }) + 2 * rank(IslandTalent.STORAGE_NETWORK) + IslandMilestone.extraHoppers(level()); }
        public int hopperBatch() { return (8 << rank(IslandTalent.LOGISTICS)) + 16 * rank(IslandTalent.STORAGE_NETWORK) + IslandMilestone.extraHopperBatch(level()); }
        public int machineCycleSeconds(int baseSeconds) { return Math.max(1, (int) Math.ceil(baseSeconds * (1 - .10 * rank(IslandTalent.WORKSHOP)))); }
        public double skillBonus(net.nerdsmp.api.model.Skill skill) { return IslandMilestone.skillXpPercent(level()) + switch (skill) {
            case MINING, SMITHING -> 8 * rank(IslandTalent.MINING_GUILD);
            case FISHING, COOKING -> 8 * rank(IslandTalent.ANGLING);
            default -> 0;
        }; }
        /** How far through the current level, 0 to 1; 1 at the maximum level. */
        public double levelFraction() {
            if (level() == MAX_LEVEL) return 1D;
            long from = experienceForLevel(level()), to = experienceForLevel(level() + 1);
            return Math.clamp((xp - from) / (double) (to - from), 0D, 1D);
        }
        public String progressLabel() { return level() == MAX_LEVEL ? "Maximum island level" : (xp - experienceForLevel(level())) + " / " + (experienceForLevel(level() + 1) - experienceForLevel(level())) + " island XP"; }
    }
    public static int earnedPoints(int level) { return Math.clamp(level, 1, MAX_LEVEL) / 2; }
}
