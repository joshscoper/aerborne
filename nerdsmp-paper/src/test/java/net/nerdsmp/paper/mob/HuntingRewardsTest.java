package net.nerdsmp.paper.mob;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HuntingRewardsTest {
    @Test void candidateCurveScalesByLevelAndExplicitRank() {
        assertEquals(40, xp(1, -1, false, false));
        assertEquals(135, xp(20, -1, false, false));
        assertEquals(203, xp(20, -1, true, false));
        assertEquals(405, xp(20, -1, true, true));
    }
    @Test void authoredOverrideIsFinalIncludingZeroAndNeverReadsVanillaOrbs() {
        assertEquals(73, xp(20, 73, true, true));
        assertEquals(0, xp(20, 0, true, true));
        assertEquals(100_000, xp(20, Long.MAX_VALUE, true, true));
    }
    @Test void malformedTuningAndLevelsAreBounded() {
        assertEquals(40, xp(-1, -1, false, false));
        assertEquals(535, xp(1000, -1, false, false));
        assertEquals(100_000, HuntingRewards.experience(100, -1, false, true, Long.MAX_VALUE, Long.MAX_VALUE, 1, 100));
        assertEquals(40, HuntingRewards.experience(1, -1, false, true, 35, 5, 1, Double.NaN));
    }
    private long xp(int level, long explicit, boolean elite, boolean boss) {
        return HuntingRewards.experience(level, explicit, elite, boss, 35, 5, 1.5, 3);
    }
}
