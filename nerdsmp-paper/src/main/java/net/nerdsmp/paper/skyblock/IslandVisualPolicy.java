package net.nerdsmp.paper.skyblock;

/** Fixed budgets and animation math, independent of entities and schedulers. */
public final class IslandVisualPolicy {
    public static final int REVEAL_TICKS = 60;
    private IslandVisualPolicy() { }
    public static int anchors(int configured) { return Math.clamp(configured, 1, 64); }
    public static int range(int configured) { return Math.clamp(configured, 8, 32); }
    public static int opacity(int age) {
        if (age < 0 || age >= REVEAL_TICKS) return 0;
        double fade = Math.min(1.0, Math.min(age / 10.0, (REVEAL_TICKS - age) / 20.0));
        return (int) Math.round(255 * fade);
    }
    public static float scale(int age) { return .7f + .3f * Math.min(1f, Math.max(0, age) / 10f); }
}
