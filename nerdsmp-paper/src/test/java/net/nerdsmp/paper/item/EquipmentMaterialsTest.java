package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static net.nerdsmp.paper.item.PaperItemService.GearSlot.CHEST;
import static net.nerdsmp.paper.item.PaperItemService.GearSlot.MAIN_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EquipmentMaterialsTest {
    @Test
    void allSpearTiersAndModernWeaponsUseTheGearTemplate() {
        long spearTiers = Arrays.stream(Material.values())
            .filter(material -> material.name().endsWith("_SPEAR"))
            .peek(material -> assertEquals(MAIN_HAND, EquipmentMaterials.slot(material)))
            .count();

        assertEquals(7L, spearTiers);
        assertEquals(MAIN_HAND, EquipmentMaterials.slot(Material.MACE));
        assertEquals(MAIN_HAND, EquipmentMaterials.slot(Material.TRIDENT));
        assertEquals(MAIN_HAND, EquipmentMaterials.slot(Material.CROSSBOW));
        assertEquals(CHEST, EquipmentMaterials.slot(Material.ELYTRA));
    }

    @Test
    void nonEquipmentDoesNotEnterTheGearPipeline() {
        assertTrue(EquipmentMaterials.isEquipment(Material.COPPER_CHESTPLATE));
        assertTrue(EquipmentMaterials.isEquipment(Material.BRUSH));
        assertTrue(EquipmentMaterials.isEquipment(Material.SHEARS));
        assertTrue(!EquipmentMaterials.isEquipment(Material.DIAMOND));
        assertTrue(!EquipmentMaterials.isEquipment(Material.WOLF_ARMOR));
    }
}
