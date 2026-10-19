package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NpcProgressStore implements AutoCloseable {
    private final File file;
    private final Map<AccountId, Progress> players = new HashMap<>();

    public NpcProgressStore(JavaPlugin plugin, String seasonId) {
        String safeSeason = seasonId.replaceAll("[^a-zA-Z0-9_-]", "_");
        file = new File(plugin.getDataFolder(), "npc-progress-" + safeSeason + ".yml");
        load();
    }

    public synchronized Snapshot snapshot(AccountId accountId) {
        Progress progress = players.computeIfAbsent(accountId, ignored -> new Progress());
        return new Snapshot(progress.flags, progress.completedChoices, progress.lastUsedAt);
    }

    public synchronized boolean available(
        AccountId accountId,
        String choiceKey,
        boolean once,
        Duration cooldown,
        Instant now
    ) {
        Progress progress = players.computeIfAbsent(accountId, ignored -> new Progress());
        if (once && progress.completedChoices.contains(choiceKey)) {
            return false;
        }
        Instant last = progress.lastUsedAt.get(choiceKey);
        return last == null || cooldown.isZero() || !last.plus(cooldown).isAfter(now);
    }

    public synchronized void recordChoice(
        AccountId accountId,
        String choiceKey,
        boolean once,
        Duration cooldown,
        Instant now,
        Map<String, Boolean> changedFlags
    ) {
        Progress progress = players.computeIfAbsent(accountId, ignored -> new Progress());
        Progress backup = progress.copy();
        if (once) {
            progress.completedChoices.add(choiceKey);
        }
        if (!cooldown.isZero()) {
            progress.lastUsedAt.put(choiceKey, now);
        }
        changedFlags.forEach((flag, enabled) -> {
            if (enabled) {
                progress.flags.add(flag);
            } else {
                progress.flags.remove(flag);
            }
        });
        try {
            save();
        } catch (RuntimeException exception) {
            players.put(accountId, backup);
            throw exception;
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String rawAccount : root.getKeys(false)) {
            try {
                AccountId account = new AccountId(UUID.fromString(rawAccount));
                ConfigurationSection section = root.getConfigurationSection(rawAccount);
                if (section == null) {
                    continue;
                }
                Progress progress = new Progress();
                progress.flags.addAll(section.getStringList("flags"));
                progress.completedChoices.addAll(section.getStringList("completed-choices"));
                ConfigurationSection cooldowns = section.getConfigurationSection("last-used-at");
                if (cooldowns != null) {
                    for (String key : cooldowns.getKeys(false)) {
                        try {
                            progress.lastUsedAt.put(key, Instant.ofEpochMilli(cooldowns.getLong(key)));
                        } catch (RuntimeException ignored) {
                            // Ignore malformed timestamps without discarding the player's other progress.
                        }
                    }
                }
                players.put(account, progress);
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid account sections from hand-edited local files.
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        players.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(AccountId::toString)))
            .forEach(entry -> {
                String path = "players." + entry.getKey();
                Progress progress = entry.getValue();
                yaml.set(path + ".flags", progress.flags.stream().sorted().toList());
                yaml.set(path + ".completed-choices", progress.completedChoices.stream().sorted().toList());
                progress.lastUsedAt.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(cooldown ->
                    yaml.set(path + ".last-used-at." + cooldown.getKey(), cooldown.getValue().toEpochMilli()));
            });
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist NPC dialogue progress", exception);
        }
    }

    @Override
    public synchronized void close() {
        save();
    }

    public record Snapshot(Set<String> flags, Set<String> completedChoices, Map<String, Instant> lastUsedAt) {
        public Snapshot {
            flags = Set.copyOf(flags);
            completedChoices = Set.copyOf(completedChoices);
            lastUsedAt = Map.copyOf(lastUsedAt);
        }

        public boolean flag(String key) {
            return flags.contains(key);
        }

        public Instant lastUsed(String choiceKey) {
            return lastUsedAt.get(choiceKey);
        }
    }

    private static final class Progress {
        private final Set<String> flags = new HashSet<>();
        private final Set<String> completedChoices = new HashSet<>();
        private final Map<String, Instant> lastUsedAt = new HashMap<>();

        private Progress copy() {
            Progress copy = new Progress();
            copy.flags.addAll(flags);
            copy.completedChoices.addAll(completedChoices);
            copy.lastUsedAt.putAll(lastUsedAt);
            return copy;
        }
    }
}
