package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * A sky-galleon moored at Highcrown's piers. The hull is shaped from smooth width and depth profiles: a rounded
 * stern, full midships and a sharp raked bow, round in section with a keel. It has planked strakes over a painted
 * band, a deck with bulwarks, a raised quarterdeck over the captain's cabin and a forecastle with a jib on a short
 * foremast. Over it all rides a long roped gas envelope in the ship's colours with fins at its tail, as the skyships
 * of the Reach are built. Skyfaring wings of timber and canvas stand out from either side with propellers at their
 * tips, and another turns at the stern. It is built in a brush's frame: local x across (port negative),
 * z from the stern (0) to the bow, y up from the main deck's planking (0).
 */
final class ReachAirship {
    private ReachAirship() { }

    /** The hull's half-width at a fraction {@code t} of its length (0 stern, 1 bow). */
    static double halfWidth(double t, double beam) {
        double f = t < 0.12 ? 0.78 + 0.22 * (t / 0.12) : t < 0.55 ? 1 : Math.pow(Math.cos((t - 0.55) / 0.45 * Math.PI / 2), 0.75);
        return beam / 2 * f;
    }

    /** The hull's depth below the deck at a fraction of its length. */
    static double depth(double t, double draught) {
        return draught * (t < 0.1 ? 0.65 + 0.35 * t / 0.1 : t < 0.7 ? 1 : 1 - 0.55 * (t - 0.7) / 0.3);
    }

