package net.nerdsmp.paper.gathering;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** Explicit right-click crop rules. Ageable fire, stems and decorative plants are not crops. */
public final class HoeHarvestRules {
    private HoeHarvestRules() { }

    public record Spec(Material crop, Material plantingMaterial, Material seed, int resetAge,
                       int matureAge, boolean tall, boolean berryVine) { }

    /** A null seed means the harvested plant stays in place and does not need a planting ingredient. */
    public static Spec recipe(Material material) {
        if (material == null) return null;
        return switch (material) {
            case WHEAT -> field(material, Material.WHEAT_SEEDS, 7);
            case CARROTS -> field(material, Material.CARROT, 7);
            case POTATOES -> field(material, Material.POTATO, 7);
            case BEETROOTS -> field(material, Material.BEETROOT_SEEDS, 3);
            case NETHER_WART -> field(material, Material.NETHER_WART, 3);
            case COCOA -> field(material, Material.COCOA_BEANS, 2);
            case SWEET_BERRY_BUSH -> new Spec(material, material, null, 1, 3, false, false);
            case CAVE_VINES, CAVE_VINES_PLANT -> new Spec(material, material, null, -1, -1, false, true);
            case TORCHFLOWER -> new Spec(material, Material.TORCHFLOWER_CROP,
                Material.TORCHFLOWER_SEEDS, 0, -1, false, false);
            case PITCHER_CROP -> new Spec(material, material, Material.PITCHER_POD, 0, 4, true, false);
            default -> null;
        };
    }

    /**
     * The caller resolves a tall plant to its lower block before checking eligibility. Torchflowers
     * finish growing by changing material, so an age-one torchflower crop is never ready to harvest.
     */
    public static boolean eligible(String material, int age, int maximumAge, boolean berries,
                                   boolean upperHalf) {
        Spec spec = recipe(material);
        if (spec == null || upperHalf) return false;
        if (spec.berryVine()) return berries;
        if (spec.crop() == Material.TORCHFLOWER) return true;
        return maximumAge == spec.matureAge() && age == spec.matureAge();
    }

    public static Spec recipe(String material) {
        if (material == null) return null;
        try {
            return recipe(Material.valueOf(material.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Spec field(Material material, Material seed, int matureAge) {
        return new Spec(material, material, seed, 0, matureAge, false, false);
    }

    /** An inert stack description; special identifies renamed, enchanted or otherwise modified items. */
    public record SeedStack(String material, int amount, boolean special) {
        public SeedStack {
            if (material == null || material.isBlank()) throw new IllegalArgumentException("Material is required");
            if (amount < 0) throw new IllegalArgumentException("Stack amount cannot be negative");
            material = material.toUpperCase(Locale.ROOT);
        }
    }

    public enum SeedSource { NONE, HARVEST, STORAGE, MISSING }

    /** A successful plan removes exactly one plain seed and never mutates either input list. */
    public record SeedReservation(boolean available, SeedSource source, int index,
                                  List<SeedStack> drops, List<SeedStack> storage) {
        public SeedReservation {
            drops = List.copyOf(drops);
            storage = List.copyOf(storage);
        }
    }

    public static SeedReservation reserveSeed(String seed, List<SeedStack> drops, List<SeedStack> storage) {
        List<SeedStack> immutableDrops = List.copyOf(drops);
        List<SeedStack> immutableStorage = List.copyOf(storage);
        if (seed == null) {
            return new SeedReservation(true, SeedSource.NONE, -1, immutableDrops, immutableStorage);
        }
        String normalized = seed.toUpperCase(Locale.ROOT);
        int dropIndex = seedIndex(immutableDrops, normalized);
        if (dropIndex >= 0) {
            return new SeedReservation(true, SeedSource.HARVEST, dropIndex,
                removeOne(immutableDrops, dropIndex), immutableStorage);
        }
        int storageIndex = seedIndex(immutableStorage, normalized);
        if (storageIndex >= 0) {
            return new SeedReservation(true, SeedSource.STORAGE, storageIndex,
                immutableDrops, removeOne(immutableStorage, storageIndex));
        }
        return new SeedReservation(false, SeedSource.MISSING, -1, immutableDrops, immutableStorage);
    }

    private static int seedIndex(List<SeedStack> stacks, String seed) {
        for (int index = 0; index < stacks.size(); index++) {
            SeedStack stack = stacks.get(index);
            if (stack.material().equals(seed) && stack.amount() > 0 && !stack.special()) return index;
        }
        return -1;
    }

    private static List<SeedStack> removeOne(List<SeedStack> stacks, int index) {
        java.util.ArrayList<SeedStack> result = new java.util.ArrayList<>(stacks);
        SeedStack seed = result.get(index);
        result.set(index, new SeedStack(seed.material(), seed.amount() - 1, false));
        return List.copyOf(result);
    }
}
