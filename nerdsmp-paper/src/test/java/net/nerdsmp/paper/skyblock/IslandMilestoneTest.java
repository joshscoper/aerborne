package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class IslandMilestoneTest {
    @TempDir Path folder;
    final AccountId owner = AccountId.random(), member = AccountId.random();
    final LocalDate day = LocalDate.of(2026, 11, 14);
    PlayerDataStore data;
    IslandService islands;

    IslandDevelopment setup() {
        data = new PlayerDataStore(folder.toFile(), Logger.getAnonymousLogger());
        for (var id : List.of(owner, member)) data.bind(id.value(), id, new PlatformIdentity(PlatformIdentity.Platform.JAVA, id.toString()), "Test");
        islands = new IslandService(data, "test");
        islands.create(owner);
        islands.invite(owner, member, Instant.now());
        islands.accept(member, owner, Instant.now());
        return new IslandDevelopment(data, islands, "test", () -> day);
    }

    static IslandDevelopment.Progress at(int level) {
        return new IslandDevelopment.Progress(IslandDevelopment.experienceForLevel(level), LocalDate.of(2026, 11, 14), 0, Map.of());
    }

    @Test void everyFiveLevelsWithRisingRewards() {
        long previous = 0;
        for (int i = 0; i < IslandMilestone.values().length; i++) {
            IslandMilestone milestone = IslandMilestone.values()[i];
            assertEquals((i + 1) * IslandMilestone.STEP, milestone.level());
            assertTrue(milestone.marks() > previous);
            previous = milestone.marks();
            assertEquals(milestone, IslandMilestone.parse(milestone.id()));
        }
        assertEquals(IslandDevelopment.MAX_LEVEL, IslandMilestone.values()[IslandMilestone.values().length - 1].level());
        assertEquals(IslandMilestone.HOMEBOUND, IslandMilestone.next(1).orElseThrow());
        assertEquals(IslandMilestone.STEADY_HANDS, IslandMilestone.next(5).orElseThrow());
        assertTrue(IslandMilestone.next(50).isEmpty());
        assertEquals(3, IslandMilestone.reachedBy(17).size());
        assertTrue(IslandMilestone.at(25).orElseThrow().booster());
    }

    @Test void perksFeedTheNumbersTheGameReads() {
        assertEquals(0D, at(14).skillBonus(Skill.FARMING));
        assertEquals(5D, at(15).skillBonus(Skill.FARMING));
        assertEquals(10D, at(25).skillBonus(Skill.FARMING));
        assertEquals(15D, at(50).skillBonus(Skill.FARMING));
        assertEquals(0, at(34).hopperLimit());
        assertEquals(2, at(35).hopperLimit());
        assertEquals(8 + 16, at(35).hopperBatch());
        assertEquals(10, IslandMilestone.cropGrowthPercent(20));
        assertEquals(0, IslandMilestone.oreRerollPercent(44));
        assertEquals(10, IslandMilestone.oreRerollPercent(45));
    }

    @Test void multiplierRaisesIslandXpButNotTheDailyCap() {
        var service = setup();
        service.xpMultiplier(() -> 2D);
        service.record(member, owner, 20);
        service.flush();
        assertEquals(40, service.progress(owner).xp());
        assertEquals(20, service.progress(owner).earnedToday());
        // The 25-per-action limit applies before doubling.
        service.record(member, owner, 100);
        service.flush();
        assertEquals(90, service.progress(owner).xp());
        for (int n = 0; n < 1000; n++) service.record(member, owner, 25);
        service.flush();
        assertEquals(IslandDevelopment.DAILY_XP_CAP, service.progress(owner).earnedToday());
        assertEquals(2L * IslandDevelopment.DAILY_XP_CAP, service.progress(owner).xp());
    }

    @Test void levelUpsAreReportedOnceAfterSaving() {
        var service = setup();
        List<int[]> seen = new ArrayList<>();
        service.onLevelUp((island, from, to) -> seen.add(new int[] {from, to}));
        service.record(owner, owner, 25);
        service.flush();
        assertTrue(seen.isEmpty());
        for (int n = 0; n < 40; n++) service.record(owner, owner, 25);
        service.flush();
        assertEquals(1, seen.size());
        assertEquals(1, seen.getFirst()[0]);
        assertEquals(IslandDevelopment.level(service.progress(owner).xp()), seen.getFirst()[1]);
    }
}
