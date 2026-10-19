package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.service.ClaimService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InMemoryClaimServiceTest {
    @Test
    void capacityAndTrustAreEnforced() {
        InMemoryClaimService service = new InMemoryClaimService(1);
        AccountId owner = AccountId.random();
        AccountId friend = AccountId.random();
        UUID world = UUID.randomUUID();
        ChunkKey first = new ChunkKey(world, 0, 0);
        ChunkKey second = new ChunkKey(world, 1, 0);

        assertEquals(ClaimService.ClaimResult.CREATED,
            service.createClaim(owner, first).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.CAPACITY_REACHED,
            service.createClaim(owner, second).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.TRUST_UPDATED,
            service.setTrust(owner, first, friend, ClaimRole.CONTAINER).toCompletableFuture().join());
        assertEquals(ClaimRole.CONTAINER, service.roleAt(friend, first).toCompletableFuture().join());
    }

    @Test
    void expansionsStayConnectedAndRespectTheHardCapacityLimit() {
        InMemoryClaimService service = new InMemoryClaimService(2, 3);
        AccountId owner = AccountId.random();
        UUID world = UUID.randomUUID();
        ChunkKey origin = new ChunkKey(world, 0, 0);

        assertEquals(ClaimService.ClaimResult.CREATED,
            service.createClaim(owner, origin).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.NOT_ADJACENT,
            service.createClaim(owner, new ChunkKey(world, 3, 0)).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.CREATED,
            service.createClaim(owner, new ChunkKey(world, 1, 0)).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.CAPACITY_UPDATED,
            service.addCapacity(owner, 1).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.MAX_CAPACITY_REACHED,
            service.addCapacity(owner, 1).toCompletableFuture().join());
    }

    @Test
    void metadataGlobalTrustAndFlagsAreCopiedIntoViews() {
        InMemoryClaimService service = new InMemoryClaimService(2);
        AccountId owner = AccountId.random();
        AccountId friend = AccountId.random();
        ChunkKey chunk = new ChunkKey(UUID.randomUUID(), 0, 0);
        service.createClaim(owner, chunk).toCompletableFuture().join();

        assertEquals(ClaimService.ClaimResult.NAME_UPDATED,
            service.setName(owner, chunk, "Copper Hollow").toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.TRUST_UPDATED,
            service.setTrustAll(owner, friend, ClaimRole.BUILD).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.FLAG_UPDATED,
            service.setFlag(owner, chunk, ClaimFlag.PUBLIC_INTERACT, true).toCompletableFuture().join());
        ClaimService.ClaimView view = service.find(chunk).toCompletableFuture().join().orElseThrow();
        assertEquals("Copper Hollow", view.name());
        assertEquals(ClaimRole.BUILD, view.members().get(friend));
        assertEquals(true, view.flags().contains(ClaimFlag.PUBLIC_INTERACT));
    }
}
