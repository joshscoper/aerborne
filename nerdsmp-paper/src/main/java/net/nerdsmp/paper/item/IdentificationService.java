package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.session.SessionRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/**
 * Nyra's bench: pick what to read and what to break down, one piece at a time, with the price of it in front of you.
 * Quotes are inventory snapshots, never promises to process whatever happens to be there later.
 */
public final class IdentificationService implements Listener, AutoCloseable {
    public static final List<String> SUBCOMMANDS = List.of("held", "all", "scrap");
    private static final int SIZE = 54, TAB_READ = 2, HEADER = 4, TAB_SCRAP = 6, SELECT_SAFE = 47, CLEAR = 48, CONFIRM = 51;
    private static final int[] CARDS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    /** Rarities worth a second look: one click of Select All won't take these. */
    private static final Set<String> PRECIOUS = Set.of("EPIC", "RELIC", "LEGENDARY", "MYTHIC");
    private final NerdSmpPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final PaperItemService items;
    private final LootProcessingRules rules;
    private final LootProcessingReceipts receipts;
    private final Set<AccountId> pending = new HashSet<>();
    private final Set<AccountId> reviewRequired = new HashSet<>();
    private final NamespacedKey keptKey;
    private volatile boolean closed;

    public IdentificationService(NerdSmpPlugin plugin, SessionRegistry sessions, EconomyService economy, PaperItemService items) {
        this.plugin = plugin; this.sessions = sessions; this.economy = economy; this.items = items;
        Map<String, Integer> dust = new HashMap<>();
        LootProcessingRules.DEFAULT_DUST.forEach((rarity, amount) -> dust.put(rarity,
            plugin.getConfig().getInt("identification.scrapping.dust-by-rarity." + rarity.toLowerCase(Locale.ROOT), amount)));
        rules = new LootProcessingRules(plugin.getConfig().getLong("identification.base-cost-marks", 75),
            plugin.getConfig().getLong("identification.per-affix-cost-marks", 25),
            plugin.getConfig().getInt("identification.scrapping.dust-per-affix", 1), dust);
        receipts = new LootProcessingReceipts(new File(plugin.getDataFolder(), "item-processing"));
        keptKey = new NamespacedKey(plugin, "salvage_kept");
    }

    public void open(Player player) { open(player, null); }

    /** With no tab asked for, it opens on whatever there's something to do on. */
    private void open(Player player, Boolean scrapping) {
        AccountId account = ready(player);
        if (account == null) return;
        try {
            Menu menu = new Menu(player.getUniqueId(), account);
            menu.scrapping = scrapping != null ? scrapping : pieces(player, false).isEmpty();
            menu.inventory = Bukkit.createInventory(menu, SIZE, Chat.render("<yellow><bold>Nyra's Bench</bold></yellow>"));
            render(player, menu);
            player.openInventory(menu.inventory);
        } catch (RuntimeException failure) {
            quoteFailure(player, failure);
        }
    }

