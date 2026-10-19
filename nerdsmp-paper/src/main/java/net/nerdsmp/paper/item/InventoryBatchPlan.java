package net.nerdsmp.paper.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One-shot, exact-snapshot replacement. Callers own defensive copies of mutable items. */
final class InventoryBatchPlan<T> {
    private final List<T> before;
    private final List<T> after;
    private boolean attempted;

    InventoryBatchPlan(List<T> before, List<T> after) {
        if (before.isEmpty() || before.size() > 36 || before.size() != after.size() || before.equals(after))
            throw new IllegalArgumentException("Invalid inventory batch");
        this.before = Collections.unmodifiableList(new ArrayList<>(before));
        this.after = Collections.unmodifiableList(new ArrayList<>(after));
    }

    boolean matches(List<T> current) { return before.equals(current); }

    Result apply(Supplier<List<T>> current, Consumer<List<T>> replace) {
        if (attempted) return Result.ALREADY_ATTEMPTED;
        try {
            if (!matches(Objects.requireNonNull(current.get()))) return Result.INPUT_CHANGED;
        } catch (RuntimeException failure) {
            return Result.UNAVAILABLE;
        }
        // No automatic retry/refund once replacement might have begun.
        attempted = true;
        try {
            replace.accept(after);
            return Result.APPLIED;
        } catch (RuntimeException failure) {
            return Result.REVIEW_REQUIRED;
        }
    }

    enum Result { APPLIED, INPUT_CHANGED, UNAVAILABLE, ALREADY_ATTEMPTED, REVIEW_REQUIRED }
}
