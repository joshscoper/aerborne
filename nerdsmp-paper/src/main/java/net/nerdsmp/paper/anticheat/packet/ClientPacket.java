package net.nerdsmp.paper.anticheat.packet;

/**
 * Serverbound packets Watchdog cares about, copied off the Netty thread into plain values. Every packet carries the
 * {@link System#nanoTime()} it arrived at, which is the client's own send spacing plus network jitter, unaffected by
 * server lag because Netty runs off the main thread.
 */
public sealed interface ClientPacket {
    long nanos();

    /** A movement packet. Position fields are meaningful only when {@code hasPos}, rotation only when {@code hasRot}. */
    record Move(double x, double y, double z, float yaw, float pitch, boolean onGround,
                boolean hasPos, boolean hasRot, long nanos) implements ClientPacket { }

    record Attack(int entityId, long nanos) implements ClientPacket { }

    record Swing(long nanos) implements ClientPacket { }

    /** Sent by 1.21.2+ clients at the end of every client tick: the timer check's clock. */
    record TickEnd(long nanos) implements ClientPacket { }

    record Dig(DigAction action, long nanos) implements ClientPacket { }

    enum DigAction { START, ABORT, STOP, OTHER }
}
