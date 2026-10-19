package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Vanilla food values are retained; only these modest timed meal effects are supported. */
record HarvestDish(String id, String name, Material material, int level, List<String> shape,
                   Map<Character, Material> ingredients, String effect, int seconds) {
    private static final Set<String> EFFECTS = Set.of("speed", "haste", "water_breathing", "night_vision", "regeneration", "resistance", "jump_boost", "fire_resistance", "slow_falling", "luck");
    HarvestDish {
        if (id == null || !id.matches("[a-z0-9_]{1,48}") || name == null || name.isBlank()
            || name.length() > 48 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 167)
            || material == null || air(material) || level < 1 || level > 50
            || !EFFECTS.contains(effect) || seconds < 5 || seconds > 120) throw new IllegalArgumentException("Invalid meal definition: " + id);
        validateShape(shape, ingredients);
        shape = List.copyOf(shape); ingredients = Map.copyOf(ingredients);
    }
    static void validateShape(List<String> shape, Map<Character, Material> ingredients) {
        if (shape == null || shape.isEmpty() || shape.size() > 3 || shape.getFirst().isEmpty() || shape.getFirst().length() > 3
            || shape.stream().anyMatch(row -> row.length() != shape.getFirst().length())) throw new IllegalArgumentException("Recipe rows must have matching widths of one to three slots");
        Set<Character> used = new HashSet<>();
        shape.forEach(row -> row.chars().filter(c -> c != ' ').forEach(c -> used.add((char)c)));
        if (used.isEmpty() || !used.equals(ingredients.keySet()) || ingredients.containsKey(' ')
            || ingredients.values().stream().anyMatch(m -> m == null || air(m))) throw new IllegalArgumentException("Recipe symbols and ingredients must match exactly");
    }
    static Map<Character, Material> ingredients(ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("Missing recipe ingredients");
        Map<Character, Material> result = new LinkedHashMap<>();
        for (String symbol : section.getKeys(false)) {
            if (symbol.length() != 1) throw new IllegalArgumentException("Ingredient symbols must be one character");
            Material material = Material.matchMaterial(section.getString(symbol, ""));
            if (material == null || air(material)) throw new IllegalArgumentException("Invalid recipe ingredient " + symbol);
            result.put(symbol.charAt(0), material);
        }
        return Map.copyOf(result);
    }
    static List<HarvestDish> load(YamlConfiguration yaml) {
        ConfigurationSection root = Objects.requireNonNull(yaml.getConfigurationSection("meals"), "Missing meals");
        List<HarvestDish> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = Objects.requireNonNull(root.getConfigurationSection(id), "Invalid meal " + id);
            result.add(new HarvestDish(id, s.getString("name"), Material.matchMaterial(s.getString("material", "")),
                s.getInt("level"), s.getStringList("shape"), ingredients(s.getConfigurationSection("ingredients")),
                s.getString("effect", ""), s.getInt("seconds")));
        }
        return result.stream().sorted(Comparator.comparingInt(HarvestDish::level).thenComparing(HarvestDish::id)).toList();
    }
    private static boolean air(Material material) { return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR; }
}
