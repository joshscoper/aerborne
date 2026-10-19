package net.nerdsmp.paper.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HubNavigationTest {
    @Test
    void backButtonsReturnToTheHubPageThatOpenedTheScreen() {
        assertEquals(MenuManager.View.CHARACTER, MenuManager.parent(MenuManager.View.PROFILE));
        assertEquals(MenuManager.View.SKILLS, MenuManager.parent(MenuManager.View.SKILL_TREE));
        assertEquals(MenuManager.View.ADVENTURE, MenuManager.parent(MenuManager.View.QUESTS));
        assertEquals(MenuManager.View.JOBS, MenuManager.parent(MenuManager.View.ORDERS));
        assertEquals(MenuManager.View.WORKSHOP, MenuManager.parent(MenuManager.View.FORGE));
        assertEquals(MenuManager.View.WORLD, MenuManager.parent(MenuManager.View.CLAIMS));
        assertEquals(MenuManager.View.HOME, MenuManager.parent(MenuManager.View.WORKSHOP));
        assertEquals(MenuManager.View.HOME, MenuManager.parent(MenuManager.View.SETTINGS));
    }

    @Test
    void stationRefusalsMatchPluginYml() {
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        Map<String, RemoteStation> commands = Map.of(
            "forge", RemoteStation.FORGE,
            "enchants", RemoteStation.ENCHANTING,
            "alchemy", RemoteStation.ALCHEMY,
            "identify", RemoteStation.IDENTIFY,
            "bank", RemoteStation.BANK,
            "repairall", RemoteStation.REPAIR);
        commands.forEach((command, station) -> {
            assertEquals(RemoteStation.PERMISSION, plugin.getString("commands." + command + ".permission"), command);
            assertEquals(station.refusal(), plugin.getString("commands." + command + ".permission-message"), command);
        });
    }
}
