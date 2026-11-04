package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * Hand-shaped trees for places that need more than vanilla's: orchard fruit trees and the very tall spruce that
 * tower over Goldenreach's farms (ref 7). Leaves are persistent so nothing decays.
 */
final class ReachTrees {
    private ReachTrees() { }

    /** A low, round fruit tree: short crooked trunk, a dome of oak leaves dotted with blossom. */
    static void fruit(ReachCanvas c, int x, int y, int z, long seed) {
        int trunk = 3 + (int) (seed & 1);
        for (int i = 0; i < trunk; i++) c.set(x + (i == trunk - 1 && (seed & 2) != 0 ? 1 : 0), y + i, z, Material.OAK_LOG, "axis=y");
        int cx = x + ((seed & 2) != 0 ? 1 : 0), cy = y + trunk;
        for (int dx = -3; dx <= 3; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -3; dz <= 3; dz++) {
            double r = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz);
            long h = ReachFlora.hash(cx + dx * 13, cz(z, dz) + dy * 7);
            if (r > 3.2 + ((h & 7) - 3) * 0.12) continue;
            boolean blossom = (h & 0x70) == 0x10;
            c.set(cx + dx, cy + dy, z + dz, blossom ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES, "persistent=true");
        }
        c.set(cx, cy - 1, z, Material.OAK_LOG, "axis=y");
        c.set(cx, cy, z, Material.OAK_LOG, "axis=y");
    }

    private static int cz(int z, int dz) { return z * 31 + dz; }

    /** A very tall spruce: a straight trunk, tiers of drooping branches thinning toward a spike. */
    static void tallSpruce(ReachCanvas c, int x, int y, int z, int height, long seed) {
        boolean thick = height > 24;
        for (int i = 0; i < height; i++) {
            c.set(x, y + i, z, Material.SPRUCE_LOG, "axis=y");
            if (thick && i < height - 6) { c.set(x + 1, y + i, z, Material.SPRUCE_LOG, "axis=y"); c.set(x, y + i, z + 1, Material.SPRUCE_LOG, "axis=y"); c.set(x + 1, y + i, z + 1, Material.SPRUCE_LOG, "axis=y"); }
        }
        if (thick) for (int[] d : new int[][] {{-1, 0}, {2, 0}, {0, -1}, {0, 2}, {2, 1}, {1, 2}, {-1, 1}, {1, -1}})
            c.set(x + d[0], y, z + d[1], Material.SPRUCE_WOOD, "axis=" + (d[0] < 0 || d[0] > 1 ? "x" : "z"));
        int start = height / 3;
        for (int i = start; i < height; i++) {
            double t = (i - start) / (double) (height - start);
            int radius = (int) Math.round((thick ? 5 : 3.5) * (1 - t) + 1);
            if ((i - start) % 3 == 2) radius = Math.max(1, radius - 2);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                double r = Math.hypot(dx, dz);
                long h = ReachFlora.hash(x + dx * 17 + i, z + dz * 29 - i);
                if (r > radius + 0.3 || r > radius - 0.6 && (h & 3) == 0) continue;
                int droop = r > radius - 1.2 ? -1 : 0;
                c.set(x + dx, y + i + droop, z + dz, Material.SPRUCE_LEAVES, "persistent=true");
            }
        }
        for (int i = 0; i < 3; i++) c.set(x, y + height + i, z, Material.SPRUCE_LEAVES, "persistent=true");
    }
}
