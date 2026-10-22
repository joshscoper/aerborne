package net.nerdsmp.paper.expedition.content;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.EncounterType;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Wave;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Staff-facing checklist. Errors block publishing and running; warnings are advice. Every issue
 * carries the command (or file key) that fixes it, so {@code /expedition check} reads as a to-do list.
 */
public final class ExpeditionValidator {
    public static final long MAX_ROOM_VOLUME = 400_000;
    public static final long MAX_GATE_VOLUME = 2_048;

    /** What the server knows about, looked up live in game and stubbed in tests. */
    public interface Catalog {
        boolean mob(String id);
        boolean boss(String id);
        boolean lootProfile(String id);
        boolean item(String id);
        boolean effect(String key);
        boolean sound(String key);
        boolean material(String key);
        boolean biome(String key);

        /** Ability ids come from the built-in kit unless a server adds more. */
        default boolean ability(String id) { return Abilities.known(id); }

        /** Whether an expedition id exists, for {@code requires:}. */
        default boolean expedition(String id) { return true; }

        /** Entity types an expedition NPC may use. */
        default boolean npcEntity(String key) {
            return java.util.Set.of("villager", "wandering_trader", "piglin", "allay", "vindicator", "pillager", "witch", "skeleton",
                "zombie", "iron_golem", "illusioner", "evoker", "wither_skeleton", "zombie_villager").contains(key);
        }
    }

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String message, String fix) {
        public boolean error() { return severity == Severity.ERROR; }
    }

    public static List<Issue> validate(ExpeditionDefinition expedition, Map<String, Tier> tiers, Catalog catalog) {
        List<Issue> issues = new ArrayList<>();
        if (expedition.spawn() == null) error(issues, "No party spawn point.", "/expedition set spawn");
        if (expedition.exit() == null) error(issues, "No exit point for the finish.", "/expedition set exit");
        if (expedition.rooms().isEmpty()) error(issues, "No rooms yet. Select a room with the wand.", "/expedition room add <name>");
        int cap = expedition.partyCap();
        if (expedition.minParty() < 1 || expedition.maxParty() > cap || expedition.minParty() > expedition.maxParty())
            error(issues, "Party size must be between 1 and " + cap + (expedition.story().raid() ? " for a raid" : "")
                + " with min <= max (now " + expedition.minParty() + "-" + expedition.maxParty() + ").", "/expedition set party <min> <max>");
        story(issues, expedition, catalog);
        if (expedition.level() < 1 || expedition.level() > 100) error(issues, "Level must be 1-100.", "/expedition set level <1-100>");
        if (expedition.timeLimitMinutes() < 5 || expedition.timeLimitMinutes() > 180)
            error(issues, "Time limit must be 5-180 minutes.", "/expedition set time <minutes>");
        if (expedition.lives() != null && (expedition.lives() < 1 || expedition.lives() > 50))
            error(issues, "Lives must be 1-50.", "lives: in the expedition file");
        if (expedition.difficulty() != null && (expedition.difficulty() < 1 || expedition.difficulty() > 5))
            error(issues, "Difficulty must be 1-5 (now " + expedition.difficulty() + ").", "difficulty: in the expedition file");
        if (expedition.difficulty() == null) warn(issues, "No difficulty rating; the menu estimates " + Difficulty.label(Difficulty.rating(expedition)) + " from the level.", "difficulty: 1-5 in the expedition file");
        if (expedition.guide().isEmpty()) warn(issues, "No player guide yet. A short overview and a tip or two help players prepare.", "guide: in the expedition file");
        for (String room : expedition.guide().rooms().keySet())
            if (!expedition.rooms().containsKey(room)) warn(issues, "The guide has a tip for unknown room '" + room + "'.", "guide.rooms in the expedition file");
        if (!catalog.material(expedition.icon())) warn(issues, "Unknown menu icon '" + expedition.icon() + "'.", "/expedition set icon <material>");
        if (expedition.biome() != null && !catalog.biome(expedition.biome()))
            error(issues, "Unknown biome '" + expedition.biome() + "'.", "biome: in the expedition file");
        if (expedition.title().isBlank()) warn(issues, "The expedition has no title.", "/expedition set title <text>");
        if (expedition.description().isBlank()) warn(issues, "Add a one-line description for the menu.", "/expedition set description <text>");
        for (String modifier : expedition.modifiers())
            if (Modifier.parse(modifier).isEmpty()) error(issues, "Unknown modifier '" + modifier + "'.", modifierFix());
        expedition.tiers().forEach((id, override) -> {
            if (!tiers.containsKey(id)) error(issues, "Tier override for unknown tier '" + id + "'.", "tiers: in the expedition file");
            if (override.modifiers() != null) for (String modifier : override.modifiers())
                if (Modifier.parse(modifier).isEmpty()) error(issues, "Tier " + id + " lists unknown modifier '" + modifier + "'.", modifierFix());
        });
        if (tiers.keySet().stream().noneMatch(id -> tiers.get(id).apply(expedition.tiers().get(id), expedition.lives()) != null))
            error(issues, "Every difficulty tier is disabled.", "tiers: in the expedition file");

        var loot = expedition.loot();
        if (loot.profile() != null && !catalog.lootProfile(loot.profile()))
            error(issues, "Loot profile '" + loot.profile() + "' does not exist.", "/mobloot create " + loot.profile());
        loot.items().forEach((item, amount) -> {
            if (!catalog.item(item) && !catalog.material(item)) error(issues, "Unknown loot item '" + item + "'.", "loot.items in the expedition file");
            if (amount < 1 || amount > 64) error(issues, "Loot item '" + item + "' amount must be 1-64.", "loot.items in the expedition file");
        });
        if (loot.marks() < 0 || loot.marks() > 10_000) error(issues, "Loot Marks must be 0-10000.", "loot.marks in the expedition file");
        if (loot.profile() == null && loot.items().isEmpty() && loot.marks() == 0)
            warn(issues, "The finish gives no loot.", "/expedition loot <profile> or loot.marks");

        boolean boss = false;
        List<Room> rooms = expedition.roomList();
        for (int index = 0; index < rooms.size(); index++) {
            Room room = rooms.get(index);
            String name = "Room '" + room.id() + "'";
            if (!room.id().matches("[a-z0-9_]{1,32}")) error(issues, name + " has an invalid id.", "rooms: in the expedition file");
            if (room.region() == null) {
                error(issues, name + " has no region.", "Select it with the wand, then /expedition room add " + room.id());
                continue;
            }
            if (room.region().volume() > MAX_ROOM_VOLUME)
                error(issues, name + " is too large (" + room.region().volume() + " blocks; max " + MAX_ROOM_VOLUME + ").", "/expedition room add " + room.id());
            for (int other = 0; other < index; other++) {
                Room earlier = rooms.get(other);
                if (earlier.region() != null && earlier.region().intersects(room.region()))
                    error(issues, name + " overlaps room '" + earlier.id() + "'.", "Reselect one of them with the wand");
            }
            if (room.checkpoint() != null && !room.region().contains(room.checkpoint()))
                warn(issues, name + "'s checkpoint is outside the room.", "/expedition checkpoint " + room.id());
            if (room.gate() != null) {
                if (room.gate().volume() > MAX_GATE_VOLUME)
                    error(issues, name + "'s gate is too large (max " + MAX_GATE_VOLUME + " blocks).", "/expedition gate " + room.id());
                if (expedition.spawn() != null && room.gate().contains(expedition.spawn()))
                    error(issues, name + "'s gate covers the party spawn.", "/expedition gate " + room.id());
                if (room.checkpoint() != null && room.gate().contains(room.checkpoint()))
                    error(issues, name + "'s gate covers its checkpoint.", "/expedition checkpoint " + room.id());
            }
            for (int point = 0; point < room.points().size(); point++)
                if (!room.region().contains(room.points().get(point)))
                    error(issues, name + " spawn point #" + (point + 1) + " is outside the room.", "/expedition point " + room.id() + " remove " + (point + 1));
            var encounter = room.encounter();
            if (encounter.type() != EncounterType.NONE && encounter.type() != EncounterType.PUZZLE && room.points().isEmpty())
                error(issues, name + " has an encounter but no mob spawn points.", "Shift-right-click with the wand inside the room");
            puzzles(issues, name, room);
            if (room.requires() != null) for (String required : room.requires()) {
                if (required.equals(room.id())) error(issues, name + " requires itself.", "rooms." + room.id() + ".requires");
                else if (!expedition.rooms().containsKey(required))
                    error(issues, name + " requires unknown room '" + required + "'.", "rooms." + room.id() + ".requires");
            }
            if (!encounter.abilities().isEmpty() || !encounter.phases().isEmpty()) {
                for (String ability : encounter.abilities())
                    if (!catalog.ability(ability)) error(issues, name + " uses unknown ability '" + ability + "'.", abilityFix());
                for (var phase : encounter.phases()) for (String ability : phase.abilities())
                    if (!catalog.ability(ability)) error(issues, name + " phase at " + phase.atHealth() + "% uses unknown ability '" + ability + "'.", abilityFix());
                if (!room.bossRoom()) warn(issues, name + " lists boss abilities or phases but has no boss.", "Use them in a boss or council room");
            }
            if (encounter.enrageSeconds() != 0 && (encounter.enrageSeconds() < 30 || encounter.enrageSeconds() > 1800))
                error(issues, name + " enrage-seconds must be 30-1800.", "rooms." + room.id() + ".encounter.enrage-seconds");
            if (encounter.linkSeconds() != 0) {
                if (encounter.type() != EncounterType.COUNCIL)
                    warn(issues, name + " sets link-seconds but isn't a council; it has no effect.", "rooms." + room.id() + ".encounter.link-seconds");
                if (encounter.linkSeconds() < 3 || encounter.linkSeconds() > 60)
                    error(issues, name + " link-seconds must be 3-60.", "rooms." + room.id() + ".encounter.link-seconds");
            }
            for (var phase : encounter.phases())
                if (phase.submergeSeconds() < 0 || phase.submergeSeconds() > 120)
                    error(issues, name + " phase at " + phase.atHealth() + "% submerge-seconds must be 0-120.", "rooms." + room.id() + ".encounter.phases");
            for (String unlock : room.unlocks())
                if (!Room.UNLOCKS.contains(unlock))
                    error(issues, name + " unlocks unknown '" + unlock + "'.", "Use one of: " + String.join(", ", Room.UNLOCKS));
            room.loot().items().forEach((item, amount) -> {
                if (!catalog.item(item) && !catalog.material(item)) error(issues, name + " has unknown loot item '" + item + "'.", "rooms." + room.id() + ".loot.items");
                if (amount < 1 || amount > 64) error(issues, name + " loot item '" + item + "' amount must be 1-64.", "rooms." + room.id() + ".loot.items");
            });
            if (room.loot().profile() != null && !catalog.lootProfile(room.loot().profile()))
                error(issues, name + " loot profile '" + room.loot().profile() + "' does not exist.", "/mobloot create " + room.loot().profile());
            if (room.loot().marks() < 0 || room.loot().marks() > 10_000) error(issues, name + " loot Marks must be 0-10000.", "rooms." + room.id() + ".loot.marks");
            switch (encounter.type()) {
                case WAVES, CLEAR -> { if (encounter.waves().isEmpty()) error(issues, name + " is a wave room without waves.", "/expedition wave " + room.id() + " <mob> [count]"); }
                case PUZZLE -> { if (room.puzzles().isEmpty()) error(issues, name + " is a puzzle room without puzzles.", "rooms." + room.id() + ".puzzles"); }
                case COUNCIL -> {
                    boss = true;
                    if (encounter.bosses().size() < 2 || encounter.bosses().size() > 4)
                        error(issues, name + " is a council of " + encounter.bosses().size() + "; use 2-4 bosses.", "rooms." + room.id() + ".encounter.bosses");
                    for (String member : encounter.bosses())
                        if (!catalog.boss(member) && !catalog.mob(member)) error(issues, name + " council boss '" + member + "' is not a known mob.", "rooms." + room.id() + ".encounter.bosses");
                    if (room.points().size() < encounter.bosses().size())
                        error(issues, name + " needs a spawn point for each council boss (the first " + encounter.bosses().size() + " points).", "Shift-right-click with the wand inside the room");
                    if (encounter.bossDamage() < 0.1 || encounter.bossDamage() > 5 || encounter.bossHealth() < 0.1 || encounter.bossHealth() > 5)
                        error(issues, name + " boss-damage and boss-health must be 0.1-5.", "rooms." + room.id() + ".encounter");
                }
                case SURVIVE -> {
                    if (encounter.waves().isEmpty()) error(issues, name + " is a survive room without waves to release.", "/expedition wave " + room.id() + " <mob> [count]");
                    if (encounter.surviveSeconds() < 10 || encounter.surviveSeconds() > 600)
                        error(issues, name + " survive-seconds must be 10-600.", "rooms." + room.id() + ".encounter.survive-seconds");
                }
                case BOSS -> {
                    boss = true;
                    if (encounter.bossDamage() < 0.1 || encounter.bossDamage() > 5 || encounter.bossHealth() < 0.1 || encounter.bossHealth() > 5)
                        error(issues, name + " boss-damage and boss-health must be 0.1-5.", "rooms." + room.id() + ".encounter");
                    if (encounter.boss() == null) error(issues, name + " is a boss room without a boss.", "/expedition boss " + room.id() + " <mob>");
                    else if (!catalog.boss(encounter.boss()) && !catalog.mob(encounter.boss()))
                        error(issues, name + " boss '" + encounter.boss() + "' is not a known mob.", "/expedition boss " + room.id() + " <mob>");
                    if (!encounter.addsAtHealth().isEmpty() && encounter.waves().isEmpty())
                        warn(issues, name + " has adds-at-health but no waves to release.", "/expedition wave " + room.id() + " <mob> [count]");
                }
                case NONE -> { }
            }
            for (int wave = 0; wave < encounter.waves().size(); wave++) {
                Wave value = encounter.waves().get(wave);
                int number = wave + 1;
                if (value.mobs().isEmpty()) error(issues, name + " wave " + (wave + 1) + " is empty.", "/expedition wave " + room.id() + " <mob> [count]");
                if (value.size() > 40) error(issues, name + " wave " + (wave + 1) + " has more than 40 mobs.", "Split it into more waves");
                if (value.delaySeconds() < 0 || value.delaySeconds() > 120) error(issues, name + " wave " + (wave + 1) + " delay must be 0-120 s.", "delay-seconds");
                value.mobs().forEach((mob, count) -> {
                    if (!catalog.mob(mob)) error(issues, name + " wave " + number + ": unknown mob '" + mob + "'.", "Use custom:<id> or vanilla:<type>");
                    if (count < 1 || count > 20) error(issues, name + ": " + mob + " count must be 1-20.", "/expedition wave " + room.id() + " " + mob + " <count>");
                });
            }
            for (var script : encounter.scripts().entrySet()) {
                if (!ScriptAction.TRIGGER.matcher(script.getKey()).matches())
                    error(issues, name + " has an unknown script trigger '" + script.getKey() + "'.", "Use start, clear, enrage, revive, wave-N, health-N, timer-N, down-N or solved-<puzzle>");
                if (script.getKey().startsWith("health-") && !room.bossRoom())
                    warn(issues, name + " uses '" + script.getKey() + "' but has no boss.", "Use it in a boss room");
                if (script.getKey().startsWith("down-") && encounter.type() != EncounterType.COUNCIL)
                    warn(issues, name + " uses '" + script.getKey() + "' but isn't a council fight.", "Use it in a council room");
                if (script.getKey().startsWith("solved-") && !room.puzzles().containsKey(script.getKey().substring(7)))
                    error(issues, name + " has a script for unknown puzzle '" + script.getKey().substring(7) + "'.", "rooms." + room.id() + ".puzzles");
                for (ScriptAction action : script.getValue()) checkAction(issues, name, action, expedition, catalog);
            }
        }
        if (!rooms.isEmpty() && !boss) {
            if (expedition.story().goal() == ExpeditionDefinition.Goal.BOSSES) error(issues, "The goal is bosses, but there's no boss room.", "/expedition boss <room> <mob>");
            else warn(issues, "No boss room. A boss gives the run a clear finale.", "/expedition boss <room> <mob>");
        }
        unreachable(issues, expedition);
        if (expedition.exit() != null && !rooms.isEmpty()) {
            Room last = rooms.get(rooms.size() - 1);
            if (last.region() != null && !last.region().contains(expedition.exit()) && rooms.stream().noneMatch(room -> room.region() != null && room.region().contains(expedition.exit())))
                warn(issues, "The exit is outside every room. Players walk there after the last room.", "/expedition set exit");
        }
        if (expedition.spawn() != null && rooms.stream().anyMatch(room -> room.region() != null && room.region().contains(expedition.spawn())
            && room.encounter().type() != EncounterType.NONE))
            warn(issues, "The spawn is inside an encounter room, so it starts the moment players arrive.", "/expedition set spawn");
        return List.copyOf(issues);
    }

    public static boolean publishable(List<Issue> issues) { return issues.stream().noneMatch(Issue::error); }

    /** Rooms whose requirements can never all be met (a cycle, or a chain through one) can't start. */
    private static void unreachable(List<Issue> issues, ExpeditionDefinition expedition) {
        List<Room> rooms = expedition.roomList();
        java.util.Set<String> cleared = new java.util.HashSet<>();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int index = 0; index < rooms.size(); index++) {
                Room room = rooms.get(index);
                if (cleared.contains(room.id())) continue;
                List<String> requires = room.requires() != null ? room.requires() : index == 0 ? List.of() : List.of(rooms.get(index - 1).id());
                if (requires.stream().allMatch(required -> cleared.contains(required) || !expedition.rooms().containsKey(required))) {
                    cleared.add(room.id());
                    progress = true;
                }
            }
        }
        for (Room room : rooms) if (!cleared.contains(room.id()))
            error(issues, "Room '" + room.id() + "' can never start: its requirements loop back on themselves.", "rooms." + room.id() + ".requires");
    }

    private static void puzzles(List<Issue> issues, String name, Room room) {
        for (var puzzle : room.puzzles().values()) {
            String label = name + " puzzle '" + puzzle.id() + "'";
            for (int index = 0; index < puzzle.blocks().size(); index++)
                if (!room.region().contains(puzzle.blocks().get(index))) error(issues, label + " block #" + (index + 1) + " is outside the room.", "rooms." + room.id() + ".puzzles");
            switch (puzzle.type()) {
                case LEVERS, PLATES, TARGETS -> { if (puzzle.blocks().isEmpty()) error(issues, label + " lists no blocks.", "blocks: [[x, y, z], ...]"); }
                case SEQUENCE -> {
                    if (puzzle.blocks().size() < 2) error(issues, label + " needs at least two blocks to press in order.", "blocks: [[x, y, z], ...]");
                }
                case REACH -> {
                    if (puzzle.area() == null) error(issues, label + " has no goal area.", "area: [x1, y1, z1, x2, y2, z2]");
                    if (puzzle.failBelowY() != null && puzzle.start() == null) error(issues, label + " has fail-below but no start point to return fallers to.", "start: [x, y, z]");
                }
            }
            if (puzzle.seconds() < 0 || puzzle.seconds() > 300) error(issues, label + " seconds must be 0-300.", "seconds:");
            if (puzzle.supply() != null && !room.region().contains(puzzle.supply()))
                error(issues, label + "'s supply crate is outside the room.", "rooms." + room.id() + ".puzzles." + puzzle.id() + ".supply");
            if ((puzzle.type() == ExpeditionDefinition.PuzzleType.TARGETS || puzzle.type() == ExpeditionDefinition.PuzzleType.PLATES) && puzzle.supply() == null)
                warn(issues, label + " needs items (arrows or something to throw) but has no supply crate.", "supply: [x, y, z] (a barrel players right-click)");
        }
    }

    private static void story(List<Issue> issues, ExpeditionDefinition expedition, Catalog catalog) {
        var story = expedition.story();
        for (String required : story.requires()) {
            if (required.equals(expedition.id())) error(issues, "The expedition requires itself.", "requires: in the expedition file");
            else if (!catalog.expedition(required)) error(issues, "requires names unknown expedition '" + required + "'.", "requires: in the expedition file");
        }
        if (story.time() != null && (story.time() < 0 || story.time() > 23_999))
            error(issues, "time must be 0-23999 (6000 noon, 13000 dusk, 18000 midnight).", "time: in the expedition file");
        switch (story.goal()) {
            case FORCES -> { if (story.forces() < 1 || story.forces() > 2000) error(issues, "A forces goal needs forces: 1-2000 (now " + story.forces() + ").", "forces: in the expedition file"); }
            case TIMED -> {
                if (story.timeBonusSeconds() < 0 || story.timeBonusSeconds() > 600) error(issues, "time-bonus-seconds must be 0-600.", "time-bonus-seconds:");
            }
            default -> { }
        }
        story.campfires().values().forEach(fire -> {
            if (expedition.rooms().values().stream().anyMatch(room -> room.region() != null && room.region().contains(fire.at())
                && room.encounter().type() != EncounterType.NONE && room.encounter().type() != EncounterType.PUZZLE))
                warn(issues, "Campfire '" + fire.id() + "' is inside a fight room; it only heals between fights.", "campfires." + fire.id() + ".at");
        });
        story.setpieces().values().forEach(piece -> {
            if (!catalog.material(piece.block())) error(issues, "Set piece '" + piece.id() + "' uses unknown block '" + piece.block() + "'.", "setpieces." + piece.id() + ".block");
            if (piece.box().volume() > 8_192) error(issues, "Set piece '" + piece.id() + "' is too large (max 8192 blocks).", "setpieces." + piece.id() + ".box");
        });
        story.hazards().values().forEach(hazard -> {
            String where = "hazards." + hazard.id();
            if (hazard.everySeconds() < 2 || hazard.everySeconds() > 120) error(issues, "Hazard '" + hazard.id() + "' every must be 2-120 seconds.", where + ".every");
            if (hazard.count() < 1 || hazard.count() > 12) error(issues, "Hazard '" + hazard.id() + "' count must be 1-12.", where + ".count");
            if (hazard.damage() < 0 || hazard.damage() > 40) error(issues, "Hazard '" + hazard.id() + "' damage must be 0-40.", where + ".damage");
        });
        if (story.intro() != null && !story.cutscenes().containsKey(story.intro()))
            error(issues, "The intro plays unknown cutscene '" + story.intro() + "'.", "intro: in the expedition file");
        story.cutscenes().values().forEach(cutscene -> {
            if (cutscene.shots().isEmpty()) error(issues, "Cutscene '" + cutscene.id() + "' has no shots.", "cutscenes." + cutscene.id());
            if (cutscene.seconds() > 45) error(issues, "Cutscene '" + cutscene.id() + "' runs " + cutscene.seconds() + " s; keep it to 45.", "cutscenes." + cutscene.id());
            for (var shot : cutscene.shots()) {
                if (shot.sound() != null && !catalog.sound(shot.sound().split(" ")[0]))
                    error(issues, "Cutscene '" + cutscene.id() + "' plays unknown sound '" + shot.sound() + "'.", "cutscenes." + cutscene.id());
                for (var action : shot.actions())
                    if (action.verb() == ScriptAction.Verb.CUTSCENE) error(issues, "Cutscene '" + cutscene.id() + "' can't start another cutscene.", action.text());
            }
        });
        story.npcs().values().forEach(npc -> {
            if (!catalog.npcEntity(npc.entity())) error(issues, "NPC '" + npc.id() + "' uses an unsupported entity '" + npc.entity() + "'.", "npcs." + npc.id() + ".entity");
            if (npc.lines().isEmpty()) warn(issues, "NPC '" + npc.id() + "' has nothing to say.", "npcs." + npc.id() + ".lines");
        });
        story.mobs().values().forEach(profile -> {
            if (!catalog.mob(profile.mob())) error(issues, "Mob profile for unknown mob '" + profile.mob() + "'.", "mobs: in the expedition file");
            for (String ability : profile.abilities())
                if (!catalog.ability(ability)) error(issues, "Mob '" + profile.mob() + "' uses unknown ability '" + ability + "'.", abilityFix());
            if (profile.forces() < 0 || profile.forces() > 100) error(issues, "Mob '" + profile.mob() + "' forces must be 0-100.", "mobs." + profile.mob() + ".forces");
        });
    }

    private static String abilityFix() {
        return "Use " + String.join(", ", Abilities.ids());
    }

    private static void checkAction(List<Issue> issues, String room, ScriptAction action, ExpeditionDefinition expedition, Catalog catalog) {
        var story = expedition.story();
        switch (action.verb()) {
            case CUTSCENE -> { if (!story.cutscenes().containsKey(action.arg(0))) error(issues, room + " script plays unknown cutscene '" + action.arg(0) + "'.", action.text()); }
            case BUILD, BREAK, RESTORE -> { if (!story.setpieces().containsKey(action.arg(0))) error(issues, room + " script names unknown set piece '" + action.arg(0) + "'.", action.text()); }
            case HAZARD -> { if (!story.hazards().containsKey(action.arg(1))) error(issues, room + " script names unknown hazard '" + action.arg(1) + "'.", action.text()); }
            case CAMPFIRE -> { if (!story.campfires().containsKey(action.arg(0))) error(issues, room + " script lights unknown campfire '" + action.arg(0) + "'.", action.text()); }
            case SPAWN -> { if (!catalog.mob(action.arg(0))) error(issues, room + " script spawns unknown mob '" + action.arg(0) + "'.", action.text()); }
            case OPEN, CLOSE -> {
                Room target = expedition.rooms().get(action.arg(0));
                if (target == null) error(issues, room + " script names unknown room '" + action.arg(0) + "'.", action.text());
                else if (target.gate() == null) warn(issues, room + " script opens room '" + target.id() + "', which has no gate.", "/expedition gate " + target.id());
            }
            case EFFECT -> { if (!catalog.effect(action.arg(0))) error(issues, room + " script uses unknown effect '" + action.arg(0) + "'.", action.text()); }
            case SOUND -> { if (!catalog.sound(action.arg(0))) warn(issues, room + " script uses unknown sound '" + action.arg(0) + "'.", action.text()); }
            default -> { }
        }
    }

    private static String modifierFix() {
        return "Use " + String.join(", ", java.util.Arrays.stream(Modifier.values()).map(Modifier::key).toList());
    }

    private static void error(List<Issue> issues, String message, String fix) { issues.add(new Issue(Severity.ERROR, message, fix)); }

    private static void warn(List<Issue> issues, String message, String fix) { issues.add(new Issue(Severity.WARNING, message, fix)); }
}
