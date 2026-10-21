package net.nerdsmp.paper.cosmetic;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where each block of a sculpture sits for one animation frame, as a display-entity transformation.
 *
 * <p>Every part's display entity stands at the companion's position and faces its direction, so the
 * transformation only places the block within the model (in blocks) and applies its animation: legs swing in
 * pairs while walking, tails wag, heads nod, ears twitch, wings flap and flames flicker. The client glides
 * between frames, so updating every few ticks still looks smooth.
 */
public final class SculptureMath {
    private SculptureMath() {}

    /** A display transformation: translation and size in blocks, plus a rotation. */
    public record Pose(Vector3f translation, Quaternionf rotation, Vector3f scale) {}

    /**
     * @param tick   animation time in server ticks
     * @param moving whether the companion is travelling this frame
     * @param flying whether it is a flying companion (wings beat harder)
     * @param scale  overall size; 1 = 16 pixels per block
     */
    public static Pose pose(SculptureModel model, SculptureModel.Part part, long tick, boolean moving, boolean flying, double scale) {
        double t = tick;
        float px = (float) (scale / 16.0D);
        Vector3f size = new Vector3f((float) part.width(), (float) part.height(), (float) part.depth());
        Vector3f centre = new Vector3f((float) part.x(), (float) part.y(), (float) part.z());
        if (part.group().equals("flame")) {
            // Flicker taller and shorter while the base stays put.
            float stretch = (float) (1.0D + 0.18D * Math.sin(t * 0.9D + part.y()));
            centre.y += size.y * (stretch - 1.0F) / 2.0F;
            size.y *= stretch;
        }
        Quaternionf rotation = rotation(part.group(), t, moving, flying);
        Vector3f pivot = pivot(model, part);
        // Rotate the block's corner about the pivot, then convert pixels to blocks.
        Vector3f corner = new Vector3f(centre).sub(new Vector3f(size).mul(0.5F)).sub(pivot);
        rotation.transform(corner);
        Vector3f translation = corner.add(pivot).mul(px);
        if (part.group().equals("head")) translation.y += (float) (Math.sin(t * 0.12D) * 0.25D) * px;
        return new Pose(translation, rotation, size.mul(px));
    }

    static Quaternionf rotation(String group, double t, boolean moving, boolean flying) {
        Quaternionf q = new Quaternionf();
        double stride = moving ? Math.sin(t * 0.9D) * 0.5D : 0.0D;
        double flap = flying ? 0.55D * Math.sin(t * 0.8D) : 0.15D * Math.sin(t * 0.3D);
        return switch (group) {
            case "leg_a" -> q.rotateX((float) stride);
            case "leg_b" -> q.rotateX((float) -stride);
            case "tail" -> q.rotateY((float) (Math.sin(t * 0.35D) * 0.35D)).rotateX(-0.12F);
            case "head" -> q.rotateX((float) (Math.sin(t * 0.12D) * 0.08D));
            case "ear" -> q.rotateX((float) (Math.pow(Math.max(0.0D, Math.sin(t * 0.21D)), 8) * 0.3D));
            case "wing_left" -> q.rotateZ((float) -flap);
            case "wing_right" -> q.rotateZ((float) flap);
            default -> q;
        };
    }

    /** The point a part turns about, in pixels: the model's pivot for its group, or a sensible spot on the part. */
    static Vector3f pivot(SculptureModel model, SculptureModel.Part part) {
        double[] set = model.pivots().get(part.group());
        if (set != null) return new Vector3f((float) set[0], (float) set[1], (float) set[2]);
        float x = (float) part.x(), y = (float) part.y(), z = (float) part.z();
        float hw = (float) part.width() / 2, hh = (float) part.height() / 2, hd = (float) part.depth() / 2;
        return switch (part.group()) {
            case "leg_a", "leg_b" -> new Vector3f(x, y + hh, z);
            case "ear", "flame" -> new Vector3f(x, y - hh, z);
            case "wing_left" -> new Vector3f(x + hw, y, z);
            case "wing_right" -> new Vector3f(x - hw, y, z);
            case "head" -> new Vector3f(x, y - hh, z - hd);
            case "tail" -> new Vector3f(x, y, z + hd);
            default -> new Vector3f(x, y, z);
        };
    }

    /** How far the whole companion lifts off the ground this frame, in blocks. */
    public static double bounce(String style, long tick, boolean moving, double scale) {
        if (!moving) return 0.0D;
        return switch (style) {
            case "trot" -> Math.abs(Math.sin(tick * 0.45D)) * 0.035D * scale;
            case "hop" -> Math.abs(Math.sin(tick * 0.3D)) * 0.2D * scale;
            default -> 0.0D;
        };
    }
}
