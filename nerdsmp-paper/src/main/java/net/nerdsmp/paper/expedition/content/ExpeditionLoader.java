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
                tier.contains("modifiers") ? lower(tier.getStringList("modifiers")) : null,
                tier.contains("health-multiplier") ? tier.getDouble("health-multiplier") : null,
                tier.contains("damage-multiplier") ? tier.getDouble("damage-multiplier") : null));
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
            party != null && party.contains("max") ? integer(party, "max")
                : yaml.getBoolean("raid", false) ? ExpeditionDefinition.MAX_RAID : ExpeditionDefinition.MAX_PARTY,
            yaml.contains("time-limit-minutes") ? integer(yaml, "time-limit-minutes") : 30,
            yaml.contains("lives") ? integer(yaml, "lives") : null,
            point(yaml, "spawn"),
            point(yaml, "exit"),
            rooms,
            loot(yaml.getConfigurationSection("loot")),
            yaml.contains("campaign-points") ? integer(yaml, "campaign-points") : 0,
            lower(yaml.getStringList("modifiers")),
            tiers,
            yaml.contains("difficulty") ? integer(yaml, "difficulty") : null,
            guide(yaml.getConfigurationSection("guide")),
            story(yaml));
    }

    private static ExpeditionDefinition.Story story(ConfigurationSection yaml) {
        ExpeditionDefinition.Goal goal = enumValue(ExpeditionDefinition.Goal.class, yaml.getString("goal", "rooms"), "goal",
            "rooms, bosses, forces or timed");
        Map<String, ExpeditionDefinition.Campfire> campfires = new LinkedHashMap<>();
        for (var entry : sections(yaml, "campfires").entrySet()) {
            ConfigurationSection fire = entry.getValue();
            String path = "campfires." + entry.getKey();
            Point at = point(fire, "at");
            if (at == null) throw new IllegalArgumentException(path + ".at: where the campfire stands, [x, y, z].");
            campfires.put(entry.getKey(), new ExpeditionDefinition.Campfire(entry.getKey(), at, fire.getBoolean("lit", false),
                fire.getString("title", titleCase(entry.getKey()))));
        }
        Map<String, ExpeditionDefinition.SetPiece> setpieces = new LinkedHashMap<>();
        for (var entry : sections(yaml, "setpieces").entrySet()) {
            ConfigurationSection piece = entry.getValue();
            String path = "setpieces." + entry.getKey();
            Box box = box(piece, "box", path);
            if (box == null) throw new IllegalArgumentException(path + ".box: the region it fills, [x1, y1, z1, x2, y2, z2].");
            setpieces.put(entry.getKey(), new ExpeditionDefinition.SetPiece(entry.getKey(), box,
                piece.getString("block", "stone").toLowerCase(Locale.ROOT),
                enumValue(ExpeditionDefinition.PieceStyle.class, piece.getString("style", "instant"), path + ".style", "instant, rise or crumble")));
        }
        Map<String, ExpeditionDefinition.Hazard> hazards = new LinkedHashMap<>();
        for (var entry : sections(yaml, "hazards").entrySet()) {
            ConfigurationSection hazard = entry.getValue();
            String path = "hazards." + entry.getKey();
            Box area = box(hazard, "area", path);
            if (area == null) throw new IllegalArgumentException(path + ".area: the floor it strikes, [x1, y1, z1, x2, y2, z2].");
            hazards.put(entry.getKey(), new ExpeditionDefinition.Hazard(entry.getKey(),
                enumValue(ExpeditionDefinition.HazardType.class, hazard.getString("type", "rocks"), path + ".type", "rocks, fire, eruption or gas"),
                area, hazard.contains("every") ? integer(hazard, "every") : 8, hazard.contains("count") ? integer(hazard, "count") : 3,
                hazard.contains("damage") ? hazard.getDouble("damage") : 6));
        }
        Map<String, ExpeditionDefinition.Cutscene> cutscenes = new LinkedHashMap<>();
        ConfigurationSection cutSection = yaml.getConfigurationSection("cutscenes");
        if (cutSection != null) for (String id : cutSection.getKeys(false)) {
            List<ExpeditionDefinition.Shot> shots = new ArrayList<>();
            List<?> raw = cutSection.getList(id, List.of());
            for (int index = 0; index < raw.size(); index++) shots.add(shot(raw.get(index), "cutscenes." + id + "[" + (index + 1) + "]"));
            cutscenes.put(id, new ExpeditionDefinition.Cutscene(id, shots));
        }
        Map<String, ExpeditionDefinition.Npc> npcs = new LinkedHashMap<>();
        for (var entry : sections(yaml, "npcs").entrySet()) {
            ConfigurationSection npc = entry.getValue();
            Point at = point(npc, "at");
            if (at == null) throw new IllegalArgumentException("npcs." + entry.getKey() + ".at: where they stand, [x, y, z, yaw].");
            npcs.put(entry.getKey(), new ExpeditionDefinition.Npc(entry.getKey(), npc.getString("name", titleCase(entry.getKey())),
                npc.getString("entity", "villager").toLowerCase(Locale.ROOT), at, npc.getStringList("lines")));
        }
        Map<String, ExpeditionDefinition.MobProfile> mobs = new LinkedHashMap<>();
        ConfigurationSection mobSection = yaml.getConfigurationSection("mobs");
        if (mobSection != null) for (String mob : mobSection.getKeys(false)) {
            ConfigurationSection profile = mobSection.getConfigurationSection(mob);
            if (profile == null) throw new IllegalArgumentException("mobs." + mob + ": must be a section with a role and abilities.");
            ExpeditionDefinition.Role role = enumValue(ExpeditionDefinition.Role.class, profile.getString("role", "normal"),
                "mobs." + mob + ".role", "normal, elite, miniboss or boss");
            mobs.put(mob.toLowerCase(Locale.ROOT), new ExpeditionDefinition.MobProfile(mob.toLowerCase(Locale.ROOT), role,
                lower(profile.getStringList("abilities")),
                profile.contains("forces") ? integer(profile, "forces") : ExpeditionDefinition.MobProfile.defaultForces(role),
                profile.getString("title")));
        }
        return new ExpeditionDefinition.Story(goal, yaml.contains("forces") ? integer(yaml, "forces") : 0,
            yaml.contains("time-bonus-seconds") ? integer(yaml, "time-bonus-seconds") : 0, yaml.getBoolean("raid", false),
            campfires, setpieces, hazards, cutscenes, npcs, mobs, yaml.contains("time") ? integer(yaml, "time") : null,
            lower(yaml.getStringList("requires")));
    }

    private static Map<String, ConfigurationSection> sections(ConfigurationSection yaml, String key) {
        Map<String, ConfigurationSection> found = new LinkedHashMap<>();
        if (!yaml.contains(key)) return found;
        ConfigurationSection section = yaml.getConfigurationSection(key);
        if (section == null) throw new IllegalArgumentException(key + ": must be a section of named entries.");
        for (String id : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(id);
            if (child == null) throw new IllegalArgumentException(key + "." + id + ": must be a section.");
            if (!id.matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException(key + "." + id + ": ids use lowercase letters, digits or underscores.");
            found.put(id, child);
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static ExpeditionDefinition.Shot shot(Object raw, String path) {
        Map<String, Object> map = raw instanceof ConfigurationSection section ? section.getValues(false)
            : raw instanceof Map<?, ?> value ? (Map<String, Object>) value : null;
        if (map == null || !(map.get("at") instanceof List<?> at) || at.size() != 5 || at.stream().anyMatch(value -> !(value instanceof Number)))
            throw new IllegalArgumentException(path + ": a shot is {at: [x, y, z, yaw, pitch], seconds: 3, text: ...}.");
        Object seconds = map.getOrDefault("seconds", 3);
        if (!(seconds instanceof Number number) || number.intValue() < 1 || number.intValue() > 20)
            throw new IllegalArgumentException(path + ".seconds: 1-20.");
        return new ExpeditionDefinition.Shot(((Number) at.get(0)).doubleValue(), ((Number) at.get(1)).doubleValue(), ((Number) at.get(2)).doubleValue(),
            ((Number) at.get(3)).floatValue(), ((Number) at.get(4)).floatValue(), number.intValue(),
            map.get("text") == null ? null : String.valueOf(map.get("text")));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String path, String choices) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + ": use " + choices + ", not '" + raw + "'.");
        }
    }

    private static Map<String, ExpeditionDefinition.Puzzle> puzzles(ConfigurationSection room, String path) {
        Map<String, ExpeditionDefinition.Puzzle> puzzles = new LinkedHashMap<>();
        for (var entry : sections(room, "puzzles").entrySet()) {
            ConfigurationSection puzzle = entry.getValue();
            String where = path + ".puzzles." + entry.getKey();
            List<Point> blocks = new ArrayList<>();
            List<?> raw = puzzle.getList("blocks", List.of());
            for (int index = 0; index < raw.size(); index++) blocks.add(point(raw.get(index), where + ".blocks[" + (index + 1) + "]"));
            List<Integer> order = new ArrayList<>();
            for (Object value : puzzle.getList("order", List.of())) {
                if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > blocks.size())
                    throw new IllegalArgumentException(where + ".order: list block numbers from 1 to " + blocks.size() + ".");
                order.add(number.intValue() - 1);
            }
            ExpeditionDefinition.PuzzleType type = enumValue(ExpeditionDefinition.PuzzleType.class, puzzle.getString("type", "levers"),
                where + ".type", "levers, sequence, plates, reach or targets");
            puzzles.put(entry.getKey(), new ExpeditionDefinition.Puzzle(entry.getKey(), type, puzzle.getString("title", defaultPuzzleTitle(type, blocks.size())),
                blocks, order, puzzle.getBoolean("show", false), box(puzzle, "area", where), point(puzzle, "start"),
                puzzle.contains("fail-below") ? integer(puzzle, "fail-below") : null,
                puzzle.contains("seconds") ? integer(puzzle, "seconds") : 0, point(puzzle, "supply")));
        }
        return puzzles;
    }

    private static String defaultPuzzleTitle(ExpeditionDefinition.PuzzleType type, int blocks) {
        return switch (type) {
            case LEVERS -> "Pull the " + blocks + " levers";
            case SEQUENCE -> "Press them in the right order";
            case PLATES -> "Hold down every plate at once";
            case REACH -> "Reach the far side";
            case TARGETS -> "Hit every target";
        };
    }

    private static List<ExpeditionDefinition.Phase> phases(ConfigurationSection section, String path) {
        List<ExpeditionDefinition.Phase> phases = new ArrayList<>();
        List<?> raw = section.getList("phases", List.of());
        for (int index = 0; index < raw.size(); index++) {
            Object value = raw.get(index);
            Map<?, ?> map = value instanceof ConfigurationSection child ? child.getValues(false) : value instanceof Map<?, ?> m ? m : null;
            String where = path + ".phases[" + (index + 1) + "]";
            if (map == null || !(map.get("at") instanceof Number at) || at.intValue() < 1 || at.intValue() > 99)
                throw new IllegalArgumentException(where + ": a phase is {at: <health %>, abilities: [...], title: ...}.");
            List<String> abilities = map.get("abilities") instanceof List<?> list ? list.stream().map(String::valueOf).map(text -> text.toLowerCase(Locale.ROOT)).toList() : List.of();
            int submerge = map.get("submerge-seconds") instanceof Number seconds ? seconds.intValue() : 0;
            phases.add(new ExpeditionDefinition.Phase(at.intValue(), abilities, map.get("title") == null ? null : String.valueOf(map.get("title")),
                submerge));
        }
        phases.sort(java.util.Comparator.comparingInt(ExpeditionDefinition.Phase::atHealth).reversed());
        return phases;
    }

    private static Room room(String id, ConfigurationSection room) {
        String path = "rooms." + id;
        List<Point> points = new ArrayList<>();
        List<?> raw = room.getList("points", List.of());
        for (int index = 0; index < raw.size(); index++)
            points.add(point(raw.get(index), path + ".points[" + (index + 1) + "]"));
        ConfigurationSection encounter = room.getConfigurationSection("encounter");
        List<String> requires = room.contains("requires") ? lower(room.getStringList("requires")) : null;
        return new Room(id, room.getString("title", titleCase(id)), box(room, "region", path), point(room, "checkpoint"),
            box(room, "gate", path), points, encounter == null ? Encounter.NONE : encounter(encounter, path + ".encounter"),
            requires, room.getBoolean("optional", false), puzzles(room, path), loot(room.getConfigurationSection("loot")),
            lower(room.getStringList("unlocks")));
    }

    private static Encounter encounter(ConfigurationSection section, String path) {
        String rawType = section.getString("type");
        List<Wave> waves = new ArrayList<>();
        List<?> rawWaves = section.getList("waves", List.of());
        for (int index = 0; index < rawWaves.size(); index++)
            waves.add(wave(rawWaves.get(index), path + ".waves[" + (index + 1) + "]"));
        String boss = lowerOrNull(section.getString("boss"));
        List<String> bosses = lower(section.getStringList("bosses"));
        EncounterType type;
        if (rawType == null) type = !bosses.isEmpty() ? EncounterType.COUNCIL : boss != null ? EncounterType.BOSS
            : section.contains("survive-seconds") ? EncounterType.SURVIVE : waves.isEmpty() ? EncounterType.NONE : EncounterType.WAVES;
        else try {
            type = EncounterType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(path + ".type: use waves, clear, survive, boss, council or puzzle, not '" + rawType + "'.");
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
            section.contains("survive-seconds") ? integer(section, "survive-seconds") : 60, thresholds, scripts,
            multiplier(section, "boss-damage", path), multiplier(section, "boss-health", path), bosses,
            lower(section.getStringList("abilities")), phases(section, path),
            section.contains("enrage-seconds") ? integer(section, "enrage-seconds") : 0,
            section.contains("link-seconds") ? integer(section, "link-seconds") : 0);
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

    private static ExpeditionDefinition.Guide guide(ConfigurationSection section) {
        if (section == null) return ExpeditionDefinition.Guide.EMPTY;
        Map<String, String> rooms = new LinkedHashMap<>();
        ConfigurationSection roomSection = section.getConfigurationSection("rooms");
        if (roomSection != null) for (String room : roomSection.getKeys(false)) rooms.put(room, roomSection.getString(room, ""));
        List<String> tips = section.isList("tips") ? section.getStringList("tips")
            : section.contains("tips") ? List.of(section.getString("tips", "")) : List.of();
        return new ExpeditionDefinition.Guide(section.getString("overview", "").strip(), tips, rooms, section.getString("boss", "").strip());
    }

    private static double multiplier(ConfigurationSection section, String key, String path) {
        if (!section.contains(key)) return 1;
        if (!section.isDouble(key) && !section.isInt(key)) throw new IllegalArgumentException(path + "." + key + ": must be a number such as 0.75.");
        return section.getDouble(key);
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
