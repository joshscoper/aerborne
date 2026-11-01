package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The Sunken Aqueduct, on one axis from north to south. The head of the works stands on a cliff at the end of a drowned
 * valley, and the Long Span carries the channel away from it on three tiers of arches, past a bastion with a stair down
 * to the silt, to the Pump House on its bluff. The works go down under the pumps into the Cistern and on through the
 * Pressure Gallery to Floodgate Control, which lets out at the foot of the Great Dam, with the Tidewarden cut into its face.
 */
final class AqueductTemplate {
    static final int Y = 63, LAKE = 30, HEAD_Z = -22, SPAN_START = 10, SPAN_END = 110, BASTION_Z = 60, DEEP = 44;
    static final Material WORN = Material.STONE_BRICKS, BEDS = Material.ANDESITE, DRESSED = Material.POLISHED_ANDESITE, TIDE = Material.DARK_PRISMARINE,
        VERDIGRIS = Material.WAXED_OXIDIZED_COPPER, BRONZE = Material.WAXED_WEATHERED_CUT_COPPER;
    private static final int DECK_HALF = 5, LOW_TIER = 44, MID_TIER = 56;

    private AqueductTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        valley(b);
        head(b);
        longSpan(b);
        bastion(b);
        AqueductWorks.build(b);
        AqueductDam.build(b);
        SampleChests.place(b, "sunken_aqueduct");
        return b.freeze();
    }

    /** Worn stone that darkens and greens towards the waterline, so a pier reads as standing in the flood. */
    static Material stone(int x, int y, int z) {
        double blob = smooth(x + y, z, 7, 901), fine = noise(x * 3 + y, z, 902);
        if (y < LAKE + 3) return fine < .5 ? TIDE : Material.PRISMARINE_BRICKS;
        if (y < LAKE + 7) return fine < .4 ? Material.MOSSY_STONE_BRICKS : fine < .6 ? TIDE : WORN;
        if (blob > .62) return Material.MOSSY_STONE_BRICKS;
        if (blob < .36) return fine < .3 ? Material.CRACKED_STONE_BRICKS : BEDS;
        return fine < .05 ? Material.CRACKED_STONE_BRICKS : WORN;
    }

    /** Half the width of the water at this point along the valley. */
    private static double valleyHalf(int z) {
        double wide = 70 + Math.sin(z * .012) * 12 + (smooth(0, z, 45, 903) - .5) * 26;
        if (z < -6) wide *= Math.sqrt(Math.max(0, 1 - Math.pow((-6 - z) / 62D, 2)));
        return wide;
    }

    private static double ground(int x, int z) {
        double over = Math.abs(x) - valleyHalf(z);
        if (over < 0) return LAKE - 2;
        double rise = LAKE + Math.min(92, Math.pow(over, .78) * 6.2) + (smooth(x, z, 17, 904) - .5) * 18;
        // The rock lies in beds, so the slope goes up in ledges.
        double wander = smooth(x, z, 38, 911) * 16 + smooth(z, x, 21, 912) * 5;
        return Math.floor((rise + wander) / 8) * 8 - wander + ((rise + wander) % 8) * .3;
    }

    /** A drowned valley: shallow water from cliff to cliff, and cliffs that are only as thick as anyone will ever see. */
    private static void valley(BuildCanvas b) {
        for (int z = -72; z <= 345; z++) for (int x = -126; x <= 126; x++) {
            double high = ground(x, z), over = Math.abs(x) - valleyHalf(z);
            if (high <= LAKE - 2) {
                b.block(x, LAKE - 1, z, smooth(x, z, 6, 906) > .55 ? Material.GRAVEL : Material.MUD);
                b.block(x, LAKE, z, Material.WATER, "level=0");
                continue;
            }
            if (over > 36) continue;
            int top = (int) Math.round(high);
            int low = top;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) low = Math.min(low, (int) Math.round(ground(x + step[0], z + step[1])));
            for (int y = top; y >= Math.max(LAKE - 1, low - 1); y--) b.block(x, y, z, cliff(x, y, z, top));
        }
    }

    private static final Material[] BEDDING = {Material.STONE, Material.STONE, Material.ANDESITE, Material.TUFF, Material.STONE, Material.ANDESITE, Material.DEEPSLATE};

    /** Rock in level beds that wander a little, green only on the ledges, and black where the flood has been. */
    private static Material cliff(int x, int y, int z, int top) {
        int bed = Math.floorDiv(y + (int) (smooth(x, z, 34, 907) * 9), 4);
        if (y == top && smooth(x, z, 19, 909) > .52) return top > 90 ? Material.GRASS_BLOCK : Material.MOSS_BLOCK;
        if (y < LAKE + 3) return Material.MUD;
        if (y < LAKE + 6) return Material.DEEPSLATE;
        return BEDDING[Math.floorMod(bed * 7 + 3, BEDDING.length)];
    }

    /** The head of the works: a paved quay on the cliff where the channel leaves, with the engineers' camp on it. */
    private static void head(BuildCanvas b) {
        for (int x = -24; x <= 24; x++) for (int z = -44; z <= SPAN_START + 2; z++) {
            double round = Math.hypot(x / 24D, (z + 16) / 28D);
            if (round > 1) continue;
            b.block(x, Y, z, Math.abs(x) <= 1 ? Material.WATER : Math.floorMod(x * 3 + z, 11) == 0 ? Material.MOSSY_STONE_BRICKS : Math.floorMod(x + z, 2) == 0 ? WORN : DRESSED, Math.abs(x) <= 1 ? "level=0" : "");
            if (Math.abs(x) <= 1) b.block(x, Y - 1, z, TIDE);
            for (int y = Y - 1; y >= Y - 4 - (int) (round * 8) && Math.abs(x) > 1; y--) b.block(x, y, z, stone(x, y, z));
            int down = (int) (Y - 6 - round * 12);
            if (round > .9) for (int y = Y - 1; y >= down - 18; y--) b.block(x, y, z, stone(x, y, z));
            if (round > .94 && (z < SPAN_START - 2)) {
                b.block(x, Y + 1, z, WORN);
                b.block(x, Y + 2, z, Math.floorMod(x + z, 4) == 0 ? DRESSED : Material.STONE_BRICK_SLAB, Math.floorMod(x + z, 4) == 0 ? "" : "type=bottom,waterlogged=false");
            }
        }
        // The intake: water comes out of the cliff through a great stone mouth at the back of the quay.
        for (int a = -7; a <= 7; a++) for (int h = 1; h <= 12; h++) for (int d = 0; d <= 3; d++) {
            double arch = Math.hypot(a, Math.max(0, h - 6));
            if (arch < 3.6) continue;
            b.block(a, Y + h, -46 - d, arch < 4.8 ? DRESSED : stone(a, Y + h, -46 - d));
        }
        for (int a = -2; a <= 2; a++) for (int h = 0; h <= 7; h++) b.block(a, Y + h, -45, h < 4 ? Material.WATER : Material.AIR, h < 4 ? "level=0" : "");
        for (int side = -1; side <= 1; side += 2) {
            pierLamp(b, side * 8, -12);
            pierLamp(b, side * 8, 4);
            SkyKit.bannerPost(b, side * 15, Y + 1, -28, true, Material.CYAN_WOOL);
        }
        SkyKit.tent(b, -14, Y + 1, -14, Material.CYAN_WOOL);
        SkyKit.crates(b, 13, Y + 1, -13);
        SkyKit.crates(b, 16, Y + 1, -20);
        b.block(4, Y + 1, -24, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
        b.block(12, Y + 1, 72, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    static void pierLamp(BuildCanvas b, int x, int z) {
        b.block(x, Y + 1, z, Material.CHISELED_STONE_BRICKS);
        b.block(x, Y + 2, z, BRONZE);
        b.block(x, Y + 3, z, BRONZE);
        b.block(x, Y + 4, z, Material.SEA_LANTERN);
        b.block(x, Y + 5, z, BRONZE);
    }

    /** Three tiers of arches, the bottom ones the widest, each pier stepped out above its foot. */
    private static void longSpan(BuildCanvas b) {
        for (int z = SPAN_START; z <= SPAN_END; z++) {
            for (int x = -DECK_HALF - 2; x <= DECK_HALF + 2; x++) {
                int a = Math.abs(x);
                if (a > DECK_HALF + 1 && !bastionHere(z)) continue;
                top(b, x, z, a);
            }
            tier(b, z, MID_TIER, Y - 2, 6, 1, DECK_HALF);
            tier(b, z, LOW_TIER, MID_TIER - 1, 12, 2, DECK_HALF + 1);
            tier(b, z, LAKE - 1, LOW_TIER - 1, 24, 3, DECK_HALF + 3);
        }
    }

    private static boolean bastionHere(int z) { return Math.abs(z - BASTION_Z) <= 15; }

    private static void top(BuildCanvas b, int x, int z, int a) {
        if (a <= 1) {
            b.block(x, Y, z, Material.WATER, "level=0");
            b.block(x, Y - 1, z, TIDE);
            return;
        }
        boolean kerb = a == 2;
        b.block(x, Y, z, kerb ? DRESSED : Math.floorMod(x * 3 + z, 13) == 0 ? Material.MOSSY_STONE_BRICKS : WORN);
        b.block(x, Y - 1, z, BEDS);
        if (a == DECK_HALF + 1) {
            b.block(x, Y + 1, z, stone(x, Y + 1, z));
            b.block(x, Y + 2, z, Math.floorMod(z, 6) == 0 ? DRESSED : Material.STONE_BRICK_SLAB, Math.floorMod(z, 6) == 0 ? "" : "type=bottom,waterlogged=false");
        }
    }

    /** One slice of one tier: a true round arch between square piers, a ring of dressed stone round it, a course along the top. */
    private static void tier(BuildCanvas b, int z, int low, int high, int bay, int pier, int half) {
        double middle = Math.abs(Math.floorMod(z - SPAN_START, bay) + .5 - bay / 2D), span = bay / 2D - pier;
        int spring = high - (int) Math.ceil(span) - 2;
        boolean atPier = middle > span;
        for (int y = low; y <= high; y++) for (int x = -half; x <= half; x++) {
            double out = y <= spring ? (atPier ? span + 3 : span - 1) : Math.hypot(middle, y - spring);
            if (out < span) continue;
            boolean ring = y > spring && out < span + 1.3;
            b.block(x, y, z, y == high ? DRESSED : ring ? Material.CHISELED_STONE_BRICKS : stone(x, y, z));
        }
        // Each pier stands on a stepped foot, and each tier's course stands a little proud of the face.
        if (atPier) for (int x = -half - 1; x <= half + 1; x++) for (int y = low; y <= low + 2; y++) b.block(x, y, z, stone(x, y, z));
        for (int side = -1; side <= 1; side += 2) b.block(side * (half + 1), high, z, DRESSED);
    }

    /** A wide place on the span, with a pump tower and the stair down to the silt. */
    private static void bastion(BuildCanvas b) {
        int half = 15;
        for (int z = BASTION_Z - half; z <= BASTION_Z + half; z++) for (int x = -half - 8; x <= half + 8; x++) {
            int a = Math.abs(x);
            double r = Math.hypot(x, (z - BASTION_Z) * 1.4);
            if (r > 22 || a <= DECK_HALF + 1) continue;
            b.block(x, Y, z, Math.floorMod(x * 2 + z, 9) == 0 ? Material.MOSSY_STONE_BRICKS : Math.floorMod(x + z, 2) == 0 ? WORN : DRESSED);
            for (int y = Y - 1; y >= Y - 2 - (int) ((22 - r) * 1.5) - (r < 14 ? 40 : 0); y--) b.block(x, y, z, stone(x, y, z));
            if (r > 21) {
                b.block(x, Y + 1, z, stone(x, Y + 1, z));
                b.block(x, Y + 2, z, Math.floorMod(x + z, 5) == 0 ? DRESSED : Material.STONE_BRICK_SLAB, Math.floorMod(x + z, 5) == 0 ? "" : "type=bottom,waterlogged=false");
            }
        }
        for (int x = DECK_HALF + 1; x <= DECK_HALF + 3; x++) for (int side = -1; side <= 1; side += 2) for (int z = BASTION_Z - 2; z <= BASTION_Z + 2; z++) {
            b.block(side * x, Y + 1, z, Material.AIR);
            b.block(side * x, Y + 2, z, Material.AIR);
        }
        // East: the old engine tower, with its beam still up.
        tower(b, 16, BASTION_Z, 5, 22);
        // West: the stair, round the inside of a tower, down to the silt.
        AqueductDam.silt(b);
    }

    static void tower(BuildCanvas b, int cx, int cz, int radius, int high) {
        for (int dx = -radius - 1; dx <= radius + 1; dx++) for (int dz = -radius - 1; dz <= radius + 1; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > radius + .5) continue;
            for (int h = 1; h <= high; h++) b.block(cx + dx, Y + h, cz + dz, h % 6 == 0 ? Material.CHISELED_STONE_BRICKS : stone(cx + dx, Y + h, cz + dz));
            b.block(cx + dx, Y + high + 1, cz + dz, VERDIGRIS);
        }
        for (int up = 2; up <= radius + 3; up++) {
            int half = radius + 2 - up;
            if (half < 0) break;
            for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) if (Math.hypot(dx, dz) <= half + .5) b.block(cx + dx, Y + high + up, cz + dz, VERDIGRIS);
        }
        for (int h = high / 2; h <= high - 2; h++) b.block(cx - radius, Y + h, cz, Material.CYAN_STAINED_GLASS);
    }
}
