package net.nerdsmp.paper.skyblock;

import net.kyori.adventure.bossbar.BossBar;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Weekend farming contests, after Hypixel's Jacob's contests. While the Double XP Weekend runs, a contest opens every
 * couple of hours for twenty minutes on three crops. Every ripe crop a player harvests on an island counts for that
 * crop; at the end each crop's farmers earn bronze, silver or gold medals by how much they brought in (the top farmer of
 * each crop goes up a medal). Medals buy farming gear at the Contest Stand.
 */
public final class FarmingContests implements Listener, AutoCloseable {
    public enum Medal {
        BRONZE("Bronze", "#cd7f32", 150), SILVER("Silver", "#c0c0c0", 400), GOLD("Gold", "#ffd700", 800);
        final String title, color; final int need;
        Medal(String title, String color, int need) { this.title = title; this.color = color; this.need = need; }
        public String paint(String text) { return "<" + color + ">" + text + "</" + color + ">"; }
        /** How many of this crop earn the medal: wheat-pace targets scaled by how fast the crop comes in. */
        int need(IslandFarmstead.Crop crop) { return (int) Math.max(10, Math.round(need * crop.pace())); }
    }

    /** The medal a count earns for a crop, or null; a crop's top farmer goes up one medal. */
    static Medal medal(IslandFarmstead.Crop crop, long count, boolean top) { return medal(crop, count, top, 1.0); }

    /** {@code factor} scales the targets (0.8 while the Harvest Contests councillor is seated). */
    static Medal medal(IslandFarmstead.Crop crop, long count, boolean top, double factor) {
        Medal earned = null;
        for (Medal medal : Medal.values()) if (count >= Math.round(medal.need(crop) * factor)) earned = medal;
        if (earned != null && top && earned != Medal.GOLD) earned = Medal.values()[earned.ordinal() + 1];
        return earned;
    }

    /** The three crops of the contest that starts at this time, decided by the time alone. */
    static List<IslandFarmstead.Crop> crops(long start) {
        List<IslandFarmstead.Crop> all = new ArrayList<>(List.of(IslandFarmstead.Crop.values()));
        java.util.Collections.shuffle(all, new Random(start / 60_000L));
        return List.copyOf(all.subList(0, 3));
    }

    /** The contest slot a time falls in: every {@code everyHours} on the hour, open for {@code minutes}; null between. */
    static Instant slot(Instant now, ZoneId zone, int everyHours, int minutes) {
        ZonedDateTime at = now.atZone(zone);
        if (at.getHour() % Math.max(1, everyHours) != 0 || at.getMinute() >= minutes) return null;
        return at.withMinute(0).withSecond(0).withNano(0).toInstant();
    }

    static final String MEDALS = "farming-contest.medals.";

    private record Contest(Instant start, Instant end, List<IslandFarmstead.Crop> crops, Map<AccountId, Map<IslandFarmstead.Crop, Long>> counts,
                           Map<AccountId, String> names) { }

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final boolean enabled;
    private final int everyHours, minutes;
    private final Map<UUID, BossBar> personal = new HashMap<>();
    private Contest active;
    private Instant lastSlot;
    private BukkitTask task;

    FarmingContests(NerdSmpPlugin plugin, SkyblockController islands) {
        this.plugin = plugin;
        this.islands = islands;
        this.enabled = plugin.getConfig().getBoolean("events.farming-contest.enabled", true);
        this.everyHours = Math.max(1, plugin.getConfig().getInt("events.farming-contest.every-hours", 2));
        this.minutes = Math.clamp(plugin.getConfig().getInt("events.farming-contest.minutes", 20), 5, 59);
    }

