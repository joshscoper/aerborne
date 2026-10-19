package net.nerdsmp.paper.market;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Objects;

/** Store branding is additive; items and money remain in the durable market journal. */
public final class PlayerStoreProfiles {
    private final PlayerDataStore data;
    private final String path;
    public PlayerStoreProfiles(PlayerDataStore data, String season) {
        this.data = Objects.requireNonNull(data);
        if (season == null || !season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid store season");
        path = "seasons." + season + ".player-store";
    }
    public record Profile(String name, String description) {
        public Profile {
            if (name == null || name.isBlank() || !name.matches("[\\p{L}\\p{N} _'-]{3,48}")) throw new IllegalArgumentException("Use 3-48 letters, numbers or spaces for the store name.");
            if (description == null || description.length() > 120 || description.codePoints().anyMatch(Character::isISOControl)
                || description.indexOf('\u2028') >= 0 || description.indexOf('\u2029') >= 0
                || description.contains("§")) throw new IllegalArgumentException("Use one plain line of up to 120 characters for the description.");
        }
    }
    public Profile profile(AccountId owner) {
        return data.read(owner, this::profile);
    }
    private Profile profile(YamlConfiguration yaml) {
        if (!yaml.contains(path)) {
            String playerName = yaml.getString("identity.last-known-name", "Player");
            String safe = playerName.replaceAll("[^\\p{L}\\p{N} _'-]", "");
            if (safe.isBlank()) safe = "Player";
            return new Profile(safe.substring(0, Math.min(36, safe.length())) + "'s Store", "Supplies offered by another player.");
        }
        var section = yaml.getConfigurationSection(path);
        if (section == null || !section.isInt("schema") || section.getInt("schema") != 1
            || !section.isString("name") || !section.isString("description"))
            throw new IllegalStateException("Invalid store profile; refusing to replace it.");
        return new Profile(section.getString("name"), section.getString("description"));
    }
    public void update(AccountId owner, Profile expected, Profile next) {
        Objects.requireNonNull(next, "next");
        data.update(owner, yaml -> {
            if (!profile(yaml).equals(expected)) throw new IllegalArgumentException("Your store changed. Open it again.");
            yaml.set(path + ".schema", 1); yaml.set(path + ".name", next.name()); yaml.set(path + ".description", next.description());
        });
    }
}
