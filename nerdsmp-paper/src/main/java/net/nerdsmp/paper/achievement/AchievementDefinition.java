package net.nerdsmp.paper.achievement;

import org.bukkit.Material;

public record AchievementDefinition(
    String id,
    String title,
    String description,
    String category,
    AchievementMetric metric,
    long target,
    int points,
    long markReward,
    Material icon
) {
    public AchievementDefinition {
        if (!id.matches("[a-z0-9_]+") || title.isBlank() || description.isBlank() || category.isBlank()
            || metric == null || target < 1L || points < 1 || markReward < 1L || icon == null || icon.isAir()) {
            throw new IllegalArgumentException("Invalid achievement definition: " + id);
        }
    }
}
