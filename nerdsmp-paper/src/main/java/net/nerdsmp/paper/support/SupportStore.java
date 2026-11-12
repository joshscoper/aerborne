package net.nerdsmp.paper.support;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable help conversations. Every mutation is saved before it becomes visible or is delivered. */
public final class SupportStore {
    public static final int MAX_MESSAGES = 100;
    public static final int MAX_OPEN = 100;
    public static final int CLOSED_RETAINED = 200;
    private final File file;
    private Map<Long, Ticket> tickets = new LinkedHashMap<>();
    private long nextId = 1;

    public SupportStore(File file) {
        this.file = file;
        if (!file.exists()) return;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            if (yaml.getInt("version") != 1) throw new IllegalArgumentException("Unknown support schema");
            nextId = yaml.getLong("next-id");
            if (!(yaml.get("tickets") instanceof List<?> rows) || rows.stream().anyMatch(row -> !(row instanceof Map<?, ?>))) throw new IllegalArgumentException("Invalid ticket list");
            for (Object entryRow : rows) {
                Map<?, ?> row = (Map<?, ?>) entryRow;
                long id = Long.parseLong(row.get("id").toString());
                AccountId owner = AccountId.parse(row.get("owner").toString());
                String name = row.get("name").toString();
                Instant created = Instant.parse(row.get("created").toString());
                String staff = row.get("staff").toString();
                AccountId assigned = staff.isEmpty() ? null : AccountId.parse(staff);
                String staffName = row.get("staff-name").toString();
                if (!(row.get("closed") instanceof Boolean closed)) throw new IllegalArgumentException("Missing closed state");
                int read = Integer.parseInt(row.get("read").toString());
                List<Message> messages = new ArrayList<>();
                if (!(row.get("messages") instanceof List<?> entries)) throw new IllegalArgumentException("Missing transcript");
                for (Object entry : entries) {
                    if (!(entry instanceof Map<?, ?> msg) || !(msg.get("staff") instanceof Boolean fromStaff)) throw new IllegalArgumentException("Invalid message");
                    messages.add(new Message(AccountId.parse(msg.get("actor").toString()), msg.get("name").toString(), fromStaff,
                        Instant.parse(msg.get("time").toString()), text(msg.get("text").toString(), 1)));
                }
                Ticket ticket = new Ticket(id, owner, name, created, assigned, staffName, closed, read, messages);
                if (id < 1 || id >= nextId || read < 0 || read > messages.size() || messages.isEmpty() || messages.size() > MAX_MESSAGES
                    || tickets.putIfAbsent(id, ticket) != null) throw new IllegalArgumentException("Invalid ticket bounds");
                for (Message message : messages) {
                    if (!message.staff() && !owner.equals(message.actor())) throw new IllegalArgumentException("Invalid requester");
                }
            }
            if (nextId < 1 || tickets.size() > MAX_OPEN + CLOSED_RETAINED || queue().size() > MAX_OPEN
                || queue().stream().map(Ticket::owner).distinct().count() != queue().size()) throw new IllegalArgumentException("Invalid queue");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot read support queue; refusing to replace " + file, failure);
        }
    }

    public synchronized Optional<Ticket> get(long id) { return Optional.ofNullable(tickets.get(id)); }
    public synchronized Optional<Ticket> active(AccountId owner) { return tickets.values().stream().filter(t -> !t.closed() && t.owner().equals(owner)).findFirst(); }
    public synchronized List<Ticket> own(AccountId owner) { return tickets.values().stream().filter(t -> t.owner().equals(owner)).sorted(Comparator.comparingLong(Ticket::id).reversed()).toList(); }
    public synchronized List<Ticket> queue() { return tickets.values().stream().filter(t -> !t.closed()).sorted(Comparator.comparingLong(Ticket::id)).toList(); }

    public synchronized Ticket create(AccountId owner, String name, String message, Instant now) {
        message = text(message, 3);
        if (active(owner).isPresent()) throw new IllegalArgumentException("You already have an open request. Use /support reply <message>.");
        if (queue().size() >= MAX_OPEN) throw new IllegalArgumentException("The help queue is full. Please try again later.");
        if (own(owner).stream().anyMatch(t -> now.isBefore(t.created().plusSeconds(60)))) throw new IllegalArgumentException("Wait a minute before opening another request.");
        Ticket ticket = new Ticket(nextId, owner, name, now, null, "", false, 0, List.of(new Message(owner, name, false, now, message)));
        commit(ticket, Math.addExact(nextId, 1));
        return ticket;
    }

    public synchronized Ticket claim(long id, AccountId staff, String name) {
        Ticket old = open(id);
        if (old.owner().equals(staff)) throw new IllegalArgumentException("Another staff member must handle your request.");
        if (old.assigned() != null && !old.assigned().equals(staff)) throw new IllegalArgumentException("This request is already assigned to " + old.staffName() + ".");
        return commit(new Ticket(id, old.owner(), old.name(), old.created(), staff, name, false, old.read(), old.messages()), nextId);
    }

    public synchronized Ticket release(long id, AccountId staff) {
        Ticket old = open(id); requireAssigned(old, staff);
        return commit(new Ticket(id, old.owner(), old.name(), old.created(), null, "", false, old.read(), old.messages()), nextId);
    }

    public synchronized Ticket reply(long id, AccountId actor, String name, boolean staff, String message, Instant now) {
        Ticket old = open(id);
        if (staff) requireAssigned(old, actor);
        else if (!old.owner().equals(actor)) throw new IllegalArgumentException("That is not your help request.");
        if (old.messages().size() >= MAX_MESSAGES) throw new IllegalArgumentException("This conversation has reached 100 messages. Close it and open a follow-up request.");
        List<Message> messages = new ArrayList<>(old.messages());
        messages.add(new Message(actor, name, staff, now, text(message, 1)));
        return commit(new Ticket(id, old.owner(), old.name(), old.created(), old.assigned(), old.staffName(), false, old.read(), messages), nextId);
    }

    public synchronized Ticket close(long id, AccountId actor) {
        Ticket old = open(id);
        if (!old.owner().equals(actor)) requireAssigned(old, actor);
        return commit(new Ticket(id, old.owner(), old.name(), old.created(), old.assigned(), old.staffName(), true, old.read(), old.messages()), nextId);
    }

    public synchronized void read(long id, AccountId actor, int through) {
        Ticket old = get(id).orElseThrow(() -> new IllegalArgumentException("No such help request."));
        if (!old.owner().equals(actor)) throw new IllegalArgumentException("That is not your help request.");
        if (through < old.read() || through > old.messages().size()) throw new IllegalArgumentException("Invalid transcript position");
        if (old.read() == through) return;
        commit(new Ticket(id, old.owner(), old.name(), old.created(), old.assigned(), old.staffName(), old.closed(), through, old.messages()), nextId);
    }

    private Ticket open(long id) {
        Ticket ticket = get(id).orElseThrow(() -> new IllegalArgumentException("No such help request."));
        if (ticket.closed()) throw new IllegalArgumentException("That request is closed. Leave private chat with /support leave, or open a new request.");
        return ticket;
    }

    private static void requireAssigned(Ticket ticket, AccountId actor) {
        if (!actor.equals(ticket.assigned())) throw new IllegalArgumentException("Claim this request before responding. Only its assigned staff member can reply or close it.");
    }

    private Ticket commit(Ticket ticket, long next) {
        Map<Long, Ticket> changed = new LinkedHashMap<>(tickets);
        changed.put(ticket.id(), ticket);
        List<Long> closed = changed.values().stream().filter(Ticket::closed).sorted(Comparator.comparingLong(Ticket::id).reversed()).skip(CLOSED_RETAINED).map(Ticket::id).toList();
        closed.forEach(changed::remove);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1); yaml.set("next-id", next);
        List<Map<String, Object>> rows = new ArrayList<>();
        changed.values().forEach(t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.id()); row.put("owner", t.owner().toString()); row.put("name", t.name()); row.put("created", t.created().toString());
            row.put("staff", t.assigned() == null ? "" : t.assigned().toString()); row.put("staff-name", t.staffName()); row.put("closed", t.closed()); row.put("read", t.read());
            row.put("messages", t.messages().stream().map(m -> Map.of("actor", m.actor().toString(), "name", m.name(), "staff", m.staff(), "time", m.time().toString(), "text", m.text())).toList());
            rows.add(row);
        });
        yaml.set("tickets", rows);
        try { AtomicYamlFiles.save(yaml, file); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Could not save the help request", failure); }
        tickets = changed; nextId = next;
        return ticket;
    }

    public static String text(String raw, int minimum) {
        String value = raw.strip();
        if (value.length() < minimum || value.length() > 500 || value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Use " + minimum + "–500 characters on one line.");
        return value;
    }

    public record Message(AccountId actor, String name, boolean staff, Instant time, String text) { }
    public record Ticket(long id, AccountId owner, String name, Instant created, AccountId assigned, String staffName, boolean closed, int read, List<Message> messages) {
        public Ticket { messages = List.copyOf(messages); }
        public int unread() { return (int) messages.stream().skip(read).filter(Message::staff).count(); }
    }
}
