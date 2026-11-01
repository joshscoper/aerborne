package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;
import static net.nerdsmp.paper.expedition.WardensTemplate.*;

/**
 * The three Wardens' arenas. Each is a horseshoe of its own stuff, open to the bridge and rising to the far end, where
 * the Warden's colossus stands over the fight, so the eye comes off the bridge straight to it. The fighting floor is the
 * same flat round in all three: what changes is everything that frames it. Fire is a caldera of basalt columns round a
 * moat of lava, frost a glacier round a frozen lake with the seal lit under the ice, and stone a mountain top cut into
 * terraces under the Stone Warden, seated, with the falls coming down either side of the throne.
 */
final class WardensArenas {
    private static final int C = ARENA, R = ARENA_RADIUS, ROCK_AT = ARENA + 8, ROCK = 46;
    /** How far round from the far end the walls go before they've sunk to nothing. */
    private static final double OPEN = 2.4;
    private static final Material GOLD = Material.GOLD_BLOCK;

    /** Builds each column of a ring, given its across, along, radius and angle from the far end. */
    private interface Column {
        void at(int a, int f, double r, double angle);
    }

    private WardensArenas() { }

    private static void everyColumn(Carving c, Column column) {
        for (int a = -ROCK - 6; a <= ROCK + 6; a++) for (int f = C - ROCK - 6; f <= C + ROCK + 14; f++)
            column.at(a, f, Math.hypot(a, f - C), Math.abs(Math.atan2(a, f - C)));
    }

    private static void rock(BuildCanvas b, Carving c, Isle isle) {
        int x = c.wx(0, ROCK_AT), z = c.wz(0, ROCK_AT);
        island(b, x, Y, z, ROCK, 44, isle);
        SkyRuins.hang(b, x, Y, z, ROCK, 44);
    }

    private static boolean onRock(BuildCanvas b, Carving c, int a, int f) { return b.get(c.wx(a, f), Y, c.wz(a, f)) != null; }

    /** The wall's crest at this angle: {@code high} at the far end, sinking to nothing at the horseshoe's tips. */
    private static double crest(double angle, int high) {
        double t = Math.min(1, angle / OPEN);
        return high * Math.pow(Math.cos(t * Math.PI / 2), .7);
    }

    /** A plain low wall round the part of the floor the bowl doesn't reach, with a gap for the bridge. */
    private static void kerb(Carving c, double r, double angle, int a, int f, Material wall, Material cap) {
        if (Math.abs(r - R) > .75 || Math.abs(a) <= 4 || angle < OPEN - .25) return;
        c.set(a, 1, f, wall);
        c.set(a, 2, f, Math.floorMod(a + f * 3, 5) == 0 ? wall : cap);
    }

    /** A tapering spear from {@code a, h, f} out along {@code da, dh, df}, its base {@code wide} across. */
    static void spear(Carving c, double a, double h, double f, double da, double dh, double df, int length, double wide, Material body, Material tip) {
        for (double along = 0; along < length; along += .5) {
            double t = along / length, radius = wide * (1 - t) + .4;
            int reach = (int) Math.ceil(radius);
            for (int x = -reach; x <= reach; x++) for (int y = -reach; y <= reach; y++) for (int z = -reach; z <= reach; z++) {
                if (Math.sqrt(x * x + y * y + z * z) > radius) continue;
                c.set((int) Math.round(a + da * along) + x, (int) Math.round(h + dh * along) + y, (int) Math.round(f + df * along) + z, t > .72 ? tip : body);
            }
        }
    }