    /** Builds a galleon {@code length} long and {@code beam} wide; {@code paint} is the band at the waterline. */
    static void build(ReachBrush b, int length, int beam, Material paint, long seed) {
        int draught = 7;
        for (int z = 0; z <= length; z++) {
            double t = z / (double) length;
            double hw = halfWidth(t, beam), dep = depth(t, draught);
            int rise = t > 0.82 ? (int) Math.round((t - 0.82) / 0.18 * 2) : 0;
            int max = (int) Math.ceil(hw) + 1;
            for (int x = -max; x <= max; x++) for (int y = -(int) Math.ceil(dep) - 1; y <= 2 + rise; y++) {
                double yy = Math.min(0, y);
                double section = hw * Math.pow(Math.max(0, 1 - Math.pow(-yy / Math.max(1, dep), 2.2)), 0.55);
                boolean inside = Math.abs(x) <= section + 0.2 && -yy <= dep;
                if (!inside) continue;
                boolean edge = Math.abs(x) > section - 0.9 || -yy > dep - 1 || z == 0 || z == length;
                if (y > 0) {
                    // Bulwarks: the hull's side rises above the deck at its edges.
                    if (Math.abs(x) > section - 0.9) b.set(x, y, z, y == 1 + rise ? Material.DARK_OAK_PLANKS : Material.AIR);
                    continue;
                }
                if (y == 0) { b.set(x, 0, z, Math.abs(x) > section - 0.9 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS); continue; }
                if (!edge) { b.set(x, y, z, Material.AIR); continue; }
                Material m = y == -1 ? Material.STRIPPED_DARK_OAK_WOOD : y == -2 ? paint : y > -4 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
                b.set(x, y, z, m);
            }
            // The keel along the bottom.
            b.set(0, -(int) Math.ceil(dep) - 1, z, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
            // A rubbing strake of upside-down stairs along each side.
            int sx = (int) Math.round(hw);
            b.set(-sx - 1, -1, z, Material.DARK_OAK_STAIRS, "facing=east,half=top");
            b.set(sx + 1, -1, z, Material.DARK_OAK_STAIRS, "facing=west,half=top");
        }
        int qz = (int) (length * 0.24), fz = (int) (length * 0.84);
        // The quarterdeck over the captain's cabin, with stern windows and lanterns.
        for (int z = 0; z <= qz; z++) {
            int hw = (int) Math.floor(halfWidth(z / (double) length, beam));
            for (int x = -hw; x <= hw; x++) {
                boolean wall = Math.abs(x) == hw || z == 0 || z == qz;
                for (int y = 1; y <= 3; y++) {
                    if (wall) {
                        boolean window = (z == 0 && Math.abs(x) % 2 == 1 || Math.abs(x) == hw && z % 3 == 1) && y == 2;
                        boolean door = z == qz && x == 0 && y <= 2;
                        b.set(x, y, z, door ? (y == 1 ? Material.SPRUCE_DOOR : Material.SPRUCE_DOOR) : window ? Material.GLASS_PANE : Material.STRIPPED_SPRUCE_WOOD,
                            door ? "facing=south,half=" + (y == 1 ? "lower" : "upper") + ",hinge=left,open=false" : "");
                    } else b.set(x, y, z, Material.AIR);
                }
                b.set(x, 4, z, Material.SPRUCE_PLANKS);
                if (Math.abs(x) == hw || z == 0) b.set(x, 5, z, Material.DARK_OAK_FENCE);
            }
            if (z == 0) for (int x : new int[] {-hw + 1, hw - 1}) b.set(x, 5, -1, Material.LANTERN);
        }
        for (int x = -1; x <= 1; x += 2) b.set(x * 2, 1, qz + 1, Material.SPRUCE_STAIRS, "facing=north,half=bottom");
        b.set(0, 5, 1, Material.DARK_OAK_FENCE);
        b.set(0, 6, 1, Material.GREEN_BANNER, "rotation=8");
        // The ship's wheel on the quarterdeck.
        b.set(0, 5, qz - 2, Material.STRIPPED_SPRUCE_LOG, "axis=y");
        b.set(0, 6, qz - 2, Material.SPRUCE_TRAPDOOR, "facing=south,half=bottom,open=true");
        // The forecastle.
        for (int z = fz; z <= length; z++) {
            int hw = (int) Math.floor(halfWidth(z / (double) length, beam));
            for (int x = -hw; x <= hw; x++) {
                b.set(x, 2, z, Material.SPRUCE_PLANKS);
                b.set(x, 1, z, Math.abs(x) == hw ? Material.DARK_OAK_PLANKS : Material.AIR);
                if (Math.abs(x) == hw) b.set(x, 3, z, Material.DARK_OAK_FENCE);
            }
        }
        // Bowsprit and figurehead.
        for (int k = 0; k <= 8; k++) b.set(0, 2 + k / 3, length + k, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        b.set(0, 1, length + 1, Material.GOLD_BLOCK);
        for (int k = 1; k <= 7; k++) b.set(0, 2 + k / 3 - 1, length + k, Material.IRON_CHAIN, "axis=z");
        // The envelope: a long gas balloon over the deck, round in section and pointed at both ends, in canvas panels
        // of white and the ship's colour, bound by rope bands, with fins at its tail.
        Material colour = switch (paint) {
            case GREEN_TERRACOTTA -> Material.GREEN_WOOL;
            case RED_TERRACOTTA -> Material.RED_WOOL;
            case BLUE_TERRACOTTA -> Material.BLUE_WOOL;
            default -> Material.LIGHT_BLUE_WOOL;
        };
        double radius = beam / 2.0 + 1.6, yc = 9 + radius;
        int z0 = -2, z1 = length + 1;
        double zc = (z0 + z1) / 2.0, halfLen = (z1 - z0) / 2.0;
        java.util.function.IntToDoubleFunction r = z -> {
            double t = (z - zc) / halfLen;
            return Math.abs(t) >= 1 ? 0 : radius * Math.pow(1 - t * t, 0.45);
        };
        for (int z = z0; z <= z1; z++) {
            double rz = r.applyAsDouble(z), inner = Math.min(r.applyAsDouble(z - 1), r.applyAsDouble(z + 1)) - 0.6;
            int span = (int) Math.ceil(rz);
            for (int x = -span; x <= span; x++) for (int y = (int) Math.floor(yc - span); y <= (int) Math.ceil(yc + span); y++) {
                double d = Math.hypot(x, y - yc);
                if (d > rz) continue;
                boolean shell = d > rz - 1.15 || d > inner;
                if (!shell) { b.set(x, y, z, Material.AIR); continue; }
                double angle = Math.toDegrees(Math.atan2(y - yc, x)) + 180;
                boolean rope = Math.floorMod(z - z0, 6) == 0 || Math.abs(x) <= 0 && y > yc;
                boolean panel = ((int) (angle / 30)) % 2 == 0;
                b.set(x, y, z, rope ? Material.BROWN_WOOL : panel ? colour : Material.WHITE_WOOL);
            }
        }
        // Tail fins: one above, two to the sides, each a framed canvas blade.
        for (int k = 0; k <= 6; k++) {
            int z = z0 + 1 + k, len = 5 - k * 5 / 7;
            for (int h = 1; h <= len; h++) {
                b.set(0, (int) Math.round(yc + r.applyAsDouble(z)) + h, z, h == len ? Material.STRIPPED_SPRUCE_LOG : colour, h == len ? "axis=z" : "");
                for (int side : new int[] {-1, 1})
                    b.set(side * ((int) Math.round(r.applyAsDouble(z)) + h), (int) Math.round(yc), z, h == len ? Material.STRIPPED_SPRUCE_LOG : colour, h == len ? "axis=z" : "");
            }
        }
        // Rigging: lines from the envelope's belly down to the bulwarks, and a keel spar under the balloon.
        for (double f : new double[] {0.18, 0.38, 0.58, 0.78}) {
            int z = (int) Math.round(length * f);
            int hx = Math.max(1, (int) Math.floor(halfWidth(z / (double) length, beam)) - 1);
            for (int side : new int[] {-1, 1}) {
                double rz = r.applyAsDouble(z);
                int belly = (int) Math.floor(yc - Math.sqrt(Math.max(0, rz * rz - hx * hx)));
                for (int y = 2; y < belly; y++) b.set(side * hx, y, z, Material.IRON_CHAIN, "axis=y");
            }
        }
        for (int z = (int) (length * 0.15); z <= (int) (length * 0.85); z++) {
            double rz = r.applyAsDouble(z);
            b.set(0, (int) Math.floor(yc - rz) - 1, z, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        }
        // A short foremast with a jib sail run out to the bowsprit.
        int fm = (int) (length * 0.88);
        for (int y = 3; y <= 8; y++) b.set(0, y, fm, Material.STRIPPED_SPRUCE_LOG, "axis=y");
        for (int z = fm + 1; z <= length + 6; z++) {
            int low = 3 + (z - length > 0 ? (z - length) / 3 : 0), high = 8 - (z - fm) * 5 / (length + 6 - fm);
            for (int y = low; y <= high; y++) b.set(0, y, z, Material.WHITE_WOOL);
        }
        // Skyfaring wings: a timber spar out from each side under the deck, with canvas stretched behind it.
        int wz0 = (int) (length * 0.32), wz1 = (int) (length * 0.58), reach = beam / 2 + 4;
        for (int side : new int[] {-1, 1}) {
            for (int k = beam / 2; k <= reach; k++) b.set(side * k, -2, wz1, Material.STRIPPED_SPRUCE_LOG, "axis=x");
            for (int k = beam / 2 + 1; k <= reach; k++) {
                int len = (int) Math.round((wz1 - wz0) * (1 - (k - beam / 2.0) / (reach - beam / 2.0) * 0.6));
                for (int z = wz1 - len; z < wz1; z++) b.set(side * k, -2, z, Material.WHITE_WOOL);
                if (k == reach) b.set(side * k, -1, wz1, Material.LANTERN);
            }
        }
        // Propellers on the wing tips.
        for (int side : new int[] {-1, 1}) {
            int px = side * reach, pz = wz0 - 1;
            b.set(px, -2, pz, Material.COPPER_BLOCK);
            for (int k = 1; k <= 2; k++) {
                b.set(px + k, -2, pz - 1, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=true");
                b.set(px - k, -2, pz - 1, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=true");
                b.set(px, -2 + k, pz - 1, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=true");
                b.set(px, -2 - k, pz - 1, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=true");
            }
        }
        // The stern propeller on its shaft.
        for (int k = 1; k <= 2; k++) b.set(0, -3, -k, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        for (int k = -3; k <= 3; k++) if (k != 0) {
            b.set(k, -3, -3, Material.SPRUCE_PLANKS);
            b.set(0, -3 + k, -3, Material.SPRUCE_PLANKS);
        }
        b.set(0, -3, -3, Material.COPPER_BLOCK);
        // Cargo and a hatch on the main deck.
        b.set(-2, 1, (int) (length * 0.5), Material.BARREL, "facing=up");
        b.set(-2, 2, (int) (length * 0.5), Material.BARREL, "facing=up");
        b.set(2, 1, (int) (length * 0.5), Material.HAY_BLOCK, "axis=y");
        for (int x = -1; x <= 1; x++) b.set(x, 0, (int) (length * 0.46), Material.SPRUCE_TRAPDOOR, "facing=north,half=top,open=false");
    }
}
