package net.nerdsmp.paper.expedition;

import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static net.nerdsmp.paper.expedition.ExpeditionLayout.Cell;
import static org.junit.jupiter.api.Assertions.*;

class WindrootV2Test {
    private static final Map<Cell,BuildCanvas.BlockSpec> PLAN = plan();

    private static Map<Cell,BuildCanvas.BlockSpec> plan() {
        BuildCanvas canvas=new BuildCanvas();
        WindrootV2.build(canvas);
        return canvas.blocks();
    }

    private static BuildCanvas.BlockSpec at(int x,int y,int z) {
        return PLAN.get(new Cell(-256+x,y,z));
    }

    private static Material material(int x,int y,int z) {
        BuildCanvas.BlockSpec spec=at(x,y,z);
        return spec==null?Material.AIR:spec.material();
    }

    @Test void compositionIsDeterministicAndContainsMoreThanUnfurnishedShells() {
        assertEquals(PLAN,plan());
        for(Material material:new Material[]{Material.FLETCHING_TABLE,Material.CRAFTING_TABLE,
            Material.GRINDSTONE,Material.BOOKSHELF,Material.GREEN_BED,Material.SPRUCE_DOOR,
            Material.LANTERN,Material.WATER,Material.LILY_PAD,Material.CRYING_OBSIDIAN})
            assertTrue(PLAN.values().stream().anyMatch(block->block.material()==material),material.name());
        assertTrue(PLAN.values().stream().filter(block->block.material().name().endsWith("_LEAVES")).count()>15_000);
        assertTrue(PLAN.keySet().stream().anyMatch(cell->cell.y()>140));
        assertTrue(PLAN.values().stream().noneMatch(block->block.material()==Material.SPAWNER));
    }

    @Test void resourceSlotsRemainEntirelyOwnedByTheHarvestCatalog() {
        int[][] clusters={{-14,-11},{13,-12},{-14,8},{14,8}};
        int[][] offsets={{-6,-3},{0,-5},{6,-2},{-7,3},{-1,1},{5,4},{0,7}};
        for(int[] cluster:clusters) for(int[] offset:offsets) for(int y=97;y<=99;y++) {
            int x=cluster[0]+offset[0],z=cluster[1]+offset[1];
            assertNull(at(x,y,z),"Permanent decoration occupied a renewable trunk at "+x+","+y+","+z);
        }
        PLAN.forEach((cell,block)-> {
            int x=cell.x()+256;
            if(Math.abs(x)<=25 && Math.abs(cell.z())<=20 && block.material().name().endsWith("_LEAVES"))
                assertTrue(cell.y()>=104,"Low leaves obstruct the gathering clearing: "+cell);
        });
    }

    @Test void arrivalCapsuleIsClearAndAllLeavesArePersistent() {
        for(int x=-2;x<=3;x++) for(int z=17;z<=22;z++) {
            assertTrue(material(x,96,z)==Material.STONE_BRICKS || material(x,96,z)==Material.MOSSY_STONE_BRICKS);
            for(int y=97;y<=101;y++) assertEquals(Material.AIR,material(x,y,z));
        }
        PLAN.values().stream().filter(block->block.material().name().endsWith("_LEAVES"))
            .forEach(block->assertTrue(block.properties().contains("persistent=true")));
    }

    @Test void lodgeHasUsableStairsADoorAndAnUpperGalleryLanding() {
        for(int z=43;z<=51;z++) for(int x=-47;x<=-43;x++) {
            int floor=Math.max(96,98-(z-43));
            assertNotEquals(Material.AIR,material(x,floor,z));
            assertEquals(Material.AIR,material(x,floor+1,z));
            assertEquals(Material.AIR,material(x,floor+2,z));
        }
        assertEquals(Material.SPRUCE_DOOR,material(-45,99,38));
        assertTrue(at(-45,99,38).properties().contains("half=lower"));
        assertEquals(Material.SPRUCE_DOOR,material(-45,100,38));
        assertEquals(Material.SPRUCE_PLANKS,material(-40,105,29));
        assertEquals(Material.SPRUCE_STAIRS,material(-40,105,30));
        assertEquals(Material.AIR,material(-45,106,38));
        assertEquals(Material.AIR,material(-45,107,38));
    }

    @Test void springHasAnUnbrokenFloorAndRetainingBank() {
        int water=ExpeditionTerrainV2.height(ExpeditionLayout.Zone.GROVE,42,-30)-1;
        for(int dx=-13;dx<=13;dx++) for(int dz=-10;dz<=10;dz++) {
            double edge=dx*dx/144.0+dz*dz/64.0;
            if(edge<.83) {
                assertNotEquals(Material.AIR,material(42+dx,water-3,-30+dz));
                assertNotEquals(Material.WATER,material(42+dx,water-3,-30+dz));
            } else if(edge<=1.25) {
                assertNotEquals(Material.AIR,material(42+dx,water,-30+dz));
                assertNotEquals(Material.WATER,material(42+dx,water,-30+dz));
            }
        }
    }
}
