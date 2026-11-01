package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalvageTest {
    @Test void onlyWeaponsToolsAndArmourCount() {
        assertTrue(Salvage.gear(Material.IRON_SWORD));
        assertTrue(Salvage.gear(Material.NETHERITE_CHESTPLATE));
        assertTrue(Salvage.gear(Material.BOW));
        assertFalse(Salvage.gear(Material.STONE));
        assertFalse(Salvage.gear(Material.FISHING_ROD));
        assertFalse(Salvage.gear(Material.SWEET_BERRIES));
    }

    @Test void plainKitNeverPaysDustSoItCantBeFarmed() {
        for (Material type : new Material[]{Material.WOODEN_SWORD, Material.STONE_PICKAXE, Material.LEATHER_BOOTS, Material.IRON_SWORD, Material.GOLDEN_HELMET})
            assertEquals(0, Salvage.vanilla(type, 0, 1).dust(), type.name());
        assertTrue(Salvage.vanilla(Material.WOODEN_SWORD, 0, 1).worthless());
        assertTrue(Salvage.vanilla(Material.STONE_AXE, 0, 1).worthless());
    }

    @Test void itGivesBackLessThanItCost() {
        var iron = Salvage.vanilla(Material.IRON_SWORD, 0, 1);
        assertEquals(Material.IRON_NUGGET, iron.material());
        assertTrue(iron.amount() < 18, "a sword is two ingots");
        assertEquals(1, Salvage.vanilla(Material.NETHERITE_SWORD, 0, 1).amount());
    }

    @Test void enchantmentsAreWorthDustAndWearIsNot() {
        assertEquals(3, Salvage.vanilla(Material.IRON_SWORD, 6, 1).dust());
        assertEquals(5, Salvage.vanilla(Material.DIAMOND_SWORD, 6, 1).dust());
        assertEquals(2, Salvage.vanilla(Material.IRON_SWORD, 0, .5).amount());
        assertEquals(1, Salvage.vanilla(Material.IRON_SWORD, 0, .01).amount());
    }
}
