package net.nerdsmp.paper.resource;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable provenance for one chunk. A blocked cell cannot produce natural-resource bonuses
 * until an explicitly approved renewal removes its marker.
 */
public final class ResourceLedger {
    private static final ResourceLedger EMPTY = new ResourceLedger(Set.of());

    private final Set<Long> blocked;

    private ResourceLedger(Set<Long> blocked) {
        this.blocked = Set.copyOf(blocked);
    }

    public static ResourceLedger empty() {
        return EMPTY;
    }

    /** Loads canonical signed-Y/local-X/local-Z coordinates; malformed data is never discarded. */
    public static ResourceLedger from(long[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0) return EMPTY;
        Set<Long> positions = new HashSet<>();
        for (long position : encoded) {
            long y = position >> 8;
            if (y < Integer.MIN_VALUE || y > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Resource position exceeds the signed integer Y range");
            }
            if (!positions.add(position)) {
                throw new IllegalArgumentException("Duplicate resource position: " + position);
            }
        }
        return new ResourceLedger(positions);
    }

    public boolean blocked(int localX, int y, int localZ) {
        return blocked.contains(encode(localX, y, localZ));
    }

    public ResourceLedger withBlocked(Collection<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        Set<Long> updated = new HashSet<>(blocked);
        boolean changed = false;
        for (Cell cell : cells) {
            Objects.requireNonNull(cell, "cell");
            changed |= updated.add(encode(cell.localX(), cell.y(), cell.localZ()));
        }
        return changed ? new ResourceLedger(updated) : this;
    }

    /** Only the named cells renew; adjacent coordinates and other heights retain their markers. */
    public ResourceLedger withRenewed(Collection<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        Set<Long> updated = new HashSet<>(blocked);
        boolean changed = false;
        for (Cell cell : cells) {
            Objects.requireNonNull(cell, "cell");
            changed |= updated.remove(encode(cell.localX(), cell.y(), cell.localZ()));
        }
        if (!changed) return this;
        return updated.isEmpty() ? EMPTY : new ResourceLedger(updated);
    }

    /** A detached, deterministically sorted snapshot for chunk persistence. */
    public long[] serialize() {
        return blocked.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    private static long encode(int localX, int y, int localZ) {
        validateLocal(localX, localZ);
        return ((long) y << 8) | ((long) localX << 4) | localZ;
    }

    private static void validateLocal(int localX, int localZ) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            throw new IllegalArgumentException("Resource X and Z must be chunk-local coordinates from 0 to 15");
        }
    }

    public record Cell(int localX, int y, int localZ) {
        public Cell {
            validateLocal(localX, localZ);
        }
    }
}
