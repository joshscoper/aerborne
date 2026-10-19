package net.nerdsmp.paper.studio.npc;

import net.nerdsmp.paper.npc.NpcContent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import static net.nerdsmp.paper.studio.npc.NpcBlueprint.*;

/** Strict local graphs; atomic disk commit precedes publication and stale edits never overwrite peers. */
public final class NpcBlueprintStore {
    @FunctionalInterface interface Writer { void write(Path file, String data) throws IOException; }
    private static final Set<String> NPC_FIELDS = Set.of("name", "title", "type", "faction", "root", "shop", "look-radius", "nodes");
    private final Path file;
    private final Writer writer;
    private NpcContent base;
    private Map<String, NpcBlueprint> snapshot;
    public NpcBlueprintStore(File file, NpcContent base) throws IOException { this(file, base, NpcBlueprintStore::atomicWrite); }
    NpcBlueprintStore(File file, NpcContent base, Writer writer) throws IOException {
        this.file = file.toPath().toAbsolutePath().normalize(); this.base = Objects.requireNonNull(base); this.writer = Objects.requireNonNull(writer);
        snapshot = read();
    }
    public synchronized List<NpcBlueprint> all() { return snapshot.values().stream().sorted(java.util.Comparator.comparing(NpcBlueprint::id)).toList(); }
    public synchronized Optional<NpcBlueprint> find(String id) { return Optional.ofNullable(snapshot.get(id)); }
    public synchronized void base(NpcContent content) { validate(snapshot, content); base = content; }
    public synchronized void validateDraft(NpcBlueprint npc) {
        NpcBlueprints.validate(npc, base);
        var previous = snapshot.get(npc.id());
        if (previous != null && !previous.type().equals(npc.type())) throw new IllegalArgumentException("Published NPC body types are locked. Copy to a new ID to change the body.");
        if (previous == null && snapshot.size() >= MAX_NPCS) throw new IllegalArgumentException("At most 128 authored NPCs are supported.");
    }
    public synchronized void put(NpcBlueprint npc, NpcBlueprint expected) throws IOException {
        if (!Objects.equals(expected, snapshot.get(npc.id()))) throw new IllegalStateException("That NPC changed while you were editing. Reopen it before publishing; your draft is retained.");
        validateDraft(npc);
        Map<String, NpcBlueprint> next = new TreeMap<>(snapshot); next.put(npc.id(), npc); validate(next, base);
        String encoded = serialize(next);
        if (encoded.getBytes(StandardCharsets.UTF_8).length > 4_194_304)
            throw new IllegalArgumentException("The authored NPC catalog exceeds 4 MiB. Shorten dialogue or archive unused authoring content before publishing.");
        writer.write(file, encoded); snapshot = Map.copyOf(next);
    }
    public synchronized void reload() throws IOException {
        Map<String, NpcBlueprint> candidate = read();
        for (NpcBlueprint old : snapshot.values()) {
            NpcBlueprint next = candidate.get(old.id());
            if (next == null) throw new IOException("Authored NPC removal needs a placement/reference review: " + old.id());
            if (!next.type().equals(old.type())) throw new IOException("Published NPC body type cannot change: " + old.id());
        }
        snapshot = candidate;
    }
    private static void validate(Map<String, NpcBlueprint> next, NpcContent base) {
        if (next.size() > MAX_NPCS) throw new IllegalArgumentException("At most 128 authored NPCs are supported.");
        NpcBlueprints.merge(base, next.values().stream().toList());
    }
    private Map<String, NpcBlueprint> read() throws IOException {
        if (!Files.exists(file)) return Map.of();
        if (!Files.isRegularFile(file) || Files.size(file) > 4_194_304) throw new IOException("NPC blueprint file must be regular and no larger than 4 MiB.");
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(18); options.setCodePointLimit(4_194_304); new Yaml(new SafeConstructor(options)).load(text);
            YamlConfiguration yaml = new YamlConfiguration(); yaml.loadFromString(text);
            exact(yaml.getValues(false), Set.of("schema-version", "npcs"));
            if (integer(yaml.get("schema-version")) != 1) throw new IllegalArgumentException("Unsupported NPC blueprint schema.");
            ConfigurationSection npcs = section(yaml, "npcs");
            if (npcs.getKeys(false).size() > MAX_NPCS) throw new IllegalArgumentException("Too many NPC blueprints.");
            Map<String, NpcBlueprint> result = new TreeMap<>();
            for (String id : npcs.getKeys(false)) {
                ConfigurationSection npc = section(npcs, id); exact(npc.getValues(false), NPC_FIELDS);
                ConfigurationSection nodeRoot = section(npc, "nodes"); Map<String, Node> nodes = new TreeMap<>();
                if (nodeRoot.getKeys(false).size() > MAX_NODES) throw new IllegalArgumentException("Too many dialogue nodes.");
                for (String nodeId : nodeRoot.getKeys(false)) {
                    ConfigurationSection node = section(nodeRoot, nodeId); exact(node.getValues(false), Set.of("text", "choices"));
                    Object rawText = node.get("text"), rawChoices = node.get("choices");
                    if (!(rawText instanceof List<?> lines) || !(rawChoices instanceof List<?> choices)) throw new IllegalArgumentException("Dialogue text and choices must be lists.");
                    List<String> dialogueText = lines.stream().map(NpcBlueprintStore::string).toList(); List<Choice> dialogueChoices = new ArrayList<>();
                    for (Object raw : choices) {
                        if (!(raw instanceof Map<?, ?> choice)) throw new IllegalArgumentException("Dialogue choices must be mappings.");
                        exact(choice, Set.of("id", "text", "next", "service", "minimum-level", "minimum-reputation"));
                        dialogueChoices.add(new Choice(string(choice.get("id")), string(choice.get("text")), string(choice.get("next")), Service.valueOf(string(choice.get("service"))), integer(choice.get("minimum-level")), integer(choice.get("minimum-reputation"))));
                    }
                    nodes.put(nodeId, new Node(nodeId, dialogueText, dialogueChoices));
                }
                result.put(id, new NpcBlueprint(id, string(npc.get("name")), string(npc.get("title")), string(npc.get("type")), string(npc.get("faction")), string(npc.get("root")), string(npc.get("shop")), number(npc.get("look-radius")), nodes));
            }
            validate(result, base); return Map.copyOf(result);
        } catch (InvalidConfigurationException | IllegalArgumentException | NullPointerException | YAMLException failure) {
            throw new IOException("Invalid NPC blueprints; existing content was preserved: " + file + " (" + failure.getMessage() + ")", failure);
        }
    }
    private static String serialize(Map<String, NpcBlueprint> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", 1); ConfigurationSection npcs = yaml.createSection("npcs");
        new TreeMap<>(snapshot).forEach((id, npc) -> {
            ConfigurationSection data = npcs.createSection(id); data.set("name", npc.name()); data.set("title", npc.title()); data.set("type", npc.type());
            data.set("faction", npc.faction()); data.set("root", npc.root()); data.set("shop", npc.shop()); data.set("look-radius", npc.lookRadius());
            ConfigurationSection nodes = data.createSection("nodes"); new TreeMap<>(npc.nodes()).forEach((nodeId, node) -> {
                ConfigurationSection n = nodes.createSection(nodeId); n.set("text", node.text());
                n.set("choices", node.choices().stream().map(choice -> {
                    Map<String, Object> entry = new LinkedHashMap<>(); entry.put("id", choice.id()); entry.put("text", choice.text()); entry.put("next", choice.next());
                    entry.put("service", choice.service().name()); entry.put("minimum-level", choice.minimumLevel()); entry.put("minimum-reputation", choice.minimumReputation()); return entry;
                }).toList());
            });
        }); return yaml.saveToString();
    }
    private static ConfigurationSection section(ConfigurationSection root, String id) { var result = root.getConfigurationSection(id); if (result == null) throw new IllegalArgumentException(id + " must be a mapping."); return result; }
    private static void exact(Map<?, ?> actual, Set<String> fields) { if (!actual.keySet().equals(fields)) throw new IllegalArgumentException("Missing or unknown NPC blueprint fields."); }
    private static String string(Object value) { if (!(value instanceof String text)) throw new IllegalArgumentException("Expected text."); return text; }
    private static int integer(Object value) { if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) || ((Number)value).longValue() < Integer.MIN_VALUE || ((Number)value).longValue() > Integer.MAX_VALUE) throw new IllegalArgumentException("Expected a bounded whole number."); return ((Number)value).intValue(); }
    private static double number(Object value) { if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Expected a finite number."); return number.doubleValue(); }
    private static void atomicWrite(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent()); Path pending = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) { ByteBuffer bytes = StandardCharsets.UTF_8.encode(text); while (bytes.hasRemaining()) channel.write(bytes); channel.force(true); }
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
    }
}
