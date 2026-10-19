package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Single-block field crops only. Cocoa, berry bushes and tall pitcher crops need distinct placement rules. */
public final class CropReplanting {
    private CropReplanting() { }
    public record Plant(Material crop,Material seed,Material soil) { }
    public static Plant plant(Material crop) {
        return switch(crop) {
            case WHEAT -> new Plant(crop,Material.WHEAT_SEEDS,Material.FARMLAND);
            case CARROTS -> new Plant(crop,Material.CARROT,Material.FARMLAND);
            case POTATOES -> new Plant(crop,Material.POTATO,Material.FARMLAND);
            case BEETROOTS -> new Plant(crop,Material.BEETROOT_SEEDS,Material.FARMLAND);
            case NETHER_WART -> new Plant(crop,Material.NETHER_WART,Material.SOUL_SAND);
            default -> null;
        };
    }
    /** Never sacrifice renamed, enchanted, socketed or other special ingredients to automation. */
    public static int seedSlot(ItemStack[] storage,Material seed) {
        for(int index=0;index<storage.length;index++) {
            ItemStack stack=storage[index];
            if(stack!=null&&stack.getType()==seed&&stack.getAmount()>0&&!stack.hasItemMeta()) return index;
        }
        return -1;
    }
    public static boolean hoe(Material material) { return material.name().endsWith("_HOE"); }
    public static boolean axe(Material material) { return material.name().endsWith("_AXE")&&!material.name().endsWith("_PICKAXE"); }
}
