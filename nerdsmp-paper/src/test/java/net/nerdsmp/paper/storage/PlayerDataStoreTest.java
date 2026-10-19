package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class PlayerDataStoreTest {
    @TempDir Path directory;

    @Test void failedMutationDoesNotChangeCachedOrPersistedDocument() {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        UUID id = UUID.randomUUID();
        data.update(id, yaml -> yaml.set("value", 10));
        assertThrows(IllegalStateException.class, () -> data.update(id, yaml -> {
            yaml.set("value", 20);
            throw new IllegalStateException("test failure");
        }));
        assertEquals(10, (int) data.read(id, yaml -> yaml.getInt("value")));
        var restarted = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        assertEquals(10, (int) restarted.read(id, yaml -> yaml.getInt("value")));
    }

    @Test void malformedYamlFailsClosedAndLeavesSourceUntouched() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("player_data"));
        Path file = folder.resolve(UUID.randomUUID() + ".yml");
        String malformed = "identity: [broken";
        Files.writeString(file, malformed);
        assertThrows(IllegalStateException.class, () -> new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger()));
        assertEquals(malformed, Files.readString(file));
    }

    @Test void failedBindDoesNotPublishAnAccountMapping() throws Exception {
        var data = new PlayerDataStore(directory.toFile(), Logger.getAnonymousLogger());
        AccountId account = AccountId.random();
        UUID playerId = UUID.randomUUID();
        Path blocked = Files.createDirectory(directory.resolve("player_data").resolve(playerId + ".yml"));
        Files.createFile(blocked.resolve("keep"));
        assertThrows(IllegalStateException.class, () -> data.bind(playerId, account,
            new PlatformIdentity(PlatformIdentity.Platform.JAVA, playerId.toString()), "Tester"));
        assertFalse(data.knownAccounts().contains(account));
    }
}
