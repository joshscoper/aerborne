package net.nerdsmp.paper.skyblock;

/** Fixed, versioned layout. Never change these constants for an existing island world. */
public final class IslandGrid {
    public static final int SPACING_CHUNKS = 32;
    public static final int COLUMNS = 1000;
    public static final int LIMIT = 100_000;
    public static final int SURFACE_Y = 96;
    private IslandGrid() { }

    public static int chunkX(int slot) { check(slot); return (slot % COLUMNS) * SPACING_CHUNKS; }
    public static int chunkZ(int slot) { check(slot); return (slot / COLUMNS) * SPACING_CHUNKS; }
    public static int blockX(int slot) { return chunkX(slot) * 16 + 8; }
    public static int blockZ(int slot) { return chunkZ(slot) * 16 + 8; }
    public static int slotAt(int chunkX, int chunkZ) {
        int x = Math.floorDiv(chunkX + SPACING_CHUNKS / 2, SPACING_CHUNKS);
        int z = Math.floorDiv(chunkZ + SPACING_CHUNKS / 2, SPACING_CHUNKS);
        if (x < 0 || x >= COLUMNS || z < 0 || z >= LIMIT / COLUMNS) return -1;
        return z * COLUMNS + x;
    }
    public static boolean contains(int slot, int tier, int x, int z) {
        int radius = tier + 1;
        return Math.abs((long) x - chunkX(slot)) <= radius && Math.abs((long) z - chunkZ(slot)) <= radius;
    }
    private static void check(int slot) {
        if (slot < 0 || slot >= LIMIT) throw new IllegalArgumentException("Island slot outside layout");
    }
}
