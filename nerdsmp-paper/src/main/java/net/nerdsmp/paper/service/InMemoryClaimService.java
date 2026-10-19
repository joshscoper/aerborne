package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Persistent homesteads with synchronous, immutable protection snapshots. */
public final class InMemoryClaimService implements ClaimService {
    private static final int SCHEMA = 3;
    private static final long APPLICATION_SECONDS = 7 * 24 * 60 * 60;
    private final Map<AccountId, Estate> estates = new HashMap<>();
    private final Map<ChunkKey, AccountId> owners = new HashMap<>();
    private final int starterCapacity;
    private final int maximumCapacity;
    private final int memberLimit;
    private final PlayerDataStore persistence;
    private final String path;
    private final Predicate<ChunkKey> reserved;
    private net.nerdsmp.paper.claim.TerritoryOverlay overlay;

    public synchronized void setTerritoryOverlay(net.nerdsmp.paper.claim.TerritoryOverlay overlay) {
        this.overlay = Objects.requireNonNull(overlay);
    }

    public InMemoryClaimService(int starterCapacity) {
        this(starterCapacity, 64);
    }

    public InMemoryClaimService(int starterCapacity, int maximumCapacity) {
        this(starterCapacity, maximumCapacity, null, "season-1");
    }

    public InMemoryClaimService(int starterCapacity, int maximumCapacity, PlayerDataStore persistence, String seasonId) {
        this(starterCapacity, maximumCapacity, persistence, seasonId, 32, chunk -> false);
    }

