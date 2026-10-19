package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.SkillTreeCatalog;
import java.util.*;

/** Global passives are selected by their stat, never by a branch's visual position. */
public final class PassiveTalentStats {
    private static final Map<String, String> GLOBAL = Map.of(
        "expedition-geode-percent", "geode-find-percent",
        "equipment-preservation-percent", "durability-save-percent",
        "durability-save-percent", "durability-save-percent",
        "fishing-luck-percent", "fishing-luck-percent",
        "life-steal-percent", "life-steal-percent",
        "excavation-find-percent", "excavation-find-percent",
        "poison-potency-percent", "poison-potency-percent");
    private PassiveTalentStats() { }
    public static Map<String, Double> resolve(Set<String> talents) {
        Map<String, Double> result = new HashMap<>();
        for (String id : talents) SkillTreeCatalog.node(id).ifPresent(node -> {
            String target = GLOBAL.get(node.stat());
            if (target != null) result.merge(target, node.value(), Double::sum);
        });
        return Map.copyOf(result);
    }
}
