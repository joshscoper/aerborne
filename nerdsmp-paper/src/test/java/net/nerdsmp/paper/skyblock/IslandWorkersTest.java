package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class IslandWorkersTest {
    static final long HOUR = 3_600_000L;

    @Test void workersGatherOverTimeAndStopWhenFull() {
        var fresh = new IslandWorkers.Worker(IslandWorkers.Type.WHEAT, 1, 0, 0);
        var oneHour = IslandWorkers.work(fresh, HOUR);
        assertEquals(60, oneHour.stored());
        var later = IslandWorkers.work(oneHour, HOUR + HOUR / 2);
        assertEquals(90, later.stored());
        // A day away fills the store to eight hours and no more.
        var full = IslandWorkers.work(fresh, 24 * HOUR);
        assertEquals(IslandWorkers.Type.WHEAT.capacity(1), full.stored());
        assertEquals(480, full.stored());
    }

    @Test void slowWorkersKeepTheirPartialProgress() {
        var diamonds = new IslandWorkers.Worker(IslandWorkers.Type.DIAMOND, 1, 0, 0);
        // 1.5 an hour: nothing after 30 minutes, but the clock doesn't move, so the hour still pays one.
        var half = IslandWorkers.work(diamonds, HOUR / 2);
        assertEquals(0, half.stored());
        assertEquals(0, half.since());
        assertEquals(1, IslandWorkers.work(half, HOUR).stored());
        assertEquals(3, IslandWorkers.work(diamonds, 2 * HOUR).stored());
    }

    @Test void tiersAndVarietyPay() {
        assertTrue(IslandWorkers.Type.IRON.rate(5) > IslandWorkers.Type.IRON.rate(1) * 3);
        assertEquals(3, IslandWorkers.slots(0));
        assertEquals(4, IslandWorkers.slots(4));
        assertEquals(IslandWorkers.MAX_SLOTS, IslandWorkers.slots(500));
        assertTrue(IslandWorkers.Type.EMERALD.marks(1) > IslandWorkers.Type.WHEAT.marks(1));
        for (var type : IslandWorkers.Type.values()) {
            assertTrue(type.cost(1) >= 2 && type.cost(5) <= 512);
            assertEquals(type, IslandWorkers.decode(IslandWorkers.encode(new IslandWorkers.Worker(type, 3, 17, 1234))).type());
        }
        assertEquals(Material.COBBLESTONE, IslandWorkers.Type.QUARRY.product());
    }

    @Test void prestigeTakesXpPastLevelFifty() throws Exception {
        Path folder = java.nio.file.Files.createTempDirectory("prestige");
        var data = new PlayerDataStore(folder.toFile(), Logger.getAnonymousLogger());
        AccountId owner = AccountId.random();
        data.bind(owner.value(), owner, new PlatformIdentity(PlatformIdentity.Platform.JAVA, owner.toString()), "Test");
        var islands = new IslandService(data, "test");
        islands.create(owner);
        data.update(owner, yaml -> { String p = "seasons.test.island-development"; yaml.set(p + ".schema", 1);
            yaml.set(p + ".xp", IslandDevelopment.experienceForLevel(50) - 10); yaml.set(p + ".day", "2026-11-14"); yaml.set(p + ".earned-today", 0); });
        var development = new IslandDevelopment(data, islands, "test", () -> LocalDate.of(2026, 11, 14));
        development.grant(owner, 1_000);
        development.flush();
        assertEquals(50, development.progress(owner).level());
        assertEquals(990, development.prestigeXp(owner));
        assertEquals(0, development.stars(owner));
        development.grant(owner, 2_000);
        for (int n = 0; n < 12; n++) development.grant(owner, 2_000);
        development.flush();
        assertEquals(1, development.stars(owner));
        // Saved, so a restart keeps the stars.
        assertEquals(1, new IslandDevelopment(data, islands, "test", () -> LocalDate.of(2026, 11, 14)).stars(owner));
        assertEquals(IslandDevelopment.MAX_STARS, IslandDevelopment.stars(Long.MAX_VALUE / 2));
    }

    @Test void rankingScoresAndWeeks() {
        assertEquals(1_000 + 500 + 3_000 + 1_000 + 500, IslandRanking.score(1_000, 500, 3, 10, 2));
        ZoneId zone = ZoneId.of("America/Denver");
        // Sunday night and Monday morning fall in different ranking weeks.
        String sunday = IslandRanking.week(Instant.parse("2026-11-16T06:00:00Z"), zone);
        String monday = IslandRanking.week(Instant.parse("2026-11-16T08:00:00Z"), zone);
        assertNotEquals(sunday, monday);
        assertEquals("2026-W47", monday);
    }

    @Test void showcasesForWorkersBossesAndPrestige() {
        var c = new IslandShowcase.Context(0, 50, 0, 0, java.util.Set.of(), java.util.Set.of(), 0, 25, 10, 5);
        assertTrue(IslandShowcase.FOREMANS_LEDGER.unlocked(c));
        assertFalse(IslandShowcase.GOLDEN_PICK.unlocked(c));
        assertTrue(IslandShowcase.LIGHTNING_ROD.unlocked(c));
        assertFalse(IslandShowcase.THUNDER_HEART.unlocked(c));
        assertTrue(IslandShowcase.ASCENDED_LANTERN.unlocked(c));
        assertFalse(IslandShowcase.STARFALL_CROWN.unlocked(c));
        assertEquals(List.of(), List.of());
    }
}
