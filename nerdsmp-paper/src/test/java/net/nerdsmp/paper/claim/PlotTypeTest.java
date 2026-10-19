package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.api.model.PlotType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PlotTypeTest {
    @Test void commonsInheritsRatherThanDuplicatingTownDefaults() {
        assertTrue(PlotType.COMMONS.preset().isEmpty());
    }
    @Test void onlyResidentialSuppressesSettlementWideMemberAccess() {
        for (PlotType type : PlotType.values()) assertEquals(type == PlotType.RESIDENTIAL, type.privateAccess());
    }
    @Test void noPresetTurnsOnDangerousTerrainRulesOrPublicStorage() {
        for (PlotType type : PlotType.values()) {
            for (ClaimFlag flag : new ClaimFlag[]{ClaimFlag.EXPLOSIONS, ClaimFlag.FIRE_SPREAD,
                ClaimFlag.MOB_GRIEFING, ClaimFlag.PUBLIC_CONTAINERS}) {
                assertNotEquals(Boolean.TRUE, type.preset().get(flag));
            }
        }
    }
    @Test void farmAndWorkshopHaveDifferentPurposes() {
        assertEquals(Boolean.TRUE, PlotType.FARM.preset().get(ClaimFlag.PUBLIC_HARVEST));
        assertEquals(Boolean.TRUE, PlotType.FARM.preset().get(ClaimFlag.PUBLIC_ANIMALS));
        assertEquals(Boolean.TRUE, PlotType.WORKSHOP.preset().get(ClaimFlag.PUBLIC_INTERACT));
        assertEquals(Boolean.FALSE, PlotType.WORKSHOP.preset().get(ClaimFlag.PUBLIC_HARVEST));
        assertEquals(Boolean.FALSE, PlotType.PRESERVE.preset().get(ClaimFlag.HOSTILE_SPAWNING));
    }
    @Test void presetsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> PlotType.RESIDENTIAL.preset().clear());
    }
}
