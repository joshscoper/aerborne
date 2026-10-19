package net.nerdsmp.paper.zone;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable staff-authored region. Bounds use inclusive block coordinates, points use exact feet positions. */
public record ZoneDefinition(String id, String title, UUID worldId, Bounds bounds,
                             boolean enabled, boolean safe, int cap, int respawnSeconds,
                             int activationRadius, List<SpawnPoint> points, List<MobEntry> mobs) {
    public static final int MAX_ZONES = 128;
    public static final int MAX_POINTS = 128;
    public static final int MAX_MOBS = 32;

    public ZoneDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}"))
            throw new IllegalArgumentException("Zone IDs use 1–48 lowercase letters, numbers, underscores or dashes.");
        if (title == null || title.isBlank() || !title.equals(title.strip()) || title.codePointCount(0, title.length()) > 64
            || title.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7))
            throw new IllegalArgumentException("Zone titles must be 1–64 plain-text characters.");
        Objects.requireNonNull(worldId, "Zone world UUID is required");
        Objects.requireNonNull(bounds, "Zone bounds are required");
        if (cap < 1 || cap > 64) throw new IllegalArgumentException("Mob cap must be 1–64.");
        if (respawnSeconds < 5 || respawnSeconds > 3600) throw new IllegalArgumentException("Respawn delay must be 5–3,600 seconds.");
        if (activationRadius < 8 || activationRadius > 128) throw new IllegalArgumentException("Activation radius must be 8–128 blocks.");
        points = List.copyOf(Objects.requireNonNull(points, "Zone points are required"));
        mobs = List.copyOf(Objects.requireNonNull(mobs, "Zone mobs are required"));
        if (points.size() > MAX_POINTS) throw new IllegalArgumentException("A zone supports at most 128 spawn points.");
        if (mobs.size() > MAX_MOBS) throw new IllegalArgumentException("A zone supports at most 32 mob entries.");
        if (new HashSet<>(points.stream().map(p -> List.of(p.x(), p.y(), p.z())).toList()).size() != points.size())
            throw new IllegalArgumentException("Spawn points cannot occupy the same position.");
        if (new HashSet<>(mobs.stream().map(MobEntry::mobId).toList()).size() != mobs.size())
            throw new IllegalArgumentException("Each mob ID may occur only once in a zone.");
        if (points.stream().anyMatch(p -> !bounds.contains(p.x(), p.y(), p.z())))
            throw new IllegalArgumentException("Every spawn point must be inside its zone.");
        if (enabled && !safe && (points.isEmpty() || mobs.isEmpty()))
            throw new IllegalArgumentException("Add at least one spawn point and mob before enabling this zone.");
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX < -29_999_984 || maxX > 29_999_984 || minZ < -29_999_984 || maxZ > 29_999_984
                || minY < -64 || maxY > 319 || minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("Select ordered bounds within the world limits (Y -64 to 319).");
            long width = (long) maxX - minX + 1, height = (long) maxY - minY + 1, depth = (long) maxZ - minZ + 1;
            if (width > 2048 || depth > 2048 || width * height * depth > 16_777_216L)
                throw new IllegalArgumentException("Zones are limited to 2,048 blocks per side and 16,777,216 blocks in volume.");
        }
        public boolean contains(double x, double y, double z) {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x >= minX && x < (double) maxX + 1 && y >= minY && y < (double) maxY + 1 && z >= minZ && z < (double) maxZ + 1;
        }
        public boolean overlaps(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }

    public record SpawnPoint(double x, double y, double z, float yaw) {
        public SpawnPoint {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)
                || x < -29_999_984 || x >= 29_999_985 || z < -29_999_984 || z >= 29_999_985 || y < -64 || y >= 320)
                throw new IllegalArgumentException("Spawn points must be finite positions within the world limits.");
            yaw = ((yaw % 360) + 540) % 360 - 180;
            // Canonical zero prevents identical positions being added as positive and negative zero.
            if (x == 0) x = 0;
            if (y == 0) y = 0;
            if (z == 0) z = 0;
        }
    }

    public record MobEntry(String mobId, int weight, int level) {
        public MobEntry {
            if (!ZoneMobTypes.validId(mobId)) throw new IllegalArgumentException("Use an allowed vanilla:<type> or custom:<definition_id> mob ID.");
            if (weight < 1 || weight > 10_000) throw new IllegalArgumentException("Mob weights must be 1–10,000.");
            if (level < 1 || level > 100) throw new IllegalArgumentException("Mob levels must be 1–100.");
        }
    }
}
