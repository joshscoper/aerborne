package net.nerdsmp.paper.presentation;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class VisualFormat {
    private VisualFormat() {
    }

    public static String compact(long value) {
        long absolute = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        if (absolute < 1_000L) {
            return Long.toString(value);
        }
        if (absolute < 1_000_000L) {
            return scaled(value, 1_000.0D, "K");
        }
        if (absolute < 1_000_000_000L) {
            return scaled(value, 1_000_000.0D, "M");
        }
        return scaled(value, 1_000_000_000.0D, "B");
    }

    public static String progressBar(long current, long maximum, int segments) {
        if (segments < 1) {
            throw new IllegalArgumentException("segments must be positive");
        }
        double ratio = maximum <= 0L ? 0.0D : Math.max(0.0D, Math.min(1.0D, current / (double) maximum));
        int filled = (int) Math.round(ratio * segments);
        return "<aqua>" + "■".repeat(filled) + "</aqua><dark_gray>"
            + "■".repeat(segments - filled) + "</dark_gray>";
    }

    public static Optional<MilestoneProgress> nextMilestone(long points, Map<String, Long> milestones) {
        return milestones.entrySet().stream()
            .filter(entry -> entry.getValue() > points)
            .min(Comparator.comparingLong(Map.Entry::getValue))
            .map(entry -> new MilestoneProgress(entry.getKey(), points, entry.getValue()));
    }

    public static String titleCase(String raw) {
        String[] words = raw.replace('_', ' ').replace('-', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private static String scaled(long value, double divisor, String suffix) {
        double scaled = value / divisor;
        if (Math.abs(scaled) >= 100.0D || Math.rint(scaled) == scaled) {
            return String.format(Locale.ROOT, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.ROOT, "%.1f%s", scaled, suffix);
    }

    public record MilestoneProgress(String id, long current, long required) {
        public long remaining() {
            return Math.max(0L, required - current);
        }
    }
}
