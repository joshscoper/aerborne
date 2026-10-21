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
    Map<String, TierOverride> tiers
) {
    public static final String ID = "[a-z0-9_]{1,32}";
    public static final int MAX_PARTY = 5;

    public ExpeditionDefinition {
        Objects.requireNonNull(id, "id");
        rooms = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rooms));
        modifiers = List.copyOf(modifiers);
        tiers = Map.copyOf(tiers);
    }

    public List<Room> roomList() { return List.copyOf(rooms.values()); }

    public Optional<Room> roomAt(double x, double y, double z) {
        return rooms.values().stream().filter(room -> room.region() != null && room.region().contains(x, y, z)).findFirst();
    }

    /** Returns a copy with one field group replaced; setup commands edit definitions through these. */
    public ExpeditionDefinition withRooms(Map<String, Room> next) {
        return new ExpeditionDefinition(id, title, description, icon, dimension, biome, templateWorld, sample, published, level,
            minParty, maxParty, timeLimitMinutes, lives, spawn, exit, next, loot, campaignPoints, modifiers, tiers);
    }

    public ExpeditionDefinition withPublished(boolean value) {
        return new ExpeditionDefinition(id, title, description, icon, dimension, biome, templateWorld, sample, value, level,
            minParty, maxParty, timeLimitMinutes, lives, spawn, exit, rooms, loot, campaignPoints, modifiers, tiers);
    }

    public ExpeditionDefinition withPoints(Point newSpawn, Point newExit) {
        return new ExpeditionDefinition(id, title, description, icon, dimension, biome, templateWorld, sample, published, level,
            minParty, maxParty, timeLimitMinutes, lives, newSpawn, newExit, rooms, loot, campaignPoints, modifiers, tiers);
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

    public record Room(String id, String title, Box region, Point checkpoint, Box gate, List<Point> points, Encounter encounter) {
        public Room {
            points = List.copyOf(points);
        }

        public Room withRegion(Box value) { return new Room(id, title, value, checkpoint, gate, points, encounter); }
        public Room withCheckpoint(Point value) { return new Room(id, title, region, value, gate, points, encounter); }
        public Room withGate(Box value) { return new Room(id, title, region, checkpoint, value, points, encounter); }
        public Room withPoints(List<Point> value) { return new Room(id, title, region, checkpoint, gate, value, encounter); }
        public Room withEncounter(Encounter value) { return new Room(id, title, region, checkpoint, gate, points, value); }
    }

    public enum EncounterType { NONE, WAVES, BOSS, SURVIVE }

    /**
     * {@code waves} are the room's mob groups. Boss rooms use them as adds released at {@code addsAtHealth}
     * thresholds; survive rooms release them in a loop until the timer ends.
     */
    public record Encounter(EncounterType type, List<Wave> waves, String boss, int bossLevelBonus,
                            int surviveSeconds, List<Integer> addsAtHealth, Map<String, List<ScriptAction>> scripts) {
        public static final Encounter NONE = new Encounter(EncounterType.NONE, List.of(), null, 0, 0, List.of(), Map.of());

        public Encounter {
            waves = List.copyOf(waves);
            addsAtHealth = List.copyOf(addsAtHealth);
            scripts = Map.copyOf(scripts);
        }

        public List<ScriptAction> script(String trigger) { return scripts.getOrDefault(trigger, List.of()); }

        public Encounter withWaves(List<Wave> value) {
            return new Encounter(type == EncounterType.NONE ? EncounterType.WAVES : type, value, boss, bossLevelBonus,
                surviveSeconds, addsAtHealth, scripts);
        }

        public Encounter withBoss(String value) {
            return new Encounter(EncounterType.BOSS, waves, value, bossLevelBonus, surviveSeconds, addsAtHealth, scripts);
        }
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

    /** Optional per-expedition changes to a global difficulty tier. Null fields keep the global value. */
    public record TierOverride(Boolean enabled, Integer levelBonus, Integer lives, Integer lootRolls, List<String> modifiers) {
        public TierOverride {
            modifiers = modifiers == null ? null : List.copyOf(modifiers);
        }
    }
}
