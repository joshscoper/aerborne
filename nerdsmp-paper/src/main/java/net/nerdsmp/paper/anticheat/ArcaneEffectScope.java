package net.nerdsmp.paper.anticheat;

import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * The one trusted path for damage that an Arcane Signature deals on the server's behalf.
 *
 * <p>Like {@link net.nerdsmp.paper.gathering.TreeHarvestBridge}, an authorization exists only for
 * the duration of one synchronous call on the main thread, and only for one source player, one
 * target and a bounded amount of damage. It cannot be created, replayed or widened by anything a
 * client sends, and it grants no other exemption: protection, PvP and claim listeners still see and
 * may cancel the damage event. Only listeners that would otherwise mistake it for a weapon swing
 * (weapon stats, life steal, poison coatings, Watchdog's reach and hitbox checks) consult it.
 */
public final class ArcaneEffectScope {
    /** What one authorization covers. {@code sequence} is unique per authorization for this server run. */
    public record Effect(UUID player, String effect, UUID target, double maximumDamage, long sequence) {
    }

    private static final ThreadLocal<Effect> ACTIVE = new ThreadLocal<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicLong GRANTED = new AtomicLong();
    private static final AtomicLong RECOGNISED = new AtomicLong();
    private static final AtomicLong REFUSED = new AtomicLong();

    private ArcaneEffectScope() {
    }

    /**
     * Runs {@code action} with one authorization in place. Nesting is refused, so an effect can never
     * authorize a second effect from inside its own damage event.
     *
     * @return the action's result, or false when the authorization was refused
     */
    public static boolean run(UUID player, String effect, UUID target, double maximumDamage, BooleanSupplier action) {
        if (ACTIVE.get() != null || player == null || target == null || player.equals(target)
            || !(maximumDamage > 0.0D) || !Double.isFinite(maximumDamage)) {
            REFUSED.incrementAndGet();
            return false;
        }
        ACTIVE.set(new Effect(player, effect, target, maximumDamage, SEQUENCE.incrementAndGet()));
        GRANTED.incrementAndGet();
        try {
            return action.getAsBoolean();
        } finally {
            ACTIVE.remove();
        }
    }

    public static Optional<Effect> active() {
        return Optional.ofNullable(ACTIVE.get());
    }

    /** True only for the exact damage event the active authorization was granted for. */
    public static boolean covers(EntityDamageByEntityEvent event) {
        return covers(event.getDamager().getUniqueId(), event.getEntity().getUniqueId(), event.getDamage());
    }

    static boolean covers(UUID damager, UUID target, double damage) {
        Effect effect = ACTIVE.get();
        boolean covered = effect != null && effect.player().equals(damager) && effect.target().equals(target)
            && damage <= effect.maximumDamage() + 1.0E-6D;
        if (covered) RECOGNISED.incrementAndGet();
        return covered;
    }

    /** True while any authorization is active on this thread; used to stop signatures re-triggering. */
    public static boolean inside() {
        return ACTIVE.get() != null;
    }

    public record Stats(long granted, long recognised, long refused) {
    }

    public static Stats stats() {
        return new Stats(GRANTED.get(), RECOGNISED.get(), REFUSED.get());
    }
}
