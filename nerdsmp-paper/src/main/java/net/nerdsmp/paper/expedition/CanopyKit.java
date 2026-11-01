package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.nerdsmp.paper.expedition.SampleKit.noise;
import static net.nerdsmp.paper.expedition.SampleKit.smooth;

/**
 * A treetop village, in pieces: lumpy bark trunks, crowns of leaf clusters, round decks ringed with
 * rails and hung with vines, huts with cone roofs and lit windows, sagging rope bridges and lanterns on
 * chains. Everything is seeded by position, so a plan is the same on every server and in tests.
 */
final class CanopyKit {
    /** A hut's roof: the stairs its cone is laid in, and the block that caps it. */
    record Roof(Material stairs, Material cap) { }

    static final Roof SHINGLES = new Roof(Material.DARK_OAK_STAIRS, Material.DARK_OAK_PLANKS);
    static final Roof THATCH = new Roof(Material.BAMBOO_MOSAIC_STAIRS, Material.HAY_BLOCK);
    static final Roof MOSSY = new Roof(Material.MOSSY_COBBLESTONE_STAIRS, Material.MOSS_BLOCK);
    static final Roof VERDIGRIS = new Roof(Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Material.WAXED_OXIDIZED_CUT_COPPER);

    static final Material RAIL = Material.SPRUCE_FENCE;
    private static final Material BARK = Material.DARK_OAK_LOG, BURL = Material.DARK_OAK_WOOD, PALE_BARK = Material.SPRUCE_WOOD;
    private static final Material[] CANOPY = {Material.DARK_OAK_LEAVES, Material.DARK_OAK_LEAVES, Material.AZALEA_LEAVES, Material.OAK_LEAVES, Material.JUNGLE_LEAVES};
    /** What a deck is laid in, for dressing it afterwards. */
    private static final Set<Material> BOARDS = Set.of(Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.STRIPPED_SPRUCE_WOOD);

    static boolean boarded(Material block) { return block != null && BOARDS.contains(block); }

    private CanopyKit() { }

    // ---------------------------------------------------------------------------------------------
    // Trees
    // ---------------------------------------------------------------------------------------------

    /** A trunk with a lumpy, ridged outline and patches of moss. Only the outer shell is placed. */
    static void trunk(BuildCanvas b, int cx, int cz, int base, int top, double radius, int salt) {
        int reach = (int) Math.ceil(radius * 1.4) + 2;
        for (int y = base; y <= top; y++) {
            double taper = radius * (1 - .16 * (y - base) / (double) (top - base));
            for (int x = cx - reach; x <= cx + reach; x++) for (int z = cz - reach; z <= cz + reach; z++) {
                double dx = x - cx, dz = z - cz, angle = Math.atan2(dz, dx), distance = Math.sqrt(dx * dx + dz * dz);
                double edge = taper * (1 + .13 * Math.sin(angle * 3 + salt + y * .07) + .07 * Math.sin(angle * 7 - salt - y * .11));
                if (distance > edge || distance < edge - 2.4) continue;
                double ridge = Math.sin(angle * 11 + y * .05 + salt);
                boolean mossy = smooth(x + y / 2, z - y / 4, 3, salt + 30) > .72;
                b.block(x, y, z, mossy ? Material.MOSS_BLOCK : ridge > .55 ? PALE_BARK : ridge < -.4 ? BURL : BARK, mossy || ridge > .55 || ridge < -.4 ? "" : "axis=y");
            }
        }
    }

    /** A rough ball of leaves. */
    static void blob(BuildCanvas b, int cx, int cy, int cz, int across, int high, int salt) {
        for (int x = cx - across; x <= cx + across; x++) for (int z = cz - across; z <= cz + across; z++) for (int y = cy - high; y <= cy + high; y++) {
            double dx = (x - cx) / (double) across, dz = (z - cz) / (double) across, dy = (y - cy) / (double) high;
            if (dx * dx + dz * dz + dy * dy + (noise(x * 7 + y, z, salt) - .5) * .35 > 1 || noise(x, z + y * 13, salt + 1) < .06) continue;
            Material leaf = CANOPY[(int) (smooth(x, z + y, 4, salt + 2) * CANOPY.length) % CANOPY.length];
            b.block(x, y, z, leaf, "persistent=true");
        }
    }

