package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayerServicesContractTest {
    @Test
    void socialChatTradeTravelAndGuideCommandsArePublished() throws IOException {
        String plugin = resource("/plugin.yml");
        for (String command : List.of("settings", "social", "friends", "ignore", "msg", "reply", "channel",
            "globalchat", "localchat", "tradechat", "trade", "spawn", "warp", "warps", "tpa", "tpahere",
            "tpaccept", "tpdeny", "back", "quickstart", "tips")) {
            assertTrue(plugin.contains("  " + command + ":"), "Missing /" + command);
        }
        assertTrue(plugin.contains("nerdsmp.travel.admin:"));
        assertTrue(plugin.contains("nerdsmp.travel.bypass:"));
    }

    @Test
    void playerServiceTimingAndTipDefaultsAreExplicit() throws IOException {
        String config = resource("/config.yml");
        assertTrue(config.contains("local-radius: 100"));
        assertTrue(config.contains("warmup-seconds: 3"));
        assertTrue(config.contains("cooldown-seconds: 15"));
        assertTrue(config.contains("initial-delay-seconds: 120"));
        assertTrue(config.contains("interval-seconds: 600"));
        String settings = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "player", "PlayerSettingsService.java"
        ));
        assertTrue(settings.contains("MOB_HEALTH_BARS(\"mob-health-bars\", true"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
