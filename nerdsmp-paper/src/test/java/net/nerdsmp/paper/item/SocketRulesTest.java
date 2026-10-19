package net.nerdsmp.paper.item;

import net.nerdsmp.paper.forge.GemType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SocketRulesTest {
    @Test void RemovingMiddleGemPreservesOtherSocketsAndExactRecoveredTier() {
        var ruby = new PaperItemService.SocketedGem(GemType.RUBY, 1);
        var prism = new PaperItemService.SocketedGem(GemType.PRISMATIC, 4);
        var jade = new PaperItemService.SocketedGem(GemType.JADE, 3);
        var original = List.of(ruby, prism, jade);
        var removal = SocketRules.remove(original, 1, prism).orElseThrow();
        assertEquals(prism, removal.recovered());
        assertEquals(List.of(ruby, jade), removal.remaining());
        assertEquals(List.of(ruby, prism, jade), original);
    }

    @Test void PreviewCannotRemoveDifferentGemAfterInputsChange() {
        var ruby = new PaperItemService.SocketedGem(GemType.RUBY, 1);
        assertTrue(SocketRules.remove(List.of(ruby), 0,
            new PaperItemService.SocketedGem(GemType.RUBY, 2)).isEmpty());
        assertTrue(SocketRules.remove(List.of(ruby), 0,
            new PaperItemService.SocketedGem(GemType.PRISMATIC, 1)).isEmpty());
        assertTrue(SocketRules.remove(List.of(ruby), 0, null).isEmpty());
    }

    @Test void OutOfRangeSocketDoesNotMutateEquipment() {
        var gem = new PaperItemService.SocketedGem(GemType.RUBY, 1);
        assertTrue(SocketRules.remove(List.of(gem), -1, gem).isEmpty());
        assertTrue(SocketRules.remove(List.of(gem), 1, gem).isEmpty());
        assertTrue(SocketRules.remove(List.of(), 0, gem).isEmpty());
    }

    @Test void RecoveryFeeRetainsExistingTierCosts() {
        assertEquals(8, SocketRules.dustCost(1));
        assertEquals(12, SocketRules.dustCost(2));
        assertEquals(16, SocketRules.dustCost(3));
        assertEquals(20, SocketRules.dustCost(4));
        assertThrows(IllegalArgumentException.class, () -> SocketRules.dustCost(0));
        assertThrows(IllegalArgumentException.class, () -> SocketRules.dustCost(5));
    }
}
