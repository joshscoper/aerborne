package net.nerdsmp.paper.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DamageDisplayMathTest {
    @Test
    void opacityFadesInHoldsAndFadesOut() {
        assertEquals(0, DamageDisplayMath.opacityAt(0, 24, 4, 8));
        assertEquals(85, DamageDisplayMath.opacityAt(1, 24, 4, 8));
        assertEquals(255, DamageDisplayMath.opacityAt(3, 24, 4, 8));
        assertEquals(255, DamageDisplayMath.opacityAt(16, 24, 4, 8));
        assertTrue(DamageDisplayMath.opacityAt(20, 24, 4, 8)
            < DamageDisplayMath.opacityAt(18, 24, 4, 8));
        assertEquals(0, DamageDisplayMath.opacityAt(23, 24, 4, 8));
        assertEquals(0, DamageDisplayMath.opacityAt(24, 24, 4, 8));
    }

    @Test
    void scalePopsInAndSettlesBeforeTheExit() {
        assertEquals(0.62F, DamageDisplayMath.scaleAt(0, 24, 4, 8), 0.001F);
        assertEquals(1.0F, DamageDisplayMath.scaleAt(3, 24, 4, 8), 0.001F);
        assertEquals(1.0F, DamageDisplayMath.scaleAt(12, 24, 4, 8), 0.001F);
        assertTrue(DamageDisplayMath.scaleAt(23, 24, 4, 8) < 1.0F);
    }

    @Test
    void displayedDamageReportsTheResolvedHitWithoutAnOverkillCap() {
        assertEquals(7.0D, DamageDisplayMath.visibleDamage(7.0D), 0.0001D);
        assertEquals(12.0D, DamageDisplayMath.visibleDamage(12.0D), 0.0001D);
        assertEquals(0.0D, DamageDisplayMath.visibleDamage(-4.0D), 0.0001D);
    }
}
