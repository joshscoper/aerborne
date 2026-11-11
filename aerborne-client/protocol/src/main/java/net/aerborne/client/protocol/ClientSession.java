package net.aerborne.client.protocol;

/** Client-thread-only capability lease; time is supplied for deterministic expiry tests. */
public final class ClientSession {
    public static final long LEASE_NANOS = 15_000_000_000L;
    private boolean menu;
    private long receivedAt;

    public void receive(Capabilities capabilities, long now) {
        menu = capabilities.supportsMenu();
        receivedAt = now;
    }

    public boolean menuAvailable(long now) {
        return menu && now - receivedAt >= 0 && now - receivedAt < LEASE_NANOS;
    }

    public void reset() { menu = false; }
}
