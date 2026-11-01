package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;

/**
 * The Gloomforge: not a furnace with a hull round it any more, but a great tiered ship, the mine's whole wealth
 * built into one vessel at the bottom of the world. A broad hull with gilded strakes, a pagoda of cabins astern
 * under curved roofs of bright and weathered copper with the forge burning in its windows, two masts with their
 * sails furled to the yards, and a web of rigging. She lies along Z with her stern north; the deck is the Foreman's arena.
 */
final class GloomforgeShip {
    static final int X = 236, Z = 40, WIDE = 17, LONG = 26, DECK = MinesTemplate.DECK;
    private static final Material RAIL = CanopyKit.RAIL;
    private static final Material[][] TILES = {
        {Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Material.WAXED_OXIDIZED_CUT_COPPER},
        {Material.WAXED_WEATHERED_CUT_COPPER_STAIRS, Material.WAXED_WEATHERED_CUT_COPPER},
        {Material.WAXED_EXPOSED_CUT_COPPER_STAIRS, Material.WAXED_EXPOSED_CUT_COPPER},
        {Material.WAXED_CUT_COPPER_STAIRS, Material.WAXED_CUT_COPPER}};

    private GloomforgeShip() { }

    /** Half her width at a point along the keel: full astern, narrowing to a sharp bow in the south. */
    private static double beam(int z) {
        double t = (z - Z) / (double) LONG;
        if (Math.abs(t) >= 1) return 0;
        double round = Math.pow(1 - Math.pow(Math.abs(t), 2.6), 1 / 2.2);
        return WIDE * round * (t > 0 ? 1 - .45 * t * t : 1);
    }

    static double edge(int x, int z) {
        double beam = beam(z);
        return beam <= 0 ? 2 : Math.abs(x - X) / beam;
    }

    static void build(BuildCanvas b) {
        hull(b);
        cabins(b);
        masts(b);
        fittings(b);
    }

