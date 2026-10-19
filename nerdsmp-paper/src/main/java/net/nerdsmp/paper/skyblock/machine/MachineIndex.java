package net.nerdsmp.paper.skyblock.machine;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.skyblock.IslandService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Additive owner-owned index. Writes never replace island, economy, skill or inventory paths. */
public final class MachineIndex {
    public static final int LIMIT = 16;
    private final PlayerDataStore data;
    private final IslandService islands;
    private final String path;
    private final Map<Position, Entry> entries = new LinkedHashMap<>();
    private List<Entry> ordered = List.of();

    public MachineIndex(PlayerDataStore data, IslandService islands, String season) {
        if (season == null || !season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid season");
        this.data = Objects.requireNonNull(data); this.islands = Objects.requireNonNull(islands);
        path = "seasons." + season + ".skyblock-machines";
        Set<UUID> tokens = new HashSet<>();
        for (AccountId owner : data.knownAccounts()) {
            List<Entry> records = data.read(owner, yaml -> {
                if (!yaml.contains(path)) return List.of();
                ConfigurationSection section = yaml.getConfigurationSection(path);
                if (section == null || !section.isInt("schema") || section.getInt("schema") != 1)
                    throw new IllegalStateException("Invalid island machine schema for " + owner);
                if (!section.getKeys(false).equals(Set.of("schema", "entries")) || !section.isList("entries"))
                    throw new IllegalStateException("Invalid island machine record for " + owner);
                List<Entry> loaded = new ArrayList<>();
                for (Object raw : section.getList("entries")) {
                    if (!(raw instanceof String value)) throw new IllegalStateException("Invalid machine entry");
                    loaded.add(Entry.parse(owner, value));
                }
                return loaded;
            });
            if (records.size() > LIMIT) throw new IllegalStateException("Island machine limit exceeded");
            for (Entry entry : records) {
                requireTerritory(owner, entry.position());
                if (!tokens.add(entry.id()) || entries.putIfAbsent(entry.position(), entry) != null)
                    throw new IllegalStateException("Duplicate machine identity or position");
            }
        }
        ordered = List.copyOf(entries.values());
    }
    public Entry at(Position position) { return entries.get(position); }
    public List<Entry> entries() { return ordered; }
    public int count(AccountId owner) { return (int) entries.values().stream().filter(e -> e.owner().equals(owner)).count(); }
    public Entry add(AccountId actor, AccountId owner, Position position, MachineKind kind) {
        var island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("No island found."));
        if (actor == null || !island.member(actor)) throw new IllegalArgumentException("Only co-op members can place machines.");
        requireTerritory(owner, position);
        if (entries.containsKey(position)) throw new IllegalArgumentException("That block already has a machine record.");
        if (count(owner) >= LIMIT) throw new IllegalArgumentException("This island has all 16 machine spaces in use.");
        Entry next = new Entry(UUID.randomUUID(), owner, position, kind);
        List<Entry> ownerEntries = new ArrayList<>(owned(owner)); ownerEntries.add(next);
        commit(owner, ownerEntries); return next;
    }
    public void remove(Entry expected) {
        if (!expected.equals(entries.get(expected.position()))) throw new IllegalArgumentException("This machine changed. Try again.");
        ArrayList<Entry> next = new ArrayList<>(owned(expected.owner())); next.remove(expected);
        commit(expected.owner(), next);
    }
    private List<Entry> owned(AccountId owner) { return entries.values().stream().filter(e -> e.owner().equals(owner)).toList(); }
    private void requireTerritory(AccountId owner, Position position) {
        if (islands.at(position.x() >> 4, position.z() >> 4).filter(i -> i.owner().equals(owner)).isEmpty())
            throw new IllegalStateException("Machine is outside its owner's island");
    }
    private void commit(AccountId owner, List<Entry> next) {
        List<String> expected = owned(owner).stream().map(Entry::encode).sorted().toList();
        data.update(owner, yaml -> {
            // Another content/tool instance can share the canonical store. Refuse a stale
            // writer instead of losing its newly placed machines or replacing malformed data.
            if (!yaml.contains(path)) {
                if (!expected.isEmpty()) throw new IllegalStateException("Machine record disappeared; reload before editing");
            } else {
                ConfigurationSection section = yaml.getConfigurationSection(path);
                if (section == null || !section.getKeys(false).equals(Set.of("schema", "entries"))
                    || !section.isInt("schema") || section.getInt("schema") != 1 || !section.isList("entries"))
                    throw new IllegalStateException("Machine record changed or is malformed; refusing to replace it");
                ArrayList<String> stored = new ArrayList<>();
                for (Object raw : section.getList("entries")) {
                    if (!(raw instanceof String value)) throw new IllegalStateException("Machine record contains malformed entries");
                    stored.add(value);
                }
                Collections.sort(stored);
                if (!stored.equals(expected)) throw new IllegalStateException("Machine record changed; reload before editing");
            }
            yaml.set(path + ".schema", 1);
            yaml.set(path + ".entries", next.stream().map(Entry::encode).sorted().toList());
        });
        entries.entrySet().removeIf(e -> e.getValue().owner().equals(owner));
        next.forEach(e -> entries.put(e.position(), e)); ordered = List.copyOf(entries.values());
    }
    public record Position(UUID world, int x, int y, int z) {
        public Position {
            Objects.requireNonNull(world);
            if (x < -30_000_000 || x > 30_000_000 || z < -30_000_000 || z > 30_000_000 || y < -2048 || y > 2048)
                throw new IllegalArgumentException("Invalid machine position");
        }
    }
    public record Entry(UUID id, AccountId owner, Position position, MachineKind kind) {
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(position); Objects.requireNonNull(kind); }
        public String encode() { return id + "," + position.world() + "," + position.x() + "," + position.y() + "," + position.z() + "," + kind.id(); }
        public static Entry parse(AccountId owner, String text) {
            String[] parts = text.split(",", -1);
            if (parts.length != 6) throw new IllegalArgumentException("Invalid machine entry");
            return new Entry(UUID.fromString(parts[0]), owner, new Position(UUID.fromString(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4])), MachineKind.parse(parts[5]));
        }
    }
}
