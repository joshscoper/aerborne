package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GearContentTest {
    private static final List<String> RELIC_SETS = List.of(
        "veilwalker", "emberforged", "verdant_oath", "tideglass", "hollowfang", "starfall"
    );
    private static final List<String> STARTER_ITEMS = List.of(
        "frontier_hood", "frontier_coat", "frontier_trousers", "frontier_boots", "frontier_blade",
        "frontier_pickaxe", "frontier_hatchet", "frontier_shovel", "frontier_hoe"
    );

    @Test
    void sixRelicCollectionsShipExactlyThreePiecesEach() throws IOException {
        String content = resource("/content/relics.yml");
        assertTrue(content.startsWith("schema-version: 12"));
        for (String set : RELIC_SETS) {
            long pieces = Pattern.compile("(?m)^    set: " + Pattern.quote(set) + "$").matcher(content).results().count();
            assertEquals(3L, pieces, set + " must contain exactly three items");
        }
        assertEquals(38L, Pattern.compile("(?m)^      '[2345]':$").matcher(content).results().count(),
            "Relic and crafted sets should expose their configured threshold bonuses");
    }

    @Test
    void craftedFamiliesProvideWeaponsToolsArmorAndSkillGatedRecipes() throws IOException {
        String content = resource("/content/relics.yml");
        String recipes = resource("/content/recipes.yml");
        for (String set : List.of("ironroot", "wildwood", "riftglass")) {
            assertEquals(set.equals("ironroot") ? 7L : 6L, Pattern.compile("(?m)^    set: " + set + "$").matcher(content).results().count());
        }
        assertEquals(19L, Pattern.compile("(?m)^    rarity: crafted$").matcher(content).results().count());
        assertEquals(52L, Pattern.compile("(?m)^    result: ").matcher(recipes).results().count());
        assertTrue(recipes.contains("level: 10"));
        assertTrue(recipes.contains("level: 20"));
        assertTrue(recipes.contains("level: 30"));
        for (String item : List.of(
            "ironroot_blade", "ironroot_pickaxe", "ironroot_helm", "ironroot_plate", "ironroot_boots",
            "wildwood_axe", "wildwood_sickle", "wildwood_hood", "wildwood_coat", "wildwood_boots",
            "riftglass_edge", "riftglass_pickaxe", "riftglass_visor", "riftglass_cuirass", "riftglass_boots",
            "ironroot_shovel", "ironroot_shield", "wildwood_rod", "riftglass_bow"
        )) {
            Pattern socketed = Pattern.compile("(?ms)^  " + item + ":\\R(?:(?!^  [a-z0-9_]+:).)*?^    sockets: [1-4]$");
            assertTrue(socketed.matcher(content).find(), item + " must expose at least one socket");
        }
    }

    @Test
    void starterKitContainsArmorWeaponAndTool() throws IOException {
        String config = resource("/config.yml");
        String content = resource("/content/relics.yml");
        for (String item : STARTER_ITEMS) {
            assertTrue(config.contains("    - " + item), "Starter config is missing " + item);
            assertTrue(content.contains("  " + item + ":"), "Gear content is missing " + item);
        }
        assertEquals(4L, Pattern.compile("(?m)^    set: frontier$").matcher(content).results().count());
        assertTrue(config.contains("material: cooked_beef"));
        assertTrue(config.contains("health-potions: 3"));
        assertTrue(config.contains("torches: 16"));
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
