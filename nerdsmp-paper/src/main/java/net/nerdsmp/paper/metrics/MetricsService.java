package net.nerdsmp.paper.metrics;

import com.google.gson.JsonObject;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Tracking is independent of panel availability. No event writes to disk or queries player databases. */
public final class MetricsService implements Listener, AutoCloseable {
    private record Observation(String key,long amount,long time,UUID player,String source) { }
    private final NerdSmpPlugin plugin;
    private final Path file;
    private final ArrayBlockingQueue<Observation> pending=new ArrayBlockingQueue<>(10_000);
    private final AtomicLong lost=new AtomicLong();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"NerdSMP-Metrics-IO");t.setDaemon(true);return t;});
    private MetricsLedger ledger;
    private JsonObject snapshot=new JsonObject();
    private BukkitTask task;
    private Future<?> save;
    private volatile long saved;
    private volatile String saveError;
    private volatile boolean closed;
    private int passes;
    public MetricsService(NerdSmpPlugin plugin) {
        this.plugin=plugin; file=plugin.getDataFolder().toPath().resolve("metrics/history-v1.json");
        snapshot.addProperty("enabled",plugin.getConfig().getBoolean("metrics.enabled",true));
        if(!snapshot.get("enabled").getAsBoolean())return;
        try {
            if(Files.exists(file)&&Files.size(file)>128L*1024*1024)throw new IllegalArgumentException("Metrics file exceeds 128 MiB");
            long now=System.currentTimeMillis();
            ledger=Files.exists(file)?MetricsLedger.restore(Files.readString(file),now):new MetricsLedger(now,plugin.getConfig().getInt("metrics.retention-days",180));
        } catch(Exception error) {
            snapshot.addProperty("error","Existing metrics could not be loaded. The original file was preserved; check the server log.");
            plugin.getLogger().log(Level.WARNING,"Metrics unavailable; existing history was preserved",error);
        }
    }
    public void start() {
        if(ledger==null)return;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers())join(player,true);
        tick(); task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,200,200);
    }
    public JsonObject snapshot() {
        JsonObject result=snapshot.deepCopy(); result.addProperty("saved",saved);
        if(saveError!=null)result.addProperty("saveError",saveError); return result;
    }
    public void economy(String kind,String reason,long marks) {
        if(closed||ledger==null||marks<=0)return;
        long now=System.currentTimeMillis();
        if(!pending.offer(new Observation("marks."+kind+"."+MetricsLedger.dimension(reason),marks,now,null,null)))lost.incrementAndGet();
    }
    public void event(Player player,String key,long amount) {
        if(closed||ledger==null||player==null||!playable(player))return;
        ledger.counter(player.getUniqueId(),key,amount,System.currentTimeMillis());
    }
    public void aggregate(String key,long amount) { if(!closed&&ledger!=null)ledger.counter(null,key,amount,System.currentTimeMillis()); }
    public void progression(ObservedProgressionService.Award award) {
        if(closed||ledger==null||award.experience()<=0)return;
        UUID player=plugin.sessions()==null?null:plugin.sessions().player(award.account()).orElse(null);
        if(!pending.offer(new Observation("xp."+award.skill().name(),award.experience(),System.currentTimeMillis(),player,MetricsLedger.dimension(award.source()))))lost.incrementAndGet();
    }
    private static boolean playable(Player p) { return p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE; }
    private void join(Player player,boolean recovered) {
        boolean fresh=!player.hasPlayedBefore()||player.getFirstPlayed()>=ledger.started();
        ledger.join(player.getUniqueId(),fresh,System.currentTimeMillis(),recovered);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event) { join(event.getPlayer(),false); }
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        sample(event.getPlayer(),System.currentTimeMillis()); ledger.quit(event.getPlayer().getUniqueId(),System.currentTimeMillis());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void death(PlayerDeathEvent event) {
        event(event.getEntity(),"player.deaths",1);
        var damage=event.getEntity().getLastDamageCause();
        event(event.getEntity(),"death."+(damage==null?"unknown":damage.getCause().name().toLowerCase(java.util.Locale.ROOT)),1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void kill(EntityDeathEvent event) {
        if(event.getEntity() instanceof Player||event.getEntity().getKiller()==null||plugin.mobs().isMechanicObjective(event.getEntity()))return;
        Player killer=event.getEntity().getKiller();
        String id=plugin.mobs().zoneMobId(event.getEntity()).orElse("vanilla:"+event.getEntityType().name().toLowerCase(java.util.Locale.ROOT));
        event(killer,"kills."+MetricsLedger.dimension(id),1);
    }
    private void sample(Player player,long now) {
        boolean active=playable(player)&&plugin.sessions().account(player.getUniqueId()).isPresent()&&plugin.engagement().recentlyActive(player.getUniqueId(),now);
        ledger.sample(player.getUniqueId(),now,active,player.getWorld().getName());
    }
    private void drain() {
        Observation observation; int count=0;
        while(count++<10_000&&(observation=pending.poll())!=null) {
            ledger.counter(observation.player(),observation.key(),observation.amount(),observation.time());
            if(observation.source()!=null) {
                ledger.counter(null,"activity."+observation.key().substring(3),1,observation.time());
                ledger.counter(null,"xpSource."+observation.source(),observation.amount(),observation.time());
            }
        }
        ledger.lost(lost.getAndSet(0));
    }
    private void tick() {
        if(ledger==null||closed)return;
        long now=System.currentTimeMillis(); drain();
        for(Player player:Bukkit.getOnlinePlayers()) { ledger.join(player.getUniqueId(),false,now,true); sample(player,now); }
        long entities=Bukkit.getWorlds().stream().mapToLong(org.bukkit.World::getEntityCount).sum();
        ledger.pulse(now,Bukkit.getOnlinePlayers().size(),Bukkit.getTPS()[0],Bukkit.getAverageTickTime(),entities);
        snapshot=ledger.report(now);
        if(++passes%6==0)save(false);
    }
    private void save(boolean shutdown) {
        if(ledger==null||!shutdown&&save!=null&&!save.isDone())return;
        MetricsLedger.State state=ledger.copyForSave();
        save=writer.submit(()->{
            Path temp=null;
            try {
                Files.createDirectories(file.getParent()); temp=Files.createTempFile(file.getParent(),"metrics-",".tmp");
                Files.writeString(temp,MetricsLedger.encode(state),StandardCharsets.UTF_8);
                try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                catch(AtomicMoveNotSupportedException unsupported) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
                saved=System.currentTimeMillis(); saveError=null;
            } catch(Exception error) { saveError="Metrics are in memory but could not be saved; check server storage and the log."; plugin.getLogger().log(Level.WARNING,"Could not save metrics history",error); }
            finally { if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){} }
        });
    }
    @Override public void close() {
        if(task!=null)task.cancel();
        if(ledger!=null) {
            if(plugin.engagement()!=null&&plugin.sessions()!=null)for(Player player:Bukkit.getOnlinePlayers())sample(player,System.currentTimeMillis());
            drain(); save(true);
        }
        closed=true; writer.shutdown();
        try { if(!writer.awaitTermination(5,TimeUnit.SECONDS))plugin.getLogger().warning("Metrics save is still finishing during shutdown."); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
