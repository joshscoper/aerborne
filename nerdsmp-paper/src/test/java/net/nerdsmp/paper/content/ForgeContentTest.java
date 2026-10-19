package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ForgeContentTest {
    private static final List<String> MODELS = List.of(
        "geode_rough", "geode_resonant", "geode_luminous",
        "gem_ruby", "gem_sapphire", "gem_emerald", "gem_topaz", "gem_aquamarine", "gem_amethyst",
        "gem_citrine", "gem_jade", "gem_onyx", "gem_moonstone", "gem_prismatic",
        "enchantment_tome", "arcane_dust"
    );

    @Test
    void everyForgeItemHasANexoItemModel() throws IOException {
        Path root = Path.of("..", "assets", "nexo-pack", "assets", "nerdsmp", "items");
        String manifest = Files.readString(Path.of("..", "assets", "shared-manifest.json"));
        for (String id : MODELS) {
            assertTrue(manifest.contains("\"id\": \"" + id + "\""), "Manifest is missing " + id);
            Path model = root.resolve(id + ".json");
            assertTrue(Files.isRegularFile(model), "Nexo ItemModel is missing " + id);
            assertTrue(Files.readString(model).contains("minecraft:model"), "Invalid ItemModel " + id);
        }
    }

    @Test
    void forgeDropRatesAreExplicitForProductionAndLocalTesting() throws IOException {
        String production = Files.readString(Path.of("src", "main", "resources", "config.yml"));
        String local = Files.readString(Path.of("..", "deploy", "test", "config.yml"));
        for (String key : List.of(
            "mining-chance:", "excavation-chance:", "custom-mob-tome-chance:", "boss-tome-chance:"
        )) {
            assertTrue(production.contains(key), "Production config is missing " + key);
            assertTrue(local.contains(key), "Local config is missing " + key);
        }
    }
}
