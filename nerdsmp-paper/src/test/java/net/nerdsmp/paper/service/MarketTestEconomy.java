package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A persistent external ledger with controllable lost acknowledgements. */
final class MarketTestEconomy implements EconomyService {
    private final Ledger ledger;
    private AccountId loseDebitFor;
    private AccountId loseCreditFor;
    private AccountId holdDebitFor;
    private CompletableFuture<TransactionResult> heldDebit;
    private TransactionResult heldResult;

    MarketTestEconomy() {
        this(new Ledger());
    }

    private MarketTestEconomy(Ledger ledger) {
        this.ledger = ledger;
    }

    MarketTestEconomy restarted() {
        return new MarketTestEconomy(ledger);
    }

    void fund(AccountId account, long amount) {
        ledger.balances.put(account, amount);
    }

    long marks(AccountId account) {
        return ledger.balances.getOrDefault(account, 0L);
    }

    void loseNextDebitResponse(AccountId account) {
        loseDebitFor = account;
    }

    void loseNextCreditResponse(AccountId account) {
        loseCreditFor = account;
    }

    void holdNextDebitResponse(AccountId account) {
        holdDebitFor = account;
    }

    void releaseDebitResponse() {
        if (heldDebit == null) {
            throw new IllegalStateException("No debit response is held");
        }
        heldDebit.complete(heldResult);
    }

    int appliedDebits(AccountId account) {
        return (int) ledger.operations.values().stream()
            .filter(operation -> operation.type.equals("debit") && operation.account.equals(account)).count();
    }

    int appliedCredits(AccountId account) {
        return (int) ledger.operations.values().stream()
            .filter(operation -> operation.type.equals("credit") && operation.account.equals(account)).count();
    }

    @Override
    public CompletionStage<Long> balance(AccountId accountId) {
        return CompletableFuture.completedFuture(marks(accountId));
    }

    @Override
    public synchronized CompletionStage<TransactionResult> credit(AccountId account, long marks,
                                                                   TransactionReason reason, String key) {
        TransactionResult result = apply(new Operation("credit", account, marks), key);
        if (account.equals(loseCreditFor)) {
            loseCreditFor = null;
            return CompletableFuture.failedFuture(new IllegalStateException("Credit committed; response lost"));
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public synchronized CompletionStage<TransactionResult> debit(AccountId account, long marks,
                                                                  TransactionReason reason, String key) {
        TransactionResult result = apply(new Operation("debit", account, marks), key);
        if (account.equals(loseDebitFor)) {
            loseDebitFor = null;
            return CompletableFuture.failedFuture(new IllegalStateException("Debit committed; response lost"));
        }
        if (account.equals(holdDebitFor)) {
            holdDebitFor = null;
            heldResult = result;
            heldDebit = new CompletableFuture<>();
            return heldDebit;
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletionStage<TransactionResult> transfer(AccountId source, AccountId destination, long marks,
                                                         TransactionReason reason, String idempotencyKey) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Market uses escrow debit/credit"));
    }

    private TransactionResult apply(Operation operation, String key) {
        if (operation.marks <= 0 || key == null || key.isBlank()) {
            return new TransactionResult(TransactionResult.Status.INVALID_AMOUNT, marks(operation.account), OptionalLong.empty());
        }
        Operation previous = ledger.operations.get(key);
        if (previous != null) {
            if (!previous.equals(operation)) {
                throw new IllegalStateException("Idempotency key reused for a different payment: " + key);
            }
            return new TransactionResult(TransactionResult.Status.DUPLICATE, marks(operation.account), OptionalLong.empty());
        }
        if (operation.type.equals("debit") && marks(operation.account) < operation.marks) {
            return new TransactionResult(TransactionResult.Status.INSUFFICIENT_FUNDS,
                marks(operation.account), OptionalLong.empty());
        }
        long balance = Math.addExact(marks(operation.account), operation.type.equals("credit") ? operation.marks : -operation.marks);
        ledger.balances.put(operation.account, balance);
        ledger.operations.put(key, operation);
        return TransactionResult.applied(balance);
    }

    private static final class Ledger {
        private final Map<AccountId, Long> balances = new HashMap<>();
        private final Map<String, Operation> operations = new HashMap<>();
    }

    private record Operation(String type, AccountId account, long marks) {
    }
}
