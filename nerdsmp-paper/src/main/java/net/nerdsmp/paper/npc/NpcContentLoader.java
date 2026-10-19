package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NpcContentLoader {
    private static final int SCHEMA_VERSION = 10;
    private static final Set<String> NPC_SERVICE_COMMANDS = Set.of(
        "menu", "quickstart", "settings", "profile", "adventurer", "skills", "mastery",
        "jobs", "daily", "playtime", "campaign", "warps", "party", "social", "duel",
        "cosmetics", "relics", "recipes", "fishing",
        "excavation", "claim", "market", "shops", "grave", "reputation"
    );

    private final JavaPlugin plugin;
    private final PaperItemService items;

    public NpcContentLoader(JavaPlugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    public NpcContent load() {
        NpcContent base = loadBase();
        try {
            var authored = new net.nerdsmp.paper.studio.npc.NpcBlueprintStore(
                new File(plugin.getDataFolder(), "content/engine-npcs.yml"), base);
            NpcContent merged = net.nerdsmp.paper.studio.npc.NpcBlueprints.merge(base, authored.all());
            validateReferences(merged.factions(), merged.shops(), merged.dialogues(), merged.npcs());
            return merged;
        } catch (java.io.IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Unable to load authored NPC dialogue; files were preserved", failure);
        }
    }

    /** Shipped/manual infrastructure is read-only to Content Studio; authored definitions are merged separately. */
    public NpcContent loadBase() {
        YamlConfiguration yaml = content();
        Map<FactionId, NpcContent.FactionDefinition> factions = factions(yaml);
        Map<String, NpcContent.ShopDefinition> shops = shops(yaml);
        Map<String, NpcContent.DialogueNode> dialogues = dialogues(yaml);
        Map<String, NpcContent.NpcDefinition> npcs = npcs(yaml);
        validateReferences(factions, shops, dialogues, npcs);
        return new NpcContent(factions, npcs, dialogues, shops);
    }

    private YamlConfiguration content() {
        File target = new File(plugin.getDataFolder(), "content/npcs.yml");
        if (!target.exists()) {
            plugin.saveResource("content/npcs.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        if (yaml.getInt("schema-version", 0) >= SCHEMA_VERSION) {
            return yaml;
        }
        try (var reader = new InputStreamReader(
            java.util.Objects.requireNonNull(plugin.getResource("content/npcs.yml")), StandardCharsets.UTF_8
        )) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load bundled NPC content", exception);
        }
    }

    private Map<FactionId, NpcContent.FactionDefinition> factions(YamlConfiguration yaml) {
        ConfigurationSection root = required(yaml, "factions");
        Map<FactionId, NpcContent.FactionDefinition> result = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = required(root, rawId);
            FactionId id = new FactionId(rawId);
            ConfigurationSection tiers = required(section, "tiers");
            List<NpcContent.TierDefinition> loadedTiers = new ArrayList<>();
            for (String tierId : tiers.getKeys(false)) {
                ConfigurationSection tier = required(tiers, tierId);
                loadedTiers.add(new NpcContent.TierDefinition(
                    tierId,
                    tier.getString("name", title(tierId)),
                    tier.getString("color", "gray"),
                    tier.getInt("threshold"),
                    tier.getDouble("shop-price-multiplier", 1.0D),
                    tier.getBoolean("can-trade", true)
                ));
            }
            loadedTiers.sort(Comparator.comparingInt(NpcContent.TierDefinition::threshold));
            if (loadedTiers.getFirst().threshold() > ReputationService.MINIMUM) {
                throw new IllegalStateException("Faction " + id + " has no tier at minimum reputation");
            }
            result.put(id, new NpcContent.FactionDefinition(
                id,
                section.getString("name", title(rawId)),
                section.getString("description", ""),
                loadedTiers
            ));
        }
        return result;
    }

    private Map<String, NpcContent.ShopDefinition> shops(YamlConfiguration yaml) {
        ConfigurationSection root = required(yaml, "shops");
        Map<String, NpcContent.ShopDefinition> result = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            String id = id(rawId, "shop");
            ConfigurationSection section = required(root, rawId);
            String description = section.getString("description", "").strip();
            if (description.isEmpty()) {
                throw new IllegalStateException("NPC shop " + id + " needs a purpose description");
            }
            ConfigurationSection products = required(section, "products");
            List<NpcContent.ShopProduct> loaded = new ArrayList<>();
            for (String rawProduct : products.getKeys(false)) {
                String productId = id(rawProduct, "product");
                ConfigurationSection product = required(products, rawProduct);
                Material material = null;
                ItemId customItem = null;
                if (product.isString("material")) {
                    material = Material.matchMaterial(product.getString("material", ""));
                    if (material == null || material.isAir()) {
                        throw new IllegalStateException("Invalid material in NPC shop: " + productId);
                    }
                }
                if (product.isString("custom-item")) {
                    customItem = new ItemId(product.getString("custom-item", ""));
                    if (!items.itemIds().contains(customItem)) {
                        throw new IllegalStateException("Unknown custom item in NPC shop: " + customItem);
                    }
                }
                NpcContent.ShopProduct definition = new NpcContent.ShopProduct(
                    productId, material, customItem, product.getInt("amount", 1),
                    product.isSet("buy-price") ? product.getLong("buy-price") : product.getLong("base-price"),
                    product.getLong("sell-price", 0L),
                    product.getInt("daily-sell-limit", 0),
                    product.getInt("minimum-reputation", ReputationService.MINIMUM),
                    product.getString("description", "")
                );
                try {
                    ShopItems.create(items, definition, definition.amount());
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalStateException("Invalid bundle in NPC shop " + id + ": " + productId, invalid);
                }
                loaded.add(definition);
            }
            result.put(id, new NpcContent.ShopDefinition(
                id,
                section.getString("title", title(rawId)),
                description,
                loaded
            ));
        }
        return result;
    }

    private Map<String, NpcContent.DialogueNode> dialogues(YamlConfiguration yaml) {
        ConfigurationSection root = required(yaml, "dialogues");
        Map<String, NpcContent.DialogueNode> result = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            String nodeId = id(rawId, "dialogue");
            ConfigurationSection section = required(root, rawId);
            List<NpcContent.DialogueChoice> choices = new ArrayList<>();
            ConfigurationSection choiceRoot = section.getConfigurationSection("choices");
            if (choiceRoot != null) {
                for (String rawChoice : choiceRoot.getKeys(false)) {
                    String choiceId = id(rawChoice, "choice");
                    ConfigurationSection choice = required(choiceRoot, rawChoice);
                    choices.add(new NpcContent.DialogueChoice(
                        choiceId,
                        choice.getString("text", title(choiceId)),
                        choice.getString("next"),
                        choice.getBoolean("close", false),
                        choice.getBoolean("once", false),
                        Duration.ofMinutes(Math.max(0L, choice.getLong("cooldown-minutes", 0L))),
                        requirements(choice.getConfigurationSection("requires")),
                        effects(choice.getMapList("effects"), nodeId, choiceId)
                    ));
                }
            }
            List<String> text = section.getStringList("text");
            if (text.isEmpty() && section.isString("text")) {
                text = List.of(section.getString("text", ""));
            }
            result.put(nodeId, new NpcContent.DialogueNode(nodeId, text, choices));
        }
        return result;
    }

    private NpcContent.Requirements requirements(ConfigurationSection section) {
        if (section == null) {
            return NpcContent.Requirements.none();
        }
        return new NpcContent.Requirements(
            section.getInt("minimum-reputation", ReputationService.MINIMUM),
            section.getInt("maximum-reputation", ReputationService.MAXIMUM),
            section.getLong("minimum-marks", 0L),
            Math.max(1, section.getInt("minimum-adventurer-level", 1)),
            new HashSet<>(section.getStringList("flags-all")),
            new HashSet<>(section.getStringList("flags-none"))
        );
    }

    private List<NpcContent.DialogueEffect> effects(List<Map<?, ?>> maps, String nodeId, String choiceId) {
        if (maps.size() > 8) {
            throw new IllegalStateException("A dialogue choice supports at most eight effects: " + nodeId + "/" + choiceId);
        }
        List<NpcContent.DialogueEffect> result = new ArrayList<>();
        for (Map<?, ?> raw : maps) {
            String typeName = String.valueOf(raw.containsKey("type") ? raw.get("type") : "")
                .toUpperCase(Locale.ROOT);
            NpcContent.EffectType type;
            try {
                type = NpcContent.EffectType.valueOf(typeName.replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid effect on " + nodeId + "/" + choiceId + ": " + typeName);
            }
            FactionId factionId = raw.containsKey("faction")
                ? new FactionId(String.valueOf(raw.get("faction"))) : null;
            int amount = integer(raw.get("amount"), 0);
            String key = raw.containsKey("key") ? id(String.valueOf(raw.get("key")), "story flag") : null;
            boolean value = !raw.containsKey("value") || Boolean.parseBoolean(String.valueOf(raw.get("value")));
            String shop = raw.containsKey("shop") ? id(String.valueOf(raw.get("shop")), "shop") : null;
            String command = raw.containsKey("command")
                ? String.valueOf(raw.get("command")).trim().toLowerCase(Locale.ROOT) : null;
            if ((type == NpcContent.EffectType.REPUTATION || type == NpcContent.EffectType.MARKS) && amount == 0) {
                throw new IllegalStateException(type + " effect needs a non-zero amount on " + nodeId + "/" + choiceId);
            }
            if (type == NpcContent.EffectType.REPUTATION && Math.abs((long) amount) > 1000L) {
                throw new IllegalStateException("Reputation effect is outside -1000..1000 on " + nodeId + "/" + choiceId);
            }
            if (type == NpcContent.EffectType.MARKS && Math.abs((long) amount) > 1_000_000L) {
                throw new IllegalStateException("Marks effect is too large on " + nodeId + "/" + choiceId);
            }
            if (type == NpcContent.EffectType.FLAG && key == null) {
                throw new IllegalStateException("Flag effect needs a key on " + nodeId + "/" + choiceId);
            }
            if (type == NpcContent.EffectType.OPEN_SHOP && shop == null) {
                throw new IllegalStateException("Shop effect needs a shop id on " + nodeId + "/" + choiceId);
            }
            if (type == NpcContent.EffectType.OPEN_COMMAND
                && (command == null || !NPC_SERVICE_COMMANDS.contains(command))) {
                throw new IllegalStateException("Unknown NPC service command on " + nodeId + "/" + choiceId);
            }
            result.add(new NpcContent.DialogueEffect(type, factionId, amount, key, value, shop, command));
        }
        long openActions = result.stream().filter(effect -> effect.type() == NpcContent.EffectType.OPEN_SHOP
            || effect.type() == NpcContent.EffectType.OPEN_BANK
            || effect.type() == NpcContent.EffectType.OPEN_IDENTIFIER
            || effect.type() == NpcContent.EffectType.OPEN_REPAIR
            || effect.type() == NpcContent.EffectType.OPEN_CONTRACTS
            || effect.type() == NpcContent.EffectType.OPEN_ARCHIVE
            || effect.type() == NpcContent.EffectType.OPEN_FISHERY
            || effect.type() == NpcContent.EffectType.OPEN_ALCHEMY
            || effect.type() == NpcContent.EffectType.OPEN_FORGE
            || effect.type() == NpcContent.EffectType.OPEN_ENCHANTMENTS
            || effect.type() == NpcContent.EffectType.OPEN_COMMAND).count();
        if (openActions > 1L) {
            throw new IllegalStateException("A dialogue choice can open only one service: " + nodeId + "/" + choiceId);
        }
        return result;
    }

    private Map<String, NpcContent.NpcDefinition> npcs(YamlConfiguration yaml) {
        ConfigurationSection root = required(yaml, "npcs");
        Map<String, NpcContent.NpcDefinition> result = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            String npcId = id(rawId, "npc");
            ConfigurationSection section = required(root, rawId);
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(section.getString("entity", "VILLAGER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid entity type for NPC " + npcId, exception);
            }
            Class<?> entityClass = entityType.getEntityClass();
            if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
                throw new IllegalStateException("NPC body must be a living entity: " + npcId);
            }
            result.put(npcId, new NpcContent.NpcDefinition(
                npcId,
                section.getString("name", title(npcId)),
                section.getString("title", ""),
                entityType,
                new FactionId(section.getString("faction", "")),
                id(section.getString("root-dialogue", ""), "root dialogue"),
                section.isString("shop") ? id(section.getString("shop", ""), "shop") : null,
                Math.max(2.0D, Math.min(24.0D, section.getDouble("look-radius", 8.0D)))
            ));
        }
        return result;
    }

    private void validateReferences(
        Map<FactionId, NpcContent.FactionDefinition> factions,
        Map<String, NpcContent.ShopDefinition> shops,
        Map<String, NpcContent.DialogueNode> dialogues,
        Map<String, NpcContent.NpcDefinition> npcs
    ) {
        for (NpcContent.NpcDefinition npc : npcs.values()) {
            if (!factions.containsKey(npc.factionId())) {
                throw new IllegalStateException("NPC " + npc.id() + " references unknown faction " + npc.factionId());
            }
            if (!dialogues.containsKey(npc.rootDialogue())) {
                throw new IllegalStateException("NPC " + npc.id() + " references unknown dialogue " + npc.rootDialogue());
            }
            if (npc.shopId() != null && !shops.containsKey(npc.shopId())) {
                throw new IllegalStateException("NPC " + npc.id() + " references unknown shop " + npc.shopId());
            }
        }
        for (NpcContent.DialogueNode node : dialogues.values()) {
            Set<String> ids = new HashSet<>();
            for (NpcContent.DialogueChoice choice : node.choices()) {
                if (!ids.add(choice.id())) {
                    throw new IllegalStateException("Duplicate choice id in " + node.id() + ": " + choice.id());
                }
                if (choice.nextNode() != null && !dialogues.containsKey(choice.nextNode())) {
                    throw new IllegalStateException("Unknown next dialogue " + choice.nextNode() + " from " + node.id());
                }
                for (NpcContent.DialogueEffect effect : choice.effects()) {
                    if (effect.factionId() != null && !factions.containsKey(effect.factionId())) {
                        throw new IllegalStateException("Unknown effect faction " + effect.factionId());
                    }
                    if (effect.shopId() != null && !shops.containsKey(effect.shopId())) {
                        throw new IllegalStateException("Unknown effect shop " + effect.shopId());
                    }
                }
            }
        }
    }

    private ConfigurationSection required(ConfigurationSection parent, String path) {
        ConfigurationSection found = parent.getConfigurationSection(path);
        if (found == null) {
            throw new IllegalStateException("NPC content is missing section: " + path);
        }
        return found;
    }

    private String id(String raw, String kind) {
        String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,96}")) {
            throw new IllegalStateException("Invalid " + kind + " id: " + raw);
        }
        return normalized;
    }

    private int integer(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return raw == null ? fallback : Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String title(String id) {
        StringBuilder result = new StringBuilder();
        for (String part : id.split("[_-]")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
