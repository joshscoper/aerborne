package net.nerdsmp.paper.loot.profile;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class LootProfileStoreTest {
    @TempDir Path directory;
    private static final ItemId SWORD = new ItemId("sword"), TOKEN = new ItemId("token");
    private static final Set<ItemId> KNOWN = Set.of(SWORD, TOKEN);
    private LootProfileDefinition profile(String id) {
        return new LootProfileDefinition(id, Mode.REPLACE, List.of(new Entry("gear", Kind.ITEM, "sword", 0.2, 1, 1, true, 3),
            new Entry("purse", Kind.MARKS, "marks", 1, 3, 8, false, 0)));
    }
    private LootProfileStore store(Path path) throws IOException { return new LootProfileStore(path.toFile(), KNOWN, Set.of(SWORD)); }

    @Test void roundTripKeepsProfilesAssignmentsAndImmutableSnapshots() throws Exception {
        Path path = directory.resolve("nested/mob-loot.yml");
        var store = store(path); assertFalse(Files.exists(path)); assertTrue(store.all().isEmpty());
        store.put(profile("crypt")); store.assignZone("crypts", "crypt"); store.assignMob("custom:veil_stalker", "crypt");
        store.assignMob("vanilla:zombie", "crypt");
        var restored = store(path);
        assertEquals(store.all(), restored.all()); assertEquals(store.zoneAssignments(), restored.zoneAssignments());
        assertEquals("crypt", restored.mobAssignments().get("veil_stalker"));
        assertEquals("crypt", restored.mobAssignments().get("vanilla:zombie"));
        assertThrows(UnsupportedOperationException.class, () -> restored.zoneAssignments().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.knownItems().clear());
        try (var files = Files.list(path.getParent())) { assertEquals(1, files.count()); }
    }
    @Test void zoneTableWinsOverMobFallbackWithoutReturningBothTables() throws Exception {
        var store = store(directory.resolve("mob-loot.yml"));
        store.put(profile("zone_table")); store.put(profile("mob_table"));
        store.assignZone("crypts", "zone_table"); store.assignMob("veil_stalker", "mob_table");
        assertEquals("zone_table", store.resolve("crypts", "custom:veil_stalker").orElseThrow().id());
        assertEquals("mob_table", store.resolve("other", "veil_stalker").orElseThrow().id());
        assertTrue(store.resolve(null, null).isEmpty()); assertTrue(store.resolve("other", "other").isEmpty());
        assertTrue(store.resolve(null, "vanilla:armor_stand").isEmpty());
        assertTrue(store.resolve(null, "vanilla:player").isEmpty());
        assertTrue(store.resolve(null, "unrelated:object").isEmpty());
        store.unassignZone("crypts"); assertEquals("mob_table", store.resolve("crypts", "veil_stalker").orElseThrow().id());
    }
    @Test void failedMutationsNeverPublishOrErasePriorTablesAndAssignments() throws Exception {
        Path path = directory.resolve("mob-loot.yml"); AtomicBoolean fail = new AtomicBoolean();
        var store = new LootProfileStore(path.toFile(), KNOWN, Set.of(SWORD), (file, text) -> {
            if (fail.get()) throw new IOException("disk full"); Files.writeString(file, text);
        });
        store.put(profile("crypt")); store.assignZone("crypts", "crypt"); store.assignMob("veil_stalker", "crypt");
        String saved = Files.readString(path); fail.set(true);
        assertThrows(IOException.class, () -> store.put(profile("new_table")));
        assertThrows(IOException.class, () -> store.put(new LootProfileDefinition("crypt", Mode.ADDITIVE, List.of())));
        assertThrows(IOException.class, () -> store.assignZone("new_zone", "crypt"));
        assertThrows(IOException.class, () -> store.assignMob("new_mob", "crypt"));
        assertThrows(IOException.class, () -> store.unassignZone("crypts"));
        assertThrows(IOException.class, () -> store.unassignMob("veil_stalker"));
        assertEquals(List.of(profile("crypt")), store.all()); assertEquals(saved, Files.readString(path));
        assertEquals("crypt", store.resolve("crypts", "veil_stalker").orElseThrow().id());
    }
    @Test void assignedProfilesCannotBeDeletedAndUnassignedRemovalPersists() throws Exception {
        Path path = directory.resolve("mob-loot.yml"); var store = store(path); store.put(profile("crypt"));
        store.assignMob("veil_stalker", "crypt");
        assertThrows(IllegalArgumentException.class, () -> store.remove("crypt"));
        store.unassignMob("veil_stalker"); store.remove("crypt");
        assertTrue(store(path).all().isEmpty()); assertThrows(IllegalArgumentException.class, () -> store.remove("missing"));
    }
    @Test void itemCatalogAndGearOnlyAffixesAreValidatedBeforeWriting() throws Exception {
        Path path = directory.resolve("mob-loot.yml"); var store = store(path);
        assertThrows(IllegalArgumentException.class, () -> store.put(new LootProfileDefinition("unknown", Mode.ADDITIVE,
            List.of(new Entry("item", Kind.ITEM, "not_known", 1, 1, 1, false, 0)))));
        for (var entry : List.of(new Entry("item", Kind.ITEM, "token", 1, 1, 1, true, 0),
            new Entry("item", Kind.ITEM, "token", 1, 1, 1, false, 2)))
            assertThrows(IllegalArgumentException.class, () -> store.put(new LootProfileDefinition("material", Mode.ADDITIVE, List.of(entry))));
        assertFalse(Files.exists(path)); assertTrue(store.all().isEmpty());
        store.put(new LootProfileDefinition("material", Mode.ADDITIVE, List.of(new Entry("item", Kind.ITEM, "token", 1, 1, 64, false, 0))));
    }
    @Test void runtimeMaterialCatalogRejectsUnobtainableMaterialsOnWriteAndReload() throws Exception {
        Path path = directory.resolve("mob-loot.yml");
        var store = new LootProfileStore(path.toFile(), KNOWN, Set.of(SWORD), material -> material == Material.IRON_INGOT);
        var valid = new LootProfileDefinition("supplies", Mode.ADDITIVE,
            List.of(new Entry("iron", Kind.MATERIAL, "IRON_INGOT", 1, 1, 4, false, 0)));
        store.put(valid); String saved = Files.readString(path);
        assertThrows(IllegalArgumentException.class, () -> store.put(new LootProfileDefinition("water", Mode.REPLACE,
            List.of(new Entry("water", Kind.MATERIAL, "WATER", 1, 1, 1, false, 0)))));
        assertEquals(List.of(valid), store.all()); assertEquals(saved, Files.readString(path));
        String invalid = saved.replace("source: IRON_INGOT", "source: WATER"); Files.writeString(path, invalid);
        assertThrows(IOException.class, store::reload); assertEquals(List.of(valid), store.all());
        assertEquals(invalid, Files.readString(path));
    }
    @Test void badAssignmentTargetsAndMissingProfilesAreRejected() throws Exception {
        var store = store(directory.resolve("mob-loot.yml")); store.put(profile("crypt"));
        assertThrows(IllegalArgumentException.class, () -> store.assignZone("../path", "crypt"));
        assertThrows(IllegalArgumentException.class, () -> store.assignZone("crypts", "missing"));
        for (String mob : List.of("vanilla:player", "vanilla:armor_stand", "vanilla:item", "vanilla:unknown", "custom:../path"))
            assertThrows(IllegalArgumentException.class, () -> store.assignMob(mob, "crypt"));
        assertTrue(store.mobAssignments().isEmpty());
    }
    @Test void malformedYamlNeverSilentlyResetsAndFailedReloadKeepsPublishedSnapshot() throws Exception {
        Path path = directory.resolve("mob-loot.yml"); var store = store(path); store.put(profile("crypt")); store.assignZone("crypts", "crypt");
        String saved = Files.readString(path);
        for (String invalid : List.of("", "schema-version: [invalid", saved.replace("schema-version: 1", "schema-version: 99"),
            saved.replace("mode: REPLACE", "mode: REPLACE\n    unknown: true"), saved.replace("chance: 0.2", "chance: .NaN"),
            saved.replace("minimum: 1", "minimum: 1.5"), saved.replace("minimum: 1", "minimum: 2147483648"),
            saved.replace("unidentified: true", "unidentified: 'true'"), saved.replace("source: sword", "source: missing_item"),
            saved.replace("mode: REPLACE", "mode: REPLACE\n    mode: ADDITIVE"), saved.replace("crypts: crypt", "crypts: missing_table"))) {
            Files.writeString(path, invalid);
            assertThrows(IOException.class, () -> store(path), invalid);
            assertThrows(IOException.class, store::reload, invalid);
            assertEquals(List.of(profile("crypt")), store.all()); assertEquals(invalid, Files.readString(path));
        }
        Files.writeString(path, saved); store.reload(); assertEquals(List.of(profile("crypt")), store.all());
    }
    @Test void profileAndAssignmentCapsAreCheckedBeforeSnapshotPublication() throws Exception {
        var store = new LootProfileStore(directory.resolve("mob-loot.yml").toFile(), KNOWN, Set.of(SWORD), (path, text) -> { });
        for (int i = 0; i < MAX_PROFILES; i++) store.put(new LootProfileDefinition("profile_" + i, Mode.ADDITIVE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> store.put(profile("overflow")));
        assertEquals(MAX_PROFILES, store.all().size());
        for (int i = 0; i < 128; i++) store.assignZone("zone_" + i, "profile_0");
        assertThrows(IllegalArgumentException.class, () -> store.assignZone("overflow", "profile_0"));
        assertEquals(128, store.zoneAssignments().size());
    }
}
