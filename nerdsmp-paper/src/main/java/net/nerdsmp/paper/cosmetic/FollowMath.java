package net.nerdsmp.paper.cosmetic;

/**
 * Where a companion should be next. Walking companions trot behind the owner's shoulder; flying ones hover
 * beside it with a gentle bob. Movement eases toward the target so the companion trails naturally, and it
 * jumps straight to the owner when it falls far behind (after a teleport, for example).
 */
public final class FollowMath {
    /** Fraction of the remaining distance covered each step (every two ticks). */
    static final double EASE = 0.35D;
    /** Further than this and the companion reappears at the owner instead of chasing. */
    static final double SNAP_DISTANCE = 12.0D;
    /** Closer than this and a walking companion stays put rather than fidgeting. */
    static final double SETTLE_DISTANCE = 0.25D;

    public record Point(double x, double y, double z, float yaw) {}

    private FollowMath() {}

    /** The spot behind and to one side of the owner, given the owner's position and facing (degrees). */
    public static Point target(double x, double y, double z, float yaw, boolean flying, long tick) {
        double radians = Math.toRadians(yaw);
        // Minecraft yaw: 0 faces +Z, 90 faces -X.
        double forwardX = -Math.sin(radians), forwardZ = Math.cos(radians);
        double rightX = -forwardZ, rightZ = forwardX;
        double back = flying ? 0.6D : 1.4D, side = flying ? 0.9D : 0.7D;
        double tx = x - forwardX * back + rightX * side;
        double tz = z - forwardZ * back + rightZ * side;
        double ty = flying ? y + 1.6D + Math.sin(tick / 10.0D) * 0.15D : y;
        return new Point(tx, ty, tz, yaw);
    }

    /** One easing step from {@code current} toward {@code target}, facing the direction of travel. */
    public static Point step(Point current, Point target, boolean flying) {
        double dx = target.x() - current.x(), dy = target.y() - current.y(), dz = target.z() - current.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > SNAP_DISTANCE) return target;
        if (!flying && distance < SETTLE_DISTANCE) return new Point(current.x(), target.y(), current.z(), target.yaw());
        double nx = current.x() + dx * EASE, ny = current.y() + dy * EASE, nz = current.z() + dz * EASE;
        float yaw = Math.abs(dx) + Math.abs(dz) > 0.05D ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : target.yaw();
        return new Point(nx, ny, nz, yaw);
    }

    public static boolean snapped(Point current, Point target) {
        double dx = target.x() - current.x(), dy = target.y() - current.y(), dz = target.z() - current.z();
        return dx * dx + dy * dy + dz * dz > SNAP_DISTANCE * SNAP_DISTANCE;
    }
}
