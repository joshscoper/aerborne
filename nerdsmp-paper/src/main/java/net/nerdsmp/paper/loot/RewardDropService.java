package net.nerdsmp.paper.loot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.forge.GeodeGrade;
import net.nerdsmp.paper.forge.ArcaneForgeService;
import net.nerdsmp.paper.item.AffixRolls;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.mob.CustomMobManager;
import net.nerdsmp.paper.loot.profile.LootProfileDefinition;
import net.nerdsmp.paper.loot.profile.LootProfileRolls;
import net.nerdsmp.paper.loot.profile.LootProfileStore;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;
import java.util.Set;

public final class RewardDropService implements Listener {
    private static final int CONTENT_SCHEMA = 2;
    private static final ItemId COIN_PURSE_ITEM = new ItemId("coin_purse");
    private final NerdSmpPlugin plugin;
    private final CustomMobManager mobs;
    private final ArcaneForgeService forge;
    private final PaperItemService items;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final Map<EntityType, List<DropDefinition>> tables;
    private final Map<EntityType, Map<Material, DropAdjustment>> adjustments;
    private final HostileGearDefinition hostileGear;
    private final CoinPurseDefinition coinPurses;
    private final CustomLootDefinition chestLoot;
    private final LootProfileStore profiles;
    private final NamespacedKey purseValueKey;
    private final NamespacedKey purseIdKey;
    private final Set<String> pendingPursePickups = ConcurrentHashMap.newKeySet();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public RewardDropService(
        NerdSmpPlugin plugin,
        CustomMobManager mobs,
        ArcaneForgeService forge,
        PaperItemService items,
        SessionRegistry sessions,
        EconomyService economy
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.forge = forge;
        this.items = items;
        this.sessions = sessions;
        this.economy = economy;
        purseValueKey = new NamespacedKey(plugin, "coin_purse_value");
        purseIdKey = new NamespacedKey(plugin, "coin_purse_id");
        LoadedContent content = load();
        tables = content.tables;
        adjustments = content.adjustments;
        hostileGear = content.hostileGear;
        coinPurses = content.coinPurses;
        chestLoot = content.chestLoot;
        Set<ItemId> knownItems = Set.copyOf(items.itemIds());
        Set<ItemId> gearItems = knownItems.stream().filter(id -> items.isSocketEquipment(items.create(id)))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try {
            profiles = new LootProfileStore(new File(plugin.getDataFolder(), "mob-loot.yml"), knownItems, gearItems);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load staff-authored mob loot profiles", exception);
        }
    }

    public LootProfileStore profiles() { return profiles; }

    public void refreshItemCatalog() {
        Set<ItemId> known=Set.copyOf(items.itemIds());
        Set<ItemId> gear=known.stream().filter(id->items.isSocketEquipment(items.create(id)))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        profiles.refreshItemCatalog(known,gear);
    }

    public Optional<LootProfileDefinition> profile(LivingEntity entity) {
        String zoneId = plugin.zoneSpawner() == null ? null : plugin.zoneSpawner().zoneId(entity).orElse(null);
        return profiles.resolve(zoneId, mobs.zoneMobId(entity)
            .orElse("vanilla:" + entity.getType().name().toLowerCase(Locale.ROOT)))
            .or(() -> mobs.authoredLootProfile(entity).flatMap(profiles::find));
    }

