package net.nerdsmp.paper.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MobHealthBarMathTest {
    @Test
    void progressIsBoundedForBossBarSafety() {
        assertEquals(1.0F, MobHealthBarMath.progress(125.0D, 100.0D), 0.0001F);
        assertEquals(0.5F, MobHealthBarMath.progress(50.0D, 100.0D), 0.0001F);
        assertEquals(0.0F, MobHealthBarMath.progress(-5.0D, 100.0D), 0.0001F);
        assertEquals(0.0F, MobHealthBarMath.progress(10.0D, 0.0D), 0.0001F);
    }

    @Test
    void aLivingMobAlwaysKeepsOneVisibleSegment() {
        assertEquals(12, MobHealthBarMath.filledSegments(100.0D, 100.0D, 12));
        assertEquals(6, MobHealthBarMath.filledSegments(50.0D, 100.0D, 12));
        assertEquals(1, MobHealthBarMath.filledSegments(0.1D, 100.0D, 12));
        assertEquals(0, MobHealthBarMath.filledSegments(0.0D, 100.0D, 12));
        assertThrows(IllegalArgumentException.class,
            () -> MobHealthBarMath.filledSegments(10.0D, 20.0D, 0));
    }

    @Test
    void healthLabelsStayCompactWithoutHidingFractions() {
        assertEquals("8", MobHealthBarMath.formatHealth(8.0D));
        assertEquals("8.5", MobHealthBarMath.formatHealth(8.5D));
        assertEquals("0", MobHealthBarMath.formatHealth(-2.0D));
    }

    @Test
    void statusIconStaysSolidThenBlinksDuringItsLastFiveSeconds() {
        assertEquals(true, MobHealthBarMath.statusIconVisible(101, 10L, 100, 10));
        assertEquals(true, MobHealthBarMath.statusIconVisible(100, 0L, 100, 10));
        assertEquals(false, MobHealthBarMath.statusIconVisible(80, 10L, 100, 10));
        assertEquals(true, MobHealthBarMath.statusIconVisible(60, 20L, 100, 10));
        assertEquals(false, MobHealthBarMath.statusIconVisible(0, 20L, 100, 10));
        assertThrows(IllegalArgumentException.class,
            () -> MobHealthBarMath.statusIconVisible(20, 0L, 100, 0));
    }
}
