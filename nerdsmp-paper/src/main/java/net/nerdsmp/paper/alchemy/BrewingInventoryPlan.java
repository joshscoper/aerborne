package net.nerdsmp.paper.alchemy;

import java.util.*;

/** Pure debit-and-deliver plan. A failed ingredient/capacity check never mutates the input snapshot. */
final class BrewingInventoryPlan {
    record Stack<T>(T kind, int amount, int maximum) {
        Stack {
            Objects.requireNonNull(kind);
            if (amount < 1 || maximum < 1 || maximum > 99 || amount > maximum) throw new IllegalArgumentException("Invalid stack");
        }
        Stack<T> withAmount(int value) { return new Stack<>(kind, value, maximum); }
    }
    private BrewingInventoryPlan() { }
    static <T> Optional<List<Stack<T>>> prepare(List<Stack<T>> inventory, Map<T, Integer> ingredients, List<Stack<T>> outputs) {
        List<Stack<T>> next = new ArrayList<>(inventory);
        for (var entry : ingredients.entrySet()) {
            Objects.requireNonNull(entry.getKey());
            int needed = entry.getValue();
            if (needed < 1) throw new IllegalArgumentException("Ingredient amount must be positive");
            for (int slot = 0; slot < next.size() && needed > 0; slot++) {
                Stack<T> stack = next.get(slot); if (stack == null || !stack.kind().equals(entry.getKey())) continue;
                int amount = Math.min(needed, stack.amount()); needed -= amount;
                next.set(slot, amount == stack.amount() ? null : stack.withAmount(stack.amount() - amount));
            }
            if (needed > 0) return Optional.empty();
        }
        for (Stack<T> output : outputs) {
            int remaining = output.amount();
            for (int slot = 0; slot < next.size() && remaining > 0; slot++) {
                Stack<T> stack = next.get(slot);
                if (stack == null || !stack.kind().equals(output.kind())) continue;
                int amount = Math.min(remaining, Math.max(0, Math.min(stack.maximum(), output.maximum()) - stack.amount()));
                if (amount > 0) { next.set(slot, stack.withAmount(stack.amount() + amount)); remaining -= amount; }
            }
            for (int slot = 0; slot < next.size() && remaining > 0; slot++) {
                if (next.get(slot) != null) continue;
                int amount = Math.min(remaining, output.maximum()); next.set(slot, output.withAmount(amount)); remaining -= amount;
            }
            if (remaining > 0) return Optional.empty();
        }
        return Optional.of(Collections.unmodifiableList(next));
    }
}
