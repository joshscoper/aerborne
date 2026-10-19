package net.nerdsmp.paper.anticheat;

public final class WatchdogMath {
    private WatchdogMath() {
    }

    public static double decay(double score, long elapsedMillis, double pointsPerSecond) {
        return Math.max(0.0D, score - Math.max(0L, elapsedMillis) / 1000.0D * Math.max(0.0D, pointsPerSecond));
    }

    public static double distanceToBox(
        double x,
        double y,
        double z,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0D;
        double dy = y < minY ? minY - y : y > maxY ? y - maxY : 0.0D;
        double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0D;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
