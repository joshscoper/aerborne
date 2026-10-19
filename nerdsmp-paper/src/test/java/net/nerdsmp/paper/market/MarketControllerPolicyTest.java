package net.nerdsmp.paper.market;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.MarketService.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class MarketControllerPolicyTest {
    private final AccountId seller = AccountId.random();
    private final AccountId buyer = AccountId.random();
    private final UUID stallId = UUID.randomUUID();
    private final StallLocation location = new StallLocation(UUID.randomUUID(), 8, 65, -12);
    private final Stall stall = new Stall(stallId, seller, location);
    private final StallLocation elsewhere = new StallLocation(location.worldId(), 200, 80, 200);

    @Test
    void everyUnfinishedListingBlocksRelocationBeforeTheServiceIsCalled() {
        for (Status status : Status.values()) {
            boolean unfinished = status != Status.SOLD && status != Status.CANCELLED;
            assertEquals(unfinished, MarketController.blocksStallMove(stall, elsewhere, List.of(entry(status))), status.name());
        }
    }

    @Test
    void registeringTheSameLocationDoesNotBecomeAnUnnecessaryRefusal() {
        assertFalse(MarketController.blocksStallMove(stall, location, List.of(entry(Status.READY))));
        assertFalse(MarketController.blocksStallMove(null, location, List.of()));
        assertFalse(MarketController.blocksStallMove(stall, elsewhere, List.of()));
        Stall other = new Stall(UUID.randomUUID(), buyer, location);
        assertFalse(MarketController.blocksStallMove(other, elsewhere, List.of(entry(Status.CHECKOUT))));
    }

    @Test
    void paymentRetryIsOnlyOfferedToTransactionParticipants() {
        Entry<String> checkout = entry(Status.CHECKOUT);
        assertTrue(MarketController.canRetryPayment(seller, checkout));
        assertTrue(MarketController.canRetryPayment(buyer, checkout));
        assertFalse(MarketController.canRetryPayment(AccountId.random(), checkout));
        assertFalse(MarketController.canRetryPayment(null, checkout));
        assertTrue(MarketController.canRetryPayment(seller, entry(Status.FEE_PENDING)));
        assertFalse(MarketController.canRetryPayment(buyer, entry(Status.FEE_PENDING)));
    }

    @Test
    void inventoryReviewsAndCompletedEntriesNeverOfferPaymentRetry() {
        for (Status status : Status.values()) {
            if (status == Status.FEE_PENDING || status == Status.CHECKOUT) continue;
            assertFalse(MarketController.canRetryPayment(seller, entry(status)), status.name());
            assertFalse(MarketController.canRetryPayment(buyer, entry(status)), status.name());
        }
    }

    private Entry<String> entry(Status status) {
        boolean checkout = status == Status.CHECKOUT;
        Listing<String> listing = new Listing<>(UUID.randomUUID(), seller, stallId, "item", 100, status,
            checkout ? buyer : null, null);
        return new Entry<>(listing, 10, 5, checkout ? UUID.randomUUID() : null, null,
            checkout ? buyer : null, ReviewKind.NONE, "");
    }
}
