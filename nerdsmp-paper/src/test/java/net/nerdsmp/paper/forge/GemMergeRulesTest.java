package net.nerdsmp.paper.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GemMergeRulesTest {
    @Test
    void gemNamesPluraliseInMergeHints() {
        assertEquals("Rubies", ArcaneForgeService.plural("Ruby"));
        assertEquals("Topazes", ArcaneForgeService.plural("Topaz"));
        assertEquals("Onyxes", ArcaneForgeService.plural("Onyx"));
        assertEquals("Amethysts", ArcaneForgeService.plural("Amethyst"));
        assertEquals("Prismatic Gems", ArcaneForgeService.plural("Prismatic Gem"));
    }

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
    void flawlessGemsMergeIntoPerfectTheCap() {
        GemMergeRules.Result flawless = GemMergeRules.evaluate(GemType.AMETHYST, 4, GemType.AMETHYST, 4);
        assertEquals(GemMergeRules.Status.READY, flawless.status());
        assertEquals(5, flawless.outputTier());

        GemMergeRules.Result result = GemMergeRules.evaluate(GemType.AMETHYST, 5, GemType.AMETHYST, 5);
        assertFalse(result.ready());
        assertEquals(GemMergeRules.Status.MAXIMUM_TIER, result.status());
        assertEquals(5, result.outputTier());
    }
}
