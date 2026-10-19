package net.nerdsmp.paper.gui;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import net.nerdsmp.api.service.CampaignService;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.engagement.QuestAction;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.item.GearSetService;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.item.RepairService;
import net.nerdsmp.paper.job.DiminishingJobService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.VisualFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class MenuManager implements Listener, AutoCloseable {
    private static final int[] SKILL_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 31};
    private static final int[] QUEST_SLOTS = {20, 22, 24, 10, 16, 28, 30, 32};

    private final NerdSmpPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Skill> selectedSkills = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> pendingTalents = new java.util.concurrent.ConcurrentHashMap<>();

    public MenuManager(NerdSmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory.getHolder() instanceof MenuHolder holder && holder.view == View.FORGE) {
                player.closeInventory();
                // Closing normally returns the inputs through onClose. The fallback is
                // idempotent and also covers shutdown before listener registration.
                returnForgeInputs(player, inventory);
            }
        }
        selectedSkills.clear();
        pendingTalents.clear();
    }

    public void openHome(Player player) {
        MenuInventory menu = menu(View.HOME, 45, plugin.branding().titleMarkup());
        decorate(menu.inventory());
        button(menu, 4, Material.NETHER_STAR, plugin.branding().titleMarkup(), List.of(
            "<gray>Choose where you want to go.</gray>"
        ), ignored -> { });
        navigate(menu, 11, Material.PLAYER_HEAD, "<aqua>Character</aqua>",
            List.of("<gray>Profile, skills, ranks, and gear.</gray>"), View.CHARACTER);
        navigate(menu, 13, Material.COMPASS, "<green>Adventure</green>",
            List.of("<gray>Quests, jobs, campaigns, and activities.</gray>"), View.ADVENTURE);
        navigate(menu, 15, Material.SMITHING_TABLE, "<light_purple>Workshop</light_purple>",
            List.of("<gray>Forge, enchant, brew, and repair.</gray>"), View.WORKSHOP);
        navigate(menu, 29, Material.EMERALD, "<gold>World & Trade</gold>",
            List.of(plugin.skyblockEnabled() ? "<gray>Markets, islands, banks, and travel.</gray>" : "<gray>Markets, claims, banks, and travel.</gray>"), View.WORLD);
        navigate(menu, 31, Material.LEAD, "<green>Community</green>",
            List.of("<gray>Parties, friends, social tools, and trades.</gray>"), View.COMMUNITY);
        navigate(menu, 33, Material.COMPARATOR, "<white>Settings</white>",
            List.of("<gray>Choose what you see and who can reach you.</gray>"), View.SETTINGS);
        if (plugin.skyblockEnabled()) commandButton(menu, 22, Material.GRASS_BLOCK, "<aqua>My Island</aqua>",
            List.of("<gray>Home, co-op, island levels, and talents.</gray>"), "island");
        rawButton(menu, MenuKit.closeSlot(45), MenuKit.close(), Player::closeInventory);
        if (player.hasPermission("nerdsmp.staff")) {
            commandButton(menu, 44, Material.SPYGLASS, "<red>Staff Desk</red>", List.of(
                "<gray>Reports, player records, and moderation tools.</gray>"
            ), "staff");
        }
        open(player, menu.inventory());
    }

    private void openCharacter(Player player) {
        MenuInventory menu = detailMenu(View.CHARACTER, 45, "<aqua><bold>Character</bold></aqua>");
        navigate(menu, 11, Material.PLAYER_HEAD, "<aqua>Profile</aqua>",
            List.of(plugin.skyblockEnabled() ? "<gray>Balance, island, and equipped stats.</gray>" : "<gray>Balance, claims, and equipped stats.</gray>"), View.PROFILE);
        navigate(menu, 13, Material.EXPERIENCE_BOTTLE, "<green>Skills & Talents</green>",
            List.of("<gray>Train skills and shape their talent trees.</gray>"), View.SKILLS);
        navigate(menu, 15, Material.NETHER_STAR, "<light_purple>Adventurer Ranks</light_purple>",
            List.of("<gray>Your long-term journey and rank perks.</gray>"), View.ADVENTURER_RANKS);
        navigate(menu, 29, Material.AMETHYST_SHARD, "<light_purple>Relic Collections</light_purple>",
            List.of("<gray>Sets, sockets, and equipped bonuses.</gray>"), View.RELICS);
        navigate(menu, 31, Material.ARMOR_STAND, "<gold>Cosmetic Wardrobe</gold>",
            List.of("<gray>Your account-bound cosmetics.</gray>"), View.COSMETICS);
        navigate(menu, 33, Material.COMPARATOR, "<white>Player Settings</white>",
            List.of("<gray>Privacy, chat and personal displays.</gray>"), View.SETTINGS);
        commandButton(menu, 22, Material.GOLD_INGOT, "<gold>Leaderboards</gold>", List.of("<gray>Compare skills, islands, achievements and Marks.</gray>"), "leaderboard");
        open(player, menu.inventory());
    }

    private void openAdventure(Player player) {
        MenuInventory menu = detailMenu(View.ADVENTURE, 45, "<green><bold>Adventure</bold></green>");
        navigate(menu, 11, Material.CLOCK, "<aqua>Daily Contracts</aqua>",
            List.of("<gray>Focused goals that reset at 00:00 UTC.</gray>"), View.QUESTS);
        navigate(menu, 13, Material.WRITABLE_BOOK, plugin.skyblockEnabled() ? "<gold>Work & Trade</gold>" : "<gold>Jobs & Orders</gold>",
            List.of("<gray>Earn Marks through useful work.</gray>"), View.JOBS);
        navigate(menu, 15, Material.BEACON, "<gradient:#C59AFA:#8CE8ED>Campaign</gradient>",
            List.of("<gray>Milestones, supplies, and finales.</gray>"), View.CAMPAIGN);
        commandButton(menu, 29, Material.FISHING_ROD, "<aqua>Angler's Journal</aqua>",
            List.of("<gray>Catches, streaks, treasure, and encounters.</gray>"), "fishing");
        commandButton(menu, 31, Material.BRUSH, "<yellow>Excavation Field Notes</yellow>",
            List.of("<gray>Survey chains and buried finds.</gray>"), "excavation");
        commandButton(menu, 33, Material.NETHER_STAR, "<gold>Achievements</gold>",
            List.of("<gray>Track milestones, points, and rewards.</gray>"), "achievements");
        open(player, menu.inventory());
    }

    private void openWorkshop(Player player) {
        MenuInventory menu = detailMenu(View.WORKSHOP, 45, "<light_purple><bold>Workshop</bold></light_purple>");
        stationButton(menu, player, 11, Material.SMITHING_TABLE, "light_purple", RemoteStation.FORGE,
            List.of("<gray>Improve gear, socket gems, and recover them.</gray>"), target -> open(target, View.FORGE));
        stationButton(menu, player, 13, Material.ENCHANTING_TABLE, "light_purple", RemoteStation.ENCHANTING,
            List.of("<gray>Spend Arcane Dust on tailored enchantments.</gray>"), target -> open(target, View.ENCHANTMENTS));
        stationButton(menu, player, 15, Material.BREWING_STAND, "green", RemoteStation.ALCHEMY,
            List.of("<gray>Potions, poisons, arrows, and coatings.</gray>"), runCommand(menu, "alchemy"));
        commandButton(menu, 29, Material.CRAFTING_TABLE, "<gold>Workshop Recipes</gold>",
            List.of("<gray>Browse recipes and Smithing requirements.</gray>"), "recipes");
        commandButton(menu, 22, Material.COOKED_CHICKEN, "<gold>Harvest Kitchen</gold>", List.of("<gray>" + plugin.recipes().mealCount() + " meals, ingredients and Cooking unlocks.</gray>", "<gray>Meals improve with the cook's level.</gray>"), "cooking");
        stationButton(menu, player, 31, Material.SPYGLASS, "yellow", RemoteStation.IDENTIFY,
            List.of("<gray>Identify one item or a whole batch.</gray>",
                "<gray>Scrap sealed gear for Arcane Dust.</gray>"), runCommand(menu, "identify"));
        stationButton(menu, player, 33, Material.ANVIL, "aqua", RemoteStation.REPAIR,
            List.of("<gray>Repair damaged equipment throughout your inventory.</gray>",
                "<gray>Repair one item at any anvil's forge.</gray>"), runCommand(menu, "repairall"));
        open(player, menu.inventory());
    }

    private void openWorld(Player player) {
        MenuInventory menu = detailMenu(View.WORLD, 45, "<gold><bold>World & Trade</bold></gold>");
        commandButton(menu, 11, Material.EMERALD, "<green>Player Stores</green>",
            List.of("<gray>Browse player supplies, buy and sell from anywhere.</gray>"), "store");
        if (plugin.skyblockEnabled()) commandButton(menu, 13, Material.GRASS_BLOCK, "<aqua>My Island</aqua>",
            List.of("<gray>Home, co-op, island levels, and talents.</gray>"), "island");
        else navigate(menu, 13, Material.GOLDEN_SHOVEL, "<yellow>Homestead</yellow>",
            List.of("<gray>Protect and manage your land.</gray>"), View.CLAIMS);
        stationButton(menu, player, 15, Material.ENDER_CHEST, "gold", RemoteStation.BANK,
            List.of("<gray>Store valuables across every world.</gray>", "<gray>Unlock more slots with Marks.</gray>"), runCommand(menu, "bank"));
        commandButton(menu, 22, Material.CHEST, "<gold>Shops</gold>",
            List.of(plugin.skyblockEnabled() ? "<gray>Buy supplies and sell surplus from anywhere.</gray>"
                : "<gray>Browse merchants, stock and directions.</gray>",
                plugin.skyblockEnabled() ? "<gray>Choose an item, check the price, then confirm.</gray>"
                : "<gray>Visit the merchant to trade.</gray>"), "shops");
        commandButton(menu, 20, Material.CLOCK, "<gold>Auction House</gold>",
            List.of("<gray>Timed bidding and Buy Now offers.</gray>", "<gray>Track sales and collect wins.</gray>"), "ah");
        commandButton(menu, 24, Material.ENDER_CHEST, "<aqua>Market Deliveries</aqua>",
            List.of("<gray>Collect purchases, wins and returned listings.</gray>"), "market inbox");
        commandButton(menu, 29, Material.FILLED_MAP, "<aqua>Faction Standing</aqua>",
            List.of("<gray>Reputation ranks, access, and shop rates.</gray>"), "reputation");
        commandButton(menu, 31, Material.ENDER_EYE, plugin.skyblockEnabled() ? "<light_purple>Expeditions</light_purple>" : "<light_purple>Warps</light_purple>",
            List.of("<gray>Browse the places you can travel.</gray>"), plugin.skyblockEnabled() ? "expedition" : "warps");
        commandButton(menu, 33, Material.RECOVERY_COMPASS, "<aqua>Return to Spawn</aqua>",
            List.of("<gray>Begin safe travel back to the city.</gray>"), "spawn");
        open(player, menu.inventory());
    }

    private void openCommunity(Player player) {
        MenuInventory menu = detailMenu(View.COMMUNITY, 45, "<green><bold>Community</bold></green>");
        commandButton(menu, 11, Material.LEAD, "<green>Party</green>",
            List.of("<gray>Group up for events and shared rewards.</gray>"), "party");
        commandButton(menu, 13, Material.OAK_SIGN, "<aqua>Chat Channels</aqua>",
            List.of("<gray>Choose who hears your messages.</gray>", plugin.skyblockEnabled()
                ? "<gray>Co-op chat: /island chat [owner]</gray>" : "<gray>Settlement chat: /claim chat [owner]</gray>"), "channel");
        commandButton(menu, 15, Material.PLAYER_HEAD, "<gold>Friends</gold>",
            List.of("<gray>Manage friends and online notices.</gray>"), "friends");
        commandButton(menu, 29, Material.CHEST, "<light_purple>Player Trade</light_purple>",
            List.of("<gray>Trade with online players from anywhere.</gray>", "<gray>Use /trade \\<player>, then review both offers.</gray>",
                "<gray>Items move only after you both confirm.</gray>"), "trade");
        navigate(menu, 31, Material.COMPARATOR, "<white>Player Settings</white>",
            List.of("<gray>Control requests, notices, and messages.</gray>"), View.SETTINGS);
        commandButton(menu, 33, Material.BOOK, "<white>Help</white>",
            List.of("<gray>See useful commands and next steps.</gray>"), "quickstart");
        open(player, menu.inventory());
    }

    public void open(Player player, View view) {
        if ((view == View.FORGE || view == View.ENCHANTMENTS) && !RemoteStation.remote(player)) {
            player.sendRichMessage((view == View.FORGE ? RemoteStation.FORGE : RemoteStation.ENCHANTING).refusalMarkup());
            return;
        }
        openTrusted(player, view);
    }

    public void openStation(Player player, View view) {
        if (view != View.FORGE && view != View.ENCHANTMENTS) {
            throw new IllegalArgumentException("Not a station view: " + view);
        }
        openTrusted(player, view);
    }

    private void openTrusted(Player player, View view) {
        switch (view) {
            case HOME -> openHome(player);
            case CHARACTER -> openCharacter(player);
            case ADVENTURE -> openAdventure(player);
            case WORKSHOP -> openWorkshop(player);
            case WORLD -> openWorld(player);
            case COMMUNITY -> openCommunity(player);
            case PROFILE -> openProfile(player);
            case SKILLS -> openSkills(player);
            case SKILL_TREE -> openSelectedSkillTree(player);
            case ADVENTURER_RANKS -> openAdventurerRanks(player);
            case JOBS -> openJobs(player);
            case ORDERS -> openOrders(player);
            case RELICS -> openRelics(player);
            case CAMPAIGN -> openCampaign(player);
            case MARKET -> openMarket(player);
            case CLAIMS -> openClaims(player);
            case COSMETICS -> openCosmetics(player);
            case SETTINGS -> openSettings(player);
            case FORGE -> openForge(player);
            case ENCHANTMENTS -> openEnchantments(player);
            case QUESTS -> openQuests(player);
            case CONFIRM_TALENT -> openTalentConfirmation(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.view == View.FORGE) {
            onForgeClick(event, holder);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
            || event.getRawSlot() >= event.getView().getTopInventory().getSize()
            || event.getClick() != org.bukkit.event.inventory.ClickType.LEFT || !holder.current(player, plugin)) {
            return;
        }
        Consumer<Player> action = holder.actions.get(event.getRawSlot());
        if (action != null) {
            MenuKit.clickSound(player);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (holder.current(player, plugin) && player.getOpenInventory().getTopInventory() == holder.inventory) action.accept(player);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.view != View.FORGE) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!(event.getWhoClicked() instanceof Player actor) || !forgeSession(holder, actor)) {
            event.setCancelled(true); return;
        }
        if (holder.forgeRepairPending) {
            event.setCancelled(true);
            return;
        }
        boolean touchesLockedSlot = event.getRawSlots().stream().anyMatch(slot ->
            slot < event.getView().getTopInventory().getSize() && !isForgeInput(slot)
        );
        event.setCancelled(touchesLockedSlot);
        if (!touchesLockedSlot && event.getWhoClicked() instanceof Player player) {
            clearForgeReview(holder);
            scheduleForgeRender(holder, player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder && holder.view == View.FORGE
            && event.getPlayer() instanceof Player player) {
            clearForgeReview(holder);
            returnForgeInputs(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || !event.getClickedBlock().getType().name().endsWith("ANVIL")) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                openForge(event.getPlayer());
            }
        });
    }

    private void openProfile(Player player) {
        var account = account(player);
        if (account == null) {
            return;
        }
        var balance = plugin.economy().balance(account).toCompletableFuture();
        var progression = plugin.progression().snapshot(account).toCompletableFuture();
        var claimCount = plugin.claims().claimCount(account).toCompletableFuture();
        var capacity = plugin.claims().capacity(account).toCompletableFuture();
        java.util.concurrent.CompletableFuture.allOf(balance, progression, claimCount, capacity)
            .thenRun(() -> sync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                var snapshot = progression.join();
                AdventurerProgression.Progress adventurer = snapshot.adventurer();
                GearSetService.GearProfile gear = plugin.gearSets().profile(player);
                MenuInventory menu = detailMenu(View.PROFILE, 45, "<aqua><bold>Profile</bold></aqua>");
                button(menu, 13, Material.PLAYER_HEAD, "<white><bold>" + player.getName() + "</bold></white>", List.of(
                    "<gray>Marks</gray> " + Chat.marksTag(balance.join()),
                    "<gray>Adventurer</gray> <aqua>Level " + adventurer.level() + "</aqua>",
                    "<gray>Rank</gray> <light_purple>" + adventurer.rank().displayName() + "</light_purple>",
                    adventurerProgress(adventurer, 12),
                    plugin.skyblock() == null ? "<gray>Claims</gray> <yellow>" + claimCount.join() + " / " + capacity.join() + "</yellow>"
                        : plugin.skyblock().islands().owned(account).map(i -> "<gray>Island</gray> <aqua>" + escape(i.name()) + " • Level " + plugin.skyblock().development().progress(account).level() + "</aqua>").orElse("<gray>Create your home with /island create.</gray>"),
                    "<gray>Defense</gray> <white>" + Math.round(gear.stat("defense")
                        + snapshot.adventurerStat("defense")) + "</white>",
                    "<gray>Damage Bonus</gray> <white>" + Math.round(gear.stat("damage-percent")
                        + snapshot.adventurerStat("damage-percent")) + "%</white>"
                ), ignored -> { });
                navigate(menu, 20, Material.EXPERIENCE_BOTTLE, "<green>Skills</green>", List.of(), View.SKILLS);
                navigate(menu, 22, Material.NETHER_STAR, "<light_purple>Adventurer Ranks</light_purple>", List.of(),
                    View.ADVENTURER_RANKS);
                navigate(menu, 24, Material.AMETHYST_SHARD, "<light_purple>Gear</light_purple>", List.of(), View.RELICS);
                if (plugin.skyblockEnabled()) commandButton(menu, 31, Material.GRASS_BLOCK, "<aqua>My Island</aqua>", List.of(), "island");
                else navigate(menu, 31, Material.GOLDEN_SHOVEL, "<yellow>Homestead</yellow>", List.of(), View.CLAIMS);
                open(player, menu.inventory());
            }));
    }

    private void openSkills(Player player) {
        var account = account(player);
        if (account == null) {
            return;
        }
        plugin.progression().snapshot(account).thenAccept(snapshot -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            MenuInventory menu = detailMenu(View.SKILLS, 54, "<green><bold>Skills</bold></green>");
            AdventurerProgression.Progress adventurer = snapshot.adventurer();
            button(menu, 4, Material.NETHER_STAR, "<light_purple><bold>Adventurer " + adventurer.level()
                + "</bold></light_purple>", List.of(
                    "<gray>Rank</gray> <white>" + adventurer.rank().displayName() + "</white>",
                    adventurerProgress(adventurer, 14),
                    "<dark_gray>All skill XP also advances Adventurer Level.</dark_gray>"
                ), ignored -> openAdventurerRanks(player));
            int index = 0;
            for (Skill skill : Skill.values()) {
                ProgressionService.SkillProgress progress = snapshot.skills().get(skill);
                boolean mastered = snapshot.masteries().contains(skill);
                List<String> lore = new ArrayList<>();
                lore.add("<gray>" + skillTrainingHint(skill) + "</gray>");
                lore.add("");
                long floor = ProgressionService.experienceForLevel(progress.level());
                long ceiling = progress.level() >= ProgressionService.MAX_LEVEL
                    ? floor : ProgressionService.experienceForLevel(progress.level() + 1);
                long current = Math.max(0L, progress.experience() - floor);
                long needed = Math.max(1L, ceiling - floor);
                lore.add("<gray>Level</gray> <white>" + progress.level() + "/" + ProgressionService.MAX_LEVEL + "</white>");
                lore.add(progress.level() >= ProgressionService.MAX_LEVEL
                    ? "<gold>Maximum level</gold>"
                    : VisualFormat.progressBar(current, needed, 10) + " <white>" + VisualFormat.compact(current)
                        + "/" + VisualFormat.compact(needed) + " XP</white>");
                lore.add(mastered ? "<gold>★ Seasonal Mastery</gold>" : "<dark_gray>Not mastered</dark_gray>");
                lore.add("<gray>Talent points</gray> <yellow>" + snapshot.talentPointsAvailable(skill) + " ready</yellow>"
                    + " <dark_gray>• " + snapshot.talentPointsSpent(skill) + " spent</dark_gray>");
                lore.add("");
                lore.add("<yellow>Click to open the skill tree.</yellow>");
                button(menu, SKILL_SLOTS[index++], skillMaterial(skill), "<aqua>" + skill.displayName()
                    + "</aqua> <dark_gray>•</dark_gray> <white>Lv " + progress.level() + "</white>",
                    lore, target -> openSkillTree(target, skill));
            }
            button(menu, 40, Material.COMPASS, "<white>How Progression Connects</white>", List.of(
                "<gray>Actions earn XP in the skill you use.</gray>",
                "<gray>That XP also raises Adventurer Level.</gray>",
                "<gray>Skill levels award points for that skill tree.</gray>",
                "<gray>At level 25, a skill can become a mastery.</gray>"
            ), ignored -> { });
            button(menu, 50, Material.NETHER_STAR, "<light_purple>Adventurer Rank Path</light_purple>", List.of(
                "<gray>See every rank and its permanent perk.</gray>",
                "<yellow>Click to view.</yellow>"
            ), this::openAdventurerRanks);
            open(player, menu.inventory());
        }));
    }

    public void openSkillTree(Player player, Skill skill) {
        selectedSkills.put(player.getUniqueId(), skill);
        var account = account(player);
        if (account == null) {
            return;
        }
        plugin.progression().snapshot(account).thenAccept(snapshot -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            ProgressionService.SkillProgress progress = snapshot.skills().get(skill);
            MenuInventory menu = menu(View.SKILL_TREE, 54,
                "<aqua><bold>" + skill.displayName() + " Tree</bold></aqua>");
            decorate(menu.inventory());
            button(menu, 0, skillMaterial(skill), "<aqua>" + skill.displayName() + " " + progress.level() + "</aqua>",
                SkillTreePresentation.overview(snapshot, skill), ignored -> { });
            addMasteryButton(menu, player, skill, snapshot, progress);

            SkillTreeCatalog.SkillTree tree = SkillTreeCatalog.tree(skill);
            for (SkillTreeCatalog.TalentBranch branch : tree.branches()) {
                int branchIndex = tree.branches().indexOf(branch);
                button(menu, SkillTreePresentation.branchSlot(branchIndex), branchMaterial(branchIndex),
                    "<white><bold>" + branch.name() + "</bold></white>",
                    SkillTreePresentation.branch(snapshot, skill, branch), ignored -> { });
                for (int nodeIndex = 0; nodeIndex < branch.nodes().size(); nodeIndex++) {
                    SkillTreeCatalog.TalentNode node = branch.nodes().get(nodeIndex);
                    SkillTreePresentation.NodeView preview = SkillTreePresentation.node(snapshot, node);
                    TalentState state = TalentState.valueOf(preview.state().name());
                    Consumer<Player> action = state == TalentState.AVAILABLE
                        ? target -> {
                            pendingTalents.put(target.getUniqueId(), node.id());
                            open(target, View.CONFIRM_TALENT);
                        }
                        : ignored -> { };
                    button(menu, SkillTreePresentation.nodeSlot(branchIndex, node.tier()), talentMaterial(state),
                        talentColor(state) + node.name(), preview.lore(), action);
                    if (nodeIndex < 2) {
                        boolean linked = snapshot.talents().contains(node.id());
                        button(menu, SkillTreePresentation.linkSlot(branchIndex, node.tier()), linked
                            ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                            "<dark_gray>│</dark_gray>", List.of(), ignored -> { });
                    }
                }
            }
            backButton(menu, StationLayout.BACK, View.SKILL_TREE);
            rawButton(menu, StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
            open(player, menu.inventory());
        }));
    }

    public void openAdventurerRanks(Player player) {
        var account = account(player);
        if (account == null) {
            return;
        }
        plugin.progression().snapshot(account).thenAccept(snapshot -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            AdventurerProgression.Progress progress = snapshot.adventurer();
            MenuInventory menu = menu(View.ADVENTURER_RANKS, 54, "<light_purple><bold>Adventurer Ranks</bold></light_purple>");
            decorate(menu.inventory());
            button(menu, 4, Material.NETHER_STAR, "<light_purple><bold>" + progress.rank().displayName()
                + "</bold></light_purple>", List.of(
                    "<gray>Adventurer Level</gray> <aqua>" + progress.level() + "/100</aqua>",
                    adventurerProgress(progress, 14),
                    "<dark_gray>Combined XP from all nine skills.</dark_gray>"
                ), ignored -> { });
            int[] rankSlots = {10, 12, 14, 16, 34, 32, 30, 28};
            int[] connectorSlots = {11, 13, 15, 25, 33, 31, 29};
            AdventurerProgression.Rank[] ranks = AdventurerProgression.Rank.values();
            for (int index = 0; index < ranks.length; index++) {
                AdventurerProgression.Rank rank = ranks[index];
                boolean unlocked = progress.level() >= rank.requiredLevel();
                boolean current = progress.rank() == rank;
                Material material = current ? Material.NETHER_STAR : unlocked ? Material.LIME_DYE : Material.GRAY_DYE;
                String color = current ? "<gold>" : unlocked ? "<green>" : "<gray>";
                button(menu, rankSlots[index], material, color + rank.displayName(), List.of(
                    "<gray>Unlocks at Adventurer " + rank.requiredLevel() + "</gray>",
                    "<white>" + rank.perk() + "</white>",
                    current ? "<gold>Current rank</gold>" : unlocked ? "<green>Unlocked</green>" : "<dark_gray>Locked</dark_gray>"
                ), ignored -> { });
                if (index < connectorSlots.length) {
                    button(menu, connectorSlots[index], unlocked ? Material.LIME_STAINED_GLASS_PANE
                        : Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of(), ignored -> { });
                }
            }
            backButton(menu, StationLayout.BACK, View.ADVENTURER_RANKS);
            rawButton(menu, StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
            open(player, menu.inventory());
        }));
    }

    private void openSelectedSkillTree(Player player) {
        openSkillTree(player, selectedSkills.getOrDefault(player.getUniqueId(), Skill.MINING));
    }

    private void openTalentConfirmation(Player player) {
        String talentId = pendingTalents.get(player.getUniqueId());
        SkillTreeCatalog.TalentNode node = SkillTreeCatalog.node(talentId).orElse(null);
        if (node == null) {
            open(player, View.SKILLS);
            return;
        }
        MenuInventory menu = menu(View.CONFIRM_TALENT, 27, "<yellow><bold>Spend a Talent Point?</bold></yellow>");
        decorate(menu.inventory());
        button(menu, 11, Material.ARROW, "<gray>Go Back</gray>", List.of(), target -> openSkillTree(target, node.skill()));
        button(menu, 15, Material.LIME_DYE, "<green>Unlock " + node.name() + "</green>", List.of(
            "<white>" + node.effect() + "</white>",
            "<gray>Costs one " + node.skill().displayName() + " talent point.</gray>",
            "<gold>This choice lasts for the season.</gold>",
            "<gray>Mastery respecs do not refund talent points.</gray>",
            "<yellow>Click again to confirm.</yellow>"
        ), target -> unlockTalent(target, node));
        open(player, menu.inventory());
    }

    private void unlockTalent(Player player, SkillTreeCatalog.TalentNode node) {
        var account = account(player);
        if (account == null) {
            return;
        }
        plugin.progression().unlockTalent(account, node.id()).thenAccept(result -> sync(() -> {
            pendingTalents.remove(player.getUniqueId());
            player.sendRichMessage(switch (result) {
                case UNLOCKED -> "<green>Unlocked " + node.name() + ".</green>";
                case ALREADY_UNLOCKED -> "<gray>That talent is already unlocked.</gray>";
                case LEVEL_TOO_LOW -> "<red>Your " + node.skill().displayName() + " level is too low.</red>";
                case PREREQUISITE_MISSING -> "<red>Unlock the previous node first.</red>";
                case NO_POINTS_AVAILABLE -> "<red>You do not have a talent point to spend.</red>";
                case UNKNOWN_TALENT -> "<red>That talent no longer exists.</red>";
            });
            plugin.presentation().refresh(player);
            openSkillTree(player, node.skill());
        }));
    }

    private void addMasteryButton(
        MenuInventory menu,
        Player player,
        Skill skill,
        ProgressionService.ProgressionSnapshot snapshot,
        ProgressionService.SkillProgress progress
    ) {
        boolean mastered = snapshot.masteries().contains(skill);
        boolean available = !mastered && progress.level() >= ProgressionService.MASTERY_LEVEL
            && snapshot.masteries().size() < ProgressionService.MAX_MASTERIES;
        button(menu, 8, mastered ? Material.NETHER_STAR : available ? Material.YELLOW_DYE : Material.GRAY_DYE,
            mastered ? "<gold>★ Mastered</gold>" : "<gray>Seasonal Mastery</gray>", List.of(
                mastered ? "<white>This is one of your two masteries.</white>"
                    : "<gray>Available at " + skill.displayName() + " 25.</gray>",
                available ? "<yellow>Click to select.</yellow>" : "<dark_gray>Two masteries per season.</dark_gray>"
            ), available ? target -> openMasteryConfirmation(target, skill) : ignored -> { });
    }

    private Material talentMaterial(TalentState state) {
        return switch (state) {
            case UNLOCKED -> Material.LIME_DYE;
            case AVAILABLE -> Material.YELLOW_DYE;
            case LEVEL_LOCKED, PATH_LOCKED -> Material.GRAY_DYE;
            case NO_POINTS -> Material.RED_DYE;
        };
    }

    private String talentColor(TalentState state) {
        return switch (state) {
            case UNLOCKED -> "<green>";
            case AVAILABLE -> "<yellow>";
            case LEVEL_LOCKED, PATH_LOCKED -> "<gray>";
            case NO_POINTS -> "<red>";
        };
    }

    private Material branchMaterial(int index) {
        return switch (index) {
            case 0 -> Material.EXPERIENCE_BOTTLE;
            case 1 -> Material.GOLD_INGOT;
            default -> Material.AMETHYST_SHARD;
        };
    }

    private void openJobs(Player player) {
        var account = account(player);
        if (account == null) {
            return;
        }
        plugin.jobs().snapshot(account).thenAccept(snapshot -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            MenuInventory menu = detailMenu(View.JOBS, 45, plugin.skyblockEnabled() ? "<gold><bold>Work & Trade</bold></gold>" : "<gold><bold>Jobs & Orders</bold></gold>");
            int index = 0;
            for (Skill skill : Skill.values()) {
                if (skill == Skill.ENCHANTING) continue; // Enchanting is a craft, not paid work.
                long units = snapshot.workUnits().getOrDefault(skill, 0L);
                long marks = snapshot.earnedMarks().getOrDefault(skill, 0L);
                button(menu, SKILL_SLOTS[index++], skillMaterial(skill), "<yellow>" + skill.displayName() + " Work</yellow>", List.of(
                    VisualFormat.progressBar(units, snapshot.maximumWorkUnits(), 10)
                        + " <white>" + units + "/" + snapshot.maximumWorkUnits() + "</white>",
                    "<gray>Marks earned today</gray> " + Chat.marksTag(marks),
                    "<dark_gray>Rewards taper off as the bar fills.</dark_gray>"
                ), ignored -> { });
            }
            navigate(menu, 31, Material.WRITABLE_BOOK, plugin.skyblockEnabled() ? "<gold>City Merchants</gold>" : "<gold>Rotating Server Orders</gold>", List.of(
                plugin.skyblockEnabled() ? "<gray>Buy and sell in the city. Prices follow demand.</gray>" : "<gray>Turn in items the server currently needs.</gray>"
            ), View.ORDERS);
            open(player, menu.inventory());
        }));
    }

    private void openOrders(Player player) {
        if (plugin.skyblockEnabled()) { openCityMerchants(player); return; }
        MenuInventory menu = detailMenu(View.ORDERS, 45, "<gold><bold>Server Orders</bold></gold>");
        var orders = plugin.orders().activeOrders();
        if (orders.isEmpty()) {
            button(menu, 22, Material.BARRIER, "<gray>All Orders Fulfilled</gray>",
                List.of("<dark_gray>Check back after the next order rotation.</dark_gray>"), ignored -> { });
        } else {
            int slot = 10;
            for (var order : orders) {
                button(menu, slot++, order.material(), "<gold>" + escape(order.title()) + "</gold>", List.of(
                    "<gray>Order ID</gray> <white>" + order.id() + "</white>",
                    "<gray>Demand remaining</gray> <white>" + order.remainingItems() + " items</white>",
                    "<gray>Payout</gray> " + Chat.marksTag(order.marksPerUnit()) + " <gray>per " + order.unitSize() + "</gray>",
                    "",
                    "<yellow>Hold the material, then use</yellow>",
                    "<white>/jobs fulfill " + order.id() + " [amount]</white>"
                ), target -> {
                    target.closeInventory();
                    target.sendRichMessage("<yellow>Hold the requested items and run <white>/jobs fulfill "
                        + order.id() + " [amount]</white>.</yellow>");
                });
                if (slot == 17) {
                    slot = 19;
                }
            }
        }
        open(player, menu.inventory());
    }

    private void openCityMerchants(Player player) {
        MenuInventory menu = detailMenu(View.ORDERS, 45, "<gold><bold>City Merchants</bold></gold>");
        var merchants = plugin.npcs().content().npcs().values().stream()
            .filter(npc -> npc.shopId() != null && plugin.npcs().content().shops().containsKey(npc.shopId()))
            .sorted(java.util.Comparator.comparing(net.nerdsmp.paper.npc.NpcContent.NpcDefinition::id)).limit(21).toList();
        int index = 0;
        for (var npc : merchants) {
            int slot = 10 + index / 7 * 9 + index % 7; index++;
            button(menu, slot, Material.CHEST, "<gold>" + escape(npc.name()) + "</gold>", List.of(
                "<gray>" + escape(npc.title()) + "</gray>", "<gray>Bring goods to their stall at spawn.</gray>",
                "<gray>Review buy and sell quotes before trading.</gray>", "<gray>Rates change with server-wide demand.</gray>",
                "<yellow>Click to travel to the city.</yellow>"
            ), target -> { target.closeInventory(); target.performCommand("spawn"); });
        }
        if (merchants.isEmpty()) button(menu, 22, Material.BARRIER, "<gray>No merchants configured</gray>", List.of("<gray>Ask staff about the city's trading stalls.</gray>"), ignored -> { });
        open(player, menu.inventory());
    }

    private void openRelics(Player player) {
        MenuInventory menu = detailMenu(View.RELICS, 54, "<light_purple><bold>Relic Collections</bold></light_purple>");
        GearSetService.GearProfile profile = plugin.gearSets().profile(player);
        int slot = 10;
        for (PaperItemService.SetDefinition set : plugin.items().setDefinitions()) {
            int equipped = profile.setPieces().getOrDefault(set.id(), 0);
            List<String> lore = new ArrayList<>();
            int fullBonus = set.bonuses().keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
            lore.add(VisualFormat.progressBar(equipped, fullBonus, 10)
                + " <white>" + equipped + " equipped | full bonus at " + fullBonus + "</white>");
            set.bonuses().forEach((pieces, bonus) -> lore.add((equipped >= pieces ? "<green>ACTIVE " : "<dark_gray>LOCKED ")
                + pieces + "pc" + (equipped >= pieces ? "</green>" : "</dark_gray>") + " <gray>" + escape(bonus.description()) + "</gray>"));
            lore.add("<gray>Wear matching armor and hold its weapon or tool.</gray>");
            lore.add("<gray>Backpack items and duplicate pieces do not count.</gray>");
            lore.add("");
            lore.add("<dark_gray>Relics can be traded.</dark_gray>");
            button(menu, slot++, setMaterial(set.id()), "<light_purple>" + escape(set.name()) + "</light_purple>", lore, ignored -> { });
            if (slot == 17) {
                slot = 19;
            }
        }
        button(menu, 40, Material.IRON_CHESTPLATE, "<aqua>Equipped Gear Profile</aqua>", List.of(
            "<gray>Defense</gray> <white>" + Math.round(profile.stat("defense")) + "</white>",
            "<gray>Flat Damage</gray> <white>+" + Math.round(profile.stat("damage-flat")) + "</white>",
            "<gray>Damage Bonus</gray> <white>+" + Math.round(profile.stat("damage-percent")) + "%</white>"
        ), ignored -> { });
        open(player, menu.inventory());
    }

    private void openCampaign(Player player) {
        plugin.campaigns().activeCampaign().thenAccept(active -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            MenuInventory menu = detailMenu(View.CAMPAIGN, 45, "<gradient:#C59AFA:#8CE8ED><bold>Campaign</bold></gradient>");
            if (active.isEmpty()) {
                button(menu, 22, Material.ENDER_EYE, "<gray>No Active Campaign</gray>",
                    List.of("<dark_gray>The next campaign has not started.</dark_gray>"), ignored -> { });
            } else {
                CampaignService.CampaignSnapshot campaign = active.get();
                long finalTarget = campaign.milestones().values().stream().mapToLong(Long::longValue).max().orElse(1L);
            button(menu, 13, Material.BEACON, "<gradient:#C59AFA:#8CE8ED>" + escape(campaign.title()) + "</gradient>", List.of(
                    VisualFormat.progressBar(campaign.totalPoints(), finalTarget, 14)
                        + " <white>" + VisualFormat.compact(campaign.totalPoints()) + "/" + VisualFormat.compact(finalTarget) + "</white>",
                    "<gray>Ends</gray> <white>" + java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'")
                        .withZone(java.time.ZoneOffset.UTC).format(campaign.endsAt()) + "</white>"
                ), ignored -> { });
                int slot = 20;
                for (Map.Entry<String, Long> category : campaign.categoryPoints().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    button(menu, slot++, Material.ENDER_EYE, "<aqua>" + VisualFormat.titleCase(category.getKey()) + "</aqua>", List.of(
                        "<gray>Contributed</gray> <white>" + VisualFormat.compact(category.getValue()) + " points</white>",
                        "",
                        "<yellow>Hold items and use</yellow>",
                        "<white>/campaign contribute " + category.getKey() + " [amount]</white>"
                    ), target -> {
                        target.closeInventory();
                        target.sendRichMessage("<yellow>Hold contribution items and run <white>/campaign contribute "
                            + category.getKey() + " [amount]</white>.</yellow>");
                    });
                }
                int milestoneSlot = 29;
                for (var milestone : plugin.serverProgression().views(campaign)) {
                    long shownTarget = milestone.requiredPoints() == Long.MAX_VALUE ? 0L : milestone.requiredPoints();
                    button(menu, milestoneSlot++, milestone.unlock().material(),
                        milestone.unlocked() ? "<green>✓ " + milestone.unlock().displayName() + "</green>"
                            : "<gray>Locked: " + milestone.unlock().displayName() + "</gray>", List.of(
                            "<gray>" + milestone.unlock().description() + "</gray>",
                            milestone.unlocked() ? "<green>Unlocked server-wide</green>"
                                : VisualFormat.progressBar(campaign.totalPoints(), shownTarget, 10)
                                    + " <white>" + VisualFormat.compact(campaign.totalPoints()) + "/"
                                    + VisualFormat.compact(shownTarget) + "</white>",
                            "<dark_gray>Milestone: " + milestone.milestoneId() + "</dark_gray>"
                        ), ignored -> { });
                }
            }
            open(player, menu.inventory());
        }));
    }

    private void openMarket(Player player) {
        plugin.marketController().open(player);
    }

    private void openClaims(Player player) {
        if (plugin.skyblock() != null) { plugin.skyblock().handle(player, new String[0]); return; }
        plugin.homesteads().open(player);
    }


    private void openMasteryConfirmation(Player player, Skill skill) {
        MenuInventory menu = menu(View.SKILLS, 27, "<gold><bold>Select Mastery?</bold></gold>");
        decorate(menu.inventory());
        navigate(menu, 11, Material.ARROW, "<gray>Cancel Selection</gray>", List.of(), View.SKILLS);
        button(menu, 15, skillMaterial(skill), "<gold>Master " + skill.displayName() + "</gold>", List.of(
            "<gray>You may select only two seasonal masteries.</gray>",
            "<gray>Removing one later costs Marks and starts a cooldown.</gray>",
            "<yellow>Click again to confirm.</yellow>"
        ), target -> {
            target.closeInventory();
            target.performCommand("mastery select " + skill.name().toLowerCase(java.util.Locale.ROOT));
            reopenLater(target, View.SKILLS);
        });
        open(player, menu.inventory());
    }

    private void openForge(Player player) {
        var account = account(player);
        if (account == null) return;
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            player.sendRichMessage("<yellow>Switch to Survival to work on gear.</yellow>"); return;
        }
        MenuInventory menu = detailMenu(View.FORGE, ForgeSocketLayout.SIZE, "<light_purple><bold>Forge</bold></light_purple>");
        menu.holder().forgeOwner = player.getUniqueId();
        menu.holder().forgeAccount = account;
        menu.inventory().setItem(ForgeSlotPolicy.PRIMARY_SLOT, null);
        menu.inventory().setItem(ForgeSlotPolicy.CATALYST_SLOT, null);
        renderForge(menu.holder(), player);
        open(player, menu.inventory());
    }

    private void openQuests(Player player) {
        var journal = plugin.engagement().dailyJournal(player);
        if (journal.isEmpty()) {
            Chat.accountLoading(player);
            return;
        }
        var daily = journal.orElseThrow();
        MenuInventory menu = detailMenu(View.QUESTS, 45, "<aqua><bold>Daily Contracts</bold></aqua>");
        button(menu, 4, Material.CLOCK, "<aqua><bold>Daily Contracts</bold></aqua>", List.of(
            "<gray>Completed</gray> <white>" + daily.completed() + "/" + daily.quests().size() + "</white>",
            "<gray>Reset</gray> <white>00:00 UTC</white>",
            "<dark_gray>Rewards are paid as soon as a contract is finished.</dark_gray>"
        ), ignored -> { });
        for (int index = 0; index < Math.min(QUEST_SLOTS.length, daily.quests().size()); index++) {
            var quest = daily.quests().get(index);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + escape(quest.description()) + "</gray>");
            lore.add("");
            lore.add(VisualFormat.progressBar(quest.progress(), quest.target(), 10)
                + " <white>" + quest.progress() + "/" + quest.target() + "</white>");
            lore.add("<gray>Reward</gray> " + Chat.marksTag(quest.markReward()));
            if(quest.forestryExperience()>0) lore.add("<gray>Bonus</gray> <aqua>"+quest.forestryExperience()+" Forestry XP</aqua>");
            lore.add(quest.complete()
                ? "<green>✓ Complete — reward paid</green>"
                : "<yellow>In progress</yellow>");
            button(menu, QUEST_SLOTS[index], questMaterial(quest.action()),
                (quest.complete() ? "<green>" : "<gold>") + escape(quest.title())
                    + (quest.complete() ? "</green>" : "</gold>"), lore, ignored -> { });
        }
        open(player, menu.inventory());
    }

    private void renderForge(MenuHolder holder, Player player) {
        Inventory inventory = holder.inventory;
        MenuInventory menu = new MenuInventory(inventory, holder);
        ItemStack gear = inventory.getItem(ForgeSlotPolicy.PRIMARY_SLOT);
        ItemStack catalyst = inventory.getItem(ForgeSlotPolicy.CATALYST_SLOT);
        if (!java.util.Objects.equals(holder.selectedGear, gear)) {
            holder.selectedGear = gear == null ? null : gear.clone();
            holder.socketIndex = -1; holder.socketPage = 0; clearForgeReview(holder);
        }
        List<PaperItemService.SocketedGem> sockets = plugin.items().sockets(gear);
        holder.socketIndex = ForgeSocketLayout.selected(holder.socketIndex, sockets.size());
        ForgePreview candidate = forgePreview(player, gear, catalyst, holder.socketIndex);
        boolean suitable = candidate.operation() == ForgeOperation.NONE || switch (holder.forgeTab) {
            case AUTO -> true;
            case SOCKETS -> candidate.operation() == ForgeOperation.MERGE || candidate.operation() == ForgeOperation.UNSOCKET
                || candidate.operation() == ForgeOperation.APPLY && plugin.forge().gem(catalyst).isPresent();
            case IMPROVE -> candidate.operation() == ForgeOperation.QUALITY;
            case TOMES -> candidate.operation() == ForgeOperation.APPLY && plugin.forge().gem(catalyst).isEmpty();
            case REPAIR -> false;
        };
        ForgePreview preview = suitable ? candidate : new ForgePreview(ForgeOperation.NONE, List.of(
            "<gray>Selected method: " + holder.forgeTab.name().toLowerCase(java.util.Locale.ROOT) + ".</gray>",
            "<gray>Choose Auto to detect the material in your inputs.</gray>"));
        boolean reviewed = preview.ready() && holder.forgeReview != null && holder.forgeReview.matches(
            gear, catalyst, holder.socketIndex, preview.operation().name(), forgeNow());
        if (!reviewed) holder.forgeReview = null;

        button(menu, 4, Material.SMITHING_TABLE, "<light_purple>Forge</light_purple>", List.of(
            "<gray>Equipment on the left. Materials on the right.</gray>",
            "<gray>Review the center result, then confirm.</gray>"), ignored -> { });

        button(menu, 10, Material.ITEM_FRAME, "<aqua>Primary Input</aqua>", List.of(
            "<gray>Place one weapon, tool or armor piece here.</gray>",
            "<gray>Gear also accepts gems, tomes, or Arcane Dust.</gray>",
            "<gray>Place the first gem here when merging.</gray>"
        ), ignored -> { });
        button(menu, 14, Material.GLOW_ITEM_FRAME, "<light_purple>Catalyst Input</light_purple>", List.of(
            "<gray>Use materials, gems, tomes, or Arcane Dust.</gray>",
            "<gray>Place the second matching gem here when merging.</gray>"
        ), ignored -> { });
        button(menu, 13, Material.YELLOW_STAINED_GLASS_PANE, "<gold>＋</gold>", List.of(
            "<gray>Review the result before confirming.</gray>"
        ), ignored -> { });
        button(menu, 20, Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>╲</light_purple>", List.of(), ignored -> { });
        button(menu, 24, Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>╱</light_purple>", List.of(), ignored -> { });

        List<String> actionLore = new ArrayList<>(preview.lore());
        if (preview.ready()) actionLore.add(reviewed ? "<green>Click again to confirm.</green>" : "<yellow>Click to review this change.</yellow>");
        button(menu, 22, reviewed ? Material.LIME_DYE : forgeActionMaterial(preview.operation()),
            reviewed ? "<green>Confirm Change</green>" : forgeActionName(preview.operation()), actionLore, target -> {
                if (preview.ready()) {
                    performForge(holder, target);
                }
            });
        renderSocketRow(menu, player, gear, sockets);
        renderForgeRepairs(menu, player, gear);
        ForgeTab[] tabs = {ForgeTab.SOCKETS, ForgeTab.IMPROVE, ForgeTab.TOMES, ForgeTab.REPAIR};
        Material[] tabIcons = {Material.AMETHYST_SHARD, Material.SMITHING_TABLE, Material.ENCHANTED_BOOK, Material.ANVIL};
        for (int i = 0; i < tabs.length; i++) {
            ForgeTab tab = tabs[i];
            button(menu, 1 + i * 2, tabIcons[i], (holder.forgeTab == tab ? "<green>" : "<gray>") + VisualFormat.titleCase(tab.name()),
                List.of("<gray>Focus this method. Your inputs stay in place.</gray>"), target -> { holder.forgeTab = tab; clearForgeReview(holder); renderForge(holder, target); });
        }
        if (holder.forgeTab != ForgeTab.AUTO && holder.forgeTab != ForgeTab.SOCKETS) for (int slot = 28; slot <= 34; slot++) clearStationButton(holder, slot);
        if (holder.forgeTab != ForgeTab.AUTO && holder.forgeTab != ForgeTab.REPAIR) { clearStationButton(holder, 37); clearStationButton(holder, 39); }
        if (holder.forgeTab == ForgeTab.IMPROVE) {
            var next = plugin.forge().qualityRequirement(gear);
            button(menu, 31, Material.SMITHING_TABLE, "<gold>Next Improvement</gold>", next.map(needed -> List.of(
                "<white>" + needed.quality().displayName() + "</white>",
                "<gray>Bring " + needed.amount() + " " + VisualFormat.titleCase(needed.material().name()) + ".</gray>",
                "<gray>Smithing " + needed.smithingLevel() + " required.</gray>",
                "<gray>Put the materials in the right input.</gray>"
            )).orElse(List.of("<gray>Place identified equipment in the left input.</gray>", "<gray>Peerless equipment cannot be improved further.</gray>")), ignored -> { });
        }
        if (holder.forgeTab == ForgeTab.REPAIR) {
            inventory.setItem(22, inventory.getItem(37)); holder.actions.put(22, holder.actions.get(37)); clearStationButton(holder, 37);
            inventory.setItem(24, inventory.getItem(39)); holder.actions.put(24, holder.actions.get(39)); clearStationButton(holder, 39);
        }
        backButton(menu, StationLayout.BACK, View.FORGE);
        navigate(menu, 49, Material.ENCHANTING_TABLE, "<light_purple>Arcane Enchanting</light_purple>", List.of(
            "<gray>Roll enchantments with bookshelves and Arcane Dust.</gray>"
        ), View.ENCHANTMENTS);
        button(menu, 51, Material.AMETHYST_CLUSTER, "<aqua>Equipment Upgrades</aqua>", List.of(
            "<gray>Improve identified equipment with materials.</gray>",
            "<gray>Two matching gems merge into the next tier.</gray>",
            "<gray>Recover any selected gem with Arcane Dust.</gray>",
            "<gray>The gem keeps its type and tier.</gray>"
        ), ignored -> { });
        button(menu, 40, Material.GLOWSTONE_DUST, "<light_purple>Arcane Dust</light_purple>", List.of(
            "<gray>Craft 1 lapis lazuli + 1 amethyst shard</gray>",
            "<gray>for 4 Arcane Dust.</gray>",
            "<gray>Recovery: I 8 | II 12 | III 16 | IV 20 Dust</gray>",
            "<gray>Keep room in your inventory for the gem.</gray>"), ignored -> { });
        button(menu, 43, Material.GRAY_DYE, "<gray>Cancel Review</gray>", List.of(
            "<gray>Your items stay in the inputs.</gray>"), target -> { clearForgeReview(holder); renderForge(holder, target); });
        button(menu, 47, Material.COMPASS, "<aqua>Auto Detect</aqua>", List.of("<gray>Show every method and use the current catalyst.</gray>"),
            target -> { holder.forgeTab = ForgeTab.AUTO; clearForgeReview(holder); renderForge(holder, target); });
    }
    private void clearStationButton(MenuHolder holder, int slot) {
        holder.inventory.setItem(slot, icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of())); holder.actions.remove(slot);
    }

    private void renderForgeRepairs(MenuInventory menu, Player player, ItemStack gear) {
        MenuHolder holder = menu.holder();
        RepairService.ItemQuote quote = plugin.repairs().quoteItem(gear);
        boolean reviewed = holder.forgeRepairQuote != null && holder.forgeRepairQuote.matches(gear)
            && holder.forgeRepairQuote.cost() == quote.cost() && forgeNow() < holder.forgeRepairExpires;
        if (!reviewed) holder.forgeRepairQuote = null;
        List<String> lore = new ArrayList<>();
        if (holder.forgeRepairPending) {
            lore.add("<gray>Finishing your repair. Keep this window open.</gray>");
            lore.add("<gray>Closing before it finishes cancels the repair.</gray>");
        } else if (quote.ready()) {
            lore.add("<gray>Durability restored</gray> <white>" + quote.missingDurability() + "</white>");
            lore.add("<gray>Cost</gray> " + Chat.marksTag(quote.cost()));
            lore.add("");
            lore.add("<gray>Repairs the equipment in the left input.</gray>");
            lore.add("<gray>Gems, enchantments and improvements stay intact.</gray>");
            lore.add(reviewed ? "<green>Left-click again to pay and repair.</green>"
                : "<yellow>Left-click to review the repair.</yellow>");
        } else {
            lore.add(switch (quote.state()) {
                case NO_ITEM -> "<gray>Place damaged equipment in the left input.</gray>";
                case NOT_REPAIRABLE -> "<gray>That item cannot be repaired.</gray>";
                case STACKED -> "<gray>Repair one piece of equipment at a time.</gray>";
                case UNDAMAGED -> "<green>That item is already in good condition.</green>";
                case READY -> throw new IllegalStateException("Ready repair handled above");
            });
        }
        button(menu, 37, holder.forgeRepairPending ? Material.CLOCK : reviewed ? Material.LIME_DYE : Material.ANVIL,
            holder.forgeRepairPending ? "<gray>Repairing...</gray>" : reviewed ? "<green>Confirm Item Repair</green>"
                : "<gold>Repair Item</gold>", lore, target -> performForgeRepair(holder, target));
        button(menu, 39, Material.DAMAGED_ANVIL, "<aqua>Repair All Equipment</aqua>", List.of(
            "<gray>Checks your inventory, hotbar, armor and off-hand.</gray>",
            "<gray>Forge inputs return to you before the quote opens.</gray>",
            "<gray>Nothing is spent until you confirm that quote.</gray>",
            "<yellow>Left-click to review all repairs.</yellow>"), target -> {
                if (holder.forgeRepairPending) return;
                if (!canReturnForgeInputs(target, holder.inventory)) {
                    target.sendRichMessage("<yellow>Make room for both forge inputs before opening Repair All.</yellow>");
                    return;
                }
                target.closeInventory();
                // onClose returns both inputs first; the all-inventory quote now includes them.
                plugin.repairs().open(target);
            });
    }

    private void performForgeRepair(MenuHolder holder, Player player) {
        if (holder.forgeRepairPending || !forgeSession(holder, player)
            || player.getOpenInventory().getTopInventory() != holder.inventory) return;
        ItemStack gear = holder.inventory.getItem(ForgeSlotPolicy.PRIMARY_SLOT);
        RepairService.ItemQuote quote = plugin.repairs().quoteItem(gear);
        if (!quote.ready()) {
            renderForge(holder, player);
            return;
        }
        if (holder.forgeRepairQuote == null || !holder.forgeRepairQuote.matches(gear)
            || holder.forgeRepairQuote.cost() != quote.cost() || forgeNow() >= holder.forgeRepairExpires) {
            clearForgeReview(holder);
            holder.forgeRepairQuote = quote;
            holder.forgeRepairExpires = forgeNow() + 15_000;
            renderForge(holder, player);
            return;
        }
        RepairService.ItemQuote offered = holder.forgeRepairQuote;
        clearForgeReview(holder);
        holder.forgeRepairPending = true;
        long generation = holder.forgeGeneration;
        renderForge(holder, player);
        plugin.repairs().repairInput(player, holder.inventory, ForgeSlotPolicy.PRIMARY_SLOT, offered,
            () -> holder.forgeRepairPending && holder.forgeGeneration == generation && forgeSession(holder, player)
                && player.getOpenInventory().getTopInventory() == holder.inventory,
            result -> {
                holder.forgeRepairPending = false;
                clearForgeReview(holder);
                plugin.repairs().tellResult(player, result);
                if (forgeSession(holder, player) && player.getOpenInventory().getTopInventory() == holder.inventory) {
                    renderForge(holder, player);
                }
            });
    }

    private void renderSocketRow(MenuInventory menu, Player player, ItemStack gear, List<PaperItemService.SocketedGem> sockets) {
        MenuHolder holder = menu.holder();
        int capacity = plugin.items().isAuthentic(gear) && !plugin.items().isUnidentified(gear)
            && plugin.items().hasValidSockets(gear) ? plugin.items().socketCapacity(gear) : 0;
        holder.socketPage = ForgeSocketLayout.page(holder.socketPage, capacity);
        for (int cell = 0; cell < ForgeSocketLayout.PAGE_SIZE; cell++) {
            int slot = ForgeSocketLayout.slot(cell), index = holder.socketPage * ForgeSocketLayout.PAGE_SIZE + cell;
            holder.actions.remove(slot);
            if (index >= capacity) {
                menu.inventory().setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of()));
            } else if (index >= sockets.size()) {
                button(menu, slot, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Socket " + (index + 1) + " - Empty</gray>",
                    List.of("<gray>A gem in the catalyst input fills the next empty socket.</gray>"), ignored -> { });
            } else {
                var gem = sockets.get(index);
                List<String> lore = new ArrayList<>();
                for (String effect : gem.type().effect(gem.tier()).split(", ")) lore.add("<white>" + effect + "</white>");
                lore.add("<dark_gray>----------------------------</dark_gray>");
                lore.add("<gray>Recovery</gray> <light_purple>" + plugin.forge().unsocketDustCost(gear, index) + " Arcane Dust</light_purple>");
                lore.add(index == holder.socketIndex ? "<aqua>Selected for recovery.</aqua>" : "<yellow>Click to select this gem.</yellow>");
                button(menu, slot, gem.type().material(), gem.type().color() + "Socket " + (index + 1) + " - "
                    + gem.type().displayName() + " " + roman(gem.tier()), lore, target -> {
                        holder.socketIndex = index; clearForgeReview(holder); renderForge(holder, target);
                    });
            }
        }
        for (int slot : new int[]{27,35}) { holder.actions.remove(slot); menu.inventory().setItem(slot, icon(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of())); }
        if (holder.socketPage > 0) button(menu, 27, Material.ARROW, "<aqua>Previous Sockets</aqua>", List.of(), target -> {
            holder.socketPage--; clearForgeReview(holder); renderForge(holder, target);
        });
        if ((holder.socketPage + 1) * ForgeSocketLayout.PAGE_SIZE < capacity) button(menu, 35, Material.ARROW, "<aqua>More Sockets</aqua>", List.of(), target -> {
            holder.socketPage++; clearForgeReview(holder); renderForge(holder, target);
        });
    }

    private void performForge(MenuHolder holder, Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() != holder || !forgeSession(holder, player)) {
            return;
        }
        ItemStack gear = holder.inventory.getItem(ForgeSlotPolicy.PRIMARY_SLOT);
        ItemStack catalyst = holder.inventory.getItem(ForgeSlotPolicy.CATALYST_SLOT);
        if (plugin.items().hasValidSockets(gear)) plugin.items().refresh(gear);
        ForgePreview preview = forgePreview(player, gear, catalyst, holder.socketIndex);
        if (!preview.ready()) {
            renderForge(holder, player);
            return;
        }
        if (holder.forgeReview == null || !holder.forgeReview.matches(gear, catalyst, holder.socketIndex, preview.operation().name(), forgeNow())) {
            holder.forgeReview = new ForgeReview<>(gear.clone(), catalyst.clone(), holder.socketIndex, preview.operation().name(), forgeNow());
            holder.forgeGeneration++;
            renderForge(holder, player); return;
        }
        holder.forgeReview = null;
        holder.forgeGeneration++;
        switch (preview.operation()) {
            case MERGE -> {
                var result = plugin.forge().mergeGems(player, holder.inventory, ForgeSlotPolicy.PRIMARY_SLOT,
                    ForgeSlotPolicy.CATALYST_SLOT, gear.clone(), catalyst.clone());
                player.sendRichMessage(switch (result) {
                    case MERGED -> "<green>Gems merged. The result was moved to your inventory.</green>";
                    case NEED_TWO_GEMS -> "<red>Place two gems in the forge.</red>";
                    case DIFFERENT_TYPE -> "<red>Those gems are different types.</red>";
                    case DIFFERENT_TIER -> "<red>Those gems are different tiers.</red>";
                    case MAXIMUM_TIER -> "<red>Tier IV gems cannot be merged.</red>";
                    case INVENTORY_FULL -> "<red>Make room for the gem, then try again. Nothing was spent.</red>";
                    case INVALID_SESSION -> "<red>Reopen the forge after your profile has loaded.</red>";
                    case INPUT_CHANGED -> "<yellow>The gems changed. Review them again.</yellow>";
                });
            }
            case APPLY -> {
                var result = plugin.forge().applyUpgrade(player, gear, catalyst);
                player.sendRichMessage(switch (result) {
                    case APPLIED -> "<green>Upgrade applied.</green>";
                    case NO_CATALYST -> "<red>Place a gem or tome in the catalyst slot.</red>";
                    case NOT_GEAR -> "<red>Place gear in the primary slot.</red>";
                    case NO_CAPACITY -> "<red>That item has no empty sockets.</red>";
                    case INCOMPATIBLE -> "<red>That enchantment does not work on this item.</red>";
                    case UNIDENTIFIED -> "<red>Identify that item before upgrading it.</red>";
                    case CORRUPTED -> "<red>Cleanse this item's corruption before enchanting it.</red>";
                    case ALREADY_PRESENT -> "<red>The item already has this enchantment at the same or a higher level.</red>";
                    case INVALID_SOCKET_DATA -> "<red>This item's socket data needs a staff review.</red>";
                });
            }
            case UNSOCKET -> {
                var selected = plugin.items().sockets(gear).get(holder.socketIndex);
                var result = plugin.forge().unsocket(player, holder.inventory, ForgeSlotPolicy.PRIMARY_SLOT,
                    ForgeSlotPolicy.CATALYST_SLOT, holder.socketIndex, selected);
                player.sendRichMessage(switch (result) {
                    case REMOVED -> "<aqua>Gem recovered and moved to your inventory.</aqua>";
                    case NOT_GEAR -> "<red>Place gear in the primary slot.</red>";
                    case UNIDENTIFIED -> "<red>Identify that item before working on its sockets.</red>";
                    case NO_GEMS -> "<red>That item has no socketed gems.</red>";
                    case NEED_DUST -> "<red>Place Arcane Dust in the catalyst slot.</red>";
                    case NOT_ENOUGH_DUST -> "<red>You need more Arcane Dust.</red>";
                    case INVENTORY_FULL -> "<red>Make room for the gem, then try again. Nothing was spent.</red>";
                    case SOCKET_CHANGED -> "<yellow>The selected gem changed. Review the item again.</yellow>";
                    case INVALID_SOCKET_DATA -> "<red>This item's socket data needs a staff review.</red>";
                    case INVALID_SESSION -> "<red>Reopen the forge after your profile has loaded.</red>";
                });
            }
            case QUALITY -> {
                var result = plugin.forge().improveQuality(player, gear, catalyst);
                player.sendRichMessage(switch (result) {
                    case APPLIED -> "<green>Item improved to " + plugin.items().quality(gear).displayName() + ".</green>";
                    case NOT_GEAR -> "<red>Place gear in the primary slot.</red>";
                    case NOT_CRAFTED -> "<red>Identify earned equipment first. Starter gear cannot be improved.</red>";
                    case UNIDENTIFIED -> "<red>Identify that item before improving it.</red>";
                    case MAXIMUM_QUALITY -> "<gold>That item is already Peerless.</gold>";
                    case SKILL_TOO_LOW -> "<red>Your Smithing level is too low.</red>";
                    case WRONG_MATERIAL -> "<red>That improvement needs a different material.</red>";
                    case NOT_ENOUGH_MATERIAL -> "<red>You need more material for that improvement.</red>";
                });
            }
            case NONE -> { }
        }
        normalizeForgeSlot(holder.inventory, ForgeSlotPolicy.PRIMARY_SLOT);
        normalizeForgeSlot(holder.inventory, ForgeSlotPolicy.CATALYST_SLOT);
        renderForge(holder, player);
    }

    private void openEnchantments(Player player) {
        MenuInventory menu = detailMenu(View.ENCHANTMENTS, 54,
            "<light_purple><bold>Arcane Enchanting</bold></light_purple>");
        var view = plugin.enchanting().view(player);
        int smithingLevel = plugin.arcane() != null ? plugin.arcane().arcaneLevel(player)
            : plugin.presentation().progression(player).skills()
                .getOrDefault(Skill.SMITHING, new ProgressionService.SkillProgress(0L, 0)).level();
        int dust = plugin.enchanting().countArcaneDust(player);
        button(menu, 4, Material.ENCHANTING_TABLE, "<light_purple><bold>Arcane Table</bold></light_purple>", List.of(
            "<gray>Bookshelf power</gray> <white>" + view.bookshelfPower() + "/15</white>",
            "<gray>Enchanting</gray> <white>" + smithingLevel + "/50</white> <dark_gray>(or Smithing, if higher)</dark_gray>",
            "<gray>Arcane Dust</gray> <light_purple>" + dust + "</light_purple>",
            "<dark_gray>Higher values improve the three offers.</dark_gray>"
        ), ignored -> { });

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            button(menu, 13, Material.BARRIER, "<red>No Item Held</red>", List.of(
                "<gray>Hold the equipment you want to enchant.</gray>"
            ), ignored -> { });
        } else {
            rawButton(menu, 13, held.clone(), ignored -> { });
        }

        if (view.state() != net.nerdsmp.paper.forge.ArcaneEnchantingService.ViewState.READY) {
            List<String> problem = switch (view.state()) {
                case NO_STATION -> List.of(
                    "<gray>Stand beside an enchanting table, then reopen this menu.</gray>",
                    "<dark_gray>Right-clicking the table opens it directly.</dark_gray>"
                );
                case NOT_GEAR -> List.of("<gray>Hold a crafted, starter, or relic item in your main hand.</gray>");
                case UNIDENTIFIED -> List.of("<gray>Take this item to an Identifier before enchanting it.</gray>");
                case CORRUPTED -> List.of("<gray>Cleanse this item's corruption before enchanting it.</gray>");
                case MAXED -> List.of("<gray>No compatible enchantment can be raised any further.</gray>");
                case READY -> List.of();
            };
            button(menu, 31, Material.RED_STAINED_GLASS_PANE, "<red>Offers Unavailable</red>", problem,
                ignored -> { });
        } else {
            int[] slots = {29, 31, 33};
            for (int index = 0; index < view.offers().size(); index++) {
                var offer = view.offers().get(index);
                if (!offer.available()) {
                    button(menu, slots[index], Material.GRAY_DYE, "<gray>No Upgrade</gray>", List.of(
                        "<dark_gray>This tier cannot improve the held item.</dark_gray>"
                    ), ignored -> { });
                    continue;
                }
                boolean affordable = dust >= offer.dustCost();
                String color = affordable ? "<light_purple>" : "<red>";
                button(menu, slots[index], Material.ENCHANTED_BOOK,
                    color + offer.enchantment().displayName() + " " + roman(offer.level()), List.of(
                        "<gray>" + offer.enchantment().description() + "</gray>",
                        "",
                        "<gray>Consumes</gray> <light_purple>" + offer.dustCost() + " Arcane Dust</light_purple>",
                        "<dark_gray>No experience levels are spent.</dark_gray>",
                        affordable ? "<yellow>Click to enchant.</yellow>" : "<red>You cannot afford this offer.</red>"
                    ), target -> {
                        reviewEnchantment(target, offer);
                    });
            }
        }
        button(menu, 40, Material.GLOWSTONE_DUST, "<light_purple>Arcane Dust</light_purple>", List.of(
            "<gray>Craft:</gray> <blue>1 Lapis Lazuli</blue> <dark_gray>+</dark_gray>"
                + " <light_purple>1 Amethyst Shard</light_purple>",
            "<gray>Yield:</gray> <white>4 Arcane Dust</white>",
            "",
            "<gray>Offers apply directly to the item in your main hand.</gray>",
            "<gray>Bookshelves raise offer strength; Enchanting unlocks top ranks.</gray>",
            "<gray>Enchanting never consumes experience levels.</gray>",
            "<gray>Vanilla enchanted books and anvil enchanting are disabled.</gray>",
            "<dark_gray>Enchantment tomes found in the world are applied at /forge.</dark_gray>"
        ), ignored -> { });
        arcaneTiles(menu, player);
        open(player, menu.inventory());
    }

    /** Arcane Forge 2.0 entry points. Each appears only when its feature is switched on. */
    private void arcaneTiles(MenuInventory menu, Player player) {
        var arcane = plugin.arcane();
        if (arcane == null || !arcane.settings().enabled()) return;
        var settings = arcane.settings();
        if (settings.codex()) {
            button(menu, 37, Material.ENCHANTED_BOOK, "<gradient:#C59AFA:#8CE8ED>Arcane Codex</gradient>", MenuKit.withHint(List.of(
                "<gray>Everything you have discovered, clues and help.</gray>"), "open"),
                target -> plugin.arcaneMenus().openCodex(target, true));
        }
        if (settings.research().enabled()) {
            button(menu, 39, Material.BREWING_STAND, "<aqua>Arcane Research</aqua>", MenuKit.withHint(List.of(
                "<gray>Combine gems to discover enchantments.</gray>"), "open"),
                target -> plugin.arcaneMenus().openResearch(target, true, List.of(), false));
        }
        if (settings.imbuement().enabled()) {
            button(menu, 41, Material.ANVIL, "<light_purple>Chosen Imbuement</light_purple>", MenuKit.withHint(List.of(
                "<gray>Pick an exact discovered enchantment and level.</gray>",
                "<gray>Fixed price, no randomness.</gray>"), "open"),
                target -> plugin.arcaneMenus().openImbue(target, true));
        }
    }

    private void reviewEnchantment(Player player, net.nerdsmp.paper.forge.ArcaneEnchantingService.Offer offer) {
        ItemStack original = player.getInventory().getItemInMainHand().clone(); int slot = player.getInventory().getHeldItemSlot();
        ItemStack result = original.clone();
        if (plugin.items().enchant(result, offer.enchantment(), offer.level()) != PaperItemService.UpgradeResult.APPLIED) { openEnchantments(player); return; }
        MenuInventory menu = detailMenu(View.ENCHANTMENTS, 54, "<light_purple>Review Enchantment</light_purple>");
        rawButton(menu, 11, original.clone(), ignored -> { }); rawButton(menu, 15, result, ignored -> { });
        button(menu, 4, Material.ENCHANTING_TABLE, "<light_purple>Before & After</light_purple>", List.of(
            "<gray>Left: your equipment. Right: the finished item.</gray>", "<gray>Cost: " + offer.dustCost() + " Arcane Dust.</gray>", "<gray>No experience levels are spent.</gray>"), ignored -> { });
        button(menu, 20, Material.ARROW, "<yellow>← Back to Offers</yellow>", List.of(), this::openEnchantments);
        button(menu, 24, Material.LIME_DYE, "<green>Confirm Enchantment</green>", List.of("<gray>Checks the item, offer, station and Dust again.</gray>"), target -> {
            var applied = plugin.enchanting().applyReviewed(target, offer, original, slot);
            target.sendRichMessage(enchantingResult(applied)); openEnchantments(target);
        });
        open(player, menu.inventory());
    }

    private String enchantingResult(net.nerdsmp.paper.forge.ArcaneEnchantingService.ApplyResult result) {
        return switch (result.state()) {
            case APPLIED -> "<light_purple>" + result.offer().enchantment().displayName() + " "
                + roman(result.offer().level()) + " bound to the item.</light_purple>";
            case NO_STATION -> "<red>Stand beside an enchanting table.</red>";
            case NOT_GEAR -> "<red>Hold a crafted, starter, or relic item.</red>";
            case UNIDENTIFIED -> "<red>Identify the item first.</red>";
            case CORRUPTED -> "<red>Cleanse this item's corruption before enchanting it.</red>";
            case NO_UPGRADE -> "<red>That offer cannot improve the item.</red>";
            case NOT_ENOUGH_DUST -> "<red>You do not have enough Arcane Dust.</red>";
            case OFFER_CHANGED -> "<red>The offers changed. Review them and try again.</red>";
        };
    }

    private ForgePreview forgePreview(Player player, ItemStack gear, ItemStack catalyst, int socketIndex) {
        Optional<net.nerdsmp.paper.forge.ArcaneForgeService.Gem> heldGem = plugin.forge().gem(gear);
        if (heldGem.isPresent()) {
            var merge = plugin.forge().mergePreview(gear, catalyst);
            List<String> lore = switch (merge.result()) {
                case MERGED -> List.of(
                    heldGem.get().type().color() + heldGem.get().type().displayName() + " "
                        + roman(heldGem.get().tier()) + " + " + roman(heldGem.get().tier()),
                    "<gray>Result</gray> " + heldGem.get().type().color() + heldGem.get().type().displayName()
                        + " " + roman(merge.outputTier()),
                    "<white>" + heldGem.get().type().effect(merge.outputTier()) + "</white>",
                    "",
                    "<gray>One gem from each input is consumed.</gray>"
                );
                case NEED_TWO_GEMS -> List.of("<gray>Place the second gem in the catalyst slot.</gray>");
                case DIFFERENT_TYPE -> List.of("<gray>Gem types must match.</gray>");
                case DIFFERENT_TIER -> List.of("<gray>Gem tiers must match.</gray>");
                case MAXIMUM_TIER -> List.of("<gold>This gem is already Tier IV.</gold>");
                case INVENTORY_FULL -> List.of("<red>Make room in your inventory for the merged gem.</red>");
                case INVALID_SESSION -> List.of("<red>Reopen the forge after your profile has loaded.</red>");
                case INPUT_CHANGED -> List.of("<yellow>The gems changed. Review them again.</yellow>");
            };
            return new ForgePreview(merge.ready() ? ForgeOperation.MERGE : ForgeOperation.NONE, lore);
        }
        if (!plugin.items().isAuthentic(gear)) {
            return new ForgePreview(ForgeOperation.NONE, List.of("<gray>Place gear or a gem in the primary slot.</gray>"));
        }
        if (gear.getAmount() != 1) return new ForgePreview(ForgeOperation.NONE, List.of("<red>Place one piece of gear at a time.</red>"));
        if (!plugin.items().hasValidSockets(gear)) return new ForgePreview(ForgeOperation.NONE,
            List.of("<red>This item's socket data needs a staff review.</red>"));
        if (plugin.items().isUnidentified(gear)) {
            return new ForgePreview(ForgeOperation.NONE, List.of("<gray>Identify this item before upgrading it.</gray>"));
        }
        if (plugin.enchanting().isArcaneDust(catalyst)) {
            List<PaperItemService.SocketedGem> sockets = plugin.items().sockets(gear);
            if (sockets.isEmpty()) {
                return new ForgePreview(ForgeOperation.NONE, List.of("<gray>That item has no gems to recover.</gray>"));
            }
            if (socketIndex < 0 || socketIndex >= sockets.size()) return new ForgePreview(ForgeOperation.NONE,
                List.of("<gray>Select a filled socket below.</gray>"));
            PaperItemService.SocketedGem selected = sockets.get(socketIndex);
            int cost = plugin.forge().unsocketDustCost(gear, socketIndex);
            boolean enough = catalyst.getAmount() >= cost;
            return new ForgePreview(enough ? ForgeOperation.UNSOCKET : ForgeOperation.NONE, List.of(
                "<aqua>Recover Socket " + (socketIndex + 1) + "</aqua>",
                selected.type().color() + selected.type().displayName() + " " + roman(selected.tier()),
                "<gray>Cost</gray> <light_purple>" + cost + " Arcane Dust</light_purple>",
                "<gray>The gem returns at its current tier.</gray>",
                "<gray>One socket is freed; your other gems remain.</gray>",
                enough ? "<gray>Dust in input: " + catalyst.getAmount() + " / " + cost + "</gray>"
                    : "<red>Add " + (cost - catalyst.getAmount()) + " more Arcane Dust.</red>"
            ));
        }
        Optional<net.nerdsmp.paper.forge.ArcaneForgeService.Gem> gem = plugin.forge().gem(catalyst);
        if (gem.isPresent()) {
            int used = plugin.items().sockets(gear).size();
            int capacity = plugin.items().socketCapacity(gear);
            if (used >= capacity) {
                return new ForgePreview(ForgeOperation.NONE, List.of("<gray>This item has no empty sockets.</gray>"));
            }
            List<String> effects = new ArrayList<>();
            effects.add(gem.get().type().color() + gem.get().type().displayName() + " " + roman(gem.get().tier()));
            for (String effect : gem.get().type().effect(gem.get().tier()).split(", ")) effects.add("<white>" + effect + "</white>");
            effects.add("<gray>Next empty socket:</gray> <white>" + (used + 1) + " / " + capacity + "</white>");
            effects.add("<gray>Cost: 1 gem. No Dust or levels.</gray>");
            effects.add("<gray>Recover it later with Arcane Dust.</gray>");
            return new ForgePreview(ForgeOperation.APPLY, effects);
        }
        Optional<net.nerdsmp.paper.forge.ArcaneForgeService.Tome> tome = plugin.forge().tome(catalyst);
        if (tome.isPresent()) {
            // The same rule PaperItemService.enchant applies, so relic gear with a matching stat previews correctly.
            if (!plugin.items().supportsEnchantment(gear, tome.get().enchantment())) {
                return new ForgePreview(ForgeOperation.NONE, List.of("<gray>That enchantment does not fit this item.</gray>"));
            }
            int current = plugin.items().enchantmentLevel(gear, tome.get().enchantment());
            if (current >= tome.get().level()) {
                return new ForgePreview(ForgeOperation.NONE, List.of("<gray>This item already has that level or better.</gray>"));
            }
            return new ForgePreview(ForgeOperation.APPLY, List.of(
                "<light_purple>" + tome.get().enchantment().displayName() + " " + roman(tome.get().level()) + "</light_purple>",
                "<gray>" + tome.get().enchantment().description() + "</gray>",
                "",
                "<gray>Cost: 1 tome.</gray>"
            ));
        }
        var requirement = plugin.forge().qualityRequirement(gear);
        if (plugin.forge().isQualityMaterial(catalyst)) {
            if (requirement.isEmpty()) {
                return new ForgePreview(ForgeOperation.NONE, List.of(plugin.items().isQualityUpgradable(gear)
                    ? "<gold>That item is already Peerless.</gold>"
                    : "<gray>Identified earned equipment can gain workmanship quality.</gray>"));
            }
            var needed = requirement.orElseThrow();
            int smithingLevel = plugin.presentation().progression(player).skills()
                .getOrDefault(Skill.SMITHING, new ProgressionService.SkillProgress(0L, 0)).level();
            boolean correctMaterial = catalyst.getType() == needed.material();
            boolean enoughMaterial = correctMaterial && catalyst.getAmount() >= needed.amount();
            boolean enoughSkill = smithingLevel >= needed.smithingLevel();
            return new ForgePreview(correctMaterial && enoughMaterial && enoughSkill
                ? ForgeOperation.QUALITY : ForgeOperation.NONE, List.of(
                needed.quality().color() + "<bold>" + needed.quality().displayName() + "</bold>",
                "<gray>Base stat bonus</gray> <white>+"
                    + String.format(java.util.Locale.ROOT, "%.0f", needed.quality().bonusPercent()) + "%</white>",
                "<gray>Materials</gray> " + (enoughMaterial ? "<green>" : "<red>") + needed.amount() + " "
                    + VisualFormat.titleCase(needed.material().name()) + "</" + (enoughMaterial ? "green" : "red") + ">",
                "<gray>Smithing</gray> " + (enoughSkill ? "<green>" : "<red>") + smithingLevel + "/"
                    + needed.smithingLevel() + "</" + (enoughSkill ? "green" : "red") + ">",
                correctMaterial && enoughMaterial && enoughSkill
                    ? "<yellow>Click to improve the item.</yellow>"
                    : !correctMaterial ? "<red>This tier needs " + VisualFormat.titleCase(needed.material().name()) + ".</red>"
                    : "<red>Requirements not met.</red>"
            ));
        }
        if (requirement.isPresent()) {
            var needed = requirement.orElseThrow();
            return new ForgePreview(ForgeOperation.NONE, List.of(
                "<gray>Current</gray> " + plugin.items().quality(gear).color() + plugin.items().quality(gear).displayName(),
                "<gray>Next</gray> " + needed.quality().color() + needed.quality().displayName(),
                "<gray>Bring</gray> <white>" + needed.amount() + " "
                    + VisualFormat.titleCase(needed.material().name()) + "</white>",
                "<gray>Requires Smithing</gray> <white>" + needed.smithingLevel() + "</white>"
            ));
        }
        return new ForgePreview(ForgeOperation.NONE, List.of(
            "<gray>Place a gem, tome, Arcane Dust, or improvement material in the catalyst slot.</gray>"));
    }

    private Material forgeActionMaterial(ForgeOperation operation) {
        return switch (operation) {
            case APPLY, QUALITY -> Material.SMITHING_TABLE;
            case MERGE -> Material.AMETHYST_CLUSTER;
            case UNSOCKET -> Material.GRINDSTONE;
            case NONE -> Material.ANVIL;
        };
    }

    private String forgeActionName(ForgeOperation operation) {
        return switch (operation) {
            case APPLY -> "<green>Review Upgrade</green>";
            case QUALITY -> "<green>Improve Quality</green>";
            case MERGE -> "<green>Merge Gems</green>";
            case UNSOCKET -> "<aqua>Review Gem Recovery</aqua>";
            case NONE -> "<red>Forge Waiting</red>";
        };
    }

    private void openCosmetics(Player player) {
        var account = account(player);
        if (account == null) {
            return;
        }
        plugin.entitlements().activeEntitlements(account, Instant.now()).thenAccept(entitlements -> sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            MenuInventory menu = detailMenu(View.COSMETICS, 45, "<gold><bold>Cosmetic Wardrobe</bold></gold>");
            if (entitlements.isEmpty()) {
                button(menu, 22, Material.ARMOR_STAND, "<gray>No Cosmetics Yet</gray>", List.of(
                    "<gray>Cosmetics are account-bound and visual only.</gray>"
                ), ignored -> { });
            } else {
                int slot = 10;
                for (EntitlementService.Entitlement entitlement : entitlements.stream()
                    .sorted(java.util.Comparator.comparing(EntitlementService.Entitlement::productId)).toList()) {
                    button(menu, slot++, Material.GLOW_INK_SAC,
                        "<gold>" + VisualFormat.titleCase(entitlement.productId()) + "</gold>", List.of(
                            "<gray>Yours to wear. Cannot be traded.</gray>",
                            entitlement.expiresAt() == null ? "<green>Permanent</green>"
                                : "<gray>Expires</gray> <white>" + entitlement.expiresAt() + "</white>"
                        ), ignored -> { });
                    if (slot == 17) {
                        slot = 19;
                    }
                }
            }
            open(player, menu.inventory());
        }));
    }

    private void openSettings(Player player) {
        MenuInventory menu = detailMenu(View.SETTINGS, 45, "<white><bold>Player Settings</bold></white>");
        boolean hud = plugin.presentation().hudVisible(player);
        button(menu, 4, hud ? Material.LIME_DYE : Material.GRAY_DYE,
            "<white>Sidebar HUD: " + (hud ? "<green>Visible</green>" : "<gray>Hidden</gray>") + "</white>", List.of(
                "<gray>Only changes your personal sidebar.</gray>",
                "<yellow>Click to toggle the sidebar.</yellow>"
            ), target -> {
                plugin.presentation().toggleHud(target);
                openSettings(target);
            });
        var options = net.nerdsmp.paper.player.PlayerSettingsService.Setting.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
        Material[] materials = {
            Material.PAPER, Material.WRITABLE_BOOK, Material.PLAYER_HEAD,
            Material.BELL, Material.EMERALD, Material.LEAD, Material.GRASS_BLOCK, Material.ENDER_PEARL, Material.GLISTERING_MELON_SLICE,
            Material.AMETHYST_CLUSTER, Material.FIREWORK_STAR, Material.DIAMOND_AXE, Material.OAK_SAPLING
        };
        for (int index = 0; index < options.length; index++) {
            var option = options[index];
            boolean enabled = plugin.settings().enabled(player.getUniqueId(), option);
            button(menu, slots[index], enabled ? materials[index] : Material.GRAY_DYE,
                "<white>" + option.displayName() + ": " + (enabled ? "<green>On</green>" : "<gray>Off</gray>")
                    + "</white>", List.of(
                        settingDescription(option),
                        "<yellow>Click to " + (enabled ? "disable" : "enable") + ".</yellow>"
                    ), target -> {
                        plugin.settings().toggle(target.getUniqueId(), option);
                        openSettings(target);
                    });
        }
        String channel = plugin.settings().channel(player.getUniqueId());
        button(menu, 31, Material.OAK_SIGN, "<aqua>Active Chat: " + VisualFormat.titleCase(channel) + "</aqua>", List.of(
            plugin.skyblockEnabled() ? "<gray>Global, local, trade, party or island chat.</gray>" : "<gray>Global, local, trade, party or homestead chat.</gray>",
            "<yellow>Use /channel [name] to switch.</yellow>"
        ), target -> {
            target.closeInventory();
            target.performCommand("channel");
        });
        open(player, menu.inventory());
    }

    private String settingDescription(net.nerdsmp.paper.player.PlayerSettingsService.Setting setting) {
        return switch (setting) {
            case TIPS -> "<gray>Periodic reminders about useful features.</gray>";
            case DIRECT_MESSAGES -> "<gray>Allow private messages from players you have not ignored.</gray>";
            case FRIEND_REQUESTS -> "<gray>Allow new friend requests.</gray>";
            case FRIEND_NOTICES -> "<gray>See when a friend comes online.</gray>";
            case TRADE_REQUESTS -> "<gray>Allow secure item trade requests.</gray>";
            case PARTY_REQUESTS -> "<gray>Allow players to invite you to a party.</gray>";
            case COOP_INVITES -> "<gray>Allow invitations to share an island.</gray>";
            case TELEPORT_REQUESTS -> "<gray>Allow /tpa and /tpahere requests.</gray>";
            case MOB_HEALTH_BARS -> "<gray>Show health bars for enemies you engage.</gray>";
            case ISLAND_DISPLAYS -> "<gray>Show nearby island centerpieces and progress labels.</gray>";
            case ISLAND_EFFECTS -> "<gray>Show chapter reveals, particles and celebration sounds.</gray>";
            case TREE_FELLING -> "<gray>Fell eligible trees with an axe. Sneak for individual logs.</gray>";
            case TREE_REPLANTING -> "<gray>Use plain saplings from your inventory after felling a tree.</gray>";
        };
    }

    private MenuInventory detailMenu(View view, int size, String title) {
        MenuInventory menu = menu(view, size, title);
        decorate(menu.inventory());
        if (view == View.FORGE) {
            // The forge fills its bottom row with tools, so it keeps the station layout.
            backButton(menu, StationLayout.BACK, view);
            rawButton(menu, StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
            return menu;
        }
        backButton(menu, MenuKit.backSlot(size), view);
        rawButton(menu, MenuKit.closeSlot(size), MenuKit.close(), Player::closeInventory);
        return menu;
    }

    private void backButton(MenuInventory menu, int slot, View from) {
        View parent = parent(from);
        rawButton(menu, slot, MenuKit.back(parentName(parent)), player -> open(player, parent));
    }

    /** Where each screen's Back button leads: its page in the hub, not always the hub itself. */
    static View parent(View view) {
        return switch (view) {
            case SKILL_TREE -> View.SKILLS;
            case PROFILE, SKILLS, ADVENTURER_RANKS, RELICS, COSMETICS -> View.CHARACTER;
            case JOBS, QUESTS, CAMPAIGN -> View.ADVENTURE;
            case ORDERS -> View.JOBS;
            case FORGE, ENCHANTMENTS -> View.WORKSHOP;
            case MARKET, CLAIMS -> View.WORLD;
            default -> View.HOME;
        };
    }

    private String parentName(View view) {
        return switch (view) {
            case CHARACTER -> "Character";
            case ADVENTURE -> "Adventure";
            case WORKSHOP -> "Workshop";
            case WORLD -> "World & Trade";
            case COMMUNITY -> "Community";
            case JOBS -> plugin.skyblockEnabled() ? "Work & Trade" : "Jobs & Orders";
            case SKILLS -> "Skills";
            default -> "Menu";
        };
    }

    private MenuInventory menu(View view, int size, String title) {
        MenuHolder holder = new MenuHolder(view);
        Inventory inventory = Bukkit.createInventory(holder, size, miniMessage.deserialize(title));
        holder.inventory = inventory;
        return new MenuInventory(inventory, holder);
    }

    private void decorate(Inventory inventory) {
        MenuKit.frame(inventory);
    }

    private void navigate(MenuInventory menu, int slot, Material material, String name, List<String> lore, View target) {
        button(menu, slot, material, name, MenuKit.withHint(lore, "open"), player -> open(player, target));
    }

    /**
     * A hub tile for a service players normally use in the world. Without remote access it is
     * shown locked, says where to go, and does nothing when clicked.
     */
    private void stationButton(MenuInventory menu, Player player, int slot, Material material, String color,
                               RemoteStation station, List<String> lore, Consumer<Player> open) {
        if (RemoteStation.remote(player)) {
            button(menu, slot, material, "<" + color + ">" + station.displayName() + "</" + color + ">",
                MenuKit.withHint(lore, "open"), open);
            return;
        }
        List<String> lines = new ArrayList<>(lore);
        lines.add("");
        lines.add(station.lockedLore());
        lines.add("<gray>Premium opens it from anywhere.</gray>");
        rawButton(menu, slot, MenuKit.locked(material, station.displayName(), lines), ignored -> { });
    }

    private Consumer<Player> runCommand(MenuInventory menu, String command) {
        return player -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory()) {
                player.closeInventory();
                player.performCommand(command);
            }
        });
    }

    private void commandButton(
        MenuInventory menu,
        int slot,
        Material material,
        String name,
        List<String> lore,
        String command
    ) {
        button(menu, slot, material, name, MenuKit.withHint(lore, "open"), runCommand(menu, command));
    }

    private void button(
        MenuInventory menu,
        int slot,
        Material material,
        String name,
        List<String> lore,
        Consumer<Player> action
    ) {
        rawButton(menu, slot, icon(material, name, lore), action);
    }

    private void rawButton(MenuInventory menu, int slot, ItemStack item, Consumer<Player> action) {
        menu.inventory().setItem(slot, item);
        menu.holder().actions.put(slot, action);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(message(name));
        meta.displayName(null);
        meta.lore(lore.stream().map(this::message).toList());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private Component message(String markup) {
        return miniMessage.deserialize(markup).decoration(TextDecoration.ITALIC, false);
    }

    private Material skillMaterial(Skill skill) {
        return switch (skill) {
            case MINING -> Material.IRON_PICKAXE;
            case FORESTRY -> Material.OAK_SAPLING;
            case FARMING -> Material.WHEAT;
            case FISHING -> Material.FISHING_ROD;
            case HUNTING -> Material.BOW;
            case EXCAVATION -> Material.BRUSH;
            case SMITHING -> Material.ANVIL;
            case ALCHEMY -> Material.BREWING_STAND;
            case BUILDING -> Material.BRICKS;
            case COOKING -> Material.COOKED_CHICKEN;
            case ENCHANTING -> Material.ENCHANTING_TABLE;
        };
    }

    private String skillTrainingHint(Skill skill) {
        return switch (skill) {
            case MINING -> "Mine naturally generated ore.";
            case FORESTRY -> "Fell trees with an axe; vary the species.";
            case FARMING -> "Harvest fully grown crops.";
            case FISHING -> "Land fish, creatures, and rare catches.";
            case HUNTING -> "Defeat hostile creatures.";
            case EXCAVATION -> "Excavate soil, sand, gravel, clay, and mud.";
            case SMITHING -> "Craft weapons, tools, armor, and shields.";
            case ALCHEMY -> "Brew mixtures and prepare alchemical goods.";
            case BUILDING -> "Place structural blocks, details, and landmark materials.";
            case COOKING -> "Prepare meals at a crafting table or cook ordinary food.";
            case ENCHANTING -> "Enchant gear, research Aspects and imbue enchantments.";
        };
    }

    private Material questMaterial(QuestAction action) {
        return switch (action) {
            case MINE_ORE -> Material.IRON_PICKAXE;
            case CHOP_LOG -> Material.IRON_AXE;
            case SLAY_MONSTER -> Material.IRON_SWORD;
            case CRAFT_ITEM -> Material.CRAFTING_TABLE;
            case CATCH_FISH -> Material.FISHING_ROD;
        };
    }

    private Material setMaterial(String setId) {
        return switch (setId) {
            case "frontier" -> Material.LEATHER_CHESTPLATE;
            case "veilwalker" -> Material.COMPASS;
            case "emberforged" -> Material.MAGMA_CREAM;
            case "verdant_oath" -> Material.FLOWERING_AZALEA;
            case "tideglass" -> Material.HEART_OF_THE_SEA;
            case "hollowfang" -> Material.BONE;
            case "starfall" -> Material.NETHER_STAR;
            case "ironroot" -> Material.COPPER_INGOT;
            case "wildwood" -> Material.MOSS_BLOCK;
            case "riftglass" -> Material.AMETHYST_CLUSTER;
            default -> Material.AMETHYST_SHARD;
        };
    }

    private net.nerdsmp.api.model.AccountId account(Player player) {
        var account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
        }
        return account;
    }

    private void open(Player player, Inventory inventory) {
        if (player.isOnline()) {
            if (inventory.getHolder(false) instanceof MenuHolder holder) { holder.viewer = player.getUniqueId(); holder.viewerAccount = account(player); }
            player.openInventory(inventory);
        }
    }

    private void onForgeClick(InventoryClickEvent event, MenuHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || !forgeSession(holder, player)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (holder.forgeRepairPending) {
            if (event.getRawSlot() == topSize - 1 && event.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory() == holder.inventory) player.closeInventory();
                });
            }
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            return;
        }
        if (event.getRawSlot() < topSize) {
            if (isForgeInput(event.getRawSlot())) {
                clearForgeReview(holder);
                event.setCancelled(false);
                scheduleForgeRender(holder, player);
                return;
            }
            Consumer<Player> action = holder.actions.get(event.getRawSlot());
            if (action != null && event.isLeftClick() && !event.isShiftClick()) {
                long revision = holder.forgeGeneration;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() == holder && forgeSession(holder, player)
                        && holder.forgeGeneration == revision) action.accept(player);
                });
            }
            return;
        }
        if (!event.isShiftClick()) {
            event.setCancelled(false);
            return;
        }
        ItemStack source = event.getCurrentItem();
        int destination = forgeShiftDestination(holder.inventory, source);
        if (destination == ForgeSlotPolicy.NO_SLOT) {
            player.sendRichMessage("<red>The forge has no suitable empty input slot for that item.</red>");
            return;
        }
        ItemStack moved = source.clone();
        // Equipment is worked on individually; catalysts and merging gems can remain stacked.
        if (plugin.items().isAuthentic(source)) {
            moved.setAmount(1);
            ItemStack remainder = source.clone();
            remainder.subtract(1);
            event.setCurrentItem(remainder.getAmount() <= 0 ? null : remainder);
        } else {
            event.setCurrentItem(null);
        }
        holder.inventory.setItem(destination, moved);
        clearForgeReview(holder);
        renderForge(holder, player);
    }

    private int forgeShiftDestination(Inventory inventory, ItemStack item) {
        RepairService.ItemState repairState = plugin.repairs().quoteItem(item).state();
        ForgeSlotPolicy.InputKind kind = plugin.enchanting().isArcaneDust(item)
            ? ForgeSlotPolicy.InputKind.DUST
            : plugin.forge().gem(item).isPresent()
            ? ForgeSlotPolicy.InputKind.GEM
            : plugin.forge().tome(item).isPresent()
                ? ForgeSlotPolicy.InputKind.TOME
                : plugin.forge().isQualityMaterial(item)
                    ? ForgeSlotPolicy.InputKind.QUALITY_MATERIAL
                : plugin.items().isAuthentic(item) || repairState == RepairService.ItemState.READY
                    || repairState == RepairService.ItemState.UNDAMAGED
                    ? ForgeSlotPolicy.InputKind.GEAR
                    : ForgeSlotPolicy.InputKind.INVALID;
        return ForgeSlotPolicy.shiftDestination(
            kind,
            empty(inventory.getItem(ForgeSlotPolicy.PRIMARY_SLOT)),
            empty(inventory.getItem(ForgeSlotPolicy.CATALYST_SLOT))
        );
    }

    private void scheduleForgeRender(MenuHolder holder, Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (forgeSession(holder, player) && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                normalizeForgeSlot(holder.inventory, ForgeSlotPolicy.PRIMARY_SLOT);
                normalizeForgeSlot(holder.inventory, ForgeSlotPolicy.CATALYST_SLOT);
                renderForge(holder, player);
            }
        });
    }

    private boolean isForgeInput(int slot) {
        return slot == ForgeSlotPolicy.PRIMARY_SLOT || slot == ForgeSlotPolicy.CATALYST_SLOT;
    }

    private boolean forgeSession(MenuHolder holder, Player player) {
        return player.isOnline() && !player.isDead() && Bukkit.getPlayer(player.getUniqueId()) == player
            && player.getGameMode() == org.bukkit.GameMode.SURVIVAL && player.getUniqueId().equals(holder.forgeOwner)
            && holder.forgeAccount != null && plugin.sessions().account(player.getUniqueId()).filter(holder.forgeAccount::equals).isPresent();
    }

    private static void clearForgeReview(MenuHolder holder) {
        holder.forgeReview = null;
        holder.forgeRepairQuote = null;
        holder.forgeGeneration++;
    }
    private static long forgeNow() { return System.nanoTime() / 1_000_000L; }

    private boolean empty(ItemStack item) {
        if (item == null || item.getAmount() <= 0) return true;
        Material material = item.getType();
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private void normalizeForgeSlot(Inventory inventory, int slot) {
        if (empty(inventory.getItem(slot))) {
            inventory.setItem(slot, null);
        }
    }

    private void returnForgeInputs(Player player, Inventory inventory) {
        for (int slot : new int[] {ForgeSlotPolicy.PRIMARY_SLOT, ForgeSlotPolicy.CATALYST_SLOT}) {
            ItemStack item = inventory.getItem(slot);
            inventory.setItem(slot, null);
            if (!empty(item)) {
                player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
    }

    private boolean canReturnForgeInputs(Player player, Inventory inventory) {
        List<ForgeReturnCapacity.Stack<ItemStack>> storage = new ArrayList<>();
        List<ForgeReturnCapacity.Stack<ItemStack>> inputs = new ArrayList<>();
        int maximum = player.getInventory().getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            storage.add(empty(item) ? null : new ForgeReturnCapacity.Stack<>(item, item.getAmount(),
                Math.min(maximum, item.getMaxStackSize())));
        }
        for (int slot : new int[] {ForgeSlotPolicy.PRIMARY_SLOT, ForgeSlotPolicy.CATALYST_SLOT}) {
            ItemStack item = inventory.getItem(slot);
            if (!empty(item)) inputs.add(new ForgeReturnCapacity.Stack<>(item, item.getAmount(),
                Math.min(maximum, item.getMaxStackSize())));
        }
        return ForgeReturnCapacity.fits(storage, inputs, ItemStack::isSimilar);
    }

    private void reopenLater(Player player, View view) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                open(player, view);
            }
        }, 10L);
    }


    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    private String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private String adventurerProgress(AdventurerProgression.Progress progress, int segments) {
        if (progress.level() >= AdventurerProgression.MAX_LEVEL) {
            return "<gold>Maximum Adventurer Level</gold>";
        }
        return VisualFormat.progressBar(progress.experienceIntoLevel(), progress.experienceNeeded(), segments)
            + " <white>" + VisualFormat.compact(progress.experienceIntoLevel()) + "/"
            + VisualFormat.compact(progress.experienceNeeded()) + " XP</white>";
    }

    public enum View {
        HOME,
        CHARACTER,
        ADVENTURE,
        WORKSHOP,
        WORLD,
        COMMUNITY,
        PROFILE,
        SKILLS,
        SKILL_TREE,
        ADVENTURER_RANKS,
        JOBS,
        ORDERS,
        RELICS,
        CAMPAIGN,
        MARKET,
        CLAIMS,
        COSMETICS,
        SETTINGS,
        FORGE,
        ENCHANTMENTS,
        QUESTS,
        CONFIRM_TALENT
    }

    private enum TalentState {
        UNLOCKED,
        AVAILABLE,
        LEVEL_LOCKED,
        PATH_LOCKED,
        NO_POINTS
    }

    private record MenuInventory(Inventory inventory, MenuHolder holder) {
    }

    private enum ForgeTab { AUTO, SOCKETS, IMPROVE, TOMES, REPAIR }
    private enum ForgeOperation {
        NONE,
        APPLY,
        MERGE,
        UNSOCKET,
        QUALITY
    }

    private record ForgePreview(ForgeOperation operation, List<String> lore) {
        private ForgePreview {
            lore = List.copyOf(lore);
        }

        private boolean ready() {
            return operation != ForgeOperation.NONE;
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private final View view;
        private UUID viewer;
        private net.nerdsmp.api.model.AccountId viewerAccount;
        private ForgeTab forgeTab = ForgeTab.AUTO;
        private boolean current(Player player, NerdSmpPlugin plugin) {
            return viewerAccount != null && viewer != null && viewer.equals(player.getUniqueId()) && player.isOnline() && !player.isDead()
                && Bukkit.getPlayer(viewer) == player && plugin.sessions().account(viewer).filter(viewerAccount::equals).isPresent();
        }
        private final Map<Integer, Consumer<Player>> actions = new java.util.HashMap<>();
        private Inventory inventory;
        private UUID forgeOwner;
        private net.nerdsmp.api.model.AccountId forgeAccount;
        private int socketIndex = -1, socketPage;
        private long forgeGeneration;
        private ItemStack selectedGear;
        private ForgeReview<ItemStack> forgeReview;
        private RepairService.ItemQuote forgeRepairQuote;
        private long forgeRepairExpires;
        private boolean forgeRepairPending;

        private MenuHolder(View view) {
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
