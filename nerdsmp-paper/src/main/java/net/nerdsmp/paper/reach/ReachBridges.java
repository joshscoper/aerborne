package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A stone bridge wherever a road crosses water. The deck follows the road itself, sample by sample, so a road that
 * bends over a river stays on its bridge; it runs level over the water and eases down a block every two at each end
 * until it meets the road's own surface, so there is never a step on or off. Under the deck an arch springs from
 * abutments on the banks, the ring picked out in its own stone; parapets with lamps over the abutments run its length.
 * Each crossing is its own feature with a small footprint.
 */
final class ReachBridges {
    private ReachBridges() { }

    /** How far past the water's edge, in road samples, the bridge runs before it hands back to the road. */
    private static final int REACH = 4;

    static List<ReachFeatures.Feature> features(ReachTerrain terrain) {
        List<ReachFeatures.Feature> out = new ArrayList<>();
        int i = 0;
        for (ReachTerrain.Crossing c : terrain.crossings()) {
            // Skip the Kingsbridge: Highcrown builds its own.
            if (Math.hypot((c.x0() + c.x1()) / 2, (c.z0() + c.z1()) / 2 - 790) < 60) continue;
            ReachPath road = terrain.roads().get(c.road());
            int a = Math.max(0, c.from() - REACH), b = Math.min(road.size() - 1, c.to() + REACH);
            double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (int k = a; k <= b; k++) {
                minX = Math.min(minX, road.xs[k]); maxX = Math.max(maxX, road.xs[k]);
                minZ = Math.min(minZ, road.zs[k]); maxZ = Math.max(maxZ, road.zs[k]);
            }
            ReachFeatures.Footprint footprint = new ReachFeatures.Footprint((int) Math.floor(minX) - 8, (int) Math.floor(minZ) - 8,
                (int) Math.ceil(maxX) + 8, (int) Math.ceil(maxZ) + 8);
            out.add(new ReachFeatures.Feature("bridge_" + i++, footprint, t -> build(t, c)));
        }
        return out;
    }

