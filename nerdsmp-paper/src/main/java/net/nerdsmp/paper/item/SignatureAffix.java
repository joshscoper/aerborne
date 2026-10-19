package net.nerdsmp.paper.item;

import net.nerdsmp.paper.engine.ItemStats;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/** Catalog-bound effects: an item name or a forged lore line never grants a signature. */
public enum SignatureAffix {
    FURNACE_HEART("furnace_heart", "Furnace Heart",
        "Smelts mined metal drops. Also earns Smithing XP.", Map.of()),
    DEEP_RESONANCE("deep_resonance", "Deep Resonance",
        "+35% geode chance and +4% excavation finds.",
        Map.of("geode-find-percent", 35.0, "excavation-find-percent", 4.0)),
    HARVEST_MOON("harvest_moon", "Harvest Moon",
        "+20% harvest yield and +2 gathering power.",
        Map.of("harvest-extra-percent", 20.0, "gathering-power", 2.0)),
    TIDEWEAVER("tideweaver", "Tideweaver",
        "+20% fishing luck and +2 fishing power.",
        Map.of("fishing-luck-percent", 20.0, "fishing-power", 2.0)),
    SECOND_DAWN("second_dawn", "Second Dawn",
        "+2% life steal and +3 defense.", Map.of("life-steal-percent", 2.0, "defense", 3.0)),
    HUNTERS_MERCY("hunters_mercy", "Hunter's Mercy",
        "+25% damage against enemies below 30% health.", Map.of("execute-damage-percent", 25.0)),
    ASTRAL_INSIGHT("astral_insight", "Astral Insight",
        "+10% activity skill XP and +5% job Marks.",
        Map.of("skill-xp-percent", 10.0, "job-marks-percent", 5.0)),
    EVERLASTING("everlasting", "Everlasting",
        "+30% chance to prevent durability loss.", Map.of("durability-save-percent", 30.0)),
    VENOM_HEART("venom_heart", "Venom Heart",
        "+35% poison potency.", Map.of("poison-potency-percent", 35.0)),
    STORMSTRING("stormstring", "Stormstring",
        "+25% projectile damage.", Map.of("projectile-damage-percent", 25.0));

    private final String id;
    private final String displayName;
    private final String description;
    private final Map<String, Double> stats;

    SignatureAffix(String id, String displayName, String description, Map<String, Double> stats) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.stats = ItemStats.validate(stats, false);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public Map<String, Double> stats() { return stats; }

    public static Optional<SignatureAffix> fromId(String id) {
        return Arrays.stream(values()).filter(signature -> signature.id.equals(id)).findFirst();
    }

    public boolean supports(Material material, PaperItemService.GearSlot slot, String rarity) {
        if (material == null || slot == null || !"MYTHIC".equals(rarity)
            || slot == PaperItemService.GearSlot.NONE || EquipmentMaterials.slot(material) != slot) return false;
        return switch (this) {
            case FURNACE_HEART -> material.name().endsWith("_PICKAXE");
            case DEEP_RESONANCE -> material.name().endsWith("_PICKAXE") || EquipmentMaterials.isExcavationTool(material);
            case HARVEST_MOON -> EquipmentMaterials.isHarvestTool(material);
            case TIDEWEAVER -> material == Material.FISHING_ROD;
            case SECOND_DAWN -> EquipmentMaterials.isArmor(material) || material == Material.SHIELD;
            case HUNTERS_MERCY, VENOM_HEART -> EquipmentMaterials.isMeleeWeapon(material);
            case ASTRAL_INSIGHT, EVERLASTING -> EquipmentMaterials.isEquipment(material);
            case STORMSTRING -> EquipmentMaterials.isRangedWeapon(material);
        };
    }
}
