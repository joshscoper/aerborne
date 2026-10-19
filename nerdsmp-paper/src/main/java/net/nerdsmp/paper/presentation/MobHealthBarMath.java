package net.nerdsmp.paper.presentation;

import java.util.Locale;

final class MobHealthBarMath {
    private MobHealthBarMath() {
    }

    static float progress(double health, double maximumHealth) {
        if (maximumHealth <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, health / maximumHealth));
    }

    static int filledSegments(double health, double maximumHealth, int segments) {
        if (segments < 1) {
            throw new IllegalArgumentException("segments must be positive");
        }
        if (health <= 0.0D || maximumHealth <= 0.0D) {
            return 0;
        }
        return Math.max(1, Math.min(segments, (int) Math.ceil(progress(health, maximumHealth) * segments)));
    }

    static String formatHealth(double health) {
        double safeHealth = Math.max(0.0D, health);
        if (Math.abs(safeHealth - Math.rint(safeHealth)) < 0.05D) {
            return String.format(Locale.ROOT, "%.0f", safeHealth);
        }
        return String.format(Locale.ROOT, "%.1f", safeHealth);
    }

    static boolean statusIconVisible(int remainingTicks, long clock, int blinkThreshold, int blinkInterval) {
        if (remainingTicks <= 0) {
            return false;
        }
        if (remainingTicks > blinkThreshold) {
            return true;
        }
        if (blinkInterval < 1) {
            throw new IllegalArgumentException("blink interval must be positive");
        }
        return clock / blinkInterval % 2L == 0L;
    }
}
