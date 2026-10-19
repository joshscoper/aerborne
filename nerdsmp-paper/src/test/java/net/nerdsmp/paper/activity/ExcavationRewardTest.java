package net.nerdsmp.paper.activity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExcavationRewardTest {
    @Test
    void everyRarityHasACompleteWeightedRewardPool() {
        assertEquals(3, ExcavationReward.pool(FishingRolls.Rarity.COMMON).size());
        assertEquals(3, ExcavationReward.pool(FishingRolls.Rarity.UNCOMMON).size());
        assertEquals(2, ExcavationReward.pool(FishingRolls.Rarity.RARE).size());
        assertEquals(1, ExcavationReward.pool(FishingRolls.Rarity.LEGENDARY).size());

        for (FishingRolls.Rarity rarity : FishingRolls.Rarity.values()) {
            assertEquals(100, ExcavationReward.pool(rarity).stream()
                .mapToInt(ExcavationReward::weight).sum());
        }
    }

    @Test
    void rewardsAreVanillaItemsWithUsefulStackSizes() {
        assertEquals(9L, Arrays.stream(ExcavationReward.values())
            .map(ExcavationReward::material).distinct().count());
        for (ExcavationReward reward : ExcavationReward.values()) {
            assertTrue(reward.material() != org.bukkit.Material.AIR);
            assertTrue(reward.amount(0.0D) >= 1);
            assertTrue(reward.amount(1.0D) >= reward.amount(0.0D));
        }
    }

    @Test
    void selectionReachesBothEndsOfEveryPool() {
        for (FishingRolls.Rarity rarity : FishingRolls.Rarity.values()) {
            var pool = ExcavationReward.pool(rarity);
            assertEquals(pool.getFirst(), ExcavationReward.select(rarity, 0.0D));
            assertEquals(pool.getLast(), ExcavationReward.select(rarity, 1.0D));
        }
    }
}
