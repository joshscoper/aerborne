package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Encounter;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.EncounterType;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.Tier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of one run, free of Bukkit: which room is live, when waves and adds are released,
 * lives, wipes, checkpoints, the time limit and the finish. The service feeds it events and carries
 * out the {@link Effect}s it returns (spawning, opening gates, teleports), so every rule is unit tested.
 */
public final class RunProgress {
    public enum Phase { ACTIVE, COMPLETE, FAILED }
    public enum RoomState { WAITING, ACTIVE, CLEARED }

    public sealed interface Effect {
        record StartRoom(String room) implements Effect { }
        record SpawnWave(String room, int wave) implements Effect { }
        record SpawnBoss(String room) implements Effect { }
        record Script(String room, String trigger) implements Effect { }
        record OpenGate(String room) implements Effect { }
        record DespawnRoom(String room) implements Effect { }
        record ResetRoom(String room) implements Effect { }
        record Checkpoint(String room) implements Effect { }
        record ClearRoom(String room) implements Effect { }
        record Lives(int remaining, UUID died) implements Effect { }
        record Complete(long seconds, boolean underPar) implements Effect { }
        record Fail(String reason) implements Effect { }
    }

    /** Seconds between survive-room waves when the wave sets no delay. */
    static final int SURVIVE_INTERVAL = 15;
    /** Survive rooms hold back the next wave while this many mobs are still up. */
    static final int SURVIVE_CROWD = 25;

