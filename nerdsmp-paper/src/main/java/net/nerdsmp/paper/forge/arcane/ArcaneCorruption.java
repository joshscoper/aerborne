package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.engine.ItemStats;
import net.nerdsmp.paper.item.EquipmentMaterials;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Corruption: a deliberate, fully previewed trade. The player picks one corruption, sees its exact
 * bonus and drawback, and can always cleanse it again for a fixed price. Nothing about it is random.
 *
 * <ul>
 *   <li><b>Bonus:</b> the listed stats, merged once into the item's stats.</li>
 *   <li><b>Drawbacks:</b> the item loses durability faster ({@code corruption.durability-multiplier}),
 *       uses Arcane Capacity, and takes no Chosen Imbuement, signature or attunement until cleansed.</li>
 *   <li><b>Cleansing</b> removes exactly the corruption and nothing else, and always succeeds.</li>
 * </ul>
 */
public enum ArcaneCorruption {
    BLOODTHIRST("bloodthirst", "Bloodthirst", Material.REDSTONE_BLOCK, "Melee weapons",
        Map.of("damage-percent", 10.0D)),
    IRONBLOOD("ironblood", "Ironblood", Material.IRON_BLOCK, "Armor and shields",
        Map.of("defense", 4.0D)),
    GREED("greed", "Greed", Material.GOLD_BLOCK, "Tools and fishing rods",
        Map.of("skill-xp-percent", 8.0D, "job-marks-percent", 8.0D)),
    FEVER("fever", "Fever", Material.MAGMA_BLOCK, "Bows and crossbows",
        Map.of("projectile-damage-percent", 10.0D));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String equipment;
    private final Map<String, Double> stats;

    ArcaneCorruption(String id, String displayName, Material icon, String equipment, Map<String, Double> stats) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.equipment = equipment;
        this.stats = ItemStats.validate(stats, false);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String equipment() {
        return equipment;
    }

    public Map<String, Double> stats() {
        return stats;
    }

    public boolean supports(Material material) {
        if (material == null || material.isAir()) return false;
        return switch (this) {
            case BLOODTHIRST -> EquipmentMaterials.isMeleeWeapon(material);
            case IRONBLOOD -> EquipmentMaterials.isArmor(material) || material == Material.SHIELD;
            case GREED -> EquipmentMaterials.isMiningTool(material) || EquipmentMaterials.isHarvestTool(material)
                || EquipmentMaterials.isExcavationTool(material) || material == Material.FISHING_ROD;
            case FEVER -> EquipmentMaterials.isRangedWeapon(material);
        };
    }

    /** Lore lines for the bonus, such as {@code +10% Damage Bonus}. */
    public String bonusText() {
        return stats.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> "+" + format(entry.getValue()) + (entry.getKey().endsWith("-percent") ? "%" : "") + " " + label(entry.getKey()))
            .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static String format(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String label(String stat) {
        return switch (stat) {
            case "damage-percent" -> "Damage";
            case "defense" -> "Defense";
            case "skill-xp-percent" -> "Skill XP";
            case "job-marks-percent" -> "Work Marks";
            case "projectile-damage-percent" -> "Projectile Damage";
            default -> stat;
        };
    }

    public static Optional<ArcaneCorruption> fromId(String id) {
        if (id == null) return Optional.empty();
        for (ArcaneCorruption corruption : values()) if (corruption.id.equals(id.trim().toLowerCase(Locale.ROOT))) return Optional.of(corruption);
        return Optional.empty();
    }
}
