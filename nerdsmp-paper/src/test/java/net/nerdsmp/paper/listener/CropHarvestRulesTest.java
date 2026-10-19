package net.nerdsmp.paper.listener;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CropHarvestRulesTest {
    @Test
    void onlyFullyGrownHarvestableCropsQualify() {
        Map<String, Integer> crops = Map.of(
            "WHEAT", 7, "CARROTS", 7, "POTATOES", 7, "BEETROOTS", 3,
            "NETHER_WART", 3, "COCOA", 2, "SWEET_BERRY_BUSH", 3, "PITCHER_CROP", 4
        );
        crops.forEach((material, maximum) -> {
            assertTrue(CropHarvestRules.mature(material, maximum, maximum), material);
            for (int age = 0; age < maximum; age++) {
                assertFalse(CropHarvestRules.mature(material, age, maximum), material + " age " + age);
            }
        });
        assertFalse(CropHarvestRules.mature("WHEAT", 0, 0));
        assertFalse(CropHarvestRules.mature("WHEAT", 8, 7));
    }

    @Test
    void ageableNonCropsAndImmatureFlowerStalksNeverQualify() {
        for (String material : new String[]{"FIRE", "FROSTED_ICE", "CACTUS", "SUGAR_CANE", "BAMBOO",
            "PUMPKIN_STEM", "MELON_STEM", "TORCHFLOWER_CROP", "STONE"}) {
            assertFalse(CropHarvestRules.mature(material, 3, 3), material);
        }
    }

    @Test
    void regrownCropsAtOneLocationReceiveDistinctXpAndJobOperations() {
        String location = "world:8:64:-12";
        HashSet<String> operations = new HashSet<>();
        for (int harvest = 0; harvest < 100; harvest++) {
            String operation = CropHarvestRules.newHarvestOperation(location);
            assertTrue(operation.startsWith("crop:" + location + ":"));
            operations.add(operation);
        }
        assertEquals(100, operations.size());
    }
}
