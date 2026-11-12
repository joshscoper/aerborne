package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

/** A custom ingredient is an authenticated catalog identity, never its name, lore or vanilla base. */
record RecipeIngredient(Material material, ItemId customItem) {
    RecipeIngredient {
        java.util.Objects.requireNonNull(material);
    }

    boolean matches(Material actual, ItemId authenticatedItem, boolean ordinary) {
        return actual == material && (customItem == null ? ordinary && authenticatedItem == null : customItem.equals(authenticatedItem));
    }

    boolean matches(ItemStack item, PaperItemService items) {
        if (item == null || item.getType().isAir()) return false;
        ItemId authenticated = items.isAuthentic(item) ? items.describe(item).orElseThrow().itemId() : null;
        return matches(item.getType(), authenticated, FoodItems.ordinary(item) || KitchenService.isStaple(item));
    }

    ItemStack display(PaperItemService items) {
        return customItem == null ? new ItemStack(material) : items.create(customItem);
    }

    RecipeChoice choice(PaperItemService items) {
        return RecipeChoice.predicateChoice(item -> matches(item, items), display(items));
    }
}
