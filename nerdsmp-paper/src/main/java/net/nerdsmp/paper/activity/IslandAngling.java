package net.nerdsmp.paper.activity;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import java.util.*;

/** Seasonal co-op fishery. Only the accepted-catch handler records work; there are no paid upgrades. */
public final class IslandAngling {
    private final PlayerDataStore data;
    private final String path;
    public IslandAngling(PlayerDataStore data, String season) {
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid angling season");
        this.data = data; path = "seasons." + season + ".island-angling";
    }
    public record Progress(long catches, Set<String> habitats, List<UUID> receipts) {
        public Progress {
            habitats = Set.copyOf(habitats); receipts = List.copyOf(receipts);
            if (catches < 0 || catches > 1_000_000 || catches < receipts.size() || catches < habitats.size() || habitats.size() > 5 || receipts.size() > 256 || new HashSet<>(receipts).size() != receipts.size()
                || habitats.stream().anyMatch(h -> !Set.of("RIVER", "OCEAN", "SWAMP", "COLD", "INLAND").contains(h))) throw new IllegalArgumentException("Invalid island fishery progress");
        }
        public int tier() { return catches >= 1000 && habitats.size() >= 5 ? 3 : catches >= 400 && habitats.size() >= 3 ? 2 : catches >= 100 ? 1 : 0; }
        public int luck() { return switch (tier()) { case 1 -> 2; case 2 -> 4; case 3 -> 8; default -> 0; }; }
        public Progress caught(UUID receipt, String habitat) {
            if (receipts.contains(receipt)) return this;
            List<UUID> history = new ArrayList<>(receipts); history.add(Objects.requireNonNull(receipt)); if (history.size() > 256) history.removeFirst();
            Set<String> waters = new HashSet<>(habitats); waters.add(habitat);
            return new Progress(Math.min(1_000_000, catches + 1), waters, history);
        }
    }
    public Progress progress(AccountId owner) { return data.read(owner, yaml -> read(yaml)); }
    private Progress read(org.bukkit.configuration.file.YamlConfiguration yaml) {
        if (!yaml.contains(path)) return new Progress(0, Set.of(), List.of());
        var root = yaml.getConfigurationSection(path);
        if (root == null || !root.getKeys(false).equals(Set.of("schema", "catches", "habitats", "receipts")) || !root.isInt("schema") || root.getInt("schema") != 1 || !root.isLong("catches") && !root.isInt("catches")
            || !root.isList("habitats") || !root.isList("receipts")) throw new IllegalStateException("Invalid island fishery data; refusing to overwrite");
        if (root.getList("habitats").stream().anyMatch(value -> !(value instanceof String))
            || root.getList("receipts").stream().anyMatch(value -> !(value instanceof String))
            || new HashSet<>(root.getStringList("habitats")).size() != root.getStringList("habitats").size()) throw new IllegalStateException("Invalid island fishery list");
        return new Progress(root.getLong("catches"), new HashSet<>(root.getStringList("habitats")), root.getStringList("receipts").stream().map(UUID::fromString).toList());
    }
    public void record(AccountId owner, UUID hook, String habitat) {
        data.update(owner, yaml -> {
            Progress before = read(yaml), next = before.caught(hook, habitat);
            yaml.set(path + ".schema", 1); yaml.set(path + ".catches", next.catches());
            yaml.set(path + ".habitats", new ArrayList<>(new TreeSet<>(next.habitats())));
            yaml.set(path + ".receipts", next.receipts().stream().map(UUID::toString).toList());
        });
    }
}
