package net.nerdsmp.paper.item;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.*;
import java.util.*;

/** Server-authoritative character slots. Menu items are display copies, never storage. */
public final class CharacterEquipmentService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PaperItemService items;
    private final EquipmentLedger ledger;
    private final net.nerdsmp.paper.market.MarketPlayerSave saves;
    private final Map<AccountId, Loaded> loaded = new HashMap<>();
    private final Set<UUID> blocked = new HashSet<>(), reported = new HashSet<>();
    private final Set<UUID> refreshQueued=new HashSet<>();
    private final Map<UUID,List<ItemStack>> nativeViews=new HashMap<>();
    private org.bukkit.scheduler.BukkitTask refreshTask;
    private record Pending(int bagSlot, ItemStack item, List<CharacterEquipmentSlot> choices) { }
    private static final class Loaded {
        EquipmentLedger.State state;
        Map<CharacterEquipmentSlot,ItemStack> accessories;
        Loaded(EquipmentLedger.State state) { set(state); }
        void set(EquipmentLedger.State next) {
            var decoded=new EnumMap<CharacterEquipmentSlot,ItemStack>(CharacterEquipmentSlot.class);
            next.accessories().forEach((slot,value)->decoded.put(slot,decode(value)));
            state=next; accessories=decoded;
        }
    }
    public CharacterEquipmentService(NerdSmpPlugin plugin) {
        this.plugin=plugin; items=plugin.items();
        ledger=new EquipmentLedger(plugin.playerData());
        saves=new net.nerdsmp.paper.market.MarketPlayerSave(plugin);
    }
    public void start() {
        // Upgrade marks compare against every slot here, rings and trinkets included.
        Bukkit.getScheduler().runTask(plugin,()->{if(plugin.gearUpgrades()!=null) plugin.gearUpgrades().wornWith((player,type)->java.util.Arrays.stream(CharacterEquipmentSlot.values())
            .filter(slot->slot.type()==type).map(slot->equipped(player,slot)).toList());});
        refreshTask=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(Player player:Bukkit.getOnlinePlayers()) if(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Screen) refreshLive(player);
        },5,5);
    }
    public boolean recover(Player player) {
        try { context(player); return true; }
        catch(RuntimeException error) {notice(player,error);player.kick(Chat.render("<red>Your equipment needs recovery. Please contact staff; your items are preserved.</red>"));return false;}
    }
    public Map<CharacterEquipmentSlot,ItemStack> accessories(Player player) {
        if(plugin.sessions().account(player.getUniqueId()).isEmpty()) return Map.of();
        try {
            Loaded state=context(player);
            Map<CharacterEquipmentSlot,ItemStack> copy=new EnumMap<>(CharacterEquipmentSlot.class);
            state.accessories.forEach((slot,item)->copy.put(slot,item.clone()));
            return copy;
        } catch(RuntimeException error) {
            if(reported.add(player.getUniqueId())) plugin.getLogger().log(java.util.logging.Level.SEVERE,"Equipment unavailable for "+player.getUniqueId()+"; saved items preserved",error);
            return Map.of();
        }
    }
    public ItemStack equipped(Player player, CharacterEquipmentSlot slot) {
        ItemStack item=slot.accessory()?context(player).accessories.get(slot):player.getInventory().getItem(slot.inventorySlot(player.getInventory()));
        return empty(item)?null:item.clone();
    }
    private Loaded context(Player player) {
        if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("Equipment changes require the server thread");
        AccountId account=plugin.sessions().account(player.getUniqueId()).orElseThrow(()->new IllegalArgumentException("Your character is still loading."));
        Loaded state=loaded.computeIfAbsent(account,id->new Loaded(ledger.read(id)));
        if(state.state.pending()!=null) complete(player,account,state);
        return state;
    }
    private void complete(Player player, AccountId account, Loaded state) {
        var transfer=state.state.pending(); blocked.add(player.getUniqueId());
        Map<Integer,String> observed=new HashMap<>();
        for(var change:transfer.changes()) {
            ItemStack current=player.getInventory().getItem(change.slot());
            observed.put(change.slot(),sameVersion(current,decode(change.before()))?change.before():sameVersion(current,decode(change.after()))?change.after():encode(current));
        }
        if(!transfer.recoverable(player.getUniqueId(),observed))
            throw new IllegalStateException("Equipment recovery needs the original inventory; items and transfer record preserved. Contact staff.");
        for(var change:transfer.changes()) player.getInventory().setItem(change.slot(),decode(change.after()));
        saves.saveAndVerify(player);
        ledger.finish(account,state.state);
        state.set(new EquipmentLedger.State(state.state.accessories(),null));
        blocked.remove(player.getUniqueId()); reported.remove(player.getUniqueId());
    }
    public void equip(Player player, CharacterEquipmentSlot target, int inventorySlot, ItemStack expected) {
        mutable(player); Loaded state=context(player);
        if(inventorySlot<0||inventorySlot>35) throw new IllegalArgumentException("Choose equipment from your bags.");
        ItemStack source=player.getInventory().getItem(inventorySlot);
        if(empty(source)||!same(source,expected)) throw new IllegalArgumentException("That item moved. Reopen Equipment.");
        if(source.getAmount()!=1) throw new IllegalArgumentException("Equipment must be a single item. Split that stack first.");
        var gear=items.gear(source);
        if(items.describe(source).isPresent()&&gear.isEmpty()) throw new IllegalArgumentException("Identify this item before equipping it.");
        var type=gear.map(PaperItemService.GearDescriptor::slot).orElseGet(()->EquipmentMaterials.slot(source.getType()));
        if(type!=target.type() || target.accessory()&&gear.isEmpty()) throw new IllegalArgumentException("That item does not fit "+target.title()+".");
        if(target.accessory()) {
            var id=gear.orElseThrow().itemId();
            for(var entry:state.accessories.entrySet()) if(entry.getKey()!=target && items.gear(entry.getValue()).map(g->g.itemId().equals(id)).orElse(false))
                throw new IllegalArgumentException("You may equip only one of this accessory. Choose a different ring or trinket.");
        }
        ItemStack previous=equipped(player,target); binding(player,target,previous);
        if(same(source,previous)) return;
        if(!target.accessory() && inventorySlot==target.inventorySlot(player.getInventory())) return;
        Map<CharacterEquipmentSlot,String> next=new EnumMap<>(CharacterEquipmentSlot.class); next.putAll(state.state.accessories());
        List<EquipmentLedger.Change> changes=new ArrayList<>();
        changes.add(new EquipmentLedger.Change(inventorySlot,encode(source),encode(previous)));
        if(target.accessory()) next.put(target,encode(source));
        else changes.add(new EquipmentLedger.Change(target.inventorySlot(player.getInventory()),encode(previous),encode(source)));
        transfer(player,state,next,changes);
    }
    public void unequip(Player player, CharacterEquipmentSlot slot, ItemStack expected) {
        mutable(player); Loaded state=context(player); ItemStack old=equipped(player,slot);
        if(empty(old)) return;
        if(!same(old,expected)) throw new IllegalArgumentException("Your equipment changed. Reopen Equipment.");
        binding(player,slot,old);
        int destination=-1;
        for(int index=0;index<36;index++) if(empty(player.getInventory().getItem(index))) {destination=index;break;}
        if(destination<0) throw new IllegalArgumentException("Make one empty bag slot before unequipping.");
        Map<CharacterEquipmentSlot,String> next=new EnumMap<>(CharacterEquipmentSlot.class); next.putAll(state.state.accessories());
        List<EquipmentLedger.Change> changes=new ArrayList<>();
        changes.add(new EquipmentLedger.Change(destination,null,encode(old)));
        if(slot.accessory()) next.remove(slot);
        else changes.add(new EquipmentLedger.Change(slot.inventorySlot(player.getInventory()),encode(old),null));
        transfer(player,state,next,changes);
    }
    private void transfer(Player player, Loaded state, Map<CharacterEquipmentSlot,String> next, List<EquipmentLedger.Change> changes) {
        AccountId account=plugin.sessions().account(player.getUniqueId()).orElseThrow();
        var intent=new EquipmentLedger.State(next,new EquipmentLedger.Transfer(player.getUniqueId(),changes));
        ledger.begin(account,state.state,intent); // No inventory changes until this durable write succeeds.
        state.set(intent);
        try { complete(player,account,state); }
        catch(RuntimeException error) {player.kick(Chat.render("<red>Your equipment transfer needs recovery. Rejoin to recover it; your saved items are preserved.</red>"));throw error;}
        player.updateInventory(); if(plugin.playerStats()!=null) plugin.playerStats().refresh(player);
    }
    private static void mutable(Player player) {
        if(!player.isOnline()||player.isDead()||player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)
            throw new IllegalArgumentException("Change equipment while alive in Survival or Adventure mode.");
        if(!empty(player.getItemOnCursor())) throw new IllegalArgumentException("Put the item on your cursor into your bags first.");
    }
    private static void binding(Player player, CharacterEquipmentSlot slot, ItemStack old) {
        if(!slot.accessory()&&slot.type()!=PaperItemService.GearSlot.MAIN_HAND&&slot.type()!=PaperItemService.GearSlot.OFF_HAND
            && !empty(old)&&old.containsEnchantment(Enchantment.BINDING_CURSE)) throw new IllegalArgumentException("Curse of Binding prevents removing that armor.");
    }
    public void open(Player player) {
        try {
            mutable(player); context(player);
            Screen screen=new Screen(player.getUniqueId()); render(player,screen); player.openInventory(screen.inventory);
        } catch(RuntimeException error) { notice(player,error); }
    }
    /** Shift-click in E and clicking bags in the character screen share this exact resolver. */
    private void quickEquip(Player player, int bagSlot, ItemStack expected, Screen screen) {
        mutable(player);context(player);
        ItemStack current=player.getInventory().getItem(bagSlot);
        if(empty(current)||!sameVersion(current,expected)) throw new IllegalArgumentException("That item moved. Choose it again.");
        var gear=items.gear(current);
        if(items.describe(current).isPresent()&&gear.isEmpty()) throw new IllegalArgumentException("Identify this item before equipping it.");
        var type=gear.map(PaperItemService.GearDescriptor::slot).orElseGet(()->EquipmentMaterials.slot(current.getType()));
        Map<CharacterEquipmentSlot,String> occupied=new EnumMap<>(CharacterEquipmentSlot.class);
        for(var slot:CharacterEquipmentSlot.values()) {
            var worn=equipped(player,slot);
            if(!empty(worn)) occupied.put(slot,items.describe(worn).map(d->d.itemId().value()).orElse("vanilla"));
        }
        var choice=EquipmentChoices.choose(type,gear.map(g->g.itemId().value()).orElse(null),occupied);
        var target=screen!=null&&screen.selected!=null&&screen.selected.type()==type?screen.selected:choice.target();
        if(target!=null) {
            if(screen!=null&&!same(equipped(player,target),screen.visible.get(target))) throw new IllegalArgumentException("Your equipment changed. Choose the item again.");
            equip(player,target,bagSlot,current.clone());
            equippedFeedback(player,target);
            if(screen!=null) {screen.pending=null;screen.selected=null;render(player,screen);}
        } else {
            Screen picker=screen==null?new Screen(player.getUniqueId()):screen;
            picker.selected=null;picker.pending=new Pending(bagSlot,current.clone(),choice.candidates());
            render(player,picker);if(screen==null)player.openInventory(picker.inventory);
        }
    }
    private void equippedFeedback(Player player, CharacterEquipmentSlot slot) {
        player.playSound(player.getLocation(),Sound.ITEM_ARMOR_EQUIP_GENERIC,0.6F,1.1F);
        PlayerStatsService.message(player,Chat.render("<green>Equipped · "+slot.title()+"</green>"));
    }
    private void comparison(Player player, ItemStack display, CharacterEquipmentSlot target, Pending pending) {
        var before=plugin.gearSets().profile(player);
        var after=plugin.gearSets().projectedProfile(player,target,pending.item(),pending.bagSlot());
        display.editMeta(meta->{
            var lore=meta.hasLore()?new ArrayList<>(meta.lore()):new ArrayList<net.kyori.adventure.text.Component>();
            lore.add(Chat.render("<gold>Build changes if replaced:</gold>"));
            var changes=EquipmentChoices.changes(before.stats(),after.stats());
            changes.forEach((stat,value)->{
                String text=net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().stripTags(PaperItemService.statLine(stat,value));
                lore.add(Chat.render("<"+(value>0?"green":"red")+">"+Chat.escape(text)+"</"+(value>0?"green":"red")+">"));
            });
            for(var bonus:after.activeBonuses()) if(!before.activeBonuses().contains(bonus)) lore.add(Chat.render("<green>Gain "+Chat.escape(bonus.setName())+" "+bonus.requiredPieces()+"pc</green>"));
            for(var bonus:before.activeBonuses()) if(!after.activeBonuses().contains(bonus)) lore.add(Chat.render("<red>Lose "+Chat.escape(bonus.setName())+" "+bonus.requiredPieces()+"pc</red>"));
            if(changes.isEmpty()&&before.activeBonuses().equals(after.activeBonuses())) lore.add(Chat.render("<gray>No stat change.</gray>"));
            lore.add(Chat.render("<yellow>Left-click to replace · Right-click to cancel.</yellow>"));meta.lore(lore);
        });
    }
    private void queueRefresh(Player player) {
        if(!refreshQueued.add(player.getUniqueId()))return;
        Bukkit.getScheduler().runTask(plugin,()->{refreshQueued.remove(player.getUniqueId());if(player.isOnline())refreshLive(player);});
    }
    private void refreshLive(Player player) {
        if(plugin.sessions().account(player.getUniqueId()).isEmpty()||player.isDead())return;
        try {
            List<ItemStack> nativeNow=new ArrayList<>();
            for(var slot:CharacterEquipmentSlot.values())if(!slot.accessory())nativeNow.add(equipped(player,slot));
            var old=nativeViews.put(player.getUniqueId(),nativeNow);
            if(!nativeNow.equals(old)&&plugin.playerStats()!=null)plugin.playerStats().refresh(player);
            if(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Screen screen) {
                boolean changed=screen.heldSlot!=player.getInventory().getHeldItemSlot();
                for(var slot:CharacterEquipmentSlot.values()) if(!same(screen.visible.get(slot),equipped(player,slot)))changed=true;
                if(screen.pending!=null&&!sameVersion(player.getInventory().getItem(screen.pending.bagSlot()),screen.pending.item())) {screen.pending=null;changed=true;}
                if(changed)render(player,screen);
            }
        } catch(RuntimeException error) {if(reported.add(player.getUniqueId()))notice(player,error);}
    }
    private void render(Player player, Screen screen) {
        screen.revision++;screen.heldSlot=player.getInventory().getHeldItemSlot();
        MenuKit.frame(screen.inventory);
        screen.inventory.setItem(0,MenuKit.item(Material.BOOK,"<gold>Character Equipment</gold>",List.of("<gray>Shift-click gear in E or click gear in these bags.</gray>","<gray>Right-click a slot to unequip it.</gray>","<gray>Armor and weapons sync with your normal inventory.</gray>","<gray>Main Hand follows your selected hotbar slot.</gray>","<gray>Sneak + F opens · F closes this screen.</gray>")));
        String choice=screen.selected==null?"Choose a slot or click an equipment item":screen.selected.title()+" selected";
        screen.inventory.setItem(4,MenuKit.item(Material.PLAYER_HEAD,"<aqua>"+choice+"</aqua>",List.of("<gray>Armor and weapons use your real Minecraft slots.</gray>","<gray>Accessories grant bonuses only while equipped here.</gray>","<gray>Swapping returns the old piece to the same bag slot.</gray>")));
        if(screen.pending!=null) {
            ItemStack candidate=ItemUpdater.current(screen.pending.item().clone());
            candidate.editMeta(meta->{var lore=meta.hasLore()?new ArrayList<>(meta.lore()):new ArrayList<net.kyori.adventure.text.Component>();
                lore.add(Chat.render("<yellow>Choose a highlighted slot below to replace.</yellow>"));
                lore.add(Chat.render("<gray>Your item stays in your bags until you choose.</gray>"));meta.lore(lore);});
            screen.inventory.setItem(4,candidate);
        }
        screen.inventory.setItem(8,MenuKit.item(Material.APPLE,"<red>Player Stats</red>",List.of("<gray>View your live health, defense and gear bonuses.</gray>")));
        for(var slot:CharacterEquipmentSlot.values()) {
            ItemStack original=equipped(player,slot); ItemStack display;
            screen.visible.put(slot,original);
            if(empty(original)) display=MenuKit.item(slot.icon(),"<gray>"+slot.title()+" · Empty</gray>",List.of("<gray>Click to choose this slot.</gray>"));
            else {
                display=ItemUpdater.current(original.clone());
                display.editMeta(meta->{var lore=meta.hasLore()?new ArrayList<>(meta.lore()):new ArrayList<net.kyori.adventure.text.Component>();
                    lore.add(Chat.render("<gold>Equipped: "+slot.title()+"</gold>"));
                    lore.add(Chat.render(screen.pending!=null&&screen.pending.choices().contains(slot)
                        ?"<gray>Compare with the new item above.</gray>":"<gray>Left-click to select · Right-click to unequip</gray>"));meta.lore(lore);});
            }
            if(screen.selected==slot) display.editMeta(meta->meta.setEnchantmentGlintOverride(true));
            if(screen.pending!=null&&screen.pending.choices().contains(slot)) {
                display.editMeta(meta->meta.setEnchantmentGlintOverride(true));comparison(player,display,slot,screen.pending);
            }
            screen.inventory.setItem(slot.menuSlot(),display);
        }
        screen.inventory.setItem(48,MenuKit.back("Character")); screen.inventory.setItem(49,MenuKit.close());
        screen.inventory.setItem(51,MenuKit.item(Material.NETHER_STAR,"<light_purple>Talents</light_purple>",List.of()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if(!(event.getWhoClicked() instanceof Player player)) return;
        if(!(event.getView().getTopInventory().getHolder(false) instanceof Screen screen)) {
            if(blocked.contains(player.getUniqueId())) {event.setCancelled(true);return;}
            // Chest/workbench/storage transfers keep normal behavior; only E gets accessory quick-equip.
            if(event.getView().getType()==InventoryType.CRAFTING&&event.getClickedInventory()==player.getInventory()
                &&event.isShiftClick()&&event.getSlot()>=0&&event.getSlot()<36&&empty(event.getCursor())
                &&items.equipmentType(event.getCurrentItem()).map(PaperItemService.GearSlot::accessory).orElse(false)) {
                event.setCancelled(true);int bagSlot=event.getSlot();ItemStack expected=event.getCurrentItem().clone();Inventory origin=event.getView().getTopInventory();
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=origin)return;
                    try {quickEquip(player,bagSlot,expected,null);}catch(RuntimeException error){notice(player,error);}
                });
            }
            return;
        }
        event.setCancelled(true);
        if(!screen.owner.equals(player.getUniqueId())||!empty(event.getCursor())) return;
        if(event.getClick()==ClickType.SWAP_OFFHAND&&plugin.getConfig().getBoolean("equipment.sneak-swap-shortcut",true)) {
            Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&player.getOpenInventory().getTopInventory()==screen.inventory)player.closeInventory();});return;
        }
        int raw=event.getRawSlot(); boolean right=event.getClick()==ClickType.RIGHT;
        if(event.getClick()!=ClickType.LEFT&&!right&&event.getClick()!=ClickType.SHIFT_LEFT&&event.getClick()!=ClickType.SHIFT_RIGHT) return;
        ItemStack expected=event.getCurrentItem()==null?null:event.getCurrentItem().clone();
        int source=event.getSlot();
        boolean bags=event.getClickedInventory()==player.getInventory();
        long revision=screen.revision;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=screen.inventory||screen.revision!=revision) return;
            try {
                if(raw>=54&&bags) {
                    if(right||empty(expected)) return;
                    quickEquip(player,source,expected,screen); return;
                }
                if(raw==49){player.closeInventory();return;}
                if(raw==48){plugin.menus().open(player,MenuManager.View.CHARACTER);return;}
                if(raw==8){plugin.menus().open(player,MenuManager.View.STATS);return;}
                if(raw==51){plugin.menus().openAdventurerTree(player);return;}
                for(var slot:CharacterEquipmentSlot.values()) if(slot.menuSlot()==raw) {
                    if(screen.pending!=null&&right) {screen.pending=null;screen.selected=null;render(player,screen);}
                    else if(screen.pending!=null) {
                        var pending=screen.pending;
                        if(!pending.choices().contains(slot))return;
                        if(!same(equipped(player,slot),screen.visible.get(slot)))throw new IllegalArgumentException("Your equipment changed. Choose the item again.");
                        ItemStack current=player.getInventory().getItem(pending.bagSlot());
                        if(!sameVersion(current,pending.item()))throw new IllegalArgumentException("That item moved. Choose it again.");
                        equip(player,slot,pending.bagSlot(),current.clone());screen.pending=null;screen.selected=null;
                        equippedFeedback(player,slot);render(player,screen);
                    }
                    else if(right) {unequip(player,slot,screen.visible.get(slot));screen.pending=null;render(player,screen);}
                    else {screen.selected=slot;render(player,screen);} return;
                }
            } catch(RuntimeException error) {notice(player,error);}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder(false) instanceof Screen||blocked.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void shortcut(PlayerSwapHandItemsEvent event) {
        var player=event.getPlayer();boolean ours=player.getOpenInventory().getTopInventory().getHolder(false) instanceof Screen;
        if(player.isSneaking()&&plugin.getConfig().getBoolean("equipment.sneak-swap-shortcut",true)
            &&(ours||player.getOpenInventory().getType()==InventoryType.CRAFTING)) {
            event.setCancelled(true);Bukkit.getScheduler().runTask(plugin,()->{if(ours)player.closeInventory();else open(player);});
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void inventoryChanged(io.papermc.paper.event.player.PlayerInventorySlotChangeEvent event) {queueRefresh(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void held(PlayerItemHeldEvent event) {queueRefresh(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void swapped(PlayerSwapHandItemsEvent event) {queueRefresh(event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent event) {if(blocked.contains(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent event) {if(blocked.contains(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent event) {
        plugin.sessions().account(event.getPlayer().getUniqueId()).ifPresent(loaded::remove);
        blocked.remove(event.getPlayer().getUniqueId());
        reported.remove(event.getPlayer().getUniqueId());
        nativeViews.remove(event.getPlayer().getUniqueId());refreshQueued.remove(event.getPlayer().getUniqueId());
    }
    private void notice(Player player, RuntimeException error) {
        if(error instanceof IllegalArgumentException) Chat.error(player,error.getMessage());
        else {Chat.error(player,"Equipment could not be saved or recovered. Your items were preserved; contact staff.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Equipment operation failed for "+player.getUniqueId(),error);}
    }
    @Override public void close() {
        if(refreshTask!=null)refreshTask.cancel();
        for(Player player:Bukkit.getOnlinePlayers()) if(player.getOpenInventory().getTopInventory().getHolder(false) instanceof Screen) player.closeInventory();
        loaded.clear();
        nativeViews.clear();refreshQueued.clear();
    }
    private static boolean empty(ItemStack item) {return item==null||item.getType().isAir()||item.getAmount()==0;}
    private static boolean same(ItemStack a,ItemStack b) {return empty(a)?empty(b):!empty(b)&&a.equals(b);}
    private static boolean sameVersion(ItemStack current,ItemStack saved) {return same(current,saved)||same(current,ItemUpdater.current(saved));}
    private static String encode(ItemStack item) {return empty(item)?null:Base64.getEncoder().encodeToString(item.serializeAsBytes());}
    private static ItemStack decode(String value) {return value==null?null:ItemStack.deserializeBytes(Base64.getDecoder().decode(value));}
    public static final class Screen implements InventoryHolder {
        final UUID owner; final Inventory inventory; CharacterEquipmentSlot selected;
        Pending pending;long revision;int heldSlot;
        final Map<CharacterEquipmentSlot,ItemStack> visible=new EnumMap<>(CharacterEquipmentSlot.class);
        Screen(UUID owner) {this.owner=owner;inventory=Bukkit.createInventory(this,54,Chat.render("<gold>Character Equipment</gold>"));}
        @Override public Inventory getInventory() {return inventory;}
    }
}
