package net.nerdsmp.paper.engagement;

public record DailyQuestDefinition(
    String id,
    String title,
    String description,
    QuestAction action,
    int target,
    long markReward
) {
    public DailyQuestDefinition {
        if (id == null || !id.matches("[a-z0-9_-]{1,48}")) {
            throw new IllegalArgumentException("Invalid daily quest id");
        }
        if (title == null || title.isBlank() || title.length() > 48) {
            throw new IllegalArgumentException("Invalid daily quest title");
        }
        if (description == null || description.isBlank() || description.length() > 120) {
            throw new IllegalArgumentException("Invalid daily quest description");
        }
        if (target < 1 || target > 10000 || markReward < 1L || markReward > 10000L) {
            throw new IllegalArgumentException("Daily quest target or reward is outside safe limits");
        }
    }
}
