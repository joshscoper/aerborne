package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.VisualFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Arcane Codex, Chosen Imbuement and Arcane Research screens. None of these screens has an
 * input slot: every click is cancelled, so no item can be placed into or taken out of them, and
 * every action re-reads the live inventory before it spends anything.
 */
public final class ArcaneMenus implements Listener {
    private static final int SIZE = 54;
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final String TITLE = "<gradient:#C59AFA:#8CE8ED><bold>Arcane Codex</bold></gradient>";

    private final NerdSmpPlugin plugin;
    private final ArcaneForge forge;

    public ArcaneMenus(NerdSmpPlugin plugin, ArcaneForge forge) {
        this.plugin = plugin;
        this.forge = forge;
    }

    enum Screen { CODEX, CATEGORY, DETAIL, ASPECTS, HISTORY, HELP, IMBUE, LEVELS, RESEARCH, ATTUNE, CORRUPT }

    enum Filter {
        ALL("All entries"), KNOWN("Discovered only"), RESEARCHING("Being researched"), FITS("Fits your held item");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    static final class Holder implements InventoryHolder {
        final UUID viewer;
        final Screen screen;
        final boolean fromTable;
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        Inventory inventory;
        ArcaneTier tier;
        Filter filter = Filter.ALL;
        int page;
        String definition;
        final List<ArcaneForge.GemChoice> selection = new ArrayList<>();
        boolean prismatic;

        Holder(UUID viewer, Screen screen, boolean fromTable) {
            this.viewer = viewer;
            this.screen = screen;
            this.fromTable = fromTable;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        // No slot in these screens takes items, from either inventory, with any click type.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.viewer)
            || event.getClick() != ClickType.LEFT || event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) return;
        Consumer<Player> action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        MenuKit.clickSound(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                action.accept(player);
            } catch (RuntimeException failure) {
                forge.telemetry().increment(ArcaneTelemetry.Metric.GUI_ERRORS);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Arcane menu action failed", failure);
                player.closeInventory();
                Chat.error(player, "That screen ran into a problem. Nothing was spent.");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    // ---------------------------------------------------------------------------------------------
    // Entry points
    // ---------------------------------------------------------------------------------------------

    public boolean available(Player player) {
        ArcaneSettings settings = forge.settings();
        if (!settings.enabled() || !settings.codex()) {
            Chat.notice(player, "The Arcane Codex is not open on this server yet.");
            return false;
        }
        if (forge.account(player).isEmpty()) {
            Chat.accountLoading(player);
            return false;
        }
        if (forge.profile(player).isEmpty()) {
            Chat.error(player, "Your Arcane knowledge needs a staff review. Your items and discoveries were not changed.");
            return false;
        }
        return true;
    }

    public void openCodex(Player player, boolean fromTable) {
        if (!available(player)) return;
        ResearchProfile profile = forge.profile(player).orElseThrow();
        ArcaneSettings settings = forge.settings();
        Holder holder = holder(player, Screen.CODEX, fromTable);
        int masteryBinds = settings.research().masteryBinds();
        long known = forge.registry().all().stream().filter(definition -> profile.known(definition.id())).count();
        long mastered = forge.registry().all().stream()
            .filter(definition -> profile.state(definition.id(), masteryBinds) == ResearchProfile.State.MASTERED).count();
        int arcane = forge.arcaneLevel(player);
        List<String> summary = new ArrayList<>(List.of(
            "<gray>Discovered</gray> <white>" + known + "/" + forge.registry().all().size() + "</white>",
            "<gray>Mastered</gray> <white>" + mastered + "</white>",
            "<gray>Enchanting</gray> <white>" + arcane + "</white>",
            "<gray>Aspects studied</gray> <white>" + profile.studiedIds().size() + "/" + ArcaneAspect.values().length + "</white>"));
        nextMilestone(settings, arcane).ifPresent(line -> {
            summary.add("");
            summary.add(line);
        });
        button(holder, 4, Material.ENCHANTED_BOOK, TITLE, summary, null);

        button(holder, 19, Material.IRON_SWORD, ArcaneTier.FUNDAMENTAL.color() + "Fundamental Enchantments</aqua>",
            MenuKit.withHint(List.of("<gray>" + ArcaneTier.FUNDAMENTAL.description() + "</gray>",
                tierCount(profile, ArcaneTier.FUNDAMENTAL)), "browse"), target -> openCategory(target, ArcaneTier.FUNDAMENTAL, fromTable, 0, Filter.ALL));
        button(holder, 21, Material.SPIDER_EYE, ArcaneTier.SPECIALIZED.color() + "Specialized Enchantments</light_purple>",
            MenuKit.withHint(List.of("<gray>" + ArcaneTier.SPECIALIZED.description() + "</gray>",
                tierCount(profile, ArcaneTier.SPECIALIZED)), "browse"), target -> openCategory(target, ArcaneTier.SPECIALIZED, fromTable, 0, Filter.ALL));
        boolean signaturesVisible = settings.signatures().enabled() || arcane >= settings.research().signatureLevel()
            || forge.registry().tier(ArcaneTier.SIGNATURE).stream().anyMatch(definition -> profile.state(definition.id(), masteryBinds) != ResearchProfile.State.UNKNOWN);
        if (signaturesVisible) {
            button(holder, 23, Material.NETHER_STAR, "<gradient:#ff70f6:#67e8f9>Arcane Signatures</gradient>",
                MenuKit.withHint(List.of("<gray>" + ArcaneTier.SIGNATURE.description() + "</gray>", tierCount(profile, ArcaneTier.SIGNATURE),
                    settings.signatures().enabled() ? "" : "<yellow>Signatures are not active on this server yet.</yellow>"), "browse"),
                target -> openCategory(target, ArcaneTier.SIGNATURE, fromTable, 0, Filter.ALL));
        } else {
            holder.inventory.setItem(23, MenuKit.locked(Material.NETHER_STAR, "Arcane Signatures",
                List.of("<gray>Rare, build-defining enchantments.</gray>",
                    "<yellow>Reach Enchanting " + settings.research().signatureLevel() + " to learn more.</yellow>")));
        }
        button(holder, 25, Material.AMETHYST_CLUSTER, "<aqua>Aspect Encyclopedia</aqua>",
            MenuKit.withHint(List.of("<gray>The nine disciplines and the gems that carry them.</gray>"), "open"),
            target -> openAspects(target, fromTable));
        button(holder, 29, Material.WRITABLE_BOOK, "<yellow>Research in Progress</yellow>",
            MenuKit.withHint(List.of("<gray>Clues and partial progress toward undiscovered entries.</gray>"), "open"),
            target -> openCategory(target, null, fromTable, 0, Filter.RESEARCHING));
        button(holder, 31, Material.CLOCK, "<gray>Research History</gray>",
            MenuKit.withHint(List.of("<gray>Your last " + ResearchProfile.HISTORY_LIMIT + " experiments and binds.</gray>"), "open"),
            target -> openHistory(target, fromTable));
        button(holder, 33, Material.KNOWLEDGE_BOOK, "<green>How Enchanting Works</green>",
            MenuKit.withHint(List.of("<gray>A short guide to every part of the Arcane Forge.</gray>"), "read"),
            target -> openHelp(target, fromTable));
        stationTiles(holder, player);
        footer(holder, fromTable ? "Arcane Enchanting" : null, fromTable ? target -> plugin.menus().openStation(target,
            net.nerdsmp.paper.gui.MenuManager.View.ENCHANTMENTS) : null);
        player.openInventory(holder.inventory);
    }

    private void stationTiles(Holder holder, Player player) {
        ArcaneSettings settings = forge.settings();
        boolean atTable = forge.atStation(player);
        if (!settings.imbuement().enabled()) {
            holder.inventory.setItem(38, MenuKit.locked(Material.ANVIL, "Chosen Imbuement",
                List.of("<gray>Not open on this server yet.</gray>")));
        } else if (!atTable) {
            holder.inventory.setItem(38, MenuKit.locked(Material.ANVIL, "Chosen Imbuement",
                List.of("<gray>Choose an exact enchantment and level.</gray>", "<yellow>Stand beside an enchanting table.</yellow>")));
        } else {
            button(holder, 38, Material.ANVIL, "<light_purple>Chosen Imbuement</light_purple>",
                MenuKit.withHint(List.of("<gray>Choose an exact enchantment and level</gray>", "<gray>for the item in your hand. No randomness.</gray>"), "open"),
                target -> openImbue(target, holder.fromTable));
        }
        if (!settings.research().enabled()) {
            holder.inventory.setItem(42, MenuKit.locked(Material.BREWING_STAND, "Arcane Research",
                List.of("<gray>Not open on this server yet.</gray>")));
        } else if (!atTable) {
            holder.inventory.setItem(42, MenuKit.locked(Material.BREWING_STAND, "Arcane Research",
                List.of("<gray>Combine gems to discover enchantments.</gray>", "<yellow>Stand beside an enchanting table.</yellow>")));
        } else {
            button(holder, 42, Material.BREWING_STAND, "<aqua>Arcane Research</aqua>",
                MenuKit.withHint(List.of("<gray>Combine gems to discover enchantments.</gray>"), "open"),
                target -> openResearch(target, holder.fromTable, List.of(), false));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Codex pages
    // ---------------------------------------------------------------------------------------------

    void openCategory(Player player, ArcaneTier tier, boolean fromTable, int page, Filter filter) {
        if (!available(player)) return;
        ResearchProfile profile = forge.profile(player).orElseThrow();
        int masteryBinds = forge.settings().research().masteryBinds();
        ItemStack held = player.getInventory().getItemInMainHand();
        List<ArcaneDefinition> entries = forge.registry().all().stream()
            .filter(definition -> tier == null || definition.tier() == tier)
            .filter(definition -> switch (filter) {
                case ALL -> true;
                case KNOWN -> profile.known(definition.id());
                case RESEARCHING -> profile.state(definition.id(), masteryBinds) == ResearchProfile.State.PARTIALLY_DISCOVERED;
                case FITS -> fits(held, definition);
            })
            .sorted(Comparator.comparing((ArcaneDefinition definition) -> profile.known(definition.id()) ? 0 : 1)
                .thenComparing(ArcaneDefinition::tier).thenComparing(ArcaneDefinition::displayName))
            .toList();
        int pages = Math.max(1, (entries.size() + GRID.length - 1) / GRID.length);
        int current = Math.max(0, Math.min(page, pages - 1));
        Holder holder = holder(player, Screen.CATEGORY, fromTable);
        holder.tier = tier;
        holder.filter = filter;
        holder.page = current;
        String title = tier == null ? "Research in Progress" : tier == ArcaneTier.SIGNATURE ? "Arcane Signatures" : tier.displayName() + " Enchantments";
        button(holder, 4, Material.BOOK, "<light_purple><bold>" + title + "</bold></light_purple>", List.of(
            "<gray>Showing</gray> <white>" + filter.label + "</white>",
            "<gray>Entries</gray> <white>" + entries.size() + "</white>"), null);
        for (int index = 0; index < GRID.length; index++) {
            int position = current * GRID.length + index;
            if (position >= entries.size()) break;
            ArcaneDefinition definition = entries.get(position);
            ResearchProfile.State state = profile.state(definition.id(), masteryBinds);
            if (state.known()) {
                List<String> lore = new ArrayList<>(summaryLore(definition, profile, masteryBinds));
                button(holder, GRID[index], definition.icon(), definition.title(), MenuKit.withHint(lore, "see details"),
                    target -> openDetail(target, definition.id(), fromTable, tier, current, filter));
            } else {
                holder.inventory.setItem(GRID[index], MenuKit.item(state == ResearchProfile.State.UNKNOWN ? Material.GRAY_DYE : Material.LIGHT_GRAY_DYE,
                    "<gray>Undiscovered " + definition.tier().displayName() + "</gray>", undiscoveredLore(definition, profile)));
            }
        }
        if (entries.isEmpty()) {
            holder.inventory.setItem(22, MenuKit.item(Material.PAPER, "<gray>Nothing here yet</gray>", List.of(
                filter == Filter.RESEARCHING ? "<gray>Run an experiment in Arcane Research to start.</gray>"
                    : filter == Filter.FITS ? "<gray>Hold the item you want to check.</gray>" : "<gray>Try another filter.</gray>")));
        }
        if (tier != null) {
            button(holder, 47, Material.HOPPER, "<aqua>Filter: " + filter.label + "</aqua>",
                MenuKit.withHint(List.of("<gray>Next:</gray> <white>" + filter.next().label + "</white>"), "change the filter"),
                target -> openCategory(target, tier, fromTable, 0, filter.next()));
        }
        if (current > 0) button(holder, MenuKit.previousSlot(SIZE), MenuKit.previous(current, pages),
            target -> openCategory(target, tier, fromTable, current - 1, filter));
        if (current + 1 < pages) button(holder, MenuKit.nextSlot(SIZE), MenuKit.next(current, pages),
            target -> openCategory(target, tier, fromTable, current + 1, filter));
        footer(holder, "Arcane Codex", target -> openCodex(target, fromTable));
        player.openInventory(holder.inventory);
    }

    private void openDetail(Player player, String id, boolean fromTable, ArcaneTier tier, int page, Filter filter) {
        if (!available(player)) return;
        ResearchProfile profile = forge.profile(player).orElseThrow();
        ArcaneDefinition definition = forge.registry().byId(id).orElse(null);
        if (definition == null || !profile.known(id)) {
            openCategory(player, tier, fromTable, page, filter);
            return;
        }
        ArcaneSettings settings = forge.settings();
        int masteryBinds = settings.research().masteryBinds();
        Holder holder = holder(player, Screen.DETAIL, fromTable);
        holder.definition = id;
        List<String> header = new ArrayList<>(summaryLore(definition, profile, masteryBinds));
        button(holder, 13, definition.icon(), definition.title(), header, null);

        List<String> fit = new ArrayList<>();
        fit.add("<gray>Fits</gray> <white>" + definition.equipment() + "</white>");
        if (definition.enchantment() != null) {
            fit.add("<gray>Relic and authored gear with a matching stat also qualify.</gray>");
            fit.add("<gray>Levels</gray> <white>1-" + definition.maximumLevel() + "</white>");
        } else {
            fit.add("<gray>One Arcane Signature per item. Binding a new one replaces the old.</gray>");
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!held.getType().isAir()) fit.add(fits(held, definition) ? "<green>Fits the item in your hand.</green>"
            : "<red>Does not fit the item in your hand.</red>");
        button(holder, 29, Material.ITEM_FRAME, "<aqua>Equipment</aqua>", fit, null);

        List<String> cost = new ArrayList<>();
        int capacityCost = ArcaneCapacity.cost(settings.capacity(), definition, Math.max(1, definition.maximumLevel()));
        if (definition.enchantment() != null) {
            cost.add("<gray>Arcane Capacity</gray> <white>" + ArcaneCapacity.cost(settings.capacity(), definition, 1) + " per level</white>");
            boolean mastered = profile.state(id, masteryBinds) == ResearchProfile.State.MASTERED;
            cost.add("<gray>Chosen Imbuement, level by level:</gray>");
            for (int level = 1; level <= definition.maximumLevel(); level++) {
                cost.add("<dark_gray>  " + ArcaneForge.roman(level) + "</dark_gray> <light_purple>"
                    + ImbueRules.dust(settings.imbuement(), definition.tier(), level - 1, level, mastered) + " Dust</light_purple>"
                    + " <gray>· Enchanting " + ImbueRules.requiredLevel(settings.imbuement(), level) + "</gray>");
            }
            if (mastered) cost.add("<green>Mastery discount: " + settings.imbuement().masteryDiscountPercent() + "% off.</green>");
        } else {
            cost.add("<gray>Arcane Capacity</gray> <white>" + capacityCost + "</white>");
            cost.add("<gray>Binding</gray> <light_purple>" + settings.imbuement().signatureDust() + " Dust</light_purple>");
            cost.add("<gray>Plus one gem of each Aspect, Tier " + ArcaneForge.roman(settings.imbuement().signatureGemTier()) + " or higher:</gray>");
            definition.aspects().stream().sorted().forEach(aspect -> cost.add("<dark_gray>  </dark_gray>" + aspect.colored()
                + " <gray>(" + gemNames(aspect) + ")</gray>"));
            cost.add("<gray>Enchanting</gray> <white>" + settings.research().signatureLevel() + "</white>");
        }
        button(holder, 31, Material.GLOWSTONE_DUST, "<light_purple>Costs</light_purple>", cost, null);

        List<String> obtain = new ArrayList<>();
        if (definition.enchantment() != null) {
            obtain.add("<gray>Arcane Enchanting offers at any enchanting table.</gray>");
            obtain.add("<gray>Tomes from rare mobs and bosses, applied at the Forge.</gray>");
            obtain.add("<gray>Chosen Imbuement, once discovered.</gray>");
            if (ArcaneForge.effectEnchantment(definition.enchantment()) && !settings.effects().enabled(definition.enchantment())) {
                obtain.add("<yellow>Held for safety testing: no research cost, offers or imbuement.</yellow>");
                obtain.add("<gray>Existing copies stay on your items, dormant.</gray>");
            }
            obtain.add("");
            obtain.add("<gray>Mastery:</gray> <white>" + Math.min(masteryBinds, profile.entry(id).binds()) + "/" + masteryBinds
                + "</white> <gray>binds at level " + ArcaneForge.roman(definition.maximumLevel()) + "</gray>");
        } else {
            ArcaneSignature signature = definition.signature();
            ArcaneSettings.SignatureSettings numbers = settings.signatures().of(signature);
            obtain.add("<gray>Bind it with Chosen Imbuement.</gray>");
            obtain.addAll(signatureNumbers(signature, numbers));
            obtain.add(ArcaneForge.definitionActive(definition, settings) ? "<green>Active on this server.</green>"
                : "<yellow>Held for safety testing: no research or binding cost.</yellow>");
        }
        button(holder, 33, Material.COMPASS, "<gold>How to Get It</gold>", obtain, null);
        footer(holder, tier == null ? "Research in Progress" : "the list", target -> openCategory(target, tier, fromTable, page, filter));
        player.openInventory(holder.inventory);
    }

    private void openAspects(Player player, boolean fromTable) {
        if (!available(player)) return;
        ResearchProfile profile = forge.profile(player).orElseThrow();
        Holder holder = holder(player, Screen.ASPECTS, fromTable);
        button(holder, 4, Material.AMETHYST_CLUSTER, "<aqua><bold>Aspect Encyclopedia</bold></aqua>", List.of(
            "<gray>Every enchantment belongs to one, two or three Aspects.</gray>",
            "<gray>Each socket gem carries one Aspect for research.</gray>",
            "<gray>Prismatic gems speed research instead.</gray>"), null);
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 31, 30};
        ArcaneAspect[] aspects = ArcaneAspect.values();
        for (int index = 0; index < aspects.length; index++) {
            ArcaneAspect aspect = aspects[index];
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + aspect.description() + "</gray>");
            lore.add("<gray>Gems</gray> <white>" + gemNames(aspect) + "</white>");
            lore.add(profile.studied(aspect) ? "<green>Studied</green>" : "<dark_gray>Not studied yet</dark_gray>");
            List<String> knownNames = forge.registry().all().stream()
                .filter(definition -> definition.aspects().contains(aspect) && profile.known(definition.id()))
                .map(ArcaneDefinition::displayName).sorted().toList();
            long hidden = forge.registry().all().stream()
                .filter(definition -> definition.aspects().contains(aspect) && !profile.known(definition.id())).count();
            if (!knownNames.isEmpty()) lore.add("<gray>Known</gray> <white>" + String.join(", ", knownNames) + "</white>");
            if (hidden > 0) lore.add("<dark_gray>" + hidden + " more to discover</dark_gray>");
            button(holder, slots[index], aspect.icon(), aspect.colored(), lore, null);
        }
        footer(holder, "Arcane Codex", target -> openCodex(target, fromTable));
        player.openInventory(holder.inventory);
    }

    private void openHistory(Player player, boolean fromTable) {
        if (!available(player)) return;
        ResearchProfile profile = forge.profile(player).orElseThrow();
        Holder holder = holder(player, Screen.HISTORY, fromTable);
        List<String> history = new ArrayList<>(profile.history());
        java.util.Collections.reverse(history);
        if (history.isEmpty()) {
            button(holder, 22, Material.PAPER, "<gray>No history yet</gray>",
                List.of("<gray>Experiments and binds appear here.</gray>"), null);
        }
        for (int page = 0; page * 10 < history.size() && page < 3; page++) {
            List<String> lines = history.subList(page * 10, Math.min(history.size(), page * 10 + 10)).stream()
                .map(line -> "<gray>" + Chat.escape(line) + "</gray>").toList();
            button(holder, 20 + page * 2, Material.WRITTEN_BOOK, "<white>Entries " + (page * 10 + 1) + "-"
                + (page * 10 + lines.size()) + "</white>", lines, null);
        }
        footer(holder, "Arcane Codex", target -> openCodex(target, fromTable));
        player.openInventory(holder.inventory);
    }

    private void openHelp(Player player, boolean fromTable) {
        if (!available(player)) return;
        ArcaneSettings settings = forge.settings();
        Holder holder = holder(player, Screen.HELP, fromTable);
        button(holder, 4, Material.KNOWLEDGE_BOOK, "<green><bold>How Enchanting Works</bold></green>", List.of(
            "<gray>Nothing in the Arcane Forge spends experience levels.</gray>",
            "<gray>Nothing removes enchantments you already have.</gray>"), null);
        button(holder, 20, Material.ENCHANTING_TABLE, "<light_purple>1. Arcane Enchanting</light_purple>", List.of(
            "<gray>Right-click an enchanting table while holding gear.</gray>",
            "<gray>Pick one of three offers and pay Arcane Dust.</gray>",
            "<gray>Bookshelves and Enchanting make offers stronger.</gray>",
            "<gray>Every enchantment you bind is recorded here.</gray>"), null);
        button(holder, 22, Material.ANVIL, "<light_purple>2. Chosen Imbuement</light_purple>", List.of(
            "<gray>Choose any discovered enchantment and an exact level.</gray>",
            "<gray>The price is shown first and never changes.</gray>",
            "<gray>Enchanting</gray> <white>" + settings.imbuement().levelRequirements().get(2) + "/"
                + settings.imbuement().levelRequirements().get(3) + "/" + settings.imbuement().levelRequirements().get(4)
                + "</white> <gray>unlocks levels III/IV/V.</gray>",
            settings.imbuement().enabled() ? "<green>Open on this server.</green>" : "<yellow>Not open on this server yet.</yellow>"), null);
        button(holder, 24, Material.BREWING_STAND, "<aqua>3. Arcane Research</aqua>", List.of(
            "<gray>Pick one to three gems with different Aspects.</gray>",
            "<gray>One Aspect: a Fundamental enchantment.</gray>",
            "<gray>Two: a Specialized one. Three: an Arcane Signature.</gray>",
            "<gray>Each try teaches something new, or the menu says it can't.</gray>",
            "<gray>Higher-tier gems and Prismatic gems research faster.</gray>",
            settings.research().enabled() ? "<green>Open on this server.</green>" : "<yellow>Not open on this server yet.</yellow>"), null);
        button(holder, 30, Material.CHEST, "<gold>Arcane Capacity</gold>", List.of(
            "<gray>Each item holds a limited amount of enchantment,</gray>",
            "<gray>set by its rarity, item level and quality.</gray>",
            "<gray>Only binding an Arcane Signature needs free capacity.</gray>",
            "<gray>Items already over capacity keep everything.</gray>"), null);
        button(holder, 32, Material.NETHER_STAR, "<gradient:#ff70f6:#67e8f9>Arcane Signatures</gradient>", List.of(
            "<gray>One per item. Discover it, then bind it with</gray>",
            "<gray>Chosen Imbuement for Dust and three gems.</gray>",
            settings.overcharge().enabled()
                ? "<gray>Overcharge binds past capacity; stabilize it later with Dust.</gray>"
                : "<gray>Free capacity is required.</gray>"), null);
        button(holder, 38, Material.REDSTONE_BLOCK, "<dark_red>Corruption</dark_red>", List.of(
            "<gray>Pick a corruption for a clear bonus. In return the item</gray>",
            "<gray>wears " + settings.corruption().durabilityMultiplier() + "x faster, uses Arcane Capacity and takes</gray>",
            "<gray>no new Arcane work. Cleansing always works and costs</gray>",
            "<light_purple>" + settings.corruption().cleanseDust() + " Dust</light_purple><gray>. Nothing is random.</gray>",
            settings.corruption().enabled() ? "<green>Open on this server.</green>" : "<yellow>Not open on this server yet.</yellow>"), null);
        button(holder, 42, Material.AMETHYST_CLUSTER, "<aqua>Attunement</aqua>", List.of(
            "<gray>Focus an item on one of its Aspects: its enchantments of</gray>",
            "<gray>that Aspect grow " + settings.attunement().bonusPercent() + "% stronger, and a matching signature</gray>",
            "<gray>cools down " + settings.attunement().signatureCooldownPercent() + "% faster. Clearing it is free.</gray>",
            settings.attunement().enabled() ? "<green>Open on this server.</green>" : "<yellow>Not open on this server yet.</yellow>"), null);
        button(holder, 31, Material.EXPERIENCE_BOTTLE, "<green>The Enchanting Skill</green>", List.of(
            "<gray>Enchanting, researching and imbuing all earn Enchanting XP.</gray>",
            "<gray>Every Arcane requirement uses your Enchanting level,</gray>",
            "<gray>or your Smithing level if that is higher.</gray>",
            "<gray>Its talents discount Dust, speed research and</gray>",
            "<gray>shorten signature cooldowns.</gray>"), null);
        button(holder, 40, Material.OAK_SIGN, "<white>Commands</white>", List.of(
            "<white>/arcane</white> <gray>opens this Codex anywhere.</gray>",
            "<white>/arcane research</white> <gray>and</gray> <white>/arcane imbue</white> <gray>work at a table.</gray>",
            "<white>/arcane mastery</white> <gray>lists your mastery.</gray>"), null);
        footer(holder, "Arcane Codex", target -> openCodex(target, fromTable));
        player.openInventory(holder.inventory);
    }

    // ---------------------------------------------------------------------------------------------
    // Chosen Imbuement
    // ---------------------------------------------------------------------------------------------

    public void openImbue(Player player, boolean fromTable) {
        if (!available(player)) return;
        ArcaneSettings settings = forge.settings();
        if (!settings.imbuement().enabled()) {
            Chat.notice(player, "Chosen Imbuement is not open on this server yet.");
            return;
        }
        if (!forge.atStation(player)) {
            Chat.error(player, "Stand beside an enchanting table in Survival to use Chosen Imbuement.");
            return;
        }
        ResearchProfile profile = forge.profile(player).orElseThrow();
        int masteryBinds = settings.research().masteryBinds();
        Holder holder = holder(player, Screen.IMBUE, fromTable);
        ItemStack held = player.getInventory().getItemInMainHand();
        ArcaneForge.ItemView view = forge.view(held);
        if (held.getType().isAir() || !view.gear()) {
            holder.inventory.setItem(13, MenuKit.item(Material.BARRIER, "<red>Hold Your Gear</red>", List.of(
                plugin.items().isUnidentified(held) ? "<gray>Identify this item first.</gray>"
                    : "<gray>Hold one crafted, starter or relic item, then reopen.</gray>")));
            footer(holder, "Arcane Codex", target -> openCodex(target, fromTable));
            player.openInventory(holder.inventory);
            return;
        }
        holder.inventory.setItem(4, held.clone());
        button(holder, 6, Material.CHEST, "<gold>Arcane Capacity</gold>", capacityLore(view), null);
        List<ArcaneDefinition> choices = forge.registry().all().stream()
            .filter(definition -> fits(held, definition))
            .filter(definition -> ArcaneForge.definitionActive(definition, settings))
            .sorted(Comparator.comparing(ArcaneDefinition::tier).thenComparing(ArcaneDefinition::displayName))
            .toList();
        int slot = 0;
        int hidden = 0;
        for (ArcaneDefinition definition : choices) {
            if (!profile.known(definition.id())) {
                hidden++;
                continue;
            }
            if (slot >= GRID.length) break;
            int index = GRID[slot++];
            if (definition.isSignature()) {
                signatureTile(holder, index, player, profile, held, definition.signature());
                continue;
            }
            int current = view.enchantments().getOrDefault(definition.enchantment(), 0);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + definition.summary() + "</gray>");
            lore.add("<gray>On this item</gray> <white>" + (current == 0 ? "none" : ArcaneForge.roman(current)) + "</white>");
            if (profile.state(definition.id(), masteryBinds) == ResearchProfile.State.MASTERED) lore.add("<green>Mastered: cheaper to imbue.</green>");
            if (current >= definition.maximumLevel()) {
                lore.add("<gold>Already at its highest level.</gold>");
                holder.inventory.setItem(index, MenuKit.item(definition.icon(), definition.title(), lore));
            } else {
                button(holder, index, definition.icon(), definition.title(), MenuKit.withHint(lore, "choose a level"),
                    target -> openLevels(target, definition.id(), fromTable));
            }
        }
        if (hidden > 0) {
            holder.inventory.setItem(40, MenuKit.item(Material.GRAY_DYE, "<gray>" + hidden + " more could fit</gray>", List.of(
                "<gray>Discover them in Arcane Research, or bind them</gray>",
                "<gray>once through an offer or a tome.</gray>")));
        }
        if (slot == 0) {
            holder.inventory.setItem(22, MenuKit.item(Material.PAPER, "<gray>Nothing discovered fits this item</gray>", List.of(
                "<gray>Research Aspects, or use the table's offers first.</gray>")));
        }
        if (view.instability() > 0) {
            int cost = forge.stabilizeCost(held);
            ItemStack snapshot = held.clone();
            int heldSlot = player.getInventory().getHeldItemSlot();
            button(holder, 42, Material.RESPAWN_ANCHOR, "<yellow>Stabilize</yellow>", MenuKit.withHint(List.of(
                "<gray>Instability</gray> <yellow>" + view.instability() + "</yellow>",
                "<gray>Cost</gray> <light_purple>" + cost + " Arcane Dust</light_purple>",
                "<gray>Removes all instability. Nothing else changes.</gray>"), "review"), target -> {
                    ItemStack after = snapshot.clone();
                    plugin.items().stabilize(after);
                    MenuKit.openConfirm(plugin, target, "<yellow><bold>Stabilize Item</bold></yellow>", after, List.of(
                        "<gray>Cost</gray> <light_purple>" + cost + " Arcane Dust</light_purple>",
                        "<gray>Instability</gray> <yellow>" + view.instability() + "</yellow> <gray>→</gray> <green>0</green>"),
                        confirmed -> {
                            tell(confirmed, forge.stabilize(confirmed, snapshot, heldSlot), "Stabilized. Its signature cooldown is back to normal.");
                            openImbue(confirmed, fromTable);
                        }, cancelled -> openImbue(cancelled, fromTable));
                });
        }
        itemWorkshopTiles(holder, player, held, view, fromTable);
        button(holder, 50, Material.ENCHANTED_BOOK, "<light_purple>Arcane Codex</light_purple>",
            MenuKit.withHint(List.of("<gray>Browse everything you know.</gray>"), "open"), target -> openCodex(target, fromTable));
        footer(holder, fromTable ? "Arcane Enchanting" : "Arcane Codex", fromTable
            ? target -> plugin.menus().openStation(target, net.nerdsmp.paper.gui.MenuManager.View.ENCHANTMENTS)
            : target -> openCodex(target, false));
        player.openInventory(holder.inventory);
    }

    private void openLevels(Player player, String id, boolean fromTable) {
        if (!available(player)) return;
        ArcaneDefinition definition = forge.registry().byId(id).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (definition == null || definition.isSignature() || !forge.atStation(player)) {
            openImbue(player, fromTable);
            return;
        }
        ResearchProfile profile = forge.profile(player).orElseThrow();
        ArcaneSettings settings = forge.settings();
        Holder holder = holder(player, Screen.LEVELS, fromTable);
        holder.definition = id;
        int dust = forge.countDust(player);
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack snapshot = held.clone();
        holder.inventory.setItem(4, held.clone());
        button(holder, 13, definition.icon(), definition.title(), List.of("<gray>" + definition.summary() + "</gray>",
            "<gray>Your Arcane Dust</gray> <light_purple>" + dust + "</light_purple>",
            "<gray>Your Enchanting</gray> <white>" + forge.arcaneLevel(player) + "</white>"), null);
        int[] slots = {29, 30, 31, 32, 33};
        for (int level = 1; level <= Math.min(slots.length, definition.maximumLevel()); level++) {
            int target = level;
            ImbueRules.Quote quote = forge.imbueQuote(player, profile, held, definition, level);
            List<String> lore = new ArrayList<>();
            if (quote.status() == ImbueRules.Status.NO_UPGRADE) {
                lore.add(target <= quote.fromLevel() ? "<green>The item already has this level.</green>" : "<gray>Not available.</gray>");
                holder.inventory.setItem(slots[level - 1], MenuKit.item(Material.LIME_STAINED_GLASS_PANE,
                    "<green>" + definition.displayName() + " " + ArcaneForge.roman(level) + "</green>", lore));
                continue;
            }
            lore.add("<gray>Cost</gray> <light_purple>" + quote.dust() + " Arcane Dust</light_purple>"
                + (quote.discounted() ? " <green>(mastery)</green>" : ""));
            lore.add("<gray>From</gray> <white>" + (quote.fromLevel() == 0 ? "none" : ArcaneForge.roman(quote.fromLevel()))
                + "</white> <gray>to</gray> <white>" + ArcaneForge.roman(target) + "</white>");
            if (quote.status() == ImbueRules.Status.NEEDS_LEVEL) {
                lore.add("<red>Requires Enchanting " + quote.requiredLevel() + ".</red>");
                holder.inventory.setItem(slots[level - 1], MenuKit.item(Material.GRAY_DYE,
                    "<gray>" + definition.displayName() + " " + ArcaneForge.roman(level) + "</gray>", lore));
                continue;
            }
            if (quote.status() != ImbueRules.Status.READY) {
                lore.add("<red>" + imbueStatus(quote.status()) + "</red>");
                holder.inventory.setItem(slots[level - 1], MenuKit.item(Material.GRAY_DYE,
                    "<gray>" + definition.displayName() + " " + ArcaneForge.roman(level) + "</gray>", lore));
                continue;
            }
            boolean affordable = dust >= quote.dust();
            if (!affordable) {
                lore.add("<red>Missing " + (quote.dust() - dust) + " Arcane Dust.</red>");
                holder.inventory.setItem(slots[level - 1], MenuKit.item(Material.ENCHANTED_BOOK,
                    "<red>" + definition.displayName() + " " + ArcaneForge.roman(level) + "</red>", lore));
                continue;
            }
            ItemStack after = snapshot.clone();
            plugin.items().enchant(after, definition.enchantment(), target);
            lore.add("<gray>Guaranteed. Nothing is random.</gray>");
            button(holder, slots[level - 1], Material.ENCHANTED_BOOK,
                "<light_purple>" + definition.displayName() + " " + ArcaneForge.roman(level) + "</light_purple>",
                MenuKit.withHint(lore, "review"), clicked -> MenuKit.openConfirm(plugin, clicked,
                    "<light_purple><bold>Chosen Imbuement</bold></light_purple>", after, List.of(
                        "<gray>Cost</gray> <light_purple>" + quote.dust() + " Arcane Dust</light_purple>",
                        "<gray>" + definition.displayName() + "</gray> <white>"
                            + (quote.fromLevel() == 0 ? "none" : ArcaneForge.roman(quote.fromLevel())) + " → " + ArcaneForge.roman(target) + "</white>",
                        "<gray>The summary above is the finished item.</gray>"),
                    confirmed -> {
                        tell(confirmed, forge.imbue(confirmed, snapshot, heldSlot, definition, target),
                            definition.displayName() + " " + ArcaneForge.roman(target) + " bound to your item.");
                        openImbue(confirmed, fromTable);
                    }, cancelled -> openLevels(cancelled, id, fromTable)));
        }
        footer(holder, "Chosen Imbuement", target -> openImbue(target, fromTable));
        player.openInventory(holder.inventory);
    }

    private void signatureTile(Holder holder, int slot, Player player, ResearchProfile profile, ItemStack held, ArcaneSignature signature) {
        ArcaneForge.SignatureQuote quote = forge.signatureQuote(player, profile, held, signature);
        ArcaneDefinition definition = forge.registry().of(signature);
        ArcaneSettings settings = forge.settings();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + signature.summary() + "</gray>");
        lore.add("<gray>Capacity</gray> <white>" + quote.capacity().loadAfter() + "/" + quote.capacity().capacity() + "</white>"
            + " <gray>after binding</gray>");
        if (quote.replaced() != null) lore.add("<yellow>Replaces " + quote.replaced().displayName() + ". It is not refunded.</yellow>");
        if (quote.capacity().fit() == ArcaneCapacity.Fit.OVERCHARGE) {
            lore.add("<yellow>Overcharge " + quote.capacity().overcharge() + ": the item becomes Unstable " + quote.capacity().overcharge() + ".</yellow>");
            lore.add("<gray>Its signature cooldown is " + quote.capacity().overcharge() * settings.overcharge().cooldownPercentPerPoint()
                + "% longer until you stabilize it for " + quote.capacity().overcharge() * settings.overcharge().stabilizeDustPerPoint() + " Dust.</gray>");
        }
        lore.add("<gray>Cost</gray> <light_purple>" + quote.dust() + " Arcane Dust</light_purple>");
        lore.add("<gray>Plus one gem each, Tier " + ArcaneForge.roman(quote.gemTier()) + "+:</gray> " + ArcaneAspect.names(signature.aspects()));
        boolean gems = forge.hasGems(player, signature.aspects(), quote.gemTier());
        boolean dust = forge.countDust(player) >= quote.dust();
        if (!settings.signatures().active(signature)) lore.add("<yellow>Not active on this server yet; it will wait, dormant.</yellow>");
        if (!quote.ready()) {
            lore.add("<red>" + signatureStatus(quote, settings) + "</red>");
            holder.inventory.setItem(slot, MenuKit.item(Material.GRAY_DYE, definition.title(), lore));
            return;
        }
        if (!gems || !dust) {
            lore.add("<red>" + (!gems ? "You are missing a required gem." : "You need more Arcane Dust.") + "</red>");
            holder.inventory.setItem(slot, MenuKit.item(signature.icon(), definition.title(), lore));
            return;
        }
        ItemStack snapshot = held.clone();
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack after = snapshot.clone();
        plugin.items().bindArcaneSignature(after, signature, quote.capacity().overcharge());
        List<String> detail = new ArrayList<>(lore.subList(1, lore.size()));
        button(holder, slot, signature.icon(), definition.title(), MenuKit.withHint(lore, "review"), clicked ->
            MenuKit.openConfirm(plugin, clicked, "<gradient:#ff70f6:#67e8f9><bold>Bind Signature</bold></gradient>", after, detail,
                confirmed -> {
                    tell(confirmed, forge.bindSignature(confirmed, snapshot, heldSlot, signature), signature.displayName() + " bound to your item.");
                    openImbue(confirmed, holder.fromTable);
                }, cancelled -> openImbue(cancelled, holder.fromTable)));
    }

