package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;

/**
 * The Gloomforge itself: a dwarven forge-palace older than the mine, standing on a stepped plinth in the black water
 * where the Foreman's digging broke through. Everything is square, heavy and symmetrical: a nave of battered walls and
 * corbelled arches under a stepped roof, stone kings leaning on their hammers either side of the gate, a hearth the size
 * of a house with a king's face over it, runnels of light cut through the floor, and a throne at the far end. Blue-black
 * stone, bronze and gold, so it can't be mistaken for the Foreman's black and red fortress up the mine. The nave is
 * the fight's arena: it runs north to south, entered through the west gate.
 */
final class GloomforgePalace {
    static final int X = 236, FLOOR = MinesTemplate.DECK;
    /** How far out the plinth runs, for anything that shouldn't grow through it. */
    static final int LEFT = 208, RIGHT = 264, TOP = -4, BOTTOM = 96;
    private static final int WEST = 225, EAST = 247, NORTH = 30, SOUTH = 77, DAIS = FLOOR + 3, EAVE = 38, RIDGE = 47;
    private static final int[] RIBS = {37, 45, 53, 61, 69};
    private static final Material BRONZE = Material.WAXED_EXPOSED_CUT_COPPER, VERDIGRIS = Material.WAXED_OXIDIZED_CUT_COPPER, GOLD = Material.GOLD_BLOCK;
    private static final Material TILE = Material.DEEPSLATE_TILES, BRICK = Material.DEEPSLATE_BRICKS, DRESSED = Material.POLISHED_DEEPSLATE, CARVED = Material.CHISELED_DEEPSLATE;
    private static final Material GLOW = Material.SHROOMLIGHT, EMBER = Material.MAGMA_BLOCK;

    private GloomforgePalace() { }

    /** A piece of carving set out from where it stands: {@code a} across, {@code h} up, {@code f} the way it faces. */
    private record Frame(BuildCanvas b, int x, int y, int z, int dirX, int dirZ) {
        void set(int a, int h, int f, Material block) { b.block(x + a * dirZ + f * dirX, y + h, z + a * dirX * -1 + f * dirZ, block); }

        void box(int a1, int a2, int h1, int h2, int f1, int f2, Material block) {
            for (int a = a1; a <= a2; a++) for (int h = h1; h <= h2; h++) for (int f = f1; f <= f2; f++) set(a, h, f, block);
        }

        /** The same box on both sides of the middle. */
        void both(int a1, int a2, int h1, int h2, int f1, int f2, Material block) {
            box(a1, a2, h1, h2, f1, f2, block);
            box(-a2, -a1, h1, h2, f1, f2, block);
        }
    }

    static void build(BuildCanvas b, MineKit kit) {
        plinth(b);
        floor(b);
        walls(b);
        vault(b);
        hearth(b, kit);
        dais(b);
        gate(b);
        towers(b);
        gantries(b);
        for (int cz : new int[]{21, 47}) statue(new Frame(b, 220, FLOOR + 1, cz, -1, 0));
        lights(b);
    }

    private static Material course(int x, int y, int z) {
        if (Math.floorMod(y, 4) == 0) return DRESSED;
        double roll = noise(x + y, z, 701);
        return roll < .12 ? Material.CRACKED_DEEPSLATE_BRICKS : roll < .3 ? TILE : BRICK;
    }

