package net.nerdsmp.paper.mob;

final class BossEncounterRules {
    private BossEncounterRules() {
    }

    static int crossedPhase(double currentHealth, double incomingDamage, double maximumHealth,
                            int phasesStarted, double[] thresholds) {
        if (maximumHealth <= 0.0D || phasesStarted < 0 || phasesStarted >= thresholds.length) {
            return -1;
        }
        double healthAfter = Math.max(0.0D, currentHealth - Math.max(0.0D, incomingDamage));
        return healthAfter / maximumHealth <= thresholds[phasesStarted] ? phasesStarted : -1;
    }

    static int completedPhases(double currentHealth, double maximumHealth, double[] thresholds) {
        if (maximumHealth <= 0.0D) {
            return 0;
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, currentHealth / maximumHealth));
        int completed = 0;
        for (double threshold : thresholds) {
            if (ratio <= threshold) {
                completed++;
            }
        }
        return completed;
    }
}
