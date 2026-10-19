package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class HomesteadServiceTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private final AccountId owner = AccountId.random();
    private final AccountId friend = AccountId.random();
    private final UUID world = UUID.randomUUID();
    private ChunkKey chunk(int x, int z) { return new ChunkKey(world, x, z); }

    @Test void focusedRolesDoNotEscalateToOtherCapabilities() {
        assertTrue(ClaimRole.CONTAINER.allows(ClaimRole.CONTAINER));
        assertFalse(ClaimRole.CONTAINER.allows(ClaimRole.BUILD));
        assertFalse(ClaimRole.REDSTONE.allows(ClaimRole.CONTAINER));
        assertFalse(ClaimRole.BUILD.allows(ClaimRole.REDSTONE));
        assertTrue(ClaimRole.RESIDENT.allows(ClaimRole.BUILD));
        assertTrue(ClaimRole.RESIDENT.allows(ClaimRole.CONTAINER));
        assertTrue(ClaimRole.RESIDENT.allows(ClaimRole.REDSTONE));
        assertFalse(ClaimRole.RESIDENT.allows(ClaimRole.MANAGE));
        assertFalse(ClaimRole.MANAGE.allows(ClaimRole.OWNER));
    }

    @Test void membershipAndDefaultsApplyToFutureChunksButPlotOverridesWin() {
        var service = new InMemoryClaimService(4);
        service.createClaim(owner, chunk(0, 0));
        service.setTrustAll(owner, friend, ClaimRole.RESIDENT);
        service.setFlagAll(owner, owner, ClaimFlag.PUBLIC_HARVEST, true);
        service.createClaim(owner, chunk(1, 0));
        assertEquals(ClaimRole.RESIDENT, service.roleAt(friend, chunk(1, 0)).toCompletableFuture().join());
        assertTrue(service.flagAt(chunk(1, 0), ClaimFlag.PUBLIC_HARVEST).toCompletableFuture().join());
        service.setTrust(owner, chunk(1, 0), friend, ClaimRole.NONE);
        assertEquals(ClaimRole.NONE, service.roleAt(friend, chunk(1, 0)).toCompletableFuture().join());
        assertEquals(ClaimRole.RESIDENT, service.roleAt(friend, chunk(0, 0)).toCompletableFuture().join());
        service.setTrustAll(owner, friend, ClaimRole.NONE);
        assertEquals(ClaimRole.NONE, service.roleAt(friend, chunk(0, 0)).toCompletableFuture().join());
        assertFalse(service.find(chunk(1, 0)).toCompletableFuture().join().orElseThrow().members().containsKey(friend));
    }

    @Test void managersCanGrowAndSetRulesButCannotReleaseOrAssignOwnership() {
        var service = new InMemoryClaimService(4);
        service.createClaim(owner, chunk(0, 0));
        service.setTrustAll(owner, friend, ClaimRole.MANAGE);
        assertEquals(ClaimResult.CREATED, service.createClaimFor(friend, owner, chunk(1, 0)).toCompletableFuture().join());
        assertEquals(ClaimResult.FLAG_UPDATED, service.setFlag(friend, chunk(1, 0), ClaimFlag.PUBLIC_INTERACT, true).toCompletableFuture().join());
        assertEquals(ClaimResult.NOT_OWNER, service.deleteClaim(friend, chunk(1, 0)).toCompletableFuture().join());
        assertEquals(ClaimResult.NOT_OWNER, service.setTrust(friend, chunk(1, 0), AccountId.random(), ClaimRole.MANAGE).toCompletableFuture().join());
        assertEquals(ClaimResult.INVALID_ROLE, service.setTrustAll(owner, friend, ClaimRole.OWNER).toCompletableFuture().join());
        assertEquals(ClaimResult.INVALID_ROLE, service.setTrust(owner, chunk(0, 0), friend, ClaimRole.OWNER).toCompletableFuture().join());
    }

    @Test void localManagersCannotManageOtherPlotsOrGrowTheHomestead() {
        var service = new InMemoryClaimService(4);
        service.createClaim(owner, chunk(0, 0));
        service.createClaim(owner, chunk(1, 0));
        service.setTrust(owner, chunk(0, 0), friend, ClaimRole.MANAGE);
        assertEquals(ClaimResult.FLAG_UPDATED, service.setFlag(friend, chunk(0, 0), ClaimFlag.PUBLIC_HARVEST, true).toCompletableFuture().join());
        assertEquals(ClaimResult.NOT_OWNER, service.setFlag(friend, chunk(1, 0), ClaimFlag.PUBLIC_HARVEST, true).toCompletableFuture().join());
        assertEquals(ClaimResult.NOT_OWNER, service.createClaimFor(friend, owner, chunk(2, 0)).toCompletableFuture().join());
    }

    @Test void invitationsExpireAndCannotBeReplayed() {
        var service = new InMemoryClaimService(4);
        service.createClaim(owner, chunk(0, 0));
        assertEquals(ClaimResult.INVITED, service.invite(owner, owner, friend, NOW).toCompletableFuture().join());
        assertEquals(List.of(owner), service.invitations(friend, NOW).toCompletableFuture().join());
        assertEquals(ClaimResult.NO_INVITATION, service.acceptInvite(friend, owner, NOW.plusSeconds(300)).toCompletableFuture().join());
        service.invite(owner, owner, friend, NOW.plusSeconds(301));
        assertEquals(ClaimResult.JOINED, service.acceptInvite(friend, owner, NOW.plusSeconds(302)).toCompletableFuture().join());
        assertEquals(ClaimResult.NO_INVITATION, service.acceptInvite(friend, owner, NOW.plusSeconds(303)).toCompletableFuture().join());
        assertEquals(ClaimResult.LEFT, service.leaveHomestead(friend, owner).toCompletableFuture().join());
        assertEquals(ClaimRole.NONE, service.roleAt(friend, chunk(0, 0)).toCompletableFuture().join());
    }

    @Test void inviteLimitCountsPendingInvitesAndRejectsVisitorInvites() {
        var service = new InMemoryClaimService(4, 64, null, "test", 1, key -> false);
        service.createClaim(owner, chunk(0, 0));
        assertEquals(ClaimResult.NOT_OWNER, service.invite(friend, owner, AccountId.random(), NOW).toCompletableFuture().join());
        service.invite(owner, owner, friend, NOW);
        assertEquals(ClaimResult.MEMBER_LIMIT, service.invite(owner, owner, AccountId.random(), NOW).toCompletableFuture().join());
        assertEquals(ClaimResult.INVITED, service.invite(owner, owner, AccountId.random(), NOW.plusSeconds(301)).toCompletableFuture().join());
    }

    @Test void bridgeReleaseIsRejectedButOuterReleaseAndNewDimensionAreAllowed() {
        var service = new InMemoryClaimService(5);
        for (int i = 0; i < 3; i++) service.createClaim(owner, chunk(i, 0));
        assertEquals(ClaimResult.WOULD_SPLIT, service.deleteClaim(owner, chunk(1, 0)).toCompletableFuture().join());
        assertEquals(3, service.claimCount(owner).toCompletableFuture().join());
        assertEquals(ClaimResult.DELETED, service.deleteClaim(owner, chunk(2, 0)).toCompletableFuture().join());
        assertEquals(ClaimResult.CREATED, service.createClaim(owner, new ChunkKey(UUID.randomUUID(), 77, -42)).toCompletableFuture().join());
        assertEquals(ClaimResult.NOT_ADJACENT, service.createClaim(owner, chunk(77, -42)).toCompletableFuture().join());
    }

    @Test void reservesRejectClaimsAndFailClosedForAccessAndFlags() {
        var service = new InMemoryClaimService(4, 64, null, "test", 32, key -> key.equals(chunk(0, 0)));
        assertEquals(ClaimResult.RESERVED, service.createClaim(owner, chunk(0, 0)).toCompletableFuture().join());
        assertEquals(ClaimRole.NONE, service.roleAt(owner, chunk(0, 0)).toCompletableFuture().join());
        assertFalse(service.flagAt(chunk(0, 0), ClaimFlag.PVP).toCompletableFuture().join());
        assertEquals(ClaimRole.OWNER, service.roleAt(friend, chunk(1, 0)).toCompletableFuture().join());
    }

    @Test void metadataAndMembershipSurviveRestartWhileSeasonResetKeepsIdentity() {
        var data = data();
        var service = new InMemoryClaimService(4, 64, data, "test");
        service.createClaim(owner, chunk(0, 0));
        service.renameHomestead(owner, "Copper Hollow");
        service.setTrustAll(owner, friend, ClaimRole.RESIDENT);
        service.invite(owner, owner, AccountId.random(), NOW);
        service.setFlagAll(owner, owner, ClaimFlag.PVP, false);
        service.addCapacity(owner, 1);
        var restarted = new InMemoryClaimService(4, 64, new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), "test");
        assertEquals(owner, restarted.find(chunk(0, 0)).toCompletableFuture().join().orElseThrow().owner());
        assertEquals("Copper Hollow", restarted.homestead(owner).toCompletableFuture().join().name());
        assertEquals(ClaimRole.RESIDENT, restarted.roleAt(friend, chunk(0, 0)).toCompletableFuture().join());
        assertFalse(restarted.flagAt(chunk(0, 0), ClaimFlag.PVP).toCompletableFuture().join());
        assertEquals(5, restarted.capacity(owner).toCompletableFuture().join());
        var nextSeason = new InMemoryClaimService(4, 64, data, "next");
        assertTrue(nextSeason.find(chunk(0, 0)).toCompletableFuture().join().isEmpty());
        assertTrue(data.knownAccounts().contains(owner));
        assertEquals("Builder", data.read(owner, yaml -> yaml.getString("identity.last-known-name")));
    }

    @Test void legacyPlotsMigrateWithoutDroppingTrustOrNewDefaultCombatFlags() {
        var data = data();
        UUID plotId = UUID.randomUUID();
        data.update(owner, yaml -> {
            String base = "seasons.test.claims.chunks." + plotId;
            yaml.set("seasons.test.claims.capacity", 12);
            yaml.set(base + ".world", world.toString());
            yaml.set(base + ".x", -3);
            yaml.set(base + ".z", 9);
            yaml.set(base + ".name", "Old Farm");
            yaml.set(base + ".members." + friend, "CONTAINER");
            yaml.set(base + ".flags", List.of("PUBLIC_INTERACT"));
        });
        var service = new InMemoryClaimService(4, 8, data, "test");
        var view = service.find(chunk(-3, 9)).toCompletableFuture().join().orElseThrow();
        assertEquals(plotId, view.claimId());
        assertEquals("Old Farm", view.name());
        assertEquals(ClaimRole.CONTAINER, view.members().get(friend));
        assertTrue(view.flags().containsAll(Set.of(ClaimFlag.PUBLIC_INTERACT, ClaimFlag.HOSTILE_SPAWNING, ClaimFlag.PVP)));
        assertEquals(12, service.capacity(owner).toCompletableFuture().join());
        assertEquals(ClaimResult.MAX_CAPACITY_REACHED, service.addCapacity(owner, 1).toCompletableFuture().join());
        service.renameHomestead(owner, "Migrated");
        assertEquals(3, (int) data.read(owner, yaml -> yaml.getInt("seasons.test.claims.schema")));
        assertEquals("Old Farm", service.find(chunk(-3, 9)).toCompletableFuture().join().orElseThrow().name());
    }

    @Test void conflictingSavedOwnershipIsRejectedInsteadOfOverwritten() {
        var data = data();
        data.bind(friend.value(), friend, new PlatformIdentity(PlatformIdentity.Platform.JAVA, friend.toString()), "Friend");
        for (AccountId id : List.of(owner, friend)) data.update(id, yaml -> {
            String base = "seasons.test.claims.chunks." + UUID.randomUUID();
            yaml.set(base + ".world", world.toString());
            yaml.set(base + ".x", 0);
            yaml.set(base + ".z", 0);
        });
        assertThrows(IllegalStateException.class, () -> new InMemoryClaimService(4, 64, data, "test"));
    }

    @Test void failedClaimSaveLeavesTheProtectionSnapshotUnchanged() throws Exception {
        var data = data();
        var service = new InMemoryClaimService(4, 64, data, "test");
        service.createClaim(owner, chunk(0, 0));
        Path target = data.directory().toPath().resolve(owner + ".yml");
        Files.delete(target);
        Files.createDirectory(target);
        Files.createFile(target.resolve("prevent-replacement"));
        assertThrows(IllegalStateException.class, () -> service.deleteClaim(owner, chunk(0, 0)));
        assertTrue(service.find(chunk(0, 0)).toCompletableFuture().join().isPresent());
        assertEquals(1, service.claimCount(owner).toCompletableFuture().join());
        assertNotNull(data.read(owner, yaml -> yaml.getConfigurationSection("seasons.test.claims.chunks")));
    }

    @Test void namesArePlainTextAndViewsCannotBeMutated() {
        var service = new InMemoryClaimService(4);
        assertEquals(ClaimResult.INVALID_NAME, service.renameHomestead(owner, "<red>Bad").toCompletableFuture().join());
        service.createClaim(owner, chunk(0, 0));
        service.renameHomestead(owner, "  Copper   Hollow ");
        assertEquals("Copper Hollow", service.find(chunk(0, 0)).toCompletableFuture().join().orElseThrow().name());
        var view = service.homestead(owner).toCompletableFuture().join();
        assertThrows(UnsupportedOperationException.class, () -> view.members().put(friend, ClaimRole.OWNER));
        assertThrows(UnsupportedOperationException.class, () -> view.flags().clear());
    }

    private PlayerDataStore data() {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        data.bind(owner.value(), owner, new PlatformIdentity(PlatformIdentity.Platform.JAVA, owner.toString()), "Builder");
        return data;
    }
}
