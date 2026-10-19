package net.nerdsmp.paper.player;

import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSettingsServiceTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private PlayerSettingsService settings() {
        return new PlayerSettingsService(Logger.getAnonymousLogger(), new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()));
    }
    @Test void partyAndCoopPrivacyPersistIndependently() {
        var settings = settings();
        settings.set(player, PlayerSettingsService.Setting.PARTY_REQUESTS, false);
        settings.set(player, PlayerSettingsService.Setting.COOP_INVITES, false);
        settings.invalidate(player);
        assertFalse(settings.enabled(player, PlayerSettingsService.Setting.PARTY_REQUESTS));
        assertFalse(settings().enabled(player, PlayerSettingsService.Setting.COOP_INVITES));
        assertTrue(settings.enabled(player, PlayerSettingsService.Setting.FRIEND_REQUESTS));
        assertEquals(13, settings.snapshot(player).size());
        assertTrue(settings.enabled(player, PlayerSettingsService.Setting.TREE_FELLING));
        assertTrue(settings.enabled(player, PlayerSettingsService.Setting.TREE_REPLANTING));
    }
    @Test void islandPreferencesSurviveRestartAndDoNotChangeOtherSettings() {
        var settings = settings();
        settings.set(player, PlayerSettingsService.Setting.ISLAND_EFFECTS, false);
        settings.set(player, PlayerSettingsService.Setting.ISLAND_DISPLAYS, false);
        settings.channel(player, "trade"); settings.close();
        var restored = settings();
        assertFalse(restored.enabled(player, PlayerSettingsService.Setting.ISLAND_EFFECTS));
        assertFalse(restored.enabled(player, PlayerSettingsService.Setting.ISLAND_DISPLAYS));
        assertTrue(restored.enabled(player, PlayerSettingsService.Setting.MOB_HEALTH_BARS));
        assertEquals("trade", restored.channel(player));
    }
    @Test void failedPreferenceSaveDoesNotPublishOrGetSavedOnShutdown() throws Exception {
        var settings = settings();
        Path blocker = Files.createDirectory(directory.resolve("player_data/" + player + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> settings.set(player, PlayerSettingsService.Setting.ISLAND_EFFECTS, false));
        assertTrue(settings.enabled(player, PlayerSettingsService.Setting.ISLAND_EFFECTS));
        assertThrows(IllegalStateException.class, () -> settings.channel(player, "trade"));
        assertEquals("global", settings.channel(player));
        settings.close(); assertTrue(Files.isDirectory(blocker));
        assertFalse(Files.exists(directory.resolve("player_data/" + player + ".yml")));
    }
    @Test void unchangedPreferencesAndShutdownNeedNoDiskWrites() throws Exception {
        var settings = settings();
        settings.set(player, PlayerSettingsService.Setting.ISLAND_DISPLAYS, false);
        settings.channel(player, "party");
        byte[] before = Files.readAllBytes(directory.resolve("player_data/" + player + ".yml"));
        Files.createDirectory(directory.resolve("player_data/" + player + ".yml.tmp"));
        assertDoesNotThrow(() -> settings.set(player, PlayerSettingsService.Setting.ISLAND_DISPLAYS, false));
        assertDoesNotThrow(() -> settings.channel(player, "PARTY"));
        assertDoesNotThrow(settings::close);
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("player_data/" + player + ".yml")));
    }
    @Test void retainedLegacyFileCannotOverwriteNewPreferencesOnRestart() throws Exception {
        var original = settings(); original.set(player, PlayerSettingsService.Setting.ISLAND_EFFECTS, false);
        var legacy = new org.bukkit.configuration.file.YamlConfiguration();
        legacy.set("players." + player + ".settings.island-effects", true);
        legacy.save(directory.resolve("player-settings.yml").toFile());
        assertFalse(settings().enabled(player, PlayerSettingsService.Setting.ISLAND_EFFECTS));
    }
}
