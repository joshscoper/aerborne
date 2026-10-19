package net.nerdsmp.paper.forge.arcane;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One account's Arcane knowledge. Knowledge is account-wide; enchantments stay on items.
 *
 * <p>The profile is a value object: every change returns a copy, and the caller publishes it only
 * after it is saved. Ids this build does not know (from a newer build, or a removed definition) are
 * loaded and saved unchanged.
 */
public final class ResearchProfile {
    public static final int SCHEMA = 1;
    static final int HISTORY_LIMIT = 30;
    static final int RECEIPT_LIMIT = 128;

    public enum State {
        UNKNOWN("Unknown"),
        PARTIALLY_DISCOVERED("Partly discovered"),
        DISCOVERED("Discovered"),
        MASTERED("Mastered");

        private final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public boolean known() {
            return this == DISCOVERED || this == MASTERED;
        }
    }

    /** {@code progress} is 0-100; {@code binds} counts times this account bound it at its top level. */
    public record Entry(int progress, boolean discovered, int binds, boolean clue) {
        public static final Entry EMPTY = new Entry(0, false, 0, false);

        public Entry {
            progress = Math.max(0, Math.min(100, progress));
            binds = Math.max(0, binds);
        }
    }

    private final int schema;
    private final boolean seeded;
    private final Map<String, Entry> entries;
    private final Set<String> studied;
    private final Map<String, Integer> attempts;
    private final List<String> history;
    private final LinkedHashSet<String> receipts;

    private ResearchProfile(int schema, boolean seeded, Map<String, Entry> entries, Set<String> studied,
                            Map<String, Integer> attempts, List<String> history, Collection<String> receipts) {
        this.schema = schema;
        this.seeded = seeded;
        this.entries = new TreeMap<>(entries);
        this.studied = new TreeSet<>(studied);
        this.attempts = new TreeMap<>(attempts);
        this.history = new ArrayList<>(history.subList(Math.max(0, history.size() - HISTORY_LIMIT), history.size()));
        List<String> recent = new ArrayList<>(receipts);
        this.receipts = new LinkedHashSet<>(recent.subList(Math.max(0, recent.size() - RECEIPT_LIMIT), recent.size()));
    }

    public static ResearchProfile empty() {
        return new ResearchProfile(SCHEMA, false, Map.of(), Set.of(), Map.of(), List.of(), List.of());
    }

    public int schema() {
        return schema;
    }

    /** Whether the one-time seed from gear the player already owned has run. */
    public boolean seeded() {
        return seeded;
    }

    public Entry entry(String id) {
        return entries.getOrDefault(id, Entry.EMPTY);
    }

    public Map<String, Entry> entries() {
        return java.util.Collections.unmodifiableMap(entries);
    }

    public State state(String id, int masteryBinds) {
        Entry entry = entry(id);
        if (entry.discovered()) return entry.binds() >= masteryBinds ? State.MASTERED : State.DISCOVERED;
        return entry.progress() > 0 || entry.clue() ? State.PARTIALLY_DISCOVERED : State.UNKNOWN;
    }

    public boolean known(String id) {
        return entry(id).discovered();
    }

    public boolean studied(ArcaneAspect aspect) {
        return studied.contains(aspect.id());
    }

    public Set<String> studiedIds() {
        return java.util.Collections.unmodifiableSet(studied);
    }

    public int attempts(String combination) {
        return attempts.getOrDefault(combination, 0);
    }

    public Map<String, Integer> attempts() {
        return java.util.Collections.unmodifiableMap(attempts);
    }

    public List<String> history() {
        return List.copyOf(history);
    }

    public boolean hasReceipt(String receipt) {
        return receipts.contains(receipt);
    }

    // ---- Copy-on-write changes ----

    public ResearchProfile withEntry(String id, Entry entry) {
        Map<String, Entry> next = new TreeMap<>(entries);
        next.put(id, entry);
        return new ResearchProfile(schema, seeded, next, studied, attempts, history, receipts);
    }

    /** Marks a definition known. Knowledge is never removed by any operation. */
    public ResearchProfile discover(String id) {
        Entry current = entry(id);
        if (current.discovered()) return this;
        return withEntry(id, new Entry(100, true, current.binds(), current.clue()));
    }

    /** Records a bind; a bind at the top level counts toward mastery. Binding always teaches it. */
    public ResearchProfile recordBind(String id, int level, int maximumLevel) {
        Entry current = entry(id);
        int binds = level >= maximumLevel && current.binds() < Integer.MAX_VALUE ? current.binds() + 1 : current.binds();
        if (current.discovered() && binds == current.binds()) return this;
        return withEntry(id, new Entry(100, true, binds, current.clue()));
    }

