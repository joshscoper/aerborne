package net.nerdsmp.paper.digsite;

import net.nerdsmp.paper.resource.ResourceMaterials;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable staff-authored excavation cells; world and zone availability are runtime checks. */
public record DigSiteDefinition(String id, String zoneId, UUID worldId, UUID generation,
                                boolean enabled, int cooldownSeconds, List<Node> nodes) {
    public static final int MAX_SITES = 64;
    public static final int MAX_NODES = 2_048;
    public static final int MAX_TOTAL_NODES = 8_192;
    public static final int DEFAULT_COOLDOWN_SECONDS = 20;
    public static final int WORLD_COORDINATE_LIMIT = 29_999_984;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;

    public DigSiteDefinition {
        if (!validId(id) || !validId(zoneId)) {
            throw new IllegalArgumentException("Dig-site and zone IDs use 1–48 lowercase letters, numbers, underscores or dashes.");
        }
        Objects.requireNonNull(worldId, "Dig-site world UUID is required");
        Objects.requireNonNull(generation, "Dig-site generation UUID is required");
        if (cooldownSeconds < 5 || cooldownSeconds > 3_600) {
            throw new IllegalArgumentException("Dig-site cooldown must be 5–3,600 seconds.");
        }
        nodes = List.copyOf(Objects.requireNonNull(nodes, "Dig-site nodes are required"));
        if (nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException("A dig site supports at most 2,048 nodes.");
        }
        if (enabled && nodes.isEmpty()) {
            throw new IllegalArgumentException("Add at least one node before enabling a dig site.");
        }
        HashSet<Position> occupied = new HashSet<>();
        for (Node node : nodes) {
            if (!occupied.add(new Position(node.x(), node.y(), node.z()))) {
                throw new IllegalArgumentException("Dig-site nodes cannot occupy the same position.");
            }
        }
    }

    public static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9][a-z0-9_-]{0,47}");
    }

    /** Block data is syntax-checked here and registry-checked by the live Paper service. */
    public record Node(int x, int y, int z, String material, String blockData) {
        public Node {
            if (x < -WORLD_COORDINATE_LIMIT || x > WORLD_COORDINATE_LIMIT
                || z < -WORLD_COORDINATE_LIMIT || z > WORLD_COORDINATE_LIMIT || y < MIN_Y || y > MAX_Y) {
                throw new IllegalArgumentException("Dig-site nodes must be within the world limits (Y -64 to 319).");
            }
            if (material == null || !ResourceMaterials.excavation(material)
                || material.equals("SUSPICIOUS_SAND") || material.equals("SUSPICIOUS_GRAVEL")) {
                throw new IllegalArgumentException("Dig-site nodes require ordinary excavation soil, not loot-bearing blocks.");
            }
            String prefix = "minecraft:" + material.toLowerCase(Locale.ROOT);
            if (blockData == null || blockData.length() > 256 || !blockData.startsWith(prefix)) {
                throw new IllegalArgumentException("Dig-site block data must match its material and use at most 256 characters.");
            }
            String properties = blockData.substring(prefix.length());
            if (!properties.isEmpty()) {
                if (!properties.matches("\\[[a-z0-9_]+=[a-z0-9_.-]+(?:,[a-z0-9_]+=[a-z0-9_.-]+)*\\]")) {
                    throw new IllegalArgumentException("Dig-site block data must use canonical Minecraft property syntax.");
                }
                HashSet<String> keys = new HashSet<>();
                for (String property : properties.substring(1, properties.length() - 1).split(",")) {
                    if (!keys.add(property.substring(0, property.indexOf('=')))) {
                        throw new IllegalArgumentException("Dig-site block data cannot repeat a property.");
                    }
                }
            }
        }
    }

    private record Position(int x, int y, int z) { }
}
