package net.nerdsmp.paper.mob;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneMobScalingTest {
    private static final MobScaling.Tuning TUNING=new MobScaling.Tuning(128,.01,.006,.008,8,.30,.06,.25,.35,.03,.35);
    @Test void fixedLevelsMatchExistingEconomyAndCombatCurvesWithoutDependingOnCoordinates() {
        for(int level=1;level<=100;level++) for(MobTier tier:MobTier.values()) {
            var zone=MobScaling.atLevel(level,4,tier,TUNING);
            var wilderness=MobScaling.calculate((level-1)*128D,4,tier,TUNING);
            assertEquals(level,zone.level()); assertEquals(0,zone.distance());
            assertEquals(wilderness.healthMultiplier(),zone.healthMultiplier(),1e-9);
            assertEquals(wilderness.damageMultiplier(),zone.damageMultiplier(),1e-9);
            assertEquals(wilderness.rewardMultiplier(),zone.rewardMultiplier(),1e-9);
        }
    }
    @Test void eliteCombatRosterDoesNotChangeAuthoredLevel() {
        var solo=MobScaling.atLevel(23,1,MobTier.ELITE,TUNING);
        var party=MobScaling.atLevel(23,8,MobTier.ELITE,TUNING);
        assertEquals(23,party.level()); assertTrue(party.healthMultiplier()>solo.healthMultiplier());
    }
    @Test void invalidStaffLevelsAreNotSilentlyClamped() {
        assertThrows(IllegalArgumentException.class,()->MobScaling.atLevel(0,1,MobTier.STANDARD,TUNING));
        assertThrows(IllegalArgumentException.class,()->MobScaling.atLevel(101,1,MobTier.STANDARD,TUNING));
    }
}
