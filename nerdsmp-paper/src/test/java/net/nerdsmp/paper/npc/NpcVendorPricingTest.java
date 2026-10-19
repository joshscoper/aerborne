package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NpcVendorPricingTest {
    @Test
    void standingDiscountsPurchasesAndImprovesPayoutsWithoutCrossingThem() {
        NpcContent.FactionDefinition faction = new NpcContent.FactionDefinition(
            new FactionId("test_faction"), "Test Faction", "", List.of(
                new NpcContent.TierDefinition("neutral", "Neutral", "gray", -1000, 1.0D, true),
                new NpcContent.TierDefinition("honored", "Honored", "gold", 600, 0.8D, true)
            )
        );
        NpcContent.ReputationView neutral = new NpcContent.ReputationView(faction, faction.tiers().getFirst(), 0);
        NpcContent.ReputationView honored = new NpcContent.ReputationView(faction, faction.tiers().getLast(), 600);

        assertEquals(100L, neutral.price(100L));
        assertEquals(40L, neutral.payout(40L));
        assertEquals(80L, honored.price(100L));
        assertEquals(48L, honored.payout(40L));
        assertTrue(honored.payout(40L) < honored.price(100L));
    }
}
