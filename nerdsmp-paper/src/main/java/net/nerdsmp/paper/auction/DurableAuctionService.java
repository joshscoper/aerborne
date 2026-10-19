package net.nerdsmp.paper.auction;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.MarketService;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static net.nerdsmp.paper.auction.Auction.Status.*;

/**
 * Timed auctions over the market's durable item escrow. Each wallet intent is saved before it is sent
 * and replayed with the same key after any lost acknowledgement. No player inventory is touched here.
 */
public final class DurableAuctionService {
    public static final int TICK_BATCH = 32;
    private final AuctionStore store;
    private final MarketService<String> market;
    private final EconomyService economy;
    private final Clock clock;
    private final int taxPercent;
    private AuctionStore.Snapshot state;
    private final Map<UUID, CompletableFuture<Result>> inFlight = new HashMap<>();
    private UUID tickCursor;

    public DurableAuctionService(File file, MarketService<String> market, EconomyService economy, int taxPercent) throws IOException {
        this(new AuctionStore(file), market, economy, Clock.systemUTC(), taxPercent);
    }

    public DurableAuctionService(AuctionStore store, MarketService<String> market, EconomyService economy,
                                 Clock clock, int taxPercent) throws IOException {
        this.store = Objects.requireNonNull(store);
        this.market = Objects.requireNonNull(market);
        this.economy = Objects.requireNonNull(economy);
        this.clock = Objects.requireNonNull(clock);
        if (taxPercent < 0 || taxPercent > 100) throw new IllegalArgumentException("Invalid auction tax");
        this.taxPercent = taxPercent;
        state = store.load();
    }

    public synchronized Optional<Auction> auction(UUID id) { return id == null ? Optional.empty() : Optional.ofNullable(state.auctions().get(id)); }
    public synchronized List<Auction> all() {
        return state.auctions().values().stream().sorted(Comparator.comparing(Auction::endsAt).thenComparing(value -> value.id().toString())).toList();
    }
    public synchronized List<Auction> forAccount(AccountId account) {
        if (account == null) return List.of();
        return all().stream().filter(value -> value.seller().equals(account) || account.equals(value.highBidder())
            || value.pending() != null && (account.equals(value.pending().bidder()) || account.equals(value.pending().previousBidder()))).toList();
    }
    public synchronized List<AuctionStore.Audit> audit() { return state.audit(); }

    /** Persist this plan before the caller removes and saves the seller's item. */
    public synchronized CompletionStage<Result> prepare(UUID listingId, AccountId seller, long startingBid,
                                                        Long buyNowPrice, Instant endsAt) {
        return prepareAt(listingId, seller, startingBid, buyNowPrice, clock.instant(), endsAt);
    }

    public synchronized CompletionStage<Result> prepare(UUID listingId, AccountId seller, long startingBid,
                                                        Long buyNowPrice, int durationHours) {
        if (durationHours < 1 || durationHours > 72) return done(result(Outcome.INVALID, listingId, "Auctions last between one and 72 hours."));
        Auction existing = listingId == null ? null : state.auctions().get(listingId);
        if (existing != null && Duration.between(existing.createdAt(), existing.endsAt()).equals(Duration.ofHours(durationHours))) {
            return prepareAt(listingId, seller, startingBid, buyNowPrice, existing.createdAt(), existing.endsAt());
        }
        Instant now = clock.instant();
        return prepareAt(listingId, seller, startingBid, buyNowPrice, now, now.plus(Duration.ofHours(durationHours)));
    }

