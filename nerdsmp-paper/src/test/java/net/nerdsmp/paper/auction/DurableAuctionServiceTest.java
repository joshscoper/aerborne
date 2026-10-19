package net.nerdsmp.paper.auction;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.MarketService;
import net.nerdsmp.paper.market.DurableMarketService;
import net.nerdsmp.paper.market.MarketStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static net.nerdsmp.paper.auction.Auction.Status.BID_PENDING;
import static net.nerdsmp.paper.auction.Auction.Status.SETTLEMENT_PENDING;
import static net.nerdsmp.paper.auction.Auction.Status.WAITING_FOR_ITEM;
import static net.nerdsmp.paper.auction.DurableAuctionService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

final class DurableAuctionServiceTest {
    @TempDir Path directory;
    private final AccountId seller = AccountId.random();
    private final AccountId first = AccountId.random();
    private final AccountId second = AccountId.random();
    private final AccountId stranger = AccountId.random();
    private final MutableClock clock = new MutableClock();
    private AuctionTestEconomy economy;
    private DurableMarketService market;
    private FailingStore store;
    private DurableAuctionService auctions;
    private UUID stall;

    @BeforeEach void setup() throws Exception {
        economy = new AuctionTestEconomy();
        economy.fund(seller, 10_000);
        economy.fund(first, 10_000);
        economy.fund(second, 10_000);
        market = new DurableMarketService(new MarketStore(directory.resolve("market.yml").toFile()), economy, clock);
        stall = await(market.registerStall(seller, new MarketService.StallLocation(UUID.randomUUID(), 0, 70, 0))).id();
        store = new FailingStore(directory.resolve("auctions.yml"));
        auctions = new DurableAuctionService(store, market, economy, clock, 5);
    }

