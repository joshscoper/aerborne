package net.nerdsmp.paper.vote;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The {@code /vote} menu: a tile for each site in votes.yml showing whether the player can vote there now, what
 * voting pays, their own record and the month's top voters. A menu can't open a web page, so clicking a site puts
 * its vote link in chat, ready to click.
 */
final class VoteMenu implements Listener {
    /** Where sites can go: the three middle rows, inside the frame. */
    static final int[] SITE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int SIZE = 45;
    private static final int GOAL = 4;
    private static final int REWARDS = 38;
    private static final int RECORD = 39;
    private static final int LEADERS = 41;

    private final NerdSmpPlugin plugin;
    private final VoteService votes;

    VoteMenu(NerdSmpPlugin plugin, VoteService votes) {
        this.plugin = plugin;
        this.votes = votes;
    }

    static boolean siteSlot(int slot) {
        for (int candidate : SITE_SLOTS) if (candidate == slot) return true;
        return false;
    }

    /** Sites without a slot of their own fill the middle row from its centre, then the rows above and below. */
    static Map<VoteService.Site, Integer> layout(List<VoteService.Site> sites) {
        Map<VoteService.Site, Integer> placed = new HashMap<>();
        List<Integer> free = new ArrayList<>();
        int loose = (int) sites.stream().filter(site -> site.slot() == -1).count();
        List<Integer> order = new ArrayList<>();
        if (loose <= 7) for (int i = 0; i < loose; i++) order.add(19 + (7 - loose) / 2 + i);
        for (int slot : new int[]{19, 20, 21, 22, 23, 24, 25, 10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34}) if (!order.contains(slot)) order.add(slot);
        for (VoteService.Site site : sites) if (site.slot() != -1) placed.put(site, site.slot());
        for (int slot : order) if (!placed.containsValue(slot)) free.add(slot);
        int next = 0;
        for (VoteService.Site site : sites) if (site.slot() == -1) placed.put(site, free.get(next++));
        return placed;
    }

