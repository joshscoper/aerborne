package net.nerdsmp.paper.studio;

import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.digsite.DigSiteDefinition;
import net.nerdsmp.paper.travel.TravelManager;
import net.nerdsmp.paper.zone.AreaSelection;
import net.nerdsmp.paper.zone.ZoneCommand;
import net.nerdsmp.paper.zone.ZoneDefinition;
import net.nerdsmp.paper.zone.ZoneMobTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Staff controls for manually built shared worlds; never generates terrain or teleports staff. */
public final class WorldStudio {
    public static final String PERMISSION = "nerdsmp.studio.world";
    private static final String NPC_PERMISSION = "nerdsmp.npc.admin", TRAVEL_PERMISSION = "nerdsmp.travel.admin";
    private static final int[] GRID = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private final NerdSmpPlugin plugin;
    private final StudioUi ui;

    public WorldStudio(NerdSmpPlugin plugin, StudioUi ui) { this.plugin=Objects.requireNonNull(plugin); this.ui=Objects.requireNonNull(ui); }

    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Chat.error(sender, "Open " + Chat.command("worldstudio") + " in game to author your world."); return true; }
        if (!access(player,PERMISSION)) return true;
        if (args.length==0) open(player);
        else switch (args[0].toLowerCase(Locale.ROOT)) {
            case "zones" -> zones(player,0);
            case "zone" -> { if(args.length==2) zone(player,args[1]); else Chat.usage(player, "/worldstudio zone <id>"); }
            case "digsites" -> digSites(player,0);
            case "warps" -> warps(player,0);
            case "npcs" -> npcCatalog(player);
            default -> Chat.usage(player, "/worldstudio [zones|zone <id>|digsites|warps|npcs]");
        }
        return true;
    }

    public void open(Player player) {
        if(!access(player,PERMISSION)) return;
        ui.open(player,PERMISSION,"World workshop",List.of(
            button(4,Material.MAP,"World workshop",List.of("Build the world by hand, then bring it to life.","These tools do not generate terrain."),p->{}),
            button(19,Material.IRON_SWORD,"Encounter zones",List.of("Author regions, spawn points, and weighted rosters.","Tune levels, population, and respawn timing."),p->zones(p,0)),
            button(21,Material.IRON_SHOVEL,"Dig sites",List.of("Capture existing soil with your area selection.","Manage renewal without replacing your build."),p->digSites(p,0)),
            button(23,Material.VILLAGER_SPAWN_EGG,"NPC catalogue",List.of("Browse shopkeepers, guides, and quest givers.","Inspect a definition, then choose Place here."),this::npcCatalog),
            button(25,Material.ENDER_PEARL,"Warps and spawn",List.of("Set public destinations at your feet.","Nothing here teleports you."),p->warps(p,0)),
            button(39,Material.STICK,"Get area wand",List.of("Left-click a block for position 1.","Right-click a block for position 2."),p->zoneCommand(p,"wand")),
            button(41,Material.SPYGLASS,"Show selection",List.of("Inspect and outline your current cuboid.","No blocks, entities, or chunks are changed."),p->zoneCommand(p,"selection")),
            button(49,Material.ARROW,"Content team tools",List.of(),ui::home)));
    }

    private void zones(Player player,int requested) {
        if(!readyZones(player)) return;
        List<ZoneDefinition> all=plugin.zones().all(); int page=WorldStudioEdits.pageNumber(all.size(),requested);
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(0,Material.WRITABLE_BOOK,"New zone from selection",List.of("Select two corners with the area wand.","New zones stay disabled until you enable them."),this::createZone));
        buttons.add(button(4,Material.MAP,"Encounter zones",List.of(all.size()+" saved zones", "Page "+(page+1)+" / "+WorldStudioEdits.pages(all.size())),p->{}));
        buttons.add(button(8,Material.STICK,"Area wand",List.of("Left-click position 1; right-click position 2."),p->zoneCommand(p,"wand")));
        List<ZoneDefinition> visible=WorldStudioEdits.page(all,page);
        for(int i=0;i<visible.size();i++) { ZoneDefinition z=visible.get(i);
            buttons.add(button(GRID[i],z.enabled()?z.safe()?Material.OAK_SAPLING:Material.IRON_SWORD:Material.GRAY_DYE,z.title(),List.of(z.id(),state(z),z.points().size()+" points · "+z.mobs().size()+" roster entries","Click to inspect and edit."),p->zone(p,z.id())));
        }
        pagination(buttons,page,all.size(),p->zones(p,page-1),p->zones(p,page+1),this::open);
        ui.open(player,PERMISSION,"Encounter zones",buttons);
    }

    private void zone(Player player,String id) {
        if(!readyZones(player)) return;
        ZoneDefinition z=plugin.zones().find(id).orElse(null);
        if(z==null) { Chat.error(player, "That zone no longer exists."); zones(player,0); return; }
        ui.open(player,PERMISSION,"Zone: "+z.title(),List.of(
            button(4,Material.MAP,z.title(),List.of(z.id()+" · "+state(z),"World: "+worldName(z),bounds(z.bounds()),"Every spawn point uses this zone's roster."),p->{}),
            button(8,Material.SPYGLASS,"Outline zone",List.of("One particle outline, visible only to you.","Nearby loaded chunks only; no teleport."),p->preview(p,z)),
            button(20,Material.ENDER_PEARL,"Spawn points",List.of(z.points().size()+" / "+ZoneDefinition.MAX_POINTS,"Add a point at your feet or remove an old point."),p->points(p,z,0)),
            button(22,Material.ZOMBIE_SPAWN_EGG,"Mob roster",List.of(z.mobs().size()+" / "+ZoneDefinition.MAX_MOBS,"Choose a mob, level, and spawn weight."),p->roster(p,z,0)),
            button(24,Material.NAME_TAG,"Zone title",List.of(z.title(),"1–64 plain-text characters."),p->askField(p,z,WorldStudioEdits.Field.TITLE,"Zone title (1–64 characters):")),
            button(29,Material.IRON_BARS,"Population cap",List.of("Current: "+z.cap(),"1–64 active mobs in this zone."),p->askField(p,z,WorldStudioEdits.Field.CAP,"Mob cap (1–64):")),
            button(31,Material.CLOCK,"Respawn delay",List.of("Current: "+z.respawnSeconds()+" seconds","5–3,600 seconds between attempts."),p->askField(p,z,WorldStudioEdits.Field.RESPAWN,"Respawn delay in seconds (5–3,600):")),
            button(33,Material.COMPASS,"Activation radius",List.of("Current: "+z.activationRadius()+" blocks","8–128 blocks; loaded chunks only."),p->askField(p,z,WorldStudioEdits.Field.RADIUS,"Activation radius in blocks (8–128):")),
            button(38,Material.OAK_SAPLING,z.safe()?"Peaceful area: on":"Peaceful area: off",List.of("Peaceful zones do not spawn encounters.","Changing this setting requires confirmation."),p->switchField(p,z,WorldStudioEdits.Field.SAFE,z.safe()?"off":"on")),
            button(40,z.enabled()?Material.LIME_DYE:Material.GRAY_DYE,z.enabled()?"Disable zone":"Enable zone",List.of(z.enabled()?"Stops this zone's encounters.":"Starts spawning when nearby players activate it.","Your terrain and NPCs stay untouched."),p->switchField(p,z,WorldStudioEdits.Field.ENABLED,z.enabled()?"off":"on")),
            button(42,Material.BARRIER,"Remove zone",List.of("Deletes the saved zone definition.","Does not delete terrain, builds, or NPCs.","Attached dig sites stop while their parent is absent."),p->deleteZone(p,z)),
            button(49,Material.ARROW,"Zones",List.of(),p->zones(p,0))));
    }

    private void createZone(Player player) {
        if(!readyZones(player)||!access(player,ZoneCommand.PERMISSION)) return;
        AreaSelection.Selection selected=selection(player); if(selected==null) return;
        List<ZoneDefinition> snapshot=plugin.zones().all();
        ui.ask(player,ZoneCommand.PERMISSION,"New zone ID (lowercase letters, numbers, _ or -):",id->{
            if(!selectionCurrent(player,selected)||!snapshot.equals(plugin.zones().all())) { Chat.notice(player, "The selection or zone list changed. Start again."); return; }
            plugin.zoneCommands().command(player,new String[]{"create",id.strip()}); zones(player,0);
        });
    }
    private void askField(Player player,ZoneDefinition expected,WorldStudioEdits.Field field,String prompt) {
        if(!currentZone(player,expected)) return;
        ui.ask(player,ZoneCommand.PERMISSION,prompt,value->{
            if(!currentZone(player,expected)) return;
            try { saveZone(player,expected,WorldStudioEdits.field(expected,field,value.strip())); }
            catch(IllegalArgumentException invalid) { Chat.error(player, Chat.escape(invalid.getMessage())); zone(player,expected.id()); }
        });
    }
    private void switchField(Player player,ZoneDefinition expected,WorldStudioEdits.Field field,String value) {
        if(!currentZone(player,expected)) return;
        String title=field==WorldStudioEdits.Field.ENABLED?value.equals("on")?"Enable this zone?":"Disable this zone?":value.equals("on")?"Make this area peaceful?":"Enable combat in this area?";
        ui.confirm(player,ZoneCommand.PERMISSION,title,List.of(expected.title(),"Terrain, builds, and NPCs are not changed.","Active encounters will follow the new setting."),p->{
            if(!currentZone(p,expected)) return;
            try { saveZone(p,expected,WorldStudioEdits.field(expected,field,value)); }
            catch(IllegalArgumentException invalid) { Chat.error(p, Chat.escape(invalid.getMessage())); zone(p,expected.id()); }
        });
    }
    private void deleteZone(Player player,ZoneDefinition expected) {
        if(!currentZone(player,expected)) return;
        long dependents=plugin.digSites()==null?0:plugin.digSites().store().all().stream().filter(d->d.zoneId().equals(expected.id())).count();
        ui.confirm(player,ZoneCommand.PERMISSION,"Remove "+expected.title()+"?",List.of("Removes only this saved zone definition.","Terrain, builds, and NPCs stay untouched.",dependents+" attached dig sites will become inactive."),p->{
            if(!currentZone(p,expected)) return;
            plugin.zoneCommands().command(p,new String[]{"delete",expected.id(),"confirm"}); zones(p,0);
        });
    }

    private void points(Player player,ZoneDefinition z,int requested) {
        if(!currentZone(player,z)) return;
        int page=WorldStudioEdits.pageNumber(z.points().size(),requested);
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(4,Material.COMPASS,"Spawn points",List.of(z.title(),"Points draw from the same weighted mob roster.","A point needs a clear, supported landing."),p->{}));
        buttons.add(button(8,Material.LIME_DYE,"Add point at my feet",List.of("Records your exact position and facing.","Stand inside this zone first."),p->{
            if(!currentZone(p,z)) return;
            if(!p.getWorld().getUID().equals(z.worldId())) { Chat.error(p, "Stand in this zone's world first."); return; }
            Location here=p.getLocation();
            try { saveZone(p,z,WorldStudioEdits.addPoint(z,new ZoneDefinition.SpawnPoint(here.getX(),here.getY(),here.getZ(),here.getYaw()))); }
            catch(IllegalArgumentException invalid) { Chat.error(p, Chat.escape(invalid.getMessage())); points(p,z,page); }
        }));
        List<ZoneDefinition.SpawnPoint> visible=WorldStudioEdits.page(z.points(),page);
        for(int i=0;i<visible.size();i++) { int index=page*WorldStudioEdits.PAGE_SIZE+i; ZoneDefinition.SpawnPoint point=visible.get(i);
            buttons.add(button(GRID[i],Material.ENDER_PEARL,"Point "+(index+1),List.of(position(point),"Facing: "+Math.round(point.yaw())+"°","Click to inspect or remove; no teleport."),p->point(p,z,index)));
        }
        pagination(buttons,page,z.points().size(),p->points(p,z,page-1),p->points(p,z,page+1),p->zone(p,z.id()));
        ui.open(player,ZoneCommand.PERMISSION,"Spawn points: "+z.title(),buttons);
    }
    private void point(Player player,ZoneDefinition z,int index) {
        if(!currentZone(player,z)) return;
        ZoneDefinition.SpawnPoint point=z.points().get(index);
        ui.open(player,ZoneCommand.PERMISSION,"Spawn point "+(index+1),List.of(
            button(4,Material.ENDER_PEARL,"Point "+(index+1),List.of(position(point),"Every point uses the full zone roster."),p->{}),
            button(20,Material.SPYGLASS,"Highlight point",List.of("Nearby loaded world only.","Does not spawn a mob or move you."),p->{ if(currentZone(p,z)) particle(p,z.worldId(),point.x(),point.y()+.2,point.z()); }),
            button(24,Material.BARRIER,"Remove point",List.of("Confirmation required.","Disable a combat zone before removing its last point."),p->ui.confirm(p,ZoneCommand.PERMISSION,"Remove spawn point "+(index+1)+"?",List.of(z.title(),position(point)),actor->{
                if(!currentZone(actor,z)) return;
                try { saveZone(actor,z,WorldStudioEdits.removePoint(z,index)); }
                catch(IllegalArgumentException invalid) { Chat.error(actor, Chat.escape(invalid.getMessage())); points(actor,z,0); }
            })),
            button(49,Material.ARROW,"Spawn points",List.of(),p->points(p,z,0))));
    }

    private void roster(Player player,ZoneDefinition z,int requested) {
        if(!currentZone(player,z)) return;
        int page=WorldStudioEdits.pageNumber(z.mobs().size(),requested); long total=z.mobs().stream().mapToLong(ZoneDefinition.MobEntry::weight).sum();
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(4,Material.ZOMBIE_SPAWN_EGG,"Weighted roster",List.of(z.title(),"Higher weight means a larger share of spawn rolls.","Each point uses this full roster."),p->{}));
        buttons.add(button(8,Material.LIME_DYE,"Add mob",List.of("Browse authored and vanilla mob definitions."),p->catalog(p,z,"all","",0)));
        List<ZoneDefinition.MobEntry> visible=WorldStudioEdits.page(z.mobs(),page);
        for(int i=0;i<visible.size();i++) { ZoneDefinition.MobEntry entry=visible.get(i);
            buttons.add(button(GRID[i],entry.mobId().startsWith("custom:")?Material.NETHER_STAR:Material.ZOMBIE_SPAWN_EGG,entry.mobId(),List.of("Level "+entry.level()+" · weight "+entry.weight(),String.format(Locale.ROOT,"%.1f%% of spawn rolls",100d*entry.weight()/total),"Click to edit this roster entry."),p->mobDraft(p,z,entry)));
        }
        pagination(buttons,page,z.mobs().size(),p->roster(p,z,page-1),p->roster(p,z,page+1),p->zone(p,z.id()));
        ui.open(player,ZoneCommand.PERMISSION,"Roster: "+z.title(),buttons);
    }

    private void catalog(Player player,ZoneDefinition z,String kind,String query,int requested) {
        if(!currentZone(player,z)) return;
        ArrayList<String> ids=new ArrayList<>(ZoneMobTypes.vanillaIds());
        plugin.mobs().ids().forEach(id->ids.add("custom:"+id));
        List<String> entries=ids.stream().distinct().filter(id->kind.equals("all")||id.startsWith(kind+":"))
            .filter(id->id.contains(query.toLowerCase(Locale.ROOT))).filter(id->{try {plugin.mobs().zoneType(id);return true;}catch(IllegalArgumentException invalid){return false;}}).sorted().toList();
        int page=WorldStudioEdits.pageNumber(entries.size(),requested);
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(0,Material.BOOK,"All mobs",List.of(),p->catalog(p,z,"all",query,0)));
        buttons.add(button(2,Material.NETHER_STAR,"Authored mobs",List.of("Definitions published through the mob engine."),p->catalog(p,z,"custom",query,0)));
        buttons.add(button(4,Material.ZOMBIE_SPAWN_EGG,"Vanilla mobs",List.of("Reviewed ordinary creature types."),p->catalog(p,z,"vanilla",query,0)));
        buttons.add(button(8,Material.SPYGLASS,"Search",List.of(query.isEmpty()?"Showing all IDs":"Search: "+query,"Enter - to clear the filter."),p->ui.ask(p,ZoneCommand.PERMISSION,"Search mob IDs, or - to clear:",value->{ if(currentZone(p,z)) catalog(p,z,kind,value.strip().equals("-")?"":value.strip(),0); })));
        List<String> visible=WorldStudioEdits.page(entries,page);
        for(int i=0;i<visible.size();i++) { String id=visible.get(i); ZoneDefinition.MobEntry entry=z.mobs().stream().filter(m->m.mobId().equals(id)).findFirst().orElse(new ZoneDefinition.MobEntry(id,1,10));
            buttons.add(button(GRID[i],id.startsWith("custom:")?Material.NETHER_STAR:Material.ZOMBIE_SPAWN_EGG,id,List.of("Choose level and weight before saving.","This does not spawn a preview mob."),p->mobDraft(p,z,entry)));
        }
        pagination(buttons,page,entries.size(),p->catalog(p,z,kind,query,page-1),p->catalog(p,z,kind,query,page+1),p->roster(p,z,0));
        ui.open(player,ZoneCommand.PERMISSION,"Mob catalogue",buttons);
    }

    private void mobDraft(Player player,ZoneDefinition z,ZoneDefinition.MobEntry entry) {
        if(!currentZone(player,z)) return;
        boolean exists=z.mobs().stream().anyMatch(m->m.mobId().equals(entry.mobId()));
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(4,Material.ZOMBIE_SPAWN_EGG,entry.mobId(),List.of(z.title(),"Draft settings; save to apply."),p->{}));
        buttons.add(button(20,Material.EXPERIENCE_BOTTLE,"Level: "+entry.level(),List.of("Authored level from 1 to 100."),p->ui.ask(p,ZoneCommand.PERMISSION,"Mob level (1–100):",value->{
            if(!currentZone(p,z)) return;
            try {mobDraft(p,z,new ZoneDefinition.MobEntry(entry.mobId(),entry.weight(),WorldStudioEdits.number(value)));}
            catch(IllegalArgumentException invalid) { Chat.error(p, Chat.escape(invalid.getMessage())); mobDraft(p,z,entry); }
        })));
        buttons.add(button(24,Material.GOLD_NUGGET,"Weight: "+entry.weight(),List.of("Relative spawn weight from 1 to 10,000."),p->ui.ask(p,ZoneCommand.PERMISSION,"Spawn weight (1–10,000):",value->{
            if(!currentZone(p,z)) return;
            try {mobDraft(p,z,new ZoneDefinition.MobEntry(entry.mobId(),WorldStudioEdits.number(value),entry.level()));}
            catch(IllegalArgumentException invalid) {Chat.error(p, Chat.escape(invalid.getMessage())); mobDraft(p,z,entry);}
        })));
        buttons.add(button(31,Material.LIME_DYE,"Save roster entry",List.of("Level "+entry.level()+" · weight "+entry.weight(),z.enabled()?"This changes an active zone.":"The zone remains disabled until enabled."),p->ui.confirm(p,ZoneCommand.PERMISSION,"Save this roster entry?",List.of(entry.mobId(),"Level "+entry.level()+" · weight "+entry.weight(),"All spawn points will use the updated roster."),actor->{
            if(!currentZone(actor,z)) return;
            try {plugin.mobs().zoneType(entry.mobId()); saveZone(actor,z,WorldStudioEdits.roster(z,entry));}
            catch(IllegalArgumentException invalid) {Chat.error(actor, Chat.escape(invalid.getMessage())); roster(actor,z,0);}
        })));
        if(exists) buttons.add(button(42,Material.BARRIER,"Remove from roster",List.of("Disable a combat zone before removing its last mob."),p->ui.confirm(p,ZoneCommand.PERMISSION,"Remove this mob?",List.of(entry.mobId(),z.title()),actor->{
            if(!currentZone(actor,z)) return;
            try {saveZone(actor,z,WorldStudioEdits.removeMob(z,entry.mobId()));}
            catch(IllegalArgumentException invalid) {Chat.error(actor, Chat.escape(invalid.getMessage())); roster(actor,z,0);}
        })));
        buttons.add(button(49,Material.ARROW,"Discard draft / roster",List.of(),p->roster(p,z,0)));
        ui.open(player,ZoneCommand.PERMISSION,"Roster entry",buttons);
    }

    private void digSites(Player player,int requested) {
        if(!access(player,PERMISSION)) return;
        if(plugin.digSites()==null) { Chat.error(player, "Dig-site controls are available in Skyblock mode."); return; }
        List<DigSiteDefinition> all=plugin.digSites().store().all(); int page=WorldStudioEdits.pageNumber(all.size(),requested);
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(0,Material.LIME_DYE,"Capture selected soil",List.of("Captures existing eligible soil only.","Renewal begins after you confirm the selection.","Maximum 32,768 blocks scanned; loaded chunks only."),this::createDigSite));
        buttons.add(button(4,Material.IRON_SHOVEL,"Dig sites",List.of(all.size()+" saved sites","Terrain is never generated or replaced."),p->{}));
        buttons.add(button(8,Material.STICK,"Area wand",List.of("Select opposite corners around your dig site."),p->zoneCommand(p,"wand")));
        List<DigSiteDefinition> visible=WorldStudioEdits.page(all,page);
        for(int i=0;i<visible.size();i++) { DigSiteDefinition site=visible.get(i);
            buttons.add(button(GRID[i],site.enabled()?Material.IRON_SHOVEL:Material.GRAY_DYE,site.id(),List.of("Parent zone: "+site.zoneId(),site.nodes().size()+" soil nodes · "+site.cooldownSeconds()+"s renewal",site.enabled()?"Enabled":"Disabled"),p->digSite(p,site)));
        }
        pagination(buttons,page,all.size(),p->digSites(p,page-1),p->digSites(p,page+1),this::open);
        ui.open(player,PERMISSION,"Dig sites",buttons);
    }

    private void createDigSite(Player player) {
        if(!access(player,ZoneCommand.PERMISSION)||plugin.digSites()==null) return;
        AreaSelection.Selection selected=selection(player); if(selected==null) return;
        List<ZoneDefinition> zones=plugin.zones().all(); List<DigSiteDefinition> sites=plugin.digSites().store().all();
        ui.ask(player,ZoneCommand.PERMISSION,"Dig-site ID (lowercase letters, numbers, _ or -):",raw->{
            String id=raw.strip();
            if(!DigSiteDefinition.validId(id)) {Chat.error(player, "Use 1–48 lowercase letters, numbers, _ or -."); return;}
            ui.ask(player,ZoneCommand.PERMISSION,"Renewal delay in seconds (5–3,600):",value->{
                int seconds;
                try {seconds=WorldStudioEdits.number(value); if(seconds<5||seconds>3600) throw new IllegalArgumentException("Renewal delay must be 5–3,600 seconds.");}
                catch(IllegalArgumentException invalid) {Chat.error(player, Chat.escape(invalid.getMessage())); return;}
                ui.confirm(player,ZoneCommand.PERMISSION,"Capture dig site "+id+"?",List.of(bounds(selected.bounds()),selected.volume()+" blocks selected · "+seconds+"s renewal","Captures soil; starts renewal when its parent is active.","Creates a peaceful parent only if no zone overlaps."),p->{
                    if(!selectionCurrent(p,selected)||!zones.equals(plugin.zones().all())||!sites.equals(plugin.digSites().store().all())) {Chat.notice(p, "The selection, zones, or dig sites changed. Start again."); return;}
                    plugin.digSiteCommands().command(p,new String[]{"create",id,Integer.toString(seconds)}); digSites(p,0);
                });
            });
        });
    }
    private void digSite(Player player,DigSiteDefinition expected) {
        if(!currentDigSite(player,expected)) return;
        var status=plugin.digSites().status(expected.id());
        ui.open(player,ZoneCommand.PERMISSION,"Dig site: "+expected.id(),List.of(
            button(4,Material.IRON_SHOVEL,expected.id(),List.of("Parent: "+expected.zoneId(),expected.nodes().size()+" nodes · "+expected.cooldownSeconds()+"s renewal","Ready "+status.ready()+" · cooling "+status.cooling(),"Blocked "+status.blocked()+" · inactive "+status.inactive()),p->{}),
            button(20,expected.enabled()?Material.GRAY_DYE:Material.LIME_DYE,expected.enabled()?"Disable renewal":"Enable renewal",List.of("Terrain remains untouched."),p->ui.confirm(p,ZoneCommand.PERMISSION,expected.enabled()?"Disable renewal?":"Enable renewal?",List.of(expected.id(),"Existing terrain is not replaced."),actor->{
                if(!currentDigSite(actor,expected)) return;
                plugin.digSiteCommands().command(actor,new String[]{expected.enabled()?"disable":"enable",expected.id()}); digSites(actor,0);
            })),
            button(22,Material.MAP,"Parent zone",List.of("Inspect the region and encounter settings."),p->zone(p,expected.zoneId())),
            button(24,Material.BARRIER,"Remove dig site",List.of("Stops renewal and removes the saved definition.","No terrain is deleted or restored."),p->ui.confirm(p,ZoneCommand.PERMISSION,"Remove dig site?",List.of(expected.id(),"This does not change terrain."),actor->{
                if(!currentDigSite(actor,expected)) return;
                plugin.digSiteCommands().command(actor,new String[]{"delete",expected.id(),"confirm"}); digSites(actor,0);
            })),
            button(49,Material.ARROW,"Dig sites",List.of(),p->digSites(p,0))));
    }

    private void npcCatalog(Player player) {
        if(!access(player,PERMISSION)||!access(player,NPC_PERMISSION)) return;
        if(plugin.npcs()==null) {Chat.error(player, "The NPC catalogue is not available."); return;}
        ui.cancel(player); plugin.npcs().handleNpcCommand(player,new String[]{"catalog"});
    }

    private void warps(Player player,int requested) {
        if(!access(player,PERMISSION)) return;
        if(plugin.travel()==null) {Chat.error(player, "Travel controls are not available."); return;}
        List<String> names=plugin.travel().warpNames(); int page=WorldStudioEdits.pageNumber(names.size(),requested);
        ArrayList<StudioUi.Button> buttons=new ArrayList<>();
        buttons.add(button(0,Material.LIME_DYE,"Set warp at my feet",List.of("Creates or updates a public warp.","Confirmation required; private islands are excluded."),this::setWarp));
        buttons.add(button(4,Material.COMPASS,"Public destinations",List.of(names.size()+" saved warps","Inspecting a warp never teleports you."),p->{}));
        buttons.add(button(8,Material.BEACON,"Set public spawn here",List.of("Changes the central spawn and world fallback.","Confirmation required; no player is moved."),this::setSpawn));
        List<String> visible=WorldStudioEdits.page(names,page);
        for(int i=0;i<visible.size();i++) {String name=visible.get(i); TravelManager.WarpDefinition saved=plugin.travel().warpDefinition(name).orElse(null);
            if(saved!=null) buttons.add(button(GRID[i],Material.ENDER_PEARL,name,List.of(warpPosition(saved),plugin.travel().warpLocation(name).isPresent()?"World available":"World unavailable; destination stays saved","Click to inspect, replace, or remove."),p->warp(p,name,saved)));
        }
        pagination(buttons,page,names.size(),p->warps(p,page-1),p->warps(p,page+1),this::open);
        ui.open(player,PERMISSION,"Warps and spawn",buttons);
    }
    private void warp(Player player,String name,TravelManager.WarpDefinition expected) {
        if(!currentWarp(player,name,expected)) return;
        ui.open(player,TRAVEL_PERMISSION,"Warp: "+name,List.of(
            button(4,Material.COMPASS,name,List.of(warpPosition(expected),"Inspecting this destination does not load its world."),p->{}),
            button(20,Material.LIME_DYE,"Replace with my position",List.of("Stand where this warp should lead.","Confirmation required."),p->confirmWarp(p,name,expected)),
            button(24,Material.BARRIER,"Remove warp",List.of("Removes only this saved travel destination."),p->ui.confirm(p,TRAVEL_PERMISSION,"Remove warp "+name+"?",List.of(warpPosition(expected)),actor->{
                if(!currentWarp(actor,name,expected)) return;
                plugin.travel().handle("delwarp",actor,new String[]{name}); warps(actor,0);
            })),
            button(49,Material.ARROW,"Warps",List.of(),p->warps(p,0))));
    }
    private void setWarp(Player player) {
        if(!access(player,PERMISSION)||!access(player,TRAVEL_PERMISSION)) return;
        var snapshot=plugin.travel().warpNames().stream().collect(java.util.stream.Collectors.toMap(name->name,name->plugin.travel().warpDefinition(name).orElseThrow()));
        ui.ask(player,TRAVEL_PERMISSION,"Warp name (1–24 letters, numbers, _ or -):",raw->{
            String name=raw.strip().toLowerCase(Locale.ROOT);
            if(!name.matches("[a-z0-9_-]{1,24}")) {Chat.error(player, "Use 1–24 letters, numbers, _ or -."); return;}
            if(!Objects.equals(snapshot.get(name),plugin.travel().warpDefinition(name).orElse(null))) {Chat.notice(player, "This warp changed. Start again."); return;}
            confirmWarp(player,name,snapshot.get(name));
        });
    }
    private void confirmWarp(Player player,String name,TravelManager.WarpDefinition expected) {
        if(!currentWarp(player,name,expected)) return;
        Location here=player.getLocation().clone();
        if(isIsland(here.getWorld())) {Chat.error(player, "Public warps cannot be placed on private islands."); return;}
        ui.confirm(player,TRAVEL_PERMISSION,expected==null?"Create warp "+name+"?":"Replace warp "+name+"?",List.of(here.getWorld().getName()+" · "+locationPosition(here),"Stay at this position until you confirm."),p->{
            if(!currentWarp(p,name,expected)||!stillHere(p,here)) return;
            plugin.travel().handle("setwarp",p,new String[]{name}); warps(p,0);
        });
    }
    private void setSpawn(Player player) {
        if(!access(player,PERMISSION)||!access(player,TRAVEL_PERMISSION)) return;
        Location here=player.getLocation().clone(); if(isIsland(here.getWorld())) {Chat.error(player, "Public spawn cannot be placed on a private island."); return;}
        var expected=plugin.travel().spawnDefinition();
        ui.confirm(player,TRAVEL_PERMISSION,"Set public spawn here?",List.of(here.getWorld().getName()+" · "+locationPosition(here),"Updates central spawn and the world's fallback spawn.","Stay at this position until you confirm."),p->{
            if(!access(p,PERMISSION)||!access(p,TRAVEL_PERMISSION)||!stillHere(p,here)) return;
            if(!expected.equals(plugin.travel().spawnDefinition())) {Chat.notice(p, "Public spawn changed. Start again."); return;}
            plugin.travel().handle("setspawn",p,new String[0]); warps(p,0);
        });
    }

    private void saveZone(Player player,ZoneDefinition expected,ZoneDefinition next) {
        if(!currentZone(player,expected)) return;
        try {
            if(next.enabled()) {
                World world=Bukkit.getWorld(next.worldId());
                if(world==null||isIsland(world)) throw new IllegalArgumentException("Load the shared world before enabling this zone.");
                if(!next.safe()) for(var mob:next.mobs()) plugin.mobs().zoneType(mob.mobId());
            }
            plugin.zones().put(next); Chat.success(player, "Saved <white>"+Chat.escape(next.title())+"</white>."); zone(player,next.id());
        } catch(IOException failed) {
            plugin.getLogger().log(Level.WARNING,"World Studio zone edit could not be saved",failed);
            Chat.error(player, "The edit could not be saved. The previous zone settings are still active."); zone(player,expected.id());
        }
    }
    private boolean currentZone(Player player,ZoneDefinition expected) {
        if(!readyZones(player)||!access(player,ZoneCommand.PERMISSION)) return false;
        try {WorldStudioEdits.requireCurrent(expected,plugin.zones().find(expected.id()).orElse(null)); return true;}
        catch(IllegalStateException stale) {Chat.error(player, Chat.escape(stale.getMessage())); return false;}
    }
    private boolean currentDigSite(Player player,DigSiteDefinition expected) {
        if(!access(player,PERMISSION)||!access(player,ZoneCommand.PERMISSION)||plugin.digSites()==null) return false;
        if(!plugin.digSites().store().find(expected.id()).filter(expected::equals).isPresent()) {Chat.notice(player, "This dig site changed. Open it again before editing."); return false;}
        return true;
    }
    private boolean currentWarp(Player player,String name,TravelManager.WarpDefinition expected) {
        if(!access(player,PERMISSION)||!access(player,TRAVEL_PERMISSION)||plugin.travel()==null) return false;
        if(!Objects.equals(expected,plugin.travel().warpDefinition(name).orElse(null))) {Chat.notice(player, "This warp changed. Open it again before editing."); return false;}
        return true;
    }
    private boolean readyZones(Player player) {
        if(!access(player,PERMISSION)) return false;
        if(plugin.zones()==null||plugin.zoneCommands()==null) {Chat.error(player, "Shared-zone controls are not available."); return false;}
        return true;
    }
    private boolean access(Player player,String permission) {
        if(!ui.allowed(player,permission)) {Chat.noPermission(player); return false;}
        return true;
    }
    private void zoneCommand(Player player,String...args) {
        if(readyZones(player)&&access(player,ZoneCommand.PERMISSION)) plugin.zoneCommands().command(player,args);
    }
    private AreaSelection.Selection selection(Player player) {
        if(!readyZones(player)) return null;
        AreaSelection.Selection selected=plugin.zoneCommands().selections().selection(player.getUniqueId()).orElse(null);
        if(selected==null||!selected.complete()) {Chat.error(player, "Select two corners with the area wand first."); return null;}
        if(!selected.worldId().equals(player.getWorld().getUID())) {Chat.error(player, "Return to the selected world or choose two new corners."); return null;}
        return selected;
    }
    private boolean selectionCurrent(Player player,AreaSelection.Selection expected) {
        if(!access(player,PERMISSION)||!access(player,ZoneCommand.PERMISSION)) return false;
        return expected.worldId().equals(player.getWorld().getUID())&&plugin.zoneCommands().selections().selection(player.getUniqueId()).filter(expected::equals).isPresent();
    }
    private boolean isIsland(World world) {return plugin.skyblock()!=null&&plugin.skyblock().isIslandWorld(world);}
    private boolean stillHere(Player player,Location expected) {
        Location actual=player.getLocation();
        if(!actual.getWorld().getUID().equals(expected.getWorld().getUID())||actual.distanceSquared(expected)>.0001||Float.compare(actual.getYaw(),expected.getYaw())!=0||Float.compare(actual.getPitch(),expected.getPitch())!=0) {
            Chat.notice(player, "Your position or facing changed. Open this action again at the intended location."); return false;
        }
        return true;
    }
    private void preview(Player player,ZoneDefinition z) {
        if(!currentZone(player,z)) return;
        if(!player.getWorld().getUID().equals(z.worldId())) {Chat.error(player, "Stand in this zone's world to see its outline."); return;}
        WorldStudioEdits.outline(z.bounds()).forEach(p->particle(player,z.worldId(),p.x(),p.y(),p.z()));
        z.points().stream().limit(ZoneDefinition.MAX_POINTS).forEach(p->particle(player,z.worldId(),p.x(),p.y()+.2,p.z()));
        Chat.success(player, "Outlined nearby loaded edges and spawn points. No world data was changed.");
    }
    private void particle(Player player,java.util.UUID world,double x,double y,double z) {
        if(!player.getWorld().getUID().equals(world)) return;
        World loaded=player.getWorld(); Location here=player.getLocation();
        if(here.distanceSquared(new Location(loaded,x,y,z))>4096||!loaded.isChunkLoaded(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4)) return;
        player.spawnParticle(Particle.END_ROD,x,y,z,1,0,0,0,0);
    }
    private static StudioUi.Button button(int slot,Material material,String title,List<String> lore,Consumer<Player> action) {return new StudioUi.Button(slot,StudioUi.card(material,title,lore),action);}
    private void pagination(List<StudioUi.Button> buttons,int page,int count,Consumer<Player> previous,Consumer<Player> next,Consumer<Player> back) {
        if(page>0) buttons.add(button(45,Material.ARROW,"Previous page",List.of(),previous));
        buttons.add(button(49,Material.ARROW,"Back",List.of("Page "+(page+1)+" / "+WorldStudioEdits.pages(count)),back));
        if(page+1<WorldStudioEdits.pages(count)) buttons.add(button(53,Material.ARROW,"Next page",List.of(),next));
    }
    private static String state(ZoneDefinition z) {return !z.enabled()?"Disabled":z.safe()?"Peaceful area":"Combat area";}
    private static String position(ZoneDefinition.SpawnPoint p) {return String.format(Locale.ROOT,"%.1f, %.1f, %.1f",p.x(),p.y(),p.z());}
    private static String locationPosition(Location p) {return String.format(Locale.ROOT,"%.1f, %.1f, %.1f",p.getX(),p.getY(),p.getZ());}
    private static String bounds(ZoneDefinition.Bounds b) {return b.minX()+", "+b.minY()+", "+b.minZ()+" to "+b.maxX()+", "+b.maxY()+", "+b.maxZ();}
    private static String warpPosition(TravelManager.WarpDefinition d) {
        World world;
        try {world=Bukkit.getWorld(java.util.UUID.fromString(d.world()));}catch(IllegalArgumentException legacy){world=Bukkit.getWorld(d.world());}
        return (world==null?d.world()+" (unloaded)":world.getName())+" · "+String.format(Locale.ROOT,"%.1f, %.1f, %.1f",d.x(),d.y(),d.z());
    }
    private static String worldName(ZoneDefinition z) {World world=Bukkit.getWorld(z.worldId());return world==null?z.worldId()+" (unloaded)":world.getName();}
}
