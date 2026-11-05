package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * Heroic statues: a figure sculpted from posed body parts at any scale, on a stepped pedestal with a lettered plaque.
 * The figure faces local -z, its right hand toward local +x, its feet on the brush's floor. Parts are boxes and limbs
 * in "units" (a figure stands about twelve units tall), rasterised at {@code scale} blocks per unit, so a statue can
 * be life-size or towering without being redrawn.
 */
final class ReachStatues {
    private ReachStatues() { }

    enum Pose { SWORD_RAISED, SWORD_PLANTED, LANTERN, BOW, STAFF, SPEAR, HAMMER_SHIELD }

    /** What a statue is carved or cast in: flesh and cloth, armour, the cloak, gilded trim, and blades. */
    record Palette(Material body, Material armour, Material cloak, Material trim, Material metal) {
        static final Palette GRANITE = new Palette(Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.STONE_BRICKS,
            Material.GOLD_BLOCK, Material.POLISHED_DIORITE);
        static final Palette MARBLE = new Palette(Material.CALCITE, Material.POLISHED_DIORITE, Material.SMOOTH_QUARTZ,
            Material.GOLD_BLOCK, Material.IRON_BLOCK);
        static final Palette BRONZE = new Palette(Material.OXIDIZED_COPPER, Material.OXIDIZED_CUT_COPPER, Material.WEATHERED_COPPER,
            Material.GOLD_BLOCK, Material.WAXED_COPPER_BLOCK);
    }

    private record Sculptor(ReachBrush b, double s) {
        void box(double x0, double y0, double z0, double x1, double y1, double z1, Material m) {
            for (int x = (int) Math.floor(x0 * s); x <= (int) Math.ceil(x1 * s); x++)
                for (int y = (int) Math.floor(y0 * s); y <= (int) Math.ceil(y1 * s); y++)
                    for (int z = (int) Math.floor(z0 * s); z <= (int) Math.ceil(z1 * s); z++) {
                        double cx = (x + 0.5) / s, cy = (y + 0.5) / s, cz = (z + 0.5) / s;
                        if (cx >= x0 && cx <= x1 && cy >= y0 && cy <= y1 && cz >= z0 && cz <= z1) b.set(x, y, z, m);
                    }
        }

        /** A rounded limb or shaft from one point to another, {@code r} units thick. */
        void limb(double[] a, double[] c, double r, Material m) {
            double len = Math.sqrt(Math.pow(c[0] - a[0], 2) + Math.pow(c[1] - a[1], 2) + Math.pow(c[2] - a[2], 2));
            int steps = Math.max(2, (int) Math.ceil(len * s * 2));
            double rr = r * s;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double px = (a[0] + (c[0] - a[0]) * t) * s, py = (a[1] + (c[1] - a[1]) * t) * s, pz = (a[2] + (c[2] - a[2]) * t) * s;
                for (int x = (int) Math.floor(px - rr); x <= (int) Math.ceil(px + rr); x++)
                    for (int y = (int) Math.floor(py - rr); y <= (int) Math.ceil(py + rr); y++)
                        for (int z = (int) Math.floor(pz - rr); z <= (int) Math.ceil(pz + rr); z++)
                            if (Math.pow(x + 0.5 - px, 2) + Math.pow(y + 0.5 - py, 2) + Math.pow(z + 0.5 - pz, 2) <= rr * rr + 0.25) b.set(x, y, z, m);
            }
        }

