package net.nerdsmp.paper.studio;

import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Team entry point. Each module retains its own permission and publication rules. */
public final class DeveloperStudio {
    public static final String PERMISSION="nerdsmp.studio.use";
    private final NerdSmpPlugin plugin; private final StudioUi ui; private final ContentLibrary library;
    public DeveloperStudio(NerdSmpPlugin plugin,StudioUi ui) { this.plugin=plugin; this.ui=ui; library=new ContentLibrary(plugin,ui); }
    public ContentLibrary library() { return library; }
    public boolean command(CommandSender sender,String[] args) {
        if(!sender.hasPermission(PERMISSION)) { Chat.error(sender, "You do not have access to the content studio."); return true; }
        String action=args.length==0?"home":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("qa") || action.equals("validate") || action.equals("board") || action.equals("simulate")) {
            String[] forwarded=action.equals("qa")?Arrays.copyOfRange(args,1,args.length):args;
            return plugin.qaStudio().command(sender,forwarded);
        }
        if(!(sender instanceof Player player)) { Chat.error(sender, "Open " + Chat.command("studio") + " in game, or use " + Chat.command("studio validate") + " from the console."); return true; }
        try {
            switch(action) {
                case "home" -> open(player);
                case "items" -> external(player,"nerdsmp.content.items",p->plugin.contentEditor().openItems(p));
                case "mobs" -> external(player,"nerdsmp.content.mobs",p->plugin.contentEditor().openMobs(p));
                case "loot" -> plugin.lootStudio().open(player);
                case "world" -> plugin.worldStudio().command(player,Arrays.copyOfRange(args,1,args.length));
                case "npcs" -> plugin.npcStudio().open(player);
                case "recipes" -> plugin.recipeStudio().open(player);
                case "library" -> { if(args.length==1) library.open(player); else library.browse(player,args[1],args.length>2?String.join(" ",Arrays.copyOfRange(args,2,args.length)):"",0); }
                default -> Chat.usage(player, "/studio [items|mobs|loot|world|npcs|recipes|library|qa|validate|board|simulate]");
            }
        } catch(IllegalArgumentException | IllegalStateException failure) { Chat.error(player, Chat.escape(failure.getMessage())); }
        return true;
    }
    public void open(Player player) {
        var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(new StudioUi.Button(4,Material.KNOWLEDGE_BOOK,"Content Studio",List.of("Build, connect, test and maintain your RPG content.","Publishing remains a direct designer action."),p->{}));
        module(buttons,10,player,Material.IRON_SWORD,"Items & Equipment Sets","nerdsmp.content.items",p->external(p,"nerdsmp.content.items",actor->plugin.contentEditor().openItems(actor)));
        module(buttons,11,player,Material.ZOMBIE_HEAD,"Mobs & Abilities","nerdsmp.content.mobs",p->external(p,"nerdsmp.content.mobs",actor->plugin.contentEditor().openMobs(actor)));
        module(buttons,12,player,Material.CHEST,"Loot Tables & Bindings","nerdsmp.loot.admin",p->plugin.lootStudio().open(p));
        module(buttons,13,player,Material.COMPASS,"Worlds & Encounters","nerdsmp.studio.world",p->plugin.worldStudio().open(p));
        module(buttons,14,player,Material.VILLAGER_SPAWN_EGG,"NPCs & Dialogue","nerdsmp.npc.admin",p->plugin.npcStudio().open(p));
        module(buttons,15,player,Material.CRAFTING_TABLE,"Crafting Recipes","nerdsmp.studio.recipes",p->plugin.recipeStudio().open(p));
        module(buttons,16,player,Material.BOOK,"Content Reference","nerdsmp.studio.library",library::open);
        module(buttons,20,player,Material.SPYGLASS,"Validation & QA","nerdsmp.studio.qa",p->plugin.qaStudio().open(p));
        module(buttons,22,player,Material.WRITABLE_BOOK,"Team Workboard","nerdsmp.studio.qa",p->plugin.qaStudio().command(p,new String[]{"board"}));
        module(buttons,24,player,Material.COMPARATOR,"Loot Balance Simulator","nerdsmp.studio.qa",p->plugin.qaStudio().command(p,new String[]{"simulate"}));
        buttons.add(new StudioUi.Button(31,Material.PAPER,"Team Workflow",List.of("1. Create a draft and preview it.","2. Publish, then connect its sources.","3. Run validation and simulation.","4. Track bugs and playtests on the workboard.","5. Complete manual release checks."),p->{}));
        buttons.add(new StudioUi.Button(49,Material.BARRIER,"Close",List.of(),p->{ui.cancel(p);p.closeInventory();}));
        ui.open(player,PERMISSION,"MMORPG Content Studio",buttons);
    }
    private void external(Player player,String permission,Consumer<Player> next) {
        if(!ui.allowed(player,permission)) { Chat.noPermission(player); return; }
        ui.cancel(player); next.accept(player);
    }
    private static void module(List<StudioUi.Button> buttons,int slot,Player player,Material icon,String title,String permission,Consumer<Player> action) {
        boolean access=player.hasPermission(permission);
        buttons.add(new StudioUi.Button(slot,access?icon:Material.GRAY_DYE,title,List.of(access?"Open this tool.":"Permission: "+permission),p->{if(access)action.accept(p);else Chat.noPermission(p);}));
    }
    public List<String> complete(CommandSender sender,String[] args) {
        if(!sender.hasPermission(PERMISSION)||args.length==0)return List.of();
        if(args.length==1) return List.of("items","mobs","loot","world","npcs","recipes","library","qa","validate","board","simulate").stream().filter(a->a.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("library"))return List.of("npcs","dialogues","quests","recipes","shops","factions").stream().filter(a->a.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
