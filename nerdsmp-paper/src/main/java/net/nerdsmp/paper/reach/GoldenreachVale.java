package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Goldenreach Vale's builds (docs/vault/30 Zones/Goldenreach Vale.md): Millbrook on its river, the windmills on the
 * high ground, Thistle Farm, the Brindle Orchards, the Tollkeepers' camp and toll house, the burned Black Granary
 * with the Old Granary Cellars under it, the Hollow Road, and the Warden's post at the Kingsbridge. Every site is
 * found from the terrain (high ground for mills, flat ground for farms), so the builds follow the land.
 */
final class GoldenreachVale {
    static final int MILLBROOK_X = 40, MILLBROOK_Z = 560;

    private final ReachTerrain terrain;
    private final ReachKit kit;
    private final ReachCanvas canvas = new ReachCanvas();
    private final List<ReachFeatures.Spot> spots = new ArrayList<>();
    private final int MILLBROOK_FLOOR;
    /** Footprints already built, so no two houses overlap. */
    private final List<int[]> lots = new ArrayList<>();

    private GoldenreachVale(ReachTerrain terrain) {
        this.terrain = terrain;
        this.kit = new ReachKit(terrain);
        this.MILLBROOK_FLOOR = terrain.millbrookFloor();
    }

    static ReachFeatures.Built build(ReachTerrain terrain) {
        GoldenreachVale vale = new GoldenreachVale(terrain);
        vale.millbrook();
        vale.windmills();
        vale.thistleFarm();
        vale.orchards();
        vale.tollroad();
        vale.granary();
        vale.hollowRoad();
        vale.wardenPost();
        vale.groves();
        vale.chests();
        vale.roost();
        // Every chest spot gets a real chest; ReachGameplay turns it into per-player loot.
        for (ReachFeatures.Spot s : vale.spots) if (s.id().startsWith("chest_"))
            vale.canvas.set((int) Math.floor(s.x()), (int) Math.floor(s.y()), (int) Math.floor(s.z()), Material.CHEST, "facing=south,type=single");
        ReachPaths.sealBlindDoors(vale.canvas, terrain);
        // Any door still without a way to the road or a lane gets a worn path to the nearest.
        ReachPaths.Ground ground = new ReachPaths.Ground(vale.canvas, terrain, new java.util.HashMap<>());
        // Where someone stands, the path is coarse earth: people won't stand on a worn path's lowered surface.
        java.util.Set<String> standing = new java.util.HashSet<>();
        for (ReachFeatures.Spot s : vale.spots) standing.add((int) Math.floor(s.x()) + "," + (int) Math.floor(s.z()));
        ReachPaths.connectDoors(vale.canvas, terrain,
            (x, y, z) -> {
                String floor = ground.floor(x, y, z);
                return floor.equals("road") || floor.contains("dirt_path") || floor.contains("gravel");
            },
            (x, y, z) -> {
                if (ReachPaths.paintable(ground.floor(x, y, z)))
                    vale.canvas.set(x, y - 1, z, standing.contains(x + "," + z) || Math.floorMod(ReachFlora.hash(x, z), 5L) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH);
            },
            1200);
        return new ReachFeatures.Built(vale.canvas, List.copyOf(vale.spots));
    }

    private void spot(String id, int x, int y, int z, float yaw) { spots.add(new ReachFeatures.Spot(id, x + 0.5, y, z + 0.5, yaw)); }

    /** A spot on open ground nearest (x, z): natural terrain the builds left alone, with room to stand. */
    private void openSpot(String id, int x, int z, float yaw) {
        for (int r = 0; r <= 8; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            ReachTerrain.Column c = terrain.column(x + dx, z + dz);
            if (!c.land() || c.wet()) continue;
            int y = c.top() + 1;
            if (canvas.has(x + dx, y - 1, z + dz) || canvas.has(x + dx, y, z + dz) || canvas.has(x + dx, y + 1, z + dz)) continue;
            spot(id, x + dx, y, z + dz, yaw);
            return;
        }
        spot(id, x, terrain.column(x, z).top() + 1, z, yaw);
    }

