package net.nerdsmp.probe;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.*;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.expedition.flow.RunProgress;
import net.nerdsmp.paper.npc.NpcManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Runs only on a fresh disposable loopback server. Reflection isolates exact reported failure paths. */
public final class PlaytestRepairProbe extends JavaPlugin {
    private NerdSmpPlugin core;
    private Player a, b, outsider;
    private Object run;
    private World instance;
    private RunProgress progress;
    private LivingEntity patrolBody;
    private Location patrolOrigin;
    private final List<String> results = new ArrayList<>();
    private int waited;

    @Override public void onEnable() {
        if (!Boolean.getBoolean("nerdsmp.playtestRepairProbe") || !Bukkit.getIp().equals("127.0.0.1"))
            throw new IllegalStateException("Disposable loopback probe only");
        core = (NerdSmpPlugin) Bukkit.getPluginManager().getPlugin("NerdSMP");
        later(20, this::awaitReady);
    }
    private void awaitReady() throws Exception {
        Map<?, ?> bodies = field(core.npcs(), "liveByInstance");
        if (++waited > 360) throw new AssertionError("NPC/player startup deadline exceeded: " + bodies.size());
        if (bodies.size() < 94) { later(20, this::awaitReady); return; }
        if (Boolean.getBoolean("nerdsmp.startupOnly")) {
            check(bodies.size() == 94, "Fresh startup placed all 94 NPC bodies under the normal watchdog");
            finish(); return;
        }
        a = Bukkit.getPlayerExact("R_FixA"); b = Bukkit.getPlayerExact("R_FixB"); outsider = Bukkit.getPlayerExact("R_Outside");
        if (a == null || b == null || outsider == null || core.sessions().account(a.getUniqueId()).isEmpty()
            || core.sessions().account(b.getUniqueId()).isEmpty()) { later(20, this::awaitReady); return; }
        for (Player player : List.of(a, b, outsider)) {
            player.setGameMode(GameMode.SURVIVAL);
            player.addAttachment(this, "nerdsmp.anticheat.bypass", true);
        }
        check(!a.hasPermission("nerdsmp.claim.bypass") && !outsider.hasPermission("nerdsmp.claim.bypass"), "Players have ordinary claim permissions");
        npcChecks();
        later(20, () -> {
            patrolBody.teleport(patrolOrigin);
            rewards();
            var definition = core.expeditions().definition("first_flight").orElseThrow();
            method(core.expeditions(), "launch", 7).invoke(core.expeditions(), definition, Tier.DEFAULTS.get("normal"),
                List.of(a.getUniqueId(), b.getUniqueId()), List.of(), false, 0, null);
            later(20, this::awaitInstance);
        });
    }

