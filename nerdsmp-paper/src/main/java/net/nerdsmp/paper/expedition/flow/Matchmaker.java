package net.nerdsmp.paper.expedition.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The expedition finder. Solo players and partial parties queue for an expedition and tier; the
 * matcher packs tickets, oldest first, into pickup groups. A full group starts straight away. A group
 * that meets the minimum starts once its oldest ticket has waited for the fill time. Starts stop once
 * every instance slot is busy, and the rest of the queue keeps its place.
 */
public final class Matchmaker {
    public record Ticket(UUID id, String expedition, String tier, List<UUID> members, Instant queuedAt) {
        public Ticket {
            Objects.requireNonNull(id);
            members = List.copyOf(members);
            if (members.isEmpty()) throw new IllegalArgumentException("A ticket needs at least one member.");
        }

        public int size() { return members.size(); }
    }

    public record Rules(int min, int max) { }

    public record Group(String expedition, String tier, List<Ticket> tickets) {
        public Group {
            tickets = List.copyOf(tickets);
        }

        public List<UUID> members() { return tickets.stream().flatMap(ticket -> ticket.members().stream()).toList(); }
    }

    public enum QueueResult { QUEUED, ALREADY_QUEUED, TOO_LARGE, UNKNOWN }

    private final List<Ticket> queue = new ArrayList<>();
    private final Duration fillWait;

    public Matchmaker(Duration fillWait) { this.fillWait = fillWait; }

    public synchronized QueueResult enqueue(Ticket ticket, Rules rules) {
        if (rules == null) return QueueResult.UNKNOWN;
        if (ticket.size() > rules.max()) return QueueResult.TOO_LARGE;
        for (Ticket queued : queue)
            if (queued.members().stream().anyMatch(ticket.members()::contains)) return QueueResult.ALREADY_QUEUED;
        queue.add(ticket);
        return QueueResult.QUEUED;
    }

    /** Puts tickets back at their original place after a ready check fails for someone else. */
    public synchronized void requeue(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            if (queue.stream().anyMatch(queued -> queued.id().equals(ticket.id()))) continue;
            int index = 0;
            while (index < queue.size() && !queue.get(index).queuedAt().isAfter(ticket.queuedAt())) index++;
            queue.add(index, ticket);
        }
    }

    /** Removes whichever ticket holds this player and returns it; the rest of that ticket leaves too. */
    public synchronized Optional<Ticket> leave(UUID member) {
        for (int index = 0; index < queue.size(); index++)
            if (queue.get(index).members().contains(member)) return Optional.of(queue.remove(index));
        return Optional.empty();
    }

    public synchronized Optional<Ticket> ticketOf(UUID member) {
        return queue.stream().filter(ticket -> ticket.members().contains(member)).findFirst();
    }

    public synchronized List<Ticket> tickets() { return List.copyOf(queue); }

    public synchronized int waiting(String expedition, String tier) {
        return queue.stream().filter(ticket -> ticket.expedition().equals(expedition) && ticket.tier().equals(tier))
            .mapToInt(Ticket::size).sum();
    }

    /** Forms and removes the groups that can start now. Rules missing for an expedition drop its tickets. */
    public synchronized List<Group> match(Map<String, Rules> rules, int freeInstances, Instant now) {
        List<Group> started = new ArrayList<>();
        queue.removeIf(ticket -> !rules.containsKey(ticket.expedition()));
        Map<String, List<Ticket>> buckets = new LinkedHashMap<>();
        for (Ticket ticket : queue) buckets.computeIfAbsent(ticket.expedition() + "\u0000" + ticket.tier(), key -> new ArrayList<>()).add(ticket);
        for (List<Ticket> bucket : buckets.values()) {
            Rules rule = rules.get(bucket.get(0).expedition());
            List<Ticket> remaining = new ArrayList<>(bucket);
            while (!remaining.isEmpty() && started.size() < freeInstances) {
                List<Ticket> group = pack(remaining, rule.max());
                int size = group.stream().mapToInt(Ticket::size).sum();
                boolean full = size == rule.max();
                boolean waited = size >= rule.min() && !group.get(0).queuedAt().plus(fillWait).isAfter(now);
                if (!full && !waited) break;
                started.add(new Group(group.get(0).expedition(), group.get(0).tier(), group));
                remaining.removeAll(group);
                queue.removeAll(group);
            }
        }
        return List.copyOf(started);
    }

    /** First-fit in queue order: the oldest ticket always leads, later tickets fill the gaps. */
    static List<Ticket> pack(List<Ticket> tickets, int max) {
        List<Ticket> group = new ArrayList<>();
        int size = 0;
        for (Ticket ticket : tickets) {
            if (size + ticket.size() > max) continue;
            group.add(ticket);
            size += ticket.size();
            if (size == max) break;
        }
        return group;
    }
}
