package net.nerdsmp.paper.party;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.PartyService.PartyResult;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.party.PartyRoster.EventKind;
import net.nerdsmp.paper.party.PartyRoster.PartyEvent;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.OrderedYamlWriter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The in-game side of parties: tells the whole party about every change, tracks who is online so an
 * absent leader hands over and long-offline members drop out, keeps parties across restarts, and
 * serves {@code /party} with a menu, clickable invites, open parties and a party finder.
 */
public final class PartyManager implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PartyRoster roster;
    private final File file;
    private final Duration handover;
    private final Duration removeAfter;
    private final BukkitTask sweep;
    private PartyMenu menu;
    private final OrderedYamlWriter writer;
    private boolean dirty;

    public PartyManager(NerdSmpPlugin plugin, PartyRoster roster) {
        this.plugin = plugin;
        this.roster = roster;
        this.file = new File(plugin.getDataFolder(), "parties.yml");
        this.writer = new OrderedYamlWriter(plugin, file, "parties");
        this.handover = Duration.ofSeconds(Math.max(10, plugin.getConfig().getLong("parties.leader-handover-seconds", 60)));
        this.removeAfter = Duration.ofMinutes(Math.max(1, plugin.getConfig().getLong("parties.offline-removal-minutes", 15)));
        if (file.isFile()) roster.load(YamlConfiguration.loadConfiguration(file), Instant.now());
        roster.onChange(this::announce);
        for (Player player : Bukkit.getOnlinePlayers()) plugin.sessions().account(player.getUniqueId()).ifPresent(roster::markOnline);
        sweep = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void attach(PartyMenu menu) { this.menu = menu; }

    public PartyRoster roster() { return roster; }

    private void tick() {
        roster.sweep(Instant.now(), handover, removeAfter);
        if (dirty) save();
    }

    // ---------------------------------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------------------------------

    private void announce(PartyEvent event) {
        dirty = true;
        String actor = event.actor() == null ? "" : Chat.escape(name(event.actor()));
        String target = event.target() == null ? "" : Chat.escape(name(event.target()));
        String size = " <dark_gray>(" + event.members().size() + "/" + roster.maximumSize() + ")</dark_gray>";
        switch (event.kind()) {
            case CREATED -> tell(event.members(), "<light_purple>Party created.</light_purple> <gray>Invite friends with "
                + Chat.syntax("/party invite <player>") + " or open it with " + Chat.command("party open") + ".</gray>");
            case INVITED -> {
                tellExcept(event.members(), List.of(event.target(), event.actor()), "<gray>" + actor + " invited " + target + " to the party.</gray>");
                inviteCard(event);
            }
            case JOINED -> {
                tellExcept(event.members(), event.target(), "<light_purple>" + target + " joined the party.</light_purple>" + size);
                player(event.target()).ifPresent(player -> {
                    Chat.success(player, "You joined the party." + size + " <gray>Chat with " + Chat.syntax("/pc <message>")
                        + ", see everyone with " + Chat.command("party") + ".</gray>");
                    chime(player, 1.4F);
                });
            }
            case LEFT -> {
                tell(event.members(), "<gray>" + target + " left the party.</gray>" + size);
                player(event.target()).ifPresent(player -> Chat.notice(player, "You left the party."));
            }
            case KICKED -> {
                tell(event.members(), "<gray>" + actor + " removed " + target + " from the party.</gray>" + size);
                player(event.target()).ifPresent(player -> Chat.notice(player, "You were removed from the party."));
            }
            case REMOVED_OFFLINE -> tell(event.members(), "<gray>" + target + " was offline too long and left the party.</gray>" + size);
            case LEADER_CHANGED -> {
                String why = event.automatic() ? " <dark_gray>(the previous leader is away)</dark_gray>" : "";
                tell(event.members(), "<gold>★ " + target + " now leads the party.</gold>" + why);
            }
            case DISBANDED -> tell(event.members(), "<yellow>The party was disbanded.</yellow>");
            case OPENED -> tell(event.members(), "<light_purple>The party is now open:</light_purple> <gray>anyone can join from "
                + Chat.command("party list") + ".</gray>");
            case CLOSED -> tell(event.members(), "<gray>The party is now invite-only.</gray>");
            case NOTE -> roster.details(event.partyId()).ifPresent(details -> tell(event.members(), details.note().isBlank()
                ? "<gray>Party note cleared.</gray>" : "<gray>Party note:</gray> <white>" + Chat.escape(details.note()) + "</white>"));
            case POLICY -> roster.details(event.partyId()).ifPresent(details -> tell(event.members(),
                details.invitePolicy() == PartyRoster.InvitePolicy.MEMBERS ? "<gray>Every member can now invite players.</gray>"
                    : "<gray>Only the leader can invite players now.</gray>"));
        }
    }

    private void inviteCard(PartyEvent event) {
        player(event.target()).ifPresent(player -> {
            AccountId leader = roster.details(event.partyId()).map(PartyRoster.Details::leader).orElse(event.actor());
            String from = Chat.escape(name(event.actor()));
            String host = Chat.escape(name(leader));
            Chat.send(player, "<light_purple>" + from + " invited you to " + (leader.equals(event.actor()) ? "their" : host + "'s")
                + " party</light_purple> <dark_gray>(" + event.members().size() + " in it)</dark_gray>  "
                + "<green><click:run_command:'/party accept " + Chat.escape(name(leader)) + "'><hover:show_text:'Join the party'>[Accept]</hover></click></green>  "
                + "<red><click:run_command:'/party deny " + Chat.escape(name(leader)) + "'><hover:show_text:'Decline'>[Decline]</hover></click></red>");
            chime(player, 1.2F);
        });
    }

    private void tell(List<AccountId> members, String markup) {
        for (AccountId member : members) player(member).ifPresent(player -> Chat.send(player, markup));
    }

    private void tellExcept(List<AccountId> members, AccountId skip, String markup) {
        tellExcept(members, List.of(skip), markup);
    }

    private void tellExcept(List<AccountId> members, List<AccountId> skip, String markup) {
        for (AccountId member : members) if (!skip.contains(member)) player(member).ifPresent(player -> Chat.send(player, markup));
    }

    private static void chime(Player player, float pitch) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .8F, pitch);
    }

    // ---------------------------------------------------------------------------------------------
    // Presence
    // ---------------------------------------------------------------------------------------------

    /** Called once the player's account has loaded (see SessionListener). */
    public void ready(Player player) {
        plugin.sessions().account(player.getUniqueId()).ifPresent(account -> {
            boolean wasAway = roster.details(account).isPresent() && !roster.online(account);
            roster.markOnline(account);
            roster.details(account).ifPresent(details -> {
                if (wasAway) tellExcept(details.members(), account, "<gray>" + Chat.escape(player.getName()) + " is back online.</gray>");
                Chat.info(player, "You're in " + (details.leader().equals(account) ? "your" : Chat.escape(name(details.leader())) + "'s")
                    + " party <dark_gray>(" + details.members().size() + "/" + roster.maximumSize() + ")</dark_gray>. " + Chat.command("party"));
            });
            int invites = roster.invitesFor(account, Instant.now()).size();
            if (invites > 0) Chat.info(player, "You have " + invites + " party invite" + (invites == 1 ? "" : "s") + ": " + Chat.command("party"));
        });
    }

    /** Runs before the session closes, while the account is still known. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().account(event.getPlayer().getUniqueId()).ifPresent(account -> roster.details(account).ifPresent(details -> {
            roster.markOffline(account, Instant.now());
            tellExcept(details.members(), account, "<gray>" + Chat.escape(event.getPlayer().getName()) + " went offline"
                + (details.leader().equals(account) && details.members().size() > 1 ? "; leadership passes on if they're not back in "
                + Chat.duration(handover) : "") + ".</gray>");
        }));
    }

    // ---------------------------------------------------------------------------------------------
    // Command
    // ---------------------------------------------------------------------------------------------

    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Chat.playersOnly(sender);
            return true;
        }
        Optional<AccountId> found = plugin.sessions().account(player.getUniqueId());
        if (found.isEmpty()) {
            Chat.accountLoading(player);
            return true;
        }
        AccountId account = found.get();
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String argument = args.length > 1 ? args[1] : null;
        try {
            switch (action) {
                case "" -> menu.open(player);
                case "info" -> info(player, account);
                case "create" -> result(player, roster.create(account, Instant.now()));
                case "invite" -> {
                    if (argument == null) { menu.openInvite(player); return true; }
                    invite(player, account, argument);
                }
                case "accept" -> accept(player, account, argument);
                case "deny", "decline" -> {
                    Optional<UUID> party = argument == null ? Optional.empty() : partyOf(argument);
                    result(player, party.isPresent() ? roster.decline(account, party.get()) : roster.decline(account).toCompletableFuture().join());
                }
                case "leave" -> result(player, roster.leave(account).toCompletableFuture().join());
                case "disband" -> result(player, roster.disband(account).toCompletableFuture().join());
                case "kick", "promote" -> {
                    if (argument == null) { Chat.usage(player, "/party " + action + " <player>"); return true; }
                    Optional<AccountId> target = plugin.social().knownAccount(argument);
                    if (target.isEmpty()) { Chat.error(player, "Choose a party member by name."); return true; }
                    result(player, (action.equals("kick") ? roster.kick(account, target.get()) : roster.promote(account, target.get())).toCompletableFuture().join());
                }
                case "open", "close" -> result(player, roster.setOpen(account, action.equals("open")));
                case "note" -> {
                    String note = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
                    result(player, roster.setNote(account, note.equalsIgnoreCase("clear") ? "" : note));
                }
                case "invites" -> {
                    if (argument == null || !List.of("leader", "members").contains(argument.toLowerCase(Locale.ROOT))) {
                        Chat.usage(player, "/party invites <leader|members>");
                        return true;
                    }
                    result(player, roster.setInvitePolicy(account, PartyRoster.InvitePolicy.valueOf(argument.toUpperCase(Locale.ROOT))));
                }
                case "join" -> {
                    if (argument == null) { menu.openFinder(player); return true; }
                    Optional<UUID> party = partyOf(argument);
                    if (party.isEmpty()) { Chat.error(player, Chat.escape(argument) + " isn't in a party."); return true; }
                    result(player, roster.join(account, party.get()));
                }
                case "list", "find" -> menu.openFinder(player);
                case "chat" -> plugin.chat().handleDirect(player, net.nerdsmp.paper.chat.ChannelChatManager.ChatChannel.PARTY,
                    Arrays.copyOfRange(args, 1, args.length));
                default -> usage(player);
            }
        } catch (IllegalArgumentException invalid) {
            Chat.error(player, Chat.escape(invalid.getMessage()));
        }
        return true;
    }

    void invite(Player player, AccountId account, String name) {
        Player target = Bukkit.getPlayerExact(name);
        Optional<AccountId> targetAccount = target == null ? Optional.empty() : plugin.sessions().account(target.getUniqueId());
        if (target == null || targetAccount.isEmpty() || !player.canSee(target)) {
            Chat.error(player, "That player must be online.");
            return;
        }
        if (!plugin.social().canContact(player, target, PlayerSettingsService.Setting.PARTY_REQUESTS)) {
            Chat.error(player, "That player is not accepting party invitations from you.");
            return;
        }
        PartyResult outcome = roster.invite(account, targetAccount.get(), Instant.now()).toCompletableFuture().join();
        if (outcome != PartyResult.INVITED) result(player, outcome);
        else Chat.success(player, "Invited " + Chat.escape(target.getName()) + ". The invite lasts "
            + Chat.duration(Duration.ofSeconds(plugin.getConfig().getLong("parties.invite-lifetime-seconds", 60))) + ".");
    }

    void accept(Player player, AccountId account, String leaderName) {
        if (!plugin.settings().enabled(player.getUniqueId(), PlayerSettingsService.Setting.PARTY_REQUESTS)) {
            Chat.error(player, "Party invitations are switched off in your " + Chat.command("settings") + ".");
            return;
        }
        Optional<UUID> party = leaderName == null ? Optional.empty() : partyOf(leaderName);
        if (leaderName != null && party.isEmpty()) { Chat.error(player, "No invitation from " + Chat.escape(leaderName) + "."); return; }
        var invite = party.isPresent() ? roster.invitesFor(account, Instant.now()).stream().filter(pending -> pending.partyId().equals(party.get())).findFirst()
            : roster.invitesFor(account, Instant.now()).stream().findFirst();
        if (invite.isPresent() && plugin.social().blocked(account, invite.get().leader())) {
            Chat.error(player, "That party invitation is no longer available.");
            return;
        }
        PartyResult outcome = roster.accept(account, invite.map(PartyRoster.PendingInvite::partyId).orElse(null), Instant.now()).toCompletableFuture().join();
        if (outcome != PartyResult.JOINED) result(player, outcome);
    }

    void join(Player player, AccountId account, UUID party) {
        PartyResult outcome = roster.join(account, party);
        if (outcome != PartyResult.JOINED) result(player, outcome);
    }

    private void info(Player player, AccountId account) {
        Optional<PartyRoster.Details> details = roster.details(account);
        if (details.isEmpty()) {
            Chat.info(player, "You aren't in a party. " + Chat.syntax("/party invite <player>") + ", " + Chat.command("party create")
                + " or find one with " + Chat.command("party list") + ".");
            return;
        }
        PartyRoster.Details party = details.get();
        Chat.send(player, "<light_purple><bold>Your party</bold></light_purple> <gray>(" + party.members().size() + "/" + roster.maximumSize()
            + ", " + (party.open() ? "open" : "invite-only") + ")</gray>" + (party.note().isBlank() ? "" : " <white>" + Chat.escape(party.note()) + "</white>"));
        for (AccountId member : party.members()) {
            Player online = player(member).filter(player::canSee).orElse(null);
            Chat.send(player, (member.equals(party.leader()) ? "<gold>★ " : "<gray>• ") + Chat.escape(name(member))
                + (online == null ? " <dark_gray>(offline)</dark_gray>" : " <dark_gray>" + Math.round(online.getHealth()) + "♥</dark_gray>"));
        }
    }

    void result(Player player, PartyResult result) {
        String message = switch (result) {
            case INVITED -> "<green>Party invite sent.</green>";
            case INVITE_PENDING -> "<yellow>That player already has an invitation from your party.</yellow>";
            case DECLINED -> "<yellow>Party invitation declined.</yellow>";
            case JOINED, LEFT, KICKED, PROMOTED, DISBANDED, OPENED, CLOSED, UPDATED -> null; // The party announcement covers it.
            case PARTY_FULL -> "<red>That party is full.</red>";
            case NO_INVITE, INVITE_EXPIRED -> "<red>You don't have an active invite from that party.</red>";
            case NOT_LEADER -> "<red>Only the party leader can do that.</red>";
            case LEADER_MUST_PROMOTE_OR_DISBAND -> "<red>Promote another member or disband the party first.</red>";
            case TARGET_ALREADY_IN_PARTY -> "<red>That player is already in a party.</red>";
            case ALREADY_IN_PARTY -> "<red>You're already in a party. Leave it first with " + Chat.command("party leave") + ".</red>";
            case TARGET_NOT_MEMBER -> "<red>That player isn't in your party.</red>";
            case CANNOT_TARGET_SELF -> "<red>You can't target yourself.</red>";
            case NOT_IN_PARTY -> "<red>You aren't in a party.</red>";
            case NOT_OPEN -> "<red>That party is invite-only.</red>";
            case TOO_MANY_INVITES -> "<red>That player already has several invitations waiting. Try again in a moment.</red>";
            case NOT_ALLOWED -> "<red>Your party's settings don't allow that.</red>";
        };
        if (message != null) Chat.send(player, message);
    }

    private void usage(Player player) {
        Chat.usage(player, "/party [invite <player>|accept [leader]|deny [leader]|leave|list|join <player>|chat <message>]");
        Chat.usage(player, "/party [kick|promote <player>|open|close|note <text>|invites <leader|members>|disband]");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("invite", "accept", "deny", "leave", "list", "join", "chat", "info", "create",
            "kick", "promote", "open", "close", "note", "invites", "disband");
        if (args.length != 2 || !(sender instanceof Player player)) return List.of();
        String action = args[0].toLowerCase(Locale.ROOT);
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        return switch (action) {
            case "invite" -> Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(player) && player.canSee(other)).map(Player::getName).toList();
            case "kick", "promote" -> account.flatMap(roster::details).map(details -> details.members().stream()
                .filter(member -> !member.equals(account.get())).map(this::name).toList()).orElse(List.of());
            case "accept", "deny", "decline" -> account.map(value -> roster.invitesFor(value, Instant.now()).stream()
                .map(invite -> name(invite.leader())).toList()).orElse(List.of());
            case "join" -> roster.openParties().stream().map(details -> name(details.leader())).toList();
            case "invites" -> List.of("leader", "members");
            default -> List.of();
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    Optional<Player> player(AccountId account) {
        return plugin.sessions().player(account).map(Bukkit::getPlayer).filter(Player::isOnline);
    }

    String name(AccountId account) {
        return player(account).map(Player::getName).orElseGet(() -> plugin.social().accountName(account));
    }

    /** The party a named player belongs to (invite cards name the leader, the finder any member). */
    private Optional<UUID> partyOf(String name) {
        return plugin.social().knownAccount(name).flatMap(roster::details).map(PartyRoster.Details::id);
    }

    private void save() {
        dirty = false;
        writer.submit(roster.save().saveToString());
    }

    @Override
    public void close() {
        sweep.cancel();
        writer.flush(roster.save().saveToString());
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
