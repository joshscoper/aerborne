package net.nerdsmp.paper.market;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.MarketService;
import net.nerdsmp.paper.market.MarketStore.*;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Durable item escrow. Bukkit inventory mutations belong exclusively to the caller. */
public final class DurableMarketService implements MarketService<String> {
    private final MarketStore store;
    private final EconomyService economy;
    private final Clock clock;
    private Snapshot state;
    private final Set<UUID> uncertainOnLoad = new HashSet<>();
    private final Map<UUID, CompletableFuture<Result<String>>> inFlight = new HashMap<>();

    public DurableMarketService(File file, EconomyService economy) throws IOException {
        this(new MarketStore(file), economy, Clock.systemUTC());
    }

    public DurableMarketService(MarketStore store, EconomyService economy, Clock clock) throws IOException {
        this.store = Objects.requireNonNull(store);
        this.economy = Objects.requireNonNull(economy);
        this.clock = Objects.requireNonNull(clock);
        state = store.load();
        state.entries().forEach((id, value) -> {
            if (value.entry().listing().status() == Status.DEPOSIT_PENDING
                || value.entry().listing().status() == Status.DELIVERING) uncertainOnLoad.add(id);
        });
    }

    @Override
    public synchronized CompletionStage<Stall> registerStall(AccountId owner, StallLocation location) {
        if (owner == null || location == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Owner and location required"));
        Stall existing = stallFor(owner).orElse(null);
        if (existing != null && existing.location().equals(location)) return CompletableFuture.completedFuture(existing);
        if (existing != null && state.entries().values().stream().anyMatch(value ->
            value.entry().listing().stallId().equals(existing.id()) && !terminal(value.entry().listing().status()))) {
            return CompletableFuture.failedFuture(new IllegalStateException("Collect or finish all listings before moving this stall"));
        }
        Stall stall = new Stall(existing == null ? UUID.randomUUID() : existing.id(), owner, location);
        Map<UUID, Stall> stalls = new HashMap<>(state.stalls());
        stalls.put(stall.id(), stall);
        Snapshot next = new Snapshot(stalls, state.entries(), audited(null, owner.toString(), "STALL_REGISTERED", stall.id().toString()));
        if (!save(next)) return CompletableFuture.failedFuture(new IOException("The stall could not be saved"));
        return CompletableFuture.completedFuture(stall);
    }

    @Override public synchronized Optional<Stall> stall(UUID id) { return Optional.ofNullable(state.stalls().get(id)); }
    @Override public synchronized Optional<Stall> stallFor(AccountId owner) {
        return state.stalls().values().stream().filter(stall -> stall.owner().equals(owner)).findFirst();
    }

    @Override
    public synchronized CompletionStage<Result<String>> prepareListing(AccountId seller, UUID stallId, String item, long price, long fee) {
        return prepareListing(seller, stallId, item, price, fee, Venue.STORE);
    }

    @Override
    public synchronized CompletionStage<Result<String>> prepareAuctionListing(AccountId seller, UUID stallId, String item, long startingPrice, long fee) {
        return prepareListing(seller, stallId, item, startingPrice, fee, Venue.AUCTION);
    }

    @Override public synchronized boolean isAuction(UUID id) {
        if (id == null) return false;
        StoredEntry value = state.entries().get(id);
        return value != null && value.venue() == Venue.AUCTION;
    }

    private CompletionStage<Result<String>> prepareListing(AccountId seller, UUID stallId, String item, long price, long fee, Venue venue) {
        Stall stall = state.stalls().get(stallId);
        if (stall == null || !stall.owner().equals(seller)) return done(result(Outcome.DENIED, null, "You do not own this stall."));
        if (price <= 0 || fee < 0) return done(result(Outcome.INVALID, null, "Invalid listing price or fee."));
        String checksum;
        try { checksum = MarketStore.checksum(item); }
        catch (RuntimeException exception) { return done(result(Outcome.INVALID, null, "The item could not be serialized.")); }
        UUID id = UUID.randomUUID();
        Listing<String> listing = new Listing<>(id, seller, stallId, item, price, Status.DEPOSIT_PENDING, null, null);
        StoredEntry value = new StoredEntry(new Entry<>(listing, fee, 0, null, null, null, ReviewKind.NONE, ""), checksum, false, false, venue);
        if (!publish(value, seller.toString(), "DEPOSIT_PREPARED", "Inventory removal has not been confirmed.")) return done(saveFailed(id));
        return done(result(Outcome.PREPARED, id, "Deposit prepared. Confirm after saving the player's inventory."));
    }

    @Override
    public CompletionStage<Result<String>> confirmDeposit(UUID id, AccountId seller) {
        synchronized (this) {
            StoredEntry value = state.entries().get(id);
            Result<String> denied = sellerCheck(value, seller);
            if (denied != null) return done(denied);
            if (uncertainOnLoad.contains(id)) return done(result(Outcome.PENDING, id, "Restart recovery must review this deposit."));
            Status status = value.entry().listing().status();
            if (status != Status.DEPOSIT_PENDING && status != Status.FEE_PENDING) return done(unavailable(id));
            if (status == Status.DEPOSIT_PENDING && !publish(status(value, Status.FEE_PENDING), seller.toString(),
                "DEPOSIT_CONFIRMED", "Inventory removal and player save confirmed.")) return done(saveFailed(id));
        }
        return resume(id);
    }

    @Override
    public synchronized CompletionStage<Result<String>> abandonDeposit(UUID id, AccountId seller) {
        StoredEntry value = state.entries().get(id);
        Result<String> denied = sellerCheck(value, seller);
        if (denied != null) return done(denied);
        if (value.entry().listing().status() != Status.DEPOSIT_PENDING || uncertainOnLoad.contains(id)) return done(unavailable(id));
        if (!publish(status(value, Status.CANCELLED), seller.toString(), "DEPOSIT_ABANDONED", "Caller confirms no inventory mutation occurred.")) return done(saveFailed(id));
        return done(result(Outcome.CANCELLED, id, "Deposit cancelled before any items were removed."));
    }

    @Override
    public synchronized CompletionStage<Result<String>> quarantineDeposit(UUID id, AccountId seller, String reason) {
        StoredEntry value = state.entries().get(id);
        Result<String> denied = sellerCheck(value, seller);
        if (denied != null) return done(denied);
        if (!validReason(reason)) return done(result(Outcome.INVALID, id, "A review reason is required."));
        if (value.entry().listing().status() != Status.DEPOSIT_PENDING) return done(unavailable(id));
        if (!publish(review(value, ReviewKind.DEPOSIT, reason), seller.toString(), "DEPOSIT_REVIEW", reason)) {
            // Fail closed in this process too; a later retry must never treat this as an untouched deposit.
            uncertainOnLoad.add(id);
            return done(saveFailed(id));
        }
        uncertainOnLoad.remove(id);
        return done(result(Outcome.REVIEW, id, "Staff will verify whether your item was deposited."));
    }

    @Override public synchronized CompletionStage<List<Listing<String>>> activeListings() {
        expireReservations();
        return CompletableFuture.completedFuture(state.entries().values().stream().map(value -> value.entry().listing())
            .filter(listing -> listing.status() == Status.ACTIVE || listing.status() == Status.RESERVED)
            .sorted(Comparator.comparingLong(Listing<String>::price).thenComparing(listing -> listing.id().toString())).toList());
    }
    @Override public synchronized CompletionStage<Optional<Listing<String>>> find(UUID id) {
        expireReservations();
        return CompletableFuture.completedFuture(entry(id).map(Entry::listing));
    }
    @Override public synchronized Optional<Entry<String>> entry(UUID id) {
        StoredEntry value = state.entries().get(id);
        return value == null ? Optional.empty() : Optional.of(value.entry());
    }
    @Override public synchronized List<Entry<String>> entriesFor(AccountId account) {
        return allEntries().stream().filter(entry -> entry.listing().seller().equals(account)
            || Objects.equals(entry.recipient(), account) || Objects.equals(entry.listing().reservedBy(), account)).toList();
    }
    @Override public synchronized List<Entry<String>> allEntries() {
        return state.entries().values().stream().map(StoredEntry::entry)
            .sorted(Comparator.comparing(entry -> entry.listing().id().toString())).toList();
    }
    public synchronized List<Audit> audit() { return state.audit(); }

    @Override
    public synchronized CompletionStage<Result<String>> reserve(UUID id, AccountId buyer, Instant until) {
        if (buyer == null || until == null || !until.isAfter(clock.instant())
            || until.isAfter(clock.instant().plus(Duration.ofMinutes(10)))) return done(result(Outcome.INVALID, id, "Reservations last up to ten minutes."));
        if (!expireReservations()) return done(saveFailed(id));
        StoredEntry value = state.entries().get(id);
        if (value == null) return done(notFound());
        if (value.venue() == Venue.AUCTION) return done(unavailable(id));
        Listing<String> listing = value.entry().listing();
        if (listing.seller().equals(buyer)) return done(result(Outcome.DENIED, id, "You cannot reserve your own listing."));
        if (listing.status() == Status.RESERVED && buyer.equals(listing.reservedBy())) return done(result(Outcome.RESERVED, id, "Your reservation is still active."));
        if (listing.status() != Status.ACTIVE) return done(unavailable(id));
        StoredEntry next = replace(value, Status.RESERVED, buyer, until, null, null, null, 0, ReviewKind.NONE, "", false, false);
        if (!publish(next, buyer.toString(), "RESERVED", until.toString())) return done(saveFailed(id));
        return done(result(Outcome.RESERVED, id, "Reserved for five minutes. Buy and collect from anywhere."));
    }

    @Override
    public CompletionStage<Result<String>> checkout(UUID id, AccountId buyer, int taxPercent) {
        synchronized (this) {
            if (buyer == null || taxPercent < 0 || taxPercent > 100) return done(result(Outcome.INVALID, id, "Invalid checkout."));
            if (!expireReservations()) return done(saveFailed(id));
            StoredEntry value = state.entries().get(id);
            if (value == null) return done(notFound());
            if (value.venue() == Venue.AUCTION) return done(unavailable(id));
            Listing<String> listing = value.entry().listing();
            if (listing.seller().equals(buyer)) return done(result(Outcome.DENIED, id, "You cannot buy your own listing."));
            if (listing.status() == Status.CHECKOUT) {
                if (!buyer.equals(listing.reservedBy())) return done(unavailable(id));
            } else {
                if (listing.status() != Status.ACTIVE && listing.status() != Status.RESERVED) return done(unavailable(id));
                if (listing.status() == Status.RESERVED && !buyer.equals(listing.reservedBy())) return done(unavailable(id));
                StoredEntry next = replace(value, Status.CHECKOUT, buyer, null, UUID.randomUUID(), null, buyer,
                    taxPercent, ReviewKind.NONE, "", false, false);
                if (!publish(next, buyer.toString(), "CHECKOUT_LOCKED", "Price " + listing.price() + ", tax " + taxPercent)) return done(saveFailed(id));
            }
        }
        return resume(id);
    }

    @Override
    public synchronized CompletionStage<Result<String>> cancel(UUID id, AccountId seller) {
        StoredEntry value = state.entries().get(id);
        Result<String> denied = sellerCheck(value, seller);
        if (denied != null) return done(denied);
        if (value.venue() == Venue.AUCTION) return done(unavailable(id));
        Status status = value.entry().listing().status();
        if (status != Status.ACTIVE && status != Status.RESERVED) return done(unavailable(id));
        if (!publish(sellerReturn(value), seller.toString(), "RETURN_READY", "Seller cancelled the listing.")) return done(saveFailed(id));
        return done(result(Outcome.READY, id, "Your item is ready in Deliveries."));
    }

    @Override
    public synchronized CompletionStage<Result<String>> cancelAuction(UUID id, AccountId seller) {
        if (id == null) return done(result(Outcome.INVALID, null, "An auction listing is required."));
        StoredEntry value = state.entries().get(id);
        Result<String> denied = sellerCheck(value, seller);
        if (denied != null) return done(denied);
        if (value.venue() != Venue.AUCTION || value.entry().listing().status() != Status.AUCTION) return done(unavailable(id));
        if (!publish(sellerReturn(value), seller.toString(), "AUCTION_RETURN_READY", "Auction domain authorized the item return.")) return done(saveFailed(id));
        return done(result(Outcome.READY, id, "Your auction item is ready to collect."));
    }

    @Override
    public CompletionStage<Result<String>> settleAuction(UUID id, AccountId winner, long settledPrice, int taxPercent, UUID settlementKey) {
        synchronized (this) {
            if (id == null || winner == null || settledPrice <= 0 || taxPercent < 0 || taxPercent > 100 || settlementKey == null) {
                return done(result(Outcome.INVALID, id, "Invalid auction settlement."));
            }
            StoredEntry value = state.entries().get(id);
            if (value == null) return done(notFound());
            if (value.venue() != Venue.AUCTION) return done(unavailable(id));
            Entry<String> entry = value.entry();
            Listing<String> listing = entry.listing();
            if (listing.seller().equals(winner)) return done(result(Outcome.DENIED, id, "You cannot win your own auction."));
            if (entry.checkoutId() != null) {
                // Every retry must describe the exact winning settlement, even after delivery.
                if (!settlementKey.equals(entry.checkoutId()) || !winner.equals(listing.reservedBy())
                    || !winner.equals(entry.recipient()) || listing.price() != settledPrice || entry.taxPercent() != taxPercent) {
                    return done(result(Outcome.DENIED, id, "This auction already has a different settlement."));
                }
                if (listing.status() == Status.READY) return done(result(Outcome.READY, id, "Your auction item is ready to collect."));
                if (listing.status() == Status.DELIVERING) return done(result(Outcome.DELIVERING, id, "Your auction item is being delivered."));
                if (listing.status() == Status.SOLD) return done(result(Outcome.SOLD, id, "Auction delivery was already recorded."));
                if (listing.status() == Status.REVIEW) return done(result(Outcome.REVIEW, id, "Auction delivery needs staff review."));
                if (listing.status() != Status.CHECKOUT) return done(unavailable(id));
            } else {
                if (listing.status() != Status.AUCTION) return done(unavailable(id));
                if (settledPrice < listing.price()) return done(result(Outcome.INVALID, id, "The winning bid is below the starting price."));
                Listing<String> settled = new Listing<>(listing.id(), listing.seller(), listing.stallId(), listing.item(),
                    settledPrice, Status.CHECKOUT, winner, null);
                StoredEntry next = new StoredEntry(new Entry<>(settled, entry.listingFee(), taxPercent, settlementKey,
                    null, winner, ReviewKind.NONE, ""), value.checksum(), true, false, Venue.AUCTION);
                if (!publish(next, winner.toString(), "AUCTION_SETTLEMENT_LOCKED", "Escrowed bid " + settledPrice + ", tax " + taxPercent)) return done(saveFailed(id));
            }
        }
        return resume(id);
    }

    @Override
    public CompletionStage<Result<String>> retry(UUID id, AccountId actor) {
        synchronized (this) {
            StoredEntry value = state.entries().get(id);
            if (value == null) return done(notFound());
            Entry<String> entry = value.entry();
            if (actor == null || !(entry.listing().seller().equals(actor) || actor.equals(entry.recipient())
                || actor.equals(entry.listing().reservedBy()))) return done(result(Outcome.DENIED, id, "This transaction belongs to another player."));
            Status status = entry.listing().status();
            if (status == Status.READY) return done(result(Outcome.READY, id, "Your item is ready to collect."));
            if (status == Status.AUCTION) return done(result(Outcome.ACTIVE, id, "Your auction is waiting for bids."));
            if (status != Status.FEE_PENDING && status != Status.CHECKOUT) return done(unavailable(id));
        }
        return resume(id);
    }

    @Override
    public CompletionStage<List<Result<String>>> recover() {
        List<UUID> paymentIds;
        List<Result<String>> results = new ArrayList<>();
        synchronized (this) {
            Map<UUID, StoredEntry> entries = new HashMap<>(state.entries());
            List<Audit> audit = new ArrayList<>(state.audit());
            List<UUID> quarantined = new ArrayList<>();
            for (UUID id : uncertainOnLoad) {
                StoredEntry value = entries.get(id);
                if (value == null) continue;
                ReviewKind kind = value.entry().listing().status() == Status.DEPOSIT_PENDING ? ReviewKind.DEPOSIT : ReviewKind.DELIVERY;
                StoredEntry review = review(value, kind, "The server restarted during an inventory change. Staff must verify the player inventory.");
                entries.put(id, review);
                audit.add(audit(id, "system", "RESTART_REVIEW", review.entry().reviewReason()));
                quarantined.add(id);
            }
            if (!quarantined.isEmpty()) {
                if (!save(new Snapshot(state.stalls(), entries, audit))) return doneList(List.of(result(Outcome.SAVE_FAILED, null, "Recovery quarantine could not be saved. Retry recovery before opening the market.")));
                uncertainOnLoad.removeAll(quarantined);
                quarantined.forEach(id -> results.add(result(Outcome.REVIEW, id, "Inventory confirmation needs staff review.")));
            }
            paymentIds = state.entries().values().stream().filter(value -> value.entry().listing().status() == Status.FEE_PENDING
                || value.entry().listing().status() == Status.CHECKOUT).map(value -> value.entry().listing().id()).toList();
        }
        List<CompletableFuture<Result<String>>> futures = paymentIds.stream().map(id -> resume(id).toCompletableFuture()).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<Result<String>> finished = new ArrayList<>(results);
            futures.forEach(future -> finished.add(future.join()));
            return List.copyOf(finished);
        });
    }

    @Override
    public synchronized CompletionStage<Result<String>> beginDelivery(UUID id, AccountId recipient) {
        StoredEntry value = state.entries().get(id);
        if (value == null) return done(notFound());
        if (recipient == null || !recipient.equals(value.entry().recipient())) return done(result(Outcome.DENIED, id, "This delivery belongs to another player."));
        if (value.entry().listing().status() != Status.READY) return done(unavailable(id));
        Entry<String> e = value.entry();
        StoredEntry next = replace(value, Status.DELIVERING, e.listing().reservedBy(), null, e.checkoutId(), UUID.randomUUID(),
            recipient, e.taxPercent(), ReviewKind.NONE, "", value.debitConfirmed(), value.payoutConfirmed());
        if (!publish(next, recipient.toString(), "DELIVERY_STARTED", "Caller must save inventory before confirming this token.")) return done(saveFailed(id));
        return done(result(Outcome.DELIVERING, id, "Delivery token issued."));
    }

    @Override
    public synchronized CompletionStage<Result<String>> finishDelivery(UUID id, AccountId recipient, UUID token) {
        StoredEntry value = state.entries().get(id);
        if (!deliveryMatches(value, recipient, token)) return done(result(value == null ? Outcome.NOT_FOUND : Outcome.DENIED, id, "Invalid delivery token."));
        Status current = value.entry().listing().status();
        if (terminal(current)) return done(result(current == Status.SOLD ? Outcome.SOLD : Outcome.CANCELLED, id, "Delivery was already recorded."));
        if (current != Status.DELIVERING || uncertainOnLoad.contains(id)) return done(unavailable(id));
        Status terminal = recipient.equals(value.entry().listing().seller()) ? Status.CANCELLED : Status.SOLD;
        if (!publish(status(value, terminal), recipient.toString(), "DELIVERY_FINISHED", "Inventory save confirmed.")) return done(saveFailed(id));
        return done(result(terminal == Status.SOLD ? Outcome.SOLD : Outcome.CANCELLED, id, "Delivery recorded."));
    }

    @Override
    public synchronized CompletionStage<Result<String>> quarantineDelivery(UUID id, AccountId recipient, UUID token, String reason) {
        StoredEntry value = state.entries().get(id);
        if (!deliveryMatches(value, recipient, token)) return done(result(value == null ? Outcome.NOT_FOUND : Outcome.DENIED, id, "Invalid delivery token."));
        if (value.entry().listing().status() != Status.DELIVERING || !validReason(reason)) return done(unavailable(id));
        if (!publish(review(value, ReviewKind.DELIVERY, reason), recipient.toString(), "DELIVERY_REVIEW", reason)) {
            uncertainOnLoad.add(id);
            return done(saveFailed(id));
        }
        uncertainOnLoad.remove(id);
        return done(result(Outcome.REVIEW, id, "Staff will verify whether the item reached your inventory."));
    }

    @Override
    public CompletionStage<Result<String>> resolve(UUID id, Resolution resolution, String staff, String reason) {
        boolean feeRetry = false;
        Result<String> immediate;
        synchronized (this) {
            StoredEntry value = state.entries().get(id);
            if (value == null) return done(notFound());
            if (resolution == null || staff == null || staff.isBlank() || staff.length() > 128 || !validReason(reason)) return done(result(Outcome.INVALID, id, "Staff identity and a review reason are required."));
            Entry<String> e = value.entry();
            if (e.listing().status() != Status.REVIEW) return done(result(Outcome.DENIED, id, "Only inventory review cases can be resolved."));
            StoredEntry next;
            Outcome outcome;
            if (e.reviewKind() == ReviewKind.DEPOSIT && resolution == Resolution.DEPOSIT_KEPT) {
                next = status(value, Status.CANCELLED); outcome = Outcome.CANCELLED;
            } else if (e.reviewKind() == ReviewKind.DEPOSIT && resolution == Resolution.DEPOSIT_TAKEN) {
                next = status(value, Status.FEE_PENDING); outcome = Outcome.PENDING; feeRetry = true;
            } else if (e.reviewKind() == ReviewKind.DELIVERY && resolution == Resolution.DELIVERY_RECEIVED) {
                Status terminal = e.recipient().equals(e.listing().seller()) ? Status.CANCELLED : Status.SOLD;
                next = status(value, terminal); outcome = terminal == Status.SOLD ? Outcome.SOLD : Outcome.CANCELLED;
            } else if (e.reviewKind() == ReviewKind.DELIVERY && resolution == Resolution.DELIVERY_MISSING) {
                next = replace(value, Status.READY, e.listing().reservedBy(), null, e.checkoutId(), null,
                    e.recipient(), e.taxPercent(), ReviewKind.NONE, "", value.debitConfirmed(), value.payoutConfirmed());
                outcome = Outcome.READY;
            } else return done(result(Outcome.DENIED, id, "That resolution does not match this review case."));
            if (!publish(next, staff, "STAFF_" + resolution.name(), reason)) return done(saveFailed(id));
            uncertainOnLoad.remove(id);
            immediate = result(outcome, id, "Review resolution recorded.");
        }
        return feeRetry ? resume(id) : done(immediate);
    }

    private CompletionStage<Result<String>> resume(UUID id) {
        CompletableFuture<Result<String>> promise;
        synchronized (this) {
            CompletableFuture<Result<String>> existing = inFlight.get(id);
            if (existing != null) return existing;
            promise = new CompletableFuture<>();
            inFlight.put(id, promise);
        }
        advance(id, promise);
        return promise;
    }

    private void advance(UUID id, CompletableFuture<Result<String>> promise) {
        Payment payment = null;
        Result<String> immediate = null;
        synchronized (this) {
            StoredEntry value = state.entries().get(id);
            if (value == null) immediate = notFound();
            else {
                Entry<String> e = value.entry();
                Listing<String> listing = e.listing();
                if (listing.status() == Status.FEE_PENDING && e.listingFee() == 0) {
                    immediate = publish(status(value, listingStatus(value)), listing.seller().toString(), "FEE_CONFIRMED", "Zero listing fee.")
                        ? result(Outcome.ACTIVE, id, "Your listing is active.") : saveFailed(id);
                } else if (listing.status() == Status.FEE_PENDING) {
                    payment = new Payment(Phase.FEE, listing.seller(), e.listingFee(), "market:" + id + ":fee:" + listing.seller());
                } else if (listing.status() == Status.CHECKOUT && !value.debitConfirmed()) {
                    payment = new Payment(Phase.DEBIT, listing.reservedBy(), listing.price(), paymentKey(e, "buyer"));
                } else if (listing.status() == Status.CHECKOUT && !value.payoutConfirmed() && net(listing.price(), e.taxPercent()) > 0) {
                    payment = new Payment(Phase.PAYOUT, listing.seller(), net(listing.price(), e.taxPercent()), paymentKey(e, "seller"));
                } else if (listing.status() == Status.CHECKOUT) {
                    StoredEntry ready = new StoredEntry(status(value, Status.READY).entry(), value.checksum(), true, true, value.venue());
                    immediate = publish(ready, "system", "PAYMENT_COMPLETE", "Seller net and tax settlement confirmed.")
                        ? result(Outcome.READY, id, "Purchase paid. Collect your item from Deliveries.") : saveFailed(id);
                } else immediate = unavailable(id);
            }
            if (immediate != null) inFlight.remove(id, promise);
        }
        if (immediate != null) { complete(id, promise, immediate); return; }
        Payment request = payment;
        try {
            EconomyService.TransactionReason reason = new EconomyService.TransactionReason(
                request.phase() == Phase.FEE ? "market.listing" : request.phase() == Phase.DEBIT ? "market.purchase" : "market.sale",
                "Market listing " + id);
            CompletionStage<EconomyService.TransactionResult> operation = request.phase() == Phase.PAYOUT
                ? economy.credit(request.account(), request.amount(), reason, request.key())
                : economy.debit(request.account(), request.amount(), reason, request.key());
            Objects.requireNonNull(operation, "Missing economic operation").whenComplete((response, error) -> {
                Result<String> result;
                synchronized (this) {
                    result = applyPayment(id, request, response, error);
                    if (result != null) inFlight.remove(id, promise);
                }
                if (result == null) advance(id, promise); else complete(id, promise, result);
            });
        } catch (RuntimeException exception) {
            Result<String> pending;
            synchronized (this) { pending = result(Outcome.PENDING, id, "Payment confirmation is pending. Retry this transaction safely."); }
            complete(id, promise, pending);
        }
    }

    private Result<String> applyPayment(UUID id, Payment request, EconomyService.TransactionResult response, Throwable error) {
        StoredEntry value = state.entries().get(id);
        if (value == null) return notFound();
        if (error != null || response == null) return result(Outcome.PENDING, id, "Payment confirmation is pending. Retry this transaction safely.");
        EconomyService.TransactionResult.Status status = response.status();
        if (status == EconomyService.TransactionResult.Status.APPLIED || status == EconomyService.TransactionResult.Status.DUPLICATE) {
            StoredEntry next = request.phase() == Phase.FEE ? status(value, listingStatus(value))
                : new StoredEntry(value.entry(), value.checksum(), true, request.phase() == Phase.PAYOUT || value.payoutConfirmed(), value.venue());
            if (!publish(next, request.account().toString(), request.phase().name() + "_CONFIRMED", request.key())) return saveFailed(id);
            return request.phase() == Phase.FEE ? result(Outcome.ACTIVE, id, "Your listing is active.") : null;
        }
        if (status == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS && request.phase() == Phase.FEE) {
            return publish(sellerReturn(value), request.account().toString(), "FEE_DECLINED", request.key())
                ? result(Outcome.READY, id, "Not enough Marks for the listing fee. Collect your returned item.") : saveFailed(id);
        }
        if (status == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS && request.phase() == Phase.DEBIT && !value.debitConfirmed()) {
            return publish(active(value), request.account().toString(), "BUYER_DECLINED", request.key())
                ? result(Outcome.INSUFFICIENT_FUNDS, id, "Not enough Marks. No payment was taken.") : saveFailed(id);
        }
        return result(Outcome.PENDING, id, "The payment could not be confirmed. This checkout remains locked for safe retry.");
    }

    private void complete(UUID id, CompletableFuture<Result<String>> promise, Result<String> result) {
        synchronized (this) { inFlight.remove(id, promise); }
        promise.complete(result);
    }

    private boolean expireReservations() {
        Map<UUID, StoredEntry> entries = new HashMap<>(state.entries());
        List<Audit> audit = new ArrayList<>(state.audit());
        boolean changed = false;
        for (StoredEntry value : state.entries().values()) {
            Listing<String> listing = value.entry().listing();
            if (listing.status() == Status.RESERVED && !listing.reservedUntil().isAfter(clock.instant())) {
                entries.put(listing.id(), active(value));
                audit.add(audit(listing.id(), "system", "RESERVATION_EXPIRED", "No checkout payment was started."));
                changed = true;
            }
        }
        return !changed || save(new Snapshot(state.stalls(), entries, audit));
    }

    private boolean publish(StoredEntry value, String actor, String action, String detail) {
        Map<UUID, StoredEntry> entries = new HashMap<>(state.entries());
        UUID id = value.entry().listing().id();
        entries.put(id, value);
        String journalDetail = detail + " [checkout=" + value.entry().checkoutId() + ", delivery=" + value.entry().deliveryId() + "]";
        return save(new Snapshot(state.stalls(), entries, audited(id, actor, action, journalDetail)));
    }
    private boolean save(Snapshot next) {
        try { store.save(next); state = next; return true; }
        catch (IOException | RuntimeException exception) { return false; }
    }
    private List<Audit> audited(UUID id, String actor, String action, String detail) {
        List<Audit> events = new ArrayList<>(state.audit()); events.add(audit(id, actor, action, detail)); return events;
    }
    private Audit audit(UUID id, String actor, String action, String detail) { return new Audit(UUID.randomUUID(), clock.instant(), id, actor, action, detail); }
    private static StoredEntry replace(StoredEntry value, Status status, AccountId buyer, Instant until, UUID checkout,
                                       UUID delivery, AccountId recipient, int tax, ReviewKind review, String reason,
                                       boolean debit, boolean payout) {
        Entry<String> e = value.entry(); Listing<String> listing = e.listing();
        return new StoredEntry(new Entry<>(new Listing<>(listing.id(), listing.seller(), listing.stallId(), listing.item(),
            listing.price(), status, buyer, until), e.listingFee(), tax, checkout, delivery, recipient, review, reason),
            value.checksum(), debit, payout, value.venue());
    }
    private static StoredEntry status(StoredEntry value, Status status) {
        Entry<String> e = value.entry();
        return replace(value, status, e.listing().reservedBy(), e.listing().reservedUntil(), e.checkoutId(), e.deliveryId(),
            e.recipient(), e.taxPercent(), ReviewKind.NONE, "", value.debitConfirmed(), value.payoutConfirmed());
    }
    private static StoredEntry active(StoredEntry value) { return replace(value, Status.ACTIVE, null, null, null, null, null, 0, ReviewKind.NONE, "", false, false); }
    private static Status listingStatus(StoredEntry value) { return value.venue() == Venue.AUCTION ? Status.AUCTION : Status.ACTIVE; }
    private static StoredEntry sellerReturn(StoredEntry value) { return replace(value, Status.READY, null, null, null, null, value.entry().listing().seller(), 0, ReviewKind.NONE, "", false, false); }
    private static StoredEntry review(StoredEntry value, ReviewKind kind, String reason) {
        Entry<String> e = value.entry();
        return replace(value, Status.REVIEW, e.listing().reservedBy(), e.listing().reservedUntil(), e.checkoutId(), e.deliveryId(),
            e.recipient(), e.taxPercent(), kind, reason, value.debitConfirmed(), value.payoutConfirmed());
    }
    private static boolean deliveryMatches(StoredEntry value, AccountId recipient, UUID token) {
        return value != null && recipient != null && token != null && recipient.equals(value.entry().recipient()) && token.equals(value.entry().deliveryId());
    }
    private Result<String> sellerCheck(StoredEntry value, AccountId seller) {
        if (value == null) return notFound();
        return value.entry().listing().seller().equals(seller) ? null : result(Outcome.DENIED, value.entry().listing().id(), "Only the seller may change this listing.");
    }
    private Result<String> result(Outcome outcome, UUID id, String message) { return new Result<>(outcome, id == null ? Optional.empty() : entry(id), message); }
    private Result<String> notFound() { return result(Outcome.NOT_FOUND, null, "Listing not found."); }
    private Result<String> unavailable(UUID id) { return result(Outcome.UNAVAILABLE, id, "This listing cannot perform that action in its current state."); }
    private Result<String> saveFailed(UUID id) { return result(Outcome.SAVE_FAILED, id, "The market journal could not be saved. No new inventory action is authorized."); }
    private static boolean terminal(Status status) { return status == Status.SOLD || status == Status.CANCELLED; }
    private static boolean validReason(String reason) { return reason != null && !reason.isBlank() && reason.length() <= 512; }
    private static long net(long gross, int tax) { return gross - ((gross / 100) * tax + (gross % 100) * tax / 100); }
    private static String paymentKey(Entry<String> e, String side) { return "market:" + e.listing().id() + ":checkout:" + e.checkoutId() + ":" + e.listing().reservedBy() + ":" + side; }
    private static CompletionStage<Result<String>> done(Result<String> result) { return CompletableFuture.completedFuture(result); }
    private static CompletionStage<List<Result<String>>> doneList(List<Result<String>> result) { return CompletableFuture.completedFuture(result); }
    private enum Phase { FEE, DEBIT, PAYOUT }
    private record Payment(Phase phase, AccountId account, long amount, String key) { }
}
