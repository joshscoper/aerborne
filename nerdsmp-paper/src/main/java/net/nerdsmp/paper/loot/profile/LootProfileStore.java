package net.nerdsmp.paper.loot.profile;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;

/** Strict staff-authored tables. Disk commit precedes snapshot publication, including assignments and reloads. */
public final class LootProfileStore {
    @FunctionalInterface interface Writer { void write(Path file, String text) throws IOException; }
    private record Snapshot(Map<String, LootProfileDefinition> profiles, Map<String, String> zones, Map<String, String> mobs) {
        Snapshot {
            profiles = Map.copyOf(profiles); zones = Map.copyOf(zones); mobs = Map.copyOf(mobs);
        }
    }
    private final Path file;
    private final Writer writer;
    private record ItemCatalog(Set<ItemId> known, Set<ItemId> gear) {
        ItemCatalog {
            known=Set.copyOf(known); gear=Set.copyOf(gear);
            if(!known.containsAll(gear)) throw new IllegalArgumentException("Unknown gear item IDs.");
        }
    }
    private ItemCatalog catalog;
    private java.util.function.Supplier<Set<String>> externalReferences=Set::of;
    private final Predicate<Material> itemMaterials;
    private Snapshot snapshot;

    public LootProfileStore(File file, Set<ItemId> knownItems) throws IOException {
        this(file, knownItems, Set.of(), Material::isItem, LootProfileStore::atomicWrite);
    }
    public LootProfileStore(File file, Set<ItemId> knownItems, Set<ItemId> gearItems) throws IOException {
        this(file, knownItems, gearItems, Material::isItem, LootProfileStore::atomicWrite);
    }
    /** Allows a supplied item catalog for API-only tests; production constructors use Paper's item registry. */
    public LootProfileStore(File file, Set<ItemId> knownItems, Set<ItemId> gearItems, Predicate<Material> itemMaterials) throws IOException {
        this(file, knownItems, gearItems, itemMaterials, LootProfileStore::atomicWrite);
    }
    LootProfileStore(File file, Set<ItemId> knownItems, Set<ItemId> gearItems, Writer writer) throws IOException {
        this(file, knownItems, gearItems, Material::isItem, writer);
    }
    LootProfileStore(File file, Set<ItemId> knownItems, Set<ItemId> gearItems, Predicate<Material> itemMaterials, Writer writer) throws IOException {
        this.file = file.toPath().toAbsolutePath().normalize();
        this.writer = writer;
        this.catalog = new ItemCatalog(knownItems,gearItems);
        this.itemMaterials = java.util.Objects.requireNonNull(itemMaterials, "itemMaterials");
        snapshot = read();
    }

    public synchronized Set<ItemId> knownItems() { return catalog.known(); }
    public synchronized Set<ItemId> gearItems() { return catalog.gear(); }
    /** Publish a validated catalog after a successful item-engine change; never drop active references. */
    public synchronized void refreshItemCatalog(Set<ItemId> known,Set<ItemId> gear) {
        ItemCatalog next=new ItemCatalog(known,gear);
        validate(snapshot,next,externalReferences);
        catalog=next;
    }
    public synchronized void externalReferences(java.util.function.Supplier<Set<String>> references) {
        var next=java.util.Objects.requireNonNull(references);
        validate(snapshot,catalog,next);
        externalReferences=next;
    }
    public synchronized List<LootProfileDefinition> all() {
        return snapshot.profiles.values().stream().sorted(java.util.Comparator.comparing(LootProfileDefinition::id)).toList();
    }
    public synchronized Optional<LootProfileDefinition> find(String id) { return Optional.ofNullable(snapshot.profiles.get(id)); }
    public synchronized Map<String, String> zoneAssignments() { return snapshot.zones; }
    public synchronized Map<String, String> mobAssignments() { return snapshot.mobs; }
    /** The zone table takes precedence over the custom-mob fallback; tables never roll twice. */
    public synchronized Optional<LootProfileDefinition> resolve(String zoneId, String customId) {
        String id = zoneId == null ? null : snapshot.zones.get(zoneId);
        if (id == null && customId != null) {
            // Reads are tolerant of unrelated living entities; writes alone validate authoring targets.
            String key = customId.startsWith("custom:") ? customId.substring(7) : customId;
            if (key.startsWith("vanilla:")) key = key.toLowerCase(Locale.ROOT);
            id = snapshot.mobs.get(key);
        }
        return Optional.ofNullable(id == null ? null : snapshot.profiles.get(id));
    }
    public synchronized void put(LootProfileDefinition profile) throws IOException {
        Map<String, LootProfileDefinition> next = new TreeMap<>(snapshot.profiles);
        next.put(profile.id(), profile);
        commit(new Snapshot(next, snapshot.zones, snapshot.mobs));
    }
    public synchronized void remove(String id) throws IOException {
        if (!snapshot.profiles.containsKey(id)) throw new IllegalArgumentException("No loot table named " + id + ".");
        if (snapshot.zones.containsValue(id) || snapshot.mobs.containsValue(id))
            throw new IllegalArgumentException("Unassign this loot table before deleting it.");
        Map<String, LootProfileDefinition> next = new TreeMap<>(snapshot.profiles);
        next.remove(id);
        commit(new Snapshot(next, snapshot.zones, snapshot.mobs));
    }
    public synchronized void assignZone(String zoneId, String profileId) throws IOException { assign(true, identifier(zoneId), profileId); }
    public synchronized void assignMob(String customId, String profileId) throws IOException { assign(false, mobId(customId), profileId); }
    public synchronized void unassignZone(String zoneId) throws IOException { unassign(true, identifier(zoneId)); }
    public synchronized void unassignMob(String customId) throws IOException { unassign(false, mobId(customId)); }
    public synchronized void reload() throws IOException { snapshot = read(); }

