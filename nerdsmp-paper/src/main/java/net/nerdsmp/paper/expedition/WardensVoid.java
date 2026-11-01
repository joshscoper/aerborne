package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;
import static net.nerdsmp.paper.expedition.WardensTemplate.*;

/**
 * The Unwritten: what's left over outside the record, reached only through the portal in the Hall of Seals. It stands well
 * away from the Hall, inside a closed shell of black with its own stars, so no daylight gets in. The party arrives on a
 * rock and crosses to a broken disc of the Hall's own floor, with the great rift behind Oriveth and pieces of the wings
 * they've just fought through turning in the dark around it.
 */
final class WardensVoid {
    static final int X = 320, Z = 300, RADIUS = 30, PAD = 52;
    private static final int SHELL = 78, SHELL_Y = 64, SHELL_Z = Z + 8, RIFT_Z = Z + 54, RIFT_Y = Y + 30, RIFT_OUT = 32, RIFT_IN = 23;
    private static final Isle DARK = new Isle(Material.BLACKSTONE, Material.DEEPSLATE,
        new Material[]{Material.BLACKSTONE, Material.DEEPSLATE, Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.DEEPSLATE});
    private static final Isle FIRE = new Isle(Material.BASALT, Material.BLACKSTONE, new Material[]{Material.BLACKSTONE, Material.BASALT, Material.MAGMA_BLOCK});
    private static final Isle ICE = new Isle(Material.SNOW_BLOCK, Material.PACKED_ICE, new Material[]{Material.PACKED_ICE, Material.BLUE_ICE});
    private static final Isle MOSS = new Isle(Material.MOSS_BLOCK, Material.DIRT, new Material[]{Material.STONE, Material.ANDESITE, Material.MOSSY_COBBLESTONE});

    private WardensVoid() { }

    static void build(BuildCanvas b) {
        shell(b);
        disc(b);
        arrival(b);
        rift(b);
        wreckage(b);
    }

