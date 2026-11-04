package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.reach.ReachTerrain.Column;
import net.nerdsmp.paper.reach.ReachTerrain.Zone;
import net.nerdsmp.paper.reach.ReachTreeGen.Species;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plants the Reach. Every tree is one of {@link ReachTreeGen}'s hand-shaped species (no vanilla trees anywhere),
 * set on a jittered grid per zone so trees keep their spacing and gather into groves where the grove noise is high.
 * Under them go bushes, and over the open ground grass and flowers grow in drifts, the way a meadow does, rather
 * than as an even speckle. Crops, hedges and hay belong to Goldenreach's field plots. Everything keeps off roads,
 * banks, the lip and anything a build occupies.
 */
final class ReachFlora extends BlockPopulator {
    /** One layer of planting: a grid spacing, how likely a cell is planted in and out of groves, and its species. */
    private record Layer(int cell, double inGrove, double outGrove, double groveFrom, List<Species> species, int[] weights) { }

    private static final Map<Zone, List<Layer>> LAYERS = Map.of(
        Zone.GOLDENREACH, List.of(
            new Layer(20, 0.9, 0.10, 0.30, List.of(Species.OAK, Species.SPRUCE, Species.BIRCH), new int[] {5, 3, 2}),
            new Layer(7, 0.45, 0.06, 0.10, List.of(Species.BUSH), new int[] {1})),
        Zone.HIGHCROWN, List.of(
            new Layer(24, 0.6, 0.05, 0.35, List.of(Species.OAK), new int[] {1}),
            new Layer(8, 0.3, 0.05, 0.20, List.of(Species.BUSH), new int[] {1})),
        Zone.BARROWDOWNS, List.of(
            new Layer(24, 0.8, 0.04, 0.40, List.of(Species.PINE, Species.SPRUCE, Species.OAK), new int[] {4, 3, 1}),
            new Layer(9, 0.35, 0.08, 0.10, List.of(Species.BUSH), new int[] {1})),
        Zone.THORNWOOD, List.of(
            new Layer(17, 0.85, 0.55, 0.00, List.of(Species.GIANT), new int[] {1}),
            new Layer(9, 0.65, 0.45, 0.00, List.of(Species.SPRUCE, Species.OAK, Species.BIRCH), new int[] {4, 3, 2}),
            new Layer(6, 0.5, 0.35, 0.00, List.of(Species.BUSH), new int[] {1})),
        Zone.MISTMERE, List.of(
            new Layer(16, 0.75, 0.10, 0.20, List.of(Species.BIRCH, Species.OAK, Species.WILLOW), new int[] {4, 3, 2}),
            new Layer(8, 0.35, 0.08, 0.10, List.of(Species.BUSH), new int[] {1})),
        Zone.IRONSPINE, List.of(
            new Layer(13, 0.8, 0.15, 0.00, List.of(Species.SPRUCE, Species.PINE), new int[] {3, 2}),
            new Layer(9, 0.3, 0.05, 0.10, List.of(Species.BUSH), new int[] {1})),
        Zone.SUNSCAR, List.of(
            new Layer(30, 0.5, 0.15, 0.10, List.of(Species.ACACIA, Species.DEAD), new int[] {3, 1})),
        Zone.SCAR, List.of(
            new Layer(22, 0.5, 0.2, 0.00, List.of(Species.DEAD), new int[] {1})),
        Zone.ARCHIVE, List.of(
            new Layer(18, 0.8, 0.3, 0.10, List.of(Species.CHERRY, Species.BIRCH), new int[] {4, 1}),
            new Layer(8, 0.3, 0.1, 0.10, List.of(Species.BUSH), new int[] {1}))
    );

    private final ReachTerrain terrain;
    private final ReachFeatures features;
    private final Map<String, BlockData> states = new ConcurrentHashMap<>();
    private final BlockData grassLower, grassUpper, fernLower, fernUpper, lilacLower, lilacUpper, peonyLower, peonyUpper,
        wheatRipe, wheatGreen, snow, carrots, potatoes, beetroots, hedge, hay;

    ReachFlora(ReachTerrain terrain, ReachFeatures features) {
        this.terrain = terrain;
        this.features = features;
        grassLower = data("minecraft:tall_grass[half=lower]");
        grassUpper = data("minecraft:tall_grass[half=upper]");
        fernLower = data("minecraft:large_fern[half=lower]");
        fernUpper = data("minecraft:large_fern[half=upper]");
        lilacLower = data("minecraft:lilac[half=lower]");
        lilacUpper = data("minecraft:lilac[half=upper]");
        peonyLower = data("minecraft:peony[half=lower]");
        peonyUpper = data("minecraft:peony[half=upper]");
        wheatRipe = data("minecraft:wheat[age=7]");
        wheatGreen = data("minecraft:wheat[age=5]");
        snow = data("minecraft:snow[layers=1]");
        carrots = data("minecraft:carrots[age=7]");
        potatoes = data("minecraft:potatoes[age=7]");
        beetroots = data("minecraft:beetroots[age=3]");
        hedge = data("minecraft:oak_leaves[persistent=true]");
        hay = data("minecraft:hay_block[axis=y]");
    }

