package net.nerdsmp.paper.expedition;

import net.kyori.adventure.bossbar.BossBar;
import net.nerdsmp.paper.expedition.combat.Telegraph;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Device;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Hazard;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Lane;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Section;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What a boss room does in a fight, beyond the boss. Lanes roll across the floor and break on columns,
 * chandeliers come down and can be baited onto the boss, vigil candles spread a pool until someone
 * lights them again, and a ward holds the boss out of reach until the party douses the room's fires.
 * Brood sacs hatch unless they're burst, floors burn away unless someone reaches their cistern, and
 * gusts throw anyone without cover. Every piece is telegraphed, and everything it changes goes back
 * when the party wipes.
 */
final class RunArena {
    interface Host {
        World world();
        ExpeditionDefinition expedition();
        boolean live();
        List<Player> online();
        boolean inCutscene(Player player);
        /** The bosses standing in the fight. */
        List<LivingEntity> bosses();
        BossBar bar();
        /** Stops a boss's cast, if it has one going. */
        void cancelCast(LivingEntity boss);
        /** Puts a block back after {@code ticks}. */
        void temporary(Block block, Material type, int ticks);
        void broadcast(String markup);
        /** Sends a line to one player's action bar. */
        void bar(Player player, String markup);
        /** Runs one of the active room's scripts, such as {@code ward-broken}. */
        void script(String trigger);
        /** Spawns room mobs at a spot. */
        void hatch(String mob, int count, Location at);
        void log(String line);
    }

    private static final long TICK = 10;
    /** Blocks a lane's front covers each tick. */
    private static final double LANE_SPEED = 1.3, LANE_WIDTH = 3.2;
    private static final int LANE_WARNING_TICKS = 32;
    private static final double COLLAPSE_RADIUS = 3.6, COLLAPSE_BOSS_SHARE = .06;
    private static final int COLLAPSE_WARNING_TICKS = 60, COLLAPSE_STUN_TICKS = 70;
    private static final double POOL_GROWTH = .35, POOL_LIMIT = 7;
    private static final int STAGGER_TICKS = 100;
    private static final double REACH = 5;
    private static final long SAC_MILLIS = 9_000, BURN_MILLIS = 13_000;
    private static final int HATCHLINGS = 2, GUST_WARNING_TICKS = 50, GUST_TICKS = 16;
    private static final double GUST_PUSH = .5, GUST_COVER = 5;

    /** A snuffed vigil device and the pool spreading from it. */
    private static final class Pool {
        final Hazard hazard;
        double radius = 1;

        Pool(Hazard hazard) { this.hazard = hazard; }
    }

    /** A ward: the devices still burning, when it runs out, and where the boss was before it rose. */
    private static final class Ward {
        final Set<String> burning;
        final long until;
        final Map<UUID, Location> held = new HashMap<>();
        final List<BlockDisplay> columns = new ArrayList<>();
        int total;

        Ward(Set<String> burning, long until) {
            this.burning = burning;
            this.until = until;
            total = burning.size();
        }
    }

    private final Plugin plugin;
    private final Host host;
    private final Map<String, Pool> pools = new LinkedHashMap<>();
    private final Set<String> fallen = new HashSet<>();
    /** Sacs swelling, by device, and when each hatches; and those already gone. */
    private final Map<String, Long> swelling = new LinkedHashMap<>();
    private final Map<String, Hazard> broods = new HashMap<>();
    private final Set<String> burst = new HashSet<>();
    /** Floors on fire, and when each falls; and those gone, and cisterns spent. */
    private final Map<Section, Long> burning = new LinkedHashMap<>();
    private final Set<Section> lost = new HashSet<>();
    private final Set<String> spent = new HashSet<>();
    private final Map<Block, BlockData> wreckage = new HashMap<>();
    private final List<Display> falling = new ArrayList<>();
    private BukkitTask task;
    private Ward ward;
    private int beat;
    private int nextBeat;

