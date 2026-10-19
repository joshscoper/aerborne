package net.nerdsmp.paper.studio.qa;

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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Strict, atomic team notes. Save precedes publication; item revisions prevent stale editor writes. */
public final class WorkboardStore {
    public static final int MAX_FILE_BYTES = 4_194_304;
    public static final int MAX_ARCHIVES = 4_096;
    @FunctionalInterface interface Writer { void write(Path file, String text) throws IOException; }
    private static final Set<String> ROOT_FIELDS = Set.of("schema-version", "items");
    private static final Set<String> ITEM_FIELDS = Set.of("title", "kind", "state", "references", "creator", "owner",
        "created-at", "updated-at", "revision", "notes");
    private static final Set<String> NOTE_FIELDS = Set.of("author", "at", "text");
    private final Path file;
    private final Path archiveDirectory;
    private final Clock clock;
    private final Writer writer;
    private Map<String, WorkItem> snapshot;
    private record ArchiveIndex(Set<String> ids, int count) {
        ArchiveIndex { ids = Set.copyOf(ids); }
    }
    private ArchiveIndex archives;

    public WorkboardStore(File file) throws IOException { this(file, Clock.systemUTC(), WorkboardStore::atomicWrite); }
    WorkboardStore(File file, Clock clock, Writer writer) throws IOException {
        this.file = file.toPath().toAbsolutePath().normalize();
        archiveDirectory = this.file.getParent().resolve("workboard-archive");
        this.clock = Objects.requireNonNull(clock);
        this.writer = Objects.requireNonNull(writer);
        snapshot = read(this.file);
        archives = readArchiveIndex();
    }
    public synchronized List<WorkItem> all() {
        return snapshot.values().stream().sorted(java.util.Comparator.comparing(WorkItem::updatedAt).reversed()
            .thenComparing(WorkItem::id)).toList();
    }
    public synchronized Optional<WorkItem> find(String id) { return Optional.ofNullable(snapshot.get(id)); }
    public synchronized int archivedCount() { return archives.count(); }
    public synchronized WorkItem create(String id, String title, WorkItem.Kind kind, UUID creator) throws IOException {
        if (snapshot.containsKey(id)) throw new IllegalArgumentException("That work-item ID is already in use.");
        ArchiveIndex index = readArchiveIndex();
        if (index.ids().contains(id)) throw new IllegalArgumentException("That ID belongs to an archived work item. Choose a new ID.");
        archives = index;
        if (snapshot.size() >= WorkItem.MAX_ITEMS) throw new IllegalArgumentException("The workboard is full (256 items).");
        Instant now = clock.instant();
        WorkItem item = new WorkItem(id, title, kind, WorkItem.State.OPEN, List.of(), creator, null, now, now, 1, List.of());
        return commit(item);
    }
    public synchronized WorkItem assign(String id, long revision, UUID owner) throws IOException {
        WorkItem previous = expected(id, revision);
        if (Objects.equals(previous.owner(), owner)) throw new IllegalArgumentException("That assignment is already set.");
        return commit(copy(previous, previous.state(), previous.references(), owner, previous.notes()));
    }
    public synchronized WorkItem transition(String id, long revision, WorkItem.State state) throws IOException {
        WorkItem previous = expected(id, revision);
        Objects.requireNonNull(state);
        if (previous.state() == state) throw new IllegalArgumentException("That work item is already " + state.name() + ".");
        return commit(copy(previous, state, previous.references(), previous.owner(), previous.notes()));
    }
    public synchronized WorkItem reference(String id, long revision, ContentRef reference) throws IOException {
        WorkItem previous = expected(id, revision);
        if (previous.references().contains(reference)) throw new IllegalArgumentException("That reference is already attached.");
        List<ContentRef> references = new ArrayList<>(previous.references()); references.add(reference);
        return commit(copy(previous, previous.state(), references, previous.owner(), previous.notes()));
    }
    public synchronized WorkItem unreference(String id, long revision, ContentRef reference) throws IOException {
        WorkItem previous = expected(id, revision);
        List<ContentRef> references = new ArrayList<>(previous.references());
        if (!references.remove(reference)) throw new IllegalArgumentException("That reference is not attached.");
        return commit(copy(previous, previous.state(), references, previous.owner(), previous.notes()));
    }
    public synchronized WorkItem note(String id, long revision, UUID author, String text) throws IOException {
        WorkItem previous = expected(id, revision);
        Instant now = timestamp(previous);
        List<WorkItem.Note> notes = new ArrayList<>(previous.notes());
        notes.add(new WorkItem.Note(author, now, text));
        if (notes.size() > WorkItem.MAX_NOTES) notes.removeFirst();
        return commit(new WorkItem(previous.id(), previous.title(), previous.kind(), previous.state(), previous.references(),
            previous.creator(), previous.owner(), previous.createdAt(), now, nextRevision(previous), notes));
    }
    /** Completed records are preserved first; only then is the active board replaced. */
    public synchronized WorkItem archive(String id, long revision) throws IOException {
        WorkItem item = expected(id, revision);
        if (item.state() != WorkItem.State.DONE) throw new IllegalArgumentException("Complete this work item before archiving it.");
        ArchiveIndex index = readArchiveIndex();
        Path record = archiveDirectory.resolve(item.id() + "-r" + item.revision() + ".yml");
        if (Files.exists(record, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(record) || !read(record).equals(Map.of(id, item)))
                throw new IOException("An archive with this revision already exists but does not match. It was not overwritten.");
        } else {
            if (index.count() >= MAX_ARCHIVES) throw new IllegalArgumentException("The archive is full (4,096 records). Staff must preserve it externally before further archiving.");
            writer.write(record, serialize(Map.of(id, item)));
            var ids = new java.util.HashSet<>(index.ids()); ids.add(id);
            index = new ArchiveIndex(ids, index.count() + 1);
        }
        // Reserve the ID even when the following active-board commit fails.
        archives = index;
        Map<String, WorkItem> next = new TreeMap<>(snapshot); next.remove(id);
        commitSnapshot(next);
        return item;
    }
    public synchronized void reload() throws IOException {
        Map<String, WorkItem> next = read(file); ArchiveIndex index = readArchiveIndex();
        snapshot = next; archives = index;
    }

