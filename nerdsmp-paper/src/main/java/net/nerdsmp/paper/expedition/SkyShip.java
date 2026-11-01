package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;

/**
 * Captain Wren's airship, the <em>Swallow</em>: a deep wooden hull with its strakes sweeping up to the bow and
 * the stern, a two-tier sterncastle with a lit gallery, and over it a slim canvas gas bag in a wooden harness.
 * It lies along X with its bow east, and is moored by its south side. {@code (cx, deck, cz)} is the middle of the
 * main deck; {@code rel} below is height above that deck, {@code dx} and {@code dz} the offsets from the middle.
 */
final class SkyShip {
    static final Material RAIL = Material.DARK_OAK_FENCE;
    private static final int LENGTH = 27, FOREDECK = 14, AFT = -11, QUARTER = -19;
    /** The gas bag: its middle, half its length, and how far it reaches each way. */
    private static final int BAG_X = -2, BAG_Y = 21, BAG_LENGTH = 30;
    private static final double BAG_WIDE = 10, BAG_HIGH = 8.5;
    private static final int[] HOOPS = {-14, 10};
    private static final Material PLASTER = Material.SMOOTH_SANDSTONE;
    private static final Material POST = Material.STRIPPED_DARK_OAK_LOG;

    private final BuildCanvas b;
    private final int cx, deck, cz;

    private SkyShip(BuildCanvas b, int cx, int deck, int cz) {
        this.b = b;
        this.cx = cx;
        this.deck = deck;
        this.cz = cz;
    }

    static void moor(BuildCanvas b, int cx, int deck, int cz) {
        SkyShip ship = new SkyShip(b, cx, deck, cz);
        ship.hull();
        ship.forecastle();
        ship.bowsprit();
        ship.sterncastle();
        ship.fittings();
        ship.bag();
        ship.harness();
        ship.burner();
        ship.tail();
    }

    private void put(int dx, int rel, int dz, Material material) { b.block(cx + dx, deck + rel, cz + dz, material); }

    private void put(int dx, int rel, int dz, Material material, String state) { b.block(cx + dx, deck + rel, cz + dz, material, state); }

    private boolean clear(int dx, int rel, int dz) { return CanopyKit.empty(b, cx + dx, deck + rel, cz + dz); }

    /** Where a cell lies along the hull, -1 at the stern to 1 at the bow. Both ends rake out as they rise. */
    private static double along(int dx, int rel) {
        return dx >= 0 ? (dx - rel * .45) / LENGTH : (dx + rel * .35) / LENGTH;
    }

    private static double beam(double t) {
        double from = Math.abs(t);
        if (from >= 1) return 0;
        return t >= 0 ? 8.4 * Math.pow(1 - Math.pow(from, 2.3), .66) : Math.max(5.5, 8.4 * Math.pow(1 - Math.pow(from, 6), .5));
    }

    private static double keel(double t) {
        return t >= 0 ? -10 + 11 * Math.pow(t, 2.4) : -10 + 7 * Math.pow(-t, 3);
    }

    /** How far the strakes have climbed here, which is what gives the hull its sweep. */
    private static double lift(double t) { return t >= 0 ? 3 * t * t : 4.5 * t * t; }

    private static boolean inside(int dx, int rel, int dz) {
        double t = along(dx, rel);
        if (Math.abs(t) >= 1) return false;
        double keel = keel(t);
        if (rel < keel || rel > 1 + lift(t)) return false;
        double width = beam(t) * (rel >= 0 ? 1 - .025 * rel : Math.pow((rel - keel) / -keel + .08, .6));
        return Math.abs(dz) <= width;
    }

    /** The widest a deck can reach here, at this height. */
    private static int edge(int dx, int rel) {
        int edge = 0;
        while (inside(dx, rel, edge + 1)) edge++;
        return edge;
    }

    private void hull() {
        for (int dx = -LENGTH - 6; dx <= LENGTH + 7; dx++) for (int rel = -11; rel <= 7; rel++) for (int dz = -10; dz <= 10; dz++) {
            if (!inside(dx, rel, dz)) continue;
            boolean side = !inside(dx + 1, rel, dz) || !inside(dx - 1, rel, dz) || !inside(dx, rel, dz + 1) || !inside(dx, rel, dz - 1);
            boolean under = !inside(dx, rel - 1, dz);
            if (rel == 0 && !side) planks(dx, rel, dz);
            else if (side || under) skin(dx, rel, dz, under && !side);
        }
        // The rail, with a gap amidships for the gangway on the south side.
        for (int dx = AFT; dx < FOREDECK; dx++) {
            int edge = edge(dx, 1);
            put(dx, 2, -edge, RAIL);
            if (Math.abs(dx) <= 1) put(dx, 1, edge, Material.AIR);
            else put(dx, 2, edge, RAIL);
        }
    }

