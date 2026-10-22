package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionLoader;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.expedition.flow.RunProgress.Effect;
import net.nerdsmp.paper.expedition.flow.RunProgress.Objective;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Clear types, puzzles, councils, phases, enrage, forces, timed bonuses and hub maps. */
class RunObjectivesTest {
    private static final UUID A = UUID.randomUUID();

    private static YamlConfiguration base() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn", List.of(0, 64, 0));
        yaml.set("exit", List.of(0, 64, 2));
        yaml.set("time-limit-minutes", 10);
        return yaml;
    }

    private static void room(YamlConfiguration yaml, String id, int z) {
        yaml.set("rooms." + id + ".region", List.of(-5, 64, z, 5, 70, z + 9));
        yaml.set("rooms." + id + ".points", List.of(List.of(0, 64, z + 2), List.of(2, 64, z + 2), List.of(-2, 64, z + 2)));
    }

    private static RunProgress start(YamlConfiguration yaml) {
        ExpeditionDefinition definition = ExpeditionLoader.parse("test", yaml);
        return new RunProgress(definition, Tier.DEFAULTS.get("normal"), Set.of(), new LinkedHashSet<>(List.of(A)), 0);
    }

    private static <T extends Effect> List<T> of(List<Effect> effects, Class<T> type) {
        return effects.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test void aRoomWithAPuzzleClearsOnlyOnceTheFightIsWonAndThePuzzleSolved() {
        YamlConfiguration yaml = base();
        room(yaml, "vault", 10);
        yaml.set("rooms.vault.encounter.waves", List.of(Map.of("vanilla:zombie", 2)));
        yaml.set("rooms.vault.puzzles.levers.type", "levers");
        yaml.set("rooms.vault.puzzles.levers.blocks", List.of(List.of(-4, 65, 12), List.of(4, 65, 12)));
        yaml.set("rooms.vault.encounter.scripts.solved-levers", List.of("message click"));
        RunProgress run = start(yaml);
        run.enter("vault", 0);
        assertTrue(of(run.tick(1_000, 0), Effect.ClearRoom.class).isEmpty(), "Waves done, puzzle not");
        assertEquals("Pull the 2 levers", run.objectives(1_000).get(1).text());
        List<Effect> solved = run.solve("vault", "levers", 2_000);
        assertTrue(solved.contains(new Effect.Script("vault", "solved-levers")));
        assertEquals(1, of(solved, Effect.Complete.class).size());
        assertTrue(run.solve("vault", "levers", 3_000).isEmpty(), "Solved once");
    }

    @Test void aPuzzleSolvedMidFightWaitsForTheFight() {
        YamlConfiguration yaml = base();
        room(yaml, "vault", 10);
        yaml.set("rooms.vault.encounter.waves", List.of(Map.of("vanilla:zombie", 2)));
        yaml.set("rooms.vault.puzzles.reach.type", "reach");
        yaml.set("rooms.vault.puzzles.reach.area", List.of(-1, 64, 18, 1, 66, 19));
        RunProgress run = start(yaml);
        run.enter("vault", 0);
        assertTrue(of(run.solve("vault", "reach", 500), Effect.ClearRoom.class).isEmpty());
        assertEquals(1, of(run.tick(1_000, 0), Effect.ClearRoom.class).size());
    }

    @Test void clearRoomsReleaseEveryWaveAtOnce() {
        YamlConfiguration yaml = base();
        room(yaml, "cave", 10);
        yaml.set("rooms.cave.encounter.type", "clear");
        yaml.set("rooms.cave.encounter.waves", List.of(Map.of("vanilla:zombie", 2), Map.of("mobs", Map.of("vanilla:spider", 2), "delay-seconds", 30)));
        RunProgress run = start(yaml);
        assertEquals(2, of(run.enter("cave", 0), Effect.SpawnWave.class).size());
        assertTrue(of(run.tick(1_000, 3), Effect.ClearRoom.class).isEmpty());
        assertEquals(1, of(run.tick(2_000, 0), Effect.ClearRoom.class).size());
    }

    @Test void aCouncilFallsBossByBossWithDownScripts() {
        YamlConfiguration yaml = base();
        room(yaml, "hall", 10);
        yaml.set("rooms.hall.encounter.bosses", List.of("custom:a", "custom:b", "custom:c"));
        RunProgress run = start(yaml);
        List<Effect> begin = run.enter("hall", 0);
        assertEquals(List.of(new Effect.SpawnBoss("hall", 0), new Effect.SpawnBoss("hall", 1), new Effect.SpawnBoss("hall", 2)), of(begin, Effect.SpawnBoss.class));
        assertEquals(List.of(new Effect.Script("hall", "down-1")), of(run.bossDefeated(1, 1_000), Effect.Script.class));
        assertTrue(run.bossDefeated(1, 1_100).isEmpty(), "The same boss can't fall twice");
        assertTrue(run.objectives(1_200).getFirst().text().endsWith("1/3 down"));
        run.bossDefeated(0, 2_000);
        List<Effect> last = run.bossDefeated(2, 3_000);
        assertTrue(last.contains(new Effect.Script("hall", "down-3")));
        assertEquals(1, of(last, Effect.Complete.class).size());
    }

    @Test void aLinkedCouncilMemberRisesAgainUnlessTheRestFallInTime() {
        YamlConfiguration yaml = base();
        room(yaml, "den", 10);
        yaml.set("rooms.den.encounter.bosses", List.of("custom:a", "custom:b"));
        yaml.set("rooms.den.encounter.link-seconds", 10);
        yaml.set("rooms.den.encounter.phases", List.of(Map.of("at", 40, "abilities", List.of("slam"), "submerge-seconds", 20)));
        RunProgress run = start(yaml);
        assertEquals(10, run.expedition().rooms().get("den").encounter().linkSeconds());
        assertEquals(20, run.expedition().rooms().get("den").encounter().phases().getFirst().submergeSeconds());
        run.enter("den", 0);
        run.bossDefeated(0, 1_000);
        assertTrue(of(run.tick(10_000, 0), Effect.ReviveBoss.class).isEmpty(), "Still inside the window");
        List<Effect> late = run.tick(11_500, 0);
        assertEquals(List.of(new Effect.ReviveBoss("den", 0)), of(late, Effect.ReviveBoss.class));
        assertTrue(late.contains(new Effect.Script("den", "revive")));
        assertTrue(run.objectives(12_000).getFirst().text().endsWith("0/2 down"));
        // Together within the window: the room clears and nothing rises.
        run.bossDefeated(1, 20_000);
        List<Effect> both = run.bossDefeated(0, 25_000);
        assertEquals(1, of(both, Effect.Complete.class).size());
        assertTrue(of(run.tick(40_000, 0), Effect.ReviveBoss.class).isEmpty());
    }

    @Test void anUnlinkedCouncilNeverRevives() {
        YamlConfiguration yaml = base();
        room(yaml, "den", 10);
        yaml.set("rooms.den.encounter.bosses", List.of("custom:a", "custom:b"));
        RunProgress run = start(yaml);
        run.enter("den", 0);
        run.bossDefeated(0, 1_000);
        assertTrue(of(run.tick(120_000, 0), Effect.ReviveBoss.class).isEmpty());
    }

    @Test void bossPhasesFireOnceInOrderAndEnrageFollowsTheTimer() {
        YamlConfiguration yaml = base();
        room(yaml, "throne", 10);
        yaml.set("rooms.throne.encounter.boss", "custom:king");
        yaml.set("rooms.throne.encounter.enrage-seconds", 60);
        yaml.set("rooms.throne.encounter.phases", List.of(Map.of("at", 30, "abilities", List.of("meteor")), Map.of("at", 70, "abilities", List.of("slam"))));
        RunProgress run = start(yaml);
        run.enter("throne", 0);
        assertEquals(List.of(new Effect.BossPhase("throne", 1)), of(run.bossHealth(65), Effect.BossPhase.class));
        assertTrue(of(run.bossHealth(60), Effect.BossPhase.class).isEmpty());
        assertEquals(List.of(new Effect.BossPhase("throne", 2)), of(run.bossHealth(20), Effect.BossPhase.class));
        assertTrue(of(run.tick(59_000, 0), Effect.Enrage.class).isEmpty());
        assertEquals(List.of(new Effect.Enrage("throne")), of(run.tick(60_000, 0), Effect.Enrage.class));
        assertTrue(of(run.tick(61_000, 0), Effect.Enrage.class).isEmpty(), "Enrages once");
    }

    @Test void forcesRunsHoldTheFinalRoomUntilEnoughEnemiesFall() {
        YamlConfiguration yaml = base();
        yaml.set("goal", "forces");
        yaml.set("forces", 10);
        room(yaml, "halls", 10);
        yaml.set("rooms.halls.encounter.waves", List.of(Map.of("vanilla:zombie", 2)));
        room(yaml, "throne", 20);
        yaml.set("rooms.throne.encounter.boss", "custom:king");
        RunProgress run = start(yaml);
        run.enter("halls", 0);
        run.tick(1_000, 0);
        assertFalse(run.startable("throne"), "Forces not reached");
        run.killed(4);
        assertTrue(of(run.killed(9), Effect.ForcesComplete.class).size() == 1);
        assertEquals(10, run.forces(), "Capped at the target");
        assertTrue(run.startable("throne"));
        assertEquals(Objective.Status.DONE, run.objectives(2_000).getFirst().status());
    }

    @Test void bossGoalsLetWingsRunInAnyOrderAndSkipOtherRooms() {
        YamlConfiguration yaml = base();
        yaml.set("goal", "bosses");
        room(yaml, "hub", 10);
        yaml.set("rooms.hub.encounter.waves", List.of(Map.of("vanilla:zombie", 1)));
        for (String wing : List.of("east", "west")) {
            room(yaml, wing, wing.equals("east") ? 20 : 30);
            yaml.set("rooms." + wing + ".requires", List.of());
            yaml.set("rooms." + wing + ".encounter.boss", "custom:" + wing);
        }
        RunProgress run = start(yaml);
        assertTrue(run.startable("west") && run.startable("east") && run.startable("hub"));
        run.enter("west", 0);
        assertFalse(run.startable("east"), "One room at a time");
        run.bossDefeated(1_000);
        assertTrue(run.startable("east"));
        run.enter("east", 2_000);
        assertEquals(1, of(run.bossDefeated(3_000), Effect.Complete.class).size(), "The hub fight isn't needed");
    }

    @Test void timedRunsGainTimeForEachClearedRoom() {
        YamlConfiguration yaml = base();
        yaml.set("goal", "timed");
        yaml.set("time-limit-minutes", 5);
        yaml.set("time-bonus-seconds", 45);
        room(yaml, "one", 10);
        yaml.set("rooms.one.encounter.waves", List.of(Map.of("vanilla:zombie", 1)));
        room(yaml, "two", 20);
        yaml.set("rooms.two.encounter.waves", List.of(Map.of("vanilla:zombie", 1)));
        RunProgress run = start(yaml);
        run.enter("one", 0);
        assertEquals(List.of(new Effect.TimeBonus(45)), of(run.tick(60_000, 0), Effect.TimeBonus.class));
        assertEquals(285, run.secondsLeft(60_000));
        assertTrue(of(run.tick(301_000, 0), Effect.Fail.class).isEmpty(), "The bonus kept the run alive past five minutes");
    }

    @Test void campfiresBecomeTheCheckpointAndOptionalRoomsArentNeeded() {
        YamlConfiguration yaml = base();
        room(yaml, "main", 10);
        yaml.set("rooms.main.encounter.waves", List.of(Map.of("vanilla:zombie", 1)));
        room(yaml, "side", 20);
        yaml.set("rooms.side.optional", true);
        yaml.set("rooms.side.requires", List.of());
        yaml.set("rooms.side.encounter.waves", List.of(Map.of("vanilla:zombie", 1)));
        RunProgress run = start(yaml);
        assertEquals(List.of(new Effect.Campfire("mouth")), run.campfire("mouth"));
        assertEquals(RunProgress.CAMPFIRE + "mouth", run.checkpoint());
        run.enter("main", 0);
        assertEquals(1, of(run.tick(1_000, 0), Effect.Complete.class).size());
    }

    @Test void aWipeForgetsTheRoomsSolvedPuzzles() {
        YamlConfiguration yaml = base();
        room(yaml, "vault", 10);
        yaml.set("rooms.vault.encounter.waves", List.of(Map.of("vanilla:zombie", 2)));
        yaml.set("rooms.vault.puzzles.levers.blocks", List.of(List.of(-4, 65, 12)));
        RunProgress run = start(yaml);
        run.enter("vault", 0);
        run.solve("vault", "levers", 100);
        run.death(A);
        assertTrue(run.solved().isEmpty());
        assertTrue(run.startable("vault"));
    }
}
