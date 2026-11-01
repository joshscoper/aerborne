package net.nerdsmp.paper.expedition.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMusterTest {
    @Test void aPartyThatsAllInsideStartsAtOnce() {
        assertTrue(new RoomMuster().call("shaft", true, 1_000));
    }

    @Test void stragglersGetTheWholeWindowAndNoMore() {
        RoomMuster muster = new RoomMuster();
        assertFalse(muster.call("shaft", false, 1_000));
        assertEquals("shaft", muster.room());
        assertEquals(10, muster.secondsLeft(1_000));
        assertFalse(muster.call("shaft", false, 1_000 + RoomMuster.WINDOW_MILLIS - 1));
        assertTrue(muster.call("shaft", false, 1_000 + RoomMuster.WINDOW_MILLIS));
    }

    @Test void theWaitEndsTheMomentTheLastPlayerIsIn() {
        RoomMuster muster = new RoomMuster();
        assertFalse(muster.call("shaft", false, 1_000));
        assertTrue(muster.call("shaft", true, 3_000));
    }

    @Test void clearingStartsTheNextWaitFresh() {
        RoomMuster muster = new RoomMuster();
        muster.call("shaft", false, 1_000);
        muster.clear();
        assertNull(muster.room());
        assertFalse(muster.call("shaft", false, 60_000));
    }

    @Test void anotherRoomStartsItsOwnClock() {
        RoomMuster muster = new RoomMuster();
        muster.call("shaft", false, 1_000);
        assertFalse(muster.call("yard", false, 1_000 + RoomMuster.WINDOW_MILLIS));
    }
}
