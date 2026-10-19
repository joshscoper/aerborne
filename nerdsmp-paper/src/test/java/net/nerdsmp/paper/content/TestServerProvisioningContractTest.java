package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestServerProvisioningContractTest {
    @Test
    void runnerStagesEveryDataDrivenGameplayCatalog() throws IOException {
        String runner = Files.readString(Path.of("..", "run-test-server.ps1"));
        String localConfig = Files.readString(Path.of("..", "deploy", "test", "config.yml"));
        String localProperties = Files.readString(Path.of("..", "deploy", "test", "server.properties"));
        String productionProperties = Files.readString(Path.of("..", "deploy", "paper", "server.properties"));
        String npcManager = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "npc", "NpcManager.java"));

        for (String content : java.util.List.of(
            "relics.yml", "mobs.yml", "orders.yml", "recipes.yml", "daily-quests.yml", "skyblock-daily-quests.yml", "npcs.yml",
            "reputation-contracts.yml", "drop-tables.yml", "achievements.yml"
        )) {
            assertTrue(runner.contains("content\\" + content), "Runner does not stage " + content);
        }
        assertTrue(localConfig.contains("maximum-capacity: 64"));
        assertTrue(localConfig.contains("parties:"));
        assertTrue(localConfig.contains("engagement:"));
        assertTrue(localConfig.contains("damage-displays:"));
        assertTrue(localConfig.contains("mob-health-bars:"));
        assertFalse(localConfig.contains("seed-sample-npcs-if-empty"));
        assertFalse(npcManager.contains("seedSampleNpcs"));
        assertTrue(localProperties.contains("level-type=minecraft:normal"));
        assertTrue(localProperties.contains("generator-settings={}"));
        assertTrue(localProperties.contains("generate-structures=true"));
        assertTrue(productionProperties.contains("level-type=minecraft:normal"));
        assertTrue(productionProperties.contains("generator-settings={}"));
        assertTrue(localConfig.contains("moderation:"));
        assertTrue(localConfig.contains("report-cooldown-seconds: 60"));
        assertTrue(runner.contains("function Assert-TestServerStopped"));
        assertTrue(runner.contains("function Copy-VersionedContentFile"));
        assertTrue(runner.contains("Updated $(Split-Path -Leaf $Destination) from schema"));
        assertEquals(2L, runner.lines().filter(line -> line.trim().equals("Assert-TestServerStopped")).count(),
            "Runner must guard before both the build and the plugin copy");
    }
}
