package net.nerdsmp.paper.activity;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import net.nerdsmp.paper.service.InMemoryProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CollectionRewardDeliveryTest {
    @TempDir
    Path directory;

    private final AccountId account = new AccountId(UUID.randomUUID());
    private static final String ENTRY = "fishing_river_sprat";

    @Test
    void completedMasteryCannotPayAgainAfterFullRestart() {
        PlayerDataStore first = store();
        var progression = new InMemoryProgressionService(first, "beta");
        var economy = new InMemoryEconomyService(first);
        assertTrue(new CollectionRewardDelivery(first, progression, economy)
            .deliver(account, ENTRY, "River Sprat", Skill.FISHING, 150, 50).toCompletableFuture().join());

        PlayerDataStore restarted = store();
        var restartedProgression = new InMemoryProgressionService(restarted, "beta");
        var restartedEconomy = new InMemoryEconomyService(restarted);
        assertFalse(new CollectionRewardDelivery(restarted, restartedProgression, restartedEconomy)
            .deliver(account, ENTRY, "River Sprat", Skill.FISHING, 150, 50).toCompletableFuture().join());
        assertEquals(150, restartedProgression.snapshot(account).toCompletableFuture().join()
            .skills().get(Skill.FISHING).experience());
        assertEquals(50, restartedEconomy.balance(account).toCompletableFuture().join());
    }

    @Test
    void lostCreditResponseRetriesWithoutDuplicatingEitherReward() {
        PlayerDataStore first = store();
        var progression = new InMemoryProgressionService(first, "beta");
        var economy = new InMemoryEconomyService(first);
        AtomicBoolean loseResponse = new AtomicBoolean(true);
        EconomyService interrupted = (EconomyService) Proxy.newProxyInstance(EconomyService.class.getClassLoader(),
            new Class<?>[]{EconomyService.class}, (proxy, method, arguments) -> {
                Object result = method.invoke(economy, arguments);
                if (method.getName().equals("credit") && loseResponse.getAndSet(false)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Connection lost after commit"));
                }
                return result;
            });
        var delivery = new CollectionRewardDelivery(first, progression, interrupted);
        assertThrows(CompletionException.class, () -> delivery
            .deliver(account, ENTRY, "River Sprat", Skill.FISHING, 150, 50).toCompletableFuture().join());
        assertFalse(delivery.delivered(account, ENTRY));

        PlayerDataStore restarted = store();
        var restartedProgression = new InMemoryProgressionService(restarted, "beta");
        var restartedEconomy = new InMemoryEconomyService(restarted);
        var recovered = new CollectionRewardDelivery(restarted, restartedProgression, restartedEconomy);
        assertTrue(recovered.deliver(account, ENTRY, "River Sprat", Skill.FISHING, 150, 50)
            .toCompletableFuture().join());
        assertTrue(recovered.delivered(account, ENTRY));
        assertEquals(150, restartedProgression.snapshot(account).toCompletableFuture().join()
            .skills().get(Skill.FISHING).experience());
        assertEquals(50, restartedEconomy.balance(account).toCompletableFuture().join());
    }

    @Test
    void legacyMasteryUsesItsOriginalLedgerKeys() {
        PlayerDataStore data = store();
        var progression = new InMemoryProgressionService(data, "beta");
        var economy = new InMemoryEconomyService(data);
        String key = "collection-mastery:" + account + ":" + ENTRY;
        progression.awardExperience(account, Skill.FISHING, 180, key + ":xp").toCompletableFuture().join();
        economy.credit(account, 50, new EconomyService.TransactionReason("collection.mastery", "Old reward"),
            key + ":marks").toCompletableFuture().join();

        assertTrue(new CollectionRewardDelivery(data, progression, economy)
            .deliver(account, ENTRY, "River Sprat", Skill.FISHING, 150, 50).toCompletableFuture().join());
        assertEquals(180, progression.snapshot(account).toCompletableFuture().join()
            .skills().get(Skill.FISHING).experience());
        assertEquals(50, economy.balance(account).toCompletableFuture().join());
    }

    private PlayerDataStore store() {
        return new PlayerDataStore(directory.toFile(), Logger.getLogger("collection-reward-test"));
    }
}
