package net.nerdsmp.paper.command;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.ClaimService.ClaimResult;
import net.nerdsmp.api.service.EconomyService.TransactionResult;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Compensates ordinary capacity-update failures; this is not a durable transaction journal. */
final class ClaimExpansionPurchase {
    private final Set<AccountId> pending = ConcurrentHashMap.newKeySet();

    enum Outcome { EXPANDED, MAXIMUM, INSUFFICIENT_FUNDS, REFUNDED, REFUND_FAILED, BUSY, FAILED }

    record Result(Outcome outcome, String detail) {
        static Result of(Outcome outcome) {
            return new Result(outcome, "");
        }
    }

    CompletionStage<Result> expand(
        AccountId account,
        int maximum,
        Supplier<CompletionStage<Integer>> capacity,
        Supplier<CompletionStage<TransactionResult>> debit,
        Supplier<CompletionStage<ClaimResult>> addCapacity,
        Supplier<CompletionStage<TransactionResult>> refund
    ) {
        if (!pending.add(account)) {
            return CompletableFuture.completedFuture(Result.of(Outcome.BUSY));
        }
        return call(capacity).thenCompose(current -> {
            if (current >= maximum) {
                return CompletableFuture.completedFuture(Result.of(Outcome.MAXIMUM));
            }
            return call(debit).thenCompose(payment -> {
                if (payment.status() == TransactionResult.Status.INSUFFICIENT_FUNDS) {
                    return CompletableFuture.completedFuture(Result.of(Outcome.INSUFFICIENT_FUNDS));
                }
                if (payment.status() != TransactionResult.Status.APPLIED) {
                    return CompletableFuture.completedFuture(new Result(Outcome.FAILED,
                        "Unexpected debit result: " + payment.status()));
                }
                // Wrapping the invocation also catches a synchronous YAML save failure.
                return call(addCapacity).handle((updated, failure) -> failure == null
                    && updated == ClaimResult.CAPACITY_UPDATED).thenCompose(updated -> {
                        if (updated) {
                            return CompletableFuture.completedFuture(Result.of(Outcome.EXPANDED));
                        }
                        return call(refund).handle((returned, failure) -> {
                            if (failure != null) {
                                return new Result(Outcome.REFUND_FAILED, failure.toString());
                            }
                            if (returned != null && (returned.status() == TransactionResult.Status.APPLIED
                                || returned.status() == TransactionResult.Status.DUPLICATE)) {
                                return Result.of(Outcome.REFUNDED);
                            }
                            return new Result(Outcome.REFUND_FAILED,
                                "Unexpected refund result: " + (returned == null ? "null" : returned.status()));
                        });
                    });
            });
        }).exceptionally(failure -> new Result(Outcome.FAILED, failure.toString()))
            .whenComplete((result, failure) -> pending.remove(account));
    }

    private static <T> CompletionStage<T> call(Supplier<CompletionStage<T>> action) {
        try {
            return Objects.requireNonNull(action.get(), "Service returned no completion stage");
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
