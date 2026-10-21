package net.nerdsmp.paper.expedition.content;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Box;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Dimension;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Encounter;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.EncounterType;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Loot;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.TierOverride;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Wave;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads one expedition file. Almost everything is optional so a fresh file from
 * {@code /expedition create} loads straight away; {@link ExpeditionValidator} reports what is
 * still missing. Structural mistakes (a region with five numbers, a word where a number belongs)
 * fail here with the path that needs fixing.
 */
public final class ExpeditionLoader {
    private ExpeditionLoader() { }

    public static String templateWorldName(String id) { return "nerdsmp_exp_tpl_" + id; }

    public static ExpeditionDefinition parse(String id, ConfigurationSection yaml) {
        if (!id.matches(ExpeditionDefinition.ID))
            throw new IllegalArgumentException("Expedition ids use 1-32 lowercase letters, digits or underscores: " + id);
        Dimension dimension = Dimension.parse(yaml.getString("dimension", "overworld"));
        ConfigurationSection party = yaml.getConfigurationSection("party");
        Map<String, Room> rooms = new LinkedHashMap<>();
        ConfigurationSection roomSection = yaml.getConfigurationSection("rooms");
        if (yaml.contains("rooms") && roomSection == null) throw new IllegalArgumentException("rooms: must be a section of named rooms.");
        if (roomSection != null) for (String roomId : roomSection.getKeys(false)) {
            ConfigurationSection room = roomSection.getConfigurationSection(roomId);
            if (room == null) throw new IllegalArgumentException("rooms." + roomId + ": must be a section.");
            rooms.put(roomId, room(roomId, room));
        }
        Map<String, TierOverride> tiers = new LinkedHashMap<>();
        ConfigurationSection tierSection = yaml.getConfigurationSection("tiers");
        if (tierSection != null) for (String tierId : tierSection.getKeys(false)) {
            ConfigurationSection tier = tierSection.getConfigurationSection(tierId);
            if (tier == null) throw new IllegalArgumentException("tiers." + tierId + ": must be a section.");
            tiers.put(tierId.toLowerCase(Locale.ROOT), new TierOverride(
                tier.contains("enabled") ? tier.getBoolean("enabled") : null,
                tier.contains("level-bonus") ? integer(tier, "level-bonus") : null,
                tier.contains("lives") ? integer(tier, "lives") : null,
                tier.contains("loot-rolls") ? integer(tier, "loot-rolls") : null,
                tier.contains("modifiers") ? lower(tier.getStringList("modifiers")) : null));
        }
        return new ExpeditionDefinition(id,
            yaml.getString("title", titleCase(id)),
            yaml.getString("description", ""),
            yaml.getString("icon", dimension == Dimension.NETHER ? "netherrack" : dimension == Dimension.END ? "end_stone" : "mossy_cobblestone")
                .toLowerCase(Locale.ROOT),
            dimension,
            lowerOrNull(yaml.getString("biome")),
            yaml.getString("template-world", templateWorldName(id)),
            lowerOrNull(yaml.getString("sample")),
            yaml.getBoolean("published", false),
            yaml.contains("level") ? integer(yaml, "level") : 10,
            party == null ? 1 : party.contains("min") ? integer(party, "min") : 1,
            party == null ? ExpeditionDefinition.MAX_PARTY : party.contains("max") ? integer(party, "max") : ExpeditionDefinition.MAX_PARTY,
            yaml.contains("time-limit-minutes") ? integer(yaml, "time-limit-minutes") : 30,
            yaml.contains("lives") ? integer(yaml, "lives") : null,
            point(yaml, "spawn"),
            point(yaml, "exit"),
            rooms,
            loot(yaml.getConfigurationSection("loot")),
            yaml.contains("campaign-points") ? integer(yaml, "campaign-points") : 0,
            lower(yaml.getStringList("modifiers")),
            tiers);
    }

    private static Room room(String id, ConfigurationSection room) {
        String path = "rooms." + id;
        List<Point> points = new ArrayList<>();
        List<?> raw = room.getList("points", List.of());
        for (int index = 0; index < raw.size(); index++)
            points.add(point(raw.get(index), path + ".points[" + (index + 1) + "]"));
        ConfigurationSection encounter = room.getConfigurationSection("encounter");
        return new Room(id, room.getString("title", titleCase(id)), box(room, "region", path), point(room, "checkpoint"),
            box(room, "gate", path), points, encounter == null ? Encounter.NONE : encounter(encounter, path + ".encounter"));
    }

