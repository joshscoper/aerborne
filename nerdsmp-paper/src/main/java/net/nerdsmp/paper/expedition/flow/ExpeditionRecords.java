package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who has cleared what. Personal clears unlock the next tier; totals and best times feed the
 * menu and the website. Written only when a run finishes, so it stays small and cheap.
 */
public final class ExpeditionRecords {
    public record Totals(long clears, long bestSeconds, List<String> bestParty) { }

    private final File file;
    private final YamlConfiguration yaml;

    public ExpeditionRecords(File file) {
        this.file = file;
        this.yaml = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public synchronized boolean cleared(String account, String expedition, String tier) {
        return yaml.getLong(personal(account, expedition, tier) + ".clears", 0) > 0;
    }

    public synchronized long clears(String account, String expedition) {
        var section = yaml.getConfigurationSection("players." + account + "." + expedition);
        if (section == null) return 0;
        return section.getKeys(false).stream().mapToLong(tier -> section.getLong(tier + ".clears", 0)).sum();
    }

    /** Records a clear for every member; returns true when the party set a new best time for that tier. */
    public synchronized boolean record(String expedition, String tier, long seconds, Map<String, String> accountsToNames) {
        for (String account : accountsToNames.keySet()) {
            String path = personal(account, expedition, tier);
            yaml.set(path + ".clears", yaml.getLong(path + ".clears", 0) + 1);
            long best = yaml.getLong(path + ".best-seconds", 0);
            if (best == 0 || seconds < best) yaml.set(path + ".best-seconds", seconds);
        }
        String total = "totals." + expedition + "." + tier;
        yaml.set(total + ".clears", yaml.getLong(total + ".clears", 0) + 1);
        long best = yaml.getLong(total + ".best-seconds", 0);
        boolean record = best == 0 || seconds < best;
        if (record) {
            yaml.set(total + ".best-seconds", seconds);
            yaml.set(total + ".best-party", List.copyOf(accountsToNames.values()));
        }
        save();
        return record;
    }

    public synchronized Map<String, Totals> totals(String expedition) {
        Map<String, Totals> totals = new LinkedHashMap<>();
        var section = yaml.getConfigurationSection("totals." + expedition);
        if (section != null) for (String tier : section.getKeys(false))
            totals.put(tier, new Totals(section.getLong(tier + ".clears"), section.getLong(tier + ".best-seconds"),
                section.getStringList(tier + ".best-party")));
        return totals;
    }

    /** Records an unlock (such as the Nether) for a player; true when it is new. */
    public synchronized boolean unlock(String account, String key) {
        List<String> unlocks = new java.util.ArrayList<>(yaml.getStringList("unlocks." + account));
        if (unlocks.contains(key)) return false;
        unlocks.add(key);
        yaml.set("unlocks." + account, unlocks);
        save();
        return true;
    }

    public synchronized boolean unlocked(String account, String key) {
        return yaml.getStringList("unlocks." + account).contains(key);
    }

    private static String personal(String account, String expedition, String tier) {
        return "players." + account + "." + expedition + "." + tier;
    }

    private void save() {
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save expedition records", failure);
        }
    }
}
