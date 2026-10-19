package net.nerdsmp.paper.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Simulates a whole offer without mutating either player's inventory. */
final class TradeInventoryCapacity {
    private TradeInventoryCapacity() {
    }

    record Stack<T>(T item, int amount, int maximum) {
    }

    static <T> boolean fits(List<Stack<T>> inventory, List<Stack<T>> incoming, BiPredicate<T, T> similar) {
        List<Stack<T>> slots = new ArrayList<>(inventory);
        for (Stack<T> offered : incoming) {
            int remaining = offered.amount();
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int index = 0; index < slots.size() && remaining > 0; index++) {
                    Stack<T> current = slots.get(index);
                    boolean empty = current == null;
                    if ((pass == 0 && (empty || !similar.test(current.item(), offered.item())))
                        || (pass == 1 && !empty)) {
                        continue;
                    }
                    int stored = empty ? 0 : current.amount();
                    int moved = Math.min(remaining, Math.max(0, offered.maximum() - stored));
                    if (moved > 0) {
                        slots.set(index, new Stack<>(offered.item(), stored + moved, offered.maximum()));
                        remaining -= moved;
                    }
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }
}
