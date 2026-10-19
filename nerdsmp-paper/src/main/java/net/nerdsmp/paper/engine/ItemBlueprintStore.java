package net.nerdsmp.paper.engine;

import org.bukkit.Material;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Staff content is committed before becoming visible. Built-in content remains read-only. */
public final class ItemBlueprintStore {
    public static final int MAX_ITEMS = 512, MAX_SETS = 128;
    @FunctionalInterface interface Writer { void write(Path path, String text) throws IOException; }
    public record Snapshot(Map<String, ItemBlueprint> items, Map<String, ItemSetBlueprint> sets) {
        public Snapshot { items = Map.copyOf(items); sets = Map.copyOf(sets); }
    }
    private final Path file;
    private final Set<String> coreItems, coreSets;
    private final Predicate<Material> itemMaterials;
    private final Writer writer;
    private Snapshot snapshot;

    public ItemBlueprintStore(File file, Set<String> coreItems, Set<String> coreSets) throws IOException {
        this(file, coreItems, coreSets, Material::isItem, ItemBlueprintStore::atomicWrite);
    }
    /** Paper's item registry is supplied by production; tests may inject a small explicit catalog. */
    public ItemBlueprintStore(File file, Set<String> coreItems, Set<String> coreSets, Predicate<Material> catalog) throws IOException {
        this(file, coreItems, coreSets, catalog, ItemBlueprintStore::atomicWrite);
    }
    ItemBlueprintStore(File file, Set<String> coreItems, Set<String> coreSets, Predicate<Material> catalog, Writer writer) throws IOException {
        this.file = file.toPath().toAbsolutePath().normalize();
        this.coreItems = Set.copyOf(coreItems); this.coreSets = Set.copyOf(coreSets);
        this.itemMaterials = java.util.Objects.requireNonNull(catalog); this.writer = java.util.Objects.requireNonNull(writer);
        snapshot = read();
    }
    public synchronized Snapshot snapshot() { return snapshot; }
    public synchronized List<ItemBlueprint> all() { return snapshot.items.values().stream().sorted(java.util.Comparator.comparing(ItemBlueprint::id)).toList(); }
    public synchronized List<ItemSetBlueprint> sets() { return snapshot.sets.values().stream().sorted(java.util.Comparator.comparing(ItemSetBlueprint::id)).toList(); }
    public synchronized Optional<ItemBlueprint> find(String id) { return Optional.ofNullable(snapshot.items.get(id)); }
    public synchronized Optional<ItemSetBlueprint> findSet(String id) { return Optional.ofNullable(snapshot.sets.get(id)); }
    public synchronized void put(ItemBlueprint candidate, ItemBlueprint expected) throws IOException {
        if (!java.util.Objects.equals(snapshot.items.get(candidate.id()), expected))
            throw new IllegalStateException("This item changed while you were editing. Reopen its editor.");
        var items = new TreeMap<>(snapshot.items); items.put(candidate.id(), candidate);
        commit(new Snapshot(items, snapshot.sets));
    }
    public synchronized void putSet(ItemSetBlueprint candidate, ItemSetBlueprint expected) throws IOException {
        if (!java.util.Objects.equals(snapshot.sets.get(candidate.id()), expected))
            throw new IllegalStateException("This set changed while you were editing. Reopen its editor.");
        var sets = new TreeMap<>(snapshot.sets); sets.put(candidate.id(), candidate);
        commit(new Snapshot(snapshot.items, sets));
    }
    public synchronized void validateDraft(ItemBlueprint candidate) {
        var items = new TreeMap<>(snapshot.items); items.put(candidate.id(), candidate);
        Snapshot next = new Snapshot(items, snapshot.sets);
        validate(next); validateTransition(snapshot, next);
    }
    public synchronized void reload() throws IOException {
        Snapshot next = read(); validateTransition(snapshot, next); snapshot = next;
    }
    private void commit(Snapshot next) throws IOException {
        validate(next); validateTransition(snapshot, next);
        writer.write(file, serialize(next)); snapshot = next;
    }
    private void validate(Snapshot next) {
        if (next.items.size() > MAX_ITEMS || next.sets.size() > MAX_SETS)
            throw new IllegalArgumentException("The item engine supports 512 items and 128 sets.");
        next.sets.forEach((id, set) -> {
            if (!id.equals(set.id()) || coreSets.contains(id)) throw new IllegalArgumentException("Built-in set IDs cannot be overwritten.");
        });
        next.items.forEach((id, item) -> {
            if (!id.equals(item.id()) || coreItems.contains(id) || id.startsWith("crafted_"))
                throw new IllegalArgumentException("Built-in and crafted item IDs cannot be overwritten.");
            if (!itemMaterials.test(item.material())) throw new IllegalArgumentException("Material has no obtainable item: " + item.material() + ".");
            if (item.setId() != null && !coreSets.contains(item.setId()) && !next.sets.containsKey(item.setId()))
                throw new IllegalArgumentException("Publish the set before assigning an item to it: " + item.setId() + ".");
        });
    }
    private static void validateTransition(Snapshot previous, Snapshot next) {
        if (!next.items.keySet().containsAll(previous.items.keySet()) || !next.sets.keySet().containsAll(previous.sets.keySet()))
            throw new IllegalArgumentException("Published content cannot be removed while existing items may reference it.");
        previous.items.forEach((id, old) -> {
            var replacement = next.items.get(id);
            if (replacement.material() != old.material() || replacement.slot() != old.slot())
                throw new IllegalArgumentException("A published item's material and equipment slot are fixed. Copy it to a new ID instead.");
            if (replacement.sockets() < old.sockets()) throw new IllegalArgumentException("Published socket capacity cannot shrink.");
        });
    }
    private Snapshot read() throws IOException {
        if (!Files.exists(file)) return new Snapshot(Map.of(), Map.of());
        if (!Files.isRegularFile(file) || Files.size(file) > 4_194_304) throw new IOException("Item engine file is not regular or exceeds 4 MiB: " + file);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(16); options.setCodePointLimit(4_194_304);
            new Yaml(new SafeConstructor(options)).load(text);
            YamlConfiguration yaml = new YamlConfiguration(); yaml.loadFromString(text);
            exact(yaml.getValues(false), Set.of("schema-version", "items", "sets"), "item engine file");
            if (integer(yaml.get("schema-version"), "schema-version") != 1) throw new IllegalArgumentException("Unsupported item engine schema.");
            Map<String, ItemSetBlueprint> sets = new TreeMap<>();
            ConfigurationSection setNodes = section(yaml, "sets");
            for (String id : setNodes.getKeys(false)) {
                ConfigurationSection set = section(setNodes, id);
                exact(set.getValues(false), Set.of("name", "piece-count", "bonuses"), "set " + id);
                Map<Integer, ItemSetBlueprint.Bonus> bonuses = new TreeMap<>();
                ConfigurationSection bonusNodes = section(set, "bonuses");
                for (String raw : bonusNodes.getKeys(false)) {
                    if (!raw.matches("[2-6]")) throw new IllegalArgumentException("Invalid set bonus threshold.");
                    var bonus = section(bonusNodes, raw); exact(bonus.getValues(false), Set.of("description", "stats"), "bonus");
                    bonuses.put(Integer.parseInt(raw), new ItemSetBlueprint.Bonus(string(bonus.get("description"), "description"), stats(section(bonus, "stats"))));
                }
                sets.put(id, new ItemSetBlueprint(id, string(set.get("name"), "name"), integer(set.get("piece-count"), "piece-count"), bonuses));
            }
            Map<String, ItemBlueprint> items = new TreeMap<>();
            ConfigurationSection itemNodes = section(yaml, "items");
            for (String id : itemNodes.getKeys(false)) {
                ConfigurationSection item = section(itemNodes, id);
                exact(item.getValues(false), Set.of("name", "material", "rarity", "sockets", "item-level", "set", "lore", "stats"), "item " + id);
                String set = string(item.get("set"), "set");
                Object rawLore = item.get("lore"); if (!(rawLore instanceof List<?> list)) throw new IllegalArgumentException("Lore must be a list.");
                List<String> lore = new ArrayList<>(); for (Object line : list) lore.add(string(line, "lore"));
                items.put(id, new ItemBlueprint(id, string(item.get("name"), "name"), Material.valueOf(string(item.get("material"), "material")),
                    string(item.get("rarity"), "rarity"), integer(item.get("sockets"), "sockets"), integer(item.get("item-level"), "item-level"),
                    set.isEmpty() ? null : set, lore, stats(section(item, "stats"))));
            }
            Snapshot next = new Snapshot(items, sets); validate(next); return next;
        } catch (InvalidConfigurationException | IllegalArgumentException | NullPointerException | YAMLException error) {
            throw new IOException("Invalid item engine file; previous content was preserved: " + error.getMessage(), error);
        }
    }
    private static String serialize(Snapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", 1);
        ConfigurationSection sets = yaml.createSection("sets");
        new TreeMap<>(snapshot.sets).forEach((id, set) -> {
            var entry = sets.createSection(id); entry.set("name", set.name()); entry.set("piece-count", set.pieceCount());
            var bonuses = entry.createSection("bonuses"); set.bonuses().forEach((threshold, bonus) -> {
                var node = bonuses.createSection(threshold.toString()); node.set("description", bonus.description());
                node.createSection("stats", new TreeMap<>(bonus.stats()));
            });
        });
        ConfigurationSection items = yaml.createSection("items");
        new TreeMap<>(snapshot.items).forEach((id, item) -> {
            var entry = items.createSection(id); entry.set("name", item.name()); entry.set("material", item.material().name());
            entry.set("rarity", item.rarity()); entry.set("sockets", item.sockets()); entry.set("item-level", item.itemLevel());
            entry.set("set", item.setId() == null ? "" : item.setId()); entry.set("lore", item.lore());
            entry.createSection("stats", new TreeMap<>(item.stats()));
        });
        return yaml.saveToString();
    }
    private static Map<String, Double> stats(ConfigurationSection section) {
        Map<String, Double> result = new TreeMap<>(); section.getValues(false).forEach((id, value) -> {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Stats must be finite numbers.");
            result.put(id, number.doubleValue());
        }); return result;
    }
    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        var section = parent.getConfigurationSection(path); if (section == null) throw new IllegalArgumentException(path + " must be a mapping."); return section;
    }
    private static void exact(Map<?, ?> map, Set<String> keys, String label) {
        if (!map.keySet().equals(keys)) throw new IllegalArgumentException(label + " has missing or unknown fields.");
    }
    private static String string(Object value, String label) {
        if (!(value instanceof String text)) throw new IllegalArgumentException(label + " must be text."); return text;
    }
    private static int integer(Object value, String label) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
            || ((Number) value).longValue() < Integer.MIN_VALUE || ((Number) value).longValue() > Integer.MAX_VALUE)
            throw new IllegalArgumentException(label + " must be an integer."); return ((Number) value).intValue();
    }
    private static void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent()); Path pending = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(text); while (bytes.hasRemaining()) channel.write(bytes); channel.force(true);
            }
            Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
}
