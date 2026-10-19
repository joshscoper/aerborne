package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CollectionMasteryContentTest {
    @Test
    void npcTurnInsOwnCollectionProgressAndMasteryRewards() throws IOException {
        String fishing = source("activity", "FishingService.java");
        String excavation = source("activity", "ExcavationService.java");
        String collections = source("activity", "CollectionMasteryService.java");

        assertFalse(fishing.contains("fishing.discovered"));
        assertTrue(fishing.contains("plugin.collections().record"));
        assertTrue(excavation.contains("plugin.collections().record"));
        assertTrue(collections.contains("collections.entries."));
        assertTrue(collections.contains("CollectionRewardDelivery"));
    }

    @Test
    void stationCommandsUsePremiumPermissionWithoutPaidEntitlementCoupling() throws IOException {
        String commands = source("command", "CommandRouter.java");
        String menus = source("gui", "MenuManager.java");
        String plugin = Files.readString(Path.of("src", "main", "resources", "plugin.yml"));

        assertTrue(commands.contains("nerdsmp.station.remote"));
        assertTrue(source("gui", "RemoteStation.java").contains("\"nerdsmp.station.remote\""));
        assertTrue(menus.contains("RemoteStation.remote(player)"));
        assertTrue(plugin.contains("nerdsmp.premium:"));
        assertTrue(plugin.contains("paid cosmetic memberships do not grant this permission"));
        assertTrue(plugin.contains("permission: nerdsmp.station.remote"));
    }

    private String source(String folder, String file) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "net", "nerdsmp", "paper", folder, file));
    }
}
