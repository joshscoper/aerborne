package net.nerdsmp.paper.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** A reversible visual layer: stripping it never strips an enchantment, recipe identity or custom tag. */
public final class ItemPresentation {
    private static final NamespacedKey VERSION = key("item_template"), KIND = key("item_template_kind"),
        BODY = key("item_template_body"), NAME = key("item_template_name");
    private static final Component DIVIDER = line("------------------------", NamedTextColor.DARK_GRAY);
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();
    private ItemPresentation() { }
    private static NamespacedKey key(String name) { return new NamespacedKey("nerdsmp", name); }

    public enum Kind {
        BLOCK("BUILDING BLOCK", "Place to build and shape your surroundings."),
        MATERIAL("CRAFTING MATERIAL", "Used in crafting and workshop recipes."),
        FUEL("FUEL", "Burn in a furnace to smelt and cook."),
        PLANT("PLANTING", "Plant or cultivate this resource."),
        REDSTONE("REDSTONE", "Build circuits and automated contraptions."),
        CONTAINER("STORAGE", "Store or carry your items."),
        DECORATION("DECORATION", "Decorate your home and island."),
        TOOL("TOOL", "Hold to gather or work with matching blocks."),
        WEAPON("WEAPON", "Hold in your main hand to fight."),
        ARMOR("ARMOR", "Equip in its matching armor slot."),
        OFF_HAND("OFF-HAND", "Equip in your off hand for protection."),
        AMMUNITION("AMMUNITION", "Ammunition for bows and crossbows."),
        POTION("POTION", "Use the bottle to apply its effects."),
        POISON("POISON", "Use or coat a weapon as described below."),
        PROVISION("PROVISIONS", "Eat to receive its food benefit."),
        SKILL_LAMP("SKILL LAMP", "Right-click and choose a skill to gain XP."),
        MACHINE("ISLAND MACHINE", "Place on your island to process resources."),
        SPAWNER("MOB SPAWNER", "Place to create a mob spawning point."),
        SUPPLY("EXPEDITION SUPPLY", "Temporary supplies for your expedition."),
        CURRENCY("CURRENCY", "Use to collect its listed reward."),
        BOOK("BOOK", "Read, write or use in enchanting."),
        MUSIC("MUSIC DISC", "Play in a jukebox."),
        TRAVEL("EXPLORATION", "Use for travel and exploration."),
        CONSUMABLE("CONSUMABLE", "Use to activate its effect."),
        UTILITY("UTILITY", "Use for its usual purpose.");
        private final String label, description;
        Kind(String label, String description) { this.label = label; this.description = description; }
        public String label() { return label; }
        public String description() { return description; }
    }

    public static Optional<ItemStack> upgrade(ItemStack item) {
        if (item == null || air(item.getType()) || Bukkit.getServer() == null) return Optional.empty();
        ItemStack base = original(item).clone();
        ItemMeta meta = base.getItemMeta();
        // These item families already have purpose-built templates and authentic render/version stamps.
        var tags = meta.getPersistentDataContainer();
        if (List.of("item_id", "forge_kind", "harvest_dish", "food_template").stream().anyMatch(id -> tags.has(key(id)))) return Optional.empty();
        if (tags.has(VERSION)) return Optional.empty(); // Unknown or edited presentation belongs to its editor.
        Kind kind = classify(base);
        List<Component> body = meta.lore() == null ? List.of() : List.copyOf(meta.lore());
        String encoded = encode(body);
        if (encoded.length() > 32768) return Optional.empty();
        Component ownedName = null;
        if (!meta.hasItemName() && !meta.hasDisplayName()) {
            ownedName = base.effectiveName().color(color(base)).decoration(TextDecoration.ITALIC, false);
            base.setData(DataComponentTypes.ITEM_NAME, ownedName);
        }
        // ItemMeta writes can synthesize unrelated fields (for example empty signed-book content).
        // Apply only the components and PDC entries owned by this presentation layer.
        base.setData(DataComponentTypes.LORE, ItemLore.lore(render(kind, body)));
        String encodedName = ownedName == null ? "" : JSON.serialize(ownedName);
        base.editPersistentDataContainer(data -> {
            data.set(VERSION, PersistentDataType.INTEGER, 1);
            data.set(KIND, PersistentDataType.STRING, kind.name());
            data.set(BODY, PersistentDataType.STRING, encoded);
            data.set(NAME, PersistentDataType.STRING, encodedName);
        });
        return base.isSimilar(item) ? Optional.empty() : Optional.of(base);
    }

