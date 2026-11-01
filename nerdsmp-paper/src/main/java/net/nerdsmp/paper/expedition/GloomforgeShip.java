package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

/**
 * The Gloomforge: the mine's whole wealth built into one vessel at the bottom of the world. She's built like a
 * real hull, three times as long as she's wide, with a keel, a wineglass section, a sheer that sweeps up to bow and
 * stern, sides that lean back in above the waterline, and a raised foredeck. Astern stands a pagoda of cabins under
 * curved roofs of bright and weathered copper with the forge burning in its windows. Two raked masts carry furled
 * yards, and the rigging droops the way rope does. She lies along Z with her stern north; the main deck is the
 * Foreman's arena.
 */
final class GloomforgeShip {
    static final int X = 236, Z = 40, WIDE = 13, LONG = 38, DECK = MinesTemplate.DECK, FOREDECK = 62;
    private static final Material RAIL = CanopyKit.RAIL, POST = Material.STRIPPED_DARK_OAK_LOG;
    private static final Material[][] TILES = {
        {Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Material.WAXED_OXIDIZED_CUT_COPPER},
        {Material.WAXED_WEATHERED_CUT_COPPER_STAIRS, Material.WAXED_WEATHERED_CUT_COPPER},
        {Material.WAXED_EXPOSED_CUT_COPPER_STAIRS, Material.WAXED_EXPOSED_CUT_COPPER},
        {Material.WAXED_CUT_COPPER_STAIRS, Material.WAXED_CUT_COPPER}};

    private GloomforgeShip() { }

    /** Half her width at the deck, -1 at the stern to 1 at the bow. */
    private static double beam(double t) {
        double from = Math.abs(t);
        if (from >= 1) return 0;
        return t >= 0 ? WIDE * Math.pow(1 - Math.pow(from, 2.2), .64) : Math.max(7, WIDE * Math.pow(1 - Math.pow(from, 5), .5));
    }

    private static double along(int z, int rel) {
        return z >= Z ? (z - Z - rel * .5) / (double) LONG : (z - Z + rel * .3) / (double) LONG;
    }

    private static double keel(double t) { return t >= 0 ? -13 + 15 * Math.pow(t, 2.6) : -13 + 8 * Math.pow(-t, 3); }

    /** How far the strakes climb toward the ends: the sheer. */
    private static double lift(double t) { return t >= 0 ? 4 * t * t : 5 * t * t; }

    private static boolean inside(int x, int rel, int z) {
        double t = along(z, rel);
        if (Math.abs(t) >= 1) return false;
        double keel = keel(t);
        if (rel < keel || rel > 2 + lift(t)) return false;
        // A wineglass below the water, full at the wale, leaning back in above it.
        double width = beam(t) * (rel >= -3 ? 1 - .022 * (rel + 3) : Math.pow((rel - keel) / (-3 - keel) + .05, .6));
        return Math.abs(x - X) <= width;
    }

    private static int edge(int z, int rel) {
        int edge = 0;
        while (inside(X + edge + 1, rel, z)) edge++;
        return edge;
    }

    /** True if a spot on the main deck is inside the rails. */
    static boolean onDeck(int x, int z) { return Math.abs(x - X) < edge(z, 1); }

    static void build(BuildCanvas b) {
        hull(b);
        foredeck(b);
        cabins(b);
        masts(b);
        fittings(b);
    }

