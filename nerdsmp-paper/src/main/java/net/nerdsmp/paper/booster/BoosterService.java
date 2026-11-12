package net.nerdsmp.paper.booster;

import net.kyori.adventure.bossbar.BossBar;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Server-wide experience boosters. Whoever uses one gives everyone online +50% skill and adventurer XP for
 * an hour; a second one used while it runs adds another hour. Nobody gets an edge over anybody else, which
 * is why they can be sold: the store grants {@value #PRODUCT} like any other product, one entitlement per
 * purchase, and each is spent when used. Clearing the raid earns one, once per tier a week.
 * <p>
 * Every week also brings a Double XP Weekend ({@link WeekendSchedule}, {@code events.double-xp-weekend} in the
 * config): by default Saturday 08:00 to Sunday 23:59, ×2 skill, adventurer and island XP for everyone. A booster
 * used during it multiplies on top.
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
    private final WeekendSchedule weekend;
    /** The start of the last weekend announced, so a restart during one doesn't announce it again. */
    private long weekendAnnounced;
    private boolean weekendShown;
    private final BossBar weekendBar = BossBar.bossBar(Chat.render("<light_purple>Double XP Weekend</light_purple>"), 1F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);

    public BoosterService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boosters.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        until = yaml.getLong("active-until", 0L);
        thanks.addAll(yaml.getStringList("thanks"));
        weekendAnnounced = yaml.getLong("weekend-announced", 0L);
        weekend = weekend(plugin.getConfig().getConfigurationSection("events.double-xp-weekend"), plugin);
    }

    /** Reads {@code events.double-xp-weekend}; a bad value is reported and the standard Saturday-to-Sunday used. */
    static WeekendSchedule weekend(org.bukkit.configuration.ConfigurationSection section, NerdSmpPlugin plugin) {
        ZoneId fallbackZone = ZoneId.of("America/Denver");
        if (section == null) return WeekendSchedule.standard(fallbackZone);
        try {
            return new WeekendSchedule(section.getBoolean("enabled", true),
                ZoneId.of(section.getString("zone", "America/Denver")),
                DayOfWeek.valueOf(section.getString("start-day", "SATURDAY").toUpperCase(Locale.ROOT)),
                LocalTime.parse(section.getString("start-time", "08:00")),
                DayOfWeek.valueOf(section.getString("end-day", "SUNDAY").toUpperCase(Locale.ROOT)),
                LocalTime.parse(section.getString("end-time", "23:59")),
                section.getDouble("multiplier", 2D));
        } catch (RuntimeException error) {
            if (plugin != null) plugin.getLogger().log(Level.WARNING, "events.double-xp-weekend is invalid; using Saturday 08:00 to Sunday 23:59", error);
            return WeekendSchedule.standard(fallbackZone);
        }
    }

    public WeekendSchedule weekend() { return weekend; }

    /** The weekend's own multiplier right now: 2 during a double XP weekend, otherwise 1. */
    public double weekendMultiplier() { return weekend.multiplierAt(Instant.now()); }

    /** Just the purchased or earned booster's multiplier, without the weekend. */
    public double boosterMultiplier() { return System.currentTimeMillis() < until ? MULTIPLIER : 1D; }

    /** What is raising XP right now ("×2 Double XP Weekend, +50% XP booster"), or empty, for chat lines. */
    public String label() {
        List<String> parts = new ArrayList<>();
        if (weekendMultiplier() > 1D) parts.add("×" + times(weekend.multiplier()) + " Double XP Weekend");
        if (boosterMultiplier() > 1D) parts.add("+50% XP booster");
        return String.join(", ", parts);
    }

    static String times(double multiplier) {
        return multiplier == Math.rint(multiplier) ? Long.toString((long) multiplier) : String.format(Locale.ROOT, "%.1f", multiplier);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** How much every XP award is worth right now: a running booster and the double XP weekend multiply. */
    public double multiplier() { return boosterMultiplier() * weekendMultiplier(); }

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
        weekendStatus(player);
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
        earn(account, key).thenAccept(applied -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (applied && player.isOnline())
                Chat.success(player, "You earned an XP booster! Use it with <white>/booster use</white> to give everyone online +50% XP for an hour.");
        }));
    }

    /** {@link #earn(Player, String)} for a player who may be offline. Completes with whether this grant was new. */
    public CompletableFuture<Boolean> earn(AccountId account, String key) {
        return plugin.entitlements().grant(account, PRODUCT, "earned:" + key + ":" + account.value(), null).toCompletableFuture()
            .thenApply(result -> result == EntitlementService.GrantResult.APPLIED);
    }

    /** The player's boosters not yet used: purchases still active, less the ones they've spent. */
    private CompletableFuture<List<String>> unspent(AccountId account) {
        Set<String> spent = Set.copyOf(plugin.playerData().read(account, yaml -> yaml.getStringList("boosters.spent")));
        return plugin.entitlements().activeEntitlements(account, Instant.now()).toCompletableFuture().thenApply(all -> all.stream()
            .filter(entitlement -> PRODUCT.equals(entitlement.productId()) && !spent.contains(entitlement.transactionId()))
            .sorted(java.util.Comparator.comparing(EntitlementService.Entitlement::grantedAt))
            .map(EntitlementService.Entitlement::transactionId).toList());
    }

    /**
     * Starts an hour of boosted XP that nobody spent a booster on, such as the vote goal's reward, or adds an hour to
     * the one running. {@code thanksTo} is shown on the boss bar; {@code announcement} opens the chat line.
     */
    public void gift(String thanksTo, String announcement) {
        activate(thanksTo, announcement);
    }

    private void activate(String name) {
        activate(name, "<gold><bold>✦</bold> " + Chat.escape(name) + " used an XP booster!</gold>");
    }

    private void activate(String name, String announcement) {
        long now = System.currentTimeMillis();
        boolean running = until > now;
        until = Math.max(until, now) + LENGTH.toMillis();
        if (!running) thanks.clear();
        if (!thanks.contains(name)) thanks.add(name);
        while (thanks.size() > 4) thanks.removeFirst();
        save();
        String line = announcement + " <gray>Everyone online gets <white>+50% XP</white> "
            + (running ? "for another hour: <white>" + Chat.duration(Duration.ofMillis(until - now)) + "</white> left." : "for the next hour.") + "</gray>";
        for (Player player : Bukkit.getOnlinePlayers()) Chat.send(player, line);
        tick();
    }

    /** One line on the double XP weekend: running and how long is left, or when the next one starts. */
    public void weekendStatus(Player player) {
        Instant now = Instant.now();
        var current = weekend.current(now);
        if (current.isPresent()) {
            Chat.info(player, "<light_purple>Double XP Weekend</light_purple> is on: <white>×" + times(weekend.multiplier())
                + " skill, adventurer and island XP</white> for everyone for <white>" + Chat.duration(current.get().left(now)) + "</white>."
                + (boosterMultiplier() > 1D ? " <gray>The XP booster stacks on top.</gray>" : ""));
            return;
        }
        weekend.next(now).ifPresent(next -> Chat.info(player, "The next <light_purple>Double XP Weekend</light_purple> starts in <white>"
            + Chat.duration(Duration.between(now, next.start())) + "</white> <gray>(" + when(next.start()) + ")</gray>."));
    }

    /** "Saturday 8:00 AM MST": a time in the event's own zone. */
    String when(Instant instant) {
        return instant.atZone(weekend.zone()).format(DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.ENGLISH)) + " "
            + weekend.zone().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private void tickWeekend() {
        Instant now = Instant.now();
        var current = weekend.current(now);
        if (current.isEmpty()) {
            if (weekendShown) {
                weekendShown = false;
                String next = weekend.next(now).map(window -> when(window.start())).orElse("next weekend");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.hideBossBar(weekendBar);
                    Chat.info(player, "<light_purple>Double XP Weekend</light_purple> is over. Thanks for playing! The next one starts <white>" + next + "</white>.");
                }
            }
            return;
        }
        WeekendSchedule.Window window = current.get();
        if (weekendAnnounced != window.start().toEpochMilli()) {
            weekendAnnounced = window.start().toEpochMilli();
            save();
            String line = "<light_purple><bold>✦ Double XP Weekend has begun!</bold></light_purple> <gray>Everyone earns <white>×"
                + times(weekend.multiplier()) + " skill, adventurer and island XP</white> until <white>" + when(window.end()) + "</white>."
                + " XP boosters stack on top.</gray>";
            for (Player player : Bukkit.getOnlinePlayers()) {
                Chat.send(player, line);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
            }
        }
        weekendShown = true;
        weekendBar.name(Chat.render("<light_purple>Double XP Weekend</light_purple> <white>×" + times(weekend.multiplier())
            + " XP</white> <gray>· ends in " + Chat.duration(window.left(now)) + "</gray>"));
        weekendBar.progress((float) Math.clamp(window.left(now).toMillis() / (double) Math.max(1L, window.length().toMillis()), 0D, 1D));
        for (Player player : Bukkit.getOnlinePlayers()) player.showBossBar(weekendBar);
    }

    private void tick() {
        tickWeekend();
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
        yaml.set("weekend-announced", weekendAnnounced);
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save boosters.yml", error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (until > System.currentTimeMillis()) event.getPlayer().showBossBar(bar);
        if (weekend.active(Instant.now())) {
            Player player = event.getPlayer();
            player.showBossBar(weekendBar);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) weekendStatus(player); }, 60L);
        }
    }

    private AccountId account(Player player) {
        return plugin.sessions().account(player.getUniqueId()).orElse(null);
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) { player.hideBossBar(bar); player.hideBossBar(weekendBar); }
    }
}
