package net.nerdsmp.paper.storage;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Owns local player persistence. Each canonical Bukkit UUID has one atomic YAML
 * document at player_data/&lt;uuid&gt;.yml; services own namespaced paths inside it.
 */
public final class PlayerDataStore {
    public static final int SCHEMA_VERSION = 1;

    private final File directory;
    private final Logger logger;
    private final Map<UUID, YamlConfiguration> documents = new HashMap<>();
    private final Map<AccountId, UUID> accountOwners = new HashMap<>();

    public PlayerDataStore(File pluginDirectory, Logger logger) {
        this.directory = new File(pluginDirectory, "player_data");
        this.logger = logger;
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Could not create player data directory: " + directory);
        }
        indexExistingFiles();
    }

    public synchronized void bind(
        UUID playerId,
        AccountId accountId,
        PlatformIdentity identity,
        String playerName
    ) {
        UUID owner = accountOwners.getOrDefault(accountId, playerId);
        update(owner, yaml -> {
            yaml.set("schema-version", SCHEMA_VERSION);
            yaml.set("identity.account-id", accountId.toString());
            yaml.set("identity.primary-uuid", owner.toString());
            yaml.set("identity.last-known-name", playerName);
            yaml.set("identity.last-seen", Instant.now().toString());
            yaml.set("identity.platform-identities." + identity.platform().name().toLowerCase(), identity.externalId());
        });
        accountOwners.putIfAbsent(accountId, owner);
    }

    public synchronized <T> T read(AccountId accountId, Function<YamlConfiguration, T> reader) {
        return read(owner(accountId), reader);
    }

    public synchronized <T> T read(UUID playerId, Function<YamlConfiguration, T> reader) {
        return reader.apply(document(playerId));
    }

    public synchronized void update(AccountId accountId, Consumer<YamlConfiguration> mutation) {
        update(owner(accountId), mutation);
    }

    public synchronized void update(UUID playerId, Consumer<YamlConfiguration> mutation) {
        // Publish only after both the mutation and the atomic disk write succeed.
        // Otherwise a failed save could leave paid rewards or protection changes in memory.
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(document(playerId).saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not copy player data for " + playerId, exception);
        }
        mutation.accept(yaml);
        save(playerId, yaml);
        documents.put(playerId, yaml);
    }

    public File directory() {
        return directory;
    }

    public synchronized Set<AccountId> knownAccounts() {
        return Set.copyOf(accountOwners.keySet());
    }

    public synchronized Map<PlatformIdentity, AccountId> identityMappings() {
        Map<PlatformIdentity, AccountId> mappings = new HashMap<>();
        documents.forEach((playerId, yaml) -> {
            String rawAccount = yaml.getString("identity.account-id");
            if (rawAccount == null) {
                return;
            }
            try {
                AccountId accountId = AccountId.parse(rawAccount);
                for (PlatformIdentity.Platform platform : PlatformIdentity.Platform.values()) {
                    String externalId = yaml.getString("identity.platform-identities."
                        + platform.name().toLowerCase(Locale.ROOT));
                    if (externalId != null && !externalId.isBlank()) {
                        mappings.put(new PlatformIdentity(platform, externalId), accountId);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring malformed identity mapping in " + file(playerId).getName());
            }
        });
        return Map.copyOf(mappings);
    }

    public synchronized void linkIdentity(AccountId accountId, PlatformIdentity identity) {
        UUID targetOwner = owner(accountId);
        String path = "identity.platform-identities." + identity.platform().name().toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, YamlConfiguration> entry : documents.entrySet()) {
            if (entry.getKey().equals(targetOwner)
                || !identity.externalId().equalsIgnoreCase(entry.getValue().getString(path, ""))) {
                continue;
            }
            update(entry.getKey(), yaml -> yaml.set(path, null));
        }
        update(targetOwner, yaml -> {
            yaml.set("schema-version", SCHEMA_VERSION);
            yaml.set("identity.account-id", accountId.toString());
            yaml.set("identity.primary-uuid", targetOwner.toString());
            yaml.set(path, identity.externalId());
        });
    }

    private UUID owner(AccountId accountId) {
        return accountOwners.getOrDefault(accountId, accountId.value());
    }

    private YamlConfiguration document(UUID playerId) {
        return documents.computeIfAbsent(playerId, id -> loadStrict(file(id)));
    }

    private YamlConfiguration loadStrict(File source) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!source.exists()) return yaml;
        try {
            yaml.load(source);
            return yaml;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Cannot read player data; refusing to replace it: " + source, exception);
        }
    }

    private File file(UUID playerId) {
        return new File(directory, playerId.toString().toLowerCase() + ".yml");
    }

    private void save(UUID playerId, YamlConfiguration yaml) {
        try {
            AtomicYamlFiles.save(yaml, file(playerId));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save player data for " + playerId, exception);
        }
    }

    private void indexExistingFiles() {
        File[] files = directory.listFiles((ignored, name) -> name.matches("[0-9a-fA-F-]{36}\\.yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                UUID playerId = UUID.fromString(file.getName().substring(0, 36));
                YamlConfiguration yaml = loadStrict(file);
                documents.put(playerId, yaml);
                String rawAccount = yaml.getString("identity.account-id");
                if (rawAccount != null) {
                    accountOwners.putIfAbsent(AccountId.parse(rawAccount), playerId);
                }
            } catch (IllegalArgumentException exception) {
                logger.warning("Ignoring malformed player data file: " + file.getName());
            }
        }
    }
}
