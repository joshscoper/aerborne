package net.nerdsmp.paper.alchemy;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Every effect-bearing vanilla potion family available through the workshop. */
public enum TraditionalPotion {
    HEALING("Healing", PotionType.HEALING, 0,
        Material.GLISTERING_MELON_SLICE),
    SWIFTNESS("Swiftness", PotionType.SWIFTNESS, 3,
        Material.SUGAR),
    FIRE_RESISTANCE("Fire Resistance", PotionType.FIRE_RESISTANCE, 5,
        Material.MAGMA_CREAM),
    NIGHT_VISION("Night Vision", PotionType.NIGHT_VISION, 7,
        Material.GOLDEN_CARROT),
    LEAPING("Leaping", PotionType.LEAPING, 9,
        Material.RABBIT_FOOT),
    WATER_BREATHING("Water Breathing", PotionType.WATER_BREATHING, 11,
        Material.PUFFERFISH),
    POISON("Poison", PotionType.POISON, 13,
        Material.SPIDER_EYE),
    REGENERATION("Regeneration", PotionType.REGENERATION, 15,
        Material.GHAST_TEAR),
    STRENGTH("Strength", PotionType.STRENGTH, 17,
        Material.BLAZE_POWDER),
    SLOW_FALLING("Slow Falling", PotionType.SLOW_FALLING, 19,
        Material.PHANTOM_MEMBRANE),
    WEAKNESS("Weakness", PotionType.WEAKNESS, 21,
        Material.FERMENTED_SPIDER_EYE),
    INVISIBILITY("Invisibility", PotionType.INVISIBILITY, 23,
        Material.GOLDEN_CARROT, Material.FERMENTED_SPIDER_EYE),
    SLOWNESS("Slowness", PotionType.SLOWNESS, 25,
        Material.SUGAR, Material.FERMENTED_SPIDER_EYE),
    HARMING("Harming", PotionType.HARMING, 27,
        Material.GLISTERING_MELON_SLICE, Material.FERMENTED_SPIDER_EYE),
    TURTLE_MASTER("the Turtle Master", PotionType.TURTLE_MASTER, 29,
        Material.TURTLE_SCUTE),
    INFESTED("Infestation", PotionType.INFESTED, 31,
        Material.STONE),
    OOZING("Oozing", PotionType.OOZING, 33,
        Material.SLIME_BLOCK),
    WEAVING("Weaving", PotionType.WEAVING, 35,
        Material.COBWEB),
    WIND_CHARGED("Wind Charging", PotionType.WIND_CHARGED, 37,
        Material.BREEZE_ROD),
    LUCK("Luck", PotionType.LUCK, 45,
        Material.RABBIT_FOOT, Material.LILY_PAD);

    private final String displayName;
    private final PotionType baseType;
    private final int requiredLevel;
    private final List<Material> reagents;

    TraditionalPotion(String displayName, PotionType baseType, int requiredLevel, Material... reagents) {
        this.displayName = displayName;
        this.baseType = baseType;
        this.requiredLevel = requiredLevel;
        this.reagents = List.copyOf(Arrays.asList(reagents));
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public PotionType baseType() {
        return baseType;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public List<Material> ingredients() {
        List<Material> ingredients = new java.util.ArrayList<>();
        ingredients.add(Material.GLASS_BOTTLE);
        if (this != WEAKNESS) {
            ingredients.add(Material.NETHER_WART);
        }
        ingredients.addAll(reagents);
        return List.copyOf(ingredients);
    }

    public Set<PotionType> availableTypes() {
        Set<PotionType> result = new LinkedHashSet<>();
        for (PotionVariant variant : PotionVariant.values()) {
            variant.resolve(baseType).ifPresent(result::add);
        }
        return Set.copyOf(result);
    }

    public static Set<PotionType> coveredTypes() {
        Set<PotionType> result = new LinkedHashSet<>();
        Arrays.stream(values()).map(TraditionalPotion::availableTypes).forEach(result::addAll);
        return Set.copyOf(result);
    }
}
