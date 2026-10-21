package net.nerdsmp.paper.expedition.content;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A difficulty tier. Global tiers live in config.yml ({@code expeditions.tiers}); an expedition can
 * disable one or change its level, lives, loot rolls and modifiers.
 */
public record Tier(String id, String title, int levelBonus, double health, double damage, int lives, int lootRolls,
                   double marks, double campaign, String requires, List<String> modifiers) {
    public Tier {
        modifiers = List.copyOf(modifiers);
    }

    public static final Map<String, Tier> DEFAULTS = defaults();

    private static Map<String, Tier> defaults() {
        Map<String, Tier> tiers = new LinkedHashMap<>();
        tiers.put("normal", new Tier("normal", "Normal", 0, 1.0, 1.0, 10, 1, 1.0, 1.0, null, List.of()));
        tiers.put("heroic", new Tier("heroic", "Heroic", 8, 1.6, 1.3, 6, 2, 1.5, 2.0, "normal", List.of()));
        tiers.put("mythic", new Tier("mythic", "Mythic", 16, 2.4, 1.6, 3, 3, 2.5, 4.0, "heroic", List.of("fortified", "timed")));
        return java.util.Collections.unmodifiableMap(tiers);
    }

    /** Reads {@code expeditions.tiers}, falling back to the built-in values for any missing key. */
    public static Map<String, Tier> load(ConfigurationSection section) {
        if (section == null) return DEFAULTS;
        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            if (!id.matches("[a-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid tier id '" + id + "'.");
            ConfigurationSection tier = section.getConfigurationSection(id);
            if (tier == null) throw new IllegalArgumentException("Tier '" + id + "' must be a section.");
            Tier base = DEFAULTS.getOrDefault(id, DEFAULTS.get("normal"));
            String title = DEFAULTS.containsKey(id) ? base.title() : Character.toUpperCase(id.charAt(0)) + id.substring(1);
            Tier loaded = new Tier(id, tier.getString("title", title),
                tier.getInt("level-bonus", base.levelBonus()), tier.getDouble("health-multiplier", base.health()),
                tier.getDouble("damage-multiplier", base.damage()), tier.getInt("lives", base.lives()),
                tier.getInt("loot-rolls", base.lootRolls()), tier.getDouble("marks-multiplier", base.marks()),
                tier.getDouble("campaign-multiplier", base.campaign()),
                tier.contains("requires") ? blankToNull(tier.getString("requires")) : base.requires(),
                tier.contains("modifiers") ? lower(tier.getStringList("modifiers")) : base.modifiers());
            if (loaded.health() <= 0 || loaded.damage() <= 0 || loaded.lives() < 1 || loaded.lives() > 50
                || loaded.lootRolls() < 0 || loaded.lootRolls() > 10 || loaded.levelBonus() < 0 || loaded.levelBonus() > 80)
                throw new IllegalArgumentException("Tier '" + id + "' has out-of-range values.");
            tiers.put(id, loaded);
        }
        for (Tier tier : tiers.values())
            if (tier.requires() != null && !tiers.containsKey(tier.requires()))
                throw new IllegalArgumentException("Tier '" + tier.id() + "' requires unknown tier '" + tier.requires() + "'.");
        if (tiers.isEmpty()) throw new IllegalArgumentException("Define at least one expedition tier.");
        return java.util.Collections.unmodifiableMap(tiers);
    }

    /** Applies an expedition's override; returns null when the expedition disables this tier. */
    public Tier apply(ExpeditionDefinition.TierOverride override, Integer expeditionLives) {
        // An expedition's own lives value sets Normal; other tiers keep their ratio to Normal's ten.
        int baseLives = expeditionLives == null ? lives : Math.max(1, Math.round(expeditionLives * (lives / 10F)));
        if (override == null) return expeditionLives == null ? this : with(levelBonus, baseLives, lootRolls, modifiers);
        if (Boolean.FALSE.equals(override.enabled())) return null;
        return with(override.levelBonus() == null ? levelBonus : override.levelBonus(),
            override.lives() == null ? baseLives : override.lives(),
            override.lootRolls() == null ? lootRolls : override.lootRolls(),
            override.modifiers() == null ? modifiers : override.modifiers());
    }

    private Tier with(int level, int livesValue, int rolls, List<String> mods) {
        return new Tier(id, title, level, health, damage, livesValue, rolls, marks, campaign, requires, mods);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT); }

    private static List<String> lower(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) result.add(value.toLowerCase(Locale.ROOT));
        return result;
    }
}