    /** Return the original semantic item only while every owned presentation field still matches. */
    public static ItemStack original(ItemStack item) {
        if (item == null || air(item.getType()) || !item.hasItemMeta() || Bukkit.getServer() == null) return item;
        ItemMeta meta = item.getItemMeta(); var tags = meta.getPersistentDataContainer();
        if (!Integer.valueOf(1).equals(tags.get(VERSION, PersistentDataType.INTEGER))) return item;
        String category = tags.get(KIND, PersistentDataType.STRING), body = tags.get(BODY, PersistentDataType.STRING), name = tags.get(NAME, PersistentDataType.STRING);
        if (category == null || body == null || body.length() > 32768 || name == null || name.length() > 8192) return item;
        try {
            Kind kind = Kind.valueOf(category); List<Component> original = decode(body);
            if (!render(kind, original).equals(meta.lore()) || !name.isEmpty() && !JSON.deserialize(name).equals(meta.itemName())) return item;
            ItemStack copy = item.clone();
            if (original.isEmpty()) copy.resetData(DataComponentTypes.LORE);
            else copy.setData(DataComponentTypes.LORE, ItemLore.lore(original));
            if (!name.isEmpty()) copy.resetData(DataComponentTypes.ITEM_NAME);
            copy.editPersistentDataContainer(data -> { data.remove(VERSION); data.remove(KIND); data.remove(BODY); data.remove(NAME); });
            return copy;
        } catch (RuntimeException malformed) { return item; }
    }
    /** Equality for native ingredients/outputs; never ignores player or item-system metadata. */
    public static boolean same(ItemStack first, ItemStack second) {
        return first != null && second != null && original(first).isSimilar(original(second));
    }
    public static boolean covered(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().lore() != null && !item.getItemMeta().lore().isEmpty();
    }
    public static Kind classify(ItemStack item) {
        var tags = item.getItemMeta().getPersistentDataContainer();
        Set<String> keys = new HashSet<>(); tags.getKeys().forEach(key -> { if (key.getNamespace().equals("nerdsmp")) keys.add(key.getKey()); });
        if (keys.contains("expedition_supply")) return Kind.SUPPLY;
        if (keys.contains("island_machine_kind")) return Kind.MACHINE;
        if (keys.stream().anyMatch(key -> key.contains("spawner"))) return Kind.SPAWNER;
        if (keys.contains("skill_lamp")) return Kind.SKILL_LAMP;
        if (keys.contains("poison_vial_type")) return Kind.POISON;
        if (keys.stream().anyMatch(key -> key.contains("purse") || key.contains("coin_pouch"))) return Kind.CURRENCY;
        Material material = item.getType(); String name = material.name();
        if (material == Material.EXPERIENCE_BOTTLE) return Kind.SKILL_LAMP;
        if (material == Material.SPAWNER || name.endsWith("_SPAWN_EGG")) return Kind.SPAWNER;
        if (material == Material.SHIELD) return Kind.OFF_HAND;
        if (EquipmentMaterials.isArmor(material) || name.endsWith("_HORSE_ARMOR") || material == Material.WOLF_ARMOR) return Kind.ARMOR;
        if (EquipmentMaterials.isWeapon(material)) return Kind.WEAPON;
        if (EquipmentMaterials.isTool(material) || Set.of("FLINT_AND_STEEL", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK").contains(name)) return Kind.TOOL;
        if (name.equals("ARROW") || name.endsWith("_ARROW") || material == Material.FIREWORK_ROCKET) return Kind.AMMUNITION;
        if (name.endsWith("POTION")) return Kind.POTION;
        if (material.getDefaultData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD) != null) return Kind.PROVISION;
        if (Set.of("COAL", "CHARCOAL", "COAL_BLOCK", "LAVA_BUCKET", "BLAZE_ROD", "DRIED_KELP_BLOCK").contains(name)) return Kind.FUEL;
        if (name.startsWith("MUSIC_DISC_")) return Kind.MUSIC;
        if (name.contains("BOOK")) return Kind.BOOK;
        if (name.endsWith("_SEEDS") || name.endsWith("_SAPLING") || Set.of("NETHER_WART", "SUGAR_CANE", "BAMBOO", "COCOA_BEANS", "PITCHER_POD").contains(name)) return Kind.PLANT;
        if (org.bukkit.Tag.FLOWERS.isTagged(material) || org.bukkit.Tag.SAPLINGS.isTagged(material)) return Kind.PLANT;
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART") || Set.of("COMPASS", "RECOVERY_COMPASS", "CLOCK", "MAP", "FILLED_MAP", "SPYGLASS", "ENDER_PEARL", "ENDER_EYE").contains(name)) return Kind.TRAVEL;
        if (name.endsWith("SHULKER_BOX") || name.endsWith("BUNDLE") || Set.of("CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL").contains(name)) return Kind.CONTAINER;
        if (name.contains("REDSTONE") || name.endsWith("_BUTTON") || name.endsWith("PRESSURE_PLATE") || Set.of("PISTON", "STICKY_PISTON", "OBSERVER", "COMPARATOR", "REPEATER", "LEVER", "DISPENSER", "DROPPER", "HOPPER", "TARGET", "CRAFTER", "DAYLIGHT_DETECTOR", "TRIPWIRE_HOOK").contains(name)) return Kind.REDSTONE;
        if (name.endsWith("_BANNER") || name.endsWith("_HEAD") || name.endsWith("_SKULL") || name.endsWith("_CANDLE") || name.endsWith("_CARPET") || name.endsWith("_BED") || Set.of("PAINTING", "ITEM_FRAME", "GLOW_ITEM_FRAME", "FLOWER_POT", "ARMOR_STAND").contains(name)) return Kind.DECORATION;
        if (Set.of("MILK_BUCKET", "HONEY_BOTTLE", "OMINOUS_BOTTLE", "FIRE_CHARGE", "TOTEM_OF_UNDYING").contains(name)) return Kind.CONSUMABLE;
        if (name.endsWith("_BUCKET") || Set.of("BUCKET", "LEAD", "NAME_TAG", "SADDLE", "BELL", "GOAT_HORN").contains(name)) return Kind.UTILITY;
        if (material.isBlock()) return Kind.BLOCK;
        return Kind.MATERIAL;
    }
    private static NamedTextColor color(ItemStack item) {
        var rarity = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.RARITY);
        return switch (rarity == null ? "COMMON" : rarity.name()) { case "UNCOMMON" -> NamedTextColor.YELLOW; case "RARE" -> NamedTextColor.AQUA; case "EPIC" -> NamedTextColor.LIGHT_PURPLE; default -> NamedTextColor.WHITE; };
    }
    private static List<Component> render(Kind kind, List<Component> body) {
        List<Component> result = new ArrayList<>(); result.add(DIVIDER); result.add(line(kind.label(), NamedTextColor.GREEN));
        int first = 0, last = body.size();
        while (first < last && spacer(body.get(first))) first++;
        while (last > first && spacer(body.get(last - 1))) last--;
        if (first == last) result.add(line(kind.description(), NamedTextColor.GRAY));
        else body.subList(first, last).forEach(component -> result.add(component.decoration(TextDecoration.ITALIC, false)));
        result.add(DIVIDER); return List.copyOf(result);
    }
    private static boolean spacer(Component line) { return PlainTextComponentSerializer.plainText().serialize(line).matches("[\\s─━_=-]*"); }
    private static Component line(String text, NamedTextColor color) { return Component.text(text, color).decoration(TextDecoration.ITALIC, false); }
    private static String encode(List<Component> body) { JsonArray array = new JsonArray(); body.forEach(line -> array.add(JsonParser.parseString(JSON.serialize(line)))); return array.toString(); }
    private static List<Component> decode(String body) {
        JsonArray array = JsonParser.parseString(body).getAsJsonArray(); if (array.size() > 256) throw new IllegalArgumentException("Too many tooltip lines");
        List<Component> result = new ArrayList<>(); array.forEach(line -> result.add(JSON.deserialize(line.toString()))); return List.copyOf(result);
    }
    private static boolean air(Material material) { return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR; }
}