    RunArena(Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    void begin() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK); }

    void close() {
        if (task != null) task.cancel();
        task = null;
        endWard();
        falling.forEach(Display::remove);
        falling.clear();
    }

    /** A wipe: pools dry up, fires and fallen chandeliers go back, and any ward ends with its boss. */
    void reset() {
        endWard();
        for (String device : List.copyOf(pools.keySet())) light(device, true);
        pools.clear();
        wreckage.forEach((block, data) -> block.setBlockData(data, false));
        wreckage.clear();
        fallen.clear();
        swelling.clear();
        broods.clear();
        burst.clear();
        burning.clear();
        lost.clear();
        spent.clear();
        nextBeat = 0;
    }

    /** The heartbeat under a boss fight, quicker as the boss weakens; the slow part of every effect. */
    private void tick() {
        if (!host.live()) return;
        heartbeat();
        tickPools();
        tickWard();
        tickSacs();
        tickBurns();
    }

    private void heartbeat() {
        List<LivingEntity> bosses = host.bosses();
        if (bosses.isEmpty() || host.bar() == null || ++beat < nextBeat) return;
        beat = 0;
        var health = bosses.getFirst().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double share = health == null ? 1 : Math.clamp(bosses.getFirst().getHealth() / health.getValue(), 0D, 1D);
        nextBeat = share > .6 ? 5 : share > .3 ? 4 : 3;
        for (Player player : host.online()) player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, .55F, share > .3 ? .8F : 1F);
    }

    // ---------------------------------------------------------------------------------------------
    // Strikes
    // ---------------------------------------------------------------------------------------------

    void strike(Hazard hazard) {
        host.log("hazard " + hazard.id());
        switch (hazard.type()) {
            case LANE -> lanes(hazard);
            case COLLAPSE -> collapse(hazard);
            case VIGIL -> snuff(hazard);
            case BROOD -> swell(hazard);
            case BURNDOWN -> ignite(hazard);
            case GUST -> gust(hazard);
            default -> { }
        }
    }

    private void lanes(Hazard hazard) {
        List<Lane> shuffled = new ArrayList<>(hazard.lanes());
        Collections.shuffle(shuffled);
        for (Lane lane : shuffled.subList(0, Math.min(hazard.count(), shuffled.size()))) lane(hazard, lane);
    }

    private void lane(Hazard hazard, Lane lane) {
        World world = host.world();
        double floor = hazard.area().minY();
        Location from = new Location(world, lane.x1() + .5, floor, lane.z1() + .5), to = new Location(world, lane.x2() + .5, floor, lane.z2() + .5);
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        Vector along = path.clone().normalize(), across = new Vector(-along.getZ(), 0, along.getX()).multiply(LANE_WIDTH / 2);
        Color color = hazard.trail() != null ? Telegraph.POISON : hazard.soul() ? Telegraph.FROST : Telegraph.FIRE;
        world.playSound(from, hazard.trail() != null ? Sound.ENTITY_SPIDER_AMBIENT : hazard.soul() ? Sound.ENTITY_EVOKER_PREPARE_ATTACK : Sound.ENTITY_BLAZE_AMBIENT, 1.3F, .6F);
        int[] tick = {0};
        double[] front = {0};
        Set<UUID> struck = new HashSet<>();
        Bukkit.getScheduler().runTaskTimer(plugin, run -> {
            if (!host.live()) { run.cancel(); return; }
            int now = tick[0]++;
            if (now < LANE_WARNING_TICKS) {
                if (now % 5 == 0) for (int edge = -1; edge <= 1; edge++) {
                    Vector side = across.clone().multiply(edge);
                    Telegraph.line(from.clone().add(side), to.clone().add(side), color);
                }
                return;
            }
            front[0] += LANE_SPEED;
            Location head = from.clone().add(along.clone().multiply(front[0]));
            if (front[0] >= length || blocked(head, floor)) {
                run.cancel();
                if (hazard.trail() == null) {
                    world.spawnParticle(hazard.soul() ? Particle.SOUL : Particle.LAVA, surface(head), 18, 1, .4, 1, .03);
                    world.playSound(head, Sound.BLOCK_FIRE_EXTINGUISH, 1F, .7F);
                }
                return;
            }
            sweep(hazard, head, across, struck);
        }, 0L, 1L);
    }

    /** A wall or column stops a lane, so there's cover from it. Low steps and tombs don't. */
    private boolean blocked(Location head, double floor) {
        return host.world().getBlockAt(head.getBlockX(), (int) floor + 2, head.getBlockZ()).getType().isSolid();
    }

    private void sweep(Hazard hazard, Location head, Vector across, Set<UUID> struck) {
        World world = host.world();
        Location top = surface(head);
        Material trail = hazard.trail() == null ? null : Material.matchMaterial(hazard.trail());
        for (double side = -1; side <= 1; side += .5) {
            Location spot = top.clone().add(across.clone().multiply(side));
            if (trail != null) Telegraph.shards(spot.clone().add(0, .4, 0), 4, .3, trail == Material.COBWEB ? Material.WHITE_WOOL : trail);
            else world.spawnParticle(hazard.soul() ? Particle.SOUL_FIRE_FLAME : Particle.FLAME, spot.clone().add(0, .4, 0), 5, .2, .4, .2, .02);
            if (hazard.burnSeconds() > 0 && side % 1 == 0) {
                Block open = surface(spot).getBlock();
                if (open.getType().isAir()) host.temporary(open, trail == null ? Material.FIRE : trail, hazard.burnSeconds() * 20);
            }
        }
        world.playSound(top, trail != null ? Sound.BLOCK_WOOL_STEP : hazard.soul() ? Sound.PARTICLE_SOUL_ESCAPE : Sound.ITEM_FIRECHARGE_USE, .7F, .7F);
        for (Player player : host.online()) {
            if (!exposed(player) || struck.contains(player.getUniqueId())) continue;
            Vector offset = player.getLocation().toVector().subtract(head.toVector()).setY(0);
            if (offset.lengthSquared() > (LANE_WIDTH / 2 + 1) * (LANE_WIDTH / 2 + 1)) continue;
            struck.add(player.getUniqueId());
            player.damage(hazard.damage());
            if (trail != null) player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
            else if (hazard.soul()) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            else player.setFireTicks(60);
        }
    }

    private boolean exposed(Player player) {
        return !player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR && !host.inCutscene(player);
    }

    /** The open block on top of the floor at a spot, so effects follow steps and daises. */
    private Location surface(Location at) {
        World world = host.world();
        for (int y = at.getBlockY(); y < at.getBlockY() + 3; y++)
            if (!world.getBlockAt(at.getBlockX(), y, at.getBlockZ()).getType().isSolid()) return new Location(world, at.getX(), y, at.getZ());
        return at;
    }

    // ---------------------------------------------------------------------------------------------
    // Collapses
    // ---------------------------------------------------------------------------------------------

    /** The next hanging thing comes down: a warning on the floor, then it falls. A boss under it pays. */
    private void collapse(Hazard hazard) {
        Point spot = hazard.spots().stream().filter(point -> !fallen.contains(key(point))).findFirst().orElse(null);
        if (spot == null) return;
        fallen.add(key(spot));
        World world = host.world();
        Location floor = surface(new Location(world, spot.x() + .5, hazard.area().minY(), spot.z() + .5));
        Location hanging = new Location(world, spot.x() + .5, spot.y(), spot.z() + .5);
        world.playSound(hanging, Sound.BLOCK_CHAIN_BREAK, 2F, .5F);
        world.playSound(hanging, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.4F, .5F);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, run -> {
            if (!host.live()) { run.cancel(); return; }
            if (tick[0] < COLLAPSE_WARNING_TICKS) {
                Telegraph.disc(floor, COLLAPSE_RADIUS * (.4 + .6 * tick[0] / COLLAPSE_WARNING_TICKS), Telegraph.DANGER);
                Telegraph.shards(hanging.clone().add(0, 1, 0), 6, .5, Material.IRON_BLOCK);
                if (tick[0] == COLLAPSE_WARNING_TICKS - 15) host.broadcast("<red>The chain gives way!</red>");
                tick[0] += 5;
                return;
            }
            run.cancel();
            drop(hazard, spot, hanging, floor);
        }, 0L, 5L);
    }

    private void drop(Hazard hazard, Point spot, Location hanging, Location floor) {
        World world = host.world();
        // The hub and its arms, and the chain up to the ceiling.
        for (int[] part : new int[][]{{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}}) clear(world.getBlockAt(spot.x() + part[0], spot.y(), spot.z() + part[1]));
        for (int y = spot.y() + 1; world.getBlockAt(spot.x(), y, spot.z()).getType() == Material.IRON_CHAIN; y++) clear(world.getBlockAt(spot.x(), y, spot.z()));
        BlockDisplay mass = world.spawn(hanging, BlockDisplay.class, display -> {
            display.setBlock(Material.POLISHED_DEEPSLATE.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setBrightness(new Display.Brightness(12, 12));
            display.setTransformation(new Transformation(new Vector3f(-1, -.5F, -1), new Quaternionf(), new Vector3f(2, 1, 2), new Quaternionf()));
        });
        falling.add(mass);
        double height = hanging.getY() - floor.getY();
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, run -> {
            int now = ++tick[0];
            double fell = .09 * now * now;
            if (!host.live() || fell >= height) {
                run.cancel();
                mass.remove();
                falling.remove(mass);
                if (host.live()) land(hazard, floor);
                return;
            }
            mass.teleport(hanging.clone().subtract(0, fell, 0));
        }, 1L, 1L);
    }

    private void clear(Block block) {
        wreckage.putIfAbsent(block, block.getBlockData().clone());
        block.setType(Material.AIR, false);
    }

    private void land(Hazard hazard, Location floor) {
        World world = host.world();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, floor, 1);
        world.spawnParticle(Particle.FLAME, floor.clone().add(0, .4, 0), 70, 2, .3, 2, .12);
        Telegraph.shards(floor.clone().add(0, .6, 0), 50, 1.6, Material.DEEPSLATE_BRICKS);
        world.playSound(floor, Sound.ENTITY_GENERIC_EXPLODE, 1.6F, .6F);
        world.playSound(floor, Sound.BLOCK_ANVIL_LAND, 1.4F, .5F);
        for (Player player : host.online())
            if (exposed(player) && player.getLocation().distanceSquared(floor) <= COLLAPSE_RADIUS * COLLAPSE_RADIUS) {
                player.damage(hazard.damage());
                player.setFireTicks(40);
            }
        for (LivingEntity boss : host.bosses()) {
            if (boss.getLocation().distanceSquared(floor) > COLLAPSE_RADIUS * COLLAPSE_RADIUS || boss.isInvulnerable()) continue;
            var health = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (health != null) boss.damage(health.getValue() * COLLAPSE_BOSS_SHARE);
            stagger(boss, COLLAPSE_STUN_TICKS);
            host.broadcast("<gold>It came down on the boss!</gold> <gray>Strike while it's staggered.</gray>");
        }
        // The wreck burns where it fell, and the floor stays dangerous for a while.
        for (double angle = 0; angle < Math.TAU; angle += Math.PI / 5) {
            Location spot = surface(floor.clone().add(Math.cos(angle) * 1.6, 0, Math.sin(angle) * 1.6));
            if (spot.getBlock().getType().isAir()) host.temporary(spot.getBlock(), Material.FIRE, 160);
        }
    }

    private void stagger(LivingEntity boss, int ticks) {
        host.cancelCast(boss);
        if (boss instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.setAware(false);
        }
        boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false));
        boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0, boss.getHeight(), 0), 30, .5, .2, .5, .3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss.isValid() && boss instanceof Mob mob && ward == null) mob.setAware(true);
        }, ticks);
    }

    private static String key(Point point) { return point.x() + "," + point.y() + "," + point.z(); }

    // ---------------------------------------------------------------------------------------------
    // Devices
    // ---------------------------------------------------------------------------------------------

    private Block block(Device device) { return host.world().getBlockAt(device.at().x(), device.at().y(), device.at().z()); }

    private Device device(String id) { return host.expedition().story().devices().get(id); }

    private void light(String id, boolean on) {
        Device device = device(id);
        if (device != null && block(device).getBlockData() instanceof Lightable lightable) {
            lightable.setLit(on);
            block(device).setBlockData(lightable, false);
        }
    }

    private Location middle(Device device) { return block(device).getLocation().add(.5, .6, .5); }

    /** A player right-clicked a block. Returns true when it was a device in use. */
    boolean interact(Player player, Block block) {
        for (Device device : host.expedition().story().devices().values()) {
            if (device.at().x() != block.getX() || device.at().y() != block.getY() || device.at().z() != block.getZ()) continue;
            if (pools.containsKey(device.id())) { relight(player, device); return true; }
            if (ward != null && ward.burning.contains(device.id())) { douse(player, device); return true; }
            if (swelling.containsKey(device.id())) { burst(player, device); return true; }
            for (Section section : burning.keySet())
                if (section.device().equals(device.id())) { quench(player, device, section); return true; }
            return false;
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Vigils
    // ---------------------------------------------------------------------------------------------

    /** A vigil's script stopped it: the candles it snuffed come back, and their pools dry up. Sacs and fires stop where they are. */
    void stopped(Hazard hazard) {
        swelling.keySet().removeIf(id -> broods.get(id) == hazard);
        burning.keySet().removeIf(hazard.sections()::contains);
        for (var entry : List.copyOf(pools.entrySet()))
            if (entry.getValue().hazard == hazard) {
                pools.remove(entry.getKey());
                light(entry.getKey(), true);
            }
    }

    private void snuff(Hazard hazard) {
        List<String> lit = new ArrayList<>(hazard.devices());
        lit.removeAll(pools.keySet());
        Collections.shuffle(lit);
        World world = host.world();
        for (String id : lit.subList(0, Math.min(hazard.count(), lit.size()))) {
            Device device = device(id);
            light(id, false);
            pools.put(id, new Pool(hazard));
            world.spawnParticle(Particle.SOUL, middle(device), 24, .3, .4, .3, .04);
            world.playSound(middle(device), Sound.BLOCK_CANDLE_EXTINGUISH, 1.6F, .5F);
            world.playSound(middle(device), Sound.ENTITY_ALLAY_DEATH, .8F, .5F);
            host.broadcast("<dark_aqua>A candle goes out.</dark_aqua> <gray>Grief spills from it. Right-click it to light it again.</gray>");
        }
    }

    private void relight(Player player, Device device) {
        pools.remove(device.id());
        light(device.id(), true);
        host.world().spawnParticle(Particle.FLAME, middle(device), 18, .2, .3, .2, .02);
        host.world().playSound(middle(device), Sound.ITEM_FLINTANDSTEEL_USE, 1.2F, 1F);
        host.bar(player, "<gold>You light the " + Chat.escape(device.title()) + "</gold>");
    }

    private void tickPools() {
        World world = host.world();
        for (var entry : pools.entrySet()) {
            Pool pool = entry.getValue();
            Location center = surface(middle(device(entry.getKey())).clone().subtract(0, .6, 0));
            pool.radius = Math.min(POOL_LIMIT, pool.radius + POOL_GROWTH);
            Telegraph.ring(center, pool.radius, Telegraph.SHADOW);
            Telegraph.ring(center, pool.radius * .6, Telegraph.SHADOW);
            world.spawnParticle(Particle.SOUL, center.clone().add(0, .2, 0), 6, pool.radius * .5, .1, pool.radius * .5, .01);
            for (Player player : host.online()) {
                if (!exposed(player) || player.getLocation().distanceSquared(center) > pool.radius * pool.radius) continue;
                player.damage(pool.hazard.damage());
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Brood sacs
    // ---------------------------------------------------------------------------------------------

    private void swap(Block block, Material type) {
        wreckage.putIfAbsent(block, block.getBlockData().clone());
        block.setType(type, false);
    }

    /** The next sacs start to swell and glow. They hatch unless they're burst. */
    private void swell(Hazard hazard) {
        List<String> ripe = new ArrayList<>(hazard.devices());
        ripe.removeAll(burst);
        ripe.removeAll(swelling.keySet());
        Collections.shuffle(ripe);
        World world = host.world();
        for (String id : ripe.subList(0, Math.min(hazard.count(), ripe.size()))) {
            swelling.put(id, System.currentTimeMillis() + SAC_MILLIS);
            broods.put(id, hazard);
            swap(block(device(id)), Material.PEARLESCENT_FROGLIGHT);
            world.playSound(middle(device(id)), Sound.BLOCK_SNIFFER_EGG_CRACK, 1.8F, .6F);
            host.broadcast("<green>A brood sac swells!</green> <gray>Right-click it before it hatches.</gray>");
        }
    }

    private void tickSacs() {
        World world = host.world();
        long now = System.currentTimeMillis();
        for (String id : List.copyOf(swelling.keySet())) {
            Device device = device(id);
            Location at = middle(device);
            world.spawnParticle(Particle.ITEM_COBWEB, at.clone().add(0, .6, 0), 8, .4, .4, .4, .02);
            Telegraph.ring(block(device).getLocation().add(.5, 0, .5), 1.4, Telegraph.POISON);
            world.playSound(at, Sound.ENTITY_TURTLE_EGG_CRACK, 1F, .7F);
            if (now < swelling.get(id)) continue;
            swelling.remove(id);
            burst.add(id);
            clear(block(device));
            world.playSound(at, Sound.BLOCK_SNIFFER_EGG_HATCH, 1.8F, .7F);
            Telegraph.shards(at, 30, 1, Material.WHITE_WOOL);
            host.hatch(broods.get(id).mob(), HATCHLINGS, at.clone().add(0, 1, 0));
        }
    }

    private void burst(Player player, Device device) {
        swelling.remove(device.id());
        burst.add(device.id());
        Location at = middle(device);
        clear(block(device));
        host.world().playSound(at, Sound.BLOCK_SLIME_BLOCK_BREAK, 1.6F, .7F);
        Telegraph.shards(at, 24, .8, Material.LIME_WOOL);
        host.bar(player, "<green>You burst the sac</green>");
    }

    // ---------------------------------------------------------------------------------------------
    // Burning floors
    // ---------------------------------------------------------------------------------------------

    /** The next stretch of floor catches. Its cistern can put it out, once. */
    private void ignite(Hazard hazard) {
        List<Section> standing = new ArrayList<>(hazard.sections());
        standing.removeAll(lost);
        standing.removeAll(burning.keySet());
        if (standing.isEmpty()) return;
        Section section = standing.get(ThreadLocalRandom.current().nextInt(standing.size()));
        burning.put(section, System.currentTimeMillis() + BURN_MILLIS);
        Location at = centre(section);
        host.world().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 2F, .5F);
        host.broadcast(spent.contains(section.device()) ? "<red>The deck is burning again!</red> <gray>Its cistern is dry. Get off it!</gray>"
            : "<red>The deck is burning!</red> <gray>Right-click its " + Chat.escape(device(section.device()).title()) + " before it falls.</gray>");
    }

    private Location centre(Section section) {
        var box = section.box();
        return new Location(host.world(), (box.minX() + box.maxX()) / 2D + .5, box.minY() + 1, (box.minZ() + box.maxZ()) / 2D + .5);
    }

    private void tickBurns() {
        World world = host.world();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        for (Section section : List.copyOf(burning.keySet())) {
            var box = section.box();
            if (now >= burning.get(section)) {
                burning.remove(section);
                giveWay(section);
                continue;
            }
            if (!spent.contains(section.device())) {
                Location cistern = middle(device(section.device()));
                world.spawnParticle(Particle.SPLASH, cistern.clone().add(0, .6, 0), 14, .3, .2, .3, 0);
                world.spawnParticle(Particle.DRIPPING_WATER, cistern.clone().add(0, 1.2, 0), 4, .2, .3, .2, 0);
            }
            int width = box.maxX() - box.minX() + 1, depth = box.maxZ() - box.minZ() + 1;
            world.spawnParticle(Particle.FLAME, centre(section), width * depth / 12, width / 2.5, .3, depth / 2.5, .02);
            world.spawnParticle(Particle.LARGE_SMOKE, centre(section), width * depth / 30, width / 2.5, .6, depth / 2.5, .02);
            world.playSound(centre(section), Sound.BLOCK_FIRE_AMBIENT, 2F, .6F);
            for (int spot = 0; spot < 4; spot++) {
                Block open = world.getBlockAt(random.nextInt(box.minX(), box.maxX() + 1), box.minY() + 1, random.nextInt(box.minZ(), box.maxZ() + 1));
                if (open.getType().isAir() && open.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) host.temporary(open, Material.FIRE, 40);
            }
        }
    }

    private void quench(Player player, Device device, Section section) {
        if (!spent.add(device.id())) {
            host.bar(player, "<red>The " + Chat.escape(device.title()) + " is dry</red>");
            return;
        }
        burning.remove(section);
        swap(block(device), Material.CAULDRON);
        World world = host.world();
        world.spawnParticle(Particle.SPLASH, centre(section), 300, 5, .4, 5, 0);
        world.spawnParticle(Particle.CLOUD, centre(section), 60, 5, .3, 5, .02);
        world.playSound(centre(section), Sound.ITEM_BUCKET_EMPTY, 2F, .7F);
        world.playSound(centre(section), Sound.BLOCK_FIRE_EXTINGUISH, 2F, .7F);
        for (Player member : host.online()) host.bar(member, "<aqua>" + Chat.escape(player.getName()) + " put the fire out</aqua>");
        host.log(player.getName() + " quenched " + device.id());
    }

    private void giveWay(Section section) {
        lost.add(section);
        var box = section.box();
        World world = host.world();
        for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isAir()) clear(block);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int piece = 0; piece < 12; piece++) timber(new Location(world, random.nextDouble(box.minX(), box.maxX()), box.minY(), random.nextDouble(box.minZ(), box.maxZ())));
        world.playSound(centre(section), Sound.ENTITY_GENERIC_EXPLODE, 2F, .6F);
        world.playSound(centre(section), Sound.BLOCK_WOOD_BREAK, 2F, .5F);
        world.spawnParticle(Particle.LARGE_SMOKE, centre(section), 60, 6, .6, 6, .03);
        host.broadcast("<red>The deck gives way!</red>");
        host.log("deck lost");
    }

    private void timber(Location from) {
        BlockDisplay beam = host.world().spawn(from, BlockDisplay.class, display -> {
            display.setBlock(Material.SPRUCE_PLANKS.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(2);
            display.setTransformation(new Transformation(new Vector3f(-1, 0, -1), new Quaternionf().rotateY(from.getBlockX()), new Vector3f(2.4F, .3F, 1F), new Quaternionf()));
        });
        falling.add(beam);
        int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, run -> {
            int now = ++tick[0];
            if (!host.live() || now > 40) {
                run.cancel();
                beam.remove();
                falling.remove(beam);
                return;
            }
            beam.teleport(from.clone().subtract(0, .05 * now * now, 0));
        }, 1L, 1L);
    }

    // ---------------------------------------------------------------------------------------------
    // Wind
    // ---------------------------------------------------------------------------------------------

    /** Leaves stream along one of the wind's lanes, then the gust hits. Something solid upwind is cover. */
    private void gust(Hazard hazard) {
        Lane lane = hazard.lanes().get(ThreadLocalRandom.current().nextInt(hazard.lanes().size()));
        World world = host.world();
        double floor = hazard.area().minY();
        Vector wind = new Vector(lane.x2() - lane.x1(), 0, lane.z2() - lane.z1()).normalize();
        Location from = new Location(world, lane.x1() + .5, floor + 1, lane.z1() + .5), to = new Location(world, lane.x2() + .5, floor + 1, lane.z2() + .5);
        Vector across = new Vector(-wind.getZ(), 0, wind.getX());
        host.broadcast("<aqua>The wind rises!</aqua> <gray>Get behind something, or it will throw you off.</gray>");
        for (Player player : host.online()) player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_INHALE, 1.6F, .6F);
        int[] tick = {0};
        Set<UUID> struck = new HashSet<>();
        Bukkit.getScheduler().runTaskTimer(plugin, run -> {
            if (!host.live() || host.bosses().isEmpty()) { run.cancel(); return; }
            int now = tick[0]++;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (now < GUST_WARNING_TICKS) {
                if (now % 4 == 0) for (double offset = -18; offset <= 18; offset += 6) {
                    Vector side = across.clone().multiply(offset + random.nextDouble(-2, 2));
                    Telegraph.line(from.clone().add(side), to.clone().add(side), Telegraph.FROST);
                    Telegraph.shards(from.clone().add(side), 3, .4, Material.DARK_OAK_LEAVES);
                }
                return;
            }
            if (now >= GUST_WARNING_TICKS + GUST_TICKS) { run.cancel(); return; }
            if (now == GUST_WARNING_TICKS) for (Player player : host.online()) player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.6F, .7F);
            world.spawnParticle(Particle.CLOUD, from.clone().add(to).multiply(.5), 30, 12, 1.5, 12, .4);
            for (Player player : host.online()) {
                var area = hazard.area();
                if (!exposed(player) || !area.contains(player.getX(), area.minY(), player.getZ()) || sheltered(player, wind)) continue;
                player.setVelocity(player.getVelocity().add(wind.clone().multiply(GUST_PUSH)).setY(Math.max(player.getVelocity().getY(), .12)));
                if (!struck.add(player.getUniqueId())) continue;
                host.log("gust caught " + player.getName());
                if (hazard.damage() > 0) player.damage(hazard.damage());
            }
        }, 0L, 1L);
    }

    private boolean sheltered(Player player, Vector wind) {
        Location eye = player.getLocation().add(0, 1, 0);
        for (double step = 1; step <= GUST_COVER; step++) if (eye.clone().subtract(wind.clone().multiply(step)).getBlock().getType().isSolid()) return true;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Wards
    // ---------------------------------------------------------------------------------------------

    /** The boss lifts out of reach until the party puts out enough of the room's fires. Bigger parties have more of them to find. */
    void ward(int seconds, List<String> devices) {
        List<LivingEntity> bosses = host.bosses();
        if (ward != null || bosses.isEmpty()) return;
        List<String> pool = new ArrayList<>(devices);
        Collections.shuffle(pool);
        int count = Math.clamp(2 + host.online().size(), 1, pool.size());
        ward = new Ward(new LinkedHashSet<>(pool.subList(0, count)), System.currentTimeMillis() + seconds * 1000L);
        for (LivingEntity boss : bosses) {
            ward.held.put(boss.getUniqueId(), boss.getLocation());
            host.cancelCast(boss);
            boss.setInvulnerable(true);
            if (boss instanceof Mob mob) {
                mob.getPathfinder().stopPathfinding();
                mob.setAware(false);
            }
            boss.teleport(boss.getLocation().add(0, REACH, 0));
            boss.setGravity(false);
            ward.columns.add(column(boss.getLocation().subtract(0, REACH + 1, 0)));
        }
        for (String id : ward.burning) {
            light(id, true);
            host.world().spawnParticle(Particle.FLAME, middle(device(id)), 40, .3, .6, .3, .08);
        }
        BossBar bar = host.bar();
        if (bar != null) bar.color(BossBar.Color.YELLOW);
        host.broadcast("<gold>The fires hold him up!</gold> <gray>Right-click " + count + " burning brazier" + (count == 1 ? "" : "s") + " to douse them.</gray>");
        host.log("ward up: " + ward.burning);
    }

    private BlockDisplay column(Location at) {
        return host.world().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(Material.FIRE.createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(-1.3F, 0, -1.3F), new Quaternionf(), new Vector3f(2.6F, 12, 2.6F), new Quaternionf()));
        });
    }

    private void douse(Player player, Device device) {
        host.log(player.getName() + " doused " + device.id());
        ward.burning.remove(device.id());
        light(device.id(), false);
        World world = host.world();
        world.spawnParticle(Particle.LARGE_SMOKE, middle(device), 24, .3, .5, .3, .03);
        world.playSound(middle(device), Sound.BLOCK_FIRE_EXTINGUISH, 1.6F, .8F);
        for (Player member : host.online()) host.bar(member, "<gold>" + Chat.escape(player.getName()) + " doused a brazier</gold> <gray>"
            + (ward.total - ward.burning.size()) + "/" + ward.total + "</gray>");
        if (ward.burning.isEmpty()) broken();
    }

    private void broken() {
        List<LivingEntity> bosses = host.bosses();
        endWard();
        for (LivingEntity boss : bosses) {
            stagger(boss, STAGGER_TICKS);
            boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 1);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 1.4F, .6F);
        }
        host.broadcast("<gold>He falls!</gold> <gray>Strike now, while he's down.</gray>");
        host.script("ward-broken");
    }

    private void tickWard() {
        if (ward == null) return;
        World world = host.world();
        for (String id : ward.burning) {
            Location at = middle(device(id));
            world.spawnParticle(Particle.FLAME, at, 8, .25, .5, .25, .04);
            world.spawnParticle(Particle.LAVA, at, 1, .2, .2, .2, 0);
        }
        for (LivingEntity boss : host.bosses()) world.spawnParticle(Particle.FLAME, boss.getLocation().add(0, 1, 0), 12, .5, 1, .5, .03);
        if (System.currentTimeMillis() < ward.until) return;
        endWard();
        host.broadcast("<red>Too slow!</red> <gray>The fires have burned through him.</gray>");
        host.script("ward-failed");
    }

    /** The boss comes back down and the columns go. */
    private void endWard() {
        Ward ending = ward;
        ward = null;
        if (ending == null) return;
        ending.columns.forEach(Display::remove);
        for (var entry : ending.held.entrySet()) {
            if (!(Bukkit.getEntity(entry.getKey()) instanceof LivingEntity boss) || !boss.isValid()) continue;
            boss.setGravity(true);
            boss.setInvulnerable(false);
            boss.teleport(entry.getValue());
            if (boss instanceof Mob mob) mob.setAware(true);
        }
        BossBar bar = host.bar();
        if (bar != null) bar.color(BossBar.Color.RED);
    }

    boolean warding() { return ward != null; }
}
