package net.nerdsmp.paper.travel;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

final class TravelStoreTest {
    @TempDir Path directory;
    private final String unloadedWorld = UUID.randomUUID().toString();
    private File file() { return directory.resolve("travel.yml").toFile(); }
    private TravelStore.Destination destination(String world) {
        return new TravelStore.Destination(world, -25.5, 97, 83.25, 160.25F, -15.5F);
    }

    @Test void anAbsentFileStartsEmptyWithoutCreatingOrChoosingASpawn() {
        var store = new TravelStore(file());
        assertTrue(store.spawn().isEmpty());
        assertTrue(store.names().isEmpty());
        assertFalse(file().exists());
    }

    @Test void unresolvedSpawnAndWarpsSurviveUnrelatedEditsAndRestart() throws Exception {
        var legacy = new YamlConfiguration();
        put(legacy, "spawn", destination(unloadedWorld));
        put(legacy, "warps.OldHarbor", destination(unloadedWorld));
        legacy.set("operator.note", "Do not erase my saved destinations");
        legacy.set("warps.OldHarbor.display-note", List.of("hand placed", "future world"));
        AtomicYamlFiles.save(legacy, file());
        var store = new TravelStore(file());
        assertTrue(store.spawn().orElseThrow().resolve(id -> null).isEmpty());
        assertTrue(store.warp("oldharbor").orElseThrow().resolve(id -> null).isEmpty());
        assertEquals(List.of("oldharbor"), store.names());
        store.setWarp("mine", destination("loaded-world-name"));

        var restarted = new TravelStore(file());
        assertEquals(destination(unloadedWorld), restarted.spawn().orElseThrow());
        assertEquals(destination(unloadedWorld), restarted.warp("OLDHARBOR").orElseThrow());
        assertEquals(List.of("mine", "oldharbor"), restarted.names());
        var saved = new YamlConfiguration();
        saved.load(file());
        assertEquals("Do not erase my saved destinations", saved.getString("operator.note"));
        assertEquals(List.of("hand placed", "future world"), saved.getStringList("warps.OldHarbor.display-note"));
    }

    @Test void unloadedWorldCanResolveLaterWithoutReloadingOrWritingAnyRecord() throws Exception {
        var store = new TravelStore(file());
        store.setSpawn(destination(unloadedWorld));
        byte[] bytes = Files.readAllBytes(file().toPath());
        AtomicReference<World> loaded = new AtomicReference<>();
        var point = store.spawn().orElseThrow();
        assertTrue(point.resolve(id -> loaded.get()).isEmpty());
        World world = world(UUID.fromString(unloadedWorld));
        loaded.set(world);
        Location location = point.resolve(id -> { assertEquals(unloadedWorld, id); return loaded.get(); }).orElseThrow();
        assertSame(world, location.getWorld());
        assertEquals(-25.5, location.getX());
        assertEquals(97, location.getY());
        assertEquals(83.25, location.getZ());
        assertEquals(160.25F, location.getYaw());
        assertEquals(-15.5F, location.getPitch());
        assertArrayEquals(bytes, Files.readAllBytes(file().toPath()));
    }

    @Test void resolvingAndRecordingLocationsNeverExposesMutableStoredState() {
        World world = world(UUID.randomUUID());
        var original = new Location(world, 12, 77, -14, 25F, 15F);
        var point = TravelStore.Destination.from(original);
        original.setX(999);
        var resolved = point.resolve(id -> world).orElseThrow();
        resolved.setZ(999);
        resolved.setYaw(0);
        var next = point.resolve(id -> world).orElseThrow();
        assertEquals(12, next.getX());
        assertEquals(-14, next.getZ());
        assertEquals(25F, next.getYaw());
        assertNotSame(resolved, next);
    }

    @Test void legacyWorldNamesRemainNamesUntilExplicitlyReplaced() {
        var store = new TravelStore(file());
        var named = destination("my-unloaded-world");
        store.setWarp("Harbor", named);
        var restarted = new TravelStore(file());
        assertEquals(named, restarted.warp("harbor").orElseThrow());
        assertTrue(restarted.warp("harbor").orElseThrow().resolve(name -> {
            assertEquals("my-unloaded-world", name);
            return null;
        }).isEmpty());
    }

