package net.nerdsmp.paper.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.PartyService;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.engine.mob.MobBlueprint;
import net.nerdsmp.paper.engine.mob.MobSnapshotCache;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

public final class CustomMobManager implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final PartyService parties;
    private final PaperItemService items;
    private final NamespacedKey mobIdKey;
    private final NamespacedKey mobLevelKey;
    private final NamespacedKey abilityDamageKey;
    private final NamespacedKey markRewardKey;
    private final NamespacedKey baseHealthKey;
    private final NamespacedKey baseDamageKey;
    private final NamespacedKey baseAbilityDamageKey;
    private final NamespacedKey baseMarkRewardKey;
    private final NamespacedKey mobTierKey;
    private final NamespacedKey mobDisplayNameKey;
    private final NamespacedKey damageMultiplierKey;
    private final NamespacedKey rewardMultiplierKey;
    private final NamespacedKey combatGroupSizeKey;
    private final NamespacedKey bossObjectiveKey;
    private final NamespacedKey islandMobCountKey;
    private final NamespacedKey zoneLevelKey;
    private final Map<String, MobDefinition> shippedDefinitions;
    private volatile Map<String, MobDefinition> definitions;
    private final NamespacedKey engineVersionKey;
    private final NamespacedKey engineNameKey;
    private final NamespacedKey engineRankKey;
    private final NamespacedKey engineAbilityKey;
    private final NamespacedKey engineRadiusKey;
    private final NamespacedKey enginePowerKey;
    private final NamespacedKey engineCooldownKey;
    private final NamespacedKey engineExperienceKey;
    private final NamespacedKey engineLootKey;
    private final NamespacedKey engineSpeedKey;
    private final Set<UUID> pendingAbilities = ConcurrentHashMap.newKeySet();
    private final MobSnapshotCache<MobDefinition> authoredSnapshots = new MobSnapshotCache<>(MobSnapshotCache.MAX_ENTRIES);
    private final org.bukkit.scheduler.BukkitTask startupTask;
    private final org.bukkit.scheduler.BukkitTask abilityTask;
    private int cleanupCycles;
    private volatile boolean closed;
    private final Map<UUID, Long> lastAbilityAt = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> combatants = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final double ambientConversionChance;
    private final BooleanSupplier worldEventsEnabled;
    private final MobScaling.Tuning scaling;
    private final double eliteChancePerLevel;
    private final double combatRadiusSquared;
    private final long combatTimeoutMillis;
    private final double maximumHealth;
    private final double partyBossRewardPercent;
    private final double partyRewardRadius;
    private java.util.function.Predicate<LivingEntity> replacementLoot = entity -> false;

    public CustomMobManager(
        JavaPlugin plugin,
        SessionRegistry sessions,
        EconomyService economy,
        PartyService parties,
        PaperItemService items,
        double ambientConversionChance,
        BooleanSupplier worldEventsEnabled
    ) {
        this.plugin = plugin;
        islandMobCountKey = new NamespacedKey(plugin, "island_mob_count");
        zoneLevelKey = new NamespacedKey(plugin, "zone_level");
        this.sessions = sessions;
        this.economy = economy;
        this.parties = parties;
        this.items = items;
        this.worldEventsEnabled = worldEventsEnabled;
        this.ambientConversionChance = Math.max(0.0D, Math.min(0.25D, ambientConversionChance));
        scaling = new MobScaling.Tuning(
            Math.max(16.0D, plugin.getConfig().getDouble("mobs.scaling.blocks-per-level", 128.0D)),
            boundedRate("mobs.scaling.health-per-level", 0.01D),
            boundedRate("mobs.scaling.damage-per-level", 0.006D),
            boundedRate("mobs.scaling.reward-per-level", 0.008D),
            Math.max(1, Math.min(16, plugin.getConfig().getInt("mobs.scaling.maximum-combatants", 8))),
            boundedRate("mobs.scaling.elite-health-per-extra-player", 0.30D),
            boundedRate("mobs.scaling.elite-damage-per-extra-player", 0.06D),
            boundedRate("mobs.scaling.elite-reward-per-extra-player", 0.25D),
            boundedRate("mobs.scaling.boss-health-per-extra-player", 0.35D),
            boundedRate("mobs.scaling.boss-damage-per-extra-player", 0.03D),
            boundedRate("mobs.scaling.boss-reward-per-extra-player", 0.35D)
        );
        eliteChancePerLevel = boundedRate("mobs.scaling.elite-chance-per-level", 0.0005D);
        double combatRadius = Math.max(16.0D,
            plugin.getConfig().getDouble("mobs.scaling.combat-radius", 64.0D));
        combatRadiusSquared = combatRadius * combatRadius;
        combatTimeoutMillis = Math.max(5_000L,
            plugin.getConfig().getLong("mobs.scaling.combat-timeout-seconds", 20L) * 1_000L);
        maximumHealth = Math.max(100.0D,
            Math.min(1_024.0D, plugin.getConfig().getDouble("mobs.scaling.maximum-health", 1_024.0D)));
        partyBossRewardPercent = boundedRate("mobs.party-boss-reward-percent", 0.60D);
        partyRewardRadius = Math.max(8.0D, plugin.getConfig().getDouble("mobs.party-reward-radius", 48.0D));
        mobIdKey = new NamespacedKey(plugin, "mob_id");
        mobLevelKey = new NamespacedKey(plugin, "mob_level");
        abilityDamageKey = new NamespacedKey(plugin, "mob_ability_damage");
        markRewardKey = new NamespacedKey(plugin, "mob_mark_reward");
        baseHealthKey = new NamespacedKey(plugin, "mob_base_health");
        baseDamageKey = new NamespacedKey(plugin, "mob_base_damage");
        baseAbilityDamageKey = new NamespacedKey(plugin, "mob_base_ability_damage");
        baseMarkRewardKey = new NamespacedKey(plugin, "mob_base_mark_reward");
        mobTierKey = new NamespacedKey(plugin, "mob_tier");
        mobDisplayNameKey = new NamespacedKey(plugin, "mob_display_name");
        damageMultiplierKey = new NamespacedKey(plugin, "mob_damage_multiplier");
        rewardMultiplierKey = new NamespacedKey(plugin, "mob_reward_multiplier");
        combatGroupSizeKey = new NamespacedKey(plugin, "mob_combat_group_size");
        bossObjectiveKey = new NamespacedKey(plugin, "boss_objective");
        engineVersionKey = new NamespacedKey(plugin, "engine_mob_version");
        engineNameKey = new NamespacedKey(plugin, "engine_mob_name");
        engineRankKey = new NamespacedKey(plugin, "engine_mob_rank");
        engineAbilityKey = new NamespacedKey(plugin, "engine_mob_ability");
        engineRadiusKey = new NamespacedKey(plugin, "engine_mob_radius");
        enginePowerKey = new NamespacedKey(plugin, "engine_mob_power");
        engineCooldownKey = new NamespacedKey(plugin, "engine_mob_cooldown");
        engineExperienceKey = new NamespacedKey(plugin, "engine_mob_experience");
        engineLootKey = new NamespacedKey(plugin, "engine_mob_loot");
        engineSpeedKey = new NamespacedKey(plugin, "engine_mob_speed");
        shippedDefinitions = load(plugin); definitions = shippedDefinitions;
        startupTask = Bukkit.getScheduler().runTask(plugin, this::upgradeLoadedMobs);
        abilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAbilities, 20L, 20L);
    }

    public LivingEntity spawn(String rawId, Location location) {
        MobDefinition definition = definitions.get(rawId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown custom mob: " + rawId);
        }
        Entity spawned = location.getWorld().spawnEntity(location, definition.type, CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            throw new IllegalStateException(definition.type + " is not a living entity type");
        }
        apply(living, definition);
        return living;
    }

    public List<String> ids() {
        return definitions.keySet().stream().sorted().toList();
    }

    public Set<String> shippedIds() { return shippedDefinitions.keySet(); }

    public synchronized void validateAuthored(Collection<MobBlueprint> blueprints) {
        if (blueprints.size() > MobBlueprint.MAX_BLUEPRINTS) throw new IllegalArgumentException("At most 256 authored mobs are supported.");
        Set<String> seen = new java.util.HashSet<>();
        for (MobBlueprint blueprint : blueprints) {
            if (!seen.add(blueprint.id())) throw new IllegalArgumentException("Duplicate mob ID: " + blueprint.id());
            if (shippedDefinitions.containsKey(blueprint.id())) throw new IllegalArgumentException("Cannot replace a shipped encounter: " + blueprint.id());
            MobDefinition existing = definitions.get(blueprint.id());
            if (existing != null && existing.type != blueprint.entityType()) throw new IllegalArgumentException("Published entity types are locked: " + blueprint.id());
        }
    }

    /** New-spawn-only publication. Existing entities retain their authored PDC snapshots and current health. */
    public synchronized void applyAuthored(Collection<MobBlueprint> blueprints) {
        validateAuthored(blueprints);
        Map<String, MobDefinition> next = new HashMap<>(shippedDefinitions);
        for (MobBlueprint mob : blueprints) next.put(mob.id(), authoredDefinition(mob));
        definitions = Map.copyOf(next);
    }

    public Optional<MobBlueprint> authoringBlueprint(String id) {
        MobDefinition definition = definitions.get(id);
        if (definition == null || definition.boss || !net.nerdsmp.paper.zone.ZoneMobTypes.allowed(definition.type)) return Optional.empty();
        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(definition.name));
        String template = switch (definition.type) { case SKELETON, STRAY, BOGGED, PILLAGER -> "ranger"; case WITCH -> "caster"; default -> "brawler"; };
        return Optional.of(new MobBlueprint(definition.id, template, plainName, definition.type.name().toLowerCase(Locale.ROOT), definition.rank,
            definition.defaultLevel, definition.health, definition.attackDamage, definition.movementSpeed > 0 ? definition.movementSpeed : 0.23,
            definition.ability, definition.abilityRadius, definition.abilityDamage, (int) definition.abilityCooldownSeconds,
            Math.max(0, definition.experienceReward), definition.lootProfile));
    }

    public Optional<String> authoredLootProfile(LivingEntity entity) {
        MobDefinition definition = definition(entity);
        return definition != null && definition.authored && !definition.lootProfile.isEmpty()
            ? Optional.of(definition.lootProfile) : Optional.empty();
    }

    private MobDefinition authoredDefinition(MobBlueprint mob) {
        return new MobDefinition(mob.id(), miniMessage.escapeTags(mob.name()), mob.entityType(), false, mob.health(), mob.attackDamage(),
            mob.abilityRadius(), mob.abilityPower(), mob.abilityCooldownSeconds(), 0, null, 0,
            true, mob.rank(), mob.level(), mob.movementSpeed(), mob.ability(), mob.experienceReward(), mob.lootProfile());
    }

    /** Known content identity for authored loot assignments; never trust a display name. */
    public java.util.Optional<String> zoneMobId(LivingEntity entity) {
        MobDefinition definition = definition(entity);
        return definition == null ? java.util.Optional.empty() : java.util.Optional.of(definition.id);
    }

    public void replacementLoot(java.util.function.Predicate<LivingEntity> policy) {
        replacementLoot = java.util.Objects.requireNonNull(policy);
    }

    public void forget(LivingEntity entity) {
        lastAbilityAt.remove(entity.getUniqueId()); combatants.remove(entity.getUniqueId()); pendingAbilities.remove(entity.getUniqueId());
        authoredSnapshots.remove(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof LivingEntity living) forget(living);
    }

    @Override public void close() {
        closed = true; startupTask.cancel(); abilityTask.cancel();
        authoredSnapshots.clear(); pendingAbilities.clear(); lastAbilityAt.clear(); combatants.clear();
    }

    /** Resolve only reviewed ground mobs. Boss encounters need their own capped mechanic lifecycle. */
    public EntityType zoneType(String id) {
        EntityType type;
        if (id.startsWith("vanilla:")) {
            try { type=EntityType.valueOf(id.substring(8).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown mob: "+id); }
        } else if (id.startsWith("custom:")) {
            MobDefinition definition=definitions.get(id.substring(7));
            if (definition==null) throw new IllegalArgumentException("Unknown mob: "+id);
            if (definition.boss) throw new IllegalArgumentException("Boss encounters are not supported by ambient zone spawners.");
            type=definition.type;
        } else throw new IllegalArgumentException("Use vanilla:<type> or custom:<id>.");
        if (!net.nerdsmp.paper.zone.ZoneMobTypes.allowed(type)) throw new IllegalArgumentException("That mob needs a specialized spawning habitat: "+type);
        return type;
    }

    public LivingEntity spawnZone(String id, Location location, int level, java.util.function.Consumer<LivingEntity> beforeSpawn) {
        if (level<1 || level>100) throw new IllegalArgumentException("Zone mob level must be 1–100");
        EntityType type=zoneType(id);
        Class<? extends LivingEntity> entityClass=type.getEntityClass().asSubclass(LivingEntity.class);
        MobDefinition definition=id.startsWith("custom:") ? definitions.get(id.substring(7)) : null;
        return location.getWorld().spawn(location,entityClass,CreatureSpawnEvent.SpawnReason.CUSTOM,false,entity-> {
            entity.getPersistentDataContainer().set(zoneLevelKey,PersistentDataType.INTEGER,level);
            if (entity instanceof org.bukkit.entity.Ageable ageable) ageable.setAdult();
            if (entity instanceof org.bukkit.entity.Zombie zombie) zombie.setBaby(false);
            // Natural randomization is disabled to prevent uncapped jockey passengers; supply essential combat gear explicitly.
            var equipment = entity.getEquipment();
            if (equipment != null) {
                Material weapon = switch (type) {
                    case SKELETON, STRAY, BOGGED -> Material.BOW;
                    case PILLAGER -> Material.CROSSBOW;
                    case WITHER_SKELETON -> Material.STONE_SWORD;
                    case VINDICATOR -> Material.IRON_AXE;
                    default -> null;
                };
                if (weapon != null) {
                    equipment.setItemInMainHand(new ItemStack(weapon));
                    equipment.setItemInMainHandDropChance(0.0F);
                }
            }
            if (definition==null) applyVanilla(entity,MobTier.STANDARD); else apply(entity,definition);
            beforeSpawn.accept(entity);
        });
    }

    public boolean isCustomMob(LivingEntity entity) {
        return definition(entity) != null;
    }

    public boolean isBoss(LivingEntity entity) {
        MobDefinition definition = definition(entity);
        return (definition != null && definition.boss) || isVanillaBoss(entity.getType())
            || tier(entity) == MobTier.BOSS;
    }

    public boolean isElite(LivingEntity entity) {
        return tier(entity) == MobTier.ELITE;
    }

    public int level(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(mobLevelKey, PersistentDataType.INTEGER, 1);
    }

    public double rewardMultiplier(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(
            rewardMultiplierKey, PersistentDataType.DOUBLE, 1.0D);
    }

    public long markReward(LivingEntity entity) {
        return Math.max(0L, entity.getPersistentDataContainer().getOrDefault(
            markRewardKey, PersistentDataType.LONG, 0L));
    }

    public Component displayName(LivingEntity entity) {
        String markup = entity.getPersistentDataContainer().get(mobDisplayNameKey, PersistentDataType.STRING);
        Component name;
        if (markup != null && !markup.isBlank()) {
            name = miniMessage.deserialize(markup);
        } else {
            Component nativeName = entity.customName();
            name = nativeName == null
                ? miniMessage.deserialize("<white><bold>" + humanName(entity.getType()) + "</bold></white>")
                : nativeName;
        }
        var data = entity.getPersistentDataContainer();
        Integer storedCount = data.has(islandMobCountKey, PersistentDataType.INTEGER)
            ? data.get(islandMobCountKey, PersistentDataType.INTEGER) : null;
        int count = storedCount != null && storedCount >= 1 && storedCount <= 16 ? storedCount : 1;
        return count > 1 ? name.append(Component.text(" ×" + count, net.kyori.adventure.text.format.NamedTextColor.AQUA)) : name;
    }

    public boolean isMechanicObjective(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(bossObjectiveKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(zoneLevelKey,PersistentDataType.INTEGER)) return;
        if (!scalable(entity)) {
            return;
        }
        if (isVanillaBoss(event.getEntityType())) {
            applyVanilla(entity, MobTier.BOSS);
            return;
        }
        boolean natural = event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL;
        double eliteChance = Math.min(0.25D,
            ambientConversionChance + distanceScale(entity.getLocation(), MobTier.ELITE, 1).level() * eliteChancePerLevel);
        if (natural && ThreadLocalRandom.current().nextDouble() < eliteChance) {
            List<MobDefinition> candidates = definitions.values().stream()
                .filter(definition -> !definition.authored && !definition.boss && definition.type == event.getEntityType())
                .toList();
            if (worldEventsEnabled.getAsBoolean() && !candidates.isEmpty()) {
                apply(entity, candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            } else {
                applyVanilla(entity, MobTier.ELITE);
            }
        } else {
            applyVanilla(entity, MobTier.STANDARD);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        LivingEntity source = attackingMob(event);
        Player attackingPlayer = attackingPlayer(event);
        if (event.getEntity() instanceof LivingEntity target && attackingPlayer != null
            && tier(target) != MobTier.STANDARD) {
            engage(target, attackingPlayer);
        }
        if (source == null) return;
        if (event.getEntity() instanceof Player targetPlayer) {
            if (tier(source) != MobTier.STANDARD) {
                engage(source, targetPlayer);
            }
        }
        MobDefinition definition = definition(source);
        boolean authoredArrow = event.getEntity() instanceof LivingEntity && definition != null && definition.authored
            && event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && source.equals(arrow.getShooter());
        if (authoredArrow) {
            // Mob bows/crossbows otherwise use vanilla velocity-randomized arrow damage, not
            // ATTACK_DAMAGE. Apply authored power to every living target, before player defense.
            event.setDamage(definition.attackDamage);
        }
        // Legacy mobs retain their player-only scaling; authored arrows receive it exactly once.
        if (event.getEntity() instanceof Player || authoredArrow) {
            double multiplier = source.getPersistentDataContainer().getOrDefault(
                damageMultiplierKey, PersistentDataType.DOUBLE, 1.0D);
            if (multiplier > 0.0D && Math.abs(multiplier - 1.0D) > 1.0E-6D) {
                event.setDamage(event.getDamage() * multiplier);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity mob && event.getTarget() instanceof Player player
            && tier(mob) != MobTier.STANDARD) {
            engage(mob, player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.isCancelled()) return;
        forget(event.getEntity());
        if (isMechanicObjective(event.getEntity())) {
            return;
        }
        MobDefinition definition = definition(event.getEntity());
        if (definition != null && definition.authored)
            event.setDroppedExp(event.getEntity().getKiller() == null ? 0 : definition.experienceReward);
        double experienceMultiplier = rewardMultiplier(event.getEntity());
        if (experienceMultiplier > 1.0D && event.getDroppedExp() > 0) {
            event.setDroppedExp((int) Math.min(10_000L,
                Math.max(event.getDroppedExp(), Math.round(event.getDroppedExp() * experienceMultiplier))));
        }
        if (definition == null) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && definition.boss) {
            AccountId accountId = sessions.account(killer.getUniqueId()).orElse(null);
            long reward = markReward(event.getEntity());
            rewardParticipants(event.getEntity(), definition, killer, accountId, reward);
        }
        if (!replacementLoot.test(event.getEntity()) && definition.relicDrop != null
            && ThreadLocalRandom.current().nextDouble() < definition.relicChance) {
            ItemStack relic = items.createLoot(new ItemId(definition.relicDrop),
                definition.boss ? 3 : 2, true, ThreadLocalRandom.current().nextLong());
            event.getDrops().add(relic);
        }
    }

    private void rewardParticipants(
        LivingEntity defeated,
        MobDefinition definition,
        Player killer,
        AccountId killerAccount,
        long reward
    ) {
        if (killerAccount == null || reward <= 0L) {
            return;
        }
        LinkedHashSet<AccountId> recipients = new LinkedHashSet<>();
        recipients.add(killerAccount);
        if (definition.boss) {
            parties.partyOf(killerAccount).toCompletableFuture().getNow(java.util.Optional.empty())
                .ifPresent(party -> party.members().stream()
                    .filter(member -> nearbyPartyMember(member, defeated.getLocation()))
                    .forEach(recipients::add));
        }
        for (AccountId recipient : recipients) {
            boolean killerReward = recipient.equals(killerAccount);
            long amount = killerReward ? reward : Math.max(1L, Math.round(reward * partyBossRewardPercent));
            economy.credit(
                recipient,
                amount,
                new EconomyService.TransactionReason(
                    "mob." + definition.id,
                    definition.boss && !killerReward ? "Party boss reward" : (definition.boss ? "Boss reward" : "Hunt reward")
                ),
                "mob-reward:" + defeated.getUniqueId() + ":" + recipient
            );
            if (!killerReward) {
                sessions.player(recipient).map(Bukkit::getPlayer).filter(java.util.Objects::nonNull)
                    .ifPresent(player -> player.sendRichMessage("<gold>Your party earned " + amount
                        + " Marks from the boss.</gold>"));
            }
        }
    }

    private boolean nearbyPartyMember(AccountId accountId, Location location) {
        Player player = sessions.player(accountId).map(Bukkit::getPlayer).orElse(null);
        return player != null && player.isOnline() && player.getWorld().equals(location.getWorld())
            && player.getLocation().distanceSquared(location) <= partyRewardRadius * partyRewardRadius;
    }

    private void tickAbilities() {
        if (closed) return;
        if (++cleanupCycles >= 5) {
            cleanupCycles = 0;
            authoredSnapshots.retain(uuid -> {
                Entity entity = Bukkit.getEntity(uuid);
                return entity instanceof LivingEntity living && living.isValid() && !living.isDead();
            });
            lastAbilityAt.keySet().removeIf(uuid -> Bukkit.getEntity(uuid) == null);
            combatants.keySet().removeIf(uuid -> Bukkit.getEntity(uuid) == null);
        }
        long now = System.currentTimeMillis();
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                MobDefinition definition = definition(entity);
                if (entity.isDead() || !entity.isValid()) {
                    continue;
                }
                if (tier(entity) != MobTier.STANDARD) {
                    refreshCombatScale(entity, now);
                }
                if (definition == null || definition.ability == MobBlueprint.Ability.NONE) {
                    continue;
                }
                long last = lastAbilityAt.getOrDefault(entity.getUniqueId(), 0L);
                if (now - last < definition.abilityCooldownSeconds * 1000L) {
                    continue;
                }
                Player target = nearestPlayer(entity, definition.abilityRadius + 8.0D);
                if (target == null) {
                    continue;
                }
                if (definition.ability == MobBlueprint.Ability.HEAL_SELF) {
                    var maximum = entity.getAttribute(Attribute.MAX_HEALTH);
                    if (maximum == null || entity.getHealth() >= maximum.getValue() - 0.01) continue;
                }
                if (pendingAbilities.size() >= 512 || !pendingAbilities.add(entity.getUniqueId())) continue;
                lastAbilityAt.put(entity.getUniqueId(), now);
                if (definition.ability == MobBlueprint.Ability.HEAL_SELF) telegraphHeal(entity, definition);
                else telegraphPulse(entity, definition);
            }
        }
    }

    private void upgradeLoadedMobs() {
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                MobDefinition definition = definition(entity);
                if (definition != null
                    && !entity.getPersistentDataContainer().has(mobTierKey, PersistentDataType.STRING)) {
                    apply(entity, definition);
                } else if (definition == null && scalable(entity)
                    && !entity.getPersistentDataContainer().has(mobTierKey, PersistentDataType.STRING)) {
                    applyVanilla(entity, isVanillaBoss(entity.getType()) ? MobTier.BOSS : MobTier.STANDARD);
                }
                if (tier(entity) != MobTier.STANDARD) {
                    int groupSize = entity.getPersistentDataContainer().getOrDefault(
                        combatGroupSizeKey, PersistentDataType.INTEGER, 1);
                    updateName(entity, tier(entity), entityScale(entity, tier(entity), groupSize));
                    entity.setCustomNameVisible(false);
                }
            }
        }
    }

    private void telegraphPulse(LivingEntity entity, MobDefinition definition) {
        Location origin = entity.getLocation().add(0.0D, entity.getHeight() * 0.5D, 0.0D);
        origin.getWorld().spawnParticle(Particle.PORTAL, origin, definition.boss ? 80 : 35,
            definition.abilityRadius * 0.35D, 0.6D, definition.abilityRadius * 0.35D, 0.05D);
        origin.getWorld().playSound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, definition.boss ? 0.55F : 0.9F);
        // Authored pulses advertise a fixed ground footprint, giving players a genuine escape window.
        Location footprint = entity.getLocation().clone();
        if (definition.authored) {
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16;
                footprint.getWorld().spawnParticle(Particle.END_ROD, footprint.clone().add(
                    Math.cos(angle) * definition.abilityRadius, 0.15, Math.sin(angle) * definition.abilityRadius), 1, 0, 0, 0, 0);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingAbilities.remove(entity.getUniqueId());
            if (closed || !entity.isValid() || entity.isDead()) {
                return;
            }
            Location pulse = definition.authored ? footprint : entity.getLocation();
            if (!entity.getWorld().equals(pulse.getWorld())) return;
            pulse.getWorld().spawnParticle(Particle.SONIC_BOOM, pulse.clone().add(0.0D, 1.0D, 0.0D), 1);
            for (Entity nearby : pulse.getWorld().getNearbyEntities(pulse,
                definition.abilityRadius,
                definition.abilityRadius,
                definition.abilityRadius
            )) {
                if (nearby instanceof Player player && !player.isDead() && !player.getGameMode().isInvulnerable()
                    && zoneCombatAllowed(entity,player) && entity.hasLineOfSight(player)
                    && (!definition.authored || player.getLocation().distanceSquared(pulse) <= definition.abilityRadius * definition.abilityRadius)) {
                    double scaledDamage = entity.getPersistentDataContainer().getOrDefault(
                        abilityDamageKey, PersistentDataType.DOUBLE, definition.abilityDamage);
                    player.damage(scaledDamage, entity);
                }
            }
        }, 30L);
    }

    private void telegraphHeal(LivingEntity entity, MobDefinition definition) {
        Location origin = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        origin.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, origin, 20, 0.5, 0.5, 0.5, 0);
        origin.getWorld().playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 0.8F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingAbilities.remove(entity.getUniqueId());
            if (closed || !entity.isValid() || entity.isDead() || nearestPlayer(entity, definition.abilityRadius + 8) == null) return;
            var maximum = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maximum == null) return;
            double healed = Math.min(maximum.getValue(), entity.getHealth() + definition.abilityDamage);
            if (healed <= entity.getHealth()) return;
            entity.setHealth(healed);
            entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, entity.getHeight(), 0), 4, 0.4, 0.2, 0.4, 0);
        }, 30L);
    }

    private Player nearestPlayer(LivingEntity source, double radius) {
        Location location=source.getLocation();
        Player nearest = null;
        double nearestDistance = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.isDead() || player.getGameMode().isInvulnerable() || !zoneCombatAllowed(source,player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance < nearestDistance && source.hasLineOfSight(player)) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private void apply(LivingEntity entity, MobDefinition definition) {
        entity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, definition.id);
        if (definition.authored) {
            var data = entity.getPersistentDataContainer();
            if (!data.has(zoneLevelKey, PersistentDataType.INTEGER)) data.set(zoneLevelKey, PersistentDataType.INTEGER, definition.defaultLevel);
            data.set(engineVersionKey, PersistentDataType.INTEGER, 1);
            data.set(engineNameKey, PersistentDataType.STRING, definition.name);
            data.set(engineRankKey, PersistentDataType.STRING, definition.rank.name());
            data.set(engineAbilityKey, PersistentDataType.STRING, definition.ability.name());
            data.set(engineRadiusKey, PersistentDataType.DOUBLE, definition.abilityRadius);
            data.set(enginePowerKey, PersistentDataType.DOUBLE, definition.abilityDamage);
            data.set(engineCooldownKey, PersistentDataType.INTEGER, (int) definition.abilityCooldownSeconds);
            data.set(engineExperienceKey, PersistentDataType.INTEGER, definition.experienceReward);
            data.set(engineLootKey, PersistentDataType.STRING, definition.lootProfile);
            data.set(engineSpeedKey, PersistentDataType.DOUBLE, definition.movementSpeed);
            authoredSnapshots.put(entity.getUniqueId(), definition.id, entity.getType().name(), definition);
            var movement = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (movement != null) movement.setBaseValue(definition.movementSpeed);
        }
        initializeScale(entity, definition.boss ? MobTier.BOSS : (definition.rank == MobBlueprint.Rank.NORMAL ? MobTier.STANDARD : MobTier.ELITE),
            definition.health, definition.attackDamage, definition.abilityDamage, definition.markReward, true);
        entity.setRemoveWhenFarAway(!definition.boss);
    }

    private void applyVanilla(LivingEntity entity, MobTier tier) {
        var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        var damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        double baseHealth = maxHealth == null ? Math.max(1.0D, entity.getHealth()) : maxHealth.getBaseValue();
        double baseDamage = damage == null ? 0.0D : damage.getBaseValue();
        initializeScale(entity, tier, baseHealth, baseDamage, 0.0D, 0L, true);
    }

    private void initializeScale(
        LivingEntity entity,
        MobTier tier,
        double baseHealth,
        double baseDamage,
        double baseAbilityDamage,
        long baseMarkReward,
        boolean restoreToFullHealth
    ) {
        var data = entity.getPersistentDataContainer();
        data.set(baseHealthKey, PersistentDataType.DOUBLE, Math.max(1.0D, baseHealth));
        data.set(baseDamageKey, PersistentDataType.DOUBLE, Math.max(0.0D, baseDamage));
        data.set(baseAbilityDamageKey, PersistentDataType.DOUBLE, Math.max(0.0D, baseAbilityDamage));
        data.set(baseMarkRewardKey, PersistentDataType.LONG, Math.max(0L, baseMarkReward));
        data.set(mobTierKey, PersistentDataType.STRING, tier.name());
        applyScale(entity, tier, 1, restoreToFullHealth);
    }

    private void refreshCombatScale(LivingEntity entity, long now) {
        MobTier tier = tier(entity);
        if (tier == MobTier.STANDARD) {
            return;
        }
        Map<UUID, Long> roster = combatants.computeIfAbsent(entity.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        roster.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            return now - entry.getValue() > combatTimeoutMillis || player == null || !player.isOnline()
                || player.isDead() || !player.getWorld().equals(entity.getWorld())
                || player.getLocation().distanceSquared(entity.getLocation()) > combatRadiusSquared;
        });
        int groupSize = Math.max(1, roster.size());
        int previous = entity.getPersistentDataContainer().getOrDefault(
            combatGroupSizeKey, PersistentDataType.INTEGER, 1);
        if (groupSize != previous) {
            applyScale(entity, tier, groupSize, false);
        }
    }

    private void applyScale(LivingEntity entity, MobTier tier, int groupSize, boolean restoreToFullHealth) {
        var data = entity.getPersistentDataContainer();
        double baseHealth = data.getOrDefault(baseHealthKey, PersistentDataType.DOUBLE, 20.0D);
        double baseDamage = data.getOrDefault(baseDamageKey, PersistentDataType.DOUBLE, 0.0D);
        double baseAbilityDamage = data.getOrDefault(baseAbilityDamageKey, PersistentDataType.DOUBLE, 0.0D);
        long baseMarkReward = data.getOrDefault(baseMarkRewardKey, PersistentDataType.LONG, 0L);
        MobScaling.Scale scale = entityScale(entity, tier, groupSize);
        var healthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null) {
            double oldMaximum = Math.max(1.0D, healthAttribute.getValue());
            double healthRatio = Math.max(0.0D, Math.min(1.0D, entity.getHealth() / oldMaximum));
            double scaledHealth = Math.max(1.0D, Math.min(maximumHealth,
                baseHealth * scale.healthMultiplier()));
            healthAttribute.setBaseValue(scaledHealth);
            entity.setHealth(restoreToFullHealth ? scaledHealth
                : Math.max(0.01D, Math.min(scaledHealth, scaledHealth * healthRatio)));
        }
        var damageAttribute = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttribute != null) {
            damageAttribute.setBaseValue(baseDamage);
        }
        data.set(mobLevelKey, PersistentDataType.INTEGER, scale.level());
        data.set(combatGroupSizeKey, PersistentDataType.INTEGER, scale.groupSize());
        data.set(damageMultiplierKey, PersistentDataType.DOUBLE, scale.damageMultiplier());
        data.set(rewardMultiplierKey, PersistentDataType.DOUBLE, scale.rewardMultiplier());
        data.set(abilityDamageKey, PersistentDataType.DOUBLE, baseAbilityDamage);
        data.set(markRewardKey, PersistentDataType.LONG,
            Math.max(baseMarkReward, Math.round(baseMarkReward * scale.rewardMultiplier())));
        updateName(entity, tier, scale);
    }

    private void updateName(LivingEntity entity, MobTier tier, MobScaling.Scale scale) {
        if (tier == MobTier.STANDARD) {
            if(entity.getPersistentDataContainer().has(zoneLevelKey,PersistentDataType.INTEGER)) {
                MobDefinition definition = definition(entity);
                String name = definition == null ? "<white>" + humanName(entity.getType()) + "</white>" : definition.name;
                entity.getPersistentDataContainer().set(mobDisplayNameKey,PersistentDataType.STRING,
                    "<dark_gray>[</dark_gray><red>Lv "+scale.level()+"</red><dark_gray>]</dark_gray> " + name);
                entity.customName(null); entity.setCustomNameVisible(false);
            }
            return;
        }
        MobDefinition definition = definition(entity);
        String baseName = definition == null ? "<white>" + humanName(entity.getType()) + "</white>" : definition.name;
        String tierName = tier == MobTier.BOSS ? "<dark_red><bold>BOSS</bold></dark_red>"
            : "<gold><bold>ELITE</bold></gold>";
        String group = scale.groupSize() > 1
            ? " <dark_gray>◆</dark_gray><yellow>" + scale.groupSize() + "</yellow>" : "";
        String markup = "<dark_gray>[</dark_gray><red>Lv " + scale.level()
            + "</red> " + tierName + group + "<dark_gray>]</dark_gray> " + baseName;
        entity.getPersistentDataContainer().set(mobDisplayNameKey, PersistentDataType.STRING, markup);
        // The per-player TextDisplay is the only label renderer. Keeping a native custom name would
        // make the client draw a second copy whenever the player aims at the mob.
        entity.customName(null);
        entity.setCustomNameVisible(false);
    }

    private MobDefinition definition(LivingEntity entity) {
        if (closed) return null;
        var data = entity.getPersistentDataContainer();
        if (!data.has(mobIdKey, PersistentDataType.STRING)) { authoredSnapshots.remove(entity.getUniqueId()); return null; }
        String id = data.get(mobIdKey, PersistentDataType.STRING);
        if (id == null) { authoredSnapshots.remove(entity.getUniqueId()); return null; }
        MobDefinition current = definitions.get(id);
        // Persist the entire authored behavior/reward snapshot. Publishing a balance edit cannot heal,
        // rename, alter attacks or change the rewards of creatures already fighting players.
        if (data.has(engineVersionKey) || (current != null && current.authored)) {
            if (!data.has(engineVersionKey, PersistentDataType.INTEGER)
                || !Integer.valueOf(1).equals(data.get(engineVersionKey, PersistentDataType.INTEGER))) {
                authoredSnapshots.remove(entity.getUniqueId()); return null;
            }
            MobDefinition cached = authoredSnapshots.find(entity.getUniqueId(), id, entity.getType().name()).orElse(null);
            if (cached != null) return cached;
            try {
                String encodedName = data.get(engineNameKey, PersistentDataType.STRING);
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(encodedName));
                MobBlueprint snapshot = new MobBlueprint(id, "brawler", name, entity.getType().name().toLowerCase(Locale.ROOT),
                    MobBlueprint.Rank.valueOf(data.get(engineRankKey, PersistentDataType.STRING)),
                    data.get(zoneLevelKey, PersistentDataType.INTEGER), data.get(baseHealthKey, PersistentDataType.DOUBLE),
                    data.get(baseDamageKey, PersistentDataType.DOUBLE), data.get(engineSpeedKey, PersistentDataType.DOUBLE),
                    MobBlueprint.Ability.valueOf(data.get(engineAbilityKey, PersistentDataType.STRING)), data.get(engineRadiusKey, PersistentDataType.DOUBLE),
                    data.get(enginePowerKey, PersistentDataType.DOUBLE), data.get(engineCooldownKey, PersistentDataType.INTEGER),
                    data.get(engineExperienceKey, PersistentDataType.INTEGER), data.get(engineLootKey, PersistentDataType.STRING));
                MobDefinition validated = authoredDefinition(snapshot);
                if (entity.isValid() && !entity.isDead()) authoredSnapshots.put(entity.getUniqueId(), id, entity.getType().name(), validated);
                return validated;
            } catch (IllegalArgumentException | NullPointerException invalidSnapshot) { authoredSnapshots.remove(entity.getUniqueId()); return null; }
        }
        return current;
    }

    private MobScaling.Scale distanceScale(Location location, MobTier tier, int groupSize) {
        Location spawn = location.getWorld().getSpawnLocation();
        double distance = Math.hypot(location.getX() - spawn.getX(), location.getZ() - spawn.getZ());
        if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerd && nerd.skyblock() != null
            && nerd.skyblock().isIslandWorld(location.getWorld())) distance = 0;
        return MobScaling.calculate(distance, groupSize, tier, scaling);
    }

    private MobScaling.Scale entityScale(LivingEntity entity,MobTier tier,int groupSize) {
        Integer fixed=entity.getPersistentDataContainer().get(zoneLevelKey,PersistentDataType.INTEGER);
        return fixed==null ? distanceScale(entity.getLocation(),tier,groupSize) : MobScaling.atLevel(fixed,groupSize,tier,scaling);
    }
    private boolean zoneCombatAllowed(Entity source,Entity target) {
        return !(plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerd) || nerd.zoneSpawner()==null || nerd.zoneSpawner().canFight(source,target);
    }

    private void engage(LivingEntity mob, Player player) {
        if (!mob.isValid() || mob.isDead() || player.getGameMode().isInvulnerable()) {
            return;
        }
        long now = System.currentTimeMillis();
        combatants.computeIfAbsent(mob.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .put(player.getUniqueId(), now);
        refreshCombatScale(mob, now);
    }

    private LivingEntity attackingMob(EntityDamageByEntityEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof LivingEntity living && !(living instanceof Player)) {
            return living;
        }
        Entity direct = event.getDamageSource().getDirectEntity();
        if (direct instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity living && !(living instanceof Player)) {
                return living;
            }
        }
        return event.getDamager() instanceof LivingEntity living && !(living instanceof Player) ? living : null;
    }

    private Player attackingPlayer(EntityDamageByEntityEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) {
            return player;
        }
        Entity direct = event.getDamageSource().getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return event.getDamager() instanceof Player player ? player : null;
    }

    private boolean scalable(LivingEntity entity) {
        return entity instanceof Enemy || isVanillaBoss(entity.getType());
    }

    private boolean isVanillaBoss(EntityType type) {
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER
            || type == EntityType.ELDER_GUARDIAN || type == EntityType.WARDEN;
    }

    private MobTier tier(LivingEntity entity) {
        String stored = entity.getPersistentDataContainer().get(mobTierKey, PersistentDataType.STRING);
        if (stored == null) {
            return isVanillaBoss(entity.getType()) ? MobTier.BOSS : MobTier.STANDARD;
        }
        try {
            return MobTier.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return MobTier.STANDARD;
        }
    }

    private String humanName(EntityType type) {
        String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private double boundedRate(String path, double fallback) {
        return Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble(path, fallback)));
    }

    private Map<String, MobDefinition> load(JavaPlugin plugin) {
        File target = new File(plugin.getDataFolder(), "content/mobs.yml");
        if (!target.exists()) {
            plugin.saveResource("content/mobs.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        ConfigurationSection mobs = yaml.getConfigurationSection("mobs");
        if (mobs == null) {
            throw new IllegalStateException("content/mobs.yml is missing the mobs section");
        }
        Map<String, MobDefinition> loaded = new HashMap<>();
        for (String id : mobs.getKeys(false)) {
            ConfigurationSection section = mobs.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(section.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid entity type for custom mob " + id, exception);
            }
            loaded.put(id, new MobDefinition(
                id,
                section.getString("name", id),
                type,
                section.getBoolean("boss", false),
                section.getDouble("health", 20.0D),
                section.getDouble("attack-damage", 3.0D),
                section.getDouble("ability-radius", 4.0D),
                section.getDouble("ability-damage", 4.0D),
                section.getLong("ability-cooldown-seconds", 12L),
                section.getLong("mark-reward", 1L),
                section.getString("relic-drop"),
                section.getDouble("relic-chance", 0.0D),
                false, MobBlueprint.Rank.ELITE, 1, 0.0, MobBlueprint.Ability.PULSE, -1, ""
            ));
        }
        return Map.copyOf(loaded);
    }

    private record MobDefinition(
        String id,
        String name,
        EntityType type,
        boolean boss,
        double health,
        double attackDamage,
        double abilityRadius,
        double abilityDamage,
        long abilityCooldownSeconds,
        long markReward,
        String relicDrop,
        double relicChance,
        boolean authored,
        MobBlueprint.Rank rank,
        int defaultLevel,
        double movementSpeed,
        MobBlueprint.Ability ability,
        int experienceReward,
        String lootProfile
    ) {
    }

}
