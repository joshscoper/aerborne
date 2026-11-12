package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.reach.ReachTerrain.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The Reach has no day and night cycle: the world's clock stands still, and each zone keeps its own hour, weather
 * and air. Highcrown is a bright late morning, the Vale a golden early one, the Barrowdowns hold a long sunset with
 * mist low on the heather, Thornwood stands in steady rain under its canopy, Mistmere in a wet grey dawn, the
 * Ironspine in clear cold air with snow on the heights, the Sunscar in a hot afternoon with red dust on the wind,
 * the Ashen Scar in an ember-lit night of falling ash, and the Archive Isle in a still twilight. A few places keep
 * their own hour inside their zone (Windmill Ridge is always moonlit, for Hollow Jack).
 * <p>
 * All of it is shown to each player alone (their own time, weather, particles and sounds), eased from one zone's
 * hour to the next as they walk, and cleared when they leave the Reach.
 */
final class ReachClimate implements Listener {
    /** One zone's air: its hour (0 is dawn, 6000 noon, 18000 midnight), rain or not, and its ambience. */
    record Climate(String id, long time, boolean rain, Ambience ambience) {
        boolean night() { return time >= 13_000 && time <= 23_000; }
    }

    /** What drifts in the air around a player, and what is heard now and then. */
    enum Ambience { CITY, MEADOW, MOOR, FOREST, MARSH, PEAKS, DUST, ASH, ARCHIVE, NIGHT_FIELDS }

    /** A place inside a zone that keeps its own hour: within {@code radius} of the spot. */
    private record Local(String spot, double radius, Climate climate) { }

    static final Map<Zone, Climate> ZONES = Map.of(
        Zone.HIGHCROWN, new Climate("highcrown", 4500, false, Ambience.CITY),
        Zone.GOLDENREACH, new Climate("goldenreach", 2200, false, Ambience.MEADOW),
        Zone.BARROWDOWNS, new Climate("barrowdowns", 12300, false, Ambience.MOOR),
        Zone.THORNWOOD, new Climate("thornwood", 6000, true, Ambience.FOREST),
        Zone.MISTMERE, new Climate("mistmere", 23400, true, Ambience.MARSH),
        Zone.IRONSPINE, new Climate("ironspine", 3000, false, Ambience.PEAKS),
        Zone.SUNSCAR, new Climate("sunscar", 8200, false, Ambience.DUST),
        Zone.SCAR, new Climate("ashen_scar", 15500, false, Ambience.ASH),
        Zone.ARCHIVE, new Climate("archive_isle", 22600, false, Ambience.ARCHIVE));

    private static final List<Local> LOCALS = List.of(
        new Local("goldenreach.windmill_ridge", 70, new Climate("windmill_ridge", 17800, false, Ambience.NIGHT_FIELDS)));

    /** Each cue belongs to a physical place. Nearby players hear the same beat from the same direction. */
    private record Source(String spot, Sound sound, int interval, float volume, float pitch) { }
    private static final List<Source> SOURCES = List.of(
        new Source("highcrown.fane", Sound.BLOCK_BELL_RESONATE, 240, 0.55F, 0.83F),
        new Source("highcrown.npc_kestrel_way", Sound.BLOCK_AMETHYST_BLOCK_CHIME, 130, 0.38F, 0.83F),
        new Source("goldenreach.smithy", Sound.BLOCK_ANVIL_LAND, 90, 0.26F, 1.22F),
        new Source("goldenreach.cellars_entrance", Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 180, 0.36F, 0.66F),
        new Source("great_aqueduct.place", Sound.BLOCK_WATER_AMBIENT, 70, 0.65F, 0.86F),
        new Source("headframe.place", Sound.BLOCK_CHAIN_HIT, 110, 0.5F, 0.77F),
        new Source("heart_tree.place", Sound.BLOCK_ROOTS_STEP, 150, 0.34F, 0.70F),
        new Source("seal_spire.place", Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 170, 0.40F, 0.64F));

    /** How far the shown hour moves toward the zone's in one update (twice a second): a walk across a border takes a few seconds. */
    private static final long EASE = 450;

    private final NerdSmpPlugin plugin;
    private final ReachService reach;
    private final Map<String, ReachFeatures.Spot> spots;
    private final Map<UUID, Double> shown = new HashMap<>();
    private final Map<UUID, Boolean> raining = new HashMap<>();
    private final SplittableRandom random = new SplittableRandom(8128);
    private final BlockData redSand = Material.RED_SAND.createBlockData();

    ReachClimate(NerdSmpPlugin plugin, ReachService reach) {
        this.plugin = plugin;
        this.reach = reach;
        this.spots = reach.features().spots();
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    /** The climate at a point: a place's own, else its zone's. Open sky takes the zone nearest under it. */
    Climate at(double x, double z) {
        for (Local local : LOCALS) {
            ReachFeatures.Spot s = spots.get(local.spot());
            if (s != null && Math.hypot(x - s.x(), z - s.z()) < local.radius()) return local.climate();
        }
        Zone zone = reach.terrain().zoneAt(x, z);
        Climate c = ZONES.get(zone);
        return c != null ? c : ZONES.get(Zone.HIGHCROWN);
    }

    /** The climate at a named spot, for things that keep hours (night-only encounters). */
    Climate atSpot(String spot) {
        ReachFeatures.Spot s = spots.get(spot);
        return s == null ? ZONES.get(Zone.HIGHCROWN) : at(s.x(), s.z());
    }

    private void tick() {
        World world = reach.world();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            Zone zone = reach.terrain().zoneAt(at.getX(), at.getZ());
            Climate target = zone == Zone.SKY && shown.containsKey(player.getUniqueId()) ? null : at(at.getX(), at.getZ());
            if (target != null) {
                double now = shown.getOrDefault(player.getUniqueId(), (double) target.time());
                double next = approach(now, target.time());
                shown.put(player.getUniqueId(), next);
                player.setPlayerTime(Math.floorMod((long) next, 24_000L), false);
                Boolean wet = raining.get(player.getUniqueId());
                if (wet == null || wet != target.rain()) {
                    player.setPlayerWeather(target.rain() ? WeatherType.DOWNFALL : WeatherType.CLEAR);
                    raining.put(player.getUniqueId(), target.rain());
                }
                ambience(player, at, target.ambience());
                soundAtLandmark(player, at, world.getGameTime() / 10);
            }
        }
    }