    private static void hull(BuildCanvas b) {
        for (int z = Z - LONG; z <= Z + LONG; z++) {
            int beam = (int) Math.floor(beam(z));
            if (beam <= 0) continue;
            for (int x = X - beam; x <= X + beam; x++) {
                boolean rim = Math.abs(x - X) == beam || (int) Math.floor(beam(z + 1)) < Math.abs(x - X) || (int) Math.floor(beam(z - 1)) < Math.abs(x - X);
                if (!rim) {
                    if (Math.floorMod(x, 2) == 0) b.block(x, DECK, z, Material.STRIPPED_SPRUCE_LOG, "axis=z");
                    else b.block(x, DECK, z, Material.SPRUCE_PLANKS);
                    continue;
                }
                b.block(x, DECK, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
                for (int y = 2; y < DECK; y++) {
                    Material plank = y == DECK - 1 ? Material.STRIPPED_OAK_WOOD : y == DECK - 2 ? Material.DARK_OAK_PLANKS : y == DECK - 3 ? Material.WAXED_OXIDIZED_CUT_COPPER
                        : y == DECK - 4 ? (Math.floorMod(x + z, 5) == 0 ? Material.SHROOMLIGHT : Material.SPRUCE_PLANKS) : y >= DECK - 6 ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
                    b.block(x, y, z, plank);
                }
                boolean gangway = x < X && z >= 33 && z <= 35;
                if (!gangway) {
                    b.block(x, DECK + 1, z, Material.SPRUCE_PLANKS);
                    b.block(x, DECK + 2, z, RAIL);
                }
            }
        }
        // A gilded prow and a long bowsprit.
        for (int y = DECK - 6; y <= DECK + 3; y++) {
            int z = Z + LONG + (y - DECK + 6) / 3;
            b.block(X, y, z, y >= DECK ? Material.GOLD_BLOCK : Material.DARK_OAK_LOG, y >= DECK ? "" : "axis=y");
        }
        for (int out = 0; out <= 16; out++) {
            int y = DECK + 3 + out * 2 / 3, z = Z + LONG + out;
            b.block(X, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
            b.block(X, y + 1, z, Material.STRIPPED_DARK_OAK_LOG, "axis=z");
        }
    }

    /** One storey of the stern pagoda: posts, lit panels with the forge behind them, and a band of dark timber. */
    private static void storey(BuildCanvas b, int y, int high, int halfX, int z1, int z2) {
        for (int x = X - halfX; x <= X + halfX; x++) for (int z = z1; z <= z2; z++) {
            boolean wall = Math.abs(x - X) == halfX || z == z1 || z == z2;
            for (int up = 0; up < high; up++) {
                if (!wall) {
                    b.block(x, y + up, z, up == 0 ? Material.MAGMA_BLOCK : Material.AIR);
                    continue;
                }
                int run = z == z1 || z == z2 ? x - X : z;
                boolean post = Math.floorMod(run, 3) == 0;
                boolean band = up == 0 || up == high - 1;
                b.block(x, y + up, z, post ? Material.STRIPPED_DARK_OAK_LOG : band ? Material.DARK_OAK_PLANKS : up % 2 == 1 ? Material.ORANGE_STAINED_GLASS : Material.SHROOMLIGHT, post ? "axis=y" : "");
            }
        }
    }

    /** A hipped roof that sweeps up at its corners, tiled in copper of every age. */
    private static void roof(BuildCanvas b, int y, int halfX, int z1, int z2, int salt) {
        int rise = 0;
        int x1 = X - halfX - 3, x2 = X + halfX + 3, n = z1 - 3, s = z2 + 3;
        while (x1 < x2 && n < s) {
            for (int x = x1; x <= x2; x++) for (int z = n; z <= s; z++) {
                boolean westEdge = x == x1, eastEdge = x == x2, northEdge = z == n, southEdge = z == s;
                if (!westEdge && !eastEdge && !northEdge && !southEdge) { if (x1 + 1 >= x2 - 1 || n + 1 >= s - 1) b.block(x, y + rise, z, Material.DARK_OAK_PLANKS); continue; }
                // Mostly green with age, with brighter patches where the sheets were replaced.
                double age = SampleKit.smooth(x * 2, z * 2 + rise, 5, salt);
                Material[] tile = TILES[age < .42 ? 3 : age < .5 ? 2 : age < .56 ? 1 : 0];
                String facing = northEdge ? "south" : southEdge ? "north" : westEdge ? "east" : "west";
                boolean corner = (westEdge || eastEdge) && (northEdge || southEdge);
                if (corner && rise == 0) {
                    // The eaves turn up at the corners.
                    b.block(x, y + 1, z, Material.WAXED_CUT_COPPER_SLAB, "type=bottom");
                    b.block(x, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
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
        storey(b, DECK + 1, 7, 9, 16, 30);
        roof(b, DECK + 8, 9, 16, 30, 711);
        storey(b, DECK + 11, 6, 6, 18, 28);
        roof(b, DECK + 17, 6, 18, 28, 712);
        storey(b, DECK + 20, 5, 3, 20, 26);
        roof(b, DECK + 25, 3, 20, 26, 713);
        for (int up = 0; up <= 5; up++) b.block(X, DECK + 29 + up, 23, up < 4 ? Material.DARK_OAK_FENCE : Material.GOLD_BLOCK);
        // The forge door: a mouth of fire behind a grate, facing down the deck.
        for (int x = X - 3; x <= X + 3; x++) for (int y = DECK + 1; y <= DECK + 5; y++) {
            b.block(x, y, 30, Material.MAGMA_BLOCK);
            b.block(x, y, 31, Material.IRON_BARS, "east=true,west=true");
        }
        for (int x = X - 4; x <= X + 4; x++) b.block(x, DECK + 6, 31, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int y = DECK + 1; y <= DECK + 5; y++) for (int x : new int[]{X - 4, X + 4}) b.block(x, y, 31, Material.POLISHED_BLACKSTONE_BRICKS);
    }

    /** A mast with two yards, the sails bellied and furled white against the dark. */
    private static void mast(BuildCanvas b, int z, int top) {
        for (int y = DECK + 1; y <= top; y++) b.block(X, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=y");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (Math.abs(dx) + Math.abs(dz) == 1) for (int y = DECK + 1; y <= DECK + 3; y++) b.block(X + dx, y, z + dz, Material.DARK_OAK_PLANKS);
        for (int[] yard : new int[][]{{top - 14, 13}, {top - 3, 9}}) {
            int y = yard[0], reach = yard[1];
            for (int dx = -reach; dx <= reach; dx++) {
                b.block(X + dx, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
                double t = Math.abs(dx) / (double) reach;
                int belly = (int) Math.round(3 * (1 - t * t)), droop = (int) Math.round(2 + 3 * (1 - t * t));
                for (int down = 1; down <= droop; down++) b.block(X + dx, y - down, z + (int) Math.round(belly * Math.sin(Math.PI * down / (droop + 1))), down == droop && noise(dx, z, 714) < .5 ? Material.LIGHT_GRAY_WOOL : Material.WHITE_WOOL);
            }
        }
        // A pennant at the masthead, and the crow's nest under it.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) + Math.abs(dz) > 3) continue;
            b.block(X + dx, top - 6, z + dz, Material.SPRUCE_PLANKS);
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2) b.block(X + dx, top - 5, z + dz, RAIL);
        }
        for (int out = 1; out <= 7; out++) b.block(X + out, top + 1 - out / 3, z, out % 3 == 0 ? Material.YELLOW_WOOL : Material.CYAN_WOOL);
    }

    private static void masts(BuildCanvas b) {
        mast(b, 40, DECK + 29);
        mast(b, 58, DECK + 24);
        // Rigging from the mastheads down to the rails, a bead at a time.
        for (int side : new int[]{-1, 1}) {
            MineProps.line(b, X + side * 12, DECK + 15, 40, X + side * (int) Math.floor(beam(44)), DECK + 3, 44, RAIL, "");
            MineProps.line(b, X + side * 12, DECK + 15, 40, X + side * (int) Math.floor(beam(36)), DECK + 3, 36, RAIL, "");
            MineProps.line(b, X + side * 12, DECK + 10, 58, X + side * (int) Math.floor(beam(61)), DECK + 3, 61, RAIL, "");
        }
        MineProps.line(b, X, DECK + 27, 40, X, DECK + 22, 58, RAIL, "");
        MineProps.line(b, X, DECK + 22, 58, X, DECK + 13, Z + LONG + 16, RAIL, "");
        MineProps.line(b, X, DECK + 27, 40, X, DECK + 30, 30, RAIL, "");
    }

    /** Ore skips on the chains over the deck, coal bunkers, and lamps down both rails. */
    private static void fittings(BuildCanvas b) {
        for (int[] skip : new int[][]{{228, 44}, {244, 44}, {228, 56}, {244, 56}}) {
            for (int y = DECK + 11; y <= 47; y++) if (CanopyKit.empty(b, skip[0], y, skip[1])) b.block(skip[0], y, skip[1], Material.IRON_CHAIN, "axis=y");
            b.block(skip[0], DECK + 10, skip[1], Material.RAW_IRON_BLOCK);
        }
        for (int[] crate : new int[][]{{225, 33}, {226, 33}, {225, 34}, {247, 33}, {246, 33}, {247, 34}}) b.block(crate[0], DECK + 1, crate[1], Material.BARREL, "facing=up");
        for (int z = Z - LONG + 3; z <= Z + LONG - 4; z += 5) for (int side : new int[]{-1, 1}) {
            int x = X + side * (int) Math.floor(beam(z));
            if (z >= 32 && z <= 36 && side < 0) continue;
            b.block(x, DECK + 3, z, RAIL);
            b.block(x, DECK + 4, z, Material.LANTERN, "hanging=false");
        }
        for (int x = 226; x <= 246; x++) for (int z : new int[]{38, 50, 60}) if (Math.floorMod(x, 4) == 0 && edge(x, z) < .85) b.block(x, DECK, z, Material.IRON_TRAPDOOR, "half=top,open=false,facing=north");
    }
}
