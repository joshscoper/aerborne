package net.nerdsmp.paper.engine.mob;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Bounded validated behavior snapshots, not a mutable catalog cache. Publishing never replaces a live entry. */
public final class MobSnapshotCache<T> {
    public static final int MAX_ENTRIES = 4096;
    private record Entry<T>(String id, String type, T value) { }
    private final int capacity;
    private final LinkedHashMap<UUID, Entry<T>> entries = new LinkedHashMap<>(16, 0.75F, true);

    public MobSnapshotCache(int capacity) {
        if (capacity < 1 || capacity > MAX_ENTRIES) throw new IllegalArgumentException("Snapshot capacity must be 1–4096.");
        this.capacity = capacity;
    }
    public synchronized Optional<T> find(UUID uuid, String id, String type) {
        Entry<T> entry = entries.get(uuid);
        if (entry == null) return Optional.empty();
        if (!entry.id.equals(id) || !entry.type.equals(type)) { entries.remove(uuid); return Optional.empty(); }
        return Optional.of(entry.value);
    }
    public synchronized void put(UUID uuid, String id, String type, T value) {
        entries.put(Objects.requireNonNull(uuid), new Entry<>(Objects.requireNonNull(id), Objects.requireNonNull(type), Objects.requireNonNull(value)));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    public synchronized void remove(UUID uuid) { entries.remove(uuid); }
    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }
    public synchronized void retain(Predicate<UUID> present) { entries.keySet().removeIf(uuid -> !present.test(uuid)); }
}
