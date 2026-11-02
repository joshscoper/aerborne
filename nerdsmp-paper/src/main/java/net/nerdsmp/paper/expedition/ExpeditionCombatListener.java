package net.nerdsmp.paper.expedition;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Feeds a run's fight: who hit whom, charged swings, raised shields, taunts and mobs trying to pick their own target. */
final class ExpeditionCombatListener implements Listener {
    /** Swing strength counted as a full, charged hit. */
    private static final float CHARGED = .9F;

    private final ExpeditionService service;
    /** Whether each player's last swing was charged; the cooldown has reset by the time the damage lands. */
    private final Map<UUID, Boolean> swings = new HashMap<>();

    ExpeditionCombatListener(ExpeditionService service) { this.service = service; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PrePlayerAttackEntityEvent event) {
        if (service.runIn(event.getPlayer().getWorld()) != null)
            swings.put(event.getPlayer().getUniqueId(), event.getPlayer().getAttackCooldown() >= CHARGED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        ExpeditionRun run = service.runIn(event.getEntity().getWorld());
        if (run == null) return;
        Entity source = event.getDamager();
        boolean charged = false;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            charged = projectile instanceof AbstractArrow arrow && arrow.isCritical();
            source = shooter;
        } else if (source instanceof Player player) {
            Boolean swing = swings.remove(player.getUniqueId());
            charged = swing != null && swing;
        }
        // A boon comes last of all, after gear and defence have had their say.
        if (event.getEntity() instanceof LivingEntity enemy && run.tracks(enemy) && source instanceof Player) event.setDamage(event.getDamage() * run.boonDealt() * run.exposure(enemy.getUniqueId()) / run.toughness(enemy.getUniqueId()));
        else if (event.getEntity() instanceof Player && source instanceof LivingEntity enemy && run.tracks(enemy)) event.setDamage(event.getDamage() * run.boonTaken());
        if (event.getEntity() instanceof LivingEntity enemy && run.tracks(enemy) && (source instanceof Player || run.isAlly(source)))
            run.enemyHit(source, enemy, event.getFinalDamage(), charged);
        else if (event.getEntity() instanceof Player player && player.isBlocking() && source instanceof LivingEntity enemy && run.tracks(enemy))
            run.fight().blocked(player, enemy, event.getDamage());
    }

    /** A run's enemies fight who their threat says, and never wander into a target on their own. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        ExpeditionRun run = service.runIn(event.getEntity().getWorld());
        if (run == null || !(event.getEntity() instanceof LivingEntity enemy) || !run.tracks(enemy)) return;
        if (run.dormant(enemy) && event.getTarget() != null) event.setCancelled(true);
        else if (event.getTarget() != null && event.getReason() != EntityTargetEvent.TargetReason.CUSTOM) event.setCancelled(true);
    }

    /** Sneak and right-click with a shield to taunt. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTaunt(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking() || !RunCombat.shield(player)) return;
        ExpeditionRun run = service.runIn(player.getWorld());
        if (run != null) run.taunt(player);
    }
}
