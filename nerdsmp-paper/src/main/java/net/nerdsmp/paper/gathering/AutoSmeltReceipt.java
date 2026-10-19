package net.nerdsmp.paper.gathering;

import java.util.Objects;
import java.util.UUID;

/** Single-threaded, one-use proof shared by the break and its immediate native drop event. */
final class AutoSmeltReceipt {
    enum State { CAPTURED, PROVEN, CONVERTED, SETTLED, REFUSED }
    record Identity(UUID player, String account, UUID world, int x, int y, int z, String blockData, int tick) {
        Identity {
            Objects.requireNonNull(player); Objects.requireNonNull(account); Objects.requireNonNull(world);
            Objects.requireNonNull(blockData);
        }
    }
    private final Identity identity;
    private State state = State.CAPTURED;
    private String operation;
    AutoSmeltReceipt(Identity identity) { this.identity = Objects.requireNonNull(identity); }
    boolean prove(String harvestOperation) {
        if (state != State.CAPTURED || harvestOperation == null || !harvestOperation.startsWith("harvest:")) return false;
        try { UUID.fromString(harvestOperation.substring("harvest:".length())); }
        catch (IllegalArgumentException malformed) { return false; }
        operation = harvestOperation + ":furnace_heart";
        state = State.PROVEN;
        return true;
    }
    boolean convert(Identity actual) {
        if (state != State.PROVEN || !identity.equals(actual)) return false;
        state = State.CONVERTED;
        return true;
    }
    boolean settle(boolean finalBreak, boolean finalDrop, int verifiedUnits) {
        if (state != State.CONVERTED) return false;
        state = finalBreak && finalDrop && verifiedUnits > 0 && verifiedUnits <= AutoSmeltRules.MAX_UNITS
            ? State.SETTLED : State.REFUSED;
        return state == State.SETTLED;
    }
    void refuse() { if (state != State.SETTLED) state = State.REFUSED; }
    String operation() { return operation; }
    State state() { return state; }
}
