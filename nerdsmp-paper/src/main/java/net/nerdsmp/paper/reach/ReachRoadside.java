package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.reach.ReachTerrain.Column;
import net.nerdsmp.paper.reach.ReachTerrain.Zone;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * What makes a road read as a road and not a stripe of path: what stands beside it. Fences and gates where it runs
 * between fields, hedgerows and dry-stone walls elsewhere, a milestone every hundred blocks, lamps on the way into a
 * town, a wayside shrine with a bench now and then, cairns over the high passes, marsh lights in the fen and a
 * parapet wherever the road rides an embankment. Each stretch of road is its own feature, and nothing is placed
 * where another feature (a town, a bridge, a camp) already builds.
 */
final class ReachRoadside {
    /** Road samples per feature: about 190 blocks of road. */
    private static final int SEGMENT = 48;
    /** How far from the centre line the edge line runs: past the carriageway and a verge. */
    private static final double EDGE = 5.5;

    /** Answers whether another feature builds at (or right beside) a column. */
    interface Clearance { boolean blocked(int x, int z); }

    static List<ReachFeatures.Feature> features(ReachTerrain terrain, Clearance clearance) {
        List<ReachFeatures.Feature> out = new ArrayList<>();
        List<ReachPath> roads = terrain.roads();
        for (int r = 0; r < roads.size(); r++) {
            ReachPath road = roads.get(r);
            for (int start = 0, part = 0; start < road.size() - 1; start += SEGMENT, part++) {
                int from = start, to = Math.min(road.size() - 1, start + SEGMENT);
                double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                for (int i = from; i <= to; i++) {
                    minX = Math.min(minX, road.xs[i]); maxX = Math.max(maxX, road.xs[i]);
                    minZ = Math.min(minZ, road.zs[i]); maxZ = Math.max(maxZ, road.zs[i]);
                }
                // Wide enough for a waypoint thirteen blocks off the road and its five-deep plot.
                ReachFeatures.Footprint footprint = new ReachFeatures.Footprint((int) Math.floor(minX) - 19, (int) Math.floor(minZ) - 19,
                    (int) Math.ceil(maxX) + 19, (int) Math.ceil(maxZ) + 19);
                int index = r;
                out.add(new ReachFeatures.Feature("road_" + r + "_" + part, footprint,
                    t -> new ReachRoadside(t, clearance, road, index).build(from, to)));
            }
        }
        return out;
    }

    private final ReachTerrain terrain;
    private final Clearance clearance;
    private final ReachPath road;
    private final int roadIndex;
    private final ReachCanvas canvas = new ReachCanvas();
    private final ReachKit kit;
    private final List<ReachFeatures.Spot> spots = new ArrayList<>();
    /** Fences, walls and parapets, joined up once they're all known. */
    private final ReachRuns runs = new ReachRuns();

    private ReachRoadside(ReachTerrain terrain, Clearance clearance, ReachPath road, int roadIndex) {
        this.terrain = terrain;
        this.clearance = clearance;
        this.road = road;
        this.roadIndex = roadIndex;
        this.kit = new ReachKit(terrain);
    }

    private ReachFeatures.Built build(int from, int to) {
        edges(from, to);
        for (int i = from; i < to; i++) {
            long h = ReachFlora.hash(i * 31 + roadIndex * 7, roadIndex * 131 + 5);
            int side = (h & 1) == 0 ? 1 : -1;
            if (i % 25 == 12) milestone(i, 1);
            if (i % 110 == 70) shrine(i, side);
            if (i % 70 == 35) wayside(i, -side);
            if (lampy(i) && i % 6 == 0) lamp(i, (i / 6) % 2 == 0 ? 1 : -1);
            if (lampy(i) && i % 18 == 9) bench(i, -side);
            marker(i, side);
        }
        parapets(from, to);
        if (roadIndex == 1 && from == 0) signpost(0, 1);
        runs.write(canvas);
        return new ReachFeatures.Built(canvas, spots);
    }

