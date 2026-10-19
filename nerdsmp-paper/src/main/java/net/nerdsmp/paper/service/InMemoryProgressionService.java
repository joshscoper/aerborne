package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProgressionService implements ProgressionService {
    private static final Duration RESPEC_COOLDOWN = Duration.ofDays(14);

    private final Map<AccountId, PlayerProgress> progress = new ConcurrentHashMap<>();
    private final Set<String> appliedActivities = ConcurrentHashMap.newKeySet();
    private final Set<AccountId> loadedAccounts = ConcurrentHashMap.newKeySet();
    private final PlayerDataStore persistence;
    private final String persistenceRoot;

    public InMemoryProgressionService() {
        this(null, "season-1");
    }

    public InMemoryProgressionService(PlayerDataStore persistence, String seasonId) {
        this.persistence = persistence;
        String safeSeason = seasonId.replaceAll("[^a-zA-Z0-9_-]", "_");
        persistenceRoot = "seasons." + safeSeason + ".progression";
    }

    @Override
    public synchronized CompletionStage<ProgressionSnapshot> snapshot(AccountId accountId) {
        ensureLoaded(accountId);
        return CompletableFuture.completedFuture(toSnapshot(progress.computeIfAbsent(accountId, ignored -> new PlayerProgress())));
    }

    @Override
    public synchronized CompletionStage<AwardResult> awardExperience(
        AccountId accountId,
        Skill skill,
        long experience,
        String activityFingerprint
    ) {
        ensureLoaded(accountId);
        PlayerProgress player = progress.computeIfAbsent(accountId, ignored -> new PlayerProgress());
        SkillProgress current = player.skills.get(skill);
        if (experience <= 0L || !appliedActivities.add(accountId + ":" + skill + ":" + activityFingerprint)) {
            return CompletableFuture.completedFuture(new AwardResult(false, current, false));
        }

        long capExperience = experienceForLevel(MAX_LEVEL);
        long nextExperience = Math.min(capExperience, Math.addExact(current.experience(), experience));
        int nextLevel = levelForExperience(nextExperience);
        SkillProgress next = new SkillProgress(nextExperience, nextLevel);
        player.skills.put(skill, next);
        persist(accountId, player);
        return CompletableFuture.completedFuture(new AwardResult(true, next, nextLevel > current.level()));
    }

    @Override
    public synchronized CompletionStage<MasteryResult> selectMastery(AccountId accountId, Skill skill) {
        ensureLoaded(accountId);
        PlayerProgress player = progress.computeIfAbsent(accountId, ignored -> new PlayerProgress());
        if (player.masteries.contains(skill)) {
            return CompletableFuture.completedFuture(MasteryResult.ALREADY_SELECTED);
        }
        if (player.skills.get(skill).level() < MASTERY_LEVEL) {
            return CompletableFuture.completedFuture(MasteryResult.LEVEL_TOO_LOW);
        }
        if (player.masteries.size() >= MAX_MASTERIES) {
            return CompletableFuture.completedFuture(MasteryResult.LIMIT_REACHED);
        }
        player.masteries.add(skill);
        persist(accountId, player);
        return CompletableFuture.completedFuture(MasteryResult.SELECTED);
    }

    @Override
    public synchronized CompletionStage<MasteryResult> removeMastery(AccountId accountId, Skill skill, Instant now) {
        ensureLoaded(accountId);
        PlayerProgress player = progress.computeIfAbsent(accountId, ignored -> new PlayerProgress());
        if (!player.masteries.contains(skill)) {
            return CompletableFuture.completedFuture(MasteryResult.NOT_SELECTED);
        }
        if (player.nextRespecAt.isAfter(now)) {
            return CompletableFuture.completedFuture(MasteryResult.RESPEC_ON_COOLDOWN);
        }
        player.masteries.remove(skill);
        player.nextRespecAt = now.plus(RESPEC_COOLDOWN);
        persist(accountId, player);
        return CompletableFuture.completedFuture(MasteryResult.REMOVED);
    }

    @Override
    public synchronized CompletionStage<TalentResult> unlockTalent(AccountId accountId, String talentId) {
        ensureLoaded(accountId);
        SkillTreeCatalog.TalentNode node = SkillTreeCatalog.node(talentId).orElse(null);
        if (node == null) {
            return CompletableFuture.completedFuture(TalentResult.UNKNOWN_TALENT);
        }
        PlayerProgress player = progress.computeIfAbsent(accountId, ignored -> new PlayerProgress());
        if (player.talents.contains(node.id())) {
            return CompletableFuture.completedFuture(TalentResult.ALREADY_UNLOCKED);
        }
        if (player.skills.get(node.skill()).level() < node.requiredLevel()) {
            return CompletableFuture.completedFuture(TalentResult.LEVEL_TOO_LOW);
        }
        if (node.previousId() != null && !player.talents.contains(node.previousId())) {
            return CompletableFuture.completedFuture(TalentResult.PREREQUISITE_MISSING);
        }
        int earned = SkillTreeCatalog.earnedPoints(player.skills.get(node.skill()).level());
        if (SkillTreeCatalog.spentPoints(player.talents, node.skill()) >= earned) {
            return CompletableFuture.completedFuture(TalentResult.NO_POINTS_AVAILABLE);
        }
        player.talents.add(node.id());
        persist(accountId, player);
        return CompletableFuture.completedFuture(TalentResult.UNLOCKED);
    }

    public static long experienceForLevel(int level) {
        return ProgressionService.experienceForLevel(level);
    }

    public static int levelForExperience(long experience) {
        return ProgressionService.levelForExperience(experience);
    }

    private ProgressionSnapshot toSnapshot(PlayerProgress player) {
        return new ProgressionSnapshot(player.skills, player.masteries, player.talents, player.nextRespecAt);
    }

    private void ensureLoaded(AccountId accountId) {
        if (persistence == null || !loadedAccounts.add(accountId)) {
            return;
        }
        PlayerProgress loaded = new PlayerProgress();
        persistence.read(accountId, yaml -> {
            for (Skill skill : Skill.values()) {
                long experience = Math.max(0L, yaml.getLong(persistenceRoot + ".skills."
                    + skill.name().toLowerCase() + ".experience", 0L));
                loaded.skills.put(skill, new SkillProgress(experience, levelForExperience(experience)));
            }
            yaml.getStringList(persistenceRoot + ".masteries").forEach(raw -> {
                try {
                    loaded.masteries.add(Skill.valueOf(raw.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore hand-edited unknown skills while retaining valid progress.
                }
            });
            loaded.talents.addAll(yaml.getStringList(persistenceRoot + ".talents"));
            String rawRespec = yaml.getString(persistenceRoot + ".next-respec-at", Instant.EPOCH.toString());
            try {
                loaded.nextRespecAt = Instant.parse(rawRespec);
            } catch (RuntimeException ignored) {
                loaded.nextRespecAt = Instant.EPOCH;
            }
            appliedActivities.addAll(yaml.getStringList(persistenceRoot + ".applied-activities"));
            return null;
        });
        progress.put(accountId, loaded);
    }

    private void persist(AccountId accountId, PlayerProgress player) {
        if (persistence == null) {
            return;
        }
        String prefix = accountId + ":";
        persistence.update(accountId, yaml -> {
            for (Map.Entry<Skill, SkillProgress> entry : player.skills.entrySet()) {
                yaml.set(persistenceRoot + ".skills." + entry.getKey().name().toLowerCase() + ".experience",
                    entry.getValue().experience());
            }
            yaml.set(persistenceRoot + ".masteries", player.masteries.stream().map(Enum::name).sorted().toList());
            yaml.set(persistenceRoot + ".talents", player.talents.stream().sorted().toList());
            yaml.set(persistenceRoot + ".next-respec-at", player.nextRespecAt.toString());
            yaml.set(persistenceRoot + ".applied-activities", appliedActivities.stream()
                .filter(key -> key.startsWith(prefix)).sorted().toList());
        });
    }

    private static final class PlayerProgress {
        private final EnumMap<Skill, SkillProgress> skills = new EnumMap<>(Skill.class);
        private final EnumSet<Skill> masteries = EnumSet.noneOf(Skill.class);
        private final Set<String> talents = new java.util.HashSet<>();
        private Instant nextRespecAt = Instant.EPOCH;

        private PlayerProgress() {
            for (Skill skill : Skill.values()) {
                skills.put(skill, new SkillProgress(0L, 0));
            }
        }
    }
}
