package net.nerdsmp.paper.engine;

import net.nerdsmp.paper.item.EquipmentMaterials;
import net.nerdsmp.paper.item.PaperItemService.GearSlot;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** An editor draft is data, not a live item. Identity is stable once published. Text is literal player-facing text. */
public record ItemBlueprint(String id, String name, Material material, String rarity, int sockets, int itemLevel,
                            String setId, List<String> lore, Map<String, Double> stats) {
    public static final Set<String> RARITIES = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "CRAFTED");
    public ItemBlueprint {
        id = identifier(id);
        name = text(name, 64, "Item name");
        if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR
            || material == Material.BARRIER || material == Material.STRUCTURE_VOID)
            throw new IllegalArgumentException("Choose an obtainable item material.");
        if (rarity == null || !RARITIES.contains(rarity.toUpperCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unknown item rarity.");
        rarity = rarity.toUpperCase(Locale.ROOT);
        if (sockets < 0 || sockets > 4 || itemLevel < 1 || itemLevel > 100)
            throw new IllegalArgumentException("Sockets must be 0–4 and item level must be 1–100.");
        setId = setId == null || setId.isBlank() ? null : identifier(setId);
        if (lore == null || lore.size() > 8) throw new IllegalArgumentException("Use at most eight lore lines.");
        lore = lore.stream().map(line -> text(line, 160, "Lore")).toList();
        stats = ItemStats.validate(stats, false);
        GearSlot slot = EquipmentMaterials.slot(material);
        if (slot == GearSlot.NONE && (sockets != 0 || setId != null || !stats.isEmpty()))
            throw new IllegalArgumentException("Only equipment can have sockets, stats or set membership.");
        if (!EquipmentMaterials.isMeleeWeapon(material) && !EquipmentMaterials.isTool(material) && stats.containsKey("damage"))
            throw new IllegalArgumentException("Melee damage requires a weapon or tool.");
        if (!EquipmentMaterials.isRangedWeapon(material) && stats.containsKey("projectile-damage"))
            throw new IllegalArgumentException("Projectile damage requires a bow, crossbow or trident.");
    }
    public GearSlot slot() { return EquipmentMaterials.slot(material); }
    public static String identifier(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}"))
            throw new IllegalArgumentException("Use a lowercase ID with up to 48 letters, numbers, underscores or hyphens.");
        return id;
    }
    public static String text(String value, int maximum, String label) {
        if (value == null || value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + " must be readable text, up to " + maximum + " characters.");
        return value.strip();
    }
}