    @Test void itemMustBeDepositedAndFeesConfirmedBeforeBidding() {
        UUID id = prepare(100, 300L);
        assertEquals(WAITING_FOR_ITEM, value(id).status());
        assertEquals(UNAVAILABLE, await(auctions.bid(id, first, 100, value(id).revision())).outcome());
        assertEquals(PENDING, await(auctions.activate(id, seller)).outcome());
        assertEquals(0, economy.debits(first));
        assertEquals(MarketService.Outcome.ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        assertEquals(DurableAuctionService.Outcome.ACTIVE, await(auctions.activate(id, seller)).outcome());
        assertEquals(Auction.Status.ACTIVE, value(id).status());
    }

    @Test void fixedPriceListingsCannotBecomeAuctions() {
        UUID id = await(market.prepareListing(seller, stall, item(), 100, 0)).entry().orElseThrow().listing().id();
        assertEquals(INVALID, await(auctions.prepare(id, seller, 100, null, 24)).outcome());
        assertTrue(auctions.all().isEmpty());
    }

    @Test void firstBidOutbidRefundOwnRaiseAndExpiryConserveCurrency() {
        UUID id = active(100, null);
        assertEquals(BID_ACCEPTED, bid(id, first, 100).outcome());
        assertEquals(9_900, economy.marks(first));
        assertEquals(INVALID, bid(id, second, 104).outcome());
        assertEquals(BID_ACCEPTED, bid(id, second, 105).outcome());
        assertEquals(10_000, economy.marks(first));
        assertEquals(9_895, economy.marks(second));
        assertEquals(BID_ACCEPTED, bid(id, second, 111).outcome());
        assertEquals(9_889, economy.marks(second));
        assertEquals(0, economy.credits(second));
        assertEquals(2, economy.debits(second));
        clock.advance(Duration.ofHours(25));
        assertEquals(COMPLETE, await(auctions.tick()).getFirst().outcome());
        assertEquals(Auction.Status.COMPLETE, value(id).status());
        assertEquals(10_106, economy.marks(seller));
        assertEquals(9_889, economy.marks(second));
        assertEquals(MarketService.Status.READY, market.entry(id).orElseThrow().listing().status());
        assertEquals(second, market.entry(id).orElseThrow().recipient());
        assertTrue(await(auctions.tick()).isEmpty());
        assertEquals(COMPLETE, await(auctions.retry(id, second)).outcome());
        assertEquals(1, economy.credits(seller));
    }

    @Test void buyNowRefundsThePreviousBidAndSettlesWithoutDoubleDebit() {
        UUID id = active(100, 250L);
        bid(id, first, 100);
        assertEquals(COMPLETE, await(auctions.buyNow(id, second, value(id).revision())).outcome());
        assertEquals(10_000, economy.marks(first));
        assertEquals(9_750, economy.marks(second));
        assertEquals(10_238, economy.marks(seller));
        assertEquals(1, economy.debits(second));
        assertEquals(250, market.entry(id).orElseThrow().listing().price());
        assertEquals(MarketService.Outcome.UNAVAILABLE, await(market.checkout(id, stranger, 0)).outcome());
    }

    @Test void ownBuyNowDebitsOnlyTheDifference() {
        UUID id = active(100, 103L);
        bid(id, first, 100);
        assertEquals(COMPLETE, await(auctions.buyNow(id, first, value(id).revision())).outcome());
        assertEquals(9_897, economy.marks(first));
        assertEquals(0, economy.credits(first));
        assertEquals(2, economy.debits(first));
    }

    @Test void bidEqualToBuyNowEndsTheAuctionAndAboveItIsRejected() {
        UUID id = active(100, 200L);
        assertEquals(INVALID, bid(id, first, 201).outcome());
        assertEquals(COMPLETE, bid(id, first, 200).outcome());
        assertEquals(Auction.Status.COMPLETE, value(id).status());
    }

    @Test void noBidExpiryReturnsTheItemWithoutGivingOrTakingMarks() {
        UUID id = active(100, null);
        clock.advance(Duration.ofHours(24));
        assertEquals(DurableAuctionService.Outcome.CANCELLED, await(auctions.tick()).getFirst().outcome());
        assertEquals(Auction.Status.CANCELLED, value(id).status());
        assertEquals(seller, market.entry(id).orElseThrow().recipient());
        assertEquals(MarketService.Status.READY, market.entry(id).orElseThrow().listing().status());
        assertEquals(10_000, economy.marks(seller));
        assertEquals(0, economy.credits(seller));
    }

    @Test void aLostCancellationAcknowledgementCanRetryEvenAfterTheSellerCollects() throws Exception {
        UUID id = active(100, null);
        var loseResponse = new java.util.concurrent.atomic.AtomicBoolean(true);
        @SuppressWarnings("unchecked") MarketService<String> wrapped = (MarketService<String>) java.lang.reflect.Proxy.newProxyInstance(
            MarketService.class.getClassLoader(), new Class<?>[]{MarketService.class}, (proxy, method, args) -> {
                Object result = method.invoke(market, args);
                if (method.getName().equals("cancelAuction") && loseResponse.getAndSet(false)) {
                    return ((CompletionStage<?>) result).thenCompose(ignored -> java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("Cancellation committed; response lost")));
                }
                return result;
            });
        auctions = new DurableAuctionService(store, wrapped, economy, clock, 5);
        assertEquals(PENDING, await(auctions.cancel(id, seller, value(id).revision())).outcome());
        assertEquals(Auction.Status.CANCEL_PENDING, value(id).status());
        UUID token = await(market.beginDelivery(id, seller)).entry().orElseThrow().deliveryId();
        await(market.finishDelivery(id, seller, token));
        assertEquals(MarketService.Status.CANCELLED, market.entry(id).orElseThrow().listing().status());
        assertEquals(DurableAuctionService.Outcome.CANCELLED, await(auctions.retry(id, seller)).outcome());
        assertEquals(0, economy.debits(seller));
        assertEquals(0, economy.credits(seller));
    }

    @Test void unconfirmedPendingBidWithMissingEscrowIsQuarantinedBeforeAnyFurtherPayment() throws Exception {
        UUID id = active(100, null);
        economy.loseNextDebit(first);
        bid(id, first, 100);
        var empty = new DurableMarketService(new MarketStore(directory.resolve("empty-market.yml").toFile()), economy, clock);
        auctions = new DurableAuctionService(store, empty, economy, clock, 5);
        assertEquals(REVIEW, await(auctions.recover()).getFirst().outcome());
        assertEquals(Auction.Status.REVIEW, value(id).status());
        assertNotNull(value(id).pending());
        assertEquals(1, economy.debits(first));
        assertEquals(9_900, economy.marks(first));
        assertEquals(REVIEW, await(auctions.retry(id, first)).outcome());
    }

