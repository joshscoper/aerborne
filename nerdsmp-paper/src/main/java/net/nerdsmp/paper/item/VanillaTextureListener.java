package net.nerdsmp.paper.item;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Strips our retired models from encountered items without changing their identity, stats or sockets. */
public final class VanillaTextureListener implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> queued=new HashSet<>();
    public VanillaTextureListener(JavaPlugin plugin) { this.plugin=plugin; }
    private void clean(Inventory inventory) {
        if(VanillaTextures.customModels(plugin)) return;
        for(int slot=0;slot<inventory.getSize();slot++) {
            ItemStack item=inventory.getItem(slot); if(clean(item)) inventory.setItem(slot,item);
        }
    }
    private boolean clean(ItemStack item) {
        if(item==null || !item.hasItemMeta()) return false;
        var meta=item.getItemMeta(); var model=meta.getItemModel();
        if(model==null || !model.getNamespace().equals("nerdsmp")) return false;
        meta.setItemModel(null); item.setItemMeta(meta); return true;
    }
    private void schedule(Player player) {
        if(VanillaTextures.customModels(plugin) || !queued.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin,()-> {
            queued.remove(player.getUniqueId()); if(!player.isOnline()) return;
            clean(player.getInventory());
            ItemStack cursor=player.getItemOnCursor(); if(clean(cursor)) player.setItemOnCursor(cursor);
        });
    }
    @EventHandler public void join(PlayerJoinEvent e) { schedule(e.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e) { if(e.getEntity() instanceof Player p) schedule(p); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent e) { clean(e.getInventory()); if(e.getPlayer() instanceof Player p) schedule(p); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void click(InventoryClickEvent e) { if(e.getWhoClicked() instanceof Player p) schedule(p); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drag(InventoryDragEvent e) { if(e.getWhoClicked() instanceof Player p) schedule(p); }
    @EventHandler public void close(InventoryCloseEvent e) { if(e.getPlayer() instanceof Player p) schedule(p); }
}
