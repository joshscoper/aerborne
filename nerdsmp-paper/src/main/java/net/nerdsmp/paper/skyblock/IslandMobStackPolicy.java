package net.nerdsmp.paper.skyblock;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Only mobs whose ordinary members are interchangeable may share an island body. */
public final class IslandMobStackPolicy {
    public static final int MAX_STACK = 16;
    public static final int MAX_CHUNK_MEMBERS = 256;
    public static final int MAX_PENDING = 1_024;
    public static final int WORK_PER_PASS = 32;
    public static final int ENTITIES_PER_CHUNK = 512;
    public static final int MAX_CHUNKS_PER_PASS = 8;
    public static final int NEARBY_CHECKS_PER_PASS = 1_024;
    public static final int RECONCILE_ENTITIES_PER_PASS = 64;
    public static final int MAX_TRACKED_CHUNKS = 32_768;
    public static final int MAX_SPAWN_BUDGET_CHUNKS = 256;
    public static final long SOFT_PASS_NANOS = 2_000_000L;
    public static final double RANGE_SQUARED = 36.0D;
    private static final Set<String> TYPES = Set.of("ZOMBIE", "HUSK", "DROWNED", "SKELETON", "STRAY",
        "SPIDER", "CAVE_SPIDER", "BLAZE", "CREEPER", "ENDERMAN", "ZOMBIFIED_PIGLIN", "WITCH", "BREEZE");

    private IslandMobStackPolicy() { }

    public static boolean supported(String type) { return TYPES.contains(type); }
    public static boolean ambient(String reason) { return "NATURAL".equals(reason) || "CHUNK_GEN".equals(reason); }
    public static int members(int raw) { return Math.clamp(raw, 1, MAX_STACK); }

    public static boolean room(int existing, int incoming, int chunkMembers) {
        return existing >= 1 && incoming >= 1 && existing <= MAX_STACK && incoming <= MAX_STACK
            && existing + incoming <= MAX_STACK && chunkMembers >= 0
            && chunkMembers <= MAX_CHUNK_MEMBERS - incoming;
    }

    /** A same-chunk merge removes a body; it never increases that chunk's represented population. */
    public static boolean mergeRoom(int existing, int incoming, int destinationMembers, boolean sameChunk) {
        return existing >= 1 && incoming >= 1 && existing <= MAX_STACK && incoming <= MAX_STACK
            && existing + incoming <= MAX_STACK && destinationMembers >= 0
            && (sameChunk || destinationMembers <= MAX_CHUNK_MEMBERS - incoming);
    }

    public static boolean equivalent(UUID source, UUID destination, Signature first, Signature second, double distanceSquared) {
        return source != null && source.equals(destination) && first != null && first.equals(second)
            && Double.isFinite(distanceSquared) && distanceSquared >= 0.0D && distanceSquared <= RANGE_SQUARED;
    }

    public record Signature(String type, int level, Map<String, Double> attributes, Map<String, Object> scale,
                            String equipment) {
        public Signature {
            if (!supported(type) || level < 1 || level > 100) throw new IllegalArgumentException("Unsafe stack member");
            attributes = Map.copyOf(attributes);
            scale = Map.copyOf(scale);
            Objects.requireNonNull(equipment);
            if (attributes.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0.0D))
                throw new IllegalArgumentException("Invalid stack attributes");
        }
    }
}
