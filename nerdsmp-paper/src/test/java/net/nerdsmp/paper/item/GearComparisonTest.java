package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearComparisonTest {
    @Test void anythingBeatsAnEmptySlot() {
        assertTrue(GearComparison.upgrade(Map.of("defense", 1D), Map.of()));
    }

    @Test void equalGearIsNoUpgrade() {
        assertFalse(GearComparison.upgrade(Map.of("defense", 3D), Map.of("defense", 3D)));
    }

    @Test void fightingStatsOutweighLifeSkillStats() {
        assertFalse(GearComparison.upgrade(Map.of("mining-power", 8D), Map.of("defense", 3D)), "A miner's helmet doesn't beat real armour");
        assertTrue(GearComparison.upgrade(Map.of("defense", 3D, "max-health", 4D), Map.of("defense", 3D, "mining-power", 2D)));
    }

    @Test void gainsCountStatsThePieceAddsAndListTheBiggestFirst() {
        var candidate = Map.of("defense", 5D, "max-health", 4D, "mining-power", 1D);
        var current = Map.of("defense", 3D, "damage-percent", 2D);
        assertEquals(List.of("defense", "max-health"), GearComparison.gains(candidate, current, 2));
        assertEquals(3, GearComparison.gains(candidate, current, 5).size(), "A stat that drops is no gain");
    }
}
