package net.nerdsmp.paper.studio.qa;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Optional team tracking, never a publishing gate or an approval requirement. */
public record WorkItem(String id, String title, Kind kind, State state, List<ContentRef> references,
                       UUID creator, UUID owner, Instant createdAt, Instant updatedAt, long revision,
                       List<Note> notes) {
    public static final int MAX_ITEMS = 256;
    public static final int MAX_REFERENCES = 12;
    public static final int MAX_NOTES = 32;
    public enum Kind { TASK, BUG, REVIEW }
    public enum State { OPEN, IN_PROGRESS, DONE }
    public record Note(UUID author, Instant at, String text) {
        public Note {
            Objects.requireNonNull(author, "Note author is required.");
            Objects.requireNonNull(at, "Note timestamp is required.");
            text = WorkItem.text(text, 400, "Note");
        }
    }
    public WorkItem {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}"))
            throw new IllegalArgumentException("Work-item IDs use 1–48 lowercase letters, digits, underscores or dashes.");
        title = text(title, 96, "Title");
        Objects.requireNonNull(kind, "Work-item kind is required.");
        Objects.requireNonNull(state, "Work-item state is required.");
        Objects.requireNonNull(creator, "Creator is required.");
        Objects.requireNonNull(createdAt, "Creation timestamp is required.");
        Objects.requireNonNull(updatedAt, "Update timestamp is required.");
        references = List.copyOf(references);
        notes = List.copyOf(notes);
        if (revision < 1 || updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Invalid work-item revision or timestamps.");
        if (references.size() > MAX_REFERENCES || references.stream().distinct().count() != references.size())
            throw new IllegalArgumentException("Use at most 12 distinct content references.");
        if (notes.size() > MAX_NOTES) throw new IllegalArgumentException("Use at most 32 recent notes.");
        Instant previous = createdAt;
        for (Note note : notes) {
            if (note.at().isBefore(previous) || note.at().isAfter(updatedAt))
                throw new IllegalArgumentException("Notes must be in timestamp order within the work item's lifetime.");
            previous = note.at();
        }
    }
    public static String text(String value, int maximum, String label) {
        if (value == null || value.isBlank() || value.length() > maximum
            || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7))
            throw new IllegalArgumentException(label + " must be 1–" + maximum + " plain-text characters.");
        return value.strip();
    }
}
