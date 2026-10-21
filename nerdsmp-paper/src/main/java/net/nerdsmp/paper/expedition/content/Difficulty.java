package net.nerdsmp.paper.expedition.content;

/**
 * The 1-5 difficulty rating on expedition cards, for Normal. Staff set it with {@code difficulty:};
 * without one it is estimated from the expedition's level.
 */
public final class Difficulty {
    private static final String[] LABELS = {"Easy", "Moderate", "Challenging", "Hard", "Brutal"};

    private Difficulty() { }

    public static int rating(ExpeditionDefinition expedition) {
        if (expedition.difficulty() != null) return Math.max(1, Math.min(5, expedition.difficulty()));
        return estimate(expedition.level());
    }

    /** Level 1-10 Easy, 11-20 Moderate, 21-35 Challenging, 36-55 Hard, above that Brutal. */
    public static int estimate(int level) {
        if (level <= 10) return 1;
        if (level <= 20) return 2;
        if (level <= 35) return 3;
        if (level <= 55) return 4;
        return 5;
    }

    public static String label(int rating) { return LABELS[Math.max(1, Math.min(5, rating)) - 1]; }

    /** Five stars, filled up to the rating, such as {@code ★★☆☆☆}. */
    public static String stars(int rating) {
        int filled = Math.max(1, Math.min(5, rating));
        return "★".repeat(filled) + "☆".repeat(5 - filled);
    }
}
