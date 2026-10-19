package net.nerdsmp.paper.presentation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class VisualFormatTest {
    @Test
    void largeValuesRemainReadableWithoutLosingSmallValues() {
        assertEquals("999", VisualFormat.compact(999L));
        assertEquals("1.5K", VisualFormat.compact(1_500L));
        assertEquals("12K", VisualFormat.compact(12_000L));
        assertEquals("2.5M", VisualFormat.compact(2_500_000L));
    }

    @Test
    void progressBarsClampAtBothEnds() {
        assertEquals("<aqua></aqua><dark_gray>■■■■</dark_gray>", VisualFormat.progressBar(-2L, 10L, 4));
        assertEquals("<aqua>■■</aqua><dark_gray>■■</dark_gray>", VisualFormat.progressBar(5L, 10L, 4));
        assertEquals("<aqua>■■■■</aqua><dark_gray></dark_gray>", VisualFormat.progressBar(50L, 10L, 4));
        assertThrows(IllegalArgumentException.class, () -> VisualFormat.progressBar(1L, 1L, 0));
    }

    @Test
    void nextMilestoneUsesTheSmallestUnreachedThreshold() {
        var next = VisualFormat.nextMilestone(12_000L, Map.of(
            "first-ripple", 10_000L,
            "broken-seal", 30_000L,
            "rift-opened", 60_000L
        )).orElseThrow();
        assertEquals("broken-seal", next.id());
        assertEquals(18_000L, next.remaining());
    }
}
