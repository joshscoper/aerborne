package net.nerdsmp.paper.expedition.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Everyone in a forming run confirms before the instance is built. One decline or the timeout cancels. */
public final class ReadyCheck {
    public enum State { PENDING, READY, CANCELLED }

    private final UUID id = UUID.randomUUID();
    private final String expedition;
    private final String tier;
    private final Set<UUID> members;
    private final Set<UUID> accepted = new HashSet<>();
    private final List<Matchmaker.Ticket> tickets;
    private final Instant deadline;
    private final int allies;
    private State state = State.PENDING;
    private UUID decliner;

    public ReadyCheck(String expedition, String tier, List<UUID> members, List<Matchmaker.Ticket> tickets, Instant now, Duration timeout) {
        this(expedition, tier, members, tickets, now, timeout, 0);
    }

    /** {@code allies} hired NPCs join the run once everyone is ready. */
    public ReadyCheck(String expedition, String tier, List<UUID> members, List<Matchmaker.Ticket> tickets, Instant now, Duration timeout, int allies) {
        if (allies < 0) throw new IllegalArgumentException("Ally count cannot be negative");
        this.allies = allies;
        this.expedition = expedition;
        this.tier = tier;
        this.members = new LinkedHashSet<>(members);
        this.tickets = List.copyOf(tickets);
        this.deadline = now.plus(timeout);
    }

    public UUID id() { return id; }
    public String expedition() { return expedition; }
    public String tier() { return tier; }
    public Set<UUID> members() { return Set.copyOf(members); }
    public List<UUID> orderedMembers() { return List.copyOf(members); }
    public List<Matchmaker.Ticket> tickets() { return tickets; }
    public State state() { return state; }
    public int allies() { return allies; }
    public UUID decliner() { return decliner; }
    public Instant deadline() { return deadline; }
    public boolean accepted(UUID member) { return accepted.contains(member); }
    public int acceptedCount() { return accepted.size(); }

    public State accept(UUID member) {
        if (state == State.PENDING && members.contains(member)) {
            accepted.add(member);
            if (accepted.containsAll(members)) state = State.READY;
        }
        return state;
    }

    public State decline(UUID member) {
        if (state == State.PENDING && members.contains(member)) {
            state = State.CANCELLED;
            decliner = member;
        }
        return state;
    }

    public State tick(Instant now) {
        if (state == State.PENDING && !now.isBefore(deadline)) state = State.CANCELLED;
        return state;
    }

    /** Tickets whose members all accepted (or never got the chance to decline) go back in the queue. */
    public List<Matchmaker.Ticket> innocentTickets() {
        return tickets.stream().filter(ticket -> ticket.members().stream()
            .allMatch(member -> !member.equals(decliner) && (decliner != null || accepted.contains(member)))).toList();
    }
}
