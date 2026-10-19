package net.nerdsmp.paper.mob;

import java.util.Arrays;
import java.util.Optional;

public enum VanillaBossKind {
    ENDER_DRAGON("ENDER_DRAGON", new double[]{0.75D, 0.50D, 0.25D}, 3, 12_000L),
    WITHER("WITHER", new double[]{0.70D, 0.35D}, 3, 10_000L),
    ELDER_GUARDIAN("ELDER_GUARDIAN", new double[]{0.66D, 0.33D}, 3, 9_000L),
    WARDEN("WARDEN", new double[]{0.75D, 0.50D, 0.25D}, 3, 11_000L);

    private final String entityType;
    private final double[] thresholds;
    private final int baseObjectives;
    private final long mechanicCooldownMillis;

    VanillaBossKind(String entityType, double[] thresholds, int baseObjectives, long mechanicCooldownMillis) {
        this.entityType = entityType;
        this.thresholds = Arrays.copyOf(thresholds, thresholds.length);
        this.baseObjectives = baseObjectives;
        this.mechanicCooldownMillis = mechanicCooldownMillis;
    }

    public static Optional<VanillaBossKind> fromEntityType(String entityType) {
        return Arrays.stream(values()).filter(kind -> kind.entityType.equals(entityType)).findFirst();
    }

    public double[] thresholds() {
        return Arrays.copyOf(thresholds, thresholds.length);
    }

    public int baseObjectives() {
        return baseObjectives;
    }

    public long mechanicCooldownMillis() {
        return mechanicCooldownMillis;
    }
}
