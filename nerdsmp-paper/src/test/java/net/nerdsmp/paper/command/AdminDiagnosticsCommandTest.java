package net.nerdsmp.paper.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AdminDiagnosticsCommandTest {
    @Test void diagnosticsUseOnePermissionProtectedAdminSurfaceAndMatchingUsage() throws Exception {
        assertTrue(CommandRouter.ADMIN_SUBCOMMANDS.containsAll(List.of("releasecheck", "stacks", "api")));
        assertEquals(CommandRouter.ADMIN_SUBCOMMANDS.size(), new HashSet<>(CommandRouter.ADMIN_SUBCOMMANDS).size());
        try (var stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals(CommandRouter.NERDADMIN_USAGE, yaml.getString("commands.nerdadmin.usage"));
            assertEquals("nerdsmp.admin", yaml.getString("commands.nerdadmin.permission"));
            assertTrue(yaml.getStringList("commands.nerdadmin.aliases").contains("aerborneadmin"));
        }
    }
}