    // ---------------------------------------------------------------------------------------------
    // Attunement, corruption and cleansing
    // ---------------------------------------------------------------------------------------------

    private void itemWorkshopTiles(Holder holder, Player player, ItemStack held, ArcaneForge.ItemView view, boolean fromTable) {
        ArcaneSettings settings = forge.settings();
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack snapshot = held.clone();
        if (settings.attunement().enabled() || view.attunement() != null) {
            List<String> lore = new ArrayList<>();
            lore.add(view.attunement() == null ? "<gray>Not attuned.</gray>" : "<gray>Attuned to</gray> " + view.attunement().colored());
            lore.add("<gray>Focus one Aspect: its enchantments here grow " + settings.attunement().bonusPercent() + "% stronger.</gray>");
            button(holder, 37, Material.AMETHYST_CLUSTER, "<aqua>Attunement</aqua>", MenuKit.withHint(lore, "open"),
                target -> openAttune(target, fromTable));
        }
        if (view.corrupted()) {
            int cost = forge.cleanseCost();
            String name = view.corruption() == null ? "an unknown corruption" : view.corruption().displayName();
            button(holder, 39, Material.MILK_BUCKET, "<green>Cleanse</green>", MenuKit.withHint(List.of(
                "<gray>Removes " + name + " and nothing else. Always works.</gray>",
                "<gray>Cost</gray> <light_purple>" + cost + " Arcane Dust</light_purple>"), "review"), clicked -> {
                    ItemStack after = snapshot.clone();
                    plugin.items().cleanse(after);
                    MenuKit.openConfirm(plugin, clicked, "<green><bold>Cleanse Item</bold></green>", after, List.of(
                        "<gray>Cost</gray> <light_purple>" + cost + " Arcane Dust</light_purple>",
                        "<gray>Removes</gray> <red>" + name + "</red>"), confirmed -> {
                            tell(confirmed, forge.cleanse(confirmed, snapshot, heldSlot), "Cleansed. The corruption is gone.");
                            openImbue(confirmed, fromTable);
                        }, cancelled -> openImbue(cancelled, fromTable));
                });
        } else if (settings.corruption().enabled()) {
            button(holder, 39, Material.REDSTONE_BLOCK, "<dark_red>Corruption</dark_red>", MenuKit.withHint(List.of(
                "<gray>Trade durability and future work for a fixed bonus.</gray>",
                "<gray>Cleansing always works.</gray>"), "open"), target -> openCorrupt(target, fromTable));
        }
    }

