package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class InMemoryAccountServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void localAuthenticatedIdentitiesResolveStablyAcrossRestarts() {
        PlatformIdentity java = new PlatformIdentity(PlatformIdentity.Platform.JAVA,
            "7c211088-ade7-4a38-bce0-1f0d06b1a64a");
        PlatformIdentity bedrock = new PlatformIdentity(PlatformIdentity.Platform.BEDROCK, "2535412345678901");

        var first = new InMemoryAccountService().resolveOrCreate(java).toCompletableFuture().join();
        var afterRestart = new InMemoryAccountService().resolveOrCreate(java).toCompletableFuture().join();
        var otherPlatform = new InMemoryAccountService().resolveOrCreate(bedrock).toCompletableFuture().join();

        assertEquals(first, afterRestart);
        assertNotEquals(first, otherPlatform);
    }

    @Test
    void linkedPlatformIdentityResolvesToCanonicalAccountAfterRestart() {
        UUID javaId = UUID.randomUUID();
        UUID floodgateId = UUID.randomUUID();
        PlatformIdentity java = new PlatformIdentity(PlatformIdentity.Platform.JAVA, javaId.toString());
        PlatformIdentity bedrock = new PlatformIdentity(PlatformIdentity.Platform.BEDROCK, "2535412345678901");
        PlayerDataStore firstStore = store();
        InMemoryAccountService first = new InMemoryAccountService(firstStore);
        var javaAccount = first.resolveOrCreate(java).toCompletableFuture().join();
        var oldBedrockAccount = first.resolveOrCreate(bedrock).toCompletableFuture().join();
        firstStore.bind(javaId, javaAccount, java, "JavaPlayer");
        firstStore.bind(floodgateId, oldBedrockAccount, bedrock, "BedrockPlayer");

        String code = first.createLinkCode(javaAccount, Duration.ofMinutes(5)).toCompletableFuture().join();
        assertEquals(net.nerdsmp.api.service.AccountService.LinkResult.LINKED,
            first.redeemLinkCode(bedrock, code, true).toCompletableFuture().join());

        InMemoryAccountService restarted = new InMemoryAccountService(store());
        assertEquals(javaAccount, restarted.resolveOrCreate(java).toCompletableFuture().join());
        assertEquals(javaAccount, restarted.resolveOrCreate(bedrock).toCompletableFuture().join());
    }

    private PlayerDataStore store() {
        return new PlayerDataStore(temporaryDirectory.resolve("NerdSMP").toFile(),
            Logger.getLogger("InMemoryAccountServiceTest"));
    }
}
