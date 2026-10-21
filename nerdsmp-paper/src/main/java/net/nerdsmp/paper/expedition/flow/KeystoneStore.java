package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.content.Keystone;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everyone's current keystone, and the best timed keys: per player and per expedition. Written only
 * when a key is granted or a keyed run ends.
 */
public final class KeystoneStore {
    /** A timed key: its level, the clock it finished on (penalties included) and who ran it. */
    public record Best(int level, long seconds, List<String> party) {
        /** Higher keys beat lower ones; at the same level the faster clear wins. */
        boolean beats(Best other) {
            return other == null || level > other.level || level == other.level && seconds < other.seconds;
        }
    }

    private final File file;
    private final YamlConfiguration yaml;

    public KeystoneStore(File file) {
        this.file = file;
        this.yaml = file != null && file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public synchronized Optional<Keystone.Key> key(String account) {
        String path = "keys." + account;
        String expedition = yaml.getString(path + ".expedition");
        if (expedition == null) return Optional.empty();
        return Optional.of(new Keystone.Key(expedition, yaml.getInt(path + ".level", Keystone.MIN_LEVEL)));
    }

    public synchronized void setKey(String account, Keystone.Key key) {
        String path = "keys." + account;
        yaml.set(path + ".expedition", key.expedition());
        yaml.set(path + ".level", key.level());
        save();
    }

    /**
     * Records a timed key for every member and the expedition; returns true when it is the
     * expedition's new best.
     */
    public synchronized boolean recordTimed(String expedition, int level, long seconds, Map<String, String> accountsToNames) {
        Best run = new Best(level, seconds, List.copyOf(accountsToNames.values()));
        for (String account : accountsToNames.keySet()) {
            String path = "players." + account + "." + expedition;
            if (run.beats(read(path))) write(path, run, false);
        }
        String path = "best." + expedition;
        boolean record = run.beats(read(path));
        if (record) write(path, run, true);
        save();
        return record;
    }

    /** The highest timed key on each expedition. */
    public synchronized Map<String, Best> bests() {
        Map<String, Best> bests = new LinkedHashMap<>();
        var section = yaml.getConfigurationSection("best");
        if (section != null) for (String expedition : section.getKeys(false)) bests.put(expedition, read("best." + expedition));
        return bests;
    }

    public synchronized Optional<Best> best(String expedition) {
        return Optional.ofNullable(read("best." + expedition));
    }

    /** One player's highest timed key on each expedition. */
    public synchronized Map<String, Best> personal(String account) {
        Map<String, Best> bests = new LinkedHashMap<>();
        var section = yaml.getConfigurationSection("players." + account);
        if (section != null) for (String expedition : section.getKeys(false))
            bests.put(expedition, read("players." + account + "." + expedition));
        return bests;
    }

    private Best read(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        return new Best(yaml.getInt(path + ".level"), yaml.getLong(path + ".seconds"), yaml.getStringList(path + ".party"));
    }

    private void write(String path, Best best, boolean party) {
        yaml.set(path + ".level", best.level());
        yaml.set(path + ".seconds", best.seconds());
        if (party) yaml.set(path + ".party", best.party());
    }

    private void save() {
        if (file == null) return;
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save keystones", failure);
        }
    }
}
