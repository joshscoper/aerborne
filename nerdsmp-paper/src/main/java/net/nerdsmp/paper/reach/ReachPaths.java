package net.nerdsmp.paper.reach;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Joins every door to the street network. From the step outside each door it walks the open ground (up or down a
 * block at a time, never through a building, a fence or a field) to the nearest street, road or path, and lays a
 * path along the way it found. Paths laid for one door count as streets for the next, so neighbours share a lane
 * instead of each cutting its own. A door that opens onto a balcony or into a closed yard finds nothing and is left.
 */
final class ReachPaths {
    private ReachPaths() { }

    /** Whether a walking position (feet at y) already stands on a street, road or path. */
    interface Street { boolean at(int x, int y, int z); }

    /** Lays one cell of path, feet at y. */
    interface Lay { void at(int x, int y, int z); }

    private static final String[] PLANTS = {"short_grass", "tall_grass", "fern", "poppy", "dandelion", "tulip", "allium",
        "daisy", "cornflower", "bluet", "orchid", "lilac", "peony", "rose_bush", "sapling", "dead_bush", "lily_of_the_valley"};
    private static final String[] NOT_FLOOR = {"_fence", "_wall", "pane", "iron_bars", "_door", "farmland", "carpet", "_sign",
        "lantern", "chain", "leaves", "_slab[type=bottom", "campfire", "chest", "barrel", "cauldron"};
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** True for a floor a path may be painted over: turf, earth, moss, or the untouched terrain. */
    static boolean paintable(String floor) {
        return floor.equals("ground") || floor.contains("grass_block") || floor.contains("dirt") || floor.contains("moss_block")
            || floor.contains("podzol") || floor.contains("mud");
    }

    static void connectDoors(ReachCanvas canvas, ReachTerrain terrain, Street street, Lay lay, int budget) {
        List<int[]> doors = new ArrayList<>();
        canvas.forEachBlock((x, y, z, spec) -> {
            String s = spec.state();
            if (!s.contains("_door[") || !s.contains("half=lower")) return;
            int f = s.indexOf("facing=");
            if (f < 0) return;
            String facing = s.substring(f + 7).replaceAll("[,\\]].*", "");
            int dx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0, dz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
            doors.add(new int[] {x, y, z, dx, dz});
        });
        doors.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[1], b[1]));
        Map<Long, ReachTerrain.Column> columns = new HashMap<>();
        Ground ground = new Ground(canvas, terrain, columns);
        for (int[] d : doors) {
            // Try both sides: a door's facing names the side it opens toward, which isn't always the street side.
            for (int side : new int[] {1, -1}) {
                int sx = d[0] + d[3] * side, sz = d[2] + d[4] * side, sy = d[1];
                if (!ground.standable(sx, sy, sz)) continue;
                if (street.at(sx, sy, sz)) break;
                List<int[]> path = search(ground, street, sx, sy, sz, budget);
                if (path == null) continue;
                for (int[] p : path) lay.at(p[0], p[1], p[2]);
                break;
            }
        }
    }

    /** Breadth-first over standable cells to the nearest street; the cells walked, street excluded, or null. */
    private static List<int[]> search(Ground g, Street street, int sx, int sy, int sz, int budget) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Map<Long, long[]> parent = new HashMap<>();
        queue.add(new int[] {sx, sy, sz});
        parent.put(key(sx, sy, sz), null);
        while (!queue.isEmpty() && parent.size() < budget) {
            int[] c = queue.poll();
            for (int[] s : STEPS) for (int dy : new int[] {0, 1, -1}) {
                int nx = c[0] + s[0], ny = c[1] + dy, nz = c[2] + s[1];
                long k = key(nx, ny, nz);
                if (parent.containsKey(k) || !g.standable(nx, ny, nz)) continue;
                // A step up needs headroom over the cell left behind; a step down, over the cell arrived at.
                if (dy == 1 && !g.passable(c[0], c[1] + 2, c[2]) || dy == -1 && !g.passable(nx, ny + 2, nz)) continue;
                parent.put(k, new long[] {c[0], c[1], c[2]});
                if (street.at(nx, ny, nz)) {
                    List<int[]> path = new ArrayList<>();
                    long[] p = parent.get(k);
                    while (p != null) {
                        path.add(new int[] {(int) p[0], (int) p[1], (int) p[2]});
                        p = parent.get(key((int) p[0], (int) p[1], (int) p[2]));
                    }
                    return path;
                }
                queue.add(new int[] {nx, ny, nz});
            }
        }
        return null;
    }

    private static long key(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF); }

    /** What a feature's plan over the terrain leaves to stand on and walk through. */
    static final class Ground {
        private final ReachCanvas canvas;
        private final ReachTerrain terrain;
        private final Map<Long, ReachTerrain.Column> columns;

        Ground(ReachCanvas canvas, ReachTerrain terrain, Map<Long, ReachTerrain.Column> columns) {
            this.canvas = canvas;
            this.terrain = terrain;
            this.columns = columns;
        }

        ReachTerrain.Column column(int x, int z) {
            return columns.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> terrain.column(x, z));
        }

        boolean solid(int x, int y, int z) {
            ReachCanvas.Spec spec = canvas.get(x, y, z);
            if (spec != null) {
                String s = spec.state();
                if (s.equals("minecraft:air") || plant(s)) return false;
                for (String n : NOT_FLOOR) if (s.contains(n)) return false;
                return true;
            }
            ReachTerrain.Column c = column(x, z);
            // A tilled plot is never walked across, so a path goes round the fields, not through the crop.
            if (c.zone() == ReachTerrain.Zone.GOLDENREACH && y == c.top()) {
                int field = terrain.field(x, z);
                if (field >= 1 && field != 6) return false;
            }
            return c.land() && !c.wet() && y <= c.top() && y >= c.bottom();
        }

        boolean passable(int x, int y, int z) {
            ReachCanvas.Spec spec = canvas.get(x, y, z);
            if (spec != null) return spec.state().equals("minecraft:air") || plant(spec.state());
            ReachTerrain.Column c = column(x, z);
            if (!c.land()) return true;
            if (c.wet() && y <= c.water()) return false;
            return y > c.top() || y < c.bottom();
        }

        boolean standable(int x, int y, int z) {
            return solid(x, y - 1, z) && passable(x, y, z) && passable(x, y + 1, z);
        }

        /** The block a walker stands on, as the generator will leave it (the terrain's is reported as its kind). */
        String floor(int x, int y, int z) {
            ReachCanvas.Spec spec = canvas.get(x, y - 1, z);
            if (spec != null) return spec.state();
            ReachTerrain.Column c = column(x, z);
            return c.carriageway() ? "road" : "ground";
        }

        static boolean plant(String s) {
            for (String p : PLANTS) if (s.contains(p)) return true;
            return false;
        }
    }
}
