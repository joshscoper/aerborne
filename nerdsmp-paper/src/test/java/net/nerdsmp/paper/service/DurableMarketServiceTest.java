package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static net.nerdsmp.api.service.MarketService.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

final class DurableMarketServiceTest {
    @TempDir Path directory;
    private final AccountId seller = AccountId.random();
    private final AccountId buyer = AccountId.random();
    private final AccountId competitor = AccountId.random();
    private final MutableClock clock = new MutableClock();
    private MarketTestEconomy economy = fundedEconomy();
    private static final String ITEM = Base64.getEncoder().encodeToString("item-a".getBytes(StandardCharsets.UTF_8));
    private static final MarketService.StallLocation LOCATION =
        new MarketService.StallLocation(UUID.randomUUID(), 8, 65, -12);

    @Test
    void stallsItemsAndReservationsSurviveServiceRestart() throws Exception {
        var market = market();
        UUID id = active(market);
        assertEquals(RESERVED, await(market.reserve(id, buyer, clock.instant().plusSeconds(30))).outcome());
        var reopened = market();
        var entry = reopened.entry(id).orElseThrow();
        assertEquals(ITEM, entry.listing().item());
        assertEquals(MarketService.Status.RESERVED, entry.listing().status());
        assertEquals(buyer, entry.listing().reservedBy());
        assertEquals(LOCATION, reopened.stallFor(seller).orElseThrow().location());
        assertEquals(entry.listing().stallId(), reopened.stallFor(seller).orElseThrow().id());
        assertEquals(990, economy.marks(seller));
    }

    @Test
    void lostBuyerDebitAcknowledgementResumesWithoutDoubleCharge() throws Exception {
        var market = market();
        UUID id = reserved(market);
        economy.loseNextDebitResponse(buyer);
        assertNotEquals(READY, await(market.checkout(id, buyer, 10)).outcome());
        assertEquals(900, economy.marks(buyer));
        assertEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        economy = economy.restarted();
        var restarted = market();
        await(restarted.recover());
        assertReady(restarted, id, buyer);
        assertEquals(900, economy.marks(buyer));
        assertEquals(1080, economy.marks(seller));
        assertEquals(1, economy.appliedDebits(buyer));
        assertEquals(1, economy.appliedCredits(seller));
    }

    @Test
    void lostSellerPayoutAcknowledgementResumesWithoutDoubleCredit() throws Exception {
        var market = market();
        UUID id = reserved(market);
        economy.loseNextCreditResponse(seller);
        assertNotEquals(READY, await(market.checkout(id, buyer, 10)).outcome());
        assertEquals(1080, economy.marks(seller));
        economy = economy.restarted();
        var restarted = market();
        await(restarted.recover());
        assertReady(restarted, id, buyer);
        assertEquals(900, economy.marks(buyer));
        assertEquals(1080, economy.marks(seller));
        assertEquals(1, economy.appliedCredits(seller));
    }

    @Test
    void pendingCheckoutCannotExpireIntoASecondBuyersPurchase() throws Exception {
        var market = market();
        UUID id = reserved(market);
        economy.holdNextDebitResponse(buyer);
        var checkout = market.checkout(id, buyer, 10).toCompletableFuture();
        assertFalse(checkout.isDone());
        clock.advanceSeconds(60);
        await(market.activeListings());
        assertEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        assertNotEquals(RESERVED, await(market.reserve(id, competitor, clock.instant().plusSeconds(30))).outcome());
        assertNotEquals(READY, await(market.checkout(id, competitor, 10)).outcome());
        assertEquals(1000, economy.marks(competitor));
        economy.releaseDebitResponse();
        assertEquals(READY, checkout.join().outcome());
        assertReady(market, id, buyer);
    }

    @Test
    void insufficientFundsLeaveItemAvailableAndNoSellerPayout() throws Exception {
        var market = market();
        UUID id = reserved(market);
        economy.fund(buyer, 5);
        assertEquals(INSUFFICIENT_FUNDS, await(market.checkout(id, buyer, 10)).outcome());
        assertEquals(5, economy.marks(buyer));
        assertEquals(990, economy.marks(seller));
        assertNotEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        clock.advanceSeconds(60);
        assertEquals(RESERVED, await(market.reserve(id, competitor, clock.instant().plusSeconds(30))).outcome());
        assertEquals(READY, await(market.checkout(id, competitor, 10)).outcome());
        assertReady(market, id, competitor);
    }

