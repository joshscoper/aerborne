package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeodeRollsTest {
    @Test
    void geodesCrackIntoRoughOrFlawedGems() {
        assertEquals(1, GeodeRolls.tier(GeodeGrade.ROUGH, 0.0D));
        assertEquals(1, GeodeRolls.tier(GeodeGrade.ROUGH, 0.849D));
        assertEquals(2, GeodeRolls.tier(GeodeGrade.ROUGH, 0.85D));
        assertEquals(2, GeodeRolls.tier(GeodeGrade.ROUGH, 0.9999D));
        assertEquals(1, GeodeRolls.tier(GeodeGrade.RESONANT, 0.49D));
        assertEquals(2, GeodeRolls.tier(GeodeGrade.RESONANT, 0.99D));
    }

    @Test
    void onlyLuminousGeodesCanHoldAFineGem() {
        assertEquals(1, GeodeRolls.tier(GeodeGrade.LUMINOUS, 0.0D));
        assertEquals(2, GeodeRolls.tier(GeodeGrade.LUMINOUS, 0.15D));
        assertEquals(3, GeodeRolls.tier(GeodeGrade.LUMINOUS, 0.85D));
        for (double roll = 0; roll < 1; roll += .01) assertTrue(GeodeRolls.tier(GeodeGrade.LUMINOUS, roll) <= 3);
    }
}
