package net.nerdsmp.paper.item;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Unidentified gear can't be worn. The common routes are refused outright, and anything else that puts
 * sealed gear in an armor or off-hand slot, such as a dispenser, sees it go straight back to the bags.
 */
public final class SealedGearGuard implements Listener {
    private static final int FIRST_WORN = 36, LAST_WORN = 40;
    private static final String NOTICE = "Identify this item before equipping it.";

    private final Plugin plugin;
    private final PaperItemService items;

    public SealedGearGuard(Plugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlot(PlayerInventorySlotChangeEvent event) {
        if (event.getSlot() >= FIRST_WORN && items.isUnidentified(event.getNewItemStack()))
            Bukkit.getScheduler().runTask(plugin, () -> evict(event.getPlayer()));
    }

    /** Anything worn since before this rule came in. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) { evict(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.ARMOR || !items.isUnidentified(event.getCursor())) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) Chat.notice(player, NOTICE);
    }

    /** Right-clicking armor swaps it in, which would throw the worn piece into your hand on its way back out. */
    @EventHandler(priority = EventPriority.LOW)
    public void onWear(PlayerInteractEvent event) {
        ItemStack held = event.getItem();
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || !items.isUnidentified(held) || !held.getType().getEquipmentSlot().isArmor()) return;
        event.setCancelled(true);
        Chat.notice(event.getPlayer(), NOTICE);
    }

    private void evict(Player player) {
        if (!player.isOnline()) return;
        PlayerInventory inventory = player.getInventory();
        for (int slot = FIRST_WORN; slot <= LAST_WORN; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!items.isUnidentified(item)) continue;
            inventory.setItem(slot, null);
            inventory.addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            Chat.notice(player, NOTICE);
        }
    }
}
