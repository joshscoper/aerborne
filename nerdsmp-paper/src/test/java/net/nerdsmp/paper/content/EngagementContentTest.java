package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EngagementContentTest {
    @Test
    void dailyPoolAndPlaytimeMilestonesAreConfigured() throws IOException {
        String quests = resource("/content/daily-quests.yml");
        String config = resource("/config.yml");

        assertEquals(5L, Pattern.compile("(?m)^    action: ").matcher(quests).results().count());
        assertEquals(5L, Pattern.compile("(?m)^    marks: ").matcher(quests).results().count());
        assertTrue(config.contains("daily-quests-per-day: 3"));
        assertTrue(config.contains("active-timeout-seconds: 300"));
        assertTrue(config.contains("'600': 600"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
