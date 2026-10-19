package net.nerdsmp.paper.npc;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class NpcPlacementSafetyTest {
    @Test
    void acceptsClearSolidFloorAndRejectsBlockedHeadroom() {
        assertTrue(fits(entity(new BoundingBox(0.2, 65, 0.2, 0.8, 66.95, 0.8), Material.AIR, true)));
        assertFalse(fits(entity(new BoundingBox(0.2, 65, 0.2, 0.8, 66.95, 0.8), Material.STONE, true)));
    }

    @Test
    void rejectsFluidsAndNeverAcceptsAnUnloadedFootprint() {
        assertFalse(fits(entity(new BoundingBox(0.2, 65, 0.2, 0.8, 66.95, 0.8), Material.WATER, true)));
        assertFalse(fits(entity(new BoundingBox(0.2, 65, 0.2, 0.8, 66.95, 0.8), Material.AIR, false)));
    }

    @Test
    void rejectsFloatingPlacementAndOversizedBodies() {
        assertFalse(fits(entity(new BoundingBox(0.2, 65.5, 0.2, 0.8, 67.45, 0.8), Material.AIR, true)));
        assertFalse(fits(entity(new BoundingBox(0, 65, 0, 9, 67, 9), Material.AIR, true)));
    }

    private boolean fits(LivingEntity entity) {
        return NpcPlacementSafety.fits(entity, material -> material == Material.STONE);
    }

    private LivingEntity entity(BoundingBox bounds, Material body, boolean loaded) {
        World world = proxy(World.class, (method, args) -> switch (method) {
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> loaded;
            case "getBlockAt" -> block((int) args[0], (int) args[1], (int) args[2], (int) args[1] == 64 ? Material.STONE : body);
            default -> throw new UnsupportedOperationException(method);
        });
        return proxy(LivingEntity.class, (method, args) -> switch (method) {
            case "isValid" -> true;
            case "getWorld" -> world;
            case "getBoundingBox" -> bounds;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private Block block(int x, int y, int z, Material material) {
        return proxy(Block.class, (method, args) -> switch (method) {
            case "getType" -> material;
            case "isPassable" -> material == Material.AIR || material == Material.WATER;
            case "isLiquid" -> material == Material.WATER;
            case "getBoundingBox" -> new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private interface Call { Object invoke(String method, Object[] arguments); }

    private <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (ignored, method, args) -> call.invoke(method.getName(), args)));
    }
}