    @Test
    void listingFeeRetriesReuseTheOriginalDebit() throws Exception {
        var market = market();
        UUID id = prepared(market);
        economy.loseNextDebitResponse(seller);
        assertNotEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        assertEquals(990, economy.marks(seller));
        var restarted = market();
        await(restarted.recover());
        assertEquals(MarketService.Status.ACTIVE, restarted.entry(id).orElseThrow().listing().status());
        assertEquals(990, economy.marks(seller));
        assertEquals(1, economy.appliedDebits(seller));
    }

    @Test
    void salesTaxRoundsDownBeforeSubtractingFromSellerProceeds() throws Exception {
        var market = market();
        var stall = await(market.registerStall(seller, LOCATION));
        UUID id = await(market.prepareListing(seller, stall.id(), ITEM, 101, 0))
            .entry().orElseThrow().listing().id();
        await(market.confirmDeposit(id, seller));
        await(market.reserve(id, buyer, clock.instant().plusSeconds(30)));
        assertEquals(READY, await(market.checkout(id, buyer, 5)).outcome());
        assertEquals(899, economy.marks(buyer));
        assertEquals(1096, economy.marks(seller));
    }

    @Test
    void maximumPriceAndFullTaxCannotOverflowOrCreateAZeroCreditRequest() throws Exception {
        var market = market();
        economy.fund(buyer, Long.MAX_VALUE);
        economy.fund(seller, 0);
        var stall = await(market.registerStall(seller, LOCATION));
        UUID id = await(market.prepareListing(seller, stall.id(), ITEM, Long.MAX_VALUE, 0))
            .entry().orElseThrow().listing().id();
        await(market.confirmDeposit(id, seller));
        await(market.reserve(id, buyer, clock.instant().plusSeconds(30)));
        assertEquals(READY, await(market.checkout(id, buyer, 100)).outcome());
        assertEquals(0, economy.marks(buyer));
        assertEquals(0, economy.marks(seller));
        assertEquals(0, economy.appliedCredits(seller));
    }

