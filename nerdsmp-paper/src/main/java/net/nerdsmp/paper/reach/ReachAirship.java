package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * A sky-galleon moored at Highcrown's piers. The hull is shaped from smooth width and depth profiles: a rounded
 * stern, full midships and a sharp raked bow, round in section with a keel. It has planked strakes over a painted
 * band, a deck with bulwarks, a raised quarterdeck over the captain's cabin and a forecastle. Three masts carry
 * square sails that belly in the wind, with a bowsprit and jib. Skyfaring wings of timber and canvas stand out from
 * either side and a propeller turns at the stern. It is built in a brush's frame: local x across (port negative),
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
        // Masts with square sails bellying forward, yards, tops and pennants.
        int[][] masts = {{(int) (length * 0.36), 22, 11}, {(int) (length * 0.6), 19, 9}, {(int) (length * 0.8), 15, 7}};
        for (int[] m : masts) {
            int mz = m[0], top = m[1], span = m[2];
            for (int y = 1; y <= top; y++) b.set(0, y, mz, Material.STRIPPED_SPRUCE_LOG, "axis=y");
            for (int[] yard : new int[][] {{top - 2, span}, {top - 8, span + 2}}) {
                int half = yard[1] / 2;
                for (int x = -half; x <= half; x++) b.set(x, yard[0], mz, Material.STRIPPED_SPRUCE_LOG, "axis=x");
                for (int y = yard[0] - 1; y >= yard[0] - 5; y--) for (int x = -half + 1; x <= half - 1; x++) {
                    double belly = 1 - Math.pow(x / (double) half, 2);
                    int dz = belly > 0.35 && y < yard[0] - 1 && y > yard[0] - 5 ? 1 : 0;
                    if (x == 0 && dz == 0) continue;
                    b.set(x, y, mz + dz, (y == yard[0] - 3 && Math.abs(x) <= 1) ? Material.GREEN_WOOL : Material.WHITE_WOOL);
                }
                // Lines from the yard's ends down to the bulwarks.
                for (int y = yard[0] - 1; y >= 2; y--) for (int x : new int[] {-half, half}) if (Math.abs(x) <= beam / 2) b.set(x, y, mz - 1, Material.IRON_CHAIN, "axis=y");
            }
            for (int x = -1; x <= 1; x++) for (int dz = -1; dz <= 1; dz++) if (x != 0 || dz != 0) b.set(x, top - 5, mz + dz, Material.SPRUCE_SLAB, "type=top");
            b.set(0, top + 1, mz, Material.DARK_OAK_FENCE);
            b.set(0, top + 2, mz, Material.GREEN_BANNER, "rotation=8");
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
