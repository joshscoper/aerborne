package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Weekly raid loot lockouts. Each boss room (and the finish) pays each player once per tier per
 * week; after that they can still help their friends, but the loot is theirs only next week.
 * Weeks are the keystone weeks (Monday UTC), so raids and affixes reset together. Old weeks are
 * dropped on load.
 */
public final class RaidLockouts {
    /** The slot for the raid's completion reward. */
    public static final String FINISH = "finish";

    private final File file;
    private final YamlConfiguration yaml;

    public RaidLockouts(File file, long currentWeek) {
        this.file = file;
        this.yaml = file != null && file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        var weeks = yaml.getConfigurationSection("weeks");
        if (weeks != null) for (String week : weeks.getKeys(false)) {
            try {
                if (Long.parseLong(week) < currentWeek) yaml.set("weeks." + week, null);
            } catch (NumberFormatException junk) {
                yaml.set("weeks." + week, null);
            }
        }
    }

    /** Claims a slot's loot for this player this week; false when they already have. */
    public synchronized boolean claim(String account, String expedition, String tier, String slot, long week) {
        String path = path(account, expedition, tier, week);
        List<String> claimed = new ArrayList<>(yaml.getStringList(path));
        if (claimed.contains(slot)) return false;
        claimed.add(slot);
        yaml.set(path, claimed);
        save();
        return true;
    }

    /** The slots this player has looted this week on that raid and tier. */
    public synchronized Set<String> claimed(String account, String expedition, String tier, long week) {
        return new LinkedHashSet<>(yaml.getStringList(path(account, expedition, tier, week)));
    }

    private static String path(String account, String expedition, String tier, long week) {
        return "weeks." + week + "." + account + "." + expedition + "." + tier;
    }

    private void save() {
        if (file == null) return;
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save raid lockouts", failure);
        }
    }
}
