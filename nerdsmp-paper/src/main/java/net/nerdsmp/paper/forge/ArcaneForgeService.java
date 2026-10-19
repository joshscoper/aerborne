package net.nerdsmp.paper.forge;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.GearSetService;
import net.nerdsmp.paper.item.GearQuality;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.item.SocketRules;
import net.nerdsmp.paper.mob.CustomMobManager;
import net.nerdsmp.paper.presentation.PlayerPresentationService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class ArcaneForgeService implements Listener {
    private final JavaPlugin plugin;
    private final PaperItemService items;
    private final GearSetService gearSets;
    private final ArcaneEnchantingService enchanting;
    private final CustomMobManager mobs;
    private final PlayerPresentationService presentation;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey forgeKindKey;
    private final NamespacedKey geodeGradeKey;
    private final NamespacedKey gemTypeKey;
    private final NamespacedKey gemTierKey;
    private final NamespacedKey enchantmentKey;
    private final NamespacedKey enchantmentLevelKey;
    private final double miningGeodeChance;
    private final double excavationGeodeChance;
    private final double customMobTomeChance;
    private final double bossTomeChance;

    public ArcaneForgeService(
        JavaPlugin plugin,
        PaperItemService items,
        GearSetService gearSets,
        ArcaneEnchantingService enchanting,
        CustomMobManager mobs,
        PlayerPresentationService presentation
    ) {
        this.plugin = plugin;
        this.items = items;
        this.gearSets = gearSets;
        this.enchanting = enchanting;
        this.mobs = mobs;
        this.presentation = presentation;
        forgeKindKey = new NamespacedKey(plugin, "forge_kind");
        geodeGradeKey = new NamespacedKey(plugin, "geode_grade");
        gemTypeKey = new NamespacedKey(plugin, "gem_type");
        gemTierKey = new NamespacedKey(plugin, "gem_tier");
        enchantmentKey = new NamespacedKey(plugin, "arcane_enchantment");
        enchantmentLevelKey = new NamespacedKey(plugin, "arcane_enchantment_level");
        miningGeodeChance = chance("forge.geodes.mining-chance", 0.018D);
        excavationGeodeChance = chance("forge.geodes.excavation-chance", 0.003D);
        customMobTomeChance = chance("forge.enchantments.custom-mob-tome-chance", 0.04D);
        bossTomeChance = chance("forge.enchantments.boss-tome-chance", 0.75D);
    }

    public ItemStack createGeode(GeodeGrade grade) {
        ItemStack item = baseItem(
            geodeMaterial(grade),
            grade.color() + "<bold>" + grade.displayName() + "</bold>",
            List.of(
                "<gray>Crack it open to find a random gem.</gray>",
                "<dark_gray>" + grade.rarity() + " geode</dark_gray>"
            ),
            "nerdsmp:geode_" + grade.id(),
            false
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(forgeKindKey, PersistentDataType.STRING, "geode");
        meta.getPersistentDataContainer().set(geodeGradeKey, PersistentDataType.STRING, grade.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGem(GemType type, int rawTier) {
        if (type == null || !SocketRules.validTier(rawTier)) {
            throw new IllegalArgumentException("Gem type and tier must be valid");
        }
        int tier = rawTier;
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>----------------------------</dark_gray>");
        lore.add(type.color() + "<bold>[ SOCKET GEM - TIER " + roman(tier) + " ]</bold>");
        for (String effect : type.effect(tier).split(", ")) {
            lore.add("<dark_gray>> </dark_gray><white>" + effect + "</white>");
        }
        lore.add("<dark_gray>----------------------------</dark_gray>");
        lore.add("<gray>Place gear and this gem in a forge to socket it.</gray>");
        if (tier < GemMergeRules.MAXIMUM_TIER) {
            lore.add("<gray>Merge two matching gems to reach Tier " + roman(tier + 1) + ".</gray>");
        } else {
            lore.add("<gray>Highest gem tier.</gray>");
        }
        lore.add("<gray>Recover intact for <light_purple>" + SocketRules.dustCost(tier)
            + " Arcane Dust</light_purple>.</gray>");
        ItemStack item = baseItem(
            type.material(),
            type.color() + "<bold>" + type.displayName() + " " + roman(tier) + "</bold>",
            lore,
            "nerdsmp:gem_" + type.id(),
            true
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(forgeKindKey, PersistentDataType.STRING, "gem");
        meta.getPersistentDataContainer().set(gemTypeKey, PersistentDataType.STRING, type.id());
        meta.getPersistentDataContainer().set(gemTierKey, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createTome(ArcaneEnchantment enchantment, int rawLevel) {
        int level = Math.max(1, Math.min(enchantment.maximumLevel(), rawLevel));
        ItemStack item = baseItem(
            Material.ENCHANTED_BOOK,
            "<light_purple><bold>" + enchantment.displayName() + " " + roman(level) + "</bold></light_purple>",
            List.of(
                "<gray>" + enchantment.description() + "</gray>",
                "<gray>Place compatible gear in the forge's primary slot</gray>",
                "<gray>and this tome in its catalyst slot.</gray>"
            ),
            "nerdsmp:enchantment_tome",
            true
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(forgeKindKey, PersistentDataType.STRING, "tome");
        meta.getPersistentDataContainer().set(enchantmentKey, PersistentDataType.STRING, enchantment.id());
        meta.getPersistentDataContainer().set(enchantmentLevelKey, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<GeodeGrade> geode(ItemStack item) {
        if (!kind(item).equals("geode")) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(geodeGradeKey, PersistentDataType.STRING);
        return Optional.ofNullable(id == null ? null : GeodeGrade.fromId(id));
    }

    public Optional<Gem> gem(ItemStack item) {
        if (!kind(item).equals("gem")) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(gemTypeKey, PersistentDataType.STRING)
            || !meta.getPersistentDataContainer().has(gemTierKey, PersistentDataType.INTEGER)) return Optional.empty();
        String id = meta.getPersistentDataContainer().get(gemTypeKey, PersistentDataType.STRING);
        Integer tier = meta.getPersistentDataContainer().get(gemTierKey, PersistentDataType.INTEGER);
        GemType type = id == null ? null : GemType.fromId(id);
        return type == null || tier == null || !SocketRules.validTier(tier)
            || item.getType() != type.material() || item.getAmount() <= 0
            ? Optional.empty() : Optional.of(new Gem(type, tier));
    }

    public Optional<Tome> tome(ItemStack item) {
        if (!kind(item).equals("tome")) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(enchantmentKey, PersistentDataType.STRING);
        Integer level = meta.getPersistentDataContainer().get(enchantmentLevelKey, PersistentDataType.INTEGER);
        ArcaneEnchantment enchantment = id == null ? null : ArcaneEnchantment.fromId(id);
        return enchantment == null || level == null
            ? Optional.empty()
            : Optional.of(new Tome(enchantment, Math.max(1, Math.min(enchantment.maximumLevel(), level))));
    }

    public ApplyResult applyHeldUpgrade(Player player) {
        ItemStack gear = player.getInventory().getItemInMainHand();
        ItemStack catalyst = player.getInventory().getItemInOffHand();
        return applyUpgrade(player, gear, catalyst);
    }

    public ApplyResult applyUpgrade(Player player, ItemStack gear, ItemStack catalyst) {
        if (gear == null || gear.getAmount() != 1) return ApplyResult.NOT_GEAR;
        Optional<Gem> gem = gem(catalyst);
        Optional<Tome> tome = tome(catalyst);
        if (gem.isEmpty() && tome.isEmpty()) {
            return ApplyResult.NO_CATALYST;
        }
        if (tome.isPresent() && items.arcaneCorruptionId(gear).isPresent()) return ApplyResult.CORRUPTED;
        PaperItemService.UpgradeResult result = gem.isPresent()
            ? items.socket(gear, gem.get().type(), gem.get().tier())
            : items.enchant(gear, tome.orElseThrow().enchantment(), tome.orElseThrow().level());
        ApplyResult mapped = switch (result) {
            case APPLIED -> ApplyResult.APPLIED;
            case NOT_GEAR -> ApplyResult.NOT_GEAR;
            case NO_CAPACITY -> ApplyResult.NO_CAPACITY;
            case INCOMPATIBLE -> ApplyResult.INCOMPATIBLE;
            case UNIDENTIFIED -> ApplyResult.UNIDENTIFIED;
            case ALREADY_PRESENT -> ApplyResult.ALREADY_PRESENT;
            case INVALID_SOCKET_DATA -> ApplyResult.INVALID_SOCKET_DATA;
        };
        if (mapped == ApplyResult.APPLIED) {
            catalyst.subtract(1);
            gearSets.refreshInventory(player);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0F, 1.25F);
            if (tome.isPresent() && plugin instanceof NerdSmpPlugin core && core.arcane() != null) {
                core.arcane().recordLegacyBind(player, tome.get().enchantment(), tome.get().level(),
                    net.nerdsmp.paper.forge.arcane.ArcaneForge.BindSource.TOME, 0);
            }
        }
        return mapped;
    }

    public MergePreview mergePreview(ItemStack first, ItemStack second) {
        Optional<Gem> firstGem = gem(first);
        Optional<Gem> secondGem = gem(second);
        if (firstGem.isEmpty() || secondGem.isEmpty()) {
            return new MergePreview(MergeResult.NEED_TWO_GEMS, null, 0);
        }
        GemMergeRules.Result rules = GemMergeRules.evaluate(
            firstGem.get().type(), firstGem.get().tier(), secondGem.get().type(), secondGem.get().tier()
        );
        MergeResult status = switch (rules.status()) {
            case READY -> MergeResult.MERGED;
            case DIFFERENT_TYPE -> MergeResult.DIFFERENT_TYPE;
            case DIFFERENT_TIER -> MergeResult.DIFFERENT_TIER;
            case MAXIMUM_TIER -> MergeResult.MAXIMUM_TIER;
        };
        return new MergePreview(status, rules.type(), rules.outputTier());
    }

    public MergeResult mergeHeldGems(Player player) {
        if (!validPlayer(player)) return MergeResult.INVALID_SESSION;
        var inventory = player.getInventory();
        ItemStack originalSecond = inventory.getItemInOffHand().clone();
        ItemStack[] originalStorage = cloneContents(inventory.getStorageContents());
        int mainSlot = inventory.getHeldItemSlot();
        MergePreview preview = mergePreview(originalStorage[mainSlot], originalSecond);
        if (!preview.ready()) return preview.result();
        ItemStack[] updatedStorage = cloneContents(originalStorage);
        updatedStorage[mainSlot].subtract(1);
        if (updatedStorage[mainSlot].getAmount() <= 0) updatedStorage[mainSlot] = null;
        ItemStack updatedSecond = originalSecond.clone();
        updatedSecond.subtract(1);
        if (!insertRecoveredGem(updatedStorage, createGem(preview.type(), preview.outputTier()),
            inventory.getMaxStackSize())) return MergeResult.INVENTORY_FULL;
        try {
            inventory.setStorageContents(updatedStorage);
            inventory.setItemInOffHand(updatedSecond.getAmount() <= 0 ? null : updatedSecond);
        } catch (RuntimeException failure) {
            try {
                inventory.setStorageContents(originalStorage);
                inventory.setItemInOffHand(originalSecond);
            } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not finish held gem merge", failure);
            return MergeResult.INVALID_SESSION;
        }
        playMergeSound(player, preview.outputTier());
        return MergeResult.MERGED;
    }

    /** Compatibility for held inputs; forge menus must use the inventory overload. */
    public MergeResult mergeGems(Player player, ItemStack first, ItemStack second) {
        if (!validPlayer(player)) return MergeResult.INVALID_SESSION;
        if (!java.util.Objects.equals(first, player.getInventory().getItemInMainHand())
            || !java.util.Objects.equals(second, player.getInventory().getItemInOffHand())) {
            return MergeResult.INPUT_CHANGED;
        }
        return mergeHeldGems(player);
    }

    public MergeResult mergeGems(Player player, Inventory forgeInventory, int firstSlot, int secondSlot,
                                  ItemStack expectedFirst, ItemStack expectedSecond) {
        if (!validForge(player, forgeInventory, firstSlot, secondSlot)) return MergeResult.INVALID_SESSION;
        ItemStack first = forgeInventory.getItem(firstSlot);
        ItemStack second = forgeInventory.getItem(secondSlot);
        if (expectedFirst == null || expectedSecond == null || !expectedFirst.equals(first)
            || !expectedSecond.equals(second)) return MergeResult.INPUT_CHANGED;
        MergePreview preview = mergePreview(first, second);
        if (!preview.ready()) return preview.result();
        ItemStack originalFirst = first.clone();
        ItemStack originalSecond = second.clone();
        ItemStack updatedFirst = first.clone();
        ItemStack updatedSecond = second.clone();
        updatedFirst.subtract(1);
        updatedSecond.subtract(1);
        ItemStack[] originalStorage = cloneContents(player.getInventory().getStorageContents());
        ItemStack[] updatedStorage = cloneContents(originalStorage);
        if (!insertRecoveredGem(updatedStorage, createGem(preview.type(), preview.outputTier()),
            player.getInventory().getMaxStackSize())) return MergeResult.INVENTORY_FULL;
        try {
            player.getInventory().setStorageContents(updatedStorage);
            forgeInventory.setItem(firstSlot, updatedFirst.getAmount() <= 0 ? null : updatedFirst);
            forgeInventory.setItem(secondSlot, updatedSecond.getAmount() <= 0 ? null : updatedSecond);
        } catch (RuntimeException failure) {
            try {
                player.getInventory().setStorageContents(originalStorage);
                forgeInventory.setItem(firstSlot, originalFirst);
                forgeInventory.setItem(secondSlot, originalSecond);
            } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Could not finish gem merge for " + player.getUniqueId(), failure);
            return MergeResult.INVALID_SESSION;
        }
        playMergeSound(player, preview.outputTier());
        return MergeResult.MERGED;
    }

    private void playMergeSound(Player player, int outputTier) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0F,
            0.95F + outputTier * 0.10F);
    }

    public Optional<QualityRequirement> qualityRequirement(ItemStack gear) {
        if (!items.isQualityUpgradable(gear)) {
            return Optional.empty();
        }
        return items.quality(gear).next().map(quality -> new QualityRequirement(
            quality, quality.requiredSmithingLevel(), quality.catalyst(), quality.catalystAmount()
        ));
    }

    public boolean isQualityMaterial(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return Arrays.stream(GearQuality.values())
            .anyMatch(quality -> quality.catalyst() == item.getType());
    }

    public QualityResult improveQuality(Player player, ItemStack gear, ItemStack catalyst) {
        if (!items.isAuthentic(gear) || gear.getAmount() != 1) {
            return QualityResult.NOT_GEAR;
        }
        if (items.isUnidentified(gear)) {
            return QualityResult.UNIDENTIFIED;
        }
        if (!items.isQualityUpgradable(gear)) {
            return QualityResult.NOT_CRAFTED;
        }
        QualityRequirement requirement = qualityRequirement(gear).orElse(null);
        if (requirement == null) {
            return QualityResult.MAXIMUM_QUALITY;
        }
        int smithingLevel = presentation.progression(player).skills()
            .getOrDefault(Skill.SMITHING,
                new net.nerdsmp.api.service.ProgressionService.SkillProgress(0L, 0)).level();
        if (smithingLevel < requirement.smithingLevel()) {
            return QualityResult.SKILL_TOO_LOW;
        }
        if (catalyst == null || catalyst.getType() != requirement.material()) {
            return QualityResult.WRONG_MATERIAL;
        }
        if (catalyst.getAmount() < requirement.amount()) {
            return QualityResult.NOT_ENOUGH_MATERIAL;
        }
        PaperItemService.QualityUpgradeResult upgraded = items.improveQuality(gear);
        if (upgraded != PaperItemService.QualityUpgradeResult.APPLIED) {
            return switch (upgraded) {
                case NOT_GEAR -> QualityResult.NOT_GEAR;
                case NOT_CRAFTED -> QualityResult.NOT_CRAFTED;
                case UNIDENTIFIED -> QualityResult.UNIDENTIFIED;
                case MAXIMUM_QUALITY -> QualityResult.MAXIMUM_QUALITY;
                case APPLIED -> throw new IllegalStateException("unreachable quality result");
            };
        }
        catalyst.subtract(requirement.amount());
        gearSets.refreshInventory(player);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F,
            1.0F + requirement.quality().tier() * 0.08F);
        return QualityResult.APPLIED;
    }

    public int unsocketDustCost(ItemStack gear) {
        List<PaperItemService.SocketedGem> sockets = items.sockets(gear);
        return unsocketDustCost(gear, sockets.size() - 1);
    }

    public int unsocketDustCost(ItemStack gear, int socketIndex) {
        if (!items.hasValidSockets(gear)) return 0;
        List<PaperItemService.SocketedGem> sockets = items.sockets(gear);
        return socketIndex < 0 || socketIndex >= sockets.size()
            ? 0 : SocketRules.dustCost(sockets.get(socketIndex).tier());
    }

    public UnsocketResult unsocket(Player player, Inventory forgeInventory, int gearSlot, int catalystSlot,
                                    int socketIndex, PaperItemService.SocketedGem expected) {
        if (!validForge(player, forgeInventory, gearSlot, catalystSlot)) {
            return UnsocketResult.INVALID_SESSION;
        }
        ItemStack gear = forgeInventory.getItem(gearSlot);
        ItemStack dust = forgeInventory.getItem(catalystSlot);
        if (!items.isSocketEquipment(gear)) {
            return UnsocketResult.NOT_GEAR;
        }
        if (items.isUnidentified(gear)) {
            return UnsocketResult.UNIDENTIFIED;
        }
        if (!items.hasValidSockets(gear)) return UnsocketResult.INVALID_SOCKET_DATA;
        List<PaperItemService.SocketedGem> sockets = items.sockets(gear);
        if (sockets.isEmpty()) {
            return UnsocketResult.NO_GEMS;
        }
        if (socketIndex < 0 || socketIndex >= sockets.size() || expected == null
            || !expected.equals(sockets.get(socketIndex))) return UnsocketResult.SOCKET_CHANGED;
        int cost = unsocketDustCost(gear, socketIndex);
        if (!enchanting.isArcaneDust(dust)) {
            return UnsocketResult.NEED_DUST;
        }
        if (dust.getAmount() < cost) {
            return UnsocketResult.NOT_ENOUGH_DUST;
        }
        ItemStack originalGear = gear.clone();
        ItemStack originalDust = dust.clone();
        ItemStack updatedGear = originalGear.clone();
        PaperItemService.SocketedGem removed = items.unsocket(updatedGear, socketIndex, expected).orElse(null);
        if (removed == null) return UnsocketResult.SOCKET_CHANGED;
        ItemStack updatedDust = originalDust.clone();
        updatedDust.subtract(cost);
        ItemStack[] originalStorage = cloneContents(player.getInventory().getStorageContents());
        ItemStack[] updatedStorage = cloneContents(originalStorage);
        if (!insertRecoveredGem(updatedStorage, createGem(removed.type(), removed.tier()),
            player.getInventory().getMaxStackSize())) return UnsocketResult.INVENTORY_FULL;
        try {
            player.getInventory().setStorageContents(updatedStorage);
            forgeInventory.setItem(gearSlot, updatedGear);
            forgeInventory.setItem(catalystSlot, updatedDust.getAmount() <= 0 ? null : updatedDust);
        } catch (RuntimeException failure) {
            try {
                player.getInventory().setStorageContents(originalStorage);
                forgeInventory.setItem(gearSlot, originalGear);
                forgeInventory.setItem(catalystSlot, originalDust);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Could not finish gem recovery for " + player.getUniqueId(), failure);
            return UnsocketResult.INVALID_SESSION;
        }
        gearSets.refreshInventory(player);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0F, 0.75F);
        return UnsocketResult.REMOVED;
    }

    private boolean validPlayer(Player player) {
        if (!Bukkit.isPrimaryThread() || player == null || !player.isOnline() || player.isDead()
            || player.getGameMode() != GameMode.SURVIVAL || !(plugin instanceof NerdSmpPlugin nerdSmp)) return false;
        var account = nerdSmp.sessions().account(player.getUniqueId()).orElse(null);
        return account != null && nerdSmp.playerData().knownAccounts().contains(account);
    }

    private boolean validForge(Player player, Inventory forgeInventory, int firstSlot, int secondSlot) {
        return validPlayer(player) && forgeInventory != null
            && player.getOpenInventory().getTopInventory() == forgeInventory
            && firstSlot >= 0 && firstSlot < forgeInventory.getSize()
            && secondSlot >= 0 && secondSlot < forgeInventory.getSize() && firstSlot != secondSlot;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        return Arrays.stream(contents).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private static boolean insertRecoveredGem(ItemStack[] contents, ItemStack gem, int inventoryLimit) {
        int limit = Math.min(inventoryLimit, gem.getMaxStackSize());
        List<ForgeOutputPlan.Stack<ItemStack>> storage = new ArrayList<>(contents.length);
        for (ItemStack item : contents) storage.add(item == null || item.getType().isAir() ? null
            : new ForgeOutputPlan.Stack<>(item, item.getAmount(), item.getMaxStackSize()));
        ForgeOutputPlan.Delivery delivery = ForgeOutputPlan.place(storage, gem, limit, ItemStack::isSimilar)
            .orElse(null);
        if (delivery == null) return false;
        ItemStack current = contents[delivery.slot()];
        if (current == null || current.getType().isAir()) contents[delivery.slot()] = gem.clone();
        else current.setAmount(delivery.amount());
        return true;
    }

    public void rollGeode(BlockBreakEvent event, Skill skill) {
        if (skill != Skill.MINING && skill != Skill.EXCAVATION) {
            return;
        }
        Player player = event.getPlayer();
        double base = skill == Skill.MINING ? miningGeodeChance : excavationGeodeChance;
        var progression = presentation.progression(player);
        double bonus = (gearSets.profile(player).stat("geode-find-percent")
            + progression.talentStat(skill, "geode-find-percent")
            + progression.adventurerStat("geode-find-percent")) / 100.0D;
        if (ThreadLocalRandom.current().nextDouble() >= base * (1.0D + bonus)) {
            return;
        }
        GeodeGrade grade = rollGrade(event.getBlock());
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), createGeode(grade));
        player.sendActionBar(miniMessage.deserialize(grade.color() + "Found a " + grade.displayName() + "."));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8F, 1.2F);
    }

    public void applyHarvestBonus(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable crop) || crop.getAge() != crop.getMaximumAge()) {
            return;
        }
        double chance = gearSets.profile(event.getPlayer()).stat("harvest-extra-percent") / 100.0D;
        Material produce = produce(block.getType());
        if (produce != null && ThreadLocalRandom.current().nextDouble() < chance) {
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(produce));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseGeode(PlayerInteractEvent event) {
        if (event.getHand() == null || (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack held = event.getHand() == EquipmentSlot.HAND
            ? event.getPlayer().getInventory().getItemInMainHand()
            : event.getPlayer().getInventory().getItemInOffHand();
        Optional<GeodeGrade> grade = geode(held);
        if (grade.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        held.subtract(1);
        GemType type = randomGem(grade.get());
        int tier = rollTier(grade.get(), ThreadLocalRandom.current().nextDouble());
        ItemStack gem = createGem(type, tier);
        giveOrDrop(event.getPlayer(), gem);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1.0F,
            0.9F + tier * 0.12F);
        event.getPlayer().sendRichMessage(type.color() + "The geode held a " + type.displayName()
            + " " + roman(tier) + ".");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (!mobs.isCustomMob(event.getEntity()) || event.getEntity().getKiller() == null) {
            return;
        }
        boolean boss = mobs.isBoss(event.getEntity());
        double chance = boss ? bossTomeChance : customMobTomeChance;
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        ArcaneEnchantment enchantment = randomEnchantment();
        int level = boss ? 3 + ThreadLocalRandom.current().nextInt(3)
            : 1 + ThreadLocalRandom.current().nextInt(2);
        event.getDrops().add(createTome(enchantment, level));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        double saveChance = gearSets.profile(event.getPlayer()).stat("durability-save-percent") / 100.0D;
        saveChance = Math.clamp(saveChance, 0.0D, 0.85D);
        if (ThreadLocalRandom.current().nextDouble() < saveChance) {
            event.setCancelled(true);
        }
    }

    public static int rollTier(GeodeGrade grade, double roll) {
        return GeodeRolls.tier(grade, roll);
    }

    private Material geodeMaterial(GeodeGrade grade) {
        return switch (grade) {
            case ROUGH -> Material.AMETHYST_SHARD;
            case RESONANT -> Material.ECHO_SHARD;
            case LUMINOUS -> Material.NETHER_STAR;
        };
    }

    private GeodeGrade rollGrade(Block block) {
        double roll = ThreadLocalRandom.current().nextDouble();
        String material = block.getType().name();
        if (material.equals("ANCIENT_DEBRIS") || material.contains("DIAMOND") || material.contains("EMERALD")) {
            return roll < 0.30D ? GeodeGrade.ROUGH : roll < 0.85D ? GeodeGrade.RESONANT : GeodeGrade.LUMINOUS;
        }
        if (material.endsWith("_ORE")) {
            return roll < 0.65D ? GeodeGrade.ROUGH : roll < 0.95D ? GeodeGrade.RESONANT : GeodeGrade.LUMINOUS;
        }
        return roll < 0.90D ? GeodeGrade.ROUGH : roll < 0.995D ? GeodeGrade.RESONANT : GeodeGrade.LUMINOUS;
    }

    private GemType randomGem(GeodeGrade grade) {
        if (GemRolls.prismatic(grade, ThreadLocalRandom.current().nextDouble())) {
            return GemType.PRISMATIC;
        }
        GemType[] values = Arrays.stream(GemType.values()).filter(type -> !type.prismatic()).toArray(GemType[]::new);
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private ArcaneEnchantment randomEnchantment() {
        ArcaneEnchantment[] values = net.nerdsmp.paper.forge.arcane.ArcaneForge.obtainable(plugin);
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private Material produce(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    private ItemStack baseItem(
        Material material,
        String name,
        List<String> lore,
        String model,
        boolean glint
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(miniMessage.deserialize(name));
        meta.displayName(null);
        meta.lore(lore.stream().map(miniMessage::deserialize).toList());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(glint);
        NamespacedKey modelKey = NamespacedKey.fromString(model);
        if (modelKey != null && net.nerdsmp.paper.item.VanillaTextures.customModels(plugin)) {
            meta.setItemModel(modelKey);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String kind(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return "";
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        return data.has(forgeKindKey, PersistentDataType.STRING)
            ? Optional.ofNullable(data.get(forgeKindKey, PersistentDataType.STRING)).orElse("") : "";
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private double chance(String path, double fallback) {
        return Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble(path, fallback)));
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

    public List<ArcaneEnchantment> enchantments() {
        return Arrays.asList(ArcaneEnchantment.values());
    }

    public enum ApplyResult {
        APPLIED,
        NO_CATALYST,
        NOT_GEAR,
        NO_CAPACITY,
        INCOMPATIBLE,
        UNIDENTIFIED,
        CORRUPTED,
        ALREADY_PRESENT,
        INVALID_SOCKET_DATA
    }

    public enum MergeResult {
        MERGED,
        NEED_TWO_GEMS,
        DIFFERENT_TYPE,
        DIFFERENT_TIER,
        MAXIMUM_TIER,
        INVENTORY_FULL,
        INVALID_SESSION,
        INPUT_CHANGED
    }

    public enum QualityResult {
        APPLIED,
        NOT_GEAR,
        NOT_CRAFTED,
        UNIDENTIFIED,
        MAXIMUM_QUALITY,
        SKILL_TOO_LOW,
        WRONG_MATERIAL,
        NOT_ENOUGH_MATERIAL
    }

    public enum UnsocketResult {
        REMOVED,
        NOT_GEAR,
        UNIDENTIFIED,
        NO_GEMS,
        NEED_DUST,
        NOT_ENOUGH_DUST,
        INVALID_SESSION,
        INVALID_SOCKET_DATA,
        SOCKET_CHANGED,
        INVENTORY_FULL
    }

    public record MergePreview(MergeResult result, GemType type, int outputTier) {
        public boolean ready() {
            return result == MergeResult.MERGED;
        }
    }

    public record Gem(GemType type, int tier) {
    }

    public record Tome(ArcaneEnchantment enchantment, int level) {
    }

    public record QualityRequirement(
        GearQuality quality,
        int smithingLevel,
        Material material,
        int amount
    ) {
    }
}
