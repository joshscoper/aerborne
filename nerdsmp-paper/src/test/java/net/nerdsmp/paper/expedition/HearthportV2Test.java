package net.nerdsmp.paper.expedition;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.Cell;
import static org.junit.jupiter.api.Assertions.*;

class HearthportV2Test {
    private static Map<Cell, BuildCanvas.BlockSpec> build() {
        BuildCanvas canvas = new BuildCanvas();
        HearthportV2.build(canvas);
        return canvas.blocks();
    }
    private static Material material(Map<Cell, BuildCanvas.BlockSpec> blocks, int x, int y, int z) {
        var block = blocks.get(new Cell(x, y, z));
        return block == null ? Material.AIR : block.material();
    }

    @Test void planIsDeterministicBoundedAndHasExplicitArchitectureStates() {
        var plan = build();
        assertEquals(plan, build());
        assertTrue(plan.size() > 30_000, "An authored city contains more than a handful of shells");
        assertTrue(plan.keySet().stream().allMatch(c -> Math.abs(c.x()) < 92 && Math.abs(c.z()) < 92 && c.y() >= 95 && c.y() < 180));
        assertTrue(plan.values().stream().map(BuildCanvas.BlockSpec::material).distinct().count() > 65);
        assertTrue(plan.values().stream().filter(b -> !b.properties().isEmpty()).count() > 4_000);
        assertTrue(plan.values().stream().anyMatch(b -> b.properties().contains("facing=west") && b.properties().contains("half=top")));
        assertTrue(plan.values().stream().anyMatch(b -> b.properties().equals("persistent=true")));
    }

    @Test void cardinalApproachesAndStableServicePositionsRemainClear() {
        var plan = build();
        for (int n = -90; n <= 90; n++) for (int y = 97; y <= 98; y++) {
            assertEquals(Material.AIR, material(plan, n, y, 0), "east-west approach " + n + " @" + y);
            assertEquals(Material.AIR, material(plan, 0, y, n), "north-south approach " + n + " @" + y);
        }
        for (int x : new int[]{-8, -4, 0, 4, 8}) assertEquals(Material.AIR, material(plan, x, 97, 26));
        assertEquals(Material.AIR, material(plan, 0, 97, 8));
    }

    @Test void housesHaveOpenEntrancesAndCityHasNoGeneratedLootOrSpawners() {
        var plan = build();
        // Front local z points toward the avenue for both west- and east-facing shops.
        for (int y = 97; y <= 99; y++) {
            assertEquals(Material.AIR, material(plan, -32, y, -17));
            assertEquals(Material.AIR, material(plan, 32, y, -18));
            assertEquals(Material.AIR, material(plan, -34, y, 24));
            assertEquals(Material.AIR, material(plan, 34, y, 24));
        }
        Set<Material> prohibited = Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.SPAWNER, Material.TRIAL_SPAWNER, Material.VAULT, Material.COMMAND_BLOCK);
        assertTrue(plan.values().stream().noneMatch(b -> prohibited.contains(b.material())));
        assertTrue(plan.values().stream().anyMatch(b -> b.material() == Material.BOOKSHELF));
        assertTrue(plan.values().stream().anyMatch(b -> b.material() == Material.GRINDSTONE));
        assertTrue(plan.values().stream().anyMatch(b -> b.material() == Material.LECTERN));
    }
}
