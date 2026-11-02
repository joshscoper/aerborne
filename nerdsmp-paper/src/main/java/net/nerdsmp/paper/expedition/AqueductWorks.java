package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.AqueductTemplate.*;
import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The works under the bluff, one room leading to the next: the Pump House with its beam engines, the Cistern under it with
 * its forest of columns, the Pressure Gallery, and Floodgate Control. The bluff's rock is only a skin. What matters is the
 * inside of each room, so each has a look of its own: pipes and pistons, then brick vaults over black water, then tanks.
 */
final class AqueductWorks {
    static final int HALL_NORTH = 110, HALL_SOUTH = 172, HALL_HALF = 26, HALL_TOP = Y + 30;
    static final int CISTERN_HALF = 32, CISTERN_TOP = DEEP + 15, GALLERY_NORTH = 176, GALLERY_SOUTH = 216, GALLERY_HALF = 16, GALLERY_TOP = DEEP + 18;
    static final int CONTROL_NORTH = 220, CONTROL_SOUTH = 238;
    private static final Material BRICK = Material.BRICKS, PIPE = Material.WAXED_WEATHERED_COPPER, GRATE = Material.WAXED_OXIDIZED_COPPER_GRATE;
    private static final Material IRON = Material.POLISHED_BLACKSTONE, CAST = Material.POLISHED_DEEPSLATE, PLATE = Material.DEEPSLATE_TILES, BRASS = Material.GOLD_BLOCK;

    private AqueductWorks() { }

    static void build(BuildCanvas b) {
        bluff(b);
        cistern(b);
        pumpHouse(b);
        gallery(b);
        control(b);
    }

