package net.nerdsmp.paper.expedition.content;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Optional affixes that change how a run plays. Expeditions and tiers list them by key. */
public enum Modifier {
    FORTIFIED("Fortified", "Regular enemies have 30% more health and hit 15% harder."),
    TYRANNICAL("Tyrannical", "Bosses have 30% more health and hit 15% harder."),
    BOLSTERING("Bolstering", "When an enemy dies, nearby enemies grow stronger."),
    VOLATILE("Volatile", "Enemies burst shortly after they die. Step away from the bodies."),
    RAGING("Raging", "Regular enemies hit 50% harder below 30% health. Finish them fast."),
    SANGUINE("Sanguine", "Fallen enemies leave a pool that heals other enemies and hurts players. Pull fights away from it."),
    TIMED("Timed", "Finish within the par time for an extra loot roll.");

    private final String title;
    private final String description;

    Modifier(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() { return title; }
    public String description() { return description; }
    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<Modifier> parse(String raw) {
        try {
            return Optional.of(valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return Optional.empty();
        }
    }

    public static Set<Modifier> parseAll(Collection<String> keys) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        for (String key : keys) parse(key).ifPresent(modifiers::add);
        return modifiers;
    }

    public static double healthMultiplier(Set<Modifier> active, boolean boss) {
        return active.contains(boss ? TYRANNICAL : FORTIFIED) ? 1.3 : 1.0;
    }

    public static double damageMultiplier(Set<Modifier> active, boolean boss) {
        return active.contains(boss ? TYRANNICAL : FORTIFIED) ? 1.15 : 1.0;
    }

    /** Par time for the timed bonus: 60% of the limit, never under one minute. */
    public static long parSeconds(int timeLimitMinutes) {
        return Math.max(60, Math.round(timeLimitMinutes * 60 * .6));
    }
}
