package net.nerdsmp.paper.item;

import net.kyori.adventure.text.Component;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Actual player vitality and a single action-bar owner, shared with temporary gameplay notices. */
public final class PlayerStatsService implements Listener, AutoCloseable {
    private static volatile PlayerStatsService active;
    private final NerdSmpPlugin plugin;
    private final NamespacedKey healthKey;
    private final PlayerStatRules.HealthRules healthRules;
    private final Map<UUID, DisplayState> displays = new HashMap<>();
    private final Map<UUID, Notice> notices = new HashMap<>();
    private final boolean enabled, actionBar;
    private BukkitTask task;

    public PlayerStatsService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        healthKey = new NamespacedKey(plugin, "player_vitality");
        enabled = plugin.getConfig().getBoolean("player-stats.enabled", true);
        actionBar = plugin.getConfig().getBoolean("player-stats.action-bar", true);
        healthRules = new PlayerStatRules.HealthRules(plugin.getConfig().getDouble("player-stats.base-health", 20),
            plugin.getConfig().getDouble("player-stats.health-per-adventurer-level", 2),
            plugin.getConfig().getDouble("player-stats.maximum-health", 512));
    }
    public void start() {
        active = this;
        Chat.routeBar(PlayerStatsService::message);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 1, 10);
    }
    public Snapshot snapshot(Player player) {
        var gear = plugin.gearSets().profile(player);
        var progression = plugin.presentation().progression(player);
        var health = healthRules.health(progression.adventurer().level(), gear.stat("max-health"), gear.stat("max-health-percent"));
        double defense = Math.max(0, gear.stat("defense") + progression.adventurerStat("defense"));
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double actualMaximum = attribute == null ? 20 : attribute.getValue();
        return new Snapshot(health, actualMaximum, defense, PlayerStatRules.defenseReduction(defense),
            PlayerStatRules.effectiveHealth(actualMaximum, defense), gear, progression.adventurer().level());
    }
    public void refresh(Player player) {
        if (!enabled || !playable(player)) { restore(player); return; }
        DisplayState display = displays.computeIfAbsent(player.getUniqueId(), id -> new DisplayState(player.isHealthScaled(), player.getHealthScale()));
        player.setHealthScale(PlayerStatRules.HEART_DISPLAY);
        player.setHealthScaled(true);
        Snapshot snapshot = snapshot(player);
        if (plugin.presentation().progressionReady(player) && !player.isDead()) {
            var attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                double current = player.getHealth(), previousMaximum = attribute.getValue();
                double amount = snapshot.health().maximum() - 20; // Own bonus only; other plugins and potion modifiers are retained.
                var existing = attribute.getModifier(healthKey);
                if (existing == null || Math.abs(existing.getAmount() - amount) > 0.0001) {
                    attribute.removeModifier(healthKey);
                    if (amount != 0) attribute.addTransientModifier(new AttributeModifier(healthKey, amount, AttributeModifier.Operation.ADD_NUMBER));
                }
                double next = PlayerStatRules.adjustedHealth(current, previousMaximum, attribute.getValue(), !display.initialized);
                if (next > 0 && Math.abs(next - player.getHealth()) > .0001) player.setHealth(next);
                display.initialized = true;
            }
        }
        if (actionBar) render(player, snapshot.defense());
    }

    /** Preserve vitals while XP, catches, quest guidance or warnings occupy the action bar briefly. */
    public static void message(Player player, Component message) {
        // The Aerborne client shows notices in its own HUD.
        if (net.nerdsmp.paper.presentation.ClientHudFeed.modded(player)) { net.nerdsmp.paper.presentation.ClientHudFeed.notice(player, message); return; }
        PlayerStatsService service = active;
        if (service == null || !service.enabled || !service.actionBar || !playable(player)) { player.sendActionBar(message); return; }
        service.notices.put(player.getUniqueId(), new Notice(message, System.nanoTime() + 3_000_000_000L));
        service.render(player, service.snapshot(player).defense());
    }
    private void render(Player player, double defense) {
        if (!player.isOnline() || player.isDead() || net.nerdsmp.paper.presentation.ClientHudFeed.modded(player)) return;
        var maximum = player.getAttribute(Attribute.MAX_HEALTH);
        double total = maximum == null ? 20 : maximum.getValue();
        String absorption = player.getAbsorptionAmount() > 0 ? " <gold>+" + number(player.getAbsorptionAmount()) + "</gold>" : "";
        // The heart darkens while recovery is held back; a countdown here only ever said the same thing louder.
        String heart = plugin.recovery() != null && plugin.recovery().delayed(player) ? "dark_red" : "red";
        Notice notice = notices.get(player.getUniqueId());
        boolean showing = notice != null && notice.until() > System.nanoTime();
        // One thing beside the vitals at a time: a passing notice takes the place of any buff.
        Component bar = Chat.render("<" + heart + ">❤ " + number(player.getHealth()) + "/" + number(total) + "</" + heart + ">" + absorption
            + " <dark_gray>│</dark_gray> <green>❈ " + number(defense) + " Defense</green>"
            + (showing || plugin.recovery() == null ? "" : plugin.recovery().status(player)));
        if (showing) bar = bar.append(Chat.render(" <dark_gray>│</dark_gray> ")).append(notice.message());
        else notices.remove(player.getUniqueId());
        player.sendActionBar(bar);
    }
    public static String number(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
    private static boolean playable(Player player) { return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE; }
    private void restore(Player player) {
        DisplayState display = displays.remove(player.getUniqueId()); notices.remove(player.getUniqueId());
        if (display == null) return;
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            double current = player.getHealth(), maximum = attribute.getValue();
            attribute.removeModifier(healthKey);
            if (!player.isDead()) {
                double next = PlayerStatRules.adjustedHealth(current, maximum, attribute.getValue(), true);
                if (next > 0) player.setHealth(next);
            }
        }
        player.setHealthScale(display.originalScale); player.setHealthScaled(display.originalScaled);
        if (player.isOnline() && actionBar) player.sendActionBar(Component.empty());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { refresh(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) { restore(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void mode(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (event.getPlayer().isOnline()) refresh(event.getPlayer()); });
    }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer(); if (!player.isOnline() || !playable(player)) return;
            refresh(player); var maximum = player.getAttribute(Attribute.MAX_HEALTH);
            if (enabled && maximum != null && !player.isDead()) player.setHealth(maximum.getValue());
        });
    }
    @Override public void close() {
        Chat.routeBar((player, line) -> player.sendActionBar(line));
        if (task != null) task.cancel();
        Bukkit.getOnlinePlayers().forEach(this::restore);
        displays.clear(); notices.clear();
        if (active == this) active = null;
    }
    private static final class DisplayState {
        private final boolean originalScaled; private final double originalScale; private boolean initialized;
        private DisplayState(boolean scaled, double scale) { originalScaled = scaled; originalScale = scale; }
    }
    private record Notice(Component message, long until) { }
    public record Snapshot(PlayerStatRules.Health health, double maximumHealth, double defense, double reduction,
                           double effectiveHealth, GearSetService.GearProfile gear, int adventurerLevel) { }
}
