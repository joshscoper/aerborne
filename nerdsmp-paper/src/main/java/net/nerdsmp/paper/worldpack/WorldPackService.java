package net.nerdsmp.paper.worldpack;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.digsite.DigSiteDefinition;
import net.nerdsmp.paper.npc.NpcManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import net.nerdsmp.paper.zone.ZoneDefinition;
import net.nerdsmp.paper.zone.ZoneMobTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Populates a known map with its shipped content the first time the plugin sees it, and again whenever a
 * newer pack version ships. Anything staff edited after the pack placed it is left alone: the pack only
 * replaces objects that still match what it last wrote, and never re-creates an NPC staff removed.
 */
public final class WorldPackService implements org.bukkit.event.Listener {
    public static final List<String> BUNDLED = List.of("worldpacks/highcrown.yml");
    private final NerdSmpPlugin plugin;
    private final File stateFile;
    private final List<WorldPack> packs = new ArrayList<>();
    private final Map<String, String> matched = new LinkedHashMap<>();
    private final java.util.Set<UUID> calmWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final MapFence fence;

    public WorldPackService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        stateFile = new File(plugin.getDataFolder(), "world-packs.yml");
        fence = new MapFence(plugin);
        for (String path : BUNDLED) {
            var resource = plugin.getResource(path);
            if (resource == null) continue;
            try (var in = resource) {
                packs.add(WorldPack.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (Exception failure) {
                plugin.getLogger().log(Level.SEVERE, "World pack " + path + " is invalid and was skipped", failure);
            }
        }
    }

    public List<WorldPack> packs() { return List.copyOf(packs); }

    /** Adds a pack built in code (a generated world's), before {@link #start()}. */
    public void register(WorldPack pack) {
        packs.removeIf(existing -> existing.id().equals(pack.id()));
        packs.add(pack);
    }

    /** Runs once the worlds and every content service are ready. */
    public void start() {
        if (!plugin.getConfig().getBoolean("world-packs.enabled", true)) return;
        Bukkit.getPluginManager().registerEvents(fence, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyAll(false, null), 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::respawnMissingBodies, 640L, 600L);
    }

    private boolean alwaysPlaceNpcs(World world) {
        return plugin.getConfig().getStringList("world-packs.always-place-npcs").stream().anyMatch(world.getName()::equalsIgnoreCase);
    }

    private UUID anchorId(WorldPack pack, WorldPack.Anchor anchor) {
        return UUID.nameUUIDFromBytes(("worldpack:" + pack.id() + ":" + anchor.key()).getBytes(StandardCharsets.UTF_8));
    }

    /** Puts every pack NPC back at its pack position in an always-place world, whatever staff or old files did. */
    private void restoreNpcs(WorldPack pack, World world, CommandSender reporter) {
        int restored = 0, failed = 0;
        for (WorldPack.Anchor anchor : pack.npcs().values()) {
            Location location = location(world, anchor.point());
            world.getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
            switch (plugin.npcs().ensureAnchor(anchorId(pack, anchor), anchor.npc(), location)) {
                case PLACED, MOVED -> restored++;
                case FAILED -> { failed++; plugin.getLogger().warning("NPC " + anchor.key() + " (" + anchor.npc() + ") could not be placed at " + anchor.point()); }
                case UNCHANGED -> { }
            }
        }
        if (restored == 0 && failed == 0) return;
        String summary = restored + " NPCs restored" + (failed > 0 ? ", " + failed + " could not be placed (see log)" : "");
        plugin.getLogger().info(pack.title() + " on " + world.getName() + ": " + summary);
        if (reporter != null) Chat.success(reporter, Chat.escape(pack.title()) + ": " + Chat.escape(summary));
    }

    /** Another plugin can clear NPC bodies; in always-place worlds they come back. */
    private void respawnMissingBodies() {
        int respawned = 0;
        for (WorldPack pack : packs) {
            String name = matched.get(pack.id());
            World world = name == null ? null : Bukkit.getWorld(name);
            if (world == null || !alwaysPlaceNpcs(world)) continue;
            for (WorldPack.Anchor anchor : pack.npcs().values())
                if (plugin.npcs().respawnIfMissing(anchorId(pack, anchor))) respawned++;
        }
        if (respawned > 0) plugin.getLogger().warning(respawned + " world pack NPC bodies had disappeared and were respawned;"
            + " another plugin may be removing entities.");
    }

    public void applyAll(boolean force, CommandSender reporter) {
        matched.clear();
        for (WorldPack pack : packs) for (World world : Bukkit.getWorlds()) {
            if (!matches(pack, world)) continue;
            matched.put(pack.id(), world.getName());
            if (!pack.naturalMonsters()) calmWorlds.add(world.getUID());
            ensureDifficulty(pack, world);
            if (pack.border() != null) {
                fence.guard(world, pack.border());
                ensureBorder(pack, world);
            }
            for (WorldPack.Anchor anchor : pack.npcs().values()) {
                if (anchor.route().isEmpty()) continue;
                UUID id = UUID.nameUUIDFromBytes(("worldpack:" + pack.id() + ":" + anchor.key()).getBytes(StandardCharsets.UTF_8));
                List<org.bukkit.util.Vector> route = new ArrayList<>();
                route.add(new org.bukkit.util.Vector(Math.floor(anchor.point().x()), anchor.point().y(), Math.floor(anchor.point().z())));
                anchor.route().forEach(step -> route.add(new org.bukkit.util.Vector(step.x(), step.y(), step.z())));
                plugin.npcs().patrol(id, route);
            }
            try {
                Report report = apply(pack, world, force);
                String summary = report == null ? "already applied" : report.toString();
                plugin.getLogger().info("World pack " + pack.id() + " v" + pack.version() + " on " + world.getName() + ": " + summary);
                if (reporter != null) Chat.success(reporter, Chat.escape(pack.title()) + ": " + Chat.escape(summary));
                if (alwaysPlaceNpcs(world)) restoreNpcs(pack, world, reporter);
            } catch (RuntimeException | IOException failure) {
                plugin.getLogger().log(Level.SEVERE, "World pack " + pack.id() + " could not be applied to " + world.getName(), failure);
                if (reporter != null) Chat.error(reporter, "World pack " + Chat.escape(pack.id()) + " failed: " + Chat.escape(String.valueOf(failure.getMessage())));
            }
        }
        if (reporter != null && matched.isEmpty()) Chat.notice(reporter, "No loaded world matches a shipped world pack.");
    }

    /**
     * Vanilla borders are square: cover the map's long axis with one (a visible wall and a hard stop), and let
     * {@link MapFence} hold the short axis. A border staff set themselves is left alone.
     */
    private void ensureBorder(WorldPack pack, World world) {
        WorldPack.Border border = pack.border();
        double size = Math.max(border.width(), border.depth());
        double centerX = (border.minX() + border.maxX() + 1) / 2.0, centerZ = (border.minZ() + border.maxZ() + 1) / 2.0;
        String wanted = centerX + "," + centerZ + "," + size;
        org.bukkit.WorldBorder current = world.getWorldBorder();
        String now = current.getCenter().getX() + "," + current.getCenter().getZ() + "," + current.getSize();
        if (now.equals(wanted)) return;
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        String key = "packs." + pack.id() + "." + world.getUID() + ".world-border";
        boolean untouched = current.getSize() >= 59_000_000D || now.equals(state.getString(key));
        if (!untouched) {
            plugin.getLogger().info("World " + world.getName() + " has a staff-set world border; " + pack.title() + " kept it.");
            return;
        }
        current.setCenter(centerX, centerZ);
        current.setSize(size);
        current.setWarningDistance(8);
        state.set(key, wanted);
        try { AtomicYamlFiles.save(state, stateFile); }
        catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Could not record the world pack border", failure); }
        plugin.getLogger().info("World border for " + world.getName() + " set to the edges of " + pack.title() + " (x "
            + border.minX() + ".." + border.maxX() + ", z " + border.minZ() + ".." + border.maxZ() + ").");
    }

    /** A pack map's monsters come from its zones; vanilla night spawns would overrun towns and outposts. */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onNaturalSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (event.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
            && event.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL) return;
        if (event.getEntity() instanceof org.bukkit.entity.Enemy && calmWorlds.contains(event.getLocation().getWorld().getUID()))
            event.setCancelled(true);
    }

