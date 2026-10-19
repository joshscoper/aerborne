package net.nerdsmp.paper.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageDisplayService implements Listener, AutoCloseable {
    private static final TextColor CRITICAL_COLOR = TextColor.color(0xFFD166);

    private final JavaPlugin plugin;
    private final Map<UUID, ArrayDeque<Indicator>> active = new HashMap<>();
    private final boolean enabled;
    private final int lifetimeTicks;
    private final int fadeInTicks;
    private final int fadeOutTicks;
    private final int maxActivePerPlayer;
    private final double verticalRisePerTick;
    private BukkitTask animationTask;

    public DamageDisplayService(JavaPlugin plugin) {
        this.plugin = plugin;
        enabled = plugin.getConfig().getBoolean("presentation.damage-displays.enabled", true);
        lifetimeTicks = Math.max(8,
            plugin.getConfig().getInt("presentation.damage-displays.lifetime-ticks", 24));
        fadeInTicks = Math.max(1, Math.min(lifetimeTicks / 2,
            plugin.getConfig().getInt("presentation.damage-displays.fade-in-ticks", 4)));
        fadeOutTicks = Math.max(1, Math.min(lifetimeTicks / 2,
            plugin.getConfig().getInt("presentation.damage-displays.fade-out-ticks", 8)));
        maxActivePerPlayer = Math.max(1, Math.min(64,
            plugin.getConfig().getInt("presentation.damage-displays.max-active-per-player", 24)));
        verticalRisePerTick = Math.max(0.0D, Math.min(0.08D,
            plugin.getConfig().getDouble("presentation.damage-displays.vertical-rise-per-tick", 0.018D)));
    }

    public void start() {
        if (enabled && animationTask == null) {
            animationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animate, 1L, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof LivingEntity target) || event.getFinalDamage() <= 0.0D) {
            return;
        }
        Player viewer = playerResponsibleFor(event);
        if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(target.getWorld())) {
            return;
        }
        String damageKey = event.getDamageSource().getDamageType().getKey().getKey();
        DamageDisplayType type = DamageDisplayType.classify(
            damageKey, event.getCause().name(), event.getDamageSource().isIndirect()
        );
        double visibleDamage = DamageDisplayMath.visibleDamage(event.getFinalDamage());
        if (visibleDamage > 0.0D) {
            spawn(viewer, target, visibleDamage, type, event.isCritical());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void spawn(
        Player viewer,
        LivingEntity target,
        double damage,
        DamageDisplayType type,
        boolean critical
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location origin = target.getLocation().add(
            random.nextDouble(-0.18D, 0.18D),
            Math.max(0.85D, target.getHeight() * 0.72D) + random.nextDouble(-0.04D, 0.12D),
            random.nextDouble(-0.18D, 0.18D)
        );
        Vector towardViewer = viewer.getEyeLocation().toVector().subtract(origin.toVector()).setY(0.0D);
        if (towardViewer.lengthSquared() > 0.0001D) {
            origin.add(towardViewer.normalize().multiply(0.22D));
        }

        TextDisplay display = target.getWorld().spawn(origin, TextDisplay.class, spawned -> {
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setViewRange(0.75F);
            spawned.setShadowed(true);
            spawned.setSeeThrough(true);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(56, 8, 10, 18));
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setLineWidth(160);
            spawned.setTextOpacity((byte) 0);
            spawned.setInterpolationDuration(2);
            spawned.setInterpolationDelay(0);
            spawned.setTeleportDuration(2);
            spawned.text(displayText(damage, type, critical));
            setTransform(spawned, 0, 0.62F);
        });
        viewer.showEntity(plugin, display);

        ArrayDeque<Indicator> indicators = active.computeIfAbsent(
            viewer.getUniqueId(), ignored -> new ArrayDeque<>()
        );
        while (indicators.size() >= maxActivePerPlayer) {
            remove(indicators.removeFirst());
        }
        indicators.addLast(new Indicator(display));
    }

    private Component displayText(double damage, DamageDisplayType type, boolean critical) {
        String amount = formatDamage(damage);
        if (critical) {
            return Component.text("✦ " + amount + " CRIT", CRITICAL_COLOR, TextDecoration.BOLD);
        }
        String label = type.label().isEmpty() ? amount : amount + " " + type.label();
        return Component.text(label, TextColor.color(type.rgb()), TextDecoration.BOLD);
    }

    private void animate() {
        Iterator<Map.Entry<UUID, ArrayDeque<Indicator>>> owners = active.entrySet().iterator();
        while (owners.hasNext()) {
            Map.Entry<UUID, ArrayDeque<Indicator>> owner = owners.next();
            Player viewer = plugin.getServer().getPlayer(owner.getKey());
            Iterator<Indicator> indicators = owner.getValue().iterator();
            while (indicators.hasNext()) {
                Indicator indicator = indicators.next();
                TextDisplay display = indicator.display();
                if (viewer == null || !viewer.isOnline() || !display.isValid()
                    || indicator.age() >= lifetimeTicks) {
                    remove(indicator);
                    indicators.remove();
                    continue;
                }
                int opacity = DamageDisplayMath.opacityAt(
                    indicator.age(), lifetimeTicks, fadeInTicks, fadeOutTicks
                );
                float scale = DamageDisplayMath.scaleAt(
                    indicator.age(), lifetimeTicks, fadeInTicks, fadeOutTicks
                );
                display.setTextOpacity((byte) opacity);
                setTransform(display, indicator.age(), scale);
                indicator.advance();
            }
            if (owner.getValue().isEmpty()) {
                owners.remove();
            }
        }
    }

    private void setTransform(TextDisplay display, int age, float scale) {
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(
            new Vector3f(0.0F, (float) (age * verticalRisePerTick), 0.0F),
            current.getLeftRotation(),
            new Vector3f(scale, scale, scale),
            current.getRightRotation()
        ));
    }

    private Player playerResponsibleFor(EntityDamageByEntityEvent event) {
        Player player = owner(event.getDamageSource().getCausingEntity());
        if (player != null) {
            return player;
        }
        player = owner(event.getDamageSource().getDirectEntity());
        return player != null ? player : owner(event.getDamager());
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

    private String formatDamage(double damage) {
        if (damage >= 1000.0D) {
            return String.format(Locale.ROOT, "%.1fk", damage / 1000.0D).replace(".0k", "k");
        }
        return String.format(Locale.ROOT, "%.1f", damage).replace(".0", "");
    }

    private void clear(UUID playerId) {
        ArrayDeque<Indicator> indicators = active.remove(playerId);
        if (indicators != null) {
            indicators.forEach(this::remove);
        }
    }

    private void remove(Indicator indicator) {
        if (indicator.display().isValid()) {
            indicator.display().remove();
        }
    }

    @Override
    public void close() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        active.values().forEach(indicators -> indicators.forEach(this::remove));
        active.clear();
    }

    private static final class Indicator {
        private final TextDisplay display;
        private int age;

        private Indicator(TextDisplay display) {
            this.display = display;
        }

        TextDisplay display() {
            return display;
        }

        int age() {
            return age;
        }

        void advance() {
            age++;
        }
    }
}
