package net.nerdsmp.paper.loot.profile;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One authored, bounded loot table; it is rolled once per actual mob death, never per stack size. */
public record LootProfileDefinition(String id, Mode mode, List<Entry> entries) {
    public static final int MAX_PROFILES = 128;
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_ITEM_REWARDS = 256;
    public static final int MAX_MARKS = 10_000;

    public LootProfileDefinition {
        id = identifier(id);
        Objects.requireNonNull(mode, "mode");
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("A loot table may contain at most 32 entries.");
        var ids = new HashSet<String>();
        int maximumItems = 0, maximumMarks = 0;
        for (Entry entry : entries) {
            if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate loot entry " + entry.id() + ".");
            if (entry.kind() == Kind.MARKS) maximumMarks += entry.maximum();
            else maximumItems += entry.maximum();
        }
        if (maximumItems > MAX_ITEM_REWARDS) throw new IllegalArgumentException("A loot table may award at most 256 items per kill.");
        if (maximumMarks > MAX_MARKS) throw new IllegalArgumentException("A loot table may award at most 10,000 Marks per kill.");
    }

    public enum Mode { ADDITIVE, REPLACE }
    public enum Kind { MATERIAL, ITEM, MARKS }

    public record Entry(String id, Kind kind, String source, double chance, int minimum, int maximum,
                        boolean unidentified, int affixes) {
        public Entry {
            id = identifier(id);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(source, "source");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1)
                throw new IllegalArgumentException("Drop chance must be between 0 and 100 percent.");
            int cap = kind == Kind.MARKS ? MAX_MARKS : 64;
            if (minimum < 1 || maximum < minimum || maximum > cap)
                throw new IllegalArgumentException("Drop quantity must be 1.." + cap + ".");
            if (affixes < 0 || affixes > 3) throw new IllegalArgumentException("Loot may have 0..3 affixes.");
            switch (kind) {
                case MATERIAL -> {
                    Material material = Material.matchMaterial(source.toUpperCase(Locale.ROOT));
                    // Registry-free syntax validation; the store checks actual item availability on Paper.
                    if (material == null || material == Material.AIR || material == Material.CAVE_AIR
                        || material == Material.VOID_AIR || material.name().startsWith("LEGACY_"))
                        throw new IllegalArgumentException("Unknown item material " + source + ".");
                    source = material.name();
                    if (unidentified || affixes != 0)
                        throw new IllegalArgumentException("Use an item ID for unidentified or affixed gear.");
                }
                case ITEM -> {
                    source = new ItemId(source).value();
                    if (source.equals("coin_purse"))
                        throw new IllegalArgumentException("Use a Marks entry to create a coin purse.");
                }
                case MARKS -> {
                    if (!source.equals("marks")) throw new IllegalArgumentException("Marks entries use the source 'marks'.");
                    if (unidentified || affixes != 0)
                        throw new IllegalArgumentException("Coin purses cannot be unidentified or affixed.");
                }
            }
        }
    }

    public static String identifier(String value) {
        if (value == null || !value.matches("[a-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("Use an ID of 1..48 lowercase letters, numbers, underscores or hyphens.");
        return value;
    }
}
