package net.nerdsmp.paper.reach;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Flight points across the Reach. Each has a roost: a perch post with a bell. A traveller who comes within reach of
 * a roost learns it; ringing a roost's bell opens the list of every flight point, the known ones ready to fly to.
 * Choosing one puts the traveller on a harnessed sky beast that rises from the roost, crosses the country high over
 * it on a smooth arc and sets down at the other roost.
 *
 * <p>A flight is safe whatever happens on the way. The rider can't fall, be hurt or get off. The beast is never saved
 * with the world, so a crash can't leave one stranded. Every flight is written down as it leaves: a rider who quits,
 * is kicked or is on board when the server stops is landed at the destination, and one whose flight was cut off by
 * a crash is set down there when they next join. Death, a change of world, an outside teleport or a lost mount end the
 * flight where it is without stranding anyone in the air.
 */
public final class ReachFlights implements Listener {
    /** A flight point: its id, the name travellers see, and the spot (or spots, first found wins) where its roost is. */
    record Point(String id, String name, String spots) { }

    static final List<Point> POINTS = List.of(
        new Point("highcrown", "Highcrown", "highcrown.npc_kestrel_way|highcrown.plaza"),
        new Point("millbrook", "Millbrook", "goldenreach.flight"),
        new Point("barrow", "The Huntsman's Barrow", "huntsmans_barrow.flight"),
        new Point("heart_tree", "The Heart Tree", "heart_tree.flight"),
        new Point("aqueduct", "The Great Aqueduct", "great_aqueduct.flight"),
        new Point("headframe", "The Headframe", "headframe.flight"),
        new Point("sunscar", "The Sunscar Arch", "sunscar_arch.flight"),
        new Point("ember_gate", "The Ember Gate", "ember_gate.flight"),
        new Point("wardens_bridge", "The Wardens' Bridge", "wardens_bridge.flight"),
        new Point("archive_isle", "The Archive Isle", "wardens_bridge.isle_flight"));

    /** Blocks a second: fast enough to cross the Reach in about a minute, slow enough to see it. */
    private static final double SPEED = 0.95;
    private static final int LEARN_RADIUS = 10, RING_RADIUS = 7;

    private final NerdSmpPlugin plugin;
    private final ReachService reach;
    private final File file;
    private final YamlConfiguration state;
    private final Map<String, ReachFeatures.Spot> roosts = new HashMap<>();
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final NamespacedKey mountKey;

