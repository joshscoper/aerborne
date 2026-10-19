package net.nerdsmp.paper.mob;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class VanillaBossMechanicsService implements Listener, AutoCloseable {
    private static final double ENCOUNTER_RADIUS = 72.0D;

    private final JavaPlugin plugin;
    private final CustomMobManager mobs;
    private final NamespacedKey objectiveKey;
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final long phaseTimeoutMillis;
    private final boolean enabled;
    private BukkitTask tickTask;

    public VanillaBossMechanicsService(JavaPlugin plugin, CustomMobManager mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
        objectiveKey = new NamespacedKey(plugin, "boss_objective");
        enabled = plugin.getConfig().getBoolean("mobs.boss-mechanics.enabled", true);
        phaseTimeoutMillis = Math.max(30_000L,
            plugin.getConfig().getLong("mobs.boss-mechanics.phase-timeout-seconds", 90L) * 1_000L);
    }

    public void start() {
        if (!enabled || tickTask != null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::discoverLoadedBosses);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (enabled && kind(event.getEntity()) != null) {
            Bukkit.getScheduler().runTask(plugin, () -> encounter(event.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        if (!enabled || !(event.getEntity() instanceof LivingEntity boss)) {
            return;
        }
        VanillaBossKind kind = kind(boss);
        if (kind == null) {
            return;
        }
        Encounter encounter = encounter(boss);
        if (encounter.shielded) {
            event.setCancelled(true);
            return;
        }
        double maximum = maximumHealth(boss);
        int phase = BossEncounterRules.crossedPhase(
            boss.getHealth(), event.getFinalDamage(), maximum, encounter.phasesStarted, kind.thresholds());
        if (phase < 0) {
            return;
        }
        event.setCancelled(true);
        boss.setHealth(Math.max(1.0D, maximum * kind.thresholds()[phase]));
        beginShieldPhase(encounter, phase);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(objectiveKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            objectiveDefeated(entity.getUniqueId());
            return;
        }
        Encounter encounter = encounters.remove(entity.getUniqueId());
        if (encounter != null) {
            removeObjectives(encounter);
        }
    }

    private void discoverLoadedBosses() {
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (kind(entity) != null) {
                    entity.setInvulnerable(false);
                    encounter(entity);
                }
            }
        }
    }

    private Encounter encounter(LivingEntity boss) {
        return encounters.computeIfAbsent(boss.getUniqueId(), ignored -> {
            VanillaBossKind kind = kind(boss);
            int completed = kind == null ? 0 : BossEncounterRules.completedPhases(
                boss.getHealth(), maximumHealth(boss), kind.thresholds());
            return new Encounter(boss, kind, completed, System.currentTimeMillis());
        });
    }

    private void beginShieldPhase(Encounter encounter, int phase) {
        LivingEntity boss = encounter.boss;
        encounter.phasesStarted = phase + 1;
        encounter.shielded = true;
        encounter.phaseStartedAt = System.currentTimeMillis();
        encounter.lastMechanicAt = encounter.phaseStartedAt;
        boss.setInvulnerable(true);

        int players = Math.max(1, nearbyPlayers(boss, ENCOUNTER_RADIUS).size());
        int objectiveCount = Math.min(6, encounter.kind.baseObjectives() + (players - 1) / 2);
        encounter.objectives.clear();
        spawnObjectives(encounter, objectiveCount);
        announce(boss, phaseMessage(encounter.kind), Sound.BLOCK_BEACON_ACTIVATE);
    }

    private void spawnObjectives(Encounter encounter, int count) {
        if (encounter.kind == VanillaBossKind.ENDER_DRAGON) {
            spawnCrystals(encounter, count);
            return;
        }
        EntityType type = switch (encounter.kind) {
            case WITHER -> EntityType.WITHER_SKELETON;
            case ELDER_GUARDIAN -> EntityType.GUARDIAN;
            case WARDEN -> EntityType.VEX;
            case ENDER_DRAGON -> throw new IllegalStateException("Dragon objectives use crystals");
        };
        String name = switch (encounter.kind) {
            case WITHER -> "<dark_red><bold>Soulbound Guard</bold></dark_red>";
            case ELDER_GUARDIAN -> "<aqua><bold>Tidal Sentinel</bold></aqua>";
            case WARDEN -> "<dark_aqua><bold>Resonant Echo</bold></dark_aqua>";
            case ENDER_DRAGON -> "<light_purple><bold>Rift Crystal</bold></light_purple>";
        };
        for (int index = 0; index < count; index++) {
            Entity spawned = encounter.boss.getWorld().spawnEntity(
                objectiveLocation(encounter.boss, index, count, 7.0D), type);
            if (!(spawned instanceof LivingEntity objective)) {
                spawned.remove();
                continue;
            }
            prepareObjective(encounter, objective, name);
        }
    }

    private void spawnCrystals(Encounter encounter, int count) {
        for (int index = 0; index < count; index++) {
            Location location = objectiveLocation(encounter.boss, index, count, 11.0D);
            EnderCrystal crystal = encounter.boss.getWorld().spawn(location, EnderCrystal.class, spawned -> {
                spawned.setShowingBottom(false);
                spawned.setBeamTarget(encounter.boss.getLocation());
                spawned.setInvulnerable(false);
                spawned.getPersistentDataContainer().set(objectiveKey, PersistentDataType.BYTE, (byte) 1);
            });
            encounter.objectives.add(crystal.getUniqueId());
        }
    }

    private void prepareObjective(Encounter encounter, LivingEntity objective, String name) {
        objective.getPersistentDataContainer().set(objectiveKey, PersistentDataType.BYTE, (byte) 1);
        objective.customName(miniMessage.deserialize(name));
        objective.setCustomNameVisible(false);
        objective.setRemoveWhenFarAway(false);
        var health = objective.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double objectiveHealth = Math.min(100.0D, 24.0D + mobs.level(encounter.boss) * 0.35D);
            health.setBaseValue(objectiveHealth);
            objective.setHealth(objectiveHealth);
        }
        if (objective instanceof Mob mob) {
            nearestPlayer(encounter.boss).ifPresent(mob::setTarget);
        }
        encounter.objectives.add(objective.getUniqueId());
    }

    private Location objectiveLocation(LivingEntity boss, int index, int count, double radius) {
        double angle = Math.PI * 2.0D * index / Math.max(1, count);
        Location center = boss.getLocation();
        double y = Math.max(boss.getWorld().getMinHeight() + 2.0D,
            Math.min(boss.getWorld().getMaxHeight() - 3.0D, center.getY() + 1.0D));
        return new Location(boss.getWorld(), center.getX() + Math.cos(angle) * radius,
            y, center.getZ() + Math.sin(angle) * radius);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Encounter> iterator = encounters.values().iterator();
        while (iterator.hasNext()) {
            Encounter encounter = iterator.next();
            LivingEntity boss = encounter.boss;
            if (!boss.isValid() || boss.isDead()) {
                removeObjectives(encounter);
                iterator.remove();
                continue;
            }
            if (encounter.shielded) {
                encounter.objectives.removeIf(id -> {
                    Entity objective = Bukkit.getEntity(id);
                    return objective == null || !objective.isValid() || objective.isDead();
                });
                if (encounter.objectives.isEmpty()) {
                    endShieldPhase(encounter, false);
                } else if (now - encounter.phaseStartedAt >= phaseTimeoutMillis) {
                    encounter.enrageStacks++;
                    removeObjectives(encounter);
                    endShieldPhase(encounter, true);
                }
                continue;
            }
            if (now - encounter.lastMechanicAt >= encounter.kind.mechanicCooldownMillis()) {
                encounter.lastMechanicAt = now;
                telegraphMechanic(encounter);
            }
        }
    }

    private void endShieldPhase(Encounter encounter, boolean timedOut) {
        encounter.shielded = false;
        encounter.boss.setInvulnerable(false);
        String message = timedOut
            ? "<red>The ward ruptures, but the boss grows more dangerous.</red>"
            : "<green>The ward is broken. Strike now!</green>";
        announce(encounter.boss, message, Sound.BLOCK_BEACON_DEACTIVATE);
    }

    private void telegraphMechanic(Encounter encounter) {
        LivingEntity boss = encounter.boss;
        String warning = switch (encounter.kind) {
            case ENDER_DRAGON -> "<light_purple>Dragonfire marks the ground—move!</light_purple>";
            case WITHER -> "<dark_red>Soul Nova—leave the inner ring!</dark_red>";
            case ELDER_GUARDIAN -> "<aqua>Undertow—swim against the pull!</aqua>";
            case WARDEN -> "<dark_aqua>Echo Rupture—get outside the ring!</dark_aqua>";
        };
        List<Player> players = nearbyPlayers(boss, ENCOUNTER_RADIUS);
        players.forEach(player -> player.sendActionBar(miniMessage.deserialize(warning)));
        Location origin = encounter.kind == VanillaBossKind.ENDER_DRAGON
            ? nearestPlayer(boss).map(player -> player.getLocation().clone()).orElse(boss.getLocation().clone())
            : boss.getLocation().clone();
        Particle particle = switch (encounter.kind) {
            case ENDER_DRAGON -> Particle.DRAGON_BREATH;
            case WITHER -> Particle.SOUL_FIRE_FLAME;
            case ELDER_GUARDIAN -> Particle.BUBBLE_POP;
            case WARDEN -> Particle.SCULK_SOUL;
        };
        boss.getWorld().spawnParticle(particle, origin.clone().add(0.0D, 1.0D, 0.0D), 80,
            2.5D, 1.0D, 2.5D, 0.03D);
        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveMechanic(encounter, origin), 35L);
    }

    private void resolveMechanic(Encounter encounter, Location origin) {
        LivingEntity boss = encounter.boss;
        if (!boss.isValid() || boss.isDead() || encounter.shielded) {
            return;
        }
        double damage = mechanicDamage(encounter.kind) * (1.0D + encounter.enrageStacks * 0.15D);
        switch (encounter.kind) {
            case ENDER_DRAGON -> dragonfire(boss, origin, damage);
            case WITHER -> soulNova(boss, damage);
            case ELDER_GUARDIAN -> undertow(boss, damage);
            case WARDEN -> echoRupture(boss, damage);
        }
    }

    private void dragonfire(LivingEntity boss, Location target, double damage) {
        boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, target, 180, 3.5D, 1.0D, 3.5D, 0.04D);
        damageWithin(boss, target, 4.5D, damage, null);
    }

    private void soulNova(LivingEntity boss, double damage) {
        Location origin = boss.getLocation();
        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, origin, 160, 6.0D, 1.5D, 6.0D, 0.04D);
        damageWithin(boss, origin, 9.0D, damage, player -> {
            Vector away = player.getLocation().toVector().subtract(origin.toVector());
            if (away.lengthSquared() > 0.01D) {
                player.setVelocity(away.normalize().multiply(0.8D).setY(0.35D));
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        });
    }

    private void undertow(LivingEntity boss, double damage) {
        Location origin = boss.getLocation();
        for (Player player : nearbyPlayers(boss, 14.0D)) {
            Vector pull = origin.toVector().subtract(player.getLocation().toVector());
            if (pull.lengthSquared() > 0.01D) {
                player.setVelocity(pull.normalize().multiply(0.75D));
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));
            if (player.getLocation().distanceSquared(origin) <= 8.0D * 8.0D) {
                player.damage(damage, boss);
            }
        }
        boss.getWorld().spawnParticle(Particle.BUBBLE_POP, origin, 180, 7.0D, 2.0D, 7.0D, 0.05D);
    }

    private void echoRupture(LivingEntity boss, double damage) {
        Location origin = boss.getLocation();
        boss.getWorld().spawnParticle(Particle.SONIC_BOOM, origin.clone().add(0.0D, 1.0D, 0.0D), 1);
        damageWithin(boss, origin, 10.0D, damage, player ->
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0)));
    }

    private void damageWithin(
        LivingEntity boss,
        Location origin,
        double radius,
        double damage,
        java.util.function.Consumer<Player> effect
    ) {
        double radiusSquared = radius * radius;
        for (Player player : nearbyPlayers(boss, radius + 2.0D)) {
            if (player.getLocation().distanceSquared(origin) > radiusSquared) {
                continue;
            }
            if (effect != null) {
                effect.accept(player);
            }
            player.damage(damage, boss);
        }
    }

    private void objectiveDefeated(UUID objectiveId) {
        for (Encounter encounter : encounters.values()) {
            if (!encounter.objectives.remove(objectiveId)) {
                continue;
            }
            if (!encounter.objectives.isEmpty()) {
                announce(encounter.boss, "<gray>Ward anchors remaining: <white>"
                    + encounter.objectives.size() + "</white></gray>", Sound.BLOCK_AMETHYST_BLOCK_HIT);
            }
            return;
        }
    }

    private void announce(LivingEntity boss, String message, Sound sound) {
        for (Player player : nearbyPlayers(boss, ENCOUNTER_RADIUS)) {
            player.sendRichMessage(message);
            player.playSound(player.getLocation(), sound, 0.9F, 0.8F);
        }
    }

    private String phaseMessage(VanillaBossKind kind) {
        return switch (kind) {
            case ENDER_DRAGON -> "<light_purple><bold>Rift Ward:</bold> Shatter every crystal.</light_purple>";
            case WITHER -> "<dark_red><bold>Soul Ward:</bold> Defeat the soulbound guards.</dark_red>";
            case ELDER_GUARDIAN -> "<aqua><bold>Tidal Ward:</bold> Defeat the sentinels.</aqua>";
            case WARDEN -> "<dark_aqua><bold>Resonant Ward:</bold> Silence every echo.</dark_aqua>";
        };
    }

    private double mechanicDamage(VanillaBossKind kind) {
        return switch (kind) {
            case ENDER_DRAGON -> 6.0D;
            case WITHER -> 5.0D;
            case ELDER_GUARDIAN -> 4.0D;
            case WARDEN -> 8.0D;
        };
    }

    private List<Player> nearbyPlayers(LivingEntity boss, double radius) {
        double radiusSquared = radius * radius;
        return boss.getWorld().getPlayers().stream()
            .filter(player -> player.isOnline() && !player.isDead() && !player.getGameMode().isInvulnerable())
            .filter(player -> player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared)
            .toList();
    }

    private java.util.Optional<Player> nearestPlayer(LivingEntity boss) {
        return nearbyPlayers(boss, ENCOUNTER_RADIUS).stream()
            .min(java.util.Comparator.comparingDouble(player ->
                player.getLocation().distanceSquared(boss.getLocation())));
    }

    private VanillaBossKind kind(LivingEntity entity) {
        return VanillaBossKind.fromEntityType(entity.getType().name()).orElse(null);
    }

    private double maximumHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, entity.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private void removeObjectives(Encounter encounter) {
        Set<UUID> ids = new HashSet<>(encounter.objectives);
        encounter.objectives.clear();
        ids.stream().map(Bukkit::getEntity).filter(java.util.Objects::nonNull).forEach(Entity::remove);
    }

    @Override
    public void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        encounters.values().forEach(encounter -> {
            if (encounter.boss.isValid()) {
                encounter.boss.setInvulnerable(false);
            }
            removeObjectives(encounter);
        });
        encounters.clear();
    }

    private static final class Encounter {
        private final LivingEntity boss;
        private final VanillaBossKind kind;
        private final Set<UUID> objectives = new HashSet<>();
        private int phasesStarted;
        private int enrageStacks;
        private boolean shielded;
        private long phaseStartedAt;
        private long lastMechanicAt;

        private Encounter(LivingEntity boss, VanillaBossKind kind, int phasesStarted, long now) {
            this.boss = boss;
            this.kind = kind;
            this.phasesStarted = phasesStarted;
            lastMechanicAt = now;
        }
    }
}
