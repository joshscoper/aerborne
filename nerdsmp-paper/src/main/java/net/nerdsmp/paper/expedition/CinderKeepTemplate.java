package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;
import static net.nerdsmp.paper.expedition.SampleTemplates.doorway;

/**
 * Cinder Keep, the first raid: a descent from the Forgewright's camp through volcanic caverns under
 * the keep. The Scorched Descent, Gorrak's Kiln, the Slagfalls, the Hound Pens, the Rune Galleries,
 * the Slag Chapel, the Great Bellows, the Steward's Hall and the Molten Heart. Each cavern is
 * carved from blackstone and basalt with an uneven wall and a domed ceiling; lava sits only in pits
 * and falls straight into them, so it never spreads. Runs south (+Z) from Z-116; floors at Y63,
 * doorways three wide at X -1..1, and X=0 stays clear at foot height the whole way.
 */
final class CinderKeepTemplate {
    /** The caverns: half width, first and last Z, ceiling height and how square the outline is. */
    record Cave(int half, int z1, int z2, int top, double squareness) { }

    static final Cave STAIR = new Cave(6, -116, -104, 71, 4);
    static final Cave DESCENT = new Cave(12, -102, -74, 76, 4);
    static final Cave KILN = new Cave(15, -72, -44, 82, 2);
    static final Cave SLAGFALLS = new Cave(14, -42, -12, 84, 4);
    static final Cave PENS = new Cave(14, -10, 14, 77, 3);
    static final Cave RUNES = new Cave(16, 16, 46, 80, 3);
    static final Cave CHAPEL = new Cave(13, 48, 74, 86, 4);
    static final Cave BELLOWS = new Cave(12, 76, 100, 78, 4);
    static final Cave STEWARD = new Cave(14, 102, 128, 84, 4);
    static final Cave HEART = new Cave(24, 130, 176, 94, 2);
    /** The Molten Heart's rings, as fractions of its radius: the central platform, then the moat. */
    static final double PLATFORM = .55, MOAT = .72;

    private static final Material[] ROCK = {Material.BLACKSTONE, Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT,
        Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT, Material.POLISHED_BASALT};

    private CinderKeepTemplate() { }

    static BuildCanvas.Plan build() {
        BuildCanvas b = new BuildCanvas();
        Cave[] caves = {STAIR, DESCENT, KILN, SLAGFALLS, PENS, RUNES, CHAPEL, BELLOWS, STEWARD, HEART};
        for (int index = 0; index < caves.length; index++) cavern(b, caves[index], 300 + index * 17);
        doorway(b, -103, null, Material.CHISELED_POLISHED_BLACKSTONE);
        for (int z : new int[]{-73, -43, -11, 15, 47, 75, 101, 129}) doorway(b, z, Material.IRON_BARS, Material.CHISELED_POLISHED_BLACKSTONE);
        stair(b);
        descent(b);
        kiln(b);
        slagfalls(b);
        pens(b);
        runes(b);
        chapel(b);
        bellows(b);
        steward(b);
        heart(b);
        SampleChests.place(b, "cinder_keep");
        return b.freeze();
    }

    // ---------------------------------------------------------------------------------------------
    // Caverns
    // ---------------------------------------------------------------------------------------------

    /** 0 at the cavern's centre, 1 at its outline (before the wall's wobble). */
    static double depth(Cave cave, int x, int z) {
        double cz = (cave.z1() + cave.z2()) / 2.0;
        double hx = cave.half() + .5, hz = (cave.z2() - cave.z1()) / 2.0 + .5;
        double p = cave.squareness();
        return Math.pow(Math.pow(Math.abs(x / hx), p) + Math.pow(Math.abs((z - cz) / hz), p), 1 / p);
    }

    static boolean open(Cave cave, int x, int z, int salt) {
        if (x < -cave.half() || x > cave.half() || z < cave.z1() || z > cave.z2()) return false;
        if (Math.abs(x) <= 2 && (z <= cave.z1() + 3 || z >= cave.z2() - 3)) return true; // Doorway mouths.
        return depth(cave, x, z) + (smooth(x, z, 5, salt) - .5) * .24 < 1;
    }

