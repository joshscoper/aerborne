package net.nerdsmp.paper.npc;

import java.util.List;

/** Three tidy rows of seven, with separate navigation and trade controls. */
final class ShopLayout {
    static final int PAGE_SIZE = 21;
    private ShopLayout() { }
    static int slot(int index) {
        if (index < 0 || index >= PAGE_SIZE) throw new IllegalArgumentException("Invalid shop cell");
        return 19 + index / 7 * 9 + index % 7;
    }
    static <T> Page<T> page(List<T> entries, int requested) {
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int number = Math.clamp(requested, 0, pages - 1);
        int start = number * PAGE_SIZE;
        return new Page<>(List.copyOf(entries.subList(start, Math.min(entries.size(), start + PAGE_SIZE))), number, pages);
    }
    record Page<T>(List<T> entries, int number, int pages) { }
}
