package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.Zone;
import static org.junit.jupiter.api.Assertions.*;

final class ExpeditionRevisionTest {
    @TempDir Path temporary;

    @Test void revisionNamesAndManifestsNeverReuseTheLegacyTargets() {
        File data = temporary.resolve("plugin").toFile();
        assertEquals("nerdsmp_expeditions_season-1", ExpeditionWorld.worldName("Season-1", 1));
        assertEquals("nerdsmp_expeditions_season-1_r2", ExpeditionWorld.worldName("Season-1", 2));
        assertEquals(new File(data, "expeditions/Season-1-world.yml"), ExpeditionWorld.manifestFile(data, "Season-1", 1));
        assertEquals(new File(data, "expeditions/Season-1-world-r2.yml"), ExpeditionWorld.manifestFile(data, "Season-1", 2));
        Path oldWorld = worldDirectory(1), newWorld = worldDirectory(2);
        assertNotEquals(oldWorld, newWorld);
        assertEquals(temporary.resolve("level/dimensions/minecraft/nerdsmp_expeditions_test_r2").toAbsolutePath(), newWorld);
    }

    @Test void maximumLengthSeasonSupportsTheRevisionSuffixWithoutPathTraversal() {
        String season = "s".repeat(64);
        String name = ExpeditionWorld.worldName(season, 2);
        assertEquals("nerdsmp_expeditions_" + season + "_r2", name);
        assertTrue(ExpeditionWorld.dimensionDirectory(temporary, name).startsWith(temporary.toAbsolutePath()));
        for (String invalid : List.of("", "../world", "a/b", "a\\b", "s".repeat(65), "a:b")) {
            assertThrows(IllegalArgumentException.class, () -> ExpeditionWorld.worldName(invalid, 2));
            assertThrows(IllegalArgumentException.class, () -> ExpeditionWorld.manifestFile(temporary.toFile(), invalid, 2));
        }
        assertThrows(IllegalArgumentException.class, () -> ExpeditionWorld.worldName(null, 2));
        assertThrows(IllegalArgumentException.class, () -> ExpeditionWorld.worldName("test", 3));
        assertThrows(IllegalArgumentException.class, () -> ExpeditionWorld.dimensionDirectory(temporary, "../world"));
    }

    @Test void aNewManifestIsStrictlyRevisionTwoAndDoesNotRequireCreatingALegacyWorld() throws Exception {
        File manifest = manifest(2);
        var yaml = ExpeditionWorld.prepareManifest(manifest, name(2), 2, worldDirectory(2), false);
        assertEquals(1, yaml.getInt("schema"));
        assertEquals(2, yaml.getInt("layout"));
        assertEquals(2, yaml.getInt("template"));
        assertEquals(name(2), yaml.getString("world"));
        assertFalse(yaml.contains("uuid"));
        assertTrue(manifest.isFile());
        assertFalse(manifest(1).exists());
        assertFalse(Files.exists(worldDirectory(1)));
    }

    @Test void readingALegacyManifestDoesNotRewriteItsBytesOrAdministratorMetadata() throws Exception {
        UUID oldId = UUID.randomUUID();
        File legacy = writeManifest(1, oldId);
        byte[] original = Files.readAllBytes(legacy.toPath());
        var loaded = ExpeditionWorld.prepareManifest(legacy, name(1), 1, worldDirectory(1), true);
        assertEquals(oldId.toString(), loaded.getString("uuid"));
        assertEquals("keep this evidence", loaded.getString("operator-note"));
        assertArrayEquals(original, Files.readAllBytes(legacy.toPath()));
    }

    @Test void openingRevisionTwoLeavesTheLegacyManifestAndRegionFilesUntouched() throws Exception {
        File legacy = writeManifest(1, UUID.randomUUID());
        Path region = worldDirectory(1).resolve("region/r.0.0.mca");
        Files.createDirectories(region.getParent());
        Files.writeString(region, "existing player-edited terrain");
        byte[] manifestBytes = Files.readAllBytes(legacy.toPath());
        byte[] regionBytes = Files.readAllBytes(region);
        ExpeditionWorld.prepareManifest(manifest(2), name(2), 2, worldDirectory(2), false);
        assertArrayEquals(manifestBytes, Files.readAllBytes(legacy.toPath()));
        assertArrayEquals(regionBytes, Files.readAllBytes(region));
        assertTrue(manifest(2).isFile());
    }