    private static Encounter encounter(ConfigurationSection section, String path) {
        String rawType = section.getString("type");
        List<Wave> waves = new ArrayList<>();
        List<?> rawWaves = section.getList("waves", List.of());
        for (int index = 0; index < rawWaves.size(); index++)
            waves.add(wave(rawWaves.get(index), path + ".waves[" + (index + 1) + "]"));
        String boss = lowerOrNull(section.getString("boss"));
        EncounterType type;
        if (rawType == null) type = boss != null ? EncounterType.BOSS : section.contains("survive-seconds") ? EncounterType.SURVIVE
            : waves.isEmpty() ? EncounterType.NONE : EncounterType.WAVES;
        else try {
            type = EncounterType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + ".type: use waves, boss or survive, not '" + rawType + "'.");
        }
        List<Integer> thresholds = new ArrayList<>();
        for (Object value : section.getList("adds-at-health", List.of())) {
            if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > 99)
                throw new IllegalArgumentException(path + ".adds-at-health: use health percentages from 1 to 99.");
            thresholds.add(number.intValue());
        }
        thresholds.sort(java.util.Comparator.reverseOrder());
        Map<String, List<ScriptAction>> scripts = new LinkedHashMap<>();
        // "scripts", not "on": YAML 1.1 reads a bare on: key as the boolean true.
        ConfigurationSection on = section.getConfigurationSection("scripts");
        if (on != null) for (String trigger : on.getKeys(false)) {
            List<ScriptAction> actions = new ArrayList<>();
            List<String> lines = on.isList(trigger) ? on.getStringList(trigger) : List.of(on.getString(trigger, ""));
            for (int index = 0; index < lines.size(); index++) {
                try {
                    actions.add(ScriptAction.parse(lines.get(index)));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(path + ".scripts." + trigger + "[" + (index + 1) + "]: " + invalid.getMessage());
                }
            }
            scripts.put(trigger.toLowerCase(Locale.ROOT), actions);
        }
        return new Encounter(type, waves, boss, section.contains("boss-level-bonus") ? integer(section, "boss-level-bonus") : 3,
            section.contains("survive-seconds") ? integer(section, "survive-seconds") : 60, thresholds, scripts);
    }

    @SuppressWarnings("unchecked")
    private static Wave wave(Object raw, String path) {
        Map<String, Object> map;
        if (raw instanceof ConfigurationSection section) map = section.getValues(false);
        else if (raw instanceof Map<?, ?> value) map = (Map<String, Object>) value;
        else throw new IllegalArgumentException(path + ": a wave is a map of mob -> count, such as {custom:veil_stalker: 3}.");
        Object nested = map.get("mobs");
        Map<String, Object> mobs = nested instanceof ConfigurationSection section ? section.getValues(false)
            : nested instanceof Map<?, ?> value ? (Map<String, Object>) value : map;
        int delay = 0;
        Object rawDelay = map.get("delay-seconds");
        if (rawDelay != null) {
            if (!(rawDelay instanceof Number number)) throw new IllegalArgumentException(path + ".delay-seconds must be a number.");
            delay = number.intValue();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var entry : mobs.entrySet()) {
            if (entry.getKey().equals("delay-seconds") || entry.getKey().equals("mobs")) continue;
            if (!(entry.getValue() instanceof Number number))
                throw new IllegalArgumentException(path + ": the count for " + entry.getKey() + " must be a number.");
            counts.put(entry.getKey().toLowerCase(Locale.ROOT), number.intValue());
        }
        return new Wave(counts, delay);
    }

    private static Loot loot(ConfigurationSection section) {
        if (section == null) return Loot.NONE;
        Map<String, Integer> items = new LinkedHashMap<>();
        ConfigurationSection itemSection = section.getConfigurationSection("items");
        if (itemSection != null) for (String item : itemSection.getKeys(false))
            items.put(item.toLowerCase(Locale.ROOT), integer(itemSection, item));
        return new Loot(lowerOrNull(section.getString("profile")), section.getLong("marks", 0), items);
    }

    static Point point(ConfigurationSection section, String key) {
        if (!section.contains(key)) return null;
        return point(section.get(key), section.getCurrentPath().isEmpty() ? key : section.getCurrentPath() + "." + key);
    }

    static Point point(Object raw, String path) {
        if (!(raw instanceof List<?> list) || list.size() < 3 || list.size() > 4 || list.stream().anyMatch(value -> !(value instanceof Number)))
            throw new IllegalArgumentException(path + ": use [x, y, z] or [x, y, z, yaw].");
        return new Point(((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue(), ((Number) list.get(2)).intValue(),
            list.size() == 4 ? ((Number) list.get(3)).floatValue() : 0F);
    }

    private static Box box(ConfigurationSection section, String key, String path) {
        if (!section.contains(key)) return null;
        List<?> list = section.getList(key);
        if (list == null || list.size() != 6 || list.stream().anyMatch(value -> !(value instanceof Number)))
            throw new IllegalArgumentException(path + "." + key + ": use [x1, y1, z1, x2, y2, z2].");
        int[] v = list.stream().mapToInt(value -> ((Number) value).intValue()).toArray();
        return Box.of(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    private static int integer(ConfigurationSection section, String key) {
        if (!section.isInt(key)) {
            String where = section.getCurrentPath() == null || section.getCurrentPath().isEmpty() ? key : section.getCurrentPath() + "." + key;
            throw new IllegalArgumentException(where + ": must be a whole number.");
        }
        return section.getInt(key);
    }

    private static String lowerOrNull(String value) { return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT); }

    private static List<String> lower(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    public static String titleCase(String id) {
        StringBuilder title = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }
}
