package net.nerdsmp.paper.reach;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.SkillLamps;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The Reach's open-world play on top of zones, NPCs and quests: discovering named places, tiered world chests that
 * refill per player (harder to reach, better loot), and encounter spots where rares and open-world dungeon bosses
 * appear on their own timers. Positions all come from the builds' named spots.
 */
public final class ReachGameplay implements Listener {
    /** A named place: discovered once per player by walking within {@code radius} of it. */
    record Place(String spot, String title, double radius, String arrivalLine) {
        Place(String spot, String title, double radius) { this(spot, title, radius, "Place discovered"); }
    }

    /** A creature that appears at a spot on its own timer: rares and dungeon bosses. */
    record Encounter(String mob, String spot, int respawnSeconds, boolean nightOnly, String notice) { }

    static final List<Place> PLACES = List.of(
        new Place("highcrown.arrival_1", "The Sky Docks", 22, "Wren flies the red and white pennants."),
        new Place("highcrown.plaza", "Kingsgate Plaza", 24),
        new Place("highcrown.keep_hall", "Highcrown Keep", 20),
        new Place("highcrown.fane", "The Lantern Fane", 14),
        new Place("highcrown.market", "The Market Ward", 22),
        new Place("highcrown.exchequer", "The Highcrown Exchequer", 12),
        new Place("highcrown.tethered_gull", "The Tethered Gull", 10),
        new Place("highcrown.crafter_0", "Crafters' Row", 18),
        new Place("highcrown.kingsbridge", "The Kingsbridge", 18, "Six Wardens stand watch over the grain road."),
        new Place("goldenreach.warden_post", "The Warden's Post", 12),
        new Place("goldenreach.kingsgate_fields", "Kingsgate Fields", 30),
        new Place("goldenreach.green", "Millbrook", 30, "Garth’s hammer rings across the green."),
        new Place("goldenreach.windmill_ridge", "Windmill Ridge", 30),
        new Place("goldenreach.thistle_yard", "Thistle Farm", 20),
        new Place("goldenreach.orchard", "Brindle Orchards", 30),
        new Place("goldenreach.tollkeeper_camp", "The Tollroad", 25),
        new Place("goldenreach.granary", "The Black Granary", 25, "The receiving beam still bears Millbrook’s name."),
        new Place("goldenreach.cellars_entrance", "The Old Granary Cellars", 6, "Brother Tobin remembers when these stairs were a refuge."),
        new Place("goldenreach.hollow_road", "The Hollow Road", 25),
        new Place("huntsmans_barrow.place", "The Huntsman's Barrow", 26, "Someone still lights the candles nearest the grate."),
        new Place("stone_rings.place", "The Stone Rings", 16),
        new Place("stonewatch.place", "Stonewatch", 14),
        new Place("heart_tree.place", "The Heart Tree", 26, "Black thorn climbs the living trunk."),
        new Place("great_aqueduct.place", "The Great Aqueduct", 30, "Follow the water east toward the Ironspine."),
        new Place("headframe.place", "The Deep Foreman's Headframe", 22, "The night shift’s lamps are still checked out."),
        new Place("sunscar_arch.place", "The Sunscar Arch", 30),
        new Place("ember_gate.place", "The Ember Gate", 20),
        new Place("seal_spire.place", "The Seal Spire", 22, "Three settings on the archive door. One is empty.")
    );

    static final List<Encounter> ENCOUNTERS = List.of(
        new Encounter("toll_captain_brask", "goldenreach.toll_captain", 900, false, null),
        new Encounter("scarecrow_king", "goldenreach.scarecrow_king", 720, false, "Straw rustles on the threshing floor. The Scarecrow King stands up."),
        new Encounter("old_grizzlehide", "goldenreach.rare_grizzlehide", 1800, false, "A huge boar crashes through the orchard: Old Grizzlehide."),
        new Encounter("hollow_jack", "goldenreach.windmill_ridge", 2400, true, "A lantern lights itself on Windmill Ridge. Hollow Jack walks."),
        new Encounter("gilt_tooth", "goldenreach.kingsgate_fields", 1500, false, "Something glints in the fields: Gilt-Tooth, queen of the gnawers.")
    );

    private static final int[] CHEST_COOLDOWN_MINUTES = {10, 20, 45, 120};

    private final NerdSmpPlugin plugin;
    private final ReachService reach;
    private final File file;
    private final YamlConfiguration state;
    private final Map<String, ReachFeatures.Spot> spots;
    private final Map<String, Integer> chestTiers = new HashMap<>();
    /** Each expedition's door in the Reach, by expedition id: the threshold spot before it. */
    private final Map<String, ReachFeatures.Spot> doors = new HashMap<>();
    private final Map<UUID, Long> doorOpened = new HashMap<>();
    private final Map<String, UUID> alive = new HashMap<>();
    private final Map<String, Long> nextSpawn = new HashMap<>();
    private boolean dirty;

