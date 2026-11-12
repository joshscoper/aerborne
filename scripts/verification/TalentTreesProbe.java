package net.nerdsmp.probe;

import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService.*;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.TalentTreePresentation;
import net.nerdsmp.paper.service.InMemoryProgressionService;
import net.nerdsmp.paper.skyblock.*;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Native inventory transactions against the current plugin on an isolated loopback server. */
public final class TalentTreesProbe extends JavaPlugin {
    private NerdSmpPlugin core;
    private Player player, guest;
    private AccountId account, cooperator;
    private final List<String> results=new ArrayList<>();
    private final Map<String,List<Map<String,Object>>> screens=new LinkedHashMap<>();
    private final List<Purchase> purchases=new ArrayList<>();
    private int waits;
    private String season;
    private static final PlainTextComponentSerializer PLAIN=PlainTextComponentSerializer.plainText();
    record Purchase(String id,int slot,int confirmation,String kind,int page) { }
    @Override public void onEnable() {
        if(!Boolean.getBoolean("nerdsmp.talentTreeProbe") || !Bukkit.getIp().equals("127.0.0.1")) throw new IllegalStateException("Disposable loopback fixture only");
        core=(NerdSmpPlugin)Bukkit.getPluginManager().getPlugin("NerdSMP");season=core.getConfig().getString("season.id","season-1");
        later(20,this::ready);
    }
    private void ready() throws Exception {
        player=Bukkit.getPlayerExact("P_TreeUI");guest=Bukkit.getPlayerExact("P_TreeCoop");
        if(++waits>240)throw new AssertionError("Player deadline");
        if(player==null||guest==null||core.sessions().account(player.getUniqueId()).isEmpty()||core.sessions().account(guest.getUniqueId()).isEmpty()) {later(20,this::ready);return;}
        account=core.sessions().account(player.getUniqueId()).orElseThrow();cooperator=core.sessions().account(guest.getUniqueId()).orElseThrow();
        for(Player p:List.of(player,guest)){p.setInvulnerable(true);p.setGameMode(GameMode.SURVIVAL);}
        core.menus().openSkillTree(player,Skill.MINING);
        later(15,()->{
            check(title().contains("Mining Talent Tree"),"Ordinary account opens new Mining tree");
            check(snapshot().talents().isEmpty(),"Fresh account has no seeded talent purchases");
            player.sendMessage("TREE_CLICK 10");
            later(15,()->{
                check(title().contains("Mining Talent Tree"),"Locked rank does not open a spending confirmation");
                check(snapshot().talents().isEmpty(),"Locked click spends no points");
                for(Skill skill:Skill.values())core.progression().awardExperience(account,skill,250000,"tree-probe-eligibility:"+skill).toCompletableFuture().join();
                core.presentation().refresh(player);
                later(15,()->inspectSkill(0));
            });
        });
    }
    private void inspectSkill(int index) throws Exception {
        if(index==Skill.values().length){inspectNavigation();return;}
        Skill skill=Skill.values()[index];core.menus().openSkillTree(player,skill);
        later(10,()->{
            check(title().contains(skill.displayName()+" Talent Tree"),skill.displayName()+" tree reaches client inventory");
            var cards=TalentTreePresentation.cards(snapshot(),skill);Inventory inventory=player.getOpenInventory().getTopInventory();
            for(var card:cards)check(name(inventory.getItem(card.slot())).contains(card.name()) && name(inventory.getItem(card.slot())).contains("0/"),skill+" card and rank visible: "+card.name());
            check(name(inventory.getItem(48)).contains("Back")&&name(inventory.getItem(49)).contains("Close"),skill+" tree keeps actual navigation slots clear");
            screen(skill.name());inspectSkill(index+1);
        });
    }
    private void inspectNavigation() throws Exception {
        core.menus().openSkillTree(player,Skill.MINING);
        later(10,()->{
            player.sendMessage("TREE_CLICK 53");
            later(10,()->{
                check(title().contains("Forestry Talent Tree"),"Native Next button opens the next profession");
                player.sendMessage("TREE_CLICK 45");
                later(10,()->{
                    check(title().contains("Mining Talent Tree"),"Native Previous button returns to Mining");
                    player.sendMessage("TREE_CLICK 48");
                    later(10,()->{
                        check(title().contains("Skills"),"Native Back button returns to Skills");
                        core.menus().openSkillTree(player,Skill.MINING);
                        later(10,()->{
                            player.sendMessage("TREE_CLICK 49");
                            later(10,()->{
                                check(player.getOpenInventory().getTopInventory().getSize()!=54,"Native Close button closes the tree");
                                inspectAdventurer();
                            });
                        });
                    });
                });
            });
        });
    }
    private void inspectAdventurer() throws Exception {
        player.performCommand("adventurer");
        later(10,()->{
            check(title().contains("Adventurer Talent Tree"),"General Adventurer tree opens");
            check(snapshot().adventurerPointsAvailable()==11,"A100 has eleven independent Adventurer points");
            screen("ADVENTURER");
            purchases.addAll(List.of(
                new Purchase("mining.contract_miner.1",12,15,"skill",0),new Purchase("mining.contract_miner.2",12,15,"skill",0),
                new Purchase("mining.crossroads.1",29,15,"skill",0),new Purchase("mining.crossroads.2",31,15,"skill",0),
                new Purchase("mining.crossroads.capstone",40,15,"skill",0),
                new Purchase("adventurer.warden.1",14,15,"adventurer",0),new Purchase("adventurer.vanguard.1",12,15,"adventurer",0),
                new Purchase("adventurer.vanguard.2",12,15,"adventurer",0),new Purchase("adventurer.crossroads.1",29,15,"adventurer",0),
                new Purchase("adventurer.crossroads.2",31,15,"adventurer",0),new Purchase("adventurer.crossroads.capstone",40,15,"adventurer",0)));
            buy(0,this::personalComplete);
        });
    }
    private void openPurchase(Purchase purchase) {
        if(purchase.kind().equals("skill"))core.menus().openSkillTree(player,Skill.MINING);
        else if(purchase.kind().equals("adventurer"))core.menus().openAdventurerTree(player);
        else {
            player.performCommand("island talents");
            if(purchase.page()==1)later(10,()->player.sendMessage("TREE_CLICK 53"));
        }
    }
    private int points(Purchase purchase) {
        return switch(purchase.kind()){case "skill"->snapshot().talentPointsAvailable(Skill.MINING);case "adventurer"->snapshot().adventurerPointsAvailable();default->core.skyblock().development().progress(account).availablePoints();};
    }
    private void buy(int index,Step finish) throws Exception {
        if(index==purchases.size()){finish.run();return;}
        Purchase purchase=purchases.get(index);int before=points(purchase);
        int otherBefore=purchase.kind().equals("adventurer")?snapshot().talentPointsAvailable(Skill.MINING):snapshot().adventurerPointsAvailable();
        openPurchase(purchase);
        later(25,()->{
            player.sendMessage("TREE_CLICK "+purchase.slot());
            later(15,()->{
                check(title().contains(purchase.kind().equals("island")?"Learn Talent?":"Spend a Talent Point?"),"Native click opens confirmation: "+purchase.id());
                player.sendMessage("TREE_CLICK "+purchase.confirmation());
                later(20,()->{
                    if(purchase.kind().equals("island")) {
                        String[] bits=purchase.id().split("\\.");
                        check(core.skyblock().development().progress(account).rank(IslandTalent.parse(bits[1]))==Integer.parseInt(bits[2]),"Native confirmation saved "+purchase.id());
                        if(purchase.page()==1)check(title().contains("2/2"),"Island confirmation returns to the same page");
                    } else check(snapshot().talents().contains(purchase.id()),"Native confirmation saved "+purchase.id());
                    check(points(purchase)==before-1,"Exactly one point spent: "+purchase.id());
                    int otherAfter=purchase.kind().equals("adventurer")?snapshot().talentPointsAvailable(Skill.MINING):snapshot().adventurerPointsAvailable();
                    check(otherBefore==otherAfter,"Other point budget preserved: "+purchase.id());
                    buy(index+1,finish);
                });
            });
        });
    }
    private void personalComplete() throws Exception {
        var progress=snapshot();
        check(progress.talentPointsSpent(Skill.MINING)==5 && progress.adventurerPointsSpent()==6,"Personal and general purchases reconcile to separate budgets");
        check(progress.adventurerStat("defense")==6,"General defense talents combine once with existing automatic rank perks");
        check(progress.adventurerStat("skill-xp-percent")==13,"Adventurer capstone adds its live XP bonus");
        check(core.gearSets().profile(player).stat("geode-find-percent")>=20,"Mining capstone reaches the real passive gear/stat consumer");
        core.menus().openSkillTree(player,Skill.MINING);
        later(10,()->{
            screen("MINING_LEARNED");
            check(name(player.getOpenInventory().getTopInventory().getItem(12)).contains("2/3"),"Purchased profession ranks appear as 2/3");
            var result=core.progression().unlockTalent(account,"mining.crossroads.capstone").toCompletableFuture().join();
            check(result==TalentResult.ALREADY_UNLOCKED && snapshot().talentPointsSpent(Skill.MINING)==5,"Repeated capstone request cannot spend or grant twice");
            islandSetup();
        });
    }
    private void islandSetup() throws Exception {
        var sky=core.skyblock();sky.islands().create(account);sky.islands().invite(account,cooperator,Instant.now());sky.islands().accept(cooperator,account,Instant.now());
        core.playerData().update(account,yaml->{
            String path="seasons."+season+".island-development";
            yaml.set(path+".schema",1);yaml.set(path+".xp",IslandDevelopment.experienceForLevel(50));
            yaml.set(path+".day",LocalDate.now(ZoneOffset.UTC).toString());yaml.set(path+".earned-today",0);
        });
        check(sky.development().progress(account).availablePoints()==25,"Island has its own twenty-five-point budget at level 50");
        player.performCommand("island talents");
        later(15,()->{
            check(title().contains("Island Talent Tree 1/2"),"Island Industry/Nature page opens");screen("ISLAND_INDUSTRY_NATURE");
            player.sendMessage("TREE_CLICK 53");
            later(15,()->{
                check(title().contains("Island Talent Tree 2/2"),"Native navigation reaches Automation/Stewardship");screen("ISLAND_AUTOMATION_STEWARDSHIP");
                purchases.clear();
                purchases.addAll(List.of(
                    new Purchase("island.prospecting.1",11,24,"island",0),new Purchase("island.prospecting.2",11,24,"island",0),
                    new Purchase("island.mining_guild.1",30,24,"island",0),new Purchase("island.mining_guild.2",30,24,"island",0),
                    new Purchase("island.ore_mastery.1",38,24,"island",0),
                    new Purchase("island.stacking.1",11,24,"island",1),new Purchase("island.stacking.2",11,24,"island",1),
                    new Purchase("island.workshop.1",30,24,"island",1),new Purchase("island.workshop.2",30,24,"island",1),
                    new Purchase("island.storage_network.1",38,24,"island",1)));
                buy(0,this::islandComplete);
            });
        });
    }
    private void islandComplete() throws Exception {
        var progress=core.skyblock().development().progress(account);
        check(progress.rank(IslandTalent.RICH_SEAMS)==0 && progress.rank(IslandTalent.ORE_MASTERY)==1,"Industry capstone works through the alternate fork");
        check(progress.skillBonus(Skill.MINING)==16,"Guild fork provides actual Mining/Smithing XP bonus");
        check(progress.rank(IslandTalent.LOGISTICS)==0 && progress.hopperLimit()==2 && progress.machineCycleSeconds(10)==8,"Alternate Automation fork changes real machine and collection limits");
        int before=progress.availablePoints();boolean denied=false;
        try{core.skyblock().development().learn(cooperator,account,IslandTalent.STORAGE_NETWORK,1);}catch(IllegalArgumentException expected){denied=true;}
        check(denied && core.skyblock().development().progress(account).availablePoints()==before,"Co-op member cannot spend shared points");
        guest.performCommand("island talents P_TreeUI");
        later(15,()->{
            check(PLAIN.serialize(guest.getOpenInventory().title()).contains("Island Talent Tree"),"Co-op member can inspect the shared tree");
            guest.sendMessage("TREE_CLICK 11");
            later(15,()->{
                check(PLAIN.serialize(guest.getOpenInventory().title()).contains("Island Talent Tree"),"Read-only co-op click cannot open spend confirmation");
                var disk=new PlayerDataStore(core.getDataFolder(),getLogger());
                var restored=new InMemoryProgressionService(disk,season).snapshot(account).toCompletableFuture().join();
                check(restored.equals(snapshot()),"All personal and Adventurer purchases reload from disk exactly");
                var islands=new IslandService(disk,season);
                var development=new IslandDevelopment(disk,islands,season,()->LocalDate.now(ZoneOffset.UTC));
                check(development.progress(account).equals(core.skyblock().development().progress(account)),"Shared island fork/capstone purchases reload from disk exactly");
                Files.writeString(Path.of("talent-tree-screens.json"),new GsonBuilder().setPrettyPrinting().create().toJson(screens));
                Files.writeString(Path.of("talent-tree-result.txt"),"PASS "+results.size()+" checks\n"+String.join("\n",results)+"\n");
                Bukkit.shutdown();
            });
        });
    }
    private ProgressionSnapshot snapshot(){return core.progression().snapshot(account).toCompletableFuture().join();}
    private String title(){return PLAIN.serialize(player.getOpenInventory().title());}
    private String name(ItemStack item){if(item==null)return "";var meta=item.getItemMeta();return meta.hasItemName()?PLAIN.serialize(meta.itemName()):meta.hasDisplayName()?PLAIN.serialize(meta.displayName()):item.getType().name();}
    private void screen(String label){
        List<Map<String,Object>> items=new ArrayList<>();Inventory inv=player.getOpenInventory().getTopInventory();
        for(int slot=0;slot<inv.getSize();slot++){
            ItemStack item=inv.getItem(slot);if(item==null)continue;
            Map<String,Object> entry=new LinkedHashMap<>();entry.put("slot",slot);entry.put("material",item.getType().name());entry.put("name",name(item));
            entry.put("lore",item.getItemMeta().hasLore()?item.getItemMeta().lore().stream().map(PLAIN::serialize).toList():List.of());items.add(entry);
        }screens.put(label,items);
    }
    private void check(boolean success,String message){if(!success)throw new AssertionError(message);results.add("PASS "+message);getLogger().info(results.getLast());}
    @FunctionalInterface interface Step{void run()throws Exception;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{
        try{step.run();}catch(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"Talent probe failed",failure);
            try{Files.writeString(Path.of("talent-tree-result.txt"),"FAIL "+failure+"\n"+String.join("\n",results));}catch(Exception ignored){}Bukkit.shutdown();}
    },ticks);}
}
