package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.PartyService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InMemoryPartyServiceTest {
    @Test void invitationsExpireAtTheExactDeadlineAndCannotBeExtendedOrReplaced() {
        var service = new InMemoryPartyService(3, Duration.ofSeconds(10));
        var leader = AccountId.random(); var otherLeader = AccountId.random(); var member = AccountId.random(); var now = Instant.EPOCH;
        assertEquals(PartyService.PartyResult.INVITED, service.invite(leader, member, now).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.INVITE_PENDING, service.invite(leader, member, now.plusSeconds(9)).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.INVITE_PENDING, service.invite(otherLeader, member, now.plusSeconds(9)).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.INVITE_EXPIRED, service.accept(member, now.plusSeconds(10)).toCompletableFuture().join());
        assertTrue(service.partyOf(otherLeader).toCompletableFuture().join().isEmpty());
    }
    @Test void pendingInvitesReserveCapacityAndDeclineReleasesIt() {
        var service = new InMemoryPartyService(2, Duration.ofSeconds(10)); var leader = AccountId.random(); var a = AccountId.random(); var b = AccountId.random();
        service.invite(leader, a, Instant.EPOCH).toCompletableFuture().join();
        assertEquals(PartyService.PartyResult.PARTY_FULL, service.invite(leader, b, Instant.EPOCH).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.DECLINED, service.decline(a).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.INVITED, service.invite(leader, b, Instant.EPOCH).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.NO_INVITE, service.accept(a, Instant.EPOCH).toCompletableFuture().join());
    }
    @Test void leadershipChangesRevokeUnacceptedInvitations() {
        var service = new InMemoryPartyService(3, Duration.ofSeconds(10)); var leader = AccountId.random(); var a = AccountId.random(); var b = AccountId.random();
        service.invite(leader, a, Instant.EPOCH); service.accept(a, Instant.EPOCH); service.invite(leader, b, Instant.EPOCH);
        service.promote(leader, a);
        assertTrue(service.pendingInvite(b, Instant.EPOCH).toCompletableFuture().join().isEmpty());
        assertEquals(PartyService.PartyResult.NO_INVITE, service.accept(b, Instant.EPOCH).toCompletableFuture().join());
    }
    @Test void staleConsentCannotAcceptAnotherPartysInvitation() {
        var service = new InMemoryPartyService(3, Duration.ofSeconds(10)); var leader = AccountId.random(); var other = AccountId.random(); var target = AccountId.random();
        service.invite(leader, target, Instant.EPOCH);
        var original = service.pendingInvite(target, Instant.EPOCH).toCompletableFuture().join().orElseThrow();
        service.decline(target); service.invite(other, target, Instant.EPOCH);
        assertEquals(PartyService.PartyResult.NO_INVITE, service.accept(target, original.partyId(), Instant.EPOCH).toCompletableFuture().join());
        assertEquals(other, service.pendingInvite(target, Instant.EPOCH).toCompletableFuture().join().orElseThrow().leader());
    }
    @Test void departureOfTheOnlyMemberRevokesInvitations() {
        var service = new InMemoryPartyService(2, Duration.ofSeconds(10)); var leader = AccountId.random(); var target = AccountId.random();
        service.invite(leader, target, Instant.EPOCH); service.leave(leader);
        assertTrue(service.pendingInvite(target, Instant.EPOCH).toCompletableFuture().join().isEmpty());
    }
    @Test
    void invitesMembershipLeadershipAndFriendlyGroupingAreConsistent() {
        InMemoryPartyService service = new InMemoryPartyService(3, Duration.ofSeconds(30));
        AccountId leader = AccountId.random();
        AccountId member = AccountId.random();
        Instant now = Instant.parse("2026-09-01T12:00:00Z");

        assertEquals(PartyService.PartyResult.INVITED,
            service.invite(leader, member, now).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.JOINED,
            service.accept(member, now.plusSeconds(5)).toCompletableFuture().join());
        assertTrue(service.sameParty(leader, member).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.LEADER_MUST_PROMOTE_OR_DISBAND,
            service.leave(leader).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.PROMOTED,
            service.promote(leader, member).toCompletableFuture().join());
        assertEquals(PartyService.PartyResult.LEFT,
            service.leave(leader).toCompletableFuture().join());
    }

    @Test
    void expiredInvitesAndPartyCapacityAreEnforced() {
        InMemoryPartyService service = new InMemoryPartyService(2, Duration.ofSeconds(10));
        AccountId leader = AccountId.random();
        AccountId first = AccountId.random();
        AccountId second = AccountId.random();
        Instant now = Instant.parse("2026-09-01T12:00:00Z");

        service.invite(leader, first, now).toCompletableFuture().join();
        assertEquals(PartyService.PartyResult.INVITE_EXPIRED,
            service.accept(first, now.plusSeconds(11)).toCompletableFuture().join());
        service.invite(leader, first, now.plusSeconds(12)).toCompletableFuture().join();
        service.accept(first, now.plusSeconds(13)).toCompletableFuture().join();
        assertEquals(PartyService.PartyResult.PARTY_FULL,
            service.invite(leader, second, now.plusSeconds(14)).toCompletableFuture().join());
    }
}