    private void npcChecks() throws Exception {
        Map<UUID, NpcManager.NpcInstance> anchors = field(core.npcs(), "instances");
        Map<UUID, LivingEntity> bodies = field(core.npcs(), "liveByInstance");
        var anchor = anchors.values().stream().filter(n -> n.definitionId().contains("ottar")).findFirst().orElseThrow();
        LivingEntity body = bodies.get(anchor.id());
        Location original = body.getLocation();
        patrolBody = body; patrolOrigin = original;
        Location moved = new Location(body.getWorld(), anchor.x() + 25, anchor.y() + 15, anchor.z());
        moved.clone().add(1,-1,0).getBlock().setType(Material.STONE);
        body.teleport(moved);
        a.teleport(moved.clone().add(1, 0, 0));
        AccountId account = core.sessions().account(a.getUniqueId()).orElseThrow();
        Method context = method(core.npcs(), "currentNpcContext", 3);
        check((boolean) context.invoke(core.npcs(), a, account, anchor), "NPC context accepts player beside body displaced 25 blocks from anchor");
        check(!(boolean) context.invoke(core.npcs(), a, AccountId.random(), anchor), "NPC context rejects mismatched account");
        var stale = new NpcManager.NpcInstance(anchor.id(), anchor.definitionId(), anchor.worldId(), anchor.x() + 1, anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
        check(!(boolean) context.invoke(core.npcs(), a, account, stale), "NPC context rejects stale anchor identity");
        var tag = new NamespacedKey(core, "npc_instance");
        body.getPersistentDataContainer().set(tag, PersistentDataType.STRING, UUID.randomUUID().toString());
        check(!(boolean) context.invoke(core.npcs(), a, account, anchor), "NPC context rejects body with wrong identity tag");
        body.getPersistentDataContainer().set(tag, PersistentDataType.STRING, anchor.id().toString());
        a.teleport(new Location(body.getWorld(), anchor.x(), anchor.y(), anchor.z()));
        check(!(boolean) context.invoke(core.npcs(), a, account, anchor), "Saved anchor cannot authorize remote use of a displaced NPC");
        a.teleport(moved.clone().add(1, 0, 0));
        a.performCommand("npc talk " + anchor.id().toString().substring(0, 8));
        check((boolean) method(core.npcs(), "currentChatLocation", 3).invoke(core.npcs(), a, account, anchor), "Displaced NPC conversation passes live range and line of sight");
    }

    private void rewards() throws Exception {
        World world = a.getWorld(); Location at = a.getLocation().clone().add(0, 10, 0);
        LivingEntity low = core.mobs().spawnZone("custom:field_gnawer", at, 1, mob -> mob.setAI(false));
        LivingEntity high = core.mobs().spawnZone("custom:field_gnawer", at, 20, mob -> mob.setAI(false));
        LivingEntity elite = core.mobs().spawnZone("custom:gilded_overseer", at, 20, mob -> mob.setAI(false));
        Pig farm = world.spawn(at, Pig.class);
        try {
            check(core.mobs().huntingExperience(low).orElseThrow() == 40, "Managed level 1 normal mob awards 40 RPG XP");
            check(core.mobs().huntingExperience(high).orElseThrow() == 135, "Managed level 20 normal mob awards 135 RPG XP");
            check(core.mobs().huntingExperience(elite).orElseThrow() == 203, "Explicit elite rank awards 203 RPG XP at level 20");
            check(core.mobs().huntingExperience(farm).isEmpty(), "Ordinary farm pig does not receive managed-encounter XP scaling");
            AccountId account = core.sessions().account(a.getUniqueId()).orElseThrow();
            long before = core.progression().snapshot(account).toCompletableFuture().join().skills().get(Skill.HUNTING).experience();
            // Isolate death reward routing from unrelated hub combat restrictions and gear math.
            high.setKiller(a); high.setHealth(0);
            long after = core.progression().snapshot(account).toCompletableFuture().join().skills().get(Skill.HUNTING).experience();
            check(after - before == 135, "Actual credited death listener persists exactly 135 hunting XP");
        } finally { for (LivingEntity mob : List.of(low, high, elite, farm)) { mob.remove(); core.mobs().forget(mob); } }
    }

    private void awaitInstance() throws Exception {
        Map<UUID, Object> byPlayer = field(core.expeditions(), "runByPlayer");
        run = byPlayer.get(a.getUniqueId());
        if (run == null) throw new AssertionError("Party launch failed");
        instance = field(run, "world");
        if (instance == null || !field(run, "state").toString().equals("ACTIVE")) { later(20, this::awaitInstance); return; }
        later(50, this::prepareInstance);
    }
    private void prepareInstance() throws Exception {
        // Skip the unrelated camera/jump course; retain ordinary claim permissions and real run membership.
        Object stage = field(run, "stage");
        method(stage, "endCutscene", 1).invoke(stage, a); method(stage, "endCutscene", 1).invoke(stage, b);
        progress = field(run, "progress");
        apply(progress.enter("skyhop", System.currentTimeMillis()));
        apply(progress.solve("skyhop", "crossing", System.currentTimeMillis()));
        apply(progress.enter("lever_lock", System.currentTimeMillis()));
        check(progress.activeRoom().id().equals("lever_lock"), "Normal two-member run is active in tutorial lever room");
        teleport(a, new Location(instance, -4.5, 65, 50.5));
        teleport(b, new Location(instance, 0.5, 65, 54.5));
        teleport(outsider, a.getLocation());
        Block authored = instance.getBlockAt(-6, 65, 50);
        check(!interaction(a, authored).isCancelled(), "Claim protection permits member use of authored tutorial lever");
        check(interaction(outsider, authored).isCancelled(), "Claim protection still denies outsider use of tutorial lever");
        Block unrelated = instance.getBlockAt(8, 65, 54); unrelated.setType(Material.LEVER);
        check(interaction(a, unrelated).isCancelled(), "Unrelated redstone remains protected for run member");
        check(!core.expeditions().mayUsePuzzle(new PlayerInteractEvent(a, Action.LEFT_CLICK_BLOCK, null, authored, BlockFace.UP, EquipmentSlot.HAND)), "Puzzle allowance does not authorize block breaking");
        teleport(outsider, Bukkit.getWorlds().getFirst().getSpawnLocation());
        puzzleChecks();
        pull(0);
    }
    private PlayerInteractEvent interaction(Player player, Block block) {
        var event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event); return event;
    }
    private void pull(int index) throws Exception {
        if (index == 3) { later(35, this::checkLevers); return; }
        int[][] points = {{-6,65,50},{6,65,50},{-4,65,56}}; int[] p = points[index];
        instance.getBlockAt(p[0]+1, p[1]-1, p[2]).setType(Material.STONE);
        teleport(a, new Location(instance, p[0]+1.5, p[1], p[2]+.5));
        later(15, () -> { a.sendMessage("PROBE_PULL" + index); later(25, () -> {
            check(((Powerable)instance.getBlockAt(p[0],p[1],p[2]).getBlockData()).isPowered(), "Native Mineflayer interaction toggles tutorial lever " + (index+1));
            pull(index+1);
        }); });
    }
    private void checkLevers() throws Exception {
        check(progress.state("lever_lock") == RunProgress.RoomState.CLEARED, "All ordinary tutorial lever interactions clear the room");
        b.sendMessage("PROBE_QUIT"); later(35, this::expireMember);
    }
    private void expireMember() throws Exception {
        check(!b.isOnline(), "Second party member disconnected normally");
        Map<UUID, Long> away = field(run, "away");
        check(away.containsKey(b.getUniqueId()), "Disconnected member has reconnect deadline");
        // Accelerate only the stored offline timestamp, exercising the actual tick/detach callback.
        away.put(b.getUniqueId(), System.currentTimeMillis() - 301_000);
        method(run, "tick", 1).invoke(run, System.currentTimeMillis());
        Map<UUID, Object> byPlayer = field(core.expeditions(), "runByPlayer");
        check(!away.containsKey(b.getUniqueId()) && !byPlayer.containsKey(b.getUniqueId()), "Expired member detached exactly once");
        check(byPlayer.get(a.getUniqueId()) == run && a.getWorld().equals(instance) && field(run, "state").toString().equals("ACTIVE"), "Remaining party member retains active instance after timeout");
        Object returns = method(core.expeditions(), "returns", 0).invoke(core.expeditions());
        check(((Optional<?>)method(returns,"location",1).invoke(returns,b.getUniqueId())).isPresent(), "Expired offline member retains safe return record");
        later(30, () -> { check(a.getWorld().equals(instance), "Instance remains available on subsequent scheduler ticks"); finish(); });
    }

