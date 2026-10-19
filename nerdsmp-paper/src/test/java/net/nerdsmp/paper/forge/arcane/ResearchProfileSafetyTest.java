package net.nerdsmp.paper.forge.arcane;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ResearchProfileSafetyTest {
    private YamlConfiguration profile() {
        var yaml = new YamlConfiguration();
        ResearchProfile.empty().seed(Set.of("keen_edge")).write(yaml.createSection("arcane"));
        return yaml;
    }

    @Test
    void malformedOrFutureKnowledgeNeverBecomesAnEmptyProfile() {
        for (String path : List.of("schema", "seeded", "entries.keen_edge", "entries.keen_edge.progress",
            "entries.keen_edge.discovered", "entries.keen_edge.binds", "entries.keen_edge.clue",
            "studied-aspects", "history", "receipts", "attempts")) {
            var yaml = profile();
            yaml.set("arcane." + path, "broken");
            assertThrows(IllegalStateException.class, () -> ResearchProfile.read(yaml.getConfigurationSection("arcane")), path);
        }
        var future = profile(); future.set("arcane.schema", 2);
        assertThrows(IllegalStateException.class, () -> ResearchProfile.read(future.getConfigurationSection("arcane")));
        var invalid = profile(); invalid.set("arcane.entries.keen_edge.progress", 101);
        assertThrows(IllegalStateException.class, () -> ResearchProfile.read(invalid.getConfigurationSection("arcane")));
    }

    @Test
    void mixedListsAndMissingRequiredFieldsAreRefused() {
        var yaml = profile(); yaml.set("arcane.history", List.of("valid", 2));
        assertThrows(IllegalStateException.class, () -> ResearchProfile.read(yaml.getConfigurationSection("arcane")));
        yaml.set("arcane.history", List.of()); yaml.set("arcane.seeded", null);
        assertThrows(IllegalStateException.class, () -> ResearchProfile.read(yaml.getConfigurationSection("arcane")));
    }

    @Test
    void supportedExtensionFieldsSurviveAnUpdateAndCountersNeverWrap() {
        var yaml = profile();
        yaml.set("arcane.extension-note", "keep");
        yaml.set("arcane.entries.keen_edge.extension-note", "keep entry");
        yaml.set("arcane.entries.keen_edge.binds", Integer.MAX_VALUE);
        yaml.set("arcane.attempts.might", Integer.MAX_VALUE);
        var updated = ResearchProfile.read(yaml.getConfigurationSection("arcane"))
            .recordBind("keen_edge", 5, 5).attempt("might");
        updated.write(yaml.getConfigurationSection("arcane"));
        assertEquals(Integer.MAX_VALUE, updated.entry("keen_edge").binds());
        assertEquals(Integer.MAX_VALUE, updated.attempts("might"));
        assertEquals("keep", yaml.getString("arcane.extension-note"));
        assertEquals("keep entry", yaml.getString("arcane.entries.keen_edge.extension-note"));
    }
}
