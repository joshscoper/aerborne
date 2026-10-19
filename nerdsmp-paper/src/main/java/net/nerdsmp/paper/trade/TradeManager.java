package net.nerdsmp.paper.trade;

import net.kyori.adventure.text.Component;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.market.MarketItems;
import net.nerdsmp.paper.market.MarketPlayerSave;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.social.SocialManager;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.time.*;
import java.util.*;

/** Offers are previews, never escrow. Both acceptances commit one recoverable exchange decision. */
public final class TradeManager implements Listener, AutoCloseable {
    private static final int[] LEFT = {10,11,12,19,20,21,28,29,30}, RIGHT = {14,15,16,23,24,25,32,33,34};
    private final NerdSmpPlugin plugin;
    private final PlayerSettingsService settings;
    private final SocialManager social;
    private final TradeInvitationBook requests = new TradeInvitationBook();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final TradeJournal journal;
    private final MarketPlayerSave saves;
    private final Duration requestLifetime;
    private final BukkitTask guard;
    public TradeManager(NerdSmpPlugin plugin, PlayerSettingsService settings, SocialManager social) {
        this.plugin = plugin; this.settings = settings; this.social = social;
        journal = new TradeJournal(new File(plugin.getDataFolder(), "player-trades"));
        saves = new MarketPlayerSave(plugin);
        requestLifetime = Duration.ofSeconds(Math.clamp(plugin.getConfig().getLong("trading.request-seconds", 60), 10, 120));
        guard = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Session session : new HashSet<>(sessions.values())) {
                if (!valid(session) || Instant.now().isAfter(session.expires)) cancel(session, "Trade closed. Your items never left your inventory.");
            }
        }, 20, 20);
    }
    public int pendingRecoveries() { return journal.pending().size(); }
    public boolean hasPending(AccountId account) { return journal.blocked(account); }
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("review")) {
            if (!sender.hasPermission("nerdsmp.admin")) { Chat.noPermission(sender); return true; }
            Chat.info(sender, "Unsettled player trades <white>" + journal.pending().size() + "</white>");
            journal.pending().forEach(t -> Chat.info(sender, "<white>" + t.id() + "</white> | " + t.left().player() + " saved=" + t.left().saved()
                + " | " + t.right().player() + " saved=" + t.right().saved()));
            return true;
        }
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        try {
            if (account(player) == null) { Chat.accountLoading(player); return true; }
            if (args.length == 0 || args[0].equalsIgnoreCase("view")) {
                Session session = sessions.get(player.getUniqueId());
                if (session == null) { Chat.info(player, "Trade from anywhere, then click inventory items to offer them."); Chat.usage(player, "/trade <player>"); }
                else player.openInventory(session.inventory);
                return true;
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            Session session = sessions.get(player.getUniqueId());
            switch (action) {
                case "accept" -> accept(player, args);
                case "deny" -> { requests.removeTarget(player.getUniqueId()); Chat.success(player, "Trade request denied."); }
                case "cancel" -> { if (session != null) cancel(session, "Trade cancelled. No items were moved."); else { requests.removeParticipant(player.getUniqueId()); Chat.success(player, "Trade requests cancelled."); } }
                case "offer" -> {
                    if (session == null) throw new IllegalArgumentException("Start a trade first.");
                    if (args.length > 2) throw new Usage("/trade offer [amount]");
                    ItemStack held = player.getInventory().getItemInMainHand();
                    add(player, session, player.getInventory().getHeldItemSlot(), args.length == 2 ? Integer.parseInt(args[1]) : held.getAmount());
                }
                case "remove" -> {
                    if (session == null) throw new IllegalArgumentException("Start a trade first.");
                    if (args.length != 2) throw new Usage("/trade remove <offer-slot>");
                    int index = Integer.parseInt(args[1]) - 1; var offered = offers(session, player);
                    if (index < 0 || index >= offered.size()) throw new IllegalArgumentException("Choose one of your offer slots.");
                    offered.remove(index); changed(session);
                }
                case "confirm" -> {
                    if (session == null || player.getOpenInventory().getTopInventory() != session.inventory) throw new IllegalArgumentException("Open /trade view and inspect both offers first.");
                    confirm(player, session);
                }
                default -> { if (args.length != 1) throw new Usage("/trade <player>"); request(player, args[0]); }
            }
        } catch (IllegalArgumentException error) {
            var active = sessions.get(player.getUniqueId()); if (active != null) { active.accepted.clear(); redraw(active); }
            refuse(player, error);
        }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Player trade failed for " + player.getUniqueId(), error);
            Chat.error(player, "Trading is unavailable right now. Please contact staff.");
        }
        return true;
    }
    private void request(Player player, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (!available(player, target) || sessions.containsKey(player.getUniqueId()) || sessions.containsKey(target.getUniqueId()))
            throw new IllegalArgumentException("That player is not available. Both players must be in Survival with empty cursors and trade requests enabled.");
        var reverse = requests.active(player.getUniqueId(), Instant.now());
        if (reverse != null && reverse.source().equals(target.getUniqueId())) { open(target, player); return; }
        var previous = requests.active(target.getUniqueId(), Instant.now());
        if (previous != null) throw new IllegalArgumentException("That player already has a trade request. Wait for it to expire.");
        requests.send(player.getUniqueId(), target.getUniqueId(), Instant.now().plus(requestLifetime));
        Chat.success(player, "Trade request sent to <white>" + Chat.escape(target.getName()) + "</white>.");
        Chat.info(target, "<white>" + Chat.escape(player.getName()) + "</white> wants to trade. " + Chat.command("trade accept") + " or " + Chat.command("trade deny") + ".");
    }
    private void accept(Player player, String[] args) {
        var request = requests.active(player.getUniqueId(), Instant.now());
        Player source = request == null ? null : Bukkit.getPlayer(request.source());
        if (source == null || args.length > 2 || args.length == 2 && !source.getName().equalsIgnoreCase(args[1])
            || !available(source, player) || sessions.containsKey(source.getUniqueId()) || sessions.containsKey(player.getUniqueId()))
            throw new IllegalArgumentException("No matching trade request is available.");
        open(source, player);
    }
    private boolean available(Player left, Player right) {
        if (left == null || right == null) return false;
        return RemoteTradePolicy.available(participant(left), participant(right),
            () -> left.canSee(right) && right.canSee(left),
            () -> !social.isIgnoring(left.getUniqueId(), right.getUniqueId())
                && !social.isIgnoring(right.getUniqueId(), left.getUniqueId()));
    }
    private RemoteTradePolicy.Participant participant(Player player) {
        AccountId account = account(player);
        return new RemoteTradePolicy.Participant(player.getUniqueId(), account,
            player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player, !player.isDead(),
            player.getGameMode() == GameMode.SURVIVAL, player.getItemOnCursor().getType().isAir(),
            settings.enabled(player.getUniqueId(), PlayerSettingsService.Setting.TRADE_REQUESTS),
            account != null && journal.blocked(account));
    }
    private void open(Player left, Player right) {
        Session session = new Session(left, right, account(left), account(right));
        session.inventory = Bukkit.createInventory(session, 54, Chat.render("<light_purple><bold>Player Trade</bold></light_purple>"));
        requests.removeParticipant(left.getUniqueId()); requests.removeParticipant(right.getUniqueId());
        sessions.put(left.getUniqueId(), session); sessions.put(right.getUniqueId(), session);
        redraw(session); left.openInventory(session.inventory); right.openInventory(session.inventory);
    }
    private void add(Player player, Session session, int slot, int amount) {
        if (!valid(session)) { cancel(session, "Trade closed. Both players must remain online and available."); return; }
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("Offer items from your main inventory.");
        var list = offers(session, player); ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir() || amount < 1 || amount > item.getAmount()) throw new IllegalArgumentException("Choose an amount from the stack you hold.");
        if (list.size() >= 9 || list.stream().anyMatch(o -> o.slot() == slot)) throw new IllegalArgumentException("That slot is already offered, or your nine offer slots are full.");
        if (plugin.items().describe(item).isPresent() && !plugin.items().isAuthentic(item)) throw new IllegalArgumentException("That item could not be verified.");
        list.add(new TradePlan.Offer<>(slot, stack(item), amount)); changed(session);
    }
    private void changed(Session session) { session.revision++; session.accepted.clear(); session.reviewAfter = System.currentTimeMillis() + 2000; redraw(session); }
    private void confirm(Player player, Session session) {
        if (!valid(session)) { cancel(session, "Trade closed. Both players must remain online and available."); return; }
        if (System.currentTimeMillis() < session.reviewAfter) throw new IllegalArgumentException("The offer changed. Take a moment to check it.");
        if (session.leftOffer.isEmpty() && session.rightOffer.isEmpty()) throw new IllegalArgumentException("Offer an item first.");
        // Recheck all originals for every confirmation, not only the second acceptance.
        plan(session.left, session.leftOffer, session.rightOffer);
        plan(session.right, session.rightOffer, session.leftOffer);
        session.accepted.add(player.getUniqueId()); redraw(session);
        if (session.accepted.size() == 2) commit(session);
    }
    private ItemStack[] plan(Player player, List<TradePlan.Offer<ItemStack>> outgoing, List<TradePlan.Offer<ItemStack>> incoming) {
        var slots = Arrays.stream(player.getInventory().getStorageContents()).map(this::stack).toList();
        var received = incoming.stream().map(o -> new TradeInventoryCapacity.Stack<>(o.snapshot().item(), o.amount(), o.snapshot().maximum())).toList();
        return TradePlan.exchange(slots, outgoing, received, ItemStack::isSimilar).stream().map(s -> {
            if (s == null) return null; ItemStack item = s.item().clone(); item.setAmount(s.amount()); return item;
        }).toArray(ItemStack[]::new);
    }
    private void commit(Session session) {
        ItemStack[] left = plan(session.left, session.leftOffer, session.rightOffer), right = plan(session.right, session.rightOffer, session.leftOffer);
        // Save originals first. Until the journal is durable, cancelling or crashing moves no items.
        saves.saveAndVerify(session.left); saves.saveAndVerify(session.right);
        var entry = journal.begin(new TradeJournal.Side(session.left.getUniqueId(), session.leftAccount, encode(session.left.getInventory().getStorageContents()), encode(left), false),
            new TradeJournal.Side(session.right.getUniqueId(), session.rightAccount, encode(session.right.getInventory().getStorageContents()), encode(right), false));
        detach(session); session.left.closeInventory(); session.right.closeInventory();
        boolean first = recover(session.left), second = recover(session.right);
        if (first && second) { Chat.success(session.left, "Trade complete."); Chat.success(session.right, "Trade complete."); }
        plugin.getLogger().info("Player trade decision " + entry.id() + " | " + session.leftAccount + " <-> " + session.rightAccount);
    }
    /** Must run before starter delivery or any other login inventory grant. */
    public boolean recover(Player player) {
        var optional = journal.forPlayer(player.getUniqueId()); if (optional.isEmpty()) return true;
        var entry = optional.get(); var side = entry.side(player.getUniqueId()); if (side.saved()) return true;
        try {
            var current = player.getInventory().getStorageContents(); var before = decode(side.before()); var after = decode(side.after());
            if (same(current, before)) player.getInventory().setStorageContents(after);
            else if (!same(current, after)) throw new IllegalStateException("Inventory differs from both saved trade snapshots; manual investigation required");
            saves.saveAndVerify(player); journal.acknowledge(entry.id(), player.getUniqueId()); return true;
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Player trade recovery held: " + entry.id() + " player " + player.getUniqueId(), error);
            player.kick(Component.text("Your trade needs a safety check. Please contact staff with trade " + entry.id() + "."));
            return false;
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onJoin(PlayerJoinEvent event) { recover(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { end(event.getPlayer(), "Trade cancelled because a player left."); requests.removeParticipant(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.LOWEST) public void onDeath(PlayerDeathEvent event) { end(event.getPlayer(), "Trade cancelled because a player died."); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) { end(event.getPlayer(), "Trade cancelled because a player travelled."); }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Session session) Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(event.getPlayer().getUniqueId()) == session && event.getPlayer().getOpenInventory().getTopInventory() != session.inventory)
                cancel(session, "Trade closed. No items were moved.");
        });
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Session) event.setCancelled(true); }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Session session) || !(event.getWhoClicked() instanceof Player player)) return;
        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled || sessions.get(player.getUniqueId()) != session || !RemoteTradePolicy.acceptsClick(event.getClick())) return;
        int raw = event.getRawSlot(), slot = event.getSlot(); boolean rightClick = event.getClick() == ClickType.RIGHT; long revision = session.revision;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(player.getUniqueId()) != session || player.getOpenInventory().getTopInventory() != session.inventory || session.revision != revision) return;
            if (!valid(session)) { cancel(session, "Trade closed. Both players must remain online and available."); return; }
            try {
                if (raw == MenuKit.closeSlot(54)) cancel(session, "Trade cancelled. No items were moved.");
                else if (raw == (player == session.left ? 47 : 51)) { MenuKit.clickSound(player); confirm(player, session); }
                else if (raw >= 54 && slot >= 0 && slot < 36) {
                    ItemStack item = player.getInventory().getItem(slot); if (item != null) add(player, session, slot, rightClick ? 1 : item.getAmount());
                } else {
                    int[] slots = player == session.left ? LEFT : RIGHT; var list = offers(session, player);
                    for (int i = 0; i < slots.length; i++) if (raw == slots[i] && i < list.size()) { list.remove(i); changed(session); break; }
                }
            } catch (IllegalArgumentException error) { session.accepted.clear(); redraw(session); refuse(player, error); }
            catch (RuntimeException error) { cancel(session, "Trade paused. No items were moved; please contact staff."); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Trade GUI failed", error); }
        });
    }
    private boolean valid(Session s) {
        return Bukkit.getPlayer(s.left.getUniqueId()) == s.left && Bukkit.getPlayer(s.right.getUniqueId()) == s.right
            && s.left.getOpenInventory().getTopInventory() == s.inventory && s.right.getOpenInventory().getTopInventory() == s.inventory
            && s.leftAccount.equals(account(s.left)) && s.rightAccount.equals(account(s.right)) && available(s.left, s.right);
    }
    private void end(Player player, String reason) { var s = sessions.get(player.getUniqueId()); if (s != null) cancel(s, reason); }
    private void detach(Session s) { sessions.remove(s.left.getUniqueId(), s); sessions.remove(s.right.getUniqueId(), s); }
    private void cancel(Session s, String reason) {
        detach(s);
        for (Player p : List.of(s.left, s.right)) if (p.isOnline()) {
            if (p.getOpenInventory().getTopInventory() == s.inventory) p.closeInventory(); Chat.notice(p, reason);
        }
    }
    private void redraw(Session s) {
        var inv = s.inventory; inv.clear();
        MenuKit.frame(inv);
        for (int i = 0; i < 9; i++) {
            inv.setItem(LEFT[i], preview(s.leftOffer, i)); inv.setItem(RIGHT[i], preview(s.rightOffer, i));
        }
        inv.setItem(2, MenuKit.item(Material.PLAYER_HEAD, "<aqua>" + Chat.escape(s.left.getName()) + "</aqua>", List.of("<gray>Left offer</gray>")));
        inv.setItem(6, MenuKit.item(Material.PLAYER_HEAD, "<aqua>" + Chat.escape(s.right.getName()) + "</aqua>", List.of("<gray>Right offer</gray>")));
        inv.setItem(4, MenuKit.item(Material.BOOK, "<gold>Review Both Offers</gold>", List.of("<gray>Left-click an inventory stack to offer it.</gray>",
            "<gray>Right-click offers one item.</gray>", "<gray>Click your preview to remove it.</gray>", "<gray>Items stay yours until both confirm.</gray>")));
        inv.setItem(47, confirmButton(s, s.left));
        inv.setItem(51, confirmButton(s, s.right));
        inv.setItem(MenuKit.closeSlot(54), MenuKit.item(Material.BARRIER, "<red>Cancel Trade</red>", MenuKit.withHint(List.of(
            "<gray>No items move until both players confirm.</gray>"), "cancel")));
    }
    private ItemStack confirmButton(Session s, Player side) {
        boolean accepted = s.accepted.contains(side.getUniqueId());
        String name = Chat.escape(side.getName());
        return accepted ? MenuKit.item(Material.LIME_DYE, "<green>" + name + " confirmed</green>", List.of("<gray>Any change to either offer clears this.</gray>"))
            : MenuKit.item(Material.YELLOW_DYE, "<yellow>Confirm: " + name + "</yellow>", List.of("<gray>Only " + name + " can press this.</gray>", "", MenuKit.hint("confirm both offers")));
    }
    private ItemStack preview(List<TradePlan.Offer<ItemStack>> offered, int i) {
        if (i >= offered.size()) return MenuKit.item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Empty offer slot</dark_gray>", List.of());
        var offer = offered.get(i); ItemStack item = offer.snapshot().item().clone(); item.setAmount(offer.amount()); return item;
    }
    private TradeInventoryCapacity.Stack<ItemStack> stack(ItemStack item) {
        return item == null || item.getType().isAir() ? null : new TradeInventoryCapacity.Stack<>(item.clone(), item.getAmount(), item.getMaxStackSize());
    }
    private static List<String> encode(ItemStack[] items) { return Arrays.stream(items).map(i -> i == null || i.getType().isAir() ? "" : MarketItems.encode(i)).toList(); }
    private static ItemStack[] decode(List<String> items) { return items.stream().map(i -> i.isEmpty() ? null : MarketItems.decode(i)).toArray(ItemStack[]::new); }
    private static boolean same(ItemStack[] left, ItemStack[] right) {
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            ItemStack a = left[i], b = right[i]; boolean emptyA = a == null || a.getType().isAir(), emptyB = b == null || b.getType().isAir();
            if (emptyA != emptyB || !emptyA && !a.equals(b)) return false;
        }
        return true;
    }
    private AccountId account(Player p) { return plugin.sessions().account(p.getUniqueId()).orElse(null); }
    private List<TradePlan.Offer<ItemStack>> offers(Session s, Player p) { return p == s.left ? s.leftOffer : s.rightOffer; }
    private void refuse(Player player, IllegalArgumentException error) {
        if (error instanceof Usage usage) Chat.usage(player, usage.getMessage());
        else Chat.error(player, error instanceof NumberFormatException ? "Choose a whole number." : Chat.escape(error.getMessage()));
    }
    /** A wrong command shape; the message is the usage syntax. */
    private static final class Usage extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private Usage(String syntax) { super(syntax); }
    }
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) { var choices = new ArrayList<>(List.of("accept","deny","cancel","offer","remove","confirm","view")); if (sender.hasPermission("nerdsmp.admin")) choices.add("review");
            Bukkit.getOnlinePlayers().stream().filter(p -> available(player, p)).map(Player::getName).forEach(choices::add); return choices; }
        if (args.length == 2 && args[0].equalsIgnoreCase("offer")) return List.of("1", Integer.toString(player.getInventory().getItemInMainHand().getAmount()));
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sessions.containsKey(player.getUniqueId()))
            return java.util.stream.IntStream.rangeClosed(1, offers(sessions.get(player.getUniqueId()), player).size()).mapToObj(Integer::toString).toList();
        return List.of();
    }
    @Override public void close() { guard.cancel(); new HashSet<>(sessions.values()).forEach(s -> cancel(s, "Trade closed for server restart. No items were moved.")); }
    private static final class Session implements InventoryHolder {
        final Player left, right; final AccountId leftAccount, rightAccount; Inventory inventory;
        final List<TradePlan.Offer<ItemStack>> leftOffer = new ArrayList<>(), rightOffer = new ArrayList<>();
        final Set<UUID> accepted = new HashSet<>(); final Instant expires = Instant.now().plusSeconds(300);
        long revision, reviewAfter;
        Session(Player left, Player right, AccountId a, AccountId b) { this.left = left; this.right = right; leftAccount = a; rightAccount = b; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
