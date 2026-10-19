package net.nerdsmp.paper.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BossEncounterRulesTest {
    @Test
    void damageCrossesOnlyTheNextUnfinishedPhase() {
        double[] thresholds = VanillaBossKind.ENDER_DRAGON.thresholds();

        assertEquals(-1, BossEncounterRules.crossedPhase(200.0D, 40.0D, 200.0D, 0, thresholds));
        assertEquals(0, BossEncounterRules.crossedPhase(200.0D, 60.0D, 200.0D, 0, thresholds));
        assertEquals(1, BossEncounterRules.crossedPhase(145.0D, 50.0D, 200.0D, 1, thresholds));
        assertEquals(2, BossEncounterRules.crossedPhase(95.0D, 50.0D, 200.0D, 2, thresholds));
        assertEquals(-1, BossEncounterRules.crossedPhase(40.0D, 40.0D, 200.0D, 3, thresholds));
    }

    @Test
    void reloadCanInferAlreadyCompletedPhasesFromHealth() {
        double[] thresholds = VanillaBossKind.WARDEN.thresholds();

        assertEquals(0, BossEncounterRules.completedPhases(500.0D, 500.0D, thresholds));
        assertEquals(1, BossEncounterRules.completedPhases(300.0D, 500.0D, thresholds));
        assertEquals(2, BossEncounterRules.completedPhases(200.0D, 500.0D, thresholds));
        assertEquals(3, BossEncounterRules.completedPhases(100.0D, 500.0D, thresholds));
    }
}
