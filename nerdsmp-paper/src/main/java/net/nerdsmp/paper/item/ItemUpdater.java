package net.nerdsmp.paper.item;

import net.nerdsmp.paper.forge.ArcaneForgeService;
import net.nerdsmp.paper.forge.GemType;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Brings every NerdSMP item made by an older version up to the current system, wherever a player can
 * meet it: their inventory and ender chest on join, any storage they open (and their own inventory
 * alongside it), dropped items, item frames and armour stands as their chunks load, and market,
 * auction and bank listings as they're read. Shulker boxes and bundles are updated inside too.
 *
 * <p>Each item carries a version stamp, so an item is rebuilt once and then left alone. Catalog gear
 * keeps its rolls, sockets, quality and enchantments ({@link PaperItemService#upgrade}); gems, geodes
 * and tomes are rebuilt on today's inert base ({@link ArcaneForgeService#upgrade}).
 */
public final class ItemUpdater implements Listener {
    private static volatile ItemUpdater active;

    private final PaperItemService items;
    private final ArcaneForgeService forge;
    private SkillLampService lamps;
    private final Set<UUID> pending = new LinkedHashSet<>();
    private BukkitTask task;
    private boolean updating;
    private final Map<NamespacedKey, Recipe> cookingOriginals = new LinkedHashMap<>();

    public ItemUpdater(PaperItemService items, ArcaneForgeService forge) {
        this.items = items;
        this.forge = forge;
        active = this;
    }

    /** Experience bottles are updated too; the service comes up after this one's dependencies. */
    public void lamps(SkillLampService lamps) { this.lamps = lamps; }

    /** The current form of an item read from storage, or the item itself when it's current (or no updater runs). */
    public static ItemStack current(ItemStack item) {
        ItemUpdater updater = active;
        return updater == null || item == null ? item : updater.items.withoutUpgradeNote(updater.upgrade(item).orElse(item));
    }
    public void start(JavaPlugin plugin) {
        // Native furnaces compare output components before firing smelt events. Their registered
        // results must use the same template as the displayed stack, including on the second smelt.
        Bukkit.getScheduler().runTask(plugin, this::cookingResults);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < 4 && !pending.isEmpty(); i++) {
                UUID id = pending.iterator().next(); pending.remove(id); Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) update(player.getInventory());
            }
        }, 1, 1);
    }
    private void cookingResults() {
        List<CookingRecipe<?>> recipes = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> { if (recipe instanceof CookingRecipe<?> cooking) recipes.add(cooking); });
        for (CookingRecipe<?> recipe : recipes) {
            ItemStack result = current(recipe.getResult());
            if (result.isSimilar(recipe.getResult())) continue;
            CookingRecipe<?> replacement;
            if (recipe instanceof FurnaceRecipe) replacement = new FurnaceRecipe(recipe.getKey(), result, recipe.getInputChoice(), recipe.getExperience(), recipe.getCookingTime());
            else if (recipe instanceof BlastingRecipe) replacement = new BlastingRecipe(recipe.getKey(), result, recipe.getInputChoice(), recipe.getExperience(), recipe.getCookingTime());
            else if (recipe instanceof SmokingRecipe) replacement = new SmokingRecipe(recipe.getKey(), result, recipe.getInputChoice(), recipe.getExperience(), recipe.getCookingTime());
            else if (recipe instanceof CampfireRecipe) replacement = new CampfireRecipe(recipe.getKey(), result, recipe.getInputChoice(), recipe.getExperience(), recipe.getCookingTime());
            else continue;
            replacement.setGroup(recipe.getGroup()); replacement.setCategory(recipe.getCategory());
            if (!Bukkit.removeRecipe(recipe.getKey())) continue;
            if (!Bukkit.addRecipe(replacement)) { Bukkit.addRecipe(recipe); throw new IllegalStateException("Could not update cooking result " + recipe.getKey()); }
            cookingOriginals.put(recipe.getKey(), recipe);
        }
        for (var world : Bukkit.getWorlds()) for (var chunk : world.getLoadedChunks()) cookingStorage(chunk);
    }
    private void cookingStorage(org.bukkit.Chunk chunk) {
        for (var state : chunk.getTileEntities()) if (state instanceof org.bukkit.block.Furnace furnace) update(furnace.getInventory());
    }
    @EventHandler public void loaded(ChunkLoadEvent event) { cookingStorage(event.getChunk()); }

    public void close() {
        if (task != null) task.cancel(); pending.clear();
        cookingOriginals.forEach((key, recipe) -> { Bukkit.removeRecipe(key); Bukkit.addRecipe(recipe); }); cookingOriginals.clear();
        if (active == this) active = null;
    }

    /** The current form of an item, or empty when it's already current or isn't a NerdSMP item. */
    public Optional<ItemStack> upgrade(ItemStack item) {
        if (item == null || item.getType().isAir()) return Optional.empty();
        if (lamps != null && item.getType() == org.bukkit.Material.EXPERIENCE_BOTTLE) return lamps.upgrade(item);
        Optional<ItemStack> food = FoodItems.upgrade(item);
        if (food.isPresent()) item = food.get();
        if (!item.hasItemMeta()) return ItemPresentation.upgrade(item).or(() -> food);
        Optional<ItemStack> forged = forge.upgrade(item);
        if (forged.isPresent()) return forged;
        Optional<ItemStack> catalog = items.upgrade(item);
        Optional<ItemStack> nested = nested(catalog.orElse(item));
        Optional<ItemStack> changed = nested.isPresent() ? nested : catalog.isPresent() ? catalog : food;
        Optional<ItemStack> presented = ItemPresentation.upgrade(changed.orElse(item));
        return presented.isPresent() ? presented : changed;
    }

    /** Updates every slot of an inventory in place; returns how many items changed. */
    public int update(Inventory inventory) {
        boolean previous = updating; updating = true;
        try {
        int changed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            Optional<ItemStack> current = upgrade(contents[slot]);
            if (current.isEmpty()) continue;
            inventory.setItem(slot, current.get());
            changed++;
        }
        return changed;
        } finally { updating = previous; }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void changed(PlayerInventorySlotChangeEvent event) { if (!updating) pending.add(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void crafted(PrepareItemCraftEvent event) {
        ItemStack item = event.getInventory().getResult(); if (item != null) event.getInventory().setResult(current(item));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void smelted(FurnaceSmeltEvent event) {
        event.setResult(current(event.getResult()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automated(CrafterCraftEvent event) { event.setResult(current(event.getResult())); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) { if (event.getResult() != null) event.setResult(current(event.getResult())); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void smithing(PrepareSmithingEvent event) { if (event.getResult() != null) event.setResult(current(event.getResult())); }

    private Optional<ItemStack> nested(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof ShulkerBox box) {
            if (update(box.getInventory()) == 0) return Optional.empty();
            ItemStack copy = item.clone();
            BlockStateMeta copyMeta = (BlockStateMeta) copy.getItemMeta();
            copyMeta.setBlockState(box);
            copy.setItemMeta(copyMeta);
            return Optional.of(copy);
        }
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            boolean changed = false;
            List<ItemStack> contents = new ArrayList<>();
            for (ItemStack inner : bundle.getItems()) {
                Optional<ItemStack> current = upgrade(inner);
                changed |= current.isPresent();
                contents.add(current.orElse(inner));
            }
            if (!changed) return Optional.empty();
            ItemStack copy = item.clone();
            BundleMeta copyMeta = (BundleMeta) copy.getItemMeta();
            copyMeta.setItems(contents);
            copy.setItemMeta(copyMeta);
            return Optional.of(copy);
        }
        return Optional.empty();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer().getInventory());
        update(event.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        update(event.getView().getBottomInventory());
        Inventory top = event.getView().getTopInventory();
        if (storage(top)) update(top);
    }

    /** Items taken from menus (graves, mail, rewards) are brought up to date as the menu closes. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) update(player.getInventory());
    }

    /** Real storage only: plugin menus build their own display items and are left alone. */
    private static boolean storage(Inventory inventory) {
        if (inventory.getType() == InventoryType.ENDER_CHEST) return true;
        InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof Container || holder instanceof DoubleChest
            || holder instanceof Entity && !(holder instanceof Player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        update(event.getItem());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        update(event.getEntity());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) update(item);
            else if (entity instanceof ItemFrame frame) upgrade(frame.getItem()).ifPresent(current -> frame.setItem(current, false));
            else if (entity instanceof ArmorStand stand) {
                EntityEquipment equipment = stand.getEquipment();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (!stand.canUseEquipmentSlot(slot)) continue;
                    upgrade(equipment.getItem(slot)).ifPresent(current -> equipment.setItem(slot, current, true));
                }
            }
        }
    }

    private void update(Item item) {
        upgrade(item.getItemStack()).ifPresent(item::setItemStack);
    }

    /**
     * A gem still on its old vanilla base (redstone, lapis, amethyst...) mustn't be placed, traded or
     * fed to a mob as that item; it's converted in the hand instead.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (convertLegacyGem(event.getPlayer(), event.getHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (convertLegacyGem(event.getPlayer(), event.getHand())) event.setCancelled(true);
    }

    private boolean convertLegacyGem(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (held.getType() == GemType.BASE || forge.gem(held).isEmpty() && forge.geode(held).isEmpty()) return false;
        forge.upgrade(held).ifPresent(current -> player.getInventory().setItem(hand, current));
        return true;
    }
}
