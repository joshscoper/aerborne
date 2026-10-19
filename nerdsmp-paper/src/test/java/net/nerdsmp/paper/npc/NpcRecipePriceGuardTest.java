package net.nerdsmp.paper.npc;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.nerdsmp.paper.npc.NpcRecipePriceGuard.*;
import static org.junit.jupiter.api.Assertions.*;

final class NpcRecipePriceGuardTest {
    RecipeRule rule(Material output, int amount, Ingredient... ingredients) {
        return new RecipeRule(output, amount, List.of(ingredients));
    }

    @Test void bonesCannotBeBoughtConvertedAndResoldForMoreMarksAtExtremePressures() {
        double boneAsk = 4 * .76;
        var caps = sellCaps(Map.of(Material.BONE, boneAsk, Material.BONE_MEAL, 2.5 * .76), Set.of(Material.BONE_MEAL),
            List.of(rule(Material.BONE_MEAL, 3, new Ingredient(Material.BONE, 1))));
        assertEquals(boneAsk / 3 * .3, caps.get(Material.BONE_MEAL), 1E-10);
        assertTrue(caps.get(Material.BONE_MEAL) * 1.5 * 3 < boneAsk * .5);
    }

    @Test void cheapestLogRoutePropagatesThroughPlanksAndSticksRegardlessOfRuleOrder() {
        var logs = rule(Material.OAK_PLANKS, 4, new Ingredient(Material.OAK_LOG, 1));
        var sticks = rule(Material.STICK, 4, new Ingredient(Set.of(Material.OAK_PLANKS, Material.SPRUCE_PLANKS), 2));
        var asks = Map.of(Material.OAK_LOG, 4.5 * .76, Material.OAK_PLANKS, 1.5 * .76,
            Material.SPRUCE_PLANKS, 2D, Material.STICK, .75 * .76);
        var targets = Set.of(Material.OAK_PLANKS, Material.STICK);
        var forwards = sellCaps(asks, targets, List.of(logs, sticks));
        assertEquals(forwards, sellCaps(asks, targets, List.of(sticks, logs)));
        assertEquals(4.5 * .76 / 4 * .3, forwards.get(Material.OAK_PLANKS), 1E-10);
        assertEquals(4.5 * .76 / 8 * .3, forwards.get(Material.STICK), 1E-10);
        assertTrue(forwards.get(Material.STICK) * 1.5 * 8 < 4.5 * .76 * .5);
    }

    @Test void rawCopperCapsIgnoreFuelCostsRatherThanAssumingFreeSmeltingProfitsAreSafe() {
        var caps = sellCaps(Map.of(Material.RAW_COPPER, 5 * .76, Material.COPPER_INGOT, 7 * .76), Set.of(Material.COPPER_INGOT),
            List.of(rule(Material.COPPER_INGOT, 1, new Ingredient(Material.RAW_COPPER, 1))));
        assertEquals(5 * .76 * .3, caps.get(Material.COPPER_INGOT), 1E-10);
        assertTrue(caps.get(Material.COPPER_INGOT) * 1.5 < 5 * .76 * .5);
    }

    @Test void compressionCyclesDoNotInventASeparateSourceOrNerfUnrelatedResourceBuyback() {
        var compress = rule(Material.IRON_BLOCK, 1, new Ingredient(Material.IRON_INGOT, 9));
        var expand = rule(Material.IRON_INGOT, 9, new Ingredient(Material.IRON_BLOCK, 1));
        var caps = sellCaps(Map.of(Material.IRON_INGOT, 12D), Set.of(Material.IRON_INGOT, Material.IRON_BLOCK, Material.DIAMOND),
            List.of(compress, expand));
        assertFalse(caps.containsKey(Material.IRON_INGOT)); assertFalse(caps.containsKey(Material.DIAMOND));
        assertEquals(12 * 9 * .3, caps.get(Material.IRON_BLOCK), 1E-10);
        var pricedBlock = sellCaps(Map.of(Material.IRON_INGOT, 12D, Material.IRON_BLOCK, 30D), Set.of(Material.IRON_INGOT),
            List.of(expand, compress));
        assertEquals(30D / 9 * .3, pricedBlock.get(Material.IRON_INGOT), 1E-10);
    }

