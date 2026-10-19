package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.MarketService;
import net.nerdsmp.paper.market.DurableMarketService;
import net.nerdsmp.paper.market.MarketStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static net.nerdsmp.api.service.MarketService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

final class AuctionMarketServiceTest {
    @TempDir Path directory;
    private final AccountId seller = AccountId.random();
    private final AccountId buyer = AccountId.random();
    private final AccountId competitor = AccountId.random();
    private final MarketTestEconomy economy = funded();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);
    private static final String ITEM = Base64.getEncoder().encodeToString("gear-with-sockets-and-enchants".getBytes(StandardCharsets.UTF_8));
    private static final MarketService.StallLocation LOCATION = new MarketService.StallLocation(UUID.randomUUID(), 8, 65, -12);

    @Test
    void confirmedAuctionIsDurableAndCannotUseFixedPriceActions() throws Exception {
        var market = market();
        UUID id = auction(market, 10);
        assertTrue(market.isAuction(id));
        assertEquals(MarketService.Status.AUCTION, market.entry(id).orElseThrow().listing().status());
        assertEquals(UNAVAILABLE, await(market.reserve(id, buyer, CLOCK.instant().plusSeconds(30))).outcome());
        assertEquals(UNAVAILABLE, await(market.checkout(id, buyer, 10)).outcome());
        assertEquals(UNAVAILABLE, await(market.cancel(id, seller)).outcome());
        assertTrue(await(market.activeListings()).isEmpty());
        var restarted = market();
        assertTrue(restarted.isAuction(id));
        assertTrue(await(restarted.recover()).isEmpty());
        assertEquals(ACTIVE, await(restarted.retry(id, seller)).outcome());
        assertEquals(DENIED, await(restarted.retry(id, competitor)).outcome());
        assertEquals(990, economy.marks(seller));
        assertEquals(1000, economy.marks(buyer));
        assertEquals(1, economy.appliedDebits(seller));
        assertEquals(0, economy.appliedCredits(seller));
        assertEquals(ITEM, restarted.entry(id).orElseThrow().listing().item());
    }

    @Test
    void zeroFeeAuctionActivatesWithoutAZeroValueEconomicOperation() throws Exception {
        var market = market();
        UUID id = auction(market, 0);
        assertEquals(MarketService.Status.AUCTION, market.entry(id).orElseThrow().listing().status());
        assertEquals(0, economy.appliedDebits(seller));
    }

    @Test
    void uncertainAuctionDepositUsesExistingReviewAndRetainsItsVenue() throws Exception {
        UUID id = prepared(market(), 10);
        var restarted = market();
        await(restarted.recover());
        assertTrue(restarted.isAuction(id));
        assertEquals(MarketService.Status.REVIEW, restarted.entry(id).orElseThrow().listing().status());
        assertEquals(ACTIVE, await(restarted.resolve(id, MarketService.Resolution.DEPOSIT_TAKEN,
            "Moderator", "Saved inventory confirms item removed")).outcome());
        assertEquals(MarketService.Status.AUCTION, restarted.entry(id).orElseThrow().listing().status());
        assertTrue(restarted.isAuction(id));
        assertEquals(990, economy.marks(seller));
    }

    @Test
    void lostAuctionFeeAcknowledgementRestoresAuctionRatherThanStoreListing() throws Exception {
        var market = market();
        UUID id = prepared(market, 10);
        economy.loseNextDebitResponse(seller);
        assertEquals(PENDING, await(market.confirmDeposit(id, seller)).outcome());
        var restarted = market();
        await(restarted.recover());
        assertTrue(restarted.isAuction(id));
        assertEquals(MarketService.Status.AUCTION, restarted.entry(id).orElseThrow().listing().status());
        assertEquals(1, economy.appliedDebits(seller));
    }

    @Test
    void declinedAuctionFeeReturnsTheOriginalItemToTheSeller() throws Exception {
        var market = market();
        UUID id = prepared(market, 10);
        economy.fund(seller, 5);
        assertEquals(READY, await(market.confirmDeposit(id, seller)).outcome());
        assertTrue(market.isAuction(id));
        assertEquals(seller, market.entry(id).orElseThrow().recipient());
        assertEquals(ITEM, market.entry(id).orElseThrow().listing().item());
        UUID token = await(market.beginDelivery(id, seller)).entry().orElseThrow().deliveryId();
        assertEquals(CANCELLED, await(market.finishDelivery(id, seller, token)).outcome());
        assertTrue(market().isAuction(id));
        assertEquals(5, economy.marks(seller));
        assertEquals(0, economy.appliedCredits(seller));
    }

    @Test
    void auctionReturnRetainsMetadataAndCannotBeSettledAfterCancellation() throws Exception {
        var market = market();
        UUID id = auction(market, 10);
        assertEquals(DENIED, await(market.cancelAuction(id, buyer)).outcome());
        assertEquals(READY, await(market.cancelAuction(id, seller)).outcome());
        assertEquals(UNAVAILABLE, await(market.settleAuction(id, buyer, 200, 10, UUID.randomUUID())).outcome());
        var reopened = market();
        assertEquals(ITEM, reopened.entry(id).orElseThrow().listing().item());
        assertEquals(seller, reopened.entry(id).orElseThrow().recipient());
        UUID token = await(reopened.beginDelivery(id, seller)).entry().orElseThrow().deliveryId();
        assertEquals(CANCELLED, await(reopened.finishDelivery(id, seller, token)).outcome());
        assertTrue(market().isAuction(id));
        assertEquals(990, economy.marks(seller));
        assertEquals(0, economy.appliedCredits(seller));
    }

    @Test
    void settlementUsesAlreadyEscrowedBidAndPaysTaxAdjustedProceedsExactlyOnce() throws Exception {
        var market = market();
        UUID id = auction(market, 10);
        UUID settlement = UUID.randomUUID();
        assertEquals(READY, await(market.settleAuction(id, buyer, 201, 5, settlement)).outcome());
        var entry = market.entry(id).orElseThrow();
        assertEquals(201, entry.listing().price());
        assertEquals(buyer, entry.recipient());
        assertEquals(settlement, entry.checkoutId());
        assertEquals(1181, economy.marks(seller));
        assertEquals(1000, economy.marks(buyer));
        assertEquals(0, economy.appliedDebits(buyer));
        assertEquals(1, economy.appliedCredits(seller));
        assertEquals(READY, await(market.settleAuction(id, buyer, 201, 5, settlement)).outcome());
        assertEquals(DENIED, await(market.settleAuction(id, competitor, 201, 5, settlement)).outcome());
        assertEquals(DENIED, await(market.settleAuction(id, buyer, 202, 5, settlement)).outcome());
        assertEquals(DENIED, await(market.settleAuction(id, buyer, 201, 6, settlement)).outcome());
        assertEquals(DENIED, await(market.settleAuction(id, buyer, 201, 5, UUID.randomUUID())).outcome());
        UUID token = await(market.beginDelivery(id, buyer)).entry().orElseThrow().deliveryId();
        assertEquals(DELIVERING, await(market.settleAuction(id, buyer, 201, 5, settlement)).outcome());
        assertEquals(SOLD, await(market.finishDelivery(id, buyer, token)).outcome());
        assertEquals(SOLD, await(market().settleAuction(id, buyer, 201, 5, settlement)).outcome());
        assertEquals(1, economy.appliedCredits(seller));
    }

    @Test
    void losingPayoutAcknowledgementRecoversWithoutChargingBuyerOrCreditingSellerTwice() throws Exception {
        var market = market();
        UUID id = auction(market, 10);
        UUID settlement = UUID.randomUUID();
        economy.loseNextCreditResponse(seller);
        assertEquals(PENDING, await(market.settleAuction(id, buyer, 200, 10, settlement)).outcome());
        assertEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        assertEquals(UNAVAILABLE, await(market.checkout(id, buyer, 10)).outcome());
        assertEquals(DENIED, await(market.settleAuction(id, competitor, 200, 10, settlement)).outcome());
        var restarted = market();
        await(restarted.recover());
        assertEquals(MarketService.Status.READY, restarted.entry(id).orElseThrow().listing().status());
        assertTrue(restarted.isAuction(id));
        assertEquals(1170, economy.marks(seller));
        assertEquals(1000, economy.marks(buyer));
        assertEquals(0, economy.appliedDebits(buyer));
        assertEquals(1, economy.appliedCredits(seller));
    }

    @Test
    void settlementSaveFailureDoesNotAuthorizePayoutOrPublishWinningPrice() throws Exception {
        var store = new FailingStore(file());
        var market = new DurableMarketService(store, economy, CLOCK);
        UUID id = auction(market, 10);
        UUID settlement = UUID.randomUUID();
        byte[] before = Files.readAllBytes(file().toPath());
        store.failAction = "AUCTION_SETTLEMENT_LOCKED";
        assertEquals(SAVE_FAILED, await(market.settleAuction(id, buyer, 200, 10, settlement)).outcome());
        assertArrayEquals(before, Files.readAllBytes(file().toPath()));
        assertEquals(MarketService.Status.AUCTION, market.entry(id).orElseThrow().listing().status());
        assertEquals(100, market.entry(id).orElseThrow().listing().price());
        assertEquals(0, economy.appliedCredits(seller));
        assertEquals(READY, await(market.settleAuction(id, buyer, 200, 10, settlement)).outcome());
        assertEquals(1, economy.appliedCredits(seller));
    }

    @Test
    void payoutConfirmationSaveFailureRecoversFromTheSameEconomicReceipt() throws Exception {
        var store = new FailingStore(file());
        var market = new DurableMarketService(store, economy, CLOCK);
        UUID id = auction(market, 10);
        UUID settlement = UUID.randomUUID();
        store.failAction = "PAYOUT_CONFIRMED";
        assertEquals(SAVE_FAILED, await(market.settleAuction(id, buyer, 200, 10, settlement)).outcome());
        assertEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        assertEquals(1170, economy.marks(seller));
        assertEquals(READY, await(market().settleAuction(id, buyer, 200, 10, settlement)).outcome());
        assertEquals(1, economy.appliedCredits(seller));
        assertEquals(0, economy.appliedDebits(buyer));
    }

    @Test
    void readySaveFailureRecoversWithoutRepeatingConfirmedPayout() throws Exception {
        var store = new FailingStore(file());
        var market = new DurableMarketService(store, economy, CLOCK);
        UUID id = auction(market, 10);
        store.failAction = "PAYMENT_COMPLETE";
        assertEquals(SAVE_FAILED, await(market.settleAuction(id, buyer, 200, 10, UUID.randomUUID())).outcome());
        assertEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        var restarted = market();
        await(restarted.recover());
        assertEquals(MarketService.Status.READY, restarted.entry(id).orElseThrow().listing().status());
        assertEquals(1, economy.appliedCredits(seller));
        assertEquals(0, economy.appliedDebits(buyer));
    }

    @Test
    void concurrentSameSettlementSharesPayoutAndDifferentSettlementCannotStealTheItem() throws Exception {
        var held = new HoldingCreditEconomy(economy);
        var market = new DurableMarketService(new MarketStore(file()), held, CLOCK);
        UUID id = auction(market, 10);
        UUID settlement = UUID.randomUUID();
        held.hold = true;
        var first = market.settleAuction(id, buyer, 200, 10, settlement).toCompletableFuture();
        assertFalse(first.isDone());
        var repeated = market.settleAuction(id, buyer, 200, 10, settlement).toCompletableFuture();
        assertSame(first, repeated);
        assertEquals(DENIED, await(market.settleAuction(id, competitor, 200, 10, UUID.randomUUID())).outcome());
        assertEquals(UNAVAILABLE, await(market.cancelAuction(id, seller)).outcome());
        held.release();
        assertEquals(READY, first.join().outcome());
        assertEquals(1, economy.appliedCredits(seller));
        assertEquals(buyer, market.entry(id).orElseThrow().recipient());
    }

    @Test
    void invalidSettlementsAndFixedPriceItemsCannotUseAuctionAuthorization() throws Exception {
        var market = market();
        UUID id = auction(market, 10);
        UUID key = UUID.randomUUID();
        assertEquals(INVALID, await(market.settleAuction(id, buyer, 99, 10, key)).outcome());
        assertEquals(INVALID, await(market.settleAuction(id, buyer, 0, 10, key)).outcome());
        assertEquals(INVALID, await(market.settleAuction(id, buyer, -1, 10, key)).outcome());
        assertEquals(INVALID, await(market.settleAuction(id, null, 100, 10, key)).outcome());
        assertEquals(INVALID, await(market.settleAuction(id, buyer, 100, -1, key)).outcome());
        assertEquals(INVALID, await(market.settleAuction(id, buyer, 100, 101, key)).outcome());
        assertEquals(INVALID, await(market.settleAuction(id, buyer, 100, 10, null)).outcome());
        assertEquals(INVALID, await(market.settleAuction(null, buyer, 100, 10, key)).outcome());
        assertEquals(INVALID, await(market.cancelAuction(null, seller)).outcome());
        assertFalse(market.isAuction(null));
        assertEquals(DENIED, await(market.settleAuction(id, seller, 100, 10, key)).outcome());
        assertEquals(MarketService.Status.AUCTION, market.entry(id).orElseThrow().listing().status());
        var fixed = await(market.prepareListing(seller, market.stallFor(seller).orElseThrow().id(), ITEM, 100, 0));
        UUID fixedId = fixed.entry().orElseThrow().listing().id();
        await(market.confirmDeposit(fixedId, seller));
        assertFalse(market.isAuction(fixedId));
        assertEquals(UNAVAILABLE, await(market.settleAuction(fixedId, buyer, 100, 10, key)).outcome());
        assertEquals(UNAVAILABLE, await(market.cancelAuction(fixedId, seller)).outcome());
        assertEquals(1, await(market.activeListings()).size());
        assertEquals(READY, await(market.cancel(fixedId, seller)).outcome());
        assertEquals(0, economy.appliedCredits(seller));
    }

    @Test
    void maximumWinningPriceWithFullTaxCannotOverflowOrIssueZeroValueCredit() throws Exception {
        var market = market();
        UUID id = auction(market, 0);
        assertEquals(READY, await(market.settleAuction(id, buyer, Long.MAX_VALUE, 100, UUID.randomUUID())).outcome());
        assertEquals(Long.MAX_VALUE, market.entry(id).orElseThrow().listing().price());
        assertEquals(1000, economy.marks(seller));
        assertEquals(1000, economy.marks(buyer));
        assertEquals(0, economy.appliedCredits(seller));
        assertTrue(market().isAuction(id));
    }

    private DurableMarketService market() throws IOException { return new DurableMarketService(new MarketStore(file()), economy, CLOCK); }
    private File file() { return directory.resolve("market.yml").toFile(); }
    private UUID prepared(DurableMarketService market, long fee) {
        var stall = await(market.registerStall(seller, LOCATION));
        var result = await(market.prepareAuctionListing(seller, stall.id(), ITEM, 100, fee));
        assertEquals(PREPARED, result.outcome());
        UUID id = result.entry().orElseThrow().listing().id();
        assertTrue(market.isAuction(id));
        return id;
    }
    private UUID auction(DurableMarketService market, long fee) {
        UUID id = prepared(market, fee);
        assertEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        return id;
    }
    private MarketTestEconomy funded() {
        var result = new MarketTestEconomy();
        result.fund(seller, 1000); result.fund(buyer, 1000); result.fund(competitor, 1000);
        return result;
    }
    private static <T> T await(CompletionStage<T> stage) { return stage.toCompletableFuture().join(); }

    private static final class FailingStore extends MarketStore {
        private String failAction;
        FailingStore(File file) { super(file); }
        @Override public void save(Snapshot snapshot) throws IOException {
            if (failAction != null && !snapshot.audit().isEmpty()
                && failAction.equals(snapshot.audit().getLast().action())) {
                failAction = null;
                throw new IOException("Injected atomic commit failure");
            }
            super.save(snapshot);
        }
    }

    private static final class HoldingCreditEconomy implements EconomyService {
        private final MarketTestEconomy delegate;
        private boolean hold;
        private CompletableFuture<TransactionResult> pending;
        private TransactionResult result;
        HoldingCreditEconomy(MarketTestEconomy delegate) { this.delegate = delegate; }
        void release() { pending.complete(result); }
        @Override public CompletionStage<Long> balance(AccountId accountId) { return delegate.balance(accountId); }
        @Override public CompletionStage<TransactionResult> debit(AccountId account, long amount, TransactionReason reason, String key) {
            return delegate.debit(account, amount, reason, key);
        }
        @Override public CompletionStage<TransactionResult> credit(AccountId account, long amount, TransactionReason reason, String key) {
            var applied = delegate.credit(account, amount, reason, key);
            if (!hold) return applied;
            hold = false; result = await(applied); pending = new CompletableFuture<>(); return pending;
        }
        @Override public CompletionStage<TransactionResult> transfer(AccountId source, AccountId destination, long amount,
                                                                    TransactionReason reason, String key) {
            return delegate.transfer(source, destination, amount, reason, key);
        }
    }
}
