package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot;
import java.util.Map;

/** One composition rule for ordinary activity and batched tree work. Percentages are additive once. */
public record ActivityBonuses(double experiencePercent,double marksPercent) {
    public static ActivityBonuses resolve(Skill skill,Map<String,Double> equipment,ProgressionSnapshot training,
                                         double islandExperiencePercent,double rhythmPercent) {
        return new ActivityBonuses(equipment.getOrDefault("skill-xp-percent",0D)+SkillPower.experienceBonusPercent(skill,equipment)
            +training.talentStat(skill,"skill-xp-percent")+training.adventurerStat("skill-xp-percent")+islandExperiencePercent+rhythmPercent,
            equipment.getOrDefault("job-marks-percent",0D)+training.talentStat(skill,"job-marks-percent")+training.adventurerStat("job-marks-percent"));
    }
}
