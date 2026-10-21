package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Encounter;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.EncounterType;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Goal;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.Tier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The rules of one run, free of Bukkit: which rooms can start, when waves and adds are released,
 * puzzles, councils, boss phases, enrage timers, enemy forces, lives, wipes, checkpoints, the clock
 * and the finish. The service feeds it events and carries out the {@link Effect}s it returns
 * (spawning, opening gates, teleports), so every rule is unit tested.
 *
 * <p>Rooms run one at a time. A room can start once the rooms it {@code requires} are cleared (by
 * default, the room before it), so a map can be a straight corridor or a hub with wings taken in any
 * order. A room clears when its encounter is done and all of its puzzles are solved.
 */
public final class RunProgress {
    public enum Phase { ACTIVE, COMPLETE, FAILED }
    public enum RoomState { WAITING, ACTIVE, CLEARED }

    public sealed interface Effect {
        record StartRoom(String room) implements Effect { }
        record SpawnWave(String room, int wave) implements Effect { }
        record SpawnBoss(String room, int index) implements Effect {
            public SpawnBoss(String room) { this(room, 0); }
        }
        record Script(String room, String trigger) implements Effect { }
        record OpenGate(String room) implements Effect { }
        record DespawnRoom(String room) implements Effect { }
        record ResetRoom(String room) implements Effect { }
        record Checkpoint(String room) implements Effect { }
        record ClearRoom(String room) implements Effect { }
        record Lives(int remaining, UUID died) implements Effect { }
        record Complete(long seconds, boolean underPar) implements Effect { }
        record Fail(String reason) implements Effect { }
        /** A puzzle in the active room was solved. */
        record PuzzleSolved(String room, String puzzle) implements Effect { }
        /** A boss crossed into phase {@code phase} (1-based) of its encounter. */
        record BossPhase(String room, int phase) implements Effect { }
        /** The room's enrage timer ran out. */
        record Enrage(String room) implements Effect { }
        /** Enough enemy forces have been slain to open the final room. */
        record ForcesComplete() implements Effect { }
        /** Time was added to the clock (timed runs). */
        record TimeBonus(int seconds) implements Effect { }
        /** A lit campfire became the respawn point. */
        record Campfire(String campfire) implements Effect { }
    }

    /** Sidebar line: an objective with its state and indent depth. */
    public record Objective(String text, Status status, int depth) {
        public enum Status { DONE, ACTIVE, OPEN, LOCKED }
    }

    /** Seconds between survive-room waves when the wave sets no delay. */
    static final int SURVIVE_INTERVAL = 15;
    /** Survive rooms hold back the next wave while this many mobs are still up. */
    static final int SURVIVE_CROWD = 25;
    /** Checkpoint ids for campfires carry this prefix so they never clash with room ids. */
    public static final String CAMPFIRE = "campfire:";

    private final ExpeditionDefinition expedition;
    private final Tier tier;
    private final Set<Modifier> modifiers;
    private final List<Room> rooms;
    private final RoomState[] states;
    private final Set<UUID> present = new LinkedHashSet<>();
    private final Set<UUID> dead = new HashSet<>();
    private final long startedAt;
    private final int finalRoom;
    private Phase phase = Phase.ACTIVE;
    private int lives;
    private int active = -1;
    private String checkpoint;
    private int waveIndex = -1;
    private long nextWaveAt = -1;
    private int pendingWave = -1;
    private long roomStartedAt;
    private boolean bossSpawned;
    private boolean encounterDone;
    private boolean enraged;
    private final Set<Integer> bossesAlive = new TreeSet<>();
    private int bossesDown;
    private final Set<String> solved = new LinkedHashSet<>();
    private final Set<String> fired = new HashSet<>();
    private int thresholdsPassed;
    private int phasesPassed;
    private long finishedSeconds;
    private long limitSeconds;
    private int forces;
    private boolean forcesAnnounced;

    public RunProgress(ExpeditionDefinition expedition, Tier tier, Set<Modifier> modifiers, Set<UUID> members, long now) {
        this.expedition = expedition;
        this.tier = tier;
        this.modifiers = Set.copyOf(modifiers);
        this.rooms = expedition.roomList();
        this.states = new RoomState[rooms.size()];
        java.util.Arrays.fill(states, RoomState.WAITING);
        this.present.addAll(members);
        this.lives = tier.lives();
        this.startedAt = now;
        this.limitSeconds = expedition.timeLimitMinutes() * 60L;
        int last = -1;
        for (int index = 0; index < rooms.size(); index++) if (!rooms.get(index).optional()) last = index;
        this.finalRoom = last;
    }

