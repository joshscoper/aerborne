package net.nerdsmp.paper.studio.qa;

import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.studio.StudioUi;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Read-only quality desk and optional team tracking. No mandatory review or publishing gate. */
public final class QAStudio {
    public static final String READ_PERMISSION="nerdsmp.studio.qa";
    public static final String MANAGE_PERMISSION="nerdsmp.studio.manage";
    private static final int[] GRID={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private final NerdSmpPlugin plugin;
    private final StudioUi ui;
    private final WorkboardStore board;
    private final String boardFailure;

    public QAStudio(NerdSmpPlugin plugin,StudioUi ui) throws IOException {
        this.plugin=plugin; this.ui=ui;
        WorkboardStore loaded=null; String failure=null;
        try { loaded=new WorkboardStore(new File(plugin.getDataFolder(),"studio/workboard.yml")); }
        catch(IOException unavailable) {
            failure="Team notes are unavailable. Staff must repair or restore the preserved workboard data, then restart.";
            plugin.getLogger().log(Level.SEVERE,"Optional Content Studio workboard could not load; files preserved and board writes disabled. Validation and loot preview remain available.",unavailable);
        }
        board=loaded; boardFailure=failure;
    }
    public boolean workboardAvailable() { return board!=null; }
    public WorkboardStore workboard() {
        if(board==null) throw new IllegalStateException(boardFailure); return board;
    }
    public ContentValidator.Report validate() {
        var catalog=DomainAuditSnapshot.capture(plugin);
        var observations=new ArrayList<>(catalog.observations());
        if(board==null) observations.add(new ContentValidator.Finding(ContentValidator.Severity.WARNING,"workboard_unavailable",null,boardFailure));
        for(var item:board==null?List.<WorkItem>of():board.all()) for(var ref:item.references()) if(!catalog.known().contains(ref))
            observations.add(new ContentValidator.Finding(ContentValidator.Severity.WARNING,"workboard_reference",ref,
                "Work item "+item.id()+" references content no longer in the catalog."));
        return ContentValidator.validate(new ContentValidator.Catalog(catalog.known(),catalog.dependencies(),catalog.items(),
            catalog.mobs(),catalog.loot(),catalog.zones(),observations));
    }
    /** Root routing strips its own prefix; arguments start with validate, board, simulate or readiness. */
    public boolean command(CommandSender sender,String[] args) {
        if(!allowed(sender,READ_PERMISSION)) return true;
        if(args.length==0) { if(sender instanceof Player p) open(p); else showReport(sender,validate(),0); return true; }
        switch(args[0].toLowerCase(Locale.ROOT)) {
            case "validate","report" -> {
                int page=0;
                try { if(args.length>2) throw new IllegalArgumentException(); if(args.length==2) {
                        int value=Integer.parseInt(args[1]); if(value<1 || value>1_000) throw new IllegalArgumentException(); page=value-1;
                    } }
                catch(IllegalArgumentException failure) { Chat.usage(sender, "/content qa validate [page]"); return true; }
                if(sender instanceof Player p) report(p,page); else showReport(sender,validate(),page);
            }
            case "board" -> {
                if(sender instanceof Player p) workboard(p,0);
                else if(board==null) Chat.error(sender, Chat.escape(boardFailure));
                else board.all().stream().limit(30).forEach(item->Chat.info(sender, "<white>"+Chat.escape(item.id())+"</white> | "+Chat.humanize(item.state().name())+" | "+Chat.escape(item.title())));
            }
            case "simulate","sim" -> {
                if(args.length==1 && sender instanceof Player p) lootTables(p,0);
                else if(args.length!=2) Chat.usage(sender, "/content qa simulate <loot-profile>");
                else if(sender instanceof Player p) simulation(p,args[1]);
                else plugin.rewardDrops().profiles().find(args[1]).ifPresentOrElse(profile->showSimulation(sender,
                    LootSimulation.simulate(profile,seed(profile.id()))),()->Chat.error(sender, "Unknown loot table."));
            }
            case "readiness" -> net.nerdsmp.paper.release.ReleaseReadiness.show(plugin,sender);
            default -> Chat.usage(sender, "/content qa [validate|board|simulate <loot-profile>|readiness]");
        }
        return true;
    }
    public void open(Player player) {
        if(!allowed(player,READ_PERMISSION)) return;
        ui.open(player,READ_PERMISSION,"Quality Desk",List.of(
            button(10,Material.SPYGLASS,"Content Validation",List.of("Find missing links, unfinished zones","and balance risks. Read-only."),p->report(p,0)),
            button(12,Material.WRITABLE_BOOK,"Team Workboard",List.of("Tasks, bugs and optional reviews.","Shared notes survive restarts."),p->workboard(p,0)),
            button(14,Material.HOPPER,"Loot Simulator",List.of("Preview 1,000 rolls without rewards.","No mobs, items or Marks are created."),p->lootTables(p,0)),
            button(16,Material.COMPASS,"Release Review",List.of("Multiplayer and operations checklist.","This desk cannot approve a release."),this::readiness),
            button(49,Material.ARROW,"Content Studio",List.of(),ui::home)));
    }
    private void report(Player player,int requested) {
        var result=validate(); int pages=pages(result.findings().size()),page=Math.min(requested,pages-1);
        var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(button(4,Material.SPYGLASS,"Validation "+(page+1)+"/"+pages,List.of(
            result.count(ContentValidator.Severity.ERROR)+" broken links | "+result.count(ContentValidator.Severity.WARNING)+" warnings",
            "No automatic release approval.","Omitted findings: "+result.omitted()),p->report(p,page)));
        for(int i=page*GRID.length;i<Math.min(result.findings().size(),(page+1)*GRID.length);i++) {
            var f=result.findings().get(i); var lore=new ArrayList<String>();
            lore.add(f.content()==null?"Server review":f.content().toString()); lore.addAll(wrap(f.message()));
            Material icon=switch(f.severity()) { case ERROR->Material.RED_CONCRETE; case WARNING->Material.YELLOW_CONCRETE;
                case INFO->Material.LIGHT_BLUE_CONCRETE; case MANUAL->Material.PURPLE_CONCRETE; };
            buttons.add(button(GRID[i%GRID.length],icon,f.severity()+": "+readable(f.code()),lore,p->{}));
        }
        navigation(buttons,page,pages,p->report(p,page-1),p->report(p,page+1));
        buttons.add(button(49,Material.ARROW,"Quality Desk",List.of(),this::open));
        ui.open(player,READ_PERMISSION,"Content Validation",buttons);
    }
    private void workboard(Player player,int requested) {
        if(board==null) {
            ui.open(player,READ_PERMISSION,"Workboard Unavailable",List.of(
                button(22,Material.RED_CONCRETE,"Notes Preserved",wrap(boardFailure),p->{}),
                button(49,Material.ARROW,"Quality Desk",List.of(),this::open))); return;
        }
        var items=board.all(); int pages=pages(items.size()),page=Math.min(requested,pages-1);
        var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(button(4,Material.WRITABLE_BOOK,"Workboard "+(page+1)+"/"+pages,List.of(items.size()+"/256 active items | "+board.archivedCount()+" archived",
            "Tracking is optional; publishing stays direct."),p->workboard(p,page)));
        for(int i=page*GRID.length;i<Math.min(items.size(),(page+1)*GRID.length);i++) {
            var item=items.get(i); Material icon=switch(item.state()) { case OPEN->Material.PAPER;
                case IN_PROGRESS->Material.WRITABLE_BOOK; case DONE->Material.LIME_DYE; };
            buttons.add(button(GRID[i%GRID.length],icon,item.title(),List.of(item.id()+" | "+item.kind(),readable(item.state().name()),
                "Owner: "+owner(item),"References: "+item.references().size()+" | Notes: "+item.notes().size()),p->item(p,item.id())));
        }
        if(player.hasPermission(MANAGE_PERMISSION)) buttons.add(button(47,Material.EMERALD,"New Work Item",List.of("Private input, then confirmation."),this::create));
        navigation(buttons,page,pages,p->workboard(p,page-1),p->workboard(p,page+1));
        buttons.add(button(49,Material.ARROW,"Quality Desk",List.of(),this::open));
        ui.open(player,READ_PERMISSION,"Team Workboard",buttons);
    }
    private void create(Player player) {
        if(board==null) { Chat.error(player, Chat.escape(boardFailure)); return; }
        ui.ask(player,MANAGE_PERMISSION,"Enter id | TASK, BUG or REVIEW | short title",text->{
            String[] fields=text.split("\\|",3);
            if(fields.length!=3) throw new IllegalArgumentException("Use id | TASK, BUG or REVIEW | title.");
            String id=fields[0].strip(),title=WorkItem.text(fields[2].strip(),96,"Title");
            WorkItem.Kind kind=WorkItem.Kind.valueOf(fields[1].strip().toUpperCase(Locale.ROOT));
            ui.confirm(player,MANAGE_PERMISSION,"Create Work Item",List.of(id,kind.name(),title),p->write(p,()->{
                board.create(id,title,kind,p.getUniqueId()); item(p,id);
            }));
        });
    }
    private void item(Player player,String id) {
        var expected=board.find(id).orElse(null);
        if(expected==null) { Chat.error(player, "That work item no longer exists."); workboard(player,0); return; }
        var buttons=new ArrayList<StudioUi.Button>();
        var details=new ArrayList<>(List.of(id+" | "+expected.kind(),readable(expected.state().name()),"Revision "+expected.revision(),
            "Owner: "+owner(expected),"Updated "+expected.updatedAt()));
        expected.references().forEach(ref->details.add(ref.toString()));
        buttons.add(button(4,Material.WRITABLE_BOOK,expected.title(),details,p->item(p,id)));
        if(player.hasPermission(MANAGE_PERMISSION)) {
            buttons.add(button(10,Material.PLAYER_HEAD,"Assign to Me",List.of(),p->confirm(p,expected,"Assign Work Item",List.of(id),
                ()->board.assign(id,expected.revision(),p.getUniqueId()))));
            buttons.add(button(11,Material.GRAY_DYE,"Unassign",List.of(),p->confirm(p,expected,"Unassign Work Item",List.of(id),
                ()->board.assign(id,expected.revision(),null))));
            buttons.add(button(13,Material.FEATHER,"Add Note",List.of("The latest 32 notes are retained."),p->ui.ask(p,MANAGE_PERMISSION,
                "Write a private team note (up to 400 characters).",text->{ String note=WorkItem.text(text,400,"Note");
                    confirm(p,expected,"Save Team Note",wrap(note),()->board.note(id,expected.revision(),p.getUniqueId(),note)); })));
            buttons.add(button(15,Material.IRON_BARS,"Attach Reference",List.of("Example: ITEM:ember_sword"),p->ui.ask(p,MANAGE_PERMISSION,
                "Enter TYPE:id (ITEM, SET, MOB, LOOT, ZONE, DIGSITE, NPC, DIALOGUE, SHOP, RECIPE, WARP, QUEST or FACTION).",text->{
                    ContentRef ref=ContentRef.parse(text.strip());
                    if(!DomainAuditSnapshot.capture(plugin).known().contains(ref)) throw new IllegalArgumentException("That reference is not in the current catalog.");
                    confirm(p,expected,"Attach Reference",List.of(ref.toString()),()->board.reference(id,expected.revision(),ref)); })));
            buttons.add(button(16,Material.SHEARS,"Remove Reference",List.of(),p->ui.ask(p,MANAGE_PERMISSION,"Enter the attached TYPE:id to remove.",text->{
                ContentRef ref=ContentRef.parse(text.strip()); confirm(p,expected,"Remove Reference",List.of(ref.toString()),
                    ()->board.unreference(id,expected.revision(),ref)); })));
            int index=0; int[] slots={20,22,24};
            for(var state:WorkItem.State.values()) buttons.add(button(slots[index++],state==WorkItem.State.DONE?Material.LIME_DYE:Material.PAPER,
                readable(state.name()),List.of("Current: "+readable(expected.state().name())),p->confirm(p,expected,"Change Work State",
                    List.of(expected.title(),readable(expected.state().name())+" -> "+readable(state.name()),"No content is published or approved."),
                    ()->board.transition(id,expected.revision(),state))));
            if(expected.state()==WorkItem.State.DONE) buttons.add(button(37,Material.CHEST,"Archive Completed Item",
                List.of("Preserve this revision and all retained notes.","Frees an active workboard slot; no data is deleted."),p->ui.confirm(p,MANAGE_PERMISSION,
                    "Archive Completed Item",List.of(expected.title(),"Saves an immutable revision snapshot before removing the active entry.",
                        "Its ID remains reserved. Archive records stay in plugin-data backups."),actor->write(actor,()->{
                            board.archive(id,expected.revision()); workboard(actor,0);
                        }))));
        }
        int slot=28;
        for(var note:expected.notes().reversed().stream().limit(7).toList()) {
            var lore=new ArrayList<>(wrap(note.text())); lore.add("By "+note.author().toString().substring(0,8)); lore.add(note.at().toString());
            buttons.add(button(slot++,Material.PAPER,"Team Note",lore,p->{}));
        }
        buttons.add(button(40,Material.BOOK,"Read All Notes",List.of("Send notes privately to your chat."),p->board.find(id).ifPresent(latest->
            latest.notes().forEach(note->Chat.info(p, note.at()+" | "+note.author().toString().substring(0,8)+" | <white>"+Chat.escape(note.text())+"</white>")))));
        buttons.add(button(49,Material.ARROW,"Workboard",List.of(),p->workboard(p,0)));
        ui.open(player,READ_PERMISSION,"Work Item",buttons);
    }
    private void confirm(Player player,WorkItem expected,String title,List<String> lore,IoAction action) {
        ui.confirm(player,MANAGE_PERMISSION,title,lore,p->write(p,()->{ action.run(); item(p,expected.id()); }));
    }
    private void lootTables(Player player,int requested) {
        var profiles=plugin.rewardDrops().profiles().all(); int pages=pages(profiles.size()),page=Math.min(requested,pages-1);
        var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(button(4,Material.HOPPER,"Loot Tables "+(page+1)+"/"+pages,List.of("Preview only. No rewards are delivered."),p->lootTables(p,page)));
        for(int i=page*GRID.length;i<Math.min(profiles.size(),(page+1)*GRID.length);i++) {
            var profile=profiles.get(i); buttons.add(button(GRID[i%GRID.length],Material.HOPPER,profile.id(),List.of(profile.mode().name(),
                profile.entries().size()+" entries","Preview 1,000 deterministic rolls."),p->simulation(p,profile.id())));
        }
        navigation(buttons,page,pages,p->lootTables(p,page-1),p->lootTables(p,page+1));
        buttons.add(button(49,Material.ARROW,"Quality Desk",List.of(),this::open));
        ui.open(player,READ_PERMISSION,"Loot Simulation",buttons);
    }
    private void simulation(Player player,String id) {
        var profile=plugin.rewardDrops().profiles().find(id).orElse(null);
        if(profile==null) { Chat.error(player, "That loot table no longer exists."); lootTables(player,0); return; }
        var result=LootSimulation.simulate(profile,seed(id)); var buttons=new ArrayList<StudioUi.Button>();
        buttons.add(button(4,Material.HOPPER,id+" — 1,000 Trials",List.of("Expected Marks/kill: "+format(result.expectedMarksPerKill()),
            "Sampled Marks/kill: "+format(result.sampledMarksPerKill()),"Seed: "+result.seed(),"Only this table is simulated; other drops excluded."),p->simulation(p,id)));
        int index=0;
        for(var entry:result.entries().stream().limit(GRID.length).toList()) buttons.add(button(GRID[index++],Material.PAPER,entry.id(),
            List.of(entry.kind()+": "+entry.source(),"Successful rolls: "+entry.successfulRolls()+"/1,000","Quantity rolled: "+entry.quantity(),
                "Expected/kill: "+format(entry.expectedPerKill()),"Unidentified: "+entry.unidentified()+" | Affixes: "+entry.affixes()),p->{}));
        buttons.add(button(47,Material.BOOK,"Full Private Report",List.of("Includes all "+result.entries().size()+" entries."),p->showSimulation(p,result)));
        buttons.add(button(49,Material.ARROW,"Loot Tables",List.of(),p->lootTables(p,0)));
        ui.open(player,READ_PERMISSION,"Loot Preview",buttons);
    }
    private void readiness(Player player) {
        ui.open(player,READ_PERMISSION,"Release Review",List.of(
            button(11,Material.COMPASS,"Player Journeys",List.of("Real-client progression and party encounters.","Multiplayer failures need acceptance evidence."),p->net.nerdsmp.paper.release.ReleaseReadiness.show(plugin,p)),
            button(13,Material.CHEST,"Persistence and Recovery",List.of("Crash boundaries and backup restoration.","A content scan cannot clear these checks."),p->net.nerdsmp.paper.release.ReleaseReadiness.show(plugin,p)),
            button(15,Material.SPYGLASS,"Security and Load",List.of("Permissions, authentication and store lifecycle.","Test occupied encounters under load."),p->net.nerdsmp.paper.release.ReleaseReadiness.show(plugin,p)),
            button(31,Material.BOOK,"Current Release Review",List.of("Read-only diagnostics. Not launch approval."),p->net.nerdsmp.paper.release.ReleaseReadiness.show(plugin,p)),
            button(49,Material.ARROW,"Quality Desk",List.of(),this::open)));
    }
    private void write(Player player,IoAction action) {
        if(!ui.allowed(player,MANAGE_PERMISSION)) return;
        if(board==null) { Chat.error(player, Chat.escape(boardFailure)); return; }
        try { action.run(); }
        catch(IOException failure) { plugin.getLogger().log(Level.WARNING,"Workboard write failed; existing snapshot retained",failure);
            Chat.error(player, "The active board was not changed. Staff should check storage; any saved archives are preserved for retry."); }
        catch(IllegalArgumentException | IllegalStateException failure) { Chat.error(player, Chat.escape(failure.getMessage())); }
    }
    private static void showReport(CommandSender sender,ContentValidator.Report report,int page) {
        Chat.info(sender, "Content review: <white>"+report.count(ContentValidator.Severity.ERROR)+"</white> errors, <white>"+report.count(ContentValidator.Severity.WARNING)+"</white> warnings. Not release approval.");
        long start=(long)page*20;
        if(start>=report.findings().size() && page!=0) { Chat.error(sender, "That report page does not exist."); return; }
        report.findings().stream().skip(start).limit(20).forEach(f->Chat.info(sender, "<white>"+Chat.humanize(f.severity().name())+"</white> | "+Chat.escape(f.content()==null?"server":f.content().toString())+" | "+Chat.escape(f.message())));
        Chat.info(sender, "Page <white>"+(page+1)+"</white>; "+Chat.syntax("/content qa validate <page>")+" | omitted: <white>"+report.omitted()+"</white>");
    }
    private static void showSimulation(CommandSender sender,LootSimulation.Result result) {
        Chat.info(sender, "<white>"+Chat.escape(result.profileId())+"</white> | 1,000 simulated rolls | seed "+result.seed()+" | no rewards delivered");
        result.entries().forEach(entry->Chat.info(sender, "<white>"+Chat.escape(entry.id())+"</white>: "+entry.successfulRolls()+"/1,000 hits, "+entry.quantity()+" quantity; expected/kill "+format(entry.expectedPerKill())+" | "+Chat.escape(String.valueOf(entry.source()))));
        Chat.info(sender, "Expected Marks/kill <gold>"+format(result.expectedMarksPerKill())+"</gold>; sampled <gold>"+format(result.sampledMarksPerKill())+"</gold>. Vanilla/default rewards excluded.");
    }
    private static boolean allowed(CommandSender sender,String permission) {
        if(sender.hasPermission(permission)) return true; Chat.noPermission(sender); return false;
    }
    private static StudioUi.Button button(int slot,Material icon,String title,List<String> lore,Consumer<Player> action) {
        return new StudioUi.Button(slot,StudioUi.card(icon,title,lore),action);
    }
    private static int pages(int count) { return Math.max(1,(count+GRID.length-1)/GRID.length); }
    private static void navigation(List<StudioUi.Button> buttons,int page,int pages,Consumer<Player> previous,Consumer<Player> next) {
        if(page>0) buttons.add(button(45,Material.ARROW,"Previous Page",List.of(),previous));
        if(page+1<pages) buttons.add(button(53,Material.ARROW,"Next Page",List.of(),next));
    }
    private static String owner(WorkItem item) { return item.owner()==null?"Unassigned":item.owner().toString().substring(0,8); }
    private static long seed(String id) { return 0x484541525448L ^ id.hashCode(); }
    private static String readable(String text) { String value=text.replace('_',' ').toLowerCase(Locale.ROOT); return Character.toUpperCase(value.charAt(0))+value.substring(1); }
    private static String format(double value) { return String.format(Locale.ROOT,"%.2f",value); }
    private static List<String> wrap(String text) {
        var lines=new ArrayList<String>(); var line=new StringBuilder();
        for(String word:text.split("\\s+")) { if(!line.isEmpty() && line.length()+word.length()+1>48) { lines.add(line.toString()); line.setLength(0); }
            if(!line.isEmpty()) line.append(' '); line.append(word); }
        if(!line.isEmpty()) lines.add(line.toString()); return List.copyOf(lines);
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
