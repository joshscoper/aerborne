package net.nerdsmp.paper.skyblock;

import java.util.*;

/** Half-open block coordinates matching IslandGrid's inclusive chunk protection. */
public final class IslandBoundary {
    public static final int POINT_LIMIT = 64;
    private IslandBoundary() { }
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public int width() { return maxX - minX; }
    }
    public record Point(double x, double y, double z) { }
    public static Bounds bounds(int slot, int tier) {
        if (tier < 0 || tier > IslandChapter.values().length) throw new IllegalArgumentException("Invalid island land tier");
        int radius = tier + 1;
        return new Bounds((IslandGrid.chunkX(slot) - radius) * 16, (IslandGrid.chunkZ(slot) - radius) * 16,
            (IslandGrid.chunkX(slot) + radius + 1) * 16, (IslandGrid.chunkZ(slot) + radius + 1) * 16);
    }
    /** Nearby outlines only; sampling never asks a world for terrain or loads chunks. */
    public static List<Point> sample(Bounds bounds, double viewerX, double y, double viewerZ) {
        if (!Double.isFinite(viewerX) || !Double.isFinite(viewerZ) || !Double.isFinite(y))
            throw new IllegalArgumentException("Invalid border viewer position");
        List<Point> points = new ArrayList<>();
        line(points, bounds.minX(), bounds.minZ(), bounds.minX(), bounds.maxZ(), viewerX, y, viewerZ);
        line(points, bounds.maxX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), viewerX, y, viewerZ);
        line(points, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.minZ(), viewerX, y, viewerZ);
        line(points, bounds.minX(), bounds.maxZ(), bounds.maxX(), bounds.maxZ(), viewerX, y, viewerZ);
        return points.stream().distinct().sorted(Comparator.comparingDouble(point ->
            (point.x() - viewerX) * (point.x() - viewerX) + (point.z() - viewerZ) * (point.z() - viewerZ)))
            .limit(POINT_LIMIT).toList();
    }
    private static void line(List<Point> output, int x1, int z1, int x2, int z2, double px, double y, double pz) {
        int length = Math.max(x2 - x1, z2 - z1);
        for (int offset = 0; offset <= length; offset += 4) {
            double x = x1 == x2 ? x1 : x1 + offset;
            double z = z1 == z2 ? z1 : z1 + offset;
            if ((x - px) * (x - px) + (z - pz) * (z - pz) > 40 * 40) continue;
            output.add(new Point(x, y, z)); output.add(new Point(x, y + 4, z));
        }
    }
}
