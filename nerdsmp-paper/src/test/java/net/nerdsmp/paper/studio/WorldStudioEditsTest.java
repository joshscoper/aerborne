package net.nerdsmp.paper.studio;

import net.nerdsmp.paper.zone.ZoneDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class WorldStudioEditsTest {
    private ZoneDefinition zone() {
        return new ZoneDefinition("orchard", "The Orchard", UUID.randomUUID(), new ZoneDefinition.Bounds(-8,64,-8,8,90,8),
            false,false,8,20,48,List.of(new ZoneDefinition.SpawnPoint(0,65,0,0)),List.of(new ZoneDefinition.MobEntry("vanilla:zombie",1,5)));
    }
    @Test void pageClampHandlesEmptyAndOversizedRequests() {
        List<Integer> values=IntStream.range(0,43).boxed().toList();
        assertEquals(List.of(42),WorldStudioEdits.page(values,100));
        assertEquals(21,WorldStudioEdits.page(values,-5).size());
        assertEquals(List.of(),WorldStudioEdits.page(List.of(),0));
        assertEquals(3,WorldStudioEdits.pages(43));
        assertEquals(1,WorldStudioEdits.pages(0));
    }
    @Test void staleSnapshotsRejectDeletedOrEditedZones() {
        ZoneDefinition z=zone();
        assertDoesNotThrow(()->WorldStudioEdits.requireCurrent(z,z));
        assertThrows(IllegalStateException.class,()->WorldStudioEdits.requireCurrent(z,null));
        assertThrows(IllegalStateException.class,()->WorldStudioEdits.requireCurrent(z,WorldStudioEdits.field(z,WorldStudioEdits.Field.CAP,"9")));
    }
    @Test void fieldsKeepAllUnrelatedSettings() {
        ZoneDefinition z=zone(), next=WorldStudioEdits.field(z,WorldStudioEdits.Field.TITLE,"Cinder Woods");
        assertEquals("Cinder Woods",next.title());
        assertEquals(z.bounds(),next.bounds()); assertEquals(z.worldId(),next.worldId());
        assertEquals(z.points(),next.points()); assertEquals(z.mobs(),next.mobs());
        assertEquals(16,WorldStudioEdits.field(z,WorldStudioEdits.Field.CAP,"16").cap());
        assertEquals(60,WorldStudioEdits.field(z,WorldStudioEdits.Field.RESPAWN,"60").respawnSeconds());
        assertEquals(64,WorldStudioEdits.field(z,WorldStudioEdits.Field.RADIUS,"64").activationRadius());
        assertTrue(WorldStudioEdits.field(z,WorldStudioEdits.Field.SAFE,"on").safe());
        assertTrue(WorldStudioEdits.field(z,WorldStudioEdits.Field.ENABLED,"on").enabled());
    }
    @Test void invalidNumbersTitlesAndSwitchesCannotCreateDefinitions() {
        ZoneDefinition z=zone();
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.field(z,WorldStudioEdits.Field.CAP,"65"));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.field(z,WorldStudioEdits.Field.RADIUS,"7"));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.field(z,WorldStudioEdits.Field.RESPAWN,"4"));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.field(z,WorldStudioEdits.Field.ENABLED,"yes"));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.field(z,WorldStudioEdits.Field.TITLE,"\n"));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.number("1;op"));
    }
    @Test void rosterUpdatesOneEntryWithoutLosingOtherMobs() {
        ZoneDefinition z=zone();
        ZoneDefinition next=WorldStudioEdits.roster(z,new ZoneDefinition.MobEntry("custom:veil_stalker",5,10));
        next=WorldStudioEdits.roster(next,new ZoneDefinition.MobEntry("vanilla:zombie",2,20));
        assertEquals(2,next.mobs().size());
        assertEquals(1,z.mobs().size());
        assertEquals(20,next.mobs().stream().filter(m->m.mobId().equals("vanilla:zombie")).findFirst().orElseThrow().level());
        assertEquals(1,WorldStudioEdits.removeMob(next,"vanilla:zombie").mobs().size());
        ZoneDefinition current=next;
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.removeMob(current,"vanilla:cow"));
    }
    @Test void spawnPointsPreserveOrientationAndRejectDuplicatesOrOutside() {
        ZoneDefinition z=zone();
        ZoneDefinition next=WorldStudioEdits.addPoint(z,new ZoneDefinition.SpawnPoint(3.5,65,2.5,93));
        assertEquals(93,next.points().get(1).yaw());
        assertEquals(1,WorldStudioEdits.removePoint(next,1).points().size());
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.addPoint(z,z.points().getFirst()));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.addPoint(z,new ZoneDefinition.SpawnPoint(30,65,0,0)));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.removePoint(z,-1));
    }
    @Test void activeEncountersCannotLoseTheirLastPointOrMob() {
        ZoneDefinition z=WorldStudioEdits.field(zone(),WorldStudioEdits.Field.ENABLED,"on");
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.removePoint(z,0));
        assertThrows(IllegalArgumentException.class,()->WorldStudioEdits.removeMob(z,"vanilla:zombie"));
        assertEquals(0,WorldStudioEdits.removeMob(WorldStudioEdits.field(z,WorldStudioEdits.Field.ENABLED,"off"),"vanilla:zombie").mobs().size());
    }
    @Test void readOnlyOutlineIsBoundedEvenForHugeZones() {
        var bounds=new ZoneDefinition.Bounds(0,-64,0,2047,-61,2047);
        List<WorldStudioEdits.PreviewPoint> points=WorldStudioEdits.outline(bounds);
        assertTrue(points.size()<=WorldStudioEdits.MAX_OUTLINE_POINTS);
        assertTrue(points.stream().allMatch(p->p.x()>=0&&p.x()<=2048&&p.y()>=-64&&p.y()<=-60&&p.z()>=0&&p.z()<=2048));
        assertTrue(points.contains(new WorldStudioEdits.PreviewPoint(2048,-60,2048)));
        assertThrows(UnsupportedOperationException.class,()->points.clear());
    }
}
