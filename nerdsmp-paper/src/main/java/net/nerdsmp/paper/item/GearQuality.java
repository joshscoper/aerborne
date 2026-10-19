package net.nerdsmp.paper.item;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The permanent workmanship tier applied to crafted equipment. */
public enum GearQuality {
    WROUGHT(0, "Wrought", "<gray>", 1.00D, 0, null, 0),
    TEMPERED(1, "Tempered", "<green>", 1.05D, 5, Material.IRON_INGOT, 8),
    REFINED(2, "Refined", "<aqua>", 1.11D, 15, Material.AMETHYST_SHARD, 12),
    MASTERWORK(3, "Masterwork", "<light_purple>", 1.18D, 30, Material.DIAMOND, 4),
    PEERLESS(4, "Peerless", "<gold>", 1.27D, 45, Material.NETHERITE_INGOT, 2);

    private static final Set<String> SCALABLE_STATS = Set.of(
        "damage", "projectile-damage", "defense", "mining-power", "fishing-power", "gathering-power", "excavation-power"
    );

    private final int tier;
    private final String displayName;
    private final String color;
    private final double multiplier;
    private final int requiredSmithingLevel;
    private final Material catalyst;
    private final int catalystAmount;

    GearQuality(
        int tier,
        String displayName,
        String color,
        double multiplier,
        int requiredSmithingLevel,
        Material catalyst,
        int catalystAmount
    ) {
        this.tier = tier;
        this.displayName = displayName;
        this.color = color;
        this.multiplier = multiplier;
        this.requiredSmithingLevel = requiredSmithingLevel;
        this.catalyst = catalyst;
        this.catalystAmount = catalystAmount;
    }

    public int tier() {
        return tier;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public double multiplier() {
        return multiplier;
    }

    public double bonusPercent() {
        return (multiplier - 1.0D) * 100.0D;
    }

    public int requiredSmithingLevel() {
        return requiredSmithingLevel;
    }

    public Material catalyst() {
        return catalyst;
    }

    public int catalystAmount() {
        return catalystAmount;
    }

    public Optional<GearQuality> next() {
        return tier >= PEERLESS.tier ? Optional.empty() : Optional.of(values()[tier + 1]);
    }

    public boolean scales(String stat) {
        return stat != null && SCALABLE_STATS.contains(stat.toLowerCase(Locale.ROOT));
    }

    public static GearQuality fromTier(Integer tier) {
        if (tier == null || tier < 0 || tier >= values().length) {
            return WROUGHT;
        }
        return values()[tier];
    }
}
