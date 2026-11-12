package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionSaveFailureTest {
    @TempDir Path directory;
    @Test void failedXpSaveCanRetrySameOperationAndSurvivesRestartExactlyOnce() throws Exception {
        var store = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        var service = new InMemoryProgressionService(store, "test"); var id = AccountId.random();
        service.snapshot(id).toCompletableFuture().join();
        Path file = store.directory().toPath().resolve(id + ".yml");
        block(file);
        assertThrows(IllegalStateException.class, () -> service.awardExperience(id, Skill.HUNTING, 100, "kill:1"));
        assertEquals(0, service.snapshot(id).toCompletableFuture().join().skills().get(Skill.HUNTING).experience());
        unblock(file);
        assertTrue(service.awardExperience(id, Skill.HUNTING, 100, "kill:1").toCompletableFuture().join().awarded());
        var restarted = new InMemoryProgressionService(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), "test");
        assertFalse(restarted.awardExperience(id, Skill.HUNTING, 100, "kill:1").toCompletableFuture().join().awarded());
        assertEquals(100, restarted.snapshot(id).toCompletableFuture().join().skills().get(Skill.HUNTING).experience());
    }
    @Test void masteryTalentAndRespecArePublishedOnlyAfterSaveSucceeds() throws Exception {
        var store = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        var service = new InMemoryProgressionService(store, "test"); var id = AccountId.random();
        service.awardExperience(id, Skill.MINING, ProgressionService.experienceForLevel(30), "eligible").toCompletableFuture().join();
        Path file = store.directory().toPath().resolve(id + ".yml"), backup = directory.resolve("backup.yml");
        Files.move(file, backup); block(file);
        assertThrows(IllegalStateException.class, () -> service.selectMastery(id, Skill.MINING));
        assertThrows(IllegalStateException.class, () -> service.unlockTalent(id, "mining.ore_study.1"));
        assertTrue(service.snapshot(id).toCompletableFuture().join().masteries().isEmpty());
        assertTrue(service.snapshot(id).toCompletableFuture().join().talents().isEmpty());
        unblock(file); Files.move(backup, file);
        assertEquals(ProgressionService.MasteryResult.SELECTED, service.selectMastery(id, Skill.MINING).toCompletableFuture().join());
        Files.move(file, backup); block(file);
        assertThrows(IllegalStateException.class, () -> service.removeMastery(id, Skill.MINING, Instant.now()));
        assertTrue(service.snapshot(id).toCompletableFuture().join().masteries().contains(Skill.MINING));
        unblock(file); Files.move(backup, file);
        assertEquals(ProgressionService.MasteryResult.REMOVED, service.removeMastery(id, Skill.MINING, Instant.now()).toCompletableFuture().join());
    }
    private void block(Path path) throws Exception { Files.createDirectory(path); Files.createFile(path.resolve("keep")); }
    private void unblock(Path path) throws Exception { Files.delete(path.resolve("keep")); Files.delete(path); }
}
