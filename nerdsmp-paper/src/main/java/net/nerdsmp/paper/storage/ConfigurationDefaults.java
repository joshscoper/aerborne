package net.nerdsmp.paper.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Resolve real default-section keys without merging them into an explicit operator-authored map. */
public final class ConfigurationDefaults {
    private ConfigurationDefaults() { }

    public static ConfigurationSection section(FileConfiguration config, String path) {
        return config.isSet(path) ? config.getConfigurationSection(path)
            : config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection(path);
    }
}
