package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.List;
import java.util.Random;

/** Pure chunk-data generation: no neighboring chunks, live terrain reads or Bukkit inventories. */
public final class IslandGenerator extends ChunkGenerator {
    private final java.util.function.Function<Material, org.bukkit.block.data.BlockData> blockData;
    public static final int CURRENT_TEMPLATE = 3;
    private final int template;
    public IslandGenerator() { this(CURRENT_TEMPLATE, Material::createBlockData); }
    public IslandGenerator(int template) { this(template, Material::createBlockData); }
    IslandGenerator(java.util.function.Function<Material, org.bukkit.block.data.BlockData> blockData) { this(CURRENT_TEMPLATE, blockData); }
    IslandGenerator(int template, java.util.function.Function<Material, org.bukkit.block.data.BlockData> blockData) {
        if (template < 1 || template > CURRENT_TEMPLATE) throw new IllegalArgumentException("Unknown island template");
        this.template = template; this.blockData = blockData;
    }
    @Override public org.bukkit.Location getFixedSpawnLocation(org.bukkit.World world, Random random) {
        return new org.bukkit.Location(world, 8.5, IslandGrid.SURFACE_Y + 1, 8.5);
    }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public int getBaseHeight(WorldInfo world, Random random, int x, int z, HeightMap heightMap) {
        return world.getMinHeight();
    }
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo world, int x, int y, int z) { return Biome.PLAINS; }
            @Override public List<Biome> getBiomes(WorldInfo world) { return List.of(Biome.PLAINS); }
        };
    }
    @Override public void generateNoise(WorldInfo world, Random random, int chunkX, int chunkZ, ChunkData data) {
        if (template == 1) generateLegacy(chunkX, chunkZ, data);
        else generateHearth(chunkX, chunkZ, data);
    }
    private void generateHearth(int chunkX, int chunkZ, ChunkData data) {
        int slot = IslandGrid.slotAt(chunkX, chunkZ);
        if (slot < 0 || Math.abs(chunkX - IslandGrid.chunkX(slot)) > 1 || Math.abs(chunkZ - IslandGrid.chunkZ(slot)) > 1) return;
        int y = IslandGrid.SURFACE_Y;
        if (y - 8 < data.getMinHeight() || y + 8 >= data.getMaxHeight()) throw new IllegalStateException("Island height outside world");
        int originX = IslandGrid.chunkX(slot) * 16 + 8, originZ = IslandGrid.chunkZ(slot) * 16 + 8;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int dx = chunkX * 16 + x - originX, dz = chunkZ * 16 + z - originZ;
            for (int depth = 0; depth <= 8; depth++) {
                double radius = 11.25 - depth * .95;
                if (dx * dx + dz * dz > radius * radius) continue;
                Material material = depth == 0 ? Material.GRASS_BLOCK : depth <= 2 ? Material.DIRT
                    : Math.floorMod(dx * 17 + dz * 31 + depth, 7) == 0 ? Material.ANDESITE : Material.STONE;
                data.setBlock(x, y - depth, z, material);
            }
        }
        // Local template coordinates are clipped to the supplied chunk; no live world reads.
        java.util.function.Consumer<Placement> put = p -> {
            int x = originX + p.x - chunkX * 16, z = originZ + p.z - chunkZ * 16;
            if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                if(template>=3 && p.material==Material.WATER && p.x==0 && p.y==0 && p.z==-4) {
                    var outlet=(org.bukkit.block.data.Levelled)blockData.apply(Material.WATER);
                    outlet.setLevel(1); // The drain receives flowing water, never a second source.
                    data.setBlock(x,y,z,outlet);
                } else data.setBlock(x, y + p.y, z, p.material);
            }
        };
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) put.accept(new Placement(x, 0, z, Material.POLISHED_ANDESITE));
        put.accept(new Placement(0, 0, 0, Material.BEDROCK));
        for (int step = 2; step <= 6; step++) {
            put.accept(new Placement(-step, 0, 0, Material.COARSE_DIRT));
            put.accept(new Placement(step, 0, 0, Material.COARSE_DIRT));
            put.accept(new Placement(0, 0, step, Material.COARSE_DIRT));
        }
        // West: starter crops, a shared water source and a compact workbench.
        for (int x = -7; x <= -3; x++) for (int z = 2; z <= 4; z++) {
            put.accept(new Placement(x, 0, z, Material.FARMLAND));
            put.accept(new Placement(x, 1, z, z == 2 ? Material.WHEAT : z == 3 ? Material.CARROTS : Material.POTATOES));
        }
        put.accept(new Placement(-5, 0, 3, Material.WATER)); put.accept(new Placement(-5, 1, 3, Material.AIR));
        put.accept(new Placement(-5, 1, -1, Material.CRAFTING_TABLE));
        put.accept(new Placement(-6, 1, -1, Material.FURNACE));
        put.accept(new Placement(-7, 1, -1, Material.BARREL)); // Empty storage, not a repeatable kit.
        for (int z = 6; z <= 7; z++) for (int x = -4; x <= -3; x++) put.accept(new Placement(x, 0, z, Material.WATER));
        put.accept(new Placement(-5, 1, 6, Material.SUGAR_CANE));
        put.accept(new Placement(-7, 0, -5, Material.SAND)); put.accept(new Placement(-7, 1, -5, Material.CACTUS));
        put.accept(new Placement(-6, 1, 6, Material.PUMPKIN)); put.accept(new Placement(-6, 1, 7, Material.MELON));
        // North: familiar mining block, contained fluids, and a guard over the lava.
        for (int x = -2; x <= 3; x++) for (int z = -5; z <= -3; z++) put.accept(new Placement(x, 0, z, Material.STONE_BRICKS));
        put.accept(new Placement(-1, 0, -4, Material.WATER)); put.accept(new Placement(0, 0, -4, Material.WATER));
        put.accept(new Placement(0, -1, -4, Material.AIR)); put.accept(new Placement(1, 0, -4, Material.COBBLESTONE));
        put.accept(new Placement(2, 0, -4, Material.LAVA)); put.accept(new Placement(2, 1, -4, Material.IRON_BARS));
        // East: an oak and a guaranteed spare sapling. Canopy blocks keep normal decay.
        var leaves = (org.bukkit.block.data.type.Leaves) blockData.apply(Material.OAK_LEAVES); leaves.setDistance(1);
        for (int dx = 4; dx <= 8; dx++) for (int dz = 3; dz <= 7; dz++) for (int h = 4; h <= 5; h++) {
            if (h == 5 && (dx == 4 || dx == 8 || dz == 3 || dz == 7)) continue;
            int x = originX + dx - chunkX * 16, z = originZ + dz - chunkZ * 16;
            if (x >= 0 && x < 16 && z >= 0 && z < 16) data.setBlock(x, y + h, z, leaves);
        }
        for (int h = 1; h <= 4; h++) put.accept(new Placement(6, h, 5, Material.OAK_LOG));
        put.accept(new Placement(5, 1, 2, Material.OAK_SAPLING));
        for (int[] light : new int[][]{{-5, -2}, {4, -1}, {1, 6}}) {
            put.accept(new Placement(light[0], 1, light[1], Material.OAK_FENCE));
            put.accept(new Placement(light[0], 2, light[1], Material.LANTERN));
        }
    }
    private record Placement(int x, int y, int z, Material material) { }
    private void generateLegacy(int chunkX, int chunkZ, ChunkData data) {
        int slot = IslandGrid.slotAt(chunkX, chunkZ);
        if (slot < 0 || chunkX != IslandGrid.chunkX(slot) || chunkZ != IslandGrid.chunkZ(slot)) return;
        int y = IslandGrid.SURFACE_Y;
        if (y - 4 < data.getMinHeight() || y + 8 >= data.getMaxHeight()) throw new IllegalStateException("Island height outside world");
        for (int x = 3; x <= 13; x++) for (int z = 3; z <= 13; z++) {
            if (Math.abs(x - 8) + Math.abs(z - 8) > 8) continue;
            data.setBlock(x, y - 2, z, Material.STONE);
            data.setBlock(x, y - 1, z, Material.DIRT);
            data.setBlock(x, y, z, Material.GRASS_BLOCK);
        }
        for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) data.setBlock(x, y, z, Material.BEDROCK);
        var leaves = (org.bukkit.block.data.type.Leaves) blockData.apply(Material.OAK_LEAVES);
        leaves.setDistance(1); // Preserve the starter canopy until normal updates follow log removal.
        for (int x = 10; x <= 14; x++) for (int z = 9; z <= 13; z++) {
            data.setBlock(x, y + 4, z, leaves);
            if (x >= 11 && x <= 13 && z >= 10 && z <= 12) data.setBlock(x, y + 5, z, leaves);
        }
        for (int h = 1; h <= 4; h++) data.setBlock(12, y + h, 11, Material.OAK_LOG);
        data.setBlock(4, y, 7, Material.WATER);
        data.setBlock(4, y, 8, Material.WATER);
        data.setBlock(3, y + 1, 7, Material.SUGAR_CANE);
        data.setBlock(5, y, 6, Material.SAND);
        data.setBlock(5, y + 1, 6, Material.CACTUS);
        data.setBlock(6, y + 1, 11, Material.PUMPKIN);
        data.setBlock(5, y + 1, 10, Material.MELON);
        data.setBlock(5, y, 8, Material.FARMLAND);
        data.setBlock(5, y + 1, 8, Material.WHEAT);
        data.setBlock(6, y, 8, Material.FARMLAND);
        data.setBlock(6, y + 1, 8, Material.CARROTS);
        // A contained, working generator. Break (9,96,4); water/lava reform it.
        data.setBlock(7, y, 4, Material.WATER);
        data.setBlock(8, y, 4, Material.WATER);
        data.setBlock(8, y - 1, 4, Material.AIR); // Water drains rather than reaching the lava source.
        data.setBlock(9, y, 4, Material.COBBLESTONE);
        data.setBlock(10, y, 4, Material.LAVA);
    }
}
