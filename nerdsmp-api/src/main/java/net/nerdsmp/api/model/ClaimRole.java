package net.nerdsmp.api.model;

public enum ClaimRole {
    NONE,
    BUILD,
    CONTAINER,
    REDSTONE,
    RESIDENT,
    MANAGE,
    OWNER;

    public boolean allows(ClaimRole required) {
        return required == NONE || this == OWNER || this == MANAGE && required != OWNER
            || this == RESIDENT && (required == BUILD || required == CONTAINER || required == REDSTONE)
            || this == required;
    }
}
