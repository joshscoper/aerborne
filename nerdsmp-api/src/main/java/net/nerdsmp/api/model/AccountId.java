package net.nerdsmp.api.model;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {
    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId parse(String value) {
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

