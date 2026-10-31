package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * The loot containers the sample maps ship with: crates (barrels), chests and hidden caches. The
 * sample expedition files list the same positions under {@code chests:}; the reward chests are
 * placed by the run at the finish, so their spots are left empty here.
 */
final class SampleChests {
    record Spot(int x, int y, int z, Material block) { }

    private static final Map<String, List<Spot>> SPOTS = Map.of(
        "first_flight", List.of(new Spot(-9, 64, 80, Material.BARREL), new Spot(7, 64, 93, Material.CHEST)),
        "hollow_barrow", List.of(new Spot(-14, 64, 15, Material.BARREL), new Spot(-51, 64, 84, Material.CHEST),
            new Spot(-16, 64, 30, Material.CHEST)),
        "thornwood_canopy", List.of(new Spot(-8, 64, 62, Material.BARREL), new Spot(-20, 64, 62, Material.CHEST),
            new Spot(10, 64, 90, Material.CHEST)),
        "sunken_aqueduct", List.of(new Spot(6, 64, 16, Material.BARREL), new Spot(-9, 64, 64, Material.CHEST),
            new Spot(8, 64, 86, Material.CHEST)),
        "gloomforge_mines", List.of(new Spot(-8, 64, 36, Material.BARREL), new Spot(7, 64, 57, Material.CHEST),
            new Spot(-9, 64, 103, Material.CHEST)),
        "hall_of_wardens", List.of(new Spot(-25, 64, 4, Material.BARREL), new Spot(30, 64, 6, Material.CHEST),
            new Spot(-5, 64, 34, Material.CHEST)),
        "cinder_keep", List.of(new Spot(-8, 64, -100, Material.BARREL), new Spot(-7, 64, 12, Material.CHEST),
            new Spot(14, 64, 44, Material.CHEST), new Spot(-10, 64, 98, Material.BARREL)));

    private SampleChests() { }

    static void place(BuildCanvas b, String sample) {
        for (Spot spot : SPOTS.getOrDefault(sample, List.of()))
            b.block(spot.x(), spot.y(), spot.z(), spot.block(), spot.block() == Material.CHEST ? "facing=north,type=single" : "facing=up");
    }
}
