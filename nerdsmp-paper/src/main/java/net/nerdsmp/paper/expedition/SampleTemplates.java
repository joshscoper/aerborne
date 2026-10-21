package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The shipped sample expeditions, built from code the way the expedition worlds are so the repo
 * carries no binary worlds. Each dungeon runs south (+Z) through a chain of halls; the shipped
 * {@code expeditions/<id>.yml} files mark the same coordinates. Floors sit at Y63, so players stand
 * at Y64. Doorways are three wide at X -1..1.
 */
public final class SampleTemplates {
    private static final Map<String, Supplier<BuildCanvas.Plan>> SAMPLES = Map.of(
        "hollow_barrow", SampleTemplates::hollowBarrow,
        "cinder_keep", SampleTemplates::cinderKeep);
    private static final Map<String, BuildCanvas.Plan> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private SampleTemplates() { }

    /** The build plan for a sample, or null when the id is unknown. */
    public static BuildCanvas.Plan plan(String sample) {
        if (sample == null) return null;
        Supplier<BuildCanvas.Plan> builder = SAMPLES.get(sample.toLowerCase(Locale.ROOT));
        return builder == null ? null : CACHE.computeIfAbsent(sample.toLowerCase(Locale.ROOT), key -> builder.get());
    }

    record Palette(Material[] floor, Material[] wall, Material pillar, String pillarState, Material ceiling, Material light, String lightState) { }

