package net.nerdsmp.paper.item;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.ItemService;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.arcane.ArcaneEnchantmentCodec;
import net.nerdsmp.paper.forge.arcane.ArcaneSignature;
import net.nerdsmp.paper.forge.arcane.ArcaneAspect;
import net.nerdsmp.paper.forge.arcane.ArcaneCorruption;
import net.nerdsmp.paper.forge.arcane.ArcaneItemEffects;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.engine.ItemBlueprint;
import net.nerdsmp.paper.engine.ItemSetBlueprint;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

public final class PaperItemService implements ItemService<ItemStack> {
    private static final int SCHEMA_VERSION = 5;
    private static final int CONTENT_SCHEMA_VERSION = 13;
    /**
     * Bump when the tooltip layout changes. Each catalog item carries a stamp of this and its catalog
     * entry, and the item updater re-renders any item whose stamp no longer matches.
     */
    private static final int RENDER_VERSION = 1;
    /** What one extra item level adds to a dropped item's base stats. */
    public static final double LEVEL_BONUS_STEP = .01D;
    private final JavaPlugin plugin;
    /** A blank line between tooltip sections, compared by identity. */
    private static final Component SECTION_BREAK = Component.empty();
    private static final java.util.Set<String> OFFENSIVE_STATS = java.util.Set.of(
        "damage", "projectile-damage", "damage-flat", "damage-percent", "projectile-damage-percent",
        "execute-damage-percent", "life-steal-percent", "critical-chance-percent", "critical-damage-percent",
        "bleed-percent", "poison-potency-percent"
    );
    private static final List<String> STAT_ORDER = List.of(
        "damage", "projectile-damage", "defense", "damage-flat", "damage-percent",
        "projectile-damage-percent", "execute-damage-percent", "life-steal-percent", "mining-power",
        "gathering-power", "fishing-power", "excavation-power", "skill-xp-percent", "job-marks-percent",
        "durability-save-percent", "poison-potency-percent", "harvest-extra-percent", "geode-find-percent",
        "fishing-luck-percent", "treasure-find-percent", "excavation-find-percent",
        "bleed-percent", "vein-size", "chain-harvest"
    );

    private final NamespacedKey itemIdKey;
    private final NamespacedKey schemaKey;
    private final NamespacedKey originKey;
    private final NamespacedKey socketCapacityKey;
    private final NamespacedKey socketsKey;
    private final NamespacedKey qualityKey;
    private final NamespacedKey levelBonusKey;
    private final NamespacedKey upgradeKey;
    private final NamespacedKey enchantmentsKey;
    private final NamespacedKey coatingTypeKey;
    private final NamespacedKey coatingPotencyKey;
    private final NamespacedKey coatingChargesKey;
    private final NamespacedKey affixesKey;
    private final NamespacedKey unidentifiedKey;
    private final NamespacedKey lootInstanceKey;
    private final NamespacedKey signatureAffixKey;
    private final NamespacedKey arcaneSignatureKey;
    private final NamespacedKey arcaneInstabilityKey;
    private final NamespacedKey arcaneCorruptionKey;
    private final NamespacedKey arcaneAttunementKey;
    private final NamespacedKey renderKey;
    private final Map<ItemId, Definition> baseDefinitions;
    private final Map<String, SetDefinition> baseSets;
    private final Map<ItemId, ExpansionEntry> expansionEntries;
    private volatile Content catalog;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PaperItemService(JavaPlugin plugin) {
        this.plugin=plugin;
        itemIdKey = new NamespacedKey(plugin, "item_id");
        schemaKey = new NamespacedKey(plugin, "item_schema");
        renderKey = new NamespacedKey(plugin, "item_render");
        originKey = new NamespacedKey(plugin, "item_origin");
        socketCapacityKey = new NamespacedKey(plugin, "socket_capacity");
        socketsKey = new NamespacedKey(plugin, "sockets");
        qualityKey = new NamespacedKey(plugin, "gear_quality");
        levelBonusKey = new NamespacedKey(plugin, "item_level_bonus");
        upgradeKey = new NamespacedKey(plugin, "upgrade_note");
        enchantmentsKey = new NamespacedKey(plugin, "custom_enchantments");
        coatingTypeKey = new NamespacedKey(plugin, "poison_coating");
        coatingPotencyKey = new NamespacedKey(plugin, "poison_potency");
        coatingChargesKey = new NamespacedKey(plugin, "poison_charges");
        affixesKey = new NamespacedKey(plugin, "loot_affixes");
        unidentifiedKey = new NamespacedKey(plugin, "unidentified");
        lootInstanceKey = new NamespacedKey(plugin, "loot_instance");
        signatureAffixKey = new NamespacedKey(plugin, "signature_affix");
        arcaneSignatureKey = new NamespacedKey(plugin, "arcane_signature");
        arcaneInstabilityKey = new NamespacedKey(plugin, "arcane_instability");
        arcaneCorruptionKey = new NamespacedKey(plugin, "arcane_corruption");
        arcaneAttunementKey = new NamespacedKey(plugin, "arcane_attunement");
        LoadedCatalog loaded = loadContent(plugin);
        Content content = loaded.content();
        expansionEntries = loaded.entries();
        baseDefinitions = withCraftedEquipment(content.items());
        baseSets = content.sets();
        catalog = new Content(baseDefinitions, baseSets);
    }