    @Test void changingSpawnDoesNotAlterAnUnresolvedWarpOrItsMetadata() throws Exception {
        var yaml = new YamlConfiguration();
        put(yaml, "warps.mine", destination(unloadedWorld));
        yaml.set("warps.mine.operator-note", "reserved for later");
        AtomicYamlFiles.save(yaml, file());
        var store = new TravelStore(file());
        store.setSpawn(destination(UUID.randomUUID().toString()));
        assertEquals(destination(unloadedWorld), new TravelStore(file()).warp("mine").orElseThrow());
        yaml.load(file());
        assertEquals("reserved for later", yaml.getString("warps.mine.operator-note"));
    }

    @Test void overwritingALegacyMixedCaseWarpDoesNotCreateCaseDuplicatesOrDropMetadata() throws Exception {
        var yaml = new YamlConfiguration();
        put(yaml, "warps.Harbor", destination(unloadedWorld));
        yaml.set("warps.Harbor.operator-note", "keep");
        AtomicYamlFiles.save(yaml, file());
        var store = new TravelStore(file());
        var replacement = destination("new-world");
        store.setWarp("HARBOR", replacement);
        assertEquals(List.of("harbor"), store.names());
        assertEquals(replacement, new TravelStore(file()).warp("harbor").orElseThrow());
        yaml.load(file());
        assertFalse(yaml.contains("warps.harbor"));
        assertEquals("keep", yaml.getString("warps.Harbor.operator-note"));
        assertTrue(store.deleteWarp("hArBoR"));
        assertTrue(new TravelStore(file()).names().isEmpty());
    }

    @Test void aFailedSpawnUpdatePreservesThePublishedAndDurableDestination() throws Exception {
        var original = destination(unloadedWorld);
        var seed = new TravelStore(file());
        seed.setSpawn(original);
        byte[] bytes = Files.readAllBytes(file().toPath());
        var failing = failingStore();
        assertThrows(IllegalStateException.class, () -> failing.setSpawn(destination("replacement")));
        assertEquals(original, failing.spawn().orElseThrow());
        assertEquals(original, new TravelStore(file()).spawn().orElseThrow());
        assertArrayEquals(bytes, Files.readAllBytes(file().toPath()));
    }

    @Test void failedWarpAddsOverwritesAndDeletesLeaveTheSameSnapshotAndFile() throws Exception {
        var original = destination(unloadedWorld);
        new TravelStore(file()).setWarp("mine", original);
        var failing = failingStore();
        byte[] bytes = Files.readAllBytes(file().toPath());
        assertThrows(IllegalStateException.class, () -> failing.setWarp("new", destination("other")));
        assertThrows(IllegalStateException.class, () -> failing.setWarp("mine", destination("other")));
        assertThrows(IllegalStateException.class, () -> failing.deleteWarp("mine"));
        assertTrue(failing.warp("new").isEmpty());
        assertEquals(original, failing.warp("mine").orElseThrow());
        assertEquals(List.of("mine"), failing.names());
        assertArrayEquals(bytes, Files.readAllBytes(file().toPath()));
    }

    @Test void actualFilesystemWriteFailureDoesNotPublishANewSpawn() throws Exception {
        Path parentFile = directory.resolve("not-a-directory");
        Files.writeString(parentFile, "obstacle");
        var store = new TravelStore(parentFile.resolve("travel.yml").toFile());
        assertThrows(IllegalStateException.class, () -> store.setSpawn(destination(unloadedWorld)));
        assertTrue(store.spawn().isEmpty());
        assertEquals("obstacle", Files.readString(parentFile));
    }

    @Test void malformedYamlFailsClosedWithoutReplacingItsBytes() throws Exception {
        Files.writeString(file().toPath(), "warps: [broken");
        byte[] bytes = Files.readAllBytes(file().toPath());
        assertThrows(IllegalStateException.class, () -> new TravelStore(file()));
        assertArrayEquals(bytes, Files.readAllBytes(file().toPath()));
    }

