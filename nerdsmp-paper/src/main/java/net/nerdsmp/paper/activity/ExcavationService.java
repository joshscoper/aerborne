package net.nerdsmp.paper.activity;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.achievement.AchievementMetric;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GeodeGrade;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.resource.HarvestClaims;
import net.nerdsmp.paper.resource.ResourceMaterials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ExcavationService implements Listener {
    private static final FactionId RIFT_SCHOLARS = new FactionId("rift_scholars");
    private final NerdSmpPlugin plugin;
    private final SkillListener skills;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, DigState> states = new ConcurrentHashMap<>();
    private final Set<UUID> archivePending = ConcurrentHashMap.newKeySet();

    public ExcavationService(NerdSmpPlugin plugin, SkillListener skills) {
        this.plugin = plugin;
        this.skills = skills;
    }

    public void open(Player player) {
        ExcavationHolder holder = new ExcavationHolder();
        Inventory inventory = Bukkit.createInventory(holder, 45,
            miniMessage.deserialize("<yellow><bold>Excavation Field Notes</bold></yellow>"));
        holder.inventory = inventory;
        decorate(inventory);
        DigState state = states.getOrDefault(player.getUniqueId(), new DigState(0, 0, 0, Instant.EPOCH));
        int level = level(player);
        double find = findBonus(player);
        inventory.setItem(4, icon(Material.BRUSH, "<yellow><bold>Excavation " + level + "</bold></yellow>", List.of(
            "<gray>Survey chain</gray> <white>" + state.chain + "</white>",
            "<gray>Find bonus</gray> <green>+" + Math.round(find) + "%</green>",
            "<gray>Finds</gray> <white>" + state.finds + "</white> <dark_gray>•</dark_gray> <gold>"
                + state.legendary + " legendary</gold>",
            "<dark_gray>Elian records archive entries only when you deposit them.</dark_gray>"
        )));
        inventory.setItem(11, icon(Material.BRICK, "<white>Surface Finds</white>", List.of(
            "<gray>Bricks, flint, and pottery sherds.</gray>",
            "<dark_gray>Practical supplies with a chance at archive pieces.</dark_gray>"
        )));
        inventory.setItem(13, icon(Material.COPPER_INGOT,
            "<green>Buried Materials</green>", List.of(
                "<gray>Copper, amethyst, and old gold coinage.</gray>",
                "<dark_gray>Useful materials recovered in small bundles.</dark_gray>"
            )));
        inventory.setItem(15, icon(Material.ECHO_SHARD,
            "<aqua>Rare Finds</aqua>", List.of(
                "<gray>Relic records, echo shards, and resonant geodes.</gray>",
                "<dark_gray>Keep them for crafting or bring records to the archive.</dark_gray>"
            )));
        inventory.setItem(22, icon(Material.SNIFFER_EGG,
            "<gold>Mythic Discoveries</gold>", List.of(
                "<gray>Sniffer eggs, luminous geodes, and lost tomes.</gray>",
                "<dark_gray>Every result has a use beyond filling a collection.</dark_gray>"
            )));
        inventory.setItem(31, icon(Material.COMPASS, "<light_purple>Survey Chains</light_purple>", List.of(
            "<gray>Keep digging natural terrain to build a chain.</gray>",
            "<gray>Chains expire after eight seconds without a dig.</gray>",
            "<dark_gray>Placed blocks never produce finds.</dark_gray>"
        )));
        inventory.setItem(29, icon(Material.FILLED_MAP, "<aqua>Dig Sites</aqua>", MenuKit.withHint(List.of(
            "<gray>Find marked soil that regrows after digging.</gray>",
            "<gray>Excavation power on shovels improves your XP.</gray>"), "see open sites and directions")));
        inventory.setItem(33, icon(Material.WRITABLE_BOOK, "<aqua>Archive Collection</aqua>", MenuKit.withHint(List.of(
            "<gray>View each find and its mastery reward.</gray>",
            "<dark_gray>Bring your finds to Elian to record them.</dark_gray>"), "open")));
        inventory.setItem(MenuKit.backSlot(45), MenuKit.back("Adventure"));
        inventory.setItem(MenuKit.closeSlot(45), MenuKit.close());
        player.openInventory(inventory);
    }

    public void openArchive(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        plugin.reputation().value(account, RIFT_SCHOLARS).whenComplete((standing, error) -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                player.sendRichMessage("<red>The archive ledger could not be opened.</red>");
                return;
            }
            openArchiveInventory(player, standing);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!ResourceMaterials.excavation(block.getType().name())) return;
        var harvest = plugin.resources().claim(event, HarvestClaims.Channel.EXCAVATION);
        if (harvest.isEmpty()) return;
        Player player = event.getPlayer();
        DigState state = advance(player);
        double chance = ExcavationRolls.findChance(level(player), findBonus(player), state.chain);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            if (state.chain > 0 && state.chain % 10 == 0) {
                player.sendActionBar(miniMessage.deserialize("<yellow>Survey chain " + state.chain + "</yellow> "
                    + "<dark_gray>• soil is looking promising</dark_gray>"));
            }
            return;
        }
        FishingRolls.Rarity rarity = ExcavationRolls.rarity(level(player), state.chain,
            ThreadLocalRandom.current().nextDouble());
        ItemStack reward = reward(player, rarity);
        block.getWorld().dropItemNaturally(block.getLocation(), reward);
        states.computeIfPresent(player.getUniqueId(), (ignored, current) -> new DigState(current.chain,
            current.finds + 1, current.legendary + (rarity == FishingRolls.Rarity.LEGENDARY ? 1 : 0), current.lastDig));
        long bonusExperience = switch (rarity) {
            case COMMON -> 6L;
            case UNCOMMON -> 18L;
            case RARE -> 55L;
            case LEGENDARY -> 150L;
        };
        skills.awardActivity(player, Skill.EXCAVATION, bonusExperience, "excavation-find:" + harvest.get().operationId());
        plugin.achievements().record(player, AchievementMetric.EXCAVATION_FINDS, 1L);
        String color = switch (rarity) {
            case COMMON -> "gray";
            case UNCOMMON -> "green";
            case RARE -> "aqua";
            case LEGENDARY -> "gold";
        };
        ItemMeta rewardMeta = reward.getItemMeta();
        Component rewardName = rewardMeta.hasItemName()
            ? rewardMeta.itemName() : Component.translatable(reward.translationKey());
        player.sendActionBar(miniMessage.deserialize("<" + color + ">" + rarity.name() + " FIND</" + color
                + "> <dark_gray>•</dark_gray> ")
            .append(rewardName)
            .append(miniMessage.deserialize(" <dark_gray>•</dark_gray> <white>Chain " + state.chain + "</white>")));
        player.playSound(player.getLocation(), rarity == FishingRolls.Rarity.LEGENDARY
            ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_GRAVEL_BREAK, 0.9F, 1.0F + rarity.ordinal() * 0.12F);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ArchiveCollectionHolder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                int slot = event.getRawSlot();
                if (slot == MenuKit.previousSlot(54) || slot == MenuKit.nextSlot(54) || slot == MenuKit.backSlot(54)) {
                    MenuKit.clickSound(player);
                }
                if (slot == MenuKit.previousSlot(54) && holder.page > 0) {
                    openCollection(player, holder.page - 1, holder.atArchive);
                } else if (slot == MenuKit.nextSlot(54) && holder.page < holder.lastPage) {
                    openCollection(player, holder.page + 1, holder.atArchive);
                } else if (slot == MenuKit.closeSlot(54)) {
                    player.closeInventory();
                } else if (slot == MenuKit.backSlot(54)) {
                    if (holder.atArchive) {
                        openArchive(player);
                    } else {
                        open(player);
                    }
                }
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ExcavationHolder holder) {
            boolean cancelled = event.isCancelled();
            event.setCancelled(true);
            if (cancelled || event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                || !(event.getWhoClicked() instanceof Player player)) return;
            int slot = event.getRawSlot();
            if (slot != 29 && slot != 33 && slot != MenuKit.backSlot(45) && slot != MenuKit.closeSlot(45)) return;
            if (slot != MenuKit.closeSlot(45)) MenuKit.clickSound(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
                if (slot == MenuKit.closeSlot(45)) player.closeInventory();
                else if (slot == MenuKit.backSlot(45)) plugin.menus().open(player, MenuManager.View.ADVENTURE);
                else if (slot == 33) openCollection(player, 0, false);
                else { player.closeInventory(); plugin.digSiteCommands().show(player); }
            });
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ArchiveHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != org.bukkit.event.inventory.ClickType.LEFT) {
                return;
            }
            if (event.getRawSlot() == MenuKit.closeSlot(45)) {
                player.closeInventory();
            } else if (event.getRawSlot() == 31 && holder.eligible > 0) {
                MenuKit.clickSound(player);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory() == holder.inventory) confirmArchive(player, holder);
                });
            } else if (event.getRawSlot() == 29) {
                MenuKit.clickSound(player);
                openCollection(player, 0, true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ExcavationHolder || holder instanceof ArchiveHolder
            || holder instanceof ArchiveCollectionHolder) {
            event.setCancelled(true);
        }
    }

    private void openArchiveInventory(Player player, int standing) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) {
            return;
        }
        ArchiveQuote quote = archiveQuote(player, account, Math.max(0, ReputationService.MAXIMUM - standing));
        ArchiveHolder holder = new ArchiveHolder();
        holder.account = account;
        holder.standing = standing;
        holder.eligible = quote.items;
        holder.reputation = quote.reputation;
        Inventory inventory = Bukkit.createInventory(holder, 45,
            miniMessage.deserialize("<aqua><bold>Field Archive</bold></aqua>"));
        holder.inventory = inventory;
        decorate(inventory);
        inventory.setItem(4, icon(Material.LECTERN, "<aqua><bold>Elian's Appraisal</bold></aqua>", List.of(
            "<gray>Rift Scholar standing</gray> <white>" + standing + "/" + ReputationService.MAXIMUM + "</white>",
            "<gray>Eligible items</gray> <white>" + quote.items + "</white>",
            "<gray>Appraisal</gray> <aqua>+" + quote.reputation + " reputation</aqua>",
            "<gray>Collection</gray> <white>" + plugin.collections().recordedCount(account,
                CollectionMasteryService.Category.EXCAVATION) + " recorded</white> <dark_gray>•</dark_gray> <gold>"
                + plugin.collections().masteredCount(account, CollectionMasteryService.Category.EXCAVATION)
                + " mastered</gold>"
        )));
        inventory.setItem(11, icon(Material.MINER_POTTERY_SHERD, "<white>Pottery Sherds</white>", List.of(
            "<gray>Any pottery sherd</gray> <aqua>+2 reputation each</aqua>",
            "<dark_gray>The archive accepts intact inscriptions.</dark_gray>"
        )));
        inventory.setItem(13, icon(Material.MUSIC_DISC_RELIC, "<aqua>Relic Records</aqua>", List.of(
            "<gray>Relic music discs</gray> <aqua>+10 reputation each</aqua>",
            "<dark_gray>Play them, collect them, or submit them here.</dark_gray>"
        )));
        inventory.setItem(15, icon(Material.SNIFFER_EGG, "<gold>Living History</gold>", List.of(
            "<gray>Sniffer eggs</gray> <aqua>+12 reputation each</aqua>",
            "<dark_gray>Hatch them or entrust them to the scholars.</dark_gray>"
        )));
        List<String> submitLore = quote.items == 0
            ? List.of("<gray>No eligible finds are in your inventory.</gray>",
                "<dark_gray>Bring pottery sherds, relic records, or sniffer eggs.</dark_gray>")
            : List.of("<gray>Deposit</gray> <white>" + quote.items + " items</white>",
                "<gray>Receive</gray> <aqua>+" + quote.reputation + " Rift Scholar reputation</aqua>",
                "", MenuKit.hint("review the deposit"));
        inventory.setItem(31, icon(quote.items == 0 ? Material.GRAY_DYE : Material.LIME_DYE,
            quote.items == 0 ? "<gray>Nothing to Appraise</gray>" : "<green><bold>Deposit Finds</bold></green>",
            submitLore));
        inventory.setItem(29, icon(Material.WRITABLE_BOOK, "<aqua>Archive Collection</aqua>", MenuKit.withHint(List.of(
            "<gray>View each find, turn-ins, and mastery rewards.</gray>"), "open")));
        inventory.setItem(MenuKit.closeSlot(45), MenuKit.close());
        player.openInventory(inventory);
    }

    /** Finds leave the inventory, so the deposit goes through the shared confirm screen first. */
    private void confirmArchive(Player player, ArchiveHolder holder) {
        MenuKit.openConfirm(plugin, player, "<aqua><bold>Deposit Finds</bold></aqua>",
            icon(Material.LECTERN, "<aqua>Field Archive</aqua>", List.of(
                "<gray>Deposit</gray> <white>" + holder.eligible + " items</white>",
                "<gray>Elian records each find in the archive.</gray>")),
            List.of("<gray>Receive</gray> <aqua>+" + holder.reputation + " Rift Scholar reputation</aqua>"),
            confirmed -> {
                if (!plugin.sessions().account(confirmed.getUniqueId()).filter(holder.account::equals).isPresent()) return;
                // submitArchive re-quotes the inventory and only proceeds while the archive is open.
                openArchiveInventory(confirmed, holder.standing);
                submitArchive(confirmed);
            },
            cancelled -> openArchiveInventory(cancelled, holder.standing));
    }

    private void submitArchive(Player player) {
        if (!archivePending.add(player.getUniqueId())) {
            player.sendRichMessage("<yellow>Elian is already recording that deposit.</yellow>");
            return;
        }
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) {
            archivePending.remove(player.getUniqueId());
            return;
        }
        Inventory appraisal = player.getOpenInventory().getTopInventory();
        plugin.reputation().value(account, RIFT_SCHOLARS).whenComplete((standing, readError) -> sync(() -> {
            if (readError != null || !activeAppraisal(player, account, appraisal)) {
                archivePending.remove(player.getUniqueId());
                if (readError != null && player.isOnline()) {
                    player.sendRichMessage("<red>The archive ledger could not be read.</red>");
                }
                return;
            }
            ArchiveQuote quote = archiveQuote(player, account, Math.max(0, ReputationService.MAXIMUM - standing));
            if (quote.items == 0) {
                archivePending.remove(player.getUniqueId());
                player.sendRichMessage("<yellow>You have no finds Elian can record or appraise.</yellow>");
                openArchiveInventory(player, standing);
                return;
            }
            String operation = "archive:" + account + ":" + UUID.randomUUID();
            if (quote.reputation == 0) {
                if (!removeQuote(player, quote)) {
                    archivePending.remove(player.getUniqueId());
                    player.sendRichMessage("<yellow>Your inventory changed during appraisal. Nothing was taken.</yellow>");
                    return;
                }
                completeArchive(player, account, quote, standing, 0);
                return;
            }
            plugin.reputation().adjust(account, RIFT_SCHOLARS, quote.reputation,
                new ReputationService.ReputationReason("archive.exchange", "Field Archive deposit"), operation
            ).whenComplete((result, writeError) -> sync(() -> {
                if (writeError != null || result == null
                    || (result.status() != ReputationService.AdjustmentResult.Status.APPLIED
                    && result.status() != ReputationService.AdjustmentResult.Status.DUPLICATE)) {
                    archivePending.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendRichMessage("<red>The archive ledger rejected the deposit. Nothing was taken.</red>");
                    }
                    return;
                }
                if (!activeAppraisal(player, account, appraisal)
                    || result.appliedAmount() != quote.reputation || !removeQuote(player, quote)) {
                    rollbackArchive(player, account, quote, result.appliedAmount(), operation);
                    return;
                }
                completeArchive(player, account, quote, result.value(), result.appliedAmount(), operation);
            }));
        }));
    }

    private void openCollection(Player player, int requestedPage, boolean atArchive) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) {
            return;
        }
        List<Map.Entry<Material, CollectionMasteryService.Entry>> entries = plugin.collections().archaeologyEntries();
        int lastPage = Math.max(0, (entries.size() - 1) / 28);
        int page = Math.max(0, Math.min(lastPage, requestedPage));
        ArchiveCollectionHolder holder = new ArchiveCollectionHolder(page, lastPage, atArchive);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            miniMessage.deserialize("<aqua><bold>Archive Collection</bold></aqua>"));
        holder.inventory = inventory;
        decorate(inventory);
        inventory.setItem(4, icon(Material.LECTERN, "<aqua>Elian's Collection Ledger</aqua>", List.of(
            "<gray>Turn in finds at the Field Archive.</gray>",
            "<gray>Each entry has its own one-time mastery reward.</gray>",
            MenuKit.pageLine(page, lastPage + 1))));
        for (int index = page * 28; index < Math.min(entries.size(), (page + 1) * 28); index++) {
            var entry = entries.get(index).getValue();
            var progress = plugin.collections().progress(account, entry);
            int position = index - page * 28;
            int slot = 10 + position / 7 * 9 + position % 7;
            String color = progress.mastered() ? "gold" : progress.count() > 0 ? "aqua" : "white";
            inventory.setItem(slot, icon(entries.get(index).getKey(),
                "<" + color + ">" + entry.displayName() + "</" + color + ">", List.of(
                    "<gray>Turn-ins</gray> <white>" + Math.min(progress.count(), progress.required())
                        + "/" + progress.required() + "</white>",
                    "<gray>Mastery reward</gray> <aqua>" + entry.experienceReward() + " Excavation XP</aqua>",
                    "<gray>Mastery reward</gray> " + Chat.marksTag(entry.marksReward()),
                    progress.mastered() ? "<gold>Mastered</gold>" : progress.count() > 0
                        ? "<aqua>Recorded by Elian</aqua>" : "<dark_gray>Not yet turned in</dark_gray>")));
        }
        if (page > 0) {
            inventory.setItem(MenuKit.previousSlot(54), MenuKit.previous(page, lastPage + 1));
        }
        if (page < lastPage) {
            inventory.setItem(MenuKit.nextSlot(54), MenuKit.next(page, lastPage + 1));
        }
        inventory.setItem(MenuKit.backSlot(54), MenuKit.back(atArchive ? "Field Archive" : "Excavation Field Notes"));
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        player.openInventory(inventory);
    }

    private void completeArchive(Player player, AccountId account, ArchiveQuote quote, int standing, int reputation) {
        completeArchive(player, account, quote, standing, reputation, "archive:" + account + ":" + UUID.randomUUID());
    }

    private void completeArchive(Player player, AccountId account, ArchiveQuote quote, int standing, int reputation,
                                 String operation) {
        Map<CollectionMasteryService.Entry, Integer> entries = new LinkedHashMap<>();
        quote.removals.forEach(removal -> entries.merge(
            plugin.collections().archaeologyEntry(removal.material), removal.amount, Integer::sum));
        CollectionMasteryService.RecordResult collections;
        try {
            collections = plugin.collections().record(player, account, entries);
        } catch (RuntimeException exception) {
            for (ArchiveRemoval removal : quote.removals) {
                ItemStack restored = removal.template.clone();
                ItemStack current = player.getInventory().getItem(removal.slot);
                restored.setAmount(removal.amount + (current == null ? 0 : current.getAmount()));
                player.getInventory().setItem(removal.slot, restored);
            }
            plugin.getLogger().severe("Could not record archive collection for " + account + ": " + exception.getMessage());
            rollbackArchive(player, account, quote, reputation, operation);
            return;
        }
        archivePending.remove(player.getUniqueId());
        plugin.achievements().record(account, player, AchievementMetric.ARCHIVE_ITEMS, quote.items);
        if (!player.isOnline()) {
            player.saveData();
            return;
        }
        player.sendRichMessage("<aqua>Field Archive</aqua> <gray>recorded " + collections.recorded()
            + " finds.</gray>" + (reputation > 0
            ? " <green>+" + reputation + " Rift Scholar reputation</green>" : ""));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.9F, 1.15F);
        openArchiveInventory(player, standing);
    }

    private void rollbackArchive(
        Player player,
        AccountId account,
        ArchiveQuote quote,
        int appliedAmount,
        String operation
    ) {
        if (appliedAmount <= 0) {
            archivePending.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendRichMessage("<yellow>The appraisal changed before it could be recorded. Nothing was taken.</yellow>");
            }
            return;
        }
        plugin.reputation().adjust(account, RIFT_SCHOLARS, -appliedAmount,
            new ReputationService.ReputationReason("archive.rollback", "Field Archive inventory changed"),
            operation + ":rollback").whenComplete((rollback, rollbackError) -> sync(() -> {
                archivePending.remove(player.getUniqueId());
                if (rollbackError != null || rollback == null
                    || rollback.status() == ReputationService.AdjustmentResult.Status.INVALID_AMOUNT) {
                    plugin.getLogger().severe("Could not roll back Field Archive deposit " + operation);
                    if (player.isOnline()) {
                        player.sendRichMessage("<red>The archive needs staff attention. Your items were not taken.</red>");
                    }
                    return;
                }
                if (player.isOnline()) {
                    player.sendRichMessage("<yellow>Your inventory or standing changed during appraisal. Nothing was taken.</yellow>");
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof ArchiveHolder
                        && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()) {
                        openArchiveInventory(player, rollback.value());
                    }
                }
            }));
    }

    private ArchiveQuote archiveQuote(Player player, AccountId account, int capacity) {
        List<ArchiveRemoval> removals = new ArrayList<>();
        Map<CollectionMasteryService.Entry, Integer> quoted = new LinkedHashMap<>();
        int reputation = 0;
        int items = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            int value = archiveValue(stack);
            CollectionMasteryService.Entry entry = stack == null
                ? null : plugin.collections().archaeologyEntry(stack.getType());
            if (value <= 0 || entry == null) {
                continue;
            }
            int masteryRemaining = Math.max(0, plugin.collections().progress(account, entry).remaining()
                - quoted.getOrDefault(entry, 0));
            int reputationItems = Math.max(0, (capacity - reputation) / value);
            int accepted = Math.min(stack.getAmount(), Math.max(masteryRemaining, reputationItems));
            if (accepted <= 0) {
                continue;
            }
            int appraised = Math.min(accepted, reputationItems);
            ItemStack template = stack.clone();
            template.setAmount(1);
            removals.add(new ArchiveRemoval(slot, template, accepted, stack.getType()));
            items += accepted;
            reputation += appraised * value;
            quoted.merge(entry, accepted, Integer::sum);
        }
        return new ArchiveQuote(List.copyOf(removals), items, reputation);
    }

    private boolean activeAppraisal(Player player, AccountId account, Inventory appraisal) {
        return player.isOnline() && player.getOpenInventory().getTopInventory() == appraisal
            && appraisal.getHolder() instanceof ArchiveHolder
            && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    private int archiveValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        return ArchaeologyValues.reputation(stack.getType());
    }

    private boolean removeQuote(Player player, ArchiveQuote quote) {
        for (ArchiveRemoval removal : quote.removals) {
            ItemStack current = player.getInventory().getItem(removal.slot);
            if (current == null || !current.isSimilar(removal.template) || current.getAmount() < removal.amount) {
                return false;
            }
        }
        for (ArchiveRemoval removal : quote.removals) {
            ItemStack current = player.getInventory().getItem(removal.slot);
            int remaining = current.getAmount() - removal.amount;
            if (remaining == 0) {
                player.getInventory().setItem(removal.slot, null);
            } else {
                current.setAmount(remaining);
            }
        }
        return true;
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private ItemStack reward(Player player, FishingRolls.Rarity rarity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (rarity) {
            case COMMON, UNCOMMON -> materialReward(rarity, random.nextDouble(), random.nextDouble());
            case RARE -> random.nextDouble() < 0.35D
                ? plugin.forge().createGeode(GeodeGrade.RESONANT)
                : materialReward(rarity, random.nextDouble(), random.nextDouble());
            case LEGENDARY -> {
                double roll = random.nextDouble();
                if (roll < 0.40D) {
                    yield plugin.forge().createGeode(GeodeGrade.LUMINOUS);
                }
                if (roll < 0.75D) {
                    yield plugin.forge().createTome(ArcaneEnchantment.ARCHAEOLOGIST,
                        Math.max(3, Math.min(5, 2 + level(player) / 15)));
                }
                yield materialReward(rarity, random.nextDouble(), random.nextDouble());
            }
        };
    }

    private ItemStack materialReward(FishingRolls.Rarity rarity, double selectionRoll, double amountRoll) {
        ExcavationReward reward = ExcavationReward.select(rarity, selectionRoll);
        return new ItemStack(reward.material(), reward.amount(amountRoll));
    }

    private DigState advance(Player player) {
        Instant now = Instant.now();
        return states.compute(player.getUniqueId(), (ignored, previous) -> {
            int chain = previous != null && previous.lastDig.plusSeconds(8L).isAfter(now) ? previous.chain + 1 : 1;
            return new DigState(chain, previous == null ? 0 : previous.finds,
                previous == null ? 0 : previous.legendary, now);
        });
    }

    private int level(Player player) {
        return plugin.presentation().progression(player).skills()
            .getOrDefault(Skill.EXCAVATION, new net.nerdsmp.api.service.ProgressionService.SkillProgress(0L, 0)).level();
    }

    private double findBonus(Player player) {
        var progress = plugin.presentation().progression(player);
        var profile = plugin.gearSets().profile(player);
        return profile.stat("excavation-find-percent")
            + profile.stat("treasure-find-percent")
            + progress.adventurerStat("treasure-find-percent")
            + progress.talentStat(Skill.EXCAVATION, "geode-find-percent");
    }

    private void decorate(Inventory inventory) {
        MenuKit.frame(inventory);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        return styleIcon(new ItemStack(material), name, lore);
    }

    private ItemStack styleIcon(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.itemName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private record DigState(int chain, int finds, int legendary, Instant lastDig) {
    }

    private record ArchiveRemoval(int slot, ItemStack template, int amount, Material material) {
    }

    private record ArchiveQuote(List<ArchiveRemoval> removals, int items, int reputation) {
    }

    private static final class ExcavationHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ArchiveHolder implements InventoryHolder {
        private Inventory inventory;
        private AccountId account;
        private int standing;
        private int eligible;
        private int reputation;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ArchiveCollectionHolder implements InventoryHolder {
        private final int page;
        private final int lastPage;
        private final boolean atArchive;
        private Inventory inventory;

        private ArchiveCollectionHolder(int page, int lastPage, boolean atArchive) {
            this.page = page;
            this.lastPage = lastPage;
            this.atArchive = atArchive;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
