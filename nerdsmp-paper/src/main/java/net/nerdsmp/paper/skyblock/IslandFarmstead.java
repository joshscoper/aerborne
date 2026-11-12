package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.item.FoodItems;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import net.nerdsmp.paper.presentation.VisualFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The island's farm, after Hypixel's garden: a composter that turns surplus produce into Compost, and crop milestones
 * that count every ripe crop a player harvests on an island and raise that crop's yield as they climb. Harvests also
 * feed the weekend farming contests ({@link FarmingContests}).
 */
public final class IslandFarmstead implements Listener {
    /** A crop that counts: the block that's harvested and what it gives. */
    public enum Crop {
        WHEAT("Wheat", Material.WHEAT, Material.WHEAT, 1.0),
        CARROT("Carrot", Material.CARROTS, Material.CARROT, 1.0),
        POTATO("Potato", Material.POTATOES, Material.POTATO, 1.0),
        BEETROOT("Beetroot", Material.BEETROOTS, Material.BEETROOT, 1.0),
        MELON("Melon", Material.MELON, Material.MELON_SLICE, 1.0),
        PUMPKIN("Pumpkin", Material.PUMPKIN, Material.PUMPKIN, 0.5),
        SUGAR_CANE("Sugar Cane", Material.SUGAR_CANE, Material.SUGAR_CANE, 1.2),
        NETHER_WART("Nether Wart", Material.NETHER_WART, Material.NETHER_WART, 0.8),
        COCOA("Cocoa", Material.COCOA, Material.COCOA_BEANS, 0.6);

        final String title; final Material block, produce;
        /** How quickly this crop is harvested next to wheat: contest targets scale by it. */
        final double pace;
        Crop(String title, Material block, Material produce, double pace) { this.title = title; this.block = block; this.produce = produce; this.pace = pace; }
        public String title() { return title; }
        public Material produce() { return produce; }
        public double pace() { return pace; }
        public static Crop of(Material block) {
            for (Crop crop : values()) if (crop.block == block) return crop;
            return null;
        }
    }

    /** Lifetime harvests for each milestone tier (I to VI); each tier adds 1.5% chance of an extra crop. */
    static final int[] TIERS = {100, 500, 1500, 5000, 15000, 50000};
    static final double EXTRA_PER_TIER = 0.015;
    static final int FAVOUR_PER_TIER = 50;

    static int tier(long harvested) {
        int tier = 0;
        for (int need : TIERS) if (harvested >= need) tier++;
        return tier;
    }

