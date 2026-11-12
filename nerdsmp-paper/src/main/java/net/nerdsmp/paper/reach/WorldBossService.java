package net.nerdsmp.paper.reach;

import net.kyori.adventure.bossbar.BossBar;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The Reach's world boss: a co-op fight on a timetable, after the scheduled raid bosses of Hytale's skyblock servers.
 * Every two hours (on even hours by default) Thunderhide comes to the Stone Rings. The server is warned five minutes
 * ahead and anyone can join with {@code /worldboss go}. It grows tougher with every fighter (the boss scaling all bosses
 * use), calls down lightning, brings its herd at two-thirds and one-third health, and leaves if not beaten in twenty
 * minutes. Everyone who did a fair share is paid by their share; the top fighter also earns an XP booster each week.
 */
public final class WorldBossService implements Listener, AutoCloseable {
    public static final String KILLS = "world-boss.kills";

    record Settings(boolean enabled, int intervalMinutes, int warnMinutes, int fleeMinutes, String spot, String mob) { }

    static Settings settings(ConfigurationSection s) {
        if (s == null) return new Settings(true, 120, 5, 20, "stone_rings.place", "thunderhide");
        return new Settings(s.getBoolean("enabled", true), Math.clamp(s.getInt("interval-minutes", 120), 30, 1440),
            Math.clamp(s.getInt("warning-minutes", 5), 1, 30), Math.clamp(s.getInt("flee-minutes", 20), 5, 60),
            s.getString("spot", "stone_rings.place"), s.getString("mob", "thunderhide"));
    }

