package net.nerdsmp.paper.social;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.webapi.WebsiteSocial;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.io.File;
import java.time.*;
import java.util.*;

/** Friends, blocks and private messages use canonical accounts, not client-platform UUIDs. */
public final class SocialManager implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PlayerSettingsService settings;
    private final SocialGraph graph;
    private final Duration lifetime;
    private final Map<UUID, Map<UUID, Instant>> requests = new HashMap<>();
    private final Map<UUID, UUID> replies = new HashMap<>();
    private final Map<UUID, Instant> lastMessage = new HashMap<>();
    /** Website sync for linked players; null when switched off. */
    private volatile WebsiteSocial website;
    public SocialManager(NerdSmpPlugin plugin, PlayerSettingsService settings) {
        this.plugin = plugin; this.settings = settings;
        graph = new SocialGraph(new File(plugin.getDataFolder(), "social.yml"), this::id);
        lifetime = Duration.ofSeconds(Math.clamp(plugin.getConfig().getLong("social.friend-request-seconds", 120), 10, 300));
    }
    private UUID id(UUID player) {
        return plugin.sessions().account(player).map(AccountId::value).orElseGet(() -> plugin.playerData().read(player,
            yaml -> UUID.fromString(yaml.getString("identity.account-id", player.toString()))));
    }
    private UUID id(Player player) { return id(player.getUniqueId()); }
    private Player online(UUID account) { return plugin.sessions().player(new AccountId(account)).map(Bukkit::getPlayer).orElse(null); }
    private String name(UUID account) { Player p = online(account); return p == null ? graph.profile(account).name() : p.getName(); }
    private UUID known(String raw) {
        Player player = Bukkit.getPlayerExact(raw); if (player != null && plugin.sessions().account(player.getUniqueId()).isPresent()) return id(player);
        try { UUID uuid = UUID.fromString(raw); if (graph.profiles().containsKey(uuid)) return uuid; } catch (IllegalArgumentException ignored) { }
        var matches = graph.profiles().entrySet().stream().filter(e -> e.getValue().name().equalsIgnoreCase(raw)).map(Map.Entry::getKey).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    public boolean isIgnoring(UUID viewer, UUID speaker) { return graph.profile(id(viewer)).ignored().contains(id(speaker)); }
    public boolean blocked(AccountId first, AccountId second) { return graph.blocked(first.value(), second.value()); }
    public Optional<AccountId> knownAccount(String raw) { return Optional.ofNullable(known(raw)).map(AccountId::new); }
    public String accountName(AccountId account) { return name(account.value()); }
    public boolean areFriends(UUID a, UUID b) { return graph.profile(id(a)).friends().contains(id(b)); }
    public boolean canContact(Player a, Player b, PlayerSettingsService.Setting preference) {
        return a != null && b != null && a != b && a.isOnline() && b.isOnline() && a.canSee(b) && b.canSee(a)
            && plugin.sessions().account(a.getUniqueId()).isPresent() && plugin.sessions().account(b.getUniqueId()).isPresent()
            && !id(a).equals(id(b)) && !graph.blocked(id(a), id(b)) && settings.enabled(b.getUniqueId(), preference);
    }
    public void website(WebsiteSocial website) { this.website = website; }
    private void toWebsite(String type, UUID actor, UUID target) {
        WebsiteSocial sync = website; if (sync != null) sync.queue(type, new AccountId(actor), new AccountId(target), "", false);
    }
    /** Applies changes made on the website. They are never queued back to it. */
    public WebsiteSocial.Port websitePort() {
        return new WebsiteSocial.Port() {
            @Override public void friend(AccountId first, AccountId second, boolean friends) {
                UUID a = first.value(), b = second.value();
                if (!friends) { graph.unfriend(a, b); return; }
                if (graph.profile(a).friends().contains(b)) return;
                try { graph.friend(a, b); } catch (IllegalArgumentException refused) { return; }
                removeRequest(a, b); removeRequest(b, a);
                Player one = online(a), two = online(b);
                if (one != null) Chat.success(one, "You and " + who(b) + " are now friends (added on the website).");
                if (two != null) Chat.success(two, "You and " + who(a) + " are now friends (added on the website).");
            }
            @Override public void block(AccountId actor, AccountId target, boolean blocked) {
                UUID a = actor.value(), b = target.value();
                if (a.equals(b) || graph.profile(a).ignored().contains(b) == blocked) return;
                graph.ignore(a, b, blocked); removeRequest(a, b); removeRequest(b, a);
                if (blocked) { replies.remove(a, b); replies.remove(b, a); }
            }
            @Override public void message(AccountId target, String fromName, Optional<AccountId> from, String body) {
                Player player = online(target.value());
                if (player == null || !settings.enabled(player.getUniqueId(), PlayerSettingsService.Setting.DIRECT_MESSAGES)) return;
                if (from.isPresent() && graph.blocked(from.get().value(), target.value())) return;
                String reply = from.map(account -> "/msg " + name(account.value()) + " ").orElse("");
                String label = "[Web] From " + Chat.escape(fromName);
                Chat.send(player, "<light_purple>" + (reply.isEmpty() ? label
                    : "<click:suggest_command:'" + reply + "'><hover:show_text:'<gray>Click to reply</gray>'>" + label + "</hover></click>")
                    + "</light_purple> <white>" + Chat.escape(body) + "</white>");
                from.ifPresent(account -> replies.put(target.value(), account.value()));
            }
            @Override public WebsiteSocial.Snapshot snapshot(AccountId account) {
                var profile = graph.profile(account.value());
                return new WebsiteSocial.Snapshot(
                    profile.friends().stream().map(AccountId::new).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    profile.ignored().stream().map(AccountId::new).collect(java.util.stream.Collectors.toUnmodifiableSet()));
            }
        };
    }
    public void ready(Player player) {
        UUID account = id(player); graph.remember(account, player.getName());
        WebsiteSocial sync = website;
        if (sync != null) sync.linked(new AccountId(account)).ifPresent(linked -> {
            int messages = linked.unreadMessages(), notices = linked.unreadNotifications();
            if (messages + notices > 0) Chat.info(player, "On the website: <white>" + messages + "</white> unread message" + (messages == 1 ? "" : "s")
                + " and <white>" + notices + "</white> notification" + (notices == 1 ? "" : "s") + ".");
        });
        for (UUID friendId : graph.profile(account).friends()) {
            Player friend = online(friendId);
            if (friend != null && friend.canSee(player) && settings.enabled(friend.getUniqueId(), PlayerSettingsService.Setting.FRIEND_NOTICES))
                Chat.info(friend, "Friend online: <white>" + Chat.escape(player.getName()) + "</white>");
        }
    }
    public boolean handleSocial(CommandSender sender) {
        if (sender instanceof Player player) plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.COMMUNITY);
        else Chat.playersOnly(sender); return true;
    }
    public boolean handleFriends(CommandSender sender, String[] args) {
        Player player = player(sender); if (player == null) return true;
        try {
            UUID actor = id(player); String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
            if (action.equals("list")) { list(player); return true; }
            if (args.length != 2) throw new Usage("/friends <add|accept|deny|remove> <player>");
            UUID other = known(args[1]); if (other == null || other.equals(actor)) throw new IllegalArgumentException("Choose another known player.");
            Player target = online(other);
            switch (action) {
                case "add" -> {
                    if (!canContact(player, target, PlayerSettingsService.Setting.FRIEND_REQUESTS)) throw new IllegalArgumentException("That player is not accepting friend requests from you.");
                    if (graph.profile(actor).friends().contains(other)) { Chat.notice(player, "You are already friends."); return true; }
                    if (pending(actor).contains(other)) { befriend(actor, other); return true; }
                    if (pending(other).contains(actor)) throw new IllegalArgumentException("Your request is still waiting. It cannot be extended by resending.");
                    if (pending(other).size() >= 50) throw new IllegalArgumentException("That player's request list is full. Try later.");
                    requests.computeIfAbsent(other, k -> new HashMap<>()).put(actor, Instant.now().plus(lifetime));
                    Chat.success(player, "Friend request sent to " + who(other) + ".");
                    Chat.info(target, "<white>" + Chat.escape(player.getName()) + "</white> wants to be friends. " + answer(actor));
                }
                case "accept" -> {
                    if (!pending(actor).contains(other) || !canContact(player, target, PlayerSettingsService.Setting.FRIEND_REQUESTS)
                        || !settings.enabled(player.getUniqueId(), PlayerSettingsService.Setting.FRIEND_REQUESTS)) throw new IllegalArgumentException("That friend request is no longer available.");
                    befriend(actor, other);
                }
                case "deny" -> { removeRequest(actor, other); Chat.success(player, "Friend request denied."); }
                case "remove" -> { graph.unfriend(actor, other); toWebsite("unfriend", actor, other); Chat.success(player, "Removed " + who(other) + " from your friends."); }
                default -> throw new Usage("/friends [list|add|accept|deny|remove] [player]");
            }
        } catch (RuntimeException error) { failed(player, error); }
        return true;
    }
    private void befriend(UUID a, UUID b) {
        graph.friend(a, b); removeRequest(a, b); removeRequest(b, a); toWebsite("friend", a, b);
        Player first = online(a), second = online(b);
        if (first != null) Chat.success(first, "You and " + who(b) + " are now friends.");
        if (second != null) Chat.success(second, "You and " + who(a) + " are now friends.");
    }
    private List<UUID> pending(UUID target) {
        Map<UUID, Instant> values = requests.get(target); if (values == null) return List.of();
        Instant now = Instant.now(); values.entrySet().removeIf(e -> !e.getValue().isAfter(now));
        if (values.isEmpty()) requests.remove(target); return List.copyOf(values.keySet());
    }
    private void removeRequest(UUID target, UUID source) { var values = requests.get(target); if (values != null) { values.remove(source); if (values.isEmpty()) requests.remove(target); } }
    private void list(Player player) {
        var profile = graph.profile(id(player));
        Chat.info(player, "Friends <white>" + profile.friends().size() + "/200</white>");
        if (profile.friends().isEmpty()) { Chat.info(player, "Add friends to keep in touch."); Chat.usage(player, "/friends add <player>"); }
        profile.friends().stream().sorted(Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER)).forEach(friend -> {
            Player target = online(friend); boolean visible = target != null && player.canSee(target);
            String message = "<click:suggest_command:'/msg " + name(friend) + " '><hover:show_text:'<gray>Click to message</gray>'>"
                + (visible ? "<green>Online</green> " : "<gray>Offline</gray> ") + "<white>" + Chat.escape(name(friend)) + "</white></hover></click>";
            Chat.send(player, message);
        });
        for (UUID source : pending(id(player))) Chat.notice(player, "Request from " + who(source) + ". " + answer(source));
    }
    public boolean handleIgnore(CommandSender sender, String[] args, boolean remove) {
        Player player = player(sender); if (player == null) return true;
        try {
            if (args.length != 1) throw new Usage("/" + (remove ? "unignore" : "ignore") + " <player>");
            UUID other = known(args[0]); if (other == null) throw new IllegalArgumentException("No player with that name has joined.");
            UUID actor = id(player); graph.ignore(actor, other, !remove); removeRequest(actor, other); removeRequest(other, actor);
            toWebsite(remove ? "unblock" : "block", actor, other);
            replies.remove(actor, other); replies.remove(other, actor);
            Chat.success(player, remove ? "You no longer ignore " + who(other) + "." : "You now ignore " + who(other) + ".");
        } catch (RuntimeException error) { failed(player, error); } return true;
    }
    public boolean handleMessage(CommandSender sender, String[] args, boolean reply) {
        Player player = player(sender); if (player == null) return true;
        UUID actor = id(player), targetId = reply ? replies.get(actor) : args.length > 0 ? known(args[0]) : null;
        Player target = targetId == null ? null : online(targetId); int from = reply ? 0 : 1;
        if (reply && targetId == null) { Chat.error(player, "You have no recent conversation."); Chat.usage(player, "/msg <player> <message>"); return true; }
        if (args.length <= from) { Chat.usage(player, reply ? "/reply <message>" : "/msg <player> <message>"); return true; }
        if (target == null) { if (!toWebsiteInbox(player, actor, targetId, args, from)) Chat.error(player, "That player is not online."); return true; }
        if (!plugin.staff().canCommunicate(player.getUniqueId()) || !canContact(player, target, PlayerSettingsService.Setting.DIRECT_MESSAGES)) {
            Chat.error(player, "You cannot send a message to that player right now."); return true;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        if (message.length() > 512 || message.isBlank()) { Chat.error(player, "Keep messages between 1 and 512 characters."); return true; }
        Instant now = Instant.now();
        if (lastMessage.getOrDefault(actor, Instant.EPOCH).plusMillis(750).isAfter(now)) { Chat.notice(player, "Please wait a moment before sending another message."); return true; }
        lastMessage.put(actor, now);
        Chat.send(player, "<light_purple>[To " + Chat.escape(target.getName()) + "]</light_purple> <white>" + Chat.escape(message) + "</white>");
        Chat.send(target, "<light_purple><click:suggest_command:'/reply '>[From " + Chat.escape(player.getName()) + "]</click></light_purple> <white>" + Chat.escape(message) + "</white>");
        replies.put(actor, targetId); replies.put(targetId, actor); return true;
    }
    /** /msg to an offline player who linked the website: deliver it to their website inbox instead. */
    private boolean toWebsiteInbox(Player player, UUID actor, UUID targetId, String[] args, int from) {
        WebsiteSocial sync = website;
        if (sync == null || targetId == null || args.length <= from) return false;
        var recipient = sync.linked(new AccountId(targetId));
        if (recipient.isEmpty()) return false;
        if (sync.linked(new AccountId(actor)).isEmpty()) {
            Chat.error(player, who(targetId) + " is offline. Link your website account with <white>/weblink</white> to message them there.");
            return true;
        }
        if (!plugin.staff().canCommunicate(player.getUniqueId()) || graph.blocked(actor, targetId)
            || !recipient.get().accepts(graph.profile(actor).friends().contains(targetId))) {
            Chat.error(player, "You cannot send a message to that player right now."); return true;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        if (message.length() > 512 || message.isBlank()) { Chat.error(player, "Keep messages between 1 and 512 characters."); return true; }
        Instant now = Instant.now();
        if (lastMessage.getOrDefault(actor, Instant.EPOCH).plusMillis(750).isAfter(now)) { Chat.notice(player, "Please wait a moment before sending another message."); return true; }
        lastMessage.put(actor, now);
        sync.queue("message", new AccountId(actor), new AccountId(targetId), message, false);
        Chat.send(player, "<light_purple>[To " + Chat.escape(name(targetId)) + " · website]</light_purple> <white>" + Chat.escape(message) + "</white>");
        Chat.info(player, who(targetId) + " is offline, so this went to their Aerborne website inbox.");
        replies.put(actor, targetId);
        return true;
    }
    public List<String> tabComplete(String command, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        UUID actor = id(player);
        if (command.equalsIgnoreCase("friends")) {
            if (args.length == 1) return List.of("list","add","accept","deny","remove");
            if (args.length == 2 && Set.of("accept","deny").contains(args[0].toLowerCase(Locale.ROOT))) return pending(actor).stream().map(this::name).toList();
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return graph.profile(actor).friends().stream().map(this::name).toList();
        }
        if (command.equalsIgnoreCase("unignore") && args.length == 1) return graph.profile(actor).ignored().stream().map(this::name).toList();
        var preference = command.equalsIgnoreCase("friends") ? PlayerSettingsService.Setting.FRIEND_REQUESTS : PlayerSettingsService.Setting.DIRECT_MESSAGES;
        return Bukkit.getOnlinePlayers().stream().filter(p -> canContact(player, p, preference)).map(Player::getName).sorted().toList();
    }
    @EventHandler(priority = EventPriority.LOWEST) public void quit(PlayerQuitEvent event) {
        UUID actor = id(event.getPlayer()); requests.remove(actor); requests.values().forEach(map -> map.remove(actor));
        replies.remove(actor); replies.values().removeIf(actor::equals); lastMessage.remove(actor);
    }
    private Player player(CommandSender sender) {
        if (sender instanceof Player player && plugin.sessions().account(player.getUniqueId()).isPresent()) return player;
        if (sender instanceof Player player) Chat.accountLoading(player); else Chat.playersOnly(sender);
        return null;
    }
    /** A player's name in white, safe inside markup. */
    private String who(UUID account) { return "<white>" + Chat.escape(name(account)) + "</white>"; }
    /** Clickable answers to a friend request from {@code source}; the account id keeps the command unambiguous. */
    private static String answer(UUID source) {
        return "<click:run_command:'/friends accept " + source + "'><hover:show_text:'<gray>Click to accept</gray>'><green>[Accept]</green></hover></click> "
            + "<click:run_command:'/friends deny " + source + "'><hover:show_text:'<gray>Click to deny</gray>'><red>[Deny]</red></hover></click>";
    }
    private void failed(Player player, RuntimeException error) {
        if (error instanceof Usage usage) Chat.usage(player, usage.getMessage());
        else if (error instanceof IllegalArgumentException) Chat.error(player, Chat.escape(error.getMessage()));
        else { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Social change failed", error); Chat.error(player, "That change could not be saved. Nothing was changed; please contact staff."); }
    }
    /** A wrong command shape; the message is the usage syntax. */
    private static final class Usage extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private Usage(String syntax) { super(syntax); }
    }
    @Override public void close() { requests.clear(); replies.clear(); lastMessage.clear(); }
}
