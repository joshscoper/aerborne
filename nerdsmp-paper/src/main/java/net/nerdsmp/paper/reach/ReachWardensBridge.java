package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wardens' Bridge: where the Spine ends, on the Sunscar's east lip, a paved bridgehead between two pale towers,
 * and from it a chain bridge out over the cloud toward the Archive Isle. It fell long ago. Its first spans still hang
 * from the towers on their chains and run out over the drop to a splintered end, chains trailing down into the cloud;
 * the isle keeps its own broken half, rising to meet nothing. Travellers cross by air from the bridgehead.
 */
final class ReachWardensBridge {
    private ReachWardensBridge() { }

    /** Where the Spine ends: the middle of the bridgehead. */
    static final int HEAD_X = 1146, HEAD_Z = 8, HEAD_Y = 114;

    static ReachFeatures.Feature feature(ReachTerrain terrain) {
        int[][] ends = ends(terrain);
        int minX = Math.min(ends[0][0], ends[1][0]) - 30, maxX = Math.max(ends[0][0], ends[1][0]) + 30;
        int minZ = Math.min(ends[0][1], ends[1][1]) - 30, maxZ = Math.max(ends[0][1], ends[1][1]) + 30;
        minX = Math.min(minX, HEAD_X - 20);
        minZ = Math.min(minZ, HEAD_Z - 20);
        maxZ = Math.max(maxZ, HEAD_Z + 20);
        return new ReachFeatures.Feature("wardens_bridge", new ReachFeatures.Footprint(minX, minZ, maxX, maxZ), t -> build(t, ends));
    }

    /** The two lips the bridge leaves from: the continent's toward the isle, the isle's toward the continent. */
    static int[][] ends(ReachTerrain terrain) {
        double ux = ReachTerrain.ISLE_X - HEAD_X, uz = ReachTerrain.ISLE_Z - HEAD_Z, len = Math.hypot(ux, uz);
        ux /= len; uz /= len;
        double x = HEAD_X, z = HEAD_Z;
        while (terrain.column((int) Math.floor(x), (int) Math.floor(z)).land()) { x += ux; z += uz; }
        int[] west = {(int) Math.floor(x - ux * 3), (int) Math.floor(z - uz * 3)};
        x = ReachTerrain.ISLE_X; z = ReachTerrain.ISLE_Z;
        while (terrain.column((int) Math.floor(x), (int) Math.floor(z)).land() && x > HEAD_X) { x -= ux; z -= uz; }
        // Past the isle's own hollows to its outer rim, then back onto the rock.
        int[] east = {(int) Math.floor(x + ux * 4), (int) Math.floor(z + uz * 4)};
        return new int[][] {west, east};
    }

