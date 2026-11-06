package net.nerdsmp.paper.reach;

import java.util.List;

import static net.nerdsmp.paper.reach.ReachNoise.clamp;
import static net.nerdsmp.paper.reach.ReachNoise.lerp;
import static net.nerdsmp.paper.reach.ReachNoise.smoothstep;

/**
 * The shape of the Wardens' Reach as pure functions of (x, z): which zone a column belongs to, where the land
 * is, how high its top is, how far its underside hangs, and where rivers, the lake and roads run. Bukkit-free and
 * immutable, so the chunk generator can call it from worker threads and tests can draw it without a server.
 * The layout follows docs/vault/20 World/World Overview.md.
 */
public final class ReachTerrain {
    public static final long SEED = 7_221_903_118L;
    /** The generated rectangle; beyond it the world is empty sky. */
    public static final int MIN_X = -1300, MAX_X = 1900, MIN_Z = -1200, MAX_Z = 1200;
    public static final int CLOUD_Y = -56;
    /** A player below this height has fallen off the Reach and is hauled back. */
    public static final int RESCUE_Y = -46;
    public static final int LAKE_LEVEL = 100;
    static final int FLOOR_Y = -44;

    public enum Zone {
        SKY("sky", "The Open Sky", ""),
        HIGHCROWN("highcrown", "Highcrown", ""),
        GOLDENREACH("goldenreach", "Goldenreach Vale", "1-7"),
        BARROWDOWNS("barrowdowns", "The Barrowdowns", "6-11"),
        THORNWOOD("thornwood", "Thornwood", "10-15"),
        MISTMERE("mistmere", "Mistmere", "14-19"),
        IRONSPINE("ironspine", "Ironspine Highlands", "18-23"),
        SUNSCAR("sunscar", "Sunscar Breaks", "21-25"),
        SCAR("ashen_scar", "The Ashen Scar", "25-30"),
        ARCHIVE("archive_isle", "The Archive Isle", "22-26");

        private final String id, title, levels;
        Zone(String id, String title, String levels) { this.id = id; this.title = title; this.levels = levels; }
        public String id() { return id; }
        public String title() { return title; }
        public String levels() { return levels; }
    }

    /**
     * One column of the world. {@code water} and {@code fall} are {@link Integer#MIN_VALUE} when absent.
     * {@code across} is the distance from the nearest road's centre line (large away from roads); {@code cut} is how far the ground
     * stands above a road beside it (a cutting's wall), {@code fill} how far a road stands above the natural ground
     * (an embankment); {@code roadLevel} is the road surface those are measured from.
     */
    public record Column(Zone zone, boolean land, int top, int bottom, int water, int fall,
                         double edge, double road, double river, double lake, double terrace,
                         double across, double cut, double fill, int roadLevel) {
        public boolean wet() { return water > top; }

        /** The same column showing another zone's ground (see {@link ReachTerrain#groundZone}). */
        public Column withZone(Zone ground) {
            return ground == zone ? this : new Column(ground, land, top, bottom, water, fall, edge, road, river, lake, terrace, across, cut, fill, roadLevel);
        }
        public boolean carriageway() { return across < 3.2; }
    }

    private record Site(Zone zone, double x, double z) { }
    /** A flattened town platform: centre, radius, height and the distance over which it blends out. */
    record Pad(String id, double x, double z, double radius, double height, double falloff) { }

    private static final Site[] RING = {
        new Site(Zone.GOLDENREACH, 0, 520),
        new Site(Zone.BARROWDOWNS, -650, 420),
        new Site(Zone.THORNWOOD, -770, -280),
        new Site(Zone.MISTMERE, -220, -800),
        new Site(Zone.IRONSPINE, 560, -620),
        new Site(Zone.SUNSCAR, 790, 260),
    };
    /** Where the border ridges open for a road. */
    static final double[][] PASSES = {
        {-330, 450}, {-880, 80}, {-560, -570}, {110, -690}, {700, -200},
    };
    static final double SCAR_X = 0, SCAR_Z = -140, SCAR_R = 330;
    static final double RIFT_X = -10, RIFT_Z = -150, RIFT_R = 62;
    static final double ISLE_X = 1520, ISLE_Z = -80, ISLE_R = 255;
    static final double LAKE_X = -220, LAKE_Z = -800, LAKE_RX = 260, LAKE_RZ = 170;
    /** Direction from the Scar's centre to the Emberpass, the one gap in the Brimwall. */
    private static final double EMBERPASS = Math.atan2(-330 - SCAR_Z, 290 - SCAR_X);

