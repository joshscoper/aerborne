package net.nerdsmp.paper.booster;

import net.kyori.adventure.bossbar.BossBar;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Server-wide experience boosters. Whoever uses one gives everyone online +50% skill and adventurer XP for
 * an hour; a second one used while it runs adds another hour. Nobody gets an edge over anybody else, which
 * is why they can be sold: the store grants {@value #PRODUCT} like any other product, one entitlement per
 * purchase, and each is spent when used. Clearing the raid earns one, once per tier a week.
 */
public final class BoosterService implements Listener, AutoCloseable {
    public static final String PRODUCT = "booster.xp";
    public static final double MULTIPLIER = 1.5;
    static final Duration LENGTH = Duration.ofHours(1);

    private final NerdSmpPlugin plugin;
    private final File file;
    private long until;
    private final List<String> thanks = new ArrayList<>();
    private final BossBar bar = BossBar.bossBar(Chat.render("<gold>XP Booster</gold>"), 1F, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private BukkitTask task;

    public BoosterService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boosters.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        until = yaml.getLong("active-until", 0L);
        thanks.addAll(yaml.getStringList("thanks"));
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** How much every XP award is worth right now. */
    public double multiplier() { return System.currentTimeMillis() < until ? MULTIPLIER : 1D; }

    /** Spends one of the player's boosters and starts it, or adds an hour to the one running. */
    public void use(Player player) {
        AccountId account = account(player);
        if (account == null) return;
        unspent(account).thenAccept(left -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (left.isEmpty()) {
                Chat.error(player, "You have no XP boosters. They come from the store at aerborne.net/store, and from clearing Cinder Keep.");
                return;
            }
            String spent = left.getFirst();
            plugin.playerData().update(account, yaml -> {
                List<String> done = new ArrayList<>(yaml.getStringList("boosters.spent"));
                done.add(spent);
                yaml.set("boosters.spent", done);
            });
            activate(player.getName());
        })).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not read boosters for " + player.getName(), error);
            return null;
        });
    }

    /** Where things stand: the server's booster, and how many the player has to use. */
    public void status(Player player) {
        long left = until - System.currentTimeMillis();
        if (left > 0) Chat.info(player, "An XP booster is running: <white>+50% XP</white> for everyone for <white>" + Chat.duration(Duration.ofMillis(left))
            + "</white>, thanks to <white>" + Chat.escape(String.join(", ", thanks)) + "</white>.");
        else Chat.info(player, "No XP booster is running.");
        AccountId account = account(player);
        if (account == null) return;
        unspent(account).thenAccept(mine -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (mine.isEmpty()) Chat.info(player, "You have no boosters. They come from the store at <white>aerborne.net/store</white>, and from clearing Cinder Keep.");
            else Chat.info(player, "You have <white>" + mine.size() + "</white> booster" + (mine.size() == 1 ? "" : "s")
                + ". <click:run_command:'/booster use'><yellow>[Use one]</yellow></click> <gray>gives everyone online +50% XP for an hour.</gray>");
        }));
    }

    /** Grants one booster for a feat, once per key: the transaction is fixed, so the same feat never pays twice. */
    public void earn(Player player, String key) {
        AccountId account = account(player);
        if (account == null) return;
        plugin.entitlements().grant(account, PRODUCT, "earned:" + key + ":" + account.value(), null).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (result == EntitlementService.GrantResult.APPLIED && player.isOnline())
                Chat.success(player, "You earned an XP booster! Use it with <white>/booster use</white> to give everyone online +50% XP for an hour.");
        }));
    }

    /** The player's boosters not yet used: purchases still active, less the ones they've spent. */
    private CompletableFuture<List<String>> unspent(AccountId account) {
        Set<String> spent = Set.copyOf(plugin.playerData().read(account, yaml -> yaml.getStringList("boosters.spent")));
        return plugin.entitlements().activeEntitlements(account, Instant.now()).toCompletableFuture().thenApply(all -> all.stream()
            .filter(entitlement -> PRODUCT.equals(entitlement.productId()) && !spent.contains(entitlement.transactionId()))
            .sorted(java.util.Comparator.comparing(EntitlementService.Entitlement::grantedAt))
            .map(EntitlementService.Entitlement::transactionId).toList());
    }

    private void activate(String name) {
        long now = System.currentTimeMillis();
        boolean running = until > now;
        until = Math.max(until, now) + LENGTH.toMillis();
        if (!running) thanks.clear();
        if (!thanks.contains(name)) thanks.add(name);
        while (thanks.size() > 4) thanks.removeFirst();
        save();
        String line = "<gold><bold>✦</bold> " + Chat.escape(name) + " used an XP booster!</gold> <gray>Everyone online gets <white>+50% XP</white> "
            + (running ? "for another hour: <white>" + Chat.duration(Duration.ofMillis(until - now)) + "</white> left." : "for the next hour.") + "</gray>";
        for (Player player : Bukkit.getOnlinePlayers()) Chat.send(player, line);
        tick();
    }

    private void tick() {
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            if (until != 0) {
                until = 0;
                thanks.clear();
                save();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.hideBossBar(bar);
                    Chat.info(player, "The XP booster has worn off. Thank you to everyone who brought one.");
                }
            }
            return;
        }
        bar.name(Chat.render("<gold>XP Booster</gold> <white>+50%</white> <gray>· " + Chat.duration(Duration.ofMillis(left))
            + " · thanks to</gray> <white>" + Chat.escape(String.join(", ", thanks)) + "</white>"));
        bar.progress((float) Math.min(1D, left / (double) LENGTH.toMillis()));
        for (Player player : Bukkit.getOnlinePlayers()) player.showBossBar(bar);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active-until", until);
        yaml.set("thanks", thanks);
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save boosters.yml", error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (until > System.currentTimeMillis()) event.getPlayer().showBossBar(bar);
    }

    private AccountId account(Player player) {
        return plugin.sessions().account(player.getUniqueId()).orElse(null);
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) player.hideBossBar(bar);
    }
}