    private final ExpeditionDefinition expedition;
    private final Tier tier;
    private final Set<Modifier> modifiers;
    private final List<Room> rooms;
    private final RoomState[] states;
    private final Set<UUID> present = new LinkedHashSet<>();
    private final Set<UUID> dead = new HashSet<>();
    private final long startedAt;
    private Phase phase = Phase.ACTIVE;
    private int lives;
    private int current;
    private String checkpoint;
    private int waveIndex = -1;
    private long nextWaveAt = -1;
    private int pendingWave = -1;
    private long roomStartedAt;
    private boolean bossSpawned;
    private final Set<String> fired = new HashSet<>();
    private int thresholdsPassed;
    private long finishedSeconds;

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
    }

    public Phase phase() { return phase; }
    public int lives() { return lives; }
    public Tier tier() { return tier; }
    public Set<Modifier> modifiers() { return modifiers; }
    public ExpeditionDefinition expedition() { return expedition; }
    /** Room whose checkpoint players respawn at, or null for the party spawn. */
    public String checkpoint() { return checkpoint; }
    public Set<UUID> present() { return Set.copyOf(present); }
    public boolean dead(UUID member) { return dead.contains(member); }
    public long finishedSeconds() { return finishedSeconds; }
    public int roomIndex() { return current; }
    public int roomCount() { return rooms.size(); }

    public Room activeRoom() {
        return current < rooms.size() && states[current] == RoomState.ACTIVE ? rooms.get(current) : null;
    }

    public Room nextRoom() { return current < rooms.size() ? rooms.get(current) : null; }

    public RoomState state(String roomId) {
        for (int index = 0; index < rooms.size(); index++) if (rooms.get(index).id().equals(roomId)) return states[index];
        throw new IllegalArgumentException("Unknown room " + roomId);
    }

    public long elapsedSeconds(long now) { return Math.max(0, (now - startedAt) / 1000); }

    /** A party member walked into a room. Only the next uncleared room can start. */
    public List<Effect> enter(String roomId, long now) {
        List<Effect> effects = new ArrayList<>();
        if (phase != Phase.ACTIVE || current >= rooms.size()) return effects;
        Room room = rooms.get(current);
        if (!room.id().equals(roomId) || states[current] != RoomState.WAITING) return effects;
        states[current] = RoomState.ACTIVE;
        roomStartedAt = now;
        waveIndex = -1;
        nextWaveAt = -1;
        pendingWave = -1;
        bossSpawned = false;
        thresholdsPassed = 0;
        fired.clear();
        effects.add(new Effect.StartRoom(room.id()));
        Encounter encounter = room.encounter();
        effects.add(new Effect.Script(room.id(), "start"));
        switch (encounter.type()) {
            case NONE -> clear(effects, now);
            case WAVES -> scheduleWave(effects, 0, encounter.waves().get(0).delaySeconds(), now);
            case SURVIVE -> scheduleWave(effects, 0, 0, now);
            case BOSS -> {
                bossSpawned = true;
                effects.add(new Effect.SpawnBoss(room.id()));
                if (encounter.addsAtHealth().isEmpty() && !encounter.waves().isEmpty())
                    scheduleWave(effects, 0, encounter.waves().get(0).delaySeconds(), now);
            }
        }
        return effects;
    }

    /**
     * Called about once a second with the number of living mobs in the active room (boss excluded).
     * Releases due waves, fires timers, ends survive rooms and enforces the time limit.
     */
    public List<Effect> tick(long now, int alive) {
        List<Effect> effects = new ArrayList<>();
        if (phase != Phase.ACTIVE) return effects;
        if (elapsedSeconds(now) >= expedition.timeLimitMinutes() * 60L) {
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
        boolean released = false;
        if (pendingWave >= 0 && now >= nextWaveAt) {
            release(effects);
            released = true;
        }
        switch (encounter.type()) {
            case WAVES -> {
                if (!released && alive == 0 && pendingWave < 0 && waveIndex >= 0) {
                    int next = waveIndex + 1;
                    if (next < encounter.waves().size()) scheduleWave(effects, next, encounter.waves().get(next).delaySeconds(), now);
                    else clear(effects, now);
                }
            }
            case SURVIVE -> {
                if (roomSeconds >= encounter.surviveSeconds()) {
                    effects.add(new Effect.DespawnRoom(room.id()));
                    clear(effects, now);
                } else if (pendingWave < 0 && alive < SURVIVE_CROWD) {
                    int next = (waveIndex + 1) % encounter.waves().size();
                    int delay = encounter.waves().get(next).delaySeconds();
                    scheduleWave(effects, next, delay > 0 ? delay : SURVIVE_INTERVAL, now);
                }
            }
            default -> { }
        }
        return effects;
    }

    /** The boss's health changed; releases adds and fires health scripts as thresholds pass. */
    public List<Effect> bossHealth(double percent) {
        List<Effect> effects = new ArrayList<>();
        Room room = activeRoom();
        if (phase != Phase.ACTIVE || room == null || room.encounter().type() != EncounterType.BOSS) return effects;
        Encounter encounter = room.encounter();
        while (thresholdsPassed < encounter.addsAtHealth().size() && percent <= encounter.addsAtHealth().get(thresholdsPassed)) {
            if (!encounter.waves().isEmpty()) {
                int wave = thresholdsPassed % encounter.waves().size();
                effects.add(new Effect.SpawnWave(room.id(), wave));
                effects.add(new Effect.Script(room.id(), "wave-" + (wave + 1)));
            }
            thresholdsPassed++;
        }
        for (String trigger : encounter.scripts().keySet())
            if (trigger.startsWith("health-") && percent <= Integer.parseInt(trigger.substring(7)) && fired.add(trigger))
                effects.add(new Effect.Script(room.id(), trigger));
        return effects;
    }

    public List<Effect> bossDefeated(long now) {
        List<Effect> effects = new ArrayList<>();
        Room room = activeRoom();
        if (phase != Phase.ACTIVE || room == null || !bossSpawned) return effects;
        effects.add(new Effect.DespawnRoom(room.id()));
        clear(effects, now);
        return effects;
    }

    /** A script's {@code checkpoint} action. */
    public List<Effect> checkpointHere() {
        Room room = activeRoom();
        if (room == null) return List.of();
        checkpoint = room.id();
        return List.of(new Effect.Checkpoint(room.id()));
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

    private void wipe(List<Effect> effects) {
        Room room = activeRoom();
        if (room == null) return;
        states[current] = RoomState.WAITING;
        effects.add(new Effect.ResetRoom(room.id()));
        waveIndex = -1;
        nextWaveAt = -1;
        pendingWave = -1;
        bossSpawned = false;
    }

    private void scheduleWave(List<Effect> effects, int wave, int delaySeconds, long now) {
        pendingWave = wave;
        nextWaveAt = now + Math.max(0, delaySeconds) * 1000L;
        if (delaySeconds <= 0) release(effects);
    }

    private void release(List<Effect> effects) {
        Room room = rooms.get(current);
        waveIndex = pendingWave;
        pendingWave = -1;
        nextWaveAt = -1;
        effects.add(new Effect.SpawnWave(room.id(), waveIndex));
        effects.add(new Effect.Script(room.id(), "wave-" + (waveIndex + 1)));
    }

    private void clear(List<Effect> effects, long now) {
        Room room = rooms.get(current);
        states[current] = RoomState.CLEARED;
        nextWaveAt = -1;
        pendingWave = -1;
        effects.add(new Effect.ClearRoom(room.id()));
        effects.add(new Effect.Script(room.id(), "clear"));
        if (room.gate() != null) effects.add(new Effect.OpenGate(room.id()));
        if (room.checkpoint() != null) {
            checkpoint = room.id();
            effects.add(new Effect.Checkpoint(room.id()));
        }
        current++;
        if (current >= rooms.size()) {
            phase = Phase.COMPLETE;
            finishedSeconds = elapsedSeconds(now);
            boolean underPar = modifiers.contains(Modifier.TIMED) && finishedSeconds <= Modifier.parSeconds(expedition.timeLimitMinutes());
            effects.add(new Effect.Complete(finishedSeconds, underPar));
        }
    }

    private void fail(List<Effect> effects, String reason) {
        phase = Phase.FAILED;
        Room room = activeRoom();
        if (room != null) effects.add(new Effect.DespawnRoom(room.id()));
        effects.add(new Effect.Fail(reason));
    }
}
