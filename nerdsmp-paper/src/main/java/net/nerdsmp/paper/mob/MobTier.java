package net.nerdsmp.paper.mob;

public enum MobTier {
    STANDARD(1.0D, 1.0D, 1.0D),
    ELITE(1.60D, 1.15D, 2.50D),
    BOSS(1.50D, 1.15D, 4.00D);

    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double rewardMultiplier;

    MobTier(double healthMultiplier, double damageMultiplier, double rewardMultiplier) {
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.rewardMultiplier = rewardMultiplier;
    }

    double healthMultiplier() {
        return healthMultiplier;
    }

    double damageMultiplier() {
        return damageMultiplier;
    }

    double rewardMultiplier() {
        return rewardMultiplier;
    }
}
