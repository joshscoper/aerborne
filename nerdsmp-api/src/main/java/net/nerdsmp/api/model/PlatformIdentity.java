package net.nerdsmp.api.model;

import java.util.Locale;
import java.util.Objects;

public record PlatformIdentity(Platform platform, String externalId) {
    public PlatformIdentity {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(externalId, "externalId");
        externalId = externalId.trim().toLowerCase(Locale.ROOT);
        if (externalId.isEmpty() || externalId.length() > 128) {
            throw new IllegalArgumentException("externalId must contain 1-128 characters");
        }
    }

    public enum Platform {
        JAVA,
        BEDROCK
    }
}

