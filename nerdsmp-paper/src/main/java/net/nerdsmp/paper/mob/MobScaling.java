package net.nerdsmp.paper.mob;

final class MobScaling {
    private MobScaling() {
    }

    static Scale calculate(double rawDistance, int rawGroupSize, MobTier tier, Tuning tuning) {
        double distance = Math.max(0.0D, rawDistance);
        int level = Math.max(1, Math.min(100,
            1 + (int) Math.floor(distance / Math.max(16.0D, tuning.blocksPerLevel()))));
        return calculateAt(level, distance, rawGroupSize, tier, tuning);
    }

    static Scale atLevel(int level, int rawGroupSize, MobTier tier, Tuning tuning) {
        if (level < 1 || level > 100) throw new IllegalArgumentException("Zone mob level must be 1–100");
        return calculateAt(level, 0, rawGroupSize, tier, tuning);
    }

    private static Scale calculateAt(int level, double distance, int rawGroupSize, MobTier tier, Tuning tuning) {
        int groupLimit = tier == MobTier.STANDARD ? 1 : tuning.maximumGroupSize();
        int groupSize = Math.max(1, Math.min(groupLimit, rawGroupSize));
        int extraPlayers = groupSize - 1;

        double distanceHealth = 1.0D + (level - 1) * nonNegative(tuning.healthPerLevel());
        double distanceDamage = 1.0D + (level - 1) * nonNegative(tuning.damagePerLevel());
        double distanceReward = 1.0D + (level - 1) * nonNegative(tuning.rewardPerLevel());
        double groupHealth = tier == MobTier.BOSS ? tuning.bossHealthPerExtraPlayer()
            : tier == MobTier.ELITE ? tuning.eliteHealthPerExtraPlayer() : 0.0D;
        double groupDamage = tier == MobTier.BOSS ? tuning.bossDamagePerExtraPlayer()
            : tier == MobTier.ELITE ? tuning.eliteDamagePerExtraPlayer() : 0.0D;
        double groupReward = tier == MobTier.BOSS ? tuning.bossRewardPerExtraPlayer()
            : tier == MobTier.ELITE ? tuning.eliteRewardPerExtraPlayer() : 0.0D;

        return new Scale(
            level,
            groupSize,
            distance,
            distanceHealth * tier.healthMultiplier() * (1.0D + extraPlayers * nonNegative(groupHealth)),
            distanceDamage * tier.damageMultiplier() * (1.0D + extraPlayers * nonNegative(groupDamage)),
            distanceReward * tier.rewardMultiplier() * (1.0D + extraPlayers * nonNegative(groupReward))
        );
    }

    private static double nonNegative(double value) {
        return Math.max(0.0D, value);
    }

    record Tuning(
        double blocksPerLevel,
        double healthPerLevel,
        double damagePerLevel,
        double rewardPerLevel,
        int maximumGroupSize,
        double eliteHealthPerExtraPlayer,
        double eliteDamagePerExtraPlayer,
        double eliteRewardPerExtraPlayer,
        double bossHealthPerExtraPlayer,
        double bossDamagePerExtraPlayer,
        double bossRewardPerExtraPlayer
    ) {
    }

    record Scale(
        int level,
        int groupSize,
        double distance,
        double healthMultiplier,
        double damageMultiplier,
        double rewardMultiplier
    ) {
    }
}
