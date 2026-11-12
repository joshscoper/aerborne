package net.nerdsmp.paper.death;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SpiritStateTest {
    final UUID world=UUID.randomUUID();
    SpiritState.Point point(double x,double y,double z){return new SpiritState.Point(world,x,y,z,0,0);}
    SpiritState ghost(boolean reachable){var location=point(0,64,0);return new SpiritState(new SpiritState.Soul(UUID.randomUUID(),location,location,"Sanctuary",reachable,1000,11000,new SpiritState.Abilities("SURVIVAL",false,true,true,location)),null,0,0);}
    @Test void corpseResurrectionHasHalfHealthAndNoSickness(){var state=ghost(true).resurrect(point(1,64,0),false,12000,60000,5000);assertTrue(state.ghost());assertEquals(.5,state.restoration().healthFraction());assertFalse(state.sick(12000));assertTrue(state.protectedNow(12000));assertFalse(state.restored().ghost());}
    @Test void healerResurrectionHasFullHealthAndBoundedSickness(){var state=ghost(true).resurrect(point(0,64,0),true,12000,60000,5000);assertEquals(1,state.restoration().healthFraction());assertTrue(state.sick(71999));assertFalse(state.sick(72000));assertFalse(state.protectedNow(17000));}
    @Test void unreachableBodyHasNoHealerSickness(){assertFalse(ghost(false).resurrect(point(0,64,0),true,12000,60000,5000).sick(12000));}
    @Test void cannotResurrectAnAlivePlayerOrStartTwoRestorations(){assertThrows(IllegalArgumentException.class,()->SpiritState.alive().resurrect(point(0,64,0),true,12000,60000,5000));var pending=ghost(true).resurrect(point(0,64,0),false,12000,60000,5000);assertThrows(IllegalArgumentException.class,()->pending.resurrect(point(0,64,0),true,13000,60000,5000));}
    @Test void rangeUsesThreeDimensionsAndExactWorldIdentity(){assertTrue(SpiritState.within(point(0,64,0),point(16,64,0),16));assertFalse(SpiritState.within(point(0,64,0),point(0,81,0),16));assertFalse(SpiritState.within(point(0,64,0),new SpiritState.Point(UUID.randomUUID(),0,64,0,0,0),16));assertFalse(SpiritState.within(point(0,64,0),point(0,64,0),Double.NaN));}
    @Test void originalFlightModeAndCompassAreRetainedThroughTransition(){var state=ghost(true);assertEquals(state.soul().abilities(),state.resurrect(point(1,64,0),false,12000,60000,5000).soul().abilities());assertTrue(state.soul().abilities().flying());assertEquals("SURVIVAL",state.soul().abilities().mode());}
    @Test void invalidCoordinatesAbilitiesAndIncompleteTransitionsAreRejected(){assertThrows(IllegalArgumentException.class,()->point(Double.NaN,64,0));assertThrows(IllegalArgumentException.class,()->point(30_000_001,64,0));assertThrows(IllegalArgumentException.class,()->new SpiritState.Abilities("CREATIVE",false,false,false,point(0,64,0)));assertThrows(IllegalArgumentException.class,()->new SpiritState.Abilities("SURVIVAL",false,false,true,point(0,64,0)));assertThrows(IllegalArgumentException.class,()->new SpiritState(null,new SpiritState.Restoration(point(0,64,0),1),0,0));}
    @Test void offensiveActionCanEndProtectionWithoutRemovingSickness(){var state=ghost(true).resurrect(point(0,64,0),true,12000,60000,5000).restored().endProtection();assertFalse(state.protectedNow(12000));assertTrue(state.sick(12000));}
    @Test void navigationWorksWithoutRequiringAnInventoryCompass(){assertEquals("N",SpiritState.direction(point(0,64,0),point(0,64,-20)));assertEquals("E",SpiritState.direction(point(0,64,0),point(20,64,0)));assertEquals("SW",SpiritState.direction(point(0,64,0),point(-20,64,20)));assertEquals("Here",SpiritState.direction(point(0,64,0),point(0,80,0)));}
}
