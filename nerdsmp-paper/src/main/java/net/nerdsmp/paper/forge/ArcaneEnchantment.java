package net.nerdsmp.paper.forge;

import net.nerdsmp.paper.item.EquipmentMaterials;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public enum ArcaneEnchantment {
    KEEN_EDGE("Keen Edge", 5, "Raises weapon damage by 5% per level.", "damage-percent", 5.0D),
    WARDING("Warding", 5, "Adds 3 defense per level.", "defense", 3.0D),
    SCHOLAR("Scholar", 5, "Raises all skill XP by 6% per level.", "skill-xp-percent", 6.0D),
    PROSPECTOR("Prospector", 5, "Raises geode discovery odds by 18% per level.", "geode-find-percent", 18.0D),
    PRESERVATION("Preservation", 5, "Has a 12% chance per level to prevent durability loss.",
        "durability-save-percent", 12.0D),
    REAPING("Reaping", 5, "Has an 8% chance per level to add another crop drop.",
        "harvest-extra-percent", 8.0D),
    SOULSIPHON("Soulsiphon", 5, "Heals for 1.2% of PvE damage per level.", "life-steal-percent", 1.2D),
    EXECUTIONER("Executioner", 5, "Deals 5% more damage per level to wounded enemies.",
        "execute-damage-percent", 5.0D),
    ANGLER("Angler", 5, "Raises fishing rarity and bite speed by 8% per level.",
        "fishing-luck-percent", 8.0D),
    ARCHAEOLOGIST("Archaeologist", 5, "Raises excavation find odds by 10% per level.",
        "excavation-find-percent", 10.0D),
    MARKSMAN("Marksman", 5, "Raises projectile damage by 5% per level.",
        "projectile-damage-percent", 5.0D),
    VENOMBOUND("Venombound", 5, "Raises poison duration and strength by 10% per level.",
        "poison-potency-percent", 10.0D),
    // Arcane Forge 2.0 effect enchantments. Appended so existing stored order and ids are unchanged.
    BLOODLETTER("Bloodletter", 5, "Full-strength hits make the target bleed for 8% of the hit per level.",
        "bleed-percent", 8.0D),
    VEINSEEKER("Veinseeker", 5, "Mining natural ore also mines up to 2 connected ores of that kind per level.",
        "vein-size", 2.0D),
    CHAIN_HARVEST("Chain Harvest", 5, "Breaking a ripe crop also harvests up to 2 connected ripe crops per level.",
        "chain-harvest", 2.0D);

    private final String displayName;
    private final int maximumLevel;
    private final String description;
    private final String stat;
    private final double valuePerLevel;

    ArcaneEnchantment(
        String displayName,
        int maximumLevel,
        String description,
        String stat,
        double valuePerLevel
    ) {
        this.displayName = displayName;
        this.maximumLevel = maximumLevel;
        this.description = description;
        this.stat = stat;
        this.valuePerLevel = valuePerLevel;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    public String description() {
        return description;
    }

    public Map<String, Double> stats(int level) {
        return Map.of(stat, valuePerLevel * Math.max(1, Math.min(maximumLevel, level)));
    }

    public boolean supports(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return supports(item.getType());
    }

    public boolean supports(Material material) {
        if (material == null || material == Material.AIR) {
            return false;
        }
        return switch (this) {
            case KEEN_EDGE -> EquipmentMaterials.isMeleeWeapon(material);
            case SOULSIPHON, EXECUTIONER -> EquipmentMaterials.isWeapon(material);
            case WARDING -> EquipmentMaterials.isArmor(material) || material == Material.SHIELD;
            case SCHOLAR -> EquipmentMaterials.isEquipment(material);
            case PROSPECTOR -> EquipmentMaterials.isMiningTool(material);
            case PRESERVATION -> EquipmentMaterials.isEquipment(material);
            case REAPING -> EquipmentMaterials.isHarvestTool(material);
            case ANGLER -> material == Material.FISHING_ROD;
            case ARCHAEOLOGIST -> EquipmentMaterials.isExcavationTool(material);
            case MARKSMAN -> EquipmentMaterials.isRangedWeapon(material);
            case VENOMBOUND -> EquipmentMaterials.isWeapon(material);
            case BLOODLETTER -> EquipmentMaterials.isMeleeWeapon(material);
            case VEINSEEKER -> material.name().endsWith("_PICKAXE");
            case CHAIN_HARVEST -> EquipmentMaterials.isHarvestTool(material);
        };
    }

    public static ArcaneEnchantment fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
