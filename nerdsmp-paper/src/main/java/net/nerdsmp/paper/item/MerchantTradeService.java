package net.nerdsmp.paper.item;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.ArcaneForgeService;
import net.nerdsmp.paper.forge.GeodeGrade;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Converts vanilla merchant gear and enchanted books into NerdSMP equivalents. */
public final class MerchantTradeService implements Listener {
    private final JavaPlugin plugin;
    private final PaperItemService items;
    private final ArcaneForgeService forge;
    private final NamespacedKey npcInstanceKey;

    public MerchantTradeService(JavaPlugin plugin, PaperItemService items, ArcaneForgeService forge) {
        this.plugin = plugin;
        this.items = items;
        this.forge = forge;
        npcInstanceKey = new NamespacedKey(plugin, "npc_instance");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquire(VillagerAcquireTradeEvent event) {
        long seed = event.getEntity().getUniqueId().getMostSignificantBits() ^ event.getRecipe().hashCode();
        event.setRecipe(convert(event.getRecipe(), seed));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractVillager merchant) || managedNpc(merchant)) {
            return;
        }
        List<MerchantRecipe> converted = new ArrayList<>();
        long seed = merchant.getUniqueId().getLeastSignificantBits();
        List<MerchantRecipe> recipes = merchant.getRecipes();
        for (int index = 0; index < recipes.size(); index++) {
            converted.add(convert(recipes.get(index), seed + index * 31L));
        }
        merchant.setRecipes(converted);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWanderingTraderSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || managedNpc(trader)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> addWanderingOffers(trader));
    }

    private MerchantRecipe convert(MerchantRecipe original, long seed) {
        ItemStack result = original.getResult();
        if (result.getType() == Material.ENCHANTED_BOOK) {
            ArcaneEnchantment[] catalog = net.nerdsmp.paper.forge.arcane.ArcaneForge.obtainable(plugin);
            SplittableRandom random = new SplittableRandom(seed);
            ArcaneEnchantment enchantment = catalog[random.nextInt(catalog.length)];
            int level = Math.min(enchantment.maximumLevel(), 1 + random.nextInt(3));
            return copy(original, forge.createTome(enchantment, level));
        }
        if (!items.isAuthentic(result) && items.isCraftedEquipment(result.getType())) {
            ItemStack styled = items.createCrafted(result.getType());
            styled.setAmount(result.getAmount());
            return copy(original, styled);
        }
        return original;
    }

    private MerchantRecipe copy(MerchantRecipe original, ItemStack result) {
        MerchantRecipe converted = new MerchantRecipe(result, original.getMaxUses());
        converted.setUses(original.getUses());
        converted.setExperienceReward(original.hasExperienceReward());
        converted.setVillagerExperience(original.getVillagerExperience());
        converted.setPriceMultiplier(original.getPriceMultiplier());
        converted.setDemand(original.getDemand());
        converted.setSpecialPrice(original.getSpecialPrice());
        converted.setIgnoreDiscounts(original.shouldIgnoreDiscounts());
        converted.setIngredients(original.getIngredients());
        return converted;
    }

    private void addWanderingOffers(WanderingTrader trader) {
        if (!trader.isValid() || managedNpc(trader)) {
            return;
        }
        List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
        if (recipes.stream().noneMatch(recipe -> forge.geode(recipe.getResult()).isPresent())) {
            MerchantRecipe geode = new MerchantRecipe(forge.createGeode(GeodeGrade.ROUGH), 4);
            geode.setIngredients(List.of(new ItemStack(Material.EMERALD, 8)));
            recipes.add(geode);
        }
        if (recipes.stream().noneMatch(recipe -> forge.tome(recipe.getResult()).isPresent())) {
            ArcaneEnchantment[] catalog = net.nerdsmp.paper.forge.arcane.ArcaneForge.obtainable(plugin);
            int selected = Math.floorMod(trader.getUniqueId().hashCode(), catalog.length);
            MerchantRecipe tome = new MerchantRecipe(forge.createTome(catalog[selected], 1), 1);
            tome.setIngredients(List.of(new ItemStack(Material.EMERALD, 16),
                new ItemStack(Material.AMETHYST_SHARD, 4)));
            recipes.add(tome);
        }
        trader.setRecipes(recipes.stream().map(recipe -> convert(recipe,
            trader.getUniqueId().getMostSignificantBits() ^ recipe.hashCode())).toList());
    }

    private boolean managedNpc(AbstractVillager entity) {
        return entity.getPersistentDataContainer().has(npcInstanceKey, PersistentDataType.STRING);
    }
}
