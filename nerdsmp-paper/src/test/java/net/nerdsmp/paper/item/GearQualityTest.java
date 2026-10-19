package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GearQualityTest {
    @Test
    void tiersAdvanceInOrderAndStopAtPeerless() {
        assertEquals(GearQuality.TEMPERED, GearQuality.WROUGHT.next().orElseThrow());
        assertEquals(GearQuality.REFINED, GearQuality.TEMPERED.next().orElseThrow());
        assertEquals(GearQuality.MASTERWORK, GearQuality.REFINED.next().orElseThrow());
        assertEquals(GearQuality.PEERLESS, GearQuality.MASTERWORK.next().orElseThrow());
        assertTrue(GearQuality.PEERLESS.next().isEmpty());
    }

    @Test
    void qualityOnlyScalesInnateEquipmentStats() {
        assertTrue(GearQuality.MASTERWORK.scales("damage"));
        assertTrue(GearQuality.MASTERWORK.scales("mining-power"));
        assertTrue(GearQuality.MASTERWORK.scales("projectile-damage"));
        assertTrue(GearQuality.MASTERWORK.scales("excavation-power"));
        assertFalse(GearQuality.MASTERWORK.scales("critical-chance-percent"));
        assertFalse(GearQuality.MASTERWORK.scales("skill-xp-percent"));
    }

    @Test
    void highTiersHaveExplicitSmithingAndMaterialRequirements() {
        assertEquals(Material.DIAMOND, GearQuality.MASTERWORK.catalyst());
        assertEquals(4, GearQuality.MASTERWORK.catalystAmount());
        assertEquals(45, GearQuality.PEERLESS.requiredSmithingLevel());
        assertEquals(Material.NETHERITE_INGOT, GearQuality.PEERLESS.catalyst());
    }
}
