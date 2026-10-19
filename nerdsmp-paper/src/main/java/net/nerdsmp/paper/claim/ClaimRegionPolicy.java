package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.ChunkKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.function.Predicate;

/** Immutable, block-read-free reserve checks; constructed on the server thread. */
public final class ClaimRegionPolicy implements Predicate<ChunkKey> {
    private final Map<UUID, Bounds> borders = new HashMap<>();
    private final List<Region> regions = new ArrayList<>();

    public ClaimRegionPolicy(FileConfiguration config, List<World> worlds) {
        for (World world : worlds) {
            var border = world.getWorldBorder();
            double half = border.getSize() / 2D;
            var center = border.getCenter();
            borders.put(world.getUID(), new Bounds(center.getX() - half, center.getZ() - half,
                center.getX() + half, center.getZ() + half));
            int buffer = Math.max(0, config.getInt("claims.spawn-buffer-blocks", 0));
            if (buffer > 0 && config.getStringList("claims.spawn-worlds").contains(world.getName())) {
                var spawn = world.getSpawnLocation();
                regions.add(new Region(world.getUID(), Math.floorDiv(spawn.getBlockX() - buffer, 16),
                    Math.floorDiv(spawn.getBlockZ() - buffer, 16), Math.floorDiv(spawn.getBlockX() + buffer, 16),
                    Math.floorDiv(spawn.getBlockZ() + buffer, 16)));
            }
        }
        for (Map<?, ?> data : config.getMapList("claims.reserved-regions")) {
            String worldName = Objects.toString(data.get("world"), "");
            World world = worlds.stream().filter(w -> w.getName().equals(worldName)
                || w.getUID().toString().equals(worldName)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("Unknown claims.reserved-regions world: " + worldName));
            int buffer = data.containsKey("buffer-chunks") ? coordinate(data, "buffer-chunks") : 0;
            if (buffer < 0 || buffer > 1024) throw new IllegalArgumentException("Invalid reserve buffer");
            int minX = Math.subtractExact(coordinate(data, "min-chunk-x"), buffer);
            int minZ = Math.subtractExact(coordinate(data, "min-chunk-z"), buffer);
            int maxX = Math.addExact(coordinate(data, "max-chunk-x"), buffer);
            int maxZ = Math.addExact(coordinate(data, "max-chunk-z"), buffer);
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Reversed reserve coordinates");
            regions.add(new Region(world.getUID(), minX, minZ, maxX, maxZ));
        }
    }

    @Override public boolean test(ChunkKey chunk) {
        Bounds border = borders.get(chunk.worldId());
        if (border == null || !border.contains(chunk)) return true;
        return regions.stream().anyMatch(region -> region.contains(chunk));
    }

    private static int coordinate(Map<?, ?> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("Reserve requires an integer " + key);
        }
        return number.intValue();
    }

    record Region(UUID world, int minX, int minZ, int maxX, int maxZ) {
        boolean contains(ChunkKey chunk) {
            return world.equals(chunk.worldId()) && chunk.x() >= minX && chunk.x() <= maxX
                && chunk.z() >= minZ && chunk.z() <= maxZ;
        }
    }
    record Bounds(double minX, double minZ, double maxX, double maxZ) {
        boolean contains(ChunkKey chunk) {
            return chunk.x() * 16D >= minX && chunk.z() * 16D >= minZ
                && chunk.x() * 16D + 16 <= maxX && chunk.z() * 16D + 16 <= maxZ;
        }
    }
}
