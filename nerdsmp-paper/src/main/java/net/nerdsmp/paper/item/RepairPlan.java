package net.nerdsmp.paper.item;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/** An exact-slot replacement plan; only a known pre-mutation failure is refundable. */
final class RepairPlan<T> {
    private final List<Entry<T>> entries;
    private boolean attempted;

    RepairPlan(List<Entry<T>> entries) {
        this.entries = List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException("A repair plan needs an item");
        if (entries.stream().map(Entry::slot).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("Repair slots must be unique");
        }
    }

    int size() { return entries.size(); }

    boolean matches(IntFunction<T> current) {
        return entries.stream().allMatch(entry -> Objects.equals(entry.before(), current.apply(entry.slot())));
    }

    ApplyResult apply(IntFunction<T> current, BiConsumer<Integer, T> replace) {
        if (attempted) return ApplyResult.ALREADY_ATTEMPTED;
        try {
            if (!matches(current)) return ApplyResult.INPUT_CHANGED;
        } catch (RuntimeException failure) {
            return ApplyResult.UNAVAILABLE;
        }
        // From this point onward, a write may have happened. Never retry or refund
        // an ambiguous application: staff must inspect the original transaction.
        attempted = true;
        try {
            entries.forEach(entry -> replace.accept(entry.slot(), entry.after()));
            return ApplyResult.APPLIED;
        } catch (RuntimeException failure) {
            return ApplyResult.REVIEW_REQUIRED;
        }
    }

    record Entry<T>(int slot, T before, T after) {
        Entry {
            if (slot < 0) throw new IllegalArgumentException("Negative repair slot");
            Objects.requireNonNull(before);
            Objects.requireNonNull(after);
        }
    }

    enum ApplyResult {
        APPLIED,
        INPUT_CHANGED,
        UNAVAILABLE,
        ALREADY_ATTEMPTED,
        REVIEW_REQUIRED
    }
}
