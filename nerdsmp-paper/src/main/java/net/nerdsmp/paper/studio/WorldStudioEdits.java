package net.nerdsmp.paper.studio;

import net.nerdsmp.paper.zone.ZoneDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, immutable builder operations shared by the world editor and its tests. */
public final class WorldStudioEdits {
    public static final int PAGE_SIZE = 21;
    public static final int MAX_OUTLINE_POINTS = 204;
    public enum Field { TITLE, CAP, RESPAWN, RADIUS, SAFE, ENABLED }
    private WorldStudioEdits() { }

    public static <T> List<T> page(List<T> entries, int requested) {
        int number = pageNumber(entries.size(), requested);
        return List.copyOf(entries.subList(number * PAGE_SIZE, Math.min(entries.size(), (number + 1) * PAGE_SIZE)));
    }
    public static int pageNumber(int count, int requested) { return Math.max(0, Math.min(requested, pages(count) - 1)); }
    public static int pages(int count) { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static void requireCurrent(ZoneDefinition expected, ZoneDefinition actual) {
        if (expected == null || !expected.equals(actual)) throw new IllegalStateException("This zone changed. Open it again before editing.");
    }

    public static ZoneDefinition field(ZoneDefinition z, Field field, String value) {
        Objects.requireNonNull(field);
        return new ZoneDefinition(z.id(), field == Field.TITLE ? value : z.title(), z.worldId(), z.bounds(),
            field == Field.ENABLED ? onOff(value) : z.enabled(), field == Field.SAFE ? onOff(value) : z.safe(),
            field == Field.CAP ? number(value) : z.cap(), field == Field.RESPAWN ? number(value) : z.respawnSeconds(),
            field == Field.RADIUS ? number(value) : z.activationRadius(), z.points(), z.mobs());
    }
    public static ZoneDefinition roster(ZoneDefinition z, ZoneDefinition.MobEntry entry) {
        ArrayList<ZoneDefinition.MobEntry> entries = new ArrayList<>(z.mobs());
        entries.removeIf(existing -> existing.mobId().equals(entry.mobId()));
        entries.add(entry);
        return contents(z, z.points(), entries);
    }
    public static ZoneDefinition removeMob(ZoneDefinition z, String id) {
        ArrayList<ZoneDefinition.MobEntry> entries = new ArrayList<>(z.mobs());
        if (!entries.removeIf(existing -> existing.mobId().equals(id))) throw new IllegalArgumentException("This mob is no longer in the roster.");
        return contents(z, z.points(), entries);
    }
    public static ZoneDefinition addPoint(ZoneDefinition z, ZoneDefinition.SpawnPoint point) {
        ArrayList<ZoneDefinition.SpawnPoint> points = new ArrayList<>(z.points());
        points.add(point);
        return contents(z, points, z.mobs());
    }
    public static ZoneDefinition removePoint(ZoneDefinition z, int index) {
        ArrayList<ZoneDefinition.SpawnPoint> points = new ArrayList<>(z.points());
        if (index < 0 || index >= points.size()) throw new IllegalArgumentException("This spawn point no longer exists.");
        points.remove(index);
        return contents(z, points, z.mobs());
    }
    private static ZoneDefinition contents(ZoneDefinition z, List<ZoneDefinition.SpawnPoint> points, List<ZoneDefinition.MobEntry> mobs) {
        return new ZoneDefinition(z.id(), z.title(), z.worldId(), z.bounds(), z.enabled(), z.safe(), z.cap(), z.respawnSeconds(), z.activationRadius(), points, mobs);
    }
    public static int number(String input) {
        try { return Integer.parseInt(input.strip()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Enter a whole number in the displayed range."); }
    }
    private static boolean onOff(String input) {
        if (input.equals("on")) return true;
        if (input.equals("off")) return false;
        throw new IllegalArgumentException("Choose on or off.");
    }

    /** Twelve sampled edges, at most seventeen samples per edge, independent of cuboid size. */
    public static List<PreviewPoint> outline(ZoneDefinition.Bounds b) {
        double[][] corners = {
            {b.minX(),b.minY(),b.minZ()}, {b.maxX()+1d,b.minY(),b.minZ()},
            {b.minX(),b.maxY()+1d,b.minZ()}, {b.maxX()+1d,b.maxY()+1d,b.minZ()},
            {b.minX(),b.minY(),b.maxZ()+1d}, {b.maxX()+1d,b.minY(),b.maxZ()+1d},
            {b.minX(),b.maxY()+1d,b.maxZ()+1d}, {b.maxX()+1d,b.maxY()+1d,b.maxZ()+1d}
        };
        int[][] edges = {{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        ArrayList<PreviewPoint> points = new ArrayList<>(MAX_OUTLINE_POINTS);
        for (int[] edge : edges) {
            double[] a = corners[edge[0]], c = corners[edge[1]];
            int steps = Math.max(1, Math.min(16, (int) Math.ceil(Math.max(Math.abs(c[0]-a[0]), Math.max(Math.abs(c[1]-a[1]), Math.abs(c[2]-a[2]))) / 2)));
            for (int step = 0; step <= steps; step++) {
                double fraction = (double) step / steps;
                points.add(new PreviewPoint(a[0]+(c[0]-a[0])*fraction,a[1]+(c[1]-a[1])*fraction,a[2]+(c[2]-a[2])*fraction));
            }
        }
        return List.copyOf(points);
    }
    public record PreviewPoint(double x, double y, double z) { }
}
