package net.nerdsmp.paper.gui;

import java.util.Objects;

/** A short-lived quote tied to both inputs and the exact selected socket. */
final class ForgeReview<T> {
    private final T primary, catalyst;
    private final int socket;
    private final String operation;
    private final long expiresAt;
    ForgeReview(T primary, T catalyst, int socket, String operation, long now) {
        this.primary = Objects.requireNonNull(primary);
        this.catalyst = Objects.requireNonNull(catalyst);
        this.socket = socket;
        this.operation = Objects.requireNonNull(operation);
        expiresAt = now + 15_000;
    }
    boolean matches(T primary, T catalyst, int socket, String operation, long now) {
        return now < expiresAt && this.socket == socket && this.operation.equals(operation)
            && this.primary.equals(primary) && this.catalyst.equals(catalyst);
    }
}