    private CompletionStage<Result> prepareAt(UUID listingId, AccountId seller, long startingBid,
                                             Long buyNowPrice, Instant createdAt, Instant endsAt) {
        if (listingId == null || seller == null || endsAt == null) return done(result(Outcome.INVALID, listingId, "An auction needs a seller, item and end time."));
        Auction existing = state.auctions().get(listingId);
        if (existing != null) {
            if (!existing.seller().equals(seller)) return done(result(Outcome.DENIED, listingId, "This auction belongs to another player."));
            if (existing.startingBid() != startingBid || !Objects.equals(existing.buyNowPrice(), buyNowPrice) || !existing.endsAt().equals(endsAt)) {
                return done(result(Outcome.INVALID, listingId, "This item already has different auction terms."));
            }
            return done(result(existing.status() == WAITING_FOR_ITEM ? Outcome.PREPARED : Outcome.UNAVAILABLE, listingId, "This auction plan is already saved."));
        }
        MarketService.Entry<String> entry = market.entry(listingId).orElse(null);
        if (entry == null || !market.isAuction(listingId) || !entry.listing().seller().equals(seller)
            || entry.listing().price() != startingBid || entry.listing().status() != MarketService.Status.DEPOSIT_PENDING) {
            return done(result(Outcome.INVALID, listingId, "Prepare the auction's item escrow first."));
        }
        Auction auction;
        try {
            auction = new Auction(listingId, seller, startingBid, buyNowPrice, createdAt, endsAt, taxPercent,
                WAITING_FOR_ITEM, 0, null, 0, null, null, "");
        } catch (IllegalArgumentException exception) { return done(result(Outcome.INVALID, listingId, exception.getMessage())); }
        if (state.auctions().size() >= AuctionStore.MAX_AUCTIONS) return done(result(Outcome.UNAVAILABLE, listingId, "The seasonal auction journal is full."));
        if (!publish(auction, seller.toString(), "PREPARED", "Item deposit has not been confirmed.")) return done(saveFailed(listingId));
        return done(result(Outcome.PREPARED, listingId, "Auction prepared. Save the item deposit before activating it."));
    }

    public synchronized CompletionStage<Result> activate(UUID id, AccountId seller) {
        if (id == null) return done(notFound());
        Auction auction = state.auctions().get(id);
        if (auction == null) return done(notFound());
        if (!auction.seller().equals(seller)) return done(result(Outcome.DENIED, id, "This auction belongs to another player."));
        if (auction.status() == ACTIVE) return done(result(Outcome.ACTIVE, id, "Your auction is active."));
        if (auction.status() != WAITING_FOR_ITEM) return done(unavailable(id));
        return done(reconcileWaiting(auction));
    }

    public CompletionStage<Result> bid(UUID id, AccountId bidder, long amount, long expectedRevision) {
        return offer(id, bidder, amount, expectedRevision, false);
    }

    public CompletionStage<Result> buyNow(UUID id, AccountId buyer, long expectedRevision) {
        Long price;
        synchronized (this) {
            if (id == null) return done(notFound());
            Auction auction = state.auctions().get(id);
            if (auction == null) return done(notFound());
            price = auction.buyNowPrice();
            if (price == null) return done(result(Outcome.UNAVAILABLE, id, "This auction has no Buy Now price."));
        }
        return offer(id, buyer, price, expectedRevision, true);
    }

    private CompletionStage<Result> offer(UUID id, AccountId bidder, long amount, long expectedRevision, boolean requestedBuyNow) {
        synchronized (this) {
            if (id == null) return done(notFound());
            Auction auction = state.auctions().get(id);
            if (auction == null) return done(notFound());
            if (bidder == null || auction.seller().equals(bidder)) return done(result(Outcome.DENIED, id, "You cannot bid on your own auction."));
            if (auction.revision() != expectedRevision) return done(result(Outcome.STALE, id, "The auction changed. Check the latest bid before trying again."));
            if (inFlight.containsKey(id)) return done(pending(id, "The previous auction operation is still finishing. Refresh before bidding again."));
            if (auction.status() != ACTIVE) return done(unavailable(id));
            if (!clock.instant().isBefore(auction.endsAt())) return done(result(Outcome.UNAVAILABLE, id, "This auction has ended."));
            Result escrowProblem = checkActiveEscrow(auction);
            if (escrowProblem != null) return done(escrowProblem);
            boolean buyNow = auction.buyNowPrice() != null && amount == auction.buyNowPrice();
            if (amount <= 0 || amount > Auction.MAX_PRICE || amount < auction.startingBid()
                || amount <= auction.highBid() || !buyNow && amount < minimumBid(auction)
                || auction.buyNowPrice() != null && amount > auction.buyNowPrice() || requestedBuyNow && !buyNow) {
                return done(result(Outcome.INVALID, id, "Bid at least " + minimumBid(auction) + " Marks, or use Buy Now."));
            }
            Auction.PendingBid pending = new Auction.PendingBid(UUID.randomUUID(), bidder, amount,
                auction.highBidder(), auction.highBid(), buyNow, false, false);
            Auction locked = replace(auction, BID_PENDING, auction.highBidder(), auction.highBid(), pending, null, "");
            if (!publish(locked, bidder.toString(), "BID_INTENT", "Bid " + amount + (buyNow ? "; Buy Now." : "."))) return done(saveFailed(id));
        }
        return resume(id);
    }

