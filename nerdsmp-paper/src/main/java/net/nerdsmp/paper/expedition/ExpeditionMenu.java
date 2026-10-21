package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Player-facing expedition board: the open expeditions, then a difficulty picker that starts or queues the party. */
public final class ExpeditionMenu implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final NerdSmpPlugin plugin;
    private final ExpeditionService service;

    public ExpeditionMenu(NerdSmpPlugin plugin, ExpeditionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) {
        Board board = new Board(player.getUniqueId());
        board.inventory = Bukkit.createInventory(board, 45, Chat.render("<gold><bold>Expeditions</bold></gold>"));
        MenuKit.frame(board.inventory);
        List<ExpeditionDefinition> open = new java.util.ArrayList<>(service.definitions().stream()
            .filter(definition -> service.playable(definition) || player.hasPermission(ExpeditionService.ADMIN) && definition.published()).toList());
        // Players who haven't cleared the tutorial see it first, marked as the place to start.
        String account = plugin.sessions().account(player.getUniqueId()).map(Object::toString).orElse("");
        boolean newcomer = service.records().clears(account, ExpeditionService.TUTORIAL) == 0;
        if (newcomer) open.sort(java.util.Comparator.comparing(definition -> !definition.id().equals(ExpeditionService.TUTORIAL)));
        for (int index = 0; index < open.size() && index < SLOTS.length; index++) {
            ExpeditionDefinition definition = open.get(index);
            Optional<String> lock = plugin.campaign().expeditionLock(definition.id());
            List<String> lore = summary(player, definition);
            if (newcomer && definition.id().equals(ExpeditionService.TUTORIAL)) {
                lore.add(0, "<green><bold>★ Start here</bold></green> <gray>A short, gentle tour of how expeditions work.</gray>");
                lore.add(1, "");
            }
            if (lock.isPresent()) {
                lore.add("");
                lore.add("<red>Sealed until the campaign reaches</red>");
                lore.add("<red>" + Chat.escape(lock.get()) + ".</red>");
                if (player.hasPermission("nerdsmp.progression.bypass")) lore.add("<gold>Staff can enter anyway.</gold>");
            }
            Material icon = Optional.ofNullable(Material.matchMaterial(definition.icon())).filter(Material::isItem).orElse(Material.COMPASS);
            board.set(SLOTS[index], MenuKit.item(lock.isPresent() && !player.hasPermission("nerdsmp.progression.bypass") ? Material.GRAY_DYE : icon,
                "<gold>" + Chat.escape(definition.title()) + "</gold>", MenuKit.withHint(lore, "choose a difficulty")),
                (who, click) -> openDetail(who, definition));
        }
        if (open.isEmpty()) board.set(22, MenuKit.item(Material.COMPASS, "<gray>No expeditions open yet</gray>",
            List.of("<gray>Check back soon.</gray>")), (who, click) -> { });
        board.set(4, MenuKit.item(Material.BOOK, "<aqua>How expeditions work</aqua>", List.of(
            "<gray>Team up with up to " + (ExpeditionDefinition.MAX_PARTY - 1) + " others (raids: " + (ExpeditionDefinition.MAX_RAID - 1) + ") in a</gray>",
            "<gray>private instance: clear the rooms,</gray>",
            "<gray>beat the boss, and leave with loot.</gray>",
            "",
            "<gray>Party up with</gray> <white>/party invite</white><gray>, or queue</gray>",
            "<gray>solo and the finder fills your group.</gray>",
            "<gray>Deaths never cost your gear, but each</gray>",
            "<gray>one spends a shared party life.</gray>")), (who, click) -> { });
        board.set(42, status(player), (who, click) -> { who.closeInventory(); service.command(who, new String[]{"status"}); });
        board.set(MenuKit.backSlot(45), MenuKit.back("World & Trade"), (who, click) -> plugin.menus().open(who, MenuManager.View.WORLD));
        board.set(MenuKit.closeSlot(45), MenuKit.close(), (who, click) -> who.closeInventory());
        player.openInventory(board.inventory);
    }

    public void openDetail(Player player, ExpeditionDefinition definition) {
        Board board = new Board(player.getUniqueId());
        board.inventory = Bukkit.createInventory(board, 45, Chat.render("<gold><bold>" + Chat.escape(definition.title()) + "</bold></gold>"));
        MenuKit.frame(board.inventory);
        Material icon = Optional.ofNullable(Material.matchMaterial(definition.icon())).filter(Material::isItem).orElse(Material.COMPASS);
        board.set(4, MenuKit.item(icon, "<gold>" + Chat.escape(definition.title()) + "</gold>", summary(player, definition)), (who, click) -> { });
        List<Tier> tiers = service.tiersFor(definition);
        int start = 22 - (Math.min(tiers.size(), 4) - 1); // Centred on the middle row: 22, 21/23, 20/22/24, 19-25.
        for (int index = 0; index < tiers.size() && index < 4; index++) {
            Tier tier = tiers.get(index);
            Optional<String> problem = service.problem(player, definition, tier);
            boolean cleared = service.cleared(player, definition, tier.id());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Enemy level</gray> <white>" + (definition.level() + tier.levelBonus()) + "</white>");
            if (tier.health() != 1 || tier.damage() != 1)
                lore.add("<gray>Enemy health</gray> <white>" + Math.round(tier.health() * 100) + "%</white> <gray>· damage</gray> <white>"
                    + Math.round(tier.damage() * 100) + "%</white>");
            lore.add("<gray>Party lives</gray> <white>" + tier.lives() + "</white> <gray>· loot rolls</gray> <white>" + tier.lootRolls() + "</white>");
            var modifiers = service.modifiers(definition, tier);
            for (Modifier modifier : modifiers) lore.add("<light_purple>" + modifier.title() + ":</light_purple> <gray>" + modifier.description() + "</gray>");
            if (cleared) lore.add("<green>✔ You've cleared this.</green>");
            int waiting = service.waiting(definition.id(), tier.id());
            if (waiting > 0) lore.add("<aqua>" + waiting + " waiting in the finder</aqua>");
            lore.add("");
            if (problem.isPresent()) {
                lore.add("<red>" + Chat.escape(problem.get()) + "</red>");
            } else {
                lore.add("<yellow>Left-click:</yellow> <gray>start with your party</gray>");
                lore.add("<yellow>Right-click:</yellow> <gray>queue and find a group</gray>");
            }
            Material material = switch (index) { case 0 -> Material.IRON_SWORD; case 1 -> Material.DIAMOND_SWORD; case 2 -> Material.NETHERITE_SWORD; default -> Material.TRIDENT; };
            board.set(start + index * 2, MenuKit.item(problem.isPresent() ? Material.GRAY_DYE : material,
                (problem.isPresent() ? "<gray>" : "<gold>") + Chat.escape(tier.title()) + (problem.isPresent() ? "</gray>" : "</gold>"), lore), (who, click) -> {
                    who.closeInventory();
                    service.join(who, definition.id(), tier.id(), click.isRightClick());
                });
        }
        var totals = service.totals(definition.id());
        List<String> records = new ArrayList<>();
        for (Tier tier : tiers) {
            var total = totals.get(tier.id());
            if (total == null || total.clears() == 0) continue;
            records.add("<gray>" + Chat.escape(tier.title()) + ":</gray> <white>" + total.clears() + " clears</white> <gray>· best</gray> <white>"
                + Chat.duration(Duration.ofSeconds(total.bestSeconds())) + "</white>");
            if (!total.bestParty().isEmpty()) records.add("<dark_gray>  " + Chat.escape(String.join(", ", total.bestParty())) + "</dark_gray>");
        }
        if (records.isEmpty()) records.add("<gray>Nobody has cleared it yet.</gray>");
        board.set(42, MenuKit.item(Material.CLOCK, "<aqua>Server records</aqua>", records), (who, click) -> { });
        board.set(38, MenuKit.item(Material.WRITTEN_BOOK, "<gold>Read the guide</gold>", MenuKit.withHint(List.of(
            "<gray>The route, the boss, every mob</gray>", "<gray>and what they can drop.</gray>"), "open the guide")),
            (who, click) -> openGuide(who, definition));
        board.set(MenuKit.backSlot(45), MenuKit.back("Expeditions"), (who, click) -> open(who));
        board.set(MenuKit.closeSlot(45), MenuKit.close(), (who, click) -> who.closeInventory());
        player.openInventory(board.inventory);
    }

    public void openGuide(Player player, ExpeditionDefinition definition) {
        player.closeInventory();
        player.openBook(ExpeditionGuideBook.build(plugin, service, definition));
    }

    private List<String> summary(Player player, ExpeditionDefinition definition) {
        List<String> lore = new ArrayList<>();
        for (String line : wrap(definition.description(), 38)) lore.add("<gray>" + Chat.escape(line) + "</gray>");
        if (!lore.isEmpty()) lore.add("");
        int rating = net.nerdsmp.paper.expedition.content.Difficulty.rating(definition);
        lore.add("<gold>" + net.nerdsmp.paper.expedition.content.Difficulty.stars(rating) + "</gold> <gray>"
            + net.nerdsmp.paper.expedition.content.Difficulty.label(rating) + "</gray>");
        String realm = switch (definition.dimension()) { case NETHER -> "<red>The Nether</red>"; case END -> "<light_purple>The End</light_purple>"; case OVERWORLD -> "<green>Overworld</green>"; };
        lore.add(realm + " <dark_gray>·</dark_gray> <gray>level</gray> <white>" + definition.level() + "</white>");
        lore.add("<gray>Party</gray> <white>" + (definition.minParty() == definition.maxParty() ? definition.maxParty()
            : definition.minParty() + "-" + definition.maxParty()) + " players</white> <dark_gray>·</dark_gray> <gray>"
            + definition.rooms().size() + " rooms · " + definition.timeLimitMinutes() + " min</gray>");
        long clears = service.clears(player, definition);
        if (clears > 0) lore.add("<green>Your clears: " + clears + "</green>");
        return lore;
    }

    private ItemStack status(Player player) {
        return MenuKit.item(Material.SPYGLASS, "<aqua>Your status</aqua>", List.of(
            "<gray>Active runs:</gray> <white>" + service.activeRuns() + "/" + service.maxInstances() + "</white>",
            "", MenuKit.hint("see your queue or run")));
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) { lines.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Board board)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !board.owner.equals(player.getUniqueId())) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        BiConsumer<Player, ClickType> action = board.actions.get(event.getRawSlot());
        if (action == null) return;
        ClickType click = event.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != board.inventory) return;
            MenuKit.clickSound(player);
            action.accept(player, click);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Board) event.setCancelled(true);
    }

    private static final class Board implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        private Inventory inventory;

        private Board(UUID owner) { this.owner = owner; }

        void set(int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
            inventory.setItem(slot, item);
            actions.put(slot, action);
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
