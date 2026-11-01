package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;

/**
 * The set dressing of the Gloomforge: the mine portal, crystal growths, hanging rock, timber trestles,
 * lamp spires, lavafalls and rope lines. Seeded by position, like the rest of the kit.
 */
final class MineProps {
    private static final Material POST = Material.DARK_OAK_LOG, TIMBER = Material.STRIPPED_SPRUCE_LOG, BOARD = Material.SPRUCE_PLANKS;
    static final Material[] BLUE = {Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.SEA_LANTERN};
    static final Material[] PINK = {Material.MAGENTA_STAINED_GLASS, Material.PINK_STAINED_GLASS, Material.PEARLESCENT_FROGLIGHT};

    private MineProps() { }

    private static String stair(String facing) { return "facing=" + facing + ",half=bottom,shape=straight"; }

    private static String under(String facing) { return "facing=" + facing + ",half=top,shape=straight"; }

    /** A timbered mine mouth facing north: capped posts, a railed lintel, lanterns on chains and a stone-lined throat. */
    static void portal(BuildCanvas b, int cx, int y, int z) {
        for (int side : new int[]{-1, 1}) {
            int x = cx + side * 5;
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                int px = x + dx * side, pz = z + dz;
                for (int up = 0; up <= 3; up++) b.block(px, y + up, pz, up == 0 ? Material.SPRUCE_PLANKS : POST, up == 0 ? "" : "axis=y");
                // A planked collar, banded round with iron, and a framed head on top.
                for (int up = 4; up <= 5; up++) b.block(px, y + up, pz, BOARD);
                b.block(px, y + 6, pz, TIMBER, "axis=y");
                b.block(px, y + 7, pz, Material.SPRUCE_PLANKS);
                for (int up = 8; up <= 9; up++) b.block(px, y + up, pz, POST, "axis=y");
            }
            for (int across = 0; across <= 1; across++) {
                b.block(x + side * across, y + 3, z - 1, Material.SPRUCE_SLAB, "type=top");
                b.block(x + side * across, y + 8, z - 1, Material.SPRUCE_STAIRS, under("south"));
                b.block(x + side * across, y + 5, z - 1, Material.IRON_TRAPDOOR, "facing=north,half=bottom,open=true");
            }
            b.block(x, y + 10, z, Material.SPRUCE_SLAB, "type=bottom");
            b.block(x + side, y + 10, z, Material.SPRUCE_SLAB, "type=bottom");
            b.block(x, y + 10, z + 1, Material.SPRUCE_SLAB, "type=bottom");
            b.block(x + side, y + 10, z + 1, Material.SPRUCE_SLAB, "type=bottom");
        }
        // The lintel: two courses of log, a fence along them, and a capping beam.
        for (int dx = -4; dx <= 4; dx++) {
            b.block(cx + dx, y + 5, z, TIMBER, "axis=x");
            b.block(cx + dx, y + 6, z, Material.DARK_OAK_PLANKS);
            b.block(cx + dx, y + 7, z, TIMBER, "axis=x");
            b.block(cx + dx, y + 8, z, Material.SPRUCE_FENCE);
            b.block(cx + dx, y + 9, z, TIMBER, "axis=x");
            b.block(cx + dx, y + 5, z + 1, TIMBER, "axis=x");
        }
        for (int dx : new int[]{-4, 4}) {
            b.block(cx + dx, y + 4, z, Material.SPRUCE_STAIRS, under(dx < 0 ? "east" : "west"));
            b.block(cx + dx + (dx < 0 ? 1 : -1), y + 4, z, Material.SPRUCE_SLAB, "type=top");
        }
        for (int dx : new int[]{-2, 2}) {
            b.block(cx + dx, y + 4, z, Material.IRON_CHAIN, "axis=y");
            b.block(cx + dx, y + 3, z, Material.LANTERN, "hanging=true");
        }
    }

    /** A little cart of plated stone with a load in it, standing where it was left. */
    static void cart(BuildCanvas b, int x, int y, int z, Material load) {
        for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++) b.block(x + dx, y, z + dz, Material.SMOOTH_STONE_SLAB, "type=top");
        for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) b.block(x + dx, y + 1, z + dz, load);
        b.block(x + 1, y + 2, z, load);
    }

    /** One long shard of crystal, leaning a little, with a glowing heart. */
    private static void shard(BuildCanvas b, int x, int y, int z, int high, double radius, double leanX, double leanZ, Material[] crystal, int salt) {
        for (int up = 0; up < high; up++) {
            double t = up / (double) high, across = radius * (t < .7 ? 1 : (1 - t) / .3) + .2;
            int cx = x + (int) Math.round(leanX * up), cz = z + (int) Math.round(leanZ * up), reach = (int) Math.ceil(across);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double from = Math.hypot(dx, dz);
                if (from > across) continue;
                Material block = from <= across - 1 ? crystal[2] : noise(cx + dx + up, cz + dz, salt) < .5 ? crystal[0] : crystal[1];
                b.block(cx + dx, y + up, cz + dz, block);
            }
        }
    }

    /** A burst of crystal: a great shard and a few smaller ones splaying out from its foot. */
    static void crystals(BuildCanvas b, int x, int y, int z, int high, Material[] crystal, int salt) {
        shard(b, x, y, z, high, Math.max(1.2, high / 5.5), 0, 0, crystal, salt);
        for (int index = 0; index < 5; index++) {
            double angle = salt + index * 2.4;
            double leanX = Math.cos(angle) * .3, leanZ = Math.sin(angle) * .3;
            shard(b, x + (int) Math.round(Math.cos(angle) * 2), y, z + (int) Math.round(Math.sin(angle) * 2), high / 2 + index, Math.max(.8, high / 9D), leanX, leanZ, crystal, salt + index);
        }
    }

    /** Hanging rock, stepped like a curtain, from the roof at {@code y} down by {@code length}. */
    static void stalactite(BuildCanvas b, int x, int y, int z, int length, double radius, Material[] rock, Material tip, int salt) {
        for (int down = 0; down < length; down++) {
            double across = radius * Math.pow(1 - down / (double) length, 1.4) + .3;
            int reach = (int) Math.ceil(across);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                if (Math.hypot(dx, dz) <= across + (noise(x + dx, z + dz + down / 3, salt) - .5) * .9)
                    b.block(x + dx, y - down, z + dz, tip != null && down >= length - 3 ? tip : pick(rock, x + dx + down, z + dz, salt));
        }
    }

    /** A timber trestle leg: splayed posts, cross braces, and a lantern hung from an arm. */
    static void trestle(BuildCanvas b, int x, int top, int z, boolean alongX, int foot) {
        int dx = alongX ? 0 : 2, dz = alongX ? 2 : 0;
        for (int y = foot; y < top; y++) for (int side : new int[]{-1, 1}) {
            int splay = (top - y) / 7;
            b.block(x + (dx + splay * Integer.signum(dx)) * side, y, z + (dz + splay * Integer.signum(dz)) * side, TIMBER, "axis=y");
            if ((top - y) % 7 == 3) for (int across = -dx - splay; across <= dx + splay && dx != 0; across++) b.block(x + across, y, z, BOARD);
            if ((top - y) % 7 == 3) for (int across = -dz - splay; across <= dz + splay && dz != 0; across++) b.block(x, y, z + across, BOARD);
        }
    }

    /** A stone lamp spire for a causeway: stepped out at the shoulders, with light set in its faces. */
    static void spire(BuildCanvas b, int x, int y, int z, int high, Material stone, Material trim, Material light) {
        for (int up = 0; up < high; up++) {
            int reach = up == 0 ? 2 : up < 3 ? 1 : up >= high - 4 && up % 2 == 0 ? 2 : 1;
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                boolean lamp = up >= 3 && up % 4 == 1 && (dx == 0 || dz == 0) && Math.abs(dx) + Math.abs(dz) == reach;
                b.block(x + dx, y + up, z + dz, lamp ? light : up % 3 == 0 ? trim : stone);
            }
        }
        for (int up = 0; up <= 2; up++) b.block(x, y + high + up, z, stone);
        b.block(x, y + high + 3, z, light);
    }

    /** Fire falling out of the rock, from a source down to {@code bottom}. */
    static void lavafall(BuildCanvas b, int x, int top, int z, int bottom, int wide) {
        for (int y = top; y >= bottom; y--) for (int dx = 0; dx < wide; dx++) for (int dz = 0; dz < wide; dz++)
            b.block(x + dx, y, z + dz, Material.LAVA, y == top ? "level=0" : "level=8");
    }

    /** Water falling out of the rock. */
    static void waterfall(BuildCanvas b, int x, int top, int z, int bottom, int wide) {
        for (int y = top; y >= bottom; y--) for (int dx = 0; dx < wide; dx++) b.block(x + dx, y, z, Material.WATER, y == top ? "level=0" : "level=8");
    }

    /** A straight rope or chain between two points, a block at a time. */
    static void line(BuildCanvas b, int x1, int y1, int z1, int x2, int y2, int z2, Material material, String state) {
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t), y = (int) Math.round(y1 + (y2 - y1) * t), z = (int) Math.round(z1 + (z2 - z1) * t);
            if (CanopyKit.empty(b, x, y, z)) b.block(x, y, z, material, state);
        }
    }

    /** A timber shack on the edge of a working: boarded walls, a lit window and a pitched shingle roof, ridge along X. */
    static void shack(BuildCanvas b, int cx, int y, int cz, int halfX, int halfZ) {
        for (int x = cx - halfX; x <= cx + halfX; x++) for (int z = cz - halfZ; z <= cz + halfZ; z++) {
            boolean corner = Math.abs(x - cx) == halfX && Math.abs(z - cz) == halfZ, wall = Math.abs(x - cx) == halfX || Math.abs(z - cz) == halfZ;
            b.block(x, y, z, Material.SPRUCE_PLANKS);
            for (int up = 1; up <= 3; up++) {
                if (!wall) b.block(x, y + up, z, Material.AIR);
                else b.block(x, y + up, z, corner ? TIMBER : up == 2 && (x + z) % 2 == 0 ? Material.ORANGE_STAINED_GLASS : Material.DARK_OAK_PLANKS, corner ? "axis=y" : "");
            }
        }
        for (int rise = 0; rise <= halfZ + 1; rise++) for (int x = cx - halfX - 1; x <= cx + halfX + 1; x++) {
            int north = cz - halfZ - 1 + rise, south = cz + halfZ + 1 - rise;
            b.block(x, y + 4 + rise, north, north == south ? Material.DARK_OAK_PLANKS : Material.DARK_OAK_STAIRS, north == south ? "" : stair("south"));
            if (north != south) b.block(x, y + 4 + rise, south, Material.DARK_OAK_STAIRS, stair("north"));
            for (int z = north + 1; z < south && (x == cx - halfX || x == cx + halfX); z++) b.block(x, y + 4 + rise, z, Material.SPRUCE_PLANKS);
        }
        b.block(cx, y + 3, cz, Material.LANTERN, "hanging=true");
    }
}
