package net.nerdsmp.paper.digsite;

import net.nerdsmp.paper.zone.ZoneDefinition;
import net.nerdsmp.paper.zone.ZoneDefinition.Bounds;
import net.nerdsmp.paper.zone.ZoneDefinition.MobEntry;
import net.nerdsmp.paper.zone.ZoneDefinition.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DigSiteSelectionPlanTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OTHER_WORLD = UUID.randomUUID();

    private ZoneDefinition zone(String id, UUID world, Bounds bounds, boolean enabled) {
        return new ZoneDefinition(id, "An authored encounter", world, bounds, enabled, false,
            31, 45, 96, List.of(new SpawnPoint(bounds.minX() + .5, bounds.minY(), bounds.minZ() + .5, 70)),
            List.of(new MobEntry("vanilla:zombie", 7, 43)));
    }

    @Test void containedSelectionReusesTheExactParentWithoutChangingItsEncounterSettings() {
        Bounds parentBounds = new Bounds(-20, 40, -20, 20, 100, 20);
        ZoneDefinition parent = zone("ruins", WORLD, parentBounds, true);
        var planned = DigSiteSelectionPlan.plan(List.of(parent), "training_pit", WORLD,
            new Bounds(-2, 60, -2, 2, 62, 2));
        assertSame(parent, planned.zone());
        assertFalse(planned.created());
        assertSame(parentBounds, planned.zone().bounds());
        assertFalse(planned.zone().safe());
        assertEquals(31, planned.zone().cap());
        assertEquals(45, planned.zone().respawnSeconds());
        assertEquals(96, planned.zone().activationRadius());
        assertEquals(parent.points(), planned.zone().points());
        assertEquals(parent.mobs(), planned.zone().mobs());
    }

    @Test void disabledParentCannotBeSilentlyEnabledByCreatingADigSite() {
        ZoneDefinition parent = zone("ruins", WORLD, new Bounds(0, 60, 0, 10, 70, 10), false);
        var error = assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(
            List.of(parent), "pit", WORLD, new Bounds(1, 61, 1, 2, 62, 2)));
        assertTrue(error.getMessage().contains("disabled"));
        assertFalse(parent.enabled());
    }

    @Test void partialIntersectionOrSpanningMultipleZonesFailsWithoutChangingAnyBounds() {
        ZoneDefinition first = zone("first", WORLD, new Bounds(0, 60, 0, 5, 70, 5), true);
        ZoneDefinition second = zone("second", WORLD, new Bounds(10, 60, 0, 15, 70, 5), true);
        for (Bounds crossing : List.of(new Bounds(-1, 60, 0, 1, 61, 1),
            new Bounds(4, 60, 0, 6, 61, 1), new Bounds(0, 59, 0, 1, 61, 1),
            new Bounds(-1, 59, -1, 6, 71, 6))) {
            assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(List.of(first), "pit", WORLD, crossing));
        }
        var error = assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(
            List.of(first, second), "pit", WORLD, new Bounds(2, 60, 0, 12, 61, 1)));
        assertTrue(error.getMessage().contains("multiple zone boundaries"));
        assertEquals(new Bounds(0, 60, 0, 5, 70, 5), first.bounds());
    }

    @Test void disjointSelectionCreatesAnEnabledPeacefulZoneWithNoMobsOrSpawnPoints() {
        Bounds selected = new Bounds(-6, -20, -6, -1, -18, -1);
        ZoneDefinition foreign = zone("foreign", OTHER_WORLD, selected, true);
        var planned = DigSiteSelectionPlan.plan(List.of(foreign), "training_pit", WORLD, selected);
        assertTrue(planned.created());
        ZoneDefinition created = planned.zone();
        assertEquals("training_pit", created.id());
        assertEquals("training pit", created.title());
        assertEquals(WORLD, created.worldId());
        assertSame(selected, created.bounds());
        assertTrue(created.enabled());
        assertTrue(created.safe());
        assertEquals(8, created.cap());
        assertEquals(20, created.respawnSeconds());
        assertEquals(48, created.activationRadius());
        assertTrue(created.points().isEmpty());
        assertTrue(created.mobs().isEmpty());
    }

    @Test void soilOnlyCuboidNeverExpandsIntoUnselectedHeadroom() {
        Bounds soil = new Bounds(-5, 63, -5, 5, 63, 5);
        var newlyCreated = DigSiteSelectionPlan.plan(List.of(), "pit", WORLD, soil);
        assertSame(soil, newlyCreated.zone().bounds());
        assertEquals(121, DigSiteSelectionPlan.volume(newlyCreated.zone().bounds()));
        assertFalse(newlyCreated.zone().bounds().contains(0.5, 64, 0.5));

        Bounds encounterBounds = new Bounds(-10, 50, -10, 10, 80, 10);
        ZoneDefinition existing = zone("ruins", WORLD, encounterBounds, true);
        var contained = DigSiteSelectionPlan.plan(List.of(existing), "pit", WORLD, soil);
        assertSame(existing, contained.zone());
        assertSame(encounterBounds, contained.zone().bounds());
        assertEquals(63, soil.maxY());
    }

    @Test void duplicateZoneIdInAnyWorldCannotBeOverwrittenForANewArea() {
        Bounds elsewhere = new Bounds(100, 60, 100, 101, 61, 101);
        Bounds selected = new Bounds(0, 60, 0, 1, 61, 1);
        for (UUID world : List.of(WORLD, OTHER_WORLD)) {
            ZoneDefinition existing = zone("pit", world, elsewhere, true);
            var error = assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(
                List.of(existing), "pit", WORLD, selected));
            assertTrue(error.getMessage().contains("already exists elsewhere"));
            assertSame(elsewhere, existing.bounds());
        }
    }

    @Test void inclusiveEdgesNegativeCoordinatesAndExtremeHeightsAreResolvedExactly() {
        for (Bounds bounds : List.of(new Bounds(-5, -64, -5, -1, -60, -1),
            new Bounds(0, 315, 0, 4, 319, 4))) {
            ZoneDefinition parent = zone("edge", WORLD, bounds, true);
            assertSame(parent, DigSiteSelectionPlan.plan(List.of(parent), "pit", WORLD, bounds).zone());
            Bounds lastCell = new Bounds(bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
            assertSame(parent, DigSiteSelectionPlan.plan(List.of(parent), "pit", WORLD, lastCell).zone());
            Bounds adjacent = new Bounds(bounds.maxX() + 1, bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1, bounds.minY(), bounds.minZ());
            assertTrue(DigSiteSelectionPlan.plan(List.of(parent), "adjacent", WORLD, adjacent).created());
        }
    }

    @Test void captureVolumeIsInclusiveAndBoundedBeforeAPlanCanBeReturned() {
        Bounds maximum = new Bounds(-16, 60, -16, 15, 91, 15);
        assertEquals(32_768, DigSiteSelectionPlan.volume(maximum));
        assertTrue(DigSiteSelectionPlan.plan(List.of(), "pit", WORLD, maximum).created());
        assertEquals(1, DigSiteSelectionPlan.volume(new Bounds(0, 60, 0, 0, 60, 0)));
        Bounds tooLarge = new Bounds(-16, 60, -16, 15, 92, 15);
        var error = assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(List.of(), "pit", WORLD, tooLarge));
        assertTrue(error.getMessage().contains("32,768"));
    }

    @Test void atZoneLimitExistingParentRemainsUsableButANewZoneIsRejected() {
        List<ZoneDefinition> zones = new ArrayList<>();
        for (int index = 0; index < ZoneDefinition.MAX_ZONES; index++) {
            zones.add(zone("zone_" + index, WORLD, new Bounds(index * 3, 60, 0, index * 3 + 1, 61, 1), true));
        }
        ZoneDefinition first = zones.getFirst();
        assertSame(first, DigSiteSelectionPlan.plan(zones, "pit", WORLD, first.bounds()).zone());
        assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(zones, "pit", WORLD,
            new Bounds(-5, 60, 0, -4, 61, 1)));
        zones.add(zone("overflow", OTHER_WORLD, new Bounds(0, 60, 0, 1, 61, 1), true));
        assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(zones, "pit", WORLD, first.bounds()));
    }

    @Test void requiredInputsAndUnsafeIdsFailClosed() {
        Bounds selected = new Bounds(0, 60, 0, 1, 61, 1);
        for (String id : List.of("", "Upper", "a.b", "../pit", "a".repeat(49))) {
            assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(List.of(), id, WORLD, selected));
        }
        assertThrows(IllegalArgumentException.class, () -> DigSiteSelectionPlan.plan(List.of(), null, WORLD, selected));
        assertThrows(NullPointerException.class, () -> DigSiteSelectionPlan.plan(null, "pit", WORLD, selected));
        assertThrows(NullPointerException.class, () -> DigSiteSelectionPlan.plan(List.of(), "pit", null, selected));
        assertThrows(NullPointerException.class, () -> DigSiteSelectionPlan.plan(List.of(), "pit", WORLD, null));
        assertThrows(NullPointerException.class, () -> DigSiteSelectionPlan.volume(null));
        assertThrows(NullPointerException.class, () -> new DigSiteSelectionPlan.Plan(null, true));
    }
}
