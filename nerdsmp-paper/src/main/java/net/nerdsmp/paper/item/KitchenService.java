package net.nerdsmp.paper.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.*;

/** Vanilla crafting transactions, a recipe journal and level-scaled, tradeable meals. */
final class KitchenService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private final CustomRecipeService recipes;
    private final Map<NamespacedKey, HarvestDish> dishes;
    private final NamespacedKey dishKey, schemaKey, levelKey, durationKey, nutritionKey;

    KitchenService(NerdSmpPlugin plugin, PaperItemService items, CustomRecipeService recipes) {
        this.plugin = plugin; this.items = items; this.recipes = recipes;
        dishKey = new NamespacedKey(plugin, "harvest_dish"); schemaKey = new NamespacedKey(plugin, "harvest_dish_schema");
        levelKey = new NamespacedKey(plugin, "meal_chef_level"); durationKey = new NamespacedKey(plugin, "meal_seconds"); nutritionKey = new NamespacedKey(plugin, "meal_nutrition");
        Map<NamespacedKey, HarvestDish> loaded = new LinkedHashMap<>();
        HarvestDish.load(RecipeCatalogFiles.load(plugin, "content/cooking.yml", "meals", 2))
            .forEach(dish -> loaded.put(new NamespacedKey(plugin, "meal_" + dish.id()), dish));
        dishes = Map.copyOf(loaded);
    }
    int mealCount() { return dishes.size(); }
    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        dishes.forEach((key, dish) -> {
            plugin.getServer().removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, create(dish, null)); recipe.shape(dish.shape().toArray(String[]::new));
            dish.ingredients().forEach((symbol, material) -> recipe.setIngredient(symbol, new RecipeChoice.MaterialChoice(material)));
            if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Could not register meal " + key);
        });
    }
    void open(Player player, boolean cooking, int requestedPage) {
        List<Entry> entries = entries(cooking, player);
        int page = Math.clamp(requestedPage, 0, Math.max(0, (entries.size() - 1) / 28));
        int pages = Math.max(1, (entries.size() + 27) / 28);
        Menu menu = menu(player, "<gold><bold>" + title(cooking) + "</bold></gold>");
        menu.inventory.setItem(2, icon(Material.ANVIL, "<gold>Workshop Recipes</gold>", tab(!cooking, "<gray>Tools, weapons and armor.</gray>")));
        menu.inventory.setItem(6, icon(Material.BREAD, "<gold>Harvest Kitchen</gold>", tab(cooking, "<gray>Meals made from your harvest.</gray>")));
        if (cooking) menu.actions.put(2, p -> open(p, false, 0)); else menu.actions.put(6, p -> open(p, true, 0));
        for (int i = 0; i < 28 && page * 28 + i < entries.size(); i++) {
            Entry entry = entries.get(page * 28 + i); ItemStack display = entry.result().clone(); var meta = display.getItemMeta();
            List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
            lore.add(Component.empty());
            lore.add(line(entry.skill().displayName() + " " + entry.level(), unlocked(player, entry.skill(), entry.level()) ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.empty());
            lore.add(Chat.render(MenuKit.hint("see the crafting pattern")).decoration(TextDecoration.ITALIC, false)); meta.lore(lore); display.setItemMeta(meta);
            int slot = 10 + i / 7 * 9 + i % 7; menu.inventory.setItem(slot, display);
            menu.actions.put(slot, p -> pattern(p, entry, cooking, page));
        }
        if (cooking) menu.inventory.setItem(4, icon(Material.CRAFTING_TABLE, "<gold><bold>Cooking " + chefLevel(player) + "</bold></gold>", List.of(
            "<gray>Craft the recipes at a table to earn Cooking XP.</gray>", "<gray>Skilled cooks prepare longer-lasting meals.</gray>",
            "<gray>Eating never awards XP. Meals can be traded.</gray>")));
        else menu.inventory.setItem(4, icon(Material.CRAFTING_TABLE, "<gold><bold>Workshop Recipes</bold></gold>", List.of(
            "<gray>Craft these at a table once your skill is high enough.</gray>", MenuKit.pageLine(page, pages))));
        if (page > 0) { menu.inventory.setItem(MenuKit.previousSlot(54), MenuKit.previous(page, pages)); menu.actions.put(MenuKit.previousSlot(54), p -> open(p, cooking, page - 1)); }
        menu.inventory.setItem(MenuKit.backSlot(54), MenuKit.back("Workshop"));
        menu.actions.put(MenuKit.backSlot(54), p -> plugin.menus().open(p, MenuManager.View.WORKSHOP));
        menu.inventory.setItem(MenuKit.closeSlot(54), MenuKit.close()); menu.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
        if ((page + 1) * 28 < entries.size()) { menu.inventory.setItem(MenuKit.nextSlot(54), MenuKit.next(page, pages)); menu.actions.put(MenuKit.nextSlot(54), p -> open(p, cooking, page + 1)); }
        player.openInventory(menu.inventory);
    }
    private void pattern(Player player, Entry entry, boolean cooking, int page) {
        Menu menu = menu(player, "<gold><bold>Crafting Pattern</bold></gold>");
        for (int row = 0; row < entry.shape().size(); row++) for (int col = 0; col < entry.shape().get(row).length(); col++) {
            Material material = entry.ingredients().get(entry.shape().get(row).charAt(col));
            if (material != null) menu.inventory.setItem(10 + row * 9 + col, new ItemStack(material));
        }
        menu.inventory.setItem(24, entry.result().clone());
        menu.inventory.setItem(40, icon(Material.CRAFTING_TABLE, "<gold>" + entry.skill().displayName() + " " + entry.level() + "</gold>", List.of(
            "<gray>Use ordinary, unmodified ingredients.</gray>", "<gray>Arrange them as shown at a crafting table.</gray>",
            "<gray>Skill-gated recipes cannot be automated.</gray>")));
        menu.inventory.setItem(MenuKit.backSlot(54), MenuKit.back(title(cooking))); menu.actions.put(MenuKit.backSlot(54), p -> open(p, cooking, page));
        menu.inventory.setItem(MenuKit.closeSlot(54), MenuKit.close()); menu.actions.put(MenuKit.closeSlot(54), Player::closeInventory);
        player.openInventory(menu.inventory);
    }
    private List<Entry> entries(boolean cooking, Player player) {
        if (cooking) return dishes.values().stream().sorted(Comparator.comparingInt(HarvestDish::level).thenComparing(HarvestDish::id))
            .map(d -> new Entry(create(d, player), Skill.COOKING, d.level(), d.shape(), d.ingredients())).toList();
        return recipes.recipes().stream().map(d -> new Entry(items.create(d.result()), d.skill(), d.requiredLevel(), d.shape(), d.ingredients())).toList();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        HarvestDish dish = dish(event.getRecipe()); if (dish == null) return;
        if (!(event.getView().getPlayer() instanceof Player player) || !unlocked(player, Skill.COOKING, dish.level())
            || !CustomRecipeService.ordinaryIngredients(event.getInventory().getMatrix())) { event.getInventory().setResult(null); return; }
        ItemStack nativeResult = event.getInventory().getResult();
        ItemStack result = create(dish, player, dish.id().startsWith("staple_") && nativeResult != null ? nativeResult : new ItemStack(dish.material()));
        result.setAmount(event.getRecipe().getResult().getAmount()); event.getInventory().setResult(result);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        HarvestDish dish = dish(event.getRecipe()); if (dish == null) return;
        if (!(event.getWhoClicked() instanceof Player player) || !unlocked(player, Skill.COOKING, dish.level())
            || !CustomRecipeService.ordinaryIngredients(event.getInventory().getMatrix())) {
            event.setCancelled(true); Chat.error(event.getWhoClicked(), "This meal needs Cooking " + dish.level() + " and ordinary ingredients.");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void cooked(CraftItemEvent event) {
        HarvestDish dish = dish(event.getRecipe());
        if (dish == null || !(event.getWhoClicked() instanceof Player player) || player.getGameMode() != GameMode.SURVIVAL
            || !unlocked(player, Skill.COOKING, dish.level()) || event.getCurrentItem() == null) return;
        // Decompressing honey/kelp/melon and recompressing it is not cooking work.
        if (dish.id().startsWith("staple_") && !STAPLE_WORK.contains(dish.material())) return;
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null); if (account == null) return;
        CraftingReceipt receipt = new CraftingReceipt(event.getInventory(), player, event.getCurrentItem(), event.isShiftClick());
        String operation = "cook:" + UUID.randomUUID();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !player.isOnline() || player.getGameMode() != GameMode.SURVIVAL
                || !plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()) return;
            int crafts = receipt.crafts(event.getInventory(), player); if (crafts <= 0) return;
            long perCraft = dish.id().startsWith("staple_") ? 12 : MealRules.craftXp(dish.level());
            plugin.skills().awardActivity(player, Skill.COOKING, Math.min(5_000, crafts * perCraft), operation);
            plugin.achievements().record(player, net.nerdsmp.paper.achievement.AchievementMetric.MEALS_PREPARED, crafts);
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extract(FurnaceExtractEvent event) {
        if (event.getItemAmount() <= 0 || !COOKED_STAPLES.contains(event.getItemType()) || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        plugin.skills().awardActivity(event.getPlayer(), Skill.COOKING, Math.min(768, 6L * event.getItemAmount()), "cooked-extract:" + UUID.randomUUID());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automate(CrafterCraftEvent event) {
        if (dishes.containsKey(event.getRecipe().getKey())) { event.setCancelled(true); return; }
        if (event.getRecipe().getKey().getNamespace().equals("minecraft") && event.getResult().getType().isEdible()) {
            ItemStack result = create(staple(event.getResult().getType()), null, event.getResult()); result.setAmount(event.getResult().getAmount()); event.setResult(result);
        }
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId()) || event.getClick() != ClickType.LEFT) return;
        var action = menu.actions.get(event.getRawSlot());
        if (action != null) MenuKit.clickSound(player);
        if (action != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory) action.accept(player);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem(); if (!item.hasItemMeta() || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        var data = item.getItemMeta().getPersistentDataContainer();
        int schema = data.getOrDefault(schemaKey, PersistentDataType.INTEGER, 0); if (schema != 1 && schema != 2) return;
        String id = data.get(dishKey, PersistentDataType.STRING);
        HarvestDish dish = dishes.values().stream().filter(d -> d.id().equals(id) && d.material() == item.getType()).findFirst().orElse(null);
        if (dish == null && isStaple(item)) dish = staple(item.getType());
        AccountId account = plugin.sessions().account(event.getPlayer().getUniqueId()).orElse(null);
        if (dish == null || account == null) return;
        HarvestDish meal = dish;
        int chef = Math.clamp(data.getOrDefault(levelKey, PersistentDataType.INTEGER, 0), 0, 50);
        int seconds = Math.clamp(data.getOrDefault(durationKey, PersistentDataType.INTEGER, meal.seconds()), 5, 300);
        int nutrition = Math.clamp(data.getOrDefault(nutritionKey, PersistentDataType.INTEGER, 0), 0, 30);
        float saturationBefore = event.getPlayer().getSaturation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (event.isCancelled() || !player.isOnline() || player.isDead() || player.getGameMode() != GameMode.SURVIVAL
                || Bukkit.getPlayer(player.getUniqueId()) != player || plugin.sessions().account(player.getUniqueId()).filter(account::equals).isEmpty()) return;
            PotionEffectType effect = PotionEffectType.getByName(meal.effect().toUpperCase(Locale.ROOT));
            if (effect != null) player.addPotionEffect(new PotionEffect(effect, seconds * 20, MealRules.amplifier(meal.effect(), chef)));
            if (nutrition > 0) player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + Math.max(0, player.getSaturation() - saturationBefore) * nutrition / 100f));
        });
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer(); if (!player.isOnline()) return;
            dishes.forEach((key, dish) -> { if (unlocked(player, Skill.COOKING, dish.level())) player.discoverRecipe(key); });
        }, 40L);
    }
    private boolean unlocked(Player player, Skill skill, int level) {
        if (player.getGameMode() != GameMode.SURVIVAL || plugin.sessions().account(player.getUniqueId()).isEmpty()) return false;
        var progress = plugin.presentation().progression(player).skills().get(skill); return progress != null && Math.max(1, progress.level()) >= level;
    }
    private HarvestDish dish(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return null;
        HarvestDish known = dishes.get(keyed.getKey());
        if (known != null) return known;
        return keyed.getKey().getNamespace().equals("minecraft") && recipe.getResult().getType().isEdible() ? staple(recipe.getResult().getType()) : null;
    }
    private int chefLevel(Player player) { return plugin.presentation().progression(player).skills().get(Skill.COOKING).level(); }
    private ItemStack create(HarvestDish dish, Player chef) {
        return create(dish, chef, new ItemStack(dish.material()));
    }
    private ItemStack create(HarvestDish dish, Player chef, ItemStack base) {
        int level = chef == null ? 0 : chefLevel(chef);
        double training = chef == null ? 0 : plugin.presentation().progression(chef).talentStat(Skill.COOKING, "meal-duration-percent");
        int nutrition = chef == null ? 0 : (int) plugin.presentation().progression(chef).talentStat(Skill.COOKING, "meal-nutrition-percent");
        double island = 0;
        if (chef != null && plugin.skyblock() != null) {
            var owner = plugin.skyblock().activityIsland(chef);
            if (owner != null) island = 10 * plugin.skyblock().development().progress(owner).rank(net.nerdsmp.paper.skyblock.IslandTalent.HEARTH);
        }
        int seconds = MealRules.seconds(dish.seconds(), level, training, island);
        ItemStack item = base.clone(); var meta = item.getItemMeta();
        meta.itemName(line(dish.name(), NamedTextColor.GOLD)); meta.displayName(line(dish.name(), NamedTextColor.GOLD));
        meta.lore(List.of(line("------------------------", NamedTextColor.DARK_GRAY), line("HARVEST MEAL", NamedTextColor.GREEN),
            line(dish.effect().replace('_', ' ') + (MealRules.amplifier(dish.effect(), level) == 1 ? " II | " : " I | ") + seconds + "s", NamedTextColor.WHITE),
            line("Prepared at Cooking " + level + ". Same effects do not stack.", NamedTextColor.GRAY),
            line("Higher Cooking makes longer-lasting meals.", NamedTextColor.GRAY), line("------------------------", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(dishKey, PersistentDataType.STRING, dish.id());
        meta.getPersistentDataContainer().set(schemaKey, PersistentDataType.INTEGER, 2);
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(durationKey, PersistentDataType.INTEGER, seconds);
        meta.getPersistentDataContainer().set(nutritionKey, PersistentDataType.INTEGER, nutrition);
        item.setItemMeta(meta); return item;
    }
    private static final Set<Material> COOKED_STAPLES = Set.of(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
        Material.COOKED_RABBIT, Material.COOKED_MUTTON, Material.COOKED_COD, Material.COOKED_SALMON, Material.BAKED_POTATO, Material.DRIED_KELP);
    private static final Set<Material> STAPLE_WORK = Set.of(Material.BREAD, Material.COOKIE, Material.PUMPKIN_PIE, Material.MUSHROOM_STEW,
        Material.RABBIT_STEW, Material.BEETROOT_SOUP, Material.SUSPICIOUS_STEW, Material.GOLDEN_CARROT, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE);
    private static HarvestDish staple(Material material) {
        String effect = material == Material.MUSHROOM_STEW || material == Material.RABBIT_STEW || material == Material.BEETROOT_SOUP ? "regeneration" : "speed";
        return new HarvestDish("staple_" + material.name().toLowerCase(Locale.ROOT), net.nerdsmp.paper.presentation.VisualFormat.titleCase(material.name().toLowerCase(Locale.ROOT)), material,
            1, List.of("X"), Map.of('X', material), effect, effect.equals("regeneration") ? 5 : 15);
    }
    static boolean isStaple(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        var key = new NamespacedKey("nerdsmp", "harvest_dish");
        return ("staple_" + item.getType().name().toLowerCase(Locale.ROOT)).equals(pdc.get(key, PersistentDataType.STRING))
            && Integer.valueOf(2).equals(pdc.get(new NamespacedKey("nerdsmp", "harvest_dish_schema"), PersistentDataType.INTEGER))
            && pdc.getKeys().stream().allMatch(k -> k.getNamespace().equals("nerdsmp") && Set.of("harvest_dish", "harvest_dish_schema", "meal_chef_level", "meal_seconds", "meal_nutrition").contains(k.getKey()));
    }
    private static String title(boolean cooking) { return cooking ? "Harvest Kitchen" : "Workshop Recipes"; }
    private static List<String> tab(boolean showing, String description) {
        return showing ? List.of(description, "", "<green>Showing now.</green>") : MenuKit.withHint(List.of(description), "switch");
    }
    private Menu menu(Player player, String title) {
        Menu menu = new Menu(player.getUniqueId()); menu.inventory = Bukkit.createInventory(menu, 54, Chat.render(title));
        MenuKit.frame(menu.inventory); return menu;
    }
    private static ItemStack icon(Material material, String name, List<String> lore) {
        return MenuKit.item(material, name, lore);
    }
    private static Component line(String text, NamedTextColor color) { return Component.text(text, color).decoration(TextDecoration.ITALIC, false); }
    @Override public void close() { HandlerList.unregisterAll(this); dishes.keySet().forEach(plugin.getServer()::removeRecipe); }
    private record Entry(ItemStack result, Skill skill, int level, List<String> shape, Map<Character, Material> ingredients) { }
    private static final class Menu implements InventoryHolder {
        private final UUID owner; private final Map<Integer, java.util.function.Consumer<Player>> actions = new HashMap<>(); private Inventory inventory;
        private Menu(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
