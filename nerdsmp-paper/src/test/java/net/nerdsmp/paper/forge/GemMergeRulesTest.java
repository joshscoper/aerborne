package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GemMergeRulesTest {
    @Test
    void matchingGemsAdvanceExactlyOneTier() {
        GemMergeRules.Result result = GemMergeRules.evaluate(GemType.RUBY, 2, GemType.RUBY, 2);

        assertTrue(result.ready());
        assertEquals(GemType.RUBY, result.type());
        assertEquals(3, result.outputTier());
    }

    @Test
    void differentTypesAndTiersCannotMerge() {
        assertEquals(GemMergeRules.Status.DIFFERENT_TYPE,
            GemMergeRules.evaluate(GemType.RUBY, 1, GemType.SAPPHIRE, 1).status());
        assertEquals(GemMergeRules.Status.DIFFERENT_TIER,
            GemMergeRules.evaluate(GemType.RUBY, 1, GemType.RUBY, 2).status());
    }

    @Test
    void tierFourIsTheStableCap() {
        GemMergeRules.Result result = GemMergeRules.evaluate(GemType.AMETHYST, 4, GemType.AMETHYST, 4);

        assertFalse(result.ready());
        assertEquals(GemMergeRules.Status.MAXIMUM_TIER, result.status());
        assertEquals(4, result.outputTier());
    }
}
