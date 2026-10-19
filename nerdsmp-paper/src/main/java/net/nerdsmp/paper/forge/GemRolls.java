package net.nerdsmp.paper.forge;

public final class GemRolls {
    private GemRolls() {
    }

    public static double prismaticChance(GeodeGrade grade) {
        return switch (grade) {
            case ROUGH -> 0.0D;
            case RESONANT -> 0.0005D;
            case LUMINOUS -> 0.005D;
        };
    }

    public static boolean prismatic(GeodeGrade grade, double roll) {
        return Math.max(0.0D, Math.min(1.0D, roll)) < prismaticChance(grade);
    }
}
