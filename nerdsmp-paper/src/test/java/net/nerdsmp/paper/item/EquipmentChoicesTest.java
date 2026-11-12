package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static net.nerdsmp.paper.item.CharacterEquipmentSlot.*;
import static net.nerdsmp.paper.item.PaperItemService.GearSlot;

class EquipmentChoicesTest {
    @Test void aSecondDifferentRingFillsTheEmptySlotInsteadOfReplacingTheFirst() {
        assertEquals(RING_2,EquipmentChoices.choose(GearSlot.RING,"amethyst",Map.of(RING_1,"copper")).target());
    }
    @Test void twoOccupiedSlotsRequireAChoiceInsteadOfSilentlyDiscardingABuildChoice() {
        var choice=EquipmentChoices.choose(GearSlot.TRINKET,"new",Map.of(TRINKET_1,"a",TRINKET_2,"b"));
        assertTrue(choice.needsChoice());assertEquals(List.of(TRINKET_1,TRINKET_2),choice.candidates());
    }
    @Test void anUpgradedRollReplacesTheSameUniqueIdentityEvenWhenAnotherSlotIsEmpty() {
        assertEquals(RING_2,EquipmentChoices.choose(GearSlot.RING,"copper",Map.of(RING_2,"copper")).target());
    }
    @Test void singleSlotsCanSwapDirectlyAndNonEquipmentHasNoDestination() {
        assertEquals(NECK,EquipmentChoices.choose(GearSlot.NECK,"new",Map.of(NECK,"old")).target());
        assertThrows(IllegalArgumentException.class,()->EquipmentChoices.choose(GearSlot.NONE,null,Map.of()));
    }
}
