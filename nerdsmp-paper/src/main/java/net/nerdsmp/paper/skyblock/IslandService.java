package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;
import java.time.Instant;
import java.util.*;

/** Canonical-account island ownership. Each mutation is persisted before it becomes visible. */
public final class IslandService {
    public static final int TEAM_LIMIT = 6;
    public static final long INVITE_SECONDS = 300;
    private final PlayerDataStore data;
    private final String path;
    private final Map<AccountId, Island> islands = new HashMap<>();
    private final Map<Integer, AccountId> slots = new HashMap<>();
    private final Map<AccountId, Set<AccountId>> memberIndex = new HashMap<>();
    private final Map<AccountId, Set<AccountId>> inviteIndex = new HashMap<>();
    private List<Island> directoryCache;
    private int nextSlot;

    public IslandService(PlayerDataStore data, String season) {
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid island season");
        this.data = Objects.requireNonNull(data);
        path = "seasons." + season + ".skyblock";
        for (AccountId owner : data.knownAccounts()) {
            Island island = data.read(owner, yaml -> {
                if (yaml.contains(path) && !yaml.isConfigurationSection(path)) throw new IllegalStateException("Malformed island record for " + owner);
                return load(owner, yaml.getConfigurationSection(path));
            });
            if (island == null) continue;
            if (slots.putIfAbsent(island.slot(), owner) != null) throw new IllegalStateException("Duplicate island slot");
            islands.put(owner, island);
            index(island);
        }
        advanceSlot();
    }