    @Test void everyIngredientMustHaveAPurchasableRouteAndUnknownMaterialsAreNeverFree() {
        var recipes = List.of(rule(Material.BOOK, 1, new Ingredient(Material.PAPER, 3), new Ingredient(Material.LEATHER, 1)));
        assertTrue(sellCaps(Map.of(Material.PAPER, 1D), Set.of(Material.BOOK), recipes).isEmpty());
        var complete = sellCaps(Map.of(Material.PAPER, 1D, Material.LEATHER, 4D), Set.of(Material.BOOK), recipes);
        assertEquals(7 * .3, complete.get(Material.BOOK), 1E-10);
    }

    @Test void ingredientChoicesUseTheCheapestAvailableAlternativeNotAnUnpricedChoice() {
        var recipe = rule(Material.STICK, 4, new Ingredient(Set.of(Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS), 2));
        var caps = sellCaps(Map.of(Material.OAK_PLANKS, 2D, Material.SPRUCE_PLANKS, 1D), Set.of(Material.STICK), List.of(recipe));
        assertEquals(.5 * .3, caps.get(Material.STICK), 1E-10);
    }

    @Test void aLongAdversarialGraphFailsClosedAtTheIterationLimit() {
        List<Material> nodes = Arrays.stream(Material.values())
            .filter(m -> m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR).limit(131).toList();
        List<RecipeRule> rules = new ArrayList<>();
        for (int index = 1; index < nodes.size(); index++) rules.add(rule(nodes.get(index), 1, new Ingredient(nodes.get(index - 1), 1)));
        Collections.reverse(rules); // Only one dependency becomes reachable per pass.
        var caps = sellCaps(Map.of(nodes.getFirst(), 2D), Set.of(nodes.getLast()), rules);
        assertEquals(0D, caps.get(nodes.getLast()));
    }

    @Test void immediateEnvironmentalRoutesAreImmutableAndExcludeGrowth() {
        var rules = environmentalConversions();
        assertEquals(Set.of(Material.FLINT, Material.MUD, Material.COBBLESTONE, Material.DIRT),
            rules.stream().map(RecipeRule::output).collect(java.util.stream.Collectors.toSet()));
        assertEquals(4, rules.size());
        assertThrows(UnsupportedOperationException.class, () -> rules.clear());
        var caps = sellCaps(Map.of(Material.GRAVEL, 2.5 * .76), Set.of(Material.FLINT), rules);
        assertEquals(2.5 * .76 * .3, caps.get(Material.FLINT), 1E-10);
    }

    @Test void dirtInteractionFeedsPackedMudAndMudBrickCrafting() {
        var rules = new ArrayList<>(environmentalConversions());
        rules.add(rule(Material.PACKED_MUD, 1, new Ingredient(Material.MUD, 1), new Ingredient(Material.WHEAT, 1)));
        rules.add(rule(Material.MUD_BRICKS, 4, new Ingredient(Material.PACKED_MUD, 4)));
        var caps = sellCaps(Map.of(Material.DIRT, 2 * .76, Material.WHEAT, 2 * .76), Set.of(Material.MUD_BRICKS), rules);
        assertEquals(4 * .76 * .3, caps.get(Material.MUD_BRICKS), 1E-10);
        assertTrue(caps.get(Material.MUD_BRICKS) * 1.5 < 4 * .76 * .5);
    }

    @Test void rulesAndReturnedCapsAreImmutableAndMalformedPricesAreRejected() {
        Set<Material> choices = new HashSet<>(Set.of(Material.BONE));
        var ingredient = new Ingredient(choices, 1); choices.clear();
        var ingredients = new ArrayList<>(List.of(ingredient));
        var recipe = new RecipeRule(Material.BONE_MEAL, 3, ingredients); ingredients.clear();
        assertEquals(Set.of(Material.BONE), ingredient.choices()); assertEquals(1, recipe.ingredients().size());
        var caps = sellCaps(Map.of(Material.BONE, 2D), Set.of(Material.BONE_MEAL), List.of(recipe));
        assertThrows(UnsupportedOperationException.class, () -> caps.put(Material.STONE, 1D));
        assertThrows(IllegalArgumentException.class, () -> sellCaps(Map.of(Material.BONE, Double.NaN), Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> sellCaps(Map.of(Material.BONE, 0D), Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(Set.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeRule(Material.AIR, 1, List.of(ingredient)));
        for (Material air : List.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR)) {
            assertThrows(IllegalArgumentException.class, () -> new Ingredient(air, 1));
            assertThrows(IllegalArgumentException.class, () -> new RecipeRule(air, 1, List.of(ingredient)));
            assertThrows(IllegalArgumentException.class, () -> sellCaps(Map.of(air, 1D), Set.of(), List.of()));
        }
    }
}
