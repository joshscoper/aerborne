package net.nerdsmp.paper.presentation;

final class DamageDisplayMath {
    private DamageDisplayMath() {
    }

    static int opacityAt(int age, int lifetime, int fadeInTicks, int fadeOutTicks) {
        if (age < 0 || lifetime <= 0 || age >= lifetime) {
            return 0;
        }
        int fadeIn = Math.max(0, Math.min(fadeInTicks, lifetime));
        int fadeOut = Math.max(0, Math.min(fadeOutTicks, lifetime));
        if (fadeIn == 1 && age == 0) {
            return 255;
        }
        if (fadeIn > 1 && age < fadeIn) {
            return clampOpacity((int) Math.round(255.0D * age / (fadeIn - 1.0D)));
        }
        int fadeStart = lifetime - fadeOut;
        if (fadeOut == 1 && age >= fadeStart) {
            return 0;
        }
        if (fadeOut > 1 && age >= fadeStart) {
            return clampOpacity((int) Math.round(255.0D * (lifetime - 1.0D - age) / (fadeOut - 1.0D)));
        }
        return 255;
    }

    static float scaleAt(int age, int lifetime, int fadeInTicks, int fadeOutTicks) {
        if (age < 0 || age >= lifetime) {
            return 0.9F;
        }
        if (fadeInTicks > 1 && age < fadeInTicks) {
            double progress = age / (fadeInTicks - 1.0D);
            double eased = 1.0D - Math.pow(1.0D - progress, 2.0D);
            return (float) (0.62D + 0.38D * eased);
        }
        int fadeStart = lifetime - Math.max(0, fadeOutTicks);
        if (fadeOutTicks > 1 && age >= fadeStart) {
            double progress = (age - fadeStart) / (fadeOutTicks - 1.0D);
            return (float) (1.0D - 0.08D * progress);
        }
        return 1.0F;
    }

    static double visibleDamage(double finalDamage) {
        return Math.max(0.0D, finalDamage);
    }

    private static int clampOpacity(int opacity) {
        return Math.max(0, Math.min(255, opacity));
    }
}
