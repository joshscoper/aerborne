package net.nerdsmp.paper.forge.arcane;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Operational counters for this server run. Fixed, low-cardinality metrics: no player names or ids,
 * so they are safe to show staff and to log.
 */
public final class ArcaneTelemetry {
    public enum Metric {
        IMBUE_STARTED, IMBUE_COMPLETED, IMBUE_REJECTED, IMBUE_FAILED,
        RESEARCH_STARTED, RESEARCH_COMPLETED, RESEARCH_REJECTED, RESEARCH_FAILED,
        DISCOVERIES, CLUES, SIGNATURES_BOUND, STABILIZED, CORRUPTED, CLEANSED, ATTUNED,
        PROFILE_SAVE_FAILURES, ROLLBACKS, DUPLICATE_RECEIPTS,
        STORMWAKE_RELEASES, STORMWAKE_STRIKES, STORMWAKE_BLOCKED,
        MOUNTAIN_RELEASES, MOUNTAIN_BLOCKED,
        BASTION_SAVES, BASTION_COOLDOWN,
        SLOW_EFFECTS, GUI_ERRORS, INVALID_ITEM_DATA, CONFIG_REJECTED
    }

    private final Map<Metric, LongAdder> counters = new EnumMap<>(Metric.class);

    public ArcaneTelemetry() {
        for (Metric metric : Metric.values()) counters.put(metric, new LongAdder());
    }

    public void increment(Metric metric) {
        counters.get(metric).increment();
    }

    public void add(Metric metric, long amount) {
        counters.get(metric).add(amount);
    }

    public long get(Metric metric) {
        return counters.get(metric).sum();
    }

    public Map<Metric, Long> snapshot() {
        Map<Metric, Long> snapshot = new EnumMap<>(Metric.class);
        counters.forEach((metric, counter) -> snapshot.put(metric, counter.sum()));
        return snapshot;
    }
}
