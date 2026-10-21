package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Dimension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Void generator for expedition templates and their run copies. A sample expedition's build plan is
 * written chunk by chunk as the chunk generates; a blank template gets a small starter platform.
 * Copied run worlds use the same generator, so any chunk staff never visited still matches.
 */
public final class TemplateGenerator extends ChunkGenerator {
    public static final int PLATFORM_Y = 64;
    private final BuildCanvas.Plan plan;
    private final Biome biome;
    private final Map<BuildCanvas.BlockSpec, BlockData> states = new ConcurrentHashMap<>();

    public TemplateGenerator(BuildCanvas.Plan plan, String biomeKey, Dimension dimension) {
        this.plan = plan;
        this.biome = resolve(biomeKey != null ? biomeKey : switch (dimension) {
            case NETHER -> "nether_wastes";
            case END -> "the_end";
            case OVERWORLD -> "plains";
        });
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (plan == null) {
            if (chunkX == 0 && chunkZ == 0)
                for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++)
                    data.setBlock(x, PLATFORM_Y - 1, z, x == 4 && z == 4 ? Material.LODESTONE : Material.POLISHED_ANDESITE);
            return;
        }
        for (var placement : plan.chunk(chunkX, chunkZ)) {
            var cell = placement.cell();
            var spec = placement.block();
            if (spec.properties().isEmpty()) data.setBlock(cell.x() & 15, cell.y(), cell.z() & 15, spec.material());
            else data.setBlock(cell.x() & 15, cell.y(), cell.z() & 15,
                states.computeIfAbsent(spec, key -> Bukkit.createBlockData(key.state())));
        }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 4.5, PLATFORM_Y, 4.5); }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo world, int x, int y, int z) { return biome; }
            @Override public List<Biome> getBiomes(WorldInfo world) { return List.of(biome); }
        };
    }

    static Biome resolve(String key) {
        return Objects.requireNonNull(io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.BIOME).get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT))),
            () -> "Unknown biome " + key);
    }
}
