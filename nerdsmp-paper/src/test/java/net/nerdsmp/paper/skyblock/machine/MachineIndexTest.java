package net.nerdsmp.paper.skyblock.machine;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.paper.skyblock.IslandService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class MachineIndexTest {
    @TempDir Path directory;
    private final AccountId owner = AccountId.random(), friend = AccountId.random(), stranger = AccountId.random();
    private final UUID world = UUID.randomUUID();
    private PlayerDataStore data() {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        for (AccountId account : List.of(owner, friend, stranger))
            data.bind(account.value(), account, new PlatformIdentity(PlatformIdentity.Platform.JAVA, account.toString()), "Player");
        return data;
    }
    private MachineIndex.Position position(int x) { return new MachineIndex.Position(world, x, 121, 0); }
    @Test void merelyOpeningTheIndexDoesNotWriteLegacyProgress() throws Exception {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        Path file = data.directory().toPath().resolve(owner + ".yml"); byte[] before = Files.readAllBytes(file);
        var index = new MachineIndex(data, islands, "season");
        assertEquals(0, index.count(owner)); assertArrayEquals(before, Files.readAllBytes(file));
        assertNull(data.read(owner, y -> y.get("seasons.season.skyblock-machines")));
    }
    @Test void restartRestoresOnlyNewMachineNamespaceAndPreservesUnrelatedData() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        data.update(owner, y -> { y.set("economy.balance", 6543L); y.set("seasons.season.skills.mining", 8000L); });
        var index = new MachineIndex(data, islands, "season"); var entry = index.add(owner, owner, position(1), MachineKind.CRUSHER);
        var reload = new MachineIndex(new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()), islands, "season");
        assertEquals(entry, reload.at(position(1))); assertEquals(1, reload.count(owner));
        assertEquals(6543L, data.<Long>read(owner, y -> y.getLong("economy.balance")).longValue());
        assertEquals(8000L, data.<Long>read(owner, y -> y.getLong("seasons.season.skills.mining")).longValue());
        assertEquals(0, new MachineIndex(data, islands, "another").count(owner));
    }
    @Test void membershipAndTerritoryAreRequiredEvenForInvitedVisitors() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        var index = new MachineIndex(data, islands, "season");
        islands.invite(owner, friend, Instant.now());
        assertThrows(IllegalArgumentException.class, () -> index.add(null, owner, position(1), MachineKind.CRUSHER));
        assertThrows(IllegalArgumentException.class, () -> index.add(friend, owner, position(1), MachineKind.CRUSHER));
        assertThrows(IllegalArgumentException.class, () -> index.add(stranger, owner, position(1), MachineKind.CRUSHER));
        islands.accept(friend, owner, Instant.now());
        assertNotNull(index.add(friend, owner, position(1), MachineKind.CRUSHER));
        assertThrows(IllegalStateException.class, () -> index.add(owner, owner, position(400), MachineKind.CRUSHER));
    }
    @Test void duplicatePositionAndSixteenMachineLimitAreEnforced() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        var index = new MachineIndex(data, islands, "season");
        for (int n = 0; n < 16; n++) index.add(owner, owner, position(n), MachineKind.CRUSHER);
        assertEquals(16, index.count(owner));
        assertThrows(IllegalArgumentException.class, () -> index.add(owner, owner, position(1), MachineKind.SAWMILL));
        assertThrows(IllegalArgumentException.class, () -> index.add(owner, owner, position(16), MachineKind.CRUSHER));
        assertThrows(UnsupportedOperationException.class, () -> index.entries().clear());
    }
    @Test void removalUsesExactEntryAndNeverReplays() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        var index = new MachineIndex(data, islands, "season"); var entry = index.add(owner, owner, position(1), MachineKind.CRUSHER);
        var stale = new MachineIndex.Entry(UUID.randomUUID(), owner, position(1), MachineKind.CRUSHER);
        assertThrows(IllegalArgumentException.class, () -> index.remove(stale)); assertEquals(entry, index.at(position(1)));
        index.remove(entry); assertNull(index.at(position(1)));
        assertThrows(IllegalArgumentException.class, () -> index.remove(entry));
    }
    @Test void failedPlacementOrRemovalWriteNeverPublishesAnIndexChange() throws Exception {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        var index = new MachineIndex(data, islands, "season"); var entry = index.add(owner, owner, position(1), MachineKind.CRUSHER);
        Path file = data.directory().toPath().resolve(owner + ".yml"); Path saved = directory.resolve("saved.yml");
        Files.move(file, saved); Files.createDirectory(file);
        try {
            assertThrows(IllegalStateException.class, () -> index.add(owner, owner, position(2), MachineKind.CRUSHER));
            assertEquals(1, index.count(owner)); assertNull(index.at(position(2)));
            assertThrows(IllegalStateException.class, () -> index.remove(entry)); assertEquals(entry, index.at(position(1)));
        } finally { Files.delete(file); Files.move(saved, file); }
    }
    @Test void corruptSchemaWrongTerritoryAndDuplicateTokensFailClosed() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        String path = "seasons.season.skyblock-machines";
        data.update(owner, y -> { y.set(path + ".schema", 99); y.set(path + ".entries", List.of()); });
        assertThrows(IllegalStateException.class, () -> new MachineIndex(data, islands, "season"));
        var invalid = new MachineIndex.Entry(UUID.randomUUID(), owner, position(400), MachineKind.CRUSHER);
        data.update(owner, y -> { y.set(path + ".schema", 1); y.set(path + ".entries", List.of(invalid.encode())); });
        assertThrows(IllegalStateException.class, () -> new MachineIndex(data, islands, "season"));
        UUID token = UUID.randomUUID();
        data.update(owner, y -> y.set(path + ".entries", List.of(
            new MachineIndex.Entry(token, owner, position(1), MachineKind.CRUSHER).encode(),
            new MachineIndex.Entry(token, owner, position(2), MachineKind.CRUSHER).encode())));
        assertThrows(IllegalStateException.class, () -> new MachineIndex(data, islands, "season"));
    }
    @Test void boundsParsingAndSeasonAreStrict() {
        assertThrows(IllegalArgumentException.class, () -> new MachineIndex.Position(world, 30_000_001, 121, 0));
        assertThrows(IllegalArgumentException.class, () -> MachineIndex.Entry.parse(owner, "not,a,machine"));
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        assertThrows(IllegalArgumentException.class, () -> new MachineIndex(data, islands, "../season"));
    }
    @Test void lateCorruptionCannotBeOverwrittenByAValidInMemoryIndex() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        var index = new MachineIndex(data, islands, "season"); var entry = index.add(owner, owner, position(1), MachineKind.CRUSHER);
        String path = "seasons.season.skyblock-machines";
        data.update(owner, y -> y.set(path + ".schema", 99));
        assertThrows(IllegalStateException.class, () -> index.remove(entry)); assertEquals(entry, index.at(position(1)));
        assertEquals(99, data.<Integer>read(owner, y -> y.getInt(path + ".schema")).intValue());
        data.update(owner, y -> { y.set(path + ".schema", 1); y.set(path + ".entries", List.of("invalid")); });
        assertThrows(IllegalStateException.class, () -> index.add(owner, owner, position(2), MachineKind.CRUSHER));
        assertEquals(List.of("invalid"), data.read(owner, y -> y.getStringList(path + ".entries")));
    }
    @Test void concurrentCanonicalStoreWritersCannotLoseEachOthersPlacements() {
        var data = data(); var islands = new IslandService(data, "season"); islands.create(owner);
        var first = new MachineIndex(data, islands, "season"); var stale = new MachineIndex(data, islands, "season");
        var entry = first.add(owner, owner, position(1), MachineKind.CRUSHER);
        assertThrows(IllegalStateException.class, () -> stale.add(owner, owner, position(2), MachineKind.SAWMILL));
        assertEquals(0, stale.count(owner)); assertEquals(entry, new MachineIndex(data, islands, "season").at(position(1)));
        var second = new MachineIndex(data, islands, "season");
        var other = second.add(owner, owner, position(2), MachineKind.SAWMILL);
        assertThrows(IllegalStateException.class, () -> first.remove(entry));
        var latest = new MachineIndex(data, islands, "season");
        assertEquals(entry, latest.at(position(1))); assertEquals(other, latest.at(position(2)));
    }
}
