package net.nerdsmp.paper.anticheat.physics;

/** Geometry for reach and hitbox checks. Pure functions, no Bukkit types, so they are unit tested directly. */
public final class HitboxMath {
    private HitboxMath() {
    }

    /** Axis-aligned box. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box expand(double amount) {
            return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
        }

        public double centerX() { return (minX + maxX) / 2.0D; }
        public double centerY() { return (minY + maxY) / 2.0D; }
        public double centerZ() { return (minZ + maxZ) / 2.0D; }
    }

    /** Unit look vector for a Minecraft yaw/pitch in degrees. */
    public static double[] direction(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    /** Distance along the ray at which it enters the box, or -1 if it misses. Starting inside the box returns 0. */
    public static double rayHit(double ox, double oy, double oz, double[] dir, Box box) {
        double near = 0.0D;
        double far = Double.MAX_VALUE;
        double[] origin = {ox, oy, oz};
        double[] min = {box.minX(), box.minY(), box.minZ()};
        double[] max = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0E-9D) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) return -1.0D;
                continue;
            }
            double t1 = (min[axis] - origin[axis]) / dir[axis];
            double t2 = (max[axis] - origin[axis]) / dir[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return -1.0D;
        }
        return near;
    }

    /** Shortest distance from a point to the box (0 inside). */
    public static double distance(double x, double y, double z, Box box) {
        double dx = Math.max(Math.max(box.minX() - x, 0.0D), x - box.maxX());
        double dy = Math.max(Math.max(box.minY() - y, 0.0D), y - box.maxY());
        double dz = Math.max(Math.max(box.minZ() - z, 0.0D), z - box.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Angle in degrees between the look direction and the line to the box centre. */
    public static double offsetToCenter(double ox, double oy, double oz, double[] dir, Box box) {
        double tx = box.centerX() - ox;
        double ty = box.centerY() - oy;
        double tz = box.centerZ() - oz;
        double length = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (length < 1.0E-9D) return 0.0D;
        double dot = (tx * dir[0] + ty * dir[1] + tz * dir[2]) / length;
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
    }

    /** Linear blend of two angles in degrees, taking the short way round for yaw. */
    public static float lerpAngle(float from, float to, double t) {
        float delta = wrap(to - from);
        return (float) (from + delta * t);
    }

    public static float wrap(float degrees) {
        float value = degrees % 360.0F;
        if (value >= 180.0F) value -= 360.0F;
        if (value < -180.0F) value += 360.0F;
        return value;
    }
}