    static String roman(int n) { return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI"; default -> "0"; }; }

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final String cropsPath, composterPath;

    IslandFarmstead(NerdSmpPlugin plugin, SkyblockController islands, String season) {
        this.plugin = plugin;
        this.islands = islands;
        this.cropsPath = "seasons." + season + ".crop-milestones";
        this.composterPath = "seasons." + season + ".composter";
    }

    // ------------------------------------------------------------------ crop milestones

    public long harvested(AccountId account, Crop crop) { return plugin.playerData().read(account, yaml -> yaml.getLong(cropsPath + "." + crop.name(), 0L)); }

    /** The player's best milestone tier over every crop. */
    public int bestTier(AccountId account) {
        int best = 0;
        for (Crop crop : Crop.values()) best = Math.max(best, tier(harvested(account, crop)));
        return best;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        Crop crop = Crop.of(block.getType());
        if (crop == null) return;
        Player player = event.getPlayer();
        if (islands.activityIsland(player) == null) return;
        if (block.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) return;
        if (plugin.skills() != null && !plugin.skills().canRewardFarmingHarvest(block)) return;
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) return;
        long[] now = {0};
        plugin.playerData().update(account, yaml -> {
            now[0] = yaml.getLong(cropsPath + "." + crop.name(), 0L) + 1;
            yaml.set(cropsPath + "." + crop.name(), now[0]);
        });
        int tier = tier(now[0]);
        if (tier > tier(now[0] - 1)) milestone(player, account, crop, tier);
        double extra = tier * EXTRA_PER_TIER * (plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.BOUNTIFUL_FIELDS) ? 2 : 1);
        if (plugin.festivals() != null) plugin.festivals().progress(player, 1);
        if (tier > 0 && ThreadLocalRandom.current().nextDouble() < extra) {
            org.bukkit.Location at = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().dropItemNaturally(at, new ItemStack(crop.produce));
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 4, 0.3, 0.3, 0.3, 0);
        }
        if (islands.contests() != null) islands.contests().harvest(player, account, crop);
    }

    private void milestone(Player player, AccountId account, Crop crop, int tier) {
        long favour = (long) FAVOUR_PER_TIER * tier;
        if (islands.guests() != null) islands.guests().addFavour(account, favour);
        Chat.send(player, "<green>✦ " + crop.title + " milestone " + roman(tier) + "!</green> <gray>+" + Math.round(tier * EXTRA_PER_TIER * 1000) / 10.0
            + "% chance of an extra " + crop.title.toLowerCase(java.util.Locale.ROOT) + "</gray> <yellow>+" + favour + " Favour</yellow>");
        ClientFeeds.toast(player, "skill", Chat.render("<green>Crop Milestone</green>"), Chat.render("<white>" + crop.title + " " + roman(tier) + "</white>"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
    }

    // ------------------------------------------------------------------ the composter

    IslandComposter.State composter(AccountId owner) {
        IslandComposter.State stored = plugin.playerData().read(owner, yaml -> new IslandComposter.State(
            yaml.getDouble(composterPath + ".organic", 0), yaml.getInt(composterPath + ".compost", 0), yaml.getLong(composterPath + ".at", 0L)));
        IslandComposter.State worked = IslandComposter.work(stored, System.currentTimeMillis());
        if (!worked.equals(stored)) save(owner, worked);
        return worked;
    }

    private void save(AccountId owner, IslandComposter.State state) {
        plugin.playerData().update(owner, yaml -> {
            yaml.set(composterPath + ".organic", state.organic());
            yaml.set(composterPath + ".compost", state.compost());
            yaml.set(composterPath + ".at", state.at());
        });
    }

    private static double organic(ItemStack stack) {
        if (stack == null || !FoodItems.ordinary(stack)) return 0;
        Double each = IslandComposter.ORGANIC.get(stack.getType());
        return each == null ? 0 : each * stack.getAmount();
    }

    /** Puts organic stacks into the composter, all of them or just one; returns how much organic matter went in. */
    private double deposit(Player player, AccountId owner, int onlySlot) {
        IslandComposter.State state = composter(owner);
        double room = IslandComposter.MAX_ORGANIC - state.organic(), added = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && room > 0.01; i++) {
            if (onlySlot >= 0 && i != onlySlot) continue;
            ItemStack stack = contents[i];
            double value = organic(stack);
            if (value <= 0) continue;
            double each = value / stack.getAmount();
            int take = (int) Math.min(stack.getAmount(), Math.floor(room / each));
            if (take <= 0) continue;
            stack.setAmount(stack.getAmount() - take);
            contents[i] = stack.getAmount() <= 0 ? null : stack;
            added += take * each;
            room -= take * each;
        }
        player.getInventory().setStorageContents(contents);
        if (added > 0) save(owner, IslandComposter.add(state, added));
        return added;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onCompost(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!IslandComposter.isCompost(plugin, held)) return;
        event.setCancelled(true);
        if (islands.activityIsland(player) == null) { Chat.error(player, "Compost only works on an island you belong to."); return; }
        Block center = event.getClickedBlock();
        int ripened = 0;
        for (int dx = -IslandComposter.RADIUS; dx <= IslandComposter.RADIUS; dx++)
            for (int dz = -IslandComposter.RADIUS; dz <= IslandComposter.RADIUS; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    Block block = center.getRelative(dx, dy, dz);
                    if (!(block.getBlockData() instanceof Ageable age) || age.getAge() >= age.getMaximumAge() || Crop.of(block.getType()) == null) continue;
                    age.setAge(age.getMaximumAge());
                    block.setBlockData(age);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.6, 0.5), 3, 0.25, 0.2, 0.25, 0);
                    ripened++;
                }
        if (ripened == 0) { Chat.info(player, "Nothing around here to ripen. Use Compost on growing crops."); return; }
        held.setAmount(held.getAmount() - 1);
        player.playSound(center.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1F, 0.9F);
        net.nerdsmp.paper.item.PlayerStatsService.message(player, Chat.render("<green>Compost ripened " + ripened + " crop" + (ripened == 1 ? "" : "s") + ".</green>"));
    }

    // ------------------------------------------------------------------ menus

    private AccountId member(Player player, AccountId owner) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        IslandService.Island island = islands.islands().owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account)) throw new IllegalArgumentException("Only this co-op can use its Farmstead.");
        return account;
    }

    void openHub(Player player, AccountId owner) {
        AccountId account = member(player, owner);
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.HUB);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<green>Farmstead</green>"));
        MenuKit.frame(holder.inventory);
        IslandComposter.State composter = composter(owner);
        holder.inventory.setItem(4, MenuKit.item(Material.HAY_BLOCK, "<green>Farmstead</green>", List.of(
            "<gray>Turn surplus into Compost, climb crop milestones</gray>", "<gray>and win medals in the weekend contests.</gray>")));
        holder.inventory.setItem(20, MenuKit.item(Material.COMPOSTER, "<green>Composter</green>", List.of(
            "<white>" + composter.compost() + "</white> <gray>Compost ready ·</gray> <white>" + (int) composter.organic() + "</white> <gray>organic</gray>",
            "<gray>Surplus crops in, Compost out.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(22, MenuKit.item(Material.GOLDEN_HOE, "<gold>Crop Milestones</gold>", List.of(
            "<gray>Best tier:</gray> <white>" + roman(bestTier(account)) + "</white>", "<gray>Every ripe crop you harvest counts.</gray>", "<yellow>Click to open.</yellow>")));
        String contest = islands.contests() == null ? "<gray>Contests are off.</gray>" : islands.contests().summary();
        holder.inventory.setItem(24, MenuKit.item(Material.GOLD_NUGGET, "<yellow>Farming Contests</yellow>", List.of(
            contest, "<gray>Medals buy farming gear.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Visitors</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    void openComposter(Player player, AccountId owner) {
        member(player, owner);
        IslandComposter.State state = composter(owner);
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.COMPOSTER);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<green>Composter</green>"));
        MenuKit.frame(holder.inventory);
        int nextIn = state.organic() >= IslandComposter.ORGANIC_PER_COMPOST && state.compost() < IslandComposter.MAX_COMPOST
            ? (int) Math.ceil((IslandComposter.MILLIS_PER_COMPOST - (System.currentTimeMillis() - state.at())) / 1000.0) : -1;
        holder.inventory.setItem(4, MenuKit.item(Material.COMPOSTER, "<green>Composter</green>", List.of(
            "<gray>Every " + (int) IslandComposter.ORGANIC_PER_COMPOST + " organic matter becomes one Compost,</gray>",
            "<gray>one every " + IslandComposter.MILLIS_PER_COMPOST / 60_000 + " minutes, even while you're away.</gray>",
            "<gray>Click organic items in your inventory to add them.</gray>")));
        holder.inventory.setItem(20, MenuKit.item(Material.HOPPER, "<yellow>Add all organic matter</yellow>", List.of(
            "<gray>Crops, seeds, leaves, saplings and more</gray>", "<gray>from your inventory.</gray>", "<yellow>Click to add.</yellow>")));
        holder.inventory.setItem(22, MenuKit.item(Material.COMPOSTER, "<white>Organic matter</white>", List.of(
            VisualFormat.progressBar((long) state.organic(), (long) IslandComposter.MAX_ORGANIC, 10) + " <white>" + (int) state.organic() + "/" + (int) IslandComposter.MAX_ORGANIC + "</white>",
            nextIn >= 0 ? "<gray>Next Compost in</gray> <white>" + Chat.duration(java.time.Duration.ofSeconds(Math.max(0, nextIn))) + "</white>"
                : state.compost() >= IslandComposter.MAX_COMPOST ? "<red>Full: collect your Compost.</red>" : "<gray>Needs " + (int) IslandComposter.ORGANIC_PER_COMPOST + " organic to start.</gray>")));
        holder.inventory.setItem(24, MenuKit.item(Material.BONE_MEAL, "<green>Collect Compost</green>", List.of(
            "<white>" + state.compost() + "/" + IslandComposter.MAX_COMPOST + "</white> <gray>ready</gray>",
            "<gray>Right-click a crop with it to ripen a 5×5 patch.</gray>", state.compost() > 0 ? "<yellow>Click to collect.</yellow>" : "<gray>Nothing ready yet.</gray>")));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Farmstead</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private static final int[] CROP_SLOTS = {19, 20, 21, 22, 23, 24, 25, 30, 32};

    void openCrops(Player player, AccountId owner) {
        AccountId account = member(player, owner);
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.CROPS);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Crop Milestones</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.GOLDEN_HOE, "<gold>Crop Milestones</gold>", List.of(
            "<gray>Every ripe crop you harvest on an island counts.</gray>",
            "<gray>Each tier: +1.5% chance of an extra crop, and Favour.</gray>",
            "<gray>Tier III in any crop brings Prize Grower Bram to visit.</gray>")));
        Crop[] crops = Crop.values();
        for (int i = 0; i < crops.length; i++) {
            Crop crop = crops[i];
            long have = harvested(account, crop);
            int tier = tier(have);
            List<String> lore = new ArrayList<>();
            lore.add(tier >= TIERS.length ? "<green>Maxed: " + have + " harvested</green>"
                : VisualFormat.progressBar(have, TIERS[tier], 10) + " <white>" + have + "/" + TIERS[tier] + "</white>");
            lore.add("<gray>Tier</gray> <white>" + roman(tier) + "</white> <gray>· extra crop</gray> <green>" + Math.round(tier * EXTRA_PER_TIER * 1000) / 10.0 + "%</green>");
            ItemStack icon = MenuKit.item(crop.produce, (tier > 0 ? "<green>" : "<white>") + crop.title + " " + roman(tier) + (tier > 0 ? "</green>" : "</white>"), lore);
            icon.setAmount(Math.max(1, tier));
            holder.inventory.setItem(CROP_SLOTS[i], icon);
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Farmstead</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int raw = event.getRawSlot();
        boolean bottom = raw >= event.getView().getTopInventory().getSize();
        int bottomSlot = bottom ? event.getSlot() : -1;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                switch (holder.page) {
                    case HUB -> {
                        if (raw == 20) openComposter(player, holder.owner);
                        else if (raw == 22) openCrops(player, holder.owner);
                        else if (raw == 24 && islands.contests() != null) islands.contests().open(player, holder.owner);
                        else if (raw == 45) islands.guests().open(player, holder.owner);
                        else if (raw == 49) player.closeInventory();
                    }
                    case COMPOSTER -> {
                        if (bottom || raw == 20) {
                            // Bottom-inventory clicks deposit that one stack; the hopper puts in everything.
                            if (bottom && bottomSlot >= 36) return;
                            double added = deposit(player, holder.owner, bottom ? bottomSlot : -1);
                            if (added > 0) { Chat.success(player, "Added <white>" + Math.round(added * 4) / 4.0 + "</white> organic matter."); player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_FILL, 0.8F, 1F); }
                            else Chat.info(player, bottom ? "That isn't organic matter, or the composter is full." : "You have no organic matter, or the composter is full.");
                            openComposter(player, holder.owner);
                        } else if (raw == 24) {
                            IslandComposter.State state = composter(holder.owner);
                            if (state.compost() <= 0) return;
                            ItemStack compost = IslandComposter.compostItem(plugin);
                            int left = state.compost();
                            while (left > 0) {
                                ItemStack stack = compost.clone();
                                stack.setAmount(Math.min(64, left));
                                left -= stack.getAmount();
                                player.getInventory().addItem(stack).values().forEach(spill -> player.getWorld().dropItemNaturally(player.getLocation(), spill));
                            }
                            save(holder.owner, new IslandComposter.State(state.organic(), 0, state.at()));
                            Chat.success(player, "Collected <white>" + state.compost() + "</white> Compost.");
                            player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_READY, 0.8F, 1F);
                            openComposter(player, holder.owner);
                        } else if (raw == 45) openHub(player, holder.owner);
                        else if (raw == 49) player.closeInventory();
                    }
                    case CROPS -> {
                        if (raw == 45) openHub(player, holder.owner);
                        else if (raw == 49) player.closeInventory();
                    }
                }
            } catch (IllegalArgumentException refused) { Chat.error(player, Chat.escape(refused.getMessage())); }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    private static final class Holder implements InventoryHolder {
        enum Page { HUB, COMPOSTER, CROPS }
        final UUID viewer; final AccountId owner; final Page page; Inventory inventory;
        Holder(UUID viewer, AccountId owner, Page page) { this.viewer = viewer; this.owner = owner; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
