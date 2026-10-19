package net.nerdsmp.paper.studio;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.engine.EditorPolicy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A searchable reference library for existing story, economy and progression definitions. Never grants rewards. */
public final class ContentLibrary {
    public static final String PERMISSION="nerdsmp.studio.library";
    public record Entry(String id,String title,Material icon,List<String> details) {
        public Entry { details=List.copyOf(details); }
    }
    private final NerdSmpPlugin plugin; private final StudioUi ui;
    public ContentLibrary(NerdSmpPlugin plugin,StudioUi ui) { this.plugin=plugin; this.ui=ui; }
    public void open(Player player) {
        var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(new StudioUi.Button(4,Material.BOOK,"Content Reference",List.of("Inspect live definitions and their connections.","These views never pay rewards or modify player progress."),p->{}));
        int slot=10;
        for(String category:List.of("npcs","dialogues","quests","recipes","shops","factions")) {
            String selected=category;
            buttons.add(new StudioUi.Button(slot++,Material.WRITABLE_BOOK,title(category),List.of(entries(category).size()+" definitions","Browse or search the published catalog."),p->browse(p,selected,"",0)));
        }
        buttons.add(new StudioUi.Button(45,Material.ARROW,"Studio",List.of(),ui::home));
        ui.open(player,PERMISSION,"Content Reference",buttons);
    }
    public List<Entry> entries(String category) {
        var content=plugin.npcs().content();
        return switch(category) {
            case "npcs" -> content.npcs().values().stream().map(npc->new Entry(npc.id(),plain(npc.name()),Material.VILLAGER_SPAWN_EGG,List.of(
                plain(npc.title()),"Faction: "+npc.factionId().value(),"Dialogue: "+npc.rootDialogue(),"Shop: "+(npc.shopId()==null?"none":npc.shopId()),
                "Body: "+npc.entityType().name(),"Place manually through the NPC catalog."))).sorted(java.util.Comparator.comparing(Entry::id)).toList();
            case "dialogues" -> content.dialogues().values().stream().map(node->{
                var lines=new ArrayList<String>(); node.text().forEach(text->lines.add(plain(text)));
                node.choices().forEach(choice->{ lines.add("Reply: "+plain(choice.text()));
                    lines.add("  Next: "+(choice.nextNode()==null?"terminal":choice.nextNode())+" · once "+choice.once()+" · cooldown "+choice.cooldown().getSeconds()+"s");
                    lines.add("  Lv "+choice.requirements().minimumAdventurerLevel()+" · rep "+choice.requirements().minimumReputation()+"–"+choice.requirements().maximumReputation());
                    choice.effects().forEach(effect->lines.add("  Effect: "+effect.type().name())); });
                return new Entry(node.id(),title(node.id()),Material.WRITABLE_BOOK,lines);
            }).sorted(java.util.Comparator.comparing(Entry::id)).toList();
            case "quests" -> plugin.engagement().definitions().stream().map(quest->new Entry(quest.id(),quest.title(),Material.PAPER,List.of(
                quest.description(),"Objective: "+quest.action().name(),"Target: "+quest.target(),"Reward: "+quest.markReward()+" Marks",
                "Daily rotation; account progress is not changed by inspection."))).sorted(java.util.Comparator.comparing(Entry::id)).toList();
            case "recipes" -> plugin.recipes().recipes().stream().map(recipe->{
                var lines=new ArrayList<String>(); lines.add("Output: "+recipe.result().value()); lines.add("Requirement: "+recipe.skill().displayName()+" "+recipe.requiredLevel());
                recipe.shape().forEach(row->lines.add("Pattern: "+row)); recipe.ingredients().forEach((symbol,material)->lines.add(symbol+" = "+material.name()));
                return new Entry(recipe.id(),title(recipe.id()),Material.CRAFTING_TABLE,lines);
            }).sorted(java.util.Comparator.comparing(Entry::id)).toList();
            case "shops" -> content.shops().values().stream().map(shop->{
                var lines=new ArrayList<String>(); lines.add(plain(shop.description()));
                shop.products().forEach(product->lines.add(product.id()+" ×"+product.amount()+" · buy "+product.basePrice()+" · sell "+product.sellPrice()+" · daily "+product.dailySellLimit()));
                return new Entry(shop.id(),plain(shop.title()),Material.EMERALD,lines);
            }).sorted(java.util.Comparator.comparing(Entry::id)).toList();
            case "factions" -> content.factions().values().stream().map(faction->{
                var lines=new ArrayList<String>(); lines.add(plain(faction.description()));
                faction.tiers().forEach(tier->lines.add(tier.name()+" · rep "+tier.threshold()+" · price ×"+tier.shopPriceMultiplier()+" · trade "+tier.canTrade()));
                return new Entry(faction.id().value(),plain(faction.name()),Material.WHITE_BANNER,lines);
            }).sorted(java.util.Comparator.comparing(Entry::id)).toList();
            default -> throw new IllegalArgumentException("Choose npcs, dialogues, quests, recipes, shops or factions.");
        };
    }
    public void browse(Player player,String category,String search,int requested) {
        String filter=search.toLowerCase(Locale.ROOT);
        List<Entry> found=entries(category).stream().filter(entry->(entry.id()+" "+entry.title()).toLowerCase(Locale.ROOT).contains(filter)).toList();
        int page=EditorPolicy.page(requested,found.size()); var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(new StudioUi.Button(4,Material.BOOK,title(category),List.of(found.size()+" results","Search: "+(search.isBlank()?"all":search)),p->{}));
        var shown=EditorPolicy.entries(found,page);
        for(int index=0;index<shown.size();index++) { Entry entry=shown.get(index);
            buttons.add(new StudioUi.Button(StudioUi.CONTENT_SLOTS[index],entry.icon(),entry.title(),List.of(entry.id(),"Inspect definition and references."),p->inspect(p,category,search,page,entry,0))); }
        buttons.add(new StudioUi.Button(45,Material.ARROW,"Reference Library",List.of(),this::open));
        buttons.add(new StudioUi.Button(48,Material.SPYGLASS,"Search",List.of("Find by ID or title. Enter all to clear."),p->ui.ask(p,PERMISSION,"Search "+category+":",value->browse(p,category,value.equalsIgnoreCase("all")?"":EditorPolicy.text(value,80),0))));
        paginate(buttons,page,found.size(),next->browse(player,category,search,next));
        ui.open(player,PERMISSION,title(category)+" Reference",buttons);
    }
    private void inspect(Player player,String category,String search,int previous,Entry entry,int requested) {
        int page=EditorPolicy.page(requested,entry.details().size()); var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(new StudioUi.Button(4,entry.icon(),entry.title(),List.of(entry.id(),"Published reference; read-only."),p->{}));
        var lines=EditorPolicy.entries(entry.details(),page);
        for(int index=0;index<lines.size();index++) buttons.add(new StudioUi.Button(StudioUi.CONTENT_SLOTS[index],Material.PAPER,"Detail "+(page*28+index+1),List.of(lines.get(index)),p->{}));
        buttons.add(new StudioUi.Button(45,Material.ARROW,"Back",List.of(),p->browse(p,category,search,previous)));
        paginate(buttons,page,entry.details().size(),next->inspect(player,category,search,previous,entry,next));
        ui.open(player,PERMISSION,entry.title(),buttons);
    }
    private static void paginate(List<StudioUi.Button> buttons,int page,int count,Consumer<Integer> next) {
        if(page>0) buttons.add(new StudioUi.Button(46,Material.ARROW,"Previous",List.of(),p->next.accept(page-1)));
        if((page+1)*28<count) buttons.add(new StudioUi.Button(52,Material.ARROW,"Next",List.of(),p->next.accept(page+1)));
    }
    private static String plain(String text) { return text==null?"":PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(text)); }
    private static String title(String text) { return net.nerdsmp.paper.presentation.VisualFormat.titleCase(text.replace('_',' ')); }
}
