package net.nerdsmp.paper.zone;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Main-thread, opt-in encounter spawning. Never loads chunks or replaces natural mobs. */
public final class ZoneSpawner implements Listener, AutoCloseable {
    public static final String ZONE_ID="zone_id", EPHEMERAL="zone_spawn";
    private static final long IDLE_MILLIS=30_000, EFFECT_MILLIS=30_000;
    private static final int EFFECT_CAP=512;
    private record Owned(LivingEntity entity,String zoneId,UUID worldId) { }
    private record Effect(Entity entity,String zoneId,long expiresAt) { }
    private final NerdSmpPlugin plugin;
    private final ZoneStore store;
    private final NamespacedKey zoneKey,ephemeralKey;
    private final ZoneSpawnPolicy policy=new ZoneSpawnPolicy();
    private final Map<UUID,Owned> owned=new HashMap<>();
    private final Map<UUID,Effect> effects=new HashMap<>();
    private final Map<String,ZoneDefinition> previous=new HashMap<>();
    private final Map<String,Long> lastActive=new HashMap<>(),lastWarning=new HashMap<>();
    private final ArrayDeque<Chunk> startupCleanup=new ArrayDeque<>();
    private BukkitTask task;
    private boolean closed;

    public ZoneSpawner(NerdSmpPlugin plugin,ZoneStore store) {
        this.plugin=plugin; this.store=store;
        zoneKey=new NamespacedKey(plugin,ZONE_ID); ephemeralKey=new NamespacedKey(plugin,EPHEMERAL);
    }

