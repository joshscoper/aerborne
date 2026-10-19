package net.nerdsmp.paper.listener;

import net.nerdsmp.api.service.ClaimService.ClaimView;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;

import java.util.Optional;
import java.util.Set;

/** Rules shared by direct interactions and indirect world changes. */
final class ClaimProtectionRules {
    private static final Set<Material> CROPS = Set.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
        Material.TORCHFLOWER_CROP, Material.PITCHER_CROP,
        Material.CAVE_VINES, Material.CAVE_VINES_PLANT
    );

    private ClaimProtectionRules() {
    }

    static boolean sameTerritory(Optional<ClaimView> source, boolean sourceReserved,
                                 Optional<ClaimView> destination, boolean destinationReserved) {
        if (sourceReserved || destinationReserved) {
            return false;
        }
        if (source.isEmpty() || destination.isEmpty()) {
            return source.isEmpty() && destination.isEmpty();
        }
        ClaimView from = source.orElseThrow();
        ClaimView to = destination.orElseThrow();
        if (!from.owner().equals(to.owner()) || from.type().privateAccess() != to.type().privateAccess()) {
            return false;
        }
        if (!from.type().privateAccess()) {
            return true;
        }
        // Residents may connect machinery across their own home, never through a neighbor's plot.
        // Unassigned homes remain isolated instead of silently becoming shared town storage.
        return from.steward() != null ? from.steward().equals(to.steward())
            : to.steward() == null && from.claimId().equals(to.claimId());
    }

    static boolean harvestCrop(Material material, int age, int maximumAge) {
        return CROPS.contains(material) && material != Material.TORCHFLOWER_CROP
            && material != Material.CAVE_VINES && material != Material.CAVE_VINES_PLANT
            && maximumAge > 0 && age == maximumAge;
    }

    static boolean harvestCrop(Material material, BlockData data) {
        // Torchflower crop age 1 is not ripe: its final growth changes the block type.
        if (material == Material.TORCHFLOWER) return true;
        if (material == Material.CAVE_VINES || material == Material.CAVE_VINES_PLANT)
            return data instanceof CaveVinesPlant vine && vine.hasBerries();
        return data instanceof Ageable crop && harvestCrop(material, crop.getAge(), crop.getMaximumAge());
    }

    static boolean seedCrop(Material material) {
        return CROPS.contains(material);
    }

    static boolean publicStation(Material material) {
        return switch (material) {
            case CRAFTING_TABLE, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                BREWING_STAND, CARTOGRAPHY_TABLE, LOOM, STONECUTTER, SMITHING_TABLE, GRINDSTONE -> true;
            default -> false;
        };
    }
}
