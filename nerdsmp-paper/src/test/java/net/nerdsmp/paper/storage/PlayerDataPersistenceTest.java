package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.paper.service.InMemoryClaimService;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import net.nerdsmp.paper.service.InMemoryEntitlementService;
import net.nerdsmp.paper.service.InMemoryProgressionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayerDataPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void localPlayerStateSurvivesAFullServiceRestart() {
        File pluginDirectory = temporaryDirectory.resolve("NerdSMP").toFile();
        UUID playerId = UUID.randomUUID();
        AccountId accountId = new AccountId(playerId);
        UUID worldId = UUID.randomUUID();
        ChunkKey homestead = new ChunkKey(worldId, 3, -7);

        PlayerDataStore firstStore = store(pluginDirectory);
        firstStore.bind(
            playerId,
            accountId,
            new PlatformIdentity(PlatformIdentity.Platform.JAVA, playerId.toString()),
            "Wayfinder"
        );
        InMemoryEconomyService firstEconomy = new InMemoryEconomyService(firstStore);
        InMemoryProgressionService firstProgression = new InMemoryProgressionService(firstStore, "beta");
        InMemoryClaimService firstClaims = new InMemoryClaimService(4, 64, firstStore, "beta");
        InMemoryEntitlementService firstEntitlements = new InMemoryEntitlementService(firstStore);

        EconomyService.TransactionReason reason = new EconomyService.TransactionReason("test.reward", "Restart test");
        assertEquals(EconomyService.TransactionResult.Status.APPLIED,
            firstEconomy.credit(accountId, 275L, reason, "reward:one").toCompletableFuture().join().status());
        assertTrue(firstProgression.awardExperience(accountId, Skill.MINING, 2_500L, "ore:one")
            .toCompletableFuture().join().awarded());
        assertTrue(firstProgression.awardExperience(accountId, Skill.BUILDING, 7_500L, "building:one")
            .toCompletableFuture().join().awarded());
        assertEquals(ClaimService.ClaimResult.CREATED,
            firstClaims.createClaim(accountId, homestead).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.NAME_UPDATED,
            firstClaims.setName(accountId, homestead, "Copper Hollow").toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.CAPACITY_UPDATED,
            firstClaims.addCapacity(accountId, 2).toCompletableFuture().join());
        assertEquals(EntitlementService.GrantResult.APPLIED,
            firstEntitlements.grant(
                accountId,
                "supporter",
                "tebex:test:one",
                Instant.now().plus(30, ChronoUnit.DAYS)
            ).toCompletableFuture().join());

        Path playerFile = firstStore.directory().toPath().resolve(playerId.toString() + ".yml");
        assertTrue(playerFile.toFile().isFile());

        PlayerDataStore secondStore = store(pluginDirectory);
        InMemoryEconomyService secondEconomy = new InMemoryEconomyService(secondStore);
        InMemoryProgressionService secondProgression = new InMemoryProgressionService(secondStore, "beta");
        InMemoryClaimService secondClaims = new InMemoryClaimService(4, 64, secondStore, "beta");
        InMemoryEntitlementService secondEntitlements = new InMemoryEntitlementService(secondStore);

        assertEquals(275L, secondEconomy.balance(accountId).toCompletableFuture().join());
        assertEquals(EconomyService.TransactionResult.Status.DUPLICATE,
            secondEconomy.credit(accountId, 275L, reason, "reward:one").toCompletableFuture().join().status());
        assertEquals(2_500L, secondProgression.snapshot(accountId).toCompletableFuture().join()
            .skills().get(Skill.MINING).experience());
        assertEquals(7_500L, secondProgression.snapshot(accountId).toCompletableFuture().join()
            .skills().get(Skill.BUILDING).experience());
        assertFalse(secondProgression.awardExperience(accountId, Skill.MINING, 2_500L, "ore:one")
            .toCompletableFuture().join().awarded());
        assertEquals("Copper Hollow", secondClaims.find(homestead).toCompletableFuture().join()
            .orElseThrow().name());
        assertEquals(6, secondClaims.capacity(accountId).toCompletableFuture().join());
        assertEquals(1, secondEntitlements.activeEntitlements(accountId, Instant.now())
            .toCompletableFuture().join().size());

        assertEquals(EntitlementService.GrantResult.REVOKED,
            secondEntitlements.revoke("tebex:test:one", "refund").toCompletableFuture().join());
        InMemoryEntitlementService thirdEntitlements = new InMemoryEntitlementService(store(pluginDirectory));
        assertTrue(thirdEntitlements.activeEntitlements(accountId, Instant.now())
            .toCompletableFuture().join().isEmpty());
    }

    private PlayerDataStore store(File pluginDirectory) {
        return new PlayerDataStore(pluginDirectory, Logger.getLogger("PlayerDataPersistenceTest"));
    }
}
