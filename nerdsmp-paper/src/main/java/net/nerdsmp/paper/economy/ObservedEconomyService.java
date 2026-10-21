package net.nerdsmp.paper.economy;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Wraps the storage-backed economy and reports which accounts' balances changed, so displays can
 * update when Marks move instead of polling the database for every player every second.
 * Listeners run on whichever thread completed the transaction.
 */
public final class ObservedEconomyService implements EconomyService {
    private final EconomyService delegate;
    private volatile Consumer<AccountId> listener = account -> { };

    public ObservedEconomyService(EconomyService delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public void onChange(Consumer<AccountId> listener) {
        this.listener = listener == null ? account -> { } : listener;
    }

    @Override
    public CompletionStage<Long> balance(AccountId accountId) {
        return delegate.balance(accountId);
    }

    @Override
    public CompletionStage<TransactionResult> credit(AccountId accountId, long marks, TransactionReason reason, String idempotencyKey) {
        return report(delegate.credit(accountId, marks, reason, idempotencyKey), accountId, null);
    }

    @Override
    public CompletionStage<TransactionResult> debit(AccountId accountId, long marks, TransactionReason reason, String idempotencyKey) {
        return report(delegate.debit(accountId, marks, reason, idempotencyKey), accountId, null);
    }

    @Override
    public CompletionStage<TransactionResult> transfer(AccountId source, AccountId destination, long marks, TransactionReason reason, String idempotencyKey) {
        return report(delegate.transfer(source, destination, marks, reason, idempotencyKey), source, destination);
    }

    private CompletionStage<TransactionResult> report(CompletionStage<TransactionResult> stage, AccountId first, AccountId second) {
        return stage.whenComplete((result, error) -> {
            if (error != null || result == null || result.status() != TransactionResult.Status.APPLIED) return;
            notify(first);
            if (second != null) notify(second);
        });
    }

    private void notify(AccountId account) {
        try {
            listener.accept(account);
        } catch (RuntimeException ignored) {
            // A display hiccup must never fail a Marks transaction.
        }
    }
}
