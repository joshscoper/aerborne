package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.Skill;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GatheringRhythmTest {
    private final UUID player=UUID.randomUUID(),world=UUID.randomUUID();
    @Test void twentyValidMixedHarvestsReachButNeverExceedTwentyPercent() {
        GatheringRhythm rhythms=new GatheringRhythm();
        String[] crops={"WHEAT","CARROTS","POTATOES","BEETROOTS"};
        int milestones=0;
        for(int i=0;i<100;i++) {
            var result=rhythms.record(player,world,Skill.FARMING,crops[i%4],"work"+i,i*200L);
            assertTrue(result.bonusPercent()>=0&&result.bonusPercent()<=20);
            assertEquals(Math.min(20,i+1),result.chain());
            if(result.milestone()) milestones++;
            if(i>=19) { assertEquals(20,result.bonusPercent()); assertEquals(4,result.varieties()); }
        }
        assertEquals(4,milestones,"A capped chain must not keep replaying its milestone feedback");
    }
    @Test void monocultureStillBuildsRhythmButVarietyFallsOutOfTheRecentWindow() {
        GatheringRhythm rhythms=new GatheringRhythm();
        rhythms.record(player,world,Skill.FARMING,"WHEAT","wheat",0);
        var mixed=rhythms.record(player,world,Skill.FARMING,"CARROTS","carrots",200);
        assertEquals(4,mixed.bonusPercent());
        GatheringRhythm.Result last=null;
        for(int i=0;i<25;i++) last=rhythms.record(player,world,Skill.FARMING,"CARROTS","carrot"+i,400+i*200L);
        assertEquals(1,last.varieties()); assertEquals(8,last.bonusPercent());
    }
    @Test void duplicateOperationsAndSameTickBatchesDoNotAdvanceTheChain() {
        GatheringRhythm rhythms=new GatheringRhythm();
        assertEquals(1,rhythms.record(player,world,Skill.FORESTRY,"OAK_LOG","one",0).chain());
        assertEquals(GatheringRhythm.Result.NONE,rhythms.record(player,world,Skill.FORESTRY,"BIRCH_LOG","two",99));
        assertEquals(GatheringRhythm.Result.NONE,rhythms.record(player,world,Skill.FORESTRY,"OAK_LOG","one",500));
        assertEquals(2,rhythms.record(player,world,Skill.FORESTRY,"BIRCH_LOG","three",500).chain());
    }
    @Test void worldChangeInactivityAndReversedClockResetTheSequence() {
        GatheringRhythm rhythms=new GatheringRhythm();
        rhythms.record(player,world,Skill.FARMING,"WHEAT","a",0);
        assertEquals(2,rhythms.record(player,world,Skill.FARMING,"WHEAT","b",12_000).chain());
        assertEquals(1,rhythms.record(player,world,Skill.FARMING,"WHEAT","c",24_001).chain());
        assertEquals(1,rhythms.record(player,UUID.randomUUID(),Skill.FARMING,"WHEAT","d",24_200).chain());
        assertEquals(1,rhythms.record(player,world,Skill.FARMING,"WHEAT","e",1).chain());
        assertEquals(60_000,GatheringRhythm.window(Skill.FORESTRY));
    }
    @Test void strippedAndUnstrippedTimberCannotPretendToBeDifferentSpecies() {
        assertEquals("OAK",GatheringRhythm.family(Skill.FORESTRY,"OAK_LOG"));
        assertEquals("OAK",GatheringRhythm.family(Skill.FORESTRY,"STRIPPED_OAK_LOG"));
        assertEquals("CRIMSON",GatheringRhythm.family(Skill.FORESTRY,"CRIMSON_STEM"));
        assertEquals("CRIMSON",GatheringRhythm.family(Skill.FORESTRY,"STRIPPED_CRIMSON_HYPHAE"));
        assertNull(GatheringRhythm.family(Skill.FORESTRY,"OAK_PLANKS"));
        assertNull(GatheringRhythm.family(Skill.FARMING,"FIRE"));
        assertNull(GatheringRhythm.family(Skill.MINING,"IRON_ORE"));
    }
    @Test void SkillSessionsAreSeparateAndLogoutOnlyClearsThatPlayer() {
        GatheringRhythm rhythms=new GatheringRhythm(); UUID other=UUID.randomUUID();
        rhythms.record(player,world,Skill.FARMING,"WHEAT","a",0);
        rhythms.record(player,world,Skill.FORESTRY,"OAK_LOG","b",0);
        rhythms.record(other,world,Skill.FARMING,"WHEAT","c",0);
        assertEquals(3,rhythms.size()); rhythms.clear(player); assertEquals(1,rhythms.size());
    }
    @Test void StateHasAHardSizeBoundEvenWhenPlayersNeverEmitQuitEvents() {
        GatheringRhythm rhythms=new GatheringRhythm();
        for(int i=0;i<GatheringRhythm.MAX_SESSIONS+100;i++)
            rhythms.record(new UUID(0,i),world,Skill.FARMING,"WHEAT","work",0);
        assertEquals(GatheringRhythm.MAX_SESSIONS,rhythms.size());
    }
    @Test void UnknownMaterialsAndEmptyOperationsNeverCreateState() {
        GatheringRhythm rhythms=new GatheringRhythm();
        assertEquals(GatheringRhythm.Result.NONE,rhythms.record(player,world,Skill.FARMING,"STONE","a",0));
        assertEquals(GatheringRhythm.Result.NONE,rhythms.record(player,world,Skill.FARMING,"WHEAT","",0));
        assertEquals(0,rhythms.size());
    }
}
