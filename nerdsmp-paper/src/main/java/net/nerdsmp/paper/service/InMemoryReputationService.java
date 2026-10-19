package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.ReputationService;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryReputationService implements ReputationService {
    protected final Map<AccountId, Map<FactionId, Integer>> values = new ConcurrentHashMap<>();
    protected final Set<String> appliedOperations = ConcurrentHashMap.newKeySet();

    @Override
    public CompletionStage<Integer> value(AccountId accountId, FactionId factionId) {
        return CompletableFuture.completedFuture(current(accountId, factionId));
    }

    @Override
    public CompletionStage<Map<FactionId, Integer>> standings(AccountId accountId) {
        return CompletableFuture.completedFuture(Map.copyOf(
            values.getOrDefault(accountId, Map.of())
        ));
    }

    @Override
    public synchronized CompletionStage<AdjustmentResult> adjust(
        AccountId accountId,
        FactionId factionId,
        int amount,
        ReputationReason reason,
        String idempotencyKey
    ) {
        int previous = current(accountId, factionId);
        if (amount == 0 || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            return completed(AdjustmentResult.Status.INVALID_AMOUNT, previous, previous);
        }
        if (!appliedOperations.add(idempotencyKey)) {
            return completed(AdjustmentResult.Status.DUPLICATE, previous, previous);
        }
        int next = clamp((long) previous + amount);
        values.computeIfAbsent(accountId, ignored -> new ConcurrentHashMap<>()).put(factionId, next);
        return CompletableFuture.completedFuture(new AdjustmentResult(
            AdjustmentResult.Status.APPLIED, previous, next, next - previous
        ));
    }

    protected int current(AccountId accountId, FactionId factionId) {
        return values.getOrDefault(accountId, Map.of()).getOrDefault(factionId, 0);
    }

    protected int clamp(long value) {
        return (int) Math.max(MINIMUM, Math.min(MAXIMUM, value));
    }

    private CompletionStage<AdjustmentResult> completed(
        AdjustmentResult.Status status,
        int previous,
        int value
    ) {
        return CompletableFuture.completedFuture(new AdjustmentResult(status, previous, value, value - previous));
    }
}
