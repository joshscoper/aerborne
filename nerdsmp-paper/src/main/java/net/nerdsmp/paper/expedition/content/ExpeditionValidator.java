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
        if (expedition.minParty() < 1 || expedition.maxParty() > ExpeditionDefinition.MAX_PARTY || expedition.minParty() > expedition.maxParty())
            error(issues, "Party size must be between 1 and " + ExpeditionDefinition.MAX_PARTY + " with min <= max (now "
                + expedition.minParty() + "-" + expedition.maxParty() + ").", "/expedition set party <min> <max>");
        if (expedition.level() < 1 || expedition.level() > 100) error(issues, "Level must be 1-100.", "/expedition set level <1-100>");
        if (expedition.timeLimitMinutes() < 5 || expedition.timeLimitMinutes() > 180)
            error(issues, "Time limit must be 5-180 minutes.", "/expedition set time <minutes>");
        if (expedition.lives() != null && (expedition.lives() < 1 || expedition.lives() > 50))
            error(issues, "Lives must be 1-50.", "lives: in the expedition file");
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
            if (encounter.type() != EncounterType.NONE && room.points().isEmpty())
                error(issues, name + " has an encounter but no mob spawn points.", "Shift-right-click with the wand inside the room");
            switch (encounter.type()) {
                case WAVES -> { if (encounter.waves().isEmpty()) error(issues, name + " is a wave room without waves.", "/expedition wave " + room.id() + " <mob> [count]"); }
                case SURVIVE -> {
                    if (encounter.waves().isEmpty()) error(issues, name + " is a survive room without waves to release.", "/expedition wave " + room.id() + " <mob> [count]");
                    if (encounter.surviveSeconds() < 10 || encounter.surviveSeconds() > 600)
                        error(issues, name + " survive-seconds must be 10-600.", "rooms." + room.id() + ".encounter.survive-seconds");
                }
                case BOSS -> {
                    boss = true;
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
                    error(issues, name + " has an unknown script trigger '" + script.getKey() + "'.", "Use start, clear, wave-N, health-N or timer-N");
                if (script.getKey().startsWith("health-") && encounter.type() != EncounterType.BOSS)
                    warn(issues, name + " uses '" + script.getKey() + "' but has no boss.", "Use it in a boss room");
                for (ScriptAction action : script.getValue()) checkAction(issues, name, action, expedition, catalog);
            }
        }
        if (!rooms.isEmpty() && !boss) warn(issues, "No boss room. A boss gives the run a clear finale.", "/expedition boss <room> <mob>");
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

    private static void checkAction(List<Issue> issues, String room, ScriptAction action, ExpeditionDefinition expedition, Catalog catalog) {
        switch (action.verb()) {
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
