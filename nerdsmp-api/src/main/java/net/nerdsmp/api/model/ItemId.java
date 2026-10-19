package net.nerdsmp.api.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ItemId(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_./-]{1,96}");

    public ItemId {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid item id: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

