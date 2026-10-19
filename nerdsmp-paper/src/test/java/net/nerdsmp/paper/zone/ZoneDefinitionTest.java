package net.nerdsmp.paper.zone;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static net.nerdsmp.paper.zone.ZoneDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ZoneDefinitionTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final Bounds BOUNDS = new Bounds(-10, 60, -10, 10, 80, 10);
    private ZoneDefinition zone(boolean enabled, boolean safe, List<SpawnPoint> points, List<MobEntry> mobs) {
        return new ZoneDefinition("crypts", "Old Crypts", WORLD, BOUNDS, enabled, safe, 8, 20, 48, points, mobs);
    }
    @Test void boundsUseInclusiveBlockCellsWithoutLeakingIntoAdjacentZones() {
        assertTrue(BOUNDS.contains(-10, 60, -10));
        assertTrue(BOUNDS.contains(10.999, 80.99, 10.99));
        assertFalse(BOUNDS.contains(11, 65, 0));
        assertFalse(BOUNDS.contains(0, 81, 0));
        assertFalse(BOUNDS.contains(Double.NaN, 65, 0));
        assertFalse(BOUNDS.contains(0, Double.POSITIVE_INFINITY, 0));
        assertFalse(BOUNDS.overlaps(new Bounds(11, 60, -10, 20, 80, 10)));
        assertTrue(BOUNDS.overlaps(new Bounds(10, 60, -10, 20, 80, 10)));
        assertFalse(BOUNDS.overlaps(new Bounds(-10, 81, -10, 10, 85, 10)));
    }
    @Test void extremeAndOversizedBoundsFailWithoutIntegerOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new Bounds(Integer.MIN_VALUE, 0, 0, 0, 50, 20));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, 0, 0, Integer.MAX_VALUE, 50, 20));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, -65, 0, 20, 50, 20));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, 0, 0, 20, 320, 20));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, 0, 0, 2048, 2, 20));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(0, -64, 0, 1024, 319, 1024));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(10, 0, 0, 0, 20, 20));
    }
    @Test void enabledMobZonesRequireCompleteRosterWhilePeacefulHubsDoNot() {
        assertDoesNotThrow(() -> zone(false, false, List.of(), List.of()));
        assertDoesNotThrow(() -> zone(true, true, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> zone(true, false, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> zone(true, false, List.of(new SpawnPoint(0, 65, 0, 0)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> zone(true, false, List.of(), List.of(new MobEntry("vanilla:zombie", 1, 1))));
        assertDoesNotThrow(() -> zone(true, false, List.of(new SpawnPoint(0, 65, 0, 0)), List.of(new MobEntry("vanilla:zombie", 1, 1))));
    }
    @Test void pointsAreFiniteInsideBoundsUniqueAndDefensivelyCopied() {
        List<SpawnPoint> points = new ArrayList<>(List.of(new SpawnPoint(0, 65, 0, 270)));
        List<MobEntry> mobs = new ArrayList<>(List.of(new MobEntry("custom:veil_stalker", 5, 10)));
        var zone = zone(true, false, points, mobs);
        points.clear(); mobs.clear();
        assertEquals(1, zone.points().size()); assertEquals(1, zone.mobs().size());
        assertEquals(-90, zone.points().getFirst().yaw());
        assertThrows(UnsupportedOperationException.class, () -> zone.points().clear());
        assertThrows(IllegalArgumentException.class, () -> new SpawnPoint(Double.NaN, 65, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpawnPoint(0, 65, 0, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> zone(false, false, List.of(new SpawnPoint(11, 65, 0, 0)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> zone(false, false, List.of(new SpawnPoint(0, 65, 0, 0), new SpawnPoint(-0.0, 65, -0.0, 90)), List.of()));
    }
    @Test void mobIdsCannotNameBossesCommandsOrUnreviewedEntityKinds() {
        for (String id : List.of("zombie", "vanilla:player", "vanilla:armor_stand", "vanilla:ender_dragon", "vanilla:warden", "vanilla:wither", "vanilla:ghast", "vanilla:evoker", "vanilla:cod", "custom:../spawn", "custom:boss;op"))
            assertThrows(IllegalArgumentException.class, () -> new MobEntry(id, 1, 1), id);
        assertThrows(IllegalArgumentException.class, () -> new MobEntry("vanilla:zombie", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MobEntry("vanilla:zombie", 10001, 1));
        assertThrows(IllegalArgumentException.class, () -> new MobEntry("vanilla:zombie", 1, 101));
        assertThrows(IllegalArgumentException.class, () -> zone(false, false, List.of(), List.of(new MobEntry("vanilla:zombie", 1, 1), new MobEntry("vanilla:zombie", 2, 3))));
        assertTrue(ZoneMobTypes.allowed(EntityType.ZOMBIE));
        assertFalse(ZoneMobTypes.allowed(EntityType.ENDER_DRAGON));
        assertFalse(ZoneMobTypes.allowed(EntityType.BREEZE), "Wind-charge interactions require a separate encounter guard");
        assertFalse(ZoneMobTypes.allowed(null));
        for (String id : ZoneMobTypes.vanillaIds()) assertTrue(ZoneMobTypes.vanilla(id).isPresent(), id);
    }
    @Test void textAndConfigurationLimitsFailClosed() {
        for (String id : List.of("UpperCase", "../zone", "", "a.b", "a".repeat(49)))
            assertThrows(IllegalArgumentException.class, () -> new ZoneDefinition(id, "Title", WORLD, BOUNDS, false, false, 8, 20, 48, List.of(), List.of()));
        for (String title : List.of("", " hidden", "Name\nOP", "§aName", "a".repeat(65)))
            assertThrows(IllegalArgumentException.class, () -> new ZoneDefinition("test", title, WORLD, BOUNDS, false, false, 8, 20, 48, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDefinition("test", "Title", WORLD, BOUNDS, false, false, 65, 20, 48, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDefinition("test", "Title", WORLD, BOUNDS, false, false, 8, 4, 48, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDefinition("test", "Title", WORLD, BOUNDS, false, false, 8, 20, 129, List.of(), List.of()));
    }
}
