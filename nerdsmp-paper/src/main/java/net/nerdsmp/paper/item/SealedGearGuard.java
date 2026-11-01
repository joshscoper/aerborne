package net.nerdsmp.paper.item;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unidentified gear can't be worn or used. The common routes are refused outright, and anything else that
 * puts sealed gear in an armor or off-hand slot, such as a dispenser, sees it go straight back to the bags.
 * A sealed weapon can be carried, but it won't draw, load, block or strike until it's identified.
 */
public final class SealedGearGuard implements Listener {
    private static final int FIRST_WORN = 36, LAST_WORN = 40;
    private static final String NOTICE = "Identify this item before equipping it.";
    private static final String USE_NOTICE = "Identify this item before using it.";
    private static final long NOTICE_GAP_MS = 3000;

    private final Plugin plugin;
    private final PaperItemService items;
    private final Map<UUID, Long> warned = new HashMap<>();

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

    /** Drawing a bow, loading a crossbow, throwing a trident, raising a shield. Doors and chests still open with one in hand. */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || !items.isUnidentified(event.getItem()) || !usable(event.getItem().getType())) return;
        event.setUseItemInHand(Event.Result.DENY);
        warn(event.getPlayer());
    }

    /** A crossbow loaded before it was sealed, or anything else that gets past the click. */
    @EventHandler(priority = EventPriority.LOW)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !items.isUnidentified(event.getBow())) return;
        event.setCancelled(true);
        warn(player);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onStrike(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !items.isUnidentified(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        warn(player);
    }

    private static boolean usable(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT || type == Material.SHIELD
            || type == Material.FISHING_ROD || type.name().endsWith("_SPEAR");
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        Long last = warned.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_GAP_MS) return;
        warned.put(player.getUniqueId(), now);
        Chat.notice(player, USE_NOTICE);
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