    public CompletionStage<Result> cancel(UUID id, AccountId seller, long expectedRevision) {
        synchronized (this) {
            if (id == null) return done(notFound());
            Auction auction = state.auctions().get(id);
            if (auction == null) return done(notFound());
            if (seller == null || !auction.seller().equals(seller)) return done(result(Outcome.DENIED, id, "This auction belongs to another player."));
            if (auction.revision() != expectedRevision) return done(result(Outcome.STALE, id, "The auction changed. Refresh before cancelling it."));
            if (inFlight.containsKey(id)) return done(pending(id, "The previous auction operation is still finishing. Refresh before cancelling."));
            if (auction.status() != ACTIVE || auction.highBidder() != null) return done(result(Outcome.UNAVAILABLE, id, "Auctions with bids cannot be cancelled."));
            Result escrowProblem = checkActiveEscrow(auction);
            if (escrowProblem != null) return done(escrowProblem);
            if (!publish(replace(auction, CANCEL_PENDING, null, 0, null, null, ""), seller.toString(), "CANCEL_INTENT", "Seller cancelled an unbid auction.")) {
                return done(saveFailed(id));
            }
        }
        return resume(id);
    }

    public CompletionStage<Result> retry(UUID id, AccountId actor) {
        synchronized (this) {
            if (id == null) return done(notFound());
            Auction auction = state.auctions().get(id);
            if (auction == null) return done(notFound());
            if (actor == null || !(actor.equals(auction.seller()) || actor.equals(auction.highBidder())
                || auction.pending() != null && (actor.equals(auction.pending().bidder()) || actor.equals(auction.pending().previousBidder())))) {
                return done(result(Outcome.DENIED, id, "This auction belongs to another player."));
            }
        }
        return resume(id);
    }

    /** One bounded recovery batch; periodic calls eventually visit every pending or ended auction. */
    public CompletionStage<List<Result>> recover() { return tick(); }

