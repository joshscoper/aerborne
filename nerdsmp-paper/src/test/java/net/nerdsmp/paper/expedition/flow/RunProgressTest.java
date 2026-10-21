package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionLoader;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.expedition.flow.RunProgress.Effect;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RunProgressTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();

    /** hall (pass-through) → gallery (2 waves, gate, checkpoint) → arena (survive 30s) → throne (boss with adds). */
    private static ExpeditionDefinition dungeon() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn", List.of(0, 64, 0));
        yaml.set("exit", List.of(0, 64, 95));
        yaml.set("time-limit-minutes", 10);
        yaml.set("rooms.hall.region", List.of(-5, 64, 5, 5, 70, 10));
        yaml.set("rooms.gallery.region", List.of(-5, 64, 11, 5, 70, 30));
        yaml.set("rooms.gallery.gate", List.of(-1, 64, 31, 1, 66, 31));
        yaml.set("rooms.gallery.checkpoint", List.of(0, 64, 29));
        yaml.set("rooms.gallery.points", List.of(List.of(0, 64, 20)));
        yaml.set("rooms.gallery.encounter.waves", List.of(java.util.Map.of("vanilla:zombie", 2),
            java.util.Map.of("mobs", java.util.Map.of("vanilla:skeleton", 2), "delay-seconds", 5)));
        yaml.set("rooms.gallery.encounter.scripts.timer-3", List.of("message hurry"));
        yaml.set("rooms.arena.region", List.of(-5, 64, 32, 5, 70, 60));
        yaml.set("rooms.arena.points", List.of(List.of(0, 64, 40)));
        yaml.set("rooms.arena.encounter.type", "survive");
        yaml.set("rooms.arena.encounter.survive-seconds", 30);
        yaml.set("rooms.arena.encounter.waves", List.of(java.util.Map.of("vanilla:zombie", 1)));
        yaml.set("rooms.throne.region", List.of(-5, 64, 61, 5, 70, 99));
        yaml.set("rooms.throne.points", List.of(List.of(0, 64, 80)));
        yaml.set("rooms.throne.encounter.boss", "custom:king");
        yaml.set("rooms.throne.encounter.adds-at-health", List.of(66, 33));
        yaml.set("rooms.throne.encounter.waves", List.of(java.util.Map.of("vanilla:skeleton", 2)));
        yaml.set("rooms.throne.encounter.scripts.health-50", List.of("message half"));
        return ExpeditionLoader.parse("test", yaml);
    }

    private static RunProgress run(Set<Modifier> modifiers) {
        return new RunProgress(dungeon(), Tier.DEFAULTS.get("normal"), modifiers, new LinkedHashSet<>(List.of(A, B)), 0);
    }

    private static <T extends Effect> List<T> of(List<Effect> effects, Class<T> type) {
        return effects.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test void roomsRunInOrderFromWavesToTheBossAndFinish() {
        RunProgress run = run(Set.of());
        assertTrue(run.enter("gallery", 0).isEmpty(), "Later rooms cannot start first");
        List<Effect> hall = run.enter("hall", 0);
        assertEquals(1, of(hall, Effect.ClearRoom.class).size(), "A room without an encounter clears on entry");
        assertEquals("gallery", run.nextRoom().id());

        List<Effect> start = run.enter("gallery", 1_000);
        assertEquals(List.of(new Effect.SpawnWave("gallery", 0)), of(start, Effect.SpawnWave.class));
        assertTrue(of(run.tick(2_000, 2), Effect.SpawnWave.class).isEmpty(), "Mobs still up");
        assertEquals(List.of(new Effect.Script("gallery", "timer-3")), of(run.tick(4_000, 2), Effect.Script.class));
        assertTrue(of(run.tick(5_000, 0), Effect.SpawnWave.class).isEmpty(), "Wave 2 waits for its delay");
        assertTrue(of(run.tick(9_000, 0), Effect.SpawnWave.class).isEmpty());
        assertEquals(List.of(new Effect.SpawnWave("gallery", 1)), of(run.tick(10_000, 0), Effect.SpawnWave.class));
        List<Effect> cleared = run.tick(11_000, 0);
        assertEquals(1, of(cleared, Effect.ClearRoom.class).size());
        assertEquals(List.of(new Effect.OpenGate("gallery")), of(cleared, Effect.OpenGate.class));
        assertEquals("gallery", run.checkpoint());

        run.enter("arena", 12_000);
        assertEquals(1, of(run.tick(13_000, 1), Effect.SpawnWave.class).size() + of(run.tick(28_000, 1), Effect.SpawnWave.class).size());
        List<Effect> survived = run.tick(42_000, 3);
        assertEquals(1, of(survived, Effect.DespawnRoom.class).size(), "Survive rooms sweep their leftovers");
        assertEquals(1, of(survived, Effect.ClearRoom.class).size());

        List<Effect> boss = run.enter("throne", 43_000);
        assertEquals(List.of(new Effect.SpawnBoss("throne")), of(boss, Effect.SpawnBoss.class));
        assertTrue(of(boss, Effect.SpawnWave.class).isEmpty(), "Adds wait for health thresholds");
        assertTrue(of(run.bossHealth(80), Effect.SpawnWave.class).isEmpty());
        List<Effect> both = run.bossHealth(30);
        assertEquals(2, of(both, Effect.SpawnWave.class).size(), "Dropping past two thresholds releases both");
        assertTrue(of(both, Effect.Script.class).contains(new Effect.Script("throne", "health-50")));
        assertTrue(of(run.bossHealth(20), Effect.Script.class).isEmpty(), "Health scripts fire once");
        List<Effect> finish = run.bossDefeated(100_000);
        assertEquals(RunProgress.Phase.COMPLETE, run.phase());
        assertEquals(List.of(new Effect.Complete(100, false)), of(finish, Effect.Complete.class));
    }

    @Test void deathsSpendLivesAndAWipeResetsTheRoom() {
        RunProgress run = run(Set.of());
        run.enter("hall", 0);
        run.enter("gallery", 0);
        assertEquals(List.of(new Effect.Lives(9, A)), run.death(A));
        run.respawned(A);
        assertTrue(of(run.death(B), Effect.ResetRoom.class).isEmpty(), "One player still standing");
        List<Effect> wipe = run.death(A);
        assertEquals(List.of(new Effect.ResetRoom("gallery")), of(wipe, Effect.ResetRoom.class));
        assertEquals(RunProgress.RoomState.WAITING, run.state("gallery"));
        run.respawned(A);
        run.respawned(B);
        assertEquals(1, of(run.enter("gallery", 5_000), Effect.SpawnWave.class).size(), "The room restarts from wave one");
        for (int death = 0; death < 7; death++) run.death(death % 2 == 0 ? A : B);
        assertEquals(RunProgress.Phase.FAILED, run.phase());
        assertEquals(0, run.lives());
    }

    @Test void leavingTheLastStandingPlayerCountsAsAWipe() {
        RunProgress run = run(Set.of());
        run.enter("hall", 0);
        run.enter("gallery", 0);
        run.death(A);
        assertEquals(List.of(new Effect.ResetRoom("gallery")), of(run.left(B), Effect.ResetRoom.class));
        assertEquals(Set.of(A), run.present());
    }

    @Test void theTimeLimitFailsTheRunAndTimedRewardsAFastClear() {
        RunProgress slow = run(Set.of());
        assertEquals(List.of(new Effect.Fail("Time ran out.")), of(slow.tick(600_000, 0), Effect.Fail.class));
        assertTrue(slow.enter("hall", 600_001).isEmpty(), "A failed run ignores further events");

        RunProgress fast = run(Set.of(Modifier.TIMED));
        fast.enter("hall", 0);
        fast.enter("gallery", 0);
        fast.tick(1_000, 0);
        fast.tick(6_000, 0);
        fast.tick(7_000, 0);
        fast.enter("arena", 8_000);
        fast.tick(39_000, 0);
        fast.enter("throne", 40_000);
        assertEquals(List.of(new Effect.Complete(60, true)), of(fast.bossDefeated(60_000), Effect.Complete.class));
    }

    @Test void abandoningAnActiveRunFailsIt() {
        RunProgress run = run(Set.of());
        assertEquals(1, of(run.abandon("Everyone left."), Effect.Fail.class).size());
        assertTrue(run.abandon("again").isEmpty());
    }
}
