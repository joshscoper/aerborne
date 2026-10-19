package net.nerdsmp.paper.economy;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.*;

/**
 * {@code /pay}. {@code /marks pay} reaches the same handler as a silent alias, so every message
 * here names {@code /pay} only.
 */
public final class PaymentController implements AutoCloseable {
    static final String USAGE = "/pay <player> <amount>";
    private final NerdSmpPlugin plugin;
    private final PlayerPayments payments;
    private final Map<UUID, Long> last = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask task;
    private volatile boolean closed;
    private int retryCursor;
    public PaymentController(NerdSmpPlugin plugin) { this.plugin = plugin; payments = new PlayerPayments(new PlayerPayments.Store(new File(plugin.getDataFolder(), "payments")), plugin.economy()); }
    public PlayerPayments service() { return payments; }
    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            var pending = payments.all().stream().filter(PlayerPayments.Payment::pending).sorted(Comparator.comparing(PlayerPayments.Payment::created)).toList();
            for (int i = 0; i < Math.min(16, pending.size()); i++) {
                var payment = pending.get(Math.floorMod(retryCursor++, pending.size()));
                payments.resume(payment.id()).exceptionally(error -> null);
            }
        }, 20, 1200);
    }
    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId source = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (source == null) { Chat.accountLoading(player); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            var entries = payments.all().stream().filter(p -> p.source().equals(source) || p.destination().equals(source))
                .sorted(Comparator.comparing(PlayerPayments.Payment::created).reversed()).limit(8).toList();
            Chat.send(player, "<gold><bold>Recent Payments</bold></gold>");
            if (entries.isEmpty()) Chat.info(player, "You have no payment receipts yet.");
            entries.forEach(p -> Chat.info(player, (p.source().equals(source) ? "Sent " : "Received ") + Chat.marksTag(p.amount())
                + " · " + phase(p.phase()) + " · " + receipt(p.id())));
            return true;
        }
        if (args.length != 2) { Chat.usage(player, USAGE); Chat.info(player, "See your receipts with " + Chat.command("pay status") + "."); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) { Chat.error(player, "<white>" + Chat.escape(args[0]) + "</white> is not online."); return true; }
        AccountId destination = plugin.sessions().account(target.getUniqueId()).orElse(null);
        if (destination == null) { Chat.error(player, "<white>" + Chat.escape(target.getName()) + "</white>'s account is still loading. Try again in a moment."); return true; }
        if (source.equals(destination)) { Chat.error(player, "You cannot pay yourself."); return true; }
        if (!args[1].matches("[1-9][0-9]{0,9}")) { Chat.error(player, "Enter a whole number from " + Chat.marksTag(1) + " to " + Chat.marksTag(PlayerPayments.MAX_PAYMENT) + "."); return true; }
        long amount = Long.parseLong(args[1]);
        if (amount > PlayerPayments.MAX_PAYMENT) { Chat.error(player, "A payment can be at most " + Chat.marksTag(PlayerPayments.MAX_PAYMENT) + "."); return true; }
        long now = System.nanoTime();
        if (now - last.getOrDefault(player.getUniqueId(), 0L) < 2_000_000_000L) { Chat.error(player, "Wait a moment between payments."); return true; }
        last.put(player.getUniqueId(), now); if (last.size() > 2048) last.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        UUID receipt = UUID.randomUUID(); String targetName = Chat.escape(target.getName()), senderName = Chat.escape(player.getName());
        try {
            payments.pay(receipt, source, destination, amount).whenComplete((payment, error) -> {
                if (closed) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (closed) return;
                    if (error != null) {
                        if (player.isOnline()) Chat.notice(player, "Your payment is still settling. Keep receipt " + receipt(receipt)
                            + " and do not send it again. Check " + Chat.command("pay status") + ".");
                        plugin.getLogger().warning("Payment " + receipt + " needs retry; its durable receipt was retained."); return;
                    }
                    if (payment.phase() == PlayerPayments.Phase.DECLINED) { if (player.isOnline()) Chat.error(player, "You do not have " + Chat.marksTag(amount) + " to send."); return; }
                    if (player.isOnline() && plugin.sessions().account(player.getUniqueId()).filter(source::equals).isPresent())
                        Chat.success(player, "Paid " + Chat.marksTag(amount) + " to <white>" + targetName + "</white>. Receipt " + receipt(receipt) + ".");
                    if (target.isOnline() && plugin.sessions().account(target.getUniqueId()).filter(destination::equals).isPresent())
                        Chat.success(target, "Received " + Chat.marksTag(amount) + " from <white>" + senderName + "</white>.");
                    plugin.presentation().refresh(player); if (target.isOnline()) plugin.presentation().refresh(target);
                });
            });
        } catch (IllegalArgumentException error) { Chat.error(player, Chat.escape(error.getMessage())); }
        catch (RuntimeException error) {
            Chat.error(player, "The payment could not be started. Check " + Chat.command("pay status") + " before trying again.");
            plugin.getLogger().warning("Could not start payment " + receipt);
        }
        return true;
    }
    /** A receipt's state in words. */
    static String phase(PlayerPayments.Phase phase) {
        return switch (phase) {
            case PREPARED, DEBITED -> "<yellow>Settling</yellow>";
            case COMPLETE -> "<green>Complete</green>";
            case DECLINED -> "<red>Declined</red>";
        };
    }
    /** A short receipt that copies the full ID when clicked. */
    private static String receipt(UUID id) {
        return "<click:copy_to_clipboard:'" + id + "'><hover:show_text:'<gray>" + id + "<newline>Click to copy</gray>'><white>"
            + id.toString().substring(0, 8) + "</white></hover></click>";
    }
    @Override public void close() { closed = true; if (task != null) task.cancel(); last.clear(); }
}
