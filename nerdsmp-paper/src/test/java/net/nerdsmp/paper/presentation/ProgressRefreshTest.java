package net.nerdsmp.paper.presentation;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class ProgressRefreshTest {
    @Test void delayedSnapshotCannotUndoAnAwardButRespecMetadataIsFresh() {
        var current = new ProgressionSnapshot(Map.of(Skill.MINING, new SkillProgress(400, 2)), Set.of(Skill.MINING), Set.of("old"), Instant.EPOCH);
        var fetched = new ProgressionSnapshot(Map.of(Skill.MINING, new SkillProgress(100, 1), Skill.FARMING, new SkillProgress(900, 3)), Set.of(), Set.of("new"), Instant.ofEpochSecond(99));
        var merged = PlayerPresentationService.mergeSkillProgress(fetched, current);
        assertEquals(400, merged.skills().get(Skill.MINING).experience());
        assertEquals(900, merged.skills().get(Skill.FARMING).experience());
        assertTrue(merged.masteries().isEmpty());
        assertEquals(Set.of("new"), merged.talents());
        assertEquals(fetched.nextRespecAt(), merged.nextRespecAt());
    }
    @Test void freshSessionUsesFetchedProgressWithoutOldState() {
        var fresh = ProgressionSnapshot.empty();
        assertSame(fresh, PlayerPresentationService.mergeSkillProgress(fresh, null));
    }
}
