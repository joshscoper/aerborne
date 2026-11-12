package net.nerdsmp.paper.mob;

import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityTransformEvent.TransformReason;

/** Stable RPG identities without granting general immunity to combat or environmental damage. */
public final class EncounterBehavior {
    public static final org.bukkit.NamespacedKey MARKER = new org.bukkit.NamespacedKey("nerdsmp", "encounter_behavior");
    private EncounterBehavior() { }
    public static void apply(LivingEntity entity) {
        entity.getPersistentDataContainer().set(MARKER, org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
        entity.setCanPickupItems(false);
        if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            zombie.setCanBreakDoors(false);
            if (zombie.isDrowning()) zombie.stopDrowning();
            if (zombie.isConverting()) zombie.setConversionTime(-1);
        }
        if (entity instanceof AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(false);
        if (entity instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
        if (entity instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
    }
    public static boolean changesIdentity(TransformReason reason) {
        return switch (reason) {
            case DROWNED, FROZEN, PIGLIN_ZOMBIFIED, INFECTION, CURED, LIGHTNING -> true;
            default -> false;
        };
    }
}
