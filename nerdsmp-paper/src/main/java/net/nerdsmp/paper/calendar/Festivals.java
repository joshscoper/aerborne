package net.nerdsmp.paper.calendar;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Monthly festivals: a few days in the middle of each month take on that month's festival. Special visitors come to
 * every island, and anyone who joins in (collecting, harvesting, serving visitors) earns that festival's title and a
 * purse of Favour. The title is cosmetic, so a festival never gives anyone an edge.
 */
public final class Festivals implements Listener {
    public record Festival(String id, String name, String color, String flavour) {
        public String paint(String text) { return "<" + color + ">" + text + "</" + color + ">"; }
        /** The cosmetic key (and title) a participant earns. */
        public String key() { return "festival:" + id; }
    }

    /** One festival a month, January to December. */
    static final List<Festival> FESTIVALS = List.of(
        new Festival("frostfall", "Frostfall", "aqua", "Snow on the sky-isles, and cocoa by the forge."),
        new Festival("lantern-night", "Lantern Night", "gold", "Every island hangs a lantern for the ones still out flying."),
        new Festival("sproutfest", "Sproutfest", "green", "The first green shoots, and the first good soup."),
        new Festival("skyfair", "Skyfair", "light_purple", "Kites, skyships and a market in the clouds."),
        new Festival("bloomtide", "Bloomtide", "#ff77cc", "The orchards flower and the bees go mad for it."),
        new Festival("sunpeak", "Sunpeak", "yellow", "The longest days, and the warmest stone."),
        new Festival("stormwatch", "Stormwatch", "dark_aqua", "The wardens keep the night watch against the summer storms."),
        new Festival("starfall", "Starfall", "#a8e6ff", "Shooting stars over the Reach. Make a wish, then get back to work."),
        new Festival("harvest-moon", "Harvest Moon", "#ffaa00", "The great harvest, under the biggest moon of the year."),
        new Festival("hollow-night", "Hollow Night", "#ff7a1a", "Lanterns in pumpkins, and things in the dark."),
        new Festival("ember-feast", "Ember Feast", "red", "The forges burn all night and everyone eats."),
        new Festival("winterlight", "Winterlight", "white", "The year's last lights, and gifts left on every doorstep."));

    /** Points a player needs during a festival to earn its title and the Favour purse. */
    public static final int GOAL = 150;
    public static final long PURSE = 500;

    static Festival of(Month month) { return FESTIVALS.get(month.getValue() - 1); }

    private final NerdSmpPlugin plugin;
    private final int startDay, days;
    private String announced;

    public Festivals(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.startDay = Math.clamp(plugin.getConfig().getInt("events.festivals.start-day", 13), 1, 25);
        this.days = Math.clamp(plugin.getConfig().getInt("events.festivals.days", 4), 1, 7);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 400L, 1200L);
    }

    private ZoneId zone() { return plugin.boosters() == null ? ZoneId.of("America/Denver") : plugin.boosters().weekend().zone(); }

    /** Whether a day falls in its month's festival. */
    static boolean during(LocalDate day, int startDay, int days) {
        return day.getDayOfMonth() >= startDay && day.getDayOfMonth() < startDay + days;
    }

    /** The festival on now, or null. */
    public Festival current() {
        if (!plugin.getConfig().getBoolean("events.festivals.enabled", true)) return null;
        LocalDate day = LocalDate.now(zone());
        return during(day, startDay, days) ? of(day.getMonth()) : null;
    }

    /** When the next festival starts (the current one's start if one is on), and which. */
    public Instant nextStart() {
        LocalDate day = LocalDate.now(zone());
        LocalDate start = day.withDayOfMonth(startDay);
        if (day.getDayOfMonth() >= startDay + days) start = start.plusMonths(1);
        return start.atStartOfDay(zone()).toInstant();
    }
    public Festival next() {
        LocalDate day = LocalDate.now(zone());
        return of((day.getDayOfMonth() >= startDay + days ? day.plusMonths(1) : day).getMonth());
    }
    public Instant currentEnd() {
        LocalDate day = LocalDate.now(zone());
        return day.withDayOfMonth(startDay).plusDays(days).atStartOfDay(zone()).toInstant();
    }

    private String term() { return YearMonth.now(zone()).toString(); }

    private void tick() {
        Festival festival = current();
        if (festival == null || term().equals(announced)) return;
        announced = term();
        for (Player player : Bukkit.getOnlinePlayers()) greet(player, festival);
    }

    private void greet(Player player, Festival festival) {
        Chat.send(player, festival.paint("<b>✦ " + festival.name() + "</b>") + " <gray>is here! " + Chat.escape(festival.flavour()) + "</gray>");
        Chat.send(player, "<gray>Special visitors are about. Collect, harvest and serve visitors to earn the</gray> " + festival.paint(festival.name()) + " <gray>title.</gray> " + Chat.command("calendar"));
        ClientFeeds.toast(player, "quest", Chat.render(festival.paint(festival.name())), Chat.render("<gray>A festival has begun</gray>"));
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6F, 1.1F);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Festival festival = current();
        if (festival == null) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) greet(player, festival); }, 140L);
    }

    /** Festival points for something the player did; the title and purse come at the goal. */
    public void progress(Player player, int points) {
        Festival festival = current();
        if (festival == null || points <= 0) return;
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) return;
        String key = "festivals." + term();
        int[] now = {0};
        boolean[] done = {false};
        plugin.playerData().update(account, yaml -> {
            done[0] = yaml.getBoolean(key + ".done", false);
            now[0] = yaml.getInt(key + ".points", 0) + points;
            yaml.set(key + ".points", now[0]);
            if (!done[0] && now[0] >= GOAL) yaml.set(key + ".done", true);
        });
        if (done[0] || now[0] < GOAL) return;
        if (plugin.cosmetics() != null) plugin.cosmetics().earn(player, List.of(festival.key()));
        if (plugin.skyblock() != null && plugin.skyblock().guests() != null) plugin.skyblock().guests().addFavour(account, PURSE);
        Chat.send(player, festival.paint("<b>✦ " + festival.name() + "</b>") + " <gold>You joined in! The</gold> " + festival.paint(festival.name()) + " <gold>title is yours, and</gold> <yellow>+" + PURSE + " Favour</yellow><gold>.</gold>");
        ClientFeeds.toast(player, "achievement", Chat.render(festival.paint(festival.name())), Chat.render("<white>Title earned</white>"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
    }

    public int points(AccountId account) { return plugin.playerData().read(account, yaml -> yaml.getInt("festivals." + term() + ".points", 0)); }

    static String slug(Festival festival) { return festival.id().toLowerCase(Locale.ROOT); }
}
