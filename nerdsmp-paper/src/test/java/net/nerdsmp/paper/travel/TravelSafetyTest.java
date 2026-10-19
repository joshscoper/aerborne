package net.nerdsmp.paper.travel;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TravelSafetyTest {
    @Test void dangerousPassableBlocksAndFloorsAreNotSafeDestinations() {
        for (Material material : new Material[]{Material.LAVA, Material.FIRE, Material.POWDER_SNOW, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.WITHER_ROSE, Material.NETHER_PORTAL, Material.END_GATEWAY, Material.SWEET_BERRY_BUSH})
            assertFalse(TravelSafety.harmless(material), material.name());
    }
    @Test void ordinaryIslandPlatformsAndAirAreHarmless() {
        assertTrue(TravelSafety.harmless(Material.AIR)); assertTrue(TravelSafety.harmless(Material.OAK_PLANKS)); assertTrue(TravelSafety.harmless(Material.GRASS_BLOCK));
    }
}
