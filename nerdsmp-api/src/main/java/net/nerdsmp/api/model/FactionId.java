package net.nerdsmp.api.model;

import java.util.Objects;

public record FactionId(String value) implements Comparable<FactionId> {
    public FactionId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Faction ids must use 1-64 lowercase letters, numbers, dashes, or underscores");
        }
    }

    @Override
    public int compareTo(FactionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
