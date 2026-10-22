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
        assertEquals(24, SocketRules.dustCost(5));
        assertThrows(IllegalArgumentException.class, () -> SocketRules.dustCost(0));
        assertThrows(IllegalArgumentException.class, () -> SocketRules.dustCost(6));
    }

    @Test void gradesClimbFromRoughToPerfect() {
        assertEquals(List.of("Rough", "Flawed", "Fine", "Flawless", "Perfect"),
            java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(SocketRules::grade).toList());
        assertTrue(SocketRules.validTier(5));
        assertFalse(SocketRules.validTier(6));
    }

    @Test void socketsAreTypedByTheGearAndRareGearEndsWithAUniversalOne() {
        var sword = java.util.Map.of("damage", 7.0);
        var pick = java.util.Map.of("damage", 6.0, "mining-power", 3.0);
        assertEquals(SocketRules.SlotType.COMBAT, SocketRules.slot(sword, false, "RARE", 0, 2));
        assertEquals(SocketRules.SlotType.COMBAT, SocketRules.slot(sword, false, "RARE", 1, 2));
        assertEquals(SocketRules.SlotType.UNIVERSAL, SocketRules.slot(sword, false, "LEGENDARY", 1, 2));
        assertEquals(SocketRules.SlotType.DEFENSIVE, SocketRules.slot(java.util.Map.of("defense", 5.0), true, "EPIC", 0, 2));
        assertEquals(SocketRules.SlotType.GATHERING, SocketRules.slot(pick, false, "MYTHIC", 0, 3));
        assertEquals(SocketRules.SlotType.DEFENSIVE, SocketRules.slot(java.util.Map.of("defense", 5.0), true, "MYTHIC", 0, 1),
            "A single socket is never the universal one");
    }

    @Test void gemsFitTheirOwnSlotsUtilityFitsAllAndPrismaticOnlyUniversal() {
        assertTrue(SocketRules.fits(GemType.RUBY.category(), SocketRules.SlotType.COMBAT));
        assertFalse(SocketRules.fits(GemType.RUBY.category(), SocketRules.SlotType.DEFENSIVE));
        assertTrue(SocketRules.fits(GemType.RUBY.category(), SocketRules.SlotType.UNIVERSAL));
        assertTrue(SocketRules.fits(GemType.SAPPHIRE.category(), SocketRules.SlotType.DEFENSIVE));
        assertFalse(SocketRules.fits(GemType.TOPAZ.category(), SocketRules.SlotType.COMBAT));
        for (var slot : SocketRules.SlotType.values()) assertTrue(SocketRules.fits(GemType.EMERALD.category(), slot));
        assertFalse(SocketRules.fits(GemType.PRISMATIC.category(), SocketRules.SlotType.COMBAT));
        assertTrue(SocketRules.fits(GemType.PRISMATIC.category(), SocketRules.SlotType.UNIVERSAL));
    }
}
