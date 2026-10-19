package net.nerdsmp.paper.presentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PresentationContractTest {
    @Test
    void playerVisualsAndGuiCommandsAreConfigured() throws IOException {
        String config = Files.readString(Path.of("src", "main", "resources", "config.yml"));
        String plugin = Files.readString(Path.of("src", "main", "resources", "plugin.yml"));
        String presentation = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "presentation", "PlayerPresentationService.java"
        ));

        assertTrue(config.contains("presentation:"));
        assertTrue(config.contains("sidebar-enabled: true"));
        assertTrue(config.contains("tab-list-enabled: true"));
        assertTrue(config.contains("nameplates-enabled: true"));
        assertTrue(config.contains("chat-format-enabled: true"));
        assertTrue(config.contains("damage-displays:"));
        assertTrue(config.contains("max-active-per-player: 24"));
        assertTrue(config.contains("mob-health-bars:"));
        assertTrue(config.contains("boss-viewer-range: 64"));
        assertTrue(plugin.contains("  menu:"));
        assertTrue(plugin.contains("  profile:"));
        assertTrue(plugin.contains("  hud:"));
        assertTrue(plugin.contains("  adventurer:"));
        assertTrue(config.contains("blocks-per-level: 128"));
        assertTrue(config.contains("health-per-level: 0.01"));
        assertTrue(config.contains("boss-health-per-extra-player: 0.35"));
        assertTrue(config.contains("boss-mechanics:"));
        assertTrue(presentation.contains("escape(view.adventurerRank())"));
        assertTrue(presentation.contains("target.getUniqueId().toString()"));
        assertFalse(presentation.contains("<aqua>/menu</aqua>"));
        assertFalse(presentation.contains("<dark_gray>/menu"));
        String damageDisplays = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "presentation", "DamageDisplayService.java"
        ));
        assertTrue(damageDisplays.contains("setVisibleByDefault(false)"));
        assertTrue(damageDisplays.contains("viewer.showEntity(plugin, display)"));
        assertTrue(damageDisplays.contains("event.isCritical()"));
        String healthBars = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "presentation", "MobHealthBarService.java"
        ));
        assertTrue(healthBars.contains("setVisibleByDefault(false)"));
        assertTrue(healthBars.contains("viewer.showEntity(plugin, current.display())"));
        assertTrue(healthBars.contains("Setting.MOB_HEALTH_BARS"));
        assertTrue(healthBars.contains("active_poison_types"));
        assertTrue(healthBars.contains("statusIconVisible"));
        String alchemy = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "alchemy", "AlchemyService.java"
        ));
        assertFalse(alchemy.contains("adventure.bossbar"));
    }
}