    // ------------------------------------------------------------------ the frame along the road

    /** The unit direction of travel at a sample. */
    private double[] along(int i) {
        int a = Math.max(0, i - 1), b = Math.min(road.size() - 1, i + 1);
        double dx = road.xs[b] - road.xs[a], dz = road.zs[b] - road.zs[a], len = Math.max(1e-6, Math.hypot(dx, dz));
        return new double[] {dx / len, dz / len};
    }

    /** The column {@code offset} blocks to one side of sample {@code i} (positive is the left of travel). */
    private int[] beside(int i, double offset) {
        double[] u = along(i);
        return new int[] {(int) Math.floor(road.xs[i] - u[1] * offset), (int) Math.floor(road.zs[i] + u[0] * offset)};
    }

    /** The brush turn whose local front (-z) faces the road from a point on {@code side}. */
    private int facingRoad(int i, int side) {
        double[] u = along(i);
        double tx = u[1] * side, tz = -u[0] * side;
        if (Math.abs(tx) > Math.abs(tz)) return tx > 0 ? 1 : 3;
        return tz > 0 ? 2 : 0;
    }

    /** The brush turn whose local +x points at the road from {@code side} (for a lamp's bracket). */
    private int bracketToRoad(int i, int side) {
        double[] u = along(i);
        double tx = u[1] * side, tz = -u[0] * side;
        if (Math.abs(tx) > Math.abs(tz)) return tx > 0 ? 0 : 2;
        return tz > 0 ? 1 : 3;
    }

    /** Lamps line the last few hundred blocks into Highcrown and Millbrook. */
    private boolean lampy(int i) {
        double x = road.xs[i], z = road.zs[i];
        return Math.hypot(x, z - 905) < 340 || Math.hypot(x - ReachTerrain.MILLBROOK_X, z - ReachTerrain.MILLBROOK_Z) < 190;
    }

    /** A spot beside the road that's dry, open and near the road's level, or null. */
    private Column ground(int x, int z, int i, int tolerance) {
        Column c = terrain.column(x, z);
        if (!c.land() || c.wet() || c.carriageway() || c.across() < 3.6 || c.river() < 4 || c.edge() < 6) return null;
        if (Math.abs(c.top() - Math.floor(road.values[i])) > tolerance) return null;
        if (clearance.blocked(x, z)) return null;
        for (int y = c.top() + 1; y <= c.top() + 3; y++) if (canvas.has(x, y, z)) return null;
        return c;
    }

    // ------------------------------------------------------------------ edges: fences, hedges, walls

    /** Walks a line on each side of the road and gives each column of it the edge its zone and land call for. */
    private void edges(int from, int to) {
        for (int side : new int[] {-1, 1}) {
            int[] prev = null;
            for (int i = from; i <= to; i++) {
                int[] cell = beside(i, side * EDGE);
                if (prev != null) for (int[] step : walk(prev, cell)) edge(step[0], step[1], i, side);
                else edge(cell[0], cell[1], i, side);
                prev = cell;
            }
        }
    }

    /** A four-connected line from a to b (excluding a), so fences and walls join along diagonals. */
    private static List<int[]> walk(int[] a, int[] b) {
        List<int[]> out = new ArrayList<>();
        int x = a[0], z = a[1];
        while (x != b[0] || z != b[1]) {
            int dx = Integer.signum(b[0] - x), dz = Integer.signum(b[1] - z);
            if (dx != 0 && (dz == 0 || Math.abs(b[0] - x) >= Math.abs(b[1] - z))) x += dx; else z += dz;
            out.add(new int[] {x, z});
        }
        return out;
    }

