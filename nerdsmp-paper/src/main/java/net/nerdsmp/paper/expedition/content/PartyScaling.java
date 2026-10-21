package net.nerdsmp.paper.expedition.content;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Sizes encounters to the party that actually showed up. Expeditions are authored for a full party;
 * a smaller group meets fewer, softer enemies. Hired allies count as part of a player each
 * ({@code allyWeight}), so they help without making the run trivial.
 *
 * <p>With the defaults, a solo player in a five-player expedition meets 52% of the authored mobs at
 * 60% health. A full party meets everything at full strength.
 */
public record PartyScaling(double countFloor, double healthFloor, double allyWeight) {
    public static final PartyScaling DEFAULT = new PartyScaling(0.4, 0.5, 0.6);

    public PartyScaling {
        if (countFloor < 0.1 || countFloor > 1 || healthFloor < 0.1 || healthFloor > 1 || allyWeight < 0 || allyWeight > 1)
            throw new IllegalArgumentException("Party scaling floors must be 0.1-1 and ally-weight 0-1.");
    }

    public static PartyScaling load(ConfigurationSection section) {
        if (section == null) return DEFAULT;
        return new PartyScaling(section.getDouble("count-floor", DEFAULT.countFloor),
            section.getDouble("health-floor", DEFAULT.healthFloor), section.getDouble("ally-weight", DEFAULT.allyWeight));
    }

    /** Players plus weighted allies, never above the expedition's full party. */
    public double effectiveSize(int players, int allies, int maxParty) {
        return Math.max(1, Math.min(maxParty, players + allies * allyWeight));
    }

    public double countFactor(double effectiveSize, int maxParty) {
        return factor(countFloor, effectiveSize, maxParty);
    }

    public double healthFactor(double effectiveSize, int maxParty) {
        return factor(healthFloor, effectiveSize, maxParty);
    }

    /** How many of an authored mob entry spawn: rounded, but never fewer than one. */
    public int scaledCount(int authored, double effectiveSize, int maxParty) {
        if (authored <= 0) return 0;
        return Math.max(1, (int) Math.round(authored * countFactor(effectiveSize, maxParty)));
    }

    private static double factor(double floor, double effectiveSize, int maxParty) {
        double share = Math.max(0, Math.min(1, effectiveSize / Math.max(1, maxParty)));
        return floor + (1 - floor) * share;
    }
}