    /** Deck boards run the length of the ship. */
    private void planks(int dx, int rel, int dz) {
        if (Math.floorMod(dz, 2) == 0) put(dx, rel, dz, Material.STRIPPED_SPRUCE_LOG, "axis=x");
        else put(dx, rel, dz, Material.SPRUCE_PLANKS);
    }

    /** The hull's planking, in bands that follow the sweep: wale, teal strake, gunports, and dark planks below. */
    private void skin(int dx, int rel, int dz, boolean keelLine) {
        double t = along(dx, rel);
        int band = rel - (int) Math.round(lift(t));
        if (keelLine) put(dx, rel, dz, Material.DARK_OAK_LOG, "axis=x");
        else if (band >= 1) put(dx, rel, dz, Material.DARK_OAK_PLANKS);
        else if (band == 0) put(dx, rel, dz, POST, "axis=x");
        else if (band == -1) put(dx, rel, dz, Material.SPRUCE_PLANKS);
        else if (band == -2) put(dx, rel, dz, Math.floorMod(dx, 6) == 0 ? Material.SEA_LANTERN : Material.WAXED_OXIDIZED_CUT_COPPER);
        else if (band == -3) put(dx, rel, dz, Material.DARK_OAK_LOG, "axis=x");
        else if (band == -4) put(dx, rel, dz, Math.floorMod(dx, 5) == 2 && Math.abs(t) < .7 ? Material.SHROOMLIGHT : Material.DARK_OAK_PLANKS);
        else if (band == -5) put(dx, rel, dz, Material.SPRUCE_PLANKS);
        else if (band == -6) put(dx, rel, dz, Material.DARK_OAK_PLANKS);
        else if (band == -7) put(dx, rel, dz, Material.WAXED_WEATHERED_CUT_COPPER);
        else put(dx, rel, dz, noise(cx + dx, cz + dz + rel, 401) < .35 ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS);
    }

    /** The raised foredeck and the steps up to it. */
    private void forecastle() {
        for (int dx = FOREDECK; dx <= LENGTH + 2; dx++) {
            int width = (int) Math.floor(beam(along(dx, 2)));
            if (width <= 0) continue;
            for (int dz = -width; dz <= width; dz++) {
                if (Math.abs(dz) == width) put(dx, 2, dz, POST, "axis=x");
                else planks(dx, 2, dz);
                put(dx, 1, dz, dx == FOREDECK || Math.abs(dz) == width ? Material.DARK_OAK_PLANKS : Material.AIR);
            }
            for (int dz : new int[]{-width, width}) put(dx, 3, dz, RAIL);
            if (dx == FOREDECK) for (int dz = -width; dz <= width; dz++) if (Math.abs(dz) > 1) put(dx, 3, dz, RAIL);
        }
        for (int dz = -1; dz <= 1; dz++) {
            put(FOREDECK - 1, 1, dz, Material.SPRUCE_STAIRS, "facing=east,half=bottom,shape=straight");
            put(FOREDECK, 2, dz, Material.SPRUCE_STAIRS, "facing=east,half=bottom,shape=straight");
            put(FOREDECK, 1, dz, Material.SPRUCE_PLANKS);
        }
    }

    /** The bowsprit climbs out over the bow; a jib runs up from it toward the bag's nose. */
    private void bowsprit() {
        int foot = LENGTH - 6, tip = LENGTH + 8;
        for (int dx = foot; dx <= tip; dx++) {
            int rel = 4 + (dx - foot) / 2;
            put(dx, rel, 0, POST, "axis=x");
            put(dx, rel - 1, 0, POST, "axis=x");
            if (dx < foot + 3 || dx > tip - 1) continue;
            int top = rel + (int) Math.round((tip - dx) * .8);
            for (int up = rel + 1; up <= top; up++) put(dx, up, 0, up == top ? Material.DARK_OAK_PLANKS : up % 5 == 2 ? Material.RED_WOOL : PLASTER);
        }
        // The stem, gilded at its head, and a short red pennant off the tip.
        for (int rel = -4; rel <= 4; rel++) put((int) Math.round(LENGTH + rel * .45), rel, 0, rel >= 2 ? Material.GOLD_BLOCK : Material.DARK_OAK_LOG, rel >= 2 ? "" : "axis=y");
        for (int out = 1; out <= 4; out++) put(tip + out, 11 + (int) Math.round(Math.sin(out * .9)), 0, Material.RED_WOOL);
    }

