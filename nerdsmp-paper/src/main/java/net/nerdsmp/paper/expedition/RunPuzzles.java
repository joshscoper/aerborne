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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Switch;
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
        /** A scene is playing, so nobody can see the room. */
        default boolean sceneRunning() { return false; }
        /** The room's enemies are done, and only its puzzles are holding the door. */
        default boolean fightOver() { return false; }
    }

    private static final long SHOW_EVERY = 12_000, NUDGE_EVERY = 20_000;

    private final Plugin plugin;
    private final Host host;
    private final Map<String, Map<Point, BlockData>> snapshots = new HashMap<>();
    private final Map<String, Integer> sequenceStep = new HashMap<>();
    private final Map<String, Set<Integer>> targetHits = new HashMap<>();
    private final Map<String, Long> targetWindow = new HashMap<>();
    private final Map<String, Long> shown = new HashMap<>();
    private final Map<String, Long> nudged = new HashMap<>();
    private final Set<String> solved = new HashSet<>();
    private final Map<java.util.UUID, Long> slipNotices = new HashMap<>();
    /** Where each puzzle's set blocks show it: the top of the pedestal the switch is fixed to. */
    private final Map<String, Set<Block>> beacons = new HashMap<>();
    private final List<Room> rooms = new java.util.ArrayList<>();

    RunPuzzles(Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    private static String key(Room room, Puzzle puzzle) { return room.id() + "/" + puzzle.id(); }

    /** Remembers every puzzle block as the template had it, for resets. */
    void begin(List<Room> rooms) {
        this.rooms.addAll(rooms);
        for (Room room : rooms) for (Puzzle puzzle : room.puzzles().values()) {
            Map<Point, BlockData> blocks = new HashMap<>();
            for (Point point : puzzle.blocks()) blocks.put(point, block(point).getBlockData().clone());
            snapshots.put(key(room, puzzle), blocks);
        }
    }

    /** A room just started. */
    void started(Room room) {
        for (Puzzle puzzle : room.puzzles().values()) {
            sequenceStep.remove(key(room, puzzle));
            targetHits.remove(key(room, puzzle));
            targetWindow.remove(key(room, puzzle));
            solved.remove(key(room, puzzle));
            shown.remove(key(room, puzzle));
            nudged.remove(key(room, puzzle));
            rewind(room, puzzle);
        }
    }

    /** A sequence keeps showing its order until someone starts it, and once the fight's over the party hears what's still holding the door. */
    private void remind(Room room, Puzzle puzzle, long now) {
        if (host.sceneRunning()) return;
        String key = key(room, puzzle);
        if (puzzle.type() == PuzzleType.SEQUENCE && puzzle.show() && sequenceStep.getOrDefault(key, 0) == 0 && now - shown.getOrDefault(key, 0L) >= SHOW_EVERY) {
            shown.put(key, now);
            flash(puzzle);
        }
        if (!host.fightOver() || now - nudged.getOrDefault(key, 0L) < NUDGE_EVERY) return;
        nudged.put(key, now);
        for (Player player : host.online()) Chat.notice(player, "The way's still shut. " + Chat.escape(puzzle.title()) + ".");
        for (Point point : puzzle.blocks()) mark(point);
    }

    private void mark(Point point) {
        Location at = new Location(host.world(), point.x() + .5, point.y() + .8, point.z() + .5);
        host.world().spawnParticle(Particle.END_ROD, at.clone().add(0, 1.6, 0), 30, .2, 1.6, .2, .01);
    }

    /** Once a second: levers, plates, jump goals and target windows. */
    void tick(long now) {
        glow();
        Room room = host.activeRoom();
        if (room == null) return;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (solved.contains(key(room, puzzle))) continue;
            remind(room, puzzle, now);
            switch (puzzle.type()) {
                case LEVERS, PLATES -> {
                    for (Point point : puzzle.blocks()) beacon(room, puzzle, block(point), powered(block(point)));
                    if (puzzle.blocks().stream().allMatch(point -> powered(block(point)))) solve(room, puzzle);
                }
                case REACH -> {
                    if (puzzle.area() != null && host.online().stream().anyMatch(player -> !player.isDead()
                        && puzzle.area().contains(player.getX(), player.getY(), player.getZ()))) solve(room, puzzle);
                }
                case TARGETS -> {
                    Long opened = targetWindow.get(key(room, puzzle));
                    if (opened != null && puzzle.seconds() > 0 && now - opened > puzzle.seconds() * 1000L) {
                        targetWindow.remove(key(room, puzzle));
                        targetHits.remove(key(room, puzzle));
                        rewind(room, puzzle);
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
        if (room == null) { settle(block); return; }
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.type() != PuzzleType.SEQUENCE) continue;
            int index = indexOf(puzzle, block);
            if (index < 0) continue;
            String key = key(room, puzzle);
            if (solved.contains(key)) { settle(block); continue; }
            List<Integer> order = puzzle.sequence();
            int step = sequenceStep.getOrDefault(key, 0);
            if (step < 0 || step >= order.size()) continue;
            if (order.get(step) == index) {
                step++;
                sequenceStep.put(key, step);
                player.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1F, .7F + step * .15F);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(.5, .8, .5), 6, .2, .2, .2, 0);
                beacon(room, puzzle, block, true);
                if (step >= order.size()) solve(room, puzzle);
            } else {
                sequenceStep.remove(key);
                rewind(room, puzzle);
                player.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1F, .5F);
                player.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(.5, .8, .5), 10, .2, .2, .2, .01);
                for (Player member : host.online()) Chat.notice(member, "Wrong order. The sequence resets" + (puzzle.show() ? "; watch it again." : "."));
                if (puzzle.show()) shown.put(key, System.currentTimeMillis() - SHOW_EVERY + 1_500);
            }
        }
    }

    /** A projectile hit a block. */
    void hit(Block struck, long now) {
        Room room = host.activeRoom();
        if (room == null) return;
        for (Puzzle puzzle : room.puzzles().values()) {
            if (puzzle.type() != PuzzleType.TARGETS) continue;
            // An arrow that lands in the fire on top of a target counts for the target.
            Block block = struck.getType() == Material.CAMPFIRE ? struck.getRelative(BlockFace.DOWN) : struck;
            int index = indexOf(puzzle, block);
            if (index < 0) continue;
            String key = key(room, puzzle);
            if (solved.contains(key)) continue;
            targetWindow.putIfAbsent(key, now);
            Set<Integer> hits = targetHits.computeIfAbsent(key, ignored -> new HashSet<>());
            if (hits.add(index)) {
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, .8F + hits.size() * .1F);
                block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(.5, .5, .5), 8, .4, .4, .4, 0);
                beacon(room, puzzle, block, true);
            }
            if (hits.size() >= puzzle.blocks().size()) solve(room, puzzle);
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
            solved.remove(key);
            rewind(room, puzzle);
        }
    }

    /** Flips the puzzle's switches back to how the template had them and puts its beacons out. */
    private void rewind(Room room, Puzzle puzzle) {
        String key = key(room, puzzle);
        Map<Point, BlockData> blocks = snapshots.get(key);
        if (blocks != null) blocks.forEach((point, data) -> block(point).setBlockData(data, false));
        Set<Block> lit = beacons.remove(key);
        if (lit != null) lit.forEach(beacon -> light(beacon, false));
    }

    /** A puzzle switch was used when it does nothing: it goes straight back to how it should sit. */
    private void settle(Block block) {
        for (Room room : rooms) for (Puzzle puzzle : room.puzzles().values()) {
            if (!(block.getBlockData() instanceof Powerable powerable) || indexOf(puzzle, block) < 0) continue;
            powerable.setPowered(solved.contains(key(room, puzzle)));
            block.setBlockData(powerable, false);
        }
    }

    private void beacon(Room room, Puzzle puzzle, Block block, boolean on) {
        Block spot = pedestalTop(block);
        Set<Block> lit = beacons.computeIfAbsent(key(room, puzzle), ignored -> new HashSet<>());
        if (on ? !lit.add(spot) : !lit.remove(spot)) return;
        light(spot, on);
        if (on) host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, spot.getLocation().add(.5, .6, .5), 16, .15, .3, .15, .03);
    }

    /** The air, or the unlit brazier, on top of whatever this switch is fixed to. */
    private static Block pedestalTop(Block block) {
        Block spot = block;
        if (block.getBlockData() instanceof Switch fixed) spot = switch (fixed.getAttachedFace()) {
            case WALL -> block.getRelative(fixed.getFacing().getOppositeFace());
            case FLOOR -> block.getRelative(BlockFace.DOWN);
            case CEILING -> block.getRelative(BlockFace.UP);
        };
        for (int step = 0; step < 6 && spot.getType().isSolid() && !(spot.getBlockData() instanceof Lightable); step++) spot = spot.getRelative(BlockFace.UP);
        return spot;
    }

    private static void light(Block beacon, boolean on) {
        if (!(beacon.getBlockData() instanceof Lightable lightable)) return;
        lightable.setLit(on);
        beacon.setBlockData(lightable, false);
    }

    /** Set switches burn on their pedestals, so the party can see where a puzzle stands. */
    private void glow() {
        for (Set<Block> lit : beacons.values()) for (Block beacon : lit) {
            Location at = beacon.getLocation().add(.5, .7, .5);
            host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, .12, .25, .12, .012);
            host.world().spawnParticle(Particle.END_ROD, at.add(0, .4, 0), 2, .1, .2, .1, .01);
        }
    }

    /** Puzzle blocks are part of the puzzle, not loot: true when this block belongs to one. */
    boolean puzzleBlock(Block block) {
        if (!host.world().equals(block.getWorld())) return false;
        for (var blocks : snapshots.values()) for (Point point : blocks.keySet())
            if (point.x() == block.getX() && point.y() == block.getY() && point.z() == block.getZ()) return true;
        return false;
    }

    /** Only authored switches and plates in the current room may bypass land protection. */
    boolean activeControl(Block block) {
        Room room = host.activeRoom();
        if (room == null || !host.world().equals(block.getWorld())) return false;
        Material type = block.getType();
        if (type != Material.LEVER && !type.name().endsWith("_BUTTON") && !type.name().endsWith("PRESSURE_PLATE")) return false;
        return room.puzzles().values().stream().anyMatch(puzzle ->
            (puzzle.type() == PuzzleType.LEVERS || puzzle.type() == PuzzleType.SEQUENCE || puzzle.type() == PuzzleType.PLATES)
                && indexOf(puzzle, block) >= 0);
    }

    private void solve(Room room, Puzzle puzzle) {
        String key = key(room, puzzle);
        if (!solved.add(key)) return;
        targetWindow.remove(key);
        host.solved(room.id(), puzzle.id());
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
        Room room = host.activeRoom();
        if (room == null || solved.contains(key(room, puzzle))) return;
        List<Integer> order = puzzle.sequence();
        for (int step = 0; step < order.size(); step++) {
            Point point = puzzle.blocks().get(order.get(step));
            int note = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (host.activeRoom() != room || solved.contains(key(room, puzzle))) return;
                Location at = new Location(host.world(), point.x() + .5, point.y() + .8, point.z() + .5);
                mark(point);
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
