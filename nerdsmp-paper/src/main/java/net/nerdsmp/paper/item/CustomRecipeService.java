package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomRecipeService implements Listener, AutoCloseable {
    private static final int SCHEMA_VERSION = 5;

    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private Map<NamespacedKey, RecipeDefinition> definitions;
    private final Map<NamespacedKey, RecipeDefinition> builtIn;
    private Map<NamespacedKey, AuthoredRecipe> authored = Map.of();
    private final KitchenService kitchen;
    private final MetallurgyCatalog metallurgy;
    private final Map<NamespacedKey, MetallurgyCatalog.Smelting> smelting = new LinkedHashMap<>();

    public CustomRecipeService(NerdSmpPlugin plugin, PaperItemService items) {
        this.plugin = plugin;
        this.items = items;
        metallurgy = MetallurgyCatalog.load(plugin, items);
        definitions = load();
        builtIn = definitions;
        kitchen = new KitchenService(plugin, items, this);
    }

    public void start() {
        kitchen.start();
        definitions.forEach((key, definition) -> {
            plugin.getServer().removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, items.create(definition.result()));
            recipe.shape(definition.shape().toArray(String[]::new));
            definition.ingredients().forEach((symbol, material) -> recipe.setIngredient(symbol,
                definition.customIngredients().isEmpty() ? new RecipeChoice.MaterialChoice(material)
                    : definition.ingredient(symbol).choice(items)));
            if (!plugin.getServer().addRecipe(recipe)) {
                throw new IllegalStateException("Could not register custom recipe " + key);
            }
        });
        for (var definition : metallurgy.smelting()) {
            RecipeIngredient input = new RecipeIngredient(items.itemMaterial(definition.input()).orElseThrow(), definition.input());
            for (boolean blasting : new boolean[]{false, true}) {
                NamespacedKey key = new NamespacedKey(plugin, "metallurgy_" + definition.id() + (blasting ? "_blasting" : "_smelting"));
                plugin.getServer().removeRecipe(key);
                Recipe recipe = blasting
                    ? new org.bukkit.inventory.BlastingRecipe(key, items.create(definition.result()), input.choice(items), definition.experience(), Math.max(1, definition.ticks() / 2))
                    : new org.bukkit.inventory.FurnaceRecipe(key, items.create(definition.result()), input.choice(items), definition.experience(), definition.ticks());
                if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Could not register metallurgy recipe " + key);
                smelting.put(key, definition);
            }
        }
        plugin.getServer().getPluginManager().registerEvents(new CraftingGuard(this), plugin);
    }

    List<MetallurgyCatalog.Smelting> smeltingRecipes() { return metallurgy.smelting(); }

    public java.util.Optional<RefiningWork> refiningWork(ItemStack source, ItemStack result) {
        return refiningOutput(result).filter(work -> new RecipeIngredient(
            items.itemMaterial(work.input()).orElseThrow(), work.input()).matches(source, items));
    }

    public java.util.Optional<RefiningWork> refiningOutput(ItemStack result) {
        return metallurgy.smelting().stream().filter(recipe -> new RecipeIngredient(
            items.itemMaterial(recipe.result()).orElseThrow(), recipe.result()).matches(result, items))
            .map(recipe -> new RefiningWork(recipe.input(), recipe.result(), recipe.smithingExperience())).findFirst();
    }

    public record RefiningWork(ItemId input, ItemId result, long experience) { }

    /** Unlock skills describe who can make the recipe; making workshop equipment trains Smithing. */
    public java.util.Optional<Skill> craftingExperienceSkill(Recipe recipe) {
        RecipeDefinition definition = definition(recipe);
        return definition == null ? java.util.Optional.empty() : java.util.Optional.of(
            definition.skill() == Skill.ALCHEMY || definition.skill() == Skill.COOKING ? definition.skill() : Skill.SMITHING);
    }
    public boolean cookingRecipe(Recipe recipe) { return kitchen.owns(recipe); }

    /** Only this registered recipe and its complete, authenticated pattern can bypass the vanilla guard. */
    boolean acceptsCustomCraft(Recipe recipe, ItemStack[] matrix) {
        RecipeDefinition definition = definition(recipe);
        return definition != null && !definition.customIngredients().isEmpty() && matchesIngredients(definition, matrix);
    }

    private boolean matchesIngredients(RecipeDefinition definition, ItemStack[] matrix) {
        return RecipePattern.matches(definition.shape(), java.util.Arrays.asList(matrix),
            stack -> stack == null || stack.getType().isAir(), (symbol, stack) -> definition.ingredient(symbol).matches(stack, items));
    }

    /** A recognized custom smelt is fully validated here; other protected items stay blocked. */
    boolean handleSmelt(org.bukkit.event.inventory.FurnaceSmeltEvent event) {
        var definition = event.getRecipe() == null ? null : smelting.get(event.getRecipe().getKey());
        // A custom ore built on a vanilla material (gloomsteel ore is raw iron) is caught by the vanilla recipe first,
        // which would make an iron ingot and so be cancelled: the metallurgy recipe for that exact item takes over.
        if (definition == null && event.getSource() != null) definition = metallurgy.smelting().stream()
            .filter(candidate -> new RecipeIngredient(items.itemMaterial(candidate.input()).orElseThrow(), candidate.input()).matches(event.getSource(), items))
            .findFirst().orElse(null);
        if (definition == null) return false;
        RecipeIngredient input = new RecipeIngredient(items.itemMaterial(definition.input()).orElseThrow(), definition.input());
        if (!input.matches(event.getSource(), items)) { event.setCancelled(true); return true; }
        ItemStack result = items.create(definition.result());
        if (!(event.getBlock().getState() instanceof org.bukkit.block.Furnace furnace)) { event.setCancelled(true); return true; }
        ItemStack before = furnace.getInventory().getResult();
        if (before != null && !before.getType().isAir()
            && (!before.isSimilar(result) || before.getAmount() >= before.getMaxStackSize())) {
            event.setCancelled(true); return true;
        }
        event.setResult(result);
        return true;
    }

    public List<RecipeDefinition> recipes() {
        return definitions.values().stream()
            .sorted((left, right) -> {
                int level = Integer.compare(left.requiredLevel(), right.requiredLevel());
                return level != 0 ? level : left.id().compareTo(right.id());
            })
            .toList();
    }

    /** Stable identifiers reserved by the read-only core catalog, independently of runtime recipe keys. */
    public java.util.Set<String> coreRecipeIds() {
        return builtIn.values().stream().map(RecipeDefinition::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void showRecipes(Player player) {
        kitchen.open(player, false, 0);
        discoverUnlocked(player);
    }
    public void showCooking(Player player) { kitchen.open(player, true, 0); }
    public int mealCount() { return kitchen.mealCount(); }

    /** Registry transaction for the authoring engine; failed registrations restore the previous recipes. */
    public void installAuthored(List<AuthoredRecipe> candidates) {
        Map<NamespacedKey, AuthoredRecipe> next = new LinkedHashMap<>();
        for (AuthoredRecipe candidate : candidates) {
            NamespacedKey key = new NamespacedKey(plugin, "engine_recipe_" + candidate.definition().id());
            if (next.put(key, candidate) != null || builtIn.containsKey(key)) throw new IllegalArgumentException("Duplicate authored recipe key.");
        }
        Map<NamespacedKey, AuthoredRecipe> previous = authored;
        for (NamespacedKey key : next.keySet()) {
            if (!previous.containsKey(key) && plugin.getServer().getRecipe(key) != null)
                throw new IllegalStateException("Authored recipe key is already registered by another owner: " + key);
        }
        try {
            authored.keySet().forEach(plugin.getServer()::removeRecipe);
            next.forEach(this::registerAuthored);
        } catch (RuntimeException failed) {
            next.keySet().forEach(plugin.getServer()::removeRecipe);
            try { previous.forEach(this::registerAuthored); }
            catch (RuntimeException rollback) { failed.addSuppressed(rollback); throw new IllegalStateException("Recipe registry could not be restored. Restart before further crafting.", failed); }
            throw failed;
        }
        authored = Map.copyOf(next);
        Map<NamespacedKey, RecipeDefinition> merged = new LinkedHashMap<>(builtIn);
        next.forEach((key, recipe) -> merged.put(key, recipe.definition()));
        definitions = Map.copyOf(merged);
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            try { discoverUnlocked(player); }
            catch (RuntimeException discovery) { plugin.getLogger().log(java.util.logging.Level.WARNING, "Authored recipe registration succeeded, but discovery could not refresh for " + player.getUniqueId(), discovery); }
        });
    }
    private void registerAuthored(NamespacedKey key, AuthoredRecipe candidate) {
        RecipeDefinition definition = candidate.definition();
        ShapedRecipe recipe = new ShapedRecipe(key, candidate.result());
        recipe.shape(definition.shape().toArray(String[]::new));
        definition.ingredients().forEach((symbol, material) -> recipe.setIngredient(symbol, new RecipeChoice.MaterialChoice(material)));
        if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Could not register authored recipe " + key);
    }
    public record AuthoredRecipe(RecipeDefinition definition, ItemStack result) {
        public AuthoredRecipe { java.util.Objects.requireNonNull(definition); result = java.util.Objects.requireNonNull(result).clone(); }
        @Override public ItemStack result() { return result.clone(); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent event) {
        RecipeDefinition definition = definition(event.getRecipe());
        if (definition == null && authoredKey(event.getRecipe())) { event.getInventory().setResult(null); return; }
        if (definition != null && event.getView().getPlayer() instanceof Player player
            && (!unlocked(player, definition) || !matchesIngredients(definition, event.getInventory().getMatrix()))) {
            event.getInventory().setResult(null);
            return;
        }
        if (definition != null) {
            ItemStack fresh = items.create(definition.result());
            if (event.getRecipe() instanceof Keyed keyed && authored.containsKey(keyed.getKey()))
                fresh.setAmount(authored.get(keyed.getKey()).result().getAmount());
            event.getInventory().setResult(fresh);
        }
        if (definition == null && !event.isRepair()) {
            ItemStack result = event.getInventory().getResult();
            if (result != null && items.isCraftedEquipment(result.getType())) {
                ItemStack styled = items.createCrafted(result.getType());
                styled.setAmount(result.getAmount());
                event.getInventory().setResult(styled);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        RecipeDefinition definition = definition(event.getRecipe());
        if (definition == null && authoredKey(event.getRecipe())) { event.setCancelled(true); return; }
        if (definition == null || !(event.getWhoClicked() instanceof Player player)
            || unlocked(player, definition) && matchesIngredients(definition, event.getInventory().getMatrix())) {
            return;
        }
        event.setCancelled(true);
        player.sendRichMessage("<red>This recipe requires " + definition.skill().displayName()
            + " level " + definition.requiredLevel() + " and the exact ingredients shown in /recipes.</red>");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                discoverUnlocked(event.getPlayer());
            }
        }, 40L);
    }

    private void discoverUnlocked(Player player) {
        smelting.keySet().forEach(player::discoverRecipe);
        definitions.forEach((key, definition) -> {
            if (unlocked(player, definition)) {
                player.discoverRecipe(key);
            }
        });
    }

    private boolean unlocked(Player player, RecipeDefinition definition) {
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL || plugin.sessions().account(player.getUniqueId()).isEmpty()) return false;
        ProgressionService.ProgressionSnapshot snapshot = plugin.presentation().progression(player);
        ProgressionService.SkillProgress progress = snapshot.skills().get(definition.skill());
        return progress != null && progress.level() >= definition.requiredLevel();
    }

    private RecipeDefinition definition(Recipe recipe) {
        return recipe instanceof Keyed keyed ? definitions.get(keyed.getKey()) : null;
    }
    private boolean authoredKey(Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
            && keyed.getKey().getKey().startsWith("engine_recipe_");
    }

    static boolean ordinaryIngredients(ItemStack[] matrix) {
        for (ItemStack item : matrix) if (item != null && !item.getType().isAir() && !FoodItems.ordinary(item) && !KitchenService.isStaple(item)) return false;
        return true;
    }
    public static boolean ordinaryIngredient(ItemStack item) {
        return item != null && !item.getType().isAir() && (FoodItems.ordinary(item) || KitchenService.isStaple(item));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedCraft(org.bukkit.event.block.CrafterCraftEvent event) {
        if (definition(event.getRecipe()) != null || authoredKey(event.getRecipe())) event.setCancelled(true);
    }

    private Map<NamespacedKey, RecipeDefinition> load() {
        YamlConfiguration yaml = RecipeCatalogFiles.load(plugin, "content/recipes.yml", "recipes", SCHEMA_VERSION);
        Map<NamespacedKey, RecipeDefinition> loaded = new LinkedHashMap<>(loadCatalog(yaml));
        YamlConfiguration expansion = RecipeCatalogFiles.load(plugin, "content/skyforge-recipes.yml", "recipes", 1);
        for (var entry : loadCatalog(expansion).entrySet()) {
            if (loaded.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new IllegalStateException("Expansion recipe collides with an existing recipe: " + entry.getKey());
        }
        for (var definition : metallurgy.crafting()) {
            NamespacedKey key = new NamespacedKey(plugin, "crafted_" + definition.id());
            if (loaded.putIfAbsent(key, definition) != null) throw new IllegalStateException("Duplicate metallurgy recipe " + key);
        }
        return Map.copyOf(loaded);
    }

    private Map<NamespacedKey, RecipeDefinition> loadCatalog(YamlConfiguration yaml) {
        ConfigurationSection recipes = yaml.getConfigurationSection("recipes");
        if (recipes == null) {
            throw new IllegalStateException("content/recipes.yml is missing the recipes section");
        }
        Map<NamespacedKey, RecipeDefinition> loaded = new LinkedHashMap<>();
        for (String id : recipes.getKeys(false)) {
            ConfigurationSection section = recipes.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            ItemId result = new ItemId(section.getString("result", id));
            if (!items.itemIds().contains(result)) {
                throw new IllegalStateException("Unknown custom recipe result: " + result);
            }
            Skill skill;
            try {
                skill = Skill.valueOf(section.getString("skill", "smithing").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid skill on custom recipe " + id, exception);
            }
            List<String> shape = new ArrayList<>(section.getStringList("shape"));
            if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.length() > 3)) {
                throw new IllegalStateException("Custom recipe " + id + " has an invalid shape");
            }
            ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
            if (ingredientSection == null) {
                throw new IllegalStateException("Custom recipe " + id + " has no ingredients");
            }
            Map<Character, Material> ingredients = new LinkedHashMap<>();
            for (String rawSymbol : ingredientSection.getKeys(false)) {
                if (rawSymbol.length() != 1) {
                    throw new IllegalStateException("Recipe ingredient keys must be one character: " + id);
                }
                Material material = Material.matchMaterial(ingredientSection.getString(rawSymbol, ""));
                if (material == null || material.isAir()) {
                    throw new IllegalStateException("Invalid ingredient on custom recipe " + id + ": " + rawSymbol);
                }
                ingredients.put(rawSymbol.charAt(0), material);
            }
            NamespacedKey key = new NamespacedKey(plugin, "crafted_" + id.toLowerCase(Locale.ROOT));
            loaded.put(key, new RecipeDefinition(
                id.toLowerCase(Locale.ROOT), result, skill,
                Math.max(1, section.getInt("level", 1)), shape, ingredients
            ));
        }
        return Map.copyOf(loaded);
    }

    private String titleCase(String raw) {
        StringBuilder result = new StringBuilder();
        for (String word : raw.split("_")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    @Override
    public void close() {
        kitchen.close();
        definitions.keySet().forEach(plugin.getServer()::removeRecipe);
        smelting.keySet().forEach(plugin.getServer()::removeRecipe);
    }

    public record RecipeDefinition(
        String id,
        ItemId result,
        Skill skill,
        int requiredLevel,
        List<String> shape,
        Map<Character, Material> ingredients,
        Map<Character, ItemId> customIngredients
    ) {
        public RecipeDefinition(String id, ItemId result, Skill skill, int requiredLevel,
                                List<String> shape, Map<Character, Material> ingredients) {
            this(id, result, skill, requiredLevel, shape, ingredients, Map.of());
        }
        public RecipeDefinition {
            if (requiredLevel < 1 || requiredLevel > 50) throw new IllegalArgumentException("Recipe level must be 1–50");
            HarvestDish.validateShape(shape, ingredients);
            shape = List.copyOf(shape);
            ingredients = Map.copyOf(ingredients);
            customIngredients = Map.copyOf(customIngredients);
            if (!ingredients.keySet().containsAll(customIngredients.keySet())) throw new IllegalArgumentException("Unknown custom ingredient symbol");
        }
        RecipeIngredient ingredient(char symbol) { return new RecipeIngredient(ingredients.get(symbol), customIngredients.get(symbol)); }
    }
}
