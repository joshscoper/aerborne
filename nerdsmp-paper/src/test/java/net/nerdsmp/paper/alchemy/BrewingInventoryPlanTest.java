package net.nerdsmp.paper.alchemy;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BrewingInventoryPlanTest {
    private BrewingInventoryPlan.Stack<String> s(String kind,int amount,int cap) { return new BrewingInventoryPlan.Stack<>(kind,amount,cap); }
    @Test void consumesAcrossStacksAndReturnsContainersWithoutMutatingInput() {
        var original=new ArrayList<>(Arrays.asList(s("wheat",1,64),s("wheat",2,64),s("honey",1,16),null));
        var before=new ArrayList<>(original);
        var result=BrewingInventoryPlan.prepare(original,Map.of("wheat",2,"honey",1),List.of(s("tonic",1,1),s("bottle",1,64))).orElseThrow();
        assertEquals(before,original);
        assertEquals(1,result.stream().filter(Objects::nonNull).filter(i->i.kind().equals("wheat")).mapToInt(BrewingInventoryPlan.Stack::amount).sum());
        assertTrue(result.contains(s("tonic",1,1))); assertTrue(result.contains(s("bottle",1,64)));
        assertThrows(UnsupportedOperationException.class,()->result.set(0,null));
    }
    @Test void insufficientIngredientsOrOutputSpaceNeverProducesPartialPlan() {
        var inventory=List.of(s("honey",2,16),s("stone",64,64));
        assertTrue(BrewingInventoryPlan.prepare(inventory,Map.of("wheat",1),List.of(s("tonic",1,1))).isEmpty());
        assertTrue(BrewingInventoryPlan.prepare(inventory,Map.of("honey",1),List.of(s("tonic",1,1),s("bottle",1,64))).isEmpty());
        assertEquals(2,inventory.getFirst().amount());
    }
    @Test void consumedSlotCanFitOutputAndBottleCanMerge() {
        var result=BrewingInventoryPlan.prepare(List.of(s("honey",1,16),s("bottle",63,64)),Map.of("honey",1),List.of(s("tonic",1,1),s("bottle",1,64))).orElseThrow();
        assertEquals(List.of(s("tonic",1,1),s("bottle",64,64)),result);
    }
    @Test void decoratedIngredientsCannotBeSubstitutedAndStackLimitsAreHonored() {
        var inventory=new ArrayList<>(Arrays.asList(s("named-honey",1,16),s("tonic",1,1),null));
        assertTrue(BrewingInventoryPlan.prepare(inventory,Map.of("honey",1),List.of(s("tonic",1,1))).isEmpty());
        var result=BrewingInventoryPlan.prepare(inventory,Map.of(),List.of(s("tonic",1,1))).orElseThrow();
        assertEquals(1,result.get(1).amount()); assertEquals(1,result.get(2).amount());
    }
    @Test void invalidQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->s("tonic",2,1));
        assertThrows(IllegalArgumentException.class,()->BrewingInventoryPlan.prepare(List.of(),Map.of("wheat",0),List.of()));
    }
}
