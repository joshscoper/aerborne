package net.nerdsmp.paper.alchemy;

import com.google.common.collect.HashMultimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class AlchemyService implements Listener {
    private static final int[] TRADITIONAL_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        29, 30, 31, 32, 33, 34
    };
    private static final int[] ARCANE_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final int[] POISON_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};
    private static final int ANTIDOTE_LEVEL = 10;
    private static final List<Material> ANTIDOTE_INGREDIENTS = List.of(
        Material.GLASS_BOTTLE,
        Material.HONEY_BOTTLE,
        Material.GHAST_TEAR
    );
    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private final SkillListener skills;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey vialTypeKey;
    private final NamespacedKey vialPotencyKey;
    private final NamespacedKey antidoteKey;
    private final NamespacedKey activePoisonTypesKey;
    private final NamespacedKey arcaneBrewKey;
    private final NamespacedKey scaleModifierKey;
    private final NamespacedKey scaleAmountKey;
    private final NamespacedKey scaleExpiresKey;
    private final Map<UUID, BukkitTask> scaleTasks = new HashMap<>();

    public AlchemyService(NerdSmpPlugin plugin, PaperItemService items, SkillListener skills) {
        this.plugin = plugin;
        this.items = items;
        this.skills = skills;
        vialTypeKey = new NamespacedKey(plugin, "poison_vial_type");
        vialPotencyKey = new NamespacedKey(plugin, "poison_vial_potency");
        antidoteKey = new NamespacedKey(plugin, "alchemy_antidote");
        activePoisonTypesKey = new NamespacedKey(plugin, "active_poison_types");
        arcaneBrewKey = new NamespacedKey(plugin, "arcane_brew");
        scaleModifierKey = new NamespacedKey(plugin, "alchemy_scale");
        scaleAmountKey = new NamespacedKey(plugin, "alchemy_scale_amount");
        scaleExpiresKey = new NamespacedKey(plugin, "alchemy_scale_expires");
    }

    public void close() {
        scaleTasks.values().forEach(BukkitTask::cancel);
        scaleTasks.clear();
    }

    public void open(Player player) {
        AlchemyHolder holder = menu(player, "<light_purple><bold>Alchemy</bold></light_purple>");
        int level = level(player);
        set(holder, 4, icon(Material.BREWING_STAND,
            "<light_purple><bold>Alchemy " + level + "</bold></light_purple>", List.of(
                "<gray>Choose a recipe. Check its ingredients. Brew.</gray>",
                "<dark_gray>Uses ingredients, never experience levels.</dark_gray>"
            )), ignored -> { });
        set(holder, 20, icon(Material.POTION, "<aqua><bold>Traditional Potions</bold></aqua>", List.of(
            "<gray>Healing, buffs and familiar potion effects.</gray>",
            "<white>Choose strength, duration and bottle type.</white>",
            "", "<yellow>Click to browse.</yellow>"
        )), this::openTraditional);
        set(holder, 22, icon(Material.EXPERIENCE_BOTTLE,
            "<light_purple><bold>Arcane Brews</bold></light_purple>", List.of(
                "<gray>Shrink, grow, harden skin or ride the wind.</gray>",
                "", "<yellow>Click to browse.</yellow>"
            )), this::openArcane);
        set(holder, 24, icon(Material.HONEY_BOTTLE, "<green><bold>Poisons</bold></green>", List.of(
            "<gray>Drink a toxin, or drag it onto weapons or arrows.</gray>",
            "<white>Train Alchemy for stronger poison.</white>",
            "", "<yellow>Click to browse.</yellow>"
        )), this::openPoisons);
        addCoatingButton(holder, 40);
        set(holder, net.nerdsmp.paper.gui.StationLayout.BACK, MenuKit.back("Workshop"), target -> {
            MenuKit.clickSound(target);
            plugin.menus().open(target, MenuManager.View.WORKSHOP);
        });
        set(holder, net.nerdsmp.paper.gui.StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
        player.openInventory(holder.inventory);
    }

    public List<String> recipeIds() {
        List<String> ids = new ArrayList<>();
        Arrays.stream(TraditionalPotion.values()).map(TraditionalPotion::id).forEach(ids::add);
        Arrays.stream(ArcaneBrew.values()).map(ArcaneBrew::id).forEach(ids::add);
        Arrays.stream(PoisonType.values()).map(PoisonType::id).forEach(ids::add);
        ids.add("antidote");
        return List.copyOf(ids);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof AlchemyHolder holder)) {
            coatFromCursor(event);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player owner) || !holder.current(owner, plugin)) {
            event.setCancelled(true);
            return;
        }
        if (coatFromCursor(event)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0
            && event.getRawSlot() < event.getView().getTopInventory().getSize()
            && event.isLeftClick() && !event.isShiftClick()) {
            Consumer<Player> action = holder.actions.get(event.getRawSlot());
            if (action != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (holder.current(player, plugin) && player.getOpenInventory().getTopInventory() == holder.inventory) action.accept(player);
                });
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AlchemyHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseVial(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            && event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
            && event.getClickedBlock() != null
            && event.getClickedBlock().getType() == Material.BREWING_STAND) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    open(event.getPlayer());
                }
            });
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (isAntidote(event.getItem())) {
            Bukkit.getScheduler().runTask(plugin, () -> consumeAntidote(event.getPlayer()));
            return;
        }
        Vial poison = vial(event.getItem());
        if (poison != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    markActivePoison(event.getPlayer(), poison.type);
                    event.getPlayer().sendActionBar(miniMessage.deserialize("<red>☠ "
                        + poison.type.displayName() + " " + roman(poison.potency) + "</red> <gray>consumed</gray>"));
                }
            });
            return;
        }
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            if (event.getPlayer().getPersistentDataContainer().has(scaleExpiresKey, PersistentDataType.LONG)) {
                Bukkit.getScheduler().runTask(plugin, () -> clearScale(event.getPlayer()));
            }
            return;
        }
        ArcaneBrew brew = arcaneBrew(event.getItem());
        if (brew == null || brew.scaleModifier() == 0.0D) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyScale(event.getPlayer(), brew));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> restoreScale(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitTask task = scaleTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearScale(event.getEntity());
        event.getEntity().getPersistentDataContainer().remove(activePoisonTypesKey);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        Vial poison = poisonIdentity(event.getConsumable());
        if (poison == null && event.getEntity() instanceof Player player) {
            ItemStack rangedWeapon = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
            PaperItemService.Coating coating = items.consumeCoating(rangedWeapon).orElse(null);
            if (coating != null) {
                PoisonType type = PoisonType.fromId(coating.type());
                if (type != null) {
                    poison = new Vial(type, coating.potency());
                    player.sendActionBar(miniMessage.deserialize("<green>☠ " + type.displayName()
                        + " readied</green> <gray>• " + Math.max(0, coating.charges() - 1)
                        + " shots left</gray>"));
                }
            }
        }
        if (poison == null) {
            return;
        }
        projectile.getPersistentDataContainer().set(vialTypeKey, PersistentDataType.STRING, poison.type.id());
        projectile.getPersistentDataContainer().set(vialPotencyKey, PersistentDataType.INTEGER, poison.potency);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPoisonStrike(EntityDamageByEntityEvent event) {
        // A signature's arc is not a strike with the coated weapon and must not spend its charges.
        if (net.nerdsmp.paper.anticheat.ArcaneEffectScope.covers(event)) return;
        if (!(event.getEntity() instanceof LivingEntity target) || event.getFinalDamage() <= 0.0D) {
            return;
        }
        Player attacker;
        PoisonType poison;
        int potency;
        Integer charges = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            PaperItemService.Coating coating = items.consumeCoating(weapon).orElse(null);
            if (coating == null) {
                return;
            }
            poison = PoisonType.fromId(coating.type());
            potency = coating.potency();
            charges = Math.max(0, coating.charges() - 1);
        } else if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof Player player) {
            attacker = player;
            Vial arrowPoison = projectilePoison(projectile);
            if (arrowPoison == null) {
                return;
            }
            poison = arrowPoison.type;
            potency = arrowPoison.potency;
        } else {
            return;
        }
        if (poison == null) {
            return;
        }
        ItemStack weapon = event.getDamager() instanceof AbstractArrow arrow ? arrow.getWeapon() : attacker.getInventory().getItemInMainHand();
        double bonus = plugin.gearSets().profile(attacker, weapon).stat("poison-potency-percent");
        poison.apply(target, potency, bonus);
        int remaining = poison.remainingDurationTicks(target);
        if (remaining <= 0) {
            attacker.sendActionBar(miniMessage.deserialize("<gray>" + poison.displayName()
                + " had no effect on that target.</gray>"));
            return;
        }
        markActivePoison(target, poison);
        String chargeText = charges == null ? "" : " <dark_gray>•</dark_gray> <gray>" + charges + " strikes left</gray>";
        attacker.sendActionBar(miniMessage.deserialize("<green>☠ " + poison.displayName() + " " + roman(potency)
            + "</green> <dark_gray>•</dark_gray> <white>" + duration(remaining) + "</white>" + chargeText));
    }

    private void openTraditional(Player player) {
        AlchemyHolder holder = menu(player, "<aqua><bold>Traditional Potions</bold></aqua>");
        int level = level(player);
        set(holder, 4, icon(Material.POTION, "<aqua><bold>Traditional Potions</bold></aqua>", List.of(
            "<gray>Select a family, then choose strength and bottle.</gray>",
            "<white>All effect-bearing vanilla potion families are here.</white>"
        )), ignored -> { });
        int index = 0;
        for (TraditionalPotion recipe : TraditionalPotion.values()) {
            boolean unlocked = level >= recipe.requiredLevel();
            ItemStack display = unlocked
                ? potionIcon(Material.POTION, recipe.baseType(), "<aqua>" + recipe.displayName() + "</aqua>",
                    traditionalLore(recipe, true))
                : icon(Material.GRAY_DYE, "<gray>" + recipe.displayName() + "</gray>",
                    traditionalLore(recipe, false));
            set(holder, TRADITIONAL_SLOTS[index++], display,
                unlocked ? target -> openTraditionalDetail(target, recipe) : ignored -> { });
        }
        addBackButton(holder);
        player.openInventory(holder.inventory);
    }

    private void openTraditionalDetail(Player player, TraditionalPotion recipe) {
        AlchemyHolder holder = menu(player, "<aqua>" + recipe.displayName() + "</aqua>");
        set(holder, 4, potionIcon(Material.POTION, recipe.baseType(),
            "<aqua><bold>Potion of " + recipe.displayName() + "</bold></aqua>", List.of(
                "<gray>Rows choose strength. Columns choose bottle.</gray>",
                "<dark_gray>Redstone extends. Glowstone concentrates.</dark_gray>"
            )), ignored -> { });
        for (int row = 0; row < PotionVariant.values().length; row++) {
            PotionVariant variant = PotionVariant.values()[row];
            set(holder, 18 + row * 9, icon(Material.PAPER, "<aqua>" + variant.displayName() + "</aqua>", List.of()), ignored -> { });
            PotionType resolved = variant.resolve(recipe.baseType()).orElse(null);
            for (int column = 0; column < PotionForm.values().length; column++) {
                PotionForm form = PotionForm.values()[column];
                set(holder, 11 + column * 2, icon(form.material(), "<aqua>" + form.displayName() + "</aqua>", List.of()), ignored -> { });
                int slot = net.nerdsmp.paper.gui.StationLayout.variant(row, column);
                if (resolved == null) {
                    set(holder, slot, icon(Material.GRAY_STAINED_GLASS_PANE,
                        "<dark_gray>" + variant.displayName() + " · " + form.displayName() + "</dark_gray>",
                        List.of("<gray>This family has no " + variant.displayName().toLowerCase()
                            + " form.</gray>")), ignored -> { });
                    continue;
                }
                List<Material> ingredients = ingredients(recipe, variant, form);
                String potionName = form.potionName(recipe, variant);
                set(holder, slot, potionIcon(form.material(), resolved, "<aqua>" + potionName + "</aqua>",
                    recipeLore(ingredients, recipe.requiredLevel(), true)),
                    target -> reviewBrew(target, potionIcon(form.material(), resolved, "<aqua>" + potionName + "</aqua>", List.of()),
                        ingredients, recipe.requiredLevel(), () -> brewTraditional(target, recipe, variant, form), () -> openTraditionalDetail(target, recipe), recipe.displayName()));
            }
        }
        set(holder, net.nerdsmp.paper.gui.StationLayout.BACK, MenuKit.back("Traditional Potions"), this::openTraditional);
        set(holder, net.nerdsmp.paper.gui.StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
        player.openInventory(holder.inventory);
    }

    private void openArcane(Player player) {
        AlchemyHolder holder = menu(player, "<light_purple><bold>Arcane Brews</bold></light_purple>");
        int level = level(player);
        set(holder, 4, icon(Material.EXPERIENCE_BOTTLE,
            "<light_purple><bold>Arcane Brews</bold></light_purple>",
            List.of("<gray>Specialist mixtures found beyond ordinary brewing.</gray>")), ignored -> { });
        int index = 0;
        for (ArcaneBrew brew : ArcaneBrew.values()) {
            boolean unlocked = level >= brew.requiredLevel();
            ItemStack display = unlocked ? arcaneDisplay(brew) : icon(Material.GRAY_DYE,
                "<gray>" + brew.displayName() + "</gray>", arcaneLore(brew, false));
            set(holder, ARCANE_SLOTS[index++], display,
                unlocked ? target -> reviewBrew(target, createArcanePotion(brew), brew.ingredients(), brew.requiredLevel(),
                    () -> brewArcane(target, brew), () -> openArcane(target), "Arcane Brews") : ignored -> { });
        }
        addBackButton(holder);
        player.openInventory(holder.inventory);
    }

    private void openPoisons(Player player) {
        AlchemyHolder holder = menu(player, "<green><bold>Poison Distillery</bold></green>");
        int level = level(player);
        set(holder, 4, icon(Material.POTION, "<green><bold>Poison Distillery</bold></green>", List.of(
            "<gray>Drink a vial or drag it onto a weapon or arrows.</gray>",
            "<white>One vial coats a weapon or up to sixteen arrows.</white>"
        )), ignored -> { });
        int potency = Math.max(1, Math.min(3, 1 + level / 20));
        int index = 0;
        for (PoisonType poison : PoisonType.values()) {
            boolean unlocked = level >= poison.requiredLevel();
            ItemStack display = unlocked
                ? poisonDisplay(poison, potency, poisonLore(poison, true, potency))
                : icon(Material.GRAY_DYE, "<gray>" + poison.displayName() + "</gray>",
                    poisonLore(poison, false, potency));
            set(holder, POISON_SLOTS[index++], display,
                unlocked ? target -> { List<Material> needed = new ArrayList<>(poison.ingredients()); needed.add(Material.HONEY_BOTTLE);
                    reviewBrew(target, createVial(poison, potency), needed, poison.requiredLevel(), () -> distill(target, poison), () -> openPoisons(target), "Poison Distillery"); } : ignored -> { });
        }
        boolean antidoteUnlocked = level >= ANTIDOTE_LEVEL;
        set(holder, 38, antidoteUnlocked ? createAntidote(antidoteLore(true))
            : icon(Material.GRAY_DYE, "<gray>Universal Antidote</gray>", antidoteLore(false)),
            antidoteUnlocked ? target -> reviewBrew(target, createAntidote(antidoteLore(true)), ANTIDOTE_INGREDIENTS,
                ANTIDOTE_LEVEL, () -> distillAntidote(target), () -> openPoisons(target), "Poison Distillery") : ignored -> { });
        addCoatingButton(holder, 40);
        addBackButton(holder);
        player.openInventory(holder.inventory);
    }

    private void reviewBrew(Player player, ItemStack output, List<Material> ingredients, int required, Runnable craft, Runnable back,
                            String backTo) {
        AlchemyHolder holder = menu(player, "<light_purple>Review Brew</light_purple>");
        holder.inventory.setItem(4, output.clone());
        Map<Material, Integer> quantities = new java.util.LinkedHashMap<>(); ingredients.forEach(material -> quantities.merge(material, 1, Integer::sum));
        int index = 0; boolean all = true;
        for (var entry : quantities.entrySet()) {
            ItemStack plain = new ItemStack(entry.getKey());
            int have = 0; for (ItemStack stack : player.getInventory().getStorageContents()) if (stack != null && stack.isSimilar(plain)) have += stack.getAmount();
            boolean enough = have >= entry.getValue(); all &= enough;
            set(holder, net.nerdsmp.paper.gui.StationLayout.ingredient(index++), icon(entry.getKey(), (enough ? "<green>" : "<red>") + pretty(entry.getKey()),
                List.of("<gray>Need</gray> <white>" + entry.getValue() + "</white>", "<gray>In inventory</gray> <white>" + have + "</white>")), ignored -> { });
        }
        boolean ready = all && level(player) >= required;
        List<String> brewLore = List.of("<gray>Alchemy " + required + " required.</gray>", "<gray>Leave room for the result and returned containers.</gray>",
            "<gray>Ingredients are checked again when you brew.</gray>");
        set(holder, 40, icon(ready ? Material.LIME_DYE : Material.GRAY_DYE, ready ? "<green>Brew One</green>" : "<gray>Ingredients Needed</gray>",
            ready ? MenuKit.withHint(brewLore, "brew one") : brewLore), target -> {
                    if (!holder.busy && ready) { holder.busy = true; try { craft.run(); } finally { holder.busy = false; } }
                });
        set(holder, net.nerdsmp.paper.gui.StationLayout.BACK, MenuKit.back(backTo), ignored -> back.run());
        set(holder, 49, icon(Material.CLOCK, "<aqua>Refresh Ingredients</aqua>", List.of(MenuKit.hint("count your ingredients again"))),
            target -> reviewBrew(target, output, ingredients, required, craft, back, backTo));
        set(holder, net.nerdsmp.paper.gui.StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
        player.openInventory(holder.inventory);
    }
    private void brewTraditional(
        Player player,
        TraditionalPotion recipe,
        PotionVariant variant,
        PotionForm form
    ) {
        PotionType potionType = variant.resolve(recipe.baseType()).orElse(null);
        if (potionType == null) {
            player.sendRichMessage("<red>That form is not available for this potion.</red>");
            return;
        }
        List<Material> ingredients = ingredients(recipe, variant, form);
        String potionName = form.potionName(recipe, variant);
        ItemStack potion = new ItemStack(form.material());
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(potionType);
        Component name = miniMessage.deserialize("<aqua>" + potionName + "</aqua>")
            .decoration(TextDecoration.ITALIC, false);
        meta.itemName(name);
        meta.displayName(name);
        meta.lore(List.of(
            miniMessage.deserialize("<dark_gray>────────────────────</dark_gray>"),
            miniMessage.deserialize("<gray>Prepared at the Alchemy Workbench.</gray>")
                .decoration(TextDecoration.ITALIC, false)
        ));
        potion.setItemMeta(meta);
        if (!brewTransaction(player, ingredients, potion, recipe.requiredLevel())) return;
        long experience = 18L + recipe.requiredLevel() / 2L + variant.ordinal() * 6L + form.ordinal() * 4L;
        complete(player, experience, potionName, () -> openTraditionalDetail(player, recipe));
    }

    private void brewArcane(Player player, ArcaneBrew brew) {
        if (!brewTransaction(player, brew.ingredients(), createArcanePotion(brew), brew.requiredLevel())) return;
        complete(player, 40L + brew.requiredLevel(), brew.displayName(), () -> openArcane(player));
    }

    private void distill(Player player, PoisonType poison) {
        List<Material> ingredients = new ArrayList<>(poison.ingredients());
        ingredients.add(Material.HONEY_BOTTLE);
        int potency = Math.max(1, Math.min(3, 1 + level(player) / 20));
        if (!brewTransaction(player, ingredients, createVial(poison, potency), poison.requiredLevel())) return;
        complete(player, 35L + poison.requiredLevel(), poison.displayName() + " vial",
            () -> openPoisons(player));
    }

    private void distillAntidote(Player player) {
        if (!brewTransaction(player, ANTIDOTE_INGREDIENTS, createAntidote(antidoteLore(true)), ANTIDOTE_LEVEL)) return;
        complete(player, 42L, "Universal Antidote", () -> openPoisons(player));
    }

    private boolean coatFromCursor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getClickedInventory() != player.getInventory() || event.isShiftClick()
            || !(event.isLeftClick() || event.isRightClick())
            || player.getGameMode() != org.bukkit.GameMode.SURVIVAL
            || plugin.sessions().account(player.getUniqueId()).isEmpty()) {
            return false;
        }
        Vial vial = vial(event.getCursor());
        ItemStack target = event.getCurrentItem();
        if (vial == null || target == null || target.getType().isAir()) {
            return false;
        }
        if (target.getType() == Material.ARROW) {
            event.setCancelled(true);
            int amount = Math.min(16, target.getAmount());
            consumeCursor(event);
            if (target.getAmount() == amount) {
                event.setCurrentItem(createArrows(vial.type, vial.potency, amount));
            } else {
                target.subtract(amount);
                event.setCurrentItem(target);
                give(player, createArrows(vial.type, vial.potency, amount));
            }
            coatingFeedback(player, vial, amount + " arrows");
            return true;
        }
        if (!items.coat(target, vial.type.id(), vial.potency, coatingCharges(vial.potency))) {
            return false;
        }
        event.setCancelled(true);
        event.setCurrentItem(target);
        consumeCursor(event);
        coatingFeedback(player, vial, "weapon");
        return true;
    }

    private void consumeCursor(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor().clone();
        cursor.subtract(1);
        event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
    }

    private void coatingFeedback(Player player, Vial vial, String target) {
        player.sendRichMessage("<green>Applied " + vial.type.displayName() + " " + roman(vial.potency)
            + " to " + target + ".</green>");
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.15F);
    }

    private int coatingCharges(int potency) {
        return 20 + Math.max(1, Math.min(3, potency)) * 5;
    }

    private void coatHeld(Player player) {
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL || plugin.sessions().account(player.getUniqueId()).isEmpty()) return;
        ItemStack vialItem = player.getInventory().getItemInOffHand();
        Vial vial = vial(vialItem);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (vial == null) {
            player.sendRichMessage("<red>Hold a poison vial in your off hand.</red>");
            return;
        }
        if (held.getType() == Material.ARROW) {
            int amount = Math.min(16, held.getAmount());
            held.subtract(amount);
            ItemStack arrows = createArrows(vial.type, vial.potency, amount);
            vialItem.subtract(1);
            give(player, arrows);
            player.sendRichMessage("<green>Coated " + amount + " arrows with " + vial.type.displayName() + ".</green>");
        } else if (items.coat(held, vial.type.id(), vial.potency, coatingCharges(vial.potency))) {
            vialItem.subtract(1);
            player.sendRichMessage("<green>Applied " + vial.type.displayName() + " to your weapon.</green>");
        } else {
            player.sendRichMessage("<red>Hold a weapon or ordinary arrows in your main hand.</red>");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.15F);
        openPoisons(player);
    }

    private ItemStack createArcanePotion(ArcaneBrew brew) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        Component name = miniMessage.deserialize("<light_purple><bold>" + brew.displayName()
            + "</bold></light_purple>").decoration(TextDecoration.ITALIC, false);
        meta.itemName(name);
        meta.displayName(name);
        meta.setColor(brew.color());
        brew.effects().forEach(effect -> meta.addCustomEffect(effect, true));
        meta.lore(List.of(
            miniMessage.deserialize("<gray>" + brew.description() + "</gray>")
                .decoration(TextDecoration.ITALIC, false),
            miniMessage.deserialize("<gray>Duration</gray> <white>" + duration(brew.durationTicks()) + "</white>")
                .decoration(TextDecoration.ITALIC, false),
            miniMessage.deserialize("<dark_gray>────────────────────</dark_gray>"),
            miniMessage.deserialize("<light_purple>Drink to awaken the mixture.</light_purple>")
                .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(arcaneBrewKey, PersistentDataType.STRING, brew.id());
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack arcaneDisplay(ArcaneBrew brew) {
        ItemStack item = createArcanePotion(brew);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.lore(arcaneLore(brew, true).stream().map(line -> miniMessage.deserialize(line)
            .decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createVial(PoisonType poison, int potency) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        meta.setColor(poison.color());
        poison.effects(potency, 0.0D).forEach(effect -> meta.addCustomEffect(effect, true));
        Component name = miniMessage.deserialize("<green><bold>" + poison.displayName() + " " + roman(potency)
            + "</bold></green>").decoration(TextDecoration.ITALIC, false);
        meta.itemName(name);
        meta.displayName(name);
        meta.lore(List.of(
            miniMessage.deserialize("<gray>" + poison.description() + "</gray>")
                .decoration(TextDecoration.ITALIC, false),
            miniMessage.deserialize("<gray>Duration</gray> <white>"
                + duration(poison.effects(potency, 0.0D).stream().mapToInt(PotionEffect::getDuration)
                    .max().orElse(0)) + "</white>").decoration(TextDecoration.ITALIC, false),
            miniMessage.deserialize("<dark_gray>────────────────────</dark_gray>"),
            miniMessage.deserialize("<green>Drink, or drag onto a weapon or arrows.</green>")
                .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(vialTypeKey, PersistentDataType.STRING, poison.id());
        meta.getPersistentDataContainer().set(vialPotencyKey, PersistentDataType.INTEGER, potency);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack poisonDisplay(PoisonType poison, int potency, List<String> lore) {
        ItemStack item = createVial(poison, potency);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line)
            .decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAntidote(List<String> lore) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        meta.setColor(org.bukkit.Color.fromRGB(232, 207, 116));
        Component name = miniMessage.deserialize("<gold><bold>Universal Antidote</bold></gold>")
            .decoration(TextDecoration.ITALIC, false);
        meta.itemName(name);
        meta.displayName(name);
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line)
            .decoration(TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(antidoteKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArrows(PoisonType poison, int potency, int amount) {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW, amount);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        Component name = miniMessage.deserialize("<green>" + poison.displayName() + " Arrow " + roman(potency)
            + "</green>").decoration(TextDecoration.ITALIC, false);
        meta.itemName(name);
        meta.displayName(name);
        meta.setColor(poison.color());
        for (PotionEffect effect : poison.effects(potency, 0.0D)) {
            meta.addCustomEffect(effect, true);
        }
        meta.getPersistentDataContainer().set(vialTypeKey, PersistentDataType.STRING, poison.id());
        meta.getPersistentDataContainer().set(vialPotencyKey, PersistentDataType.INTEGER, potency);
        item.setItemMeta(meta);
        return item;
    }

    private Vial vial(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return null;
        }
        return poisonIdentity(item);
    }

    private Vial poisonIdentity(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(vialTypeKey, PersistentDataType.STRING);
        Integer potency = item.getItemMeta().getPersistentDataContainer().get(vialPotencyKey, PersistentDataType.INTEGER);
        PoisonType type = id == null ? null : PoisonType.fromId(id);
        return type == null || potency == null ? null : new Vial(type, Math.max(1, Math.min(3, potency)));
    }

    private Vial projectilePoison(Projectile projectile) {
        String id = projectile.getPersistentDataContainer().get(vialTypeKey, PersistentDataType.STRING);
        Integer potency = projectile.getPersistentDataContainer().get(vialPotencyKey, PersistentDataType.INTEGER);
        PoisonType type = id == null ? null : PoisonType.fromId(id);
        if (type != null && potency != null) {
            return new Vial(type, Math.max(1, Math.min(3, potency)));
        }
        return projectile instanceof AbstractArrow arrow ? poisonIdentity(arrow.getItemStack()) : null;
    }

    private boolean isAntidote(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(antidoteKey, PersistentDataType.BYTE);
    }

    private ArcaneBrew arcaneBrew(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return ArcaneBrew.fromId(item.getItemMeta().getPersistentDataContainer()
            .get(arcaneBrewKey, PersistentDataType.STRING));
    }

    private void applyScale(Player player, ArcaneBrew brew) {
        long expiresAt = System.currentTimeMillis() + brew.durationTicks() * 50L;
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(scaleAmountKey, PersistentDataType.DOUBLE, brew.scaleModifier());
        data.set(scaleExpiresKey, PersistentDataType.LONG, expiresAt);
        installScale(player, brew.scaleModifier(), expiresAt);
        String change = brew.scaleModifier() > 0.0D ? "larger" : "smaller";
        player.sendActionBar(miniMessage.deserialize("<light_purple>✦ " + brew.displayName()
            + "</light_purple> <gray>You feel " + change + ".</gray>"));
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0.0D, 1.0D, 0.0D),
            24, 0.45D, 0.7D, 0.45D, 0.02D);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9F,
            brew.scaleModifier() > 0.0D ? 0.75F : 1.35F);
    }

    private void restoreScale(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Double amount = data.get(scaleAmountKey, PersistentDataType.DOUBLE);
        Long expiresAt = data.get(scaleExpiresKey, PersistentDataType.LONG);
        if (amount == null || expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            clearScale(player);
            return;
        }
        installScale(player, amount, expiresAt);
    }

    private void installScale(Player player, double amount, long expiresAt) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            clearScale(player);
            return;
        }
        scale.removeModifier(scaleModifierKey);
        scale.addTransientModifier(new AttributeModifier(scaleModifierKey, amount,
            AttributeModifier.Operation.ADD_SCALAR));
        BukkitTask previous = scaleTasks.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        long delay = Math.max(1L, (expiresAt - System.currentTimeMillis() + 49L) / 50L + 1L);
        scaleTasks.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long currentExpiry = player.getPersistentDataContainer().get(scaleExpiresKey, PersistentDataType.LONG);
            if (currentExpiry != null && currentExpiry <= System.currentTimeMillis()) {
                clearScale(player);
            }
        }, delay));
    }

    private void clearScale(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.removeModifier(scaleModifierKey);
        }
        player.getPersistentDataContainer().remove(scaleAmountKey);
        player.getPersistentDataContainer().remove(scaleExpiresKey);
        BukkitTask task = scaleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void consumeAntidote(Player player) {
        PoisonType.antidoteEffectTypes().forEach(player::removePotionEffect);
        player.setFreezeTicks(0);
        player.getPersistentDataContainer().remove(activePoisonTypesKey);
        player.sendActionBar(miniMessage.deserialize("<gold>✦ Antidote</gold> <gray>All known toxins cleared.</gray>"));
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
            player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.55D, 0.35D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65F, 1.55F);
    }

    private void markActivePoison(LivingEntity target, PoisonType poison) {
        PersistentDataContainer data = target.getPersistentDataContainer();
        String stored = data.get(activePoisonTypesKey, PersistentDataType.STRING);
        List<String> active = stored == null || stored.isBlank()
            ? new ArrayList<>() : new ArrayList<>(Arrays.asList(stored.split(",")));
        if (!active.contains(poison.id())) {
            active.add(poison.id());
        }
        data.set(activePoisonTypesKey, PersistentDataType.STRING, String.join(",", active));
    }

    private boolean brewTransaction(Player player, List<Material> ingredients, ItemStack output, int requiredLevel) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Brewing must run on the server thread");
        if (!player.isOnline() || player.getGameMode() != org.bukkit.GameMode.SURVIVAL
            || plugin.sessions().account(player.getUniqueId()).isEmpty() || level(player) < requiredLevel) {
            Chat.error(player, "This recipe needs Alchemy " + requiredLevel + ", Survival mode and a loaded account.");
            return false;
        }
        ItemStack[] original = player.getInventory().getStorageContents();
        List<BrewingInventoryPlan.Stack<ItemStack>> snapshot = new ArrayList<>();
        int inventoryLimit = player.getInventory().getMaxStackSize();
        for (ItemStack stack : original) {
            if (stack == null || stack.getType().isAir()) { snapshot.add(null); continue; }
            int limit = Math.min(inventoryLimit, stack.getMaxStackSize());
            if (stack.getAmount() < 1 || stack.getAmount() > limit) {
                Chat.error(player, "An inventory stack is over its limit. Split it before brewing."); return false;
            }
            snapshot.add(new BrewingInventoryPlan.Stack<>(one(stack), stack.getAmount(), limit));
        }
        Map<ItemStack, Integer> needed = new java.util.LinkedHashMap<>();
        Map<Material, Integer> containers = new EnumMap<>(Material.class);
        for (Material ingredient : ingredients) {
            needed.merge(new ItemStack(ingredient), 1, Integer::sum);
            Material container = switch (ingredient) {
                case HONEY_BOTTLE, DRAGON_BREATH -> Material.GLASS_BOTTLE;
                case MILK_BUCKET -> Material.BUCKET;
                default -> null;
            };
            if (container != null) containers.merge(container, 1, Integer::sum);
        }
        List<BrewingInventoryPlan.Stack<ItemStack>> results = new ArrayList<>();
        results.add(new BrewingInventoryPlan.Stack<>(one(output), output.getAmount(), Math.min(inventoryLimit, output.getMaxStackSize())));
        containers.forEach((material, amount) -> results.add(new BrewingInventoryPlan.Stack<>(new ItemStack(material), amount,
            Math.min(inventoryLimit, material.getMaxStackSize()))));
        var prepared = BrewingInventoryPlan.prepare(snapshot, needed, results);
        if (prepared.isEmpty()) {
            Chat.error(player, "You need the listed ingredients and room for the result and returned bottles. Nothing was used.");
            return false;
        }
        double savingChance=plugin.presentation().progression(player).talentStat(Skill.ALCHEMY,"reagent-save-percent");
        Material saved=ReagentSavings.choose(ingredients,savingChance,java.util.concurrent.ThreadLocalRandom.current().nextDouble()).orElse(null);
        if(saved!=null) {
            var reduced=new java.util.LinkedHashMap<>(needed);
            ItemStack reagent=new ItemStack(saved);int amount=reduced.getOrDefault(reagent,0);
            if(amount<=1) reduced.remove(reagent);else reduced.put(reagent,amount-1);
            prepared=BrewingInventoryPlan.prepare(snapshot,reduced,results);
            if(prepared.isEmpty()) {
                Chat.error(player,"Make room to keep the saved reagent alongside your brew. Nothing was used.");return false;
            }
        }
        ItemStack[] next = new ItemStack[original.length];
        for (int slot = 0; slot < next.length; slot++) {
            var stack = prepared.get().get(slot);
            if (stack != null) { next[slot] = stack.kind().clone(); next[slot].setAmount(stack.amount()); }
        }
        player.getInventory().setStorageContents(next);
        if(saved!=null) Chat.info(player,"Careful Measure saved one "+pretty(saved)+".");
        return true;
    }

    private static ItemStack one(ItemStack item) { ItemStack copy = item.clone(); copy.setAmount(1); return copy; }

    private void missing(Player player, List<Material> ingredients) {
        player.sendRichMessage("<red>Missing ingredients:</red> <gray>" + String.join(", ", ingredients.stream()
            .map(this::pretty).toList()) + "</gray>");
    }

    private void complete(Player player, long experience, String result, Runnable reopen) {
        skills.awardActivity(player, Skill.ALCHEMY, experience, "alchemy-menu:" + UUID.randomUUID());
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.0F);
        player.sendRichMessage("<green>Created " + result + ".</green>");
        reopen.run();
    }

    private int level(Player player) {
        return plugin.presentation().progression(player).skills()
            .getOrDefault(Skill.ALCHEMY, new net.nerdsmp.api.service.ProgressionService.SkillProgress(0L, 0)).level();
    }

    private List<Material> ingredients(
        TraditionalPotion recipe,
        PotionVariant variant,
        PotionForm form
    ) {
        List<Material> ingredients = new ArrayList<>(recipe.ingredients());
        if (variant.reagent() != null) {
            ingredients.add(variant.reagent());
        }
        ingredients.addAll(form.reagents());
        return List.copyOf(ingredients);
    }

    private List<String> traditionalLore(TraditionalPotion recipe, boolean unlocked) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Alchemy " + recipe.requiredLevel() + "</gray>");
        lore.add("<dark_gray>────────────────────</dark_gray>");
        lore.addAll(recipeLore(recipe.ingredients(), recipe.requiredLevel(), unlocked));
        if (unlocked) {
            lore.add("<dark_gray>Open for bottle and strength options.</dark_gray>");
        }
        return lore;
    }

    private List<String> recipeLore(List<Material> ingredients, int requiredLevel, boolean unlocked) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Ingredients</gray>");
        ingredients.forEach(material -> lore.add("<white>• " + pretty(material) + "</white>"));
        lore.add("");
        lore.add(unlocked ? "<yellow>Click to review ingredients and result.</yellow>" : "<red>Requires Alchemy " + requiredLevel + ".</red>");
        return lore;
    }

    private List<String> arcaneLore(ArcaneBrew brew, boolean unlocked) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + brew.description() + "</gray>");
        lore.add("<gray>Duration</gray> <white>" + duration(brew.durationTicks()) + "</white>");
        lore.add("<dark_gray>────────────────────</dark_gray>");
        lore.addAll(recipeLore(brew.ingredients(), brew.requiredLevel(), unlocked));
        return lore;
    }

    private List<String> poisonLore(PoisonType poison, boolean unlocked, int potency) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + poison.description() + "</gray>");
        lore.add("<gray>Potency</gray> <green>" + roman(potency) + "</green>");
        for (PotionEffect effect : poison.effects(potency, 0.0D)) {
            lore.add("<dark_gray>•</dark_gray> <white>" + prettyEffect(effect)
                + " " + roman(effect.getAmplifier() + 1) + "</white> <gray>" + duration(effect.getDuration())
                + "</gray>");
        }
        lore.add("<dark_gray>────────────────────</dark_gray>");
        List<Material> ingredients = new ArrayList<>(poison.ingredients());
        ingredients.add(Material.HONEY_BOTTLE);
        lore.addAll(recipeLore(ingredients, poison.requiredLevel(), unlocked));
        return lore;
    }

    private List<String> antidoteLore(boolean unlocked) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Clears every toxin made at this workbench.</gray>");
        lore.add("<gray>Also clears the lingering freeze from Frostbite.</gray>");
        lore.add("<dark_gray>────────────────────</dark_gray>");
        lore.addAll(recipeLore(ANTIDOTE_INGREDIENTS, ANTIDOTE_LEVEL, unlocked));
        return lore;
    }

    private void addCoatingButton(AlchemyHolder holder, int slot) {
        set(holder, slot, icon(Material.IRON_SWORD, "<green>Apply Poison</green>", List.of(
            "<gray>Weapon or arrows in your main hand.</gray>",
            "<gray>Poison vial in your off hand.</gray>",
            "<yellow>Click here, or drag a vial onto the item.</yellow>"
        )), this::coatHeld);
    }

    private void addBackButton(AlchemyHolder holder) {
        set(holder, net.nerdsmp.paper.gui.StationLayout.BACK, MenuKit.back("Alchemy"), this::open);
        set(holder, net.nerdsmp.paper.gui.StationLayout.CLOSE, MenuKit.close(), Player::closeInventory);
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private AlchemyHolder menu(Player player, String title) {
        AlchemyHolder holder = new AlchemyHolder(player.getUniqueId(), plugin.sessions().account(player.getUniqueId()).orElse(null));
        holder.inventory = Bukkit.createInventory(holder, 54, miniMessage.deserialize(title));
        decorate(holder.inventory);
        return holder;
    }

    private void decorate(Inventory inventory) {
        MenuKit.frame(inventory);
    }

    private void set(AlchemyHolder holder, int slot, ItemStack item, Consumer<Player> action) {
        holder.inventory.setItem(slot, item);
        holder.actions.put(slot, action);
    }

    private ItemStack potionIcon(
        Material material,
        PotionType type,
        String name,
        List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        style(meta, name, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        style(meta, name, lore);
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void style(ItemMeta meta, String name, List<String> lore) {
        Component resolvedName = miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false);
        meta.itemName(resolvedName);
        meta.displayName(resolvedName);
        meta.lore(lore.stream().map(line -> miniMessage.deserialize(line)
            .decoration(TextDecoration.ITALIC, false)).toList());
    }

    private String pretty(Material material) {
        String value = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String prettyEffect(PotionEffect effect) {
        String value = effect.getType().getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String duration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(value);
        };
    }

    private record Vial(PoisonType type, int potency) {
    }

    private static final class AlchemyHolder implements InventoryHolder {
        private final UUID owner;
        private final net.nerdsmp.api.model.AccountId account;
        private final Map<Integer, Consumer<Player>> actions = new java.util.HashMap<>();
        private Inventory inventory;

        private AlchemyHolder(UUID owner, net.nerdsmp.api.model.AccountId account) { this.owner = owner; this.account = account; }
        private boolean busy;
        private boolean current(Player player, NerdSmpPlugin plugin) {
            return account != null && player.isOnline() && owner.equals(player.getUniqueId())
                && Bukkit.getPlayer(owner) == player && plugin.sessions().account(owner).filter(account::equals).isPresent();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
