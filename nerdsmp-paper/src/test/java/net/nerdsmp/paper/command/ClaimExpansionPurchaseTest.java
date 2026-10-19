package net.nerdsmp.paper.command;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.ClaimService.ClaimResult;
import net.nerdsmp.api.service.EconomyService.TransactionResult;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static net.nerdsmp.paper.command.ClaimExpansionPurchase.Outcome.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClaimExpansionPurchaseTest {
    private final ClaimExpansionPurchase purchases = new ClaimExpansionPurchase();

    @Test
    void aSynchronousCapacitySaveFailureRefundsTheAppliedPayment() {
        AtomicInteger refunds = new AtomicInteger();
        var result = purchases.expand(AccountId.random(), 64, () -> done(4),
            () -> done(TransactionResult.applied(0)),
            () -> { throw new IllegalStateException("disk full"); },
            () -> { refunds.incrementAndGet(); return done(TransactionResult.applied(500)); });

        assertEquals(REFUNDED, result.toCompletableFuture().join().outcome());
        assertEquals(1, refunds.get());
    }

    @Test
    void aRejectedRefundIsNotReportedAsReturnedMoney() {
        var result = purchases.expand(AccountId.random(), 64, () -> done(4),
            () -> done(TransactionResult.applied(0)), () -> done(ClaimResult.MAX_CAPACITY_REACHED),
            () -> done(new TransactionResult(TransactionResult.Status.INVALID_AMOUNT, 0, OptionalLong.empty())));

        assertEquals(REFUND_FAILED, result.toCompletableFuture().join().outcome());
    }

    @Test
    void aSynchronousRefundFailureNeedsReviewAndReleasesTheAccountLock() {
        AccountId account = AccountId.random();
        var result = purchases.expand(account, 64, () -> done(4), () -> done(TransactionResult.applied(0)),
            () -> CompletableFuture.failedFuture(new IllegalStateException("save failed")),
            () -> { throw new IllegalStateException("refund failed"); });

        assertEquals(REFUND_FAILED, result.toCompletableFuture().join().outcome());
        assertEquals(EXPANDED, success(account).toCompletableFuture().join().outcome());
    }

    @Test
    void simultaneousRequestsCannotChargeTheSameAccountTwice() {
        AccountId account = AccountId.random();
        CompletableFuture<Integer> capacity = new CompletableFuture<>();
        AtomicInteger debits = new AtomicInteger();
        var first = purchases.expand(account, 64, () -> capacity,
            () -> { debits.incrementAndGet(); return done(TransactionResult.applied(0)); },
            () -> done(ClaimResult.CAPACITY_UPDATED), () -> done(TransactionResult.applied(500)));

        assertEquals(BUSY, success(account).toCompletableFuture().join().outcome());
        assertEquals(EXPANDED, success(AccountId.random()).toCompletableFuture().join().outcome());
        capacity.complete(4);
        assertEquals(EXPANDED, first.toCompletableFuture().join().outcome());
        assertEquals(1, debits.get());
        assertEquals(EXPANDED, success(account).toCompletableFuture().join().outcome());
    }

    @Test
    void aCapacityLookupFailureDoesNotLeaveTheAccountLocked() {
        AccountId account = AccountId.random();
        var first = purchases.expand(account, 64, () -> { throw new IllegalStateException("read failed"); },
            () -> done(TransactionResult.applied(0)), () -> done(ClaimResult.CAPACITY_UPDATED),
            () -> done(TransactionResult.applied(500)));

        assertEquals(FAILED, first.toCompletableFuture().join().outcome());
        assertEquals(EXPANDED, success(account).toCompletableFuture().join().outcome());
    }

    private java.util.concurrent.CompletionStage<ClaimExpansionPurchase.Result> success(AccountId account) {
        return purchases.expand(account, 64, () -> done(4), () -> done(TransactionResult.applied(0)),
            () -> done(ClaimResult.CAPACITY_UPDATED), () -> done(TransactionResult.applied(500)));
    }

    private static <T> CompletableFuture<T> done(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
