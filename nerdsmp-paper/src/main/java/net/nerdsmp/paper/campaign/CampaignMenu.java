package net.nerdsmp.paper.campaign;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.campaign.season.SeasonDefinition;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Goal;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Milestone;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Source;
import net.nerdsmp.paper.campaign.season.SeasonLedger;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.VisualFormat;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** The /campaign board: goal progress, milestones and their rewards, running buffs and the claim button. */
final class CampaignMenu implements Listener {
    private static final int[] GOALS = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] MILESTONES = {28, 29, 30, 31, 32, 33, 34};
    private final NerdSmpPlugin plugin;
    private final SeasonCampaignService campaign;

    CampaignMenu(NerdSmpPlugin plugin, SeasonCampaignService campaign) {
        this.plugin = plugin;
        this.campaign = campaign;
    }

    void open(Player player) {
        Board board = new Board(player.getUniqueId());
        board.inventory = Bukkit.createInventory(board, 54, Chat.render("<gradient:#C59AFA:#8CE8ED><bold>Campaign</bold></gradient>"));
        MenuKit.frame(board.inventory);
        Optional<SeasonDefinition> loaded = campaign.season();
        board.set(MenuKit.backSlot(54), MenuKit.back("Adventure"), who -> plugin.menus().open(who, MenuManager.View.ADVENTURE));
        board.set(MenuKit.closeSlot(54), MenuKit.close(), Player::closeInventory);
        if (loaded.isEmpty()) {
            board.set(22, MenuKit.item(Material.ENDER_EYE, "<gray>No season running</gray>", List.of("<dark_gray>The next season has not started.</dark_gray>")), who -> { });
            player.openInventory(board.inventory);
            return;
        }
        SeasonDefinition season = loaded.get();
        SeasonLedger ledger = campaign.ledger();
        Instant now = Instant.now();
        String account = plugin.sessions().account(player.getUniqueId()).map(AccountId::toString).orElse("");
        List<String> header = new ArrayList<>(wrap(season.description()));
        if (!header.isEmpty()) header.add("");
        header.add(season.active(now) ? "<gray>Ends in</gray> <white>" + Chat.duration(Duration.between(now, season.endsAt()).withNanos(0)) + "</white>"
            : now.isBefore(season.startsAt()) ? "<gray>Starts</gray> <white>" + season.startsAt() + "</white>" : "<gray>This season has ended.</gray>");
        header.add("<gray>Milestones reached</gray> <white>" + ledger.reachedMilestones().size() + "/" + season.milestones().size() + "</white>");
        board.set(4, MenuKit.item(Material.BEACON, "<gradient:#C59AFA:#8CE8ED>" + Chat.escape(season.title()) + "</gradient>", header), who -> { });

        int index = 0;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (Goal goal : season.goals().values()) {
            if (index >= GOALS.length) break;
            long points = ledger.points(goal.id());
            long cap = goal.dailyCap() != null ? goal.dailyCap() : season.playerDailyCap();
            List<String> lore = new ArrayList<>(wrap(goal.description()));
            if (!lore.isEmpty()) lore.add("");
            lore.add(VisualFormat.progressBar(points, goal.target(), 14) + " <white>" + VisualFormat.compact(points) + "/" + VisualFormat.compact(goal.target()) + "</white>");
            lore.add("<gray>You've added</gray> <white>" + VisualFormat.compact(ledger.contributed(account, goal.id())) + "</white> <dark_gray>(today "
                + VisualFormat.compact(ledger.today(account, goal.id(), today)) + "/" + VisualFormat.compact(cap) + ")</dark_gray>");
            lore.add("");
            lore.add("<yellow>How to help:</yellow>");
            for (Source source : goal.sources()) lore.add("<gray>• " + Chat.escape(describe(source)) + "</gray>");
            board.set(GOALS[index++], MenuKit.item(material(goal.icon(), Material.BEACON), "<aqua>" + Chat.escape(goal.title()) + "</aqua>", lore), who -> { });
        }
        index = 0;
        for (Milestone milestone : season.milestones().values()) {
            if (index >= MILESTONES.length) break;
            boolean reached = ledger.reached(milestone.id());
            List<String> lore = new ArrayList<>(wrap(milestone.description()));
            if (!lore.isEmpty()) lore.add("");
            milestone.requires().forEach((goalId, amount) -> {
                Goal goal = season.goals().get(goalId);
                long points = ledger.points(goalId);
                lore.add((points >= amount ? "<green>✔ " : "<gray>○ ") + Chat.escape(goal == null ? goalId : goal.title()) + " "
                    + VisualFormat.compact(Math.min(points, amount)) + "/" + VisualFormat.compact(amount) + (points >= amount ? "</green>" : "</gray>"));
            });
            lore.add("");
            lore.add("<yellow>Rewards for everyone:</yellow>");
            for (Reward reward : milestone.rewards()) describe(reward).ifPresent(line -> lore.add("<gray>• " + line + "</gray>"));
            if (reached && milestone.claimable())
                lore.add(ledger.claimed(account, milestone.id()) ? "<dark_green>Claimed</dark_green>" : "<gold>Ready to claim!</gold>");
            board.set(MILESTONES[index++], MenuKit.item(reached ? Material.LIME_STAINED_GLASS : material(milestone.icon(), Material.NETHER_STAR),
                (reached ? "<green>✔ " : "<gold>") + Chat.escape(milestone.title()) + (reached ? "</green>" : "</gold>"), lore), who -> { });
        }
        List<String> active = new ArrayList<>();
        for (SeasonLedger.ActiveBuff buff : ledger.activeBuffs(now))
            active.add("<white>" + Chat.escape(campaign.buffName(buff)) + "</white> <gray>" + Chat.duration(SeasonLedger.remaining(buff, now).withNanos(0)) + " left</gray>");
        if (active.isEmpty()) active.add("<gray>No server-wide buffs right now.</gray>");
        board.set(40, MenuKit.item(Material.POTION, "<light_purple>Server buffs</light_purple>", active), who -> { });
        int claimable = ledger.claimable(season, account).size();
        board.set(50, MenuKit.item(claimable > 0 ? Material.CHEST : Material.ENDER_CHEST,
            claimable > 0 ? "<gold>Claim " + claimable + " reward" + (claimable == 1 ? "" : "s") + "</gold>" : "<gray>Nothing to claim</gray>",
            claimable > 0 ? List.of(MenuKit.hint("claim")) : List.of("<gray>Rewards appear here as milestones are reached.</gray>")),
            who -> { if (claimable > 0) { campaign.claim(who); open(who); } });
        board.set(46, MenuKit.item(Material.GOLDEN_HELMET, "<yellow>Top contributors</yellow>", List.of(MenuKit.hint("see the leaderboard"))),
            who -> { who.closeInventory(); campaign.command(who, new String[]{"top"}); });
        player.openInventory(board.inventory);
    }

    private static String describe(Source source) {
        String what = source.filters().isEmpty() ? "anything" : String.join(", ", source.filters().stream().sorted().limit(4)
            .map(value -> Chat.humanize(value.replace("custom:", "").replace("vanilla:", ""))).toList()) + (source.filters().size() > 4 ? "…" : "");
        String points = " (+" + source.points() + ")";
        return switch (source.type()) {
            case MINE -> "Mine " + what + points;
            case SMELT -> "Smelt " + what + points;
            case CRAFT -> "Craft " + what + points;
            case FISH -> "Fish up " + what + points;
            case KILL -> "Defeat " + (source.filters().contains("hostile") && source.filters().size() == 1 ? "hostile mobs" : what) + points;
            case BOSS -> "Defeat bosses" + points;
            case EXPEDITION -> "Clear " + (source.filters().isEmpty() ? "any expedition" : what) + points
                + (source.tierMultipliers().isEmpty() ? "" : ", more on harder tiers");
            case CONTRIBUTE -> "/campaign contribute " + what + points;
        };
    }

    private Optional<String> describe(Reward reward) {
        return Optional.ofNullable(switch (reward) {
            case Reward.Buff buff -> SeasonCampaignService.words(buff.effect()) + (buff.amplifier() > 0 ? " " + (buff.amplifier() + 1) : "") + " for " + Chat.duration(buff.duration());
            case Reward.Boost boost -> "+" + boost.percent() + "% " + boost.kind().label() + " for " + Chat.duration(boost.duration());
            case Reward.Item item -> item.amount() + "× " + Chat.escape(Chat.humanize(item.item())) + " (claim)";
            case Reward.Marks marks -> Chat.marks(marks.amount()) + " (claim)";
            case Reward.Unlock unlock -> unlock.expedition() != null ? "Opens the " + Chat.escape(plugin.expeditions() == null ? unlock.expedition()
                : plugin.expeditions().definition(unlock.expedition()).map(definition -> definition.title()).orElse(unlock.expedition())) + " expedition"
                : "Unlocks " + SeasonCampaignService.words(unlock.feature());
            case Reward.Broadcast broadcast -> null;
            case Reward.Command command -> null;
        });
    }

    private static Material material(String key, Material fallback) {
        Material material = Material.matchMaterial(key);
        return material != null && material.isItem() ? material : fallback;
    }

    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.length() + word.length() + 1 > 38 && !line.isEmpty()) { lines.add("<gray>" + Chat.escape(line.toString()) + "</gray>"); line.setLength(0); }
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
