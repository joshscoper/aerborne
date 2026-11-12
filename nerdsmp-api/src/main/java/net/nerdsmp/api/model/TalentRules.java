package net.nerdsmp.api.model;

import net.nerdsmp.api.service.ProgressionService.*;

/** One authoritative eligibility rule for file storage, SQL, and menu previews. */
public final class TalentRules {
    private TalentRules() { }
    public static String canonicalId(String id) {
        return SkillTreeCatalog.node(id).map(SkillTreeCatalog.TalentNode::id)
            .orElseGet(() -> AdventurerTalents.node(id).map(AdventurerTalents.Node::id).orElse(null));
    }
    public static TalentResult check(ProgressionSnapshot snapshot, String id) {
        String canonical = canonicalId(id);
        if (canonical == null) return TalentResult.UNKNOWN_TALENT;
        if (snapshot.talents().contains(canonical)) return TalentResult.ALREADY_UNLOCKED;
        var skill = SkillTreeCatalog.node(canonical).orElse(null);
        int level, required, available; TalentRequirement requirement;
        if (skill != null) {
            level = snapshot.skills().getOrDefault(skill.skill(),new SkillProgress(0,0)).level();
            required = skill.requiredLevel(); available = snapshot.talentPointsAvailable(skill.skill()); requirement = skill.requirement();
        } else {
            var node = AdventurerTalents.node(canonical).orElseThrow();
            level = snapshot.adventurer().level(); required = node.requiredLevel(); available = snapshot.adventurerPointsAvailable(); requirement = node.requirement();
        }
        if (level < required) return TalentResult.LEVEL_TOO_LOW;
        if (!requirement.met(snapshot.talents())) return TalentResult.PREREQUISITE_MISSING;
        return available > 0 ? TalentResult.UNLOCKED : TalentResult.NO_POINTS_AVAILABLE;
    }
}
