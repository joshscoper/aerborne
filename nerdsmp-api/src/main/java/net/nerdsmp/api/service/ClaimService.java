package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.api.model.ClaimRole;
import net.nerdsmp.api.model.PlotType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ClaimService {
    CompletionStage<ClaimResult> createClaim(AccountId owner, ChunkKey chunk);

    CompletionStage<ClaimResult> deleteClaim(AccountId actor, ChunkKey chunk);

    CompletionStage<ClaimResult> setTrust(AccountId actor, ChunkKey chunk, AccountId target, ClaimRole role);

    CompletionStage<ClaimResult> setTrustAll(AccountId actor, AccountId target, ClaimRole role);

    CompletionStage<ClaimResult> setName(AccountId actor, ChunkKey chunk, String name);

    CompletionStage<ClaimResult> setFlag(AccountId actor, ChunkKey chunk, ClaimFlag flag, boolean enabled);

    CompletionStage<Optional<ClaimView>> find(ChunkKey chunk);

    CompletionStage<ClaimRole> roleAt(AccountId accountId, ChunkKey chunk);

    CompletionStage<Boolean> flagAt(ChunkKey chunk, ClaimFlag flag);

    CompletionStage<List<ClaimView>> listClaims(AccountId accountId);

    CompletionStage<Integer> claimCount(AccountId accountId);

    CompletionStage<Integer> capacity(AccountId accountId);

    CompletionStage<ClaimResult> addCapacity(AccountId accountId, int chunks);

    CompletionStage<ClaimResult> createClaimFor(AccountId actor, AccountId owner, ChunkKey chunk);

    CompletionStage<HomesteadView> homestead(AccountId owner);

    CompletionStage<ClaimResult> renameHomestead(AccountId actor, String name);

    CompletionStage<ClaimResult> setFlagAll(AccountId actor, AccountId owner, ClaimFlag flag, boolean enabled);

    CompletionStage<ClaimResult> invite(AccountId actor, AccountId owner, AccountId target, java.time.Instant now);

    CompletionStage<ClaimResult> acceptInvite(AccountId account, AccountId owner, java.time.Instant now);

    CompletionStage<ClaimResult> leaveHomestead(AccountId member, AccountId owner);

    CompletionStage<List<AccountId>> invitations(AccountId target, java.time.Instant now);

    CompletionStage<ClaimResult> resetFlag(AccountId actor, ChunkKey chunk, ClaimFlag flag);

    CompletionStage<ClaimResult> resetTrust(AccountId actor, ChunkKey chunk, AccountId target);

    CompletionStage<ClaimResult> setPlotType(AccountId actor, ChunkKey chunk, PlotType type);

    CompletionStage<ClaimResult> assignSteward(AccountId actor, ChunkKey chunk, AccountId steward);

    CompletionStage<ClaimResult> setBulletin(AccountId actor, AccountId owner, String text);

    CompletionStage<ClaimResult> setListed(AccountId actor, AccountId owner, boolean listed);

    CompletionStage<ClaimResult> setRecruiting(AccountId actor, AccountId owner, boolean recruiting);

    CompletionStage<List<HomesteadView>> directory();

    CompletionStage<List<HomesteadView>> settlementsFor(AccountId member);

    CompletionStage<ClaimResult> applyTo(AccountId applicant, AccountId owner, java.time.Instant now);

    CompletionStage<ClaimResult> withdrawApplication(AccountId applicant, AccountId owner);

    CompletionStage<List<ApplicationView>> applications(AccountId actor, AccountId owner, java.time.Instant now);

    CompletionStage<ClaimResult> reviewApplication(AccountId actor, AccountId owner, AccountId applicant,
                                                  boolean accept, java.time.Instant now);

    CompletionStage<ClaimResult> cancelInvite(AccountId actor, AccountId owner, AccountId target);

    CompletionStage<List<AuditEntry>> history(AccountId actor, AccountId owner);

    boolean isReserved(ChunkKey chunk);

    enum ClaimResult {
        CREATED,
        DELETED,
        TRUST_UPDATED,
        NAME_UPDATED,
        FLAG_UPDATED,
        CAPACITY_UPDATED,
        ALREADY_CLAIMED,
        NOT_CLAIMED,
        NOT_OWNER,
        CAPACITY_REACHED,
        MAX_CAPACITY_REACHED,
        NOT_ADJACENT,
        INVALID_NAME,
        NO_CLAIMS,
        RESERVED,
        WOULD_SPLIT,
        INVALID_ROLE,
        INVITED,
        JOINED,
        LEFT,
        NO_INVITATION,
        ALREADY_MEMBER,
        MEMBER_LIMIT,
        PLOT_UPDATED,
        STEWARD_UPDATED,
        NOT_MEMBER,
        BULLETIN_UPDATED,
        DIRECTORY_UPDATED,
        INVALID_TEXT,
        APPLIED,
        APPLICATION_REMOVED,
        NO_APPLICATION,
        APPLICATION_LIMIT,
        NOT_RECRUITING,
        INVITATION_CANCELLED
    }

    record HomesteadView(AccountId owner, String name, Map<AccountId, ClaimRole> members,
                         Set<ClaimFlag> flags, int chunks, int capacity,
                         String bulletin, boolean listed, boolean recruiting) {
        public HomesteadView(AccountId owner, String name, Map<AccountId, ClaimRole> members,
                             Set<ClaimFlag> flags, int chunks, int capacity) {
            this(owner, name, members, flags, chunks, capacity, "", false, false);
        }
        public HomesteadView {
            members = Map.copyOf(members);
            flags = Set.copyOf(flags);
        }
    }

    record ClaimView(
        UUID claimId,
        AccountId owner,
        ChunkKey chunk,
        String name,
        Map<AccountId, ClaimRole> members,
        Set<ClaimFlag> flags,
        PlotType type,
        AccountId steward,
        Map<ClaimFlag, Boolean> flagOverrides,
        Map<AccountId, ClaimRole> localMembers
    ) {
        public ClaimView(UUID claimId, AccountId owner, ChunkKey chunk, String name,
                         Map<AccountId, ClaimRole> members, Set<ClaimFlag> flags) {
            this(claimId, owner, chunk, name, members, flags, PlotType.COMMONS, null, Map.of(), Map.of());
        }

        public ClaimView {
            members = Map.copyOf(members);
            flags = Set.copyOf(flags);
            flagOverrides = Map.copyOf(flagOverrides);
            localMembers = Map.copyOf(localMembers);
            java.util.Objects.requireNonNull(type);
        }
    }

    record ApplicationView(AccountId applicant, java.time.Instant expiresAt) { }

    record AuditEntry(java.time.Instant at, AccountId actor, String action, String detail) { }
}
