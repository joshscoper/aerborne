package net.nerdsmp.paper.item;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

/**
 * What ordinary gear with no NerdSMP data is worth to Nyra. It always comes back as less than it took to make, so
 * nothing here can be farmed: raw materials for plain kit, dust only for what's rare or has had XP spent on it.
 */
public final class Salvage {
    /** Dust, and an amount of one material. */
    public record Yield(int dust, Material material, int amount) {
        public static final Yield NOTHING = new Yield(0, null, 0);
        public boolean worthless() { return dust == 0 && amount == 0; }
    }

    private static final Set<String> PIECES = Set.of("SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "SPEAR", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private Salvage() { }

    /** True for a vanilla weapon, tool or piece of armour. */
    public static boolean gear(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT || type == Material.MACE || type == Material.SHIELD
            || PIECES.contains(type.name().substring(type.name().lastIndexOf('_') + 1)) && type.name().contains("_");
    }

    /** {@code condition} is the share of durability left, 0 to 1; a wreck gives back less, but never nothing. */
    public static Yield vanilla(Material type, int enchantmentLevels, double condition) {
        if (!gear(type)) return Yield.NOTHING;
        String tier = type.name().substring(0, type.name().indexOf('_') < 0 ? type.name().length() : type.name().indexOf('_')).toUpperCase(Locale.ROOT);
        Yield base = switch (tier) {
            case "LEATHER" -> new Yield(0, Material.RABBIT_HIDE, 2);
            case "CHAINMAIL", "IRON", "COPPER" -> new Yield(0, tier.equals("COPPER") ? Material.COPPER_NUGGET : Material.IRON_NUGGET, 4);
            case "GOLDEN" -> new Yield(0, Material.GOLD_NUGGET, 4);
            case "DIAMOND" -> new Yield(2, null, 0);
            case "NETHERITE" -> new Yield(6, Material.NETHERITE_SCRAP, 1);
            case "BOW", "CROSSBOW" -> new Yield(0, Material.STRING, 1);
            case "TRIDENT" -> new Yield(2, Material.PRISMARINE_SHARD, 2);
            case "MACE" -> new Yield(3, Material.BREEZE_ROD, 1);
            default -> Yield.NOTHING;
        };
        int dust = base.dust() + Math.max(0, enchantmentLevels) / 2;
        int amount = base.amount() == 0 ? 0 : Math.max(1, (int) Math.floor(base.amount() * Math.clamp(condition, 0, 1)));
        return new Yield(dust, amount == 0 ? null : base.material(), amount);
    }
}
