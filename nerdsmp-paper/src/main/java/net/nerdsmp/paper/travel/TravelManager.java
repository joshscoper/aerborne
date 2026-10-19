package net.nerdsmp.paper.travel;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.social.SocialManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class TravelManager implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PlayerSettingsService settings;
    private final SocialManager social;
    private final TravelStore destinations;
    private final Duration requestLifetime;
    private final Duration cooldown;
    private final int warmupSeconds;
    private final Map<UUID, TeleportRequest> requests = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Instant> cooldowns = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Map<UUID, PendingTrip> inFlight = new HashMap<>();

    public TravelManager(NerdSmpPlugin plugin, PlayerSettingsService settings, SocialManager social) {
        this.plugin = plugin;
        this.settings = settings;
        this.social = social;
        destinations = new TravelStore(new File(plugin.getDataFolder(), "travel.yml"));
        requestLifetime = Duration.ofSeconds(Math.clamp(plugin.getConfig().getLong("travel.request-seconds", 60L), 10, 300));
        cooldown = Duration.ofSeconds(Math.clamp(plugin.getConfig().getLong("travel.cooldown-seconds", 15L), 0, 300));
        warmupSeconds = Math.clamp(plugin.getConfig().getInt("travel.warmup-seconds", 3), 0, 30);
    }

    public boolean handle(String command, CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(player);
            case "warp" -> warp(player, args);
            case "warps" -> listWarps(player);
            case "setwarp" -> setWarp(player, args);
            case "delwarp" -> deleteWarp(player, args);
            case "setspawn" -> setSpawn(player);
            case "tpa" -> request(player, args, Direction.TO_TARGET);
            case "tpahere" -> request(player, args, Direction.TARGET_TO_SOURCE);
            case "tpaccept" -> accept(player, args);
            case "tpdeny" -> deny(player, args);
            case "tpcancel" -> cancelRequest(player);
            case "back" -> back(player);
            default -> false;
        };
    }

    public List<String> tabComplete(String command, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String name = command.toLowerCase(Locale.ROOT);
        if (name.equals("warp") && args.length == 1) {
            return warpNames();
        }
        if (name.equals("delwarp") && args.length == 1 && player.hasPermission("nerdsmp.travel.admin")) {
            return warpNames();
        }
        if ((name.equals("tpa") || name.equals("tpahere")) && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().filter(target -> !target.equals(player))
                .filter(target -> social.canContact(player, target, PlayerSettingsService.Setting.TELEPORT_REQUESTS))
                .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if ((name.equals("tpaccept") || name.equals("tpdeny")) && args.length == 1) {
            TeleportRequest request = activeRequest(player.getUniqueId());
            Player source = request == null ? null : Bukkit.getPlayer(request.source);
            return source == null ? List.of() : List.of(source.getName());
        }
        return List.of();
    }

    public List<String> warpNames() {
        return destinations.names();
    }
    public record WarpDefinition(String world,double x,double y,double z,float yaw,float pitch) { }
    public Optional<WarpDefinition> warpDefinition(String name) {
        return destinations.warp(name).map(point->new WarpDefinition(point.world(),point.x(),point.y(),point.z(),point.yaw(),point.pitch()));
    }
    public Optional<WarpDefinition> spawnDefinition() {
        return destinations.spawn().map(point->new WarpDefinition(point.world(),point.x(),point.y(),point.z(),point.yaw(),point.pitch()));
    }
    public Optional<Location> warpLocation(String name) {
        return destinations.warp(name).flatMap(point -> point.resolve(TravelManager::resolveWorld))
            .filter(this::publicDestination).map(Location::clone);
    }
    /** Only the configured public hub; an unavailable saved world never resolves to another world. */
    public Location publicSpawn() {
        return destinations.spawn().flatMap(point -> point.resolve(TravelManager::resolveWorld))
            .filter(this::publicDestination).map(Location::clone).orElse(null);
    }
    public boolean nearCentralSpawn(Location location, double radius) {
        Location spawn = publicSpawn();
        return Double.isFinite(radius) && radius >= 0 && spawn != null && publicDestination(location)
            && spawn.getWorld().equals(location.getWorld())
            && spawn.distanceSquared(location) <= radius * radius;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Location spawn = spawnLocation();
        if (!event.getPlayer().hasPlayedBefore() && spawn != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    event.getPlayer().teleportAsync(spawn.clone());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = spawnLocation();
        if (plugin.skyblock() != null && (plugin.skyblock().isIslandWorld(event.getPlayer().getWorld())
            || plugin.skyblock().isIslandWorld(event.getRespawnLocation().getWorld()))) return;
        if (spawn != null && !event.isBedSpawn() && !event.isAnchorSpawn()) {
            event.setRespawnLocation(spawn.clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        lastLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup != null && moved(warmup.origin, event.getTo())) {
            cancelWarmup(event.getPlayer(), "Teleport cancelled because you moved.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && warmups.containsKey(player.getUniqueId())) {
            cancelWarmup(player, "Teleport cancelled because you took damage.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelWarmup(event.getPlayer(), null);
        inFlight.remove(event.getPlayer().getUniqueId());
        lastLocations.remove(event.getPlayer().getUniqueId());
        requests.remove(event.getPlayer().getUniqueId());
        requests.values().removeIf(request -> request.source.equals(event.getPlayer().getUniqueId()));
    }

    @Override
    public void close() {
        warmups.values().forEach(warmup -> warmup.task.cancel());
        warmups.clear();
        inFlight.clear();
    }

    private boolean spawn(Player player) {
        var record = destinations.spawn();
        Location spawn = spawnLocation();
        if (spawn == null) {
            Chat.error(player, "The public spawn is unavailable. Ask staff to check it.");
            return true;
        }
        BooleanSupplier unchanged = unchangedDestination(spawn, this::spawnLocation);
        begin(player, spawn, "spawn", () -> destinations.spawn().equals(record) && unchanged.getAsBoolean());
        return true;
    }

    private boolean warp(Player player, String[] args) {
        if (args.length != 1) {
            return listWarps(player);
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        var record = destinations.warp(name);
        if (record.isEmpty()) {
            Chat.error(player, "Unknown warp. " + Chat.command("warps") + " lists every destination.");
            return true;
        }
        Location destination = warpLocation(name).orElse(null);
        if (destination == null) {
            Chat.error(player, "That warp's world is unavailable or private. Ask staff to check it.");
            return true;
        }
        BooleanSupplier unchanged = unchangedDestination(destination, () -> warpLocation(name).orElse(null));
        begin(player, destination, "warp " + name,
            () -> destinations.warp(name).equals(record) && unchanged.getAsBoolean());
        return true;
    }

    private boolean listWarps(Player player) {
        List<String> names = warpNames();
        Chat.send(player, "<aqua><bold>Warps</bold></aqua>");
        if (names.isEmpty()) Chat.info(player, "No public warps have been set.");
        else Chat.send(player, String.join("<gray> • </gray>", names.stream().map(name -> Chat.command("warp " + name)).toList()));
        return true;
    }

    private boolean setWarp(Player player, String[] args) {
        if (!player.hasPermission("nerdsmp.travel.admin")) {
            Chat.noPermission(player);
            return true;
        }
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{1,24}")) {
            Chat.usage(player, "/setwarp <name>");
            Chat.info(player, "Names may use letters, numbers, _ and -.");
            return true;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Location location = player.getLocation().clone();
        if (!publicDestination(location)) {
            Chat.error(player, "Public warps cannot be placed on private island worlds.");
            return true;
        }
        if (!persist(player, () -> destinations.setWarp(name, TravelStore.Destination.from(location)))) return true;
        Chat.success(player, "Warp <white>" + Chat.escape(name) + "</white> saved.");
        return true;
    }

    private boolean deleteWarp(Player player, String[] args) {
        if (!player.hasPermission("nerdsmp.travel.admin")) {
            Chat.noPermission(player);
            return true;
        }
        if (args.length != 1 || destinations.warp(args[0]).isEmpty()) {
            Chat.usage(player, "/delwarp <warp>");
            return true;
        }
        if (!persist(player, () -> destinations.deleteWarp(args[0]))) return true;
        Chat.success(player, "Warp removed.");
        return true;
    }

    private boolean setSpawn(Player player) {
        if (!player.hasPermission("nerdsmp.travel.admin")) {
            Chat.noPermission(player);
            return true;
        }
        Location location = player.getLocation().clone();
        if (!publicDestination(location)) {
            Chat.error(player, "The public spawn cannot be placed on a private island world.");
            return true;
        }
        if (!persist(player, () -> destinations.setSpawn(TravelStore.Destination.from(location)))) return true;
        try {
            if (!location.getWorld().setSpawnLocation(location)) {
                Chat.notice(player, "Public spawn saved. The world's fallback spawn could not be updated.");
                return true;
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Public spawn saved, but the world fallback spawn could not be updated", error);
            Chat.notice(player, "Public spawn saved. The world's fallback spawn could not be updated.");
            return true;
        }
        Chat.success(player, "Public spawn updated.");
        return true;
    }

    private boolean request(Player player, String[] args, Direction direction) {
        if (args.length != 1) {
            Chat.usage(player, "/" + (direction == Direction.TO_TARGET ? "tpa" : "tpahere") + " <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            Chat.error(player, "Choose another online player.");
            return true;
        }
        if (!social.canContact(player, target, PlayerSettingsService.Setting.TELEPORT_REQUESTS)) {
            Chat.error(player, "That player is not accepting teleport requests from you.");
            return true;
        }
        if (activeRequest(target.getUniqueId()) != null) {
            Chat.notice(player, "That player already has a teleport request waiting. Try again later."); return true;
        }
        requests.values().removeIf(request -> request.source.equals(player.getUniqueId()) || !request.expiresAt.isAfter(Instant.now()));
        requests.put(target.getUniqueId(), new TeleportRequest(player.getUniqueId(), plugin.sessions().account(player.getUniqueId()).orElseThrow(),
            plugin.sessions().account(target.getUniqueId()).orElseThrow(), direction,
            Instant.now().plus(requestLifetime)));
        Chat.success(player, "Teleport request sent to <white>" + Chat.escape(target.getName()) + "</white>.");
        Chat.notice(target, "<white>" + Chat.escape(player.getName()) + "</white>" + (direction == Direction.TO_TARGET
            ? " wants to teleport to you." : " wants you to teleport to them.")
            + " <gray>Use " + Chat.command("tpaccept") + " or " + Chat.command("tpdeny") + ".</gray>");
        return true;
    }

    private boolean accept(Player target, String[] args) {
        TeleportRequest request = activeRequest(target.getUniqueId());
        Player source = request == null ? null : Bukkit.getPlayer(request.source);
        if (source == null || args.length > 1 || (args.length == 1 && !source.getName().equalsIgnoreCase(args[0]))) {
            Chat.error(target, "You do not have a matching teleport request.");
            return true;
        }
        requests.remove(target.getUniqueId());
        BooleanSupplier allowed = () -> current(source, request.sourceAccount) && current(target, request.targetAccount)
            && social.canContact(source, target, PlayerSettingsService.Setting.TELEPORT_REQUESTS);
        if (!allowed.getAsBoolean()) { Chat.error(target, "That teleport request is no longer available."); return true; }
        if (request.direction == Direction.TO_TARGET) {
            begin(source, target.getLocation(), "teleport to " + target.getName(), allowed);
        } else {
            begin(target, source.getLocation(), "teleport to " + source.getName(), allowed);
        }
        return true;
    }

    private boolean deny(Player target, String[] args) {
        TeleportRequest request = activeRequest(target.getUniqueId());
        Player source = request == null ? null : Bukkit.getPlayer(request.source);
        if (source == null || args.length > 1 || (args.length == 1 && !source.getName().equalsIgnoreCase(args[0]))) {
            Chat.error(target, "You do not have a matching teleport request.");
            return true;
        }
        requests.remove(target.getUniqueId());
        Chat.notice(target, "Teleport request denied.");
        Chat.notice(source, "<white>" + Chat.escape(target.getName()) + "</white> denied your teleport request.");
        return true;
    }

    private boolean cancelRequest(Player source) {
        boolean removed = requests.values().removeIf(request -> request.source.equals(source.getUniqueId()));
        if (removed) Chat.notice(source, "Teleport request cancelled.");
        else Chat.info(source, "You have no outgoing teleport request.");
        return true;
    }

    private boolean back(Player player) {
        Location destination = lastLocations.get(player.getUniqueId());
        if (destination == null) {
            Chat.error(player, "You do not have a previous location.");
            return true;
        }
        begin(player, destination, "previous location");
        return true;
    }

    private Location spawnLocation() {
        if (destinations.spawn().isPresent()) return publicSpawn();
        if (Bukkit.getWorlds().isEmpty()) return null;
        Location fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
        return publicDestination(fallback) ? fallback.clone() : null;
    }

    private boolean publicDestination(Location location) {
        return location != null && location.getWorld() != null
            && (plugin.skyblock() == null || !plugin.skyblock().isIslandWorld(location.getWorld()));
    }

    private static World resolveWorld(String world) {
        try { return Bukkit.getWorld(UUID.fromString(world)); }
        catch (IllegalArgumentException notUuid) { return Bukkit.getWorld(world); }
    }

    private boolean persist(Player player, Runnable mutation) {
        try {
            mutation.run();
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save travel destination", error);
            Chat.error(player, "That change could not be saved. Existing spawn and warps have not changed.");
            return false;
        }
    }

    public void visit(Player player, Location destination, String label, BooleanSupplier access) {
        begin(player, destination, label, access);
    }

    /** Recheck resolved world, position and facing at warmup completion and again at teleport dispatch. */
    static BooleanSupplier unchangedDestination(Location initial, Supplier<Location> current) {
        Location snapshot = initial.clone();
        return () -> snapshot.equals(current.get());
    }

    private void begin(Player player, Location requested, String label) {
        begin(player, requested, label, () -> true);
    }

    private void begin(Player player, Location requested, String label, BooleanSupplier consent) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) { Chat.accountLoading(player); return; }
        BooleanSupplier allowed = () -> current(player, account) && consent.getAsBoolean();
        if (warmups.containsKey(player.getUniqueId()) || inFlight.containsKey(player.getUniqueId())) {
            Chat.error(player, "A teleport is already warming up.");
            return;
        }
        Instant readyAt = cooldowns.get(player.getUniqueId());
        if (!player.hasPermission("nerdsmp.travel.bypass") && readyAt != null && readyAt.isAfter(Instant.now())) {
            long seconds = Math.max(1L, Duration.between(Instant.now(), readyAt).toSeconds() + 1L);
            Chat.error(player, "Travel is ready in <white>" + seconds + "</white> " + (seconds == 1L ? "second" : "seconds") + ".");
            return;
        }
        Location destination = safeLocation(requested);
        if (destination == null) {
            Chat.error(player, "That destination is not safe.");
            return;
        }
        if (warmupSeconds == 0 || player.hasPermission("nerdsmp.travel.bypass")) {
            teleport(player, destination, label, allowed);
            return;
        }
        Chat.notice(player, "Traveling to <white>" + Chat.escape(label) + "</white> in " + warmupSeconds
            + " seconds. <gray>Do not move or take damage.</gray>");
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            warmups.remove(player.getUniqueId());
            if (player.isOnline()) {
                teleport(player, destination, label, allowed);
            }
        }, warmupSeconds * 20L);
        warmups.put(player.getUniqueId(), new Warmup(player.getLocation().clone(), task));
    }

    private boolean current(Player player, AccountId account) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
            && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PendingTrip trip = inFlight.get(event.getPlayer().getUniqueId());
        if (trip != null && trip.destination.equals(event.getTo()) && (!trip.allowed.getAsBoolean() || !safe(event.getTo()))) event.setCancelled(true);
    }

    private void teleport(Player player, Location destination, String label, BooleanSupplier allowed) {
        if (!allowed.getAsBoolean() || !safe(destination)) { Chat.error(player, "Teleport cancelled. Access or the destination changed."); return; }
        Location origin = player.getLocation().clone();
        PendingTrip trip = new PendingTrip(destination.clone(), allowed);
        inFlight.put(player.getUniqueId(), trip);
        player.teleportAsync(destination.clone()).whenComplete((success, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
            if (!inFlight.remove(player.getUniqueId(), trip) || !allowed.getAsBoolean()) return;
            if (error != null || !Boolean.TRUE.equals(success)) {
                Chat.error(player, "Teleport failed. Try again.");
                return;
            }
            lastLocations.put(player.getUniqueId(), origin);
            cooldowns.put(player.getUniqueId(), Instant.now().plus(cooldown));
            Chat.success(player, "Arrived at <white>" + Chat.escape(label) + "</white>.");
            });
        });
    }

    private void cancelWarmup(Player player, String message) {
        Warmup warmup = warmups.remove(player.getUniqueId());
        if (warmup != null) {
            warmup.task.cancel();
            if (message != null) {
                Chat.error(player, Chat.escape(message));
            }
        }
    }

    private TeleportRequest activeRequest(UUID target) {
        TeleportRequest request = requests.get(target);
        if (request != null && request.expiresAt.isAfter(Instant.now())) {
            return request;
        }
        requests.remove(target);
        return null;
    }

    private Location safeLocation(Location requested) {
        if (requested == null) return null;
        World world = requested.getWorld();
        if (world == null || !world.getWorldBorder().isInside(requested)) {
            return null;
        }
        for (int radius = 0; radius <= 2; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y : List.of(0, 1, -1, 2, -2, 3, -3)) {
                        Location candidate = requested.clone().add(x, y, z);
                        candidate.setX(candidate.getBlockX() + 0.5D);
                        candidate.setZ(candidate.getBlockZ() + 0.5D);
                        if (safe(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean safe(Location location) {
        World world = location.getWorld();
        if (world == null || location.getY() <= world.getMinHeight() || location.getY() + 1 >= world.getMaxHeight()
            || !world.getWorldBorder().isInside(location)) return false;
        var feet = location.getBlock(); var head = location.clone().add(0, 1, 0).getBlock(); var floor = location.clone().add(0, -1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && floor.getType().isSolid()
            && TravelSafety.harmless(feet.getType()) && TravelSafety.harmless(head.getType()) && TravelSafety.harmless(floor.getType());
    }

    private boolean moved(Location from, Location to) {
        return !from.getWorld().equals(to.getWorld()) || from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.playersOnly(sender);
        return null;
    }




    private enum Direction {
        TO_TARGET,
        TARGET_TO_SOURCE
    }

    private record TeleportRequest(UUID source, AccountId sourceAccount, AccountId targetAccount, Direction direction, Instant expiresAt) {
    }

    private record PendingTrip(Location destination, BooleanSupplier allowed) { }

    private record Warmup(Location origin, BukkitTask task) {
    }
}