    private BlockData data(String state) { return states.computeIfAbsent(state, Bukkit::createBlockData); }

    @Override
    public void populate(WorldInfo info, Random ignored, int chunkX, int chunkZ, LimitedRegion region) {
        trees(chunkX, chunkZ, region);
        int baseX = chunkX << 4, baseZ = chunkZ << 4;
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int x = baseX + lx, z = baseZ + lz;
            Column c = terrain.column(x, z);
            if (!c.land() || c.wet()) continue;
            int y = c.top() + 1;
            if (!region.isInRegion(x, y, z) || region.getType(x, y, z) != Material.AIR) continue;
            Material ground = region.getType(x, c.top(), z);
            long hash = hash(x, z);
            double roll = (hash & 0xFFFF) / 65536.0, roll2 = ((hash >>> 16) & 0xFFFF) / 65536.0;
            if (ground == Material.FARMLAND) {
                region.setBlockData(x, y, z, switch (terrain.field(x, z)) {
                    case 2 -> wheatGreen;
                    case 3 -> carrots;
                    case 4 -> potatoes;
                    case 5 -> beetroots;
                    default -> terrain.patch(x, z, 0.08) > 0.55 ? wheatGreen : wheatRipe;
                });
                continue;
            }
            if (ground == Material.SNOW_BLOCK) { region.setBlockData(x, y, z, snow); continue; }
            if (c.road() > 0.05 || (c.river() >= 0 && c.river() < 3) || c.edge() < 3 || features.occupied(x, z)) continue;
            if (c.zone() == Zone.GOLDENREACH && terrain.field(x, z) == 0 && ground == Material.GRASS_BLOCK) {
                // A hedge between plots, broken by gaps so the fields can be walked.
                if ((Math.floorMod(x * 7 + z * 3, 23)) > 2) region.setBlockData(x, y, z, hedge);
                continue;
            }
            if (c.zone() == Zone.GOLDENREACH && terrain.field(x, z) == 6 && roll < 0.03) {
                region.setBlockData(x, y, z, hay);
                continue;
            }
            boolean soil = ground == Material.GRASS_BLOCK || ground == Material.PODZOL || ground == Material.MOSS_BLOCK
                || ground == Material.COARSE_DIRT || ground == Material.MUD || ground == Material.RED_SAND || ground == Material.ROOTED_DIRT;
            if (!soil) continue;
            plant(region, c, x, y, z, ground, roll, roll2);
        }
    }

    // ------------------------------------------------------------------ trees

    private void trees(int chunkX, int chunkZ, LimitedRegion region) {
        int buffer = region.getBuffer();
        ReachTreeGen.Writer writer = new ReachTreeGen.Writer() {
            @Override public boolean air(int x, int y, int z) {
                if (!region.isInRegion(x, y, z)) return false;
                Material m = region.getType(x, y, z);
                return m.isAir() || m == Material.SHORT_GRASS || m == Material.TALL_GRASS || m == Material.FERN;
            }
            @Override public void set(int x, int y, int z, Material material, String properties) {
                if (!region.isInRegion(x, y, z)) return;
                String key = "minecraft:" + material.name().toLowerCase(java.util.Locale.ROOT) + (properties.isEmpty() ? "" : "[" + properties + "]");
                region.setBlockData(x, y, z, data(key));
            }
        };
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        // The zone at the chunk's middle chooses the layers; grids are world-aligned, so cells never double up.
        Zone zone = terrain.zoneAt(x0 + 8, z0 + 8);
        List<Layer> layers = LAYERS.get(zone);
        if (layers == null) return;
        for (Layer layer : layers) {
            int s = layer.cell;
            for (int cx = Math.floorDiv(x0, s); cx <= Math.floorDiv(x0 + 15, s); cx++) for (int cz = Math.floorDiv(z0, s); cz <= Math.floorDiv(z0 + 15, s); cz++) {
                long h = hash(cx * 92821 + s, cz * 68903 - s);
                int x = cx * s + (int) Math.floorMod(h, (long) s), z = cz * s + (int) Math.floorMod(h >>> 20, (long) s);
                if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                double grove = terrain.patch(x, z, 0.012);
                double chance = grove > layer.groveFrom ? layer.inGrove : layer.outGrove;
                if (((h >>> 40) & 0xFFFF) / 65536.0 >= chance) continue;
                Species species = pick(layer, h);
                if (species.radius > buffer) continue;
                Column c = terrain.column(x, z);
                if (!c.land() || c.wet() || c.zone() != zone || c.road() > 0.02 || c.river() < 5 || c.edge() < species.radius + 2) continue;
                if (terrain.field(x, z) >= 0 || terrain.terraceMask(x, z) > 0.3) continue;
                if (!clearOfBuilds(x, z, Math.max(2, species.radius / 2))) continue;
                Material ground = region.getType(x, c.top(), z);
                if (ground != Material.GRASS_BLOCK && ground != Material.PODZOL && ground != Material.MOSS_BLOCK && ground != Material.COARSE_DIRT
                    && ground != Material.RED_SAND && ground != Material.TUFF && ground != Material.GRAVEL && ground != Material.ROOTED_DIRT
                    && ground != Material.GRAY_CONCRETE_POWDER && ground != Material.LIGHT_GRAY_CONCRETE_POWDER && ground != Material.MUD) continue;
                if (!region.getType(x, c.top() + 1, z).isAir()) continue;
                ReachTreeGen.grow(writer, species, x, c.top() + 1, z, h);
            }
        }
    }

    private static Species pick(Layer layer, long h) {
        int total = 0;
        for (int w : layer.weights) total += w;
        int roll = (int) Math.floorMod(h >>> 8, (long) total);
        for (int i = 0; i < layer.weights.length; i++) { roll -= layer.weights[i]; if (roll < 0) return layer.species.get(i); }
        return layer.species.get(0);
    }

    private boolean clearOfBuilds(int x, int z, int r) {
        if (features.occupied(x, z)) return false;
        for (int a = 0; a < 8; a++) {
            double t = a * Math.PI / 4;
            if (features.occupied(x + (int) Math.round(Math.cos(t) * r), z + (int) Math.round(Math.sin(t) * r))) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ ground plants, in drifts

    private void plant(LimitedRegion region, Column c, int x, int y, int z, Material ground, double roll, double roll2) {
        boolean canTall = region.isInRegion(x, y + 1, z) && region.getType(x, y + 1, z) == Material.AIR;
        // Drift noise: grass and flowers gather in patches, each patch mostly one kind.
        double meadow = terrain.patch(x, z, 0.035), drift = terrain.grain(x, z, 0.06);
        int kind = (int) Math.floorMod((long) Math.floor(terrain.patch(x + 3000, z, 0.02) * 4), 4L);
        switch (c.zone()) {
            case GOLDENREACH, HIGHCROWN -> {
                if (drift > 0.45 && roll < 0.55) region.setType(x, y, z, flower(kind, Material.DANDELION, Material.POPPY, Material.CORNFLOWER, Material.OXEYE_DAISY));
                else if (meadow > 0.25 && roll < 0.35 && canTall) tall(region, x, y, z, grassLower, grassUpper);
                else if (roll < 0.45 + meadow * 0.2) region.setType(x, y, z, Material.SHORT_GRASS);
                else if (roll < 0.47 && canTall) tall(region, x, y, z, kind == 0 ? peonyLower : lilacLower, kind == 0 ? peonyUpper : lilacUpper);
            }
            case BARROWDOWNS -> {
                if (drift > 0.35 && roll < 0.6) region.setType(x, y, z, flower(kind, Material.ALLIUM, Material.ALLIUM, Material.AZURE_BLUET, Material.PINK_TULIP));
                else if (drift > 0.55 && roll < 0.7 && canTall) tall(region, x, y, z, lilacLower, lilacUpper);
                else if (roll < 0.5) region.setType(x, y, z, Material.SHORT_GRASS);
            }
            case THORNWOOD -> {
                if (roll < 0.28) region.setType(x, y, z, Material.FERN);
                else if (roll < 0.40 && canTall) tall(region, x, y, z, fernLower, fernUpper);
                else if (roll < 0.48 && ground == Material.MOSS_BLOCK) region.setType(x, y, z, Material.MOSS_CARPET);
                else if (roll < 0.50) region.setType(x, y, z, roll2 < 0.5 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM);
                else if (roll < 0.60) region.setType(x, y, z, Material.SHORT_GRASS);
            }
            case MISTMERE -> {
                if (c.lake() < 1.2 && roll < 0.3 && canTall) tall(region, x, y, z, grassLower, grassUpper);
                else if (drift > 0.4 && roll < 0.5) region.setType(x, y, z, flower(kind, Material.BLUE_ORCHID, Material.AZURE_BLUET, Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY));
                else if (roll < 0.45) region.setType(x, y, z, Material.SHORT_GRASS);
            }
            case IRONSPINE -> { if (c.top() < 166 && roll < 0.3) region.setType(x, y, z, Material.SHORT_GRASS); }
            case SUNSCAR -> { if (roll < 0.03) region.setType(x, y, z, Material.DEAD_BUSH); }
            case ARCHIVE -> {
                if (drift > 0.4 && roll < 0.5) region.setType(x, y, z, flower(kind, Material.PINK_TULIP, Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY, Material.OXEYE_DAISY));
                else if (roll < 0.3) region.setType(x, y, z, Material.SHORT_GRASS);
            }
            default -> { }
        }
    }

    private static void tall(LimitedRegion region, int x, int y, int z, BlockData lower, BlockData upper) {
        region.setBlockData(x, y, z, lower);
        region.setBlockData(x, y + 1, z, upper);
    }

    private static Material flower(int kind, Material a, Material b, Material c, Material d) {
        return switch (kind) { case 0 -> a; case 1 -> b; case 2 -> c; default -> d; };
    }

    static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }
}
