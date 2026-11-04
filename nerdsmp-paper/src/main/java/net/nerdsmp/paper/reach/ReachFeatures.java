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
    /** A point a player stands at: arrival, an NPC, a chest. */
    public record Spot(double x, double y, double z, float yaw) { }

    /** A feature's reserved rectangle, which plants keep out of. */
    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        boolean touches(int chunkX, int chunkZ) {
            return (chunkX << 4) <= maxX && (chunkX << 4) + 15 >= minX && (chunkZ << 4) <= maxZ && (chunkZ << 4) + 15 >= minZ;
        }
    }

    public record Feature(String id, Footprint footprint, Function<ReachTerrain, ReachCanvas> build) { }

    private final ReachTerrain terrain;
    private final List<Feature> features;
    private final Map<String, ReachCanvas.Frozen> built = new ConcurrentHashMap<>();
    private final Map<String, BlockData> states = new ConcurrentHashMap<>();

    public ReachFeatures(ReachTerrain terrain, List<Feature> features) {
        this.terrain = terrain;
        this.features = List.copyOf(features);
    }

    public List<Feature> features() { return features; }

    /** Builds (once) and returns a feature's plan. */
    public ReachCanvas.Frozen plan(Feature feature) {
        return built.computeIfAbsent(feature.id(), id -> feature.build().apply(terrain).freeze());
    }

    /** Writes every feature's blocks that fall in this chunk. */
    void place(int chunkX, int chunkZ, ChunkGenerator.ChunkData data) {
        for (Feature feature : features) {
            if (!feature.footprint().touches(chunkX, chunkZ)) continue;
            plan(feature).forEach(chunkX, chunkZ, cell ->
                data.setBlock(cell.lx(), cell.y(), cell.lz(), states.computeIfAbsent(cell.spec().state(), Bukkit::createBlockData)));
        }
    }

    /** True where a feature has reserved the ground. */
    public boolean occupied(int x, int z) {
        for (Feature feature : features) if (feature.footprint().contains(x, z)) return true;
        return false;
    }

    /** Where islanders land: the end of Highcrown's main pier until the docks are built, then the docks. */
    public Spot arrival() {
        ReachTerrain.Column column = terrain.column(0, 1040);
        return new Spot(0.5, column.top() + 1, 1040.5, 180F);
    }

    /** All features covering this chunk, for tests. */
    List<Feature> covering(int chunkX, int chunkZ) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : features) if (f.footprint().touches(chunkX, chunkZ)) out.add(f);
        return out;
    }
}
