package net.nerdsmp.paper.zone;

import net.nerdsmp.paper.presentation.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;

/** Staff-only selection tool. Clicks never break blocks or activate workstations. */
public final class AreaWand implements Listener {
    private final NerdSmpPlugin plugin;
    private final NamespacedKey marker;
    public AreaWand(NerdSmpPlugin plugin) { this.plugin=plugin; marker=new NamespacedKey(plugin,"area_wand"); }

    public ItemStack create() {
        ItemStack item=new ItemStack(Material.STICK);
        var meta=item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.itemName(line("Area Wand",NamedTextColor.AQUA));
        meta.lore(List.of(line("Left-click a block: pos1",NamedTextColor.GRAY),
            line("Right-click a block: pos2",NamedTextColor.GRAY),
            line("Select both corners of the full cuboid.",NamedTextColor.GRAY),
            line("Save: /digsite create <id> [seconds]",NamedTextColor.YELLOW),
            line("Also works with /zone create <id>.",NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(marker,PersistentDataType.INTEGER,1);
        item.setItemMeta(meta); return item;
    }

    public boolean isWand(ItemStack item) {
        return item!=null && item.getType()==Material.STICK && item.getAmount()==1 && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(marker,PersistentDataType.INTEGER)
            && Integer.valueOf(1).equals(item.getItemMeta().getPersistentDataContainer().get(marker,PersistentDataType.INTEGER));
    }

    public void give(Player player) {
        if (!player.hasPermission(ZoneCommand.PERMISSION)) { Chat.error(player, "Only staff can use the area wand."); return; }
        if (!player.getInventory().addItem(create()).isEmpty()) { Chat.error(player, "Make one empty inventory slot for the area wand."); return; }
        Chat.success(player, "Area wand ready. Left-click pos1, right-click pos2; then " + Chat.syntax("/digsite create <id> [seconds]") + ".");
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void select(PlayerInteractEvent event) {
        if (event.getHand()!=EquipmentSlot.HAND || !isWand(event.getItem())
            || event.getAction()!=Action.LEFT_CLICK_BLOCK && event.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        boolean previouslyCancelled=event.isCancelled();
        event.setCancelled(true);
        if (previouslyCancelled || event.getClickedBlock()==null) return;
        Player player=event.getPlayer();
        if (!player.hasPermission(ZoneCommand.PERMISSION)) { Chat.error(player, "Only staff can use the area wand."); return; }
        try {
            plugin.zoneCommands().selectCorner(player,event.getAction()==Action.LEFT_CLICK_BLOCK,event.getClickedBlock().getLocation());
            player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.35F,
                event.getAction()==Action.LEFT_CLICK_BLOCK ? 1F : 1.25F);
            preview(player);
        } catch (IllegalArgumentException | IllegalStateException invalid) { Chat.error(player, Chat.escape(invalid.getMessage())); }
    }

    @EventHandler public void quit(PlayerQuitEvent event) { plugin.zoneCommands().selections().clear(event.getPlayer().getUniqueId()); }

    public void preview(Player player) {
        var selected=plugin.zoneCommands().selections().selection(player.getUniqueId()).orElse(null);
        if (selected==null || !selected.worldId().equals(player.getWorld().getUID())) return;
        if (!selected.complete()) {
            var point=selected.first()!=null ? selected.first() : selected.second();
            player.spawnParticle(Particle.END_ROD,point.x()+.5,point.y()+.5,point.z()+.5,8,.2,.2,.2,0);
            return;
        }
        var b=selected.bounds();
        double[][] corners={{b.minX(),b.minY(),b.minZ()},{b.maxX()+1D,b.minY(),b.minZ()},
            {b.minX(),b.maxY()+1D,b.minZ()},{b.maxX()+1D,b.maxY()+1D,b.minZ()},
            {b.minX(),b.minY(),b.maxZ()+1D},{b.maxX()+1D,b.minY(),b.maxZ()+1D},
            {b.minX(),b.maxY()+1D,b.maxZ()+1D},{b.maxX()+1D,b.maxY()+1D,b.maxZ()+1D}};
        int[][] edges={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        double perimeter=4D*((long)b.maxX()-b.minX()+1+(long)b.maxY()-b.minY()+1+(long)b.maxZ()-b.minZ()+1);
        double spacing=Math.max(1D,perimeter/180D);
        var at=player.getLocation();
        // One burst, at most 204 samples; no entities, scheduled animations, or chunk loads.
        for (int[] edge:edges) {
            double[] a=corners[edge[0]],c=corners[edge[1]];
            double length=Math.abs(c[0]-a[0])+Math.abs(c[1]-a[1])+Math.abs(c[2]-a[2]);
            int steps=Math.max(1,(int)Math.ceil(length/spacing));
            for(int sample=0;sample<=steps;sample++) {
                double t=(double)sample/steps,x=a[0]+(c[0]-a[0])*t,y=a[1]+(c[1]-a[1])*t,z=a[2]+(c[2]-a[2])*t;
                double dx=x-at.getX(),dy=y-at.getY(),dz=z-at.getZ();
                if(dx*dx+dy*dy+dz*dz<=4096) player.spawnParticle(Particle.END_ROD,x,y,z,1,0,0,0,0);
            }
        }
    }

    private static Component line(String value,NamedTextColor color) { return Component.text(value,color).decoration(TextDecoration.ITALIC,false); }
}
