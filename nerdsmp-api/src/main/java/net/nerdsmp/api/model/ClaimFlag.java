package net.nerdsmp.api.model;

public enum ClaimFlag {
    EXPLOSIONS,
    FIRE_SPREAD,
    MOB_GRIEFING,
    PUBLIC_INTERACT,
    PUBLIC_CONTAINERS,
    PUBLIC_HARVEST,
    PUBLIC_ANIMALS,
    HOSTILE_SPAWNING,
    PVP;

    public static java.util.Set<ClaimFlag> defaults() {
        return java.util.Set.of(HOSTILE_SPAWNING, PVP);
    }
}
