package net.nerdsmp.paper.expedition;

import com.destroystokyo.paper.entity.ai.GoalType;
import net.kyori.adventure.text.Component;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.combat.AbilityCaster;
import net.nerdsmp.paper.expedition.combat.Telegraph;
import net.nerdsmp.paper.expedition.combat.ThreatTable;
import net.nerdsmp.paper.expedition.content.Abilities;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * How enemies pick fights in a run. Every enemy keeps a {@link ThreatTable} and fights the top of it,
 * not the nearest player. Anyone can steer that: a raised shield that takes a hit draws far more
 * attention than damage does, and sneaking with a shield and right-clicking taunts everything close.
 * Casts show a bar over the mob and root it; a charged melee hit or a full-draw arrow can stop one.
 * Casters hang back and fight with their abilities instead of walking into melee.
 */
final class RunCombat {
    /** What the run tells the fight about its world. */
    interface Host {
        boolean live();
        List<Player> online();
        boolean inCutscene(Player player);
        /** Living enemies being fought (or able to be), not those waiting in a pack. */
        Set<UUID> engaged();
        boolean ally(Entity entity);
        void logAbility(String line);
        AbilityCaster.Arena arena();
        /** Allies draw this many times the threat of their damage. */
        double allyThreat(Entity ally);
    }

    private static final long TICK = 5;
    /** Threat for every point of a hit a raised shield took. */
    private static final double BLOCK_THREAT = 4;
    private static final double INTERRUPT_THREAT = 15;
    static final long TAUNT_COOLDOWN_MILLIS = 12_000;
    private static final long TAUNT_HOLD_MILLIS = 4_000;
    private static final double TAUNT_RADIUS = 10;
    /** How far from a mob a player can be and stay on its table. */
    private static final double FORGET_RADIUS = 56;
    private static final double SEED_RADIUS = 28;
    private static final double MELEE_REACH = 4.5;
    /** Distances a caster tries to keep from its target. */
    private static final double CASTER_NEAR = 6, CASTER_FAR = 15;

    private final NerdSmpPlugin plugin;
    private final Host host;
    private final Map<UUID, ThreatTable> tables = new HashMap<>();
    private final Map<UUID, Cast> casts = new HashMap<>();
    private final Map<UUID, Long> taunts = new HashMap<>();
    private final Set<UUID> kiting = new HashSet<>();
    private BukkitTask task;
    private int tick;

    /** A cast in progress, its bar and how many charged hits it has taken. */
    private static final class Cast {
        final Abilities.Info info;
        final Consumer<Boolean> finish;
        final boolean wasAware;
        final long started;
        final long ends;
        TextDisplay bar;
        int hits;

        Cast(Abilities.Info info, Consumer<Boolean> finish, boolean wasAware, long started) {
            this.info = info;
            this.finish = finish;
            this.wasAware = wasAware;
            this.started = started;
            this.ends = started + Math.round(info.castSeconds() * 1000);
        }
    }

