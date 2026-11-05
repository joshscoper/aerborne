package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.SplittableRandom;

/**
 * Hand-shaped trees for the whole Reach; no vanilla tree grows anywhere. Each species is grown from a seed, so a
 * regenerated chunk grows the same tree. Trunks flare into roots, limbs branch at angles and end in leaf clusters,
 * clusters mix two leaf tones in patches, and nothing is placed except into air. Pure and thread-safe: the same code
 * plants the generator's woods (through a LimitedRegion) and the builds' showpiece trees (through a canvas).
 */
public final class ReachTreeGen {
    /** Where a tree is written: only into air, never over a building. */
    public interface Writer {
        boolean air(int x, int y, int z);
        void set(int x, int y, int z, Material material, String properties);
    }

    public enum Species {
        OAK(9), BIRCH(5), SPRUCE(7), PINE(6), GIANT(14), WILLOW(9), CHERRY(9), ACACIA(8), DEAD(6), FRUIT(4), BUSH(3);
        /** How far the tree can reach from its trunk, so it is only planted where all of it fits. */
        public final int radius;
        Species(int radius) { this.radius = radius; }
    }

    private ReachTreeGen() { }

    public static void grow(Writer w, Species species, int x, int y, int z, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        switch (species) {
            case OAK -> oak(w, x, y, z, r, Material.OAK_LOG, Material.OAK_LEAVES, Material.DARK_OAK_LEAVES, false);
            case CHERRY -> oak(w, x, y, z, r, Material.CHERRY_LOG, Material.CHERRY_LEAVES, Material.CHERRY_LEAVES, true);
            case BIRCH -> birch(w, x, y, z, r);
            case SPRUCE -> spruce(w, x, y, z, r);
            case PINE -> pine(w, x, y, z, r);
            case GIANT -> giant(w, x, y, z, r);
            case WILLOW -> willow(w, x, y, z, r);
            case ACACIA -> acacia(w, x, y, z, r);
            case DEAD -> dead(w, x, y, z, r);
            case FRUIT -> fruit(w, x, y, z, r);
            case BUSH -> bush(w, x, y, z, r);
        }
    }

    // ------------------------------------------------------------------ pieces

    private static void log(Writer w, int x, int y, int z, Material log, String axis) {
        if (w.air(x, y, z)) w.set(x, y, z, log, "axis=" + axis);
    }

    /** The all-bark block for a log, so limbs and roots show bark on every face instead of cut rings. */
    static Material wood(Material log) {
        return switch (log) {
            case OAK_LOG -> Material.OAK_WOOD;
            case SPRUCE_LOG -> Material.SPRUCE_WOOD;
            case BIRCH_LOG -> Material.BIRCH_WOOD;
            case DARK_OAK_LOG -> Material.DARK_OAK_WOOD;
            case ACACIA_LOG -> Material.ACACIA_WOOD;
            case CHERRY_LOG -> Material.CHERRY_WOOD;
            case MANGROVE_LOG -> Material.MANGROVE_WOOD;
            case STRIPPED_DARK_OAK_LOG -> Material.STRIPPED_DARK_OAK_WOOD;
            default -> log;
        };
    }

