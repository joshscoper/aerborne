package net.nerdsmp.paper.reach;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Every built thing in the Reach: towns, ruins, bridges, dungeons, chests. Each feature is a function that writes a
 * {@link ReachCanvas}; it runs once, the first time a chunk it covers generates, and the frozen plan is kept so the
 * other chunks read their slice. Feature footprints also tell the plant populator where not to grow trees.
 */
public final class ReachFeatures {
    /** A named point a player stands at: arrival, an NPC, a chest. */
    public record Spot(String id, double x, double y, double z, float yaw) { }

    /** What a feature builds: its blocks, and the named spots its NPCs, chests and doors use. */
    public record Built(ReachCanvas canvas, List<Spot> spots) { }

    /** A feature's reserved rectangle, which plants keep out of. */
    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        boolean touches(int chunkX, int chunkZ) {
            return (chunkX << 4) <= maxX && (chunkX << 4) + 15 >= minX && (chunkZ << 4) <= maxZ && (chunkZ << 4) + 15 >= minZ;
        }
    }

    public record Feature(String id, Footprint footprint, Function<ReachTerrain, Built> build) { }

    private final ReachTerrain terrain;
    private final List<Feature> features;
    private final Map<String, ReachCanvas.Frozen> built = new ConcurrentHashMap<>();
    private final Map<String, List<Spot>> spots = new ConcurrentHashMap<>();
    private final Map<String, BlockData> states = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public ReachFeatures(ReachTerrain terrain, List<Feature> features) {
        this.terrain = terrain;
        // What lines the roads is laid out around everything else, so it's added last and asks the rest for room.
        List<Feature> all = new ArrayList<>(features);
        all.addAll(ReachRoadside.features(terrain, this::builtOn));
        this.features = List.copyOf(all);
    }

    /** True where a feature other than the roadside builds at or beside a column. */
    private boolean builtOn(int x, int z) {
        for (Feature feature : features) {
            if (feature.id().startsWith("road_") || !feature.footprint().contains(x, z)) continue;
            ReachCanvas.Frozen plan = plan(feature);
            if (plan.occupies(x, z) || plan.occupies(x + 1, z) || plan.occupies(x - 1, z) || plan.occupies(x, z + 1) || plan.occupies(x, z - 1)) return true;
        }
        return false;
    }

    public List<Feature> features() { return features; }

    /**
     * Builds (once) and returns a feature's plan. Building one plan may need another's (the roadside asks the towns
     * where they stand), so each is built under its own lock rather than inside the map's compute.
     */
    public ReachCanvas.Frozen plan(Feature feature) {
        ReachCanvas.Frozen done = built.get(feature.id());
        if (done != null) return done;
        synchronized (locks.computeIfAbsent(feature.id(), id -> new Object())) {
            done = built.get(feature.id());
            if (done != null) return done;
            Built result = feature.build().apply(terrain);
            spots.put(feature.id(), List.copyOf(result.spots()));
            ReachCanvas.Frozen frozen = result.canvas().freeze();
            built.put(feature.id(), frozen);
            return frozen;
        }
    }

    /** Every named spot of every feature, prefixed with the feature's id ({@code highcrown.plaza}). */
    public Map<String, Spot> spots() {
        Map<String, Spot> all = new java.util.LinkedHashMap<>();
        for (Feature feature : features) {
            plan(feature);
            for (Spot spot : spots.getOrDefault(feature.id(), List.of())) all.put(feature.id() + "." + spot.id(), spot);
        }
        return all;
    }

    public java.util.Optional<Feature> feature(String id) {
        return features.stream().filter(f -> f.id().equals(id)).findFirst();
    }

    /** Writes every feature's blocks that fall in this chunk. */
    void place(int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
        for (Feature feature : features) {
            if (!feature.footprint().touches(chunkX, chunkZ)) continue;
            plan(feature).forEach(chunkX, chunkZ, cell ->
                data.setBlock(cell.lx(), cell.y(), cell.lz(), states.computeIfAbsent(cell.spec().state(), Bukkit::createBlockData)));
        }
    }

    /** True where a feature builds something in this column, or right beside it, so plants keep clear. */
    public boolean occupied(int x, int z) {
        for (Feature feature : features) {
            if (!feature.footprint().contains(x, z)) continue;
            ReachCanvas.Frozen plan = plan(feature);
            if (plan.occupies(x, z) || plan.occupies(x + 1, z) || plan.occupies(x - 1, z) || plan.occupies(x, z + 1) || plan.occupies(x, z - 1)) return true;
        }
        return false;
    }

    /** Where islanders land: the end of Highcrown's main pier until the docks are built, then the docks. */
    public Spot arrival() {
        Spot pier = spots().get("highcrown.pier_mid");
        if (pier != null) return new Spot("arrival", pier.x(), pier.y(), pier.z() + 30, 180F);
        ReachTerrain.Column column = terrain.column(0, 1040);
        return new Spot("arrival", 0.5, column.top() + 1, 1040.5, 180F);
    }

    /** All features covering this chunk, for tests. */
    List<Feature> covering(int chunkX, int chunkZ) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : features) if (f.footprint().touches(chunkX, chunkZ)) out.add(f);
        return out;
    }
}
