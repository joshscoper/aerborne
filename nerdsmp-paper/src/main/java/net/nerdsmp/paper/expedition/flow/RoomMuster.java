package net.nerdsmp.paper.expedition.flow;

/**
 * Holds the start of a sealed boss room while the party catches up. The first player past the door calls the
 * muster; the fight begins once everyone is inside or the window runs out, whichever comes first.
 */
public final class RoomMuster {
    public static final long WINDOW_MILLIS = 10_000;
    private String room;
    private long calledAt;

    /** A room is waiting on the party. True when its fight should start now. */
    public boolean call(String roomId, boolean everyoneInside, long now) {
        if (!roomId.equals(room)) {
            room = roomId;
            calledAt = now;
        }
        return everyoneInside || now - calledAt >= WINDOW_MILLIS;
    }

    /** The room being held, or null. */
    public String room() { return room; }

    public long secondsLeft(long now) { return Math.max(1, (WINDOW_MILLIS - (now - calledAt) + 999) / 1000); }

    public void clear() { room = null; }
}