    @Test void onlyTheSellerCanCancelAndOnlyBeforeBids() {
        UUID id = active(100, null);
        assertEquals(DENIED, await(auctions.cancel(id, stranger, value(id).revision())).outcome());
        assertEquals(DENIED, bid(id, seller, 100).outcome());
        bid(id, first, 100);
        assertEquals(UNAVAILABLE, await(auctions.cancel(id, seller, value(id).revision())).outcome());
        assertEquals(DENIED, await(auctions.retry(id, stranger)).outcome());
        UUID unbid = active(100, null);
        assertEquals(DurableAuctionService.Outcome.CANCELLED, await(auctions.cancel(unbid, seller, value(unbid).revision())).outcome());
    }

    @Test void staleViewsCannotOverbidCancelOrBuy() {
        UUID id = active(100, 300L);
        long oldRevision = value(id).revision();
        bid(id, first, 100);
        assertEquals(STALE, await(auctions.bid(id, second, 200, oldRevision)).outcome());
        assertEquals(STALE, await(auctions.buyNow(id, second, oldRevision)).outcome());
        assertEquals(STALE, await(auctions.cancel(id, seller, oldRevision)).outcome());
        assertEquals(0, economy.debits(second));
    }

    @Test void insufficientNewBidKeepsTheFundedOldBidAndNoRefund() {
        UUID id = active(100, null);
        bid(id, first, 100);
        economy.fund(second, 99);
        assertEquals(INSUFFICIENT_FUNDS, bid(id, second, 105).outcome());
        assertEquals(first, value(id).highBidder());
        assertEquals(100, value(id).highBid());
        assertEquals(9_900, economy.marks(first));
        assertEquals(0, economy.credits(first));
        assertEquals(Auction.Status.ACTIVE, value(id).status());
    }

    @Test void lostBidDebitAcknowledgementRecoversAcrossRestartOnce() throws Exception {
        UUID id = active(100, null);
        economy.loseNextDebit(first);
        assertEquals(PENDING, bid(id, first, 100).outcome());
        assertEquals(BID_PENDING, value(id).status());
        restart();
        assertEquals(BID_ACCEPTED, await(auctions.recover()).getFirst().outcome());
        assertEquals(9_900, economy.marks(first));
        assertEquals(1, economy.debits(first));
        assertEquals(first, value(id).highBidder());
    }

    @Test void lostOutbidRefundAcknowledgementRecoversWithoutRepeatingTheRefund() throws Exception {
        UUID id = active(100, null);
        bid(id, first, 100);
        economy.loseNextCredit(first);
        assertEquals(PENDING, bid(id, second, 105).outcome());
        assertEquals(10_000, economy.marks(first));
        restart();
        assertEquals(BID_ACCEPTED, await(auctions.recover()).getFirst().outcome());
        assertEquals(second, value(id).highBidder());
        assertEquals(10_000, economy.marks(first));
        assertEquals(9_895, economy.marks(second));
        assertEquals(1, economy.credits(first));
        assertEquals(1, economy.debits(second));
    }

    @Test void lostSellerPayoutAcknowledgementRecoversThroughMarketWithoutExtraDebitOrPayout() throws Exception {
        UUID id = active(100, 250L);
        economy.loseNextCredit(seller);
        assertEquals(PENDING, await(auctions.buyNow(id, first, value(id).revision())).outcome());
        assertEquals(SETTLEMENT_PENDING, value(id).status());
        assertEquals(10_238, economy.marks(seller));
        restart();
        await(market.recover());
        assertEquals(COMPLETE, await(auctions.recover()).getFirst().outcome());
        assertEquals(1, economy.debits(first));
        assertEquals(1, economy.credits(seller));
        assertEquals(9_750, economy.marks(first));
    }

    @Test void diskFailureBeforeIntentDoesNotChargeTheBidder() {
        UUID id = active(100, null);
        store.failNext = true;
        assertEquals(SAVE_FAILED, bid(id, first, 100).outcome());
        assertEquals(0, economy.debits(first));
        assertEquals(Auction.Status.ACTIVE, value(id).status());
    }