    private static final List<Pad> FIXED_PADS = List.of(
        new Pad("highcrown", 0, 905, 165, 107, 45),
        new Pad("highcrown_keep", 112, 868, 46, ReachTerrain.CROWN_TOP + 12, 18),
        new Pad("last_light", 285, -325, 34, 112, 20),
        // The Wardens' Bridge's head on the Sunscar's east lip, where the Spine ends (ReachWardensBridge).
        new Pad("wardens_bridgehead", 1146, 8, 13, 114, 16)
    );
    static final double MILLBROOK_X = 40, MILLBROOK_Z = 560;

    /**
     * Highcrown climbs in three terraces from the docks to the keep, each six blocks above the last, their edges
     * arcs round a point by the keep so the city wraps its hill (see the vault's Shovel Study). These are top-block
     * heights; people stand one above.
     */
    static final int CROWN_TOP = 119, MARKET_TOP = 113, DOCK_TOP = 107;
    static final double CROWN_RING_X = 40, CROWN_RING_Z = 840, CROWN_RADIUS = 85, MARKET_RADIUS = 150;

    /** The terrace height Highcrown's ground is cut to at a point: the Crown Ward, the Market Ward or the Dock Quarter. */
    static int highcrownTop(double x, double z) {
        if (z < 834) return CROWN_TOP;
        double r = Math.hypot(x - CROWN_RING_X, z - CROWN_RING_Z);
        return r < CROWN_RADIUS ? CROWN_TOP : r < MARKET_RADIUS ? MARKET_TOP : DOCK_TOP;
    }

    /**
     * Highcrown's ground: the stepped terraces inside the walls, and outside them (past the ring wall, and across
     * the gorge on the west bank) the same three levels eased into one smooth slope. The steps belong to the city's
     * walled streets; carried out into the country they read as terrain sliced off in lines.
     */
    static double highcrownGround(double x, double z) {
        double stepped = highcrownTop(x, z);
        double outside = Math.max(smoothstep(158, 178, Math.hypot(x, z - 905)), 1 - smoothstep(-24, -8, x - goldwaterX(z)));
        if (outside <= 0) return stepped;
        double r = Math.hypot(x - CROWN_RING_X, z - CROWN_RING_Z);
        double crown = Math.max(1 - smoothstep(72, 104, r), 1 - smoothstep(816, 852, z));
        double market = Math.max(crown, 1 - smoothstep(132, 172, r));
        double smooth = DOCK_TOP + (MARKET_TOP - DOCK_TOP) * market + (CROWN_TOP - MARKET_TOP) * crown;
        return lerp(stepped, smooth, outside);
    }

    /** The Goldwater's line past Highcrown, as x at a given z (it runs south-west through the city's gorge). */
    static double goldwaterX(double z) {
        double[][] p = GOLDWATER;
        if (z <= p[10][1]) return p[10][0];
        for (int i = 10; i < p.length - 1; i++)
            if (z <= p[i + 1][1]) return lerp(p[i][0], p[i + 1][0], (z - p[i][1]) / (p[i + 1][1] - p[i][1]));
        return p[p.length - 1][0];
    }
    /** Town platforms. Millbrook's height follows the river, so it is set once the river is known. */
    private volatile List<Pad> pads = FIXED_PADS;
    private int millbrookFloor;

    static final double[][] GOLDWATER = {
        {-930, 240}, {-820, 300}, {-700, 330}, {-560, 400}, {-430, 455}, {-300, 470}, {-170, 505}, {-60, 540},
        {40, 600}, {70, 680}, {35, 760}, {-50, 830}, {-110, 905}, {-150, 980}, {-170, 1080}, {-185, 1180}
    };
    static final double[][] LONG_FALL = {{-150, -945}, {-160, -1010}, {-175, -1100}, {-185, -1260}};

    /** Where a river leaves the lip: the falling water's position, half-width and top. */
    record Fall(double x, double z, double halfWidth, int top) { }
    static final double[][] SPINE = {
        {0, 770}, {12, 660}, {40, 575}, {-100, 505}, {-230, 470}, {-330, 452}, {-480, 445}, {-640, 455},
        {-790, 320}, {-880, 80}, {-860, -80}, {-760, -220}, {-700, -420}, {-560, -570}, {-430, -690},
        {-300, -600}, {-100, -600}, {110, -680}, {300, -650}, {600, -560}, {700, -380}, {700, -200},
        {760, 40}, {830, 320}, {1000, 200}, {1146, 8}
    };
    static final double[][] EMBER_ROAD = {{600, -560}, {420, -430}, {285, -325}, {150, -250}, {60, -200}};

