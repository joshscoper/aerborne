package net.nerdsmp.paper.zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneSpawnPolicyTest {
    private static final UUID WORLD=new UUID(8,9);
    private static ZoneDefinition zone(String id,int cap,int count) {
        List<ZoneDefinition.SpawnPoint> points=new ArrayList<>();
        for(int i=0;i<count;i++) points.add(new ZoneDefinition.SpawnPoint(i+.5,70,.5,0));
        return new ZoneDefinition(id,id,WORLD,new ZoneDefinition.Bounds(-1,60,-1,200,100,10),
            true,false,cap,20,48,points,List.of(new ZoneDefinition.MobEntry("vanilla:zombie",1,8)));
    }
    @Test void pointOccupancyAndZoneCapAreIndependentAndDuplicateReceiptsCannotConsumeCapacity() {
        var policy=new ZoneSpawnPolicy(); var zone=zone("camp",2,3);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        assertTrue(policy.spawned(zone,0,first,0));
        assertFalse(policy.spawned(zone,1,first,0));
        assertFalse(policy.spawned(zone,0,second,0));
        assertTrue(policy.spawned(zone,1,second,0));
        assertFalse(policy.available(zone,2,0));
        assertEquals(2,policy.alive("camp")); assertEquals(2,policy.alive());
    }
    @Test void respawnTimerStartsOnDeathNotSpawnAndDuplicateRemovalDoesNotExtendIt() {
        var policy=new ZoneSpawnPolicy(); var zone=zone("camp",2,3); UUID id=UUID.randomUUID();
        assertTrue(policy.spawned(zone,0,id,0));
        assertFalse(policy.available(zone,0,100_000));
        policy.removed(id,100_000);
        assertFalse(policy.available(zone,0,119_999));
        policy.removed(id,110_000);
        assertTrue(policy.available(zone,0,120_000));
        assertEquals(0,policy.alive());
    }
    @Test void failedOrCancelledSpawnReservesNoCapacityButBacksOffItsPoint() {
        var policy=new ZoneSpawnPolicy(); var zone=zone("camp",1,2);
        policy.failed(zone,0,10_000);
        assertEquals(0,policy.alive());
        assertFalse(policy.available(zone,0,14_999));
        assertTrue(policy.available(zone,0,15_000));
        assertTrue(policy.available(zone,1,10_000));
    }
    @Test void globalCapIsEnforcedAcrossZonesAndFreedByRemoval() {
        var policy=new ZoneSpawnPolicy(); UUID first=null;
        for(int z=0;z<4;z++) {
            var zone=zone("camp"+z,64,64);
            for(int point=0;point<64;point++) {
                UUID id=UUID.randomUUID(); if(first==null) first=id;
                assertTrue(policy.spawned(zone,point,id,0));
            }
        }
        var fifth=zone("fifth",64,64);
        assertEquals(256,policy.alive()); assertFalse(policy.available(fifth,0,0));
        policy.removed(first,1);
        assertTrue(policy.spawned(fifth,0,UUID.randomUUID(),1)); assertEquals(256,policy.alive());
    }
    @Test void inactiveAndSafeZonesDoNotReserveAndDefinitionRemovalClearsOnlyItsOwnState() {
        var policy=new ZoneSpawnPolicy(); var camp=zone("camp",3,3); var other=zone("other",3,3);
        var safe=new ZoneDefinition("safe","Safe",WORLD,camp.bounds(),true,true,2,20,48,camp.points(),camp.mobs());
        var disabled=new ZoneDefinition("disabled","Disabled",WORLD,camp.bounds(),false,false,2,20,48,camp.points(),camp.mobs());
        assertFalse(policy.available(safe,0,0)); assertFalse(policy.available(disabled,0,0));
        assertTrue(policy.spawned(camp,0,UUID.randomUUID(),0));
        assertTrue(policy.spawned(other,0,UUID.randomUUID(),0));
        policy.failed(camp,1,0); policy.retain(Set.of("other"));
        assertEquals(0,policy.alive("camp")); assertEquals(1,policy.alive("other"));
        assertTrue(policy.available(camp,1,1));
        policy.clear(); assertEquals(0,policy.alive());
    }
    @Test void zoneAndPointCursorsContinueAfterEveryProbeAndAfterListChanges() {
        var policy=new ZoneSpawnPolicy(); var first=zone("first",2,3); var second=zone("second",2,2);
        for(int i=0;i<15;i++) {
            assertEquals(i%2==0?first:second,policy.nextZone(List.of(first,second)));
            assertEquals(i%3,policy.nextPoint(first));
        }
        assertEquals(first,policy.nextZone(List.of(first)));
        assertNull(policy.nextZone(List.of()));
    }
    @Test void weightedChoiceUsesTheWholeTableWithoutUniformizingWeights() {
        var common=new ZoneDefinition.MobEntry("vanilla:zombie",3,1);
        var rare=new ZoneDefinition.MobEntry("vanilla:skeleton",1,25);
        Random random=new Random(91); int rareCount=0;
        for(int i=0;i<20_000;i++) {
            var pick=ZoneSpawnPolicy.choose(List.of(common,rare),random);
            assertTrue(pick==common||pick==rare);
            if(pick==rare) rareCount++;
        }
        assertTrue(rareCount>4_700&&rareCount<5_300,"Rare selections: "+rareCount);
        assertThrows(IllegalArgumentException.class,()->ZoneSpawnPolicy.choose(List.of(),random));
        assertEquals(Long.MAX_VALUE,ZoneSpawnPolicy.deadline(Long.MAX_VALUE-2,5000));
    }
    @Test void spawnFootingRejectsFluidFireAndContactDamageEvenIfPassable() {
        for(Material hazard:new Material[]{Material.LAVA,Material.WATER,Material.FIRE,Material.CAMPFIRE,
            Material.POWDER_SNOW,Material.MAGMA_BLOCK,Material.WITHER_ROSE,Material.NETHER_PORTAL,
            Material.CACTUS,Material.SWEET_BERRY_BUSH,Material.POINTED_DRIPSTONE})
            assertFalse(ZoneSpawner.harmless(hazard),hazard.name());
        assertTrue(ZoneSpawner.harmless(Material.STONE)); assertTrue(ZoneSpawner.harmless(Material.AIR));
    }
}
