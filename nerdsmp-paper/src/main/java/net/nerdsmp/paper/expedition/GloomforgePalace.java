package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * The Gloomforge: a dwarven forge-palace cut into the far cliff of the Drowned Deep, older than the mine. Everything hangs
 * on one axis, the line of the causeway. You come out of the last drift looking straight down it, across the black water,
 * between stone guards, to a terrace and a flared stair, and over them a front carved from the cliff: a gate in a deep
 * stepped sleeve, two jutting pylons crowned with crystal, lava pouring down either side, and a king's face the height of
 * the wall, wedged up into the roof of the cave. Behind the gate the same axis runs on through a hall of great columns to
 * the hearth at the far end. Blue-black stone, tuff and bronze, in blobs and not noise; dark in the recesses, pale on
 * what stands proud. The hall is the Foreman's arena.
 */
final class GloomforgePalace {
    static final int FACE = 246, AXIS = 34, FLOOR = MinesTemplate.DECK, END = 292, HALF = 16, CEILING = FLOOR + 30, DOCK = MinesTemplate.DOCK;
    private static final int GRID = 9, FIRST_COLUMN = FACE + 8, ROW = 9;
    private static final Material BRONZE = Material.WAXED_EXPOSED_CUT_COPPER, VERDIGRIS = Material.WAXED_OXIDIZED_CUT_COPPER, GOLD = Material.GOLD_BLOCK;
    private static final Material TILE = Material.DEEPSLATE_TILES, BRICK = Material.DEEPSLATE_BRICKS, DRESSED = Material.POLISHED_DEEPSLATE, CARVED = Material.CHISELED_DEEPSLATE, SHADOW = Material.DEEPSLATE;
    private static final Material PALE = Material.POLISHED_TUFF, PALE_BRICK = Material.TUFF_BRICKS, CYAN = Material.CYAN_TERRACOTTA;
    private static final Material GLOW = Material.SHROOMLIGHT, EMBER = Material.MAGMA_BLOCK, PANE = Material.ORANGE_STAINED_GLASS;

    private GloomforgePalace() { }

    /** A piece of carving set out from a spot: {@code a} across, {@code h} up, {@code f} the way it faces. */
    private record Frame(BuildCanvas b, int x, int y, int z, int dirX, int dirZ) {
        int wx(int a, int f) { return x + a * dirZ + f * dirX; }

        int wz(int a, int f) { return z - a * dirX + f * dirZ; }

        void set(int a, int h, int f, Material block) { b.block(wx(a, f), y + h, wz(a, f), block); }

        void set(int a, int h, int f, Material block, String state) { b.block(wx(a, f), y + h, wz(a, f), block, state); }

        void box(int a1, int a2, int h1, int h2, int f1, int f2, Material block) {
            for (int a = a1; a <= a2; a++) for (int h = h1; h <= h2; h++) for (int f = f1; f <= f2; f++) set(a, h, f, block);
        }

        /** The same box either side of the middle. */
        void both(int a1, int a2, int h1, int h2, int f1, int f2, Material block) {
            box(a1, a2, h1, h2, f1, f2, block);
            box(-a2, -a1, h1, h2, f1, f2, block);
        }

        /** Texture in blobs, so a wall reads as one stone with patches of wear and not as static. */
        void dress(int a1, int a2, int h1, int h2, int f1, int f2) {
            for (int a = a1; a <= a2; a++) for (int h = h1; h <= h2; h++) for (int f = f1; f <= f2; f++) set(a, h, f, stone(wx(a, f), y + h, wz(a, f)));
        }
    }

    /** The palace's worked stone: blue-black brick, drifting to tile and to a cold cyan where the damp has got at it. */
    private static Material stone(int x, int y, int z) {
        double blob = (smooth(x + y, z, 9, 701) + smooth(x, z + y, 9, 702)) / 2, fine = noise(x + y * 3, z, 703);
        if (blob > .62) return fine < .25 ? CYAN : TILE;
        if (blob < .4) return fine < .2 ? Material.CRACKED_DEEPSLATE_BRICKS : SHADOW;
        return fine < .06 ? Material.CRACKED_DEEPSLATE_BRICKS : BRICK;
    }

