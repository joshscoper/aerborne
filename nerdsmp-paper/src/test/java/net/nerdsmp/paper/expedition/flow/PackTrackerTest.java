package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.flow.PackTracker.State;
import net.nerdsmp.paper.expedition.flow.PackTracker.View;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackTrackerTest {
    private final PackTracker pack = new PackTracker(8, 30);

    private static View nobody() { return new View(3, -1, 0, false); }
    private static View seen(double distance) { return new View(3, distance, 5, false); }
    private static View fighting() { return new View(3, 4, 5, true); }

    @Test void aPackWaitsUntilSomeoneIsSeenInsideItsRadius() {
        assertNull(pack.tick(nobody(), 0));
        assertNull(pack.tick(seen(8.5), 0));
        assertTrue(pack.waiting());
        assertEquals(State.FIGHTING, pack.tick(seen(7.5), 0));
    }

    @Test void aHitPullsTheWholePackOnce() {
        assertTrue(pack.pull());
        assertFalse(pack.pull(), "A second hit doesn't pull it again");
        assertTrue(pack.fighting());
    }

    @Test void aPackLeashesWhenDraggedTooFarFromHome() {
        pack.pull();
        assertNull(pack.tick(new View(3, 4, 30, true), 0));
        assertEquals(State.RETURNING, pack.tick(new View(3, 4, 31, true), 0));
    }

    @Test void aPackGivesUpOnlyAfterLosingEveryoneForAWhile() {
        pack.pull();
        assertNull(pack.tick(fighting(), 0));
        assertNull(pack.tick(nobody(), 1_000));
        assertNull(pack.tick(nobody(), 1_000 + PackTracker.GIVE_UP_MILLIS - 1));
        assertEquals(State.RETURNING, pack.tick(nobody(), 1_000 + PackTracker.GIVE_UP_MILLIS));
    }

    @Test void aTargetReappearingStopsTheGiveUpClock() {
        pack.pull();
        pack.tick(nobody(), 0);
        pack.tick(fighting(), 3_000);
        assertNull(pack.tick(nobody(), 4_000));
        assertNull(pack.tick(nobody(), 6_000), "The clock restarted at 4s");
        assertTrue(pack.fighting());
    }

    @Test void aReturningPackWaitsAgainOnceHome() {
        pack.pull();
        pack.tick(new View(3, 4, 99, true), 0);
        assertNull(pack.tick(nobody(), PackTracker.RETURN_MILLIS - 1));
        assertEquals(State.WAITING, pack.tick(nobody(), PackTracker.RETURN_MILLIS));
        assertEquals(State.FIGHTING, pack.tick(seen(2), PackTracker.RETURN_MILLIS + 1));
    }

    @Test void aReturningPackCanBePulledBack() {
        pack.pull();
        pack.tick(new View(3, 4, 99, true), 0);
        assertTrue(pack.pull());
        assertTrue(pack.fighting());
    }

    @Test void deadPacksStayDead() {
        pack.pull();
        assertEquals(State.DEAD, pack.tick(new View(0, -1, -1, false), 0));
        assertFalse(pack.pull());
        assertNull(pack.tick(new View(0, 2, 2, false), 1));
    }

    @Test void patrolsWalkThereAndBack() {
        int[] at = {0, 1};
        int[] seen = new int[6];
        for (int step = 0; step < 6; step++) {
            at = PackTracker.nextStop(at[0], at[1], 3);
            seen[step] = at[0];
        }
        assertArrayEquals(new int[]{1, 2, 1, 0, 1, 2}, seen);
        assertArrayEquals(new int[]{0, 1}, PackTracker.nextStop(0, 1, 1));
    }

    @Test void membersClusterWithoutStacking() {
        assertArrayEquals(new double[]{0, 0}, PackTracker.offset(0, 1));
        double[] first = PackTracker.offset(0, 4), second = PackTracker.offset(1, 4);
        assertTrue(Math.hypot(first[0] - second[0], first[1] - second[1]) > 1);
    }

    @Test void onlyLivingPacksBlockADoor() {
        PackTracker gone = new PackTracker(8, 30);
        gone.tick(new View(0, -1, -1, false), 0);
        assertEquals(1, PackTracker.blocking(List.of(gone, pack)));
    }
}
