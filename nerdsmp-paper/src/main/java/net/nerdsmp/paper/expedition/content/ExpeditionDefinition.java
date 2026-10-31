package net.nerdsmp.paper.expedition.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One instanced party adventure, as staff author it in {@code expeditions/<id>.yml}. Coordinates are
 * template-world block positions; every run world is a copy of the template, so they hold in every run.
 * Nothing here touches Bukkit, which keeps loading and validation testable without a server.
 */
public record ExpeditionDefinition(
    String id,
    String title,
    String description,
    String icon,
    Dimension dimension,
    String biome,
    String templateWorld,
    String sample,
    boolean published,
    int level,
    int minParty,
    int maxParty,
    int timeLimitMinutes,
    Integer lives,
    Point spawn,
    Point exit,
    Map<String, Room> rooms,
    Loot loot,
    int campaignPoints,
    List<String> modifiers,
    Map<String, TierOverride> tiers,
    Integer difficulty,
    Guide guide,
    Story story
) {
    public static final String ID = "[a-z0-9_]{1,32}";
    public static final int MAX_PARTY = 5;
    /** Raids take larger groups. */
    public static final int MAX_RAID = 8;

    public ExpeditionDefinition {
        Objects.requireNonNull(id, "id");
        rooms = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rooms));
        modifiers = List.copyOf(modifiers);
        tiers = Map.copyOf(tiers);
        story = story == null ? Story.EMPTY : story;
    }

    /** The largest group this expedition's type allows. */
    public int partyCap() { return story.raid() ? MAX_RAID : MAX_PARTY; }

    /**
     * How a run is won. {@code ROOMS}: clear every required room. {@code BOSSES}: defeat every boss
     * room, in any order the map allows. {@code FORCES}: slay enough enemies (each worth some
     * forces) before the final room opens. {@code TIMED}: beat the clock, which each cleared room
     * pushes back by {@link Story#timeBonusSeconds()}.
     */
    public enum Goal { ROOMS, BOSSES, FORCES, TIMED }

    /** Encounter roles. Elites and mini-bosses carry abilities; mini-bosses also get a boss bar. */
    public enum Role { NORMAL, ELITE, MINIBOSS, BOSS }

    /** How a mob fights. {@code BRUISER}s close in; {@code CASTER}s keep their distance and fight with their abilities. */
    public enum Behavior { BRUISER, CASTER }

    /**
     * Everything beyond rooms and fights: the win condition, rest points, set pieces that change the
     * map, arena hazards, cutscenes, talking characters and per-mob roles and abilities.
     */
    public record Story(Goal goal, int forces, int timeBonusSeconds, boolean raid,
                        Map<String, Campfire> campfires, Map<String, SetPiece> setpieces, Map<String, Hazard> hazards,
                        Map<String, Cutscene> cutscenes, Map<String, Npc> npcs, Map<String, MobProfile> mobs, Integer time,
                        List<String> requires, Map<String, LootChest> chests, RewardChests rewardChests, String intro, List<String> outro,
                        Map<String, Pack> packs, int recordsVersion, Map<String, CompanionDrop> companionDrops,
                        Map<String, Device> devices, List<Ambience> ambience) {
        public static final Story EMPTY = new Story(Goal.ROOMS, 0, 0, false, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, List.of(),
            Map.of(), null, null, List.of(), Map.of(), 0, Map.of(), Map.of(), List.of());

        public Story(Goal goal, int forces, int timeBonusSeconds, boolean raid, Map<String, Campfire> campfires, Map<String, SetPiece> setpieces,
                     Map<String, Hazard> hazards, Map<String, Cutscene> cutscenes, Map<String, Npc> npcs, Map<String, MobProfile> mobs,
                     Integer time, List<String> requires, Map<String, LootChest> chests, RewardChests rewardChests) {
            this(goal, forces, timeBonusSeconds, raid, campfires, setpieces, hazards, cutscenes, npcs, mobs, time, requires, chests, rewardChests,
                null, List.of(), Map.of(), 0, Map.of(), Map.of(), List.of());
        }

        public Story(Goal goal, int forces, int timeBonusSeconds, boolean raid, Map<String, Campfire> campfires, Map<String, SetPiece> setpieces,
                     Map<String, Hazard> hazards, Map<String, Cutscene> cutscenes, Map<String, Npc> npcs, Map<String, MobProfile> mobs,
                     Integer time, List<String> requires) {
            this(goal, forces, timeBonusSeconds, raid, campfires, setpieces, hazards, cutscenes, npcs, mobs, time, requires, Map.of(), null,
                null, List.of(), Map.of(), 0, Map.of(), Map.of(), List.of());
        }

        public Story {
            goal = goal == null ? Goal.ROOMS : goal;
            campfires = ordered(campfires);
            setpieces = ordered(setpieces);
            hazards = ordered(hazards);
            cutscenes = ordered(cutscenes);
            npcs = ordered(npcs);
            mobs = ordered(mobs);
            requires = requires == null ? List.of() : List.copyOf(requires);
            chests = ordered(chests);
            outro = outro == null ? List.of() : List.copyOf(outro);
            packs = ordered(packs);
            companionDrops = ordered(companionDrops);
            devices = ordered(devices);
            ambience = ambience == null ? List.of() : List.copyOf(ambience);
        }

        /** How many secret caches the map hides, for the run's exploration score. */
        public int secrets() { return (int) chests.values().stream().filter(chest -> chest.kind() == ChestKind.SECRET).count(); }

        /** The profile for a wave mob id, or a plain one. */
        public MobProfile mob(String id) {
            return mobs.getOrDefault(id, MobProfile.plain(id));
        }

        private static <T> Map<String, T> ordered(Map<String, T> map) {
            return map == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }
    }

    /**
     * A group of enemies already standing in the world (or walking {@code patrol}). They wait until
     * someone comes within {@code aggro} blocks in plain sight, is hit, or a nearby fight spills into
     * them, then fight together and give up if dragged more than {@code leash} blocks from {@code at}.
     */
    public record Pack(String id, Point at, Map<String, Integer> mobs, int aggro, int leash, List<Point> patrol) {
        public static final int DEFAULT_AGGRO = 9;
        public static final int DEFAULT_LEASH = 36;

        public Pack {
            mobs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(mobs));
            patrol = patrol == null ? List.of() : List.copyOf(patrol);
        }
    }

    /** Crates (barrels) hold a little, chests more, and secret caches the best; each counts as found for the score. */
    public enum ChestKind { CRATE, CHEST, SECRET }

    /** A loot container in the map. Every player loots it once, with their own roll. */
    public record LootChest(String id, Point at, ChestKind kind, String title) { }

    /** A rare companion a boss can drop: each player rolls {@code chance} (0-1) for themselves, raised on harder tiers. */
    public record CompanionDrop(String companion, double chance) { }

    /** Where the end-of-run reward chests appear: the free chest and the Marks-bought bonus chest. */
    public record RewardChests(Point free, Point bonus) { }

    /** A rest point. Lit campfires heal nearby players out of combat and become the respawn point. */
    public record Campfire(String id, Point at, boolean lit, String title) { }

    /** How a set piece appears or goes: all at once, rising layer by layer, or crumbling with dust. */
    public enum PieceStyle { INSTANT, RISE, CRUMBLE }

    /** A region of the map a script can fill ({@code build}), empty ({@code break}) or put back ({@code restore}). */
    public record SetPiece(String id, Box box, String block, PieceStyle style) { }

    /**
     * Arena hazards, always telegraphed before they land. The first four strike patches of floor. The
     * rest use the room itself: a {@code LANE} rolls down one of its {@code lanes} until a wall or column
     * stops it, a {@code COLLAPSE} drops the next of its hanging {@code spots}, and a {@code VIGIL} snuffs
     * one of its {@code devices}, which spreads a pool until someone lights it again.
     */
    public enum HazardType {
        ROCKS, FIRE, ERUPTION, GAS, LANE, COLLAPSE, VIGIL;

        public boolean usesRoom() { return this == LANE || this == COLLAPSE || this == VIGIL; }
    }

    /** A straight run across the floor, from one [x, z] to another. */
    public record Lane(int x1, int z1, int x2, int z2) { }

    /**
     * A hazard over an area: every {@code everySeconds} it strikes {@code count} spots for {@code damage}.
     * A lane can leave the floor burning for {@code burnSeconds}, and {@code soul} draws it in blue.
     */
    public record Hazard(String id, HazardType type, Box area, int everySeconds, int count, double damage,
                         List<Lane> lanes, List<Point> spots, List<String> devices, int burnSeconds, boolean soul) {
        public Hazard {
            lanes = lanes == null ? List.of() : List.copyOf(lanes);
            spots = spots == null ? List.of() : List.copyOf(spots);
            devices = devices == null ? List.of() : List.copyOf(devices);
        }

        public Hazard(String id, HazardType type, Box area, int everySeconds, int count, double damage) {
            this(id, type, area, everySeconds, count, damage, List.of(), List.of(), List.of(), 0, false);
        }
    }

    /** A lit block players can right-click in a fight: wards ask them to douse it, vigils to light it again. */
    public record Device(String id, Point at, String title) { }

    /** A sound that drifts through the map (or only {@code area}), about once every {@code everySeconds}. */
    public record Ambience(String sound, int everySeconds, float volume, float pitch, Box area) { }

    /**
     * One camera position; the camera glides from each shot to the next over {@code seconds}, unless
     * the next shot {@code fade}s in (a cut through black). A shot can show a {@code title} card and a
     * subtitle ({@code text}), play a {@code sound}, speak {@code lines} ({@code "Speaker|line"}, or
     * {@code "|line"} for narration) spread across its length, and run script {@code actions} as it
     * starts (once for the party), so a scene can change the map.
     */
    public record Shot(double x, double y, double z, float yaw, float pitch, int seconds, String text, String title,
                       List<String> lines, String sound, List<ScriptAction> actions, boolean fade, Frame to) {
        public Shot {
            lines = lines == null ? List.of() : List.copyOf(lines);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        public Shot(double x, double y, double z, float yaw, float pitch, int seconds, String text) {
            this(x, y, z, yaw, pitch, seconds, text, null, List.of(), null, List.of(), false, null);
        }

        public Frame frame() { return new Frame(x, y, z, yaw, pitch); }
    }

    /** A camera position; a shot with a {@code to} frame moves there over its own length. */
    public record Frame(double x, double y, double z, float yaw, float pitch) { }

    public record Cutscene(String id, List<Shot> shots) {
        public Cutscene { shots = List.copyOf(shots); }

        public int seconds() { return shots.stream().mapToInt(Shot::seconds).sum(); }
    }

    /** A talking character placed at run start. Right-click to hear the next line. */
    public record Npc(String id, String name, String entity, Point at, List<String> lines, String quests) {
        public Npc { lines = List.copyOf(lines); }
        public Npc(String id, String name, String entity, Point at, List<String> lines) { this(id, name, entity, at, lines, null); }
    }

    /**
     * How a wave mob behaves in this expedition: its role, the abilities it uses and how much it
     * counts toward enemy forces. Mobs not listed are normal and worth one.
     */
    public record MobProfile(String mob, Role role, List<String> abilities, int forces, String title, Behavior behavior, String deathAbility) {
        public MobProfile {
            abilities = List.copyOf(abilities);
            behavior = behavior == null ? Behavior.BRUISER : behavior;
        }

        public MobProfile(String mob, Role role, List<String> abilities, int forces, String title, Behavior behavior) {
            this(mob, role, abilities, forces, title, behavior, null);
        }

        public MobProfile(String mob, Role role, List<String> abilities, int forces, String title) {
            this(mob, role, abilities, forces, title, Behavior.BRUISER);
        }

        public static MobProfile plain(String mob) { return new MobProfile(mob, Role.NORMAL, List.of(), 1, null); }

        public static int defaultForces(Role role) {
            return switch (role) { case NORMAL -> 1; case ELITE -> 4; case MINIBOSS -> 12; case BOSS -> 0; };
        }
    }

    /**
     * A room puzzle. {@code LEVERS}: switch every lever on. {@code SEQUENCE}: use the blocks in
     * {@code order} (mistakes reset; {@code show} flashes the order first). {@code PLATES}: hold every
     * pressure plate down at once. {@code REACH}: get someone into {@code area}; anyone falling below
     * {@code failBelowY} is put back at {@code start} without losing a life. {@code TARGETS}: hit every
     * target block within {@code seconds}. A target or plate puzzle can name a {@code supply} crate
     * that hands out what it needs (a bow and arrows, or counterweights).
     */
    public enum PuzzleType { LEVERS, SEQUENCE, PLATES, REACH, TARGETS }

    public record Puzzle(String id, PuzzleType type, String title, List<Point> blocks, List<Integer> order, boolean show,
                         Box area, Point start, Integer failBelowY, int seconds, Point supply) {
        public Puzzle {
            blocks = List.copyOf(blocks);
            order = List.copyOf(order);
        }

        public Puzzle(String id, PuzzleType type, String title, List<Point> blocks, List<Integer> order, boolean show,
                      Box area, Point start, Integer failBelowY, int seconds) {
            this(id, type, title, blocks, order, show, area, start, failBelowY, seconds, null);
        }

        /** Block indexes in the order they must be used. */
        public List<Integer> sequence() {
            if (!order.isEmpty()) return order;
            List<Integer> natural = new java.util.ArrayList<>();
            for (int index = 0; index < blocks.size(); index++) natural.add(index);
            return natural;
        }
    }

    public List<Room> roomList() { return List.copyOf(rooms.values()); }

    public Optional<Room> roomAt(double x, double y, double z) {
        return rooms.values().stream().filter(room -> room.region() != null && room.region().contains(x, y, z)).findFirst();
    }

    public enum Dimension {
        OVERWORLD, NETHER, END;

        public static Dimension parse(String raw) {
            return switch (raw == null ? "overworld" : raw.toLowerCase(java.util.Locale.ROOT)) {
                case "overworld", "normal" -> OVERWORLD;
                case "nether", "the_nether" -> NETHER;
                case "end", "the_end" -> END;
                default -> throw new IllegalArgumentException("Unknown dimension '" + raw + "'. Use overworld, nether or end.");
            };
        }

        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** A block position, optionally with a facing for teleport targets. */
    public record Point(int x, int y, int z, float yaw) {
        public double distanceSquared(double px, double py, double pz) {
            double dx = px - (x + .5), dy = py - y, dz = pz - (z + .5);
            return dx * dx + dy * dy + dz * dz;
        }

        public List<Object> serialize() {
            return yaw == 0 ? List.of(x, y, z) : List.of(x, y, z, (double) yaw);
        }
    }

    /** Inclusive block cuboid. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }

        public boolean contains(Point point) { return contains(point.x() + .5, point.y(), point.z() + .5); }

        public long volume() { return ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1); }

        public boolean intersects(Box other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        public List<Integer> serialize() { return List.of(minX, minY, minZ, maxX, maxY, maxZ); }
    }

    /**
     * {@code requires} lists the rooms that must be cleared before this one can start; null means the
     * room before it in the file. Optional rooms aren't needed to finish. Puzzles must all be solved
     * (as well as the encounter) for the room to clear. {@code seal} is a doorway that stays barred
     * until {@code requiresPacks} are dead and shuts behind the party during the fight, so anyone who
     * dies stays out until it ends.
     */
    public record Room(String id, String title, Box region, Point checkpoint, Box gate, List<Point> points, Encounter encounter,
                       List<String> requires, boolean optional, Map<String, Puzzle> puzzles, Loot loot, List<String> unlocks,
                       Box seal, List<String> requiresPacks) {
        /** What a room can unlock for everyone who clears it. */
        public static final List<String> UNLOCKS = List.of("nether");

        public Room {
            points = List.copyOf(points);
            requires = requires == null ? null : List.copyOf(requires);
            puzzles = puzzles == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(puzzles));
            loot = loot == null ? Loot.NONE : loot;
            unlocks = unlocks == null ? List.of() : List.copyOf(unlocks);
            requiresPacks = requiresPacks == null ? List.of() : List.copyOf(requiresPacks);
        }

        public Room(String id, String title, Box region, Point checkpoint, Box gate, List<Point> points, Encounter encounter,
                    List<String> requires, boolean optional, Map<String, Puzzle> puzzles, Loot loot, List<String> unlocks) {
            this(id, title, region, checkpoint, gate, points, encounter, requires, optional, puzzles, loot, unlocks, null, List.of());
        }

        public Room(String id, String title, Box region, Point checkpoint, Box gate, List<Point> points, Encounter encounter,
                    List<String> requires, boolean optional, Map<String, Puzzle> puzzles) {
            this(id, title, region, checkpoint, gate, points, encounter, requires, optional, puzzles, Loot.NONE, List.of());
        }

        public Room(String id, String title, Box region, Point checkpoint, Box gate, List<Point> points, Encounter encounter) {
            this(id, title, region, checkpoint, gate, points, encounter, null, false, Map.of());
        }

        public boolean bossRoom() {
            return encounter.type() == EncounterType.BOSS || encounter.type() == EncounterType.COUNCIL;
        }
    }

    /**
     * {@code CLEAR} releases every wave at once and clears when all are dead. {@code COUNCIL} is a
     * fight against several bosses at once. {@code PUZZLE} has no fight; solving the puzzles clears it.
     */
    public enum EncounterType { NONE, WAVES, BOSS, SURVIVE, CLEAR, COUNCIL, PUZZLE }

    /** A boss rising out of the floor, or a pile of bones drawing in the room's soul fires and closing around it. */
    public enum EntranceStyle { RISE, REANIMATE, SUMMON }

    /**
     * How a boss arrives: held out of reach, then brought up over {@code seconds}, {@code delaySeconds}
     * into the fight. {@code sources} are the fires a reanimation draws on.
     */
    public record Entrance(EntranceStyle style, int delaySeconds, int seconds, List<Point> sources) {
        public Entrance { sources = sources == null ? List.of() : List.copyOf(sources); }
    }

    /**
     * A boss phase: below {@code atHealth} percent the boss switches to these abilities. With
     * {@code submergeSeconds} it first withdraws, untouchable, for that long or until its adds are dead.
     */
    public record Phase(int atHealth, List<String> abilities, String title, int submergeSeconds) {
        public Phase { abilities = List.copyOf(abilities); }

        public Phase(int atHealth, List<String> abilities, String title) { this(atHealth, abilities, title, 0); }
    }

    /**
     * {@code waves} are the room's mob groups. Boss rooms use them as adds released at {@code addsAtHealth}
     * thresholds; survive rooms release them in a loop until the timer ends. A council with
     * {@code linkSeconds} is linked: a fallen member rises again unless the rest fall within that time.
     */
    public record Encounter(EncounterType type, List<Wave> waves, String boss, int bossLevelBonus,
                            int surviveSeconds, List<Integer> addsAtHealth, Map<String, List<ScriptAction>> scripts,
                            double bossDamage, double bossHealth, List<String> bosses, List<String> abilities,
                            List<Phase> phases, int enrageSeconds, int linkSeconds, Entrance entrance) {
        public static final Encounter NONE = new Encounter(EncounterType.NONE, List.of(), null, 0, 0, List.of(), Map.of(), 1, 1,
            List.of(), List.of(), List.of(), 0);

        public Encounter(EncounterType type, List<Wave> waves, String boss, int bossLevelBonus, int surviveSeconds,
                         List<Integer> addsAtHealth, Map<String, List<ScriptAction>> scripts, double bossDamage, double bossHealth,
                         List<String> bosses, List<String> abilities, List<Phase> phases, int enrageSeconds) {
            this(type, waves, boss, bossLevelBonus, surviveSeconds, addsAtHealth, scripts, bossDamage, bossHealth, bosses, abilities,
                phases, enrageSeconds, 0, null);
        }

        public Encounter {
            waves = List.copyOf(waves);
            addsAtHealth = List.copyOf(addsAtHealth);
            scripts = Map.copyOf(scripts);
            bosses = bosses == null ? List.of() : List.copyOf(bosses);
            abilities = abilities == null ? List.of() : List.copyOf(abilities);
            phases = phases == null ? List.of() : List.copyOf(phases);
        }

        public Encounter(EncounterType type, List<Wave> waves, String boss, int bossLevelBonus, int surviveSeconds,
                         List<Integer> addsAtHealth, Map<String, List<ScriptAction>> scripts, double bossDamage, double bossHealth) {
            this(type, waves, boss, bossLevelBonus, surviveSeconds, addsAtHealth, scripts, bossDamage, bossHealth,
                List.of(), List.of(), List.of(), 0);
        }

        /** Every boss this encounter spawns: the council, or the single boss. */
        public List<String> bossList() {
            if (type == EncounterType.COUNCIL) return bosses;
            return type == EncounterType.BOSS && boss != null ? List.of(boss) : List.of();
        }

        public List<ScriptAction> script(String trigger) { return scripts.getOrDefault(trigger, List.of()); }
    }

    /** Mob ids use the zone syntax: {@code custom:<id>} or {@code vanilla:<type>}. */
    public record Wave(Map<String, Integer> mobs, int delaySeconds) {
        public Wave {
            mobs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(mobs));
        }

        public int size() { return mobs.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    /** Completion rewards, rolled once per player per loot roll of the tier. */
    public record Loot(String profile, long marks, Map<String, Integer> items) {
        public static final Loot NONE = new Loot(null, 0, Map.of());

        public Loot {
            items = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(items));
        }
    }

    /**
     * Player-facing advice shown in the expedition's in-game guide book and on the website: an overview,
     * general tips, a line per room and notes on the boss. Everything is optional.
     */
    public record Guide(String overview, List<String> tips, Map<String, String> rooms, String boss, String lore) {
        public static final Guide EMPTY = new Guide("", List.of(), Map.of(), "", "");

        public Guide {
            tips = List.copyOf(tips);
            rooms = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rooms));
            lore = lore == null ? "" : lore;
        }

        public Guide(String overview, List<String> tips, Map<String, String> rooms, String boss) { this(overview, tips, rooms, boss, ""); }

        public boolean isEmpty() { return overview.isBlank() && tips.isEmpty() && rooms.isEmpty() && boss.isBlank(); }
    }

    /** Optional per-expedition changes to a global difficulty tier. Null fields keep the global value. */
    public record TierOverride(Boolean enabled, Integer levelBonus, Integer lives, Integer lootRolls, List<String> modifiers,
                               Double health, Double damage) {
        public TierOverride {
            modifiers = modifiers == null ? null : List.copyOf(modifiers);
        }

        public TierOverride(Boolean enabled, Integer levelBonus, Integer lives, Integer lootRolls, List<String> modifiers) {
            this(enabled, levelBonus, lives, lootRolls, modifiers, null, null);
        }
    }
}
