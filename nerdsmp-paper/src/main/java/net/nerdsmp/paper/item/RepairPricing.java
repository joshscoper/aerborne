package net.nerdsmp.paper.item;

public final class RepairPricing {
    private RepairPricing() {
    }

    public static long quote(
        int itemCount,
        long missingDurability,
        long baseCost,
        long perItemCost,
        double perDurabilityCost,
        long maximumCost
    ) {
        if (itemCount <= 0 || missingDurability <= 0L) {
            return 0L;
        }
        double raw = Math.max(0L, baseCost)
            + (double) itemCount * Math.max(0L, perItemCost)
            + missingDurability * Math.max(0.0D, perDurabilityCost);
        long rounded = raw >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(raw);
        return Math.max(1L, Math.min(Math.max(1L, maximumCost), rounded));
    }
}
