package net.nerdsmp.paper.worldpack;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps players on a pack map's playable rectangle. A vanilla world border can only be square, so it covers
 * the long axis; this fence holds the short axis, where untouched generated land lies beyond the map.
 * Anyone who crosses (on foot, swimming, by boat or elytra) is put back where they last stood inside.
 */
public final class MapFence implements Listener {
    public static final String BYPASS = "nerdsmp.worldpack.bypass";
    private static final int WARN_DISTANCE = 10;
    private final NerdSmpPlugin plugin;
    private final Map<UUID, WorldPack.Border> fences = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastInside = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private BukkitTask task;

    public MapFence(NerdSmpPlugin plugin) { this.plugin = plugin; }

    public void guard(World world, WorldPack.Border border) {
        fences.put(world.getUID(), border);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L, 4L);
    }

    public WorldPack.Border border(World world) { return fences.get(world.getUID()); }

    private boolean exempt(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR || player.hasPermission(BYPASS);
    }

    private void check() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            WorldPack.Border border = fences.get(player.getWorld().getUID());
            if (border == null) continue;
            Location at = player.getLocation();
            if (border.contains(at.getX(), at.getZ())) {
                if (inset(border, at) >= 2) lastInside.put(player.getUniqueId(), at.clone());
                if (!exempt(player) && inset(border, at) <= WARN_DISTANCE) showEdge(player, border, at, now);
                continue;
            }
            if (!exempt(player)) pushBack(player, border, now);
        }
    }

    private void pushBack(Player player, WorldPack.Border border, long now) {
        if (player.isInsideVehicle()) player.leaveVehicle();
        if (player.isGliding()) player.setGliding(false);
        Location back = lastInside.get(player.getUniqueId());
        // Only a nearby spot is a sensible place to return to; otherwise step just inside the nearest edge.
        if (back == null || !back.getWorld().equals(player.getWorld()) || back.distanceSquared(player.getLocation()) > 64 * 64)
            back = inward(player, border);
        back = back.clone();
        back.setYaw(player.getLocation().getYaw() + 180F);
        back.setPitch(0F);
        player.setVelocity(new Vector());
        player.setFallDistance(0F);
        player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (now - lastNotice.getOrDefault(player.getUniqueId(), 0L) > 3_000L) {
            lastNotice.put(player.getUniqueId(), now);
            Chat.notice(player, "A wall of sea mist turns you back. The Highcrown Isles end here.");
        }
    }

    /** A point a few blocks inside the edge, on the highest block there (sea surface over open water). */
    private static Location inward(Player player, WorldPack.Border border) {
        Location at = player.getLocation();
        double x = Math.max(border.minX() + 3.5, Math.min(border.maxX() - 2.5, at.getX()));
        double z = Math.max(border.minZ() + 3.5, Math.min(border.maxZ() - 2.5, at.getZ()));
        World world = player.getWorld();
        int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        return new Location(world, x, y, z, at.getYaw(), 0F);
    }

    private static double inset(WorldPack.Border border, Location at) {
        return Math.min(Math.min(at.getX() - border.minX(), border.maxX() + 1 - at.getX()),
            Math.min(at.getZ() - border.minZ(), border.maxZ() + 1 - at.getZ()));
    }

    /** A curtain of mist on the nearby edge, visible only to the player approaching it. */
    private void showEdge(Player player, WorldPack.Border border, Location at, long now) {
        double y = at.getY();
        double[][] edges = {
            {border.minX(), Double.NaN}, {border.maxX() + 1, Double.NaN}, {Double.NaN, border.minZ()}, {Double.NaN, border.maxZ() + 1}};
        for (double[] edge : edges) {
            boolean alongZ = !Double.isNaN(edge[0]);
            double distance = alongZ ? Math.abs(at.getX() - edge[0]) : Math.abs(at.getZ() - edge[1]);
            if (distance > WARN_DISTANCE) continue;
            for (int step = -6; step <= 6; step += 2) for (int rise = -1; rise <= 3; rise++) {
                double px = alongZ ? edge[0] : at.getX() + step, pz = alongZ ? at.getZ() + step : edge[1];
                player.spawnParticle(Particle.CLOUD, px, y + rise, pz, 1, 0.1, 0.2, 0.1, 0.0);
            }
        }
        if (now - lastNotice.getOrDefault(player.getUniqueId(), 0L) > 8_000L) {
            lastNotice.put(player.getUniqueId(), now);
            net.nerdsmp.paper.item.PlayerStatsService.message(player, Chat.render("<gray>The sea mist thickens. The Highcrown Isles end just ahead.</gray>"));
        }
    }

    /** Pearls, chorus fruit and similar can't hop over the fence; staff commands and plugin travel still work. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        WorldPack.Border border = to.getWorld() == null ? null : fences.get(to.getWorld().getUID());
        if (border == null || border.contains(to.getX(), to.getZ()) || exempt(event.getPlayer())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastInside.remove(event.getPlayer().getUniqueId());
        lastNotice.remove(event.getPlayer().getUniqueId());
    }
}
