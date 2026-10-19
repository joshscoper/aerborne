package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class HomesteadSettlementTest {
    @TempDir Path directory;
    private final AccountId owner = AccountId.random(), resident = AccountId.random(), manager = AccountId.random(), guest = AccountId.random();
    private final UUID world = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-26T12:00:00Z");
    private ChunkKey chunk(int x) { return new ChunkKey(world, x, 0); }
    private static <T> T value(CompletionStage<T> stage) { return stage.toCompletableFuture().join(); }
    private InMemoryClaimService home() {
        var service = new InMemoryClaimService(4);
        service.createClaim(owner, chunk(0));
        service.setTrustAll(owner, resident, ClaimRole.RESIDENT);
        service.setTrustAll(owner, manager, ClaimRole.MANAGE);
        return service;
    }
    private void recruiting(InMemoryClaimService service) {
        service.setListed(owner, owner, true);
        service.setRecruiting(owner, owner, true);
    }
    private PlayerDataStore data() {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        data.bind(owner.value(), owner, new PlatformIdentity(PlatformIdentity.Platform.JAVA, owner.toString()), "Founder");
        return data;
    }
    private ClaimView plot(InMemoryClaimService service) { return value(service.find(chunk(0))).orElseThrow(); }

    @Test void townDefaultsNeverOverwriteLocalRulesEvenWhenTheOldValueMatched() {
        var service = home();
        service.setFlag(owner, chunk(0), ClaimFlag.PUBLIC_CONTAINERS, false);
        service.setFlagAll(manager, owner, ClaimFlag.PUBLIC_CONTAINERS, true);
        assertFalse(value(service.flagAt(chunk(0), ClaimFlag.PUBLIC_CONTAINERS)));
        service.createClaim(owner, chunk(1));
        assertTrue(value(service.flagAt(chunk(1), ClaimFlag.PUBLIC_CONTAINERS)));
        service.resetFlag(owner, chunk(0), ClaimFlag.PUBLIC_CONTAINERS);
        assertTrue(value(service.flagAt(chunk(0), ClaimFlag.PUBLIC_CONTAINERS)));
        assertTrue(plot(service).flagOverrides().isEmpty());
    }

    @Test void roleChangesPreserveLocalDenialsUntilExplicitlyReset() {
        var service = home();
        service.setTrust(owner, chunk(0), resident, ClaimRole.NONE);
        service.setTrustAll(owner, resident, ClaimRole.MANAGE);
        assertEquals(ClaimRole.NONE, value(service.roleAt(resident, chunk(0))));
        service.resetTrust(owner, chunk(0), resident);
        assertEquals(ClaimRole.MANAGE, value(service.roleAt(resident, chunk(0))));
    }

    @Test void housingExcludesSharedManagersAndFutureResidentsButOwnerRetainsControl() {
        var service = home();
        service.assignSteward(owner, chunk(0), resident);
        assertEquals(ClaimRole.OWNER, value(service.roleAt(owner, chunk(0))));
        assertEquals(ClaimRole.RESIDENT, value(service.roleAt(resident, chunk(0))));
        assertEquals(ClaimRole.NONE, value(service.roleAt(manager, chunk(0))));
        service.setTrustAll(owner, guest, ClaimRole.RESIDENT);
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
        assertEquals(ClaimResult.NOT_OWNER, value(service.setFlag(manager, chunk(0), ClaimFlag.PVP, true)));
        assertEquals(ClaimResult.NOT_OWNER, value(service.setPlotType(manager, chunk(0), PlotType.COMMONS)));
    }

    @Test void stewardCanNameAndTrustGuestsButCannotPromoteOrRewriteTheTown() {
        var service = home();
        service.assignSteward(owner, chunk(0), resident);
        assertEquals(ClaimResult.NAME_UPDATED, value(service.setName(resident, chunk(0), "Copper Cottage")));
        assertEquals(ClaimResult.TRUST_UPDATED, value(service.setTrust(resident, chunk(0), guest, ClaimRole.CONTAINER)));
        assertEquals(ClaimRole.CONTAINER, value(service.roleAt(guest, chunk(0))));
        assertEquals(ClaimResult.INVALID_ROLE, value(service.setTrust(resident, chunk(0), guest, ClaimRole.MANAGE)));
        assertEquals(ClaimResult.INVALID_ROLE, value(service.setTrust(resident, chunk(0), resident, ClaimRole.MANAGE)));
        assertEquals(ClaimResult.NOT_OWNER, value(service.setFlag(resident, chunk(0), ClaimFlag.PVP, true)));
        assertEquals(ClaimResult.NOT_OWNER, value(service.deleteClaim(resident, chunk(0))));
        service.resetTrust(resident, chunk(0), guest);
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
    }

    @Test void privateHousingAlwaysMasksPublicAccessFlags() {
        var service = home();
        service.assignSteward(owner, chunk(0), resident);
        for (ClaimFlag flag : ClaimFlag.values()) if (flag.name().startsWith("PUBLIC_")) {
            service.setFlagAll(owner, owner, flag, true);
            service.setFlag(owner, chunk(0), flag, true);
            assertFalse(value(service.flagAt(chunk(0), flag)));
            assertFalse(plot(service).flags().contains(flag));
        }
    }

    @Test void stewardRemovalAndReassignmentClearGuestsWithoutOpeningTheHome() {
        var service = home();
        service.assignSteward(owner, chunk(0), resident);
        service.setTrust(resident, chunk(0), guest, ClaimRole.RESIDENT);
        service.assignSteward(owner, chunk(0), manager);
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
        assertEquals(ClaimRole.NONE, value(service.roleAt(resident, chunk(0))));
        service.setTrust(manager, chunk(0), guest, ClaimRole.RESIDENT);
        service.leaveHomestead(manager, owner);
        assertNull(plot(service).steward());
        assertEquals(PlotType.RESIDENTIAL, plot(service).type());
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
        assertEquals(ClaimRole.NONE, value(service.roleAt(resident, chunk(0))));
    }

    @Test void onlyOwnerAssignsExistingResidentsAndPrivateTransitionsClearOldLocalTrust() {
        var service = home();
        assertEquals(ClaimResult.NOT_OWNER, value(service.assignSteward(manager, chunk(0), resident)));
        assertEquals(ClaimResult.NOT_MEMBER, value(service.assignSteward(owner, chunk(0), guest)));
        service.setTrust(owner, chunk(0), guest, ClaimRole.RESIDENT);
        service.setPlotType(owner, chunk(0), PlotType.RESIDENTIAL);
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
        service.assignSteward(owner, chunk(0), resident);
        service.setPlotType(owner, chunk(0), PlotType.COMMONS);
        assertNull(plot(service).steward());
        assertEquals(ClaimRole.MANAGE, value(service.roleAt(manager, chunk(0))));
        assertEquals(ClaimResult.PLOT_UPDATED, value(service.setPlotType(manager, chunk(0), PlotType.FARM)));
    }

    @Test void directoryIsOptInAndApplicationDoesNotGrantAnyAccess() {
        var service = home();
        assertTrue(value(service.directory()).isEmpty());
        assertEquals(ClaimResult.NOT_RECRUITING, value(service.applyTo(guest, owner, now)));
        assertEquals(ClaimResult.NOT_OWNER, value(service.setListed(guest, owner, true)));
        recruiting(service);
        assertEquals(1, value(service.directory()).size());
        assertEquals(ClaimResult.APPLIED, value(service.applyTo(guest, owner, now)));
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
        assertTrue(value(service.settlementsFor(guest)).isEmpty());
        assertTrue(value(service.applications(guest, owner, now)).isEmpty());
        assertEquals(ClaimResult.NOT_OWNER, value(service.reviewApplication(guest, owner, guest, true, now)));
        assertEquals(ClaimResult.JOINED, value(service.reviewApplication(manager, owner, guest, true, now)));
        assertEquals(ClaimRole.RESIDENT, value(service.roleAt(guest, chunk(0))));
        assertEquals(1, value(service.settlementsFor(guest)).size());
    }

    @Test void applicationReplayDoesNotExtendExpiryAndExpiredApprovalFails() {
        var service = home(); recruiting(service);
        service.applyTo(guest, owner, now);
        var expiry = value(service.applications(owner, owner, now)).getFirst().expiresAt();
        service.applyTo(guest, owner, now.plusSeconds(60));
        assertEquals(expiry, value(service.applications(owner, owner, now)).getFirst().expiresAt());
        assertEquals(ClaimResult.NO_APPLICATION, value(service.reviewApplication(owner, owner, guest, true, expiry)));
        assertEquals(ClaimRole.NONE, value(service.roleAt(guest, chunk(0))));
    }

    @Test void applicationLimitsBoundBothPlayerAndTownRequests() {
        var service = new InMemoryClaimService(4);
        for (int n = 0; n < 6; n++) {
            AccountId host = AccountId.random();
            service.createClaim(host, chunk(n)); service.setListed(host, host, true); service.setRecruiting(host, host, true);
            assertEquals(n < 5 ? ClaimResult.APPLIED : ClaimResult.APPLICATION_LIMIT, value(service.applyTo(guest, host, now)));
        }
        var town = home(); recruiting(town);
        for (int n = 0; n < 33; n++) assertEquals(n < 32 ? ClaimResult.APPLIED : ClaimResult.APPLICATION_LIMIT,
            value(town.applyTo(AccountId.random(), owner, now)));
    }

    @Test void approvalRespectsSeatsReservedByInvitationsAndDeclineReleasesSeat() {
        var service = new InMemoryClaimService(4, 64, null, "test", 1, key -> false);
        service.createClaim(owner, chunk(0)); recruiting(service);
        service.invite(owner, owner, resident, now);
        service.applyTo(guest, owner, now);
        assertEquals(ClaimResult.MEMBER_LIMIT, value(service.reviewApplication(owner, owner, guest, true, now)));
        assertEquals(ClaimResult.NOT_OWNER, value(service.cancelInvite(guest, owner, resident)));
        assertEquals(ClaimResult.INVITATION_CANCELLED, value(service.cancelInvite(resident, owner, resident)));
        assertEquals(ClaimResult.JOINED, value(service.reviewApplication(owner, owner, guest, true, now)));
        assertEquals(ClaimResult.NO_APPLICATION, value(service.reviewApplication(owner, owner, guest, true, now)));
    }

    @Test void withdrawalAndLastPlotReleaseRemovePendingRecruitment() {
        var service = home(); recruiting(service);
        service.applyTo(guest, owner, now);
        assertEquals(ClaimResult.APPLICATION_REMOVED, value(service.withdrawApplication(guest, owner)));
        service.applyTo(guest, owner, now);
        service.invite(owner, owner, guest, now);
        service.deleteClaim(owner, chunk(0));
        assertTrue(value(service.directory()).isEmpty());
        assertTrue(value(service.applications(owner, owner, now)).isEmpty());
        assertTrue(value(service.invitations(guest, now)).isEmpty());
        service.createClaim(owner, chunk(0));
        assertFalse(value(service.homestead(owner)).listed());
    }

    @Test void bulletinAndHistoryAreBoundedAndPermissionChecked() {
        var service = home();
        assertEquals(ClaimResult.NOT_OWNER, value(service.setBulletin(guest, owner, "Hi")));
        for (String invalid : List.of("x".repeat(161), "bad\ntext", "<red>bad", "§cbad"))
            assertEquals(ClaimResult.INVALID_TEXT, value(service.setBulletin(manager, owner, invalid)));
        for (int n = 0; n < 105; n++) service.setBulletin(manager, owner, "Project " + n);
        assertEquals("Project 104", value(service.homestead(owner)).bulletin());
        var history = value(service.history(resident, owner));
        assertEquals(100, history.size());
        assertEquals(manager, history.getLast().actor());
        assertTrue(value(service.history(guest, owner)).isEmpty());
        assertThrows(UnsupportedOperationException.class, history::clear);
    }

    @Test void schemaTwoFlagsMigrateWithoutChangingProtection() {
        var data = data();
        UUID id = UUID.randomUUID();
        String path = "seasons.test.claims";
        data.update(owner, yaml -> {
            yaml.set(path + ".schema", 2); yaml.set(path + ".default-flags", List.of("HOSTILE_SPAWNING", "PUBLIC_INTERACT"));
            yaml.set(path + ".chunks." + id + ".world", world.toString()); yaml.set(path + ".chunks." + id + ".x", 0); yaml.set(path + ".chunks." + id + ".z", 0);
            yaml.set(path + ".chunks." + id + ".flags", List.of("HOSTILE_SPAWNING", "PUBLIC_HARVEST"));
        });
        var service = new InMemoryClaimService(4, 64, data, "test");
        assertEquals(id, plot(service).claimId());
        assertEquals(Set.of(ClaimFlag.HOSTILE_SPAWNING, ClaimFlag.PUBLIC_HARVEST), plot(service).flags());
        assertEquals(Map.of(ClaimFlag.PUBLIC_INTERACT, false, ClaimFlag.PUBLIC_HARVEST, true), plot(service).flagOverrides());
        assertFalse(value(service.homestead(owner)).listed());
    }

    @Test void privatePlotsRecruitmentNoticesAndHistorySurviveRestart() {
        var data = data();
        var service = new InMemoryClaimService(4, 64, data, "test");
        service.createClaim(owner, chunk(0)); service.setTrustAll(owner, resident, ClaimRole.RESIDENT);
        service.assignSteward(owner, chunk(0), resident); service.setTrust(resident, chunk(0), manager, ClaimRole.CONTAINER);
        recruiting(service); service.applyTo(guest, owner, now); service.setBulletin(owner, owner, "Build the bridge together");
        var before = plot(service);
        var freshData = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        var restarted = new InMemoryClaimService(4, 64, freshData, "test");
        assertEquals(before, plot(restarted));
        assertEquals("Build the bridge together", value(restarted.homestead(owner)).bulletin());
        assertEquals(1, value(restarted.applications(owner, owner, now)).size());
        assertEquals(value(service.history(owner, owner)), value(restarted.history(owner, owner)));
        assertTrue(value(new InMemoryClaimService(4, 64, freshData, "next").find(chunk(0))).isEmpty());
    }

    @Test void futureSchemaAndMalformedPrivacyDataFailClosed() {
        var data = data(); var service = new InMemoryClaimService(4, 64, data, "test");
        service.createClaim(owner, chunk(0)); service.setTrustAll(owner, resident, ClaimRole.RESIDENT); service.assignSteward(owner, chunk(0), resident);
        String path = "seasons.test.claims";
        String plot = path + ".chunks." + plot(service).claimId();
        for (Map.Entry<String, Object> bad : Map.<String, Object>of(path + ".schema", 99, plot + ".type", "invalid",
            plot + ".x", "broken", plot + ".members." + resident, "invalid", plot + ".steward", guest.toString(),
            plot + ".flag-overrides", "broken").entrySet()) {
            Object previous = data.read(owner, yaml -> yaml.get(bad.getKey()));
            data.update(owner, yaml -> yaml.set(bad.getKey(), bad.getValue()));
            assertThrows(RuntimeException.class, () -> new InMemoryClaimService(4, 64, data, "test"), bad.getKey());
            data.update(owner, yaml -> yaml.set(bad.getKey(), previous));
        }
        data.update(owner, yaml -> yaml.set(plot + ".type", null));
        assertThrows(RuntimeException.class, () -> new InMemoryClaimService(4, 64, data, "test"));
    }

    @Test void failedWritePublishesNeitherPrivateAssignmentNorItsAudit() throws Exception {
        var data = data(); var service = new InMemoryClaimService(4, 64, data, "test");
        service.createClaim(owner, chunk(0)); service.setTrustAll(owner, resident, ClaimRole.RESIDENT);
        var before = plot(service); var history = value(service.history(owner, owner));
        Path file = data.directory().toPath().resolve(owner + ".yml"); Files.delete(file); Files.createDirectory(file); Files.createFile(file.resolve("block-replace"));
        assertThrows(IllegalStateException.class, () -> service.assignSteward(owner, chunk(0), resident));
        assertEquals(before, plot(service));
        assertEquals(history, value(service.history(owner, owner)));
    }
}
