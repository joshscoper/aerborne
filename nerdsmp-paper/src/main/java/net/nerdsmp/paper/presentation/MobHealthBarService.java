package net.nerdsmp.paper.presentation;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.nerdsmp.paper.alchemy.PoisonType;
import net.nerdsmp.paper.mob.CustomMobManager;
import net.nerdsmp.paper.player.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.Comparator;

public final class MobHealthBarService implements Listener, AutoCloseable {
    private static final TextColor HEALTHY = TextColor.color(0x55E58A);
    private static final TextColor WOUNDED = TextColor.color(0xF6C85F);
    private static final TextColor CRITICAL = TextColor.color(0xFF5D73);
    private static final int STATUS_BLINK_THRESHOLD_TICKS = 100;
    private static final int STATUS_BLINK_INTERVAL_TICKS = 10;

    private final JavaPlugin plugin;
    private final CustomMobManager mobs;
    private final PlayerSettingsService settings;
    private final NamespacedKey activePoisonTypesKey;
    private final NamespacedKey npcInstanceKey;
    private final Map<UUID, OverheadIndicator> overhead = new LinkedHashMap<>();
    private final Map<UUID, BossIndicator> bosses = new HashMap<>();
    private final boolean enabled;
    private final int updateIntervalTicks;
    private final int overheadLifetimeTicks;
    private final int segments;
    private final int maximumOverheadBars;
    private final double overheadViewerRangeSquared;
    private final double bossViewerRangeSquared;
    private final float displayViewRange;
    private final Set<String> nameplateWorlds;
    private final double nameplateRange;
    private final int nameplatesPerViewer;
    private long nextNameplateScan;
    private static final int NAMEPLATE_SCAN_TICKS = 10;
    private long clock;
    private BukkitTask updateTask;

