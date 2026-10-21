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
        "first_flight", FirstFlightTemplate::build,
        "hollow_barrow", HollowBarrowTemplate::build,
        "thornwood_canopy", ThornwoodTemplate::build,
        "sunken_aqueduct", AqueductTemplate::build,
        "gloomforge_mines", MinesTemplate::build,
        "hall_of_wardens", WardensTemplate::build,
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

    static final Palette BARROW = new Palette(
        new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.STONE_BRICKS},
        new Material[]{Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES},
        Material.POLISHED_DEEPSLATE, "", Material.DEEPSLATE_TILES, Material.SOUL_LANTERN, "hanging=true");

    static final Palette KEEP = new Palette(
        new Material[]{Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS, Material.NETHER_BRICKS},
        new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE},
        Material.BASALT, "axis=y", Material.BLACKSTONE, Material.LANTERN, "hanging=true");

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
    static void hall(BuildCanvas b, int x1, int x2, int z1, int z2, int top, Palette p) {
        for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
            boolean wall = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
            b.block(x, 62, z, p.wall()[0]);
            b.block(x, 63, z, pick(p.floor(), x, z, 11));
            if (wall) for (int y = 64; y <= top; y++) b.block(x, y, z, SampleKit.pick(p.wall(), x * 31 + y * 17, z * 13 + y, 5));
            b.block(x, top + 1, z, p.ceiling());
        }
        for (int z = z1 + 2; z <= z2 - 1; z += 6) for (int x : new int[]{x1, x2}) {
            for (int y = 64; y <= top; y++) b.block(x, y, z, p.pillar(), p.pillarState());
        }
        for (int z = z1 + 3; z <= z2 - 2; z += 6) for (int x : new int[]{x1 + 2, x2 - 2}) b.block(x, top, z, p.light(), p.lightState());
    }

    /** A 3x3 opening in the wall row at {@code z}; with a material it is sealed (a room gate). */
    static void doorway(BuildCanvas b, int z, Material gate, Material lintel) {
        for (int x = -1; x <= 1; x++) for (int y = 64; y <= 66; y++) {
            if (gate == null) b.block(x, y, z, Material.AIR);
            else b.block(x, y, z, gate, gate == Material.IRON_BARS ? "east=true,west=true" : "");
        }
        b.box(-2, 67, z, 2, 67, z, lintel);
    }

    static void candles(BuildCanvas b, int[][] spots) {
        for (int[] spot : spots) b.block(spot[0], 64, spot[1], Material.CANDLE, "candles=3,lit=true");
    }

    static void brazier(BuildCanvas b, int x, int z) {
        b.block(x, 64, z, Material.NETHERRACK);
        b.block(x, 65, z, Material.FIRE);
    }

    private static Material pick(Material[] options, int x, int z, int salt) {
        int hash = (x * 73_856_093) ^ (z * 19_349_663) ^ (salt * 83_492_791);
        return options[Math.floorMod(hash, options.length)];
    }
}
