package net.nerdsmp.paper.forge;

public final class GeodeRolls {
    private GeodeRolls() {
    }

    public static int tier(GeodeGrade grade, double roll) {
        double bounded = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        return switch (grade) {
            // Geodes crack into Rough or Flawed gems; merging climbs the rest of the ladder.
            case ROUGH -> bounded < 0.85D ? 1 : 2;
            case RESONANT -> bounded < 0.50D ? 1 : 2;
            case LUMINOUS -> bounded < 0.15D ? 1 : bounded < 0.85D ? 2 : 3;
        };
    }
}
