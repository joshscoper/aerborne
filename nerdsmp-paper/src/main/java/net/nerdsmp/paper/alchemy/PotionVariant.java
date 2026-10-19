package net.nerdsmp.paper.alchemy;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.Optional;
import java.util.Set;

public enum PotionVariant {
    STANDARD("Standard", "", null),
    EXTENDED("Extended", "Extended ", Material.REDSTONE),
    POTENT("Potent", "Potent ", Material.GLOWSTONE_DUST);

    private final String displayName;
    private final String namePrefix;
    private final Material reagent;
    private static final Set<PotionType> EXTENDABLE = Set.of(
        PotionType.FIRE_RESISTANCE,
        PotionType.INVISIBILITY,
        PotionType.LEAPING,
        PotionType.NIGHT_VISION,
        PotionType.POISON,
        PotionType.REGENERATION,
        PotionType.SLOW_FALLING,
        PotionType.SLOWNESS,
        PotionType.STRENGTH,
        PotionType.SWIFTNESS,
        PotionType.TURTLE_MASTER,
        PotionType.WATER_BREATHING,
        PotionType.WEAKNESS
    );
    private static final Set<PotionType> UPGRADEABLE = Set.of(
        PotionType.HARMING,
        PotionType.HEALING,
        PotionType.LEAPING,
        PotionType.POISON,
        PotionType.REGENERATION,
        PotionType.SLOWNESS,
        PotionType.STRENGTH,
        PotionType.SWIFTNESS,
        PotionType.TURTLE_MASTER
    );

    PotionVariant(String displayName, String namePrefix, Material reagent) {
        this.displayName = displayName;
        this.namePrefix = namePrefix;
        this.reagent = reagent;
    }

    public String displayName() {
        return displayName;
    }

    public String namePrefix() {
        return namePrefix;
    }

    public Material reagent() {
        return reagent;
    }

    public Optional<PotionType> resolve(PotionType base) {
        if (this == STANDARD) {
            return Optional.of(base);
        }
        if (!supports(base)) {
            return Optional.empty();
        }
        String prefix = this == EXTENDED ? "LONG_" : "STRONG_";
        try {
            return Optional.of(PotionType.valueOf(prefix + base.name()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean supports(PotionType base) {
        return this == STANDARD || this == EXTENDED && EXTENDABLE.contains(base)
            || this == POTENT && UPGRADEABLE.contains(base);
    }
}
