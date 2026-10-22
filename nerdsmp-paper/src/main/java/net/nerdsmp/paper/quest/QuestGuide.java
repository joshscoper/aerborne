package net.nerdsmp.paper.quest;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.digsite.DigSiteDefinition;
import net.nerdsmp.paper.npc.NpcManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.zone.ZoneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Shows each player where their quests are: a gold ! over NPCs with a quest for them, a gold ? over NPCs they
 * can hand a quest in to (grey while it's still in progress), the tracked quest on the HUD, and a breadcrumb
 * trail with a light column leading to the tracked quest's next objective. Everything is per player.
 */
final class QuestGuide implements AutoCloseable {
    enum Mark { NONE, AVAILABLE, READY, ACTIVE }

    private static final double MARKER_RANGE = 64.0D;
    private static final Particle.DustOptions TRAIL = new Particle.DustOptions(Color.fromRGB(255, 196, 0), 1.1F);
    private final NerdSmpPlugin plugin;
    private final QuestService quests;
    private final Map<UUID, Markers> markers = new HashMap<>();
    private final Map<UUID, Map<UUID, Mark>> shown = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private final Map<UUID, Long> lastBar = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    QuestGuide(NerdSmpPlugin plugin, QuestService quests) {
        this.plugin = plugin;
        this.quests = quests;
    }

    void start() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::followBodies, 20L, 2L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::updateMarkers, 30L, 10L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::guide, 40L, 10L));
    }

    @Override public void close() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        markers.values().forEach(Markers::remove);
        markers.clear();
    }

    void forget(Player player) {
        shown.remove(player.getUniqueId());
        lastBar.remove(player.getUniqueId());
    }

    void show(Player player) {
        hidden.remove(player.getUniqueId());
    }

    void hide(Player player) {
        hidden.add(player.getUniqueId());
        refresh(player);
    }

    // ------------------------------------------------------------------ NPC markers

    private final class Markers {
        private final LivingEntity body;
        private final Map<Mark, TextDisplay> displays = new EnumMap<>(Mark.class);
        private Location last;

        private Markers(LivingEntity body) {
            this.body = body;
            display(Mark.AVAILABLE, "<gold><bold>!</bold></gold>");
            display(Mark.READY, "<gold><bold>?</bold></gold>");
            display(Mark.ACTIVE, "<gray><bold>?</bold></gray>");
        }

        private void display(Mark mark, String text) {
            Location at = position();
            displays.put(mark, at.getWorld().spawn(at, TextDisplay.class, display -> {
                display.text(Chat.render(text));
                display.setBillboard(Display.Billboard.CENTER);
                display.setVisibleByDefault(false);
                display.setPersistent(false);
                display.setShadowed(true);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setTeleportDuration(2);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(2.4F, 2.4F, 2.4F), new AxisAngle4f()));
            }));
            last = body.getLocation();
        }

        private Location position() {
            return body.getLocation().add(0.0D, body.getHeight() + 1.25D, 0.0D);
        }

        private boolean valid() {
            return body.isValid() && displays.values().stream().allMatch(TextDisplay::isValid);
        }

        private void follow() {
            Location now = body.getLocation();
            if (last != null && last.getWorld().equals(now.getWorld()) && last.distanceSquared(now) < 0.0004D) return;
            last = now;
            Location at = position();
            displays.values().forEach(display -> display.teleport(at));
        }

        private void remove() { displays.values().forEach(TextDisplay::remove); }
    }

    private void followBodies() {
        markers.values().forEach(Markers::follow);
    }

    private void updateMarkers() {
        if (plugin.npcs() == null) return;
        Set<UUID> live = new HashSet<>();
        for (NpcManager.Body body : plugin.npcs().bodies()) {
            if (quests.catalog().offeredBy(body.definitionId()).isEmpty()) continue;
            live.add(body.instance());
            Markers current = markers.get(body.instance());
            if (current != null && current.body.equals(body.entity()) && current.valid()) continue;
            if (current != null) current.remove();
            markers.put(body.instance(), new Markers(body.entity()));
            shown.values().forEach(map -> map.remove(body.instance()));
        }
        markers.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
        Map<UUID, String> definitions = new HashMap<>();
        plugin.npcs().bodies().forEach(body -> definitions.put(body.instance(), body.definitionId()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<UUID, Mark> seen = shown.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
            for (Map.Entry<UUID, Markers> entry : markers.entrySet()) {
                LivingEntity body = entry.getValue().body;
                Mark mark = Mark.NONE;
                if (body.getWorld().equals(player.getWorld()) && body.getLocation().distanceSquared(player.getLocation()) <= MARKER_RANGE * MARKER_RANGE)
                    mark = quests.mark(player, definitions.getOrDefault(entry.getKey(), ""));
                Mark before = seen.getOrDefault(entry.getKey(), Mark.NONE);
                if (mark == before) continue;
                if (before != Mark.NONE) player.hideEntity(plugin, entry.getValue().displays.get(before));
                if (mark != Mark.NONE) player.showEntity(plugin, entry.getValue().displays.get(mark));
                seen.put(entry.getKey(), mark);
            }
        }
    }

    // ------------------------------------------------------------------ tracker and trail

    /** Redraws the tracked quest on the HUD after progress, a new quest or a hand-in. */
    void refresh(Player player) {
        if (plugin.presentation() == null) return;
        Optional<QuestDefinition> quest = quests.tracked(player);
        if (quest.isEmpty()) { plugin.presentation().hudAppendix(player, List.of()); return; }
        int[] counts = quests.counts(player, quest.get());
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        lines.add("<gold>➤ " + Chat.escape(shorten(quest.get().title(), 22)) + "</gold>");
        boolean done = true;
        for (int i = 0; i < quest.get().objectives().size() && lines.size() < 5; i++) {
            QuestDefinition.Objective objective = quest.get().objectives().get(i);
            int have = objective.type() == QuestDefinition.Type.DELIVER ? Math.min(objective.count(), quests.heldFor(player, objective))
                : counts == null || i >= counts.length ? 0 : counts[i];
            boolean met = have >= objective.count();
            if (!met && objective.type() != QuestDefinition.Type.DELIVER) done = false;
            lines.add((met ? "<green>✔ " : "<gray>• ") + Chat.escape(shorten(objective.description(), 20))
                + (objective.count() > 1 ? " " + have + "/" + objective.count() : "") + (met ? "</green>" : "</gray>"));
        }
        if (done) lines.add("<yellow>Return to " + Chat.escape(shorten(quests.npcTitle(quest.get().turnIn()), 16)) + "</yellow>");
        plugin.presentation().hudAppendix(player, lines);
    }

    private void guide() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hidden.contains(player.getUniqueId())) continue;
            Optional<QuestDefinition> quest = quests.tracked(player);
            if (quest.isEmpty()) continue;
            Target target = target(player, quest.get());
            if (target == null || !target.location().getWorld().equals(player.getWorld())) continue;
            Location from = player.getLocation();
            double distance = horizontal(from, target.location());
            if (target.arrived() || distance < 6.0D) {
                if (now - lastBar.getOrDefault(player.getUniqueId(), 0L) > 3_000L && target.arrived()) {
                    lastBar.put(player.getUniqueId(), now);
                    player.sendActionBar(Chat.render("<gold>➤</gold> <white>" + Chat.escape(target.label()) + "</white> <gray>· you're there</gray>"));
                }
                continue;
            }
            drawTrail(player, from, target.location(), distance);
            if (distance <= 160.0D) drawBeacon(player, target.location());
            if (now - lastBar.getOrDefault(player.getUniqueId(), 0L) > 1_500L) {
                lastBar.put(player.getUniqueId(), now);
                player.sendActionBar(Chat.render("<gold>➤</gold> <white>" + Chat.escape(target.label()) + "</white> <yellow>"
                    + Math.round(distance) + "m " + compass(from, target.location()) + "</yellow>"));
            }
        }
    }

    record Target(Location location, String label, boolean arrived) { }

    /** The next unfinished objective's place, or the turn-in NPC once only hand-ins remain. */
    Target target(Player player, QuestDefinition quest) {
        int[] counts = quests.counts(player, quest);
        Location here = player.getLocation();
        String zoneHere = zoneAt(here);
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            if (objective.type() == QuestDefinition.Type.DELIVER) continue;
            if (counts != null && i < counts.length && counts[i] >= objective.count()) continue;
            String label = objective.description();
            switch (objective.type()) {
                case TALK -> {
                    Location npc = nearest(here, objective.targets().stream().flatMap(id -> plugin.npcs().locationsOf(id).stream()).toList());
                    if (npc != null) return new Target(npc, label, false);
                }
                case VISIT -> {
                    return zoneTarget(here, objective.zone(), label, zoneHere);
                }
                case KILL -> {
                    if (objective.zone() != null) return zoneTarget(here, objective.zone(), label, zoneHere);
                    List<Location> points = new ArrayList<>();
                    for (ZoneDefinition zone : plugin.zones().all()) {
                        if (!zone.enabled() || zone.mobs().stream().noneMatch(mob -> objective.targets().contains(mob.mobId()))) continue;
                        if (zone.id().equals(zoneHere)) return new Target(here, label, true);
                        World world = Bukkit.getWorld(zone.worldId());
                        if (world != null) zone.points().forEach(point -> points.add(new Location(world, point.x(), point.y(), point.z())));
                    }
                    Location best = nearest(here, points);
                    if (best != null) return new Target(best, label, false);
                }
                case HARVEST, DIG -> {
                    Location node = nearestNode(here, objective);
                    if (node != null) return new Target(node, label, node.distanceSquared(here) < 36 * 36
                        && (objective.zone() == null || objective.zone().equals(zoneHere)));
                    if (objective.zone() != null) return zoneTarget(here, objective.zone(), label, zoneHere);
                }
                default -> { }
            }
        }
        Location npc = nearest(here, plugin.npcs().locationsOf(quest.turnIn()));
        return npc == null ? null : new Target(npc, "Return to " + quests.npcTitle(quest.turnIn()), false);
    }

    private Target zoneTarget(Location here, String zoneId, String label, String zoneHere) {
        if (zoneId == null) return null;
        if (zoneId.equals(zoneHere)) return new Target(here, label, true);
        ZoneDefinition zone = plugin.zones().find(zoneId).orElse(null);
        World world = zone == null ? null : Bukkit.getWorld(zone.worldId());
        if (world == null) return null;
        List<Location> points = new ArrayList<>();
        zone.points().forEach(point -> points.add(new Location(world, point.x(), point.y(), point.z())));
        Location best = nearest(here, points);
        if (best == null) {
            ZoneDefinition.Bounds b = zone.bounds();
            int x = (b.minX() + b.maxX()) / 2, z = (b.minZ() + b.maxZ()) / 2;
            best = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        }
        return new Target(best, label, false);
    }

    private Location nearestNode(Location here, QuestDefinition.Objective objective) {
        if (plugin.digSites() == null) return null;
        World world = here.getWorld();
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (DigSiteDefinition site : plugin.digSites().store().all()) {
            if (!site.enabled() || !site.worldId().equals(world.getUID()) || objective.zone() != null && !objective.zone().equals(site.zoneId())) continue;
            for (DigSiteDefinition.Node node : site.nodes()) {
                if (!objective.matches(node.material())) continue;
                double distance = here.distanceSquared(new Location(world, node.x() + 0.5, node.y(), node.z() + 0.5));
                if (distance < bestDistance) { bestDistance = distance; best = new Location(world, node.x() + 0.5, node.y() + 1, node.z() + 0.5); }
            }
        }
        return best;
    }

    private String zoneAt(Location location) {
        return plugin.zones().at(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ())
            .filter(ZoneDefinition::enabled).map(ZoneDefinition::id).orElse(null);
    }

    private static Location nearest(Location here, List<Location> candidates) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Location candidate : candidates) {
            if (candidate == null || !candidate.getWorld().equals(here.getWorld())) continue;
            double distance = candidate.distanceSquared(here);
            if (distance < bestDistance) { bestDistance = distance; best = candidate; }
        }
        return best;
    }

    /** Gold dust on the ground for the next dozen blocks toward the target. */
    private void drawTrail(Player player, Location from, Location to, double distance) {
        double dx = (to.getX() - from.getX()) / distance, dz = (to.getZ() - from.getZ()) / distance;
        double y = from.getY();
        World world = from.getWorld();
        for (double step = 1.5D; step <= Math.min(14.0D, distance); step += 1.25D) {
            double x = from.getX() + dx * step, z = from.getZ() + dz * step;
            Double ground = ground(world, x, y, z);
            if (ground == null) continue;
            y = ground;
            player.spawnParticle(Particle.DUST, x, ground + 0.15D, z, 1, 0.05D, 0.0D, 0.05D, 0.0D, TRAIL);
        }
    }

    /** A column of light over the destination, drawn even from afar. */
    private void drawBeacon(Player player, Location target) {
        for (double rise = 0.5D; rise <= 18.0D; rise += 1.5D)
            Particle.END_ROD.builder().location(target.clone().add(0.0D, rise, 0.0D)).count(1).offset(0.05D, 0.2D, 0.05D)
                .extra(0.0D).receivers(player).force(true).spawn();
    }

    private static Double ground(World world, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return null;
        for (int feet = by + 3; feet >= by - 4; feet--) {
            Block body = world.getBlockAt(bx, feet, bz), below = world.getBlockAt(bx, feet - 1, bz);
            if (body.isPassable() && (!below.isPassable() || below.isLiquid())) return below.isLiquid() ? (double) feet - 0.1D : below.getBoundingBox().getMaxY();
        }
        return null;
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String compass(Location from, Location to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[Math.floorMod(Math.round(angle / 45.0D), 8)];
    }

    private static String shorten(String text, int width) {
        return text.length() <= width ? text : text.substring(0, width - 1) + "…";
    }
}
