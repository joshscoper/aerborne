package net.nerdsmp.paper.expedition.content;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hired allies: server-controlled NPCs that fill empty party slots once a queue has waited long
 * enough. They are labelled NPCs, never players. A run with allies is "assisted": it still pays
 * loot and Marks, but counts toward neither the season campaign nor the expedition records.
 */
public record Allies(boolean enabled, Duration offerAfter, int max, double damageMultiplier, List<Role> roles) {
    public static final Allies DEFAULT = new Allies(true, Duration.ofSeconds(120), 4, 1.5,
        List.of(Role.BULWARK, Role.BLADE, Role.HEDGE_WITCH, Role.ARCHER));

    public Allies {
        roles = List.copyOf(roles);
        if (max < 0 || max > ExpeditionDefinition.MAX_PARTY - 1) throw new IllegalArgumentException("allies.max must be 0-4.");
        if (damageMultiplier <= 0 || damageMultiplier > 10) throw new IllegalArgumentException("allies.damage-multiplier must be above 0 and at most 10.");
        if (roles.isEmpty()) throw new IllegalArgumentException("allies.roles needs at least one role.");
    }

    public enum Role {
        /** Iron golem: slow, sturdy, draws enemies off the party. */
        BULWARK("Bulwark", "Tank", 100),
        /** Vindicator with an axe: steady melee damage. */
        BLADE("Blade", "Fighter", 40),
        /** Skeleton archer: damage from range. */
        ARCHER("Archer", "Ranged", 30),
        /** Witch: throws harming potions at enemies and mends the most hurt player nearby. */
        HEDGE_WITCH("Hedge-witch", "Healer", 32);

        private final String title;
        private final String job;
        private final double health;

        Role(String title, String job, double health) {
            this.title = title;
            this.job = job;
            this.health = health;
        }

        public String title() { return title; }
        public String job() { return job; }
        public double health() { return health; }
        public String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }

        public static Role parse(String raw) {
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Unknown ally role '" + raw + "'. Use bulwark, blade, archer or hedge-witch.");
            }
        }
    }

    public static Allies load(ConfigurationSection section) {
        if (section == null) return DEFAULT;
        List<Role> roles = new ArrayList<>();
        for (String raw : section.getStringList("roles")) roles.add(Role.parse(raw));
        return new Allies(section.getBoolean("enabled", DEFAULT.enabled),
            Duration.ofSeconds(Math.max(0, section.getLong("offer-after-seconds", DEFAULT.offerAfter.toSeconds()))),
            section.getInt("max", DEFAULT.max), section.getDouble("damage-multiplier", DEFAULT.damageMultiplier),
            roles.isEmpty() ? DEFAULT.roles : roles);
    }

    /** Empty slots allies may fill for a group of this size. */
    public int slots(int players, int maxParty) {
        if (!enabled) return 0;
        return Math.max(0, Math.min(max, maxParty - players));
    }

    /** Whether a ticket queued at {@code queuedAt} has waited long enough to be offered allies. */
    public boolean offerable(Instant queuedAt, Instant now, int players, int maxParty) {
        return slots(players, maxParty) > 0 && !queuedAt.plus(offerAfter).isAfter(now);
    }

    /** The roles hired for {@code count} slots, taken in the configured order and repeating if needed. */
    public List<Role> pick(int count) {
        List<Role> picked = new ArrayList<>();
        for (int index = 0; index < count; index++) picked.add(roles.get(index % roles.size()));
        return List.copyOf(picked);
    }
}
