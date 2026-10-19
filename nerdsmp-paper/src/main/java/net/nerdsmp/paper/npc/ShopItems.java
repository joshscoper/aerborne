package net.nerdsmp.paper.npc;

import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.inventory.ItemStack;

/** The same template is used for storefront previews, delivery, and buyback matching. */
final class ShopItems {
    private ShopItems() { }

    static ItemStack create(PaperItemService items, NpcContent.ShopProduct product, int units) {
        if (units < 1 || units > 64) throw new IllegalArgumentException("Shop quantity must be 1..64");
        ItemStack item = product.customItemId().map(items::create).orElseGet(() ->
            items.isCraftedEquipment(product.material()) ? items.createCrafted(product.material())
                : new ItemStack(product.material()));
        if (units > item.getMaxStackSize()) throw new IllegalArgumentException("That item cannot be bought in this quantity");
        item.setAmount(units);
        return item;
    }
}
