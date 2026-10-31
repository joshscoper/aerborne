package net.nerdsmp.paper.expedition.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One enemy's threat: who it hates most and whether that's enough to change targets. A mob keeps its
 * target until someone passes 110% of that target's threat in melee reach, or 130% from further away,
 * so a target doesn't flicker between two close attackers. A taunt jumps to the top of the table and
 * forces the mob for a few seconds. Free of Bukkit: callers say who can still be targeted.
 */
public final class ThreatTable {
    /** Threat needed to steal the mob's attention, relative to its current target's. */
    public static final double MELEE_SWITCH = 1.1;
    public static final double RANGED_SWITCH = 1.3;
    /** Threat added on top of the highest when a taunt lands. */
    public static final double TAUNT_LEAD = 1.1;

    private final Map<UUID, Double> threat = new HashMap<>();
    private UUID target;
    private UUID forced;
    private long forcedUntil;

    public void add(UUID who, double amount) {
        if (who == null || amount <= 0) return;
        threat.merge(who, amount, Double::sum);
    }

    /** {@code who} takes the lead and holds the mob until {@code until}, whatever anyone else does. */
    public void taunt(UUID who, long until) {
        double top = threat.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        threat.put(who, Math.max(1, top * TAUNT_LEAD));
        forced = who;
        forcedUntil = until;
        target = who;
    }

    public void remove(UUID who) {
        threat.remove(who);
        if (who.equals(target)) target = null;
        if (who.equals(forced)) forced = null;
    }

    public void clear() {
        threat.clear();
        target = null;
        forced = null;
    }

    public boolean empty() { return threat.isEmpty(); }

    public double threat(UUID who) { return threat.getOrDefault(who, 0.0); }

    public UUID current() { return target; }

    /**
     * Who the mob should fight now, or null when it has nobody left. {@code valid} says who can still
     * be targeted (others drop off the table); {@code inMelee} who is in melee reach of it.
     */
    public UUID choose(Predicate<UUID> valid, Predicate<UUID> inMelee, long now) {
        threat.keySet().removeIf(valid.negate());
        if (target != null && !threat.containsKey(target)) target = null;
        if (forced != null && (now >= forcedUntil || !threat.containsKey(forced))) forced = null;
        if (forced != null) return target = forced;
        UUID top = null;
        double best = -1;
        for (var entry : threat.entrySet())
            if (entry.getValue() > best) {
                best = entry.getValue();
                top = entry.getKey();
            }
        if (top == null) return target = null;
        if (target == null) return target = top;
        if (top.equals(target)) return target;
        double needed = threat.get(target) * (inMelee.test(top) ? MELEE_SWITCH : RANGED_SWITCH);
        if (best > needed) target = top;
        return target;
    }
}
