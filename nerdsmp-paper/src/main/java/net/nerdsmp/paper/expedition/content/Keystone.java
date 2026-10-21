package net.nerdsmp.paper.expedition.content;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Mythic keystones: a personal key to one expedition at a level. Each level makes the run tougher;
 * weekly affixes join at +4, +7 and +10. Beat the key's timer to upgrade it (by more for faster
 * clears); finish late, fail or abandon it and it drops a level. Deaths add time instead of
 * spending lives. Free of Bukkit so the rules are unit tested.
 */
public final class Keystone {
    public static final int MIN_LEVEL = 2;
    public static final int MAX_LEVEL = 30;
    /** Seconds each death adds to a keyed run's clock. */
    public static final int DEATH_PENALTY_SECONDS = 10;
    /** Keyed runs have no practical life limit; time is the only pressure. */
    public static final int KEY_LIVES = 50;
    /** The second weekly affix, rotating one step each week from +7. */
    static final List<Modifier> ROTATION = List.of(Modifier.BOLSTERING, Modifier.RAGING, Modifier.SANGUINE, Modifier.VOLATILE);
    /** Weeks count from this Monday, so the schedule is the same on every server. */
    static final LocalDate EPOCH = LocalDate.of(2026, 1, 5);

    private Keystone() { }

    /** A player's key. */
    public record Key(String expedition, int level) {
        public Key {
            level = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        }
    }

    /** Health and damage over plain Mythic: 8% per level above +2, compounding. */
    public static double scale(int level) {
        return Math.pow(1.08, Math.max(0, level - MIN_LEVEL));
    }

    /** The week number of {@code date}, counted from {@link #EPOCH}. */
    public static long week(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return Math.floorDiv(ChronoUnit.DAYS.between(EPOCH, monday), 7);
    }

    /**
     * This week's affixes at a key level: from +4 Fortified or Tyrannical (alternating weekly), from
     * +7 one of Bolstering, Raging, Sanguine or Volatile, and from +10 both Fortified and Tyrannical.
     */
    public static List<Modifier> affixes(int level, LocalDate date) {
        long week = week(date);
        List<Modifier> affixes = new ArrayList<>();
        if (level >= 10) {
            affixes.add(Modifier.FORTIFIED);
            affixes.add(Modifier.TYRANNICAL);
        } else if (level >= 4) affixes.add(Math.floorMod(week, 2) == 0 ? Modifier.FORTIFIED : Modifier.TYRANNICAL);
        if (level >= 7) affixes.add(ROTATION.get(Math.floorMod(week, ROTATION.size())));
        return List.copyOf(affixes);
    }

    /** The key's timer: 60% of the expedition's time limit (the same as the Timed par). */
    public static long timerSeconds(int timeLimitMinutes) {
        return Modifier.parSeconds(timeLimitMinutes);
    }

    /**
     * Levels the key moves after a finish: +3 within 60% of the timer, +2 within 80%, +1 within the
     * timer, and -1 when it ran over.
     */
    public static int change(long seconds, long timer) {
        if (seconds > timer) return -1;
        if (seconds <= timer * .6) return 3;
        if (seconds <= timer * .8) return 2;
        return 1;
    }

    /**
     * The tier a keyed run uses: the expedition's Mythic, scaled for the level, with lives replaced by
     * time penalties, extra loot rolls at +5 and +10, more Marks per level, and this week's affixes
     * in place of Timed (the key has its own timer).
     */
    public static Tier tier(Tier mythic, int level, List<Modifier> affixes) {
        double scale = scale(level);
        Set<String> modifiers = new java.util.LinkedHashSet<>();
        for (String modifier : mythic.modifiers()) if (!modifier.equals(Modifier.TIMED.key())) modifiers.add(modifier);
        for (Modifier affix : affixes) modifiers.add(affix.key());
        int rolls = mythic.lootRolls() + (level >= 5 ? 1 : 0) + (level >= 10 ? 1 : 0);
        return new Tier("keystone", "Mythic +" + level, mythic.levelBonus() + 2 * (level - MIN_LEVEL), mythic.health() * scale,
            mythic.damage() * scale, KEY_LIVES, rolls, mythic.marks() * (1 + .1 * level), mythic.campaign(), null, List.copyOf(modifiers));
    }

    /** The keystone after a run: upgraded, depleted, or unchanged at the floor. */
    public static Key after(Key key, int change, String nextExpedition) {
        return new Key(nextExpedition, key.level() + change);
    }

    /** Affix names for menus and chat. */
    public static String describe(List<Modifier> affixes) {
        if (affixes.isEmpty()) return "none";
        return String.join(", ", affixes.stream().map(Modifier::title).toList());
    }

    /** Every affix that can appear on a key, for docs and validation. */
    public static Set<Modifier> pool() {
        Set<Modifier> pool = EnumSet.of(Modifier.FORTIFIED, Modifier.TYRANNICAL);
        pool.addAll(ROTATION);
        return pool;
    }
}
