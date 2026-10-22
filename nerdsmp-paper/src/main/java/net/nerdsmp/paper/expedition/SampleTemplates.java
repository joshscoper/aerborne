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
        "cinder_keep", CinderKeepTemplate::build);
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

    private static Material pick(Material[] options, int x, int z, int salt) {
        int hash = (x * 73_856_093) ^ (z * 19_349_663) ^ (salt * 83_492_791);
        return options[Math.floorMod(hash, options.length)];
    }
}
