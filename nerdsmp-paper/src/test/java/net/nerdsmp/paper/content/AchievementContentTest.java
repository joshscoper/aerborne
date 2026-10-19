package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AchievementContentTest {
    @Test
    void shipsPersistentMilestonesRewardsAndPlayerEntryPoints() throws IOException {
        String content = resource("/content/achievements.yml");
        String plugin = resource("/plugin.yml");

        assertTrue(content.startsWith("schema-version: 3"));
        assertEquals(37L, Pattern.compile("(?m)^  [a-z0-9_]+:$").matcher(content).results().count());
        for (String metric : java.util.List.of(
            "adventurer_level", "highest_skill_level", "total_skill_xp", "items_crafted",
            "mobs_defeated", "elites_defeated", "bosses_defeated", "fish_caught", "fishing_species",
            "excavation_finds", "archive_items", "cooking_level", "meals_prepared", "island_level", "island_talents", "resource_stage", "active_set_bonuses"
        )) {
            assertTrue(content.contains("metric: " + metric), "Missing achievement metric " + metric);
        }
        assertEquals(37L, Pattern.compile("(?m)^    marks: [1-9][0-9]*$").matcher(content).results().count());
        assertTrue(plugin.contains("  achievements:"));
        assertTrue(plugin.contains("aliases: [awards, ach]"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
