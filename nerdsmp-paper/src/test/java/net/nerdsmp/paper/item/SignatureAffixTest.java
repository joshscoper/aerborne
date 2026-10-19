package net.nerdsmp.paper.item;

import net.nerdsmp.paper.engine.ItemStats;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SignatureAffixTest {
    @Test void allTenSignaturesHaveStableUniqueIdsAndValidatedImmutableStats() {
        assertEquals(10, SignatureAffix.values().length);
        assertEquals(10, Arrays.stream(SignatureAffix.values()).map(SignatureAffix::id).distinct().count());
        for (SignatureAffix signature : SignatureAffix.values()) {
            assertEquals(signature, SignatureAffix.fromId(signature.id()).orElseThrow());
            assertEquals(signature.stats(), ItemStats.validate(signature.stats(), false));
            assertFalse(signature.displayName().isBlank());
            assertFalse(signature.description().isBlank());
            assertThrows(UnsupportedOperationException.class, () -> signature.stats().put("damage", 500.0));
        }
        assertTrue(SignatureAffix.FURNACE_HEART.stats().isEmpty());
        assertTrue(SignatureAffix.fromId(null).isEmpty());
        assertTrue(SignatureAffix.fromId("FURNACE_HEART").isEmpty());
        assertTrue(SignatureAffix.fromId("display name Furnace Heart").isEmpty());
    }

    @Test void signaturesRequireMythicEquipmentInItsRealSlot() {
        var expected = Map.of(
            SignatureAffix.FURNACE_HEART, Material.NETHERITE_PICKAXE,
            SignatureAffix.DEEP_RESONANCE, Material.NETHERITE_SHOVEL,
            SignatureAffix.HARVEST_MOON, Material.NETHERITE_HOE,
            SignatureAffix.TIDEWEAVER, Material.FISHING_ROD,
            SignatureAffix.SECOND_DAWN, Material.NETHERITE_CHESTPLATE,
            SignatureAffix.HUNTERS_MERCY, Material.NETHERITE_SWORD,
            SignatureAffix.ASTRAL_INSIGHT, Material.NETHERITE_HELMET,
            SignatureAffix.EVERLASTING, Material.NETHERITE_AXE,
            SignatureAffix.VENOM_HEART, Material.DIAMOND_SPEAR,
            SignatureAffix.STORMSTRING, Material.BOW);
        expected.forEach((signature, material) -> {
            assertTrue(signature.supports(material, EquipmentMaterials.slot(material), "MYTHIC"));
            assertFalse(signature.supports(material, EquipmentMaterials.slot(material), "LEGENDARY"));
            assertFalse(signature.supports(material, PaperItemService.GearSlot.NONE, "MYTHIC"));
            assertFalse(signature.supports(Material.DIRT, PaperItemService.GearSlot.MAIN_HAND, "MYTHIC"));
        });
        assertFalse(SignatureAffix.FURNACE_HEART.supports(Material.DIAMOND_SHOVEL, PaperItemService.GearSlot.MAIN_HAND, "MYTHIC"));
        assertFalse(SignatureAffix.TIDEWEAVER.supports(Material.BOW, PaperItemService.GearSlot.MAIN_HAND, "MYTHIC"));
    }

    @Test void formerDefinitionConstructorRemainsAvailableAndHasNoSignature() throws Exception {
        Class<?> definition = Class.forName(PaperItemService.class.getName() + "$Definition");
        var oldConstructor = definition.getDeclaredConstructor(Material.class, String.class, String.class,
            PaperItemService.GearSlot.class, String.class, String.class, String.class, int.class,
            List.class, Map.class, int.class);
        oldConstructor.setAccessible(true);
        Object old = oldConstructor.newInstance(Material.IRON_PICKAXE, "Old Pickaxe", null,
            PaperItemService.GearSlot.MAIN_HAND, null, "RARE", null, 2, List.of(), Map.of("mining-power", 2.0), 15);
        var accessor = definition.getDeclaredMethod("signatureAffix");
        accessor.setAccessible(true);
        assertNull(accessor.invoke(old));
    }
}