    public InMemoryClaimService(int starterCapacity, int maximumCapacity, PlayerDataStore persistence,
                                String seasonId, int memberLimit, Predicate<ChunkKey> reserved) {
        if (starterCapacity < 1 || maximumCapacity < starterCapacity || memberLimit < 1) {
            throw new IllegalArgumentException("Invalid homestead capacity or member limit");
        }
        this.starterCapacity = starterCapacity;
        this.maximumCapacity = maximumCapacity;
        this.memberLimit = memberLimit;
        this.persistence = persistence;
        this.path = "seasons." + seasonId + ".claims";
        this.reserved = Objects.requireNonNull(reserved);
        if (persistence != null) persistence.knownAccounts().forEach(this::estate);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> createClaim(AccountId owner, ChunkKey chunk) {
        return createClaimFor(owner, owner, chunk);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> createClaimFor(AccountId actor, AccountId owner, ChunkKey chunk) {
        if (overlay != null && overlay.manages(chunk.worldId())) return done(ClaimResult.RESERVED);
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (isReserved(chunk)) return done(ClaimResult.RESERVED);
        if (owners.containsKey(chunk)) return done(ClaimResult.ALREADY_CLAIMED);
        if (current.plots.size() >= current.capacity) return done(ClaimResult.CAPACITY_REACHED);
        Set<ChunkKey> inWorld = inWorld(current, chunk.worldId());
        if (!inWorld.isEmpty() && neighbors(chunk).stream().noneMatch(inWorld::contains)) {
            return done(ClaimResult.NOT_ADJACENT);
        }
        Estate next = current.copy();
        next.plots.put(chunk, new Plot(UUID.randomUUID(), "", Map.of(), Map.of()));
        commit(owner, next, actor, "CLAIM", chunk.toString());
        return done(ClaimResult.CREATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> deleteClaim(AccountId actor, ChunkKey chunk) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        // Releasing protection remains an owner-only operation.
        if (!owner.equals(actor)) return done(ClaimResult.NOT_OWNER);
        Estate current = estate(owner);
        Set<ChunkKey> before = inWorld(current, chunk.worldId());
        Set<ChunkKey> after = new HashSet<>(before);
        after.remove(chunk);
        if (components(after) > components(before)) return done(ClaimResult.WOULD_SPLIT);
        Estate next = current.copy();
        next.plots.remove(chunk);
        commit(owner, next, actor, "RELEASE", chunk.toString());
        return done(ClaimResult.DELETED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setTrust(AccountId actor, ChunkKey chunk, AccountId target, ClaimRole role) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        Plot plot = estate(owner).plots.get(chunk);
        if (!controlsGuests(actor, owner, plot)) return done(ClaimResult.NOT_OWNER);
        if (target.equals(owner) || target.equals(plot.steward) || role == ClaimRole.OWNER) return done(ClaimResult.INVALID_ROLE);
        if (!owner.equals(actor) && (actor.equals(target) || role == ClaimRole.MANAGE)) return done(ClaimResult.INVALID_ROLE);
        Estate next = estate(owner).copy();
        // NONE is an explicit plot denial, overriding territory membership.
        next.plots.get(chunk).members.put(target, Objects.requireNonNull(role));
        commit(owner, next, actor, "PLOT_TRUST", chunk + " " + target + " " + role);
        return done(ClaimResult.TRUST_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setTrustAll(AccountId actor, AccountId target, ClaimRole role) {
        Estate current = estate(actor);
        if (current.plots.isEmpty()) return done(ClaimResult.NO_CLAIMS);
        if (actor.equals(target) || role == ClaimRole.OWNER) return done(ClaimResult.INVALID_ROLE);
        if (role != ClaimRole.NONE && !current.members.containsKey(target) && !hasMemberRoom(current, target, Instant.now())) {
            return done(ClaimResult.MEMBER_LIMIT);
        }
        Estate next = current.copy();
        if (role == ClaimRole.NONE) removeMember(next, target);
        else next.members.put(target, Objects.requireNonNull(role));
        next.invites.remove(target);
        next.applications.remove(target);
        commit(actor, next, actor, "MEMBER_ROLE", target + " " + role);
        return done(ClaimResult.TRUST_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setName(AccountId actor, ChunkKey chunk, String rawName) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        Plot plot = estate(owner).plots.get(chunk);
        if (!managesPlot(actor, owner, chunk) && !actor.equals(plot.steward)) return done(ClaimResult.NOT_OWNER);
        String name = normalizeName(rawName);
        if (name == null) return done(ClaimResult.INVALID_NAME);
        Estate next = estate(owner).copy();
        next.plots.get(chunk).name = name;
        commit(owner, next, actor, "PLOT_NAME", chunk + " " + name);
        return done(ClaimResult.NAME_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> renameHomestead(AccountId actor, String rawName) {
        String name = normalizeName(rawName);
        if (name == null) return done(ClaimResult.INVALID_NAME);
        Estate next = estate(actor).copy();
        next.name = name;
        commit(actor, next, actor, "HOMESTEAD_NAME", name);
        return done(ClaimResult.NAME_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setFlag(AccountId actor, ChunkKey chunk, ClaimFlag flag, boolean enabled) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        if (!managesPlot(actor, owner, chunk)) return done(ClaimResult.NOT_OWNER);
        Estate next = estate(owner).copy();
        next.plots.get(chunk).overrides.put(Objects.requireNonNull(flag), enabled);
        commit(owner, next, actor, "PLOT_FLAG", chunk + " " + flag + "=" + enabled);
        return done(ClaimResult.FLAG_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setFlagAll(AccountId actor, AccountId owner, ClaimFlag flag, boolean enabled) {
        if (!manages(actor, owner, estate(owner))) return done(ClaimResult.NOT_OWNER);
        Estate next = estate(owner).copy();
        changeFlag(next.flags, flag, enabled);
        commit(owner, next, actor, "DEFAULT_FLAG", flag + "=" + enabled);
        return done(ClaimResult.FLAG_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> invite(AccountId actor, AccountId owner, AccountId target, Instant now) {
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (current.plots.isEmpty()) return done(ClaimResult.NO_CLAIMS);
        if (owner.equals(target) || current.members.containsKey(target)) return done(ClaimResult.ALREADY_MEMBER);
        Estate next = current.copy();
        next.invites.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (next.members.size() + next.invites.size() >= memberLimit && !next.invites.containsKey(target)) {
            return done(ClaimResult.MEMBER_LIMIT);
        }
        next.invites.put(target, now.plusSeconds(300));
        commit(owner, next, actor, "INVITE", target.toString());
        return done(ClaimResult.INVITED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> acceptInvite(AccountId account, AccountId owner, Instant now) {
        Estate current = estate(owner);
        if (current.plots.isEmpty() || !current.invites.getOrDefault(account, Instant.EPOCH).isAfter(now)) {
            return done(ClaimResult.NO_INVITATION);
        }
        if (current.members.containsKey(account)) return done(ClaimResult.ALREADY_MEMBER);
        if (!hasMemberRoom(current, account, now)) return done(ClaimResult.MEMBER_LIMIT);
        Estate next = current.copy();
        next.invites.remove(account);
        next.members.put(account, ClaimRole.RESIDENT);
        next.applications.remove(account);
        commit(owner, next, account, "JOIN_INVITATION", account.toString());
        return done(ClaimResult.JOINED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> leaveHomestead(AccountId member, AccountId owner) {
        Estate current = estate(owner);
        if (member.equals(owner) || !current.members.containsKey(member)) return done(ClaimResult.NOT_OWNER);
        Estate next = current.copy();
        removeMember(next, member);
        commit(owner, next, member, "LEAVE", member.toString());
        return done(ClaimResult.LEFT);
    }

    @Override
    public synchronized CompletionStage<List<AccountId>> invitations(AccountId target, Instant now) {
        return done(estates.entrySet().stream().filter(entry ->
            !entry.getValue().plots.isEmpty() && entry.getValue().invites.getOrDefault(target, Instant.EPOCH).isAfter(now))
            .map(Map.Entry::getKey).sorted(Comparator.comparing(AccountId::toString)).toList());
    }

    @Override
    public synchronized CompletionStage<HomesteadView> homestead(AccountId owner) {
        Estate e = estate(owner);
        return done(homeView(owner, e));
    }

    @Override
    public synchronized CompletionStage<Optional<ClaimView>> find(ChunkKey chunk) {
        if (overlay != null && overlay.manages(chunk.worldId())) return done(overlay.find(chunk));
        AccountId owner = owners.get(chunk);
        return done(owner == null ? Optional.empty() : Optional.of(view(owner, chunk)));
    }

    @Override
    public synchronized CompletionStage<ClaimRole> roleAt(AccountId account, ChunkKey chunk) {
        if (overlay != null && overlay.manages(chunk.worldId())) {
            return done(overlay.find(chunk).map(view -> view.owner().equals(account) ? ClaimRole.OWNER
                : view.members().getOrDefault(account, ClaimRole.NONE)).orElse(ClaimRole.NONE));
        }
        if (isReserved(chunk)) return done(ClaimRole.NONE);
        AccountId owner = owners.get(chunk);
        if (owner == null || owner.equals(account)) return done(ClaimRole.OWNER);
        Estate e = estate(owner);
        return done(role(owner, e, e.plots.get(chunk), account));
    }

    @Override
    public synchronized CompletionStage<Boolean> flagAt(ChunkKey chunk, ClaimFlag flag) {
        if (overlay != null && overlay.manages(chunk.worldId())) {
            return done(overlay.find(chunk).map(view -> view.flags().contains(flag)).orElse(false));
        }
        if (isReserved(chunk)) return done(false);
        AccountId owner = owners.get(chunk);
        return done(owner != null && effectiveFlags(estate(owner), estate(owner).plots.get(chunk)).contains(flag));
    }

    @Override
    public synchronized CompletionStage<List<ClaimView>> listClaims(AccountId account) {
        return done(estate(account).plots.keySet().stream()
            .sorted(Comparator.comparing(ChunkKey::worldId).thenComparingInt(ChunkKey::x).thenComparingInt(ChunkKey::z))
            .map(chunk -> view(account, chunk)).toList());
    }

    @Override
    public synchronized CompletionStage<Integer> claimCount(AccountId account) {
        return done(estate(account).plots.size());
    }

    @Override
    public synchronized CompletionStage<Integer> capacity(AccountId account) {
        return done(estate(account).capacity);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> addCapacity(AccountId account, int chunks) {
        Estate current = estate(account);
        if (chunks <= 0) return CompletableFuture.failedFuture(new IllegalArgumentException("chunks must be positive"));
        if (chunks > maximumCapacity - current.capacity) return done(ClaimResult.MAX_CAPACITY_REACHED);
        Estate next = current.copy();
        next.capacity += chunks;
        commit(account, next, account, "CAPACITY", "+" + chunks);
        return done(ClaimResult.CAPACITY_UPDATED);
    }

    @Override
    public synchronized boolean isReserved(ChunkKey chunk) {
        if (overlay != null && overlay.manages(chunk.worldId())) return overlay.find(chunk).isEmpty();
        return reserved.test(chunk);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> resetFlag(AccountId actor, ChunkKey chunk, ClaimFlag flag) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        if (!managesPlot(actor, owner, chunk)) return done(ClaimResult.NOT_OWNER);
        Estate next = estate(owner).copy();
        next.plots.get(chunk).overrides.remove(Objects.requireNonNull(flag));
        commit(owner, next, actor, "INHERIT_FLAG", chunk + " " + flag);
        return done(ClaimResult.FLAG_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> resetTrust(AccountId actor, ChunkKey chunk, AccountId target) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        if (!controlsGuests(actor, owner, estate(owner).plots.get(chunk))) return done(ClaimResult.NOT_OWNER);
        if (target.equals(owner) || !actor.equals(owner) && target.equals(actor)) return done(ClaimResult.INVALID_ROLE);
        Estate next = estate(owner).copy();
        next.plots.get(chunk).members.remove(target);
        commit(owner, next, actor, "INHERIT_TRUST", chunk + " " + target);
        return done(ClaimResult.TRUST_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setPlotType(AccountId actor, ChunkKey chunk, PlotType type) {
        Objects.requireNonNull(type);
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        Estate current = estate(owner);
        Plot before = current.plots.get(chunk);
        boolean privateChange = before.type.privateAccess() || type.privateAccess();
        if (privateChange ? !owner.equals(actor) : !manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        Estate next = current.copy();
        Plot plot = next.plots.get(chunk);
        if (plot.type.privateAccess() != type.privateAccess()) {
            plot.members.clear();
            plot.steward = null;
        }
        plot.type = type;
        plot.overrides.clear();
        plot.overrides.putAll(type.preset());
        commit(owner, next, actor, "PLOT_TYPE", chunk + " " + type);
        return done(ClaimResult.PLOT_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> assignSteward(AccountId actor, ChunkKey chunk, AccountId steward) {
        AccountId owner = owners.get(chunk);
        if (owner == null) return done(ClaimResult.NOT_CLAIMED);
        if (!owner.equals(actor)) return done(ClaimResult.NOT_OWNER);
        Estate current = estate(owner);
        if (steward != null && (owner.equals(steward) || !current.members.containsKey(steward))) return done(ClaimResult.NOT_MEMBER);
        Estate next = current.copy();
        Plot plot = next.plots.get(chunk);
        plot.type = PlotType.RESIDENTIAL;
        plot.steward = steward;
        plot.members.clear();
        plot.overrides.clear();
        plot.overrides.putAll(PlotType.RESIDENTIAL.preset());
        commit(owner, next, actor, "STEWARD", chunk + " " + (steward == null ? "unassigned" : steward));
        return done(ClaimResult.STEWARD_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setBulletin(AccountId actor, AccountId owner, String text) {
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (current.plots.isEmpty()) return done(ClaimResult.NO_CLAIMS);
        String normalized = normalizeBulletin(text);
        if (normalized == null) return done(ClaimResult.INVALID_TEXT);
        Estate next = current.copy();
        next.bulletin = normalized;
        commit(owner, next, actor, "BULLETIN", normalized.isEmpty() ? "cleared" : normalized);
        return done(ClaimResult.BULLETIN_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setListed(AccountId actor, AccountId owner, boolean listed) {
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (current.plots.isEmpty()) return done(ClaimResult.NO_CLAIMS);
        Estate next = current.copy();
        next.listed = listed;
        commit(owner, next, actor, "DIRECTORY", Boolean.toString(listed));
        return done(ClaimResult.DIRECTORY_UPDATED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> setRecruiting(AccountId actor, AccountId owner, boolean recruiting) {
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (current.plots.isEmpty()) return done(ClaimResult.NO_CLAIMS);
        Estate next = current.copy();
        next.recruiting = recruiting;
        commit(owner, next, actor, "RECRUITING", Boolean.toString(recruiting));
        return done(ClaimResult.DIRECTORY_UPDATED);
    }

    @Override
    public synchronized CompletionStage<List<HomesteadView>> directory() {
        return done(estates.entrySet().stream().filter(e -> e.getValue().listed && !e.getValue().plots.isEmpty())
            .map(e -> homeView(e.getKey(), e.getValue())).sorted(Comparator.comparing(HomesteadView::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.owner().toString())).toList());
    }

    @Override
    public synchronized CompletionStage<List<HomesteadView>> settlementsFor(AccountId member) {
        return done(estates.entrySet().stream().filter(e -> !e.getValue().plots.isEmpty()
                && (e.getKey().equals(member) || e.getValue().members.containsKey(member)))
            .map(e -> homeView(e.getKey(), e.getValue())).sorted(Comparator.comparing(HomesteadView::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.owner().toString())).toList());
    }

    @Override
    public synchronized CompletionStage<ClaimResult> applyTo(AccountId applicant, AccountId owner, Instant now) {
        Estate current = estate(owner);
        if (applicant.equals(owner) || current.members.containsKey(applicant)) return done(ClaimResult.ALREADY_MEMBER);
        if (current.plots.isEmpty() || !current.listed || !current.recruiting) return done(ClaimResult.NOT_RECRUITING);
        if (current.applications.getOrDefault(applicant, Instant.EPOCH).isAfter(now)) return done(ClaimResult.APPLIED);
        long activeForPlayer = estates.values().stream().filter(e -> e.applications.getOrDefault(applicant, Instant.EPOCH).isAfter(now)).count();
        long activeForHome = current.applications.values().stream().filter(expiry -> expiry.isAfter(now)).count();
        if (activeForPlayer >= 5 || activeForHome >= 32) return done(ClaimResult.APPLICATION_LIMIT);
        Estate next = current.copy();
        next.applications.entrySet().removeIf(e -> !e.getValue().isAfter(now));
        next.applications.put(applicant, now.plusSeconds(APPLICATION_SECONDS));
        commit(owner, next, applicant, "APPLICATION", "requested membership");
        return done(ClaimResult.APPLIED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> withdrawApplication(AccountId applicant, AccountId owner) {
        Estate current = estate(owner);
        if (!current.applications.containsKey(applicant)) return done(ClaimResult.NO_APPLICATION);
        Estate next = current.copy();
        next.applications.remove(applicant);
        commit(owner, next, applicant, "WITHDRAW_APPLICATION", "withdrew request");
        return done(ClaimResult.APPLICATION_REMOVED);
    }

    @Override
    public synchronized CompletionStage<List<ApplicationView>> applications(AccountId actor, AccountId owner, Instant now) {
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(List.of());
        return done(current.applications.entrySet().stream().filter(e -> e.getValue().isAfter(now))
            .sorted(Map.Entry.comparingByValue()).map(e -> new ApplicationView(e.getKey(), e.getValue())).toList());
    }

    @Override
    public synchronized CompletionStage<ClaimResult> reviewApplication(AccountId actor, AccountId owner, AccountId applicant, boolean accept, Instant now) {
        Estate current = estate(owner);
        if (!manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (current.plots.isEmpty() || !current.applications.getOrDefault(applicant, Instant.EPOCH).isAfter(now)) return done(ClaimResult.NO_APPLICATION);
        if (accept && (applicant.equals(owner) || current.members.containsKey(applicant))) return done(ClaimResult.ALREADY_MEMBER);
        if (accept && !hasMemberRoom(current, applicant, now)) return done(ClaimResult.MEMBER_LIMIT);
        Estate next = current.copy();
        next.applications.remove(applicant);
        if (accept) { next.members.put(applicant, ClaimRole.RESIDENT); next.invites.remove(applicant); }
        commit(owner, next, actor, accept ? "APPROVE_APPLICATION" : "DECLINE_APPLICATION", applicant.toString());
        return done(accept ? ClaimResult.JOINED : ClaimResult.APPLICATION_REMOVED);
    }

    @Override
    public synchronized CompletionStage<ClaimResult> cancelInvite(AccountId actor, AccountId owner, AccountId target) {
        Estate current = estate(owner);
        if (!actor.equals(target) && !manages(actor, owner, current)) return done(ClaimResult.NOT_OWNER);
        if (!current.invites.containsKey(target)) return done(ClaimResult.NO_INVITATION);
        Estate next = current.copy();
        next.invites.remove(target);
        commit(owner, next, actor, "CANCEL_INVITATION", target.toString());
        return done(ClaimResult.INVITATION_CANCELLED);
    }

    @Override
    public synchronized CompletionStage<List<AuditEntry>> history(AccountId actor, AccountId owner) {
        Estate current = estate(owner);
        return done(owner.equals(actor) || current.members.containsKey(actor) ? List.copyOf(current.history) : List.of());
    }

    private Estate estate(AccountId owner) {
        Estate existing = estates.get(owner);
        if (existing != null) return existing;
        Estate loaded = new Estate(starterCapacity);
        if (persistence != null) persistence.read(owner, yaml -> {
            if (yaml.contains(path + ".schema") && !yaml.isInt(path + ".schema")) throw new IllegalStateException("Invalid homestead schema");
            int schema = yaml.getInt(path + ".schema", 1);
            if (schema < 1 || schema > SCHEMA) throw new IllegalStateException("Unsupported homestead schema: " + schema);
            // A lower configuration limit stops new purchases; it must not erase purchased capacity.
            loaded.capacity = Math.max(starterCapacity, yaml.getInt(path + ".capacity", starterCapacity));
            loaded.name = yaml.getString(path + ".name", "Homestead");
            loaded.bulletin = yaml.getString(path + ".bulletin", "");
            loaded.listed = schema >= 3 && yaml.getBoolean(path + ".listed", false);
            loaded.recruiting = schema >= 3 && yaml.getBoolean(path + ".recruiting", false);
            requireSection(yaml, path + ".members");
            requireSection(yaml, path + ".chunks");
            readMembers(yaml.getConfigurationSection(path + ".members"), loaded.members, false);
            if (yaml.contains(path + ".default-flags")) {
                if (!yaml.isList(path + ".default-flags")) throw new IllegalStateException("Invalid default flags");
                loaded.flags.clear();
                readFlags(yaml.getStringList(path + ".default-flags"), loaded.flags);
            }
            ConfigurationSection invitations = yaml.getConfigurationSection(path + ".invitations");
            if (invitations != null) for (String id : invitations.getKeys(false)) {
                try { loaded.invites.put(AccountId.parse(id), Instant.parse(invitations.getString(id))); }
                catch (RuntimeException ignored) { /* Invalid invitations cannot grant access. */ }
            }
            ConfigurationSection applications = yaml.getConfigurationSection(path + ".applications");
            if (applications != null) for (String id : applications.getKeys(false)) {
                try { loaded.applications.put(AccountId.parse(id), Instant.parse(applications.getString(id))); }
                catch (RuntimeException ignored) { /* Invalid applications cannot grant access. */ }
            }
            for (Map<?, ?> record : yaml.getMapList(path + ".history")) {
                loaded.history.add(new AuditEntry(Instant.parse(Objects.toString(record.get("at"))),
                    AccountId.parse(Objects.toString(record.get("actor"))), Objects.toString(record.get("action")),
                    Objects.toString(record.get("detail"))));
            }
            while (loaded.history.size() > 100) loaded.history.removeFirst();
            ConfigurationSection chunks = yaml.getConfigurationSection(path + ".chunks");
            if (chunks != null) for (String rawId : chunks.getKeys(false)) {
                ConfigurationSection section = Objects.requireNonNull(chunks.getConfigurationSection(rawId));
                if (!section.isInt("x") || !section.isInt("z")) throw new IllegalStateException("Invalid saved claim coordinates");
                requireSection(section, "members");
                requireSection(section, "flag-overrides");
                ChunkKey chunk = new ChunkKey(UUID.fromString(section.getString("world")), section.getInt("x"), section.getInt("z"));
                Plot plot = new Plot(UUID.fromString(rawId), section.getString("name", ""), Map.of(), Map.of());
                if (schema < 3) {
                    Set<ClaimFlag> legacy = EnumSet.noneOf(ClaimFlag.class);
                    if (schema < 2) legacy.addAll(ClaimFlag.defaults());
                    readFlags(section.getStringList("flags"), legacy);
                    for (ClaimFlag flag : ClaimFlag.values()) {
                        if (legacy.contains(flag) != loaded.flags.contains(flag)) plot.overrides.put(flag, legacy.contains(flag));
                    }
                } else {
                    plot.type = PlotType.valueOf(Objects.requireNonNull(section.getString("type"), "Missing saved plot type"));
                    String steward = section.getString("steward");
                    if (steward != null) {
                        AccountId candidate = AccountId.parse(steward);
                        if (!plot.type.privateAccess() || !loaded.members.containsKey(candidate) || candidate.equals(owner)) {
                            throw new IllegalStateException("Invalid saved residential steward");
                        }
                        plot.steward = candidate;
                    }
                    ConfigurationSection overrides = section.getConfigurationSection("flag-overrides");
                    if (overrides != null) for (String name : overrides.getKeys(false)) {
                        ClaimFlag flag;
                        try { flag = ClaimFlag.valueOf(name); }
                        catch (IllegalArgumentException ignored) { continue; }
                        if (!overrides.isBoolean(name)) throw new IllegalStateException("Invalid plot flag override: " + name);
                        plot.overrides.put(flag, overrides.getBoolean(name));
                    }
                }
                readMembers(section.getConfigurationSection("members"), plot.members, true);
                if (loaded.plots.putIfAbsent(chunk, plot) != null) {
                    throw new IllegalStateException("Duplicate saved claim at " + chunk);
                }
            }
            return null;
        });
        clearEmptyEstate(loaded);
        for (ChunkKey chunk : loaded.plots.keySet()) {
            if (owners.containsKey(chunk) && !owner.equals(owners.get(chunk))) {
                throw new IllegalStateException("Conflicting saved claim ownership at " + chunk);
            }
        }
        estates.put(owner, loaded);
        loaded.plots.keySet().forEach(chunk -> owners.put(chunk, owner));
        return loaded;
    }

    private void commit(AccountId owner, Estate next, AccountId actor, String action, String detail) {
        clearEmptyEstate(next);
        next.history.add(new AuditEntry(Instant.now(), Objects.requireNonNull(actor), action, detail));
        while (next.history.size() > 100) next.history.removeFirst();
        if (persistence != null) persistence.update(owner, yaml -> {
            yaml.set(path, null);
            yaml.set(path + ".schema", SCHEMA);
            yaml.set(path + ".name", next.name);
            yaml.set(path + ".bulletin", next.bulletin);
            yaml.set(path + ".listed", next.listed);
            yaml.set(path + ".recruiting", next.recruiting);
            yaml.set(path + ".capacity", next.capacity);
            yaml.set(path + ".default-flags", next.flags.stream().map(Enum::name).sorted().toList());
            next.members.forEach((account, role) -> yaml.set(path + ".members." + account, role.name()));
            next.invites.forEach((account, expiry) -> yaml.set(path + ".invitations." + account, expiry.toString()));
            next.applications.forEach((account, expiry) -> yaml.set(path + ".applications." + account, expiry.toString()));
            yaml.set(path + ".history", next.history.stream().map(entry -> Map.of(
                "at", entry.at().toString(), "actor", entry.actor().toString(),
                "action", entry.action(), "detail", entry.detail())).toList());
            next.plots.forEach((chunk, plot) -> {
                String base = path + ".chunks." + plot.id;
                yaml.set(base + ".world", chunk.worldId().toString());
                yaml.set(base + ".x", chunk.x());
                yaml.set(base + ".z", chunk.z());
                yaml.set(base + ".name", plot.name);
                yaml.set(base + ".type", plot.type.name());
                if (plot.steward != null) yaml.set(base + ".steward", plot.steward.toString());
                plot.overrides.forEach((flag, enabled) -> yaml.set(base + ".flag-overrides." + flag.name(), enabled));
                plot.members.forEach((account, role) -> yaml.set(base + ".members." + account, role.name()));
            });
        });
        owners.entrySet().removeIf(entry -> entry.getValue().equals(owner));
        next.plots.keySet().forEach(chunk -> owners.put(chunk, owner));
        estates.put(owner, next);
    }

    private ClaimView view(AccountId owner, ChunkKey chunk) {
        Estate e = estate(owner);
        Plot plot = e.plots.get(chunk);
        Map<AccountId, ClaimRole> effective = plot.type.privateAccess() ? new HashMap<>() : new HashMap<>(e.members);
        effective.putAll(plot.members);
        if (plot.steward != null) effective.put(plot.steward, ClaimRole.RESIDENT);
        effective.remove(owner);
        return new ClaimView(plot.id, owner, chunk, plot.name.isBlank() ? e.name : plot.name, effective,
            effectiveFlags(e, plot), plot.type, plot.steward, plot.overrides, plot.members);
    }

    private boolean manages(AccountId actor, AccountId owner, Estate e) {
        return owner.equals(actor) || e.members.getOrDefault(actor, ClaimRole.NONE).allows(ClaimRole.MANAGE);
    }

    private boolean managesPlot(AccountId actor, AccountId owner, ChunkKey chunk) {
        Estate e = estate(owner);
        return role(owner, e, e.plots.get(chunk), actor).allows(ClaimRole.MANAGE);
    }

    private static ClaimRole role(AccountId owner, Estate estate, Plot plot, AccountId account) {
        if (owner.equals(account)) return ClaimRole.OWNER;
        if (plot.type.privateAccess() && account.equals(plot.steward)) return ClaimRole.RESIDENT;
        return plot.members.getOrDefault(account,
            plot.type.privateAccess() ? ClaimRole.NONE : estate.members.getOrDefault(account, ClaimRole.NONE));
    }

    private static Set<ClaimFlag> effectiveFlags(Estate estate, Plot plot) {
        Set<ClaimFlag> result = EnumSet.noneOf(ClaimFlag.class);
        result.addAll(estate.flags);
        plot.overrides.forEach((flag, enabled) -> changeFlag(result, flag, enabled));
        if (plot.type.privateAccess()) result.removeIf(flag -> flag.name().startsWith("PUBLIC_"));
        return result;
    }

    private static boolean controlsGuests(AccountId actor, AccountId owner, Plot plot) {
        return actor.equals(owner) || plot.type.privateAccess() && actor.equals(plot.steward);
    }

    private static void removeMember(Estate estate, AccountId member) {
        estate.members.remove(member);
        estate.invites.remove(member);
        estate.applications.remove(member);
        estate.plots.values().forEach(plot -> {
            plot.members.remove(member);
            if (member.equals(plot.steward)) {
                plot.steward = null;
                plot.members.clear();
            }
        });
    }

    private static void clearEmptyEstate(Estate estate) {
        if (!estate.plots.isEmpty()) return;
        estate.invites.clear();
        estate.applications.clear();
        estate.listed = false;
        estate.recruiting = false;
    }

    private boolean hasMemberRoom(Estate estate, AccountId target, Instant now) {
        long reservations = estate.invites.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(target) && !estate.members.containsKey(entry.getKey())
                && entry.getValue().isAfter(now)).count();
        return estate.members.size() + reservations < memberLimit;
    }

    private HomesteadView homeView(AccountId owner, Estate estate) {
        return new HomesteadView(owner, estate.name, estate.members, estate.flags, estate.plots.size(),
            estate.capacity, estate.bulletin, estate.listed, estate.recruiting);
    }

    private static String normalizeBulletin(String text) {
        if (text == null) return "";
        if (text.length() > 160 || text.codePoints().anyMatch(Character::isISOControl)
            || text.indexOf('§') >= 0 || text.indexOf('<') >= 0 || text.indexOf('>') >= 0
            || text.matches("(?is).*&[0-9a-fk-orx].*")) return null;
        return text.strip();
    }

    private static void readFlags(List<String> names, Set<ClaimFlag> target) {
        for (String name : names) {
            try { target.add(ClaimFlag.valueOf(name)); }
            catch (IllegalArgumentException ignored) { /* Removed flags remain disabled. */ }
        }
    }

    private static void readMembers(ConfigurationSection source, Map<AccountId, ClaimRole> target, boolean allowNone) {
        if (source == null) return;
        for (String id : source.getKeys(false)) {
            try {
                ClaimRole role = ClaimRole.valueOf(source.getString(id, "NONE"));
                if (role == ClaimRole.OWNER || !allowNone && role == ClaimRole.NONE) throw new IllegalArgumentException("Invalid saved role");
                target.put(AccountId.parse(id), role);
            } catch (IllegalArgumentException error) { throw new IllegalStateException("Invalid saved trust for " + id, error); }
        }
    }

    private static void requireSection(ConfigurationSection parent, String key) {
        if (parent.contains(key) && !parent.isConfigurationSection(key)) throw new IllegalStateException("Invalid saved section: " + key);
    }

    private static String normalizeName(String raw) {
        String result = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        return result.matches("[\\p{L}\\p{N} _'\\-]{3,24}") ? result : null;
    }

    private static void changeFlag(Set<ClaimFlag> flags, ClaimFlag flag, boolean enabled) {
        if (enabled) flags.add(flag); else flags.remove(flag);
    }

    private static Set<ChunkKey> inWorld(Estate estate, UUID world) {
        Set<ChunkKey> result = new HashSet<>();
        estate.plots.keySet().stream().filter(chunk -> chunk.worldId().equals(world)).forEach(result::add);
        return result;
    }

    private static List<ChunkKey> neighbors(ChunkKey chunk) {
        return List.of(new ChunkKey(chunk.worldId(), chunk.x() - 1, chunk.z()),
            new ChunkKey(chunk.worldId(), chunk.x() + 1, chunk.z()),
            new ChunkKey(chunk.worldId(), chunk.x(), chunk.z() - 1),
            new ChunkKey(chunk.worldId(), chunk.x(), chunk.z() + 1));
    }

    private static int components(Set<ChunkKey> chunks) {
        Set<ChunkKey> remaining = new HashSet<>(chunks);
        int count = 0;
        while (!remaining.isEmpty()) {
            count++;
            Deque<ChunkKey> queue = new ArrayDeque<>();
            ChunkKey start = remaining.iterator().next();
            remaining.remove(start);
            queue.add(start);
            while (!queue.isEmpty()) for (ChunkKey neighbor : neighbors(queue.remove())) {
                if (remaining.remove(neighbor)) queue.add(neighbor);
            }
        }
        return count;
    }

    private static <T> CompletionStage<T> done(T value) { return CompletableFuture.completedFuture(value); }

    private static final class Estate {
        private String name = "Homestead";
        private String bulletin = "";
        private boolean listed;
        private boolean recruiting;
        private int capacity;
        private final Map<AccountId, ClaimRole> members = new HashMap<>();
        private final Map<AccountId, Instant> invites = new HashMap<>();
        private final Map<AccountId, Instant> applications = new HashMap<>();
        private final List<AuditEntry> history = new ArrayList<>();
        private final Set<ClaimFlag> flags = EnumSet.copyOf(ClaimFlag.defaults());
        private final Map<ChunkKey, Plot> plots = new HashMap<>();
        private Estate(int capacity) { this.capacity = capacity; }
        private Estate copy() {
            Estate copy = new Estate(capacity);
            copy.name = name;
            copy.bulletin = bulletin;
            copy.listed = listed;
            copy.recruiting = recruiting;
            copy.members.putAll(members);
            copy.invites.putAll(invites);
            copy.applications.putAll(applications);
            copy.history.addAll(history);
            copy.flags.clear();
            copy.flags.addAll(flags);
            plots.forEach((key, plot) -> copy.plots.put(key, plot.copy()));
            return copy;
        }
    }

    private static final class Plot {
        private final UUID id;
        private String name;
        private final Map<AccountId, ClaimRole> members;
        private final Map<ClaimFlag, Boolean> overrides = new EnumMap<>(ClaimFlag.class);
        private PlotType type = PlotType.COMMONS;
        private AccountId steward;
        private Plot(UUID id, String name, Map<AccountId, ClaimRole> members, Map<ClaimFlag, Boolean> overrides) {
            this.id = id;
            this.name = name;
            this.members = new HashMap<>(members);
            this.overrides.putAll(overrides);
        }
        private Plot copy() {
            Plot copy = new Plot(id, name, members, overrides);
            copy.type = type;
            copy.steward = steward;
            return copy;
        }
    }
}
