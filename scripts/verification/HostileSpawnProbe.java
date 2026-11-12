package net.nerdsmp.probe;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Cow;
import org.bukkit.entity.LivingEntity;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiConsumer;

final class HostileSpawnProbe {
    @SuppressWarnings("unchecked")
    static void run(NerdSmpPlugin core, World world, BiConsumer<Boolean,String> check) throws Exception {
        var service=core.worldPacks();
        var pack=service.packs().getFirst();
        var ensure=service.getClass().getDeclaredMethod("ensureDifficulty",pack.getClass(),World.class); ensure.setAccessible(true);
        var maintain=service.getClass().getDeclaredMethod("maintainDifficulty"); maintain.setAccessible(true);
        var field=service.getClass().getDeclaredField("matched"); field.setAccessible(true);
        var matched=(Map<String,String>)field.get(service); var before=Map.copyOf(matched);
        Difficulty previous=world.getDifficulty();
        Object setting=core.getConfig().get("world-packs.maintain-combat-difficulty");
        File file=new File(core.getDataFolder(),"world-packs.yml");
        var state=YamlConfiguration.loadConfiguration(file);
        String key="packs."+pack.id()+"."+world.getUID()+".difficulty-raised";
        state.set(key,true); state.save(file); // Reproduce the persisted flag from an earlier successful repair.
        var entities=new ArrayList<LivingEntity>();
        try {
            Location at=new Location(world,8.5,165,8.5); world.getChunkAt(at).load();
            world.setDifficulty(Difficulty.PEACEFUL);
            Cow passive=world.spawn(at,Cow.class); entities.add(passive);
            check.accept(passive.isValid(),"Peaceful still permits passive creatures, matching the hostile-only symptom");
            boolean refused=false;
            try { var hostile=core.mobs().spawnZone("vanilla:zombie",at,5,mob->{}); entities.add(hostile); refused=!hostile.isValid()||hostile.isDead(); }
            catch(IllegalArgumentException | IllegalStateException expected) { refused=true; }
            check.accept(refused,"native Paper refuses hostile zone creatures on Peaceful");
            core.getConfig().set("world-packs.maintain-combat-difficulty",true);
            ensure.invoke(service,pack,world);
            check.accept(world.getDifficulty()==Difficulty.NORMAL,"startup repairs Peaceful despite a previous difficulty-raised flag");
            for(String type:new String[]{"zombie","skeleton","spider","witch"}) {
                LivingEntity mob=core.mobs().spawnZone("vanilla:"+type,at,5,candidate->{}); entities.add(mob);
                check.accept(mob.isValid()&&!mob.isDead(),"native hostile zone spawn succeeds after repair: "+type);
            }
            matched.put(pack.id(),world.getName()); world.setDifficulty(Difficulty.PEACEFUL); maintain.invoke(service);
            check.accept(world.getDifficulty()==Difficulty.NORMAL,"periodic guard repairs a later world-manager difficulty reset");
            world.setDifficulty(Difficulty.HARD); maintain.invoke(service);
            check.accept(world.getDifficulty()==Difficulty.HARD,"guard preserves a playable Hard setting");
            core.getConfig().set("world-packs.maintain-combat-difficulty",false);
            world.setDifficulty(Difficulty.PEACEFUL); ensure.invoke(service,pack,world); maintain.invoke(service);
            check.accept(world.getDifficulty()==Difficulty.PEACEFUL,"operator opt-out preserves intentional Peaceful difficulty");
        } finally {
            entities.forEach(entity->{if(entity!=null) entity.remove();});
            world.setDifficulty(previous); core.getConfig().set("world-packs.maintain-combat-difficulty",setting);
            matched.clear(); matched.putAll(before);
        }
    }
}
