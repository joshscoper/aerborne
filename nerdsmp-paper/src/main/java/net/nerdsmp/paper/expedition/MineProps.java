package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.pick;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * The set dressing of the Gloomforge: the mine portal, crystal growths, hanging rock, timber trestles,
 * cranes, lamp spires, lavafalls and rope lines. Seeded by position, like the rest of the kit.
 */
final class MineProps {
    private static final Material POST = Material.DARK_OAK_LOG, TIMBER = Material.STRIPPED_SPRUCE_LOG, BOARD = Material.SPRUCE_PLANKS;
    /** Two glass facets, the heart, then the two solid facets of the shard's foot. */
    static final Material[] BLUE = {Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.SEA_LANTERN, Material.BLUE_ICE, Material.PACKED_ICE};
    static final Material[] PINK = {Material.MAGENTA_STAINED_GLASS, Material.PINK_STAINED_GLASS, Material.PEARLESCENT_FROGLIGHT, Material.PURPUR_BLOCK, Material.MAGENTA_TERRACOTTA};

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
        for (int dz = 0; dz <= 2; dz++) for (int dx = 0; dx <= 1; dx++) {
            // A plank bed on wheels, boarded up the sides, and the load heaped in the middle under loose stuff.
            b.block(x + dx, y, z + dz, Material.DARK_OAK_SLAB, "type=top");
            b.block(x + dx, y + 1, z + dz, dz == 1 ? load : Material.GRAVEL);
            if (dz == 1) b.block(x + dx, y + 2, z + dz, Material.COBBLED_DEEPSLATE_SLAB, "type=bottom");
        }
        for (int dz = 0; dz <= 2; dz++) {
            b.block(x - 1, y + 1, z + dz, Material.SPRUCE_TRAPDOOR, "facing=west,half=bottom,open=true");
            b.block(x + 2, y + 1, z + dz, Material.SPRUCE_TRAPDOOR, "facing=east,half=bottom,open=true");
        }
        for (int dx = 0; dx <= 1; dx++) {
            b.block(x + dx, y + 1, z - 1, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=true");
            b.block(x + dx, y + 1, z + 3, Material.SPRUCE_TRAPDOOR, "facing=south,half=bottom,open=true");
        }
        for (int dz : new int[]{0, 2}) for (int side : new int[]{x - 1, x + 2}) b.block(side, y, z + dz, Material.DARK_OAK_TRAPDOOR, "facing=" + (side < x ? "west" : "east") + ",half=top,open=true");
    }

