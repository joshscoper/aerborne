package net.nerdsmp.api.model;

import java.util.Arrays;
import java.util.Map;

public final class AdventurerProgression {
    public static final int MAX_LEVEL = 100;
    private static final long EXPERIENCE_FACTOR = 200L;

    private AdventurerProgression() {
    }

    public static Progress fromSkills(Map<Skill, ? extends SkillExperience> skills) {
        long experience = skills.values().stream().mapToLong(SkillExperience::experience).sum();
        return fromExperience(experience);
    }

    public static Progress fromExperience(long rawExperience) {
        long experience = Math.max(0L, rawExperience);
        int level = levelForExperience(experience);
        long floor = experienceForLevel(level);
        long ceiling = level >= MAX_LEVEL ? floor : experienceForLevel(level + 1);
        return new Progress(experience, level, floor, ceiling, rankForLevel(level));
    }

    public static long experienceForLevel(int level) {
        int bounded = Math.max(1, Math.min(MAX_LEVEL, level));
        long completedLevels = bounded - 1L;
        return EXPERIENCE_FACTOR * completedLevels * completedLevels;
    }

    public static int levelForExperience(long experience) {
        if (experience <= 0L) {
            return 1;
        }
        return Math.min(MAX_LEVEL, 1 + (int) Math.floor(Math.sqrt(experience / (double) EXPERIENCE_FACTOR)));
    }

    public static Rank rankForLevel(int level) {
        return Arrays.stream(Rank.values())
            .filter(rank -> level >= rank.requiredLevel())
            .reduce((left, right) -> right)
            .orElse(Rank.WANDERER);
    }

    public static double rankStat(int level, String stat) {
        return Arrays.stream(Rank.values())
            .filter(rank -> level >= rank.requiredLevel())
            .mapToDouble(rank -> rank.bonuses().getOrDefault(stat, 0.0D))
            .sum();
    }

    public interface SkillExperience {
        long experience();
    }

    public record Progress(long experience, int level, long levelFloor, long nextLevelAt, Rank rank) {
        public long experienceIntoLevel() {
            return Math.max(0L, experience - levelFloor);
        }

        public long experienceNeeded() {
            return level >= MAX_LEVEL ? 0L : Math.max(1L, nextLevelAt - levelFloor);
        }
    }

    public enum Rank {
        WANDERER(1, "Wanderer", "The road starts here.", Map.of()),
        SCOUT(10, "Scout", "+2% skill XP.", Map.of("skill-xp-percent", 2.0D)),
        PATHFINDER(20, "Pathfinder", "+3% Marks from skill work.", Map.of("job-marks-percent", 3.0D)),
        RANGER(35, "Ranger", "+3 Defense.", Map.of("defense", 3.0D)),
        VANGUARD(50, "Vanguard", "+3% custom damage.", Map.of("damage-percent", 3.0D)),
        CHAMPION(70, "Champion", "+3% skill XP and work Marks.",
            Map.of("skill-xp-percent", 3.0D, "job-marks-percent", 3.0D)),
        LEGEND(90, "Legend", "+5% geode and fishing treasure odds.",
            Map.of("geode-find-percent", 5.0D, "treasure-find-percent", 5.0D)),
        MYTHIC(100, "Mythic", "+5% custom damage, skill XP, and work Marks.",
            Map.of("damage-percent", 5.0D, "skill-xp-percent", 5.0D, "job-marks-percent", 5.0D));

        private final int requiredLevel;
        private final String displayName;
        private final String perk;
        private final Map<String, Double> bonuses;

        Rank(int requiredLevel, String displayName, String perk, Map<String, Double> bonuses) {
            this.requiredLevel = requiredLevel;
            this.displayName = displayName;
            this.perk = perk;
            this.bonuses = Map.copyOf(bonuses);
        }

        public int requiredLevel() {
            return requiredLevel;
        }

        public String displayName() {
            return displayName;
        }

        public String perk() {
            return perk;
        }

        public Map<String, Double> bonuses() {
            return bonuses;
        }
    }
}
