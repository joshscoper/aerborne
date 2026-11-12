package net.nerdsmp.paper.mob;

/** RPG experience for controlled encounters, independent of vanilla orb drops. */
public final class HuntingRewards {
    private HuntingRewards() { }

    public static long experience(int level, long explicit, boolean elite, boolean boss,
                                  long base, long perLevel, double eliteMultiplier, double bossMultiplier) {
        if (explicit >= 0) return Math.min(100_000, explicit);
        double reward = Math.clamp(base, 0, 10_000) + (double) Math.clamp(perLevel, 0, 1_000) * Math.clamp(level, 1, 100);
        double multiplier = boss ? bossMultiplier : elite ? eliteMultiplier : 1;
        if (!Double.isFinite(multiplier)) multiplier = 1;
        return Math.clamp(Math.round(reward * Math.clamp(multiplier, 0, 10)), 0, 100_000);
    }
}