    void start() {
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    // ------------------------------------------------------------------ surviving a restart

    private final java.io.File file() { return new java.io.File(plugin.getDataFolder(), "farming-contest.yml"); }
    private boolean dirty;
    private int sinceSave;

    /** Picks a contest back up after a restart, counts and all, if it hasn't ended. */
    private void load() {
        java.io.File file = file();
        if (!file.isFile()) return;
        try {
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            Instant start = Instant.ofEpochMilli(yaml.getLong("start")), end = Instant.ofEpochMilli(yaml.getLong("end"));
            lastSlot = start;
            if (!Instant.now().isBefore(end)) { file.delete(); return; }
            List<IslandFarmstead.Crop> crops = yaml.getStringList("crops").stream().map(IslandFarmstead.Crop::valueOf).toList();
            Map<AccountId, Map<IslandFarmstead.Crop, Long>> counts = new HashMap<>();
            Map<AccountId, String> names = new HashMap<>();
            var section = yaml.getConfigurationSection("counts");
            if (section != null) for (String id : section.getKeys(false)) {
                AccountId account = new AccountId(UUID.fromString(id));
                Map<IslandFarmstead.Crop, Long> mine = new EnumMap<>(IslandFarmstead.Crop.class);
                for (IslandFarmstead.Crop crop : crops) { long n = section.getLong(id + "." + crop.name(), 0); if (n > 0) mine.put(crop, n); }
                counts.put(account, mine);
                names.put(account, section.getString(id + ".name", "?"));
            }
            active = new Contest(start, end, crops, counts, names);
            plugin.getLogger().info("Farming contest resumed: " + counts.size() + " farmers, ends " + end);
        } catch (RuntimeException unreadable) {
            plugin.getLogger().warning("The saved farming contest couldn't be read; it's dropped: " + unreadable);
        }
    }

    private void save() {
        dirty = false;
        sinceSave = 0;
        java.io.File file = file();
        if (active == null) { file.delete(); return; }
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("start", active.start().toEpochMilli());
        yaml.set("end", active.end().toEpochMilli());
        yaml.set("crops", active.crops().stream().map(Enum::name).toList());
        active.counts().forEach((account, mine) -> {
            String key = "counts." + account.value();
            yaml.set(key + ".name", active.names().getOrDefault(account, "?"));
            mine.forEach((crop, n) -> yaml.set(key + "." + crop.name(), n));
        });
        try { yaml.save(file); } catch (java.io.IOException failure) { plugin.getLogger().warning("Could not save the farming contest: " + failure); }
    }

    private double factor() { return plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.HARVEST_CONTESTS) ? 0.8 : 1.0; }

    private ZoneId zone() { return plugin.boosters() == null ? ZoneId.of("America/Denver") : plugin.boosters().weekend().zone(); }
    private boolean weekend(Instant now) { return plugin.boosters() != null && plugin.boosters().weekend().active(now); }