    /** Stone that stands proud and catches the light. */
    private static Material proud(int x, int y, int z) {
        double blob = smooth(x + y, z, 7, 704);
        return blob > .6 ? PALE : blob < .38 ? Material.TUFF : PALE_BRICK;
    }

    /** The cave ends at the cliff, and the hall goes on into the rock behind it. */
    static void dig(MineKit kit) {
        // A high vault in front of the cliff: the king has to stand in open air, with the roof well over his head.
        kit.cavern(FACE - 16, 1, AXIS, 40, 60, 84, 62, 18);
        kit.stopAt(FACE);
        kit.hall(FACE + 1, AXIS - HALF, END, AXIS + HALF, FLOOR, CEILING - FLOOR);
    }

    static void build(BuildCanvas b, MineKit kit) {
        Frame front = new Frame(b, FACE, FLOOR + 1, AXIS, -1, 0);
        causeway(b);
        terrace(front);
        wings(front);
        pylon(front, 1);
        pylon(front, -1);
        gate(front);
        king(front);
        wedge(front, kit);
        lava(front);
        hall(b);
        columns(b);
        hearth(b);
        ruin(b);
    }

    // ---------------------------------------------------------------------------------------------
    // The approach
    // ---------------------------------------------------------------------------------------------

    /** A stone road on piers, straight at the gate, with a pair of guards every so often and lamps let into stone pylons. */
    private static void causeway(BuildCanvas b) {
        for (int x = 188; x <= FACE - 14; x++) {
            int bay = Math.floorMod(x - 188, 12);
            boolean pier = bay <= 2;
            for (int a = -3; a <= 3; a++) {
                int z = AXIS + a;
                b.block(x, DOCK, z, Math.abs(a) == 3 ? DRESSED : Math.floorMod(x, 3) == 0 && a == 0 ? BRONZE : Math.abs(a) <= 1 ? TILE : BRICK);
                b.block(x, DOCK - 1, z, BRICK);
                // Between the piers the road springs on stepped arches, so it's a bridge and not a wall.
                int spring = pier ? 2 : DOCK - 2 - Math.min(Math.min(bay - 2, 12 - bay) + 1, 3);
                if (pier) for (int y = spring; y < DOCK - 1; y++) b.block(x, y, z, stone(x, y, z));
                else b.block(x, DOCK - 2, z, stone(x, DOCK - 2, z));
            }
            if (pier) for (int side : new int[]{-1, 1}) {
                for (int y = 2; y <= DOCK; y++) {
                    b.block(x, y, AXIS + side * 4, y >= DOCK - 2 ? DRESSED : stone(x, y, AXIS + side * 4));
                    if (bay == 1 && y < DOCK - 2) b.block(x, y, AXIS + side * 5, stone(x, y, AXIS + side * 5));
                }
            }
            for (int side : new int[]{-1, 1}) b.block(x, DOCK + 1, AXIS + side * 3, Math.floorMod(x, 2) == 0 ? Material.DEEPSLATE_BRICK_WALL : Material.DEEPSLATE_BRICK_SLAB);
        }
        for (int x : new int[]{200, 224}) for (int side : new int[]{-1, 1}) {
            for (int dx = -5; dx <= 5; dx++) for (int dz = 4; dz <= 11; dz++) {
                b.block(x + dx, DOCK, AXIS + side * dz, Math.abs(dx) == 5 || dz == 11 ? DRESSED : Math.floorMod(dx + dz, 2) == 0 ? TILE : BRICK);
                if (Math.abs(dx) == 5 || dz == 11) for (int y = 2; y < DOCK; y++) b.block(x + dx, y, AXIS + side * dz, stone(x + dx, y, AXIS + side * dz));
            }
            for (int dx = -2; dx <= 2; dx++) b.block(x + dx, DOCK + 1, AXIS + side * 3, Material.AIR);
            guard(new Frame(b, x, DOCK + 1, AXIS + side * 8, 0, -side));
        }
        for (int x : new int[]{190, 212, 230}) for (int side : new int[]{-1, 1}) lamp(b, x, DOCK + 1, AXIS + side * 3);
    }