    static ReachFeatures.Built build(ReachTerrain terrain, ReachTerrain.Crossing c) {
        ReachCanvas canvas = new ReachCanvas();
        ReachPath road = terrain.roads().get(c.road());
        int a = Math.max(0, c.from() - REACH), b = Math.min(road.size() - 1, c.to() + REACH);
        int deck = c.deck() + 1;
        // Where the water is along the road: the span's ends in road samples, and so in blocks along it.
        double wetFrom = c.from() + 2, wetTo = c.to() - 1;
        double mid = (wetFrom + wetTo) / 2.0, halfSpan = Math.max(2, (wetTo - wetFrom) / 2.0 + 1);
        // Every block within the bridge's width of the road's line, found by measuring, so a deck on a bend or a
        // diagonal has no holes in it.
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int k = a; k <= b; k++) {
            minX = Math.min(minX, road.xs[k]); maxX = Math.max(maxX, road.xs[k]);
            minZ = Math.min(minZ, road.zs[k]); maxZ = Math.max(maxZ, road.zs[k]);
        }
        for (int x = (int) Math.floor(minX) - 5; x <= (int) Math.ceil(maxX) + 5; x++) for (int z = (int) Math.floor(minZ) - 5; z <= (int) Math.ceil(maxZ) + 5; z++) {
            double bestD = Double.MAX_VALUE, t = 0, sw = 0;
            int bk = a;
            double bs = 0;
            for (int k = a; k < b; k++) {
                double dx = road.xs[k + 1] - road.xs[k], dz = road.zs[k + 1] - road.zs[k], len2 = Math.max(1e-9, dx * dx + dz * dz);
                double s = Math.max(0, Math.min(1, ((x + 0.5 - road.xs[k]) * dx + (z + 0.5 - road.zs[k]) * dz) / len2));
                double ex = road.xs[k] + dx * s - (x + 0.5), ez = road.zs[k] + dz * s - (z + 0.5);
                double d = Math.hypot(ex, ez);
                if (d < bestD) {
                    bestD = d; bk = k; bs = s; t = k + s;
                    sw = (dx * (z + 0.5 - road.zs[k]) - dz * (x + 0.5 - road.xs[k])) / Math.sqrt(len2);
                }
            }
            // Past either end of the run the road takes over again.
            if (bestD > 4.6 || (bk == a && bs <= 0 && bestD > Math.abs(sw) + 0.5) || (bk == b - 1 && bs >= 1 && bestD > Math.abs(sw) + 0.5)) continue;
            // The deck's walking level here: level over the span, falling a block every two blocks beyond it to the road.
            double beyond = Math.max(0, Math.abs(t - mid) - halfSpan) * ReachPath.STEP;
            double roadLevel = road.values[bk] + (road.values[bk + 1] - road.values[bk]) * bs;
            int walk = (int) Math.max(Math.floor(roadLevel) + 1, deck - Math.floor(beyond / 2));
            // The arch: highest at mid-span, springing at the water's edges.
            double along = (t - mid) / halfSpan;
            double arch = Math.max(0, 1 - along * along);
            {
                ReachTerrain.Column col = terrain.column(x, z);
                double w = Math.abs(sw);
                boolean parapet = w > 3.6;
                canvas.set(x, walk - 1, z, parapet ? Material.STONE_BRICKS : Math.abs(w) <= 1 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
                if (parapet) canvas.set(x, walk, z, Material.STONE_BRICK_WALL, "up=true");
                else for (int y = walk; y < walk + 4; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
                int ground = col.land() ? (col.wet() ? col.water() + 1 : col.top() + 1) : walk - 10;
                int soffit = arch > 0 ? walk - 3 - (int) Math.round((1 - arch) * 6) : ground;
                boolean face = parapet;
                for (int y = Math.max(ground, soffit); y < walk - 1; y++)
                    canvas.set(x, y, z, y == soffit && arch > 0 ? Material.STONE_BRICKS : face ? (y < walk - 4 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS) : Material.STONE);
                if (!col.wet() && arch <= 0) for (int y = col.top() + 1; y < walk - 1; y++) canvas.set(x, y, z, y < walk - 4 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
        // Lamps on the parapets over each abutment.
        for (double t : new double[] {wetFrom - 1, wetTo + 1}) {
            int k = (int) Math.max(a, Math.min(b - 1, Math.floor(t)));
            double dx = road.xs[k + 1] - road.xs[k], dz = road.zs[k + 1] - road.zs[k], len = Math.max(1e-6, Math.hypot(dx, dz));
            for (int w : new int[] {-4, 4}) {
                int x = (int) Math.floor(road.xs[k] - dz / len * w), z = (int) Math.floor(road.zs[k] + dx / len * w);
                int y = top(canvas, x, z);
                if (y != Integer.MIN_VALUE) canvas.set(x, y + 1, z, Material.LANTERN);
            }
        }
        int mk = (int) Math.max(a, Math.min(b, Math.round(mid)));
        int sx = (int) Math.floor(road.xs[mk]), sz = (int) Math.floor(road.zs[mk]);
        int st = top(canvas, sx, sz);
        return new ReachFeatures.Built(canvas, List.of(new ReachFeatures.Spot("deck", sx + 0.5, st == Integer.MIN_VALUE ? deck : st + 1, sz + 0.5, 0F)));
    }

    /** The highest block the bridge placed in a column, or MIN_VALUE. */
    private static int top(ReachCanvas canvas, int x, int z) {
        for (int y = 320; y > -64; y--) {
            ReachCanvas.Spec s = canvas.get(x, y, z);
            if (s != null && !s.state().equals("minecraft:air")) return y;
        }
        return Integer.MIN_VALUE;
    }
}
