package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AffixRollsTest {
    @Test
    void affixCountsRespectSourceBounds() {
        assertEquals(1, AffixRolls.count(0.0D, 1, 3));
        assertEquals(2, AffixRolls.count(0.34D, 1, 3));
        assertEquals(3, AffixRolls.count(0.99D, 1, 3));
    }

    @Test
    void valuesStayInsideEachAffixRange() {
        for (ItemAffix affix : ItemAffix.values()) {
            assertTrue(affix.roll(0.0D) > 0.0D);
            assertTrue(affix.roll(1.0D) >= affix.roll(0.0D));
        }
    }
}
