package net.nerdsmp.paper.activity;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

enum FishingCatch {
    RIVER_SPRAT("river_sprat", FishingRolls.Rarity.COMMON, Material.COD, 35, 1,
        Habitat.RIVER, Habitat.INLAND),
    REDFIN_PERCH("redfin_perch", FishingRolls.Rarity.COMMON, Material.SALMON, 30, 1,
        Habitat.RIVER, Habitat.INLAND),
    MOSSBACK_CARP("mossback_carp", FishingRolls.Rarity.COMMON, Material.COD, 20, 2,
        Habitat.SWAMP, Habitat.INLAND),
    SHORELINE_MACKEREL("shoreline_mackerel", FishingRolls.Rarity.COMMON, Material.COD, 15, 2,
        Habitat.OCEAN),

    MOONLIT_KOI("moonlit_koi", FishingRolls.Rarity.UNCOMMON, Material.TROPICAL_FISH, 35, 3,
        Habitat.RIVER, Habitat.INLAND),
    EMBERGILL_TROUT("embergill_trout", FishingRolls.Rarity.UNCOMMON, Material.SALMON, 35, 4,
        Habitat.RIVER, Habitat.COLD),
    GLASSSCALE_EEL("glassscale_eel", FishingRolls.Rarity.UNCOMMON, Material.COD, 30, 4,
        Habitat.SWAMP, Habitat.OCEAN),

    STORMFIN_TUNA("stormfin_tuna", FishingRolls.Rarity.RARE, Material.COD, 38, 10,
        Habitat.OCEAN),
    FROSTVEIL_CHAR("frostveil_char", FishingRolls.Rarity.RARE, Material.SALMON, 34, 10,
        Habitat.COLD, Habitat.RIVER),
    RIFTWATER_RAY("riftwater_ray", FishingRolls.Rarity.RARE, Material.PHANTOM_MEMBRANE, 28, 12,
        Habitat.SWAMP, Habitat.OCEAN),

    CROWNSCALE_COELACANTH("crownscale_coelacanth", FishingRolls.Rarity.LEGENDARY,
        Material.TROPICAL_FISH, 55, 30, Habitat.OCEAN, Habitat.COLD),
    STARFALL_STURGEON("starfall_sturgeon", FishingRolls.Rarity.LEGENDARY, Material.COD, 45, 35,
        Habitat.RIVER, Habitat.INLAND);

    private final ItemId itemId;
    private final FishingRolls.Rarity rarity;
    private final Material material;
    private final int weight;
    private final int reputation;
    private final Set<Habitat> habitats;

    FishingCatch(
        String itemId,
        FishingRolls.Rarity rarity,
        Material material,
        int weight,
        int reputation,
        Habitat... habitats
    ) {
        this.itemId = new ItemId(itemId);
        this.rarity = rarity;
        this.material = material;
        this.weight = weight;
        this.reputation = reputation;
        this.habitats = Set.of(habitats);
    }

    ItemId itemId() {
        return itemId;
    }

    FishingRolls.Rarity rarity() {
        return rarity;
    }

    Material material() {
        return material;
    }

    int reputation() {
        return reputation;
    }

    Set<Habitat> habitats() {
        return habitats;
    }

    static List<FishingCatch> pool(FishingRolls.Rarity rarity) {
        return Arrays.stream(values()).filter(entry -> entry.rarity == rarity).toList();
    }

    static FishingCatch byItemId(String rawItemId) {
        if (rawItemId == null) {
            return null;
        }
        return Arrays.stream(values()).filter(entry -> entry.itemId.value().equals(rawItemId)).findFirst().orElse(null);
    }

    static FishingCatch select(FishingRolls.Rarity rarity, Habitat habitat, double roll) {
        List<FishingCatch> pool = pool(rarity);
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No fishing catches configured for " + rarity);
        }
        int totalWeight = pool.stream().mapToInt(entry -> adjustedWeight(entry, habitat)).sum();
        double bounded = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        double target = bounded * totalWeight;
        int cumulative = 0;
        for (FishingCatch entry : pool) {
            cumulative += adjustedWeight(entry, habitat);
            if (target < cumulative) {
                return entry;
            }
        }
        return pool.getLast();
    }

    private static int adjustedWeight(FishingCatch entry, Habitat habitat) {
        return entry.weight * (entry.habitats.contains(habitat) ? 4 : 1);
    }

    enum Habitat {
        RIVER("River"),
        OCEAN("Ocean"),
        SWAMP("Wetland"),
        COLD("Cold Water"),
        INLAND("Inland Water");

        private final String displayName;

        Habitat(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        static Habitat fromBiome(String rawBiome) {
            String biome = rawBiome.toLowerCase(Locale.ROOT);
            if (biome.contains("ocean")) {
                return OCEAN;
            }
            if (biome.contains("river")) {
                return RIVER;
            }
            if (biome.contains("swamp") || biome.contains("mangrove")) {
                return SWAMP;
            }
            if (biome.contains("frozen") || biome.contains("snow") || biome.contains("ice")
                || biome.contains("cold") || biome.contains("grove")) {
                return COLD;
            }
            return INLAND;
        }
    }
}
