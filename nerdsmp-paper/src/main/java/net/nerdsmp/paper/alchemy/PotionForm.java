package net.nerdsmp.paper.alchemy;

import org.bukkit.Material;

import java.util.List;

public enum PotionForm {
    DRINKABLE("Drinkable", Material.POTION, List.of()),
    SPLASH("Splash", Material.SPLASH_POTION, List.of(Material.GUNPOWDER)),
    LINGERING("Lingering", Material.LINGERING_POTION,
        List.of(Material.GUNPOWDER, Material.DRAGON_BREATH));

    private final String displayName;
    private final Material material;
    private final List<Material> reagents;

    PotionForm(String displayName, Material material, List<Material> reagents) {
        this.displayName = displayName;
        this.material = material;
        this.reagents = List.copyOf(reagents);
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public List<Material> reagents() {
        return reagents;
    }

    public String potionName(TraditionalPotion potion, PotionVariant variant) {
        String formPrefix = this == DRINKABLE ? "" : displayName + " ";
        return variant.namePrefix() + formPrefix + "Potion of " + potion.displayName();
    }
}