    public MobHealthBarService(JavaPlugin plugin, CustomMobManager mobs, PlayerSettingsService settings) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.settings = settings;
        activePoisonTypesKey = new NamespacedKey(plugin, "active_poison_types");
        npcInstanceKey = new NamespacedKey(plugin, "npc_instance");
        enabled = plugin.getConfig().getBoolean("presentation.mob-health-bars.enabled", true);
        updateIntervalTicks = Math.max(1, Math.min(10,
            plugin.getConfig().getInt("presentation.mob-health-bars.update-interval-ticks", 2)));
        overheadLifetimeTicks = Math.max(20,
            plugin.getConfig().getInt("presentation.mob-health-bars.overhead-lifetime-ticks", 100));
        segments = Math.max(5, Math.min(20,
            plugin.getConfig().getInt("presentation.mob-health-bars.segments", 12)));
        maximumOverheadBars = Math.max(8, Math.min(256,
            plugin.getConfig().getInt("presentation.mob-health-bars.maximum-overhead-bars", 64)));
        double overheadViewerRange = Math.max(8.0D,
            plugin.getConfig().getDouble("presentation.mob-health-bars.overhead-viewer-range", 32.0D));
        double bossViewerRange = Math.max(16.0D,
            plugin.getConfig().getDouble("presentation.mob-health-bars.boss-viewer-range", 64.0D));
        overheadViewerRangeSquared = overheadViewerRange * overheadViewerRange;
        bossViewerRangeSquared = bossViewerRange * bossViewerRange;
        displayViewRange = (float) Math.max(0.25D, Math.min(2.0D,
            plugin.getConfig().getDouble("presentation.mob-health-bars.display-view-range", 0.75D)));
        String worldPath = "presentation.mob-health-bars.nameplate-worlds";
        nameplateWorlds = new HashSet<>();
        for (String world : plugin.getConfig().contains(worldPath)
            ? plugin.getConfig().getStringList(worldPath) : List.of("MMORPG"))
            nameplateWorlds.add(world.toLowerCase(Locale.ROOT));
        nameplateRange = Math.clamp(plugin.getConfig().getDouble("presentation.mob-health-bars.nameplate-range", 24), 8, Math.sqrt(overheadViewerRangeSquared));
        nameplatesPerViewer = Math.clamp(plugin.getConfig().getInt("presentation.mob-health-bars.nameplates-per-viewer", 24), 1, maximumOverheadBars);
    }

    public void start() {
        if (enabled && updateTask == null) {
            updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::update, updateIntervalTicks, updateIntervalTicks
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!enabled || !(event.getEntity() instanceof LivingEntity target)
            || !eligible(target) || event.getFinalDamage() <= 0.0D) {
            return;
        }
        double healthAfterHit = Math.max(0.0D, target.getHealth() - event.getFinalDamage());
        Player viewer = playerResponsibleFor(event);
        if (mobs.isBoss(target)) {
            if (viewer != null) {
                showBoss(target, healthAfterHit, viewer);
            } else {
                BossIndicator indicator = bosses.get(target.getUniqueId());
                if (indicator != null) {
                    updateBoss(indicator, healthAfterHit);
                }
            }
        } else if (viewer != null) {
            showOverhead(target, healthAfterHit, viewer);
        } else {
            OverheadIndicator indicator = overhead.get(target.getUniqueId());
            if (indicator != null) {
                refreshOverheadViewers(indicator);
                updateOverhead(indicator, healthAfterHit);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!enabled || !(event.getEntity() instanceof LivingEntity target) || !eligible(target)) {
            return;
        }
        double healthAfterHealing = Math.min(maximumHealth(target), target.getHealth() + event.getAmount());
        OverheadIndicator overheadIndicator = overhead.get(target.getUniqueId());
        if (overheadIndicator != null) {
            updateOverhead(overheadIndicator, healthAfterHealing);
        }
        BossIndicator bossIndicator = bosses.get(target.getUniqueId());
        if (bossIndicator != null) {
            updateBoss(bossIndicator, healthAfterHealing);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (enabled && event.getTarget() instanceof Player viewer
            && event.getEntity() instanceof LivingEntity target && eligible(target)) {
            if (mobs.isBoss(target)) {
                showBoss(target, target.getHealth(), viewer);
            } else if (mobs.isElite(target) || mobs.isCustomMob(target)) {
                showOverhead(target, target.getHealth(), viewer);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        for (OverheadIndicator indicator : overhead.values()) {
            indicator.viewers().remove(viewerId);
        }
        for (BossIndicator indicator : bosses.values()) {
            if (indicator.viewers().remove(viewerId)) {
                event.getPlayer().hideBossBar(indicator.bar());
            }
        }
    }

    private void showOverhead(LivingEntity target, double health, Player viewer) {
        // The Aerborne client draws its own nameplates from the list discoverNameplates sends it.
        if (ClientFeeds.listens(viewer, ClientFeeds.PLATES)) return;
        if (!canView(viewer, target, overheadViewerRangeSquared)) {
            return;
        }
        OverheadIndicator current = ensureOverhead(target, false);
        current.combatUntil = clock + overheadLifetimeTicks;
        current.viewers().put(viewer.getUniqueId(), clock + overheadLifetimeTicks);
        viewer.showEntity(plugin, current.display());
        updateOverhead(current, health);
    }

    private OverheadIndicator ensureOverhead(LivingEntity target, boolean passive) {
        OverheadIndicator current = overhead.get(target.getUniqueId());
        if (current == null || !current.display().isValid()) {
            removeOverhead(target.getUniqueId());
            // Ambient names share the existing display pool, but cannot evict a combat indicator.
            if (passive && overhead.size() >= maximumOverheadBars) return null;
            while (overhead.size() >= maximumOverheadBars) {
                UUID oldest = overhead.keySet().iterator().next();
                removeOverhead(oldest);
            }
            TextDisplay display = spawnDisplay(target);
            current = new OverheadIndicator(target, display);
            overhead.put(target.getUniqueId(), current);
        }
        return current;
    }

    /** Only scans already-loaded nearby entities; no world-wide scan or chunk tickets. */
    private void discoverNameplates(Player viewer) {
        if (enabled && viewer.isOnline() && ClientFeeds.listens(viewer, ClientFeeds.PLATES)) {
            clientNameplates(viewer);
            return;
        }
        if (!enabled || !viewer.isOnline() || !nameplateWorld(viewer.getWorld())
            || !settings.enabled(viewer.getUniqueId(), PlayerSettingsService.Setting.MOB_HEALTH_BARS)) return;
        Location origin = viewer.getLocation();
        var candidates = viewer.getNearbyEntities(nameplateRange, nameplateRange, nameplateRange).stream()
            .filter(entity -> entity instanceof LivingEntity living && eligible(living))
            .map(entity -> (LivingEntity) entity)
            .filter(entity -> entity.getLocation().distanceSquared(origin) <= nameplateRange * nameplateRange)
            .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
            .filter(entity -> canView(viewer, entity, nameplateRange * nameplateRange))
            .limit(nameplatesPerViewer)
            .toList();
        Set<UUID> selected = new HashSet<>();
        candidates.forEach(target -> selected.add(target.getUniqueId()));
        var previousEntries = overhead.entrySet().iterator();
        while (previousEntries.hasNext()) {
            OverheadIndicator indicator = previousEntries.next().getValue();
            if (clock >= indicator.combatUntil && !selected.contains(indicator.target().getUniqueId())
                && indicator.viewers().remove(viewer.getUniqueId()) != null) {
                viewer.hideEntity(plugin, indicator.display());
                if (indicator.viewers().isEmpty()) { removeDisplay(indicator.display()); previousEntries.remove(); }
            }
        }
        for (LivingEntity target : candidates) {
            OverheadIndicator indicator = ensureOverhead(target, true);
            if (indicator == null) continue;
            Long previous = indicator.viewers().putIfAbsent(viewer.getUniqueId(), 0L);
            indicator.viewers().merge(viewer.getUniqueId(), clock + NAMEPLATE_SCAN_TICKS + updateIntervalTicks + 1, Math::max);
            if (previous == null) viewer.showEntity(plugin, indicator.display());
            updateOverhead(indicator, target.getHealth());
        }
    }

    /** For the Aerborne client: hide the floating text from this viewer and send the names to draw instead. */
    private void clientNameplates(Player viewer) {
        var entries = overhead.entrySet().iterator();
        while (entries.hasNext()) {
            OverheadIndicator indicator = entries.next().getValue();
            if (indicator.viewers().remove(viewer.getUniqueId()) != null) {
                viewer.hideEntity(plugin, indicator.display());
                if (indicator.viewers().isEmpty()) { removeDisplay(indicator.display()); entries.remove(); }
            }
        }
        List<LivingEntity> targets = List.of();
        if (settings.enabled(viewer.getUniqueId(), PlayerSettingsService.Setting.MOB_HEALTH_BARS)) {
            Location origin = viewer.getLocation();
            double range = nameplateRange * nameplateRange;
            targets = viewer.getNearbyEntities(nameplateRange, nameplateRange, nameplateRange).stream()
                .filter(entity -> entity instanceof LivingEntity living && eligible(living))
                .map(entity -> (LivingEntity) entity)
                .filter(entity -> entity.getLocation().distanceSquared(origin) <= range && viewer.canSee(entity))
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(origin)))
                .limit(nameplatesPerViewer)
                .toList();
        }
        // The companion's target frame also names what the player looks at (out to 32 blocks, past the plate range)
        // and nearby townsfolk, so it never falls back to a bare "Vindicator" or "Fisherman".
        List<LivingEntity> named = new java.util.ArrayList<>(targets);
        Entity looked = viewer.getTargetEntity(32, false);
        if (looked instanceof LivingEntity living && !named.contains(living) && (eligible(living) || npcLabel(living) != null)) named.add(living);
        Location here = viewer.getLocation();
        viewer.getNearbyEntities(16, 8, 16).stream()
            .filter(entity -> entity instanceof LivingEntity living && !named.contains(living) && npcLabel(living) != null)
            .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(here)))
            .limit(16)
            .forEach(entity -> named.add((LivingEntity) entity));
        // Only true elites are flagged: a zone's ordinary custom creatures are plain foes, not gold "ELITE" plates.
        ClientFeeds.plates(viewer, named, this::plateName, mobs::isBoss, mobs::isElite, living -> npcLabel(living) != null);
        ClientFeeds.effects(viewer, targets);
    }

    private boolean nameplateWorld(org.bukkit.World world) {
        return nameplateWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    /** Apply a settings change immediately, independently for this viewer. Main thread only. */
    public void refreshViewer(Player viewer) {
        var entries = overhead.entrySet().iterator();
        while (entries.hasNext()) {
            OverheadIndicator indicator = entries.next().getValue();
            if (indicator.viewers().containsKey(viewer.getUniqueId()) && !canView(viewer, indicator.target(), overheadViewerRangeSquared)) {
                viewer.hideEntity(plugin, indicator.display());
                indicator.viewers().remove(viewer.getUniqueId());
                if (indicator.viewers().isEmpty()) { removeDisplay(indicator.display()); entries.remove(); }
            }
        }
        for (BossIndicator indicator : bosses.values()) {
            if (!canView(viewer, indicator.target(), bossViewerRangeSquared) && indicator.viewers().remove(viewer.getUniqueId()))
                viewer.hideBossBar(indicator.bar());
        }
        discoverNameplates(viewer);
    }

    private TextDisplay spawnDisplay(LivingEntity target) {
        Location location = displayLocation(target);
        return target.getWorld().spawn(location, TextDisplay.class, display -> {
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(displayViewRange);
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(72, 7, 9, 16));
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(220);
            display.setTeleportDuration(updateIntervalTicks);
        });
    }

    private void showBoss(LivingEntity target, double health, Player viewer) {
        // Expedition runs draw one bar for their own bosses; a second one here would just repeat it.
        if (net.nerdsmp.paper.expedition.InstanceWorlds.isRunWorld(target.getWorld())) return;
        if (!canView(viewer, target, bossViewerRangeSquared)) {
            return;
        }
        BossIndicator indicator = bosses.computeIfAbsent(target.getUniqueId(), ignored -> {
            BossBar bar = BossBar.bossBar(
                bossTitle(target, health),
                MobHealthBarMath.progress(health, maximumHealth(target)),
                BossBar.Color.PURPLE,
                BossBar.Overlay.NOTCHED_10
            );
            return new BossIndicator(target, bar);
        });
        updateBoss(indicator, health);
        if (indicator.viewers().add(viewer.getUniqueId())) {
            viewer.showBossBar(indicator.bar());
        }
    }

    private void update() {
        clock += updateIntervalTicks;
        Iterator<Map.Entry<UUID, OverheadIndicator>> overheadEntries = overhead.entrySet().iterator();
        while (overheadEntries.hasNext()) {
            Map.Entry<UUID, OverheadIndicator> entry = overheadEntries.next();
            OverheadIndicator indicator = entry.getValue();
            LivingEntity target = indicator.target();
            if (!target.isValid() || target.isDead() || !indicator.display().isValid()
                || !eligible(target)) {
                removeDisplay(indicator.display());
                overheadEntries.remove();
                continue;
            }
            pruneOverheadViewers(indicator);
            if (indicator.viewers().isEmpty()) {
                removeDisplay(indicator.display());
                overheadEntries.remove();
                continue;
            }
            updateOverhead(indicator, target.getHealth());
        }

        Iterator<Map.Entry<UUID, BossIndicator>> bossEntries = bosses.entrySet().iterator();
        while (bossEntries.hasNext()) {
            BossIndicator indicator = bossEntries.next().getValue();
            LivingEntity target = indicator.target();
            if (!target.isValid() || target.isDead() || !mobs.isBoss(target)) {
                hideBoss(indicator);
                bossEntries.remove();
                continue;
            }
            updateBoss(indicator, target.getHealth());
            pruneBossViewers(indicator);
            if (indicator.viewers().isEmpty()) {
                bossEntries.remove();
            }
        }
        if (clock >= nextNameplateScan) {
            nextNameplateScan = clock + NAMEPLATE_SCAN_TICKS;
            for (Player viewer : Bukkit.getOnlinePlayers()) discoverNameplates(viewer);
        }
    }

    private void pruneOverheadViewers(OverheadIndicator indicator) {
        Iterator<Map.Entry<UUID, Long>> viewers = indicator.viewers().entrySet().iterator();
        while (viewers.hasNext()) {
            Map.Entry<UUID, Long> entry = viewers.next();
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (clock < entry.getValue() && canView(viewer, indicator.target(), overheadViewerRangeSquared)) {
                continue;
            }
            if (viewer != null) {
                viewer.hideEntity(plugin, indicator.display());
            }
            viewers.remove();
        }
    }

    private void refreshOverheadViewers(OverheadIndicator indicator) {
        indicator.combatUntil = clock + overheadLifetimeTicks;
        for (Map.Entry<UUID, Long> entry : indicator.viewers().entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (canView(viewer, indicator.target(), overheadViewerRangeSquared)) {
                entry.setValue(clock + overheadLifetimeTicks);
            }
        }
    }

    private void updateOverhead(OverheadIndicator indicator, double health) {
        Location location = displayLocation(indicator.target());
        if (!indicator.display().getWorld().equals(location.getWorld())
            || indicator.display().getLocation().distanceSquared(location) > 0.0001D)
            indicator.display().teleport(location);
        Component text = nameplateWorld(indicator.target().getWorld()) && (clock >= indicator.combatUntil || mobs.isBoss(indicator.target()))
            ? mobName(indicator.target()) : overheadText(indicator.target(), health);
        if (!text.equals(indicator.display().text())) indicator.display().text(text);
    }

    private void updateBoss(BossIndicator indicator, double health) {
        double maximumHealth = maximumHealth(indicator.target());
        float progress = MobHealthBarMath.progress(health, maximumHealth);
        indicator.bar().name(bossTitle(indicator.target(), health));
        indicator.bar().progress(progress);
        indicator.bar().color(progress > 0.50F
            ? BossBar.Color.PURPLE : (progress > 0.25F ? BossBar.Color.YELLOW : BossBar.Color.RED));
    }

    private void pruneBossViewers(BossIndicator indicator) {
        LivingEntity target = indicator.target();
        Iterator<UUID> viewers = indicator.viewers().iterator();
        while (viewers.hasNext()) {
            UUID viewerId = viewers.next();
            Player player = Bukkit.getPlayer(viewerId);
            if (canView(player, target, bossViewerRangeSquared)) {
                continue;
            }
            if (player != null) {
                player.hideBossBar(indicator.bar());
            }
            viewers.remove();
        }
    }

    private Component overheadText(LivingEntity target, double health) {
        double maximumHealth = maximumHealth(target);
        int filled = MobHealthBarMath.filledSegments(health, maximumHealth, segments);
        TextColor color = healthColor(MobHealthBarMath.progress(health, maximumHealth));
        Component bar = Component.text("■".repeat(filled), color)
            .append(Component.text("■".repeat(segments - filled), NamedTextColor.DARK_GRAY))
            .append(Component.text(" " + MobHealthBarMath.formatHealth(health)
                + "/" + MobHealthBarMath.formatHealth(maximumHealth), NamedTextColor.GRAY))
            .append(poisonStatus(target));
        return mobName(target).append(Component.newline()).append(bar);
    }

    private Component bossTitle(LivingEntity target, double health) {
        return mobName(target)
            .append(Component.text("  " + MobHealthBarMath.formatHealth(health)
                + "/" + MobHealthBarMath.formatHealth(maximumHealth(target)), NamedTextColor.GRAY))
            .append(poisonStatus(target));
    }

    private Component poisonStatus(LivingEntity target) {
        PersistentDataContainer data = target.getPersistentDataContainer();
        String stored = data.get(activePoisonTypesKey, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return Component.empty();
        }
        List<String> active = new ArrayList<>();
        PoisonType longestPoison = null;
        int longestDuration = 0;
        for (String id : stored.split(",")) {
            PoisonType poison = PoisonType.fromId(id);
            int remaining = poison == null ? 0 : poison.remainingDurationTicks(target);
            if (remaining <= 0) {
                continue;
            }
            active.add(poison.id());
            if (remaining > longestDuration) {
                longestDuration = remaining;
                longestPoison = poison;
            }
        }
        if (active.isEmpty()) {
            data.remove(activePoisonTypesKey);
            return Component.empty();
        }
        String refreshed = String.join(",", active);
        if (!refreshed.equals(stored)) {
            data.set(activePoisonTypesKey, PersistentDataType.STRING, refreshed);
        }
        if (!MobHealthBarMath.statusIconVisible(longestDuration, clock,
            STATUS_BLINK_THRESHOLD_TICKS, STATUS_BLINK_INTERVAL_TICKS)) {
            return Component.empty();
        }
        return Component.text(" ☠", TextColor.color(longestPoison.color().asRGB()));
    }

    private Component mobName(LivingEntity target) {
        return mobs.displayName(target);
    }

    /** An NPC's name and title, or null when the entity isn't a townsperson. */
    private String npcLabel(LivingEntity target) {
        if (!target.getPersistentDataContainer().has(npcInstanceKey)) {
            // Island visitors are townsfolk too: named, never health-barred.
            if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core && core.skyblock() != null && core.skyblock().guests() != null)
                return core.skyblock().guests().label(target);
            return null;
        }
        if (!(plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core) || core.npcs() == null) return null;
        return core.npcs().label(target).orElse(null);
    }

    private Component plateName(LivingEntity target) {
        String npc = npcLabel(target);
        return npc != null ? Component.text(npc) : mobName(target);
    }

    private TextColor healthColor(float progress) {
        if (progress > 0.50F) {
            return HEALTHY;
        }
        return progress > 0.25F ? WOUNDED : CRITICAL;
    }

    private boolean eligible(LivingEntity target) {
        return target.isValid() && !target.isDead()
            && !target.getPersistentDataContainer().has(npcInstanceKey)
            && !mobs.isMechanicObjective(target)
            && !(target instanceof Tameable tameable && tameable.isTamed())
            && (target instanceof Enemy || mobs.isCustomMob(target) || mobs.isBoss(target));
    }

    private boolean canView(Player viewer, LivingEntity target, double rangeSquared) {
        return viewer != null && viewer.isOnline()
            && settings.enabled(viewer.getUniqueId(), PlayerSettingsService.Setting.MOB_HEALTH_BARS)
            && viewer.getWorld().equals(target.getWorld())
            && viewer.canSee(target)
            && viewer.getLocation().distanceSquared(target.getLocation()) <= rangeSquared
            && viewer.hasLineOfSight(target);
    }

    private Player playerResponsibleFor(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Player player = owner(byEntity.getDamageSource().getCausingEntity());
        if (player != null) {
            return player;
        }
        player = owner(byEntity.getDamageSource().getDirectEntity());
        return player != null ? player : owner(byEntity.getDamager());
    }

    private Player owner(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    private double maximumHealth(LivingEntity target) {
        var attribute = target.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0D, target.getHealth()) : Math.max(1.0D, attribute.getValue());
    }

    private Location displayLocation(LivingEntity target) {
        return target.getLocation().add(0.0D, target.getHeight() + 0.48D, 0.0D);
    }

    private void remove(UUID entityId) {
        removeOverhead(entityId);
        BossIndicator boss = bosses.remove(entityId);
        if (boss != null) {
            hideBoss(boss);
        }
    }

    private void removeOverhead(UUID entityId) {
        OverheadIndicator indicator = overhead.remove(entityId);
        if (indicator != null) {
            for (UUID viewerId : indicator.viewers().keySet()) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    viewer.hideEntity(plugin, indicator.display());
                }
            }
            removeDisplay(indicator.display());
        }
    }

    private void removeDisplay(TextDisplay display) {
        if (display.isValid()) {
            display.remove();
        }
    }

    private void hideBoss(BossIndicator indicator) {
        for (UUID viewerId : indicator.viewers()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null) {
                player.hideBossBar(indicator.bar());
            }
        }
        indicator.viewers().clear();
    }

    @Override
    public void close() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        overhead.values().forEach(indicator -> removeDisplay(indicator.display()));
        overhead.clear();
        bosses.values().forEach(this::hideBoss);
        bosses.clear();
    }

    private static final class OverheadIndicator {
        private final LivingEntity target;
        private final TextDisplay display;
        private final Map<UUID, Long> viewers = new HashMap<>();
        private long combatUntil;

        private OverheadIndicator(LivingEntity target, TextDisplay display) {
            this.target = target;
            this.display = display;
        }

        LivingEntity target() {
            return target;
        }

        TextDisplay display() {
            return display;
        }

        Map<UUID, Long> viewers() {
            return viewers;
        }
    }

    private record BossIndicator(LivingEntity target, BossBar bar, Set<UUID> viewers) {
        private BossIndicator(LivingEntity target, BossBar bar) {
            this(target, bar, new HashSet<>());
        }
    }
}
