package net.nerdsmp.probe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.player.PlayerSettingsService.Setting;
import net.nerdsmp.paper.presentation.MobHealthBarService;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiConsumer;

/** Native TextDisplays and real settings, with controlled per-viewer show/hide calls (no client login). */
final class MobNameplateProbe {
    @SuppressWarnings("unchecked")
    static void run(NerdSmpPlugin core,World world,BiConsumer<Boolean,String> check) throws Exception {
        String root="presentation.mob-health-bars.";
        Map<String,Object> previous=new HashMap<>();
        for(String key:List.of("nameplate-worlds","nameplate-range","nameplates-per-viewer","maximum-overhead-bars"))previous.put(key,core.getConfig().get(root+key));
        List<LivingEntity> entities=new ArrayList<>();
        List<MobHealthBarService> services=new ArrayList<>();
        try {
            core.getConfig().set(root+"nameplate-worlds",List.of(world.getName().toUpperCase(Locale.ROOT)));
            core.getConfig().set(root+"nameplate-range",12);core.getConfig().set(root+"nameplates-per-viewer",2);core.getConfig().set(root+"maximum-overhead-bars",8);
            var service=new MobHealthBarService(core,core.mobs(),core.settings());services.add(service);
            Location at=new Location(world,8.5,220,8.5);world.getChunkAt(at).load();
            for(int offset:new int[]{4,5,6,1,2,30})entities.add(core.mobs().spawnZone("vanilla:zombie",at.clone().add(offset,0,0),5,mob->{mob.setAI(false);mob.setGravity(false);}));
            entities.get(3).getPersistentDataContainer().set(new NamespacedKey(core,"npc_instance"),PersistentDataType.STRING,UUID.randomUUID().toString());
            var eligible=MobHealthBarService.class.getDeclaredMethod("eligible",LivingEntity.class);eligible.setAccessible(true);
            // Use the actual objective key rather than depending on an unrelated encounter's creation.
            var objectiveKey=core.mobs().getClass().getDeclaredField("bossObjectiveKey");objectiveKey.setAccessible(true);
            entities.get(4).getPersistentDataContainer().set((NamespacedKey)objectiveKey.get(core.mobs()),PersistentDataType.BYTE,(byte)1);
            check.accept(!(boolean)eligible.invoke(service,entities.get(3))&&!(boolean)eligible.invoke(service,entities.get(4)),"NPCs and mechanic objectives never receive an extra mob nameplate");
            List<Entity> nearby=new ArrayList<>(entities);Set<UUID> shownA=new HashSet<>(),shownB=new HashSet<>();boolean[] sight={true,true};
            Player a=viewer(world,at,nearby,shownA,sight),b=viewer(world,at,nearby,shownB,new boolean[]{true});
            core.settings().set(b.getUniqueId(),Setting.MOB_HEALTH_BARS,false);
            service.refreshViewer(a);service.refreshViewer(b);
            var field=MobHealthBarService.class.getDeclaredField("overhead");field.setAccessible(true);Map<UUID,?> overhead=(Map<UUID,?>)field.get(service);
            check.accept(shownA.size()==2&&overhead.size()==2&&overhead.containsKey(entities.get(0).getUniqueId())&&overhead.containsKey(entities.get(1).getUniqueId()),"default nameplates appear for the nearest eligible enemies before combat, within the per-viewer cap");
            check.accept(shownB.isEmpty(),"a player who opted out receives no labels");
            nearby.clear();nearby.add(entities.get(1));nearby.add(entities.get(2));service.refreshViewer(a);
            check.accept(shownA.size()==2&&overhead.size()==2&&!overhead.containsKey(entities.get(0).getUniqueId()),"changing nearby candidates releases old idle names without exceeding the per-viewer cap");
            nearby.clear();nearby.addAll(entities);service.refreshViewer(a);
            TextDisplay display=(TextDisplay)Bukkit.getEntity(shownA.iterator().next());
            String name=plain(display.text());
            check.accept(name.contains("Lv 5")&&name.contains("Zombie")&&!name.contains("\n"),"idle display contains the authored level and name without a combat bar");
            check.accept(!display.isVisibleByDefault()&&!display.isPersistent()&&!display.isSeeThrough(),"native display is opt-in, temporary and occluded by blocks");
            core.settings().set(b.getUniqueId(),Setting.MOB_HEALTH_BARS,true);service.refreshViewer(b);
            check.accept(shownA.equals(shownB)&&overhead.size()==2,"two viewers share one display per mob");
            core.settings().set(a.getUniqueId(),Setting.MOB_HEALTH_BARS,false);service.refreshViewer(a);
            check.accept(shownA.isEmpty()&&shownB.size()==2&&display.isValid(),"opting out immediately hides only that viewer's labels");
            core.settings().set(b.getUniqueId(),Setting.MOB_HEALTH_BARS,false);service.refreshViewer(b);
            check.accept(overhead.isEmpty()&&!display.isValid(),"last-viewer opt-out removes the display entities");
            core.settings().set(a.getUniqueId(),Setting.MOB_HEALTH_BARS,true);sight[0]=false;service.refreshViewer(a);
            check.accept(shownA.isEmpty()&&overhead.isEmpty(),"enemies behind walls do not acquire visible labels");
            sight[0]=true;sight[1]=false;service.refreshViewer(a);
            check.accept(shownA.isEmpty()&&overhead.isEmpty(),"hidden mobs do not leak their location through a nameplate");
            sight[1]=true;nearby.clear();nearby.add(entities.get(0));service.refreshViewer(a);
            Object indicator=overhead.get(entities.get(0).getUniqueId());
            var getDisplay=indicator.getClass().getDeclaredMethod("display");getDisplay.setAccessible(true);display=(TextDisplay)getDisplay.invoke(indicator);
            var engage=MobHealthBarService.class.getDeclaredMethod("showOverhead",LivingEntity.class,double.class,Player.class);engage.setAccessible(true);
            engage.invoke(service,entities.get(0),10D,a);
            check.accept(overhead.size()==1&&plain(display.text()).contains("\n")&&plain(display.text()).contains("10/"),"combat expands the existing label with health instead of spawning a duplicate");
            var clock=MobHealthBarService.class.getDeclaredField("clock");clock.setAccessible(true);clock.setLong(service,1000);
            var render=MobHealthBarService.class.getDeclaredMethod("updateOverhead",indicator.getClass(),double.class);render.setAccessible(true);render.invoke(service,indicator,10D);
            check.accept(!plain(display.text()).contains("\n")&&display.isValid(),"the combat label returns to its idle name when combat expires");
            entities.get(0).remove();var update=MobHealthBarService.class.getDeclaredMethod("update");update.setAccessible(true);update.invoke(service);
            check.accept(!display.isValid()&&overhead.isEmpty(),"removed mobs release their native display and tracking entry");

            core.getConfig().set(root+"nameplate-worlds",List.of("unrelated-world"));
            var elsewhere=new MobHealthBarService(core,core.mobs(),core.settings());services.add(elsewhere);nearby.clear();nearby.add(entities.get(1));shownA.clear();elsewhere.refreshViewer(a);
            check.accept(shownA.isEmpty(),"ambient nameplates do not spread into unconfigured worlds");
            core.getConfig().set(root+"nameplate-worlds",List.of(world.getName()));core.getConfig().set(root+"nameplates-per-viewer",24);
            var capped=new MobHealthBarService(core,core.mobs(),core.settings());services.add(capped);nearby.clear();shownA.clear();
            for(int n=0;n<12;n++){var mob=core.mobs().spawnZone("vanilla:zombie",at.clone().add(1+n*.25,0,0),5,e->{e.setAI(false);e.setGravity(false);});entities.add(mob);nearby.add(mob);}
            capped.refreshViewer(a);capped.refreshViewer(a);
            check.accept(((Map<?,?>)field.get(capped)).size()==8&&shownA.size()==8,"global display cap is enforced and repeated scans reuse existing entities");
            capped.close();check.accept(((Map<?,?>)field.get(capped)).isEmpty(),"shutdown clears all nameplate tracking");
        } finally {
            services.forEach(MobHealthBarService::close);entities.forEach(entity->{core.mobs().forget(entity);entity.remove();});
            previous.forEach((key,value)->core.getConfig().set(root+key,value));
        }
    }
    private static String plain(Component text){return PlainTextComponentSerializer.plainText().serialize(text);}
    private static Player viewer(World world,Location at,List<Entity> nearby,Set<UUID> shown,boolean[] sight) {
        UUID id=UUID.randomUUID();
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
            case "getUniqueId"->id;case "getWorld"->world;case "getLocation"->at.clone();case "isOnline"->true;
            case "getNearbyEntities"->List.copyOf(nearby);case "hasLineOfSight"->sight[0];
            case "canSee"->sight.length<2||sight[1];
            case "showEntity"->{shown.add(((Entity)args[1]).getUniqueId());yield null;}
            case "hideEntity"->{shown.remove(((Entity)args[1]).getUniqueId());yield null;}
            case "hideBossBar","showBossBar"->null;
            case "hashCode"->id.hashCode();case "equals"->proxy==args[0];case "toString"->"NameplateProbe";
            default->throw new UnsupportedOperationException("Nameplate viewer: "+method.getName());
        });
    }
}
