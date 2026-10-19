package net.nerdsmp.paper.expedition;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionGeneratorTest {
    private final Map<String,Map<Cell,Material>> chunks=new HashMap<>();
    private Map<Cell,Material> chunk(int cx,int cz) {
        return chunks.computeIfAbsent(cx+":"+cz,ignored-> {
            Map<Cell,Material> blocks=new HashMap<>();
            var data=(ChunkGenerator.ChunkData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ChunkGenerator.ChunkData.class},(proxy,method,args)-> {
                if(!method.getName().equals("setBlock")) throw new AssertionError("Unexpected world read: "+method.getName());
                int x=(int)args[0],y=(int)args[1],z=(int)args[2];
                assertTrue(x>=0 && x<16 && z>=0 && z<16 && y>=-64 && y<320);
                blocks.put(new Cell(cx*16+x,y,cz*16+z),(Material)args[3]); return null;
            });
            var info=(WorldInfo)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{WorldInfo.class},(proxy,method,args)-> { throw new AssertionError("Live terrain read"); });
            new ExpeditionGenerator().generateNoise(info,new Random(7),cx,cz,data); return blocks;
        });
    }
    private Material block(int x,int y,int z) { return chunk(x>>4,z>>4).getOrDefault(new Cell(x,y,z),Material.AIR); }
    private boolean solid(Material material) {
        return Set.of(Material.STONE,Material.STONE_BRICKS,Material.CHISELED_STONE_BRICKS,Material.SMOOTH_QUARTZ,Material.GRASS_BLOCK,Material.COARSE_DIRT,
            Material.ANDESITE,Material.TUFF,Material.DEEPSLATE,Material.DIRT,Material.SANDSTONE,
            Material.POLISHED_BLACKSTONE_BRICKS,Material.BLACKSTONE,Material.PURPUR_BLOCK,Material.END_STONE_BRICKS).contains(material);
    }
    @Test void everyCatalogNodeExistsInGeneratedTerrainAndHasSolidSupport() {
        assertEquals(196,NODES.size());
        for(Node node:NODES.values()) {
            Cell cell=node.cell(); assertEquals(node.material(),block(cell.x(),cell.y(),cell.z()));
            assertTrue(solid(block(cell.x(),SURFACE,cell.z())));
            assertSame(node.zone(),zoneAt(cell.x(),cell.z()).orElseThrow());
            assertTrue(node.cooldownSeconds()>=45);
        }
    }
    @Test void destinationLandingsHaveTwoBlocksOfHeadroomAndSolidFloors() {
        for(Zone zone:Zone.values()) {
            int z=zone.z+(zone==Zone.CITY?0:zone==Zone.HARBOR?23:19);
            assertTrue(solid(block(zone.x,SURFACE,z)),zone.id);
            assertEquals(Material.AIR,block(zone.x,SURFACE+1,z),zone.id);
            assertEquals(Material.AIR,block(zone.x,SURFACE+2,z),zone.id);
        }
    }
    @Test void harborSupportsOpenWaterFishingWithoutUncoveredEdgesOrOverheadBlocks() {
        for(int x=-17;x<=17;x++) for(int z=-182;z<=-148;z++) {
            for(int y=SURFACE-3;y<=SURFACE;y++) assertEquals(Material.WATER,block(x,y,z));
            assertTrue(solid(block(x,SURFACE-4,z)));
            assertEquals(Material.AIR,block(x,SURFACE+1,z)); assertEquals(Material.AIR,block(x,SURFACE+2,z));
        }
    }
    @Test void hubProvidesWorkstationsAndDoesNotGenerateLootContainersOrNpcs() {
        Material[] stations={Material.CRAFTING_TABLE,Material.ANVIL,Material.ENCHANTING_TABLE,Material.BREWING_STAND,Material.SMITHING_TABLE};
        for(int i=0;i<stations.length;i++) assertEquals(stations[i],block(-8+i*4,SURFACE+1,26));
        assertEquals(Material.LECTERN,block(0,SURFACE+1,8));
        assertTrue(NODES.values().stream().noneMatch(node->node.zone()==Zone.CITY));
    }
    @Test void cityHasADistinctSkylineAndWalkableProtectedBridges() {
        assertEquals(Material.GOLD_BLOCK,block(18,SURFACE+34+15+1,-39));
        assertEquals(Material.LECTERN,block(0,SURFACE+1,8));
        for(int n=53;n<=112;n++) {
            assertEquals(Material.STONE_BRICKS,block(n,SURFACE,0));
            assertEquals(Material.AIR,block(n,SURFACE+1,0));
            assertEquals(Material.AIR,block(n,SURFACE+2,0));
            assertEquals(Material.STONE_BRICK_WALL,block(n,SURFACE+1,3));
        }
        assertTrue(ExpeditionGenerator.surfaceY(Zone.QUARRY,39,-28)>SURFACE);
    }
    @Test void outsideTheFiniteLayoutStaysVoidAndVanillaGenerationIsDisabled() {
        assertTrue(chunk(100,100).isEmpty()); assertTrue(chunk(-100,-100).isEmpty());
        var generator=new ExpeditionGenerator();
        assertFalse(generator.shouldGenerateMobs()); assertFalse(generator.shouldGenerateStructures());
        assertFalse(generator.shouldGenerateDecorations()); assertFalse(generator.shouldGenerateNoise());
        assertTrue(zoneAt(80,80).isEmpty()); assertTrue(named("missing").isEmpty());
        assertNotNull(Zone.CINDER.unlock); assertNotNull(Zone.ASTRAL.unlock);
    }
}
