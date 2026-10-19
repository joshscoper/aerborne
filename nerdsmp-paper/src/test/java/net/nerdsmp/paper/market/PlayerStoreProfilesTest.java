package net.nerdsmp.paper.market;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStoreProfilesTest {
    @TempDir Path directory;
    private final AccountId owner = AccountId.random();
    private static final String PATH = "seasons.season-1.player-store";

    private PlayerDataStore data() {
        return new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
    }

    private PlayerStoreProfiles profiles(PlayerDataStore data) {
        return new PlayerStoreProfiles(data, "season-1");
    }

    @Test void openingAnUnconfiguredStoreDoesNotCreateOrSaveAPlayerFile() {
        var profiles = profiles(data());
        assertEquals(new PlayerStoreProfiles.Profile("Player's Store", "Supplies offered by another player."), profiles.profile(owner));
        assertFalse(Files.exists(directory.resolve("player_data/" + owner + ".yml")));
    }

    @Test void defaultsUseSafeBoundPlayerNamesAndFitTheNameLimit() {
        var data = data();
        UUID player = UUID.randomUUID();
        data.bind(player, owner, new PlatformIdentity(PlatformIdentity.Platform.JAVA, player.toString()), "Joshscoper");
        assertEquals("Joshscoper's Store", profiles(data).profile(owner).name());
        data.update(owner, yaml -> yaml.set("identity.last-known-name", "<red>" + "A".repeat(70) + "§"));
        var longName = profiles(data).profile(owner).name();
        assertTrue(longName.matches("[\\p{L}\\p{N} _'-]{3,48}"));
        assertEquals(44, longName.length());
        data.update(owner, yaml -> yaml.set("identity.last-known-name", "<>§"));
        assertEquals("Player's Store", profiles(data).profile(owner).name());
    }

    @Test void brandingPersistsAcrossAFullPlayerStoreRestart() {
        var profiles = profiles(data());
        var next = new PlayerStoreProfiles.Profile("Élan's Ore Exchange", "Iron, copper and other mining supplies.");
        profiles.update(owner, profiles.profile(owner), next);
        assertEquals(next, profiles(data()).profile(owner));
    }

    @Test void staleEditsCannotOverwriteNewerBranding() throws Exception {
        var profiles = profiles(data());
        var before = profiles.profile(owner);
        var next = new PlayerStoreProfiles.Profile("Cloud Market", "Fresh island supplies.");
        profiles.update(owner, before, next);
        Path file = directory.resolve("player_data/" + owner + ".yml");
        byte[] saved = Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class, () -> profiles.update(owner, before,
            new PlayerStoreProfiles.Profile("Stale Market", "This must not replace the new store.")));
        assertEquals(next, profiles.profile(owner));
        assertArrayEquals(saved, Files.readAllBytes(file));
    }

    @Test void concurrentEditsCompareAndSaveWithinTheSamePlayerDataLock() throws Exception {
        var profiles = profiles(data());
        var before = profiles.profile(owner);
        var first = new PlayerStoreProfiles.Profile("First Store", "First edit.");
        var second = new PlayerStoreProfiles.Profile("Second Store", "Second edit.");
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> {
                ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                try { profiles.update(owner, before, first); return true; }
                catch (IllegalArgumentException stale) { return false; }
            });
            var b = workers.submit(() -> {
                ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                try { profiles.update(owner, before, second); return true; }
                catch (IllegalArgumentException stale) { return false; }
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean firstWon = a.get(5, TimeUnit.SECONDS), secondWon = b.get(5, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(firstWon ? first : second, profiles(data()).profile(owner));
        }
    }

    @Test void failedWritesLeaveBothCachedAndSavedProfilesUnchanged() throws Exception {
        var profiles = profiles(data());
        var next = new PlayerStoreProfiles.Profile("Cloud Market", "Fresh island supplies.");
        profiles.update(owner, profiles.profile(owner), next);
        Path file = directory.resolve("player_data/" + owner + ".yml");
        byte[] saved = Files.readAllBytes(file);
        Files.createDirectory(directory.resolve("player_data/" + owner + ".yml.tmp"));
        assertThrows(IllegalStateException.class, () -> profiles.update(owner, next,
            new PlayerStoreProfiles.Profile("Unsaved Store", "The disk is blocked.")));
        assertEquals(next, profiles.profile(owner));
        assertEquals(next, profiles(data()).profile(owner));
        assertArrayEquals(saved, Files.readAllBytes(file));
    }

    @Test void profileChangesPreserveExistingIslandSkillsMarksAndEntitlements() {
        var data = data();
        data.update(owner, yaml -> {
            yaml.set("seasons.season-1.island.name", "Old Island");
            yaml.set("seasons.season-1.skills.mining.xp", 2357);
            yaml.set("economy.marks", 15300L);
            yaml.set("entitlements.supporter", "existing-purchase");
        });
        var profiles = profiles(data);
        profiles.update(owner, profiles.profile(owner), new PlayerStoreProfiles.Profile("Old Island Goods", "Made on my island."));
        var restarted = data();
        assertEquals("Old Island", restarted.read(owner, yaml -> yaml.getString("seasons.season-1.island.name")));
        assertEquals(2357, restarted.read(owner, yaml -> yaml.getInt("seasons.season-1.skills.mining.xp")).intValue());
        assertEquals(15300L, restarted.read(owner, yaml -> yaml.getLong("economy.marks")).longValue());
        assertEquals("existing-purchase", restarted.read(owner, yaml -> yaml.getString("entitlements.supporter")));
    }

    @Test void linkedIdentitiesShareTheCanonicalStoreWithoutCreatingAnotherOwnerFile() {
        var data = data();
        UUID primary = UUID.randomUUID(), linked = UUID.randomUUID();
        data.bind(primary, owner, new PlatformIdentity(PlatformIdentity.Platform.JAVA, primary.toString()), "Primary");
        var profiles = profiles(data);
        var next = new PlayerStoreProfiles.Profile("Shared Shop", "One account, one shop.");
        profiles.update(owner, profiles.profile(owner), next);
        data.bind(linked, owner, new PlatformIdentity(PlatformIdentity.Platform.JAVA, linked.toString()), "Linked");
        assertEquals(next, profiles.profile(owner));
        assertEquals(next, profiles(data()).profile(owner));
        assertTrue(Files.exists(directory.resolve("player_data/" + primary + ".yml")));
        assertFalse(Files.exists(directory.resolve("player_data/" + linked + ".yml")));
    }

    @Test void seasonsKeepTheirOwnBrandingWithoutDeletingOlderProfiles() {
        var data = data();
        var first = profiles(data);
        var second = new PlayerStoreProfiles(data, "season-2");
        var old = new PlayerStoreProfiles.Profile("First Season Shop", "Old goods.");
        first.update(owner, first.profile(owner), old);
        assertEquals("Player's Store", second.profile(owner).name());
        var current = new PlayerStoreProfiles.Profile("Second Season Shop", "New goods.");
        second.update(owner, second.profile(owner), current);
        assertEquals(old, profiles(data()).profile(owner));
        assertEquals(current, new PlayerStoreProfiles(data(), "season-2").profile(owner));
    }

    @Test void malformedStoredProfilesFailClosedWithoutReplacingTheDocument() throws Exception {
        var data = data();
        var profiles = profiles(data);
        for (String invalid : new String[]{"scalar", "schema", "schema-text", "missing-name", "missing-description", "bad-name", "bad-description"}) {
            data.update(owner, yaml -> {
                yaml.set(PATH, null);
                yaml.set(PATH + ".schema", 1);
                yaml.set(PATH + ".name", "Valid Store");
                yaml.set(PATH + ".description", "Valid description.");
                switch (invalid) {
                    case "scalar" -> yaml.set(PATH, "not a section");
                    case "schema" -> yaml.set(PATH + ".schema", 2);
                    case "schema-text" -> yaml.set(PATH + ".schema", "1");
                    case "missing-name" -> yaml.set(PATH + ".name", null);
                    case "missing-description" -> yaml.set(PATH + ".description", null);
                    case "bad-name" -> yaml.set(PATH + ".name", "<red>bad");
                    case "bad-description" -> yaml.set(PATH + ".description", "bad\nline");
                    default -> throw new AssertionError(invalid);
                }
            });
            Path file = directory.resolve("player_data/" + owner + ".yml");
            byte[] saved = Files.readAllBytes(file);
            assertThrows(RuntimeException.class, () -> profiles.profile(owner), invalid);
            assertArrayEquals(saved, Files.readAllBytes(file), invalid);
        }
    }

    @Test void namesRejectUnsupportedCharactersAndLengths() {
        for (String name : new String[]{null, "", "   ", "ab", "A".repeat(49), "<red>Shop", "Shop§", "Line\nBreak", "foo/bar"}) {
            assertThrows(IllegalArgumentException.class, () -> new PlayerStoreProfiles.Profile(name, ""));
        }
        assertDoesNotThrow(() -> new PlayerStoreProfiles.Profile("Ore", ""));
        assertDoesNotThrow(() -> new PlayerStoreProfiles.Profile("A".repeat(48), ""));
    }

    @Test void descriptionsRejectFormattingCodesControlCharactersAndExcessLength() {
        for (String description : new String[]{null, "A".repeat(121), "§aGreen", "line\nline", "line\rline", "tab\tvalue", "zero\0value", "line\u2028line", "line\u2029line"}) {
            assertThrows(IllegalArgumentException.class, () -> new PlayerStoreProfiles.Profile("Valid Store", description));
        }
        assertDoesNotThrow(() -> new PlayerStoreProfiles.Profile("Valid Store", "A".repeat(120)));
        var literal = new PlayerStoreProfiles.Profile("Valid Store", "<green>These are plain characters.</green>");
        assertEquals("<green>These are plain characters.</green>", literal.description());
    }

    @Test void seasonNamesCannotInjectAnotherPlayerDataPath() {
        var data = data();
        for (String season : new String[]{null, "", "season.one", "has space", "../season", "a".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> new PlayerStoreProfiles(data, season));
        }
        assertDoesNotThrow(() -> new PlayerStoreProfiles(data, "season_1-2"));
        assertDoesNotThrow(() -> new PlayerStoreProfiles(data, "a".repeat(64)));
    }
}
