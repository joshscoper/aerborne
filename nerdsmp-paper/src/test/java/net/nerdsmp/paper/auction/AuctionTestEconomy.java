package net.nerdsmp.paper.auction;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** External ledger survives service restarts and can commit a transaction while losing its response. */
final class AuctionTestEconomy implements EconomyService {
    private final Ledger ledger;
    private AccountId loseDebit;
    private AccountId loseCredit;
    private AccountId holdDebit;
    private CompletableFuture<TransactionResult> held;
    private TransactionResult heldResult;
    Runnable afterNextDebit;
    Runnable afterNextCredit;

    AuctionTestEconomy() { this(new Ledger()); }
    private AuctionTestEconomy(Ledger ledger) { this.ledger = ledger; }
    AuctionTestEconomy restarted() { return new AuctionTestEconomy(ledger); }
    void fund(AccountId account, long amount) { ledger.balances.put(account, amount); }
    long marks(AccountId account) { return ledger.balances.getOrDefault(account, 0L); }
    void loseNextDebit(AccountId account) { loseDebit = account; }
    void loseNextCredit(AccountId account) { loseCredit = account; }
    void holdNextDebit(AccountId account) { holdDebit = account; }
    void releaseDebit() { held.complete(heldResult); }
    int debits(AccountId account) { return (int) ledger.operations.values().stream().filter(value -> !value.credit && value.account.equals(account)).count(); }
    int credits(AccountId account) { return (int) ledger.operations.values().stream().filter(value -> value.credit && value.account.equals(account)).count(); }

    @Override public CompletionStage<Long> balance(AccountId accountId) { return CompletableFuture.completedFuture(marks(accountId)); }
    @Override public synchronized CompletionStage<TransactionResult> debit(AccountId account, long amount, TransactionReason reason, String key) {
        TransactionResult result = apply(new Operation(false, account, amount), key);
        if (afterNextDebit != null) { Runnable action = afterNextDebit; afterNextDebit = null; action.run(); }
        if (account.equals(loseDebit)) { loseDebit = null; return CompletableFuture.failedFuture(new IllegalStateException("Debit committed; response lost")); }
        if (account.equals(holdDebit)) { holdDebit = null; heldResult = result; held = new CompletableFuture<>(); return held; }
        return CompletableFuture.completedFuture(result);
    }
    @Override public synchronized CompletionStage<TransactionResult> credit(AccountId account, long amount, TransactionReason reason, String key) {
        TransactionResult result = apply(new Operation(true, account, amount), key);
        if (afterNextCredit != null) { Runnable action = afterNextCredit; afterNextCredit = null; action.run(); }
        if (account.equals(loseCredit)) { loseCredit = null; return CompletableFuture.failedFuture(new IllegalStateException("Credit committed; response lost")); }
        return CompletableFuture.completedFuture(result);
    }
    @Override public CompletionStage<TransactionResult> transfer(AccountId source, AccountId destination, long amount, TransactionReason reason, String key) {
        throw new UnsupportedOperationException("Auctions use journaled debit/refund operations");
    }

    private TransactionResult apply(Operation operation, String key) {
        if (operation.amount <= 0) return new TransactionResult(TransactionResult.Status.INVALID_AMOUNT, marks(operation.account), OptionalLong.empty());
        Operation prior = ledger.operations.get(key);
        if (prior != null) {
            if (!prior.equals(operation)) throw new IllegalStateException("Payment key was reused with different terms");
            return new TransactionResult(TransactionResult.Status.DUPLICATE, marks(operation.account), OptionalLong.empty());
        }
        if (!operation.credit && marks(operation.account) < operation.amount) return new TransactionResult(TransactionResult.Status.INSUFFICIENT_FUNDS,
            marks(operation.account), OptionalLong.empty());
        long balance = Math.addExact(marks(operation.account), operation.credit ? operation.amount : -operation.amount);
        ledger.balances.put(operation.account, balance);
        ledger.operations.put(key, operation);
        return TransactionResult.applied(balance);
    }
    private static final class Ledger {
        private final Map<AccountId, Long> balances = new HashMap<>();
        private final Map<String, Operation> operations = new HashMap<>();
    }
    private record Operation(boolean credit, AccountId account, long amount) { }
}