    private final ReachNoise warp = new ReachNoise(SEED), warp2 = new ReachNoise(SEED + 1), low = new ReachNoise(SEED + 2),
        mid = new ReachNoise(SEED + 3), ridge = new ReachNoise(SEED + 4), edgeNoise = new ReachNoise(SEED + 5),
        misc = new ReachNoise(SEED + 6), cloud = new ReachNoise(SEED + 7);
    private final ReachPath goldwater = new ReachPath(GOLDWATER, 3.5, 7.5);
    private final ReachPath longFall = new ReachPath(LONG_FALL, 5, 6);
    /** Ember Road's curve, starting exactly on the Spine's curve where the two meet, so the junction has no gap. */
    private static double[][] emberCurve() {
        double[] start = EMBER_ROAD[0];
        double[] best = SPINE_CURVE[0];
        for (double[] p : SPINE_CURVE) if (Math.hypot(p[0] - start[0], p[1] - start[1]) < Math.hypot(best[0] - start[0], best[1] - start[1])) best = p;
        double[][] route = EMBER_ROAD.clone();
        route[0] = best;
        return ReachPath.chaikin(route, 3);
    }

    /** How far a road's grading reaches into the hillside, and the tallest retaining wall at a cutting's verge. */
    static final double GRADE_REACH = 34, CUT_WALL = 3;
    private static final double[][] SPINE_CURVE = ReachPath.chaikin(SPINE, 3);
    private final ReachPath spine = new ReachPath(SPINE_CURVE, 2.6, 2.6);
    private final ReachPath emberRoad = new ReachPath(emberCurve(), 2.6, 2.6);

    private final List<Fall> falls;

    public ReachTerrain() {
        goldwater.sample((x, z) -> shaped(x, z) - 3);
        goldwater.smooth(20);
        goldwater.values[0] = Math.min(goldwater.values[0], 116);
        goldwater.neverRise(0.01);
        // Millbrook sits just above the Goldwater, like a river town should, not on a bluff above a cutting.
        ReachPath.Hit ford = goldwater.nearest(MILLBROOK_X, MILLBROOK_Z + 15, 300);
        int padTop = (int) Math.floor(ford == null ? 100 : ford.value()) + 3;
        millbrookFloor = padTop + 1;
        List<Pad> all = new java.util.ArrayList<>(FIXED_PADS);
        all.add(new Pad("millbrook", MILLBROOK_X, MILLBROOK_Z, 52, padTop, 34));
        pads = List.copyOf(all);
        longFall.sample((x, z) -> LAKE_LEVEL);
        falls = List.of(fall(goldwater), fall(longFall));
        for (ReachPath road : List.of(spine, emberRoad)) {
            road.sample((x, z) -> {
                double h = shaped(x, z);
                ReachPath.Hit river = goldwater.nearest(x, z, 12);
                if (river != null) h = Math.max(h, river.value() + 3);
                double lake = lakeRadius(x, z);
                if (lake < 1.25) h = Math.max(h, LAKE_LEVEL + 2);
                return h;
            });
            road.smooth(6);
            road.limitSlope(0.3);
        }
        ReachPath.Hit join = spine.nearest(emberRoad.xs[0], emberRoad.zs[0], 30);
        if (join != null) {
            int n = Math.min(40, emberRoad.values.length);
            for (int i = 0; i < n; i++) {
                double t = i / (double) n, ease = t * t * (3 - 2 * t);
                emberRoad.values[i] = join.value() * (1 - ease) + emberRoad.values[i] * ease;
            }
        }
    }

    /** How far a point is from the nearest road's centre line, looking no further than {@code reach} (beyond: MAX_VALUE). */
    public double roadDistance(double x, double z, double reach) {
        double best = Double.MAX_VALUE;
        for (ReachPath road : List.of(spine, emberRoad)) {
            ReachPath.Hit hit = road.nearest(x, z, reach);
            if (hit != null) best = Math.min(best, hit.distance());
        }
        return best;
    }

    /** The roads themselves (the Spine, then Ember Road), for what's built along them. */
    List<ReachPath> roads() { return List.of(spine, emberRoad); }

    /** The main road's samples within {@code radius} of a point, in order, as {x, z}: for laying out a street. */
    public List<double[]> roadSamples(double x, double z, double radius) {
        List<double[]> out = new java.util.ArrayList<>();
        for (ReachPath road : List.of(spine, emberRoad))
            for (int i = 0; i < road.size(); i++) if (Math.hypot(road.xs[i] - x, road.zs[i] - z) < radius) out.add(new double[] {road.xs[i], road.zs[i]});
        return out;
    }

    /** Where a road crosses water: dry bank to dry bank, and the height its deck should run at. */
    public record Crossing(double x0, double z0, double x1, double z1, int deck, int road, int from, int to) { }

