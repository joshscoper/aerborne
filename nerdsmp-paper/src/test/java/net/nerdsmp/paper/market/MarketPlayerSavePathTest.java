package net.nerdsmp.paper.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MarketPlayerSavePathTest {
    @TempDir Path directory;

    @Test
    void paper262PlayerDataBelongsToTheLevelRootNotADimensionFolder() throws Exception {
        Path level = Files.createDirectory(directory.resolve("world"));
        Path overworld = Files.createDirectories(level.resolve("dimensions/minecraft/overworld"));
        Path savedPlayers = Files.createDirectories(level.resolve("players/data"));

        assertEquals(savedPlayers.toAbsolutePath().normalize(), MarketPlayerSave.playerDataDirectory(level));
        assertThrows(IllegalStateException.class, () -> MarketPlayerSave.playerDataDirectory(overworld));
    }

    @Test
    void legacyPlayerdataIsNeverAcceptedAsAFallback() throws Exception {
        Path level = Files.createDirectory(directory.resolve("world"));
        Files.createDirectory(level.resolve("playerdata"));

        assertThrows(IllegalStateException.class, () -> MarketPlayerSave.playerDataDirectory(level));
    }

    @Test
    void anUnavailableOrNonDirectoryStoragePathFailsClosed() throws Exception {
        Path level = Files.createDirectory(directory.resolve("world"));
        Files.createDirectory(level.resolve("players"));
        Files.createFile(level.resolve("players/data"));

        assertThrows(IllegalStateException.class, () -> MarketPlayerSave.playerDataDirectory(level));
    }
}
