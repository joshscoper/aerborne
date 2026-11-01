package net.nerdsmp.paper.listener;

import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Progress here is skill experience, so vanilla orbs are never spawned, from any source: mobs, ore, furnaces,
 * fishing, breeding, trades or bottles. Death events keep their dropped amount, because Hunting reads it.
 */
public final class VanillaExperienceGuard implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOrb(EntitySpawnEvent event) {
        if (event.getEntity() instanceof ExperienceOrb) event.setCancelled(true);
    }
}