    /** A fan of spears standing out behind a colossus's head, which is what you see first from the far end of a bridge. */
    private static void crown(Carving c, int h, int f, Material body, Material tip) {
        for (int index = -5; index <= 5; index++) {
            double angle = index * Math.toRadians(17);
            int length = 30 - Math.abs(index) * 2 + Math.floorMod(index * 7, 5);
            spear(c, Math.sin(angle) * 4, h + Math.cos(angle) * 3, f, Math.sin(angle), Math.cos(angle), .08, length, 2.6, body, tip);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fire: a caldera
    // ---------------------------------------------------------------------------------------------

    private static final int CALDERA = 33, CALDERA_WIDE = 16, CALDERA_HIGH = 50, COLUMN = 3;

    static void ember(BuildCanvas b, Carving c) {
        rock(b, c, new SampleKit.Isle(Material.BASALT, Material.BLACKSTONE,
            new Material[]{Material.BLACKSTONE, Material.BASALT, Material.DEEPSLATE, Material.BLACKSTONE, Material.MAGMA_BLOCK}));
        everyColumn(c, (a, f, r, angle) -> {
            if (r <= R - .5) c.set(a, 0, f, emberFloor(r, Math.atan2(f - C, a)));
            else if (r <= R + 1) c.set(a, 0, f, Material.POLISHED_BLACKSTONE_BRICKS);
            else if (r < CALDERA && angle < OPEN + .1) moat(c, a, f);
            kerb(c, r, angle, a, f, Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE);
            if (Math.abs(r - (R + 1)) < .75 && angle < OPEN + .1) {
                c.set(a, 1, f, GOLD);
                c.set(a, 2, f, Math.floorMod(a + f, 4) == 0 ? GOLD : Material.POLISHED_BLACKSTONE_BRICKS);
            }
            if (angle < OPEN && r >= CALDERA && r < CALDERA + CALDERA_WIDE && onRock(b, c, a, f)) basalt(c, a, f, r, angle);
        });
        medallion(c, 7, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
        for (int side = -1; side <= 1; side += 2) WardensWings.brazier(c, side * 18, C, Material.POLISHED_BLACKSTONE_BRICKS, GOLD, false);
        for (double angle : new double[]{-1.9, -.95, -.5, .5, .95, 1.9}) lavaFall(c, angle);
        ring(c, 25, 8, Math.PI / 8, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, GOLD);
        int foot = C + 37;
        plinth(c, foot, 6, Material.POLISHED_BLACKSTONE_BRICKS, GOLD);
        WardenStatue.stand(b, c.wx(0, foot), Y + 7, c.wz(0, foot), -c.dirX(), -c.dirZ(), WardensHall.EMBER);
        crown(c, 34, foot + 6, Material.BLACKSTONE, Material.MAGMA_BLOCK);
        for (int a = -13; a <= 13; a += 3) for (int h = 44; h >= 2; h--) if (Math.floorMod(a * 3, 9) == 0) c.set(a, h, foot + 8, Material.LAVA, "level=0");
    }

    /** Black paving, a braid of gold round the middle of it, and a hairline or two of fire. */
    private static Material emberFloor(double r, double angle) {
        Material knot = knotFloor(r, angle, 19, 10);
        if (knot == GOLD) return GOLD;
        if (knot == TRIM) return Material.CHISELED_POLISHED_BLACKSTONE;
        return Math.floorMod((int) (r * .4) + (int) Math.floor(angle * r * .14), 2) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE;
    }

    private static void moat(Carving c, int a, int f) {
        c.set(a, 0, f, Material.LAVA, "level=0");
        c.set(a, -1, f, Material.MAGMA_BLOCK);
    }

    /** Columns of basalt standing shoulder to shoulder, each its own height, the way lava cools. */
    private static void basalt(Carving c, int a, int f, double r, double angle) {
        int row = Math.floorDiv(f, COLUMN), cell = Math.floorDiv(a + (Math.floorMod(row, 2) == 0 ? 0 : 1), COLUMN);
        double through = (r - CALDERA) / CALDERA_WIDE, tone = noise(cell, row, 701);
        int alcove = alcoveFloor(a, f, angle, 12, 40);
        int top = (int) Math.round(crest(angle, CALDERA_HIGH) * (.45 + .55 * Math.sin(Math.min(1, through * 1.5) * Math.PI / 2)) * (1 - through * through * .7) + noise(cell, row, 702) * 7 - 3);
        if (alcove >= 0) top = Math.min(top, alcove);
        Material shaft = tone < .5 ? Material.BASALT : tone < .75 ? Material.POLISHED_BASALT : Material.BLACKSTONE;
        for (int h = 0; h <= top; h++) {
            boolean seam = Math.abs(Math.sin(angle * 11 + h * .07 + noise(cell, row, 703) * .5)) < .03;
            if (seam) c.set(a, h, f, Material.MAGMA_BLOCK);
            else if (shaft == Material.BLACKSTONE) c.set(a, h, f, shaft);
            else c.set(a, h, f, shaft, "axis=y");
        }
        if (top >= 1) c.set(a, top, f, noise(cell, row, 704) < .08 ? Material.MAGMA_BLOCK : Material.SMOOTH_BASALT);
    }

    /** Behind the colossus the wall is cut back. Returns how high the wall may stand here, or -1 if it's outside the cut. */
    private static int alcoveFloor(int a, int f, double angle, int half, int back) {
        return Math.abs(a) <= half && f - C < back + 6 && angle < 1 ? 0 : -1;
    }

    private static void lavaFall(Carving c, double angle) {
        double inner = CALDERA + .5;
        int a0 = (int) Math.round(Math.sin(angle) * inner), f0 = C + (int) Math.round(Math.cos(angle) * inner);
        int top = (int) (crest(Math.abs(angle), CALDERA_HIGH) * .5);
        for (int h = top; h >= 0; h--) {
            int wide = 1 + (top - h) / 12;
            for (int d = -wide; d <= wide; d++) c.set(a0 + (int) Math.round(Math.cos(angle) * d), h, f0 - (int) Math.round(Math.sin(angle) * d), Material.LAVA, "level=0");
        }
    }

    /** The stepped block a colossus stands on. */
    private static void plinth(Carving c, int f, int high, Material stone, Material band) {
        for (int h = 0; h <= high; h++) {
            int half = 10 + (high - h) / 2;
            for (int a = -half; a <= half; a++) for (int d = -half; d <= half; d++) {
                boolean corner = Math.abs(a) + Math.abs(d) > half * 3 / 2;
                if (!corner) c.set(a, h, f + d, h == high - 1 ? band : stone);
            }
        }
    }

    /** A round seal in the middle of the floor. */
    private static void medallion(Carving c, int radius, Material field, Material heart) {
        for (int a = -radius; a <= radius; a++) for (int d = -radius; d <= radius; d++) {
            double r = Math.hypot(a, d), angle = Math.atan2(d, a);
            if (r > radius + .4) continue;
            boolean edge = r > radius - 1.2, spoke = Math.abs(Math.sin(angle * 1.5 + Math.PI / 4)) < .16;
            c.set(a, 0, C + d, r < 1.3 ? heart : edge || spoke ? GOLD : field);
        }
    }

    /** Columns round the floor, in the hall's manner, each with a band and a head. */
    private static void ring(Carving c, int radius, int count, double start, Material shaft, Material band, Material head) {
        for (int index = 0; index < count; index++) {
            double angle = start + index * Math.TAU / count;
            int a = (int) Math.round(Math.cos(angle) * radius), f = C + (int) Math.round(Math.sin(angle) * radius);
            if (Math.abs(a) <= 7 && f < C) continue;
            int high = 16 + index % 3 * 2;
            for (int h = 1; h <= high; h++) {
                int reach = h <= 2 || h >= high - 1 ? 2 : 1;
                Material block = h <= 2 || h >= high - 1 ? band : Math.floorMod(h, 6) == 0 ? band : shaft;
                for (int da = -reach; da <= reach; da++) for (int df = -reach; df <= reach; df++)
                    if (Math.abs(da) + Math.abs(df) < reach * 2) c.set(a + da, h, f + df, block);
            }
            c.set(a, high + 1, f, head);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Frost: a glacier round a frozen lake
    // ---------------------------------------------------------------------------------------------

    private static final int GLACIER = 30, GLACIER_WIDE = 17, GLACIER_HIGH = 54;
    private static final double RIFT = 1.15;

    static void frost(BuildCanvas b, Carving c) {
        rock(b, c, new SampleKit.Isle(Material.SNOW_BLOCK, Material.PACKED_ICE,
            new Material[]{Material.PACKED_ICE, Material.STONE, Material.BLUE_ICE, Material.DIORITE, Material.PACKED_ICE}));
        everyColumn(c, (a, f, r, angle) -> {
            if (r <= R - 2) lake(c, a, f, r, Math.atan2(f - C, a));
            else if (r <= GLACIER + 1) c.set(a, 0, f, Math.floorMod((int) (angle * 40), 7) == 0 ? GOLD : Math.floorMod(a + f, 2) == 0 ? Material.POLISHED_DIORITE : Material.CALCITE);
            kerb(c, r, angle, a, f, Material.DIORITE, Material.POLISHED_DIORITE);
            if (angle < OPEN && r >= GLACIER - 3 && r < GLACIER + GLACIER_WIDE && onRock(b, c, a, f)) glacier(c, a, f, r, angle);
        });
        for (int side = -1; side <= 1; side += 2) {
            frozenFall(c, side * .55);
            frozenFall(c, side * 1.65);
            rift(c, side * RIFT);
        }
        boundColumns(c);
        int foot = C + 37;
        // A deep cut in the ice for him, its back wall the darkest blue there is, so the pale figure stands clear of it.
        for (int a = -22; a <= 22; a++) for (int f = C + 29; f <= foot + 12; f++) for (int h = 1; h <= 64; h++) {
            if (Math.abs(a) / 23D + h / 66D < 1) c.set(a, h, f, f >= foot + 9 ? Material.BLUE_ICE : Material.AIR);
        }
        for (int a = -22; a <= 22; a++) for (int f = C + 29; f <= foot + 8; f++) c.set(a, 0, f, Material.POLISHED_DIORITE);
        plinth(c, foot, 4, Material.POLISHED_DIORITE, GOLD);
        WardenStatue.stand(b, c.wx(0, foot), Y + 5, c.wz(0, foot), -c.dirX(), -c.dirZ(), WardensHall.FROST_PALE);
        crown(c, 32, foot + 6, Material.CALCITE, Material.SNOW_BLOCK);
    }

    /** Glass over the seal, so it reads as clear ice with the gold a block down in the dark, and nobody slides on it. */
    private static void lake(Carving c, int a, int f, double r, double angle) {
        Material under = knotFloor(r, angle, 17, 10);
        double crack = Math.abs(smooth(c.wx(a, f), c.wz(a, f), 8, 711) - .5);
        c.set(a, 0, f, crack < .012 ? Material.WHITE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
        boolean ring = r > 5.8 && r < 7.2, spoke = r < 6 && Math.abs(Math.sin(angle * 1.5 + Math.PI / 4)) < .16;
        c.set(a, -1, f, under == GOLD || ring || spoke ? GOLD : Math.floorMod(a * 7 + f * 3, 23) == 0 ? Material.SEA_LANTERN : Material.BLUE_ICE);
    }

    /** One column of the ice, fluted down its face, leaning out over the lake near the top, snow on its crown. */
    private static void glacier(Carving c, int a, int f, double r, double angle) {
        double through = (r - GLACIER) / GLACIER_WIDE, flute = Math.abs(Math.sin(angle * 15)) * 2.6;
        int top = (int) Math.round(crest(angle, GLACIER_HIGH) * Math.sqrt(Math.max(0, 1 - through * through)) + smooth(a, f, 7, 712) * 8 - 4);
        for (int h = 0; h <= top; h++) {
            double lean = Math.max(0, h - top * .62) * .22, inner = GLACIER + flute - lean + (smooth((int) (angle * 20) + 900, h, 6, 713) - .5) * 2.4;
            if (r < inner) continue;
            double seam = smooth((int) (angle * 26) + 500, h, 5, 714);
            c.set(a, h, f, h >= top - 1 ? Material.SNOW_BLOCK : seam > .6 ? Material.BLUE_ICE : seam < .34 ? Material.ICE : Material.PACKED_ICE);
        }
    }

    /** A tongue of ice frozen on its way down the face, wider at the foot. */
    private static void frozenFall(Carving c, double angle) {
        double abs = Math.abs(angle);
        int top = (int) (crest(abs, GLACIER_HIGH) * .86);
        for (int h = top; h >= 0; h--) {
            double wide = 2 + (top - h) * .09;
            double out = GLACIER - 1 - (top - h) * .04;
            for (double d = -wide; d <= wide; d += .5) for (double in = 0; in <= 3; in += .5) {
                double along = angle + d / out;
                c.set((int) Math.round(Math.sin(along) * (out + in)), h, C + (int) Math.round(Math.cos(along) * (out + in)), Math.abs(d) < wide * .4 ? Material.BLUE_ICE : Material.PACKED_ICE);
            }
        }
    }

    /** A crack back into the ice, tall and pointed, that the Warden's pack comes out of. */
    private static void rift(Carving c, double angle) {
        for (double r = GLACIER - 3; r <= GLACIER + 13; r += .5) for (double d = -5; d <= 5; d += .5) for (int h = 1; h <= 18; h++) {
            int a = (int) Math.round(Math.sin(angle) * r + Math.cos(angle) * d), f = C + (int) Math.round(Math.cos(angle) * r - Math.sin(angle) * d);
            double mouth = Math.abs(d) / 5 + h / 18D;
            if (mouth >= 1) continue;
            c.set(a, h, f, r > GLACIER + 12 ? Material.BLACK_CONCRETE : Material.AIR);
            if (h == 1) c.set(a, 0, f, Material.POLISHED_DIORITE);
        }
    }

    /** Columns of the Hall, gold and sandstone under a crust of ice: the glacier came after. */
    private static void boundColumns(Carving c) {
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI / 8 + index * Math.TAU / 8;
            int a = (int) Math.round(Math.cos(angle) * 24), f = C + (int) Math.round(Math.sin(angle) * 24);
            if (Math.abs(a) <= 7 && f < C) continue;
            int high = 11 + Math.floorMod(index * 5, 4) * 3;
            for (int h = 1; h <= high; h++) for (int da = -2; da <= 2; da++) for (int df = -2; df <= 2; df++) {
                double out = Math.hypot(da, df);
                if (out > 2.5) continue;
                boolean crust = out > 1.5 && h < high * .7 + noise(da + index, df, 716) * 5;
                if (out <= 1.5 || crust) c.set(a + da, h, f + df, crust ? Material.PACKED_ICE : h % 5 == 0 ? TRIM : BLOCK);
            }
            c.set(a, high + 1, f, GOLD);
            c.set(a, high + 2, f, Material.SEA_LANTERN);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stone: a mountain top cut into terraces
    // ---------------------------------------------------------------------------------------------

    private static final int TERRACE = 30, TREAD = 3, RISE = 3, TIERS = 6, CRAG_HIGH = 56;

    static void stone(BuildCanvas b, Carving c) {
        rock(b, c, new SampleKit.Isle(Material.MOSS_BLOCK, Material.DIRT,
            new Material[]{Material.STONE, Material.ANDESITE, Material.MOSSY_COBBLESTONE, Material.COBBLESTONE, Material.TUFF}));
        everyColumn(c, (a, f, r, angle) -> {
            if (r <= R) c.set(a, 0, f, stoneFloor(c, a, f, r, Math.atan2(f - C, a)));
            kerb(c, r, angle, a, f, Material.STONE_BRICKS, Material.POLISHED_ANDESITE);
            if (r > R && onRock(b, c, a, f)) terrace(c, a, f, r, angle);
        });
        medallion(c, 8, Material.POLISHED_ANDESITE, Material.OCHRE_FROGLIGHT);
        int seat = C + 39;
        // The cut the throne stands in, with the falls either side of it running down into a channel round the floor.
        for (int a = -27; a <= 27; a++) for (int f = C + 29; f <= seat + 17; f++) for (int h = 1; h <= 62; h++) c.set(a, h, f, Material.AIR);
        for (int a = -27; a <= 27; a++) for (int f = C + 29; f <= seat + 17; f++) c.set(a, 0, f, Math.floorMod(a * 3 + f, 7) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        for (int a = -27; a <= 27; a++) for (int h = 1; h <= 60; h++) for (int back = 0; back <= 3; back++) c.set(a, h, seat + 18 + back, smooth(a + back, h, 6, 721) > .56 ? Material.ANDESITE : noise(a * 3 + h, back, 722) < .2 ? Material.MOSSY_COBBLESTONE : Material.STONE);
        for (int side = -1; side <= 1; side += 2) {
            // The cut's walls are the raw hill, stepping in as they go back.
            for (int f = C + 29; f <= seat + 17; f++) for (int a = 25; a <= 30; a++) for (int h = 1; h <= 44 + (f - C - 29) / 2; h++) {
                if (a - 25 > (seat + 17 - f) / 3 + (int) (smooth(a, h + f, 5, 731) * 4)) continue;
                c.set(side * a, h, f, smooth(a + f, h, 6, 732) > .55 ? Material.ANDESITE : noise(a * 3 + h, f, 733) < .2 ? Material.MOSSY_COBBLESTONE : Material.STONE);
            }
            // The falls come off the hill behind the throne and run forward round its feet.
            for (int a = 19; a <= 23; a++) {
                for (int h = 1; h <= 60; h++) c.set(side * a, h, seat + 17, Material.WATER, h == 60 ? "level=0" : "level=8");
                for (int f = C + 29; f <= seat + 17; f++) {
                    c.set(side * a, -1, f, Material.STONE_BRICKS);
                    c.set(side * a, 0, f, Material.WATER, "level=0");
                }
            }
        }
        WardenStatue.sit(b, c.wx(0, seat), Y + 1, c.wz(0, seat), -c.dirX(), -c.dirZ(), WardensHall.STONE);
        trilithons(c);
        groves(c);
    }

    /** Old flags, worn and mossed, with a ring of pale stone in them. */
    private static Material stoneFloor(Carving c, int a, int f, double r, double angle) {
        double moss = smooth(c.wx(a, f), c.wz(a, f), 6, 723);
        Material knot = knotFloor(r, angle, 18, 8);
        if (knot == GOLD) return Material.POLISHED_DIORITE;
        if (knot == TRIM) return Material.CHISELED_STONE_BRICKS;
        if (moss > .64) return Material.MOSS_BLOCK;
        if (moss > .56) return Material.MOSSY_STONE_BRICKS;
        return Math.floorMod((int) (r * .4) + (int) Math.floor(angle * r * .14), 2) == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
    }

    /** One column of the bowl: terraces like an old theatre for a way out, then the raw mountain behind. */
    private static void terrace(Carving c, int a, int f, double r, double angle) {
        double crest = crest(angle, CRAG_HIGH), slip = smooth(a, f, 9, 724);
        int tier = Math.max(0, (int) ((r - TERRACE) / TREAD)), high = Math.min(TIERS, tier + 1) * RISE;
        high = (int) Math.min(high, crest);
        boolean fallen = slip > .67;
        if (fallen) high -= RISE + (int) (noise(a, f, 725) * 3);
        for (int h = 1; h <= high; h++) c.set(a, h, f, h == high ? (fallen ? Material.COBBLESTONE : Material.POLISHED_ANDESITE) : Math.floorMod(h, RISE) == 0 ? Material.CHISELED_STONE_BRICKS : noise(a + h, f, 726) < .22 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        if (high > 0 && noise(a, f, 727) < .16) c.set(a, high + 1, f, Material.MOSS_CARPET);
        if (r <= TERRACE + TIERS * TREAD + 1) return;
        // Past the top tier it stops being worked stone.
        double through = (r - TERRACE - TIERS * TREAD) / 14D;
        int crag = (int) Math.round(crest * (1 - through * through * .6) * (.62 + smooth(a, f, 6, 728) * .5));
        for (int h = high + 1; h <= crag; h++) c.set(a, h, f, h >= crag - 1 && smooth(a, f, 4, 729) > .45 ? Material.MOSS_BLOCK : smooth(a + h, f, 5, 730) > .55 ? Material.ANDESITE : Material.STONE);
    }

    /** Lintels still standing on the top tier, at the sides of the bowl. */
    private static void trilithons(Carving c) {
        for (double angle : new double[]{-1.75, 1.75, -1.3, 1.3}) {
            double r = TERRACE + TIERS * TREAD - 3;
            int a = (int) Math.round(Math.sin(angle) * r), f = C + (int) Math.round(Math.cos(angle) * r);
            int foot = (int) Math.min(TIERS * RISE, crest(Math.abs(angle), CRAG_HIGH));
            int high = 11 + Math.floorMod((int) (angle * 10), 4);
            double tangent = angle + Math.PI / 2;
            int ta = (int) Math.round(Math.sin(tangent) * 4), tf = (int) Math.round(Math.cos(tangent) * 4);
            for (int side = -1; side <= 1; side += 2) c.box(a + ta * side, a + ta * side + 1, foot + 1, foot + high, f + tf * side, f + tf * side + 1, Material.STONE_BRICKS);
            for (int d = -7; d <= 7; d++) for (int w = -1; w <= 1; w++)
                c.box(a + ta * d / 4 + w, a + ta * d / 4 + w, foot + high + 1, foot + high + 2, f + tf * d / 4 - 1, f + tf * d / 4 + 1, Material.POLISHED_ANDESITE);
        }
    }

    /** Small trees on the upper tiers, where a thousand years of leaf mould has collected. */
    private static void groves(Carving c) {
        for (int index = 0; index < 14; index++) {
            double angle = (index % 2 == 0 ? 1 : -1) * (1 + (index / 2) * .2), r = TERRACE + 4 + Math.floorMod(index * 7, 4) * TREAD;
            int a = (int) Math.round(Math.sin(angle) * r), f = C + (int) Math.round(Math.cos(angle) * r);
            int foot = (int) Math.min(Math.min(TIERS, (int) ((r - TERRACE) / TREAD) + 1) * RISE, crest(Math.abs(angle), CRAG_HIGH));
            int high = 4 + index % 3;
            c.box(a, a, foot + 1, foot + high, f, f, Material.OAK_LOG);
            for (int da = -3; da <= 3; da++) for (int dh = -2; dh <= 2; dh++) for (int df = -3; df <= 3; df++)
                if (da * da + dh * dh * 2 + df * df <= 10 && (da != 0 || df != 0 || dh > 0)) c.set(a + da, foot + high + dh, f + df, index % 3 == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES, "persistent=true");
        }
    }
}
