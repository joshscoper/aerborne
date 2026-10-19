package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.PartyService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPartyService implements PartyService {
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<AccountId, UUID> membership = new ConcurrentHashMap<>();
    private final Map<AccountId, Invite> invites = new ConcurrentHashMap<>();
    private final int maximumSize;
    private final Duration inviteLifetime;

    public InMemoryPartyService(int maximumSize, Duration inviteLifetime) {
        if (maximumSize < 2 || maximumSize > 8) {
            throw new IllegalArgumentException("maximumSize must be between 2 and 8");
        }
        if (inviteLifetime.isNegative() || inviteLifetime.isZero()) {
            throw new IllegalArgumentException("inviteLifetime must be positive");
        }
        this.maximumSize = maximumSize;
        this.inviteLifetime = inviteLifetime;
    }

    @Override
    public synchronized CompletionStage<PartyResult> invite(AccountId actor, AccountId target, Instant now) {
        if (actor.equals(target)) {
            return completed(PartyResult.CANNOT_TARGET_SELF);
        }
        if (membership.containsKey(target)) {
            return completed(PartyResult.TARGET_ALREADY_IN_PARTY);
        }
        invites.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now) || !parties.containsKey(entry.getValue().partyId));
        if (invites.containsKey(target)) return completed(PartyResult.INVITE_PENDING);
        Party party = partyFor(actor);
        if (party == null) {
            party = new Party(UUID.randomUUID(), actor);
            parties.put(party.id, party);
            membership.put(actor, party.id);
        } else if (!party.leader.equals(actor)) {
            return completed(PartyResult.NOT_LEADER);
        }
        UUID partyId = party.id;
        long reserved = invites.values().stream().filter(invite -> invite.partyId.equals(partyId)).count();
        if (party.members.size() + reserved >= maximumSize) {
            return completed(PartyResult.PARTY_FULL);
        }
        invites.put(target, new Invite(party.id, now.plus(inviteLifetime)));
        return completed(PartyResult.INVITED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> accept(AccountId target, Instant now) {
        return accept(target, null, now);
    }

    @Override
    public synchronized CompletionStage<PartyResult> accept(AccountId target, UUID expectedParty, Instant now) {
        if (membership.containsKey(target)) {
            return completed(PartyResult.ALREADY_IN_PARTY);
        }
        Invite invite = invites.get(target);
        if (invite == null) {
            return completed(PartyResult.NO_INVITE);
        }
        if (expectedParty != null && !expectedParty.equals(invite.partyId)) return completed(PartyResult.NO_INVITE);
        invites.remove(target);
        if (!invite.expiresAt.isAfter(now)) {
            return completed(PartyResult.INVITE_EXPIRED);
        }
        Party party = parties.get(invite.partyId);
        if (party == null) {
            return completed(PartyResult.NO_INVITE);
        }
        if (party.members.size() >= maximumSize) {
            return completed(PartyResult.PARTY_FULL);
        }
        party.members.add(target);
        membership.put(target, party.id);
        return completed(PartyResult.JOINED);
    }

    @Override
    public synchronized CompletionStage<Optional<InviteView>> pendingInvite(AccountId target, Instant now) {
        Invite invite = invites.get(target);
        Party party = invite == null ? null : parties.get(invite.partyId);
        if (invite == null || party == null || !invite.expiresAt.isAfter(now)) {
            invites.remove(target);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new InviteView(party.id, party.leader, invite.expiresAt)));
    }

    @Override
    public synchronized CompletionStage<PartyResult> decline(AccountId target) {
        return completed(invites.remove(target) == null ? PartyResult.NO_INVITE : PartyResult.DECLINED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> leave(AccountId member) {
        Party party = partyFor(member);
        if (party == null) {
            return completed(PartyResult.NOT_IN_PARTY);
        }
        if (party.leader.equals(member) && party.members.size() > 1) {
            return completed(PartyResult.LEADER_MUST_PROMOTE_OR_DISBAND);
        }
        party.members.remove(member);
        membership.remove(member);
        if (party.members.isEmpty()) {
            parties.remove(party.id);
            invites.entrySet().removeIf(entry -> entry.getValue().partyId.equals(party.id));
        }
        return completed(PartyResult.LEFT);
    }

    @Override
    public synchronized CompletionStage<PartyResult> kick(AccountId actor, AccountId target) {
        Party party = partyFor(actor);
        if (party == null) {
            return completed(PartyResult.NOT_IN_PARTY);
        }
        if (!party.leader.equals(actor)) {
            return completed(PartyResult.NOT_LEADER);
        }
        if (actor.equals(target)) {
            return completed(PartyResult.CANNOT_TARGET_SELF);
        }
        if (!party.members.remove(target)) {
            return completed(PartyResult.TARGET_NOT_MEMBER);
        }
        membership.remove(target);
        return completed(PartyResult.KICKED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> promote(AccountId actor, AccountId target) {
        Party party = partyFor(actor);
        if (party == null) {
            return completed(PartyResult.NOT_IN_PARTY);
        }
        if (!party.leader.equals(actor)) {
            return completed(PartyResult.NOT_LEADER);
        }
        if (actor.equals(target)) {
            return completed(PartyResult.CANNOT_TARGET_SELF);
        }
        if (!party.members.contains(target)) {
            return completed(PartyResult.TARGET_NOT_MEMBER);
        }
        party.leader = target;
        invites.entrySet().removeIf(entry -> entry.getValue().partyId.equals(party.id));
        return completed(PartyResult.PROMOTED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> disband(AccountId actor) {
        Party party = partyFor(actor);
        if (party == null) {
            return completed(PartyResult.NOT_IN_PARTY);
        }
        if (!party.leader.equals(actor)) {
            return completed(PartyResult.NOT_LEADER);
        }
        party.members.forEach(membership::remove);
        parties.remove(party.id);
        invites.entrySet().removeIf(entry -> entry.getValue().partyId.equals(party.id));
        return completed(PartyResult.DISBANDED);
    }

    @Override
    public synchronized CompletionStage<Optional<PartyView>> partyOf(AccountId member) {
        Party party = partyFor(member);
        return CompletableFuture.completedFuture(party == null ? Optional.empty() : Optional.of(view(party)));
    }

    @Override
    public CompletionStage<Boolean> sameParty(AccountId first, AccountId second) {
        UUID party = membership.get(first);
        return CompletableFuture.completedFuture(party != null && party.equals(membership.get(second)));
    }

    private Party partyFor(AccountId member) {
        UUID partyId = membership.get(member);
        return partyId == null ? null : parties.get(partyId);
    }

    private PartyView view(Party party) {
        return new PartyView(party.id, party.leader, new ArrayList<>(party.members));
    }

    private CompletionStage<PartyResult> completed(PartyResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static final class Party {
        private final UUID id;
        private final LinkedHashSet<AccountId> members = new LinkedHashSet<>();
        private AccountId leader;

        private Party(UUID id, AccountId leader) {
            this.id = id;
            this.leader = leader;
            members.add(leader);
        }
    }

    private record Invite(UUID partyId, Instant expiresAt) {
    }
}
