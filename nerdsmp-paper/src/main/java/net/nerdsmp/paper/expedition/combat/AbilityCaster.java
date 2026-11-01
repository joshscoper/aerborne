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
        /** Living party members within {@code radius} of a spot, for effects that outlast or don't need a caster. */
        default List<Player> near(Location at, double radius) { return List.of(); }
        /** Encounter mobs near the caster, itself included. */
        List<LivingEntity> friends(LivingEntity caster, double radius);
        /** Spawns helpers next to the caster; the run tracks them like any wave mob. */
        List<LivingEntity> summon(LivingEntity caster, int count);
        /** The caster can't be hurt while any of {@code helpers} lives. */
        void shield(LivingEntity caster, List<LivingEntity> helpers);
        /** Places a block the run removes again after {@code ticks} (fire, webs). */
        void temporary(Block block, Material type, int ticks);
        /** Who the caster is fighting: the top of its threat table. */
        default LivingEntity target(LivingEntity caster) { return null; }
        /** Spawns a mob that doesn't move, tracked by the room, or null if it can't. */
        default LivingEntity fixture(String mob, Location at) { return null; }
    }

    /** How long silk holds a player if nobody breaks the cocoon. */
    private static final int WRAP_TICKS = 120;

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
        if (targets.isEmpty() && !ability.equals("mend") && !ability.equals("ward")) return false;
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
            case "volatile_mark" -> volatileMark(caster, arena, targets, power);
            case "bolt" -> bolt(caster, arena, power);
            case "ward" -> ward(caster, arena);
            case "dread_howl" -> dreadHowl(caster, arena);
            case "rally" -> rally(caster, arena);
            case "heavy_blow" -> heavyBlow(caster, arena, power);
            case "soul_rend" -> soulRend(caster, arena, power);
            case "grave_grasp" -> graveGrasp(caster, arena, targets, power);
            case "pin_shot" -> pinShot(caster, arena, power);
            case "arrow_rain" -> arrowRain(caster, arena, targets, power);
            case "cursed_ground" -> cursedGround(caster, arena, targets, power);
            case "ambush" -> ambush(caster, arena, targets, power);
            case "bone_throw" -> boneThrow(caster, arena, power);
            case "bone_wave" -> boneWave(caster, arena, power);
            case "venom_spit" -> venomSpit(caster, arena, power);
            case "root_snare" -> rootSnare(caster, arena, targets, power);
            case "web_wrap" -> webWrap(caster, arena, targets, power);
            default -> false;
        };
    }

    /** Whether an ability has any use right now, so a mob doesn't start casting it for nothing. */
    public boolean useful(String ability, LivingEntity caster, Arena arena) {
        return switch (ability) {
            case "mend", "ward" -> arena.friends(caster, 10).stream().anyMatch(friend -> ratio(friend) < .8);
            case "rally" -> arena.friends(caster, 10).size() > 1;
            case "bolt", "heavy_blow", "pin_shot", "bone_throw", "venom_spit" -> {
                LivingEntity target = arena.target(caster);
                yield target != null && caster.hasLineOfSight(target);
            }
            default -> true;
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
        boolean floor = !behind.clone().subtract(0, 1, 0).getBlock().isPassable();
        if (!floor || !behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) behind = target.getLocation();
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
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1F, 1.2F);
        caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, caster.getLocation().add(0, 1.2, 0), 16, .5, .6, .5, 0);
        for (LivingEntity friend : arena.friends(caster, 10)) {
            double max = maxHealth(friend);
            friend.setHealth(Math.min(max, friend.getHealth() + max * .15));
            friend.getWorld().spawnParticle(Particle.HEART, friend.getLocation().add(0, friend.getHeight() + .3, 0), 3, .3, .2, .3, 0);
        }
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


    /** Marks a player: the ring follows them for four seconds, then bursts on everyone near them. */
    private boolean volatileMark(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player marked = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        net.nerdsmp.paper.presentation.Chat.bar(marked, "<red>You are marked! Get away from the others!</red>");
        marked.playSound(marked.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1F, .5F);
        repeat(caster, arena, 8, 5, tick -> Telegraph.ring(marked.getLocation(), 4, Telegraph.SHADOW), () -> {
            Location at = marked.getLocation();
            at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 50, 1.6, .5, 1.6, .03);
            at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, .8F, .7F);
            for (Player player : arena.targets(caster, 48)) if (player.getLocation().distanceSquared(at) <= 16) hit(player, caster, 7 * power);
        });
        return true;
    }

    private boolean bolt(LivingEntity caster, Arena arena, double power) {
        LivingEntity target = arena.target(caster);
        if (target == null || !caster.hasLineOfSight(target)) return false;
        Location from = caster.getEyeLocation(), to = target.getEyeLocation();
        Telegraph.line(from, to, Telegraph.SHADOW);
        caster.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, to, 12, .3, .4, .3, .02);
        caster.getWorld().playSound(from, Sound.ENTITY_EVOKER_CAST_SPELL, 1F, 1.3F);
        target.damage(5 * power, caster);
        return true;
    }

    /** The most wounded ally takes far less damage for ten seconds. */
    private boolean ward(LivingEntity caster, Arena arena) {
        LivingEntity ally = arena.friends(caster, 10).stream().min(Comparator.comparingDouble(AbilityCaster::ratio)).orElse(caster);
        ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1, false, true));
        ally.getWorld().spawnParticle(Particle.ENCHANT, ally.getLocation().add(0, 1, 0), 40, .5, .7, .5, .4);
        ally.getWorld().playSound(ally.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1F, 1.5F);
        return true;
    }

    private boolean dreadHowl(LivingEntity caster, Arena arena) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1F, 1.4F);
        Telegraph.ring(caster.getLocation(), 9, Telegraph.SHADOW);
        for (Player player : arena.targets(caster, 9)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
        }
        return true;
    }

    private boolean rally(LivingEntity caster, Arena arena) {
        caster.getWorld().playSound(caster.getLocation(), Sound.EVENT_RAID_HORN, 1F, 1.5F);
        for (LivingEntity friend : arena.friends(caster, 10)) {
            friend.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, true));
            friend.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, friend.getLocation().add(0, friend.getHeight(), 0), 3, .3, .2, .3, 0);
        }
        return true;
    }

    /** A huge hit on its target. A raised shield takes it; anyone who stepped out of reach is missed. */
    private boolean heavyBlow(LivingEntity caster, Arena arena, double power) {
        LivingEntity target = arena.target(caster);
        caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK, caster.getLocation().add(0, 1, 0), 4, .8, .3, .8, 0);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2F, .6F);
        if (target == null || target.getLocation().distanceSquared(caster.getLocation()) > 4.5 * 4.5) return true;
        face(caster, target.getLocation());
        target.damage(12 * power, caster);
        return true;
    }

    private boolean soulRend(LivingEntity caster, Arena arena, double power) {
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2F, .6F);
        caster.getWorld().spawnParticle(Particle.SOUL, caster.getLocation().add(0, 1, 0), 80, 4, 1, 4, .05);
        for (Player player : arena.targets(caster, 24)) {
            hit(player, caster, 8 * power);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
        }
        return true;
    }

    /** Bones burst from the floor where a player stood. The ring holds still, so stepping off it is enough. */
    private boolean graveGrasp(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location at = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        caster.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_BREAK, 1.2F, .6F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.disc(at, 1.9, Telegraph.SHADOW), () -> {
            Telegraph.spikes(plugin, at, Material.BONE_BLOCK, 9, 1.8, 1.7F);
            Telegraph.shards(at, 24, 1, Material.BONE_BLOCK);
            caster.getWorld().playSound(at, Sound.ENTITY_SKELETON_HURT, 1.2F, .6F);
            caster.getWorld().playSound(at, Sound.BLOCK_BONE_BLOCK_PLACE, 1.4F, .7F);
            for (Player player : arena.near(at, 2.1)) {
                hit(player, caster, 4 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 5));
            }
        });
        return true;
    }

    private boolean rootSnare(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location at = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        caster.getWorld().playSound(at, Sound.BLOCK_ROOTS_BREAK, 1.4F, .6F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.disc(at, 1.9, Telegraph.POISON), () -> {
            Telegraph.spikes(plugin, at, Material.DARK_OAK_LOG, 9, 1.8, 1.7F);
            Telegraph.shards(at, 24, 1, Material.DARK_OAK_LEAVES);
            caster.getWorld().playSound(at, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.4F, .6F);
            for (Player player : arena.near(at, 2.1)) {
                hit(player, caster, 4 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5));
            }
        });
        return true;
    }

    /** A green line to the target, then venom down it. Anyone standing on the line is hit. */
    private boolean venomSpit(LivingEntity caster, Arena arena, double power) {
        LivingEntity target = arena.target(caster);
        if (target == null || !caster.hasLineOfSight(target)) return false;
        hold(caster, 24);
        Location from = caster.getEyeLocation();
        Location[] to = {target.getEyeLocation()};
        caster.getWorld().playSound(from, Sound.ENTITY_SPIDER_HURT, 1.2F, .6F);
        repeat(caster, arena, 4, 5, tick -> {
            if (tick < 2 && target.isValid()) to[0] = target.getEyeLocation();
            Telegraph.line(from, to[0], Telegraph.POISON);
        }, () -> {
            caster.getWorld().playSound(from, Sound.ENTITY_LLAMA_SPIT, 1.4F, .6F);
            Vector along = to[0].toVector().subtract(from.toVector());
            double length = along.length();
            along.normalize();
            for (double step = 0; step < length; step += .8) caster.getWorld().spawnParticle(Particle.SPIT, from.clone().add(along.clone().multiply(step)), 1, 0, 0, 0, 0);
            for (Player player : arena.targets(caster, 24)) {
                Vector offset = player.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
                double reach = Math.clamp(offset.dot(along), 0, length);
                if (offset.subtract(along.clone().multiply(reach)).lengthSquared() > 1.6 * 1.6) continue;
                hit(player, caster, 4 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
            }
        });
        return true;
    }

    /** Silk closes round a player the caster isn't fighting. It holds and poisons them until the cocoon at their feet is broken. */
    private boolean webWrap(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        LivingEntity fought = arena.target(caster);
        List<Player> others = targets.stream().filter(player -> player != fought).toList();
        List<Player> pool = others.isEmpty() ? targets : others;
        Player wrapped = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        caster.getWorld().playSound(wrapped.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.4F, .5F);
        net.nerdsmp.paper.presentation.Chat.bar(wrapped, "<red>Silk is closing round you!</red>");
        repeat(caster, arena, 3, 5, tick -> Telegraph.ring(wrapped.getLocation(), 1.4, Telegraph.SHADOW), () -> {
            if (!wrapped.isValid() || wrapped.isDead()) return;
            Location anchor = wrapped.getLocation();
            LivingEntity cocoon = arena.fixture("custom:silk_cocoon", anchor);
            if (cocoon == null) return;
            org.bukkit.entity.BlockDisplay shell = anchor.getWorld().spawn(anchor, org.bukkit.entity.BlockDisplay.class, display -> {
                display.setBlock(Material.COBWEB.createBlockData());
                display.setPersistent(false);
                display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-.7F, 0, -.7F), new org.joml.Quaternionf(), new org.joml.Vector3f(1.4F, 2.2F, 1.4F), new org.joml.Quaternionf()));
            });
            net.nerdsmp.paper.presentation.Chat.bar(wrapped, "<red>You're wrapped!</red> <gray>Break the cocoon at your feet.</gray>");
            caster.getWorld().playSound(anchor, Sound.BLOCK_WOOL_PLACE, 1.4F, .6F);
            int[] tick = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                boolean over = !arena.live() || !cocoon.isValid() || cocoon.isDead() || !wrapped.isValid() || wrapped.isDead() || tick[0] >= WRAP_TICKS;
                if (over) {
                    task.cancel();
                    shell.remove();
                    if (cocoon.isValid()) cocoon.remove();
                    return;
                }
                tick[0] += 5;
                if (wrapped.getLocation().distanceSquared(anchor) > .25) {
                    Location held = anchor.clone();
                    held.setYaw(wrapped.getYaw());
                    held.setPitch(wrapped.getPitch());
                    wrapped.teleport(held);
                }
                if (tick[0] % 20 == 0) {
                    wrapped.damage(1.5 * power);
                    wrapped.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 25, 0));
                }
            }, 0L, 5L);
        });
        return true;
    }

    /** It swells as it dies, then bursts, and a cloud hangs where it fell. */
    private void sporeBurst(Location at, Arena arena) {
        at.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 1.2F, .5F);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arena.live()) { task.cancel(); return; }
            if (tick[0]++ < 4) {
                Telegraph.ring(at, 2.6, Telegraph.POISON);
                Telegraph.shards(at.clone().add(0, .3, 0), 5, .3, Material.BROWN_MUSHROOM_BLOCK);
                return;
            }
            task.cancel();
            Telegraph.shards(at.clone().add(0, .6, 0), 36, 1.4, Material.BROWN_MUSHROOM_BLOCK);
            at.getWorld().playSound(at, Sound.BLOCK_FUNGUS_BREAK, 1.6F, .5F);
            at.getWorld().spawn(at, AreaEffectCloud.class, cloud -> {
                cloud.setRadius(2.6F);
                cloud.setDuration(80);
                cloud.setRadiusPerTick(0F);
                cloud.setWaitTime(0);
                cloud.setReapplicationDelay(20);
                cloud.setColor(Telegraph.POISON);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 50, 0), true);
            });
        }, 0L, 5L);
    }

    /** A thrall comes apart as it falls, and what's left of it flies out a moment later. */
    public void deathThroe(String ability, Location at, Arena arena) {
        if (ability.equals("spore_burst") && !arena.near(at, 12).isEmpty()) sporeBurst(at, arena);
        if (!ability.equals("bone_burst") || arena.near(at, 12).isEmpty()) return;
        at.getWorld().playSound(at, Sound.ENTITY_SKELETON_DEATH, 1F, .7F);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arena.live()) { task.cancel(); return; }
            if (tick[0]++ < 5) {
                Telegraph.ring(at, 2.6, Telegraph.DANGER);
                Telegraph.shards(at.clone().add(0, .3, 0), 6, .3, Material.BONE_BLOCK);
                return;
            }
            task.cancel();
            Telegraph.shards(at.clone().add(0, .6, 0), 40, 1.4, Material.BONE_BLOCK);
            Telegraph.spikes(plugin, at, Material.BONE_BLOCK, 7, 2.2, 1.1F);
            at.getWorld().playSound(at, Sound.ENTITY_SKELETON_HURT, 1.4F, .5F);
            at.getWorld().playSound(at, Sound.BLOCK_DECORATED_POT_SHATTER, 1.4F, .6F);
            for (Player player : arena.near(at, 2.8)) {
                player.damage(3);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
            }
        }, 0L, 5L);
    }

    /** A red line pins the target down, then a heavy arrow flies straight along it. It locks a moment before the shot. */
    private boolean pinShot(LivingEntity caster, Arena arena, double power) {
        LivingEntity target = arena.target(caster);
        if (target == null || !caster.hasLineOfSight(target)) return false;
        hold(caster, 28);
        Location from = caster.getEyeLocation();
        Location[] to = {target.getEyeLocation()};
        caster.getWorld().playSound(from, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.3F, .6F);
        repeat(caster, arena, 5, 5, tick -> {
            if (tick < 3 && target.isValid()) to[0] = target.getEyeLocation();
            Telegraph.line(from, to[0], Telegraph.DANGER);
        }, () -> {
            Arrow arrow = caster.launchProjectile(Arrow.class, to[0].toVector().subtract(from.toVector()).normalize().multiply(2.8));
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(5 * power);
            arrow.setCritical(true);
            arrow.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2), true);
            caster.getWorld().playSound(from, Sound.ITEM_CROSSBOW_SHOOT, 1.3F, .7F);
        });
        return true;
    }

    /** Arrows fall across a marked ring a moment after it's drawn. */
    private boolean arrowRain(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location at = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        hold(caster, 20);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.2F, .5F);
        repeat(caster, arena, 7, 5, tick -> Telegraph.disc(at, 3.8, Telegraph.DANGER), () -> repeat(caster, arena, 6, 2, tick -> {
            var random = ThreadLocalRandom.current();
            for (int shaft = 0; shaft < 3; shaft++) {
                double angle = random.nextDouble(Math.PI * 2), distance = Math.sqrt(random.nextDouble()) * 3.6;
                Location top = at.clone().add(Math.cos(angle) * distance, 4.5, Math.sin(angle) * distance);
                Arrow arrow = at.getWorld().spawnArrow(top, new Vector(0, -1, 0), 1.6F, 2F);
                arrow.setShooter(caster);
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setDamage(2.5 * power);
            }
            at.getWorld().playSound(at, Sound.ENTITY_ARROW_HIT, .9F, .8F);
        }, () -> { }));
        return true;
    }

    /** Soul fire pools under a player and keeps burning after they've moved, and after the caster has fallen. */
    private boolean cursedGround(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location at = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1F, .7F);
        repeat(caster, arena, 3, 5, tick -> Telegraph.disc(at, 2.8, Telegraph.SHADOW), () -> {
            at.getWorld().playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 1.4F, .6F);
            int[] tick = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!arena.live() || tick[0]++ >= 14) { task.cancel(); return; }
                Telegraph.ring(at, 2.8, Telegraph.FROST);
                at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, .2, 0), 16, 1.4, .1, 1.4, .01);
                if (tick[0] % 2 == 0) for (Player player : arena.near(at, 3)) {
                    hit(player, caster, 1.5 * power);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1));
                }
            }, 0L, 10L);
        });
        return true;
    }

    /** Fades out, then steps out behind its target with a moment's warning, and strikes. */
    private boolean ambush(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        caster.getWorld().spawnParticle(Particle.LARGE_SMOKE, caster.getLocation().add(0, 1, 0), 26, .4, .7, .4, .03);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2F, .7F);
        caster.setInvisible(true);
        if (caster instanceof Mob mob) mob.setAware(false);
        repeat(caster, arena, 5, 5, tick -> { }, () -> {
            Location at = target.getLocation();
            Location behind = at.clone().add(at.getDirection().setY(0).normalize().multiply(-1.6));
            if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) behind = at;
            Location spot = behind;
            caster.teleport(spot.clone().setDirection(at.toVector().subtract(spot.toVector())));
            caster.setInvisible(false);
            caster.getWorld().spawnParticle(Particle.PORTAL, spot.clone().add(0, 1, 0), 30, .3, .6, .3, .4);
            caster.getWorld().playSound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, .7F);
            repeat(caster, arena, 2, 5, tick -> Telegraph.ring(spot, 2.5, Telegraph.DANGER), () -> {
                if (caster instanceof Mob mob) {
                    mob.setAware(true);
                    mob.setTarget(target);
                }
                caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK, caster.getLocation().add(0, 1, 0), 3, .6, .3, .6, 0);
                caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.3F, .7F);
                if (target.isValid() && target.getLocation().distanceSquared(spot) <= 6.25) {
                    hit(target, caster, 7 * power);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 50, 0));
                }
            });
        });
        return true;
    }

    /** A mass of bone sails over and lands on the marked spot. */
    private boolean boneThrow(LivingEntity caster, Arena arena, double power) {
        LivingEntity target = arena.target(caster);
        if (target == null || !caster.hasLineOfSight(target)) return false;
        hold(caster, 24);
        face(caster, target.getLocation());
        Location landing = target.getLocation(), start = caster.getEyeLocation();
        caster.getWorld().playSound(start, Sound.ENTITY_WITCH_THROW, 1.3F, .5F);
        var mass = caster.getWorld().spawn(start, org.bukkit.entity.BlockDisplay.class, display -> {
            display.setBlock(Material.BONE_BLOCK.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(5);
            display.setBrightness(new org.bukkit.entity.Display.Brightness(12, 12));
            display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-.5F, -.5F, -.5F),
                new org.joml.Quaternionf().rotateXYZ(.6F, .5F, .3F), new org.joml.Vector3f(1, 1, 1), new org.joml.Quaternionf()));
        });
        repeat(caster, arena, 5, 5, tick -> {
            Telegraph.disc(landing, 2.6, Telegraph.DANGER);
            double flown = (tick + 1) / 5D;
            mass.teleport(start.clone().add(landing.toVector().subtract(start.toVector()).multiply(flown)).add(0, Math.sin(flown * Math.PI) * 2.5, 0));
        }, () -> {
            mass.remove();
            Telegraph.shards(landing.clone().add(0, .4, 0), 40, 1.3, Material.BONE_BLOCK);
            Telegraph.spikes(plugin, landing, Material.BONE_BLOCK, 6, 1.6, .9F);
            landing.getWorld().playSound(landing, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.3F, .5F);
            for (Player player : arena.near(landing, 2.8)) {
                hit(player, caster, 6 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
            }
        });
        return true;
    }

    /** A ring of bone rolls out across the floor; it only catches those standing on the ground as it passes. */
    private boolean boneWave(LivingEntity caster, Arena arena, double power) {
        hold(caster, 30);
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2F, .7F);
        repeat(caster, arena, 5, 5, tick -> Telegraph.ring(center, 1 + tick, Telegraph.DANGER), () -> {
            caster.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1F, .6F);
            double[] radius = {1};
            Set<UUID> struck = new HashSet<>();
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                radius[0] += .55;
                if (!arena.live() || radius[0] > 16) { task.cancel(); return; }
                for (int index = 0; index < 18; index++) {
                    double angle = index * Math.PI / 9;
                    Telegraph.shards(center.clone().add(Math.cos(angle) * radius[0], .25, Math.sin(angle) * radius[0]), 2, .15, Material.BONE_BLOCK);
                }
                for (Player player : arena.near(center, radius[0] + 1.2)) {
                    double out = Math.hypot(player.getX() - center.getX(), player.getZ() - center.getZ());
                    if (Math.abs(out - radius[0]) > .9 || player.getY() - center.getY() > 1.5 || !((org.bukkit.entity.Entity) player).isOnGround() || !struck.add(player.getUniqueId())) continue;
                    hit(player, caster, 6 * power);
                    player.setVelocity(player.getVelocity().setY(.45));
                }
            }, 0L, 1L);
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