    /** Every place a road meets a river or the lake, found by walking the roads' samples. */
    public List<Crossing> crossings() {
        List<Crossing> out = new java.util.ArrayList<>();
        List<ReachPath> all = List.of(spine, emberRoad);
        for (int r = 0; r < all.size(); r++) {
            ReachPath road = all.get(r);
            int start = -1;
            for (int i = 0; i < road.size(); i++) {
                boolean wet = column((int) Math.floor(road.xs[i]), (int) Math.floor(road.zs[i])).wet();
                if (wet && start < 0) start = i;
                if (!wet && start >= 0) {
                    int a = Math.max(0, start - 2), b = Math.min(road.size() - 1, i + 1);
                    int deck = Integer.MIN_VALUE;
                    for (int k = a; k <= b; k++) {
                        Column c = column((int) Math.floor(road.xs[k]), (int) Math.floor(road.zs[k]));
                        deck = Math.max(deck, c.wet() ? c.water() + 3 : c.top());
                    }
                    out.add(new Crossing(road.xs[a], road.zs[a], road.xs[b], road.zs[b], deck, r, a, b));
                    start = -1;
                }
            }
        }
        return out;
    }

    /** The first sample past the lip is where the river pours off. */
    private Fall fall(ReachPath river) {
        for (int i = 1; i < river.size(); i++)
            if (continentEdge(river.xs[i], river.zs[i]) <= 0)
                return new Fall(river.xs[i], river.zs[i], river.halfWidths[i], (int) Math.floor(river.values[i - 1]));
        throw new IllegalStateException("A river never reaches the lip");
    }

    public List<Fall> falls() { return falls; }

    /** Where Millbrook's people stand: one above its platform. */
    public int millbrookFloor() { return millbrookFloor; }

    // ------------------------------------------------------------------ shape of the land

    /** Roughly the distance in blocks inside the continent's outer edge (negative outside). */
    double continentEdge(double x, double z) {
        double wx = x + 110 * warp.fbm(x * 0.003, z * 0.003, 2), wz = z + 110 * warp2.fbm(x * 0.003, z * 0.003, 2);
        double e = Math.hypot(wx / 1150.0, (wz + 40) / 1080.0);
        // Big lobes and bays, then a ragged lip.
        double edge = 1.0 + 0.07 * edgeNoise.fbm(x * 0.0045 + 77, z * 0.0045, 2) + 0.03 * edgeNoise.fbm(x * 0.018, z * 0.018, 3);
        double main = (edge - e) * 1100;
        double prom = (1.0 - Math.hypot(x / 200.0, (z - 905) / 190.0) + 0.03 * edgeNoise.noise(x * 0.03, z * 0.03)) * 190;
        return Math.max(main, prom);
    }

    /** Broken-off islets floating just beyond an edge: positive inside one. */
    double isletEdge(double x, double z, double outside) {
        if (outside > -18 || outside < -150) return -1;
        double blob = edgeNoise.fbm(x * 0.012 - 333, z * 0.012 + 129, 2);
        double fade = smoothstep(-150, -50, outside);
        return (blob - 0.52) * 160 * fade;
    }

    /** Distance outside the rift (negative inside it). */
    double riftEdge(double x, double z) {
        double r = RIFT_R * (1 + 0.22 * edgeNoise.fbm(x * 0.02 + 40, z * 0.02, 2));
        return Math.hypot(x - RIFT_X, z - RIFT_Z) - r;
    }

    double isleEdge(double x, double z) {
        double wx = x + 40 * warp.noise(x * 0.008 + 5, z * 0.008), wz = z + 40 * warp2.noise(x * 0.008, z * 0.008 + 5);
        double r = ISLE_R * (1 + 0.16 * edgeNoise.fbm(x * 0.009 - 90, z * 0.009, 3));
        return r - Math.hypot((wx - ISLE_X) * 1.1, (wz - ISLE_Z) * 0.92);
    }

    /** Positive inside land, with the rift cut out. */
    double landEdge(double x, double z) {
        double continent = continentEdge(x, z), isle = isleEdge(x, z);
        double main = Math.min(continent, riftEdge(x, z));
        double islets = Math.max(isletEdge(x, z, continent), isletEdge(x, z, isle));
        return Math.max(Math.max(main, isle), islets);
    }

    /** True for a broken-off islet rather than the continent or the Archive Isle. */
    boolean islet(double x, double z) {
        return continentEdge(x, z) <= 0 && isleEdge(x, z) <= 0 && landEdge(x, z) > 0;
    }

    double scarRadius(double x, double z) {
        double r = SCAR_R * (1 + 0.09 * edgeNoise.fbm(x * 0.006 + 300, z * 0.006, 3));
        return Math.hypot(x - SCAR_X, z - SCAR_Z) / r;
    }

    double lakeRadius(double x, double z) {
        double n = 0.07 * edgeNoise.fbm(x * 0.012 + 500, z * 0.012, 3);
        return Math.hypot((x - LAKE_X) / LAKE_RX, (z - LAKE_Z) / LAKE_RZ) - n;
    }

    // ------------------------------------------------------------------ zones

