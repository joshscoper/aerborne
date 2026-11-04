package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.reach.ReachTerrain.Column;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Plants the Reach: trees, grass, flowers and crops per zone, kept off roads, banks, the lip and anything a feature
 * reserved. Each column's choices come from a hash of its position, so a regenerated chunk grows the same plants.
 */
final class ReachFlora extends BlockPopulator {
    private final ReachTerrain terrain;
    private final ReachFeatures features;
    private final BlockData grassLower, grassUpper, fernLower, fernUpper, lilacLower, lilacUpper, wheatRipe, wheatGreen, snow;

    ReachFlora(ReachTerrain terrain, ReachFeatures features) {
        this.terrain = terrain;
        this.features = features;
        grassLower = Bukkit.createBlockData("minecraft:tall_grass[half=lower]");
        grassUpper = Bukkit.createBlockData("minecraft:tall_grass[half=upper]");
        fernLower = Bukkit.createBlockData("minecraft:large_fern[half=lower]");
        fernUpper = Bukkit.createBlockData("minecraft:large_fern[half=upper]");
        lilacLower = Bukkit.createBlockData("minecraft:lilac[half=lower]");
        lilacUpper = Bukkit.createBlockData("minecraft:lilac[half=upper]");
        wheatRipe = Bukkit.createBlockData("minecraft:wheat[age=7]");
        wheatGreen = Bukkit.createBlockData("minecraft:wheat[age=5]");
        snow = Bukkit.createBlockData("minecraft:snow[layers=1]");
    }

    @Override
    public void populate(WorldInfo info, Random ignored, int chunkX, int chunkZ, LimitedRegion region) {
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
                region.setBlockData(x, y, z, terrain.patch(x, z, 0.06) > 0.35 ? wheatGreen : wheatRipe);
                continue;
            }
            if (ground == Material.SNOW_BLOCK) { region.setBlockData(x, y, z, snow); continue; }
            if (c.road() > 0.05 || (c.river() >= 0 && c.river() < 4) || c.edge() < 4 || features.occupied(x, z)) continue;
            boolean soil = ground == Material.GRASS_BLOCK || ground == Material.PODZOL || ground == Material.MOSS_BLOCK
                || ground == Material.COARSE_DIRT || ground == Material.MUD || ground == Material.RED_SAND;
            if (!soil) continue;
            double grove = terrain.patch(x, z, 0.012);
            TreeType tree = tree(c, grove, roll, roll2);
            if (tree != null && lx > 1 && lx < 14 && lz > 1 && lz < 14) {
                if (region.generateTree(new Location(null, x, y, z), new Random(hash), tree)) continue;
            }
            plant(region, c, x, y, z, ground, roll, roll2);
        }
    }

    /** Which tree, if any, grows here. {@code grove} clusters trees into woods and leaves open fields between. */
    private TreeType tree(Column c, double grove, double roll, double roll2) {
        return switch (c.zone()) {
            case GOLDENREACH -> c.terrace() > 0.2 ? null : grove > 0.45 && roll < 0.012 ? (roll2 < 0.5 ? TreeType.TALL_REDWOOD : TreeType.REDWOOD)
                : grove > 0.25 && roll < 0.006 ? (roll2 < 0.6 ? TreeType.TREE : TreeType.BIRCH) : roll < 0.0006 ? TreeType.BIG_TREE : null;
            case BARROWDOWNS -> grove > 0.5 && roll < 0.01 ? TreeType.REDWOOD : roll < 0.0008 ? TreeType.TREE : null;
            case THORNWOOD -> roll < (grove > 0 ? 0.05 : 0.03)
                ? (roll2 < 0.35 ? TreeType.MEGA_REDWOOD : roll2 < 0.6 ? TreeType.TALL_REDWOOD : roll2 < 0.85 ? TreeType.DARK_OAK : TreeType.MEGA_PINE) : null;
            case MISTMERE -> c.lake() < 1.35 && roll < 0.01 ? TreeType.SWAMP
                : grove > 0.2 && roll < 0.02 ? (roll2 < 0.6 ? TreeType.BIRCH : TreeType.TALL_BIRCH) : null;
            case IRONSPINE -> c.top() < 160 && grove > 0.0 && roll < 0.02 ? (roll2 < 0.7 ? TreeType.TALL_REDWOOD : TreeType.REDWOOD) : null;
            case SUNSCAR -> roll < 0.0015 ? TreeType.ACACIA : null;
            case ARCHIVE -> grove > 0.15 && roll < 0.018 ? TreeType.CHERRY : null;
            case HIGHCROWN -> roll < 0.002 ? TreeType.TREE : null;
            default -> null;
        };
    }

    private void plant(LimitedRegion region, Column c, int x, int y, int z, Material ground, double roll, double roll2) {
        boolean canTall = region.isInRegion(x, y + 1, z) && region.getType(x, y + 1, z) == Material.AIR;
        switch (c.zone()) {
            case GOLDENREACH, HIGHCROWN -> {
                if (roll < 0.30) region.setType(x, y, z, Material.SHORT_GRASS);
                else if (roll < 0.36 && canTall) tall(region, x, y, z, grassLower, grassUpper);
                else if (roll < 0.40) region.setType(x, y, z, flower(roll2, Material.DANDELION, Material.POPPY, Material.CORNFLOWER, Material.OXEYE_DAISY));
            }
            case BARROWDOWNS -> {
                if (roll < 0.35) region.setType(x, y, z, Material.SHORT_GRASS);
                else if (roll < 0.43) region.setType(x, y, z, flower(roll2, Material.ALLIUM, Material.ALLIUM, Material.AZURE_BLUET, Material.PINK_TULIP));
                else if (roll < 0.46 && canTall) tall(region, x, y, z, lilacLower, lilacUpper);
            }
            case THORNWOOD -> {
                if (roll < 0.22) region.setType(x, y, z, Material.FERN);
                else if (roll < 0.30 && canTall) tall(region, x, y, z, fernLower, fernUpper);
                else if (roll < 0.36 && ground == Material.MOSS_BLOCK) region.setType(x, y, z, Material.MOSS_CARPET);
                else if (roll < 0.38) region.setType(x, y, z, roll2 < 0.5 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM);
            }
            case MISTMERE -> {
                if (roll < 0.3) region.setType(x, y, z, Material.SHORT_GRASS);
                else if (roll < 0.34) region.setType(x, y, z, flower(roll2, Material.BLUE_ORCHID, Material.AZURE_BLUET, Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY));
            }
            case IRONSPINE -> { if (c.top() < 166 && roll < 0.25) region.setType(x, y, z, Material.SHORT_GRASS); }
            case SUNSCAR -> { if (roll < 0.02) region.setType(x, y, z, Material.DEAD_BUSH); }
            case ARCHIVE -> {
                if (roll < 0.2) region.setType(x, y, z, Material.SHORT_GRASS);
                else if (roll < 0.26) region.setType(x, y, z, flower(roll2, Material.PINK_TULIP, Material.WHITE_TULIP, Material.LILY_OF_THE_VALLEY, Material.OXEYE_DAISY));
            }
            default -> { }
        }
    }

    private static void tall(LimitedRegion region, int x, int y, int z, BlockData lower, BlockData upper) {
        region.setBlockData(x, y, z, lower);
        region.setBlockData(x, y + 1, z, upper);
    }

    private static Material flower(double roll, Material a, Material b, Material c, Material d) {
        return roll < 0.25 ? a : roll < 0.5 ? b : roll < 0.75 ? c : d;
    }

    static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }
}
