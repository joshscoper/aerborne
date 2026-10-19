package net.nerdsmp.paper.item;

final class GearMath {
    private GearMath() {
    }

    static double calculateDamage(double baseDamage, double flatBonus, double percentBonus, double cooldown) {
        double clampedCooldown = Math.max(0.0D, Math.min(1.0D, cooldown));
        double cooldownScale = 0.2D + (clampedCooldown * clampedCooldown * 0.8D);
        double damage = Math.max(0.0D, baseDamage + flatBonus)
            * (1.0D + Math.max(-100.0D, percentBonus) / 100.0D);
        return Math.max(0.1D, damage * cooldownScale);
    }

    static double mitigateDamage(double incomingDamage, double defense) {
        if (incomingDamage <= 0.0D || defense <= 0.0D) {
            return Math.max(0.0D, incomingDamage);
        }
        return incomingDamage * (100.0D / (100.0D + defense));
    }

    static double applyPercentBonus(double incomingDamage, double percentBonus) {
        double multiplier = 1.0D + Math.max(-100.0D, percentBonus) / 100.0D;
        return Math.max(0.0D, incomingDamage) * multiplier;
    }

    static double applyCritical(double incomingDamage, boolean critical, double criticalMultiplier) {
        double multiplier = critical ? Math.max(1.0D, criticalMultiplier) : 1.0D;
        return Math.max(0.0D, incomingDamage) * multiplier;
    }

    static double lifeSteal(double finalDamage,double targetHealth,double percent) {
        return Math.min(4,Math.min(Math.max(0,finalDamage),Math.max(0,targetHealth))*Math.max(0,percent)/100.0);
    }
}