    public Zone zoneAt(double x, double z) {
        if (isleEdge(x, z) > 0) return Zone.ARCHIVE;
        if (landEdge(x, z) <= 0) return Zone.SKY;
        if (scarRadius(x, z) < 1.0) return Zone.SCAR;
        if (z > 765 + 12 * misc.noise(x * 0.02, 0) && Math.hypot(x / 210.0, (z - 905) / 200.0) < 1.0) return Zone.HIGHCROWN;
        return ring(x, z).zone;
    }

    /**
     * The zone whose ground and plants show at a column. Heights already blend across a border; this makes the
     * ground do the same. Between two zones the line is pushed back and forth by noise, up to about twenty blocks
     * either way, so heather runs out into the fields in tongues and the badlands' red eats into the grass in bays,
     * never along a ruled line. The Scar's ash frays out over its rim the same way.
     */
    public Zone groundZone(int bx, int bz, Zone zone) {
        if (zone == Zone.SKY || zone == Zone.HIGHCROWN || zone == Zone.ARCHIVE) return zone;
        double x = bx + 0.5, z = bz + 0.5;
        double n = 0.62 * misc.noise(x * 0.028 + 210, z * 0.028 - 75) + 0.38 * mid.noise(x * 0.085 - 31, z * 0.085 + 44);
        double sr = scarRadius(x, z);
        if (Math.abs(sr - 1) < 0.08) {
            boolean ash = sr < 1 + 0.055 * n;
            if (ash) return Zone.SCAR;
            if (zone == Zone.SCAR) return ring(x, z).zone;
        }
        if (zone == Zone.SCAR) return zone;
        double px = wx(x, z), pz = wz(x, z), d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        Site a = RING[0], b = RING[0];
        for (Site site : RING) {
            double d = Math.hypot(px - site.x, pz - site.z);
            if (d < d1) { d2 = d1; b = a; d1 = d; a = site; } else if (d < d2) { d2 = d; b = site; }
        }
        if (d2 - d1 > 24) return zone;
        // The border's displacement has a sign fixed by the pair, so both sides agree where it lies.
        double shift = 21 * n * (a.zone.ordinal() < b.zone.ordinal() ? 1 : -1);
        return d2 - d1 > shift ? a.zone : b.zone;
    }

    private double wx(double x, double z) { return x + 70 * warp.fbm(x * 0.005 + 11, z * 0.005, 2); }
    private double wz(double x, double z) { return z + 70 * warp2.fbm(x * 0.005, z * 0.005 + 11, 2); }

    private Site ring(double x, double z) {
        double px = wx(x, z), pz = wz(x, z), best = Double.MAX_VALUE;
        Site nearest = RING[0];
        for (Site site : RING) {
            double d = Math.hypot(px - site.x, pz - site.z);
            if (d < best) { best = d; nearest = site; }
        }
        return nearest;
    }

    /** Height from the zones, the border ridges, the Scar and the town platforms, before water and roads. */
    double shaped(double x, double z) {
        double px = wx(x, z), pz = wz(x, z);
        double[] d = new double[RING.length];
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        int i1 = 0, i2 = 0;
        for (int i = 0; i < RING.length; i++) {
            d[i] = Math.hypot(px - RING[i].x, pz - RING[i].z);
            if (d[i] < d1) { d2 = d1; i2 = i1; d1 = d[i]; i1 = i; } else if (d[i] < d2) { d2 = d[i]; i2 = i; }
        }
        double sum = 0, total = 0;
        for (int i = 0; i < RING.length; i++) {
            double w = Math.exp(-(d[i] - d1) / 42.0);
            if (w < 0.015) continue;
            sum += w * recipe(RING[i].zone, x, z);
            total += w;
        }
        double h = sum / total;

        // Border ridges between neighbouring zones, opened at the passes.
        double ridgeFactor = 1 - smoothstep(0, 75, d2 - d1);
        if (ridgeFactor > 0) {
            Zone a = RING[i1].zone, b = RING[i2].zone;
            double scale = (a == Zone.SUNSCAR && b == Zone.GOLDENREACH) || (b == Zone.SUNSCAR && a == Zone.GOLDENREACH) ? 1.9 : 1.0;
            for (double[] pass : PASSES) ridgeFactor *= smoothstep(28, 85, Math.hypot(x - pass[0], z - pass[1]));
            h += scale * 19 * ridgeFactor * (0.5 + 0.5 * ridge.ridged(x * 0.012, z * 0.012, 3));
        }

        // The Ashen Scar: a crater ringed by the Brimwall, open only at the Emberpass.
        double r = scarRadius(x, z);
        if (r < 1.14) {
            double floor = 82 + 4 * mid.fbm(x * 0.02, z * 0.02, 3) + 7 * low.fbm(x * 0.008, z * 0.008, 3);
            double crest = 158 + 14 * ridge.ridged(x * 0.02, z * 0.02, 3);
            double angle = Math.atan2(z - SCAR_Z, x - SCAR_X);
            double gap = Math.abs(Math.atan2(Math.sin(angle - EMBERPASS), Math.cos(angle - EMBERPASS)));
            double pass = 1 - smoothstep(0.05, 0.15, gap);
            crest = lerp(crest, 108, pass);
            double scar;
            if (r < 0.80) scar = floor;
            else if (r < 0.95) scar = lerp(floor, crest, Math.pow(smoothstep(0.80, 0.95, r), 1.6));
            else scar = crest;
            h = lerp(h, scar, 1 - smoothstep(0.98, 1.13, r));
            double toRift = riftEdge(x, z);
            if (toRift < 95) h = lerp(h, 70 + 6 * mid.noise(x * 0.05, z * 0.05), smoothstep(95, 0, toRift) * 0.75);
        }

        // Town platforms: Highcrown's terraces, the keep's crag, Millbrook's riverside shelf.
        for (Pad pad : pads) {
            double dist = Math.hypot(x - pad.x, z - pad.z);
            double height = pad.id.equals("highcrown") ? highcrownGround(x, z) : pad.height;
            if (dist < pad.radius + pad.falloff) h = lerp(h, height, 1 - smoothstep(pad.radius, pad.radius + pad.falloff, dist));
        }
        double lake = lakeRadius(x, z);
        if (lake < 1.45) h = Math.max(h, LAKE_LEVEL + 1 + Math.max(0, lake - 1) * 22);
        return h;
    }

