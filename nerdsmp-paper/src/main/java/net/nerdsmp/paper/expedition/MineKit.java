package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * Digs a mine out of nothing: every passage is carved as open air first, then {@link #close} wraps the whole
 * of it in rock, so drifts and caverns can run into each other freely. The rock changes with depth, drifts get
 * timber sets and a line of rails, and steps in the floor get a half block so they can be walked.
 */
final class MineKit {
    private static final Material[] UPPER = {Material.STONE, Material.ANDESITE, Material.STONE, Material.COBBLESTONE, Material.TUFF};
    private static final Material[] MIDDLE = {Material.TUFF, Material.STONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.ANDESITE};
    private static final Material[] LOWER = {Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, Material.TUFF};
    private static final Material[] FORGE = {Material.BLACKSTONE, Material.SMOOTH_BASALT, Material.DEEPSLATE, Material.BLACKSTONE};
    private static final Material[] DROWNED = {Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.DARK_PRISMARINE};
    private static final Material[] ORES = {Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_REDSTONE_ORE};
    private static final Material TIMBER = Material.STRIPPED_SPRUCE_LOG;
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int SET_EVERY = 11;

    private record Door(int x, int floor, int z, boolean alongX, int length) { }

    /** No rock is put round anything in this circle at or above {@code y}: it's open to the sky. */
    private record Sky(int x, int z, int radius, int y) { }

    private final Set<Long> air = new HashSet<>();
    private final List<List<int[]>> drifts = new ArrayList<>();
    private final List<Door> doors = new ArrayList<>();
    private final List<Sky> skies = new ArrayList<>();

    private static long key(int x, int y, int z) { return ((x + 2048L) << 40) | ((y + 512L) << 20) | (z + 2048L); }

    private static int xOf(long key) { return (int) (key >> 40) - 2048; }

    private static int yOf(long key) { return (int) ((key >> 20) & 0xFFFFF) - 512; }

    private static int zOf(long key) { return (int) (key & 0xFFFFF) - 2048; }

    boolean open(int x, int y, int z) { return air.contains(key(x, y, z)); }

    void carve(int x, int y, int z) { air.add(key(x, y, z)); }

    void openToSky(int x, int z, int radius, int y) { skies.add(new Sky(x, z, radius, y)); }

    private boolean underSky(int x, int y, int z) {
        for (Sky sky : skies) if (y >= sky.y() && Math.hypot(x - sky.x(), z - sky.z()) <= sky.radius()) return true;
        return false;
    }

    /** A round-backed section at a spot along a drift. */
    private void disk(double cx, int floor, double cz, double radius, int high) {
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            int x = (int) Math.round(cx) + dx, z = (int) Math.round(cz) + dz;
            double from = Math.hypot(x - cx, z - cz) / radius;
            if (from > 1) continue;
            int top = floor + 3 + (int) Math.round((high - 3) * Math.sqrt(1 - from * from));
            for (int y = floor + 1; y <= top; y++) carve(x, y, z);
        }
    }

    /** A winding drift through {x, floor, z} points, roughly {@code wide} across and {@code high} at its crown. */
    void tunnel(int wide, int high, int salt, int[]... points) {
        List<int[]> line = new ArrayList<>();
        for (int index = 0; index + 1 < points.length; index++) {
            int[] from = points[index], to = points[index + 1];
            double length = Math.hypot(to[0] - from[0], to[2] - from[2]);
            int steps = Math.max(2, (int) Math.ceil(length * 2));
            double acrossX = -(to[2] - from[2]) / length, acrossZ = (to[0] - from[0]) / length;
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                double wobble = Math.sin(t * Math.PI) * (smooth((int) (t * length) + from[0], from[2], 6, salt) - .5) * 7;
                double x = from[0] + (to[0] - from[0]) * t + acrossX * wobble, z = from[2] + (to[2] - from[2]) * t + acrossZ * wobble;
                int floor = (int) Math.round(from[1] + (to[1] - from[1]) * t);
                double radius = wide / 2D + (smooth((int) x, (int) z, 5, salt + 1) - .5) * 2;
                disk(x, floor, z, radius, high);
                int[] cell = {(int) Math.round(x), floor, (int) Math.round(z)};
                if (line.isEmpty() || line.getLast()[0] != cell[0] || line.getLast()[2] != cell[2]) line.add(cell);
            }
        }
        drifts.add(line);
    }

    /** A rough cave. Its walls stand {@code walls} high before the roof starts to close. */
    void cavern(int cx, int floor, int cz, int rx, int rz, int high, int walls, int salt) {
        for (int x = cx - rx * 2; x <= cx + rx * 2; x++) for (int z = cz - rz * 2; z <= cz + rz * 2; z++) {
            double edge = 1 + (smooth(x, z, 7, salt) - .5) * .4, from = Math.hypot((x - cx) / (double) rx, (z - cz) / (double) rz) / edge;
            if (from > 1) continue;
            int top = floor + walls + (int) Math.round((high - walls) * Math.sqrt(1 - from * from));
            for (int y = floor + 1; y <= top; y++) carve(x, y, z);
        }
    }

    /** A square-cut room. */
    void hall(int x1, int z1, int x2, int z2, int floor, int high) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = floor + 1; y <= floor + high; y++) carve(x, y, z);
    }

    /** A round room, or a shaft if it's tall. */
    void round(int cx, int floor, int cz, int radius, int high) {
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++)
            if (Math.hypot(x - cx, z - cz) <= radius + .3) for (int y = floor + 1; y <= floor + high; y++) carve(x, y, z);
    }

    /** A timbered doorway three wide and four high, cut through {@code length} blocks of rock. */
    void door(int x, int floor, int z, boolean alongX, int length) {
        for (int along = 0; along < length; along++) for (int across = -1; across <= 1; across++) for (int y = floor + 1; y <= floor + 4; y++)
            carve(alongX ? x + along : x + across, y, alongX ? z + across : z + along);
        doors.add(new Door(x, floor, z, alongX, length));
    }

    /** The rock at a depth, in seams and patches. The bands blur into each other instead of changing on a line. */
    private static Material rock(int x, int y, int z) {
        double depth = y + (smooth(x, z, 11, 501) - .5) * 8;
        Material[] band = x > 168 && z < 92 ? DROWNED : depth >= 84 ? UPPER : depth >= 62 ? MIDDLE : depth >= 42 ? LOWER : depth >= 22 ? FORGE : DROWNED;
        // Seams lie nearly flat, so stretch the patches sideways and let them lean with the height.
        double seam = (smooth(x + y * 2, z - y * 2, 5, 502) + smooth(x - y * 3, z + y * 3, 9, 507)) / 2;
        return band[Math.clamp((int) ((seam - .3) / .4 * band.length), 0, band.length - 1)];
    }

    /** Wraps everything carved in rock, then sets the floors, timbers and rails. */
    void close(BuildCanvas b) {
        Set<Long> solid = new HashSet<>();
        for (long cell : air) {
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            boolean exposed = false;
            for (int[] face : FACES) exposed |= !open(x + face[0], y + face[1], z + face[2]);
            if (!exposed) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
                if (!open(x + dx, y + dy, z + dz)) solid.add(key(x + dx, y + dy, z + dz));
        }
        for (long cell : solid) {
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            if (underSky(x, y, z)) continue;
            if (open(x, y + 1, z)) b.block(x, y, z, noise(x, z, 503) < .2 ? Material.GRAVEL : noise(z, x, 504) < .3 ? Material.COBBLED_DEEPSLATE : rock(x, y, z));
            else if (x > 168 && z < 92 && noise(x * 7 + y, z, 508) < .018) b.block(x, y, z, Material.SEA_LANTERN);
            else if (noise(x * 7 + y, z, 505) < .05 && y > 20) b.block(x, y, z, pick(ORES, x + y, z, 506));
            else b.block(x, y, z, rock(x, y, z));
        }
        for (List<int[]> drift : drifts) {
            rails(b, drift);
            sets(b, drift);
        }
        steps(b);
        for (Door door : doors) frame(b, door);
    }

    /** The lowest open block at a spot, working down from {@code y}, or {@code MIN_VALUE} if it's solid there. */
    private int standing(int x, int y, int z) {
        if (!open(x, y, z)) return Integer.MIN_VALUE;
        while (open(x, y - 1, z)) y--;
        return y;
    }

    /** Half blocks on the terraces a slope leaves, so a drift can be walked up as well as down. */
    private void steps(BuildCanvas b) {
        for (long cell : air) {
            int x = xOf(cell), y = yOf(cell), z = zOf(cell);
            if (open(x, y - 1, z) || !CanopyKit.empty(b, x, y, z)) continue;
            for (int[] face : FACES) {
                if (face[1] != 0) continue;
                int nx = x + face[0], nz = z + face[2];
                if (open(nx, y, nz) || !open(nx, y + 1, nz) || !open(nx, y + 2, nz)) continue;
                b.block(x, y, z, Material.COBBLED_DEEPSLATE_SLAB, "type=bottom");
                break;
            }
        }
    }

    /** A line of rails down the middle of a drift, with its curves and slopes. */
    private void rails(BuildCanvas b, List<int[]> drift) {
        List<int[]> cells = new ArrayList<>();
        for (int[] at : drift) {
            if (!cells.isEmpty()) {
                int[] last = cells.getLast();
                if (last[0] != at[0] && last[2] != at[2]) cells.add(new int[]{at[0], at[1] + 1, last[2]});
            }
            cells.add(new int[]{at[0], at[1] + 1, at[2]});
        }
        for (int[] cell : cells) cell[1] = standing(cell[0], cell[1], cell[2]);
        for (int index = 1; index + 1 < cells.size(); index++) {
            int[] before = cells.get(index - 1), at = cells.get(index), after = cells.get(index + 1);
            if (at[1] == Integer.MIN_VALUE || before[1] == Integer.MIN_VALUE || after[1] == Integer.MIN_VALUE) continue;
            String shape = shape(before, at, after);
            if (shape != null) b.block(at[0], at[1], at[2], Material.RAIL, "shape=" + shape);
        }
    }

    private static String side(int dx, int dz) { return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north"; }

    private static String shape(int[] before, int[] at, int[] after) {
        int fromX = before[0] - at[0], fromZ = before[2] - at[2], toX = after[0] - at[0], toZ = after[2] - at[2];
        if (Math.abs(fromX) + Math.abs(fromZ) != 1 || Math.abs(toX) + Math.abs(toZ) != 1) return null;
        if (after[1] > at[1]) return "ascending_" + side(toX, toZ);
        if (before[1] > at[1]) return "ascending_" + side(fromX, fromZ);
        if (fromX != 0 && toX != 0) return "east_west";
        if (fromZ != 0 && toZ != 0) return "north_south";
        String north = fromZ != 0 ? side(fromX, fromZ) : side(toX, toZ), east = fromX != 0 ? side(fromX, fromZ) : side(toX, toZ);
        return north + "_" + east;
    }

    /** A timber set every few yards: a post at each wall and a cap across, with a lamp under every other one. */
    private void sets(BuildCanvas b, List<int[]> drift) {
        for (int index = SET_EVERY / 2; index + 3 < drift.size(); index += SET_EVERY) {
            int[] at = drift.get(index), ahead = drift.get(index + 3), behind = drift.get(index - 3);
            double dx = ahead[0] - behind[0], dz = ahead[2] - behind[2], length = Math.hypot(dx, dz);
            if (length == 0) continue;
            boolean acrossX = Math.abs(dz) >= Math.abs(dx);
            int stepX = acrossX ? 1 : 0, stepZ = acrossX ? 0 : 1;
            int floor = standing(at[0], at[1] + 1, at[2]);
            if (floor == Integer.MIN_VALUE) continue;
            int left = 0, right = 0;
            while (left < 8 && open(at[0] - stepX * (left + 1), floor, at[2] - stepZ * (left + 1))) left++;
            while (right < 8 && open(at[0] + stepX * (right + 1), floor, at[2] + stepZ * (right + 1))) right++;
            int cap = floor + 3;
            for (int offset = -left; offset <= right; offset++) {
                int x = at[0] + stepX * offset, z = at[2] + stepZ * offset;
                if (open(x, cap, z)) b.block(x, cap, z, TIMBER, acrossX ? "axis=x" : "axis=z");
                if (offset == -left || offset == right) for (int y = cap - 1; y >= floor; y--) if (open(x, y, z)) b.block(x, y, z, TIMBER, "axis=y");
            }
            if (index % (SET_EVERY * 2) == SET_EVERY / 2 && open(at[0], cap - 1, at[2])) b.block(at[0], cap - 1, at[2], Material.LANTERN, "hanging=true");
        }
    }

    private void frame(BuildCanvas b, Door door) {
        for (int along = 0; along < door.length(); along += door.length() - 1) {
            for (int across = -2; across <= 2; across++) {
                int x = door.alongX() ? door.x() + along : door.x() + across, z = door.alongX() ? door.z() + across : door.z() + along;
                if (Math.abs(across) == 2) for (int y = door.floor() + 1; y <= door.floor() + 5; y++) b.block(x, y, z, TIMBER, "axis=y");
                b.block(x, door.floor() + 5, z, TIMBER, door.alongX() ? "axis=z" : "axis=x");
            }
        }
    }

    /** Fills what's open in a box with water or lava, up to {@code top}, leaving anything built there. */
    void flood(BuildCanvas b, Material liquid, int x1, int z1, int x2, int z2, int bottom, int top) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = bottom; y <= top; y++)
            if (open(x, y, z) && CanopyKit.empty(b, x, y, z)) b.block(x, y, z, liquid, "level=0");
    }

    /** A rock standing out of the floor of a cave, from {@code bottom} to {@code top}. */
    void shore(BuildCanvas b, int cx, int cz, int rx, int rz, int bottom, int top, int salt) {
        for (int x = cx - rx - 3; x <= cx + rx + 3; x++) for (int z = cz - rz - 3; z <= cz + rz + 3; z++) {
            double from = Math.hypot((x - cx) / (double) rx, (z - cz) / (double) rz) / (1 + (smooth(x, z, 5, salt) - .5) * .5);
            if (from > 1) continue;
            int surface = top - (int) Math.round(Math.pow(from, 5) * 5);
            for (int y = bottom; y <= surface; y++) if (open(x, y, z)) b.block(x, y, z, y == surface ? (noise(x, z, salt) < .35 ? Material.GRAVEL : Material.TUFF) : rock(x, y, z));
        }
    }
}
