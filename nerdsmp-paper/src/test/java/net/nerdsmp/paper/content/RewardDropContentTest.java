package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RewardDropContentTest {
    @Test
    void hostileLootShipsTieredGearPursesAndThemedWorldTables() throws IOException {
        String content = resource("/content/drop-tables.yml");
        assertTrue(Pattern.compile("(?m)^  [a-z].*:$").matcher(content).results().count() >= 20L);
        assertTrue(content.contains("ENDERMAN"));
        assertTrue(content.contains("WITHER_SKELETON"));
        assertTrue(content.contains("GUARDIAN"));
        assertTrue(content.contains("special: rough_geode"));
        assertTrue(content.contains("hostile-gear:"));
        assertTrue(content.contains("chance: 0.008"));
        assertTrue(content.contains("rolls: 2"));
        assertTrue(content.contains("coin-purses:"));
        assertTrue(content.contains("min-marks: 75"));
        assertTrue(content.contains("vanilla-adjustments:"));
        assertTrue(content.contains("keep-chance: 0.0"));
        assertTrue(content.contains("maximum-affixes: 3"));
        assertTrue(content.contains("chest:"));
        assertTrue(content.contains("entities: [ENDER_DRAGON]"));
        assertTrue(content.contains("material: HEART_OF_THE_SEA"));
        assertTrue(content.contains("material: WITHER_ROSE"));
        assertTrue(content.contains("material: ECHO_SHARD"));
        assertTrue(content.contains("material: DRAGON_BREATH"));
    }

    @Test
    void passiveFarmingDoesNotReceiveSupplementalKillLoot() throws IOException {
        String content = resource("/content/drop-tables.yml");
        assertTrue(!content.contains("farm-animals:"));
        assertTrue(!content.contains("entities: [COW"));
    }

    @Test
    void coinPurseIsAnAuthenticatedNexoBackedItem() throws IOException {
        String relics = resource("/content/relics.yml");
        assertTrue(relics.contains("  coin_purse:"));
        assertTrue(relics.contains("model: nerdsmp:coin_purse"));
    }

    @Test
    void coinPursesCannotBeCollectedByHoppers() throws IOException {
        String service = Files.readString(Path.of(
            "src", "main", "java", "net", "nerdsmp", "paper", "loot", "RewardDropService.java"));

        assertTrue(service.contains("onCoinPurseHopperPickup(InventoryPickupItemEvent event)"));
        assertTrue(service.contains("if (isCoinPurse(event.getItem().getItemStack()))"));
        assertTrue(service.contains("event.setCancelled(true)"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
