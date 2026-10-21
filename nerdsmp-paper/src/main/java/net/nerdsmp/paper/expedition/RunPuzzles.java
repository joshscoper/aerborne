package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Puzzle;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.PuzzleType;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The live side of room puzzles: reads levers and plates, follows button sequences, counts target
 * hits, watches jump courses, and puts the blocks back when the room resets. Solving is reported to
 * the run, which asks {@link net.nerdsmp.paper.expedition.flow.RunProgress} whether the room clears.
 */
final class RunPuzzles {
    interface Host {
        World world();
        Room activeRoom();
        List<Player> online();
        void solved(String room, String puzzle);
        void teleport(Player player, Location to);
    }

    private final Plugin plugin;
    private final Host host;
    private final Map<String, Map<Point, BlockData>> snapshots = new HashMap<>();
    private final Map<String, Integer> sequenceStep = new HashMap<>();
    private final Map<String, Set<Integer>> targetHits = new HashMap<>();
    private final Map<String, Long> targetWindow = new HashMap<>();
    private final Map<java.util.UUID, Long> slipNotices = new HashMap<>();

    RunPuzzles(Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    private static String key(Room room, Puzzle puzzle) { return room.id() + "/" + puzzle.id(); }

    /** Remembers every puzzle block as the template had it, for resets. */
    void begin(List<Room> rooms) {
        for (Room room : rooms) for (Puzzle puzzle : room.puzzles().values()) {
            Map<Point, BlockData> blocks = new HashMap<>();
            for (Point point : puzzle.blocks()) blocks.put(point, block(point).getBlockData().clone());
            snapshots.put(key(room, puzzle), blocks);
        }
    }

    /** A room just started: flash any sequences it shows. */
    void started(Room room) {
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.type() == PuzzleType.SEQUENCE && puzzle.show()) flash(puzzle);
            sequenceStep.remove(key(room, puzzle));
            targetHits.remove(key(room, puzzle));
        }
    }

    /** Once a second: levers, plates, jump goals and target windows. */
    void tick(long now) {
        Room room = host.activeRoom();
        if (room == null) return;
        for (Puzzle puzzle : room.puzzles().values()) {
            switch (puzzle.type()) {
                case LEVERS -> { if (puzzle.blocks().stream().allMatch(point -> powered(block(point)))) host.solved(room.id(), puzzle.id()); }
                case PLATES -> { if (puzzle.blocks().stream().allMatch(point -> powered(block(point)))) host.solved(room.id(), puzzle.id()); }
                case REACH -> {
                    if (puzzle.area() != null && host.online().stream().anyMatch(player -> !player.isDead()
                        && puzzle.area().contains(player.getX(), player.getY(), player.getZ()))) host.solved(room.id(), puzzle.id());
                }
                case TARGETS -> {
                    Long opened = targetWindow.get(key(room, puzzle));
                    if (opened != null && puzzle.seconds() > 0 && now - opened > puzzle.seconds() * 1000L) {
                        targetWindow.remove(key(room, puzzle));
                        targetHits.remove(key(room, puzzle));
                        for (Player player : host.online()) Chat.notice(player, "Too slow. The targets reset.");
                        sound(Sound.BLOCK_NOTE_BLOCK_BASS, .6F);
                    }
                }
                default -> { }
            }
        }
    }

    /** A player used a block (button, lever, any block in a sequence). */
    void used(Player player, Block block) {
        Room room = host.activeRoom();
        if (room == null) return;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.type() != PuzzleType.SEQUENCE) continue;
            int index = indexOf(puzzle, block);
            if (index < 0) continue;
            String key = key(room, puzzle);
            List<Integer> order = puzzle.sequence();
            int step = sequenceStep.getOrDefault(key, 0);
            if (order.get(step) == index) {
                step++;
                sequenceStep.put(key, step);
                player.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1F, .7F + step * .15F);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(.5, .8, .5), 6, .2, .2, .2, 0);
                if (step >= order.size()) host.solved(room.id(), puzzle.id());
            } else {
                sequenceStep.remove(key);
                player.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1F, .5F);
                player.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(.5, .8, .5), 10, .2, .2, .2, .01);
                for (Player member : host.online()) Chat.notice(member, "Wrong order. The sequence resets" + (puzzle.show() ? "; watch it again." : "."));
                if (puzzle.show()) Bukkit.getScheduler().runTaskLater(plugin, () -> flash(puzzle), 30L);
            }
        }
    }

    /** A projectile hit a block. */
    void hit(Block block, long now) {
        Room room = host.activeRoom();
        if (room == null) return;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.type() != PuzzleType.TARGETS) continue;
            int index = indexOf(puzzle, block);
            if (index < 0) continue;
            String key = key(room, puzzle);
            targetWindow.putIfAbsent(key, now);
            Set<Integer> hits = targetHits.computeIfAbsent(key, ignored -> new HashSet<>());
            if (hits.add(index)) {
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, .8F + hits.size() * .1F);
                block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(.5, .5, .5), 8, .4, .4, .4, 0);
            }
            if (hits.size() >= puzzle.blocks().size()) host.solved(room.id(), puzzle.id());
        }
    }

    /** Players who fall off a jump course go back to its start without spending a life. */
    void moved(Player player, Location to) {
        Room room = host.activeRoom();
        if (room == null) return;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.type() != PuzzleType.REACH || puzzle.failBelowY() == null || puzzle.start() == null) continue;
            var region = room.region();
            boolean overCourse = to.getX() >= region.minX() && to.getX() < region.maxX() + 1 && to.getZ() >= region.minZ() && to.getZ() < region.maxZ() + 1;
            if (!overCourse || to.getY() >= puzzle.failBelowY()) continue;
            Point start = puzzle.start();
            host.teleport(player, new Location(host.world(), start.x() + .5, start.y(), start.z() + .5, start.yaw(), 0));
            player.setFallDistance(0);
            long now = System.currentTimeMillis();
            if (now - slipNotices.getOrDefault(player.getUniqueId(), 0L) > 4000) {
                slipNotices.put(player.getUniqueId(), now);
                Chat.notice(player, "You slipped. Back to the start of the course; no life lost.");
            }
            return;
        }
    }

    /** Puts a room's puzzle blocks back after a wipe. */
    void reset(Room room) {
        for (Puzzle puzzle : room.puzzles().values()) {
            String key = key(room, puzzle);
            sequenceStep.remove(key);
            targetHits.remove(key);
            targetWindow.remove(key);
            Map<Point, BlockData> blocks = snapshots.get(key);
            if (blocks != null) blocks.forEach((point, data) -> block(point).setBlockData(data, false));
        }
    }

    /** Puzzle blocks are part of the puzzle, not loot: true when this block belongs to one. */
    boolean puzzleBlock(Block block) {
        for (var blocks : snapshots.values()) for (Point point : blocks.keySet())
            if (point.x() == block.getX() && point.y() == block.getY() && point.z() == block.getZ()) return true;
        return false;
    }

    /** Text for a puzzle's progress, such as {@code 2/4}, for the sidebar. */
    String progress(Room room, Puzzle puzzle) {
        return switch (puzzle.type()) {
            case LEVERS, PLATES -> puzzle.blocks().stream().filter(point -> powered(block(point))).count() + "/" + puzzle.blocks().size();
            case SEQUENCE -> sequenceStep.getOrDefault(key(room, puzzle), 0) + "/" + puzzle.sequence().size();
            case TARGETS -> targetHits.getOrDefault(key(room, puzzle), Set.of()).size() + "/" + puzzle.blocks().size();
            case REACH -> "";
        };
    }

    private void flash(Puzzle puzzle) {
        List<Integer> order = puzzle.sequence();
        for (int step = 0; step < order.size(); step++) {
            Point point = puzzle.blocks().get(order.get(step));
            int note = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (host.activeRoom() == null) return;
                Location at = new Location(host.world(), point.x() + .5, point.y() + .8, point.z() + .5);
                host.world().spawnParticle(Particle.END_ROD, at, 14, .25, .25, .25, .02);
                host.world().playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2F, .7F + note * .15F);
            }, 20L + step * 20L);
        }
    }

    private int indexOf(Puzzle puzzle, Block block) {
        for (int index = 0; index < puzzle.blocks().size(); index++) {
            Point point = puzzle.blocks().get(index);
            if (point.x() == block.getX() && point.y() == block.getY() && point.z() == block.getZ()) return index;
        }
        return -1;
    }

    private Block block(Point point) { return host.world().getBlockAt(point.x(), point.y(), point.z()); }

    private static boolean powered(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Powerable powerable) return powerable.isPowered();
        if (data instanceof AnaloguePowerable analogue) return analogue.getPower() > 0;
        return block.getType() == Material.REDSTONE_BLOCK;
    }

    private void sound(Sound sound, float pitch) {
        for (Player player : host.online()) player.playSound(player.getLocation(), sound, 1F, pitch);
    }
}
