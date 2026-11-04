package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.reach.ReachTerrain.Column;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Generates the Wardens' Reach: terrain from {@link ReachTerrain}, painted by {@link ReachSurface}, then built
 * features, then plants from {@link ReachFlora}. Vanilla noise, caves, decorations, structures and mob spawning are
 * all off, so nothing appears that the plan didn't put there. Every call is a pure function of the coordinates,
 * which keeps worker-thread generation safe and the world reproducible from code.
 */
public final class ReachGenerator extends ChunkGenerator {
    /** Bump when the generated world changes; a new revision gets a new world rather than regenerating in place. */
    public static final int REVISION = 1;
    private final ReachTerrain terrain;
    private final ReachSurface surface;
    private final ReachFeatures features;
    private final BlockData farmland, fallingWater, cloud;
    private final ReachBiomes biomes;

    public ReachGenerator(ReachTerrain terrain, ReachFeatures features) {
        this.terrain = terrain;
        this.surface = new ReachSurface(terrain);
        this.features = features;
        this.farmland = Bukkit.createBlockData("minecraft:farmland[moisture=7]");
        this.fallingWater = Bukkit.createBlockData("minecraft:water[level=8]");
        this.cloud = Material.WHITE_CONCRETE.createBlockData();
        this.biomes = new ReachBiomes(terrain);
    }

    public ReachTerrain terrain() { return terrain; }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int baseX = chunkX << 4, baseZ = chunkZ << 4;
        // An 18x18 grid of columns, so slopes at the chunk's edge see their neighbours.
        Column[] grid = new Column[18 * 18];
        for (int dz = -1; dz <= 16; dz++) for (int dx = -1; dx <= 16; dx++)
            grid[(dz + 1) * 18 + dx + 1] = terrain.column(baseX + dx, baseZ + dz);
        int minY = data.getMinHeight();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = baseX + lx, z = baseZ + lz;
            Column c = grid[(lz + 1) * 18 + lx + 1];
            int clouds = terrain.cloud(x, z);
            for (int i = 0; i < clouds; i++) data.setBlock(lx, ReachTerrain.CLOUD_Y + i, lz, cloud);
            if (!c.land()) {
                if (c.fall() != Integer.MIN_VALUE)
                    for (int y = c.fall(); y >= minY; y--) data.setBlock(lx, y, lz, fallingWater);
                continue;
            }
            int slope = slope(grid, lx + 1, lz + 1, c.top());
            double p = terrain.patch(x, z, 0.05);
            data.setBlock(lx, c.bottom(), lz, surface.underside(c, p));
            for (int y = c.bottom() + 1; y < c.top() - 3; y++) data.setBlock(lx, y, lz, surface.rock(x, y, z, c.zone()));
            for (int d = 3; d >= 1; d--) {
                int y = c.top() - d;
                if (y > c.bottom()) data.setBlock(lx, y, lz, surface.sub(c, d, slope));
            }
            Material top = surface.top(x, z, c, slope);
            if (top == Material.FARMLAND) data.setBlock(lx, c.top(), lz, farmland);
            else data.setBlock(lx, c.top(), lz, top);
            if (c.wet()) for (int y = c.top() + 1; y <= c.water(); y++) data.setBlock(lx, y, lz, Material.WATER);
        }
        features.place(chunkX, chunkZ, data);
    }

    private static int slope(Column[] grid, int gx, int gz, int top) {
        int worst = 0;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            Column n = grid[(gz + d[1]) * 18 + gx + d[0]];
            if (n.land()) worst = Math.max(worst, top - n.top());
            else worst = Math.max(worst, 2);
        }
        return worst;
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new ReachFlora(terrain, features));
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        ReachFeatures.Spot arrival = features.arrival();
        return new Location(world, arrival.x(), arrival.y(), arrival.z(), arrival.yaw(), 0F);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) { return biomes; }

    /** Biomes give each zone its grass tint, fog, ambience and music. */
    static final class ReachBiomes extends BiomeProvider {
        private final ReachTerrain terrain;
        private final Biome plains, meadow, taiga, darkForest, birch, swamp, river, windswept, stony, jagged, badlands, basalt, cherry;
        private final List<Biome> all;

        ReachBiomes(ReachTerrain terrain) {
            this.terrain = terrain;
            plains = biome("plains"); meadow = biome("meadow"); taiga = biome("old_growth_spruce_taiga"); darkForest = biome("dark_forest");
            birch = biome("birch_forest"); swamp = biome("mangrove_swamp"); river = biome("river"); windswept = biome("windswept_hills");
            stony = biome("stony_peaks"); jagged = biome("jagged_peaks"); badlands = biome("badlands"); basalt = biome("basalt_deltas");
            cherry = biome("cherry_grove");
            all = List.of(plains, meadow, taiga, darkForest, birch, swamp, river, windswept, stony, jagged, badlands, basalt, cherry);
        }

        private static Biome biome(String key) {
            return java.util.Objects.requireNonNull(io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.BIOME).get(org.bukkit.NamespacedKey.minecraft(key)), key);
        }

        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return switch (terrain.zoneAt(x, z)) {
                case HIGHCROWN, GOLDENREACH, SKY -> plains;
                case BARROWDOWNS -> meadow;
                case THORNWOOD -> terrain.patch(x, z, 0.004) > 0.25 ? darkForest : taiga;
                case MISTMERE -> terrain.lakeRadius(x, z) < 1.0 ? river : x > -60 && z > -760 ? swamp : birch;
                case IRONSPINE -> y > 182 ? jagged : y > 158 ? stony : windswept;
                case SUNSCAR -> badlands;
                case SCAR -> basalt;
                case ARCHIVE -> cherry;
            };
        }

        @Override public List<Biome> getBiomes(WorldInfo info) { return all; }
    }
}