        void ball(double[] c, double r, Material m) { limb(c, c, r, m); }
    }

    private static double[] p(double x, double y, double z) { return new double[] {x, y, z}; }

    /** A hero standing on the brush's floor, {@code scale} blocks to a unit (about twelve units tall). */
    static void hero(ReachBrush b, double scale, Pose pose, Palette pal) {
        Sculptor f = new Sculptor(b, scale);
        // The cloak first, so the body is carved in front of it.
        f.box(-2.1, 1.3, 0.9, 2.1, 9.4, 1.5, pal.cloak());
        f.box(-2.6, 0.9, 1.0, 2.6, 3.6, 1.9, pal.cloak());
        // Boots, legs, tasset, belt and torso; a breastplate standing proud.
        f.box(-1.6, 0, -1.1, -0.2, 1.3, 0.7, pal.armour());
        f.box(0.2, 0, -1.1, 1.6, 1.3, 0.7, pal.armour());
        f.box(-1.5, 1.3, -0.6, -0.3, 5, 0.6, pal.body());
        f.box(0.3, 1.3, -0.6, 1.5, 5, 0.6, pal.body());
        f.box(-1.8, 4, -0.9, 1.8, 5.6, 0.9, pal.armour());
        f.box(-1.85, 5.6, -0.95, 1.85, 6.1, 0.95, pal.trim());
        f.box(-1.7, 6.1, -0.85, 1.7, 9.2, 0.85, pal.body());
        f.box(-1.4, 7, -1.15, 1.4, 9, -0.85, pal.armour());
        // Pauldrons, neck, head and helm with a crest.
        f.box(-2.7, 8.4, -1, -1.6, 9.7, 1, pal.armour());
        f.box(1.6, 8.4, -1, 2.7, 9.7, 1, pal.armour());
        f.box(-0.4, 9.2, -0.4, 0.4, 9.7, 0.4, pal.body());
        f.box(-0.75, 9.7, -0.8, 0.75, 11.1, 0.75, pal.body());
        f.box(-0.85, 10.6, -0.85, 0.85, 11.4, 0.85, pal.armour());
        f.box(-0.15, 11.4, -0.7, 0.15, 12, 0.7, pal.trim());
        double[] rs = p(2.1, 8.8, 0), ls = p(-2.1, 8.8, 0);
        switch (pose) {
            case SWORD_RAISED -> {
                arm(f, rs, p(2.6, 10.6, -0.4), p(2.4, 12.4, -0.6), pal);
                f.limb(p(1.6, 12.9, -0.6), p(3.2, 12.9, -0.6), 0.25, pal.trim());
                f.limb(p(2.4, 13.1, -0.6), p(2.4, 18.2, -0.6), 0.28, pal.metal());
                arm(f, ls, p(-2.6, 7.2, -0.5), p(-2.5, 6.0, -1.2), pal);
                shield(f, pal);
            }
            case SWORD_PLANTED -> {
                arm(f, rs, p(1.9, 7.4, -1.2), p(0.4, 6.4, -2.0), pal);
                arm(f, ls, p(-1.9, 7.4, -1.2), p(-0.4, 6.4, -2.0), pal);
                f.ball(p(0, 7.0, -2.1), 0.35, pal.trim());
                f.limb(p(-1.3, 5.8, -2.1), p(1.3, 5.8, -2.1), 0.25, pal.trim());
                f.limb(p(0, 5.6, -2.1), p(0, 0.3, -2.1), 0.3, pal.metal());
            }
            case LANTERN -> {
                arm(f, rs, p(2.8, 9.8, -1.2), p(2.6, 11.4, -2.6), pal);
                f.limb(p(2.6, 11.4, -2.7), p(2.6, 10.6, -2.7), 0.15, pal.trim());
                f.box(2.0, 9.0, -3.3, 3.2, 10.5, -2.1, Material.OCHRE_FROGLIGHT);
                f.box(1.9, 10.5, -3.4, 3.3, 10.9, -2.0, pal.trim());
                arm(f, ls, p(-2.5, 7.0, 0.2), p(-2.3, 5.4, -0.2), pal);
            }
            case BOW -> {
                arm(f, ls, p(-2.2, 8.9, -1.8), p(-1.6, 8.9, -3.6), pal);
                f.limb(p(-1.6, 5.6, -3.0), p(-1.6, 8.9, -4.2), 0.25, pal.armour());
                f.limb(p(-1.6, 8.9, -4.2), p(-1.6, 12.2, -3.0), 0.25, pal.armour());
                arm(f, rs, p(2.6, 9.2, 1.0), p(0.9, 9.0, -0.6), pal);
            }
            case STAFF -> {
                arm(f, rs, p(2.5, 7.8, -1.0), p(2.4, 7.6, -2.0), pal);
                f.limb(p(2.4, 0.2, -2.0), p(2.4, 13.4, -2.0), 0.3, pal.armour());
                f.ball(p(2.4, 14.2, -2.0), 0.8, Material.AMETHYST_BLOCK);
                arm(f, ls, p(-2.8, 10.2, -0.6), p(-2.6, 11.6, -1.6), pal);
            }
            case SPEAR -> {
                arm(f, rs, p(2.6, 7.6, -0.5), p(2.5, 7.4, -1.4), pal);
                f.limb(p(2.5, 0.2, -1.4), p(2.5, 15.4, -1.4), 0.28, pal.armour());
                f.box(2.15, 15.4, -1.75, 2.85, 17.4, -1.05, pal.metal());
                arm(f, ls, p(-2.9, 7.2, 0.4), p(-1.9, 5.9, -0.6), pal);
            }
            case HAMMER_SHIELD -> {
                arm(f, rs, p(2.9, 9.8, 0.3), p(2.6, 11.0, 1.1), pal);
                f.limb(p(2.6, 10.4, 0.8), p(2.6, 14.2, 2.4), 0.25, pal.armour());
                f.box(1.7, 13.7, 1.7, 3.5, 15.3, 3.1, pal.metal());
                arm(f, ls, p(-2.6, 7.2, -0.5), p(-2.5, 6.0, -1.2), pal);
                shield(f, pal);
            }
        }
    }

    private static void arm(Sculptor f, double[] shoulder, double[] elbow, double[] hand, Palette pal) {
        f.limb(shoulder, elbow, 0.62, pal.body());
        f.limb(elbow, hand, 0.55, pal.armour());
        f.ball(hand, 0.5, pal.body());
    }

    private static void shield(Sculptor f, Palette pal) {
        f.box(-3.6, 4.4, -2.2, -3.0, 8.8, 0.6, pal.armour());
        f.box(-3.7, 5.0, -1.9, -3.5, 8.2, 0.3, pal.trim());
        f.ball(p(-3.9, 6.6, -0.8), 0.45, pal.trim());
    }

    /**
     * A stepped pedestal {@code 2 * half + 1} square and {@code height} tall: a broad step, a carved die with a
     * frieze, a cornice, and a plaque on its front face lettered with the hero's name. Returns the height of its top.
     */
    static int pedestal(ReachBrush b, int half, int height, String... plaque) {
        for (int x = -half - 1; x <= half + 1; x++) for (int z = -half - 1; z <= half + 1; z++) {
            boolean rim = Math.abs(x) == half + 1 || Math.abs(z) == half + 1;
            b.set(x, 0, z, rim ? Material.STONE_BRICK_STAIRS : Material.STONE_BRICKS,
                rim ? "facing=" + (x == -half - 1 ? "east" : x == half + 1 ? "west" : z == -half - 1 ? "south" : "north") + ",half=bottom" : "");
        }
        for (int y = 1; y < height; y++) for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) {
            boolean edge = Math.abs(x) == half || Math.abs(z) == half;
            boolean corner = Math.abs(x) == half && Math.abs(z) == half;
            b.set(x, y, z, corner ? Material.POLISHED_ANDESITE : edge && y == height - 2 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
        }
        for (int x = -half - 1; x <= half + 1; x++) for (int z = -half - 1; z <= half + 1; z++) {
            boolean rim = Math.abs(x) == half + 1 || Math.abs(z) == half + 1;
            if (rim) b.set(x, height - 1, z, Material.STONE_BRICK_STAIRS,
                "facing=" + (x == -half - 1 ? "west" : x == half + 1 ? "east" : z == -half - 1 ? "north" : "south") + ",half=top");
            b.set(x, height, z, rim ? Material.STONE_BRICK_SLAB : Material.POLISHED_ANDESITE, rim ? "type=bottom" : "");
        }
        if (plaque.length > 0) {
            b.set(0, height / 2, -half - 1, Material.DARK_OAK_WALL_SIGN, "facing=north");
            b.canvas.text(b.wx(0, -half - 1), b.oy + height / 2, b.wz(0, -half - 1), plaque);
            for (int x : new int[] {-half, half}) b.set(x, height + 1, -half, Material.LANTERN);
        }
        return height;
    }
}
