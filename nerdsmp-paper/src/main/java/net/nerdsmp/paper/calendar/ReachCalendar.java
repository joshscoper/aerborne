package net.nerdsmp.paper.calendar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Reach Calendar: everything on the server's schedule in one place. The Double XP Weekend, farming contests, the
 * world boss, festivals, the Wardens' Council vote and the island race payout. Shown in game with /calendar and on the
 * website through the world feed.
 */
public final class ReachCalendar implements Listener {
    /** One thing on the calendar: what, when it starts and ends (end null for a moment), and a line about it. */
    public record Event(String kind, String title, Material icon, Instant start, Instant end, String about) {
        public boolean live(Instant now) { return end != null && !now.isBefore(start) && now.isBefore(end); }
    }

    private final NerdSmpPlugin plugin;

    public ReachCalendar(NerdSmpPlugin plugin) { this.plugin = plugin; }

    private ZoneId zone() { return plugin.boosters() == null ? ZoneId.of("America/Denver") : plugin.boosters().weekend().zone(); }

    /** Everything coming up (or on now), soonest first. */
    public List<Event> upcoming() {
        Instant now = Instant.now();
        List<Event> events = new ArrayList<>();
        if (plugin.boosters() != null) {
            var weekend = plugin.boosters().weekend();
            weekend.current(now).or(() -> weekend.next(now)).ifPresent(window -> events.add(new Event("weekend", "Double XP Weekend", Material.EXPERIENCE_BOTTLE,
                window.start(), window.end(), "×2 skill, adventurer and island XP for everyone. Special visitors and farming contests too.")));
        }
        if (plugin.skyblock() != null && plugin.skyblock().contests() != null)
            events.add(new Event("contest", "Farming Contest", Material.GOLDEN_CARROT, now, null, stripTags(plugin.skyblock().contests().summary())));
        if (plugin.reach() != null && plugin.reach().worldBoss() != null) {
            Instant boss = plugin.reach().worldBoss().nextArrival();
            if (boss != null) events.add(new Event("boss", "Thunderhide", Material.LIGHTNING_ROD, boss, null, "The world boss comes to the Stone Rings. /worldboss go"));
        }
        if (plugin.festivals() != null) {
            Festivals.Festival festival = plugin.festivals().current();
            if (festival != null) events.add(new Event("festival", festival.name(), Material.FIREWORK_ROCKET, Instant.now().minusSeconds(1), plugin.festivals().currentEnd(), festival.flavour()));
            else {
                Festivals.Festival next = plugin.festivals().next();
                Instant start = plugin.festivals().nextStart();
                events.add(new Event("festival", next.name(), Material.FIREWORK_ROCKET, start, start.plus(Duration.ofDays(4)), next.flavour()));
            }
        }
        if (plugin.council() != null) {
            boolean voting = WardensCouncil.voting(plugin.council().today());
            Instant change = plugin.council().nextChange();
            events.add(voting ? new Event("council", "Council vote", Material.BELL, now.minusSeconds(1), change, "Vote for this month's Wardens' Council seat. /council")
                : new Event("council", "Council vote", Material.BELL, change, change.plus(Duration.ofDays(WardensCouncil.VOTING_DAYS)), "Vote for next month's Wardens' Council seat. /council"));
        }
        ZonedDateTime monday = ZonedDateTime.now(zone()).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(zone());
        events.add(new Event("race", "Island race payout", Material.GOLD_BLOCK, monday.toInstant(), null, "The week's top three island co-ops are paid. /island top"));
        events.sort(Comparator.comparing((Event e) -> e.live(now) ? Instant.EPOCH : e.start()));
        return events;
    }

    private static String stripTags(String markup) { return markup.replaceAll("<[^>]*>", ""); }

    private static String when(Event event, Instant now) {
        if (event.live(now)) return "<green>On now</green> <gray>· ends in " + Chat.duration(Duration.between(now, event.end())) + "</gray>";
        if (event.kind().equals("contest")) return "";
        Duration until = Duration.between(now, event.start());
        return until.isNegative() ? "<gray>Soon</gray>" : "<gray>In</gray> <white>" + Chat.duration(until) + "</white>";
    }

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Reach Calendar</gold>"));
        MenuKit.frame(holder.inventory);
        Instant now = Instant.now();
        List<String> head = new ArrayList<>();
        head.add("<gray>Everything on the server's schedule.</gray>");
        if (plugin.council() != null) {
            var seat = plugin.council().seated();
            head.add(seat == null ? "<gray>Council seat: empty</gray>" : "<gray>Council:</gray> <white>" + Chat.escape(seat.name()) + "</white> <aqua>· " + seat.perk().title() + "</aqua>");
        }
        if (plugin.festivals() != null && plugin.festivals().current() != null) {
            var account = plugin.sessions().account(player.getUniqueId()).orElse(null);
            if (account != null) head.add("<gray>Festival progress:</gray> <white>" + Math.min(Festivals.GOAL, plugin.festivals().points(account)) + "/" + Festivals.GOAL + "</white>");
        }
        holder.inventory.setItem(4, MenuKit.item(Material.CLOCK, "<gold>Reach Calendar</gold>", head));
        List<Event> events = upcoming();
        for (int i = 0; i < events.size() && i < SLOTS.length; i++) {
            Event event = events.get(i);
            List<String> lore = new ArrayList<>();
            String when = when(event, now);
            if (!when.isEmpty()) lore.add(when);
            lore.add("<gray>" + Chat.escape(event.about()) + "</gray>");
            var icon = MenuKit.item(event.icon(), (event.live(now) ? "<green>" : "<white>") + Chat.escape(event.title()) + (event.live(now) ? "</green>" : "</white>"), lore);
            if (event.live(now)) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            holder.inventory.setItem(SLOTS[i], icon);
        }
        holder.inventory.setItem(40, MenuKit.item(Material.BELL, "<gold>Wardens' Council</gold>", List.of("<gray>See who holds the seat and vote.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            if (slot == 40 && plugin.council() != null) plugin.council().open(player);
            else if (slot == 49) player.closeInventory();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    public JsonObject json() {
        JsonObject out = new JsonObject();
        Instant now = Instant.now();
        JsonArray list = new JsonArray();
        for (Event event : upcoming()) {
            JsonObject row = new JsonObject();
            row.addProperty("kind", event.kind());
            row.addProperty("title", event.title());
            row.addProperty("start", event.start().toString());
            if (event.end() != null) row.addProperty("end", event.end().toString());
            row.addProperty("live", event.live(now));
            row.addProperty("about", event.about());
            list.add(row);
        }
        out.add("events", list);
        if (plugin.festivals() != null && plugin.festivals().current() != null) out.addProperty("festival", plugin.festivals().current().name());
        return out;
    }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; Inventory inventory;
        Holder(UUID viewer) { this.viewer = viewer; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
