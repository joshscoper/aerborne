package net.nerdsmp.paper.engine.mob;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MobSnapshotCacheTest {
    @Test void capacityIsStrictlyBoundedAndRecentlyUsedEntriesSurviveEviction() {
        var cache = new MobSnapshotCache<String>(2); UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        cache.put(first, "guardian", "HUSK", "old behavior"); cache.put(second, "ranger", "SKELETON", "ranged");
        assertEquals("old behavior", cache.find(first, "guardian", "HUSK").orElseThrow());
        cache.put(third, "mender", "WITCH", "heal");
        assertEquals(2, cache.size()); assertTrue(cache.find(second, "ranger", "SKELETON").isEmpty());
        assertEquals("old behavior", cache.find(first, "guardian", "HUSK").orElseThrow());
    }
    @Test void mismatchingIdentityOrEntityTypeCannotReuseAValidatedSnapshot() {
        var cache = new MobSnapshotCache<String>(2); UUID uuid = UUID.randomUUID();
        cache.put(uuid, "guardian", "HUSK", "pulse");
        assertTrue(cache.find(uuid, "ranger", "HUSK").isEmpty()); assertEquals(0, cache.size());
        cache.put(uuid, "guardian", "HUSK", "pulse");
        assertTrue(cache.find(uuid, "guardian", "SKELETON").isEmpty()); assertEquals(0, cache.size());
    }
    @Test void publishedCatalogObjectsDoNotMutateExistingImmutableBehaviorSnapshots() {
        var cache = new MobSnapshotCache<MobBlueprint>(2); UUID uuid = UUID.randomUUID();
        var old = MobBlueprintTest.mob("guardian"); cache.put(uuid, old.id(), "HUSK", old);
        var updatedCatalog = old.withHealth(100).withAbility(MobBlueprint.Ability.HEAL_SELF);
        assertNotEquals(old, updatedCatalog); assertEquals(old, cache.find(uuid, old.id(), "HUSK").orElseThrow());
    }
    @Test void unloadingForgettingAndShutdownRemoveCachedSnapshots() {
        var cache = new MobSnapshotCache<String>(2); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        cache.put(first, "guardian", "HUSK", "pulse"); cache.put(second, "ranger", "SKELETON", "arrow");
        cache.retain(uuid -> uuid.equals(second)); assertEquals(1, cache.size()); assertTrue(cache.find(first, "guardian", "HUSK").isEmpty());
        cache.remove(second); assertEquals(0, cache.size());
        cache.put(first, "guardian", "HUSK", "pulse"); cache.clear(); assertEquals(0, cache.size());
    }
    @Test void invalidCapacityAndNullSnapshotValuesAreRejected() {
        for (int capacity : new int[] {0, -1, 4097}) assertThrows(IllegalArgumentException.class, () -> new MobSnapshotCache<>(capacity));
        var cache = new MobSnapshotCache<String>(1);
        assertThrows(NullPointerException.class, () -> cache.put(UUID.randomUUID(), "guardian", "HUSK", null));
        assertEquals(0, cache.size());
    }
}