    private double recipe(Zone zone, double x, double z) {
        return switch (zone) {
            case GOLDENREACH -> {
                double h = 101 + 15 * low.fbm(x * 0.0035, z * 0.0035, 4) + 3.5 * mid.fbm(x * 0.015, z * 0.015, 3);
                yield terraced(h, terraceMask(x, z), 3);
            }
            case BARROWDOWNS -> 104 + 13 * low.fbm(x * 0.003, z * 0.003, 3) + 4.5 * mid.fbm(x * 0.011, z * 0.011, 2)
                + 9 * Math.pow(Math.max(0, ridge.ridged(x * 0.02, z * 0.02, 2) - 0.72) / 0.28, 2);
            case THORNWOOD -> 109 + 24 * low.fbm(x * 0.004, z * 0.004, 4) + 6 * mid.fbm(x * 0.016, z * 0.016, 3)
                - 11 * Math.pow(1 - Math.abs(misc.noise(x * 0.0028, z * 0.0028)), 10);
            case MISTMERE -> 105 + 10 * low.fbm(x * 0.0035, z * 0.0035, 3) + 3 * mid.fbm(x * 0.014, z * 0.014, 2);
            case IRONSPINE -> {
                // Big massifs with sharp ridges, falling to green valleys.
                double massif = smoothstep(-0.35, 0.45, low.fbm(x * 0.0022 + 40, z * 0.0022, 3));
                double r = ridge.ridged(x * 0.0028, z * 0.0028, 6);
                yield 112 + massif * (40 + 105 * Math.pow(r, 1.8)) + 14 * low.fbm(x * 0.006, z * 0.006, 3) + 4 * mid.fbm(x * 0.03, z * 0.03, 2);
            }
            case SUNSCAR -> {
                double m = 112 + 38 * low.fbm(x * 0.004, z * 0.004, 4);
                double mesa = terraced(m, 1.0, 8);
                double canyon = 34 * Math.pow(1 - Math.abs(misc.noise(x * 0.005 + 70, z * 0.005)), 10);
                yield mesa - canyon + 2 * mid.fbm(x * 0.03, z * 0.03, 2);
            }
            default -> 104;
        };
    }

    /** 0..1: how strongly Goldenreach's slopes are cut into wheat terraces here. */
    double terraceMask(double x, double z) {
        return smoothstep(0.02, 0.22, misc.fbm(x * 0.006 - 200, z * 0.006, 2));
    }

    /**
     * Goldenreach's fields: a grid of plots turned to follow the valley, each with its own crop. Returns -1 off the
     * fields, 0 on a hedged border between plots, or 1..6 for the plot's crop (ripe wheat, green wheat, carrots,
     * potatoes, beetroot, fallow). Fields only lie on the terraced slopes near Millbrook.
     */
    public int field(double x, double z) {
        double fromTown = Math.hypot(x - MILLBROOK_X, z - MILLBROOK_Z);
        if (terraceMask(x, z) < 0.3 || fromTown > 360 || fromTown < 85) return -1;
        double a = 0.52, u = x * Math.cos(a) + z * Math.sin(a), v = -x * Math.sin(a) + z * Math.cos(a);
        double cu = 23, cv = 15;
        int iu = (int) Math.floor(u / cu), iv = (int) Math.floor(v / cv);
        double fu = u - iu * cu, fv = v - iv * cv;
        if (fu < 1 || fv < 1) return 0;
        long h = ReachFlora.hash(iu * 977 + 3, iv * 131 - 9);
        int roll = (int) Math.floorMod(h, 10L);
        return roll < 5 ? 1 : roll == 5 ? 2 : roll == 6 ? 3 : roll == 7 ? 4 : roll == 8 ? 5 : 6;
    }