    private void assign(boolean zone, String target, String profileId) throws IOException {
        if (!snapshot.profiles.containsKey(profileId)) throw new IllegalArgumentException("No loot table named " + profileId + ".");
        Map<String, String> next = new TreeMap<>(zone ? snapshot.zones : snapshot.mobs);
        next.put(target, profileId);
        commit(new Snapshot(snapshot.profiles, zone ? next : snapshot.zones, zone ? snapshot.mobs : next));
    }
    private void unassign(boolean zone, String target) throws IOException {
        Map<String, String> next = new TreeMap<>(zone ? snapshot.zones : snapshot.mobs);
        if (next.remove(target) == null) throw new IllegalArgumentException("That target has no assigned loot table.");
        commit(new Snapshot(snapshot.profiles, zone ? next : snapshot.zones, zone ? snapshot.mobs : next));
    }
    private void commit(Snapshot next) throws IOException {
        validate(next);
        writer.write(file, serialize(next));
        snapshot = next;
    }
    private void validate(Snapshot next) {
        validate(next,catalog,externalReferences);
    }
    private void validate(Snapshot next,ItemCatalog itemCatalog,java.util.function.Supplier<Set<String>> references) {
        if (next.profiles.size() > MAX_PROFILES) throw new IllegalArgumentException("At most 128 loot tables are supported.");
        if (next.zones.size() > 128 || next.mobs.size() > 256) throw new IllegalArgumentException("Too many loot assignments.");
        next.profiles.forEach((id, profile) -> {
            if (!id.equals(profile.id())) throw new IllegalArgumentException("Mismatched loot table ID.");
            for (Entry entry : profile.entries()) {
                if (entry.kind() == Kind.MATERIAL && !itemMaterials.test(Material.valueOf(entry.source())))
                    throw new IllegalArgumentException("Material has no obtainable item: " + entry.source() + ".");
                if (entry.kind() != Kind.ITEM) continue;
                ItemId itemId = new ItemId(entry.source());
                if (!itemCatalog.known().contains(itemId)) throw new IllegalArgumentException("Unknown item ID " + itemId + ".");
                if ((entry.unidentified() || entry.affixes() > 0) && !itemCatalog.gear().contains(itemId))
                    throw new IllegalArgumentException("Only gear can be unidentified or affixed: " + itemId + ".");
            }
        });
        next.zones.forEach((id, profile) -> { identifier(id); requireProfile(next, profile); });
        next.mobs.forEach((id, profile) -> { if (!id.equals(mobId(id))) throw new IllegalArgumentException("Invalid mob assignment ID."); requireProfile(next, profile); });
        for(String profile:references.get()) if(!next.profiles.containsKey(profile))
            throw new IllegalArgumentException("Loot table "+profile+" is still referenced by an authored mob.");
    }
    private static void requireProfile(Snapshot next, String profile) {
        if (!next.profiles.containsKey(profile)) throw new IllegalArgumentException("Loot assignment refers to missing table " + profile + ".");
    }
    private static String mobId(String raw) {
        if (raw == null) throw new IllegalArgumentException("A custom-mob ID is required.");
        if (raw.startsWith("vanilla:")) {
            EntityType type;
            try { type = EntityType.valueOf(raw.substring(8).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown mob type " + raw + "."); }
            if (!type.isAlive() || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND)
                throw new IllegalArgumentException("Loot may only be assigned to mob types.");
            return "vanilla:" + type.name().toLowerCase(Locale.ROOT);
        }
        return identifier(raw.startsWith("custom:") ? raw.substring(7) : raw);
    }

    private Snapshot read() throws IOException {
        if (!Files.exists(file)) return new Snapshot(Map.of(), Map.of(), Map.of());
        if (!Files.isRegularFile(file) || Files.size(file) > 4_194_304)
            throw new IOException("Loot profile file is not a regular file or exceeds 4 MiB: " + file);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(16); options.setCodePointLimit(4_194_304);
            new Yaml(new SafeConstructor(options)).load(text);
            YamlConfiguration yaml = new YamlConfiguration(); yaml.loadFromString(text);
            exact(yaml.getValues(false), Set.of("schema-version", "profiles", "assignments"), "loot file");
            if (integer(yaml.get("schema-version"), "schema-version") != 1) throw new IllegalArgumentException("Unsupported loot profile schema.");
            ConfigurationSection profiles = section(yaml, "profiles");
            Map<String, LootProfileDefinition> loaded = new TreeMap<>();
            for (String id : profiles.getKeys(false)) {
                ConfigurationSection profile = section(profiles, id);
                exact(profile.getValues(false), Set.of("mode", "entries"), "profile " + id);
                List<Entry> entries = new ArrayList<>();
                Object rawEntries = profile.get("entries");
                if (!(rawEntries instanceof List<?> list)) throw new IllegalArgumentException("entries must be a list.");
                for (Object value : list) {
                    if (!(value instanceof Map<?, ?> entry)) throw new IllegalArgumentException("Loot entries must be mappings.");
                    exact(entry, Set.of("id", "kind", "source", "chance", "minimum", "maximum", "unidentified", "affixes"), "entry");
                    entries.add(new Entry(string(entry.get("id"), "id"), Kind.valueOf(string(entry.get("kind"), "kind")),
                        string(entry.get("source"), "source"), number(entry.get("chance"), "chance"),
                        integer(entry.get("minimum"), "minimum"), integer(entry.get("maximum"), "maximum"),
                        bool(entry.get("unidentified"), "unidentified"), integer(entry.get("affixes"), "affixes")));
                }
                loaded.put(id, new LootProfileDefinition(id, Mode.valueOf(string(profile.get("mode"), "mode")), entries));
            }
            ConfigurationSection assignments = section(yaml, "assignments");
            exact(assignments.getValues(false), Set.of("zones", "mobs"), "assignments");
            Snapshot next = new Snapshot(loaded, assignmentMap(section(assignments, "zones")), assignmentMap(section(assignments, "mobs")));
            validate(next); return next;
        } catch (InvalidConfigurationException | IllegalArgumentException | NullPointerException | YAMLException error) {
            throw new IOException("Invalid loot profile file; refusing to replace it: " + file + " (" + error.getMessage() + ")", error);
        }
    }
    private static String serialize(Snapshot next) {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", 1);
        ConfigurationSection profiles = yaml.createSection("profiles");
        new TreeMap<>(next.profiles).forEach((id, profile) -> {
            ConfigurationSection node = profiles.createSection(id); node.set("mode", profile.mode().name());
            node.set("entries", profile.entries().stream().map(entry -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", entry.id()); value.put("kind", entry.kind().name()); value.put("source", entry.source());
                value.put("chance", entry.chance()); value.put("minimum", entry.minimum()); value.put("maximum", entry.maximum());
                value.put("unidentified", entry.unidentified()); value.put("affixes", entry.affixes()); return value;
            }).toList());
        });
        yaml.createSection("assignments.zones", new TreeMap<>(next.zones));
        yaml.createSection("assignments.mobs", new TreeMap<>(next.mobs));
        return yaml.saveToString();
    }
    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection result = parent.getConfigurationSection(path);
        if (result == null) throw new IllegalArgumentException(path + " must be a mapping."); return result;
    }
    private static Map<String, String> assignmentMap(ConfigurationSection section) {
        Map<String, String> result = new TreeMap<>();
        section.getValues(false).forEach((id, value) -> result.put(id, string(value, "profile ID"))); return result;
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
    private static double number(Object value, String label) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IllegalArgumentException(label + " must be a finite number."); return number.doubleValue();
    }
    private static boolean bool(Object value, String label) {
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(label + " must be true or false."); return bool;
    }
    private static void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path pending = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
}
