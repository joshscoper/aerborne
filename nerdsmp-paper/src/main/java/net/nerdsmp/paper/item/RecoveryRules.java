package net.nerdsmp.paper.item;

/** Recovery scales with real maximum health, so higher-level characters rest at the same pace. */
public record RecoveryRules(int combatDelaySeconds, double healthPercentPerSecond,
                            int foodSeconds, double foodRecoveryPercent) {
    public RecoveryRules {
        if (combatDelaySeconds < 1 || combatDelaySeconds > 60
            || !Double.isFinite(healthPercentPerSecond) || healthPercentPerSecond <= 0 || healthPercentPerSecond > 20
            || foodSeconds < 10 || foodSeconds > 3600
            || !Double.isFinite(foodRecoveryPercent) || foodRecoveryPercent < 0 || foodRecoveryPercent > 100)
            throw new IllegalArgumentException("Invalid food-and-recovery configuration");
    }
    public double healing(double current, double maximum, long now, long blockedUntil, WellFed food) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || current <= 0 || maximum <= current || now < blockedUntil) return 0;
        double bonus = food.active(now) ? food.recoveryPercent() : 0;
        return Math.min(maximum - current, maximum * healthPercentPerSecond / 100 * (1 + bonus / 100));
    }
    public WellFed meal(long now, int nutrition) {
        double quality = foodRecoveryPercent + Math.clamp(nutrition, 0, 30);
        // The latest serving replaces one benefit; cheap snacks cannot extend a chef's higher quality.
        return new WellFed(now + foodSeconds * 1000L, quality);
    }
    public record WellFed(long until, double recoveryPercent) {
        public static final WellFed NONE = new WellFed(0, 0);
        public WellFed {
            if (until < 0 || !Double.isFinite(recoveryPercent) || recoveryPercent < 0 || recoveryPercent > 130)
                throw new IllegalArgumentException("Invalid Well Fed state");
        }
        public boolean active(long now) { return until > now; }
    }
}
