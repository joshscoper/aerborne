package net.nerdsmp.paper.worldpack;

import net.nerdsmp.paper.zone.ZoneDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ZoneRetuneTest {
    static final UUID WORLD = UUID.randomUUID();
    static final ZoneDefinition.Bounds BOX = new ZoneDefinition.Bounds(0, 60, 0, 100, 120, 100);

    static ZoneDefinition zone(int cap, int respawn, int radius, List<ZoneDefinition.SpawnPoint> points) {
        return new ZoneDefinition("fields", "Fields", WORLD, BOX, true, false, cap, respawn, radius, points,
            List.of(new ZoneDefinition.MobEntry("custom:field_gnawer", 5, 1)));
    }

    @Test void untouchedDensityTakesThePackTuningAndKeepsStaffPoints() {
        var staffPoints = List.of(new ZoneDefinition.SpawnPoint(10.5, 70, 10.5, 0F), new ZoneDefinition.SpawnPoint(20.5, 70, 20.5, 0F));
        var existing = zone(10, 30, 48, staffPoints);
        var wanted = zone(18, 15, 64, List.of(new ZoneDefinition.SpawnPoint(50.5, 70, 50.5, 0F)));
        var retuned = WorldPackService.retuned(existing, wanted);
        assertNotNull(retuned);
        assertEquals(18, retuned.cap());
        assertEquals(15, retuned.respawnSeconds());
        assertEquals(64, retuned.activationRadius());
        assertEquals(staffPoints, retuned.points());
    }

    @Test void handSetDensityIsLeftAlone() {
        var points = List.of(new ZoneDefinition.SpawnPoint(10.5, 70, 10.5, 0F));
        assertNull(WorldPackService.retuned(zone(10, 20, 48, points), zone(18, 15, 64, points)));
        assertNull(WorldPackService.retuned(zone(10, 30, 32, points), zone(18, 15, 64, points)));
        // A staff cap above the pack's is kept.
        assertEquals(40, WorldPackService.retuned(zone(40, 30, 48, points), zone(18, 15, 64, points)).cap());
    }
}
