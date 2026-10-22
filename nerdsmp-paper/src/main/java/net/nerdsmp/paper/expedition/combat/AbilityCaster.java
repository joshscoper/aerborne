package net.nerdsmp.paper.expedition.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Casts the ability kit ({@link net.nerdsmp.paper.expedition.content.Abilities}). Every ability shows
 * its warning first (a ring, cone, line or marker plus a sound) and lands a moment later, re-checking
 * that the caster is still alive and the run still live. Damage is dealt as the caster, so the run's
 * tier and boss multipliers apply to it like any other hit.
 */
public final class AbilityCaster {
    /** What the run provides: who to hit, who counts as a friend, and the run-owned side effects. */
    public interface Arena {
        boolean live();
        /** Living party members (not allies) within {@code radius} of the caster. */
        List<Player> targets(LivingEntity caster, double radius);
        /** Encounter mobs near the caster, itself included. */
        List<LivingEntity> friends(LivingEntity caster, double radius);
        /** Spawns helpers next to the caster; the run tracks them like any wave mob. */
        List<LivingEntity> summon(LivingEntity caster, int count);
        /** The caster can't be hurt while any of {@code helpers} lives. */
        void shield(LivingEntity caster, List<LivingEntity> helpers);
        /** Places a block the run removes again after {@code ticks} (fire, webs). */
        void temporary(Block block, Material type, int ticks);
    }

    private final Plugin plugin;
    private final Set<UUID> channeling = new HashSet<>();

    public AbilityCaster(Plugin plugin) { this.plugin = plugin; }

    /** True while the caster is mid-cast; the run waits before starting another. */
    public boolean busy(LivingEntity caster) { return channeling.contains(caster.getUniqueId()); }

