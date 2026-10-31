package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Pack;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.flow.PackTracker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Enemies already standing in the map, in packs a party can look at and plan around. Each pack waits
 * (or walks its patrol) with its AI off, so it won't notice anyone until {@link PackTracker} says so.
 * A pulled pack fights as one, and a fight that spills near another pack wakes that pack too.
 */
final class RunPacks {
    /** What the run provides. */
    interface Host {
        World world();
        ExpeditionDefinition expedition();
        List<Player> online();
        boolean inCutscene(Player player);
        int scaled(int count);
        /** Spawns a tracked encounter mob for a pack. */
        BiFunction<Pack, String, java.util.function.Function<Location, LivingEntity>> spawner();
        RunCombat combat();
        void cleared(String pack);
        void logAbility(String line);
    }

    /** How often the packs are checked, in the run's second-long ticks' terms: every half second. */
    private static final long PERIOD_TICKS = 10;
    /** A fight this close, in plain sight, wakes a waiting pack. */
    private static final double ASSIST_RADIUS = 6;
    private static final double PATROL_SPEED = .85;
    private static final long PATROL_PAUSE_MILLIS = 2_500;

    private final org.bukkit.plugin.Plugin plugin;
    private final Host host;
    private final Map<String, Live> packs = new LinkedHashMap<>();
    private final Map<UUID, Live> owner = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask task;

    /** A pack in the world: where each member belongs, and where its patrol has got to. */
    private final class Live {
        final Pack pack;
        final PackTracker rules;
        final Map<UUID, Location> home = new LinkedHashMap<>();
        int stop;
        int direction = 1;
        long pauseUntil;

        Live(Pack pack) {
            this.pack = pack;
            this.rules = new PackTracker(pack.aggro(), pack.leash());
        }

        Location center() { return new Location(host.world(), pack.at().x() + .5, pack.at().y(), pack.at().z() + .5, pack.at().yaw(), 0); }

        List<LivingEntity> living() {
            List<LivingEntity> found = new java.util.ArrayList<>();
            for (UUID uuid : home.keySet())
                if (Bukkit.getEntity(uuid) instanceof LivingEntity entity && entity.isValid() && !entity.isDead()) found.add(entity);
            return found;
        }
    }