    @Test void diskFailureAfterDebitKeepsTheSavedIntentAndReplaysTheSamePayment() throws Exception {
        UUID id = active(100, null);
        economy.afterNextDebit = () -> store.failNext = true;
        assertEquals(SAVE_FAILED, bid(id, first, 100).outcome());
        assertEquals(BID_PENDING, value(id).status());
        assertFalse(value(id).pending().debitConfirmed());
        restart();
        assertEquals(BID_ACCEPTED, await(auctions.recover()).getFirst().outcome());
        assertEquals(1, economy.debits(first));
        assertEquals(9_900, economy.marks(first));
    }

    @Test void diskFailureAfterRefundKeepsIntentAndNeverCreditsAgain() throws Exception {
        UUID id = active(100, null);
        bid(id, first, 100);
        economy.afterNextCredit = () -> store.failNext = true;
        assertEquals(SAVE_FAILED, bid(id, second, 105).outcome());
        assertTrue(value(id).pending().debitConfirmed());
        assertFalse(value(id).pending().refundConfirmed());
        restart();
        assertEquals(BID_ACCEPTED, await(auctions.recover()).getFirst().outcome());
        assertEquals(1, economy.credits(first));
        assertEquals(10_000, economy.marks(first));
    }

    @Test void simultaneousOffersCannotSpendWhileAnEarlierPaymentIsInFlight() {
        UUID id = active(100, null);
        economy.holdNextDebit(first);
        var pending = auctions.bid(id, first, 100, value(id).revision()).toCompletableFuture();
        assertFalse(pending.isDone());
        assertEquals(PENDING, bid(id, second, 105).outcome());
        assertEquals(0, economy.debits(second));
        assertSame(pending, auctions.retry(id, first).toCompletableFuture());
        economy.releaseDebit();
        assertEquals(BID_ACCEPTED, pending.join().outcome());
        assertEquals(first, value(id).highBidder());
    }

    @Test void aHungPaymentDoesNotBlockOtherExpiredAuctionsOrRepeatRecoveryCallbacks() {
        UUID hung = active(100, null);
        economy.holdNextDebit(first);
        var payment = auctions.bid(hung, first, 100, value(hung).revision()).toCompletableFuture();
        assertFalse(payment.isDone());
        UUID other = active(100, null);
        clock.advance(Duration.ofHours(25));
        var recovery = auctions.tick().toCompletableFuture();
        assertTrue(recovery.isDone());
        assertEquals(1, recovery.join().size());
        assertEquals(other, recovery.join().getFirst().auction().orElseThrow().id());
        assertEquals(DurableAuctionService.Outcome.CANCELLED, recovery.join().getFirst().outcome());
        assertFalse(payment.isDone());
        assertTrue(await(auctions.tick()).isEmpty());
        assertEquals(1, economy.debits(first));
        economy.releaseDebit();
        assertEquals(BID_ACCEPTED, payment.join().outcome());
        assertEquals(COMPLETE, await(auctions.tick()).getFirst().outcome());
        assertEquals(first, market.entry(hung).orElseThrow().recipient());
    }

    @Test void expiredAuctionsRejectBidsButFundedPendingBidsStillRecover() throws Exception {
        UUID id = active(100, null);
        economy.loseNextDebit(first);
        bid(id, first, 100);
        clock.advance(Duration.ofHours(25));
        restart();
        assertEquals(BID_ACCEPTED, await(auctions.recover()).getFirst().outcome());
        assertEquals(UNAVAILABLE, bid(id, second, 105).outcome());
        assertEquals(COMPLETE, await(auctions.tick()).getFirst().outcome());
        assertEquals(first, market.entry(id).orElseThrow().recipient());
    }

    @Test void preparedPlanCanActivateAfterCrashWithoutInventingAnItem() throws Exception {
        UUID id = prepare(100, null);
        await(market.confirmDeposit(id, seller));
        restart();
        assertEquals(DurableAuctionService.Outcome.ACTIVE, await(auctions.recover()).getFirst().outcome());
        assertEquals(Auction.Status.ACTIVE, value(id).status());
        assertEquals(0, economy.debits(first));
    }

