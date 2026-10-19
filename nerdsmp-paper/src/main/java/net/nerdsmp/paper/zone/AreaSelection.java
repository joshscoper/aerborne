package net.nerdsmp.paper.zone;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Ephemeral staff cuboids shared by area authoring commands, never persisted as player progress. */
public final class AreaSelection {
    public static final int MAX_PLAYERS = 256;
    private final Map<UUID, Selection> selections = new HashMap<>();

    /** Selecting in another world starts a fresh selection; it cannot retain a cross-world corner. */
    public synchronized Selection select(UUID player, UUID world, int x, int y, int z, boolean first) {
        Objects.requireNonNull(player, "Player UUID is required");
        Objects.requireNonNull(world, "World UUID is required");
        Point point = new Point(x, y, z);
        Selection previous = selections.get(player);
        if (previous == null && selections.size() >= MAX_PLAYERS)
            throw new IllegalStateException("Too many area selections are open. Clear an unused selection and try again.");
        if (previous != null && !previous.worldId().equals(world)) previous = null;
        Selection updated = new Selection(world,
            first ? point : previous == null ? null : previous.first(),
            first ? previous == null ? null : previous.second() : point);
        // Invalid second corners must not replace an already valid, useful selection.
        if (updated.complete()) updated.bounds();
        selections.put(player, updated);
        return updated;
    }

    public synchronized Optional<Selection> selection(UUID player) {
        return Optional.ofNullable(selections.get(Objects.requireNonNull(player, "Player UUID is required")));
    }

    public synchronized void clear(UUID player) {
        selections.remove(Objects.requireNonNull(player, "Player UUID is required"));
    }

    public synchronized void clearAll() {
        selections.clear();
    }

    public record Point(int x, int y, int z) {
        public Point {
            new ZoneDefinition.Bounds(x, y, z, x, y, z);
        }
    }

    public record Selection(UUID worldId, Point first, Point second) {
        public Selection {
            Objects.requireNonNull(worldId, "World UUID is required");
            if (first == null && second == null)
                throw new IllegalArgumentException("Select a corner first.");
        }

        public boolean complete() {
            return first != null && second != null;
        }

        public ZoneDefinition.Bounds bounds() {
            if (!complete())
                throw new IllegalStateException("Select both corners first: left-click for pos1, right-click for pos2.");
            return new ZoneDefinition.Bounds(
                Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()));
        }

        /** Inclusive block volume, with the same limits used when saving a zone. */
        public long volume() {
            ZoneDefinition.Bounds bounds = bounds();
            return ((long) bounds.maxX() - bounds.minX() + 1)
                * ((long) bounds.maxY() - bounds.minY() + 1)
                * ((long) bounds.maxZ() - bounds.minZ() + 1);
        }
    }
}
