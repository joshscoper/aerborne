package net.nerdsmp.paper.activity;

public final class FishingRolls {
    private FishingRolls() {
    }

    public static Rarity rarity(int level, double luckPercent, int streak, double roll) {
        return rarity(level, luckPercent, streak, false, roll);
    }

    public static Rarity rarity(int level, double luckPercent, int streak, boolean openWater, double roll) {
        double bonus = Math.min(0.18D, Math.max(0, level) * 0.0015D
            + Math.max(0.0D, luckPercent) / 500.0D + Math.min(20, Math.max(0, streak)) * 0.002D
            + (openWater ? 0.025D : 0.0D));
        double legendary = 0.004D + bonus * 0.08D;
        double rare = 0.045D + bonus * 0.38D;
        double uncommon = 0.24D + bonus;
        if (roll < legendary) {
            return Rarity.LEGENDARY;
        }
        if (roll < rare) {
            return Rarity.RARE;
        }
        if (roll < uncommon) {
            return Rarity.UNCOMMON;
        }
        return Rarity.COMMON;
    }

    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        LEGENDARY
    }
}
