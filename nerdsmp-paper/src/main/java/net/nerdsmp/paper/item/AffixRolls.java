package net.nerdsmp.paper.item;

public final class AffixRolls {
    private AffixRolls() {
    }

    public static int count(double roll, int minimum, int maximum) {
        int low = Math.max(0, Math.min(3, minimum));
        int high = Math.max(low, Math.min(3, maximum));
        if (low == high) {
            return low;
        }
        double bounded = Math.max(0.0D, Math.min(0.999999D, roll));
        return low + Math.min(high - low, (int) Math.floor(bounded * (high - low + 1)));
    }
}
