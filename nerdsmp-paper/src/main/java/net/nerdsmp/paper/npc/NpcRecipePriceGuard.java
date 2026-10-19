package net.nerdsmp.paper.npc;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounds processed buybacks against the cheapest complete, purchasable ingredient route. */
final class NpcRecipePriceGuard {
    private static final int MAX_PASSES = 128;
    private static final double ROUNDING_EPSILON = 1.0E-12;
    // Inputs may be bought at 50% market rate while unrelated outputs sell at 150%.
    private static final double CONVERSION_CAP = .5 / 1.5 * .9;

    private NpcRecipePriceGuard() { }

    record Ingredient(Set<Material> choices, int units) {
        Ingredient {
            choices = Set.copyOf(choices);
            if (choices.isEmpty() || choices.stream().anyMatch(NpcRecipePriceGuard::isAir) || units < 1 || units > 64)
                throw new IllegalArgumentException("Invalid recipe ingredient");
        }
        Ingredient(Material material, int units) { this(Set.of(material), units); }
    }

    record RecipeRule(Material output, int outputUnits, List<Ingredient> ingredients) {
        RecipeRule {
            Objects.requireNonNull(output);
            ingredients = List.copyOf(ingredients);
            if (isAir(output) || outputUnits < 1 || outputUnits > 64 || ingredients.isEmpty() || ingredients.size() > 9)
                throw new IllegalArgumentException("Invalid market recipe rule");
        }
    }

    /** Capture once during primary-thread startup; the resulting graph needs no Bukkit calls. */
    static List<RecipeRule> captureVanillaRecipes() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Capture vendor recipes on the primary thread");
        Set<RecipeRule> rules = new LinkedHashSet<>(environmentalConversions());
        var recipes = Bukkit.recipeIterator();
        int count = 0;
        while (recipes.hasNext()) {
            if (++count > 20_000) throw new IllegalStateException("Vendor recipe snapshot exceeds the safety limit");
            Recipe recipe = recipes.next();
            if (!(recipe instanceof Keyed keyed) || !keyed.getKey().getNamespace().equals("minecraft")) continue;
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe
                || recipe instanceof CookingRecipe<?> || recipe instanceof StonecuttingRecipe)) continue;
            var result = recipe.getResult();
            if (isAir(result.getType()) || result.hasItemMeta() || result.getAmount() < 1 || result.getAmount() > 64) continue;
            List<RecipeChoice> choices = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                var ingredients = shaped.getChoiceMap();
                for (String row : shaped.getShape()) for (char cell : row.toCharArray()) {
                    if (cell != ' ') choices.add(ingredients.get(cell));
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) choices.addAll(shapeless.getChoiceList());
            else if (recipe instanceof CookingRecipe<?> cooking) choices.add(cooking.getInputChoice());
            else if (recipe instanceof StonecuttingRecipe cutting) choices.add(cutting.getInputChoice());
            else continue;
            List<Ingredient> ingredients = new ArrayList<>();
            boolean supported = !choices.isEmpty();
            for (RecipeChoice choice : choices) {
                // Exact choices often require metadata. Treating them as a plain material would invent a cheap route.
                if (!(choice instanceof RecipeChoice.MaterialChoice materials)) { supported = false; break; }
                Set<Material> options = new HashSet<>(materials.getChoices());
                options.removeIf(NpcRecipePriceGuard::isAir);
                if (options.isEmpty()) { supported = false; break; }
                ingredients.add(new Ingredient(options, 1));
            }
            if (supported) rules.add(new RecipeRule(result.getType(), result.getAmount(), ingredients));
        }
        return List.copyOf(rules);
    }

    /** Immediate vanilla interactions that recipeIterator does not expose; no farming or timed growth. */
    static List<RecipeRule> environmentalConversions() {
        return List.of(
            new RecipeRule(Material.FLINT, 1, List.of(new Ingredient(Material.GRAVEL, 1))),
            // Water bottles can be refilled: their reusable container is not a recurring ingredient cost.
            new RecipeRule(Material.MUD, 1, List.of(new Ingredient(Material.DIRT, 1))),
            new RecipeRule(Material.COBBLESTONE, 1, List.of(new Ingredient(Material.STONE, 1))),
            new RecipeRule(Material.DIRT, 1, List.of(new Ingredient(Material.GRASS_BLOCK, 1)))
        );
    }

    // Material.isAir() accesses Paper's runtime registry. This graph also runs in plain JVM unit tests.
    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    /** Inputs already include the strongest obtainable reputation discount, but not market pressure. */
    static Map<Material, Double> sellCaps(Map<Material, Double> cheapestAsks, Set<Material> soldMaterials,
                                          List<RecipeRule> rules) {
        Objects.requireNonNull(cheapestAsks); Objects.requireNonNull(soldMaterials); Objects.requireNonNull(rules);
        for (var entry : cheapestAsks.entrySet()) {
            if (entry.getKey() == null || isAir(entry.getKey()) || entry.getValue() == null
                || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0)
                throw new IllegalArgumentException("Invalid ingredient ask price");
        }
        Set<Material> craftable = new HashSet<>();
        rules.forEach(rule -> craftable.add(rule.output()));
        Map<Material, Double> caps = new EnumMap<>(Material.class);
        for (Material target : soldMaterials) {
            if (!craftable.contains(target)) continue;
            Map<Material, Double> costs = new EnumMap<>(Material.class);
            costs.putAll(cheapestAsks);
            // A commodity's own ask cannot seed its reverse compression recipe: pressure is shared for that route.
            costs.remove(target);
            boolean changed = false;
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                changed = false;
                for (RecipeRule rule : rules) {
                    double cost = ingredientCost(rule, costs);
                    if (!Double.isFinite(cost)) continue; // An unknown ingredient is never free.
                    double before = costs.getOrDefault(rule.output(), Double.POSITIVE_INFINITY);
                    if (cost < before && (Double.isInfinite(before) || before - cost > Math.max(1, before) * ROUNDING_EPSILON)) {
                        costs.put(rule.output(), cost); changed = true;
                    }
                }
                if (!changed) break;
            }
            Double cost = costs.get(target);
            if (changed) caps.put(target, 0D); // An unfinished closure must not leave a known conversion unprotected.
            else if (cost != null) caps.put(target, cost * CONVERSION_CAP);
        }
        return Map.copyOf(caps);
    }

    private static double ingredientCost(RecipeRule rule, Map<Material, Double> costs) {
        double total = 0;
        for (Ingredient ingredient : rule.ingredients()) {
            double lowest = ingredient.choices().stream().mapToDouble(material -> costs.getOrDefault(material, Double.POSITIVE_INFINITY))
                .min().orElse(Double.POSITIVE_INFINITY);
            if (!Double.isFinite(lowest)) return Double.POSITIVE_INFINITY;
            total += lowest * ingredient.units();
        }
        return total / rule.outputUnits();
    }
}
