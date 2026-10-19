package net.nerdsmp.paper.presentation;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The house chat style. Every helper takes MiniMessage markup; wrap player-chosen text in
 * {@link #escape(String)} first. Colours carry meaning: red refuses or fails, green completes,
 * yellow is pending, partial or refunded, gray is detail, white names commands and values,
 * and gold is reserved for Marks. See docs/STYLE_GUIDE.md.
 */
public final class Chat {
    public static final String ACCOUNT_LOADING = "Your account is still loading. Try again in a moment.";
    public static final String NO_PERMISSION = "You do not have permission to do that.";
    public static final String PLAYERS_ONLY = "Use this command in game.";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern DURATION = Pattern.compile("(\\d{1,9})([smhdw]?)");

    private Chat() {
    }

    public static Component render(String markup) {
        return MINI_MESSAGE.deserialize(markup);
    }

    public static void send(Audience audience, String markup) {
        audience.sendMessage(render(markup));
    }

    public static void success(Audience audience, String markup) {
        send(audience, "<green>" + markup + "</green>");
    }

    public static void error(Audience audience, String markup) {
        send(audience, "<red>" + markup + "</red>");
    }

    public static void notice(Audience audience, String markup) {
        send(audience, "<yellow>" + markup + "</yellow>");
    }

    public static void info(Audience audience, String markup) {
        send(audience, "<gray>" + markup + "</gray>");
    }

    public static void accountLoading(Audience audience) {
        error(audience, ACCOUNT_LOADING);
    }

    public static void noPermission(Audience audience) {
        error(audience, NO_PERMISSION);
    }

    public static void playersOnly(Audience audience) {
        error(audience, PLAYERS_ONLY);
    }

    /** Escapes text a player chose, or any text that may contain angle brackets. */
    public static String escape(String text) {
        return MINI_MESSAGE.escapeTags(text);
    }

    /** A white, clickable command: clicking runs it. */
    public static String command(String command) {
        String slash = command.startsWith("/") ? command : "/" + command;
        return "<click:run_command:'" + quoted(slash) + "'><hover:show_text:'<gray>Click to run</gray>'><white>"
            + escape(slash) + "</white></hover></click>";
    }

    /**
     * A usage line written as {@code /cmd <required> [optional]}. Clicking it puts everything
     * before the first argument into the chat box.
     */
    public static String usage(String syntax) {
        return "<gray>Usage:</gray> " + syntax(syntax);
    }

    /**
     * A command with arguments, white and clickable, for use inside a sentence: {@link #usage(String)}
     * without the "Usage:" label. Clicking puts everything before the first argument into the chat box.
     */
    public static String syntax(String syntax) {
        int argument = firstArgument(syntax);
        String base = (argument < 0 ? syntax : syntax.substring(0, argument)).stripTrailing();
        return "<click:suggest_command:'" + quoted(base) + " '><hover:show_text:'<gray>Click to type</gray>'><white>"
            + escape(syntax) + "</white></hover></click>";
    }

    public static void usage(Audience audience, String syntax) {
        send(audience, usage(syntax));
    }

    /** Makes text safe inside a quoted MiniMessage tag argument such as {@code click:run_command:'…'}. */
    private static String quoted(String argument) {
        return argument.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static int firstArgument(String syntax) {
        int angle = syntax.indexOf('<');
        int square = syntax.indexOf('[');
        if (angle < 0) return square;
        if (square < 0) return angle;
        return Math.min(angle, square);
    }

    /** Plain text, e.g. {@code 1,234 Marks} or {@code 1 Mark}. */
    public static String marks(long amount) {
        return String.format(Locale.ROOT, "%,d", amount) + (amount == 1L || amount == -1L ? " Mark" : " Marks");
    }

    /** {@link #marks(long)} in gold, for chat and lore. */
    public static String marksTag(long amount) {
        return "<gold>" + marks(amount) + "</gold>";
    }

    /** {@code 1.2K ✦}, for the sidebar and other tight spaces only. Chat and lore use {@link #marksTag(long)}. */
    public static String marksCompact(long amount) {
        return VisualFormat.compact(amount) + " ✦";
    }

    /** Turns {@code NOT_ENOUGH_MARKS} into {@code not enough marks}, as a fallback for unmapped results. */
    public static String humanize(String constant) {
        return constant.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim();
    }

    /** True for {@code perm}, {@code permanent} or {@code forever}. Check this before {@link #duration(String, java.time.temporal.ChronoUnit)}. */
    public static boolean isPermanent(String raw) {
        if (raw == null) return false;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "perm", "permanent", "forever" -> true;
            default -> false;
        };
    }

    /**
     * Reads {@code 45s}, {@code 30m}, {@code 2h}, {@code 7d} or {@code 1w}. A bare number uses
     * {@code bareUnit}, so older inputs such as {@code /staff mute Steve 30} keep working.
     * Zero, negative and malformed values are empty.
     */
    public static Optional<Duration> duration(String raw, java.time.temporal.ChronoUnit bareUnit) {
        if (raw == null) return Optional.empty();
        Matcher matcher = DURATION.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return Optional.empty();
        long value = Long.parseLong(matcher.group(1));
        if (value <= 0L) return Optional.empty();
        return Optional.of(switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            case "w" -> Duration.ofDays(value * 7L);
            default -> Duration.of(value, bareUnit);
        });
    }

    /** {@code 90 minutes} → {@code 1h 30m}. */
    public static String duration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long rest = seconds % 60L;
        StringBuilder text = new StringBuilder();
        if (days > 0) text.append(days).append("d ");
        if (hours > 0) text.append(hours).append("h ");
        if (minutes > 0) text.append(minutes).append("m ");
        if (rest > 0 || text.isEmpty()) text.append(rest).append("s");
        return text.toString().trim();
    }
}
