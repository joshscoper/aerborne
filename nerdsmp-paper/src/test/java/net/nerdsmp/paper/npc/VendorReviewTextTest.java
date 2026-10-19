package net.nerdsmp.paper.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class VendorReviewTextTest {
    @Test
    void stuckTradePhasesAreWordsForStaff() {
        for (NpcMarket.Phase phase : NpcMarket.Phase.values()) {
            String name = NpcVendorTrading.phaseName(phase);
            assertFalse(name.isBlank() || name.contains("_") || name.equals(name.toUpperCase()), phase.name());
        }
    }
}
