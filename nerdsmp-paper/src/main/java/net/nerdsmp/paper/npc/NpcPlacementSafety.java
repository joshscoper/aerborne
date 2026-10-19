package net.nerdsmp.paper.npc;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.function.Predicate;

final class NpcPlacementSafety {
    private NpcPlacementSafety() { }

    static boolean fits(LivingEntity entity) {
        return fits(entity, Material::isSolid);
    }

    static boolean fits(LivingEntity entity, Predicate<Material> solid) {
        if (entity == null || !entity.isValid()) return false;
        World world = entity.getWorld();
        BoundingBox box = entity.getBoundingBox();
        if (!Double.isFinite(box.getMinX()) || !Double.isFinite(box.getMinY()) || !Double.isFinite(box.getMinZ())
            || !Double.isFinite(box.getMaxX()) || !Double.isFinite(box.getMaxY()) || !Double.isFinite(box.getMaxZ())) return false;
        if (box.getWidthX() <= 0 || box.getWidthZ() <= 0 || box.getHeight() <= 0
            || box.getWidthX() > 8 || box.getWidthZ() > 8 || box.getHeight() > 12
            || box.getMinY() < world.getMinHeight() + 1 || box.getMaxY() >= world.getMaxHeight()) return false;
        int minX = floor(box.getMinX() + 1E-5), maxX = floor(box.getMaxX() - 1E-5);
        int minZ = floor(box.getMinZ() + 1E-5), maxZ = floor(box.getMaxZ() - 1E-5);
        int feet = floor(box.getMinY() - 1E-5);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
            Block below = world.getBlockAt(x, feet, z);
            if (!solid.test(below.getType()) || !harmless(below.getType())
                || Math.abs(below.getBoundingBox().getMaxY() - box.getMinY()) > 0.06) return false;
            for (int y = floor(box.getMinY() + 1E-5); y <= floor(box.getMaxY() - 1E-5); y++) {
                Block body = world.getBlockAt(x, y, z);
                if (!body.isPassable() || body.isLiquid() || !harmless(body.getType())) return false;
            }
        }
        return true;
    }

    private static int floor(double value) { return (int) Math.floor(value); }

    private static boolean harmless(Material material) {
        return switch (material) {
            case FIRE, SOUL_FIRE, LAVA, WATER, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE,
                CACTUS, SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW, POINTED_DRIPSTONE -> false;
            default -> true;
        };
    }
}
