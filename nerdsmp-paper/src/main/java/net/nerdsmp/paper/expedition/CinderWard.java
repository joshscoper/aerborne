package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.CinderKeepTemplate.*;
import static net.nerdsmp.paper.expedition.SampleKit.*;

/**
 * The outer ward of the keep: a barbican at the end of the bridge, a walled yard of forges under the Great Kiln, and the
 * hound pens beyond it, a sunken alley of cages leading to the cathedral's west door.
 */
final class CinderWard {
    static final int WARD_NORTH = -116, WARD_SOUTH = -44, WARD_HALF = 52, PENS_SOUTH = -8, PENS_HALF = 22, KILN_X = 30, KILN_Z = -66;

    private CinderWard() { }

    static void build(BuildCanvas b) {
        ground(b);
        walls(b);
        barbican(b);
        kiln(b);
        forges(b);
        pens(b);
    }

    private static void ground(BuildCanvas b) {
        for (int x = -WARD_HALF - 16; x <= WARD_HALF + 16; x++) for (int z = WARD_NORTH - 6; z <= PENS_SOUTH + 8; z++) {
            b.block(x, Y, z, Math.abs(x) <= 3 ? BRICK : smooth(x, z, 8, 961) > .58 ? Material.BASALT : smooth(x, z, 5, 962) > .52 ? Material.BLACKSTONE : Math.floorMod(x + z, 2) == 0 ? POLISHED : BRICK);
            for (int y = Y - 1; y >= Y - 3; y--) b.block(x, y, z, DARK);
        }
        // The rock the keep stands on, going down into the fog.
        for (int x = -WARD_HALF - 16; x <= WARD_HALF + 16; x++) for (int depth = 4; depth <= 44; depth += 1) {
            b.block(x, Y - depth, WARD_NORTH - 6, rock(x, Y - depth, WARD_NORTH - 6));
            b.block(x, Y - depth, WARD_NORTH - 7 - (depth + Math.floorMod(x * 3, 5)) / 6, rock(x, Y - depth, WARD_NORTH - 7));
        }
    }

    private static void wallSlice(BuildCanvas b, int x, int z, int high) {
        for (int y = Y + 1; y <= Y + high; y++) b.block(x, y, z, Math.floorMod(y, 9) == 0 ? GILT : masonry(x, y, z));
        if (Math.floorMod(x + z, 4) < 2) b.block(x, Y + high + 1, z, BRICK);
    }

    private static void walls(BuildCanvas b) {
        for (int thick = 0; thick <= 3; thick++) {
            for (int z = WARD_NORTH; z <= WARD_SOUTH; z++) for (int side = -1; side <= 1; side += 2) wallSlice(b, side * (WARD_HALF + thick), z, 26);
            for (int x = -WARD_HALF - 3; x <= WARD_HALF + 3; x++) {
                if (Math.abs(x) > 9) wallSlice(b, x, WARD_NORTH - thick, 26);
                if (Math.abs(x) > 7) wallSlice(b, x, WARD_SOUTH + thick, 26);
            }
        }
        for (int[] tower : new int[][]{{-WARD_HALF, WARD_NORTH}, {WARD_HALF, WARD_NORTH}, {-WARD_HALF, WARD_SOUTH}, {WARD_HALF, WARD_SOUTH}}) round(b, tower[0], tower[1], 8, 40);
        // A south door to the pens, walled up with bars until Gorrak falls.
        for (int x = -6; x <= 6; x++) for (int y = Y + 1; y <= Y + 11; y++) for (int z = WARD_SOUTH; z <= WARD_SOUTH + 3; z++) {
            boolean head = Math.abs(x) * 1.2 + Math.max(0, y - Y - 7) > 7.6;
            b.block(x, y, z, head ? BRICK : Material.AIR);
        }
        for (int x = -3; x <= 3; x++) for (int y = Y + 1; y <= Y + 8; y++) if (Math.abs(x) * 1.2 + Math.max(0, y - Y - 7) <= 7.6) b.block(x, y, WARD_SOUTH + 1, Material.IRON_BARS, "north=false,south=false,east=true,west=true");
    }

    /** A round tower with a ring of battlements and a fire on top. */
    static void round(BuildCanvas b, int cx, int cz, int radius, int high) {
        for (int dx = -radius - 2; dx <= radius + 2; dx++) for (int dz = -radius - 2; dz <= radius + 2; dz++) {
            double r = Math.hypot(dx, dz);
            if (r > radius + 1.5) continue;
            for (int h = 1; h <= high; h++) if (r <= radius) b.block(cx + dx, Y + h, cz + dz, Math.floorMod(h, 10) == 0 ? GILT : masonry(cx + dx, Y + h, cz + dz));
            if (r > radius - 1) {
                b.block(cx + dx, Y + high + 1, cz + dz, BRICK);
                if (Math.floorMod(dx + dz, 3) == 0) b.block(cx + dx, Y + high + 2, cz + dz, BRICK);
            }
        }
        for (int h = high + 1; h <= high + 3; h++) b.block(cx, Y + h, cz, h == high + 3 ? Material.CAMPFIRE : HOT, h == high + 3 ? "lit=true,signal_fire=false,facing=north" : "");
    }

    private static void barbican(BuildCanvas b) {
        for (int side = -1; side <= 1; side += 2) round(b, side * 15, WARD_NORTH - 2, 7, 34);
        for (int x = -8; x <= 8; x++) for (int y = Y + 1; y <= Y + 10; y++) b.block(x, y, WARD_NORTH - 1, Material.IRON_BARS, "north=false,south=false,east=true,west=true");
        for (int x = -9; x <= 9; x++) for (int y = Y + 11; y <= Y + 24; y++) for (int z = WARD_NORTH - 3; z <= WARD_NORTH; z++) {
            if (Math.abs(x) * 1.1 + Math.max(0, y - Y - 7) < 11) continue;
            b.block(x, y, z, Math.floorMod(y, 6) == 0 ? GILT : masonry(x, y, z));
        }
    }