    /**
     * Starts an ability. {@code power} scales its damage (elites 1, mini-bosses and bosses more).
     * Returns false when it has nothing to aim at, so the run keeps the cooldown free.
     */
    public boolean cast(String ability, LivingEntity caster, Arena arena, double power) {
        if (!caster.isValid() || caster.isDead() || busy(caster)) return false;
        List<Player> targets = arena.targets(caster, 24);
        if (targets.isEmpty() && !ability.equals("mend")) return false;
        return switch (ability) {
            case "charge" -> charge(caster, arena, targets, power);
            case "cleave" -> cleave(caster, arena, targets, power);
            case "slam" -> slam(caster, arena, power);
            case "leap" -> leap(caster, arena, targets, power);
            case "blink" -> blink(caster, arena, targets);
            case "fire_pools" -> firePools(caster, arena, targets, power);
            case "meteor" -> meteor(caster, arena, targets, power);
            case "summon" -> summon(caster, arena);
            case "shield" -> shield(caster, arena);
            case "frost_nova" -> frostNova(caster, arena, power);
            case "poison_cloud" -> poisonCloud(caster, arena, targets);
            case "tether" -> tether(caster, arena, targets, power);
            case "barrage" -> barrage(caster, arena, targets);
            case "mend" -> mend(caster, arena);
            case "web" -> web(caster, arena, targets);
            case "wrath" -> wrath(caster, arena, power);
            default -> false;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Abilities
    // ---------------------------------------------------------------------------------------------

    private boolean charge(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.stream().max(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(caster.getLocation()))).orElseThrow();
        Location aim = target.getLocation();
        hold(caster, 20);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1F, 1.3F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.line(caster.getLocation(), aim, Telegraph.DANGER), () -> {
            Vector dash = aim.toVector().subtract(caster.getLocation().toVector()).setY(0);
            if (dash.lengthSquared() < 1e-3) return;
            caster.setVelocity(dash.normalize().multiply(1.6).setY(.25));
            Set<UUID> struck = new HashSet<>();
            repeat(caster, arena, 8, 1, tick -> {
                for (Player player : arena.targets(caster, 2.5))
                    if (struck.add(player.getUniqueId())) {
                        hit(player, caster, 6 * power);
                        player.setVelocity(player.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0).normalize().multiply(.9).setY(.45));
                    }
            }, () -> { });
        });
        return true;
    }

    private boolean cleave(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = nearest(caster, targets);
        face(caster, target.getLocation());
        hold(caster, 20);
        Location origin = caster.getLocation();
        caster.getWorld().playSound(origin, Sound.ITEM_TRIDENT_RETURN, 1F, .6F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.cone(origin, 4.5, 110, Telegraph.DANGER), () -> {
            caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK, origin.clone().add(origin.getDirection().setY(0).normalize().multiply(2)).add(0, 1, 0), 6, 1, .3, 1, 0);
            caster.getWorld().playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, .7F);
            for (Player player : arena.targets(caster, 5)) if (Telegraph.inCone(origin, player.getLocation(), 4.8, 115)) hit(player, caster, 7 * power);
        });
        return true;
    }

    private boolean slam(LivingEntity caster, Arena arena, double power) {
        hold(caster, 30);
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1F, 1.2F);
        repeat(caster, arena, 6, 5, tick -> Telegraph.ring(center, 6, Telegraph.DANGER), () -> {
            caster.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 1.5, .2, 1.5, 0);
            caster.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1F, .7F);
            for (Player player : arena.targets(caster, 6.5)) {
                hit(player, caster, 8 * power);
                player.setVelocity(player.getVelocity().setY(.7));
            }
        });
        return true;
    }

    /** A wide ring, then a blast that throws everyone inside outward. Stand at the edge, with your back to safe ground. */
    private boolean wrath(LivingEntity caster, Arena arena, double power) {
        hold(caster, 40);
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.ENTITY_BLAZE_AMBIENT, 1.4F, .5F);
        repeat(caster, arena, 8, 5, tick -> Telegraph.ring(center, 9, Telegraph.DANGER), () -> {
            caster.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 120, 4, .6, 4, .15);
            caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
            caster.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4F, .5F);
            for (Player player : arena.targets(caster, 9.5)) {
                hit(player, caster, 6 * power);
                var push = player.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (push.lengthSquared() < .01) push = new org.bukkit.util.Vector(1, 0, 0);
                player.setVelocity(push.normalize().multiply(1.6).setY(.55));
            }
        });
        return true;
    }

    private boolean leap(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.stream().max(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(caster.getLocation()))).orElseThrow();
        Location landing = target.getLocation();
        hold(caster, 24);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1F, .6F);
        repeat(caster, arena, 5, 5, tick -> Telegraph.disc(landing, 3, Telegraph.DANGER), () -> {
            caster.teleport(landing.clone().setDirection(caster.getLocation().getDirection()));
            caster.getWorld().spawnParticle(Particle.EXPLOSION, landing, 2, .8, .1, .8, 0);
            caster.getWorld().playSound(landing, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1F, .5F);
            for (Player player : arena.targets(caster, 3.2)) {
                hit(player, caster, 6 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
            }
        });
        return true;
    }

    private boolean blink(LivingEntity caster, Arena arena, List<Player> targets) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Location behind = target.getLocation().clone().add(target.getLocation().getDirection().setY(0).normalize().multiply(-2));
        if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) behind = target.getLocation();
        Location to = behind;
        caster.getWorld().spawnParticle(Particle.LARGE_SMOKE, caster.getLocation().add(0, 1, 0), 20, .3, .6, .3, .02);
        repeat(caster, arena, 2, 5, tick -> caster.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 20, .3, .6, .3, .3), () -> {
            caster.teleport(to.clone().setDirection(target.getLocation().toVector().subtract(to.toVector())));
            caster.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, .8F);
            if (caster instanceof Mob mob) mob.setTarget(target);
        });
        return true;
    }

    private boolean firePools(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        List<Location> marks = new ArrayList<>();
        for (Player player : targets.subList(0, Math.min(4, targets.size()))) marks.add(player.getLocation());
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1F, .6F);
        repeat(caster, arena, 6, 5, tick -> marks.forEach(mark -> Telegraph.disc(mark, 2, Telegraph.FIRE)), () -> {
            for (Location mark : marks) {
                mark.getWorld().spawnParticle(Particle.FLAME, mark.clone().add(0, .3, 0), 40, 1.2, .2, 1.2, .02);
                mark.getWorld().playSound(mark, Sound.ITEM_FIRECHARGE_USE, 1F, .8F);
                Block floor = mark.getBlock();
                if (floor.getType().isAir() && floor.getRelative(0, -1, 0).getType().isSolid()) arena.temporary(floor, Material.FIRE, 100);
                for (Player player : arena.targets(caster, 40))
                    if (player.getLocation().distanceSquared(mark) <= 4.4) {
                        hit(player, caster, 5 * power);
                        player.setFireTicks(60);
                    }
            }
        });
        return true;
    }

    private boolean meteor(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        caster.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1F, .5F);
        Location[] mark = {target.getLocation()};
        repeat(caster, arena, 10, 5, tick -> {
            if (target.isValid() && tick < 6) mark[0] = target.getLocation(); // The shadow follows, then locks.
            Telegraph.disc(mark[0], 3, Telegraph.SHADOW);
        }, () -> {
            World world = mark[0].getWorld();
            world.spawnParticle(Particle.EXPLOSION_EMITTER, mark[0], 1);
            world.spawnParticle(Particle.LAVA, mark[0], 30, 1.5, .5, 1.5, 0);
            world.playSound(mark[0], Sound.ENTITY_GENERIC_EXPLODE, 1.4F, .5F);
            List<Player> sharing = new ArrayList<>();
            for (Player player : arena.targets(caster, 60)) if (player.getLocation().distanceSquared(mark[0]) <= 9.6) sharing.add(player);
            // Sharing the impact splits it: alone it's brutal, three players shrug it off.
            double each = 18 * power / Math.max(1, sharing.size());
            for (Player player : sharing) hit(player, caster, each);
        });
        return true;
    }

    private boolean summon(LivingEntity caster, Arena arena) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1F, 1F);
        hold(caster, 20);
        repeat(caster, arena, 4, 5, tick -> Telegraph.ring(caster.getLocation(), 2.5, Telegraph.SHADOW), () -> arena.summon(caster, 2));
        return true;
    }

    private boolean shield(LivingEntity caster, Arena arena) {
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1F, .6F);
        hold(caster, 20);
        repeat(caster, arena, 4, 5, tick -> Telegraph.ring(caster.getLocation(), 1.6, Telegraph.HOLY), () -> {
            List<LivingEntity> helpers = arena.summon(caster, 2);
            if (!helpers.isEmpty()) arena.shield(caster, helpers);
        });
        return true;
    }

    private boolean frostNova(LivingEntity caster, Arena arena, double power) {
        hold(caster, 20);
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.BLOCK_POWDER_SNOW_STEP, 1.5F, .5F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.ring(center, 5, Telegraph.FROST), () -> {
            caster.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 80, 3, .5, 3, .05);
            caster.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1F, .6F);
            for (Player player : arena.targets(caster, 5.5)) {
                hit(player, caster, 4 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                player.setFreezeTicks(Math.max(player.getFreezeTicks(), 120));
            }
        });
        return true;
    }

    private boolean poisonCloud(LivingEntity caster, Arena arena, List<Player> targets) {
        Location at = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITCH_THROW, 1F, .7F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.disc(at, 3, Telegraph.POISON), () ->
            at.getWorld().spawn(at, AreaEffectCloud.class, cloud -> {
                cloud.setRadius(3F);
                cloud.setDuration(160);
                cloud.setRadiusPerTick(0F);
                cloud.setWaitTime(0);
                cloud.setReapplicationDelay(20);
                cloud.setSource(caster);
                cloud.setColor(Telegraph.POISON);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 1), true);
            }));
        return true;
    }

    private boolean tether(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = nearest(caster, targets);
        if (target.getLocation().distanceSquared(caster.getLocation()) > 14 * 14) return false;
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5F, .5F);
        repeat(caster, arena, 6, 5, tick -> Telegraph.line(caster.getEyeLocation().subtract(0, .5, 0), target.getLocation().add(0, 1, 0), Telegraph.SHADOW), () -> {
            if (!target.isValid() || target.isDead() || !caster.hasLineOfSight(target)) {
                caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1F, 1F);
                return; // Broke line of sight: the chain snaps.
            }
            Vector pull = caster.getLocation().toVector().subtract(target.getLocation().toVector());
            target.setVelocity(pull.normalize().multiply(Math.min(1.8, pull.length() / 5)).setY(.4));
            hit(target, caster, 3 * power);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        });
        return true;
    }

    private boolean barrage(LivingEntity caster, Arena arena, List<Player> targets) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1.2F, .7F);
        repeat(caster, arena, 4, 5, tick -> caster.getWorld().spawnParticle(Particle.CRIT, caster.getEyeLocation(), 10, .4, .4, .4, .1), () ->
            repeat(caster, arena, 5, 4, tick -> {
                List<Player> live = arena.targets(caster, 30);
                if (live.isEmpty()) return;
                Player aim = live.get(ThreadLocalRandom.current().nextInt(live.size()));
                Vector velocity = aim.getEyeLocation().subtract(0, .4, 0).toVector().subtract(caster.getEyeLocation().toVector()).normalize().multiply(1.6);
                Arrow arrow = caster.launchProjectile(Arrow.class, velocity);
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setDamage(3);
                caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1F, 1F);
            }, () -> { }));
        return true;
    }

    private boolean mend(LivingEntity caster, Arena arena) {
        List<LivingEntity> wounded = arena.friends(caster, 10).stream().filter(friend -> ratio(friend) < .9).toList();
        if (wounded.isEmpty()) return false;
        double start = caster.getHealth();
        double cap = maxHealth(caster);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1F, 1.2F);
        hold(caster, 40);
        boolean[] broken = {false};
        repeat(caster, arena, 8, 5, tick -> {
            // Burst it down mid-channel to interrupt.
            if (start - caster.getHealth() >= cap * .12) broken[0] = true;
            if (!broken[0]) caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, caster.getLocation().add(0, 1.2, 0), 8, .5, .6, .5, 0);
        }, () -> {
            if (broken[0]) {
                caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, .6F);
                return;
            }
            for (LivingEntity friend : arena.friends(caster, 10)) {
                double max = maxHealth(friend);
                friend.setHealth(Math.min(max, friend.getHealth() + max * .15));
                friend.getWorld().spawnParticle(Particle.HEART, friend.getLocation().add(0, friend.getHeight() + .3, 0), 3, .3, .2, .3, 0);
            }
        });
        return true;
    }

    private boolean web(LivingEntity caster, Arena arena, List<Player> targets) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1F, .5F);
        Location[] at = {target.getLocation()};
        repeat(caster, arena, 3, 5, tick -> { at[0] = target.getLocation(); Telegraph.ring(at[0], 1.2, Telegraph.SHADOW); }, () -> {
            Block feet = at[0].getBlock();
            if (feet.getType().isAir()) arena.temporary(feet, Material.COBWEB, 60);
        });
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Runs {@code each} {@code times} times every {@code period} ticks, then {@code then}; stops if the caster dies or the run ends. */
    private void repeat(LivingEntity caster, Arena arena, int times, int period, java.util.function.IntConsumer each, Runnable then) {
        UUID id = caster.getUniqueId();
        channeling.add(id);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arena.live() || !caster.isValid() || caster.isDead()) {
                channeling.remove(id);
                task.cancel();
                return;
            }
            if (tick[0] < times) {
                each.accept(tick[0]++);
                return;
            }
            task.cancel();
            channeling.remove(id);
            then.run();
        }, 0L, period);
    }

    /** Roots the caster briefly so its warning matches where it strikes. */
    private static void hold(LivingEntity caster, int ticks) {
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false));
    }

    private static void face(LivingEntity caster, Location at) {
        Location look = caster.getLocation().setDirection(at.toVector().subtract(caster.getLocation().toVector()));
        caster.setRotation(look.getYaw(), 0);
    }

    private static Player nearest(LivingEntity caster, List<Player> targets) {
        return targets.stream().min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(caster.getLocation()))).orElseThrow();
    }

    private static void hit(Player player, LivingEntity caster, double amount) {
        if (player.isValid() && !player.isDead() && amount > 0) player.damage(amount, caster);
    }

    private static double maxHealth(LivingEntity entity) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? Math.max(1, entity.getHealth()) : max.getValue();
    }

    private static double ratio(LivingEntity entity) { return entity.getHealth() / maxHealth(entity); }
}
