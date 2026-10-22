package net.nerdsmp.paper.item;

import net.nerdsmp.api.service.ProgressionService;

import java.util.Locale;
import java.util.Optional;

/**
 * Experience bottles as skill lamps: right-click one, choose a skill, and it grants a share of the
 * experience that skill needs for its next level, so a bottle stays worth using at any level. Plain
 * bottles give the least; Greater and Grand bottles drop from harder content. Free of Bukkit.
 */
public final class SkillLamps {
    /** The three bottle grades, by the share of the next level's XP they grant and their floor. */
    public enum Grade {
        PLAIN("Experience Bottle", .10, 40),
        GREATER("Greater Experience Bottle", .25, 100),
        GRAND("Grand Experience Bottle", .60, 250);

        private final String title;
        private final double share;
        private final long floor;

        Grade(String title, double share, long floor) {
            this.title = title;
            this.share = share;
            this.floor = floor;
        }

        public String title() { return title; }
        public double share() { return share; }
        public String key() { return name().toLowerCase(Locale.ROOT); }

        public static Optional<Grade> parse(String raw) {
            if (raw == null) return Optional.empty();
            for (Grade grade : values()) if (grade.key().equalsIgnoreCase(raw)) return Optional.of(grade);
            return Optional.empty();
        }
    }

    private SkillLamps() { }

    /** Skill XP one bottle grants at a skill level; zero once the skill is maxed. */
    public static long experience(Grade grade, int level) {
        if (level >= ProgressionService.MAX_LEVEL) return 0;
        long toNext = ProgressionService.experienceForLevel(level + 1) - ProgressionService.experienceForLevel(level);
        return Math.max(grade.floor, Math.round(toNext * grade.share));
    }
}
