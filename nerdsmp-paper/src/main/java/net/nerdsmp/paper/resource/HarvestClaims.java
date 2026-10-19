package net.nerdsmp.paper.resource;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** One eligibility decision and operation ID per event; each reward path consumes it once. */
public final class HarvestClaims<E> {
    public enum Channel { SKILLS, EXCAVATION, QUESTS, SIGNATURES }
    public record Harvest(String operationId) { }
    private final WeakHashMap<E, Decision> decisions = new WeakHashMap<>();

    public Optional<Harvest> claim(E event, Channel channel, BooleanSupplier eligible) {
        Decision decision = decisions.computeIfAbsent(java.util.Objects.requireNonNull(event), ignored ->
            new Decision(eligible.getAsBoolean(), new Harvest("harvest:" + UUID.randomUUID())));
        return decision.eligible && decision.claimed.add(java.util.Objects.requireNonNull(channel))
            ? Optional.of(decision.harvest) : Optional.empty();
    }

    private static final class Decision {
        private final boolean eligible;
        private final Harvest harvest;
        private final EnumSet<Channel> claimed = EnumSet.noneOf(Channel.class);
        private Decision(boolean eligible, Harvest harvest) { this.eligible = eligible; this.harvest = harvest; }
    }
}
