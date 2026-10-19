package net.nerdsmp.paper.engine.mob;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Strict staff content file. A failed disk write or reload cannot publish half of an encounter catalog. */
public final class MobBlueprintStore {
    @FunctionalInterface interface Writer { void write(Path file, String text) throws IOException; }
    private record Snapshot(long revision, Map<String, MobBlueprint> mobs) {
        Snapshot { mobs = Map.copyOf(mobs); }
    }
    private static final Set<String> FIELDS = Set.of("template", "name", "type", "rank", "level", "health", "attack-damage",
        "movement-speed", "ability", "ability-radius", "ability-power", "ability-cooldown-seconds", "experience-reward", "loot-profile");
    private final Path file;
    private final Set<String> reservedIds;
    private final Predicate<String> knownLoot;
    private final Writer writer;
    private Snapshot snapshot;

    public MobBlueprintStore(File file, Set<String> reservedIds, Predicate<String> knownLoot) throws IOException {
        this(file, reservedIds, knownLoot, MobBlueprintStore::atomicWrite);
    }
    MobBlueprintStore(File file, Set<String> reservedIds, Predicate<String> knownLoot, Writer writer) throws IOException {
        this.file = file.toPath().toAbsolutePath().normalize();
        this.reservedIds = Set.copyOf(reservedIds); this.knownLoot = java.util.Objects.requireNonNull(knownLoot);
        this.writer = java.util.Objects.requireNonNull(writer); snapshot = read();
    }
    public synchronized long revision() { return snapshot.revision; }
    public synchronized List<MobBlueprint> all() { return snapshot.mobs.values().stream().sorted(java.util.Comparator.comparing(MobBlueprint::id)).toList(); }
    public synchronized Optional<MobBlueprint> find(String id) { return Optional.ofNullable(snapshot.mobs.get(id)); }
    public synchronized void validate(MobBlueprint blueprint) {
        if (reservedIds.contains(blueprint.id())) throw new IllegalArgumentException("That ID belongs to a shipped encounter; copy it to a new ID instead.");
        if (!blueprint.lootProfile().isEmpty() && !knownLoot.test(blueprint.lootProfile())) throw new IllegalArgumentException("Unknown loot profile: " + blueprint.lootProfile());
        MobBlueprint old = snapshot.mobs.get(blueprint.id());
        if (old != null && !old.type().equals(blueprint.type())) throw new IllegalArgumentException("Published entity types are locked. Copy the mob to a new ID to change its type.");
        if (old == null && snapshot.mobs.size() >= MobBlueprint.MAX_BLUEPRINTS) throw new IllegalArgumentException("At most 256 authored mobs are supported.");
    }
    public synchronized void publish(MobBlueprint blueprint, long expectedRevision) throws IOException {
        if (expectedRevision != snapshot.revision) throw new IllegalStateException("The mob catalog changed. Reopen the editor before publishing.");
        validate(blueprint);
        Map<String, MobBlueprint> next = new TreeMap<>(snapshot.mobs); next.put(blueprint.id(), blueprint);
        Snapshot candidate = new Snapshot(Math.incrementExact(snapshot.revision), next);
        writer.write(file, serialize(candidate)); snapshot = candidate;
    }
    /** Read-only validation so the engine can validate its runtime manager before accepting a reload. */
    public synchronized List<MobBlueprint> readCandidate() throws IOException {
        Snapshot candidate = read(); validateTypes(candidate);
        return candidate.mobs.values().stream().sorted(java.util.Comparator.comparing(MobBlueprint::id)).toList();
    }
    public synchronized void reload() throws IOException {
        Snapshot candidate = read(); validateTypes(candidate);
        // A changed external file must invalidate every open editor, even if its on-disk revision is stale.
        snapshot = new Snapshot(Math.max(Math.incrementExact(snapshot.revision), candidate.revision), candidate.mobs);
    }
    private void validateTypes(Snapshot candidate) throws IOException {
        for (MobBlueprint old : snapshot.mobs.values()) {
            MobBlueprint next = candidate.mobs.get(old.id());
            if (next != null && !old.type().equals(next.type())) throw new IOException("Published mob type cannot change: " + old.id());
        }
    }
    private Snapshot read() throws IOException {
        if (!Files.exists(file)) return new Snapshot(0, Map.of());
        if (!Files.isRegularFile(file) || Files.size(file) > 2_097_152) throw new IOException("Mob blueprint file exceeds 2 MiB or is not a regular file: " + file);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(12); options.setCodePointLimit(2_097_152);
            new Yaml(new SafeConstructor(options)).load(text);
            YamlConfiguration yaml = new YamlConfiguration(); yaml.loadFromString(text);
            exact(yaml.getValues(false), Set.of("schema-version", "revision", "mobs"));
            if (integer(yaml.get("schema-version")) != 1) throw new IllegalArgumentException("Unsupported mob blueprint schema.");
            long revision = whole(yaml.get("revision")); if (revision < 0 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid catalog revision.");
            ConfigurationSection mobs = yaml.getConfigurationSection("mobs");
            if (mobs == null || mobs.getKeys(false).size() > MobBlueprint.MAX_BLUEPRINTS) throw new IllegalArgumentException("mobs must be a mapping with at most 256 entries.");
            Map<String, MobBlueprint> loaded = new TreeMap<>();
            for (String id : mobs.getKeys(false)) {
                if (reservedIds.contains(id)) throw new IllegalArgumentException("Shipped encounter ID is reserved: " + id);
                ConfigurationSection entry = mobs.getConfigurationSection(id);
                if (entry == null) throw new IllegalArgumentException("Mob " + id + " must be a mapping.");
                exact(entry.getValues(false), FIELDS);
                MobBlueprint blueprint = new MobBlueprint(id, string(entry.get("template")), string(entry.get("name")), string(entry.get("type")),
                    MobBlueprint.Rank.valueOf(string(entry.get("rank"))), integer(entry.get("level")), number(entry.get("health")), number(entry.get("attack-damage")),
                    number(entry.get("movement-speed")), MobBlueprint.Ability.valueOf(string(entry.get("ability"))), number(entry.get("ability-radius")),
                    number(entry.get("ability-power")), integer(entry.get("ability-cooldown-seconds")), integer(entry.get("experience-reward")), string(entry.get("loot-profile")));
                if (!blueprint.lootProfile().isEmpty() && !knownLoot.test(blueprint.lootProfile())) throw new IllegalArgumentException("Unknown loot profile: " + blueprint.lootProfile());
                loaded.put(id, blueprint);
            }
            return new Snapshot(revision, loaded);
        } catch (InvalidConfigurationException | IllegalArgumentException | NullPointerException | YAMLException exception) {
            throw new IOException("Invalid mob blueprints; keeping the previous catalog: " + file + " (" + exception.getMessage() + ")", exception);
        }
    }
    private static String serialize(Snapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", 1); yaml.set("revision", snapshot.revision);
        ConfigurationSection mobs = yaml.createSection("mobs");
        new TreeMap<>(snapshot.mobs).forEach((id, mob) -> {
            ConfigurationSection entry = mobs.createSection(id);
            entry.set("template", mob.template()); entry.set("name", mob.name()); entry.set("type", mob.type()); entry.set("rank", mob.rank().name());
            entry.set("level", mob.level()); entry.set("health", mob.health()); entry.set("attack-damage", mob.attackDamage()); entry.set("movement-speed", mob.movementSpeed());
            entry.set("ability", mob.ability().name()); entry.set("ability-radius", mob.abilityRadius()); entry.set("ability-power", mob.abilityPower());
            entry.set("ability-cooldown-seconds", mob.abilityCooldownSeconds()); entry.set("experience-reward", mob.experienceReward()); entry.set("loot-profile", mob.lootProfile());
        });
        return yaml.saveToString();
    }
    private static void exact(Map<?, ?> actual, Set<String> expected) {
        if (!actual.keySet().equals(expected)) throw new IllegalArgumentException("Missing or unknown mob fields.");
    }
    private static String string(Object value) { if (!(value instanceof String text)) throw new IllegalArgumentException("Expected text."); return text; }
    private static long whole(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException("Expected an integer.");
        return ((Number) value).longValue();
    }
    private static int integer(Object value) {
        long whole = whole(value); if (whole < Integer.MIN_VALUE || whole > Integer.MAX_VALUE) throw new IllegalArgumentException("Integer is out of range."); return (int) whole;
    }
    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Expected a finite number."); return number.doubleValue();
    }
    private static void atomicWrite(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent()); Path pending = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer data = StandardCharsets.UTF_8.encode(text); while (data.hasRemaining()) channel.write(data); channel.force(true);
            }
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
}
