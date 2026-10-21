package net.nerdsmp.paper.campaign;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Server-wide feature unlocks. They come from season milestones ({@code {type: unlock, feature: ...}});
 * unlocks recorded by the older campaign in {@code server-progression.yml} stay unlocked. The Nether and
 * End are no longer gated here: vanilla portals are sealed and those realms are reached through
 * expeditions, which the season can seal behind a milestone instead.
 */
public final class ServerProgressionService {
    private final NerdSmpPlugin plugin;
    private final Set<Unlock> legacy = EnumSet.noneOf(Unlock.class);

    public ServerProgressionService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "server-progression.yml");
        String season = plugin.getConfig().getString("season.id", "season-1");
        if (file.isFile()) {
            YamlConfiguration yaml = net.nerdsmp.paper.storage.SeasonalYaml.load(file, season, "unlocked");
            for (String raw : yaml.getStringList("seasons." + season + ".unlocked")) {
                try {
                    legacy.add(Unlock.valueOf(raw));
                } catch (IllegalArgumentException unknown) {
                    plugin.getLogger().warning("Ignoring unknown recorded unlock " + raw);
                }
            }
        }
    }

    public boolean unlocked(Unlock unlock) {
        return legacy.contains(unlock) || plugin.campaign() != null && plugin.campaign().featureUnlocked(unlock.key());
    }

    public boolean worldEventsUnlocked() {
        return unlocked(Unlock.WORLD_EVENTS);
    }

    public enum Unlock {
        WORLD_EVENTS("World Events", Material.ENDER_EYE, "Rifts and ambient frontier events begin appearing."),
        NETHER("The Nether", Material.OBSIDIAN, "Nether frontier routes open."),
        END("The End", Material.END_PORTAL_FRAME, "End frontier routes open.");

        private final String displayName;
        private final Material material;
        private final String description;

        Unlock(String displayName, Material material, String description) {
            this.displayName = displayName;
            this.material = material;
            this.description = description;
        }

        public String displayName() { return displayName; }
        public Material material() { return material; }
        public String description() { return description; }
        /** The feature name a season reward uses, such as {@code world-events}. */
        public String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    }
}
