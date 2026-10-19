package net.nerdsmp.paper.npc.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Literal text only: authored dialogue cannot inject chat click or hover commands. */
public final class ChatDialogueRenderer {
    public static final int WRAP_WIDTH = 70;
    public static final int MAX_BODY_LINES = 8;

    private ChatDialogueRenderer() { }

    public static List<Component> render(
        UUID token,
        String name,
        String title,
        List<String> body,
        List<Choice> choices
    ) {
        Objects.requireNonNull(token, "token");
        List<String> text = List.copyOf(body);
        List<Choice> responses = List.copyOf(choices);
        if (responses.size() > 5) throw new IllegalArgumentException("Dialogue supports at most five responses");
        List<Component> output = new ArrayList<>();
        output.add(Component.empty());
        Component header = Component.text(clean(name), NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        String subtitle = clean(title);
        if (!subtitle.isBlank()) {
            header = header.append(Component.text("  |  " + subtitle, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.BOLD, false));
        }
        output.add(header);
        List<String> wrapped = new ArrayList<>();
        for (String paragraph : text) wrapped.addAll(wrap(clean(paragraph), WRAP_WIDTH));
        boolean truncated = wrapped.size() > MAX_BODY_LINES;
        int shown = Math.min(MAX_BODY_LINES, wrapped.size());
        for (int index = 0; index < shown; index++) {
            String line = wrapped.get(index);
            if (truncated && index == shown - 1) line = shorten(line, WRAP_WIDTH - 4) + " ...";
            Component content = Component.text("  " + line, NamedTextColor.WHITE);
            if (truncated) {
                content = content.hoverEvent(HoverEvent.showText(Component.text(
                    String.join("\n", wrapped), NamedTextColor.WHITE)));
            }
            output.add(content);
        }
        output.add(Component.empty());
        for (int index = 0; index < responses.size(); index++) {
            Choice choice = responses.get(index);
            String label = "  [" + (index + 1) + "] " + clean(choice.text());
            Component response = Component.text(label, choice.available()
                ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY);
            List<String> details = new ArrayList<>();
            if (choice.available()) {
                details.addAll(choice.hints().stream().map(ChatDialogueRenderer::clean).toList());
                details.add("Click to reply, or type /npc reply " + (index + 1) + ".");
                response = response.clickEvent(ClickEvent.runCommand("/npc reply " + token + " " + (index + 1)));
            } else {
                details.add("Unavailable");
                details.addAll(choice.lockedReasons().stream().map(ChatDialogueRenderer::clean).distinct().toList());
            }
            output.add(response.hoverEvent(HoverEvent.showText(Component.text(
                String.join("\n", details), choice.available() ? NamedTextColor.GRAY : NamedTextColor.RED))));
        }
        output.add(Component.text("  [Leave conversation]", NamedTextColor.GRAY)
            .clickEvent(ClickEvent.runCommand("/npc bye " + token))
            .hoverEvent(HoverEvent.showText(Component.text("End this conversation.", NamedTextColor.GRAY))));
        return List.copyOf(output);
    }

    static List<String> wrap(String text, int width) {
        if (width < 1) throw new IllegalArgumentException("Text wrap width must be positive");
        if (text.isBlank()) return List.of("");
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String remaining = word;
            if (remaining.length() > width && !line.isEmpty()) {
                result.add(line.toString());
                line.setLength(0);
            }
            while (remaining.length() > width) {
                int boundary = boundary(remaining, width);
                result.add(remaining.substring(0, boundary));
                remaining = remaining.substring(boundary);
            }
            if (remaining.isEmpty()) continue;
            if (!line.isEmpty() && line.length() + 1 + remaining.length() > width) {
                result.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(remaining);
        }
        if (!line.isEmpty()) result.add(line.toString());
        return List.copyOf(result);
    }

    private static String clean(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > 16_384) throw new IllegalArgumentException("Dialogue text is too long");
        return text.replaceAll("[\\p{Cntrl}\\u00a7]", " ").strip();
    }

    private static String shorten(String text, int length) {
        return text.length() <= length ? text : text.substring(0, boundary(text, length)).stripTrailing();
    }

    private static int boundary(String text, int length) {
        int end = Math.min(text.length(), length);
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
            && Character.isLowSurrogate(text.charAt(end))) {
            // Keep supplementary characters intact, even with a one-character test width.
            return end == 1 ? 2 : end - 1;
        }
        return end;
    }

    public record Choice(String text, boolean available, List<String> hints, List<String> lockedReasons) {
        public Choice {
            Objects.requireNonNull(text, "text");
            hints = List.copyOf(hints);
            lockedReasons = List.copyOf(lockedReasons);
        }
    }
}