    private void edge(int x, int z, int i, int side) {
        Column c = ground(x, z, i, 5);
        if (c == null) return;
        double stretch = terrain.patch(x * 0.6, z * 0.6, 0.02), grain = terrain.grain(x, z, 0.3);
        int y = c.top() + 1;
        switch (c.zone()) {
            case GOLDENREACH -> {
                int[] beyond = beside(i, side * (EDGE + 3));
                if (terrain.field(beyond[0], beyond[1]) >= 1) {
                    // Field fence, with a gate into each plot about every twenty blocks.
                    boolean gate = Math.floorMod(x * 7 + z * 13, 23) == 0;
                    canvas.set(x, c.top(), z, Material.GRASS_BLOCK);
                    if (gate) runs.gate(x, y, z, Material.OAK_FENCE_GATE, ReachBrush.turnFace("north", facingRoad(i, side)));
                    else runs.fence(x, y, z, Material.OAK_FENCE);
                } else if (stretch > -0.05) hedge(x, y, z, c, grain);
            }
            case HIGHCROWN, BARROWDOWNS -> {
                if (stretch > -0.15) {
                    canvas.set(x, c.top(), z, Material.GRASS_BLOCK);
                    runs.wall(x, y, z, terrain.patch(x, z, 0.2) > 0.2 ? Material.MOSSY_COBBLESTONE_WALL : Material.COBBLESTONE_WALL);
                }
            }
            case THORNWOOD -> {
                if (stretch > 0.35) {
                    canvas.set(x, c.top(), z, Material.PODZOL);
                    runs.fence(x, y, z, Material.SPRUCE_FENCE);
                }
            }
            default -> { }
        }
    }

    private void hedge(int x, int y, int z, Column c, double grain) {
        canvas.set(x, c.top(), z, Material.GRASS_BLOCK);
        canvas.set(x, y, z, Material.OAK_LEAVES, "persistent=true");
        if (grain > 0.1) canvas.set(x, y + 1, z, grain > 0.6 ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES, "persistent=true");
    }

    // ------------------------------------------------------------------ furniture

    /** A squat carved stone on the verge, every hundred blocks of road. */
    private void milestone(int i, int side) {
        int[] p = beside(i, side * 4.6);
        Column c = ground(p[0], p[1], i, 2);
        if (c == null) return;
        int y = c.top() + 1;
        canvas.set(p[0], c.top(), p[1], Material.COARSE_DIRT);
        canvas.set(p[0], y, p[1], terrain.patch(p[0], p[1], 0.3) > 0.2 ? Material.MOSSY_STONE_BRICKS : Material.CHISELED_STONE_BRICKS);
        canvas.set(p[0], y + 1, p[1], Material.POLISHED_ANDESITE_SLAB, "type=bottom");
    }

    /** A lamp post on the verge with its lantern hung out over the road. */
    private void lamp(int i, int side) {
        int[] p = beside(i, side * 4.4);
        Column c = ground(p[0], p[1], i, 2);
        if (c == null) return;
        ReachKit.Style style = Math.hypot(road.xs[i], road.zs[i] - 905) < 340 ? ReachKit.Style.HIGHCROWN : ReachKit.Style.VALE;
        kit.lamp(new ReachBrush(canvas, p[0], c.top() + 1, p[1], bracketToRoad(i, side)), style);
    }