    static ReachFeatures.Built build(ReachTerrain terrain, int[][] ends) {
        ReachCanvas canvas = new ReachCanvas();
        List<ReachFeatures.Spot> spots = new ArrayList<>();
        double ux = ends[1][0] - ends[0][0], uz = ends[1][1] - ends[0][1], len = Math.hypot(ux, uz);
        ux /= len; uz /= len;
        double px = -uz, pz = ux;
        int y0 = terrain.column(ends[0][0], ends[0][1]).top() + 1;
        int y1 = terrain.column(ends[1][0], ends[1][1]).top() + 1;

        // The bridgehead: a paved round the road runs into, its edge kerbed, a lamp at each quarter.
        int hy = terrain.column(HEAD_X, HEAD_Z).top() + 1;
        for (int dx = -11; dx <= 11; dx++) for (int dz = -11; dz <= 11; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 10.5) continue;
            int x = HEAD_X + dx, z = HEAD_Z + dz;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land()) continue;
            // Where the road runs in, the paving takes the road's own level, so it meets the round without a step up.
            int floor = c.carriageway() ? Math.min(hy - 1, c.top()) : hy - 1;
            for (int y = c.top() + 1; y < floor; y++) canvas.set(x, y, z, Material.RED_SANDSTONE);
            boolean ring = d > 9.5 && !c.carriageway();
            canvas.set(x, floor, z, ring ? Material.CHISELED_RED_SANDSTONE : (Math.floorMod(dx + dz, 2) == 0 ? Material.SMOOTH_RED_SANDSTONE : Material.CUT_RED_SANDSTONE));
            for (int y = floor + 1; y <= Math.max(hy + 5, c.top() + 1); y++) canvas.set(x, y, z, Material.AIR);
        }
        for (int q = 0; q < 8; q++) {
            double a = q * Math.PI / 4 + Math.PI / 8;
            int x = HEAD_X + (int) Math.round(Math.cos(a) * 9), z = HEAD_Z + (int) Math.round(Math.sin(a) * 9);
            // A lamp every other eighth of the round, and none where the road comes in.
            if (q % 2 == 1 || terrain.column(x, z).across() < 6) continue;
            canvas.set(x, hy, z, Material.RED_SANDSTONE_WALL, "up=true");
            canvas.set(x, hy + 1, z, Material.RED_SANDSTONE_WALL, "up=true");
            canvas.set(x, hy + 2, z, Material.LANTERN, "hanging=false");
        }
        // A causeway from the round out to the lip, level with it.
        for (double d = 0; d <= Math.hypot(ends[0][0] - HEAD_X, ends[0][1] - HEAD_Z) + 1; d += 0.5) for (double w = -3; w <= 3; w += 0.5) {
            int x = (int) Math.floor(HEAD_X + 0.5 + (ends[0][0] - HEAD_X) * d / Math.max(1, Math.hypot(ends[0][0] - HEAD_X, ends[0][1] - HEAD_Z)) + px * w);
            int z = (int) Math.floor(HEAD_Z + 0.5 + (ends[0][1] - HEAD_Z) * d / Math.max(1, Math.hypot(ends[0][0] - HEAD_X, ends[0][1] - HEAD_Z)) + pz * w);
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land()) continue;
            for (int y = c.top() + 1; y < hy - 1; y++) canvas.set(x, y, z, Material.RED_SANDSTONE);
            canvas.set(x, hy - 1, z, Math.abs(w) > 2.6 ? Material.CHISELED_RED_SANDSTONE : Material.SMOOTH_RED_SANDSTONE);
            for (int y = hy; y <= Math.max(hy + 5, c.top() + 1); y++) canvas.set(x, y, z, Material.AIR);
        }
        y0 = hy;
        half(canvas, terrain, ends[0][0], ends[0][1], y0, ux, uz, px, pz, 46, 0.14, false);
        half(canvas, terrain, ends[1][0], ends[1][1], y1, -ux, -uz, -px, -pz, 38, -0.16, true);

        // The waymark at the bridgehead.
        int sx = ends[0][0] + (int) Math.round(-ux * 4 + px * 4), sz = ends[0][1] + (int) Math.round(-uz * 4 + pz * 4);
        canvas.set(sx, hy, sz, Material.CHISELED_QUARTZ_BLOCK);
        canvas.set(sx, hy + 1, sz, Material.QUARTZ_PILLAR, "axis=y");
        String facing = Math.abs(ux) > Math.abs(uz) ? (ux > 0 ? "west" : "east") : (uz > 0 ? "north" : "south");
        int fx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0, fz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
        canvas.set(sx + fx, hy + 1, sz + fz, Material.BIRCH_WALL_SIGN, "facing=" + facing);
        canvas.text(sx + fx, hy + 1, sz + fz, "THE WARDENS'", "BRIDGE", "Fallen", "Fly to the Isle");
        spots.add(new ReachFeatures.Spot("place", HEAD_X + 0.5, hy, HEAD_Z + 0.5, yaw(ux, uz)));
        // The roost on the round's edge, facing in across it.
        int[] in = axis(-px, -pz);
        spots.add(ReachRoost.build(canvas, terrain, HEAD_X + (int) Math.round(-ux * 3 + px * 7), HEAD_Z + (int) Math.round(-uz * 3 + pz * 7), in[0], in[1], "Wardens' Bridge"));
        // The isle's end: a small paved landing behind its towers, where flights from the bridgehead set down.
        int ex = ends[1][0] + (int) Math.round(ux * 9), ez = ends[1][1] + (int) Math.round(uz * 9);
        int ey = terrain.column(ex, ez).top() + 1;
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            if (Math.hypot(dx, dz) > 5.2) continue;
            int x = ex + dx, z = ez + dz;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land()) continue;
            for (int y = c.top() + 1; y < ey - 1; y++) canvas.set(x, y, z, Material.CALCITE);
            canvas.set(x, ey - 1, z, Math.floorMod(dx + dz, 2) == 0 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS);
            for (int y = ey; y <= Math.max(ey + 4, c.top() + 1); y++) canvas.set(x, y, z, Material.AIR);
        }
        int[] back = axis(-ux, -uz);
        ReachFeatures.Spot isle = ReachRoost.build(canvas, terrain, ex + (int) Math.round(ux * 3), ez + (int) Math.round(uz * 3), back[0], back[1], "Archive Isle");
        spots.add(new ReachFeatures.Spot("isle_flight", isle.x(), isle.y(), isle.z(), isle.yaw()));
        return new ReachFeatures.Built(canvas, spots);
    }

    /**
     * One half of the bridge: two towers on the lip, the deck hung from their tops on great chains, running out
     * {@code length} blocks along (ux, uz) and climbing {@code rise} a block, to a broken end with its boards gone
     * ragged and chains hanging down into the cloud.
     */
    private static void half(ReachCanvas canvas, ReachTerrain terrain, int ox, int oz, int y, double ux, double uz, double px, double pz,
                             int length, double rise, boolean isle) {
        Material tower = isle ? Material.CALCITE : Material.SMOOTH_QUARTZ, trim = Material.CHISELED_QUARTZ_BLOCK, band = Material.GOLD_BLOCK;
        int towerH = 20;
        // The towers, either side of the deck at the lip, footed down to the rock.
        for (int side : new int[] {-1, 1}) {
            int tx = (int) Math.round(ox + px * 5 * side), tz = (int) Math.round(oz + pz * 5 * side);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int x = tx + dx, z = tz + dz;
                ReachTerrain.Column c = terrain.column(x, z);
                int foot = c.land() ? c.top() + 1 : y - 12;
                for (int yy = Math.min(foot, y - 1); yy < y + towerH; yy++) {
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    Material m = yy == y + towerH - 1 ? trim : (yy - y) % 7 == 6 ? band : corner ? Material.QUARTZ_PILLAR : tower;
                    canvas.set(x, yy, z, m, m == Material.QUARTZ_PILLAR ? "axis=y" : "");
                }
            }
            canvas.set(tx, y + towerH, tz, Material.LANTERN, "hanging=false");
        }
        // The deck, its hangers and the main chains, out to the break.
        int breakAt = length - 6;
        for (double d = 0; d <= length; d += 0.5) for (double w = -3; w <= 3; w += 0.5) {
            int x = (int) Math.floor(ox + 0.5 + ux * d + px * w), z = (int) Math.floor(oz + 0.5 + uz * d + pz * w);
            int dy = y - 1 + (int) Math.floor(d * rise);
            boolean edge = Math.abs(w) > 2.4;
            boolean broken = d > breakAt && Math.floorMod((int) (d * 3 + w * 7 + x + z), 5) < (int) ((d - breakAt) * 0.9);
            if (broken) continue;
            ReachTerrain.Column c = terrain.column(x, z);
            if (c.land() && c.top() >= dy) continue;
            canvas.set(x, dy, z, edge ? Material.STRIPPED_DARK_OAK_LOG : Material.DARK_OAK_PLANKS, edge ? (Math.abs(ux) > Math.abs(uz) ? "axis=x" : "axis=z") : "");
            if (edge && d < breakAt) canvas.set(x, dy + 1, z, Material.DARK_OAK_FENCE);
        }
        // Chains from each tower's top sag to the deck's edge, with hangers down to it every three blocks. Each main
        // chain is one unbroken run: along the span where it runs level, straight down where it drops a block.
        String along = Math.abs(ux) > Math.abs(uz) ? "x" : "z";
        for (int side : new int[] {-1, 1}) {
            int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
            for (double d = 0; d <= length - 8; d += 0.25) {
                double t = d / (length - 8);
                int deckY = y - 1 + (int) Math.floor(d * rise);
                int cableY = deckY + 2 + (int) Math.round((y + 19 - deckY - 2) * (1 - t) * (1 - t));
                int x = (int) Math.floor(ox + 0.5 + ux * d + px * 5 * side), z = (int) Math.floor(oz + 0.5 + uz * d + pz * 5 * side);
                if (lastX != Integer.MIN_VALUE && (x != lastX || z != lastZ)) {
                    // A step sideways and a step down never meet at a corner only: the drop happens in the old column.
                    for (int yy = Math.min(lastY, cableY) + 1; yy < Math.max(lastY, cableY); yy++) canvas.set(lastX, yy, lastZ, Material.IRON_CHAIN, "axis=y");
                    if (cableY != lastY) canvas.set(lastX, cableY, lastZ, Material.IRON_CHAIN, "axis=y");
                    if (x != lastX && z != lastZ) canvas.set(x, cableY, lastZ, Material.IRON_CHAIN, "axis=x");
                }
                // Each link lies the way the chain came into it, so it holds on to the link before.
                String axis = lastX == Integer.MIN_VALUE || x != lastX ? "x" : z != lastZ ? "z" : along;
                if (lastX != Integer.MIN_VALUE && x == lastX && z == lastZ) axis = canvas.get(x, cableY, z) != null && canvas.get(x, cableY, z).state().contains("axis=") ? canvas.get(x, cableY, z).state().replaceAll(".*axis=([xyz]).*", "$1") : along;
                if (canvas.get(x, cableY, z) == null || !canvas.get(x, cableY, z).state().contains("quartz") && !canvas.get(x, cableY, z).state().contains("gold") && !canvas.get(x, cableY, z).state().contains("calcite"))
                    canvas.set(x, cableY, z, Material.IRON_CHAIN, "axis=" + axis);
                lastX = x; lastY = cableY; lastZ = z;
                if (Math.floorMod((int) Math.floor(d), 3) == 0 && d == Math.floor(d))
                    for (int yy = deckY + 1; yy < cableY; yy++) canvas.set((int) Math.floor(ox + 0.5 + ux * d + px * 3 * side), yy, (int) Math.floor(oz + 0.5 + uz * d + pz * 3 * side), Material.IRON_CHAIN, "axis=y");
            }
        }
        // At the break, the last boards hang by their chains down into the cloud.
        for (int k = -2; k <= 2; k += 2) {
            double d = breakAt + 1;
            int x = (int) Math.floor(ox + 0.5 + ux * d + px * k), z = (int) Math.floor(oz + 0.5 + uz * d + pz * k);
            int top = y - 2 + (int) Math.floor(d * rise);
            int drop = 8 + Math.floorMod(x * 7 + z * 3, 9);
            for (int yy = top; yy > top - drop; yy--) canvas.set(x, yy, z, Material.IRON_CHAIN, "axis=y");
            canvas.set(x, top - drop, z, Material.DARK_OAK_PLANKS);
            canvas.set(x, top - drop - 1, z, Material.LANTERN, "hanging=true");
        }
    }

    private static float yaw(double ux, double uz) { return (float) Math.toDegrees(Math.atan2(-ux, uz)); }

    /** The compass step nearest a direction. */
    private static int[] axis(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? new int[] {(int) Math.signum(dx), 0} : new int[] {0, (int) Math.signum(dz)};
    }
}