    public synchronized Optional<Island> owned(AccountId owner) { return Optional.ofNullable(islands.get(owner)); }
    public synchronized Optional<Island> at(int x, int z) {
        AccountId owner = slots.get(IslandGrid.slotAt(x, z));
        Island island = islands.get(owner);
        return island != null && IslandGrid.contains(island.slot(), island.tier(), x, z)
            ? Optional.of(island) : Optional.empty();
    }
    public synchronized List<Island> homes(AccountId account) {
        return memberIndex.getOrDefault(account, Set.of()).stream().map(islands::get)
            .sorted(Comparator.comparingInt(Island::slot)).toList();
    }
    public synchronized List<Island> directory() {
        if (directoryCache == null) directoryCache = islands.values().stream().filter(Island::visitors)
            .sorted(Comparator.comparingInt(Island::slot)).toList();
        return directoryCache;
    }
    public synchronized Island create(AccountId owner) {
        Island existing = islands.get(owner);
        if (existing != null) return existing;
        int slot = nextSlot;
        if (slot == IslandGrid.LIMIT) throw new IllegalStateException("Island layout is full");
        Island island = new Island(owner, UUID.randomUUID(), slot, 0, "Skyhold", false, Set.of(), Map.of(), Map.of(), IslandShowcase.HEARTH);
        commit(island);
        return island;
    }
    public synchronized void rename(AccountId actor, String name) {
        if (name == null || !name.trim().matches("[\\p{L}\\p{N} _'-]{3,32}")) throw new IllegalArgumentException("Use 3-32 letters, numbers or spaces.");
        Island i = require(actor);
        if (i.name().equals(name.trim())) return;
        commit(new Island(i.owner(), i.id(), i.slot(), i.tier(), name.trim(), i.visitors(), i.members(), i.invites(), i.proofs(), i.showcase()));
    }
    public synchronized void visitors(AccountId actor, boolean enabled) {
        Island i = require(actor);
        if (i.visitors() == enabled) return;
        commit(new Island(i.owner(), i.id(), i.slot(), i.tier(), i.name(), enabled, i.members(), i.invites(), i.proofs(), i.showcase()));
    }
    public synchronized void showcase(AccountId owner, IslandShowcase showcase) {
        Island i = require(owner);
        if (!showcase.unlocked(i.tier())) throw new IllegalArgumentException("Complete chapter " + showcase.chapter() + " to unlock this display.");
        if (i.showcase() == showcase) return;
        commit(new Island(i.owner(), i.id(), i.slot(), i.tier(), i.name(), i.visitors(), i.members(), i.invites(), i.proofs(), showcase));
    }
    public synchronized void invite(AccountId owner, AccountId target, Instant now) {
        Island i = require(owner);
        if (i.member(target)) throw new IllegalArgumentException("That player already belongs to this island.");
        Map<AccountId, Instant> invites = activeInvites(i, now);
        if (invites.containsKey(target)) return; // Replays do not extend an invitation.
        if (i.members().size() + invites.size() >= TEAM_LIMIT - 1) throw new IllegalArgumentException("Your co-op and invitations fill all six places.");
        invites.put(target, now.plusSeconds(INVITE_SECONDS));
        commit(copyTeam(i, i.members(), invites));
    }
    public synchronized void accept(AccountId member, AccountId owner, Instant now) {
        Island i = require(owner);
        if (i.member(member)) return;
        Map<AccountId, Instant> invites = activeInvites(i, now);
        if (!invites.containsKey(member)) throw new IllegalArgumentException("That invitation has expired or was cancelled.");
        if (i.members().size() >= TEAM_LIMIT - 1) throw new IllegalArgumentException("That co-op is full.");
        // A player can keep their own home and join up to three other co-ops.
        long joined = homes(member).stream().filter(home -> !home.owner().equals(member)).count();
        if (joined >= 3) throw new IllegalArgumentException("Leave a co-op before joining another (limit: three).");
        Set<AccountId> members = new HashSet<>(i.members()); members.add(member); invites.remove(member);
        commit(copyTeam(i, members, invites));
    }
    public synchronized void remove(AccountId actor, AccountId owner, AccountId target) {
        Island i = require(owner);
        if ((!actor.equals(owner) && !actor.equals(target)) || target.equals(owner)) throw new IllegalArgumentException("Only the owner can remove another member.");
        if (!i.members().contains(target) && !i.invites().containsKey(target)) return;
        Set<AccountId> members = new HashSet<>(i.members()); members.remove(target);
        Map<AccountId, Instant> invites = new HashMap<>(i.invites()); invites.remove(target);
        commit(copyTeam(i, members, invites));
    }
    public synchronized List<Island> invitations(AccountId target, Instant now) {
        return inviteIndex.getOrDefault(target, Set.of()).stream().map(islands::get)
            .filter(i -> i.invites().getOrDefault(target, Instant.MIN).isAfter(now))
            .sorted(Comparator.comparingInt(Island::slot)).toList();
    }
    public synchronized boolean advance(AccountId actor, AccountId owner, int expectedTier,
                                         net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot skills) {
        Island i = require(owner);
        if (!i.member(actor)) throw new IllegalArgumentException("You no longer belong to this island.");
        if (i.tier() != expectedTier) throw new IllegalArgumentException("The island chapter changed. Open the journal again.");
        if (i.tier() >= IslandChapter.values().length) return false;
        IslandChapter chapter = IslandChapter.values()[i.tier()];
        // Each teammate can certify the skills they have trained; proofs combine across the co-op.
        Map<String, AccountId> proofs = new HashMap<>(i.proofs());
        chapter.requirements().forEach((skill, level) -> {
            if (skills.skills().getOrDefault(skill, new net.nerdsmp.api.service.ProgressionService.SkillProgress(0, 0)).level() >= level)
                proofs.putIfAbsent(i.tier() + ":" + skill.name(), actor);
        });
        boolean complete = chapter.requirements().keySet().stream().allMatch(skill -> proofs.containsKey(i.tier() + ":" + skill.name()));
        if (!complete && proofs.equals(i.proofs())) return false;
        commit(new Island(i.owner(), i.id(), i.slot(), i.tier() + (complete ? 1 : 0), i.name(), i.visitors(), i.members(), i.invites(), proofs, i.showcase()));
        return complete;
    }
    private Island require(AccountId owner) {
        Island i = islands.get(owner);
        if (i == null) throw new IllegalArgumentException("No island found. Use /island create.");
        return i;
    }
    private Map<AccountId, Instant> activeInvites(Island i, Instant now) {
        Map<AccountId, Instant> result = new HashMap<>();
        i.invites().forEach((id, expires) -> { if (expires.isAfter(now)) result.put(id, expires); });
        return result;
    }
    private Island copyTeam(Island i, Set<AccountId> members, Map<AccountId, Instant> invites) {
        return new Island(i.owner(), i.id(), i.slot(), i.tier(), i.name(), i.visitors(), members, invites, i.proofs(), i.showcase());
    }
    private void commit(Island i) {
        data.update(i.owner(), yaml -> {
            yaml.set(path, null);
            yaml.set(path + ".schema", 1); yaml.set(path + ".layout", 1);
            yaml.set(path + ".id", i.id().toString()); yaml.set(path + ".slot", i.slot());
            yaml.set(path + ".tier", i.tier()); yaml.set(path + ".name", i.name()); yaml.set(path + ".visitors", i.visitors());
            yaml.set(path + ".showcase", i.showcase().id());
            yaml.set(path + ".members", i.members().stream().map(AccountId::toString).sorted().toList());
            i.invites().forEach((id, expiry) -> yaml.set(path + ".invites." + id, expiry.toString()));
            i.proofs().forEach((key, actor) -> yaml.set(path + ".proofs." + key, actor.toString()));
        });
        // Index/cache publication follows durable commit; failed writes retain old permissions.
        Island previous = islands.put(i.owner(), i);
        if (previous != null) unindex(previous);
        index(i);
        slots.put(i.slot(), i.owner()); advanceSlot(); directoryCache = null;
    }
    private void advanceSlot() { while (nextSlot < IslandGrid.LIMIT && slots.containsKey(nextSlot)) nextSlot++; }
    private void index(Island i) {
        memberIndex.computeIfAbsent(i.owner(), ignored -> new HashSet<>()).add(i.owner());
        i.members().forEach(member -> memberIndex.computeIfAbsent(member, ignored -> new HashSet<>()).add(i.owner()));
        i.invites().keySet().forEach(member -> inviteIndex.computeIfAbsent(member, ignored -> new HashSet<>()).add(i.owner()));
    }
    private void unindex(Island i) {
        removeIndex(memberIndex, i.owner(), i.owner());
        i.members().forEach(member -> removeIndex(memberIndex, member, i.owner()));
        i.invites().keySet().forEach(member -> removeIndex(inviteIndex, member, i.owner()));
    }
    private void removeIndex(Map<AccountId, Set<AccountId>> index, AccountId member, AccountId owner) {
        Set<AccountId> owners = index.get(member);
        if (owners != null) { owners.remove(owner); if (owners.isEmpty()) index.remove(member); }
    }
    private Island load(AccountId owner, ConfigurationSection s) {
        if (s == null) return null;
        try {
            if (!s.isInt("schema") || s.getInt("schema") != 1 || s.getInt("layout") != 1
                || !s.isInt("slot") || !s.isInt("tier") || !s.isBoolean("visitors") || !s.isList("members")) throw new IllegalArgumentException("Invalid island schema");
            int slot = s.getInt("slot"), tier = s.getInt("tier");
            IslandGrid.chunkX(slot);
            if (tier < 0 || tier > IslandChapter.values().length) throw new IllegalArgumentException("Invalid island tier");
            String name = s.getString("name", "");
            if (!name.matches("[\\p{L}\\p{N} _'-]{3,32}")) throw new IllegalArgumentException("Invalid island name");
            Set<AccountId> members = new HashSet<>();
            for (Object raw : s.getList("members")) {
                if (!(raw instanceof String str) || !members.add(AccountId.parse(str))) throw new IllegalArgumentException("Invalid member");
            }
            Map<AccountId, Instant> invites = new HashMap<>();
            ConfigurationSection invitationSection = section(s, "invites");
            if (invitationSection != null) for (String key : invitationSection.getKeys(false)) invites.put(AccountId.parse(key), Instant.parse(invitationSection.getString(key, "")));
            Map<String, AccountId> proofs = new HashMap<>();
            ConfigurationSection proofSection = section(s, "proofs");
            if (proofSection != null) for (String key : proofSection.getKeys(false)) {
                boolean valid = false;
                for (IslandChapter chapter : IslandChapter.values()) for (var skill : chapter.requirements().keySet()) {
                    if (key.equals(chapter.ordinal() + ":" + skill.name())) valid = true;
                }
                if (!valid) throw new IllegalArgumentException("Invalid island proof");
                proofs.put(key, AccountId.parse(proofSection.getString(key, "")));
            }
            if (members.contains(owner) || members.size() > TEAM_LIMIT - 1 || invites.size() > TEAM_LIMIT - 1
                || invites.containsKey(owner) || members.stream().anyMatch(invites::containsKey)) throw new IllegalArgumentException("Invalid island team");
            if (s.contains("showcase") && !s.isString("showcase")) throw new IllegalArgumentException("Invalid showcase");
            IslandShowcase showcase = IslandShowcase.parse(s.getString("showcase", "hearth"));
            if (!showcase.unlocked(tier)) throw new IllegalArgumentException("Locked showcase in island save");
            return new Island(owner, UUID.fromString(s.getString("id", "")), slot, tier, name, s.getBoolean("visitors"), members, invites, proofs, showcase);
        } catch (RuntimeException error) { throw new IllegalStateException("Cannot load island for " + owner + "; refusing to replace it", error); }
    }
    private static ConfigurationSection section(ConfigurationSection s, String key) {
        if (s.contains(key) && !s.isConfigurationSection(key)) throw new IllegalArgumentException("Invalid " + key);
        return s.getConfigurationSection(key);
    }
    public record Island(AccountId owner, UUID id, int slot, int tier, String name, boolean visitors,
                         Set<AccountId> members, Map<AccountId, Instant> invites, Map<String, AccountId> proofs, IslandShowcase showcase) {
        public Island { members = Set.copyOf(members); invites = Map.copyOf(invites); proofs = Map.copyOf(proofs); }
        public boolean member(AccountId actor) { return actor != null && (owner.equals(actor) || members.contains(actor)); }
        public boolean canEnter(AccountId actor) { return visitors || member(actor); }
        public int width() { return (tier * 2 + 3) * 16; }
    }
}