    private void soundAtLandmark(Player player, Location at, long beat) {
        for (Source source : SOURCES) {
            if (Math.floorMod(beat + source.spot().hashCode(), source.interval() / 10) != 0) continue;
            ReachFeatures.Spot spot = spots.get(source.spot());
            if (spot == null) continue;
            double dx = at.getX() - spot.x(), dy = at.getY() - spot.y(), dz = at.getZ() - spot.z();
            if (dx * dx + dy * dy + dz * dz > 16 * 16) continue;
            Location origin = new Location(at.getWorld(), spot.x(), spot.y() + 1, spot.z());
            player.playSound(origin, source.sound(), SoundCategory.AMBIENT, source.volume(), source.pitch());
        }
    }

    /** Moves an hour toward another the short way round the day, at most {@link #EASE} at a time. */
    static double approach(double now, double target) {
        double diff = Math.floorMod((long) (target - now), 24_000L);
        if (diff > 12_000) diff -= 24_000;
        if (Math.abs(diff) <= EASE) return target;
        return Math.floorMod((long) (now + Math.signum(diff) * EASE), 24_000L);
    }

    /** A few particles drifting round the player, and now and then a sound of the place. Only that player sees or hears them. */
    private void ambience(Player player, Location at, Ambience ambience) {
        double x = at.getX(), y = at.getY(), z = at.getZ();
        switch (ambience) {
            case CITY -> { /* The Fane and waystone supply the sound in their own streets. */ }
            case MEADOW -> {
                spray(player, Particle.SPORE_BLOSSOM_AIR, x, y + 2, z, 2, 10);
                if (random.nextInt(60) == 0) player.playSound(at, Sound.ENTITY_BEE_LOOP, 0.2F, 1.2F);
            }
            case MOOR -> {
                spray(player, Particle.WHITE_ASH, x, y, z, 10, 14);
                if (random.nextInt(140) == 0) player.playSound(at.clone().add(random.nextInt(40) - 20, 0, random.nextInt(40) - 20), Sound.ENTITY_WOLF_GROWL, 0.3F, 0.6F);
            }
            case FOREST -> {
                spray(player, Particle.SPORE_BLOSSOM_AIR, x, y + 1, z, 4, 10);
                spray(player, Particle.MYCELIUM, x, y, z, 6, 10);
                if (random.nextInt(120) == 0) player.playSound(at, Sound.AMBIENT_CAVE, 0.2F, 1.4F);
            }
            case MARSH -> {
                spray(player, Particle.CLOUD, x, y - 0.5, z, 2, 12);
                if (random.nextInt(50) == 0) player.playSound(at.clone().add(random.nextInt(16) - 8, 0, random.nextInt(16) - 8), Sound.ENTITY_FROG_AMBIENT, 0.4F, 1F);
            }
            case PEAKS -> {
                if (y > 150) spray(player, Particle.SNOWFLAKE, x, y + 4, z, 14, 12);
                if (random.nextInt(100) == 0) player.playSound(at, Sound.ITEM_ELYTRA_FLYING, 0.08F, 0.6F);
            }
            case DUST -> {
                for (int i = 0; i < 6; i++)
                    player.spawnParticle(Particle.FALLING_DUST, x + random.nextDouble() * 24 - 12, y + random.nextDouble() * 6, z + random.nextDouble() * 24 - 12, 1, redSand);
            }
            case ASH -> {
                spray(player, Particle.ASH, x, y + 2, z, 24, 14);
                spray(player, Particle.WHITE_ASH, x, y + 2, z, 6, 14);
                if (random.nextInt(4) == 0) player.spawnParticle(Particle.LAVA, x + random.nextDouble() * 20 - 10, y - 1, z + random.nextDouble() * 20 - 10, 1);
                if (random.nextInt(40) == 0) player.playSound(at, Sound.AMBIENT_BASALT_DELTAS_ADDITIONS, 0.5F, 1F);
            }
            case ARCHIVE -> {
                spray(player, Particle.END_ROD, x, y + 3, z, 2, 14);
                spray(player, Particle.ENCHANT, x, y + 1, z, 6, 6);
                if (random.nextInt(70) == 0) player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.1F);
            }
            case NIGHT_FIELDS -> {
                spray(player, Particle.WHITE_ASH, x, y, z, 6, 12);
                if (random.nextInt(110) == 0) player.playSound(at, Sound.ENTITY_FOX_SCREECH, 0.3F, 0.8F);
            }
        }
    }

    private void spray(Player player, Particle particle, double x, double y, double z, int count, double spread) {
        for (int i = 0; i < count; i++)
            player.spawnParticle(particle, x + random.nextDouble() * spread * 2 - spread, y + random.nextDouble() * 4, z + random.nextDouble() * spread * 2 - spread, 1, 0, 0, 0, 0);
    }

    private void clear(Player player) {
        player.resetPlayerTime();
        player.resetPlayerWeather();
        shown.remove(player.getUniqueId());
        raining.remove(player.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (reach.isReach(event.getFrom())) clear(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shown.remove(event.getPlayer().getUniqueId());
        raining.remove(event.getPlayer().getUniqueId());
    }
}
