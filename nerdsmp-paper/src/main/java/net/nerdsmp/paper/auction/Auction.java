package net.nerdsmp.paper.auction;

import net.nerdsmp.api.model.AccountId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable auction journal entry. Items remain in the market's existing durable escrow. */
public record Auction(UUID id, AccountId seller, long startingBid, Long buyNowPrice,
                      Instant createdAt, Instant endsAt, int taxPercent, Status status, long revision,
                      AccountId highBidder, long highBid, PendingBid pending, UUID settlementId,
                      String reviewReason) {
    public static final long MAX_PRICE = 1_000_000_000_000L;

    public Auction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reviewReason, "reviewReason");
        require(startingBid > 0 && startingBid <= MAX_PRICE, "Invalid starting bid");
        require(buyNowPrice == null || buyNowPrice >= startingBid && buyNowPrice <= MAX_PRICE, "Invalid Buy Now price");
        Duration duration = Duration.between(createdAt, endsAt);
        require(duration.compareTo(Duration.ofHours(1)) >= 0 && duration.compareTo(Duration.ofHours(72)) <= 0,
            "Auctions last between one and 72 hours");
        require(taxPercent >= 0 && taxPercent <= 100 && revision >= 0, "Invalid auction tax or revision");
        require(highBidder == null ? highBid == 0 : !seller.equals(highBidder)
            && highBid >= startingBid && highBid <= MAX_PRICE, "Invalid high bid");
        require(buyNowPrice == null || highBid <= buyNowPrice, "High bid exceeds Buy Now price");
        require(reviewReason.length() <= 512 && (status == Status.REVIEW) == !reviewReason.isBlank(), "Invalid review reason");
        require(status == Status.BID_PENDING || status == Status.REVIEW || pending == null, "Pending bid outside pending state");
        if (status == Status.BID_PENDING) require(pending != null && settlementId == null, "Missing pending bid");
        if (pending != null) {
            require(!seller.equals(pending.bidder()) && Objects.equals(pending.previousBidder(), highBidder)
                && pending.previousAmount() == highBid, "Pending bid does not match the previous high bid");
            require(pending.amount() >= startingBid && pending.amount() > highBid && pending.amount() <= MAX_PRICE,
                "Invalid pending bid amount");
            require(buyNowPrice == null || pending.amount() <= buyNowPrice, "Pending bid exceeds Buy Now price");
            require(!pending.buyNow() || buyNowPrice != null && pending.amount() == buyNowPrice, "Invalid Buy Now operation");
        }
        if (status == Status.WAITING_FOR_ITEM || status == Status.CANCEL_PENDING || status == Status.CANCELLED) {
            require(highBidder == null && pending == null && settlementId == null, "Unbid auction has payment state");
        }
        if (status == Status.ACTIVE) {
            require(settlementId == null && pending == null && (buyNowPrice == null || highBid < buyNowPrice), "Invalid active auction");
        }
        if (status == Status.SETTLEMENT_PENDING || status == Status.COMPLETE) {
            require(highBidder != null && settlementId != null && pending == null, "Settlement has no funded winner");
        }
        require(settlementId == null || status == Status.SETTLEMENT_PENDING || status == Status.COMPLETE
            || status == Status.REVIEW, "Settlement outside settlement state");
    }

    public enum Status { WAITING_FOR_ITEM, ACTIVE, BID_PENDING, SETTLEMENT_PENDING, CANCEL_PENDING, COMPLETE, CANCELLED, REVIEW }

    /** Every external ledger operation can be replayed using this durable operation ID. */
    public record PendingBid(UUID operationId, AccountId bidder, long amount, AccountId previousBidder,
                             long previousAmount, boolean buyNow, boolean debitConfirmed, boolean refundConfirmed) {
        public PendingBid {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(bidder, "bidder");
            require(amount > 0 && amount <= MAX_PRICE, "Invalid pending amount");
            require(previousBidder == null ? previousAmount == 0 : previousAmount > 0 && previousAmount <= MAX_PRICE,
                "Invalid previous bid");
            require(!refundConfirmed || debitConfirmed, "Refund confirmed before the new bid was funded");
        }

        public long debitAmount() { return bidder.equals(previousBidder) ? amount - previousAmount : amount; }
        public long refundAmount() { return previousBidder == null || bidder.equals(previousBidder) ? 0 : previousAmount; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
