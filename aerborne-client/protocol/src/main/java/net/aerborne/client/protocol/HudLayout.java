package net.aerborne.client.protocol;

import java.util.Locale;

/**
 * Where the player put one HUD element in edit mode: which point of the screen it hangs from, its offset from that
 * point in GUI pixels, its scale, and whether it is shown. The element's own matching point sits on the screen's
 * (a top-right element keeps its top-right corner at the offset), so it stays put as the window is resized.
 */
public record HudLayout(Anchor anchor, int dx, int dy, float scale, boolean visible) {
    public static final float MIN_SCALE = 0.5F, MAX_SCALE = 2F;

    /** Nine points of the screen, as fractions of its width and height. */
    public enum Anchor {
        TOP_LEFT(0, 0), TOP(0.5F, 0), TOP_RIGHT(1, 0),
        LEFT(0, 0.5F), CENTER(0.5F, 0.5F), RIGHT(1, 0.5F),
        BOTTOM_LEFT(0, 1), BOTTOM(0.5F, 1), BOTTOM_RIGHT(1, 1);
        public final float fx, fy;
        Anchor(float fx, float fy) { this.fx = fx; this.fy = fy; }

        /** The anchor for a point, by which third of the screen it falls in. */
        public static Anchor nearest(float x, float y, float width, float height) {
            int col = x < width / 3 ? 0 : x < width * 2 / 3 ? 1 : 2, row = y < height / 3 ? 0 : y < height * 2 / 3 ? 1 : 2;
            return values()[row * 3 + col];
        }
    }

    public HudLayout {
        if (anchor == null) anchor = Anchor.TOP_LEFT;
        dx = Math.clamp(dx, -4096, 4096);
        dy = Math.clamp(dy, -4096, 4096);
        scale = Float.isFinite(scale) ? Math.clamp(scale, MIN_SCALE, MAX_SCALE) : 1F;
    }

    public HudLayout withScale(float value) { return new HudLayout(anchor, dx, dy, value, visible); }
    public HudLayout withVisible(boolean value) { return new HudLayout(anchor, dx, dy, scale, value); }

    /** The element's left edge for a screen and element width (already scaled). */
    public int left(int screenWidth, int elementWidth) { return Math.round(anchor.fx * screenWidth + dx - anchor.fx * elementWidth); }
    public int top(int screenHeight, int elementHeight) { return Math.round(anchor.fy * screenHeight + dy - anchor.fy * elementHeight); }

    /** The layout that puts an element of this size with its top-left corner at (left, top), anchored where it lies. */
    public static HudLayout at(int left, int top, int width, int height, int screenWidth, int screenHeight, float scale, boolean visible) {
        Anchor anchor = Anchor.nearest(left + width / 2F, top + height / 2F, screenWidth, screenHeight);
        int dx = Math.round(left + anchor.fx * width - anchor.fx * screenWidth);
        int dy = Math.round(top + anchor.fy * height - anchor.fy * screenHeight);
        return new HudLayout(anchor, dx, dy, scale, visible);
    }

    public String encode() {
        return anchor.name() + ";" + dx + ";" + dy + ";" + String.format(Locale.ROOT, "%.2f", scale) + ";" + visible;
    }

    /** Null for anything malformed, so a bad line falls back to the default layout. */
    public static HudLayout decode(String text) {
        if (text == null) return null;
        String[] part = text.split(";");
        if (part.length != 5) return null;
        try {
            return new HudLayout(Anchor.valueOf(part[0]), Integer.parseInt(part[1]), Integer.parseInt(part[2]),
                    Float.parseFloat(part[3]), Boolean.parseBoolean(part[4]));
        } catch (IllegalArgumentException e) { return null; }
    }
}