    private void openAttune(Player player, boolean fromTable) {
        if (!available(player) || !forge.atStation(player)) {
            openImbue(player, fromTable);
            return;
        }
        ArcaneSettings settings = forge.settings();
        ItemStack held = player.getInventory().getItemInMainHand();
        ArcaneForge.ItemView view = forge.view(held);
        if (!view.gear()) {
            openImbue(player, fromTable);
            return;
        }
        Holder holder = holder(player, Screen.ATTUNE, fromTable);
        holder.inventory.setItem(4, held.clone());
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack snapshot = held.clone();
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 30, 32};
        ArcaneAspect[] aspects = ArcaneAspect.values();
        for (int index = 0; index < aspects.length; index++) {
            ArcaneAspect aspect = aspects[index];
            ArcaneForge.AttunementStatus status = forge.attunementStatus(player, held, aspect);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + aspect.description() + "</gray>");
            List<String> boosted = view.enchantments().keySet().stream()
                .filter(enchantment -> forge.registry().of(enchantment).aspects().contains(aspect))
                .map(ArcaneEnchantment::displayName).sorted().toList();
            if (!boosted.isEmpty()) lore.add("<gray>Strengthens</gray> <white>" + String.join(", ", boosted) + "</white> <gray>by "
                + settings.attunement().bonusPercent() + "%</gray>");
            if (view.signature() != null && view.signature().aspects().contains(aspect)) {
                lore.add("<gray>" + view.signature().displayName() + " cools down " + settings.attunement().signatureCooldownPercent() + "% faster.</gray>");
            }
            if (status != ArcaneForge.AttunementStatus.READY) {
                lore.add(switch (status) {
                    case READY -> "";
                    case DISABLED -> "<yellow>Attunement is not open on this server yet.</yellow>";
                    case NEEDS_LEVEL -> "<red>Requires Enchanting " + settings.attunement().requiredLevel() + ".</red>";
                    case CORRUPTED -> "<red>Cleanse this item's corruption first.</red>";
                    case NO_MATCH -> "<dark_gray>Nothing on this item has this Aspect.</dark_gray>";
                    case ALREADY_ATTUNED -> "<green>Current attunement.</green>";
                });
                ItemStack icon = MenuKit.item(status == ArcaneForge.AttunementStatus.ALREADY_ATTUNED ? aspect.icon() : Material.GRAY_DYE,
                    aspect.colored(), lore);
                if (status == ArcaneForge.AttunementStatus.ALREADY_ATTUNED) glint(icon);
                holder.inventory.setItem(slots[index], icon);
                continue;
            }
            lore.add("<gray>Cost</gray> <light_purple>" + settings.attunement().dust() + " Arcane Dust</light_purple>");
            if (view.attunement() != null) lore.add("<yellow>Replaces the " + view.attunement().displayName() + " attunement.</yellow>");
            ItemStack after = snapshot.clone();
            plugin.items().attune(after, aspect);
            button(holder, slots[index], aspect.icon(), aspect.colored(), MenuKit.withHint(lore, "review"), clicked ->
                MenuKit.openConfirm(plugin, clicked, "<aqua><bold>Attune Item</bold></aqua>", after, List.of(
                    "<gray>Cost</gray> <light_purple>" + settings.attunement().dust() + " Arcane Dust</light_purple>",
                    "<gray>Attune to</gray> " + aspect.colored()), confirmed -> {
                        tell(confirmed, forge.attune(confirmed, snapshot, heldSlot, aspect), "Attuned to " + aspect.displayName() + ".");
                        openAttune(confirmed, fromTable);
                    }, cancelled -> openAttune(cancelled, fromTable)));
        }
        if (view.attunement() != null) {
            button(holder, 40, Material.BUCKET, "<gray>Clear Attunement</gray>", MenuKit.withHint(List.of(
                "<gray>Free. The item returns to its normal strength.</gray>"), "clear it"), clicked -> {
                    tell(clicked, forge.clearAttunement(clicked, snapshot, heldSlot), "Attunement cleared.");
                    openAttune(clicked, fromTable);
                });
        }
        footer(holder, "Chosen Imbuement", target -> openImbue(target, fromTable));
        player.openInventory(holder.inventory);
    }

    private void openCorrupt(Player player, boolean fromTable) {
        if (!available(player) || !forge.atStation(player)) {
            openImbue(player, fromTable);
            return;
        }
        ArcaneSettings settings = forge.settings();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!forge.view(held).gear()) {
            openImbue(player, fromTable);
            return;
        }
        Holder holder = holder(player, Screen.CORRUPT, fromTable);
        holder.inventory.setItem(4, held.clone());
        button(holder, 13, Material.REDSTONE_BLOCK, "<dark_red><bold>Corruption</bold></dark_red>", List.of(
            "<gray>A fixed bonus, a fixed price. Nothing is random.</gray>",
            "<gray>The item wears " + settings.corruption().durabilityMultiplier() + "x faster, uses "
                + settings.corruption().capacityCost() + " Arcane Capacity,</gray>",
            "<gray>and takes no imbuement, signature or attunement until cleansed.</gray>",
            "<gray>Cleansing always works and costs " + settings.corruption().cleanseDust() + " Dust.</gray>"), null);
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack snapshot = held.clone();
        int dust = forge.countDust(player);
        ArcaneCorruption[] corruptions = ArcaneCorruption.values();
        int[] positions = {28, 30, 32, 34};
        for (int index = 0; index < corruptions.length; index++) {
            ArcaneCorruption corruption = corruptions[index];
            ArcaneForge.CorruptionQuote quote = forge.corruptionQuote(player, held, corruption);
            List<String> lore = new ArrayList<>(List.of(
                "<gray>Bonus</gray> <white>" + corruption.bonusText() + "</white>",
                "<gray>Fits</gray> <white>" + corruption.equipment() + "</white>",
                "<gray>Capacity</gray> <white>" + quote.capacity().loadAfter() + "/" + quote.capacity().capacity() + "</white> <gray>after</gray>",
                "<gray>Cost</gray> <light_purple>" + quote.dust() + " Arcane Dust</light_purple>"));
            if (!quote.ready()) {
                lore.add("<red>" + switch (quote.status()) {
                    case READY -> "";
                    case DISABLED -> "Corruption is not open on this server yet.";
                    case NEEDS_LEVEL -> "Requires Enchanting " + quote.requiredLevel() + ".";
                    case INCOMPATIBLE -> "Does not fit this item.";
                    case ALREADY_CORRUPTED -> "This item is already corrupted.";
                    case UNSTABLE -> "Stabilize this item first.";
                    case OVER_CAPACITY -> "Insufficient Arcane Capacity: " + quote.capacity().shortfall() + " more needed.";
                } + "</red>");
                holder.inventory.setItem(positions[index], MenuKit.item(Material.GRAY_DYE, "<gray>" + corruption.displayName() + "</gray>", lore));
                continue;
            }
            if (dust < quote.dust()) {
                lore.add("<red>Missing " + (quote.dust() - dust) + " Arcane Dust.</red>");
                holder.inventory.setItem(positions[index], MenuKit.item(corruption.icon(), "<red>" + corruption.displayName() + "</red>", lore));
                continue;
            }
            ItemStack after = snapshot.clone();
            plugin.items().corrupt(after, corruption);
            button(holder, positions[index], corruption.icon(), "<dark_red>" + corruption.displayName() + "</dark_red>",
                MenuKit.withHint(lore, "review"), clicked -> MenuKit.openConfirm(plugin, clicked,
                    "<dark_red><bold>Corrupt Item</bold></dark_red>", after, List.of(
                        "<gray>Cost</gray> <light_purple>" + quote.dust() + " Arcane Dust</light_purple>",
                        "<gray>Bonus</gray> <white>" + corruption.bonusText() + "</white>",
                        "<red>Wears " + settings.corruption().durabilityMultiplier() + "x faster until cleansed.</red>"),
                    confirmed -> {
                        tell(confirmed, forge.corrupt(confirmed, snapshot, heldSlot, corruption), "Corrupted with " + corruption.displayName() + ".");
                        openImbue(confirmed, fromTable);
                    }, cancelled -> openCorrupt(cancelled, fromTable)));
        }
        footer(holder, "Chosen Imbuement", target -> openImbue(target, fromTable));
        player.openInventory(holder.inventory);
    }

    // ---------------------------------------------------------------------------------------------
    // Arcane Research
    // ---------------------------------------------------------------------------------------------

    public void openResearch(Player player, boolean fromTable, List<ArcaneForge.GemChoice> selection, boolean prismatic) {
        if (!available(player)) return;
        ArcaneSettings settings = forge.settings();
        if (!settings.research().enabled()) {
            Chat.notice(player, "Arcane Research is not open on this server yet.");
            return;
        }
        if (!forge.atStation(player)) {
            Chat.error(player, "Stand beside an enchanting table in Survival to use Arcane Research.");
            return;
        }
        ResearchProfile profile = forge.profile(player).orElseThrow();
        Map<ArcaneForge.GemChoice, Integer> owned = forge.gems(player);
        // Drop selections the player no longer carries.
        List<ArcaneForge.GemChoice> chosen = selection.stream().filter(choice -> owned.getOrDefault(choice, 0) > 0).limit(3).toList();
        boolean hasPrismatic = owned.keySet().stream().anyMatch(choice -> choice.type() == GemType.PRISMATIC);
        boolean usePrismatic = prismatic && hasPrismatic;
        Holder holder = holder(player, Screen.RESEARCH, fromTable);
        holder.selection.addAll(chosen);
        holder.prismatic = usePrismatic;
        int arcane = forge.arcaneLevel(player);
        int dust = forge.countDust(player);
        button(holder, 4, Material.BREWING_STAND, "<aqua><bold>Arcane Research</bold></aqua>", List.of(
            "<gray>Pick one to three gems with different Aspects.</gray>",
            "<gray>One gem of each pick, and Dust, are used up.</gray>",
            "<gray>Your Enchanting</gray> <white>" + arcane + "</white> <gray>· Dust</gray> <light_purple>" + dust + "</light_purple>"), null);
        int index = 0;
        for (var entry : owned.entrySet()) {
            ArcaneForge.GemChoice choice = entry.getKey();
            if (choice.type() == GemType.PRISMATIC || index >= GRID.length) continue;
            ArcaneAspect aspect = ArcaneAspect.ofGem(choice.type()).orElseThrow();
            boolean selected = chosen.contains(choice);
            List<String> lore = new ArrayList<>(List.of(
                "<gray>Aspect</gray> " + aspect.colored(),
                "<gray>Tier</gray> <white>" + ArcaneForge.roman(choice.tier()) + "</white> <gray>· research ×"
                    + String.format(java.util.Locale.ROOT, "%.2f", settings.research().gemMultiplier(choice.tier())) + "</gray>",
                "<gray>You carry</gray> <white>" + entry.getValue() + "</white>"));
            ItemStack icon = MenuKit.item(choice.type().material(), choice.type().color() + choice.type().displayName() + " "
                + ArcaneForge.roman(choice.tier()), MenuKit.withHint(lore, selected ? "remove it" : "add it"));
            if (selected) glint(icon);
            holder.inventory.setItem(GRID[index], icon);
            holder.actions.put(GRID[index], target -> {
                List<ArcaneForge.GemChoice> next = new ArrayList<>(chosen);
                if (!next.remove(choice)) {
                    next.removeIf(other -> ArcaneAspect.ofGem(other.type()).equals(ArcaneAspect.ofGem(choice.type())));
                    if (next.size() < 3) next.add(choice);
                }
                openResearch(target, fromTable, next, usePrismatic);
            });
            index++;
        }
        if (index == 0) {
            holder.inventory.setItem(22, MenuKit.item(Material.PAPER, "<gray>You carry no socket gems</gray>", List.of(
                "<gray>Crack geodes from mining and excavation.</gray>")));
        }
        if (hasPrismatic) {
            ItemStack icon = MenuKit.item(Material.NETHER_STAR, "<gradient:#ff70f6:#67e8f9>Prismatic Catalyst</gradient>",
                MenuKit.withHint(List.of("<gray>Spend one Prismatic gem for ×"
                    + settings.research().prismaticMultiplier() + " research progress.</gray>",
                    usePrismatic ? "<green>Included.</green>" : "<gray>Not included.</gray>"), usePrismatic ? "remove it" : "add it"));
            if (usePrismatic) glint(icon);
            holder.inventory.setItem(37, icon);
            holder.actions.put(37, target -> openResearch(target, fromTable, chosen, !usePrismatic));
        }
        researchPreview(holder, player, profile, chosen, usePrismatic, dust);
        if (!chosen.isEmpty()) button(holder, 43, Material.BUCKET, "<gray>Clear Selection</gray>",
            MenuKit.withHint(List.of(), "clear"), target -> openResearch(target, fromTable, List.of(), false));
        button(holder, 50, Material.ENCHANTED_BOOK, "<light_purple>Arcane Codex</light_purple>",
            MenuKit.withHint(List.of("<gray>See clues and partial progress.</gray>"), "open"), target -> openCodex(target, fromTable));
        footer(holder, fromTable ? "Arcane Enchanting" : "Arcane Codex", fromTable
            ? target -> plugin.menus().openStation(target, net.nerdsmp.paper.gui.MenuManager.View.ENCHANTMENTS)
            : target -> openCodex(target, false));
        player.openInventory(holder.inventory);
    }

    private void researchPreview(Holder holder, Player player, ResearchProfile profile, List<ArcaneForge.GemChoice> chosen,
                                 boolean prismatic, int dust) {
        ArcaneSettings settings = forge.settings();
        if (chosen.isEmpty()) {
            holder.inventory.setItem(40, MenuKit.item(Material.GLASS_BOTTLE, "<gray>Choose Gems</gray>", List.of(
                "<gray>Click gems above to add them to the experiment.</gray>")));
            return;
        }
        ArcaneResearch.Plan plan = forge.researchPlan(player, profile, chosen, prismatic);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Aspects</gray> " + ArcaneAspect.names(plan.aspects()));
        chosen.forEach(choice -> lore.add("<dark_gray>  </dark_gray><gray>1 × " + choice.type().displayName() + " "
            + ArcaneForge.roman(choice.tier()) + "</gray>"));
        if (prismatic) lore.add("<dark_gray>  </dark_gray><gray>1 × Prismatic Gem</gray>");
        lore.add("<gray>Cost</gray> <light_purple>" + plan.dustCost() + " Arcane Dust</light_purple>"
            + (dust < plan.dustCost() ? " <red>(you have " + dust + ")</red>" : ""));
        lore.add("<gray>Research speed</gray> <white>×" + String.format(java.util.Locale.ROOT, "%.2f", plan.multiplier()) + "</white>");
        if (profile.attempts(plan.combination()) > 0) lore.add("<gray>You have tried this " + profile.attempts(plan.combination())
            + " time" + (profile.attempts(plan.combination()) == 1 ? "" : "s") + ". Repeats teach less.</gray>");
        String problem = switch (plan.status()) {
            case READY -> dust < plan.dustCost() ? "You need more Arcane Dust." : null;
            case INVALID -> "Use one to three gems with different Aspects.";
            case LOCKED -> "Research unlocks at Enchanting " + plan.requiredLevel() + ".";
            case NEEDS_LEVEL -> plan.aspects().size() + "-Aspect research needs Enchanting " + plan.requiredLevel() + ".";
            case NEEDS_MASTERY -> "Three-Aspect research needs a mastered enchantment sharing one of these Aspects ("
                + settings.research().masteryBinds() + " binds at its top level).";
            case ALREADY_KNOWN -> "You already know what these Aspects make.";
            case FRUITLESS -> "This combination has nothing more to teach you.";
            case FEATURE_DISABLED -> "This discipline is held for safety testing. No gems or Dust will be spent.";
        };
        if (problem != null) {
            lore.add("<red>" + problem + "</red>");
            holder.inventory.setItem(40, MenuKit.item(Material.BARRIER, "<red>Can't Research This</red>", lore));
            return;
        }
        lore.add("<gray>The outcome is revealed when you try.</gray>");
        List<ArcaneForge.GemChoice> frozen = List.copyOf(chosen);
        ItemStack summary = MenuKit.item(Material.BREWING_STAND, "<aqua>Experiment</aqua>", lore);
        button(holder, 40, Material.LIME_DYE, "<green>Run Experiment</green>", MenuKit.withHint(lore, "review"), clicked ->
            MenuKit.openConfirm(plugin, clicked, "<aqua><bold>Run Experiment</bold></aqua>", summary, lore.subList(0, lore.size() - 1),
                confirmed -> {
                    ArcaneForge.ResearchOutcome outcome = forge.research(confirmed, frozen, prismatic);
                    if (outcome.outcome() == ArcaneForge.Outcome.APPLIED) reportResearch(confirmed, outcome.result());
                    else tell(confirmed, outcome.outcome(), "");
                    openResearch(confirmed, holder.fromTable, frozen, prismatic);
                }, cancelled -> openResearch(cancelled, holder.fromTable, frozen, prismatic)));
    }

    private void reportResearch(Player player, ArcaneResearch.Result result) {
        Chat.info(player, "<aqua>Experiment complete.</aqua>");
        for (ArcaneResearch.Outcome outcome : result.outcomes()) {
            switch (outcome.kind()) {
                case DISCOVERED -> Chat.success(player, "Discovered " + outcome.definition().title() + "<green>! It is now in your Codex.</green>");
                case PROGRESS -> Chat.notice(player, "Progress toward an undiscovered " + outcome.definition().tier().displayName()
                    + ": " + outcome.from() + "% → " + outcome.to() + "%.");
                case PARTIAL -> Chat.info(player, "These Aspects resonate with an unknown " + outcome.definition().tier().displayName()
                    + " (" + outcome.to() + "%).");
                case CLUE -> Chat.notice(player, "Clue found: an unknown " + outcome.definition().tier().displayName() + " is made of "
                    + ArcaneAspect.names(outcome.definition().aspects()) + "<yellow>.</yellow>");
                case ASPECT_STUDIED -> Chat.info(player, "You studied the " + outcome.aspect().colored() + " <gray>Aspect.</gray>");
                case NO_RESONANCE -> Chat.info(player, "No resonance. These Aspects form no known discipline.");
            }
        }
        if (result.experience() > 0) Chat.info(player, "Enchanting XP earned: <white>" + result.experience() + "</white>.");
    }

    // ---------------------------------------------------------------------------------------------
    // Shared pieces
    // ---------------------------------------------------------------------------------------------

    private Holder holder(Player player, Screen screen, boolean fromTable) {
        Holder holder = new Holder(player.getUniqueId(), screen, fromTable);
        String title = switch (screen) {
            case CODEX, CATEGORY, DETAIL, ASPECTS, HISTORY, HELP -> TITLE;
            case IMBUE, LEVELS -> "<light_purple><bold>Chosen Imbuement</bold></light_purple>";
            case RESEARCH -> "<aqua><bold>Arcane Research</bold></aqua>";
            case ATTUNE -> "<aqua><bold>Attunement</bold></aqua>";
            case CORRUPT -> "<dark_red><bold>Corruption</bold></dark_red>";
        };
        holder.inventory = Bukkit.createInventory(holder, SIZE, Chat.render(title));
        MenuKit.frame(holder.inventory);
        return holder;
    }

    private void footer(Holder holder, String parent, Consumer<Player> back) {
        if (parent != null && back != null) button(holder, MenuKit.backSlot(SIZE), MenuKit.back(parent), back);
        button(holder, MenuKit.closeSlot(SIZE), MenuKit.close(), Player::closeInventory);
    }

    private void button(Holder holder, int slot, Material material, String name, List<String> lore, Consumer<Player> action) {
        button(holder, slot, MenuKit.item(material, name, lore), action);
    }

    private void button(Holder holder, int slot, ItemStack item, Consumer<Player> action) {
        holder.inventory.setItem(slot, item);
        if (action == null) holder.actions.remove(slot);
        else holder.actions.put(slot, action);
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private boolean fits(ItemStack held, ArcaneDefinition definition) {
        if (held == null || held.getType().isAir() || plugin.items().gear(held).isEmpty()) return false;
        return definition.enchantment() != null ? plugin.items().supportsEnchantment(held, definition.enchantment())
            : definition.signature().supports(held.getType());
    }

    private String tierCount(ResearchProfile profile, ArcaneTier tier) {
        List<ArcaneDefinition> definitions = forge.registry().tier(tier);
        long known = definitions.stream().filter(definition -> profile.known(definition.id())).count();
        return "<gray>Discovered</gray> <white>" + known + "/" + definitions.size() + "</white>";
    }

    private List<String> summaryLore(ArcaneDefinition definition, ResearchProfile profile, int masteryBinds) {
        List<String> lore = new ArrayList<>();
        lore.add(definition.tier().color() + definition.tier().displayName() + "</" + closing(definition.tier().color()) + ">"
            + (definition.discipline() == null ? "" : " <dark_gray>·</dark_gray> <gray>" + definition.discipline() + "</gray>"));
        lore.add("<gray>Aspects</gray> " + ArcaneAspect.names(definition.aspects()));
        lore.add("<gray>" + definition.summary() + "</gray>");
        lore.add("<gray>Fits</gray> <white>" + definition.equipment() + "</white>");
        ResearchProfile.State state = profile.state(definition.id(), masteryBinds);
        lore.add(state == ResearchProfile.State.MASTERED ? "<gold>★ Mastered</gold>" : "<green>Discovered</green>");
        return lore;
    }

    private List<String> undiscoveredLore(ArcaneDefinition definition, ResearchProfile profile) {
        List<String> lore = new ArrayList<>();
        ResearchProfile.Entry entry = profile.entry(definition.id());
        Set<ArcaneAspect> revealed = ArcaneResearch.revealedAspects(definition, profile);
        lore.add("<gray>Needs " + definition.tier().aspectCount() + " Aspect" + (definition.tier().aspectCount() == 1 ? "" : "s")
            + " combined in Arcane Research.</gray>");
        if (entry.progress() > 0) {
            lore.add(VisualFormat.progressBar(entry.progress(), 100, 10) + " <white>" + entry.progress() + "%</white>");
        }
        if (entry.clue()) {
            lore.add("<yellow>Clue:</yellow> " + ArcaneAspect.names(definition.aspects()));
        } else if (!revealed.isEmpty()) {
            Set<ArcaneAspect> unknown = EnumSet.complementOf(EnumSet.copyOf(revealed));
            unknown.retainAll(definition.aspects());
            lore.add("<gray>Resonates with</gray> " + ArcaneAspect.names(revealed)
                + (unknown.isEmpty() ? "" : " <dark_gray>+ " + "?".repeat(unknown.size()) + "</dark_gray>"));
        } else {
            lore.add("<dark_gray>No research yet.</dark_gray>");
        }
        return lore;
    }

    private List<String> capacityLore(ArcaneForge.ItemView view) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Load</gray> <white>" + view.load() + "/" + view.capacity() + "</white>");
        lore.add("<gray>From rarity " + Chat.escape(VisualFormat.titleCase(view.rarity())) + ", item level " + view.itemLevel()
            + " and quality tier " + view.qualityTier() + ".</gray>");
        if (view.grandfathered()) {
            lore.add("<yellow>Over capacity from before Arcane Capacity existed.</yellow>");
            lore.add("<gray>Everything on it keeps working. Only new signatures need room.</gray>");
        }
        if (view.signature() != null) lore.add("<gray>Signature</gray> <light_purple>" + view.signature().displayName() + "</light_purple>");
        if (view.unknownSignature() != null) lore.add("<gray>Dormant signature from a newer build. Kept as is.</gray>");
        if (view.instability() > 0) lore.add("<yellow>Unstable " + view.instability() + "</yellow>");
        if (!view.preserved().isEmpty()) lore.add("<gray>" + view.preserved().size() + " stored entries this server can't read are kept safe.</gray>");
        return lore;
    }

    private List<String> signatureNumbers(ArcaneSignature signature, ArcaneSettings.SignatureSettings numbers) {
        return switch (signature) {
            case STORMWAKE -> List.of(
                "<gray>Static Charge:</gray> <white>" + numbers.threshold() + "</white> <gray>full-strength hits, fading after "
                    + numbers.chargeDecaySeconds() + "s idle.</gray>",
                "<gray>Arcs to</gray> <white>" + numbers.targets() + "</white> <gray>enemies within " + numbers.range() + " blocks for "
                    + (int) numbers.damagePercent() + "% of the hit, " + (int) Math.round(numbers.falloff() * 100) + "% per jump.</gray>",
                "<gray>Cooldown</gray> <white>" + numbers.cooldownSeconds() + "s</white>" + (numbers.pvp() ? "" : " <gray>· never strikes players</gray>"));
            case HEART_OF_THE_MOUNTAIN -> List.of(
                "<gray>Resonance:</gray> <white>" + numbers.threshold() + "</white> <gray>natural ores mined.</gray>",
                "<gray>Then the next natural ore drops its loot twice.</gray>",
                "<gray>Placed ore never counts.</gray>");
            case LAST_BASTION -> List.of(
                "<gray>Cooldown</gray> <white>" + numbers.cooldownSeconds() + "s</white> <gray>per player.</gray>",
                "<gray>Grants</gray> <white>" + numbers.absorptionHearts() + "</white> <gray>absorption hearts and "
                    + numbers.protectionSeconds() + "s of Resistance.</gray>",
                numbers.pvp() ? "<gray>Works against players too.</gray>" : "<gray>Does not trigger when a player lands the blow.</gray>",
                "<gray>A Totem of Undying is used first. /kill and the void cannot be survived.</gray>");
        };
    }

    private static String gemNames(ArcaneAspect aspect) {
        return ArcaneAspect.gemsFor(aspect).stream().map(GemType::displayName).sorted().reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private static String closing(String opening) {
        return opening.substring(1, opening.length() - 1).split(":", 2)[0];
    }

    private java.util.Optional<String> nextMilestone(ArcaneSettings settings, int arcane) {
        List<Map.Entry<Integer, String>> milestones = new ArrayList<>(List.of(
            Map.entry(settings.research().unlockLevel(), "Arcane Research"),
            Map.entry(settings.research().specializedLevel(), "Two-Aspect research"),
            Map.entry(settings.imbuement().levelRequirements().get(2), "Imbue level III"),
            Map.entry(settings.imbuement().levelRequirements().get(3), "Imbue level IV"),
            Map.entry(settings.research().signatureLevel(), "Arcane Signature research"),
            Map.entry(settings.imbuement().levelRequirements().get(4), "Imbue level V")));
        if (settings.overcharge().enabled()) milestones.add(Map.entry(settings.overcharge().requiredLevel(), "Overcharge"));
        return milestones.stream().filter(entry -> entry.getKey() > arcane).min(Map.Entry.comparingByKey())
            .map(entry -> "<gray>Next at Enchanting " + entry.getKey() + ":</gray> <white>" + entry.getValue() + "</white>");
    }

    private static String imbueStatus(ImbueRules.Status status) {
        return switch (status) {
            case READY -> "Ready.";
            case UNDISCOVERED -> "Not discovered yet.";
            case INCOMPATIBLE -> "Does not fit this item.";
            case NO_UPGRADE -> "Would not raise its level.";
            case NEEDS_LEVEL -> "Enchanting too low.";
        };
    }

    private static String signatureStatus(ArcaneForge.SignatureQuote quote, ArcaneSettings settings) {
        return switch (quote.status()) {
            case READY -> "Ready.";
            case DISABLED -> "Signature binding is not open on this server yet.";
            case UNDISCOVERED -> "Not discovered yet.";
            case INCOMPATIBLE -> "Does not fit this item.";
            case ALREADY_BOUND -> "Already bound to this item.";
            case NEEDS_LEVEL -> "Requires Enchanting " + quote.requiredLevel() + ".";
            case UNSTABLE -> "Stabilize this item first.";
            case CORRUPTED -> "Cleanse this item's corruption first.";
            case OVER_CAPACITY -> "Insufficient Arcane Capacity: " + quote.capacity().shortfall() + " more needed"
                + (settings.overcharge().enabled() ? " (overcharge allows " + quote.capacity().overchargeLimit() + ")." : ".");
        };
    }

    /** One sentence per outcome; never an enum name. */
    static String message(ArcaneForge.Outcome outcome) {
        return switch (outcome) {
            case APPLIED -> "Done.";
            case DISABLED -> "That part of the Arcane Forge is not open on this server.";
            case NOT_AT_STATION -> "Stand beside an enchanting table in Survival, then try again.";
            case BUSY -> "An Arcane Forge operation is already in progress.";
            case ITEM_CHANGED -> "Your held item changed. Review it again. Nothing was spent.";
            case NOT_GEAR -> "Hold one crafted, starter or relic item.";
            case UNIDENTIFIED -> "Identify that item first.";
            case UNDISCOVERED -> "You have not discovered that yet. Research it in Arcane Research.";
            case INCOMPATIBLE -> "That does not fit this item.";
            case NO_UPGRADE -> "That would change nothing, so nothing was spent.";
            case NEEDS_LEVEL -> "Your Enchanting level is too low for that.";
            case UNSTABLE -> "This item is unstable. Stabilize it first.";
            case CORRUPTED -> "This item is corrupted. Cleanse it first.";
            case OVER_CAPACITY -> "This item does not have enough Arcane Capacity.";
            case MISSING_DUST -> "You need more Arcane Dust. Nothing was spent.";
            case MISSING_GEMS -> "You are missing a required gem. Nothing was spent.";
            case SAVE_FAILED -> "Your research could not be saved, so nothing was spent. Try again shortly.";
            case RECOVERY_REQUIRED -> "Your inventory could not be restored safely. Stop using the station and contact staff for recovery.";
            case FAILED -> "The Arcane Forge could not finish that. If anything looks wrong, tell staff.";
        };
    }

    private void tell(Player player, ArcaneForge.Outcome outcome, String success) {
        if (outcome == ArcaneForge.Outcome.APPLIED) {
            if (!success.isBlank()) Chat.success(player, success);
        } else if (outcome == ArcaneForge.Outcome.ITEM_CHANGED || outcome == ArcaneForge.Outcome.BUSY) {
            Chat.notice(player, message(outcome));
        } else {
            Chat.error(player, message(outcome));
        }
    }

    /** Lists mastery progress in chat, for {@code /arcane mastery}. */
    public void showMastery(Player player) {
        if (!available(player)) return;
        ResearchProfile profile = forge.profile(player).orElseThrow();
        int masteryBinds = forge.settings().research().masteryBinds();
        Chat.send(player, "<light_purple><bold>Arcane Mastery</bold></light_purple> <gray>(" + masteryBinds
            + " binds at an enchantment's top level)</gray>");
        List<ArcaneDefinition> known = forge.registry().all().stream()
            .filter(definition -> !definition.isSignature() && profile.known(definition.id())).toList();
        if (known.isEmpty()) {
            Chat.info(player, "You have not discovered any enchantments yet. Open " + Chat.command("arcane") + " to begin.");
            return;
        }
        for (ArcaneDefinition definition : known) {
            int binds = Math.min(masteryBinds, profile.entry(definition.id()).binds());
            Chat.send(player, (binds >= masteryBinds ? "<gold>★</gold> " : "<dark_gray>·</dark_gray> ") + definition.title()
                + " <gray>" + binds + "/" + masteryBinds + "</gray>");
        }
    }
}
