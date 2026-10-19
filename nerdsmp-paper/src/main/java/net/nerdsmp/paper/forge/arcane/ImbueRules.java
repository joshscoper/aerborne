package net.nerdsmp.paper.forge.arcane;

/**
 * Chosen Imbuement: pick a discovered enchantment and an exact level. The price is fixed in advance
 * and nothing is random. Table offers and tomes are unchanged and remain the cheaper, random route.
 */
public final class ImbueRules {
    private ImbueRules() {
    }

    public enum Status {
        READY,
        /** The player has not discovered this enchantment yet. */
        UNDISCOVERED,
        /** The item cannot hold this enchantment. */
        INCOMPATIBLE,
        /** The target is not above the item's current level, or above the enchantment's maximum. */
        NO_UPGRADE,
        /** The player's Enchanting level cannot reach this level yet. */
        NEEDS_LEVEL
    }

    public record Quote(Status status, int fromLevel, int toLevel, int dust, int requiredLevel,
                        int highestAllowedLevel, boolean discounted) {
        public boolean ready() {
            return status == Status.READY;
        }
    }

    /** The highest level this Enchanting level may imbue, from {@code imbuement.level-requirements}. */
    public static int highestLevel(ArcaneSettings.Imbuement rules, int arcane, int maximumLevel) {
        int allowed = 0;
        for (int level = 1; level <= Math.min(maximumLevel, rules.levelRequirements().size()); level++) {
            if (arcane >= rules.levelRequirements().get(level - 1)) allowed = level;
        }
        return allowed;
    }

    public static int requiredLevel(ArcaneSettings.Imbuement rules, int level) {
        return rules.levelRequirements().get(Math.max(1, Math.min(rules.levelRequirements().size(), level)) - 1);
    }

    /**
     * Dust for raising an enchantment from {@code from} to {@code to}: the per-level price times each
     * level gained (level 3 costs three times level 1). Mastery takes a percentage off, rounded up.
     */
    public static int dust(ArcaneSettings.Imbuement rules, ArcaneTier tier, int from, int to, boolean mastered) {
        int perLevel = rules.dustPerLevel().getOrDefault(tier, 0);
        long total = 0;
        for (int level = Math.max(0, from) + 1; level <= to; level++) total += (long) perLevel * level;
        if (mastered) total = (long) Math.ceil(total * (100 - rules.masteryDiscountPercent()) / 100.0D);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static Quote quote(ArcaneSettings.Imbuement rules, ArcaneDefinition definition, boolean discovered,
                              boolean mastered, boolean compatible, int currentLevel, int targetLevel, int arcane) {
        int highest = highestLevel(rules, arcane, definition.maximumLevel());
        int required = requiredLevel(rules, targetLevel);
        int dust = dust(rules, definition.tier(), currentLevel, targetLevel, mastered);
        Status status;
        if (definition.isSignature()) throw new IllegalArgumentException("Signatures are bound, not levelled");
        else if (!discovered) status = Status.UNDISCOVERED;
        else if (!compatible) status = Status.INCOMPATIBLE;
        else if (targetLevel <= currentLevel || targetLevel > definition.maximumLevel()) status = Status.NO_UPGRADE;
        else if (arcane < required) status = Status.NEEDS_LEVEL;
        else status = Status.READY;
        return new Quote(status, currentLevel, targetLevel, dust, required, highest, mastered);
    }
}
