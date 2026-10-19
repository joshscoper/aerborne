package net.nerdsmp.paper.item;

/** Chef quality is fixed when crafted; eating never generates XP or currency. */
public final class MealRules {
    private MealRules() { }
    public static int seconds(int baseSeconds, int level, double training, double island) {
        double bonus = Math.clamp(training, 0, 50) + Math.clamp(island, 0, 30);
        return Math.clamp((int) Math.round(baseSeconds * (1 + Math.clamp(level, 0, 50) / 100.0 + bonus / 100)), 5, 300);
    }
    public static int amplifier(String effect, int level) {
        return level >= 40 && java.util.Set.of("speed", "haste", "jump_boost").contains(effect) ? 1 : 0;
    }
    public static long craftXp(int requiredLevel) { return 40L + 6L * Math.clamp(requiredLevel, 1, 50); }
}
