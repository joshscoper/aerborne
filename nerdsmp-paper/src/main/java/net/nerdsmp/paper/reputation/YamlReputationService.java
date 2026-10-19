package net.nerdsmp.paper.reputation;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.service.InMemoryReputationService;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class YamlReputationService extends InMemoryReputationService implements AutoCloseable {
    private final File file;

    public YamlReputationService(JavaPlugin plugin, String seasonId) {
        String safeSeason = seasonId.replaceAll("[^a-zA-Z0-9_-]", "_");
        file = new File(plugin.getDataFolder(), "reputation-" + safeSeason + ".yml");
        load();
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
        boolean operationExisted = idempotencyKey != null && appliedOperations.contains(idempotencyKey);
        CompletionStage<AdjustmentResult> stage = super.adjust(
            accountId, factionId, amount, reason, idempotencyKey
        );
        AdjustmentResult result = stage.toCompletableFuture().join();
        if (result.status() != AdjustmentResult.Status.APPLIED) {
            return stage;
        }
        try {
            save();
            return stage;
        } catch (RuntimeException exception) {
            Map<FactionId, Integer> accountValues = values.computeIfAbsent(
                accountId, ignored -> new ConcurrentHashMap<>()
            );
            if (previous == 0) {
                accountValues.remove(factionId);
            } else {
                accountValues.put(factionId, previous);
            }
            if (!operationExisted) {
                appliedOperations.remove(idempotencyKey);
            }
            throw exception;
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players != null) {
            for (String rawAccount : players.getKeys(false)) {
                AccountId account;
                try {
                    account = new AccountId(UUID.fromString(rawAccount));
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                ConfigurationSection factions = players.getConfigurationSection(rawAccount);
                if (factions == null) {
                    continue;
                }
                Map<FactionId, Integer> loaded = new ConcurrentHashMap<>();
                for (String rawFaction : factions.getKeys(false)) {
                    try {
                        loaded.put(new FactionId(rawFaction), clamp(factions.getLong(rawFaction)));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore hand-edited invalid ids while retaining valid reputation data.
                    }
                }
                values.put(account, loaded);
            }
        }
        appliedOperations.addAll(yaml.getStringList("applied-operations"));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(
            java.util.Comparator.comparing(AccountId::toString)
        )).forEach(account -> account.getValue().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(faction -> yaml.set(
                "players." + account.getKey() + "." + faction.getKey().value(), faction.getValue()
            )));
        yaml.set("applied-operations", appliedOperations.stream().sorted().toList());
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist reputation history", exception);
        }
    }

    @Override
    public synchronized void close() {
        save();
    }
}
