package net.nerdsmp.paper.anticheat;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * The trusted path for a block that Veinseeker or Chain Harvest breaks on the server's behalf.
 *
 * <p>Like {@link net.nerdsmp.paper.gathering.TreeHarvestBridge}, the break is a real native
 * {@code Player.breakBlock}: it fires a normal {@link BlockBreakEvent}, so claim, island, zone and
 * provenance listeners, skill XP, drops and tool durability all apply, and any listener may cancel it.
 * The scope only lets Watchdog's fast-break check know that this one break, of this one block by this
 * one player, was not a client dig. It lasts for one synchronous call on the main thread, cannot be
 * nested, and cannot be created from anything a client sends.
 */
public final class ArcaneBreakScope {
    private record Scope(Player player, Block block) {
    }

    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final AtomicLong GRANTED = new AtomicLong();
    private static final AtomicLong REFUSED = new AtomicLong();

    private ArcaneBreakScope() {
    }

    /** Breaks one block inside the scope. Refused (false) when nested or inside a tree-felling break. */
    public static boolean breaking(Player player, Block block, BooleanSupplier nativeBreak) {
        if (ACTIVE.get() != null || player == null || block == null) {
            REFUSED.incrementAndGet();
            return false;
        }
        ACTIVE.set(new Scope(player, block));
        GRANTED.incrementAndGet();
        try {
            return nativeBreak.getAsBoolean();
        } finally {
            ACTIVE.remove();
        }
    }

    /** True only for the exact break the active scope was opened for. */
    public static boolean automated(BlockBreakEvent event) {
        Scope scope = ACTIVE.get();
        return scope != null && scope.player() == event.getPlayer() && scope.block().equals(event.getBlock());
    }

    /** True while any Arcane break is in progress on this thread: chain effects never start another chain. */
    public static boolean inside() {
        return ACTIVE.get() != null;
    }

    public static long granted() {
        return GRANTED.get();
    }

    public static long refused() {
        return REFUSED.get();
    }
}