    /** One long shard of crystal, leaning a little, with a glowing heart. */
    private static void shard(BuildCanvas b, int x, int y, int z, int high, double radius, double leanX, double leanZ, Material[] crystal, int salt) {
        for (int up = 0; up < high; up++) {
            double t = up / (double) high, across = radius * (t < .7 ? 1 : (1 - t) / .3) + .2;
            int cx = x + (int) Math.round(leanX * up), cz = z + (int) Math.round(leanZ * up), reach = (int) Math.ceil(across);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double from = Math.hypot(dx, dz);
                if (from > across) continue;
                // Two flat tones split down the shard, so it reads as cut facets and not as noise.
                boolean foot = t < .34, lit = dx - dz + (salt & 1) > 0;
                Material block = from <= across - 1 && !foot ? crystal[2] : lit ? crystal[foot ? 3 : 0] : crystal[foot ? 4 : 1];
                b.block(cx + dx, y + up, cz + dz, block);
            }
        }
    }

    /** A burst of crystal: a great shard and a few smaller ones splaying out from its foot, no two alike. */
    static void crystals(BuildCanvas b, int x, int y, int z, int high, Material[] crystal, int salt) {
        burst(b, x, y, z, high, 0, 0, crystal, salt);
    }

    /** The same burst, thrown out of a wall or a slope by {@code leanX, leanZ} per block of rise. */
    static void burst(BuildCanvas b, int x, int y, int z, int high, double leanX, double leanZ, Material[] crystal, int salt) {
        shard(b, x, y, z, high, Math.max(1.2, high / 5.5), leanX, leanZ, crystal, salt);
        int count = 3 + Math.floorMod(salt, 4);
        for (int index = 0; index < count; index++) {
            double angle = salt + index * 2.4, spread = .22 + noise(salt, index, 90) * .34;
            double size = .4 + noise(index, salt, 91) * .5;
            shard(b, x + (int) Math.round(Math.cos(angle) * 2), y, z + (int) Math.round(Math.sin(angle) * 2), Math.max(3, (int) (high * size)), Math.max(.7, high * size / 8), leanX + Math.cos(angle) * spread, leanZ + Math.sin(angle) * spread, crystal, salt + index);
        }
    }

    /** A column where a spike from the floor has met one from the roof: thick at both ends, waisted in the middle, ribbed all the way. */
    static void column(BuildCanvas b, int x, int foot, int z, int head, double radius, Material[] rock, int salt) {
        int high = Math.max(1, head - foot);
        for (int up = 0; up <= high; up++) {
            double t = up / (double) high, waist = Math.pow(Math.abs(2 * t - 1), 2.4), across = radius * (.32 + .68 * waist);
            int reach = (int) Math.ceil(across + 2), cx = x + (int) Math.round(Math.sin(t * 3 + salt) * 1.4), cz = z + (int) Math.round(Math.cos(t * 2.3 + salt) * 1.4);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double ribs = 1 + Math.sin(Math.atan2(dz, dx) * 5 + salt) * .16;
                if (Math.hypot(dx, dz) > across * ribs + (smooth(cx + dx, up, 3, salt) - .5) * 1.2) continue;
                double value = (smooth(x + dx + up, z + dz, 5, salt + 1) - .5) * 2.2 + .5 + (Math.hypot(dx, dz) / Math.max(1, across) - .6) * .7 - (waist * .15);
                b.block(cx + dx, foot + up, cz + dz, rock[Math.clamp((int) Math.round(value * (rock.length - 1)), 0, rock.length - 1)]);
            }
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

    /**
     * A curtain of hanging rock: a row of ribs side by side, long in the middle and short at the ends, each stepping in as
     * it drops. A roof comes down in folds like this. A cone on its own only ever looks like a cone.
     */
    static void curtain(BuildCanvas b, int x, int y, int z, int length, int ribs, boolean alongX, Material[] rock, Material tip, int salt) {
        for (int rib = -ribs; rib <= ribs; rib++) {
            double share = 1 - Math.pow(Math.abs(rib) / (ribs + 1D), 1.6);
            int ribLength = Math.max(2, (int) Math.round(length * share * (.65 + noise(rib, salt, salt) * .45)));
            int rx = x + (alongX ? rib * 2 : 0), rz = z + (alongX ? 0 : rib * 2), top = y + 3;
            for (int down = 0; down < ribLength + 3; down++) {
                double t = down / (double) (ribLength + 3), across = (1 - t * t) * (1.5 + share) + .2;
                int reach = (int) Math.ceil(across);
                for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                    if (Math.hypot(dx, dz) <= across && CanopyKit.empty(b, rx + dx, top - down, rz + dz))
                        b.block(rx + dx, top - down, rz + dz, tip != null && down >= ribLength ? tip : rock[Math.clamp((int) (t * rock.length), 0, rock.length - 1)]);
            }
        }
    }

    /**
     * One bent of a timber trestle, as a railway builds it: two plumb posts under the rails, two battered legs that spread
     * as they drop, a waling across every six blocks, sway bracing in each panel, a cap with corbels and a stone pile at the foot.
     */
    static void trestle(BuildCanvas b, int x, int top, int z, boolean alongX, int foot) {
        int stepX = alongX ? 0 : 1, stepZ = alongX ? 1 : 0;
        String across = alongX ? "axis=z" : "axis=x";
        for (int y = foot; y < top; y++) {
            int spread = 2 + (top - y) / 6;
            for (int side : new int[]{-1, 1}) {
                b.block(x + stepX * side, y, z + stepZ * side, TIMBER, "axis=y");
                b.block(x + stepX * side * spread, y, z + stepZ * side * spread, POST, "axis=y");
            }
            if ((top - y) % 6 == 0) for (int out = -spread; out <= spread; out++) b.block(x + stepX * out, y, z + stepZ * out, TIMBER, across);
        }
        for (int panel = top - 6, flip = 0; panel - 6 >= foot; panel -= 6, flip++) {
            int high = 2 + (top - panel) / 6, low = 2 + (top - panel + 6) / 6, sign = flip % 2 == 0 ? 1 : -1;
            line(b, x + stepX * sign * high, panel - 1, z + stepZ * sign * high, x - stepX * sign * low, panel - 5, z - stepZ * sign * low, CanopyKit.RAIL, "");
        }
        for (int out = -3; out <= 3; out++) b.block(x + stepX * out, top - 1, z + stepZ * out, POST, across);
        for (int side : new int[]{-1, 1}) {
            String facing = alongX ? (side < 0 ? "south" : "north") : (side < 0 ? "east" : "west");
            b.block(x + stepX * side * 4, top - 1, z + stepZ * side * 4, Material.SPRUCE_STAIRS, under(facing));
            int spread = 2 + (top - foot) / 6;
            for (int y = foot; y <= foot + 1; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                b.block(x + stepX * side * spread + dx, y, z + stepZ * side * spread + dz, y == foot ? Material.COBBLED_DEEPSLATE : Material.STONE_BRICKS);
        }
    }

    /** A lamp on a post, built up the way a real one is: stone foot, fence, a collar of trapdoors, an arm each way and a lantern under each. */
    static void lamp(BuildCanvas b, int x, int y, int z, boolean alongX) {
        b.block(x, y, z, Material.COBBLESTONE_WALL);
        for (int up = 1; up <= 3; up++) b.block(x, y + up, z, Material.SPRUCE_FENCE);
        b.block(x, y + 4, z, TIMBER, "axis=y");
        b.block(x, y + 5, z, Material.SPRUCE_SLAB, "type=bottom");
        for (int side : new int[]{-1, 1}) {
            int ax = alongX ? side : 0, az = alongX ? 0 : side;
            b.block(x + ax, y + 4, z + az, Material.SPRUCE_SLAB, "type=top");
            b.block(x + ax, y + 3, z + az, Material.LANTERN, "hanging=true");
            b.block(x + az, y + 2, z + ax, Material.SPRUCE_TRAPDOOR, "facing=" + (alongX ? (side < 0 ? "north" : "south") : (side < 0 ? "west" : "east")) + ",half=bottom,open=true");
        }
    }

    /**
     * A hoist on the lip of a working: a battered and braced tower, a slewing deck, a long jib raked up over the drop and held by
     * stays from a king post, a boxed counterweight behind, and a loaded skip on chains. It's the shape that says mine from a long way off.
     */
    static void crane(BuildCanvas b, int x, int y, int z, int high, int out, int dir, int drop, Material load) {
        for (int up = 0; up < high; up++) {
            int spread = 2 + (high - up) / 7;
            for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) b.block(x + sx * spread, y + up, z + sz * spread, POST, "axis=y");
            if (up % 5 == 4) for (int run = -spread; run <= spread; run++) for (int side : new int[]{-1, 1}) {
                b.block(x + run, y + up, z + side * spread, TIMBER, "axis=x");
                b.block(x + side * spread, y + up, z + run, TIMBER, "axis=z");
            }
        }
        for (int panel = 4; panel + 5 < high; panel += 5) {
            int spread = 2 + (high - panel) / 7, next = 2 + (high - panel - 5) / 7, sign = panel % 2 == 0 ? 1 : -1;
            line(b, x - spread * sign, y + panel + 1, z + spread, x + next * sign, y + panel + 4, z + next, CanopyKit.RAIL, "");
            line(b, x + spread * sign, y + panel + 1, z - spread, x - next * sign, y + panel + 4, z - next, CanopyKit.RAIL, "");
        }
        int deck = y + high;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) {
            b.block(x + dx, deck, z + dz, BOARD);
            if (Math.abs(dz) == 3 && dx % 2 == 0) b.block(x + dx, deck - 1, z + dz, Material.SPRUCE_STAIRS, under(dz < 0 ? "south" : "north"));
            if (Math.abs(dx) == 4 || Math.abs(dz) == 3) b.block(x + dx, deck + 1, z + dz, CanopyKit.RAIL);
        }
        int kingTop = deck + 10, tipX = x + dir * out, tipY = deck + 3 + out / 3;
        for (int up = 1; up <= 10; up++) for (int side : new int[]{-1, 1}) b.block(x - dir, deck + up, z + side, POST, "axis=y");
        for (int side : new int[]{-1, 0, 1}) b.block(x - dir, kingTop + 1, z + side, TIMBER, "axis=z");
        for (int side : new int[]{-1, 1}) line(b, x + dir * 2, deck + 2, z + side, tipX, tipY, z + side, TIMBER, "axis=x");
        for (int along = 3; along < out; along += 3) {
            int ax = x + dir * along, ay = deck + 2 + (tipY - deck - 2) * (along - 2) / (out - 2);
            b.block(ax, ay, z, BOARD);
            b.block(ax, ay - 1, z, Material.SPRUCE_FENCE);
        }
        b.block(tipX, tipY + 1, z, POST, "axis=z");
        for (int side : new int[]{-1, 1}) b.block(tipX, tipY + 1, z + side, POST, "axis=z");
        b.block(tipX, tipY, z, Material.GRINDSTONE, "face=wall,facing=north");
        line(b, x - dir, kingTop + 2, z, tipX, tipY + 2, z, CanopyKit.RAIL, "");
        for (int dx = 0; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) for (int up = 1; up <= 4; up++) {
            boolean band = up == 2 || up == 3 ? dx == 0 || dx == 3 || Math.abs(dz) == 2 : true;
            if (band) b.block(x - dir * (4 + dx), deck + up, z + dz, up == 4 ? Material.COBBLED_DEEPSLATE_SLAB : up == 1 ? Material.COBBLESTONE : Material.BARREL, up == 4 ? "type=bottom" : up == 1 ? "" : dir > 0 ? "facing=east" : "facing=west");
        }
        line(b, x - dir, kingTop + 2, z, x - dir * 7, deck + 5, z, CanopyKit.RAIL, "");
        for (int down = 1; down <= drop; down++) b.block(tipX, tipY - down, z, Material.IRON_CHAIN, "axis=y");
        cart(b, tipX - 1, tipY - drop - 3, z - 1, load);
        for (int dx = -1; dx <= 0; dx++) b.block(tipX + dx, tipY - drop - 1, z, Material.IRON_CHAIN, "axis=y");
    }

    /**
     * The headhouse: a stone ground floor, an upper storey of frame and board jettied out over it on brackets, a steep gable
     * of green copper with kicked eaves and heavy barge boards, and a stack.
     */
    static void headhouse(BuildCanvas b, int cx, int y, int cz, int halfX, int halfZ, int salt) {
        for (int x = cx - halfX; x <= cx + halfX; x++) for (int z = cz - halfZ; z <= cz + halfZ; z++) {
            boolean corner = Math.abs(x - cx) == halfX && Math.abs(z - cz) == halfZ, wall = Math.abs(x - cx) == halfX || Math.abs(z - cz) == halfZ;
            for (int down = 1; down <= 6; down++) b.block(x, y - down, z, down < 3 ? Material.STONE_BRICKS : Material.COBBLESTONE);
            b.block(x, y, z, wall ? Material.STONE_BRICKS : Material.SPRUCE_PLANKS);
            for (int up = 1; up <= 4; up++) {
                if (!wall) b.block(x, y + up, z, Material.AIR);
                else if (corner) b.block(x, y + up, z, Material.POLISHED_ANDESITE);
                else {
                    boolean window = up >= 2 && up <= 3 && Math.floorMod((Math.abs(z - cz) == halfZ ? x : z) - salt, 3) == 1;
                    b.block(x, y + up, z, window ? Material.ORANGE_STAINED_GLASS : smooth(x + y + up, z, 4, salt) < .45 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
                }
            }
        }
        for (int x = cx - halfX - 1; x <= cx + halfX + 1; x++) for (int z = cz - halfZ - 1; z <= cz + halfZ + 1; z++) {
            boolean wall = Math.abs(x - cx) == halfX + 1 || Math.abs(z - cz) == halfZ + 1, corner = Math.abs(x - cx) == halfX + 1 && Math.abs(z - cz) == halfZ + 1;
            b.block(x, y + 5, z, wall ? TIMBER : BOARD, wall ? (Math.abs(z - cz) == halfZ + 1 ? "axis=x" : "axis=z") : "");
            for (int up = 6; up <= 8; up++) {
                if (!wall) { b.block(x, y + up, z, Material.AIR); continue; }
                int run = Math.abs(z - cz) == halfZ + 1 ? x - cx : z - cz;
                boolean post = corner || Math.floorMod(run, 3) == 0;
                b.block(x, y + up, z, post ? POST : up == 7 ? Material.ORANGE_STAINED_GLASS : BOARD, post ? "axis=y" : "");
            }
            if (wall && Math.floorMod(x + z, 2) == 0 && !corner) {
                int ox = Math.abs(x - cx) == halfX + 1 ? Integer.signum(x - cx) : 0, oz = ox != 0 ? 0 : Integer.signum(z - cz);
                b.block(x, y + 4, z, Material.SPRUCE_STAIRS, under(ox > 0 ? "west" : ox < 0 ? "east" : oz > 0 ? "north" : "south"));
            }
        }
        int ridge = halfZ + 3;
        for (int rise = 0; rise <= ridge; rise++) for (int x = cx - halfX - 3; x <= cx + halfX + 3; x++) {
            int north = cz - ridge + rise, south = cz + ridge - rise, top = y + 9 + rise - (rise == 0 ? 1 : 0);
            boolean end = x == cx - halfX - 3 || x == cx + halfX + 3;
            Material tile = end ? Material.DARK_OAK_STAIRS : smooth(x, north + rise, 5, salt) < .42 ? Material.WAXED_WEATHERED_CUT_COPPER_STAIRS : Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS;
            if (north == south) {
                b.block(x, top, north, Material.DARK_OAK_PLANKS);
                continue;
            }
            b.block(x, top, north, tile, stair("south"));
            b.block(x, top, south, tile, stair("north"));
            if (Math.abs(x - cx) == halfX + 1) for (int z = north + 1; z < south; z++) b.block(x, top, z, Math.floorMod(z, 3) == 0 ? POST : BOARD, Math.floorMod(z, 3) == 0 ? "axis=y" : "");
        }
        for (int z = cz - 1; z <= cz + 1; z++) for (int side : new int[]{-1, 1}) b.block(cx + side * (halfX + 1), y + 11, z, Material.ORANGE_STAINED_GLASS);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int up = 0; up <= ridge + 12; up++) {
            boolean head = up >= ridge + 11;
            if (dx == 0 && dz == 0 && up >= ridge + 12) continue;
            b.block(cx - halfX + 2 + dx, y + up, cz + halfZ - 2 + dz, head ? Material.STONE_BRICKS : smooth(up, dx + dz, 3, salt) < .5 ? Material.COBBLESTONE : Material.STONE_BRICKS);
        }
        b.block(cx - halfX + 2, y + ridge + 11, cz + halfZ - 2, Material.CAMPFIRE, "lit=true,signal_fire=false,facing=north");
    }

    /** A hill spruce: a trunk with a bend in it, irregular tiers that droop at the tips, and now and then a bare branch. */
    static void pine(BuildCanvas b, int x, int y, int z, int high, int salt) {
        int thick = high >= 15 ? 1 : 0;
        for (int up = 0; up < high; up++) {
            double t = up / (double) high;
            int cx = x + (int) Math.round(Math.sin(t * 2.6 + salt) * 1.2), cz = z + (int) Math.round(Math.cos(t * 2.1 + salt) * 1.2);
            int wide = t < .4 ? thick : 0;
            for (int dx = 0; dx <= wide; dx++) for (int dz = 0; dz <= wide; dz++) b.block(cx + dx, y + up, cz + dz, Material.SPRUCE_LOG, "axis=y");
            if (up % 3 != 1 || up < high / 4) continue;
            double taper = 1 - t, radius = taper * high * .3 + 1.2;
            int reach = (int) Math.ceil(radius) + 1;
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                double from = Math.hypot(dx, dz) / radius * (1 + (noise(x + dx, z + dz + up, salt) - .5) * .5);
                if (from > 1) continue;
                int droop = (int) Math.round(from * from * 2.4);
                Material leaf = smooth(x + dx, z + dz, 3, salt + up) < .36 ? Material.DARK_OAK_LEAVES : Material.SPRUCE_LEAVES;
                if (dx == 0 && dz == 0) continue;
                b.block(cx + dx, y + up - droop, cz + dz, leaf, "persistent=true");
                if (from < .55) b.block(cx + dx, y + up + 1 - droop, cz + dz, leaf, "persistent=true");
            }
        }
        b.block(x + (int) Math.round(Math.sin(2.6 + salt) * 1.2), y + high, z + (int) Math.round(Math.cos(2.1 + salt) * 1.2), Material.SPRUCE_LEAVES, "persistent=true");
        // Noise thins the ends of the boughs, and a leaf left on its own reads as a speck in the sky.
        int reach = high / 2;
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) for (int up = 0; up <= high + 1; up++)
            if (leaf(b.get(x + dx, y + up, z + dz)) && loose(b, x + dx, y + up, z + dz)) b.block(x + dx, y + up, z + dz, Material.AIR);
    }

    private static boolean leaf(Material block) { return block == Material.SPRUCE_LEAVES || block == Material.DARK_OAK_LEAVES; }

    private static boolean loose(BuildCanvas b, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            if ((dx != 0 || dy != 0 || dz != 0) && !CanopyKit.empty(b, x + dx, y + dy, z + dz)) return false;
        return true;
    }

    /** A boulder half sunk in the ground, stepped round its shoulders, with moss on the top of it. */
    static void boulder(BuildCanvas b, int x, int y, int z, int rx, int rz, int high, int salt) {
        Material[] stone = {Material.COBBLESTONE, Material.ANDESITE, Material.STONE};
        for (int dx = -rx - 1; dx <= rx + 1; dx++) for (int dz = -rz - 1; dz <= rz + 1; dz++) {
            double from = Math.hypot(dx / (double) rx, dz / (double) rz) * (1 + (smooth(x + dx, z + dz, 3, salt) - .5) * .6);
            if (from > 1) continue;
            int top = (int) Math.round(Math.sqrt(1 - from * from) * high);
            for (int up = -1; up <= top; up++) b.block(x + dx, y + up, z + dz, up == top && top >= high - 1 ? Material.MOSS_BLOCK : stone[Math.clamp((int) (smooth(x + dx + up, z + dz, 4, salt + 1) * 3), 0, 2)]);
            if (from > .75 && top <= 1) b.block(x + dx, y + top + 1, z + dz, Material.COBBLESTONE_SLAB, "type=bottom");
        }
    }

    /** The Foreman's pile: yellow powder heaped up, gold stacked on it, lamps hidden under carpet round the edge, and the odd gem. */
    static void hoard(BuildCanvas b, int cx, int y, int cz, int radius, int salt) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double from = Math.hypot(dx, dz) / radius;
            if (from > 1 || !CanopyKit.empty(b, cx + dx, y, cz + dz)) continue;
            int top = (int) Math.round(Math.pow(1 - from, .8) * radius * .7);
            for (int up = 0; up <= top; up++) b.block(cx + dx, y + up, cz + dz, Material.YELLOW_CONCRETE_POWDER);
            double roll = noise(cx + dx, cz + dz, salt);
            if (from > .78) {
                b.block(cx + dx, y + top, cz + dz, Material.GLOWSTONE);
                b.block(cx + dx, y + top + 1, cz + dz, Material.YELLOW_CARPET);
            } else if (roll < .3) {
                for (int up = 1; up <= 1 + (int) (noise(cz + dz, cx + dx, salt) * 2); up++) b.block(cx + dx, y + top + up - 1, cz + dz, Material.GOLD_BLOCK);
            } else if (roll < .36) {
                b.block(cx + dx, y + top, cz + dz, Material.EMERALD_BLOCK);
            } else if (roll < .41) {
                b.block(cx + dx, y + top, cz + dz, Material.DIAMOND_BLOCK);
            } else if (roll < .5) {
                b.block(cx + dx, y + top, cz + dz, Material.RAW_GOLD_BLOCK);
            }
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

    /** A timber shack on the edge of a working, on a stone footing: posts proud of boarded walls, eaves on rafters, a pitched shingle roof with its ridge along X, and a chimney. */
    static void shack(BuildCanvas b, int cx, int y, int cz, int halfX, int halfZ) {
        for (int x = cx - halfX; x <= cx + halfX; x++) for (int z = cz - halfZ; z <= cz + halfZ; z++) {
            boolean corner = Math.abs(x - cx) == halfX && Math.abs(z - cz) == halfZ, wall = Math.abs(x - cx) == halfX || Math.abs(z - cz) == halfZ;
            b.block(x, y, z, wall ? Material.COBBLESTONE : Material.SPRUCE_PLANKS);
            if (wall) b.block(x, y - 1, z, Material.COBBLESTONE);
            for (int up = 1; up <= 3; up++) {
                if (!wall) b.block(x, y + up, z, Material.AIR);
                else if (corner) b.block(x, y + up, z, POST, "axis=y");
                else if (up == 1) b.block(x, y + up, z, Material.STONE_BRICKS);
                else if (up == 3) b.block(x, y + up, z, TIMBER, Math.abs(z - cz) == halfZ ? "axis=x" : "axis=z");
                else b.block(x, y + up, z, (x + z) % 2 == 0 ? Material.ORANGE_STAINED_GLASS : Material.SPRUCE_PLANKS);
            }
            if (corner) {
                b.block(x + Integer.signum(x - cx), y + 1, z, Material.COBBLESTONE_WALL);
                b.block(x, y + 1, z + Integer.signum(z - cz), Material.COBBLESTONE_WALL);
            }
        }
        for (int x = cx - halfX - 1; x <= cx + halfX + 1; x += 2) {
            b.block(x, y + 3, cz - halfZ - 1, Material.SPRUCE_STAIRS, under("south"));
            b.block(x, y + 3, cz + halfZ + 1, Material.SPRUCE_STAIRS, under("north"));
        }
        for (int up = 3; up <= 10; up++) b.block(cx + halfX, y + up, cz, up == 10 ? Material.COBBLESTONE_WALL : Material.COBBLESTONE);
        for (int rise = 0; rise <= halfZ + 1; rise++) for (int x = cx - halfX - 1; x <= cx + halfX + 1; x++) {
            int north = cz - halfZ - 1 + rise, south = cz + halfZ + 1 - rise;
            b.block(x, y + 4 + rise, north, north == south ? Material.DARK_OAK_PLANKS : Material.DARK_OAK_STAIRS, north == south ? "" : stair("south"));
            if (north != south) b.block(x, y + 4 + rise, south, Material.DARK_OAK_STAIRS, stair("north"));
            for (int z = north + 1; z < south && (x == cx - halfX || x == cx + halfX); z++) b.block(x, y + 4 + rise, z, Material.SPRUCE_PLANKS);
        }
        b.block(cx, y + 3, cz, Material.LANTERN, "hanging=true");
        for (int up = 4; up < 5 + halfZ; up++) b.block(cx, y + up, cz, Material.IRON_CHAIN, "axis=y");
    }
}
