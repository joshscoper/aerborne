package net.nerdsmp.paper.gathering;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

/**
 * XP values for verified harvests. This catalog never makes a block eligible for a harvest:
 * callers must still check maturity, growth provenance, permissions and the harvest outcome.
 */
public final class FarmingExperience {
    public static final double DEFAULT_MULTIPLIER = 3.0D;
    public static final double MIN_MULTIPLIER = 0.25D;
    public static final double MAX_MULTIPLIER = 10.0D;
    private static final long DEFAULT_CROP_BASE = 5L;
    private static final String XP_PATH = "progression.xp.";

    private static final Map<String, Crop> CROPS = Map.ofEntries(
        crop("WHEAT", "WHEAT", 5),
        crop("CARROTS", "CARROTS", 5), crop("CARROT", "CARROTS", 5),
        crop("POTATOES", "POTATOES", 5), crop("POTATO", "POTATOES", 5),
        crop("BEETROOTS", "BEETROOTS", 5), crop("BEETROOT", "BEETROOTS", 5),
        crop("NETHER_WART", "NETHER_WART", 6),
        crop("COCOA", "COCOA", 6), crop("COCOA_BEANS", "COCOA", 6),
        crop("SWEET_BERRY_BUSH", "SWEET_BERRY_BUSH", 5),
        crop("SWEET_BERRIES", "SWEET_BERRY_BUSH", 5),
        crop("CAVE_VINES", "CAVE_VINES", 5), crop("CAVE_VINES_PLANT", "CAVE_VINES", 5),
        crop("GLOW_BERRIES", "CAVE_VINES", 5),
        crop("PITCHER_CROP", "PITCHER_CROP", 8), crop("PITCHER_PLANT", "PITCHER_CROP", 8),
        crop("TORCHFLOWER_CROP", "TORCHFLOWER_CROP", 8), crop("TORCHFLOWER", "TORCHFLOWER_CROP", 8),
        crop("MELON", "MELON", 8), crop("MELON_SLICE", "MELON", 8),
        crop("PUMPKIN", "PUMPKIN", 8),
        crop("SUGAR_CANE", "SUGAR_CANE", 3),
        crop("CACTUS", "CACTUS", 3),
        crop("BAMBOO", "BAMBOO", 3)
    );

    private FarmingExperience() { }

    public static long baseXp(JavaPlugin plugin, Material material) {
        return baseXp(plugin.getConfig(), material);
    }

    /** Reads new settings with explicit fallbacks so older saved configs receive the rebalance. */
    public static long baseXp(ConfigurationSection configuration, Material material) {
        Crop crop = CROPS.get(material.name());
        if (crop == null) return 0L;
        Object override = configuration.get(XP_PATH + "farming-items." + material.name());
        if (!(override instanceof Number)) {
            override = configuration.get(XP_PATH + "farming-items." + crop.canonicalId());
        }
        return calculate(crop, configuration.getLong(XP_PATH + "farming-crop", DEFAULT_CROP_BASE),
            configuration.getDouble(XP_PATH + "farming-multiplier", DEFAULT_MULTIPLIER),
            override instanceof Number number ? number : null);
    }

    public static long experience(String material, long configuredCropBase, double configuredMultiplier) {
        return experience(material, configuredCropBase, configuredMultiplier, Map.of());
    }

    /** Overrides are base XP, before the multiplier; canonical crop IDs apply to item aliases too. */
    public static long experience(String material, long configuredCropBase, double configuredMultiplier,
                                  Map<String, ? extends Number> overrides) {
        if (material == null) return 0L;
        String normalized = material.toUpperCase(Locale.ROOT);
        Crop crop = CROPS.get(normalized);
        if (crop == null) return 0L;
        Number override = overrides.get(normalized);
        if (override == null) override = overrides.get(crop.canonicalId());
        return calculate(crop, configuredCropBase, configuredMultiplier, override);
    }

    private static long calculate(Crop crop, long configuredCropBase, double configuredMultiplier, Number override) {
        double base = Math.max(1L, configuredCropBase) * (crop.defaultBase() / (double) DEFAULT_CROP_BASE);
        if (override != null && Double.isFinite(override.doubleValue()) && override.doubleValue() >= 1.0D) {
            base = override.doubleValue();
        }
        return Math.max(1L, Math.round(base * multiplier(configuredMultiplier)));
    }

    public static double multiplier(double configured) {
        if (!Double.isFinite(configured)) return DEFAULT_MULTIPLIER;
        return Math.clamp(configured, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    /** Known material IDs only; not a substitute for maturity or placed-block checks. */
    public static boolean recognized(String material) {
        return material != null && CROPS.containsKey(material.toUpperCase(Locale.ROOT));
    }

    private static Map.Entry<String, Crop> crop(String material, String canonicalId, long defaultBase) {
        return Map.entry(material, new Crop(canonicalId, defaultBase));
    }

    private record Crop(String canonicalId, long defaultBase) { }
}
