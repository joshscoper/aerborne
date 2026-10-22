package net.nerdsmp.paper.quest;

import net.nerdsmp.api.model.Skill;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict, Bukkit-free reader for {@code content/world-quests.yml}. */
public record QuestCatalog(int schemaVersion, Map<String, QuestDefinition> quests) {
    public static final int SCHEMA_VERSION = 1;

    public QuestCatalog {
        quests = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(quests));
    }

    public static QuestCatalog empty() { return new QuestCatalog(SCHEMA_VERSION, Map.of()); }

    public List<QuestDefinition> offeredBy(String npc) {
        return quests.values().stream().filter(quest -> quest.giver().equals(npc) || quest.turnIn().equals(npc)).toList();
    }

    public static QuestCatalog parse(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(16);
        Object root = new Yaml(new SafeConstructor(options)).load(text);
        Map<?, ?> document = map(root, "quest file");
        int schema = integer(document.get("schema-version"), "schema-version");
        if (schema < 1 || schema > SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported quest schema " + schema);
        Map<?, ?> section = map(document.get("quests"), "quests");
        Map<String, QuestDefinition> quests = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String id = String.valueOf(entry.getKey());
            try {
                quests.put(id, quest(id, map(entry.getValue(), id)));
            } catch (IllegalArgumentException | ClassCastException failure) {
                throw new IllegalArgumentException("Quest " + id + ": " + failure.getMessage(), failure);
            }
        }
        for (QuestDefinition quest : quests.values()) for (String required : quest.requires())
            if (!quests.containsKey(required)) throw new IllegalArgumentException("Quest " + quest.id() + " requires unknown quest " + required);
        for (QuestDefinition quest : quests.values()) cycle(quest.id(), quests, new HashSet<>(), new HashSet<>());
        return new QuestCatalog(schema, quests);
    }

    private static void cycle(String id, Map<String, QuestDefinition> quests, Set<String> visiting, Set<String> done) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Quest prerequisites form a loop at " + id);
        for (String required : quests.get(id).requires()) cycle(required, quests, visiting, done);
        visiting.remove(id);
        done.add(id);
    }

    private static final Set<String> QUEST_KEYS = Set.of("chain", "title", "giver", "turn-in", "level", "requires", "offer", "complete", "objectives", "rewards");

    private static QuestDefinition quest(String id, Map<?, ?> node) {
        keys(node, QUEST_KEYS, Set.of("chain", "title", "giver", "offer", "complete", "objectives"), id);
        String giver = string(node.get("giver"), "giver");
        List<QuestDefinition.Objective> objectives = new ArrayList<>();
        for (Object raw : list(node.get("objectives"), "objectives")) objectives.add(objective(map(raw, "objective")));
        return new QuestDefinition(id, string(node.get("chain"), "chain"), string(node.get("title"), "title"), giver,
            node.containsKey("turn-in") ? string(node.get("turn-in"), "turn-in") : giver,
            node.containsKey("level") ? integer(node.get("level"), "level") : 1,
            node.containsKey("requires") ? strings(node.get("requires"), "requires") : List.of(),
            lines(node.get("offer"), "offer"), lines(node.get("complete"), "complete"),
            objectives, node.containsKey("rewards") ? rewards(map(node.get("rewards"), "rewards")) : new QuestDefinition.Rewards(0, Map.of(), Map.of(), List.of()));
    }

    private static QuestDefinition.Objective objective(Map<?, ?> node) {
        keys(node, Set.of("type", "targets", "zone", "count", "description"), Set.of("type", "description"), "objective");
        QuestDefinition.Type type;
        try { type = QuestDefinition.Type.valueOf(string(node.get("type"), "type").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown objective type " + node.get("type")); }
        Set<String> targets = new LinkedHashSet<>();
        if (node.containsKey("targets")) for (String target : strings(node.get("targets"), "targets"))
            targets.add(type == QuestDefinition.Type.KILL || type == QuestDefinition.Type.TALK ? target : target.toUpperCase(Locale.ROOT));
        return new QuestDefinition.Objective(type, targets, node.containsKey("zone") ? string(node.get("zone"), "zone") : null,
            node.containsKey("count") ? integer(node.get("count"), "count") : 1, string(node.get("description"), "description"));
    }

    private static QuestDefinition.Rewards rewards(Map<?, ?> node) {
        keys(node, Set.of("marks", "xp", "reputation", "items"), Set.of(), "rewards");
        Map<String, Long> experience = new LinkedHashMap<>();
        if (node.containsKey("xp")) for (Map.Entry<?, ?> entry : map(node.get("xp"), "xp").entrySet()) {
            String skill = String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT);
            try { Skill.valueOf(skill); } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown skill " + entry.getKey()); }
            experience.put(skill, (long) integer(entry.getValue(), "xp"));
        }
        Map<String, Integer> reputation = new LinkedHashMap<>();
        if (node.containsKey("reputation")) for (Map.Entry<?, ?> entry : map(node.get("reputation"), "reputation").entrySet())
            reputation.put(String.valueOf(entry.getKey()), integer(entry.getValue(), "reputation"));
        List<QuestDefinition.ItemReward> items = new ArrayList<>();
        if (node.containsKey("items")) for (String raw : strings(node.get("items"), "items")) {
            // "material:bread 4" or "item:skill_lamp"
            String[] parts = raw.trim().split("\\s+");
            if (parts.length > 2 || !parts[0].contains(":")) throw new IllegalArgumentException("Reward items look like material:bread 4");
            String[] kind = parts[0].split(":", 2);
            items.add(new QuestDefinition.ItemReward(kind[0], kind[1], parts.length == 2 ? Integer.parseInt(parts[1]) : 1));
        }
        return new QuestDefinition.Rewards(node.containsKey("marks") ? integer(node.get("marks"), "marks") : 0, experience, reputation, items);
    }

    private static void keys(Map<?, ?> node, Set<String> allowed, Set<String> required, String label) {
        for (Object key : node.keySet()) if (!allowed.contains(String.valueOf(key))) throw new IllegalArgumentException(label + " has unknown field " + key);
        for (String key : required) if (!node.containsKey(key)) throw new IllegalArgumentException(label + " is missing " + key);
    }

    private static Map<?, ?> map(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be a mapping");
        return map;
    }

    private static List<?> list(Object value, String label) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(label + " must be a list");
        return list;
    }

    private static List<String> strings(Object value, String label) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value, label)) result.add(string(item, label));
        return result;
    }

    private static List<String> lines(Object value, String label) {
        return value instanceof String text ? List.of(text) : strings(value, label);
    }

    private static String string(Object value, String label) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(label + " must be text");
        return text.strip();
    }

    private static int integer(Object value, String label) {
        if (!(value instanceof Integer number)) throw new IllegalArgumentException(label + " must be a whole number");
        return number;
    }
}
