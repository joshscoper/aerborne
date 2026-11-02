package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;
import static net.nerdsmp.paper.expedition.WardensTemplate.*;

/**
 * The three wings, each the same walk out from the hall: a bridge, a court with a door to open, and a second bridge to
 * the Warden's arena, which {@link WardensArenas} builds. They're all written along a turned plan, with {@code f} counting
 * out from the middle of the hall.
 */
final class WardensWings {
    /** A wing's stuff: {@code floor} and {@code alt} pave it, {@code wall} stands up from it, {@code trim} marks its edges. */
    private record Theme(Isle isle, Material floor, Material alt, Material wall, Material trim, Material vein, Material light) { }

    private static final Isle EMBER_ISLE = new Isle(Material.BLACKSTONE, Material.BASALT,
        new Material[]{Material.BLACKSTONE, Material.BASALT, Material.MAGMA_BLOCK, Material.BLACKSTONE, Material.DEEPSLATE});
    private static final Isle FROST_ISLE = new Isle(Material.SNOW_BLOCK, Material.PACKED_ICE,
        new Material[]{Material.PACKED_ICE, Material.STONE, Material.BLUE_ICE, Material.DIORITE, Material.PACKED_ICE});
    private static final Isle STONE_ISLE = new Isle(Material.MOSS_BLOCK, Material.DIRT,
        new Material[]{Material.STONE, Material.ANDESITE, Material.MOSSY_COBBLESTONE, Material.COBBLESTONE});
    private static final Theme EMBER = new Theme(EMBER_ISLE, Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE, Material.BLACKSTONE, Material.GOLD_BLOCK, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
    private static final Theme FROST = new Theme(FROST_ISLE, Material.POLISHED_DIORITE, Material.CALCITE, Material.DIORITE, Material.BLUE_ICE, Material.PACKED_ICE, Material.SEA_LANTERN);
    private static final Theme STONE = new Theme(STONE_ISLE, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.MOSS_BLOCK, Material.OCHRE_FROGLIGHT);
    private static final int WIDE = 3;

    private WardensWings() { }

    static void build(BuildCanvas b) {
        Carving west = new Carving(b, 0, Y, HALL_Z, -1, 0), east = new Carving(b, 0, Y, HALL_Z, 1, 0), south = new Carving(b, 0, Y, HALL_Z, 0, 1);
        wing(b, west, EMBER);
        wing(b, east, FROST);
        wing(b, south, STONE);
        emberCourt(b, west);
        frostCourt(b, east);
        stoneCourt(b, south);
        WardensArenas.ember(b, west);
        WardensArenas.frost(b, east);
        WardensArenas.stone(b, south);
    }

    private static void wing(BuildCanvas b, Carving c, Theme theme) {
        span(c, WardensHall.DRUM + 2, COURT - COURT_RADIUS + 2, theme);
        span(c, COURT + COURT_RADIUS - 2, ARENA - ARENA_RADIUS + 2, theme);
        round(b, c, COURT, COURT_RADIUS, 20, theme);
        gateway(c, COURT - COURT_RADIUS, theme);
        gateway(c, COURT + COURT_RADIUS, theme);
        gateway(c, ARENA - ARENA_RADIUS, theme);
        // What holds the far gate shut until the court is done.
        String bars = c.dirZ() == 0 ? "north=true,south=true" : "east=true,west=true";
        for (int a = -2; a <= 2; a++) for (int h = 1; h <= 7; h++) c.set(a, h, COURT + COURT_RADIUS, Material.IRON_BARS, bars);
    }

    /** A bridge out over the drop, thin at the crown of its arch and deep at each end. */
    private static void span(Carving c, int f1, int f2, Theme theme) {
        for (int f = f1; f <= f2; f++) {
            double along = (f - f1) / (double) (f2 - f1);
            int deep = 2 + (int) Math.round(Math.pow(Math.abs(along - .5) * 2, 2.4) * 11);
            for (int a = -WIDE - 1; a <= WIDE + 1; a++) {
                int rail = Math.abs(a) == WIDE + 1 ? 1 : 0;
                c.set(a, 0, f, rail == 1 ? theme.wall() : a == 0 ? theme.trim() : Math.floorMod(f, 2) == 0 ? theme.floor() : theme.alt());
                for (int h = 1; h <= deep; h++) c.set(a, -h, f, theme.wall());
                if (rail == 1) {
                    c.set(a, 1, f, theme.wall());
                    c.set(a, 2, f, Math.floorMod(f, 6) == 0 ? theme.trim() : theme.floor());
                    if (Math.floorMod(f, 12) == 0) {
                        c.set(a, 3, f, theme.trim());
                        c.set(a, 4, f, theme.light());
                    }
                }
            }
        }
    }

    /** A round island at {@code center}, paved in rings, with a wall round it broken only for the wing. */
    private static void round(BuildCanvas b, Carving c, int center, int radius, int salt, Theme theme) {
        island(b, c.wx(0, center), Y, c.wz(0, center), radius + 2, radius + 12, theme.isle());
        SkyRuins.hang(b, c.wx(0, center), Y, c.wz(0, center), radius + 2, radius + 12);
        for (int a = -radius - 2; a <= radius + 2; a++) for (int f = center - radius - 2; f <= center + radius + 2; f++) {
            double r = Math.hypot(a, f - center), angle = Math.atan2(f - center, a);
            if (r > radius + 1) continue;
            double vein = smooth(c.wx(a, f), c.wz(a, f), 7, 630 + salt);
            Material block = Math.abs(r - radius * .45) < .7 || Math.abs(r - radius * .85) < .7 ? theme.trim()
                : Math.abs(vein - .5) < .009 ? theme.vein()
                : Math.floorMod((int) (r * .34) + (int) Math.floor(angle * r * .12), 2) == 0 ? theme.floor() : theme.alt();
            c.set(a, 0, f, block);
            if (Math.abs(r - radius) > .75 || Math.abs(a) <= WIDE + 1) continue;
            c.set(a, 1, f, theme.wall());
            c.set(a, 2, f, Math.floorMod(a + f * 3, 5) == 0 ? theme.wall() : theme.floor());
        }
        int posts = radius * 2 / 5;
        for (int index = 0; index < posts; index++) {
            double angle = index * Math.TAU / posts + .13;
            int a = (int) Math.round(Math.cos(angle) * radius), f = center + (int) Math.round(Math.sin(angle) * radius);
            if (Math.abs(a) <= WIDE + 2) continue;
            c.box(a, a, 1, 3, f, f, theme.wall());
            c.set(a, 4, f, theme.trim());
            c.set(a, 5, f, theme.light());
        }
    }

    /** Two piers and a lintel over the walk, with room for bars in the opening. */
    private static void gateway(Carving c, int f, Theme theme) {
        for (int a = WIDE; a <= WIDE + 3; a++) for (int h = 1; h <= 13; h++) for (int d = -1; d <= 1; d++) {
            boolean top = h >= 12;
            c.set(a, h, f + d, top ? theme.trim() : a == WIDE + 3 || d != 0 ? theme.wall() : theme.floor());
            c.set(-a, h, f + d, top ? theme.trim() : a == WIDE + 3 || d != 0 ? theme.wall() : theme.floor());
        }
        for (int a = -WIDE; a <= WIDE; a++) for (int h = 8; h <= 13; h++) for (int d = -1; d <= 1; d++) {
            boolean arch = h == 8 && Math.abs(a) < 3;
            if (!arch) c.set(a, h, f + d, h >= 12 ? theme.trim() : theme.wall());
        }
        for (int a = -2; a <= 2; a++) c.set(a, 14, f, theme.light());
    }

    private static void column(Carving c, int a, int f, int high, Material shaft, Material band, Material head) {
        for (int h = 1; h <= high; h++) {
            int reach = h <= 2 || h >= high - 1 ? 2 : 1;
            c.box(a - reach, a + reach, h, h, f - reach, f + reach, h <= 2 || h >= high - 1 ? band : Math.floorMod(h, 6) == 0 ? band : shaft);
        }
        c.set(a, high + 1, f, head);
    }

    /** A wide stone bowl with fire in it. Light the campfires by hand or by ritual; the coals stay red either way. */
    static void brazier(Carving c, int a, int f, Material stem, Material bowl, boolean lit) {
        c.box(a - 1, a + 1, 1, 1, f - 1, f + 1, stem);
        c.box(a, a, 2, 3, f, f, stem);
        for (int da = -3; da <= 3; da++) for (int df = -3; df <= 3; df++) {
            double r = Math.hypot(da, df);
            if (r > 3.3) continue;
            c.set(a + da, 4, f + df, r > 2.3 ? bowl : Material.MAGMA_BLOCK);
            if (r > 2.3) c.set(a + da, 3, f + df, bowl);
            if (r > 2.3) c.set(a + da, 5, f + df, bowl);
        }
        c.set(a, 5, f, Material.CAMPFIRE, "lit=" + lit + ",signal_fire=false,facing=north");
    }

    // ---------------------------------------------------------------------------------------------
    // West: fire
    // ---------------------------------------------------------------------------------------------

    private static void emberCourt(BuildCanvas b, Carving c) {
        for (int index = 0; index < 12; index++) {
            double angle = (index + .5) * Math.TAU / 12;
            int a = (int) Math.round(Math.cos(angle) * 17), f = COURT + (int) Math.round(Math.sin(angle) * 17);
            if (Math.abs(a) < 7) continue;
            column(c, a, f, 12 + index % 3 * 2, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.GOLD_BLOCK);
        }
        // Four pedestals: the court's door opens when all four are lit by arrows.
        for (int[] spot : new int[][]{{-9, -8}, {9, -8}, {-9, 8}, {9, 8}}) {
            c.box(spot[0], spot[0], 1, 1, COURT + spot[1], COURT + spot[1], Material.POLISHED_BLACKSTONE_BRICKS);
            c.set(spot[0], 2, COURT + spot[1], Material.TARGET);
            c.set(spot[0], 3, COURT + spot[1], Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        }
        c.set(5, 1, COURT - 12, Material.BARREL, "facing=up");
        c.set(13, 1, COURT + 4, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    // ---------------------------------------------------------------------------------------------
    // East: ice
    // ---------------------------------------------------------------------------------------------

    private static void frostCourt(BuildCanvas b, Carving c) {
        // The stacks of the archive, frozen where they stood.
        for (int[] stack : new int[][]{{-15, -8}, {-15, 8}, {15, -8}, {15, 8}, {-7, 12}, {7, 12}, {-7, -12}, {7, -12}}) {
            boolean sideways = Math.abs(stack[0]) > 10;
            int da = sideways ? 0 : 3, df = sideways ? 3 : 0;
            for (int s = -1; s <= 1; s++) for (int h = 1; h <= 6; h++) for (int d = 0; d <= 1; d++) {
                int a = stack[0] + da * s + (sideways ? d : 0), f = COURT + stack[1] + df * s + (sideways ? 0 : d);
                for (int run = -1; run <= 1; run++) c.set(a + (sideways ? 0 : run), h, f + (sideways ? run : 0), h == 6 ? Material.SNOW_BLOCK : noise(a * 5 + h, f, 661) < .3 ? Material.PACKED_ICE : Material.BOOKSHELF);
            }
        }
        // Four chimes on their pillars.
        for (int[] chime : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            for (int h = 1; h <= 4; h++) c.set(chime[0], h, COURT + chime[1], Material.PACKED_ICE);
            c.set(chime[0], 5, COURT + chime[1], Material.BLUE_ICE);
            int toward = chime[1] < 0 ? 1 : -1;
            c.set(chime[0], 1, COURT + chime[1] + toward, Material.PACKED_ICE);
            c.set(chime[0], 2, COURT + chime[1] + toward, Material.GOLD_BLOCK);
            c.set(chime[0], 3, COURT + chime[1] + toward, Material.STONE_BUTTON, "face=floor,facing=north,powered=false");
        }
        c.set(13, 1, COURT, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    // ---------------------------------------------------------------------------------------------
    // South: stone
    // ---------------------------------------------------------------------------------------------

    private static void stoneCourt(BuildCanvas b, Carving c) {
        // Four thrones at the corners, each waiting for its king.
        for (int[] seat : new int[][]{{-13, -9}, {13, -9}, {-13, 9}, {13, 9}}) throne(c, seat[0], COURT + seat[1], seat[0] < 0 ? 1 : -1);
        for (int[] plate : new int[][]{{-6, -4}, {6, -4}, {0, 6}}) {
            c.box(plate[0] - 1, plate[0] + 1, 1, 1, COURT + plate[1] - 1, COURT + plate[1] + 1, Material.STONE_BRICKS);
            c.set(plate[0], 2, COURT + plate[1], Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "power=0");
        }
        c.set(10, 1, COURT - 16, Material.BARREL, "facing=up");
        c.set(-13, 1, COURT, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    private static void throne(Carving c, int a, int f, int toward) {
        c.box(a - 2, a + 2, 1, 1, f - 2, f + 2, Material.CHISELED_STONE_BRICKS);
        c.box(a - 1, a + 1, 2, 2, f - 1, f + 1, Material.POLISHED_ANDESITE);
        c.box(a - 2 * toward, a - 2 * toward, 2, 8, f - 2, f + 2, Material.STONE_BRICKS);
        c.box(a - 2 * toward, a - 2 * toward, 9, 9, f - 1, f + 1, Material.GOLD_BLOCK);
        c.box(a - 1, a + 1, 3, 3, f + 2, f + 2, Material.MOSSY_STONE_BRICKS);
        c.box(a - 1, a + 1, 3, 3, f - 2, f - 2, Material.MOSSY_STONE_BRICKS);
    }
}
