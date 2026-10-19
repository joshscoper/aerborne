package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.engine.ItemEngine;
import net.nerdsmp.paper.item.CustomRecipeService;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Publishes enabled staff recipes with durable CAS and registry rollback; core recipes stay read-only. */
public final class RecipeEngine implements AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final CustomRecipeService crafting;
    private final ItemEngine items;
    private final File file;
    private RecipeBlueprintStore store;
    private boolean started;
    public RecipeEngine(NerdSmpPlugin plugin,CustomRecipeService crafting,ItemEngine items) {
        this.plugin=Objects.requireNonNull(plugin);this.crafting=Objects.requireNonNull(crafting);this.items=Objects.requireNonNull(items);
        file=new File(plugin.getDataFolder(),"content/engine-recipes.yml");
        try {store=new RecipeBlueprintStore(file,this::validateOutput);}catch(IOException invalid){throw new IllegalStateException("Unable to load authored recipes; content was preserved",invalid);}
    }
    /** Call after the built-in CustomRecipeService.start(). No recipe is enabled automatically. */
    public void start(){install(store.all());started=true;}
    public List<RecipeBlueprint> all(){return store.all();}
    public Optional<RecipeBlueprint> find(String id){return store.find(id);}
    public List<String> outputIds(){return items.all().stream().map(net.nerdsmp.paper.engine.ItemBlueprint::id).sorted().toList();}
    public RecipeBlueprint draft(String id,String output) {
        RecipeBlueprint result=new RecipeBlueprint(id,output,1,Skill.SMITHING,1,false,java.util.Collections.nCopies(9,Material.AIR));validateOutput(result);return result;
    }
    /** An editor ghost is deliberately not an authentic item, even outside the shared StudioUi. */
    public ItemStack preview(RecipeBlueprint blueprint){
        ItemStack result=nativeResult(blueprint);
        var meta=result.getItemMeta();
        for(var key:new ArrayList<>(meta.getPersistentDataContainer().getKeys()))meta.getPersistentDataContainer().remove(key);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());result.setItemMeta(meta);return result;
    }
    /** Only registry installation may receive the authentic crafted output. Never expose it to editor inventories. */
    private ItemStack nativeResult(RecipeBlueprint blueprint){validateOutput(blueprint);ItemStack result=plugin.items().create(new ItemId(blueprint.output()));result.setAmount(blueprint.amount());return result;}
    public synchronized void publish(RecipeBlueprint candidate,RecipeBlueprint expected)throws IOException {
        store.validate(candidate,expected);List<RecipeBlueprint> previous=store.all();var next=new TreeMap<String,RecipeBlueprint>();previous.forEach(r->next.put(r.id(),r));next.put(candidate.id(),candidate);
        install(List.copyOf(next.values()));
        try {store.put(candidate,expected);}catch(IOException|RuntimeException failed){restore(previous,failed);throw failed;}
    }
    public synchronized void remove(String id,RecipeBlueprint expected)throws IOException {
        if(expected==null||!find(id).filter(expected::equals).isPresent())throw new IllegalStateException("This recipe changed. Reopen its editor.");
        List<RecipeBlueprint> previous=store.all();install(previous.stream().filter(r->!r.id().equals(id)).toList());
        try {store.remove(id,expected);}catch(IOException|RuntimeException failed){restore(previous,failed);throw failed;}
    }
    public synchronized void reload()throws IOException {
        RecipeBlueprintStore candidate=new RecipeBlueprintStore(file,this::validateOutput);install(candidate.all());store=candidate;
    }
    public synchronized void refresh(){if(started)install(store.all());}
    private void restore(List<RecipeBlueprint> previous,Exception failed){try{install(previous);}catch(RuntimeException rollback){failed.addSuppressed(rollback);throw new IllegalStateException("Recipe rollback failed. Stop crafting and restart before editing further.",failed);}}
    private void validateOutput(RecipeBlueprint recipe) {
        if(crafting.coreRecipeIds().contains(recipe.id()))
            throw new IllegalArgumentException("Built-in recipe IDs are reserved. Choose a distinct authored recipe ID.");
        var blueprint=items.find(recipe.output()).orElseThrow(()->new IllegalArgumentException("Publish an authored item before selecting it as a recipe output. Built-in outputs are read-only."));
        ItemStack output=plugin.items().create(new ItemId(recipe.output()));
        if(recipe.amount()>output.getMaxStackSize()||blueprint.slot()!=PaperItemService.GearSlot.NONE&&recipe.amount()!=1)
            throw new IllegalArgumentException("Equipment produces one item; material output must fit its ordinary stack size.");
        for(Material ingredient:recipe.grid())if(ingredient!=Material.AIR&&!ingredient.isItem())throw new IllegalArgumentException("Ingredients must be obtainable vanilla items.");
    }
    private void install(List<RecipeBlueprint> recipes) {
        recipes.forEach(this::validateOutput);
        List<RecipeBlueprint> enabled=recipes.stream().filter(RecipeBlueprint::enabled).toList();
        List<Recipe> external=new ArrayList<>();plugin.getServer().recipeIterator().forEachRemaining(recipe->{
            if(!(recipe instanceof Keyed keyed)||!owned(keyed.getKey()))external.add(recipe);
        });
        for(int i=0;i<enabled.size();i++) {
            RecipeBlueprint candidate=enabled.get(i);
            for(Recipe existing:external)if(conflicts(existing,candidate))throw new IllegalArgumentException("This pattern matches an existing recipe. Choose a distinct pattern before enabling it.");
            for(int j=0;j<i;j++){var pattern=enabled.get(j).pattern();var choices=pattern.ingredients().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->Set.of(e.getValue())));
                if(RecipeCollision.shaped(pattern.shape(),choices,candidate))throw new IllegalArgumentException("This pattern matches authored recipe "+enabled.get(j).id()+".");}
        }
        ArrayList<CustomRecipeService.AuthoredRecipe> installed=new ArrayList<>();
        for(RecipeBlueprint recipe:enabled){var pattern=recipe.pattern();installed.add(new CustomRecipeService.AuthoredRecipe(new CustomRecipeService.RecipeDefinition(recipe.id(),new ItemId(recipe.output()),recipe.skill(),recipe.level(),pattern.shape(),pattern.ingredients()),nativeResult(recipe)));}
        crafting.installAuthored(installed);
    }
    private boolean owned(NamespacedKey key){return key.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))&&key.getKey().startsWith("engine_recipe_");}
    private boolean conflicts(Recipe existing,RecipeBlueprint candidate) {
        if(existing instanceof ShapedRecipe shaped){var choices=shaped.getChoiceMap().entrySet().stream().filter(e->e.getValue()!=null).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->materials(e.getValue())));return RecipeCollision.shaped(List.of(shaped.getShape()),choices,candidate);}
        if(existing instanceof ShapelessRecipe shapeless)return RecipeCollision.shapeless(shapeless.getChoiceList().stream().map(this::materials).toList(),candidate);
        return false;
    }
    private Set<Material> materials(RecipeChoice choice){
        if(choice instanceof RecipeChoice.MaterialChoice material)return Set.copyOf(material.getChoices());
        if(choice instanceof RecipeChoice.ExactChoice exact)return exact.getChoices().stream().filter(item->!item.hasItemMeta()).map(ItemStack::getType).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return Set.of();
    }
    @Override public void close(){crafting.installAuthored(List.of());started=false;}
}
