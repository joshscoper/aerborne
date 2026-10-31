package net.nerdsmp.paper.item;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.anticheat.ArcaneEffectScope;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes item abilities happen: on a hit, on a right-click, or for as long as an item is worn. They only
 * touch hostile mobs, never players or anything invulnerable, so they work anywhere and can't start a
 * fight. Their own damage doesn't set off further abilities.
 */
public final class ItemAbilityService implements Listener, AutoCloseable {
    private static final double MARK_BONUS = 1.15;
    private static final long MARK_MILLIS = 6_000;
    private static final int MARK_TICKS = 120, CHECK_TICKS = 20;

    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private final Map<UUID, Map<ItemAbility, Long>> cooldowns = new HashMap<>();
    /** Who's marked, by the player who marked them, and until when. */
    private final Map<UUID, Map<UUID, Long>> marks = new HashMap<>();
    private BukkitTask task;

    public ItemAbilityService(NerdSmpPlugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::worn), CHECK_TICKS, CHECK_TICKS);
    }

    @Override public void close() {
        if (task != null) task.cancel();
    }

    private static boolean hostile(Entity entity) {
        return entity instanceof Enemy && entity instanceof LivingEntity living && !living.isDead() && !living.isInvulnerable();
    }

    private List<LivingEntity> enemies(Location at, double radius) {
        return at.getWorld().getNearbyLivingEntities(at, radius, Math.max(2, radius / 2), radius).stream()
            .filter(ItemAbilityService::hostile).filter(enemy -> enemy.getLocation().distanceSquared(at) <= radius * radius).toList();
    }

    /** Ability damage is the player's, but not a swing: weapon stats and reach don't apply, and it sets off nothing further. */
    private void strike(Player player, LivingEntity enemy, double amount) {
        ArcaneEffectScope.run(player.getUniqueId(), "item-ability", enemy.getUniqueId(), amount * MARK_BONUS * 2, () -> {
            enemy.damage(amount, player);
            return true;
        });
    }

    private boolean ready(Player player, ItemAbility ability) {
        return cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(ability, 0L) <= System.currentTimeMillis();
    }

    private void spent(Player player, ItemAbility ability) {
        cooldowns.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(ItemAbility.class)).put(ability, System.currentTimeMillis() + ability.cooldownSeconds() * 1000L);
    }

    private double weaponDamage(ItemStack weapon) {
        return items.gear(weapon).map(gear -> Math.max(gear.stat("damage"), gear.stat("projectile-damage"))).filter(damage -> damage > 0).orElse(4D);
    }

    // ---------------------------------------------------------------------------------------------
    // Hits
    // ---------------------------------------------------------------------------------------------

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        return damager instanceof Projectile shot && shot.getShooter() instanceof Player player ? player : null;
    }

    /** A marked enemy takes more from whoever marked it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarked(EntityDamageByEntityEvent event) {
        Player player = attacker(event.getDamager());
        if (player == null) return;
        Map<UUID, Long> on = marks.get(event.getEntity().getUniqueId());
        if (on == null) return;
        if (on.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) event.setDamage(event.getDamage() * MARK_BONUS);
        else on.remove(player.getUniqueId());
        if (on.isEmpty()) marks.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player player = attacker(event.getDamager());
        if (player == null || ArcaneEffectScope.inside() || !(event.getEntity() instanceof LivingEntity enemy) || !hostile(enemy)) return;
        for (ItemAbility ability : items.abilities(player.getInventory().getItemInMainHand())) {
            if (ability.trigger() != ItemAbility.Trigger.HIT || !ready(player, ability)) continue;
            if (ThreadLocalRandom.current().nextInt(100) >= ability.chance()) continue;
            spent(player, ability);
            hit(ability, player, enemy, event.getFinalDamage());
        }
    }

    private void hit(ItemAbility ability, Player player, LivingEntity enemy, double damage) {
        World world = enemy.getWorld();
        Location body = enemy.getLocation().add(0, enemy.getHeight() / 2, 0);
        switch (ability) {
            case SOUL_SIPHON -> {
                var maximum = player.getAttribute(Attribute.MAX_HEALTH);
                if (maximum != null) player.setHealth(Math.min(maximum.getValue(), player.getHealth() + maximum.getValue() * .06));
                Vector path = player.getEyeLocation().subtract(body).toVector().multiply(.2);
                for (int step = 0; step <= 5; step++) world.spawnParticle(Particle.SOUL, body.clone().add(path.clone().multiply(step)), 2, .05, .05, .05, .01);
                world.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.2F, 1.1F);
            }
            case BONE_SHARDS -> {
                world.spawnParticle(Particle.BLOCK, body, 30, .8, .5, .8, 0, Material.BONE_BLOCK.createBlockData());
                world.playSound(body, Sound.BLOCK_BONE_BLOCK_BREAK, 1.3F, .8F);
                for (LivingEntity near : enemies(enemy.getLocation(), 3.5)) if (near != enemy) strike(player, near, damage * .4);
            }
            case EMBER_BRAND -> {
                enemy.setFireTicks(Math.max(enemy.getFireTicks(), 80));
                world.spawnParticle(Particle.FLAME, body, 18, .4, .6, .4, .03);
                world.playSound(body, Sound.ITEM_FIRECHARGE_USE, .8F, 1.2F);
            }
            case GRAVE_CHILL -> {
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                world.spawnParticle(Particle.SNOWFLAKE, body, 20, .4, .6, .4, .02);
                world.playSound(body, Sound.BLOCK_POWDER_SNOW_BREAK, 1.2F, .7F);
            }
            case THUNDERCLAP -> {
                world.strikeLightningEffect(enemy.getLocation());
                for (LivingEntity near : enemies(enemy.getLocation(), 3.5)) strike(player, near, damage * .6);
            }
            case HUNTERS_MARK -> {
                marks.computeIfAbsent(enemy.getUniqueId(), id -> new HashMap<>()).put(player.getUniqueId(), System.currentTimeMillis() + MARK_MILLIS);
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, MARK_TICKS, 0, false, false));
                world.playSound(body, Sound.ITEM_CROSSBOW_HIT, 1.2F, .8F);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Right-clicks
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        // A right-click on a chest or lever means the chest or lever.
        if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable() && !player.isSneaking()) return;
        for (ItemAbility ability : items.abilities(event.getItem())) {
            if (ability.trigger() != ItemAbility.Trigger.USE) continue;
            if (!ready(player, ability)) {
                long left = cooldowns.get(player.getUniqueId()).get(ability) - System.currentTimeMillis();
                Chat.bar(player, "<gray>" + ability.displayName() + " ready in " + (left + 999) / 1000 + "s</gray>");
                return;
            }
            if (use(ability, player, event.getItem())) {
                spent(player, ability);
                Chat.bar(player, "<gold>" + ability.displayName() + "</gold>");
            }
            return;
        }
    }

    private boolean use(ItemAbility ability, Player player, ItemStack weapon) {
        World world = player.getWorld();
        Location at = player.getLocation();
        double damage = weaponDamage(weapon);
        switch (ability) {
            case EMBER_SWEEP -> {
                Vector facing = at.getDirection().setY(0);
                if (facing.lengthSquared() < 1e-6) return false;
                facing.normalize();
                for (double reach = 1.5; reach <= 6; reach += 1.5) for (double angle = -55; angle <= 55; angle += 15) {
                    Location flame = at.clone().add(0, 1, 0).add(facing.clone().rotateAroundY(Math.toRadians(angle)).multiply(reach));
                    world.spawnParticle(Particle.FLAME, flame, 3, .15, .25, .15, .02);
                }
                world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.2F, .8F);
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, .7F);
                for (LivingEntity enemy : enemies(at, 6.5)) {
                    Vector to = enemy.getLocation().toVector().subtract(at.toVector()).setY(0);
                    if (to.lengthSquared() > .25 && Math.toDegrees(facing.angle(to)) > 62 || !player.hasLineOfSight(enemy)) continue;
                    strike(player, enemy, damage * 1.5);
                    enemy.setFireTicks(Math.max(enemy.getFireTicks(), 60));
                }
            }
            case SOUL_NOVA -> {
                for (double radius = 1; radius <= 5; radius += 1) for (double angle = 0; angle < Math.TAU; angle += Math.PI / (radius * 4))
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(Math.cos(angle) * radius, .3, Math.sin(angle) * radius), 1, 0, .05, 0, .01);
                world.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2F, .7F);
                world.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1.6F, .6F);
                for (LivingEntity enemy : enemies(at, 5.5)) {
                    strike(player, enemy, damage * 1.2);
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                }
            }
            case SHADOW_STEP -> {
                Location eye = player.getEyeLocation();
                Vector facing = eye.getDirection().setY(0);
                if (facing.lengthSquared() < 1e-6) return false;
                facing.normalize();
                RayTraceResult blocked = world.rayTraceBlocks(eye, facing, 8);
                double reach = blocked == null ? 8 : Math.max(0, eye.toVector().distance(blocked.getHitPosition()) - .8);
                Location to = at.clone().add(facing.clone().multiply(reach));
                while (reach > 1 && !(to.getBlock().isPassable() && to.clone().add(0, 1, 0).getBlock().isPassable())) {
                    reach -= .5;
                    to = at.clone().add(facing.clone().multiply(reach));
                }
                if (reach <= 1) return false;
                world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 18, .3, .6, .3, .02);
                player.teleport(to);
                world.spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 30, .3, .6, .3, .4);
                world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.2F);
            }
            case BULWARK -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 2, false, false));
                world.spawnParticle(Particle.ENCHANT, at.clone().add(0, 1, 0), 40, .5, .7, .5, .4);
                world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1F, 1.4F);
            }
            default -> { return false; }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Worn
    // ---------------------------------------------------------------------------------------------

    /** Once a second, what a player's gear is doing for them. */
    private void worn(Player player) {
        if (player.isDead() || player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;
        Set<ItemAbility> active = EnumSet.noneOf(ItemAbility.class);
        for (ItemStack item : plugin.gearUpgrades().worn(player)) for (ItemAbility ability : items.abilities(item))
            if (ability.trigger() == ItemAbility.Trigger.EQUIP) active.add(ability);
        for (ItemAbility ability : active) if (ready(player, ability)) passive(ability, player);
    }

    private void passive(ItemAbility ability, Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        switch (ability) {
            case EMBER_AURA -> {
                List<LivingEntity> enemies = enemies(at, 3);
                if (enemies.isEmpty()) return;
                spent(player, ability);
                world.spawnParticle(Particle.FLAME, at.clone().add(0, .4, 0), 14, 1.4, .1, 1.4, .01);
                for (LivingEntity enemy : enemies) {
                    strike(player, enemy, 2);
                    enemy.setFireTicks(Math.max(enemy.getFireTicks(), 40));
                }
            }
            case GRAVE_SIGHT -> keep(player, PotionEffectType.NIGHT_VISION, 300, 0);
            case FLEET_STRIDE -> keep(player, PotionEffectType.SPEED, 60, 0);
            case LAST_STAND -> {
                var maximum = player.getAttribute(Attribute.MAX_HEALTH);
                if (maximum == null || player.getHealth() >= maximum.getValue() * .3) return;
                spent(player, ability);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, false));
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 30, .4, .6, .4, .2);
                world.playSound(at, Sound.ITEM_TOTEM_USE, .6F, 1.3F);
                Chat.bar(player, "<gold>Last Stand</gold>");
            }
            default -> { }
        }
    }

    /** Tops up an effect only as it runs low, so its icon doesn't flicker. */
    private static void keep(Player player, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null || current.getDuration() < ticks / 2 && current.getAmplifier() <= amplifier)
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, false));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cooldowns.remove(event.getPlayer().getUniqueId()); }
}
