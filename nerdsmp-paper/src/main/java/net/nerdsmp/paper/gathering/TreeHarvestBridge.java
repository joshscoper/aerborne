package net.nerdsmp.paper.gathering;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import java.util.*;
import java.util.function.*;

/** Routes proven native breaks to one tree award; it grants no protection or provenance bypass. */
public final class TreeHarvestBridge {
    public record Work(BlockBreakEvent event,long experience,String operation,Runnable bonus) { }
    private record Scope(Player player,Block block,Consumer<Work> accepted) { }
    private static final Map<BlockBreakEvent,Consumer<Work>> ROOTS=new WeakHashMap<>();
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    private TreeHarvestBridge() { }
    public static void root(BlockBreakEvent event,Consumer<Work> accepted) { ROOTS.put(event,accepted); }
    public static void forget(BlockBreakEvent event) { ROOTS.remove(event); }
    public static boolean automated(BlockBreakEvent event) {
        Scope scope=ACTIVE.get(); return scope!=null&&scope.player()==event.getPlayer()&&scope.block().equals(event.getBlock());
    }
    public static boolean capture(BlockBreakEvent event,long experience,String operation,Runnable bonus) {
        Consumer<Work> accepted=ROOTS.get(event);
        if(accepted==null&&automated(event)) accepted=ACTIVE.get().accepted();
        if(accepted==null) return false;
        accepted.accept(new Work(event,experience,operation,bonus)); return true;
    }
    static boolean breaking(Player player,Block block,Consumer<Work> accepted,BooleanSupplier nativeBreak) {
        if(ACTIVE.get()!=null) throw new IllegalStateException("Nested automatic tree harvest");
        ACTIVE.set(new Scope(player,block,accepted));
        try { return nativeBreak.getAsBoolean(); } finally { ACTIVE.remove(); }
    }
}
