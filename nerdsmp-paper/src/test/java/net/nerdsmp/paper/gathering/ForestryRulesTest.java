package net.nerdsmp.paper.gathering;

import org.junit.jupiter.api.Test;
import java.util.*;
import static net.nerdsmp.paper.gathering.ForestryRules.*;
import static org.junit.jupiter.api.Assertions.*;

class ForestryRulesTest {
    final Point root=new Point(0,64,0);
    final Cell air=new Cell("AIR",false,false,false);
    Map<Point,Cell> tree(String family,int height) {
        Map<Point,Cell> cells=new HashMap<>();
        cells.put(root.offset(0,-1,0),new Cell("DIRT",false,false,true));
        for(int y=0;y<height;y++) cells.put(root.offset(0,y,0),new Cell(family+"_LOG",true,false,false));
        cells.put(root.offset(0,height,0),new Cell(family+"_LEAVES",false,true,false));return cells;
    }
    Plan plan(Map<Point,Cell> cells,int maximum) {return ForestryRules.plan(root,p->cells.getOrDefault(p,air),maximum);}
    @Test void recognizesOneTreeAndKeepsItsRootFirst() {
        var result=plan(tree("OAK",6),16);
        assertEquals(State.READY,result.state());assertEquals(6,result.logs().size());
        assertEquals(List.of(root),result.roots());assertEquals(root,result.logs().getFirst());assertEquals("OAK",result.family());
        assertThrows(UnsupportedOperationException.class,()->result.logs().clear());
    }
    @Test void placedLogsAndDecorativeLeavesNeverBecomeFellingTargets() {
        var cells=tree("OAK",5);cells.put(root.offset(1,2,0),new Cell("OAK_LOG",false,false,false));
        assertEquals(5,plan(cells,16).logs().size());
        cells.put(root,new Cell("OAK_LOG",false,false,false));assertEquals(State.NOT_TREE,plan(cells,16).state());
        cells=tree("OAK",5);cells.put(root.offset(0,5,0),new Cell("OAK_LEAVES",false,false,false));
        assertEquals(State.NOT_TREE,plan(cells,16).state());
    }
    @Test void multipleNaturalRootClustersAreNotMistakenForOneTree() {
        var cells=tree("OAK",5);
        for(int y=0;y<5;y++) cells.put(root.offset(2,y,0),new Cell("OAK_LOG",true,false,false));
        cells.put(root.offset(2,-1,0),new Cell("DIRT",false,false,true));
        cells.put(root.offset(1,4,0),new Cell("OAK_LOG",true,false,false));
        assertEquals(State.AMBIGUOUS,plan(cells,32).state());
    }
    @Test void largeTwoByTwoTreesRetainAllFourPlantingCells() {
        var cells=tree("SPRUCE",6);
        for(int x=0;x<2;x++) for(int z=0;z<2;z++) {
            cells.put(root.offset(x,-1,z),new Cell("DIRT",false,false,true));
            for(int y=0;y<6;y++) cells.put(root.offset(x,y,z),new Cell("SPRUCE_LOG",true,false,false));
        }
        assertEquals(State.TOO_LARGE,plan(cells,16).state());
        var result=plan(cells,32);assertEquals(State.READY,result.state());assertEquals(24,result.logs().size());assertEquals(4,result.roots().size());
    }
    @Test void unloadedCellsSizeCapsAndMissingSoilRefuseWithoutAPartialPlan() {
        var cells=tree("OAK",20);assertEquals(State.TOO_LARGE,plan(cells,16).state());
        assertTrue(plan(cells,16).logs().isEmpty());
        var shortTree=tree("OAK",5);
        assertEquals(State.UNLOADED,ForestryRules.plan(root,p->p.x()==1?null:shortTree.getOrDefault(p,air),16).state());
        shortTree.remove(root.offset(0,-1,0));assertEquals(State.NOT_TREE,plan(shortTree,16).state());
    }
    @Test void neighboringSpeciesDoNotBecomePartOfTheHarvest() {
        var cells=tree("OAK",5);cells.put(root.offset(1,2,0),new Cell("BIRCH_LOG",true,false,false));
        assertEquals(5,plan(cells,16).logs().size());
        assertEquals("OAK",family("STRIPPED_OAK_LOG"));assertEquals("CRIMSON_FUNGUS",sapling("CRIMSON"));
        assertEquals("MANGROVE_PROPAGULE",sapling("MANGROVE"));assertEquals("PALE_OAK_SAPLING",sapling("PALE_OAK"));
    }
    @Test void skillMilestonesAndExperienceRatesPreserveConfiguredBaseValues() {
        assertEquals(16,capacity(0));assertEquals(32,capacity(10));assertEquals(64,capacity(20));assertEquals(128,capacity(35));assertEquals(192,capacity(50));
        assertEquals(18,experience(6,3));assertEquals(30,experience(10,3));assertEquals(6,experience(6,1));
        assertThrows(IllegalArgumentException.class,()->experience(6,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->experience(6,11));
        assertThrows(IllegalArgumentException.class,()->ForestryRules.plan(root,p->air,193));
    }
}