    private void puzzleChecks() throws Exception {
        for (PuzzleType type : List.of(PuzzleType.SEQUENCE, PuzzleType.TARGETS)) {
            int[] solves = {0};
            Puzzle puzzle = new Puzzle("test",type,"Test",List.of(new Point(20,65,20,0),new Point(21,65,20,0)),List.of(0,1),false,null,null,null,5);
            Room room = new Room("probe","Probe",Box.of(18,64,18,24,70,24),new Point(20,65,20,0),null,List.of(),Encounter.NONE,List.of(),false,Map.of("test",puzzle));
            Class<?> hostType = Class.forName("net.nerdsmp.paper.expedition.RunPuzzles$Host");
            Object host = Proxy.newProxyInstance(hostType.getClassLoader(),new Class<?>[]{hostType},(object,method,args) -> switch(method.getName()) {
                case "world" -> instance; case "activeRoom" -> room; case "online" -> List.of(a);
                case "solved" -> { solves[0]++; yield null; } default -> null;
            });
            Class<?> puzzleType = Class.forName("net.nerdsmp.paper.expedition.RunPuzzles");
            Constructor<?> constructor = puzzleType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            Object live = constructor.newInstance(core,host); method(live,"started",1).invoke(live,room);
            Block first = instance.getBlockAt(20,65,20), second = instance.getBlockAt(21,65,20);
            first.setType(type==PuzzleType.SEQUENCE ? Material.LEVER : Material.TARGET); second.setType(first.getType());
            if (type==PuzzleType.SEQUENCE) {
                method(live,"used",2).invoke(live,a,first); method(live,"used",2).invoke(live,a,second);
                for(int i=0;i<20;i++) method(live,"used",2).invoke(live,a,i%2==0?first:second);
                check(solves[0]==1, "Solved sequence ignores 20 repeated presses without exception or duplicate solve");
            } else {
                method(live,"hit",2).invoke(live,first,0L); method(live,"hit",2).invoke(live,second,1L);
                method(live,"tick",1).invoke(live,60_000L); method(live,"hit",2).invoke(live,first,61_000L);
                check(solves[0]==1 && field(live,"targetWindow") instanceof Map<?,?> map && map.isEmpty()
                    && method(live,"progress",2).invoke(live,room,puzzle).equals("2/2"), "Solved targets retire timer and retain complete progress after timeout window");
            }
            method(live,"reset",1).invoke(live,room); method(live,"started",1).invoke(live,room);
            if(type==PuzzleType.SEQUENCE) {
                method(live,"used",2).invoke(live,a,first); method(live,"used",2).invoke(live,a,second);
            } else {
                method(live,"hit",2).invoke(live,first,0L); method(live,"tick",1).invoke(live,60_000L);
                check(method(live,"progress",2).invoke(live,room,puzzle).equals("0/2"),"Incomplete target attempt still resets on timeout");
                method(live,"hit",2).invoke(live,first,61_000L); method(live,"hit",2).invoke(live,second,61_001L);
            }
            check(solves[0]==2, "Puzzle can solve again after explicit wipe/reset: "+type);
        }
    }

