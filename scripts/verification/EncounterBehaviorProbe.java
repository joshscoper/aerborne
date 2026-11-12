package net.nerdsmp.probe;

import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

final class EncounterBehaviorProbe {
    static Runnable begin(NerdSmpPlugin core, World world, BiConsumer<Boolean,String> check) {
        long time=world.getTime(); boolean storm=world.hasStorm(), thunder=world.isThundering();
        world.setTime(6000); world.setStorm(false); world.setThundering(false);
        Location at=new Location(world,8.5,180,8.5); world.getChunkAt(at).load();
        for(String id:net.nerdsmp.paper.zone.ZoneMobTypes.vanillaIds()) {
            LivingEntity candidate=core.mobs().spawnZone(id,at,5,mob->{});
            try { check.accept(candidate.isValid(),"shared encounter safeguards support ground archetype: "+id); }
            finally { candidate.remove(); core.mobs().forget(candidate); }
        }
        List<LivingEntity> spawned=new ArrayList<>();
        Zombie zombie=(Zombie)core.mobs().spawnZone("vanilla:zombie",at,5,mob->mob.setGravity(false)); spawned.add(zombie);
        AbstractSkeleton skeleton=(AbstractSkeleton)core.mobs().spawnZone("vanilla:skeleton",at.clone().add(2,0,0),5,mob->mob.setGravity(false)); spawned.add(skeleton);
        zombie.getEquipment().setHelmet(null); skeleton.getEquipment().setHelmet(null);
        check.accept(!zombie.shouldBurnInDay() && !skeleton.shouldBurnInDay(),"helmetless managed undead explicitly disable daylight burning");
        check.accept(!zombie.getCanPickupItems() && !zombie.canBreakDoors(),"encounters cannot replace their loadout or break doors");
        Zombie ordinary=world.spawn(at.clone().add(0,0,3),Zombie.class); ordinary.setGravity(false); ordinary.getEquipment().setHelmet(null); spawned.add(ordinary);
        check.accept(ordinary.shouldBurnInDay(),"ordinary vanilla undead retain normal daylight rules");
        Zombie custom=(Zombie)core.mobs().spawn("straw_scarecrow",at.clone().add(3,0,3)); custom.setGravity(false); custom.getEquipment().setHelmet(null); spawned.add(custom);
        check.accept(!custom.shouldBurnInDay(),"direct custom spawn is protected without relying on its costume helmet");
        zombie.setShouldBurnInDay(true);
        core.mobs().onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(at),List.of(zombie)));
        check.accept(!zombie.shouldBurnInDay(),"chunk reload reapplies behavior to an existing managed creature");
        PiglinAbstract piglin=(PiglinAbstract)core.mobs().spawnEncounter("vanilla:piglin",at,5,mob->mob.setGravity(false)); spawned.add(piglin);
        Hoglin hoglin=(Hoglin)core.mobs().spawnEncounter("vanilla:hoglin",at,5,mob->mob.setGravity(false)); spawned.add(hoglin);
        check.accept(piglin.isImmuneToZombification() && hoglin.isImmuneToZombification(),"overworld encounters retain piglin and hoglin identities");
        piglin.remove(); hoglin.remove(); // Keep native mob rivalries out of the isolated daylight check.
        for(var reason:new EntityTransformEvent.TransformReason[]{EntityTransformEvent.TransformReason.DROWNED,EntityTransformEvent.TransformReason.FROZEN}) {
            var managed=new EntityTransformEvent(zombie,List.of(zombie),reason); Bukkit.getPluginManager().callEvent(managed);
            var vanilla=new EntityTransformEvent(ordinary,List.of(ordinary),reason); Bukkit.getPluginManager().callEvent(vanilla);
            check.accept(managed.isCancelled() && !vanilla.isCancelled(),"conversion guard distinguishes encounters from vanilla mobs: "+reason);
        }
        var blockFire=new org.bukkit.event.entity.EntityCombustByBlockEvent(null,zombie,8f);
        Bukkit.getPluginManager().callEvent(blockFire);
        var attackFire=new org.bukkit.event.entity.EntityCombustByEntityEvent(ordinary,zombie,8f);
        Bukkit.getPluginManager().callEvent(attackFire);
        check.accept(!blockFire.isCancelled() && !attackFire.isCancelled(),"sunlight fallback preserves block/lava and combat ignition events");
        Zombie fire=(Zombie)core.mobs().spawnZone("vanilla:zombie",at,5,mob->mob.setGravity(false)); spawned.add(fire);
        double fireHealth=fire.getHealth(); fire.setFireTicks(80);
        for(LivingEntity entity:spawned) if(entity.isValid()) { entity.setRemoveWhenFarAway(false); entity.setPersistent(true); }
        return ()->{
            try {
                check.accept(zombie.isValid() && skeleton.isValid() && custom.isValid() && zombie.getFireTicks()<=0 && skeleton.getFireTicks()<=0 && custom.getFireTicks()<=0,
                    "helmetless managed undead stay unburnt through a native sunny-noon tick window: zombie="+zombie.isValid()+"/"+zombie.getFireTicks()
                    +", skeleton="+skeleton.isValid()+"/"+skeleton.getFireTicks()+", custom="+custom.isValid()+"/"+custom.getFireTicks());
                check.accept(ordinary.getFireTicks()>0 || ordinary.getHealth()<ordinary.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                    "same daylight window burns the unprotected vanilla control");
                check.accept(fire.getHealth()<fireHealth && !fire.isInvulnerable(),"real fire still damages a sunlight-protected encounter");
            } finally {
                spawned.forEach(LivingEntity::remove); world.setTime(time); world.setStorm(storm); world.setThundering(thunder);
            }
        };
    }
}