    /**
     * A straight limb from (x, y, z) along (dx, dy, dz), {@code length} blocks, logs turned to the limb's main axis.
     * Returns the end point.
     */
    private static int[] limb(Writer w, double x, double y, double z, double dx, double dy, double dz, int length, Material log, boolean thick) {
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= n; dy /= n; dz /= n;
        String axis = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz)) * 0.9 ? "y" : Math.abs(dx) > Math.abs(dz) ? "x" : "z";
        Material bark = wood(log);
        int ex = (int) Math.floor(x), ey = (int) Math.floor(y), ez = (int) Math.floor(z);
        int px = ex, py = ey, pz = ez;
        // Half-block steps, and wherever a step moves on more than one axis the gap is filled through the faces,
        // so a limb is one continuous piece of timber rather than blocks touching at their corners.
        for (int i = 0; i <= length * 2; i++) {
            ex = (int) Math.floor(x + dx * i * 0.5); ey = (int) Math.floor(y + dy * i * 0.5); ez = (int) Math.floor(z + dz * i * 0.5);
            if (i > 0) {
                if (ex != px && (ey != py || ez != pz)) log(w, ex, py, pz, bark, axis);
                if (ez != pz && ey != py) log(w, ex, py, ez, bark, axis);
            }
            log(w, ex, ey, ez, bark, axis);
            if (thick && i < length * 1.2) {
                log(w, ex + 1, ey, ez, bark, axis);
                log(w, ex, ey, ez + 1, bark, axis);
            }
            px = ex; py = ey; pz = ez;
        }
        return new int[] {ex, ey, ez};
    }

    /**
     * A leaf cluster: an ellipsoid, flatter underneath, whose edge swells and dips in a few broad lumps (never
     * block-by-block speckle or holes), in two tones that change in patches rather than block by block.
     */
    private static void cluster(Writer w, int cx, int cy, int cz, double rx, double ry, double rz, Material leaf, Material second, SplittableRandom r) {
        int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
        double p1 = r.nextDouble() * Math.PI * 2, p2 = r.nextDouble() * Math.PI * 2, p3 = r.nextDouble() * Math.PI * 2;
        for (int dx = -ix; dx <= ix; dx++) for (int dy = -iy; dy <= iy; dy++) for (int dz = -iz; dz <= iz; dz++) {
            double d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
            double ax = dx / rx, az = dz / rz, ay = dy / ry;
            double lumps = 0.11 * Math.sin(ax * 2.6 + p1) * Math.cos(az * 2.3 + p2) + 0.07 * Math.sin(ay * 3.1 + ax * 1.7 + p3);
            double edge = (dy < 0 ? 0.78 : 1.0) - 0.08 + lumps;
            if (d > edge) continue;
            int x = cx + dx, y = cy + dy, z = cz + dz;
            if (!w.air(x, y, z)) continue;
            boolean tone = second != leaf && ((ReachFlora.hash((cx + dx) >> 1, (cz + dz) >> 1) ^ ((cy + dy) >> 1)) & 7) < 3;
            w.set(x, y, z, tone ? second : leaf, "persistent=true");
        }
    }

    /** Roots: short logs leaning out from the base, one or two reaching further and dipping into the ground. */
    private static void roots(Writer w, int x, int y, int z, int count, int reach, Material log, SplittableRandom r) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int i = 0; i < count; i++) {
            int[] d = dirs[r.nextInt(dirs.length)];
            int len = 1 + r.nextInt(Math.max(1, reach));
            for (int k = 1; k <= len; k++) {
                int yy = y + (k == 1 ? (r.nextBoolean() ? 1 : 0) : 0) - (k > 2 ? 1 : 0);
                log(w, x + d[0] * k, yy, z + d[1] * k, wood(log), d[0] != 0 && d[1] == 0 ? "x" : d[1] != 0 && d[0] == 0 ? "z" : "y");
            }
        }
    }

    // ------------------------------------------------------------------ species

    /** A broad oak (or cherry): a flared trunk splitting into three or four limbs, each ending in a cloud of leaves. */
    private static void oak(Writer w, int x, int y, int z, SplittableRandom r, Material log, Material leaf, Material second, boolean flat) {
        int h = 4 + r.nextInt(4);
        roots(w, x, y, z, 3 + r.nextInt(2), 2, log, r);
        for (int i = 0; i < h; i++) log(w, x, y + i, z, log, "y");
        int limbs = 3 + r.nextInt(2);
        double turn = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < limbs; i++) {
            double a = turn + i * (Math.PI * 2 / limbs) + (r.nextDouble() - 0.5) * 0.6;
            double rise = flat ? 0.35 + r.nextDouble() * 0.25 : 0.6 + r.nextDouble() * 0.5;
            int len = 3 + r.nextInt(3);
            int[] end = limb(w, x + 0.5, y + h - 1 - r.nextInt(2), z + 0.5, Math.cos(a), rise, Math.sin(a), len, log, false);
            // A twig off the limb, for an irregular crown.
            if (r.nextBoolean()) {
                double b = a + (r.nextBoolean() ? 0.8 : -0.8);
                int[] twig = limb(w, end[0] + 0.5, end[1] + 0.5, end[2] + 0.5, Math.cos(b), 0.5, Math.sin(b), 2, log, false);
                cluster(w, twig[0], twig[1] + 1, twig[2], 2.6, 1.8, 2.6, leaf, second, r);
            }
            cluster(w, end[0], end[1] + 1, end[2], 3.4 + r.nextDouble(), flat ? 1.8 : 2.4, 3.4 + r.nextDouble(), leaf, second, r);
        }
        cluster(w, x, y + h + 1, z, 3.2, flat ? 1.8 : 2.6, 3.2, leaf, second, r);
    }

    private static void birch(Writer w, int x, int y, int z, SplittableRandom r) {
        int h = 9 + r.nextInt(4);
        for (int i = 0; i < h; i++) log(w, x, y + i, z, Material.BIRCH_LOG, "y");
        for (int k = 0; k < 3; k++) {
            int ly = y + h / 2 + k * (h / 6) + r.nextInt(2);
            double a = r.nextDouble() * Math.PI * 2;
            int[] end = limb(w, x + 0.5, ly, z + 0.5, Math.cos(a), 0.7, Math.sin(a), 2, Material.BIRCH_LOG, false);
            cluster(w, end[0], end[1] + 1, end[2], 2.2, 1.7, 2.2, Material.BIRCH_LEAVES, Material.OAK_LEAVES, r);
        }
        cluster(w, x, y + h, z, 2.4, 2.6, 2.4, Material.BIRCH_LEAVES, Material.BIRCH_LEAVES, r);
    }

    /** A tall spruce: tiers of drooping branches with leaves gathered round them, narrowing to a spike. */
    private static void spruce(Writer w, int x, int y, int z, SplittableRandom r) {
        int h = 16 + r.nextInt(12);
        boolean thick = h > 24;
        roots(w, x, y, z, thick ? 5 : 3, thick ? 3 : 2, Material.SPRUCE_LOG, r);
        for (int i = 0; i < h; i++) {
            log(w, x, y + i, z, Material.SPRUCE_LOG, "y");
            if (thick && i < h * 0.7) { log(w, x + 1, y + i, z, Material.SPRUCE_LOG, "y"); log(w, x, y + i, z + 1, Material.SPRUCE_LOG, "y"); log(w, x + 1, y + i, z + 1, Material.SPRUCE_LOG, "y"); }
        }
        int start = h / 4 + r.nextInt(3);
        for (int ty = start; ty < h - 1; ty += 2 + r.nextInt(2)) {
            double t = (ty - start) / (double) (h - start);
            int len = (int) Math.round((thick ? 5.5 : 4) * (1 - t)) + 1;
            int arms = 4 + r.nextInt(3);
            double turn = r.nextDouble() * Math.PI;
            for (int a = 0; a < arms; a++) {
                double ang = turn + a * Math.PI * 2 / arms;
                double bx = x + 0.5 + Math.cos(ang) * (thick ? 1 : 0), bz = z + 0.5 + Math.sin(ang) * (thick ? 1 : 0);
                int[] end = limb(w, bx, y + ty, bz, Math.cos(ang), -0.25, Math.sin(ang), Math.max(1, len - 1), Material.SPRUCE_LOG, false);
                cluster(w, (int) Math.round((bx + end[0]) / 2), end[1], (int) Math.round((bz + end[2]) / 2), len * 0.7 + 1, 1.2, len * 0.7 + 1,
                    Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES, r);
            }
        }
        for (int i = 0; i < 3; i++) if (w.air(x, y + h + i, z)) w.set(x, y + h + i, z, Material.SPRUCE_LEAVES, "persistent=true");
    }

    /** A pine: a long bare trunk and a ragged crown of flat sprays near the top. */
    private static void pine(Writer w, int x, int y, int z, SplittableRandom r) {
        int h = 14 + r.nextInt(8);
        roots(w, x, y, z, 3, 2, Material.SPRUCE_LOG, r);
        for (int i = 0; i < h; i++) log(w, x, y + i, z, Material.SPRUCE_LOG, "y");
        for (int k = 0; k < 4; k++) {
            int ly = y + (int) (h * (0.62 + k * 0.09));
            double a = r.nextDouble() * Math.PI * 2;
            int[] end = limb(w, x + 0.5, ly, z + 0.5, Math.cos(a), 0.15, Math.sin(a), 2 + r.nextInt(3), Material.SPRUCE_LOG, false);
            cluster(w, end[0], end[1] + 1, end[2], 2.8, 1.1, 2.8, Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES, r);
        }
        cluster(w, x, y + h, z, 2.2, 1.6, 2.2, Material.SPRUCE_LEAVES, Material.SPRUCE_LEAVES, r);
    }

    /** Thornwood's giants: a 2x2 trunk on buttress roots, great limbs, hanging vines. */
    private static void giant(Writer w, int x, int y, int z, SplittableRandom r) {
        Material log = r.nextInt(3) == 0 ? Material.SPRUCE_LOG : Material.DARK_OAK_LOG;
        int h = 20 + r.nextInt(12);
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7 + r.nextDouble() * 0.4;
            int len = 3 + r.nextInt(4);
            for (int k = 0; k < len; k++) {
                int rx = x + (int) Math.round(Math.cos(a) * (k + 1.5)), rz = z + (int) Math.round(Math.sin(a) * (k + 1.5));
                int top = Math.max(0, 3 - k);
                for (int yy = -1; yy <= top; yy++) log(w, rx, y + yy, rz, log, "y");
            }
        }
        for (int i = 0; i < h; i++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) log(w, x + dx, y + i, z + dz, log, "y");
        int limbs = 4 + r.nextInt(3);
        double turn = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < limbs; i++) {
            double a = turn + i * Math.PI * 2 / limbs + (r.nextDouble() - 0.5) * 0.5;
            int ly = y + (int) (h * (0.5 + r.nextDouble() * 0.35));
            int len = 6 + r.nextInt(5);
            int[] end = limb(w, x + 1 + Math.cos(a), ly, z + 1 + Math.sin(a), Math.cos(a), 0.45, Math.sin(a), len, log, true);
            cluster(w, end[0], end[1] + 1, end[2], 4.5, 2.6, 4.5, Material.DARK_OAK_LEAVES, Material.OAK_LEAVES, r);
            vines(w, end[0], end[1] - 1, end[2], 4, r);
        }
        cluster(w, x + 1, y + h + 1, z + 1, 5, 3, 5, Material.DARK_OAK_LEAVES, Material.OAK_LEAVES, r);
    }

    /** Vines hanging below a cluster's underside. */
    private static void vines(Writer w, int cx, int cy, int cz, int radius, SplittableRandom r) {
        for (int k = 0; k < 6; k++) {
            int vx = cx + r.nextInt(radius * 2 + 1) - radius, vz = cz + r.nextInt(radius * 2 + 1) - radius;
            int top = cy;
            while (top < cy + 4 && w.air(vx, top, vz)) top++;
            if (w.air(vx, top, vz)) continue;
            int len = 2 + r.nextInt(5);
            for (int i = 1; i <= len && w.air(vx, top - i, vz); i++) w.set(vx, top - i, vz, Material.VINE, "up=true");
        }
    }

    /** A willow by the water: a leaning trunk, a wide crown and curtains of leaves trailing down. */
    private static void willow(Writer w, int x, int y, int z, SplittableRandom r) {
        double lean = r.nextDouble() * Math.PI * 2;
        int h = 6 + r.nextInt(3);
        roots(w, x, y, z, 4, 2, Material.OAK_LOG, r);
        int[] top = limb(w, x + 0.5, y, z + 0.5, Math.cos(lean) * 0.3, 1, Math.sin(lean) * 0.3, h, Material.OAK_LOG, false);
        cluster(w, top[0], top[1] + 1, top[2], 4.5, 2.2, 4.5, Material.OAK_LEAVES, Material.MANGROVE_LEAVES, r);
        for (int k = 0; k < 14; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = 3 + r.nextDouble() * 1.5;
            int vx = top[0] + (int) Math.round(Math.cos(a) * d), vz = top[2] + (int) Math.round(Math.sin(a) * d);
            int len = 2 + r.nextInt(4);
            for (int i = 0; i < len; i++) if (w.air(vx, top[1] - i, vz)) w.set(vx, top[1] - i, vz, Material.OAK_LEAVES, "persistent=true");
        }
    }

    /** An acacia: a crooked trunk forking into flat, layered canopies. */
    private static void acacia(Writer w, int x, int y, int z, SplittableRandom r) {
        int h = 3 + r.nextInt(2);
        for (int i = 0; i < h; i++) log(w, x, y + i, z, Material.ACACIA_LOG, "y");
        for (int k = 0; k < 2; k++) {
            double a = r.nextDouble() * Math.PI * 2;
            int[] end = limb(w, x + 0.5, y + h, z + 0.5, Math.cos(a), 1, Math.sin(a), 3 + r.nextInt(2), Material.ACACIA_LOG, false);
            cluster(w, end[0], end[1] + 1, end[2], 3.6, 0.8, 3.6, Material.ACACIA_LEAVES, Material.ACACIA_LEAVES, r);
        }
    }

    /** A dead tree, burned or drowned: bare limbs forking, no leaves. */
    private static void dead(Writer w, int x, int y, int z, SplittableRandom r) {
        Material log = r.nextBoolean() ? Material.DARK_OAK_LOG : Material.STRIPPED_DARK_OAK_LOG;
        int h = 4 + r.nextInt(5);
        roots(w, x, y, z, 2, 2, log, r);
        for (int i = 0; i < h; i++) log(w, x, y + i, z, log, "y");
        for (int k = 0; k < 2 + r.nextInt(3); k++) {
            double a = r.nextDouble() * Math.PI * 2;
            int[] end = limb(w, x + 0.5, y + h - 1 - r.nextInt(3), z + 0.5, Math.cos(a), 0.8, Math.sin(a), 2 + r.nextInt(3), log, false);
            if (r.nextBoolean()) limb(w, end[0] + 0.5, end[1] + 0.5, end[2] + 0.5, Math.cos(a + 0.9), 0.9, Math.sin(a + 0.9), 2, log, false);
        }
    }

    /** A low orchard tree: a short crooked trunk and a dome dotted with blossom. */
    private static void fruit(Writer w, int x, int y, int z, SplittableRandom r) {
        int h = 2 + r.nextInt(2);
        for (int i = 0; i < h; i++) log(w, x, y + i, z, Material.OAK_LOG, "y");
        double a = r.nextDouble() * Math.PI * 2;
        int[] end = limb(w, x + 0.5, y + h, z + 0.5, Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5, 2, Material.OAK_LOG, false);
        cluster(w, end[0], end[1], end[2], 3, 2, 3, Material.OAK_LEAVES, Material.FLOWERING_AZALEA_LEAVES, r);
    }

    /** A bush: a low mound of leaves round a stub, in one of the Vale's greens. */
    private static void bush(Writer w, int x, int y, int z, SplittableRandom r) {
        Material leaf = switch (r.nextInt(4)) { case 0 -> Material.AZALEA_LEAVES; case 1 -> Material.DARK_OAK_LEAVES; default -> Material.OAK_LEAVES; };
        Material second = r.nextInt(3) == 0 ? Material.FLOWERING_AZALEA_LEAVES : leaf;
        log(w, x, y, z, Material.OAK_LOG, "y");
        cluster(w, x, y, z, 1.6 + r.nextDouble(), 1.2, 1.6 + r.nextDouble(), leaf, second, r);
    }
}