    void open(Player player) {
        VoteService.Settings settings = votes.settings();
        Board board = new Board(player.getUniqueId());
        board.inventory = Bukkit.createInventory(board, SIZE, Chat.render(settings.title()));
        MenuKit.frame(board.inventory);
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        long now = System.currentTimeMillis();
        VoteLedger.Rules rules = settings.rules();

        List<String> rewards = new ArrayList<>(List.of("<gray>Every vote pays</gray> " + Chat.marksTag(rules.marksPerVote())));
        if (rules.streakBonus() > 0) rewards.add("<gray>+" + Chat.marks(rules.streakBonus()) + " for each day in a row, up to " + rules.streakCap() + " days</gray>");
        if (rules.siteBonus() > 0) rewards.add("<gray>+" + Chat.marks(rules.siteBonus()) + " more for each extra site the same day</gray>");
        if (rules.allSitesBonus() > 0 && settings.sites().size() >= 2) {
            rewards.add("<gray>Vote on all " + settings.sites().size() + " sites in a day:</gray> " + Chat.marksTag(rules.allSitesBonus()) + " <gray>bonus</gray>");
        }
        if (rules.boosterEvery() > 0) rewards.add("<gray>Every " + rules.boosterEvery() + " votes earn an</gray> <gold>XP booster</gold>");
        rewards.add("");
        rewards.add("<gray>Each site takes one vote a day.</gray>");
        rewards.add("<gray>Vote on all of them, every day!</gray>");
        board.set(REWARDS, MenuKit.item(Material.EMERALD, "<green><bold>Vote rewards</bold></green>", rewards), who -> { });
        if (settings.goal().daily() > 0) board.set(GOAL, goalTile(settings.goal(), votes.goalToday()), who -> { });

        if (settings.sites().isEmpty()) {
            board.set(22, MenuKit.item(Material.BARRIER, "<gray>No vote sites yet</gray>",
                List.of("<dark_gray>Staff add them in votes.yml.</dark_gray>")), who -> { });
        }
        Map<VoteService.Site, Integer> layout = layout(settings.sites());
        for (VoteService.Site site : settings.sites()) {
            long next = account == null ? 0L : plugin.playerData().read(account, data -> VoteLedger.nextVote(data, site.service(), now));
            List<String> lore = new ArrayList<>(wrap(site.description()));
            if (!lore.isEmpty()) lore.add("");
            lore.add(next == 0 ? "<green>✔ You can vote here now</green>" : "<gray>Voted · again in <white>" + Chat.duration(Duration.ofMillis(next - now)) + "</white></gray>");
            ItemStack tile = MenuKit.item(icon(site.icon()), (next == 0 ? "<aqua><bold>" : "<gray>") + Chat.escape(site.name()) + (next == 0 ? "</bold></aqua>" : "</gray>"),
                MenuKit.withHint(lore, "get the vote link"));
            if (next == 0) {
                ItemMeta meta = tile.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                tile.setItemMeta(meta);
            }
            board.set(layout.get(site), tile, who -> {
                who.closeInventory();
                Chat.send(who, "<gold>✦</gold> <click:open_url:'" + site.url() + "'><hover:show_text:'<gray>" + Chat.escape(site.url()) + "</gray>'><aqua><u>Click here to vote on "
                    + Chat.escape(site.name()) + "</u></aqua></hover></click> <gray>Your reward arrives a moment after you vote.</gray>");
            });
        }

        if (account != null) {
            int total = plugin.playerData().read(account, data -> data.getInt("votes.total"));
            int streak = plugin.playerData().read(account, data -> VoteLedger.streak(data, now, votes.zone()));
            List<String> record = new ArrayList<>(List.of(
                "<gray>Votes in all:</gray> <white>" + total + "</white>",
                "<gray>This month:</gray> <white>" + votes.monthVotes(player.getName()) + "</white>",
                "<gray>Streak:</gray> <white>" + streak + " day" + (streak == 1 ? "" : "s") + "</white>"));
            if (rules.boosterEvery() > 0) record.add("<gray>Next XP booster in</gray> <white>" + (rules.boosterEvery() - total % rules.boosterEvery()) + "</white> <gray>votes</gray>");
            board.set(RECORD, MenuKit.item(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Your votes</bold></yellow>", record), who -> { });
        }
        List<String> leaders = new ArrayList<>();
        int place = 1;
        for (Map.Entry<String, Integer> entry : votes.leaders(5)) {
            leaders.add("<gold>" + place++ + ".</gold> <white>" + Chat.escape(entry.getKey()) + "</white> <gray>" + entry.getValue() + "</gray>");
        }
        if (leaders.isEmpty()) leaders.add("<gray>No votes yet this month.</gray>");
        board.set(LEADERS, MenuKit.item(Material.GOLDEN_HELMET, "<gold><bold>Top voters this month</bold></gold>", MenuKit.withHint(leaders, "see the top 10")),
            who -> { who.closeInventory(); votes.top(who); });
        board.set(MenuKit.closeSlot(SIZE), MenuKit.close(), Player::closeInventory);
        player.openInventory(board.inventory);
    }

    /** Today's community goal, with a bar that fills as the server votes. */
    static ItemStack goalTile(VoteService.Goal goal, int[] today) {
        int filled = Math.min(20, today[0] * 20 / Math.max(1, today[1]));
        boolean reached = today[2] == 1;
        List<String> lore = new ArrayList<>();
        lore.add("<green>" + "|".repeat(filled) + "</green><dark_gray>" + "|".repeat(20 - filled) + "</dark_gray> <white>" + today[0] + "/" + today[1] + "</white>");
        lore.add("");
        if (reached) {
            lore.add("<green>Reached! Everyone who voted today gets</green>");
            lore.add(Chat.marksTag(goal.voterMarks()) + "<green>, and so does anyone who votes</green>");
            lore.add("<green>before midnight.</green>");
        } else {
            lore.add("<gray>Votes from everyone today count.</gray>");
            lore.add("<gray>Reach it and everyone who voted gets</gray> " + Chat.marksTag(goal.voterMarks()));
            if (goal.booster()) lore.add("<gray>and everyone online an hour of</gray> <white>+50% XP</white><gray>.</gray>");
            lore.add("<dark_gray>Starts again at midnight.</dark_gray>");
        }
        ItemStack tile = MenuKit.item(Material.NETHER_STAR, reached ? "<green><bold>Today's vote goal: reached!</bold></green>" : "<aqua><bold>Today's vote goal</bold></aqua>", lore);
        if (reached) {
            ItemMeta meta = tile.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            tile.setItemMeta(meta);
        }
        return tile;
    }

    private static Material icon(String key) {
        Material material = Material.matchMaterial(key);
        return material != null && material.isItem() && !material.isAir() ? material : Material.PAPER;
    }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.length() + word.length() + 1 > 34 && !line.isEmpty()) { lines.add("<gray>" + Chat.escape(line.toString()) + "</gray>"); line.setLength(0); }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add("<gray>" + Chat.escape(line.toString()) + "</gray>");
        return lines;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Board board)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !board.owner.equals(player.getUniqueId()) || !event.isLeftClick()) return;
        Consumer<Player> action = board.actions.get(event.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != board.inventory) return;
            MenuKit.clickSound(player);
            action.accept(player);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Board) event.setCancelled(true);
    }

    private static final class Board implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Inventory inventory;

        private Board(UUID owner) { this.owner = owner; }

        void set(int slot, ItemStack item, Consumer<Player> action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