    public Phase phase() { return phase; }
    public int lives() { return lives; }
    public Tier tier() { return tier; }
    public Set<Modifier> modifiers() { return modifiers; }
    public ExpeditionDefinition expedition() { return expedition; }
    /** Room id or {@code campfire:<id>} players respawn at, or null for the party spawn. */
    public String checkpoint() { return checkpoint; }
    public Set<UUID> present() { return Set.copyOf(present); }
    public boolean dead(UUID member) { return dead.contains(member); }
    public long finishedSeconds() { return finishedSeconds; }
    public int forces() { return forces; }
    public int forcesTarget() { return expedition.story().goal() == Goal.FORCES ? expedition.story().forces() : 0; }
    public int bossesDown() { return bossesDown; }
    public Set<String> solved() { return Set.copyOf(solved); }

    /** Cleared required rooms, for progress displays. */
    public int roomIndex() {
        int cleared = 0;
        for (int index = 0; index < rooms.size(); index++) if (states[index] == RoomState.CLEARED && counts(index)) cleared++;
        return cleared;
    }

    /** Required rooms in total. */
    public int roomCount() {
        int total = 0;
        for (int index = 0; index < rooms.size(); index++) if (counts(index)) total++;
        return total;
    }

    public Room activeRoom() { return active >= 0 ? rooms.get(active) : null; }

    /** The first room that can start now, for "push on to…" hints; null when none. */
    public Room nextRoom() {
        for (int index = 0; index < rooms.size(); index++) if (available(index)) return rooms.get(index);
        return null;
    }

    /** True when entering this room now would start it. */
    public boolean startable(String roomId) {
        int index = index(roomId);
        return index >= 0 && active < 0 && available(index);
    }

    public RoomState state(String roomId) {
        int index = index(roomId);
        if (index < 0) throw new IllegalArgumentException("Unknown room " + roomId);
        return states[index];
    }

    public long elapsedSeconds(long now) { return Math.max(0, (now - startedAt) / 1000); }

    public long secondsLeft(long now) { return Math.max(0, limitSeconds - elapsedSeconds(now)); }

    /** Seconds the active survive room still has to hold, or -1. */
    public long surviveLeft(long now) {
        Room room = activeRoom();
        if (room == null || room.encounter().type() != EncounterType.SURVIVE || encounterDone) return -1;
        return Math.max(0, room.encounter().surviveSeconds() - (now - roomStartedAt) / 1000);
    }

    /** A party member walked into a room. Any room whose requirements are met can start while none is running. */
    public List<Effect> enter(String roomId, long now) {
        List<Effect> effects = new ArrayList<>();
        if (phase != Phase.ACTIVE || active >= 0) return effects;
        int index = index(roomId);
        if (index < 0 || !available(index)) return effects;
        Room room = rooms.get(index);
        active = index;
        states[index] = RoomState.ACTIVE;
        roomStartedAt = now;
        waveIndex = -1;
        nextWaveAt = -1;
        pendingWave = -1;
        bossSpawned = false;
        encounterDone = false;
        enraged = false;
        bossesAlive.clear();
        bossesDown = 0;
        thresholdsPassed = 0;
        phasesPassed = 0;
        solved.clear();
        fired.clear();
        effects.add(new Effect.StartRoom(room.id()));
        Encounter encounter = room.encounter();
        effects.add(new Effect.Script(room.id(), "start"));
        switch (encounter.type()) {
            case NONE, PUZZLE -> {
                encounterDone = true;
                maybeClear(effects, now);
            }
            case WAVES -> scheduleWave(effects, 0, encounter.waves().get(0).delaySeconds(), now);
            case CLEAR -> {
                for (int wave = 0; wave < encounter.waves().size(); wave++) {
                    pendingWave = wave;
                    release(effects);
                }
            }
            case SURVIVE -> scheduleWave(effects, 0, 0, now);
            case BOSS, COUNCIL -> {
                bossSpawned = true;
                List<String> bosses = encounter.bossList();
                for (int boss = 0; boss < Math.max(1, bosses.size()); boss++) {
                    bossesAlive.add(boss);
                    effects.add(new Effect.SpawnBoss(room.id(), boss));
                }
                if (encounter.addsAtHealth().isEmpty() && !encounter.waves().isEmpty())
                    scheduleWave(effects, 0, encounter.waves().get(0).delaySeconds(), now);
            }
        }
        return effects;
    }

