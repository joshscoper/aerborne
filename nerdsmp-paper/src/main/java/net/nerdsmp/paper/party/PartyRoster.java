package net.nerdsmp.paper.party;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.PartyService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Who is in which party, free of Bukkit. Beyond the basic {@link PartyService} contract it keeps
 * parties alive through disconnects (an offline leader hands over, long-offline members drop out),
 * lets a leader open the party to anyone or let members invite, carries a short note for the party
 * finder, and reports every change as a {@link PartyEvent} so the game can tell the whole party.
 */
public final class PartyRoster implements PartyService {
    public enum InvitePolicy { LEADER, MEMBERS }

    public enum EventKind { CREATED, INVITED, JOINED, LEFT, KICKED, REMOVED_OFFLINE, LEADER_CHANGED, DISBANDED, OPENED, CLOSED, NOTE, POLICY }

    /** {@code actor} did it ({@code null} for automatic changes); {@code target} is who it was done to. */
    public record PartyEvent(EventKind kind, UUID partyId, AccountId actor, AccountId target, List<AccountId> members, boolean automatic) {
        public PartyEvent {
            members = List.copyOf(members);
        }
    }

    /** Read-only view of one party with its settings. */
    public record Details(UUID id, AccountId leader, List<AccountId> members, boolean open, String note, InvitePolicy invitePolicy) {
        public Details {
            members = List.copyOf(members);
        }
    }

    public record PendingInvite(UUID partyId, AccountId leader, AccountId invitedBy, Instant expiresAt) { }

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<AccountId, UUID> membership = new HashMap<>();
    private final Map<AccountId, Map<UUID, Invite>> invites = new HashMap<>();
    private final Map<AccountId, Instant> offlineSince = new HashMap<>();
    private final int maximumSize;
    private final Duration inviteLifetime;
    private final int maxPendingInvites;
    private Consumer<PartyEvent> listener = event -> { };

    public PartyRoster(int maximumSize, Duration inviteLifetime) {
        this(maximumSize, inviteLifetime, 3);
    }

    public PartyRoster(int maximumSize, Duration inviteLifetime, int maxPendingInvites) {
        if (maximumSize < 2 || maximumSize > 8) throw new IllegalArgumentException("maximumSize must be between 2 and 8");
        if (inviteLifetime.isNegative() || inviteLifetime.isZero()) throw new IllegalArgumentException("inviteLifetime must be positive");
        if (maxPendingInvites < 1) throw new IllegalArgumentException("maxPendingInvites must be positive");
        this.maximumSize = maximumSize;
        this.inviteLifetime = inviteLifetime;
        this.maxPendingInvites = maxPendingInvites;
    }

    public synchronized void onChange(Consumer<PartyEvent> listener) { this.listener = listener == null ? event -> { } : listener; }

    public int maximumSize() { return maximumSize; }

    // ---------------------------------------------------------------------------------------------
    // PartyService
    // ---------------------------------------------------------------------------------------------

