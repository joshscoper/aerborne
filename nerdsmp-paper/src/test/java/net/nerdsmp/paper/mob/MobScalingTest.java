package net.nerdsmp.paper.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MobScalingTest {
    private static final MobScaling.Tuning TUNING = new MobScaling.Tuning(
        128.0D, 0.01D, 0.006D, 0.008D, 8,
        0.30D, 0.06D, 0.25D,
        0.35D, 0.03D, 0.35D
    );

    @Test
    void distanceFromSpawnRaisesLevelHealthDamageAndRewards() {
        MobScaling.Scale atSpawn = MobScaling.calculate(0.0D, 1, MobTier.STANDARD, TUNING);
        MobScaling.Scale frontier = MobScaling.calculate(6_400.0D, 1, MobTier.STANDARD, TUNING);

        assertEquals(1, atSpawn.level());
        assertEquals(51, frontier.level());
        assertTrue(frontier.healthMultiplier() > atSpawn.healthMultiplier());
        assertTrue(frontier.damageMultiplier() > atSpawn.damageMultiplier());
        assertTrue(frontier.rewardMultiplier() > atSpawn.rewardMultiplier());
    }

    @Test
    void elitesScaleWithTheirActiveCombatRoster() {
        MobScaling.Scale solo = MobScaling.calculate(3_200.0D, 1, MobTier.ELITE, TUNING);
        MobScaling.Scale group = MobScaling.calculate(3_200.0D, 5, MobTier.ELITE, TUNING);

        assertTrue(group.healthMultiplier() > solo.healthMultiplier());
        assertTrue(group.damageMultiplier() > solo.damageMultiplier());
        assertTrue(group.rewardMultiplier() > solo.rewardMultiplier());
        assertEquals(solo.level(), group.level());
    }

    @Test
    void ordinaryMobsIgnoreGroupSizeAndAllTiersHonorTheCap() {
        MobScaling.Scale solo = MobScaling.calculate(99_999.0D, 1, MobTier.STANDARD, TUNING);
        MobScaling.Scale crowd = MobScaling.calculate(99_999.0D, 50, MobTier.STANDARD, TUNING);
        MobScaling.Scale boss = MobScaling.calculate(99_999.0D, 50, MobTier.BOSS, TUNING);

        assertEquals(100, solo.level());
        assertEquals(solo.healthMultiplier(), crowd.healthMultiplier());
        assertEquals(1, crowd.groupSize());
        assertEquals(8, boss.groupSize());
    }
}
