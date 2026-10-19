package net.nerdsmp.api.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public interface ProgressionService {
    int MAX_LEVEL = 50;
    int MASTERY_LEVEL = 25;
    int MAX_MASTERIES = 2;

    CompletionStage<ProgressionSnapshot> snapshot(AccountId accountId);

    CompletionStage<AwardResult> awardExperience(
        AccountId accountId,
        Skill skill,
        long experience,
        String activityFingerprint
    );

    CompletionStage<MasteryResult> selectMastery(AccountId accountId, Skill skill);

    CompletionStage<MasteryResult> removeMastery(AccountId accountId, Skill skill, Instant now);

    CompletionStage<TalentResult> unlockTalent(AccountId accountId, String talentId);

    static long experienceForLevel(int level) {
        int bounded = Math.max(0, Math.min(MAX_LEVEL, level));
        return 100L * bounded * bounded;
    }

    static int levelForExperience(long experience) {
        if (experience <= 0L) {
            return 0;
        }
        return Math.min(MAX_LEVEL, (int) Math.floor(Math.sqrt(experience / 100.0D)));
    }

    record SkillProgress(long experience, int level) implements AdventurerProgression.SkillExperience {
    }

    record ProgressionSnapshot(
        Map<Skill, SkillProgress> skills,
        Set<Skill> masteries,
        Set<String> talents,
        Instant nextRespecAt
    ) {
        public ProgressionSnapshot {
            skills = Map.copyOf(skills);
            masteries = Set.copyOf(masteries);
            talents = Set.copyOf(talents);
        }

        public boolean isFresh() {
            return skills.values().stream().allMatch(progress -> progress.experience() == 0L)
                && masteries.isEmpty() && talents.isEmpty();
        }

        public AdventurerProgression.Progress adventurer() {
            return AdventurerProgression.fromSkills(skills);
        }

        public int talentPointsEarned(Skill skill) {
            return SkillTreeCatalog.earnedPoints(skills.getOrDefault(skill, new SkillProgress(0L, 0)).level());
        }

        public int talentPointsSpent(Skill skill) {
            return SkillTreeCatalog.spentPoints(talents, skill);
        }

        public int talentPointsAvailable(Skill skill) {
            return Math.max(0, talentPointsEarned(skill) - talentPointsSpent(skill));
        }

        public double talentStat(Skill skill, String stat) {
            return SkillTreeCatalog.stat(talents, skill, stat);
        }

        public double adventurerStat(String stat) {
            return AdventurerProgression.rankStat(adventurer().level(), stat);
        }

        public static ProgressionSnapshot empty() {
            EnumMap<Skill, SkillProgress> skills = new EnumMap<>(Skill.class);
            for (Skill skill : Skill.values()) {
                skills.put(skill, new SkillProgress(0L, 0));
            }
            return new ProgressionSnapshot(skills, Set.of(), Set.of(), Instant.EPOCH);
        }
    }

    record AwardResult(boolean awarded, SkillProgress progress, boolean leveledUp) {
    }

    enum MasteryResult {
        SELECTED,
        REMOVED,
        ALREADY_SELECTED,
        NOT_SELECTED,
        LEVEL_TOO_LOW,
        LIMIT_REACHED,
        RESPEC_ON_COOLDOWN
    }

    enum TalentResult {
        UNLOCKED,
        UNKNOWN_TALENT,
        ALREADY_UNLOCKED,
        LEVEL_TOO_LOW,
        PREREQUISITE_MISSING,
        NO_POINTS_AVAILABLE
    }
}