    @Override
    public Optional<ItemDescriptor> describe(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        var identity = meta.getPersistentDataContainer();
        if (!identity.has(itemIdKey, PersistentDataType.STRING)
            || !identity.has(schemaKey, PersistentDataType.INTEGER)
            || !identity.has(originKey, PersistentDataType.STRING)) return Optional.empty();
        String rawId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        Integer schema = meta.getPersistentDataContainer().get(schemaKey, PersistentDataType.INTEGER);
        String origin = meta.getPersistentDataContainer().get(originKey, PersistentDataType.STRING);
        if (rawId == null || schema == null || origin == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ItemDescriptor(new ItemId(rawId), schema, origin));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public ItemStack create(ItemId itemId) {
        Definition definition = catalog.items().get(itemId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown NerdSMP item: " + itemId);
        }
        ItemStack item = new ItemStack(definition.material());
        applyPresentation(item, itemId, definition, "nerdsmp");
        return item;
    }

    public ItemStack createLoot(ItemId itemId, int affixCount, boolean unidentified, long seed) {
        return createLoot(itemId, affixCount, unidentified, seed, 0);
    }

    /** Loot above its catalog level: each extra item level adds {@link #LEVEL_BONUS_STEP} to its base stats. */
    public ItemStack createLoot(ItemId itemId, int affixCount, boolean unidentified, long seed, int levelBonus) {
        Definition definition = catalog.items().get(itemId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown NerdSMP item: " + itemId);
        }
        ItemStack item = create(itemId);
        Random random = new Random(seed);
        List<ItemAffix> eligible = new ArrayList<>(java.util.Arrays.stream(ItemAffix.values())
            .filter(affix -> affix.supports(definition.stats())).toList());
        Collections.shuffle(eligible, random);
        List<AffixRoll> rolled = new ArrayList<>();
        for (int index = 0; index < Math.min(Math.max(0, affixCount), eligible.size()); index++) {
            ItemAffix affix = eligible.get(index);
            rolled.add(new AffixRoll(affix, affix.roll(random.nextDouble())));
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(affixesKey, PersistentDataType.STRING, encodeAffixes(rolled));
        meta.getPersistentDataContainer().set(unidentifiedKey, PersistentDataType.BYTE, unidentified ? (byte) 1 : (byte) 0);
        meta.getPersistentDataContainer().set(lootInstanceKey, PersistentDataType.STRING,
            new UUID(random.nextLong(), random.nextLong()).toString());
        if (levelBonus > 0 && definition.itemLevel() > 0) meta.getPersistentDataContainer().set(levelBonusKey, PersistentDataType.INTEGER, levelBonus);
        item.setItemMeta(meta);
        refresh(item);
        return item;
    }

    public ItemStack createLoot(Material material, int affixCount, boolean unidentified, long seed) {
        if (!isCraftedEquipment(material)) {
            throw new IllegalArgumentException("Material is not supported equipment: " + material);
        }
        return createLoot(craftedItemId(material), affixCount, unidentified, seed);
    }

    @Override
    public boolean isAuthentic(ItemStack item) {
        Optional<ItemDescriptor> descriptor = describe(item);
        return descriptor.isPresent()
            && descriptor.get().schemaVersion() == SCHEMA_VERSION
            && descriptor.get().origin().equals("nerdsmp")
            && catalog.items().containsKey(descriptor.get().itemId())
            && catalog.items().get(descriptor.get().itemId()).material() == item.getType()
            && validSignatureTag(item.getItemMeta(), catalog.items().get(descriptor.get().itemId()));
    }

    /** Signatures are resolved from the authenticated catalog, never a name, lore line or stored bonus value. */
    public Optional<SignatureAffix> signatureAffix(ItemStack item) {
        if (!isAuthentic(item) || item.getAmount() != 1 || isUnidentified(item)) return Optional.empty();
        Definition definition = catalog.items().get(describe(item).orElseThrow().itemId());
        SignatureAffix signature = definition.signatureAffix();
        return signature != null && signature.supports(item.getType(), definition.slot(), definition.rarity())
            ? Optional.of(signature) : Optional.empty();
    }

    private boolean validSignatureTag(ItemMeta meta, Definition definition) {
        var data = meta.getPersistentDataContainer();
        if (!data.has(signatureAffixKey)) return true; // Existing authentic items can be refreshed after a catalog update.
        if (!data.has(signatureAffixKey, PersistentDataType.STRING)) return false;
        return definition.signatureAffix() != null && definition.signatureAffix().id()
            .equals(data.get(signatureAffixKey, PersistentDataType.STRING));
    }

    public boolean refresh(ItemStack item) {
        Optional<ItemDescriptor> descriptor = describe(item);
        if (descriptor.isEmpty() || !descriptor.get().origin().equals("nerdsmp")) {
            return false;
        }
        Definition definition = catalog.items().get(descriptor.get().itemId());
        if (definition == null) {
            return false;
        }
        applyPresentation(item, descriptor.get().itemId(), definition, descriptor.get().origin());
        return true;
    }

    /**
     * Whether a catalog item was rendered by an older layout, an older catalog entry or an older item
     * schema, and so needs {@link #upgrade}.
     */
    public boolean needsUpdate(ItemStack item) {
        Optional<ItemDescriptor> descriptor = describe(item);
        if (descriptor.isEmpty() || !descriptor.get().origin().equals("nerdsmp")) return false;
        Definition definition = catalog.items().get(descriptor.get().itemId());
        if (definition == null) return false;
        Integer stamp = item.getItemMeta().getPersistentDataContainer().get(renderKey, PersistentDataType.INTEGER);
        return stamp == null || stamp != renderStamp(definition) || descriptor.get().schemaVersion() != SCHEMA_VERSION
            || item.getType() != definition.material();
    }

    /**
     * The current form of an out-of-date catalog item: today's base item, name and tooltip, keeping its
     * rolls, sockets, quality, enchantments and every other stored property. Empty when it is current.
     */
    public Optional<ItemStack> upgrade(ItemStack item) {
        if (!needsUpdate(item)) return Optional.empty();
        Definition definition = catalog.items().get(describe(item).orElseThrow().itemId());
        ItemStack current = item.getType() == definition.material() ? item.clone() : item.withType(definition.material());
        refresh(current);
        return Optional.of(current);
    }

    /** A stable fingerprint of what an item's presentation is built from. */
    private int renderStamp(Definition definition) {
        StringBuilder key = new StringBuilder().append(RENDER_VERSION)
            .append('|').append(definition.material().name()).append('|').append(definition.name())
            .append('|').append(definition.setId()).append('|').append(definition.slot().name())
            .append('|').append(definition.category()).append('|').append(definition.rarity())
            .append('|').append(definition.model()).append('|').append(definition.sockets())
            .append('|').append(definition.lore()).append('|').append(new TreeMap<>(definition.stats()))
            .append('|').append(definition.itemLevel()).append('|').append(VanillaTextures.customModels(plugin));
        SignatureAffix signature = definition.signatureAffix();
        if (signature != null) key.append('|').append(signature.name()).append(signature.displayName())
            .append(signature.description()).append(new TreeMap<>(signature.stats()));
        SetDefinition set = definition.setId() == null ? null : catalog.sets().get(definition.setId());
        if (set != null) {
            key.append('|').append(set.name()).append(set.pieceCount());
            set.bonuses().forEach((pieces, bonus) -> key.append(pieces).append(bonus.description()));
        }
        return key.toString().hashCode();
    }

    public Optional<GearDescriptor> gear(ItemStack item) {
        if (!isAuthentic(item) || isUnidentified(item)) {
            return Optional.empty();
        }
        ItemId itemId = describe(item).orElseThrow().itemId();
        Definition definition = catalog.items().get(itemId);
        if (definition.signatureAffix() != null && item.getAmount() != 1) return Optional.empty();
        Map<String, Double> combinedStats = combinedStats(definition, item.getItemMeta());
        return Optional.of(new GearDescriptor(
            itemId,
            definition.setId(),
            definition.slot(),
            definition.rarity(),
            combinedStats
        ));
    }

    /** Each socket's type, in order, for a piece of catalog gear (empty for anything else). */
    public List<SocketRules.SlotType> slotTypes(ItemStack item) {
        Optional<ItemDescriptor> descriptor = describe(item);
        Definition definition = descriptor.map(found -> catalog.items().get(found.itemId())).orElse(null);
        if (definition == null) return List.of();
        return slotTypes(definition, socketCapacity(item));
    }

    private static List<SocketRules.SlotType> slotTypes(Definition definition, int capacity) {
        boolean armour = EquipmentMaterials.isArmor(definition.material()) || definition.material() == Material.SHIELD;
        List<SocketRules.SlotType> types = new ArrayList<>();
        for (int index = 0; index < capacity; index++)
            types.add(SocketRules.slot(definition.stats(), armour, definition.rarity(), index, capacity));
        return List.copyOf(types);
    }

    public int socketCapacity(ItemStack item) {
        if (!isAuthentic(item)) {
            return 0;
        }
        Definition definition = catalog.items().get(describe(item).orElseThrow().itemId());
        return definition.material() == item.getType() && definition.slot() != GearSlot.NONE
            ? definition.sockets() : 0;
    }

    public List<SocketedGem> sockets(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return List.of();
        }
        return sockets(item.getItemMeta());
    }

    public boolean hasValidSockets(ItemStack item) {
        if (!isAuthentic(item)) return false;
        ItemMeta meta = item.getItemMeta();
        var data = meta.getPersistentDataContainer();
        if (data.has(socketsKey) && !data.has(socketsKey, PersistentDataType.STRING)) return false;
        if (!data.has(socketCapacityKey, PersistentDataType.INTEGER)) return false;
        Integer storedCapacity = data.get(socketCapacityKey, PersistentDataType.INTEGER);
        return storedCapacity != null && storedCapacity == socketCapacity(item)
            && SocketCodec.decode(data.get(socketsKey, PersistentDataType.STRING), socketCapacity(item)).valid();
    }

    public boolean isSocketEquipment(ItemStack item) {
        return isAuthentic(item) && item.getAmount() == 1 && socketCapacity(item) > 0;
    }

    public GearQuality quality(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return GearQuality.WROUGHT;
        }
        Integer tier = item.getItemMeta().getPersistentDataContainer().get(qualityKey, PersistentDataType.INTEGER);
        return GearQuality.fromTier(tier);
    }

    public boolean isQualityUpgradable(ItemStack item) {
        if (!isAuthentic(item) || isUnidentified(item)) {
            return false;
        }
        Definition definition = catalog.items().get(describe(item).orElseThrow().itemId());
        return definition != null && !definition.rarity().equals("STARTER") && definition.slot() != GearSlot.NONE;
    }

    public QualityUpgradeResult improveQuality(ItemStack item) {
        if (!isAuthentic(item) || item.getAmount() != 1) {
            return QualityUpgradeResult.NOT_GEAR;
        }
        if (isUnidentified(item)) {
            return QualityUpgradeResult.UNIDENTIFIED;
        }
        if (!isQualityUpgradable(item)) {
            return QualityUpgradeResult.NOT_CRAFTED;
        }
        Optional<GearQuality> next = quality(item).next();
        if (next.isEmpty()) {
            return QualityUpgradeResult.MAXIMUM_QUALITY;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.INTEGER, next.get().tier());
        item.setItemMeta(meta);
        refresh(item);
        return QualityUpgradeResult.APPLIED;
    }

    public Optional<SocketedGem> unsocketLast(ItemStack item) {
        List<SocketedGem> current = sockets(item);
        return current.isEmpty() ? Optional.empty()
            : unsocket(item, current.size() - 1, current.get(current.size() - 1));
    }

    public Optional<SocketedGem> unsocket(ItemStack item, int index, SocketedGem expected) {
        if (!isSocketEquipment(item) || isUnidentified(item) || !hasValidSockets(item)) {
            return Optional.empty();
        }
        SocketRules.Removal removal = SocketRules.remove(sockets(item), index, expected).orElse(null);
        if (removal == null) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(socketsKey, PersistentDataType.STRING,
            SocketCodec.encode(removal.remaining()));
        item.setItemMeta(meta);
        refresh(item);
        return Optional.of(removal.recovered());
    }

