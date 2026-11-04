package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highcrown, the capital (docs/vault/30 Zones/Highcrown.md), built as a city that climbs its hill (the vault's Shovel
 * Study): three terraces from the Dock Quarter on the lip, through the Market Ward, to the Crown Ward round the
 * Kingsgate and the keep. One axis runs from the sky docks up two grand stairs and the market avenue to the gate,
 * then over the Kingsbridge to the Vale. Each ward has its own work and its own roof colour: warehouses and cranes
 * by the docks under red roofs, the market hall, stalls, guild houses and Crafters' Row under brown, the Lantern Fane,
 * the Hall of Callings and the steward's houses under slate. Terrace walls carry walks with parapets, a ring wall
 * with towers closes the east, and every building stands on a graded, walled site.
 */
final class HighcrownCity {
    static final int GATE_Z = 822, AVENUE_HALF = 6, DOCK_SQUARE_Z = 1000;
    /** Street levels on the three terraces and in the keep: one above each terrace's top block. */
    static final int CROWN = ReachTerrain.CROWN_TOP + 1, MARKET = ReachTerrain.MARKET_TOP + 1, DOCK = ReachTerrain.DOCK_TOP + 1;
    static final int KEEP = ReachTerrain.CROWN_TOP + 13;
    private static final double CX = ReachTerrain.CROWN_RING_X, CZ = ReachTerrain.CROWN_RING_Z;
    /** Where the avenue's two grand stairs start down from the Crown Ward and the Market Ward. */
    private static final int STAIR_A = 905, STAIR_B = 977;

    /** The street level at a point: one above the terrace's top block. */
    static int street(double x, double z) { return ReachTerrain.highcrownTop(x, z) + 1; }

    private record Mark(String id, int x, int y, int z, float yaw) { }

    /** What a workshop on Crafters' Row makes, which picks the props at its door. */
    private enum Trade { NONE, SMITH, WEAVER, ALCHEMIST, CARPENTER, TANNER, WAREHOUSE }

    private final ReachTerrain terrain;
    private final ReachKit kit;
    private final ReachCanvas canvas = new ReachCanvas();
    private final List<Mark> marks = new ArrayList<>();
    private final ReachRuns runs = new ReachRuns();
    /** Columns kept for streets, stairs and walls: no building takes them. */
    private final Set<Long> reserved = new HashSet<>();
    /** Street columns and their level and paving, so doorsteps can be re-laid in stone after the houses go up. */
    private final Map<Long, double[]> streets = new HashMap<>();
    /** Footprints already built, with a one-block margin. */
    private final List<int[]> lots = new ArrayList<>();
    private int crafterCount;

    private HighcrownCity(ReachTerrain terrain) {
        this.terrain = terrain;
        this.kit = new ReachKit(terrain);
    }

    static ReachFeatures.Built build(ReachTerrain terrain) {
        HighcrownCity city = new HighcrownCity(terrain);
        city.avenue();
        city.kingsbridge();
        city.kingsgate();
        city.northWall();
        city.ringWall();
        city.plaza();
        city.citadel();
        city.fane();
        city.hallOfCallings();
        city.crownWard();
        city.marketSquare();
        city.marketWard();
        city.waterwheel(-104, 878);
        city.dockSquare();
        city.docks();
        city.dockQuarter();
        city.terraceWalls();
        city.streetLife();
        city.cityGreen();
        city.relayDoorsteps();
        city.runs.write(city.canvas);
        return new ReachFeatures.Built(city.canvas, city.marks.stream()
            .map(m -> new ReachFeatures.Spot(m.id(), m.x() + 0.5, m.y(), m.z() + 0.5, m.yaw())).toList());
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    private void reserve(int x, int z) { reserved.add(key(x, z)); }

    private boolean onPlatform(int x, int z) {
        ReachTerrain.Column c = terrain.column(x, z);
        int level = street(x, z);
        return c.land() && !c.wet() && c.top() >= level - 3 && c.top() <= level && c.river() > 4;
    }

    /** Polar position round the Crown ring's centre: radius and angle in degrees (0 east, 90 south). */
    private static double ring(double x, double z) { return Math.hypot(x - CX, z - CZ); }

    private static double angle(double x, double z) {
        double a = Math.toDegrees(Math.atan2(z - CZ, x - CX));
        return a < -90 ? a + 360 : a;
    }

    private boolean isAir(int x, int y, int z) {
        ReachCanvas.Spec s = canvas.get(x, y, z);
        return s == null || s.state().equals("minecraft:air");
    }

    // ------------------------------------------------------------------ paving

    /** Paving with a value ramp: worn cobble and gravel down the middle, stone bricks, andesite edges, moss in the cracks. */
    private void pave(int x, int z, int y, double centre) {
        double p = terrain.patch(x, z, 0.16), g = terrain.grain(x, z, 0.3), f = terrain.grain(x + 517, z - 91, 0.7);
        Material m;
        if (centre < 0.3) m = g > 0.45 ? Material.GRAVEL : p > 0.3 ? Material.COBBLESTONE : f > 0.2 ? Material.ANDESITE : Material.STONE_BRICKS;
        else if (centre < 0.75) m = p > 0.5 ? Material.POLISHED_ANDESITE : g > 0.55 ? Material.CRACKED_STONE_BRICKS : p < -0.5 ? Material.MOSSY_STONE_BRICKS
            : f > 0.6 ? Material.COBBLESTONE : Material.STONE_BRICKS;
        else m = g > 0.3 ? Material.ANDESITE : p > 0.4 ? Material.MOSSY_COBBLESTONE : Material.STONE_BRICKS;
        canvas.set(x, y - 1, z, m);
        ReachTerrain.Column c = terrain.column(x, z);
        if (c.land() && !c.wet()) for (int yy = c.top() + 1; yy < y - 1; yy++) if (!canvas.has(x, yy, z)) canvas.set(x, yy, z, Material.STONE);
        int clear = Math.max(y + 4, c.land() ? c.top() : y);
        for (int yy = y; yy <= clear; yy++) if (!canvas.has(x, yy, z)) canvas.set(x, yy, z, Material.AIR);
        streets.put(key(x, z), new double[] {y, centre});
    }

    /** A street: paved and kept clear of buildings. */
    private void street(int x, int z, int y, double centre) {
        pave(x, z, y, centre);
        reserve(x, z);
    }

    /** Doorsteps cut by the house kit are earth; in the city they're re-laid to match the street they open onto. */
    private void relayDoorsteps() {
        for (Map.Entry<Long, double[]> e : streets.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey().intValue();
            int y = (int) e.getValue()[0];
            ReachCanvas.Spec s = canvas.get(x, y - 1, z);
            if (s != null && (s.state().contains("packed_mud") || s.state().contains("coarse_dirt") || s.state().contains("grass_block"))) pave(x, z, y, e.getValue()[1]);
        }
    }

    // ------------------------------------------------------------------ the axis: avenue and grand stairs

    private void avenue() {
        for (int z = GATE_Z + 12; z <= DOCK_SQUARE_Z; z++) {
            if (z > STAIR_A && z < STAIR_A + 15 || z > STAIR_B && z < STAIR_B + 15) continue;
            for (int x = -AVENUE_HALF - 2; x <= AVENUE_HALF + 2; x++) {
                if (!onPlatform(x, z)) continue;
                double centre = Math.abs(x) <= AVENUE_HALF ? Math.abs(x) / (double) AVENUE_HALF * 0.7 : 0.9;
                street(x, z, street(x, z), centre);
            }
        }
        // Inlaid kerb lines of polished andesite between the carriageway and the walks.
        for (Map.Entry<Long, double[]> e : new ArrayList<>(streets.entrySet())) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue();
            if (Math.abs(x) == AVENUE_HALF + 1) canvas.set(x, (int) e.getValue()[0] - 1, z, Material.POLISHED_ANDESITE);
        }
        int lower = grandStair(-AVENUE_HALF, AVENUE_HALF, STAIR_A, CROWN, CROWN - MARKET);
        grandStair(-AVENUE_HALF, AVENUE_HALF, STAIR_B, MARKET, MARKET - DOCK);
        marks.add(new Mark("grand_stair", 0, MARKET, lower + 1, 180F));
    }

    /**
     * A grand stair running south from {@code zTop}, {@code x0..x1} wide, down {@code rises} blocks from {@code level}:
     * a step then a tread for each rise, a landing halfway, parapets on both sides with lamps at the top and foot.
     * The terrain is cut or filled under it. Returns the first z on the lower level.
     */
    private int grandStair(int x0, int x1, int zTop, int level, int rises) {
        int z = zTop + 1, l = level;
        for (int i = 0; i < rises; i++) {
            for (int x = x0; x <= x1; x++) stairCell(x, z, l, true);
            l--;
            z++;
            for (int x = x0; x <= x1; x++) stairCell(x, z, l, false);
            z++;
            if (i == rises / 2 - 1) for (int k = 0; k < 2; k++, z++) for (int x = x0; x <= x1; x++) stairCell(x, z, l, false);
        }
        // Parapets: a solid balustrade on both sides, stepping with the stair, joined to the terrace walls.
        for (int k = zTop - 1; k <= z; k++) {
            int walk = levelAt(x0, k, zTop, level, rises);
            for (int x : new int[] {x0 - 1, x1 + 1}) {
                ReachTerrain.Column c = terrain.column(x, k);
                int top = Math.max(walk - 1, c.land() ? c.top() : walk - 1);
                for (int y = Math.min(c.land() ? c.top() + 1 : walk - 6, walk - 1); y <= top; y++) canvas.set(x, y, k, kit.weathered(new ReachBrush(canvas, 0, 0, 0, 0), x, y - walk, k, Material.STONE_BRICKS));
                if (top < walk + 4) for (int y = top + 1; y <= walk + 4; y++) if (!canvas.has(x, y, k)) canvas.set(x, y, k, Material.AIR);
                runs.wall(x, top + 1, k, Material.STONE_BRICK_WALL);
                reserve(x, k);
            }
        }
        for (int x : new int[] {x0 - 1, x1 + 1}) {
            lampOn(x, levelAt(x0, zTop - 1, zTop, level, rises), zTop - 1, x < 0 ? 0 : 2);
            lampOn(x, levelAt(x0, z, zTop, level, rises), z, x < 0 ? 0 : 2);
        }
        return z;
    }

    /** The walking level of a grand stair at a given z, for its parapets. */
    private int levelAt(int x0, int z, int zTop, int level, int rises) {
        int l = level, k = zTop + 1;
        if (z <= zTop) return level;
        for (int i = 0; i < rises; i++) {
            if (z == k) return l;
            l--; k++;
            if (z == k) return l;
            k++;
            if (i == rises / 2 - 1) { if (z == k || z == k + 1) return l; k += 2; }
        }
        return l;
    }

