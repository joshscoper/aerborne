package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PartyService {
    CompletionStage<PartyResult> invite(AccountId actor, AccountId target, Instant now);

    CompletionStage<PartyResult> accept(AccountId target, Instant now);

    CompletionStage<PartyResult> accept(AccountId target, UUID expectedParty, Instant now);

    CompletionStage<Optional<InviteView>> pendingInvite(AccountId target, Instant now);

    CompletionStage<PartyResult> decline(AccountId target);

    CompletionStage<PartyResult> leave(AccountId member);

    CompletionStage<PartyResult> kick(AccountId actor, AccountId target);

    CompletionStage<PartyResult> promote(AccountId actor, AccountId target);

    CompletionStage<PartyResult> disband(AccountId actor);

    CompletionStage<Optional<PartyView>> partyOf(AccountId member);

    CompletionStage<Boolean> sameParty(AccountId first, AccountId second);

    enum PartyResult {
        INVITED,
        INVITE_PENDING,
        DECLINED,
        JOINED,
        LEFT,
        KICKED,
        PROMOTED,
        DISBANDED,
        ALREADY_IN_PARTY,
        TARGET_ALREADY_IN_PARTY,
        NO_INVITE,
        INVITE_EXPIRED,
        PARTY_FULL,
        NOT_IN_PARTY,
        NOT_LEADER,
        TARGET_NOT_MEMBER,
        CANNOT_TARGET_SELF,
        LEADER_MUST_PROMOTE_OR_DISBAND
    }

    record InviteView(UUID partyId, AccountId leader, Instant expiresAt) { }

    record PartyView(UUID partyId, AccountId leader, List<AccountId> members) {
        public PartyView {
            members = List.copyOf(members);
        }
    }
}
