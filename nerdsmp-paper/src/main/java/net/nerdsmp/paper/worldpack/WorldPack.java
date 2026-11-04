package net.nerdsmp.paper.worldpack;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shipped content for one known map: the zones, NPC anchors, warps and renewable node sites that make it
 * playable. A pack only applies to a world whose seed and landmark blocks match, so it never touches an
 * unrelated map. Bukkit-free so the bundled packs can be validated in unit tests.
 */
public record WorldPack(
    String id,
    int version,
    String title,
    long seed,
    String difficulty,
    boolean naturalMonsters,
    /** A generated world the pack belongs to by name; null for a map matched by seed and landmarks. */
    String world,
    List<Landmark> landmarks,
    Point spawn,
    Border border,
    Map<String, Point> warps,
    Map<String, Zone> zones,
    Map<String, Anchor> npcs,
    Map<String, Site> sites
) {
    public static final int SCHEMA_VERSION = 1;

    public WorldPack {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid world pack id " + id);
        if (version < 1) throw new IllegalArgumentException("World pack version must be positive");
        if (difficulty != null && !List.of("easy", "normal", "hard").contains(difficulty))
            throw new IllegalArgumentException("World pack difficulty must be easy, normal or hard");
        landmarks = List.copyOf(landmarks);
        warps = unmodifiable(warps);
        zones = unmodifiable(zones);
        npcs = unmodifiable(npcs);
        sites = unmodifiable(sites);
        if (landmarks.isEmpty() && (world == null || world.isBlank()))
            throw new IllegalArgumentException("A world pack needs landmark blocks (or a generated world's name) so it never applies to another map");
        List<Zone> list = List.copyOf(zones.values());
        for (int i = 0; i < list.size(); i++) for (int j = i + 1; j < list.size(); j++)
            if (list.get(i).overlaps(list.get(j))) throw new IllegalArgumentException("Pack zones " + list.get(i).id() + " and " + list.get(j).id() + " overlap");
        for (Zone zone : zones.values()) for (Point point : zone.points())
            if (!zone.contains(point)) throw new IllegalArgumentException("Spawn point " + point + " lies outside zone " + zone.id());
        if (border != null) {
            for (Zone zone : zones.values()) if (!border.contains(zone.bounds()))
                throw new IllegalArgumentException("Zone " + zone.id() + " reaches past the map border");
            for (Anchor anchor : npcs.values()) {
                if (!border.contains(anchor.point().x(), anchor.point().z()))
                    throw new IllegalArgumentException("NPC " + anchor.key() + " stands outside the map border");
                for (Point step : anchor.route()) if (!border.contains(step.x(), step.z()))
                    throw new IllegalArgumentException("NPC " + anchor.key() + " patrols outside the map border");
            }
            for (Map.Entry<String, Point> warp : warps.entrySet()) if (!border.contains(warp.getValue().x(), warp.getValue().z()))
                throw new IllegalArgumentException("Warp " + warp.getKey() + " lies outside the map border");
            if (spawn != null && !border.contains(spawn.x(), spawn.z())) throw new IllegalArgumentException("Spawn lies outside the map border");
        }
        for (Site site : sites.values()) {
            Zone zone = zones.get(site.zone());
            if (zone == null) throw new IllegalArgumentException("Site " + site.id() + " uses unknown pack zone " + site.zone());
            if (!zone.containsBox(site.bounds())) throw new IllegalArgumentException("Site " + site.id() + " must lie inside zone " + zone.id());
        }
    }

    private static <V> Map<String, V> unmodifiable(Map<String, V> map) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public record Landmark(int x, int y, int z, String material) { }

    /**
     * The playable rectangle (block coordinates, inclusive). Vanilla world borders are square, so the pack sets
     * one on the long axis and the plugin keeps players inside the short axis itself.
     */
    public record Border(int minX, int minZ, int maxX, int maxZ) {
        public Border {
            if (minX >= maxX || minZ >= maxZ || maxX - minX > 60_000 || maxZ - minZ > 60_000)
                throw new IllegalArgumentException("The map border must be an ordered rectangle");
        }
        public boolean contains(double x, double z) {
            return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
        }
        boolean contains(Box box) {
            return box.minX() >= minX && box.maxX() <= maxX && box.minZ() >= minZ && box.maxZ() <= maxZ;
        }
        public int width() { return maxX - minX + 1; }
        public int depth() { return maxZ - minZ + 1; }
    }

    public record Point(double x, double y, double z, float yaw) {
        @Override public String toString() { return x + "," + y + "," + z; }
    }

    /** Inclusive block bounds: minX, minY, minZ, maxX, maxY, maxZ. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ || minY < -64 || maxY > 319) throw new IllegalArgumentException("Bounds must be ordered and within Y -64..319");
        }
        boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }
    }

    public record MobEntry(String id, int weight, int level) { }

    public record Zone(String id, String title, Box bounds, boolean safe, String levels, int cap, int respawnSeconds,
                       int radius, List<MobEntry> mobs, List<Point> points) {
        public Zone {
            mobs = List.copyOf(mobs);
            points = List.copyOf(points);
            if (!safe && (mobs.isEmpty() || points.isEmpty())) throw new IllegalArgumentException("Combat zone " + id + " needs mobs and spawn points");
        }
        boolean contains(Point point) { return bounds.contains(point.x(), point.y(), point.z()); }
        boolean containsBox(Box box) {
            return box.minX() >= bounds.minX() && box.maxX() <= bounds.maxX() && box.minY() >= bounds.minY()
                && box.maxY() <= bounds.maxY() && box.minZ() >= bounds.minZ() && box.maxZ() <= bounds.maxZ();
        }
        boolean overlaps(Zone other) {
            Box a = bounds, b = other.bounds;
            return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minY() <= b.maxY() && a.maxY() >= b.minY()
                && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
        }
    }

    /** An NPC anchor; guards add a patrol route of block positions walked back and forth from the anchor. */
    public record Anchor(String key, String npc, Point point, List<Point> route) {
        public Anchor {
            route = List.copyOf(route);
            if (route.size() == 1 || route.size() > 32) throw new IllegalArgumentException("Patrol routes have 2-32 waypoints: " + key);
        }

        public Anchor(String key, String npc, Point point) { this(key, npc, point, List.of()); }
    }

    /** Blocks of {@code materials} inside {@code bounds} become renewable nodes when the pack applies. */
    public record Site(String id, String zone, Box bounds, Set<String> materials, int cooldownSeconds) {
        public Site { materials = Set.copyOf(materials); }
    }

    // ------------------------------------------------------------------ parsing

    public static WorldPack parse(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(16);
        Map<?, ?> root = map(new Yaml(new SafeConstructor(options)).load(text), "world pack");
        if (integer(root.get("schema-version"), "schema-version") != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported world pack schema");
        Map<?, ?> match = map(root.get("match"), "match");
        List<Landmark> landmarks = new ArrayList<>();
        if (match.containsKey("landmarks")) for (Object raw : list(match.get("landmarks"), "landmarks")) {
            List<?> item = list(raw, "landmark");
            if (item.size() != 4) throw new IllegalArgumentException("Landmarks are [x, y, z, material]");
            landmarks.add(new Landmark(integer(item.get(0), "x"), integer(item.get(1), "y"), integer(item.get(2), "z"),
                string(item.get(3), "material").toUpperCase(Locale.ROOT)));
        }
        Map<String, Point> warps = new LinkedHashMap<>();
        if (root.containsKey("warps")) for (Map.Entry<?, ?> entry : map(root.get("warps"), "warps").entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!name.matches("[a-z0-9_-]{1,24}")) throw new IllegalArgumentException("Invalid warp name " + name);
            warps.put(name, point(entry.getValue()));
        }
        Map<String, Zone> zones = new LinkedHashMap<>();
        if (root.containsKey("zones")) for (Map.Entry<?, ?> entry : map(root.get("zones"), "zones").entrySet()) {
            String id = String.valueOf(entry.getKey());
            zones.put(id, zone(id, map(entry.getValue(), id)));
        }
        Map<String, Anchor> npcs = new LinkedHashMap<>();
        if (root.containsKey("npcs")) for (Map.Entry<?, ?> entry : map(root.get("npcs"), "npcs").entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!key.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("Invalid NPC anchor key " + key);
            List<?> item = list(entry.getValue(), key);
            if (item.size() != 5 && item.size() != 6) throw new IllegalArgumentException("NPC anchors are [npc, x, y, z, yaw] or [npc, x, y, z, yaw, route]: " + key);
            List<Point> route = new ArrayList<>();
            if (item.size() == 6) for (Object raw : list(item.get(5), "route")) {
                List<?> step = list(raw, "route point");
                if (step.size() != 3) throw new IllegalArgumentException("Route points are [x, y, z]: " + key);
                route.add(new Point(number(step.get(0), "x").doubleValue(), number(step.get(1), "y").doubleValue(), number(step.get(2), "z").doubleValue(), 0F));
            }
            npcs.put(key, new Anchor(key, string(item.get(0), "npc"), point(item.subList(1, 5)), route));
        }
        Map<String, Site> sites = new LinkedHashMap<>();
        if (root.containsKey("sites")) for (Map.Entry<?, ?> entry : map(root.get("sites"), "sites").entrySet()) {
            String id = String.valueOf(entry.getKey());
            Map<?, ?> node = map(entry.getValue(), id);
            List<String> materials = new ArrayList<>();
            for (Object material : list(node.get("materials"), "materials")) materials.add(string(material, "material").toUpperCase(Locale.ROOT));
            sites.put(id, new Site(id, string(node.get("zone"), "zone"), box(node.get("bounds")), Set.copyOf(materials),
                node.containsKey("cooldown") ? integer(node.get("cooldown"), "cooldown") : 30));
        }
        return new WorldPack(string(root.get("id"), "id"), integer(root.get("version"), "version"), string(root.get("title"), "title"),
            number(match.get("seed"), "seed").longValue(),
            root.containsKey("difficulty") ? string(root.get("difficulty"), "difficulty").toLowerCase(Locale.ROOT) : null,
            !Boolean.FALSE.equals(root.get("natural-monsters")), match.containsKey("world") ? string(match.get("world"), "world") : null,
            landmarks, root.containsKey("spawn") ? point(root.get("spawn")) : null,
            root.containsKey("border") ? border(root.get("border")) : null,
            warps, zones, npcs, sites);
    }

    private static Zone zone(String id, Map<?, ?> node) {
        boolean safe = node.containsKey("safe") && Boolean.TRUE.equals(node.get("safe"));
        List<MobEntry> mobs = new ArrayList<>();
        if (node.containsKey("mobs")) for (Object raw : list(node.get("mobs"), "mobs")) {
            List<?> item = list(raw, "mob");
            if (item.size() != 3) throw new IllegalArgumentException("Zone mobs are [id, weight, level] in " + id);
            mobs.add(new MobEntry(string(item.get(0), "mob"), integer(item.get(1), "weight"), integer(item.get(2), "level")));
        }
        List<Point> points = new ArrayList<>();
        if (node.containsKey("points")) for (Object raw : list(node.get("points"), "points")) points.add(point(raw));
        return new Zone(id, string(node.get("title"), "title"), box(node.get("bounds")), safe,
            node.containsKey("levels") ? String.valueOf(node.get("levels")) : "",
            node.containsKey("cap") ? integer(node.get("cap"), "cap") : 8,
            node.containsKey("respawn") ? integer(node.get("respawn"), "respawn") : 30,
            node.containsKey("radius") ? integer(node.get("radius"), "radius") : 48, mobs, points);
    }

    private static Border border(Object raw) {
        List<?> item = list(raw, "border");
        if (item.size() != 4) throw new IllegalArgumentException("The border is [minX, minZ, maxX, maxZ]");
        return new Border(integer(item.get(0), "border"), integer(item.get(1), "border"), integer(item.get(2), "border"), integer(item.get(3), "border"));
    }

    private static Box box(Object raw) {
        List<?> item = list(raw, "bounds");
        if (item.size() != 6) throw new IllegalArgumentException("Bounds are [minX, minY, minZ, maxX, maxY, maxZ]");
        return new Box(integer(item.get(0), "bounds"), integer(item.get(1), "bounds"), integer(item.get(2), "bounds"),
            integer(item.get(3), "bounds"), integer(item.get(4), "bounds"), integer(item.get(5), "bounds"));
    }

    private static Point point(Object raw) {
        List<?> item = list(raw, "point");
        if (item.size() != 4) throw new IllegalArgumentException("Points are [x, y, z, yaw]");
        return new Point(number(item.get(0), "x").doubleValue(), number(item.get(1), "y").doubleValue(),
            number(item.get(2), "z").doubleValue(), number(item.get(3), "yaw").floatValue());
    }

    private static Map<?, ?> map(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be a mapping");
        return map;
    }

    private static List<?> list(Object value, String label) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " must be a list");
        return list;
    }

    private static String string(Object value, String label) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(label + " must be text");
        return text.strip();
    }

    private static int integer(Object value, String label) {
        if (!(value instanceof Integer number)) throw new IllegalArgumentException(label + " must be a whole number");
        return number;
    }

    private static Number number(Object value, String label) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException(label + " must be a number");
        return number;
    }
}
