package net.nerdsmp.paper.gui;

/** The socket row stays independent from movable inputs, review and footer controls. */
final class ForgeSocketLayout {
    static final int SIZE = 54, PAGE_SIZE = 7;
    private ForgeSocketLayout() { }
    static int page(int requested, int capacity) {
        return Math.clamp(requested, 0, Math.max(0, (Math.max(0, capacity) - 1) / PAGE_SIZE));
    }
    static int slot(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= PAGE_SIZE) throw new IllegalArgumentException("Invalid socket cell");
        return 28 + visibleIndex;
    }
    static int selected(int requested, int used) { return used < 1 ? -1 : Math.clamp(requested, 0, used - 1); }
}
