package net.nerdsmp.probe;

import com.google.gson.GsonBuilder;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.player.TutorialProgress;
import net.nerdsmp.paper.support.SupportStore;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.file.*;
import java.util.*;

/** Runs only in an explicitly opted-in disposable loopback server. Not part of the shipped plugin. */
public final class TutorialSupportProbe extends JavaPlugin {
    private NerdSmpPlugin core;
    private Player player, helper, other, watcher;
    private AccountId account, staff, second;
    private PermissionAttachment helperAccess;
    private ItemStack[] beforeInventory;
    private int waits;
    private final List<String> checks = new ArrayList<>();
    private final Map<String,Object> screens = new LinkedHashMap<>();
    private static final PlainTextComponentSerializer PLAIN=PlainTextComponentSerializer.plainText();
    @Override public void onEnable() {
        if(!Boolean.getBoolean("nerdsmp.tutorialSupportProbe")||!Bukkit.getIp().equals("127.0.0.1"))throw new IllegalStateException("Disposable loopback fixture only");
        core=(NerdSmpPlugin)Bukkit.getPluginManager().getPlugin("NerdSMP");later(20,this::ready);
    }
    private void ready() throws Exception {
        player=Bukkit.getPlayerExact("T_Player");helper=Bukkit.getPlayerExact("T_Helper");other=Bukkit.getPlayerExact("T_Other");watcher=Bukkit.getPlayerExact("T_Watcher");
        if(++waits>240)throw new AssertionError("Player deadline");
        if(player==null||helper==null||other==null||watcher==null||Bukkit.getOnlinePlayers().stream().anyMatch(p->core.sessions().account(p.getUniqueId()).isEmpty())){later(20,this::ready);return;}
        for(Player p:List.of(player,helper,other,watcher)){p.setInvulnerable(true);p.setGameMode(GameMode.SURVIVAL);p.closeInventory();}
        account=core.sessions().account(player.getUniqueId()).orElseThrow();staff=core.sessions().account(helper.getUniqueId()).orElseThrow();second=core.sessions().account(other.getUniqueId()).orElseThrow();
        beforeInventory=Arrays.stream(player.getInventory().getContents()).map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
        helperAccess=helper.addAttachment(this);helperAccess.setPermission("nerdsmp.staff",true);
        other.addAttachment(this).setPermission("nerdsmp.support.staff",true);
        check(!helper.isOp()&&helper.hasPermission("nerdsmp.support.staff"),"Existing non-OP staff inherits support access");
        check(!player.hasPermission("nerdsmp.support.staff")&&!watcher.hasPermission("nerdsmp.support.staff"),"Normal players have no queue access");
        say(player,"/guide");later(15,()->{
            check(title().startsWith("Tutorial"),"Guide alias opens a standard inventory tutorial");screen("WELCOME");
            click(49,0,0);later(12,()->{
                check(core.tutorial().progress().state(account).read().isEmpty(),"Closing tutorial does not mark unread lessons complete");
                lesson(0);
            });
        });
    }
    private void lesson(int index)throws Exception {
        if(index==8){afterTutorial();return;}
        say(player,"/tutorial "+(index+1));later(10,()->{
            check(title().startsWith("Tutorial"),"Lesson "+(index+1)+" opens");screen("LESSON_"+(index+1));
            click(53,0,0);later(10,()->{
                check(core.tutorial().progress().state(account).read().contains(TutorialProgress.LESSONS.get(index)),"Native click saves lesson "+(index+1));
                lesson(index+1);
            });
        });
    }
    private void afterTutorial()throws Exception {
        check(core.tutorial().progress().state(account).complete(),"All eight read checkpoints complete");
        check(Arrays.equals(beforeInventory,player.getInventory().getContents()),"Tutorial clicks do not move or duplicate inventory items");
        check(new TutorialProgress(new PlayerDataStore(core.getDataFolder(),getLogger())).state(account).complete(),"Tutorial checkpoints reload from a fresh disk store");
        say(player,"/tutorial 8");later(10,()->{click(29,0,0);later(12,()->{
            check(player.getOpenInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING,"Website action closes tutorial and returns to normal inventory");
            say(player,"/tips off");later(8,()->{
                check(!core.settings().enabled(player.getUniqueId(),net.nerdsmp.paper.player.PlayerSettingsService.Setting.TIPS),"Tip opt-out remains available");
                say(player,"/helpme OPEN_SECRET Need help with my equipment");later(12,this::supportCreated);
            });
        });});
    }
    private SupportStore.Ticket ticket(){return core.support().store().get(1).orElseThrow();}
    private void supportCreated()throws Exception {
        check(ticket().owner().equals(account)&&ticket().messages().size()==1,"Native helpme command creates one account-owned request");
        check(core.support().privateChat(player.getUniqueId()),"Opening a request immediately selects private chat for safe follow-ups");
        say(player,"/helpme DUPLICATE_SECRET");say(watcher,"/support read 1");say(watcher,"/support queue");say(watcher,"/support claim 1");
        later(15,()->{
            check(core.support().store().queue().size()==1&&ticket().messages().size()==1&&ticket().assigned()==null,"Duplicate requests and unauthorized claims do not mutate state");
            say(helper,"/support claim 1");later(12,()->{
                check(staff.equals(ticket().assigned()),"Non-OP helper claims request");
                say(other,"/support claim 1");say(other,"/support reply #1 UNASSIGNED_SECRET");later(10,()->{
                    check(staff.equals(ticket().assigned())&&ticket().messages().size()==1,"Second staff member cannot steal claim or reply");
                    say(player,"/channel support");say(helper,"/support chat 1");later(12,()->{
                        check(core.support().privateChat(player.getUniqueId())&&core.support().privateChat(helper.getUniqueId()),"Both participants select explicit private chat");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"staff mute T_Player 1m fixture appeal path");
                        check(!core.staff().canCommunicate(player.getUniqueId()),"Fixture player is muted in ordinary chat");
                        say(player,"PLAYER_SECRET My ring will not equip");later(15,()->{
                            check(ticket().messages().getLast().text().startsWith("PLAYER_SECRET"),"Muted player can still reach staff privately for help or appeal");
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"staff unmute T_Player");
                            say(helper,"STAFF_SECRET Shift-click it in E");later(15,()->{
                                check(ticket().messages().getLast().staff()&&ticket().messages().getLast().text().startsWith("STAFF_SECRET"),"Ordinary staff chat replies privately");
                                later(30,this::offline);
                            });
                        });
                    });
                });
            });
        });
    }
    private void offline()throws Exception {
        player.kick(net.kyori.adventure.text.Component.text("TS_RECONNECT"));
        later(10,()->{
            check(Bukkit.getPlayerExact("T_Player")==null,"Requester disconnects before offline reply");
            say(helper,"/support reply #1 OFFLINE_SECRET Your reply will be here when you return");
            later(15,()->{
                check(ticket().messages().getLast().text().startsWith("OFFLINE_SECRET"),"Staff reply is saved while requester is offline");
                check(new SupportStore(new java.io.File(core.getDataFolder(),"support.yml")).get(1).orElseThrow().unread()==2,"Unread staff replies and transcript survive a fresh store reload");
                waits=0;reconnected();
            });
        });
    }
    private void reconnected()throws Exception {
        player=Bukkit.getPlayerExact("T_Player");
        if(++waits>60)throw new AssertionError("Reconnect deadline");
        if(player==null||core.sessions().account(player.getUniqueId()).isEmpty()){later(10,this::reconnected);return;}
        check(account.equals(core.sessions().account(player.getUniqueId()).orElseThrow()),"Reconnect keeps the same account and request");
        check(!core.support().privateChat(player.getUniqueId()),"Private chat selection is cleared on disconnect");
        say(player,"/support read 1");later(12,()->{
            check(ticket().unread()==0,"Reading persisted replies clears the unread marker");
            say(player,"/support chat");later(10,()->{
                say(helper,"/support release 1");later(10,()->{
                    say(other,"/support claim 1");later(10,()->{
                        check(second.equals(ticket().assigned()),"Released request can be picked up by another helper");
                        helperAccess.setPermission("nerdsmp.support.staff",false);
                        say(helper,"REVOKED_SECRET Must not leak");later(12,()->{
                            check(ticket().messages().size()==4,"Revoked former helper's private message is blocked");
                            say(player,"/support close");later(10,()->{
                                check(ticket().closed(),"Requester closes own conversation");
                                say(player,"CLOSED_SECRET Must remain private");later(12,()->{
                                    check(ticket().messages().size()==4&&core.support().privateChat(player.getUniqueId()),"Closed chat remains locked instead of falling back to global");
                                    say(player,"/support leave");later(10,()->{
                                        check(!core.support().privateChat(player.getUniqueId()),"Explicit leave exits private support mode");
                                        say(player,"/channel global");later(8,()->{say(player,"PUBLIC_AFTER_LEAVE");later(15,this::finish);});
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    private void finish()throws Exception {
        String publicChat=core.chat().console().snapshot().toString();
        check(!publicChat.contains("SECRET"),"Private support content never enters the public/staff web chat console");
        check(publicChat.contains("PUBLIC_AFTER_LEAVE"),"Public chat works again after explicit support exit");
        check(new SupportStore(new java.io.File(core.getDataFolder(),"support.yml")).get(1).orElseThrow().closed(),"Closed transcript persists to disk");
        Files.writeString(Path.of("tutorial-screens.json"),new GsonBuilder().setPrettyPrinting().create().toJson(screens));
        Files.writeString(Path.of("support-result.txt"),"PASS "+checks.size()+"\n"+String.join("\n",checks));Bukkit.shutdown();
    }
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.add(message);getLogger().info("PASS "+message);}
    private void say(Player player,String text){player.sendMessage("TS_SAY "+text);}
    private void click(int slot,int button,int mode){player.sendMessage("TS_CLICK "+slot+" "+button+" "+mode);}
    private String title(){return PLAIN.serialize(player.getOpenInventory().title());}
    private void screen(String label){List<Map<String,Object>> list=new ArrayList<>();var inv=player.getOpenInventory().getTopInventory();for(int i=0;i<inv.getSize();i++){var item=inv.getItem(i);if(item==null)continue;var meta=item.getItemMeta();list.add(Map.of("slot",i,"material",item.getType().name(),"name",meta.hasItemName()?PLAIN.serialize(meta.itemName()):item.getType().name(),"lore",meta.hasLore()?meta.lore().stream().map(PLAIN::serialize).toList():List.of()));}screens.put(label,list);}
    @FunctionalInterface interface Step{void run()throws Exception;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"Tutorial/support probe failed",error);try{Files.writeString(Path.of("support-result.txt"),"FAIL "+error+"\n"+String.join("\n",checks));}catch(Exception ignored){}Bukkit.shutdown();}},ticks);}
}
