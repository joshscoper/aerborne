package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Additive, seasonal resource unlocks. Reading a legacy island neither migrates its
 * document nor changes its size, chapter receipts, talents, name or membership.
 */
public final class IslandResourceProgression {
    public static final int SCHEMA_VERSION = 1;
    private final PlayerDataStore data;
    private final IslandService islands;
    private final IslandDevelopment development;
    private final String path;
    private final Map<AccountId, Saved> saved = new HashMap<>();

    public IslandResourceProgression(PlayerDataStore data, IslandService islands,
                                     IslandDevelopment development, String season) {
        if (season == null || !season.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid island resource season");
        this.data = Objects.requireNonNull(data);
        this.islands = Objects.requireNonNull(islands);
        this.development = Objects.requireNonNull(development);
        path = "seasons." + season + ".island-resources";
        // Bad saved unlocks must fail visibly at startup, not be silently reset.
        data.knownAccounts().stream().filter(owner -> islands.owned(owner).isPresent()).forEach(this::state);
    }

    public synchronized int effectiveStage(AccountId owner) {
        return Math.max(requireIsland(owner).tier(), state(owner).stage());
    }

    public synchronized Snapshot snapshot(AccountId owner) {
        return new Snapshot(effectiveStage(owner), development.progress(owner).level(), state(owner).proofs());
    }

    /**
     * Certifies already-earned skills; no items, XP, currency or talent points are
     * spent. Each co-op member may contribute different requirements. Publication
     * follows the atomic document write, and receipts survive that member leaving.
     */
    public synchronized boolean contribute(AccountId actor, AccountId owner, int expectedStage,
                                             ProgressionService.ProgressionSnapshot skills) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(skills);
        IslandService.Island island = requireIsland(owner);
        if (!island.member(actor)) throw new IllegalArgumentException("You no longer belong to this island.");
        int stage = effectiveStage(owner);
        if (stage != expectedStage)
            throw new IllegalArgumentException("The island's resource stage changed. Open the journal again.");
        if (stage < IslandResourceStage.SANCTUARY.index())
            throw new IllegalArgumentException("Complete the current island chapter first.");
        if (stage == IslandResourceStage.AETHERWORKS.index()) return false;

        Saved before = state(owner);
        IslandResourceStage target = IslandResourceStage.at(stage + 1);
        EnumMap<Proof, AccountId> proofs = new EnumMap<>(Proof.class);
        proofs.putAll(before.proofs());
        target.requirements().forEach((skill, requiredLevel) -> {
            ProgressionService.SkillProgress trained = skills.skills().get(skill);
            // XP is authoritative. A malformed snapshot cannot certify a forged level.
            if (trained != null && trained.experience() >= ProgressionService.experienceForLevel(requiredLevel)
                && trained.level() >= requiredLevel)
                proofs.putIfAbsent(Proof.forRequirement(target, skill), actor);
        });
        boolean complete = development.progress(owner).level() >= target.islandLevel()
            && target.requirements().keySet().stream().allMatch(skill -> proofs.containsKey(Proof.forRequirement(target, skill)));
        if (!complete && proofs.equals(before.proofs())) return false;

        Saved next = new Saved(stage + (complete ? 1 : 0), proofs);
        data.update(owner, yaml -> {
            // Compare inside the document's write lock too: another service or
            // staff edit must not let an old page replace a newer saved stage.
            if (!readSaved(owner, yaml).equals(before)) {
                saved.remove(owner);
                throw new IllegalArgumentException("The island's resource stage changed. Open the journal again.");
            }
            yaml.set(path, null);
            yaml.set(path + ".schema", SCHEMA_VERSION);
            yaml.set(path + ".stage", next.stage());
            next.proofs().forEach((proof, contributor) -> yaml.set(path + ".proofs." + proof.id(), contributor.toString()));
        });
        saved.put(owner, next);
        return complete;
    }

    private IslandService.Island requireIsland(AccountId owner) {
        return islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
    }

    private Saved state(AccountId owner) {
        requireIsland(owner);
        return saved.computeIfAbsent(owner, this::load);
    }

    private Saved load(AccountId owner) {
        return data.read(owner, yaml -> readSaved(owner, yaml));
    }

