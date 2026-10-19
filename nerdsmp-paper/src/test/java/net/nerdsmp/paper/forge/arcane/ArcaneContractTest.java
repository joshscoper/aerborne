package net.nerdsmp.paper.forge.arcane;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneContractTest {
    private static YamlConfiguration load(String path) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(path));
        return yaml;
    }

    @Test
    void productionConfigEnablesCoreWorkflowButKeepsUnprovenPowersOff() throws Exception {
        ArcaneSettings production = ArcaneSettings.from(load("src/main/resources/config.yml"));
        assertTrue(production.errors().isEmpty(), production.errors().toString());
        assertTrue(production.enabled() && production.codex() && production.anticheatBridge());
        assertTrue(production.research().enabled());
        assertTrue(production.imbuement().enabled());
        assertTrue(production.overcharge().enabled());
        assertTrue(production.attunement().enabled() && production.corruption().enabled());
        assertFalse(production.signatures().enabled());
        for (ArcaneSignature signature : ArcaneSignature.values()) assertFalse(production.signatures().of(signature).enabled());
        // The documented values are the code defaults, so a server without the section behaves the same.
        assertEquals(ArcaneSettings.defaults().research(), production.research());
        assertEquals(ArcaneSettings.defaults().imbuement(), production.imbuement());
        assertEquals(ArcaneSettings.defaults().capacity(), production.capacity());
        assertEquals(ArcaneSettings.defaults().signatures(), production.signatures());
    }

    @Test
    void localTestConfigTurnsEverythingOnAndIsValid() throws Exception {
        ArcaneSettings local = ArcaneSettings.from(load("../deploy/test/config.yml"));
        assertTrue(local.errors().isEmpty(), local.errors().toString());
        assertTrue(local.research().enabled() && local.imbuement().enabled() && local.overcharge().enabled());
        for (ArcaneSignature signature : ArcaneSignature.values()) assertTrue(local.signatures().active(signature), signature.name());
    }

    @Test
    void commandsUsageAndPermissionsMatchPluginYml() throws Exception {
        // Permission names contain dots, so read plugin.yml with another path separator.
        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        assertEquals(ArcaneCommands.PLAYER_USAGE, plugin.getString("commands/arcane/usage"));
        assertEquals(null, plugin.getString("commands/arcane/permission"), "the Codex is open to everyone");
        assertEquals(ArcaneCommands.ADMIN_PERMISSION, plugin.getString("commands/forgeadmin/permission"));
        assertEquals(ArcaneCommands.ADMIN_USAGE, plugin.getString("commands/forgeadmin/usage"));
        assertEquals("op", plugin.getString("permissions/nerdsmp.forge.admin/default"));
        assertTrue(plugin.getBoolean("permissions/nerdsmp.admin/children/nerdsmp.forge.admin"));
        assertEquals("nerdsmp.station.remote", plugin.getString("commands/forge/permission"), "/forge is unchanged");
    }

    @Test
    void everyOutcomeHasAPlayerSentenceAndNeverAnEnumName() {
        for (ArcaneForge.Outcome outcome : ArcaneForge.Outcome.values()) {
            String message = ArcaneMenus.message(outcome);
            assertFalse(message.isBlank(), outcome.name());
            assertFalse(message.contains("_") || message.contains(outcome.name()), message);
            assertTrue(message.endsWith("."), message);
        }
        for (String spender : List.of(ArcaneMenus.message(ArcaneForge.Outcome.MISSING_DUST),
            ArcaneMenus.message(ArcaneForge.Outcome.MISSING_GEMS), ArcaneMenus.message(ArcaneForge.Outcome.ITEM_CHANGED),
            ArcaneMenus.message(ArcaneForge.Outcome.SAVE_FAILED))) {
            assertTrue(spender.contains("nothing was spent") || spender.contains("Nothing was spent"), spender);
        }
    }
}