    @Test void rePreparingTheSameDurationDoesNotChangeTheEndTimeOrChargeAnything() {
        UUID id = prepare(100, 300L);
        Instant end = value(id).endsAt();
        clock.advance(Duration.ofMinutes(1));
        assertEquals(PREPARED, await(auctions.prepare(id, seller, 100, 300L, 24)).outcome());
        assertEquals(end, value(id).endsAt());
        assertEquals(0, economy.debits(seller));
        assertEquals(INVALID, await(auctions.prepare(id, seller, 100, 300L, 12)).outcome());
    }

    @Test void diskFailureAfterSellerPayoutRecoversCompletionWithoutAnySecondPayment() throws Exception {
        UUID id = active(100, 250L);
        economy.afterNextCredit = () -> store.failNext = true;
        assertEquals(SAVE_FAILED, await(auctions.buyNow(id, first, value(id).revision())).outcome());
        assertEquals(SETTLEMENT_PENDING, value(id).status());
        assertEquals(MarketService.Status.READY, market.entry(id).orElseThrow().listing().status());
        restart();
        assertEquals(COMPLETE, await(auctions.recover()).getFirst().outcome());
        assertEquals(1, economy.debits(first));
        assertEquals(1, economy.credits(seller));
        assertEquals(10_238, economy.marks(seller));
    }

    @Test void collectingBeforeAuctionCompletionIsSavedStillRecoversAsComplete() throws Exception {
        UUID id = active(100, 250L);
        economy.afterNextCredit = () -> store.failNext = true;
        await(auctions.buyNow(id, first, value(id).revision()));
        var delivery = await(market.beginDelivery(id, first));
        UUID token = delivery.entry().orElseThrow().deliveryId();
        await(market.finishDelivery(id, first, token));
        assertEquals(MarketService.Status.SOLD, market.entry(id).orElseThrow().listing().status());
        restart();
        assertEquals(COMPLETE, await(auctions.recover()).getFirst().outcome());
        assertEquals(1, economy.debits(first));
        assertEquals(1, economy.credits(seller));
    }

    @Test void missingMarketJournalCannotInventItemOrRefundAnUnverifiedPayment() throws Exception {
        UUID id = active(100, null);
        bid(id, first, 100);
        DurableMarketService empty = new DurableMarketService(new MarketStore(directory.resolve("missing-market.yml").toFile()), economy, clock);
        auctions = new DurableAuctionService(store, empty, economy, clock, 5);
        assertEquals(REVIEW, bid(id, second, 105).outcome());
        assertEquals(Auction.Status.REVIEW, value(id).status());
        assertEquals(first, value(id).highBidder());
        assertEquals(100, value(id).highBid());
        assertEquals(9_900, economy.marks(first));
        assertEquals(0, economy.debits(second));
        assertEquals(0, economy.credits(first));
    }

    @Test void abandonedAndUncertainDepositsNeverOpenForBids() throws Exception {
        UUID abandoned = prepare(100, null);
        await(market.abandonDeposit(abandoned, seller));
        assertEquals(DurableAuctionService.Outcome.CANCELLED, await(auctions.activate(abandoned, seller)).outcome());
        UUID uncertain = prepare(100, null);
        restart();
        await(market.recover());
        assertEquals(PENDING, await(auctions.recover()).getFirst().outcome());
        assertEquals(WAITING_FOR_ITEM, value(uncertain).status());
        assertEquals(UNAVAILABLE, bid(uncertain, first, 100).outcome());
    }

    @Test void invalidDurationsPricesAndMissingIdsDoNotCreateAuctions() {
        UUID id = await(market.prepareAuctionListing(seller, stall, item(), 100, 0)).entry().orElseThrow().listing().id();
        assertEquals(INVALID, await(auctions.prepare(id, seller, 100, null, 0)).outcome());
        assertEquals(INVALID, await(auctions.prepare(id, seller, 100, null, 73)).outcome());
        assertEquals(INVALID, await(auctions.prepare(id, seller, 100, 99L, 24)).outcome());
        assertEquals(INVALID, await(auctions.prepare(null, seller, 100, null, 24)).outcome());
        assertEquals(NOT_FOUND, await(auctions.bid(UUID.randomUUID(), first, 100, 0)).outcome());
        assertTrue(auctions.all().isEmpty());
    }

