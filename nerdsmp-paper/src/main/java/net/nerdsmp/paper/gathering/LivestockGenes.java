package net.nerdsmp.paper.gathering;

import java.util.random.RandomGenerator;

/** Bounded, inheritable livestock traits. Legacy animals start with ordinary traits. */
public record LivestockGenes(int vitality, int growth, int fertility) {
    public LivestockGenes {
        for (int trait : new int[]{vitality, growth, fertility})
            if (trait < 0 || trait > 10) throw new IllegalArgumentException("Livestock traits must be 0-10");
    }
    public static LivestockGenes ordinary() { return new LivestockGenes(0, 0, 0); }
    public static LivestockGenes inherit(LivestockGenes mother, LivestockGenes father, double bonusPercent, RandomGenerator random) {
        double mutation = Math.clamp(.25 + Math.max(0, bonusPercent) / 100, .25, .65);
        return new LivestockGenes(trait(mother.vitality, father.vitality, mutation, random),
            trait(mother.growth, father.growth, mutation, random), trait(mother.fertility, father.fertility, mutation, random));
    }
    private static int trait(int a, int b, double mutation, RandomGenerator random) {
        int inherited = random.nextBoolean() ? a : b;
        return Math.min(10, inherited + (random.nextDouble() < mutation ? 1 : 0));
    }
    public double healthMultiplier() { return 1 + vitality * .05; }
    public int babyTicks() { return -(24_000 - growth * 600); }
    public int breedingCooldown() { return 6_000 - fertility * 120; }
}
