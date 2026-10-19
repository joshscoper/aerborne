package net.nerdsmp.paper.forge;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

public final class ArcaneEnchantingService implements Listener, AutoCloseable {
    private static final int MAX_BOOKSHELF_POWER = 15;
    private static final int DUST_YIELD = 4;

    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey dustKey;
    private final NamespacedKey dustRecipeKey;

    public ArcaneEnchantingService(NerdSmpPlugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
        dustKey = new NamespacedKey(plugin, "arcane_dust");
        dustRecipeKey = new NamespacedKey(plugin, "arcane_dust_recipe");
    }

    public void start() {
        plugin.getServer().removeRecipe(dustRecipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(dustRecipeKey, createArcaneDust(DUST_YIELD));
        recipe.addIngredient(Material.LAPIS_LAZULI);
        recipe.addIngredient(Material.AMETHYST_SHARD);
        if (!plugin.getServer().addRecipe(recipe)) {
            throw new IllegalStateException("Could not register the Arcane Dust recipe");
        }
    }

    public ItemStack createArcaneDust(int amount) {
        ItemStack dust = new ItemStack(Material.GLOWSTONE_DUST, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = dust.getItemMeta();
        meta.itemName(message("<light_purple><bold>Arcane Dust</bold></light_purple>"));
        meta.displayName(null);
        meta.lore(List.of(
            message("<gray>A refined catalyst for Arcane Enchanting.</gray>"),
            message("<dark_gray>Crafted from lapis lazuli and an amethyst shard.</dark_gray>")
        ));
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(net.nerdsmp.paper.item.VanillaTextures.customModels(plugin) ? NamespacedKey.fromString("nerdsmp:arcane_dust") : null);
        meta.getPersistentDataContainer().set(dustKey, PersistentDataType.BYTE, (byte) 1);
        dust.setItemMeta(meta);
        return dust;
    }

    public boolean isArcaneDust(ItemStack item) {
        if (item == null || item.getType() != Material.GLOWSTONE_DUST || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(dustKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public int countArcaneDust(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isArcaneDust(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public EnchantingView view(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        int bookshelfPower = nearbyBookshelfPower(player);
        if (bookshelfPower < 0) {
            return new EnchantingView(ViewState.NO_STATION, 0, null, List.of());
        }
        if (!items.isAuthentic(item)) {
            return new EnchantingView(ViewState.NOT_GEAR, bookshelfPower, null, List.of());
        }
        if (items.isUnidentified(item)) {
            return new EnchantingView(ViewState.UNIDENTIFIED, bookshelfPower,
                items.describe(item).orElseThrow().itemId(), List.of());
        }
        if (items.arcaneCorruptionId(item).isPresent()) {
            return new EnchantingView(ViewState.CORRUPTED, bookshelfPower, items.describe(item).orElseThrow().itemId(), List.of());
        }
        List<ArcaneEnchantment> eligible = eligible(item);
        if (eligible.isEmpty()) {
            return new EnchantingView(ViewState.MAXED, bookshelfPower,
                items.describe(item).orElseThrow().itemId(), List.of());
        }
        return new EnchantingView(
            ViewState.READY,
            bookshelfPower,
            items.describe(item).orElseThrow().itemId(),
            rollOffers(player, item, eligible, bookshelfPower)
        );
    }

    public ApplyResult apply(Player player, int button) {
        EnchantingView current = view(player);
        ApplyState blocked = switch (current.state()) {
            case NO_STATION -> ApplyState.NO_STATION;
            case NOT_GEAR -> ApplyState.NOT_GEAR;
            case UNIDENTIFIED -> ApplyState.UNIDENTIFIED;
            case CORRUPTED -> ApplyState.CORRUPTED;
            case MAXED -> ApplyState.NO_UPGRADE;
            case READY -> null;
        };
        if (blocked != null) {
            return new ApplyResult(blocked, null);
        }
        if (button < 0 || button >= current.offers().size()) {
            return new ApplyResult(ApplyState.OFFER_CHANGED, null);
        }
        Offer offer = current.offers().get(button);
        if (!offer.available()) {
            return new ApplyResult(ApplyState.NO_UPGRADE, offer);
        }
        if (countArcaneDust(player) < offer.dustCost()) {
            return new ApplyResult(ApplyState.NOT_ENOUGH_DUST, offer);
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        PaperItemService.UpgradeResult upgraded = items.enchant(item, offer.enchantment(), offer.level());
        if (upgraded != PaperItemService.UpgradeResult.APPLIED) {
            return new ApplyResult(upgraded == PaperItemService.UpgradeResult.UNIDENTIFIED
                ? ApplyState.UNIDENTIFIED : ApplyState.OFFER_CHANGED, offer);
        }
        removeArcaneDust(player, offer.dustCost());
        player.setEnchantmentSeed(ThreadLocalRandom.current().nextInt());
        items.refresh(item);
        plugin.gearSets().refreshInventory(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.15F);
        // Learning by doing: the Arcane Codex records what this player has bound. Never affects the enchant.
        if (plugin.arcane() != null) plugin.arcane().recordLegacyBind(player, offer.enchantment(), offer.level(),
            net.nerdsmp.paper.forge.arcane.ArcaneForge.BindSource.TABLE, offer.dustCost());
        return new ApplyResult(ApplyState.APPLIED, offer);
    }

    public ApplyResult applyReviewed(Player player, Offer expected, ItemStack original, int heldSlot) {
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL || player.getInventory().getHeldItemSlot() != heldSlot
            || !player.getInventory().getItemInMainHand().equals(original)) return new ApplyResult(ApplyState.OFFER_CHANGED, null);
        var current = view(player);
        if (current.state() != ViewState.READY || current.offers().stream().noneMatch(expected::equals)) return new ApplyResult(ApplyState.OFFER_CHANGED, null);
        return apply(player, expected.button());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTableUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                // The player is at a real table: the station opens without the Premium remote permission.
                plugin.menus().openStation(event.getPlayer(), net.nerdsmp.paper.gui.MenuManager.View.ENCHANTMENTS);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemEnchantEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        event.setCancelled(true);
        event.getEnchanter().sendRichMessage(
            "<red>Vanilla enchanting is disabled. Right-click the table to use Arcane Enchanting.</red>"
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);
        if (isEnchantedBook(first) || isEnchantedBook(second)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(dustRecipeKey);
    }

    private List<Offer> rollOffers(
        Player player,
        ItemStack item,
        List<ArcaneEnchantment> eligible,
        int bookshelfPower
    ) {
        // The higher of Enchanting and Smithing: offers never get weaker than before Enchanting existed.
        int smithingLevel = plugin.arcane() != null ? plugin.arcane().arcaneLevel(player)
            : plugin.presentation().progression(player).skills()
                .getOrDefault(Skill.SMITHING, new net.nerdsmp.api.service.ProgressionService.SkillProgress(0L, 0)).level();
        long seed = Integer.toUnsignedLong(player.getEnchantmentSeed())
            ^ player.getUniqueId().getMostSignificantBits()
            ^ item.getType().ordinal() * 31L
            ^ items.enchantments(item).hashCode() * 17L;
        SplittableRandom random = new SplittableRandom(seed);
        List<Offer> offers = new ArrayList<>();
        List<ArcaneEnchantment> pool = new ArrayList<>(eligible);
        for (int button = 0; button < 3; button++) {
            if (pool.isEmpty()) {
                pool.addAll(eligible);
            }
            Offer selected = Offer.unavailable(button);
            int attempts = pool.size();
            while (attempts-- > 0 && !pool.isEmpty()) {
                ArcaneEnchantment enchantment = pool.remove(random.nextInt(pool.size()));
                int rolled = EnchantingRolls.level(button, bookshelfPower, smithingLevel,
                    random.nextDouble(), enchantment.maximumLevel());
                int level = EnchantingRolls.upgradeLevel(items.enchantmentLevel(item, enchantment), rolled,
                    enchantment.maximumLevel());
                if (level > 0) {
                    selected = new Offer(
                        enchantment,
                        level,
                        EnchantingRolls.dustCost(button),
                        button
                    );
                    break;
                }
            }
            offers.add(selected);
        }
        return List.copyOf(offers);
    }

    private List<ArcaneEnchantment> eligible(ItemStack item) {
        return java.util.Arrays.stream(net.nerdsmp.paper.forge.arcane.ArcaneForge.obtainable(plugin))
            .filter(enchantment -> items.supportsEnchantment(item, enchantment))
            .filter(enchantment -> items.enchantmentLevel(item, enchantment) < enchantment.maximumLevel())
            .sorted(Comparator.comparing(ArcaneEnchantment::id))
            .toList();
    }

    /** True when an enchanting table is within reach, the same rule the table's offers use. */
    public boolean atTable(Player player) {
        return nearbyBookshelfPower(player) >= 0;
    }

    private int nearbyBookshelfPower(Player player) {
        Block origin = player.getLocation().getBlock();
        Block nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -4; x <= 4; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -4; z <= 4; z++) {
                    int blockY = origin.getY() + y;
                    if (blockY < origin.getWorld().getMinHeight() || blockY >= origin.getWorld().getMaxHeight()
                        || !origin.getWorld().isChunkLoaded((origin.getX() + x) >> 4, (origin.getZ() + z) >> 4)) continue;
                    Block candidate = origin.getRelative(x, y, z);
                    if (candidate.getType() != Material.ENCHANTING_TABLE) {
                        continue;
                    }
                    double distance = x * x + y * y + z * z;
                    if (distance < nearestDistance) {
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest == null ? -1 : bookshelfPower(nearest);
    }

    static int bookshelfPower(Block table) {
        int shelves = 0;
        for (int y = 0; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean ring = Math.abs(x) == 2 && Math.abs(z) <= 1
                        || Math.abs(z) == 2 && Math.abs(x) <= 1;
                    Material gap = loadedType(table, x / 2, y, z / 2);
                    if (!ring || gap == null || !gap.isAir()) {
                        continue;
                    }
                    Material target = loadedType(table, x, y, z);
                    if (target != null && target.name().endsWith("BOOKSHELF")) {
                        shelves++;
                    }
                }
            }
        }
        return Math.min(MAX_BOOKSHELF_POWER, shelves);
    }

    /** Station inspection must never generate or load a neighboring chunk. */
    private static Material loadedType(Block origin, int x, int y, int z) {
        var world = origin.getWorld();
        int blockY = origin.getY() + y;
        if (blockY < world.getMinHeight() || blockY >= world.getMaxHeight()
            || !world.isChunkLoaded((origin.getX() + x) >> 4, (origin.getZ() + z) >> 4)) return null;
        return world.getBlockAt(origin.getX() + x, blockY, origin.getZ() + z).getType();
    }

    private void removeArcaneDust(Player player, int amount) {
        int remaining = amount;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
            ItemStack item = storage[slot];
            if (!isArcaneDust(item)) {
                continue;
            }
            int taken = Math.min(remaining, item.getAmount());
            item.subtract(taken);
            remaining -= taken;
        }
    }

    private boolean isEnchantedBook(ItemStack item) {
        return item != null && item.getType() == Material.ENCHANTED_BOOK;
    }

    private Component message(String markup) {
        return miniMessage.deserialize(markup).decoration(TextDecoration.ITALIC, false);
    }

    public enum ViewState {
        READY,
        NO_STATION,
        NOT_GEAR,
        UNIDENTIFIED,
        CORRUPTED,
        MAXED
    }

    public enum ApplyState {
        APPLIED,
        NO_STATION,
        NOT_GEAR,
        UNIDENTIFIED,
        CORRUPTED,
        NO_UPGRADE,
        NOT_ENOUGH_DUST,
        OFFER_CHANGED
    }

    public record Offer(
        ArcaneEnchantment enchantment,
        int level,
        int dustCost,
        int button
    ) {
        private static Offer unavailable(int button) {
            return new Offer(null, 0, 0, button);
        }

        public boolean available() {
            return enchantment != null;
        }
    }

    public record EnchantingView(ViewState state, int bookshelfPower, ItemId itemId, List<Offer> offers) {
        public EnchantingView {
            offers = List.copyOf(offers);
        }
    }

    public record ApplyResult(ApplyState state, Offer offer) {
    }

    @Override
    public void close() {
        plugin.getServer().removeRecipe(dustRecipeKey);
    }
}