    @Test
    void readyItemsRemainDurableUntilTheirRecipientCollects() throws Exception {
        var market = market();
        UUID id = reserved(market);
        assertEquals(READY, await(market.checkout(id, buyer, 10)).outcome());
        economy = economy.restarted();
        var restarted = market();
        await(restarted.recover());
        assertReady(restarted, id, buyer);
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, competitor)).outcome());
        var delivery = await(restarted.beginDelivery(id, buyer));
        assertEquals(DELIVERING, delivery.outcome());
        UUID token = delivery.entry().orElseThrow().deliveryId();
        assertNotNull(token);
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, buyer)).outcome());
        assertNotEquals(SOLD, await(restarted.finishDelivery(id, buyer, UUID.randomUUID())).outcome());
        assertEquals(SOLD, await(restarted.finishDelivery(id, buyer, token)).outcome());
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, buyer)).outcome());
        assertEquals(MarketService.Status.SOLD, market().entry(id).orElseThrow().listing().status());
    }

    @Test
    void ambiguousDepositIsQuarantinedWithoutChargingOrReissuingItem() throws Exception {
        var market = market();
        UUID id = prepared(market);
        var restarted = market();
        await(restarted.recover());
        var review = restarted.entry(id).orElseThrow();
        assertEquals(MarketService.Status.REVIEW, review.listing().status());
        assertEquals(MarketService.ReviewKind.DEPOSIT, review.reviewKind());
        assertEquals(1000, economy.marks(seller));
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, seller)).outcome());
        assertNotEquals(ACTIVE, await(restarted.confirmDeposit(id, seller)).outcome());
    }

    @Test
    void liveDepositSaveFailureCanEnterStaffReviewWithoutRestart() throws Exception {
        var market = market();
        UUID id = prepared(market);
        assertNotEquals(REVIEW, await(market.quarantineDeposit(id, competitor, "Not the seller")).outcome());
        assertEquals(REVIEW, await(market.quarantineDeposit(id, seller, "Inventory save acknowledgement failed")).outcome());
        assertEquals(MarketService.ReviewKind.DEPOSIT, market.entry(id).orElseThrow().reviewKind());
        assertNotEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        assertEquals(CANCELLED, await(market.resolve(id, MarketService.Resolution.DEPOSIT_KEPT,
            "Moderator", "Confirmed original item remains in saved inventory")).outcome());
        assertEquals(1000, economy.marks(seller));
    }

    @Test
    void failedDepositQuarantineSaveStillBlocksUnsafeConfirmation() throws Exception {
        FailingStore store = new FailingStore(file());
        var market = new DurableMarketService(store, economy, clock);
        UUID id = prepared(market);
        byte[] before = Files.readAllBytes(file().toPath());
        store.failNext = true;
        assertEquals(SAVE_FAILED, await(market.quarantineDeposit(id, seller, "Inventory save failed")).outcome());
        assertArrayEquals(before, Files.readAllBytes(file().toPath()));
        assertNotEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        assertEquals(1000, economy.marks(seller));
        await(market.recover());
        assertEquals(MarketService.Status.REVIEW, market.entry(id).orElseThrow().listing().status());
    }

    @Test
    void recoveryDoesNotInterruptAHandOffThatStartedInThisProcess() throws Exception {
        var market = market();
        UUID id = prepared(market);
        await(market.recover());
        assertEquals(MarketService.Status.DEPOSIT_PENDING, market.entry(id).orElseThrow().listing().status());
        assertEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        await(market.reserve(id, buyer, clock.instant().plusSeconds(30)));
        await(market.checkout(id, buyer, 10));
        UUID token = await(market.beginDelivery(id, buyer)).entry().orElseThrow().deliveryId();
        await(market.recover());
        assertEquals(MarketService.Status.DELIVERING, market.entry(id).orElseThrow().listing().status());
        assertEquals(SOLD, await(market.finishDelivery(id, buyer, token)).outcome());
    }

    @Test
    void uncollectedItemsPreventTheSellerFromMovingTheirStall() throws Exception {
        var market = market();
        UUID id = reserved(market);
        await(market.checkout(id, buyer, 10));
        var elsewhere = new MarketService.StallLocation(LOCATION.worldId(), 200, 80, 200);
        assertThrows(Exception.class, () -> await(market.registerStall(seller, elsewhere)));
        assertEquals(LOCATION, market.stallFor(seller).orElseThrow().location());
        UUID token = await(market.beginDelivery(id, buyer)).entry().orElseThrow().deliveryId();
        await(market.finishDelivery(id, buyer, token));
        assertEquals(elsewhere, await(market.registerStall(seller, elsewhere)).location());
    }

    @Test
    void ambiguousDeliveryRequiresAuditedStaffResolutionBeforeRetry() throws Exception {
        var market = market();
        UUID id = reserved(market);
        await(market.checkout(id, buyer, 10));
        UUID oldToken = await(market.beginDelivery(id, buyer)).entry().orElseThrow().deliveryId();
        var restarted = market();
        await(restarted.recover());
        var review = restarted.entry(id).orElseThrow();
        assertEquals(MarketService.Status.REVIEW, review.listing().status());
        assertEquals(MarketService.ReviewKind.DELIVERY, review.reviewKind());
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, buyer)).outcome());
        assertEquals(READY, await(restarted.resolve(id, MarketService.Resolution.DELIVERY_MISSING,
            "Moderator", "Inventory rollback checked against backup")).outcome());
        assertTrue(restarted.audit().stream().anyMatch(audit -> id.equals(audit.listingId())
            && audit.actor().equals("Moderator") && audit.detail().contains("Inventory rollback checked")));
        UUID newToken = await(restarted.beginDelivery(id, buyer)).entry().orElseThrow().deliveryId();
        assertNotEquals(oldToken, newToken);
        assertNotEquals(SOLD, await(restarted.finishDelivery(id, buyer, oldToken)).outcome());
        assertEquals(SOLD, await(restarted.finishDelivery(id, buyer, newToken)).outcome());
        assertEquals(900, economy.marks(buyer));
        assertEquals(1080, economy.marks(seller));
    }

    @Test
    void restartedDeliveryCannotBeAcknowledgedBeforeRecoveryReviewsIt() throws Exception {
        var market = market();
        UUID id = reserved(market);
        await(market.checkout(id, buyer, 10));
        UUID token = await(market.beginDelivery(id, buyer)).entry().orElseThrow().deliveryId();
        var restarted = market();
        assertEquals(UNAVAILABLE, await(restarted.finishDelivery(id, buyer, token)).outcome());
        assertEquals(MarketService.Status.DELIVERING, restarted.entry(id).orElseThrow().listing().status());
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, buyer)).outcome());
        await(restarted.recover());
        assertEquals(MarketService.Status.REVIEW, restarted.entry(id).orElseThrow().listing().status());
        assertNotEquals(SOLD, await(restarted.finishDelivery(id, buyer, token)).outcome());
    }

    @Test
    void sellerReturnSurvivesRestartAndCompletesAsCancelledWithoutPayout() throws Exception {
        var market = market();
        UUID id = active(market);
        assertEquals(READY, await(market.cancel(id, seller)).outcome());
        economy = economy.restarted();
        var restarted = market();
        await(restarted.recover());
        assertReady(restarted, id, seller);
        UUID token = await(restarted.beginDelivery(id, seller)).entry().orElseThrow().deliveryId();
        assertEquals(CANCELLED, await(restarted.finishDelivery(id, seller, token)).outcome());
        assertEquals(MarketService.Status.CANCELLED, market().entry(id).orElseThrow().listing().status());
        assertEquals(990, economy.marks(seller));
        assertEquals(0, economy.appliedCredits(seller));
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, seller)).outcome());
    }

    @Test
    void interruptedSellerReturnCanOnlyResolveToCancelledAfterStaffVerification() throws Exception {
        var market = market();
        UUID id = active(market);
        await(market.cancel(id, seller));
        UUID token = await(market.beginDelivery(id, seller)).entry().orElseThrow().deliveryId();
        var restarted = market();
        assertEquals(UNAVAILABLE, await(restarted.finishDelivery(id, seller, token)).outcome());
        await(restarted.recover());
        assertEquals(CANCELLED, await(restarted.resolve(id, MarketService.Resolution.DELIVERY_RECEIVED,
            "Moderator", "Returned stack confirmed in seller inventory")).outcome());
        assertEquals(MarketService.Status.CANCELLED, market().entry(id).orElseThrow().listing().status());
        assertEquals(990, economy.marks(seller));
        assertEquals(0, economy.appliedCredits(seller));
    }

    @Test
    void staffCanConfirmAnAlreadyReceivedDeliveryWithoutIssuingAnother() throws Exception {
        var market = market();
        UUID id = reserved(market);
        await(market.checkout(id, buyer, 10));
        await(market.beginDelivery(id, buyer));
        var restarted = market();
        await(restarted.recover());
        assertEquals(SOLD, await(restarted.resolve(id, MarketService.Resolution.DELIVERY_RECEIVED,
            "Moderator", "Item found in recipient inventory")).outcome());
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, buyer)).outcome());
        assertEquals(MarketService.Status.SOLD, market().entry(id).orElseThrow().listing().status());
    }

    @Test
    void staffDepositKeptResolutionNeverCreatesAnExtraReturnItem() throws Exception {
        UUID id = prepared(market());
        var restarted = market();
        await(restarted.recover());
        assertEquals(CANCELLED, await(restarted.resolve(id, MarketService.Resolution.DEPOSIT_KEPT,
            "Moderator", "Original item is still in seller inventory")).outcome());
        assertNotEquals(DELIVERING, await(restarted.beginDelivery(id, seller)).outcome());
        assertEquals(1000, economy.marks(seller));
        var reopened = market();
        assertEquals(MarketService.Status.CANCELLED, reopened.entry(id).orElseThrow().listing().status());
        assertTrue(reopened.audit().stream().anyMatch(audit -> id.equals(audit.listingId())
            && audit.actor().equals("Moderator") && audit.detail().contains("Original item is still")));
    }

    @Test
    void staffDepositTakenResolutionRestoresEscrowAndItsSingleListingFee() throws Exception {
        UUID id = prepared(market());
        var restarted = market();
        await(restarted.recover());
        assertEquals(ACTIVE, await(restarted.resolve(id, MarketService.Resolution.DEPOSIT_TAKEN,
            "Moderator", "Inventory save confirms the item was removed")).outcome());
        assertEquals(990, economy.marks(seller));
        assertEquals(1, economy.appliedDebits(seller));
        assertEquals(READY, await(restarted.cancel(id, seller)).outcome());
        assertReady(restarted, id, seller);
        var delivery = await(restarted.beginDelivery(id, seller));
        assertEquals(DELIVERING, delivery.outcome());
        assertEquals(CANCELLED, await(restarted.finishDelivery(id, seller,
            delivery.entry().orElseThrow().deliveryId())).outcome());
        assertEquals(990, economy.marks(seller));
    }

    @Test
    void neitherOtherPlayersNorAnUnreviewedResolutionCanReleaseEscrow() throws Exception {
        var market = market();
        UUID id = prepared(market);
        assertNotEquals(ACTIVE, await(market.confirmDeposit(id, competitor)).outcome());
        assertNotEquals(CANCELLED, await(market.abandonDeposit(id, competitor)).outcome());
        assertNotEquals(READY, await(market.resolve(id, MarketService.Resolution.DELIVERY_MISSING,
            "Moderator", "Wrong state must be rejected")).outcome());
        assertEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        assertNotEquals(RESERVED, await(market.reserve(id, seller, clock.instant().plusSeconds(30))).outcome());
        assertNotEquals(READY, await(market.checkout(id, seller, 10)).outcome());
        assertNotEquals(READY, await(market.cancel(id, competitor)).outcome());
        assertEquals(MarketService.Status.ACTIVE, market.entry(id).orElseThrow().listing().status());
    }

    @Test
    void failedSaveDoesNotPublishOrReplaceThePreviousSnapshot() throws Exception {
        FailingStore store = new FailingStore(file());
        var market = new DurableMarketService(store, economy, clock);
        UUID id = active(market);
        byte[] before = Files.readAllBytes(file().toPath());
        store.failNext = true;
        assertEquals(SAVE_FAILED, await(market.reserve(id, buyer, clock.instant().plusSeconds(30))).outcome());
        assertEquals(MarketService.Status.ACTIVE, market.entry(id).orElseThrow().listing().status());
        assertArrayEquals(before, Files.readAllBytes(file().toPath()));
        assertEquals(MarketService.Status.ACTIVE, market().entry(id).orElseThrow().listing().status());
    }

    @Test
    void saveFailureAfterCommittedDebitCanRetryWithoutChargingAgain() throws Exception {
        FailingStore store = new FailingStore(file());
        var market = new DurableMarketService(store, economy, clock);
        UUID id = reserved(market);
        economy.holdNextDebitResponse(buyer);
        var checkout = market.checkout(id, buyer, 10).toCompletableFuture();
        store.failNext = true;
        economy.releaseDebitResponse();
        assertEquals(SAVE_FAILED, checkout.join().outcome());
        assertEquals(MarketService.Status.CHECKOUT, market.entry(id).orElseThrow().listing().status());
        assertEquals(900, economy.marks(buyer));
        assertEquals(READY, await(market.retry(id, buyer)).outcome());
        assertEquals(900, economy.marks(buyer));
        assertEquals(1080, economy.marks(seller));
        assertEquals(1, economy.appliedDebits(buyer));
    }

    @Test
    void malformedStoreFailsClosedWithoutReplacingTheSource() throws Exception {
        Files.writeString(file().toPath(), "entries: [broken");
        assertThrows(Exception.class, this::market);
        assertEquals("entries: [broken", Files.readString(file().toPath()));
    }

    @Test
    void duplicateJournalKeysCannotSilentlyDiscardEscrow() throws Exception {
        active(market());
        String duplicated = Files.readString(file().toPath()) + "\nentries: {}\n";
        Files.writeString(file().toPath(), duplicated);
        assertThrows(Exception.class, this::market);
        assertEquals(duplicated, Files.readString(file().toPath()));
    }

    private DurableMarketService market() throws Exception {
        return new DurableMarketService(new MarketStore(file()), economy, clock);
    }

    private File file() {
        return directory.resolve("market.yml").toFile();
    }

    private UUID prepared(DurableMarketService market) {
        var stall = await(market.registerStall(seller, LOCATION));
        var prepared = await(market.prepareListing(seller, stall.id(), ITEM, 100, 10));
        assertEquals(PREPARED, prepared.outcome());
        return prepared.entry().orElseThrow().listing().id();
    }

    private UUID active(DurableMarketService market) {
        UUID id = prepared(market);
        assertEquals(ACTIVE, await(market.confirmDeposit(id, seller)).outcome());
        return id;
    }

    private UUID reserved(DurableMarketService market) {
        UUID id = active(market);
        assertEquals(RESERVED, await(market.reserve(id, buyer, clock.instant().plusSeconds(30))).outcome());
        return id;
    }

    private void assertReady(DurableMarketService market, UUID id, AccountId recipient) {
        var entry = market.entry(id).orElseThrow();
        assertEquals(MarketService.Status.READY, entry.listing().status());
        assertEquals(recipient, entry.recipient());
        assertEquals(ITEM, entry.listing().item());
    }

    private MarketTestEconomy fundedEconomy() {
        var value = new MarketTestEconomy();
        value.fund(seller, 1000);
        value.fund(buyer, 1000);
        value.fund(competitor, 1000);
        return value;
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-13T12:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class FailingStore extends MarketStore {
        private boolean failNext;
        FailingStore(File file) { super(file); }
        @Override public void save(Snapshot snapshot) throws IOException {
            if (failNext) {
                failNext = false;
                throw new IOException("Simulated disk failure before replace");
            }
            super.save(snapshot);
        }
    }
}
