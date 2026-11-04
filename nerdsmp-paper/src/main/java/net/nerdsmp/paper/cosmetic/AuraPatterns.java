package net.nerdsmp.paper.cosmetic;

import java.util.ArrayList;
import java.util.List;

/**
 * The shapes auras and finishers draw, as particle offsets from the player's feet or the fallen mob's.
 * Auras draw every two ticks while the player stands still (a trail takes over when they move), and stay
 * small: at most six particles a frame. A finisher draws once, where a kill falls.
 */
public final class AuraPatterns {
    private AuraPatterns() {}

    public static List<TrailPatterns.Puff> aura(String pattern, long tick) {
        return switch (pattern) {
            case "halo" -> tick % 4 == 0 ? spin(0.32D, 5, 2.3D, tick * 0.15D) : List.of();
            case "orbit" -> {
                double angle = tick * 0.12D, bob = Math.sin(tick * 0.08D) * 0.2D;
                yield List.of(new TrailPatterns.Puff(Math.cos(angle) * 0.85D, 1.1D + bob, Math.sin(angle) * 0.85D, 1, 0.0D),
                    new TrailPatterns.Puff(-Math.cos(angle) * 0.85D, 1.1D - bob, -Math.sin(angle) * 0.85D, 1, 0.0D));
            }
            case "flurry" -> tick % 2 == 0 ? List.of(new TrailPatterns.Puff(0.0D, 2.4D, 0.0D, 1, 0.55D)) : List.of();
            case "rise" -> {
                double angle = tick * 1.7D;
                yield tick % 2 == 0 ? List.of(new TrailPatterns.Puff(Math.cos(angle) * 0.55D, 0.15D, Math.sin(angle) * 0.55D, 1, 0.05D)) : List.of();
            }
            default -> List.of();
        };
    }

    public static List<TrailPatterns.Puff> finisher(String pattern) {
        return switch (pattern) {
            case "burst" -> List.of(new TrailPatterns.Puff(0.0D, 0.8D, 0.0D, 24, 0.45D));
            case "fountain" -> List.of(new TrailPatterns.Puff(0.0D, 0.6D, 0.0D, 30, 0.2D));
            case "column" -> {
                List<TrailPatterns.Puff> out = new ArrayList<>(6);
                for (int step = 0; step < 6; step++) out.add(new TrailPatterns.Puff(0.0D, step * 0.45D, 0.0D, 3, 0.15D));
                yield out;
            }
            default -> List.of();
        };
    }

    private static List<TrailPatterns.Puff> spin(double radius, int points, double y, double turn) {
        List<TrailPatterns.Puff> out = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2 * index / points + turn;
            out.add(new TrailPatterns.Puff(Math.cos(angle) * radius, y, Math.sin(angle) * radius, 1, 0.0D));
        }
        return out;
    }
}
