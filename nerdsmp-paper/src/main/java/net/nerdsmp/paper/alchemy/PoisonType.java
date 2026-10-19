package net.nerdsmp.paper.alchemy;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public enum PoisonType {
    NIGHTSHADE("Nightshade", "A lasting toxin that steadily strips health.", Color.fromRGB(92, 36, 120), 5,
        List.of(Material.SPIDER_EYE, Material.FERMENTED_SPIDER_EYE, Material.REDSTONE)),
    FROSTBITE("Frostbite", "Freezes, slows, and weakens the target.", Color.fromRGB(108, 210, 226), 15,
        List.of(Material.SNOWBALL, Material.BLUE_ICE, Material.FERMENTED_SPIDER_EYE)),
    CRIPPLECAP("Cripplecap", "Cripples movement and attack strength.", Color.fromRGB(126, 93, 69), 20,
        List.of(Material.BROWN_MUSHROOM, Material.BLUE_ICE, Material.FERMENTED_SPIDER_EYE)),
    MINDFOG("Mindfog", "Clouds sight, balance, and combat focus.", Color.fromRGB(48, 54, 86), 25,
        List.of(Material.INK_SAC, Material.POISONOUS_POTATO, Material.FERMENTED_SPIDER_EYE)),
    DREADSPORE("Dreadspore", "Smothers the target in weakening darkness.", Color.fromRGB(20, 33, 38), 30,
        List.of(Material.SCULK, Material.BROWN_MUSHROOM, Material.FERMENTED_SPIDER_EYE)),
    WIDOWMAKER("Widowmaker", "A concentrated wither toxin for hard targets.", Color.fromRGB(42, 20, 44), 35,
        List.of(Material.WITHER_ROSE, Material.GHAST_TEAR, Material.FERMENTED_SPIDER_EYE)),
    HEMLOCK("Hemlock", "Poisons the blood, drains hunger, and saps strength.", Color.fromRGB(68, 105, 37), 40,
        List.of(Material.POISONOUS_POTATO, Material.SPIDER_EYE, Material.WITHER_ROSE)),
    SUNDERING_ACID("Sundering Acid", "Burns steadily and leaves the target exposed.", Color.fromRGB(177, 207, 52), 45,
        List.of(Material.PRISMARINE_CRYSTALS, Material.BLAZE_POWDER, Material.FERMENTED_SPIDER_EYE));

    private final String displayName;
    private final String description;
    private final Color color;
    private final int requiredLevel;
    private final List<Material> ingredients;

    PoisonType(String displayName, String description, Color color, int requiredLevel, List<Material> ingredients) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.requiredLevel = requiredLevel;
        this.ingredients = List.copyOf(ingredients);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Color color() {
        return color;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public List<Material> ingredients() {
        return ingredients;
    }

    public int effectCount() {
        return switch (this) {
            case NIGHTSHADE, WIDOWMAKER -> 1;
            case FROSTBITE, CRIPPLECAP -> 2;
            case MINDFOG, DREADSPORE, HEMLOCK, SUNDERING_ACID -> 3;
        };
    }

    public void apply(LivingEntity target, int potency, double bonusPercent) {
        target.addPotionEffects(effects(potency, bonusPercent));
        if (this == FROSTBITE) {
            int freezeTicks = (int) Math.round((100 + Math.max(1, Math.min(3, potency)) * 40)
                * (1.0D + Math.max(0.0D, bonusPercent) / 100.0D));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
        }
    }

    public List<PotionEffect> effects(int potency, double bonusPercent) {
        int tier = Math.max(1, Math.min(3, potency));
        double scale = 1.0D + Math.max(0.0D, bonusPercent) / 100.0D;
        int duration = (int) Math.round((240 + tier * 80) * scale);
        int amplifier = Math.min(2, tier - 1 + (bonusPercent >= 40.0D ? 1 : 0));
        return switch (this) {
            case NIGHTSHADE -> List.of(effect(PotionEffectType.POISON, duration, Math.min(1, amplifier)));
            case FROSTBITE -> List.of(
                effect(PotionEffectType.SLOWNESS, duration, amplifier),
                effect(PotionEffectType.WEAKNESS, duration, Math.min(1, amplifier))
            );
            case CRIPPLECAP -> List.of(
                effect(PotionEffectType.SLOWNESS, duration, amplifier),
                effect(PotionEffectType.WEAKNESS, duration, Math.min(1, amplifier))
            );
            case MINDFOG -> List.of(
                effect(PotionEffectType.BLINDNESS, Math.min(duration, 180), 0),
                effect(PotionEffectType.NAUSEA, duration, 0),
                effect(PotionEffectType.WEAKNESS, duration, Math.min(1, amplifier))
            );
            case DREADSPORE -> List.of(
                effect(PotionEffectType.DARKNESS, Math.min(duration, 240), 0),
                effect(PotionEffectType.SLOWNESS, duration, Math.min(1, amplifier)),
                effect(PotionEffectType.WEAKNESS, duration, Math.min(1, amplifier))
            );
            case WIDOWMAKER -> List.of(
                effect(PotionEffectType.WITHER, Math.min(duration, 240 + tier * 40), Math.min(1, amplifier))
            );
            case HEMLOCK -> List.of(
                effect(PotionEffectType.POISON, duration, Math.min(1, amplifier)),
                effect(PotionEffectType.HUNGER, duration, amplifier),
                effect(PotionEffectType.WEAKNESS, duration, Math.min(1, amplifier))
            );
            case SUNDERING_ACID -> List.of(
                effect(PotionEffectType.POISON, duration, Math.min(1, amplifier)),
                effect(PotionEffectType.WEAKNESS, duration, amplifier),
                effect(PotionEffectType.GLOWING, duration, 0)
            );
        };
    }

    public int maximumDurationTicks(int potency, double bonusPercent) {
        int tier = Math.max(1, Math.min(3, potency));
        double scale = 1.0D + Math.max(0.0D, bonusPercent) / 100.0D;
        int duration = (int) Math.round((240 + tier * 80) * scale);
        return this == WIDOWMAKER ? Math.min(duration, 240 + tier * 40) : duration;
    }

    public Set<PotionEffectType> effectTypes() {
        return effects(1, 0.0D).stream().map(PotionEffect::getType).collect(Collectors.toUnmodifiableSet());
    }

    public int remainingDurationTicks(LivingEntity target) {
        return effectTypes().stream()
            .map(target::getPotionEffect)
            .filter(java.util.Objects::nonNull)
            .mapToInt(PotionEffect::getDuration)
            .max()
            .orElse(0);
    }

    public static Set<PotionEffectType> antidoteEffectTypes() {
        return java.util.Arrays.stream(values())
            .flatMap(poison -> poison.effectTypes().stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    private PotionEffect effect(PotionEffectType type, int duration, int amplifier) {
        return new PotionEffect(type, duration, amplifier);
    }

    public static PoisonType fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