    /** A bench facing the road, with trapdoor arms. */
    private void bench(int i, int side) {
        int[] p = beside(i, side * 5.0);
        Column c = ground(p[0], p[1], i, 1);
        if (c == null) return;
        ReachBrush b = new ReachBrush(canvas, p[0], c.top() + 1, p[1], facingRoad(i, side));
        for (int x = -1; x <= 2; x++) {
            Column under = terrain.column(b.wx(x, 0), b.wz(x, 0));
            if (!under.land() || under.top() != c.top() || clearance.blocked(b.wx(x, 0), b.wz(x, 0))) return;
        }
        b.set(0, 0, 0, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
        b.set(1, 0, 0, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
        b.set(-1, 0, 0, Material.SPRUCE_TRAPDOOR, "facing=west,half=bottom,open=true");
        b.set(2, 0, 0, Material.SPRUCE_TRAPDOOR, "facing=east,half=bottom,open=true");
    }

    /**
     * A wayside shrine: a little gabled roof on dark posts over a carved altar with candles, flowers either side,
     * set on a graded stone pad back from the verge.
     */
    private void shrine(int i, int side) {
        int[] p = beside(i, side * 7.0);
        Column c = ground(p[0], p[1], i, 2);
        if (c == null) return;
        ReachBrush b = new ReachBrush(canvas, p[0], c.top() + 1, p[1], facingRoad(i, side));
        for (int x = -2; x <= 4; x++) for (int z = -2; z <= 3; z++) if (clearance.blocked(b.wx(x, z), b.wz(x, z))) return;
        kit.site(b, 0, 0, 2, 1, 1, Material.STONE_BRICKS);
        for (int x = 0; x <= 2; x++) for (int z = 0; z <= 1; z++) b.set(x, -1, z, kit.weathered(b, x, -1, z, Material.STONE_BRICKS));
        for (int x = 0; x <= 2; x++) b.set(x, -1, -1, Material.STONE_BRICK_STAIRS, "facing=south,half=bottom");
        for (int x = 0; x <= 2; x++) for (int y = 0; y <= 2; y++) b.set(x, y, 1, x == 1 && y == 1 ? Material.CHISELED_STONE_BRICKS : kit.weathered(b, x, y, 1, Material.STONE_BRICKS));
        for (int x : new int[] {0, 2}) for (int y = 0; y <= 2; y++) b.set(x, y, 0, Material.DARK_OAK_LOG, "axis=y");
        b.set(1, 0, 0, Material.CHISELED_STONE_BRICKS);
        b.set(1, 1, 0, Material.CANDLE, "candles=3,lit=true");
        b.set(1, 2, 0, Material.AIR);
        // The roof: a gable running front to back, with an overhang at the front.
        for (int x = -1; x <= 3; x++) {
            b.set(x, 3, -1, Material.SPRUCE_STAIRS, x == -1 ? "facing=east,half=bottom" : x == 3 ? "facing=west,half=bottom" : "facing=south,half=bottom");
            b.set(x, 3, 2, Material.SPRUCE_STAIRS, "facing=north,half=bottom");
            b.set(x, 3, 0, Material.SPRUCE_PLANKS);
            b.set(x, 3, 1, Material.SPRUCE_PLANKS);
            b.set(x, 4, 0, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
            b.set(x, 4, 1, Material.SPRUCE_STAIRS, "facing=north,half=bottom");
        }
        b.set(1, 2, -1, Material.LANTERN, "hanging=true");
        b.set(-1, 0, 0, Material.POTTED_CORNFLOWER);
        b.set(3, 0, 0, Material.POTTED_POPPY);
    }

    /**
     * A waypoint every three hundred blocks or so along the roads through the zones beyond the Vale, so a long road
     * always has the next thing in sight: a traveller's camp (tent, fire, packs), a ruined waystation (broken walls
     * round a hearth) or a cairn with an offering stone, each with a chest of the zone's tier. The Vale and the
     * capital have their own builds and are left alone.
     */
    private void wayside(int i, int side) {
        Zone zone = terrain.zoneAt(road.xs[i], road.zs[i]);
        if (zone == Zone.GOLDENREACH || zone == Zone.HIGHCROWN || zone == Zone.SKY) return;
        int[] p = beside(i, side * 13.0);
        Column c = ground(p[0], p[1], i, 3);
        if (c == null) return;
        ReachBrush b = new ReachBrush(canvas, p[0], c.top() + 1, p[1], facingRoad(i, side));
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 5; z++) {
            int wx = b.wx(x, z), wz = b.wz(x, z);
            Column g = terrain.column(wx, wz);
            if (clearance.blocked(wx, wz) || !g.land() || g.wet() || Math.abs(g.top() - c.top()) > 2 || g.carriageway()) return;
        }
        int tier = switch (zone) { case BARROWDOWNS, THORNWOOD -> 1; case MISTMERE, IRONSPINE -> 2; default -> 3; };
        long h = ReachFlora.hash(i * 13 + roadIndex, 991);
        int kind = (int) Math.floorMod(h, 3L);
        kit.site(b, -3, -2, 3, 4, 1, Material.COBBLESTONE);
        if (kind == 0) camp(b, zone);
        else if (kind == 1) waystation(b);
        else cairnStone(b, zone);
        b.set(0, 0, 3, Material.CHEST, "facing=north,type=single");
        spots.add(new ReachFeatures.Spot("chest_tier" + tier + "_way_" + roadIndex + "_" + i, b.wx(0, 3) + 0.5, b.oy, b.wz(0, 3) + 0.5, 0F));
    }

    /** A traveller's camp: a ridge tent of the zone's cloth, a ringed fire with a spit, bedrolls, packs and a lamp. */
    private void camp(ReachBrush b, Zone zone) {
        Material cloth = switch (zone) {
            case SUNSCAR -> Material.ORANGE_WOOL;
            case SCAR -> Material.GRAY_WOOL;
            case THORNWOOD -> Material.GREEN_WOOL;
            case MISTMERE -> Material.LIGHT_BLUE_WOOL;
            default -> Material.WHITE_WOOL;
        };
        Material stripe = zone == Zone.SUNSCAR ? Material.BROWN_WOOL : Material.LIGHT_GRAY_WOOL;
        // The tent: an A-frame of cloth on a spruce ridge pole, open at the front.
        for (int z = 1; z <= 4; z++) {
            b.set(-2, 0, z, cloth);
            b.set(2, 0, z, cloth);
            b.set(-1, 1, z, z % 2 == 0 ? stripe : cloth);
            b.set(1, 1, z, z % 2 == 0 ? stripe : cloth);
            b.set(0, 2, z, Material.SPRUCE_FENCE);
            for (int x = -1; x <= 1; x++) b.set(x, 0, z, x == 0 ? Material.AIR : Material.AIR);
        }
        for (int x = -1; x <= 1; x++) b.set(x, 0, 4, cloth);
        b.set(0, 1, 4, cloth);
        for (int z : new int[] {0, 5}) b.set(0, 2, z, Material.SPRUCE_FENCE);
        for (int z : new int[] {0, 5}) for (int y = 0; y <= 1; y++) b.set(0, y, z, Material.SPRUCE_FENCE);
        b.set(-1, 0, 2, Material.BROWN_CARPET);
        b.set(1, 0, 2, Material.RED_CARPET);
        // The fire in front: a ring of stones round a campfire, a spit over it, a log to sit on.
        b.set(0, 0, -2, Material.CAMPFIRE, "lit=true,signal_fire=false");
        for (int[] d : new int[][] {{1, -2}, {-1, -2}, {0, -3}}) b.set(d[0], 0, d[1], Material.COBBLESTONE_SLAB, "type=bottom");
        b.set(2, 0, -2, Material.SPRUCE_LOG, "axis=z");
        b.set(-2, 0, -1, Material.BARREL, "facing=up");
        b.set(-3, 0, -1, Material.HAY_BLOCK, "axis=y");
        b.set(3, 0, 1, Material.LANTERN);
    }

    /** A ruined waystation: broken walls of an old road-house round a cold hearth, a fallen beam, nettles. */
    private void waystation(ReachBrush b) {
        for (int x = -3; x <= 3; x++) for (int z = 0; z <= 5; z++) {
            boolean wall = x == -3 || x == 3 || z == 0 || z == 5;
            if (!wall) continue;
            if (z == 0 && Math.abs(x) <= 1) continue;
            int h = 1 + (int) Math.floorMod(ReachFlora.hash(b.wx(x, z), b.wz(x, z)), 3L) + (x == 3 ? 1 : 0);
            for (int y = 0; y < h; y++) b.set(x, y, z, kit.weathered(b, x, y, z, Material.COBBLESTONE));
            if (h >= 3 && (x + z) % 2 == 0) b.set(x, h, z, Material.MOSSY_COBBLESTONE_SLAB, "type=bottom");
        }
        // The hearth against the back wall, a fallen roof beam, rubble.
        b.set(0, 0, 4, Material.CAMPFIRE, "lit=false,signal_fire=false");
        b.set(-1, 0, 4, Material.COBBLESTONE);
        b.set(1, 0, 4, Material.COBBLESTONE);
        for (int y = 1; y <= 3; y++) b.set(0, y, 5, Material.COBBLESTONE);
        for (int x = -2; x <= 1; x++) b.set(x, 0, 2, Material.STRIPPED_SPRUCE_LOG, "axis=x");
        b.set(2, 0, 1, Material.MOSSY_COBBLESTONE);
        b.set(-2, 0, 4, Material.COBWEB);
    }

    /** A cairn by the road with an offering stone, the zone's own stone, a lantern for those who pass at night. */
    private void cairnStone(ReachBrush b, Zone zone) {
        Material stone = switch (zone) {
            case SUNSCAR -> Material.RED_SANDSTONE;
            case SCAR -> Material.BLACKSTONE;
            case IRONSPINE -> Material.ANDESITE;
            default -> Material.MOSSY_COBBLESTONE;
        };
        for (int dx = -2; dx <= 2; dx++) for (int dz = 0; dz <= 4; dz++) {
            double d = Math.hypot(dx, dz - 2);
            int h = d < 0.9 ? 4 : d < 1.8 ? 2 : d < 2.5 ? 1 : 0;
            for (int y = 0; y < h; y++) b.set(dx, y, dz, (dx + dz + y) % 3 == 0 ? Material.COBBLESTONE : stone);
        }
        b.set(0, 4, 2, Material.LANTERN);
        b.set(0, 0, 0, Material.POLISHED_ANDESITE_SLAB, "type=bottom");
        b.set(1, 0, -1, Material.CANDLE, "candles=2,lit=true");
    }

    /** Zone markers that aren't continuous edges: cairns over the passes, marsh lights, ash-road pillars, log piles. */
    private void marker(int i, int side) {
        Zone zone = terrain.zoneAt(road.xs[i], road.zs[i]);
        switch (zone) {
            case IRONSPINE -> { if (i % 8 == 0) cairn(i, (i / 8) % 2 == 0 ? 1 : -1, Material.COBBLESTONE, Material.COBBLESTONE_WALL, Material.STONE); }
            case SUNSCAR -> { if (i % 10 == 0) cairn(i, (i / 10) % 2 == 0 ? 1 : -1, Material.RED_SANDSTONE, Material.RED_SANDSTONE_WALL, Material.SMOOTH_RED_SANDSTONE); }
            case MISTMERE -> { if (i % 5 == 0) marshLight(i, (i / 5) % 2 == 0 ? 1 : -1); }
            case SCAR -> { if (i % 6 == 0) pillar(i, (i / 6) % 2 == 0 ? 1 : -1); }
            case THORNWOOD -> { if (Math.floorMod(ReachFlora.hash(i * 17, roadIndex), 13L) == 0) logPile(i, side); }
            default -> { }
        }
    }

    private void cairn(int i, int side, Material base, Material wall, Material top) {
        int[] p = beside(i, side * 4.8);
        Column c = ground(p[0], p[1], i, 3);
        if (c == null) return;
        int y = c.top() + 1;
        canvas.set(p[0], y, p[1], base);
        canvas.set(p[0], y + 1, p[1], wall, "up=true");
        canvas.set(p[0], y + 2, p[1], top);
        if (i % 32 == 0) canvas.set(p[0], y + 3, p[1], Material.LANTERN);
    }

    private void marshLight(int i, int side) {
        int[] p = beside(i, side * 4.6);
        Column c = ground(p[0], p[1], i, 3);
        if (c == null) return;
        int y = c.top() + 1;
        for (int k = 0; k < 3; k++) canvas.set(p[0], y + k, p[1], Material.SPRUCE_FENCE);
        canvas.set(p[0], y + 3, p[1], Material.SPRUCE_TRAPDOOR, "half=bottom,open=false,facing=north");
        canvas.set(p[0], y + 4, p[1], Material.LANTERN);
    }

    private void pillar(int i, int side) {
        int[] p = beside(i, side * 4.6);
        Column c = ground(p[0], p[1], i, 3);
        if (c == null) return;
        int y = c.top() + 1;
        int height = 1 + (int) Math.floorMod(ReachFlora.hash(p[0], p[1]), 4L);
        for (int k = 0; k < height; k++) canvas.set(p[0], y + k, p[1], k == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BASALT, k == 0 ? "" : "axis=y");
        if (height >= 4) canvas.set(p[0], y + height, p[1], Material.SOUL_LANTERN);
    }

    private void logPile(int i, int side) {
        int[] p = beside(i, side * 5.5);
        Column c = ground(p[0], p[1], i, 1);
        if (c == null) return;
        double[] u = along(i);
        boolean xAxis = Math.abs(u[0]) > Math.abs(u[1]);
        for (int k = -1; k <= 1; k++) {
            int x = p[0] + (xAxis ? k : 0), z = p[1] + (xAxis ? 0 : k);
            Column under = terrain.column(x, z);
            if (!under.land() || under.top() != c.top() || clearance.blocked(x, z)) continue;
            canvas.set(x, c.top() + 1, z, Material.SPRUCE_LOG, xAxis ? "axis=x" : "axis=z");
            if (k == 0) canvas.set(x, c.top() + 2, z, Material.SPRUCE_LOG, xAxis ? "axis=x" : "axis=z");
        }
    }

    /** Where Ember Road leaves the Spine, a signpost says so. */
    private void signpost(int i, int side) {
        int[] p = beside(i, side * 5.0);
        Column c = ground(p[0], p[1], i, 2);
        if (c == null) return;
        kit.signpost(new ReachBrush(canvas, p[0], c.top() + 1, p[1], facingRoad(i, side)), ReachKit.Style.VALE);
    }

    /** A low wall along the outer edge wherever the road stands on an embankment more than a couple of blocks high. */
    private void parapets(int from, int to) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.Map<Long, int[]> edge = new java.util.HashMap<>();
        for (int i = from; i <= to; i++) {
            int cx = (int) Math.floor(road.xs[i]), cz = (int) Math.floor(road.zs[i]);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                int x = cx + dx, z = cz + dz;
                long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                if (!seen.add(key)) continue;
                Column c = terrain.column(x, z);
                if (!c.land() || !c.carriageway() || c.across() < 2.6 || c.fill() < 1.5 || clearance.blocked(x, z)) continue;
                edge.put(key, new int[] {x, z, c.top() + 1});
            }
        }
        // A parapet is a run: lone posts and scraps under six blocks long are left out, so the embankment's edge
        // reads as a wall where it is high and as open verge where it is not.
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (long start : edge.keySet()) {
            if (done.contains(start)) continue;
            java.util.List<int[]> run = new java.util.ArrayList<>();
            java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>(java.util.List.of(start));
            done.add(start);
            while (!queue.isEmpty()) {
                int[] p = edge.get(queue.poll());
                run.add(p);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    long n = ((long) (p[0] + dx) << 32) ^ ((p[1] + dz) & 0xFFFFFFFFL);
                    if (edge.containsKey(n) && done.add(n)) queue.add(n);
                }
            }
            if (run.size() < 6) continue;
            for (int[] p : run) runs.wall(p[0], p[2], p[1], terrain.patch(p[0], p[1], 0.2) > 0.3 ? Material.MOSSY_STONE_BRICK_WALL : Material.STONE_BRICK_WALL);
        }
    }
}
