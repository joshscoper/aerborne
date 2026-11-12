package net.nerdsmp.paper.item;

import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;

/** Material-based recipes must never consume a gem, sealed gear, token or machine as its vanilla base. */
public final class CraftingGuard implements Listener {
    private final CustomRecipeService recipes;
    public CraftingGuard() { this(null); }
    CraftingGuard(CustomRecipeService recipes) { this.recipes = recipes; }
    public static boolean protectedIngredient(ItemStack item) {
        item = ItemPresentation.original(item);
        return item != null && !item.getType().isAir() && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().getKeys().stream().anyMatch(key -> key.getNamespace().equals("nerdsmp"))
            && !KitchenService.isStaple(item) && !FoodItems.ordinary(item);
    }
    public static boolean blocked(ItemStack[] matrix) {
        return java.util.Arrays.stream(matrix).anyMatch(CraftingGuard::protectedIngredient);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if (blocked(event.getInventory().getMatrix()) && (recipes == null
            || !recipes.acceptsCustomCraft(event.getRecipe(), event.getInventory().getMatrix()))) event.getInventory().setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (blocked(event.getInventory().getMatrix()) && (recipes == null
            || !recipes.acceptsCustomCraft(event.getRecipe(), event.getInventory().getMatrix()))) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("Use ordinary materials here. Gems and special items belong at their own stations.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automate(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof Crafter crafter && blocked(crafter.getInventory().getContents())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void smelt(FurnaceSmeltEvent event) {
        if (recipes != null && recipes.handleSmelt(event)) return;
        if (protectedIngredient(event.getSource()) || protectedIngredient(event.getResult())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(FurnaceBurnEvent event) { if (protectedIngredient(event.getFuel())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void smith(org.bukkit.event.inventory.PrepareSmithingEvent event) {
        if (protectedIngredient(event.getInventory().getItem(0)) || protectedIngredient(event.getInventory().getItem(2))) event.setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void smithCommit(org.bukkit.event.inventory.SmithItemEvent event) {
        if (protectedIngredient(event.getInventory().getItem(0)) || protectedIngredient(event.getInventory().getItem(2))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        if (protectedIngredient(event.getInventory().getItem(1))) event.setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void anvilCommit(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTopInventory() instanceof org.bukkit.inventory.AnvilInventory anvil && event.getRawSlot() == 2 && protectedIngredient(anvil.getItem(1))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void brew(org.bukkit.event.inventory.BrewEvent event) { if (protectedIngredient(event.getContents().getIngredient())) event.setCancelled(true); }
}
