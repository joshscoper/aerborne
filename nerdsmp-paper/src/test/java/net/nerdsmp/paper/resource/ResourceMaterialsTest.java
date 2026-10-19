package net.nerdsmp.paper.resource;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourceMaterialsTest {
    @Test
    void sharedExcavationCatalogIncludesEverySupportedDigMaterial() {
        Set<String> soils = Set.of("DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK",
            "SAND", "RED_SAND", "GRAVEL", "CLAY", "MUD", "SUSPICIOUS_SAND",
            "SUSPICIOUS_GRAVEL", "SOUL_SAND");

        for (String soil : soils) {
            assertTrue(ResourceMaterials.excavation(soil), soil);
            assertFalse(ResourceMaterials.ore(soil), soil);
            assertFalse(ResourceMaterials.log(soil), soil);
        }
        for (String other : Set.of("STONE", "DIRT_PATH", "FARMLAND", "MUD_BRICKS",
            "SANDSTONE", "CLAY_BALL", "AIR")) {
            assertFalse(ResourceMaterials.excavation(other), other);
        }
    }

    @Test
    void treeAndFungusLogsUseTheSameRegrowthAndForestryClassification() {
        for (String wood : Set.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA",
            "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK")) {
            assertTrue(ResourceMaterials.log(wood + "_LOG"), wood);
            assertTrue(ResourceMaterials.log("STRIPPED_" + wood + "_LOG"), wood);
        }
        for (String fungus : Set.of("CRIMSON", "WARPED")) {
            assertTrue(ResourceMaterials.log(fungus + "_STEM"), fungus);
            assertTrue(ResourceMaterials.log("STRIPPED_" + fungus + "_STEM"), fungus);
            assertTrue(ResourceMaterials.log(fungus + "_HYPHAE"), fungus);
            assertTrue(ResourceMaterials.log("STRIPPED_" + fungus + "_HYPHAE"), fungus);
        }
    }

    @Test
    void cropAndMushroomStemsDoNotCountAsForestryLogs() {
        for (String material : Set.of("PUMPKIN_STEM", "MELON_STEM", "ATTACHED_PUMPKIN_STEM",
            "ATTACHED_MELON_STEM", "MUSHROOM_STEM", "BAMBOO", "OAK_SAPLING", "OAK_LEAVES")) {
            assertFalse(ResourceMaterials.log(material), material);
        }
    }

    @Test
    void oresAndAncientDebrisQualifyButProcessedProductsDoNot() {
        for (String material : Set.of("COAL_ORE", "IRON_ORE", "COPPER_ORE", "GOLD_ORE",
            "REDSTONE_ORE", "LAPIS_ORE", "DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_DIAMOND_ORE",
            "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE", "ANCIENT_DEBRIS")) {
            assertTrue(ResourceMaterials.ore(material), material);
            assertFalse(ResourceMaterials.excavation(material), material);
            assertFalse(ResourceMaterials.log(material), material);
        }
        for (String material : Set.of("DIAMOND_BLOCK", "RAW_IRON_BLOCK", "NETHERITE_BLOCK",
            "COAL", "STONE", "DEEPSLATE", "BUDDING_AMETHYST")) {
            assertFalse(ResourceMaterials.ore(material), material);
        }
    }
}