    /** Stone stacked on stone, stepping out and in, with the light let into it: the avenue's lamps. */
    private static void lamp(BuildCanvas b, int x, int y, int z) {
        Frame lamp = new Frame(b, x, y, z, -1, 0);
        lamp.box(-1, 1, 0, 2, -1, 1, DRESSED);
        lamp.box(0, 0, 3, 7, 0, 0, BRICK);
        lamp.box(-1, 1, 4, 4, -1, 1, Material.DEEPSLATE_BRICK_SLAB);
        lamp.box(-1, 1, 7, 8, -1, 1, proud(x, y, z));
        lamp.box(0, 0, 7, 8, 0, 0, GLOW);
        lamp.set(0, 8, 1, GLOW);
        lamp.set(0, 8, -1, GLOW);
        lamp.set(1, 8, 0, GLOW);
        lamp.set(-1, 8, 0, GLOW);
        lamp.box(-1, 1, 9, 9, -1, 1, Material.DEEPSLATE_BRICK_SLAB);
        lamp.box(0, 0, 10, 11, 0, 0, BRONZE);
    }

    /** A hooded stone dwarf, both hands on the head of a hammer planted between his feet. */
    private static void guard(Frame stone) {
        stone.box(-3, 3, 0, 1, -3, 3, DRESSED);
        stone.box(-2, 2, 1, 1, -2, 2, CARVED);
        for (int h = 2; h <= 9; h++) {
            int wide = h < 6 ? 3 : 2;
            stone.box(-wide, wide, h, h, -2, 1, h % 3 == 2 ? TILE : BRICK);
        }
        stone.both(3, 3, 7, 10, -1, 1, DRESSED);
        stone.box(-3, 3, 10, 11, -2, 1, BRICK);
        stone.box(-3, 3, 9, 9, -2, 1, BRONZE);
        stone.box(-2, 2, 12, 15, -2, 1, SHADOW);
        stone.box(-2, 2, 16, 16, -2, 0, BRICK);
        stone.box(-1, 1, 14, 14, 2, 2, GLOW);
        stone.box(-2, 2, 12, 12, 2, 2, PALE);
        stone.box(-1, 1, 10, 11, 2, 2, PALE);
        stone.box(-1, 1, 8, 9, 2, 3, PALE);
        stone.box(0, 0, 6, 7, 2, 2, GOLD);
        stone.box(0, 0, 2, 8, 3, 3, Material.POLISHED_BLACKSTONE);
        stone.box(-2, 2, 2, 4, 3, 4, Material.POLISHED_BLACKSTONE);
        stone.box(-1, 1, 3, 3, 5, 5, BRONZE);
    }

