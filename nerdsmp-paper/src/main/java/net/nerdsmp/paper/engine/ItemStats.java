package net.nerdsmp.paper.engine;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Only stats with a gameplay consumer are offered by the editor. Values use percentage points, not ratios. */
public final class ItemStats {
    private static final Set<String> ALLOWED = Set.of("damage", "projectile-damage", "defense", "damage-flat",
        "damage-percent", "projectile-damage-percent", "mining-power", "fishing-power", "gathering-power",
        "excavation-power", "skill-xp-percent", "durability-save-percent", "poison-potency-percent",
        "execute-damage-percent", "life-steal-percent", "fishing-luck-percent", "treasure-find-percent",
        "excavation-find-percent", "geode-find-percent", "harvest-extra-percent", "job-marks-percent",
        "bleed-percent", "vein-size", "chain-harvest");
    private ItemStats() { }
    public static Set<String> allowed() { return ALLOWED; }
    public static Set<String> setBonusAllowed() {
        return ALLOWED.stream().filter(id -> !id.equals("damage") && !id.equals("projectile-damage"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static Map<String, Double> validate(Map<String, Double> stats, boolean setBonus) {
        if (stats == null || stats.size() > ALLOWED.size()) throw new IllegalArgumentException("Invalid stat collection.");
        var allowed = setBonus ? setBonusAllowed() : ALLOWED;
        Map<String, Double> result = new TreeMap<>();
        stats.forEach((id, value) -> {
            double maximum = id != null && (id.equals("damage") || id.equals("projectile-damage")
                || id.equals("defense") || id.equals("damage-flat")) ? 1_000 : 100;
            if (id == null || !allowed.contains(id) || value == null || !Double.isFinite(value) || value < 0 || value > maximum)
                throw new IllegalArgumentException("Unsupported or out-of-range stat: " + id + ".");
            if (value != 0) result.put(id, value);
        });
        return Map.copyOf(result);
    }
}
