package net.nerdsmp.paper.travel;

import org.bukkit.Material;

final class TravelSafety {
    private TravelSafety() { }
    static boolean harmless(Material material) {
        return switch (material) {
            case LAVA, WATER, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, CACTUS,
                SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW, POINTED_DRIPSTONE, NETHER_PORTAL,
                END_PORTAL, END_GATEWAY -> false;
            default -> true;
        };
    }
}
