package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A compact block plan in world coordinates for one built feature (a town, a ruin, a dungeon). Writes are stored
 * per chunk as packed positions and palette indices, so a city of a few million blocks costs a few megabytes and
 * each generated chunk reads only its own slice. Later writes replace earlier ones at the same spot.
 */
public final class ReachCanvas {
    /** A block state string, e.g. {@code minecraft:oak_stairs[facing=north]}. */
    public record Spec(String state) {
        public static Spec of(Material material, String properties) {
            String base = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
            return new Spec(properties == null || properties.isEmpty() ? base : base + "[" + properties + "]");
        }
    }

    /** A building's footprint and floor, recorded so tests can prove no terrain stands inside it. */
    public record Lot(int minX, int minZ, int maxX, int maxZ, int floor) { }

    private final List<Lot> lots = new ArrayList<>();

    public void lot(int minX, int minZ, int maxX, int maxZ, int floor) { lots.add(new Lot(minX, minZ, maxX, maxZ, floor)); }

    private final List<Spec> palette = new ArrayList<>();
    private final Map<Spec, Short> paletteIndex = new HashMap<>();
    private final Map<Long, Map<Integer, Short>> chunks = new HashMap<>();
    private int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public void set(int x, int y, int z, Material material) { set(x, y, z, material, ""); }

    public void set(int x, int y, int z, Material material, String properties) {
        set(x, y, z, Spec.of(material, properties));
    }

    public void set(int x, int y, int z, Spec spec) {
        if (y < -64 || y > 319) throw new IllegalArgumentException("Height " + y + " is outside the world");
        Short index = paletteIndex.get(spec);
        if (index == null) {
            if (palette.size() >= Short.MAX_VALUE) throw new IllegalStateException("Palette full");
            index = (short) palette.size();
            palette.add(spec);
            paletteIndex.put(spec, index);
        }
        chunks.computeIfAbsent(key(x >> 4, z >> 4), k -> new HashMap<>()).put(pack(x & 15, y, z & 15), index);
        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
    }

    /** What has been written here so far, or null. */
    public Spec get(int x, int y, int z) {
        Map<Integer, Short> chunk = chunks.get(key(x >> 4, z >> 4));
        if (chunk == null) return null;
        Short index = chunk.get(pack(x & 15, y, z & 15));
        return index == null ? null : palette.get(index);
    }

    public boolean has(int x, int y, int z) { return get(x, y, z) != null; }

    public void box(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, material);
    }

    public int size() { return chunks.values().stream().mapToInt(Map::size).sum(); }
    public boolean empty() { return chunks.isEmpty(); }
    public int minX() { return minX; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxZ() { return maxZ; }

    /** A frozen, read-only copy for the generator. */
    public Frozen freeze() { return new Frozen(this); }

    static int pack(int lx, int y, int lz) { return (lx << 14) | (lz << 10) | (y + 64); }
    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** One chunk's blocks: local x, y, local z and the state. */
    public record Cell(int lx, int y, int lz, Spec spec) { }

    public static final class Frozen {
        private final Spec[] palette;
        private final Map<Long, int[]> positions = new HashMap<>();
        private final Map<Long, short[]> states = new HashMap<>();
        /** Per chunk, which of its 256 columns hold any block of this plan (air included), as four longs. */
        private final Map<Long, long[]> columns = new HashMap<>();
        final int minX, minZ, maxX, maxZ;
        final List<Lot> lots;

        /** True when this plan writes anything in the column at (x, z). */
        public boolean occupies(int x, int z) {
            long[] bits = columns.get(key(x >> 4, z >> 4));
            if (bits == null) return false;
            int i = ((z & 15) << 4) | (x & 15);
            return (bits[i >> 6] & (1L << (i & 63))) != 0;
        }

        private Frozen(ReachCanvas canvas) {
            palette = canvas.palette.toArray(new Spec[0]);
            canvas.chunks.forEach((key, cells) -> {
                int[] p = new int[cells.size()];
                short[] s = new short[cells.size()];
                int i = 0;
                long[] bits = new long[4];
                for (Map.Entry<Integer, Short> e : cells.entrySet()) {
                    p[i] = e.getKey(); s[i] = e.getValue(); i++;
                    int col = ((e.getKey() >> 10 & 15) << 4) | (e.getKey() >> 14 & 15);
                    bits[col >> 6] |= 1L << (col & 63);
                }
                positions.put(key, p);
                states.put(key, s);
                columns.put(key, bits);
            });
            minX = canvas.minX; minZ = canvas.minZ; maxX = canvas.maxX; maxZ = canvas.maxZ;
            lots = List.copyOf(canvas.lots);
        }

        public boolean touches(int chunkX, int chunkZ) { return positions.containsKey(key(chunkX, chunkZ)); }

        public void forEach(int chunkX, int chunkZ, java.util.function.Consumer<Cell> action) {
            int[] p = positions.get(key(chunkX, chunkZ));
            if (p == null) return;
            short[] s = states.get(key(chunkX, chunkZ));
            for (int i = 0; i < p.length; i++)
                action.accept(new Cell((p[i] >> 14) & 15, (p[i] & 1023) - 64, (p[i] >> 10) & 15, palette[s[i]]));
        }

        public Spec get(int x, int y, int z) {
            int[] p = positions.get(key(x >> 4, z >> 4));
            if (p == null) return null;
            int wanted = pack(x & 15, y, z & 15);
            for (int i = 0; i < p.length; i++) if (p[i] == wanted) return palette[states.get(key(x >> 4, z >> 4))[i]];
            return null;
        }
    }
}
