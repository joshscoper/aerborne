package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReputationContractContentTest {
    @Test
    void everyFactionShipsTwoRotatingOptionsForEveryCadence() throws IOException {
        String content = resource("/content/reputation-contracts.yml");
        assertTrue(content.startsWith("schema-version: 2"));
        assertEquals(27L, count(content, "(?m)^  [a-z0-9_]+:$"));
        for (String faction : java.util.List.of("frontier_wardens", "rift_scholars", "free_delvers")) {
            assertEquals(9L, count(content, "(?m)^    faction: " + faction + "$"));
        }
        for (String cadence : java.util.List.of("daily", "weekly", "monthly")) {
            assertEquals(6L, count(content, "(?m)^    cadence: " + cadence + "$"));
        }
        assertEquals(6L, count(content, "(?m)^    reputation: 12$"));
        assertEquals(6L, count(content, "(?m)^    reputation: 55$"));
        assertEquals(6L, count(content, "(?m)^    reputation: 175$"));
        assertEquals(9L, count(content, "(?m)^    cadence: story$"));
        assertEquals(6L, count(content, "(?m)^    prerequisite: [a-z0-9_]+$"));
    }

    private long count(String content, String expression) {
        return Pattern.compile(expression).matcher(content).results().count();
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