    RunPacks(org.bukkit.plugin.Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    /** Puts every pack on the map, waiting. */
    void begin() {
        for (Pack pack : host.expedition().story().packs().values()) {
            Live live = new Live(pack);
            packs.put(pack.id(), live);
            int size = pack.mobs().values().stream().mapToInt(host::scaled).sum();
            int[] index = {0};
            var spawner = host.spawner();
            pack.mobs().forEach((mob, count) -> {
                for (int copy = 0; copy < host.scaled(count); copy++) {
                    double[] offset = PackTracker.offset(index[0]++, size);
                    Location at = live.center().add(offset[0], 0, offset[1]);
                    if (!at.getBlock().isPassable()) at = live.center();
                    LivingEntity entity = spawner.apply(pack, mob).apply(at);
                    if (entity == null) continue;
                    live.home.put(entity.getUniqueId(), at);
                    owner.put(entity.getUniqueId(), live);
                    settle(entity, pack);
                }
            });
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    void close() {
        if (task != null) task.cancel();
        task = null;
    }

    /** With no patrol, a waiting mob's AI is off entirely: it stands where it was put. */
    private void settle(LivingEntity entity, Pack pack) {
        if (!(entity instanceof Mob mob)) return;
        mob.setTarget(null);
        mob.setAware(!pack.patrol().isEmpty());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Live live : packs.values()) tick(live, now);
        // A fight spilling next to a waiting pack, in plain sight, drags it in.
        for (Live fighting : packs.values()) {
            if (!fighting.rules.fighting()) continue;
            List<LivingEntity> in = fighting.living();
            for (Live waiting : packs.values())
                if (waiting.rules.waiting() && close(in, waiting.living())) {
                    wake(waiting, null);
                    host.logAbility("pack " + waiting.pack.id() + " joined " + fighting.pack.id());
                }
        }
    }

    private static boolean close(List<LivingEntity> fighting, List<LivingEntity> waiting) {
        for (LivingEntity one : fighting) for (LivingEntity other : waiting)
            if (one.getWorld() == other.getWorld() && one.getLocation().distanceSquared(other.getLocation()) <= ASSIST_RADIUS * ASSIST_RADIUS
                && one.hasLineOfSight(other)) return true;
        return false;
    }

    private void tick(Live live, long now) {
        List<LivingEntity> members = live.living();
        boolean wasDead = live.rules.state() == PackTracker.State.DEAD;
        boolean target = false;
        double home = 0;
        Location center = live.center();
        var falls = host.expedition().story().falls();
        for (LivingEntity member : members) {
            if (falls != null && member.getY() < falls.below() && live.home.containsKey(member.getUniqueId())) member.teleport(live.home.get(member.getUniqueId()));
            home = Math.max(home, Math.sqrt(member.getLocation().distanceSquared(center)));
            if (member instanceof Mob mob && mob.getTarget() != null) target = true;
        }
        // The party running past the leash counts too, so nobody has to wait for the mobs to catch up.
        double party = -1;
        for (Player player : host.online())
            if (!player.isDead() && player.getWorld() == center.getWorld()) {
                double distance = Math.sqrt(player.getLocation().distanceSquared(center));
                if (party < 0 || distance < party) party = distance;
            }
        home = Math.max(home, party);
        PackTracker.State entered = live.rules.tick(new PackTracker.View(members.size(), live.rules.waiting() ? seen(live, members) : -1, home, target), now);
        if (entered == PackTracker.State.DEAD && !wasDead) {
            host.cleared(live.pack.id());
            return;
        }
        if (entered == PackTracker.State.FIGHTING) wake(live, null);
        else if (entered == PackTracker.State.RETURNING) retreat(live, members);
        else if (entered == PackTracker.State.WAITING) restore(live, members);
        if (live.rules.waiting() && !live.pack.patrol().isEmpty()) patrol(live, members, now);
    }

    /** The distance of the nearest player any member can see within its aggro radius, or -1. */
    private double seen(Live live, List<LivingEntity> members) {
        double best = -1;
        int radius = live.pack.aggro();
        for (Player player : host.online()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || host.inCutscene(player)) continue;
            for (LivingEntity member : members) {
                if (member.getWorld() != player.getWorld()) continue;
                double squared = member.getLocation().distanceSquared(player.getLocation());
                if (squared > radius * radius || !member.hasLineOfSight(player)) continue;
                double distance = Math.sqrt(squared);
                if (best < 0 || distance < best) best = distance;
            }
        }
        return best;
    }

    /** The pack wakes and fights; {@code cause} starts at the top of every member's table. */
    private void wake(Live live, LivingEntity cause) {
        live.rules.pull();
        List<LivingEntity> members = live.living();
        for (LivingEntity member : members) {
            member.setInvulnerable(false);
            if (member instanceof Mob mob) mob.setAware(true);
            if (cause != null) host.combat().hit(cause, member, 1, false);
        }
        if (!members.isEmpty()) {
            Location at = members.getFirst().getLocation();
            at.getWorld().playSound(at, Sound.ENTITY_WARDEN_ANGRY, .5F, 1.5F);
        }
        host.logAbility("pack " + live.pack.id() + " pulled");
    }

    /** A hit or a taunt on a member wakes everyone with it. */
    void struck(LivingEntity member, LivingEntity by) {
        Live live = owner.get(member.getUniqueId());
        if (live != null && !live.rules.fighting()) wake(live, by);
    }

    /** The pack gives up: it can't be hurt on the way home and forgets everyone. */
    private void retreat(Live live, List<LivingEntity> members) {
        host.logAbility("pack " + live.pack.id() + " gave up");
        for (LivingEntity member : members) {
            host.combat().forget(member.getUniqueId());
            member.setInvulnerable(true);
            if (!(member instanceof Mob mob)) continue;
            mob.setTarget(null);
            mob.setAware(true);
            Location home = live.home.get(member.getUniqueId());
            if (home != null) mob.getPathfinder().moveTo(home, 1.3);
        }
    }

    /** Home again, at full health. */
    private void restore(Live live, List<LivingEntity> members) {
        for (LivingEntity member : members) {
            Location home = live.home.get(member.getUniqueId());
            if (home != null && home.distanceSquared(member.getLocation()) > 4) member.teleport(home);
            var max = member.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) member.setHealth(max.getValue());
            member.setInvulnerable(false);
            host.combat().forget(member.getUniqueId());
            settle(member, live.pack);
            member.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, member.getLocation().add(0, 1, 0), 4, .3, .4, .3, 0);
        }
        live.stop = 0;
        live.direction = 1;
    }

    private void patrol(Live live, List<LivingEntity> members, long now) {
        if (members.isEmpty() || now < live.pauseUntil) return;
        List<Point> route = live.pack.patrol();
        Point stop = route.get(live.stop);
        Location goal = new Location(host.world(), stop.x() + .5, stop.y(), stop.z() + .5);
        if (members.getFirst().getLocation().distanceSquared(goal) <= 9) {
            int[] next = PackTracker.nextStop(live.stop, live.direction, route.size());
            live.stop = next[0];
            live.direction = next[1];
            live.pauseUntil = now + PATROL_PAUSE_MILLIS;
            return;
        }
        int size = members.size();
        for (int index = 0; index < size; index++) {
            if (!(members.get(index) instanceof Mob mob)) continue;
            double[] offset = PackTracker.offset(index, size);
            mob.getPathfinder().moveTo(goal.clone().add(offset[0], 0, offset[1]), PATROL_SPEED);
        }
    }

    void died(UUID uuid) { owner.remove(uuid); }

    /** Whether a mob is in a pack that isn't fighting, so it shouldn't target or cast. */
    boolean dormant(UUID uuid) {
        Live live = owner.get(uuid);
        return live != null && !live.rules.fighting();
    }

    boolean member(UUID uuid) { return owner.containsKey(uuid); }

    /** Living enemies of every pack that's fighting. */
    Set<UUID> fighting() {
        Set<UUID> found = new HashSet<>();
        for (Live live : packs.values()) if (live.rules.fighting()) found.addAll(live.home.keySet());
        return found;
    }

    /** Waiting and fighting members close to a spot, for taunts. */
    List<LivingEntity> near(Location at, double radius) {
        List<LivingEntity> found = new java.util.ArrayList<>();
        for (Live live : packs.values()) {
            if (live.rules.state() == PackTracker.State.RETURNING) continue;
            for (LivingEntity member : live.living())
                if (member.getWorld() == at.getWorld() && member.getLocation().distanceSquared(at) <= radius * radius) found.add(member);
        }
        return found;
    }

    int total() { return packs.size(); }
}
