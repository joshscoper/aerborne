package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.paper.service.InMemoryClaimService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class IslandTerritoriesTest {
    @TempDir Path directory;
    private final AccountId owner = AccountId.random(), member = AccountId.random(), guest = AccountId.random();
    private final UUID world = UUID.randomUUID();
    private final Instant now = Instant.now();
    private IslandService islands() { return new IslandService(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), "test"); }
    private InMemoryClaimService claims(IslandService islands) {
        var claims = new InMemoryClaimService(4, 64, null, "test", 32, chunk -> true);
        claims.setTerritoryOverlay(new IslandTerritories(world, islands)); return claims;
    }
    private ChunkKey chunk(int x) { return new ChunkKey(world, x, 0); }
    @Test void unassignedLandAndSpaceOutsideIslandStayReserved() {
        var islands = islands(); var claims = claims(islands);
        assertTrue(claims.isReserved(chunk(0))); islands.create(owner);
        assertFalse(claims.isReserved(chunk(0))); assertTrue(claims.isReserved(chunk(2)));
        assertTrue(claims.isReserved(chunk(32))); assertTrue(claims.isReserved(new ChunkKey(UUID.randomUUID(), 0, 0)));
    }
    @Test void membershipMapsToExistingProtectionRolesAndRevokesImmediately() {
        var islands = islands(); islands.create(owner); islands.visitors(owner, true); var claims = claims(islands);
        assertEquals(ClaimRole.OWNER, claims.roleAt(owner, chunk(0)).toCompletableFuture().join());
        assertEquals(ClaimRole.NONE, claims.roleAt(guest, chunk(0)).toCompletableFuture().join());
        islands.invite(owner, member, now); assertEquals(ClaimRole.NONE, claims.roleAt(member, chunk(0)).toCompletableFuture().join());
        islands.accept(member, owner, now); assertEquals(ClaimRole.RESIDENT, claims.roleAt(member, chunk(0)).toCompletableFuture().join());
        islands.remove(owner, owner, member); assertEquals(ClaimRole.NONE, claims.roleAt(member, chunk(0)).toCompletableFuture().join());
    }
    @Test void manualClaimsCannotOverrideVirtualTerritories() {
        var islands = islands(); islands.create(owner); var claims = claims(islands);
        assertEquals(ClaimService.ClaimResult.RESERVED, claims.createClaim(guest, chunk(0)).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.NOT_CLAIMED, claims.deleteClaim(owner, chunk(0)).toCompletableFuture().join());
        assertEquals(ClaimService.ClaimResult.NOT_CLAIMED, claims.setTrust(owner, chunk(0), guest, ClaimRole.OWNER).toCompletableFuture().join());
    }
    @Test void mobsSpawnButDestructiveAndPublicFlagsStayOff() {
        var islands = islands(); islands.create(owner); var claims = claims(islands);
        for (ClaimFlag flag : ClaimFlag.values()) assertEquals(flag == ClaimFlag.HOSTILE_SPAWNING, claims.flagAt(chunk(0), flag).toCompletableFuture().join());
        assertFalse(claims.flagAt(chunk(5), ClaimFlag.HOSTILE_SPAWNING).toCompletableFuture().join());
    }
    @Test void immutableSnapshotsExposeSameIslandIdentityAcrossChunks() {
        var islands = islands(); islands.create(owner); var overlay = new IslandTerritories(world, islands);
        var a = overlay.find(chunk(0)).orElseThrow(); var b = overlay.find(chunk(1)).orElseThrow();
        assertEquals(a.claimId(), b.claimId()); assertNotEquals(a.chunk(), b.chunk());
        assertTrue(overlay.find(new ChunkKey(UUID.randomUUID(), 0, 0)).isEmpty());
    }
    @Test void hotProtectionReadsReuseViewsAndRevocationInvalidatesThemImmediately() {
        var islands = islands(); islands.create(owner); var overlay = new IslandTerritories(world, islands);
        var initial = overlay.find(chunk(0)).orElseThrow();
        for (int n = 0; n < 10000; n++) assertSame(initial, overlay.find(chunk(0)).orElseThrow());
        islands.invite(owner, member, now); islands.accept(member, owner, now);
        var joined = overlay.find(chunk(0)).orElseThrow(); assertNotSame(initial, joined);
        assertEquals(ClaimRole.RESIDENT, joined.members().get(member));
        islands.remove(owner, owner, member);
        assertFalse(overlay.find(chunk(0)).orElseThrow().members().containsKey(member));
        assertEquals(ClaimRole.RESIDENT, joined.members().get(member)); // Old snapshots remain immutable.
    }
    @Test void protectionCacheEvictsOldEntriesAtItsBudget() {
        var islands = islands(); islands.create(owner); var overlay = new IslandTerritories(world, islands, 4);
        var first = overlay.find(new ChunkKey(world, -1, -1)).orElseThrow();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) overlay.find(new ChunkKey(world, x, z));
        assertEquals(4, overlay.cachedChunks());
        assertNotSame(first, overlay.find(new ChunkKey(world, -1, -1)).orElseThrow());
        assertEquals(4, overlay.cachedChunks());
    }
}
