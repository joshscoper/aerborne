package net.nerdsmp.paper.engine;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Small, deterministic guardrails shared by staff content views and their chat inputs. */
public final class EditorPolicy {
    public static final int PAGE_SIZE = 28;
    public static final int MAX_EDITORS = 64;
    public static final long PROMPT_MILLIS = 60_000L;

    private EditorPolicy() { }

    public static String identifier(String value) {
        String id = Objects.requireNonNull(value, "ID").strip().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9_-]{0,47}")) {
            throw new IllegalArgumentException("Use 1–48 letters, numbers, underscores, or hyphens for the ID.");
        }
        return id;
    }

    public static String text(String value, int max) {
        String result = Objects.requireNonNull(value, "Text").strip();
        if (result.isEmpty() || result.length() > max || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Enter between 1 and " + max + " characters, without control characters.");
        }
        return result;
    }

    public static double number(String value, double min, double max) {
        final double result;
        try { result = Double.parseDouble(value.strip()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Enter a number."); }
        if (!Double.isFinite(result) || result < min || result > max) {
            throw new IllegalArgumentException("Enter a number from " + min + " to " + max + ".");
        }
        return result;
    }

    public static int integer(String value, int min, int max) {
        double parsed = number(value, min, max);
        if (parsed != Math.rint(parsed)) throw new IllegalArgumentException("Enter a whole number.");
        return (int) parsed;
    }

    public static int page(int requested, int count) {
        return Math.max(0, Math.min(requested, Math.max(0, (count - 1) / PAGE_SIZE)));
    }

    public static <T> List<T> entries(List<T> values, int requested) {
        int first = page(requested, values.size()) * PAGE_SIZE;
        return List.copyOf(values.subList(first, Math.min(values.size(), first + PAGE_SIZE)));
    }

    public static boolean current(UUID owner, UUID actor, UUID token, UUID activeToken,
                                  long revision, long activeRevision, boolean permitted) {
        return permitted && owner.equals(actor) && token.equals(activeToken) && revision == activeRevision;
    }

    public static boolean promptCurrent(long expiresAt, long now, long revision, long activeRevision) {
        return now < expiresAt && revision == activeRevision;
    }
}
