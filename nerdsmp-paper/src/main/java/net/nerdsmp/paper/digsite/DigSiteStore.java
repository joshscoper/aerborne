package net.nerdsmp.paper.digsite;

import org.bukkit.configuration.ConfigurationSection;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static net.nerdsmp.paper.digsite.DigSiteDefinition.*;

/** Strict UUID-based storage. A failed save leaves the published definitions unchanged. */
public final class DigSiteStore {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_FILE_BYTES = 4_194_304;
    private static final Set<String> ROOT_KEYS = Set.of("schema-version", "sites");
    private static final Set<String> SITE_KEYS = Set.of("zone-id", "world-uuid", "generation", "enabled", "cooldown-seconds", "nodes");
    private static final Set<String> NODE_KEYS = Set.of("x", "y", "z", "material", "block-data");

    @FunctionalInterface interface Writer { void write(Path path, String yaml) throws IOException; }
    private final Path file;
    private final Writer writer;
    private Map<String, DigSiteDefinition> sites;

    public DigSiteStore(File file) throws IOException {
        this(file, DigSiteStore::atomicWrite);
    }

    DigSiteStore(File file, Writer writer) throws IOException {
        this.file = Objects.requireNonNull(file).toPath().toAbsolutePath().normalize();
        this.writer = Objects.requireNonNull(writer);
        sites = read(this.file);
    }

    public synchronized List<DigSiteDefinition> all() {
        return sites.values().stream().sorted(Comparator.comparing(DigSiteDefinition::id)).toList();
    }

    public synchronized Optional<DigSiteDefinition> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sites.get(id));
    }

    public synchronized void put(DigSiteDefinition site) throws IOException {
        Objects.requireNonNull(site, "Dig site is required");
        Map<String, DigSiteDefinition> next = new TreeMap<>(sites);
        next.put(site.id(), site);
        validate(next.values());
        save(next);
        sites = Map.copyOf(next);
    }

    public synchronized void remove(String id) throws IOException {
        if (id == null || !sites.containsKey(id)) {
            throw new IllegalArgumentException("No dig site named " + id + ".");
        }
        Map<String, DigSiteDefinition> next = new TreeMap<>(sites);
        next.remove(id);
        save(next);
        sites = Map.copyOf(next);
    }

    private void save(Map<String, DigSiteDefinition> next) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        ConfigurationSection out = yaml.createSection("sites");
        next.forEach((id, site) -> {
            ConfigurationSection node = out.createSection(id);
            node.set("zone-id", site.zoneId());
            node.set("world-uuid", site.worldId().toString());
            node.set("generation", site.generation().toString());
            node.set("enabled", site.enabled());
            node.set("cooldown-seconds", site.cooldownSeconds());
            node.set("nodes", site.nodes().stream().map(cell -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("x", cell.x()); value.put("y", cell.y()); value.put("z", cell.z());
                value.put("material", cell.material()); value.put("block-data", cell.blockData());
                return value;
            }).toList());
        });
        String text = yaml.saveToString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IOException("Dig-site file exceeds 4 MiB.");
        }
        writer.write(file, text);
    }

    private static Map<String, DigSiteDefinition> read(Path file) throws IOException {
        if (!Files.exists(file)) return Map.of();
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("Dig-site file is not a regular file or exceeds 4 MiB: " + file);
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(16);
            options.setCodePointLimit(MAX_FILE_BYTES);
            Map<?, ?> root = mapping(new Yaml(new SafeConstructor(options)).load(text), "dig-site file");
            exactKeys(root, ROOT_KEYS, "dig-site file");
            if (integer(root.get("schema-version"), "schema-version") != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported dig-site schema.");
            }
            Map<?, ?> definitions = mapping(root.get("sites"), "sites");
            if (definitions.size() > MAX_SITES) throw new IllegalArgumentException("At most 64 dig sites are supported.");
            Map<String, DigSiteDefinition> next = new TreeMap<>();
            for (Map.Entry<?, ?> entry : definitions.entrySet()) {
                String id = string(entry.getKey(), "dig-site id");
                Map<?, ?> value = mapping(entry.getValue(), "dig site " + id);
                exactKeys(value, SITE_KEYS, "dig site " + id);
                Object rawNodes = value.get("nodes");
                if (!(rawNodes instanceof List<?> list) || list.size() > MAX_NODES) {
                    throw new IllegalArgumentException("Dig-site nodes must be a list of at most 2,048 cells.");
                }
                List<Node> nodes = new ArrayList<>();
                for (Object rawNode : list) {
                    Map<?, ?> node = mapping(rawNode, "node");
                    exactKeys(node, NODE_KEYS, "node");
                    nodes.add(new Node(integer(node.get("x"), "x"), integer(node.get("y"), "y"),
                        integer(node.get("z"), "z"), string(node.get("material"), "material"),
                        string(node.get("block-data"), "block-data")));
                }
                next.put(id, new DigSiteDefinition(id, string(value.get("zone-id"), "zone-id"),
                    uuid(value.get("world-uuid"), "world-uuid"), uuid(value.get("generation"), "generation"),
                    bool(value.get("enabled"), "enabled"), integer(value.get("cooldown-seconds"), "cooldown-seconds"), nodes));
            }
            validate(next.values());
            return Map.copyOf(next);
        } catch (IllegalArgumentException | NullPointerException | YAMLException exception) {
            throw new IOException("Invalid dig-site file; refusing to replace it: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }

    private static void validate(Collection<DigSiteDefinition> sites) {
        if (sites.size() > MAX_SITES) throw new IllegalArgumentException("At most 64 dig sites are supported.");
        Set<Cell> occupied = new HashSet<>();
        int count = 0;
        for (DigSiteDefinition site : sites) {
            count += site.nodes().size();
            if (count > MAX_TOTAL_NODES) throw new IllegalArgumentException("At most 8,192 dig-site nodes are supported in total.");
            for (Node node : site.nodes()) {
                if (!occupied.add(new Cell(site.worldId(), node.x(), node.y(), node.z()))) {
                    throw new IllegalArgumentException("Dig sites cannot share a world cell, including disabled sites.");
                }
            }
        }
    }

    private static Map<?, ?> mapping(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be a mapping.");
        return map;
    }

    private static void exactKeys(Map<?, ?> values, Set<String> keys, String label) {
        if (!values.keySet().equals(keys)) throw new IllegalArgumentException(label + " has missing or unknown fields.");
    }

    private static String string(Object value, String label) {
        if (!(value instanceof String text)) throw new IllegalArgumentException(label + " must be text.");
        return text;
    }

    private static boolean bool(Object value, String label) {
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(label + " must be true or false.");
        return bool;
    }

    private static int integer(Object value, String label) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
            || ((Number) value).longValue() < Integer.MIN_VALUE || ((Number) value).longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be an integer.");
        }
        return ((Number) value).intValue();
    }

    private static UUID uuid(Object value, String label) {
        String text = string(value, label);
        if (!text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException(label + " must be a full UUID.");
        }
        return UUID.fromString(text);
    }

    private static void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer data = StandardCharsets.UTF_8.encode(text);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record Cell(UUID world, int x, int y, int z) { }
}
