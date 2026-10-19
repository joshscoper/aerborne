package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AssetManifestTest {
    private static final Pattern ITEM_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern JAVA_MODEL = Pattern.compile("\\\"javaModel\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Test
    void everyManifestItemHasUniqueJavaIdentityAndNexoModel() throws IOException {
        Path manifestPath = Path.of("..", "assets", "shared-manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "Shared asset manifest must exist");
        String manifest = Files.readString(manifestPath);

        Set<String> ids = values(ITEM_ID, manifest);
        Set<String> javaModels = values(JAVA_MODEL, manifest);

        assertEquals(41, ids.size(),
            "The manifest must define gear, forge materials, currency, and fish");
        assertEquals(ids.size(), javaModels.size(), "Each relic needs a unique Java model");
        assertFalse(manifest.contains("bedrockIdentifier"), "Release assets are Java-only");
        assertFalse(javaModels.stream().anyMatch(value -> !value.startsWith("nerdsmp:")));

        Path nexoItems = Path.of("..", "assets", "nexo-pack", "assets", "nerdsmp", "items");
        for (String id : ids) {
            Path itemModel = nexoItems.resolve(id + ".json");
            assertTrue(Files.isRegularFile(itemModel), "Nexo source pack is missing ItemModel " + id);
            assertTrue(Files.readString(itemModel).contains("minecraft:model"),
                "Nexo ItemModel must provide a valid model dispatcher: " + id);
        }
    }

    private Set<String> values(Pattern pattern, String input) {
        Set<String> values = new HashSet<>();
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
            assertTrue(values.add(matcher.group(1)), "Duplicate manifest value: " + matcher.group(1));
        }
        assertEquals(count, values.size());
        return values;
    }
}