    /**
     * Called about once a second with the number of living mobs in the active room (bosses excluded).
     * Releases due waves, fires timers, ends survive rooms, enrages and enforces the clock.
     */
    public List<Effect> tick(long now, int alive) {
        List<Effect> effects = new ArrayList<>();
        if (phase != Phase.ACTIVE) return effects;
        if (elapsedSeconds(now) >= limitSeconds) {
            fail(effects, "Time ran out.");
            return effects;
        }
        Room room = activeRoom();
        if (room == null) return effects;
        Encounter encounter = room.encounter();
        long roomSeconds = (now - roomStartedAt) / 1000;
        for (String trigger : encounter.scripts().keySet())
            if (trigger.startsWith("timer-") && roomSeconds >= Long.parseLong(trigger.substring(6)) && fired.add(trigger))
                effects.add(new Effect.Script(room.id(), trigger));
        if (encounter.enrageSeconds() > 0 && !enraged && !encounterDone && roomSeconds >= encounter.enrageSeconds()) {
            enraged = true;
            effects.add(new Effect.Enrage(room.id()));
            effects.add(new Effect.Script(room.id(), "enrage"));
        }
        boolean released = false;
        if (pendingWave >= 0 && now >= nextWaveAt) {
            release(effects);
            released = true;
        }
        if (encounterDone) return effects; // Waiting on puzzles.
        switch (encounter.type()) {
            case WAVES -> {
                if (!released && alive == 0 && pendingWave < 0 && waveIndex >= 0) {
                    int next = waveIndex + 1;
                    if (next < encounter.waves().size()) scheduleWave(effects, next, encounter.waves().get(next).delaySeconds(), now);
                    else finishEncounter(effects, now, false);
                }
            }
            case CLEAR -> { if (alive == 0 && waveIndex >= 0) finishEncounter(effects, now, false); }
            case SURVIVE -> {
                if (roomSeconds >= encounter.surviveSeconds()) finishEncounter(effects, now, true);
                else if (pendingWave < 0 && alive < SURVIVE_CROWD) {
                    int next = (waveIndex + 1) % encounter.waves().size();
                    int delay = encounter.waves().get(next).delaySeconds();
                    scheduleWave(effects, next, delay > 0 ? delay : SURVIVE_INTERVAL, now);
                }
            }
            default -> { }
        }
        return effects;
    }

    /** The boss's health changed (for a council, the average); releases adds, phases and health scripts. */
    public List<Effect> bossHealth(double percent) {
        List<Effect> effects = new ArrayList<>();
        Room room = activeRoom();
        if (phase != Phase.ACTIVE || room == null || !room.bossRoom() || encounterDone) return effects;
        Encounter encounter = room.encounter();
        while (thresholdsPassed < encounter.addsAtHealth().size() && percent <= encounter.addsAtHealth().get(thresholdsPassed)) {
            if (!encounter.waves().isEmpty()) {
                int wave = thresholdsPassed % encounter.waves().size();
                effects.add(new Effect.SpawnWave(room.id(), wave));
                effects.add(new Effect.Script(room.id(), "wave-" + (wave + 1)));
            }
            thresholdsPassed++;
        }
        while (phasesPassed < encounter.phases().size() && percent <= encounter.phases().get(phasesPassed).atHealth()) {
            phasesPassed++;
            effects.add(new Effect.BossPhase(room.id(), phasesPassed));
        }
        for (String trigger : encounter.scripts().keySet())
            if (trigger.startsWith("health-") && percent <= Integer.parseInt(trigger.substring(7)) && fired.add(trigger))
                effects.add(new Effect.Script(room.id(), trigger));
        return effects;
    }

    /** The current boss phase, 0 before the first threshold. */
    public int bossPhase() { return phasesPassed; }

    public List<Effect> bossDefeated(long now) { return bossDefeated(0, now); }

    /** One of the room's bosses fell. The room's fight ends when the last one does. */
    public List<Effect> bossDefeated(int index, long now) {
        List<Effect> effects = new ArrayList<>();
        Room room = activeRoom();
        if (phase != Phase.ACTIVE || room == null || !bossSpawned || !bossesAlive.remove(index)) return effects;
        bossesDown++;
        if (!bossesAlive.isEmpty()) {
            effects.add(new Effect.Script(room.id(), "down-" + bossesDown));
            return effects;
        }
        if (room.encounter().type() == EncounterType.COUNCIL) effects.add(new Effect.Script(room.id(), "down-" + bossesDown));
        finishEncounter(effects, now, true);
        return effects;
    }

