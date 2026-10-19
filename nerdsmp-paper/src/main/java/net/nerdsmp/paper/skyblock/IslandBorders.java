package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Private particle overlay, not a client/server world border or a collision wall. */
public final class IslandBorders implements AutoCloseable {
    private static final int MAX_VIEWERS = 128, VIEWERS_PER_PASS = 16;
    private final NerdSmpPlugin plugin;
    private final IslandService islands;
    private final World world;
    private final Map<UUID, View> viewers = new LinkedHashMap<>();
    private final BukkitTask task;
    private final Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(72, 205, 190), 1.3F);
    private int cursor;
    public IslandBorders(NerdSmpPlugin plugin, IslandService islands, World world) {
        this.plugin = plugin; this.islands = islands; this.world = world;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }
    public boolean enabled(Player player, AccountId owner) {
        View view = viewers.get(player.getUniqueId()); return view != null && view.owner().equals(owner);
    }
    public void toggle(Player player, AccountId owner) { set(player, owner, !enabled(player, owner)); }
    public void set(Player player, AccountId owner, boolean enabled) {
        if (!enabled) { forget(player); Chat.info(player, "Island outline hidden."); return; }
        AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null);
        var island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("That island does not exist."));
        if (actor == null || !island.canEnter(actor) || !world.equals(player.getWorld())
            || islands.at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4)
                .filter(current -> current.id().equals(island.id())).isEmpty())
            throw new IllegalArgumentException("Visit that island to show its boundary.");
        if (!viewers.containsKey(player.getUniqueId()) && viewers.size() >= MAX_VIEWERS)
            throw new IllegalArgumentException("Too many outlines are open. Try again shortly.");
        viewers.put(player.getUniqueId(), new View(actor, owner, island.id()));
        Chat.success(player, "Island outline shown, only to you. Walk near an edge to see it. <gray>" + Chat.command("island border off") + " hides it.</gray>");
    }
    public void forget(Player player) { viewers.remove(player.getUniqueId()); }
    public String diagnostics() { return "Island borders: " + viewers.size() + "/128 viewers; 16 per 10 ticks; max 1,024 private particles per pass; no entities or chunk tickets."; }
    private void tick() {
        viewers.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey()); var island = islands.owned(entry.getValue().owner()).orElse(null);
            return player == null || !valid(player, entry.getValue(), island);
        });
        if (viewers.isEmpty()) { cursor = 0; return; }
        List<Map.Entry<UUID, View>> active = List.copyOf(viewers.entrySet());
        for (int visited = 0; visited < Math.min(VIEWERS_PER_PASS, active.size()); visited++) {
            cursor %= active.size(); var entry = active.get(cursor++); Player player = Bukkit.getPlayer(entry.getKey());
            var island = islands.owned(entry.getValue().owner()).orElseThrow();
            Location location = player.getLocation();
            double y = Math.clamp(Math.floor(location.getY()), world.getMinHeight() + 1, world.getMaxHeight() - 5);
            for (var point : IslandBoundary.sample(IslandBoundary.bounds(island.slot(), island.tier()), location.getX(), y, location.getZ())) {
                if (!world.isChunkLoaded(((int) Math.floor(point.x())) >> 4, ((int) Math.floor(point.z())) >> 4)) continue;
                player.spawnParticle(Particle.DUST, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0, dust);
            }
        }
    }
    private boolean valid(Player player, View view, IslandService.Island island) {
        return player.isOnline() && world.equals(player.getWorld()) && island != null && island.id().equals(view.island())
            && plugin.sessions().account(player.getUniqueId()).filter(view.account()::equals).isPresent()
            && island.canEnter(view.account()) && islands.at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4)
                .filter(current -> current.id().equals(island.id())).isPresent();
    }
    @Override public void close() { task.cancel(); viewers.clear(); }
    private record View(AccountId account, AccountId owner, UUID island) { }
}
