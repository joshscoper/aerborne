package net.nerdsmp.paper.loot;

public final class DropRolls {
    private DropRolls() {
    }

    public static double chance(double baseChance, double lootingBonus, int lootingLevel) {
        return Math.max(0.0D, Math.min(1.0D, baseChance + Math.max(0, lootingLevel) * lootingBonus));
    }

    public static int amount(int minimum, int maximum, double roll) {
        int low = Math.max(1, minimum);
        int high = Math.max(low, maximum);
        double normalized = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        return low + (int) Math.floor(normalized * (high - low + 1));
    }

    public static double scaledChance(double baseChance, double rewardMultiplier) {
        return Math.max(0.0D, Math.min(1.0D, baseChance * rewardFactor(rewardMultiplier)));
    }

    public static int scaledAmount(int minimum, int maximum, double roll, double rewardMultiplier) {
        return Math.max(1, (int) Math.round(amount(minimum, maximum, roll) * rewardFactor(rewardMultiplier)));
    }

    private static double rewardFactor(double rewardMultiplier) {
        return Math.max(1.0D, Math.min(2.5D, Math.sqrt(Math.max(1.0D, rewardMultiplier))));
    }
}
