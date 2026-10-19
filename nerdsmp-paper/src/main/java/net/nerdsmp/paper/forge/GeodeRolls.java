package net.nerdsmp.paper.forge;

public final class GeodeRolls {
    private GeodeRolls() {
    }

    public static int tier(GeodeGrade grade, double roll) {
        double bounded = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        return switch (grade) {
            case ROUGH -> bounded < 0.70D ? 1 : bounded < 0.95D ? 2 : 3;
            case RESONANT -> bounded < 0.25D ? 1 : bounded < 0.80D ? 2 : bounded < 0.98D ? 3 : 4;
            case LUMINOUS -> bounded < 0.10D ? 2 : bounded < 0.70D ? 3 : 4;
        };
    }
}
