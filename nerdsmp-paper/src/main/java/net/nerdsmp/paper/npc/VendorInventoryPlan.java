package net.nerdsmp.paper.npc;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** All-or-nothing storage planning. The adapter supplies exact item identity and detached copies. */
final class VendorInventoryPlan {
    interface Items<T> {
        boolean empty(T item);
        boolean same(T left, T right);
        int amount(T item);
        int maximum(T item);
        T copy(T item, int amount);
    }

    private VendorInventoryPlan() { }

    static <T> List<T> plan(List<T> contents, T product, int units, boolean buy, Items<T> items) {
        Objects.requireNonNull(contents); Objects.requireNonNull(items);
        if (items.empty(product) || units < 1 || units > 64 || units > items.maximum(product)) {
            throw new IllegalArgumentException("Invalid vendor item quantity");
        }
        List<T> next = new ArrayList<>(contents.size());
        for (T item : contents) {
            if (items.empty(item)) next.add(null);
            else {
                if (items.amount(item) < 1 || items.amount(item) > items.maximum(item)) return null;
                next.add(items.copy(item, items.amount(item)));
            }
        }
        int remaining = units;
        if (!buy) {
            for (int n = 0; n < next.size() && remaining > 0; n++) {
                T item = next.get(n);
                if (item == null || !items.same(item, product)) continue;
                int moved = Math.min(remaining, items.amount(item));
                remaining -= moved;
                next.set(n, moved == items.amount(item) ? null : items.copy(item, items.amount(item) - moved));
            }
        } else {
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int n = 0; n < next.size() && remaining > 0; n++) {
                    T item = next.get(n);
                    if (pass == 0 && (item == null || !items.same(item, product)) || pass == 1 && item != null) continue;
                    int amount = item == null ? 0 : items.amount(item);
                    int maximum = item == null ? items.maximum(product) : Math.min(items.maximum(item), items.maximum(product));
                    int moved = Math.min(remaining, Math.max(0, maximum - amount));
                    if (moved < 1) continue;
                    next.set(n, items.copy(item == null ? product : item, amount + moved));
                    remaining -= moved;
                }
            }
        }
        return remaining == 0 ? next : null;
    }
}
