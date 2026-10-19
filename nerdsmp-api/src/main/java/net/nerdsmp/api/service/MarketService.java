package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable escrow workflow. Items must be immutable serialized values, never live inventories. */
public interface MarketService<T> {
    CompletionStage<Stall> registerStall(AccountId owner, StallLocation location);
    Optional<Stall> stall(UUID id);
    Optional<Stall> stallFor(AccountId owner);

    CompletionStage<Result<T>> prepareListing(AccountId seller, UUID stallId, T item, long price, long fee);
    CompletionStage<Result<T>> prepareAuctionListing(AccountId seller, UUID stallId, T item, long startingPrice, long fee);
    boolean isAuction(UUID id);
    CompletionStage<Result<T>> confirmDeposit(UUID id, AccountId seller);
    CompletionStage<Result<T>> abandonDeposit(UUID id, AccountId seller);
    CompletionStage<Result<T>> quarantineDeposit(UUID id, AccountId seller, String reason);
    CompletionStage<List<Listing<T>>> activeListings();
    CompletionStage<Optional<Listing<T>>> find(UUID id);
    Optional<Entry<T>> entry(UUID id);
    List<Entry<T>> entriesFor(AccountId account);
    List<Entry<T>> allEntries();

    CompletionStage<Result<T>> reserve(UUID id, AccountId buyer, Instant until);
    CompletionStage<Result<T>> checkout(UUID id, AccountId buyer, int taxPercent);
    CompletionStage<Result<T>> cancel(UUID id, AccountId seller);
    /** Caller owns auction bid/expiry validation; this only returns the escrowed item. */
    CompletionStage<Result<T>> cancelAuction(UUID id, AccountId seller);
    /** Trusted auction settlement: the winning amount has already been debited into auction escrow. */
    CompletionStage<Result<T>> settleAuction(UUID id, AccountId winner, long settledPrice, int taxPercent, UUID settlementKey);
    CompletionStage<Result<T>> retry(UUID id, AccountId actor);
    CompletionStage<List<Result<T>>> recover();

    CompletionStage<Result<T>> beginDelivery(UUID id, AccountId recipient);
    CompletionStage<Result<T>> finishDelivery(UUID id, AccountId recipient, UUID token);
    CompletionStage<Result<T>> quarantineDelivery(UUID id, AccountId recipient, UUID token, String reason);
    CompletionStage<Result<T>> resolve(UUID id, Resolution resolution, String staff, String reason);

    record Stall(UUID id, AccountId owner, StallLocation location) {
        public Stall {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(owner);
            java.util.Objects.requireNonNull(location);
        }
    }

    record StallLocation(UUID worldId, int x, int y, int z) {
        public StallLocation { java.util.Objects.requireNonNull(worldId); }
    }

    record Listing<T>(UUID id, AccountId seller, UUID stallId, T item, long price, Status status,
                      AccountId reservedBy, Instant reservedUntil) {
        public Listing {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(seller);
            java.util.Objects.requireNonNull(stallId);
            java.util.Objects.requireNonNull(item);
            java.util.Objects.requireNonNull(status);
            if (price <= 0) throw new IllegalArgumentException("Price must be positive");
        }
    }

    record Entry<T>(Listing<T> listing, long listingFee, int taxPercent, UUID checkoutId,
                    UUID deliveryId, AccountId recipient, ReviewKind reviewKind, String reviewReason) {
        public Entry {
            java.util.Objects.requireNonNull(listing);
            java.util.Objects.requireNonNull(reviewKind);
            reviewReason = reviewReason == null ? "" : reviewReason;
            if (listingFee < 0 || taxPercent < 0 || taxPercent > 100) {
                throw new IllegalArgumentException("Invalid market fee or tax");
            }
        }
    }

    record Result<T>(Outcome outcome, Optional<Entry<T>> entry, String message) {
        public Result {
            java.util.Objects.requireNonNull(outcome);
            java.util.Objects.requireNonNull(entry);
            java.util.Objects.requireNonNull(message);
        }
    }

    enum Status {
        DEPOSIT_PENDING, FEE_PENDING, ACTIVE, AUCTION, RESERVED, CHECKOUT, READY, DELIVERING, SOLD, CANCELLED, REVIEW
    }

    enum Outcome {
        PREPARED, ACTIVE, RESERVED, READY, DELIVERING, SOLD, CANCELLED, REVIEW, PENDING,
        NOT_FOUND, DENIED, UNAVAILABLE, INVALID, INSUFFICIENT_FUNDS, SAVE_FAILED
    }

    enum ReviewKind { NONE, DEPOSIT, DELIVERY }

    enum Resolution { DEPOSIT_KEPT, DEPOSIT_TAKEN, DELIVERY_RECEIVED, DELIVERY_MISSING }
}
