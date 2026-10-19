package net.nerdsmp.paper.digsite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static net.nerdsmp.paper.digsite.DigSiteDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class DigSiteStoreTest {
    @TempDir Path directory;
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID GENERATION = UUID.randomUUID();

    private DigSiteDefinition site(String id, int x, UUID world, boolean enabled) {
        return new DigSiteDefinition(id, "quarry", world, GENERATION, enabled, 20,
            List.of(new Node(x, 64, 0, "GRASS_BLOCK", "minecraft:grass_block[snowy=false]")));
    }

    @Test void roundTripPreservesWorldGenerationAndExactImmutableSnapshots() throws Exception {
        Path file = directory.resolve("nested/dig-sites.yml");
        DigSiteStore store = new DigSiteStore(file.toFile());
        assertTrue(store.all().isEmpty());
        assertFalse(Files.exists(file));
        DigSiteDefinition definition = site("pit", 0, WORLD, true);
        store.put(definition);
        DigSiteStore reloaded = new DigSiteStore(file.toFile());
        assertEquals(definition, reloaded.find("pit").orElseThrow());
        assertEquals(GENERATION, reloaded.find("pit").orElseThrow().generation());
        assertThrows(UnsupportedOperationException.class, () -> reloaded.all().clear());
        assertTrue(reloaded.find("missing").isEmpty());
        assertTrue(reloaded.find(null).isEmpty());
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count(), "Successful atomic commit leaves no pending file");
        }
    }

    @Test void overlapIncludesDisabledDefinitionsButSameCoordinatesInOtherWorldsAreValid() throws Exception {
        DigSiteStore store = new DigSiteStore(directory.resolve("dig-sites.yml").toFile());
        store.put(site("pit", 0, WORLD, false));
        store.put(site("pit", 0, WORLD, true));
        store.put(site("adjacent", 1, WORLD, false));
        store.put(site("other_world", 0, UUID.randomUUID(), true));
        assertThrows(IllegalArgumentException.class, () -> store.put(site("overlap", 0, WORLD, false)));
        assertThrows(IllegalArgumentException.class, () -> store.put(site("adjacent", 0, WORLD, true)));
        assertEquals(List.of("adjacent", "other_world", "pit"), store.all().stream().map(DigSiteDefinition::id).toList());
    }

    @Test void failedWritesNeverPublishCreatedUpdatedOrRemovedDefinitions() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        AtomicBoolean fail = new AtomicBoolean();
        DigSiteStore store = new DigSiteStore(file.toFile(), (path, text) -> {
            if (fail.get()) throw new IOException("disk full");
            Files.writeString(path, text);
        });
        DigSiteDefinition original = site("pit", 0, WORLD, true);
        store.put(original);
        String before = Files.readString(file);
        fail.set(true);
        assertThrows(IOException.class, () -> store.put(site("new", 2, WORLD, false)));
        assertThrows(IOException.class, () -> store.put(site("pit", 2, WORLD, false)));
        assertThrows(IOException.class, () -> store.remove("pit"));
        assertEquals(List.of(original), store.all());
        assertEquals(before, Files.readString(file));
        assertEquals(original, new DigSiteStore(file.toFile()).find("pit").orElseThrow());
    }

    @Test void aBlockedParentCannotPublishAnUnsavedDefinition() throws Exception {
        Path blocker = directory.resolve("not-a-directory");
        Files.writeString(blocker, "keep");
        DigSiteStore store = new DigSiteStore(blocker.resolve("dig-sites.yml").toFile());
        assertThrows(IOException.class, () -> store.put(site("pit", 0, WORLD, true)));
        assertTrue(store.all().isEmpty());
        assertEquals("keep", Files.readString(blocker));
    }

    @Test void removalAndRecreationUseExplicitNewGenerationIdentity() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        DigSiteStore store = new DigSiteStore(file.toFile());
        store.put(site("pit", 0, WORLD, true));
        store.remove("pit");
        assertTrue(new DigSiteStore(file.toFile()).all().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.remove("pit"));
        UUID replacementGeneration = UUID.randomUUID();
        DigSiteDefinition replacement = new DigSiteDefinition("pit", "quarry", WORLD, replacementGeneration,
            true, 20, List.of(new Node(0, 64, 0, "DIRT", "minecraft:dirt")));
        store.put(replacement);
        assertEquals(replacementGeneration, new DigSiteStore(file.toFile()).find("pit").orElseThrow().generation());
        assertNotEquals(GENERATION, replacementGeneration);
    }

    @Test void missingUnknownFieldsMalformedYamlAndExactTypeErrorsFailClosed() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        new DigSiteStore(file.toFile()).put(site("pit", 0, WORLD, true));
        String base = Files.readString(file);
        assertTrue(base.contains("cooldown-seconds: 20"));
        for (String invalid : List.of("", "sites: [broken", "schema-version: 1\nsites: []\n",
            base.replace("schema-version: 1", "schema-version: 2"),
            base.replace("schema-version: 1", "schema-version: '1'"),
            base.replace("enabled: true", "enabled: 'true'"),
            base.replace("cooldown-seconds: 20", "cooldown-seconds: 20.0"),
            base.replace("cooldown-seconds: 20", "cooldown-seconds: 2147483648"),
            base.replace("cooldown-seconds: 20", "cooldown-seconds: 4"),
            base.replace("cooldown-seconds: 20", "cooldown-seconds: 3601"),
            base.replace("    zone-id: quarry\n", ""),
            base.replace("    zone-id: quarry", "    zone-id: quarry\n    unknown: true"),
            base.replace("material: GRASS_BLOCK", "material: GRASS_BLOCK\n      unknown: true"),
            base.replace("material: GRASS_BLOCK", "material: 123"),
            base.replace("x: 0", "x: 0.0"),
            base.replace("y: 64", "y: 320"),
            base.replace("block-data: minecraft:grass_block[snowy=false]", "block-data: minecraft:dirt"),
            base.replace("    zone-id: quarry", "    zone-id: Upper"))) {
            assertNotEquals(base, invalid, "Malformed fixture must actually alter the file");
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> new DigSiteStore(file.toFile()), invalid);
            assertEquals(invalid, Files.readString(file), "Invalid data must not be rewritten or discarded");
        }
    }

    @Test void abbreviatedWorldAndGenerationUuidsAreRejectedRatherThanExpanded() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        new DigSiteStore(file.toFile()).put(site("pit", 0, WORLD, true));
        String base = Files.readString(file);
        for (String invalid : List.of(base.replace(WORLD.toString(), "1-2-3-4-5"),
            base.replace(GENERATION.toString(), "1-2-3-4-5"),
            base.replace("generation: " + GENERATION, "generation: 123"))) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> new DigSiteStore(file.toFile()));
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test void duplicateKeysAndCollectionAliasesCannotProduceAmbiguousDefinitions() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        new DigSiteStore(file.toFile()).put(site("pit", 0, WORLD, true));
        String base = Files.readString(file);
        for (String invalid : List.of(base.replace("enabled: true", "enabled: true\n    enabled: false"),
            base.replace("schema-version: 1", "schema-version: 1\nschema-version: 1"),
            "schema-version: 1\nsites: &definitions\n  pit: *definitions\n")) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> new DigSiteStore(file.toFile()));
        }
    }

    @Test void oversizedFilesAndDirectoryTargetsAreRejectedWithoutRewriting() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        Files.writeString(file, " ".repeat(4_194_305));
        assertThrows(IOException.class, () -> new DigSiteStore(file.toFile()));
        assertEquals(4_194_305, Files.size(file));
        Path folder = Files.createDirectory(directory.resolve("folder.yml"));
        assertThrows(IOException.class, () -> new DigSiteStore(folder.toFile()));
        assertTrue(Files.isDirectory(folder));
    }

    @Test void maximumSiteAndGlobalNodeCountsAreEnforcedBeforePublication() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        DigSiteStore countStore = new DigSiteStore(file.toFile(), (path, text) -> { });
        for (int i = 0; i < MAX_SITES; i++) {
            countStore.put(new DigSiteDefinition("site_" + i, "quarry", WORLD, UUID.randomUUID(), false, 20, List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> countStore.put(site("overflow", 0, WORLD, false)));
        assertEquals(MAX_SITES, countStore.all().size());

        DigSiteStore nodeStore = new DigSiteStore(file.toFile(), (path, text) -> { });
        for (int i = 0; i < MAX_TOTAL_NODES / MAX_NODES; i++) {
            int offset = i * MAX_NODES;
            List<Node> nodes = IntStream.range(offset, offset + MAX_NODES)
                .mapToObj(x -> new Node(x, 64, 0, "DIRT", "minecraft:dirt")).toList();
            nodeStore.put(new DigSiteDefinition("site_" + i, "quarry", WORLD, UUID.randomUUID(), true, 20, nodes));
        }
        assertEquals(MAX_TOTAL_NODES, nodeStore.all().stream().mapToInt(site -> site.nodes().size()).sum());
        assertThrows(IllegalArgumentException.class, () -> nodeStore.put(site("overflow", MAX_TOTAL_NODES, WORLD, false)));
        assertEquals(4, nodeStore.all().size());
    }

    @Test void loadedOverlappingSitesFailClosedEvenWhenOneIsDisabled() throws Exception {
        Path file = directory.resolve("dig-sites.yml");
        DigSiteStore store = new DigSiteStore(file.toFile());
        store.put(site("one", 0, WORLD, true));
        store.put(site("two", 1, WORLD, false));
        String invalid = Files.readString(file).replace("x: 1", "x: 0");
        Files.writeString(file, invalid);
        assertThrows(IOException.class, () -> new DigSiteStore(file.toFile()));
        assertEquals(invalid, Files.readString(file));
    }
}