    RunCombat(NerdSmpPlugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID caster : List.copyOf(casts.keySet())) endCast(caster, false);
        tables.clear();
    }

    private void tick() {
        if (!host.live()) return;
        long now = System.currentTimeMillis();
        for (UUID uuid : host.engaged()) {
            if (!(Bukkit.getEntity(uuid) instanceof Mob mob) || mob.isDead()) continue;
            LivingEntity target = choose(mob, now);
            if (target != null && !target.equals(mob.getTarget())) mob.setTarget(target);
            if (target != null && kiting.contains(uuid) && !casts.containsKey(uuid)) keepDistance(mob, target);
        }
        for (UUID uuid : List.copyOf(casts.keySet())) tickCast(uuid, now);
        if (++tick % 4 == 0) warnAggro();
    }

    private LivingEntity choose(Mob mob, long now) {
        ThreatTable table = tables.computeIfAbsent(mob.getUniqueId(), key -> new ThreatTable());
        if (table.empty()) nearest(mob, SEED_RADIUS).ifPresent(player -> table.add(player.getUniqueId(), 1));
        Location at = mob.getLocation();
        Predicate<UUID> valid = uuid -> reachable(uuid, at);
        Predicate<UUID> melee = uuid -> Bukkit.getEntity(uuid) instanceof LivingEntity living
            && living.getLocation().distanceSquared(at) <= MELEE_REACH * MELEE_REACH;
        UUID before = table.current();
        UUID chosen = table.choose(valid, melee, now);
        if (chosen == null || !(Bukkit.getEntity(chosen) instanceof LivingEntity living)) return null;
        if (before != null && !before.equals(chosen))
            host.logAbility("threat: " + mob.getType().name().toLowerCase(java.util.Locale.ROOT) + " turned on " + living.getName());
        return living;
    }

    private boolean reachable(UUID uuid, Location from) {
        Entity entity = Bukkit.getEntity(uuid);
        if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead() || entity.getWorld() != from.getWorld()) return false;
        if (entity.getLocation().distanceSquared(from) > FORGET_RADIUS * FORGET_RADIUS) return false;
        if (entity instanceof Player player) return player.getGameMode() != GameMode.SPECTATOR && !host.inCutscene(player);
        return host.ally(entity);
    }

    private java.util.Optional<Player> nearest(Mob mob, double radius) {
        Location at = mob.getLocation();
        return host.online().stream().filter(player -> player.getWorld() == at.getWorld() && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR && !host.inCutscene(player)
                && player.getLocation().distanceSquared(at) <= radius * radius)
            .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(at)));
    }

    /** A caster steps away from anyone on top of it and closes in when it can't see its target. */
    private void keepDistance(Mob mob, LivingEntity target) {
        Location at = mob.getLocation(), to = target.getLocation();
        double distance = Math.sqrt(at.distanceSquared(to));
        mob.lookAt(target);
        var path = mob.getPathfinder();
        if (distance < CASTER_NEAR) {
            var away = at.toVector().subtract(to.toVector()).setY(0);
            if (away.lengthSquared() < 1e-3) return;
            path.moveTo(at.clone().add(away.normalize().multiply(6)), 1.15);
        } else if (distance > CASTER_FAR || !mob.hasLineOfSight(target)) path.moveTo(target, 1.0);
        else path.stopPathfinding();
    }

    /** Parties get a warning when something turns on them; a solo player already knows. */
    private void warnAggro() {
        List<Player> online = host.online();
        if (online.size() < 2) return;
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID uuid : host.engaged()) {
            ThreatTable table = tables.get(uuid);
            if (table != null && table.current() != null) counts.merge(table.current(), 1, Integer::sum);
        }
        for (Player player : online) {
            int count = counts.getOrDefault(player.getUniqueId(), 0);
            if (count > 0) player.sendActionBar(Chat.render("<red>" + count + (count == 1 ? " enemy is" : " enemies are") + " on you</red>"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events from the run
    // ---------------------------------------------------------------------------------------------

    /** The mob now fights without its vanilla movement: {@link #keepDistance} moves it. */
    void caster(Mob mob) {
        Bukkit.getMobGoals().removeAllGoals(mob, GoalType.MOVE);
        Bukkit.getMobGoals().removeAllGoals(mob, GoalType.LOOK);
        kiting.add(mob.getUniqueId());
    }

    /** Someone hit an enemy. {@code charged}: a full swing or full-draw arrow, which can stop a cast. */
    void hit(Entity source, LivingEntity enemy, double amount, boolean charged) {
        double threat = source instanceof Player ? amount : amount * host.allyThreat(source);
        tables.computeIfAbsent(enemy.getUniqueId(), key -> new ThreatTable()).add(source.getUniqueId(), Math.max(.5, threat));
        Cast cast = casts.get(enemy.getUniqueId());
        if (cast == null || !charged || !(source instanceof Player player) || !cast.info.interruptible()) return;
        cast.hits++;
        if (cast.hits < cast.info.interruptHits()) {
            enemy.getWorld().playSound(enemy.getLocation(), Sound.BLOCK_ANVIL_LAND, .5F, 1.6F);
            return;
        }
        tables.get(enemy.getUniqueId()).add(player.getUniqueId(), INTERRUPT_THREAT);
        enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.2F, .6F);
        enemy.getWorld().spawnParticle(Particle.CRIT, enemy.getLocation().add(0, enemy.getHeight() / 2, 0), 24, .4, .5, .4, .2);
        enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 3, false, false));
        for (Player member : host.online())
            member.sendActionBar(Chat.render("<aqua>" + Chat.escape(player.getName()) + " interrupted " + Chat.escape(cast.info.title()) + "</aqua>"));
        host.logAbility(player.getName() + " interrupted " + cast.info.id());
        endCast(enemy.getUniqueId(), true);
    }

    /** A raised shield took a hit: nothing draws a mob's attention like standing up to it. */
    void blocked(Player player, LivingEntity enemy, double amount) {
        tables.computeIfAbsent(enemy.getUniqueId(), key -> new ThreatTable()).add(player.getUniqueId(), Math.max(4, amount * BLOCK_THREAT));
        host.logAbility(player.getName() + " blocked a hit");
    }

    /** Sneak and right-click a shield: every enemy close by turns on you for a few seconds. */
    boolean taunt(Player player, List<LivingEntity> enemies) {
        long now = System.currentTimeMillis();
        long ready = taunts.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            player.sendActionBar(Chat.render("<gray>Taunt ready in " + Math.max(1, (ready - now + 999) / 1000) + "s</gray>"));
            return false;
        }
        int taunted = 0;
        for (LivingEntity enemy : enemies) {
            if (enemy.getLocation().distanceSquared(player.getLocation()) > TAUNT_RADIUS * TAUNT_RADIUS) continue;
            ThreatTable table = tables.computeIfAbsent(enemy.getUniqueId(), key -> new ThreatTable());
            UUID before = table.current();
            table.taunt(player.getUniqueId(), now + TAUNT_HOLD_MILLIS);
            if (before != null && !before.equals(player.getUniqueId()))
                host.logAbility("threat: " + enemy.getType().name().toLowerCase(java.util.Locale.ROOT) + " turned on " + player.getName());
            if (enemy instanceof Mob mob) mob.setTarget(player);
            enemy.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, enemy.getLocation().add(0, enemy.getHeight(), 0), 2, .2, .2, .2, 0);
            taunted++;
        }
        if (taunted == 0) return false;
        taunts.put(player.getUniqueId(), now + TAUNT_COOLDOWN_MILLIS);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.2F, .7F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, .5F, 1.6F);
        player.sendActionBar(Chat.render("<gold>Taunted " + taunted + (taunted == 1 ? " enemy" : " enemies") + "</gold>"));
        host.logAbility(player.getName() + " taunted " + taunted);
        return true;
    }

    void died(UUID enemy) {
        endCast(enemy, false);
        tables.remove(enemy);
        kiting.remove(enemy);
    }

    /** The mob forgets everyone: it's going home, or waiting for the next fight. */
    void forget(UUID enemy) {
        endCast(enemy, false);
        ThreatTable table = tables.get(enemy);
        if (table != null) table.clear();
    }

    /** A player's or ally's threat is wiped everywhere: they left, or the party regrouped. */
    void forgetParticipant(UUID who) {
        tables.values().forEach(table -> table.remove(who));
    }

    /** Whoever this mob is fighting, for abilities that aim at its target. */
    LivingEntity target(LivingEntity mob) {
        ThreatTable table = tables.get(mob.getUniqueId());
        if (table != null && table.current() != null && Bukkit.getEntity(table.current()) instanceof LivingEntity living
            && living.isValid() && !living.isDead()) return living;
        return mob instanceof Mob creature ? creature.getTarget() : null;
    }

    double threat(UUID enemy, UUID who) {
        ThreatTable table = tables.get(enemy);
        return table == null ? 0 : table.threat(who);
    }

    // ---------------------------------------------------------------------------------------------
    // Casts
    // ---------------------------------------------------------------------------------------------

    boolean casting(UUID uuid) { return casts.containsKey(uuid); }

    /** Starts a cast with a bar over the mob. {@code finish} runs at the end with true if it was stopped. */
    void cast(LivingEntity caster, Abilities.Info info, Consumer<Boolean> finish) {
        boolean aware = caster instanceof Mob mob && mob.isAware();
        Cast cast = new Cast(info, finish, aware, System.currentTimeMillis());
        casts.put(caster.getUniqueId(), cast);
        if (caster instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.setAware(false);
        }
        cast.bar = caster.getWorld().spawn(caster.getLocation().add(0, caster.getHeight(), 0), TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(true);
            display.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            display.setTransformation(new Transformation(new Vector3f(0, .45F, 0), new AxisAngle4f(), new Vector3f(.8F, .8F, .8F), new AxisAngle4f()));
            display.text(bar(cast, cast.started));
        });
        caster.addPassenger(cast.bar);
        caster.getWorld().playSound(caster.getLocation(), info.interruptible() ? Sound.ENTITY_EVOKER_PREPARE_ATTACK : Sound.BLOCK_ANVIL_PLACE, 1F, 1.2F);
    }

    /** Stops a cast without it landing or counting as an interrupt (the boss submerged, say). */
    void cancelCast(UUID uuid) { endCast(uuid, false); }

    private void tickCast(UUID uuid, long now) {
        Cast cast = casts.get(uuid);
        Entity entity = Bukkit.getEntity(uuid);
        if (cast == null) return;
        if (!(entity instanceof LivingEntity caster) || caster.isDead() || !caster.isValid()) {
            endCast(uuid, false);
            return;
        }
        if (now >= cast.ends) {
            casts.remove(uuid);
            release(caster, cast);
            cast.finish.accept(false);
            return;
        }
        if (cast.bar != null && cast.bar.isValid()) cast.bar.text(bar(cast, now));
        if (!cast.info.interruptible()) {
            LivingEntity target = target(caster);
            if (target != null) {
                Telegraph.ring(caster.getLocation(), 4.5, Telegraph.DANGER);
                if (target instanceof Player player && (now - cast.started) < TICK * 50 + 10)
                    player.sendActionBar(Chat.render("<red>" + Chat.escape(cast.info.title()) + " on you! Raise a shield or get out of reach.</red>"));
            }
        }
    }

    private void endCast(UUID uuid, boolean interrupted) {
        Cast cast = casts.remove(uuid);
        if (cast == null) return;
        if (Bukkit.getEntity(uuid) instanceof LivingEntity caster) release(caster, cast);
        else if (cast.bar != null) cast.bar.remove();
        cast.finish.accept(true);
    }

    private void release(LivingEntity caster, Cast cast) {
        if (cast.bar != null) cast.bar.remove();
        cast.bar = null;
        if (caster instanceof Mob mob) mob.setAware(cast.wasAware);
    }

    private static Component bar(Cast cast, long now) {
        double done = Math.max(0, Math.min(1, (now - cast.started) / (cast.info.castSeconds() * 1000)));
        int filled = (int) Math.round(done * 10);
        String color = cast.info.interruptible() ? "yellow" : "red";
        String hits = cast.info.interruptHits() > 1 ? " <white>" + cast.hits + "/" + cast.info.interruptHits() + "</white>" : "";
        return Chat.render("<" + color + ">" + Chat.escape(cast.info.title()) + hits + "</" + color + ">\n<" + color + ">"
            + "■".repeat(filled) + "</" + color + "><dark_gray>" + "■".repeat(10 - filled) + "</dark_gray>");
    }

    /** Enemies a shield taunt can reach, for the listener. */
    static boolean shield(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.SHIELD
            || player.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    /** All enemies currently carrying a table, for tests and debugging. */
    List<UUID> tracked() { return new ArrayList<>(tables.keySet()); }
}
