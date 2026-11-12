package net.nerdsmp.probe;

import net.nerdsmp.paper.loot.DropStacks;
import net.nerdsmp.paper.loot.DropStackingService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

final class DropStackingProbe implements Listener {
    private Item veto;
    @EventHandler public void merge(ItemMergeEvent event) { if (event.getEntity()==veto || event.getTarget()==veto) event.setCancelled(true); }
    static void run(JavaPlugin core, World world, BiConsumer<Boolean,String> check) throws Exception {
        List<ItemStack> inputs=new ArrayList<>();
        for(int i=0;i<130;i++) inputs.add(new ItemStack(Material.COBBLESTONE));
        var packed=DropStacks.compact(inputs);
        check.accept(packed.size()==3 && packed.get(0).getAmount()==64 && packed.get(1).getAmount()==64 && packed.get(2).getAmount()==2,
            "130 single drops pack into three legal stacks without losing rewards");
        check.accept(inputs.stream().allMatch(stack->stack.getAmount()==1),"pre-spawn packing does not mutate reward inputs");
        ItemStack named=new ItemStack(Material.COBBLESTONE); var meta=named.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Different reward")); named.setItemMeta(meta);
        check.accept(DropStacks.compact(List.of(inputs.getFirst(),named)).size()==2,"different reward metadata stays distinct");
        var service=new DropStackingService(core);
        var consolidate=DropStackingService.class.getDeclaredMethod("consolidate",Item.class); consolidate.setAccessible(true);
        var listener=new DropStackingProbe(); Bukkit.getPluginManager().registerEvents(listener,core);
        List<Item> entities=new ArrayList<>();
        try {
            Location at=new Location(world,8,150,8);
            world.getChunkAt(at).load();
            Item a=world.dropItem(at,new ItemStack(Material.COBBLESTONE,40)); entities.add(a);
            Item b=world.dropItem(at,new ItemStack(Material.COBBLESTONE,40)); entities.add(b);
            a.setTicksLived(100); a.setPickupDelay(30); b.setPickupDelay(5);
            consolidate.invoke(service,a);
            check.accept(a.getItemStack().getAmount()==16 && b.getItemStack().getAmount()==64,"native consolidation preserves all 80 items within legal limits: "
                + a.getItemStack().getAmount()+"/"+b.getItemStack().getAmount()+", valid="+a.isValid()+"/"+b.isValid()+", nearby="+a.getNearbyEntities(3,3,3).size());
            check.accept(b.getPickupDelay()==30 && b.getTicksLived()>=100,"consolidation retains pickup delay and oldest despawn age");
            a.remove(); b.remove();
            a=world.dropItem(at,new ItemStack(Material.COBBLESTONE)); entities.add(a);
            b=world.dropItem(at,new ItemStack(Material.COBBLESTONE)); entities.add(b);
            b.setOwner(UUID.randomUUID()); consolidate.invoke(service,a);
            check.accept(a.isValid() && b.isValid() && a.getItemStack().getAmount()==1,"different pickup owners never combine");
            b.setOwner(null); listener.veto=a; consolidate.invoke(service,a);
            check.accept(a.isValid() && b.isValid() && b.getItemStack().getAmount()==1,"cancelled merge event preserves both item entities");
            listener.veto=null; consolidate.invoke(service,a);
            check.accept(!a.isValid() && b.getItemStack().getAmount()==2,"compatible ground items become one entity");
        } finally { entities.forEach(Item::remove); service.close(); org.bukkit.event.HandlerList.unregisterAll(listener); }
    }
}