    /** The great stepped footing, four courses out of the water. */
    private static void plinth(BuildCanvas b) {
        int[][] tiers = {{10, 16}, {12, 11}, {14, 6}, {FLOOR, 1}};
        for (int[] tier : tiers) {
            int top = tier[0], out = tier[1], x1 = WEST - 2 - out, x2 = EAST + 2 + out, z1 = 12 - out, z2 = 80 + out;
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                boolean rim = x <= x1 + 1 || x >= x2 - 1 || z <= z1 + 1 || z >= z2 - 1;
                if (rim) for (int y = 2; y < top; y++) b.block(x, y, z, course(x, y, z));
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                b.block(x, top, z, edge ? DRESSED : noise(x, z, 702) < .02 ? Material.MOSS_BLOCK : noise(z, x, 703) < .2 ? BRICK : TILE);
            }
            // Dressed stairs round every step, so no corner of it is a raw cube.
            for (int x = x1 - 1; x <= x2 + 1; x++) for (int z : new int[]{z1 - 1, z2 + 1}) b.block(x, top, z, Material.DEEPSLATE_BRICK_SLAB, "type=bottom");
            for (int z = z1; z <= z2; z++) for (int x : new int[]{x1 - 1, x2 + 1}) b.block(x, top, z, Material.DEEPSLATE_BRICK_SLAB, "type=bottom");
        }
    }

    /** Tile between borders, a runnel of light under glass down each side and across at each vent, and the anvil sigil where the Foreman stands. */
    private static void floor(BuildCanvas b) {
        for (int x = WEST; x <= EAST; x++) for (int z = NORTH + 1; z < SOUTH; z++) {
            boolean border = x == WEST + 1 || x == EAST - 1, seam = Math.floorMod(z - 37, 8) == 0 || Math.floorMod(x - X, 8) == 0;
            b.block(x, FLOOR, z, border || seam ? DRESSED : Math.floorMod(x + z, 2) == 0 ? TILE : BRICK);
            boolean along = Math.abs(x - X) == 5 && z < 63, across = (z == 39 || z == 50 || z == 60) && x > WEST + 1 && x < EAST - 1;
            if (along || across) {
                b.block(x, FLOOR, z, Material.ORANGE_STAINED_GLASS);
                b.block(x, FLOOR - 1, z, GLOW);
            }
            double from = Math.hypot(x - X, z - 49);
            if (from > 6.4 && from < 7.4) b.block(x, FLOOR, z, BRONZE);
            if (from < 1.6) b.block(x, FLOOR, z, GOLD);
            else if (from < 3.4 && (x == X || z == 49)) b.block(x, FLOOR, z, BRONZE);
        }
    }

    /** Side walls: three thick, battered at the foot, with a pilaster at every rib and a tall window in every bay. */
    private static void walls(BuildCanvas b) {
        for (int side : new int[]{-1, 1}) for (int z = NORTH; z <= SOUTH; z++) {
            int inner = side < 0 ? WEST : EAST;
            for (int y = FLOOR + 1; y <= EAVE; y++) for (int depth = 0; depth < 3; depth++) b.block(inner + side * depth, y, z, course(inner + depth, y, z));
            boolean pilaster = false;
            for (int rib : RIBS) pilaster |= Math.abs(z - rib) <= 1;
            int outer = inner + side * 3;
            // The foot of the wall spreads, course by course.
            for (int y = FLOOR + 1; y <= FLOOR + 3; y++) b.block(outer, y, z, y == FLOOR + 3 ? DRESSED : BRICK);
            b.block(outer, FLOOR + 4, z, Material.DEEPSLATE_BRICK_STAIRS, "facing=" + (side < 0 ? "east" : "west") + ",half=bottom,shape=straight");
            b.block(inner - side, EAVE - 3, z, DRESSED);
            b.block(inner + side * 2, EAVE, z, DRESSED);
            if (pilaster) {
                for (int y = FLOOR + 1; y <= EAVE; y++) {
                    b.block(outer, y, z, Math.floorMod(z - 37, 8) == 0 ? DRESSED : BRICK);
                    b.block(inner - side, y, z, y > FLOOR + 3 && y % 6 == 0 ? CARVED : DRESSED);
                }
                b.block(outer, EAVE + 1, z, BRONZE);
            } else {
                int off = Math.abs(z - (33 + 8 * Math.floorDiv(z - 29, 8)));
                if (off <= 1) for (int y = FLOOR + 7; y <= FLOOR + 17 - off * 2; y++) {
                    if (side < 0 && z >= 33 && z <= 35) continue;
                    for (int depth = 0; depth < 3; depth++) b.block(inner + side * depth, y, z, depth == 1 ? Material.ORANGE_STAINED_GLASS : Material.AIR);
                }
                if (off == 2) for (int y = FLOOR + 7; y <= FLOOR + 14; y++) b.block(outer, y, z, CARVED);
            }
            // A band of runes and a band of bronze along the whole wall.
            b.block(outer, FLOOR + 6, z, Math.floorMod(z, 3) == 0 ? CARVED : DRESSED);
            b.block(outer, EAVE - 1, z, BRONZE);
        }
    }

    private static int step(int x) { return Math.min(x - WEST, EAST - x); }

    /** Corbelled ribs that step in to a flat crown, and a stepped roof over them. Dwarves didn't trust the curve. */
    private static void vault(BuildCanvas b) {
        for (int x = WEST - 2; x <= EAST + 2; x++) {
            int up = Math.max(0, step(x));
            int roof = EAVE + Math.min(RIDGE - EAVE, up);
            for (int z = NORTH; z <= SOUTH; z++) {
                b.block(x, roof, z, DRESSED);
                b.block(x, roof + 1, z, up >= RIDGE - EAVE ? VERDIGRIS : TILE);
                if (up < RIDGE - EAVE) b.block(x, roof + 2, z, Material.DEEPSLATE_TILE_STAIRS, "facing=" + (x < X ? "east" : "west") + ",half=bottom,shape=straight");
            }
            for (int rib : RIBS) for (int dz = 0; dz <= 1; dz++) {
                int under = EAVE - 4 + Math.min(RIDGE - EAVE, up);
                for (int y = under; y < roof; y++) b.block(x, y, rib + dz, up % 2 == 0 ? DRESSED : BRICK);
                if (x > WEST && x < EAST) b.block(x, under - 1, rib + dz, Material.DEEPSLATE_BRICK_STAIRS, "facing=" + (x < X ? "west" : "east") + ",half=top,shape=straight");
            }
            if (x >= X - 1 && x <= X + 1) for (int rib : RIBS) for (int dz = 0; dz <= 1; dz++) b.block(x, EAVE - 4 + (RIDGE - EAVE), rib + dz, GOLD);
        }
        // Each end closes on a stepped gable.
        for (int x = WEST - 2; x <= EAST + 2; x++) for (int y = EAVE; y < EAVE + Math.min(RIDGE - EAVE, Math.max(0, step(x))); y++) {
            b.block(x, y, NORTH, BRICK);
            b.block(x, y, SOUTH, course(x, y, SOUTH));
        }
    }

    private static void face(Frame head) {
        head.box(-5, 5, 0, 8, -3, 1, BRICK);
        // The helm, its brow and cheek guards.
        head.box(-6, 6, 7, 10, -3, 2, DRESSED);
        head.box(-7, 7, 7, 7, -3, 3, BRONZE);
        head.box(-1, 1, 11, 13, -2, 2, GOLD);
        head.box(-2, 2, 11, 11, -3, 3, BRONZE);
        head.both(6, 6, 2, 6, 1, 2, DRESSED);
        // Deep-set eyes, a heavy nose, and the beard in stepped plaits.
        head.box(-5, 5, 5, 6, 2, 2, BRICK);
        head.both(2, 3, 5, 5, 2, 2, EMBER);
        head.box(-1, 1, 3, 5, 2, 3, DRESSED);
        for (int down = 0; down < 8; down++) {
            int wide = Math.max(1, 5 - down / 2);
            head.box(-wide, wide, 2 - down, 2 - down, 2, 3, down % 3 == 2 ? BRONZE : Material.CALCITE);
        }
        head.box(-1, 1, -6, -5, 2, 3, GOLD);
        head.both(8, 9, 8, 9, -1, 1, VERDIGRIS);
        head.both(10, 10, 9, 12, -1, 1, VERDIGRIS);
        head.both(11, 11, 12, 14, 0, 0, VERDIGRIS);
    }

    /** The hearth: a block of masonry filling the north end, a fire behind a grate, and an old king's face above it. */
    private static void hearth(BuildCanvas b, MineKit kit) {
        for (int x = WEST - 2; x <= EAST + 2; x++) for (int z = 14; z <= NORTH; z++) {
            boolean rim = x <= WEST - 1 || x >= EAST + 1 || z <= 15 || z == NORTH;
            for (int y = FLOOR; y <= EAVE + 2; y++) if (rim || y >= EAVE) b.block(x, y, z, course(x, y, z));
            b.block(x, EAVE + 3, z, Material.DEEPSLATE_TILE_SLAB, "type=bottom");
        }
        // The mouth, stepping in at the top as a dwarven door does.
        for (int y = FLOOR + 1; y <= FLOOR + 10; y++) {
            int half = y <= FLOOR + 6 ? 6 : 6 - (y - FLOOR - 6);
            for (int x = X - half; x <= X + half; x++) {
                b.block(x, y, NORTH, Material.IRON_BARS, "east=true,west=true");
                for (int z = 22; z < NORTH; z++) b.block(x, y, z, y == FLOOR + 1 ? EMBER : Material.AIR);
            }
            for (int x : new int[]{X - half - 1, X + half + 1}) b.block(x, y, NORTH, y % 2 == 0 ? BRONZE : DRESSED);
            for (int x = X - half; x <= X + half; x++) b.block(x, y, 21, y % 3 == 0 ? GLOW : EMBER);
        }
        for (int x = X - 8; x <= X + 8; x++) for (int y = FLOOR + 11; y <= FLOOR + 12; y++) b.block(x, y, NORTH, y == FLOOR + 11 ? GOLD : BRONZE);
        for (int y = FLOOR + 1; y <= FLOOR + 10; y++) for (int side : new int[]{-1, 1}) {
            b.block(X + side * 8, y, NORTH + 1, y == FLOOR + 10 ? GOLD : y % 4 == 0 ? CARVED : DRESSED);
            b.block(X + side * 9, y, NORTH + 1, y % 3 == 0 ? BRONZE : DRESSED);
        }
        face(new Frame(b, X, FLOOR + 17, NORTH + 1, 0, 1));
        // Twin bronze bellows pipes, floor to crown, either side of the fire.
        for (int side : new int[]{-1, 1}) for (int y = FLOOR + 1; y <= EAVE + 4; y++) for (int dx = 0; dx <= 1; dx++) b.block(X + side * 15 + dx, y, NORTH + 1, y % 5 == 0 ? GOLD : VERDIGRIS);
        // The stack, climbing until it meets the roof of the cavern.
        for (int y = EAVE + 3; y < 62; y++) {
            int half = y < 50 ? 6 : 5;
            boolean through = false;
            for (int x = X - half; x <= X + half; x++) for (int z = 22 - half; z <= 22 + half; z++) {
                if (!kit.open(x, y, z)) continue;
                through = true;
                boolean wall = Math.abs(x - X) == half || Math.abs(z - 22) == half;
                if (wall) b.block(x, y, z, Math.floorMod(y, 6) == 0 ? BRONZE : course(x, y, z));
                else b.block(x, y, z, y > 56 ? Material.AIR : y % 5 == 0 ? EMBER : Material.AIR);
            }
            if (!through) break;
        }
    }

    /** Three steps up to the far end, the Foreman's heap of gold, and a stone throne with nobody in it for three thousand years. */
    private static void dais(BuildCanvas b) {
        for (int step = 0; step < 3; step++) for (int x = WEST; x <= EAST; x++) {
            b.block(x, FLOOR + step, 63 + step, DRESSED);
            b.block(x, FLOOR + step + 1, 63 + step, Material.DEEPSLATE_BRICK_STAIRS, "facing=south,half=bottom,shape=straight");
        }
        for (int x = WEST; x <= EAST; x++) for (int z = 66; z < SOUTH; z++) {
            for (int y = FLOOR; y < DAIS; y++) b.block(x, y, z, BRICK);
            b.block(x, DAIS, z, Math.abs(x - X) <= 2 ? BRONZE : Math.floorMod(x + z, 2) == 0 ? TILE : DRESSED);
        }
        Frame throne = new Frame(b, X, DAIS + 1, 74, 0, -1);
        throne.box(-3, 3, 0, 1, -1, 2, DRESSED);
        throne.box(-4, 4, 0, 13, -2, -1, BRICK);
        throne.box(-3, 3, 13, 14, -2, -1, GOLD);
        throne.box(-1, 1, 15, 17, -2, -1, GOLD);
        throne.both(4, 5, 0, 4, -1, 3, BRICK);
        throne.both(4, 5, 5, 5, -1, 3, BRONZE);
        throne.box(-2, 2, 2, 2, 0, 1, Material.RED_WOOL);
    }

    /** The west gate, the one way in: a stepped door in a thick frame, with bronze and gold over it. */
    private static void gate(BuildCanvas b) {
        for (int z = 29; z <= 39; z++) for (int y = FLOOR + 1; y <= FLOOR + 13; y++) {
            int off = Math.abs(z - 34);
            int top = FLOOR + 8 + (off <= 1 ? 1 : 0);
            boolean opening = off <= 1 && y <= FLOOR + 4;
            for (int x = WEST - 4; x <= WEST; x++) {
                if (opening) b.block(x, y, z, Material.AIR);
                else if (x == WEST - 4 && off <= 4 && y <= top + 3) b.block(x, y, z, off == 4 || y == FLOOR + 5 ? BRONZE : y == FLOOR + 6 ? CARVED : y >= top ? GOLD : DRESSED);
            }
        }
        for (int z = 30; z <= 38; z++) for (int y = FLOOR - 1; y <= FLOOR; y++) if (Math.abs(z - 34) > 1) b.block(WEST - 4, y, z, DRESSED);
        for (int z = 31; z <= 37; z++) for (int y = FLOOR + 5; y <= FLOOR + 7; y++) b.block(WEST - 5, y, z, y == FLOOR + 6 ? Math.floorMod(z, 2) == 0 ? GOLD : CARVED : BRONZE);
        // A stepped frame round the opening, in the old way.
        for (int y = FLOOR + 1; y <= FLOOR + 4; y++) for (int z : new int[]{32, 36}) b.block(WEST - 5, y, z, y == FLOOR + 4 ? BRONZE : DRESSED);
    }

    /** A square tower at each corner, stepping in as it climbs, with a gold spike on it. */
    private static void towers(BuildCanvas b) {
        for (int[] tower : new int[][]{{WEST - 6, 9, 46}, {EAST + 6, 9, 46}, {WEST - 6, 82, 46}, {EAST + 6, 82, 46}, {EAST + 7, 45, 52}}) {
            int cx = tower[0], cz = tower[1], top = tower[2];
            for (int y = FLOOR - 1; y <= top + 3; y++) {
                int half = y < top - 12 ? 4 : y < top ? 3 : top + 3 - y;
                for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
                    boolean wall = Math.abs(dx) == half || Math.abs(dz) == half;
                    if (!wall) { if (y % 8 == 0) b.block(cx + dx, y, cz + dz, DRESSED); continue; }
                    boolean slit = y > FLOOR + 5 && y < top - 2 && Math.floorMod(y, 7) < 3 && (dx == 0 || dz == 0);
                    b.block(cx + dx, y, cz + dz, slit ? Material.ORANGE_STAINED_GLASS : Math.floorMod(y, 12) == 0 ? BRONZE : course(cx + dx, y, cz + dz));
                }
            }
            for (int y = top + 4; y <= top + 9; y++) b.block(cx, y, cz, y >= top + 7 ? GOLD : BRONZE);
        }
    }

    /** Bronze gantry rails under the crown, with the ore skips hung from them on chains. */
    private static void gantries(BuildCanvas b) {
        for (int x : new int[]{228, 244}) {
            for (int z = NORTH + 1; z < SOUTH; z++) b.block(x, 34, z, BRONZE);
            for (int rib : RIBS) for (int y = 35; y <= 36; y++) b.block(x, y, rib, Material.IRON_CHAIN, "axis=y");
        }
        for (int[] skip : new int[][]{{228, 44}, {244, 44}, {228, 56}, {244, 56}}) {
            for (int y = FLOOR + 11; y < 34; y++) b.block(skip[0], y, skip[1], Material.IRON_CHAIN, "axis=y");
            b.block(skip[0], FLOOR + 10, skip[1], Material.RAW_IRON_BLOCK);
        }
    }

    /** One stone king: boots planted, both hands on the pommel of a hammer as tall as a man's chest, looking down the water at whoever's coming. */
    private static void statue(Frame king) {
        king.box(-8, 8, -2, -1, -5, 8, DRESSED);
        king.box(-9, 9, -2, -2, -6, 9, Material.DEEPSLATE_BRICK_SLAB);
        king.both(2, 6, 0, 2, -4, 4, Material.POLISHED_BLACKSTONE);
        king.both(2, 6, 3, 3, -3, 4, BRONZE);
        for (int h = 4; h <= 9; h++) {
            int wide = 7 - (h - 4) / 3;
            king.box(-wide, wide, h, h, -3, 3, h % 2 == 0 ? BRICK : TILE);
        }
        king.box(-6, 6, 10, 11, -4, 4, BRONZE);
        king.box(-2, 2, 10, 11, 5, 5, GOLD);
        king.box(-7, 7, 12, 18, -4, 3, BRICK);
        king.box(-5, 5, 13, 17, 4, 4, DRESSED);
        king.both(8, 11, 16, 19, -4, 3, DRESSED);
        king.both(9, 11, 20, 20, -3, 2, VERDIGRIS);
        king.both(9, 10, 11, 15, -2, 2, BRICK);
        king.both(9, 10, 11, 11, -2, 2, BRONZE);
        king.both(3, 9, 10, 12, 3, 6, BRICK);
        king.box(-3, 3, 10, 13, 6, 8, Material.POLISHED_BLACKSTONE);
        king.box(-1, 1, 14, 14, 6, 8, GOLD);
        // The hammer, head on the ground.
        king.box(-1, 1, 3, 9, 6, 7, DRESSED);
        king.box(-4, 4, 0, 3, 5, 8, Material.POLISHED_BLACKSTONE);
        king.box(-5, 5, 1, 2, 5, 8, GOLD);
        king.box(-5, 5, 19, 22, -3, 3, BRICK);
        face(new Frame(king.b(), king.x(), king.y() + 18, king.z(), king.dirX(), king.dirZ()));
    }

    private static void lights(BuildCanvas b) {
        for (int z = 35; z <= 74; z += 8) for (int x = 230; x <= 242; x += 6) b.block(x, 30, z, Material.LIGHT, "level=15");
        for (int rib : RIBS) for (int side : new int[]{-1, 1}) {
            int x = (side < 0 ? WEST + 2 : EAST - 2);
            b.block(x, FLOOR + 8, rib, EMBER);
            b.block(x, FLOOR + 9, rib, Material.FIRE);
        }
        // Great rings of lamps hung down the nave.
        for (int cz : new int[]{41, 57}) {
            for (int y = 28; y < RIDGE; y++) b.block(X, y, cz, Material.IRON_CHAIN, "axis=y");
            for (double angle = 0; angle < Math.TAU; angle += .08) b.block(X + (int) Math.round(Math.cos(angle) * 4), 27, cz + (int) Math.round(Math.sin(angle) * 4), BRONZE);
            for (int out = -3; out <= 3; out++) {
                b.block(X + out, 27, cz, BRONZE);
                b.block(X, 27, cz + out, BRONZE);
            }
            for (double angle = 0; angle < Math.TAU; angle += Math.PI / 4) b.block(X + (int) Math.round(Math.cos(angle) * 4), 26, cz + (int) Math.round(Math.sin(angle) * 4), Material.LANTERN, "hanging=true");
        }
    }
}