    private void tick() {
        Instant now = Instant.now();
        if (dirty && ++sinceSave >= 15) save();
        if (active != null) {
            if (!now.isBefore(active.end())) finish();
            else show(now);
            return;
        }
        if (!enabled || !weekend(now)) return;
        Instant slot = slot(now, zone(), plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.HARVEST_CONTESTS) ? 1 : everyHours, minutes);
        if (slot != null && !slot.equals(lastSlot)) begin(slot, slot.plus(Duration.ofMinutes(minutes)));
    }

    /** Starts a contest now (staff), or at a scheduled slot. */
    void begin(Instant start, Instant end) {
        if (active != null) return;
        lastSlot = start;
        active = new Contest(start, end, crops(start.toEpochMilli()), new HashMap<>(), new HashMap<>());
        save();
        String names = String.join(", ", active.crops().stream().map(IslandFarmstead.Crop::title).toList());
        for (Player player : Bukkit.getOnlinePlayers()) {
            Chat.send(player, "<yellow>🌾 A farming contest has begun!</yellow> <white>" + names + "</white> <gray>for " + Duration.between(start, end).toMinutes()
                + " minutes. Harvest ripe crops on your island to earn medals.</gray> " + Chat.command("island contest"));
            ClientFeeds.toast(player, "quest", Chat.render("<yellow>Farming Contest</yellow>"), Chat.render("<white>" + names + "</white>"));
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.4F, 1.4F);
        }
    }

    void stop() { if (active != null) finish(); }

    /** A ripe crop harvested on an island: counts if it's one of this contest's crops. */
    void harvest(Player player, AccountId account, IslandFarmstead.Crop crop) {
        if (active == null || !active.crops().contains(crop) || Instant.now().isAfter(active.end())) return;
        long count = active.counts().computeIfAbsent(account, ignored -> new EnumMap<>(IslandFarmstead.Crop.class)).merge(crop, 1L, Long::sum);
        active.names().put(account, player.getName());
        dirty = true;
        Medal now = medal(crop, count, false, factor()), before = medal(crop, count - 1, false, factor());
        if (now != null && now != before) {
            Chat.send(player, "<yellow>🌾</yellow> " + now.paint(now.title + " medal pace") + " <gray>in " + crop.title() + ": " + count + " harvested.</gray>");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7F, now == Medal.GOLD ? 2F : 1.5F);
        }
    }

    private void show(Instant now) {
        Duration left = Duration.between(now, active.end());
        float progress = (float) Math.clamp(left.toMillis() / (double) Duration.between(active.start(), active.end()).toMillis(), 0, 1);
        String crops = String.join(" · ", active.crops().stream().map(IslandFarmstead.Crop::title).toList());
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar mine = personal.computeIfAbsent(player.getUniqueId(), ignored -> BossBar.bossBar(Chat.render(""), 1F, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
            AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
            Map<IslandFarmstead.Crop, Long> counts = account == null ? Map.of() : active.counts().getOrDefault(account, Map.of());
            String best = counts.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> { Medal m = medal(e.getKey(), e.getValue(), false); return " <gray>· You:</gray> <white>" + e.getValue() + " " + e.getKey().title() + "</white>"
                    + (m == null ? "" : " " + m.paint("(" + m.title + ")")); }).orElse("");
            mine.name(Chat.render("<yellow>🌾 Farming Contest</yellow> <white>" + crops + "</white> <gray>· " + Chat.duration(left) + " left</gray>" + best));
            mine.progress(progress);
            if (islands.isIslandWorld(player.getWorld())) player.showBossBar(mine); else player.hideBossBar(mine);
        }
    }

    private void finish() {
        Contest contest = active;
        active = null;
        save();
        personal.forEach((id, mine) -> { Player p = Bukkit.getPlayer(id); if (p != null) p.hideBossBar(mine); });
        personal.clear();
        Map<AccountId, List<String>> results = new LinkedHashMap<>();
        for (IslandFarmstead.Crop crop : contest.crops()) {
            List<Map.Entry<AccountId, Long>> ranked = contest.counts().entrySet().stream()
                .filter(e -> e.getValue().getOrDefault(crop, 0L) > 0)
                .map(e -> Map.entry(e.getKey(), e.getValue().get(crop)))
                .sorted(Map.Entry.<AccountId, Long>comparingByValue().reversed()).toList();
            for (int i = 0; i < ranked.size(); i++) {
                AccountId account = ranked.get(i).getKey();
                long count = ranked.get(i).getValue();
                Medal medal = medal(crop, count, i == 0, factor());
                String line = "<white>" + crop.title() + "</white> <gray>#" + (i + 1) + " with " + count + "</gray> "
                    + (medal == null ? "<dark_gray>no medal (" + Medal.BRONZE.need(crop) + " for bronze)</dark_gray>" : medal.paint("<b>" + medal.title.toUpperCase(Locale.ROOT) + "</b>"));
                results.computeIfAbsent(account, ignored -> new ArrayList<>()).add(line);
                if (medal != null) plugin.playerData().update(account, yaml -> {
                    yaml.set(MEDALS + medal.name().toLowerCase(Locale.ROOT), yaml.getInt(MEDALS + medal.name().toLowerCase(Locale.ROOT), 0) + 1);
                    yaml.set("farming-contest.earned", yaml.getInt("farming-contest.earned", 0) + 1);
                });
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
            List<String> mine = account == null ? null : results.get(account);
            if (mine == null) { Chat.send(player, "<yellow>🌾 The farming contest is over.</yellow>"); continue; }
            Chat.send(player, "<yellow>🌾 The farming contest is over! Your results:</yellow>");
            mine.forEach(line -> Chat.send(player, "  " + line));
            Chat.send(player, "<gray>Spend medals at the Contest Stand:</gray> " + Chat.command("island contest"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
        }
    }

    // ------------------------------------------------------------------ medals and the Contest Stand

    public int medals(AccountId account, Medal medal) { return plugin.playerData().read(account, yaml -> yaml.getInt(MEDALS + medal.name().toLowerCase(Locale.ROOT), 0)); }
    /** Every medal ever earned, spent or not: what opens the contest judge's visit. */
    public int medalCount(AccountId account) { return plugin.playerData().read(account, yaml -> yaml.getInt("farming-contest.earned", 0)); }

    private boolean spend(AccountId account, Medal medal, int amount) {
        boolean[] spent = {false};
        String key = MEDALS + medal.name().toLowerCase(Locale.ROOT);
        plugin.playerData().update(account, yaml -> {
            int have = yaml.getInt(key, 0);
            if (have < amount) return;
            yaml.set(key, have - amount);
            spent[0] = true;
        });
        return spent[0];
    }

    /** One line for menus: the contest running now, or when the next one is. */
    public String summary() {
        if (active != null) return "<green>On now:</green> <white>" + String.join(", ", active.crops().stream().map(IslandFarmstead.Crop::title).toList())
            + "</white> <gray>(" + Chat.duration(Duration.between(Instant.now(), active.end())) + " left)</gray>";
        if (!enabled) return "<gray>Contests are off on this server.</gray>";
        Instant now = Instant.now();
        if (weekend(now)) return "<gray>Next contest in</gray> <white>" + Chat.duration(untilNext(now)) + "</white>";
        return plugin.boosters() == null ? "<gray>Held during Double XP Weekends.</gray>"
            : plugin.boosters().weekend().next(now).map(w -> "<gray>Next during the weekend, in</gray> <white>" + Chat.duration(Duration.between(now, w.start())) + "</white>")
                .orElse("<gray>Held during Double XP Weekends.</gray>");
    }

    private Duration untilNext(Instant now) {
        ZonedDateTime at = now.atZone(zone()).withMinute(0).withSecond(0).withNano(0).plusHours(1);
        while (at.getHour() % everyHours != 0) at = at.plusHours(1);
        return Duration.between(now, at.toInstant());
    }

    /** What medals buy: a catalog item (or Compost, Favour, a booster) for so many medals of one kind. */
    private record Offer(String title, Material icon, String item, Medal medal, int cost, String about) { }
    private static final List<Offer> OFFERS = List.of(
        new Offer("Compost ×16", Material.BONE_MEAL, "compost", Medal.BRONZE, 2, "Ripens a 5×5 patch of crops each."),
        new Offer("Furrowkeeper Hoe", Material.IRON_HOE, "furrowkeeper_hoe", Medal.BRONZE, 6, "A sturdy hoe for long rows."),
        new Offer("250 Favour", Material.SUNFLOWER, "favour", Medal.SILVER, 1, "Spend it at the Visitor's Stall."),
        new Offer("Copperfield Hoe", Material.IRON_HOE, "copperfield_hoe", Medal.SILVER, 3, "Copperfield set farming hoe."),
        new Offer("Wildwood Sickle", Material.IRON_HOE, "wildwood_sickle", Medal.SILVER, 5, "Wildwood set sickle."),
        new Offer("Verdant Sickle", Material.IRON_HOE, "verdant_sickle", Medal.GOLD, 3, "A relic of the Verdant Oath."),
        new Offer("XP Booster", Material.EXPERIENCE_BOTTLE, "booster", Medal.GOLD, 2, "+50% XP for everyone online, for an hour."));
    private static final int[] OFFER_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    void open(Player player, AccountId owner) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        Holder holder = new Holder(player.getUniqueId(), owner);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<yellow>Farming Contests</yellow>"));
        MenuKit.frame(holder.inventory);
        List<String> about = new ArrayList<>();
        about.add(summary());
        about.add("<gray>Twenty minutes, three crops, every " + everyHours + " hours</gray>");
        about.add("<gray>through the Double XP Weekend.</gray>");
        if (active != null) {
            Map<IslandFarmstead.Crop, Long> mine = active.counts().getOrDefault(account, Map.of());
            for (IslandFarmstead.Crop crop : active.crops()) {
                long count = mine.getOrDefault(crop, 0L);
                Medal medal = medal(crop, count, false);
                about.add("<white>" + crop.title() + ":</white> <white>" + count + "</white> " + (medal == null ? "<dark_gray>(bronze at " + Medal.BRONZE.need(crop) + ")</dark_gray>" : medal.paint(medal.title)));
            }
        }
        holder.inventory.setItem(4, MenuKit.item(Material.CLOCK, "<yellow>Farming Contests</yellow>", about));
        int slot = 11;
        for (Medal medal : Medal.values()) {
            holder.inventory.setItem(slot, MenuKit.item(medal == Medal.GOLD ? Material.GOLD_INGOT : medal == Medal.SILVER ? Material.IRON_INGOT : Material.COPPER_INGOT,
                medal.paint(medal.title + " medals: " + medals(account, medal)), List.of(
                    "<gray>Wheat-pace target:</gray> <white>" + medal.need + "</white>",
                    "<gray>Faster or slower crops scale it.</gray>",
                    "<gray>A crop's top farmer goes up a medal.</gray>")));
            slot += 2;
        }
        holder.inventory.setItem(19, MenuKit.item(Material.WHEAT, "<white>Medal targets</white>", targets()));
        for (int i = 0; i < OFFERS.size(); i++) {
            Offer offer = OFFERS.get(i);
            boolean afford = medals(account, offer.medal()) >= offer.cost();
            holder.inventory.setItem(OFFER_SLOTS[i], MenuKit.item(offer.icon(), "<white>" + offer.title() + "</white>", List.of(
                offer.medal().paint(offer.cost() + " " + offer.medal().title + " medal" + (offer.cost() == 1 ? "" : "s")),
                "<gray>" + offer.about() + "</gray>", afford ? "<yellow>Click to buy.</yellow>" : "<gray>Not enough medals yet.</gray>")));
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Farmstead</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private static List<String> targets() {
        List<String> lines = new ArrayList<>();
        for (IslandFarmstead.Crop crop : IslandFarmstead.Crop.values())
            lines.add("<white>" + crop.title() + ":</white> " + Medal.BRONZE.paint(String.valueOf(Medal.BRONZE.need(crop))) + " <dark_gray>/</dark_gray> "
                + Medal.SILVER.paint(String.valueOf(Medal.SILVER.need(crop))) + " <dark_gray>/</dark_gray> " + Medal.GOLD.paint(String.valueOf(Medal.GOLD.need(crop))));
        return lines;
    }

    private void buy(Player player, AccountId owner, Offer offer) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        ItemStack made = null;
        if (!SPECIAL_OFFERS.contains(offer.item())) {
            try { made = plugin.items().create(new ItemId(offer.item())); } catch (RuntimeException missing) { made = null; }
            if (made == null || made.getType().isAir()) { Chat.error(player, "That isn't available on this server."); return; }
        }
        if (!spend(account, offer.medal(), offer.cost())) { Chat.error(player, "That needs " + offer.cost() + " " + offer.medal().title + " medals."); return; }
        switch (offer.item()) {
            case "compost" -> { made = IslandComposter.compostItem(plugin); made.setAmount(16); }
            case "favour" -> { if (islands.guests() != null) islands.guests().addFavour(account, 250); }
            case "booster" -> { if (plugin.boosters() != null) plugin.boosters().earn(player, "contest:" + UUID.randomUUID()); }
            default -> { }
        }
        if (made != null) player.getInventory().addItem(made).values().forEach(spill -> player.getWorld().dropItemNaturally(player.getLocation(), spill));
        Chat.success(player, "Bought <white>" + offer.title() + "</white>.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.7F, 1.1F);
        open(player, owner);
    }

    private static final java.util.Set<String> SPECIAL_OFFERS = java.util.Set.of("compost", "favour", "booster");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                for (int i = 0; i < OFFERS.size(); i++) if (OFFER_SLOTS[i] == slot) { buy(player, holder.owner, OFFERS.get(i)); return; }
                if (slot == 45) islands.farmstead().openHub(player, holder.owner);
                else if (slot == 49) player.closeInventory();
            } catch (IllegalArgumentException refused) { Chat.error(player, Chat.escape(refused.getMessage())); }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    @Override public void close() {
        if (task != null) task.cancel();
        if (active != null) save();
        personal.forEach((id, mine) -> { Player p = Bukkit.getPlayer(id); if (p != null) p.hideBossBar(mine); });
        personal.clear();
    }

    private static final class Holder implements InventoryHolder {
        final UUID viewer; final AccountId owner; Inventory inventory;
        Holder(UUID viewer, AccountId owner) { this.viewer = viewer; this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
