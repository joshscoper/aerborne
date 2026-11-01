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
        "first_flight", List.of(new Spot(-7, 64, 22, Material.BARREL), new Spot(3, 64, 79, Material.CHEST)),
        "hollow_barrow", List.of(new Spot(-14, 64, 15, Material.BARREL), new Spot(-51, 64, 84, Material.CHEST),
            new Spot(-16, 64, 30, Material.CHEST)),
        "thornwood_canopy", List.of(new Spot(-18, 64, 33, Material.BARREL), new Spot(47, 64, 68, Material.CHEST),
            new Spot(-41, 66, 26, Material.CHEST)),
        "sunken_aqueduct", List.of(new Spot(14, 64, 52, Material.BARREL), new Spot(-26, 45, 164, Material.CHEST),
            new Spot(-66, 33, 60, Material.CHEST)),
        "gloomforge_mines", List.of(new Spot(30, 96, 42, Material.BARREL), new Spot(47, 71, 143, Material.CHEST),
            new Spot(66, 94, 32, Material.CHEST)),
        "hall_of_wardens", List.of(new Spot(-9, 57, -68, Material.BARREL), new Spot(88, 64, 34, Material.CHEST),
            new Spot(154, 64, 10, Material.CHEST)),
        "cinder_keep", List.of(new Spot(-8, 64, -100, Material.BARREL), new Spot(-7, 64, 12, Material.CHEST),
            new Spot(14, 64, 44, Material.CHEST), new Spot(-10, 64, 98, Material.BARREL)));

    private SampleChests() { }

    static void place(BuildCanvas b, String sample) {
        for (Spot spot : SPOTS.getOrDefault(sample, List.of()))
            b.block(spot.x(), spot.y(), spot.z(), spot.block(), spot.block() == Material.CHEST ? "facing=north,type=single" : "facing=up");
    }
}