    /** The two-tier sterncastle: the great cabin under the poop deck, a quarter cabin behind, and the stern gallery. */
    private void sterncastle() {
        int last = sternEdge(3);
        for (int dx = last; dx <= AFT; dx++) {
            int width = Math.min(8, (int) Math.floor(beam(along(dx, 3))));
            for (int dz = -width; dz <= width; dz++) {
                boolean wall = Math.abs(dz) == width || dx == AFT || dx == last;
                for (int rel = 1; rel <= 4; rel++) put(dx, rel, dz, wall ? cabinWall(dx, rel, dz, last) : Material.AIR);
                if (Math.abs(dz) == width) put(dx, 5, dz, POST, "axis=x");
                else if (dx == AFT) put(dx, 5, dz, POST, "axis=z");
                else planks(dx, 5, dz);
                if ((Math.abs(dz) == width || dx == AFT) && dx > QUARTER) put(dx, 6, dz, RAIL);
                // Light behind every pane, so the cabin glows from outside.
                if (!wall && (Math.abs(dz) == width - 1 || dx == AFT - 1) && behindPane(dx, dz)) for (int rel = 2; rel <= 3; rel++) put(dx, rel, dz, Material.SHROOMLIGHT);
            }
            // The quarter cabin sits narrower, so the poop deck runs along its sides.
            if (dx > QUARTER) continue;
            int tight = width - 2;
            for (int dz = -tight; dz <= tight; dz++) {
                boolean wall = Math.abs(dz) == tight || dx == QUARTER || dx == last;
                for (int rel = 6; rel <= 8; rel++) put(dx, rel, dz, wall ? quarterWall(dx, rel, dz, last) : Material.AIR);
                put(dx, 9, dz, wall ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                if (wall) put(dx, 10, dz, RAIL);
            }
        }
        // Up to the poop deck on the north side.
        for (int step = 1; step <= 5; step++) for (int dz : new int[]{-5, -6}) put(AFT + 6 - step, step, dz, Material.SPRUCE_STAIRS, "facing=west,half=bottom,shape=straight");
        for (int dz : new int[]{-5, -6}) put(AFT, 6, dz, Material.AIR);
        // The cabin door, and the striped awning over it.
        put(AFT, 1, 0, Material.SPRUCE_DOOR, "facing=east,half=lower,hinge=left,open=false,powered=false");
        put(AFT, 2, 0, Material.SPRUCE_DOOR, "facing=east,half=upper,hinge=left,open=false,powered=false");
        for (int dz = -3; dz <= 3; dz++) for (int out = 1; out <= 2; out++) put(AFT + out, 5 - out, dz, Math.floorMod(dz, 2) == 0 ? Material.RED_WOOL : PLASTER);
        for (int dz : new int[]{-3, 3}) for (int rel = 1; rel <= 2; rel++) put(AFT + 2, rel, dz, RAIL);
        // Stern lanterns, the wheel, and the rudder.
        for (int dz : new int[]{-4, 0, 4}) {
            put(last, 11, dz, RAIL);
            put(last, 12, dz, Material.LANTERN, "hanging=false,waterlogged=false");
        }
        put(-13, 6, 0, RAIL);
        put(-13, 7, 0, POST, "axis=y");
        put(-12, 7, 0, Material.DARK_OAK_TRAPDOOR, "facing=east,half=bottom,open=true,powered=false,waterlogged=false");
        for (int rel = -7; rel <= 1; rel++) {
            int dx = sternEdge(rel) - 1;
            for (int back = 0; back <= (rel < -3 ? 2 : 0); back++) put(dx - back, rel, 0, back == 2 ? POST : Material.DARK_OAK_PLANKS, back == 2 ? "axis=y" : "");
        }
    }

    private static boolean behindPane(int dx, int dz) { return Math.floorMod(dx, 4) == 2 || Math.floorMod(dz, 4) == 2; }

    private static int sternEdge(int rel) { return (int) Math.ceil(-LENGTH * .985 - rel * .35); }

    /** Timber frame and cream plaster, with lit glass in the panels. */
    private Material cabinWall(int dx, int rel, int dz, int last) {
        if (rel == 1 || rel == 4) return Material.DARK_OAK_PLANKS;
        if (dx == last) return Math.floorMod(dz, 3) == 0 ? POST : Material.ORANGE_STAINED_GLASS;
        boolean front = dx == AFT;
        int run = front ? dz : dx;
        if (Math.floorMod(run, 4) == 0) return POST;
        if (front && Math.abs(dz) <= 1) return PLASTER;
        return Math.floorMod(run, 4) == 2 ? Material.ORANGE_STAINED_GLASS : PLASTER;
    }

    private Material quarterWall(int dx, int rel, int dz, int last) {
        if (rel == 6) return Material.DARK_OAK_PLANKS;
        if (dx == last) return Math.floorMod(dz, 3) == 0 ? POST : Material.ORANGE_STAINED_GLASS;
        int run = dx == QUARTER ? dz : dx;
        return Math.floorMod(run, 3) == 0 ? POST : rel == 7 ? Material.ORANGE_STAINED_GLASS : PLASTER;
    }

    /** Cargo, the bell, hanging lanterns and an anchor let down on its chain. */
    private void fittings() {
        for (int[] cask : new int[][]{{1, -7}, {2, -7}, {2, -6}, {8, -7}, {9, -7}, {9, -6}, {-8, 7}}) put(cask[0], 1, cask[1], Material.BARREL, "facing=up,open=false");
        put(-8, 2, 7, Material.BARREL, "facing=up,open=false");
        for (int[] crate : new int[][]{{8, 7}, {9, 7}, {9, 6}}) put(crate[0], 1, crate[1], Material.SPRUCE_PLANKS);
        put(20, 3, 0, POST, "axis=y");
        put(20, 4, 0, POST, "axis=y");
        put(20, 5, 0, POST, "axis=z");
        put(20, 4, 1, Material.BELL, "attachment=ceiling,facing=north,powered=false");
        for (int[] hang : new int[][]{{-12, -2}, {0, 3}, {10, -2}, {18, 2}}) CanopyKit.lantern(b, cx + hang[0], deck - 11, cz + hang[1], 3);
        for (int down = 1; down <= 24; down++) put(19, 2 - down, -8, Material.IRON_CHAIN, "axis=y");
        for (int[] fluke : new int[][]{{19, -23}, {18, -23}, {20, -23}, {17, -22}, {21, -22}, {19, -21}}) put(fluke[0], fluke[1], -8, Material.POLISHED_DEEPSLATE);
    }

    /** How much of the bag's full width stands at this point along it: a long taper to a sharp nose and tail. */
    private static double taper(int dx) {
        double u = (dx - BAG_X) / (double) BAG_LENGTH, from = Math.abs(u);
        if (from >= 1) return 0;
        return u >= 0 ? Math.pow(1 - Math.pow(from, 1.7), .85) : Math.pow(1 - Math.pow(from, 2.1), .8);
    }

    /** Each ring of canvas panels overlaps the next, like tiles. */
    private static double panel(int dx) { return taper(dx) * (.93 + .07 * Math.floorMod(dx, 6) / 5); }

    /** The canvas: cream panels, red nose and tail behind a gold band, and a gold compass on each flank. */
    private void bag() {
        for (int dx = BAG_X - BAG_LENGTH; dx <= BAG_X + BAG_LENGTH; dx++) {
            double wide = BAG_WIDE * panel(dx), high = BAG_HIGH * panel(dx);
            if (wide < .4) continue;
            for (int dy = (int) -high - 1; dy <= high + 1; dy++) for (int dz = (int) -wide - 1; dz <= wide + 1; dz++) {
                double out = Math.sqrt(dz * dz / (wide * wide) + dy * dy / (high * high));
                if (out > 1 || out < 1 - 1.5 / Math.max(2, Math.min(wide, high))) continue;
                put(dx, BAG_Y + dy, dz, canvas(dx, dy, dz));
            }
        }
    }

    private Material canvas(int dx, int dy, int dz) {
        double u = Math.abs(dx - BAG_X) / (double) BAG_LENGTH;
        if (u >= .84) return Material.RED_WOOL;
        if (u >= .8) return Material.YELLOW_WOOL;
        int x = dx - BAG_X;
        double reach = Math.hypot(x, dy);
        if (Math.abs(dz) > 4 && reach <= 6.6) return reach > 5.8 || compass(x, dy) ? Material.YELLOW_WOOL : Material.RED_WOOL;
        if (dy < -3) return Math.floorMod(dx, 12) < 6 ? Material.WHITE_TERRACOTTA : PLASTER;
        return Math.floorMod(dx, 12) < 6 ? PLASTER : Material.BIRCH_PLANKS;
    }

    /** Four long points and four short, as on the banners. */
    private static boolean compass(int x, int y) {
        double long1 = Math.abs(x) / 1.5 + Math.abs(y) / 5.4, long2 = Math.abs(y) / 1.5 + Math.abs(x) / 5.4;
        double u = Math.abs(x + y) / Math.sqrt(2), v = Math.abs(x - y) / Math.sqrt(2);
        return long1 <= 1 || long2 <= 1 || u / 1.1 + v / 3.8 <= 1 || v / 1.1 + u / 3.8 <= 1;
    }

    /** The wooden cage round the bag, the pylons that carry it, and the stays down to the rails. */
    private void harness() {
        for (int hoop : HOOPS) {
            double wide = BAG_WIDE * taper(hoop) + 1, high = BAG_HIGH * taper(hoop) + 1;
            for (double angle = 0; angle < Math.TAU; angle += .02) {
                int dy = (int) Math.round(Math.sin(angle) * high), dz = (int) Math.round(Math.cos(angle) * wide);
                put(hoop, BAG_Y + dy, dz, Material.DARK_OAK_PLANKS);
                put(hoop + 1, BAG_Y + dy, dz, Material.DARK_OAK_PLANKS);
            }
        }
        for (int dx = BAG_X - 23; dx <= BAG_X + 23; dx++) {
            double wide = BAG_WIDE * taper(dx), high = BAG_HIGH * taper(dx);
            int flank = (int) Math.round(wide * .87) + 1, drop = (int) Math.round(high * .5);
            boolean collar = dx == HOOPS[0] || dx == HOOPS[0] + 1 || dx == HOOPS[1] || dx == HOOPS[1] + 1;
            for (int dz : new int[]{-flank, flank}) {
                if (collar) put(dx, BAG_Y - drop, dz, Material.WAXED_CUT_COPPER);
                else put(dx, BAG_Y - drop, dz, POST, "axis=x");
            }
            put(dx, BAG_Y + (int) Math.round(high) + 1, 0, POST, "axis=x");
            put(dx, BAG_Y - (int) Math.round(high) - 1, 0, POST, "axis=x");
        }
        // Four great pylons, a beam across each pair, and braces fore and aft.
        for (int[] pylon : new int[][]{{-4, -6}, {-4, 6}, {12, -6}, {12, 6}}) {
            for (int rel = 1; clear(pylon[0], rel, pylon[1]) && rel < 20; rel++) put(pylon[0], rel, pylon[1], Material.DARK_OAK_LOG, "axis=y");
            for (int up = 1; up <= 3; up++) put(pylon[0] + (pylon[0] < 4 ? up : -up), 8 + up, pylon[1], POST, "axis=x");
        }
        for (int dx : new int[]{-4, 12}) for (int dz = -5; dz <= 5; dz++) put(dx, 10, dz, POST, "axis=z");
        for (int dx : new int[]{-9, 5, 13}) for (int side : new int[]{-1, 1}) {
            int edge = edge(dx, 1);
            for (int rel = 3; clear(dx, rel, side * edge) && rel < 22; rel++) put(dx, rel, side * edge, Material.IRON_CHAIN, "axis=y");
        }
    }

    /** The burner: a copper drum on the deck, and a canvas throat flaring up into the bag. */
    private void burner() {
        for (int rel = 1; rel <= 13; rel++) {
            double across = rel <= 3 ? 2.4 : 2.4 + (rel - 3) * .3;
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                double from = Math.hypot(dx, dz);
                if (from > across || from < across - 1.4) continue;
                put(4 + dx, rel, dz, rel == 2 ? Material.WAXED_OXIDIZED_CUT_COPPER : rel <= 3 ? Material.WAXED_CUT_COPPER : rel % 4 == 0 ? Material.DARK_OAK_PLANKS : PLASTER);
            }
        }
    }

    /** Four slim fins at the tail, red cloth barred with gold, swept back. */
    private void tail() {
        int root = BAG_X - BAG_LENGTH + 12;
        for (int back = 0; back <= 12; back++) {
            int dx = root - back, reach = 1 + back * 6 / 12;
            if (back == 12) reach = 4;
            int high = (int) Math.round(BAG_HIGH * taper(dx)), wide = (int) Math.round(BAG_WIDE * taper(dx));
            for (int out = 1; out <= reach; out++) {
                Material cloth = back % 5 == 3 ? Material.YELLOW_WOOL : Material.RED_WOOL;
                put(dx, BAG_Y + high + out, 0, cloth);
                put(dx, BAG_Y - high - out, 0, cloth);
                put(dx, BAG_Y, wide + out, cloth);
                put(dx, BAG_Y, -wide - out, cloth);
            }
        }
    }
}
