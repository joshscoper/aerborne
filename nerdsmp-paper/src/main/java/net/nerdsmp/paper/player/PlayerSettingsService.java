package net.nerdsmp.paper.player;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsService implements AutoCloseable {
    private final java.util.logging.Logger logger;
    private final File legacyFile;
    private final PlayerDataStore playerData;
    private final Map<UUID, PlayerPreferences> preferences = new ConcurrentHashMap<>();

    public PlayerSettingsService(NerdSmpPlugin plugin, PlayerDataStore playerData) {
        this(plugin.getLogger(), playerData);
    }

    PlayerSettingsService(java.util.logging.Logger logger, PlayerDataStore playerData) {
        this.logger = logger;
        this.playerData = playerData;
        legacyFile = new File(playerData.directory().getParentFile(), "player-settings.yml");
        migrateLegacy();
    }

    public boolean enabled(UUID playerId, Setting setting) {
        return preferences(playerId).values().getOrDefault(setting, setting.defaultValue());
    }

    public void invalidate(UUID playerId) { preferences.remove(playerId); }

    public boolean toggle(UUID playerId, Setting setting) {
        boolean value = !enabled(playerId, setting);
        set(playerId, setting, value);
        return value;
    }

    public void set(UUID playerId, Setting setting, boolean value) {
        PlayerPreferences current = preferences(playerId);
        if (current.values().getOrDefault(setting, setting.defaultValue()) == value) return;
        EnumMap<Setting, Boolean> values = new EnumMap<>(current.values());
        values.put(setting, value);
        PlayerPreferences updated = new PlayerPreferences(values, current.channel());
        save(playerId, updated);
        preferences.put(playerId, updated);
    }

    public String channel(UUID playerId) {
        return preferences(playerId).channel();
    }

    public void channel(UUID playerId, String rawChannel) {
        String channel = rawChannel.toLowerCase(Locale.ROOT);
        PlayerPreferences current = preferences(playerId);
        if (current.channel().equals(channel)) return;
        PlayerPreferences updated = new PlayerPreferences(new EnumMap<>(current.values()), channel);
        save(playerId, updated);
        preferences.put(playerId, updated);
    }

    public Map<Setting, Boolean> snapshot(UUID playerId) {
        return Map.copyOf(preferences(playerId).values());
    }

    @Override
    public void close() {
        // Every mutation is synchronously committed; shutdown need not rewrite every loaded profile.
        preferences.clear();
    }

    private PlayerPreferences preferences(UUID playerId) {
        return preferences.computeIfAbsent(playerId, this::load);
    }

    private PlayerPreferences defaults() {
        EnumMap<Setting, Boolean> values = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) {
            values.put(setting, setting.defaultValue());
        }
        return new PlayerPreferences(values, "global");
    }

    private PlayerPreferences load(UUID playerId) {
        return playerData.read(playerId, yaml -> {
            PlayerPreferences loaded = defaults();
            EnumMap<Setting, Boolean> values = new EnumMap<>(loaded.values());
            for (Setting setting : Setting.values()) {
                values.put(setting, yaml.getBoolean("preferences.settings." + setting.id(), setting.defaultValue()));
            }
            String channel = yaml.getString("preferences.chat-channel", "global").toLowerCase(Locale.ROOT);
            return new PlayerPreferences(values, channel);
        });
    }

    private void migrateLegacy() {
        if (!legacyFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String rawId : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawId);
                if (playerData.read(playerId, document -> document.contains("preferences"))) continue;
                PlayerPreferences loaded = defaults();
                EnumMap<Setting, Boolean> values = new EnumMap<>(loaded.values());
                for (Setting setting : Setting.values()) {
                    String path = rawId + ".settings." + setting.id();
                    values.put(setting, players.getBoolean(path, setting.defaultValue()));
                }
                String channel = players.getString(rawId + ".channel", "global").toLowerCase(Locale.ROOT);
                PlayerPreferences migrated = new PlayerPreferences(values, channel);
                save(playerId, migrated);
                preferences.put(playerId, migrated);
            } catch (IllegalArgumentException exception) {
                logger.warning("Ignoring invalid player settings key: " + rawId);
            }
        }
    }

    private synchronized void save(UUID playerId, PlayerPreferences data) {
        playerData.update(playerId, yaml -> {
            yaml.set("preferences.chat-channel", data.channel());
            data.values().forEach((setting, value) ->
                yaml.set("preferences.settings." + setting.id(), value));
        });
    }

    public enum Setting {
        TIPS("tips", true, "Tip messages"),
        DIRECT_MESSAGES("direct-messages", true, "Direct messages"),
        FRIEND_REQUESTS("friend-requests", true, "Friend requests"),
        FRIEND_NOTICES("friend-notices", true, "Friend online notices"),
        TRADE_REQUESTS("trade-requests", true, "Trade requests"),
        PARTY_REQUESTS("party-requests", true, "Party invitations"),
        COOP_INVITES("coop-invites", true, "Co-op invitations"),
        TELEPORT_REQUESTS("teleport-requests", true, "Teleport requests"),
        MOB_HEALTH_BARS("mob-health-bars", true, "Mob health bars"),
        ISLAND_DISPLAYS("island-displays", true, "Island showcases"),
        ISLAND_EFFECTS("island-effects", true, "Island celebration effects"),
        TREE_FELLING("tree-felling", true, "Tree felling"),
        TREE_REPLANTING("tree-replanting", true, "Tree replanting");

        private final String id;
        private final boolean defaultValue;
        private final String displayName;

        Setting(String id, boolean defaultValue, String displayName) {
            this.id = id;
            this.defaultValue = defaultValue;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public boolean defaultValue() {
            return defaultValue;
        }

        public String displayName() {
            return displayName;
        }

        public static Setting parse(String raw) {
            String normalized = raw.toLowerCase(Locale.ROOT).replace('_', '-');
            for (Setting setting : values()) {
                if (setting.id.equals(normalized)) {
                    return setting;
                }
            }
            return null;
        }
    }

    private record PlayerPreferences(EnumMap<Setting, Boolean> values, String channel) {
    }
}
