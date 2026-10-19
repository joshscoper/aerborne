package net.nerdsmp.paper.forge;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/** Chooses a storage slot for one output without changing any input or inventory slot. */
final class ForgeOutputPlan {
    private ForgeOutputPlan() {}

    record Stack<T>(T item, int amount, int maximum) {}
    record Delivery(int slot, int amount) {}

    static <T> Optional<Delivery> place(List<Stack<T>> storage, T output, int maximum,
                                       BiPredicate<T, T> similar) {
        if (maximum < 1) return Optional.empty();
        for (int slot = 0; slot < storage.size(); slot++) {
            Stack<T> current = storage.get(slot);
            if (current != null && current.amount() > 0 && similar.test(current.item(), output)
                && current.amount() < Math.min(current.maximum(), maximum)) {
                return Optional.of(new Delivery(slot, current.amount() + 1));
            }
        }
        for (int slot = 0; slot < storage.size(); slot++) {
            if (storage.get(slot) == null) return Optional.of(new Delivery(slot, 1));
        }
        return Optional.empty();
    }
}
