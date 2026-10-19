package net.nerdsmp.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Preflights both forge inputs together before switching to a repair-all quote. */
final class ForgeReturnCapacity {
    private ForgeReturnCapacity() { }

    static <T> boolean fits(List<Stack<T>> storage, List<Stack<T>> inputs, BiPredicate<T, T> similar) {
        List<Stack<T>> planned = new ArrayList<>(storage);
        for (Stack<T> input : inputs) {
            if (input == null || input.amount() <= 0) continue;
            int remaining = input.amount();
            for (int slot = 0; slot < planned.size() && remaining > 0; slot++) {
                Stack<T> current = planned.get(slot);
                if (current == null || !similar.test(current.item(), input.item())) continue;
                int room = Math.max(0, Math.min(input.maximum(), current.maximum()) - current.amount());
                int moved = Math.min(room, remaining);
                if (moved > 0) planned.set(slot, new Stack<>(current.item(), current.amount() + moved, current.maximum()));
                remaining -= moved;
            }
            for (int slot = 0; slot < planned.size() && remaining > 0; slot++) {
                if (planned.get(slot) != null) continue;
                int moved = Math.min(input.maximum(), remaining);
                if (moved <= 0) return false;
                planned.set(slot, new Stack<>(input.item(), moved, input.maximum()));
                remaining -= moved;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    record Stack<T>(T item, int amount, int maximum) { }
}
