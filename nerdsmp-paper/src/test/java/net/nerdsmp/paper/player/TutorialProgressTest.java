package net.nerdsmp.paper.player;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class TutorialProgressTest {
    @TempDir Path directory;
    final AccountId actor = new AccountId(UUID.randomUUID());
    TutorialProgress progress() { return new TutorialProgress(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger())); }
    @Test void checkpointsPersistAndResumeAtFirstUnreadLesson() {
        var p = progress(); p.acknowledge(actor, "welcome"); p.acknowledge(actor, "equipment"); p.pause(actor, true);
        var loaded = progress().state(actor); assertEquals(2, loaded.read().size()); assertEquals(1, loaded.next()); assertTrue(loaded.paused()); assertTrue(loaded.invited());
        assertTrue(progress().state(new AccountId(UUID.randomUUID())).read().isEmpty());
    }
    @Test void finishingAndReplayingAreIdempotentAndRestartIsScoped() {
        var p = progress(); TutorialProgress.LESSONS.forEach(id -> p.acknowledge(actor, id)); p.acknowledge(actor, "equipment"); assertTrue(progress().state(actor).complete());
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()); data.update(actor, yaml -> yaml.set("equipment.test", "keep"));
        new TutorialProgress(data).restart(actor); assertFalse(progress().state(actor).complete()); assertEquals("keep", data.read(actor, yaml -> yaml.getString("equipment.test")));
    }
    @Test void failedSaveDoesNotAdvanceProgress() throws Exception {
        var p = progress(); p.invite(actor); Files.createDirectory(directory.resolve("player_data/" + actor + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> p.acknowledge(actor, "welcome")); assertEquals(0, p.state(actor).next());
        assertThrows(IllegalStateException.class, () -> p.pause(actor, true)); assertFalse(p.state(actor).paused());
    }
    @Test void unknownLessonCannotCountTowardCompletion() {
        assertThrows(IllegalArgumentException.class, () -> progress().acknowledge(actor, "fake"));
        assertEquals(0, new TutorialProgress.State(java.util.Set.of("fake"), false, false).read().size());
    }
    @Test void linkedClientsShareCanonicalAccountProgress() {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        UUID primary = UUID.randomUUID(), linked = UUID.randomUUID();
        data.bind(primary, actor, new net.nerdsmp.api.model.PlatformIdentity(net.nerdsmp.api.model.PlatformIdentity.Platform.JAVA, primary.toString()), "Primary");
        new TutorialProgress(data).acknowledge(actor, "equipment");
        data.bind(linked, actor, new net.nerdsmp.api.model.PlatformIdentity(net.nerdsmp.api.model.PlatformIdentity.Platform.BEDROCK, linked.toString()), "Linked");
        assertTrue(new TutorialProgress(data).state(actor).read().contains("equipment"));
        assertEquals(java.util.List.of("equipment"), data.read(primary, yaml -> yaml.getStringList("tutorial.v1.read")));
    }
    @Test void currentTipsPreserveCustomMessagesDeduplicateAndAllowOperatorsToDisableAdditions() {
        var tips = PlayerGuideService.currentTips(java.util.List.of("Custom operator message"), true, true);
        assertTrue(tips.contains("Custom operator message")); assertEquals(8, tips.size());
        assertEquals(tips, PlayerGuideService.currentTips(tips, true, true));
        assertEquals(java.util.List.of("Custom"), PlayerGuideService.currentTips(java.util.List.of("Custom"), false, false));
        assertFalse(PlayerGuideService.currentTips(java.util.List.of(), true, false).stream().anyMatch(t -> t.contains("https://")));
    }
}
