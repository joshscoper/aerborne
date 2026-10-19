package net.nerdsmp.paper.achievement;

import java.util.Locale;

public enum AchievementMetric {
    ADVENTURER_LEVEL,
    HIGHEST_SKILL_LEVEL,
    TOTAL_SKILL_XP,
    ITEMS_CRAFTED,
    MOBS_DEFEATED,
    ELITES_DEFEATED,
    BOSSES_DEFEATED,
    FISH_CAUGHT,
    FISHING_SPECIES,
    EXCAVATION_FINDS,
    ARCHIVE_ITEMS,
    COOKING_LEVEL,
    MEALS_PREPARED,
    ISLAND_LEVEL,
    ISLAND_TALENTS,
    RESOURCE_STAGE,
    ACTIVE_SET_BONUSES;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AchievementMetric fromId(String raw) {
        try {
            return valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
