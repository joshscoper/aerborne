package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static net.nerdsmp.api.service.EconomyService.TransactionResult.Status.APPLIED;
import static net.nerdsmp.api.service.EconomyService.TransactionResult.Status.DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class InMemoryEconomyPersistenceTest {
    @TempDir Path directory;
    private static final EconomyService.TransactionReason REASON =
        new EconomyService.TransactionReason("test.persistence", "Single-account transaction recovery");

    @Test
    void balancesAndBothKindsOfReceiptSurviveRestart() {
        Fixture fixture = fixture();
        assertEquals(APPLIED, fixture.economy.credit(fixture.account, 500, REASON, "initial-credit")
            .toCompletableFuture().join().status());
        assertEquals(APPLIED, fixture.economy.debit(fixture.account, 125, REASON, "purchase-debit")
            .toCompletableFuture().join().status());

        InMemoryEconomyService restarted = restart();
        assertEquals(375L, restarted.balance(fixture.account).toCompletableFuture().join());
        assertEquals(DUPLICATE, restarted.credit(fixture.account, 500, REASON, "initial-credit")
            .toCompletableFuture().join().status());
        assertEquals(DUPLICATE, restarted.debit(fixture.account, 125, REASON, "purchase-debit")
            .toCompletableFuture().join().status());
        assertEquals(375L, restarted.balance(fixture.account).toCompletableFuture().join());
    }

    @Test
    void failedCreditDoesNotPublishMoneyOrReceiptAndTheSameKeyCanBeRetried() throws Exception {
        Fixture fixture = funded();
        Path blocker = Files.createDirectory(fixture.temporarySave);
        try {
            assertThrows(CompletionException.class, () -> fixture.economy.credit(fixture.account, 75, REASON, "sale-credit")
                .toCompletableFuture().join());
            assertUnchangedAfterFailure(fixture, "sale-credit");
        } finally {
            Files.delete(blocker);
        }

        assertEquals(APPLIED, fixture.economy.credit(fixture.account, 75, REASON, "sale-credit")
            .toCompletableFuture().join().status());
        InMemoryEconomyService restarted = restart();
        assertEquals(575L, restarted.balance(fixture.account).toCompletableFuture().join());
        assertEquals(DUPLICATE, restarted.credit(fixture.account, 75, REASON, "sale-credit")
            .toCompletableFuture().join().status());
    }

    @Test
    void failedDebitDoesNotPublishLossOrReceiptAndTheSameKeyCanBeRetried() throws Exception {
        Fixture fixture = funded();
        Path blocker = Files.createDirectory(fixture.temporarySave);
        try {
            assertThrows(CompletionException.class, () -> fixture.economy.debit(fixture.account, 125, REASON, "sale-debit")
                .toCompletableFuture().join());
            assertUnchangedAfterFailure(fixture, "sale-debit");
        } finally {
            Files.delete(blocker);
        }

        assertEquals(APPLIED, fixture.economy.debit(fixture.account, 125, REASON, "sale-debit")
            .toCompletableFuture().join().status());
        InMemoryEconomyService restarted = restart();
        assertEquals(375L, restarted.balance(fixture.account).toCompletableFuture().join());
        assertEquals(DUPLICATE, restarted.debit(fixture.account, 125, REASON, "sale-debit")
            .toCompletableFuture().join().status());
    }

    @Test
    void anOverflowDoesNotConsumeTheOperationKey() {
        Fixture fixture = fixture();
        fixture.economy.credit(fixture.account, Long.MAX_VALUE, REASON, "maximum").toCompletableFuture().join();
        assertThrows(CompletionException.class, () -> fixture.economy.credit(fixture.account, 1, REASON, "overflow")
            .toCompletableFuture().join());
        fixture.economy.debit(fixture.account, 1, REASON, "make-room").toCompletableFuture().join();

        assertEquals(APPLIED, fixture.economy.credit(fixture.account, 1, REASON, "overflow")
            .toCompletableFuture().join().status());
        assertEquals(Long.MAX_VALUE, restart().balance(fixture.account).toCompletableFuture().join());
    }

    @Test
    void aFailedInitialReadCanBeRetriedAfterTheDocumentIsRepaired() throws Exception {
        PlayerDataStore data = store();
        InMemoryEconomyService economy = new InMemoryEconomyService(data);
        AccountId account = AccountId.random();
        Path accountFile = directory.resolve("player_data").resolve(account + ".yml");
        Files.writeString(accountFile, "economy: [broken");
        assertThrows(IllegalStateException.class, () -> economy.balance(account));
        Files.writeString(accountFile, "economy:\n  balance: 123\n  applied-operations: [existing]\n");

        assertEquals(123L, economy.balance(account).toCompletableFuture().join());
        assertEquals(DUPLICATE, economy.credit(account, 123, REASON, "existing")
            .toCompletableFuture().join().status());
    }

    private void assertUnchangedAfterFailure(Fixture fixture, String failedKey) {
        assertEquals(500L, fixture.economy.balance(fixture.account).toCompletableFuture().join());
        assertEquals(500L, (long) fixture.data.read(fixture.account, yaml -> yaml.getLong("economy.balance")));
        assertEquals(List.of("initial-credit"), fixture.data.read(fixture.account,
            yaml -> yaml.getStringList("economy.applied-operations")));
        assertFalse((boolean) fixture.data.read(fixture.account,
            yaml -> yaml.getStringList("economy.applied-operations").contains(failedKey)));
        assertEquals("keep", fixture.data.read(fixture.account, yaml -> yaml.getString("unrelated.value")));
        assertEquals(500L, restart().balance(fixture.account).toCompletableFuture().join());
    }

    private Fixture funded() {
        Fixture fixture = fixture();
        fixture.economy.credit(fixture.account, 500, REASON, "initial-credit").toCompletableFuture().join();
        return fixture;
    }

    private Fixture fixture() {
        PlayerDataStore data = store();
        UUID player = UUID.randomUUID();
        AccountId account = AccountId.random();
        data.bind(player, account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, player.toString()), "TestPlayer");
        data.update(account, yaml -> yaml.set("unrelated.value", "keep"));
        return new Fixture(data, new InMemoryEconomyService(data), account,
            data.directory().toPath().resolve(player + ".yml.tmp"));
    }

    private InMemoryEconomyService restart() {
        return new InMemoryEconomyService(store());
    }

    private PlayerDataStore store() {
        return new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
    }

    private record Fixture(PlayerDataStore data, InMemoryEconomyService economy, AccountId account, Path temporarySave) {
    }
}
