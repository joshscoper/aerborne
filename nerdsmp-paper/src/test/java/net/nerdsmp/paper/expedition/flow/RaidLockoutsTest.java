package net.nerdsmp.paper.expedition.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidLockoutsTest {
    @TempDir Path folder;

    @Test void eachBossPaysOncePerTierPerWeek() {
        RaidLockouts lockouts = new RaidLockouts(null, 40);
        assertTrue(lockouts.claim("a", "cinder_keep", "normal", "kilnbreaker", 40));
        assertFalse(lockouts.claim("a", "cinder_keep", "normal", "kilnbreaker", 40));
        assertTrue(lockouts.claim("a", "cinder_keep", "heroic", "kilnbreaker", 40), "Tiers lock separately");
        assertTrue(lockouts.claim("b", "cinder_keep", "normal", "kilnbreaker", 40), "Players lock separately");
        assertTrue(lockouts.claim("a", "cinder_keep", "normal", "kilnbreaker", 41), "A new week resets");
        assertEquals(Set.of("kilnbreaker"), lockouts.claimed("a", "cinder_keep", "normal", 40));
    }

    @Test void lockoutsSurviveARestartButOldWeeksAreDropped() {
        File file = folder.resolve("raid-lockouts.yml").toFile();
        RaidLockouts lockouts = new RaidLockouts(file, 40);
        lockouts.claim("a", "cinder_keep", "normal", "kilnbreaker", 40);
        lockouts.claim("a", "cinder_keep", "normal", RaidLockouts.FINISH, 40);
        assertEquals(Set.of("kilnbreaker", RaidLockouts.FINISH), new RaidLockouts(file, 40).claimed("a", "cinder_keep", "normal", 40));
        RaidLockouts later = new RaidLockouts(file, 41);
        assertTrue(later.claimed("a", "cinder_keep", "normal", 40).isEmpty());
        assertTrue(later.claim("a", "cinder_keep", "normal", "kilnbreaker", 41));
    }
}
