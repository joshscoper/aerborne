package net.nerdsmp.paper.studio;

import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.engine.EditorPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Ghost-grid authoring: samples a plain cursor ingredient without consuming or granting items. */
public final class RecipeStudio implements Listener,AutoCloseable {
    public static final String PERMISSION="nerdsmp.studio.recipes";
    private static final int[] GRID={10,11,12,19,20,21,28,29,30};
    private static final int[] CATALOG={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private final NerdSmpPlugin plugin;
    private final StudioUi ui;
    private final RecipeEngine engine;
    private final Map<java.util.UUID,GridPage> views=new HashMap<>();
    private boolean closed;
    public RecipeStudio(NerdSmpPlugin plugin,StudioUi ui,RecipeEngine engine){this.plugin=Objects.requireNonNull(plugin);this.ui=Objects.requireNonNull(ui);this.engine=Objects.requireNonNull(engine);}

    public boolean command(CommandSender sender,String[] args){
        if(!(sender instanceof Player player)){Chat.error(sender,"Open "+Chat.command("recipestudio")+" in game to author recipes.");return true;}
        if(!allowed(player))return true;
        if(args.length==0){open(player);return true;}
        if(args.length==2&&args[0].equalsIgnoreCase("edit")){RecipeBlueprint current=engine.find(args[1]).orElse(null);if(current==null)Chat.error(player,"No authored recipe has that ID.");else grid(player,current,current);return true;}
        if(args.length==1&&args[0].equalsIgnoreCase("reload")){
            ui.confirm(player,PERMISSION,"Reload authored recipes?",List.of("Reads only content/engine-recipes.yml.","Invalid content leaves the current runtime recipes active."),p->{try{engine.reload();Chat.success(p,"Authored recipes reloaded.");open(p);}catch(IOException|RuntimeException failed){failure(p,failed);}});return true;
        }
        Chat.usage(player,"/recipestudio [edit <id>|reload]");return true;
    }
    public List<String> complete(CommandSender sender,String[] args) {
        if(!sender.hasPermission(PERMISSION)||args.length==0)return List.of();
        List<String> values=args.length==1?List.of("edit","reload")
            :args.length==2&&args[0].equalsIgnoreCase("edit")?engine.all().stream().map(RecipeBlueprint::id).toList():List.of();
        return values.stream().filter(value->value.startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
    public void open(Player player){catalog(player,0);}
    public void cancel(Player player){views.remove(player.getUniqueId());}
    private void catalog(Player player,int requested){
        if(!allowed(player))return;
        List<RecipeBlueprint> all=engine.all();int page=WorldStudioEdits.pageNumber(all.size(),requested);ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(0,Material.WRITABLE_BOOK,"New recipe",List.of("Publish an item first, then choose it as the output.","Built-in recipes and currency outputs stay read-only."),this::newRecipe));
        buttons.add(button(4,Material.CRAFTING_TABLE,"Authored recipes",List.of(all.size()+" recipes · enabled recipes register at crafting tables.","Only players in Survival can use skill-gated recipes."),p->{}));
        buttons.add(button(8,Material.BOOK,"Built-in recipe guide",List.of("Browse the existing crafting guide.","These recipes are not editable here."),p->{ui.leave(p);plugin.recipes().showRecipes(p);}));
        List<RecipeBlueprint> visible=WorldStudioEdits.page(all,page);
        for(int i=0;i<visible.size();i++){RecipeBlueprint r=visible.get(i);buttons.add(button(CATALOG[i],engine.preview(r).getType(),r.id(),List.of(r.amount()+" × "+r.output(),r.skill().displayName()+" level "+r.level(),r.enabled()?"Enabled":"Disabled","Click to edit the pattern and output."),p->grid(p,r,r)));}
        if(page>0)buttons.add(button(45,Material.ARROW,"Previous page",List.of(),p->catalog(p,page-1)));
        buttons.add(button(49,Material.ARROW,"Content team tools",List.of("Page "+(page+1)+" / "+WorldStudioEdits.pages(all.size())),ui::home));
        if(page+1<WorldStudioEdits.pages(all.size()))buttons.add(button(53,Material.ARROW,"Next page",List.of(),p->catalog(p,page+1)));
        ui.open(player,PERMISSION,"Recipe workshop",buttons);
    }
    private void newRecipe(Player player){
        if(!allowed(player))return;
        if(engine.outputIds().isEmpty()){Chat.error(player,"Publish an item through "+Chat.command("itemengine")+" first. Only authored item IDs can be recipe outputs.");return;}
        ui.ask(player,PERMISSION,"Recipe ID (lowercase letters, numbers, _ or -):",raw->{
            String id=net.nerdsmp.paper.engine.ItemBlueprint.identifier(raw.strip());
            if(engine.find(id).isPresent()){Chat.error(player,"A recipe with that ID already exists. Open it from the catalogue.");return;}
            outputs(player,id,null,null,0);
        });
    }
    private void outputs(Player player,String id,RecipeBlueprint draft,RecipeBlueprint expected,int requested){
        if(!allowed(player))return;
        List<String> ids=engine.outputIds();int page=WorldStudioEdits.pageNumber(ids.size(),requested);ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(4,Material.NETHER_STAR,"Choose an authored output",List.of("One equipment item per craft.","Materials can use their ordinary stack size."),p->{}));
        List<String> visible=WorldStudioEdits.page(ids,page);
        for(int i=0;i<visible.size();i++){String output=visible.get(i);ItemStack preview=engine.preview(engine.draft(id,output));buttons.add(new StudioUi.Button(CATALOG[i],preview,p->{
            RecipeBlueprint next=draft==null?engine.draft(id,output):new RecipeBlueprint(id,output,1,draft.skill(),draft.level(),draft.enabled(),draft.grid());grid(p,next,expected);
        }));}
        if(page>0)buttons.add(button(45,Material.ARROW,"Previous page",List.of(),p->outputs(p,id,draft,expected,page-1)));
        buttons.add(button(49,Material.ARROW,"Cancel",List.of(),p->{if(draft==null)open(p);else grid(p,draft,expected);}));
        if(page+1<WorldStudioEdits.pages(ids.size()))buttons.add(button(53,Material.ARROW,"Next page",List.of(),p->outputs(p,id,draft,expected,page+1)));
        ui.open(player,PERMISSION,"Recipe output",buttons);
    }
    private void grid(Player player,RecipeBlueprint draft,RecipeBlueprint expected){
        if(!allowed(player))return;
        if(!Objects.equals(engine.find(draft.id()).orElse(null),expected)){Chat.notice(player,"This recipe changed. Reopen its editor.");return;}
        if(!views.containsKey(player.getUniqueId())&&views.size()>=EditorPolicy.MAX_EDITORS)throw new IllegalStateException("The recipe workshop is busy. Try again shortly.");
        engine.preview(draft);ui.leave(player);
        GridPage page=new GridPage(player,plugin.sessions().account(player.getUniqueId()).orElseThrow(),draft,expected);
        page.inventory=Bukkit.createInventory(page,54,net.kyori.adventure.text.Component.text("Recipe: "+draft.id()));
        ItemStack filler=StudioUi.card(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int slot=0;slot<54;slot++)page.inventory.setItem(slot,filler);
        for(int i=0;i<GRID.length;i++){Material ingredient=draft.grid().get(i);page.inventory.setItem(GRID[i],StudioUi.card(ingredient==Material.AIR?Material.LIGHT_GRAY_STAINED_GLASS_PANE:ingredient,ingredient==Material.AIR?"Empty ingredient":"Ingredient: "+label(ingredient),List.of("Left-click with a plain item on your cursor to sample it.","Drag across slots to sample the same ingredient.","Right-click to clear. Your items are not consumed.")));}
        page.inventory.setItem(4,StudioUi.card(Material.BOOK,draft.id(),List.of(draft.enabled()?"Draft will publish enabled":"Draft will publish disabled", "Nothing changes until you confirm Publish.")));
        control(page,14,Material.NETHER_STAR,"Choose output",List.of(draft.output()),p->outputs(p,draft.id(),draft,page.expected,0));
        page.inventory.setItem(16,engine.preview(draft));
        control(page,23,Material.EXPERIENCE_BOTTLE,"Skill: "+draft.skill().displayName(),List.of("Click to cycle the required skill."),p->{Skill next=Skill.values()[(draft.skill().ordinal()+1)%Skill.values().length];grid(p,new RecipeBlueprint(draft.id(),draft.output(),draft.amount(),next,draft.level(),draft.enabled(),draft.grid()),page.expected);});
        control(page,25,Material.CLOCK,"Required level: "+draft.level(),List.of("Level 1–50 in the chosen skill."),p->ui.ask(p,PERMISSION,"Required skill level (1–50):",value->grid(p,new RecipeBlueprint(draft.id(),draft.output(),draft.amount(),draft.skill(),WorldStudioEdits.number(value),draft.enabled(),draft.grid()),page.expected),actor->grid(actor,draft,page.expected)));
        control(page,32,Material.CHEST,"Output amount: "+draft.amount(),List.of("Equipment: exactly one.","Materials: up to their ordinary stack limit."),p->ui.ask(p,PERMISSION,"Output amount:",value->grid(p,new RecipeBlueprint(draft.id(),draft.output(),WorldStudioEdits.number(value),draft.skill(),draft.level(),draft.enabled(),draft.grid()),page.expected),actor->grid(actor,draft,page.expected)));
        control(page,34,draft.enabled()?Material.LIME_DYE:Material.GRAY_DYE,draft.enabled()?"Publish enabled":"Publish disabled",List.of("Click to change the draft's active state.","Saving an enabled recipe makes it usable immediately."),p->grid(p,new RecipeBlueprint(draft.id(),draft.output(),draft.amount(),draft.skill(),draft.level(),!draft.enabled(),draft.grid()),page.expected));
        control(page,40,Material.LIME_CONCRETE,"Publish recipe",List.of("Review the ingredient pattern and output first.","Patterns cannot conflict with existing recipes."),p->publish(p,draft,page.expected));
        if(expected!=null)control(page,42,Material.BARRIER,"Remove authored recipe",List.of("Confirmation required.","Existing crafted items are not removed."),p->ui.confirm(p,PERMISSION,"Remove recipe "+draft.id()+"?",List.of("Stops this authored recipe from being crafted.","Existing items and built-in recipes remain unchanged."),actor->{try{engine.remove(draft.id(),page.expected);open(actor);}catch(IOException|RuntimeException failed){failure(actor,failed);}}));
        control(page,49,Material.ARROW,"Discard draft / catalogue",List.of(),this::open);
        views.put(player.getUniqueId(),page);player.openInventory(page.inventory);
    }
    private void publish(Player player,RecipeBlueprint draft,RecipeBlueprint expected){
        if(!allowed(player))return;
        engine.preview(draft);
        if(draft.grid().stream().allMatch(m->m==Material.AIR)){Chat.error(player,"Add at least one ingredient before publishing.");return;}
        ui.open(player,PERMISSION,"Publish recipe "+draft.id()+"?",List.of(
            button(22,Material.BOOK,"Review recipe",List.of(draft.amount()+" × "+draft.output(),draft.skill().displayName()+" level "+draft.level(),draft.enabled()?"Immediately usable at crafting tables.":"Saved disabled; not available for crafting.","Plain vanilla ingredients only; no automated crafting."),p->{}),
            button(30,Material.LIME_CONCRETE,"Confirm publication",List.of("Apply this recipe."),p->{try{engine.publish(draft,expected);Chat.success(p,"Recipe published.");open(p);}catch(IOException|RuntimeException failed){failure(p,failed);}}),
            button(32,Material.RED_CONCRETE,"Back to draft",List.of("Keep editing without changing the live recipe."),p->grid(p,draft,expected))));
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof GridPage page))return;
        boolean already=event.isCancelled();int raw=event.getRawSlot();
        if(raw>=54){
            if(!(event.getWhoClicked() instanceof Player player)||!current(player,page)||event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)event.setCancelled(true);
            return;
        }
        event.setCancelled(true);if(already||!(event.getWhoClicked() instanceof Player player)||!current(player,page)||page.pending)return;
        if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)return;
        int index=gridIndex(raw);
        if(index>=0){
            Material sample=event.getClick()==ClickType.RIGHT?Material.AIR:sample(event.getCursor());
            if(sample==null){Chat.info(player,"Pick up a plain vanilla item from your inventory, then click or drag it over the grid.");return;}
            page.pending=true;Bukkit.getScheduler().runTask(plugin,()->{if(current(player,page)){page.pending=false;try{grid(player,page.draft.slot(index,sample),page.expected);}catch(IllegalArgumentException|IllegalStateException invalid){Chat.error(player, Chat.escape(invalid.getMessage()));}}});return;
        }
        Consumer<Player> action=page.actions.get(raw);if(action==null)return;
        page.pending=true;Bukkit.getScheduler().runTask(plugin,()->{if(current(player,page)){page.pending=false;try{action.accept(player);}catch(IllegalArgumentException|IllegalStateException invalid){Chat.error(player, Chat.escape(invalid.getMessage()));}}});
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void drag(InventoryDragEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof GridPage page))return;
        boolean already=event.isCancelled();List<Integer> touched=event.getRawSlots().stream().filter(slot->slot<54).toList();
        if(touched.isEmpty())return;
        event.setCancelled(true);if(already||!(event.getWhoClicked() instanceof Player player)||!current(player,page)||page.pending||touched.stream().anyMatch(slot->gridIndex(slot)<0))return;
        Material sample=sample(event.getOldCursor());if(sample==null){Chat.error(player,"Only plain vanilla items can be sampled as recipe ingredients.");return;}
        RecipeBlueprint draft=page.draft;for(int slot:touched)draft=draft.slot(gridIndex(slot),sample);RecipeBlueprint next=draft;
        page.pending=true;Bukkit.getScheduler().runTask(plugin,()->{if(current(player,page)){page.pending=false;try{grid(player,next,page.expected);}catch(IllegalArgumentException|IllegalStateException invalid){Chat.error(player, Chat.escape(invalid.getMessage()));}}});
    }
    @EventHandler public void inventoryClose(InventoryCloseEvent event){if(event.getInventory().getHolder() instanceof GridPage page)views.remove(page.owner.getUniqueId(),page);}
    @EventHandler public void quit(PlayerQuitEvent event){views.remove(event.getPlayer().getUniqueId());}
    private boolean current(Player player,GridPage page){return allowed(player)&&page.owner==player&&views.get(player.getUniqueId())==page&&player.getOpenInventory().getTopInventory()==page.inventory&&plugin.sessions().account(player.getUniqueId()).filter(page.account::equals).isPresent();}
    private boolean allowed(Player player){return !closed&&ui.allowed(player,PERMISSION);}
    private static int gridIndex(int slot){for(int i=0;i<GRID.length;i++)if(GRID[i]==slot)return i;return-1;}
    private static Material sample(ItemStack item){return item==null||item.getType().isAir()||item.hasItemMeta()?null:item.getType();}
    private void control(GridPage page,int slot,Material icon,String title,List<String> lore,Consumer<Player> action){page.inventory.setItem(slot,StudioUi.card(icon,title,lore));page.actions.put(slot,action);}
    private void failure(Player player,Exception failed){if(failed instanceof IOException)plugin.getLogger().log(Level.WARNING,"Authored recipe edit could not be saved",failed);Chat.error(player,failed.getMessage()==null?"The recipe edit could not be completed. Check the server log.":Chat.escape(failed.getMessage()));}
    private static String label(Material material){return material.name().toLowerCase(Locale.ROOT).replace('_',' ');}
    private static StudioUi.Button button(int slot,Material icon,String title,List<String> lore,Consumer<Player> action){return new StudioUi.Button(slot,StudioUi.card(icon,title,lore),action);}
    @Override public void close(){closed=true;for(GridPage page:List.copyOf(views.values()))if(page.owner.getOpenInventory().getTopInventory()==page.inventory)page.owner.closeInventory();views.clear();}
    private static final class GridPage implements InventoryHolder {
        private final Player owner;private final AccountId account;private final RecipeBlueprint draft,expected;private final Map<Integer,Consumer<Player>> actions=new HashMap<>();private Inventory inventory;private boolean pending;
        private GridPage(Player owner,AccountId account,RecipeBlueprint draft,RecipeBlueprint expected){this.owner=owner;this.account=account;this.draft=draft;this.expected=expected;}
        @Override public Inventory getInventory(){return inventory;}
    }
}