    public ResearchProfile study(Collection<ArcaneAspect> aspects) {
        Set<String> next = new TreeSet<>(studied);
        aspects.forEach(aspect -> next.add(aspect.id()));
        return next.equals(studied) ? this : new ResearchProfile(schema, seeded, entries, next, attempts, history, receipts);
    }

    public ResearchProfile attempt(String combination) {
        Map<String, Integer> next = new TreeMap<>(attempts);
        next.compute(combination, (ignored, count) -> count == null ? 1 : count == Integer.MAX_VALUE ? count : count + 1);
        return new ResearchProfile(schema, seeded, entries, studied, next, history, receipts);
    }

    public ResearchProfile log(String line) {
        List<String> next = new ArrayList<>(history);
        next.add(line);
        return new ResearchProfile(schema, seeded, entries, studied, attempts, next, receipts);
    }

    public ResearchProfile receipt(String receipt) {
        List<String> next = new ArrayList<>(receipts);
        next.remove(receipt);
        next.add(receipt);
        return new ResearchProfile(schema, seeded, entries, studied, attempts, history, next);
    }

    /** The one-time seed from owned gear. Running it again changes nothing. */
    public ResearchProfile seed(Collection<String> knownIds) {
        if (seeded) return this;
        ResearchProfile next = this;
        for (String id : new TreeSet<>(knownIds)) next = next.discover(id);
        return new ResearchProfile(SCHEMA, true, next.entries, next.studied, next.attempts, next.history, next.receipts);
    }

    // ---- Persistence ----

    public static ResearchProfile read(ConfigurationSection section) {
        if (section == null) return empty();
        int schema = integer(section, "schema", SCHEMA, SCHEMA);
        boolean seeded = bool(section, "seeded");
        Map<String, Entry> entries = new TreeMap<>();
        ConfigurationSection rawEntries = child(section, "entries");
        if (rawEntries != null) {
            for (String id : rawEntries.getKeys(false)) {
                ConfigurationSection entry = rawEntries.getConfigurationSection(id);
                if (entry == null) throw malformed("entries." + id);
                entries.put(id, new Entry(integer(entry, "progress", 0, 100), bool(entry, "discovered"),
                    integer(entry, "binds", 0, Integer.MAX_VALUE), bool(entry, "clue")));
            }
        }
        Map<String, Integer> attempts = new TreeMap<>();
        ConfigurationSection rawAttempts = child(section, "attempts");
        if (rawAttempts != null) {
            for (String key : rawAttempts.getKeys(false)) attempts.put(key, integer(rawAttempts, key, 0, Integer.MAX_VALUE));
        }
        return new ResearchProfile(
            schema,
            seeded,
            entries,
            new TreeSet<>(strings(section, "studied-aspects")),
            attempts,
            strings(section, "history"),
            strings(section, "receipts")
        );
    }

    private static ConfigurationSection child(ConfigurationSection section, String path) {
        if (!section.isSet(path)) return null; // Empty maps are omitted by the YAML writer.
        ConfigurationSection child = section.getConfigurationSection(path);
        if (child == null) throw malformed(path);
        return child;
    }

    private static int integer(ConfigurationSection section, String path, int minimum, int maximum) {
        Object raw = section.get(path);
        if (!(raw instanceof Integer value) || value < minimum || value > maximum) throw malformed(path);
        return value;
    }

    private static boolean bool(ConfigurationSection section, String path) {
        if (!(section.get(path) instanceof Boolean value)) throw malformed(path);
        return value;
    }

    private static List<String> strings(ConfigurationSection section, String path) {
        List<?> values = section.getList(path);
        if (values == null || values.stream().anyMatch(value -> !(value instanceof String))) throw malformed(path);
        return values.stream().map(String.class::cast).toList();
    }

    private static IllegalStateException malformed(String path) {
        return new IllegalStateException("Unsupported or malformed Arcane knowledge at " + path + "; refusing to replace it");
    }

    /** Writes known fields, preserving extension fields and unknown entry ids in a supported schema. */
    public void write(ConfigurationSection section) {
        section.set("schema", schema);
        section.set("seeded", seeded);
        entries.forEach((id, entry) -> {
            String base = "entries." + id + ".";
            section.set(base + "progress", entry.progress());
            section.set(base + "discovered", entry.discovered());
            section.set(base + "binds", entry.binds());
            section.set(base + "clue", entry.clue());
        });
        section.set("studied-aspects", new ArrayList<>(studied));
        attempts.forEach((key, count) -> section.set("attempts." + key, count));
        section.set("history", new ArrayList<>(history));
        section.set("receipts", new ArrayList<>(receipts));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ResearchProfile that && schema == that.schema && seeded == that.seeded
            && entries.equals(that.entries) && studied.equals(that.studied) && attempts.equals(that.attempts)
            && history.equals(that.history) && new ArrayList<>(receipts).equals(new ArrayList<>(that.receipts));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(schema, seeded, entries, studied, attempts, history);
    }
}
