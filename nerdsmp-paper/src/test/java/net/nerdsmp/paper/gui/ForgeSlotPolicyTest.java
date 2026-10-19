package net.nerdsmp.paper.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ForgeSlotPolicyTest {
    @Test
    void gearAndTomesRouteToTheirDedicatedSlots() {
        assertEquals(11, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.GEAR, true, true));
        assertEquals(15, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.TOME, true, true));
        assertEquals(-1, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.GEAR, false, true));
        assertEquals(-1, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.TOME, true, false));
    }

    @Test
    void matchingGemsFillPrimaryThenCatalystWithoutTouchingOtherMenuSlots() {
        assertEquals(11, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.GEM, true, true));
        assertEquals(15, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.GEM, false, true));
        assertEquals(-1, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.GEM, false, false));
        assertEquals(-1, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.INVALID, true, true));
    }

    @Test
    void dustAndQualityMaterialsOnlyRouteToTheCatalystSlot() {
        assertEquals(15, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.DUST, true, true));
        assertEquals(15, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.QUALITY_MATERIAL, true, true));
        assertEquals(-1, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.DUST, true, false));
        assertEquals(-1, ForgeSlotPolicy.shiftDestination(ForgeSlotPolicy.InputKind.QUALITY_MATERIAL, false, false));
    }
}
