package net.nerdsmp.paper.skyblock.machine;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class MachineRecipesTest {
    private ArrayList<MachineRecipes.Stack> inventory() { return new ArrayList<>(Collections.nCopies(9, null)); }
    private MachineRecipes.Stack stack(Material material, int amount) { return new MachineRecipes.Stack(material, amount, true); }
    @Test void crusherConsumesOneBoundedBatchAndLeavesOriginalUntouched() {
        var slots = inventory(); slots.set(0, stack(Material.COBBLESTONE, 16)); slots.set(3, stack(Material.COAL, 2));
        var result = MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).orElseThrow();
        assertEquals(stack(Material.COBBLESTONE, 8), result.slots().get(0));
        assertEquals(stack(Material.COAL, 1), result.slots().get(3));
        assertEquals(stack(Material.GRAVEL, 8), result.slots().get(6));
        assertEquals(stack(Material.COBBLESTONE, 16), slots.get(0));
        assertThrows(UnsupportedOperationException.class, () -> result.slots().set(0, null));
    }
    @Test void ingredientAndFuelStacksCombineOnlyAcrossTheirOwnRows() {
        var slots = inventory(); slots.set(0, stack(Material.GRAVEL, 8)); slots.set(1, stack(Material.GRAVEL, 8));
        slots.set(3, stack(Material.COAL, 1)); slots.set(4, stack(Material.CHARCOAL, 1));
        var result = MachineRecipes.plan(MachineKind.SIFTER, 3, slots).orElseThrow();
        assertNull(result.slots().get(0)); assertNull(result.slots().get(1));
        assertNull(result.slots().get(3)); assertNull(result.slots().get(4));
        assertEquals(stack(Material.IRON_NUGGET, 4), result.slots().get(6));
    }
    @Test void wrongRowsAndMissingFuelDoNotProcess() {
        var slots = inventory(); slots.set(0, stack(Material.COBBLESTONE, 8)); slots.set(1, stack(Material.COAL, 1));
        assertTrue(MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).isEmpty());
        slots.set(3, stack(Material.COBBLESTONE, 8)); slots.set(0, null);
        assertTrue(MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).isEmpty());
    }
    @Test void metadataOnIngredientsOrFuelCannotBeDestroyed() {
        var slots = inventory(); slots.set(0, new MachineRecipes.Stack(Material.COBBLESTONE, 8, false));
        slots.set(3, stack(Material.COAL, 1));
        assertTrue(MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).isEmpty());
        slots.set(0, stack(Material.COBBLESTONE, 8)); slots.set(3, new MachineRecipes.Stack(Material.COAL, 1, false));
        assertTrue(MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).isEmpty());
    }
    @Test void fullOutputsRetainAllInputAndFuel() {
        var slots = inventory(); slots.set(0, stack(Material.COBBLESTONE, 8)); slots.set(3, stack(Material.COAL, 1));
        for (int slot = 6; slot < 9; slot++) slots.set(slot, stack(Material.GRAVEL, 64));
        List<MachineRecipes.Stack> before = new ArrayList<>(slots);
        assertTrue(MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).isEmpty()); assertEquals(before, slots);
    }
    @Test void partialCapacityDoesNotCommitPartialResults() {
        var slots = inventory(); slots.set(0, stack(Material.COBBLESTONE, 8)); slots.set(3, stack(Material.COAL, 1));
        slots.set(6, stack(Material.GRAVEL, 63)); slots.set(7, stack(Material.STONE, 64)); slots.set(8, stack(Material.STONE, 64));
        assertTrue(MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).isEmpty());
        assertEquals(63, slots.get(6).amount()); assertEquals(8, slots.get(0).amount());
    }
    @Test void outputsMergeBeforeUsingAnotherSlotWithoutOverwritingNamedItems() {
        var slots = inventory(); slots.set(0, stack(Material.COBBLESTONE, 8)); slots.set(3, stack(Material.COAL, 1));
        slots.set(6, new MachineRecipes.Stack(Material.GRAVEL, 1, false)); slots.set(7, stack(Material.GRAVEL, 60));
        var result = MachineRecipes.plan(MachineKind.CRUSHER, 0, slots).orElseThrow();
        assertEquals(slots.get(6), result.slots().get(6)); assertEquals(stack(Material.GRAVEL, 64), result.slots().get(7));
        assertEquals(stack(Material.GRAVEL, 4), result.slots().get(8));
    }
    @Test void sawmillProducesMatchingPlanksForNormalAndStrippedWood() {
        for (Material input : List.of(Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.WARPED_STEM)) {
            var slots = inventory(); slots.set(0, stack(input, 4)); slots.set(3, stack(Material.CHARCOAL, 1));
            var result = MachineRecipes.plan(MachineKind.SAWMILL, 2, slots).orElseThrow();
            assertEquals(24, result.slots().get(6).amount());
            assertEquals(input == Material.WARPED_STEM ? Material.WARPED_PLANKS : Material.OAK_PLANKS, result.slots().get(6).material());
        }
    }
    @Test void everyRecipeHonorsItsStageAndExactMaterialCosts() {
        for (MachineKind kind : MachineKind.values()) for (var recipe : MachineRecipes.forKind(kind)) {
            var slots = inventory(); slots.set(0, stack(recipe.input(), recipe.amount())); slots.set(3, stack(Material.COAL, recipe.fuel()));
            if (recipe.stage() > 0) assertTrue(MachineRecipes.plan(kind, recipe.stage() - 1, slots).isEmpty(), recipe.description());
            var result = MachineRecipes.plan(kind, recipe.stage(), slots).orElseThrow();
            assertNull(result.slots().get(0)); assertNull(result.slots().get(3));
            assertEquals(stack(recipe.output(), recipe.result()), result.slots().get(6));
        }
    }
    @Test void advancedMaterialsAreExplicitDeterministicAndHaveNoNetheriteOrCurrency() {
        var sifter = MachineRecipes.forKind(MachineKind.SIFTER);
        assertTrue(sifter.stream().anyMatch(r -> r.output() == Material.DIAMOND && r.stage() == 5 && r.amount() >= 32 && r.fuel() >= 8));
        assertTrue(sifter.stream().anyMatch(r -> r.output() == Material.QUARTZ && r.stage() == 4));
        assertTrue(sifter.stream().noneMatch(r -> List.of(Material.EMERALD, Material.NETHERITE_INGOT, Material.ANCIENT_DEBRIS).contains(r.output())));
        assertFalse(MachineRecipes.acceptsInput(MachineKind.SAWMILL, Material.DIAMOND_SWORD));
        assertTrue(MachineRecipes.fuel(Material.CHARCOAL)); assertFalse(MachineRecipes.fuel(Material.DIAMOND));
    }
    @Test void boundsAndMachineParsingAreStrict() {
        assertThrows(IllegalArgumentException.class, () -> MachineRecipes.plan(MachineKind.CRUSHER, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> MachineRecipes.plan(MachineKind.CRUSHER, 6, inventory()));
        assertThrows(IllegalArgumentException.class, () -> new MachineRecipes.Stack(Material.AIR, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new MachineRecipes.Stack(Material.COAL, 65, true));
        assertThrows(IllegalArgumentException.class, () -> MachineKind.parse("<red>crusher"));
        assertEquals(MachineKind.CRUSHER, MachineKind.parse("crusher"));
    }
}
