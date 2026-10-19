package net.nerdsmp.paper.zone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import java.time.Duration;
import java.util.*;

/** Public hub safety complements existing server-reserve building/container protections. */
public final class ZoneProtection implements Listener {
    private final ZoneStore store;
    private final Map<UUID,String> lastZone=new HashMap<>();
    private final Map<UUID,Long> lastNotice=new HashMap<>();
    public ZoneProtection(ZoneStore store) { this.store=store; }
    private Optional<ZoneDefinition> active(Location at) {
        if(at==null || at.getWorld()==null) return Optional.empty();
        return store.at(at.getWorld().getUID(),at.getX(),at.getY(),at.getZ()).filter(ZoneDefinition::enabled);
    }
    private boolean safe(Location at) { return active(at).filter(ZoneDefinition::safe).isPresent(); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent event) {
        // Explicit admin/NPC/managed spawns are not natural ambient population.
        if(event.getSpawnReason()==CreatureSpawnEvent.SpawnReason.CUSTOM || event.getSpawnReason()==CreatureSpawnEvent.SpawnReason.COMMAND) return;
        if(active(event.getLocation()).isPresent()) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent event) {
        if(event.getTarget() instanceof Player player && safe(player.getLocation())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player player && safe(player.getLocation())
            && event.getCause()!=EntityDamageEvent.DamageCause.VOID
            && event.getCause()!=EntityDamageEvent.DamageCause.SUICIDE
            && event.getCause()!=EntityDamageEvent.DamageCause.KILL) event.setCancelled(true);
        Entity causing=event.getDamageSource().getCausingEntity();
        if(causing instanceof Player attacker) {
            if(safe(attacker.getLocation()) || safe(event.getEntity().getLocation())) event.setCancelled(true);
            if(event.getEntity() instanceof Player victim
                && (active(attacker.getLocation()).isPresent() || active(victim.getLocation()).isPresent())) event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void splash(PotionSplashEvent event) {
        for(LivingEntity entity:event.getAffectedEntities()) if(entity instanceof Player && safe(entity.getLocation())) event.setIntensity(entity,0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void cloud(AreaEffectCloudApplyEvent event) {
        event.getAffectedEntities().removeIf(entity->entity instanceof Player && safe(entity.getLocation()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) { if(event.hasChangedBlock()) announce(event.getPlayer(),event.getTo()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) { announce(event.getPlayer(),event.getTo()); }
    @EventHandler public void quit(PlayerQuitEvent event) { lastZone.remove(event.getPlayer().getUniqueId()); lastNotice.remove(event.getPlayer().getUniqueId()); }
    private void announce(Player player,Location at) {
        var found=active(at); String id=found.map(ZoneDefinition::id).orElse("");
        if(Objects.equals(lastZone.put(player.getUniqueId(),id),id) || found.isEmpty()) return;
        long now=System.currentTimeMillis();
        if(now-lastNotice.getOrDefault(player.getUniqueId(),0L)<3000) return;
        lastNotice.put(player.getUniqueId(),now);
        ZoneDefinition zone=found.get();
        String suffix=zone.safe()?" • Safe area":zone.mobs().isEmpty()?"":" • Lv "+zone.mobs().stream().mapToInt(ZoneDefinition.MobEntry::level).min().orElse(1)
            +"–"+zone.mobs().stream().mapToInt(ZoneDefinition.MobEntry::level).max().orElse(1);
        player.showTitle(Title.title(Component.empty(),Component.text(zone.title()+suffix,zone.safe()?NamedTextColor.AQUA:NamedTextColor.GOLD),
            Title.Times.times(Duration.ofMillis(200),Duration.ofSeconds(2),Duration.ofMillis(400))));
    }
}
