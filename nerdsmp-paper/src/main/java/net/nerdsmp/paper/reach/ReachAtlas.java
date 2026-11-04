package net.nerdsmp.paper.reach;

import java.awt.image.BufferedImage;

/**
 * Draws the Reach from above, shaded by height, so the shape of the land can be checked (and shown on the website)
 * without generating a world.
 */
public final class ReachAtlas {
    private ReachAtlas() { }

    public static BufferedImage render(ReachTerrain terrain, int minX, int minZ, int maxX, int maxZ, int blocksPerPixel) {
        int w = (maxX - minX) / blocksPerPixel, h = (maxZ - minZ) / blocksPerPixel;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] tops = new int[w * h];
        ReachTerrain.Column[] columns = new ReachTerrain.Column[w * h];
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) {
            ReachTerrain.Column column = terrain.column(minX + px * blocksPerPixel, minZ + py * blocksPerPixel);
            columns[py * w + px] = column;
            tops[py * w + px] = column.land() ? column.top() : Integer.MIN_VALUE;
        }
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) {
            ReachTerrain.Column c = columns[py * w + px];
            int x = minX + px * blocksPerPixel, z = minZ + py * blocksPerPixel;
            int rgb;
            if (!c.land()) {
                if (c.fall() != Integer.MIN_VALUE) rgb = 0x5a8fd8;
                else rgb = terrain.cloud(x, z) > 0 ? 0xf4f8ff : 0x78c0ec;
            } else if (c.wet()) {
                int depth = c.water() - c.top();
                rgb = mix(0x4f86c9, 0x24508c, Math.min(1, depth / 12.0));
            } else {
                rgb = colour(c);
                if (c.road() > 0.5) rgb = 0xd9c79a;
                // Light from the north-west, like a relief map.
                int west = px > 0 && tops[py * w + px - 1] != Integer.MIN_VALUE ? tops[py * w + px - 1] : c.top();
                int north = py > 0 && tops[(py - 1) * w + px] != Integer.MIN_VALUE ? tops[(py - 1) * w + px] : c.top();
                double slope = ((c.top() - west) + (c.top() - north)) / (double) blocksPerPixel;
                double light = ReachNoise.clamp(1.0 + slope * 0.18, 0.55, 1.35);
                double height = ReachNoise.clamp((c.top() - 70) / 160.0, 0, 1);
                rgb = shade(rgb, light * (0.82 + 0.3 * height));
            }
            image.setRGB(px, py, rgb);
        }
        return image;
    }

    private static int colour(ReachTerrain.Column c) {
        return switch (c.zone()) {
            case HIGHCROWN -> 0x9aa3ad;
            case GOLDENREACH -> c.terrace() > 0.3 ? 0xe3c25a : 0x8dbb4f;
            case BARROWDOWNS -> 0x8f9a6a;
            case THORNWOOD -> 0x3f7a43;
            case MISTMERE -> 0x76a85f;
            case IRONSPINE -> c.top() > 185 ? 0xf2f4f6 : c.top() > 160 ? 0x8f8f93 : 0x6c8f58;
            case SUNSCAR -> 0xc8743f;
            case SCAR -> 0x4a3e3a;
            case ARCHIVE -> 0xe0d6ea;
            case SKY -> 0x78c0ec;
        };
    }

    private static int shade(int rgb, double f) {
        int r = (int) ReachNoise.clamp(((rgb >> 16) & 255) * f, 0, 255);
        int g = (int) ReachNoise.clamp(((rgb >> 8) & 255) * f, 0, 255);
        int b = (int) ReachNoise.clamp((rgb & 255) * f, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) ReachNoise.lerp((a >> 16) & 255, (b >> 16) & 255, t);
        int g = (int) ReachNoise.lerp((a >> 8) & 255, (b >> 8) & 255, t);
        int bl = (int) ReachNoise.lerp(a & 255, b & 255, t);
        return (r << 16) | (g << 8) | bl;
    }
}
