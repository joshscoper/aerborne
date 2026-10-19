package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.loot.profile.LootProfileDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;

/** Registry-free authoring controls and exact, non-awarding balance previews. */
public final class LootStudioPolicy {
    public static final int PAGE_SIZE = 28;
    public static final int MAX_EDITORS = 64;
    private LootStudioPolicy() { }

    public static LootProfileDefinition create(String id) {
        return new LootProfileDefinition(identifier(id), Mode.ADDITIVE, List.of());
    }

    public static LootProfileDefinition copy(String id, LootProfileDefinition source) {
        Objects.requireNonNull(source);
        return new LootProfileDefinition(identifier(id), source.mode(), source.entries());
    }

    public static LootProfileDefinition mode(LootProfileDefinition profile, Mode mode) {
        return new LootProfileDefinition(profile.id(), mode, profile.entries());
    }

    public static LootProfileDefinition entry(LootProfileDefinition profile, Entry next, boolean replace) {
        List<Entry> entries = new ArrayList<>(profile.entries());
        int index = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(next.id())) index = i;
        if (replace && index < 0) throw new IllegalArgumentException("That reward no longer exists in this draft.");
        if (!replace && index >= 0) throw new IllegalArgumentException("That reward ID is already used. Choose another ID.");
        if (replace) entries.set(index, next); else entries.add(next);
        return new LootProfileDefinition(profile.id(), profile.mode(), entries);
    }

    public static LootProfileDefinition remove(LootProfileDefinition profile, String id) {
        List<Entry> entries = new ArrayList<>(profile.entries());
        if (!entries.removeIf(entry -> entry.id().equals(id))) throw new IllegalArgumentException("That reward no longer exists in this draft.");
        return new LootProfileDefinition(profile.id(), profile.mode(), entries);
    }

    public static Entry chance(Entry entry, String percent) {
        double value;
        String input = percent.trim();
        if (input.endsWith("%")) input = input.substring(0, input.length() - 1).trim();
        try { value = Double.parseDouble(input); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a chance from 0 to 100, such as 12.5."); }
        if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("Chance must be between 0 and 100 percent.");
        return new Entry(entry.id(), entry.kind(), entry.source(), value / 100, entry.minimum(), entry.maximum(), entry.unidentified(), entry.affixes());
    }

    public static Entry quantity(Entry entry, String input) {
        String[] values = input.trim().split("\\s+");
        if (values.length < 1 || values.length > 2) throw new IllegalArgumentException("Enter one quantity, or a minimum and maximum: 1 4.");
        try {
            int min = Integer.parseInt(values[0]), max = values.length == 1 ? min : Integer.parseInt(values[1]);
            return new Entry(entry.id(), entry.kind(), entry.source(), entry.chance(), min, max, entry.unidentified(), entry.affixes());
        } catch (NumberFormatException error) { throw new IllegalArgumentException("Quantities must be whole numbers."); }
    }

    public static Entry source(Entry entry, String source) {
        String value = source.trim();
        if (entry.kind() == Kind.ITEM) value = value.toLowerCase(Locale.ROOT);
        return new Entry(entry.id(), entry.kind(), value, entry.chance(), entry.minimum(), entry.maximum(), entry.unidentified(), entry.affixes());
    }

    public static Entry unidentified(Entry entry) {
        if (entry.kind() != Kind.ITEM) throw new IllegalArgumentException("Only authored gear may be unidentified.");
        return new Entry(entry.id(), entry.kind(), entry.source(), entry.chance(), entry.minimum(), entry.maximum(), !entry.unidentified(), entry.affixes());
    }

    public static Entry affixes(Entry entry) {
        if (entry.kind() != Kind.ITEM) throw new IllegalArgumentException("Only authored gear may have affixes.");
        return new Entry(entry.id(), entry.kind(), entry.source(), entry.chance(), entry.minimum(), entry.maximum(), entry.unidentified(), (entry.affixes() + 1) % 4);
    }

    public static void validateCatalog(LootProfileDefinition profile, Set<ItemId> known, Set<ItemId> gear) {
        for (Entry entry : profile.entries()) {
            if (entry.kind() != Kind.ITEM) continue;
            ItemId id = new ItemId(entry.source());
            if (!known.contains(id)) throw new IllegalArgumentException("Unknown item ID " + id.value() + ". Publish the item first.");
            if ((entry.unidentified() || entry.affixes() > 0) && !gear.contains(id))
                throw new IllegalArgumentException("Only gear may be unidentified or affixed: " + id.value() + ".");
        }
    }

    public static void expected(LootProfileDefinition baseline, LootProfileDefinition active) {
        if (!Objects.equals(baseline, active)) throw new IllegalStateException("This loot profile changed while you were editing. Your draft is retained; reopen the published profile before saving.");
    }

    public static void expectedBinding(String baseline, String active) {
        if (!Objects.equals(baseline, active)) throw new IllegalStateException("That assignment changed while this confirmation was open. Reopen the assignments and try again.");
    }

    public record Summary(double expectedItemQuantity, double expectedMarks, int maximumItems, int maximumMarks, int rewards) { }

    public static Summary summary(LootProfileDefinition profile) {
        double items = 0, marks = 0;
        int maximumItems = 0, maximumMarks = 0;
        for (Entry entry : profile.entries()) {
            double expected = entry.chance() * ((entry.minimum() + entry.maximum()) / 2D);
            if (entry.kind() == Kind.MARKS) { marks += expected; maximumMarks += entry.maximum(); }
            else { items += expected; maximumItems += entry.maximum(); }
        }
        return new Summary(items, marks, maximumItems, maximumMarks, profile.entries().size());
    }

    public static int page(int requested, int size) { return Math.max(0, Math.min(requested, Math.max(0, (size - 1) / PAGE_SIZE))); }
    public static <T> List<T> entries(List<T> all, int page) {
        int safe = page(page, all.size()), from = safe * PAGE_SIZE;
        return List.copyOf(all.subList(from, Math.min(from + PAGE_SIZE, all.size())));
    }
}
