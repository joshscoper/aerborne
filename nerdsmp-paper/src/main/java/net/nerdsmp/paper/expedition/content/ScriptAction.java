package net.nerdsmp.paper.expedition.content;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One line of optional encounter scripting, such as {@code spawn custom:cinder_wraith 2} or
 * {@code open gatehouse}. Lines are parsed when an expedition loads, so a typo is reported by
 * {@code /expedition check} instead of failing mid-run.
 */
public record ScriptAction(Verb verb, List<String> args, String text) {
    /** Triggers an encounter can attach actions to. */
    public static final Pattern TRIGGER = Pattern.compile(
        "start|clear|enrage|wave-[1-9][0-9]?|health-[1-9][0-9]?|timer-[1-9][0-9]{0,3}|down-[1-9]|solved-[a-z0-9_]{1,32}");

    public ScriptAction {
        args = List.copyOf(args);
    }

    public enum Verb {
        /** {@code message <MiniMessage>} to the whole party. */
        MESSAGE(1, true),
        /** {@code title <title>[|<subtitle>]}. */
        TITLE(1, true),
        /** {@code sound <sound key> [pitch]}. */
        SOUND(1, false),
        /** {@code spawn <mob> [count] [point#]}. */
        SPAWN(1, false),
        /** {@code open <room>} removes that room's gate blocks. */
        OPEN(1, false),
        /** {@code close <room>} restores that room's gate blocks. */
        CLOSE(1, false),
        /** {@code effect <potion> <seconds> [amplifier]} on every party member. */
        EFFECT(2, false),
        /** {@code checkpoint} moves the respawn point to this room. */
        CHECKPOINT(0, false),
        /** {@code wait <seconds>} delays the actions after it. */
        WAIT(1, false),
        /** {@code command <console command>}; {@code {player}} runs it once per member. */
        COMMAND(1, true),
        /** {@code say <speaker>|<line>}: a line of dialogue. The speaker may be an npc id or any name. */
        SAY(1, true),
        /** {@code cutscene <id>} plays one of the expedition's cutscenes for the party. */
        CUTSCENE(1, false),
        /** {@code build <set piece>} fills a set piece in with its block. */
        BUILD(1, false),
        /** {@code break <set piece>} empties a set piece. */
        BREAK(1, false),
        /** {@code restore <set piece>} puts the set piece back as the template had it. */
        RESTORE(1, false),
        /** {@code hazard <start|stop|pulse> <id>} runs, stops or fires once one of the expedition's hazards. */
        HAZARD(2, false),
        /** {@code campfire <id>} lights a campfire for the party. */
        CAMPFIRE(1, false);

        final int minimumArgs;
        final boolean freeText;

        Verb(int minimumArgs, boolean freeText) {
            this.minimumArgs = minimumArgs;
            this.freeText = freeText;
        }
    }

    public static ScriptAction parse(String line) {
        if (line == null || line.isBlank()) throw new IllegalArgumentException("Empty script line.");
        String trimmed = line.strip();
        int space = trimmed.indexOf(' ');
        String word = (space < 0 ? trimmed : trimmed.substring(0, space)).toUpperCase(Locale.ROOT);
        Verb verb;
        try {
            verb = Verb.valueOf(word);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown script action '" + word.toLowerCase(Locale.ROOT)
                + "'. Use message, title, say, sound, spawn, open, close, effect, checkpoint, wait, command, cutscene, "
                + "build, break, restore, hazard or campfire.");
        }
        String rest = space < 0 ? "" : trimmed.substring(space + 1).strip();
        List<String> args = rest.isEmpty() ? List.of() : verb.freeText ? List.of(rest) : List.of(rest.split("\\s+"));
        if (args.size() < verb.minimumArgs)
            throw new IllegalArgumentException("'" + verb.name().toLowerCase(Locale.ROOT) + "' needs more detail: " + trimmed);
        switch (verb) {
            case SPAWN -> {
                if (args.size() > 1) positive(args.get(1), "spawn count", 50);
                if (args.size() > 2) positive(args.get(2), "spawn point", 64);
            }
            case EFFECT -> {
                positive(args.get(1), "effect seconds", 600);
                if (args.size() > 2) nonNegative(args.get(2), "effect amplifier", 4);
            }
            case WAIT -> positive(args.get(0), "wait seconds", 300);
            case SOUND -> { if (args.size() > 1) Double.parseDouble(args.get(1)); }
            case SAY -> { if (!args.get(0).contains("|")) throw new IllegalArgumentException("say needs a speaker and a line: say <speaker>|<line>"); }
            case HAZARD -> {
                if (!List.of("start", "stop", "pulse").contains(args.get(0).toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("hazard needs start, stop or pulse first: hazard start <id>");
            }
            default -> { }
        }
        return new ScriptAction(verb, args, trimmed);
    }

    public String arg(int index) { return args.get(index); }

    public int intArg(int index, int fallback) { return args.size() > index ? Integer.parseInt(args.get(index)) : fallback; }

    private static void positive(String raw, String label, int max) {
        int value = number(raw, label);
        if (value < 1 || value > max) throw new IllegalArgumentException(label + " must be 1-" + max + ".");
    }

    private static void nonNegative(String raw, String label, int max) {
        int value = number(raw, label);
        if (value < 0 || value > max) throw new IllegalArgumentException(label + " must be 0-" + max + ".");
    }

    private static int number(String raw, String label) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be a whole number, not '" + raw + "'.");
        }
    }
}
