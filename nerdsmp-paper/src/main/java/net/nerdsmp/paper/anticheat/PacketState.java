package net.nerdsmp.paper.anticheat;

import net.nerdsmp.paper.anticheat.ml.CombatWindow;
import net.nerdsmp.paper.anticheat.physics.ClickProfile;
import net.nerdsmp.paper.anticheat.physics.TimerWindow;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-player state for packet checks. Fields without volatile/atomic are touched only on the player's Netty thread;
 * the volatile ones are published by the main thread each tick (or read by it) so neither side ever blocks.
 */
final class PacketState {
    // Published by the main thread every tick.
    volatile boolean movementExempt = true;
    volatile boolean bypass;
    volatile int ping;
    volatile int entityId = Integer.MIN_VALUE;

    // Read by the main thread.
    volatile long ticks;
    volatile int lastDigTicks = -1;
    final ConcurrentLinkedQueue<double[]> positions = new ConcurrentLinkedQueue<>(); // {x, y, z, client ticks since previous, yaw or NaN}
    final AtomicReference<CombatWindow> window = new AtomicReference<>();

    // Netty thread only.
    final long joinedNanos = System.nanoTime();
    final TimerWindow timerLong = new TimerWindow(200, 350L, 20);
    final TimerWindow timerShort = new TimerWindow(60, 750L, 10);
    final ClickProfile clicks = new ClickProfile(60);
    double timerBuffer;
    double swingBuffer;
    double clickBuffer;
    int clicksSinceEvaluation;
    long lastSwingTick = -1L;
    long lastAttackTick = -1_000L;
    long digStartTick = -1L;
    boolean digging;
    boolean attackAwaitingSwing;
    int ticksSincePosition;
    boolean hasRotation;
    float lastYaw;
    float lastPitch;
}