    /**
     * Zone encounters cannot spawn on Peaceful, and a map keeps the difficulty it was saved with. Raise a
     * Peaceful map once; if staff choose Peaceful again later, that choice stands.
     */
    private void ensureDifficulty(WorldPack pack, World world) {
        if (pack.difficulty() == null || world.getDifficulty() != org.bukkit.Difficulty.PEACEFUL) return;
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        String key = "packs." + pack.id() + "." + world.getUID() + ".difficulty-raised";
        if (state.getBoolean(key)) return;
        org.bukkit.Difficulty target = org.bukkit.Difficulty.valueOf(pack.difficulty().toUpperCase(java.util.Locale.ROOT));
        world.setDifficulty(target);
        state.set(key, true);
        try { AtomicYamlFiles.save(state, stateFile); }
        catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Could not record the world pack difficulty change", failure); }
        plugin.getLogger().warning("World " + world.getName() + " was saved on Peaceful, where " + pack.title()
            + "'s zone creatures cannot spawn. Difficulty set to " + target.name().toLowerCase(java.util.Locale.ROOT) + " (use /difficulty to change it).");
    }

    boolean matches(WorldPack pack, World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return false;
        if (pack.world() != null) return world.getName().equals(pack.world());
        if (world.getSeed() != pack.seed()) return false;
        if (plugin.skyblock() != null && plugin.skyblock().isIslandWorld(world)) return false;
        for (WorldPack.Landmark landmark : pack.landmarks()) {
            world.getChunkAt(landmark.x() >> 4, landmark.z() >> 4);
            if (!world.getBlockAt(landmark.x(), landmark.y(), landmark.z()).getType().name().equals(landmark.material())) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ applying

    public record Report(int zones, int sites, int npcs, int warps, int kept, int skipped) {
        @Override public String toString() {
            return zones + " zones, " + sites + " node sites, " + npcs + " NPCs, " + warps + " warps written; "
                + kept + " staff-edited kept" + (skipped > 0 ? ", " + skipped + " skipped (see log)" : "");
        }
    }

    private Report apply(WorldPack pack, World world, boolean force) throws IOException {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        String root = "packs." + pack.id() + "." + world.getUID();
        if (!force && state.getInt(root + ".version", 0) >= pack.version()) return null;
        // Owned keys contain dots (zone.x, npc.y), so they are stored as a list rather than as YAML paths.
        Map<String, String> before = new LinkedHashMap<>();
        for (Map<?, ?> entry : state.getMapList(root + ".owned"))
            if (entry.get("key") instanceof String key && entry.get("value") instanceof String value) before.put(key, value);
        Map<String, String> after = new LinkedHashMap<>();
        int zones = 0, sites = 0, npcs = 0, warps = 0, kept = 0, skipped = 0;

        // Zones first: node sites and quests depend on them.
        for (WorldPack.Zone spec : pack.zones().values()) {
            String key = "zone." + spec.id();
            ZoneDefinition wanted;
            try { wanted = zone(spec, world); }
            catch (IllegalArgumentException invalid) { plugin.getLogger().warning("Pack zone " + spec.id() + " skipped: " + invalid.getMessage()); skipped++; continue; }
            Optional<ZoneDefinition> existing = plugin.zones().find(spec.id());
            if (existing.isPresent() && !fingerprint(existing.get()).equals(before.get(key))) {
                if (existing.get().equals(wanted)) { after.put(key, fingerprint(wanted)); continue; }
                plugin.getLogger().info("Zone " + spec.id() + " was edited by staff; the pack left it unchanged.");
                kept++;
                continue;
            }
            if (existing.isPresent() && existing.get().equals(wanted)) { after.put(key, fingerprint(wanted)); continue; }
            try {
                plugin.zones().put(wanted);
                after.put(key, fingerprint(wanted));
                zones++;
            } catch (IllegalArgumentException conflict) {
                plugin.getLogger().warning("Pack zone " + spec.id() + " skipped: " + conflict.getMessage());
                skipped++;
            }
        }

        if (plugin.digSites() != null) for (WorldPack.Site spec : pack.sites().values()) {
            String key = "site." + spec.id();
            String specPrint = spec.zone() + spec.bounds() + new java.util.TreeSet<>(spec.materials()) + spec.cooldownSeconds();
            Optional<DigSiteDefinition> existing = plugin.digSites().store().find(spec.id());
            String previous = before.get(key);
            if (existing.isPresent()) {
                String ownedPrint = specPrint + "#" + existing.get().generation();
                if (ownedPrint.equals(previous)) { after.put(key, previous); continue; }
                if (previous == null || !previous.endsWith("#" + existing.get().generation())) {
                    plugin.getLogger().info("Node site " + spec.id() + " was edited by staff; the pack left it unchanged.");
                    kept++;
                    continue;
                }
            }
            if (plugin.zones().find(spec.zone()).filter(ZoneDefinition::enabled).isEmpty()) {
                plugin.getLogger().warning("Node site " + spec.id() + " skipped: zone " + spec.zone() + " is missing or disabled.");
                skipped++;
                continue;
            }
            try {
                DigSiteDefinition site = capture(spec, world, pack.npcs().values());
                plugin.digSites().register(site);
                after.put(key, specPrint + "#" + site.generation());
                sites++;
            } catch (IOException | RuntimeException failure) {
                plugin.getLogger().warning("Node site " + spec.id() + " skipped: " + failure.getMessage());
                skipped++;
            }
        }

        for (WorldPack.Anchor anchor : pack.npcs().values()) {
            String key = "npc." + anchor.key();
            UUID id = UUID.nameUUIDFromBytes(("worldpack:" + pack.id() + ":" + anchor.key()).getBytes(StandardCharsets.UTF_8));
            String print = anchor.npc() + "@" + anchor.point() + "/" + anchor.point().yaw();
            if (before.containsKey(key) && !plugin.npcs().hasAnchor(id)) { after.put(key, before.get(key)); kept++; continue; }
            // An anchor tied to another world (a copied npc-instances.yml, a re-created world) is moved here below.
            if (print.equals(before.get(key)) && plugin.npcs().hasAnchorIn(id, world)) { after.put(key, print); continue; }
            Location location = location(world, anchor.point());
            world.getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
            NpcManager.AnchorResult result = plugin.npcs().ensureAnchor(id, anchor.npc(), location);
            if (result == NpcManager.AnchorResult.FAILED) { plugin.getLogger().warning("NPC " + anchor.key() + " (" + anchor.npc() + ") could not be placed at " + anchor.point()); skipped++; continue; }
            after.put(key, print);
            if (result != NpcManager.AnchorResult.UNCHANGED) npcs++;
        }
        // NPCs a newer pack no longer lists are retired.
        for (String key : before.keySet()) if (key.startsWith("npc.") && !after.containsKey(key))
            plugin.npcs().removeAnchor(UUID.nameUUIDFromBytes(("worldpack:" + pack.id() + ":" + key.substring(4)).getBytes(StandardCharsets.UTF_8)));

        for (Map.Entry<String, WorldPack.Point> entry : pack.warps().entrySet()) {
            String key = "warp." + entry.getKey();
            Location location = location(world, entry.getValue());
            String current = plugin.travel().warpDefinition(entry.getKey()).map(this::print).orElse(null);
            if (current != null && !current.equals(before.get(key))) { kept++; continue; }
            plugin.travel().saveWarp(entry.getKey(), location);
            after.put(key, plugin.travel().warpDefinition(entry.getKey()).map(this::print).orElse(""));
            if (!print(location).equals(current)) warps++;
        }
        // Warps a newer pack no longer lists are removed unless staff moved them.
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (!entry.getKey().startsWith("warp.") || after.containsKey(entry.getKey())) continue;
            String name = entry.getKey().substring(5);
            if (plugin.travel().warpDefinition(name).map(this::print).filter(entry.getValue()::equals).isPresent()) plugin.travel().removeWarp(name);
        }
        if (pack.spawn() != null) {
            Location location = location(world, pack.spawn());
            String current = plugin.travel().spawnDefinition().map(this::print).orElse(null);
            if (current == null || current.equals(before.get("spawn"))) {
                plugin.travel().saveSpawn(location);
                after.put("spawn", plugin.travel().spawnDefinition().map(this::print).orElse(""));
            } else kept++;
        }

        state.set(root + ".version", pack.version());
        List<Map<String, String>> owned = new ArrayList<>();
        after.forEach((key, value) -> owned.add(Map.of("key", key, "value", value)));
        state.set(root + ".owned", owned);
        AtomicYamlFiles.save(state, stateFile);
        return new Report(zones, sites, npcs, warps, kept, skipped);
    }

    private ZoneDefinition zone(WorldPack.Zone spec, World world) {
        WorldPack.Box b = spec.bounds();
        List<ZoneDefinition.MobEntry> mobs = new ArrayList<>();
        for (WorldPack.MobEntry mob : spec.mobs()) {
            if (!ZoneMobTypes.validId(mob.id())) throw new IllegalArgumentException("unknown mob " + mob.id());
            if (mob.id().startsWith("custom:") && !plugin.mobs().ids().contains(mob.id().substring(7)))
                throw new IllegalArgumentException("custom mob " + mob.id() + " is not loaded");
            mobs.add(new ZoneDefinition.MobEntry(mob.id(), mob.weight(), mob.level()));
        }
        List<ZoneDefinition.SpawnPoint> points = spec.points().stream()
            .map(point -> new ZoneDefinition.SpawnPoint(point.x(), point.y(), point.z(), point.yaw())).toList();
        return new ZoneDefinition(spec.id(), spec.title(), world.getUID(),
            new ZoneDefinition.Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()),
            true, spec.safe(), spec.cap(), spec.respawnSeconds(), spec.radius(), spec.safe() ? List.of() : points, spec.safe() ? List.of() : mobs);
    }

