package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A stone bridge wherever a road crosses water: a level deck from bank to bank, parapets with lamps on the piers,
 * and an arch underneath. Each crossing is its own feature with a small footprint.
 */
final class ReachBridges {
    private ReachBridges() { }

    static List<ReachFeatures.Feature> features(ReachTerrain terrain) {
        List<ReachFeatures.Feature> out = new ArrayList<>();
        int i = 0;
        for (ReachTerrain.Crossing c : terrain.crossings()) {
            // Skip the Kingsbridge: Highcrown builds its own.
            if (Math.hypot((c.x0() + c.x1()) / 2, (c.z0() + c.z1()) / 2 - 790) < 60) continue;
            int minX = (int) Math.floor(Math.min(c.x0(), c.x1())) - 10, maxX = (int) Math.ceil(Math.max(c.x0(), c.x1())) + 10;
            int minZ = (int) Math.floor(Math.min(c.z0(), c.z1())) - 10, maxZ = (int) Math.ceil(Math.max(c.z0(), c.z1())) + 10;
            out.add(new ReachFeatures.Feature("bridge_" + i++, new ReachFeatures.Footprint(minX, minZ, maxX, maxZ), t -> build(t, c)));
        }
        return out;
    }

    static ReachFeatures.Built build(ReachTerrain terrain, ReachTerrain.Crossing c) {
        ReachCanvas canvas = new ReachCanvas();
        double dx = c.x1() - c.x0(), dz = c.z1() - c.z0(), len = Math.hypot(dx, dz);
        double ux = dx / len, uz = dz / len, px = -uz, pz = ux;
        int deck = c.deck() + 1;
        int steps = (int) Math.ceil(len) + 8;
        for (int s = -4; s <= steps; s++) {
            double t = s / (double) Math.max(1, steps - 8);
            double cx = c.x0() + ux * s, cz = c.z0() + uz * s;
            double arch = Math.sin(Math.PI * Math.max(0, Math.min(1, t)));
            for (int w = -4; w <= 4; w++) {
                int x = (int) Math.floor(cx + px * w), z = (int) Math.floor(cz + pz * w);
                ReachTerrain.Column col = terrain.column(x, z);
                boolean parapet = Math.abs(w) == 4;
                canvas.set(x, deck - 1, z, parapet ? Material.STONE_BRICKS : Math.abs(w) <= 1 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
                if (parapet) {
                    canvas.set(x, deck, z, Material.STONE_BRICK_WALL, "up=true");
                    if (s == 0 || s == steps - 8) canvas.set(x, deck + 1, z, Material.LANTERN);
                } else for (int y = deck; y < deck + 4; y++) canvas.set(x, y, z, Material.AIR);
                // The arch and abutments underneath.
                int soffit = (int) Math.round(deck - 2 - (1 - arch) * 6);
                int ground = col.land() ? (col.wet() ? col.water() + 1 : col.top() + 1) : deck - 8;
                for (int y = Math.min(soffit, deck - 2); y < deck - 1; y++) if (y >= ground) canvas.set(x, y, z, Material.STONE_BRICKS);
                if (!col.wet()) for (int y = col.top() + 1; y < deck - 1; y++) canvas.set(x, y, z, y < deck - 3 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
        double mx = (c.x0() + c.x1()) / 2, mz = (c.z0() + c.z1()) / 2;
        return new ReachFeatures.Built(canvas, List.of(new ReachFeatures.Spot("deck", Math.floor(mx) + 0.5, deck, Math.floor(mz) + 0.5, 0F)));
    }
}
