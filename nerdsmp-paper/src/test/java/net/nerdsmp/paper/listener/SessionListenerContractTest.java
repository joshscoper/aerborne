package net.nerdsmp.paper.listener;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionListenerContractTest {
    @Test
    void loginWelcomeDoesNotIncludeThePublishingDisclaimer() throws IOException {
        String listener = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "listener", "SessionListener.java"
        ));
        String config = Files.readString(Path.of("src", "main", "resources", "config.yml"));

        assertFalse(listener.toLowerCase(java.util.Locale.ROOT).contains("disclaimer"));
        assertFalse(listener.contains("NOT AN OFFICIAL"));
        assertTrue(config.contains("compliance:"));
        assertTrue(config.contains("NOT AN OFFICIAL MINECRAFT SERVER"));
    }
}
