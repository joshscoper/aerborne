package net.nerdsmp.paper.activity;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.achievement.AchievementMetric;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GeodeGrade;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.skyblock.IslandGrid;
import net.nerdsmp.paper.skyblock.IslandResourceStage;
import net.nerdsmp.paper.skyblock.IslandService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingService implements Listener {
    private static final FactionId FREE_DELVERS = new FactionId("free_delvers");
    private static final int[] CATCH_SLOTS = {10, 11, 12, 13, 19, 20, 21, 28, 29, 30, 37, 38};

    private final NerdSmpPlugin plugin;
    private final SkillListener skills;
    private final IslandAngling angling;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<AccountId, CatchState> states = new ConcurrentHashMap<>();
    private final Set<UUID> fisheryPending = ConcurrentHashMap.newKeySet();
    private final Map<PlayerFishEvent, PreparedCatch> preparedCatches = new IdentityHashMap<>();
    private final Map<UUID, Long> acceptedHooks = new LinkedHashMap<>();

    public FishingService(NerdSmpPlugin plugin, SkillListener skills) {
        this.plugin = plugin;
        this.skills = skills;
        this.angling = new IslandAngling(plugin.playerData(), plugin.getConfig().getString("season.id", "season-1"));
    }

    public void open(Player player) {
        AccountId account = account(player);
        FishingHolder holder = new FishingHolder(player.getUniqueId(), account);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            miniMessage.deserialize("<aqua><bold>Angler's Journal</bold></aqua>"));
        holder.inventory = inventory;
        decorate(inventory);
        CatchState state = state(player);
        double luck = luck(player);
        int level = level(player);
        FishingCatch.Habitat habitat = habitat(player);
        IslandService.Island pondIsland = journalIsland(player, account);
        if (pondIsland != null) {
            holder.islandOwner = pondIsland.owner();
            holder.islandId = pondIsland.id();
        }
        inventory.setItem(4, icon(Material.FISHING_ROD, "<aqua><bold>Fishing " + level + "</bold></aqua>", List.of(
            "<gray>Catch streak</gray> <white>" + state.streak + "</white> <dark_gray>•</dark_gray> <gray>best</gray> <white>"
                + state.bestStreak + "</white>",
            "<gray>Fishing luck</gray> <green>+" + Math.round(luck) + "%</green>",
            "<gray>Total catches</gray> <white>" + state.total + "</white>",
            "<gray>Journal</gray> <aqua>" + (account == null ? 0 : plugin.collections().recordedCount(account,
                CollectionMasteryService.Category.FISHING)) + "/" + FishingCatch.values().length + " species</aqua>",
            "<dark_gray>Ivo records catches only when you turn them in.</dark_gray>"
        )));
        FishingCatch[] catches = FishingCatch.values();
        for (int index = 0; index < catches.length; index++) {
            inventory.setItem(CATCH_SLOTS[index], journalIcon(catches[index], account));
        }
        inventory.setItem(45, icon(habitatIcon(habitat), "<aqua>Nearby Biome: " + habitat.displayName() + "</aqua>", List.of(
            "<gray>Shared zones use the bobber's biome.</gray>",
            "<gray>Species that favor it are four times more likely.</gray>",
            "<dark_gray>Every species can still appear in other waters.</dark_gray>",
            "<dark_gray>Island ponds use your co-op's selected habitat.</dark_gray>"
        )));
        if (pondIsland != null) {
            int stage = plugin.skyblock().resources().effectiveStage(pondIsland.owner());
            var fishery = angling.progress(pondIsland.owner());
            inventory.setItem(51, icon(Material.HEART_OF_THE_SEA, "<aqua>Co-op Fishery | Tier " + fishery.tier() + "</aqua>", List.of(
                "<gray>Accepted island catches</gray> <white>" + fishery.catches() + "</white>",
                "<gray>Waters explored</gray> <white>" + fishery.habitats().size() + "/5</white>",
                "<gray>Co-op luck</gray> <green>+" + fishery.luck() + "%</green>",
                "<gray>100 catches: +2% luck</gray>", "<gray>400 catches in 3 waters: +4% luck</gray>",
                "<gray>1,000 catches in all 5 waters: +8% luck</gray>",
                "<gray>Each tier adds 2% bite speed, up to the 55% cap.</gray>")));
            IslandFishingPolicy.PondHabitat selected = pondHabitat(pondIsland.owner());
            List<String> pondLore = new ArrayList<>(List.of(
                "<gray>Island</gray> <white>" + MiniMessage.miniMessage().escapeTags(pondIsland.name()) + "</white>",
                "<gray>Fish anywhere you can land a catch.</gray>",
                "<gray>No minimum pond size or depth.</gray>",
                "<gray>Keep your bobber inside your island border.</gray>",
                "<gray>Island fishing luck</gray> <green>+" + ((int) IslandFishingPolicy.islandLuck(stage)
                    + 5 * plugin.skyblock().development().progress(pondIsland.owner()).rank(net.nerdsmp.paper.skyblock.IslandTalent.FISHERY)) + "%</green>",
                "<gray>Available</gray> <white>" + IslandFishingPolicy.unlocked(stage).stream()
                    .map(IslandFishingPolicy.PondHabitat::title).collect(java.util.stream.Collectors.joining(", "))
                    + "</white>"
            ));
            java.util.Arrays.stream(IslandFishingPolicy.PondHabitat.values()).filter(value -> value.stage() > stage)
                .findFirst().ifPresent(next -> pondLore.add("<gray>Next</gray> <aqua>" + next.title()
                    + "</aqua> <dark_gray>•</dark_gray> <white>" + IslandResourceStage.at(next.stage()).title() + "</white>"));
            if (IslandFishingPolicy.unlocked(stage).size() <= 1) pondLore.add("<dark_gray>Advance your island to unlock more habitats.</dark_gray>");
            inventory.setItem(46, icon(Material.WATER_BUCKET, "<aqua>Pond Habitat: " + selected.title() + "</aqua>",
                IslandFishingPolicy.unlocked(stage).size() > 1 ? MenuKit.withHint(pondLore, "change your co-op's pond habitat") : pondLore));
        } else {
            inventory.setItem(46, icon(Material.WATER_BUCKET, "<aqua>Island Ponds</aqua>", List.of(
                "<gray>Any pool you can catch fish in will work.</gray>",
                "<gray>Island milestones unlock new pond habitats.</gray>",
                "<gray>Join a co-op or start an island with /island.</gray>")));
        }
        inventory.setItem(47, icon(Material.NAUTILUS_SHELL, "<light_purple>Treasure & Encounters</light_purple>", List.of(
            "<gray>Rare catches can yield geodes and sea treasure.</gray>",
            "<gray>Prismarine shards and crystals supply Deepcurrent gear.</gray>",
            "<gray>Legendary catches can yield luminous geodes or tomes.</gray>",
            "<dark_gray>Creature encounters happen in shared zones only.</dark_gray>"
        )));
        inventory.setItem(52, icon(Material.BARREL, "<gold>Ivo's Fishery</gold>", List.of(
            "<gray>Ivo records authentic journal catches for</gray>",
            "<gray>Free Delver reputation.</gray>",
            "<dark_gray>Speak with Ivo to submit a catch.</dark_gray>"
        )));
        inventory.setItem(MenuKit.backSlot(54), MenuKit.back("Adventure"));
        inventory.setItem(MenuKit.closeSlot(54), MenuKit.close());
        inventory.setItem(50, icon(Material.CRAFTING_TABLE, "<gold>Rods & Equipment</gold>", MenuKit.withHint(List.of(
            "<gray>Craft rods and fishing sets in Workshop Recipes.</gray>", "<gray>Prismarine from fishing supplies Deepcurrent gear.</gray>"), "open recipes")));
        player.openInventory(inventory);
    }

    public void openFishery(Player player) {
        AccountId account = account(player);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        plugin.reputation().value(account, FREE_DELVERS).whenComplete((standing, error) -> sync(() -> {
            if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                || !account.equals(account(player))) {
                return;
            }
            if (error != null) {
                player.sendRichMessage("<red>Ivo could not open his catch ledger.</red>");
                return;
            }
            openFisheryInventory(player, standing);
        }));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            AccountId owner = plugin.skyblock() == null ? null : plugin.skyblock().activityIsland(player);
            double speed = Math.min(0.55D, level(player) * 0.004D + luck(player) / 250.0D
                + (owner == null ? 0 : .02 * angling.progress(owner).tier()));
            int minimum = Math.max(60, (int) Math.round(100 * (1.0D - speed)));
            int maximum = Math.max(minimum + 40, (int) Math.round(500 * (1.0D - speed)));
            event.getHook().setWaitTime(minimum, maximum);
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        AccountId account = account(player);
        if (account == null || !rewardGameMode(player)) return;
        CatchContext context = catchContext(player, event.getHook().getLocation());
        if (!context.eligible) {
            event.setCancelled(true);
            player.sendRichMessage("<yellow>" + context.reason + "</yellow>");
            return;
        }
        int streak = nextStreak(state(player), Instant.now());
        boolean openWater = context.islandOwner != null || event.getHook().isInOpenWater();
        FishingRolls.Rarity rarity = FishingRolls.rarity(level(player), luck(player)
            + IslandFishingPolicy.islandLuck(context.stage)
            + (context.islandOwner == null ? 0 : angling.progress(context.islandOwner).luck())
            + (context.islandOwner == null ? 0 : 5 * plugin.skyblock().development().progress(context.islandOwner)
                .rank(net.nerdsmp.paper.skyblock.IslandTalent.FISHERY)), streak, openWater,
            ThreadLocalRandom.current().nextDouble());
        CatchReward reward = reward(rarity, player, context.habitat);
        caught.setItemStack(reward.item);
        preparedCatches.put(event, new PreparedCatch(account, event.getHook().getUniqueId(),
            event.getHook().getLocation().clone(), caught, rarity, reward, context));
    }

    /** Accepted catches are the single source of fishing XP, jobs, streaks and island work. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void completeCatch(PlayerFishEvent event) {
        PreparedCatch prepared = preparedCatches.remove(event);
        if (event.isCancelled()) return;
        if (prepared == null) {
            if (event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT
                || event.getState() == PlayerFishEvent.State.REEL_IN) {
                Player player = event.getPlayer();
                AccountId account = account(player);
                if (account != null) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!event.isCancelled() && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                        && account.equals(account(player)) && rewardGameMode(player)) breakStreak(player);
                });
            }
            return;
        }
        // Validate the prepared item before native pickup, spawn formatting or item removal can change it.
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || event.getCaught() != prepared.item
            || !prepared.item.getItemStack().isSimilar(prepared.reward.item)
            || prepared.item.getItemStack().getAmount() != prepared.reward.item.getAmount()) return;
        Bukkit.getScheduler().runTask(plugin, () -> commitCatch(event, prepared));
    }

    /** Deferral observes cancellations from every listener, including later MONITOR listeners. */
    private void commitCatch(PlayerFishEvent event, PreparedCatch prepared) {
        Player player = event.getPlayer();
        if (event.isCancelled() || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
            || !prepared.account.equals(account(player)) || !rewardGameMode(player)
            || event.getState() != PlayerFishEvent.State.CAUGHT_FISH || event.getCaught() != prepared.item
            || !event.getHook().getUniqueId().equals(prepared.hookId)) return;
        if (!currentCatchContext(player, prepared, event.getHook().getLocation())) return;
        long now = System.currentTimeMillis();
        var receipts = acceptedHooks.entrySet().iterator();
        while (receipts.hasNext()) {
            if (now - receipts.next().getValue() <= 600_000L) break;
            receipts.remove();
        }
        if (acceptedHooks.putIfAbsent(prepared.hookId, now) != null) return;
        while (acceptedHooks.size() > 4096) acceptedHooks.remove(acceptedHooks.keySet().iterator().next());
        CatchState state;
        try {
            state = advance(player);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not record fishing catch for " + prepared.account, error);
            return;
        }
        FishingRolls.Rarity rarity = prepared.rarity;
        CatchReward reward = prepared.reward;
        if (prepared.context.islandOwner != null) {
            try { angling.record(prepared.context.islandOwner, prepared.hookId, prepared.context.habitat.name()); }
            catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.WARNING, "Island fishery progress could not be saved; caught item is preserved", error); }
        }
        skills.awardActivityAtIsland(player, Skill.FISHING,
            IslandFishingPolicy.experience(plugin.getConfig().getLong("progression.xp.fishing-catch", 25L), rarity),
            "fish:" + prepared.hookId, prepared.context.islandOwner);
        plugin.achievements().record(player, net.nerdsmp.paper.achievement.AchievementMetric.FISH_CAUGHT, 1);
        String color = switch (rarity) {
            case COMMON -> "gray";
            case UNCOMMON -> "green";
            case RARE -> "aqua";
            case LEGENDARY -> "gold";
        };
        ItemMeta rewardMeta = reward.item.getItemMeta();
        Component rewardName = rewardMeta.hasItemName()
            ? rewardMeta.itemName() : Component.translatable(reward.item.translationKey());
        player.sendActionBar(miniMessage.deserialize("<" + color + ">" + rarity.name() + " CATCH</" + color
                + "> <dark_gray>•</dark_gray> ")
            .append(rewardName)
            .append(miniMessage.deserialize(" <dark_gray>•</dark_gray> <white>Streak " + state.streak + "</white>")));
        player.playSound(player.getLocation(), rarity == FishingRolls.Rarity.LEGENDARY
            ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8F,
            0.9F + rarity.ordinal() * 0.12F);
        if (prepared.context.islandOwner == null) maybeEncounter(player, state.streak);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FishingHolder holder) {
            boolean alreadyCancelled = event.isCancelled();
            event.setCancelled(true);
            if (alreadyCancelled) return;
            if (event.getWhoClicked() instanceof Player player && holder.playerId.equals(player.getUniqueId())
                && java.util.Objects.equals(holder.account, account(player))
                && event.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
                if (event.getRawSlot() == MenuKit.closeSlot(54)) player.closeInventory();
                else if (event.getRawSlot() == MenuKit.backSlot(54)) {
                    MenuKit.clickSound(player);
                    plugin.menus().open(player, MenuManager.View.ADVENTURE);
                }
                else if (event.getRawSlot() == 50) { MenuKit.clickSound(player); player.closeInventory(); player.performCommand("recipes"); }
                else if (event.getRawSlot() == 46 && holder.islandOwner != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.getOpenInventory().getTopInventory() == holder.inventory) changePondHabitat(player, holder);
                    });
                }
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof FisheryHolder holder) {
            boolean denied = event.isCancelled();
            event.setCancelled(true);
            if (denied || !(event.getWhoClicked() instanceof Player player) || event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                || !holder.playerId.equals(player.getUniqueId()) || !holder.account.equals(account(player)) || Bukkit.getPlayer(player.getUniqueId()) != player) {
                return;
            }
            if (event.getRawSlot() == MenuKit.closeSlot(45)) {
                player.closeInventory();
            } else if (event.getRawSlot() == 31 && holder.eligible > 0) {
                MenuKit.clickSound(player);
                Bukkit.getScheduler().runTask(plugin, () -> { if (player.getOpenInventory().getTopInventory() == holder.inventory
                    && holder.account.equals(account(player)) && Bukkit.getPlayer(player.getUniqueId()) == player) confirmFishery(player, holder); });
            } else if (event.getRawSlot() == 29) {
                MenuKit.clickSound(player);
                open(player);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof FishingHolder || holder instanceof FisheryHolder) {
            event.setCancelled(true);
        }
    }

    private CatchReward reward(FishingRolls.Rarity rarity, Player player, FishingCatch.Habitat habitat) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double roll = random.nextDouble();
        if (rarity == FishingRolls.Rarity.UNCOMMON && roll >= .70 && roll < .85)
            return new CatchReward(new ItemStack(Material.PRISMARINE_SHARD, 3), null);
        if (rarity == FishingRolls.Rarity.RARE && roll >= .50 && roll < .65)
            return new CatchReward(new ItemStack(Material.PRISMARINE_CRYSTALS, 2), null);
        if (rarity == FishingRolls.Rarity.RARE && roll >= 0.65D) {
            return roll < 0.85D
                ? new CatchReward(plugin.forge().createGeode(GeodeGrade.RESONANT), null)
                : new CatchReward(named(Material.HEART_OF_THE_SEA, 1, "<aqua>Heart of the Current</aqua>",
                    "<gray>Sea-worn treasure prized by conduit builders.</gray>"), null);
        }
        if (rarity == FishingRolls.Rarity.LEGENDARY && roll >= 0.55D) {
            return roll < 0.80D
                ? new CatchReward(plugin.forge().createGeode(GeodeGrade.LUMINOUS), null)
                : new CatchReward(plugin.forge().createTome(ArcaneEnchantment.ANGLER,
                    Math.max(3, Math.min(5, 2 + level(player) / 15))), null);
        }
        if (rarity == FishingRolls.Rarity.UNCOMMON && roll >= 0.85D) {
            return new CatchReward(named(Material.NAUTILUS_SHELL, 1, "<green>Current-Worn Nautilus</green>",
                "<gray>A sound shell pulled from deep water.</gray>"), null);
        }
        FishingCatch catchType = FishingCatch.select(rarity, habitat, random.nextDouble());
        return new CatchReward(plugin.items().create(catchType.itemId()), catchType);
    }

    private void maybeEncounter(Player player, int streak) {
        if (streak < 5 || ThreadLocalRandom.current().nextDouble() >= Math.min(0.04D, 0.005D + streak * 0.001D)) {
            return;
        }
        try {
            plugin.mobs().spawn(streak >= 15 ? "bog_oracle" : "mire_weaver", player.getLocation().add(3, 0, 3));
            player.sendRichMessage("<light_purple>Something followed your line out of the water.</light_purple>");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not spawn fishing encounter: " + exception.getMessage());
        }
    }

    private CatchState advance(Player player) {
        AccountId account = account(player);
        if (account == null) {
            return CatchState.empty();
        }
        Instant now = Instant.now();
        CatchState current = states.computeIfAbsent(account, this::load);
        int streak = nextStreak(current, now);
        CatchState next = new CatchState(streak, current.total + 1L, Math.max(current.bestStreak, streak), now);
        persist(account, next);
        states.put(account, next);
        return next;
    }

    private static int nextStreak(CatchState current, Instant now) {
        return current.lastCatch.plusSeconds(90L).isAfter(now) ? Math.min(1_000_000, current.streak + 1) : 1;
    }

    private void breakStreak(Player player) {
        AccountId account = account(player);
        if (account == null) {
            return;
        }
        CatchState next = states.compute(account, (ignored, previous) -> {
            CatchState current = previous == null ? load(account) : previous;
            return new CatchState(0, current.total, current.bestStreak, Instant.EPOCH);
        });
        persist(account, next);
    }

    private CatchState state(Player player) {
        AccountId account = account(player);
        return account == null ? CatchState.empty() : states.computeIfAbsent(account, this::load);
    }

    private CatchState load(AccountId account) {
        return plugin.playerData().read(account, yaml -> {
            Instant lastCatch;
            try {
                lastCatch = Instant.parse(yaml.getString("fishing.last-catch", Instant.EPOCH.toString()));
            } catch (DateTimeParseException exception) {
                lastCatch = Instant.EPOCH;
            }
            return new CatchState(
                Math.max(0, yaml.getInt("fishing.streak")),
                Math.max(0L, yaml.getLong("fishing.total-catches")),
                Math.max(0, yaml.getInt("fishing.best-streak")),
                lastCatch
            );
        });
    }

    private void persist(AccountId account, CatchState state) {
        plugin.playerData().update(account, yaml -> {
            yaml.set("fishing.streak", state.streak);
            yaml.set("fishing.total-catches", state.total);
            yaml.set("fishing.best-streak", state.bestStreak);
            yaml.set("fishing.last-catch", state.lastCatch.toString());
        });
    }

    private void openFisheryInventory(Player player, int standing) {
        AccountId account = account(player);
        if (account == null) {
            return;
        }
        FisheryQuote quote = fisheryQuote(player, account, Math.max(0, ReputationService.MAXIMUM - standing));
        FisheryHolder holder = new FisheryHolder(player.getUniqueId(), account);
        holder.standing = standing;
        holder.eligible = quote.items;
        holder.reputation = quote.reputation;
        Inventory inventory = Bukkit.createInventory(holder, 45,
            miniMessage.deserialize("<aqua><bold>Ivo's Fishery</bold></aqua>"));
        holder.inventory = inventory;
        decorate(inventory);
        inventory.setItem(4, icon(Material.BARREL, "<aqua><bold>Catch Ledger</bold></aqua>", List.of(
            "<gray>Free Delver standing</gray> <white>" + standing + "/" + ReputationService.MAXIMUM + "</white>",
            "<gray>Eligible catches</gray> <white>" + quote.items + "</white>",
            "<gray>Appraisal</gray> <aqua>+" + quote.reputation + " reputation</aqua>",
            "<gray>Mastered</gray> <gold>" + plugin.collections().masteredCount(account,
                CollectionMasteryService.Category.FISHING) + "/" + FishingCatch.values().length + " entries</gold>"
        )));
        inventory.setItem(11, plugin.items().create(FishingCatch.RIVER_SPRAT.itemId()));
        inventory.setItem(13, plugin.items().create(FishingCatch.MOONLIT_KOI.itemId()));
        inventory.setItem(15, plugin.items().create(FishingCatch.STORMFIN_TUNA.itemId()));
        inventory.setItem(22, plugin.items().create(FishingCatch.STARFALL_STURGEON.itemId()));
        List<String> submitLore = quote.items == 0
            ? List.of("<gray>No journal catches are in your inventory.</gray>",
                "<dark_gray>Only authentic catches landed with a rod qualify.</dark_gray>")
            : List.of("<gray>Submit</gray> <white>" + quote.items + " catches</white>",
                "<gray>Receive</gray> <aqua>+" + quote.reputation + " Free Delver reputation</aqua>",
                "", MenuKit.hint("review the turn-in"));
        inventory.setItem(31, icon(quote.items == 0 ? Material.GRAY_DYE : Material.LIME_DYE,
            quote.items == 0 ? "<gray>Nothing to Appraise</gray>" : "<green><bold>Submit Catch</bold></green>",
            submitLore));
        inventory.setItem(29, icon(Material.WRITABLE_BOOK, "<aqua>Angler's Journal</aqua>", MenuKit.withHint(List.of(
            "<gray>View each species, turn-ins, and mastery rewards.</gray>"), "open")));
        inventory.setItem(MenuKit.closeSlot(45), MenuKit.close());
        player.openInventory(inventory);
    }

    /** Catches leave the inventory, so the turn-in goes through the shared confirm screen first. */
    private void confirmFishery(Player player, FisheryHolder holder) {
        int standing = holder.standing;
        MenuKit.openConfirm(plugin, player, "<aqua><bold>Turn In Catches</bold></aqua>",
            icon(Material.BARREL, "<aqua>Ivo's Fishery</aqua>", List.of(
                "<gray>Submit</gray> <white>" + holder.eligible + " catches</white>",
                "<gray>Ivo records each species in your journal.</gray>")),
            List.of("<gray>Receive</gray> <aqua>+" + holder.reputation + " Free Delver reputation</aqua>"),
            confirmed -> {
                if (!holder.account.equals(account(confirmed))) return;
                // submitFishery re-quotes the inventory and only proceeds while the ledger is open.
                openFisheryInventory(confirmed, standing);
                submitFishery(confirmed);
            },
            cancelled -> {
                if (holder.account.equals(account(cancelled))) openFisheryInventory(cancelled, standing);
            });
    }

    private void submitFishery(Player player) {
        if (!fisheryPending.add(player.getUniqueId())) {
            player.sendRichMessage("<yellow>Ivo is already recording that catch.</yellow>");
            return;
        }
        AccountId account = account(player);
        if (account == null) {
            fisheryPending.remove(player.getUniqueId());
            return;
        }
        Inventory appraisal = player.getOpenInventory().getTopInventory();
        plugin.reputation().value(account, FREE_DELVERS).whenComplete((standing, readError) -> sync(() -> {
            if (readError != null || !activeAppraisal(player, account, appraisal)) {
                fisheryPending.remove(player.getUniqueId());
                if (readError != null && player.isOnline()) {
                    player.sendRichMessage("<red>Ivo could not read his catch ledger.</red>");
                }
                return;
            }
            FisheryQuote quote = fisheryQuote(player, account, Math.max(0, ReputationService.MAXIMUM - standing));
            if (quote.items == 0) {
                fisheryPending.remove(player.getUniqueId());
                player.sendRichMessage("<yellow>You have no catches that Ivo can record or appraise.</yellow>");
                openFisheryInventory(player, standing);
                return;
            }
            String operation = "fishery:" + account + ":" + UUID.randomUUID();
            if (quote.reputation == 0) {
                if (!removeQuote(player, quote)) {
                    fisheryPending.remove(player.getUniqueId());
                    player.sendRichMessage("<yellow>Your inventory changed during appraisal. Nothing was taken.</yellow>");
                    return;
                }
                completeFishery(player, account, quote, standing, 0);
                return;
            }
            plugin.reputation().adjust(account, FREE_DELVERS, quote.reputation,
                new ReputationService.ReputationReason("fishery.exchange", "Ivo's catch ledger"), operation
            ).whenComplete((result, writeError) -> sync(() -> {
                if (writeError != null || result == null
                    || (result.status() != ReputationService.AdjustmentResult.Status.APPLIED
                    && result.status() != ReputationService.AdjustmentResult.Status.DUPLICATE)) {
                    fisheryPending.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendRichMessage("<red>Ivo's ledger rejected the appraisal. Nothing was taken.</red>");
                    }
                    return;
                }
                if (!activeAppraisal(player, account, appraisal)
                    || result.appliedAmount() != quote.reputation || !removeQuote(player, quote)) {
                    rollbackFishery(player, account, result.appliedAmount(), operation);
                    return;
                }
                completeFishery(player, account, quote, result.value(), result.appliedAmount(), operation);
            }));
        }));
    }

    private void completeFishery(Player player, AccountId account, FisheryQuote quote, int standing, int reputation) {
        completeFishery(player, account, quote, standing, reputation, "fishery:" + account + ":" + UUID.randomUUID());
    }

    private void completeFishery(Player player, AccountId account, FisheryQuote quote, int standing, int reputation,
                                 String operation) {
        Map<CollectionMasteryService.Entry, Integer> entries = new LinkedHashMap<>();
        quote.removals.forEach(removal -> entries.merge(
            plugin.collections().fishingEntry(removal.catchType.itemId().value()), removal.amount, Integer::sum));
        CollectionMasteryService.RecordResult collections;
        try {
            collections = plugin.collections().record(player, account, entries);
        } catch (RuntimeException exception) {
            for (CatchRemoval removal : quote.removals) {
                ItemStack restored = removal.template.clone();
                ItemStack current = player.getInventory().getItem(removal.slot);
                restored.setAmount(removal.amount + (current == null ? 0 : current.getAmount()));
                player.getInventory().setItem(removal.slot, restored);
            }
            plugin.getLogger().severe("Could not record catch collection for " + account + ": " + exception.getMessage());
            rollbackFishery(player, account, reputation, operation);
            return;
        }
        fisheryPending.remove(player.getUniqueId());
        plugin.achievements().setAtLeast(player, AchievementMetric.FISHING_SPECIES,
            plugin.collections().recordedCount(account, CollectionMasteryService.Category.FISHING));
        if (!player.isOnline()) {
            player.saveData();
            return;
        }
        player.sendRichMessage("<aqua>Ivo's Fishery</aqua> <gray>recorded " + collections.recorded()
            + " catches.</gray>" + (reputation > 0
            ? " <green>+" + reputation + " Free Delver reputation</green>" : ""));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.9F, 1.15F);
        openFisheryInventory(player, standing);
    }

    private void rollbackFishery(Player player, AccountId account, int appliedAmount, String operation) {
        if (appliedAmount <= 0) {
            fisheryPending.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendRichMessage("<yellow>The appraisal changed before it could be recorded. Nothing was taken.</yellow>");
            }
            return;
        }
        plugin.reputation().adjust(account, FREE_DELVERS, -appliedAmount,
            new ReputationService.ReputationReason("fishery.rollback", "Catch inventory changed"),
            operation + ":rollback").whenComplete((rollback, rollbackError) -> sync(() -> {
                fisheryPending.remove(player.getUniqueId());
                if (rollbackError != null || rollback == null
                    || rollback.status() == ReputationService.AdjustmentResult.Status.INVALID_AMOUNT) {
                    plugin.getLogger().severe("Could not roll back fishery appraisal " + operation);
                    if (player.isOnline()) {
                        player.sendRichMessage("<red>Ivo's ledger needs staff attention. Your items were not taken.</red>");
                    }
                    return;
                }
                if (player.isOnline()) {
                    player.sendRichMessage("<yellow>Your inventory or standing changed during appraisal. Nothing was taken.</yellow>");
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof FisheryHolder
                        && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()) {
                        openFisheryInventory(player, rollback.value());
                    }
                }
            }));
    }

    private FisheryQuote fisheryQuote(Player player, AccountId account, int capacity) {
        List<CatchRemoval> removals = new ArrayList<>();
        Map<CollectionMasteryService.Entry, Integer> quoted = new LinkedHashMap<>();
        int reputation = 0;
        int items = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            FishingCatch catchType = catchType(stack);
            if (catchType == null) {
                continue;
            }
            CollectionMasteryService.Entry entry = plugin.collections().fishingEntry(catchType.itemId().value());
            int masteryRemaining = Math.max(0, plugin.collections().progress(account, entry).remaining()
                - quoted.getOrDefault(entry, 0));
            int reputationItems = Math.max(0, (capacity - reputation) / catchType.reputation());
            int accepted = Math.min(stack.getAmount(), Math.max(masteryRemaining, reputationItems));
            if (accepted <= 0) {
                continue;
            }
            int appraised = Math.min(accepted, reputationItems);
            ItemStack template = stack.clone();
            template.setAmount(1);
            removals.add(new CatchRemoval(slot, template, accepted, catchType));
            items += accepted;
            reputation += appraised * catchType.reputation();
            quoted.merge(entry, accepted, Integer::sum);
        }
        return new FisheryQuote(List.copyOf(removals), items, reputation);
    }

    private boolean activeAppraisal(Player player, AccountId account, Inventory appraisal) {
        return player.isOnline() && player.getOpenInventory().getTopInventory() == appraisal
            && appraisal.getHolder() instanceof FisheryHolder
            && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    private FishingCatch catchType(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !plugin.items().isAuthentic(stack)) {
            return null;
        }
        String itemId = plugin.items().describe(stack).map(descriptor -> descriptor.itemId().value()).orElse(null);
        return FishingCatch.byItemId(itemId);
    }

    private boolean removeQuote(Player player, FisheryQuote quote) {
        for (CatchRemoval removal : quote.removals) {
            ItemStack current = player.getInventory().getItem(removal.slot);
            if (current == null || !current.isSimilar(removal.template) || current.getAmount() < removal.amount) {
                return false;
            }
        }
        for (CatchRemoval removal : quote.removals) {
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

    private ItemStack journalIcon(FishingCatch catchType, AccountId account) {
        CollectionMasteryService.Entry entry = plugin.collections().fishingEntry(catchType.itemId().value());
        CollectionMasteryService.Progress progress = account == null
            ? new CollectionMasteryService.Progress(0, entry.required(), false)
            : plugin.collections().progress(account, entry);
        boolean known = progress.count() > 0;
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Rarity</gray> " + rarityColor(catchType.rarity()) + catchType.rarity().name() + "</" +
            rarityColor(catchType.rarity()).substring(1));
        lore.add("<gray>Favored water</gray> <white>" + catchType.habitats().stream()
            .map(FishingCatch.Habitat::displayName).sorted().collect(java.util.stream.Collectors.joining(", ")) + "</white>");
        lore.add("<gray>Ivo's appraisal</gray> <aqua>+" + catchType.reputation() + " reputation</aqua>");
        lore.add("<gray>Turn-ins</gray> <white>" + Math.min(progress.count(), progress.required()) + "/"
            + progress.required() + "</white>");
        lore.add("<gray>Mastery</gray> <aqua>+" + entry.experienceReward() + " Fishing XP</aqua>"
            + " <dark_gray>•</dark_gray> <gold>+" + Chat.marks(entry.marksReward()) + "</gold>");
        lore.add(progress.mastered() ? "<gold>★ Mastered</gold>" : known
            ? "<green>Recorded by Ivo</green>" : "<dark_gray>Not yet turned in</dark_gray>");
        if (!known) {
            return icon(Material.GRAY_DYE, "<gray>Unknown " + title(catchType.rarity().name()) + " Catch</gray>", lore);
        }
        ItemStack item = plugin.items().create(catchType.itemId());
        ItemMeta meta = item.getItemMeta();
        Component name = meta.itemName();
        meta.itemName(Component.text("✓ ").append(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line)
            .decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private FishingCatch.Habitat habitat(Player player) {
        return FishingCatch.Habitat.fromBiome(player.getLocation().getBlock().getBiome().getKey().getKey());
    }

    private CatchContext catchContext(Player player, Location hook) {
        World world = hook.getWorld();
        if (world == null) return CatchContext.rejected("Your line is outside the fishing area.");
        if (!world.isChunkLoaded(hook.getBlockX() >> 4, hook.getBlockZ() >> 4)) {
            return CatchContext.rejected("Move closer so the fishing spot is loaded.");
        }
        if (hook.getBlockY() < world.getMinHeight() || hook.getBlockY() >= world.getMaxHeight()) {
            return CatchContext.rejected("Your line is outside the fishing area.");
        }
        var skyblock = plugin.skyblock();
        if (skyblock == null || !skyblock.isIslandWorld(world)) {
            return new CatchContext(null, null, 0,
                FishingCatch.Habitat.fromBiome(world.getBiome(hook.getBlockX(), hook.getBlockY(), hook.getBlockZ())
                    .getKey().getKey()), true, "");
        }
        AccountId account = account(player);
        IslandService.Island island = skyblock.islands().at(hook.getBlockX() >> 4, hook.getBlockZ() >> 4)
            .filter(value -> value.member(account)).orElse(null);
        if (island == null) return CatchContext.rejected("Cast into a pond inside your own island or a co-op you belong to.");
        IslandFishingPolicy.PondStatus check = IslandFishingPolicy.inspect(new IslandFishingPolicy.PondAccess() {
            @Override public int minHeight() { return world.getMinHeight(); }
            @Override public int maxHeight() { return world.getMaxHeight(); }
            @Override public boolean loaded(int x, int z) { return world.isChunkLoaded(x >> 4, z >> 4); }
            @Override public boolean belongsToIsland(int x, int z) {
                return IslandGrid.contains(island.slot(), island.tier(), x >> 4, z >> 4);
            }
        }, hook.getBlockX(), hook.getBlockY(), hook.getBlockZ());
        if (check != IslandFishingPolicy.PondStatus.VALID) {
            return CatchContext.rejected(switch (check) {
                case OUTSIDE_ISLAND -> "Keep your bobber inside your island border.";
                case UNLOADED -> "Move closer to your pond so the fishing spot is loaded.";
                default -> "Your line is outside the fishing area.";
            });
        }
        int stage = skyblock.resources().effectiveStage(island.owner());
        return new CatchContext(island.owner(), island.id(), stage,
            pondHabitat(island.owner()).fishingHabitat(), true, "");
    }

    private boolean currentCatchContext(Player player, PreparedCatch prepared, Location hook) {
        Location previous = prepared.hookLocation;
        if (hook.getWorld() == null || !hook.getWorld().equals(previous.getWorld())
            || !player.getWorld().equals(previous.getWorld())
            || hook.getBlockX() != previous.getBlockX() || hook.getBlockY() != previous.getBlockY()
            || hook.getBlockZ() != previous.getBlockZ()) return false;
        var skyblock = plugin.skyblock();
        if (prepared.context.islandOwner == null) return skyblock == null || !skyblock.isIslandWorld(hook.getWorld());
        if (skyblock == null || !skyblock.isIslandWorld(hook.getWorld())) return false;
        return skyblock.islands().at(hook.getBlockX() >> 4, hook.getBlockZ() >> 4)
            .filter(island -> island.owner().equals(prepared.context.islandOwner)
                && island.id().equals(prepared.context.islandId) && island.member(account(player))).isPresent();
    }

    private IslandService.Island journalIsland(Player player, AccountId account) {
        var skyblock = plugin.skyblock();
        if (skyblock == null || account == null) return null;
        if (skyblock.isIslandWorld(player.getWorld())) {
            IslandService.Island current = skyblock.islands().at(player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4).filter(value -> value.member(account)).orElse(null);
            if (current != null) return current;
        }
        return skyblock.islands().owned(account).orElseGet(() -> skyblock.islands().homes(account).stream().findFirst().orElse(null));
    }

    private String pondHabitatPath() {
        String season = plugin.getConfig().getString("season.id", "season-1");
        if (!season.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalStateException("Invalid fishing season");
        return "seasons." + season + ".island-fishing.pond-habitat";
    }

    private IslandFishingPolicy.PondHabitat pondHabitat(AccountId owner) {
        String key = plugin.playerData().read(owner, yaml -> yaml.getString(pondHabitatPath(), "freshwater"));
        return IslandFishingPolicy.effective(IslandFishingPolicy.PondHabitat.parse(key),
            plugin.skyblock().resources().effectiveStage(owner));
    }

    private void changePondHabitat(Player player, FishingHolder holder) {
        var skyblock = plugin.skyblock();
        if (skyblock == null || holder.account == null || !player.isOnline()
            || Bukkit.getPlayer(player.getUniqueId()) != player || !holder.account.equals(account(player))) return;
        IslandService.Island island = skyblock.islands().owned(holder.islandOwner)
            .filter(value -> value.id().equals(holder.islandId) && value.member(holder.account)).orElse(null);
        if (island == null) {
            player.closeInventory();
            player.sendRichMessage("<yellow>You no longer belong to that co-op.</yellow>");
            return;
        }
        int stage = skyblock.resources().effectiveStage(island.owner());
        if (IslandFishingPolicy.unlocked(stage).size() < 2) return;
        IslandFishingPolicy.PondHabitat next = IslandFishingPolicy.next(pondHabitat(island.owner()), stage);
        try {
            plugin.playerData().update(island.owner(), yaml -> yaml.set(pondHabitatPath(), next.key()));
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save island pond habitat", error);
            player.sendRichMessage("<red>Your pond habitat could not be saved. Nothing changed.</red>");
            return;
        }
        player.sendRichMessage("<aqua>Pond habitat:</aqua> <white>" + next.title()
            + "</white><gray>. This applies to everyone fishing on this co-op's island.</gray>");
        open(player);
    }

    private static boolean rewardGameMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private Material habitatIcon(FishingCatch.Habitat habitat) {
        return switch (habitat) {
            case RIVER -> Material.WATER_BUCKET;
            case OCEAN -> Material.NAUTILUS_SHELL;
            case SWAMP -> Material.LILY_PAD;
            case COLD -> Material.BLUE_ICE;
            case INLAND -> Material.COMPASS;
        };
    }

    private int level(Player player) {
        return plugin.presentation().progression(player).skills()
            .getOrDefault(Skill.FISHING,
                new net.nerdsmp.api.service.ProgressionService.SkillProgress(0L, 0)).level();
    }

    private double luck(Player player) {
        var progression = plugin.presentation().progression(player);
        var profile = plugin.gearSets().profile(player);
        return profile.stat("fishing-luck-percent")
            + profile.stat("fishing-power") * 5.0D
            + profile.stat("treasure-find-percent")
            + progression.talentStat(Skill.FISHING, "treasure-find-percent")
            + progression.adventurerStat("treasure-find-percent");
    }

    private AccountId account(Player player) {
        return plugin.sessions().account(player.getUniqueId()).orElse(null);
    }

    private ItemStack named(Material material, int amount, String name, String lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(miniMessage.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private void decorate(Inventory inventory) {
        MenuKit.frame(inventory);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private String rarityColor(FishingRolls.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "<white>";
            case UNCOMMON -> "<green>";
            case RARE -> "<aqua>";
            case LEGENDARY -> "<gold>";
        };
    }

    private String displayName(FishingCatch catchType) {
        return title(catchType.itemId().value());
    }

    private String title(String raw) {
        return java.util.Arrays.stream(raw.toLowerCase(java.util.Locale.ROOT).split("[_-]"))
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private record CatchReward(ItemStack item, FishingCatch catchType) {
    }

    private record CatchContext(AccountId islandOwner, UUID islandId, int stage, FishingCatch.Habitat habitat,
                                boolean eligible, String reason) {
        private static CatchContext rejected(String reason) {
            return new CatchContext(null, null, 0, FishingCatch.Habitat.INLAND, false, reason);
        }
    }

    private record PreparedCatch(AccountId account, UUID hookId, Location hookLocation, Item item, FishingRolls.Rarity rarity,
                                 CatchReward reward, CatchContext context) { }

    private record CatchState(int streak, long total, int bestStreak, Instant lastCatch) {
        private static CatchState empty() {
            return new CatchState(0, 0L, 0, Instant.EPOCH);
        }
    }

    private record CatchRemoval(int slot, ItemStack template, int amount, FishingCatch catchType) {
    }

    private record FisheryQuote(List<CatchRemoval> removals, int items, int reputation) {
    }

    private static final class FishingHolder implements InventoryHolder {
        private final UUID playerId;
        private final AccountId account;
        private AccountId islandOwner;
        private UUID islandId;
        private Inventory inventory;

        private FishingHolder(UUID playerId, AccountId account) {
            this.playerId = playerId;
            this.account = account;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class FisheryHolder implements InventoryHolder {
        private final UUID playerId;
        private final AccountId account;
        private FisheryHolder(UUID playerId, AccountId account) { this.playerId = playerId; this.account = account; }
        private Inventory inventory;
        private int standing;
        private int eligible;
        private int reputation;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

}