    /** A hill of rock the works are dug into, taller than the pump house so it sits in it, on a quay where the span comes in. */
    private static void bluff(BuildCanvas b) {
        for (int x = -46; x <= 46; x++) for (int z = 100; z <= 112; z++) for (int y = LAKE - 1; y <= Y; y++) {
            if (Math.abs(x) <= 6 && z <= SPAN_END) continue;
            int bay = Math.floorMod(x + 4, 16) - 8;
            boolean arch = z == 100 && Math.hypot(bay, Math.max(0, y - 46)) < 5 && y > LAKE;
            b.block(x, y, z, arch ? Material.AIR : y == Y ? WORN : z == 100 ? stone(x, y, z) : BEDS);
        }
        for (int x = -68; x <= 68; x++) for (int z = 96; z <= 250; z++) {
            double dome = Math.hypot(x / 68D, (z - 173) / 77D);
            if (dome > 1) continue;
            int top = (int) Math.round(LAKE + 56 * Math.pow(1 - dome * dome, .6) + (smooth(x, z, 9, 921) - .5) * 12);
            if (Math.abs(x) < HALL_HALF + 6 && z < HALL_NORTH + 2) continue;
            int low = top;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                double near = Math.hypot((x + step[0]) / 68D, (z + step[1] - 173) / 77D);
                low = Math.min(low, near > 1 ? LAKE : (int) (LAKE + 56 * Math.pow(1 - near * near, .6)));
            }
            for (int y = top; y >= Math.max(LAKE - 1, low - 4); y--) {
                double band = smooth(x + z, y * 3, 11, 922);
                b.block(x, y, z, y == top && top > 60 ? (smooth(x, z, 7, 923) > .45 ? Material.GRASS_BLOCK : Material.MOSS_BLOCK) : y < LAKE + 3 ? Material.MUD : band > .56 ? Material.ANDESITE : band > .42 ? Material.STONE : Material.TUFF);
            }
        }
    }

    /** Walls, floor and flat roof of a plain box of a room, {@code thick} deep. */
    private static void shell(BuildCanvas b, int half, int north, int south, int floor, int top, Material wall, int thick) {
        for (int x = -half - thick; x <= half + thick; x++) for (int z = north - thick; z <= south + thick; z++) for (int y = floor - 1; y <= top + 1; y++) {
            boolean inside = Math.abs(x) <= half && z >= north && z <= south && y >= floor && y <= top;
            if (!inside) b.block(x, y, z, wall);
            else if (b.get(x, y, z) != null) b.block(x, y, z, Material.AIR);
        }
    }

    private static void pumpHouse(BuildCanvas b) {
        shell(b, HALL_HALF, HALL_NORTH, HALL_SOUTH, Y + 1, HALL_TOP, WORN, 2);
        for (int x = -HALL_HALF; x <= HALL_HALF; x++) for (int z = HALL_NORTH; z <= HALL_SOUTH; z++) {
            boolean drain = Math.abs(x) <= 1 || Math.floorMod(z - 4, 12) == 0;
            b.block(x, Y, z, drain ? GRATE : Math.floorMod(x + z, 2) == 0 ? PLATE : CAST);
        }
        front(b);
        hallWalls(b);
        for (int side = -1; side <= 1; side += 2) for (int z : new int[]{HALL_NORTH + 16, HALL_NORTH + 42}) engine(b, side, z);
        // Mains along the ceiling, and rungs of pipe down the walls.
        for (int z = HALL_NORTH; z <= HALL_SOUTH; z++) for (int side = -1; side <= 1; side += 2) {
            for (int y = HALL_TOP - 4; y <= HALL_TOP - 2; y++) for (int x = HALL_HALF - 2; x <= HALL_HALF; x++) b.block(side * x, y, z, Math.floorMod(z, 10) == 0 ? IRON : PIPE);
        }
        // Iron roof trusses, one to every bay.
        for (int z = HALL_NORTH + 6; z <= HALL_SOUTH - 2; z += 13) for (int x = -HALL_HALF; x <= HALL_HALF; x++) {
            int sag = (HALL_HALF - Math.abs(x)) / 5;
            b.block(x, HALL_TOP - sag, z, IRON);
            if (Math.floorMod(x, 6) == 0) for (int y = HALL_TOP - sag; y <= HALL_TOP; y++) b.block(x, y, z, IRON);
            b.block(x, HALL_TOP, z, IRON);
        }
        for (int x = -HALL_HALF + 6; x <= HALL_HALF - 6; x += 10) for (int z = HALL_NORTH + 6; z <= HALL_SOUTH - 6; z += 10) {
            b.block(x, Y + 10, z, Material.LIGHT, "level=14,waterlogged=false");
            b.block(x, Y + 22, z, Material.LIGHT, "level=12,waterlogged=false");
        }
        // A travelling crane over the floor, with its chains hanging.
        for (int x = -HALL_HALF; x <= HALL_HALF; x++) for (int y = HALL_TOP - 8; y <= HALL_TOP - 7; y++) {
            b.block(x, y, 141, IRON);
            b.block(x, y, 143, IRON);
        }
        for (int y = Y + 14; y <= HALL_TOP - 9; y++) for (int x : new int[]{-4, 4}) b.block(x, y, 142, Material.IRON_CHAIN, "axis=y");
        b.box(-5, Y + 13, 141, 5, Y + 13, 143, IRON);
        b.box(-5, Y + 12, 142, 5, Y + 12, 142, PIPE);
        // The stair down, along the east aisle, behind the gate.
        for (int step = 0; step <= 19; step++) {
            int z = 130 + step * 2, y = Y - step;
            for (int x = 16; x <= 20; x++) {
                for (int up = 0; up <= 5; up++) for (int d = 0; d <= 1; d++) b.block(x, y + up, z + d, Material.AIR);
                b.block(x, y - 1, z, DRESSED);
                b.block(x, y - 1, z + 1, DRESSED);
                b.block(x, y - 2, z, BEDS);
                b.block(x, y - 2, z + 1, BEDS);
            }
            for (int d = 0; d <= 1; d++) for (int up = -1; up <= 5; up++) for (int x : new int[]{15, 21}) if (y + up < Y) b.block(x, y + up, z + d, BEDS);
        }
        for (int x = 15; x <= 21; x++) for (int z = 130; z <= 170; z++) {
            int depth = (z - 130) / 2;
            if (x == 15 || x == 21) b.block(x, Y + 1, z, CAST);
            if (x == 15 && z % 4 == 0) b.block(x, Y + 2, z, IRON);
        }
        for (int x = 16; x <= 20; x++) for (int y = Y + 1; y <= Y + 5; y++) b.block(x, y, 129, Material.IRON_BARS, "north=false,south=false,east=true,west=true");
        b.block(-22, Y + 1, 139, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        // Three great valve wheels on the west wall, each with a bulb over it that lights when it's turned.
        for (int z : new int[]{124, 137, 150}) {
            b.block(-HALL_HALF, Y + 3, z, DRESSED);
            b.block(-HALL_HALF + 1, Y + 3, z, Material.STONE_BUTTON, "face=wall,facing=east,powered=false");
            for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                int reach = Math.abs(dy) + Math.abs(dz);
                if (reach == 0 || reach == 4) continue;
                if (Math.max(Math.abs(dy), Math.abs(dz)) == 2) b.block(-HALL_HALF + 1, Y + 3 + dy, z + dz, Material.WAXED_CUT_COPPER);
                else if (dy == 0) b.block(-HALL_HALF + 1, Y + 3, z + dz, Material.IRON_CHAIN, "axis=z,waterlogged=false");
                else if (dz == 0) b.block(-HALL_HALF + 1, Y + 3 + dy, z, Material.IRON_CHAIN, "axis=y,waterlogged=false");
            }
            for (int up = 1; up <= 2; up++) b.block(-HALL_HALF, Y + 3 + up, z, PIPE);
            b.block(-HALL_HALF, Y + 6, z, Material.WAXED_COPPER_BULB, "lit=false,powered=false");
            for (int up = 4; up <= 5; up++) b.block(-HALL_HALF, Y + 3 + up, z, PIPE);
        }
    }

    /** The Pump House's north end: a tall arched front in the bluff, the channel running in through the door. */
    private static void front(BuildCanvas b) {
        int face = HALL_NORTH - 2;
        for (int x = -HALL_HALF - 8; x <= HALL_HALF + 8; x++) for (int h = -4; h <= 42; h++) {
            int gable = 42 - Math.abs(x) * 12 / (HALL_HALF + 8);
            if (h > gable) continue;
            boolean edge = h >= gable - 1;
            b.block(x, Y + h, face, edge ? Material.CHISELED_STONE_BRICKS : Math.floorMod(h, 7) == 0 ? DRESSED : stone(x, Y + h, face));
            b.block(x, Y + h, face + 1, WORN);
            if (edge) b.block(x, Y + h, face - 1, VERDIGRIS);
        }
        for (int x = -HALL_HALF - 8; x <= HALL_HALF + 8; x += 7) for (int h = -4; h <= 42 - Math.abs(x) * 12 / (HALL_HALF + 8) - 2; h++) {
            b.block(x, Y + h, face - 1, h % 7 == 3 ? Material.CHISELED_STONE_BRICKS : BEDS);
        }
        // Three tall round-headed windows, and the door under the middle one.
        for (int mid : new int[]{-16, 0, 16}) for (int a = -4; a <= 4; a++) for (int h = mid == 0 ? 15 : 8; h <= 32; h++) {
            double arch = Math.hypot(a, Math.max(0, h - 28));
            if (arch > 4.4) continue;
            for (int d = 0; d <= 2; d++) b.block(mid + a, Y + h, face + d, arch > 3.4 ? DRESSED : d == 2 ? Math.floorMod(a, 3) == 0 || h % 4 == 0 ? BRONZE : Material.CYAN_STAINED_GLASS : Material.AIR);
        }
        for (int a = -5; a <= 5; a++) for (int h = 1; h <= 11; h++) {
            double arch = Math.hypot(a, Math.max(0, h - 7));
            if (arch > 5.2) continue;
            for (int d = -1; d <= 3; d++) b.block(a, Y + h, face + d, arch > 4.1 ? DRESSED : Material.AIR);
        }
        // The great wheel over the door, in bronze.
        for (int a = -9; a <= 9; a++) for (int h = 12; h <= 15 + 16; h++) {
            double r = Math.hypot(a, h - 22), angle = Math.atan2(h - 22, a);
            if (mid(a, h) && r < 9.2 && (r > 8 || r < 2 || Math.abs(Math.sin(angle * 4)) < .13)) b.block(a, Y + h, face - 2, BRONZE);
        }
    }

    private static boolean mid(int a, int h) { return Math.abs(a) <= 9 && h >= 12; }

    /** Tall round-headed bays between brick piers, the way a waterworks is built: as if it were a cathedral. */
    private static void hallWalls(BuildCanvas b) {
        for (int side = -1; side <= 1; side += 2) for (int z = HALL_NORTH; z <= HALL_SOUTH; z++) {
            int off = Math.floorMod(z - HALL_NORTH, 13);
            for (int y = Y + 1; y <= HALL_TOP - 5; y++) {
                boolean pier = off <= 1;
                double arch = Math.hypot(off - 7, Math.max(0, y - (HALL_TOP - 12)));
                boolean recess = !pier && arch <= 5.2;
                b.block(side * (HALL_HALF + 1), y, z, recess ? BRICK : pier ? DRESSED : y % 5 == 0 ? BEDS : WORN);
                b.block(side * HALL_HALF, y, z, recess ? Material.AIR : pier ? (y % 6 == 0 ? Material.CHISELED_STONE_BRICKS : DRESSED) : arch < 6.4 ? DRESSED : WORN);
            }
            for (int y = Y + 1; y <= Y + 3; y++) b.block(side * HALL_HALF, y, z, y == Y + 3 ? DRESSED : BEDS);
        }
    }

    /**
     * A beam engine, of the kind a town once built a palace round. A cylinder taller than a man stands on an iron bed, its
     * rod rises into a beam rocking on a column, and the far end of the beam turns a flywheel the height of the hall.
     */
    private static void engine(BuildCanvas b, int side, int z) {
        int cylinder = side * 9, column = side * 16, wheel = side * 22;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > 4.5) continue;
            for (int h = 1; h <= 2; h++) b.block(cylinder + dx, Y + h, z + dz, h == 2 ? CAST : IRON);
            if (r < 3.7) for (int h = 3; h <= 9; h++) b.block(cylinder + dx, Y + h, z + dz, h == 9 ? (r < 1.5 ? BRASS : IRON) : r > 2.8 ? (h == 3 || h == 8 ? BRASS : h % 2 == 0 ? IRON : PIPE) : VERDIGRIS);
        }
        for (int h = 10; h <= 21; h++) b.block(cylinder, Y + h, z, Material.IRON_BLOCK);
        // Two columns carry the beam, fluted, with their heads picked out.
        for (int dz : new int[]{-3, 3}) for (int h = 1; h <= 20; h++) for (int dx = -1; dx <= 1; dx++) for (int d = -1; d <= 1; d++) {
            if (Math.abs(dx) + Math.abs(d) == 2 && h > 2 && h < 19) continue;
            b.block(column + dx, Y + h, z + dz + d, h == 2 || h == 19 ? BRASS : IRON);
        }
        // The beam, deep in the middle and thin at the ends.
        for (int a = -13; a <= 13; a++) {
            int x = column - side * a;
            int half = 2 - Math.abs(a) / 6;
            for (int h = 21 - half; h <= 21 + half; h++) for (int d = -1; d <= 1; d++) b.block(x, Y + h, z + d, Math.abs(h - 21) == half ? IRON : PIPE);
        }
        // The flywheel, on edge, half in its pit against the wall.
        int cy = Y + 10;
        for (int dy = -11; dy <= 11; dy++) for (int dz = -11; dz <= 11; dz++) for (int d = -1; d <= 1; d++) {
            double r = Math.hypot(dy, dz), angle = Math.atan2(dy, dz);
            boolean rim = r > 8.6 && r < 11, spoke = r < 9 && Math.abs(Math.sin(angle * 3)) < .12 && d == 0, hub = r < 2.2;
            if (cy + dy > Y && (rim || spoke || hub)) b.block(wheel + d, cy + dy, z + dz, hub ? BRASS : r > 10 ? IRON : rim ? CAST : IRON);
        }
        for (int h = 11; h <= 20; h++) b.block(wheel - side, Y + h, z, Material.IRON_BLOCK);
    }

    /** The Cistern: columns in rows under brick vaults, with nothing moving but the water. */
    private static void cistern(BuildCanvas b) {
        shell(b, CISTERN_HALF, HALL_NORTH + 2, HALL_SOUTH, DEEP + 1, CISTERN_TOP - 1, BRICK, 2);
        int bay = 9;
        for (int x = -CISTERN_HALF; x <= CISTERN_HALF; x++) for (int z = HALL_NORTH + 2; z <= HALL_SOUTH; z++) {
            b.block(x, DEEP, z, smooth(x, z, 5, 924) > .56 ? Material.MUD_BRICKS : Math.floorMod(x + z, 2) == 0 ? BRICK : Material.PACKED_MUD);
            double ox = Math.abs(Math.sin(Math.PI * x / bay)), oz = Math.abs(Math.sin(Math.PI * (z - 2) / bay));
            int vault = (int) Math.round(DEEP + 12 + 4 * Math.min(ox, oz));
            for (int y = vault; y <= CISTERN_TOP; y++) b.block(x, y, z, Math.min(ox, oz) < .12 ? DRESSED : Math.floorMod(y + x, 3) == 0 ? Material.MUD_BRICKS : BRICK);
        }
        for (int x = -bay * 3; x <= bay * 3; x += bay) for (int z = HALL_NORTH + 11; z <= HALL_SOUTH - 7; z += bay) {
            if (x > 12 && z > 136) continue;
            cisternColumn(b, x, z);
        }
        for (int x = -bay * 3 + 4; x <= bay * 3; x += bay) for (int z = HALL_NORTH + 15; z <= HALL_SOUTH - 7; z += bay) {
            b.block(x, DEEP + 9, z, Material.LIGHT, "level=9,waterlogged=false");
            if (Math.floorMod(x + z, 2) == 0) {
                for (int y = DEEP + 10; y <= DEEP + 15; y++) b.block(x, y, z, Material.IRON_CHAIN, "axis=y");
                b.block(x, DEEP + 9, z, Material.SEA_LANTERN);
            }
        }
        // A stone head fallen at the foot of one column, and a second, set upside down at another, as they were found.
        stoneHead(b, -18, 141);
        stoneHead(b, 0, 159);
    }

    private static void cisternColumn(BuildCanvas b, int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double r = Math.hypot(dx, dz);
            for (int y = DEEP + 1; y <= DEEP + 12; y++) {
                boolean head = y >= DEEP + 10, foot = y <= DEEP + 2;
                if (r > (head || foot ? 2.5 : 1.6)) continue;
                b.block(cx + dx, y, cz + dz, head ? (y == DEEP + 10 ? Material.CHISELED_STONE_BRICKS : DRESSED) : foot ? BEDS : y < DEEP + 6 ? Material.MOSSY_STONE_BRICKS : Math.floorMod(y, 3) == 0 ? BEDS : WORN);
            }
        }
    }

    private static void stoneHead(BuildCanvas b, int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int h = 1; h <= 5; h++) {
            if (Math.sqrt(dx * dx + dz * dz + (h - 3) * (h - 3) * 1.4) > 3.3) continue;
            b.block(cx + dx, DEEP + h, cz + dz, Math.floorMod(dx + h, 3) == 0 ? Material.MOSSY_STONE_BRICKS : DRESSED);
        }
        for (int side = -1; side <= 1; side += 2) b.block(cx + side, DEEP + 3, cz + 3, TIDE);
    }

    /** The Pressure Gallery: a long hall of tanks, with gantries over them and a pipe to every one. */
    private static void gallery(BuildCanvas b) {
        shell(b, GALLERY_HALF, GALLERY_NORTH, GALLERY_SOUTH, DEEP + 1, GALLERY_TOP, Material.DEEPSLATE_BRICKS, 2);
        for (int x = -GALLERY_HALF; x <= GALLERY_HALF; x++) for (int z = GALLERY_NORTH; z <= GALLERY_SOUTH; z++)
            b.block(x, DEEP, z, Math.abs(x) <= 1 ? GRATE : Math.floorMod(x + z, 2) == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE);
        for (int side = -1; side <= 1; side += 2) for (int z : new int[]{184, 208}) tank(b, side * 12, z);
        for (int z = GALLERY_NORTH; z <= GALLERY_SOUTH; z++) for (int side = -1; side <= 1; side += 2) {
            for (int y = DEEP + 13; y <= DEEP + 14; y++) for (int x = 11; x <= GALLERY_HALF; x++) b.block(side * x, y, z, Math.floorMod(z, 6) == 0 ? BRONZE : PIPE);
            if (Math.floorMod(z, 5) == 0) for (int y = DEEP + 1; y <= GALLERY_TOP; y++) b.block(side * GALLERY_HALF, y, z, Material.POLISHED_DEEPSLATE);
        }
        for (int x = -10; x <= 10; x += 5) for (int z = GALLERY_NORTH + 5; z <= GALLERY_SOUTH - 5; z += 8) b.block(x, DEEP + 8, z, Material.LIGHT, "level=13,waterlogged=false");
        for (int x = -14; x <= 14; x += 14) for (int y = DEEP + 15; y <= GALLERY_TOP; y++) for (int z : new int[]{188, 204}) b.block(x, y, z, Material.IRON_CHAIN, "axis=y");
        for (int z : new int[]{188, 204}) for (int x = -14; x <= 14; x++) b.block(x, DEEP + 14, z, BRONZE);
        door(b, HALL_SOUTH + 1, GALLERY_NORTH + 1);
        door(b, GALLERY_SOUTH, CONTROL_NORTH + 1);
        for (int z : new int[]{HALL_SOUTH + 2, GALLERY_SOUTH + 1}) for (int x = -2; x <= 2; x++) for (int y = DEEP + 1; y <= DEEP + 6; y++) b.block(x, y, z, Material.IRON_BARS, "north=false,south=false,east=true,west=true");
    }

    private static void tank(BuildCanvas b, int cx, int cz) {
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > 4.3) continue;
            b.block(cx + dx, DEEP + 1, cz + dz, Material.POLISHED_DEEPSLATE);
            if (r < 3.6) for (int y = DEEP + 2; y <= DEEP + 12; y++) b.block(cx + dx, y, cz + dz, y % 3 == 1 ? BRONZE : VERDIGRIS);
            if (r < 2.4) b.block(cx + dx, DEEP + 13, cz + dz, BRONZE);
        }
    }

    private static void door(BuildCanvas b, int from, int to) {
        for (int z = from; z <= to; z++) for (int x = -3; x <= 3; x++) {
            b.block(x, DEEP, z, DRESSED);
            for (int y = DEEP + 1; y <= DEEP + 7; y++) b.block(x, y, z, Math.abs(x) == 3 || y == DEEP + 7 ? (Math.abs(x) == 3 && y % 2 == 0 ? DRESSED : WORN) : Material.AIR);
        }
    }

    /** Floodgate Control: the wheel that holds the Great Dam's gates, a pair of plates that lift them, and the way out. */
    private static void control(BuildCanvas b) {
        shell(b, 12, CONTROL_NORTH, CONTROL_SOUTH, DEEP + 1, DEEP + 12, WORN, 2);
        for (int x = -12; x <= 12; x++) for (int z = CONTROL_NORTH; z <= CONTROL_SOUTH; z++) b.block(x, DEEP, z, Math.floorMod(x + z, 2) == 0 ? Material.WAXED_CUT_COPPER : DRESSED);
        for (int side = -1; side <= 1; side += 2) {
            b.box(side * 6 - 1, DEEP + 1, 226, side * 6 + 1, DEEP + 1, 228, WORN);
            b.block(side * 6, DEEP + 2, 227, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "power=0");
        }
        b.block(3, DEEP + 1, 222, Material.BARREL, "facing=up");
        b.block(-9, DEEP + 1, 222, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
        for (int a = -5; a <= 5; a++) for (int h = 1; h <= 11; h++) {
            double r = Math.hypot(a, h - 6), angle = Math.atan2(h - 6, a);
            if (r < 5.4 && (r > 4.4 || r < 1.3 || Math.abs(Math.sin(angle * 3)) < .18)) b.block(a + 12, DEEP + h, CONTROL_NORTH + 8, BRONZE);
        }
        for (int z = CONTROL_SOUTH; z <= CONTROL_SOUTH + 4; z++) for (int x = -3; x <= 3; x++) {
            b.block(x, DEEP, z, DRESSED);
            for (int y = DEEP + 1; y <= DEEP + 7; y++) b.block(x, y, z, Math.abs(x) == 3 || y == DEEP + 7 ? BEDS : Material.AIR);
        }
        for (int x = -2; x <= 2; x++) for (int y = DEEP + 1; y <= DEEP + 6; y++) b.block(x, y, CONTROL_SOUTH + 2, Material.IRON_BARS, "north=false,south=false,east=true,west=true");
    }
}
