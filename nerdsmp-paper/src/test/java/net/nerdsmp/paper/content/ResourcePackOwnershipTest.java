package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourcePackOwnershipTest {
    @Test
    void vanillaTexturesAreDefaultAndNexoRemainsAnExplicitOption() throws IOException {
        String config = Files.readString(Path.of("src", "main", "resources", "config.yml"));
        String plugin = Files.readString(Path.of("src", "main", "resources", "plugin.yml"));
        String runner = Files.readString(Path.of("..", "run-test-server.ps1"));

        assertTrue(config.contains("provider: none"));
        assertTrue(config.contains("require-provider-plugin: false"));
        assertTrue(config.contains("custom-models: false"));
        assertTrue(config.contains("mode: skyblock"));
        assertTrue(runner.contains("[switch]$Nexo"));
        assertTrue(runner.contains("[switch]$LegacySmp"));
        assertTrue(runner.contains("if (-not $LegacySmp) { $Skyblock = $true }"));
        assertTrue(runner.contains("-Dnerdsmp.vanillaTextures=$vanillaTextures"));
        assertTrue(plugin.contains("softdepend: [Nexo,"));
        assertTrue(runner.contains("pack\\external_packs"));
        assertTrue(runner.contains("local-plugins"));
        assertTrue(runner.contains("settings.local.fragment.yml"));
        assertTrue(runner.contains("paper-global.yml"));
        assertTrue(runner.contains("content\\relics.yml"));
        assertFalse(config.contains("java-url:"));
        assertFalse(config.contains("provider: direct"));
    }
}
