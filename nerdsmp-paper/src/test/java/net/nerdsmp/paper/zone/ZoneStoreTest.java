package net.nerdsmp.paper.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static net.nerdsmp.paper.zone.ZoneDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ZoneStoreTest {
    @TempDir Path directory;
    private static final UUID WORLD = UUID.randomUUID();
    private ZoneDefinition zone(String id, int x, UUID world) {
        return new ZoneDefinition(id, "Old Crypts", world, new Bounds(x, 60, 0, x + 10, 80, 10), true, false, 8, 20, 48,
            List.of(new SpawnPoint(x + 0.5, 65, 0.5, 37.4f)), List.of(new MobEntry("custom:veil_stalker", 5, 10), new MobEntry("vanilla:zombie", 10, 8)));
    }
    @Test void roundTripPreservesUnloadedWorldUuidAndExactImmutableDefinitions() throws Exception {
        Path file = directory.resolve("nested/zones.yml");
        ZoneStore store = new ZoneStore(file.toFile());
        assertTrue(store.all().isEmpty()); assertFalse(Files.exists(file));
        var zone = zone("crypts", 0, WORLD);
        store.put(zone);
        var reloaded = new ZoneStore(file.toFile());
        assertEquals(zone, reloaded.find("crypts").orElseThrow());
        assertEquals(zone, reloaded.at(WORLD, 10.99, 70, 10.99).orElseThrow());
        assertTrue(reloaded.at(UUID.randomUUID(), 1, 70, 1).isEmpty());
        assertTrue(reloaded.at(WORLD, 11, 70, 1).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> reloaded.all().clear());
        try (var stream = Files.list(file.getParent())) { assertEquals(1, stream.count(), "No pending file remains after commit"); }
    }
    @Test void overlapIsRejectedButReplacementAdjacentBoundsAndOtherWorldsWork() throws Exception {
        ZoneStore store = new ZoneStore(directory.resolve("zones.yml").toFile());
        store.put(zone("crypts", 0, WORLD));
        store.put(zone("crypts", 0, WORLD));
        store.put(zone("quarry", 11, WORLD));
        store.put(zone("other_world", 0, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> store.put(zone("overlap", 10, WORLD)));
        assertEquals(3, store.all().size());
        assertEquals("quarry", store.at(WORLD, 11, 70, 0).orElseThrow().id());
        assertEquals(List.of("crypts", "other_world", "quarry"), store.all().stream().map(ZoneDefinition::id).toList());
    }
    @Test void failedWritesDoNotPublishNewUpdatedOrRemovedZones() throws Exception {
        Path file = directory.resolve("zones.yml");
        AtomicBoolean fail = new AtomicBoolean();
        ZoneStore store = new ZoneStore(file.toFile(), (path, text) -> {
            if (fail.get()) throw new IOException("disk full");
            Files.writeString(path, text);
        });
        var original = zone("crypts", 0, WORLD);
        store.put(original);
        String before = Files.readString(file);
        fail.set(true);
        assertThrows(IOException.class, () -> store.put(zone("second", 20, WORLD)));
        assertThrows(IOException.class, () -> store.put(zone("crypts", 20, WORLD)));
        assertThrows(IOException.class, () -> store.remove("crypts"));
        assertEquals(List.of(original), store.all());
        assertEquals(before, Files.readString(file));
        assertEquals(original, new ZoneStore(file.toFile()).find("crypts").orElseThrow());
    }
    @Test void removalPersistsAndEmptyStoreCanBeReloaded() throws Exception {
        Path file = directory.resolve("zones.yml");
        ZoneStore store = new ZoneStore(file.toFile());
        store.put(zone("crypts", 0, WORLD));
        store.remove("crypts");
        assertTrue(new ZoneStore(file.toFile()).all().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.remove("unknown"));
    }
    @Test void malformedSchemaTypesFieldsAndYamlNeverSilentlyResetTheFile() throws Exception {
        Path file = directory.resolve("zones.yml");
        ZoneStore valid = new ZoneStore(file.toFile()); valid.put(zone("crypts", 0, WORLD));
        String base = Files.readString(file);
        for (String invalid : List.of("schema-version: [broken", "", base.replace("schema-version: 1", "schema-version: 99"),
            base.replace("enabled: true", "enabled: 'true'"), base.replace("cap: 8", "cap: 8.5"),
            base.replace("cap: 8", "cap: 2147483648"), base.replace("title: Old Crypts", "title: Old Crypts\n    admin: true"),
            base.replace(WORLD.toString(), "1-2-3-4-5"), base.replace("custom:veil_stalker", "vanilla:player"))) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> new ZoneStore(file.toFile()), invalid);
            assertEquals(invalid, Files.readString(file));
        }
    }
    @Test void duplicateKeysAndNonFinitePointsAreRejected() throws Exception {
        Path file = directory.resolve("zones.yml");
        ZoneStore valid = new ZoneStore(file.toFile()); valid.put(zone("crypts", 0, WORLD));
        String base = Files.readString(file);
        for (String invalid : List.of(base.replace("cap: 8", "cap: 8\n    cap: 64"), base.replace("x: 0.5", "x: .NaN"))) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> new ZoneStore(file.toFile()));
        }
    }
    @Test void loadedOverlappingZonesAreRejectedAndMaximumCountIsBounded() throws Exception {
        Path file = directory.resolve("zones.yml");
        ZoneStore store = new ZoneStore(file.toFile(), (path, yaml) -> { });
        for (int i = 0; i < MAX_ZONES; i++) store.put(zone("zone_" + i, i * 12, WORLD));
        assertThrows(IllegalArgumentException.class, () -> store.put(zone("overflow", 2000, WORLD)));
        assertEquals(MAX_ZONES, store.all().size());
        ZoneStore disk = new ZoneStore(file.toFile());
        disk.put(zone("one", 0, WORLD)); disk.put(zone("two", 20, WORLD));
        String yaml = Files.readString(file).replace("min-x: 20", "min-x: 0").replace("max-x: 30", "max-x: 10").replace("x: 20.5", "x: 0.5");
        Files.writeString(file, yaml);
        assertThrows(IOException.class, () -> new ZoneStore(file.toFile()));
    }
}