    /** Also used by the mob manager before it appends its own physical relic reward. */
    public boolean replacesDefaultLoot(LivingEntity entity) {
        return profile(entity).map(value -> value.mode() == LootProfileDefinition.Mode.REPLACE).orElse(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (mobs.isMechanicObjective(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        LootProfileDefinition authored = profile(event.getEntity()).orElse(null);
        if (authored != null && authored.mode() == LootProfileDefinition.Mode.REPLACE) {
            // Physical drops are replaced, not XP, quest credit, or boss participation awards.
            event.getDrops().clear();
            if (event.getEntity().getKiller() != null) rollProfile(authored).forEach(event.getDrops()::add);
            return;
        }
        applyVanillaAdjustments(event.getEntityType(), event.getDrops());
        boolean hostile = rewardHostile(event.getEntity());
        normalizeEquipment(event.getDrops(), hostile ? 1 : 0, hostile ? 2 : 1, hostile ? 1.0D : 0.12D);
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (authored != null) rollProfile(authored).forEach(event.getDrops()::add);
        double scaledReward = Math.max(1.0D, mobs.rewardMultiplier(event.getEntity()));
        if (hostile) {
            rollHostileGear(event.getEntityType(), scaledReward,
                mobs.isBoss(event.getEntity()), mobs.isElite(event.getEntity()))
                .forEach(event.getDrops()::add);
            rollCoinPurse(scaledReward, mobs.markReward(event.getEntity()),
                mobs.isBoss(event.getEntity()), mobs.isElite(event.getEntity()))
                .ifPresent(event.getDrops()::add);
        }
        List<DropDefinition> definitions = tables.get(event.getEntityType());
        if (definitions == null) {
            return;
        }
        int looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (DropDefinition definition : definitions) {
            double chance = Math.min(1.0D, DropRolls.chance(definition.chance, definition.lootingBonus, looting)
                * Math.min(2.5D, Math.sqrt(scaledReward)));
            if (random.nextDouble() >= chance) {
                continue;
            }
            int amount = DropRolls.amount(definition.minimum, definition.maximum, random.nextDouble());
            ItemStack reward = createReward(definition, amount);
            event.getDrops().add(reward);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoinPurseHopperPickup(InventoryPickupItemEvent event) {
        if (isCoinPurse(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoinPursePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item dropped = event.getItem();
        ItemStack stack = dropped.getItemStack();
        if (!isCoinPurse(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        Long marks = meta.getPersistentDataContainer().get(purseValueKey, PersistentDataType.LONG);
        String purseId = meta.getPersistentDataContainer().get(purseIdKey, PersistentDataType.STRING);
        if (marks == null || marks <= 0L || marks > 10_000L || purseId == null || purseId.isBlank()) {
            return;
        }
        event.setCancelled(true);
        if (!pendingPursePickups.add(purseId)) {
            return;
        }
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            pendingPursePickups.remove(purseId);
            dropped.setPickupDelay(20);
            return;
        }
        dropped.setPickupDelay(Integer.MAX_VALUE);
        economy.credit(account, marks,
            new EconomyService.TransactionReason("loot.coin_purse", "Coin purse pickup"),
            "coin-purse:" + purseId
        ).whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingPursePickups.remove(purseId);
            if (error != null || result == null
                || (result.status() != EconomyService.TransactionResult.Status.APPLIED
                && result.status() != EconomyService.TransactionResult.Status.DUPLICATE)) {
                if (dropped.isValid()) {
                    dropped.setPickupDelay(20);
                }
                if (player.isOnline()) {
                    player.sendRichMessage("<red>That coin purse could not be opened. Try again.</red>");
                }
                return;
            }
            if (dropped.isValid()) {
                dropped.remove();
            }
            if (player.isOnline() && result.status() == EconomyService.TransactionResult.Status.APPLIED) {
                player.sendActionBar(miniMessage.deserialize("<gold>+" + marks + " Marks</gold>"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, 1.35F);
            }
        }));
    }

    private boolean isCoinPurse(ItemStack stack) {
        return items.isAuthentic(stack) && items.describe(stack).map(descriptor -> descriptor.itemId())
            .filter(COIN_PURSE_ITEM::equals).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.isPlugin() || !(event.getInventoryHolder() instanceof org.bukkit.block.Container)) {
            return;
        }
        normalizeEquipment(event.getLoot(), 1, 2, 0.25D);
        rollCustom(chestLoot, 1.0D).ifPresent(event.getLoot()::add);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLooseEquipment(ItemSpawnEvent event) {
        ItemStack original = event.getEntity().getItemStack();
        if (!items.isAuthentic(original) && items.isCraftedEquipment(original.getType())) {
            ItemStack styled = items.createCrafted(original.getType());
            styled.setAmount(original.getAmount());
            event.getEntity().setItemStack(styled);
        }
    }

    public int tableCount() {
        return tables.size();
    }

    private LoadedContent load() {
        File file = new File(plugin.getDataFolder(), "content/drop-tables.yml");
        if (!file.isFile()) {
            plugin.saveResource("content/drop-tables.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("schema", 0) < CONTENT_SCHEMA
            || yaml.getConfigurationSection("hostile-gear") == null
            || yaml.getConfigurationSection("coin-purses") == null) {
            plugin.getLogger().warning("Using bundled drop-table schema " + CONTENT_SCHEMA
                + " because the data-folder copy is older. Merge content/drop-tables.yml before production.");
            try (var reader = new InputStreamReader(
                java.util.Objects.requireNonNull(plugin.getResource("content/drop-tables.yml")),
                StandardCharsets.UTF_8
            )) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load bundled drop tables", exception);
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection("tables");
        if (section == null) {
            throw new IllegalStateException("content/drop-tables.yml is missing tables");
        }
        Map<EntityType, List<DropDefinition>> loaded = new EnumMap<>(EntityType.class);
        for (String tableId : section.getKeys(false)) {
            ConfigurationSection table = section.getConfigurationSection(tableId);
            if (table == null) {
                continue;
            }
            List<DropDefinition> rolls = readRolls(tableId, table.getMapList("rolls"));
            for (String rawType : table.getStringList("entities")) {
                EntityType type;
                try {
                    type = EntityType.valueOf(rawType.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Unknown entity type " + rawType + " in drop table " + tableId, exception);
                }
                loaded.computeIfAbsent(type, ignored -> new ArrayList<>()).addAll(rolls);
            }
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("No reward drop tables were loaded");
        }
        loaded.replaceAll((type, definitions) -> List.copyOf(definitions));
        Map<EntityType, Map<Material, DropAdjustment>> loadedAdjustments = adjustments(yaml);
        HostileGearDefinition loadedHostileGear = hostileGear(yaml);
        CoinPurseDefinition loadedCoinPurses = coinPurses(yaml);
        CustomLootDefinition loadedChestLoot = customLoot(yaml, "custom-loot.chest");
        plugin.getLogger().info("Loaded bonus reward drops for " + loaded.size()
            + " vanilla mob types, " + loadedAdjustments.size() + " adjusted vanilla tables, hostile gear,"
            + " coin purses, and chest gear rolls.");
        return new LoadedContent(Map.copyOf(loaded), Map.copyOf(loadedAdjustments),
            loadedHostileGear, loadedCoinPurses, loadedChestLoot);
    }

    private Map<EntityType, Map<Material, DropAdjustment>> adjustments(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("vanilla-adjustments");
        if (root == null) {
            return Map.of();
        }
        Map<EntityType, Map<Material, DropAdjustment>> loaded = new EnumMap<>(EntityType.class);
        for (String groupId : root.getKeys(false)) {
            ConfigurationSection group = root.getConfigurationSection(groupId);
            ConfigurationSection itemSection = group == null ? null : group.getConfigurationSection("items");
            if (group == null || itemSection == null) {
                throw new IllegalStateException("Invalid vanilla adjustment group " + groupId);
            }
            Map<Material, DropAdjustment> itemRules = new EnumMap<>(Material.class);
            for (String rawMaterial : itemSection.getKeys(false)) {
                Material material = Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
                ConfigurationSection rule = itemSection.getConfigurationSection(rawMaterial);
                if (material == null || !material.isItem() || rule == null) {
                    throw new IllegalStateException("Invalid adjusted material " + rawMaterial + " in " + groupId);
                }
                double keepChance = rule.getDouble("keep-chance", -1.0D);
                int maximum = rule.getInt("maximum", 64);
                if (keepChance < 0.0D || keepChance > 1.0D || maximum < 0) {
                    throw new IllegalStateException("Invalid vanilla adjustment for " + rawMaterial + " in " + groupId);
                }
                itemRules.put(material, new DropAdjustment(keepChance, maximum));
            }
            for (String rawType : group.getStringList("entities")) {
                EntityType type = entityType(rawType, "vanilla adjustment " + groupId);
                Map<Material, DropAdjustment> combined = new EnumMap<>(Material.class);
                combined.putAll(loaded.getOrDefault(type, Map.of()));
                combined.putAll(itemRules);
                loaded.put(type, Map.copyOf(combined));
            }
        }
        return loaded;
    }

    private HostileGearDefinition hostileGear(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("hostile-gear");
        if (root == null) {
            throw new IllegalStateException("content/drop-tables.yml is missing hostile-gear");
        }
        Set<ItemId> known = Set.copyOf(items.itemIds());
        List<ItemId> fallback = itemPool(root.getStringList("default-items"), known, "hostile-gear.default-items");
        Map<LootBand, GearBand> bands = new EnumMap<>(LootBand.class);
        for (LootBand band : LootBand.values()) {
            ConfigurationSection section = root.getConfigurationSection(band.name().toLowerCase(Locale.ROOT));
            if (section == null) {
                throw new IllegalStateException("hostile-gear is missing " + band.name().toLowerCase(Locale.ROOT));
            }
            double chance = section.getDouble("chance", -1.0D);
            int minimumAffixes = section.getInt("minimum-affixes", 1);
            int maximumAffixes = section.getInt("maximum-affixes", minimumAffixes);
            int rolls = section.getInt("rolls", 1);
            if (chance < 0.0D || chance > 1.0D || minimumAffixes < 0 || maximumAffixes < minimumAffixes
                || maximumAffixes > 3 || rolls < 1 || rolls > 3) {
                throw new IllegalStateException("Invalid hostile gear band " + band);
            }
            bands.put(band, new GearBand(chance, minimumAffixes, maximumAffixes, rolls));
        }
        Map<EntityType, List<ItemId>> pools = new EnumMap<>(EntityType.class);
        ConfigurationSection poolRoot = root.getConfigurationSection("pools");
        if (poolRoot == null) {
            throw new IllegalStateException("hostile-gear is missing pools");
        }
        for (String poolId : poolRoot.getKeys(false)) {
            ConfigurationSection pool = poolRoot.getConfigurationSection(poolId);
            if (pool == null) {
                continue;
            }
            List<ItemId> poolItems = itemPool(pool.getStringList("items"), known,
                "hostile-gear.pools." + poolId);
            for (String rawType : pool.getStringList("entities")) {
                EntityType type = entityType(rawType, "hostile gear pool " + poolId);
                List<ItemId> combined = new ArrayList<>(pools.getOrDefault(type, List.of()));
                poolItems.stream().filter(item -> !combined.contains(item)).forEach(combined::add);
                pools.put(type, List.copyOf(combined));
            }
        }
        return new HostileGearDefinition(Map.copyOf(bands), fallback, Map.copyOf(pools));
    }

    private CoinPurseDefinition coinPurses(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("coin-purses");
        if (root == null || !items.itemIds().contains(COIN_PURSE_ITEM)) {
            throw new IllegalStateException("Coin purses require coin-purses settings and the coin_purse item");
        }
        Map<LootBand, PurseBand> bands = new EnumMap<>(LootBand.class);
        for (LootBand band : LootBand.values()) {
            ConfigurationSection section = root.getConfigurationSection(band.name().toLowerCase(Locale.ROOT));
            if (section == null) {
                throw new IllegalStateException("coin-purses is missing " + band.name().toLowerCase(Locale.ROOT));
            }
            double chance = section.getDouble("chance", -1.0D);
            int minimum = section.getInt("min-marks", 0);
            int maximum = section.getInt("max-marks", 0);
            if (chance < 0.0D || chance > 1.0D || minimum < 1 || maximum < minimum) {
                throw new IllegalStateException("Invalid coin purse band " + band);
            }
            bands.put(band, new PurseBand(chance, minimum, maximum));
        }
        return new CoinPurseDefinition(Map.copyOf(bands));
    }

    private List<ItemId> itemPool(List<String> rawItems, Set<ItemId> known, String path) {
        List<ItemId> pool = rawItems.stream().map(ItemId::new).toList();
        if (pool.isEmpty() || pool.stream().anyMatch(item -> !known.contains(item) || item.equals(COIN_PURSE_ITEM))) {
            throw new IllegalStateException("Invalid item pool at " + path);
        }
        return List.copyOf(pool);
    }

    private EntityType entityType(String rawType, String path) {
        try {
            return EntityType.valueOf(rawType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown entity type " + rawType + " in " + path, exception);
        }
    }

    private CustomLootDefinition customLoot(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException("content/drop-tables.yml is missing " + path);
        }
        double chance = section.getDouble("chance", -1.0D);
        double unidentifiedChance = section.getDouble("unidentified-chance", -1.0D);
        int minimumAffixes = section.getInt("minimum-affixes", 1);
        int maximumAffixes = section.getInt("maximum-affixes", minimumAffixes);
        List<ItemId> pool = section.getStringList("items").stream().map(ItemId::new).toList();
        Set<ItemId> known = Set.copyOf(items.itemIds());
        if (chance < 0.0D || chance > 1.0D || unidentifiedChance < 0.0D || unidentifiedChance > 1.0D
            || minimumAffixes < 0 || maximumAffixes < minimumAffixes || maximumAffixes > 3 || pool.isEmpty()
            || pool.stream().anyMatch(item -> !known.contains(item))) {
            throw new IllegalStateException("Invalid custom loot definition at " + path);
        }
        return new CustomLootDefinition(chance, unidentifiedChance, minimumAffixes, maximumAffixes, pool);
    }

    private Optional<ItemStack> rollCustom(CustomLootDefinition definition, double chanceMultiplier) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= Math.min(1.0D, definition.chance * Math.max(0.0D, chanceMultiplier))) {
            return Optional.empty();
        }
        ItemId itemId = definition.items.get(random.nextInt(definition.items.size()));
        int affixes = AffixRolls.count(random.nextDouble(), definition.minimumAffixes, definition.maximumAffixes);
        boolean unidentified = random.nextDouble() < definition.unidentifiedChance;
        return Optional.of(items.createLoot(itemId, affixes, unidentified, random.nextLong()));
    }

    private List<ItemStack> rollHostileGear(
        EntityType type,
        double rewardMultiplier,
        boolean boss,
        boolean elite
    ) {
        LootBand band = boss ? LootBand.BOSS : (elite ? LootBand.ELITE : LootBand.STANDARD);
        GearBand settings = hostileGear.bands.get(band);
        List<ItemId> pool = hostileGear.pools.getOrDefault(type, hostileGear.defaultItems);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> rewards = new ArrayList<>();
        for (int roll = 0; roll < settings.rolls; roll++) {
            if (random.nextDouble() >= DropRolls.scaledChance(settings.chance, rewardMultiplier)) {
                continue;
            }
            ItemId itemId = pool.get(random.nextInt(pool.size()));
            int affixes = AffixRolls.count(random.nextDouble(), settings.minimumAffixes, settings.maximumAffixes);
            rewards.add(items.createLoot(itemId, affixes, true, random.nextLong()));
        }
        return List.copyOf(rewards);
    }

    private Optional<ItemStack> rollCoinPurse(
        double rewardMultiplier,
        long namedMobReward,
        boolean boss,
        boolean elite
    ) {
        LootBand band = boss ? LootBand.BOSS : (elite ? LootBand.ELITE : LootBand.STANDARD);
        PurseBand settings = coinPurses.bands.get(band);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= DropRolls.scaledChance(settings.chance, rewardMultiplier)) {
            return Optional.empty();
        }
        int marks = Math.min(LootProfileDefinition.MAX_MARKS, Math.max((int) Math.min(Integer.MAX_VALUE, namedMobReward),
            DropRolls.scaledAmount(settings.minimumMarks, settings.maximumMarks,
                random.nextDouble(), rewardMultiplier)));
        return Optional.of(createCoinPurse(marks));
    }

    private ItemStack createCoinPurse(long marks) {
        if (marks < 1 || marks > LootProfileDefinition.MAX_MARKS)
            throw new IllegalArgumentException("Coin purses must contain 1..10,000 Marks.");
        ItemStack purse = items.create(COIN_PURSE_ITEM);
        ItemMeta meta = purse.getItemMeta();
        String quality = marks >= 100L ? "Royal" : (marks >= 20L ? "Weighty" : "Worn");
        meta.itemName(miniMessage.deserialize("<gold><bold>" + quality + " Coin Purse</bold></gold>"));
        meta.lore(List.of(
            miniMessage.deserialize("<dark_gray>----------------------------</dark_gray>"),
            miniMessage.deserialize("<yellow><bold>[ MARKS ]</bold></yellow>"),
            miniMessage.deserialize("<gray>Contains <white>" + marks + " Marks</white>.</gray>"),
            miniMessage.deserialize("<dark_gray>Collected automatically when picked up.</dark_gray>")
        ));
        meta.getPersistentDataContainer().set(purseValueKey, PersistentDataType.LONG, marks);
        meta.getPersistentDataContainer().set(purseIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        purse.setItemMeta(meta);
        return purse;
    }

    private List<ItemStack> rollProfile(LootProfileDefinition profile) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> rewards = new ArrayList<>();
        for (var roll : LootProfileRolls.roll(profile, random::nextDouble)) {
            var entry = roll.entry();
            if (entry.kind() == LootProfileDefinition.Kind.MARKS) {
                rewards.add(createCoinPurse(roll.amount()));
                continue;
            }
            ItemId itemId = entry.kind() == LootProfileDefinition.Kind.ITEM ? new ItemId(entry.source()) : null;
            Material material = itemId == null ? Material.valueOf(entry.source()) : null;
            boolean gear = itemId == null ? items.isCraftedEquipment(material) : profiles.gearItems().contains(itemId);
            if (gear) {
                // Each piece has independent rolled affixes and a unique loot-instance ID.
                for (int count = 0; count < roll.amount(); count++)
                    rewards.add(itemId == null ? items.createLoot(material, 0, false, random.nextLong())
                        : items.createLoot(itemId, entry.affixes(), entry.unidentified(), random.nextLong()));
            } else {
                ItemStack reward = itemId == null ? new ItemStack(material) : items.create(itemId);
                int remaining = roll.amount();
                while (remaining > 0) {
                    ItemStack portion = reward.clone();
                    int amount = Math.min(remaining, reward.getMaxStackSize());
                    portion.setAmount(amount); rewards.add(portion); remaining -= amount;
                }
            }
        }
        return List.copyOf(rewards);
    }

    private ItemStack createReward(DropDefinition definition, int amount) {
        ItemStack reward;
        if (definition.itemId != null) {
            reward = items.create(definition.itemId);
        } else if (definition.special == SpecialDrop.ROUGH_GEODE) {
            reward = forge.createGeode(GeodeGrade.ROUGH);
        } else {
            reward = new ItemStack(definition.material);
        }
        reward.setAmount(amount);
        return reward;
    }

    private boolean rewardHostile(LivingEntity entity) {
        return entity instanceof Enemy || mobs.isCustomMob(entity) || mobs.isBoss(entity);
    }

    private void applyVanillaAdjustments(EntityType type, List<ItemStack> loot) {
        Map<Material, DropAdjustment> rules = adjustments.get(type);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        loot.removeIf(item -> {
            // Material-based junk rules must not remove a separately rolled, authenticated reward.
            if (items.isAuthentic(item)) return false;
            DropAdjustment rule = rules.get(item.getType());
            if (rule == null) {
                return false;
            }
            if (rule.maximum == 0 || random.nextDouble() >= rule.keepChance) {
                return true;
            }
            item.setAmount(Math.min(item.getAmount(), rule.maximum));
            return false;
        });
    }

    private void normalizeEquipment(
        List<ItemStack> loot,
        int minimumAffixes,
        int maximumAffixes,
        double unidentifiedChance
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < loot.size(); index++) {
            ItemStack original = loot.get(index);
            if (items.isAuthentic(original) || !items.isCraftedEquipment(original.getType())) {
                continue;
            }
            int affixes = AffixRolls.count(random.nextDouble(), minimumAffixes, maximumAffixes);
            ItemStack styled = items.createLoot(original.getType(), affixes,
                random.nextDouble() < unidentifiedChance, random.nextLong());
            styled.setAmount(original.getAmount());
            loot.set(index, styled);
        }
    }

    private List<DropDefinition> readRolls(String tableId, List<Map<?, ?>> rawRolls) {
        List<DropDefinition> definitions = new ArrayList<>();
        for (Map<?, ?> raw : rawRolls) {
            String rawMaterial = string(raw.get("material"));
            String rawItemId = string(raw.get("item-id"));
            String rawSpecial = string(raw.get("special"));
            SpecialDrop special = SpecialDrop.parse(rawSpecial);
            Material material = rawMaterial.isBlank() ? Material.AIR
                : Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
            ItemId itemId = rawItemId.isBlank() ? null : new ItemId(rawItemId);
            boolean validItem = itemId != null && items.itemIds().contains(itemId);
            int sourceCount = (special == SpecialDrop.NONE ? 0 : 1) + (validItem ? 1 : 0)
                + (material != null && material.isItem() && !material.isAir() ? 1 : 0);
            if (sourceCount != 1) {
                throw new IllegalStateException("Invalid material in drop table " + tableId + ": " + rawMaterial);
            }
            double chance = decimal(raw.get("chance"), -1.0D);
            double lootingBonus = decimal(raw.get("looting-bonus"), 0.0D);
            int minimum = integer(raw.get("min"), 1);
            int maximum = integer(raw.get("max"), minimum);
            if (chance < 0.0D || chance > 1.0D || lootingBonus < 0.0D || minimum < 1 || maximum < minimum) {
                throw new IllegalStateException("Invalid roll values in drop table " + tableId);
            }
            definitions.add(new DropDefinition(material, itemId, special, chance, lootingBonus, minimum, maximum));
        }
        if (definitions.isEmpty()) {
            throw new IllegalStateException("Drop table " + tableId + " has no rolls");
        }
        return List.copyOf(definitions);
    }

    private String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private double decimal(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private enum SpecialDrop {
        NONE,
        ROUGH_GEODE;

        private static SpecialDrop parse(String raw) {
            if (raw.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unknown special drop: " + raw, exception);
            }
        }
    }

    private record DropDefinition(
        Material material,
        ItemId itemId,
        SpecialDrop special,
        double chance,
        double lootingBonus,
        int minimum,
        int maximum
    ) {
    }

    private enum LootBand {
        STANDARD,
        ELITE,
        BOSS
    }

    private record GearBand(double chance, int minimumAffixes, int maximumAffixes, int rolls) {
    }

    private record HostileGearDefinition(
        Map<LootBand, GearBand> bands,
        List<ItemId> defaultItems,
        Map<EntityType, List<ItemId>> pools
    ) {
    }

    private record PurseBand(double chance, int minimumMarks, int maximumMarks) {
    }

    private record CoinPurseDefinition(Map<LootBand, PurseBand> bands) {
    }

    private record DropAdjustment(double keepChance, int maximum) {
    }

    private record CustomLootDefinition(
        double chance,
        double unidentifiedChance,
        int minimumAffixes,
        int maximumAffixes,
        List<ItemId> items
    ) {
        private CustomLootDefinition {
            items = List.copyOf(items);
        }
    }

    private record LoadedContent(
        Map<EntityType, List<DropDefinition>> tables,
        Map<EntityType, Map<Material, DropAdjustment>> adjustments,
        HostileGearDefinition hostileGear,
        CoinPurseDefinition coinPurses,
        CustomLootDefinition chestLoot
    ) {
    }
}
