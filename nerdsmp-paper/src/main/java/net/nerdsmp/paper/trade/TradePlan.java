package net.nerdsmp.paper.trade;

import java.util.*;
import java.util.function.BiPredicate;

/** Plans the entire storage exchange without moving real items. */
final class TradePlan {
    record Offer<T>(int slot, TradeInventoryCapacity.Stack<T> snapshot, int amount) { }
    static <T> List<TradeInventoryCapacity.Stack<T>> exchange(List<TradeInventoryCapacity.Stack<T>> inventory,
        List<Offer<T>> outgoing, List<TradeInventoryCapacity.Stack<T>> incoming, BiPredicate<T, T> similar) {
        var result = new ArrayList<>(inventory); Set<Integer> used = new HashSet<>();
        for (var offer : outgoing) {
            if (offer.slot < 0 || offer.slot >= result.size() || !used.add(offer.slot)) throw new IllegalArgumentException("Duplicate or invalid offer slot");
            var actual = result.get(offer.slot); var expected = offer.snapshot;
            if (actual == null || expected == null || offer.amount < 1 || offer.amount > actual.amount()
                || actual.amount() != expected.amount() || !similar.test(actual.item(), expected.item())) throw new IllegalArgumentException("An offered item changed. Review the trade again.");
            result.set(offer.slot, actual.amount() == offer.amount ? null : new TradeInventoryCapacity.Stack<>(actual.item(), actual.amount() - offer.amount, actual.maximum()));
        }
        for (var item : incoming) {
            if (item.amount() < 1 || item.amount() > item.maximum()) throw new IllegalArgumentException("Invalid offered stack");
            int remaining = item.amount();
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < result.size() && remaining > 0; i++) {
                var stored = result.get(i);
                if (pass == 0 && (stored == null || !similar.test(stored.item(), item.item())) || pass == 1 && stored != null) continue;
                int count = stored == null ? 0 : stored.amount(), cap = stored == null ? item.maximum() : Math.min(stored.maximum(), item.maximum());
                int moved = Math.min(remaining, Math.max(0, cap - count));
                if (moved > 0) { result.set(i, new TradeInventoryCapacity.Stack<>(item.item(), count + moved, cap)); remaining -= moved; }
            }
            if (remaining > 0) throw new IllegalArgumentException("Make room for every item before confirming.");
        }
        return Collections.unmodifiableList(result);
    }
}
