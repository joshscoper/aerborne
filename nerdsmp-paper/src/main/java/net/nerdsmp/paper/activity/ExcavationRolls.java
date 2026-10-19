package net.nerdsmp.paper.activity;

public final class ExcavationRolls {
    private ExcavationRolls() {
    }

    public static double findChance(int level, double findPercent, int chain) {
        double base = 0.012D + Math.max(0, level) * 0.0004D;
        double multiplier = 1.0D + Math.max(0.0D, findPercent) / 100.0D;
        double chainBonus = Math.min(0.025D, Math.max(0, chain) * 0.0005D);
        return Math.min(0.15D, base * multiplier + chainBonus);
    }

    public static FishingRolls.Rarity rarity(int level, int chain, double roll) {
        double bonus = Math.min(0.12D, Math.max(0, level) * 0.001D + Math.min(50, Math.max(0, chain)) * 0.001D);
        if (roll < 0.003D + bonus * 0.05D) {
            return FishingRolls.Rarity.LEGENDARY;
        }
        if (roll < 0.05D + bonus * 0.35D) {
            return FishingRolls.Rarity.RARE;
        }
        if (roll < 0.30D + bonus) {
            return FishingRolls.Rarity.UNCOMMON;
        }
        return FishingRolls.Rarity.COMMON;
    }
}
