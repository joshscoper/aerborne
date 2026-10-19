package net.nerdsmp.paper.item;

import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/** Validate before publishing; fill missing IDs without replacing any operator-authored entry. */
final class RelicCatalogFiles {
    private RelicCatalogFiles() { }

    static YamlConfiguration load(File file, YamlConfiguration bundled, int version,
                                  Consumer<YamlConfiguration> validateContent) throws Exception {
        if (file.exists() && !file.isFile()) throw new IllegalStateException("Relic catalog is not a regular file");
        YamlConfiguration current = new YamlConfiguration();
        boolean exists = file.exists();
        if (exists) current.load(file); else current.loadFromString(bundled.saveToString());
        if (!current.isInt("schema-version") || current.getInt("schema-version") < 1
            || current.getInt("schema-version") > version) throw new IllegalStateException("Unsupported relic schema");
        int previous = current.getInt("schema-version");
        for (String section : List.of("sets", "relics")) {
            if (!current.isConfigurationSection(section) || !bundled.isConfigurationSection(section))
                throw new IllegalStateException("Missing relic catalog section: " + section);
            if (previous < version) {
                var defaults = bundled.getConfigurationSection(section);
                for (String id : defaults.getKeys(false)) {
                    String path = section + "." + id;
                    if (!current.contains(path)) current.set(path, defaults.get(id));
                }
            }
        }
        current.set("schema-version", version);
        validateContent.accept(current);
        if (!exists || previous < version) {
            if (exists) {
                File backup = new File(file.getParentFile(), file.getName() + ".before-v" + version + ".bak");
                if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
            }
            AtomicYamlFiles.save(current, file);
        }
        return current;
    }
}