    /** The inside of the sky: black, with a few real stars, and streaks of something that isn't quite light. */
    private static void shell(BuildCanvas b) {
        int reach = SHELL + 1;
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) for (int dy = -reach; dy <= reach; dy++) {
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (Math.abs(r - SHELL) > .9) continue;
            double cloud = (smooth(dx + dy, dz, 19, 801) + smooth(dx, dz + dy, 13, 802)) / 2;
            double fleck = noise(dx * 3 + dy, dz, 803);
            b.block(X + dx, SHELL_Y + dy, SHELL_Z + dz, fleck < .012 ? Material.SEA_LANTERN : cloud > .62 ? Material.CRYING_OBSIDIAN
                : cloud > .57 ? Material.PURPLE_CONCRETE : cloud > .54 ? Material.PURPLE_TERRACOTTA : Material.BLACK_CONCRETE);
        }
    }

    /** A disc of the Hall's floor, gone dark, edges broken, with the rock it was cut from hanging under it. */
    private static void disc(BuildCanvas b) {
        for (int dx = -RADIUS - 4; dx <= RADIUS + 4; dx++) for (int dz = -RADIUS - 4; dz <= RADIUS + 4; dz++) {
            double r = Math.hypot(dx, dz), angle = Math.atan2(dz, dx), edge = RADIUS + (smooth(dx, dz, 7, 804) - .5) * 7;
            if (r > edge) continue;
            int x = X + dx, z = Z + dz;
            b.block(x, Y, z, floor(dx, dz, r, angle));
            int under = 2 + (int) Math.round(26 * Math.pow(Math.max(0, 1 - r / edge), .6) + noise(x, z, 805) * 3);
            for (int down = 1; down <= under; down++) {
                double seam = smooth(x + down, z, 6, 806);
                b.block(x, Y - down, z, seam > .64 ? Material.CRYING_OBSIDIAN : seam < .36 ? Material.OBSIDIAN : down % 7 == 0 ? Material.DEEPSLATE_TILES : Material.BLACKSTONE);
            }
        }
        WardensHall.plate(b, X - 17, Z, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
        WardensHall.plate(b, X + 17, Z, Material.BLUE_ICE, Material.SEA_LANTERN);
        WardensHall.plate(b, X, Z + 17, Material.MOSS_BLOCK, Material.OCHRE_FROGLIGHT);
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > 8.6) continue;
            b.block(X + dx, Y + 1, Z + dz, r > 7.6 ? Material.POLISHED_BLACKSTONE_SLAB : r > 6.6 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE, r > 7.6 ? "type=bottom,waterlogged=false" : "");
        }
        medallion(b, X, Y + 1, Z, 5);
        for (int index = 0; index < 7; index++) columnAdrift(b, index);
        for (int dx = -24; dx <= 24; dx += 8) for (int dz = -24; dz <= 24; dz += 8) if (Math.hypot(dx, dz) <= 28) b.block(X + dx, Y + 7, Z + dz, Material.LIGHT, "level=11,waterlogged=false");
    }

    private static Material floor(int dx, int dz, double r, double angle) {
        Material knot = knotFloor(r, angle, 22, 12);
        if (knot == Material.GOLD_BLOCK) return Material.GOLD_BLOCK;
        if (knot == TRIM) return Material.CHISELED_DEEPSLATE;
        double seam = Math.abs(smooth(X + dx, Z + dz, 9, 807) - .5);
        if (seam < .012) return Material.CRYING_OBSIDIAN;
        return Math.floorMod((int) (r * .4) + (int) Math.floor(angle * r * .14), 2) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
    }

    /** A column of the Hall, broken, its upper drums holding in the air over the stump as if it hadn't been told yet. */
    private static void columnAdrift(BuildCanvas b, int index) {
        double angle = .35 + index * Math.TAU / 7;
        int cx = X + (int) Math.round(Math.cos(angle) * 26), cz = Z + (int) Math.round(Math.sin(angle) * 26);
        if (Math.abs(cx - X) <= 7 && cz < Z) return;
        int stump = 3 + index % 3, drums = 3 + index % 2;
        for (int h = 1; h <= stump; h++) drum(b, cx, Y + h, cz, h == 1 ? TRIM : BLOCK);
        int at = Y + stump + 3;
        for (int drum = 0; drum < drums; drum++) {
            int high = 3 + (drum + index) % 2;
            int slide = (drum + 1) * (index % 2 == 0 ? 1 : -1) / 2;
            for (int h = 0; h < high; h++) drum(b, cx + slide, at + h, cz - slide, h == high - 1 && drum == drums - 1 ? Material.GOLD_BLOCK : h == 0 ? TRIM : drum > 1 ? Material.POLISHED_BLACKSTONE : BLOCK);
            at += high + 2;
        }
    }

    private static void drum(BuildCanvas b, int cx, int y, int cz, Material block) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.hypot(dx, dz) <= 2.4) b.block(cx + dx, y, cz + dz, block);
    }

    /** The rock the portal sets you on, the walk over, and the last doorway of the Hall, standing by itself. */
    private static void arrival(BuildCanvas b) {
        int z = Z - PAD;
        island(b, X, Y, z, 11, 18, DARK);
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            double r = Math.hypot(dx, dz);
            if (r < 9) b.block(X + dx, Y, z + dz, Math.abs(r - 6) < .7 ? Material.GOLD_BLOCK : (dx + dz) % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
        }
        for (int at = z + 8; at <= Z - RADIUS + 3; at++) for (int dx = -3; dx <= 3; dx++) {
            b.block(X + dx, Y, at, Math.abs(dx) == 3 ? Material.POLISHED_BLACKSTONE_BRICKS : dx == 0 ? Material.GOLD_BLOCK : Math.floorMod(at, 2) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
            for (int down = 1; down <= 2 + Math.abs(dx) / 3 + Math.floorMod(at * 3 + dx, 3); down++) b.block(X + dx, Y - down, at, Material.BLACKSTONE);
        }
        int door = Z - RADIUS - 1;
        // One jamb stands to the lintel; the other was broken off a long time ago.
        for (int side = -1; side <= 1; side += 2) for (int a = 3; a <= 4; a++) for (int h = 1; h <= 9; h++) {
            if (side > 0 && h > 5 - (a - 3) * 2) continue;
            b.block(X + side * a, Y + h, door, h == 9 ? Material.GOLD_BLOCK : a == 3 ? TRIM : BLOCK);
        }
        for (int dx = -6; dx <= 6; dx += 6) for (int dz = -6; dz <= 6; dz += 6) b.block(X + dx, Y + 6, z + dz, Material.LIGHT, "level=11,waterlogged=false");
        b.block(X + 6, Y + 1, z + 2, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    /** The hero: a tear taller than the Hall's gate, ringed in black glass, with nothing in the middle at all. */
    private static void rift(BuildCanvas b) {
        for (int dx = -RIFT_OUT - 3; dx <= RIFT_OUT + 3; dx++) for (int dy = -RIFT_OUT - 3; dy <= RIFT_OUT + 3; dy++) {
            double r = Math.hypot(dx, dy), angle = Math.atan2(dy, dx), rough = (smooth(dx, dy, 5, 808) - .5) * 5, out = RIFT_OUT + rough + Math.sin(angle * 3) * 2;
            if (r > out) continue;
            int y = RIFT_Y + dy, x = X + dx;
            if (r < RIFT_IN) {
                b.block(x, y, RIFT_Z, Material.END_GATEWAY);
                b.block(x, y, RIFT_Z + 1, Material.BLACK_CONCRETE);
                continue;
            }
            double through = (r - RIFT_IN) / (out - RIFT_IN);
            int deep = 1 + (int) Math.round((1 - Math.abs(through - .5) * 2) * 4);
            double streak = Math.sin(angle * 18 + r * .7);
            for (int d = -deep; d <= deep; d++) b.block(x, y, RIFT_Z + d, through < .16 ? Material.PEARLESCENT_FROGLIGHT : streak > .72 ? Material.CRYING_OBSIDIAN : streak < -.8 ? Material.GOLD_BLOCK : Material.OBSIDIAN);
        }
        // The three Wardens' swords, driven through the ring from behind, each a long way from its owner.
        Carving c = new Carving(b, X, Y, RIFT_Z - 6, 0, 1);
        WardensArenas.spear(c, -32, 58, 8, .5, -.58, -.1, 40, 3.4, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
        WardensArenas.spear(c, 32, 58, 8, -.5, -.58, -.1, 40, 3.4, Material.DIAMOND_BLOCK, Material.SEA_LANTERN);
        WardensArenas.spear(c, 0, -14, 8, 0, .8, -.1, 26, 3.4, Material.POLISHED_DIORITE, Material.OCHRE_FROGLIGHT);
    }

    /** What it's already taken: pieces of each wing, adrift. */
    private static void wreckage(BuildCanvas b) {
        Isle[] kinds = {FIRE, ICE, MOSS, WardensTemplate.HALL_ROCK, DARK};
        int[][] pieces = {{-56, 48, -18, 7}, {-62, 30, 14, 5}, {-46, 74, 26, 5}, {52, 46, -22, 7}, {60, 68, 12, 5}, {44, 26, 30, 6}, {-40, 22, -36, 5}, {38, 88, -30, 4},
            {-30, 90, 12, 4}, {66, 40, 40, 5}, {-66, 52, 44, 6}, {18, 16, -52, 5}, {-20, 92, -46, 4}};
        for (int index = 0; index < pieces.length; index++) {
            int[] piece = pieces[index];
            Isle kind = kinds[index % kinds.length];
            island(b, X + piece[0], piece[1], Z + piece[2], piece[3], piece[3] * 2, kind);
            int stones = 2 + index % 3;
            for (int stone = 0; stone < stones; stone++) {
                int dx = (stone * 3 + index) % (piece[3] * 2 - 1) - piece[3] + 1, dz = (stone * 5 + index * 2) % (piece[3] * 2 - 1) - piece[3] + 1;
                for (int h = 1; h <= 2 + (stone + index) % 4; h++) b.block(X + piece[0] + dx, piece[1] + h, Z + piece[2] + dz, kind == FIRE ? Material.POLISHED_BLACKSTONE_BRICKS : kind == ICE ? Material.BLUE_ICE : kind == MOSS ? Material.MOSSY_STONE_BRICKS : BLOCK);
            }
        }
    }
}
