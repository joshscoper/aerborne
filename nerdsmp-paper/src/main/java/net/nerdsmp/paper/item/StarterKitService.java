package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import net.nerdsmp.paper.storage.StablePlayerKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StarterKitService {
    private static final List<ItemId> ESSENTIAL_ITEMS = List.of(
        "frontier_hood", "frontier_coat", "frontier_trousers", "frontier_boots",
        "frontier_blade", "frontier_pickaxe", "frontier_hatchet", "frontier_shovel", "frontier_hoe"
    ).stream().map(ItemId::new).toList();

    private final JavaPlugin plugin;
    private final PaperItemService items;
    private final File grantsFile;
    private final String seasonId;
    private final String storageMode;
    private final int kitVersion;
    private final boolean autoEquip;
    private final List<ItemId> kitItems;
    private final Material foodMaterial;
    private final int foodAmount;
    private final int healthPotions;
    private final int torches;
    private final Set<String> grants;

    public StarterKitService(JavaPlugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
        seasonId = plugin.getConfig().getString("season.id", "unknown-season");
        storageMode = plugin.getConfig().getString("storage.mode", "memory");
        kitVersion = Math.max(1, plugin.getConfig().getInt("starter-kit.version", 2));
        autoEquip = plugin.getConfig().getBoolean("starter-kit.auto-equip", true);
        java.util.LinkedHashSet<ItemId> configuredItems = plugin.getConfig().getStringList("starter-kit.items")
            .stream().map(ItemId::new).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        configuredItems.addAll(ESSENTIAL_ITEMS);
        kitItems = List.copyOf(configuredItems);
        if (kitItems.isEmpty()) {
            throw new IllegalStateException("starter-kit.items must contain at least one NerdSMP item id");
        }
        for (ItemId itemId : kitItems) {
            if (!items.itemIds().contains(itemId)) {
                throw new IllegalStateException("Unknown starter-kit item: " + itemId);
            }
        }
        foodMaterial = Material.matchMaterial(plugin.getConfig().getString(
            "starter-kit.food.material", "cooked_beef"));
        foodAmount = boundedAmount("starter-kit.food.amount", 16, 64);
        healthPotions = boundedAmount("starter-kit.health-potions", 3, 9);
        torches = boundedAmount("starter-kit.torches", 16, 64);
        if (foodMaterial == null || !foodMaterial.isItem() || !foodMaterial.isEdible()) {
            throw new IllegalStateException("starter-kit.food.material must be an edible item");
        }
        grantsFile = new File(plugin.getDataFolder(), "starter-kits.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        if (grantsFile.isFile()) {
            try {
                configuration.load(grantsFile);
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                throw new IllegalStateException("Could not read starter-kit history; refusing to issue duplicate kits", exception);
            }
        }
        grants = new HashSet<>(configuration.getStringList("grants"));
    }

    public synchronized GrantResult grantIfNeeded(AccountId accountId, Player player) {
        String grantId = grantKey(seasonId, storageMode, accountId, player.getUniqueId(), kitVersion);
        if (grants.contains(grantId)) {
            return GrantResult.ALREADY_GRANTED;
        }
        Set<ItemId> present = presentKitItems(player);
        if (hasEveryKitItem(kitItems, present)) {
            recordGrant(grantId);
            return GrantResult.RECORDED_EXISTING;
        }
        List<ItemStack> prepared = kitItems.stream()
            .filter(itemId -> !present.contains(itemId))
            .map(items::create)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        prepared.addAll(supplies());
        recordGrant(grantId);
        givePrepared(player, prepared);
        return GrantResult.GRANTED;
    }

    public void give(Player player) {
        List<ItemStack> prepared = kitItems.stream().map(items::create)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        prepared.addAll(supplies());
        givePrepared(player, prepared);
    }

    static String grantKey(
        String seasonId,
        String storageMode,
        AccountId accountId,
        java.util.UUID playerId,
        int kitVersion
    ) {
        return seasonId + ":v" + kitVersion + ":" + StablePlayerKey.forStorage(storageMode, accountId, playerId);
    }

    static boolean hasEveryKitItem(Collection<ItemId> expected, Collection<ItemId> present) {
        return new HashSet<>(present).containsAll(expected);
    }

    private Set<ItemId> presentKitItems(Player player) {
        return java.util.Arrays.stream(player.getInventory().getContents())
            .filter(java.util.Objects::nonNull)
            .filter(items::isAuthentic)
            .map(items::describe)
            .flatMap(java.util.Optional::stream)
            .map(net.nerdsmp.api.service.ItemService.ItemDescriptor::itemId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void recordGrant(String grantId) {
        grants.add(grantId);
        try {
            saveGrants();
        } catch (RuntimeException exception) {
            grants.remove(grantId);
            throw exception;
        }
    }

    private void givePrepared(Player player, List<ItemStack> prepared) {
        for (ItemStack item : prepared) {
            if (autoEquip && equipIfEmpty(player, item)) {
                continue;
            }
            player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    public List<ItemId> itemIds() {
        return kitItems;
    }

    private List<ItemStack> supplies() {
        List<ItemStack> result = new ArrayList<>();
        result.add(new ItemStack(foodMaterial, foodAmount));
        result.add(new ItemStack(Material.TORCH, torches));
        for (int index = 0; index < healthPotions; index++) {
            result.add(healthPotion());
        }
        return result;
    }

    private ItemStack healthPotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(PotionType.HEALING);
        var miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        meta.itemName(miniMessage.deserialize("<red>Minor Health Potion</red>")
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
            miniMessage.deserialize("<dark_gray>────────────────────</dark_gray>"),
            miniMessage.deserialize("<gray>Restores</gray> <green>4 Health</green> <gray>instantly.</gray>"),
            miniMessage.deserialize("<dark_gray>Frontier medical supply.</dark_gray>")
        ).stream().map(line -> line.decoration(
            net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        potion.setItemMeta(meta);
        return potion;
    }

    private int boundedAmount(String path, int fallback, int maximum) {
        int configured = plugin.getConfig().getInt(path, fallback);
        if (configured < 1 || configured > maximum) {
            throw new IllegalStateException(path + " must be between 1 and " + maximum);
        }
        return configured;
    }

    private boolean equipIfEmpty(Player player, ItemStack item) {
        var gear = items.gear(item).orElse(null);
        if (gear == null) {
            return false;
        }
        return switch (gear.slot()) {
            case HEAD -> setIfEmpty(player.getInventory().getHelmet(), item, player.getInventory()::setHelmet);
            case CHEST -> setIfEmpty(player.getInventory().getChestplate(), item, player.getInventory()::setChestplate);
            case LEGS -> setIfEmpty(player.getInventory().getLeggings(), item, player.getInventory()::setLeggings);
            case FEET -> setIfEmpty(player.getInventory().getBoots(), item, player.getInventory()::setBoots);
            default -> false;
        };
    }

    private boolean setIfEmpty(ItemStack current, ItemStack replacement, java.util.function.Consumer<ItemStack> setter) {
        if (current != null && !current.getType().isAir()) {
            return false;
        }
        setter.accept(replacement);
        return true;
    }

    private void saveGrants() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("grants", grants.stream().sorted().toList());
        try {
            AtomicYamlFiles.save(configuration, grantsFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist starter-kit grant history", exception);
        }
    }

    public enum GrantResult {
        GRANTED,
        RECORDED_EXISTING,
        ALREADY_GRANTED
    }
}
