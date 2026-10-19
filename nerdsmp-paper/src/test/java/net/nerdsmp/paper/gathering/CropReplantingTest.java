package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropReplantingTest {
    @Test void SingleBlockFieldCropsConsumeTheirActualPlantingItem() {
        assertEquals(Material.WHEAT_SEEDS,CropReplanting.plant(Material.WHEAT).seed());
        assertEquals(Material.CARROT,CropReplanting.plant(Material.CARROTS).seed());
        assertEquals(Material.POTATO,CropReplanting.plant(Material.POTATOES).seed());
        assertEquals(Material.BEETROOT_SEEDS,CropReplanting.plant(Material.BEETROOTS).seed());
        assertEquals(Material.NETHER_WART,CropReplanting.plant(Material.NETHER_WART).seed());
        assertEquals(Material.SOUL_SAND,CropReplanting.plant(Material.NETHER_WART).soil());
        assertEquals(Material.FARMLAND,CropReplanting.plant(Material.WHEAT).soil());
    }
    @Test void BranchMountedAndDoubleHeightPlantsNeverUseFieldPlacementRules() {
        for(Material unsupported:new Material[]{Material.COCOA,Material.PITCHER_CROP,Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP,Material.FIRE,Material.OAK_LOG,Material.CACTUS})
            assertNull(CropReplanting.plant(unsupported),unsupported.name());
    }
    @Test void ToolsAreDeliberateAndPickaxesDoNotCountAsWoodcuttingAxes() {
        assertTrue(CropReplanting.hoe(Material.DIAMOND_HOE));
        assertFalse(CropReplanting.hoe(Material.DIAMOND_SWORD));
        assertTrue(CropReplanting.axe(Material.DIAMOND_AXE));
        assertFalse(CropReplanting.axe(Material.DIAMOND_PICKAXE));
    }
    @Test void SeedSelectionSkipsSpecialItemsEmptyStacksAndTheWrongIngredientWithoutMutatingAnything() {
        ItemStack[] storage={null,new Stack(Material.WHEAT_SEEDS,4,true),new Stack(Material.POTATO,12,false),
            new Stack(Material.WHEAT_SEEDS,0,false),new Stack(Material.WHEAT_SEEDS,2,false)};
        assertEquals(4,CropReplanting.seedSlot(storage,Material.WHEAT_SEEDS));
        assertEquals(2,storage[4].getAmount());
        assertEquals(-1,CropReplanting.seedSlot(storage,Material.BEETROOT_SEEDS));
    }
    private static final class Stack extends ItemStack {
        private final Material type; private final int amount; private final boolean special;
        Stack(Material type,int amount,boolean special) { super(); this.type=type; this.amount=amount; this.special=special; }
        @Override public Material getType() { return type; }
        @Override public int getAmount() { return amount; }
        @Override public boolean hasItemMeta() { return special; }
    }
}