    /** The Great Kiln: a bottle of brick the height of the walls, with a mouth you could drive a cart through and fire inside. */
    private static void kiln(BuildCanvas b) {
        int high = 44;
        for (int h = 1; h <= high; h++) {
            double t = h / (double) high, wide = 18 * (1 - t * .3) - Math.pow(Math.max(0, t - .55) / .45, 2) * 11;
            int reach = (int) Math.ceil(wide);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > wide) continue;
                boolean skin = r > wide - 2;
                b.block(KILN_X + dx, Y + h, KILN_Z + dz, skin ? (Math.floorMod(h, 8) == 0 ? Material.WAXED_WEATHERED_CUT_COPPER : smooth(dx + h, dz, 5, 963) > .6 ? Material.CRACKED_NETHER_BRICKS : RED) : h < 8 ? Material.LAVA : Material.AIR, !skin && h < 8 ? "level=0" : "");
            }
        }
        // Iron hoops round the bottle, and the mouth, facing the yard.
        for (int a = -6; a <= 6; a++) for (int h = 1; h <= 13; h++) {
            double arch = Math.hypot(a, Math.max(0, h - 7));
            if (arch > 7) continue;
            for (int d = 12; d <= 19; d++) b.block(KILN_X - d, Y + h, KILN_Z + a, arch > 6 ? GILT : h < 2 ? HOT : Material.AIR);
        }
        for (int a = -5; a <= 5; a++) b.block(KILN_X - 20, Y, KILN_Z + a, HOT);
        for (int y = Y + 45; y <= Y + 58; y++) b.block(KILN_X, y, KILN_Z, Material.BLACK_CONCRETE);
    }

    /** Open forges along the west wall and the north of the yard: hearths, quench troughs and racks. */
    private static void forges(BuildCanvas b) {
        for (int z = WARD_NORTH + 10; z <= WARD_SOUTH - 10; z += 16) forge(b, -WARD_HALF + 1, z);
        for (int z : new int[]{-100, -84}) forge(b, WARD_HALF - 8, z);
        for (int[] spot : new int[][]{{-18, -98}, {18, -100}, {-22, -60}, {-8, -80}, {12, -56}}) {
            b.block(spot[0], Y + 1, spot[1], Material.ANVIL, "facing=north");
            b.block(spot[0] + 1, Y + 1, spot[1], Material.CAULDRON);
        }
    }

    private static void forge(BuildCanvas b, int x, int z) {
        b.box(x, Y + 1, z - 4, x + 6, Y + 1, z + 4, BRICK);
        b.box(x + 1, Y + 2, z - 3, x + 5, Y + 2, z + 3, HOT);
        for (int h = 2; h <= 11; h++) {
            int half = Math.max(1, 5 - h / 2);
            for (int dz = -half; dz <= half; dz++) b.block(x, Y + h, z + dz, RED);
        }
        for (int dz = -4; dz <= 4; dz += 8) for (int h = 2; h <= 8; h++) b.block(x + 6, Y + h, z + dz, POLISHED);
        for (int dz = -4; dz <= 4; dz++) b.block(x + 6, Y + 9, z + dz, Material.WAXED_WEATHERED_CUT_COPPER);
    }

    /** The pens: a sunken alley, with cages each side and chains hanging over it. */
    private static void pens(BuildCanvas b) {
        for (int z = WARD_SOUTH + 4; z <= PENS_SOUTH; z++) for (int side = -1; side <= 1; side += 2) {
            for (int a = PENS_HALF; a <= PENS_HALF + 2; a++) for (int h = 1; h <= 18; h++) b.block(side * a, Y + h, z, Math.floorMod(h, 6) == 0 ? GILT : masonry(side * a, Y + h, z));
            if (Math.floorMod(z + 38, 12) == 0) cage(b, side, z);
        }
        for (int x = -PENS_HALF; x <= PENS_HALF; x += 6) for (int z = WARD_SOUTH + 8; z <= PENS_SOUTH - 2; z += 8) {
            int top = 14 + Math.floorMod(x + z, 4);
            for (int h = top; h <= 18; h++) b.block(x, Y + h, z, Material.IRON_CHAIN, "axis=y");
            b.block(x, Y + top - 1, z, HOT);
        }
        for (int a = -PENS_HALF - 2; a <= PENS_HALF + 2; a++) b.block(a, Y + 19, -26, BRICK);
        b.block(-10, Y + 1, -50, Material.CAMPFIRE, "lit=false,signal_fire=false,facing=north");
    }

    private static void cage(BuildCanvas b, int side, int z) {
        for (int a = PENS_HALF - 6; a <= PENS_HALF - 1; a++) for (int d = -4; d <= 4; d++) {
            b.block(side * a, Y + 8, z + d, RED);
            b.block(side * a, Y, z + d, Material.SOUL_SOIL);
        }
        for (int d = -4; d <= 4; d++) for (int h = 1; h <= 7; h++) b.block(side * (PENS_HALF - 6), Y + h, z + d, Math.abs(d) == 4 ? BRICK : Material.IRON_BARS, Math.abs(d) == 4 ? "" : "north=true,south=true,east=false,west=false");
        for (int a = PENS_HALF - 5; a <= PENS_HALF - 1; a++) for (int h = 1; h <= 7; h++) for (int d : new int[]{-4, 4}) b.block(side * a, Y + h, z + d, BRICK);
    }
}