    @Test void anExistingUnownedWorldIsNeverAdopted() throws Exception {
        Files.createDirectories(worldDirectory(2));
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.prepareManifest(manifest(2), name(2), 2, worldDirectory(2), false));
        assertFalse(manifest(2).exists());
        Path absent = temporary.resolve("absent-world");
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.prepareManifest(manifest(2), name(2), 2, absent, true));
        assertFalse(manifest(2).exists());
    }

    @Test void aMissingLegacyManifestIsNotCreatedAsPartOfTheUpgrade() {
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.prepareManifest(manifest(1), name(1), 1, worldDirectory(1), false));
        assertFalse(manifest(1).exists());
    }

    @Test void missingLegacyUuidRequiresReviewInsteadOfSilentlyAssigningANewIdentity() throws Exception {
        File legacy = writeManifest(1, null);
        byte[] bytes = Files.readAllBytes(legacy.toPath());
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.prepareManifest(legacy, name(1), 1, worldDirectory(1), false));
        assertArrayEquals(bytes, Files.readAllBytes(legacy.toPath()));
    }

    @Test void aSavedWorldUuidCannotBeReplacedByGeneratingAnEmptyWorld() throws Exception {
        File manifest = writeManifest(2, UUID.randomUUID());
        byte[] bytes = Files.readAllBytes(manifest.toPath());
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.prepareManifest(manifest, name(2), 2, temporary.resolve("missing"), false));
        assertArrayEquals(bytes, Files.readAllBytes(manifest.toPath()));
    }

    @Test void wrongRevisionTemplateWorldOrSchemaIsRejectedWithoutModification() throws Exception {
        for (String field : List.of("schema", "layout", "template", "world", "uuid")) {
            File manifest = writeManifest(2, UUID.randomUUID());
            var yaml = new YamlConfiguration();
            yaml.load(manifest);
            yaml.set(field, switch (field) {
                case "schema" -> 9;
                case "layout", "template" -> 1;
                case "world" -> name(1);
                default -> "invalid-uuid";
            });
            AtomicYamlFiles.save(yaml, manifest);
            byte[] bytes = Files.readAllBytes(manifest.toPath());
            assertThrows(Exception.class,
                () -> ExpeditionWorld.prepareManifest(manifest, name(2), 2, worldDirectory(2), false), field);
            assertArrayEquals(bytes, Files.readAllBytes(manifest.toPath()));
        }
    }

    @Test void anInterruptedRevisionTwoCreationRetainsItsManifestRatherThanReplacingIt() throws Exception {
        var first = ExpeditionWorld.prepareManifest(manifest(2), name(2), 2, worldDirectory(2), false);
        byte[] bytes = Files.readAllBytes(manifest(2).toPath());
        Files.createDirectories(worldDirectory(2));
        var resumed = ExpeditionWorld.prepareManifest(manifest(2), name(2), 2, worldDirectory(2), true);
        assertEquals(first.saveToString(), resumed.saveToString());
        assertArrayEquals(bytes, Files.readAllBytes(manifest(2).toPath()));
    }

    @Test void loadedWorldIdentityAndStorageMustMatchBeforeAnyManifestUpdate() {
        UUID id = UUID.randomUUID();
        var yaml = new YamlConfiguration();
        yaml.set("uuid", id.toString());
        String key = "minecraft:" + name(2);
        Path path = worldDirectory(2);
        assertDoesNotThrow(() -> ExpeditionWorld.verifyLoadedWorld(yaml, key, path, key, path, id));
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.verifyLoadedWorld(yaml, key, path, key, path, UUID.randomUUID()));
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.verifyLoadedWorld(yaml, key, path, "minecraft:" + name(1), path, id));
        assertThrows(IllegalStateException.class,
            () -> ExpeditionWorld.verifyLoadedWorld(yaml, key, path, key, worldDirectory(1), id));
        assertEquals(id.toString(), yaml.getString("uuid"));
    }

    @Test void staffPreviewRequiresTheSpecificPermissionAndRechecksRevocation() {
        AtomicBoolean bypass = new AtomicBoolean(false);
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> {
                assertEquals("hasPermission", method.getName());
                assertEquals("nerdsmp.progression.bypass", args[0]);
                return bypass.get();
            });
        assertFalse(ExpeditionController.routeAccessible(player, false));
        assertTrue(ExpeditionController.routeAccessible(player, true));
        bypass.set(true);
        assertTrue(ExpeditionController.routeAccessible(player, false));
        bypass.set(false);
        assertFalse(ExpeditionController.routeAccessible(player, false));
    }

    @Test void everyNewArrivalUsesRevisionTwoCentersAtHeightNinetySeven() {
        for (Zone zone : Zone.values()) {
            var arrival = ExpeditionController.arrival(zone);
            assertEquals(ExpeditionPlanV2.centerX(zone), arrival.x());
            assertEquals(97, arrival.y());
            assertEquals(ExpeditionPlanV2.centerZ(zone) + (zone == Zone.CITY ? 0 : zone == Zone.HARBOR ? 23 : 19), arrival.z());
            assertSame(zone, ExpeditionPlanV2.zoneAt(arrival.x(), arrival.z()).orElseThrow());
            if (zone != Zone.CITY) assertTrue(arrival.x() != zone.x || arrival.z() != zone.z + 19);
        }
    }

    private String name(int revision) { return ExpeditionWorld.worldName("test", revision); }
    private File manifest(int revision) { return ExpeditionWorld.manifestFile(temporary.resolve("plugin").toFile(), "test", revision); }
    private Path worldDirectory(int revision) { return ExpeditionWorld.dimensionDirectory(temporary.resolve("level"), name(revision)); }
    private File writeManifest(int revision, UUID id) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 1); yaml.set("layout", revision); yaml.set("template", revision); yaml.set("world", name(revision));
        if (id != null) yaml.set("uuid", id.toString());
        yaml.set("operator-note", "keep this evidence");
        Files.createDirectories(worldDirectory(revision));
        File manifest = manifest(revision);
        AtomicYamlFiles.save(yaml, manifest);
        return manifest;
    }
}
