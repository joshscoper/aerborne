package net.nerdsmp.paper.webapi;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** One fixed-size address table per minute. Rejected traffic never grows a queue or unbounded address cache. */
final class ApiRateLimit {
    private static final int MAX_ADDRESSES = 1_024;
    private static final long WINDOW = TimeUnit.MINUTES.toNanos(1);
    private final int globalLimit;
    private final int addressLimit;
    private final LongSupplier clock;
    private final Map<String, Integer> addresses = new HashMap<>();
    private long start;
    private int total;

    ApiRateLimit(int globalLimit, int addressLimit, LongSupplier clock) {
        this.globalLimit = globalLimit;
        this.addressLimit = addressLimit;
        this.clock = clock;
        start = clock.getAsLong();
    }

    synchronized boolean acquire(String address) {
        long now = clock.getAsLong();
        if (now - start >= WINDOW) { start = now; total = 0; addresses.clear(); }
        total = Math.min(total + 1, globalLimit + 1);
        if (total > globalLimit) return false;
        Integer count = addresses.get(address);
        if (count == null && addresses.size() >= MAX_ADDRESSES) return false;
        int next = count == null ? 1 : count + 1;
        addresses.put(address, Math.min(next, addressLimit + 1));
        return next <= addressLimit;
    }

    synchronized int trackedAddresses() { return addresses.size(); }
}
