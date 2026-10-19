package net.nerdsmp.paper.engine;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ItemBlueprintStoreTest {
    @TempDir Path directory;
    private final Set<String> coreItems = Set.of("starter_sword", "crafted_iron_sword");
    private ItemBlueprintStore store(Path path) throws IOException {
        return new ItemBlueprintStore(path.toFile(), coreItems, Set.of("frontier"), material -> material != Material.WATER && material != Material.FIRE);
    }
    private ItemBlueprint sword(String id, int sockets, String set) {
        return new ItemBlueprint(id, "Sky Blade", Material.IRON_SWORD, "RARE", sockets, 10, set, List.of("Found above the clouds."), Map.of("damage", 12D));
    }
    private ItemSetBlueprint set() {
        return new ItemSetBlueprint("sky", "Sky Ward", 4, Map.of(2, new ItemSetBlueprint.Bonus("+2 Defense", Map.of("defense", 2D)),
            4, new ItemSetBlueprint.Bonus("+5% Damage", Map.of("damage-percent", 5D))));
    }
    @Test void publishedItemsAndSetsRoundTripWithoutCreatingFileOnRead() throws Exception {
        Path path = directory.resolve("content/engine-items.yml"); var store = store(path);
        assertFalse(Files.exists(path)); assertTrue(store.all().isEmpty());
        store.putSet(set(), null); store.put(sword("sky_blade", 2, "sky"), null);
        var restored = store(path); assertEquals(store.snapshot(), restored.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> restored.snapshot().items().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.all().clear());
        try (var paths = Files.list(path.getParent())) { assertEquals(1, paths.count()); }
    }
    @Test void builtInItemsSetsAndAllCraftedPrefixIdsAreReadOnly() throws Exception {
        Path path = directory.resolve("items.yml"); var store = store(path);
        for (String id : List.of("starter_sword", "crafted_iron_sword", "crafted_new_sword"))
            assertThrows(IllegalArgumentException.class, () -> store.put(sword(id, 1, null), null));
        assertThrows(IllegalArgumentException.class, () -> store.putSet(new ItemSetBlueprint("frontier", "New", 4, set().bonuses()), null));
        assertFalse(Files.exists(path)); assertTrue(store.all().isEmpty());
    }
    @Test void setReferencesRequireAnExistingCoreOrAuthoredSet() throws Exception {
        var store = store(directory.resolve("items.yml"));
        assertThrows(IllegalArgumentException.class, () -> store.put(sword("sky_blade", 1, "sky"), null));
        store.put(sword("core_set_blade", 1, "frontier"), null);
        store.putSet(set(), null); store.put(sword("sky_blade", 1, "sky"), null);
        assertEquals("sky", store.find("sky_blade").orElseThrow().setId());
    }
    @Test void concurrentEditorsCannotSilentlyOverwritePublishedDrafts() throws Exception {
        var store = store(directory.resolve("items.yml")); var first = sword("blade", 1, null); store.put(first, null);
        var newer = sword("blade", 2, null); store.put(newer, first);
        assertThrows(IllegalStateException.class, () -> store.put(sword("blade", 3, null), first));
        assertThrows(IllegalStateException.class, () -> store.put(newer, null));
        store.putSet(set(), null); assertThrows(IllegalStateException.class, () -> store.putSet(set(), null));
        assertEquals(newer, store.find("blade").orElseThrow());
    }
    @Test void publishedMaterialAndSlotCannotChangeAndSocketsCannotShrink() throws Exception {
        Path path = directory.resolve("items.yml"); var store = store(path); var first = sword("blade", 2, null); store.put(first, null);
        assertThrows(IllegalArgumentException.class, () -> store.put(sword("blade", 1, null), first));
        for (Material material : List.of(Material.DIAMOND_SWORD, Material.IRON_HELMET)) {
            var changed = new ItemBlueprint("blade", "Blade", material, "RARE", 2, 10, null, List.of(), Map.of());
            assertThrows(IllegalArgumentException.class, () -> store.put(changed, first));
        }
        assertEquals(first, store.find("blade").orElseThrow());
        store.put(sword("blade", 3, null), first); assertEquals(3, store.find("blade").orElseThrow().sockets());
    }
    @Test void failedDiskCommitNeverPublishesItemOrSetChanges() throws Exception {
        Path path = directory.resolve("items.yml"); var fail = new AtomicBoolean();
        var store = new ItemBlueprintStore(path.toFile(), coreItems, Set.of("frontier"), material -> true, (file, text) -> {
            if (fail.get()) throw new IOException("disk full"); Files.writeString(file, text);
        });
        var first = sword("blade", 1, null); store.put(first, null); String saved = Files.readString(path); fail.set(true);
        assertThrows(IOException.class, () -> store.put(sword("blade", 2, null), first));
        assertThrows(IOException.class, () -> store.putSet(set(), null));
        assertEquals(first, store.find("blade").orElseThrow()); assertTrue(store.sets().isEmpty()); assertEquals(saved, Files.readString(path));
    }
    @Test void productionItemRegistryIsValidatedOnWriteAndReload() throws Exception {
        Path path = directory.resolve("items.yml"); var store = store(path);
        var water = new ItemBlueprint("water", "Water", Material.WATER, "COMMON", 0, 1, null, List.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> store.put(water, null));
        var permissive = new ItemBlueprintStore(path.toFile(), Set.of(), Set.of(), material -> true); permissive.put(water, null);
        assertThrows(IOException.class, store::reload); assertTrue(store.all().isEmpty());
    }
    @Test void malformedReloadKeepsThePublishedSnapshotAndNeverRewritesFile() throws Exception {
        Path path = directory.resolve("items.yml"); var store = store(path); store.put(sword("blade", 1, null), null);
        String good = Files.readString(path);
        for (String bad : List.of(good.replace("schema-version: 1", "schema-version: 9"), good + "unknown: true\n",
            good.replace("sockets: 1", "sockets: 1.5"), good.replace("damage: 12.0", "damage: .nan"),
            good.replace("name: Sky Blade", "name: Sky Blade\n    name: Duplicate"))) {
            Files.writeString(path, bad); assertThrows(IOException.class, store::reload);
            assertEquals(1, store.all().size()); assertEquals(bad, Files.readString(path));
        }
        Files.writeString(path, good); store.reload(); assertEquals(1, store.all().size());
    }
    @Test void reloadCannotRemovePublishedIdsOrChangeIdentity() throws Exception {
        Path path = directory.resolve("items.yml"); var store = store(path); store.putSet(set(), null); store.put(sword("blade", 2, "sky"), null);
        String good = Files.readString(path);
        for (String changed : List.of(good.replace("IRON_SWORD", "DIAMOND_SWORD"), good.replace("sockets: 2", "sockets: 1"),
            "schema-version: 1\nitems: {}\nsets: {}\n")) {
            Files.writeString(path, changed); assertThrows(IllegalArgumentException.class, store::reload);
            assertEquals("blade", store.all().getFirst().id()); assertEquals(2, store.all().getFirst().sockets());
        }
    }
}
