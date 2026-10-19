package net.nerdsmp.paper.engine.mob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class MobBlueprintStoreTest {
    @TempDir Path directory;
    private MobBlueprintStore store(Path path) throws IOException { return new MobBlueprintStore(path.toFile(), Set.of("veil_stalker"), id -> id.equals("crypt_drops")); }
    private MobBlueprint mob(String id) { return MobBlueprintTest.mob(id); }

    @Test void absentCatalogDoesNotWriteOrSpawnAnythingUntilExplicitPublish() throws Exception {
        Path path = directory.resolve("content/engine-mobs.yml"); var store = store(path);
        assertFalse(Files.exists(path)); assertEquals(0, store.revision()); assertEquals(List.of(), store.all());
    }
    @Test void completeStrictRoundTripRevisionAndImmutableLists() throws Exception {
        Path path = directory.resolve("content/engine-mobs.yml"); var store = store(path);
        var blueprint = mob("guardian").withLootProfile("crypt_drops").withName("Sentinel #1 : <red>");
        store.publish(blueprint, 0); store.publish(mob("brawler").withRank(MobBlueprint.Rank.NORMAL).withAbility(MobBlueprint.Ability.NONE), 1);
        var restored = store(path); assertEquals(2, restored.revision()); assertEquals(blueprint, restored.find("guardian").orElseThrow());
        assertEquals(List.of("brawler", "guardian"), restored.all().stream().map(MobBlueprint::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> restored.all().clear());
        try (var files = Files.list(path.getParent())) { assertEquals(1, files.count(), "No pending file remains after atomic commit"); }
    }
    @Test void staleEditorRevisionCannotReplaceFreshPublishedStats() throws Exception {
        var store = store(directory.resolve("engine-mobs.yml")); store.publish(mob("guardian"), 0);
        assertThrows(IllegalStateException.class, () -> store.publish(mob("guardian").withHealth(100), 0));
        assertEquals(64, store.find("guardian").orElseThrow().health()); assertEquals(1, store.revision());
    }
    @Test void failedWritesNeverPublishNewOrUpdatedDefinitionsOrAdvanceRevision() throws Exception {
        Path path = directory.resolve("engine-mobs.yml"); AtomicBoolean fail = new AtomicBoolean();
        var store = new MobBlueprintStore(path.toFile(), Set.of(), id -> true, (file, text) -> {
            if (fail.get()) throw new IOException("disk full"); Files.writeString(file, text);
        });
        store.publish(mob("guardian"), 0); String before = Files.readString(path); fail.set(true);
        assertThrows(IOException.class, () -> store.publish(mob("guardian").withHealth(80), 1));
        assertThrows(IOException.class, () -> store.publish(mob("second"), 1));
        assertEquals(mob("guardian"), store.find("guardian").orElseThrow()); assertEquals(1, store.revision());
        assertEquals(before, Files.readString(path)); assertEquals(store.all(), store(path).all());
    }
    @Test void publishedEntityTypeIsLockedButNewIdCanUseDifferentType() throws Exception {
        var store = store(directory.resolve("engine-mobs.yml")); store.publish(mob("guardian"), 0);
        assertThrows(IllegalArgumentException.class, () -> store.publish(mob("guardian").withType("skeleton"), 1));
        store.publish(mob("ranger").withType("skeleton"), 1); assertEquals(2, store.all().size());
    }
    @Test void shippedIdsAndUnknownLootProfilesCannotBePublished() throws Exception {
        var store = store(directory.resolve("engine-mobs.yml"));
        assertThrows(IllegalArgumentException.class, () -> store.publish(mob("veil_stalker"), 0));
        assertThrows(IllegalArgumentException.class, () -> store.publish(mob("guardian").withLootProfile("missing"), 0));
        assertEquals(0, store.revision()); assertTrue(store.all().isEmpty());
    }
    @Test void reloadRejectsTypeMutationAndKeepsCompleteOldSnapshot() throws Exception {
        Path path = directory.resolve("engine-mobs.yml"); var store = store(path); store.publish(mob("guardian"), 0);
        String valid = Files.readString(path); Files.writeString(path, valid.replace("type: husk", "type: skeleton"));
        assertThrows(IOException.class, store::reload); assertEquals(1, store.revision()); assertEquals(mob("guardian"), store.find("guardian").orElseThrow());
    }
    @Test void validReloadInvalidatesEditorsEvenWhenFileRevisionWasNotBumped() throws Exception {
        Path path = directory.resolve("engine-mobs.yml"); var store = store(path); store.publish(mob("guardian"), 0);
        Files.writeString(path, Files.readString(path).replace("health: 64.0", "health: 80.0"));
        assertEquals(80, store.readCandidate().getFirst().health()); assertEquals(64, store.find("guardian").orElseThrow().health());
        store.reload(); assertEquals(2, store.revision()); assertEquals(80, store.find("guardian").orElseThrow().health());
        assertThrows(IllegalStateException.class, () -> store.publish(mob("guardian"), 1));
    }
    @Test void malformedSchemaFieldsAliasesAndDuplicateKeysAreRejected() throws Exception {
        Path path = directory.resolve("engine-mobs.yml"); var store = store(path); store.publish(mob("guardian"), 0); String valid = Files.readString(path);
        for (String invalid : List.of(valid.replace("schema-version: 1", "schema-version: 2"),
            valid.replace("health: 64.0", "health: .nan"), valid.replace("level: 12", "level: 12.5"),
            valid.replace("rank: ELITE", "rank: SUPER"), valid.replace("ability: PULSE", "ability: EXECUTE_SCRIPT"),
            valid.replace("health: 64.0", "health: 64.0\n    health: 80.0"),
            valid + "unexpected: true\n", valid.replace("name: Crypt Guardian", "name: &name Crypt Guardian\n    ignored: *name"))) {
            Files.writeString(path, invalid); assertThrows(IOException.class, store::reload);
            assertEquals(List.of(mob("guardian")), store.all()); assertEquals(1, store.revision());
        }
    }
    @Test void unknownLootProfileOnDiskFailsClosedWithoutAnyRuntimeChanges() throws Exception {
        Path path = directory.resolve("engine-mobs.yml"); var store = store(path); store.publish(mob("guardian").withLootProfile("crypt_drops"), 0);
        Files.writeString(path, Files.readString(path).replace("crypt_drops", "unregistered"));
        assertThrows(IOException.class, store::reload); assertEquals("crypt_drops", store.find("guardian").orElseThrow().lootProfile());
    }
    @Test void catalogLimitCannotBeBypassedWithUpdateOrReload() throws Exception {
        var store = new MobBlueprintStore(directory.resolve("engine-mobs.yml").toFile(), Set.of(), id -> true, (file, text) -> { });
        for (int i = 0; i < 256; i++) store.publish(mob("mob_" + i), i);
        assertThrows(IllegalArgumentException.class, () -> store.publish(mob("overflow"), 256));
        store.publish(mob("mob_0").withHealth(80), 256); assertEquals(256, store.all().size()); assertEquals(257, store.revision());
    }
}
