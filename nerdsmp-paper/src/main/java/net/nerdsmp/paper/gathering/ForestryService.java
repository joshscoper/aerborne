package net.nerdsmp.paper.gathering;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.player.PlayerSettingsService.Setting;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.*;

/** Bounded native breaks: no silent world edits, chunk loads, protection bypass or per-log player-file writes. */
public final class ForestryService implements Listener, AutoCloseable {
    private static final Set<String> SUPPORT=Set.of("GRASS_BLOCK","DIRT","COARSE_DIRT","ROOTED_DIRT","PODZOL","MYCELIUM",
        "MOSS_BLOCK","MUD","MUDDY_MANGROVE_ROOTS","MANGROVE_ROOTS","CRIMSON_NYLIUM","WARPED_NYLIUM","NETHERRACK");
    private final NerdSmpPlugin plugin;
    private final Predicate<Player> currentPlayer;
    private final BiFunction<Player,Block,Boolean> nativeBreak;
    private final LinkedHashMap<UUID,Job> jobs=new LinkedHashMap<>();
    private final int budget,maximumJobs;
    private BukkitTask task;
    private int discoveryTick=-1,discoveries;
    private boolean closed;
    public ForestryService(NerdSmpPlugin plugin) {
        this(plugin,player->Bukkit.getPlayer(player.getUniqueId())==player,Player::breakBlock);
    }
    /** Test fixture supplies controlled native-event dispatch; production always uses Player.breakBlock. */
    ForestryService(NerdSmpPlugin plugin,Predicate<Player> currentPlayer,BiFunction<Player,Block,Boolean> nativeBreak) {
        this.plugin=plugin;this.currentPlayer=currentPlayer;this.nativeBreak=nativeBreak;
        ForestryRules.experience(6,plugin.getConfig().getDouble("forestry.experience-multiplier",3));
        budget=Math.clamp(plugin.getConfig().getInt("forestry.blocks-per-tick",12),1,32);
        maximumJobs=Math.clamp(plugin.getConfig().getInt("forestry.maximum-active-trees",16),1,32);
    }
    public void start() { task=Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,1,1); }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onRoot(BlockBreakEvent event) {
        if(closed||TreeHarvestBridge.automated(event)||!plugin.getConfig().getBoolean("forestry.enabled",true)) return;
        Player player=event.getPlayer();Block root=event.getBlock();
        if(!validPlayer(player)||player.isSneaking()||!event.isDropItems()||jobs.containsKey(player.getUniqueId())||jobs.size()>=maximumJobs
            ||!CropReplanting.axe(player.getInventory().getItemInMainHand().getType())
            ||!plugin.settings().enabled(player.getUniqueId(),Setting.TREE_FELLING)||!plugin.resources().naturalLog(root)) return;
        int tick=Bukkit.getCurrentTick();if(tick!=discoveryTick) {discoveryTick=tick;discoveries=0;}
        if(discoveries++>=2) return;
        var context=plugin.skills().forestryContext(player);if(context==null) return;
        int level=plugin.presentation().progression(player).skills().get(Skill.FORESTRY).level();
        String family=ForestryRules.family(root.getType().name());
        var plan=ForestryRules.plan(point(root),position->cell(root.getWorld(),position,family),ForestryRules.capacity(level));
        if(plan.state()!=ForestryRules.State.READY) {
            if(plan.state()==ForestryRules.State.TOO_LARGE) player.sendActionBar(Chat.render("<gray>Tree felling: up to "+ForestryRules.capacity(level)+" logs. Sneak to cut individual logs.</gray>"));
            return;
        }
        Job job=new Job(player,context,event,plan,normalize(player.getInventory().getItemInMainHand()));
        for(var position:plan.logs()) job.logs.add(block(root.getWorld(),position).getState());
        jobs.put(player.getUniqueId(),job);
        TreeHarvestBridge.root(event,work->job.rootWork=work);
        // Root removal is vanilla's transaction. Observe final cancellation and actual removal next tick.
        Bukkit.getScheduler().runTask(plugin,()->{
            TreeHarvestBridge.forget(event);
            if(!jobs.containsValue(job)) return;
            if(job.rootWork==null||event.isCancelled()||!event.isDropItems()||!removed(job.logs.getFirst())) {
                restoreAborted(job.rootWork,job.logs.getFirst());finish(job,false);return;
            }
            commit(job,job.rootWork);job.started=true;job.cursor=1;
        });
    }

    private void pulse() {
        if(closed||jobs.isEmpty()) return;
        int remaining=budget;
        // Round robin: one native break per tree per pass; a single large tree cannot starve a co-op.
        while(remaining>0) {
            boolean worked=false;
            for(Job job:List.copyOf(jobs.values())) {
                if(remaining==0) break;
                if(!job.started) continue;
                if(!valid(job)) {finish(job,false);continue;}
                if(job.cursor>=job.logs.size()) {finish(job,true);continue;}
                BlockState expected=job.logs.get(job.cursor++);Block target=expected.getBlock();
                if(!target.getWorld().isChunkLoaded(target.getX()>>4,target.getZ()>>4)||!same(expected)||!plugin.resources().naturalLog(target)) {
                    finish(job,false);continue;
                }
                remaining--;worked=true;
                TreeHarvestBridge.Work[] captured={null};
                try {
                    boolean broken=TreeHarvestBridge.breaking(job.player,target,work->captured[0]=work,()->nativeBreak.apply(job.player,target));
                    if(!broken||captured[0]==null||captured[0].event().isCancelled()||!captured[0].event().isDropItems()||!removed(expected)) {
                        restoreAborted(captured[0],expected);finish(job,false);continue;
                    }
                    commit(job,captured[0]);
                    if(job.cursor>=job.logs.size()) finish(job,true);
                } catch(RuntimeException error) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING,"Tree harvest stopped at "+target.getLocation(),error);
                    finish(job,false);
                }
            }
            if(!worked) break;
        }
    }
    private void commit(Job job,TreeHarvestBridge.Work work) {
        if(!job.operations.add(work.operation())) return;
        job.experience=Math.addExact(job.experience,work.experience());job.harvested++;
        try {work.bonus().run();} catch(RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,"A Forestry bonus could not be delivered for "+work.operation(),failure);
        }
    }
    private void restoreAborted(TreeHarvestBridge.Work work,BlockState expected) {
        if(work!=null&&loaded(expected)&&same(expected)) plugin.resources().renewManaged(expected.getBlock(),expected.getType());
    }
    private void finish(Job job,boolean complete) {
        if(!jobs.remove(job.player.getUniqueId(),job)) return;
        TreeHarvestBridge.forget(job.rootEvent);
        Block root=job.logs.getFirst().getBlock();
        if(!job.started&&job.rootWork!=null&&!job.rootEvent.isCancelled()&&job.rootEvent.isDropItems()
            &&root.getWorld().isChunkLoaded(root.getX()>>4,root.getZ()>>4)
            &&(root.getType().isAir()||root.getType()==Material.WATER)) commit(job,job.rootWork);
        if(job.harvested>0) plugin.skills().awardTree(job.player,job.context,job.logs.getFirst().getType().name(),
            job.experience,job.harvested,"tree:"+job.operation);
        if(complete&&job.harvested==job.logs.size()&&sameSite(job)
            &&plugin.settings().enabled(job.player.getUniqueId(),Setting.TREE_REPLANTING)) replant(job);
    }

    private void replant(Job job) {
        String seedId=ForestryRules.sapling(job.plan.family());if(seedId==null) return;
        Material seed=Material.matchMaterial(seedId);
        if(seed==null) return;
        List<BlockState> before=new ArrayList<>(),support=new ArrayList<>();
        for(var position:job.plan.roots()) {
            Block target=block(job.player.getWorld(),position);
            if(!target.getType().isAir()||!target.canPlace(seed.createBlockData())) return;
            before.add(target.getState());
            support.add(target.getRelative(BlockFace.DOWN).getState());
        }
        ItemStack[] original=Arrays.stream(job.player.getInventory().getStorageContents())
            .map(item->item==null?null:item.clone()).toArray(ItemStack[]::new), next=Arrays.stream(original)
            .map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
        int needed=before.size();
        for(int slot=0;slot<next.length&&needed>0;slot++) {
            ItemStack stack=next[slot];if(stack==null||stack.getType()!=seed||stack.hasItemMeta()) continue;
            int used=Math.min(needed,stack.getAmount());needed-=used;stack.setAmount(stack.getAmount()-used);if(stack.getAmount()==0) next[slot]=null;
        }
        if(needed>0) {job.player.sendActionBar(Chat.render("<gray>Keep "+before.size()+" plain "+seed.name().toLowerCase(Locale.ROOT).replace('_',' ')+" for replanting.</gray>"));return;}
        boolean committed=false;
        try {
            for(BlockState state:before) state.getBlock().setBlockData(seed.createBlockData(),false);
            BlockPlaceEvent placement=before.size()==1
                ?new BlockPlaceEvent(before.getFirst().getBlock(),before.getFirst(),before.getFirst().getBlock().getRelative(BlockFace.DOWN),new ItemStack(seed),job.player,true,EquipmentSlot.HAND)
                :new BlockMultiPlaceEvent(before,before.getFirst().getBlock().getRelative(BlockFace.DOWN),new ItemStack(seed),job.player,true,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(placement);
            if(placement.isCancelled()||!placement.canBuild()||!sameSite(job)
                ||!Arrays.equals(original,job.player.getInventory().getStorageContents())
                ||before.stream().anyMatch(state->!loaded(state)||state.getBlock().getType()!=seed)
                ||support.stream().anyMatch(state->!loaded(state)||!same(state))) return;
            job.player.getInventory().setStorageContents(next);committed=true;
        } finally {
            if(!committed) for(BlockState state:before) if(state.getBlock().getType()==seed) state.update(true,false);
        }
    }
    private boolean valid(Job job) {
        return sameSite(job)
            &&!job.player.isSneaking()&&plugin.settings().enabled(job.player.getUniqueId(),Setting.TREE_FELLING)
            &&job.tool.equals(normalize(job.player.getInventory().getItemInMainHand()));
    }
    private boolean sameSite(Job job) {
        return validPlayer(job.player)&&job.context.account().equals(plugin.sessions().account(job.player.getUniqueId()).orElse(null))
            &&job.player.getWorld().getUID().equals(job.context.world())
            &&job.player.getLocation().distanceSquared(job.rootEvent.getBlock().getLocation().add(.5,.5,.5))<=144;
    }
    private boolean validPlayer(Player player) {
        return !closed&&player.isOnline()&&!player.isDead()&&currentPlayer.test(player)&&player.getGameMode()==GameMode.SURVIVAL
            &&plugin.sessions().account(player.getUniqueId()).isPresent();
    }
    private ForestryRules.Cell cell(World world,ForestryRules.Point point,String family) {
        if(point.y()<world.getMinHeight()||point.y()>=world.getMaxHeight()) return new ForestryRules.Cell("AIR",false,false,false);
        if(!world.isChunkLoaded(point.x()>>4,point.z()>>4)) return null;
        Block block=block(world,point);String material=block.getType().name();
        boolean foliage=material.equals(family+"_LEAVES")&&block.getBlockData() instanceof Leaves leaf&&!leaf.isPersistent()
            ||family.equals("CRIMSON")&&material.equals("NETHER_WART_BLOCK")||family.equals("WARPED")&&material.equals("WARPED_WART_BLOCK");
        return new ForestryRules.Cell(material,plugin.resources().naturalLog(block),foliage,SUPPORT.contains(material));
    }
    private static ItemStack normalize(ItemStack item) {
        ItemStack copy=item.clone();var meta=copy.getItemMeta();if(meta instanceof Damageable damage) {damage.setDamage(0);copy.setItemMeta(meta);}return copy;
    }
    private static boolean same(BlockState state) {return state.getBlock().getBlockData().getAsString().equals(state.getBlockData().getAsString());}
    private static boolean loaded(BlockState state) {return state.getWorld().isChunkLoaded(state.getX()>>4,state.getZ()>>4);}
    private static boolean removed(BlockState state) {
        return loaded(state)&&(state.getBlock().getType().isAir()||state.getBlock().getType()==Material.WATER);
    }
    private static ForestryRules.Point point(Block block) {return new ForestryRules.Point(block.getX(),block.getY(),block.getZ());}
    private static Block block(World world,ForestryRules.Point point) {return world.getBlockAt(point.x(),point.y(),point.z());}
    @EventHandler(priority=EventPriority.LOWEST) public void onQuit(PlayerQuitEvent event) {
        Job job=jobs.get(event.getPlayer().getUniqueId());if(job!=null) finish(job,false);
    }
    @Override public void close() {
        for(Job job:List.copyOf(jobs.values())) try {finish(job,false);} catch(RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not flush committed Forestry work for "+job.player.getUniqueId(),failure);
        }
        closed=true;if(task!=null) task.cancel();
    }
    private static final class Job {
        final Player player;final SkillListener.ForestryContext context;final BlockBreakEvent rootEvent;final ForestryRules.Plan plan;
        final ItemStack tool;final UUID operation=UUID.randomUUID();final List<BlockState> logs=new ArrayList<>();final Set<String> operations=new HashSet<>();
        TreeHarvestBridge.Work rootWork;long experience;int harvested,cursor;boolean started;
        Job(Player player,SkillListener.ForestryContext context,BlockBreakEvent rootEvent,ForestryRules.Plan plan,ItemStack tool) {
            this.player=player;this.context=context;this.rootEvent=rootEvent;this.plan=plan;this.tool=tool;
        }
    }
}