    /** Call after listener registration, so the first mob is protected before its spawn event. */
    public void start() {
        mainThread();
        if(closed) throw new IllegalStateException("The zone spawner is closed");
        if(task!=null) return;
        for(World world:Bukkit.getWorlds()) Collections.addAll(startupCleanup,world.getLoadedChunks());
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1L,20L);
    }
    public int alive() { return policy.alive(); }
    public int alive(String id) { return policy.alive(id); }

    /** A zone name alone never grants managed-mob privileges. Both ownership markers are needed. */
    public Optional<String> zoneId(Entity entity) {
        if(entity==null) return Optional.empty();
        Optional<String> direct=marker(entity);
        if(direct.isPresent()) return direct;
        Entity actor=actor(entity);
        return actor==entity?Optional.empty():marker(actor);
    }
    private Optional<String> marker(Entity entity) {
        if(entity.getPersistentDataContainer().getOrDefault(ephemeralKey,PersistentDataType.BYTE,(byte)0)!=1)
            return Optional.empty();
        return Optional.ofNullable(entity.getPersistentDataContainer().get(zoneKey,PersistentDataType.STRING))
            .filter(id->!id.isBlank());
    }

    /** Consult before directly applying ability effects, not just for ordinary attack events. */
    public boolean canFight(Entity source,Entity target) {
        if(source==null || target==null) return false;
        Optional<String> sourceZone=zoneId(source),targetZone=zoneId(target);
        if(sourceZone.isEmpty() && targetZone.isEmpty()) return true;
        if(sourceZone.isPresent() && targetZone.isPresent() && !sourceZone.get().equals(targetZone.get())) return false;
        String id=sourceZone.orElseGet(targetZone::orElseThrow);
        ZoneDefinition zone=store.find(id).orElse(null);
        if(zone==null || !zone.enabled() || zone.safe()) return false;
        Entity actor=actor(source);
        return inside(zone,source) && inside(zone,actor) && inside(zone,target)
            && (!(actor instanceof Player p) || adventuring(p))
            && (!(target instanceof Player p) || adventuring(p));
    }
    private static Entity actor(Entity entity) {
        if(entity instanceof Projectile p && p.getShooter() instanceof Entity source) return source;
        if(entity instanceof AreaEffectCloud c && c.getSource() instanceof Entity source) return source;
        return entity;
    }

    private void tick() {
        if(closed) return;
        long now=now();
        for(int i=0;i<4&&!startupCleanup.isEmpty();i++) cleanup(startupCleanup.removeFirst());
        List<ZoneDefinition> definitions=store.all();
        Map<String,List<Player>> active=new HashMap<>();
        Set<String> ids=new HashSet<>();
        for(ZoneDefinition zone:definitions) {
            ids.add(zone.id());
            ZoneDefinition old=previous.put(zone.id(),zone);
            if(old!=null&&!old.equals(zone)) { despawnZone(zone.id(),now); policy.forget(zone.id()); }
            if(!zone.enabled()||zone.safe()) { despawnZone(zone.id(),now); continue; }
            World world=Bukkit.getWorld(zone.worldId());
            if(world!=null && plugin.skyblock()!=null && plugin.skyblock().isIslandWorld(world)) {
                despawnZone(zone.id(),now); continue;
            }
            List<Player> players=world==null?List.of():world.getPlayers().stream()
                .filter(ZoneSpawner::adventuring)
                .filter(p->plugin.sessions().account(p.getUniqueId()).isPresent())
                .filter(p->inside(zone,p)).toList();
            active.put(zone.id(),players);
            if(!players.isEmpty()) lastActive.put(zone.id(),now);
            else if(now-lastActive.computeIfAbsent(zone.id(),ignored->now)>=IDLE_MILLIS) despawnZone(zone.id(),now);
        }
        for(String removed:new ArrayList<>(previous.keySet())) if(!ids.contains(removed)) despawnZone(removed,now);
        previous.keySet().retainAll(ids); lastActive.keySet().retainAll(ids); lastWarning.keySet().retainAll(ids); policy.retain(ids);
        for(Owned record:new ArrayList<>(owned.values())) {
            LivingEntity entity=record.entity();
            ZoneDefinition zone=store.find(record.zoneId()).orElse(null);
            if(!entity.isValid()||entity.isDead()) release(record,now,false);
            else if(zone==null||!inside(zone,entity)||entity.isLeashed()||entity.isInsideVehicle()
                ||!entity.getPassengers().isEmpty()) release(record,now,true);
            else if(entity instanceof Mob mob&&mob.getTarget()!=null&&!canFight(entity,mob.getTarget())) mob.setTarget(null);
        }
        for(Effect effect:new ArrayList<>(effects.values())) {
            ZoneDefinition zone=store.find(effect.zoneId()).orElse(null);
            if(!effect.entity().isValid()||zone==null||!zone.enabled()||zone.safe()
                ||now>=effect.expiresAt()||!inside(zone,effect.entity())) removeEffect(effect);
        }
        int attempts=0;
        for(int probes=0;probes<ZoneSpawnPolicy.POINT_PROBES_PER_CYCLE
            &&attempts<ZoneSpawnPolicy.ATTEMPTS_PER_CYCLE&&policy.alive()<ZoneSpawnPolicy.GLOBAL_CAP;probes++) {
            ZoneDefinition zone=policy.nextZone(definitions);
            if(zone==null) break;
            List<Player> players=active.getOrDefault(zone.id(),List.of());
            if(players.isEmpty()) continue;
            int pointIndex=policy.nextPoint(zone);
            if(!policy.available(zone,pointIndex,now)) continue;
            var point=zone.points().get(pointIndex);
            World world=Bukkit.getWorld(zone.worldId());
            if(world==null||!world.isChunkLoaded(floor(point.x())>>4,floor(point.z())>>4)) continue;
            double radiusSquared=(double)zone.activationRadius()*zone.activationRadius();
            if(players.stream().noneMatch(p->distanceSquared(p.getLocation(),point)<=radiusSquared)) continue;
            attempts++;
            spawn(zone,pointIndex,world,now);
        }
    }

    private void spawn(ZoneDefinition zone,int pointIndex,World world,long now) {
        var point=zone.points().get(pointIndex);
        // Caller checked isChunkLoaded in this same main-thread turn; this never requests generation.
        Chunk chunk=world.getChunkAt(floor(point.x())>>4,floor(point.z())>>4);
        if(!chunk.isEntitiesLoaded()) { policy.failed(zone,pointIndex,now); return; }
        cleanup(chunk);
        var entry=ZoneSpawnPolicy.choose(zone.mobs(),ThreadLocalRandom.current());
        try {
            LivingEntity entity=plugin.mobs().spawnZone(entry.mobId(),
                new Location(world,point.x(),point.y(),point.z(),point.yaw(),0),entry.level(),candidate->{
                    if(!safePoint(zone,candidate)) {
                        plugin.mobs().forget(candidate); throw new UnsafeSpawnPointException();
                    }
                    stamp(candidate,zone.id());
                    candidate.setRemoveWhenFarAway(false); candidate.setCanPickupItems(false);
                });
            if(entity==null||!entity.isValid()||entity.isDead()) {
                if(entity!=null) plugin.mobs().forget(entity);
                policy.failed(zone,pointIndex,now); return;
            }
            // Spawn listeners may change size or position. Validate the final entity as well.
            if(!safePoint(zone,entity)||!policy.spawned(zone,pointIndex,entity.getUniqueId(),now)) {
                plugin.mobs().forget(entity); entity.remove(); policy.failed(zone,pointIndex,now); return;
            }
            owned.put(entity.getUniqueId(),new Owned(entity,zone.id(),world.getUID()));
        } catch(UnsafeSpawnPointException ignored) {
            policy.failed(zone,pointIndex,now);
        } catch(RuntimeException failure) {
            policy.failed(zone,pointIndex,now);
            if(now-lastWarning.getOrDefault(zone.id(),now-60_000)>=60_000) {
                lastWarning.put(zone.id(),now);
                plugin.getLogger().warning("Zone "+zone.id()+" could not spawn "+entry.mobId()+": "+failure.getMessage());
            }
        }
    }

    private boolean safePoint(ZoneDefinition zone,LivingEntity entity) {
        if(!inside(zone,entity)) return false;
        BoundingBox box=entity.getBoundingBox();
        World world=entity.getWorld();
        if(box.getWidthX()>8||box.getWidthZ()>8||box.getHeight()>12||box.getWidthX()<=0||box.getHeight()<=0
            ||box.getMinY()<world.getMinHeight()+1||box.getMaxY()>=world.getMaxHeight()) return false;
        int minX=floor(box.getMinX()+1E-5),maxX=floor(box.getMaxX()-1E-5);
        int minZ=floor(box.getMinZ()+1E-5),maxZ=floor(box.getMaxZ()-1E-5),feet=floor(box.getMinY()-1E-5);
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            if(!world.isChunkLoaded(x>>4,z>>4)) return false;
            Block below=world.getBlockAt(x,feet,z);
            if(!below.getType().isSolid()||!harmless(below.getType())
                ||Math.abs(below.getBoundingBox().getMaxY()-box.getMinY())>.06) return false;
            for(int y=floor(box.getMinY()+1E-5);y<=floor(box.getMaxY()-1E-5);y++) {
                Block body=world.getBlockAt(x,y,z);
                if(!body.isPassable()||!harmless(body.getType())) return false;
            }
        }
        return world.getNearbyEntities(box,other->other instanceof LivingEntity
            &&!other.getUniqueId().equals(entity.getUniqueId())).isEmpty();
    }

    private boolean inside(ZoneDefinition zone,Entity entity) {
        if(plugin.skyblock()!=null && plugin.skyblock().isIslandWorld(entity.getWorld())) return false;
        if(!entity.getWorld().getUID().equals(zone.worldId())) return false;
        BoundingBox box=entity.getBoundingBox();
        if(!zone.bounds().contains(box.getMinX(),box.getMinY(),box.getMinZ())
            ||!zone.bounds().contains(Math.nextDown(box.getMaxX()),Math.nextDown(box.getMaxY()),Math.nextDown(box.getMaxZ()))) return false;
        Location location=entity.getLocation();
        return store.at(zone.worldId(),location.getX(),location.getY(),location.getZ())
            .map(current->current.id().equals(zone.id())&&current.enabled()&&!current.safe()).orElse(false);
    }

    static boolean harmless(Material material) {
        return switch(material) {
            case WATER,LAVA,FIRE,SOUL_FIRE,CAMPFIRE,SOUL_CAMPFIRE,MAGMA_BLOCK,CACTUS,SWEET_BERRY_BUSH,
                WITHER_ROSE,POWDER_SNOW,POINTED_DRIPSTONE,NETHER_PORTAL,END_PORTAL,END_GATEWAY -> false;
            default -> true;
        };
    }
    private static boolean adventuring(Player p) {
        return p.isOnline()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);
    }
    private void stamp(Entity entity,String id) {
        entity.getPersistentDataContainer().set(zoneKey,PersistentDataType.STRING,id);
        entity.getPersistentDataContainer().set(ephemeralKey,PersistentDataType.BYTE,(byte)1);
        entity.setPersistent(false);
    }
    private void cleanup(Chunk chunk) {
        if(!chunk.isLoaded()||!chunk.isEntitiesLoaded()) return;
        for(Entity entity:chunk.getEntities()) if(marker(entity).isPresent()
            &&!owned.containsKey(entity.getUniqueId())&&!effects.containsKey(entity.getUniqueId())) discardStale(entity);
    }
    private void release(Owned record,long now,boolean removeEntity) {
        owned.remove(record.entity().getUniqueId()); policy.removed(record.entity().getUniqueId(),now);
        plugin.mobs().forget(record.entity());
        if(removeEntity&&marker(record.entity()).isPresent()) record.entity().remove();
    }
    private void discardStale(Entity entity) {
        if(entity instanceof LivingEntity living) plugin.mobs().forget(living);
        entity.remove();
    }
    private void despawnZone(String id,long now) {
        for(Owned record:new ArrayList<>(owned.values())) if(record.zoneId().equals(id)) release(record,now,true);
        for(Effect effect:new ArrayList<>(effects.values())) if(effect.zoneId().equals(id)) removeEffect(effect);
    }
    private void removeEffect(Effect effect) {
        effects.remove(effect.entity().getUniqueId());
        if(marker(effect.entity()).isPresent()) effect.entity().remove();
    }
    private boolean trackEffect(Entity effect,String id) {
        if(effects.containsKey(effect.getUniqueId())) return true;
        if(effects.size()>=EFFECT_CAP) return false;
        stamp(effect,id);
        effects.put(effect.getUniqueId(),new Effect(effect,id,ZoneSpawnPolicy.deadline(now(),EFFECT_MILLIS)));
        return true;
    }

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onEarlyDamage(EntityDamageByEntityEvent e) { guardDamage(e); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent e) { guardDamage(e); }
    private void guardDamage(EntityDamageByEntityEvent e) { if(!canFight(e.getDamager(),e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onEnvironmentalDamage(EntityDamageEvent e) {
        Optional<String> id=zoneId(e.getEntity());
        if(id.isEmpty()) return;
        ZoneDefinition zone=store.find(id.get()).orElse(null);
        if(zone==null||!zone.enabled()||zone.safe()||!inside(zone,e.getEntity())) {
            e.setCancelled(true);
            Owned record=owned.get(e.getEntity().getUniqueId());
            if(record!=null) release(record,now(),true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if(zoneId(e.getEntity()).isPresent()&&e.getTarget()!=null
            &&(!(e.getTarget() instanceof Player)||!canFight(e.getEntity(),e.getTarget()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        Owned record=owned.get(e.getEntity().getUniqueId()); if(record!=null) release(record,now(),false);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onLaunch(ProjectileLaunchEvent e) {
        Projectile projectile=e.getEntity();
        Optional<String> id=zoneId(projectile); if(id.isEmpty()) return;
        ZoneDefinition zone=store.find(id.get()).orElse(null);
        Entity source=actor(projectile);
        if(zone==null||!zone.enabled()||zone.safe()||!inside(zone,source)
            ||source instanceof Mob mob&&mob.getTarget()!=null&&!canFight(source,mob.getTarget())
            ||!trackEffect(projectile,id.get())) { e.setCancelled(true); return; }
        if(projectile instanceof AbstractArrow arrow) arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile projectile=e.getEntity();
        Optional<String> id=zoneId(projectile); if(id.isEmpty()) return;
        ZoneDefinition zone=store.find(id.get()).orElse(null);
        if(zone==null||!inside(zone,projectile)||e.getHitEntity()!=null&&!canFight(projectile,e.getHitEntity())) {
            e.setCancelled(true);
            Effect effect=effects.get(projectile.getUniqueId());
            if(effect!=null) removeEffect(effect); else if(marker(projectile).isPresent()) projectile.remove();
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onSplash(PotionSplashEvent e) {
        for(LivingEntity target:e.getAffectedEntities()) if(!canFight(e.getPotion(),target)) e.setIntensity(target,0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onLingering(LingeringPotionSplashEvent e) {
        Optional<String> id=zoneId(e.getEntity());
        if(id.isPresent()&&!trackEffect(e.getAreaEffectCloud(),id.get())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onCloud(AreaEffectCloudApplyEvent e) { e.getAffectedEntities().removeIf(target->!canFight(e.getEntity(),target)); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onEffectSpawn(EntitySpawnEvent e) {
        // Creepers carrying status effects can create clouds without throwing a lingering potion.
        if(!(e.getEntity() instanceof AreaEffectCloud cloud)) return;
        Optional<String> id=zoneId(cloud); if(id.isEmpty()) return;
        ZoneDefinition zone=store.find(id.get()).orElse(null);
        if(zone==null||!zone.enabled()||zone.safe()||!inside(zone,cloud)||!trackEffect(cloud,id.get())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onTeleport(EntityTeleportEvent e) {
        Optional<String> id=zoneId(e.getEntity()); if(id.isEmpty()) return;
        Location to=e.getTo();
        ZoneDefinition zone=store.find(id.get()).orElse(null);
        if(to==null||zone==null||to.getWorld()==null||!zone.worldId().equals(to.getWorld().getUID())
            ||!zone.bounds().contains(to.getX(),to.getY(),to.getZ())
            ||!to.getWorld().isChunkLoaded(to.getBlockX()>>4,to.getBlockZ()>>4)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onBlockChange(EntityChangeBlockEvent e) { if(zoneId(e.getEntity()).isPresent()) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onExplosion(EntityExplodeEvent e) {
        if(zoneId(e.getEntity()).isPresent()) { e.blockList().clear(); e.setYield(0); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        zoneId(e.getEntity()).ifPresent(id->{
            ZoneDefinition zone=store.find(id).orElse(null);
            if(zone==null||!zone.enabled()||zone.safe()||!inside(zone,e.getEntity())) e.setCancelled(true);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onIgnite(BlockIgniteEvent e) { if(zoneId(e.getIgnitingEntity()).isPresent()) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onCombust(EntityCombustByEntityEvent e) { if(!canFight(e.getCombuster(),e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onSplit(SlimeSplitEvent e) { if(zoneId(e.getEntity()).isPresent()) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onTransform(EntityTransformEvent e) { if(zoneId(e.getEntity()).isPresent()) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onBreed(EntityBreedEvent e) {
        if(zoneId(e.getMother()).isPresent()||zoneId(e.getFather()).isPresent()) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) { if(!closed) cleanup(e.getChunk()); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        if(closed) return;
        for(Entity entity:e.getEntities()) if(marker(entity).isPresent()
            &&!owned.containsKey(entity.getUniqueId())&&!effects.containsKey(entity.getUniqueId())) discardStale(entity);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk=e.getChunk();
        for(Owned record:new ArrayList<>(owned.values())) if(inChunk(record.entity(),chunk)) release(record,now(),true);
        for(Effect effect:new ArrayList<>(effects.values())) if(inChunk(effect.entity(),chunk)) removeEffect(effect);
        cleanup(chunk);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onWorldUnload(WorldUnloadEvent e) {
        for(Owned record:new ArrayList<>(owned.values())) if(record.worldId().equals(e.getWorld().getUID())) release(record,now(),true);
        for(Effect effect:new ArrayList<>(effects.values())) if(effect.entity().getWorld().getUID().equals(e.getWorld().getUID())) removeEffect(effect);
    }

    @Override public void close() {
        mainThread(); closed=true;
        if(task!=null) { task.cancel(); task=null; }
        for(Owned record:new ArrayList<>(owned.values())) release(record,now(),true);
        for(Effect effect:new ArrayList<>(effects.values())) removeEffect(effect);
        for(World world:Bukkit.getWorlds()) for(Chunk chunk:world.getLoadedChunks()) cleanup(chunk);
        startupCleanup.clear(); previous.clear(); lastActive.clear(); lastWarning.clear(); policy.clear();
    }
    private static boolean inChunk(Entity entity,Chunk chunk) {
        Location p=entity.getLocation();
        return p.getWorld().getUID().equals(chunk.getWorld().getUID())&&(p.getBlockX()>>4)==chunk.getX()&&(p.getBlockZ()>>4)==chunk.getZ();
    }
    private static void mainThread() {
        if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Zone entities must be managed on the server thread");
    }
    private static long now() { return System.nanoTime()/1_000_000L; }
    private static int floor(double value) { return (int)Math.floor(value); }
    private static double distanceSquared(Location p,ZoneDefinition.SpawnPoint point) {
        double x=p.getX()-point.x(),y=p.getY()-point.y(),z=p.getZ()-point.z(); return x*x+y*y+z*z;
    }
    public static final class UnsafeSpawnPointException extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public UnsafeSpawnPointException() { super("The exact spawn point has no safe, unobstructed mob-sized footing"); }
    }
}
