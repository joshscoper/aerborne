package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.paper.grave.GraveManager;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.PitcherCrop;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Set;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

public final class ClaimProtectionListener implements Listener {
    private static final Set<Material> REDSTONE = Set.of(
        Material.REPEATER,
        Material.COMPARATOR,
        Material.LEVER,
        Material.REDSTONE_WIRE,
        Material.DAYLIGHT_DETECTOR,
        Material.OBSERVER,
        Material.TARGET
    );

    private final ClaimService claims;
    private final SessionRegistry sessions;
    private final GraveManager graves;
    private final java.util.function.Predicate<BlockBreakEvent> managedHarvest;
    private final java.util.function.Predicate<EntityDamageEvent> managedCombat;

    public ClaimProtectionListener(ClaimService claims, SessionRegistry sessions, GraveManager graves) {
        this(claims, sessions, graves, event -> false);
    }
    public ClaimProtectionListener(ClaimService claims, SessionRegistry sessions, GraveManager graves,
                                   java.util.function.Predicate<BlockBreakEvent> managedHarvest) {
        this(claims,sessions,graves,managedHarvest,event->false);
    }
    public ClaimProtectionListener(ClaimService claims, SessionRegistry sessions, GraveManager graves,
                                   java.util.function.Predicate<BlockBreakEvent> managedHarvest,
                                   java.util.function.Predicate<EntityDamageEvent> managedCombat) {
        this.claims = claims;
        this.sessions = sessions;
        this.graves = graves;
        this.managedHarvest = managedHarvest;
        this.managedCombat = managedCombat;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (managedHarvest.test(event)) return;
        if ((!allows(event.getPlayer(), event.getBlock(), ClaimRole.BUILD)
            || (container(event.getBlock()) && !allows(event.getPlayer(), event.getBlock(), ClaimRole.CONTAINER))
            || (redstone(event.getBlock().getType()) && !allows(event.getPlayer(), event.getBlock(), ClaimRole.REDSTONE)))
            && !publicHarvest(event.getBlock())) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        boolean allowed = canPlace(event.getPlayer(), event.getBlock());
        if (event instanceof BlockMultiPlaceEvent multi) {
            boolean publicPitcherReplant = publicPitcherReplant(multi);
            allowed &= multi.getReplacedBlockStates().stream()
                .allMatch(state -> canPlace(event.getPlayer(), state.getBlock())
                    || publicPitcherReplant && state.getBlock().getType() == Material.AIR);
        }
        if (!allowed) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!allows(event.getPlayer(), event.getBlock(), ClaimRole.BUILD)) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!allows(event.getPlayer(), event.getBlock(), ClaimRole.BUILD)) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        AccountId accountId = sessions.account(event.getPlayer().getUniqueId()).orElse(null);
        if (accountId != null && graves.isGraveOwner(block, accountId)) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL && block.getType() == Material.FARMLAND) {
            event.setCancelled(!allows(event.getPlayer(), block, ClaimRole.BUILD));
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && ClaimProtectionRules.publicStation(block.getType())
            && (claims.isReserved(key(block)) || flag(block, ClaimFlag.PUBLIC_INTERACT))) {
            return;
        }
        ClaimRole required = container(block)
            ? ClaimRole.CONTAINER
            : (redstone(block.getType()) ? ClaimRole.REDSTONE : ClaimRole.BUILD);
        if ((required == ClaimRole.CONTAINER && flag(block, ClaimFlag.PUBLIC_CONTAINERS))
            || publicInteractionAllowed(block) || publicHarvest(block) || publicPlantingInteraction(event)) {
            return;
        }
        if (!allows(event.getPlayer(), block, required)) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getPersistentDataContainer().has(new org.bukkit.NamespacedKey("nerdsmp", "npc_instance"),
            org.bukkit.persistence.PersistentDataType.STRING)) {
            return;
        }
        Block block = entity.getLocation().getBlock();
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (entity instanceof Animals animal && flag(block, ClaimFlag.PUBLIC_ANIMALS)
            && (animal.isBreedItem(held) || held.getType() == Material.BUCKET
                || held.getType() == Material.SHEARS || held.getType() == Material.BOWL)) {
            return;
        }
        if (entity instanceof AbstractVillager && flag(block, ClaimFlag.PUBLIC_INTERACT)) {
            return;
        }
        if (!allows(event.getPlayer(), block, ClaimRole.CONTAINER)) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        onEntityInteract(event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!allows(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
            denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!allows(event.getPlayer(), event.getHarvestedBlock(), ClaimRole.BUILD)
            && !publicHarvest(event.getHarvestedBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean canPlace(Player player, Block block) {
        return allows(player, block, ClaimRole.BUILD)
            || (flag(block, ClaimFlag.PUBLIC_HARVEST) && ClaimProtectionRules.seedCrop(block.getType()));
    }

    private boolean publicPlantingInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null) {
            return false;
        }
        boolean seed = switch (event.getItem().getType()) {
            case WHEAT_SEEDS, CARROT, POTATO, BEETROOT_SEEDS, NETHER_WART, COCOA_BEANS,
                SWEET_BERRIES, GLOW_BERRIES, TORCHFLOWER_SEEDS, PITCHER_POD -> true;
            default -> false;
        };
        return seed && flag(event.getClickedBlock().getRelative(event.getBlockFace()), ClaimFlag.PUBLIC_HARVEST);
    }

    private static boolean container(Block block) {
        return block.getState() instanceof Container || switch (block.getType()) {
            case CHISELED_BOOKSHELF, JUKEBOX, LECTERN, DECORATED_POT, CAMPFIRE,
                SOUL_CAMPFIRE, BEEHIVE, BEE_NEST -> true;
            default -> false;
        };
    }

    private static boolean redstone(Material material) {
        return REDSTONE.contains(material) || material.name().endsWith("_BUTTON")
            || material.name().endsWith("_PRESSURE_PLATE") || material == Material.TRIPWIRE_HOOK;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isClaimed(block) && !flag(block, ClaimFlag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isClaimed(block) && !flag(block, ClaimFlag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(!allows(player, event.getBlock(), ClaimRole.BUILD));
            return;
        }
        if (event.getEntity() instanceof FallingBlock falling) {
            Location origin = falling.getOrigin();
            if (origin != null) {
                event.setCancelled(!sameTerritory(origin.getBlock(), event.getBlock()));
                return;
            }
        }
        if (isClaimed(event.getBlock()) && !flag(event.getBlock(), ClaimFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isClaimed(event.getBlock()) && !flag(event.getBlock(), ClaimFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!isClaimed(event.getBlock())) {
            return;
        }
        if (event.getPlayer() != null) {
            if (!allows(event.getPlayer(), event.getBlock(), ClaimRole.BUILD)) {
                event.setCancelled(true);
                denied(event.getPlayer());
            }
        } else if (!flag(event.getBlock(), ClaimFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    private boolean allows(Player player, Block block, ClaimRole required) {
        return allowsAt(player, key(block), required);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Block block = event.getEntity().getLocation().getBlock();
        if (!isClaimed(block)) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            Player player = responsiblePlayer(byEntity.getRemover());
            if (player != null) {
                event.setCancelled(!allows(player, block, ClaimRole.CONTAINER));
                return;
            }
        }
        event.setCancelled(event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION
            || !flag(block, ClaimFlag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHostileSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)
            || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
            || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
            return;
        }
        Block block = event.getLocation().getBlock();
        if (isClaimed(block) && !flag(block, ClaimFlag.HOSTILE_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobInteract(EntityInteractEvent event) {
        if (isClaimed(event.getBlock()) && !flag(event.getBlock(), ClaimFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Block block = event.getEntity().getLocation().getBlock();
        if (!allows(event.getPlayer(), block, ClaimRole.CONTAINER)
            && !(event.getEntity() instanceof Sheep && flag(block, ClaimFlag.PUBLIC_ANIMALS))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCaptureAnimal(PlayerBucketEntityEvent event) {
        if (!allows(event.getPlayer(), event.getEntity().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTakeBook(PlayerTakeLecternBookEvent event) {
        Block block = event.getLectern().getBlock();
        if (!allows(event.getPlayer(), block, ClaimRole.CONTAINER) && !flag(block, ClaimFlag.PUBLIC_CONTAINERS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (!allows(event.getPlayer(), event.getEntity().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        if (!allows(event.getPlayer(), event.getEntity().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player
            && !allows(player, event.getEntity().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player
            && !allows(player, event.getMount().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player
            && !allows(player, event.getVehicle().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Block destination = event.getEntity().getLocation().getBlock();
        if (event.getPlayer() != null ? !allows(event.getPlayer(), destination, ClaimRole.BUILD)
            : !sameTerritory(event.getBlock(), destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null) {
            event.setCancelled(isClaimed(event.getEntity().getLocation().getBlock()));
        } else if (!allows(event.getPlayer(), event.getBlock(), ClaimRole.BUILD)
            || !allows(event.getPlayer(), event.getEntity().getLocation().getBlock(), ClaimRole.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFishingHook(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() != null
            && isClaimed(event.getCaught().getLocation().getBlock())
            && !allows(event.getPlayer(), event.getCaught().getLocation().getBlock(), ClaimRole.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent event) {
        Player player = responsiblePlayer(event.getPrimingEntity());
        if ((player != null && !allows(player, event.getBlock(), ClaimRole.BUILD))
            || (isClaimed(event.getBlock()) && !flag(event.getBlock(), ClaimFlag.EXPLOSIONS))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        Player player = responsiblePlayer(event.getEntity());
        if (event.getBlocks().stream().anyMatch(state -> player == null ? isClaimed(state.getBlock())
            : !allows(player, state.getBlock(), ClaimRole.BUILD))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) && managedCombat.test(event)) return;
        Entity target = event.getEntity();
        Block block = target.getLocation().getBlock();
        Entity cause = event.getDamageSource().getCausingEntity();
        Player attacker = responsiblePlayer(cause);
        if (target instanceof Player victim) {
            if (attacker != null && !attacker.equals(victim)
                && (pvpDenied(block) || pvpDenied(attacker.getLocation().getBlock()))) {
                event.setCancelled(true);
            }
            return;
        }
        if (!protectedEntity(target) || !isClaimed(block)) {
            return;
        }
        if (attacker != null) {
            event.setCancelled(!allows(attacker, block, ClaimRole.CONTAINER));
            return;
        }
        if (claims.isReserved(key(block))) {
            event.setCancelled(true);
            return;
        }
        switch (event.getCause()) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> event.setCancelled(!flag(block, ClaimFlag.EXPLOSIONS));
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> event.setCancelled(!flag(block, ClaimFlag.FIRE_SPREAD));
            default -> {
                if (cause != null && !flag(block, ClaimFlag.MOB_GRIEFING)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (entityAttackDenied(event.getAttacker(), event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (entityAttackDenied(event.getAttacker(), event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Block block = event.getHitBlock();
        if (block == null || !redstone(block.getType())) {
            return;
        }
        Player player = responsiblePlayer(event.getEntity());
        if (player != null ? !allows(player, block, ClaimRole.REDSTONE)
            : isClaimed(block) && !(event.getEntity().getShooter() instanceof BlockProjectileSource source
                && sameTerritory(source.getBlock(), block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (potionDenied(event.getEntity(), entity)) {
                event.setIntensity(entity, 0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        event.getAffectedEntities().removeIf(entity -> potionDenied(event.getEntity(), entity));
    }

    private static boolean protectedEntity(Entity entity) {
        return entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Vehicle
            || entity instanceof Animals || entity instanceof AbstractVillager || entity instanceof WaterMob
            || entity instanceof Allay || entity instanceof Golem || entity instanceof Tameable;
    }

    private boolean entityAttackDenied(Entity source, Entity target) {
        Block block = target.getLocation().getBlock();
        if (!isClaimed(block)) {
            return false;
        }
        Player player = responsiblePlayer(source);
        return player == null ? !flag(block, ClaimFlag.MOB_GRIEFING) : !allows(player, block, ClaimRole.CONTAINER);
    }

    private boolean potionDenied(Entity source, LivingEntity target) {
        Player player = responsiblePlayer(source);
        if (target instanceof Player victim) {
            return player != null && !player.equals(victim)
                && (pvpDenied(target.getLocation().getBlock()) || pvpDenied(player.getLocation().getBlock()));
        }
        return protectedEntity(target) && entityAttackDenied(source, target);
    }

    private boolean pvpDenied(Block block) {
        return isClaimed(block) && !flag(block, ClaimFlag.PVP);
    }

    private static Player responsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!sameTerritory(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!pistonAllowed(event.getBlock(), event.getBlocks(), event.getDirection(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace facing = event.getBlock().getBlockData() instanceof Directional directional
            ? directional.getFacing() : event.getDirection().getOppositeFace();
        if (!pistonAllowed(event.getBlock(), event.getBlocks(), facing.getOppositeFace(), facing)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getBlock().getBlockData() instanceof Directional directional
            && !sameTerritory(event.getBlock(), event.getBlock().getRelative(directional.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!sameTerritory(event.getSource(), event.getBlock())
            || (event.getNewState().getType() == Material.FIRE && isClaimed(event.getBlock())
                && !flag(event.getBlock(), ClaimFlag.FIRE_SPREAD))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getPlayer() != null && !allows(event.getPlayer(), event.getBlock(), ClaimRole.BUILD)
            && !(flag(event.getBlock(), ClaimFlag.PUBLIC_HARVEST)
                && ClaimProtectionRules.seedCrop(event.getBlock().getType()))) {
            event.setCancelled(true);
            return;
        }
        event.getBlocks().removeIf(state -> !sameTerritory(event.getBlock(), state.getBlock()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent event) {
        Block origin = event.getLocation().getBlock();
        if (event.getPlayer() != null && !allows(event.getPlayer(), origin, ClaimRole.BUILD)) {
            event.setCancelled(true);
            return;
        }
        event.getBlocks().removeIf(state -> !sameTerritory(origin, state.getBlock()));
    }

    private boolean pistonAllowed(Block piston, List<Block> moved, BlockFace movement, BlockFace facing) {
        if (!sameTerritory(piston, piston.getRelative(facing))) {
            return false;
        }
        for (Block source : moved) {
            if (!sameTerritory(piston, source) || !sameTerritory(piston, source.getRelative(movement))) {
                return false;
            }
        }
        return true;
    }

    private boolean allowsAt(Player player, ChunkKey chunk, ClaimRole required) {
        if (player.hasPermission("nerdsmp.claim.bypass")) {
            return true;
        }
        if (claims.isReserved(chunk)) {
            return false;
        }
        AccountId accountId = sessions.account(player.getUniqueId()).orElse(null);
        if (accountId == null) {
            return false;
        }
        ClaimRole role = claims.roleAt(accountId, chunk).toCompletableFuture().getNow(ClaimRole.NONE);
        return role.allows(required);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // Mobile hoppers can enter a home and leave with its contents without ever
        // transferring across a chunk boundary. Residential land rejects this route.
        if (privateMobileHopper(event.getSource()) || privateMobileHopper(event.getDestination())) {
            event.setCancelled(true);
            return;
        }
        for (Location source : inventoryLocations(event.getSource())) {
            for (Location destination : inventoryLocations(event.getDestination())) {
                if (!sameTerritory(source.getBlock(), destination.getBlock())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (privateMobileHopper(event.getInventory())) { event.setCancelled(true); return; }
        for (Location location : inventoryLocations(event.getInventory())) {
            if (!sameTerritory(event.getItem().getLocation().getBlock(), location.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !canAccessInventory(player, event.getInventory())) {
            event.setCancelled(true);
            denied(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Trust and stewardship can change while the chest is still open. Check the entire view,
        // including shift-clicks, hotbar swaps and collect-to-cursor clicks from the player inventory.
        if (event.getWhoClicked() instanceof Player player
            && !canAccessInventory(player, event.getView().getTopInventory())) {
            event.setCancelled(true);
            denied(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && !canAccessInventory(player, event.getView().getTopInventory())) {
            event.setCancelled(true);
            denied(player);
        }
    }

    private boolean canAccessInventory(Player player, Inventory inventory) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        for (Location location : inventoryLocations(inventory)) {
            Block block = location.getBlock();
            if (account != null && graves != null && graves.isGraveOwner(block, account)) {
                continue;
            }
            if (!allows(player, block, ClaimRole.CONTAINER) && !flag(block, ClaimFlag.PUBLIC_CONTAINERS)) {
                return false;
            }
        }
        return true;
    }

    private static List<Location> inventoryLocations(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest chest) {
            List<Location> locations = new ArrayList<>(2);
            if (chest.getLeftSide() instanceof BlockState state) {
                locations.add(state.getLocation());
            }
            if (chest.getRightSide() instanceof BlockState state) {
                locations.add(state.getLocation());
            }
            return locations;
        }
        // Player inventories and menu holders are not world storage.
        if (!(holder instanceof BlockState) && !(holder instanceof Entity entity && !(entity instanceof Player))) {
            return List.of();
        }
        Location location = inventory.getLocation();
        return location == null ? List.of() : List.of(location);
    }

    private boolean privateMobileHopper(Inventory inventory) {
        if (!(inventory.getHolder() instanceof org.bukkit.entity.minecart.HopperMinecart)) return false;
        return inventoryLocations(inventory).stream().anyMatch(location -> claim(location.getBlock())
            .map(plot -> plot.type().privateAccess()).orElse(false));
    }

    private boolean isClaimed(Block block) {
        return claims.isReserved(key(block)) || claim(block).isPresent();
    }

    private boolean flag(Block block, ClaimFlag flag) {
        return !claims.isReserved(key(block)) && claims.flagAt(key(block), flag).toCompletableFuture().getNow(false);
    }

    private Optional<ClaimService.ClaimView> claim(Block block) {
        return claims.find(key(block)).toCompletableFuture().getNow(Optional.empty());
    }

    private boolean sameTerritory(Block source, Block destination) {
        return ClaimProtectionRules.sameTerritory(claim(source), claims.isReserved(key(source)),
            claim(destination), claims.isReserved(key(destination)));
    }

    private boolean publicHarvest(Block block) {
        if (!flag(block, ClaimFlag.PUBLIC_HARVEST)
            || !ClaimProtectionRules.harvestCrop(block.getType(), block.getBlockData())) return false;
        if (block.getType() != Material.PITCHER_CROP) return true;
        if (!(block.getBlockData() instanceof PitcherCrop pitcher)) return false;
        Block partner = block.getRelative(pitcher.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
        return partner.getType() == Material.PITCHER_CROP
            && partner.getBlockData() instanceof PitcherCrop other
            && other.getHalf() != pitcher.getHalf()
            && ClaimProtectionRules.harvestCrop(partner.getType(), other)
            && flag(partner, ClaimFlag.PUBLIC_HARVEST);
    }

    /** Removing a ripe pitcher's top is part of replanting, never permission to clear arbitrary blocks. */
    private boolean publicPitcherReplant(BlockMultiPlaceEvent event) {
        ItemStack seed = event.getItemInHand();
        List<BlockState> replaced = event.getReplacedBlockStates();
        if (event.getHand() != EquipmentSlot.HAND || seed == null || seed.getType() != Material.PITCHER_POD
            || seed.getAmount() < 1 || seed.hasItemMeta() || replaced.size() != 2) return false;
        BlockState lower = null, upper = null;
        for (BlockState previous : replaced) {
            if (previous.getType() != Material.PITCHER_CROP
                || !(previous.getBlockData() instanceof PitcherCrop crop)
                || !ClaimProtectionRules.harvestCrop(previous.getType(), crop)) return false;
            if (crop.getHalf() == Bisected.Half.BOTTOM) {
                if (lower != null) return false;
                lower = previous;
            } else {
                if (upper != null) return false;
                upper = previous;
            }
        }
        if (lower == null || upper == null || !lower.getWorld().getUID().equals(upper.getWorld().getUID())
            || lower.getX() != upper.getX() || lower.getZ() != upper.getZ() || lower.getY() + 1 != upper.getY()) return false;
        Block base = lower.getBlock(), top = upper.getBlock();
        return base.getType() == Material.PITCHER_CROP
            && base.getBlockData() instanceof PitcherCrop planted
            && planted.getHalf() == Bisected.Half.BOTTOM && planted.getAge() == 0
            && top.getType() == Material.AIR
            && flag(base, ClaimFlag.PUBLIC_HARVEST) && flag(top, ClaimFlag.PUBLIC_HARVEST);
    }

    private boolean publicInteractionAllowed(Block block) {
        if (!claims.isReserved(key(block)) && !flag(block, ClaimFlag.PUBLIC_INTERACT)) {
            return false;
        }
        String material = block.getType().name();
        return material.endsWith("_DOOR") || material.endsWith("_TRAPDOOR")
            || material.endsWith("_FENCE_GATE") || material.endsWith("_BUTTON")
            || material.endsWith("_PRESSURE_PLATE") || block.getType() == Material.BELL;
    }

    private ChunkKey key(Block block) {
        return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }

    private void denied(Player player) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "This territory has not granted you that permission.", net.kyori.adventure.text.format.NamedTextColor.RED));
    }
}
