package net.nerdsmp.paper.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether one piece of gear beats another. Fighting stats count most, life-skill stats a little, so a
 * better helmet is one that keeps you alive, not one that mines faster. Free of Bukkit.
 */
public final class GearComparison {
    private static final Map<String, Double> WEIGHTS = Map.ofEntries(
        Map.entry("damage", 3D), Map.entry("projectile-damage", 3D), Map.entry("damage-flat", 3D), Map.entry("defense", 3D),
        Map.entry("life-steal-percent", 2D), Map.entry("max-health", 1D), Map.entry("max-health-percent", 1D),
        Map.entry("damage-percent", 1D), Map.entry("projectile-damage-percent", 1D), Map.entry("critical-chance-percent", 1D),
        Map.entry("critical-damage-percent", .5D), Map.entry("execute-damage-percent", .5D));
    private static final double OTHER_WEIGHT = .25D;
    /** Rounding noise and tiny rolls don't count as an upgrade. */
    private static final double MARGIN = .05D;

    private GearComparison() { }

    private static double weight(String stat) { return WEIGHTS.getOrDefault(stat, OTHER_WEIGHT); }

    public static double score(Map<String, Double> stats) {
        double score = 0;
        for (var stat : stats.entrySet()) score += stat.getValue() * weight(stat.getKey());
        return score;
    }

    public static boolean upgrade(Map<String, Double> candidate, Map<String, Double> current) {
        return score(candidate) > score(current) + MARGIN;
    }

    /** The stats a player gains, biggest first, at most {@code limit}. */
    public static List<String> gains(Map<String, Double> candidate, Map<String, Double> current, int limit) {
        Set<String> stats = new HashSet<>(candidate.keySet());
        stats.addAll(current.keySet());
        List<String> gained = new ArrayList<>();
        for (String stat : stats) if (gain(candidate, current, stat) > 0) gained.add(stat);
        gained.sort(Comparator.<String>comparingDouble(stat -> -gain(candidate, current, stat) * weight(stat)).thenComparing(stat -> stat));
        return gained.subList(0, Math.min(limit, gained.size()));
    }

    static double gain(Map<String, Double> candidate, Map<String, Double> current, String stat) {
        return candidate.getOrDefault(stat, 0D) - current.getOrDefault(stat, 0D);
    }
}
