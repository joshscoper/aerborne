package net.nerdsmp.paper.presentation;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.anticheat.WatchdogMath;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.arcane.ArcaneEnchantmentCodec;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.skyblock.IslandWorld;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** The public brand may change; these persisted identities and security decisions must not. */
final class AerborneCompatibilityTest {
    @TempDir Path directory;

    @Test void pluginIdentityAndHistoricalPermissionAssignmentsRemainValid() throws Exception {
        var metadata = yaml("/plugin.yml");
        assertEquals("NerdSMP", metadata.getString("name"), "Changing this abandons the existing data folder and PDC namespace");
        assertEquals("net.nerdsmp.paper.NerdSmpPlugin", metadata.getString("main"));
        assertEquals("26.2", metadata.getString("api-version"));
        assertFalse(metadata.getString("version", "").isBlank());
        assertTrue(Files.readString(Path.of("src/main/resources/plugin.yml")).contains("version: '${version}'"));

        var permissions = metadata.getConfigurationSection("permissions");
        assertNotNull(permissions);
        assertTrue(permissions.getKeys(false).stream().allMatch(node -> node.startsWith("nerdsmp.")));
        for (String node : Set.of("nerdsmp.admin", "nerdsmp.permissions.manage", "nerdsmp.forge.admin",
            "nerdsmp.staff.anticheat", "nerdsmp.anticheat.bypass", "nerdsmp.store.fulfill")) {
            assertEquals("op", permissions.getString(node + "/default"), node);
        }
        assertEquals(false, permissions.get("nerdsmp.station.remote/default"));
        assertEquals(false, permissions.get("nerdsmp.premium/default"));
        assertTrue(permissions.getBoolean("nerdsmp.premium/children/nerdsmp.station.remote"));
        assertTrue(permissions.getBoolean("nerdsmp.admin/children/nerdsmp.staff.anticheat"));
    }

    @Test void brandedEntryPointsReuseTheMenuHandlerWithoutAliasCollisions() throws Exception {
        var commands = yaml("/plugin.yml").getConfigurationSection("commands");
        assertNotNull(commands);
        assertTrue(commands.getStringList("menu/aliases").containsAll(Set.of("nexus", "aerborne", "hearthsky")));
        Map<String, String> owners = new HashMap<>();
        for (String command : commands.getKeys(false)) {
            assertNull(owners.putIfAbsent(command.toLowerCase(Locale.ROOT), command), command);
        }
        for (String command : commands.getKeys(false)) {
            for (String alias : commands.getStringList(command + "/aliases")) {
                assertNull(owners.putIfAbsent(alias.toLowerCase(Locale.ROOT), command), "Alias collision: " + alias);
            }
        }
        assertEquals("menu", owners.get("aerborne"));
        assertEquals("menu", owners.get("hearthsky"));
        assertEquals("menu", owners.get("nexus"));
    }