    /** The next scheduled arrival after {@code now}: every interval, counted from midnight in the event zone. */
    static Instant next(Instant now, ZoneId zone, int intervalMinutes) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime midnight = local.toLocalDate().atStartOfDay(zone);
        long minutes = Duration.between(midnight, local).toMinutes();
        long slot = minutes / intervalMinutes + 1;
        return midnight.plusMinutes(slot * intervalMinutes).toInstant();
    }

    /** Each fighter's pay: a pool that grows with the crowd, split by share of damage, never under a floor. */
    static Map<UUID, Long> payouts(Map<UUID, Double> damage) {
        double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Long> pay = new HashMap<>();
        if (total <= 0) return pay;
        List<UUID> fair = damage.entrySet().stream().filter(e -> e.getValue() / total >= 0.02 || e.getValue() >= 40).map(Map.Entry::getKey).toList();
        long pool = 2_500 + 500L * fair.size();
        // The pool is split among those who earned a share, so nothing is lost to token hits.
        double fairTotal = fair.stream().mapToDouble(damage::get).sum();
        for (UUID id : fair) pay.put(id, Math.max(200, Math.round(pool * damage.get(id) / fairTotal)));
        return pay;
    }

    private final NerdSmpPlugin plugin;
    private final ReachService reach;
    private final Settings settings;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final BossBar timerBar = BossBar.bossBar(Chat.render("<aqua>Thunderhide</aqua>"), 1F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    private BukkitTask task;
    private Instant nextArrival;
    private boolean warned;
    private UUID boss;
    private long spawnedAt;
    private int phase;
    private long nextStrike;
    private int ticketX = Integer.MIN_VALUE, ticketZ;

    WorldBossService(NerdSmpPlugin plugin, ReachService reach) {
        this.plugin = plugin;
        this.reach = reach;
        this.settings = settings(plugin.getConfig().getConfigurationSection("events.world-boss"));
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    private ZoneId zone() { return plugin.boosters() == null ? ZoneId.of("America/Denver") : plugin.boosters().weekend().zone(); }

    private Location arena() {
        World world = reach.world();
        ReachFeatures.Spot spot = reach.features().spots().get(settings.spot());
        return world == null || spot == null ? null : new Location(world, spot.x(), spot.y(), spot.z(), spot.yaw(), 0F);
    }

    /** When Thunderhide next comes (for the calendar), or null when the world boss is off. */
    public Instant nextArrival() {
        if (!settings.enabled()) return null;
        if (nextArrival == null) nextArrival = next(Instant.now(), zone(), settings.intervalMinutes());
        return nextArrival;
    }

    public boolean active() { return boss != null && Bukkit.getEntity(boss) instanceof LivingEntity living && living.isValid() && !living.isDead(); }

    // ------------------------------------------------------------------ the timetable

    private void tick() {
        if (!settings.enabled() && boss == null) return;
        Instant now = Instant.now();
        if (nextArrival == null) nextArrival = next(now, zone(), settings.intervalMinutes());
        if (boss != null) { fight(now); return; }
        long until = Duration.between(now, nextArrival).toSeconds();
        if (!warned && until <= settings.warnMinutes() * 60L) {
            warned = true;
            Location arena = arena();
            if (arena != null) {
                ensureTicket(arena);
                broadcast("<aqua><bold>⚡ Thunder rolls over the Stone Rings.</bold></aqua> <gray>Thunderhide arrives in <white>"
                    + Chat.duration(Duration.ofSeconds(Math.max(1, until))) + "</white>. Gather a crew:</gray> <click:run_command:'/worldboss go'><yellow>[/worldboss go]</yellow></click>");
            }
        }
        if (warned) {
            timerBar.name(Chat.render("<aqua>⚡ Thunderhide arrives at the Stone Rings</aqua> <gray>in " + Chat.duration(Duration.ofSeconds(Math.max(0, until))) + "</gray>"));
            timerBar.progress((float) Math.clamp(until / (settings.warnMinutes() * 60D), 0D, 1D));
            for (Player p : Bukkit.getOnlinePlayers()) if (reach.isReach(p.getWorld())) p.showBossBar(timerBar); else p.hideBossBar(timerBar);
        }
        if (!now.isBefore(nextArrival)) {
            hideTimer();
            warned = false;
            nextArrival = next(now, zone(), settings.intervalMinutes());
            spawn();
        }
    }

    private void hideTimer() { for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(timerBar); }

    private void ensureTicket(Location at) {
        int x = at.getBlockX() >> 4, z = at.getBlockZ() >> 4;
        if (ticketX == x && ticketZ == z) return;
        at.getWorld().addPluginChunkTicket(x, z, plugin);
        ticketX = x; ticketZ = z;
    }

    private void releaseTicket() {
        World world = reach.world();
        if (world != null && ticketX != Integer.MIN_VALUE) world.removePluginChunkTicket(ticketX, ticketZ, plugin);
        ticketX = Integer.MIN_VALUE;
    }

    /** Brings the boss now; false when the arena or the creature isn't available. */
    boolean spawn() {
        if (active()) return false;
        Location arena = arena();
        if (arena == null || plugin.mobs() == null || !plugin.mobs().ids().contains(settings.mob())) {
            plugin.getLogger().warning("World boss skipped: the arena spot " + settings.spot() + " or mob " + settings.mob() + " is missing.");
            return false;
        }
        ensureTicket(arena);
        try {
            LivingEntity spawned = plugin.mobs().spawn(settings.mob(), arena);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(false);
            boss = spawned.getUniqueId();
            spawnedAt = System.currentTimeMillis();
            phase = 0;
            nextStrike = spawnedAt + 15_000;
            damage.clear();
            arena.getWorld().strikeLightningEffect(arena);
            broadcast("<aqua><bold>⚡ Thunderhide has come to the Stone Rings!</bold></aqua> <gray>Every fighter is paid by their share. "
                + "It leaves in " + settings.fleeMinutes() + " minutes.</gray> <click:run_command:'/worldboss go'><yellow>[/worldboss go]</yellow></click>");
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "World boss could not spawn", failure);
            return false;
        }
    }

    // ------------------------------------------------------------------ the fight

    private void fight(Instant now) {
        if (!(Bukkit.getEntity(boss) instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
            if (Bukkit.getEntity(boss) == null && System.currentTimeMillis() - spawnedAt > 5_000) end(false, null);
            return;
        }
        long t = System.currentTimeMillis();
        if (t - spawnedAt > settings.fleeMinutes() * 60_000L) {
            living.getWorld().strikeLightningEffect(living.getLocation());
            living.remove();
            end(false, null);
            return;
        }
        Location home = arena();
        if (home != null && living.getLocation().distanceSquared(home) > 48 * 48) living.teleport(home);
        var maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double fraction = maxHealth == null ? 1 : living.getHealth() / maxHealth.getValue();
        if (phase == 0 && fraction <= 2 / 3D || phase == 1 && fraction <= 1 / 3D) {
            phase++;
            herd(living);
        }
        if (t >= nextStrike) {
            nextStrike = t + (phase >= 2 ? 9_000 : 14_000);
            strike(living);
        }
    }

    /** Lightning: a warning ring under a fighter, then the bolt two seconds later where they stood. */
    private void strike(LivingEntity living) {
        List<Player> near = living.getWorld().getPlayers().stream()
            .filter(p -> !p.getGameMode().isInvulnerable() && p.getLocation().distanceSquared(living.getLocation()) < 30 * 30).toList();
        if (near.isEmpty()) return;
        Player target = near.get(ThreadLocalRandom.current().nextInt(near.size()));
        Location at = target.getLocation().clone();
        for (int a = 0; a < 360; a += 20)
            at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(Math.cos(Math.toRadians(a)) * 2.5, 0.2, Math.sin(Math.toRadians(a)) * 2.5), 2, 0, 0, 0, 0);
        target.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 0.6F, 1.6F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active()) return;
            at.getWorld().strikeLightningEffect(at);
            for (Player p : at.getWorld().getPlayers())
                if (!p.getGameMode().isInvulnerable() && p.getLocation().distanceSquared(at) < 2.5 * 2.5) p.damage(6, living);
        }, 40L);
    }

    private void herd(LivingEntity living) {
        int count = 2 + phase;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location at = living.getLocation().clone().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
            try {
                LivingEntity add = plugin.mobs().spawn("bristle_boar", at);
                add.setRemoveWhenFarAway(true);
            } catch (RuntimeException ignored) { /* no herd if the creature is missing */ }
        }
        living.getWorld().playSound(living.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5F, 0.8F);
        broadcastNear(living.getLocation(), "<aqua>Thunderhide bellows, and the herd answers!</aqua>");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (boss == null || !event.getEntity().getUniqueId().equals(boss)) return;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (source instanceof Player player) damage.merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (boss == null || !event.getEntity().getUniqueId().equals(boss)) return;
        end(true, event.getEntity().getLocation());
    }

    private void end(boolean beaten, Location at) {
        boss = null;
        releaseTicket();
        if (!beaten) {
            broadcast("<gray>Thunderhide shakes off the fight and gallops into the storm. It will be back in "
                + Chat.duration(Duration.between(Instant.now(), nextArrival)) + ".</gray>");
            damage.clear();
            return;
        }
        Map<UUID, Long> pay = payouts(damage);
        UUID top = damage.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        String week = weekKey();
        List<String> names = new ArrayList<>();
        for (var entry : pay.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            AccountId account = plugin.sessions().account(entry.getKey()).orElse(null);
            if (account == null) continue;
            long marks = entry.getValue();
            String fight = Long.toString(spawnedAt);
            plugin.economy().credit(account, marks, new EconomyService.TransactionReason("world-boss", "Thunderhide"), "world-boss:" + fight + ":" + account.value());
            int kills = plugin.playerData().read(account, yaml -> yaml.getInt(KILLS, 0)) + 1;
            plugin.playerData().update(account, yaml -> yaml.set(KILLS, kills));
            double share = damage.getOrDefault(entry.getKey(), 0D) / Math.max(1, total);
            if (player != null) {
                names.add(player.getName());
                if (plugin.skills() != null) plugin.skills().awardMilestone(player, Skill.HUNTING, Math.round(400 + 1_200 * share), "world-boss:" + fight);
                Chat.success(player, "Thunderhide is down! Your share: <white>" + Math.round(share * 100) + "%</white> <gold>+" + Chat.marks(marks) + "</gold>"
                    + (kills == 1 || kills == 10 || kills == 25 ? " <gold>✦ New island showcase unlocked:</gold> " + Chat.command("island showcase") : ""));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1F);
            }
            if (entry.getKey().equals(top) && plugin.boosters() != null) plugin.boosters().earn(account, "world-boss:" + week);
        }
        String topName = top == null ? null : Bukkit.getOfflinePlayer(top).getName();
        broadcast("<aqua><bold>⚡ Thunderhide has fallen!</bold></aqua> <gray>" + names.size() + " fighter" + (names.size() == 1 ? "" : "s")
            + " shared the spoils" + (topName == null ? "" : "; <white>" + Chat.escape(topName) + "</white> led the charge and earned an XP booster") + ". "
            + "It returns in " + Chat.duration(Duration.between(Instant.now(), nextArrival)) + ".</gray>");
        if (at != null) at.getWorld().spawnParticle(Particle.FIREWORK, at.clone().add(0, 2, 0), 80, 1.5, 1.5, 1.5, 0.15);
        damage.clear();
    }

    private String weekKey() {
        var date = Instant.now().atZone(zone()).toLocalDate();
        return date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) + "-W" + date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private void broadcast(String line) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Chat.send(p, line);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.4F, 1.2F);
        }
    }

    private void broadcastNear(Location at, String line) {
        for (Player p : at.getWorld().getPlayers()) if (p.getLocation().distanceSquared(at) < 64 * 64) Chat.send(p, line);
    }

    // ------------------------------------------------------------------ command

    public boolean command(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "go" -> {
                if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
                if (!active() && !warned) { Chat.info(player, "Thunderhide isn't here yet. " + status()); return true; }
                if (net.nerdsmp.paper.expedition.InstanceWorlds.isRunWorld(player.getWorld())) { Chat.error(player, "Finish your expedition first."); return true; }
                Location arena = arena();
                if (arena == null) { Chat.error(player, "The Stone Rings can't be reached right now."); return true; }
                Location edge = arena.clone().add(12, 0, 0);
                edge.setY(arena.getWorld().getHighestBlockYAt(edge) + 1);
                edge.setDirection(arena.toVector().subtract(edge.toVector()));
                player.teleportAsync(edge);
                Chat.info(player, "To the Stone Rings! Stay out of the sparking rings: lightning follows.");
            }
            case "start" -> {
                if (!sender.hasPermission("nerdsmp.admin")) { Chat.noPermission(sender); return true; }
                Chat.info(sender, spawn() ? "Thunderhide summoned." : "Thunderhide is already here, or the arena is missing.");
            }
            case "stop" -> {
                if (!sender.hasPermission("nerdsmp.admin")) { Chat.noPermission(sender); return true; }
                if (boss != null && Bukkit.getEntity(boss) != null) Bukkit.getEntity(boss).remove();
                end(false, null);
                Chat.info(sender, "Thunderhide sent away.");
            }
            default -> Chat.info(sender, status());
        }
        return true;
    }

    private String status() {
        if (active()) return "<aqua>Thunderhide is at the Stone Rings now!</aqua> " + Chat.command("worldboss go");
        if (nextArrival == null) nextArrival = next(Instant.now(), zone(), settings.intervalMinutes());
        return "Thunderhide next comes to the Stone Rings in <white>" + Chat.duration(Duration.between(Instant.now(), nextArrival)) + "</white>.";
    }

    @Override public void close() {
        if (task != null) task.cancel();
        if (boss != null && Bukkit.getEntity(boss) != null) Bukkit.getEntity(boss).remove();
        hideTimer();
        releaseTicket();
    }
}