    /** Carves one cavern: floor, wall, domed ceiling, hanging basalt and glowing seams. */
    private static void cavern(BuildCanvas b, Cave cave, int salt) {
        for (int x = -cave.half() - 4; x <= cave.half() + 4; x++) for (int z = cave.z1() - 1; z <= cave.z2() + 1; z++) {
            if (!open(cave, x, z, salt)) {
                for (int y = 61; y <= cave.top() + 3; y++) rock(b, x, y, z, salt);
                continue;
            }
            double d = Math.min(1, depth(cave, x, z));
            b.block(x, 61, z, Material.BLACKSTONE);
            b.block(x, 62, z, Material.BLACKSTONE);
            floor(b, x, z, salt);
            int ceiling = Math.max(69, cave.top() - (int) Math.round(d * d * 6) + (int) Math.round((smooth(x, z, 4, salt + 7) - .5) * 3));
            for (int y = ceiling + 1; y <= cave.top() + 3; y++) rock(b, x, y, z, salt);
            double roll = noise(x, z, salt + 3);
            if (roll < .035) b.block(x, ceiling + 1, z, Material.SHROOMLIGHT);
            else if (roll < .05) b.block(x, ceiling + 1, z, Material.GLOWSTONE);
            else if (roll < .1 && d > .35) { // Hanging basalt, longer near the walls.
                int length = 1 + (int) (noise(x, z, salt + 5) * (d > .7 ? 4 : 2));
                for (int y = ceiling; y > ceiling - length && y > 67; y--) b.block(x, y, z, Material.BASALT, "axis=y");
            }
        }
    }

    private static void rock(BuildCanvas b, int x, int y, int z, int salt) {
        double vein = smooth(x * 3 + y, z * 3 - y, 6, salt + 11);
        if (Math.abs(vein - .5) < .018) { b.block(x, y, z, Material.MAGMA_BLOCK); return; }
        double roll = noise(x * 7 + y, z * 5 - y * 3, salt + 13);
        if (roll < .015) { b.block(x, y, z, Material.GILDED_BLACKSTONE); return; }
        if (roll < .04) { b.block(x, y, z, Material.OBSIDIAN); return; }
        Material material = ROCK[(int) (smooth(x + y * 2, z - y, 3, salt + 17) * ROCK.length) % ROCK.length];
        if (material == Material.BASALT || material == Material.POLISHED_BASALT) b.block(x, y, z, material, "axis=y");
        else b.block(x, y, z, material);
    }

    /** Dark rock underfoot with glowing magma seams. */
    private static void floor(BuildCanvas b, int x, int z, int salt) {
        double seam = smooth(x, z, 7, salt + 19);
        if (Math.abs(seam - .5) < .025 && Math.abs(x) > 2) { b.block(x, 63, z, Material.MAGMA_BLOCK); return; }
        double roll = smooth(x, z, 3, salt + 23);
        if (roll < .3) b.block(x, 63, z, Material.BLACKSTONE);
        else if (roll < .55) b.block(x, 63, z, Material.BASALT, "axis=y");
        else if (roll < .8) b.block(x, 63, z, Material.SMOOTH_BASALT);
        else b.block(x, 63, z, Material.POLISHED_BLACKSTONE);
    }

    // ---------------------------------------------------------------------------------------------
    // Pieces
    // ---------------------------------------------------------------------------------------------