    /** Cuts a height into flat steps {@code step} high with short risers. */
    static double terraced(double h, double strength, double step) {
        if (strength <= 0) return h;
        double base = Math.floor(h / step) * step;
        double f = (h - base) / step;
        double stepped = base + step * smoothstep(0.78, 1.0, f);
        return lerp(h, stepped, strength);
    }

    // ------------------------------------------------------------------ columns

    public Column column(int bx, int bz) {
        double x = bx + 0.5, z = bz + 0.5;
        double edge = landEdge(x, z);
        boolean isle = isleEdge(x, z) > 0;
        Zone zone = zoneAt(x, z);
        if (edge <= 0) return sky(x, z, zone);

        double h;
        if (isle) {
            h = 172 + 16 * low.fbm(x * 0.006, z * 0.006, 3) + 4 * mid.fbm(x * 0.02, z * 0.02, 2);
        } else {
            h = shaped(x, z);
        }
        // A gentle roll-off at the lip, before any water is placed so banks still hold it.
        if (edge < 10) h -= (10 - edge) * 0.3;
        int water = Integer.MIN_VALUE;
        double riverDistance = Double.MAX_VALUE, lakeValue = Double.MAX_VALUE, roadValue = 0, terrace = 0;
        double across = Double.MAX_VALUE, cut = 0, fill = 0;
        int roadLevel = Integer.MIN_VALUE;

        if (!isle) {
            if (zone == Zone.GOLDENREACH) terrace = terraceMask(x, z);
            double lake = lakeRadius(x, z);
            lakeValue = lake;
            if (lake < 1.0) {
                double bed = LAKE_LEVEL - 2 - 12 * smoothstep(1.0, 0.45, lake) - 1.5 * mid.noise(x * 0.05, z * 0.05);
                h = Math.min(h, bed);
                water = LAKE_LEVEL;
            }
            for (ReachPath river : List.of(goldwater, longFall)) {
                ReachPath.Hit hit = river.nearest(x, z, 36);
                if (hit == null) continue;
                double w = hit.halfWidth() * (1 + 0.15 * mid.noise(x * 0.03, z * 0.03));
                double inside = hit.distance() - w;
                riverDistance = Math.min(riverDistance, inside);
                if (inside < 0) {
                    double depth = 3.4 * (1 - Math.pow(hit.distance() / w, 2));
                    h = Math.min(h, hit.value() - 1 - depth);
                    water = Math.max(water, (int) Math.floor(hit.value()));
                } else {
                    // Carve a valley down to the water, then hold a lip of land along the water's edge.
                    double bank = hit.value() + 1.2 + inside * 0.3;
                    if (h > bank) h = lerp(bank, h, smoothstep(0, 20, inside));
                    if (inside < 3) h = Math.max(h, hit.value() + 1);
                }
            }
            for (ReachPath road : List.of(spine, emberRoad)) {
                ReachPath.Hit hit = road.nearest(x, z, GRADE_REACH);
                if (hit == null) continue;
                if (hit.distance() <= 12) {
                    roadValue = Math.max(roadValue, 1 - smoothstep(hit.halfWidth() - 0.5, hit.halfWidth() + 1.5, hit.distance()));
                    across = Math.min(across, hit.distance());
                }
                if (riverDistance < 2) continue;
                // An engineered road: a level carriageway with sharp edges, graded the way a real road is. Through
                // high ground it runs in a cutting: a retaining wall at most CUT_WALL high at the verge, then the
                // hillside battered back one up for one across until it meets the natural slope. Over low ground it
                // runs on an embankment whose sides fall away one for one. Small differences ease into a verge.
                double hw = hit.halfWidth() + 0.6, d = hit.distance(), level = hit.value(), natural = h;
                if (d <= hw) {
                    h = level;
                    roadLevel = (int) Math.floor(level);
                    if (natural < level - 1) fill = Math.max(fill, level - natural);
                    continue;
                }
                double diff = natural - level;
                if (Math.abs(diff) <= 2.5) {
                    if (d <= hw + 4) h = lerp(level, natural, smoothstep(hw, hw + 4, d));
                } else if (diff > 0) {
                    double batter = d <= hw + 1.2 ? CUT_WALL : CUT_WALL + (d - hw - 1.2) * 1.05;
                    h = Math.min(h, level + batter);
                    if (d <= hw + 1.2) { cut = Math.max(cut, h - level); roadLevel = (int) Math.floor(level); }
                } else if (riverDistance > 12 && lakeValue > 1.3 && -diff <= 8) {
                    // Never over a valley a bridge spans, or past a modest fill: there the ground keeps its own shape.
                    h = Math.max(h, level - (d - hw) * 1.0);
                }
            }
        }
        int top = (int) Math.floor(h);
        if (water != Integer.MIN_VALUE && water <= top) water = Integer.MIN_VALUE;
        // The underside follows the outer edge, so the rift's walls drop the whole depth of the continent.
        double outer = isle ? isleEdge(x, z) : islet(x, z) ? edge : continentEdge(x, z);
        double inner = Math.min(Math.max(outer, 0), 400);
        // An inverted mountain: thick in the middle, lobed, with tapering rock spires hanging below.
        double thickness = 10 + 0.26 * inner + 14 * misc.fbm(x * 0.008, z * 0.008, 3) + 6 * mid.noise(x * 0.04, z * 0.04);
        // Spires shrink where the underside already hangs deep, so none is cut flat by the floor.
        double room = Math.max(0, top - thickness - FLOOR_Y);
        double drip = spires(x, z) * smoothstep(4, 40, edge) * clamp(room / 70.0, 0, 1);
        int bottom = (int) Math.max(FLOOR_Y, Math.floor(top - thickness - drip));
        bottom = Math.min(bottom, top - 3);
        return new Column(zone, true, top, bottom, water, Integer.MIN_VALUE, edge, roadValue, riverDistance, lakeValue, terrace,
            across, cut, fill, roadLevel);
    }

