package net.nerdsmp.paper.activity;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Durable receipts let a partially delivered mastery reward finish after a restart. */
final class CollectionRewardDelivery {
    private final PlayerDataStore data;
    private final ProgressionService progression;
    private final EconomyService economy;

    CollectionRewardDelivery(PlayerDataStore data, ProgressionService progression, EconomyService economy) {
        this.data = data;
        this.progression = progression;
        this.economy = economy;
    }

    boolean delivered(AccountId account, String entryId) {
        return data.read(account, yaml -> yaml.getBoolean(receipt(entryId) + ".delivered"));
    }

    CompletionStage<Boolean> deliver(AccountId account, String entryId, String name, Skill skill, long xp, long marks) {
        if (xp <= 0 || marks <= 0 || !entryId.matches("[a-z0-9_]{1,128}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid collection reward"));
        }
        if (delivered(account, entryId)) {
            return CompletableFuture.completedFuture(false);
        }
        // Keep the original keys so repairing pre-receipt rewards cannot grant them twice.
        String key = "collection-mastery:" + account + ":" + entryId;
        String receipt = receipt(entryId);
        CompletionStage<Void> experience = data.read(account, yaml -> yaml.getBoolean(receipt + ".xp-delivered"))
            ? CompletableFuture.completedFuture(null)
            : progression.awardExperience(account, skill, xp, key + ":xp").thenAccept(result -> {
                if (result == null) {
                    throw new IllegalStateException("Missing collection XP receipt");
                }
                data.update(account, yaml -> yaml.set(receipt + ".xp-delivered", true));
            });
        return experience.thenCompose(ignored -> {
            if (data.read(account, yaml -> yaml.getBoolean(receipt + ".marks-delivered"))) {
                return CompletableFuture.completedFuture(null);
            }
            return economy.credit(account, marks,
                new EconomyService.TransactionReason("collection.mastery", name + " mastery"),
                key + ":marks").thenAccept(result -> {
                    if (result == null || (result.status() != EconomyService.TransactionResult.Status.APPLIED
                        && result.status() != EconomyService.TransactionResult.Status.DUPLICATE)) {
                        throw new IllegalStateException("Collection Marks reward was rejected");
                    }
                    data.update(account, yaml -> yaml.set(receipt + ".marks-delivered", true));
                });
        }).thenApply(ignored -> {
            data.update(account, yaml -> yaml.set(receipt + ".delivered", true));
            return true;
        });
    }

    private static String receipt(String entryId) {
        return "collections.entries." + entryId + ".mastery-reward";
    }
}
