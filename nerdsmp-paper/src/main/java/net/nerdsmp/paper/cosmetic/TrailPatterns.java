package net.nerdsmp.paper.cosmetic;

import java.util.ArrayList;
import java.util.List;

/**
 * The shapes trails draw. Each call returns particle offsets relative to the player's feet for one frame
 * (every two ticks). Trails only draw while the player is moving, except {@code swirl}, which draws when the
 * player turns. Kept small: at most eight particles a frame.
 */
public final class TrailPatterns {
    public record Puff(double x, double y, double z, int count, double spread) {}

    private TrailPatterns() {}

    /**
     * @param yaw    player facing in degrees
     * @param turned degrees turned since the last frame
     */
    public static List<Puff> frame(String pattern, long tick, float yaw, double turned, boolean moving) {
        if ("swirl".equals(pattern)) return turned >= 20.0D ? swirl(tick) : List.of();
        if (!moving) return List.of();
        double radians = Math.toRadians(yaw);
        double backX = Math.sin(radians) * 0.45D, backZ = -Math.cos(radians) * 0.45D;
        return switch (pattern) {
            case "steps" -> tick % 4 == 0 ? List.of(new Puff(backX, 0.05D, backZ, 2, 0.08D)) : List.of();
            case "scatter" -> List.of(new Puff(backX, 0.4D, backZ, 1, 0.3D));
            case "orbit" -> {
                double angle = tick * 0.35D;
                yield List.of(new Puff(Math.cos(angle) * 0.8D, 1.0D + Math.sin(tick * 0.2D) * 0.3D, Math.sin(angle) * 0.8D, 1, 0.0D));
            }
            case "ring" -> tick % 6 == 0 ? ring(0.7D, 6, 0.1D) : List.of();
            case "rise" -> List.of(new Puff(backX, 0.3D + (tick % 3) * 0.2D, backZ, 1, 0.15D));
            case "streak" -> List.of(new Puff(backX, 1.0D, backZ, 2, 0.05D), new Puff(backX * 2, 0.9D, backZ * 2, 1, 0.05D));
            case "drift" -> tick % 2 == 0 ? List.of(new Puff(backX, 1.2D, backZ, 2, 0.35D)) : List.of();
            default -> List.of();
        };
    }

    static List<Puff> ring(double radius, int points, double y) {
        List<Puff> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2 * index / points;
            out.add(new Puff(Math.cos(angle) * radius, y, Math.sin(angle) * radius, 1, 0.0D));
        }
        return out;
    }

    static List<Puff> swirl(long tick) {
        List<Puff> out = new ArrayList<>(8);
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2 * index / 8 + tick * 0.4D;
            out.add(new Puff(Math.cos(angle) * 0.6D, 0.2D + index * 0.15D, Math.sin(angle) * 0.6D, 1, 0.0D));
        }
        return out;
    }
}