    /** One column of a stair: a step down (stair block) or a tread at walking level {@code l}. */
    private void stairCell(int x, int z, int l, boolean step) {
        ReachTerrain.Column c = terrain.column(x, z);
        int ground = c.land() ? c.top() : l - 8;
        for (int y = ground + 1; y <= l - 2; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
        if (step) canvas.set(x, l - 1, z, Material.STONE_BRICK_STAIRS, "facing=north,half=bottom");
        else canvas.set(x, l - 1, z, Math.abs(x) <= 1 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
        if (step) canvas.set(x, l - 2, z, Material.STONE_BRICKS);
        for (int y = l; y <= Math.max(l + 4, ground); y++) canvas.set(x, y, z, Material.AIR);
        reserve(x, z);
    }

    /** A lamp on a parapet post: a wall post with a lantern on top. */
    private void lampOn(int x, int walk, int z, int turn) {
        canvas.set(x, walk, z, Material.STONE_BRICKS);
        canvas.set(x, walk + 1, z, Material.CHISELED_STONE_BRICKS);
        canvas.set(x, walk + 2, z, Material.LANTERN);
        runs.remove(x, walk, z);
    }

    // ------------------------------------------------------------------ the arrival from the Vale

    /** A three-arch stone bridge over the gorge, carrying the Kingsroad to the gate, its piers deep in the gorge. */
    private void kingsbridge() {
        int z0 = 764, z1 = GATE_Z - 4, half = 5, y0 = CROWN;
        for (int z = z0; z <= z1; z++) {
            for (int x = -half; x <= half; x++) {
                canvas.set(x, y0 - 1, z, Math.abs(x) <= 2 ? (terrain.grain(x, z, 0.4) > 0.5 ? Material.COBBLESTONE : Material.STONE_BRICKS) : Material.POLISHED_ANDESITE);
                for (int y = y0; y < y0 + 4; y++) canvas.set(x, y, z, Material.AIR);
                reserve(x, z);
            }
            for (int x : new int[] {-half - 1, half + 1}) {
                canvas.set(x, y0 - 1, z, Material.STONE_BRICKS);
                canvas.set(x, y0, z, Material.STONE_BRICKS);
                canvas.set(x, y0 + 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
            }
            int span = (z - z0) % 18;
            double arch = Math.sin(Math.PI * span / 18.0);
            int soffit = y0 - 2 - (int) Math.round(11 * (1 - arch));
            for (int x = -half - 1; x <= half + 1; x++) for (int y = soffit; y < y0 - 1; y++)
                canvas.set(x, y, z, y == soffit || x == -half - 1 || x == half + 1 ? Material.STONE_BRICKS : Material.STONE);
            if (span == 0) for (int x = -half - 2; x <= half + 2; x++) for (int dz = -1; dz <= 1; dz++) {
                ReachTerrain.Column c = terrain.column(x, z + dz);
                int floor = c.land() ? Math.min(c.top(), c.wet() ? c.top() : c.top()) : y0 - 34;
                for (int y = floor; y < y0 - 1; y++) canvas.set(x, y, z + dz, y < y0 - 18 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                // Cutwaters: the piers' faces step out where they stand in the river.
                if (c.wet()) for (int y = floor; y <= c.water() + 1; y++) for (int ex : new int[] {-half - 3, half + 3}) canvas.set(ex, y, z + dz, Material.MOSSY_STONE_BRICKS);
            }
            if (span == 0) for (int x : new int[] {-half - 1, half + 1}) {
                canvas.set(x, y0 + 1, z, Material.STONE_BRICKS);
                canvas.set(x, y0 + 2, z, Material.CHISELED_STONE_BRICKS);
                canvas.set(x, y0 + 3, z, Material.LANTERN);
            }
        }
        marks.add(new Mark("kingsbridge", 0, y0, 790, 180F));
    }

    /** The Kingsgate (ref 4): twin towers, a wide arch, the green emblem of Highcrown over it. */
    private void kingsgate() {
        ReachKit.Style s = ReachKit.Style.SLATE;
        int y0 = CROWN, z0 = GATE_Z - 3, z1 = GATE_Z + 11;
        for (int x = -9; x <= 9; x++) for (int z = z0; z <= z1; z++) {
            reserve(x, z);
            ReachTerrain.Column col = terrain.column(x, z);
            int ground = col.land() ? Math.min(col.top(), y0 - 1) : y0 - 12;
            for (int y = ground; y < y0 + 28; y++) {
                boolean passage = Math.abs(x) <= 4 && y >= y0 - 1 && y < y0 + 11 - (Math.abs(x) == 4 ? 2 : Math.abs(x) == 3 ? 1 : 0);
                boolean shell = Math.abs(x) == 9 || z == z0 || z == z1 || y == y0 + 27 || y == y0 + 12 || y < y0;
                if (passage) { canvas.set(x, y, z, y == y0 - 1 ? Material.STONE_BRICKS : Material.AIR); continue; }
                if (!shell) { canvas.set(x, y, z, Material.AIR); continue; }
                canvas.set(x, y, z, kitWeather(x, y, z));
            }
        }
        for (int x : new int[] {-7, -3, 3, 7}) for (int z : new int[] {z0 - 1, z1 + 1}) for (int y = y0 - 1; y < y0 + 27; y++) {
            if (Math.abs(x) == 3 && y < y0 + 12) continue;
            canvas.set(x, y, z, y % 7 == 6 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
        }
        ReachBrush hoard = new ReachBrush(canvas, -10, y0 + 28, z0 - 2, 0);
        for (int x = 0; x < 21; x++) for (int dz = 0; dz < z1 - z0 + 5; dz++) for (int y = 0; y < 4; y++) {
            boolean wall = x == 0 || x == 20 || dz == 0 || dz == z1 - z0 + 4;
            if (!wall) { hoard.set(x, y, dz, y == 0 ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
            boolean post = x % 4 == 0 || dz == 0 && x == 20;
            hoard.set(x, y, dz, y == 0 ? Material.SPRUCE_PLANKS : post ? Material.DARK_OAK_LOG : y == 2 && x % 2 == 1 ? Material.AIR : Material.CALCITE,
                y > 0 && post ? "axis=y" : "");
            if (y == 0 && (dz == 0 || dz == z1 - z0 + 4)) hoard.set(x, -1, dz == 0 ? -1 : dz + 1, Material.SPRUCE_STAIRS, dz == 0 ? "facing=south,half=top" : "facing=north,half=top");
        }
        kit.roof(hoard.at(20, 4, 0, 1), -1, z1 - z0 + 5, -1, 21, 0, s, true, true);
        for (int x : new int[] {-5, 5}) for (int z : new int[] {z0 - 2, z1 + 2}) canvas.set(x, y0 + 9, z, Material.LANTERN, "hanging=false");
        for (int x = -3; x <= 3; x++) canvas.set(x, y0 + 10, z0 + 1, Material.IRON_BARS, "east=true,west=true");
        for (int x = -9; x <= 9; x++) for (int z : new int[] {z0, z1}) if (Math.floorMod(x, 2) == 0) canvas.set(x, y0 + 28, z, Material.STONE_BRICKS);
        for (int x : new int[] {-9, -5, 5, 9}) for (int z : new int[] {z0 - 1, z1 + 1}) for (int y = y0 - 1; y < y0 + 24; y++) {
            int step = y < y0 + 8 ? 0 : y < y0 + 16 ? 1 : 2;
            if (step == 2 && Math.abs(x) == 5) continue;
            canvas.set(x, y, z, step == 0 ? Material.TUFF_BRICKS : kitWeather(x, y, z));
            if (y == y0 + 7 || y == y0 + 15) canvas.set(x, y + 1, z, Material.STONE_BRICK_STAIRS, z < z0 ? "facing=south,half=bottom" : "facing=north,half=bottom");
        }
        for (int x = -7; x <= 7; x += 2) for (int z : new int[] {z0, z1}) canvas.set(x, y0 + 24, z, Material.AIR);
        for (int ez : new int[] {z0 - 1, z1 + 1}) {
            int ey = y0 + 18;
            for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) {
                double r = Math.hypot(dx, dy);
                if (r > 4.4) continue;
                Material m = r > 3.5 ? Material.STRIPPED_DARK_OAK_WOOD : Material.GREEN_TERRACOTTA;
                if (r < 3.5 && (dy == 1 && Math.abs(dx) <= 2 || dy == 2 && Math.abs(dx) % 2 == 0 && Math.abs(dx) <= 2 || dy == 0 && Math.abs(dx) <= 2)) m = Material.GOLD_BLOCK;
                canvas.set(dx, ey + dy, ez, m);
            }
        }
        for (int x : new int[] {-5, 5}) canvas.set(x, y0 + 7, z0 - 1, Material.GREEN_WALL_BANNER, "facing=north");
        for (int x : new int[] {-5, 5}) canvas.set(x, y0 + 7, z1 + 1, Material.GREEN_WALL_BANNER, "facing=south");
        for (int sx : new int[] {-1, 1}) {
            int cx = sx * 16;
            kit.keepTower(new ReachBrush(canvas, cx - 5, y0, z0 - 2, 0), 11, 40, s, 7 + sx);
            for (int x = cx - 7; x <= cx + 7; x++) for (int z = z0 - 4; z <= z0 + 10; z++) reserve(x, z);
        }
        marks.add(new Mark("kingsgate", 0, y0, GATE_Z + 14, 180F));
    }

    private Material kitWeather(int x, int y, int z) {
        double c = terrain.patch(x + y * 0.7, z - y * 0.5, 0.2);
        if (c > 0.3 && y < CROWN + 2) return Material.MOSSY_STONE_BRICKS;
        if (c < -0.55) return Material.CRACKED_STONE_BRICKS;
        if (c > 0.6) return Material.TUFF_BRICKS;
        return Material.STONE_BRICKS;
    }

    // ------------------------------------------------------------------ walls

    /** A curtain wall section on one column: stone from the ground to the walk, crenellated on the outer face. */
    private void wallColumn(int x, int z, int walk, boolean outer, boolean inner) {
        ReachTerrain.Column c = terrain.column(x, z);
        if (!c.land() || c.wet()) return;
        for (int y = c.top() - 1; y < walk; y++) canvas.set(x, y, z, kitWeather(x, y, z));
        canvas.set(x, walk - 1, z, outer || inner ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
        for (int y = walk; y < walk + 4; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
        if (outer) {
            canvas.set(x, walk, z, Material.STONE_BRICKS);
            if (Math.floorMod(x + z, 2) == 0) canvas.set(x, walk + 1, z, Material.STONE_BRICKS);
            else canvas.set(x, walk + 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
        } else if (inner) runs.wall(x, walk, z, Material.STONE_BRICK_WALL);
        reserve(x, z);
    }

    /** The north wall from the gate east past the keep's crag to the ring wall, rising with the ground. */
    private void northWall() {
        for (int x = 23; x <= 141; x++) {
            int zc = GATE_Z + 3 + (int) Math.round(6 * Math.sin(x / 40.0));
            int ground = terrain.column(x, zc).top();
            int walk = Math.max(CROWN + 12, ground + 9);
            for (int dz = 0; dz < 4; dz++) wallColumn(x, zc + dz, walk, dz == 0, dz == 3);
            if (x % 44 == 0) kit.keepTower(new ReachBrush(canvas, x - 4, Math.max(CROWN, ground + 1), zc - 2, 0), 9, walk - Math.max(CROWN, ground + 1) + 12, ReachKit.Style.SLATE, x);
        }
    }

    /** The ring wall round the east: from the north wall's end down to the lip, with towers. */
    private void ringWall() {
        Set<Long> done = new HashSet<>();
        for (double a = -28; a <= 75; a += 0.15) {
            double rad = Math.toRadians(a);
            for (double r = 158.5; r <= 161.5; r += 0.5) {
                int x = (int) Math.floor(r * Math.cos(rad)), z = (int) Math.floor(905 + r * Math.sin(rad));
                if (!done.add(key(x, z))) continue;
                ReachTerrain.Column c = terrain.column(x, z);
                if (!c.land() || c.edge() < 3) continue;
                int walk = Math.max(street(x, z) + 8, c.top() + 6);
                wallColumn(x, z, walk, r > 161, r < 159);
            }
        }
        for (double a : new double[] {-12, 22, 52}) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(160 * Math.cos(rad)), z = (int) Math.round(905 + 160 * Math.sin(rad));
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land()) continue;
            int base = Math.max(street(x, z), c.top() + 1);
            kit.keepTower(new ReachBrush(canvas, x - 4, base, z - 4, 0), 9, 30, ReachKit.Style.SLATE, (long) a);
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) reserve(x + dx, z + dz);
        }
    }

    // ------------------------------------------------------------------ the Crown Ward

    private void plaza() {
        int z0 = GATE_Z + 12, z1 = GATE_Z + 44;
        for (int x = -32; x <= 32; x++) for (int z = z0; z <= z1; z++) if (onPlatform(x, z) && street(x, z) == CROWN) {
            double r = Math.hypot(x, (z - (z0 + z1) / 2.0) * 1.3);
            street(x, z, CROWN, r < 6 ? 0.9 : Math.abs(Math.sin(r / 3.0)) * 0.6);
        }
        int cz = (z0 + z1) / 2;
        for (int x = -5; x <= 5; x++) for (int z = cz - 5; z <= cz + 5; z++) {
            double r = Math.hypot(x, z - cz);
            if (r > 5.4) continue;
            if (r > 4.4) { canvas.set(x, CROWN, z, Material.STONE_BRICK_SLAB, "type=bottom"); canvas.set(x, CROWN - 1, z, Material.STONE_BRICKS); }
            else { canvas.set(x, CROWN - 1, z, Material.STONE_BRICKS); canvas.set(x, CROWN, z, Material.WATER); }
        }
        for (int y = CROWN; y < CROWN + 4; y++) canvas.set(0, y, cz, Material.STONE_BRICKS);
        canvas.set(0, CROWN + 4, cz, Material.CHISELED_STONE_BRICKS);
        for (int y = CROWN + 5; y < CROWN + 9; y++) canvas.set(0, y, cz, y == CROWN + 8 ? Material.POLISHED_DEEPSLATE : Material.POLISHED_ANDESITE);
        canvas.set(0, CROWN + 9, cz, Material.GOLD_BLOCK);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(d[0], CROWN + 4, cz + d[1], Material.WATER);
        for (int[] p : new int[][] {{-22, z0 + 3}, {26, z0 + 3}, {-22, z1 - 3}, {26, z1 - 3}, {-12, z0 + 1}, {12, z0 + 1}})
            if (onPlatform(p[0], p[1])) ReachProps.lamp(new ReachBrush(canvas, p[0], CROWN, p[1], 0), Material.DARK_OAK_LOG, Material.SPRUCE_TRAPDOOR);
        for (int[] p : new int[][] {{-16, cz + 9}, {16, cz + 9}})
            ReachProps.bench(new ReachBrush(canvas, p[0], CROWN, p[1], 2), Material.SPRUCE_STAIRS, Material.SPRUCE_TRAPDOOR);
        ReachProps.noticeBoard(new ReachBrush(canvas, 8, CROWN, z0 + 2, 2));
        marks.add(new Mark("plaza", 0, CROWN, cz + 8, 180F));
        marks.add(new Mark("plaza_guide", 6, CROWN, z0 + 5, 180F));
        int[] ring = {-20, -14, -8, 14, 20, 26, -2};
        for (int i = 0; i < ring.length; i++) marks.add(new Mark("gate_ring_" + i, ring[i], CROWN, z0 + 4, 0F));
    }

    /** The citadel street from the plaza, the steps up the crag and Highcrown Keep on top. */
    private void citadel() {
        int z0 = 865, z1 = 871, mid = 868;
        for (int x = 33; x <= 48; x++) for (int z = z0 - 1; z <= z1 + 1; z++) street(x, z, CROWN, Math.abs(z - mid) / 4.0);
        int l = CROWN, x = 49;
        for (int i = 0; i < KEEP - CROWN; i++) {
            for (int z = z0; z <= z1; z++) stepEast(x, z, l);
            l++;
            x++;
            for (int z = z0; z <= z1; z++) treadAt(x, z, l);
            x++;
        }
        for (; x <= 90; x++) for (int z = z0; z <= z1; z++) treadAt(x, z, KEEP);
        // Parapets and lamps up both sides.
        for (int k = 49; k <= 90; k++) {
            int walk = Math.min(KEEP, CROWN + Math.max(0, (k - 48) / 2));
            for (int z : new int[] {z0 - 1, z1 + 1}) {
                ReachTerrain.Column c = terrain.column(k, z);
                int top = Math.max(walk - 1, c.top());
                for (int y = Math.min(c.top() + 1, walk - 1); y <= top; y++) canvas.set(k, y, z, Material.STONE_BRICKS);
                runs.wall(k, top + 1, z, Material.STONE_BRICK_WALL);
                if (k % 8 == 1) lampOn(k, top + 1, z, 0);
                reserve(k, z);
            }
        }
        marks.add(new Mark("citadel_steps", 44, CROWN, z0, 90F));
        keep();
    }

    private void stepEast(int x, int z, int l) {
        ReachTerrain.Column c = terrain.column(x, z);
        for (int y = c.top() + 1; y < l; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
        canvas.set(x, l, z, Material.STONE_BRICK_STAIRS, "facing=east,half=bottom");
        for (int y = l + 1; y <= Math.max(l + 5, c.top()); y++) canvas.set(x, y, z, Material.AIR);
        reserve(x, z);
    }

    private void treadAt(int x, int z, int l) {
        ReachTerrain.Column c = terrain.column(x, z);
        for (int y = c.top() + 1; y < l - 1; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
        canvas.set(x, l - 1, z, (z & 1) == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
        for (int y = l; y <= Math.max(l + 4, c.top()); y++) canvas.set(x, y, z, Material.AIR);
        reserve(x, z);
    }

    /** Highcrown Keep on its crag: a curtain wall with corner towers and a tall donjon holding the empty crown. */
    private void keep() {
        int cx = 112, cz = 868, top = KEEP, half = 22;
        ReachKit.Style s = ReachKit.Style.SLATE;
        for (int x = cx - half; x <= cx + half; x++) for (int z = cz - half; z <= cz + half; z++) {
            reserve(x, z);
            boolean wall = Math.abs(x - cx) >= half - 2 || Math.abs(z - cz) >= half - 2;
            int ground = terrain.column(x, z).top();
            for (int y = Math.min(ground, top - 1); y < top - 1; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
            if (!wall) {
                canvas.set(x, top - 1, z, terrain.patch(x, z, 0.2) > 0.3 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                for (int y = top; y < top + 6; y++) canvas.set(x, y, z, Material.AIR);
                continue;
            }
            for (int y = top - 1; y < top + 12; y++) canvas.set(x, y, z, kitWeather(x, y, z));
            if (Math.floorMod(x + z, 2) == 0 && (Math.abs(x - cx) == half || Math.abs(z - cz) == half)) canvas.set(x, top + 12, z, Material.STONE_BRICKS);
        }
        for (int z = cz - 3; z <= cz + 3; z++) for (int y = top; y < top + 7; y++) for (int x = cx - half; x <= cx - half + 2; x++)
            canvas.set(x, y, z, Material.AIR);
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
            kit.tower(new ReachBrush(canvas, cx + c[0] * half - 4, top, cz + c[1] * half - 4, 0), 9, 22, s, c[0] * 3 + c[1]);
        ReachBrush donjon = new ReachBrush(canvas, cx + 2, top, cz - 8, 0);
        int dw = 15, dd = 15, dh = 34;
        for (int x = 0; x < dw; x++) for (int z = 0; z < dd; z++) for (int y = 0; y < dh; y++) {
            boolean wall = x == 0 || x == dw - 1 || z == 0 || z == dd - 1;
            boolean floor = y % 8 == 7;
            if (!wall) { donjon.set(x, y, z, floor ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
            boolean pier = x % 7 == 0 || z % 7 == 0;
            donjon.set(x, y, z, pier ? Material.POLISHED_DEEPSLATE : kitWeather(donjon.wx(x, z), top + y, donjon.wz(x, z)));
        }
        for (int y = 2; y < dh - 3; y += 8) for (int k : new int[] {3, 4, 10, 11}) {
            donjon.set(k, y, 0, Material.GLASS_PANE, "east=true,west=true"); donjon.set(k, y + 1, 0, Material.GLASS_PANE, "east=true,west=true");
            donjon.set(0, y, k, Material.GLASS_PANE, "north=true,south=true"); donjon.set(0, y + 1, k, Material.GLASS_PANE, "north=true,south=true");
        }
        donjon.air(6, 0, 0, 8, 4, 0);
        kit.spire(donjon, -1, dw, dh, s);
        donjon.set(7, 0, 9, Material.POLISHED_BLACKSTONE_BRICKS);
        donjon.set(7, 1, 9, Material.GOLD_BLOCK);
        donjon.set(7, 2, 9, Material.LIGHTNING_ROD);
        // Banners down the donjon's face toward the city.
        for (int k : new int[] {2, 12}) donjon.set(k, dh - 6, -1, Material.GREEN_WALL_BANNER, "facing=north");
        marks.add(new Mark("keep_hall", cx + 9, top, cz - 2, 180F));
        marks.add(new Mark("keep_court", cx - 8, top, cz + 6, 270F));
    }

    /**
     * The Lantern Fane: the city's temple, where the lantern for the Wardens who left is never let go out. A nave with
     * buttresses and tall coloured windows, transepts with round windows, an apse, and a west tower with an open
     * belfry and the tallest spire in Highcrown, its door on the avenue.
     */
    private void fane() {
        int f = CROWN;
        ReachKit.Style s = ReachKit.Style.FANE;
        kit.site(new ReachBrush(canvas, 13, f, 876, 0), 0, 0, 40, 26, 2, Material.STONE_BRICKS);
        for (int x = 11; x <= 55; x++) for (int z = 874; z <= 904; z++) reserve(x, z);
        Material[] glass = {Material.BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE};
        // Floor through the whole church.
        for (int x = 14; x <= 52; x++) for (int z = 877; z <= 901; z++) {
            boolean inside = x <= 46 && z >= 883 && z <= 895 || x >= 32 && x <= 38 || x > 46 && Math.hypot(x - 46, z - 889) < 6.5 || x <= 22 && z >= 885 && z <= 893;
            if (inside) canvas.set(x, f - 1, z, z == 889 || x >= 33 && x <= 37 && z == 889 ? Material.POLISHED_ANDESITE : (x + z) % 2 == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
        }
        // Nave: walls, buttresses, windows.
        for (int x = 22; x <= 46; x++) for (int z : new int[] {883, 895}) for (int y = f - 1; y <= f + 12; y++) {
            if (x >= 32 && x <= 38) continue;
            canvas.set(x, y, z, y == f + 12 ? Material.STONE_BRICK_SLAB : kit.weathered(new ReachBrush(canvas, 0, 0, 0, 0), x, y - f, z, Material.STONE_BRICKS),
                y == f + 12 ? "type=bottom" : "");
        }
        for (int x = 23; x <= 45; x++) for (int z = 884; z <= 894; z++) for (int y = f; y <= f + 12; y++) canvas.set(x, y, z, Material.AIR);
        for (int[] bay : new int[][] {{25, 26}, {29, 30}, {41, 42}, {44, 45}}) for (int x = bay[0]; x <= bay[1]; x++) for (int z : new int[] {883, 895}) {
            for (int y = f + 3; y <= f + 9; y++) canvas.set(x, y, z, glass[(x + y) % glass.length], "east=true,west=true");
            canvas.set(x, f + 2, z, Material.STONE_BRICK_SLAB, "type=top");
        }
        for (int bx : new int[] {24, 28, 31, 39, 43}) for (int z : new int[] {882, 896}) {
            int out = z < 889 ? -1 : 1;
            for (int y = f - 1; y <= f + 9; y++) canvas.set(bx, y, z, Material.STONE_BRICKS);
            canvas.set(bx, f + 10, z, Material.STONE_BRICK_STAIRS, out < 0 ? "facing=north,half=bottom" : "facing=south,half=bottom");
            for (int y = f - 1; y <= f + 4; y++) canvas.set(bx, y, z + out, Material.STONE_BRICKS);
            canvas.set(bx, f + 5, z + out, Material.STONE_BRICK_STAIRS, out < 0 ? "facing=north,half=bottom" : "facing=south,half=bottom");
        }
        // Pews either side of the aisle, the altar and the ever-burning lantern in the apse.
        for (int x = 25; x <= 43; x += 2) {
            if (x >= 31 && x <= 39) continue;
            for (int z = 885; z <= 894; z++) if (z != 889 && z != 888 && z != 890) canvas.set(x, f, z, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
        }
        // Transepts.
        for (int z = 877; z <= 901; z++) for (int x = 32; x <= 38; x++) for (int y = f - 1; y <= f + 12; y++) {
            boolean wall = x == 32 || x == 38 || z == 877 || z == 901;
            boolean crossing = z > 883 && z < 895;
            if (crossing && (x == 32 || x == 38)) { canvas.set(x, y, z, y >= f + 11 ? Material.STONE_BRICKS : Material.AIR); continue; }
            if (wall) canvas.set(x, y, z, y == f + 12 ? Material.STONE_BRICK_SLAB : kit.weathered(new ReachBrush(canvas, 0, 0, 0, 0), x, y - f, z, Material.STONE_BRICKS), y == f + 12 ? "type=bottom" : "");
            else if (y >= f) canvas.set(x, y, z, Material.AIR);
        }
        for (int z : new int[] {877, 901}) for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
            double r = Math.hypot(dx, dy);
            if (r < 2.6) canvas.set(35 + dx, f + 7 + dy, z, r < 1 ? Material.YELLOW_STAINED_GLASS_PANE : glass[(dx + dy + 8) % 2 == 0 ? 0 : 3], "east=true,west=true");
        }
        // Apse: a half-round east end with tall windows under a stepped dome.
        for (int x = 46; x <= 53; x++) for (int z = 882; z <= 896; z++) {
            double r = Math.hypot(x - 46, z - 889);
            if (x == 46 && z > 883 && z < 895) continue;
            if (r >= 5.6 && r < 6.6) for (int y = f - 1; y <= f + 10; y++) {
                boolean window = y >= f + 3 && y <= f + 8 && (z == 886 || z == 892 || z == 889);
                canvas.set(x, y, z, window ? glass[(z + y) % glass.length] : kit.weathered(new ReachBrush(canvas, 0, 0, 0, 0), x, y - f, z, Material.STONE_BRICKS),
                    window ? "north=true,south=true" : "");
            } else if (r < 5.6) for (int y = f; y <= f + 10; y++) canvas.set(x, y, z, Material.AIR);
            for (int k = 0; k <= 6; k++) if (r < 6.6 - k && r >= 5.6 - k) canvas.set(x, f + 11 + k, z, Material.DEEPSLATE_TILES);
        }
        canvas.set(50, f, 889, Material.CHISELED_STONE_BRICKS);
        canvas.set(50, f + 1, 889, Material.CANDLE, "candles=4,lit=true");
        canvas.set(49, f, 887, Material.CANDLE, "candles=3,lit=true");
        canvas.set(49, f, 891, Material.CANDLE, "candles=3,lit=true");
        for (int y = f + 6; y <= f + 10; y++) canvas.set(47, y, 889, Material.IRON_CHAIN, "axis=y");
        canvas.set(47, f + 5, 889, Material.LANTERN, "hanging=true");
        for (int z : new int[] {885, 893}) canvas.set(45, f + 6, z, Material.GREEN_WALL_BANNER, "facing=west");
        // Roofs: the nave's ridge along x, the transepts' along z, both deep slate.
        kit.roof(new ReachBrush(canvas, 46, f + 13, 883, 1), -1, 13, 0, 24, 0, s, true, true);
        kit.roof(new ReachBrush(canvas, 32, f + 13, 877, 0), -1, 7, 0, 5, 0, s, true, true);
        kit.roof(new ReachBrush(canvas, 32, f + 13, 896, 0), -1, 7, 0, 5, 0, s, true, true);
        // The west tower, its door on the avenue, a round window, an open belfry and the spire.
        int tx0 = 14, tx1 = 22, tz0 = 885, tz1 = 893, tc = 889;
        for (int x = tx0; x <= tx1; x++) for (int z = tz0; z <= tz1; z++) for (int y = f - 1; y <= f + 30; y++) {
            boolean wall = x == tx0 || x == tx1 || z == tz0 || z == tz1;
            if (!wall) { canvas.set(x, y, z, y > f && y % 9 == 0 ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
            boolean corner = (x == tx0 || x == tx1) && (z == tz0 || z == tz1);
            canvas.set(x, y, z, corner ? Material.POLISHED_ANDESITE : kit.weathered(new ReachBrush(canvas, 0, 0, 0, 0), x, y - f, z, Material.STONE_BRICKS));
        }
        for (int[] c : new int[][] {{tx0 - 1, tz0 - 1}, {tx0 - 1, tz1 + 1}, {tx1 + 1, tz0 - 1}, {tx1 + 1, tz1 + 1}}) {
            for (int y = f - 1; y <= f + 24; y++) canvas.set(c[0], y, c[1], Material.STONE_BRICKS);
            canvas.set(c[0], f + 25, c[1], Material.STONE_BRICK_WALL, "up=true");
        }
        for (int z = tc - 2; z <= tc + 2; z++) for (int y = f; y <= f + 6; y++) {
            int dz = Math.abs(z - tc);
            if (y <= f + 4 || y == f + 5 && dz <= 1 || y == f + 6 && dz == 0) {
                canvas.set(tx0, y, z, Material.AIR);
                canvas.set(tx1, y, z, Material.AIR);
            }
        }
        canvas.set(tx0, f, tc - 1, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=left,open=true");
        canvas.set(tx0, f + 1, tc - 1, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=left,open=true");
        canvas.set(tx0, f, tc + 1, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=right,open=true");
        canvas.set(tx0, f + 1, tc + 1, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=right,open=true");
        for (int dz = -3; dz <= 3; dz++) for (int dy = -3; dy <= 3; dy++) {
            double r = Math.hypot(dz, dy);
            if (r > 3.3) continue;
            Material m = r > 2.6 ? Material.CHISELED_STONE_BRICKS : r < 0.9 ? Material.YELLOW_STAINED_GLASS_PANE
                : Math.abs(dz) == Math.abs(dy) || dz == 0 || dy == 0 ? Material.WHITE_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE;
            canvas.set(tx0, f + 14 + dy, tc + dz, m, m == Material.CHISELED_STONE_BRICKS ? "" : "north=true,south=true");
        }
        for (int x = tx0 - 1; x <= tx1 + 1; x++) for (int z = tz0 - 1; z <= tz1 + 1; z++) {
            boolean edge = x == tx0 - 1 || x == tx1 + 1 || z == tz0 - 1 || z == tz1 + 1;
            if (edge) canvas.set(x, f + 30, z, Material.STONE_BRICK_STAIRS, x == tx0 - 1 ? "facing=east,half=top" : x == tx1 + 1 ? "facing=west,half=top"
                : z == tz0 - 1 ? "facing=south,half=top" : "facing=north,half=top");
        }
        // The belfry: piers at the corners and the middle of each face, open arches between, a bell inside.
        for (int y = f + 31; y <= f + 36; y++) for (int x = tx0; x <= tx1; x++) for (int z = tz0; z <= tz1; z++) {
            boolean wall = x == tx0 || x == tx1 || z == tz0 || z == tz1;
            if (!wall) { canvas.set(x, y, z, y == f + 31 ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
            boolean pier = (x == tx0 || x == tx1 || x == 18) && (z == tz0 || z == tz1 || z == tc);
            boolean open = !pier && y >= f + 32 && y <= f + 35;
            canvas.set(x, y, z, open ? Material.AIR : pier ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
        }
        canvas.set(18, f + 32, tc, Material.BELL, "attachment=floor,facing=west");
        // The spire: rings of slate stepping in, steep, with a gilded finial.
        int y = f + 37;
        for (int lo = tx0 - 1, hi = tx1 + 1, zlo = tz0 - 1, zhi = tz1 + 1; lo <= hi; lo++, hi--, zlo++, zhi--) {
            for (int k = 0; k < 3; k++, y++) for (int x = lo; x <= hi; x++) for (int z = zlo; z <= zhi; z++) {
                boolean edge = x == lo || x == hi || z == zlo || z == zhi;
                if (!edge) continue;
                if (k == 0) canvas.set(x, y, z, Material.DEEPSLATE_TILE_STAIRS, x == lo ? "facing=east,half=bottom" : x == hi ? "facing=west,half=bottom"
                    : z == zlo ? "facing=south,half=bottom" : "facing=north,half=bottom");
                else canvas.set(x, y, z, Material.DEEPSLATE_TILES);
            }
            if (hi - lo <= 0) break;
        }
        canvas.set(18, y, tc, Material.GOLD_BLOCK);
        canvas.set(18, y + 1, tc, Material.LIGHTNING_ROD);
        // The porch: lamps and planters either side of the door on the avenue walk.
        for (int z : new int[] {tc - 4, tc + 4}) {
            canvas.set(tx0 - 2, f, z, Material.STONE_BRICKS);
            canvas.set(tx0 - 2, f + 1, z, Material.CHISELED_STONE_BRICKS);
            canvas.set(tx0 - 2, f + 2, z, Material.LANTERN);
        }
        marks.add(new Mark("fane", tx0 - 3, f, tc, 270F));
        // The Fane garden: lawns, gravel walks, two great oaks and benches looking out over the Market Ward.
        for (int x = 12; x <= 56; x++) for (int z = 902; z <= 926; z++) {
            if (reserved.contains(key(x, z)) && z <= 904) continue;
            if (!onPlatform(x, z) || street(x, z) != CROWN || ring(x, z) > ReachTerrain.CROWN_RADIUS - 1.5) continue;
            double walk = Math.abs(ring(x, z) - (ReachTerrain.CROWN_RADIUS - 5));
            if (walk < 1.6) street(x, z, CROWN, 0.5);
            else if (!canvas.has(x, CROWN - 1, z)) canvas.set(x, CROWN - 1, z, terrain.grain(x, z, 0.3) > 0.6 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
        }
        ReachTrees.oak(canvas, 24, CROWN, 908, 9001);
        ReachTrees.oak(canvas, 46, CROWN, 909, 9002);
        for (int x : new int[] {30, 38}) ReachProps.bench(new ReachBrush(canvas, x, CROWN, 916, 2), Material.SPRUCE_STAIRS, Material.SPRUCE_TRAPDOOR);
    }

    /** The Hall of Callings on the avenue below the plaza: skills, mastery, jobs and parties. */
    private void hallOfCallings() {
        int x0 = -24, x1 = -10, z0 = 867, z1 = 883;
        ReachBrush b = lotBrush(x0, z0, x1, z1, 1, CROWN);
        int[] size = lotSize(x0, z0, x1, z1, 1);
        kit.house(b, size[0], size[1], 2, ReachKit.Style.SLATE, true, false, 4242);
        lots.add(new int[] {x0 - 2, z0 - 1, x1 + 3, z1 + 1});
        for (int z = z0 - 1; z <= z1 + 1; z++) for (int x = x0 - 1; x <= x1; x++) reserve(x, z);
        for (int z : new int[] {z0 + 2, z1 - 2}) {
            canvas.set(x1 + 2, CROWN + 6, z, Material.GREEN_WALL_BANNER, "facing=east");
            canvas.set(x1 + 2, CROWN + 7, z, Material.GREEN_WALL_BANNER, "facing=east");
        }
        for (int i = 0; i < 3; i++) marks.add(new Mark("hall_" + i, x1 + 4, CROWN, z0 + 3 + i * 5, 270F));
    }

    /** The steward's houses round the plaza and along the citadel street, and a house below the Hall. */
    private void crownWard() {
        ReachKit.Style[] slate = {ReachKit.Style.SLATE, ReachKit.Style.SLATE, ReachKit.Style.HIGHCROWN};
        int n = 0;
        for (int z = 836; z + 8 <= 861; z += 9) tryHouse(36, z, 45, z + 7, 3, CROWN, slate[n % 3], 2 + n % 2, 7100 + n++, Trade.NONE);
        for (int x = 47; x + 7 <= 60; x += 9) tryHouse(x, 851, x + 7, 861, 2, CROWN, slate[n % 3], 2, 7100 + n++, Trade.NONE);
        tryHouse(-21, 887, -12, 895, 1, CROWN, ReachKit.Style.SLATE, 1, 7150, Trade.NONE);
    }

    // ------------------------------------------------------------------ the Market Ward

    private void marketSquare() {
        int z0 = 920, z1 = 976;
        for (int x = -33; x <= 33; x++) for (int z = z0; z <= z1; z++) {
            if (!onPlatform(x, z) || street(x, z) != MARKET) continue;
            if (reserved.contains(key(x, z))) continue;
            boolean line = Math.floorMod(z - z0, 8) == 0 || Math.abs(x) == 13;
            street(x, z, MARKET, line ? 0.5 : Math.abs(x) <= AVENUE_HALF ? Math.abs(x) / 9.0 : 0.85);
            if (line) canvas.set(x, MARKET - 1, z, Material.POLISHED_ANDESITE);
        }
        Material[][] awnings = {{Material.RED_WOOL, Material.WHITE_WOOL}, {Material.BLUE_WOOL, Material.WHITE_WOOL},
            {Material.PURPLE_WOOL, Material.LIGHT_GRAY_WOOL}, {Material.RED_WOOL, Material.LIGHT_GRAY_WOOL}};
        Material[] goods = {Material.MELON, Material.PUMPKIN, Material.HAY_BLOCK, Material.BARREL, Material.DRIED_KELP_BLOCK};
        for (int i = 0; i <= 8; i++) {
            int z = 922 + i * 5;
            Material[] a = awnings[i % awnings.length], b2 = awnings[(i + 2) % awnings.length];
            kit.stall(new ReachBrush(canvas, -AVENUE_HALF - 1, MARKET, z, 1), a[0], a[1], goods[i % goods.length], ReachKit.Style.HIGHCROWN);
            kit.stall(new ReachBrush(canvas, AVENUE_HALF + 1, MARKET, z + 4, 3), b2[0], b2[1], goods[(i + 3) % goods.length], ReachKit.Style.HIGHCROWN);
            marks.add(new Mark("stall_w_" + i, -AVENUE_HALF + 1, MARKET, z + 2, 90F));
            marks.add(new Mark("stall_e_" + i, AVENUE_HALF - 1, MARKET, z + 2, 270F));
        }
        // Strings of lanterns across the avenue between the stall rows.
        for (int z = 924; z <= 964; z += 10) for (int x = -AVENUE_HALF; x <= AVENUE_HALF; x++) {
            int sag = (int) Math.round(1.2 * (1 - Math.pow(x / (double) AVENUE_HALF, 2)));
            canvas.set(x, MARKET + 6 - sag, z, Material.IRON_CHAIN, "axis=x");
            if (x % 3 == 0) canvas.set(x, MARKET + 5 - sag, z, Material.LANTERN, "hanging=true");
        }
        marketHall(-31, 928, -15, 958);
        marketCross(21, 941);
        for (int k = 0; k < 4; k++) {
            ReachProps.marketCrates(new ReachBrush(canvas, 14 + k * 4, MARKET, 926, 0), 31 + k);
            ReachProps.cart(new ReachBrush(canvas, 16 + k * 4, MARKET, 962, 2), 77 + k);
        }
        ReachProps.trough(new ReachBrush(canvas, 27, MARKET, 952, 0));
        for (int x : new int[] {15, 27}) ReachProps.planter(new ReachBrush(canvas, x, MARKET, 970, 0), 3, x);
        for (int x = -33; x <= 33; x++) for (int z = z0 - 1; z <= z1 + 1; z++) reserve(x, z);
        marks.add(new Mark("market", 20, MARKET, 948, 180F));
    }

    /**
     * The Market Hall: an open arcade of stone piers with a timber-framed guild room above, under a big roof with a
     * bell cupola, the stalls of the covered market inside.
     */
    private void marketHall(int x0, int z0, int x1, int z1) {
        ReachKit.Style s = ReachKit.Style.HIGHCROWN;
        kit.site(new ReachBrush(canvas, x0, MARKET, z0, 0), 0, 0, x1 - x0, z1 - z0, 1, Material.STONE_BRICKS);
        int up = MARKET + 6;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean pier = edge && (Math.floorMod(x - x0, 4) <= 1 && (z == z0 || z == z1) || Math.floorMod(z - z0, 5) <= 1 && (x == x0 || x == x1));
            canvas.set(x, MARKET - 1, z, (x + z) % 3 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
            for (int y = MARKET; y < up; y++) {
                boolean archTop = edge && !pier && y >= up - 2;
                canvas.set(x, y, z, pier ? kitWeather(x, y, z) : archTop ? Material.STONE_BRICKS : Material.AIR);
            }
            if (edge && !pier) canvas.set(x, up - 3, z, Material.STONE_BRICK_STAIRS,
                x == x0 ? "facing=east,half=top" : x == x1 ? "facing=west,half=top" : z == z0 ? "facing=south,half=top" : "facing=north,half=top");
            canvas.set(x, up - 1, z, edge ? Material.STRIPPED_DARK_OAK_LOG : Material.SPRUCE_PLANKS, edge ? (z == z0 || z == z1 ? "axis=x" : "axis=z") : "");
            // The guild room: timber frame and plaster with windows, jettied one block out.
            for (int y = up; y < up + 5; y++) {
                if (!edge) { canvas.set(x, y, z, Material.AIR); continue; }
                boolean post = (x == x0 || x == x1) && (z == z0 || z == z1) || Math.floorMod(x - x0, 4) == 0 && (z == z0 || z == z1) || Math.floorMod(z - z0, 5) == 0 && (x == x0 || x == x1);
                boolean window = !post && (y == up + 1 || y == up + 2);
                canvas.set(x, y, z, post ? s.post() : window ? Material.GLASS_PANE : s.plaster(),
                    post ? "axis=y" : window ? (z == z0 || z == z1 ? "east=true,west=true" : "north=true,south=true") : "");
            }
        }
        // Stalls inside the arcade, and the hall's lanterns.
        for (int z = z0 + 3; z <= z1 - 6; z += 7) {
            ReachProps.marketCrates(new ReachBrush(canvas, x0 + 3, MARKET, z, 1), z);
            ReachProps.marketCrates(new ReachBrush(canvas, x1 - 3, MARKET, z + 2, 3), z + 1);
            canvas.set((x0 + x1) / 2, up - 2, z, Material.LANTERN, "hanging=true");
        }
        kit.roof(new ReachBrush(canvas, x0, up + 5, z0, 0), -1, x1 - x0 + 1, -1, z1 - z0 + 1, 0, s, true, true);
        // A bell cupola on the ridge.
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, ridge = up + 5 + (x1 - x0 + 3) / 2;
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) for (int y = ridge - 1; y <= ridge + 3; y++) {
            boolean corner = Math.abs(x - cx) == 1 && Math.abs(z - cz) == 1;
            canvas.set(x, y, z, y == ridge - 1 ? Material.SPRUCE_PLANKS : corner ? Material.DARK_OAK_LOG : Material.AIR, corner && y > ridge - 1 ? "axis=y" : "");
        }
        canvas.set(cx, ridge, cz, Material.BELL, "attachment=floor,facing=north");
        kit.spire(new ReachBrush(canvas, cx - 1, MARKET, cz - 1, 0), -1, 3, ridge + 4 - MARKET, s);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) reserve(x, z);
        lots.add(new int[] {x0 - 1, z0 - 1, x1 + 1, z1 + 1});
    }

    /** The market cross: a stepped base, a carved shaft and a lantern on top, where notices are cried. */
    private void marketCross(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int r = Math.max(Math.abs(dx), Math.abs(dz));
            if (r == 3) canvas.set(cx + dx, MARKET, cz + dz, Material.STONE_BRICK_SLAB, "type=bottom");
            else canvas.set(cx + dx, MARKET, cz + dz, Material.STONE_BRICKS);
            if (r <= 1) canvas.set(cx + dx, MARKET + 1, cz + dz, r == 1 ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS, r == 1 ? "type=bottom" : "");
        }
        for (int y = MARKET + 2; y <= MARKET + 5; y++) canvas.set(cx, y, cz, y == MARKET + 5 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICK_WALL, y == MARKET + 5 ? "" : "up=true");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(cx + d[0], MARKET + 5, cz + d[1], Material.STONE_BRICK_SLAB, "type=top");
        canvas.set(cx, MARKET + 6, cz, Material.LANTERN);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) reserve(cx + dx, cz + dz);
    }

    /**
     * The Market Ward's two ring streets round the hill and the houses between them, with Crafters' Row on the west
     * by the gorge. Rows: houses backing onto the Crown wall facing out, a ring street, houses facing in, houses
     * facing out onto the terrace walk, and the walk itself along the edge above the Dock Quarter.
     */
    private void marketWard() {
        double[][] alleys = {{68, 98, 137}, {150, 98, 137}};
        for (int x = -120; x <= 175; x++) for (int z = 834; z <= 1000; z++) {
            if (street(x, z) != MARKET || !onPlatform(x, z) || reserved.contains(key(x, z))) continue;
            double r = ring(x, z), a = angle(x, z);
            boolean ring1 = r >= 100 && r < 106.5, walk = r >= 137 && r < 145.5;
            boolean alley = false;
            for (double[] al : alleys) if (r >= al[1] && r <= al[2] && Math.abs(Math.toRadians(a - al[0]) * r) < 1.6) alley = true;
            if (ring1) street(x, z, MARKET, Math.abs(r - 103.2) / 3.4);
            else if (walk) street(x, z, MARKET, 0.6 + Math.abs(r - 141) / 12);
            else if (alley) street(x, z, MARKET, 0.4);
            else if (r >= 145.5 && r < 149 && !canvas.has(x, MARKET - 1, z)) {
                canvas.set(x, MARKET - 1, z, Material.GRASS_BLOCK);
                long h = ReachFlora.hash(x, z);
                if (h % 9 == 0) canvas.set(x, MARKET, z, Material.ROSE_BUSH, "half=lower");
                else if (h % 9 == 1) canvas.set(x, MARKET, z, Material.AZALEA);
                else if (h % 31 == 2) ReachTrees.fruit(canvas, x, MARKET, z, h);
                reserve(x, z);
            }
        }
        ReachKit.Style[] guild = {ReachKit.Style.HIGHCROWN, ReachKit.Style.HIGHCROWN, ReachKit.Style.EMBER, ReachKit.Style.SLATE};
        // East: Guild Street.
        arcRow(89, 98.5, 48, 92, true, MARKET, guild, 8100, Trade.NONE);
        arcRow(107.5, 119, 46, 92, false, MARKET, guild, 8200, Trade.NONE);
        arcRow(122.5, 136, 40, 92, true, MARKET, guild, 8300, Trade.NONE);
        // West: Crafters' Row along the gorge.
        ReachKit.Style[] crafts = {ReachKit.Style.VALE_SPRUCE, ReachKit.Style.HIGHCROWN, ReachKit.Style.VALE_SPRUCE};
        arcRow(107.5, 119, 128, 178, false, MARKET, crafts, 8400, Trade.SMITH);
        arcRow(89, 98.5, 130, 178, true, MARKET, crafts, 8500, Trade.WEAVER);
        arcRow(122.5, 136, 128, 178, true, MARKET, crafts, 8600, Trade.CARPENTER);
        // Side stairs down from the terrace walk to the Dock Quarter, east and west of the avenue.
        sideStair(78, 82, 984);
        sideStair(-64, -60, 952);
    }

    /** A narrower stair through the Market Ward's outer wall, south from the walk to the Dock Quarter. */
    private void sideStair(int x0, int x1, int zEdge) {
        int top = zEdge - 6;
        for (int z = top - 4; z <= top; z++) for (int x = x0; x <= x1; x++) if (onPlatform(x, z)) street(x, z, MARKET, 0.5);
        grandStair(x0, x1, top, MARKET, MARKET - DOCK);
    }

    /**
     * A row of houses along an arc of the Crown ring between radii {@code r0} and {@code r1}, from angle {@code from}
     * to {@code to} (degrees). Each house is square to the world but faces the street the row serves, out from the
     * hill or in toward it; lots that aren't level, aren't on this terrace or are taken are skipped.
     */
    private void arcRow(double r0, double r1, double from, double to, boolean faceOut, int level, ReachKit.Style[] styles, long seed, Trade trade) {
        double rm = (r0 + r1) / 2;
        double t = Math.toRadians(from), end = Math.toRadians(to);
        int n = 0;
        while (t < end && n < 400) {
            long h = ReachFlora.hash((int) seed + n * 37, (int) (t * 1000));
            int w = 7 + (int) Math.floorMod(h, 3L), d = (int) Math.min(r1 - r0, 9 + ((h >> 4) & 1));
            double a = t + (w / 2.0) / rm, ux = Math.cos(a), uz = Math.sin(a);
            int turn = turnFacing(faceOut ? ux : -ux, faceOut ? uz : -uz);
            double rc = faceOut ? r1 - d / 2.0 : r0 + d / 2.0;
            double px = CX + rc * ux, pz = CZ + rc * uz;
            int sx = (turn & 1) == 0 ? w : d, sz = (turn & 1) == 0 ? d : w;
            int x0 = (int) Math.round(px - sx / 2.0), z0 = (int) Math.round(pz - sz / 2.0), x1 = x0 + sx - 1, z1 = z0 + sz - 1;
            n++;
            Trade kind = trade == Trade.NONE || trade == Trade.WAREHOUSE ? trade : Trade.values()[1 + (int) Math.floorMod(h >> 7, 5L)];
            if (tryHouse(x0, z0, x1, z1, turn, level, styles[(int) Math.floorMod(h >> 9, (long) styles.length)], 1 + (int) Math.floorMod(h >> 11, 2L), h, kind))
                t += (w + 1.0) / rm;
            else t += 1.5 / rm;
        }
    }

    private static int turnFacing(double fx, double fz) {
        if (Math.abs(fx) > Math.abs(fz)) return fx > 0 ? 1 : 3;
        return fz > 0 ? 2 : 0;
    }

    /** A brush for a building whose world footprint is x0..x1, z0..z1 with its front on the side {@code turn} faces. */
    private ReachBrush lotBrush(int x0, int z0, int x1, int z1, int turn, int y) {
        return switch (turn) {
            case 0 -> new ReachBrush(canvas, x0, y, z0, 0);
            case 1 -> new ReachBrush(canvas, x1, y, z0, 1);
            case 2 -> new ReachBrush(canvas, x1, y, z1, 2);
            default -> new ReachBrush(canvas, x0, y, z1, 3);
        };
    }

    /** Width along the front and depth back from it. */
    private static int[] lotSize(int x0, int z0, int x1, int z1, int turn) {
        int sx = x1 - x0 + 1, sz = z1 - z0 + 1;
        return (turn & 1) == 0 ? new int[] {sx, sz} : new int[] {sz, sx};
    }

    /**
     * True where a lot and a margin round it lie on this terrace and near its level (the site grading cuts or fills
     * up to a few blocks and walls the difference), away from the lip and the gorge, and the lot itself is free.
     */
    private boolean flatLot(int x0, int z0, int x1, int z1, int level) {
        for (int x = x0 - 2; x <= x1 + 2; x++) for (int z = z0 - 2; z <= z1 + 2; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.river() < 5 || c.edge() < 6 || street(x, z) != level || Math.abs(c.top() - (level - 1)) > 3) return false;
            boolean core = x >= x0 && x <= x1 && z >= z0 && z <= z1;
            if (core && reserved.contains(key(x, z))) return false;
        }
        for (int[] l : lots) if (x0 <= l[2] && x1 >= l[0] && z0 <= l[3] && z1 >= l[1]) return false;
        return true;
    }

    /** Builds a house (or a workshop) on a lot if it's level and free; marks its doorstep. */
    private boolean tryHouse(int x0, int z0, int x1, int z1, int turn, int level, ReachKit.Style style, int floors, long seed, Trade trade) {
        if (!flatLot(x0, z0, x1, z1, level)) return false;
        lots.add(new int[] {x0 - 1, z0 - 1, x1 + 1, z1 + 1});
        int[] size = lotSize(x0, z0, x1, z1, turn);
        ReachBrush b = lotBrush(x0, z0, x1, z1, turn, level);
        boolean warehouse = trade == Trade.WAREHOUSE;
        boolean gable = warehouse || (seed >> 15 & 1) == 0;
        if (warehouse) floors = 2;
        boolean jetty = floors > 1 || (seed & 32) == 0;
        kit.house(b, size[0], size[1], floors, style, jetty, gable, seed);
        int door = size[0] / 2;
        if (warehouse) {
            // A hoist beam out of the gable with its chain and a load of cargo hanging over the loading balcony.
            int front = jetty ? -1 : 0, top = 4 + floors * 4, apex = top + (size[0] + 2) / 2, beam = apex - 2;
            for (int k = 0; k <= 3; k++) b.set(door, beam, front - k, Material.STRIPPED_SPRUCE_LOG, "axis=z");
            for (int y = beam - 1; y >= top - 1; y--) b.set(door, y, front - 3, Material.IRON_CHAIN, "axis=y");
            b.set(door, top - 2, front - 3, (seed & 1) == 0 ? Material.BARREL : Material.HAY_BLOCK, (seed & 1) == 0 ? "facing=up" : "axis=y");
            ReachProps.crates(b.at(1, 0, -2, 0), seed);
            ReachProps.cart(b.at(size[0] - 2, 0, -3, 0), seed >> 3);
        }
        switch (trade) {
            case SMITH -> {
                ReachProps.smithYard(b.at(size[0] - 3, 0, -4, 0));
                ReachKit.shopSign(b, door + 2, 2, Material.DARK_OAK_WALL_HANGING_SIGN);
            }
            case WEAVER -> {
                b.set(1, 0, 1, Material.LOOM, "facing=south");
                for (int k = 0; k < 2; k++) b.set(size[0] - 2 - k, 0, -2, k == 0 ? Material.LIGHT_BLUE_WOOL : Material.MAGENTA_WOOL);
                ReachKit.shopSign(b, door + 2, 2, Material.SPRUCE_WALL_HANGING_SIGN);
            }
            case ALCHEMIST -> {
                b.set(1, 0, 1, Material.BREWING_STAND, "has_bottle_0=true,has_bottle_1=false,has_bottle_2=true");
                b.set(size[0] - 2, 0, -2, Material.WATER_CAULDRON, "level=2");
                ReachKit.shopSign(b, door + 2, 2, Material.CRIMSON_WALL_HANGING_SIGN);
            }
            case CARPENTER -> {
                ReachProps.crates(b.at(size[0] - 2, 0, -2, 0), seed);
                b.set(size[0] - 1, 0, -3, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                ReachKit.shopSign(b, door + 2, 2, Material.OAK_WALL_HANGING_SIGN);
            }
            case TANNER -> {
                ReachProps.crates(b.at(size[0] - 2, 0, -2, 0), seed);
                ReachKit.shopSign(b, door + 2, 2, Material.SPRUCE_WALL_HANGING_SIGN);
            }
            default -> { if (!warehouse && (seed & 64) == 0) ReachProps.crates(b.at(size[0] - 2, 0, -2, 0), seed); }
        }
        if (trade != Trade.NONE && !warehouse && crafterCount < 5) {
            marks.add(new Mark("crafter_" + crafterCount++, b.wx(door, -2), level, b.wz(door, -2), yawFacing(turn)));
        }
        return true;
    }

    /** The yaw of a person standing in front of a door, facing the street (away from the door). */
    private static float yawFacing(int turn) {
        return switch (turn) { case 0 -> 180F; case 1 -> 270F; case 2 -> 0F; default -> 90F; };
    }

    private void waterwheel(int cx, int cz) {
        ReachTerrain.Column c = terrain.column(cx, cz);
        int water = c.wet() ? c.water() : 91;
        int axleY = water + 4;
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int y = axleY + (int) Math.round(5 * Math.sin(rad)), z = cz + (int) Math.round(5 * Math.cos(rad));
            canvas.set(cx, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=x");
            canvas.set(cx + 1, y, z, Material.SPRUCE_TRAPDOOR, "facing=east,half=bottom,open=true");
        }
        for (int r = 0; r < 5; r++) {
            canvas.set(cx, axleY + r, cz, Material.SPRUCE_FENCE);
            canvas.set(cx, axleY - r, cz, Material.SPRUCE_FENCE);
            canvas.set(cx, axleY, cz + r, Material.SPRUCE_FENCE);
            canvas.set(cx, axleY, cz - r, Material.SPRUCE_FENCE);
        }
        for (int x = cx; x <= cx + 8; x++) canvas.set(x, axleY, cz, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
    }

    // ------------------------------------------------------------------ the Dock Quarter

    private void dockSquare() {
        for (int x = -30; x <= 30; x++) for (int z = DOCK_SQUARE_Z; z <= DOCK_SQUARE_Z + 40; z++) if (onPlatform(x, z) && !reserved.contains(key(x, z)))
            street(x, z, DOCK, 0.5 + 0.5 * Math.sin(x / 4.0) * Math.sin(z / 4.0));
        int mz = DOCK_SQUARE_Z + 16;
        for (int x = -9; x <= 9; x++) for (int z = mz - 9; z <= mz + 9; z++) {
            double r = Math.hypot(x, z - mz);
            if (r > 9.4) continue;
            double a = Math.atan2(z - mz, x);
            boolean point = Math.abs(Math.sin(a * 4)) < 0.18 && r > 3;
            canvas.set(x, DOCK - 1, z, r > 8.5 ? Material.POLISHED_DEEPSLATE : point ? Material.SMOOTH_STONE : r < 3.4 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            canvas.set(dx, DOCK, mz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "type=bottom" : "");
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) canvas.set(dx, DOCK + 1, mz + dz, Material.STONE_BRICKS);
        }
        for (int y = DOCK + 2; y < DOCK + 7; y++) canvas.set(0, y, mz, y % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
        canvas.set(0, DOCK + 7, mz, Material.CHISELED_STONE_BRICKS);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(d[0], DOCK + 7, mz + d[1], Material.OXIDIZED_COPPER);
        canvas.set(0, DOCK + 8, mz, Material.LIGHTNING_ROD);
        for (int px : new int[] {-20, 20}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                canvas.set(px + dx, DOCK, mz + dz, rim ? Material.STONE_BRICKS : Material.GRASS_BLOCK);
                if (rim) canvas.set(px + dx, DOCK + 1, mz + dz, Material.STONE_BRICK_SLAB, "type=bottom");
            }
            ReachTrees.oak(canvas, px, DOCK + 1, mz, ReachFlora.hash(px, mz));
            for (int bx = -1; bx <= 1; bx += 2) ReachProps.lamp(new ReachBrush(canvas, px + bx * 4, DOCK, mz + 4, 0), Material.SPRUCE_LOG, Material.SPRUCE_TRAPDOOR);
        }
        for (int k = 0; k < 6; k++) {
            int cx = -28 + (k % 3) * 3, cz = DOCK_SQUARE_Z + 34 + (k / 3) * 2;
            kit.clutter(new ReachBrush(canvas, cx, DOCK, cz, k & 3), k);
            kit.clutter(new ReachBrush(canvas, 26 - (k % 3) * 3, DOCK, cz, (k + 2) & 3), k + 9);
        }
        ReachProps.cart(new ReachBrush(canvas, -14, DOCK, DOCK_SQUARE_Z + 36, 1), 5);
        ReachProps.cart(new ReachBrush(canvas, 14, DOCK, DOCK_SQUARE_Z + 37, 3), 6);
        // The quay along the lip, and three lanes from the square down to the piers between the quayside houses.
        for (int x = -66; x <= 66; x++) for (int z = DOCK_SQUARE_Z + 41; z <= 1094; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.edge() <= 2 || reserved.contains(key(x, z))) continue;
            boolean lane = Math.abs(x) <= 5 || Math.abs(Math.abs(x) - 24) <= 5;
            if (z >= 1068 || lane) street(x, z, DOCK, lane && z < 1068 ? Math.abs(x % 24) / 6.0 : 0.6);
        }
        quayside();
        // The Tethered Gull: a wide inn on the east side of the square, its front on the square.
        ReachBrush gull = new ReachBrush(canvas, 34, DOCK, DOCK_SQUARE_Z + 16, 3);
        kit.house(gull, 15, 13, 2, ReachKit.Style.HARBOUR, true, false, 77);
        ReachKit.shopSign(gull, 9, 2, Material.SPRUCE_WALL_HANGING_SIGN);
        lots.add(new int[] {31, DOCK_SQUARE_Z, 48, DOCK_SQUARE_Z + 18});
        for (int x = 31; x <= 48; x++) for (int z = DOCK_SQUARE_Z; z <= DOCK_SQUARE_Z + 18; z++) reserve(x, z);
        marks.add(new Mark("tethered_gull", 30, DOCK, DOCK_SQUARE_Z + 9, 90F));
        marks.add(new Mark("dock_square", 0, DOCK, DOCK_SQUARE_Z + 20, 180F));
        for (int i = 0; i < 3; i++) marks.add(new Mark("arrival_" + i, -12 + i * 12, DOCK, DOCK_SQUARE_Z + 30, 0F));
        marks.add(new Mark("memorial", -24, DOCK, DOCK_SQUARE_Z + 24, 270F));
    }

    /**
     * The quayside: chandlers, a fish market and sailors' lodgings facing the quay between the lanes, mooring bollards
     * along the edge, cargo stacked by each pier and lamps along the front.
     */
    private void quayside() {
        ReachKit.Style[] harbour = {ReachKit.Style.HARBOUR, ReachKit.Style.EMBER, ReachKit.Style.HARBOUR};
        int[][] blocks = {{-62, -31}, {-17, -7}, {7, 17}, {31, 62}};
        int n = 0;
        // Two rows back to back in each block: one facing the quay, one facing the dock square.
        for (int[] row : new int[][] {{1055, 1066, 2}, {1043, 1053, 0}}) for (int[] bl : blocks) for (int x = bl[0]; x + 6 <= bl[1]; ) {
            long h = ReachFlora.hash(x * 13 + 7, row[0]);
            int w = Math.min(bl[1] - x + 1, 8 + (int) Math.floorMod(h, 3L));
            if (w < 7) break;
            boolean built = tryHouse(x, row[0], x + w - 1, row[1], row[2], DOCK, harbour[n++ % harbour.length], 1 + (int) Math.floorMod(h >> 5, 2L), h, Trade.NONE);
            x += built ? w + 1 : 2;
        }
        for (int x = -60; x <= 60; x++) {
            int z = 1070;
            if (Math.floorMod(x, 12) == 6 && isAir(x, DOCK, z) && !nearDoor(x, DOCK, z)) ReachProps.lamp(new ReachBrush(canvas, x, DOCK, z, 2), Material.SPRUCE_LOG, Material.SPRUCE_TRAPDOOR);
        }
        crane(-36, 1074);
        crane(34, 1074);
        // Bollards on the quay's edge, cargo by the piers, a fish stall at each pier's root.
        for (int x = -64; x <= 64; x += 5) {
            int z = 1094;
            while (z > 1070 && !(terrain.column(x, z).land() && terrain.column(x, z).edge() > 2)) z--;
            if (Math.abs(Math.abs(x) - 24) <= 3 || Math.abs(x) <= 3 || !isAir(x, DOCK, z)) continue;
            canvas.set(x, DOCK, z, Material.POLISHED_DEEPSLATE_WALL, "up=true");
            canvas.set(x, DOCK + 1, z, Material.POLISHED_DEEPSLATE_SLAB, "type=bottom");
        }
        for (int px : new int[] {-24, 0, 24}) {
            ReachProps.crates(new ReachBrush(canvas, px - 8, DOCK, 1074, 0), px + 3);
            ReachProps.marketCrates(new ReachBrush(canvas, px + 6, DOCK, 1075, 0), px + 4);
            if (px != 0) kit.stall(new ReachBrush(canvas, px + 7, DOCK, 1071, 0), Material.BLUE_WOOL, Material.WHITE_WOOL, Material.DRIED_KELP_BLOCK, ReachKit.Style.HARBOUR);
        }
    }

    /** A timber jib crane on the quay, its chain hanging over the edge with a crate on the hook. */
    private void crane(int x, int z) {
        for (int y = DOCK; y < DOCK + 12; y++) {
            canvas.set(x, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=y");
            if (y < DOCK + 3) for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(x + d[0], y, z + d[1], Material.SPRUCE_PLANKS);
        }
        for (int k = 1; k <= 9; k++) canvas.set(x, DOCK + 11, z + k, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        for (int k = 1; k <= 4; k++) canvas.set(x, DOCK + 11 - k, z + k, Material.SPRUCE_FENCE);
        for (int y = DOCK + 5; y < DOCK + 11; y++) canvas.set(x, y, z + 9, Material.IRON_CHAIN, "axis=y");
        canvas.set(x, DOCK + 4, z + 9, Material.BARREL, "facing=up");
    }

    /** Three piers over the cloud sea, the Dockmaster's Tower and the Guildhall, and two moored airships. */
    private void docks() {
        int lip = 1094;
        for (int px : new int[] {-24, 0, 24}) {
            for (int z = lip - 12; z <= lip + 52; z++) for (int x = px - 2; x <= px + 2; x++) {
                canvas.set(x, DOCK - 1, z, Math.abs(x - px) == 2 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS, Math.abs(x - px) == 2 ? "axis=z" : "");
                for (int y = DOCK; y < DOCK + 4; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
            }
            for (int z = lip - 8; z <= lip + 52; z += 6) for (int x : new int[] {px - 2, px + 2}) {
                canvas.set(x, DOCK, z, Material.SPRUCE_FENCE);
                canvas.set(x, DOCK + 1, z, z % 12 == 0 ? Material.LANTERN : Material.SPRUCE_FENCE);
                for (int k = 1; k <= 6 && z - k * 2 > lip - 14; k++) canvas.set(x, DOCK - 1 - k, z - k * 2 + 1, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                canvas.set(x, DOCK - 2, z, Material.IRON_CHAIN, "axis=y");
                canvas.set(x, DOCK - 3, z, Material.IRON_CHAIN, "axis=y");
            }
            kit.clutter(new ReachBrush(canvas, px - 1, DOCK, lip + 48, 0), px);
            marks.add(new Mark("pier_" + (px < 0 ? "west" : px > 0 ? "east" : "mid"), px, DOCK, lip + 4, 0F));
        }
        kit.tower(new ReachBrush(canvas, -48, DOCK, lip - 18, 0), 9, 36, ReachKit.Style.HARBOUR, 41);
        kit.tower(new ReachBrush(canvas, 40, DOCK, lip - 18, 0), 9, 32, ReachKit.Style.HARBOUR, 42);
        marks.add(new Mark("dockmaster", -44, DOCK, lip - 21, 180F));
        marks.add(new Mark("guildhall", 44, DOCK, lip - 21, 180F));
        net.nerdsmp.paper.expedition.BuildCanvas ships = new net.nerdsmp.paper.expedition.BuildCanvas();
        net.nerdsmp.paper.expedition.SkyShip.moor(ships, 0, 0, 0);
        stamp(ships, new ReachBrush(canvas, -12, DOCK + 1, lip + 34, 1));
        stamp(ships, new ReachBrush(canvas, 12, DOCK + 1, lip + 30, 1));
        for (int x = -60; x <= 60; x++) for (int z = lip - 24; z <= lip + 52; z++) reserve(x, z);
    }

    private static void stamp(net.nerdsmp.paper.expedition.BuildCanvas source, ReachBrush at) {
        source.blocks().forEach((cell, spec) -> at.set(cell.x(), cell.y(), cell.z(), spec.material(), spec.properties()));
    }

    /** Warehouses backing onto the Market Ward's wall, facing a dock street, under red and dark roofs. */
    private void dockQuarter() {
        for (int x = -120; x <= 175; x++) for (int z = 900; z <= 1080; z++) {
            if (street(x, z) != DOCK || !onPlatform(x, z) || reserved.contains(key(x, z))) continue;
            double r = ring(x, z);
            if (r >= 165 && r < 171.5) street(x, z, DOCK, Math.abs(r - 168.2) / 3.4);
        }
        ReachKit.Style[] docks = {ReachKit.Style.EMBER, ReachKit.Style.HARBOUR, ReachKit.Style.EMBER};
        arcRow(153, 164.5, 18, 80, true, DOCK, docks, 8700, Trade.WAREHOUSE);
        arcRow(153, 164.5, 110, 150, true, DOCK, docks, 8800, Trade.WAREHOUSE);
        arcRow(172.5, 184, 30, 80, false, DOCK, docks, 8900, Trade.NONE);
        arcRow(172.5, 184, 108, 150, false, DOCK, docks, 8950, Trade.NONE);
        // Houses on the lip itself, facing out over the cloud sea, their balconies hanging over the void.
        arcRow(188, 199, 40, 80, true, DOCK, docks, 9050, Trade.NONE);
        arcRow(188, 199, 104, 140, true, DOCK, docks, 9150, Trade.NONE);
    }

    /**
     * Trees where the city has room for them: the keep's crag, lawns, the Fane garden and the west bank across the
     * gorge, never over a street or close enough to a building for a crown to cut into it.
     */
    private void cityGreen() {
        final int cell = 10;
        for (int gx = -21; gx <= 18; gx++) for (int gz = 83; gz <= 108; gz++) {
            long h = ReachFlora.hash(gx * 7919 + 31, gz * 104729 - 17);
            int x = gx * cell + (int) Math.floorMod(h, (long) cell), z = gz * cell + (int) Math.floorMod(h >> 8, (long) cell);
            if (Math.hypot(x, z - 905) > 170) continue;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.river() < 4 || c.edge() < 8) continue;
            if (!clearForTree(x, c.top() + 1, z)) continue;
            double toKeep = Math.hypot(x - 112, z - 868);
            boolean crag = toKeep > 38 && toKeep < 64;
            int roll = (int) Math.floorMod(h >> 16, 10L);
            canvas.set(x, c.top(), z, Material.GRASS_BLOCK);
            if (crag && roll < 3) ReachTrees.tallSpruce(canvas, x, c.top() + 1, z, 11 + roll * 2, h);
            else if (roll < 6) ReachTrees.oak(canvas, x, c.top() + 1, z, h);
            else if (roll < 9) ReachTrees.fruit(canvas, x, c.top() + 1, z, h);
            else for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(dx) + Math.abs(dz) < 2 && clearForTree(x + dx, c.top() + 1, z + dz)) canvas.set(x + dx, terrain.column(x + dx, z + dz).top() + 1, z + dz, (h >> dx + 3 & 1) == 0 ? Material.MOSSY_COBBLESTONE : Material.ANDESITE);
        }
    }

    /** True where nothing is built within six blocks of a spot, from its foot up a tree's height, and it isn't a street. */
    private boolean clearForTree(int x, int y, int z) {
        for (int dx = -6; dx <= 6; dx += 2) for (int dz = -6; dz <= 6; dz += 2) {
            if (reserved.contains(key(x + dx, z + dz)) && Math.abs(dx) <= 2 && Math.abs(dz) <= 2) return false;
            for (int dy = 0; dy <= 12; dy += 2) if (!isAir(x + dx, y + dy, z + dz)) return false;
        }
        for (int[] l : lots) if (x >= l[0] - 4 && x <= l[2] + 4 && z >= l[1] - 4 && z <= l[3] + 4) return false;
        return true;
    }

    // ------------------------------------------------------------------ terraces and street life

    /**
     * Faces every step between terraces with a retaining wall: weathered stone down the face, buttresses on the low
     * side, a parapet along the top with a lamp now and then. Stairs and buildings keep their own edges.
     */
    private void terraceWalls() {
        Map<Long, Integer> parapet = new HashMap<>();
        for (int x = -200; x <= 200; x++) for (int z = 834; z <= 1080; z++) {
            int top = ReachTerrain.highcrownTop(x, z);
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.top() != top || reserved.contains(key(x, z)) && !streets.containsKey(key(x, z))) continue;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], nz = z + d[1];
                int low = ReachTerrain.highcrownTop(nx, nz);
                if (low >= top) continue;
                ReachTerrain.Column n = terrain.column(nx, nz);
                if (!n.land() || n.wet() || n.top() != low) continue;
                if (reserved.contains(key(nx, nz)) && !streets.containsKey(key(nx, nz))) continue;
                for (int y = low + 1; y <= top; y++) if (!canvas.has(x, y, z) || isAir(x, y, z) && y < top) canvas.set(x, y, z, kitWeather(x, y, z));
                canvas.set(x, top, z, Material.STONE_BRICKS);
                if (isAir(x, top + 1, z)) {
                    long h = ReachFlora.hash(x * 3, z * 5);
                    if (h % 29 == 0) lampOn(x, top + 1, z, 0);
                    else runs.wall(x, top + 1, z, Material.STONE_BRICK_WALL);
                    parapet.put(key(x, z), top);
                }
                // A buttress on the low side every so often, its top stepped.
                if (Math.floorMod(x * 7 + z * 13, 11) == 0 && isAir(nx, low + 1, nz) && !reserved.contains(key(nx, nz))) {
                    for (int y = low + 1; y <= top - 2; y++) canvas.set(nx, y, nz, Material.STONE_BRICKS);
                    String face = d[0] == 1 ? "west" : d[0] == -1 ? "east" : d[1] == 1 ? "north" : "south";
                    canvas.set(nx, top - 1, nz, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=bottom");
                } else if (Math.floorMod(ReachFlora.hash(x * 5 + 1, z * 3 - 2), 4L) == 0 && !reserved.contains(key(nx, nz))) {
                    // Ivy hanging down the face from the parapet, a few blocks long.
                    String side = d[0] == 1 ? "west" : d[0] == -1 ? "east" : d[1] == 1 ? "north" : "south";
                    int length = 2 + (int) Math.floorMod(ReachFlora.hash(z, x), 4L);
                    for (int y = top; y > top - length && y > low + 1; y--) if (isAir(nx, y, nz)) canvas.set(nx, y, nz, Material.VINE, side + "=true");
                }
                break;
            }
        }
        // An arc's edge steps diagonally; close each diagonal step with a post on the high side so the parapet runs on.
        for (Map.Entry<Long, Integer> e : new ArrayList<>(parapet.entrySet())) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue(), top = e.getValue();
            for (int[] d : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                if (!parapet.containsKey(key(x + d[0], z + d[1])) || parapet.containsKey(key(x + d[0], z)) || parapet.containsKey(key(x, z + d[1]))) continue;
                for (int[] fill : new int[][] {{x + d[0], z}, {x, z + d[1]}}) {
                    ReachTerrain.Column c = terrain.column(fill[0], fill[1]);
                    if (ReachTerrain.highcrownTop(fill[0], fill[1]) != top || c.top() != top || !isAir(fill[0], top + 1, fill[1])) continue;
                    if (reserved.contains(key(fill[0], fill[1])) && !streets.containsKey(key(fill[0], fill[1]))) continue;
                    runs.wall(fill[0], top + 1, fill[1], Material.STONE_BRICK_WALL);
                    parapet.put(key(fill[0], fill[1]), top);
                    break;
                }
            }
        }
    }

    /** True within two blocks of a door, a gate or a step, so props never block a way in. */
    private boolean nearDoor(int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++) {
            ReachCanvas.Spec s = canvas.get(x + dx, y + dy, z + dz);
            if (s != null && (s.state().contains("_door") || s.state().contains("_gate") || s.state().contains("_stairs"))) return true;
        }
        return false;
    }

    /** Lamps along the ring streets and walks, and carts, crates, troughs and benches where there's room. */
    private void streetLife() {
        int i = 0;
        for (Map.Entry<Long, double[]> e : streets.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue(), y = (int) e.getValue()[0];
            if (e.getValue()[1] < 0.85) continue;
            long h = ReachFlora.hash(x * 11, z * 7);
            if (Math.floorMod(h, 19L) != 0) continue;
            if (!isAir(x, y, z) || !isAir(x, y + 1, z) || nearDoor(x, y, z)) continue;
            // Only on the edge of a street: some neighbour is a building or a wall, not more street.
            int turn = -1;
            int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            for (int k = 0; k < 4; k++) {
                long nk = key(x + dirs[k][0], z + dirs[k][1]);
                if (!streets.containsKey(nk) && canvas.has(x + dirs[k][0], y, z + dirs[k][1]) && !isAir(x + dirs[k][0], y, z + dirs[k][1])) { turn = (k + 2) & 3; break; }
            }
            if (turn < 0) continue;
            ReachBrush b = new ReachBrush(canvas, x, y, z, turn);
            switch (i++ % 6) {
                case 0 -> ReachProps.lamp(b, Material.DARK_OAK_LOG, Material.SPRUCE_TRAPDOOR);
                case 1 -> ReachProps.crates(b, h);
                case 2 -> ReachProps.bench(b, Material.SPRUCE_STAIRS, Material.SPRUCE_TRAPDOOR);
                case 3 -> ReachProps.cart(b, h);
                case 4 -> ReachProps.lamp(b, Material.DARK_OAK_LOG, Material.SPRUCE_TRAPDOOR);
                default -> ReachProps.trough(b);
            }
        }
        // The Watch walks the avenue from the plaza down to the docks.
        int[][] route = {{-8, 852}, {-8, 893}, {-3, 930}, {-3, 965}, {-3, 995}, {-3, 1030}};
        for (int k = 0; k < route.length; k++) marks.add(new Mark("watch_" + k, route[k][0], street(route[k][0], route[k][1]), route[k][1], 0F));
    }
}