    private static void hull(BuildCanvas b) {
        for (int z = Z - LONG - 6; z <= Z + LONG + 8; z++) for (int rel = -14; rel <= 8; rel++) for (int x = X - WIDE - 2; x <= X + WIDE + 2; x++) {
            if (!inside(x, rel, z)) continue;
            int y = DECK + rel;
            boolean side = !inside(x + 1, rel, z) || !inside(x - 1, rel, z) || !inside(x, rel, z + 1) || !inside(x, rel, z - 1);
            if (rel == 0 && !side) {
                if (Math.floorMod(x, 2) == 0) b.block(x, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=z");
                else b.block(x, y, z, Material.SPRUCE_PLANKS);
            } else if (side || !inside(x, rel - 1, z)) {
                skin(b, x, y, z, rel);
            }
        }
        // The rail: a planked bulwark and a fence on it, open at the gangway.
        for (int z = Z - LONG; z <= FOREDECK; z++) for (int side : new int[]{-1, 1}) {
            int edge = edge(z, 1);
            if (edge <= 0) continue;
            boolean gangway = side < 0 && z >= 33 && z <= 35;
            if (gangway) {
                b.block(X - edge, DECK + 1, z, Material.AIR);
                b.block(X - edge, DECK + 2, z, Material.AIR);
            } else {
                b.block(X + side * edge, DECK + 2, z, RAIL);
            }
        }
        for (int z = 33; z <= 35; z++) for (int x = X - WIDE - 1; x <= X - 10; x++) for (int y = DECK + 1; y <= DECK + 3; y++) b.block(x, y, z, Material.AIR);
        // The stem, gilded at its head, the rudder, and a long bowsprit raked up off the bow.
        for (int rel = -8; rel <= 6; rel++) {
            int z = (int) Math.round(Z + LONG + rel * .5);
            b.block(X, DECK + rel, z, rel >= 3 ? Material.GOLD_BLOCK : Material.DARK_OAK_LOG, rel >= 3 ? "" : "axis=y");
        }
        for (int out = 0; out <= 17; out++) {
            int y = DECK + 6 + out * 2 / 3, z = Z + LONG - 3 + out;
            b.block(X, y, z, POST, "axis=z");
            b.block(X, y + 1, z, POST, "axis=z");
        }
        for (int rel = -9; rel <= 1; rel++) for (int back = 0; back <= (rel < -2 ? 2 : 0); back++)
            b.block(X, DECK + rel, Z - LONG - back - 1 - (int) Math.round(rel * .3), back == 2 ? POST : Material.DARK_OAK_PLANKS, back == 2 ? "axis=y" : "");
    }

    /** Strakes that follow the sheer, dark at the waterline and lighter up the side, with a proud wale and lit ports. */
    private static void skin(BuildCanvas b, int x, int y, int z, int rel) {
        double t = along(z, rel);
        int band = rel - (int) Math.round(lift(t));
        if (band >= 1) b.block(x, y, z, Material.SPRUCE_PLANKS);
        else if (band == 0) b.block(x, y, z, POST, "axis=z");
        else if (band == -1) b.block(x, y, z, Material.STRIPPED_OAK_WOOD);
        else if (band == -2) b.block(x, y, z, Material.WAXED_OXIDIZED_CUT_COPPER);
        else if (band == -3) b.block(x, y, z, POST, "axis=z");
        else if (band == -4) b.block(x, y, z, Math.floorMod(z, 5) == 2 && Math.abs(t) < .7 ? Material.SHROOMLIGHT : Material.SPRUCE_PLANKS);
        else if (band == -5) b.block(x, y, z, Material.SPRUCE_PLANKS);
        else b.block(x, y, z, Material.DARK_OAK_PLANKS);
    }

    private static void foredeck(BuildCanvas b) {
        for (int z = FOREDECK; z <= Z + LONG + 2; z++) {
            int width = edge(z, 2);
            if (width <= 0) continue;
            for (int x = X - width; x <= X + width; x++) {
                boolean rim = Math.abs(x - X) == width;
                b.block(x, DECK + 3, z, rim ? POST : Material.SPRUCE_PLANKS, rim ? "axis=z" : "");
                for (int y = DECK + 1; y <= DECK + 2; y++) b.block(x, y, z, z == FOREDECK || rim ? Material.DARK_OAK_PLANKS : Material.AIR);
            }
            for (int side : new int[]{-1, 1}) b.block(X + side * width, DECK + 4, z, RAIL);
            if (z == FOREDECK) for (int x = X - width; x <= X + width; x++) if (Math.abs(x - X) > 1) b.block(x, DECK + 4, z, RAIL);
        }
        for (int x = X - 1; x <= X + 1; x++) {
            for (int step = 0; step < 3; step++) b.block(x, DECK + 1 + step, FOREDECK - 3 + step + 1 - 1, Material.SPRUCE_STAIRS, "facing=south,half=bottom,shape=straight");
            for (int y = DECK + 1; y <= DECK + 3; y++) b.block(x, y, FOREDECK, y == DECK + 3 ? Material.SPRUCE_PLANKS : Material.AIR);
            b.block(x, DECK + 3, FOREDECK, Material.SPRUCE_STAIRS, "facing=south,half=bottom,shape=straight");
        }
    }

    /** One storey of the stern pagoda: posts proud of the wall, lit panels between them, a band of dark timber top and bottom. */
    private static void storey(BuildCanvas b, int y, int high, int halfX, int z1, int z2) {
        for (int x = X - halfX; x <= X + halfX; x++) for (int z = z1; z <= z2; z++) {
            boolean wall = Math.abs(x - X) == halfX || z == z1 || z == z2;
            for (int up = 0; up < high; up++) {
                if (!wall) {
                    b.block(x, y + up, z, up == 0 ? Material.MAGMA_BLOCK : Material.AIR);
                    continue;
                }
                int run = z == z1 || z == z2 ? x - X : z;
                boolean post = Math.floorMod(run, 3) == 0, band = up == 0 || up == high - 1;
                b.block(x, y + up, z, post ? POST : band ? Material.DARK_OAK_PLANKS : up % 2 == 1 ? Material.ORANGE_STAINED_GLASS : Material.SHROOMLIGHT, post ? "axis=y" : "");
                // Posts stand a block proud, and each panel gets a sill.
                if (post) {
                    int ox = Math.abs(x - X) == halfX ? Integer.signum(x - X) : 0, oz = ox != 0 ? 0 : z == z1 ? -1 : 1;
                    b.block(x + ox, y + up, z + oz, Material.DARK_OAK_LOG, "axis=y");
                }
            }
        }
    }

    /** A hipped roof that sweeps up at its corners, tiled in copper of every age, with rafters under the eaves. */
    private static void roof(BuildCanvas b, int y, int halfX, int z1, int z2, int salt) {
        int rise = 0, x1 = X - halfX - 3, x2 = X + halfX + 3, n = z1 - 3, s = z2 + 3;
        for (int x = x1 + 1; x < x2; x += 2) for (int z : new int[]{n, s}) b.block(x, y - 1, z + (z == n ? 1 : -1), Material.DARK_OAK_STAIRS, "facing=" + (z == n ? "south" : "north") + ",half=top,shape=straight");
        for (int z = n + 1; z < s; z += 2) for (int x : new int[]{x1, x2}) b.block(x + (x == x1 ? 1 : -1), y - 1, z, Material.DARK_OAK_STAIRS, "facing=" + (x == x1 ? "east" : "west") + ",half=top,shape=straight");
        while (x1 < x2 && n < s) {
            for (int x = x1; x <= x2; x++) for (int z = n; z <= s; z++) {
                boolean westEdge = x == x1, eastEdge = x == x2, northEdge = z == n, southEdge = z == s;
                if (!westEdge && !eastEdge && !northEdge && !southEdge) {
                    if (x1 + 1 >= x2 - 1 || n + 1 >= s - 1) b.block(x, y + rise, z, Material.DARK_OAK_PLANKS);
                    continue;
                }
                // Green with age, and a darker blue-grey where sheets were replaced, kept close so it doesn't shout over the hull.
                double age = SampleKit.smooth(x * 2, z * 2 + rise, 5, salt);
                Material[] tile = TILES[age < .4 ? 1 : 0];
                String facing = northEdge ? "south" : southEdge ? "north" : westEdge ? "east" : "west";
                boolean corner = (westEdge || eastEdge) && (northEdge || southEdge);
                if (corner && rise == 0) {
                    // The eaves turn up at the corners.
                    b.block(x, y + 1, z, Material.WAXED_CUT_COPPER_SLAB, "type=bottom");
                    b.block(x, y, z, POST, "axis=y");
                    b.block(x, y + 2, z, Material.GOLD_BLOCK);
                } else if (corner) {
                    b.block(x, y + rise, z, tile[1]);
                } else {
                    b.block(x, y + rise, z, tile[0], "facing=" + facing + ",half=bottom,shape=straight");
                }
            }
            x1++;
            x2--;
            n++;
            s--;
            if ((x2 - x1) % 2 == 0 || rise == 0) rise++;
            if (rise > 8) break;
        }
    }

    private static void cabins(BuildCanvas b) {
        storey(b, DECK + 1, 7, 8, 14, 30);
        roof(b, DECK + 8, 8, 14, 30, 711);
        storey(b, DECK + 11, 6, 5, 17, 27);
        roof(b, DECK + 17, 5, 17, 27, 712);
        storey(b, DECK + 20, 5, 3, 19, 25);
        roof(b, DECK + 25, 3, 19, 25, 713);
        for (int up = 0; up <= 5; up++) b.block(X, DECK + 29 + up, 22, up < 4 ? Material.DARK_OAK_FENCE : Material.GOLD_BLOCK);
        // The forge door: a mouth of fire behind a grate, facing down the deck.
        for (int x = X - 3; x <= X + 3; x++) for (int y = DECK + 1; y <= DECK + 5; y++) {
            b.block(x, y, 30, Material.MAGMA_BLOCK);
            b.block(x, y, 31, Material.IRON_BARS, "east=true,west=true");
        }
        for (int x = X - 4; x <= X + 4; x++) b.block(x, DECK + 6, 31, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int y = DECK + 1; y <= DECK + 5; y++) for (int x : new int[]{X - 4, X + 4}) b.block(x, y, 31, Material.POLISHED_BLACKSTONE_BRICKS);
    }

    /** A raked mast with two yards, the sails furled in bundles along them, and a fighting top. */
    private static void mast(BuildCanvas b, int z, int top) {
        for (int y = DECK + 1; y <= top; y++) {
            int lean = (y - DECK) / 8;
            b.block(X, y, z - lean, Material.STRIPPED_SPRUCE_LOG, "axis=y");
            if (y < top - 12) for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (y <= DECK + 4) b.block(X + side[0], y, z - lean + side[1], Material.DARK_OAK_PLANKS);
        }
        int topLean = (top - DECK) / 8;
        for (int[] yard : new int[][]{{top - 15, 14}, {top - 4, 9}}) {
            int y = yard[0], reach = yard[1], lean = (y - DECK) / 8;
            for (int dx = -reach; dx <= reach; dx++) {
                b.block(X + dx, y, z - lean, POST, "axis=x");
                double t = Math.abs(dx) / (double) reach;
                int belly = (int) Math.round(2 * (1 - t * t)), droop = 1 + (int) Math.round(2.5 * (1 - t * t));
                for (int down = 1; down <= droop; down++) for (int dz = 0; dz <= belly; dz++) b.block(X + dx, y - down, z - lean + 1 + dz - (down == droop ? 0 : 1), down == droop ? Material.LIGHT_GRAY_WOOL : Material.WHITE_WOOL);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) + Math.abs(dz) > 3) continue;
            b.block(X + dx, top - 8, z - topLean + dz, Material.SPRUCE_PLANKS);
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2) b.block(X + dx, top - 7, z - topLean + dz, RAIL);
        }
        for (int out = 1; out <= 7; out++) b.block(X + out, top + 1 - out / 3, z - topLean, out % 3 == 0 ? Material.YELLOW_WOOL : Material.CYAN_WOOL);
    }

    /** Rigging droops: a line between two points, sagging at the middle. */
    private static void rope(BuildCanvas b, int x1, int y1, int z1, int x2, int y2, int z2, double sag) {
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t), y = (int) Math.round(y1 + (y2 - y1) * t - Math.sin(t * Math.PI) * sag), z = (int) Math.round(z1 + (z2 - z1) * t);
            if (CanopyKit.empty(b, x, y, z)) b.block(x, y, z, RAIL);
        }
    }

    private static void masts(BuildCanvas b) {
        mast(b, 42, DECK + 36);
        mast(b, 58, DECK + 30);
        for (int side : new int[]{-1, 1}) {
            for (int fan = -1; fan <= 1; fan++) {
                rope(b, X + side, DECK + 28, 38, X + side * (edge(40 + fan * 3, 1) - 1), DECK + 3, 40 + fan * 3, .6);
                rope(b, X + side, DECK + 22, 55, X + side * (edge(56 + fan * 3, 1) - 1), DECK + 3, 56 + fan * 3, .6);
            }
        }
        // Stays, fore and aft.
        rope(b, X, DECK + 34, 38, X, DECK + 28, 55, 1.5);
        rope(b, X, DECK + 28, 55, X, DECK + 16, Z + LONG + 13, 2);
        rope(b, X, DECK + 34, 38, X, DECK + 31, 22, 1);
        // The jib, lashed down along the forestay in a long uneven bundle.
        for (int z = 62; z <= Z + LONG + 8; z++) {
            double t = (z - 62) / (double) (Z + LONG + 8 - 62);
            int y = (int) Math.round(DECK + 26 - t * 9 - Math.sin(t * Math.PI) * 2) - 1;
            int depth = 1 + (int) Math.round(Math.sin(t * Math.PI) * 2);
            boolean lashing = Math.floorMod(z, 6) == 0;
            for (int down = 0; down < depth; down++) b.block(X, y - down, z, lashing || down == depth - 1 ? Material.LIGHT_GRAY_WOOL : Material.WHITE_WOOL);
        }
    }

    /** Ore skips on chains over the deck, coal bunkers, a wheel, and lamps down both rails. */
    private static void fittings(BuildCanvas b) {
        for (int[] skip : new int[][]{{228, 44}, {244, 44}, {228, 56}, {244, 56}}) {
            for (int y = DECK + 11; y <= 56; y++) if (CanopyKit.empty(b, skip[0], y, skip[1])) b.block(skip[0], y, skip[1], Material.IRON_CHAIN, "axis=y");
            b.block(skip[0], DECK + 10, skip[1], Material.RAW_IRON_BLOCK);
        }
        for (int[] cask : new int[][]{{227, 32}, {228, 32}, {227, 31}, {245, 32}, {244, 32}, {245, 31}}) b.block(cask[0], DECK + 1, cask[1], Material.BARREL, "facing=up");
        for (int z = Z - LONG + 4; z <= FOREDECK - 2; z += 6) for (int side : new int[]{-1, 1}) {
            int x = X + side * edge(z, 1);
            if (z >= 31 && z <= 37 && side < 0) continue;
            b.block(x, DECK + 3, z, RAIL);
            b.block(x, DECK + 4, z, Material.LANTERN, "hanging=false");
        }
        for (int x = 228; x <= 244; x++) for (int z : new int[]{38, 50}) if (Math.floorMod(x, 4) == 0 && onDeck(x, z)) b.block(x, DECK, z, Material.IRON_TRAPDOOR, "half=top,open=false,facing=north");
    }
}