    /**
     * Hanging rock spires under the land: each cell of a jittered grid holds one cone of its own width and length,
     * so the underside reads as distinct stalactites rather than noise.
     */
    double spires(double x, double z) {
        final int cell = 34;
        int cx = Math.floorDiv((int) Math.floor(x), cell), cz = Math.floorDiv((int) Math.floor(z), cell);
        double deepest = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long h = ReachFlora.hash((cx + dx) * 7919 + 13, (cz + dz) * 104729 + 7);
            double u = (h & 0xFFFF) / 65536.0, v = ((h >>> 16) & 0xFFFF) / 65536.0;
            double size = ((h >>> 32) & 0xFFFF) / 65536.0, length = ((h >>> 48) & 0xFFFF) / 65536.0;
            if (size < 0.25) continue;
            double px = (cx + dx + 0.15 + 0.7 * u) * cell, pz = (cz + dz + 0.15 + 0.7 * v) * cell;
            double radius = 6 + 14 * size;
            double d = Math.hypot(x - px, z - pz) / radius;
            if (d >= 1) continue;
            deepest = Math.max(deepest, (14 + 52 * length) * Math.pow(1 - d, 1.7));
        }
        return deepest;
    }

    /** Empty sky: clouds far below, and falling water where a river leaves the lip. */
    private Column sky(double x, double z, Zone zone) {
        int fall = Integer.MIN_VALUE;
        for (Fall f : falls)
            if (Math.hypot(x - f.x, z - f.z) < f.halfWidth + 3) fall = f.top;
        return new Column(zone, false, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, fall,
            landEdge(x, z), 0, Double.MAX_VALUE, Double.MAX_VALUE, 0, Double.MAX_VALUE, 0, 0, Integer.MIN_VALUE);
    }

    /** Thickness of cloud at this column (0 for none). Blocky, like the reference art. */
    public int cloud(int bx, int bz) {
        if (bx < MIN_X || bx > MAX_X || bz < MIN_Z || bz > MAX_Z) return 0;
        // The Rift has no floor of cloud: looking down it, the dark goes on.
        if (Math.hypot(bx - RIFT_X, bz - RIFT_Z) < RIFT_R * 1.6 && riftEdge(bx, bz) < 10) return 0;
        double qx = Math.floorDiv(bx, 4) * 4, qz = Math.floorDiv(bz, 4) * 4;
        double n = cloud.fbm(qx * 0.0045, qz * 0.0045, 3);
        if (n < 0.28) return 0;
        for (Fall f : falls) if (Math.hypot(bx - f.x, bz - f.z) < 28) return 0;
        return n > 0.42 ? 4 : n > 0.34 ? 3 : 2;
    }

    /** True near a town, where roads are paved rather than rutted earth. */
    public boolean townRoad(double x, double z) {
        return Math.hypot(x, z - 905) < 240 || Math.hypot(x - MILLBROOK_X, z - MILLBROOK_Z) < 95;
    }

    /** Small noise used by the surface painter so texture breaks up in clusters, not speckles. */
    public double patch(double x, double z, double scale) { return misc.noise(x * scale + 900, z * scale - 400); }

    /** A finer second patch field, independent of {@link #patch}. */
    public double grain(double x, double z, double scale) { return mid.noise(x * scale - 700, z * scale + 260); }
}
