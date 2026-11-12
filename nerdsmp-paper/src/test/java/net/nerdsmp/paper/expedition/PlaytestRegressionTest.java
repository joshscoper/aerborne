package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlaytestRegressionTest {
    @Test void expiredMembersDetachWithoutMutatingAnActiveIteration() {
        UUID expired = UUID.randomUUID(), reconnecting = UUID.randomUUID();
        Map<UUID, Long> away = new HashMap<>(Map.of(expired, 0L, reconnecting, 999L));
        List<UUID> detached = new ArrayList<>();
        ExpeditionRun.expireAbsent(away, 1000, 1000, member -> {
            away.remove(member); // ExpeditionService.detach -> run.left does this too.
            detached.add(member);
        });
        assertEquals(List.of(expired), detached);
        assertEquals(Map.of(reconnecting, 999L), away);
        away.remove(reconnecting); // Rejoined just before expiry.
        ExpeditionRun.expireAbsent(away, 5000, 1000, detached::add);
        assertEquals(List.of(expired), detached);
    }

    @Test void everyOfflineMemberCanExpireAndRepeatedCleanupIsHarmless() {
        Map<UUID, Long> away = new HashMap<>(Map.of(UUID.randomUUID(), 0L, UUID.randomUUID(), 0L));
        List<UUID> detached = new ArrayList<>();
        ExpeditionRun.expireAbsent(away, 1000, 1000, member -> { away.remove(member); detached.add(member); });
        ExpeditionRun.expireAbsent(away, 2000, 1000, detached::add);
        assertEquals(2, detached.size());
        assertTrue(away.isEmpty());
    }

    @Test void protectionExemptionIsLimitedToCurrentAuthoredControlsInSameWorld() {
        Fixture f = new Fixture(PuzzleType.SEQUENCE);
        assertTrue(f.puzzles.activeControl(f.block(0, Material.LEVER)));
        assertTrue(f.puzzles.activeControl(f.block(1, Material.STONE_BUTTON)));
        assertFalse(f.puzzles.activeControl(f.block(9, Material.LEVER)));
        assertFalse(f.puzzles.activeControl(f.block(0, Material.CHEST)));
        World other = proxy(World.class, (method, args) -> null);
        Block foreign = proxy(Block.class, (method, args) -> method.equals("getWorld") ? other : null);
        assertFalse(f.puzzles.activeControl(foreign));
        f.active = null;
        assertFalse(f.puzzles.activeControl(f.block(0, Material.LEVER)));
    }

    private static final class Fixture implements RunPuzzles.Host {
        int solves, notices;
        final World world = proxy(World.class, (method, args) -> null);
        final Player player = proxy(Player.class, (method, args) -> {
            if (method.equals("getWorld")) return world;
            if (method.equals("sendMessage")) notices++;
            if (method.equals("getLocation")) return new Location(world, 0, 64, 0);
            return null;
        });
        final Puzzle puzzle;
        final Room room;
        Room active;
        final RunPuzzles puzzles;
        Fixture(PuzzleType type) {
            puzzle = new Puzzle("test", type, "Test", List.of(new Point(0, 64, 0, 0), new Point(1, 64, 0, 0)), List.of(0, 1), false, null, null, null, 5);
            room = new Room("room", "Room", Box.of(0, 64, 0, 10, 70, 10), new Point(0, 64, 0, 0), null, List.of(), Encounter.NONE, List.of(), false, Map.of("test", puzzle));
            active = room;
            puzzles = new RunPuzzles(null, this);
        }
        Block block(int x, Material type) {
            return proxy(Block.class, (method, args) -> switch (method) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> 64;
                case "getZ" -> 0;
                case "getType" -> type;
                case "getLocation" -> new Location(world, x, 64, 0);
                default -> null;
            });
        }
        public World world() { return world; }
        public Room activeRoom() { return active; }
        public List<Player> online() { return List.of(player); }
        public void solved(String room, String puzzle) { solves++; }
        public void teleport(Player player, Location to) { }
    }

    @FunctionalInterface interface Calls { Object call(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Calls calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getName().equals("equals")) return object == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(object);
            if (method.getName().equals("toString")) return type.getSimpleName();
            Object result = calls.call(method.getName(), args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == void.class) return null;
            if (method.getReturnType() == double.class) return 0D;
            return 0;
        });
    }
}
