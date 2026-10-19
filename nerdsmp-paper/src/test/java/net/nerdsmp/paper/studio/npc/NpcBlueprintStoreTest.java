package net.nerdsmp.paper.studio.npc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NpcBlueprintStoreTest {
    @TempDir Path folder;
    private final NpcBlueprint npc = NpcBlueprints.draft("healer", "guild");
    @Test void missingFileIsEmptyAndPublicationRoundTrips() throws Exception {
        Path path = folder.resolve("engine-npcs.yml"); var store = new NpcBlueprintStore(path.toFile(), NpcBlueprintTest.base());
        assertTrue(store.all().isEmpty()); assertFalse(Files.exists(path));
        store.put(npc, null); assertEquals(List.of(npc), store.all());
        assertEquals(npc, new NpcBlueprintStore(path.toFile(), NpcBlueprintTest.base()).find(npc.id()).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> store.all().clear());
    }
    @Test void staleCreationAndReplacementAreRejectedWithoutDiskMutation() throws Exception {
        Path path = folder.resolve("engine-npcs.yml"); var store = new NpcBlueprintStore(path.toFile(), NpcBlueprintTest.base()); store.put(npc, null);
        String before = Files.readString(path);
        assertThrows(IllegalStateException.class, () -> store.put(npc, null));
        var changed = NpcStudioPolicy.details(npc, "New Name", "", npc.type(), npc.faction(), npc.shop(), 8);
        store.put(changed, npc); String latest = Files.readString(path);
        assertThrows(IllegalStateException.class, () -> store.put(npc, npc));
        assertEquals(latest, Files.readString(path)); assertEquals(changed, store.find(npc.id()).orElseThrow()); assertNotEquals(before, latest);
    }
    @Test void failedWriterLeavesSnapshotUnchanged() throws Exception {
        var store = new NpcBlueprintStore(folder.resolve("fail.yml").toFile(), NpcBlueprintTest.base(), (path, text) -> { throw new IOException("disk failure"); });
        assertThrows(IOException.class, () -> store.put(npc, null)); assertTrue(store.all().isEmpty());
    }
    @Test void publishedTypesCannotChangeButDraftCopiesCanChooseAnotherType() throws Exception {
        var store = new NpcBlueprintStore(folder.resolve("engine.yml").toFile(), NpcBlueprintTest.base()); store.put(npc, null);
        var witch = NpcStudioPolicy.details(npc, npc.name(), "", "WITCH", npc.faction(), "", 8);
        assertThrows(IllegalArgumentException.class, () -> store.put(witch, npc));
        assertDoesNotThrow(() -> store.put(NpcBlueprints.copy("witch_healer", witch), null));
    }
    @Test void malformedExternalReloadKeepsActiveSnapshotAndPreservesBadFileForRepair() throws Exception {
        Path path = folder.resolve("engine.yml"); var store = new NpcBlueprintStore(path.toFile(), NpcBlueprintTest.base()); store.put(npc, null);
        String malformed = "schema-version: 999\nnpcs: {}\n"; Files.writeString(path, malformed);
        assertThrows(IOException.class, store::reload); assertEquals(npc, store.find(npc.id()).orElseThrow()); assertEquals(malformed, Files.readString(path));
    }
    @Test void duplicateYamlKeysAliasesAndUnknownFieldsAreRejected() throws Exception {
        for (String malformed : List.of("schema-version: 1\nschema-version: 1\nnpcs: {}\n", "schema-version: 1\nnpcs: &a {}\nextra: *a\n", "schema-version: 1\nnpcs: {}\nextra: unsafe\n")) {
            Path path = folder.resolve("bad.yml"); Files.writeString(path, malformed);
            assertThrows(IOException.class, () -> new NpcBlueprintStore(path.toFile(), NpcBlueprintTest.base()));
        }
    }
    @Test void invalidGraphAndReferencesCannotBeCommitted() throws Exception {
        var store = new NpcBlueprintStore(folder.resolve("engine.yml").toFile(), NpcBlueprintTest.base());
        var disconnected = NpcStudioPolicy.node(npc, new NpcBlueprint.Node("other", List.of("Text"), List.of()), false);
        assertDoesNotThrow(() -> store.validateDraft(disconnected)); assertThrows(IllegalArgumentException.class, () -> store.put(disconnected, null));
        var wrongFaction = NpcStudioPolicy.details(npc, npc.name(), "", npc.type(), "missing", "", 8);
        assertThrows(IllegalArgumentException.class, () -> store.put(wrongFaction, null)); assertTrue(store.all().isEmpty());
    }
    @Test void externalReloadCannotChangeBodyOrRemoveEstablishedAuthoringDefinition() throws Exception {
        Path path = folder.resolve("engine.yml"); var store = new NpcBlueprintStore(path.toFile(), NpcBlueprintTest.base()); store.put(npc, null);
        Files.writeString(path, Files.readString(path).replace("type: VILLAGER", "type: WITCH"));
        assertThrows(IOException.class, store::reload); assertEquals("VILLAGER", store.find(npc.id()).orElseThrow().type());
        Files.writeString(path, "schema-version: 1\nnpcs: {}\n"); assertThrows(IOException.class, store::reload);
        assertEquals(1, store.all().size());
    }
}
