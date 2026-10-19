package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import java.util.*;

/** Persistent collector index, including unloaded chunks. The island world is season-owned. */
public final class IslandCollectors {
    public static final int MAX_PER_ISLAND = 14;
    private final PlayerDataStore data;
    private final IslandService islands;
    private final String path;
    private final Map<AccountId, Set<Position>> byOwner = new HashMap<>();
    private final Map<Position, AccountId> owners = new LinkedHashMap<>();
    private List<Position> positions = List.of();
    public IslandCollectors(PlayerDataStore data, IslandService islands, String season) {
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid season");
        this.data = data; this.islands = islands; path = "seasons." + season + ".island-collectors";
        for (AccountId owner : data.knownAccounts()) {
            Set<Position> loaded = data.read(owner, yaml -> {
                Set<Position> result = new LinkedHashSet<>();
                if (!yaml.contains(path)) return result;
                if (!yaml.isList(path)) throw new IllegalStateException("Malformed island collector index");
                for (Object raw : yaml.getList(path)) {
                    if (!(raw instanceof String value)) throw new IllegalStateException("Malformed collector position");
                    Position position = Position.parse(value);
                    if (!result.add(position)) throw new IllegalStateException("Duplicate collector position");
                }
                return result;
            });
            if (loaded.size() > MAX_PER_ISLAND) throw new IllegalStateException("Island collector limit exceeded");
            for (Position position : loaded) {
                if (islands.at(position.x() >> 4, position.z() >> 4).filter(i -> i.owner().equals(owner)).isEmpty()
                    || owners.keySet().stream().anyMatch(p -> p.sameChunk(position))) throw new IllegalStateException("Invalid collector territory or duplicate chunk");
                owners.put(position, owner);
            }
            if (!loaded.isEmpty()) byOwner.put(owner, Set.copyOf(loaded));
        }
        positions = List.copyOf(owners.keySet());
    }
    public List<Position> positions() { return positions; }
    public AccountId owner(Position position) { return owners.get(position); }
    public int count(AccountId owner) { return byOwner.getOrDefault(owner, Set.of()).size(); }
    public void add(AccountId actor, AccountId owner, Position position, int limit) {
        var island = islands.owned(owner).orElseThrow();
        if (!island.member(actor)) throw new IllegalArgumentException("Only co-op members can place collectors.");
        if (!IslandGrid.contains(island.slot(), island.tier(), position.x() >> 4, position.z() >> 4)) throw new IllegalArgumentException("Place the collector on your island.");
        if (limit < 1 || count(owner) >= Math.min(MAX_PER_ISLAND, limit)) throw new IllegalArgumentException("Train Collection network or Freight network to unlock more chunk hoppers.");
        if (owners.keySet().stream().anyMatch(p -> p.sameChunk(position))) throw new IllegalArgumentException("This chunk already has a collector.");
        Set<Position> next = new HashSet<>(byOwner.getOrDefault(owner, Set.of())); next.add(position); commit(owner, next);
    }
    public void remove(Position position) {
        AccountId owner = owners.get(position); if (owner == null) return;
        Set<Position> next = new HashSet<>(byOwner.get(owner)); next.remove(position); commit(owner, next);
    }
    private void commit(AccountId owner, Set<Position> next) {
        data.update(owner, yaml -> yaml.set(path, next.stream().map(Position::encode).sorted().toList()));
        byOwner.getOrDefault(owner, Set.of()).forEach(owners::remove);
        byOwner.put(owner, Set.copyOf(next)); next.forEach(p -> owners.put(p, owner)); positions = List.copyOf(owners.keySet());
    }
    public record Position(int x, int y, int z) {
        public Position { if (y < -64 || y >= 320) throw new IllegalArgumentException("Invalid collector height"); }
        public boolean sameChunk(Position other) { return x >> 4 == other.x >> 4 && z >> 4 == other.z >> 4; }
        public String encode() { return x + "," + y + "," + z; }
        public static Position parse(String text) {
            String[] parts = text.split(",", -1); if (parts.length != 3) throw new IllegalArgumentException("Invalid collector position");
            return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
    }
}