    private WorkItem expected(String id, long revision) {
        WorkItem item = snapshot.get(id);
        if (item == null) throw new IllegalArgumentException("No work item named " + id + ".");
        if (item.revision() != revision) throw new IllegalStateException("This work item changed. Reopen it before editing.");
        return item;
    }
    private WorkItem copy(WorkItem item, WorkItem.State state, List<ContentRef> references, UUID owner, List<WorkItem.Note> notes) {
        return new WorkItem(item.id(), item.title(), item.kind(), state, references, item.creator(), owner,
            item.createdAt(), timestamp(item), nextRevision(item), notes);
    }
    private Instant timestamp(WorkItem item) {
        Instant now = clock.instant(); return now.isBefore(item.updatedAt()) ? item.updatedAt() : now;
    }
    private static long nextRevision(WorkItem item) {
        if (item.revision() == Long.MAX_VALUE) throw new IllegalStateException("Work-item revision limit reached.");
        return item.revision() + 1;
    }
    private WorkItem commit(WorkItem item) throws IOException {
        Map<String, WorkItem> next = new TreeMap<>(snapshot); next.put(item.id(), item);
        commitSnapshot(next);
        return item;
    }
    private void commitSnapshot(Map<String, WorkItem> next) throws IOException {
        String serialized = serialize(next);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IOException("The workboard exceeds its file-size limit.");
        writer.write(file, serialized);
        snapshot = Map.copyOf(next);
    }
    private Map<String, WorkItem> read(Path source) throws IOException {
        if (!Files.exists(source)) return Map.of();
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source) || Files.size(source) > MAX_FILE_BYTES)
            throw new IOException("Workboard file must be a regular file no larger than 4 MiB.");
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(16); options.setCodePointLimit(MAX_FILE_BYTES);
            Map<?, ?> root = mapping(new Yaml(new SafeConstructor(options)).load(Files.readString(source)), "workboard");
            fields(root, ROOT_FIELDS, "workboard");
            if (number(root.get("schema-version"), "schema-version") != 1) throw new IllegalArgumentException("Unsupported workboard schema.");
            Map<?, ?> records = mapping(root.get("items"), "items");
            if (records.size() > WorkItem.MAX_ITEMS) throw new IllegalArgumentException("At most 256 work items are supported.");
            Map<String, WorkItem> result = new TreeMap<>();
            for (var record : records.entrySet()) {
                String id = string(record.getKey(), "item ID");
                Map<?, ?> item = mapping(record.getValue(), id); fields(item, ITEM_FIELDS, id);
                List<ContentRef> references = new ArrayList<>();
                for (Object reference : list(item.get("references"), WorkItem.MAX_REFERENCES, "references"))
                    references.add(ContentRef.parse(string(reference, "reference")));
                List<WorkItem.Note> notes = new ArrayList<>();
                for (Object rawNote : list(item.get("notes"), WorkItem.MAX_NOTES, "notes")) {
                    Map<?, ?> note = mapping(rawNote, "note"); fields(note, NOTE_FIELDS, "note");
                    notes.add(new WorkItem.Note(uuid(note.get("author"), "note author"),
                        instant(note.get("at"), "note timestamp"), string(note.get("text"), "note text")));
                }
                String owner = string(item.get("owner"), "owner");
                result.put(id, new WorkItem(id, string(item.get("title"), "title"),
                    WorkItem.Kind.valueOf(string(item.get("kind"), "kind")), WorkItem.State.valueOf(string(item.get("state"), "state")),
                    references, uuid(item.get("creator"), "creator"), owner.isEmpty() ? null : uuid(owner, "owner"),
                    instant(item.get("created-at"), "created-at"), instant(item.get("updated-at"), "updated-at"),
                    number(item.get("revision"), "revision"), notes));
            }
            return Map.copyOf(result);
        } catch (IllegalArgumentException | NullPointerException | YAMLException failure) {
            throw new IOException("Invalid workboard; existing notes were not replaced: " + failure.getMessage(), failure);
        }
    }
    private ArchiveIndex readArchiveIndex() throws IOException {
        if (!Files.exists(archiveDirectory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return new ArchiveIndex(Set.of(), 0);
        if (Files.isSymbolicLink(archiveDirectory) || !Files.isDirectory(archiveDirectory))
            throw new IOException("Workboard archive must be an ordinary directory.");
        var ids = new java.util.HashSet<String>(); int count = 0;
        var pattern = java.util.regex.Pattern.compile("([a-z0-9][a-z0-9_-]{0,47})-r([1-9][0-9]{0,18})\\.yml");
        try (var files = Files.list(archiveDirectory)) {
            var iterator = files.iterator(); int inspected = 0;
            while (iterator.hasNext()) {
                Path record = iterator.next();
                if (++inspected > MAX_ARCHIVES + 32) throw new IOException("The workboard archive exceeds its directory inspection limit.");
                String name = record.getFileName().toString();
                // Interrupted atomic writes are never mistaken for completed archive records.
                if (name.endsWith(".pending")) continue;
                var match = pattern.matcher(name);
                if (!match.matches() || Files.isSymbolicLink(record) || !Files.isRegularFile(record))
                    throw new IOException("Unexpected archive entry " + name + "; nothing was removed.");
                try { Long.parseLong(match.group(2)); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid archived revision in " + name, invalid); }
                ids.add(match.group(1));
                if (++count > MAX_ARCHIVES) throw new IOException("The workboard archive exceeds 4,096 records.");
            }
        } catch (java.io.UncheckedIOException failure) { throw failure.getCause(); }
        return new ArchiveIndex(ids, count);
    }
    private static String serialize(Map<String, WorkItem> items) {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", 1); yaml.createSection("items");
        new TreeMap<>(items).forEach((id, item) -> {
            String path = "items." + id;
            yaml.set(path + ".title", item.title()); yaml.set(path + ".kind", item.kind().name());
            yaml.set(path + ".state", item.state().name()); yaml.set(path + ".references", item.references().stream().map(ContentRef::toString).toList());
            yaml.set(path + ".creator", item.creator().toString()); yaml.set(path + ".owner", item.owner() == null ? "" : item.owner().toString());
            yaml.set(path + ".created-at", item.createdAt().toString()); yaml.set(path + ".updated-at", item.updatedAt().toString());
            yaml.set(path + ".revision", item.revision());
            yaml.set(path + ".notes", item.notes().stream().map(note -> Map.of("author", note.author().toString(),
                "at", note.at().toString(), "text", note.text())).toList());
        });
        return yaml.saveToString();
    }
    private static Map<?, ?> mapping(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be a mapping."); return map;
    }
    private static void fields(Map<?, ?> value, Set<String> expected, String label) {
        if (!value.keySet().equals(expected)) throw new IllegalArgumentException(label + " has missing or unknown fields.");
    }
    private static List<?> list(Object value, int maximum, String label) {
        if (!(value instanceof List<?> list) || list.size() > maximum) throw new IllegalArgumentException(label + " exceeds its list limit.");
        return list;
    }
    private static String string(Object value, String label) {
        if (!(value instanceof String string)) throw new IllegalArgumentException(label + " must be text."); return string;
    }
    private static long number(Object value, String label) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long))
            throw new IllegalArgumentException(label + " must be an integer."); return ((Number) value).longValue();
    }
    private static UUID uuid(Object value, String label) {
        String text = string(value, label);
        if (!text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException(label + " must be a full UUID."); return UUID.fromString(text);
    }
    private static Instant instant(Object value, String label) {
        try { return Instant.parse(string(value, label)); }
        catch (java.time.format.DateTimeParseException failure) { throw new IllegalArgumentException(label + " must be an ISO UTC timestamp."); }
    }
    private static void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".pending");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