    /** The front court: three steps of plinth out of the water, a flared stair on the axis, and fire raised either side of it. */
    private static void terrace(Frame front) {
        int[][] tiers = {{-7, 24, 42}, {-5, 20, 38}, {-3, 16, 34}};
        for (int[] tier : tiers) {
            int top = tier[0], out = tier[1], wide = tier[2];
            for (int a = -wide; a <= wide; a++) for (int f = 0; f <= out; f++) {
                boolean rim = Math.abs(a) >= wide - 1 || f >= out - 1;
                if (rim) front.dress(a, a, -14, top - 1, f, f);
                front.set(a, top, f, rim ? DRESSED : TILE);
            }
            for (int a = -wide - 1; a <= wide + 1; a++) front.set(a, top, out + 1, Material.DEEPSLATE_BRICK_SLAB, "type=bottom");
            for (int f = 0; f <= out + 1; f++) for (int side : new int[]{-1, 1}) front.set(side * (wide + 1), top, f, Material.DEEPSLATE_BRICK_SLAB, "type=bottom");
        }
        for (int a = -30; a <= 30; a++) for (int f = 0; f <= 13; f++) {
            boolean rim = Math.abs(a) == 30 || f == 13;
            front.set(a, -1, f, rim ? DRESSED : knot(front.wx(a, f), front.wz(a, f)));
            for (int h = -2; h >= -3; h--) front.set(a, h, f, BRICK);
        }
        // The stair: it fans as it comes down, so you feel the whole front open up over you.
        for (int step = 0; step < 3; step++) {
            int reach = 6 + step * 2, f = 13 + step, h = -1 - step;
            for (int a = -reach; a <= reach; a++) {
                front.set(a, h, f, Material.DEEPSLATE_BRICK_STAIRS, "facing=east,half=bottom,shape=straight");
                for (int under = h - 1; under >= -14; under--) front.set(a, under, f, BRICK);
            }
        }
        for (int side : new int[]{-1, 1}) {
            for (int f = 11; f <= 14; f++) for (int a = 11; a <= 13; a++) front.dress(side * a, side * a, -1, 0, f, f);
            brazier(front, side * 12, 3, 12);
        }
    }

    /** A bowl of fire on a stem, high enough to be seen over a crowd. */
    private static void brazier(Frame at, int a, int h, int f) {
        at.box(a, a, h - 2, h, f, f, Material.POLISHED_BLACKSTONE);
        at.box(a - 1, a + 1, h + 1, h + 1, f - 1, f + 1, BRONZE);
        at.box(a - 1, a + 1, h + 2, h + 2, f - 1, f + 1, EMBER);
        at.set(a, h + 3, f, Material.FIRE);
        at.set(a - 1, h + 3, f, Material.FIRE);
        at.set(a + 1, h + 3, f, Material.FIRE);
        at.set(a, h + 3, f - 1, Material.FIRE);
        at.set(a, h + 3, f + 1, Material.FIRE);
    }

    /** Square knotwork: every tile a key pattern, as if the floor were one long line that never ends. */
    private static Material knot(int x, int z) {
        int u = Math.floorMod(x, 12), v = Math.floorMod(z, 12);
        boolean edge = u == 0 || v == 0;
        boolean key = u == 2 && v >= 2 && v <= 10 || v == 10 && u >= 2 && u <= 10 || u == 10 && v >= 4 && v <= 10 || v == 4 && u >= 5 && u <= 10 || u == 5 && v >= 4 && v <= 7 || v == 2 && u >= 2 && u <= 8;
        return edge ? DRESSED : key ? PALE : Math.floorMod(x + z, 2) == 0 ? TILE : BRICK;
    }

    // ---------------------------------------------------------------------------------------------
    // The front
    // ---------------------------------------------------------------------------------------------

    private static Material proudAt(Frame front, int a, int h, int f) { return proud(front.wx(a, f), front.y() + h, front.wz(a, f)); }

