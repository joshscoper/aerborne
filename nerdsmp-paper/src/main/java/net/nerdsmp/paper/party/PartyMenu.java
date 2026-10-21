package net.nerdsmp.paper.party;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** {@code /party}: your party at a glance with every action a click away, plus invites and the party finder. */
public final class PartyMenu implements Listener {
    private static final int[] MEMBER_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31};
    private static final int[] LIST_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final NerdSmpPlugin plugin;
    private final PartyManager manager;

    public PartyMenu(NerdSmpPlugin plugin, PartyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player) {
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) { Chat.accountLoading(player); return; }
        Optional<PartyRoster.Details> details = manager.roster().details(account.get());
        if (details.isEmpty()) openLobby(player, account.get());
        else openParty(player, account.get(), details.get());
    }

    private void openParty(Player player, AccountId me, PartyRoster.Details party) {
        boolean leader = party.leader().equals(me);
        Board board = board(player, 54, "<light_purple><bold>Your party</bold></light_purple>");
        List<String> header = new ArrayList<>();
        header.add("<gray>Leader</gray> <gold>" + Chat.escape(manager.name(party.leader())) + "</gold>");
        header.add("<gray>Members</gray> <white>" + party.members().size() + "/" + manager.roster().maximumSize() + "</white>");
        header.add(party.open() ? "<green>Open:</green> <gray>anyone can join from the finder</gray>" : "<gray>Invite-only</gray>");
        if (!party.note().isBlank()) header.add("<gray>Note</gray> <white>" + Chat.escape(party.note()) + "</white>");
        board.set(4, MenuKit.item(Material.CAKE, "<light_purple>" + Chat.escape(manager.name(party.leader())) + "'s party</light_purple>", header), (who, click) -> { });
        int index = 0;
        for (AccountId member : party.members()) {
            if (index >= MEMBER_SLOTS.length) break;
            Optional<Player> online = manager.player(member).filter(player::canSee);
            List<String> lore = new ArrayList<>();
            if (member.equals(party.leader())) lore.add("<gold>★ Party leader</gold>");
            if (online.isPresent()) {
                Player other = online.get();
                var max = other.getAttribute(Attribute.MAX_HEALTH);
                lore.add("<red>" + "❤".repeat((int) Math.ceil(other.getHealth() / 4)) + "</red> <gray>"
                    + Math.round(other.getHealth()) + "/" + Math.round(max == null ? 20 : max.getValue()) + "</gray>");
                lore.add("<gray>" + Chat.escape(worldName(other)) + "</gray>");
            } else lore.add("<dark_gray>Offline</dark_gray>");
            if (leader && !member.equals(me)) {
                lore.add("");
                lore.add("<yellow>Left-click:</yellow> <gray>make leader</gray>");
                lore.add("<yellow>Right-click:</yellow> <gray>remove from party</gray>");
            }
            board.set(MEMBER_SLOTS[index++], head(member, (online.isPresent() ? "<white>" : "<dark_gray>") + Chat.escape(manager.name(member)), lore), (who, click) -> {
                if (!leader || member.equals(me)) return;
                String verb = click.isRightClick() ? "kick" : "promote";
                List<String> detail = List.of(click.isRightClick()
                    ? "<gray>They'll be removed from the party.</gray>" : "<gray>You'll hand over leadership.</gray>");
                MenuKit.openConfirm(plugin, who, click.isRightClick() ? "Remove from party?" : "Make leader?",
                    MenuKit.item(Material.PLAYER_HEAD, "<white>" + Chat.escape(manager.name(member)) + "</white>", detail), detail,
                    confirmed -> { confirmed.closeInventory(); manager.command(confirmed, new String[]{verb, manager.name(member)}); },
                    this::open);
            });
        }
        boolean canInvite = leader || party.invitePolicy() == PartyRoster.InvitePolicy.MEMBERS;
        if (party.members().size() < manager.roster().maximumSize() && canInvite)
            board.set(38, MenuKit.item(Material.WRITABLE_BOOK, "<green>Invite players</green>", MenuKit.withHint(List.of(
                "<gray>Pick from the players online.</gray>"), "invite")), (who, click) -> openInvite(who));
        board.set(39, MenuKit.item(Material.PAPER, "<aqua>Party chat</aqua>", MenuKit.withHint(List.of(
            "<gray>Talk only to your party with</gray>", "<white>/pc <message></white><gray>, or switch</gray>", "<gray>your chat to the party.</gray>"), "switch to party chat")),
            (who, click) -> { who.closeInventory(); who.performCommand("channel party"); });
        board.set(40, MenuKit.item(Material.ENDER_EYE, "<gold>Expeditions</gold>", MenuKit.withHint(List.of(
            "<gray>Take the party on a dungeon run.</gray>", leader ? "<gray>You start runs for the party.</gray>" : "<gray>Your leader starts runs.</gray>"),
            "browse expeditions")), (who, click) -> who.performCommand("expedition"));
        if (leader) {
            board.set(41, MenuKit.item(party.open() ? Material.LIME_DYE : Material.GRAY_DYE,
                party.open() ? "<green>Open party</green>" : "<gray>Invite-only</gray>", MenuKit.withHint(List.of(
                    party.open() ? "<gray>Anyone can join from the finder.</gray>" : "<gray>Only invited players can join.</gray>"),
                    party.open() ? "make it invite-only" : "open it to the finder")),
                (who, click) -> { manager.command(who, new String[]{party.open() ? "close" : "open"}); open(who); });
            board.set(42, MenuKit.item(party.invitePolicy() == PartyRoster.InvitePolicy.MEMBERS ? Material.FILLED_MAP : Material.MAP,
                "<aqua>Who can invite</aqua>", MenuKit.withHint(List.of(party.invitePolicy() == PartyRoster.InvitePolicy.MEMBERS
                    ? "<gray>Every member can invite.</gray>" : "<gray>Only you can invite.</gray>"), "switch")),
                (who, click) -> {
                    manager.command(who, new String[]{"invites", party.invitePolicy() == PartyRoster.InvitePolicy.MEMBERS ? "leader" : "members"});
                    open(who);
                });
        }
        board.set(MenuKit.backSlot(54), MenuKit.back("Community"), (who, click) -> plugin.menus().open(who, MenuManager.View.COMMUNITY));
        board.set(MenuKit.closeSlot(54), MenuKit.close(), (who, click) -> who.closeInventory());
        board.set(53, MenuKit.item(Material.BARRIER, leader ? "<red>Disband party</red>" : "<red>Leave party</red>", List.of(MenuKit.hint(leader ? "disband" : "leave"))),
            (who, click) -> MenuKit.openConfirm(plugin, who, leader ? "Disband the party?" : "Leave the party?",
                MenuKit.item(Material.BARRIER, leader ? "<red>Disband</red>" : "<red>Leave</red>", List.of()),
                List.of(leader ? "<gray>Everyone leaves the party.</gray>" : "<gray>You can rejoin if invited.</gray>"),
                confirmed -> { confirmed.closeInventory(); manager.command(confirmed, new String[]{leader ? "disband" : "leave"}); }, this::open));
        player.openInventory(board.inventory);
    }

    private void openLobby(Player player, AccountId me) {
        Board board = board(player, 45, "<light_purple><bold>Parties</bold></light_purple>");
        board.set(4, MenuKit.item(Material.CAKE, "<light_purple>Team up</light_purple>", List.of(
            "<gray>A party of up to " + manager.roster().maximumSize() + " shares chat,</gray>", "<gray>expeditions and boss rewards.</gray>",
            "<gray>It doesn't share your island.</gray>")), (who, click) -> { });
        board.set(20, MenuKit.item(Material.WRITABLE_BOOK, "<green>Invite players</green>", MenuKit.withHint(List.of(
            "<gray>Starts a party with you as leader.</gray>"), "invite")), (who, click) -> openInvite(who));
        board.set(22, MenuKit.item(Material.SPYGLASS, "<aqua>Find a party</aqua>", MenuKit.withHint(List.of(
            "<gray>" + manager.roster().openParties().size() + " open parties right now.</gray>"), "browse")), (who, click) -> openFinder(who));
        board.set(24, MenuKit.item(Material.LIME_DYE, "<green>Start an open party</green>", MenuKit.withHint(List.of(
            "<gray>Anyone can join from the finder.</gray>"), "create")), (who, click) -> {
                manager.command(who, new String[]{"create"});
                manager.command(who, new String[]{"open"});
                open(who);
            });
        List<PartyRoster.PendingInvite> invites = manager.roster().invitesFor(me, Instant.now());
        int slot = 29;
        for (PartyRoster.PendingInvite invite : invites) {
            if (slot > 33) break;
            long seconds = Math.max(0, Duration.between(Instant.now(), invite.expiresAt()).toSeconds());
            int members = manager.roster().details(invite.partyId()).map(details -> details.members().size()).orElse(0);
            board.set(slot++, head(invite.leader(), "<light_purple>Invite: " + Chat.escape(manager.name(invite.leader())) + "'s party</light_purple>", List.of(
                "<gray>" + members + " in the party · " + seconds + "s left</gray>", "",
                "<yellow>Left-click:</yellow> <gray>accept</gray>", "<yellow>Right-click:</yellow> <gray>decline</gray>")), (who, click) -> {
                    who.closeInventory();
                    manager.command(who, new String[]{click.isRightClick() ? "deny" : "accept", manager.name(invite.leader())});
                });
        }
        board.set(MenuKit.backSlot(45), MenuKit.back("Community"), (who, click) -> plugin.menus().open(who, MenuManager.View.COMMUNITY));
        board.set(MenuKit.closeSlot(45), MenuKit.close(), (who, click) -> who.closeInventory());
        player.openInventory(board.inventory);
    }

    public void openInvite(Player player) {
        Optional<AccountId> me = plugin.sessions().account(player.getUniqueId());
        if (me.isEmpty()) return;
        Board board = board(player, 54, "<green><bold>Invite to party</bold></green>");
        List<Player> candidates = Bukkit.getOnlinePlayers().stream().map(Player.class::cast)
            .filter(other -> !other.equals(player) && player.canSee(other))
            .filter(other -> plugin.sessions().account(other.getUniqueId()).map(account -> manager.roster().details(account).isEmpty()).orElse(false))
            .filter(other -> plugin.social().canContact(player, other, PlayerSettingsService.Setting.PARTY_REQUESTS))
            .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).limit(LIST_SLOTS.length).toList();
        for (int index = 0; index < candidates.size(); index++) {
            Player other = candidates.get(index);
            AccountId account = plugin.sessions().account(other.getUniqueId()).orElseThrow();
            board.set(LIST_SLOTS[index], head(account, "<white>" + Chat.escape(other.getName()) + "</white>", List.of(MenuKit.hint("invite"))),
                (who, click) -> { manager.command(who, new String[]{"invite", other.getName()}); openInvite(who); });
        }
        if (candidates.isEmpty()) board.set(22, MenuKit.item(Material.GRAY_DYE, "<gray>Nobody to invite</gray>", List.of(
            "<gray>Everyone online is in a party or</gray>", "<gray>isn't taking invitations.</gray>")), (who, click) -> { });
        board.set(MenuKit.backSlot(54), MenuKit.back("Party"), (who, click) -> open(who));
        board.set(MenuKit.closeSlot(54), MenuKit.close(), (who, click) -> who.closeInventory());
        player.openInventory(board.inventory);
    }

    public void openFinder(Player player) {
        Board board = board(player, 54, "<aqua><bold>Party finder</bold></aqua>");
        List<PartyRoster.Details> open = manager.roster().openParties();
        for (int index = 0; index < open.size() && index < LIST_SLOTS.length; index++) {
            PartyRoster.Details party = open.get(index);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + party.members().size() + "/" + manager.roster().maximumSize() + " members</gray>");
            if (!party.note().isBlank()) lore.add("<white>" + Chat.escape(party.note()) + "</white>");
            lore.add("");
            lore.add(MenuKit.hint("join"));
            board.set(LIST_SLOTS[index], head(party.leader(), "<light_purple>" + Chat.escape(manager.name(party.leader())) + "'s party</light_purple>", lore),
                (who, click) -> {
                    who.closeInventory();
                    plugin.sessions().account(who.getUniqueId()).ifPresent(account -> manager.join(who, account, party.id()));
                });
        }
        if (open.isEmpty()) board.set(22, MenuKit.item(Material.GRAY_DYE, "<gray>No open parties</gray>", List.of(
            "<gray>Start one and others can join:</gray>", "<white>/party create</white><gray>, then</gray> <white>/party open</white>")), (who, click) -> { });
        board.set(MenuKit.backSlot(54), MenuKit.back("Party"), (who, click) -> open(who));
        board.set(MenuKit.closeSlot(54), MenuKit.close(), (who, click) -> who.closeInventory());
        player.openInventory(board.inventory);
    }

    private ItemStack head(AccountId account, String name, List<String> lore) {
        ItemStack item = MenuKit.item(Material.PLAYER_HEAD, name, lore);
        plugin.sessions().player(account).ifPresent(uuid -> {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            item.setItemMeta(meta);
        });
        return item;
    }

    private static String worldName(Player player) {
        String world = player.getWorld().getName();
        if (world.startsWith("nerdsmp_exp_run_")) return "On an expedition";
        if (world.startsWith("nerdsmp_skyblock")) return "On an island";
        return switch (player.getWorld().getEnvironment()) {
            case NETHER -> "In the Nether";
            case THE_END -> "In the End";
            default -> "In the overworld";
        };
    }

    private Board board(Player player, int size, String title) {
        Board board = new Board(player.getUniqueId());
        board.inventory = Bukkit.createInventory(board, size, Chat.render(title));
        MenuKit.frame(board.inventory);
        return board;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Board board)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !board.owner.equals(player.getUniqueId())) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        BiConsumer<Player, ClickType> action = board.actions.get(event.getRawSlot());
        if (action == null) return;
        ClickType click = event.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != board.inventory) return;
            MenuKit.clickSound(player);
            action.accept(player, click);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Board) event.setCancelled(true);
    }

    private static final class Board implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        private Inventory inventory;

        private Board(UUID owner) { this.owner = owner; }

        void set(int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
