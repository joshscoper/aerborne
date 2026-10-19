package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.storage.PlayerDataStore;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEconomyService implements EconomyService {
    private final Map<AccountId, Long> balances = new ConcurrentHashMap<>();
    private final Set<String> appliedOperations = ConcurrentHashMap.newKeySet();
    private final Map<AccountId, Set<String>> operationsByAccount = new ConcurrentHashMap<>();
    private final Set<AccountId> loadedAccounts = ConcurrentHashMap.newKeySet();
    private final PlayerDataStore persistence;

    public InMemoryEconomyService() {
        this(null);
    }

    public InMemoryEconomyService(PlayerDataStore persistence) {
        this.persistence = persistence;
        if (persistence != null) {
            persistence.knownAccounts().forEach(this::ensureLoaded);
        }
    }

    @Override
    public synchronized CompletionStage<Long> balance(AccountId accountId) {
        ensureLoaded(accountId);
        return CompletableFuture.completedFuture(balances.getOrDefault(accountId, 0L));
    }

    @Override
    public synchronized CompletionStage<TransactionResult> credit(
        AccountId accountId,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    ) {
        ensureLoaded(accountId);
        if (marks <= 0L) {
            return completed(TransactionResult.Status.INVALID_AMOUNT, balances.getOrDefault(accountId, 0L));
        }
        if (appliedOperations.contains(idempotencyKey)) {
            return completed(TransactionResult.Status.DUPLICATE, balances.getOrDefault(accountId, 0L));
        }

        long next;
        try {
            next = Math.addExact(balances.getOrDefault(accountId, 0L), marks);
        } catch (ArithmeticException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return commitSingleAccount(accountId, next, idempotencyKey);
    }

    @Override
    public synchronized CompletionStage<TransactionResult> debit(
        AccountId accountId,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    ) {
        ensureLoaded(accountId);
        long current = balances.getOrDefault(accountId, 0L);
        if (marks <= 0L) {
            return completed(TransactionResult.Status.INVALID_AMOUNT, current);
        }
        if (appliedOperations.contains(idempotencyKey)) {
            return completed(TransactionResult.Status.DUPLICATE, current);
        }
        if (current < marks) {
            return completed(TransactionResult.Status.INSUFFICIENT_FUNDS, current);
        }

        long next = current - marks;
        return commitSingleAccount(accountId, next, idempotencyKey);
    }

    /**
     * Local transfers still write two player files separately and are not crash-atomic.
     * Recoverable workflows must use separately journaled, idempotent debit/credit steps.
     */
    @Override
    public synchronized CompletionStage<TransactionResult> transfer(
        AccountId source,
        AccountId destination,
        long marks,
        TransactionReason reason,
        String idempotencyKey
    ) {
        ensureLoaded(source);
        ensureLoaded(destination);
        long sourceBalance = balances.getOrDefault(source, 0L);
        long destinationBalance = balances.getOrDefault(destination, 0L);
        if (marks <= 0L || source.equals(destination)) {
            return CompletableFuture.completedFuture(new TransactionResult(
                TransactionResult.Status.INVALID_AMOUNT,
                sourceBalance,
                OptionalLong.of(destinationBalance)
            ));
        }
        if (appliedOperations.contains(idempotencyKey)) {
            return CompletableFuture.completedFuture(new TransactionResult(
                TransactionResult.Status.DUPLICATE,
                sourceBalance,
                OptionalLong.of(destinationBalance)
            ));
        }
        if (sourceBalance < marks) {
            return CompletableFuture.completedFuture(new TransactionResult(
                TransactionResult.Status.INSUFFICIENT_FUNDS,
                sourceBalance,
                OptionalLong.of(destinationBalance)
            ));
        }

        long newDestination = Math.addExact(destinationBalance, marks);
        appliedOperations.add(idempotencyKey);
        operationsByAccount.computeIfAbsent(source, ignored -> ConcurrentHashMap.newKeySet())
            .add(idempotencyKey);
        balances.put(source, sourceBalance - marks);
        balances.put(destination, newDestination);
        persist(source);
        persist(destination);
        return CompletableFuture.completedFuture(new TransactionResult(
            TransactionResult.Status.APPLIED,
            sourceBalance - marks,
            OptionalLong.of(newDestination)
        ));
    }

    private CompletionStage<TransactionResult> completed(TransactionResult.Status status, long balance) {
        return CompletableFuture.completedFuture(new TransactionResult(status, balance, OptionalLong.empty()));
    }

    private CompletionStage<TransactionResult> commitSingleAccount(AccountId accountId, long next, String idempotencyKey) {
        Set<String> nextOperations = ConcurrentHashMap.newKeySet();
        nextOperations.addAll(operationsByAccount.getOrDefault(accountId, Set.of()));
        nextOperations.add(idempotencyKey);
        try {
            persist(accountId, next, nextOperations);
        } catch (RuntimeException exception) {
            // Neither the balance nor either idempotency index has been published yet.
            // PlayerDataStore also publishes its document only after its atomic save succeeds.
            return CompletableFuture.failedFuture(exception);
        }
        balances.put(accountId, next);
        operationsByAccount.put(accountId, nextOperations);
        appliedOperations.add(idempotencyKey);
        return completed(TransactionResult.Status.APPLIED, next);
    }

    private void ensureLoaded(AccountId accountId) {
        if (persistence == null || loadedAccounts.contains(accountId)) {
            return;
        }
        AccountSnapshot snapshot = persistence.read(accountId, yaml -> new AccountSnapshot(
            Math.max(0L, yaml.getLong("economy.balance", 0L)), Set.copyOf(yaml.getStringList("economy.applied-operations"))));
        Set<String> loaded = ConcurrentHashMap.newKeySet();
        loaded.addAll(snapshot.operations());
        balances.put(accountId, snapshot.balance());
        operationsByAccount.put(accountId, loaded);
        appliedOperations.addAll(loaded);
        loadedAccounts.add(accountId);
    }

    private void persist(AccountId accountId) {
        persist(accountId, balances.getOrDefault(accountId, 0L), operationsByAccount.getOrDefault(accountId, Set.of()));
    }

    private void persist(AccountId accountId, long balance, Set<String> operations) {
        if (persistence == null) {
            return;
        }
        persistence.update(accountId, yaml -> {
            yaml.set("economy.balance", balance);
            yaml.set("economy.applied-operations", operations.stream().sorted().toList());
        });
    }

    private record AccountSnapshot(long balance, Set<String> operations) {
    }
}