    /** What's on one tab: the backpack and hotbar slots of everything that can be worked on, in order. */
    private Map<Integer, Piece> pieces(Player player, boolean scrapping) {
        Map<Integer, Piece> found = new LinkedHashMap<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            Piece piece = scrapping ? scrapPiece(storage[slot]) : readPiece(storage[slot]);
            if (piece != null) found.put(slot, piece);
        }
        return found;
    }

    private Piece readPiece(ItemStack item) {
        var gear = items.sealedGear(item).orElse(null);
        return gear == null ? null : new Piece(gear.rarity(), rules.cost(gear.affixes()), Salvage.Yield.NOTHING, null);
    }

    /** What a piece is worth, or why it can't be broken down, or null if it isn't gear at all. */
    private Piece scrapPiece(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        var sealed = items.sealedGear(item).orElse(null);
        if (sealed != null) return new Piece(sealed.rarity(), 0, dust(sealed.rarity(), sealed.affixes()), kept(item) ? "Protected" : null);
        var gear = items.gear(item).orElse(null);
        if (gear != null) {
            if (gear.slot().name().equals("NONE") || !LootProcessingRules.DEFAULT_DUST.containsKey(gear.rarity())) return null;
            String blocked = kept(item) ? "Protected" : !items.sockets(item).isEmpty() ? "Take its gems out first" : item.getAmount() != 1 ? "Split the stack first" : null;
            return new Piece(gear.rarity(), 0, dust(gear.rarity(), items.affixes(item).size()), blocked);
        }
        if (items.describe(item).isPresent() || !Salvage.gear(item.getType())) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !ordinary(meta)) return null;
        Salvage.Yield worth = Salvage.vanilla(item.getType(), meta == null ? 0 : meta.getEnchants().values().stream().mapToInt(Integer::intValue).sum(), condition(item, meta));
        if (worth.worthless()) return null;
        return new Piece("COMMON", 0, worth, kept(item) ? "Protected" : item.getAmount() != 1 ? "Split the stack first" : null);
    }

    /** Quest tokens and the like are built on vanilla gear too. Tooltips and a protection mark are all an ordinary piece carries. */
    private boolean ordinary(ItemMeta meta) {
        return meta.getPersistentDataContainer().getKeys().stream().allMatch(key -> key.equals(keptKey) || key.getKey().startsWith("item_template"));
    }

    private Salvage.Yield dust(String rarity, int affixes) { return new Salvage.Yield(rules.dust(rarity, affixes), null, 0); }

    private static double condition(ItemStack item, ItemMeta meta) {
        int most = item.getType().getMaxDurability();
        return most <= 0 || !(meta instanceof Damageable worn) ? 1 : 1 - worn.getDamage() / (double) most;
    }

    private boolean kept(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keptKey, PersistentDataType.BYTE);
    }

    private void toggleKept(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (kept(item)) meta.getPersistentDataContainer().remove(keptKey);
        else meta.getPersistentDataContainer().set(keptKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private void render(Player player, Menu menu) {
        MenuKit.frame(menu.inventory);
        Map<Integer, Piece> pieces = pieces(player, menu.scrapping);
        menu.selected.retainAll(pieces.keySet());
        List<Integer> slots = new ArrayList<>(pieces.keySet());
        int pages = Math.max(1, (slots.size() + CARDS.length - 1) / CARDS.length);
        menu.page = Math.clamp(menu.page, 0, pages - 1);
        menu.cards.clear();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int index = 0; index < CARDS.length; index++) {
            int at = menu.page * CARDS.length + index;
            if (at >= slots.size()) break;
            int slot = slots.get(at);
            menu.inventory.setItem(CARDS[index], card(storage[slot], pieces.get(slot), menu.selected.contains(slot), menu.scrapping));
            menu.cards.put(CARDS[index], slot);
        }
        long marks = 0, precious = 0;
        int dust = 0;
        Map<Material, Integer> materials = new TreeMap<>();
        for (int slot : menu.selected) {
            Piece piece = pieces.get(slot);
            marks += piece.marks();
            dust += piece.yield().dust();
            if (piece.yield().material() != null) materials.merge(piece.yield().material(), piece.yield().amount(), Integer::sum);
            if (PRECIOUS.contains(piece.rarity())) precious++;
        }
        menu.inventory.setItem(TAB_READ, MenuKit.item(Material.SPYGLASS, (menu.scrapping ? "<gray>" : "<yellow><bold>") + "Identify" + (menu.scrapping ? "</gray>" : "</bold></yellow>"),
            List.of("<gray>Read a sealed piece, so it can be worn.</gray>")));
        menu.inventory.setItem(TAB_SCRAP, MenuKit.item(Material.GRINDSTONE, (menu.scrapping ? "<red><bold>" : "<gray>") + "Scrap" + (menu.scrapping ? "</bold></red>" : "</gray>"),
            List.of("<gray>Break down gear you don't want,</gray>", "<gray>sealed or not.</gray>")));
        List<String> header = new ArrayList<>(List.of("<gray>" + pieces.size() + (menu.scrapping ? " pieces of gear" : " sealed pieces") + " in your backpack and hotbar.</gray>",
            "<gray>What you're wearing is never touched.</gray>", ""));
        header.add(menu.scrapping ? "<gray>Left-click to pick, right-click to protect.</gray>" : "<gray>Left-click to pick.</gray>");
        menu.inventory.setItem(HEADER, MenuKit.item(Material.WRITABLE_BOOK, "<yellow>" + (menu.scrapping ? "Scrap" : "Identify") + "</yellow>", header));
        menu.inventory.setItem(SELECT_SAFE, MenuKit.item(Material.LIME_DYE, "<green>Select All</green>", menu.scrapping
            ? List.of("<gray>Everything up to Rare.</gray>", "<gray>Shift-click to take Epic and better too.</gray>") : List.of("<gray>Every sealed piece.</gray>")));
        menu.inventory.setItem(CLEAR, MenuKit.item(Material.GRAY_DYE, "<gray>Clear Selection</gray>", List.of()));
        List<String> total = new ArrayList<>(List.of("<gray>Selected</gray> <white>" + menu.selected.size() + "</white>"));
        if (menu.scrapping) {
            total.add("<gray>Receive</gray> <light_purple>" + dust + " Arcane Dust</light_purple>");
            materials.forEach((material, amount) -> total.add("<gray>Receive</gray> <white>" + amount + " " + plain(material) + "</white>"));
            if (precious > 0) total.add("<red>Includes " + precious + " Epic or better.</red>");
        } else total.add("<gray>Cost</gray> " + Chat.marksTag(marks));
        if (menu.selected.isEmpty()) menu.inventory.setItem(CONFIRM, MenuKit.locked(Material.ANVIL, menu.scrapping ? "Scrap Selected" : "Identify Selected", List.of("<gray>Pick something first.</gray>")));
        else menu.inventory.setItem(CONFIRM, MenuKit.item(menu.scrapping ? Material.GRINDSTONE : Material.ENDER_EYE, (menu.scrapping ? "<red>Scrap Selected</red>" : "<yellow>Identify Selected</yellow>"), MenuKit.withHint(total, "review")));
        if (menu.page > 0) menu.inventory.setItem(MenuKit.previousSlot(SIZE), MenuKit.previous(menu.page + 1, pages));
        if (menu.page < pages - 1) menu.inventory.setItem(MenuKit.nextSlot(SIZE), MenuKit.next(menu.page + 1, pages));
        menu.inventory.setItem(MenuKit.closeSlot(SIZE), MenuKit.close());
    }

    /** The piece itself, with what it costs or pays, whether it's picked and why it can't be. */
    private ItemStack card(ItemStack item, Piece piece, boolean selected, boolean scrapping) {
        ItemStack card = item.clone();
        ItemMeta meta = card.getItemMeta();
        if (meta == null) return card;
        List<Component> lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.lore()) : List.of());
        lore.add(Component.empty());
        if (scrapping) {
            if (piece.yield().dust() > 0) lore.add(line("<gray>Scraps for</gray> <light_purple>" + piece.yield().dust() + " Arcane Dust</light_purple>"));
            if (piece.yield().material() != null) lore.add(line("<gray>Scraps for</gray> <white>" + piece.yield().amount() + " " + plain(piece.yield().material()) + "</white>"));
        } else lore.add(line("<gray>Costs</gray> " + Chat.marksTag(piece.marks())));
        if (piece.blocked() != null) lore.add(line("<red>" + piece.blocked() + "</red>"));
        else if (selected) lore.add(line("<green><bold>Selected</bold></green>"));
        meta.lore(lore);
        if (selected) meta.setEnchantmentGlintOverride(true);
        card.setItemMeta(meta);
        return card;
    }

    private static Component line(String markup) {
        return Chat.render(markup).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private static String plain(Material material) {
        StringBuilder name = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return name.toString();
    }

    /** Asks for a look at the batch, then does it. */
    private void review(Player player, Menu menu) {
        if (menu.selected.isEmpty()) return;
        Set<Integer> chosen = Set.copyOf(menu.selected);
        String action = menu.scrapping ? "scrap" : "all";
        openReview(player, menu.account, action, chosen);
    }

    /** Command shortcuts still open the same explicit confirmation as Nyra's menu. */
    public void openAction(Player player, String action) {
        AccountId account = ready(player);
        if (account == null) return;
        if (!SUBCOMMANDS.contains(action)) { Chat.usage(player, "/identify [held|all|scrap]"); return; }
        // There's no scrapping everything at a stroke any more: it opens the bench, where you can see what you're breaking.
        if (action.equals("scrap")) { open(player, true); return; }
        openReview(player, account, action, null);
    }

    private void openReview(Player player, AccountId account, String action, Set<Integer> chosen) {
        try {
            BatchQuote quote = quote(action, chosen, player.getInventory().getStorageContents(), player.getInventory().getHeldItemSlot());
            if (quote.count == 0) {
                Chat.notice(player, action.equals("held") ? "Hold a sealed piece of equipment in your main hand."
                    : "You have no sealed equipment in your backpack or hotbar.");
                return;
            }
            if (quote.plan == null) { Chat.error(player, "Make room for what you'd get back before scrapping this."); return; }
            boolean scrap = action.equals("scrap");
            List<String> summary = new ArrayList<>();
            summary.add("<gray>" + (scrap ? "Gear" : "Sealed equipment") + "</gray> <white>" + quote.count + "</white>");
            quote.rarities.forEach((rarity, count) -> summary.add("<gray>" + Chat.escape(title(rarity)) + "</gray> <white>" + count + "</white>"));
            summary.add("");
            summary.add(scrap ? "<red>These items will be destroyed. This cannot be undone.</red>"
                : "<gray>Reveals the existing affixes, sockets and item names.</gray>");
            List<String> detail = new ArrayList<>();
            if (scrap) {
                if (quote.dust > 0) detail.add("<gray>Receive</gray> <light_purple>" + quote.dust + " Arcane Dust</light_purple>");
                quote.materials.forEach((material, amount) -> detail.add("<gray>Receive</gray> <white>" + amount + " " + plain(material) + "</white>"));
                detail.add("<red>Destroys all " + quote.count + " quoted items.</red>");
                detail.add("<gray>No Marks are spent.</gray>");
            } else {
                detail.add("<gray>Items</gray> <white>" + quote.count + "</white>");
                detail.add("<gray>Cost</gray> " + Chat.marksTag(quote.marks));
                detail.add("<gray>One payment for the whole batch.</gray>");
            }
            MenuKit.openConfirm(plugin, player, scrap ? "<red><bold>Scrap Gear</bold></red>"
                    : "<yellow><bold>Arcane Identification</bold></yellow>",
                MenuKit.item(scrap ? Material.GRINDSTONE : Material.SPYGLASS, scrap ? "<red>Scrap " + quote.count + " items</red>"
                    : "<yellow>Identify " + quote.count + " items</yellow>", summary), detail,
                confirmed -> process(confirmed, account, quote), back -> open(back, scrap));
        } catch (RuntimeException failure) {
            quoteFailure(player, failure);
        }
    }

    public long cost(ItemStack item) { return rules.cost(items.affixes(item).size()); }

    /** {@code chosen} limits it to those slots; null means every slot the action can use. */
    private BatchQuote quote(String action, Set<Integer> chosen, ItemStack[] contents, int heldSlot) {
        if (!SUBCOMMANDS.contains(action) || contents.length != 36 || heldSlot < 0 || heldSlot > 8)
            throw new IllegalArgumentException("Invalid item-processing request");
        List<ItemStack> before = copy(Arrays.asList(contents)), after = copy(before);
        Map<UUID, Integer> identities = new HashMap<>();
        for (ItemStack item : before) if (items.isAuthentic(item))
            items.lootInstance(item).ifPresent(id -> identities.merge(id, 1, Integer::sum));
        Map<String, Integer> rarities = new TreeMap<>();
        List<String> instances = new ArrayList<>();
        Map<Material, Integer> materials = new TreeMap<>();
        long marks = 0; int dust = 0;
        for (int slot = 0; slot < before.size(); slot++) {
            if (action.equals("held") && slot != heldSlot || chosen != null && !chosen.contains(slot)) continue;
            ItemStack original = before.get(slot);
            Piece piece = action.equals("scrap") ? scrapPiece(original) : readPiece(original);
            if (piece == null || piece.blocked() != null) continue;
            UUID identity = items.isAuthentic(original) ? items.lootInstance(original).orElse(null) : null;
            if (identity != null && identities.getOrDefault(identity, 0) != 1) throw new IllegalStateException("Duplicate loot identity in batch");
            // Ordinary gear has no loot identity of its own; the receipt still needs one per item.
            instances.add((identity == null ? UUID.randomUUID() : identity).toString()); rarities.merge(piece.rarity(), 1, Integer::sum);
            if (action.equals("scrap")) {
                dust = Math.addExact(dust, piece.yield().dust());
                if (piece.yield().material() != null) materials.merge(piece.yield().material(), piece.yield().amount(), Integer::sum);
                after.set(slot, null);
            } else {
                marks = Math.addExact(marks, piece.marks());
                if (!items.identify(after.get(slot))) throw new IllegalStateException("Unable to prepare sealed item");
            }
        }
        boolean room = dust == 0 || insert(after, plugin.enchanting().createArcaneDust(1), dust);
        for (var entry : materials.entrySet()) room = room && insert(after, new ItemStack(entry.getKey()), entry.getValue());
        InventoryBatchPlan<ItemStack> plan = instances.isEmpty() || !room ? null : new InventoryBatchPlan<>(before, after);
        return new BatchQuote(action, before, after, instances.size(), marks, dust, Map.copyOf(materials), Map.copyOf(rarities), List.copyOf(instances), plan);
    }

    private boolean insert(List<ItemStack> storage, ItemStack template, int amount) {
        int remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int slot = 0; slot < storage.size() && remaining > 0; slot++) {
            ItemStack current = storage.get(slot);
            if (pass == 0 && (current == null || !current.isSimilar(template)) || pass == 1 && current != null) continue;
            int held = current == null ? 0 : current.getAmount();
            int maximum = current == null ? template.getMaxStackSize() : Math.min(current.getMaxStackSize(), template.getMaxStackSize());
            int moved = Math.min(remaining, Math.max(0, maximum - held));
            if (moved == 0) continue;
            ItemStack next = current == null ? template.clone() : current.clone(); next.setAmount(held + moved);
            storage.set(slot, next); remaining -= moved;
        }
        return remaining == 0;
    }

    private void process(Player player, AccountId account, BatchQuote quote) {
        if (!session(player, account) || ready(player) == null) return;
        if (quote.plan == null || !quote.plan.matches(storage(player))) {
            Chat.notice(player, "Your inventory changed. Review the batch again. Nothing was spent."); return;
        }
        if (!pending.add(account)) { Chat.info(player, "Your items are already being processed."); return; }
        LootProcessingReceipts.Receipt receipt;
        try {
            receipt = new LootProcessingReceipts.Receipt(UUID.randomUUID(), account, quote.action, quote.marks, quote.dust,
                quote.count, Instant.now(), quote.instances, encode(quote.before), encode(quote.after), LootProcessingReceipts.Phase.PREPARED);
            receipts.begin(receipt);
        } catch (RuntimeException failure) { hold(player, account, null, failure); return; }
        if (quote.marks == 0) { apply(player, quote, receipt); return; }
        CompletionStage<EconomyService.TransactionResult> debit;
        try {
            debit = economy.debit(account, quote.marks, new EconomyService.TransactionReason("item.identify", quote.count + " sealed items"),
                paymentKey(receipt));
            if (debit == null) throw new IllegalStateException("Identification debit returned no stage");
        } catch (RuntimeException failure) { hold(player, account, receipt, failure); return; }
        debit.whenComplete((result, failure) -> sync(() -> {
            if (failure != null || result == null) { hold(player, account, receipt, failure); return; }
            if (result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS) {
                if (finish(player, receipt, LootProcessingReceipts.Phase.DECLINED))
                    tell(player, "You need " + Chat.marksTag(quote.marks) + " to identify this batch.", true);
            } else if (applied(result)) apply(player, quote, receipt);
            else hold(player, account, receipt, null);
        }));
    }

    private void apply(Player player, BatchQuote quote, LootProcessingReceipts.Receipt prepared) {
        if (!session(player, prepared.account()) || !quote.plan.matches(storage(player))) { refund(player, prepared); return; }
        try { receipts.advance(prepared, LootProcessingReceipts.Phase.APPLYING); }
        catch (RuntimeException failure) { hold(player, prepared.account(), prepared, failure); return; }
        var applying = prepared.at(LootProcessingReceipts.Phase.APPLYING);
        InventoryBatchPlan.Result result = quote.plan.apply(() -> storage(player), next -> player.getInventory().setStorageContents(copy(next).toArray(ItemStack[]::new)));
        if (result == InventoryBatchPlan.Result.INPUT_CHANGED || result == InventoryBatchPlan.Result.UNAVAILABLE) { refund(player, applying); return; }
        if (result != InventoryBatchPlan.Result.APPLIED) { hold(player, prepared.account(), applying, null); return; }
        try {
            // Save the native inventory before marking the receipt complete. An interruption
            // in this window stays pending for staff review; it is never blindly replayed.
            player.saveData();
        } catch (RuntimeException failure) { hold(player, prepared.account(), applying, failure); return; }
        if (!finish(player, applying, LootProcessingReceipts.Phase.COMPLETE)) return;
        plugin.gearSets().refreshInventory(player); plugin.presentation().refresh(player);
        player.playSound(player.getLocation(), quote.action.equals("scrap") ? Sound.BLOCK_GRINDSTONE_USE : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
        StringBuilder gained = new StringBuilder(quote.dust > 0 ? "<light_purple>" + quote.dust + " Arcane Dust</light_purple>" : "");
        quote.materials.forEach((material, amount) -> gained.append(gained.isEmpty() ? "" : ", ").append("<white>" + amount + " " + plain(material) + "</white>"));
        Chat.success(player, quote.action.equals("scrap") ? "Scrapped " + quote.count + (quote.count == 1 ? " item" : " items") + " for " + gained + "."
            : "Identified " + quote.count + (quote.count == 1 ? " item" : " items") + " for " + Chat.marksTag(quote.marks) + ".");
        if (!quote.action.equals("scrap")) reveal(player, quote);
        plugin.getLogger().info("Item processing complete: " + prepared.operation() + " account=" + prepared.account()
            + " action=" + quote.action + " items=" + quote.count + " marks=" + quote.marks + " dust=" + quote.dust);
    }

    /** Nyra reads out what she found. Hover a name to see the piece. */
    private void reveal(Player player, BatchQuote quote) {
        int shown = 0;
        for (int slot = 0; slot < quote.after.size() && shown < 8; slot++) {
            ItemStack now = quote.after.get(slot), was = quote.before.get(slot);
            if (now == null || was == null || now.equals(was) || items.gear(now).isEmpty()) continue;
            shown++;
            player.sendMessage(Component.text("  ").append(now.displayName().hoverEvent(now.asHoverEvent())));
        }
        if (quote.count > shown) Chat.send(player, "<dark_gray>  and " + (quote.count - shown) + " more.</dark_gray>");
    }

    private void refund(Player player, LootProcessingReceipts.Receipt receipt) {
        if (receipt.marks() == 0) {
            if (finish(player, receipt, LootProcessingReceipts.Phase.REFUNDED)) tell(player, "Your inventory or session changed. Nothing was processed.", false);
            return;
        }
        CompletionStage<EconomyService.TransactionResult> refund;
        try {
            refund = economy.credit(receipt.account(), receipt.marks(),
                new EconomyService.TransactionReason("item.identify.refund", "Inventory or session changed before identification"), paymentKey(receipt) + ":refund");
            if (refund == null) throw new IllegalStateException("Identification refund returned no stage");
        } catch (RuntimeException failure) { hold(player, receipt.account(), receipt, failure); return; }
        refund.whenComplete((result, failure) -> sync(() -> {
            if (failure != null || !applied(result)) { hold(player, receipt.account(), receipt, failure); return; }
            if (finish(player, receipt, LootProcessingReceipts.Phase.REFUNDED))
                tell(player, "Your inventory or session changed. Returned " + Chat.marksTag(receipt.marks()) + ".", false);
        }));
    }

    private boolean finish(Player player, LootProcessingReceipts.Receipt receipt, LootProcessingReceipts.Phase phase) {
        try { receipts.advance(receipt, phase); pending.remove(receipt.account()); return true; }
        catch (RuntimeException failure) { hold(player, receipt.account(), receipt, failure); return false; }
    }

    private AccountId ready(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) { Chat.accountLoading(player); return null; }
        if (!session(player, account)) { Chat.error(player, "Use Survival or Adventure mode to process equipment."); return null; }
        if (pending.contains(account)) { Chat.info(player, "Your items are still being processed."); return null; }
        try {
            if (reviewRequired.contains(account) || receipts.find(account).filter(LootProcessingReceipts.Receipt::pending).isPresent()) {
                Chat.error(player, "Your last item batch needs a staff review before you can process more gear."); return null;
            }
        } catch (RuntimeException failure) { hold(player, account, null, failure); return null; }
        return account;
    }

    private void hold(Player player, AccountId account, LootProcessingReceipts.Receipt receipt, Throwable failure) {
        pending.remove(account); reviewRequired.add(account);
        plugin.getLogger().log(Level.SEVERE, "Item processing held for review; do not replay or automatically refund: account=" + account
            + " operation=" + (receipt == null ? "unprepared" : receipt.operation()), failure);
        tell(player, "That item batch needs a staff review. Please do not retry it yet.", true);
    }

    private void quoteFailure(Player player, RuntimeException failure) {
        plugin.getLogger().log(Level.WARNING, "Cannot prepare sealed-item batch for " + player.getUniqueId(), failure);
        Chat.error(player, "Some sealed equipment could not be checked. Ask staff to inspect it; nothing was spent.");
    }

    private void tell(Player player, String message, boolean error) {
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) return;
        if (error) Chat.error(player, message); else Chat.notice(player, message);
    }

    private boolean session(Player player, AccountId account) {
        return !closed && player.isOnline() && !player.isDead() && Bukkit.getPlayer(player.getUniqueId()) == player
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && sessions.account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    private void sync(Runnable task) {
        if (closed || !plugin.isEnabled()) return; // The durable pending receipt remains for review on restart.
        if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) task.run(); });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean canceled = event.isCancelled(); event.setCancelled(true);
        if (canceled || !(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(menu.owner)
            || event.getClickedInventory() != menu.inventory || !session(player, menu.account)) return;
        int slot = event.getRawSlot();
        Integer card = menu.cards.get(slot);
        if (card != null) {
            pick(player, menu, card, event.getClick());
        } else if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
            if (slot == MenuKit.closeSlot(SIZE)) { player.closeInventory(); return; }
            if (slot == CONFIRM) {
                if (menu.selected.isEmpty()) return;
                MenuKit.clickSound(player);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!session(player, menu.account) || player.getOpenInventory().getTopInventory() != menu.inventory) return;
                    player.closeInventory();
                    review(player, menu);
                });
                return;
            }
            if (slot == TAB_READ || slot == TAB_SCRAP) {
                menu.scrapping = slot == TAB_SCRAP;
                menu.selected.clear();
                menu.page = 0;
            } else if (slot == CLEAR) menu.selected.clear();
            else if (slot == SELECT_SAFE) pieces(player, menu.scrapping).forEach((at, piece) -> {
                if (piece.blocked() == null && (event.getClick() == ClickType.SHIFT_LEFT || !menu.scrapping || !PRECIOUS.contains(piece.rarity()))) menu.selected.add(at);
            });
            else if (slot == MenuKit.previousSlot(SIZE)) menu.page--;
            else if (slot == MenuKit.nextSlot(SIZE)) menu.page++;
            else return;
        } else return;
        MenuKit.clickSound(player);
        render(player, menu);
    }

    /** Left-click picks a piece, right-click protects it from the scrap tab for good, until right-clicked again. */
    private void pick(Player player, Menu menu, int slot, ClickType click) {
        ItemStack real = player.getInventory().getItem(slot);
        if (real == null) return;
        if (click == ClickType.RIGHT && menu.scrapping) {
            toggleKept(real);
            player.getInventory().setItem(slot, real);
            menu.selected.remove(slot);
            return;
        }
        if (click != ClickType.LEFT) return;
        Piece piece = menu.scrapping ? scrapPiece(real) : readPiece(real);
        if (piece == null || piece.blocked() != null) return;
        if (!menu.selected.remove(slot)) menu.selected.add(slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @Override public void close() { closed = true; }
    private static boolean applied(EconomyService.TransactionResult result) {
        return result != null && (result.status() == EconomyService.TransactionResult.Status.APPLIED || result.status() == EconomyService.TransactionResult.Status.DUPLICATE);
    }
    private static String paymentKey(LootProcessingReceipts.Receipt receipt) { return "identify:" + receipt.operation(); }
    private static String title(String text) { return text.substring(0, 1) + text.substring(1).toLowerCase(Locale.ROOT); }
    private static List<ItemStack> storage(Player player) { return copy(Arrays.asList(player.getInventory().getStorageContents())); }
    private static List<ItemStack> copy(List<ItemStack> contents) {
        List<ItemStack> result = new ArrayList<>(contents.size());
        for (ItemStack item : contents) result.add(item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item.clone());
        return result;
    }
    private static List<String> encode(List<ItemStack> contents) {
        return contents.stream().map(item -> item == null ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes())).toList();
    }
    private record BatchQuote(String action, List<ItemStack> before, List<ItemStack> after, int count, long marks, int dust, Map<Material, Integer> materials,
                              Map<String, Integer> rarities, List<String> instances, InventoryBatchPlan<ItemStack> plan) { }
    /** One thing on the bench: what it's worth on its tab, and why it can't be picked, if it can't. */
    private record Piece(String rarity, long marks, Salvage.Yield yield, String blocked) { }
    private static final class Menu implements InventoryHolder {
        final UUID owner; final AccountId account; final Set<Integer> selected = new TreeSet<>(); final Map<Integer, Integer> cards = new HashMap<>();
        Inventory inventory; boolean scrapping; int page;
        Menu(UUID owner, AccountId account) { this.owner = owner; this.account = account; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