    private static final Palette BARROW = new Palette(
        new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.STONE_BRICKS},
        new Material[]{Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES},
        Material.POLISHED_DEEPSLATE, "", Material.DEEPSLATE_TILES, Material.SOUL_LANTERN, "hanging=true");

    private static final Palette KEEP = new Palette(
        new Material[]{Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS, Material.NETHER_BRICKS},
        new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE},
        Material.BASALT, "axis=y", Material.BLACKSTONE, Material.LANTERN, "hanging=true");

    static BuildCanvas.Plan hollowBarrow() {
        BuildCanvas b = new BuildCanvas();
        // Barrow mouth (spawn), Crypt Gallery, Ossuary, the King's Rest.
        hall(b, -5, 5, 0, 12, 68, BARROW);
        hall(b, -7, 7, 14, 34, 69, BARROW);
        hall(b, -8, 8, 36, 54, 69, BARROW);
        hall(b, -10, 10, 56, 80, 73, BARROW);
        doorway(b, 13, null, Material.CHISELED_STONE_BRICKS);
        doorway(b, 35, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        doorway(b, 55, Material.IRON_BARS, Material.CHISELED_STONE_BRICKS);
        // Mouth: a worn stair down from the surface and a ring of candles round the party's arrival.
        for (int x = -2; x <= 2; x++) b.block(x, 64, 0, Material.STONE_BRICK_STAIRS, "facing=north,half=bottom,shape=straight");
        candles(b, new int[][]{{-3, 3}, {3, 3}, {-3, 7}, {3, 7}});
        // Gallery: burial niches with bones and webs between the pillars.
        for (int z = 16; z <= 32; z += 4) for (int x : new int[]{-7, 7}) {
            b.block(x, 64, z, Material.BONE_BLOCK, "axis=y");
            b.block(x, 69, z, Material.COBWEB);
        }
        candles(b, new int[][]{{-4, 18}, {4, 24}, {-3, 29}});
        // Ossuary: bone pillars and chained ceiling.
        for (int[] pillar : new int[][]{{-4, 41}, {4, 41}, {-4, 49}, {4, 49}}) {
            for (int y = 64; y <= 69; y++) b.block(pillar[0], y, pillar[1], Material.BONE_BLOCK, "axis=y");
        }
        for (int z = 38; z <= 52; z += 7) for (int x : new int[]{-6, 0, 6}) b.block(x, 69, z, Material.IRON_CHAIN, "axis=y");
        // King's Rest: a raised dais and sarcophagus where the Huntsman wakes.
        b.box(-4, 64, 71, 4, 64, 75, Material.POLISHED_DEEPSLATE);
        b.box(-1, 65, 73, 1, 65, 75, Material.CHISELED_DEEPSLATE);
        for (int x = -4; x <= 4; x++) b.block(x, 64, 70, Material.POLISHED_DEEPSLATE_STAIRS, "facing=south,half=bottom,shape=straight");
        candles(b, new int[][]{{-6, 66}, {6, 66}, {-6, 74}, {6, 74}, {-2, 79}, {2, 79}});
        for (int x : new int[]{-9, 9}) for (int z = 60; z <= 76; z += 8) b.block(x, 73, z, Material.COBWEB);
        // Exit: the cracked tomb wall at the far end, ringed by soul fire once the King falls.
        b.box(-1, 63, 78, 1, 63, 79, Material.SOUL_SOIL);
        return b.freeze();
    }

    static BuildCanvas.Plan cinderKeep() {
        BuildCanvas b = new BuildCanvas();
        // Ashen approach (spawn), Gatehouse, Blaze Forge, Throne of Embers.
        hall(b, -5, 5, 0, 10, 69, KEEP);
        hall(b, -7, 7, 12, 30, 70, KEEP);
        hall(b, -9, 9, 32, 54, 75, KEEP);
        hall(b, -11, 11, 56, 84, 74, KEEP);
        doorway(b, 11, null, Material.CHISELED_POLISHED_BLACKSTONE);
        doorway(b, 31, Material.IRON_BARS, Material.CHISELED_POLISHED_BLACKSTONE);
        doorway(b, 55, Material.IRON_BARS, Material.CHISELED_POLISHED_BLACKSTONE);
        // Braziers: netherrack under eternal fire, fenced in nether brick.
        for (int[] brazier : new int[][]{{-3, 3}, {3, 3}, {-5, 16}, {5, 16}, {-5, 26}, {5, 26}, {-7, 36}, {7, 36}, {-7, 50}, {7, 50},
            {-9, 60}, {9, 60}, {-9, 80}, {9, 80}}) brazier(b, brazier[0], brazier[1]);
        // Magma seams along the gatehouse and forge walls: warm underfoot unless you sneak.
        for (int z = 13; z <= 29; z++) for (int x : new int[]{-7, 7}) b.block(x, 63, z, Material.MAGMA_BLOCK);
        for (int z = 33; z <= 53; z++) for (int x : new int[]{-9, 9}) b.block(x, 63, z, Material.MAGMA_BLOCK);
        // Forge anvils of blackstone and the bellows of crying obsidian.
        for (int[] anvil : new int[][]{{-4, 40}, {4, 40}, {-4, 47}, {4, 47}}) {
            b.box(anvil[0] - 1, 64, anvil[1], anvil[0] + 1, 64, anvil[1] + 1, Material.POLISHED_BLACKSTONE);
            b.block(anvil[0], 65, anvil[1], Material.CRYING_OBSIDIAN);
        }
        for (int z = 34; z <= 52; z += 6) for (int x : new int[]{-5, 5}) b.block(x, 75, z, Material.SHROOMLIGHT);
        // Throne: a gilded dais the Keeper guards.
        b.box(-5, 64, 75, 5, 64, 81, Material.POLISHED_BLACKSTONE_BRICKS);
        b.box(-2, 65, 79, 2, 65, 81, Material.GILDED_BLACKSTONE);
        for (int x = -5; x <= 5; x++) b.block(x, 64, 74, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, "facing=south,half=bottom,shape=straight");
        for (int z = 60; z <= 80; z += 10) for (int x : new int[]{-6, 0, 6}) b.block(x, 74, z, Material.SHROOMLIGHT);
        b.box(-1, 63, 82, 1, 63, 83, Material.CRYING_OBSIDIAN);
        return b.freeze();
    }

    /** An enclosed hall: interior X/Z inclusive, walking space from Y64 up to {@code top}. */
    private static void hall(BuildCanvas b, int x1, int x2, int z1, int z2, int top, Palette p) {
        for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
            boolean wall = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
            b.block(x, 62, z, p.wall()[0]);
            b.block(x, 63, z, pick(p.floor(), x, z, 11));
            if (wall) for (int y = 64; y <= top; y++) b.block(x, y, z, pick(p.wall(), x * 7 + y, z, 5));
            b.block(x, top + 1, z, p.ceiling());
        }
        for (int z = z1 + 2; z <= z2 - 1; z += 6) for (int x : new int[]{x1, x2}) {
            for (int y = 64; y <= top; y++) b.block(x, y, z, p.pillar(), p.pillarState());
        }
        for (int z = z1 + 3; z <= z2 - 2; z += 6) for (int x : new int[]{x1 + 2, x2 - 2}) b.block(x, top, z, p.light(), p.lightState());
    }

    /** A 3x3 opening in the wall row at {@code z}; with a material it is sealed (a room gate). */
    private static void doorway(BuildCanvas b, int z, Material gate, Material lintel) {
        for (int x = -1; x <= 1; x++) for (int y = 64; y <= 66; y++) {
            if (gate == null) b.block(x, y, z, Material.AIR);
            else b.block(x, y, z, gate, gate == Material.IRON_BARS ? "east=true,west=true" : "");
        }
        b.box(-2, 67, z, 2, 67, z, lintel);
    }

    private static void candles(BuildCanvas b, int[][] spots) {
        for (int[] spot : spots) b.block(spot[0], 64, spot[1], Material.CANDLE, "candles=3,lit=true");
    }

    private static void brazier(BuildCanvas b, int x, int z) {
        b.block(x, 64, z, Material.NETHERRACK);
        b.block(x, 65, z, Material.FIRE);
    }

    private static Material pick(Material[] options, int x, int z, int salt) {
        int hash = (x * 73_856_093) ^ (z * 19_349_663) ^ (salt * 83_492_791);
        return options[Math.floorMod(hash, options.length)];
    }
}
