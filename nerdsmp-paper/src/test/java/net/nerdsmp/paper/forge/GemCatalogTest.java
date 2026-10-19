package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GemCatalogTest {
    @Test
    void expandedCatalogIncludesAnActuallyPowerfulPrismaticGem() {
        assertEquals(11, GemType.values().length);
        assertEquals(5, GemType.PRISMATIC.stats(4).size());
        assertTrue(GemType.PRISMATIC.stats(4).get("damage-percent") >= 8.0D);
        assertTrue(GemType.PRISMATIC.stats(4).get("treasure-find-percent") >= 12.0D);
    }

    @Test
    void prismaticRollsAreUltraRareAndNeverComeFromRoughGeodes() {
        assertEquals(0.0D, GemRolls.prismaticChance(GeodeGrade.ROUGH));
        assertEquals(0.0005D, GemRolls.prismaticChance(GeodeGrade.RESONANT));
        assertEquals(0.005D, GemRolls.prismaticChance(GeodeGrade.LUMINOUS));
        assertFalse(GemRolls.prismatic(GeodeGrade.ROUGH, 0.0D));
        assertTrue(GemRolls.prismatic(GeodeGrade.LUMINOUS, 0.0049D));
        assertFalse(GemRolls.prismatic(GeodeGrade.LUMINOUS, 0.005D));
    }
}
