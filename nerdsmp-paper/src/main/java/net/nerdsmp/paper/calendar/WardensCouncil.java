package net.nerdsmp.paper.calendar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Wardens' Council, after Hypixel SkyBlock's mayors. Each month three figures of the Reach stand for the council
 * seat; for the first {@value #VOTING_DAYS} days everyone gets one vote (and can change it), then the winner takes the
 * seat until the next month's vote closes, and their perk applies to the whole server. Perks are server-wide and voted
 * for, so they never favour anyone who paid.
 */
public final class WardensCouncil implements Listener, AutoCloseable {
    /** What a councillor does while seated. Read by the systems they change. */
    public enum Perk {
        OPEN_DOORS("Open Doors", "Island visitors come 20% sooner."),
        BOUNTIFUL_FIELDS("Bountiful Fields", "Crop milestone extra-crop chances are doubled."),
        FREE_MARKET("Free Market", "The Bazaar's sales tax is halved."),
        COLLECTORS_ZEAL("Collector's Zeal", "Collection tiers pay 50% more Marks."),
        CROWNS_FAVOUR("Crown's Favour", "Visitors pay 25% more Favour."),
        HARVEST_CONTESTS("Harvest Contests", "Farming contests run every hour of the weekend, and medals need 20% less.");
        final String title, about;
        Perk(String title, String about) { this.title = title; this.about = about; }
        public String title() { return title; }
        public String about() { return about; }
    }

    public record Candidate(String id, String name, String pitch, Material icon, Perk perk) { }

    static final List<Candidate> CANDIDATES = List.of(
        new Candidate("tilly", "Tilly Miller", "\"Every door in the Reach should be open. Especially yours.\"", Material.WHEAT, Perk.OPEN_DOORS),
        new Candidate("ada", "Guildmistress Ada", "\"Feed the mill, feed the Reach. I'll see the fields fat.\"", Material.GOLDEN_HOE, Perk.BOUNTIFUL_FIELDS),
        new Candidate("ebb", "Dockmaster Ebb", "\"Let trade flow like the wind under a hull.\"", Material.OAK_BOAT, Perk.FREE_MARKET),
        new Candidate("sorrel", "Archivist Sorrel", "\"A thing gathered is a thing remembered. I'll pay for memory.\"", Material.WRITABLE_BOOK, Perk.COLLECTORS_ZEAL),
        new Candidate("envoy", "Envoy of Highcrown", "\"The Crown is generous to those who are generous.\"", Material.GOLDEN_HELMET, Perk.CROWNS_FAVOUR),
        new Candidate("ellery", "Contest Judge Ellery", "\"More contests, fairer medals. The fields deserve a festival.\"", Material.GOLDEN_CARROT, Perk.HARVEST_CONTESTS));

    static final String NOBODY = "none";
    /** Days at the start of each month when the vote is open. */
    public static final int VOTING_DAYS = 5;

    /** The three who stand in a month's election, decided by the month alone. */
    static List<Candidate> standing(YearMonth term) {
        List<Candidate> all = new ArrayList<>(CANDIDATES);
        java.util.Collections.shuffle(all, new Random(term.getYear() * 13L + term.getMonthValue()));
        return List.copyOf(all.subList(0, 3));
    }

    /** Whether a day is in its month's voting window. */
    static boolean voting(LocalDate day) { return day.getDayOfMonth() <= VOTING_DAYS; }

    /** The winner of a tally: most votes, ties going to the first standing. */
    static Candidate winner(List<Candidate> standing, Map<String, Integer> tally) {
        Candidate best = standing.getFirst();
        for (Candidate candidate : standing) if (tally.getOrDefault(candidate.id(), 0) > tally.getOrDefault(best.id(), 0)) best = candidate;
        return best;
    }

    private final NerdSmpPlugin plugin;
    private final File file;
    private final Map<String, Map<UUID, String>> votes = new HashMap<>();
    private final Map<String, String> seated = new LinkedHashMap<>();
    private org.bukkit.scheduler.BukkitTask task;

    public WardensCouncil(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "council.yml");
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 1200L);
    }

    private ZoneId zone() { return plugin.boosters() == null ? ZoneId.of("America/Denver") : plugin.boosters().weekend().zone(); }
    LocalDate today() { return LocalDate.now(zone()); }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection v = yaml.getConfigurationSection("votes");
        if (v != null) for (String term : v.getKeys(false)) {
            Map<UUID, String> ballots = new HashMap<>();
            ConfigurationSection t = v.getConfigurationSection(term);
            for (String voter : t.getKeys(false)) try { ballots.put(UUID.fromString(voter), t.getString(voter)); } catch (RuntimeException ignored) { }
            votes.put(term, ballots);
        }
        ConfigurationSection s = yaml.getConfigurationSection("seated");
        if (s != null) for (String term : s.getKeys(false)) seated.put(term, s.getString(term));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        votes.forEach((term, ballots) -> ballots.forEach((voter, choice) -> yaml.set("votes." + term + "." + voter, choice)));
        seated.forEach((term, id) -> yaml.set("seated." + term, id));
        try { AtomicYamlFiles.save(yaml, file); } catch (IOException failure) { plugin.getLogger().warning("The council could not be saved: " + failure); }
    }

    /** Once a minute: when a month's vote closes, its winner is seated and the server hears about it. */
    private void tick() {
        LocalDate day = today();
        YearMonth term = YearMonth.from(day);
        if (voting(day) || seated.containsKey(term.toString())) return;
        Map<String, Integer> tally = tally(term);
        // Nobody voted (the council's first month, say): the seat stays empty rather than going to whoever's listed first.
        if (tally.isEmpty()) { seated.put(term.toString(), NOBODY); save(); return; }
        Candidate winner = winner(standing(term), tally);
        seated.put(term.toString(), winner.id());
        save();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Chat.send(player, "<gold>⚖ The Wardens' Council has a new seat-holder:</gold> <white>" + Chat.escape(winner.name()) + "</white><gold>!</gold> <aqua>"
                + winner.perk().title + ":</aqua> <gray>" + winner.perk().about + "</gray>");
            ClientFeeds.toast(player, "quest", Chat.render("<gold>Wardens' Council</gold>"), Chat.render("<white>" + Chat.escape(winner.name()) + "</white> <gray>· " + winner.perk().title + "</gray>"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1F);
        }
    }

    Map<String, Integer> tally(YearMonth term) {
        Map<String, Integer> tally = new HashMap<>();
        votes.getOrDefault(term.toString(), Map.of()).values().forEach(choice -> tally.merge(choice, 1, Integer::sum));
        return tally;
    }

    /** Who holds the seat now: this month's winner once the vote closes, last month's while it's open. */
    public Candidate seated() {
        LocalDate day = today();
        YearMonth term = voting(day) ? YearMonth.from(day).minusMonths(1) : YearMonth.from(day);
        String id = seated.get(term.toString());
        if (id == null && !voting(day) && !tally(term).isEmpty()) id = winner(standing(term), tally(term)).id();
        String found = id;
        return found == null ? null : CANDIDATES.stream().filter(c -> c.id().equals(found)).findFirst().orElse(null);
    }

    /** Whether a perk is in force right now. */
    public boolean active(Perk perk) {
        Candidate holder = seated();
        return holder != null && holder.perk() == perk;
    }

    /** When the current vote closes (if open) or the next one opens. */
    Instant nextChange() {
        LocalDate day = today();
        LocalDate change = voting(day) ? day.withDayOfMonth(VOTING_DAYS + 1) : day.plusMonths(1).withDayOfMonth(1);
        return change.atStartOfDay(zone()).toInstant();
    }

    // ------------------------------------------------------------------ the ballot

    private static final int[] CANDIDATE_SLOTS = {20, 22, 24};

    public void open(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        LocalDate day = today();
        boolean open = voting(day);
        // While voting is closed, the ballot shows who stands in the next vote.
        YearMonth term = open ? YearMonth.from(day) : YearMonth.from(day).plusMonths(1);
        Holder holder = new Holder(player.getUniqueId(), term);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Wardens' Council · " + term.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + "</gold>"));
        MenuKit.frame(holder.inventory);
        Candidate holderNow = seated();
        String change = Chat.duration(Duration.between(Instant.now(), nextChange()));
        holder.inventory.setItem(4, MenuKit.item(Material.BELL, "<gold>Wardens' Council</gold>", List.of(
            holderNow == null ? "<gray>The seat is empty.</gray>" : "<gray>Seated:</gray> <white>" + Chat.escape(holderNow.name()) + "</white> <aqua>· " + holderNow.perk().title + "</aqua>",
            holderNow == null ? "" : "<gray>" + holderNow.perk().about + "</gray>",
            open ? "<green>Voting is open: it closes in " + change + ".</green>" : "<gray>The next vote opens in " + change + ". Standing:</gray>",
            "<gray>One vote each; you can change it while voting is open.</gray>")));
        Map<String, Integer> tally = tally(term);
        int total = tally.values().stream().mapToInt(Integer::intValue).sum();
        String mine = votes.getOrDefault(term.toString(), Map.of()).get(account.value());
        List<Candidate> standing = standing(term);
        for (int i = 0; i < standing.size(); i++) {
            Candidate candidate = standing.get(i);
            int count = tally.getOrDefault(candidate.id(), 0);
            List<String> lore = new ArrayList<>();
            lore.add("<aqua>" + candidate.perk().title + "</aqua>");
            lore.add("<gray>" + candidate.perk().about + "</gray>");
            lore.add("<gray><i>" + Chat.escape(candidate.pitch()) + "</i></gray>");
            lore.add("<gray>Votes:</gray> <white>" + count + "</white>" + (total > 0 ? " <dark_gray>(" + Math.round(count * 100.0 / total) + "%)</dark_gray>" : ""));
            lore.add(candidate.id().equals(mine) ? "<green>✔ Your vote</green>" : open ? "<yellow>Click to vote.</yellow>" : "<dark_gray>Voting is closed.</dark_gray>");
            var icon = MenuKit.item(candidate.icon(), (candidate.id().equals(mine) ? "<green>" : "<white>") + Chat.escape(candidate.name()) + (candidate.id().equals(mine) ? "</green>" : "</white>"), lore);
            if (candidate.id().equals(mine)) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            holder.inventory.setItem(CANDIDATE_SLOTS[i], icon);
        }
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private void vote(Player player, YearMonth term, Candidate candidate) {
        if (!voting(today()) || !YearMonth.from(today()).equals(term)) { Chat.error(player, "Voting is closed."); return; }
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        String before = votes.computeIfAbsent(term.toString(), ignored -> new HashMap<>()).put(account.value(), candidate.id());
        save();
        Chat.success(player, (before == null ? "You voted for " : "You changed your vote to ") + "<white>" + Chat.escape(candidate.name()) + "</white>.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.4F);
        open(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            List<Candidate> standing = standing(holder.term);
            for (int i = 0; i < CANDIDATE_SLOTS.length; i++) if (CANDIDATE_SLOTS[i] == slot) { vote(player, holder.term, standing.get(i)); return; }
            if (slot == 49) player.closeInventory();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    public JsonObject json() {
        JsonObject out = new JsonObject();
        LocalDate day = today();
        YearMonth term = YearMonth.from(day);
        Candidate holder = seated();
        if (holder != null) {
            JsonObject seat = new JsonObject();
            seat.addProperty("name", holder.name());
            seat.addProperty("perk", holder.perk().title);
            seat.addProperty("about", holder.perk().about);
            out.add("seated", seat);
        }
        out.addProperty("voting", voting(day));
        out.addProperty("changesAt", nextChange().toString());
        JsonArray standing = new JsonArray();
        Map<String, Integer> tally = tally(term);
        for (Candidate candidate : standing(term)) {
            JsonObject row = new JsonObject();
            row.addProperty("name", candidate.name());
            row.addProperty("perk", candidate.perk().title);
            row.addProperty("about", candidate.perk().about);
            row.addProperty("votes", tally.getOrDefault(candidate.id(), 0));
            standing.add(row);
        }
        out.add("candidates", standing);
        return out;
    }

    @Override public void close() { if (task != null) task.cancel(); }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; final YearMonth term; Inventory inventory;
        Holder(UUID viewer, YearMonth term) { this.viewer = viewer; this.term = term; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