    @Test void legacyPlayerDocumentsReloadWithoutRenamingOrRewritingProgress() throws Exception {
        UUID player = UUID.randomUUID();
        AccountId account = AccountId.random();
        var store = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        store.bind(player, account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, player.toString()), "ReturningPlayer");
        store.update(account, profile -> {
            profile.set("economy.balance", 123_456L);
            profile.set("progression.skills.mining.experience", 87_654L);
            profile.set("island.owner", account.toString());
            profile.set("compatibility.opaque-item-bytes", "legacy-inventory-payload-not-a-display-name");
            profile.set("compatibility.item-pdc.nerdsmp:item_id", "sunforge_pickaxe");
            profile.set("compatibility.item-pdc.nerdsmp:custom_enchantments", "keen_edge:5|scholar:3");
            profile.set("entitlements.receipts.historical-purchase", true);
        });
        assertEquals(directory.resolve("player_data"), store.directory().toPath());
        assertEquals(1, PlayerDataStore.SCHEMA_VERSION);
        Path document = store.directory().toPath().resolve(player + ".yml");
        byte[] before = Files.readAllBytes(document);
        var restarted = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        assertTrue(restarted.knownAccounts().contains(account));
        assertEquals(123_456L, restarted.read(account, profile -> profile.getLong("economy.balance")).longValue());
        assertEquals(87_654L, restarted.read(account, profile -> profile.getLong("progression.skills.mining.experience")).longValue());
        assertEquals(account.toString(), restarted.read(account, profile -> profile.getString("island.owner")));
        assertEquals("legacy-inventory-payload-not-a-display-name", restarted.read(account,
            profile -> profile.getString("compatibility.opaque-item-bytes")));
        assertEquals(Boolean.TRUE,restarted.read(account, profile -> profile.getBoolean("entitlements.receipts.historical-purchase")));
        assertArrayEquals(before, Files.readAllBytes(document), "Read-only startup must not migrate branding into player data");
        assertFalse(Files.exists(directory.resolve("Aerborne")));
    }

    @Test void legacyItemSchemaAndEnchantmentPayloadsRemainReadable() throws Exception {
        var schema = PaperItemService.class.getDeclaredField("SCHEMA_VERSION");
        schema.setAccessible(true);
        assertEquals(5, schema.getInt(null));
        assertEquals("nerdsmp:item_id", new NamespacedKey("nerdsmp", "item_id").toString());
        String encoded = "keen_edge:5|scholar:3|future_enchantment:7|unknown-data";
        var decoded = ArcaneEnchantmentCodec.decode(encoded);
        assertEquals(5, decoded.known().get(ArcaneEnchantment.KEEN_EDGE));
        assertEquals(3, decoded.known().get(ArcaneEnchantment.SCHOLAR));
        assertEquals(Map.of("damage-percent", 25.0D), ArcaneEnchantment.KEEN_EDGE.stats(5));
        assertEquals(Map.of("skill-xp-percent", 18.0D), ArcaneEnchantment.SCHOLAR.stats(3));
        assertEquals(decoded, ArcaneEnchantmentCodec.decode(ArcaneEnchantmentCodec.encode(decoded.known(), decoded.preserved())));
        assertTrue(decoded.preserved().contains("future_enchantment:7"));
        assertTrue(decoded.preserved().contains("unknown-data"));
    }

    @Test void islandDimensionStillResolvesTheOriginalPersistentWorldName() throws Exception {
        var resolver = IslandWorld.class.getDeclaredMethod("dimensionDirectory", Path.class, String.class);
        resolver.setAccessible(true);
        Path level = directory.resolve("existing-world");
        assertEquals(level.resolve("dimensions/minecraft/nerdsmp_skyblock_existing-season"),
            resolver.invoke(null, level, "nerdsmp_skyblock_existing-season"));
        var rejected = assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> resolver.invoke(null, level, "aerborne_skyblock_existing-season"));
        assertInstanceOf(IllegalArgumentException.class, rejected.getCause());
    }

    @Test void historicalDatabaseMigrationsAreNotCosmeticallyRewritten() throws Exception {
        Map<String, String> checksums = Map.of(
            "V001__foundation.sql", "E4ED2F31C7E977B3D2C1306BF0E5C9A73DE8554A0163956BCF21F5CF394664F1",
            "V002__progression.sql", "3977684B7F1B044195C015ECD92D6572EED9C95B781DBD44337351AD4695FA65",
            "V003__skill_talents.sql", "F6BE56AA0C503E62AE00A6C1C9E46E96B2D8940288CB6344B5AEAE4EE1738961",
            "V004__npc_reputation.sql", "94590CB131B88E9F0B24DBC577AF8EDC4A50511196A40BC3CFC797E5C4EDEF85");
        for (var entry : checksums.entrySet()) {
            try (var stream = getClass().getResourceAsStream("/db/migration/" + entry.getKey())) {
                assertNotNull(stream);
                String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                assertEquals(entry.getValue(), HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8))), entry.getKey());
            }
        }
    }

    @Test void brandingReadsDoNotMutateSecurityOrGameplayConfiguration() throws Exception {
        var configuration = yaml("/config.yml");
        configuration.set("skyblock.display-name", "Hearthsky");
        configuration.set("storage.postgres.jdbc-url", "jdbc:postgresql://private-host:5432/existing_database");
        configuration.set("integration.webhook", "private-integration-placeholder");
        configuration.set("season.id", "existing-season");
        String before = configuration.saveToString();
        var branding = ServerBranding.read(configuration, "Hearthsky", true);
        branding.motd();
        branding.welcome();
        branding.tabHeaderMarkup();
        assertEquals(before, configuration.saveToString(), "Legacy display adaptation must be read-only");
        assertEquals("autonomous", configuration.getString("anti-cheat.mode"));
        assertEquals(12.0D, configuration.getDouble("anti-cheat.alert-score"));
        assertEquals(24.0D, configuration.getDouble("anti-cheat.setback-score"));
        assertEquals(50.0D, configuration.getDouble("anti-cheat.kick-score"));
        assertEquals(0.10D, configuration.getDouble("anti-cheat.decay-per-second"));
        assertTrue(configuration.getBoolean("anti-cheat.movement-simulation"));
        assertEquals(9.0D, WatchdogMath.decay(10.0D, 10_000L, .1D));
        assertEquals(2.0D, WatchdogMath.distanceToBox(0, 1, 0, 2, 0, -1, 3, 2, 1));
    }

    @Test void rebrandDoesNotPublishAnAdminListenerOrChangeReleaseNetworking() throws Exception {
        var panel = yaml("/security/watchdog-panel.yml");
        assertFalse(panel.getBoolean("enabled"));
        assertEquals("127.0.0.1", panel.getString("bind"));
        assertEquals(9029, panel.getInt("port"));
        assertEquals("", panel.getString("hash"));
        assertEquals("", panel.getString("salt"));
        assertEquals(600_000, panel.getInt("iterations"));
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("../deploy/release/server.properties"))) { properties.load(reader); }
        assertEquals("", properties.getProperty("server-ip"));
        assertEquals("25565", properties.getProperty("server-port"));
        assertEquals("world", properties.getProperty("level-name"));
        assertEquals("true", properties.getProperty("online-mode"));
        assertEquals("false", properties.getProperty("enable-rcon"));
        assertEquals("false", properties.getProperty("enable-query"));
        String velocity = Files.readString(Path.of("../deploy/velocity/velocity.toml"));
        assertTrue(velocity.contains("bind = \"0.0.0.0:25565\""));
        assertTrue(velocity.contains("smp = \"paper:25566\""));
        assertTrue(velocity.contains("forwarding-secret-file = \"forwarding.secret\""));
        assertTrue(velocity.contains("player-info-forwarding-mode = \"modern\""));
    }

    private static YamlConfiguration yaml(String resource) throws Exception {
        try (var stream = AerborneCompatibilityTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            var configuration = new YamlConfiguration();
            // Permission nodes are literal dotted YAML keys, not configuration paths.
            if (resource.equals("/plugin.yml")) configuration.options().pathSeparator('/');
            configuration.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return configuration;
        }
    }
}
