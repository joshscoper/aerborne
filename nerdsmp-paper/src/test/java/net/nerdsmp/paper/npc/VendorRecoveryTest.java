package net.nerdsmp.paper.npc;

import org.junit.jupiter.api.Test;
import static net.nerdsmp.paper.npc.NpcMarket.*;
import static net.nerdsmp.paper.npc.VendorRecovery.Action.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class VendorRecoveryTest {
    @Test void uncertainInventoryRequiresReviewForEitherSide() {
        for (Side side : Side.values()) assertEquals(REVIEW_INVENTORY, VendorRecovery.action(side, Phase.INVENTORY));
    }
    @Test void onlyConfirmedEscrowCanAutomaticallyPayASale() {
        assertEquals(CANCEL_UNTOUCHED_SALE, VendorRecovery.action(Side.SELL, Phase.RESERVED));
        assertEquals(SETTLE_SALE, VendorRecovery.action(Side.SELL, Phase.ESCROWED));
        assertEquals(REVIEW_INVENTORY, VendorRecovery.action(Side.SELL, Phase.PAID));
    }
    @Test void lostPurchaseResponsesRetrySameDebitAndConfirmedPaymentsRefund() {
        assertEquals(RETRY_PURCHASE, VendorRecovery.action(Side.BUY, Phase.RESERVED));
        assertEquals(REFUND_PURCHASE, VendorRecovery.action(Side.BUY, Phase.PAID));
        assertEquals(REFUND_PURCHASE, VendorRecovery.action(Side.BUY, Phase.REFUNDING));
    }
    @Test void terminalOutcomesCannotTransferAnythingAgain() {
        for (Side side : Side.values()) for (Phase phase : new Phase[]{Phase.COMPLETE, Phase.CANCELLED})
            assertEquals(NONE, VendorRecovery.action(side, phase));
    }
}
