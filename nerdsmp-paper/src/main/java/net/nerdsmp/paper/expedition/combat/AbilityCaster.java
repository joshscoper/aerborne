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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        /** Shields as {@link #shield}, then leaves the caster taking double for this long when it breaks. */
        default void shield(LivingEntity caster, List<LivingEntity> helpers, int exposedSeconds) { shield(caster, helpers); }
        /** How far this room's bosses can see to cast at. */
        default double reach() { return 24; }
        /** Spawns a room mob at a spot, free to move. */
        default LivingEntity spawn(String mob, Location at) { return null; }
        /** Holds one of the caster's body's arms up or down, in radians from hanging; side 0 lets both go. */
        default void strike(LivingEntity caster, int side, float arm) { }
        /** The room's marked spots, past the boss's own. */
        default List<Location> marks() { return List.of(); }
    }

    /** Frost's patch of ice: how wide it is and how long it stays. */
    private static final double ICE_RADIUS = 2.4;
    private static final int ICE_TICKS = 100;
    /** How long silk holds a player if nobody breaks the cocoon. */
    private static final int WRAP_TICKS = 120;
    private static final double ARC_REACH = 6, PULSE_RADIUS = 6, SPEAR_RADIUS = 2.2, SPEAR_BURST = 5, BLADE_REACH = 15, SHARE_RADIUS = 6, AEGIS_RADIUS = 4.5, BRAND_RADIUS = 3.6;
    private static final int SPEAR_STANDS = 50, BLADE_TICKS = 18, CHASE_STEPS = 28, BRAND_STEPS = 16;
    private static final double WELL_PULL = 11, WELL_CORE = 3, RIFT_RADIUS = 2.6, BOND_APART = 14, HARVEST_GAP = .28, STEP_BURST = 4.5;
    private static final int RIFT_PULSES = 16, HARVEST_TICKS = 120, HARVEST_SPACE = 26;
    private static final double SMASH_OFFSET = 13, SMASH_LENGTH = 52, SMASH_HALF = 5, METEOR_SPEED = .17, BINDING_RADIUS = 5, BINDING_SHARE = .1;
    private static final int SONS = 6, METEOR_TICKS = 240;
    private static final String[] BINDING_NAMES = {"<gold><bold>The ember plate!</bold></gold>", "<aqua><bold>The frost plate!</bold></aqua>", "<green><bold>The stone plate!</bold></green>"};
    private static final double GEYSER_RADIUS = 3.2, SPOUT_REACH = 5, WAVE_REACH = 34, VINE_REACH = 10, SLIDE_REACH = 26, SILT_BURST = 4.5;
    private static final int EXPOSED_SECONDS = 12, PRESSURE_STEPS = 20;
    private static final String[] ECHOES = {"custom:echo_of_ember", "custom:echo_of_frost", "custom:echo_of_stone"};
    private static final org.bukkit.Color[] SEALS = {Telegraph.FIRE, Telegraph.FROST, Telegraph.POISON};
    private static final String[] SEAL_NAMES = {"<gold><bold>Branded by Ember!</bold></gold> <gray>Reach the fire plate.</gray>",
        "<aqua><bold>Branded by Frost!</bold></aqua> <gray>Reach the ice plate.</gray>", "<green><bold>Branded by Stone!</bold></green> <gray>Reach the stone plate.</gray>"};

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
        List<Player> targets = arena.targets(caster, arena.reach());
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
            case "gust_shot" -> gustShot(caster, arena, targets, power);
            case "ember_burst" -> emberBurst(caster, arena, power);
            case "frost_bolt" -> frostBolt(caster, arena, targets, power);
            case "arc_bolt" -> arcBolt(caster, arena, targets, power);
            case "charged_pulse" -> chargedPulse(caster, arena, power);
            case "spear_fall" -> spearFall(caster, arena, targets, power);
            case "dancing_blade" -> dancingBlade(caster, arena, targets, power);
            case "rime_share" -> rimeShare(caster, arena, power);
            case "hoarfrost_chase" -> hoarfrostChase(caster, arena, targets, power);
            case "cataclysm" -> cataclysm(caster, arena, power);
            case "sigil_brand" -> sigilBrand(caster, arena, targets, power);
            case "echo_ward" -> echoWard(caster, arena);
            case "void_bolt" -> voidBolt(caster, targets, power);
            case "gravity_well" -> gravityWell(caster, arena, targets, power);
            case "void_rift" -> voidRift(caster, arena, targets, power);
            case "void_bond" -> voidBond(caster, arena, targets, power);
            case "eye_of_the_void" -> voidEyes(caster, arena);
            case "black_harvest" -> blackHarvest(caster, arena, power);
            case "void_step" -> voidStep(caster, arena, power);
            case "drone_ward" -> droneWard(caster, arena);
            case "geyser" -> geyser(caster, arena, targets, power);
            case "waterspout" -> waterspout(caster, arena, power);
            case "tidal_wave" -> tidalWave(caster, arena, power);
            case "overpressure" -> overpressure(caster, arena, power);
            case "binding_vines" -> bindingVines(caster, arena, targets, power);
            case "mudslide" -> mudslide(caster, arena, targets, power);
            case "awaken_silt" -> awakenSilt(caster, arena, targets, power);
            case "sovereign_smash" -> sovereignSmash(caster, arena, power);
            case "engulfing_flames" -> engulfingFlames(caster, arena, power);
            case "sons_of_flame" -> sonsOfFlame(caster, arena, power);
            case "living_meteor" -> livingMeteor(caster, arena, targets, power);
            case "binding_chain" -> bindingChain(caster, arena, power);
            case "drake_breath" -> drakeBreath(caster, arena, targets, power);
            case "ashen_decree" -> ashenDecree(caster, arena, targets, power);
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

    /** Something slow and visible flies from the caster to a spot, stopping at walls; {@code landed} gets where it stopped. */
    private void fly(LivingEntity caster, Arena arena, Location to, double speed, java.util.function.Consumer<Location> trail, java.util.function.Predicate<Location> arrive, java.util.function.Consumer<Location> landed) {
        Location at = caster.getEyeLocation();
        Vector along = to.toVector().subtract(at.toVector());
        double length = Math.min(30, along.length());
        along.normalize().multiply(speed);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arena.live()) { task.cancel(); return; }
            at.add(along);
            trail.accept(at);
            if (++tick[0] * speed >= length || at.getBlock().getType().isSolid() || arrive.test(at)) {
                task.cancel();
                landed.accept(at);
            }
        }, 1L, 1L);
    }

    /** A slow ball of wind. It pushes, it doesn't throw: a gust that hits you near an edge shouldn't put you over it. */
    private boolean gustShot(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BREEZE_INHALE, 1.2F, 1.4F);
        Location aim = target.getLocation().add(0, 1, 0);
        Vector push = aim.toVector().subtract(caster.getEyeLocation().toVector()).setY(0);
        if (push.lengthSquared() > 1e-3) push.normalize().multiply(.42).setY(.16);
        Set<UUID> struck = new HashSet<>();
        fly(caster, arena, aim.clone().add(aim.clone().subtract(caster.getEyeLocation()).toVector().normalize().multiply(6)), .42, at -> {
            at.getWorld().spawnParticle(Particle.CLOUD, at, 4, .18, .18, .18, 0);
            at.getWorld().spawnParticle(Particle.GUST, at, 1, 0, 0, 0, 0);
        }, at -> {
            boolean hit = false;
            for (Player player : arena.near(at, 1.5)) if (struck.add(player.getUniqueId())) {
                hit(player, caster, 1.5 * power);
                player.setVelocity(push);
                hit = true;
            }
            return hit;
        }, at -> at.getWorld().playSound(at, Sound.ENTITY_BREEZE_WIND_BURST, .9F, 1.5F));
        return true;
    }

    /** It glows and a ring shows the reach, then a small burst of flame. */
    private boolean emberBurst(LivingEntity caster, Arena arena, double power) {
        hold(caster, 26);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 26, 0, false, false));
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.4F, 1.4F);
        repeat(caster, arena, 5, 5, tick -> {
            Telegraph.ring(center, 2.8, Telegraph.FIRE);
            caster.getWorld().spawnParticle(Particle.SMALL_FLAME, center.clone().add(0, .5, 0), 8, .4, .3, .4, .01);
        }, () -> {
            caster.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, .4, 0), 40, 1.2, .3, 1.2, .08);
            caster.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1F, 1.5F);
            for (Player player : arena.near(center, 3)) {
                hit(player, caster, 3 * power);
                player.setFireTicks(20);
            }
        });
        return true;
    }

    /** Lightning off one player and on to anyone standing close: spread out and it stops at one. */
    private boolean arcBolt(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player first = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        hold(caster, 14);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, .5F, 1.8F);
        repeat(caster, arena, 3, 5, tick -> Telegraph.line(caster.getLocation().add(0, 1, 0), first.getLocation().add(0, 1, 0), Telegraph.FROST), () -> {
            Set<UUID> struck = new HashSet<>();
            Location from = caster.getLocation().add(0, 1, 0);
            Player next = first;
            for (int jump = 0; jump < 4 && next != null; jump++) {
                struck.add(next.getUniqueId());
                Location to = next.getLocation().add(0, 1, 0);
                Vector step = to.toVector().subtract(from.toVector());
                for (double along = 0; along <= 1; along += .1) caster.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, from.clone().add(step.clone().multiply(along)), 2, .08, .08, .08, 0);
                caster.getWorld().playSound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, .8F, 1.4F);
                hit(next, caster, 3 * power);
                from = to;
                Player found = null;
                for (Player player : arena.near(to, ARC_REACH)) if (!struck.contains(player.getUniqueId())) found = player;
                next = found;
            }
        });
        return true;
    }

    /** A ring closes round the caster, and everything left inside it is thrown back. */
    private boolean chargedPulse(LivingEntity caster, Arena arena, double power) {
        hold(caster, 24);
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2F, 1.6F);
        repeat(caster, arena, 5, 5, tick -> Telegraph.ring(center, PULSE_RADIUS, Telegraph.FROST), () -> {
            caster.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1, 0), 80, 2.5, .6, 2.5, .2);
            caster.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2F, 1.2F);
            for (Player player : arena.near(center, PULSE_RADIUS)) {
                hit(player, caster, 3 * power);
                player.setVelocity(player.getLocation().toVector().subtract(center.toVector()).setY(0).normalize().multiply(1.1).setY(.4));
            }
        });
        return true;
    }

    /** Spears of light come down on marked spots, stand a moment, and burst. Don't stay near one. */
    private boolean spearFall(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        List<Location> spots = new ArrayList<>();
        for (Player player : targets.subList(0, Math.min(3, targets.size()))) spots.add(player.getLocation());
        hold(caster, 16);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6F, .6F);
        repeat(caster, arena, 4, 5, tick -> { for (Location spot : spots) Telegraph.disc(spot, SPEAR_RADIUS, Telegraph.HOLY); }, () -> {
            for (Location spot : spots) {
                BlockDisplay spear = caster.getWorld().spawn(spot.clone().add(0, 14, 0), BlockDisplay.class, display -> {
                    display.setBlock(Material.GOLD_BLOCK.createBlockData());
                    display.setPersistent(false);
                    display.setTeleportDuration(4);
                    display.setBrightness(new Display.Brightness(15, 15));
                    display.setTransformation(new Transformation(new Vector3f(-.2F, 0, -.2F), new Quaternionf(), new Vector3f(.4F, 6, .4F), new Quaternionf()));
                });
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (spear.isValid()) spear.teleport(spot.clone().subtract(0, 1.5, 0)); }, 2L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!arena.live()) { spear.remove(); return; }
                    caster.getWorld().playSound(spot, Sound.ITEM_TRIDENT_THUNDER, 1F, 1.4F);
                    caster.getWorld().spawnParticle(Particle.END_ROD, spot.clone().add(0, 1, 0), 30, .6, 1, .6, .05);
                    for (Player player : arena.near(spot, SPEAR_RADIUS)) hit(player, caster, 3 * power);
                }, 7L);
                int[] tick = {0};
                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                    if (!arena.live() || (tick[0] += 5) > SPEAR_STANDS) {
                        task.cancel();
                        if (!arena.live()) { spear.remove(); return; }
                        spear.remove();
                        caster.getWorld().spawnParticle(Particle.FIREWORK, spot.clone().add(0, 1, 0), 60, 1.4, .8, 1.4, .12);
                        caster.getWorld().playSound(spot, Sound.BLOCK_GLASS_BREAK, 1.4F, .7F);
                        for (Player player : arena.near(spot, SPEAR_BURST)) hit(player, caster, 2 * power);
                        return;
                    }
                    Telegraph.ring(spot, SPEAR_BURST, Telegraph.HOLY);
                }, 12L, 5L);
            }
        });
        return true;
    }

    /** A blade thrown spinning down a marked line, and it comes back the same way. */
    private boolean dancingBlade(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Location from = caster.getLocation().add(0, 1, 0);
        Vector along = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector()).setY(0);
        if (along.lengthSquared() < 1e-3) return false;
        along.normalize();
        Location end = from.clone().add(along.clone().multiply(BLADE_REACH));
        hold(caster, 18);
        caster.getWorld().playSound(from, Sound.ITEM_TRIDENT_RETURN, 1.4F, .7F);
        repeat(caster, arena, 4, 5, tick -> Telegraph.line(from, end, Telegraph.DANGER), () -> {
            ItemDisplay blade = caster.getWorld().spawn(from, ItemDisplay.class, display -> {
                display.setItemStack(new ItemStack(Material.GOLDEN_SWORD));
                display.setPersistent(false);
                display.setTeleportDuration(1);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(3, 3, 3), new Quaternionf()));
            });
            Set<UUID> struck = new HashSet<>();
            int[] tick = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                int now = tick[0]++;
                if (!arena.live() || now >= BLADE_TICKS * 2) {
                    blade.remove();
                    task.cancel();
                    return;
                }
                double out = (now < BLADE_TICKS ? now : BLADE_TICKS * 2 - now) / (double) BLADE_TICKS;
                Location at = from.clone().add(along.clone().multiply(BLADE_REACH * out));
                at.setYaw(now * 50F);
                blade.teleport(at);
                caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1);
                if (now == BLADE_TICKS) struck.clear();
                for (Player player : arena.near(at, 1.7)) if (struck.add(player.getUniqueId())) hit(player, caster, 3 * power);
            }, 0L, 1L);
        });
        return true;
    }

    /** The cold is shared out among everyone standing in the ring, so together it's a chill and alone it's a killing frost. */
    private boolean rimeShare(LivingEntity caster, Arena arena, double power) {
        hold(caster, 30);
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.BLOCK_POWDER_SNOW_STEP, 2F, .5F);
        for (Player player : arena.targets(caster, 30)) bar(player, "<aqua>Get in the ring, together!</aqua> <gray>The frost is shared.</gray>");
        repeat(caster, arena, 6, 5, tick -> {
            Telegraph.ring(center, SHARE_RADIUS, Telegraph.FROST);
            caster.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 20, 2, .8, 2, .01);
        }, () -> {
            List<Player> inside = arena.near(center, SHARE_RADIUS);
            caster.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, center.clone().add(0, 1, 0), 80, 3, 1, 3, .1);
            caster.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 2F, .6F);
            double share = 8 * power / Math.max(1, inside.size());
            for (Player player : inside) hit(player, caster, share);
            for (Player player : arena.targets(caster, 30)) if (!inside.contains(player)) {
                hit(player, caster, 8 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            }
        });
        return true;
    }

    /** It picks one player and runs them down, freezing the ground behind it. Keep moving, and keep away from your friends. */
    private boolean hoarfrostChase(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        if (!(caster instanceof Mob mob)) return false;
        Player prey = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        bar(prey, "<aqua><bold>It's hunting you. Run!</bold></aqua>");
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, 2F, .7F);
        Set<UUID> struck = new HashSet<>();
        repeat(caster, arena, CHASE_STEPS, 6, tick -> {
            if (!prey.isOnline() || prey.isDead()) return;
            mob.getPathfinder().moveTo(prey, 1.7);
            Telegraph.ring(prey.getLocation(), 1.4, Telegraph.FROST);
            Block floor = caster.getLocation().getBlock();
            if (floor.getType().isAir() && floor.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) arena.temporary(floor, Material.SNOW, ICE_TICKS);
            for (Player player : arena.targets(caster, 2.4)) if (struck.add(player.getUniqueId())) {
                hit(player, caster, 4 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
            }
        }, () -> mob.getPathfinder().stopPathfinding());
        return true;
    }

    /** It throws its aegis down somewhere on the floor and brings the sky in on everyone not sheltering behind it. */
    private boolean cataclysm(LivingEntity caster, Arena arena, double power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.TAU);
        Location spot = caster.getLocation().add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10);
        while (spot.getBlock().isSolid() && spot.getY() < caster.getLocation().getY() + 6) spot.add(0, 1, 0);
        while (!spot.clone().subtract(0, 1, 0).getBlock().isSolid() && spot.getY() > caster.getLocation().getY() - 6) spot.subtract(0, 1, 0);
        hold(caster, 100);
        BlockDisplay aegis = caster.getWorld().spawn(spot, BlockDisplay.class, display -> {
            display.setBlock(Material.GOLD_BLOCK.createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(-1.2F, 0, -.2F), new Quaternionf(), new Vector3f(2.4F, 3.4F, .4F), new Quaternionf()));
        });
        caster.getWorld().playSound(spot, Sound.ITEM_SHIELD_BLOCK, 2F, .5F);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.6F, .7F);
        for (Player player : arena.targets(caster, 40)) bar(player, "<gold><bold>To the Aegis!</bold></gold>");
        repeat(caster, arena, 20, 5, tick -> {
            Telegraph.ring(spot, AEGIS_RADIUS, Telegraph.HOLY);
            if (tick % 2 == 0) Telegraph.disc(spot, AEGIS_RADIUS, Telegraph.HOLY);
            caster.getWorld().spawnParticle(Particle.END_ROD, spot.clone().add(0, 2, 0), 4, 1, 1, 1, .01);
            if (tick > 12) caster.getWorld().spawnParticle(Particle.FLAME, caster.getLocation().add(0, 1, 0), 30, 2, 1, 2, .1);
        }, () -> {
            aegis.remove();
            caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, caster.getLocation(), 3, 4, 1, 4, 0);
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2F, .5F);
            for (Player player : arena.targets(caster, 40)) if (player.getLocation().distance(spot) > AEGIS_RADIUS) hit(player, caster, 12 * power);
        });
        return true;
    }

    /** Every player is branded with one of the three seals, and has to reach that seal's plate before the brand burns in. */
    private boolean sigilBrand(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        List<Location> plates = arena.marks();
        if (plates.size() < SEALS.length) return false;
        int start = ThreadLocalRandom.current().nextInt(SEALS.length);
        java.util.Map<Player, Integer> brands = new java.util.HashMap<>();
        for (int index = 0; index < targets.size(); index++) {
            int seal = (start + index) % SEALS.length;
            brands.put(targets.get(index), seal);
            bar(targets.get(index), SEAL_NAMES[seal]);
            targets.get(index).playSound(targets.get(index).getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1F, 1.2F);
        }
        hold(caster, 60);
        repeat(caster, arena, BRAND_STEPS, 5, tick -> {
            for (int seal = 0; seal < SEALS.length; seal++) Telegraph.ring(plates.get(seal), BRAND_RADIUS, SEALS[seal]);
            brands.forEach((player, seal) -> {
                if (!player.isOnline()) return;
                Telegraph.ring(player.getLocation(), .8, SEALS[seal]);
                if (tick % 6 == 0) bar(player, SEAL_NAMES[seal]);
            });
        }, () -> {
            brands.forEach((player, seal) -> {
                if (!player.isOnline() || player.isDead() || player.getWorld() != caster.getWorld()) return;
                if (player.getLocation().distance(plates.get(seal)) <= BRAND_RADIUS) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4F, 1.2F);
                    return;
                }
                hit(player, caster, 10 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
                bar(player, "<red>Unworthy.</red>");
            });
        });
        return true;
    }

    /** Three echoes on their plates, and the caster can't be touched until the last of them is dead. */
    private boolean echoWard(LivingEntity caster, Arena arena) {
        List<Location> plates = arena.marks();
        if (plates.size() < ECHOES.length) return false;
        hold(caster, 30);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 2F, .6F);
        List<LivingEntity> echoes = new ArrayList<>();
        for (int index = 0; index < ECHOES.length; index++) {
            LivingEntity echo = arena.fixture(ECHOES[index], plates.get(index));
            if (echo != null) echoes.add(echo);
        }
        if (echoes.isEmpty()) return false;
        arena.shield(caster, echoes);
        return true;
    }

    private boolean voidBolt(LivingEntity caster, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Location from = caster.getLocation().add(0, caster.getHeight() * .7, 0), to = target.getLocation().add(0, 1, 0);
        Vector step = to.toVector().subtract(from.toVector());
        for (double along = 0; along <= 1; along += .04) caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(step.clone().multiply(along)), 2, .05, .05, .05, 0);
        caster.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, .6F);
        hit(target, caster, 3.2 * power);
        return true;
    }

    /** A well opens under someone. Everything in reach is dragged towards it, and it closes with a crack: be gone by then. */
    private boolean gravityWell(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location well = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        hold(caster, 14);
        caster.getWorld().playSound(well, Sound.BLOCK_PORTAL_TRIGGER, 1.6F, .5F);
        repeat(caster, arena, 18, 4, tick -> {
            Telegraph.ring(well, WELL_PULL, Telegraph.SHADOW);
            Telegraph.disc(well, WELL_CORE, Telegraph.SHADOW);
            caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, well.clone().add(0, .5, 0), 30, 3, .4, 3, .06);
            for (Player player : arena.near(well, WELL_PULL)) {
                Vector pull = well.toVector().subtract(player.getLocation().toVector()).setY(0);
                if (pull.lengthSquared() > 1) player.setVelocity(player.getVelocity().add(pull.normalize().multiply(.13)));
            }
        }, () -> {
            caster.getWorld().spawnParticle(Particle.EXPLOSION, well, 3, 1, .3, 1, 0);
            caster.getWorld().spawnParticle(Particle.SQUID_INK, well.clone().add(0, 1, 0), 60, 2, 1, 2, .1);
            caster.getWorld().playSound(well, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2F, .7F);
            for (Player player : arena.near(well, WELL_CORE)) hit(player, caster, 7 * power);
        });
        return true;
    }

    /** Pools of nothing open under each player, and stay open a while. */
    private boolean voidRift(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        List<Location> spots = new ArrayList<>();
        for (Player player : targets.subList(0, Math.min(3, targets.size()))) spots.add(player.getLocation());
        hold(caster, 12);
        repeat(caster, arena, 3, 5, tick -> { for (Location spot : spots) Telegraph.disc(spot, RIFT_RADIUS, Telegraph.SHADOW); }, () -> repeat(caster, arena, RIFT_PULSES, 10, tick -> {
            for (Location spot : spots) {
                caster.getWorld().spawnParticle(Particle.SQUID_INK, spot.clone().add(0, .2, 0), 16, 1.4, .05, 1.4, 0);
                caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, spot.clone().add(0, .2, 0), 10, 1.4, .2, 1.4, .02);
                Telegraph.ring(spot, RIFT_RADIUS, Telegraph.SHADOW);
                for (Player player : arena.near(spot, RIFT_RADIUS)) hit(player, caster, power);
            }
        }, () -> { }));
        return true;
    }

    /** Two players are tied together, and it only breaks if they get far enough apart before it closes. */
    private boolean voidBond(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        if (targets.size() < 2) return false;
        List<Player> pair = new ArrayList<>(targets);
        java.util.Collections.shuffle(pair);
        Player one = pair.get(0), two = pair.get(1);
        for (Player player : List.of(one, two)) bar(player, "<dark_purple><bold>Bound!</bold></dark_purple> <gray>Get away from " + player(player == one ? two : one) + ".</gray>");
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_CHAIN_PLACE, 2F, .5F);
        repeat(caster, arena, 24, 5, tick -> {
            if (!one.isOnline() || !two.isOnline() || one.getWorld() != two.getWorld()) return;
            Telegraph.line(one.getLocation().add(0, 1, 0), two.getLocation().add(0, 1, 0), Telegraph.SHADOW);
        }, () -> {
            if (!one.isOnline() || !two.isOnline() || one.getWorld() != two.getWorld()) return;
            if (one.getLocation().distance(two.getLocation()) >= BOND_APART) {
                for (Player player : List.of(one, two)) player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.4F, .7F);
                return;
            }
            for (Player player : List.of(one, two)) {
                hit(player, caster, 7 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            }
        });
        return true;
    }

    private static String player(Player player) { return net.nerdsmp.paper.presentation.Chat.escape(player.getName()); }

    /** Eyes open in the air round the arena and start casting. They don't stop until they're shut. */
    private boolean voidEyes(LivingEntity caster, Arena arena) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        hold(caster, 20);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2F, .7F);
        for (int eye = 0; eye < 2; eye++) {
            double angle = random.nextDouble(Math.TAU), far = random.nextDouble(11, 20);
            Location spot = caster.getLocation().add(Math.cos(angle) * far, 3, Math.sin(angle) * far);
            caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, spot, 50, .6, .6, .6, .1);
            arena.fixture("custom:void_eye", spot);
        }
        return true;
    }

    /** Rings of black run out across the floor from the caster, each with one gap in it. Find the gap. */
    private boolean blackHarvest(LivingEntity caster, Arena arena, double power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location center = caster.getLocation();
        hold(caster, HARVEST_TICKS);
        double[] gaps = {random.nextDouble(Math.TAU), random.nextDouble(Math.TAU), random.nextDouble(Math.TAU)};
        List<Set<UUID>> struck = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        caster.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.6F, .6F);
        for (Player player : arena.near(center, 40)) bar(player, "<dark_purple><bold>Black Harvest!</bold></dark_purple> <gray>Find the gap in each ring.</gray>");
        repeat(caster, arena, HARVEST_TICKS, 1, tick -> {
            for (int wave = 0; wave < gaps.length; wave++) {
                int age = tick - 12 - wave * HARVEST_SPACE;
                double reach = age * .8;
                if (age < 0 || reach > 34) continue;
                int points = (int) (reach * 2.4) + 8;
                for (int spot = 0; spot < points; spot++) {
                    double angle = spot * Math.TAU / points;
                    if (Math.abs(Math.sin((angle - gaps[wave]) / 2)) < HARVEST_GAP) continue;
                    caster.getWorld().spawnParticle(Particle.SQUID_INK, center.clone().add(Math.cos(angle) * reach, .4, Math.sin(angle) * reach), 1, 0, .1, 0, 0);
                }
                for (Player player : arena.near(center, 40)) {
                    Location at = player.getLocation();
                    double out = Math.hypot(at.getX() - center.getX(), at.getZ() - center.getZ()), angle = Math.atan2(at.getZ() - center.getZ(), at.getX() - center.getX());
                    if (Math.abs(out - reach) > 1.3 || Math.abs(Math.sin((angle - gaps[wave]) / 2)) < HARVEST_GAP || !struck.get(wave).add(player.getUniqueId())) continue;
                    hit(player, caster, 6 * power);
                }
            }
        }, () -> { });
        return true;
    }

    /** Three steps through nothing, and each place it stood bursts a moment after it leaves. */
    private boolean voidStep(LivingEntity caster, Arena arena, double power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location home = caster.getLocation();
        hold(caster, 45);
        repeat(caster, arena, 3, 14, step -> {
            Location left = caster.getLocation();
            double angle = random.nextDouble(Math.TAU), far = random.nextDouble(8, 18);
            Location next = step == 2 ? home : home.clone().add(Math.cos(angle) * far, 0, Math.sin(angle) * far);
            caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, left.clone().add(0, 2, 0), 80, 1, 2, 1, .1);
            caster.getWorld().playSound(left, Sound.ENTITY_ENDERMAN_TELEPORT, 1.6F, .5F);
            caster.teleport(next);
            repeat(caster, arena, 3, 5, tick -> Telegraph.ring(left, STEP_BURST, Telegraph.SHADOW), () -> {
                caster.getWorld().spawnParticle(Particle.SQUID_INK, left.clone().add(0, 1, 0), 50, 2, .6, 2, .08);
                caster.getWorld().playSound(left, Sound.ENTITY_GENERIC_EXPLODE, .8F, 1.4F);
                for (Player player : arena.near(left, STEP_BURST)) hit(player, caster, 4 * power);
            });
        }, () -> { });
        return true;
    }

    /** Valve drones on their marks. While they run she can't be touched, and when the last one stops she's wide open. */
    private boolean droneWard(LivingEntity caster, Arena arena) {
        List<Location> marks = arena.marks();
        if (marks.size() < 4) return false;
        hold(caster, 30);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BELL_USE, 2F, .6F);
        List<LivingEntity> drones = new ArrayList<>();
        for (Location mark : marks.subList(0, 4)) {
            LivingEntity drone = arena.fixture("custom:valve_drone", mark);
            if (drone != null) drones.add(drone);
        }
        if (drones.isEmpty()) return false;
        arena.shield(caster, drones, EXPOSED_SECONDS);
        return true;
    }

    /** Water under someone's feet, a few seconds' warning, then a column that throws everyone close into the air. */
    private boolean geyser(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location spot = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        hold(caster, 10);
        repeat(caster, arena, 10, 5, tick -> {
            Telegraph.disc(spot, GEYSER_RADIUS, Telegraph.FROST);
            caster.getWorld().spawnParticle(Particle.BUBBLE_POP, spot.clone().add(0, .3, 0), 16, 1.6, .1, 1.6, .02);
        }, () -> {
            caster.getWorld().spawnParticle(Particle.SPLASH, spot.clone().add(0, 3, 0), 200, 1.4, 3, 1.4, .3);
            caster.getWorld().playSound(spot, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2F, .7F);
            for (Player player : arena.near(spot, GEYSER_RADIUS)) {
                hit(player, caster, 4 * power);
                player.setVelocity(new Vector(0, 1.1, 0));
            }
        });
        return true;
    }

    /** She draws the water up round her and calls her guard. Nothing gets through it until they're dead. */
    private boolean waterspout(LivingEntity caster, Arena arena, double power) {
        Location center = caster.getLocation();
        hold(caster, 30);
        caster.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.4F, .6F);
        List<LivingEntity> guard = arena.summon(caster, 3);
        if (guard.isEmpty()) return false;
        arena.shield(caster, guard);
        repeat(caster, arena, 240, 5, tick -> {
            if (!caster.isInvulnerable()) return;
            caster.getWorld().spawnParticle(Particle.SPLASH, center.clone().add(0, 3, 0), 40, 2.5, 4, 2.5, .2);
            caster.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone().add(0, 1, 0), 24, 2, 3, 2, .1);
            Telegraph.ring(center, SPOUT_REACH, Telegraph.FROST);
            for (Player player : arena.near(center, SPOUT_REACH)) {
                player.setVelocity(player.getLocation().toVector().subtract(center.toVector()).setY(0).normalize().multiply(.9).setY(.35));
                if (tick % 4 == 0) hit(player, caster, power);
            }
        }, () -> { });
        return true;
    }

    /** A wall of water crosses the whole floor. Anything solid between you and it will do; nothing else will. */
    private boolean tidalWave(LivingEntity caster, Arena arena, double power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextInt(4) * Math.PI / 2 + Math.PI / 4;
        Vector along = new Vector(Math.cos(angle), 0, Math.sin(angle));
        Location start = caster.getLocation().subtract(along.clone().multiply(WAVE_REACH));
        for (Player player : arena.near(caster.getLocation(), 45)) bar(player, "<aqua><bold>The tide is coming!</bold></aqua> <gray>Get behind something solid.</gray>");
        hold(caster, 40);
        caster.getWorld().playSound(start, Sound.WEATHER_RAIN_ABOVE, 3F, .5F);
        Vector across = new Vector(-along.getZ(), 0, along.getX());
        repeat(caster, arena, 8, 5, tick -> {
            for (double offset = -WAVE_REACH; offset <= WAVE_REACH; offset += 3)
                caster.getWorld().spawnParticle(Particle.SPLASH, start.clone().add(across.clone().multiply(offset)).add(0, 1, 0), 6, .6, .6, .6, .1);
        }, () -> {
            Quaternionf turn = new Quaternionf().rotateY((float) (-angle + Math.PI / 2));
            BlockDisplay wave = caster.getWorld().spawn(start, BlockDisplay.class, display -> {
                display.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
                display.setPersistent(false);
                display.setTeleportDuration(1);
                display.setViewRange(4F);
                display.setBrightness(new Display.Brightness(14, 14));
                display.setTransformation(new Transformation(new Vector3f((float) -WAVE_REACH, 0, -.5F), turn, new Vector3f((float) WAVE_REACH * 2, 5, 1.6F), new Quaternionf()));
            });
            Set<UUID> struck = new HashSet<>();
            int[] tick = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                double reach = tick[0]++ * 1.1;
                if (!arena.live() || reach > WAVE_REACH * 2) {
                    wave.remove();
                    task.cancel();
                    return;
                }
                Location front = start.clone().add(along.clone().multiply(reach));
                wave.teleport(front);
                caster.getWorld().spawnParticle(Particle.SPLASH, front.clone().add(0, 4, 0), 30, across.getX() * 14, .4, across.getZ() * 14, .1);
                for (Player player : arena.near(caster.getLocation(), 45)) {
                    Vector to = player.getLocation().toVector().subtract(front.toVector());
                    if (Math.abs(to.dot(along)) > 1.3 || sheltered(player, along) || !struck.add(player.getUniqueId())) continue;
                    hit(player, caster, 8 * power);
                    player.setVelocity(along.clone().multiply(1.3).setY(.5));
                }
            }, 0L, 1L);
        });
        return true;
    }

    /** Something solid within a few blocks upwind of a player, at knee or chest height. */
    private static boolean sheltered(Player player, Vector along) {
        Location at = player.getLocation();
        for (double back = 1; back <= 5; back += .5) for (double up : new double[]{.5, 1.5}) if (at.clone().subtract(along.clone().multiply(back)).add(0, up, 0).getBlock().isSolid()) return true;
        return false;
    }

    /** A tank at the end of its strength. Shut the valve on it in time, or everything in it goes to him. */
    private boolean overpressure(LivingEntity caster, Arena arena, double power) {
        List<Location> tanks = arena.marks();
        if (tanks.isEmpty()) return false;
        Location tank = tanks.stream().min(Comparator.comparingDouble(spot -> spot.distanceSquared(caster.getLocation()))).orElseThrow();
        LivingEntity valve = arena.fixture("custom:pressure_valve", tank);
        if (valve == null) return false;
        for (Player player : arena.near(tank, 40)) bar(player, "<gold><bold>Overpressure!</bold></gold> <gray>Break the valve before it blows.</gray>");
        caster.getWorld().playSound(tank, Sound.BLOCK_BELL_USE, 2F, 1.4F);
        repeat(caster, arena, PRESSURE_STEPS, 5, tick -> {
            if (!valve.isValid()) return;
            caster.getWorld().spawnParticle(Particle.CLOUD, tank.clone().add(0, 3, 0), 10 + tick, .6, 1, .6, .1);
            if (tick % 4 == 0) caster.getWorld().playSound(tank, Sound.BLOCK_FIRE_EXTINGUISH, 1.4F, .6F + tick * .02F);
        }, () -> {
            if (!valve.isValid() || valve.isDead()) {
                caster.getWorld().playSound(tank, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 2F, .6F);
                return;
            }
            valve.remove();
            caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, tank, 1, 0, 0, 0, 0);
            caster.getWorld().spawnParticle(Particle.CLOUD, tank.clone().add(0, 2, 0), 200, 4, 2, 4, .25);
            caster.getWorld().playSound(tank, Sound.ENTITY_GENERIC_EXPLODE, 2F, .7F);
            for (Player player : arena.near(tank, 40)) hit(player, caster, 3 * power);
            PotionEffect now = caster.getPotionEffect(PotionEffectType.STRENGTH);
            caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 2400, now == null ? 0 : Math.min(3, now.getAmplifier() + 1)));
        });
        return true;
    }

    /** Roots tie two players together. Stay close and they only bite; pull away and they drag you back. */
    private boolean bindingVines(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        if (targets.size() < 2) return false;
        List<Player> pair = new ArrayList<>(targets);
        java.util.Collections.shuffle(pair);
        Player one = pair.get(0), two = pair.get(1);
        for (Player player : List.of(one, two)) bar(player, "<dark_green><bold>Bound by the roots!</bold></dark_green> <gray>Stay near " + player(player == one ? two : one) + ".</gray>");
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_ROOTS_PLACE, 2F, .5F);
        repeat(caster, arena, 32, 5, tick -> {
            if (!one.isOnline() || !two.isOnline() || one.getWorld() != two.getWorld()) return;
            Telegraph.line(one.getLocation().add(0, 1, 0), two.getLocation().add(0, 1, 0), Telegraph.POISON);
            double apart = one.getLocation().distance(two.getLocation());
            if (tick % 4 == 0) for (Player player : List.of(one, two)) hit(player, caster, power * .5);
            if (apart <= VINE_REACH) return;
            Vector pull = two.getLocation().toVector().subtract(one.getLocation().toVector()).setY(0).normalize().multiply(.9).setY(.25);
            one.setVelocity(pull);
            two.setVelocity(pull.clone().multiply(-1).setY(.25));
            hit(one, caster, power);
            hit(two, caster, power);
        }, () -> { });
        return true;
    }

    /** A long tongue of mud down a marked line from the caster, through whoever it's picked. */
    private boolean mudslide(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Location from = caster.getLocation().add(0, .2, 0);
        Vector along = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (along.lengthSquared() < 1) return false;
        along.normalize();
        Location end = from.clone().add(along.clone().multiply(SLIDE_REACH));
        hold(caster, 26);
        caster.getWorld().playSound(from, Sound.BLOCK_MUD_BREAK, 2F, .5F);
        repeat(caster, arena, 5, 5, tick -> Telegraph.line(from, end, Telegraph.POISON), () -> {
            for (double reach = 0; reach < SLIDE_REACH; reach += 1) Telegraph.shards(from.clone().add(along.clone().multiply(reach)), 6, .6, Material.MUD);
            caster.getWorld().playSound(from, Sound.BLOCK_MUD_BREAK, 2F, .7F);
            for (Player player : arena.near(from.clone().add(along.clone().multiply(SLIDE_REACH / 2)), SLIDE_REACH)) {
                Vector to = player.getLocation().toVector().subtract(from.toVector()).setY(0);
                double on = to.dot(along), off = to.clone().subtract(along.clone().multiply(on)).length();
                if (on < 0 || on > SLIDE_REACH || off > 2.2) continue;
                hit(player, caster, 5 * power);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
            }
        });
        return true;
    }

    /** The silt wakes in rings, one after another, and only the ground that's already gone off is safe. */
    private boolean awakenSilt(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Location> spots = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            Location near = targets.get(index % targets.size()).getLocation();
            spots.add(near.add(random.nextDouble(-7, 7), 0, random.nextDouble(-7, 7)));
        }
        hold(caster, 60);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WARDEN_DIG, 2F, .6F);
        repeat(caster, arena, 25, 4, tick -> {
            int going = tick / 5;
            for (int index = going; index < spots.size(); index++) Telegraph.ring(spots.get(index), SILT_BURST, Telegraph.POISON);
            if (tick % 5 != 4) return;
            Location spot = spots.get(going);
            Telegraph.shards(spot, 40, 3, Material.MUD);
            caster.getWorld().playSound(spot, Sound.BLOCK_MUD_BREAK, 2F, .6F);
            for (Player player : arena.near(spot, SILT_BURST)) hit(player, caster, 3.5 * power);
        }, () -> { });
        return true;
    }

    /** Where the fight is, from a boss that stands off to one side of it: the middle of the room's marks. */
    private static Location middle(LivingEntity caster, Arena arena) {
        List<Location> marks = arena.marks();
        if (marks.isEmpty()) return caster.getLocation();
        Vector sum = new Vector();
        for (Location mark : marks) sum.add(mark.toVector());
        return sum.multiply(1D / marks.size()).toLocation(caster.getWorld());
    }

    /** One of his arms goes up, a lane across the floor burns red, and the hammer comes down along the whole of it. */
    private boolean sovereignSmash(LivingEntity caster, Arena arena, double power) {
        Location heart = middle(caster, arena);
        Vector along = heart.toVector().subtract(caster.getLocation().toVector()).setY(0).normalize(), across = new Vector(-along.getZ(), 0, along.getX());
        int side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        double off = SMASH_OFFSET * side;
        Location start = caster.getLocation().add(across.clone().multiply(off));
        Location end = start.clone().add(along.clone().multiply(SMASH_LENGTH));
        arena.strike(caster, side, 2.8F);
        caster.getWorld().playSound(heart, Sound.ENTITY_RAVAGER_ROAR, 3F, .5F);
        hold(caster, 50);
        repeat(caster, arena, 10, 5, tick -> {
            for (double lane = -SMASH_HALF; lane <= SMASH_HALF; lane += 2) Telegraph.line(start.clone().add(across.clone().multiply(lane)).add(0, .1, 0), end.clone().add(across.clone().multiply(lane)).add(0, .1, 0), Telegraph.FIRE);
        }, () -> {
            arena.strike(caster, side, 1.25F);
            caster.getWorld().playSound(heart, Sound.ENTITY_GENERIC_EXPLODE, 4F, .4F);
            caster.getWorld().playSound(heart, Sound.BLOCK_ANVIL_LAND, 4F, .4F);
            for (double reach = 0; reach < SMASH_LENGTH; reach += 2) caster.getWorld().spawnParticle(Particle.LAVA, start.clone().add(along.clone().multiply(reach)), 8, SMASH_HALF / 2, .2, 1, 0);
            caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, start.clone().add(along.clone().multiply(SMASH_LENGTH / 2)), 3, 2, .4, 12, 0);
            for (Player player : arena.near(heart, 60)) {
                Vector to = player.getLocation().toVector().subtract(start.toVector()).setY(0);
                double on = to.dot(along), offLane = Math.abs(to.dot(across));
                if (on < 0 || on > SMASH_LENGTH) continue;
                if (offLane <= SMASH_HALF) hit(player, caster, 10 * power);
                else if (offLane <= SMASH_HALF + 5) player.setVelocity(across.clone().multiply(Math.signum(to.dot(across)) * 1.1).setY(.5));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> arena.strike(caster, side, 0), 30L);
        });
        return true;
    }

    /** One of three bands of the floor goes up in flame, and you've a moment to be somewhere else. */
    private boolean engulfingFlames(LivingEntity caster, Arena arena, double power) {
        Location heart = middle(caster, arena);
        Vector along = heart.toVector().subtract(caster.getLocation().toVector()).setY(0).normalize(), across = new Vector(-along.getZ(), 0, along.getX());
        double far = heart.distance(caster.getLocation()) + 26, near = Math.max(0, far - 54);
        int band = ThreadLocalRandom.current().nextInt(3);
        double from = near + band * 18, to = from + 18;
        for (Player player : arena.near(heart, 60)) bar(player, "<red><bold>Engulfing Flames!</bold></red> <gray>Get out of the burning band.</gray>");
        caster.getWorld().playSound(heart, Sound.ITEM_FIRECHARGE_USE, 3F, .5F);
        Location base = caster.getLocation();
        repeat(caster, arena, 8, 5, tick -> {
            for (double across_ = -30; across_ <= 30; across_ += 5) {
                Location a = base.clone().add(along.clone().multiply(from)).add(across.clone().multiply(across_)).add(0, .1, 0), b = base.clone().add(along.clone().multiply(to)).add(across.clone().multiply(across_)).add(0, .1, 0);
                Telegraph.line(a, b, Telegraph.FIRE);
            }
        }, () -> repeat(caster, arena, 12, 5, tick -> {
            for (double across_ = -30; across_ <= 30; across_ += 3) for (double up = from; up <= to; up += 3)
                caster.getWorld().spawnParticle(Particle.FLAME, base.clone().add(along.clone().multiply(up)).add(across.clone().multiply(across_)), 2, 1, .4, 1, .03);
            for (Player player : arena.near(heart, 60)) {
                double on = player.getLocation().toVector().subtract(base.toVector()).setY(0).dot(along);
                if (on >= from && on <= to) {
                    hit(player, caster, 2 * power);
                    player.setFireTicks(40);
                }
            }
        }, () -> { }));
        return true;
    }

    /** He drives the hammer into the middle of the floor and goes under, and his sons run for it. Any that gets there, it goes up. */
    private boolean sonsOfFlame(LivingEntity caster, Arena arena, double power) {
        Location heart = middle(caster, arena);
        BlockDisplay hammer = caster.getWorld().spawn(heart.clone().add(0, 14, 0), BlockDisplay.class, display -> {
            display.setBlock(Material.GILDED_BLACKSTONE.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(10);
            display.setViewRange(4F);
            display.setBrightness(new Display.Brightness(14, 14));
            display.setTransformation(new Transformation(new Vector3f(-3, 0, -2), new Quaternionf(), new Vector3f(6, 5, 4), new Quaternionf()));
        });
        BlockDisplay haft = caster.getWorld().spawn(heart.clone().add(0, 14, 0), BlockDisplay.class, display -> {
            display.setBlock(Material.POLISHED_BLACKSTONE.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(10);
            display.setViewRange(4F);
            display.setTransformation(new Transformation(new Vector3f(-.6F, -14, -.6F), new Quaternionf(), new Vector3f(1.2F, 14, 1.2F), new Quaternionf()));
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            hammer.teleport(heart.clone().add(0, 9, 0));
            haft.teleport(heart.clone().add(0, 13, 0));
            caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, heart, 3, 2, .4, 2, 0);
            caster.getWorld().playSound(heart, Sound.BLOCK_ANVIL_LAND, 4F, .4F);
        }, 4L);
        for (Player player : arena.near(heart, 60)) bar(player, "<gold><bold>Sons of Flame!</bold></gold> <gray>Don't let them reach the hammer.</gray>");
        List<LivingEntity> sons = new ArrayList<>();
        for (int son = 0; son < SONS; son++) {
            double angle = son * Math.TAU / SONS;
            LivingEntity spawned = arena.spawn("custom:son_of_flame", heart.clone().add(Math.cos(angle) * 27, 0, Math.sin(angle) * 27));
            if (spawned != null) sons.add(spawned);
        }
        repeat(caster, arena, 110, 5, tick -> {
            for (LivingEntity son : List.copyOf(sons)) {
                if (!son.isValid() || son.isDead()) {
                    sons.remove(son);
                    continue;
                }
                if (son instanceof Mob mob) mob.getPathfinder().moveTo(heart, .55);
                if (son.getLocation().distanceSquared(heart) > 16) continue;
                sons.remove(son);
                son.remove();
                caster.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, heart, 2, 2, .4, 2, 0);
                caster.getWorld().playSound(heart, Sound.ENTITY_GENERIC_EXPLODE, 3F, .6F);
                for (Player player : arena.near(heart, 60)) hit(player, caster, 6 * power);
            }
        }, () -> {
            hammer.remove();
            haft.remove();
        });
        return true;
    }

    /** A ball of the mountain's own heart breaks loose and rolls after one player until something stops it. */
    private boolean livingMeteor(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player prey = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Location heart = middle(caster, arena);
        Location[] at = {heart.clone().add(ThreadLocalRandom.current().nextDouble(-16, 16), 0, ThreadLocalRandom.current().nextDouble(-16, 16))};
        bar(prey, "<red><bold>A meteor is rolling for you!</bold></red> <gray>Run.</gray>");
        BlockDisplay meteor = caster.getWorld().spawn(at[0], BlockDisplay.class, display -> {
            display.setBlock(Material.MAGMA_BLOCK.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(-1.6F, 0, -1.6F), new Quaternionf(), new Vector3f(3.2F, 3.2F, 3.2F), new Quaternionf()));
        });
        caster.getWorld().playSound(at[0], Sound.ENTITY_BLAZE_SHOOT, 2F, .5F);
        repeat(caster, arena, METEOR_TICKS, 1, tick -> {
            if (!prey.isOnline() || prey.getWorld() != caster.getWorld()) return;
            Vector to = prey.getLocation().toVector().subtract(at[0].toVector()).setY(0);
            if (to.lengthSquared() > .01) at[0].add(to.normalize().multiply(METEOR_SPEED));
            meteor.teleport(at[0]);
            if (tick % 3 == 0) caster.getWorld().spawnParticle(Particle.FLAME, at[0].clone().add(0, 1.4, 0), 8, 1, .8, 1, .02);
            for (Player player : arena.near(at[0], 2.6)) {
                hit(player, caster, 7 * power);
                caster.getWorld().spawnParticle(Particle.EXPLOSION, at[0], 4, 1, 1, 1, 0);
                player.setVelocity(player.getLocation().toVector().subtract(at[0].toVector()).setY(0).normalize().multiply(1.2).setY(.6));
            }
        }, meteor::remove);
        return true;
    }

    /** A plate lights. Stand on it, enough of you, and one more of the Wardens' chains goes round him. */
    private boolean bindingChain(LivingEntity caster, Arena arena, double power) {
        List<Location> plates = arena.marks();
        if (plates.size() < SEALS.length) return false;
        int seal = ThreadLocalRandom.current().nextInt(SEALS.length);
        Location plate = plates.get(seal);
        int need = Math.max(1, (int) Math.ceil(arena.near(plate, 70).size() / 3D));
        for (Player player : arena.near(plate, 70)) bar(player, BINDING_NAMES[seal] + " <gray>" + need + " to hold it.</gray>");
        caster.getWorld().playSound(plate, Sound.BLOCK_BEACON_ACTIVATE, 3F, .6F);
        hold(caster, 70);
        repeat(caster, arena, 14, 5, tick -> {
            Telegraph.ring(plate, BINDING_RADIUS, SEALS[seal]);
            caster.getWorld().spawnParticle(Particle.END_ROD, plate.clone().add(0, 1, 0), 10, 2, 1, 2, .02);
        }, () -> {
            int standing = arena.near(plate, BINDING_RADIUS).size();
            if (standing < need) {
                caster.getWorld().playSound(plate, Sound.ENTITY_GENERIC_EXPLODE, 3F, .5F);
                for (Player player : arena.near(plate, 70)) hit(player, caster, 9 * power);
                return;
            }
            Location from = plate.clone().add(0, 1, 0), to = caster.getLocation().add(0, 8, 0);
            Vector step = to.toVector().subtract(from.toVector());
            for (double along = 0; along <= 1; along += .01) caster.getWorld().spawnParticle(Particle.END_ROD, from.clone().add(step.clone().multiply(along)), 1, 0, 0, 0, 0);
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_CHAIN_PLACE, 5F, .4F);
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 4F, .4F);
            double max = caster.getAttribute(Attribute.MAX_HEALTH) == null ? 0 : caster.getAttribute(Attribute.MAX_HEALTH).getValue();
            caster.setHealth(Math.max(1, caster.getHealth() - max * BINDING_SHARE));
        });
        return true;
    }

    /** A long breath down a marked line. */
    private boolean drakeBreath(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Location from = caster.getLocation().add(0, 2, 0), to = target.getLocation().add(0, .1, 0);
        Vector along = to.toVector().subtract(from.toVector()).setY(0);
        if (along.lengthSquared() < 1) return false;
        along.normalize();
        Location floor = target.getLocation().add(0, .1, 0);
        Location end = floor.clone().add(along.clone().multiply(14)), start = floor.clone().subtract(along.clone().multiply(14));
        caster.getWorld().playSound(from, Sound.ENTITY_ENDER_DRAGON_GROWL, 3F, 1F);
        repeat(caster, arena, 7, 5, tick -> Telegraph.line(start, end, Telegraph.FIRE), () -> {
            caster.getWorld().playSound(floor, Sound.ENTITY_BLAZE_SHOOT, 3F, .5F);
            for (double reach = -14; reach <= 14; reach += 1) caster.getWorld().spawnParticle(Particle.FLAME, floor.clone().add(along.clone().multiply(reach)), 10, .8, .5, .8, .04);
            for (Player player : arena.near(floor, 15)) {
                Vector to2 = player.getLocation().toVector().subtract(floor.toVector()).setY(0);
                double off = to2.clone().subtract(along.clone().multiply(to2.dot(along))).length();
                if (off <= 2.2) {
                    hit(player, caster, 5 * power);
                    player.setFireTicks(60);
                }
            }
        });
        return true;
    }

    /** The Steward speaks a word in the old tongue, and whoever's looking at him when he finishes it burns. Turn your back. */
    private boolean ashenDecree(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        hold(caster, 34);
        Location at = caster.getLocation().add(0, 2, 0);
        for (Player player : targets) bar(player, "<gold><bold>The Ashen Decree!</bold></gold> <gray>Look away from him.</gray>");
        caster.getWorld().playSound(at, Sound.ENTITY_WITHER_AMBIENT, 2F, .6F);
        repeat(caster, arena, 7, 5, tick -> caster.getWorld().spawnParticle(Particle.FLAME, at, 20, 1, 1.4, 1, .08), () -> {
            caster.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 1, 1, 1, 0);
            for (Player player : arena.targets(caster, 40)) {
                Vector look = player.getEyeLocation().getDirection(), toHim = at.toVector().subtract(player.getEyeLocation().toVector());
                if (toHim.lengthSquared() > .1 && look.dot(toHim.normalize()) > .3) hit(player, caster, 6 * power);
            }
        });
        return true;
    }

    private static void bar(Player player, String markup) { net.nerdsmp.paper.presentation.Chat.bar(player, markup); }

    /** A bolt you can watch fly to a marked spot, and ice there for a few seconds after. */
    private boolean frostBolt(LivingEntity caster, Arena arena, List<Player> targets, double power) {
        Location spot = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1F, 1.6F);
        hold(caster, 16);
        repeat(caster, arena, 3, 5, tick -> Telegraph.disc(spot, ICE_RADIUS, Telegraph.FROST), () -> {
            fly(caster, arena, spot.clone().add(0, .4, 0), .6, at -> {
                at.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 6, .12, .12, .12, 0);
                Telegraph.disc(spot, ICE_RADIUS, Telegraph.FROST);
            }, at -> false, at -> {
                at.getWorld().playSound(spot, Sound.BLOCK_GLASS_BREAK, 1.2F, 1.5F);
                for (Player player : arena.near(spot, ICE_RADIUS)) hit(player, caster, 2 * power);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    Block floor = spot.clone().add(dx, 0, dz).getBlock();
                    if (dx * dx + dz * dz <= ICE_RADIUS * ICE_RADIUS && floor.getType().isAir() && floor.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid())
                        arena.temporary(floor, Material.SNOW, ICE_TICKS);
                }
                int[] tick = {0};
                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                    if (!arena.live() || (tick[0] += 10) > ICE_TICKS) { task.cancel(); return; }
                    Telegraph.ring(spot, ICE_RADIUS, Telegraph.FROST);
                    for (Player player : arena.near(spot, ICE_RADIUS)) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1));
                }, 0L, 10L);
            });
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
