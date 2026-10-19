package net.nerdsmp.paper.forge;

import org.bukkit.Material;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public enum GemType {
    RUBY("Ruby", "<red>", Material.REDSTONE, "damage-flat", 0.5D),
    SAPPHIRE("Sapphire", "<blue>", Material.LAPIS_LAZULI, "defense", 1.0D),
    EMERALD("Emerald", "<green>", Material.EMERALD, "skill-xp-percent", 3.0D),
    TOPAZ("Topaz", "<gold>", Material.GLOWSTONE_DUST, "mining-power", 0.75D),
    AQUAMARINE("Aquamarine", "<aqua>", Material.PRISMARINE_CRYSTALS, "fishing-power", 0.75D),
    AMETHYST("Amethyst", "<light_purple>", Material.AMETHYST_SHARD, "damage-percent", 2.0D),
    CITRINE("Citrine", "<yellow>", Material.RAW_GOLD, "job-marks-percent", 2.5D),
    JADE("Jade", "<dark_green>", Material.SLIME_BALL, "gathering-power", 1.0D),
    ONYX("Onyx", "<dark_gray>", Material.ECHO_SHARD, "durability-save-percent", 6.0D),
    MOONSTONE("Moonstone", "<white>", Material.QUARTZ, "treasure-find-percent", 3.0D),
    PRISMATIC("Prismatic Gem", "<gradient:#ff70f6:#67e8f9>", Material.NETHER_STAR, Map.of(
        "damage-percent", 2.0D,
        "defense", 2.0D,
        "skill-xp-percent", 2.5D,
        "geode-find-percent", 3.0D,
        "treasure-find-percent", 3.0D
    ));

    private final String displayName;
    private final String color;
    private final Material material;
    private final Map<String, Double> valuesPerTier;

    GemType(String displayName, String color, Material material, String stat, double valuePerTier) {
        this.displayName = displayName;
        this.color = color;
        this.material = material;
        this.valuesPerTier = Map.of(stat, valuePerTier);
    }

    GemType(String displayName, String color, Material material, Map<String, Double> valuesPerTier) {
        this.displayName = displayName;
        this.color = color;
        this.material = material;
        this.valuesPerTier = Map.copyOf(valuesPerTier);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public Material material() {
        return material;
    }

    public Map<String, Double> stats(int tier) {
        Map<String, Double> scaled = new LinkedHashMap<>();
        valuesPerTier.forEach((stat, value) -> scaled.put(stat, value * clampTier(tier)));
        return Map.copyOf(scaled);
    }

    public String effect(int tier) {
        return stats(tier).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> effect(entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", "));
    }

    public boolean prismatic() {
        return this == PRISMATIC;
    }

    private String effect(String stat, double value) {
        String shown = Math.rint(value) == value ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
        String suffix = stat.endsWith("-percent") ? "%" : "";
        return "+" + shown + suffix + " " + switch (stat) {
            case "damage-flat" -> "Damage";
            case "defense" -> "Defense";
            case "skill-xp-percent" -> "Skill XP";
            case "mining-power" -> "Mining Power";
            case "fishing-power" -> "Fishing Power";
            case "damage-percent" -> "Damage";
            case "job-marks-percent" -> "Work Marks";
            case "gathering-power" -> "Gathering Power";
            case "durability-save-percent" -> "Durability Save";
            case "treasure-find-percent" -> "Treasure Find";
            case "geode-find-percent" -> "Geode Find";
            default -> stat;
        };
    }

    public static GemType fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static int clampTier(int tier) {
        return Math.max(1, Math.min(4, tier));
    }
}
