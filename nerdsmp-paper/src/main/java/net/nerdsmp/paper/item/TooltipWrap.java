package net.nerdsmp.paper.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Breaks a line of MiniMessage markup into lines a tooltip can hold. Minecraft sizes a tooltip to its
 * longest line, so one long sentence can stretch it across the screen. Colours carry over each break.
 */
final class TooltipWrap {
    static final int WIDTH = 38;

    private TooltipWrap() { }

    static List<String> wrap(String markup) {
        return wrap(markup, WIDTH);
    }

    static List<String> wrap(String markup, int width) {
        List<String> lines = new ArrayList<>();
        Deque<String> open = new ArrayDeque<>();
        StringBuilder line = new StringBuilder();
        int shown = 0, index = 0;
        while (index < markup.length()) {
            char next = markup.charAt(index);
            if (next == '<') {
                int end = markup.indexOf('>', index);
                if (end > index) {
                    String tag = markup.substring(index, end + 1);
                    line.append(tag);
                    if (tag.startsWith("</")) close(open, name(tag));
                    else if (!tag.endsWith("/>")) open.push(tag);
                    index = end + 1;
                    continue;
                }
            }
            int stop = wordEnd(markup, index);
            int length = visible(markup, index, stop);
            boolean space = next == ' ';
            if (shown > 0 && shown + length > width) {
                closeAll(line, open);
                lines.add(line.toString());
                line = new StringBuilder();
                open.descendingIterator().forEachRemaining(line::append);
                shown = 0;
                if (space) {
                    index++;
                    length--;
                }
            }
            line.append(markup, index, stop);
            shown += length;
            index = stop;
        }
        lines.add(line.toString());
        return List.copyOf(lines);
    }

    /** Where the word starting here ends: its leading space, then up to the next space or tag. */
    private static int wordEnd(String markup, int from) {
        int at = from;
        if (markup.charAt(at) == ' ') at++;
        while (at < markup.length()) {
            char next = markup.charAt(at);
            if (next == '\\' && at + 1 < markup.length()) at += 2;
            else if (next == ' ' || next == '<' && markup.indexOf('>', at) > at) break;
            else at++;
        }
        return Math.max(at, from + 1);
    }

    /** Characters on screen in this stretch: an escape counts as the one character it stands for. */
    private static int visible(String markup, int from, int to) {
        int count = 0;
        for (int at = from; at < to; at++) {
            if (markup.charAt(at) == '\\' && at + 1 < to) at++;
            count++;
        }
        return count;
    }

    private static String name(String tag) {
        String body = tag.substring(tag.startsWith("</") ? 2 : 1, tag.length() - 1);
        int colon = body.indexOf(':');
        return colon < 0 ? body : body.substring(0, colon);
    }

    private static void close(Deque<String> open, String name) {
        if (name.isEmpty()) {
            open.poll();
            return;
        }
        for (var tags = open.iterator(); tags.hasNext(); ) {
            if (name(tags.next()).equals(name)) {
                tags.remove();
                return;
            }
        }
    }

    private static void closeAll(StringBuilder line, Deque<String> open) {
        for (String tag : open) line.append("</").append(name(tag)).append('>');
    }
}
