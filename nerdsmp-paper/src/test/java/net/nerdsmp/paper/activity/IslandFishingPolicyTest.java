package net.nerdsmp.paper.activity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.nerdsmp.paper.activity.IslandFishingPolicy.PondHabitat.*;
import static net.nerdsmp.paper.activity.IslandFishingPolicy.PondStatus.*;
import static org.junit.jupiter.api.Assertions.*;

final class IslandFishingPolicyTest {
    @Test void habitatUnlocksFollowEarnedIslandResourceStages() {
        assertEquals(List.of(FRESHWATER), IslandFishingPolicy.unlocked(0));
        assertEquals(List.of(FRESHWATER), IslandFishingPolicy.unlocked(1));
        assertEquals(List.of(FRESHWATER, RIVER), IslandFishingPolicy.unlocked(2));
        assertEquals(List.of(FRESHWATER, RIVER, WETLAND), IslandFishingPolicy.unlocked(3));
        assertEquals(List.of(FRESHWATER, RIVER, WETLAND, OCEAN), IslandFishingPolicy.unlocked(4));
        assertEquals(List.of(FRESHWATER, RIVER, WETLAND, OCEAN, COLD), IslandFishingPolicy.unlocked(5));
    }

    @Test void selectionCannotBypassAnUnlockAndExistingIslandsHaveASafeDefault() {
        assertEquals(FRESHWATER, IslandFishingPolicy.effective(OCEAN, 2));
        assertEquals(FRESHWATER, IslandFishingPolicy.effective(null, -3));
        assertEquals(FRESHWATER, IslandFishingPolicy.PondHabitat.parse(null));
        assertEquals(FRESHWATER, IslandFishingPolicy.PondHabitat.parse("unknown"));
        assertEquals(OCEAN, IslandFishingPolicy.PondHabitat.parse("OCEAN"));
        assertEquals(RIVER, IslandFishingPolicy.next(FRESHWATER, 2));
        assertEquals(FRESHWATER, IslandFishingPolicy.next(RIVER, 2));
        assertEquals(FRESHWATER, IslandFishingPolicy.next(FRESHWATER, 1));
    }

    @Test void pondHabitatsMapToExistingFishCollectionsWithoutRemovingAnySpecies() {
        assertEquals(FishingCatch.Habitat.INLAND, FRESHWATER.fishingHabitat());
        assertEquals(FishingCatch.Habitat.RIVER, RIVER.fishingHabitat());
        assertEquals(FishingCatch.Habitat.SWAMP, WETLAND.fishingHabitat());
        assertEquals(FishingCatch.Habitat.OCEAN, OCEAN.fishingHabitat());
        assertEquals(FishingCatch.Habitat.COLD, COLD.fishingHabitat());
        for (var habitat : IslandFishingPolicy.PondHabitat.values()) {
            for (var rarity : FishingRolls.Rarity.values()) {
                assertNotNull(FishingCatch.select(rarity, habitat.fishingHabitat(), 0));
                assertNotNull(FishingCatch.select(rarity, habitat.fishingHabitat(), Math.nextDown(1.0D)));
            }
        }
    }

    @Test void combinedExperienceIncludesExactlyOneBaseAndOneRarityBonus() {
        assertEquals(25, IslandFishingPolicy.experience(25, FishingRolls.Rarity.COMMON));
        assertEquals(37, IslandFishingPolicy.experience(25, FishingRolls.Rarity.UNCOMMON));
        assertEquals(65, IslandFishingPolicy.experience(25, FishingRolls.Rarity.RARE));
        assertEquals(145, IslandFishingPolicy.experience(25, FishingRolls.Rarity.LEGENDARY));
        assertEquals(1, IslandFishingPolicy.experience(-100, FishingRolls.Rarity.COMMON));
        assertEquals(1_000_120, IslandFishingPolicy.experience(Long.MAX_VALUE, FishingRolls.Rarity.LEGENDARY));
    }

    @Test void islandLuckIsSmallAndCapped() {
        assertEquals(0, IslandFishingPolicy.islandLuck(-3));
        assertEquals(0, IslandFishingPolicy.islandLuck(0));
        assertEquals(3, IslandFishingPolicy.islandLuck(3));
        assertEquals(5, IslandFishingPolicy.islandLuck(100));
    }

    @Test void anEligibleCatchChecksOnlyTheBobberColumnAndReadsNoTerrain() {
        TestPond pond = new TestPond();
        assertEquals(VALID, IslandFishingPolicy.inspect(pond, 0, 100, 0));
        assertEquals(0, IslandFishingPolicy.MAX_BLOCK_READS);
        assertEquals(1, pond.columns);
        assertEquals(1, pond.loadedChecks);
    }

    @Test void noFootprintOrHeadroomIsRequiredAtTheIslandAndWorldEdges() {
        for(int height:new int[]{-64,-63,318,319}) {
            TestPond pond = new TestPond();pond.boundary=0;
            assertEquals(VALID, IslandFishingPolicy.inspect(pond, 0, height, 0));
            assertEquals(1,pond.columns);
        }
    }

    @Test void unloadedBobberChunkIsRejectedWithoutLoadingIt() {
        TestPond pond = new TestPond();
        pond.loaded = false;
        assertEquals(UNLOADED, IslandFishingPolicy.inspect(pond, 0, 100, 0));
        assertEquals(1, pond.loadedChecks);
    }

    @Test void aBobberOutsideTheIslandCannotBorrowItsHabitatOrRewards() {
        TestPond pond = new TestPond();
        pond.boundary = 0;
        assertEquals(OUTSIDE_ISLAND, IslandFishingPolicy.inspect(pond, 1, 100, 0));
        assertEquals(0, pond.loadedChecks);
    }

    @Test void outOfWorldBobbersCannotReceiveRewards() {
        for (int height : new int[]{-65, 320, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            TestPond pond = new TestPond();
            assertEquals(INVALID_HEIGHT, IslandFishingPolicy.inspect(pond, 0, height, 0));
        }
    }

    @Test void extremeCoordinatesNeverExpandIntoNeighboringColumns() {
        TestPond pond = new TestPond();pond.boundary=Long.MAX_VALUE;
        assertEquals(VALID, IslandFishingPolicy.inspect(pond, Integer.MAX_VALUE, 100, Integer.MIN_VALUE));
        assertEquals(1,pond.columns);assertEquals(1,pond.loadedChecks);
    }

    private static final class TestPond implements IslandFishingPolicy.PondAccess {
        private int columns,loadedChecks;
        private long boundary = 5;
        private boolean loaded = true;

        @Override public int minHeight() { return -64; }
        @Override public int maxHeight() { return 320; }
        @Override public boolean loaded(int x, int z) { loadedChecks++;return loaded; }
        @Override public boolean belongsToIsland(int x, int z) {
            columns++;
            return Math.abs((long)x) <= boundary && Math.abs((long)z) <= boundary;
        }
    }
}
