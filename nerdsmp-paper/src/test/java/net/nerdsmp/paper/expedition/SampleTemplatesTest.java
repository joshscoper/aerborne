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
        return material != Material.AIR && material != Material.CANDLE && material != Material.COBWEB && material != Material.MOSS_CARPET && material != Material.RAIL;
    }

    /** Maps laid out as one straight corridor south along X=0. */
    private static final java.util.Set<String> LINEAR = java.util.Set.of("cinder_keep", "sunken_aqueduct");

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

    private static boolean seeThrough(Material material) {
        return material == Material.AIR || material == Material.COBWEB || material == Material.HANGING_ROOTS || material == Material.IRON_CHAIN
            || material == Material.RAIL || material == Material.VINE
            || material.name().endsWith("CANDLE") || material.name().endsWith("LANTERN") || material.name().endsWith("CAMPFIRE");
    }

    /** The first solid block along a look direction, as a distance from the eye. */
    private static double view(Map<Cell, Material> blocks, double x, double y, double z, float yaw, float pitch) {
        double dx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double dy = -Math.sin(Math.toRadians(pitch));
        double dz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        for (double reach = 0; reach < 40; reach += .25) {
            Cell at = new Cell((int) Math.floor(x + dx * reach), (int) Math.floor(y + dy * reach), (int) Math.floor(z + dz * reach));
            if (!seeThrough(blocks.getOrDefault(at, Material.AIR))) return reach;
        }
        return 40;
    }

    /** Walks every camera move of a map: never inside or against anything, and always with room to see. */
    private static void camerasStayClearOfWalls(String id) {
        var expedition = definition(id);
        Map<Cell, Material> blocks = blocks(id);
        List<String> problems = new java.util.ArrayList<>();
        for (var scene : expedition.story().cutscenes().values()) {
            var shots = scene.shots();
            for (int index = 0; index < shots.size(); index++) {
                var shot = shots.get(index);
                var next = index + 1 < shots.size() ? shots.get(index + 1) : shot;
                var from = shot.frame();
                var to = shot.to() != null ? shot.to() : next.fade() ? from : next.frame();
                for (double t = 0; t <= 1; t += .1) {
                    double x = from.x() + (to.x() - from.x()) * t, y = from.y() + (to.y() - from.y()) * t, z = from.z() + (to.z() - from.z()) * t;
                    float yaw = (float) (from.yaw() + (to.yaw() - from.yaw()) * t), pitch = (float) (from.pitch() + (to.pitch() - from.pitch()) * t);
                    String where = scene.id() + " shot " + (index + 1) + " at " + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", x, y, z);
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                        Cell near = new Cell((int) Math.floor(x) + dx, (int) Math.floor(y) + dy, (int) Math.floor(z) + dz);
                        if (!seeThrough(blocks.getOrDefault(near, Material.AIR))) problems.add(where + " touches " + blocks.get(near) + " at " + near);
                    }
                    double clear = view(blocks, x, y, z, yaw, pitch);
                    if (clear < 7) problems.add(where + " only sees " + clear + " blocks");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", new java.util.LinkedHashSet<>(problems)));
    }

    @Test void theBarrowsCamerasStayClearOfWalls() { camerasStayClearOfWalls("hollow_barrow"); }

    @Test void theCanopysCamerasStayClearOfWalls() { camerasStayClearOfWalls("thornwood_canopy"); }

    @Test void firstFlightsCamerasStayClearOfWalls() { camerasStayClearOfWalls("first_flight"); }

    @Test void theMinesCamerasStayClearOfWalls() { camerasStayClearOfWalls("gloomforge_mines"); }

    /** Walks the Mines from the spawn on foot, with every gate up: drops of three at most, steps of one, no jumps across a gap. */
    @Test void theMinesCanBeWalkedFromTheTopToTheBarge() {
        var mines = definition("gloomforge_mines");
        Map<Cell, Material> blocks = new HashMap<>(blocks("gloomforge_mines"));
        blocks.values().removeIf(material -> material == Material.IRON_BARS);
        java.util.Set<Cell> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Cell> open = new java.util.ArrayDeque<>();
        open.add(new Cell(mines.spawn().x(), mines.spawn().y(), mines.spawn().z()));
        while (!open.isEmpty()) {
            Cell at = open.poll();
            if (!seen.add(at)) continue;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = -3; dy <= 1; dy++) {
                int x = at.x() + step[0], y = at.y() + dy, z = at.z() + step[1];
                if (!solid(blocks, x, y, z) && !solid(blocks, x, y + 1, z) && solid(blocks, x, y - 1, z)
                    && !blocks.getOrDefault(new Cell(x, y - 1, z), Material.AIR).name().endsWith("FENCE")) open.add(new Cell(x, y, z));
            }
        }
        var targets = new java.util.ArrayList<>(List.of(mines.exit()));
        for (var room : mines.roomList()) targets.add(room.checkpoint());
        for (var fire : mines.story().campfires().values()) targets.add(fire.at());
        for (var pack : mines.story().packs().values()) targets.add(pack.at());
        List<String> stranded = new java.util.ArrayList<>();
        for (var target : targets) {
            boolean reached = false;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                reached |= seen.contains(new Cell(target.x() + dx, target.y() + dy, target.z() + dz));
            if (!reached) stranded.add(target.x() + "," + target.y() + "," + target.z());
        }
        assertTrue(stranded.isEmpty(), "can't be reached on foot: " + stranded);
    }

    private static boolean walkThrough(Material material) {
        return seeThrough(material) || material == Material.MOSS_CARPET || material == Material.RED_MUSHROOM || material == Material.SWEET_BERRY_BUSH;
    }

    private static boolean standsOn(Map<Cell, Material> blocks, int x, int y, int z) {
        Material floor = blocks.getOrDefault(new Cell(x, y, z), Material.AIR);
        return !walkThrough(floor) && !floor.name().endsWith("FENCE");
    }

    /** Fills the canopy from the spawn on foot, up steps and across short jumps, once the span and the nest wall have gone: everything must be reachable. */
    @Test void theCanopyCanBeClimbedFromTheSpawn() {
        var canopy = definition("thornwood_canopy");
        Map<Cell, Material> blocks = new HashMap<>(blocks("thornwood_canopy"));
        for (var piece : canopy.story().setpieces().values()) {
            var box = piece.box();
            Material fill = Material.matchMaterial(piece.block());
            for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++) for (int z = box.minZ(); z <= box.maxZ(); z++) blocks.put(new Cell(x, y, z), fill);
        }
        java.util.function.BiPredicate<Cell, Integer> room = (at, up) -> walkThrough(blocks.getOrDefault(new Cell(at.x(), at.y() + up, at.z()), Material.AIR));
        java.util.function.Predicate<Cell> stand = at -> room.test(at, 0) && room.test(at, 1) && standsOn(blocks, at.x(), at.y() - 1, at.z());
        java.util.Set<Cell> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Cell> open = new java.util.ArrayDeque<>();
        open.add(new Cell(canopy.spawn().x(), canopy.spawn().y(), canopy.spawn().z()));
        while (!open.isEmpty()) {
            Cell at = open.poll();
            if (!seen.add(at)) continue;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int reach = 1; reach <= 3; reach++) {
                Cell over = new Cell(at.x() + step[0] * reach, at.y(), at.z() + step[1] * reach);
                for (int dy : reach == 1 ? new int[]{-2, -1, 0, 1} : new int[]{0, 1}) {
                    Cell landing = new Cell(over.x(), over.y() + dy, over.z());
                    if (stand.test(landing)) open.add(landing);
                }
                if (!room.test(over, 0) || !room.test(over, 1)) break;
            }
        }
        java.util.function.Consumer<ExpeditionDefinition.Point> reached = point -> {
            boolean near = false;
            for (int[] step : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) near |= seen.contains(new Cell(point.x() + step[0], point.y(), point.z() + step[1]));
            assertTrue(near, "can't reach " + point);
        };
        canopy.story().packs().values().forEach(pack -> reached.accept(pack.at()));
        canopy.story().chests().values().forEach(chest -> reached.accept(chest.at()));
        canopy.story().devices().values().forEach(device -> reached.accept(device.at()));
        canopy.story().campfires().values().forEach(fire -> reached.accept(fire.at()));
        reached.accept(canopy.exit());
        for (var room2 : canopy.roomList()) {
            room2.points().forEach(reached);
            room2.puzzles().values().forEach(puzzle -> { if (puzzle.supply() != null) reached.accept(puzzle.supply()); });
        }
    }

    /** Walks every deck: past each edge there's a rail, floor or a wall, everywhere but the windward side of the perch and the way to the hidden hollow. */
    @Test void theCanopysDecksAreRailed() {
        var canopy = definition("thornwood_canopy");
        Map<Cell, Material> blocks = new HashMap<>(blocks("thornwood_canopy"));
        for (String piece : List.of("heart_span", "span_west", "span_east")) {
            var box = canopy.story().setpieces().get(piece).box();
            for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++) for (int z = box.minZ(); z <= box.maxZ(); z++) blocks.put(new Cell(x, y, z), Material.SPRUCE_PLANKS);
        }
        java.util.function.Predicate<Cell> stand = at -> walkThrough(blocks.getOrDefault(at, Material.AIR)) && walkThrough(blocks.getOrDefault(new Cell(at.x(), at.y() + 1, at.z()), Material.AIR))
            && standsOn(blocks, at.x(), at.y() - 1, at.z());
        java.util.Set<Cell> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Cell> open = new java.util.ArrayDeque<>();
        open.add(new Cell(canopy.spawn().x(), canopy.spawn().y(), canopy.spawn().z()));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            Cell at = open.poll();
            if (!seen.add(at)) continue;
            for (int[] step : steps) for (int dy = -1; dy <= 1; dy++) {
                Cell next = new Cell(at.x() + step[0], at.y() + dy, at.z() + step[1]);
                if (stand.test(next)) open.add(next);
            }
        }
        assertTrue(seen.size() > 4000, seen.size() + " cells walked");
        List<String> gaps = new java.util.ArrayList<>();
        for (Cell at : seen) for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int x = at.x() + step[0], z = at.z() + step[1];
            boolean windward = at.y() == 71 && x >= 33 && z <= 43, hiddenWay = at.y() == 64 && x < -19 && Math.abs(z - 27) <= 2;
            if (windward || hiddenWay) continue;
            // A diagonal is only a way through if neither side of the corner is shut.
            boolean past = walkThrough(blocks.getOrDefault(new Cell(x, at.y(), z), Material.AIR));
            if (step[0] != 0 && step[1] != 0) past &= walkThrough(blocks.getOrDefault(new Cell(x, at.y(), at.z()), Material.AIR)) && walkThrough(blocks.getOrDefault(new Cell(at.x(), at.y(), z), Material.AIR));
            boolean ground = false;
            for (int down = 1; down <= 4; down++) ground |= !walkThrough(blocks.getOrDefault(new Cell(x, at.y() - down, z), Material.AIR));
            if (past && !ground) gaps.add(at.x() + "," + at.y() + "," + at.z() + " on " + blocks.get(new Cell(at.x(), at.y() - 1, at.z())) + " to " + x + "," + z);
        }
        assertTrue(gaps.isEmpty(), gaps.size() + " gaps: " + gaps.stream().sorted().limit(200).toList());
    }

    /** A fence left to itself is a thin post with gaps a player fits through, so each has to know its neighbours. */
    @Test void theCanopysRailsAreJoinedUp() {
        int joined = 0, loose = 0;
        for (var placement : SampleTemplates.plan("thornwood_canopy").placements()) {
            if (placement.block().material() != Material.SPRUCE_FENCE) continue;
            if (placement.block().properties().contains("=true")) joined++;
            else loose++;
        }
        assertTrue(joined > 500 && loose < joined / 8, joined + " joined, " + loose + " loose");
    }

    /** Sacs are wool, cisterns hold water, and the rot the Shaman's fire runs from is really in the bark. */
    @Test void theCanopysDevicesAreRealBlocks() {
        Map<Cell, Material> blocks = blocks("thornwood_canopy");
        var canopy = definition("thornwood_canopy");
        assertEquals(10, canopy.story().devices().size());
        for (var device : canopy.story().devices().values()) {
            Material block = blocks.get(new Cell(device.at().x(), device.at().y(), device.at().z()));
            assertEquals(device.id().startsWith("sac") ? Material.WHITE_WOOL : Material.WATER_CAULDRON, block, device.id());
        }
        for (var source : canopy.rooms().get("heart_grove").encounter().entrance().sources())
            assertEquals(Material.SHROOMLIGHT, blocks.get(new Cell(source.x(), source.y(), source.z())), "knot at " + source);
    }

    /** When the deck burns away, what's left must still hold the fight: the boss, his adds, the cisterns, the chests and the way out. */
    @Test void theGroveKeepsItsMiddleWhenTheDeckBurns() {
        var canopy = definition("thornwood_canopy");
        var hazard = canopy.story().hazards().get("heart_deck");
        var grove = canopy.rooms().get("heart_grove");
        List<ExpeditionDefinition.Point> safe = new java.util.ArrayList<>(grove.points());
        safe.add(grove.checkpoint());
        safe.add(canopy.exit());
        safe.add(canopy.story().rewardChests().free());
        safe.add(canopy.story().rewardChests().bonus());
        for (var section : hazard.sections()) {
            safe.add(canopy.story().devices().get(section.device()).at());
            for (var point : safe) assertFalse(section.box().contains(point.x() + .5, point.y(), point.z() + .5), point + " burns with " + section.device());
        }
    }

    /** The wind has to reach the open side, and each way it can blow has a stump to hide behind. */
    @Test void thePerchHasCoverFromTheWind() {
        Map<Cell, Material> blocks = blocks("thornwood_canopy");
        var gale = definition("thornwood_canopy").story().hazards().get("canopy_gale");
        for (var lane : gale.lanes()) {
            int dx = Integer.signum(lane.x2() - lane.x1()), dz = Integer.signum(lane.z2() - lane.z1()), cover = 0;
            for (int x = 28; x <= 54; x++) for (int z = 26; z <= 46; z++)
                if (blocks.get(new Cell(x, 72, z)) == Material.DARK_OAK_WOOD && blocks.getOrDefault(new Cell(x + dx, 72, z + dz), Material.AIR) == Material.AIR) cover++;
            assertTrue(cover >= 12, "only " + cover + " cover against " + lane);
        }
    }

    /** Webs and thorns run on open floor; the props in the nest stop the side lanes partway, so there's cover. */
    @Test void theCanopysLanesCrossOpenFloor() {
        Map<Cell, Material> blocks = blocks("thornwood_canopy");
        var hazards = definition("thornwood_canopy").story().hazards();
        for (String id : List.of("web_lines_doubled", "thorn_lanes")) for (var lane : hazards.get(id).lanes()) {
            int y = hazards.get(id).area().minY(), steps = Math.max(Math.abs(lane.x2() - lane.x1()), Math.abs(lane.z2() - lane.z1())), open = 0;
            for (int step = 0; step <= steps; step++) {
                int x = lane.x1() + Math.round((lane.x2() - lane.x1()) * step / (float) steps), z = lane.z1() + Math.round((lane.z2() - lane.z1()) * step / (float) steps);
                if (solid(blocks, x, y + 2, z)) break;
                open++;
            }
            assertTrue(open >= Math.min(12, steps + 1), id + " lane " + lane + " stops after " + open);
        }
    }

    /** The goal is forces, so the standing packs must be able to fill it, with some to spare. */
    @Test void theCanopysPacksCanFillItsForces() {
        var canopy = definition("thornwood_canopy");
        int total = 0;
        for (var pack : canopy.story().packs().values()) for (var mob : pack.mobs().entrySet()) total += mob.getValue() * canopy.story().mob(mob.getKey()).forces();
        assertTrue(total >= canopy.story().forces() * 1.1, total + " forces standing");
        assertTrue(total <= canopy.story().forces() * 1.4, total + " forces standing");
    }

    /** The Warden's reanimation draws on real soul fires. */
    @Test void theWardensFiresAreRealCampfires() {
        Map<Cell, Material> blocks = blocks("hollow_barrow");
        var entrance = definition("hollow_barrow").rooms().get("wardens_hall").encounter().entrance();
        assertEquals(ExpeditionDefinition.EntranceStyle.REANIMATE, entrance.style());
        for (var fire : entrance.sources()) assertEquals(Material.SOUL_CAMPFIRE, blocks.get(new Cell(fire.x(), fire.y(), fire.z())), "fire at " + fire);
    }

    /** The King's rite, the Widow's vigils and the fight's wards all work real blocks that can be lit and put out. */
    @Test void theBarrowsDevicesAreRealLightableBlocks() {
        Map<Cell, Material> blocks = blocks("hollow_barrow");
        var barrow = definition("hollow_barrow");
        assertEquals(ExpeditionDefinition.EntranceStyle.SUMMON, barrow.rooms().get("kings_rest").encounter().entrance().style());
        for (var fire : barrow.rooms().get("kings_rest").encounter().entrance().sources())
            assertEquals(Material.CAMPFIRE, blocks.get(new Cell(fire.x(), fire.y(), fire.z())), "fire at " + fire);
        assertEquals(10, barrow.story().devices().size());
        for (var device : barrow.story().devices().values()) {
            Material block = blocks.get(new Cell(device.at().x(), device.at().y(), device.at().z()));
            assertTrue(block == Material.CAMPFIRE || block == Material.CANDLE, device.id() + " is " + block);
        }
    }

    /** What a collapse brings down has to hang where the hazard says, or it falls from nothing. */
    @Test void theKingsChandeliersHangWhereTheHazardLooks() {
        Map<Cell, Material> blocks = blocks("hollow_barrow");
        var hazard = definition("hollow_barrow").story().hazards().get("king_chandeliers");
        assertEquals(ExpeditionDefinition.HazardType.COLLAPSE, hazard.type());
        for (var spot : hazard.spots()) {
            assertEquals(Material.POLISHED_DEEPSLATE, blocks.get(new Cell(spot.x(), spot.y(), spot.z())), "hub at " + spot);
            assertEquals(Material.IRON_CHAIN, blocks.get(new Cell(spot.x(), spot.y() + 1, spot.z())), "chain over " + spot);
        }
    }

    /** A lane has to run over open floor, and stops at the first thing tall enough to stop it. */
    @Test void theBarrowsLanesCrossOpenFloor() {
        Map<Cell, Material> blocks = blocks("hollow_barrow");
        var hazards = definition("hollow_barrow").story().hazards();
        for (String id : List.of("soul_lances", "soul_lances_doubled", "ember_channels")) for (var lane : hazards.get(id).lanes()) {
            int steps = Math.max(Math.abs(lane.x2() - lane.x1()), Math.abs(lane.z2() - lane.z1())), open = 0;
            for (int step = 0; step <= steps; step++) {
                int x = lane.x1() + (lane.x2() - lane.x1()) * step / steps, z = lane.z1() + (lane.z2() - lane.z1()) * step / steps;
                if (!blocks.containsKey(new Cell(x, 66, z)) || blocks.get(new Cell(x, 66, z)) == Material.AIR) open++;
            }
            assertEquals(steps + 1, open, id + " " + lane + " runs into a wall");
        }
    }

    /** Fills the Barrow from the spawn through everything a player can pass: nothing may lead out of the map. */
    @Test void theBarrowIsSealed() {
        var barrow = definition("hollow_barrow");
        Map<Cell, Material> blocks = blocks("hollow_barrow");
        java.util.function.Predicate<Material> open = material -> material == Material.AIR || material == Material.COBWEB
            || material == Material.HANGING_ROOTS || material == Material.LEVER || material == Material.IRON_CHAIN
            || material.name().endsWith("CANDLE") || material.name().endsWith("LANTERN") || material.name().endsWith("SKULL")
            || material.name().endsWith("CAMPFIRE") || material.name().endsWith("BUTTON");
        int minX = blocks.keySet().stream().mapToInt(Cell::x).min().orElseThrow(), maxX = blocks.keySet().stream().mapToInt(Cell::x).max().orElseThrow();
        int minZ = blocks.keySet().stream().mapToInt(Cell::z).min().orElseThrow(), maxZ = blocks.keySet().stream().mapToInt(Cell::z).max().orElseThrow();
        int maxY = blocks.keySet().stream().mapToInt(Cell::y).max().orElseThrow();
        java.util.Set<Cell> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Cell> queue = new java.util.ArrayDeque<>();
        queue.add(new Cell(barrow.spawn().x(), barrow.spawn().y(), barrow.spawn().z()));
        while (!queue.isEmpty()) {
            Cell at = queue.poll();
            if (!open.test(blocks.getOrDefault(at, Material.AIR)) || !seen.add(at)) continue;
            assertTrue(at.x() > minX && at.x() < maxX && at.z() > minZ && at.z() < maxZ && at.y() > 62 && at.y() < maxY, "The map leaks at " + at);
            for (int[] step : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}})
                queue.add(new Cell(at.x() + step[0], at.y() + step[1], at.z() + step[2]));
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
            assertTrue(SampleTemplates.plan(id).placements().stream().allMatch(p -> p.cell().x() > -128 && p.cell().x() < 320 && p.cell().z() > -128 && p.cell().z() < 256), id);
        }
        assertNull(SampleTemplates.plan("nope"));
        assertTrue(blocks("cinder_keep").containsValue(Material.MAGMA_BLOCK));
        assertTrue(blocks("hollow_barrow").containsValue(Material.SOUL_LANTERN));
    }
}