    @Override
    public synchronized CompletionStage<PartyResult> invite(AccountId actor, AccountId target, Instant now) {
        if (actor.equals(target)) return done(PartyResult.CANNOT_TARGET_SELF);
        if (membership.containsKey(target)) return done(PartyResult.TARGET_ALREADY_IN_PARTY);
        prune(now);
        Party party = partyFor(actor);
        if (party != null && !party.leader.equals(actor) && party.invitePolicy == InvitePolicy.LEADER) return done(PartyResult.NOT_LEADER);
        Map<UUID, Invite> held = invites.getOrDefault(target, Map.of());
        if (party != null && held.containsKey(party.id)) return done(PartyResult.INVITE_PENDING);
        if (held.size() >= maxPendingInvites) return done(PartyResult.TOO_MANY_INVITES);
        if (party == null) party = create(actor, now, false);
        if (party.members.size() + reserved(party.id) >= maximumSize) return done(PartyResult.PARTY_FULL);
        invites.computeIfAbsent(target, key -> new LinkedHashMap<>()).put(party.id, new Invite(party.id, actor, now.plus(inviteLifetime)));
        emit(EventKind.INVITED, party, actor, target, false);
        return done(PartyResult.INVITED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> accept(AccountId target, Instant now) {
        return accept(target, null, now);
    }

    /** Accepts the invite from {@code expectedParty}, or the newest invite when it is null. */
    @Override
    public synchronized CompletionStage<PartyResult> accept(AccountId target, UUID expectedParty, Instant now) {
        if (membership.containsKey(target)) return done(PartyResult.ALREADY_IN_PARTY);
        Map<UUID, Invite> held = invites.get(target);
        if (held == null || held.isEmpty()) return done(PartyResult.NO_INVITE);
        Invite invite = expectedParty != null ? held.get(expectedParty) : newest(held);
        if (invite == null) return done(PartyResult.NO_INVITE);
        held.remove(invite.partyId);
        if (held.isEmpty()) invites.remove(target);
        if (!invite.expiresAt.isAfter(now)) return done(PartyResult.INVITE_EXPIRED);
        Party party = parties.get(invite.partyId);
        if (party == null) return done(PartyResult.NO_INVITE);
        if (party.members.size() >= maximumSize) return done(PartyResult.PARTY_FULL);
        add(party, target);
        invites.remove(target); // Joining one party withdraws every other invitation.
        emit(EventKind.JOINED, party, target, target, false);
        return done(PartyResult.JOINED);
    }

    @Override
    public synchronized CompletionStage<Optional<InviteView>> pendingInvite(AccountId target, Instant now) {
        prune(now);
        Map<UUID, Invite> held = invites.get(target);
        Invite invite = held == null ? null : newest(held);
        Party party = invite == null ? null : parties.get(invite.partyId);
        return CompletableFuture.completedFuture(party == null ? Optional.empty()
            : Optional.of(new InviteView(party.id, party.leader, invite.expiresAt)));
    }

    @Override
    public synchronized CompletionStage<PartyResult> decline(AccountId target) {
        Map<UUID, Invite> held = invites.remove(target);
        return done(held == null || held.isEmpty() ? PartyResult.NO_INVITE : PartyResult.DECLINED);
    }

    public synchronized PartyResult decline(AccountId target, UUID partyId) {
        Map<UUID, Invite> held = invites.get(target);
        if (held == null || held.remove(partyId) == null) return PartyResult.NO_INVITE;
        if (held.isEmpty()) invites.remove(target);
        return PartyResult.DECLINED;
    }

    /** A leaving leader hands the party to the member who has been there longest, preferring someone online. */
    @Override
    public synchronized CompletionStage<PartyResult> leave(AccountId member) {
        Party party = partyFor(member);
        if (party == null) return done(PartyResult.NOT_IN_PARTY);
        remove(party, member);
        emit(EventKind.LEFT, party, member, member, false);
        settle(party, false);
        return done(PartyResult.LEFT);
    }

    @Override
    public synchronized CompletionStage<PartyResult> kick(AccountId actor, AccountId target) {
        Party party = partyFor(actor);
        if (party == null) return done(PartyResult.NOT_IN_PARTY);
        if (!party.leader.equals(actor)) return done(PartyResult.NOT_LEADER);
        if (actor.equals(target)) return done(PartyResult.CANNOT_TARGET_SELF);
        if (!party.members.contains(target)) return done(PartyResult.TARGET_NOT_MEMBER);
        remove(party, target);
        emit(EventKind.KICKED, party, actor, target, false);
        settle(party, false);
        return done(PartyResult.KICKED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> promote(AccountId actor, AccountId target) {
        Party party = partyFor(actor);
        if (party == null) return done(PartyResult.NOT_IN_PARTY);
        if (!party.leader.equals(actor)) return done(PartyResult.NOT_LEADER);
        if (actor.equals(target)) return done(PartyResult.CANNOT_TARGET_SELF);
        if (!party.members.contains(target)) return done(PartyResult.TARGET_NOT_MEMBER);
        lead(party, target, actor, false);
        return done(PartyResult.PROMOTED);
    }

    @Override
    public synchronized CompletionStage<PartyResult> disband(AccountId actor) {
        Party party = partyFor(actor);
        if (party == null) return done(PartyResult.NOT_IN_PARTY);
        if (!party.leader.equals(actor)) return done(PartyResult.NOT_LEADER);
        dissolve(party, actor, false);
        return done(PartyResult.DISBANDED);
    }

    @Override
    public synchronized CompletionStage<Optional<PartyView>> partyOf(AccountId member) {
        Party party = partyFor(member);
        return CompletableFuture.completedFuture(party == null ? Optional.empty()
            : Optional.of(new PartyView(party.id, party.leader, new ArrayList<>(party.members))));
    }

    @Override
    public synchronized CompletionStage<Boolean> sameParty(AccountId first, AccountId second) {
        UUID party = membership.get(first);
        return CompletableFuture.completedFuture(party != null && party.equals(membership.get(second)));
    }

    // ---------------------------------------------------------------------------------------------
    // Extras
    // ---------------------------------------------------------------------------------------------

    /** Starts a party of one, so a player can open it to the finder before inviting anyone. */
    public synchronized PartyResult create(AccountId actor, Instant now) {
        if (membership.containsKey(actor)) return PartyResult.ALREADY_IN_PARTY;
        create(actor, now, true);
        return PartyResult.JOINED;
    }

    public synchronized PartyResult join(AccountId target, UUID partyId) {
        if (membership.containsKey(target)) return PartyResult.ALREADY_IN_PARTY;
        Party party = parties.get(partyId);
        if (party == null) return PartyResult.NO_INVITE;
        if (!party.open) return PartyResult.NOT_OPEN;
        if (party.members.size() + reserved(partyId) >= maximumSize) return PartyResult.PARTY_FULL;
        add(party, target);
        invites.remove(target);
        emit(EventKind.JOINED, party, target, target, false);
        return PartyResult.JOINED;
    }

    public synchronized PartyResult setOpen(AccountId actor, boolean open) {
        Party party = partyFor(actor);
        if (party == null) return PartyResult.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return PartyResult.NOT_LEADER;
        if (party.open == open) return open ? PartyResult.OPENED : PartyResult.CLOSED;
        party.open = open;
        emit(open ? EventKind.OPENED : EventKind.CLOSED, party, actor, null, false);
        return open ? PartyResult.OPENED : PartyResult.CLOSED;
    }

    public synchronized PartyResult setNote(AccountId actor, String note) {
        Party party = partyFor(actor);
        if (party == null) return PartyResult.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return PartyResult.NOT_LEADER;
        String clean = note == null ? "" : note.strip();
        if (clean.length() > 48 || clean.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Keep the note to 48 visible characters.");
        party.note = clean;
        emit(EventKind.NOTE, party, actor, null, false);
        return PartyResult.UPDATED;
    }

    public synchronized PartyResult setInvitePolicy(AccountId actor, InvitePolicy policy) {
        Party party = partyFor(actor);
        if (party == null) return PartyResult.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return PartyResult.NOT_LEADER;
        party.invitePolicy = policy;
        emit(EventKind.POLICY, party, actor, null, false);
        return PartyResult.UPDATED;
    }

    public synchronized Optional<Details> details(AccountId member) {
        Party party = partyFor(member);
        return party == null ? Optional.empty() : Optional.of(details(party));
    }

    public synchronized Optional<Details> details(UUID partyId) {
        Party party = parties.get(partyId);
        return party == null ? Optional.empty() : Optional.of(details(party));
    }

    /** Open parties with room, fullest first, for the party finder. */
    public synchronized List<Details> openParties() {
        return parties.values().stream().filter(party -> party.open && party.members.size() + reserved(party.id) < maximumSize)
            .sorted(Comparator.comparingInt((Party party) -> -party.members.size()).thenComparing(party -> party.createdAt))
            .map(this::details).toList();
    }

    public synchronized List<PendingInvite> invitesFor(AccountId target, Instant now) {
        prune(now);
        List<PendingInvite> list = new ArrayList<>();
        for (Invite invite : invites.getOrDefault(target, Map.of()).values()) {
            Party party = parties.get(invite.partyId);
            if (party != null) list.add(new PendingInvite(party.id, party.leader, invite.invitedBy, invite.expiresAt));
        }
        list.sort(Comparator.comparing(PendingInvite::expiresAt).reversed());
        return List.copyOf(list);
    }

    public synchronized boolean online(AccountId member) { return !offlineSince.containsKey(member); }

    public synchronized void markOnline(AccountId member) { offlineSince.remove(member); }

    public synchronized void markOffline(AccountId member, Instant now) {
        if (membership.containsKey(member)) offlineSince.putIfAbsent(member, now);
    }

    /**
     * Housekeeping, about once a second: an offline leader hands over after {@code handover}, and a
     * member offline for {@code removeAfter} leaves the party (an empty party disbands).
     */
    public synchronized void sweep(Instant now, Duration handover, Duration removeAfter) {
        prune(now);
        for (Party party : List.copyOf(parties.values())) {
            for (AccountId member : List.copyOf(party.members)) {
                Instant since = offlineSince.get(member);
                if (since == null || now.isBefore(since.plus(removeAfter))) continue;
                remove(party, member);
                offlineSince.remove(member);
                emit(EventKind.REMOVED_OFFLINE, party, null, member, true);
            }
            if (!parties.containsKey(party.id)) continue;
            Instant leaderAway = offlineSince.get(party.leader);
            if (leaderAway != null && !now.isBefore(leaderAway.plus(handover))) {
                successor(party).filter(this::online).ifPresent(next -> lead(party, next, null, true));
            }
            settle(party, true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    public synchronized YamlConfiguration save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Party party : parties.values()) {
            String path = "parties." + party.id;
            yaml.set(path + ".leader", party.leader.toString());
            yaml.set(path + ".members", party.members.stream().map(AccountId::toString).toList());
            yaml.set(path + ".open", party.open);
            yaml.set(path + ".note", party.note);
            yaml.set(path + ".invite-policy", party.invitePolicy.name());
            yaml.set(path + ".created-at", party.createdAt.toString());
        }
        return yaml;
    }

    /** Restores parties after a restart. Everyone starts offline until they log back in. */
    public synchronized void load(ConfigurationSection yaml, Instant now) {
        ConfigurationSection section = yaml.getConfigurationSection("parties");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection saved = section.getConfigurationSection(id);
            if (saved == null) continue;
            try {
                Party party = new Party(UUID.fromString(id), AccountId.parse(saved.getString("leader", "")),
                    Instant.parse(saved.getString("created-at", now.toString())));
                for (String raw : saved.getStringList("members")) party.members.add(AccountId.parse(raw));
                party.members.add(party.leader);
                party.members.removeIf(membership::containsKey);
                if (party.members.size() < 1 || party.members.size() > maximumSize || !party.members.contains(party.leader)) continue;
                party.open = saved.getBoolean("open");
                party.note = saved.getString("note", "");
                party.invitePolicy = InvitePolicy.valueOf(saved.getString("invite-policy", "LEADER"));
                parties.put(party.id, party);
                for (AccountId member : party.members) {
                    membership.put(member, party.id);
                    offlineSince.put(member, now);
                }
            } catch (IllegalArgumentException | NullPointerException damaged) {
                // A damaged entry is skipped; the rest of the parties still load.
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    private Party create(AccountId leader, Instant now, boolean announce) {
        Party party = new Party(UUID.randomUUID(), leader, now);
        parties.put(party.id, party);
        membership.put(leader, party.id);
        invites.remove(leader);
        if (announce) emit(EventKind.CREATED, party, leader, leader, false);
        return party;
    }

    private void add(Party party, AccountId member) {
        party.members.add(member);
        membership.put(member, party.id);
        offlineSince.remove(member);
    }

    private void remove(Party party, AccountId member) {
        party.members.remove(member);
        membership.remove(member);
        offlineSince.remove(member);
    }

    /** After someone leaves: disband an empty party or hand a leaderless one to the next member. */
    private void settle(Party party, boolean automatic) {
        if (!parties.containsKey(party.id)) return;
        if (party.members.isEmpty()) {
            dissolve(party, null, automatic);
            return;
        }
        if (!party.members.contains(party.leader)) successor(party).ifPresent(next -> lead(party, next, null, true));
    }

    private Optional<AccountId> successor(Party party) {
        return party.members.stream().filter(member -> !member.equals(party.leader) && online(member)).findFirst()
            .or(() -> party.members.stream().filter(member -> !member.equals(party.leader)).findFirst());
    }

    private void lead(Party party, AccountId next, AccountId actor, boolean automatic) {
        party.leader = next;
        dropInvites(party.id); // Invitations were the old leader's offer; the new leader re-invites.
        emit(EventKind.LEADER_CHANGED, party, actor, next, automatic);
    }

    private void dissolve(Party party, AccountId actor, boolean automatic) {
        List<AccountId> members = List.copyOf(party.members);
        party.members.forEach(member -> {
            membership.remove(member);
            offlineSince.remove(member);
        });
        parties.remove(party.id);
        dropInvites(party.id);
        listener.accept(new PartyEvent(EventKind.DISBANDED, party.id, actor, null, members, automatic));
    }

    private void dropInvites(UUID partyId) {
        invites.values().forEach(held -> held.remove(partyId));
        invites.values().removeIf(Map::isEmpty);
    }

    private long reserved(UUID partyId) {
        return invites.values().stream().filter(held -> held.containsKey(partyId)).count();
    }

    private void prune(Instant now) {
        invites.values().forEach(held -> held.values().removeIf(invite -> !invite.expiresAt.isAfter(now) || !parties.containsKey(invite.partyId)));
        invites.values().removeIf(Map::isEmpty);
    }

    private static Invite newest(Map<UUID, Invite> held) {
        return held.values().stream().max(Comparator.comparing(invite -> invite.expiresAt)).orElse(null);
    }

    private Party partyFor(AccountId member) {
        UUID partyId = membership.get(member);
        return partyId == null ? null : parties.get(partyId);
    }

    private Details details(Party party) {
        return new Details(party.id, party.leader, List.copyOf(party.members), party.open, party.note, party.invitePolicy);
    }

    private void emit(EventKind kind, Party party, AccountId actor, AccountId target, boolean automatic) {
        listener.accept(new PartyEvent(kind, party.id, actor, target, List.copyOf(party.members), automatic));
    }

    private static CompletionStage<PartyResult> done(PartyResult result) { return CompletableFuture.completedFuture(result); }

    private static final class Party {
        final UUID id;
        final LinkedHashSet<AccountId> members = new LinkedHashSet<>();
        final Instant createdAt;
        AccountId leader;
        boolean open;
        String note = "";
        InvitePolicy invitePolicy = InvitePolicy.LEADER;

        Party(UUID id, AccountId leader, Instant createdAt) {
            this.id = id;
            this.leader = leader;
            this.createdAt = createdAt;
            members.add(leader);
        }
    }

    private record Invite(UUID partyId, AccountId invitedBy, Instant expiresAt) { }
}
