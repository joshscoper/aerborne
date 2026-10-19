package net.nerdsmp.paper.market;

import java.util.*;
import java.util.function.BiPredicate;

/** Preflight one delivery without touching live inventory; empty slots are null. */
final class MarketInventoryPlan {
    private MarketInventoryPlan() { }
    record Stack<T>(T item, int amount, int maximum) {
        Stack {
            Objects.requireNonNull(item);
            if (amount < 1 || maximum < 1 || amount > maximum) throw new IllegalArgumentException("Invalid stack");
        }
    }

    static <T> Optional<List<Stack<T>>> insert(List<Stack<T>> original, Stack<T> item, BiPredicate<T, T> similar) {
        List<Stack<T>> slots = new ArrayList<>(original);
        int remaining = item.amount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int index = 0; index < slots.size() && remaining > 0; index++) {
                Stack<T> current = slots.get(index);
                if (pass == 0 && (current == null || !similar.test(current.item(), item.item()))
                    || pass == 1 && current != null) continue;
                int amount = current == null ? 0 : current.amount();
                int maximum = current == null ? item.maximum() : Math.min(current.maximum(), item.maximum());
                int moved = Math.min(remaining, Math.max(0, maximum - amount));
                if (moved == 0) continue;
                slots.set(index, new Stack<>(current == null ? item.item() : current.item(), amount + moved, maximum));
                remaining -= moved;
            }
        }
        return remaining == 0 ? Optional.of(Collections.unmodifiableList(slots)) : Optional.empty();
    }
}