    /** A crown of leaf clusters spread on heavy limbs. */
    static void crown(BuildCanvas b, int cx, int y, int cz, int spread, int salt) {
        for (int index = 0; index < 6; index++) {
            double angle = salt + index * Math.TAU / 6;
            int x = cx + (int) Math.round(Math.cos(angle) * spread * .75), z = cz + (int) Math.round(Math.sin(angle) * spread * .75);
            limb(b, cx, y - 6, cz, x, y + 1, z, 2, 1, salt + index);
            blob(b, x, y + 3 + (int) (noise(x, z, salt) * 4), z, spread * 2 / 3, Math.max(3, spread / 3), salt + index);
        }
        blob(b, cx, y + 8, cz, spread * 3 / 4, Math.max(4, spread / 3), salt + 7);
    }

    /** A limb that arcs from one spot to another, thinning as it goes. */
    static void limb(BuildCanvas b, int x1, int y1, int z1, int x2, int y2, int z2, double from, double to, int salt) {
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1))) * 2;
        double rise = steps / 16.0;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps, radius = from + (to - from) * t;
            double x = x1 + (x2 - x1) * t, z = z1 + (z2 - z1) * t, y = y1 + (y2 - y1) * t + Math.sin(t * Math.PI) * rise;
            int reach = (int) Math.ceil(radius);
            for (int dx = -reach; dx <= reach; dx++) for (int dy = -reach; dy <= reach; dy++) for (int dz = -reach; dz <= reach; dz++)
                if (dx * dx + dy * dy + dz * dz <= radius * radius + .3) {
                    int bx = (int) Math.round(x) + dx, by = (int) Math.round(y) + dy, bz = (int) Math.round(z) + dz;
                    b.block(bx, by, bz, noise(bx, bz + by, salt) < .2 ? PALE_BARK : BURL);
                }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Decks
    // ---------------------------------------------------------------------------------------------

    /** Boards laid in long runs, a few of them darker, inside a rim of log. */
    static Material board(int x, int z, double distance, double radius) {
        if (distance > radius - 1.1) return Material.STRIPPED_SPRUCE_WOOD;
        double run = noise(x, Math.floorDiv(z + x / 2, 5), 201);
        return run < .14 ? Material.DARK_OAK_PLANKS : run < .24 ? Material.STRIPPED_SPRUCE_WOOD : Material.SPRUCE_PLANKS;
    }

    /** A round deck. Rails come last, from {@link #railEdges}, once the shape of everything is known. */
    static void platform(BuildCanvas b, int cx, int cz, int radius, int y) {
        for (int x = cx - radius - 1; x <= cx + radius + 1; x++) for (int z = cz - radius - 1; z <= cz + radius + 1; z++) {
            double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (distance > radius + .4) continue;
            b.block(x, y, z, board(x, z, distance, radius));
        }
    }

    /** A place a deck's edge should stay open. */
    interface Gap {
        boolean at(int x, int y, int z);
    }

    static boolean empty(BuildCanvas b, int x, int y, int z) {
        Material block = b.get(x, y, z);
        return block == null || block == Material.AIR;
    }

    private static boolean walkway(Material block) { return boarded(block) || block == Material.SPRUCE_SLAB || block == Material.SPRUCE_STAIRS; }

    /** Moss and mushrooms give way to a rail. */
    private static boolean railable(Material block) {
        return block == null || block == Material.AIR || block == Material.MOSS_CARPET || block == Material.RED_MUSHROOM || block == Material.SWEET_BERRY_BUSH || block == Material.COBWEB;
    }

    /** Rails along every edge of every deck, corners included, so there's no way between them. */
    static void railEdges(BuildCanvas b, Gap gap) {
        List<ExpeditionLayout.Cell> edges = new ArrayList<>();
        for (var entry : b.blocks().entrySet()) {
            var at = entry.getKey();
            if (!boarded(entry.getValue().material()) || !railable(b.get(at.x(), at.y() + 1, at.z())) || gap.at(at.x(), at.y(), at.z())) continue;
            boolean edge = false;
            // Something a step up or down is the way on, as on a bridge's sag or a stair, not an edge.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                edge |= empty(b, at.x() + dx, at.y(), at.z() + dz) && !walkway(b.get(at.x() + dx, at.y() - 1, at.z() + dz)) && !walkway(b.get(at.x() + dx, at.y() + 1, at.z() + dz));
            if (edge) edges.add(at);
        }
        for (var at : edges) b.block(at.x(), at.y() + 1, at.z(), RAIL);
    }

    private static boolean joins(Material block) {
        if (block == null) return false;
        String name = block.name();
        return name.endsWith("FENCE") || name.endsWith("PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD");
    }

    /**
     * Fences are placed without their neighbours being told, so left alone each stands as a thin post
     * with gaps a player fits through. This sets every side that touches something to hold on to.
     */
    static void joinRails(BuildCanvas b) { joinRails(b, RAIL); }

    static void joinRails(BuildCanvas b, Material rail) {
        List<ExpeditionLayout.Cell> rails = new ArrayList<>();
        for (var entry : b.blocks().entrySet()) if (entry.getValue().material() == rail) rails.add(entry.getKey());
        for (var at : rails) b.block(at.x(), at.y(), at.z(), rail, "east=" + joins(b.get(at.x() + 1, at.y(), at.z())) + ",west=" + joins(b.get(at.x() - 1, at.y(), at.z()))
            + ",north=" + joins(b.get(at.x(), at.y(), at.z() - 1)) + ",south=" + joins(b.get(at.x(), at.y(), at.z() + 1)) + ",waterlogged=false");
    }

    /** What holds a round deck up and what hangs from it: joists, braces back to the trunk, moss, and strands of glow berries. */
    static void underside(BuildCanvas b, int cx, int cz, int radius, int y, int trunk, int salt) {
        for (int spoke = 0; spoke < 10; spoke++) {
            double angle = spoke * Math.TAU / 10 + salt;
            for (double reach = 0; reach <= radius; reach += .5) {
                int x = cx + (int) Math.round(Math.cos(angle) * reach), z = cz + (int) Math.round(Math.sin(angle) * reach);
                b.block(x, y - 1, z, BURL);
                // The brace climbs from the trunk to the rim.
                if (reach >= trunk) b.block(x, y - 2 - (int) Math.round((radius - reach) * .75), z, Material.STRIPPED_DARK_OAK_WOOD);
            }
        }
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (distance > radius - .5 || distance < radius - 3.5) continue;
            double roll = noise(x, z, salt + 40);
            if (roll < .22) b.block(x, y - 1, z, Material.MOSS_BLOCK);
            if (roll < .1) strand(b, x, y - 2, z, 2 + (int) (noise(z, x, salt + 41) * 7), noise(x, z, salt + 42) < .6);
        }
    }

    /** A strand hanging down from {@code y}: glow berries that light the dark, or plain roots. */
    static void strand(BuildCanvas b, int x, int y, int z, int length, boolean berries) {
        if (!berries) {
            b.block(x, y, z, Material.HANGING_ROOTS);
            return;
        }
        for (int down = 0; down < length; down++)
            b.block(x, y - down, z, down == length - 1 ? Material.CAVE_VINES : Material.CAVE_VINES_PLANT, down == length - 1 ? "age=25,berries=true" : "berries=" + (Math.floorMod(x + z + down, 3) == 0));
    }

    /** A lantern on a chain, hung from {@code y}. */
    static void lantern(BuildCanvas b, int x, int y, int z, int length) {
        for (int down = 0; down < length; down++) b.block(x, y - down, z, Material.IRON_CHAIN, "axis=y");
        b.block(x, y - length, z, Material.LANTERN, "hanging=true");
    }

    /** A rope bridge along one axis, three wide, sagging by {@code sag} half-blocks at the middle. */
    static void rope(BuildCanvas b, int x1, int z1, int x2, int z2, int y, int sag) {
        boolean alongX = z1 == z2;
        int length = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        int dx = Integer.signum(x2 - x1), dz = Integer.signum(z2 - z1);
        for (int step = 0; step <= length; step++) {
            double t = step / (double) length;
            int drop = (int) Math.round(sag * 4 * t * (1 - t));
            int level = y - drop / 2;
            for (int side = -2; side <= 2; side++) {
                int x = x1 + dx * step + (alongX ? 0 : side), z = z1 + dz * step + (alongX ? side : 0);
                if (Math.abs(side) == 2) {
                    b.block(x, level, z, Material.STRIPPED_SPRUCE_WOOD);
                    // Two high, so rails a step apart overlap where the bridge sags.
                    b.block(x, level + 1, z, RAIL);
                    b.block(x, level + 2, z, RAIL);
                    if (step % 4 == 0) b.block(x, level + 3, z, Material.LANTERN);
                } else if (drop % 2 == 1) {
                    b.block(x, level, z, Material.SPRUCE_SLAB, "type=bottom");
                } else {
                    b.block(x, level, z, step % 2 == 0 ? Material.SPRUCE_PLANKS : Material.STRIPPED_SPRUCE_WOOD);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Huts
    // ---------------------------------------------------------------------------------------------

    /**
     * A round hut with its floor at {@code y}: framed walls, lit windows and a door on {@code door} (0 east,
     * 1 south, 2 west, 3 north), under a cone roof with a lantern on its spire.
     */
    static void hut(BuildCanvas b, int cx, int y, int cz, int radius, int height, Roof roof, int door) {
        int[][] doors = {{radius, 0}, {0, radius}, {-radius, 0}, {0, -radius}};
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (distance > radius + .4) continue;
            b.block(x, y, z, Material.SPRUCE_PLANKS);
            if (distance <= radius - .6) continue;
            double angle = Math.atan2(z - cz, x - cx);
            boolean post = Math.abs(Math.sin(angle * 4)) < .22;
            int inX = x + Integer.signum(cx - x), inZ = z + Integer.signum(cz - z);
            for (int up = 1; up <= height; up++) {
                boolean window = !post && (up == 2 || up == 3 && height > 3) && up < height;
                b.block(x, y + up, z, post ? Material.STRIPPED_DARK_OAK_LOG : window ? Material.ORANGE_STAINED_GLASS : Material.SPRUCE_PLANKS, post ? "axis=y" : "");
                // A light right behind the glass, so the window itself glows.
                if (window) b.block(inX, y + up, inZ, Material.GLOWSTONE);
            }
        }
        int doorX = cx + doors[door][0], doorZ = cz + doors[door][1];
        String facing = new String[]{"east", "south", "west", "north"}[door];
        b.block(doorX, y + 1, doorZ, Material.DARK_OAK_DOOR, "facing=" + facing + ",half=lower,hinge=left,open=false,powered=false");
        b.block(doorX, y + 2, doorZ, Material.DARK_OAK_DOOR, "facing=" + facing + ",half=upper,hinge=left,open=false,powered=false");
        cone(b, cx, y + height + 1, cz, radius + 1, roof);
    }

    /** Rings of stairs, each one in from the last, climbing to a spire. */
    private static void cone(BuildCanvas b, int cx, int y, int cz, int radius, Roof roof) {
        int level = 0;
        for (int ring = radius; ring >= 1; ring--, level++) for (int x = cx - ring; x <= cx + ring; x++) for (int z = cz - ring; z <= cz + ring; z++) {
            double distance = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (distance > ring + .4 || distance <= ring - .7) continue;
            int dx = cx - x, dz = cz - z;
            String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : dz > 0 ? "south" : "north";
            b.block(x, y + level, z, roof.stairs(), "facing=" + facing + ",half=bottom,shape=straight");
        }
        b.block(cx, y + level, cz, roof.cap());
        b.block(cx, y + level + 1, cz, RAIL);
        b.block(cx, y + level + 2, cz, Material.LANTERN);
    }

    /** A hut on its own round platform, hung with vines. */
    static void pod(BuildCanvas b, int cx, int y, int cz, int radius, int height, Roof roof, int door, int salt) {
        int deck = radius + 2;
        platform(b, cx, cz, deck, y);
        underside(b, cx, cz, deck, y, 1, salt);
        hut(b, cx, y, cz, radius, height, roof, door);
    }
}