    private DigSiteDefinition capture(WorldPack.Site spec, World world, java.util.Collection<WorldPack.Anchor> anchors) {
        WorldPack.Box b = spec.bounds();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) world.getChunkAt(cx, cz);
        List<DigSiteDefinition.Node> nodes = new ArrayList<>();
        for (int x = b.minX(); x <= b.maxX(); x++) for (int y = b.minY(); y <= b.maxY(); y++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!spec.materials().contains(type.name()) || !exposed(block) || underNpc(x, y, z, anchors)) continue;
            nodes.add(new DigSiteDefinition.Node(x, y, z, type.name(), block.getBlockData().getAsString()));
            if (nodes.size() >= DigSiteDefinition.MAX_NODES) break;
        }
        if (nodes.isEmpty()) throw new IllegalArgumentException("no " + spec.materials() + " blocks were found in its bounds");
        return new DigSiteDefinition(spec.id(), spec.zone(), world.getUID(), UUID.randomUUID(), true, spec.cooldownSeconds(), nodes);
    }

    /** Never turn the ground an NPC stands on into a node that can be dug away. */
    private static boolean underNpc(int x, int y, int z, java.util.Collection<WorldPack.Anchor> anchors) {
        for (WorldPack.Anchor anchor : anchors) {
            WorldPack.Point point = anchor.point();
            if (Math.abs(x + 0.5 - point.x()) <= 2.5 && Math.abs(z + 0.5 - point.z()) <= 2.5 && y < point.y() && y >= point.y() - 4) return true;
        }
        return false;
    }

    /** Only blocks a player can reach become nodes: buried blocks would never be dug. */
    private static boolean exposed(Block block) {
        for (org.bukkit.block.BlockFace face : List.of(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN, org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST))
            if (block.getRelative(face).isPassable()) return true;
        return false;
    }

    private static Location location(World world, WorldPack.Point point) {
        return new Location(world, point.x(), point.y(), point.z(), point.yaw(), 0F);
    }

    private static String fingerprint(ZoneDefinition zone) {
        return Integer.toHexString(zone.toString().hashCode());
    }

    private String print(net.nerdsmp.paper.travel.TravelManager.WarpDefinition warp) {
        return warp.world() + "@" + Math.round(warp.x() * 100) + "," + Math.round(warp.y() * 100) + "," + Math.round(warp.z() * 100);
    }

    private String print(Location location) {
        return location.getWorld().getUID() + "@" + Math.round(location.getX() * 100) + "," + Math.round(location.getY() * 100) + "," + Math.round(location.getZ() * 100);
    }

    // ------------------------------------------------------------------ command

    public boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nerdsmp.zone.admin")) { Chat.noPermission(sender); return true; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("apply")) {
            applyAll(args.length >= 2 && args[1].equalsIgnoreCase("force"), sender);
            return true;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        Chat.info(sender, "<gold>World packs</gold>");
        for (WorldPack pack : packs) {
            String world = Bukkit.getWorlds().stream().filter(candidate -> matches(pack, candidate)).map(World::getName).findFirst().orElse(null);
            int applied = world == null ? 0 : state.getInt("packs." + pack.id() + "." + Bukkit.getWorld(world).getUID() + ".version", 0);
            sender.sendRichMessage("<white>" + Chat.escape(pack.title()) + "</white> <gray>v" + pack.version() + " · "
                + (world == null ? "no matching world loaded" : "world " + Chat.escape(world) + ", applied v" + applied) + " · "
                + pack.zones().size() + " zones, " + pack.npcs().size() + " NPCs, " + pack.sites().size() + " node sites, " + pack.warps().size() + " warps</gray>");
        }
        sender.sendRichMessage("<gray>/worldpack apply [force] re-applies; staff-edited zones, sites, warps and removed NPCs are kept.</gray>");
        return true;
    }

    public List<String> complete(String[] args) {
        if (args.length == 1) return List.of("status", "apply");
        if (args.length == 2 && args[0].equalsIgnoreCase("apply")) return List.of("force");
        return List.of();
    }
}
