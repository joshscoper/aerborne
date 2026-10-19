package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.Skill;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RecipeBlueprintTest {
    private RecipeBlueprint draft(){return new RecipeBlueprint("arcblade","arcblade",1,Skill.SMITHING,10,false,Collections.nCopies(9,Material.AIR));}
    @Test void blankDraftIsAllowedButBlankEnabledRecipeIsRejected(){
        assertThrows(IllegalArgumentException.class,()->draft().pattern());
        assertThrows(IllegalArgumentException.class,()->new RecipeBlueprint("arcblade","arcblade",1,Skill.SMITHING,10,true,Collections.nCopies(9,Material.AIR)));
    }
    @Test void gridSamplesAreImmutableAndTrimEmptyBorders(){
        RecipeBlueprint empty=draft(), next=empty.slot(4,Material.IRON_INGOT).slot(7,Material.STICK);
        assertEquals(Material.AIR,empty.grid().get(4));
        assertEquals(List.of("A","B"),next.pattern().shape());
        assertEquals(Map.of('A',Material.IRON_INGOT,'B',Material.STICK),next.pattern().ingredients());
        assertThrows(UnsupportedOperationException.class,()->next.grid().clear());
        assertThrows(IllegalArgumentException.class,()->next.slot(9,Material.DIAMOND));
    }
    @Test void repeatedMaterialsUseOneSymbolAndGapsArePreserved(){
        var pattern=draft().slot(0,Material.DIAMOND).slot(2,Material.DIAMOND).slot(7,Material.STICK).pattern();
        assertEquals(List.of("A A","   "," B "),pattern.shape());
        assertEquals(2,pattern.ingredients().size());
    }
    @Test void modelRejectsMalformedContent(){
        assertThrows(IllegalArgumentException.class,()->new RecipeBlueprint("../bad","arcblade",1,Skill.SMITHING,1,false,Collections.nCopies(9,Material.AIR)));
        assertThrows(IllegalArgumentException.class,()->new RecipeBlueprint("good","arcblade",65,Skill.SMITHING,1,false,Collections.nCopies(9,Material.AIR)));
        assertThrows(IllegalArgumentException.class,()->new RecipeBlueprint("good","arcblade",1,Skill.SMITHING,51,false,Collections.nCopies(9,Material.AIR)));
        assertThrows(IllegalArgumentException.class,()->draft().slot(0,Material.BARRIER));
        assertThrows(IllegalArgumentException.class,()->new RecipeBlueprint("good","arcblade",1,Skill.SMITHING,1,false,List.of(Material.DIAMOND)));
    }
    @Test void collisionMatchesShiftedAndMirroredRecipes(){
        RecipeBlueprint r=draft().slot(4,Material.IRON_INGOT).slot(5,Material.STICK);
        assertTrue(RecipeCollision.shaped(List.of(" AB","   "),Map.of('A',Set.of(Material.IRON_INGOT),'B',Set.of(Material.STICK)),r));
        assertTrue(RecipeCollision.shaped(List.of("BA"),Map.of('A',Set.of(Material.IRON_INGOT),'B',Set.of(Material.STICK)),r));
        assertFalse(RecipeCollision.shaped(List.of("AB"),Map.of('A',Set.of(Material.DIAMOND),'B',Set.of(Material.STICK)),r));
        assertFalse(RecipeCollision.shaped(List.of("A","B"),Map.of('A',Set.of(Material.IRON_INGOT),'B',Set.of(Material.STICK)),r));
    }
    @Test void collisionRespectsIngredientChoicesAndEmptyPatternCells(){
        RecipeBlueprint r=draft().slot(0,Material.OAK_PLANKS).slot(2,Material.STICK);
        assertTrue(RecipeCollision.shaped(List.of("A B"),Map.of('A',Set.of(Material.OAK_PLANKS,Material.BIRCH_PLANKS),'B',Set.of(Material.STICK)),r));
        assertFalse(RecipeCollision.shaped(List.of("AAB"),Map.of('A',Set.of(Material.OAK_PLANKS),'B',Set.of(Material.STICK)),r));
        assertFalse(RecipeCollision.shaped(List.of("A B"),Map.of('A',Set.of(Material.BIRCH_PLANKS),'B',Set.of(Material.STICK)),r));
    }
    @Test void shapelessCollisionUsesMatchingNotGreedyChoiceOrder(){
        RecipeBlueprint r=draft().slot(0,Material.OAK_PLANKS).slot(1,Material.BIRCH_PLANKS);
        assertTrue(RecipeCollision.shapeless(List.of(Set.of(Material.OAK_PLANKS,Material.BIRCH_PLANKS),Set.of(Material.OAK_PLANKS)),r));
        assertFalse(RecipeCollision.shapeless(List.of(Set.of(Material.OAK_PLANKS),Set.of(Material.OAK_PLANKS)),r));
        assertFalse(RecipeCollision.shapeless(List.of(Set.of(Material.OAK_PLANKS)),r));
    }
}
