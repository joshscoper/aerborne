package net.nerdsmp.paper.studio.qa;

import net.nerdsmp.paper.loot.profile.LootProfileDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class LootSimulationTest {
    @Test void deterministicTrialsAreRepeatableAndDoNotChangePublishedData() {
        var table=new LootProfileDefinition("crypt",Mode.REPLACE,List.of(new Entry("blade",Kind.ITEM,"sword",.3,1,2,true,2),
            new Entry("coins",Kind.MARKS,"marks",.5,2,4,false,0)));
        var before=List.copyOf(table.entries()); var result=LootSimulation.simulate(table,1234);
        assertEquals(result,LootSimulation.simulate(table,1234)); assertEquals(before,table.entries()); assertEquals(1000,result.samples());
        assertEquals(.45,result.entries().getFirst().expectedPerKill(),1e-9); assertEquals(1.5,result.expectedMarksPerKill(),1e-9);
        assertTrue(result.entries().getFirst().successfulRolls()>200); assertTrue(result.entries().getFirst().successfulRolls()<400);
        assertThrows(UnsupportedOperationException.class,()->result.entries().clear());
    }
    @Test void GuaranteedRewardsZeroChanceAndFixedQuantityAreExact() {
        var table=new LootProfileDefinition("fixed",Mode.ADDITIVE,List.of(new Entry("coins",Kind.MARKS,"marks",1,3,3,false,0),
            new Entry("never",Kind.ITEM,"blade",0,1,1,true,3)));
        var result=LootSimulation.simulate(table,55);
        assertEquals(1000,result.entries().getFirst().successfulRolls()); assertEquals(3000,result.entries().getFirst().quantity());
        assertEquals(0,result.entries().getLast().successfulRolls()); assertEquals(0,result.entries().getLast().quantity());
        assertEquals(3,result.expectedMarksPerKill()); assertEquals(3,result.sampledMarksPerKill()); assertEquals(Mode.ADDITIVE,result.mode());
    }
    @Test void rangeSamplingNeverExceedsAuthoredQuantityBounds() {
        var profile=new LootProfileDefinition("range",Mode.REPLACE,List.of(new Entry("tokens",Kind.ITEM,"token",1,2,5,false,0)));
        var entry=LootSimulation.simulate(profile,0).entries().getFirst();
        assertTrue(entry.quantity()>=2000 && entry.quantity()<=5000); assertEquals(3.5,entry.expectedPerKill());
    }
    @Test void emptyProfilesProduceEmptyNoRewardReports() {
        var result=LootSimulation.simulate(new LootProfileDefinition("empty",Mode.REPLACE,List.of()),0);
        assertTrue(result.entries().isEmpty()); assertEquals(0,result.expectedMarksPerKill()); assertEquals(0,result.sampledMarksPerKill());
    }
}