    /** Ground height under a footprint's highest corner, plus one: the floor a building on it should have. */
    private int floorOver(int x0, int z0, int x1, int z1) {
        int top = Integer.MIN_VALUE;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x += 2) for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z += 2)
            top = Math.max(top, terrain.column(x, z).top());
        return top + 1;
    }

    /** Dry, unroaded, unfielded, land-locked ground: somewhere a building may stand. */
    private boolean buildable(int x, int z, int w, int d) {
        for (int dx = -1; dx <= w; dx += 2) for (int dz = -1; dz <= d; dz += 2) {
            ReachTerrain.Column c = terrain.column(x + dx, z + dz);
            if (!c.land() || c.wet() || c.river() < 3 || c.across() < 6 || c.edge() < 12 || terrain.field(x + dx, z + dz) >= 0) return false;
        }
        return true;
    }

    /**
     * A house centred on (cx, cz) whose front faces {@code turn} (0 north, 1 east, 2 south, 3 west), on its own
     * floor height. Returns the spot in front of its door, or null when the lot isn't buildable.
     */
    private ReachFeatures.Spot house(String id, int cx, int cz, int w, int d, int floors, ReachKit.Style s, boolean gable, int turn, int floor) {
        int[] o = origin(cx, cz, w, d, turn);
        int minX = Math.min(cx - w / 2, cx - d / 2) - 1, minZ = Math.min(cz - w / 2, cz - d / 2) - 1;
        int size = Math.max(w, d) + 2;
        if (!buildable(minX, minZ, size, size)) return null;
        for (int[] l : lots) if (minX <= l[2] && minX + size >= l[0] && minZ <= l[3] && minZ + size >= l[1]) return null;
        lots.add(new int[] {minX, minZ, minX + size, minZ + size});
        int y = floor > 0 ? floor : floorOver(minX, minZ, minX + size, minZ + size);
        ReachBrush b = new ReachBrush(canvas, o[0], y, o[1], turn);
        kit.house(b, w, d, floors, s, floors > 1, gable, ReachFlora.hash(cx, cz));
        int fx = b.wx(w / 2, -2), fz = b.wz(w / 2, -2);
        float yaw = switch (turn) { case 0 -> 0F; case 1 -> 90F; case 2 -> 180F; default -> 270F; };
        ReachFeatures.Spot spot = new ReachFeatures.Spot(id, fx + 0.5, y, fz + 0.5, yaw);
        // A short path from the door to the front of the lot.
        for (int k = -1; k >= -3; k--) for (int side = -1; side <= 1; side++) {
            int px = b.wx(w / 2 + side, k), pz = b.wz(w / 2 + side, k);
            // Coarse dirt, not path blocks: NPCs won't stand on a block shorter than a full one.
            canvas.set(px, y - 1, pz, side == 0 ? Material.PACKED_MUD : Material.COARSE_DIRT);
            for (int yy = y; yy < y + 3; yy++) if (!canvas.has(px, yy, pz)) canvas.set(px, yy, pz, Material.AIR);
            ReachTerrain.Column c = terrain.column(px, pz);
            for (int yy = c.top() + 1; yy < y - 1; yy++) canvas.set(px, yy, pz, Material.DIRT);
        }
        return spot;
    }

    /** The front-left corner for a footprint centred on (cx, cz) turned {@code turn}. */
    private static int[] origin(int cx, int cz, int w, int d, int turn) {
        int hw = w / 2, hd = d / 2;
        return switch (turn) {
            case 0 -> new int[] {cx - hw, cz - hd};
            case 1 -> new int[] {cx + hd, cz - hw};
            case 2 -> new int[] {cx + hw, cz + hd};
            default -> new int[] {cx - hd, cz + hw};
        };
    }

    /** Which way to face so the front looks at (tx, tz). */
    private static int facing(int x, int z, int tx, int tz) {
        int dx = tx - x, dz = tz - z;
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    // ------------------------------------------------------------------ Millbrook

    private void millbrook() {
        int cx = MILLBROOK_X, cz = MILLBROOK_Z;
        // The green: worn grass, a well, the market cross and the old oak.
        for (int x = cx - 10; x <= cx + 10; x++) for (int z = cz - 8; z <= cz + 8; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.river() < 2) continue;
            double r = Math.hypot((x - cx) / 10.0, (z - cz) / 8.0);
            if (r > 1) continue;
            canvas.set(x, MILLBROOK_FLOOR - 1, z, r > 0.85 || terrain.grain(x, z, 0.3) > 0.5 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
        }
        well(cx - 4, cz + 2);
        ReachTrees.fruit(canvas, cx + 5, MILLBROOK_FLOOR, cz - 3, 11);
        kit.signpost(new ReachBrush(canvas, cx, MILLBROOK_FLOOR, cz + 6, 0), ReachKit.Style.VALE);
        spot("green", cx, MILLBROOK_FLOOR, cz + 3, 180F);
        spot("skiff", cx + 3, MILLBROOK_FLOOR, cz + 5, 180F);

        // Houses line the road through the village on both sides, fronts to the road, the closest to the green first.
        String[] named = {"inn", "reeve_hall", "chapel", "smithy", "ada_cottage", "house_a", "house_b", "house_c", "house_d", "house_e",
            "house_f", "house_g", "house_h", "house_i", "house_j", "house_k"};
        List<double[]> road = terrain.roadSamples(cx, cz, 90);
        List<int[]> candidates = new ArrayList<>();
        for (int i = 1; i < road.size() - 1; i += 2) {
            double dx = road.get(i + 1)[0] - road.get(i - 1)[0], dz = road.get(i + 1)[1] - road.get(i - 1)[1], len = Math.hypot(dx, dz);
            if (len == 0) continue;
            double px = -dz / len, pz = dx / len;
            for (int side : new int[] {-1, 1}) for (int depth : new int[] {10, 21, 32}) {
                int hx = (int) Math.round(road.get(i)[0] + px * side * depth), hz = (int) Math.round(road.get(i)[1] + pz * side * depth);
                int target = depth == 11 ? 0 : 1;
                // Front rows face the road; the second row faces a back lane toward the road too.
                int turn = facing(hx, hz, (int) Math.round(road.get(i)[0]), (int) Math.round(road.get(i)[1]));
                candidates.add(new int[] {hx, hz, turn, (int) Math.hypot(hx - cx, hz - cz) + target * 30});
            }
        }
        candidates.sort(java.util.Comparator.comparingInt(c -> c[3]));
        int n = 0;
        // Gardens go in after every house and door path exists, so none lands on a doorstep.
        List<long[]> yards = new ArrayList<>();
        for (int[] c : candidates) {
            if (Math.hypot(c[0] - cx, c[1] - cz) < 15) continue;
            long h = ReachFlora.hash(c[0], c[1]);
            String id = n < named.length ? named[n] : "house_" + n;
            boolean inn = id.equals("inn");
            int w = inn ? 13 : 7 + (int) (h & 3), d = inn ? 11 : 8 + (int) ((h >> 2) & 1);
            int floors = inn ? 2 : 1 + (int) ((h >> 5) & 1);
            ReachKit.Style s = (h & 16) == 0 ? ReachKit.Style.VALE : ReachKit.Style.VALE_SPRUCE;
            // On the village platform the floor is the platform's; on the slopes round it, the ground's.
            int ground = terrain.column(c[0], c[1]).top();
            int floor = Math.abs(ground + 1 - MILLBROOK_FLOOR) <= 1 ? MILLBROOK_FLOOR : 0;
            ReachFeatures.Spot door = house(id, c[0], c[1], w, d, floors, s, !inn && (h & 64) == 0, c[2], floor);
            if (door == null) continue;
            spots.add(door);
            yards.add(new long[] {c[0], c[1], c[2], w, d, h});
            n++;
            if (n >= 30) break;
        }
        for (long[] y : yards) yard((int) y[0], (int) y[1], (int) y[2], (int) y[3], (int) y[4], y[5]);
        watermill();
    }

    /** A cottage garden behind the house: a fenced plot of crops or flowers, sometimes a fruit tree. */
    private void yard(int cx, int cz, int turn, int w, int d, long h) {
        // "Behind" is the way the front doesn't face.
        int bx = switch (turn) { case 1 -> -1; case 3 -> 1; default -> 0; }, bz = switch (turn) { case 0 -> 1; case 2 -> -1; default -> 0; };
        int gx = cx + bx * (d / 2 + 5), gz = cz + bz * (d / 2 + 5);
        int hw = w / 2 + 1;
        for (int u = -hw; u <= hw; u++) for (int v = -3; v <= 3; v++) {
            int x = bx != 0 ? gx + v : gx + u, z = bx != 0 ? gz + u : gz + v;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.road() > 0.2 || c.river() < 3 || canvas.has(x, c.top() + 1, z)) continue;
            boolean edge = Math.abs(u) == hw || Math.abs(v) == 3;
            if (edge) { if (ReachFlora.hash(x, z) % 7 != 0) canvas.set(x, c.top() + 1, z, Material.OAK_FENCE, bx != 0 ? (Math.abs(v) == 3 ? "north=true,south=true" : "east=true,west=true") : (Math.abs(u) == hw ? "north=true,south=true" : "east=true,west=true")); }
            else if ((h & 1) == 0) {
                canvas.set(x, c.top(), z, Material.FARMLAND, "moisture=7");
                canvas.set(x, c.top() + 1, z, (u + v) % 3 == 0 ? Material.CARROTS : Material.POTATOES, "age=7");
            } else if (ReachFlora.hash(z, x) % 3 == 0) canvas.set(x, c.top() + 1, z, (h & 2) == 0 ? Material.PEONY : Material.ROSE_BUSH, "half=lower");
        }
        if ((h & 4) == 0) {
            ReachTerrain.Column c = terrain.column(gx, gz);
            if (c.land() && !c.wet() && !canvas.has(gx, c.top() + 1, gz)) ReachTrees.fruit(canvas, gx, c.top() + 1, gz, h);
        }
    }

    private void well(int x, int z) {
        int y = MILLBROOK_FLOOR;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean rim = dx != 0 || dz != 0;
            canvas.set(x + dx, y - 1, z + dz, rim ? Material.COBBLESTONE : Material.WATER);
            if (rim) canvas.set(x + dx, y, z + dz, Material.COBBLESTONE_WALL, "up=true");
            for (int yy = y - 6; yy < y - 1; yy++) canvas.set(x + dx, yy, z + dz, rim ? Material.COBBLESTONE : Material.WATER);
        }
        for (int[] p : new int[][] {{-1, -1}, {1, 1}}) for (int yy = y + 1; yy <= y + 2; yy++) canvas.set(x + p[0], yy, z + p[1], Material.OAK_FENCE);
        for (int dx = -2; dx <= 2; dx++) canvas.set(x + dx, y + 3, z, Material.OAK_SLAB, "type=bottom");
        for (int dz = -1; dz <= 1; dz++) canvas.set(x, y + 3, z + dz, Material.OAK_SLAB, "type=bottom");
        canvas.set(x, y + 2, z, Material.IRON_CHAIN, "axis=y");
    }

    /**
     * A working mill on the Goldwater: the mill house on the bank, and in front of it a bay cut into the bank and
     * lined with stone, open to the river so the water runs in. The wheel stands in the bay, its bottom paddles in
     * the water, a double rim on spokes with a paddle at every eighth, and its axle runs into the mill's wall. The
     * site keeps clear of the road and its bridge.
     */
    private void watermill() {
        int bestX = 0, bestZ = 0; double best = Double.MAX_VALUE;
        for (int x = MILLBROOK_X - 70; x <= MILLBROOK_X + 70; x += 2) for (int z = MILLBROOK_Z - 50; z <= MILLBROOK_Z + 70; z += 2) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.river() < 2 || c.river() > 4) continue;
            if (terrain.roadDistance(x, z, 30) < 28) continue;
            double d = Math.hypot(x - MILLBROOK_X, z - MILLBROOK_Z);
            if (d < best && d > 18) { best = d; bestX = x; bestZ = z; }
        }
        if (best == Double.MAX_VALUE) return;
        // Which way the water lies from the bank: the wheel's bay opens that way.
        int nx = 0, nz = 0;
        for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (terrain.column(bestX + dir[0] * 5, bestZ + dir[1] * 5).wet()) { nx = dir[0]; nz = dir[1]; break; }
        if (nx == 0 && nz == 0) return;
        int vx = -nz, vz = nx;
        ReachTerrain.Column river = terrain.column(bestX + nx * 5, bestZ + nz * 5);
        int water = river.wet() ? river.water() : MILLBROOK_FLOOR - 4;
        int turn = facing(bestX, bestZ, bestX + nx, bestZ + nz);
        ReachFeatures.Spot door = house("mill", bestX - nx * 8, bestZ - nz * 8, 9, 9, 2, ReachKit.Style.VALE_SPRUCE, true, (turn + 2) & 3, MILLBROOK_FLOOR);
        if (door != null) spots.add(door);
        // The wheel stands in the river itself, the current running under it: between a stone revetment on the
        // bank and a pier in the stream that carries the far end of its axle. The bed is cut clean round the
        // wheel's sweep so its paddles dip into water, never into the bank.
        int hubY = water + 2;
        for (int u = 0; u <= 5; u++) for (int w = -7; w <= 7; w++) {
            int x = bestX + nx * u + vx * w, z = bestZ + nz * u + vz * w;
            for (int y = water - 3; y <= MILLBROOK_FLOOR + 6; y++) {
                if (y == water - 3) canvas.set(x, y, z, Material.GRAVEL);
                else if (u == 0 && y < MILLBROOK_FLOOR) canvas.set(x, y, z, (x + y + z) % 3 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                else if (u == 0 && y == MILLBROOK_FLOOR) canvas.set(x, y, z, Material.STONE_BRICK_WALL, "up=true");
                else if (y <= water) canvas.set(x, y, z, Material.WATER);
                else canvas.set(x, y, z, Material.AIR);
            }
        }
        // The pier in the stream, cutwater upstream and down, its top the bearing for the axle.
        for (int u = 5; u <= 6; u++) for (int w = -1; w <= 1; w++)
            for (int y = water - 3; y <= hubY; y++) canvas.set(bestX + nx * u + vx * w, y, bestZ + nz * u + vz * w, y >= water ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
        for (int w : new int[] {-2, 2}) for (int y = water - 3; y <= water; y++) canvas.set(bestX + nx * 5 + vx * w, y, bestZ + nz * 5 + vz * w, Material.STONE_BRICK_STAIRS, "facing=" + compass(-vx * w / 2, -vz * w / 2) + ",half=bottom");
        // The wheel: a double rim of planks on eight spokes about a hub, paddles round the rim.
        for (int u : new int[] {2, 3}) {
            for (int a = 0; a < 360; a += 4) {
                double rad = Math.toRadians(a);
                int w = (int) Math.round(4 * Math.cos(rad)), y = hubY + (int) Math.round(4 * Math.sin(rad));
                canvas.set(bestX + nx * u + vx * w, y, bestZ + nz * u + vz * w, Material.STRIPPED_SPRUCE_LOG, "axis=" + (nx != 0 ? "x" : "z"));
            }
            for (int k = 1; k <= 3; k++) for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                canvas.set(bestX + nx * u + vx * d[0] * k, hubY + d[1] * k, bestZ + nz * u + vz * d[0] * k, Material.SPRUCE_FENCE);
            for (int[] d : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) for (int k = 1; k <= 2; k++)
                canvas.set(bestX + nx * u + vx * d[0] * k, hubY + d[1] * k, bestZ + nz * u + vz * d[0] * k, Material.SPRUCE_FENCE);
        }
        for (int a = 0; a < 360; a += 45) {
            double rad = Math.toRadians(a);
            int w = (int) Math.round(5 * Math.cos(rad)), y = hubY + (int) Math.round(5 * Math.sin(rad));
            for (int u : new int[] {2, 3}) canvas.set(bestX + nx * u + vx * w, y, bestZ + nz * u + vz * w, Material.SPRUCE_PLANKS);
        }
        // The axle from the pier's bearing, through the wheel and the revetment, into the mill's wall.
        for (int u = -4; u <= 6; u++) canvas.set(bestX + nx * u, hubY, bestZ + nz * u, Material.STRIPPED_DARK_OAK_LOG, "axis=" + (nx != 0 ? "x" : "z"));
    }

    /** Where the Choir camps: a ring round the granary twenty to thirty-two out, the flattest, clear of everything. */
    private int[] choirCampSite(int gx, int gz, int r) {
        int[] best = {gx - 24, gz + 10};
        double bestScore = Double.MAX_VALUE;
        for (int a = 0; a < 360; a += 15) for (int d = 20; d <= 32; d += 4) {
            int cx = gx + (int) Math.round(Math.cos(Math.toRadians(a)) * d), cz = gz + (int) Math.round(Math.sin(Math.toRadians(a)) * d);
            // The barn east of the tower and the loading bay south of it, each with the camp's own width to spare.
            if (cx > gx + r - 14 && cx < gx + r + 23 + 14 && Math.abs(cz - gz) < 5 + 14) continue;
            if (Math.hypot(cx - gx, cz - (gz + r + 3)) < 18) continue;
            if (terrain.roadDistance(cx, cz, 22) < 20) continue;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean ok = true;
            for (int dx = -12; dx <= 12 && ok; dx += 3) for (int dz = -12; dz <= 12; dz += 3) {
                ReachTerrain.Column c = terrain.column(cx + dx, cz + dz);
                if (!c.land() || c.wet() || terrain.field(cx + dx, cz + dz) >= 1) { ok = false; break; }
                lo = Math.min(lo, c.top()); hi = Math.max(hi, c.top());
            }
            if (!ok) continue;
            double score = (hi - lo) + d * 0.05;
            if (score < bestScore) { bestScore = score; best = new int[] {cx, cz}; }
        }
        return best;
    }

    /**
     * Millbrook's flight point: a roost on the nearest clear ground by the green, off the road and the fields,
     * facing the green.
     */
    private void roost() {
        for (int r = 12; r <= 34; r += 2) for (int a = 0; a < 360; a += 15) {
            int x = (int) Math.round(MILLBROOK_X + Math.cos(Math.toRadians(a)) * r), z = (int) Math.round(MILLBROOK_Z + Math.sin(Math.toRadians(a)) * r);
            boolean ok = true;
            for (int dx = -3; dx <= 3 && ok; dx++) for (int dz = -3; dz <= 3; dz++) {
                ReachTerrain.Column c = terrain.column(x + dx, z + dz);
                if (!c.land() || c.wet() || c.across() < 5 || terrain.field(x + dx, z + dz) >= 0) { ok = false; break; }
                for (int y = c.top() - 1; y <= c.top() + 6; y++) if (canvas.has(x + dx, y, z + dz)) { ok = false; break; }
                if (!ok) break;
            }
            if (!ok) continue;
            double gx = MILLBROOK_X - x, gz = MILLBROOK_Z - z;
            int fx = Math.abs(gx) >= Math.abs(gz) ? (int) Math.signum(gx) : 0, fz = fx == 0 ? (int) Math.signum(gz) : 0;
            spots.add(ReachRoost.build(canvas, terrain, x, z, fx, fz, "Millbrook"));
            return;
        }
    }

    /** The compass name of a unit step. */
    private static String compass(int dx, int dz) { return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north"; }

    // ------------------------------------------------------------------ the windmills

    private void windmills() {
        List<int[]> tops = highPoints(MILLBROOK_X - 140, MILLBROOK_Z - 220, MILLBROOK_X + 60, MILLBROOK_Z - 100, 5, 38);
        int i = 0;
        for (int[] t : tops) {
            boolean burned = i == 2;
            windmill(t[0], t[1], terrain.column(t[0], t[1]).top() + 1, burned, i);
            spot(burned ? "burned_mill" : "mill_" + i, t[0], terrain.column(t[0], t[1] + 5).top() + 1, t[1] + 5, 180F);
            if (i == 0) spot("chest_tier3_mill", t[0], terrain.column(t[0], t[1]).top() + 16, t[1] + 1, 0F);
            i++;
        }
        if (!tops.isEmpty()) {
            int rx = tops.get(0)[0] - 8, rz = tops.get(0)[1] + 10;
            spot("windmill_ridge", rx, terrain.column(rx, rz).top() + 1, rz, 180F);
        }
    }

    /** The {@code count} highest dry points in a rectangle, at least {@code spacing} apart. */
    private List<int[]> highPoints(int x0, int z0, int x1, int z1, int count, int spacing) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = x0; x <= x1; x += 4) for (int z = z0; z <= z1; z += 4) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.road() > 0.1 || c.river() < 8 || terrain.field(x, z) >= 0 || c.edge() < 20) continue;
            candidates.add(new int[] {x, z, c.top()});
        }
        candidates.sort(Comparator.comparingInt((int[] a) -> -a[2]));
        List<int[]> chosen = new ArrayList<>();
        for (int[] c : candidates) {
            if (chosen.size() >= count) break;
            if (chosen.stream().allMatch(o -> Math.hypot(o[0] - c[0], o[1] - c[1]) >= spacing)) chosen.add(c);
        }
        return chosen;
    }

    /** A tower mill: tapering stone body, a turned spruce cap and four lattice sails facing east into the wind. */
    private void windmill(int cx, int cz, int y, boolean burned, int seed) {
        int height = 15;
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            ReachTerrain.Column c = terrain.column(cx + dx, cz + dz);
            for (int yy = c.top() + 1; yy < y; yy++) if (Math.hypot(dx, dz) <= 4.6) canvas.set(cx + dx, yy, cz + dz, Material.COBBLESTONE);
        }
        for (int h = -1; h < height; h++) {
            double r = 4.3 - h * 0.12;
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r) continue;
                boolean shell = d > r - 1.1;
                Material m;
                if (!shell) m = h % 5 == 4 && !(dx == 0 && dz == 0) ? Material.SPRUCE_PLANKS : Material.AIR;
                else if (burned && terrain.patch(cx + dx + h, cz + dz - h, 0.3) > 0.1 && h > 4) m = h > 11 ? Material.AIR : Material.BLACKSTONE;
                else m = h < 2 ? Material.MOSSY_COBBLESTONE : terrain.patch(cx + dx + h * 0.6, cz + dz, 0.25) > 0.45 ? Material.STONE_BRICKS : Material.COBBLESTONE;
                canvas.set(cx + dx, y + h, cz + dz, m);
            }
        }
        if (!burned) for (int h = 0; h < height - 1; h++) canvas.set(cx, y + h, cz, Material.SCAFFOLDING, "distance=0,bottom=false,waterlogged=false");
        // Door facing south, windows up the body.
        canvas.set(cx, y, cz + 4, Material.SPRUCE_DOOR, "facing=south,half=lower,hinge=left,open=false");
        canvas.set(cx, y + 1, cz + 4, Material.SPRUCE_DOOR, "facing=south,half=upper,hinge=left,open=false");
        for (int h = 5; h < height - 2; h += 4) canvas.set(cx - 3, y + h, cz, Material.GLASS_PANE, "north=true,south=true");
        if (burned) {
            for (int k = 0; k < 6; k++) canvas.set(cx + 2 - k, y + 12 - k / 2, cz + (k % 3) - 1, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
            canvas.set(cx, y + 1, cz, Material.CAMPFIRE, "lit=false");
            return;
        }
        // The cap: a boat-shaped spruce roof.
        int capY = y + height;
        for (int dz = -4; dz <= 4; dz++) {
            int half = 4 - Math.abs(dz) / 2;
            for (int k = 0; k <= half; k++) {
                canvas.set(cx - half + k, capY + k, cz + dz, Material.SPRUCE_STAIRS, "facing=east,half=bottom");
                canvas.set(cx + half - k, capY + k, cz + dz, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
            }
            canvas.set(cx, capY + half, cz + dz, Material.SPRUCE_SLAB, "type=bottom");
            // The cap's curb: a floor of boards across its foot, resting on the tower's top course.
            for (int x = cx - half + 1; x <= cx + half - 1; x++) canvas.set(x, capY, cz + dz, Material.SPRUCE_PLANKS);
        }
        // Sails on the east face: the windshaft runs out of the cap to the hub, and four lattice arms turn on it.
        int hubX = cx + 5, hubY = capY;
        for (int x = cx + 2; x < hubX; x++) canvas.set(x, hubY, cz, Material.STRIPPED_SPRUCE_LOG, "axis=x");
        canvas.set(hubX, hubY, cz, Material.SPRUCE_LOG, "axis=x");
        double turnA = seed * 0.4;
        for (int arm = 0; arm < 4; arm++) {
            double a = turnA + arm * Math.PI / 2;
            for (int r = 1; r <= 10; r++) {
                int dy = (int) Math.round(Math.sin(a) * r), dz = (int) Math.round(Math.cos(a) * r);
                canvas.set(hubX, hubY + dy, cz + dz, Material.SPRUCE_FENCE);
                // The sail's cloth on its lattice, from the arm out, so it hangs on the arm rather than beside it.
                if (r > 2) for (double off : new double[] {1, 2}) {
                    int sy = (int) Math.round(Math.sin(a) * r + Math.cos(a) * off), sz = (int) Math.round(Math.cos(a) * r - Math.sin(a) * off);
                    if (canvas.get(hubX, hubY + sy, cz + sz) == null)
                        canvas.set(hubX, hubY + sy, cz + sz, (r % 2 == 0) ? Material.WHITE_WOOL : Material.BIRCH_FENCE);
                }
            }
        }
    }

    // ------------------------------------------------------------------ farms and orchards

    private void thistleFarm() {
        int[] site = flatSpot(MILLBROOK_X - 175, MILLBROOK_Z - 30, 40);
        int fx = site[0], fz = site[1];
        ReachFeatures.Spot door = house("thistle_house", fx, fz, 9, 9, 1, ReachKit.Style.VALE_SPRUCE, true, 2, 0);
        if (door != null) { spots.add(door); }
        ReachFeatures.Spot barn = house("thistle_barn", fx + 16, fz + 2, 11, 13, 1, ReachKit.Style.VALE_SPRUCE, false, 3, 0);
        if (barn != null) spots.add(barn);
        // A paddock fenced in oak, with hay.
        int py = terrain.column(fx - 4, fz + 18).top() + 1;
        for (int x = fx - 12; x <= fx + 4; x++) for (int z = fz + 10; z <= fz + 24; z++) {
            boolean edge = x == fx - 12 || x == fx + 4 || z == fz + 10 || z == fz + 24;
            int top = terrain.column(x, z).top();
            if (edge && !(z == fz + 10 && x == fx - 4)) canvas.set(x, top + 1, z, Material.OAK_FENCE, (x == fx - 12 || x == fx + 4) ? "north=true,south=true" : "east=true,west=true");
            else if (!edge && ReachFlora.hash(x, z) % 37 == 0) canvas.set(x, top + 1, z, Material.HAY_BLOCK, "axis=y");
        }
        openSpot("thistle_yard", fx + 4, fz + 9, 0F);
        spot("thistle_paddock", fx - 4, py, fz + 17, 0F);
    }

    /** The flattest dry spot within {@code radius} of (x, z). */
    private int[] flatSpot(int x, int z, int radius) { return flatSpot(x, z, radius, 0); }

    /** As above, and at least {@code roadClear} from any road's centre, for a site that spreads past its middle. */
    private int[] flatSpot(int x, int z, int radius, int roadClear) {
        int bestX = x, bestZ = z; int best = Integer.MAX_VALUE;
        for (int px = x - radius; px <= x + radius; px += 3) for (int pz = z - radius; pz <= z + radius; pz += 3) {
            if (!buildable(px - 8, pz - 8, 16, 16)) continue;
            if (roadClear > 0 && terrain.roadDistance(px, pz, roadClear + 2) < roadClear) continue;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int dx = -8; dx <= 8; dx += 4) for (int dz = -8; dz <= 8; dz += 4) {
                int t = terrain.column(px + dx, pz + dz).top();
                lo = Math.min(lo, t); hi = Math.max(hi, t);
            }
            if (hi - lo < best) { best = hi - lo; bestX = px; bestZ = pz; }
        }
        return new int[] {bestX, bestZ};
    }

    private void orchards() {
        int ox = MILLBROOK_X - 150, oz = MILLBROOK_Z + 90;
        int[] site = flatSpot(ox, oz, 25);
        ReachFeatures.Spot press = house("cider_press", site[0], site[1], 7, 7, 1, ReachKit.Style.VALE, true, 0, 0);
        if (press != null) spots.add(press);
        int planted = 0;
        for (int x = ox - 50; x <= ox + 50; x += 7) for (int z = oz - 35; z <= oz + 35; z += 7) {
            int jx = x + (int) (ReachFlora.hash(x, z) % 2), jz = z + (int) (ReachFlora.hash(z, x) % 2);
            ReachTerrain.Column c = terrain.column(jx, jz);
            if (!c.land() || c.wet() || c.road() > 0.05 || c.river() < 5 || terrain.field(jx, jz) >= 0 || c.edge() < 10) continue;
            if (Math.hypot(jx - site[0], jz - site[1]) < 12) continue;
            ReachTrees.fruit(canvas, jx, c.top() + 1, jz, ReachFlora.hash(jx, jz));
            planted++;
        }
        for (int k = 0; k < 4; k++) {
            int bx = site[0] - 6 + k * 3, bz = site[1] + 9;
            canvas.set(bx, terrain.column(bx, bz).top() + 1, bz, Material.BEEHIVE, "facing=south,honey_level=3");
        }
        spot("orchard", ox, terrain.column(ox, oz).top() + 1, oz, 0F);
        spot("rare_grizzlehide", ox + 20, terrain.column(ox + 20, oz + 10).top() + 1, oz + 10, 0F);
        if (planted == 0) spot("orchard_empty", ox, 104, oz, 0F);
    }

    /** Tall spruce stands like ref 7's, rising over the farms. */
    private void groves() {
        int[][] stands = {{MILLBROOK_X + 70, MILLBROOK_Z + 40}, {MILLBROOK_X - 60, MILLBROOK_Z - 50}, {MILLBROOK_X + 20, MILLBROOK_Z - 90}};
        for (int[] s : stands) for (int k = 0; k < 6; k++) {
            long h = ReachFlora.hash(s[0] + k * 11, s[1] - k * 7);
            int x = s[0] + (int) (h % 15), z = s[1] + (int) ((h >> 8) % 15);
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.road() > 0.05 || c.river() < 4 || terrain.field(x, z) >= 0 || canvas.has(x, c.top() + 1, z)) continue;
            ReachTrees.tallSpruce(canvas, x, c.top() + 1, z, 20 + (int) ((h >> 16) % 10), h);
        }
    }

    // ------------------------------------------------------------------ the Tollroad

    private void tollroad() {
        // Off the road by the width of its palisade, so the camp watches the road rather than standing on it.
        int[] site = flatSpot(-300, 438, 30, 21);
        int tx = site[0], tz = site[1];
        int y = floorOver(tx - 12, tz - 12, tx + 12, tz + 12);
        // A ring of patched tents round a fire, a palisade on the road side, crates of tolls.
        int floor = camp(tx, tz, Material.RED_WOOL, Material.BROWN_WOOL, 4);
        // The palisade stands just past the campground's eased edge, on the hillside's own ground.
        for (int a = 0; a < 360; a += 5) {
            if (a > 300 || a < 30) continue;
            double rad = Math.toRadians(a);
            int px = tx + (int) Math.round(Math.cos(rad) * 17), pz = tz + (int) Math.round(Math.sin(rad) * 17);
            int top = terrain.column(px, pz).top();
            for (int yy = top + 1; yy <= top + 3; yy++) canvas.set(px, yy, pz, Material.STRIPPED_SPRUCE_LOG, "axis=y");
            canvas.set(px, top + 4, pz, Material.SPRUCE_FENCE);
        }
        // The old toll house, roof half fallen.
        ReachFeatures.Spot toll = house("toll_house", tx + 22, tz - 10, 7, 7, 1, ReachKit.Style.VALE, true, 3, 0);
        if (toll != null) spots.add(toll);
        spot("tollkeeper_camp", tx + 3, floor, tz - 3, 0F);
        spot("toll_captain", tx + 2, floor, tz + 2, 0F);
    }

    /**
     * Tents in a ring round a campfire, on a campground levelled for them: the ground cut and made up to one floor
     * across the ring, its edge eased back into the slope, so no tent hangs off a hillside or sits in it.
     */
    private int camp(int cx, int cz, Material cloth, Material patch, int tents) {
        int floor = campGround(cx, cz, 12);
        int top = floor - 1;
        canvas.set(cx, top + 1, cz, Material.CAMPFIRE, "lit=true");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(cx + d[0], top, cz + d[1], Material.COBBLESTONE);
        for (int t = 0; t < tents; t++) {
            double rad = Math.toRadians(t * 360.0 / tents + 20);
            int tx = cx + (int) Math.round(Math.cos(rad) * 8), tz = cz + (int) Math.round(Math.sin(rad) * 8);
            int ty = floor;
            for (int dz = -2; dz <= 2; dz++) for (int k = 0; k <= 2; k++) {
                Material m = (dz + k + t) % 4 == 0 ? patch : cloth;
                canvas.set(tx - 2 + k, ty + k, tz + dz, m);
                canvas.set(tx + 2 - k, ty + k, tz + dz, m);
            }
            canvas.set(tx, ty + 3, tz - 2, Material.SPRUCE_FENCE);
            canvas.set(tx, ty + 3, tz + 2, Material.SPRUCE_FENCE);
            canvas.set(tx, ty, tz, Material.BARREL, "facing=up");
        }
        return floor;
    }

    /**
     * Levels a round campground: the floor is the middle height of the ground there; inside {@code radius} the ground
     * is cut down or made up to it, and over the next four blocks it eases back to the slope. Returns the standing level.
     */
    private int campGround(int cx, int cz, int radius) {
        List<Integer> tops = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2)
            if (Math.hypot(dx, dz) <= radius) tops.add(terrain.column(cx + dx, cz + dz).top());
        tops.sort(Integer::compare);
        int ground = tops.get(tops.size() / 2);
        for (int dx = -radius - 4; dx <= radius + 4; dx++) for (int dz = -radius - 4; dz <= radius + 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > radius + 4) continue;
            int x = cx + dx, z = cz + dz;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet()) continue;
            int t = c.top();
            double ease = d <= radius ? 1 : 1 - (d - radius) / 4.0;
            int target = (int) Math.round(t + (ground - t) * ease);
            if (target == t) continue;
            for (int y = t + 1; y < target; y++) canvas.set(x, y, z, Material.DIRT);
            for (int y = target + 1; y <= t; y++) canvas.set(x, y, z, Material.AIR);
            canvas.set(x, target, z, d <= radius ? (ReachFlora.hash(x, z) % 3 == 0 ? Material.PODZOL : Material.COARSE_DIRT) : Material.GRASS_BLOCK);
        }
        return ground + 1;
    }

    // ------------------------------------------------------------------ the Black Granary and its cellars

    private void granary() {
        List<int[]> top = highPoints(-230, 360, -125, 460, 1, 1);
        int gx = top.isEmpty() ? -190 : top.get(0)[0], gz = top.isEmpty() ? 420 : top.get(0)[1];
        int gy = terrain.column(gx, gz).top() + 1;
        int r = 7;
        // Footing and the burned round tower.
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) {
            if (Math.hypot(dx, dz) > r + 0.6) continue;
            int t = terrain.column(gx + dx, gz + dz).top();
            for (int y = t + 1; y < gy; y++) canvas.set(gx + dx, y, gz + dz, Material.STONE_BRICKS);
            canvas.set(gx + dx, gy - 1, gz + dz, Math.hypot(dx, dz) < r - 1 ? Material.COARSE_DIRT : Material.STONE_BRICKS);
        }
        for (int h = 0; h < 24; h++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > r + 0.4 || d < r - 0.7) { if (d < r - 0.7) canvas.set(gx + dx, gy + h, gz + dz, Material.AIR); continue; }
            // The fire took the southeast quarter of the loft. One standing crescent and two
            // broken shoulders are legible from the fields even when the windows are out of view.
            double fireFace = (dx + dz) / (r * Math.sqrt(2));
            double crown = 19 - 5 * fireFace + terrain.patch(gx + dx, gz + dz, 0.13) * 1.5;
            if (h > crown) continue;
            boolean vent = h >= 10 && h <= 12 && (Math.abs(dx) == 0 || Math.abs(dz) == 0);
            if (vent) { canvas.set(gx + dx, gy + h, gz + dz, Material.AIR); continue; }
            // The fire's mark in broad bands: soot climbing from the burned side, deepest under the broken crown.
            double burn = fireFace * 0.6 + (h - 8) * 0.06;
            Material m = burn > 0.75 ? Material.BLACKSTONE : burn > 0.5 ? Material.POLISHED_BLACKSTONE_BRICKS : burn > 0.32 ? Material.CRACKED_STONE_BRICKS
                : h == 8 || h == 13 ? Material.POLISHED_ANDESITE : h < 2 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
            canvas.set(gx + dx, gy + h, gz + dz, m);
        }
        // The doorway on the south and charred beams fallen across.
        for (int h = 0; h < 4; h++) for (int dx = -1; dx <= 1; dx++) canvas.set(gx + dx, gy + h, gz + r, Material.AIR);
        for (int k = -5; k <= 4; k++) canvas.set(gx + k, gy + 9 + k / 3, gz - 1, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
        for (int k = -4; k <= 5; k++) canvas.set(gx - 1, gy + 4 - k / 3, gz + k, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        canvas.set(gx + 3, gy, gz + 3, Material.CAMPFIRE, "lit=true,signal_fire=true");
        for (int h = 0; h < 12; h++) {
            double a = Math.toRadians(200 + h * 24);
            int sx = gx + (int) Math.round(Math.cos(a) * (r - 1.6)), szz = gz + (int) Math.round(Math.sin(a) * (r - 1.6));
            for (int y = gy; y < gy + h; y++) canvas.set(sx, y, szz, Material.STONE_BRICKS);
            canvas.set(sx, gy + h, szz, Material.STONE_BRICK_SLAB, "type=top");
            for (int y = gy + h + 1; y < gy + h + 4; y++) canvas.set(sx, y, szz, Material.AIR);
        }
        double landing = Math.toRadians(200 + 12 * 24);
        int lx = gx + (int) Math.round(Math.cos(landing) * (r - 1.6)), lz = gz + (int) Math.round(Math.sin(landing) * (r - 1.6));
        for (int y = gy; y < gy + 12; y++) canvas.set(lx, y, lz, Material.STONE_BRICKS);
        for (int y = gy + 12; y < gy + 15; y++) canvas.set(lx, y, lz, Material.AIR);
        // The Choir's camp below the tower: grey tents marked in red round a fire, on the flattest ground clear of the
        // tower, its barn, the loading bay and the road.
        int[] cs = choirCampSite(gx, gz, r);
        int campFloor = camp(cs[0], cs[1], Material.LIGHT_GRAY_WOOL, Material.RED_WOOL, 3);
        spot("granary", gx, terrain.column(gx, gz + r + 3).top() + 1, gz + r + 3, 0F);
        spot("choir_camp", cs[0] + 3, campFloor, cs[1] + 3, 0F);
        spot("chest_tier3_granary", lx, gy + 12, lz, 0F);
        cellars(gx, gy, gz);
        granaryLoadingBay(gx, gy, gz + r);
        granaryBarn(gx + r + 1, gy, gz);
    }

    /**
     * The granary's barn, joined to the tower's east side: a long hall on a stone plinth, the lower walls stone and the
     * upper a dark timber frame, great doors on the yard side. The fire took the half by the tower: there the roof is
     * gone to charred rafters and the frame stands black; the far half keeps its roof of dark shingles. Hay, sacks
     * and a fallen beam lie inside.
     */
    private void granaryBarn(int x0, int gy, int gz) {
        int len = 22, half = 5, x1 = x0 + len - 1, z0 = gz - half, z1 = gz + half, mid = x0 + len / 2;
        for (int x = x0; x <= x1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            for (int y = c.top() + 1; y < gy - 1; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
            canvas.set(x, gy - 1, z, z == z0 - 1 || z == z1 + 1 ? Material.STONE_BRICKS : (x + z) % 4 == 0 ? Material.COARSE_DIRT : Material.SPRUCE_PLANKS);
        }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            boolean burned = x < mid;
            for (int y = gy; y <= gy + 7; y++) {
                if (!wall) { canvas.set(x, y, z, Material.AIR); continue; }
                boolean post = (x - x0) % 4 == 0 || x == x1;
                Material m;
                if (y <= gy + 2) m = y == gy ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
                else if (post || y == gy + 3 || y == gy + 7) m = burned ? Material.POLISHED_BLACKSTONE : Material.DARK_OAK_LOG;
                else m = burned ? (y > gy + 5 && (x + z) % 3 != 0 ? Material.AIR : Material.BLACKSTONE) : Material.STRIPPED_SPRUCE_WOOD;
                boolean window = !post && (y == gy + 4 || y == gy + 5) && (x - x0) % 4 == 2 && (z == z0 || z == z1);
                canvas.set(x, y, z, window ? (burned ? Material.AIR : Material.GLASS_PANE) : m, m == Material.DARK_OAK_LOG && !window ? "axis=y" : "");
            }
        }
        // The great doors on the yard (south) side, under a lintel.
        for (int x = mid + 2; x <= mid + 4; x++) for (int y = gy; y <= gy + 3; y++) canvas.set(x, y, z1, Material.AIR);
        for (int x = mid + 1; x <= mid + 5; x++) canvas.set(x, gy + 4, z1, Material.DARK_OAK_LOG, "axis=x");
        canvas.set(mid + 2, gy, z1, Material.SPRUCE_DOOR, "facing=south,half=lower,hinge=left,open=true");
        canvas.set(mid + 2, gy + 1, z1, Material.SPRUCE_DOOR, "facing=south,half=upper,hinge=left,open=true");
        canvas.set(mid + 4, gy, z1, Material.SPRUCE_DOOR, "facing=south,half=lower,hinge=right,open=true");
        canvas.set(mid + 4, gy + 1, z1, Material.SPRUCE_DOOR, "facing=south,half=upper,hinge=right,open=true");
        for (int x = mid + 2; x <= mid + 4; x++) canvas.set(x, gy - 1, z1 + 1, Material.COBBLESTONE);
        // The roof: intact shingles on the far half, charred rafters and a few hanging boards on the near.
        int ridge = gy + 8 + half + 1;
        for (int x = x0; x <= x1 + 1; x++) for (int k = 0; k <= half + 1; k++) {
            int y = gy + 8 + k;
            boolean burned = x < mid;
            for (int z : new int[] {z0 - 1 + k, z1 + 1 - k}) {
                if (burned) {
                    if ((x - x0) % 2 == 0) canvas.set(x, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
                    else if (k == 0 && (x + z) % 3 == 0) canvas.set(x, y, z, Material.DARK_OAK_SLAB, "type=bottom");
                } else if (z0 - 1 + k == z1 + 1 - k) canvas.set(x, y, z, Material.DARK_OAK_SLAB, "type=bottom");
                else canvas.set(x, y, z, Material.DARK_OAK_STAIRS, "facing=" + (z == z0 - 1 + k ? "south" : "north") + ",half=bottom");
            }
            if (!burned && k > 0) for (int z = z0 - 1 + k; z <= z1 + 1 - k; z++) if (canvas.get(x, y - 1, z) == null || canvas.get(x, y - 1, z).state().equals("minecraft:air")) canvas.set(x, y - 1, z, Material.DARK_OAK_PLANKS);
        }
        for (int x = x0; x < mid; x += 2) canvas.set(x, ridge - 1, gz, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
        // The east gable, closed in timber.
        for (int k = 1; k <= half; k++) for (int z = z0 - 1 + k + 1; z <= z1 + 1 - k - 1; z++) canvas.set(x1, gy + 7 + k, z, Material.STRIPPED_SPRUCE_WOOD);
        // Inside: hay stacked at the far end, sacks, a fallen beam among ash in the burned half.
        for (int x = x1 - 4; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z0 + 3; z++) for (int y = gy; y < gy + 1 + (x + z) % 2; y++) canvas.set(x, y, z, Material.HAY_BLOCK, "axis=y");
        for (int z = z1 - 3; z <= z1 - 1; z++) canvas.set(x1 - 1, gy, z, Material.BARREL, "facing=up");
        for (int x = x0 + 1; x <= mid - 2; x++) canvas.set(x, gy, gz + (x % 3) - 1, (x % 4 == 0) ? Material.STRIPPED_DARK_OAK_LOG : Material.GRAY_CONCRETE_POWDER, (x % 4 == 0) ? "axis=x" : "");
        canvas.set(mid - 1, gy, gz, Material.COAL_BLOCK);
    }

    /** The last intact grain hoist frames the burned south door. Its load lane continues to the cellar stair. */
    private void granaryLoadingBay(int gx, int floor, int doorZ) {
        for (int side : new int[] {-1, 1}) {
            int post = gx + side * 3;
            for (int z = doorZ; z <= doorZ + 2; z++) {
                int bed = terrain.column(post, z).top();
                for (int y = Math.min(bed, floor - 1); y < floor + 1; y++)
                    canvas.set(post, y, z, Material.STONE_BRICKS);
            }
            for (int h = 1; h <= 7; h++)
                canvas.set(post, floor + h, doorZ + 2, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
            canvas.set(post, floor + 5, doorZ + 3, Material.LANTERN, "hanging=true");
            canvas.set(post, floor + 6, doorZ + 3, Material.DARK_OAK_STAIRS, "half=top,facing=north");
        }
        for (int dx = -4; dx <= 4; dx++)
            canvas.set(gx + dx, floor + 7, doorZ + 2, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
        for (int z = doorZ - 1; z <= doorZ + 4; z++)
            canvas.set(gx + 2, floor + 8, z, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        canvas.set(gx + 2, floor + 7, doorZ + 4, Material.GRINDSTONE, "face=ceiling,facing=east");
        for (int h = 4; h <= 6; h++) canvas.set(gx + 2, floor + h, doorZ + 4, Material.IRON_CHAIN, "axis=y");
        canvas.set(gx + 2, floor + 3, doorZ + 4, Material.BARREL, "facing=up");
        canvas.set(gx, floor + 7, doorZ + 3, Material.SPRUCE_WALL_SIGN, "facing=south");
        canvas.text(gx, floor + 7, doorZ + 3, "MILLBROOK", "Grain Reserve", "Lower Stores", "Receiving");
        // Keep the width beneath the lintel open, including where the hill rises into the landing.
        for (int dz = 0; dz <= 3; dz++) for (int dx = -1; dx <= 1; dx++) {
            int x = gx + dx, z = doorZ + dz;
            int surface = dz == 3 ? terrain.column(gx, z).top() : floor - 1;
            int bed = terrain.column(x, z).top();
            for (int y = bed; y < surface; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
            canvas.set(x, surface, z, dx == 0 ? Material.PACKED_MUD : Material.STONE_BRICKS);
            for (int y = surface + 1; y <= Math.max(bed + 1, surface + 4); y++) canvas.set(x, y, z, Material.AIR);
        }
    }

    /**
     * The Old Granary Cellars: a stair from the granary floor down into a vaulted long cellar, a side room with a
     * chest, the round hatchery, and the high threshing floor where the Scarecrow King waits under a crack of sky.
     */
    private void cellars(int gx, int gy, int gz) {
        int minTop = Integer.MAX_VALUE;
        for (int z = gz - 115; z <= gz; z += 3) for (int x = gx - 12; x <= gx + 12; x += 3) minTop = Math.min(minTop, terrain.column(x, z).top());
        int floor = Math.min(gy - 14, minTop - 17);
        // The grain stair, three wide, going north down to the cellar floor.
        int sz = gz - 2, sy = gy - 1;
        while (sy > floor) {
            for (int dx = -1; dx <= 1; dx++) {
                canvas.set(gx + dx, sy, sz, Material.STONE_BRICK_STAIRS, "facing=south,half=bottom");
                canvas.set(gx + dx, sy - 1, sz, Material.STONE_BRICKS);
                for (int y = sy + 1; y < sy + 5; y++) canvas.set(gx + dx, y, sz, Material.AIR);
            }
            for (int dx : new int[] {-2, 2}) for (int y = sy - 1; y < sy + 5; y++) canvas.set(gx + dx, y, sz, Material.BRICKS);
            for (int dx = -2; dx <= 2; dx++) canvas.set(gx + dx, sy + 5, sz, Material.BRICKS);
            if ((gz - sz) % 6 == 0) canvas.set(gx + 1, sy + 3, sz, Material.LANTERN, "hanging=true");
            sz--; sy--;
        }
        spot("cellars_entrance", gx, gy, gz - 1, 180F);
        // The long cellar: vaulted, brick arches every six blocks.
        int lz0 = sz, lz1 = sz - 36;
        room(gx - 4, floor, lz1, gx + 4, floor + 6, lz0, Material.BRICKS);
        for (int z = lz0; z >= lz1; z -= 6) for (int x = gx - 4; x <= gx + 4; x++) {
            int arch = (int) Math.round(Math.sqrt(Math.max(0, 16 - (x - gx) * (x - gx))) * 0.6);
            for (int y = floor + 4 + arch; y <= floor + 6; y++) canvas.set(x, y, z, Material.BRICKS);
        }
        for (int z = lz0 - 3; z >= lz1 + 3; z -= 6) {
            canvas.set(gx - 4, floor, z, Material.BARREL, "facing=east"); canvas.set(gx - 4, floor + 1, z, Material.HAY_BLOCK, "axis=y");
            canvas.set(gx + 4, floor, z, Material.COMPOSTER, "level=8"); canvas.set(gx + 3, floor + 4, z, Material.LANTERN, "hanging=true");
        }
        spot("long_cellar_a", gx, floor, lz0 - 10, 180F);
        spot("long_cellar_b", gx, floor, lz0 - 26, 180F);
        // The side room behind fallen barrels, with the hidden chest.
        int sideZ = lz0 - 18;
        room(gx + 5, floor, sideZ - 1, gx + 7, floor + 2, sideZ + 1, Material.BRICKS);
        canvas.set(gx + 5, floor, sideZ, Material.BARREL, "facing=up");
        room(gx + 8, floor, sideZ - 3, gx + 12, floor + 3, sideZ + 3, Material.STONE_BRICKS);
        spot("chest_tier2_cellar", gx + 11, floor, sideZ, 90F);
        // The hatchery: a round chamber of straw and glowing coals.
        int hz = lz1 - 10;
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 10.5) continue;
            for (int y = floor - 1; y <= floor + 8; y++) {
                boolean shell = d > 9.4 || y == floor - 1 || y == floor + 8 - (int) (d / 4);
                if (y > floor + 8 - (int) (d / 4)) continue;
                canvas.set(gx + dx, y, hz + dz, shell ? (y == floor - 1 ? Material.COARSE_DIRT : Material.STONE_BRICKS) : Material.AIR);
            }
            long h = ReachFlora.hash(gx + dx, hz + dz);
            if (d < 9 && h % 9 == 0) canvas.set(gx + dx, floor - 1, hz + dz, Material.MAGMA_BLOCK);
            else if (d < 9 && h % 7 == 0) canvas.set(gx + dx, floor, hz + dz, Material.HAY_BLOCK, "axis=y");
        }
        for (int z = lz1; z > hz - 9; z--) for (int dx = -1; dx <= 1; dx++) for (int y = floor; y < floor + 4; y++) if (!canvas.has(gx + dx, y, z) || z > hz + 9) canvas.set(gx + dx, y, z, Material.AIR);
        spot("hatchery", gx, floor, hz, 180F);
        // The Sower's journal on a lectern, with room to stand and read it.
        for (int y = floor; y < floor + 3; y++) { canvas.set(gx + 6, y, hz + 2, Material.AIR); canvas.set(gx + 5, y, hz + 2, Material.AIR); }
        canvas.set(gx + 6, floor - 1, hz + 2, Material.COARSE_DIRT);
        canvas.set(gx + 5, floor - 1, hz + 2, Material.COARSE_DIRT);
        canvas.set(gx + 7, floor, hz + 2, Material.LECTERN, "facing=west,has_book=true");
        spot("sower_journal", gx + 6, floor, hz + 2, 270F);
        // The threshing floor: a tall hall, light falling through a crack in the ceiling.
        int t1 = hz - 12, t0 = t1 - 22;
        room(gx - 8, floor, t0, gx + 8, floor + 13, t1, Material.STONE_BRICKS);
        for (int z = t1; z <= hz - 9; z++) for (int dx = -1; dx <= 1; dx++) for (int y = floor; y < floor + 4; y++) canvas.set(gx + dx, y, z, Material.AIR);
        for (int x = gx - 8; x <= gx + 8; x += 4) for (int y = floor; y <= floor + 13; y++) {
            canvas.set(x, y, t1, Material.POLISHED_DEEPSLATE);
            canvas.set(x, y, t0, Material.POLISHED_DEEPSLATE);
        }
        for (int z = t0 + 2; z <= t1 - 2; z++) {
            int surface = terrain.column(gx + 2, z).top();
            if (Math.abs(z - (t0 + t1) / 2) < 3) for (int y = floor + 14; y <= surface; y++) {
                canvas.set(gx + 2, y, z, Material.AIR);
                if (y == surface) canvas.set(gx + 2, y, z, Material.IRON_BARS, "north=true,south=true");
            }
        }
        // The throne of sheaves at the far end, and the boss's place before it.
        for (int dx = -3; dx <= 3; dx++) for (int y = floor; y < floor + 3 - Math.abs(dx) / 2; y++) canvas.set(gx + dx, y, t0 + 1, Material.HAY_BLOCK, "axis=y");
        canvas.set(gx, floor + 2, t0 + 2, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
        spot("scarecrow_king", gx, floor, t0 + 7, 0F);
        spot("chest_tier3_cellars", gx + 5, floor, t0 + 1, 0F);
        spot("threshing_floor", gx, floor, t1 - 3, 180F);
        // The way out: a ladder up a shaft by the throne to a hatch on the hill.
        int ex = gx - 6, ez = t0 + 1;
        int surface = terrain.column(ex, ez).top();
        for (int y = floor; y <= surface; y++) {
            canvas.set(ex, y, ez, Material.LADDER, "facing=south");
            canvas.set(ex, y, ez - 1, Material.STONE_BRICKS);
            canvas.set(ex - 1, y, ez, Material.STONE_BRICKS);
            canvas.set(ex + 1, y, ez, Material.STONE_BRICKS);
        }
        canvas.set(ex, surface + 1, ez, Material.SPRUCE_TRAPDOOR, "facing=south,half=bottom,open=true");
        int outZ = ez + 2;
        int outTop = terrain.column(ex, outZ).top();
        for (int y = outTop + 1; y < outTop + 3; y++) canvas.set(ex, y, outZ, Material.AIR);
        spot("cellars_exit", ex, outTop + 1, outZ, 0F);
    }

    /** A room: stone shell, air inside, a lantern-lit floor. */
    private void room(int x0, int y0, int z0, int x1, int y1, int z1, Material wall) {
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int y = y0 - 1; y <= y1 + 1; y++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean shell = x < x0 || x > x1 || y < y0 || y > y1 || z < z0 || z > z1;
            if (shell) { if (!canvas.has(x, y, z) || canvas.get(x, y, z).state().endsWith("air") == false) canvas.set(x, y, z, y < y0 ? Material.STONE_BRICKS : wall); }
            else canvas.set(x, y, z, Material.AIR);
        }
    }

    // ------------------------------------------------------------------ the Hollow Road and the Warden's post

    private void hollowRoad() {
        // Hedge banks with mossy dry-stone faces on both sides of the road for the last stretch to the downs.
        for (int x = -420; x <= -290; x++) for (int z = 420; z <= 490; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.road() > 0.3 || c.road() < 0.01) continue;
            canvas.set(x, c.top() + 1, z, terrain.grain(x, z, 0.3) > 0.2 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
            canvas.set(x, c.top() + 2, z, Material.OAK_LEAVES, "persistent=true");
            if (ReachFlora.hash(x, z) % 5 == 0) canvas.set(x, c.top() + 3, z, Material.OAK_LEAVES, "persistent=true");
        }
        int[] camp = flatSpot(-370, 430, 20);
        int y = terrain.column(camp[0], camp[1]).top();
        canvas.set(camp[0], y + 1, camp[1], Material.CAMPFIRE, "lit=true");
        for (int dz = -2; dz <= 2; dz++) for (int k = 0; k <= 2; k++) {
            canvas.set(camp[0] + 3 + k, y + 1 + k, camp[1] + dz, Material.GREEN_WOOL);
            canvas.set(camp[0] + 7 - k, y + 1 + k, camp[1] + dz, Material.GREEN_WOOL);
        }
        spot("maren_camp", camp[0] - 2, y + 1, camp[1], 90F);
        spot("hollow_road", -330, terrain.column(-330, 452).top() + 1, 452, 270F);
    }

    private void wardenPost() {
        int x = 9, z = 752;
        int y = terrain.column(x, z).top() + 1;
        // The Warden Post: a stone lodge with a timber loft beside the road at the bridge's north end, its door on
        // the road, a flag over it and a signpost, where the frontier wardens keep watch on the Kingsroad.
        ReachBrush lodge = new ReachBrush(canvas, x + 1, y, z, 3);
        kit.house(lodge, 7, 8, 1, ReachKit.Style.VALE, false, true, 4711, ReachInteriors.Use.OFFICE, ReachInteriors.Trade.MAPS);
        ReachKit.shopSign(lodge, 5, 2, Material.SPRUCE_WALL_HANGING_SIGN, "Warden", "Post");
        for (int k = 0; k <= 7; k++) canvas.set(x, y + k, z - 8, k == 7 ? Material.GREEN_BANNER : Material.SPRUCE_FENCE, k == 7 ? "rotation=12" : "");
        spot("warden_post", x - 1, y, z - 3, 90F);
        spot("kingsgate_fields", x - 30, terrain.column(x - 30, z - 40).top() + 1, z - 40, 0F);
    }

    // ------------------------------------------------------------------ chests along the roads and off them

    private void chests() {
        // Tier I beside the road, every so often, on dry ground just off the verge.
        int placed = 0;
        int[][] roadside = {{20, 700}, {-30, 520}, {-160, 492}, {-250, 495}, {90, 640}, {-60, 610}};
        for (int[] p : roadside) {
            int[] s = nearDry(p[0], p[1]);
            spot("chest_tier1_" + placed++, s[0], terrain.column(s[0], s[1]).top() + 1, s[1], 0F);
        }
        int[][] hidden = {{-120, 640}, {-235, 445}, {140, 520}, {-60, 470}};
        int h = 0;
        for (int[] p : hidden) {
            int[] s = nearDry(p[0], p[1]);
            spot("chest_tier2_" + h++, s[0], terrain.column(s[0], s[1]).top() + 1, s[1], 0F);
        }
    }

    private int[] nearDry(int x, int z) {
        for (int r = 0; r < 60; r += 2) for (int a = 0; a < 360; a += 30) {
            int px = x + (int) Math.round(Math.cos(Math.toRadians(a)) * r), pz = z + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
            ReachTerrain.Column c = terrain.column(px, pz);
            if (c.land() && !c.wet() && c.road() < 0.05 && c.river() > 3 && terrain.field(px, pz) < 0 && !canvas.has(px, c.top() + 1, pz)) return new int[] {px, pz};
        }
        return new int[] {x, z};
    }
}
