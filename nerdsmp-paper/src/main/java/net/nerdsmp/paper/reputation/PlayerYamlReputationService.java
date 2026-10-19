package net.nerdsmp.paper.reputation;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.service.InMemoryReputationService;
import net.nerdsmp.paper.storage.PlayerDataStore;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Local reputation persistence stored alongside the rest of a player's seasonal data. */
public final class PlayerYamlReputationService extends InMemoryReputationService implements AutoCloseable {
    private final PlayerDataStore store;
    private final String root;
    private final Set<AccountId> loaded = ConcurrentHashMap.newKeySet();

    public PlayerYamlReputationService(PlayerDataStore store, String seasonId) {
        this.store = store;
        root = "seasons." + seasonId.replaceAll("[^a-zA-Z0-9_-]", "_") + ".reputation";
    }

    @Override
    public CompletionStage<Integer> value(AccountId accountId, FactionId factionId) {
        ensureLoaded(accountId);
        return super.value(accountId, factionId);
    }

    @Override
    public CompletionStage<Map<FactionId, Integer>> standings(AccountId accountId) {
        ensureLoaded(accountId);
        return super.standings(accountId);
    }

    @Override
    public synchronized CompletionStage<AdjustmentResult> adjust(
        AccountId accountId,
        FactionId factionId,
        int amount,
        ReputationReason reason,
        String idempotencyKey
    ) {
        ensureLoaded(accountId);
        CompletionStage<AdjustmentResult> result = super.adjust(accountId, factionId, amount, reason, idempotencyKey);
        if (result.toCompletableFuture().join().status() == AdjustmentResult.Status.APPLIED) {
            persist(accountId);
        }
        return result;
    }

    private void ensureLoaded(AccountId accountId) {
        if (!loaded.add(accountId)) {
            return;
        }
        store.read(accountId, yaml -> {
            var section = yaml.getConfigurationSection(root + ".factions");
            Map<FactionId, Integer> accountValues = new ConcurrentHashMap<>();
            if (section != null) {
                for (String raw : section.getKeys(false)) {
                    try {
                        accountValues.put(new FactionId(raw), clamp(section.getLong(raw)));
                    } catch (IllegalArgumentException ignored) {
                        // Retain valid faction entries from a hand-edited file.
                    }
                }
            }
            values.put(accountId, accountValues);
            appliedOperations.addAll(yaml.getStringList(root + ".applied-operations"));
            return null;
        });
    }

    private void persist(AccountId accountId) {
        String prefix = accountId + ":";
        store.update(accountId, yaml -> {
            yaml.set(root + ".factions", null);
            values.getOrDefault(accountId, Map.of()).forEach((faction, value) ->
                yaml.set(root + ".factions." + faction.value(), value));
            yaml.set(root + ".applied-operations", appliedOperations.stream()
                .filter(key -> key.startsWith(prefix)).sorted().toList());
        });
    }

    @Override
    public void close() {
        // Mutations are flushed immediately through atomic per-player writes.
    }
}
