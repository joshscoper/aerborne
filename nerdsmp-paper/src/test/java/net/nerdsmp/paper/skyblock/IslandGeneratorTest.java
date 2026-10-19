package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class IslandGeneratorTest {
    private record Cell(int x, int y, int z) { }
    private final Map<Cell, Integer> waterLevels = new HashMap<>();
    private Map<Cell, Material> generate(int x, int z) {
        return generate(x, z, IslandGenerator.CURRENT_TEMPLATE);
    }
    private Map<Cell, Material> generate(int x, int z, int template) {
        waterLevels.clear();
        Map<Cell, Material> blocks = new HashMap<>();
        var data = (ChunkGenerator.ChunkData) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ChunkGenerator.ChunkData.class}, (instance, method, args) -> switch (method.getName()) {
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "setBlock" -> {
                int bx = (int) args[0], by = (int) args[1], bz = (int) args[2];
                assertTrue(bx >= 0 && bx <= 15 && bz >= 0 && bz <= 15 && by >= -64 && by < 320);
                Material material = args[3] instanceof Material m ? m : ((org.bukkit.block.data.BlockData) args[3]).getMaterial();
                blocks.put(new Cell(bx, by, bz), material);
                if (material == Material.WATER) waterLevels.put(new Cell(bx, by, bz), args[3] instanceof org.bukkit.block.data.Levelled fluid ? fluid.getLevel() : 0);
                yield null;
            }
            default -> throw new AssertionError("Unexpected live terrain operation: " + method.getName());
        });
        WorldInfo info = (WorldInfo) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorldInfo.class}, (instance, method, args) -> { throw new AssertionError("Unexpected world read: " + method.getName()); });
        new IslandGenerator(template, material -> (org.bukkit.block.data.BlockData) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{material==Material.WATER ? org.bukkit.block.data.Levelled.class : org.bukkit.block.data.type.Leaves.class}, (instance, method, args) -> switch (method.getName()) {
                case "getMaterial" -> material;
                case "setDistance" -> { assertEquals(1, args[0]); yield null; }
                case "setLevel" -> { assertEquals(1, args[0]); yield null; }
                case "getLevel" -> 1;
                default -> throw new AssertionError(method.getName());
            })).generateNoise(info, new Random(1), x, z, data);
        return blocks;
    }
    @Test void everyIslandUsesTheSameLocalTemplateRegardlessOfAllocationOrder() {
        assertEquals(generate(0, 0), generate(32, 0));
        assertEquals(generate(0, 0), generate(IslandGrid.chunkX(99_999), IslandGrid.chunkZ(99_999)));
    }
    @Test void interveningChunksStayVoid() {
        assertTrue(generate(2, 0).isEmpty()); assertTrue(generate(-2, 0).isEmpty());
        assertTrue(generate(30, 0).isEmpty()); assertTrue(generate(-32, 0).isEmpty());
        assertFalse(generate(1, 0).isEmpty()); assertFalse(generate(-1, 0).isEmpty());
    }
    @Test void oldWorldsKeepTheirOriginalTemplate() {
        assertTrue(generate(1, 0, 1).isEmpty()); assertTrue(generate(-1, 0, 1).isEmpty());
        assertEquals(Material.BEDROCK, generate(0, 0, 1).get(new Cell(7, 96, 7)));
        assertDoesNotThrow(() -> new IslandGenerator(2));
        assertThrows(IllegalArgumentException.class, () -> new IslandGenerator(4));
    }
    @Test void newTemplateHasWorkstationLightingSpareSaplingAndGuardedLava() {
        var blocks = generate(0, 0);
        assertTrue(blocks.containsValue(Material.CRAFTING_TABLE)); assertTrue(blocks.containsValue(Material.FURNACE));
        assertTrue(blocks.containsValue(Material.LANTERN)); assertTrue(blocks.containsValue(Material.OAK_SAPLING));
        assertTrue(blocks.containsValue(Material.POTATOES));
        assertEquals(Material.IRON_BARS, blocks.get(new Cell(10, 97, 4)));
        assertEquals(Material.POLISHED_ANDESITE, blocks.get(new Cell(7, 96, 7)));
    }
    @Test void starterContainsProtectedLandingRenewableFoodAndGeneratorWithoutInventoryGrants() {
        var blocks = generate(0, 0);
        assertEquals(Material.BEDROCK, blocks.get(new Cell(8, 96, 8)));
        assertNull(blocks.get(new Cell(8, 97, 8))); assertNull(blocks.get(new Cell(8, 98, 8)));
        assertEquals(Material.LAVA, blocks.get(new Cell(10, 96, 4)));
        assertEquals(Material.COBBLESTONE, blocks.get(new Cell(9, 96, 4)));
        assertEquals(Material.WATER, blocks.get(new Cell(8, 96, 4)));
        assertTrue(blocks.containsValue(Material.OAK_LOG)); assertTrue(blocks.containsValue(Material.CARROTS));
        assertFalse(blocks.containsValue(Material.CHEST));
    }
    @Test void vanillaTerrainAndPopulationAreExplicitlyDisabled() {
        var generator = new IslandGenerator();
        assertFalse(generator.shouldGenerateNoise()); assertFalse(generator.shouldGenerateSurface());
        assertFalse(generator.shouldGenerateCaves()); assertFalse(generator.shouldGenerateDecorations());
        assertFalse(generator.shouldGenerateMobs()); assertFalse(generator.shouldGenerateStructures());
    }
    @Test void currentGeneratorHasOneSourceAndFlowingOutletWhileHistoricalChunksStayStable() {
        generate(0,0,3);
        assertEquals(0, waterLevels.get(new Cell(7,96,4)));
        assertEquals(1, waterLevels.get(new Cell(8,96,4)));
        generate(0,0,2);
        assertEquals(0, waterLevels.get(new Cell(8,96,4)));
        generate(0,0,1);
        assertEquals(0, waterLevels.get(new Cell(8,96,4)));
    }
}