    ReachGameplay(NerdSmpPlugin plugin, ReachService reach) {
        this.plugin = plugin;
        this.reach = reach;
        this.file = new File(plugin.getDataFolder(), "reach-progress.yml");
        this.state = YamlConfiguration.loadConfiguration(file);
        this.spots = reach.features().spots();
        spots.forEach((id, spot) -> {
            String local = id.substring(id.indexOf('.') + 1);
            if (local.startsWith("chest_tier")) chestTiers.put(key(spot), Character.getNumericValue(local.charAt("chest_tier".length())));
            if (local.startsWith("door_")) doors.put(local.substring("door_".length()), spot);
        });
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::discover, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::encounters, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::glimmer, 80L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 1200L, 1200L);
    }

    void stop() { save(); }

    private static String key(ReachFeatures.Spot s) { return (int) Math.floor(s.x()) + "," + (int) Math.floor(s.y()) + "," + (int) Math.floor(s.z()); }

    private World world() { return reach.world(); }

    // ------------------------------------------------------------------ places

    private void discover() {
        World world = world();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            Place nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (Place place : PLACES) {
                ReachFeatures.Spot s = spots.get(place.spot());
                if (s == null) continue;
                double dx = at.getX() - s.x(), dz = at.getZ() - s.z(), dy = at.getY() - s.y();
                if (dx * dx + dz * dz > place.radius() * place.radius() || Math.abs(dy) > 20) continue;
                String path = "players." + player.getUniqueId() + ".places";
                List<String> found = new ArrayList<>(state.getStringList(path));
                if (found.contains(place.spot())) continue;
                found.add(place.spot());
                state.set(path, found);
                dirty = true;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearestDistance) {
                    nearest = place;
                    nearestDistance = distance;
                }
                credit(player, 10, "reach.discovery", place.title(), "reach:place:" + place.spot());
            }
            // Keep every discovery and reward. Show one arrival when neighboring areas overlap.
            if (nearest != null) {
                player.showTitle(Title.title(Component.text(nearest.title(), NamedTextColor.GOLD), Component.text(nearest.arrivalLine(), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(850))));
                player.playSound(at, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, org.bukkit.SoundCategory.AMBIENT, 0.32F, 0.86F);
            }
        }
    }

    public int discovered(Player player) { return state.getStringList("players." + player.getUniqueId() + ".places").size(); }

    // ------------------------------------------------------------------ no vanilla creatures

    /** Every way the game would bring a creature into the Reach on its own; what the plugin spawns is CUSTOM. */
    private static final Set<CreatureSpawnEvent.SpawnReason> WILD = Set.of(CreatureSpawnEvent.SpawnReason.NATURAL,
        CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER,
        CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
        CreatureSpawnEvent.SpawnReason.JOCKEY, CreatureSpawnEvent.SpawnReason.MOUNT, CreatureSpawnEvent.SpawnReason.TRAP,
        CreatureSpawnEvent.SpawnReason.LIGHTNING, CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE, CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
        CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK, CreatureSpawnEvent.SpawnReason.ENDER_PEARL, CreatureSpawnEvent.SpawnReason.NETHER_PORTAL);

    /** The game rules stop natural spawning; this stops the rest (spawners, traps, lightning, raids) in the Reach. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWildSpawn(CreatureSpawnEvent event) {
        if (WILD.contains(event.getSpawnReason()) && reach.isReach(event.getLocation().getWorld())) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ expedition doors

    /**
     * Stepping into an expedition's starlit door opens that expedition's board. The player is set back on the
     * threshold, facing the door, so closing the board leaves them standing before it rather than inside it.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDoor(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        if (!reach.isReach(to.getWorld())) return;
        Block feet = to.getBlock();
        if (feet.getType() != Material.END_GATEWAY && feet.getRelative(org.bukkit.block.BlockFace.UP).getType() != Material.END_GATEWAY) return;
        String expedition = null;
        ReachFeatures.Spot threshold = null;
        double nearest = 16 * 16;
        for (Map.Entry<String, ReachFeatures.Spot> door : doors.entrySet()) {
            ReachFeatures.Spot s = door.getValue();
            double dx = to.getX() - s.x(), dz = to.getZ() - s.z();
            if (dx * dx + dz * dz >= nearest || Math.abs(to.getY() - s.y()) > 24) continue;
            nearest = dx * dx + dz * dz;
            expedition = door.getKey();
            threshold = s;
        }
        if (expedition == null) return;
        Player player = event.getPlayer();
        event.setTo(new Location(to.getWorld(), threshold.x(), threshold.y(), threshold.z(), threshold.yaw(), 0F));
        long now = System.currentTimeMillis();
        Long last = doorOpened.get(player.getUniqueId());
        if (last != null && now - last < 1500) return;
        doorOpened.put(player.getUniqueId(), now);
        player.playSound(event.getTo(), org.bukkit.Sound.BLOCK_END_PORTAL_FRAME_FILL, org.bukkit.SoundCategory.BLOCKS, 0.7F, 0.8F);
        String id = expedition;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.performCommand("expedition info " + id);
        });
    }

    // ------------------------------------------------------------------ world chests

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (block.getType() != Material.CHEST || !reach.isReach(block.getWorld())) return;
        Integer tier = chestTiers.get(block.getX() + "," + block.getY() + "," + block.getZ());
        if (tier == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String path = "players." + player.getUniqueId() + ".chests." + block.getX() + "_" + block.getY() + "_" + block.getZ();
        long now = System.currentTimeMillis(), ready = state.getLong(path, 0L);
        if (now < ready) {
            long minutes = Math.max(1, (ready - now + 59_999) / 60_000);
            Chat.notice(player, "You've emptied this chest. It refills for you in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
            return;
        }
        boolean first = ready == 0L;
        state.set(path, now + CHEST_COOLDOWN_MINUTES[Math.max(0, Math.min(3, tier - 1))] * 60_000L);
        dirty = true;
        Inventory loot = Bukkit.createInventory(null, 27, Component.text(tierName(tier) + " Chest"));
        roll(tier).forEach(stack -> loot.addItem(stack));
        player.openInventory(loot);
        player.playSound(block.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.8F, 1F);
        long marks = switch (tier) { case 1 -> 6 + random(7); case 2 -> 15 + random(11); case 3 -> 40 + random(31); default -> 90 + random(51); };
        if (first) marks += 10L * tier;
        credit(player, marks, "reach.chest", tierName(tier) + " chest", "reach:chest:" + player.getUniqueId() + ":" + path.hashCode() + ":" + now);
        if (first) Chat.success(player, "First find! " + tierName(tier) + " chest, +" + Chat.marks(marks) + ".");
    }

    private static String tierName(int tier) { return switch (tier) { case 1 -> "Tier I"; case 2 -> "Tier II"; case 3 -> "Tier III"; default -> "Tier IV"; }; }

    private static int random(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    private static boolean chance(double p) { return ThreadLocalRandom.current().nextDouble() < p; }

    private List<ItemStack> roll(int tier) {
        List<ItemStack> out = new ArrayList<>();
        switch (tier) {
            case 1 -> {
                out.add(new ItemStack(chance(0.5) ? Material.BREAD : Material.COOKED_BEEF, 2 + random(3)));
                if (chance(0.4)) out.add(new ItemStack(Material.ARROW, 4 + random(5)));
                if (chance(0.05)) lamp(out, SkillLamps.Grade.PLAIN);
            }
            case 2 -> {
                out.add(new ItemStack(Material.GOLDEN_CARROT, 2 + random(3)));
                if (chance(0.5)) out.add(new ItemStack(Material.IRON_INGOT, 1 + random(3)));
                if (chance(0.3)) out.add(new ItemStack(Material.HONEY_BOTTLE, 1 + random(2)));
                if (chance(0.2)) lamp(out, SkillLamps.Grade.PLAIN);
            }
            case 3 -> {
                if (chance(0.5)) out.add(new ItemStack(Material.GOLDEN_APPLE, 1));
                if (chance(0.25)) out.add(new ItemStack(Material.DIAMOND, 1));
                lamp(out, chance(0.3) ? SkillLamps.Grade.GREATER : SkillLamps.Grade.PLAIN);
            }
            default -> {
                lamp(out, chance(0.15) ? SkillLamps.Grade.GRAND : SkillLamps.Grade.GREATER);
                out.add(new ItemStack(Material.GOLDEN_APPLE, 1 + random(2)));
                if (chance(0.05)) out.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            }
        }
        return out;
    }

    private void lamp(List<ItemStack> out, SkillLamps.Grade grade) {
        if (plugin.skillLamps() != null) out.add(plugin.skillLamps().bottle(grade, 1));
    }

    /** A sparkle over chests this player can open, so they're seen from a little way off. */
    private void glimmer() {
        World world = world();
        if (world == null) return;
        long now = System.currentTimeMillis();
        for (Player player : world.getPlayers()) for (Map.Entry<String, Integer> chest : chestTiers.entrySet()) {
            String[] p = chest.getKey().split(",");
            double x = Integer.parseInt(p[0]) + 0.5, y = Integer.parseInt(p[1]) + 1.1, z = Integer.parseInt(p[2]) + 0.5;
            if (player.getLocation().distanceSquared(new Location(world, x, y, z)) > 28 * 28) continue;
            long ready = state.getLong("players." + player.getUniqueId() + ".chests." + p[0] + "_" + p[1] + "_" + p[2], 0L);
            if (now < ready) continue;
            player.spawnParticle(chest.getValue() >= 3 ? Particle.END_ROD : Particle.HAPPY_VILLAGER, x, y, z, 3, 0.25, 0.2, 0.25, 0.01);
        }
    }

    // ------------------------------------------------------------------ encounters

    private void encounters() {
        World world = world();
        if (world == null) return;
        long now = System.currentTimeMillis();
        for (Encounter e : ENCOUNTERS) {
            // A night-only creature keeps the hour of its own place: the Reach has no night of its own.
            boolean night = reach.climate() != null && reach.climate().atSpot(e.spot()).night();
            ReachFeatures.Spot s = spots.get(e.spot());
            if (s == null || plugin.mobs() == null || !plugin.mobs().ids().contains(e.mob())) continue;
            Location home = new Location(world, s.x(), s.y(), s.z(), s.yaw(), 0F);
            UUID id = alive.get(e.mob());
            if (id != null) {
                var entity = Bukkit.getEntity(id);
                if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                    // Leash: a rare dragged too far walks home.
                    if (living.getLocation().getWorld() == world && living.getLocation().distanceSquared(home) > 40 * 40) living.teleport(home);
                    if (e.nightOnly() && !night && !(living instanceof org.bukkit.entity.Mob mob && mob.getTarget() != null)) { living.remove(); alive.remove(e.mob()); }
                    continue;
                }
                alive.remove(e.mob());
                if (entity == null && !nextSpawn.containsKey(e.mob())) nextSpawn.put(e.mob(), now);
            }
            if (now < nextSpawn.getOrDefault(e.mob(), 0L) || (e.nightOnly() && !night)) continue;
            if (!world.isChunkLoaded(home.getBlockX() >> 4, home.getBlockZ() >> 4)) continue;
            List<Player> near = world.getPlayers().stream().filter(p -> p.getLocation().distanceSquared(home) < 48 * 48).toList();
            if (near.isEmpty()) continue;
            try {
                LivingEntity spawned = plugin.mobs().spawn(e.mob(), home);
                spawned.setRemoveWhenFarAway(false);
                alive.put(e.mob(), spawned.getUniqueId());
                nextSpawn.remove(e.mob());
                if (e.notice() != null) near.forEach(p -> Chat.notice(p, e.notice()));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Reach encounter " + e.mob() + " could not spawn", failure);
                nextSpawn.put(e.mob(), now + 60_000L);
            }
        }
    }

    /** An encounter creature never waits in an unloaded chunk: it goes, and comes back when someone is near. */
    @EventHandler
    public void onUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        if (!reach.isReach(event.getWorld())) return;
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) for (Encounter e : ENCOUNTERS) {
            if (!entity.getUniqueId().equals(alive.get(e.mob()))) continue;
            entity.remove();
            alive.remove(e.mob());
            nextSpawn.putIfAbsent(e.mob(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        for (Encounter e : ENCOUNTERS) {
            if (!event.getEntity().getUniqueId().equals(alive.get(e.mob()))) continue;
            alive.remove(e.mob());
            nextSpawn.put(e.mob(), System.currentTimeMillis() + e.respawnSeconds() * 1000L);
        }
    }

    // ------------------------------------------------------------------ shared

    private void credit(Player player, long marks, String type, String what, String operation) {
        if (marks <= 0 || plugin.economy() == null) return;
        java.util.Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) return;
        plugin.economy().credit(account.get(), marks, new EconomyService.TransactionReason(type, what), operation);
        player.sendRichMessage(" <gold>+" + Chat.marks(marks) + "</gold>");
    }

    private void save() {
        if (!dirty) return;
        dirty = false;
        try { AtomicYamlFiles.save(state, file); }
        catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Could not save Reach progress", failure); }
    }

    /** Places this player has found, by title, for /reach places. */
    public List<String> placesFound(Player player) {
        List<String> found = state.getStringList("players." + player.getUniqueId() + ".places");
        List<String> titles = new ArrayList<>();
        for (Place place : PLACES) if (found.contains(place.spot())) titles.add(place.title());
        return titles;
    }

    static Set<String> encounterMobs() {
        Map<String, Boolean> ids = new LinkedHashMap<>();
        for (Encounter e : ENCOUNTERS) ids.put(e.mob(), true);
        return ids.keySet();
    }
}
