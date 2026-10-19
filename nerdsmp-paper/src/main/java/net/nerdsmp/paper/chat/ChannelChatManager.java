package net.nerdsmp.paper.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.PlayerPresentationService;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.social.SocialManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import net.nerdsmp.api.service.ClaimService.HomesteadView;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public final class ChannelChatManager implements Listener {
    private final NerdSmpPlugin plugin;
    private final PlayerSettingsService settings;
    private final SocialManager social;
    private final PlayerPresentationService presentation;
    private final SessionRegistry sessions;
    private final double localRadiusSquared;
    private final Map<UUID, HomesteadChatPolicy.Selection> homesteadChannels = new HashMap<>();
    private final Map<UUID, IslandChatPolicy.Selection> islandChannels = new HashMap<>();

    public ChannelChatManager(
        NerdSmpPlugin plugin,
        PlayerSettingsService settings,
        SocialManager social,
        PlayerPresentationService presentation,
        SessionRegistry sessions
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.social = social;
        this.presentation = presentation;
        this.sessions = sessions;
        double radius = plugin.getConfig().getDouble("chat.local-radius", 100.0D);
        localRadiusSquared = radius * radius;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
        Player sender = event.getPlayer();
        Component message = event.message();
        AccountId identity = sessions.account(sender.getUniqueId()).orElse(null);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sender.isOnline() && Bukkit.getPlayer(sender.getUniqueId()) == sender && identity != null
                && sessions.account(sender.getUniqueId()).filter(identity::equals).isPresent()) sendSelected(sender, message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { homesteadChannels.remove(event.getPlayer().getUniqueId()); islandChannels.remove(event.getPlayer().getUniqueId()); }

    public boolean handleIsland(CommandSender source, String[] args) {
        Player player = player(source); if (player == null || plugin.skyblock() == null) return true;
        AccountId actor = sessions.account(player.getUniqueId()).orElse(null); if (actor == null) { Chat.accountLoading(player); return true; }
        AccountId owner = args.length == 0 ? actor : resolveOwner(args[0]);
        var island = owner == null ? null : plugin.skyblock().islands().owned(owner).orElse(null);
        if (island == null && args.length == 0) {
            var homes = plugin.skyblock().islands().homes(actor); if (homes.size() == 1) island = homes.getFirst();
        }
        if (island == null || !island.member(actor)) {
            Chat.notice(player, "Choose one of your co-ops."); Chat.usage(player, "/island chat <owner> [message]"); return true;
        }
        var selection = new IslandChatPolicy.Selection(actor, island.owner(), island.id());
        if (args.length > 1) sendIsland(player, selection, Component.text(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
        else {
            settings.channel(player.getUniqueId(), ChatChannel.ISLAND.id()); islandChannels.put(player.getUniqueId(), selection);
            Chat.success(player, "Chat set to <white>" + Chat.escape(island.name()) + "</white>. Only your co-op can hear you.");
            Chat.info(player, "Leave with " + Chat.command("channel global") + ".");
        }
        return true;
    }

    /** Explicit owner selection avoids sending to the wrong home when helping several settlements. */
    public boolean handleHomestead(CommandSender source, String[] args) {
        Player player = player(source);
        if (player == null) return true;
        AccountId actor = sessions.account(player.getUniqueId()).orElse(null);
        if (actor == null) { Chat.accountLoading(player); return true; }
        if (args.length == 0) {
            Chat.usage(player, "/claim chat <owner> [message]");
            Chat.info(player, "Without a message, this selects that homestead's channel.");
            return true;
        }
        AccountId owner = resolveOwner(args[0]);
        if (owner == null) {
            Chat.error(player, "No player with that name owns a homestead. See your homes with " + Chat.command("claim mine") + "."); return true;
        }
        var selection = new HomesteadChatPolicy.Selection(actor, owner);
        HomesteadView home = snapshot(owner);
        if (!selection.permits(actor, home)) {
            Chat.error(player, "You must be a resident of that homestead to use its chat."); return true;
        }
        if (args.length == 1) {
            homesteadChannels.remove(player.getUniqueId());
            settings.channel(player.getUniqueId(), ChatChannel.HOMESTEAD.id());
            homesteadChannels.put(player.getUniqueId(), selection);
            Chat.success(player, "Chat set to <white>" + Chat.escape(home.name()) + "</white>.");
            Chat.info(player, "Leave with " + Chat.command("channel global") + ".");
        } else {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (message.length() > 512) Chat.error(player, "Keep chat messages under 512 characters.");
            else sendHomestead(player, selection, Component.text(message));
        }
        return true;
    }

    public boolean handleChannel(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            ChatChannel selected = selected(player);
            Chat.send(player, "<gray>Active channel</gray> " + selected.tag() + " <white>" + selected.displayName() + "</white>");
            Chat.usage(player, "/channel [global|local|trade|party|" + (plugin.skyblockEnabled() ? "island" : "homestead") + "|staff] [message]");
            if (selected == ChatChannel.HOMESTEAD) Chat.usage(player, "/claim chat <owner>");
            return true;
        }
        ChatChannel channel = ChatChannel.parse(args[0]);
        if (channel == null || !allowed(player, channel)) {
            Chat.error(player, "That channel is not available to you.");
            return true;
        }
        if (args.length == 1) {
            if (channel == ChatChannel.ISLAND) return handleIsland(sender, new String[0]);
            settings.channel(player.getUniqueId(), channel.id());
            Chat.success(player, "Chat channel set to <white>" + channel.displayName() + "</white>.");
            return true;
        }
        send(player, channel, Component.text(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
        return true;
    }

    public boolean handleDirect(CommandSender sender, ChatChannel channel, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (!allowed(player, channel)) { Chat.error(player, "That channel is not available to you."); return true; }
        if (args.length == 0) {
            settings.channel(player.getUniqueId(), channel.id());
            Chat.success(player, "Chat channel set to <white>" + channel.displayName() + "</white>.");
            return true;
        }
        send(player, channel, Component.text(String.join(" ", args)));
        return true;
    }

    public List<String> tabComplete(String command, String[] args) {
        if (command.equalsIgnoreCase("channel") && args.length == 1) {
            return Arrays.stream(ChatChannel.values()).filter(c -> c != (plugin.skyblockEnabled() ? ChatChannel.HOMESTEAD : ChatChannel.ISLAND)).map(ChatChannel::id).toList();
        }
        return List.of();
    }

    private void sendSelected(Player sender, Component message) {
        ChatChannel channel = selected(sender);
        if (!allowed(sender, channel)) {
            Chat.notice(sender, "Message not sent. Choose an available channel with " + Chat.command("channel") + "."); return;
        }
        // Never leak a private message to global after removal, relinking, or a restart.
        if (channel == ChatChannel.HOMESTEAD) {
            sendHomestead(sender, homesteadChannels.get(sender.getUniqueId()), message);
            return;
        }
        send(sender, channel, message);
    }

    private void send(Player sender, ChatChannel channel, Component message) {
        if (!allowed(sender, channel) || !validMessage(sender, message)) return;
        if (channel == ChatChannel.ISLAND) { sendIsland(sender, islandChannels.get(sender.getUniqueId()), message); return; }
        if (channel == ChatChannel.HOMESTEAD) {
            sendHomestead(sender, homesteadChannels.get(sender.getUniqueId()), message);
            return;
        }
        if (!plugin.staff().canCommunicate(sender.getUniqueId())) {
            Chat.error(sender, "You cannot use chat while muted.");
            return;
        }
        if (channel == ChatChannel.PARTY) {
            sendParty(sender, message);
            return;
        }
        Collection<? extends Player> candidates = channel == ChatChannel.STAFF
            ? Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission("nerdsmp.staff")).toList()
            : Bukkit.getOnlinePlayers();
        Component line = line(sender, channel, message);
        int heard = 0;
        for (Player viewer : candidates) {
            if (social.isIgnoring(viewer.getUniqueId(), sender.getUniqueId())) {
                continue;
            }
            if (channel == ChatChannel.LOCAL && (!viewer.getWorld().equals(sender.getWorld())
                || viewer.getLocation().distanceSquared(sender.getLocation()) > localRadiusSquared)) {
                continue;
            }
            viewer.sendMessage(line);
            heard++;
        }
        if (channel == ChatChannel.LOCAL && heard <= 1) {
            Chat.info(sender, "No one nearby heard you.");
        }
    }

    private void sendParty(Player sender, Component message) {
        AccountId account = sessions.account(sender.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(sender);
            return;
        }
        plugin.parties().partyOf(account).thenAccept(optional -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sender.isOnline() || Bukkit.getPlayer(sender.getUniqueId()) != sender || !sessions.account(sender.getUniqueId()).filter(account::equals).isPresent()
                || !plugin.staff().canCommunicate(sender.getUniqueId())) return;
            var current = plugin.parties().partyOf(account).toCompletableFuture().getNow(java.util.Optional.empty());
            if (optional.isEmpty() || current.isEmpty() || !current.get().partyId().equals(optional.get().partyId()) || !current.get().members().contains(account)) {
                Chat.error(sender, "You are not in a party. Start one with " + Chat.command("party") + ".");
                return;
            }
            Component line = line(sender, ChatChannel.PARTY, message);
            for (AccountId member : current.get().members()) {
                UUID playerId = sessions.player(member).orElse(null);
                Player viewer = playerId == null ? null : Bukkit.getPlayer(playerId);
                if (viewer != null && !social.isIgnoring(viewer.getUniqueId(), sender.getUniqueId())) {
                    viewer.sendMessage(line);
                }
            }
        }));
    }

    private void sendHomestead(Player sender, HomesteadChatPolicy.Selection selection, Component message) {
        if (plugin.skyblockEnabled() || !validMessage(sender, message)) return;
        AccountId actor = sessions.account(sender.getUniqueId()).orElse(null);
        if (!sender.isOnline() || actor == null) return;
        if (!plugin.staff().canCommunicate(sender.getUniqueId())) {
            Chat.error(sender, "You cannot use chat while muted."); return;
        }
        HomesteadView home = selection == null ? null : snapshot(selection.owner());
        if (home == null || !selection.permits(actor, home)) {
            homesteadChannels.remove(sender.getUniqueId());
            Chat.notice(sender, "Message not sent. Choose a home first, or use " + Chat.command("channel global") + "."); Chat.usage(sender, "/claim chat <owner>");
            return;
        }
        var audience = HomesteadChatPolicy.audience(home);
        Component line = Component.text("[" + home.name() + "] ", NamedTextColor.DARK_AQUA)
            .append(presentation.chatPrefix(sender)).append(sender.displayName().colorIfAbsent(NamedTextColor.WHITE))
            .append(Component.text(" > ", NamedTextColor.DARK_GRAY)).append(message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            AccountId member = sessions.account(viewer.getUniqueId()).orElse(null);
            if (member != null && audience.contains(member) && !social.isIgnoring(viewer.getUniqueId(), sender.getUniqueId())) {
                viewer.sendMessage(line);
            }
        }
    }

    private boolean validMessage(Player sender, Component message) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
        if (plain.isBlank() || plain.length() > 512) { Chat.error(sender, "Keep messages between 1 and 512 characters."); return false; }
        return true;
    }
    private void sendIsland(Player sender, IslandChatPolicy.Selection selection, Component message) {
        if (plugin.skyblock() == null || !validMessage(sender, message)) return;
        AccountId actor = sessions.account(sender.getUniqueId()).orElse(null);
        var island = selection == null ? null : plugin.skyblock().islands().owned(selection.owner()).orElse(null);
        if (!sender.isOnline() || actor == null || selection == null || !selection.permits(actor, island)) {
            islandChannels.remove(sender.getUniqueId()); Chat.notice(sender, "Message not sent. Choose your co-op first."); Chat.usage(sender, "/island chat <owner>"); return;
        }
        if (!plugin.staff().canCommunicate(sender.getUniqueId())) { Chat.error(sender, "You cannot use chat while muted."); return; }
        var audience = IslandChatPolicy.audience(island);
        Component line = Component.text("[" + island.name() + "] ", NamedTextColor.DARK_AQUA)
            .append(presentation.chatPrefix(sender)).append(Component.text(sender.getName(), NamedTextColor.WHITE))
            .append(Component.text(" > ", NamedTextColor.DARK_GRAY)).append(message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            var account = sessions.account(viewer.getUniqueId()).orElse(null);
            if (account != null && audience.contains(account) && !social.isIgnoring(viewer.getUniqueId(), sender.getUniqueId())) viewer.sendMessage(line);
        }
    }
    private HomesteadView snapshot(AccountId owner) {
        var future = plugin.claims().homestead(owner).toCompletableFuture();
        if (!future.isDone()) throw new IllegalStateException("Homestead snapshot unavailable");
        return future.join();
    }

    private AccountId resolveOwner(String raw) {
        var known = plugin.playerData().knownAccounts();
        try { AccountId id = AccountId.parse(raw); if (known.contains(id)) return id; }
        catch (IllegalArgumentException ignored) { }
        var matches = known.stream().filter(id -> plugin.playerData().read(id,
            yaml -> yaml.getString("identity.last-known-name", "")).equalsIgnoreCase(raw)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private Component line(Player source, ChatChannel channel, Component message) {
        return Component.empty().append(channel.component()).append(Component.space())
            .append(presentation.chatPrefix(source))
            .append(source.displayName().colorIfAbsent(NamedTextColor.WHITE))
            .append(Component.text(" › ", NamedTextColor.DARK_GRAY)).append(message);
    }

    private ChatChannel selected(Player player) {
        ChatChannel channel = ChatChannel.parse(settings.channel(player.getUniqueId()));
        return channel == null ? ChatChannel.GLOBAL : channel;
    }

    private boolean allowed(Player player, ChatChannel channel) {
        return (channel != ChatChannel.STAFF || player.hasPermission("nerdsmp.staff"))
            && (channel != ChatChannel.ISLAND || plugin.skyblockEnabled()) && (channel != ChatChannel.HOMESTEAD || !plugin.skyblockEnabled());
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.playersOnly(sender);
        return null;
    }

    public enum ChatChannel {
        GLOBAL("global", "Global", "G", NamedTextColor.AQUA),
        LOCAL("local", "Local", "L", NamedTextColor.GREEN),
        TRADE("trade", "Trade", "T", NamedTextColor.GOLD),
        PARTY("party", "Party", "P", NamedTextColor.LIGHT_PURPLE),
        ISLAND("island", "Island", "I", NamedTextColor.DARK_AQUA),
        HOMESTEAD("homestead", "Homestead", "H", NamedTextColor.DARK_AQUA),
        STAFF("staff", "Staff", "S", NamedTextColor.RED);

        private final String id;
        private final String displayName;
        private final String shortName;
        private final NamedTextColor color;

        ChatChannel(String id, String displayName, String shortName, NamedTextColor color) {
            this.id = id;
            this.displayName = displayName;
            this.shortName = shortName;
            this.color = color;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String tag() {
            return "<dark_gray>[</dark_gray><" + color.toString().toLowerCase(Locale.ROOT) + ">" + shortName
                + "</" + color.toString().toLowerCase(Locale.ROOT) + "><dark_gray>]</dark_gray>";
        }

        private Component component() {
            return Component.text("[", NamedTextColor.DARK_GRAY).append(Component.text(shortName, color))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));
        }

        public static ChatChannel parse(String raw) {
            for (ChatChannel channel : values()) {
                if (channel.id.equalsIgnoreCase(raw)) {
                    return channel;
                }
            }
            return null;
        }
    }
}
