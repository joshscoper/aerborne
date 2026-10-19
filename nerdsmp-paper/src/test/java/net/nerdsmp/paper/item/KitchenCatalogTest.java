package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KitchenCatalogTest {
    @TempDir Path directory;
    private YamlConfiguration yaml(String source) throws Exception { var yaml=new YamlConfiguration(); yaml.loadFromString(source); return yaml; }
    private String recipe(String id,int introduced,int level) { return "  "+id+":\n    introduced: "+introduced+"\n    level: "+level+"\n    shape: ['A']\n    ingredients: {A: wheat}\n"; }
    @Test void thirtyEightMealsSpanCookingProgressionAndKeepTheirOriginalIds() throws Exception {
        var meals=HarvestDish.load(yaml(Files.readString(Path.of("src/main/resources/content/cooking.yml"))));
        assertEquals(38,meals.size()); assertEquals(38,meals.stream().map(HarvestDish::id).distinct().count());
        assertEquals(1,meals.getFirst().level()); assertEquals(50,meals.getLast().level());
        assertTrue(meals.stream().allMatch(m->m.seconds()<=120));
        assertTrue(meals.stream().map(HarvestDish::id).toList().containsAll(List.of("orchard_loaf","miners_potato","anglers_chowder","foragers_stew","homestead_supper","wardens_roast")));
        assertEquals(32, meals.stream().filter(m -> !Set.of("orchard_loaf","miners_potato","anglers_chowder","foragers_stew","homestead_supper","wardens_roast").contains(m.id())).count());
    }
    @Test void cookingMigrationAddsOnlyNewMealsAndPreservesExistingRecipes() throws Exception {
        var bundled=yaml(Files.readString(Path.of("src/main/resources/content/cooking.yml")));
        var old=yaml(bundled.saveToString()); old.set("schema-version",1);
        for(String id:List.copyOf(old.getConfigurationSection("meals").getKeys(false))) if(old.getInt("meals."+id+".introduced",1)>1) old.set("meals."+id,null);
        old.set("meals.orchard_loaf.seconds",47); old.set("meals.miners_potato",null);
        Path file=directory.resolve("cooking.yml"); Files.writeString(file,old.saveToString());
        var upgraded=RecipeCatalogFiles.load(file.toFile(),bundled,"meals",2);
        assertEquals(37,HarvestDish.load(upgraded).size()); assertEquals(47,upgraded.getInt("meals.orchard_loaf.seconds"));
        assertFalse(upgraded.contains("meals.miners_potato")); assertTrue(Files.exists(directory.resolve("cooking.yml.before-v2.bak")));
    }
    @Test void mealQualityScalesWithTheChefButNeverAmplifiesRegenerationOrResistance() {
        assertEquals(30,MealRules.seconds(30,0,0,0)); assertEquals(45,MealRules.seconds(30,50,0,0));
        assertEquals(69,MealRules.seconds(30,50,50,30));
        assertEquals(0,MealRules.amplifier("haste",39)); assertEquals(1,MealRules.amplifier("haste",40));
        assertEquals(0,MealRules.amplifier("regeneration",50)); assertEquals(0,MealRules.amplifier("resistance",50));
        assertEquals(300,MealRules.seconds(1000,500,500,500)); assertEquals(340,MealRules.craftXp(50));
    }
    @Test void recipesHaveDistinctNormalAndMirroredIngredientPatterns() throws Exception {
        var meals=HarvestDish.load(yaml(Files.readString(Path.of("src/main/resources/content/cooking.yml"))));
        Set<String> patterns=new HashSet<>();
        for(var dish:meals) {
            String normal=dish.shape().stream().map(row->row.chars().mapToObj(c->c==' '?"AIR":dish.ingredients().get((char)c).name()).collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining(";"));
            String mirrored=dish.shape().stream().map(row->new StringBuilder(row).reverse().toString()).map(row->row.chars().mapToObj(c->c==' '?"AIR":dish.ingredients().get((char)c).name()).collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining(";"));
            assertFalse(patterns.contains(normal)||patterns.contains(mirrored),dish.id()); patterns.add(normal); patterns.add(mirrored);
        }
    }
    @Test void patternsRejectMissingUnusedAndUnevenSymbols() {
        assertThrows(IllegalArgumentException.class,()->HarvestDish.validateShape(List.of("AA","A"),Map.of('A',Material.WHEAT)));
        assertThrows(IllegalArgumentException.class,()->HarvestDish.validateShape(List.of("AB"),Map.of('A',Material.WHEAT)));
        assertThrows(IllegalArgumentException.class,()->HarvestDish.validateShape(List.of("A"),Map.of('A',Material.WHEAT,'B',Material.APPLE)));
    }
    @Test void recipeUpgradePreservesEditsAndDoesNotResurrectOldDeletedRecipes() throws Exception {
        Path file=directory.resolve("recipes.yml"); String original="schema-version: 1\nrecipes:\n"+recipe("kept",1,31);
        Files.writeString(file,original);
        var bundled=yaml("schema-version: 2\nrecipes:\n"+recipe("kept",1,2)+recipe("deleted",1,5)+recipe("new_recipe",2,12));
        var result=RecipeCatalogFiles.load(file.toFile(),bundled,"recipes",2);
        assertEquals(31,result.getInt("recipes.kept.level")); assertEquals(12,result.getInt("recipes.new_recipe.level"));
        assertFalse(result.contains("recipes.deleted"));
        assertEquals(original,Files.readString(directory.resolve("recipes.yml.before-v2.bak")));
    }
    @Test void malformedRecipeIsNotOverwrittenDuringUpgrade() throws Exception {
        Path file=directory.resolve("recipes.yml"); String original="schema-version: 1\nrecipes:\n  bad: {shape: ['Z'], ingredients: {A: wheat}}\n";
        Files.writeString(file,original);
        assertThrows(Exception.class,()->RecipeCatalogFiles.load(file.toFile(),yaml("schema-version: 2\nrecipes:\n"+recipe("new_recipe",2,12)),"recipes",2));
        assertEquals(original,Files.readString(file));
        assertFalse(Files.exists(directory.resolve("recipes.yml.before-v2.bak")));
    }
    @Test void forestryMigrationAddsOnlyThreeNewRoutesAndPreservesOperatorEdits() throws Exception {
        var bundled=yaml(Files.readString(Path.of("src/main/resources/content/recipes.yml")));
        var old=yaml(bundled.saveToString());old.set("schema-version",3);
        for(String id:List.of("forester_axe","forester_boots","forester_coat")) old.set("recipes."+id,null);
        String retained=old.getConfigurationSection("recipes").getKeys(false).iterator().next();
        old.set("recipes."+retained+".level",49);
        String deleted=old.getConfigurationSection("recipes").getKeys(false).stream().filter(id->!id.equals(retained)).findFirst().orElseThrow();
        old.set("recipes."+deleted,null);
        Path file=directory.resolve("forestry-recipes.yml");String original=old.saveToString();Files.writeString(file,original);
        var upgraded=RecipeCatalogFiles.load(file.toFile(),bundled,"recipes",4);
        assertEquals(4,upgraded.getInt("schema-version"));assertEquals(49,upgraded.getInt("recipes."+retained+".level"));
        assertFalse(upgraded.contains("recipes."+deleted));
        for(String id:List.of("forester_axe","forester_boots","forester_coat")) {
            assertEquals("forestry",upgraded.getString("recipes."+id+".skill"));
            assertEquals(4,upgraded.getInt("recipes."+id+".introduced"));
        }
        assertEquals(original,Files.readString(directory.resolve("forestry-recipes.yml.before-v4.bak")));
        String saved=Files.readString(file);RecipeCatalogFiles.load(file.toFile(),bundled,"recipes",4);
        assertEquals(saved,Files.readString(file));
    }
    @Test void newForestryRecipesDoNotCollideWithExistingOrMirroredPatterns() throws Exception {
        var recipes=yaml(Files.readString(Path.of("src/main/resources/content/recipes.yml"))).getConfigurationSection("recipes");
        Set<String> patterns=new HashSet<>();
        List<String> ordered=new ArrayList<>(recipes.getKeys(false));
        ordered.sort(Comparator.comparingInt(id->recipes.getInt(id+".introduced",1)));
        for(String id:ordered) {
            var entry=recipes.getConfigurationSection(id);var ingredients=HarvestDish.ingredients(entry.getConfigurationSection("ingredients"));
            List<String> rows=entry.getStringList("shape");HarvestDish.validateShape(rows,ingredients);
            String normal=pattern(rows,ingredients),mirrored=pattern(rows.stream().map(row->new StringBuilder(row).reverse().toString()).toList(),ingredients);
            if(recipes.getInt(id+".introduced",1)==4) assertFalse(patterns.contains(normal)||patterns.contains(mirrored),id);
            patterns.add(normal);patterns.add(mirrored);
        }
    }
    private String pattern(List<String> rows,Map<Character,Material> ingredients) {
        return rows.stream().map(row->row.chars().mapToObj(c->c==' '?"AIR":ingredients.get((char)c).name())
            .collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining(";"));
    }
    @Test void futureSchemaRemainsUntouched() throws Exception {
        Path file=directory.resolve("recipes.yml"); String original="schema-version: 99\nrecipes: {}\n"; Files.writeString(file,original);
        assertThrows(Exception.class,()->RecipeCatalogFiles.load(file.toFile(),yaml("schema-version: 2\nrecipes: {}\n"),"recipes",2));
        assertEquals(original,Files.readString(file));
    }
}
