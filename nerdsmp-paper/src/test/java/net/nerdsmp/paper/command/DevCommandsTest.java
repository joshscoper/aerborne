package net.nerdsmp.paper.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tools stay registered as written, and stay away from anyone but ops unless granted. */
class DevCommandsTest {
    @Test void theCommandIsRegisteredForOpsOnly() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"))) {
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals(DevCommands.USAGE, yaml.getString("commands.dev.usage"));
            assertEquals(DevCommands.PERMISSION, yaml.getString("commands.dev.permission"));
            assertEquals("op", yaml.getString("permissions." + DevCommands.PERMISSION + ".default"));
        }
    }
}