    /** The long low arcades either side, dark in the arches, each with a lit window deep in it. */
    private static void wings(Frame front) {
        for (int side : new int[]{-1, 1}) {
            for (int a = 25; a <= 44; a++) {
                front.dress(side * a, side * a, 0, 20, 0, 3);
                front.set(side * a, 21, 3, DRESSED);
                front.set(side * a, 21, 2, BRONZE);
                for (int h = 22; h <= 24; h++) for (int f = -2; f <= 2 - (h - 22); f++) front.set(side * a, h, f, Math.floorMod(a, 2) == 0 ? TILE : BRICK);
            }
            for (int bay = 0; bay < 3; bay++) {
                int middle = 28 + bay * 6;
                for (int h = 0; h <= 16; h++) for (int across = -2; across <= 2; across++) {
                    boolean arch = h <= 12 || Math.abs(across) <= 2 - (h - 13);
                    if (!arch) continue;
                    front.set(side * (middle + across), h, 3, Material.AIR);
                    front.set(side * (middle + across), h, 2, Material.AIR);
                    front.set(side * (middle + across), h, 1, h < 11 && Math.abs(across) <= 1 ? PANE : SHADOW);
                    front.set(side * (middle + across), h, 0, h < 11 && Math.abs(across) <= 1 ? GLOW : SHADOW);
                }
                for (int h = 0; h <= 18; h++) front.set(side * (middle + 3), h, 4, proudAt(front, side * (middle + 3), h, 4));
            }
        }
    }

    /** A tower of the front: it juts out from the cliff, its corners stand proud of its faces, and it steps in as it climbs to a crown of crystal. */
    private static void pylon(Frame front, int side) {
        int inner = 13, outer = 24, out = 11, eave = 34;
        for (int a = inner; a <= outer; a++) for (int f = 0; f <= out; f++) {
            int s = side * a;
            boolean corner = (a <= inner + 2 || a >= outer - 2) && f >= out - 2;
            boolean face = a == inner || a == outer || f == out;
            if (corner) for (int h = 0; h <= eave + 5; h++) front.set(s, h, f, Math.floorMod(h, 7) == 0 ? DRESSED : proudAt(front, s, h, f));
            else if (face) for (int h = 0; h <= eave; h++) front.set(s, h, f - (f == out ? 1 : 0), stone(front.wx(s, f), front.y() + h, front.wz(s, f)));
            front.set(s, eave + 1, Math.min(f, out - 1), DRESSED);
        }
        // Fluted faces: ribs a block proud, dark between, the way a Moria column reads from a distance.
        for (int a = inner + 3; a <= outer - 3; a++) for (int h = 2; h <= eave - 1; h++) {
            boolean rib = Math.floorMod(a, 2) == 0;
            front.set(side * a, h, out, rib ? (h % 9 == 0 ? BRONZE : proudAt(front, a, h, out)) : Material.AIR);
            if (!rib) front.set(side * a, h, out - 1, SHADOW);
        }
        // Slits of firelight up the front, in a grid.
        for (int row = 0; row < 3; row++) for (int a : new int[]{inner + 4, outer - 4}) {
            int low = 6 + row * 9, slit = side * (Math.floorMod(a, 2) == 0 ? a + 1 : a);
            for (int h = low; h <= low + 4; h++) front.set(slit, h, out - 1, GLOW);
        }
        // It steps in, one course at a time, and the eye goes up with it.
        for (int step = 0; step < 6; step++) {
            int h = eave + 2 + step;
            for (int a = inner + step; a <= outer - step; a++) for (int f = 0; f <= out - 1 - step; f++) {
                boolean rim = a == inner + step || a == outer - step || f == out - 1 - step;
                if (rim) front.set(side * a, h, f, step % 2 == 0 ? BRICK : TILE);
            }
        }
        int cx = side * (inner + outer) / 2;
        MineProps.crystals(front.b(), front.wx(cx, 4), front.y() + eave + 8, front.wz(cx, 4), 14, MineProps.BLUE, side + 40);
        for (int a : new int[]{inner + 1, outer - 1}) brazier(front, side * a, eave + 7, out - 1);
    }

