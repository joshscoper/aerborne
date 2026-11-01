package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

/**
 * Building on a turned plan: {@code a} runs across, {@code h} up from {@code y} and {@code f} along {@code dirX, dirZ}, so a
 * piece is written once and can face any way.
 */
record Carving(BuildCanvas b, int x, int y, int z, int dirX, int dirZ) {
    int wx(int a, int f) { return x + a * dirZ + f * dirX; }

    int wz(int a, int f) { return z - a * dirX + f * dirZ; }

    void set(int a, int h, int f, Material block) { b.block(wx(a, f), y + h, wz(a, f), block); }

    void set(int a, int h, int f, Material block, String state) { b.block(wx(a, f), y + h, wz(a, f), block, state); }

    void box(int a1, int a2, int h1, int h2, int f1, int f2, Material block) {
        for (int a = a1; a <= a2; a++) for (int h = h1; h <= h2; h++) for (int f = f1; f <= f2; f++) set(a, h, f, block);
    }

    /** The same box either side of the middle. */
    void both(int a1, int a2, int h1, int h2, int f1, int f2, Material block) {
        box(a1, a2, h1, h2, f1, f2, block);
        box(-a2, -a1, h1, h2, f1, f2, block);
    }
}
