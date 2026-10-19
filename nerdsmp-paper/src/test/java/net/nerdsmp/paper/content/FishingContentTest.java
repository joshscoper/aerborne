package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class FishingContentTest {
    private static final List<String> CATCHES = List.of(
        "river_sprat", "redfin_perch", "mossback_carp", "shoreline_mackerel",
        "moonlit_koi", "embergill_trout", "glassscale_eel",
        "stormfin_tuna", "frostveil_char", "riftwater_ray",
        "crownscale_coelacanth", "starfall_sturgeon"
    );

    @Test
    void everyJournalCatchHasItemAndJavaModelDefinitions() throws IOException {
        String items = Files.readString(Path.of("src", "main", "resources", "content", "relics.yml"));
        String manifest = Files.readString(Path.of("..", "assets", "shared-manifest.json"));
        Path models = Path.of("..", "assets", "nexo-pack", "assets", "nerdsmp", "items");

        for (String catchId : CATCHES) {
            assertTrue(items.contains("  " + catchId + ":"), "Missing catch item " + catchId);
            assertTrue(items.contains("model: nerdsmp:" + catchId), "Missing item model " + catchId);
            assertTrue(manifest.contains("\"id\": \"" + catchId + "\""), "Missing manifest entry " + catchId);
            assertTrue(Files.isRegularFile(models.resolve(catchId + ".json")), "Missing Nexo model " + catchId);
        }
    }

    @Test
    void fishingIsConnectedToIvoPersistenceReputationAndAchievements() throws IOException {
        String npcs = Files.readString(Path.of("src", "main", "resources", "content", "npcs.yml"));
        String achievements = Files.readString(Path.of(
            "src", "main", "resources", "content", "achievements.yml"));
        String service = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "activity", "FishingService.java"));

        assertTrue(npcs.contains("type: open_fishery"));
        assertTrue(achievements.contains("metric: fishing_species"));
        assertTrue(service.contains("plugin.collections().record"));
        assertTrue(service.contains("openFishery"));
        assertTrue(service.contains("fishery.exchange"));
        assertTrue(service.contains("FREE_DELVERS"));
    }
}
