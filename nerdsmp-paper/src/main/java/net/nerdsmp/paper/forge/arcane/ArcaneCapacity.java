package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;

import java.util.Locale;
import java.util.Map;

/**
 * Arcane Capacity: how much enchantment an item holds. It is derived from the item's rarity, item
 * level and workmanship quality every time it is needed, never stored, so there is nothing to
 * migrate and no stored value to fall out of date.
 *
 * <p>Capacity is only <em>enforced</em> by new Forge 2.0 actions (binding an Arcane Signature,
 * overcharging). Table offers and tomes ignore it, and an existing item whose load is above its
 * capacity is <em>grandfathered</em>: everything on it keeps working, and nothing is ever removed
 * to make it fit.
 */
public final class ArcaneCapacity {
    private ArcaneCapacity() {
    }

    public static int capacity(ArcaneSettings.Capacity rules, String rarity, int itemLevel, int qualityTier) {
        int base = rarity == null ? rules.fallback()
            : rules.byRarity().getOrDefault(rarity.toUpperCase(Locale.ROOT), rules.fallback());
        return Math.max(0, base + Math.max(0, qualityTier) * rules.perQualityTier()
            + Math.max(0, itemLevel) / rules.itemLevelDivisor());
    }

    public static int cost(ArcaneSettings.Capacity rules, ArcaneDefinition definition, int level) {
        return switch (definition.tier()) {
            case FUNDAMENTAL -> rules.fundamentalPerLevel() * Math.max(0, level);
            case SPECIALIZED -> rules.specializedPerLevel() * Math.max(0, level);
            case SIGNATURE -> rules.signatureCost();
        };
    }

    /** Load from the enchantments already on an item, plus its signature if it has one. */
    public static int load(ArcaneSettings.Capacity rules, ArcaneRegistry registry,
                           Map<ArcaneEnchantment, Integer> enchantments, ArcaneSignature signature) {
        int load = 0;
        for (var entry : enchantments.entrySet()) load += cost(rules, registry.of(entry.getKey()), entry.getValue());
        if (signature != null) load += cost(rules, registry.of(signature), 1);
        return load;
    }

    public enum Fit {
        /** Fits within capacity. */
        FITS,
        /** Over capacity, but within the allowed overcharge. */
        OVERCHARGE,
        /** Over capacity and overcharge is off, too small, or not unlocked. */
        OVER_CAPACITY
    }

    public record Evaluation(Fit fit, int capacity, int loadBefore, int loadAfter, int overcharge, int overchargeLimit) {
        public int remainingBefore() {
            return capacity - loadBefore;
        }

        /** Capacity the item would need to fit without any overcharge. */
        public int shortfall() {
            return Math.max(0, loadAfter - capacity);
        }

        public boolean allowed() {
            return fit != Fit.OVER_CAPACITY;
        }
    }

    /**
     * Evaluates replacing {@code removedCost} of load with {@code addedCost}.
     *
     * <ul>
     *   <li>A change that does not raise the load always fits, so grandfathered items can always be
     *       reworked downward.</li>
     *   <li>A change that raises the load must end within capacity, or within capacity plus the
     *       overcharge limit when overcharge is allowed. The overcharge is the whole excess.</li>
     * </ul>
     */
    public static Evaluation evaluate(int capacity, int loadBefore, int removedCost, int addedCost,
                                      boolean overchargeAllowed, int overchargePercent) {
        int loadAfter = Math.max(0, loadBefore - removedCost) + addedCost;
        int limit = overchargeAllowed ? capacity * Math.max(0, overchargePercent) / 100 : 0;
        if (addedCost <= removedCost || loadAfter <= capacity) {
            return new Evaluation(Fit.FITS, capacity, loadBefore, loadAfter, 0, limit);
        }
        int excess = loadAfter - capacity;
        if (overchargeAllowed && excess <= limit) {
            return new Evaluation(Fit.OVERCHARGE, capacity, loadBefore, loadAfter, excess, limit);
        }
        return new Evaluation(Fit.OVER_CAPACITY, capacity, loadBefore, loadAfter, excess, limit);
    }

    /** True when an item already holds more than its capacity: grandfathered, never stripped. */
    public static boolean grandfathered(int capacity, int load) {
        return load > capacity;
    }
}