    /** A puzzle in the active room was solved. */
    public List<Effect> solve(String roomId, String puzzle, long now) {
        List<Effect> effects = new ArrayList<>();
        Room room = activeRoom();
        if (phase != Phase.ACTIVE || room == null || !room.id().equals(roomId) || !room.puzzles().containsKey(puzzle) || !solved.add(puzzle))
            return effects;
        effects.add(new Effect.PuzzleSolved(roomId, puzzle));
        effects.add(new Effect.Script(roomId, "solved-" + puzzle));
        maybeClear(effects, now);
        return effects;
    }

    /** An encounter mob died, worth {@code value} enemy forces. */
    public List<Effect> killed(int value) {
        List<Effect> effects = new ArrayList<>();
        int target = forcesTarget();
        if (phase != Phase.ACTIVE || target <= 0 || value <= 0) return effects;
        forces = Math.min(target, forces + value);
        if (forces >= target && !forcesAnnounced) {
            forcesAnnounced = true;
            effects.add(new Effect.ForcesComplete());
        }
        return effects;
    }

    /** A script's {@code checkpoint} action. */
    public List<Effect> checkpointHere() {
        Room room = activeRoom();
        if (room == null) return List.of();
        checkpoint = room.id();
        return List.of(new Effect.Checkpoint(room.id()));
    }

    /** A campfire was lit and becomes the respawn point. */
    public List<Effect> campfire(String id) {
        if (phase != Phase.ACTIVE) return List.of();
        checkpoint = CAMPFIRE + id;
        return List.of(new Effect.Campfire(id));
    }

    /** A member died. Each death spends a life; everyone down at once is a wipe and resets the room. */
    public List<Effect> death(UUID member) {
        List<Effect> effects = new ArrayList<>();
        if (phase != Phase.ACTIVE || !present.contains(member)) return effects;
        dead.add(member);
        lives--;
        effects.add(new Effect.Lives(lives, member));
        if (lives <= 0) {
            fail(effects, "The party ran out of lives.");
            return effects;
        }
        if (dead.containsAll(present)) wipe(effects);
        return effects;
    }

    public void respawned(UUID member) { dead.remove(member); }

    public List<Effect> left(UUID member) {
        List<Effect> effects = new ArrayList<>();
        present.remove(member);
        dead.remove(member);
        if (phase == Phase.ACTIVE && !present.isEmpty() && dead.containsAll(present)) wipe(effects);
        return effects;
    }

    public void rejoined(UUID member) { present.add(member); }

    public List<Effect> abandon(String reason) {
        List<Effect> effects = new ArrayList<>();
        if (phase == Phase.ACTIVE) fail(effects, reason);
        return effects;
    }

    /** Lines for the run sidebar: enemy forces, then each room with the active room's puzzles under it. */
    public List<Objective> objectives(long now) {
        List<Objective> lines = new ArrayList<>();
        int target = forcesTarget();
        if (target > 0) lines.add(new Objective("Enemy forces " + Math.min(100, forces * 100 / target) + "%",
            forces >= target ? Objective.Status.DONE : Objective.Status.ACTIVE, 0));
        for (int index = 0; index < rooms.size(); index++) {
            Room room = rooms.get(index);
            boolean hallway = room.encounter().type() == EncounterType.NONE && room.puzzles().isEmpty();
            if (hallway) continue;
            Objective.Status status = states[index] == RoomState.CLEARED ? Objective.Status.DONE
                : states[index] == RoomState.ACTIVE ? Objective.Status.ACTIVE
                : available(index) ? Objective.Status.OPEN : Objective.Status.LOCKED;
            String text = room.title() + (room.optional() ? " (optional)" : "");
            if (status == Objective.Status.ACTIVE) text += detail(room, now);
            lines.add(new Objective(text, status, 0));
            if (status == Objective.Status.ACTIVE) for (var puzzle : room.puzzles().values())
                lines.add(new Objective(puzzle.title(), solved.contains(puzzle.id()) ? Objective.Status.DONE : Objective.Status.ACTIVE, 1));
        }
        return lines;
    }

