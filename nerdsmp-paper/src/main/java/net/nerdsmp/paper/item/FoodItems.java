package net.nerdsmp.paper.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.List;
import java.util.Optional;

/** Shared presentation for ordinary food; native consumption and prepared-meal identity stay intact. */
public final class FoodItems {
    private static final NamespacedKey VERSION = new NamespacedKey("nerdsmp", "food_template");
    private static final NamespacedKey SECONDS = new NamespacedKey("nerdsmp", "food_template_seconds");
    private static final NamespacedKey RECOVERY = new NamespacedKey("nerdsmp", "food_template_recovery");
    private static boolean enabled;
    private static Style current = new Style(300, 25);
    private FoodItems() { }
    static void enabled(boolean value) { enabled = value; }
    static void configure(boolean value, RecoveryRules rules) {
        current = new Style(rules.foodSeconds(), rules.foodRecoveryPercent());
        enabled = value;
    }
    public static Optional<ItemStack> upgrade(ItemStack item) {
        if (!enabled || item == null || item.getType().isAir()) return Optional.empty();
        var food = item.getData(DataComponentTypes.FOOD);
        if (food == null) return Optional.empty();
        boolean full = !food.canAlwaysEat();
        boolean styled = item.hasItemMeta() && matches(item, item.getItemMeta(), current);
        // Current presentation is left alone, including any additional metadata owned by another system.
        boolean present = !styled && ordinary(item);
        if (!full && !present) return Optional.empty();
        ItemStack copy = present ? withoutTemplate(item) : item.clone();
        if (full) copy.setData(DataComponentTypes.FOOD, food.toBuilder().canAlwaysEat(true));
        if (present) {
            var meta = copy.getItemMeta();
            Component name = name(item.getType());
            meta.itemName(name); meta.displayName(name); meta.lore(current.lore());
            var data = meta.getPersistentDataContainer();
            data.set(VERSION, PersistentDataType.INTEGER, 1);
            data.set(SECONDS, PersistentDataType.INTEGER, current.seconds());
            data.set(RECOVERY, PersistentDataType.DOUBLE, current.recovery());
            copy.setItemMeta(meta);
        }
        return Optional.of(copy);
    }
    /**
     * Plain food for trading and requests: ordinary, or made by the vanilla recipe (crafted bread, pumpkin pie and the
     * like carry only the kitchen's staple tags). Prepared meals and anything else special are not plain.
     */
    public static boolean plain(ItemStack item) { return ordinary(item) || KitchenService.isStaple(item); }

    /** Strip only verified server presentation; extra lore, names, enchantments and tags remain special. */
    public static boolean ordinary(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.CAVE_AIR || item.getType() == Material.VOID_AIR) return false;
        item = ItemPresentation.original(item);
        if (!item.hasItemMeta()) return true;
        // Metadata-bearing food needs Paper's live item registry; pure planners keep it protected.
        if (Bukkit.getServer() == null) return false;
        var original = item.getType().getDefaultData(DataComponentTypes.FOOD);
        var food = item.getData(DataComponentTypes.FOOD);
        if (original == null || food == null) return false;
        ItemStack copy = withoutTemplate(item);
        if (!food.equals(original) && !food.equals(original.toBuilder().canAlwaysEat(true).build())) return false;
        copy.resetData(DataComponentTypes.FOOD);
        return !copy.hasItemMeta();
    }
    /** Prepared recipes replace the provision template instead of inheriting its ordinary-food tags. */
    static ItemStack withoutTemplate(ItemStack item) {
        ItemStack copy = ItemPresentation.original(item).clone();
        if (!copy.hasItemMeta()) return copy;
        var meta = copy.getItemMeta();
        Style saved = saved(meta);
        if (saved == null || !matches(item, meta, saved)) return copy;
        meta.itemName(null); meta.displayName(null); meta.lore(null);
        var data = meta.getPersistentDataContainer();
        data.remove(VERSION); data.remove(SECONDS); data.remove(RECOVERY);
        copy.setItemMeta(meta);
        return copy;
    }
    private static Style saved(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        if (!Integer.valueOf(1).equals(data.get(VERSION, PersistentDataType.INTEGER))) return null;
        Integer seconds = data.get(SECONDS, PersistentDataType.INTEGER);
        Double recovery = data.get(RECOVERY, PersistentDataType.DOUBLE);
        if (seconds == null || seconds < 10 || seconds > 3600 || recovery == null || !Double.isFinite(recovery) || recovery < 0 || recovery > 100) return null;
        return current.seconds() == seconds && current.recovery() == recovery ? current : new Style(seconds, recovery);
    }
    private static boolean matches(ItemStack item, ItemMeta meta, Style style) {
        var data = meta.getPersistentDataContainer();
        return Integer.valueOf(1).equals(data.get(VERSION, PersistentDataType.INTEGER))
            && Integer.valueOf(style.seconds()).equals(data.get(SECONDS, PersistentDataType.INTEGER))
            && Double.valueOf(style.recovery()).equals(data.get(RECOVERY, PersistentDataType.DOUBLE))
            && name(item.getType()).equals(meta.itemName()) && name(item.getType()).equals(meta.displayName())
            && style.lore().equals(meta.lore());
    }
    private static Component name(Material material) {
        return Component.translatable(material.translationKey()).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
    }
    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    private record Style(int seconds, double recovery, List<Component> lore) {
        Style(int seconds, double recovery) {
            this(seconds, recovery, List.of(
                line("------------------------", NamedTextColor.DARK_GRAY),
                line("PROVISIONS", NamedTextColor.GREEN),
                line("Well Fed | " + duration(seconds), NamedTextColor.WHITE),
                line("+" + PlayerStatsService.number(recovery) + "% recovery outside combat.", NamedTextColor.GRAY),
                line("Eat even while full. Well Fed does not stack.", NamedTextColor.GRAY),
                line("Latest food replaces Well Fed. Cooking adds buffs.", NamedTextColor.GRAY),
                line("------------------------", NamedTextColor.DARK_GRAY)));
        }
        private static String duration(int seconds) {
            return seconds % 60 == 0 ? seconds / 60 + "m" : seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
        }
    }
}
