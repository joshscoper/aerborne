package net.nerdsmp.paper.engine;

import net.nerdsmp.paper.item.PaperItemService.GearSlot;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemBlueprintTest {
    private ItemBlueprint item(Material material, int sockets, Map<String, Double> stats) {
        return new ItemBlueprint("sky_blade", "Sky Blade", material, "rare", sockets, 10, null, List.of("A blade from the clouds."), stats);
    }
    @Test void equipmentSlotFollowsVanillaMaterialAndRarityNormalizes() {
        assertEquals(GearSlot.MAIN_HAND, item(Material.IRON_SWORD, 2, Map.of("damage", 12D)).slot());
        assertEquals(GearSlot.CHEST, item(Material.IRON_CHESTPLATE, 1, Map.of("defense", 6D)).slot());
        assertEquals(GearSlot.OFF_HAND, item(Material.SHIELD, 1, Map.of("defense", 2D)).slot());
        assertEquals("RARE", item(Material.BOW, 1, Map.of("projectile-damage", 6D)).rarity());
    }
    @Test void materialItemsCannotPretendToBeEquippableOrSupplyStats() {
        assertEquals(GearSlot.NONE, item(Material.AMETHYST_SHARD, 0, Map.of()).slot());
        assertThrows(IllegalArgumentException.class, () -> item(Material.AMETHYST_SHARD, 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> item(Material.AMETHYST_SHARD, 0, Map.of("defense", 2D)));
        assertThrows(IllegalArgumentException.class, () -> new ItemBlueprint("ore", "Ore", Material.IRON_INGOT, "COMMON", 0, 1, "sky", List.of(), Map.of()));
    }
    @Test void primaryDamageCannotBeAddedToUnsupportedMaterialRoles() {
        assertThrows(IllegalArgumentException.class, () -> item(Material.IRON_HELMET, 1, Map.of("damage", 2D)));
        assertThrows(IllegalArgumentException.class, () -> item(Material.IRON_SWORD, 1, Map.of("projectile-damage", 2D)));
        assertDoesNotThrow(() -> item(Material.TRIDENT, 1, Map.of("damage", 8D, "projectile-damage", 7D)));
        assertDoesNotThrow(() -> item(Material.MACE, 1, Map.of("damage", 8D)));
        assertDoesNotThrow(() -> item(Material.IRON_SPEAR, 1, Map.of("damage", 6D)));
    }
    @Test void identityTextAndNumericBoundsAreStrict() {
        for (String id : List.of("Uppercase", "../blade", "space name", "a".repeat(49)))
            assertThrows(IllegalArgumentException.class, () -> new ItemBlueprint(id, "Blade", Material.IRON_SWORD, "COMMON", 1, 1, null, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemBlueprint("blade", "Bad\nName", Material.IRON_SWORD, "COMMON", 1, 1, null, List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> item(Material.AIR, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> item(Material.IRON_SWORD, 5, Map.of()));
        for (int level : List.of(0, 101))
            assertThrows(IllegalArgumentException.class, () -> new ItemBlueprint("blade", "Blade", Material.IRON_SWORD, "COMMON", 1, level, null, List.of(), Map.of()));
    }
    @Test void unknownOrNonFiniteStatsAreRejectedAndZeroIsOmitted() {
        assertFalse(ItemStats.allowed().contains("critical-chance-percent"));
        assertThrows(IllegalArgumentException.class, () -> item(Material.IRON_SWORD, 1, Map.of("critical-chance-percent", 10D)));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 1001})
            assertThrows(IllegalArgumentException.class, () -> item(Material.IRON_SWORD, 1, Map.of("damage", value)));
        assertThrows(IllegalArgumentException.class, () -> item(Material.IRON_SWORD, 1, Map.of("life-steal-percent", 101D)));
        assertTrue(item(Material.IRON_SWORD, 1, Map.of("damage", 0D)).stats().isEmpty());
    }
    @Test void descriptionsRemainLiteralAndCollectionsAreImmutable() {
        var blueprint = new ItemBlueprint("blade", "<red>Blade", Material.IRON_SWORD, "COMMON", 1, 1, null,
            List.of("<click:run_command:/op>Literal text"), Map.of("damage", 5D));
        assertEquals("<red>Blade", blueprint.name());
        assertThrows(UnsupportedOperationException.class, () -> blueprint.lore().clear());
        assertThrows(UnsupportedOperationException.class, () -> blueprint.stats().clear());
    }
    @Test void setBonusesMustActuallyAffectAConsumedProfileStat() {
        var set = new ItemSetBlueprint("sky", "Sky Ward", 4, Map.of(2, new ItemSetBlueprint.Bonus("+2 Defense", Map.of("defense", 2D))));
        assertEquals(2D, set.bonuses().get(2).stats().get("defense"));
        assertThrows(IllegalArgumentException.class, () -> new ItemSetBlueprint.Bonus("Damage", Map.of("damage", 5D)));
        assertEquals(5D, new ItemSetBlueprint.Bonus("Poison", Map.of("poison-potency-percent", 5D)).stats().get("poison-potency-percent"));
        assertEquals(5D, new ItemSetBlueprint.Bonus("Durability", Map.of("durability-save-percent", 5D)).stats().get("durability-save-percent"));
        assertThrows(IllegalArgumentException.class, () -> new ItemSetBlueprint.Bonus("Nothing", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemSetBlueprint("sky", "Sky", 4, Map.of(5, set.bonuses().get(2))));
        assertThrows(UnsupportedOperationException.class, () -> set.bonuses().clear());
    }
}
