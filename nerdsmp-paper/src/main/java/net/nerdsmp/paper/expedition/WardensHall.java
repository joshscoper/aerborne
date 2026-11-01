package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.WardenStatue.Dress;
import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.*;
import static net.nerdsmp.paper.expedition.WardensTemplate.*;

/**
 * The High Gate and the Hall of Seals behind it. From the stair the eye goes up the gate between its towers to the Stone
 * Warden in the great niche, and over his head to the dome, so everything in front of the dome stops below the line from
 * the landing to its crown.
 */
final class WardensHall {
    private static final int FRONT = 6, TOWER_IN = 10, TOWER_OUT = 20, TOWER_BACK = 9, TOWER_FORE = -4, TOWER_TOP = 66, SPIRE = 22;
    private static final int NICHE_LOW = 13, NICHE_TOP = 46, BLOCK_TOP = 48;
    static final int DRUM = 31, INSIDE = 28, DRUM_TOP = 104, DOME_HIGH = 48, COLUMNS = 16, COLUMN_RING = 23, GALLERY = 21;
    static final int PLATE_RING = 17, PLATE_RADIUS = 3;
    private static final Material ROOF = Material.WAXED_OXIDIZED_CUT_COPPER, ROOF_B = Material.WAXED_WEATHERED_CUT_COPPER, GOLD = Material.GOLD_BLOCK;
    static final Dress EMBER = new Dress(Material.POLISHED_BLACKSTONE, Material.BLACKSTONE, GOLD, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
    static final Dress FROST = new Dress(Material.PACKED_ICE, Material.BLUE_ICE, Material.IRON_BLOCK, Material.DIAMOND_BLOCK, Material.SEA_LANTERN);
    /** Pale stone, for where he has to stand out against ice. */
    static final Dress FROST_PALE = new Dress(Material.CALCITE, Material.POLISHED_DIORITE, Material.IRON_BLOCK, Material.DIAMOND_BLOCK, Material.SEA_LANTERN);
    static final Dress STONE = new Dress(Material.STONE_BRICKS, Material.ANDESITE, GOLD, Material.POLISHED_DIORITE, Material.OCHRE_FROGLIGHT);

    private WardensHall() { }

    static void build(BuildCanvas b) {
        island(b, 0, Y, HALL_Z, DRUM + 6, 58, HALL_ROCK);
        SkyRuins.hang(b, 0, Y, HALL_Z, DRUM + 6, 58);
        facade(b);
        for (int side = -1; side <= 1; side += 2) tower(b, side);
        colossi(b);
        drum(b);
        dome(b);
        interior(b);
    }

    private static void facade(BuildCanvas b) {
        Carving c = new Carving(b, 0, Y, FRONT, 0, 1);
        c.box(-TOWER_IN, TOWER_IN, 0, 0, 0, TOWER_BACK, PAVE);
        for (int a = -TOWER_IN + 1; a <= TOWER_IN - 1; a++) for (int h = 1; h <= BLOCK_TOP; h++) for (int f = 0; f <= TOWER_BACK; f++)
            c.set(a, h, f, h % 16 == 0 ? TRIM : Math.floorMod(h, 4) == 0 ? BLOCK : PAVE);
        // The pediment: a low gable over the niche, its edge picked out in gold.
        for (int h = 1; h <= 7; h++) {
            int half = TOWER_IN - 1 - h;
            for (int a = -half; a <= half; a++) for (int f = 0; f <= 3; f++) c.set(a, BLOCK_TOP + h, f, Math.abs(a) >= half - 1 ? GOLD : BLOCK);
            for (int f = 4; f <= TOWER_BACK; f++) for (int a = -half; a <= half; a++) c.set(a, BLOCK_TOP + h, f, ROOF);
        }
        niche(c);
        door(c);
        // The passage in, gold at the ceiling.
        c.box(-3, 3, 1, 8, 0, TOWER_BACK, Material.AIR);
        c.box(-3, 3, 9, 9, 0, TOWER_BACK, Material.AIR);
        for (int a = -3; a <= 3; a++) for (int f = 0; f <= TOWER_BACK; f++) c.set(a, 10, f, a == 0 ? GOLD : TRIM);
        c.box(-3, 3, 1, 9, 2, 2, Material.WAXED_COPPER_BLOCK);
        for (int h = 2; h <= 9; h += 2) for (int a = -3; a <= 3; a += 3) c.set(a, h, 2, Material.WAXED_CHISELED_COPPER);
    }

    /** The door's great round head, with rays of gold above the bronze. */
    private static void door(Carving c) {
        int spring = 10;
        for (int a = -9; a <= 9; a++) for (int h = 1; h <= 20; h++) {
            double r = Math.hypot(a, Math.max(0, h - spring));
            if (r > 8.4) continue;
            int depth = r > 7.2 ? -1 : r > 6 ? 0 : 1;
            Material block = r > 7.2 ? TRIM : r > 6 ? BLOCK : h > spring - 1 ? Math.floorMod(a, 2) == 0 ? GOLD : Material.RAW_GOLD_BLOCK : BLOCK;
            c.set(a, h, depth, block);
            for (int f = depth - 1; f >= 0; f--) c.set(a, h, f, Material.AIR);
        }
    }

    /** The tall recess the Stone Warden stands in, with the sun behind him. */
    private static void niche(Carving c) {
        int half = 8, spring = NICHE_TOP - half;
        for (int a = -half; a <= half; a++) for (int h = NICHE_LOW; h <= NICHE_TOP; h++) {
            if (Math.hypot(a, Math.max(0, h - spring)) > half + .3) continue;
            for (int f = -1; f <= 5; f++) c.set(a, h, f, Material.AIR);
            double rays = Math.atan2(h - 28, a), r = Math.hypot(a, h - 28);
            c.set(a, h, 6, Math.abs(Math.sin(rays * 8)) < .3 ? GOLD : r < 6 ? Material.RAW_GOLD_BLOCK : Material.LAPIS_BLOCK);
        }
        for (int a = -half - 1; a <= half + 1; a++) for (int h = NICHE_LOW - 1; h <= NICHE_TOP + 1; h++) {
            double r = Math.hypot(a, Math.max(0, h - spring));
            if (r > half + .3 && r < half + 1.5) c.set(a, h, 0, Math.floorMod(h, 3) == 0 ? GOLD : TRIM);
        }
        // A balcony to stand him on, carried on steps of stone over the door.
        c.box(-half, half, NICHE_LOW - 1, NICHE_LOW - 1, -3, 5, BLOCK);
        for (int a = -half; a <= half; a++) {
            c.set(a, NICHE_LOW - 2, -2, TRIM);
            c.set(a, NICHE_LOW - 2, -1, BLOCK);
        }
        for (int a = -half; a <= half; a += 2) c.set(a, NICHE_LOW, -3, GOLD);
    }

    private static void tower(BuildCanvas b, int side) {
        Carving c = new Carving(b, 0, Y, FRONT, 0, 1);
        for (int ring = TOWER_IN; ring <= TOWER_OUT; ring++) for (int f = TOWER_FORE; f <= TOWER_BACK; f++) for (int h = 0; h <= TOWER_TOP; h++) {
            boolean corner = (ring == TOWER_IN || ring == TOWER_OUT) && (f == TOWER_FORE || f == TOWER_BACK);
            if (corner) continue;
            boolean edge = ring <= TOWER_IN + 1 || ring >= TOWER_OUT - 1 || f <= TOWER_FORE + 1 || f >= TOWER_BACK - 1;
            c.set(side * ring, h, f, h % 16 == 0 ? TRIM : edge && Math.floorMod(h, 6) < 2 ? BLOCK : PAVE);
        }
        // The foot steps out from the shaft.
        for (int ring = TOWER_IN - 1; ring <= TOWER_OUT + 1; ring++) for (int f = TOWER_FORE - 1; f <= TOWER_BACK + 1; f++) for (int h = 1; h <= 5; h++) {
            boolean shell = ring < TOWER_IN || ring > TOWER_OUT || f < TOWER_FORE || f > TOWER_BACK;
            if (shell && ring >= TOWER_IN - (f < 0 ? 0 : 1) && (h < 5 || (ring + f) % 2 == 0)) c.set(side * ring, h, f, h == 5 ? GOLD : BLOCK);
        }
        int mid = (TOWER_IN + TOWER_OUT) / 2;
        // Two tiers of windows up the north face, each lit from behind, with a gold hood.
        for (int[] tier : new int[][]{{20, 30}, {36, 48}}) {
            for (int h = tier[0]; h <= tier[1]; h++) for (int a = -1; a <= 1; a++) {
                c.set(side * (mid + a), h, TOWER_FORE, h >= tier[1] - 1 && a != 0 ? PAVE : Material.YELLOW_STAINED_GLASS);
                c.set(side * (mid + a), h, TOWER_FORE + 1, Material.OCHRE_FROGLIGHT);
            }
            for (int a = -2; a <= 2; a++) c.set(side * (mid + a), tier[1] + 1, TOWER_FORE - 1, GOLD);
            for (int h = tier[0] - 1; h <= tier[1]; h++) for (int a = -2; a <= 2; a += 4) c.set(side * (mid + a), h, TOWER_FORE - 1, TRIM);
            for (int a = -2; a <= 2; a++) c.set(side * (mid + a), tier[0] - 1, TOWER_FORE - 1, TRIM);
        }
        // Pilasters up the corners of the face, tied together by a gold string course at each storey.
        for (int h = 6; h < TOWER_TOP - 9; h++) for (int ring : new int[]{TOWER_IN + 1, TOWER_OUT - 1}) {
            c.set(side * ring, h, TOWER_FORE - 1, h % 16 == 0 ? GOLD : TRIM);
            c.set(side * (ring > mid ? ring + 1 : ring - 1), h, TOWER_FORE, BLOCK);
        }
        for (int h = 16; h < TOWER_TOP - 9; h += 16) for (int ring = TOWER_IN + 1; ring <= TOWER_OUT - 1; ring++) if (ring < mid - 3 || ring > mid + 3 || h == 32) c.set(side * ring, h, TOWER_FORE - 1, GOLD);
        // A red banner on each tower below the windows, pointed at the foot.
        for (int h = 18; h >= 7; h--) {
            int point = h < 10 ? 10 - h : 0;
            for (int a = -3 + point; a <= 3 - point; a++) c.set(side * (mid + a), h, TOWER_FORE - 1, a == 0 || Math.abs(a) == 3 ? Material.YELLOW_WOOL : Material.RED_WOOL);
        }
        for (int a = -4; a <= 4; a++) c.set(side * (mid + a), 19, TOWER_FORE - 1, GOLD);
        belvedere(c, side, mid);
        spire(c, side, mid);
    }

    /** The open lantern at the top of a tower. */
    private static void belvedere(Carving c, int side, int mid) {
        int back = (TOWER_FORE + TOWER_BACK) / 2;
        for (int h = TOWER_TOP - 8; h <= TOWER_TOP - 2; h++) for (int a = -3; a <= 3; a++) {
            c.set(side * (mid + a), h, TOWER_FORE, Material.AIR);
            c.set(side * (mid + a), h, TOWER_FORE + 1, Material.AIR);
            c.set(side * (mid + a), h, TOWER_BACK, Material.AIR);
            c.set(side * (mid + a), h, TOWER_BACK - 1, Material.AIR);
        }
        c.box(side > 0 ? mid - 2 : -mid - 2, side > 0 ? mid + 2 : -mid + 2, TOWER_TOP - 8, TOWER_TOP - 2, back - 2, back + 2, Material.RAW_GOLD_BLOCK);
        for (int ring = TOWER_IN - 1; ring <= TOWER_OUT + 1; ring++) for (int f = TOWER_FORE - 1; f <= TOWER_BACK + 1; f++) {
            c.set(side * ring, TOWER_TOP + 1, f, Math.floorMod(ring + f, 2) == 0 ? GOLD : TRIM);
            c.set(side * ring, TOWER_TOP - 9, f, TRIM);
        }
    }

    private static void spire(Carving c, int side, int mid) {
        int back = (TOWER_FORE + TOWER_BACK) / 2;
        for (int up = 0; up < SPIRE; up++) {
            int half = 7 - up / 3;
            for (int a = -half; a <= half; a++) for (int f = -half; f <= half; f++) {
                boolean rib = Math.abs(a) == Math.abs(f);
                c.set(side * (mid + a), TOWER_TOP + 2 + up, back + f, rib ? GOLD : Math.floorMod(up, 3) == 0 ? ROOF_B : ROOF);
            }
        }
        for (int up = 0; up < 3; up++) c.set(side * mid, TOWER_TOP + 2 + SPIRE + up, back, GOLD);
    }

    /** Ember and Frost stand either side of the gate on their own rocks, Stone in the niche above the door. */
    private static void colossi(BuildCanvas b) {
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 34, z = -4;
            island(b, x, Y, z, 12, 24, HALL_ROCK);
            SkyRuins.hang(b, x, Y, z, 12, 24);
            for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 14) continue;
                boolean inner = Math.abs(dx) <= 9 && Math.abs(dz) <= 9 && Math.abs(dx) + Math.abs(dz) <= 13;
                for (int h = 1; h <= 3; h++) if (inner) b.block(x + dx, Y + h, z + dz, h == 3 ? TRIM : BLOCK);
            }
            WardenStatue.stand(b, x, Y + 4, z, 0, -1, side < 0 ? EMBER : FROST);
        }
        WardenStatue.stand(b, 0, Y + NICHE_LOW, FRONT + 2, 0, -1, STONE);
    }

    private static double angleOf(int x, int z) { return Math.atan2(z - HALL_Z, x); }

    /** How far this column is along the wall from the nearest of {@code bays} marks, in blocks. */
    private static double fromBay(double angle, double r, int bays, double start) {
        double bay = Math.TAU / bays, off = ((angle - start) % bay + bay) % bay;
        return Math.min(off, bay - off) * r;
    }

    private static void drum(BuildCanvas b) {
        for (int x = -DRUM - 3; x <= DRUM + 3; x++) for (int z = HALL_Z - DRUM - 3; z <= HALL_Z + DRUM + 3; z++) {
            double r = Math.hypot(x, z - HALL_Z), angle = angleOf(x, z);
            if (r < INSIDE - .5 || r > DRUM + 2.5) continue;
            if (r > DRUM + .5) {
                // A ledge round the foot of the dome, and a plinth round the foot of the drum.
                for (int h = 1; h <= 3; h++) b.block(x, Y + h, z, h == 3 ? TRIM : BLOCK);
                b.block(x, DRUM_TOP + 1, z, GOLD);
                b.block(x, DRUM_TOP, z, BLOCK);
                continue;
            }
            double pier = fromBay(angle, r, COLUMNS, Math.PI / COLUMNS), window = fromBay(angle, r, COLUMNS, 0);
            for (int h = 1; h <= DRUM_TOP - Y; h++) {
                int y = Y + h;
                boolean arched = window < 2 && h >= 24 && Math.hypot(window, Math.max(0, h - 36)) < 2.2;
                if (arched) {
                    b.block(x, y, z, r > 29 && r < 30 ? Material.YELLOW_STAINED_GLASS : Material.AIR);
                    continue;
                }
                boolean hood = window < 3.4 && h >= 24 && Math.hypot(window, Math.max(0, h - 36)) < 3.4;
                Material block = pier < 1.2 ? BLOCK : h % 20 == 1 ? TRIM : Math.floorMod(h, 4) == 0 ? COURSE : PAVE;
                b.block(x, y, z, hood && r > DRUM - .9 ? GOLD : block);
            }
        }
    }

    private static double domeY(double r, double radius, double high) { return DRUM_TOP + high * Math.sqrt(Math.max(0, 1 - Math.pow(r / radius, 2))); }

    /** A tall shell over the drum, gold ribs on green copper outside and stars on blue inside. */
    private static void dome(BuildCanvas b) {
        for (int x = -DRUM - 1; x <= DRUM + 1; x++) for (int z = HALL_Z - DRUM - 1; z <= HALL_Z + DRUM + 1; z++) {
            double r = Math.hypot(x, z - HALL_Z), angle = angleOf(x, z);
            if (r > DRUM + .3 || r < 4) continue;
            int top = (int) Math.round(domeY(Math.min(r, DRUM), DRUM + .3, DOME_HIGH));
            int under = r < INSIDE - .5 ? (int) Math.round(domeY(r, INSIDE, DOME_HIGH - 3)) : DRUM_TOP;
            double rib = fromBay(angle, r, COLUMNS, Math.PI / COLUMNS);
            for (int y = under; y <= top; y++) {
                boolean outer = y >= top - 2;
                Material block;
                if (outer) block = rib < 1.1 ? GOLD : smooth(x + y, z, 5, 621) > .55 ? ROOF_B : ROOF;
                else if (rib < 1.1) block = BLOCK;
                else block = noise(x * 3 + y, z, 622) < .035 ? GOLD : Material.LAPIS_BLOCK;
                b.block(x, y, z, block);
            }
        }
        int crown = DRUM_TOP + DOME_HIGH - 1;
        // The lantern on the crown, with the shaft of daylight under it.
        for (int x = -6; x <= 6; x++) for (int z = HALL_Z - 6; z <= HALL_Z + 6; z++) {
            double r = Math.hypot(x, z - HALL_Z);
            if (r > 6.4) continue;
            if (r >= 3.6) for (int y = crown; y <= crown + 7; y++) b.block(x, y, z, r > 5.4 && Math.floorMod(Math.round(angleOf(x, z) * 3), 2) == 0 ? GOLD : r > 5.4 ? Material.GLASS : TRIM);
            b.block(x, crown + 8, z, r > 5.4 ? GOLD : ROOF);
        }
        for (int up = 0; up < 8; up++) {
            int half = 5 - up * 2 / 3;
            for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) if (Math.hypot(x, z) <= half + .4) b.block(x, crown + 9 + up, HALL_Z + z, up % 2 == 0 ? ROOF : ROOF_B);
        }
        for (int up = 0; up < 4; up++) b.block(0, crown + 17 + up, HALL_Z, GOLD);
    }

    private static void interior(BuildCanvas b) {
        for (int x = -INSIDE; x <= INSIDE; x++) for (int z = HALL_Z - INSIDE; z <= HALL_Z + INSIDE; z++) {
            double dz = z - HALL_Z, r = Math.hypot(x, dz);
            if (r > INSIDE - .4) continue;
            b.block(x, Y, z, r > 19 ? (r > 24 ? BLOCK : knotFloor(r, Math.atan2(dz, x), 21.5, 12)) : knotFloor(r, Math.atan2(dz, x), 13, 9));
        }
        dais(b);
        gallery(b);
        for (int x = -24; x <= 24; x += 8) for (int z = HALL_Z - 24; z <= HALL_Z + 24; z += 8) {
            if (Math.hypot(x, z - HALL_Z) > 26) continue;
            b.block(x, Y + 8, z, Material.LIGHT, "level=15,waterlogged=false");
            b.block(x, Y + 40, z, Material.LIGHT, "level=15,waterlogged=false");
        }
        chandelier(b);
        wallDoors(b);
        for (int[] spot : new int[][]{{-1, 0}, {1, 0}, {0, 1}, {0, -1}}) banner(new Carving(b, 0, Y, HALL_Z, spot[0], spot[1]), spot[0] < 0 ? Material.ORANGE_WOOL : spot[0] > 0 ? Material.LIGHT_BLUE_WOOL : spot[1] > 0 ? Material.GREEN_WOOL : Material.RED_WOOL);
        b.block(5, Y + 1, 22, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    /** The raised floor in the middle, and the three seal plates set round it. */
    private static void dais(BuildCanvas b) {
        for (int x = -10; x <= 10; x++) for (int z = HALL_Z - 10; z <= HALL_Z + 10; z++) {
            double r = Math.hypot(x, z - HALL_Z);
            if (r > 9.5) continue;
            if (r > 8.5) b.block(x, Y + 1, z, Material.SMOOTH_SANDSTONE_SLAB, "type=bottom");
            else {
                b.block(x, Y + 1, z, r > 7.4 ? GOLD : BLOCK);
                if (r < 5.5) b.block(x, Y + 2, z, r > 4.5 ? Material.SMOOTH_SANDSTONE_SLAB : BLOCK, r > 4.5 ? "type=bottom" : "");
            }
        }
        medallion(b, 0, Y + 2, HALL_Z, 4);
        plate(b, -PLATE_RING, HALL_Z, Material.MAGMA_BLOCK, Material.SHROOMLIGHT);
        plate(b, PLATE_RING, HALL_Z, Material.BLUE_ICE, Material.SEA_LANTERN);
        plate(b, 0, HALL_Z + PLATE_RING, Material.MOSS_BLOCK, Material.OCHRE_FROGLIGHT);
    }

    /** A seal's plate on the floor. */
    static void plate(BuildCanvas b, int cx, int cz, Material ring, Material heart) {
        for (int x = cx - PLATE_RADIUS; x <= cx + PLATE_RADIUS; x++) for (int z = cz - PLATE_RADIUS; z <= cz + PLATE_RADIUS; z++) {
            double r = Math.hypot(x - cx, z - cz);
            if (r <= PLATE_RADIUS + .3) b.block(x, Y, z, r < 1.2 ? heart : r > PLATE_RADIUS - .9 ? GOLD : ring);
        }
    }

    /** Sixteen round columns carrying a gallery all the way round, with an aisle behind them. */
    private static void gallery(BuildCanvas b) {
        int deck = GALLERY;
        for (int x = -INSIDE; x <= INSIDE; x++) for (int z = HALL_Z - INSIDE; z <= HALL_Z + INSIDE; z++) {
            double r = Math.hypot(x, z - HALL_Z);
            if (r < COLUMN_RING - 1.4 || r > INSIDE - .4) continue;
            b.block(x, Y + deck, z, r < COLUMN_RING - .6 ? GOLD : BLOCK);
            b.block(x, Y + deck - 1, z, r < COLUMN_RING + .6 ? TRIM : Material.AIR);
            if (r < COLUMN_RING - .4) {
                b.block(x, Y + deck + 1, z, BLOCK);
                b.block(x, Y + deck + 2, z, Material.SMOOTH_SANDSTONE_SLAB, "type=bottom");
            }
        }
        for (int index = 0; index < COLUMNS; index++) {
            double angle = (index + .5) * Math.TAU / COLUMNS;
            int cx = (int) Math.round(Math.cos(angle) * COLUMN_RING), cz = HALL_Z + (int) Math.round(Math.sin(angle) * COLUMN_RING);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                double r = Math.hypot(dx, dz);
                for (int h = 1; h < deck; h++) {
                    double wide = h <= 2 ? 3.2 : h >= deck - 2 ? 3.2 : 2.4;
                    if (r > wide) continue;
                    b.block(cx + dx, Y + h, cz + dz, h <= 2 || h >= deck - 2 ? (h == 1 || h == deck - 1 ? GOLD : TRIM) : Math.floorMod(h, 5) == 0 ? BLOCK : PAVE);
                }
            }
            // A lamp on the aisle side, where the hall needs it.
            int lx = (int) Math.round(Math.cos(angle) * (COLUMN_RING + 3)), lz = HALL_Z + (int) Math.round(Math.sin(angle) * (COLUMN_RING + 3));
            b.block(lx, Y + 16, lz, Material.OCHRE_FROGLIGHT);
        }
    }

    private static void chandelier(BuildCanvas b) {
        int ring = 11, high = 46;
        for (int x = -ring - 1; x <= ring + 1; x++) for (int z = -ring - 1; z <= ring + 1; z++) {
            double r = Math.hypot(x, z);
            if (Math.abs(r - ring) < .8) b.block(x, Y + high, HALL_Z + z, GOLD);
            if (Math.abs(r - ring) < .5 && Math.floorMod(x * 3 + z, 4) == 0) b.block(x, Y + high - 1, HALL_Z + z, Material.LANTERN, "hanging=true");
        }
        for (int[] arm : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            int top = (int) domeY(Math.hypot(arm[0], arm[1]), INSIDE, DOME_HIGH - 3);
            for (int y = Y + high + 1; y < top; y++) b.block(arm[0], y, HALL_Z + arm[1], Material.IRON_CHAIN, "axis=y");
        }
    }

    /** The doors out to the wings, framed the same, and the great one in. */
    private static void wallDoors(BuildCanvas b) {
        for (int[] way : new int[][]{{-1, 0}, {1, 0}, {0, 1}, {0, -1}}) {
            Carving c = new Carving(b, 0, Y, HALL_Z, way[0], way[1]);
            for (int f = INSIDE - 2; f <= DRUM + 3; f++) {
                c.box(-3, 3, 1, 9, f, f, Material.AIR);
                c.box(-3, 3, 0, 0, f, f, f % 2 == 0 ? PAVE : COURSE);
                c.set(0, 0, f, GOLD);
                c.box(-3, 3, 10, 10, f, f, TRIM);
            }
            for (int h = 1; h <= 12; h++) for (int a = 4; a <= 5; a++) {
                c.set(a, h, INSIDE - 3, h == 12 ? GOLD : TRIM);
                c.set(-a, h, INSIDE - 3, h == 12 ? GOLD : TRIM);
            }
            for (int a = -5; a <= 5; a++) for (int h = 11; h <= 12; h++) c.set(a, h, INSIDE - 3, h == 12 ? GOLD : TRIM);
        }
    }

    /** A long cloth hung from the gallery over each door, in its wing's colour. */
    private static void banner(Carving c, Material cloth) {
        int f = 21;
        for (int a = -3; a <= 3; a++) for (int h = GALLERY - 1; h >= 7; h--) {
            int point = h < 11 ? 11 - h : 0;
            if (Math.abs(a) > 3 - point) continue;
            boolean edge = Math.abs(a) == 3 && point == 0, stripe = a == 0 || h == GALLERY - 3;
            c.set(a, h, f, edge || stripe ? Material.YELLOW_WOOL : cloth);
        }
    }
}
