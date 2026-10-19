package net.nerdsmp.paper.market;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Verifies Paper's otherwise unacknowledged player save using a fresh persisted PDC receipt. */
public final class MarketPlayerSave {
    private final JavaPlugin plugin;
    private final NamespacedKey receiptKey;
    private final Path levelDirectory;
    private final Path playerDirectory;

    public MarketPlayerSave(JavaPlugin plugin) {
        this.plugin = plugin;
        requireMainThread();
        // Paper 26.2 World.getWorldPath() is a dimension folder, even for the Overworld.
        // The supported server API exposes the shared level root used by player saves.
        levelDirectory = plugin.getServer().getLevelDirectory().toAbsolutePath().normalize();
        playerDirectory = playerDataDirectory(levelDirectory);
        receiptKey = new NamespacedKey(plugin, "market_save_receipt");
    }

    public void saveAndVerify(Player player) {
        requireMainThread();
        if (!plugin.isEnabled() || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
            || !levelDirectory.equals(plugin.getServer().getLevelDirectory().toAbsolutePath().normalize())) {
            throw new IllegalStateException("Player session or level storage changed during the market transfer");
        }
        String receipt = UUID.randomUUID().toString();
        player.getPersistentDataContainer().set(receiptKey, PersistentDataType.STRING, receipt);
        player.saveData();
        try {
            SavedPlayerReceipt.verify(playerDirectory.resolve(player.getUniqueId() + ".dat"), receiptKey.toString(), receipt);
        } catch (IOException exception) {
            throw new IllegalStateException("Player inventory save was not verified; the market transfer requires review", exception);
        }
    }

    static Path playerDataDirectory(Path levelDirectory) {
        // Exact pinned Paper 26.2 LevelResource.PLAYER_DATA_DIR. Never search legacy paths
        // or walk up from a dimension folder: that could acknowledge an unrelated save.
        Path players = levelDirectory.toAbsolutePath().normalize().resolve("players").resolve("data");
        if (!Files.isDirectory(players)) {
            throw new IllegalStateException("The level's players/data folder is unavailable; market inventory transfers cannot be verified");
        }
        return players;
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Market inventory saves require the main server thread");
    }
}
