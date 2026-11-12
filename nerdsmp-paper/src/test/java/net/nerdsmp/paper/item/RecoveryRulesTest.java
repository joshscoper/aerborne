package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryRulesTest {
    final RecoveryRules rules = new RecoveryRules(8, 5, 300, 25);
    @Test void combatDelayAppliesAtAllHealthPools() {
        assertEquals(0, rules.healing(10, 20, 7999, 8000, RecoveryRules.WellFed.NONE));
        assertEquals(1, rules.healing(10, 20, 8000, 8000, RecoveryRules.WellFed.NONE));
        assertEquals(10, rules.healing(100, 200, 8000, 8000, RecoveryRules.WellFed.NONE));
    }
    @Test void recoveryClampsWithoutRevivingDeadPlayers() {
        assertEquals(.1, rules.healing(19.9, 20, 8000, 0, RecoveryRules.WellFed.NONE), .0001);
        assertEquals(0, rules.healing(0, 20, 8000, 0, RecoveryRules.WellFed.NONE));
        assertEquals(0, rules.healing(20, 20, 8000, 0, RecoveryRules.WellFed.NONE));
        assertEquals(0, rules.healing(Double.NaN, 20, 8000, 0, RecoveryRules.WellFed.NONE));
    }
    @Test void foodHelpsRecoveryWithoutBypassingCombat() {
        var food = rules.meal(1000, 0);
        assertEquals(1.25, rules.healing(10, 20, 8000, 0, food));
        assertEquals(0, rules.healing(10, 20, 8000, 9000, food));
        assertEquals(1, rules.healing(10, 20, 301000, 0, food));
    }
    @Test void nourishmentImprovesOneRefreshedBenefit() {
        var quality = rules.meal(1000, 15);
        var refreshed = rules.meal(2000, 0);
        assertEquals(25, refreshed.recoveryPercent());
        assertEquals(302000, refreshed.until());
        assertEquals(1.4, rules.healing(10, 20, 8000, 0, quality), .0001);
        assertEquals(1.25, rules.healing(10, 20, 8000, 0, refreshed), .0001);
        assertEquals(25, rules.meal(302000, 0).recoveryPercent());
        assertEquals(55, rules.meal(1000, Integer.MAX_VALUE).recoveryPercent());
    }
    @Test void pathologicalTuningIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecoveryRules(0, 5, 300, 25));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryRules(8, Double.NaN, 300, 25));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryRules(8, 5, -1, 25));
        assertThrows(IllegalArgumentException.class, () -> new RecoveryRules.WellFed(1, Double.POSITIVE_INFINITY));
    }
}
