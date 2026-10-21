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

    @Test void everyStandingSpotHasFloorAndHeadroom() {
        for (String id : ExpeditionService.SAMPLES) {
            ExpeditionDefinition expedition = definition(id);
            Map<Cell, Material> blocks = blocks(id);
            List<ExpeditionDefinition.Point> spots = new java.util.ArrayList<>(List.of(expedition.spawn(), expedition.exit()));
            for (var room : expedition.roomList()) {
                spots.addAll(room.points());
                if (room.checkpoint() != null) spots.add(room.checkpoint());
            }
            for (var spot : spots) {
                String where = id + " " + spot;
                assertTrue(solid(blocks, spot.x(), spot.y() - 1, spot.z()), "floor under " + where);
                assertFalse(solid(blocks, spot.x(), spot.y(), spot.z()), "feet at " + where);
                assertFalse(solid(blocks, spot.x(), spot.y() + 1, spot.z()), "head at " + where);
            }
        }
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
            // Walking south from the spawn along X=0 crosses only air and gates into the boss room (its dais starts there).
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
            assertTrue(SampleTemplates.plan(id).placements().stream().allMatch(p -> Math.abs(p.cell().x()) < 64 && p.cell().z() > -16 && p.cell().z() < 256), id);
        }
        assertNull(SampleTemplates.plan("nope"));
        assertTrue(blocks("cinder_keep").containsValue(Material.MAGMA_BLOCK));
        assertTrue(blocks("hollow_barrow").containsValue(Material.SOUL_LANTERN));
    }
}
