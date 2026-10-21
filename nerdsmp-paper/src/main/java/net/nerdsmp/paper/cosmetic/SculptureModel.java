package net.nerdsmp.paper.cosmetic;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A companion sculpture read from companion-models.yml: small blocks positioned in pixels (16 = one block),
 * standing at 0,0,0 and facing +Z. See the comments at the top of that file for the format.
 */
public record SculptureModel(String name, String bounce, Map<String, double[]> pivots, List<Part> parts) {
    public static final Set<String> GROUPS = Set.of("", "leg_a", "leg_b", "tail", "head", "ear", "wing_left", "wing_right", "flame");
    public static final Set<String> BOUNCES = Set.of("none", "trot", "hop");
    /** Keeps companions cheap to show: each part is one display entity. */
    public static final int MAX_PARTS = 32;

    /** One block of the sculpture: its centre and size in pixels. */
    public record Part(String block, double x, double y, double z, double width, double height, double depth, String group, boolean glow) {
        public boolean animated() { return !group.isEmpty(); }
    }

    public SculptureModel {
        pivots = Map.copyOf(pivots);
        parts = List.copyOf(parts);
    }

    /** Reads every model in a companion-models.yml document. Throws with a readable message when one is malformed. */
    public static Map<String, SculptureModel> read(YamlConfiguration yaml) {
        Map<String, SculptureModel> models = new LinkedHashMap<>();
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            if (section == null) throw new IllegalArgumentException(name + " must be a section");
            String bounce = section.getString("bounce", "none").toLowerCase(Locale.ROOT);
            if (!BOUNCES.contains(bounce)) throw new IllegalArgumentException(name + ": bounce must be none, trot or hop");
            Map<String, double[]> pivots = new LinkedHashMap<>();
            ConfigurationSection pivotSection = section.getConfigurationSection("pivots");
            if (pivotSection != null) {
                for (String group : pivotSection.getKeys(false)) {
                    if (!GROUPS.contains(group) || group.isEmpty()) throw new IllegalArgumentException(name + ": unknown pivot group " + group);
                    pivots.put(group, triple(pivotSection.getList(group), name + " pivot " + group));
                }
            }
            List<Part> parts = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("parts")) {
                String where = name + " part " + (parts.size() + 1);
                Object block = raw.get("block");
                if (!(block instanceof String id) || id.isBlank()) throw new IllegalArgumentException(where + ": block is missing");
                double[] at = triple(raw.get("at"), where + " at");
                double[] size = triple(raw.get("size"), where + " size");
                if (size[0] <= 0 || size[1] <= 0 || size[2] <= 0 || size[0] > 64 || size[1] > 64 || size[2] > 64) {
                    throw new IllegalArgumentException(where + ": sizes must be between 0 and 64 pixels");
                }
                String group = raw.get("group") == null ? "" : raw.get("group").toString().toLowerCase(Locale.ROOT);
                if (!GROUPS.contains(group)) throw new IllegalArgumentException(where + ": unknown group " + group);
                boolean glow = Boolean.TRUE.equals(raw.get("glow"));
                parts.add(new Part(id.toUpperCase(Locale.ROOT), at[0], at[1], at[2], size[0], size[1], size[2], group, glow));
            }
            if (parts.isEmpty()) throw new IllegalArgumentException(name + " has no parts");
            if (parts.size() > MAX_PARTS) throw new IllegalArgumentException(name + " has more than " + MAX_PARTS + " parts");
            models.put(name.toLowerCase(Locale.ROOT), new SculptureModel(name.toLowerCase(Locale.ROOT), bounce, pivots, parts));
        }
        return models;
    }

    /** The server's models, plus any built-in model the server's file doesn't have yet. */
    public record Merge(Map<String, SculptureModel> models, List<String> added) {}

    /** Server edits win; built-in models only fill gaps, for example a companion added in a plugin update. */
    public static Merge merge(Map<String, SculptureModel> builtIn, Map<String, SculptureModel> custom) {
        Map<String, SculptureModel> models = new LinkedHashMap<>(custom);
        List<String> added = new ArrayList<>();
        builtIn.forEach((name, model) -> {
            if (models.putIfAbsent(name, model) == null) added.add(name);
        });
        return new Merge(Map.copyOf(models), List.copyOf(added));
    }

    private static double[] triple(Object raw, String where) {
        if (!(raw instanceof List<?> list) || list.size() != 3) throw new IllegalArgumentException(where + " must be [x, y, z]");
        double[] out = new double[3];
        for (int index = 0; index < 3; index++) {
            if (!(list.get(index) instanceof Number number)) throw new IllegalArgumentException(where + " must contain numbers");
            out[index] = number.doubleValue();
        }
        return out;
    }
}