    /** A lava pit two deep with rock under it. */
    private static void pit(BuildCanvas b, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            b.block(x, 61, z, Material.BLACKSTONE);
            b.block(x, 62, z, Material.LAVA);
            b.block(x, 63, z, Material.LAVA);
        }
    }

    /** A round lava pool. */
    private static void pool(BuildCanvas b, int cx, int cz, int radius) {
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++)
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= radius * radius + 1) pit(b, x, z, x, z);
    }

    /** A plank of rock across lava, flush with the floor. */
    private static void bridge(BuildCanvas b, int x1, int z1, int x2, int z2, Material deck) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            b.block(x, 62, z, Material.BLACKSTONE);
            b.block(x, 63, z, deck);
        }
    }

    /**
     * A lavafall: a source sealed in a block of rock at {@code (x, y, z)}, open only on the side
     * {@code (dx, dz)}, pouring straight down the column beside it into a pit. Every side of the
     * source but that one is rock, and the column only falls, so the lava never spreads.
     */
    private static void lavafall(BuildCanvas b, int x, int y, int z, int dx, int dz) {
        for (int cx = x - 1; cx <= x + 1; cx++) for (int cy = y - 1; cy <= y + 1; cy++) for (int cz = z - 1; cz <= z + 1; cz++)
            b.block(cx, cy, cz, Material.BLACKSTONE);
        b.block(x, y, z, Material.LAVA);
        for (int fall = 64; fall <= y; fall++) b.block(x + dx, fall, z + dz, Material.LAVA, "level=8");
        pit(b, x + dx, z + dz, x + dx, z + dz);
    }

    /** A basalt column, two by two, floor to ceiling. */
    private static void column(BuildCanvas b, int x, int z, int top) {
        for (int y = 64; y <= top; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
            b.block(x + dx, y, z + dz, y % 5 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BASALT, y % 5 == 0 ? "" : "axis=y");
    }

    private static void brazier(BuildCanvas b, int x, int z, boolean soul) {
        b.block(x, 64, z, Material.POLISHED_BLACKSTONE_BRICKS);
        b.block(x, 65, z, soul ? Material.SOUL_SOIL : Material.NETHERRACK);
        b.block(x, 66, z, soul ? Material.SOUL_FIRE : Material.FIRE);
    }

    private static void chain(BuildCanvas b, int x, int z, int from, int to) {
        for (int y = to; y <= from; y++) b.block(x, y, z, Material.IRON_CHAIN, "axis=y");
    }

    // ---------------------------------------------------------------------------------------------
    // Rooms
    // ---------------------------------------------------------------------------------------------

    /** The Forgewright's camp at the top of the stair: the party spawn. */
    private static void stair(BuildCanvas b) {
        b.block(4, 64, -110, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=west");
        b.block(5, 64, -111, Material.ANVIL, "facing=north");
        b.block(5, 64, -108, Material.SMITHING_TABLE);
        b.block(-5, 64, -111, Material.BARREL, "facing=up");
        b.block(-5, 64, -112, Material.BARREL, "facing=up");
        for (int x = -2; x <= 2; x++) b.block(x, 63, -114, Material.POLISHED_BLACKSTONE_BRICKS);
        brazier(b, -4, -105, false);
        brazier(b, 4, -105, false);
    }

    /** A sloping cavern split by a lava channel with two crossings. */
    private static void descent(BuildCanvas b) {
        pit(b, -12, -89, 12, -87);
        bridge(b, -2, -89, 2, -87, Material.POLISHED_BLACKSTONE_BRICKS);
        bridge(b, 7, -89, 9, -87, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int z = -89; z <= -87; z++) for (int x : new int[]{-3, 3, 6, 10}) b.block(x, 64, z, Material.POLISHED_BLACKSTONE_WALL);
        for (int[] spot : new int[][]{{-8, -97}, {7, -97}, {-9, -81}, {8, -78}}) column(b, spot[0], spot[1], 72);
        pool(b, -8, -92, 1);
        brazier(b, -4, -100, false);
        brazier(b, 4, -100, false);
        brazier(b, -4, -76, false);
        brazier(b, 4, -76, false);
    }

    /** Gorrak's round kiln: blast furnaces in the walls, a broken kiln ring and four lava pools. */
    private static void kiln(BuildCanvas b) {
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI / 6 + Math.PI / 12;
            int x = (int) Math.round(Math.sin(angle) * 13), z = -58 + (int) Math.round(Math.cos(angle) * 12.5);
            if (Math.abs(x) <= 2) continue;
            String facing = Math.abs(x) > Math.abs(z + 58) ? (x > 0 ? "west" : "east") : (z > -58 ? "north" : "south");
            b.block(x, 64, z, Material.BLAST_FURNACE, "lit=true,facing=" + facing);
            b.block(x, 65, z, Material.BLAST_FURNACE, "lit=true,facing=" + facing);
            b.block(x, 66, z, Material.POLISHED_BLACKSTONE_BRICK_SLAB, "type=bottom");
        }
        // The broken kiln: a ring of nether brick, gapped at the path and fallen in places.
        for (int index = 0; index < 48; index++) {
            double angle = index * Math.PI / 24;
            int x = (int) Math.round(Math.sin(angle) * 5), z = -58 + (int) Math.round(Math.cos(angle) * 5);
            if (Math.abs(x) <= 2) continue;
            int height = 1 + (int) (noise(x, z, 401) * 3);
            for (int y = 64; y < 64 + height; y++) b.block(x, y, z, noise(x, y, 403) < .3 ? Material.CRACKED_NETHER_BRICKS : Material.NETHER_BRICKS);
        }
        for (int[] spot : new int[][]{{-9, -66}, {9, -66}, {-9, -50}, {9, -50}}) pool(b, spot[0], spot[1], 2);
        chain(b, -4, -58, 81, 72);
        chain(b, 4, -58, 81, 72);
    }

    /** Lava channels down both walls, fed by lavafalls, and a cross channel with one bridge. */
    private static void slagfalls(BuildCanvas b) {
        for (int side : new int[]{-1, 1}) {
            int inner = side * 10, outer = side * 12;
            pit(b, Math.min(inner, outer), -40, Math.max(inner, outer), -14);
            for (int z = -38; z <= -16; z += 6) lavafall(b, side * 13, 79, z, -side, 0);
        }
        pit(b, -9, -28, 9, -26);
        bridge(b, -2, -28, 2, -26, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int z = -28; z <= -26; z++) for (int x : new int[]{-3, 3}) b.block(x, 64, z, Material.POLISHED_BLACKSTONE_WALL);
        b.block(5, 64, -16, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=west");
        b.block(6, 64, -17, Material.POLISHED_BASALT, "axis=x");
        for (int[] spot : new int[][]{{-6, -34}, {6, -34}, {-6, -19}, {6, -19}}) column(b, spot[0], spot[1], 78);
    }

    /** The hound pens: fenced kennels along the walls, bones and chains, fire pits by the path. */
    private static void pens(BuildCanvas b) {
        for (int side : new int[]{-1, 1}) {
            int fence = side * 9;
            for (int z = -6; z <= 10; z++) {
                if (z == 2) continue; // Kennel gates stand open.
                b.block(fence, 64, z, Material.NETHER_BRICK_FENCE);
                b.block(fence, 65, z, Material.NETHER_BRICK_FENCE);
            }
            for (int z = -5; z <= 9; z += 2) {
                b.block(side * 11, 64, z, noise(side, z, 411) < .5 ? Material.BONE_BLOCK : Material.SOUL_SOIL, "");
                chain(b, side * 11, z, 75, 68);
            }
        }
        for (int[] spot : new int[][]{{-4, -3}, {4, -3}, {-4, 9}, {4, 9}}) brazier(b, spot[0], spot[1], false);
        b.block(-3, 63, 4, Material.MAGMA_BLOCK);
        b.block(3, 63, 4, Material.MAGMA_BLOCK);
    }

    /** A lava lake crossed by a causeway, with three rune pedestals around it. */
    private static void runes(BuildCanvas b) {
        for (int x = -9; x <= 9; x++) for (int z = 24; z <= 38; z++) {
            if (Math.abs(x) <= 1) continue;
            double edge = Math.pow(Math.abs(x / 9.5), 2) + Math.pow(Math.abs((z - 31) / 7.5), 2);
            if (edge <= 1) pit(b, x, z, x, z);
        }
        bridge(b, -1, 23, 1, 39, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int[] rune : new int[][]{{-13, 31}, {13, 31}, {7, 43}}) rune(b, rune[0], rune[1]);
        for (int[] spot : new int[][]{{-12, 19}, {12, 19}, {-12, 43}}) brazier(b, spot[0], spot[1], true);
    }

    /** A rune circle with a valve lever on a pedestal in the middle. */
    private static void rune(BuildCanvas b, int cx, int cz) {
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
            boolean ring = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
            boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
            b.block(x, 62, z, Material.BLACKSTONE);
            b.block(x, 63, z, corner ? Material.CRYING_OBSIDIAN : ring ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE);
        }
        b.block(cx, 64, cz, Material.CHISELED_POLISHED_BLACKSTONE);
        b.block(cx, 65, cz, Material.LEVER, "face=floor,facing=north,powered=false");
        for (int[] corner : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.block(cx + corner[0], 64, cz + corner[1], Material.SOUL_LANTERN, "hanging=false");
    }

    /** The Slag Chapel: two rows of columns, soul-fire braziers and a flush altar of gilded stone. */
    private static void chapel(BuildCanvas b) {
        for (int z : new int[]{52, 58, 64}) for (int x : new int[]{-8, 7}) column(b, x, z, 82);
        for (int x = -4; x <= 4; x++) for (int z = 69; z <= 73; z++)
            b.block(x, 63, z, Math.abs(x) == 4 || z == 69 || z == 73 ? Material.CRYING_OBSIDIAN : Material.GILDED_BLACKSTONE);
        for (int x : new int[]{-3, 3}) {
            b.block(x, 64, 72, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            b.block(x, 65, 72, Material.SOUL_LANTERN, "hanging=false");
        }
        for (int[] spot : new int[][]{{-5, 50}, {5, 50}, {-5, 70}, {5, 70}}) brazier(b, spot[0], spot[1], true);
        chain(b, 0, 70, 85, 76);
        b.block(0, 75, 70, Material.SOUL_LANTERN, "hanging=true");
    }

    /** The Great Bellows: vents of magma under the floor and the bellows frames on the walls. */
    private static void bellows(BuildCanvas b) {
        for (int[] vent : new int[][]{{-6, 82}, {6, 82}, {-6, 94}, {6, 94}})
            for (int x = vent[0] - 1; x <= vent[0] + 1; x++) for (int z = vent[1] - 1; z <= vent[1] + 1; z++) b.block(x, 63, z, Material.MAGMA_BLOCK);
        for (int side : new int[]{-1, 1}) for (int z = 80; z <= 96; z += 8) {
            for (int y = 64; y <= 70; y++) b.block(side * 10, y, z, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int dz = 1; dz <= 3; dz++) {
                b.block(side * 10, 70, z + dz, Material.POLISHED_BLACKSTONE_BRICK_SLAB, "type=bottom");
                b.block(side * 10, 64, z + dz, Material.CRYING_OBSIDIAN);
            }
            chain(b, side * 10, z + 2, 77, 71);
        }
        b.block(-5, 64, 98, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=east");
        b.block(-6, 64, 99, Material.POLISHED_BASALT, "axis=x");
    }

    /** The Steward's hall: a red runner, red brick pillars with lanterns and a dais at the back. */
    private static void steward(BuildCanvas b) {
        for (int z = 103; z <= 127; z++) for (int x = -1; x <= 1; x++) b.block(x, 63, z, Material.RED_NETHER_BRICKS);
        for (int z = 106; z <= 122; z += 8) for (int x : new int[]{-8, 8}) {
            for (int y = 64; y <= 80; y++) b.block(x, y, z, Material.RED_NETHER_BRICKS);
            b.block(x + (x < 0 ? 1 : -1), 69, z, Material.LANTERN, "hanging=false");
        }
        for (int x = -5; x <= 5; x++) for (int z = 123; z <= 127; z++) if (Math.abs(x) > 1) b.block(x, 63, z, Material.GILDED_BLACKSTONE);
        for (int x : new int[]{-4, 4}) {
            b.block(x, 64, 126, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=" + (x < 0 ? "east" : "west") + ",half=bottom,shape=straight");
            brazier(b, x, 124, false);
        }
    }

    /**
     * The Molten Heart: a round cavern with a central platform ringed by a lava moat. The causeway
     * across the moat is a set piece; the Sovereign rises from a pool at the platform's south end.
     */
    private static void heart(BuildCanvas b) {
        Cave cave = HEART;
        for (int x = -cave.half(); x <= cave.half(); x++) for (int z = cave.z1(); z <= cave.z2(); z++) {
            if (!open(cave, x, z, 300 + 9 * 17)) continue;
            double d = depth(cave, x, z);
            if (d >= PLATFORM && d < MOAT) pit(b, x, z, x, z);
            else if (d < PLATFORM && noise(x, z, 431) < .08 && Math.abs(x) > 2) b.block(x, 63, z, Material.MAGMA_BLOCK);
        }
        // The Sovereign's pool, with a plinth to rise onto.
        for (int x = -4; x <= 4; x++) for (int z = 156; z <= 162; z++) if (Math.abs(x) > 1 || z < 158 || z > 160) pit(b, x, z, x, z);
        bridge(b, -1, 158, 1, 160, Material.CRYING_OBSIDIAN);
        // Exit pad on the north rim.
        for (int x = -1; x <= 1; x++) for (int z = 132; z <= 134; z++) b.block(x, 63, z, Material.CRYING_OBSIDIAN);
        // Lavafalls on the far wall into the moat's back.
        for (int x : new int[]{-12, -6, 6, 12}) {
            int z = 153 + (int) Math.round(Math.sqrt(Math.max(0, 1 - Math.pow(x / 24.5, 2))) * 23.5 * .64);
            lavafall(b, x, 87, z + 1, 0, -1);
        }
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.sin(angle) * 9), z = 153 + (int) Math.round(Math.cos(angle) * 8.5);
            brazier(b, x, z, false);
        }
        for (int x : new int[]{-10, 10}) chain(b, x, 153, 92, 78);
    }
}