    private void apply(List<RunProgress.Effect> effects) throws Exception { method(run,"apply",2).invoke(run,effects,System.currentTimeMillis()); }
    private void teleport(Player player, Location to) throws Exception { method(core.expeditions(),"teleport",2).invoke(core.expeditions(),player,to); }
    private void check(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
        results.add("PASS " + message); getLogger().info(results.getLast());
    }
    private void finish() throws Exception {
        Files.writeString(Path.of("playtest-repair-result.txt"),"PASS " + results.size() + " checks\n" + String.join("\n",results)+"\n");
        Bukkit.shutdown();
    }
    @FunctionalInterface interface Step { void run() throws Exception; }
    private void later(long ticks, Step step) { Bukkit.getScheduler().runTaskLater(this,()->{
        try { step.run(); } catch(Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE,"Repair probe failed",failure);
            try { Files.writeString(Path.of("playtest-repair-result.txt"),"FAIL " + failure + "\n" + String.join("\n",results)); } catch(Exception ignored) { }
            Bukkit.shutdown();
        }
    },ticks); }
    @SuppressWarnings("unchecked") private static <T> T field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return (T)field.get(object);
    }
    private static Method method(Object object,String name,int count) {
        for(Method method:object.getClass().getDeclaredMethods()) if(method.getName().equals(name)&&method.getParameterCount()==count) { method.setAccessible(true); return method; }
        throw new IllegalArgumentException("Missing method "+name);
    }
}
