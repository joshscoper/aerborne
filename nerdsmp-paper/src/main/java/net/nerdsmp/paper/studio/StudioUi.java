package net.nerdsmp.paper.studio;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.engine.EditorPolicy;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One read-only, authenticated UI and private input broker for the content-team modules. */
public final class StudioUi implements Listener,AutoCloseable {
    public static final int[] CONTENT_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    public record Button(int slot,ItemStack icon,Consumer<Player> action) {
        public Button {
            if(slot<0 || slot>=54) throw new IllegalArgumentException("Studio button slot must be 0–53.");
            icon=java.util.Objects.requireNonNull(icon).clone();
            action=java.util.Objects.requireNonNull(action);
        }
        public Button(int slot,Material icon,String name,List<String> lore,Consumer<Player> action) {
            this(slot,card(icon,name,lore),action);
        }
        @Override public ItemStack icon() { return icon.clone(); }
    }
    private final NerdSmpPlugin plugin;
    private final Map<UUID,Page> views=new HashMap<>();
    private final Map<UUID,Prompt> prompts=new ConcurrentHashMap<>();
    private final BukkitTask expiry;
    private Consumer<Player> home=player->player.closeInventory();
    private Consumer<Player> beforeOpen=player->{};
    private boolean closed;

    public StudioUi(NerdSmpPlugin plugin) {
        this.plugin=java.util.Objects.requireNonNull(plugin);
        expiry=Bukkit.getScheduler().runTaskTimer(plugin,this::expire,20,20);
    }
    public void setHome(Consumer<Player> action) { home=java.util.Objects.requireNonNull(action); }
    public void beforeOpen(Consumer<Player> action) { beforeOpen=java.util.Objects.requireNonNull(action); }
    public void home(Player player) { cancel(player); home.accept(player); }
    public void cancel(Player player) { prompts.remove(player.getUniqueId()); views.remove(player.getUniqueId()); }
    public void leave(Player player) { cancel(player); beforeOpen.accept(player); }
    public boolean allowed(Player player,String permission) {
        return !closed && player.isOnline() && Bukkit.getPlayer(player.getUniqueId())==player
            && player.hasPermission(permission) && plugin.sessions().account(player.getUniqueId()).isPresent();
    }
    public void open(Player player,String permission,String title,List<Button> buttons) {
        if(!allowed(player,permission)) { Chat.error(player,"Your profile or permission does not allow this tool."); return; }
        if(!views.containsKey(player.getUniqueId()) && views.size()>=EditorPolicy.MAX_EDITORS)
            throw new IllegalStateException("The studio is busy. Try again shortly.");
        List<Button> copied=List.copyOf(buttons);
        if(copied.stream().map(Button::slot).distinct().count()!=copied.size())
            throw new IllegalArgumentException("Studio controls overlap.");
        beforeOpen.accept(player); prompts.remove(player.getUniqueId());
        Page page=new Page(player,permission,title,copied,plugin.sessions().account(player.getUniqueId()).orElseThrow());
        page.inventory=Bukkit.createInventory(page,54,Component.text(title.length()>60?title.substring(0,60):title,NamedTextColor.DARK_PURPLE));
        ItemStack border=card(Material.GRAY_STAINED_GLASS_PANE," ",List.of());
        for(int slot=0;slot<54;slot++) if(slot<9 || slot>=45 || slot%9==0 || slot%9==8) page.inventory.setItem(slot,border);
        for(Button button:copied) { page.inventory.setItem(button.slot(),inert(button.icon())); page.actions.put(button.slot(),button.action()); }
        views.put(player.getUniqueId(),page); player.openInventory(page.inventory);
    }
    public void ask(Player player,String permission,String question,Consumer<String> accept) {
        ask(player,permission,question,accept,null);
    }
    public void ask(Player player,String permission,String question,Consumer<String> accept,Consumer<Player> onReturn) {
        if(!allowed(player,permission)) return;
        if(!prompts.containsKey(player.getUniqueId()) && prompts.size()>=EditorPolicy.MAX_EDITORS)
            throw new IllegalStateException("The studio is busy. Try again shortly.");
        beforeOpen.accept(player);
        Page previous=views.remove(player.getUniqueId());
        Prompt prompt=new Prompt(player,permission,plugin.sessions().account(player.getUniqueId()).orElseThrow(),previous,
            System.currentTimeMillis()+EditorPolicy.PROMPT_MILLIS,java.util.Objects.requireNonNull(accept),
            onReturn==null?p->restore(p,previous):onReturn);
        prompts.put(player.getUniqueId(),prompt); player.closeInventory();
        Chat.notice(player,Chat.escape(question));
        Chat.info(player,"Reply in chat. Your answer stays private. Type <white>cancel</white> to return; expires in 60 seconds.");
    }
    public void confirm(Player player,String permission,String title,List<String> explanation,Consumer<Player> yes) {
        Page previous=views.get(player.getUniqueId());
        open(player,permission,title,List.of(
            new Button(22,Material.BOOK,"Review Change",explanation,p->{}),
            new Button(30,Material.LIME_CONCRETE,"Confirm",List.of("Apply this change."),yes),
            new Button(32,Material.RED_CONCRETE,"Keep Current",List.of("Leave published settings alone."),p->restore(p,previous))));
    }
    private void restore(Player player,Page previous) {
        if(previous!=null && sameSession(player,previous.account) && allowed(player,previous.permission))
            open(player,previous.permission,previous.title,previous.buttons);
        else home(player);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        boolean already=event.isCancelled(); event.setCancelled(true);
        if(already || !(event.getWhoClicked() instanceof Player player) || !current(player,page)) return;
        if(event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT) return;
        if(event.getRawSlot()<0 || event.getRawSlot()>=54 || page.pending) return;
        var action=page.actions.get(event.getRawSlot()); if(action==null) return;
        page.pending=true;
        Bukkit.getScheduler().runTask(plugin,()-> {
            if(!current(player,page)) return;
            page.pending=false;
            try { action.accept(player); }
            catch(IllegalArgumentException | IllegalStateException failure) { fail(player,failure.getMessage()); }
        });
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void drag(InventoryDragEvent event) { if(event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void chat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId(); Prompt prompt=prompts.get(id);
        if(prompt==null) return;
        event.setCancelled(true);
        if(!prompt.claimed.compareAndSet(false,true)) return;
        String answer=PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin,()-> {
            if(!prompts.remove(id,prompt)) return;
            if(!allowed(prompt.player,prompt.permission) || !sameSession(prompt.player,prompt.account)) return;
            if(System.currentTimeMillis()>=prompt.expires) { Chat.notice(prompt.player,"That input expired."); prompt.returnTo.accept(prompt.player); return; }
            if(answer.strip().equalsIgnoreCase("cancel")) { prompt.returnTo.accept(prompt.player); return; }
            try { prompt.accept.accept(EditorPolicy.text(answer,1288)); }
            catch(IllegalArgumentException | IllegalStateException failure) { fail(prompt.player,failure.getMessage()); prompt.returnTo.accept(prompt.player); }
        });
    }
    @EventHandler
    public void closeView(InventoryCloseEvent event) {
        if(closed || !(event.getInventory().getHolder() instanceof Page page) || !(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin,()-> {
            if(closed || !sameSession(player,page.account) || Bukkit.getPlayer(player.getUniqueId())!=player) return;
            if(views.get(player.getUniqueId())==page && !prompts.containsKey(player.getUniqueId())
                && player.getOpenInventory().getTopInventory()!=page.inventory) views.remove(player.getUniqueId(),page);
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void command(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        // A different command ends a private input; subsequent public chat must not fill an old field.
        if(prompts.containsKey(event.getPlayer().getUniqueId())) cancel(event.getPlayer());
    }
    private boolean current(Player player,Page page) {
        return allowed(player,page.permission) && page.player==player && views.get(player.getUniqueId())==page
            && player.getOpenInventory().getTopInventory()==page.inventory && sameSession(player,page.account);
    }
    private boolean sameSession(Player player,AccountId expected) { return plugin.sessions().account(player.getUniqueId()).filter(expected::equals).isPresent(); }
    private void expire() {
        long now=System.currentTimeMillis();
        prompts.forEach((id,prompt)-> {
            if(now>=prompt.expires && prompts.remove(id,prompt) && allowed(prompt.player,prompt.permission)
                && sameSession(prompt.player,prompt.account)) { Chat.notice(prompt.player,"Studio input expired. No change was saved."); prompt.returnTo.accept(prompt.player); }
        });
    }
    public static ItemStack card(Material material,String name,List<String> lore) {
        ItemStack item=new ItemStack(material); var meta=item.getItemMeta();
        meta.itemName(Component.text(name,NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));
        meta.lore(lore.stream().map(line->Component.text(line,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)).toList());
        meta.addItemFlags(ItemFlag.values()); item.setItemMeta(meta); return item;
    }
    private static ItemStack inert(ItemStack source) {
        ItemStack icon=source.clone(); icon.setAmount(1); var meta=icon.getItemMeta();
        for(var key:new ArrayList<>(meta.getPersistentDataContainer().getKeys())) meta.getPersistentDataContainer().remove(key);
        meta.addItemFlags(ItemFlag.values()); icon.setItemMeta(meta); return icon;
    }
    private static void fail(Player player,String message) { Chat.error(player,Chat.escape(message==null?"That edit was not valid.":message)); }
    @Override public void close() {
        closed=true; expiry.cancel(); prompts.clear(); views.clear();
        for(Player player:Bukkit.getOnlinePlayers()) if(player.getOpenInventory().getTopInventory().getHolder() instanceof Page) player.closeInventory();
    }
    private static final class Page implements InventoryHolder {
        final Player player; final AccountId account; final String permission,title; final List<Button> buttons;
        final Map<Integer,Consumer<Player>> actions=new HashMap<>(); Inventory inventory; boolean pending;
        Page(Player player,String permission,String title,List<Button> buttons,AccountId account) {
            this.player=player; this.permission=permission; this.title=title; this.buttons=buttons; this.account=account;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Prompt {
        final Player player; final String permission; final AccountId account; final Page previous; final long expires; final Consumer<String> accept; final Consumer<Player> returnTo;
        final AtomicBoolean claimed=new AtomicBoolean();
        Prompt(Player player,String permission,AccountId account,Page previous,long expires,Consumer<String> accept,Consumer<Player> returnTo) {
            this.player=player; this.permission=permission; this.account=account; this.previous=previous; this.expires=expires; this.accept=accept; this.returnTo=returnTo;
        }
    }
}
