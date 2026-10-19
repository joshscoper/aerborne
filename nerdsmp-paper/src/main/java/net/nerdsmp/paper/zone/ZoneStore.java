package net.nerdsmp.paper.zone;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static net.nerdsmp.paper.zone.ZoneDefinition.*;

/** Strict UUID-based zone file. Failed writes never change the published snapshot. */
public final class ZoneStore {
    @FunctionalInterface interface Writer { void write(Path path, String yaml) throws IOException; }
    private final Path file;
    private final Writer writer;
    private Map<String, ZoneDefinition> zones;

    public ZoneStore(File file) throws IOException { this(file, ZoneStore::atomicWrite); }
    ZoneStore(File file, Writer writer) throws IOException {
        this.file = file.toPath().toAbsolutePath().normalize();
        this.writer = writer;
        this.zones = read(this.file);
    }
    public synchronized List<ZoneDefinition> all() { return zones.values().stream().sorted(java.util.Comparator.comparing(ZoneDefinition::id)).toList(); }
    public synchronized Optional<ZoneDefinition> find(String id) { return Optional.ofNullable(zones.get(id)); }
    /** Includes disabled definitions: overlap and safe-area management do not silently depend on spawning being active. */
    public synchronized Optional<ZoneDefinition> at(UUID world, double x, double y, double z) {
        return zones.values().stream().filter(zone -> zone.worldId().equals(world) && zone.bounds().contains(x, y, z)).findFirst();
    }
    public synchronized void put(ZoneDefinition zone) throws IOException {
        Map<String, ZoneDefinition> next = new TreeMap<>(zones);
        next.put(zone.id(), zone);
        validate(next.values());
        save(next);
        zones = Map.copyOf(next);
    }
    public synchronized void remove(String id) throws IOException {
        if (!zones.containsKey(id)) throw new IllegalArgumentException("No zone named " + id + ".");
        Map<String, ZoneDefinition> next = new TreeMap<>(zones);
        next.remove(id);
        save(next);
        zones = Map.copyOf(next);
    }
    private void save(Map<String, ZoneDefinition> next) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        ConfigurationSection section = yaml.createSection("zones");
        next.forEach((id, zone) -> {
            ConfigurationSection out = section.createSection(id);
            out.set("title", zone.title()); out.set("world-uuid", zone.worldId().toString());
            out.set("enabled", zone.enabled()); out.set("safe", zone.safe());
            out.set("cap", zone.cap()); out.set("respawn-seconds", zone.respawnSeconds()); out.set("activation-radius", zone.activationRadius());
            Bounds b = zone.bounds();
            out.set("bounds", Map.of("min-x", b.minX(), "min-y", b.minY(), "min-z", b.minZ(), "max-x", b.maxX(), "max-y", b.maxY(), "max-z", b.maxZ()));
            out.set("points", zone.points().stream().map(p -> {
                Map<String, Object> point = new LinkedHashMap<>(); point.put("x", p.x()); point.put("y", p.y()); point.put("z", p.z()); point.put("yaw", p.yaw()); return point;
            }).toList());
            out.set("mobs", zone.mobs().stream().map(m -> Map.<String, Object>of("id", m.mobId(), "weight", m.weight(), "level", m.level())).toList());
        });
        writer.write(file, yaml.saveToString());
    }
    private static Map<String, ZoneDefinition> read(Path file) throws IOException {
        if (!Files.exists(file)) return Map.of();
        if (!Files.isRegularFile(file) || Files.size(file) > 4_194_304) throw new IOException("Zone file is not a regular file or exceeds 4 MiB: " + file);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(16);
            options.setCodePointLimit(4_194_304);
            // Paper's convenience loader accepts duplicate mapping keys; reject ambiguity first.
            new Yaml(new SafeConstructor(options)).load(text);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            exactKeys(yaml.getValues(false), Set.of("schema-version", "zones"), "zone file");
            if (integer(yaml.get("schema-version"), "schema-version") != 1) throw new IllegalArgumentException("Unsupported zone schema.");
            ConfigurationSection section = yaml.getConfigurationSection("zones");
            if (section == null) throw new IllegalArgumentException("zones must be a mapping.");
            Map<String, ZoneDefinition> next = new TreeMap<>();
            for (String id : section.getKeys(false)) {
                ConfigurationSection node = section.getConfigurationSection(id);
                if (node == null) throw new IllegalArgumentException("Zone " + id + " must be a mapping.");
                Map<String, Object> value = node.getValues(false);
                exactKeys(value, Set.of("title", "world-uuid", "enabled", "safe", "cap", "respawn-seconds", "activation-radius", "bounds", "points", "mobs"), id);
                ConfigurationSection bounds = node.getConfigurationSection("bounds");
                if (bounds == null) throw new IllegalArgumentException("Zone " + id + " requires bounds.");
                exactKeys(bounds.getValues(false), Set.of("min-x", "min-y", "min-z", "max-x", "max-y", "max-z"), "bounds");
                Bounds b = new Bounds(integer(bounds.get("min-x"), "min-x"), integer(bounds.get("min-y"), "min-y"), integer(bounds.get("min-z"), "min-z"),
                    integer(bounds.get("max-x"), "max-x"), integer(bounds.get("max-y"), "max-y"), integer(bounds.get("max-z"), "max-z"));
                List<SpawnPoint> points = new ArrayList<>();
                for (Map<?, ?> point : maps(node.get("points"), "points")) {
                    exactKeys(point, Set.of("x", "y", "z", "yaw"), "point");
                    points.add(new SpawnPoint(number(point.get("x"), "x"), number(point.get("y"), "y"), number(point.get("z"), "z"), (float) number(point.get("yaw"), "yaw")));
                }
                List<MobEntry> mobs = new ArrayList<>();
                for (Map<?, ?> mob : maps(node.get("mobs"), "mobs")) {
                    exactKeys(mob, Set.of("id", "weight", "level"), "mob");
                    mobs.add(new MobEntry(string(mob.get("id"), "mob id"), integer(mob.get("weight"), "weight"), integer(mob.get("level"), "level")));
                }
                String uuid = string(value.get("world-uuid"), "world-uuid");
                if (!uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException("Invalid world UUID.");
                next.put(id, new ZoneDefinition(id, string(value.get("title"), "title"), UUID.fromString(uuid), b,
                    bool(value.get("enabled"), "enabled"), bool(value.get("safe"), "safe"), integer(value.get("cap"), "cap"),
                    integer(value.get("respawn-seconds"), "respawn-seconds"), integer(value.get("activation-radius"), "activation-radius"), points, mobs));
            }
            validate(next.values());
            return Map.copyOf(next);
        } catch (InvalidConfigurationException | IllegalArgumentException | NullPointerException | YAMLException e) {
            throw new IOException("Invalid zone file; refusing to replace it: " + file + " (" + e.getMessage() + ")", e);
        }
    }
    private static void validate(Collection<ZoneDefinition> zones) {
        if (zones.size() > MAX_ZONES) throw new IllegalArgumentException("At most 128 zones are supported.");
        List<ZoneDefinition> list = List.copyOf(zones);
        for (int i = 0; i < list.size(); i++) for (int j = i + 1; j < list.size(); j++) {
            ZoneDefinition a = list.get(i), b = list.get(j);
            if (a.worldId().equals(b.worldId()) && a.bounds().overlaps(b.bounds()))
                throw new IllegalArgumentException("Zones " + a.id() + " and " + b.id() + " overlap.");
        }
    }
    private static void exactKeys(Map<?, ?> values, Set<String> expected, String label) {
        if (!values.keySet().equals(expected)) throw new IllegalArgumentException(label + " has missing or unknown fields.");
    }
    private static String string(Object value, String label) {
        if (!(value instanceof String text)) throw new IllegalArgumentException(label + " must be text."); return text;
    }
    private static boolean bool(Object value, String label) {
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(label + " must be true or false."); return bool;
    }
    private static int integer(Object value, String label) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
            || ((Number) value).longValue() < Integer.MIN_VALUE || ((Number) value).longValue() > Integer.MAX_VALUE)
            throw new IllegalArgumentException(label + " must be an integer.");
        return ((Number) value).intValue();
    }
    private static double number(Object value, String label) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException(label + " must be a finite number.");
        return number.doubleValue();
    }
    private static List<Map<?, ?>> maps(Object value, String label) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " must be a list.");
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " entries must be mappings.");
            result.add(map);
        }
        return result;
    }
    private static void atomicWrite(Path path, String yaml) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer data = StandardCharsets.UTF_8.encode(yaml);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
