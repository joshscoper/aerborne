package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FarmingExperienceTest {
    @Test void fieldCropsAndBerriesAwardFifteenExperienceByDefault() {
        for (String crop : new String[]{"WHEAT", "CARROTS", "CARROT", "POTATOES", "POTATO", "BEETROOTS",
            "BEETROOT", "SWEET_BERRY_BUSH", "SWEET_BERRIES", "CAVE_VINES", "CAVE_VINES_PLANT", "GLOW_BERRIES"}) {
            assertEquals(15L, FarmingExperience.experience(crop, 5L, 3.0D), crop);
        }
    }

    @Test void longerGrowthAndSpecialtyCropsHaveHigherRewards() {
        for (String crop : new String[]{"NETHER_WART", "COCOA", "COCOA_BEANS"}) {
            assertEquals(18L, FarmingExperience.experience(crop, 5L, 3.0D), crop);
        }
        for (String crop : new String[]{"PITCHER_CROP", "PITCHER_PLANT", "TORCHFLOWER_CROP", "TORCHFLOWER",
            "MELON", "MELON_SLICE", "PUMPKIN"}) {
            assertEquals(24L, FarmingExperience.experience(crop, 5L, 3.0D), crop);
        }
    }

    @Test void renewableStalksAreRewardedWithoutMakingPlacedBlocksEligible() {
        for (String crop : new String[]{"SUGAR_CANE", "CACTUS", "BAMBOO"}) {
            assertEquals(9L, FarmingExperience.experience(crop, 5L, 3.0D), crop);
        }
        for (String notCrop : new String[]{"FIRE", "KELP", "OAK_LOG", "STONE", "MELON_STEM",
            "PUMPKIN_STEM", "BAMBOO_SAPLING", "WHEAT_SEEDS", "PITCHER_POD", "TORCHFLOWER_SEEDS"}) {
            assertFalse(FarmingExperience.recognized(notCrop), notCrop);
            assertEquals(0L, FarmingExperience.experience(notCrop, 5L, 3.0D), notCrop);
        }
        assertFalse(FarmingExperience.recognized(null));
        assertEquals(0L, FarmingExperience.experience(null, 5L, 3.0D));
    }

    @Test void legacyBaseSettingStillControlsEveryCropAndDoesNotNeedAConfigReset() {
        YamlConfiguration savedConfig = new YamlConfiguration();
        savedConfig.set("progression.xp.farming-crop", 5L);
        assertEquals(15L, FarmingExperience.baseXp(savedConfig, Material.WHEAT));
        assertEquals(18L, FarmingExperience.baseXp(savedConfig, Material.NETHER_WART));
        assertEquals(24L, FarmingExperience.baseXp(savedConfig, Material.PUMPKIN));
        assertEquals(9L, FarmingExperience.baseXp(savedConfig, Material.BAMBOO));
        savedConfig.set("progression.xp.farming-crop", 10L);
        assertEquals(30L, FarmingExperience.baseXp(savedConfig, Material.WHEAT));
        assertEquals(36L, FarmingExperience.baseXp(savedConfig, Material.COCOA));
        assertFalse(savedConfig.contains("progression.xp.farming-multiplier"));
    }

    @Test void optionalPerCropBaseOverridesApplyBeforeTheMultiplier() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("progression.xp.farming-items.CARROTS", 12L);
        assertEquals(36L, FarmingExperience.baseXp(config, Material.CARROT));
        assertEquals(36L, FarmingExperience.baseXp(config, Material.CARROTS));
        assertEquals(15L, FarmingExperience.baseXp(config, Material.POTATOES));
        assertEquals(0L, FarmingExperience.experience("STONE", 5L, 3.0D, Map.of("STONE", 500L)));
    }

    @Test void multipliersAndMalformedOverridesStayFinitePositiveAndBounded() {
        assertEquals(3.0D, FarmingExperience.multiplier(Double.NaN));
        assertEquals(3.0D, FarmingExperience.multiplier(Double.POSITIVE_INFINITY));
        assertEquals(3.0D, FarmingExperience.multiplier(Double.NEGATIVE_INFINITY));
        assertEquals(0.25D, FarmingExperience.multiplier(-1.0D));
        assertEquals(10.0D, FarmingExperience.multiplier(100.0D));
        assertEquals(15L, FarmingExperience.experience("WHEAT", 5L, Double.NaN));
        assertEquals(50L, FarmingExperience.experience("WHEAT", 5L, 100.0D));
        assertEquals(1L, FarmingExperience.experience("SUGAR_CANE", 0L, 0.0D));
        assertEquals(Long.MAX_VALUE, FarmingExperience.experience("PUMPKIN", Long.MAX_VALUE, 10.0D));
        for (Number invalid : new Number[]{0L, -1L, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(15L, FarmingExperience.experience("WHEAT", 5L, 3.0D, Map.of("WHEAT", invalid)));
        }
        assertEquals(15L, FarmingExperience.experience("wheat", 5L, 3.0D));
    }
}