    /** The gate in its sleeve: frame within frame, each one darker and further in, to the doors and the hammer over them. */
    private static void gate(Frame front) {
        // The sleeve, stepping in from the face of the wall.
        for (int ring = 0; ring < 4; ring++) {
            int f = 4 - ring * 2, half = 12 - ring * 2, spring = 16 - ring * 2, top = spring + half - 3;
            for (int a = -half - 1; a <= half + 1; a++) for (int h = 0; h <= top + 2; h++) {
                int inside = h <= spring ? half : half - (h - spring);
                if (Math.abs(a) <= inside && inside > 0) {
                    front.box(a, a, h, h, f - 1, f, Material.AIR);
                    continue;
                }
                if (Math.abs(a) <= inside + 2) front.box(a, a, h, h, f - 1, f, ring == 0 ? (h > spring ? BRONZE : DRESSED) : ring == 1 ? PALE_BRICK : SHADOW);
            }
        }
        // The wall the doors stand in: dark, with the hammer of the old kings on it.
        for (int a = -7; a <= 7; a++) for (int h = 0; h <= 16; h++) for (int f = -4; f <= -3; f++) front.set(a, h, f, SHADOW);
        front.box(-4, 4, 0, 7, -5, -2, Material.AIR);
        front.box(-5, 5, 8, 8, -3, -2, GOLD);
        front.both(5, 5, 0, 7, -3, -2, BRONZE);
        front.box(-3, 3, 11, 13, -2, -2, Material.POLISHED_BLACKSTONE);
        front.box(-4, 4, 11, 13, -3, -3, GOLD);
        front.box(0, 0, 9, 10, -2, -2, BRONZE);
    }

    /** How far the old king stands out of the wall at a spot on his face, in blocks: a relief, carved as one surface. */
    private static double relief(int a, int h) {
        double side = Math.abs(a);
        if (h >= 44) return 3 + 5 * Math.sqrt(Math.max(0, 1 - Math.pow(side / 15, 2) - Math.pow((h - 44) / 15D, 2)));
        if (h >= 42) return side <= 14 ? 9 - side / 5 : 0;
        boolean socket = side >= 3 && side <= 9 && h >= 38;
        if (socket) return 3;
        if (side <= 2 && h >= 32) return 7 + (42 - h) / 4D - side / 2;
        if (h >= 36) return side <= 12 ? 6 - side / 4 : 0;
        if (h >= 34) return side <= 12 ? 8 - side / 3 : 0;
        double wide = 12 - (34 - h) * .55;
        return side <= wide ? 7 - (34 - h) / 10D - side / 6 + (Math.floorMod(a, 4) == 0 ? -1 : 0) : 0;
    }

    /** The old king, carved out of the front, looking down the causeway at whoever's coming. */
    private static void king(Frame front) {
        for (int a = -15; a <= 15; a++) for (int h = 24; h <= 59; h++) {
            int out = (int) Math.round(relief(a, h));
            if (out <= 0) continue;
            for (int f = 0; f <= out; f++) {
                boolean surface = f == out;
                Material block;
                if (h >= 44) block = surface && Math.abs(a) <= 1 ? GOLD : surface && Math.floorMod(h, 5) == 0 ? BRONZE : surface ? Material.DEEPSLATE_TILES : DRESSED;
                else if (h >= 42) block = surface ? BRONZE : DRESSED;
                else if (h >= 34) block = surface ? PALE : PALE_BRICK;
                else block = !surface ? PALE_BRICK : Math.floorMod(a, 4) == 0 ? PALE_BRICK : Math.floorMod(h, 4) == 0 ? BRONZE : Material.CALCITE;
                front.set(a, h, f, block);
            }
        }
        // Fire deep in the sockets, and a line of it for a mouth.
        for (int side : new int[]{-1, 1}) {
            for (int a = 4; a <= 8; a++) for (int h = 39; h <= 40; h++) front.set(side * a, h, 3, a >= 5 && a <= 7 ? GLOW : EMBER);
            for (int a = 3; a <= 9; a++) front.set(side * a, 37, 4, SHADOW);
        }
        for (int a = -4; a <= 4; a++) front.set(a, 34, (int) Math.round(relief(a, 34)), EMBER);
        // Gold clasps on the plaits, a crest on the helm, and horns swept back into the rock.
        for (int a : new int[]{-6, -2, 2, 6}) front.box(a, a, 27, 28, (int) Math.round(relief(a, 28)) + 1, (int) Math.round(relief(a, 28)) + 1, GOLD);
        front.box(-1, 1, 59, 63, 1, 5, GOLD);
        front.both(15, 19, 46, 48, 1, 5, VERDIGRIS);
        front.both(20, 22, 48, 51, 1, 4, VERDIGRIS);
        front.both(22, 24, 51, 55, 1, 3, VERDIGRIS);
        front.both(24, 25, 55, 59, 1, 2, GOLD);
    }