    @Test void malformedSavedRecordsAndUnknownSchemasAreRejectedRatherThanSilentlyDropped() throws Exception {
        for (Map.Entry<String, Object> invalid : List.<Map.Entry<String, Object>>of(
            Map.entry("schema", 2), Map.entry("schema", "1"), Map.entry("warps", "not-a-map"),
            Map.entry("spawn", "not-a-location"), Map.entry("spawn.world", 123),
            Map.entry("spawn.x", "not-a-number"), Map.entry("spawn.y", Double.NaN),
            Map.entry("spawn.yaw", Double.POSITIVE_INFINITY))) {
            var yaml = new YamlConfiguration();
            put(yaml, "spawn", destination(unloadedWorld));
            yaml.set(invalid.getKey(), invalid.getValue());
            AtomicYamlFiles.save(yaml, file());
            byte[] bytes = Files.readAllBytes(file().toPath());
            assertThrows(IllegalStateException.class, () -> new TravelStore(file()), invalid.getKey());
            assertArrayEquals(bytes, Files.readAllBytes(file().toPath()));
        }
    }

    @Test void ambiguousCaseInsensitiveWarpNamesAreRejectedWithoutChoosingAWinner() throws Exception {
        var yaml = new YamlConfiguration();
        put(yaml, "warps.Mine", destination(unloadedWorld));
        put(yaml, "warps.mine", destination("another-world"));
        AtomicYamlFiles.save(yaml, file());
        assertThrows(IllegalStateException.class, () -> new TravelStore(file()));
    }

    @Test void optionalLegacyAnglesDefaultToZeroButMissingCoordinatesAreRejected() throws Exception {
        var yaml = new YamlConfiguration();
        put(yaml, "spawn", destination(unloadedWorld));
        yaml.set("spawn.yaw", null); yaml.set("spawn.pitch", null);
        AtomicYamlFiles.save(yaml, file());
        var point = new TravelStore(file()).spawn().orElseThrow();
        assertEquals(0F, point.yaw()); assertEquals(0F, point.pitch());
        yaml.set("spawn.z", null);
        AtomicYamlFiles.save(yaml, file());
        assertThrows(IllegalStateException.class, () -> new TravelStore(file()));
    }

    @Test void invalidNamesAndNonFiniteCoordinatesCannotReachTheWriter() {
        var store = new TravelStore(file(), (yaml, file) -> fail("Invalid data reached writer"));
        for (String name : List.of("", "has space", "a.b", "x".repeat(25))) {
            assertThrows(IllegalArgumentException.class, () -> store.setWarp(name, destination(unloadedWorld)));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new TravelStore.Destination(unloadedWorld, Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new TravelStore.Destination(" ", 0, 0, 0, 0, 0));
        assertFalse(store.deleteWarp("unknown"));
        assertTrue(store.names().isEmpty());
    }

    @Test void warpNamesAreAnImmutableSnapshot() {
        var store = new TravelStore(file());
        store.setWarp("first", destination(unloadedWorld));
        var before = store.names();
        assertThrows(UnsupportedOperationException.class, () -> before.add("injected"));
        store.setWarp("second", destination(unloadedWorld));
        assertEquals(List.of("first"), before);
        assertEquals(List.of("first", "second"), store.names());
    }

    private TravelStore failingStore() {
        return new TravelStore(file(), (yaml, file) -> { throw new IOException("simulated full disk"); });
    }
    private static World world(UUID id) {
        return (World) Proxy.newProxyInstance(TravelStoreTest.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
    private static void put(YamlConfiguration yaml, String path, TravelStore.Destination point) {
        yaml.set(path + ".world", point.world());
        yaml.set(path + ".x", point.x()); yaml.set(path + ".y", point.y()); yaml.set(path + ".z", point.z());
        yaml.set(path + ".yaw", point.yaw()); yaml.set(path + ".pitch", point.pitch());
    }
}
