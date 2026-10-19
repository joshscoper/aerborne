package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

final class IslandDevelopmentTest {
    @TempDir Path folder;
    final AccountId owner = AccountId.random(), member = AccountId.random(), guest = AccountId.random();
    final AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.of(2026, 9, 29));
    PlayerDataStore data; IslandService islands;
    IslandDevelopment setup() {
        data = new PlayerDataStore(folder.toFile(), Logger.getAnonymousLogger());
        for (var id : List.of(owner, member, guest)) data.bind(id.value(), id, new PlatformIdentity(PlatformIdentity.Platform.JAVA, id.toString()), "Test");
        islands = new IslandService(data, "test"); islands.create(owner); islands.invite(owner, member, Instant.now()); islands.accept(member, owner, Instant.now());
        return new IslandDevelopment(data, islands, "test", day::get);
    }
    void seed(int level) { data.update(owner, yaml -> { String p = "seasons.test.island-development"; yaml.set(p + ".schema", 1); yaml.set(p + ".xp", IslandDevelopment.experienceForLevel(level)); yaml.set(p + ".day", day.get().toString()); yaml.set(p + ".earned-today", 0); }); }
    @Test void curveHasExactBoundariesAndTenPointsAtCap() {
        for (int level = 2; level <= 50; level++) {
            long xp = IslandDevelopment.experienceForLevel(level); assertEquals(level - 1, IslandDevelopment.level(xp - 1)); assertEquals(level, IslandDevelopment.level(xp));
        }
        assertEquals(25, new IslandDevelopment.Progress(IslandDevelopment.experienceForLevel(50), day.get(), 0, Map.of()).availablePoints());
        assertEquals(50, IslandDevelopment.level(Long.MAX_VALUE));
    }
    @Test void coOpWorkIsBufferedCappedAndSeasonal() {
        var service = setup(); service.record(guest, owner, 1000); service.flush(); assertEquals(0, service.progress(owner).xp());
        for (int n = 0; n < 500; n++) service.record(member, owner, 1000);
        assertEquals(0, service.progress(owner).xp()); service.flush(); assertEquals(10_000, service.progress(owner).xp());
        service.record(owner, owner, 25); service.flush(); assertEquals(10_000, service.progress(owner).xp());
        day.set(day.get().plusDays(1)); service.record(owner, owner, 10); service.close(); assertEquals(10_010, service.progress(owner).xp());
        var reloaded = new IslandDevelopment(data, islands, "test", day::get); assertEquals(service.progress(owner), reloaded.progress(owner));
        assertEquals(0, new IslandDevelopment(data, islands, "another", day::get).progress(owner).xp());
    }
    @Test void kickedMembersCannotQueueMoreXp() {
        var service = setup(); service.record(member, owner, 10); islands.remove(owner, owner, member); service.record(member, owner, 25); service.flush();
        assertEquals(10, service.progress(owner).xp());
    }
    @Test void ownerOnlyPrerequisitesStaleMenusAndPointBudget() {
        setup(); seed(12); var service = new IslandDevelopment(data, islands, "test", day::get);
        assertThrows(IllegalArgumentException.class, () -> service.learn(member, owner, IslandTalent.STACKING, 0));
        assertThrows(IllegalArgumentException.class, () -> service.learn(owner, owner, IslandTalent.LOGISTICS, 0));
        service.learn(owner, owner, IslandTalent.STACKING, 0);
        assertThrows(IllegalArgumentException.class, () -> service.learn(owner, owner, IslandTalent.STACKING, 0));
        service.learn(owner, owner, IslandTalent.STACKING, 1); service.learn(owner, owner, IslandTalent.LOGISTICS, 0); service.learn(owner, owner, IslandTalent.LOGISTICS, 1);
        assertEquals(8, service.progress(owner).stackLimit()); assertEquals(4, service.progress(owner).hopperLimit()); assertEquals(32, service.progress(owner).hopperBatch());
        service.learn(owner, owner, IslandTalent.LOGISTICS, 2);
        service.learn(owner, owner, IslandTalent.STACKING, 2);
        assertEquals(0, service.progress(owner).availablePoints());
        assertThrows(IllegalArgumentException.class, () -> service.learn(owner, owner, IslandTalent.FISHERY, 0));
        assertEquals(service.progress(owner), new IslandDevelopment(data, islands, "test", day::get).progress(owner));
    }
    @Test void failedFlushRetainsBufferAndDoesNotPublish() throws Exception {
        var service = setup(); service.record(owner, owner, 25);
        Path file = data.directory().toPath().resolve(owner + ".yml"); Files.delete(file); Files.createDirectory(file); Files.createFile(file.resolve("occupied"));
        assertThrows(IllegalStateException.class, service::flush); assertEquals(0, service.progress(owner).xp());
        Files.delete(file.resolve("occupied")); Files.delete(file); service.flush(); assertEquals(25, service.progress(owner).xp());
    }
    @Test void corruptOrOverspentTalentsFailClosed() {
        setup(); seed(5); data.update(owner, yaml -> yaml.set("seasons.test.island-development.talents.stacking", 3));
        assertThrows(IllegalStateException.class, () -> new IslandDevelopment(data, islands, "test", day::get));
        assertThrows(IllegalArgumentException.class, () -> new IslandDevelopment.Progress(1000, day.get(), 0, Map.of(IslandTalent.LOGISTICS, 1)));
    }
    @Test void failedUpgradeKeepsOldRank() throws Exception {
        setup(); seed(10); var service = new IslandDevelopment(data, islands, "test", day::get);
        Path file = data.directory().toPath().resolve(owner + ".yml"); Files.delete(file); Files.createDirectory(file); Files.createFile(file.resolve("occupied"));
        assertThrows(IllegalStateException.class, () -> service.learn(owner, owner, IslandTalent.STACKING, 0));
        assertEquals(0, service.progress(owner).rank(IslandTalent.STACKING)); assertEquals(5, service.progress(owner).availablePoints());
    }
    @Test void fourSpecializationsFitTheMenuAndOfferRealTradeoffsWithoutResettingOldRanks() {
        Set<Integer> occupied=new HashSet<>(Set.of(4,45,49));
        for(var talent:IslandTalent.values()) { assertTrue(occupied.add(talent.menuSlot())); assertTrue(talent.menuSlot()<45); }
        assertEquals(16,IslandTalent.values().length); assertEquals(48,IslandTalent.values().length*IslandTalent.MAX_RANK);
        var legacy=new IslandDevelopment.Progress(IslandDevelopment.experienceForLevel(50),day.get(),0,Map.of(IslandTalent.STACKING,3,IslandTalent.LOGISTICS,3));
        assertEquals(19,legacy.availablePoints()); assertEquals(16,legacy.stackLimit()); assertEquals(8,legacy.hopperLimit());
    }
    @Test void newTalentsRequireLevelAndHaveRuntimeEffectsThatSurviveRestart() {
        setup();seed(50);var service=new IslandDevelopment(data,islands,"test",day::get);
        for(var talent:List.of(IslandTalent.STACKING,IslandTalent.LOGISTICS,IslandTalent.WORKSHOP,IslandTalent.STORAGE_NETWORK))
            for(int rank=0;rank<3;rank++) service.learn(owner,owner,talent,rank);
        var progress=service.progress(owner); assertEquals(14,progress.hopperLimit());assertEquals(112,progress.hopperBatch()); assertEquals(7,progress.machineCycleSeconds(10));
        assertEquals(progress,new IslandDevelopment(data,islands,"test",day::get).progress(owner));
    }
    @Test void generatorPreferenceNeverCreatesAnOreOutsideItsRolls() {
        for (int tier = 0; tier <= 3; tier++) for (int a = 0; a < 100; a++) for (int b = 0; b < 100; b++) {
            var first = GeneratorLoot.roll(tier, a); var second = GeneratorLoot.roll(tier, b); var selected = GeneratorLoot.rarer(first, second);
            assertTrue(selected == first || selected == second);
        }
    }
}
