package net.nerdsmp.paper.support;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Support never enters public chat, private-message forwarding, or the public chat console. */
public final class SupportService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final SupportStore store;
    private final Map<UUID, Selection> conversations = new ConcurrentHashMap<>();
    private final Map<AccountId, Long> nextMessage = new java.util.HashMap<>();

    public SupportService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        store = new SupportStore(new File(plugin.getDataFolder(), "support.yml"));
    }

    public SupportStore store() { return store; }
    public boolean privateChat(UUID player) { return conversations.containsKey(player); }
    public void leave(Player player) {
        if (conversations.remove(player.getUniqueId()) != null) Chat.notice(player, "Private support chat ended. Your previous channel is active again.");
    }

    public boolean handle(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (actor == null) { Chat.accountLoading(player); return true; }
        try {
            if (command.equals("helpme")) { create(player, actor, String.join(" ", args)); return true; }
            String action = args.length == 0 ? "read" : args[0].toLowerCase(java.util.Locale.ROOT);
            switch (action) {
                case "new" -> create(player, actor, join(args, 1));
                case "leave" -> leave(player);
                case "help" -> help(player);
                case "history" -> {
                    var own = store.own(actor);
                    if (own.isEmpty()) Chat.info(player, "You have no saved requests. Use " + Chat.syntax("/helpme <what you need>") + ".");
                    own.stream().limit(10).forEach(t -> Chat.info(player, "<white>#" + t.id() + "</white> " + (t.closed() ? "Closed" : "Open") + " · " + t.unread() + " unread · " + Chat.command("support read " + t.id())));
                }
                case "queue" -> {
                    requireStaff(player);
                    Chat.info(player, "<white>Help queue · " + store.queue().size() + " open</white>");
                    store.queue().stream().limit(30).forEach(t -> Chat.info(player, "<white>#" + t.id() + " " + Chat.escape(t.name()) + "</white> · "
                        + (t.assigned() == null ? "Waiting" : "Assigned: " + Chat.escape(t.staffName())) + " · " + Chat.command("support read " + t.id()) + " " + Chat.command("support claim " + t.id())));
                    Chat.info(player, "Claim before replying. " + Chat.command("support help") + " lists controls.");
                }
                case "claim", "release" -> {
                    requireStaff(player);
                    if (args.length != 2) throw new IllegalArgumentException("Use /support " + action + " <id>.");
                    SupportStore.Ticket ticket = visible(player, actor, id(args[1]));
                    if (action.equals("claim")) {
                        ticket = store.claim(ticket.id(), actor, player.getName());
                        notifyParticipants(ticket, "Your request is now with " + player.getName() + ". Use /support chat to speak privately.");
                        Chat.success(player, "Claimed #" + ticket.id() + ". " + Chat.command("support chat " + ticket.id()) + " opens private chat.");
                    } else {
                        if (!actor.equals(ticket.assigned()) && !player.hasPermission("nerdsmp.support.manage")) throw new IllegalArgumentException("Only the assigned staff member or a support manager can release this request.");
                        if (ticket.assigned() == null) throw new IllegalArgumentException("This request is already waiting for staff.");
                        ticket = store.release(ticket.id(), ticket.assigned());
                        notifyParticipants(ticket, "Your request is back in the help queue. Messages here remain private.");
                        announceQueue(ticket);
                        Chat.success(player, "Released #" + ticket.id() + ". Leave private chat with " + Chat.command("support leave") + ".");
                    }
                }
                case "reply" -> {
                    boolean explicit = args.length > 1 && args[1].startsWith("#");
                    SupportStore.Ticket ticket = target(player, actor, explicit ? args[1] : null);
                    reply(player, actor, ticket, join(args, explicit ? 2 : 1));
                }
                case "read", "chat", "close" -> {
                    SupportStore.Ticket ticket = target(player, actor, args.length > 1 ? args[1] : null);
                    if (action.equals("read")) {
                        int page = args.length > 2 ? page(args[2]) : -1;
                        read(player, actor, ticket, page);
                    }
                    else if (action.equals("close")) {
                        store.close(ticket.id(), actor);
                        notifyParticipants(ticket, "Request #" + ticket.id() + " closed by " + player.getName() + ". Private chat stays locked until /support leave.");
                    } else {
                        if (ticket.closed()) throw new IllegalArgumentException("This request is closed. Use /support read " + ticket.id() + " to read it.");
                        authorizeReply(player, actor, ticket);
                        conversations.put(player.getUniqueId(), new Selection(actor, ticket.id()));
                        player.closeInventory();
                        Chat.success(player, "<white>PRIVATE SUPPORT #" + ticket.id() + "</white> · Your chat now goes only to this request.");
                        Chat.info(player, "Staff can review its saved transcript. " + Chat.command("support leave") + " returns to your previous channel.");
                        read(player, actor, ticket, -1);
                    }
                }
                default -> help(player);
            }
        } catch (IllegalArgumentException failure) { Chat.error(player, Chat.escape(failure.getMessage())); }
        catch (IllegalStateException failure) { failed(player, failure); }
        return true;
    }

    // Registered before editor prompts at LOWEST. A support message cannot become editor input or public chat.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        Selection selected = conversations.get(event.getPlayer().getUniqueId());
        if (selected == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
            if (conversations.get(player.getUniqueId()) != selected || !plugin.sessions().account(player.getUniqueId()).filter(selected.account()::equals).isPresent()) {
                Chat.notice(player, "Support chat changed. That message was not sent."); return;
            }
            try { reply(player, selected.account(), visible(player, selected.account(), selected.ticket()), message); }
            catch (IllegalArgumentException failure) { Chat.error(player, Chat.escape(failure.getMessage())); }
            catch (IllegalStateException failure) { failed(player, failure); }
        });
    }

    @EventHandler public void quit(PlayerQuitEvent event) { conversations.remove(event.getPlayer().getUniqueId()); plugin.sessions().account(event.getPlayer().getUniqueId()).ifPresent(nextMessage::remove); }
    @Override public void close() { conversations.clear(); nextMessage.clear(); }

    public void ready(Player player) {
        AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (actor == null) return;
        var own = store.own(actor);
        int unread = own.stream().mapToInt(SupportStore.Ticket::unread).sum();
        if (unread > 0) Chat.notice(player, "You have " + unread + " unread support replies. " + Chat.command("support history") + " lists your conversations.");
        store.active(actor).ifPresent(t -> Chat.info(player, "Help request #" + t.id() + " is open. " + Chat.command("support") + " resumes it."));
        if (staff(player) && !store.queue().isEmpty()) Chat.notice(player, "There are " + store.queue().size() + " open help requests. " + Chat.command("support queue"));
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) return staff(sender) ? List.of("new", "read", "reply", "chat", "leave", "close", "history", "help", "queue", "claim", "release") : List.of("new", "read", "reply", "chat", "leave", "close", "history", "help");
        if (args.length == 2 && sender instanceof Player player && List.of("read", "chat", "close", "claim", "release", "reply").contains(args[0])) {
            AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null);
            if (actor == null) return List.of();
            return (staff(player) ? store.queue() : store.own(actor)).stream().map(t -> (args[0].equals("reply") ? "#" : "") + t.id()).toList();
        }
        return List.of();
    }

    private void create(Player player, AccountId actor, String message) {
        if (message.isBlank()) { help(player); return; }
        SupportStore.Ticket ticket = store.create(actor, player.getName(), message, Instant.now());
        conversations.put(player.getUniqueId(), new Selection(actor, ticket.id()));
        player.closeInventory();
        Chat.success(player, "Help request #" + ticket.id() + " saved. Staff will respond here; replies are saved while you are offline.");
        Chat.info(player, "<aqua>PRIVATE SUPPORT selected.</aqua> Your normal chat now stays in this request. " + Chat.command("support leave") + " returns to your previous channel. Staff can review the transcript.");
        announceQueue(ticket);
    }

    private void announceQueue(SupportStore.Ticket ticket) {
        for (Player online : Bukkit.getOnlinePlayers()) if (staff(online) && plugin.sessions().account(online.getUniqueId()).isPresent())
            Chat.notice(online, "Help #" + ticket.id() + " from " + Chat.escape(ticket.name()) + " · " + Chat.command("support read " + ticket.id()) + " · " + Chat.command("support claim " + ticket.id()));
    }

    private SupportStore.Ticket target(Player player, AccountId actor, String raw) {
        if (raw != null) return visible(player, actor, id(raw));
        Selection selected = conversations.get(player.getUniqueId());
        if (selected != null && selected.account().equals(actor)) return visible(player, actor, selected.ticket());
        return store.active(actor).orElseThrow(() -> new IllegalArgumentException("No open request. Use /helpme <what you need>, /support history, or /support help."));
    }

    private SupportStore.Ticket visible(Player player, AccountId actor, long id) {
        SupportStore.Ticket ticket = store.get(id).orElse(null);
        if (ticket == null || (!ticket.owner().equals(actor) && !staff(player))) throw new IllegalArgumentException("No accessible help request with that number.");
        return ticket;
    }

    private void authorizeReply(Player player, AccountId actor, SupportStore.Ticket ticket) {
        if (actor.equals(ticket.owner())) return;
        requireStaff(player);
        if (!actor.equals(ticket.assigned())) throw new IllegalArgumentException("Claim this request before chatting. It may have been reassigned.");
    }

    private void reply(Player player, AccountId actor, SupportStore.Ticket ticket, String raw) {
        authorizeReply(player, actor, ticket);
        long now = System.currentTimeMillis();
        if (nextMessage.getOrDefault(actor, 0L) > now) throw new IllegalArgumentException("Wait two seconds between support messages.");
        SupportStore.Ticket saved = store.reply(ticket.id(), actor, player.getName(), !actor.equals(ticket.owner()), raw, Instant.now());
        nextMessage.put(actor, now + 2_000);
        Component message = line(saved, saved.messages().getLast());
        player.sendMessage(message);
        AccountId other = actor.equals(saved.owner()) ? saved.assigned() : saved.owner();
        Player recipient = online(other);
        if (recipient != null && recipient != player && (other.equals(saved.owner()) || staff(recipient))) recipient.sendMessage(message);
        else Chat.notice(player, other == null ? "Saved in your request. A staff member has not claimed it yet." : "Saved. The other participant is offline or unavailable.");
    }

    private void read(Player player, AccountId actor, SupportStore.Ticket ticket, int requested) {
        Chat.info(player, "<white>Support #" + ticket.id() + " · " + (ticket.closed() ? "Closed" : ticket.assigned() == null ? "Waiting for staff" : "Assigned: " + Chat.escape(ticket.staffName())) + "</white>");
        int pages = Math.max(1, (ticket.messages().size() + 9) / 10);
        int selected = requested < 0 ? actor.equals(ticket.owner()) && ticket.unread() > 0 ? ticket.read() / 10 : pages - 1 : requested;
        if (selected < 0 || selected >= pages) throw new IllegalArgumentException("Choose a transcript page from 1 to " + pages + ".");
        int start = selected * 10, end = Math.min(start + 10, ticket.messages().size());
        for (int i = start; i < end; i++) player.sendMessage(line(ticket, ticket.messages().get(i)));
        Chat.info(player, "Page " + (selected + 1) + "/" + pages + (selected + 1 < pages ? " · " + Chat.command("support read " + ticket.id() + " " + (selected + 2)) : ""));
        if (actor.equals(ticket.owner()) && start <= ticket.read()) store.read(ticket.id(), actor, Math.max(ticket.read(), end));
        Chat.info(player, Chat.command("support chat " + ticket.id()) + " · " + Chat.command("support close " + ticket.id()) + " · " + Chat.command("support help"));
    }

    private Component line(SupportStore.Ticket ticket, SupportStore.Message message) {
        return Component.text("[Support #" + ticket.id() + "] ", NamedTextColor.AQUA)
            .append(Component.text((message.staff() ? "Staff " : "") + message.name() + ": ", NamedTextColor.YELLOW))
            .append(Component.text(message.text(), NamedTextColor.WHITE));
    }

    private void notifyParticipants(SupportStore.Ticket ticket, String message) {
        Player owner = online(ticket.owner()); if (owner != null) Chat.notice(owner, Chat.escape(message));
        Player assigned = online(ticket.assigned()); if (assigned != null && staff(assigned)) Chat.notice(assigned, Chat.escape(message));
    }
    private Player online(AccountId actor) { return actor == null ? null : plugin.sessions().player(actor).map(Bukkit::getPlayer).orElse(null); }
    private static boolean staff(CommandSender sender) { return sender.hasPermission("nerdsmp.support.staff"); }
    private static void requireStaff(CommandSender sender) { if (!staff(sender)) throw new IllegalArgumentException("Only support staff can do that."); }
    private static long id(String raw) { try { long id = Long.parseLong(raw.startsWith("#") ? raw.substring(1) : raw); if (id > 0) return id; } catch (NumberFormatException ignored) { } throw new IllegalArgumentException("Use a valid help request number."); }
    private static int page(String raw) { try { int value = Integer.parseInt(raw); if (value > 0) return value - 1; } catch (NumberFormatException ignored) { } throw new IllegalArgumentException("Use a valid transcript page number."); }
    private static String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)); }
    private void failed(Player player, RuntimeException failure) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Support persistence failed", failure); Chat.error(player, "The help request could not be saved. Your message was not delivered; please try again."); }
    private void help(Player player) {
        Chat.info(player, "<white>Private staff support</white> · " + Chat.syntax("/helpme <what you need>") + " opens a saved request.");
        Chat.info(player, Chat.syntax("/support reply <message>") + " · " + Chat.command("support chat") + " · " + Chat.command("support leave"));
        Chat.info(player, Chat.command("support history") + " · " + Chat.syntax("/support read <id>") + " · " + Chat.command("support close"));
        Chat.info(player, "Staff can review transcripts. Replies are saved offline. For player misconduct, use " + Chat.syntax("/report <player> <reason>") + ".");
        if (staff(player)) Chat.info(player, Chat.command("support queue") + " · /support claim|release <id> · /support reply #<id> <message> · /support chat|close <id>");
    }

    private record Selection(AccountId account, long ticket) { }
}
