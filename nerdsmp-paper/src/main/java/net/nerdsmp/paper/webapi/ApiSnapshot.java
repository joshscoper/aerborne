package net.nerdsmp.paper.webapi;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Only immutable, bounded JSON crosses from the game thread into HTTP event loops. */
public record ApiSnapshot(long sampledAtMillis, String status, String telemetry, String catalog,
                          String leaderboards, Map<String, String> players) {
    static final int MAX_RESPONSE_BYTES = 32_768;
    static final int MAX_PROFILE_BYTES = 4_096;
    static final int MAX_PROFILES = 20_000;
    static final String NO_LEADERBOARDS = "{\"apiVersion\":1,\"ready\":false,\"boards\":{}}";

    public ApiSnapshot(long sampledAtMillis, String status, String telemetry, String catalog) {
        this(sampledAtMillis, status, telemetry, catalog, NO_LEADERBOARDS, Map.of());
    }

    public ApiSnapshot {
        if (sampledAtMillis < 0) throw new IllegalArgumentException("Invalid snapshot timestamp");
        players = Map.copyOf(players);
        if (players.size() > MAX_PROFILES) throw new IllegalArgumentException("Web API snapshot has too many profiles");
        for (String value : players.values()) {
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_PROFILE_BYTES) {
                throw new IllegalArgumentException("Web API profile exceeded its size budget");
            }
        }
        for (String value : new String[]{status, telemetry, catalog, leaderboards}) {
            if (Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new IllegalArgumentException("Web API snapshot exceeded its size budget");
            }
        }
    }

    boolean stale(long now, int intervalSeconds) {
        return now < sampledAtMillis || now - sampledAtMillis > Math.max(15_000L, intervalSeconds * 3_000L);
    }
}
