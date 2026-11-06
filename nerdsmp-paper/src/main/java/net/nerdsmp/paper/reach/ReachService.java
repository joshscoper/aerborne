package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Owns the Wardens' Reach world: creates it from {@link ReachGenerator}, loads it with the same generator on every
 * start, pre-generates its chunks on request, and catches anyone who falls off the edge (the skyfarer's line).
 */
public final class ReachService implements Listener {
    public static final String DEFAULT_WORLD = "aerborne_reach";
    private final NerdSmpPlugin plugin;
    private final ReachTerrain terrain = new ReachTerrain();
    private final ReachFeatures features = new ReachFeatures(terrain, ReachBuilds.all(terrain));
    private final Map<UUID, Location> lastFooting = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rescued = new ConcurrentHashMap<>();
    private volatile Pregen pregen;
    private ReachGameplay gameplay;
    private ReachFlights flights;
    private ReachEdits edits;
    private ReachEditor editor;
    private ReachClimate climate;

    public ReachService(NerdSmpPlugin plugin) { this.plugin = plugin; }

    public ReachTerrain terrain() { return terrain; }
    public ReachFeatures features() { return features; }
    ReachClimate climate() { return climate; }

    public String worldName() {
        return plugin.getConfig().getString("reach.world", DEFAULT_WORLD) + (ReachGenerator.REVISION > 1 ? "_r" + ReachGenerator.REVISION : "");
    }

    public World world() { return Bukkit.getWorld(NamespacedKey.minecraft(worldName())); }
    public boolean isReach(World world) { return world != null && world.getName().equals(worldName()); }

    /** Loads the Reach if it has been generated before; a fresh server only creates it when staff ask. */
    public void start() {
        if (!plugin.getConfig().getBoolean("reach.enabled", true)) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try { plugin.worldPacks().register(ReachPack.build(worldName(), terrain, features)); }
        catch (RuntimeException failure) { plugin.getLogger().log(Level.SEVERE, "The Wardens' Reach world pack is invalid", failure); }
        // Hand edits are laid over the generated world, so they're loaded before the world is opened.
        edits = new ReachEdits(new java.io.File(plugin.getDataFolder(), "reach-edits.yml"), plugin.getResource("reach/edits.yml"));
        editor = new ReachEditor(plugin, this, edits);
        editor.start();
        climate = new ReachClimate(plugin, this);
        climate.start();
        gameplay = new ReachGameplay(plugin, this);
        gameplay.start();
        // The dig sites' nodes are registered once the world and the pack's zones are there, retried until they are.
        flights = new ReachFlights(plugin, this);
        flights.start();
        ReachDigSites digs = new ReachDigSites(plugin, this);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> { if (digs.register()) task.cancel(); }, 200L, 400L);
        if (Files.isDirectory(directory())) {
            try { open(); }
            catch (RuntimeException failure) { plugin.getLogger().log(Level.SEVERE, "The Wardens' Reach could not be loaded", failure); }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::recordFooting, 40L, 10L);
    }

    private Path directory() {
        return Bukkit.getServer().getLevelDirectory().toAbsolutePath().normalize()
            .resolve("dimensions").resolve("minecraft").resolve(worldName());
    }

    public World open() {
        World loaded = world();
        if (loaded != null) return loaded;
        boolean fresh = !Files.isDirectory(directory());
        World world = WorldCreator.ofKey(NamespacedKey.minecraft(worldName()))
            .environment(World.Environment.NORMAL)
            .seed(ReachTerrain.SEED)
            .generator(new ReachGenerator(terrain, features, edits))
            .generateStructures(false)
            .createWorld();
        if (world == null) throw new IllegalStateException("The Reach world did not load");
        rules(world, fresh);
        plugin.getLogger().info("The Wardens' Reach is loaded as " + world.getName() + (fresh ? " (newly created)" : "") + ".");
        return world;
    }

    private void rules(World world, boolean fresh) {
        // No vanilla creatures at all: the Reach's people, beasts and foes are all placed by its own encounters.
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SPAWN_WARDENS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        // No random ticks: fields, leaves and grass stay exactly as built.
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        // No day and night: the clock stands at noon and each zone shows its own hour (ReachClimate).
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setTime(6000);
        if (world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        org.bukkit.WorldBorder border = world.getWorldBorder();
        double centreX = (ReachTerrain.MIN_X + ReachTerrain.MAX_X) / 2.0, centreZ = (ReachTerrain.MIN_Z + ReachTerrain.MAX_Z) / 2.0;
        double size = Math.max(ReachTerrain.MAX_X - ReachTerrain.MIN_X, ReachTerrain.MAX_Z - ReachTerrain.MIN_Z) + 200;
        if (fresh || border.getSize() >= 59_000_000D) {
            border.setCenter(centreX, centreZ);
            border.setSize(size);
        }
    }

    public Location arrival() {
        World world = world();
        if (world == null) return null;
        ReachFeatures.Spot spot = features.arrival();
        return new Location(world, spot.x(), spot.y(), spot.z(), spot.yaw(), 0F);
    }

    // ------------------------------------------------------------------ the skyfarer's line

    private void recordFooting() {
        World world = world();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            if (!player.isOnGround() || player.getLocation().getY() < ReachTerrain.RESCUE_Y + 4) continue;
            if (!player.getLocation().clone().add(0, -0.2, 0).getBlock().getType().isSolid()) continue;
            lastFooting.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() >= ReachTerrain.RESCUE_Y || !isReach(event.getTo().getWorld())) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Location back = lastFooting.getOrDefault(player.getUniqueId(), arrival());
        if (back == null) return;
        rescued.put(player.getUniqueId(), System.currentTimeMillis());
        player.setFallDistance(0F);
        player.setVelocity(new org.bukkit.util.Vector());
        player.teleportAsync(back).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            player.setFallDistance(0F);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, true));
            Chat.notice(player, "Your skyfarer's line catches you and hauls you back to solid ground.");
        }));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL && event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        Long at = rescued.get(event.getEntity().getUniqueId());
        if (at != null && System.currentTimeMillis() - at < 5000) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFooting.remove(event.getPlayer().getUniqueId());
        rescued.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ pre-generation

    /** Walks every chunk of the map a few at a time so players never wait on generation. */
    private final class Pregen {
        final int minCX = ReachTerrain.MIN_X >> 4, maxCX = ReachTerrain.MAX_X >> 4, minCZ = ReachTerrain.MIN_Z >> 4, maxCZ = ReachTerrain.MAX_Z >> 4;
        final int total = (maxCX - minCX + 1) * (maxCZ - minCZ + 1);
        int next, inFlight, done;
        final long started = System.currentTimeMillis();
        org.bukkit.scheduler.BukkitTask task;
        final CommandSender reporter;
        long lastReport;

        Pregen(CommandSender reporter) { this.reporter = reporter; }

        void tick(World world) {
            int budget = plugin.getConfig().getInt("reach.pregen-in-flight", 24);
            while (inFlight < budget && next < total) {
                int cx = minCX + next % (maxCX - minCX + 1), cz = minCZ + next / (maxCX - minCX + 1);
                next++;
                inFlight++;
                world.getChunkAtAsync(cx, cz, true).whenComplete((chunk, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight--;
                    done++;
                    if (failure != null) plugin.getLogger().log(Level.WARNING, "Reach chunk " + chunk + " failed to generate", failure);
                }));
            }
            long now = System.currentTimeMillis();
            if (now - lastReport > 15000) {
                lastReport = now;
                String line = "Reach pre-generation: " + done + " / " + total + " chunks (" + (done * 100 / total) + "%)";
                plugin.getLogger().info(line);
                if (reporter instanceof Player p && p.isOnline()) Chat.info(p, Chat.escape(line));
            }
            if (done >= total) {
                task.cancel();
                pregen = null;
                world.save();
                String line = "Reach pre-generation finished: " + total + " chunks in " + (now - started) / 1000 + " s.";
                plugin.getLogger().info(line);
                if (reporter != null) Chat.success(reporter, Chat.escape(line));
                // Populate it now: zones, NPCs, warps and the arrival spawn.
                plugin.worldPacks().applyAll(false, reporter);
            }
        }
    }

    // ------------------------------------------------------------------ rebuilding a feature in place

    /**
     * Regenerates the columns each feature builds in into the live world, a slice per tick: the same terrain and
     * blocks a fresh chunk would get, so changes to a kit show up without regenerating the map. Plants outside the
     * build are left alone; blocks an older version built where the new one doesn't are not cleared.
     */
    private void rebuild(World world, List<ReachFeatures.Feature> chosen, CommandSender sender) {
        ReachGenerator generator = (ReachGenerator) world.getGenerator();
        if (generator == null) { Chat.error(sender, "The Reach world has no generator attached."); return; }
        java.util.ArrayDeque<long[]> chunks = new java.util.ArrayDeque<>();
        for (ReachFeatures.Feature feature : chosen) {
            ReachFeatures.Footprint f = feature.footprint();
            for (int cx = f.minX() >> 4; cx <= f.maxX() >> 4; cx++) for (int cz = f.minZ() >> 4; cz <= f.maxZ() >> 4; cz++)
                chunks.add(new long[] {cx, cz});
        }
        int total = chunks.size();
        Chat.notice(sender, "Rebuilding " + chosen.size() + " feature(s) across " + total + " chunks.");
        int lowY = 40, highY = 240;
        int[] done = {0};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long start = System.nanoTime();
            while (!chunks.isEmpty() && System.nanoTime() - start < 30_000_000L) {
                long[] next = chunks.poll();
                int cx = (int) next[0], cz = (int) next[1];
                org.bukkit.Chunk chunk = world.getChunkAt(cx, cz);
                org.bukkit.generator.ChunkGenerator.ChunkData data = Bukkit.createChunkData(world);
                generator.generateNoise(world, new java.util.Random(0), cx, cz, data);
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                    int x = (cx << 4) + lx, z = (cz << 4) + lz;
                    // Only columns the feature builds in: everything else keeps its plants and terrain.
                    boolean inside = chosen.stream().anyMatch(f -> f.footprint().contains(x, z) && features.plan(f).occupies(x, z));
                    if (!inside) continue;
                    for (int y = lowY; y < highY; y++) {
                        org.bukkit.block.data.BlockData want = data.getBlockData(lx, y, lz);
                        org.bukkit.block.Block block = chunk.getBlock(lx, y, lz);
                        if (!block.getBlockData().equals(want)) block.setBlockData(want, false);
                    }
                }
                // Hand edits go back over the rebuilt chunk.
                if (edits != null) edits.apply(chunk);
                done[0]++;
            }
            if (chunks.isEmpty()) {
                task[0].cancel();
                Chat.success(sender, "Rebuilt " + done[0] + " chunks.");
            }
        }, 1L, 1L);
    }

    // ------------------------------------------------------------------ command

    public boolean command(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "go" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (!sub.equals("go") && !sub.equals("places") && !sender.hasPermission("nerdsmp.reach.admin")) { Chat.noPermission(sender); return true; }
        switch (sub) {
            case "go" -> {
                if (!(sender instanceof Player player)) { Chat.error(sender, "Only players can travel."); return true; }
                if (!sender.hasPermission("nerdsmp.reach.visit")) { Chat.noPermission(sender); return true; }
                Location arrival = arrival();
                if (arrival == null) { Chat.error(sender, "The Reach hasn't been generated yet."); return true; }
                player.teleportAsync(arrival);
            }
            case "generate" -> {
                World world = open();
                if (pregen != null) { Chat.notice(sender, "Pre-generation is already running."); return true; }
                Pregen job = new Pregen(sender);
                pregen = job;
                job.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> job.tick(world), 1L, 1L);
                Chat.success(sender, "Generating the Wardens' Reach: " + job.total + " chunks. Progress goes to the console every 15 s.");
            }
            case "rebuild" -> {
                if (args.length < 2) { Chat.error(sender, "Use /reach rebuild <feature|all>."); return true; }
                World world = open();
                List<ReachFeatures.Feature> chosen = args[1].equalsIgnoreCase("all") ? features.features()
                    : features.feature(args[1].toLowerCase(java.util.Locale.ROOT)).map(List::of).orElse(List.of());
                if (chosen.isEmpty()) { Chat.error(sender, "No feature called " + Chat.escape(args[1]) + "."); return true; }
                rebuild(world, chosen, sender);
            }
            case "stop" -> {
                Pregen job = pregen;
                if (job == null) { Chat.notice(sender, "Nothing is generating."); return true; }
                job.task.cancel();
                pregen = null;
                Chat.success(sender, "Stopped at " + job.done + " / " + job.total + " chunks.");
            }
            case "tp" -> {
                if (!(sender instanceof Player player) || args.length < 3) { Chat.error(sender, "Use /reach tp <x> <z>."); return true; }
                World world = open();
                int x = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);
                ReachTerrain.Column column = terrain.column(x, z);
                int y = column.land() ? Math.max(column.top(), column.wet() ? column.water() : column.top()) + 1 : 140;
                player.teleportAsync(new Location(world, x + 0.5, y + 1, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch()));
            }
            case "edit" -> {
                if (editor == null) return true;
                return editor.command(sender, args);
            }
            case "flights", "fly" -> {
                if (!(sender instanceof Player player) || flights == null) return true;
                flights.openNear(player);
            }
            case "places" -> {
                if (!(sender instanceof Player player) || gameplay == null) return true;
                List<String> found = gameplay.placesFound(player);
                Chat.info(sender, "<gold>Places discovered</gold> <gray>" + found.size() + " / " + ReachGameplay.PLACES.size() + "</gray>");
                if (!found.isEmpty()) sender.sendRichMessage("<gray>" + Chat.escape(String.join(", ", found)) + "</gray>");
            }
            case "where" -> {
                if (!(sender instanceof Player player)) return true;
                Location at = player.getLocation();
                ReachTerrain.Zone zone = terrain.zoneAt(at.getX(), at.getZ());
                Chat.info(sender, "<white>" + Chat.escape(zone.title()) + "</white> <gray>" + (zone.levels().isEmpty() ? "" : "Lv " + zone.levels() + " · ")
                    + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + "</gray>");
            }
            default -> {
                World world = world();
                Chat.info(sender, "<gold>The Wardens' Reach</gold> <gray>revision " + ReachGenerator.REVISION + " · "
                    + (world == null ? "not generated" : "world " + Chat.escape(world.getName())) + (pregen != null ? " · generating " + pregen.done + "/" + pregen.total : "") + "</gray>");
                sender.sendRichMessage("<gray>/reach go · /reach generate · /reach stop · /reach tp <x> <z> · /reach where</gray>");
            }
        }
        return true;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) return sender.hasPermission("nerdsmp.reach.admin")
            ? List.of("go", "places", "flights", "edit", "status", "generate", "rebuild", "stop", "tp", "where") : List.of("go", "places", "flights");
        if (args.length == 2 && args[0].equalsIgnoreCase("rebuild") && sender.hasPermission("nerdsmp.reach.admin")) {
            List<String> ids = new java.util.ArrayList<>(features.features().stream().map(ReachFeatures.Feature::id).toList());
            ids.add("all");
            return ids;
        }
        return List.of();
    }
}