    @Test void maximumPriceAndRoundingUseSafeIntegerArithmetic() {
        UUID id = active(1, null);
        bid(id, first, 1);
        assertEquals(2, DurableAuctionService.minimumBid(value(id)));
        assertEquals(INVALID, bid(id, second, Auction.MAX_PRICE + 1).outcome());
        economy.fund(second, Auction.MAX_PRICE);
        assertEquals(BID_ACCEPTED, bid(id, second, Auction.MAX_PRICE).outcome());
        assertEquals(1_050_000_000_000L, DurableAuctionService.minimumBid(value(id)));
        assertEquals(0, economy.marks(second));
    }

    @Test void auctionTaxIsCapturedAndCannotChangeDuringADeployment() throws Exception {
        UUID id = active(100, null);
        bid(id, first, 100);
        auctions = new DurableAuctionService(store, market, economy, clock, 99);
        clock.advance(Duration.ofHours(25));
        assertEquals(COMPLETE, await(auctions.tick()).getFirst().outcome());
        assertEquals(10_095, economy.marks(seller));
        assertEquals(5, value(id).taxPercent());
    }

    @Test void pendingItemRecoveryIsBoundedAndFairAcrossBatches() {
        for (int index = 0; index < 36; index++) prepare(100, null);
        var firstBatch = await(auctions.tick());
        var secondBatch = await(auctions.tick());
        assertEquals(32, firstBatch.size());
        assertEquals(32, secondBatch.size());
        var visited = new java.util.HashSet<UUID>();
        firstBatch.forEach(result -> visited.add(result.auction().orElseThrow().id()));
        secondBatch.forEach(result -> visited.add(result.auction().orElseThrow().id()));
        assertEquals(36, visited.size());
        assertTrue(auctions.all().stream().allMatch(value -> value.status() == WAITING_FOR_ITEM));
    }

    @Test void forAccountIncludesTheCurrentPendingRefundOwnerButNotUnrelatedPlayers() {
        UUID id = active(100, null);
        bid(id, first, 100);
        economy.loseNextCredit(first);
        bid(id, second, 105);
        assertEquals(1, auctions.forAccount(seller).size());
        assertEquals(1, auctions.forAccount(first).size());
        assertEquals(1, auctions.forAccount(second).size());
        assertTrue(auctions.forAccount(stranger).isEmpty());
    }

    private UUID prepare(long start, Long buyNow) {
        UUID id = await(market.prepareAuctionListing(seller, stall, item(), start, 0)).entry().orElseThrow().listing().id();
        assertEquals(PREPARED, await(auctions.prepare(id, seller, start, buyNow, 24)).outcome());
        return id;
    }
    private UUID active(long start, Long buyNow) {
        UUID id = prepare(start, buyNow);
        await(market.confirmDeposit(id, seller));
        assertEquals(DurableAuctionService.Outcome.ACTIVE, await(auctions.activate(id, seller)).outcome());
        return id;
    }
    private DurableAuctionService.Result bid(UUID id, AccountId account, long amount) { return await(auctions.bid(id, account, amount, value(id).revision())); }
    private Auction value(UUID id) { return auctions.auction(id).orElseThrow(); }
    private void restart() throws Exception {
        economy = economy.restarted();
        market = new DurableMarketService(new MarketStore(directory.resolve("market.yml").toFile()), economy, clock);
        store = new FailingStore(directory.resolve("auctions.yml"));
        auctions = new DurableAuctionService(store, market, economy, clock, 5);
    }
    private static <T> T await(CompletionStage<T> value) { return value.toCompletableFuture().join(); }
    private static String item() { return Base64.getEncoder().encodeToString("auction-item".getBytes(StandardCharsets.UTF_8)); }
    private static final class FailingStore extends AuctionStore {
        boolean failNext;
        FailingStore(Path path) { super(path.toFile()); }
        @Override public void save(Snapshot snapshot) throws IOException {
            if (failNext) { failNext = false; throw new IOException("Injected journal failure"); }
            super.save(snapshot);
        }
    }
    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-10-12T12:00:00Z");
        void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