    private String detail(Room room, long now) {
        Encounter encounter = room.encounter();
        if (encounterDone) return "";
        return switch (encounter.type()) {
            case WAVES -> encounter.waves().size() > 1 ? " · wave " + Math.max(1, waveIndex + 1) + "/" + encounter.waves().size() : "";
            case SURVIVE -> {
                long left = surviveLeft(now);
                yield String.format(Locale.ROOT, " · %d:%02d", left / 60, left % 60);
            }
            case COUNCIL -> " · " + bossesDown + "/" + encounter.bosses().size() + " down";
            default -> "";
        };
    }

    private void wipe(List<Effect> effects) {
        Room room = activeRoom();
        if (room == null) return;
        states[active] = RoomState.WAITING;
        active = -1;
        effects.add(new Effect.ResetRoom(room.id()));
        waveIndex = -1;
        nextWaveAt = -1;
        pendingWave = -1;
        bossSpawned = false;
        bossesAlive.clear();
        solved.clear();
        encounterDone = false;
    }

    private void scheduleWave(List<Effect> effects, int wave, int delaySeconds, long now) {
        pendingWave = wave;
        nextWaveAt = now + Math.max(0, delaySeconds) * 1000L;
        if (delaySeconds <= 0) release(effects);
    }

    private void release(List<Effect> effects) {
        Room room = rooms.get(active);
        waveIndex = pendingWave;
        pendingWave = -1;
        nextWaveAt = -1;
        effects.add(new Effect.SpawnWave(room.id(), waveIndex));
        effects.add(new Effect.Script(room.id(), "wave-" + (waveIndex + 1)));
    }

    /** The room's fight is over; it clears now or once its puzzles are solved. */
    private void finishEncounter(List<Effect> effects, long now, boolean despawn) {
        encounterDone = true;
        nextWaveAt = -1;
        pendingWave = -1;
        if (despawn) effects.add(new Effect.DespawnRoom(rooms.get(active).id()));
        maybeClear(effects, now);
    }

    private void maybeClear(List<Effect> effects, long now) {
        Room room = rooms.get(active);
        if (!encounterDone || !solved.containsAll(room.puzzles().keySet())) return;
        int index = active;
        states[index] = RoomState.CLEARED;
        active = -1;
        effects.add(new Effect.ClearRoom(room.id()));
        effects.add(new Effect.Script(room.id(), "clear"));
        if (room.gate() != null) effects.add(new Effect.OpenGate(room.id()));
        if (room.checkpoint() != null) {
            checkpoint = room.id();
            effects.add(new Effect.Checkpoint(room.id()));
        }
        int bonus = expedition.story().timeBonusSeconds();
        if (expedition.story().goal() == Goal.TIMED && bonus > 0 && counts(index)) {
            limitSeconds += bonus;
            effects.add(new Effect.TimeBonus(bonus));
        }
        if (won()) {
            phase = Phase.COMPLETE;
            finishedSeconds = elapsedSeconds(now);
            boolean underPar = modifiers.contains(Modifier.TIMED) && finishedSeconds <= Modifier.parSeconds(expedition.timeLimitMinutes());
            effects.add(new Effect.Complete(finishedSeconds, underPar));
        }
    }

    private boolean won() {
        for (int index = 0; index < rooms.size(); index++) if (counts(index) && states[index] != RoomState.CLEARED) return false;
        return forces >= forcesTarget();
    }

    /** Whether a room is needed to win. */
    private boolean counts(int index) {
        Room room = rooms.get(index);
        if (room.optional()) return false;
        return expedition.story().goal() != Goal.BOSSES || room.bossRoom();
    }

    private boolean available(int index) {
        if (states[index] != RoomState.WAITING) return false;
        Room room = rooms.get(index);
        List<String> requires = room.requires();
        if (requires == null) {
            if (index > 0 && states[index - 1] != RoomState.CLEARED) return false;
        } else for (String required : requires) {
            int other = index(required);
            if (other >= 0 && states[other] != RoomState.CLEARED) return false;
        }
        // Forces runs hold the final room until enough enemies are slain.
        return index != finalRoom || forces >= forcesTarget();
    }

    private int index(String roomId) {
        for (int index = 0; index < rooms.size(); index++) if (rooms.get(index).id().equals(roomId)) return index;
        return -1;
    }

    private void fail(List<Effect> effects, String reason) {
        phase = Phase.FAILED;
        Room room = activeRoom();
        if (room != null) effects.add(new Effect.DespawnRoom(room.id()));
        effects.add(new Effect.Fail(reason));
    }
}
