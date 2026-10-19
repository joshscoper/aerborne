package net.nerdsmp.paper.alchemy;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum ArcaneBrew {
    FIELD_REMEDY("Field Remedy", "A honey-and-apple tonic for the road.", Color.fromRGB(231, 184, 82), 5,
        600, 0.0D,
        List.of(spec(EffectKind.REGENERATION, 200, 0), spec(EffectKind.SPEED, 600, 0)),
        Material.GLASS_BOTTLE, Material.APPLE, Material.WHEAT, Material.HONEY_BOTTLE),
    TIDEWALKER("Tidewalker Draught", "Breathe beneath the surface and swim with the current.", Color.fromRGB(64, 184, 176), 18,
        1_200, 0.0D,
        List.of(spec(EffectKind.WATER_BREATHING, 1_200, 0), spec(EffectKind.DOLPHINS_GRACE, 600, 0)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.KELP, Material.PUFFERFISH),
    GROWTH("Growth Elixir", "Increases your size by 50%.", Color.fromRGB(229, 126, 52), 30, 3_600, 0.50D,
        List.of(spec(EffectKind.ABSORPTION, 3_600, 1), spec(EffectKind.SLOWNESS, 3_600, 0)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.CRIMSON_FUNGUS, Material.SLIME_BALL,
        Material.GHAST_TEAR),
    DIMINUTION("Diminution Draught", "Reduces your size by 40%.", Color.fromRGB(103, 202, 212), 30, 3_600, -0.40D,
        List.of(spec(EffectKind.SPEED, 3_600, 0), spec(EffectKind.JUMP_BOOST, 3_600, 0)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.WARPED_FUNGUS, Material.FERMENTED_SPIDER_EYE,
        Material.AMETHYST_SHARD),
    STONEBLOOD("Stoneblood Tonic", "Hardens the body at the cost of speed.", Color.fromRGB(112, 113, 116), 22,
        2_400, 0.0D,
        List.of(spec(EffectKind.RESISTANCE, 2_400, 0), spec(EffectKind.SLOWNESS, 2_400, 0)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.IRON_INGOT, Material.TURTLE_SCUTE),
    WINDWALKER("Windwalker Cordial", "Quickens your stride and steadies long falls.",
        Color.fromRGB(174, 229, 240), 25, 2_400, 0.0D,
        List.of(spec(EffectKind.SPEED, 2_400, 1), spec(EffectKind.SLOW_FALLING, 2_400, 0)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.BREEZE_ROD, Material.SUGAR),
    EMBERHEART("Emberheart Elixir", "Guards against flame while mending wounds.", Color.fromRGB(232, 79, 42), 35,
        1_800, 0.0D,
        List.of(spec(EffectKind.FIRE_RESISTANCE, 1_800, 0), spec(EffectKind.REGENERATION, 600, 0)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.MAGMA_CREAM, Material.GHAST_TEAR),
    RIFT_SIGHT("Rift Sight", "Reveals dark paths and favors rare discoveries.", Color.fromRGB(142, 93, 213), 40,
        3_600, 0.0D,
        List.of(spec(EffectKind.NIGHT_VISION, 3_600, 0), spec(EffectKind.LUCK, 3_600, 1)),
        Material.GLASS_BOTTLE, Material.NETHER_WART, Material.ECHO_SHARD, Material.GOLDEN_CARROT);

    private final String displayName;
    private final String description;
    private final Color color;
    private final int requiredLevel;
    private final int durationTicks;
    private final double scaleModifier;
    private final List<EffectSpec> effectSpecs;
    private final List<Material> ingredients;

    ArcaneBrew(
        String displayName,
        String description,
        Color color,
        int requiredLevel,
        int durationTicks,
        double scaleModifier,
        List<EffectSpec> effectSpecs,
        Material... ingredients
    ) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.requiredLevel = requiredLevel;
        this.durationTicks = durationTicks;
        this.scaleModifier = scaleModifier;
        this.effectSpecs = List.copyOf(effectSpecs);
        this.ingredients = List.copyOf(Arrays.asList(ingredients));
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

    public int durationTicks() {
        return durationTicks;
    }

    public double scaleModifier() {
        return scaleModifier;
    }

    public int effectCount() {
        return effectSpecs.size();
    }

    public List<PotionEffect> effects() {
        return effectSpecs.stream().map(EffectSpec::toPotionEffect).toList();
    }

    public List<Material> ingredients() {
        return ingredients;
    }

    public static ArcaneBrew fromId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static EffectSpec spec(EffectKind kind, int duration, int amplifier) {
        return new EffectSpec(kind, duration, amplifier);
    }

    private record EffectSpec(EffectKind kind, int duration, int amplifier) {
        private PotionEffect toPotionEffect() {
            return new PotionEffect(kind.type(), duration, amplifier);
        }
    }

    private enum EffectKind {
        ABSORPTION,
        SLOWNESS,
        SPEED,
        JUMP_BOOST,
        RESISTANCE,
        SLOW_FALLING,
        FIRE_RESISTANCE,
        REGENERATION,
        NIGHT_VISION,
        LUCK,
        WATER_BREATHING,
        DOLPHINS_GRACE;

        private PotionEffectType type() {
            return switch (this) {
                case ABSORPTION -> PotionEffectType.ABSORPTION;
                case SLOWNESS -> PotionEffectType.SLOWNESS;
                case SPEED -> PotionEffectType.SPEED;
                case JUMP_BOOST -> PotionEffectType.JUMP_BOOST;
                case RESISTANCE -> PotionEffectType.RESISTANCE;
                case SLOW_FALLING -> PotionEffectType.SLOW_FALLING;
                case FIRE_RESISTANCE -> PotionEffectType.FIRE_RESISTANCE;
                case REGENERATION -> PotionEffectType.REGENERATION;
                case NIGHT_VISION -> PotionEffectType.NIGHT_VISION;
                case LUCK -> PotionEffectType.LUCK;
                case WATER_BREATHING -> PotionEffectType.WATER_BREATHING;
                case DOLPHINS_GRACE -> PotionEffectType.DOLPHINS_GRACE;
            };
        }
    }
}
