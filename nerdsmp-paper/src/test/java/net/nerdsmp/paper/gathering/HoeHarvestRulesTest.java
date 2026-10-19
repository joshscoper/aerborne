package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class HoeHarvestRulesTest {
    @Test
    void onlyFullyMatureKnownRootCropsQualify() {
        Map<String, Integer> ages = Map.of("WHEAT", 7, "CARROTS", 7, "POTATOES", 7,
            "BEETROOTS", 3, "NETHER_WART", 3, "COCOA", 2, "SWEET_BERRY_BUSH", 3,
            "PITCHER_CROP", 4);
        ages.forEach((material, matureAge) -> {
            assertTrue(HoeHarvestRules.eligible(material, matureAge, matureAge, false, false), material);
            for (int age = -1; age < matureAge; age++) {
                assertFalse(HoeHarvestRules.eligible(material, age, matureAge, false, false), material);
            }
            assertFalse(HoeHarvestRules.eligible(material, matureAge + 1, matureAge, false, false), material);
            assertFalse(HoeHarvestRules.eligible(material, matureAge, matureAge, false, true), material);
            assertFalse(HoeHarvestRules.eligible(material, matureAge, matureAge + 1, false, false), material);
            assertFalse(HoeHarvestRules.eligible(material, 0, 0, false, false), material);
        });
    }

    @Test
    void matureTorchflowersChangeMaterialAndRequireSeedsToReplant() {
        assertFalse(HoeHarvestRules.eligible("TORCHFLOWER_CROP", 1, 1, false, false));
        assertTrue(HoeHarvestRules.eligible("TORCHFLOWER", -1, -1, false, false));
        HoeHarvestRules.Spec spec = HoeHarvestRules.recipe(Material.TORCHFLOWER);
        assertEquals(Material.TORCHFLOWER_CROP, spec.plantingMaterial());
        assertEquals(Material.TORCHFLOWER_SEEDS, spec.seed());
        assertEquals(0, spec.resetAge());
    }

    @Test
    void decorativePitcherPlantsAndStemFruitAreNotRightClickCrops() {
        for (String material : List.of("FIRE", "FROSTED_ICE", "MELON_STEM", "PUMPKIN_STEM",
            "ATTACHED_MELON_STEM", "MELON", "PUMPKIN", "BAMBOO", "CACTUS", "SUGAR_CANE",
            "PITCHER_PLANT", "STONE", "WHEAT_SEEDS", "not-a-material")) {
            assertNull(HoeHarvestRules.recipe(material), material);
            assertFalse(HoeHarvestRules.eligible(material, 3, 3, true, false), material);
        }
        assertNull(HoeHarvestRules.recipe((String) null));
        assertNull(HoeHarvestRules.recipe((Material) null));
        assertFalse(HoeHarvestRules.eligible(null, 3, 3, true, false));
    }

    @Test
    void berriesKeepTheirBushOrVineWithoutReservingSeeds() {
        HoeHarvestRules.Spec bush = HoeHarvestRules.recipe("SWEET_BERRY_BUSH");
        assertNull(bush.seed());
        assertEquals(1, bush.resetAge());
        for (String vine : List.of("CAVE_VINES", "CAVE_VINES_PLANT")) {
            assertTrue(HoeHarvestRules.eligible(vine, 0, 25, true, false));
            assertFalse(HoeHarvestRules.eligible(vine, 25, 25, false, false));
            HoeHarvestRules.Spec spec = HoeHarvestRules.recipe(vine);
            assertNull(spec.seed());
            assertTrue(spec.berryVine());
            assertEquals(spec.crop(), spec.plantingMaterial());
        }
    }

    @Test
    void fieldAndTallCropPlansUseTheCorrectPlantingIngredient() {
        Map<Material, Material> seeds = Map.of(Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS, Material.PITCHER_CROP, Material.PITCHER_POD);
        seeds.forEach((crop, seed) -> {
            HoeHarvestRules.Spec spec = HoeHarvestRules.recipe(crop);
            assertEquals(seed, spec.seed());
            assertEquals(crop, spec.plantingMaterial());
            assertEquals(0, spec.resetAge());
            assertEquals(crop == Material.PITCHER_CROP, spec.tall());
            assertFalse(spec.berryVine());
        });
    }

    @Test
    void reservesOneHarvestSeedBeforeTouchingTheInventory() {
        List<HoeHarvestRules.SeedStack> drops = new ArrayList<>(List.of(stack("WHEAT", 1), stack("WHEAT_SEEDS", 3)));
        List<HoeHarvestRules.SeedStack> storage = new ArrayList<>(List.of(stack("WHEAT_SEEDS", 20)));
        HoeHarvestRules.SeedReservation plan = HoeHarvestRules.reserveSeed("WHEAT_SEEDS", drops, storage);
        assertTrue(plan.available());
        assertEquals(HoeHarvestRules.SeedSource.HARVEST, plan.source());
        assertEquals(1, plan.index());
        assertEquals(2, plan.drops().get(1).amount());
        assertEquals(20, plan.storage().getFirst().amount());
        assertEquals(3, drops.get(1).amount());
        assertEquals(20, storage.getFirst().amount());
        assertThrows(UnsupportedOperationException.class, () -> plan.drops().clear());
        drops.clear();
        storage.clear();
        assertEquals(2, plan.drops().size());
        assertEquals(1, plan.storage().size());
    }

    @Test
    void flowerSeedFallbackUsesOnlyAPlainInventorySeed() {
        List<HoeHarvestRules.SeedStack> drops = List.of(stack("TORCHFLOWER", 1));
        List<HoeHarvestRules.SeedStack> storage = List.of(
            new HoeHarvestRules.SeedStack("TORCHFLOWER_SEEDS", 4, true),
            stack("TORCHFLOWER_SEEDS", 0), stack("TORCHFLOWER_SEEDS", 1));
        HoeHarvestRules.SeedReservation plan = HoeHarvestRules.reserveSeed("torchflower_seeds", drops, storage);
        assertTrue(plan.available());
        assertEquals(HoeHarvestRules.SeedSource.STORAGE, plan.source());
        assertEquals(2, plan.index());
        assertEquals(0, plan.storage().get(2).amount());
        assertEquals(4, plan.storage().getFirst().amount());
        assertEquals(drops, plan.drops());
        assertEquals(1, storage.get(2).amount());
    }

    @Test
    void neverConsumesSpecialDropsOrInventsAMissingSeed() {
        List<HoeHarvestRules.SeedStack> drops = List.of(new HoeHarvestRules.SeedStack("PITCHER_POD", 1, true));
        List<HoeHarvestRules.SeedStack> storage = List.of(stack("PITCHER_POD", 0), stack("WHEAT_SEEDS", 64));
        HoeHarvestRules.SeedReservation plan = HoeHarvestRules.reserveSeed("PITCHER_POD", drops, storage);
        assertFalse(plan.available());
        assertEquals(HoeHarvestRules.SeedSource.MISSING, plan.source());
        assertEquals(-1, plan.index());
        assertEquals(drops, plan.drops());
        assertEquals(storage, plan.storage());
    }

    @Test
    void persistentBerryPlantsRequireNoSeedAndKeepAllDrops() {
        List<HoeHarvestRules.SeedStack> drops = List.of(stack("GLOW_BERRIES", 1));
        HoeHarvestRules.SeedReservation plan = HoeHarvestRules.reserveSeed(null, drops, List.of());
        assertTrue(plan.available());
        assertEquals(HoeHarvestRules.SeedSource.NONE, plan.source());
        assertEquals(-1, plan.index());
        assertEquals(drops, plan.drops());
        assertEquals(List.of(), plan.storage());
    }

    @Test
    void rejectsMalformedCountDescriptions() {
        assertThrows(IllegalArgumentException.class, () -> stack("", 1));
        assertThrows(IllegalArgumentException.class, () -> stack(null, 1));
        assertThrows(IllegalArgumentException.class, () -> stack("WHEAT_SEEDS", -1));
        assertEquals("WHEAT_SEEDS", stack("wheat_seeds", 1).material());
    }

    private static HoeHarvestRules.SeedStack stack(String material, int amount) {
        return new HoeHarvestRules.SeedStack(material, amount, false);
    }
}
