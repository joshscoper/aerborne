package net.nerdsmp.paper.party;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.PartyService.PartyResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PartyRosterTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Duration HANDOVER = Duration.ofSeconds(60);
    private static final Duration REMOVAL = Duration.ofMinutes(15);

    private static <T> T join(java.util.concurrent.CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static PartyRoster.Details details(PartyRoster roster, AccountId member) {
        return roster.details(member).orElseThrow();
    }

    @Test void invitationsExpireAtTheExactDeadline() {
        var roster = new PartyRoster(3, Duration.ofSeconds(10));
        var leader = AccountId.random(); var member = AccountId.random();
        assertEquals(PartyResult.INVITED, join(roster.invite(leader, member, NOW)));
        assertEquals(PartyResult.INVITE_PENDING, join(roster.invite(leader, member, NOW.plusSeconds(9))));
        assertEquals(PartyResult.INVITE_EXPIRED, join(roster.accept(member, NOW.plusSeconds(10))));
        assertFalse(join(roster.sameParty(leader, member)));
    }

    @Test void aPlayerCanHoldInvitesFromSeveralPartiesUpToTheCap() {
        var roster = new PartyRoster(5, Duration.ofSeconds(30), 2);
        var target = AccountId.random(); var first = AccountId.random(); var second = AccountId.random(); var third = AccountId.random();
        assertEquals(PartyResult.INVITED, join(roster.invite(first, target, NOW)));
        assertEquals(PartyResult.INVITED, join(roster.invite(second, target, NOW.plusSeconds(1))));
        assertEquals(PartyResult.TOO_MANY_INVITES, join(roster.invite(third, target, NOW.plusSeconds(2))));
        assertTrue(roster.details(third).isEmpty(), "A refused invite doesn't leave an empty party behind");
        assertEquals(2, roster.invitesFor(target, NOW.plusSeconds(3)).size());
        assertEquals(second, roster.invitesFor(target, NOW.plusSeconds(3)).getFirst().leader(), "Newest first");

        var chosen = roster.invitesFor(target, NOW).getLast().partyId();
        assertEquals(PartyResult.JOINED, join(roster.accept(target, chosen, NOW.plusSeconds(4))));
        assertTrue(join(roster.sameParty(first, target)));
        assertTrue(roster.invitesFor(target, NOW.plusSeconds(4)).isEmpty(), "Joining withdraws the other invitations");
    }

    @Test void pendingInvitesReserveCapacityAndDeclineReleasesIt() {
        var roster = new PartyRoster(2, Duration.ofSeconds(10));
        var leader = AccountId.random(); var a = AccountId.random(); var b = AccountId.random();
        join(roster.invite(leader, a, NOW));
        assertEquals(PartyResult.PARTY_FULL, join(roster.invite(leader, b, NOW)));
        assertEquals(PartyResult.DECLINED, join(roster.decline(a)));
        assertEquals(PartyResult.INVITED, join(roster.invite(leader, b, NOW)));
        assertEquals(PartyResult.NO_INVITE, join(roster.accept(a, NOW)));
    }

    @Test void decliningOneInviteKeepsTheOthers() {
        var roster = new PartyRoster(5, Duration.ofSeconds(30));
        var target = AccountId.random(); var first = AccountId.random(); var second = AccountId.random();
        join(roster.invite(first, target, NOW)); join(roster.invite(second, target, NOW));
        var firstParty = details(roster, first).id();
        assertEquals(PartyResult.DECLINED, roster.decline(target, firstParty));
        assertEquals(PartyResult.NO_INVITE, roster.decline(target, firstParty));
        assertEquals(List.of(second), roster.invitesFor(target, NOW).stream().map(PartyRoster.PendingInvite::leader).toList());
    }

    @Test void leadershipChangesRevokeUnacceptedInvitations() {
        var roster = new PartyRoster(3, Duration.ofSeconds(10));
        var leader = AccountId.random(); var a = AccountId.random(); var b = AccountId.random();
        join(roster.invite(leader, a, NOW)); join(roster.accept(a, NOW)); join(roster.invite(leader, b, NOW));
        assertEquals(PartyResult.PROMOTED, join(roster.promote(leader, a)));
        assertTrue(join(roster.pendingInvite(b, NOW)).isEmpty());
        assertEquals(PartyResult.NO_INVITE, join(roster.accept(b, NOW)));
    }

    @Test void staleConsentCannotAcceptAnotherPartysInvitation() {
        var roster = new PartyRoster(3, Duration.ofSeconds(10));
        var leader = AccountId.random(); var other = AccountId.random(); var target = AccountId.random();
        join(roster.invite(leader, target, NOW));
        var original = join(roster.pendingInvite(target, NOW)).orElseThrow();
        join(roster.decline(target)); join(roster.invite(other, target, NOW));
        assertEquals(PartyResult.NO_INVITE, join(roster.accept(target, original.partyId(), NOW)));
        assertEquals(other, join(roster.pendingInvite(target, NOW)).orElseThrow().leader());
    }

    @Test void departureOfTheOnlyMemberDisbandsAndRevokesInvitations() {
        var roster = new PartyRoster(2, Duration.ofSeconds(10));
        var leader = AccountId.random(); var target = AccountId.random();
        join(roster.invite(leader, target, NOW));
        assertEquals(PartyResult.LEFT, join(roster.leave(leader)));
        assertTrue(join(roster.pendingInvite(target, NOW)).isEmpty());
        assertTrue(roster.details(leader).isEmpty());
    }

    @Test void aLeavingLeaderHandsOverToTheLongestServingOnlineMember() {
        var roster = new PartyRoster(4, Duration.ofSeconds(30));
        var leader = AccountId.random(); var away = AccountId.random(); var here = AccountId.random();
        join(roster.invite(leader, away, NOW)); join(roster.accept(away, NOW));
        join(roster.invite(leader, here, NOW)); join(roster.accept(here, NOW));
        roster.markOffline(away, NOW);
        assertEquals(PartyResult.LEFT, join(roster.leave(leader)));
        assertEquals(here, details(roster, here).leader());
        assertEquals(List.of(away, here), details(roster, here).members());
    }

    @Test void onlyLeadersInviteUnlessThePolicyAllowsMembers() {
        var roster = new PartyRoster(5, Duration.ofSeconds(30));
        var leader = AccountId.random(); var member = AccountId.random(); var friend = AccountId.random();
        join(roster.invite(leader, member, NOW)); join(roster.accept(member, NOW));
        assertEquals(PartyResult.NOT_LEADER, join(roster.invite(member, friend, NOW)));
        assertEquals(PartyResult.NOT_LEADER, roster.setInvitePolicy(member, PartyRoster.InvitePolicy.MEMBERS));
        assertEquals(PartyResult.UPDATED, roster.setInvitePolicy(leader, PartyRoster.InvitePolicy.MEMBERS));
        assertEquals(PartyResult.INVITED, join(roster.invite(member, friend, NOW)));
        assertEquals(member, roster.invitesFor(friend, NOW).getFirst().invitedBy());
        assertEquals(leader, roster.invitesFor(friend, NOW).getFirst().leader());
    }

    @Test void openPartiesCanBeJoinedFromTheFinder() {
        var roster = new PartyRoster(2, Duration.ofSeconds(30));
        var leader = AccountId.random(); var stranger = AccountId.random(); var late = AccountId.random();
        assertEquals(PartyResult.JOINED, roster.create(leader, NOW));
        assertEquals(PartyResult.ALREADY_IN_PARTY, roster.create(leader, NOW));
        var party = details(roster, leader).id();
        assertEquals(PartyResult.NOT_OPEN, roster.join(stranger, party));
        assertTrue(roster.openParties().isEmpty());
        assertEquals(PartyResult.OPENED, roster.setOpen(leader, true));
        assertEquals(List.of(party), roster.openParties().stream().map(PartyRoster.Details::id).toList());
        assertEquals(PartyResult.JOINED, roster.join(stranger, party));
        assertTrue(roster.openParties().isEmpty(), "A full party leaves the finder");
        assertEquals(PartyResult.PARTY_FULL, roster.join(late, party));
    }

    @Test void notesAreShortAndPlain() {
        var roster = new PartyRoster(3, Duration.ofSeconds(30));
        var leader = AccountId.random();
        roster.create(leader, NOW);
        assertEquals(PartyResult.UPDATED, roster.setNote(leader, "  Cinder Keep, bring food  "));
        assertEquals("Cinder Keep, bring food", details(roster, leader).note());
        assertThrows(IllegalArgumentException.class, () -> roster.setNote(leader, "x".repeat(49)));
        assertThrows(IllegalArgumentException.class, () -> roster.setNote(leader, "line\nbreak"));
    }

    @Test void anOfflineLeaderHandsOverAndLongAbsencesLeaveTheParty() {
        var roster = new PartyRoster(3, Duration.ofSeconds(30));
        var leader = AccountId.random(); var member = AccountId.random();
        join(roster.invite(leader, member, NOW)); join(roster.accept(member, NOW));
        roster.markOffline(leader, NOW);
        roster.sweep(NOW.plusSeconds(59), HANDOVER, REMOVAL);
        assertEquals(leader, details(roster, member).leader());
        roster.sweep(NOW.plusSeconds(60), HANDOVER, REMOVAL);
        assertEquals(member, details(roster, member).leader());
        assertTrue(join(roster.sameParty(leader, member)), "Still a member until the removal window");
        roster.sweep(NOW.plus(REMOVAL), HANDOVER, REMOVAL);
        assertEquals(List.of(member), details(roster, member).members());
    }

    @Test void aLeaderComingBackInTimeKeepsTheParty() {
        var roster = new PartyRoster(3, Duration.ofSeconds(30));
        var leader = AccountId.random(); var member = AccountId.random();
        join(roster.invite(leader, member, NOW)); join(roster.accept(member, NOW));
        roster.markOffline(leader, NOW);
        roster.markOnline(leader);
        roster.sweep(NOW.plusSeconds(120), HANDOVER, REMOVAL);
        assertEquals(leader, details(roster, member).leader());
    }

    @Test void nobodyOnlineMeansNoHandover() {
        var roster = new PartyRoster(3, Duration.ofSeconds(30));
        var leader = AccountId.random(); var member = AccountId.random();
        join(roster.invite(leader, member, NOW)); join(roster.accept(member, NOW));
        roster.markOffline(leader, NOW); roster.markOffline(member, NOW);
        roster.sweep(NOW.plusSeconds(120), HANDOVER, REMOVAL);
        assertEquals(leader, details(roster, member).leader());
        roster.sweep(NOW.plus(REMOVAL), HANDOVER, REMOVAL);
        assertTrue(roster.details(member).isEmpty(), "Everyone timed out, so the party disbanded");
    }

    @Test void partiesSurviveARestart() {
        var roster = new PartyRoster(4, Duration.ofSeconds(30));
        var leader = AccountId.random(); var member = AccountId.random();
        join(roster.invite(leader, member, NOW)); join(roster.accept(member, NOW));
        roster.setOpen(leader, true); roster.setNote(leader, "Farming run");
        roster.setInvitePolicy(leader, PartyRoster.InvitePolicy.MEMBERS);
        var saved = roster.save().saveToString();

        var restored = new PartyRoster(4, Duration.ofSeconds(30));
        var yaml = new YamlConfiguration();
        try { yaml.loadFromString(saved); } catch (Exception exception) { throw new AssertionError(exception); }
        restored.load(yaml, NOW);
        var party = details(restored, member);
        assertEquals(details(roster, leader).id(), party.id());
        assertEquals(leader, party.leader());
        assertEquals(List.of(leader, member), party.members());
        assertTrue(party.open());
        assertEquals("Farming run", party.note());
        assertEquals(PartyRoster.InvitePolicy.MEMBERS, party.invitePolicy());
        assertFalse(restored.online(leader), "Restored members start offline until they log in");
    }

    @Test void damagedSavesAreSkipped() {
        var yaml = new YamlConfiguration();
        yaml.set("parties.not-a-uuid.leader", AccountId.random().toString());
        yaml.set("parties." + java.util.UUID.randomUUID() + ".leader", "garbage");
        var roster = new PartyRoster(4, Duration.ofSeconds(30));
        roster.load(yaml, NOW);
        assertTrue(roster.openParties().isEmpty());
        assertEquals("{}", roster.save().getValues(false).toString());
    }

    @Test void changesAreReportedAsEvents() {
        var roster = new PartyRoster(3, Duration.ofSeconds(30));
        List<PartyRoster.EventKind> seen = new ArrayList<>();
        roster.onChange(event -> seen.add(event.kind()));
        var leader = AccountId.random(); var member = AccountId.random();
        join(roster.invite(leader, member, NOW)); join(roster.accept(member, NOW));
        join(roster.promote(leader, member)); join(roster.kick(member, leader)); join(roster.disband(member));
        assertEquals(List.of(PartyRoster.EventKind.INVITED, PartyRoster.EventKind.JOINED, PartyRoster.EventKind.LEADER_CHANGED,
            PartyRoster.EventKind.KICKED, PartyRoster.EventKind.DISBANDED), seen);
    }

}
