package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionLoader;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.nerdsmp.paper.expedition.ExpeditionLayout.Cell;
import static org.junit.jupiter.api.Assertions.*;

/** The shipped sample files and the code-built templates must agree, or a run spawns mobs in walls. */
class SampleTemplatesTest {
    private static ExpeditionDefinition definition(String id) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(SampleTemplatesTest.class.getResourceAsStream("/expeditions/" + id + ".yml")), StandardCharsets.UTF_8)) {
            return ExpeditionLoader.parse(id, YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Map<Cell, Material> blocks(String id) {
        Map<Cell, Material> blocks = new HashMap<>();
        for (var placement : SampleTemplates.plan(id).placements()) blocks.put(placement.cell(), placement.block().material());
        return blocks;
    }

    private static boolean solid(Map<Cell, Material> blocks, int x, int y, int z) {
        Material material = blocks.getOrDefault(new Cell(x, y, z), Material.AIR);
        return material != Material.AIR && material != Material.CANDLE && material != Material.COBWEB;
    }

    /** Maps laid out as one straight corridor south along X=0. */
    private static final java.util.Set<String> LINEAR = java.util.Set.of("first_flight", "cinder_keep", "sunken_aqueduct");

    @Test void everyStandingSpotHasFloorAndHeadroom() {
        List<String> problems = new java.util.ArrayList<>();
        for (String id : ExpeditionService.SAMPLES) {
            ExpeditionDefinition expedition = definition(id);
            Map<Cell, Material> blocks = blocks(id);
            List<ExpeditionDefinition.Point> spots = new java.util.ArrayList<>(List.of(expedition.spawn(), expedition.exit()));
            for (var pack : expedition.story().packs().values()) {
                spots.add(pack.at());
                spots.addAll(pack.patrol());
            }
            for (var room : expedition.roomList()) {
                spots.addAll(room.points());
                if (room.checkpoint() != null) spots.add(room.checkpoint());
                for (var puzzle : room.puzzles().values()) {
                    if (puzzle.start() != null) spots.add(puzzle.start());
                    if (puzzle.supply() != null) {
                        var crate = puzzle.supply();
                        if (blocks.get(new Cell(crate.x(), crate.y(), crate.z())) != Material.BARREL)
                            problems.add(id + " " + room.id() + "/" + puzzle.id() + " supply crate is " + blocks.get(new Cell(crate.x(), crate.y(), crate.z())));
                        if (!solid(blocks, crate.x(), crate.y() - 1, crate.z())) problems.add(id + " " + room.id() + "/" + puzzle.id() + " supply crate floats");
                    }
                    boolean needsItems = puzzle.type() == ExpeditionDefinition.PuzzleType.TARGETS || puzzle.type() == ExpeditionDefinition.PuzzleType.PLATES;
                    if (needsItems && puzzle.supply() == null) problems.add(id + " " + room.id() + "/" + puzzle.id() + " needs items but has no supply crate");
                    for (var block : puzzle.blocks()) {
                        String material = String.valueOf(blocks.get(new Cell(block.x(), block.y(), block.z())));
                        boolean fits = switch (puzzle.type()) {
                            case LEVERS -> material.equals("LEVER");
                            case SEQUENCE -> material.equals("LEVER") || material.endsWith("_BUTTON");
                            case PLATES -> material.endsWith("PRESSURE_PLATE");
                            case TARGETS -> material.equals("TARGET");
                            case REACH -> true;
                        };
                        if (!fits) problems.add(id + " " + room.id() + "/" + puzzle.id() + " block " + block.x() + "," + block.y() + "," + block.z() + " is " + material);
                        // Something you shoot or press needs an open side to reach it from.
                        boolean open = false;
                        for (int[] side : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}})
                            open |= room.region().contains(block.x() + side[0] + .5, block.y() + side[1], block.z() + side[2] + .5)
                                && !solid(blocks, block.x() + side[0], block.y() + side[1], block.z() + side[2]);
                        if (puzzle.type() != ExpeditionDefinition.PuzzleType.REACH && !open)
                            problems.add(id + " " + room.id() + "/" + puzzle.id() + " block " + block.x() + "," + block.y() + "," + block.z() + " is boxed in");
                    }
                }
            }
            for (var fire : expedition.story().campfires().values())
                if (blocks.get(new Cell(fire.at().x(), fire.at().y(), fire.at().z())) != Material.CAMPFIRE)
                    problems.add(id + " campfire " + fire.id() + " is " + blocks.get(new Cell(fire.at().x(), fire.at().y(), fire.at().z())));
            for (var npc : expedition.story().npcs().values()) spots.add(npc.at());
            // Loot containers stand on the floor as barrels (crates) or chests, each with room to open it.
            for (var chest : expedition.story().chests().values()) {
                var at = chest.at();
                Material expected = chest.kind() == ExpeditionDefinition.ChestKind.CRATE ? Material.BARREL : Material.CHEST;
                if (blocks.get(new Cell(at.x(), at.y(), at.z())) != expected)
                    problems.add(id + " chest " + chest.id() + " is " + blocks.get(new Cell(at.x(), at.y(), at.z())) + ", not " + expected);
                if (!solid(blocks, at.x(), at.y() - 1, at.z())) problems.add(id + " chest " + chest.id() + " floats");
                if (solid(blocks, at.x(), at.y() + 1, at.z())) problems.add(id + " chest " + chest.id() + " can't open (blocked above)");
            }
            // Cutscene cameras sit in open air, never inside a wall.
            for (var cutscene : expedition.story().cutscenes().values())
                for (var shot : cutscene.shots()) {
                    int x = (int) Math.floor(shot.x()), y = (int) Math.floor(shot.y()), z = (int) Math.floor(shot.z());
                    if (solid(blocks, x, y, z) || solid(blocks, x, y + 1, z))
                        problems.add(id + " cutscene " + cutscene.id() + " camera at " + x + "," + y + "," + z + " is inside " + blocks.get(new Cell(x, y, z)));
                }
            // Reward chests appear at the finish, so their spots are empty floor.
            var rewards = expedition.story().rewardChests();
            if (expedition.sample() != null && rewards == null) problems.add(id + " has no reward-chests");
            if (rewards != null) for (var at : java.util.Arrays.asList(rewards.free(), rewards.bonus())) {
                if (at == null) continue;
                if (solid(blocks, at.x(), at.y(), at.z()) || solid(blocks, at.x(), at.y() + 1, at.z()))
                    problems.add(id + " reward chest spot " + at.x() + "," + at.y() + "," + at.z() + " is blocked");
                if (!solid(blocks, at.x(), at.y() - 1, at.z())) problems.add(id + " reward chest spot " + at.x() + "," + at.y() + "," + at.z() + " has no floor");
            }
            for (var spot : spots) {
                String where = id + " " + spot.x() + "," + spot.y() + "," + spot.z();
                if (!solid(blocks, spot.x(), spot.y() - 1, spot.z())) problems.add("no floor under " + where);
                if (solid(blocks, spot.x(), spot.y(), spot.z())) problems.add("feet blocked at " + where + " by " + blocks.get(new Cell(spot.x(), spot.y(), spot.z())));
                if (solid(blocks, spot.x(), spot.y() + 1, spot.z())) problems.add("head blocked at " + where);
            }
        }
        assertEquals(List.of(), problems);
    }

    /** The run bars seals itself; in the template they must be open doorways it can fill. */
    @Test void sealsAreOpenDoorways() {
        for (String id : ExpeditionService.SAMPLES) {
            Map<Cell, Material> blocks = blocks(id);
            for (var room : definition(id).roomList()) {
                var seal = room.seal();
                if (seal == null) continue;
                assertTrue(seal.minX() == seal.maxX() || seal.minZ() == seal.maxZ(), id + " seal of " + room.id() + " is one block thick");
                for (int x = seal.minX(); x <= seal.maxX(); x++) for (int y = seal.minY(); y <= seal.maxY(); y++) for (int z = seal.minZ(); z <= seal.maxZ(); z++)
                    assertEquals(Material.AIR, blocks.getOrDefault(new Cell(x, y, z), Material.AIR), id + " seal of " + room.id() + " at " + x + "," + y + "," + z);
            }
        }
    }

    /** Walks the Barrow's floor from the spawn: everything a party must reach is connected, once gates and the tunnel open. */
    @Test void theBarrowsWingsAndTunnelConnect() {
        var barrow = definition("hollow_barrow");
        Map<Cell, Material> blocks = new java.util.HashMap<>(blocks("hollow_barrow"));
        var setpieces = barrow.story().setpieces().values();
        java.util.function.Predicate<int[]> walkable = at -> {
            for (int y : new int[]{64, 65}) {
                Material here = blocks.getOrDefault(new Cell(at[0], y, at[1]), Material.AIR);
                if (here == Material.IRON_BARS || !solid(blocks, at[0], y, at[1])) continue;
                boolean opens = false;
                for (var piece : setpieces) if (piece.box().contains(at[0], y, at[1])) opens = true;
                if (!opens) return false;
            }
            return true;
        };
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
        open.add(new int[]{barrow.spawn().x(), barrow.spawn().z()});
        while (!open.isEmpty()) {
            int[] at = open.poll();
            if (!seen.add(((long) at[0] << 32) | (at[1] & 0xffffffffL)) || Math.abs(at[0]) > 90 || at[1] < -5 || at[1] > 140 || !walkable.test(at)) continue;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) open.add(new int[]{at[0] + step[0], at[1] + step[1]});
        }
        for (var pack : barrow.story().packs().values())
            assertTrue(seen.contains(((long) pack.at().x() << 32) | (pack.at().z() & 0xffffffffL)), pack.id() + " can be walked to");
        for (var room : barrow.roomList()) {
            var point = room.points().isEmpty() ? room.puzzles().values().iterator().next().blocks().getFirst() : room.points().get(1 % room.points().size());
            assertTrue(room.points().isEmpty() || seen.contains(((long) point.x() << 32) | (point.z() & 0xffffffffL)), room.id() + " can be walked to");
        }
        assertTrue(seen.contains(((long) -30 << 32) | (88 & 0xffffffffL)), "The tunnel is reachable");
    }

    @Test void gatesAreBuiltAndRoomsConnect() {
        for (String id : ExpeditionService.SAMPLES) {
            ExpeditionDefinition expedition = definition(id);
            Map<Cell, Material> blocks = blocks(id);
            for (var room : expedition.roomList()) {
                if (room.gate() == null) continue;
                var gate = room.gate();
                for (int x = gate.minX(); x <= gate.maxX(); x++) for (int y = gate.minY(); y <= gate.maxY(); y++) for (int z = gate.minZ(); z <= gate.maxZ(); z++)
                    assertEquals(Material.IRON_BARS, blocks.get(new Cell(x, y, z)), id + " gate of " + room.id() + " at " + x + "," + y + "," + z);
            }
            // Linear maps: walking south from the spawn along X=0 crosses only air and gates into the boss room.
            if (!LINEAR.contains(id)) continue;
            var boss = expedition.roomList().get(expedition.rooms().size() - 1);
            for (int z = expedition.spawn().z(); z <= boss.region().minZ() + 4; z++) {
                Material feet = blocks.getOrDefault(new Cell(0, 64, z), Material.AIR);
                assertTrue(feet == Material.AIR || feet == Material.IRON_BARS, id + " corridor blocked at z=" + z + " by " + feet);
            }
        }
    }

    @Test void plansAreDeterministicAndStayInsideTheTemplate() {
        for (String id : ExpeditionService.SAMPLES) {
            assertSame(SampleTemplates.plan(id), SampleTemplates.plan(id));
            assertTrue(SampleTemplates.plan(id).size() > 5_000, id);
            assertTrue(SampleTemplates.plan(id).placements().stream().allMatch(p -> Math.abs(p.cell().x()) < 128 && p.cell().z() > -128 && p.cell().z() < 256), id);
        }
        assertNull(SampleTemplates.plan("nope"));
        assertTrue(blocks("cinder_keep").containsValue(Material.MAGMA_BLOCK));
        assertTrue(blocks("hollow_barrow").containsValue(Material.SOUL_LANTERN));
    }
}
