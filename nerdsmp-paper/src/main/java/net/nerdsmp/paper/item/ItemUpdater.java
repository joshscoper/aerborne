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
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        return updater == null || item == null ? item : updater.upgrade(item).orElse(item);
    }

    public void close() {
        if (active == this) active = null;
    }

    /** The current form of an item, or empty when it's already current or isn't a NerdSMP item. */
    public Optional<ItemStack> upgrade(ItemStack item) {
        if (item == null || item.getType().isAir()) return Optional.empty();
        if (lamps != null && item.getType() == org.bukkit.Material.EXPERIENCE_BOTTLE) return lamps.upgrade(item);
        if (!item.hasItemMeta()) return Optional.empty();
        Optional<ItemStack> forged = forge.upgrade(item);
        if (forged.isPresent()) return forged;
        Optional<ItemStack> catalog = items.upgrade(item);
        Optional<ItemStack> nested = nested(catalog.orElse(item));
        return nested.isPresent() ? nested : catalog;
    }

    /** Updates every slot of an inventory in place; returns how many items changed. */
    public int update(Inventory inventory) {
        int changed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            Optional<ItemStack> current = upgrade(contents[slot]);
            if (current.isEmpty()) continue;
            inventory.setItem(slot, current.get());
            changed++;
        }
        return changed;
    }

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