    /** Masonry shouldered up into the roof of the cave, so the front grows out of the rock and doesn't stand in front of it. */
    private static void wedge(Frame front, MineKit kit) {
        for (int a = -60; a <= 60; a++) for (int f = 0; f <= 9; f++) {
            // Behind the crown of the helm and over the towers it comes down to meet the masonry; out at the sides it falls to the wings.
            int over = Math.abs(a) <= 28 ? 65 + (int) ((smooth(a + 40, f, 6, 706) - .5) * 8) + f * 2 : 26 + (60 - Math.abs(a)) / 2 + f * 3;
            for (int h = over; h <= 64; h++) {
                int x = front.wx(a, f), y = front.y() + h, z = front.wz(a, f);
                if (kit.open(x, y, z) && CanopyKit.empty(front.b(), x, y, z)) front.b().block(x, y, z, smooth(x + y, z, 8, 705) > .5 ? Material.DEEPSLATE : Material.COBBLED_DEEPSLATE);
            }
        }
    }

    /** The forge's overflow, running down the front either side of the gate and away along the terrace in stone channels. */
    private static void lava(Frame front) {
        for (int side : new int[]{-1, 1}) {
            int a = side * 12;
            for (int h = 30; h >= 0; h--) for (int f = 4; f <= 5; f++) front.set(a, h, f, Material.LAVA, h == 30 ? "level=0" : "level=8");
            front.box(a - 1, a + 1, 31, 32, 3, 6, BRONZE);
            front.box(a, a, -1, -1, 4, 10, Material.LAVA);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The hall
    // ---------------------------------------------------------------------------------------------

    private static void hall(BuildCanvas b) {
        for (int x = FACE + 1; x <= END; x++) for (int z = AXIS - HALF; z <= AXIS + HALF; z++) {
            b.block(x, FLOOR, z, knot(x, z));
            int off = Math.abs(z - AXIS);
            if (off == HALF) for (int y = FLOOR + 1; y <= CEILING; y++) {
                boolean bay = Math.floorMod(x - FIRST_COLUMN, GRID) <= 1;
                b.block(x, y, z, bay ? (y % 8 == 0 ? BRONZE : DRESSED) : y > FLOOR + 22 ? SHADOW : stone(x, y, z));
            }
            // A run of slag down each aisle, behind the columns, so the hall's lit from its edges.
            if (off == HALF - 2 || off == HALF - 3) {
                b.block(x, FLOOR, z, Material.LAVA, "level=0");
                b.block(x, FLOOR - 1, z, EMBER);
            }
            b.block(x, CEILING, z, Math.floorMod(x, GRID) == 0 || Math.floorMod(z - AXIS, GRID) == 0 ? DRESSED : SHADOW);
        }
        for (int x = FACE + 4; x <= END; x += 8) for (int z : new int[]{AXIS - 4, AXIS + 4}) b.block(x, FLOOR + 14, z, Material.LIGHT, "level=15");
        // A low kerb keeps the slag off the floor of the aisles.
        for (int x = FACE + 1; x <= END - 3; x++) for (int side : new int[]{-1, 1}) b.block(x, FLOOR + 1, AXIS + side * (HALF - 4), Math.floorMod(x, 9) == 0 ? BRONZE : Material.DEEPSLATE_BRICK_WALL);
        // The ore skips the old masters left hanging, on chains as long as the hall is high.
        for (int[] skip : new int[][]{{262, 30}, {278, 30}, {262, 38}, {278, 38}}) {
            for (int y = FLOOR + 11; y < CEILING; y++) b.block(skip[0], y, skip[1], Material.IRON_CHAIN, "axis=y");
            b.block(skip[0], FLOOR + 10, skip[1], Material.RAW_IRON_BLOCK);
        }
    }

    /** Two rows of great columns: a labyrinth at the foot, a narrow fluted shaft, and a capital that opens in steps into the roof. */
    private static void columns(BuildCanvas b) {
        for (int x = FIRST_COLUMN; x < END - 8; x += GRID) for (int side : new int[]{-1, 1}) {
            int z = AXIS + side * ROW;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                b.block(x + dx, FLOOR + 1, z + dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? DRESSED : CARVED);
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) b.block(x + dx, FLOOR + 2, z + dz, BRONZE);
            }
            for (int y = FLOOR + 3; y < CEILING; y++) {
                int flare = y < FLOOR + 21 ? 0 : (y - FLOOR - 19) / 2, shaft = 2 + flare;
                for (int dx = -shaft; dx <= shaft; dx++) for (int dz = -shaft; dz <= shaft; dz++) {
                    boolean chamfer = Math.abs(dx) == shaft && Math.abs(dz) == shaft;
                    if (chamfer && flare == 0) continue;
                    if (flare > 0 && Math.abs(dx) < shaft && Math.abs(dz) < shaft) continue;
                    boolean flute = flare == 0 && (Math.floorMod(dx, 2) == 0 && Math.abs(dz) == shaft || Math.floorMod(dz, 2) == 0 && Math.abs(dx) == shaft);
                    b.block(x + dx, y, z + dz, flare > 0 ? (y % 2 == 0 ? DRESSED : TILE) : flute ? PALE : SHADOW);
                }
                if (y == FLOOR + 19) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) == 3 || Math.abs(dz) == 3) b.block(x + dx, y, z + dz, GOLD);
            }
        }
    }

    /** The far end: the great hearth, a grate as tall as a house with the fire behind it. */
    private static void hearth(BuildCanvas b) {
        Frame end = new Frame(b, END, FLOOR + 1, AXIS, -1, 0);
        end.dress(-HALF, HALF, 0, CEILING - FLOOR - 1, 0, 2);
        for (int h = 0; h <= 12; h++) {
            int half = h <= 8 ? 8 : 8 - (h - 8);
            for (int a = -half; a <= half; a++) {
                end.set(a, h, 2, Material.IRON_BARS, "north=true,south=true");
                end.box(a, a, h, h, 0, 1, h < 2 ? EMBER : Material.AIR);
                end.set(a, h, -1, h % 3 == 0 ? GLOW : EMBER);
            }
            for (int side : new int[]{-1, 1}) end.set(side * (half + 1), h, 3, h % 2 == 0 ? BRONZE : DRESSED);
        }
        end.box(-10, 10, 13, 14, 3, 3, GOLD);
    }

    /** Three thousand years: fallen stone along the foot of everything, and the floor broken where it's taken the weight. */
    private static void ruin(BuildCanvas b) {
        for (int x = FACE + 1; x <= END - 4; x++) for (int z = AXIS - HALF + 1; z <= AXIS + HALF - 1; z++) {
            if (b.get(x, FLOOR, z) == Material.LAVA) continue;
            if (noise(x, z, 706) < .04 && Math.abs(z - AXIS) > 6) b.block(x, FLOOR, z, Material.CRACKED_DEEPSLATE_TILES);
            if (noise(z, x, 707) < .018 && Math.abs(z - AXIS) > 6 && CanopyKit.empty(b, x, FLOOR + 1, z)) b.block(x, FLOOR + 1, z, Material.COBBLED_DEEPSLATE_SLAB, "type=bottom");
        }
    }
}