    private Saved readSaved(AccountId owner, YamlConfiguration yaml) {
        if (!yaml.contains(path)) return new Saved(0, Map.of());
        try {
            ConfigurationSection section = yaml.getConfigurationSection(path);
            if (section == null || !section.isInt("schema") || section.getInt("schema") != SCHEMA_VERSION
                || !section.isInt("stage")) throw new IllegalArgumentException("Invalid resource schema");
            for (String key : section.getKeys(false))
                if (!key.equals("schema") && !key.equals("stage") && !key.equals("proofs"))
                    throw new IllegalArgumentException("Unknown resource field: " + key);
            EnumMap<Proof, AccountId> proofs = new EnumMap<>(Proof.class);
            if (section.contains("proofs")) {
                ConfigurationSection entries = section.getConfigurationSection("proofs");
                if (entries == null) throw new IllegalArgumentException("Invalid resource proofs");
                for (String key : entries.getKeys(false)) {
                    if (!entries.isString(key)) throw new IllegalArgumentException("Invalid resource contributor");
                    String rawContributor = entries.getString(key, "");
                    AccountId contributor = AccountId.parse(rawContributor);
                    if (!rawContributor.equalsIgnoreCase(contributor.toString()))
                        throw new IllegalArgumentException("Invalid resource contributor UUID");
                    proofs.put(Proof.parse(key), contributor);
                }
            }
            return new Saved(section.getInt("stage"), proofs);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Cannot load island resources for " + owner + "; refusing to replace them", error);
        }
    }

    public enum Proof {
        FOUNDRY_MINING(IslandResourceStage.FOUNDRY, Skill.MINING),
        FOUNDRY_SMITHING(IslandResourceStage.FOUNDRY, Skill.SMITHING),
        FOUNDRY_EXCAVATION(IslandResourceStage.FOUNDRY, Skill.EXCAVATION),
        AETHERWORKS_MINING(IslandResourceStage.AETHERWORKS, Skill.MINING),
        AETHERWORKS_ALCHEMY(IslandResourceStage.AETHERWORKS, Skill.ALCHEMY),
        AETHERWORKS_FARMING(IslandResourceStage.AETHERWORKS, Skill.FARMING);

        private final IslandResourceStage stage;
        private final Skill skill;
        Proof(IslandResourceStage stage, Skill skill) { this.stage = stage; this.skill = skill; }
        public IslandResourceStage stage() { return stage; }
        public Skill skill() { return skill; }
        public int requiredLevel() { return stage.requirements().get(skill); }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public static Proof forRequirement(IslandResourceStage stage, Skill skill) {
            for (Proof proof : values()) if (proof.stage == stage && proof.skill == skill) return proof;
            throw new IllegalArgumentException("No resource proof for " + stage + "/" + skill);
        }
        public static Proof parse(String id) {
            for (Proof proof : values()) if (proof.id().equals(id)) return proof;
            throw new IllegalArgumentException("Unknown resource proof: " + id);
        }
    }

    public record Snapshot(int stage, int islandLevel, Map<Proof, AccountId> proofs) {
        public Snapshot {
            IslandResourceStage.at(stage);
            if (islandLevel < 1 || islandLevel > IslandDevelopment.MAX_LEVEL)
                throw new IllegalArgumentException("Invalid island level");
            proofs = Map.copyOf(proofs);
        }
        public IslandResourceStage resourceStage() { return IslandResourceStage.at(stage); }
        public Optional<IslandResourceStage> nextStage() { return resourceStage().next(); }
        public boolean certified(Skill skill) {
            return nextStage().filter(target -> target.index() >= IslandResourceStage.FOUNDRY.index()
                && target.requirements().containsKey(skill))
                .map(target -> proofs.containsKey(Proof.forRequirement(target, skill))).orElse(false);
        }
    }

    private record Saved(int stage, Map<Proof, AccountId> proofs) {
        private Saved {
            IslandResourceStage.at(stage);
            proofs = Map.copyOf(proofs);
            for (Proof proof : proofs.keySet())
                if (proof.stage().index() > stage + 1)
                    throw new IllegalArgumentException("Resource proof belongs to a locked stage");
            // Advanced completed stages must have their immutable skill receipts.
            for (Proof proof : Proof.values())
                if (proof.stage().index() <= stage && !proofs.containsKey(proof))
                    throw new IllegalArgumentException("Completed resource stage is missing a skill receipt");
        }
    }
}
