package net.nerdsmp.paper.digsite;

import org.junit.jupiter.api.Test;
import static net.nerdsmp.paper.digsite.DigSitePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class DigSitePolicyTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test void onlyExactSnapshotsWithNoMarkerAreReady() {
        Marker absent = marker(false, false, null, NOW, 3600);
        assertEquals(Decision.READY, decide(true, false, absent, BlockState.EXPECTED, false, NOW));
        assertEquals(Decision.BLOCKED, decide(true, false, absent, BlockState.AIR, false, NOW));
        assertEquals(Decision.BLOCKED, decide(true, false, absent, BlockState.FOREIGN, false, NOW));
    }

    @Test void wrongTypedNullNegativeAndFarFutureCooldownsFailClosed() {
        for (Marker corrupt : new Marker[]{
            marker(true, false, NOW + 1000, NOW, 3600), marker(true, true, null, NOW, 3600),
            marker(true, true, 0L, NOW, 3600), marker(true, true, -1L, NOW, 3600),
            marker(true, true, Long.MAX_VALUE, NOW, 3600)}) {
            assertEquals(MarkerState.CORRUPT, corrupt.state());
            assertEquals(Decision.BLOCKED, decide(true, false, corrupt, BlockState.EXPECTED, false, NOW));
        }
    }

    @Test void cooldownSurvivesUntilDueEvenIfBlockIsStillPresent() {
        Marker waiting = marker(true, true, NOW + 20_000, NOW, 3600);
        assertEquals(Decision.WAITING, decide(true, false, waiting, BlockState.AIR, false, NOW));
        assertEquals(Decision.WAITING, decide(true, false, waiting, BlockState.EXPECTED, false, NOW));
        assertEquals(Decision.RESTORE, decide(true, false, waiting, BlockState.AIR, false, NOW + 20_000));
        assertEquals(Decision.RELEASE_RETAINED, decide(true, false, waiting, BlockState.EXPECTED, false, NOW + 20_000));
    }

    @Test void restorationNeverOverwritesForeignBlocksOrLivingOccupants() {
        Marker elapsed = marker(true, true, NOW - 1, NOW, 3600);
        assertEquals(Decision.BLOCKED, decide(true, false, elapsed, BlockState.FOREIGN, false, NOW));
        assertEquals(Decision.OCCUPIED, decide(true, false, elapsed, BlockState.AIR, true, NOW));
        assertEquals(Decision.RESTORE, decide(true, false, elapsed, BlockState.AIR, false, NOW));
    }

    @Test void disabledZonesAndQuarantinedCellsCannotBeRestored() {
        Marker elapsed = marker(true, true, NOW - 1, NOW, 3600);
        assertEquals(Decision.INACTIVE, decide(false, false, elapsed, BlockState.AIR, false, NOW));
        assertEquals(Decision.BLOCKED, decide(true, true, elapsed, BlockState.AIR, false, NOW));
    }

    @Test void cooldownWritesAreExactAndRejectClockOverflow() {
        assertEquals(NOW + 20_000, due(NOW, 20));
        assertThrows(IllegalArgumentException.class, () -> due(0, 20));
        assertThrows(IllegalArgumentException.class, () -> due(NOW, 0));
        assertThrows(ArithmeticException.class, () -> due(Long.MAX_VALUE - 10, 20));
    }

    @Test void recapturesCannotInheritAnotherGenerationsMarker() {
        var first = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var second = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertNotEquals(markerKey(first, -1, -64, -1), markerKey(second, -1, -64, -1));
        assertEquals("digsite_00000000000000000000000000000001_15_0_15", markerKey(first, -1, -64, -1));
        assertNotEquals(markerKey(first, 0, 0, 0), markerKey(first, 1, 0, 0));
        assertNotEquals(markerKey(first, 0, 0, 0), markerKey(first, 0, 1, 0));
        assertNotEquals(markerKey(first, 0, 0, 0), markerKey(first, 0, 0, 1));
    }
}
