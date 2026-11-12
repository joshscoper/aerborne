package net.nerdsmp.paper.skyblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandVisualPolicyTest {
    @Test void configCannotCreateUnboundedDisplayWork() {
        assertEquals(64, IslandVisualPolicy.anchors(Integer.MAX_VALUE));
        assertEquals(1, IslandVisualPolicy.anchors(-1));
        assertEquals(32, IslandVisualPolicy.range(Integer.MAX_VALUE));
        assertEquals(8, IslandVisualPolicy.range(-1));
    }
    @Test void revealFadesInThenOutWithoutInvalidOpacity() {
        assertEquals(0, IslandVisualPolicy.opacity(0));
        assertEquals(255, IslandVisualPolicy.opacity(10));
        assertEquals(255, IslandVisualPolicy.opacity(40));
        assertTrue(IslandVisualPolicy.opacity(50) < 255);
        assertEquals(0, IslandVisualPolicy.opacity(60));
        assertEquals(0, IslandVisualPolicy.opacity(-1));
        for (int age = -10; age < 100; age++) {
            assertTrue(IslandVisualPolicy.opacity(age) >= 0 && IslandVisualPolicy.opacity(age) <= 255);
            assertTrue(IslandVisualPolicy.scale(age) >= .7f && IslandVisualPolicy.scale(age) <= 1f);
        }
    }
    @Test void everyChapterUnlocksOneDistinctShowcaseAndNeverRequiresCurrency() {
        var chapters = java.util.Arrays.stream(IslandShowcase.values()).filter(s -> s.kind() == IslandShowcase.Kind.CHAPTER).toList();
        assertEquals(4, chapters.size());
        for (IslandShowcase showcase : chapters) {
            assertTrue(showcase.unlocked(showcase.chapter()));
            assertFalse(showcase.unlocked(showcase.chapter() - 1));
        }
        for (IslandShowcase showcase : IslandShowcase.values()) assertSame(showcase, IslandShowcase.parse(showcase.id()));
        assertThrows(IllegalArgumentException.class, () -> IslandShowcase.parse("unknown"));
    }
    @Test void levelAndStageShowcasesFollowIslandProgress() {
        assertEquals(43, IslandShowcase.values().length);
        assertFalse(IslandShowcase.WYRM_EGG.unlocked(3, 49, 5));
        assertTrue(IslandShowcase.WYRM_EGG.unlocked(0, 50, 0));
        assertFalse(IslandShowcase.WYRM_EGG.unlocked(3), "chapters alone never unlock a level showcase");
        assertTrue(IslandShowcase.LODESTONE.unlocked(0, 1, IslandResourceStage.FOUNDRY.index()));
        assertFalse(IslandShowcase.AETHER_CRYSTAL.unlocked(3, 50, IslandResourceStage.FOUNDRY.index()));
        // Every level reward brings one showcase.
        for (IslandMilestone milestone : IslandMilestone.values())
            assertEquals(milestone.level(), SkyblockController.showcaseFor(milestone).need());
    }
}
