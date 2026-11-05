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

    /** A business that takes the next house a row builds: who keeps it, what it's for, and the sign at its door. */
    private record Venue(String npc, ReachInteriors.Use use, ReachInteriors.Trade trade, Material sign, String... text) { }

    /** The venues still waiting for a house in the rows being built now; null builds plain homes. */
    private java.util.Deque<Venue> venues;

    private static java.util.Deque<Venue> queue(Venue... v) { return new java.util.ArrayDeque<>(List.of(v)); }

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
        city.westWall();
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
        city.waymarks();
        city.roundCorners();
        city.terraceWalls();
        city.streetLife();
        city.cityGreen();
        city.relayDoorsteps();
        city.doorPaths();
        city.lipParapet();
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
    /** Joins every door to the streets: a lane of the city's paving from each doorstep that doesn't already meet one. */
    private void doorPaths() {
        ReachPaths.Ground ground = new ReachPaths.Ground(canvas, terrain, new HashMap<>());
        ReachPaths.connectDoors(canvas, terrain,
            (x, y, z) -> {
                double[] s = streets.get(key(x, z));
                return s != null && Math.abs((int) s[0] - y) <= 1 || ground.floor(x, y, z).equals("road");
            },
            (x, y, z) -> { if (ReachPaths.paintable(ground.floor(x, y, z))) street(x, z, y, 0.85); },
            1500);
    }

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
        // The city's banners on tall poles down both sides of the avenue in the Crown Ward.
        for (int z = GATE_Z + 50; z < STAIR_A - 4; z += 10) for (int x : new int[] {-AVENUE_HALF - 2, AVENUE_HALF + 2}) {
            if (!onPlatform(x, z) || x > 0 && z == HighcrownCathedral.AX) continue;
            canvas.set(x, CROWN, z, Material.STONE_BRICK_WALL, "up=true");
            for (int y = CROWN + 1; y <= CROWN + 5; y++) canvas.set(x, y, z, Material.DARK_OAK_FENCE);
            canvas.set(x, CROWN + 6, z, Material.GREEN_BANNER, "rotation=" + (x < 0 ? 4 : 12));
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

    /**
     * The Kingsbridge and the Walk of Wardens. A broad stone bridge carries the Kingsroad over the gorge to the gate
     * on arches springing from deep piers. On three bastions down each side, standing out over the gorge, the
     * founding Wardens of the Reach stand in stone on lettered pedestals, facing the road, so every traveller walks
     * between them to the Kingsgate. An arch at the north end names the city.
     */
    private void kingsbridge() {
        int z0 = 764, z1 = GATE_Z - 4, half = 7, y0 = CROWN, spanLen = 12;
        for (int z = z0; z <= z1; z++) {
            for (int x = -half; x <= half; x++) {
                double p = terrain.patch(x, z, 0.3);
                canvas.set(x, y0 - 1, z, Math.abs(x) <= 1 ? Material.POLISHED_ANDESITE : Math.abs(x) == half ? Material.CHISELED_STONE_BRICKS
                    : p > 0.45 ? Material.COBBLESTONE : p < -0.5 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                for (int y = y0; y < y0 + 5; y++) canvas.set(x, y, z, Material.AIR);
                reserve(x, z);
            }
            for (int x : new int[] {-half - 1, half + 1}) {
                canvas.set(x, y0 - 1, z, Material.STONE_BRICKS);
                canvas.set(x, y0, z, Material.STONE_BRICKS);
                runs.wall(x, y0 + 1, z, Material.STONE_BRICK_WALL);
                reserve(x, z);
            }
            int span = (z - z0) % spanLen;
            double arch = Math.sin(Math.PI * span / spanLen);
            int soffit = y0 - 2 - (int) Math.round(9 * (1 - arch));
            for (int x = -half - 1; x <= half + 1; x++) for (int y = soffit; y < y0 - 1; y++)
                canvas.set(x, y, z, y == soffit || Math.abs(x) == half + 1 ? Material.STONE_BRICKS : Material.STONE);
            // A string course of upside-down stairs along both faces under the parapet.
            for (int x : new int[] {-half - 2, half + 2}) canvas.set(x, y0 - 1, z, Material.STONE_BRICK_STAIRS, x < 0 ? "facing=east,half=top" : "facing=west,half=top");
            if (span == 0) {
                for (int x = -half - 2; x <= half + 2; x++) for (int dz = -2; dz <= 2; dz++) {
                    ReachTerrain.Column c = terrain.column(x, z + dz);
                    int floor = c.land() ? c.top() : y0 - 34;
                    for (int y = floor; y < y0 - 1; y++) canvas.set(x, y, z + dz, y < y0 - 18 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    if (c.wet()) for (int y = floor; y <= c.water() + 1; y++) for (int ex : new int[] {-half - 4, half + 4}) canvas.set(ex, y, z + dz, Material.MOSSY_STONE_BRICKS);
                }
                // Lamps on the parapet over each pier, except where a bastion opens: there the statue's plaque must show.
                if (z != 776 && z != 788 && z != 800) for (int x : new int[] {-half - 1, half + 1}) {
                    canvas.set(x, y0 + 1, z, Material.STONE_BRICKS);
                    canvas.set(x, y0 + 2, z, Material.CHISELED_STONE_BRICKS);
                    canvas.set(x, y0 + 3, z, Material.LANTERN);
                    runs.remove(x, y0 + 1, z);
                }
            }
        }
        // The Walk of Wardens: three bastions a side over the piers, each carrying a founding Warden.
        Object[][] wardens = {
            {-1, 776, ReachStatues.Pose.LANTERN, ReachStatues.Palette.MARBLE, new String[] {"MAELIS", "the Lanternbearer", "who lit the way", "to the Reach"}},
            {1, 776, ReachStatues.Pose.SWORD_PLANTED, ReachStatues.Palette.GRANITE, new String[] {"CORWEN", "First Admiral", "of the Sky", "Fleets"}},
            {-1, 788, ReachStatues.Pose.HAMMER_SHIELD, ReachStatues.Palette.GRANITE, new String[] {"TOBREN", "Ironhand", "who raised", "the Kingsgate"}},
            {1, 788, ReachStatues.Pose.SPEAR, ReachStatues.Palette.MARBLE, new String[] {"HESTA", "of Goldenreach", "who held", "the Vale"}},
            {-1, 800, ReachStatues.Pose.BOW, ReachStatues.Palette.MARBLE, new String[] {"YSOLDE", "of the Gale", "Warden of", "the Heights"}},
            {1, 800, ReachStatues.Pose.STAFF, ReachStatues.Palette.GRANITE, new String[] {"VARN", "the Lorekeeper", "who kept", "the Archive"}}};
        for (Object[] w : wardens) {
            int side = (Integer) w[0], pz = (Integer) w[1];
            bastion(side, pz, y0, half);
            ReachBrush at = new ReachBrush(canvas, side * 13, y0, pz, side < 0 ? 1 : 3);
            int top = ReachStatues.pedestal(at, 3, 6, (String[]) w[4]);
            ReachStatues.hero(at.at(0, top + 1, 0, 0), 1.5, (ReachStatues.Pose) w[2], (ReachStatues.Palette) w[3]);
        }
        wardensArch(z0 - 6, y0);
        marks.add(new Mark("kingsbridge", 0, y0, 790, 180F));
    }

    /** A bastion standing out from the bridge over a pier: a paved platform with a parapet, carried on stepped corbels. */
    private void bastion(int side, int pz, int y0, int half) {
        for (int k = half + 2; k <= 17; k++) for (int z = pz - 5; z <= pz + 5; z++) {
            int x = side * k;
            boolean edge = k == 17 || Math.abs(z - pz) == 5;
            canvas.set(x, y0 - 1, z, edge ? Material.STONE_BRICKS : (Math.abs(z - pz) + k) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
            for (int y = y0; y < y0 + 4; y++) canvas.set(x, y, z, Material.AIR);
            if (edge) { canvas.set(x, y0, z, Material.STONE_BRICKS); runs.wall(x, y0 + 1, z, Material.STONE_BRICK_WALL); }
            // Corbels: the platform's underside steps back toward the pier.
            int depth = 2 + Math.max(0, 5 - Math.abs(z - pz)) + Math.max(0, 17 - k) / 2;
            for (int y = y0 - 1 - depth; y < y0 - 1; y++) canvas.set(x, y, z, y == y0 - 1 - depth ? Material.STONE_BRICK_STAIRS : Material.STONE_BRICKS,
                y == y0 - 1 - depth ? (side < 0 ? "facing=east,half=top" : "facing=west,half=top") : "");
            reserve(x, z);
        }
        // The bastion opens from the bridge: no parapet across its mouth.
        for (int z = pz - 4; z <= pz + 4; z++) {
            int x = side * (half + 1);
            runs.remove(x, y0 + 1, z);
            canvas.set(x, y0, z, Material.AIR);
            canvas.set(x, y0 - 1, z, Material.STONE_BRICKS);
        }
        for (int z : new int[] {pz - 5, pz + 5}) { canvas.set(side * 17, y0 + 1, z, Material.CHISELED_STONE_BRICKS); canvas.set(side * 17, y0 + 2, z, Material.LANTERN); runs.remove(side * 17, y0 + 1, z); }
    }

    /** The arch at the bridge's north end: two piers and a lintel lettered with the city's name, banners on both faces. */
    private void wardensArch(int z, int y0) {
        for (int x = -11; x <= 11; x++) for (int dz = 0; dz <= 3; dz++) for (int y = y0 - 1; y <= y0 + 11; y++) {
            int ax = Math.abs(x);
            boolean pier = ax >= 8;
            boolean opening = ax <= 7 && y < y0 + 7 + (ax <= 3 ? 1 : 0) + (ax <= 1 ? 1 : 0);
            if (y == y0 - 1) { canvas.set(x, y, z + dz, ax <= 1 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS); continue; }
            if (opening) { canvas.set(x, y, z + dz, Material.AIR); continue; }
            Material m = y == y0 + 11 ? (Math.floorMod(x, 2) == 0 ? Material.STONE_BRICKS : Material.AIR)
                : pier && (dz == 0 || dz == 3) && y % 4 == 0 ? Material.CHISELED_STONE_BRICKS : kitWeather(x, y, z + dz);
            canvas.set(x, y, z + dz, m);
            reserve(x, z + dz);
        }
        for (int x = -10; x <= 10; x++) for (int dz = 0; dz <= 3; dz++) {
            ReachTerrain.Column c = terrain.column(x, z + dz);
            if (c.land()) for (int y = c.top() + 1; y < y0 - 1; y++) canvas.set(x, y, z + dz, Material.STONE_BRICKS);
        }
        for (int[] face : new int[][] {{-1, 0}, {4, 1}}) {
            int fz = z + face[0];
            String facing = face[1] == 0 ? "north" : "south";
            canvas.set(0, y0 + 9, fz, Material.DARK_OAK_WALL_SIGN, "facing=" + facing);
            canvas.text(0, y0 + 9, fz, "HIGHCROWN", "Walk of", "the Wardens");
            for (int x : new int[] {-9, 9}) {
                canvas.set(x, y0 + 8, fz, Material.GREEN_WALL_BANNER, "facing=" + facing);
                canvas.set(x, y0 + 7, fz, Material.GREEN_WALL_BANNER, "facing=" + facing);
                canvas.set(x, y0 + 3, fz, Material.LANTERN, "hanging=false");
            }
        }
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
    private void wallColumn(int x, int z, int walk, boolean outer, boolean inner) { wallColumn(x, z, walk, outer, inner, -1); }

    /**
     * One column of a curtain wall: stone from the ground to the walk, merlons on the outer face, a rail on the inner.
     * With {@code gate} set to a street level, the column is an arch over that street instead: the way stays open
     * five blocks high and paved, and the wall carries on above it, so no wall ever blocks a street.
     */
    private void wallColumn(int x, int z, int walk, boolean outer, boolean inner, int gate) {
        ReachTerrain.Column c = terrain.column(x, z);
        if (!c.land() || c.wet()) return;
        for (int y = c.top() - 1; y < walk; y++) canvas.set(x, y, z, kitWeather(x, y, z));
        canvas.set(x, walk - 1, z, outer || inner ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
        for (int y = walk; y < walk + 4; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
        if (outer) {
            canvas.set(x, walk, z, Material.STONE_BRICKS);
            if (Math.floorMod(x + z, 2) == 0) canvas.set(x, walk + 1, z, Material.STONE_BRICKS);
        } else if (inner) runs.wall(x, walk, z, Material.STONE_BRICK_WALL);
        if (gate > 0) {
            pave(x, z, gate, 0.4);
            for (int y = gate; y <= gate + 4; y++) canvas.set(x, y, z, Material.AIR);
            canvas.set(x, gate + 5, z, Material.CHISELED_STONE_BRICKS);
        }
        reserve(x, z);
    }

    /** The west wall: down the gorge side from the Kingsgate to the water, then along the rim of the gorge to the lip. */
    private void westWall() {
        for (int x = -22; x >= -48; x--) {
            int zc = GATE_Z + 3;
            ReachTerrain.Column c = terrain.column(x, zc);
            if (!c.land() || c.wet()) break;
            int walk = Math.max(c.top() + 8, CROWN + 12 - 4 * ((-22 - x) / 4));
            for (int dz = 0; dz < 4; dz++) wallColumn(x, zc + dz, walk, dz == 0, dz == 3);
        }
        // The rim: for each step south, the last level column of the city before the drop into the gorge.
        List<double[]> rim = new ArrayList<>();
        for (int z = 838; z <= 1072; z += 3) {
            int last = Integer.MIN_VALUE;
            for (int x = -8; x > -200; x--) {
                ReachTerrain.Column c = terrain.column(x, z);
                if (!c.land() || c.wet() || c.river() < 6 || c.edge() < 3 || c.top() != ReachTerrain.highcrownTop(x, z)) break;
                last = x;
            }
            if (last != Integer.MIN_VALUE && last < -12) rim.add(new double[] {last + 1, z});
        }
        // Smooth the line so the wall bends like a built one, not like the noise; it starts at the Kingsgate's west tower.
        List<int[]> line = new ArrayList<>();
        line.add(new int[] {-22, GATE_Z + 6});
        for (int i = 0; i < rim.size(); i++) {
            double sx = 0; int n = 0;
            for (int k = Math.max(0, i - 3); k <= Math.min(rim.size() - 1, i + 3); k++) { sx += rim.get(k)[0]; n++; }
            line.add(new int[] {(int) Math.round(Math.max(sx / n, rim.get(i)[0])), (int) rim.get(i)[1]});
        }
        int[] prev = null;
        int steps = 0;
        for (int[] p : line) {
            List<int[]> cells = new ArrayList<>();
            if (prev == null) cells.add(p);
            else {
                int x = prev[0], z = prev[1];
                while (x != p[0] || z != p[1]) {
                    if (x != p[0] && (z == p[1] || Math.abs(p[0] - x) >= Math.abs(p[1] - z))) x += Integer.signum(p[0] - x); else z += Integer.signum(p[1] - z);
                    cells.add(new int[] {x, z});
                }
            }
            for (int[] cell : cells) {
                int y = street(cell[0], cell[1]);
                ReachTerrain.Column c = terrain.column(cell[0], cell[1]);
                if (!c.land() || c.wet()) continue;
                wallColumn(cell[0], cell[1], Math.max(y + 6, c.top() + 6), true, false);
                wallColumn(cell[0] + 1, cell[1], Math.max(y + 6, c.top() + 6), false, true);
                if (++steps % 46 == 0) {
                    wallTower(cell[0] + 1, cell[1], y, 7, 15, 1, steps);
                }
            }
            prev = p;
        }
    }

    /** A low parapet along the lip wherever the city meets the sky, except the quay where ships come in. */
    private void lipParapet() {
        for (int x = -200; x <= 200; x++) for (int z = 900; z <= 1100; z++) {
            if (Math.abs(x) <= 64 && z > 1062 || z < 990 || outsideRing(x, z)) continue;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet() || c.edge() >= 2.5 || c.edge() < 0.8 || Math.hypot(x, z - 905) > 175) continue;
            if (reserved.contains(key(x, z)) && !streets.containsKey(key(x, z))) continue;
            int y = c.top() + 1;
            if (!isAir(x, y, z) || !isAir(x, y + 1, z)) continue;
            runs.wall(x, y, z, Material.STONE_BRICK_WALL);
        }
    }

    /** The north wall from the gate east past the keep's crag to the ring wall, rising with the ground. */
    private void northWall() {
        // From the Kingsgate's east tower to the castle's north-west tower, then from its north-east tower to the
        // corner tower where the ring wall turns south. The walk steps up in level runs of six, as a built wall does on
        // a rising slope, never in a sawtooth.
        int from = 21, to = 138;
        int[] raw = new int[to - from + 1];
        for (int x = from; x <= to; x++) {
            int zc = GATE_Z + 3 + (int) Math.round(6 * Math.sin(x / 40.0));
            raw[x - from] = Math.max(CROWN + 12, terrain.column(x, zc).top() + 9);
        }
        for (int x = from; x <= to; x++) {
            if (x > 96 && x < 128) continue;
            int seg = (x - from) / 6, walk = 0;
            for (int k = seg * 6; k < Math.min(raw.length, seg * 6 + 6); k++) walk = Math.max(walk, raw[k]);
            int zc = GATE_Z + 3 + (int) Math.round(6 * Math.sin(x / 40.0));
            for (int dz = 0; dz < 4; dz++) wallColumn(x, zc + dz, walk, dz == 0, dz == 3);
        }
        for (int x : new int[] {46, 72, 136}) {
            int zc = GATE_Z + 3 + (int) Math.round(6 * Math.sin(x / 40.0));
            int base = Math.max(CROWN, terrain.column(x, zc + 2).top() + 1);
            wallTower(x, zc + 2, base, 9, raw[x - from] - base + 12, 2, x);
        }
    }

    /** A wall tower centred on a point with its door facing {@code turn} (0 north, 1 east, 2 south, 3 west): into the city. */
    private void wallTower(int cx, int cz, int base, int size, int height, int turn, long seed) {
        int x0 = cx - size / 2, z0 = cz - size / 2;
        kit.keepTower(lotBrush(x0, z0, x0 + size - 1, z0 + size - 1, turn, base), size, height, ReachKit.Style.SLATE, seed);
        for (int dx = -size / 2 - 2; dx <= size / 2 + 2; dx++) for (int dz = -size / 2 - 2; dz <= size / 2 + 2; dz++) reserve(cx + dx, cz + dz);
    }

    /** The ring wall round the east: from the north wall's end down to the lip, with towers. */
    private void ringWall() {
        // The walk's height is set per run of three degrees (the highest ground in the run), so the wall steps cleanly.
        Map<Integer, Integer> runWalk = new HashMap<>();
        for (double a = -30; a <= 75; a += 0.15) {
            double rad = Math.toRadians(a);
            for (double r = 158.5; r <= 161.5; r += 0.5) {
                int x = (int) Math.floor(r * Math.cos(rad)), z = (int) Math.floor(905 + r * Math.sin(rad));
                ReachTerrain.Column c = terrain.column(x, z);
                if (!c.land() || c.edge() < 3) continue;
                runWalk.merge((int) Math.floor(a / 3), Math.max(street(x, z) + 8, c.top() + 6), Math::max);
            }
        }
        Set<Long> done = new HashSet<>();
        for (double a = -30; a <= 75; a += 0.15) {
            double rad = Math.toRadians(a);
            for (double r = 158.5; r <= 161.5; r += 0.5) {
                int x = (int) Math.floor(r * Math.cos(rad)), z = (int) Math.floor(905 + r * Math.sin(rad));
                if (!done.add(key(x, z))) continue;
                ReachTerrain.Column c = terrain.column(x, z);
                if (!c.land() || c.edge() < 3) continue;
                int walk = runWalk.getOrDefault((int) Math.floor(a / 3), Math.max(street(x, z) + 8, c.top() + 6));
                // Postern arches where the ring streets, the terrace walk and the dock street pass through.
                double rc = ring(x, z);
                int level = street(x, z);
                boolean way = level == MARKET && (rc >= 99.5 && rc < 107 || rc >= 136.5 && rc < 146) || level == DOCK && rc >= 164.5 && rc < 172;
                wallColumn(x, z, walk, r > 161, r < 159, way && c.top() == level - 1 ? level : -1);
            }
        }
        for (double a : new double[] {-12, 22, 52, 72}) {
            double rad = Math.toRadians(a);
            int x = (int) Math.round(160 * Math.cos(rad)), z = (int) Math.round(905 + 160 * Math.sin(rad));
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land()) continue;
            int base = Math.max(street(x, z), c.top() + 1);
            wallTower(x, z, base, 9, 30, turnFacing(-Math.cos(rad), -Math.sin(rad)), (long) a);
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
        fountain(0, cz);
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

    /**
     * The plaza fountain: a carved pedestal holding a bowl, a spring welling up in its middle and spilling over all
     * four lips in falling sheets into the basin. The water is placed already flowing, in the states the game itself
     * settles on, so it looks alive the moment the chunk loads and no update can change it.
     */
    private void fountain(int cx, int cz) {
        int c = CROWN;
        for (int y = c; y <= c + 2; y++) canvas.set(cx, y, cz, y == c + 1 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(cx + d[0], c, cz + d[1], Material.STONE_BRICK_STAIRS,
            "facing=" + (d[0] == 1 ? "west" : d[0] == -1 ? "east" : d[1] == 1 ? "north" : "south") + ",half=bottom");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean corner = dx != 0 && dz != 0;
            canvas.set(cx + dx, c + 3, cz + dz, corner ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
            if (corner) canvas.set(cx + dx, c + 4, cz + dz, Material.STONE_BRICK_WALL, "up=true");
        }
        canvas.set(cx, c + 4, cz, Material.WATER);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            canvas.set(cx + d[0], c + 4, cz + d[1], Material.WATER, "level=1");
            canvas.set(cx + 2 * d[0], c + 4, cz + 2 * d[1], Material.WATER, "level=2");
            for (int y = c + 1; y <= c + 3; y++) canvas.set(cx + 2 * d[0], y, cz + 2 * d[1], Material.WATER, "level=8");
        }
        for (int[] c4 : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) canvas.set(cx + c4[0], c + 5, cz + c4[1], Material.LANTERN);
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
        // Parapets and lamps up both sides, as far as the castle's gatehouse.
        for (int k = 49; k <= 71; k++) {
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
        new HighcrownCastle(terrain, canvas, kit, new HighcrownCastle.Site() {
            @Override public void mark(String id, int x, int y, int z, float yaw) { marks.add(new Mark(id, x, y, z, yaw)); }
            @Override public void reserve(int x, int z) { HighcrownCity.this.reserve(x, z); }
        }).build();
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

    /**
     * The Lantern Fane, the city's cathedral ({@link HighcrownCathedral}), and its close: the churchyard along the
     * south aisle, a garden round the apse with oaks and benches over the Market Ward, a lawn on the north side and a
     * path from the citadel street to the north door.
     */
    private void fane() {
        new HighcrownCathedral(terrain, canvas, new HighcrownCathedral.Site() {
            @Override public void mark(String id, int x, int y, int z, float yaw) { marks.add(new Mark(id, x, y, z, yaw)); }
            @Override public void reserve(int x, int z) { HighcrownCity.this.reserve(x, z); }
        }).build();
        for (int x = 8; x <= 78; x++) for (int z = 904; z <= 926; z++) {
            if (!onPlatform(x, z) || street(x, z) != CROWN || ring(x, z) > ReachTerrain.CROWN_RADIUS - 1.5) continue;
            ReachCanvas.Spec there = canvas.get(x, CROWN - 1, z);
            if (there != null && !there.state().contains("grass") && !there.state().contains("moss")) continue;
            double walk = Math.abs(ring(x, z) - (ReachTerrain.CROWN_RADIUS - 5));
            if (walk < 1.6) street(x, z, CROWN, 0.5);
            else {
                canvas.set(x, CROWN - 1, z, terrain.grain(x, z, 0.3) > 0.6 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK);
                reserve(x, z);
            }
        }
        // Paths: from the south aisle's door and the south transept's door down to the terrace walk.
        for (int[] p : new int[][] {{32, 906}, {49, 911}}) for (int z = p[1]; z <= 926; z++) {
            if (streets.containsKey(key(p[0], z)) || !onPlatform(p[0], z) || street(p[0], z) != CROWN) break;
            for (int x = p[0] - 1; x <= p[0] + 1; x++) canvas.set(x, CROWN - 1, z, x == p[0] ? Material.GRAVEL : Material.COARSE_DIRT);
        }
        for (int z = 873; z <= 874; z++) for (int x = 47; x <= 51; x++) canvas.set(x, CROWN - 1, z, Material.STONE_BRICKS);
        churchyard();
        ReachTrees.oak(canvas, 64, CROWN, 909, 9001);
        ReachTrees.oak(canvas, 74, CROWN, 893, 9002);
        for (int x : new int[] {56, 62}) ReachProps.bench(new ReachBrush(canvas, x, CROWN, 915, 2), Material.SPRUCE_STAIRS, Material.SPRUCE_TRAPDOOR);
    }

    /** The churchyard along the south aisle: rows of weathered headstones over grassy mounds, candles, flowers, a yew. */
    private void churchyard() {
        Material[] stones = {Material.MOSSY_STONE_BRICK_WALL, Material.STONE_BRICK_WALL, Material.COBBLESTONE_WALL, Material.ANDESITE_WALL};
        int n = 0;
        for (int x = 23; x <= 41; x += 3) for (int z = 908; z <= 914; z += 3) {
            if (Math.abs(x - 32) <= 1 || !onPlatform(x, z) || street(x, z) != CROWN || streets.containsKey(key(x, z))) continue;
            Material stone = stones[n++ % stones.length];
            canvas.set(x, CROWN, z, stone, "up=true");
            if (n % 3 == 0) canvas.set(x, CROWN + 1, z, Material.CANDLE, "candles=1,lit=true");
            canvas.set(x, CROWN - 1, z + 1, Material.PODZOL);
            if (n % 2 == 0) canvas.set(x, CROWN, z + 1, Material.POPPY);
        }
        ReachTrees.tallSpruce(canvas, 70, CROWN, 902, 8, 9003);
        marks.add(new Mark("npc_tamsin_wake", 29, CROWN, 909, 180F));
    }

    /** The Hall of Callings on the avenue below the plaza: skills, mastery, jobs and parties. */
    private void hallOfCallings() {
        int x0 = -24, x1 = -10, z0 = 867, z1 = 883;
        ReachBrush b = lotBrush(x0, z0, x1, z1, 1, CROWN);
        int[] size = lotSize(x0, z0, x1, z1, 1);
        kit.house(b, size[0], size[1], 2, ReachKit.Style.SLATE, true, false, 4242, ReachInteriors.Use.OFFICE, ReachInteriors.Trade.MAPS);
        ReachKit.shopSign(b, size[0] / 2 + 2, 2, Material.DARK_OAK_WALL_HANGING_SIGN, "Hall of", "Callings");
        String[] mentors = {"pell_wren", "fen_calder", "nima_hart"};
        int[][] desks = {{2, 3}, {size[0] - 3, 3}, {2, 6}};
        for (int i = 0; i < mentors.length; i++) marks.add(new Mark("npc_" + mentors[i], b.wx(desks[i][0], desks[i][1]), CROWN, b.wz(desks[i][0], desks[i][1]), 270F));
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
        // The Wayfarers' Hall: the expedition house on the plaza's east side, its desks for maps, claims, surveys,
        // brokers and fixers.
        int x0 = 35, x1 = 48, z0 = 835, z1 = 861;
        ReachBrush hall = lotBrush(x0, z0, x1, z1, 3, CROWN);
        int[] size = lotSize(x0, z0, x1, z1, 3);
        kit.site(hall, -1, -2, size[0], size[1], 2, Material.STONE_BRICKS);
        kit.house(hall, size[0], size[1], 2, ReachKit.Style.SLATE, true, false, 7301, ReachInteriors.Use.OFFICE, ReachInteriors.Trade.MAPS);
        ReachKit.shopSign(hall, size[0] / 2 + 2, 2, Material.SPRUCE_WALL_HANGING_SIGN, "Wayfarers'", "Hall");
        lots.add(new int[] {x0 - 3, z0 - 1, x1 + 1, z1 + 1});
        String[] wayfarers = {"elian_quill", "gilda_penn", "hessa_clay", "garrick_mott", "bram_tallow"};
        int[][] desks = {{2, 3}, {size[0] - 3, 3}, {2, 6}, {size[0] - 3, 6}, {2, 9}};
        for (int i = 0; i < wayfarers.length; i++) marks.add(new Mark("npc_" + wayfarers[i], hall.wx(desks[i][0], desks[i][1]), CROWN, hall.wz(desks[i][0], desks[i][1]), 90F));
        for (int z : new int[] {z0 + 4, z1 - 4}) canvas.set(x0 - 2, CROWN + 6, z, Material.BLUE_WALL_BANNER, "facing=west");
        // A steward's house beside it on the citadel street.
        tryHouse(50, 851, 57, 861, 2, CROWN, ReachKit.Style.SLATE, 2, 7110, Trade.NONE);
        // The Archive below the Hall of Callings, where the research commissioner keeps the Reach's records.
        venues = queue(new Venue("lyra_senn", ReachInteriors.Use.OFFICE, ReachInteriors.Trade.MAPS, Material.DARK_OAK_WALL_HANGING_SIGN, "The", "Archive"));
        tryHouse(-21, 887, -12, 896, 1, CROWN, ReachKit.Style.SLATE, 2, 7150, Trade.NONE);
        if (!venues.isEmpty()) tryHouse(-20, 886, -12, 894, 1, CROWN, ReachKit.Style.SLATE, 1, 7151, Trade.NONE);
        venues = null;
        waystone(-23, 856);
    }

    /**
     * The waystone arch on the plaza: a ring of carved stone and amethyst round a shimmer of glass, where the portal
     * master opens the ways to the islands and expeditions.
     */
    private void waystone(int x, int cz) {
        for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 7; dy++) {
            double r = Math.hypot(dz, (dy - 3) * 0.85);
            if (r > 3.6) continue;
            boolean ring = r > 2.6;
            canvas.set(x, CROWN + dy, cz + dz, ring ? ((dy + dz) % 3 == 0 ? Material.AMETHYST_BLOCK : Material.CHISELED_STONE_BRICKS)
                : Material.PURPLE_STAINED_GLASS_PANE, ring ? "" : "north=true,south=true");
        }
        for (int dz : new int[] {-4, 4}) {
            canvas.set(x, CROWN, cz + dz, Material.STONE_BRICKS);
            canvas.set(x, CROWN + 1, cz + dz, Material.AMETHYST_CLUSTER, "facing=up");
        }
        for (int dz = -3; dz <= 3; dz++) canvas.set(x, CROWN - 1, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
        marks.add(new Mark("npc_kestrel_way", x + 2, CROWN, cz + 2, 90F));
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
            marks.add(new Mark("stall_w_" + i, -AVENUE_HALF + 1, MARKET, z + 2, 90F));
            // The east row stops short of the Exchequer's front, leaving its steps a forecourt on the avenue.
            if (i >= 2 && i <= 6) continue;
            kit.stall(new ReachBrush(canvas, AVENUE_HALF + 1, MARKET, z + 4, 3), b2[0], b2[1], goods[(i + 3) % goods.length], ReachKit.Style.HIGHCROWN);
            marks.add(new Mark("stall_e_" + i, AVENUE_HALF - 1, MARKET, z + 2, 270F));
        }
        // Garlands of lanterns across the avenue: a straight chain between two poles that rise from the stalls' posts.
        for (int i = 1; i <= 7; i += 2) {
            int z = 922 + i * 5 + 4;
            boolean standing = i >= 2 && i <= 6;
            for (int x : new int[] {-AVENUE_HALF - 1, AVENUE_HALF + 1}) for (int y = x > 0 && standing ? MARKET : MARKET + 5; y <= MARKET + 7; y++)
                canvas.set(x, y, z, y == MARKET + 7 ? Material.DARK_OAK_LOG : y == MARKET ? Material.STONE_BRICK_WALL : Material.DARK_OAK_FENCE,
                    y == MARKET + 7 ? "axis=y" : y == MARKET ? "up=true" : "");
            for (int x = -AVENUE_HALF; x <= AVENUE_HALF; x++) {
                canvas.set(x, MARKET + 7, z, Material.IRON_CHAIN, "axis=x");
                if (x % 3 == 0) canvas.set(x, MARKET + 6, z, Material.LANTERN, "hanging=true");
            }
        }
        marketHall(-31, 928, -15, 958);
        new HighcrownExchequer(canvas, new HighcrownExchequer.Site() {
            @Override public void mark(String id, int x, int y, int z, float yaw) { marks.add(new Mark(id, x, y, z, yaw)); }
            @Override public void reserve(int x, int z) { HighcrownCity.this.reserve(x, z); }
        }).build();
        marketCross(22, 971);
        for (int k = 0; k < 2; k++) {
            ReachProps.marketCrates(new ReachBrush(canvas, 11 + k * 4, MARKET, 974, 0), 31 + k);
            ReachProps.cart(new ReachBrush(canvas, 29 + k * 3, MARKET, 968, 2), 77 + k);
        }
        ReachProps.trough(new ReachBrush(canvas, 11, MARKET, 968, 0));
        for (int x = -33; x <= 33; x++) for (int z = z0 - 1; z <= z1 + 1; z++) reserve(x, z);
        marks.add(new Mark("market", 0, MARKET, 948, 180F));
        marks.add(new Mark("npc_orris_bell", -23, MARKET, 943, 90F));
        marks.add(new Mark("npc_highcrown_folk_widow", AVENUE_HALF - 1, MARKET, 963, 270F));
        marks.add(new Mark("npc_highcrown_folk_urchin", 3, MARKET, 950, 180F));
        canvas.set(AVENUE_HALF + 1, MARKET + 1, 963, Material.POTTED_POPPY);
        canvas.set(AVENUE_HALF + 1, MARKET + 1, 965, Material.POTTED_CORNFLOWER);
    }

    /**
     * The Market Hall, Highcrown's cloth hall: the covered market in a stone arcade round the ground floor; the
     * guild's hall above it in stone, two lancets to each bay between pilasters; round turrets corbelled out at the
     * corners under slate cones; a steep dark roof broken by rows of dormers between coped stone gables; and in the
     * middle of the front, facing the avenue and the Exchequer across it, a belfry tower whose ground floor is the
     * hall's gateway.
     */
    private void marketHall(int x0, int z0, int x1, int z1) {
        int m = MARKET, up = m + 6, eaves = m + 13;
        kit.site(new ReachBrush(canvas, x0, m, z0, 0), 0, 0, x1 - x0, z1 - z0, 1, Material.STONE_BRICKS);
        int cz = (z0 + z1) / 2;
        int tx0 = x1 - 6, tx1 = x1, tz0 = cz - 3, tz1 = cz + 3;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean side = x == x0 || x == x1, end = z == z0 || z == z1, edge = side || end;
            int along = side ? z - z0 : x - x0, period = side ? 5 : 4;
            boolean pier = edge && (Math.floorMod(along, period) == 0 || side && end);
            canvas.set(x, m - 1, z, (x + z) % 3 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
            // The arcade: round arches four high between the piers, a cornice over them.
            int inBay = Math.floorMod(along, period), width = period - 1;
            double a = Math.abs(inBay - (period / 2.0));
            int open = !edge || pier ? -1 : (a < 1 ? m + 4 : m + 3);
            for (int y = m; y < up; y++) {
                Material mat;
                if (!edge) mat = Material.AIR;
                else if (y <= open) mat = Material.AIR;
                else if (y == up - 1) mat = Material.STONE_BRICKS;
                else mat = pier ? kitWeather(x, y, z) : Material.STONE_BRICKS;
                canvas.set(x, y, z, mat);
            }
            if (edge && !pier) canvas.set(x, open + 1, z, a < 1 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            // The guild's hall: stone walls, pilasters over the piers, paired lancets in each bay.
            canvas.set(x, up - 1, z, edge ? Material.POLISHED_ANDESITE : Material.SPRUCE_PLANKS);
            for (int y = up; y < eaves; y++) {
                if (!edge) { canvas.set(x, y, z, Material.AIR); continue; }
                boolean light = !pier && (period == 5 ? inBay == 2 || inBay == 3 : inBay == 2);
                boolean lancet = light && y >= up + 1 && y <= up + 4;
                boolean head = light && y == up + 5;
                canvas.set(x, y, z, pier ? Material.POLISHED_ANDESITE : lancet ? Material.GLASS_PANE : head ? Material.CHISELED_STONE_BRICKS : kitWeather(x, y, z));
            }
        }
        // Cornices outside: over the arcade, and a corbel table under the eaves.
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean ring = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
            if (!ring || (x == x0 - 1 || x == x1 + 1) && (z == z0 - 1 || z == z1 + 1)) continue;
            String face = x == x0 - 1 ? "east" : x == x1 + 1 ? "west" : z == z0 - 1 ? "south" : "north";
            canvas.set(x, up - 1, z, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=top");
            canvas.set(x, eaves - 1, z, Material.STONE_BRICK_STAIRS, "facing=" + face + ",half=top");
        }
        // Inside: two rows of timber posts carry the hall's floor; stalls and lamps in the market below.
        for (int z = z0 + 5; z < z1; z += 5) for (int x : new int[] {x0 + 4, x1 - 4}) {
            if (Math.abs(z - cz) <= 2) continue;
            for (int y = m; y < up - 1; y++) canvas.set(x, y, z, Material.DARK_OAK_LOG, "axis=y");
        }
        for (int z = z0 + 3; z <= z1 - 6; z += 7) {
            ReachProps.marketCrates(new ReachBrush(canvas, x0 + 2, m, z, 1), z);
            // The east row stops either side of the belfry's foot.
            if (z + 4 < tz0 || z > tz1) ReachProps.marketCrates(new ReachBrush(canvas, x1 - 2, m, z + 2, 3), z + 1);
            canvas.set((x0 + x1) / 2, up - 2, z, Material.LANTERN, "hanging=true");
        }
        // The roof: steep, dark oak, gables closed in stone with a coping and pinnacles.
        ReachKit.Style roofStyle = ReachKit.Style.HARBOUR;
        kit.roof(new ReachBrush(canvas, x0, eaves, z0, 0), -1, x1 - x0 + 1, 0, z1 - z0, 0, roofStyle, false, true);
        int span = x1 - x0 + 3, half = span / 2, ridgeY = eaves + half - 1;
        for (int z : new int[] {z0, z1}) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                int k = Math.min(x - (x0 - 1), (x1 + 1) - x);
                int top = eaves + k;
                for (int y = eaves; y <= top; y++) canvas.set(x, y, z, kitWeather(x, y, z));
                canvas.set(x, top + 1, z, Material.STONE_BRICK_STAIRS, "facing=" + (x < (x0 + x1) / 2.0 ? "east" : "west") + ",half=bottom");
            }
            int mx = (x0 + x1) / 2;
            canvas.set(mx, ridgeY + 2, z, Material.CHISELED_STONE_BRICKS);
            canvas.set(mx, ridgeY + 3, z, Material.STONE_BRICK_WALL, "up=true");
            canvas.set(mx, ridgeY + 4, z, Material.LANTERN);
            // A tall lancet in each gable.
            for (int y = eaves + 2; y <= eaves + 6; y++) canvas.set(mx, y, z, y == eaves + 6 ? Material.CHISELED_STONE_BRICKS : Material.GLASS_PANE);
        }
        // Dormers in two rows down both slopes.
        for (int z = z0 + 2; z <= z1 - 3; z += 5) {
            if (z >= tz0 - 1 && z <= tz1 + 1) continue;
            hallDormer(x0 + 1, eaves + 2, z, 1);
            hallDormer(x1 - 1, eaves + 2, z, -1);
            if (Math.floorMod(z - z0, 10) == 2) {
                hallDormer(x0 + 4, eaves + 5, z + 2, 1);
                hallDormer(x1 - 4, eaves + 5, z + 2, -1);
            }
        }
        // Corner turrets, corbelled out from the arcade's top, under slate cones.
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 2.2) continue;
                int x = c[0] + dx, z = c[1] + dz;
                canvas.set(x, up - 1, z, Material.STONE_BRICK_STAIRS, "facing=" + (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north")) + ",half=top");
                for (int y = up; y <= eaves + 3; y++) canvas.set(x, y, z, d > 1.2 ? (y % 4 == 1 && (dx == 0 || dz == 0) ? Material.GLASS_PANE : kitWeather(x, y, z)) : Material.AIR);
                for (int k = 0; k <= 5; k++) if (d <= 2.6 - k * 0.5) canvas.set(x, eaves + 4 + k, z, Material.DEEPSLATE_TILES);
            }
            canvas.set(c[0], eaves + 10, c[1], Material.LIGHTNING_ROD, "facing=up");
        }
        hallBelfry(tx0, tx1, tz0, tz1, m);
        for (int x = x0 - 2; x <= x1 + 2; x++) for (int z = z0 - 2; z <= z1 + 2; z++) reserve(x, z);
        lots.add(new int[] {x0 - 1, z0 - 1, x1 + 1, z1 + 1});
    }

    /** A small dormer on the hall's roof: a window under a little gable, facing {@code dir} along x. */
    private void hallDormer(int x, int y, int z, int dir) {
        for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
            if (dy == 2 && dz != 0) continue;
            canvas.set(x, y + dy, z + dz, dz == 0 && dy < 2 ? Material.GLASS_PANE : Material.DARK_OAK_PLANKS);
            canvas.set(x + dir, y + dy, z + dz, Material.DARK_OAK_PLANKS);
        }
        for (int k = -1; k <= 2; k++) {
            int xx = x - dir + k * dir;
            canvas.set(xx, y + 2, z - 1, Material.DARK_OAK_STAIRS, "facing=south,half=bottom");
            canvas.set(xx, y + 2, z + 1, Material.DARK_OAK_STAIRS, "facing=north,half=bottom");
            canvas.set(xx, y + 3, z, Material.DARK_OAK_SLAB, "type=bottom");
        }
    }

    /**
     * The belfry tower on the hall's front: the gateway through its foot, a tall window over it, the city's banners,
     * an open belfry with its bell, a pinnacled parapet and a slate spire with a gilded finial.
     */
    private void hallBelfry(int x0, int x1, int z0, int z1, int m) {
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, top = m + 30, belfry = m + 22;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = m - 1; y <= top; y++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            if (!wall) { canvas.set(x, y, z, y == m - 1 || y == m + 6 || y == belfry - 1 ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
            canvas.set(x, y, z, corner ? Material.POLISHED_ANDESITE : kitWeather(x, y, z));
        }
        // The gateway through the tower, east to west, into the market.
        for (int x = x0; x <= x1 + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) for (int y = m; y <= m + 4; y++) {
            if (y == m + 4 && z != cz) continue;
            canvas.set(x, y, z, Material.AIR);
        }
        for (int x = x0; x <= x1; x++) for (int z = cz - 1; z <= cz + 1; z++) canvas.set(x, m - 1, z, Material.POLISHED_ANDESITE);
        canvas.set(x1, m + 5, cz, Material.CHISELED_STONE_BRICKS);
        canvas.set(x1 + 1, m + 3, cz - 2, Material.LANTERN, "hanging=false");
        canvas.set(x1 + 1, m + 2, cz - 2, Material.STONE_BRICK_WALL, "up=true");
        canvas.set(x1 + 1, m + 3, cz + 2, Material.LANTERN, "hanging=false");
        canvas.set(x1 + 1, m + 2, cz + 2, Material.STONE_BRICK_WALL, "up=true");
        // A tall window on the front over the gate, and string courses at each stage.
        for (int y = m + 9; y <= m + 15; y++) for (int z = cz - 1; z <= cz + 1; z++)
            if (y < m + 15 || z == cz) canvas.set(x1, y, z, y == m + 15 ? Material.CHISELED_STONE_BRICKS : Material.GLASS_PANE);
        for (int y : new int[] {m + 7, m + 17, belfry - 1}) for (int z = z0; z <= z1; z++) canvas.set(x1 + 1, y, z, Material.STONE_BRICK_STAIRS, "facing=west,half=top");
        for (int z : new int[] {z0, z1}) canvas.set(x1 + 1, m + 14, z, Material.GREEN_WALL_BANNER, "facing=east");
        // The belfry: twin openings in every face, the bell on its beam.
        for (int y = belfry; y <= belfry + 5; y++) for (int k = -2; k <= 2; k++) {
            if (k == 0) continue;
            boolean open = y <= belfry + 4 || Math.abs(k) == 1;
            if (!open) continue;
            canvas.set(x0, y, cz + k, Material.AIR);
            canvas.set(x1, y, cz + k, Material.AIR);
            canvas.set(cx + k, y, z0, Material.AIR);
            canvas.set(cx + k, y, z1, Material.AIR);
        }
        for (int x = x0 + 1; x < x1; x++) canvas.set(x, belfry + 5, cz, Material.DARK_OAK_LOG, "axis=x");
        canvas.set(cx, belfry + 4, cz, Material.BELL, "attachment=ceiling,facing=east");
        // Parapet with corner pinnacles, then the spire.
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
            if (!edge) continue;
            boolean corner = (x == x0 - 1 || x == x1 + 1) && (z == z0 - 1 || z == z1 + 1);
            canvas.set(x, top, z, Material.STONE_BRICK_STAIRS, x == x0 - 1 ? "facing=east,half=top" : x == x1 + 1 ? "facing=west,half=top"
                : z == z0 - 1 ? "facing=south,half=top" : "facing=north,half=top");
            if (corner) {
                canvas.set(x, top + 1, z, Material.CHISELED_STONE_BRICKS);
                for (int y = top + 2; y <= top + 4; y++) canvas.set(x, y, z, Material.STONE_BRICK_WALL, "up=true");
                canvas.set(x, top + 5, z, Material.LIGHTNING_ROD, "facing=up");
            } else runs.wall(x, top + 1, z, Material.STONE_BRICK_WALL);
        }
        kit.spire(new ReachBrush(canvas, x0, m, z0, 0), 0, x1 - x0, top + 1 - m, ReachKit.Style.SLATE);
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
        venues = queue(
            new Venue("alba_meadow", ReachInteriors.Use.SHOP, ReachInteriors.Trade.GROCER, Material.OAK_WALL_HANGING_SIGN, "Meadow's", "Grocery"),
            new Venue("highcrown_folk_baker", ReachInteriors.Use.SHOP, ReachInteriors.Trade.BAKER, Material.SPRUCE_WALL_HANGING_SIGN, "Crumb's", "Bakery"),
            new Venue("vera_lune", ReachInteriors.Use.SHOP, ReachInteriors.Trade.TAILOR, Material.CHERRY_WALL_HANGING_SIGN, "Lune's", "Wardrobe"),
            new Venue("nyra_vell", ReachInteriors.Use.SHOP, ReachInteriors.Trade.ARCANE, Material.CRIMSON_WALL_HANGING_SIGN, "Vell's", "Identifier"),
            new Venue("rook_salen", ReachInteriors.Use.SHOP, ReachInteriors.Trade.RELICS, Material.DARK_OAK_WALL_HANGING_SIGN, "Salen's", "Relics"),
            new Venue("rowan_ashwood", ReachInteriors.Use.SHOP, ReachInteriors.Trade.TIMBER, Material.SPRUCE_WALL_HANGING_SIGN, "Ashwood", "Timber"),
            new Venue("dain_slate", ReachInteriors.Use.SHOP, ReachInteriors.Trade.STONE, Material.DARK_OAK_WALL_HANGING_SIGN, "Slate's", "Quarry"),
            new Venue("petra_mason", ReachInteriors.Use.SHOP, ReachInteriors.Trade.BUILDER, Material.OAK_WALL_HANGING_SIGN, "Mason's", "Builders"),
            new Venue("hob_copper", ReachInteriors.Use.WORKSHOP, ReachInteriors.Trade.MECHANIST, Material.WARPED_WALL_HANGING_SIGN, "Copper's", "Mechanics"));
        arcRow(89, 98.5, 48, 92, true, MARKET, guild, 8100, Trade.NONE);
        arcRow(107.5, 119, 46, 92, false, MARKET, guild, 8200, Trade.NONE);
        arcRow(122.5, 136, 40, 92, true, MARKET, guild, 8300, Trade.NONE);
        java.util.Deque<Venue> leftOver = venues;
        // West: Crafters' Row along the gorge.
        ReachKit.Style[] crafts = {ReachKit.Style.VALE_SPRUCE, ReachKit.Style.HIGHCROWN, ReachKit.Style.VALE_SPRUCE};
        venues = queue(
            new Venue("torren_pike", ReachInteriors.Use.WORKSHOP, ReachInteriors.Trade.SMITH, Material.DARK_OAK_WALL_HANGING_SIGN, "The", "Forge"),
            new Venue("sella_mire", ReachInteriors.Use.SHOP, ReachInteriors.Trade.ALCHEMY, Material.CRIMSON_WALL_HANGING_SIGN, "Mire's", "Alchemy"),
            new Venue("veyra_loom", ReachInteriors.Use.SHOP, ReachInteriors.Trade.ENCHANTER, Material.WARPED_WALL_HANGING_SIGN, "Loom's", "Enchanting"));
        venues.addAll(leftOver);
        arcRow(107.5, 119, 128, 178, false, MARKET, crafts, 8400, Trade.SMITH);
        arcRow(89, 98.5, 130, 178, true, MARKET, crafts, 8500, Trade.WEAVER);
        arcRow(122.5, 136, 128, 178, true, MARKET, crafts, 8600, Trade.CARPENTER);
        // Anyone still without a shop keeps a stall in the market square instead.
        int stall = 0;
        for (Venue v : venues) marks.add(new Mark("npc_" + v.npc(), stall % 2 == 0 ? -AVENUE_HALF + 1 : AVENUE_HALF - 1, MARKET, 924 + (stall++ / 2) * 5, stall % 2 == 1 ? 90F : 270F));
        venues = null;
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
            if (outsideRing(x, z)) return false;
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
        Venue venue = venues == null || warehouse ? null : venues.poll();
        ReachInteriors.Use use = venue != null ? venue.use() : warehouse ? ReachInteriors.Use.WAREHOUSE : ReachInteriors.Use.HOME;
        ReachInteriors.Trade kind = venue != null ? venue.trade() : ReachInteriors.Trade.GENERAL;
        int[] keeper = kit.house(b, size[0], size[1], floors, style, jetty, gable, seed, use, kind);
        int door = size[0] / 2;
        if (venue != null) {
            if (keeper != null) marks.add(new Mark("npc_" + venue.npc(), b.wx(keeper[0], keeper[2]), level + keeper[1], b.wz(keeper[0], keeper[2]), yawFacing(turn)));
            if (venue.sign() != null) ReachKit.shopSign(b, door + 2, 2, venue.sign(), venue.text());
            if (venue.use() == ReachInteriors.Use.SHOP) ReachProps.marketCrates(b.at(1, 0, -2, 0), seed);
            return true;
        }
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
                ReachKit.shopSign(b, door + 2, 2, Material.DARK_OAK_WALL_HANGING_SIGN, "Smithy");
            }
            case WEAVER -> {
                b.set(1, 0, 1, Material.LOOM, "facing=south");
                for (int k = 0; k < 2; k++) b.set(size[0] - 2 - k, 0, -2, k == 0 ? Material.LIGHT_BLUE_WOOL : Material.MAGENTA_WOOL);
                ReachKit.shopSign(b, door + 2, 2, Material.SPRUCE_WALL_HANGING_SIGN, "Weaver");
            }
            case ALCHEMIST -> {
                b.set(1, 0, 1, Material.BREWING_STAND, "has_bottle_0=true,has_bottle_1=false,has_bottle_2=true");
                b.set(size[0] - 2, 0, -2, Material.WATER_CAULDRON, "level=2");
                ReachKit.shopSign(b, door + 2, 2, Material.CRIMSON_WALL_HANGING_SIGN, "Apothecary");
            }
            case CARPENTER -> {
                ReachProps.crates(b.at(size[0] - 2, 0, -2, 0), seed);
                b.set(size[0] - 1, 0, -3, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                ReachKit.shopSign(b, door + 2, 2, Material.OAK_WALL_HANGING_SIGN, "Carpenter");
            }
            case TANNER -> {
                ReachProps.crates(b.at(size[0] - 2, 0, -2, 0), seed);
                ReachKit.shopSign(b, door + 2, 2, Material.SPRUCE_WALL_HANGING_SIGN, "Tannery");
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
        // Aldren the First Skyfarer, sword raised, facing the piers where every islander steps ashore.
        ReachBrush aldren = new ReachBrush(canvas, 0, DOCK, mz, 2);
        int plinth = ReachStatues.pedestal(aldren, 3, 5, "ALDREN", "the First", "Skyfarer", "who found the Reach");
        ReachStatues.hero(aldren.at(0, plinth + 1, 0, 0), 1.4, ReachStatues.Pose.SWORD_RAISED, ReachStatues.Palette.BRONZE);
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
        int[] host = kit.house(gull, 15, 13, 2, ReachKit.Style.HARBOUR, true, false, 77, ReachInteriors.Use.INN, ReachInteriors.Trade.GENERAL);
        if (host != null) marks.add(new Mark("npc_gull_innkeeper", gull.wx(host[0], host[2]), DOCK, gull.wz(host[0], host[2]), 90F));
        ReachKit.shopSign(gull, 9, 2, Material.SPRUCE_WALL_HANGING_SIGN, "The", "Tethered", "Gull", "Inn");
        lots.add(new int[] {31, DOCK_SQUARE_Z, 48, DOCK_SQUARE_Z + 18});
        for (int x = 31; x <= 48; x++) for (int z = DOCK_SQUARE_Z; z <= DOCK_SQUARE_Z + 18; z++) reserve(x, z);
        marks.add(new Mark("tethered_gull", 30, DOCK, DOCK_SQUARE_Z + 9, 90F));
        marks.add(new Mark("dock_square", 0, DOCK, DOCK_SQUARE_Z + 24, 180F));
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
            venues = row[2] == 2 && bl[0] == 7 ? queue(new Venue("ivo_reed", ReachInteriors.Use.SHOP, ReachInteriors.Trade.GENERAL, Material.SPRUCE_WALL_HANGING_SIGN, "Reed's", "Tackle"))
                : row[2] == 0 && bl[0] == -17 ? queue(new Venue("aeron_vale", ReachInteriors.Use.OFFICE, ReachInteriors.Trade.MAPS, Material.DARK_OAK_WALL_HANGING_SIGN, "Adventurer", "Registry"))
                : row[2] == 0 && bl[0] == 7 ? queue(new Venue("cato_flint", ReachInteriors.Use.OFFICE, ReachInteriors.Trade.MAPS, Material.DARK_OAK_WALL_HANGING_SIGN, "Warden", "Contracts")) : null;
            long h = ReachFlora.hash(x * 13 + 7, row[0]);
            int w = Math.min(bl[1] - x + 1, 8 + (int) Math.floorMod(h, 3L));
            if (w < 7) break;
            boolean built = tryHouse(x, row[0], x + w - 1, row[1], row[2], DOCK, harbour[n++ % harbour.length], 1 + (int) Math.floorMod(h >> 5, 2L), h, Trade.NONE);
            x += built ? w + 1 : 2;
            venues = null;
        }
        for (int x = -60; x <= 60; x++) {
            int z = 1070;
            if (Math.floorMod(x, 12) == 6 && isAir(x, DOCK, z) && !nearDoor(x, DOCK, z)) ReachProps.lamp(new ReachBrush(canvas, x, DOCK, z, 2), Material.SPRUCE_LOG, Material.SPRUCE_TRAPDOOR);
        }

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

    /**
     * The piers: decks seven wide on log stringers, railed and lamp-lit, each carried out over the void on a pair of
     * timber trusses. The trusses are deep at the root and shallow at the tip, with posts and diagonals. Each root sits
     * on a stone buttress stepped out of the cliff, with knee braces from the rock, and a pier head with a crane.
     */
    private void docks() {
        int lip = 1094, end = lip + 44;
        for (int px : new int[] {-24, 0, 24}) {
            int root = lip - 12;
            while (root < end && terrain.column(px, root + 1).land()) root++;
            for (int z = lip - 12; z <= end; z++) for (int x = px - 3; x <= px + 3; x++) {
                boolean edge = Math.abs(x - px) == 3;
                canvas.set(x, DOCK - 1, z, edge ? Material.STRIPPED_SPRUCE_LOG : (z % 5 == 0 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS),
                    edge ? "axis=z" : z % 5 == 0 ? "axis=x" : "");
                for (int y = DOCK; y < DOCK + 4; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
                if (edge && z > root - 2 && !gangway(px, x, z, lip)) runs.fence(x, DOCK, z, Material.SPRUCE_FENCE);
            }
            for (int z = root; z <= end; z += 8) for (int x : new int[] {px - 3, px + 3}) {
                for (int y = DOCK; y <= DOCK + 2; y++) canvas.set(x, y, z, Material.SPRUCE_FENCE);
                canvas.set(x, DOCK + 3, z, Material.LANTERN);
            }
            // The trusses: one under each edge of the deck, the same depth all the way out. A stringer under the deck, a
            // bottom chord six below, a post every six blocks, and one clean 45-degree brace in each panel, all leaning
            // the same way, so the frame reads at a glance. Cross beams tie the two trusses at every post.
            final int depth = 6, panel = 6;
            int top = DOCK - 2, bottom = top - depth;
            int last = root + ((end - root) / panel) * panel;
            for (int z = root; z <= last; z++) {
                int k = (z - root) % panel;
                for (int x : new int[] {px - 3, px + 3}) {
                    canvas.set(x, top, z, Material.STRIPPED_SPRUCE_LOG, "axis=z");
                    canvas.set(x, bottom, z, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
                    if (k == 0) for (int y = bottom + 1; y < top; y++) canvas.set(x, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
                    else canvas.set(x, bottom + k, z, Material.STRIPPED_SPRUCE_LOG, "axis=z");
                }
                if (k == 0) for (int x = px - 2; x <= px + 2; x++) {
                    canvas.set(x, top, z, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
                    canvas.set(x, bottom, z, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
                }
            }
            // The corbel the pier springs from: courses of stone stepping out of the cliff under the root, one block
            // further at each course up, so the trusses sit on a seat of masonry rather than hanging from the edge.
            for (int course = 0; course <= 9; course++) for (int x = px - 4; x <= px + 4; x++) {
                int y = bottom - 1 + depth + 1 - course;
                int reach = root + 2 - course / 2;
                for (int z = root - 6; z <= reach; z++) {
                    ReachTerrain.Column col = terrain.column(x, z);
                    if (col.land() && y <= col.top() && y >= col.bottom()) continue;
                    boolean face = z == reach || Math.abs(x - px) == 4;
                    canvas.set(x, y, z, face ? (course % 3 == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS) : Material.STONE);
                }
            }
            // The pier head: a wider platform with bollards and a crane.
            for (int z = end - 5; z <= end; z++) for (int x = px - 5; x <= px + 5; x++) {
                if (Math.abs(x - px) <= 3) continue;
                canvas.set(x, DOCK - 1, z, Material.SPRUCE_PLANKS);
                canvas.set(x, DOCK - 2, z, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
                if (Math.abs(x - px) == 5) runs.fence(x, DOCK, z, Material.SPRUCE_FENCE);
            }
            for (int x = px - 5; x <= px + 5; x++) runs.fence(x, DOCK, end + 1, Material.SPRUCE_FENCE);
            for (int x = px - 5; x <= px + 5; x++) canvas.set(x, DOCK - 1, end + 1, Material.STRIPPED_SPRUCE_LOG, "axis=x");
            ReachProps.crates(new ReachBrush(canvas, px - 4, DOCK, end - 3, 0), px);
            if (px != 0) crane(px + 4, end - 3);
            canvas.set(px + 4, DOCK, end - 1, Material.POLISHED_DEEPSLATE_WALL, "up=true");
            marks.add(new Mark("pier_" + (px < 0 ? "west" : px > 0 ? "east" : "mid"), px, DOCK, lip + 4, 0F));
        }
        kit.tower(new ReachBrush(canvas, -48, DOCK, lip - 18, 0), 9, 36, ReachKit.Style.HARBOUR, 41);
        kit.tower(new ReachBrush(canvas, 40, DOCK, lip - 18, 0), 9, 32, ReachKit.Style.HARBOUR, 42);
        marks.add(new Mark("dockmaster", -44, DOCK, lip - 21, 180F));
        marks.add(new Mark("guildhall", 44, DOCK, lip - 21, 180F));
        marks.add(new Mark("npc_dockmaster_ebb", -44, DOCK, lip - 15, 180F));
        marks.add(new Mark("npc_captain_wren", 44, DOCK, lip - 15, 180F));
        marks.add(new Mark("npc_cassia_dawn", 2, DOCK, lip - 8, 180F));
        marks.add(new Mark("npc_highcrown_folk_porter", -14, DOCK, 1077, 180F));
        // Two sky-galleons moored between the piers, gangways out to them and mooring chains to the bollards.
        int[][] ships = {{-12, 0}, {12, 1}};
        for (int[] s : ships) {
            ReachAirship.build(new ReachBrush(canvas, s[0], DOCK - 1, lip + 6, 0), 34, 9, s[1] == 0 ? Material.GREEN_TERRACOTTA : Material.RED_TERRACOTTA, 700 + s[1], true);
            int side = s[0] < 0 ? -1 : 1, pierEdge = s[0] + side * 9, hull = s[0] + side * 4;
            for (int x = Math.min(pierEdge, hull); x <= Math.max(pierEdge, hull); x++) for (int z = lip + 19; z <= lip + 21; z++) {
                canvas.set(x, DOCK - 1, z, z == lip + 20 ? Material.SPRUCE_PLANKS : Material.STRIPPED_SPRUCE_LOG, z == lip + 20 ? "" : "axis=x");
                canvas.set(x, DOCK, z, z == lip + 20 ? Material.AIR : Material.SPRUCE_FENCE);
                canvas.set(x, DOCK + 1, z, Material.AIR);
            }
            for (int z : new int[] {lip + 10, lip + 32}) for (int x = Math.min(pierEdge, hull) + 1; x < Math.max(pierEdge, hull); x++) canvas.set(x, DOCK, z, Material.IRON_CHAIN, "axis=x");
        }
        // A third galleon coming in to moor, high over the cloud sea east of the piers, turned toward the city.
        ReachAirship.build(new ReachBrush(canvas, 78, DOCK + 22, lip + 64, 2), 30, 9, Material.BLUE_TERRACOTTA, 703);
        for (int x = -60; x <= 60; x++) for (int z = lip - 24; z <= end + 10; z++) reserve(x, z);
    }

    /** True where a pier's railing opens onto a gangway out to a moored ship. */
    private static boolean gangway(int px, int x, int z, int lip) {
        return z >= lip + 19 && z <= lip + 21 && (px == -24 && x == px + 3 || px == 24 && x == px - 3);
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
            // A garden, churchyard or lawn may hold the edge: what matters is that nothing is built on it.
            if (!c.land() || c.wet() || c.top() != top || !openAbove(x, top, z)) continue;
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

    /** True where nothing stands on a column's ground but air or a low plant: open lawn, paving, a garden's edge. */
    private boolean openAbove(int x, int top, int z) {
        for (int y = top + 1; y <= top + 2; y++) {
            ReachCanvas.Spec spec = canvas.get(x, y, z);
            if (spec == null) continue;
            String st = spec.state();
            if (st.equals("minecraft:air") || st.contains("grass") || st.contains("fern") || st.contains("poppy") || st.contains("dandelion")
                || st.contains("tulip") || st.contains("allium") || st.contains("daisy") || st.contains("cornflower") || st.contains("bluet")) continue;
            return false;
        }
        return true;
    }

    /**
     * Rounds the street network's corners: a closing of the paved area (grow it by a few blocks, then shrink it back)
     * fills every inside corner where two ways meet with a curved fillet, so streets turn the way walked ground wears
     * rather than at right angles. Only free, level ground of the same terrace is paved.
     */
    private void roundCorners() {
        final int radius = 3;
        java.util.List<int[]> disk = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) if (dx * dx + dz * dz <= radius * radius) disk.add(new int[] {dx, dz});
        Map<Long, Integer> grown = new HashMap<>();
        for (Map.Entry<Long, double[]> e : streets.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue(), y = (int) e.getValue()[0];
            for (int[] d : disk) grown.putIfAbsent(key(x + d[0], z + d[1]), y);
        }
        List<int[]> fill = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : grown.entrySet()) {
            if (streets.containsKey(e.getKey())) continue;
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue(), y = e.getValue();
            boolean kept = true;
            for (int[] d : disk) {
                Integer level = grown.get(key(x + d[0], z + d[1]));
                if (level == null || level != y) { kept = false; break; }
            }
            if (!kept || reserved.contains(e.getKey()) || street(x, z) != y) continue;
            ReachTerrain.Column col = terrain.column(x, z);
            if (!col.land() || col.wet() || col.top() != y - 1) continue;
            if (!isAir(x, y, z) || !isAir(x, y + 1, z)) continue;
            ReachCanvas.Spec below = canvas.get(x, y - 1, z);
            if (below != null && !below.state().contains("grass") && !below.state().contains("dirt") && !below.state().contains("moss")) continue;
            boolean nearLot = false;
            for (int[] l : lots) if (x >= l[0] && x <= l[2] && z >= l[1] && z <= l[3]) { nearLot = true; break; }
            if (!nearLot) fill.add(new int[] {x, z, y});
        }
        for (int[] f : fill) street(f[0], f[1], f[2], 0.7);
        // A strip of grass a block or two wide between two paved ways reads as a mistake: pave it through.
        for (int pass = 0; pass < 2; pass++) {
            List<int[]> slivers = new ArrayList<>();
            for (Map.Entry<Long, double[]> e : new ArrayList<>(streets.entrySet())) {
                int x = (int) (e.getKey() >> 32), z = e.getKey().intValue(), y = (int) e.getValue()[0];
                for (int[] d : new int[][] {{1, 0}, {0, 1}}) for (int gap = 1; gap <= 2; gap++) {
                    int fx = x + d[0] * (gap + 1), fz = z + d[1] * (gap + 1);
                    double[] far = streets.get(key(fx, fz));
                    if (far == null || (int) far[0] != y) continue;
                    boolean ok = true;
                    for (int k = 1; k <= gap && ok; k++) {
                        int gx = x + d[0] * k, gz = z + d[1] * k;
                        ReachTerrain.Column col = terrain.column(gx, gz);
                        ReachCanvas.Spec below = canvas.get(gx, y - 1, gz);
                        ok = !streets.containsKey(key(gx, gz)) && !reserved.contains(key(gx, gz)) && col.land() && !col.wet() && col.top() == y - 1
                            && isAir(gx, y, gz) && (below == null || below.state().contains("grass") || below.state().contains("dirt") || below.state().contains("moss"));
                    }
                    if (ok) for (int k = 1; k <= gap; k++) slivers.add(new int[] {x + d[0] * k, z + d[1] * k, y});
                }
            }
            for (int[] s : slivers) street(s[0], s[1], s[2], 0.7);
        }
    }

    /** Signposts where the ways divide, each arm naming what lies that way. */
    private void waymarks() {
        ReachProps.waymark(canvas, 9, CROWN, 866, new String[] {"Kingsgate", "The Vale"}, new String[] {"Highcrown", "Keep"},
            new String[] {"Market", "Docks"}, new String[] {"Hall of", "Callings"});
        ReachProps.waymark(canvas, 9, CROWN, 902, new String[] {"Plaza", "Kingsgate"}, new String[] {"Lantern", "Fane"},
            new String[] {"Market", "Ward"}, null);
        ReachProps.waymark(canvas, 9, MARKET, 921, new String[] {"Crown", "Ward"}, new String[] {"Guild", "Street"},
            new String[] {"Dock", "Quarter"}, new String[] {"Crafters'", "Row"});
        ReachProps.waymark(canvas, -9, DOCK, 997, new String[] {"Market", "Ward"}, new String[] {"Tethered", "Gull"},
            new String[] {"Sky", "Piers"}, null);
        for (int[] p : new int[][] {{9, CROWN, 866}, {9, CROWN, 902}, {9, MARKET, 921}, {-9, DOCK, 997}}) reserve(p[0], p[2]);
    }

    /** True beyond the ring wall on the east, where no house of the city may stand. */
    private static boolean outsideRing(int x, int z) {
        double a = Math.toDegrees(Math.atan2(z - 905, x));
        return a >= -32 && a <= 77 && Math.hypot(x, z - 905) > 156;
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
            // The market square has its own stalls and garlands; elsewhere a lamp every so often and the rest life.
            boolean market = Math.abs(x) <= 34 && z >= 918 && z <= 978;
            switch (i++ % 6) {
                case 0 -> { if (!market) ReachProps.lamp(b, Material.DARK_OAK_LOG, Material.SPRUCE_TRAPDOOR); }
                case 1 -> ReachProps.crates(b, h);
                case 2 -> ReachProps.bench(b, Material.SPRUCE_STAIRS, Material.SPRUCE_TRAPDOOR);
                case 3 -> { if (isAir(x + 1, y, z) && isAir(x + 1, y, z + 1) && isAir(x, y, z + 2)) ReachProps.cart(b, h); }
                case 4 -> ReachProps.planter(b, 2, h);
                default -> ReachProps.trough(b);
            }
        }
        // The Watch walks the avenue from the plaza down to the docks.
        int[][] route = {{-8, 852}, {-8, 893}, {-3, 930}, {-3, 965}, {-3, 995}, {-3, 1030}};
        for (int k = 0; k < route.length; k++) marks.add(new Mark("watch_" + k, route[k][0], street(route[k][0], route[k][1]), route[k][1], 0F));
    }
}