    ReachFlights(NerdSmpPlugin plugin, ReachService reach) {
        this.plugin = plugin;
        this.reach = reach;
        this.file = new File(plugin.getDataFolder(), "reach-flights.yml");
        this.state = YamlConfiguration.loadConfiguration(file);
        this.mountKey = new NamespacedKey(plugin, "reach_flight_mount");
        Map<String, ReachFeatures.Spot> spots = reach.features().spots();
        for (Point p : POINTS) for (String choice : p.spots().split("\\|")) {
            ReachFeatures.Spot s = spots.get(choice);
            if (s != null) { roosts.put(p.id(), s); break; }
        }
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::learn, 80L, 40L);
    }

    // ------------------------------------------------------------------ learning and the list

    private void learn() {
        World world = reach.world();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            Point near = nearest(player.getLocation(), LEARN_RADIUS);
            if (near == null || knows(player, near)) continue;
            List<String> known = new ArrayList<>(state.getStringList("known." + player.getUniqueId()));
            known.add(near.id());
            state.set("known." + player.getUniqueId(), known);
            save();
            player.sendActionBar(Component.text("Flight point learned: " + near.name(), NamedTextColor.AQUA));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6F, 1.4F);
        }
    }

    private boolean knows(Player player, Point point) {
        return state.getStringList("known." + player.getUniqueId()).contains(point.id());
    }

    private Point nearest(Location at, double radius) {
        Point best = null;
        double bestD = radius * radius;
        for (Point p : POINTS) {
            ReachFeatures.Spot s = roosts.get(p.id());
            if (s == null) continue;
            double dx = at.getX() - s.x(), dz = at.getZ() - s.z(), dy = at.getY() - s.y();
            double d = dx * dx + dz * dz;
            if (d < bestD && Math.abs(dy) < 12) { bestD = d; best = p; }
        }
        return best;
    }

    /** Ringing a roost's bell (or touching Highcrown's waystone) opens the list. */
    @EventHandler(priority = EventPriority.LOW)
    public void onRing(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || !reach.isReach(block.getWorld())) return;
        if (block.getType() != Material.BELL && block.getType() != Material.LODESTONE) return;
        Point here = nearest(block.getLocation().add(0.5, 0, 0.5), RING_RADIUS);
        if (here == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!knows(player, here)) {
            List<String> known = new ArrayList<>(state.getStringList("known." + player.getUniqueId()));
            known.add(here.id());
            state.set("known." + player.getUniqueId(), known);
            save();
        }
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.8F, 1.1F);
        open(player, here);
    }

    /** Opens the list at a flight point, or tells the player to find a roost. */
    void openNear(Player player) {
        Point here = nearest(player.getLocation(), RING_RADIUS + 3);
        if (here == null) { Chat.notice(player, "Find a flight point's roost and ring its bell."); return; }
        open(player, here);
    }

    private final class Menu implements InventoryHolder {
        final Point from;
        final Map<Integer, Point> slots = new HashMap<>();
        Inventory inventory;
        Menu(Point from) { this.from = from; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private void open(Player player, Point from) {
        Menu menu = new Menu(from);
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text("Flight from " + from.name()));
        ReachFeatures.Spot origin = roosts.get(from.id());
        int slot = 10;
        for (Point p : POINTS) {
            ReachFeatures.Spot s = roosts.get(p.id());
            if (s == null) continue;
            if (slot == 17) slot = 19;
            boolean here = p.equals(from), known = knows(player, p);
            ItemStack icon = new ItemStack(here ? Material.COMPASS : known ? Material.FEATHER : Material.GRAY_DYE);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(known || here ? p.name() : "Undiscovered", here ? NamedTextColor.GOLD : known ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            ReachTerrain.Zone zone = reach.terrain().zoneAt(s.x(), s.z());
            if (known || here) lore.add(Component.text(zone.title(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (here) lore.add(Component.text("You are here", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            else if (known) {
                int distance = (int) Math.round(Math.hypot(s.x() - origin.x(), s.z() - origin.z()));
                int seconds = (int) Math.round(distance * 1.25 / SPEED / 20);
                lore.add(Component.text(distance + " blocks · about " + Math.max(10, seconds) + "s in the air", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to fly", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else lore.add(Component.text("Find its roost to fly there", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            menu.inventory.setItem(slot, icon);
            menu.slots.put(slot, p);
            slot++;
        }
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory) return;
        Point to = menu.slots.get(event.getSlot());
        if (to == null || to.equals(menu.from) || !knows(player, to)) return;
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> fly(player, menu.from, to));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ flying

    private final class Flight {
        final Player player;
        final Point to;
        final Location dest;
        final double[][] path;
        final double[] along;
        final Entity mount;
        final boolean wasInvulnerable;
        double travelled;
        boolean ending, moving;
        BukkitTask task;

        Flight(Player player, Point to, Location dest, double[][] path, double[] along, Entity mount, boolean wasInvulnerable) {
            this.player = player; this.to = to; this.dest = dest; this.path = path; this.along = along; this.mount = mount;
            this.wasInvulnerable = wasInvulnerable;
        }
    }

    void fly(Player player, Point from, Point to) {
        if (!player.isOnline() || flights.containsKey(player.getUniqueId())) return;
        World world = reach.world();
        ReachFeatures.Spot a = roosts.get(from.id()), b = roosts.get(to.id());
        if (world == null || a == null || b == null || !player.getWorld().equals(world)) return;
        if (player.isInsideVehicle()) player.leaveVehicle();
        Location start = player.getLocation().clone();
        Location dest = new Location(world, b.x(), b.y(), b.z(), b.yaw(), 0F);
        double[][] path = arc(start.getX(), start.getY(), start.getZ(), dest.getX(), dest.getY(), dest.getZ());
        double[] along = new double[path.length];
        for (int i = 1; i < path.length; i++)
            along[i] = along[i - 1] + Math.sqrt(Math.pow(path[i][0] - path[i - 1][0], 2) + Math.pow(path[i][1] - path[i - 1][1], 2) + Math.pow(path[i][2] - path[i - 1][2], 2));
        // Written down before leaving the ground: a crash mid-flight still lands the rider where they were going.
        state.set("pending." + player.getUniqueId(), List.of(world.getName(), dest.getX(), dest.getY(), dest.getZ(), (double) dest.getYaw()));
        save();
        Entity mount = world.spawn(start, HappyGhast.class, CreatureSpawnEvent.SpawnReason.CUSTOM, ghast -> {
            ghast.setAI(false);
            ghast.setGravity(false);
            ghast.setInvulnerable(true);
            ghast.setSilent(true);
            ghast.setPersistent(false);
            ghast.setRemoveWhenFarAway(false);
            ghast.getPersistentDataContainer().set(mountKey, PersistentDataType.BYTE, (byte) 1);
            Material harness = Material.matchMaterial("LIGHT_BLUE_HARNESS");
            if (harness != null) ghast.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(harness));
        });
        Flight flight = new Flight(player, to, dest, path, along, mount, player.isInvulnerable());
        flights.put(player.getUniqueId(), flight);
        player.setInvulnerable(true);
        player.setFallDistance(0);
        mount.addPassenger(player);
        world.playSound(start, Sound.ENTITY_HAPPY_GHAST_HARNESS_GOGGLES_DOWN, SoundCategory.NEUTRAL, 1F, 1F);
        player.showTitle(Title.title(Component.empty(), Component.text("Flying to " + to.name(), NamedTextColor.AQUA),
            Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(600))));
        flight.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(flight), 1L, 1L);
    }

    /**
     * The way there: a smooth rise from the roost to a cruising height clear of every hill on the way, the long
     * crossing, and a smooth descent onto the far roost. Sampled finely so the beast moves at an even speed.
     */
    private double[][] arc(double x0, double y0, double z0, double x1, double y1, double z1) {
        double cruise = Math.max(y0, y1) + 28;
        for (int i = 0; i <= 64; i++) {
            double t = i / 64.0;
            ReachTerrain.Column c = reach.terrain().column((int) Math.floor(x0 + (x1 - x0) * t), (int) Math.floor(z0 + (z1 - z0) * t));
            if (c.land()) cruise = Math.max(cruise, c.top() + 24);
        }
        double[][] ctrl = {{x0, y0 + 1, z0}, {x0 + (x1 - x0) * 0.18, cruise, z0 + (z1 - z0) * 0.18},
            {x0 + (x1 - x0) * 0.82, cruise, z0 + (z1 - z0) * 0.82}, {x1, y1 + 1, z1}};
        int n = 600;
        double[][] out = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            double b0 = u * u * u, b1 = 3 * u * u * t, b2 = 3 * u * t * t, b3 = t * t * t;
            out[i] = new double[] {b0 * ctrl[0][0] + b1 * ctrl[1][0] + b2 * ctrl[2][0] + b3 * ctrl[3][0],
                b0 * ctrl[0][1] + b1 * ctrl[1][1] + b2 * ctrl[2][1] + b3 * ctrl[3][1],
                b0 * ctrl[0][2] + b1 * ctrl[1][2] + b2 * ctrl[2][2] + b3 * ctrl[3][2]};
        }
        return out;
    }

    private void tick(Flight f) {
        if (f.ending) return;
        if (!f.player.isOnline()) { land(f, true); return; }
        if (!f.mount.isValid()) { land(f, true); return; }
        // Ease in and out: slower at take-off and landing, full speed across.
        double total = f.along[f.along.length - 1];
        double edge = Math.min(f.travelled, total - f.travelled);
        double speed = SPEED * Math.max(0.35, Math.min(1, edge / 24));
        f.travelled += speed;
        if (f.travelled >= total) { land(f, true); return; }
        double[] p = at(f, f.travelled), q = at(f, Math.min(total, f.travelled + 2));
        float yaw = (float) Math.toDegrees(Math.atan2(-(q[0] - p[0]), q[2] - p[2]));
        Location to = new Location(f.mount.getWorld(), p[0], p[1], p[2], yaw, 0F);
        f.moving = true;
        try { f.mount.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS); } finally { f.moving = false; }
        if (!f.mount.getPassengers().contains(f.player)) f.mount.addPassenger(f.player);
        f.player.setFallDistance(0);
        if (((int) f.travelled) % 40 == 0) f.mount.getWorld().playSound(to, Sound.ENTITY_PHANTOM_FLAP, SoundCategory.NEUTRAL, 0.4F, 0.6F);
    }

    private double[] at(Flight f, double distance) {
        int lo = 0, hi = f.along.length - 1;
        while (lo < hi - 1) { int mid = (lo + hi) >>> 1; if (f.along[mid] < distance) lo = mid; else hi = mid; }
        double span = f.along[hi] - f.along[lo], t = span <= 0 ? 0 : (distance - f.along[lo]) / span;
        return new double[] {f.path[lo][0] + (f.path[hi][0] - f.path[lo][0]) * t, f.path[lo][1] + (f.path[hi][1] - f.path[lo][1]) * t,
            f.path[lo][2] + (f.path[hi][2] - f.path[lo][2]) * t};
    }

    /** Ends a flight: the beast goes, the rider is set down at the roost (or left where they are) and made mortal again. */
    private void land(Flight f, boolean atDestination) {
        if (f.ending) return;
        f.ending = true;
        if (f.task != null) f.task.cancel();
        flights.remove(f.player.getUniqueId());
        f.mount.eject();
        f.mount.remove();
        f.player.setInvulnerable(f.wasInvulnerable);
        f.player.setFallDistance(0);
        state.set("pending." + f.player.getUniqueId(), null);
        save();
        if (atDestination) {
            f.player.teleport(f.dest);
            if (f.player.isOnline()) {
                f.player.playSound(f.dest, Sound.ENTITY_HAPPY_GHAST_HARNESS_GOGGLES_UP, SoundCategory.NEUTRAL, 1F, 1F);
                f.player.sendActionBar(Component.text("Landed at " + f.to.name(), NamedTextColor.AQUA));
            }
        }
    }

    private Flight flying(Entity e) { return e instanceof Player p ? flights.get(p.getUniqueId()) : null; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent event) {
        Flight f = flying(event.getEntity());
        if (f != null && !f.ending) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        Flight f = flying(event.getEntity());
        if (f != null || event.getEntity().getPersistentDataContainer().has(mountKey, PersistentDataType.BYTE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Flight f = flights.get(event.getPlayer().getUniqueId());
        if (f != null) land(f, true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Flight f = flights.get(event.getEntity().getUniqueId());
        if (f != null) land(f, false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Flight f = flights.get(event.getPlayer().getUniqueId());
        if (f != null) land(f, false);
    }

    /** Someone else moving a rider (a command, another plugin) ends the flight and lets the move happen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Flight f = flights.get(event.getPlayer().getUniqueId());
        if (f != null && !f.ending && !f.moving && event.getCause() != PlayerTeleportEvent.TeleportCause.DISMOUNT && event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN)
            land(f, false);
    }

    /** A rider whose flight a crash cut short is set down where they were going. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<?> pending = state.getList("pending." + player.getUniqueId());
        if (pending == null || pending.size() < 5) return;
        state.set("pending." + player.getUniqueId(), null);
        save();
        World world = Bukkit.getWorld(String.valueOf(pending.get(0)));
        if (world == null) return;
        Location dest = new Location(world, ((Number) pending.get(1)).doubleValue(), ((Number) pending.get(2)).doubleValue(),
            ((Number) pending.get(3)).doubleValue(), ((Number) pending.get(4)).floatValue(), 0F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.setFallDistance(0);
            player.teleport(dest);
            Chat.notice(player, "Your flight was cut short. You've been set down at its destination.");
        }, 5L);
    }

    /** The server stopping lands everyone still in the air. */
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        for (Flight f : new ArrayList<>(flights.values())) {
            try { land(f, true); } catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "Could not land a flight", failure); }
        }
    }

    private void save() {
        try { state.save(file); } catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Could not save reach-flights.yml", failure); }
    }

    boolean flying(Player player) { return flights.containsKey(player.getUniqueId()); }

    ConfigurationSection known() { return state.getConfigurationSection("known"); }
}