    public CompletionStage<List<Result>> tick() {
        List<UUID> ids;
        synchronized (this) {
            Instant now = clock.instant();
            List<UUID> eligible = state.auctions().values().stream().filter(value -> !inFlight.containsKey(value.id())).filter(value -> value.status() == WAITING_FOR_ITEM
                || value.status() == BID_PENDING || value.status() == SETTLEMENT_PENDING || value.status() == CANCEL_PENDING
                || value.status() == ACTIVE && !now.isBefore(value.endsAt())).map(Auction::id).sorted().toList();
            if (eligible.isEmpty()) return CompletableFuture.completedFuture(List.of());
            int start = 0;
            if (tickCursor != null) {
                while (start < eligible.size() && eligible.get(start).compareTo(tickCursor) <= 0) start++;
                if (start == eligible.size()) start = 0;
            }
            List<UUID> batch = new ArrayList<>();
            for (int offset = 0; offset < Math.min(TICK_BATCH, eligible.size()); offset++) batch.add(eligible.get((start + offset) % eligible.size()));
            ids = List.copyOf(batch);
            tickCursor = ids.getLast();
        }
        List<CompletableFuture<Result>> futures = ids.stream().map(id -> resume(id).toCompletableFuture()).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    public static long minimumBid(Auction auction) {
        return auction.highBidder() == null ? auction.startingBid() : auction.highBid() + Math.max(1, (auction.highBid() + 19) / 20);
    }

    private CompletionStage<Result> resume(UUID id) {
        CompletableFuture<Result> operation;
        synchronized (this) {
            CompletableFuture<Result> existing = inFlight.get(id);
            if (existing != null) return existing;
            operation = new CompletableFuture<>();
            inFlight.put(id, operation);
        }
        CompletionStage<Result> work;
        try { work = advance(id); }
        catch (RuntimeException exception) { work = done(pending(id, "The transaction is saved and will retry.")); }
        work.whenComplete((result, error) -> {
            Result completed = result;
            synchronized (this) {
                if (error != null) completed = pending(id, "The transaction is saved and will retry.");
                inFlight.remove(id, operation);
            }
            operation.complete(completed);
        });
        return operation;
    }

    private CompletionStage<Result> advance(UUID id) {
        Auction auction;
        synchronized (this) {
            auction = state.auctions().get(id);
            if (auction == null) return done(notFound());
            if (auction.status() == WAITING_FOR_ITEM) {
                Result result = reconcileWaiting(auction);
                auction = state.auctions().get(id);
                if (auction.status() != ACTIVE || clock.instant().isBefore(auction.endsAt())) return done(result);
            }
            if (auction.status() == ACTIVE) {
                if (clock.instant().isBefore(auction.endsAt())) return done(result(Outcome.ACTIVE, id, "The auction is still open."));
                Result escrowProblem = checkActiveEscrow(auction);
                if (escrowProblem != null) return done(escrowProblem);
                Auction next = replace(auction, auction.highBidder() == null ? CANCEL_PENDING : SETTLEMENT_PENDING,
                    auction.highBidder(), auction.highBid(), null, auction.highBidder() == null ? null : UUID.randomUUID(), "");
                if (!publish(next, "system", "EXPIRED", auction.highBidder() == null ? "No bids; returning the item." : "Settling the funded winning bid.")) return done(saveFailed(id));
                auction = next;
            }
        }
        return switch (auction.status()) {
            case BID_PENDING -> advanceBid(auction);
            case SETTLEMENT_PENDING -> advanceSettlement(auction);
            case CANCEL_PENDING -> advanceCancel(auction);
            case COMPLETE -> done(result(Outcome.COMPLETE, id, "The auction is complete. The winner can collect the item from the market inbox."));
            case CANCELLED -> done(result(Outcome.CANCELLED, id, "The auction ended without a sale."));
            case REVIEW -> done(result(Outcome.REVIEW, id, "This auction needs staff review."));
            default -> done(unavailable(id));
        };
    }

    private CompletionStage<Result> advanceBid(Auction auction) {
        synchronized (this) {
            Result escrowProblem = checkActiveEscrow(auction);
            if (escrowProblem != null) return done(escrowProblem);
        }
        Auction.PendingBid bid = auction.pending();
        if (!bid.debitConfirmed()) {
            return economy.debit(bid.bidder(), bid.debitAmount(), reason("auction.bid", auction.id()), paymentKey(auction, bid, "debit"))
                .thenCompose(payment -> {
                    synchronized (this) {
                        Auction current = state.auctions().get(auction.id());
                        if (!samePending(current, bid)) return done(pending(auction.id(), "The bid is being recovered."));
                        if (payment.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS
                            || payment.status() == EconomyService.TransactionResult.Status.INVALID_AMOUNT) {
                            if (!publish(replace(current, ACTIVE, current.highBidder(), current.highBid(), null, null, ""), bid.bidder().toString(),
                                "BID_DECLINED", "The previous high bid remains unchanged.")) return done(saveFailed(auction.id()));
                            return done(result(Outcome.INSUFFICIENT_FUNDS, auction.id(), "You do not have enough Marks for this bid."));
                        }
                        if (!paid(payment)) return done(pending(auction.id(), "The bid payment is still pending."));
                        Auction.PendingBid funded = new Auction.PendingBid(bid.operationId(), bid.bidder(), bid.amount(), bid.previousBidder(),
                            bid.previousAmount(), bid.buyNow(), true, false);
                        Auction next = replace(current, BID_PENDING, current.highBidder(), current.highBid(), funded, null, "");
                        if (!publish(next, bid.bidder().toString(), "BID_FUNDED", "New bid escrow confirmed.")) return done(saveFailed(auction.id()));
                        return advanceBid(next);
                    }
                });
        }
        if (!bid.refundConfirmed()) {
            CompletionStage<EconomyService.TransactionResult> refund = bid.refundAmount() == 0
                ? CompletableFuture.completedFuture(EconomyService.TransactionResult.applied(0))
                : economy.credit(bid.previousBidder(), bid.refundAmount(), reason("auction.outbid_refund", auction.id()), paymentKey(auction, bid, "refund"));
            return refund.thenCompose(payment -> {
                synchronized (this) {
                    Auction current = state.auctions().get(auction.id());
                    if (!samePending(current, bid)) return done(pending(auction.id(), "The bid is being recovered."));
                    if (!paid(payment)) return done(pending(auction.id(), "The previous bidder's refund is still pending."));
                    Auction.PendingBid refunded = new Auction.PendingBid(bid.operationId(), bid.bidder(), bid.amount(), bid.previousBidder(),
                        bid.previousAmount(), bid.buyNow(), true, true);
                    Auction next = replace(current, BID_PENDING, current.highBidder(), current.highBid(), refunded, null, "");
                    if (!publish(next, bid.bidder().toString(), "REFUND_CONFIRMED", "Previous bid refund confirmed.")) return done(saveFailed(auction.id()));
                    return advanceBid(next);
                }
            });
        }
        synchronized (this) {
            Auction current = state.auctions().get(auction.id());
            if (!samePending(current, bid)) return done(pending(auction.id(), "The bid is being recovered."));
            Auction next = replace(current, bid.buyNow() ? SETTLEMENT_PENDING : ACTIVE, bid.bidder(), bid.amount(), null,
                bid.buyNow() ? bid.operationId() : null, "");
            if (!publish(next, bid.bidder().toString(), "BID_ACCEPTED", "High bid " + bid.amount() + ".")) return done(saveFailed(auction.id()));
            if (bid.buyNow()) return advanceSettlement(next);
            return done(result(Outcome.BID_ACCEPTED, auction.id(), "Your bid is now the highest. Marks are held until you win or are outbid."));
        }
    }

    private CompletionStage<Result> advanceSettlement(Auction auction) {
        return market.settleAuction(auction.id(), auction.highBidder(), auction.highBid(), auction.taxPercent(), auction.settlementId())
            .thenApply(settlement -> {
                synchronized (this) {
                    Auction current = state.auctions().get(auction.id());
                    if (!sameSettlement(current, auction)) return pending(auction.id(), "The sale is being recovered.");
                    MarketService.Entry<String> entry = market.entry(auction.id()).orElse(null);
                    if (settledEscrow(auction, entry)) {
                        if (!publish(replace(current, COMPLETE, current.highBidder(), current.highBid(), null, current.settlementId(), ""),
                            "system", "SETTLED", "Winner item delivery is available; seller payout confirmed.")) return saveFailed(auction.id());
                        return result(Outcome.COMPLETE, auction.id(), "Auction complete. The winner can collect the item from the market inbox.");
                    }
                    if (settlement.outcome() == MarketService.Outcome.INVALID || settlement.outcome() == MarketService.Outcome.DENIED
                        || settlement.outcome() == MarketService.Outcome.NOT_FOUND) return review(current, "The market escrow does not match the funded auction settlement.");
                    return settlement.outcome() == MarketService.Outcome.SAVE_FAILED ? saveFailed(auction.id())
                        : pending(auction.id(), "Your sale is saved; settlement will retry.");
                }
            });
    }

    private CompletionStage<Result> advanceCancel(Auction auction) {
        return market.cancelAuction(auction.id(), auction.seller()).thenApply(cancellation -> {
            synchronized (this) {
                Auction current = state.auctions().get(auction.id());
                if (current == null || current.status() != CANCEL_PENDING) return pending(auction.id(), "The item return is being recovered.");
                MarketService.Entry<String> entry = market.entry(auction.id()).orElse(null);
                boolean returned = entry != null && market.isAuction(auction.id()) && entry.listing().seller().equals(auction.seller())
                    && auction.seller().equals(entry.recipient()) && entry.checkoutId() == null
                    && (entry.listing().status() == MarketService.Status.READY || entry.listing().status() == MarketService.Status.DELIVERING
                        || entry.listing().status() == MarketService.Status.CANCELLED || entry.reviewKind() == MarketService.ReviewKind.DELIVERY);
                if (returned) {
                    if (!publish(replace(current, CANCELLED, null, 0, null, null, ""), "system", "RETURNED", "Seller item return is available in the market inbox.")) return saveFailed(auction.id());
                    return result(Outcome.CANCELLED, auction.id(), "Your item is ready to collect from the market inbox.");
                }
                if (cancellation.outcome() == MarketService.Outcome.INVALID || cancellation.outcome() == MarketService.Outcome.DENIED
                    || cancellation.outcome() == MarketService.Outcome.NOT_FOUND) return review(current, "The market escrow does not match the auction's item return.");
                return cancellation.outcome() == MarketService.Outcome.SAVE_FAILED ? saveFailed(auction.id())
                    : pending(auction.id(), "Your item return is saved and will retry.");
            }
        });
    }

    private synchronized Result reconcileWaiting(Auction auction) {
        MarketService.Entry<String> entry = market.entry(auction.id()).orElse(null);
        if (entry == null || !market.isAuction(auction.id()) || !entry.listing().seller().equals(auction.seller())
            || entry.listing().price() != auction.startingBid()) return review(auction, "The prepared auction does not match its item escrow.");
        if (entry.listing().status() == MarketService.Status.AUCTION) {
            if (!publish(replace(auction, ACTIVE, null, 0, null, null, ""), "system", "ACTIVATED", "Item deposit and listing fee confirmed.")) return saveFailed(auction.id());
            return result(Outcome.ACTIVE, auction.id(), "Your auction is now open for bids.");
        }
        if (entry.listing().status() == MarketService.Status.CANCELLED || auction.seller().equals(entry.recipient())
            && (entry.listing().status() == MarketService.Status.READY || entry.listing().status() == MarketService.Status.DELIVERING)) {
            if (!publish(replace(auction, CANCELLED, null, 0, null, null, ""), "system", "DEPOSIT_CANCELLED", "The item deposit was cancelled or returned.")) return saveFailed(auction.id());
            return result(Outcome.CANCELLED, auction.id(), "The auction's item deposit was cancelled.");
        }
        if (entry.listing().status() == MarketService.Status.DEPOSIT_PENDING || entry.listing().status() == MarketService.Status.FEE_PENDING
            || entry.listing().status() == MarketService.Status.REVIEW && entry.reviewKind() == MarketService.ReviewKind.DEPOSIT) {
            return pending(auction.id(), "The item deposit or listing fee still needs confirmation.");
        }
        return review(auction, "The prepared auction has an unexpected item escrow state.");
    }

    private synchronized Result checkActiveEscrow(Auction auction) {
        MarketService.Entry<String> entry = market.entry(auction.id()).orElse(null);
        if (entry != null && market.isAuction(auction.id()) && entry.listing().status() == MarketService.Status.AUCTION
            && entry.listing().seller().equals(auction.seller()) && entry.listing().price() == auction.startingBid()) return null;
        return review(auction, "This auction's active item escrow is missing or inconsistent.");
    }

    private boolean settledEscrow(Auction auction, MarketService.Entry<String> entry) {
        return entry != null && market.isAuction(auction.id()) && entry.listing().seller().equals(auction.seller())
            && entry.listing().price() == auction.highBid() && auction.highBidder().equals(entry.recipient())
            && auction.highBidder().equals(entry.listing().reservedBy()) && auction.settlementId().equals(entry.checkoutId())
            && auction.taxPercent() == entry.taxPercent() && (entry.listing().status() == MarketService.Status.READY
                || entry.listing().status() == MarketService.Status.DELIVERING || entry.listing().status() == MarketService.Status.SOLD
                || entry.listing().status() == MarketService.Status.REVIEW && entry.reviewKind() == MarketService.ReviewKind.DELIVERY);
    }

    private synchronized Result review(Auction auction, String reason) {
        if (!publish(replace(auction, REVIEW, auction.highBidder(), auction.highBid(), auction.pending(), auction.settlementId(), reason),
            "system", "REVIEW", reason)) return saveFailed(auction.id());
        return result(Outcome.REVIEW, auction.id(), "This auction needs staff review. Its saved payment state has been preserved.");
    }

    private static boolean samePending(Auction current, Auction.PendingBid bid) {
        return current != null && current.status() == BID_PENDING && current.pending().operationId().equals(bid.operationId());
    }
    private static boolean sameSettlement(Auction current, Auction expected) {
        return current != null && current.status() == SETTLEMENT_PENDING && current.settlementId().equals(expected.settlementId());
    }
    private static boolean paid(EconomyService.TransactionResult result) {
        return result.status() == EconomyService.TransactionResult.Status.APPLIED || result.status() == EconomyService.TransactionResult.Status.DUPLICATE;
    }
    private static String paymentKey(Auction auction, Auction.PendingBid bid, String operation) {
        return "auction:" + auction.id() + ":" + bid.operationId() + ":" + operation;
    }
    private static EconomyService.TransactionReason reason(String code, UUID id) {
        return new EconomyService.TransactionReason(code, "Auction " + id);
    }
    private static Auction replace(Auction value, Auction.Status status, AccountId bidder, long bid,
                                   Auction.PendingBid pending, UUID settlementId, String reviewReason) {
        return new Auction(value.id(), value.seller(), value.startingBid(), value.buyNowPrice(), value.createdAt(), value.endsAt(), value.taxPercent(),
            status, Math.addExact(value.revision(), 1), bidder, bid, pending, settlementId, reviewReason);
    }

    private synchronized boolean publish(Auction auction, String actor, String action, String detail) {
        Map<UUID, Auction> auctions = new HashMap<>(state.auctions());
        auctions.put(auction.id(), auction);
        List<AuctionStore.Audit> audit = new ArrayList<>(state.audit());
        audit.add(new AuctionStore.Audit(UUID.randomUUID(), clock.instant(), auction.id(), actor, action, detail));
        if (audit.size() > AuctionStore.MAX_AUDIT) audit = new ArrayList<>(audit.subList(audit.size() - AuctionStore.MAX_AUDIT, audit.size()));
        AuctionStore.Snapshot next = new AuctionStore.Snapshot(auctions, audit);
        try { store.save(next); }
        catch (IOException | RuntimeException exception) { return false; }
        state = next;
        return true;
    }

    private synchronized Result result(Outcome outcome, UUID id, String message) {
        return new Result(outcome, id == null ? Optional.empty() : Optional.ofNullable(state.auctions().get(id)), message);
    }
    private Result saveFailed(UUID id) { return result(Outcome.SAVE_FAILED, id, "The auction could not be saved. Its last confirmed state is unchanged; retry safely."); }
    private Result pending(UUID id, String message) { return result(Outcome.PENDING, id, message); }
    private Result unavailable(UUID id) { return result(Outcome.UNAVAILABLE, id, "This auction is not accepting that action right now."); }
    private Result notFound() { return new Result(Outcome.NOT_FOUND, Optional.empty(), "That auction was not found."); }
    private static CompletionStage<Result> done(Result result) { return CompletableFuture.completedFuture(result); }

    public enum Outcome { PREPARED, ACTIVE, BID_ACCEPTED, COMPLETE, CANCELLED, PENDING, REVIEW, NOT_FOUND,
        DENIED, UNAVAILABLE, INVALID, INSUFFICIENT_FUNDS, SAVE_FAILED, STALE }
    public record Result(Outcome outcome, Optional<Auction> auction, String message) {
        public Result { Objects.requireNonNull(outcome); Objects.requireNonNull(auction); Objects.requireNonNull(message); }
    }
}
