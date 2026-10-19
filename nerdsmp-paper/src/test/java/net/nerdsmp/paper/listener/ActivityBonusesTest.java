package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ActivityBonusesTest {
    private ProgressionService.ProgressionSnapshot training(Set<String> talents) {
        var skills=new EnumMap<Skill,ProgressionService.SkillProgress>(Skill.class);
        for(Skill skill:Skill.values()) skills.put(skill,new ProgressionService.SkillProgress(40_000,20));
        return new ProgressionService.ProgressionSnapshot(skills,Set.of(),talents,Instant.EPOCH);
    }
    @Test void equipmentAffixesGemsSetAndMatchingSkillBonusesAreAddedOnce() {
        var training=training(Set.of("forestry.woodcraft.1","forestry.woodcraft.2","forestry.timber_trade.1","mining.ore_study.1"));
        var stats=Map.of("skill-xp-percent",22.0,"job-marks-percent",7.5,"gathering-power",7.0,"mining-power",3.0);
        var forestry=ActivityBonuses.resolve(Skill.FORESTRY,stats,training,8,4);
        assertEquals(22+35+10+8+4+training.adventurerStat("skill-xp-percent"),forestry.experiencePercent(),1E-9);
        assertEquals(7.5+5+training.adventurerStat("job-marks-percent"),forestry.marksPercent(),1E-9);
        var mining=ActivityBonuses.resolve(Skill.MINING,stats,training,0,0);
        assertEquals(22+15+5+training.adventurerStat("skill-xp-percent"),mining.experiencePercent(),1E-9);
    }
    @Test void everySkillUsesOnlyItsOwnPowerAndTrainingWhileGlobalEquipmentXpStillApplies() {
        var training=training(Set.of("forestry.woodcraft.1"));
        var stats=Map.of("skill-xp-percent",12.0,"gathering-power",2.0,"fishing-power",3.0,"excavation-power",4.0,"mining-power",5.0);
        for(Skill skill:Skill.values()) {
            double expectedPower=switch(skill) {case MINING->25;case FISHING->15;case EXCAVATION->20;case FORESTRY,FARMING->10;default->0;};
            assertEquals(12+expectedPower+(skill==Skill.FORESTRY?5:0)+training.adventurerStat("skill-xp-percent"),
                ActivityBonuses.resolve(skill,stats,training,0,0).experiencePercent(),1E-9,skill.name());
        }
    }
}
