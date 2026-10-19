package net.nerdsmp.paper.gui;

/** Shared 54-slot station geometry with separate result, content and navigation rows. */
public final class StationLayout {
    private StationLayout() { }
    public static final int PREVIEW = 4, BACK = 45, CLOSE = 53;
    public static int variant(int strength, int bottle) {
        if (strength < 0 || strength > 2 || bottle < 0 || bottle > 2) throw new IllegalArgumentException("Invalid brewing cell");
        return 20 + strength * 9 + bottle * 2;
    }
    public static int ingredient(int index) {
        if (index < 0 || index > 13) throw new IllegalArgumentException("Too many recipe ingredients");
        return 19 + index / 7 * 9 + index % 7;
    }
}
