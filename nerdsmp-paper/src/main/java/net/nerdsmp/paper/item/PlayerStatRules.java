package net.nerdsmp.paper.item;

/** Health points remain real server health; only the client's ten-heart representation is scaled. */
public final class PlayerStatRules {
    private PlayerStatRules() { }
    public static final double HEART_DISPLAY = 20;

    public record HealthRules(double base, double perAdventurerLevel, double maximum) {
        public HealthRules {
            if (!Double.isFinite(base) || !Double.isFinite(perAdventurerLevel) || !Double.isFinite(maximum)
                || base < 1 || base > 1024 || perAdventurerLevel < 0 || perAdventurerLevel > 20 || maximum < base || maximum > 1024)
                throw new IllegalArgumentException("Invalid player health configuration");
        }
        public Health health(int level, double equipment, double percent) {
            double progression = Math.clamp(level - 1, 0, 99) * perAdventurerLevel;
            double flat = finitePositive(equipment), bonus = finitePositive(percent);
            return new Health(base, progression, flat, bonus, Math.clamp((base + progression + flat) * (1 + bonus / 100), 1, maximum));
        }
    }
    public record Health(double base, double progression, double equipment, double percent, double maximum) { }

    /** Catalog armor gains one health point per four item levels unless it explicitly defines health. */
    public static double armorHealth(PaperItemService.GearSlot slot, int itemLevel) {
        return switch (slot) {
            case HEAD, CHEST, LEGS, FEET -> Math.floor(Math.clamp(itemLevel, 0, 100) / 4.0);
            default -> 0;
        };
    }

    public static double defenseReduction(double defense) {
        double value = finitePositive(defense);
        return 100 * value / (100 + value);
    }
    public static double effectiveHealth(double health, double defense) {
        return finitePositive(health) * (1 + finitePositive(defense) / 100);
    }
    public static double adjustedHealth(double current, double previousMaximum, double nextMaximum, boolean restoreRatio) {
        double value = restoreRatio && previousMaximum > 0 ? current / previousMaximum * nextMaximum : current;
        return Math.clamp(Double.isFinite(value) ? value : 0, 0, nextMaximum);
    }
    private static double finitePositive(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