    public Map<ArcaneEnchantment, Integer> enchantments(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Map.of();
        }
        return enchantments(item.getItemMeta());
    }

    public List<AffixRoll> affixes(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return List.of();
        }
        return affixes(item.getItemMeta());
    }

    public boolean isUnidentified(ItemStack item) {
        return item != null && item.hasItemMeta() && isUnidentified(item.getItemMeta());
    }

    /** Internal processing metadata only; sealed items still provide no usable gear stats. */
    public Optional<SealedGear> sealedGear(ItemStack item) {
        try {
            if (!isAuthentic(item) || !isUnidentified(item) || item.getAmount() != 1 || !hasValidSockets(item)) return Optional.empty();
            Definition definition = catalog.items().get(describe(item).orElseThrow().itemId());
            UUID instance = lootInstance(item).orElse(null);
            if (instance == null || definition.slot() == GearSlot.NONE || !LootProcessingRules.DEFAULT_DUST.containsKey(definition.rarity()))
                return Optional.empty();
            List<AffixRoll> affixes = affixes(item);
            String raw = item.getItemMeta().getPersistentDataContainer().get(affixesKey, PersistentDataType.STRING);
            if (raw == null || (raw.isEmpty() ? 0 : raw.split("\\|", -1).length) != affixes.size()
                || affixes.stream().map(AffixRoll::type).distinct().count() != affixes.size()
                || affixes.stream().anyMatch(roll -> !Double.isFinite(roll.value()) || !roll.type().supports(definition.stats())
                    || roll.value() < roll.type().roll(0) || roll.value() > roll.type().roll(1))) return Optional.empty();
            return Optional.of(new SealedGear(instance, definition.rarity(), affixes.size()));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    public record SealedGear(UUID instance, String rarity, int affixes) { }

    public Optional<UUID> lootInstance(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(lootInstanceKey, PersistentDataType.STRING);
        try {
            return raw == null ? Optional.empty() : Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean identify(ItemStack item) {
        if (!isAuthentic(item) || !isUnidentified(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(unidentifiedKey, PersistentDataType.BYTE, (byte) 0);
        item.setItemMeta(meta);
        return refresh(item);
    }

    public int enchantmentLevel(ItemStack item, ArcaneEnchantment enchantment) {
        return enchantments(item).getOrDefault(enchantment, 0);
    }

    public boolean supportsEnchantment(ItemStack item, ArcaneEnchantment enchantment) {
        Optional<GearDescriptor> gear = gear(item);
        if (gear.isEmpty()) {
            return false;
        }
        if (enchantment.supports(item)) {
            return true;
        }
        GearDescriptor descriptor = gear.get();
        return switch (enchantment) {
            case KEEN_EDGE, SOULSIPHON, EXECUTIONER -> descriptor.stat("damage") > 0.0D;
            case WARDING -> descriptor.stat("defense") > 0.0D;
            case SCHOLAR -> descriptor.slot() != GearSlot.NONE;
            case PROSPECTOR -> descriptor.stat("mining-power") > 0.0D;
            case PRESERVATION -> item.getType().getMaxDurability() > 0;
            case REAPING -> descriptor.stat("gathering-power") > 0.0D;
            case ANGLER -> descriptor.stat("fishing-power") > 0.0D;
            case ARCHAEOLOGIST -> descriptor.stat("excavation-power") > 0.0D;
            case MARKSMAN -> descriptor.stat("projectile-damage") > 0.0D;
            case VENOMBOUND -> descriptor.stat("damage") > 0.0D
                || descriptor.stat("projectile-damage") > 0.0D;
            case BLOODLETTER -> descriptor.stat("damage") > 0.0D;
            case VEINSEEKER -> descriptor.stat("mining-power") > 0.0D;
            case CHAIN_HARVEST -> descriptor.stat("gathering-power") > 0.0D;
        };
    }

    public UpgradeResult socket(ItemStack item, GemType type, int tier) {
        if (!isSocketEquipment(item)) {
            return UpgradeResult.NOT_GEAR;
        }
        if (isUnidentified(item)) {
            return UpgradeResult.UNIDENTIFIED;
        }
        if (!hasValidSockets(item)) return UpgradeResult.INVALID_SOCKET_DATA;
        if (type == null || !SocketRules.validTier(tier)) return UpgradeResult.INCOMPATIBLE;
        List<SocketedGem> current = new ArrayList<>(sockets(item));
        if (current.size() >= socketCapacity(item)) {
            return UpgradeResult.NO_CAPACITY;
        }
        // The next socket's type decides which gems it takes.
        List<SocketRules.SlotType> types = slotTypes(item);
        if (current.size() < types.size() && !SocketRules.fits(type.category(), types.get(current.size()))) return UpgradeResult.WRONG_SOCKET;
        current.add(new SocketedGem(type, tier));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(socketsKey, PersistentDataType.STRING, SocketCodec.encode(current));
        item.setItemMeta(meta);
        refresh(item);
        return UpgradeResult.APPLIED;
    }

    public UpgradeResult enchant(ItemStack item, ArcaneEnchantment enchantment, int rawLevel) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item)) {
            return UpgradeResult.NOT_GEAR;
        }
        if (isUnidentified(item)) {
            return UpgradeResult.UNIDENTIFIED;
        }
        if (!supportsEnchantment(item, enchantment)) {
            return UpgradeResult.INCOMPATIBLE;
        }
        int level = Math.max(1, Math.min(enchantment.maximumLevel(), rawLevel));
        ItemMeta meta = item.getItemMeta();
        // Lossless: entries this build cannot read (newer builds, retired ids) are written back unchanged.
        var stored = ArcaneEnchantmentCodec.decode(
            meta.getPersistentDataContainer().get(enchantmentsKey, PersistentDataType.STRING));
        Map<ArcaneEnchantment, Integer> current = new HashMap<>(stored.known());
        if (current.getOrDefault(enchantment, 0) >= level) {
            return UpgradeResult.ALREADY_PRESENT;
        }
        current.put(enchantment, level);
        meta.getPersistentDataContainer().set(
            enchantmentsKey,
            PersistentDataType.STRING,
            ArcaneEnchantmentCodec.encode(current, stored.preserved())
        );
        item.setItemMeta(meta);
        refresh(item);
        return UpgradeResult.APPLIED;
    }

    /** Raw entries in {@code custom_enchantments} that this build cannot read; kept, never deleted. */
    public List<String> preservedEnchantmentEntries(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return List.of();
        return ArcaneEnchantmentCodec.decode(item.getItemMeta().getPersistentDataContainer()
            .get(enchantmentsKey, PersistentDataType.STRING)).preserved();
    }

    /** The stored Arcane Signature id, even one this build does not recognise. */
    public Optional<String> arcaneSignatureId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        var data = item.getItemMeta().getPersistentDataContainer();
        return data.has(arcaneSignatureKey, PersistentDataType.STRING)
            ? Optional.ofNullable(data.get(arcaneSignatureKey, PersistentDataType.STRING)) : Optional.empty();
    }

    /** The active Arcane Signature on one identified, authentic piece of gear. */
    public Optional<ArcaneSignature> arcaneSignature(ItemStack item) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item) || isUnidentified(item)) return Optional.empty();
        return arcaneSignatureId(item).flatMap(ArcaneSignature::fromId).filter(signature -> signature.supports(item.getType()));
    }

    public int arcaneInstability(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(arcaneInstabilityKey, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    /**
     * Binds (or replaces) the Arcane Signature on one piece of gear. {@code instability} is the
     * overcharge the binding needed; it adds to any instability already on the item.
     */
    public UpgradeResult bindArcaneSignature(ItemStack item, ArcaneSignature signature, int instability) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item)) return UpgradeResult.NOT_GEAR;
        if (isUnidentified(item)) return UpgradeResult.UNIDENTIFIED;
        if (signature == null || !signature.supports(item.getType()) || gear(item).isEmpty()) return UpgradeResult.INCOMPATIBLE;
        if (arcaneSignatureId(item).filter(signature.id()::equals).isPresent()) return UpgradeResult.ALREADY_PRESENT;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(arcaneSignatureKey, PersistentDataType.STRING, signature.id());
        int total = arcaneInstability(item) + Math.max(0, instability);
        if (total > 0) meta.getPersistentDataContainer().set(arcaneInstabilityKey, PersistentDataType.INTEGER, total);
        item.setItemMeta(meta);
        refresh(item);
        return UpgradeResult.APPLIED;
    }

    /** Removes all instability. Enchantments and the signature are untouched. */
    public boolean stabilize(ItemStack item) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item) || arcaneInstability(item) <= 0) return false;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(arcaneInstabilityKey);
        item.setItemMeta(meta);
        refresh(item);
        return true;
    }

    /** The stored corruption id, even one this build does not recognise. */
    public Optional<String> arcaneCorruptionId(ItemStack item) {
        return storedString(item, arcaneCorruptionKey);
    }

    /** The known corruption on one identified, authentic piece of gear, whether or not it is currently active. */
    public Optional<ArcaneCorruption> arcaneCorruption(ItemStack item) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item) || isUnidentified(item)) return Optional.empty();
        return arcaneCorruptionId(item).flatMap(ArcaneCorruption::fromId).filter(corruption -> corruption.supports(item.getType()));
    }

    /** Corrupts one uncorrupted piece of gear with exactly this corruption. */
    public UpgradeResult corrupt(ItemStack item, ArcaneCorruption corruption) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item)) return UpgradeResult.NOT_GEAR;
        if (isUnidentified(item)) return UpgradeResult.UNIDENTIFIED;
        if (corruption == null || !corruption.supports(item.getType()) || gear(item).isEmpty()) return UpgradeResult.INCOMPATIBLE;
        if (arcaneCorruptionId(item).isPresent()) return UpgradeResult.ALREADY_PRESENT;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(arcaneCorruptionKey, PersistentDataType.STRING, corruption.id());
        item.setItemMeta(meta);
        refresh(item);
        return UpgradeResult.APPLIED;
    }

    /** Removes the corruption, including an unrecognised one, and nothing else. */
    public boolean cleanse(ItemStack item) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item) || arcaneCorruptionId(item).isEmpty()) return false;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(arcaneCorruptionKey);
        item.setItemMeta(meta);
        refresh(item);
        return true;
    }

    public Optional<String> arcaneAttunementId(ItemStack item) {
        return storedString(item, arcaneAttunementKey);
    }

    public Optional<ArcaneAspect> arcaneAttunement(ItemStack item) {
        return arcaneAttunementId(item).flatMap(ArcaneAspect::fromId);
    }

    /** Sets (or, with null, clears) the item's attuned Aspect. */
    public UpgradeResult attune(ItemStack item, ArcaneAspect aspect) {
        if (item == null || item.getAmount() != 1 || !isAuthentic(item)) return UpgradeResult.NOT_GEAR;
        if (isUnidentified(item)) return UpgradeResult.UNIDENTIFIED;
        if (gear(item).isEmpty()) return UpgradeResult.INCOMPATIBLE;
        ItemMeta meta = item.getItemMeta();
        if (aspect == null) meta.getPersistentDataContainer().remove(arcaneAttunementKey);
        else meta.getPersistentDataContainer().set(arcaneAttunementKey, PersistentDataType.STRING, aspect.id());
        item.setItemMeta(meta);
        refresh(item);
        return UpgradeResult.APPLIED;
    }

    private Optional<String> storedString(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        var data = item.getItemMeta().getPersistentDataContainer();
        return data.has(key, PersistentDataType.STRING) ? Optional.ofNullable(data.get(key, PersistentDataType.STRING)) : Optional.empty();
    }

    /** What this item would gain its owner, when they've been told it's an upgrade; null otherwise. */
    public String upgradeNote(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : item.getItemMeta().getPersistentDataContainer().get(upgradeKey, PersistentDataType.STRING);
    }

    /** Shows or clears the upgrade mark on this item. */
    public void markUpgrade(ItemStack item, String note) {
        item.editMeta(meta -> {
            if (note == null) meta.getPersistentDataContainer().remove(upgradeKey);
            else meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.STRING, note);
        });
        refresh(item);
    }

    /** This item with no upgrade mark, for anywhere the next person might see it. */
    public ItemStack withoutUpgradeNote(ItemStack item) {
        if (upgradeNote(item) == null) return item;
        ItemStack clean = item.clone();
        markUpgrade(clean, null);
        return clean;
    }

    /** Extra item levels this one item dropped with. */
    private int levelBonus(ItemMeta meta) {
        return Math.clamp(meta.getPersistentDataContainer().getOrDefault(levelBonusKey, PersistentDataType.INTEGER, 0), 0, 100);
    }

    /** The catalog item level, 0 when the item has none. */
    public int itemLevel(ItemStack item) {
        if (!isAuthentic(item)) return 0;
        Definition definition = catalog.items().get(describe(item).orElseThrow().itemId());
        return definition == null ? 0 : Math.max(0, definition.itemLevel()) + levelBonus(item.getItemMeta());
    }

    public boolean coat(ItemStack item, String type, int potency, int charges) {
        Optional<GearDescriptor> gear = gear(item);
        if (gear.isEmpty() || gear.get().stats().getOrDefault("damage", 0.0D) <= 0.0D) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(coatingTypeKey, PersistentDataType.STRING,
            type.toLowerCase(Locale.ROOT));
        meta.getPersistentDataContainer().set(coatingPotencyKey, PersistentDataType.INTEGER,
            Math.max(1, Math.min(3, potency)));
        meta.getPersistentDataContainer().set(coatingChargesKey, PersistentDataType.INTEGER,
            Math.max(1, charges));
        item.setItemMeta(meta);
        refresh(item);
        return true;
    }

    public Optional<Coating> coating(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        String type = meta.getPersistentDataContainer().get(coatingTypeKey, PersistentDataType.STRING);
        Integer potency = meta.getPersistentDataContainer().get(coatingPotencyKey, PersistentDataType.INTEGER);
        Integer charges = meta.getPersistentDataContainer().get(coatingChargesKey, PersistentDataType.INTEGER);
        return type == null || potency == null || charges == null || charges <= 0
            ? Optional.empty() : Optional.of(new Coating(type, Math.max(1, Math.min(3, potency)), charges));
    }

    public Optional<Coating> consumeCoating(ItemStack item) {
        Optional<Coating> current = coating(item);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        int remaining = current.get().charges() - 1;
        if (remaining <= 0) {
            meta.getPersistentDataContainer().remove(coatingTypeKey);
            meta.getPersistentDataContainer().remove(coatingPotencyKey);
            meta.getPersistentDataContainer().remove(coatingChargesKey);
        } else {
            meta.getPersistentDataContainer().set(coatingChargesKey, PersistentDataType.INTEGER, remaining);
        }
        item.setItemMeta(meta);
        refresh(item);
        return current;
    }

    public Optional<SetDefinition> setDefinition(String setId) {
        return Optional.ofNullable(catalog.sets().get(setId.toLowerCase(Locale.ROOT)));
    }

    public List<SetDefinition> setDefinitions() {
        return catalog.sets().values().stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    }

    /** The authored display name of a NerdSMP item, for guides and menus. */
    public Optional<String> itemName(ItemId itemId) {
        Definition definition = catalog.items().get(itemId);
        return definition == null ? Optional.empty() : Optional.of(definition.name());
    }

    public List<ItemId> itemIds() {
        return catalog.items().keySet().stream().sorted((left, right) -> left.value().compareTo(right.value())).toList();
    }

    public java.util.Set<String> coreItemIds() {
        return baseDefinitions.keySet().stream().map(ItemId::value).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public java.util.Set<String> coreSetIds() { return baseSets.keySet(); }

    /** Acquisition metadata for the separately installed expansion; authored editor content is not included. */
    public Map<ItemId, ExpansionEntry> expansionEntries() { return expansionEntries; }

    /** Publish one complete immutable catalog after the staff file's atomic commit succeeds. */
    public void installEngineCatalog(List<ItemBlueprint> blueprints, List<ItemSetBlueprint> authoredSets) {
        Map<ItemId, Definition> nextItems = new HashMap<>(baseDefinitions);
        Map<String, SetDefinition> nextSets = new HashMap<>(baseSets);
        for (ItemSetBlueprint set : authoredSets) {
            if (nextSets.containsKey(set.id())) throw new IllegalArgumentException("Built-in set IDs are read-only.");
            Map<Integer, SetBonus> bonuses = new TreeMap<>();
            set.bonuses().forEach((threshold, bonus) -> bonuses.put(threshold, new SetBonus(bonus.description(), bonus.stats())));
            nextSets.put(set.id(), new SetDefinition(set.id(), set.name(), set.pieceCount(), bonuses));
        }
        for (ItemBlueprint item : blueprints) {
            ItemId id = new ItemId(item.id());
            if (nextItems.containsKey(id)) throw new IllegalArgumentException("Built-in item IDs are read-only.");
            if (item.setId() != null && !nextSets.containsKey(item.setId())) throw new IllegalArgumentException("Unknown item set.");
            nextItems.put(id, engineDefinition(item));
        }
        catalog = new Content(nextItems, nextSets);
    }

    /** Preview items deliberately lack an authentic identity and cannot contribute gear stats. */
    public ItemStack preview(ItemBlueprint blueprint) {
        ItemStack item = new ItemStack(blueprint.material());
        applyPresentation(item, new ItemId(blueprint.id()), engineDefinition(blueprint), "editor-preview");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(itemIdKey);
        meta.getPersistentDataContainer().remove(schemaKey);
        item.setItemMeta(meta);
        return item;
    }

    public ItemBlueprint copyBlueprint(String newId, ItemId source) {
        Definition definition = catalog.items().get(source);
        if (definition == null) throw new IllegalArgumentException("Unknown source item " + source + ".");
        String rarity = ItemBlueprint.RARITIES.contains(definition.rarity()) ? definition.rarity() : "EPIC";
        var plain = PlainTextComponentSerializer.plainText();
        Map<String, Double> stats = new HashMap<>();
        definition.stats().forEach((id, value) -> { if (net.nerdsmp.paper.engine.ItemStats.allowed().contains(id)) stats.put(id, value); });
        return new ItemBlueprint(newId, plain.serialize(miniMessage.deserialize(definition.name())), definition.material(), rarity,
            definition.slot() == GearSlot.NONE ? 0 : definition.sockets(), Math.max(1, definition.itemLevel()), definition.setId(),
            definition.lore().stream().map(line -> plain.serialize(miniMessage.deserialize(line))).limit(8).toList(), stats);
    }

    private Definition engineDefinition(ItemBlueprint item) {
        String color = switch (item.rarity()) {
            case "UNCOMMON" -> "green"; case "RARE" -> "aqua"; case "EPIC" -> "light_purple";
              case "LEGENDARY", "CRAFTED" -> "gold"; case "MYTHIC" -> "light_purple"; default -> "white";
        };
        return new Definition(item.material(), "<" + color + ">" + miniMessage.escapeTags(item.name()) + "</" + color + ">",
            item.setId(), item.slot(), null, item.rarity(), null, item.sockets(),
            item.lore().stream().map(line -> "<gray>" + miniMessage.escapeTags(line) + "</gray>").toList(), item.stats(), item.itemLevel());
    }

    public List<ItemId> itemIdsForSet(String rawSetId) {
        String setId = rawSetId.toLowerCase(Locale.ROOT);
        return catalog.items().entrySet().stream()
            .filter(entry -> setId.equals(entry.getValue().setId()))
            .map(Map.Entry::getKey)
            .sorted((left, right) -> left.value().compareTo(right.value()))
            .toList();
    }

    public boolean isCraftedEquipment(Material material) {
        return material != null && catalog.items().containsKey(craftedItemId(material));
    }

    public ItemStack createCrafted(Material material) {
        ItemId itemId = craftedItemId(material);
        Definition definition = catalog.items().get(itemId);
        if (definition == null || !itemId.value().startsWith("crafted_")) {
            throw new IllegalArgumentException("Material is not supported crafted equipment: " + material);
        }
        return create(itemId);
    }

    private void applyPresentation(ItemStack item, ItemId itemId, Definition definition, String origin) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(null);
        meta.getPersistentDataContainer().set(socketCapacityKey, PersistentDataType.INTEGER, definition.sockets());
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId.value());
        meta.getPersistentDataContainer().set(schemaKey, PersistentDataType.INTEGER, SCHEMA_VERSION);
        meta.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, origin);
        meta.getPersistentDataContainer().set(renderKey, PersistentDataType.INTEGER, renderStamp(definition));
        if (definition.signatureAffix() == null) meta.getPersistentDataContainer().remove(signatureAffixKey);
        else meta.getPersistentDataContainer().set(signatureAffixKey, PersistentDataType.STRING, definition.signatureAffix().id());
        meta.removeEnchantments();
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        meta.setHideTooltip(false);
        if (isUnidentified(meta)) {
            meta.itemName(tooltipLine("<yellow><bold>Unidentified " + classification(definition)
                + "</bold></yellow>"));
            meta.lore(List.of(
                tooltipLine("<gray>Take this sealed item to an Identifier</gray>"),
                tooltipLine("<gray>to reveal its stats, affixes and sockets.</gray>"),
                SECTION_BREAK,
                tooltipLine("<dark_gray>No bonuses until identified.</dark_gray>"),
                SECTION_BREAK,
                tooltipLine("<yellow><bold>SEALED " + classification(definition).toUpperCase(Locale.ROOT) + "</bold></yellow>")
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.setItemModel(null);
            item.setItemMeta(meta);
            return;
        }
        GearQuality quality = quality(meta);
        String name = colored(rarityColor(definition.rarity()), miniMessage.escapeTags(miniMessage.stripTags(definition.name())));
        boolean upgrade = meta.getPersistentDataContainer().has(upgradeKey);
        meta.itemName(tooltipLine((upgrade ? "<green>▲</green> " : "") + (quality == GearQuality.WROUGHT ? name
            : quality.color() + "<bold>" + quality.displayName() + "</bold> " + name)));
        meta.lore(renderTooltip(definition, meta));
        meta.setEnchantmentGlintOverride(upgrade || !enchantments(meta).isEmpty()
            || meta.getPersistentDataContainer().has(arcaneSignatureKey));
        if (VanillaTextures.customModels(plugin) && definition.model() != null && !definition.model().isBlank()) {
            NamespacedKey model = NamespacedKey.fromString(definition.model());
            if (model != null) {
                meta.setItemModel(model);
            }
        } else {
            meta.setItemModel(null);
        }
        item.setItemMeta(meta);
    }

    private List<Component> renderTooltip(Definition definition, ItemMeta meta) {
        List<Component> tooltip = new ArrayList<>();
        String upgrade = meta.getPersistentDataContainer().get(upgradeKey, PersistentDataType.STRING);
        if (upgrade != null) tooltip.add(tooltipLine("<green><bold>▲ Upgrade</bold></green>" + (upgrade.isEmpty() ? "" : " <green>" + miniMessage.escapeTags(upgrade) + "</green>")));
        List<AffixRoll> rolledAffixes = affixes(meta);
        // WoW-style header: item level, and (Wynncraft) how well the item rolled overall.
        StringBuilder header = new StringBuilder();
        if (definition.itemLevel() > 0) header.append("<yellow>Item Level ").append(definition.itemLevel() + levelBonus(meta)).append("</yellow>");
        if (!rolledAffixes.isEmpty()) {
            int overall = (int) Math.round(rolledAffixes.stream()
                .mapToInt(affix -> affix.type().rollPercent(affix.value())).average().orElse(0.0D));
            if (!header.isEmpty()) header.append(" <dark_gray>|</dark_gray> ");
            header.append("<gray>Roll</gray> ").append(rollTag(overall));
        }
        if (!header.isEmpty()) tooltip.add(tooltipLine(header.toString()));
        GearQuality quality = quality(meta);
        if (quality != GearQuality.WROUGHT) {
            tooltip.add(tooltipLine(colored(quality.color(), quality.displayName() + " Quality")
                + " <gray>+" + formatNumber(quality.bonusPercent()) + "% base stats</gray>"));
        }

        Map<String, Double> presentationStats = new HashMap<>(combinedStats(definition, meta));
        if (presentationStats.containsKey("damage")) {
            presentationStats.put("damage", displayedItemDamage(presentationStats));
            presentationStats.remove("damage-flat");
            presentationStats.remove("damage-percent");
        }
        Map<String, Double> orderedStats = new LinkedHashMap<>();
        STAT_ORDER.forEach(stat -> {
            if (presentationStats.containsKey(stat)) {
                orderedStats.put(stat, presentationStats.get(stat));
            }
        });
        presentationStats.forEach(orderedStats::putIfAbsent);
        if (!orderedStats.isEmpty()) {
            appendDivider(tooltip);
        }
        orderedStats.forEach((stat, value) -> tooltip.add(tooltipLine(statLine(stat, value))));

        // Wynncraft-style identifications: each rolled affix and where it landed in its range.
        if (!rolledAffixes.isEmpty()) {
            appendDivider(tooltip);
            rolledAffixes.forEach(affix -> tooltip.add(tooltipLine("<green>" + affix.type().effect(affix.value())
                + "</green> <dark_gray>" + affix.type().displayName() + "</dark_gray> "
                + rollTag(affix.type().rollPercent(affix.value())))));
        }

        List<SocketedGem> socketed = sockets(meta);
        if (definition.sockets() > 0) {
            List<SocketRules.SlotType> types = slotTypes(definition, definition.sockets());
            appendDivider(tooltip);
            StringBuilder row = new StringBuilder();
            for (int index = 0; index < definition.sockets(); index++) {
                SocketRules.SlotType type = types.get(index);
                if (!row.isEmpty()) row.append(" ");
                row.append(index < socketed.size()
                    ? colored(socketed.get(index).type().color(), "[" + type.symbol() + "]")
                    : "<dark_gray>[" + type.symbol() + "]</dark_gray>");
            }
            tooltip.add(tooltipLine("<gray>Gemstone Slots:</gray> " + row));
            for (int index = 0; index < socketed.size(); index++) {
                SocketedGem socket = socketed.get(index);
                tooltip.add(tooltipLine(colored(socket.type().color(), SocketRules.grade(socket.tier()) + " " + socket.type().displayName())
                    + " <dark_gray>|</dark_gray> <gray>" + socket.type().effect(socket.tier()) + "</gray>"));
            }
            for (int empty = socketed.size(); empty < definition.sockets(); empty++) {
                SocketRules.SlotType type = types.get(empty);
                tooltip.add(tooltipLine("<dark_gray>Empty " + type.title() + " Slot</dark_gray>"));
            }
            if ((meta.getPersistentDataContainer().has(socketsKey)
                && !meta.getPersistentDataContainer().has(socketsKey, PersistentDataType.STRING))
                || !SocketCodec.decode(meta.getPersistentDataContainer().get(socketsKey, PersistentDataType.STRING),
                definition.sockets()).valid()) {
                tooltip.add(tooltipLine("<red>Socket data needs staff review.</red>"));
            }
        }

        // Hypixel-style ability: the signature affix, in gold.
        if (definition.signatureAffix() != null) {
            SignatureAffix signature = definition.signatureAffix();
            appendDivider(tooltip);
            tooltip.add(tooltipLine("<gold>Ability: " + miniMessage.escapeTags(signature.displayName()) + "</gold>"));
            tooltip.add(tooltipLine("<gray>" + miniMessage.escapeTags(signature.description()) + "</gray>"));
        }

        Map<ArcaneEnchantment, Integer> customEnchantments = enchantments(meta);
        if (!customEnchantments.isEmpty()) {
            appendDivider(tooltip);
            customEnchantments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> tooltip.add(tooltipLine("<blue>"
                    + entry.getKey().displayName() + " " + roman(entry.getValue()) + "</blue>"
                    + " <dark_gray>|</dark_gray> <gray>" + entry.getKey().description() + "</gray>")));
        }

        String arcaneSignature = meta.getPersistentDataContainer().get(arcaneSignatureKey, PersistentDataType.STRING);
        if (arcaneSignature != null) {
            appendDivider(tooltip);
            tooltip.add(tooltipLine("<gradient:#ff70f6:#67e8f9><bold>Arcane Signature</bold></gradient>"));
            ArcaneSignature known = ArcaneSignature.fromId(arcaneSignature).orElse(null);
            if (known == null) {
                tooltip.add(tooltipLine("<gray>Dormant signature. A newer build of the server can read it.</gray>"));
            } else {
                tooltip.add(tooltipLine("<light_purple>+ " + known.displayName() + "</light_purple>"));
                tooltip.add(tooltipLine("<gray>" + known.summary() + "</gray>"));
            }
            Integer instability = meta.getPersistentDataContainer().get(arcaneInstabilityKey, PersistentDataType.INTEGER);
            if (instability != null && instability > 0) {
                tooltip.add(tooltipLine("<yellow>Unstable " + instability + "</yellow> <dark_gray>|</dark_gray> "
                    + "<gray>Stabilize at an enchanting table.</gray>"));
            }
        }

        String corruptionId = meta.getPersistentDataContainer().get(arcaneCorruptionKey, PersistentDataType.STRING);
        if (corruptionId != null) {
            appendDivider(tooltip);
            tooltip.add(tooltipLine("<dark_red><bold>Corrupted</bold></dark_red>"));
            ArcaneCorruption corruption = ArcaneCorruption.fromId(corruptionId).orElse(null);
            if (corruption == null) {
                tooltip.add(tooltipLine("<gray>Dormant corruption from a newer build. Cleanse it at an enchanting table.</gray>"));
            } else {
                tooltip.add(tooltipLine("<red>+ " + corruption.displayName() + "</red> <dark_gray>|</dark_gray> <white>" + corruption.bonusText() + "</white>"));
                tooltip.add(tooltipLine("<gray>Wears " + ArcaneItemEffects.durabilityMultiplier() + "x faster. No new Arcane work until cleansed.</gray>"));
                if (!ArcaneItemEffects.corruptionActive()) tooltip.add(tooltipLine("<dark_gray>Dormant: corruption is off on this server.</dark_gray>"));
            }
        }
        ArcaneAspect attunement = ArcaneAspect.fromId(meta.getPersistentDataContainer().get(arcaneAttunementKey, PersistentDataType.STRING)).orElse(null);
        if (attunement != null) {
            appendDivider(tooltip);
            tooltip.add(tooltipLine("<aqua><bold>Attunement</bold></aqua>"));
            tooltip.add(tooltipLine("<gray>Attuned to</gray> " + attunement.colored() + " <dark_gray>|</dark_gray> <gray>"
                + attunement.displayName() + " enchantments here are " + ArcaneItemEffects.attunementBonusPercent() + "% stronger.</gray>"));
            if (!ArcaneItemEffects.attunementActive()) tooltip.add(tooltipLine("<dark_gray>Dormant: attunement is off on this server.</dark_gray>"));
        }

        coating(meta).ifPresent(coating -> {
            appendDivider(tooltip);
            tooltip.add(tooltipLine("<green><bold>Weapon Coating</bold></green>"));
            tooltip.add(tooltipLine("<green>[!] " + titleCase(coating.type()) + " "
                + roman(coating.potency()) + "</green> <dark_gray>|</dark_gray> <gray>"
                + coating.charges() + " strikes left</gray>"));
        });

        if (definition.setId() != null) {
            SetDefinition set = catalog.sets().get(definition.setId());
            if (set != null) {
                appendDivider(tooltip);
                tooltip.add(tooltipLine("<gold>Set: " + miniMessage.escapeTags(set.name())
                    + "</gold> <dark_gray>(" + set.pieceCount() + " pieces)</dark_gray>"));
                set.bonuses().forEach((pieces, bonus) -> tooltip.add(tooltipLine(
                    "<dark_gray>[" + pieces + "]</dark_gray> <gray>" + miniMessage.escapeTags(bonus.description()) + "</gray>"
                )));
            }
        }

        // Flavour text, then the Hypixel-style rarity and type line in the WoW quality colour.
        if (!definition.lore().isEmpty()) {
            appendDivider(tooltip);
            definition.lore().stream().map(this::tooltipLine).forEach(tooltip::add);
        }
        appendDivider(tooltip);
        tooltip.add(tooltipLine(rarityFooter(definition.rarity(), classification(definition))));
        return List.copyOf(tooltip);
    }

    /** Starts a new tooltip section with one blank line (never at the top, never two in a row). */
    private void appendDivider(List<Component> tooltip) {
        if (!tooltip.isEmpty() && tooltip.getLast() != SECTION_BREAK) tooltip.add(SECTION_BREAK);
    }

    /** A Wynncraft roll percentage, coloured from red (a poor roll) to aqua (a near-perfect one). */
    static String rollTag(int percent) {
        int shown = Math.max(0, Math.min(100, percent));
        String color = shown >= 95 ? "aqua" : shown >= 70 ? "green" : shown >= 30 ? "yellow" : "red";
        return "<" + color + ">[" + shown + "%]</" + color + ">";
    }

    /** The WoW quality colour of a rarity, as an opening MiniMessage tag. */
    static String rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toUpperCase(Locale.ROOT)) {
            case "STARTER" -> "<color:#9d9d9d>";
            case "COMMON" -> "<white>";
            case "CRAFTED" -> "<color:#e6cc80>";
            case "CURRENCY" -> "<color:#ffd700>";
            case "UNCOMMON" -> "<color:#1eff00>";
            case "RARE" -> "<color:#0070dd>";
            case "EPIC" -> "<color:#a335ee>";
            case "LEGENDARY" -> "<color:#ff8000>";
            case "MYTHIC" -> "<gradient:#ff4fd8:#ffb347>";
            default -> "<gradient:#9b7bff:#67e8f9>";
        };
    }

    /** The bottom line of a tooltip: rarity and item type, bold, in the rarity's colour. */
    static String rarityFooter(String rarity, String classification) {
        String known = rarity == null || rarity.isBlank() ? "RELIC" : rarity.toUpperCase(Locale.ROOT);
        return colored(rarityColor(rarity), "<bold>" + known + " " + classification.toUpperCase(Locale.ROOT) + "</bold>");
    }

    private Component tooltipLine(String markup) {
        return miniMessage.deserialize(markup).decoration(TextDecoration.ITALIC, false);
    }

    static String colored(String openingTag, String text) {
        String tagName = openingTag.substring(1, openingTag.length() - 1).split(":", 2)[0];
        return openingTag + text + "</" + tagName + ">";
    }

    private List<SocketedGem> sockets(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        if ((data.has(socketsKey) && !data.has(socketsKey, PersistentDataType.STRING))
            || !data.has(socketCapacityKey, PersistentDataType.INTEGER)) return List.of();
        String encoded = meta.getPersistentDataContainer().get(socketsKey, PersistentDataType.STRING);
        Integer capacity = meta.getPersistentDataContainer().get(socketCapacityKey, PersistentDataType.INTEGER);
        return SocketCodec.decode(encoded, capacity == null ? 0 : capacity).sockets();
    }

    private Map<ArcaneEnchantment, Integer> enchantments(ItemMeta meta) {
        // Same result as the original reader (ArcaneEnchantmentCodecTest pins this); unreadable entries are kept, not used.
        return ArcaneEnchantmentCodec.decode(meta.getPersistentDataContainer().get(enchantmentsKey, PersistentDataType.STRING)).known();
    }

    private List<AffixRoll> affixes(ItemMeta meta) {
        String encoded = meta.getPersistentDataContainer().get(affixesKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        List<AffixRoll> result = new ArrayList<>();
        for (String part : encoded.split("\\|")) {
            String[] fields = part.split(":", 2);
            ItemAffix type = fields.length == 2 ? ItemAffix.fromId(fields[0]) : null;
            try {
                double value = fields.length == 2 ? Double.parseDouble(fields[1]) : 0.0D;
                if (type != null && Double.isFinite(value) && value > 0.0D) {
                    result.add(new AffixRoll(type, value));
                }
            } catch (NumberFormatException ignored) {
                // Preserve valid affixes when one legacy entry is malformed.
            }
        }
        return List.copyOf(result);
    }

    private boolean isUnidentified(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        if (data.has(unidentifiedKey) && !data.has(unidentifiedKey, PersistentDataType.BYTE)) return true;
        Byte value = data.get(unidentifiedKey, PersistentDataType.BYTE);
        return value != null && value != (byte) 0;
    }

    private Optional<Coating> coating(ItemMeta meta) {
        String type = meta.getPersistentDataContainer().get(coatingTypeKey, PersistentDataType.STRING);
        Integer potency = meta.getPersistentDataContainer().get(coatingPotencyKey, PersistentDataType.INTEGER);
        Integer charges = meta.getPersistentDataContainer().get(coatingChargesKey, PersistentDataType.INTEGER);
        return type == null || potency == null || charges == null || charges <= 0
            ? Optional.empty() : Optional.of(new Coating(type, Math.max(1, Math.min(3, potency)), charges));
    }

    private String encodeAffixes(List<AffixRoll> affixes) {
        return affixes.stream().map(affix -> affix.type().id() + ":"
            + String.format(Locale.ROOT, "%.1f", affix.value())).reduce((left, right) -> left + "|" + right).orElse("");
    }

    private Map<String, Double> combinedStats(Definition definition, ItemMeta meta) {
        GearQuality quality = quality(meta);
        double levelScale = 1 + levelBonus(meta) * LEVEL_BONUS_STEP;
        Map<String, Double> combined = new HashMap<>();
        definition.stats().forEach((stat, value) -> combined.put(stat,
            quality.scales(stat) ? value * quality.multiplier() * levelScale : value));
        sockets(meta).forEach(socket -> socket.type().stats(socket.tier()).forEach(
            (stat, value) -> combined.merge(stat, value, Double::sum)
        ));
        // Attunement raises only the attuned enchantments' own bonus; corruption adds its listed stats once.
        ArcaneAspect attuned = ArcaneAspect.fromId(meta.getPersistentDataContainer().get(arcaneAttunementKey, PersistentDataType.STRING)).orElse(null);
        enchantments(meta).forEach((enchantment, level) -> enchantment.stats(level).forEach(
            (stat, value) -> combined.merge(stat, ArcaneItemEffects.attuned(enchantment, attuned, value), Double::sum)
        ));
        if (ArcaneItemEffects.corruptionActive()) {
            ArcaneCorruption.fromId(meta.getPersistentDataContainer().get(arcaneCorruptionKey, PersistentDataType.STRING))
                .filter(corruption -> corruption.supports(definition.material()))
                .ifPresent(corruption -> corruption.stats().forEach((stat, value) -> combined.merge(stat, value, Double::sum)));
        }
        affixes(meta).forEach(affix -> combined.merge(affix.type().stat(), affix.value(), Double::sum));
        if (definition.signatureAffix() != null) definition.signatureAffix().stats().forEach(
            (stat, value) -> combined.merge(stat, value, Double::sum));
        return combined;
    }

    private GearQuality quality(ItemMeta meta) {
        return GearQuality.fromTier(meta.getPersistentDataContainer().get(qualityKey, PersistentDataType.INTEGER));
    }

    static double displayedItemDamage(Map<String, Double> stats) {
        return GearMath.calculateDamage(
            stats.getOrDefault("damage", 0.0D),
            stats.getOrDefault("damage-flat", 0.0D),
            stats.getOrDefault("damage-percent", 0.0D),
            1.0D
        );
    }

    private String classification(Definition definition) {
        if (definition.category() != null && !definition.category().isBlank()) {
            return titleCase(definition.category());
        }
        String material = definition.material().name();
        String specific = material.endsWith("_SWORD") ? "Sword"
            : material.endsWith("_PICKAXE") ? "Pickaxe"
            : material.endsWith("_AXE") ? "Axe"
            : material.endsWith("_SHOVEL") ? "Shovel"
            : material.endsWith("_HOE") ? "Hoe"
            : material.endsWith("_HELMET") ? "Helmet"
            : material.endsWith("_CHESTPLATE") ? "Chestplate"
            : material.endsWith("_LEGGINGS") ? "Leggings"
            : material.endsWith("_BOOTS") ? "Boots"
            : switch (definition.material()) {
                case BOW -> "Bow";
                case CROSSBOW -> "Crossbow";
                case TRIDENT -> "Trident";
                case MACE -> "Mace";
                case FISHING_ROD -> "Fishing Rod";
                case SHIELD -> "Shield";
                case ELYTRA -> "Cloak";
                default -> null;
            };
        if (specific != null && definition.slot() != GearSlot.NONE) return specific;
        return switch (definition.slot()) {
            case MAIN_HAND -> definition.stats().containsKey("mining-power")
                || definition.stats().containsKey("gathering-power")
                || definition.stats().containsKey("fishing-power")
                || definition.stats().containsKey("excavation-power") ? "Tool" : "Weapon";
            case HEAD, CHEST, LEGS, FEET -> "Armor";
            case OFF_HAND -> "Focus";
            case NONE -> "Item";
        };
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

    /** A short gain, such as {@code +3 Defense}, for upgrade marks. */
    static String statChange(String stat, double value) {
        return "+" + formatNumber(value) + (stat.endsWith("-percent") ? "% " : " ") + statName(stat);
    }

    private static String statName(String stat) {
        return switch (stat) {
            case "damage" -> "Damage";
            case "defense" -> "Defense";
            case "damage-flat" -> "Bonus Damage";
            case "damage-percent" -> "Damage Bonus";
            case "mining-power" -> "Mining Power";
            case "fishing-power" -> "Fishing Power";
            case "gathering-power" -> "Gathering Power";
            case "projectile-damage" -> "Projectile Damage";
            case "excavation-power" -> "Excavation Power";
            case "skill-xp-percent" -> "Skill XP";
            case "durability-save-percent" -> "Durability Protection";
            case "poison-potency-percent" -> "Poison Potency";
            case "execute-damage-percent" -> "Execute Damage";
            case "life-steal-percent" -> "Life Steal";
            case "critical-chance-percent" -> "Critical Chance";
            case "critical-damage-percent" -> "Critical Damage";
            case "bleed-percent" -> "Bleed";
            case "vein-size" -> "Vein Size";
            case "chain-harvest" -> "Chain Harvest";
            default -> titleCase(stat.endsWith("-percent") ? stat.substring(0, stat.length() - 8) : stat);
        };
    }

    static String statLine(String stat, double value) {
        String label = statName(stat);
        String suffix = stat.endsWith("-percent") ? "%" : "";
        String prefix = value > 0.0D ? "+" : "";
        // Hypixel colours: red for offence, green for everything else.
        String color = OFFENSIVE_STATS.contains(stat) ? "red" : "green";
        return "<gray>" + label + ":</gray> <" + color + ">" + prefix + formatNumber(value) + suffix + "</" + color + ">";
    }

    private static String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static String titleCase(String raw) {
        String[] words = raw.replace('-', ' ').replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String normalized = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(normalized.charAt(0))).append(normalized.substring(1));
        }
        return result.toString();
    }

    private LoadedCatalog loadContent(JavaPlugin plugin) {
        File contentDirectory = new File(plugin.getDataFolder(), "content");
        File target = new File(contentDirectory, "relics.yml");
        YamlConfiguration configuration;
        try (var reader = new InputStreamReader(
            java.util.Objects.requireNonNull(plugin.getResource("content/relics.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration bundled = new YamlConfiguration(); bundled.load(reader);
            configuration = RelicCatalogFiles.load(target, bundled, CONTENT_SCHEMA_VERSION,
                candidate -> loadItems(candidate, loadSets(candidate)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load relic definitions; existing content was preserved", exception);
        }

        try (var stream = java.util.Objects.requireNonNull(plugin.getResource("content/skyforge-items.yml"))) {
            String bundledExpansion = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var expansion = AdditiveItemCatalogFiles.load(new File(contentDirectory, "skyforge-items.yml"),
                bundledExpansion, configuration, candidate -> loadItems(candidate, loadSets(candidate)));
            Map<String, SetDefinition> loadedSets = loadSets(expansion.merged());
            Map<ItemId, Definition> loadedItems = loadItems(expansion.merged(), loadedSets);
            return new LoadedCatalog(new Content(loadedItems, loadedSets), expansion.entries());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the item expansion; existing content was preserved", exception);
        }
    }

    private Map<ItemId, Definition> withCraftedEquipment(Map<ItemId, Definition> loaded) {
        Map<ItemId, Definition> expanded = new HashMap<>(loaded);
        for (Material material : Material.values()) {
            craftedDefinition(material).ifPresent(definition ->
                expanded.putIfAbsent(craftedItemId(material), definition));
        }
        return Map.copyOf(expanded);
    }

    private ItemId craftedItemId(Material material) {
        return new ItemId("crafted_" + material.name().toLowerCase(Locale.ROOT));
    }

    private Optional<Definition> craftedDefinition(Material material) {
        String name = material.name();
        GearSlot slot = EquipmentMaterials.slot(material);
        if (slot == GearSlot.NONE) {
            return Optional.empty();
        }
        Map<String, Double> stats = craftedStats(material, slot);
        if (stats.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Definition(
            material,
            "<white>" + titleCase(name) + "</white>",
            null,
            slot,
            null,
            "CRAFTED",
            null,
            1,
            List.of(),
            stats,
            0
        ));
    }

    private Map<String, Double> craftedStats(Material material, GearSlot slot) {
        String name = material.name();
        Map<String, Double> stats = new HashMap<>();
        double tier = materialTier(name);
        if (name.endsWith("_SWORD")) {
            stats.put("damage", 3.0D + tier);
        } else if (name.endsWith("_AXE")) {
            stats.put("damage", 6.0D + tier);
            stats.put("gathering-power", tier);
        } else if (name.endsWith("_PICKAXE")) {
            stats.put("damage", 1.0D + tier);
            stats.put("mining-power", tier);
        } else if (name.endsWith("_SHOVEL")) {
            stats.put("damage", 1.5D + tier);
            stats.put("excavation-power", tier);
        } else if (name.endsWith("_HOE")) {
            stats.put("damage", Math.max(1.0D, tier));
            stats.put("gathering-power", tier);
        } else if (name.endsWith("_SPEAR")) {
            stats.put("damage", 2.5D + tier);
        } else if (name.equals("BOW") || name.equals("CROSSBOW")) {
            stats.put("projectile-damage", name.equals("BOW") ? 6.0D : 9.0D);
        } else if (name.equals("MACE")) {
            stats.put("damage", 8.0D);
        } else if (name.equals("TRIDENT")) {
            stats.put("damage", 9.0D);
            stats.put("projectile-damage", 8.0D);
        } else if (name.equals("FISHING_ROD")) {
            stats.put("fishing-power", 2.0D);
        } else if (name.equals("BRUSH")) {
            stats.put("excavation-power", 2.0D);
        } else if (name.equals("SHEARS")) {
            stats.put("gathering-power", 2.0D);
        } else if (name.equals("ELYTRA")) {
            stats.put("defense", 1.0D);
        } else if (slot == GearSlot.OFF_HAND) {
            stats.put("defense", 2.0D);
        } else if (slot != GearSlot.MAIN_HAND && slot != GearSlot.NONE) {
            stats.put("defense", armorDefense(name, slot));
        }
        return Map.copyOf(stats);
    }

    private double materialTier(String name) {
        if (name.startsWith("NETHERITE_")) {
            return 5.0D;
        }
        if (name.startsWith("DIAMOND_")) {
            return 4.0D;
        }
        if (name.startsWith("IRON_")) {
            return 3.0D;
        }
        if (name.startsWith("COPPER_") || name.startsWith("STONE_")) {
            return 2.0D;
        }
        return 1.0D;
    }

    private double armorDefense(String name, GearSlot slot) {
        double tier = materialTier(name);
        return switch (slot) {
            case HEAD -> tier >= 4.0D ? 3.0D : Math.max(1.0D, Math.min(2.0D, tier));
            case CHEST -> tier >= 4.0D ? 8.0D : tier >= 3.0D ? 6.0D : tier >= 2.0D ? 5.0D : 3.0D;
            case LEGS -> tier >= 4.0D ? 6.0D : tier >= 3.0D ? 5.0D : tier >= 2.0D ? 4.0D : 2.0D;
            case FEET -> tier >= 4.0D ? 3.0D : tier >= 3.0D ? 2.0D : 1.0D;
            default -> 0.0D;
        };
    }

    private Map<String, SetDefinition> loadSets(YamlConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("sets");
        if (section == null) {
            throw new IllegalStateException("content/relics.yml does not contain a sets section");
        }
        Map<String, SetDefinition> loaded = new HashMap<>();
        for (String rawId : section.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection setSection = section.getConfigurationSection(rawId);
            if (setSection == null) {
                continue;
            }
            Map<Integer, SetBonus> bonuses = new TreeMap<>();
            ConfigurationSection bonusSection = setSection.getConfigurationSection("bonuses");
            if (bonusSection != null) {
                for (String rawPieces : bonusSection.getKeys(false)) {
                    int pieces = Integer.parseInt(rawPieces);
                    ConfigurationSection entry = bonusSection.getConfigurationSection(rawPieces);
                    if (entry == null) {
                        continue;
                    }
                    bonuses.put(pieces, new SetBonus(entry.getString("description", "Set bonus"), stats(entry)));
                }
            }
            loaded.put(id, new SetDefinition(
                id,
                setSection.getString("name", titleCase(id)),
                setSection.getInt("piece-count", bonuses.keySet().stream().mapToInt(Integer::intValue).max().orElse(1)),
                bonuses
            ));
        }
        return loaded;
    }

    private Map<ItemId, Definition> loadItems(YamlConfiguration configuration, Map<String, SetDefinition> loadedSets) {
        ConfigurationSection relics = configuration.getConfigurationSection("relics");
        if (relics == null) {
            throw new IllegalStateException("content/relics.yml does not contain a relics section");
        }
        Map<ItemId, Definition> loaded = new HashMap<>();
        for (String key : relics.getKeys(false)) {
            ConfigurationSection section = relics.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ItemId itemId = new ItemId(key);
            Material material = Material.matchMaterial(section.getString("material", ""));
            if (material == null || material.isAir()) {
                throw new IllegalStateException("Invalid material for NerdSMP item " + key);
            }
            String setId = section.getString("set");
            if (setId != null) {
                setId = setId.toLowerCase(Locale.ROOT);
                if (!loadedSets.containsKey(setId)) {
                    throw new IllegalStateException("Unknown set " + setId + " on NerdSMP item " + key);
                }
            }
            GearSlot slot;
            try {
                slot = GearSlot.valueOf(section.getString("slot", "NONE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid gear slot on NerdSMP item " + key, exception);
            }
            String rarity = section.getString("rarity", "RELIC").toUpperCase(Locale.ROOT);
            int sockets = section.getInt("sockets", rarity.equals("STARTER") ? 1 : 2);
            if (rarity.equals("CRAFTED")) {
                sockets = Math.max(1, sockets);
            }
            SignatureAffix signature = null;
            if (section.contains("unique-affix")) {
                signature = SignatureAffix.fromId(section.getString("unique-affix")).orElseThrow(
                    () -> new IllegalStateException("Unknown signature affix on NerdSMP item " + key));
                if (!signature.supports(material, slot, rarity))
                    throw new IllegalStateException("Incompatible signature affix on NerdSMP item " + key);
            }
            loaded.put(itemId, new Definition(
                material,
                section.getString("name", titleCase(key)),
                setId,
                slot,
                section.getString("category"),
                rarity,
                section.getString("model"),
                sockets,
                new ArrayList<>(section.getStringList("lore")),
                stats(section),
                Math.clamp(section.getInt("item-level", 0), 0, 100),
                signature
            ));
        }
        return loaded;
    }

    private Map<String, Double> stats(ConfigurationSection parent) {
        ConfigurationSection section = parent.getConfigurationSection("stats");
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new HashMap<>();
        for (String key : section.getKeys(false)) {
            stats.put(key.toLowerCase(Locale.ROOT), section.getDouble(key));
        }
        return Map.copyOf(stats);
    }

    public enum GearSlot {
        MAIN_HAND,
        OFF_HAND,
        HEAD,
        CHEST,
        LEGS,
        FEET,
        NONE;

        public boolean matches(EquipmentSlot equipmentSlot) {
            return switch (this) {
                case MAIN_HAND -> equipmentSlot == EquipmentSlot.HAND;
                case OFF_HAND -> equipmentSlot == EquipmentSlot.OFF_HAND;
                case HEAD -> equipmentSlot == EquipmentSlot.HEAD;
                case CHEST -> equipmentSlot == EquipmentSlot.CHEST;
                case LEGS -> equipmentSlot == EquipmentSlot.LEGS;
                case FEET -> equipmentSlot == EquipmentSlot.FEET;
                case NONE -> false;
            };
        }
    }

    public record GearDescriptor(ItemId itemId, String setId, GearSlot slot, String rarity, Map<String, Double> stats) {
        public GearDescriptor {
            stats = Map.copyOf(stats);
        }

        public double stat(String id) {
            return stats.getOrDefault(id, 0.0D);
        }
    }

    public record SocketedGem(GemType type, int tier) {
    }

    public record AffixRoll(ItemAffix type, double value) {
    }

    public record Coating(String type, int potency, int charges) {
    }

    public record ExpansionEntry(String rarity, String acquisition, String setId) { }

    public enum UpgradeResult {
        APPLIED,
        NOT_GEAR,
        NO_CAPACITY,
        WRONG_SOCKET,
        INCOMPATIBLE,
        UNIDENTIFIED,
        ALREADY_PRESENT,
        INVALID_SOCKET_DATA
    }

    public enum QualityUpgradeResult {
        APPLIED,
        NOT_GEAR,
        NOT_CRAFTED,
        UNIDENTIFIED,
        MAXIMUM_QUALITY
    }

    public record SetDefinition(String id, String name, int pieceCount, Map<Integer, SetBonus> bonuses) {
        public SetDefinition {
            bonuses = Collections.unmodifiableMap(new TreeMap<>(bonuses));
        }
    }

    public record SetBonus(String description, Map<String, Double> stats) {
        public SetBonus {
            stats = Map.copyOf(stats);
        }
    }

    private record Definition(
        Material material,
        String name,
        String setId,
        GearSlot slot,
        String category,
        String rarity,
        String model,
        int sockets,
        List<String> lore,
        Map<String, Double> stats,
        int itemLevel,
        SignatureAffix signatureAffix
    ) {
        private Definition(Material material, String name, String setId, GearSlot slot, String category,
                           String rarity, String model, int sockets, List<String> lore, Map<String, Double> stats,
                           int itemLevel) {
            this(material, name, setId, slot, category, rarity, model, sockets, lore, stats, itemLevel, null);
        }
        private Definition {
            sockets = Math.max(0, Math.min(4, sockets));
            lore = List.copyOf(lore);
            stats = Map.copyOf(stats);
            if (signatureAffix != null && !signatureAffix.supports(material, slot, rarity))
                throw new IllegalArgumentException("Incompatible signature affix");
            if (signatureAffix != null) {
                Map<String, Double> combined = new HashMap<>(stats);
                signatureAffix.stats().forEach((stat, value) -> combined.merge(stat, value, Double::sum));
                net.nerdsmp.paper.engine.ItemStats.validate(combined, false);
            }
        }
    }

    private record LoadedCatalog(Content content, Map<ItemId, ExpansionEntry> entries) {
        private LoadedCatalog { entries = Map.copyOf(entries); }
    }

    private record Content(Map<ItemId, Definition> items, Map<String, SetDefinition> sets) {
        private Content { items = Map.copyOf(items); sets = Map.copyOf(sets); }
    }
}
