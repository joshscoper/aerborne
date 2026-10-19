package net.nerdsmp.paper.npc;

import static net.nerdsmp.paper.npc.NpcMarket.*;

/** No inventory-boundary outcome is inferred from a payment receipt. */
final class VendorRecovery {
    enum Action { NONE, CANCEL_UNTOUCHED_SALE, SETTLE_SALE, REFUND_PURCHASE, RETRY_PURCHASE, REVIEW_INVENTORY }
    private VendorRecovery() { }
    static Action action(Side side, Phase phase) {
        if (phase == Phase.COMPLETE || phase == Phase.CANCELLED) return Action.NONE;
        if (phase == Phase.INVENTORY) return Action.REVIEW_INVENTORY;
        if (side == Side.SELL) return switch (phase) {
            case RESERVED -> Action.CANCEL_UNTOUCHED_SALE;
            case ESCROWED -> Action.SETTLE_SALE;
            default -> Action.REVIEW_INVENTORY;
        };
        return switch (phase) {
            case RESERVED -> Action.RETRY_PURCHASE;
            case PAID, REFUNDING -> Action.REFUND_PURCHASE;
            default -> Action.REVIEW_INVENTORY;
        };
    }
}
