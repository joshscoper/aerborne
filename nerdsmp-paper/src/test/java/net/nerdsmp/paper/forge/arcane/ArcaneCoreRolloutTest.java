package net.nerdsmp.paper.forge.arcane;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ArcaneCoreRolloutTest {
    @TempDir Path directory;

    @Test
    void rolloutBacksUpExactOriginalChangesOnlyFiveSwitchesAndIsIdempotent() throws Exception {
        Path config = directory.resolve("config.yml");
        String original = """
            # Keep operator content.
            operator-extension: original
            anticheat:
              mode: ALERT
            arcane-forge:
              research:
                enabled: false
                dust-per-reagent: 9
              imbuement:
                enabled: false
              attunement:
                enabled: false
              corruption:
                enabled: false
              overcharge:
                enabled: false
              signatures:
                enabled: false
                stormwake:
                  enabled: false
              effects:
                bloodletter:
                  enabled: false
              anticheat-bridge:
                enabled: false
            """;
        Files.writeString(config, original);
        var plan = ArcaneCoreRollout.preview(config);
        assertEquals(5, plan.changes().size());
        assertEquals(original, Files.readString(config), "preview writes nothing");
        Path backup = ArcaneCoreRollout.commit(config, plan);
        assertEquals(original, Files.readString(backup));
        YamlConfiguration before = new YamlConfiguration(); before.loadFromString(original);
        YamlConfiguration after = new YamlConfiguration(); after.load(config.toFile());
        for (String key : before.getKeys(true)) {
            if (!before.isConfigurationSection(key) && !plan.changes().contains(key)) assertEquals(before.get(key), after.get(key), key);
        }
        assertTrue(Files.readString(config).contains("# Keep operator content."));
        assertEquals(9, plan.settings().research().dustPerReagent());
        assertFalse(plan.settings().signatures().enabled() || plan.settings().effects().bloodletter() || plan.settings().anticheatBridge());
        var second = ArcaneCoreRollout.preview(config);
        assertTrue(second.changes().isEmpty());
        assertNull(ArcaneCoreRollout.commit(config, second));
    }

    @Test
    void staleAndMalformedConfigAreRefusedBeforeReplacement() throws Exception {
        Path config = directory.resolve("config.yml");
        Files.writeString(config, "arcane-forge: {}\n");
        var plan = ArcaneCoreRollout.preview(config);
        Files.writeString(config, "operator-edit: true\n");
        assertThrows(java.io.IOException.class, () -> ArcaneCoreRollout.commit(config, plan));
        assertEquals("operator-edit: true\n", Files.readString(config));
        Files.writeString(config, "arcane-forge:\n  research:\n    enabled: perhaps\n");
        assertThrows(IllegalArgumentException.class, () -> ArcaneCoreRollout.preview(config));
        assertEquals("arcane-forge:\n  research:\n    enabled: perhaps\n", Files.readString(config));
    }

    @Test
    void releaseConfigMakesCoreAvailableWithoutEnablingHighRiskRuntime() throws Exception {
        for (Path path : List.of(Path.of("src/main/resources/config.yml"), Path.of("../deploy/release/config.yml"))) {
            YamlConfiguration yaml = new YamlConfiguration(); yaml.load(path.toFile());
            var settings = ArcaneSettings.from(yaml);
            assertTrue(settings.errors().isEmpty(), path + ": " + settings.errors());
            assertTrue(settings.research().enabled() && settings.imbuement().enabled() && settings.attunement().enabled()
                && settings.corruption().enabled() && settings.overcharge().enabled(), path.toString());
            assertFalse(settings.effects().bloodletter() || settings.effects().veinseeker() || settings.effects().chainHarvest());
            for (var signature : ArcaneSignature.values()) assertFalse(settings.signatures().active(signature));
        }
    }

    @Test
    void explicitDisabledSwitchesStayDisabledAndMalformedOnSwitchFailsClosed() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("arcane-forge:\n  research:\n    enabled: false\n  imbuement:\n    enabled: maybe\n");
        var settings = ArcaneSettings.from(yaml);
        assertFalse(settings.research().enabled());
        assertFalse(settings.imbuement().enabled());
        assertEquals(1, settings.errors().size());
    }

    @Test
    void availabilityRequiresIndividualSignatureSwitchAndTheStormwakeBridge() throws Exception {
        var registry = ArcaneRegistry.defaults();
        var yaml = new YamlConfiguration();
        yaml.loadFromString("arcane-forge:\n  signatures:\n    enabled: true\n    stormwake:\n      enabled: true\n  anticheat-bridge:\n    enabled: false\n");
        var settings = ArcaneSettings.from(yaml);
        assertFalse(ArcaneForge.definitionActive(registry.of(ArcaneSignature.STORMWAKE), settings));
        assertFalse(ArcaneForge.definitionActive(registry.of(ArcaneSignature.LAST_BASTION), settings));
        assertFalse(ArcaneForge.definitionActive(registry.of(net.nerdsmp.paper.forge.ArcaneEnchantment.BLOODLETTER), settings));
        assertTrue(ArcaneForge.definitionActive(registry.of(net.nerdsmp.paper.forge.ArcaneEnchantment.KEEN_EDGE), settings));
    }

    @Test
    void safeResearchCannotGivePartialProgressOrCluesTowardDormantPowers() {
        var settings = ArcaneSettings.defaults();
        var registry = new ArcaneRegistry(ArcaneRegistry.defaults().all().stream()
            .filter(definition -> ArcaneForge.definitionActive(definition, settings)).toList());
        var plan = ArcaneResearch.plan(registry, settings.research(), ResearchProfile.empty(),
            List.of(new ArcaneResearch.Reagent(ArcaneAspect.AFFINITY, 4)), false, 50);
        var result = ArcaneResearch.apply(registry, settings.research(), ResearchProfile.empty(), plan, "receipt", "Research");
        for (var outcome : result.outcomes()) if (outcome.definition() != null) assertTrue(ArcaneForge.definitionActive(outcome.definition(), settings));
        for (var definition : ArcaneRegistry.defaults().all()) {
            if (!ArcaneForge.definitionActive(definition, settings)) assertEquals(ResearchProfile.Entry.EMPTY, result.profile().entry(definition.id()));
        }
    }
}
