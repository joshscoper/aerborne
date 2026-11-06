package net.nerdsmp.paper.reach;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand edits to the Reach, kept as an overlay on the generated world: each one a block position with the block it
 * should be (and what the generator put there first), plus a sign's words where a sign was lettered. Edits made in
 * the editor are saved to {@code reach-edits.yml} in the plugin's folder; a copy bundled at {@code reach/edits.yml}
 * bakes them into the map for every server. The overlay is laid down last whenever a chunk is generated, and again
 * after a feature rebuild, so a fix made by hand survives the world being generated afresh.
 */
public final class ReachEdits {
    /** One hand edit. {@code lines} is null unless the block is a lettered sign. */
    public record Edit(int x, int y, int z, String after, String before, List<String> lines) { }

    private final File file;
    private final Map<Long, Edit> edits = new ConcurrentHashMap<>();
    private final Map<Long, List<Edit>> byChunk = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    ReachEdits(File file, InputStream bundled) {
        this.file = file;
        if (bundled != null) try (InputStreamReader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
            read(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException ignored) { }
        if (file != null && file.isFile()) read(YamlConfiguration.loadConfiguration(file));
        reindex();
    }

    private void read(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("edits");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(key);
            if (e == null) continue;
            String[] p = key.split("_");
            if (p.length != 3) continue;
            try {
                int x = Integer.parseInt(p[0].replace('m', '-')), y = Integer.parseInt(p[1].replace('m', '-')), z = Integer.parseInt(p[2].replace('m', '-'));
                List<String> lines = e.isList("lines") ? e.getStringList("lines") : null;
                edits.put(key(x, y, z), new Edit(x, y, z, e.getString("after", "minecraft:air"), e.getString("before", "minecraft:air"), lines));
            } catch (NumberFormatException ignored) { }
        }
    }

    static long key(int x, int y, int z) { return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFF); }

    private static long chunk(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    private void reindex() {
        byChunk.clear();
        for (Edit e : edits.values()) byChunk.computeIfAbsent(chunk(e.x() >> 4, e.z() >> 4), k -> new ArrayList<>()).add(e);
    }

    public Edit get(int x, int y, int z) { return edits.get(key(x, y, z)); }

    public Collection<Edit> all() { return edits.values(); }

    public int size() { return edits.size(); }

    /** Records an edit; one that puts back what the generator made is no edit at all and is dropped. */
    public synchronized void put(Edit edit) {
        long k = key(edit.x(), edit.y(), edit.z());
        if (edit.after().equals(edit.before()) && edit.lines() == null) edits.remove(k);
        else edits.put(k, edit);
        reindex();
        dirty = true;
    }

    public synchronized void remove(int x, int y, int z) {
        edits.remove(key(x, y, z));
        reindex();
        dirty = true;
    }

    public synchronized void save() {
        if (!dirty || file == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Hand edits to the Wardens' Reach, laid over the generated world (ReachEdits).",
            "Copy this file to nerdsmp-paper/src/main/resources/reach/edits.yml to bake the edits into the map."));
        for (Edit e : edits.values()) {
            String path = "edits." + name(e.x()) + "_" + name(e.y()) + "_" + name(e.z());
            yaml.set(path + ".after", e.after());
            yaml.set(path + ".before", e.before());
            if (e.lines() != null) yaml.set(path + ".lines", e.lines());
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException failure) {
            Bukkit.getLogger().warning("Could not save " + file.getName() + ": " + failure.getMessage());
        }
    }

    private static String name(int v) { return v < 0 ? "m" + (-v) : String.valueOf(v); }

    /** Lays this chunk's edits into a live chunk (after a rebuild). */
    void apply(Chunk chunk) {
        List<Edit> list = byChunk.get(chunk(chunk.getX(), chunk.getZ()));
        if (list == null) return;
        for (Edit e : list) {
            Block block = chunk.getBlock(e.x() & 15, e.y(), e.z() & 15);
            BlockData data = parse(e.after());
            if (data != null && !block.getBlockData().equals(data)) block.setBlockData(data, false);
            if (e.lines() != null && block.getState() instanceof org.bukkit.block.Sign sign) {
                letter(sign, e.lines());
                sign.update(true, false);
            }
        }
    }

    private static BlockData parse(String state) {
        try { return Bukkit.createBlockData(state); } catch (IllegalArgumentException bad) { return null; }
    }

    private static void letter(org.bukkit.block.Sign sign, List<String> lines) {
        for (org.bukkit.block.sign.Side side : org.bukkit.block.sign.Side.values()) {
            org.bukkit.block.sign.SignSide face = sign.getSide(side);
            for (int i = 0; i < 4; i++) face.line(i, net.kyori.adventure.text.Component.text(i < lines.size() ? lines.get(i) : ""));
            face.setGlowingText(true);
        }
        sign.setWaxed(true);
    }

    /** The last pass of generation: every hand edit in the chunk, laid over what the generator and its plants made. */
    BlockPopulator populator() {
        return new BlockPopulator() {
            @Override
            public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
                List<Edit> list = byChunk.get(chunk(chunkX, chunkZ));
                if (list == null) return;
                for (Edit e : list) {
                    if (!region.isInRegion(e.x(), e.y(), e.z())) continue;
                    BlockData data = parse(e.after());
                    if (data == null) continue;
                    region.setBlockData(e.x(), e.y(), e.z(), data);
                    if (e.lines() != null && region.getBlockState(e.x(), e.y(), e.z()) instanceof org.bukkit.block.Sign sign) {
                        letter(sign, e.lines());
                        region.setBlockState(e.x(), e.y(), e.z(), sign);
                    }
                }
            }
        };
    }
}
