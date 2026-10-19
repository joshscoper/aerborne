package net.nerdsmp.paper.activity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FishingCatchTest {
    @Test
    void journalHasTwelveUniqueCatchesAcrossEveryRarity() {
        Set<String> ids = Arrays.stream(FishingCatch.values())
            .map(entry -> entry.itemId().value()).collect(Collectors.toSet());

        assertEquals(12, ids.size());
        assertEquals(4, FishingCatch.pool(FishingRolls.Rarity.COMMON).size());
        assertEquals(3, FishingCatch.pool(FishingRolls.Rarity.UNCOMMON).size());
        assertEquals(3, FishingCatch.pool(FishingRolls.Rarity.RARE).size());
        assertEquals(2, FishingCatch.pool(FishingRolls.Rarity.LEGENDARY).size());
        for (FishingCatch entry : FishingCatch.values()) {
            assertTrue(entry.reputation() > 0);
            assertTrue(!entry.habitats().isEmpty());
            assertNotNull(FishingCatch.byItemId(entry.itemId().value()));
        }
    }

    @Test
    void biomeNamesResolveToStableFishingHabitats() {
        assertEquals(FishingCatch.Habitat.OCEAN, FishingCatch.Habitat.fromBiome("deep_frozen_ocean"));
        assertEquals(FishingCatch.Habitat.RIVER, FishingCatch.Habitat.fromBiome("frozen_river"));
        assertEquals(FishingCatch.Habitat.SWAMP, FishingCatch.Habitat.fromBiome("mangrove_swamp"));
        assertEquals(FishingCatch.Habitat.COLD, FishingCatch.Habitat.fromBiome("snowy_taiga"));
        assertEquals(FishingCatch.Habitat.INLAND, FishingCatch.Habitat.fromBiome("plains"));
    }

    @Test
    void habitatBiasNeverRemovesAValidCatchFromThePool() {
        for (FishingRolls.Rarity rarity : FishingRolls.Rarity.values()) {
            for (FishingCatch.Habitat habitat : FishingCatch.Habitat.values()) {
                assertNotNull(FishingCatch.select(rarity, habitat, 0.0D));
                assertNotNull(FishingCatch.select(rarity, habitat, Math.nextDown(1.0D)));
            }
        }
    }
}
