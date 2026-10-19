package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeodeRollsTest {
    @Test
    void roughGeodesFavorLowTierGems() {
        assertEquals(1, GeodeRolls.tier(GeodeGrade.ROUGH, 0.0D));
        assertEquals(1, GeodeRolls.tier(GeodeGrade.ROUGH, 0.699D));
        assertEquals(2, GeodeRolls.tier(GeodeGrade.ROUGH, 0.70D));
        assertEquals(3, GeodeRolls.tier(GeodeGrade.ROUGH, 0.95D));
    }

    @Test
    void strongerGeodesCanRollTierFour() {
        assertEquals(4, GeodeRolls.tier(GeodeGrade.RESONANT, 0.99D));
        assertEquals(2, GeodeRolls.tier(GeodeGrade.LUMINOUS, 0.0D));
        assertEquals(3, GeodeRolls.tier(GeodeGrade.LUMINOUS, 0.10D));
        assertEquals(4, GeodeRolls.tier(GeodeGrade.LUMINOUS, 0.70D));
    }
}
